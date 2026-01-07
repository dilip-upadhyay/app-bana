package com.appbana.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for rate limiting requests using a sliding window algorithm.
 * Implements Story 1.3: Rate Limiting
 * 
 * Features:
 * - Per-IP rate limiting
 * - Per-endpoint rate limiting
 * - Sliding window tracking
 * - Automatic cleanup of expired entries
 * - Configurable limits (maxAttempts, windowMinutes)
 * - Thread-safe implementation using ConcurrentHashMap
 * 
 * Default limits:
 * - 100 requests per 15 minutes per IP per endpoint
 */
public class RateLimitService {
    
    private static final Logger LOG = LoggerFactory.getLogger(RateLimitService.class);
    
    /**
     * Default maximum attempts per window.
     */
    public static final int DEFAULT_MAX_ATTEMPTS = 100;
    
    /**
     * Default window size in minutes.
     */
    public static final int DEFAULT_WINDOW_MINUTES = 15;
    
    /**
     * Warning threshold (percentage of limit).
     * When requests exceed this percentage, include warning headers.
     */
    public static final double WARNING_THRESHOLD = 0.8; // 80%
    
    /**
     * Storage for rate limit attempts: "ip:endpoint" -> List<Timestamp>
     * Thread-safe using ConcurrentHashMap.
     */
    private static final Map<String, List<Long>> RATE_LIMIT_STORE = new ConcurrentHashMap<>();
    
    /**
     * Custom rate limit configurations: "ip:endpoint" -> RateLimitConfig
     */
    private static final Map<String, RateLimitConfig> CUSTOM_LIMITS = new ConcurrentHashMap<>();
    
    /**
     * Configuration for rate limiting.
     */
    public record RateLimitConfig(int maxAttempts, int windowMinutes) {
        public RateLimitConfig {
            if (maxAttempts <= 0) {
                throw new IllegalArgumentException("maxAttempts must be positive");
            }
            if (windowMinutes <= 0) {
                throw new IllegalArgumentException("windowMinutes must be positive");
            }
        }
    }
    
    /**
     * Result of rate limit check.
     */
    public record RateLimitResult(
        boolean allowed,
        int remaining,
        long resetAt,
        int limit,
        boolean isWarning
    ) {
        /**
         * Get retry-after duration in seconds.
         */
        public long retryAfterSeconds() {
            if (allowed) {
                return 0;
            }
            long now = System.currentTimeMillis();
            return Math.max(0, (resetAt - now) / 1000);
        }
    }
    
    /**
     * Check if a request is allowed under rate limiting.
     * 
     * @param ipAddress Client IP address
     * @param endpoint Request endpoint (e.g., "/api/login")
     * @return RateLimitResult indicating if request is allowed
     */
    public static RateLimitResult checkRateLimit(String ipAddress, String endpoint) {
        return checkRateLimit(ipAddress, endpoint, DEFAULT_MAX_ATTEMPTS, DEFAULT_WINDOW_MINUTES);
    }
    
