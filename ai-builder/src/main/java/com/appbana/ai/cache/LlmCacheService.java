package com.appbana.ai.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * LLM Response Cache Service using Caffeine
 * Caches OpenAI API responses to reduce redundant calls and avoid rate limits
 */
@Slf4j
public class LlmCacheService {

    private final Cache<String, String> cache;

    public LlmCacheService() {
        this(100_000, Duration.ofHours(6)); // Default: 100k entries, 6 hour TTL
    }

    public LlmCacheService(long maxSize, Duration ttl) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(ttl.toMillis(), TimeUnit.MILLISECONDS)
                .recordStats()
                .build();

        log.info("[LlmCache] Initialized with maxSize={}, ttl={}", maxSize, ttl);
    }

    /**
     * Get cached response for a prompt
     * 
     * @param prompt      The LLM prompt
     * @param model       The model name (e.g., "gpt-4o")
     * @param temperature The temperature parameter
     * @return Cached response if exists
     */
    public Optional<String> get(String prompt, String model, double temperature) {
        String key = generateKey(prompt, model, temperature);
        String cached = cache.getIfPresent(key);

        if (cached != null) {
            log.debug("[LlmCache] HIT for key: {}", key.substring(0, Math.min(16, key.length())));
            return Optional.of(cached);
        }

        log.debug("[LlmCache] MISS for key: {}", key.substring(0, Math.min(16, key.length())));
        return Optional.empty();
    }

    /**
     * Cache a response
     * 
     * @param prompt      The LLM prompt
     * @param model       The model name
     * @param temperature The temperature parameter
     * @param response    The LLM response to cache
     */
    public void put(String prompt, String model, double temperature, String response) {
        String key = generateKey(prompt, model, temperature);
        cache.put(key, response);
        log.debug("[LlmCache] PUT key: {}, response length: {}",
                key.substring(0, Math.min(16, key.length())), response.length());
    }

    /**
     * Invalidate all cache entries
     */
    public void invalidateAll() {
        cache.invalidateAll();
        log.info("[LlmCache] Invalidated all entries");
    }

    /**
     * Get cache statistics
     */
    public CacheStats getStats() {
        return cache.stats();
    }

    /**
     * Get cache metrics as a formatted string
     */
    public String getMetrics() {
        CacheStats stats = cache.stats();
        long size = cache.estimatedSize();

        double hitRate = stats.hitRate() * 100;
        double missRate = stats.missRate() * 100;

        return String.format(
                "Cache Stats: size=%d, hits=%d (%.1f%%), misses=%d (%.1f%%), evictions=%d",
                size, stats.hitCount(), hitRate, stats.missCount(), missRate, stats.evictionCount());
    }

    /**
     * Generate cache key from prompt, model, and temperature
     * Uses SHA-256 hash to create consistent keys
     */
    private String generateKey(String prompt, String model, double temperature) {
        String input = String.format("%s|%s|%.2f", prompt, model, temperature);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // Fallback to simple hash if SHA-256 not available
            return String.valueOf(input.hashCode());
        }
    }
}
