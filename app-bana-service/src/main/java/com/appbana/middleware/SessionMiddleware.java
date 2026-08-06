package com.appbana.middleware;

import com.appbana.api.Router.HttpRequest;
import com.appbana.api.Router.HttpResponse;
import com.appbana.service.AuthService;
import com.appbana.service.SessionService;
import com.appbana.service.SessionService.SessionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

/**
 * Session Middleware for Entity Form Binding Security.
 * 
 * Validates session tokens and attaches user context to requests.
 * Automatically renews sessions on valid access.
 * 
 * Protected Routes: All /api/ except /api/auth/*
 * 
 * Story 2.1: Session Management
 */
public class SessionMiddleware {
    private static final Logger LOG = LoggerFactory.getLogger(SessionMiddleware.class);

    // Paths that don't require authentication
    private static final String[] EXCLUDED_PATHS = {
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh",
            // S3.6 (discovered while writing LoginDoesNotLeakEntityExistenceTest): the runtime
            // (per-app end-user table) login route registered in AuthRoutes.java was never added
            // here, so it fell through every carve-out above and required a session to reach a
            // route whose entire purpose is issuing one -- a real caller could never log in.
            // S3.3's own tests called GenericAppAuthController.login() directly (bypassing
            // Router/SessionMiddleware entirely), so this was never exercised through real HTTP
            // until this task's route-level formalization. Mirrors the three sibling builder-auth
            // entries above.
            "/api/runtime/auth/login",
            "/health",
            "/ready",
            "/ui/",
            "/openapi.json",
            "/api/csrf/token", // CSRF token generation is public
            "/api/templates", // Templates are public read-only resources
            "/api/apps/", // Public runtime APIs for end users
            "/api/ai/", // AI endpoints (development mode - for Magic Data Seed, AI generation)
            "/api/tenants/*/branding", // Stage 0: tenant branding is public (needed pre-login by runtime)
            "/api/app-context", // Stage 0: app-context resolver is public
            "/*.html", // All HTML files are public (studio.html, index.html, etc.)
            "/*.js", // JavaScript files from Vite build
            "/*.css", // CSS files from Vite build
            "/assets/" // Vite build assets
    };

    // Special pattern: Entity APIs use a single path segment for the entity key,
    // shaped like "{tenantId}_{appId}_{entityName}" (underscore-joined, NOT
    // slash-joined). These are public for runtime apps — route-level auth
    // (AuthService.hasRead/hasWrite) still applies when tokens are configured,
    // so defense in depth is preserved in production.
    //
    // Covers all CRUD paths: base entity, /{rowId}, /batch, /bulk-delete,
    // /bulk-export. Character class allows letters, digits, underscore, hyphen,
    // and dot.
    private static final String ENTITY_API_PATTERN =
            "^/api/[A-Za-z0-9_.-]+(/([A-Za-z0-9_.-]+))?/?$";

    // Special pattern: App runtime APIs for loading apps/pages in published runtime
    // Example: /api/{tenantId}/apps/{appId}/env/{env}/full
    private static final String APP_RUNTIME_API_PATTERN = "^/api/[^/]+/apps/.*";

    // S1.18: matches exactly 3 path segments after /api/files/ (tenantId/appId/fileId) — the
    // anonymous file-download shape. See the isExcludedPath() usage below for the full security
    // rationale. Named/testable (rather than an inline literal) specifically so a unit test can
    // assert its exact-3-segment boundary directly instead of only through an HTTP round-trip
    // that can't distinguish this class's own behavior from a downstream layer's (round-16
    // review: FileRoutesTenantIsolationTest.uploadRouteStillRequiresASessionAfterTheDownload-
    // RouteExclusion previously asserted only a bare 401 over HTTP, which stayed green even when
    // this pattern was deliberately widened to also swallow POST /api/files/upload, because that
    // route's 401 actually comes from TenantAccessGuard, not from this class — see below).
    //
    // Path-only, therefore verb-agnostic: it excludes EVERY HTTP method registered on this exact
    // shape from session validation, not just GET. Today only GET is registered here
    // (FileRoutes.register()), so this is inert for other verbs — but a future non-GET route
    // added on this same 3-segment shape would silently inherit anonymous access too, and would
    // need its own deliberate re-scoping of this pattern (round-16 review finding; guarded by
    // FileRoutesTenantIsolationTest.onlyGetIsRegisteredOnTheFileDownloadPathShape, which fails the
    // moment a second route is registered on this shape).
    private static final Pattern FILE_DOWNLOAD_EXCLUSION_PATTERN =
            Pattern.compile("^/api/files/[A-Za-z0-9_-]+/[A-Za-z0-9_-]+/[A-Za-z0-9_-]+/?$");

    // Note: /appbana-studio/* is NOT excluded above, so it requires a valid
    // session like any other route (verified live, S1.11 review round 4).

