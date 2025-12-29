package com.appbana.middleware;

import com.appbana.api.Router;
import com.appbana.service.CsrfService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Middleware for CSRF (Cross-Site Request Forgery) token validation.
 * Implements Story 1.2 Task 4: CSRF Validation Middleware
 * 
 * This middleware:
 * - Intercepts POST/PUT/DELETE requests
 * - Validates X-CSRF-Token header against session
 * - Returns 403 Forbidden if validation fails
 * - Allows GET/HEAD/OPTIONS requests to pass through without CSRF check
 * 
 * Usage in Router:
 * router.use(CsrfMiddleware.validate());
 */
public class CsrfMiddleware {
    
    private static final Logger LOG = LoggerFactory.getLogger(CsrfMiddleware.class);
    
    /**
     * HTTP methods that require CSRF protection.
     * GET, HEAD, and OPTIONS are considered safe methods and don't modify state.
     */
    private static final String[] PROTECTED_METHODS = {"POST", "PUT", "DELETE", "PATCH"};
    
    /**
     * Paths that should be excluded from CSRF validation.
     * Typically includes authentication endpoints where CSRF token doesn't exist yet.
     */
    private static final String[] EXCLUDED_PATHS = {
        "/api/auth/register",
        "/api/auth/login",
        "/api/csrf-token"  // Token generation endpoint
    };
    
    /**
     * Create CSRF validation middleware.
     * 
     * @return Middleware handler that validates CSRF tokens
     */
    public static BiConsumer<Router.HttpRequest, Router.HttpResponse> validate() {
        return (req, res) -> {
            String method = req.method();
            String path = req.path();
            
            // Skip CSRF validation for safe methods (GET, HEAD, OPTIONS)
            if (!requiresCsrfProtection(method)) {
                LOG.debug("Skipping CSRF validation for safe method: {}", method);
                return; // Continue to next handler
            }
            
            // Skip CSRF validation for excluded paths
            if (isExcludedPath(path)) {
                LOG.debug("Skipping CSRF validation for excluded path: {}", path);
                return; // Continue to next handler
            }
            
            // Extract session ID and CSRF token from headers
            String sessionId = req.header("X-Session-Id");
            String csrfToken = req.header("X-CSRF-Token");
            
            // Validate session ID presence
            if (sessionId == null || sessionId.trim().isEmpty()) {
                LOG.warn("CSRF validation failed: missing session ID for {} {}", method, path);
                res.json(403, Map.of(
                    "ok", false,
                    "error", "Forbidden: Session ID is required",
                    "code", "CSRF_SESSION_MISSING"
                ));
                return;
            }
            
            // Validate CSRF token presence
            if (csrfToken == null || csrfToken.trim().isEmpty()) {
                LOG.warn("CSRF validation failed: missing CSRF token for {} {}", method, path);
                res.json(403, Map.of(
                    "ok", false,
                    "error", "Forbidden: CSRF token is required",
                    "code", "CSRF_TOKEN_MISSING"
                ));
                return;
            }
            
            // Validate CSRF token
            boolean isValid = CsrfService.validateToken(sessionId, csrfToken);
            
            if (!isValid) {
                LOG.warn("CSRF validation failed: invalid token for session {} on {} {}", 
                        sessionId, method, path);
                res.json(403, Map.of(
                    "ok", false,
                    "error", "Forbidden: Invalid or expired CSRF token",
                    "code", "CSRF_TOKEN_INVALID"
                ));
                return;
            }
            
            // Token is valid, continue to next handler
            LOG.debug("CSRF validation passed for session {} on {} {}", sessionId, method, path);
        };
    }
    
    /**
     * Check if the HTTP method requires CSRF protection.
     * 
     * @param method HTTP method (GET, POST, PUT, DELETE, etc.)
     * @return true if method modifies state and requires CSRF protection
     */
    private static boolean requiresCsrfProtection(String method) {
        if (method == null) {
            return false;
        }
        
        for (String protectedMethod : PROTECTED_METHODS) {
            if (protectedMethod.equalsIgnoreCase(method)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check if the path should be excluded from CSRF validation.
     * 
     * @param path Request path
     * @return true if path should skip CSRF validation
     */
    private static boolean isExcludedPath(String path) {
        if (path == null) {
            return false;
        }
        
        for (String excludedPath : EXCLUDED_PATHS) {
            if (path.equals(excludedPath) || path.startsWith(excludedPath + "/")) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Add a custom excluded path at runtime.
     * Useful for adding dynamic exclusions without modifying the middleware.
     * 
     * @param path Path to exclude from CSRF validation
     */
    public static void addExcludedPath(String path) {
        // In a real implementation, this would modify a dynamic list
        // For now, it's a placeholder for future enhancement
        LOG.info("Note: Dynamic path exclusion not implemented. Add '{}' to EXCLUDED_PATHS array.", path);
    }
}
