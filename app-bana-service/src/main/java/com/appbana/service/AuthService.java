package com.appbana.service;

import com.appbana.config.AppConfig;
import com.appbana.api.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Authentication and Authorization Service
 * Handles token extraction, validation, and permission checks
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
     * Extract token from request
     * Checks X-AppBana-Token header first, then Authorization Bearer token
     */
    public static String extractToken(Router.HttpRequest req) {
        String tok = req.header("X-AppBana-Token");
        if (tok == null || tok.isBlank()) {
            String auth = req.header("Authorization");
            if (auth != null && auth.toLowerCase(Locale.ROOT).startsWith("bearer "))
                tok = auth.substring(7).trim();
        }
        return tok;
    }

    /**
     * Extract user ID from request.
     * Priority:
     * 1. Request attribute "userId" set by validated SessionMiddleware (authoritative).
     * 2. Service token override: If request presents a valid admin/service token, allow X-User-Id header override (for internal service calls).
     * 3. Session token lookup fallback via SessionService.validateSession(token).
     */
    public static String extractUserId(Router.HttpRequest req, AppConfig cfg) {
        String token = extractToken(req);

        // Priority 1: Check if admin service token is present -> allow X-User-Id header override for internal services
        if (token != null && !token.isBlank() && hasAdmin(token, cfg)) {
            String headerUserId = req.header("X-User-Id");
            if (headerUserId != null && !headerUserId.isBlank()) {
                return headerUserId;
            }
            return "admin";
        }

        // Priority 2: Check session attribute set by SessionMiddleware (authoritative session user)
        Object attrUserId = req.getAttribute("userId");
        if (attrUserId instanceof String uid && !uid.isBlank()) {
            return uid;
        }

        // Priority 3: Session token lookup fallback via SessionService
        if (token != null && !token.isBlank()) {
            if (hasRead(token, cfg)) {
                return "reader";
            }
            SessionService.SessionData session = SessionService.validateSession(token);
            if (session != null && session.userId() != null) {
                return session.userId();
            }
        }

        return null;
    }

    /**
     * Check if token has admin permission
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
