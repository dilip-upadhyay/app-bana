package com.appbana.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session Management Service for Entity Form Binding Security.
 * 
 * Provides secure session handling with:
 * - Session creation and validation
 * - Automatic expiration (default 30 min inactivity)
 * - Session renewal on activity
 * - Thread-safe concurrent access
 * - Cleanup of expired sessions
 * 
 * Story 2.1: Session Management
 */
public class SessionService {
    private static final Logger LOG = LoggerFactory.getLogger(SessionService.class);
    
    // Session configuration
    private static final int SESSION_TIMEOUT_MINUTES = 30;
    private static final int SESSION_ID_LENGTH = 32; // 256 bits
    private static final long SESSION_TIMEOUT_MS = SESSION_TIMEOUT_MINUTES * 60 * 1000L;
    
    // Thread-safe session storage
    private static final Map<String, SessionData> SESSIONS = new ConcurrentHashMap<>();
    private static final SecureRandom RANDOM = new SecureRandom();
    
    /**
     * Session data containing user information and timing metadata.
     */
    public record SessionData(
        String sessionId,
        String userId,
        long createdAt,
        long lastAccessedAt,
        long expiresAt,
        Map<String, Object> attributes,
        // S1.1: captured once at login from User.tenantId, avoids a DB round-trip per request.
        String tenantId,
        // S1.1: reserved for a future scoped end-user session (S3); nothing populates it yet.
        String scopedAppId
    ) {
        /**
         * Check if session is expired.
         */
        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
        
        /**
         * Check if session is valid (exists and not expired).
         */
        public boolean isValid() {
            return !isExpired();
        }
        
        /**
         * Get time remaining until expiration in seconds.
         */
        public long remainingSeconds() {
            long remaining = expiresAt - System.currentTimeMillis();
            return Math.max(0, remaining / 1000);
        }
    }
    
    /**
     * Create a new session for a user.
     * 
     * @param userId User identifier
     * @return SessionData containing session details
     */
    public static SessionData createSession(String userId) {
        return createSession(userId, (String) null);
    }

    /**
     * Create a new session for a user, capturing tenantId once at login (S1.1) so
     * tenant-boundary checks don't need a DB round-trip per request.
     *
     * @param userId User identifier
     * @param tenantId Tenant identifier from User.tenantId, or null if unknown
     * @return SessionData containing session details
     */
    public static SessionData createSession(String userId, String tenantId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("userId cannot be null or empty");
        }
        
        String sessionId = generateSessionId();
        long now = System.currentTimeMillis();
        long expiresAt = now + SESSION_TIMEOUT_MS;
        
        SessionData session = new SessionData(
            sessionId,
            userId,
            now,
            now,
            expiresAt,
            new ConcurrentHashMap<>(),
            tenantId,
            null
        );
        
        SESSIONS.put(sessionId, session);
        LOG.info("Session created for user {} with sessionId {} (expires in {} minutes)", 
                userId, sessionId, SESSION_TIMEOUT_MINUTES);
        
