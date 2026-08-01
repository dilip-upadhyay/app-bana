package com.appbana.service;

import com.appbana.config.AppConfig;
import com.appbana.api.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Authentication and Authorization Service
 * Handles token extraction, validation, and permission checks.
 *
 * Token type separation (H8):
 *   - extractServiceToken:     reads X-AppBana-Token, Authorization Bearer. Used for admin/write/read gates.
 *   - extractToken:            backward-compat shim; may also return session IDs from X-Session-Token.
 *                              MUST NOT be passed to hasAdmin/hasWrite/hasRead.
 *   - extractSessionCredential: locates a raw session token trying both supported credential forms
 *                              (X-Session-Token, Authorization Bearer). Does NOT validate it. Shared
 *                              by resolveIdentity() and SessionMiddleware. A session_id cookie form
 *                              was removed here (post-S0.1 review fix) — nothing in the codebase
 *                              ever set that cookie, so it was dead-but-accepted attack surface.
 *   - resolveIdentity:         canonical identity resolution (S0.1). Single method for both credential
 *                              forms; use this for new code.
 *   - resolveSession:          like resolveIdentity, but returns the full SessionData (S1.2) so
 *                              fields such as tenantId are available. Returns null for a
 *                              service/admin token caller (no session exists for them).
 *   - extractUserId:           resolves a human identity for audit and approval attribution.
 *                              Delegates to resolveIdentity() — kept for the existing ~30 call sites.
 */
public class AuthService {
    private static final Logger LOG = LoggerFactory.getLogger(AuthService.class);

    /**
     * Check if authentication is enabled in configuration
     */
    public static boolean authEnabled(AppConfig cfg) {
        return cfg.getAdminToken() != null && !cfg.getAdminToken().isBlank()
                || cfg.getReadToken() != null && !cfg.getReadToken().isBlank();
    }

    /**
     * Extract a service/admin token from the request.
     * Reads ONLY X-AppBana-Token and Authorization: Bearer headers.
     * Never reads X-Session-Token or cookies, so its output is safe to pass to hasAdmin().
     *
     * Use this method (not extractToken) whenever the result will be compared to adminToken/readToken.
     */
    public static String extractServiceToken(Router.HttpRequest req) {
        String tok = req.header("X-AppBana-Token");
        if (tok == null || tok.isBlank()) {
            String auth = req.header("Authorization");
            if (auth != null && auth.toLowerCase(Locale.ROOT).startsWith("bearer "))
                tok = auth.substring(7).trim();
        }
        return tok;
    }

    /**
     * Extract token from request — backward-compatible shim.
     * Checks X-Session-Token first, then delegates to extractServiceToken.
     *
     * WARNING: the returned value may be a session ID (a random opaque string).
     * Do NOT pass to hasAdmin(), hasWrite(), or hasRead() — use extractServiceToken() instead.
     * Safe uses: logging (avoid), passing to SessionService.validateSession(), null checks.
     */
    public static String extractToken(Router.HttpRequest req) {
        // NOTE: X-Session-Token is intentionally checked here for backward compat,
        // but see extractServiceToken() for the safe admin-check path.
        String tok = req.header("X-Session-Token");
        if (tok == null || tok.isBlank()) {
            tok = extractServiceToken(req);
        }
        return tok;
    }

    /**
     * Locate a raw session credential (opaque token) from the request, trying both supported
     * forms in a fixed priority order. Does NOT validate the token — just finds it. Safe to pass
     * to SessionService.validateSession()/renewSession(); never to hasAdmin(), hasWrite(), or
     * hasRead() (a session id must never be compared against the admin/read token).
     *
     * Priority:
     * 1. X-Session-Token header (preferred)
     * 2. Authorization: Bearer &lt;token&gt; (least preferred — shared with service tokens, so
     *    callers that need a service/admin token must check extractServiceToken() first)
     *
     * A session_id cookie form was previously supported here but has been removed: nothing in
     * the codebase (studio, runtime, shared, ai-builder, e2e) ever set that cookie, so it was
     * dead-but-accepted attack surface that also undermined this plan's CSRF-non-applicability
     * premise elsewhere (cookie-based auth is what makes CSRF a real concern; this service has
     * no other cookie-based credential path now that this one is gone).
     *
     * Shared by resolveIdentity() (S0.1) and SessionMiddleware.create(), so both places agree on
     * exactly the same forms instead of maintaining separate, potentially-drifting logic.
     */
    public static String extractSessionCredential(Router.HttpRequest req) {
        String token = req.header("X-Session-Token");
        if (token != null && !token.isBlank()) {
            return token.trim();
        }

        String auth = req.header("Authorization");
        if (auth != null && !auth.isBlank() && auth.trim().toLowerCase(Locale.ROOT).startsWith("bearer ")) {
            return auth.trim().substring(7).trim();
        }

        return null;
    }