    /**
     * Check if a request is allowed with custom limits.
     * 
     * @param ipAddress Client IP address
     * @param endpoint Request endpoint
     * @param maxAttempts Maximum attempts allowed in window
     * @param windowMinutes Size of sliding window in minutes
     * @return RateLimitResult indicating if request is allowed
     */
    public static RateLimitResult checkRateLimit(
            String ipAddress, 
            String endpoint, 
            int maxAttempts, 
            int windowMinutes) {
        
        if (ipAddress == null || ipAddress.trim().isEmpty()) {
            throw new IllegalArgumentException("IP address cannot be null or empty");
        }
        if (endpoint == null || endpoint.trim().isEmpty()) {
            throw new IllegalArgumentException("Endpoint cannot be null or empty");
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        if (windowMinutes <= 0) {
            throw new IllegalArgumentException("windowMinutes must be positive");
        }
        
        String key = buildKey(ipAddress, endpoint);
        long now = System.currentTimeMillis();
        long windowStartMs = now - (windowMinutes * 60 * 1000L);
        
        // Get or create attempt list for this key
        List<Long> attempts = RATE_LIMIT_STORE.computeIfAbsent(key, k -> new ArrayList<>());
        
        // Thread-safe operation on attempt list
        synchronized (attempts) {
            // Remove expired attempts (outside sliding window)
            attempts.removeIf(timestamp -> timestamp < windowStartMs);
            
            // Count remaining attempts in current window
            int currentAttempts = attempts.size();
            int remaining = maxAttempts - currentAttempts;
            
            // Calculate when the oldest attempt will expire (reset time)
            long resetAt = attempts.isEmpty() ? 
                now + (windowMinutes * 60 * 1000L) : 
                attempts.get(0) + (windowMinutes * 60 * 1000L);
            
            // Check if limit is exceeded
            if (currentAttempts >= maxAttempts) {
                LOG.warn("Rate limit exceeded for IP {} on endpoint {} ({}/{} attempts)", 
                        ipAddress, endpoint, currentAttempts, maxAttempts);
                return new RateLimitResult(false, 0, resetAt, maxAttempts, false);
            }
            
            // Add current attempt
            attempts.add(now);
            
            // Check if we should warn (approaching limit)
            boolean isWarning = (currentAttempts + 1) >= (maxAttempts * WARNING_THRESHOLD);
            
            if (isWarning) {
                LOG.debug("Rate limit warning for IP {} on endpoint {} ({}/{} attempts)", 
                        ipAddress, endpoint, currentAttempts + 1, maxAttempts);
            }
            
            return new RateLimitResult(
                true, 
                remaining - 1, 
                resetAt, 
                maxAttempts, 
                isWarning
            );
        }
    }
    
    /**
     * Record a failed attempt (for tracking purposes).
     * This is useful when you want to track attempts without blocking the request.
     * 
     * @param ipAddress Client IP address
     * @param endpoint Request endpoint
     */
    public static void recordAttempt(String ipAddress, String endpoint) {
        checkRateLimit(ipAddress, endpoint);
    }
    
    /**
     * Reset rate limit for a specific IP and endpoint.
     * Useful for clearing limits after successful authentication or admin override.
     * 
     * @param ipAddress Client IP address
     * @param endpoint Request endpoint
     */
    public static void resetRateLimit(String ipAddress, String endpoint) {
        String key = buildKey(ipAddress, endpoint);
        RATE_LIMIT_STORE.remove(key);
        LOG.info("Rate limit reset for IP {} on endpoint {}", ipAddress, endpoint);
    }
    
    /**
     * Reset rate limit for an entire IP across all endpoints.
     * 
     * @param ipAddress Client IP address
     */
    public static void resetRateLimitForIp(String ipAddress) {
        List<String> keysToRemove = RATE_LIMIT_STORE.keySet().stream()
            .filter(key -> key.startsWith(ipAddress + ":"))
            .collect(Collectors.toList());
        
        keysToRemove.forEach(RATE_LIMIT_STORE::remove);
        LOG.info("Rate limit reset for IP {} across {} endpoints", ipAddress, keysToRemove.size());
    }
    
    /**
     * Get current rate limit status without recording an attempt.
     * 
     * @param ipAddress Client IP address
     * @param endpoint Request endpoint
     * @return Current rate limit status
     */
    public static RateLimitResult getRateLimitStatus(String ipAddress, String endpoint) {
        return getRateLimitStatus(ipAddress, endpoint, DEFAULT_MAX_ATTEMPTS, DEFAULT_WINDOW_MINUTES);
    }
    
    /**
     * Get current rate limit status with custom limits.
     * 
     * @param ipAddress Client IP address
     * @param endpoint Request endpoint
     * @param maxAttempts Maximum attempts allowed
     * @param windowMinutes Window size in minutes
     * @return Current rate limit status
     */
    public static RateLimitResult getRateLimitStatus(
            String ipAddress, 
            String endpoint,
            int maxAttempts,
            int windowMinutes) {
        
        if (ipAddress == null || ipAddress.trim().isEmpty()) {
            throw new IllegalArgumentException("IP address cannot be null or empty");
        }
        if (endpoint == null || endpoint.trim().isEmpty()) {
            throw new IllegalArgumentException("Endpoint cannot be null or empty");
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        if (windowMinutes <= 0) {
            throw new IllegalArgumentException("windowMinutes must be positive");
        }
        
        String key = buildKey(ipAddress, endpoint);
        long now = System.currentTimeMillis();
        long windowStartMs = now - (windowMinutes * 60 * 1000L);
        
        List<Long> attempts = RATE_LIMIT_STORE.get(key);
        if (attempts == null) {
            return new RateLimitResult(true, maxAttempts, 
                now + (windowMinutes * 60 * 1000L), maxAttempts, false);
        }
        
        synchronized (attempts) {
            // Remove expired attempts
            attempts.removeIf(timestamp -> timestamp < windowStartMs);
            
            int currentAttempts = attempts.size();
            int remaining = maxAttempts - currentAttempts;
            long resetAt = attempts.isEmpty() ? 
                now + (windowMinutes * 60 * 1000L) : 
                attempts.get(0) + (windowMinutes * 60 * 1000L);
            
            boolean isWarning = currentAttempts >= (maxAttempts * WARNING_THRESHOLD);
            boolean allowed = currentAttempts < maxAttempts;
            
            return new RateLimitResult(allowed, remaining, resetAt, maxAttempts, isWarning);
        }
    }
    
    /**
     * Configure custom rate limit for a specific IP and endpoint.
     * 
     * @param ipAddress Client IP address
     * @param endpoint Request endpoint
     * @param maxAttempts Maximum attempts allowed
     * @param windowMinutes Window size in minutes
     */
    public static void configureRateLimit(
            String ipAddress, 
            String endpoint,
            int maxAttempts,
            int windowMinutes) {
        
        String key = buildKey(ipAddress, endpoint);
        CUSTOM_LIMITS.put(key, new RateLimitConfig(maxAttempts, windowMinutes));
        LOG.info("Custom rate limit configured for {}: {} attempts per {} minutes", 
                key, maxAttempts, windowMinutes);
    }
    
    /**
     * Cleanup expired entries from the store.
     * Should be called periodically to prevent memory leaks.
     * 
     * @return Number of entries cleaned up
     */
    public static int cleanupExpiredEntries() {
        int cleaned = 0;
        long now = System.currentTimeMillis();
        long maxWindowMs = DEFAULT_WINDOW_MINUTES * 60 * 1000L;
        
        Iterator<Map.Entry<String, List<Long>>> iterator = RATE_LIMIT_STORE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, List<Long>> entry = iterator.next();
            List<Long> attempts = entry.getValue();
            
            synchronized (attempts) {
                // Remove attempts older than the maximum window
                long oldestAllowed = now - maxWindowMs;
                attempts.removeIf(timestamp -> timestamp < oldestAllowed);
                
                // If no attempts remain, remove the entry
                if (attempts.isEmpty()) {
                    iterator.remove();
                    cleaned++;
                }
            }
        }
        
        if (cleaned > 0) {
            LOG.debug("Cleaned up {} expired rate limit entries", cleaned);
        }
        
        return cleaned;
    }
    
    /**
     * Get total number of tracked IP/endpoint combinations.
     * 
     * @return Number of tracked entries
     */
    public static int getTrackedEntriesCount() {
        return RATE_LIMIT_STORE.size();
    }
    
    /**
     * Clear all rate limit data.
     * USE WITH CAUTION - only for testing or emergency situations.
     */
    public static void clearAllRateLimits() {
        RATE_LIMIT_STORE.clear();
        CUSTOM_LIMITS.clear();
        LOG.warn("All rate limit data cleared");
    }
    
    /**
     * Build storage key from IP and endpoint.
     * 
     * @param ipAddress Client IP address
     * @param endpoint Request endpoint
     * @return Storage key in format "ip:endpoint"
     */
    private static String buildKey(String ipAddress, String endpoint) {
        return ipAddress + ":" + endpoint;
    }
}