        return session;
    }

    /**
     * S3.1: create a session scoped to a single app. Populates the {@code scopedAppId} field
     * reserved since S1.1 — nothing minted this until now. This path is for the optional,
     * separate-user-table login ({@code GenericAppAuthController}, S3.3) where the caller
     * authenticates against a generated app's own entity table, not against
     * {@code appbana_users}. It is NOT what the shipped {@code app-bana-runtime} uses — that
     * keeps an ordinary tenant-wide session and relies on an {@code end-user}-role
     * {@code appbana_app_members} grant instead (S2.6/S3.7; see
     * docs/planning/TENANT_ISOLATION_SECURITY_PLAN.md, review round 3, R3-1).
     *
     * <p>A non-null {@code scopedAppId} means this session is valid ONLY for that app's entity
     * routes ({@code EntityAccessGuard} rule ii, S3.2) — never any {@code AppRoutes}/Studio
     * management endpoint.
     *
     * @param userId User identifier
     * @param tenantId Tenant identifier the app belongs to, or null if unknown
     * @param scopedAppId The single app this session is scoped to
     * @return SessionData containing session details
     */
    public static SessionData createSession(String userId, String tenantId, String scopedAppId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("userId cannot be null or empty");
        }
        if (scopedAppId == null || scopedAppId.trim().isEmpty()) {
            throw new IllegalArgumentException("scopedAppId cannot be null or empty");
        }

        String sessionId = generateSessionId();
        long now = System.currentTimeMillis();
        long expiresAt = now + SESSION_TIMEOUT_MS;

        SessionData session = new SessionData(
            sessionId,
            userId,
            now,
            now,
            expiresAt,
            new ConcurrentHashMap<>(),
            tenantId,
            scopedAppId
        );

        SESSIONS.put(sessionId, session);
        LOG.info("Scoped session created for user {} with sessionId {} (scopedAppId={}, expires in {} minutes)",
                userId, sessionId, scopedAppId, SESSION_TIMEOUT_MINUTES);

        return session;
    }
    
    /**
     * Create session with custom timeout.
     * 
     * @param userId User identifier
     * @param timeoutMinutes Custom timeout in minutes
     * @return SessionData containing session details
     */
    public static SessionData createSession(String userId, int timeoutMinutes) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("userId cannot be null or empty");
        }
        if (timeoutMinutes <= 0) {
            throw new IllegalArgumentException("timeoutMinutes must be positive");
        }
        
        String sessionId = generateSessionId();
        long now = System.currentTimeMillis();
        long expiresAt = now + (timeoutMinutes * 60 * 1000L);
        
        SessionData session = new SessionData(
            sessionId,
            userId,
            now,
            now,
            expiresAt,
            new ConcurrentHashMap<>(),
            null,
            null
        );
        
        SESSIONS.put(sessionId, session);
        LOG.info("Session created for user {} with sessionId {} (expires in {} minutes)", 
                userId, sessionId, timeoutMinutes);
        
        return session;
    }
    
    /**
     * Validate a session and return session data if valid.
     * Does NOT renew session expiration.
     * 
     * @param sessionId Session identifier
     * @return SessionData if valid, null otherwise
     */
    public static SessionData validateSession(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return null;
        }
        
        SessionData session = SESSIONS.get(sessionId);
        if (session == null) {
            LOG.debug("Session not found: {}", sessionId);
            return null;
        }
        
        if (session.isExpired()) {
            LOG.info("Session expired: {} (userId: {})", sessionId, session.userId());
            SESSIONS.remove(sessionId);
            return null;
        }
        
        return session;
    }
    
    /**
     * Renew session - extends expiration time based on activity.
     * Returns renewed session data.
     * 
     * @param sessionId Session identifier
     * @return Renewed SessionData if valid, null otherwise
     */
    public static SessionData renewSession(String sessionId) {
        SessionData oldSession = validateSession(sessionId);
        if (oldSession == null) {
            return null;
        }
        
        long now = System.currentTimeMillis();
        long newExpiresAt = now + SESSION_TIMEOUT_MS;
        
        SessionData renewedSession = new SessionData(
            oldSession.sessionId(),
            oldSession.userId(),
            oldSession.createdAt(),
            now,
            newExpiresAt,
            oldSession.attributes(),
            oldSession.tenantId(),
            oldSession.scopedAppId()
        );
        
        SESSIONS.put(sessionId, renewedSession);
        LOG.debug("Session renewed: {} (userId: {}, new expiration: {})", 
                sessionId, oldSession.userId(), SESSION_TIMEOUT_MINUTES);
        
        return renewedSession;
    }
    
    /**
     * Renew session with custom timeout.
     * 
     * @param sessionId Session identifier
     * @param timeoutMinutes Custom timeout in minutes
     * @return Renewed SessionData if valid, null otherwise
     */
    public static SessionData renewSession(String sessionId, int timeoutMinutes) {
        if (timeoutMinutes <= 0) {
            throw new IllegalArgumentException("timeoutMinutes must be positive");
        }
        
        SessionData oldSession = validateSession(sessionId);
        if (oldSession == null) {
            return null;
        }
        
        long now = System.currentTimeMillis();
        long newExpiresAt = now + (timeoutMinutes * 60 * 1000L);
        
        SessionData renewedSession = new SessionData(
            oldSession.sessionId(),
            oldSession.userId(),
            oldSession.createdAt(),
            now,
            newExpiresAt,
            oldSession.attributes(),
            oldSession.tenantId(),
            oldSession.scopedAppId()
        );
        
        SESSIONS.put(sessionId, renewedSession);
        LOG.debug("Session renewed: {} (userId: {}, new expiration: {} minutes)", 
                sessionId, oldSession.userId(), timeoutMinutes);
        
        return renewedSession;
    }
    
    /**
     * Invalidate (destroy) a session.
     * 
     * @param sessionId Session identifier
     */
    public static void invalidateSession(String sessionId) {
        if (sessionId == null) {
            return;
        }
        
        SessionData removed = SESSIONS.remove(sessionId);
        if (removed != null) {
            LOG.info("Session invalidated: {} (userId: {})", sessionId, removed.userId());
        }
    }
    
    /**
     * Get session data without validation or renewal.
     * Used for testing/debugging only.
     * 
     * @param sessionId Session identifier
     * @return SessionData or null
     */
    public static SessionData getSessionData(String sessionId) {
        return SESSIONS.get(sessionId);
    }
    
    /**
     * Set session attribute.
     * 
     * @param sessionId Session identifier
     * @param key Attribute key
     * @param value Attribute value
     */
    public static void setSessionAttribute(String sessionId, String key, Object value) {
        SessionData session = validateSession(sessionId);
        if (session != null) {
            session.attributes().put(key, value);
        }
    }
    
    /**
     * Get session attribute.
     * 
     * @param sessionId Session identifier
     * @param key Attribute key
     * @return Attribute value or null
     */
    public static Object getSessionAttribute(String sessionId, String key) {
        SessionData session = validateSession(sessionId);
        return session != null ? session.attributes().get(key) : null;
    }
    
    /**
     * Cleanup expired sessions.
     * Should be called periodically (e.g., every 5 minutes).
     * 
     * @return Number of sessions cleaned up
     */
    public static int cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        int cleaned = 0;
        
        for (Map.Entry<String, SessionData> entry : SESSIONS.entrySet()) {
            if (entry.getValue().isExpired()) {
                SESSIONS.remove(entry.getKey());
                cleaned++;
            }
        }
        
        if (cleaned > 0) {
            LOG.info("Cleaned up {} expired sessions", cleaned);
        }
        
        return cleaned;
    }
    
    /**
     * Get count of active (non-expired) sessions.
     * 
     * @return Number of active sessions
     */
    public static int getActiveSessionCount() {
        cleanupExpiredSessions(); // Clean first
        return SESSIONS.size();
    }
    
    /**
     * Get all sessions for a user.
     * 
     * @param userId User identifier
     * @return Array of session IDs
     */
    public static String[] getUserSessions(String userId) {
        return SESSIONS.entrySet().stream()
            .filter(e -> userId.equals(e.getValue().userId()))
            .filter(e -> !e.getValue().isExpired())
            .map(Map.Entry::getKey)
            .toArray(String[]::new);
    }
    
    /**
     * Invalidate all sessions for a user (e.g., on password change).
     * 
     * @param userId User identifier
     * @return Number of sessions invalidated
     */
    public static int invalidateUserSessions(String userId) {
        String[] sessions = getUserSessions(userId);
        for (String sessionId : sessions) {
            invalidateSession(sessionId);
        }
        LOG.info("Invalidated {} sessions for user {}", sessions.length, userId);
        return sessions.length;
    }
    
    /**
     * Clear all sessions (for testing only).
     */
    public static void clearAllSessions() {
        int count = SESSIONS.size();
        SESSIONS.clear();
        LOG.warn("Cleared all sessions ({} total) - should only happen in tests", count);
    }
    
    /**
     * Generate a cryptographically secure session ID.
     * 
     * @return Base64-encoded session ID (256 bits)
     */
    private static String generateSessionId() {
        byte[] bytes = new byte[SESSION_ID_LENGTH];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
