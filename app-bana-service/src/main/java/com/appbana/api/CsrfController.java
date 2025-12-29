package com.appbana.api;

import com.appbana.service.CsrfService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Controller for CSRF token management endpoints.
 * Implements Story 1.2: CSRF Protection
 * 
 * Endpoints:
 * - GET /api/csrf-token: Generate a CSRF token for a session
 * - POST /api/csrf-validate: Validate a CSRF token (test endpoint)
 */
public class CsrfController {
    
    private static final Logger LOG = LoggerFactory.getLogger(CsrfController.class);
    
    /**
     * GET /api/csrf-token
     * Generate and return a CSRF token for the current session.
     * 
     * Request headers:
     * - X-Session-Id: Session identifier (required)
     * 
     * Response (200 OK):
     * {
     *   "ok": true,
     *   "token": "base64-encoded-token",
     *   "expiresAt": 1234567890000
     * }
     * 
     * Response (400 Bad Request):
     * {
     *   "ok": false,
     *   "error": "Session ID is required"
     * }
     */
    public static BiConsumer<Router.HttpRequest, Router.HttpResponse> generateToken() {
        return (req, res) -> {
            try {
                // Extract session ID from header
                String sessionId = req.header("X-Session-Id");
                
                if (sessionId == null || sessionId.trim().isEmpty()) {
                    LOG.warn("CSRF token generation failed: missing session ID");
                    res.json(400, Map.of(
                        "ok", false,
                        "error", "Session ID is required. Provide X-Session-Id header."
                    ));
                    return;
                }
                
                // Generate token
                String token = CsrfService.generateToken(sessionId);
                long expiresAt = System.currentTimeMillis() + (30 * 60 * 1000); // 30 minutes
                
                LOG.info("Generated CSRF token for session: {}", sessionId);
                
                // Return token and expiration
                Map<String, Object> response = new HashMap<>();
                response.put("ok", true);
                response.put("token", token);
                response.put("expiresAt", expiresAt);
                
                res.json(200, response);
                
            } catch (Exception e) {
                LOG.error("Failed to generate CSRF token", e);
                res.json(500, Map.of(
                    "ok", false,
                    "error", "Internal server error"
                ));
            }
        };
    }
    
    /**
     * POST /api/csrf-validate
     * Validate a CSRF token for the current session.
     * This is primarily for testing; actual validation should happen via middleware.
     * 
     * Request headers:
     * - X-Session-Id: Session identifier (required)
     * - X-CSRF-Token: Token to validate (required)
     * 
     * Response (200 OK):
     * {
     *   "ok": true,
     *   "valid": true
     * }
     * 
     * Response (400 Bad Request):
     * {
     *   "ok": false,
     *   "error": "Session ID and CSRF token are required"
     * }
     * 
     * Response (403 Forbidden):
     * {
     *   "ok": false,
     *   "valid": false,
     *   "error": "Invalid CSRF token"
     * }
     */
    public static BiConsumer<Router.HttpRequest, Router.HttpResponse> validateToken() {
        return (req, res) -> {
            try {
                // Extract session ID and token from headers
                String sessionId = req.header("X-Session-Id");
                String token = req.header("X-CSRF-Token");
                
                if (sessionId == null || sessionId.trim().isEmpty()) {
                    LOG.warn("CSRF validation failed: missing session ID");
                    res.json(400, Map.of(
                        "ok", false,
                        "error", "Session ID is required. Provide X-Session-Id header."
                    ));
                    return;
                }
                
                if (token == null || token.trim().isEmpty()) {
                    LOG.warn("CSRF validation failed: missing token");
                    res.json(400, Map.of(
                        "ok", false,
                        "error", "CSRF token is required. Provide X-CSRF-Token header."
                    ));
                    return;
                }
                
                // Validate token
                boolean isValid = CsrfService.validateToken(sessionId, token);
                
                if (isValid) {
                    LOG.info("CSRF token validated successfully for session: {}", sessionId);
                    res.json(200, Map.of(
                        "ok", true,
                        "valid", true
                    ));
                } else {
                    LOG.warn("CSRF token validation failed for session: {}", sessionId);
                    res.json(403, Map.of(
                        "ok", false,
                        "valid", false,
                        "error", "Invalid CSRF token"
                    ));
                }
                
            } catch (Exception e) {
                LOG.error("Failed to validate CSRF token", e);
                res.json(500, Map.of(
                    "ok", false,
                    "error", "Internal server error"
                ));
            }
        };
    }
}
