package com.appbana.middleware;

import com.appbana.api.Router.HttpRequest;
import com.appbana.api.Router.HttpResponse;
import com.appbana.service.SessionService;
import com.appbana.service.SessionService.SessionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.BiConsumer;

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

    // Note: /appbana-studio/* is currently public for development. Enable
    // authentication in production.

    /**
     * Create session validation middleware.
     * 
     * Extracts session token from:
     * 1. X-Session-Token header (preferred)
     * 2. Cookie: session_id
     * 3. Authorization: Bearer <token>
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

            // Extract session token
            String sessionToken = extractSessionToken(req);

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
     * Extract session token from request.
     * 
     * Priority:
     * 1. X-Session-Token header
     * 2. Cookie: session_id=<token>
     * 3. Authorization: Bearer <token>
     */
    private static String extractSessionToken(HttpRequest req) {
        // Try X-Session-Token header first (recommended)
        String token = req.header("X-Session-Token");
        if (token != null && !token.trim().isEmpty()) {
            return token.trim();
        }

        // Try Cookie header
        String cookie = req.header("Cookie");
        if (cookie != null) {
            String[] cookies = cookie.split(";");
            for (String c : cookies) {
                String[] parts = c.trim().split("=", 2);
                if (parts.length == 2 && "session_id".equals(parts[0])) {
                    return parts[1].trim();
                }
            }
        }

        // Try Authorization: Bearer header (least preferred for sessions)
        String auth = req.header("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7).trim();
        }

        return null;
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
