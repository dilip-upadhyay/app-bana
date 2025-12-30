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
        "/api/csrf/token",   // CSRF token generation is public
        "/api/templates",    // Templates are public read-only resources
        "/apps/"             // Runtime apps are public for end users (GET only)
    };
    
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
        
        for (String excluded : EXCLUDED_PATHS) {
            if (path.startsWith(excluded)) {
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
            "status", 401
        );
        
        res.json(401, error);
    }
}
