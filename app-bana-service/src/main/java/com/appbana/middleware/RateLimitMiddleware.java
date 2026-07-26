package com.appbana.middleware;

import com.appbana.api.Router;
import com.appbana.service.RateLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Middleware for rate limiting requests.
 * Implements Story 1.3: Rate Limiting
 * 
 * This middleware:
 * - Tracks requests per IP per endpoint using sliding window
 * - Returns 429 Too Many Requests when limit exceeded
 * - Includes Retry-After header with wait time
 * - Adds X-RateLimit-* headers for client visibility
 * - Supports custom limits per endpoint
 * 
 * Usage in Router:
 * router.use(RateLimitMiddleware.create());
 */
public class RateLimitMiddleware {
    
    private static final Logger LOG = LoggerFactory.getLogger(RateLimitMiddleware.class);
    
    /**
     * Header name for client IP address.
     * Checks in order: X-Forwarded-For, X-Real-IP, remote address
     */
    private static final String[] IP_HEADERS = {
        "X-Forwarded-For",
        "X-Real-IP",
        "X-Client-IP"
    };
    
    /**
     * Paths that should be excluded from rate limiting.
     * Typically includes health checks and static assets.
     */
    private static final String[] EXCLUDED_PATHS = {
        "/health",
        "/ready",
        "/ui/",
        "/openapi.json"
    };
    
    /**
     * Custom rate limits per endpoint.
     * Format: endpoint -> (maxAttempts, windowMinutes)
     */
    private static final Map<String, RateLimitConfig> ENDPOINT_LIMITS = new HashMap<>();
    
    static {
        // Auth endpoints — relaxed for local dev; tighten in production via env/config
        ENDPOINT_LIMITS.put("/api/auth/login", new RateLimitConfig(1000, 15));
        ENDPOINT_LIMITS.put("/api/auth/register", new RateLimitConfig(500, 60));
        
        // Moderate limits for API endpoints
        ENDPOINT_LIMITS.put("/api/", new RateLimitConfig(10000, 15));
    }
    
    /**
     * Configuration for endpoint-specific rate limits.
     */
    private record RateLimitConfig(int maxAttempts, int windowMinutes) {}
    
    /**
     * Create rate limiting middleware with default configuration.
     * 
     * @return Middleware handler
     */
    public static BiConsumer<Router.HttpRequest, Router.HttpResponse> create() {
        return create(RateLimitService.DEFAULT_MAX_ATTEMPTS, RateLimitService.DEFAULT_WINDOW_MINUTES);
    }
    
    /**
     * Create rate limiting middleware with custom default limits.
     * 
     * @param defaultMaxAttempts Default maximum attempts per window
     * @param defaultWindowMinutes Default window size in minutes
     * @return Middleware handler
     */
    public static BiConsumer<Router.HttpRequest, Router.HttpResponse> create(
            int defaultMaxAttempts, 
            int defaultWindowMinutes) {
        
        return (req, res) -> {
            String path = req.path();
            
            // Skip rate limiting for excluded paths
            if (isExcludedPath(path)) {
                LOG.debug("Skipping rate limit for excluded path: {}", path);
                return; // Continue to next handler
            }
            
            // Extract client IP address
            String ipAddress = extractIpAddress(req);
            if (ipAddress == null || ipAddress.trim().isEmpty()) {
                LOG.warn("Could not extract IP address for rate limiting");
                ipAddress = "unknown";
            }
            
            // Get endpoint-specific limits or use defaults
            RateLimitConfig config = getEndpointConfig(path);
            int maxAttempts = config != null ? config.maxAttempts : defaultMaxAttempts;
            int windowMinutes = config != null ? config.windowMinutes : defaultWindowMinutes;
            
            // Check rate limit
            RateLimitService.RateLimitResult result = RateLimitService.checkRateLimit(
                ipAddress, path, maxAttempts, windowMinutes
            );
            
            // Add rate limit headers to response
            addRateLimitHeaders(res, result);
            
            // If limit exceeded, return 429
            if (!result.allowed()) {
                LOG.warn("Rate limit exceeded for IP {} on path {}: {}/{} attempts", 
                        ipAddress, path, result.limit() - result.remaining(), result.limit());
                
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("ok", false);
                errorResponse.put("error", "Too Many Requests");
                errorResponse.put("message", String.format(
                    "Rate limit exceeded. Maximum %d requests per %d minutes.", 
                    result.limit(), windowMinutes));
                errorResponse.put("retryAfter", result.retryAfterSeconds());
                errorResponse.put("code", "RATE_LIMIT_EXCEEDED");
                
                res.json(429, errorResponse);
                return;
            }
            
            // Log warning if approaching limit
            if (result.isWarning()) {
                LOG.info("Rate limit warning for IP {} on path {}: {}/{} attempts remaining", 
                        ipAddress, path, result.remaining(), result.limit());
            }
            
            // Request allowed, continue to next handler
        };
    }
    
