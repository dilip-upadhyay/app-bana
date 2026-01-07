package com.appbana.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for CSRF (Cross-Site Request Forgery) token generation and validation.
 * 
 * Implements OWASP CSRF prevention guidelines:
 * - Synchronizer Token Pattern (unique token per session)
 * - Token expiration (30 minutes default)
 * - Secure random token generation
 * - Token rotation on authentication events
 * 
 * Thread-safe implementation using ConcurrentHashMap.
 */
public class CsrfService {
    
    private static final Logger LOG = LoggerFactory.getLogger(CsrfService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    
    /**
     * Token length in bytes (before base64 encoding).
     * 32 bytes = 256 bits of entropy = ~43 characters after base64.
     */
    private static final int TOKEN_LENGTH_BYTES = 32;
    
    /**
     * Default token expiration time in milliseconds (30 minutes).
     */
    private static final long DEFAULT_EXPIRATION_MS = 30 * 60 * 1000; // 30 minutes
    
    /**
     * In-memory token store: sessionId -> TokenData
     * In production, this should be replaced with Redis or a distributed cache.
     */
    private static final Map<String, TokenData> TOKEN_STORE = new ConcurrentHashMap<>();
    
    /**
     * Generate a new CSRF token for a session.
     * 
     * @param sessionId The user's session identifier
     * @return Base64-encoded CSRF token
     */
    public static String generateToken(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            throw new IllegalArgumentException("Session ID cannot be null or empty");
        }
        
        // Generate cryptographically secure random token
        byte[] tokenBytes = new byte[TOKEN_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        
        // Store token with expiration
        long expiresAt = System.currentTimeMillis() + DEFAULT_EXPIRATION_MS;
        TOKEN_STORE.put(sessionId, new TokenData(token, expiresAt));
        
        LOG.debug("Generated CSRF token for session: {} (expires in 30 minutes)", sessionId);
        return token;
    }
    
    /**
     * Generate a token with custom expiration time.
     * 
     * @param sessionId The user's session identifier
     * @param expirationMs Expiration time in milliseconds
     * @return Base64-encoded CSRF token
     */
    public static String generateToken(String sessionId, long expirationMs) {
        if (sessionId == null || sessionId.isEmpty()) {
            throw new IllegalArgumentException("Session ID cannot be null or empty");
        }
        if (expirationMs <= 0) {
            throw new IllegalArgumentException("Expiration time must be positive");
        }
        
        byte[] tokenBytes = new byte[TOKEN_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        
        long expiresAt = System.currentTimeMillis() + expirationMs;
        TOKEN_STORE.put(sessionId, new TokenData(token, expiresAt));
        
        LOG.debug("Generated CSRF token for session: {} (expires in {}ms)", sessionId, expirationMs);
        return token;
    }
    
    /**
     * Validate a CSRF token for a session.
     * 
     * @param sessionId The user's session identifier
     * @param token The CSRF token to validate
     * @return true if token is valid and not expired, false otherwise
     */
    public static boolean validateToken(String sessionId, String token) {
        if (sessionId == null || sessionId.isEmpty()) {
            LOG.warn("CSRF validation failed: null or empty session ID");
            return false;
        }
        if (token == null || token.isEmpty()) {
            LOG.warn("CSRF validation failed: null or empty token");
            return false;
        }
        
        TokenData tokenData = TOKEN_STORE.get(sessionId);
        if (tokenData == null) {
            LOG.warn("CSRF validation failed: no token found for session {}", sessionId);
            return false;
        }
        
        // Check expiration
        if (System.currentTimeMillis() > tokenData.expiresAt) {
            LOG.warn("CSRF validation failed: token expired for session {}", sessionId);
            TOKEN_STORE.remove(sessionId); // Cleanup expired token
            return false;
        }
        
        // Constant-time comparison to prevent timing attacks
        boolean isValid = constantTimeEquals(token, tokenData.token);
        
        if (isValid) {
            LOG.debug("CSRF validation succeeded for session {}", sessionId);
        } else {
            LOG.warn("CSRF validation failed: token mismatch for session {}", sessionId);
        }
        
        return isValid;
    }
    
    /**
     * Rotate the CSRF token for a session (e.g., after login/logout).
     * Generates a new token and invalidates the old one.
     * 
     * @param sessionId The user's session identifier
     * @return New CSRF token
     */
    public static String rotateToken(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            throw new IllegalArgumentException("Session ID cannot be null or empty");
        }
        
        // Remove old token
        TOKEN_STORE.remove(sessionId);
        
        // Generate new token
        String newToken = generateToken(sessionId);
        LOG.debug("Rotated CSRF token for session {}", sessionId);
        
        return newToken;
    }
    
    /**
     * Invalidate (remove) a CSRF token for a session.
     * Should be called on logout or session termination.
     * 
     * @param sessionId The user's session identifier
     */
    public static void invalidateToken(String sessionId) {
        if (sessionId != null) {
            TOKEN_STORE.remove(sessionId);
            LOG.debug("Invalidated CSRF token for session {}", sessionId);
        }
    }
    
    /**
     * Get the expiration time for a token.
     * 
     * @param sessionId The user's session identifier
     * @return Expiration timestamp in milliseconds, or null if token doesn't exist
     */
    public static Long getTokenExpiration(String sessionId) {
        TokenData tokenData = TOKEN_STORE.get(sessionId);
        return tokenData != null ? tokenData.expiresAt : null;
    }
    
    /**
     * Check if a token is expired without removing it.
     * 
     * @param sessionId The user's session identifier
     * @return true if token exists and is expired, false otherwise
     */
    public static boolean isTokenExpired(String sessionId) {
        TokenData tokenData = TOKEN_STORE.get(sessionId);
        if (tokenData == null) {
            return false; // No token = not expired
        }
        return System.currentTimeMillis() > tokenData.expiresAt;
    }
    
    /**
     * Cleanup expired tokens (should be called periodically).
     * In production, this would be handled by cache TTL.
     */
    public static void cleanupExpiredTokens() {
        long now = System.currentTimeMillis();
        int removed = 0;
        
        for (Map.Entry<String, TokenData> entry : TOKEN_STORE.entrySet()) {
            if (now > entry.getValue().expiresAt) {
                TOKEN_STORE.remove(entry.getKey());
                removed++;
            }
        }
        
        if (removed > 0) {
            LOG.debug("Cleaned up {} expired CSRF tokens", removed);
        }
    }
    
    /**
     * Get the total number of active tokens (for monitoring/testing).
     * 
     * @return Number of active tokens in the store
     */
    public static int getActiveTokenCount() {
        return TOKEN_STORE.size();
    }
    
    /**
     * Clear all tokens (for testing only).
     * WARNING: Never call this in production!
     */
    public static void clearAllTokens() {
        TOKEN_STORE.clear();
        LOG.warn("Cleared all CSRF tokens (should only happen in tests)");
    }
    
    /**
     * Constant-time string comparison to prevent timing attacks.
     * Compares two strings in constant time regardless of where they differ.
     * 
     * @param a First string
     * @param b Second string
     * @return true if strings are equal
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return a == b; // Both null = equal
        }
        
        if (a.length() != b.length()) {
            return false;
        }
        
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        
        return result == 0;
    }
    
    /**
     * Internal class to store token data with expiration.
     */
    private static class TokenData {
        final String token;
        final long expiresAt; // Timestamp in milliseconds
        
        TokenData(String token, long expiresAt) {
            this.token = token;
            this.expiresAt = expiresAt;
        }
    }
}