    /**
     * Create session validation middleware.
     * 
     * Extracts session token from:
     * 1. X-Session-Token header (preferred)
     * 2. Authorization: Bearer <token>
     * 
     * On valid session:
     * - Attaches userId to request attributes
     * - Renews session expiration
     * - Allows request to proceed
     * 
     * On invalid/missing session:
     * - Returns 401 Unauthorized with JSON error
     * - Includes WWW-Authenticate header
     * 
     * @return BiConsumer middleware handler
     */
    public static BiConsumer<HttpRequest, HttpResponse> create() {
        return (req, res) -> {
            String path = req.path();

            // Skip authentication for excluded paths
            if (isExcludedPath(path)) {
                LOG.debug("Session middleware: skipping excluded path {}", path);
                return; // Continue to next middleware/handler
            }

            // Extract session token — delegates to AuthService.extractSessionCredential() (S0.1)
            // so this middleware and AuthService.resolveIdentity() agree on the exact same forms.
            String sessionToken = AuthService.extractSessionCredential(req);

            if (sessionToken == null || sessionToken.trim().isEmpty()) {
                LOG.warn("Session middleware: missing session token for {}", path);
                sendUnauthorized(res, "Missing session token");
                return; // Don't call next handler
            }

            // Validate and renew session
            SessionData session = SessionService.renewSession(sessionToken);

            if (session == null) {
                LOG.warn("Session middleware: invalid/expired session {} for {}", sessionToken, path);
                sendUnauthorized(res, "Invalid or expired session");
                return; // Don't call next handler
            }

            // Session valid - attach user context to request
            req.setAttribute("userId", session.userId());
            req.setAttribute("sessionId", session.sessionId());

            LOG.debug("Session middleware: validated user {} for {}", session.userId(), path);

            // Continue to next middleware/handler
        };
    }

    /**
     * Check if path is excluded from session validation.
     */
    private static boolean isExcludedPath(String path) {
        if (path == null) {
            return false;
        }

        // CRITICAL (C1.12 & C2.6): Role management, schema APIs, and approval routes MUST ALWAYS require session authentication
        if (path.contains("/roles") || path.equals("/schema") || path.contains("/approvals") || path.endsWith("/submit") || path.endsWith("/approve") || path.endsWith("/reject")) {
            return false;
        }

        // C3.3: /api/users/me reports the caller's own identity and roles, so it
        // is meaningless without a validated session. It would otherwise be
        // swallowed by ENTITY_API_PATTERN below (as entity="users", id="me"),
        // which would skip session validation and leave `userId` unset — the
        // handler would then 401 every caller regardless of their token.
        if (path.equals("/api/users/me") || path.startsWith("/api/users/me/")) {
            return false;
        }

        // S1.5 (H1): Debug/admin routes must ALWAYS require session authentication,
        // regardless of how many path segments they have. Do not rely on
        // ENTITY_API_PATTERN's segment-count arithmetic to (accidentally) decide
        // this: /api/debug/schemas (2 segments after /api/) used to match
        // ENTITY_API_PATTERN and be treated as a public entity API path -- fully
        // anonymous, cross-tenant schema-summary listing -- while its sibling
        // /api/debug/schemas/names (3 segments) happened to fall outside the
        // pattern and correctly required a session. Name debug/admin routes here
        // explicitly instead of depending on incidental segment counts.
        if (path.startsWith("/api/debug/")) {
            return false;
        }

        // S1.18: restores the anonymous-download design FileRoutes.java's own class Javadoc
        // always documented, for the download route's exact 3-segment shape (see
        // FILE_DOWNLOAD_EXCLUSION_PATTERN above for scoping detail and the verb-agnostic
        // caveat). Protection rests entirely on the (tenantId, appId, fileId) triple: fileId is
        // a server-issued random UUID (122 random bits once the fixed version/variant bits of a
        // v4 UUID are excluded — still unguessable by any margin that matters), and FileRoutes'
        // SELECT_SQL returns an identical 404 for "unknown fileId" and "wrong tenant" so a probe
        // attack learns nothing either way -- the download route never called TenantAccessGuard
        // and a session was never part of its actual protection model. A plain
        // <a href target="_blank"> is the only way to let a real browser preview/stream/
        // right-click-save a file natively, and it can never carry the Authorization header this
        // app's header-based auth needs -- so requiring a session here only ever 401s real users
        // (FileUploadField.tsx's Preview link, StudioTableLive.tsx's Download column), never an
        // attacker who lacks the unguessable fileId anyway. POST /api/files/upload is unaffected
        // by this pattern (round-16 review correction: it was ALREADY excluded from this class
        // entirely, via ENTITY_API_PATTERN below, long before S1.18 existed -- its own
        // required-session behavior is enforced by TenantAccessGuard, not by this class; the
        // prior version of this comment wrongly implied SessionMiddleware itself was the layer
        // preserving that requirement).
        if (FILE_DOWNLOAD_EXCLUSION_PATTERN.matcher(path).matches()) {
            return true;
        }

        // Check if it matches the entity API pattern (/api/{tenantId}/{entityName})
        if (path.matches(ENTITY_API_PATTERN)) {
            LOG.info("[SessionMiddleware] Matched entity API pattern for: {}", path);
            return true;
        }

        // Check if it matches the app runtime API pattern (/api/{tenantId}/apps/...)
        if (path.startsWith("/api/") && path.contains("/apps/")) {
            LOG.info("[SessionMiddleware] Matched app runtime API pattern for: {}", path);
            return true;
        }

        for (String excluded : EXCLUDED_PATHS) {
            // Handle wildcard patterns like "/*.html"
            if (excluded.contains("*")) {
                String pattern = excluded.replace("*", ".*").replace("/", "\\/");
                if (path.matches(pattern)) {
                    return true;
                }
            } else if (path.startsWith(excluded)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Send 401 Unauthorized response with JSON error.
     */
    private static void sendUnauthorized(HttpResponse res, String message) {
        res.setHeader("WWW-Authenticate", "Session realm=\"AppBana\"");
        res.setHeader("Content-Type", "application/json");

        Map<String, Object> error = Map.of(
                "error", "Unauthorized",
                "message", message,
                "status", 401);

        res.json(401, error);
    }
}