    /**
     * Resolve the caller's user id from the request (S0.1) — the canonical identity-resolution
     * entry point covering both session credential forms. Prefer this over extractUserId() in
     * new code.
     *
     * Priority (never reordered — the service/admin-token check always runs first):
     * 1. Service-token admin override: if the request carries a valid service/admin token
     *    AND an X-User-Id header, returns X-User-Id (internal service-to-service impersonation).
     *    H8 FIX: uses extractServiceToken() — session IDs can never match the admin token.
     * 2. Session attribute set by SessionMiddleware (authoritative for browser sessions —
     *    avoids re-validating the same session twice per request).
     * 3. Session credential fallback via extractSessionCredential() + SessionService.validateSession().
     *    Covers routes excluded from SessionMiddleware. This is the fix for B1: extractUserId()
     *    used to check only the X-Session-Token header here, silently dropping a session id sent
     *    via Authorization: Bearer — the form every real Studio/Runtime/ai-builder caller
     *    actually sends — on any middleware-excluded route.
     */
    public static String resolveIdentity(Router.HttpRequest req, AppConfig cfg) {
        // Priority 1: admin service token — H8: use extractServiceToken, NOT extractToken.
        // This ensures a session ID (e.g., "abc123") can never accidentally equal adminToken.
        String serviceToken = extractServiceToken(req);
        if (serviceToken != null && !serviceToken.isBlank() && hasAdmin(serviceToken, cfg)) {
            String headerUserId = req.header("X-User-Id");
            if (headerUserId != null && !headerUserId.isBlank()) {
                return headerUserId;
            }
            return "admin";
        }

        // Priority 2: session attribute populated by SessionMiddleware (authoritative)
        Object attrUserId = req.getAttribute("userId");
        if (attrUserId instanceof String uid && !uid.isBlank()) {
            return uid;
        }

        // Priority 3: session credential fallback — all 3 forms (covers routes excluded from
        // SessionMiddleware). Never reads a form already claimed as a service token above.
        String sessionTok = extractSessionCredential(req);
        if (sessionTok != null && !sessionTok.isBlank()) {
            SessionService.SessionData session = SessionService.validateSession(sessionTok);
            if (session != null && session.userId() != null) {
                return session.userId();
            }
        }

        return null;
    }

    /**
     * Extract user ID from request.
     *
     * Delegates to resolveIdentity() (S0.1) — kept as a separate name for the ~30 existing call
     * sites across the route handlers; prefer resolveIdentity() directly in new code.
     */
    public static String extractUserId(Router.HttpRequest req, AppConfig cfg) {
        return resolveIdentity(req, cfg);
    }

    /**
     * Resolve the caller's full SessionData (S1.2) — mirrors resolveIdentity()'s session-based
     * priority order (request attribute set by SessionMiddleware, then the session credential
     * fallback for routes excluded from SessionMiddleware), but returns the whole SessionData so
     * fields like tenantId are available, rather than narrowing to just the userId.
     *
     * Deliberately does NOT check the service/admin token — a service/admin caller has no
     * session at all. Callers needing to admit that principal unconditionally (e.g.
     * TenantAccessGuard's break-glass branch) must check extractServiceToken()+hasAdmin()
     * themselves FIRST, exactly as resolveIdentity() does internally.
     *
     * @return the resolved SessionData, or null if no valid session is found
     */
    public static SessionService.SessionData resolveSession(Router.HttpRequest req) {
        // Priority 1: sessionId attached by SessionMiddleware (authoritative for browser
        // sessions — avoids re-validating the same session twice per request).
        Object attrSessionId = req.getAttribute("sessionId");
        if (attrSessionId instanceof String sid && !sid.isBlank()) {
            SessionService.SessionData session = SessionService.validateSession(sid);
            if (session != null) {
                return session;
            }
        }

        // Priority 2: session credential fallback (covers routes excluded from SessionMiddleware).
        String sessionTok = extractSessionCredential(req);
        if (sessionTok != null && !sessionTok.isBlank()) {
            return SessionService.validateSession(sessionTok);
        }

        return null;
    }

    /**
     * Check if token has admin permission.
     * Only call with extractServiceToken() output — never with extractToken() or session IDs.
     */
    public static boolean hasAdmin(String token, AppConfig cfg) {
        String at = cfg.getAdminToken();
        return at != null && !at.isBlank() && at.equals(token);
    }

    /**
     * Check if token has read permission
     */
    public static boolean hasRead(String token, AppConfig cfg) {
        if (hasAdmin(token, cfg))
            return true;
        String rt = cfg.getReadToken();
        return rt != null && !rt.isBlank() && rt.equals(token);
    }

    /**
     * Check if token has write permission (same as admin)
     */
    public static boolean hasWrite(String token, AppConfig cfg) {
        return hasAdmin(token, cfg);
    }
}