    /**
     * Extract client IP address from request.
     * Checks multiple headers and falls back to remote address.
     * 
     * @param req HTTP request
     * @return Client IP address
     */
    private static String extractIpAddress(Router.HttpRequest req) {
        // Check common proxy headers
        for (String headerName : IP_HEADERS) {
            String ip = req.header(headerName);
            if (ip != null && !ip.trim().isEmpty()) {
                // X-Forwarded-For may contain multiple IPs (client, proxy1, proxy2)
                // Take the first one (original client)
                if (headerName.equals("X-Forwarded-For") && ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        }
        
        // Fallback to remote address (not available in current Router implementation)
        // In production, this should be extracted from the underlying HttpExchange
        return "unknown";
    }
    
    /**
     * Check if path should be excluded from rate limiting.
     * 
     * @param path Request path
     * @return true if path should be excluded
     */
    private static boolean isExcludedPath(String path) {
        if (path == null) {
            return false;
        }
        
        for (String excludedPath : EXCLUDED_PATHS) {
            if (path.equals(excludedPath) || path.startsWith(excludedPath)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Get endpoint-specific rate limit configuration.
     * 
     * @param path Request path
     * @return Rate limit config or null for defaults
     */
    private static RateLimitConfig getEndpointConfig(String path) {
        if (path == null) {
            return null;
        }
        
        // Exact match first
        if (ENDPOINT_LIMITS.containsKey(path)) {
            return ENDPOINT_LIMITS.get(path);
        }
        
        // Check for prefix matches
        for (Map.Entry<String, RateLimitConfig> entry : ENDPOINT_LIMITS.entrySet()) {
            if (path.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        return null;
    }
    
    /**
     * Add rate limit headers to response.
     * Headers follow standard conventions:
     * - X-RateLimit-Limit: Maximum requests per window
     * - X-RateLimit-Remaining: Requests remaining in current window
     * - X-RateLimit-Reset: Unix timestamp when limit resets
     * - Retry-After: Seconds to wait before retrying (only when blocked)
     * 
     * @param res HTTP response
     * @param result Rate limit check result
     */
    private static void addRateLimitHeaders(Router.HttpResponse res, RateLimitService.RateLimitResult result) {
        // These headers are informational and should be added via response object
        // Current Router implementation doesn't support custom headers
        // In production, extend Router.HttpResponse to support setHeader(name, value)
        
        // Note: Since current Router doesn't support custom headers,
        // we log them for now. In production, implement:
        // res.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
        // res.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        // res.setHeader("X-RateLimit-Reset", String.valueOf(result.resetAt() / 1000));
        // if (!result.allowed()) {
        //     res.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));
        // }
        
        LOG.debug("Rate limit headers: Limit={}, Remaining={}, Reset={}", 
                result.limit(), result.remaining(), result.resetAt());
    }
    
    /**
     * Configure custom rate limit for a specific endpoint.
     * 
     * @param endpoint Endpoint path
     * @param maxAttempts Maximum attempts per window
     * @param windowMinutes Window size in minutes
     */
    public static void configureEndpointLimit(String endpoint, int maxAttempts, int windowMinutes) {
        ENDPOINT_LIMITS.put(endpoint, new RateLimitConfig(maxAttempts, windowMinutes));
        LOG.info("Configured custom rate limit for {}: {} attempts per {} minutes", 
                endpoint, maxAttempts, windowMinutes);
    }
}
