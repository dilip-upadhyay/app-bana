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
 *   - extractServiceToken: reads X-AppBana-Token, Authorization Bearer. Used for admin/write/read gates.
 *   - extractToken:        backward-compat shim; may also return session IDs from X-Session-Token.
 *                          MUST NOT be passed to hasAdmin/hasWrite/hasRead.
 *   - extractUserId:       resolves a human identity for audit and approval attribution.
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
     * Extract user ID from request.
     * Priority:
     * 1. Service-token admin override: if the request carries a valid service/admin token
     *    AND an X-User-Id header, returns X-User-Id (internal service-to-service impersonation).
     *    H8 FIX: uses extractServiceToken() — session IDs can never match the admin token.
     * 2. Session attribute set by SessionMiddleware (authoritative for browser sessions).
     * 3. Session token lookup fallback via SessionService.validateSession(token).
     */
    public static String extractUserId(Router.HttpRequest req, AppConfig cfg) {
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

        // Priority 3: session token fallback (covers routes excluded from SessionMiddleware)
        String sessionTok = req.header("X-Session-Token");
        if (sessionTok != null && !sessionTok.isBlank()) {
            SessionService.SessionData session = SessionService.validateSession(sessionTok);
            if (session != null && session.userId() != null) {
                return session.userId();
            }
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
