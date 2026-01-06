package com.appbana.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SmallTalkCache - Caches GPT responses for repeated conversational queries
 * to avoid unnecessary API calls for similar questions.
 * 
 * Features:
 * - Semantic similarity matching (normalized text comparison)
 * - File-based persistence
 * - Usage tracking for analytics
 * - Thread-safe operations
 */
public class SmallTalkCache {
    private static final Logger LOG = LoggerFactory.getLogger(SmallTalkCache.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final String CACHE_DIR = "app-bana-service/ai-mem";
    private static final String CACHE_FILE = CACHE_DIR + "/smalltalk-cache.json";
    
    private static final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private static boolean initialized = false;

    /**
     * Initialize the cache by loading from disk
     */
    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        
        try {
            ensureCacheDirectory();
            loadFromDisk();
            initialized = true;
            LOG.info("[SmallTalkCache] Initialized with {} entries", cache.size());
        } catch (Exception e) {
            LOG.error("[SmallTalkCache] Failed to initialize cache", e);
            initialized = true; // Continue with empty cache
        }
    }

    /**
     * Get cached response for normalized text
     * Returns null if not found
     */
    public static String get(String text) {
        if (!initialized) {
            initialize();
        }
        
        String normalized = normalize(text);
        CacheEntry entry = cache.get(normalized);
        
        if (entry == null) {
            LOG.debug("[SmallTalkCache] Cache miss for: {}", normalized);
            return null;
        }
        
        // Update hit count and last access time
        entry.hitCount++;
        entry.lastAccessTime = System.currentTimeMillis();
        
        LOG.info("[SmallTalkCache] Cache hit for: {} (hits: {})", normalized, entry.hitCount);
        saveToDiskAsync(); // Save updated stats
        
        return entry.response;
    }

    /**
     * Store response in cache
     */
    public static void put(String text, String response) {
        if (!initialized) {
            initialize();
        }
        
        String normalized = normalize(text);
        CacheEntry entry = new CacheEntry();
        entry.originalText = text;
        entry.normalizedText = normalized;
        entry.response = response;
        entry.createdTime = System.currentTimeMillis();
        entry.lastAccessTime = entry.createdTime;
        entry.hitCount = 0;
        
        cache.put(normalized, entry);
        LOG.info("[SmallTalkCache] Cached response for: {}", normalized);
        saveToDiskAsync();
    }

    /**
     * Remove an entry from cache
     */
    public static void remove(String text) {
        if (!initialized) {
            initialize();
        }
        
        String normalized = normalize(text);
        cache.remove(normalized);
        LOG.info("[SmallTalkCache] Removed entry: {}", normalized);
        saveToDiskAsync();
    }

    /**
     * Clear all cache entries
     */
    public static void clear() {
        cache.clear();
        LOG.info("[SmallTalkCache] Cache cleared");
        saveToDiskAsync();
    }

    /**
     * Get cache statistics
     */
    public static CacheStats getStats() {
        CacheStats stats = new CacheStats();
        stats.size = cache.size();
        stats.totalHits = cache.values().stream().mapToLong(e -> e.hitCount).sum();
        
        Optional<CacheEntry> mostUsed = cache.values().stream()
            .max(Comparator.comparingLong(e -> e.hitCount));
        
        if (mostUsed.isPresent()) {
            stats.mostUsedQuery = mostUsed.get().originalText;
            stats.mostUsedHits = mostUsed.get().hitCount;
        }
        
        return stats;
    }

    /**
     * Normalize text for cache key
     * - Lowercase
     * - Trim whitespace
     * - Remove extra spaces
     * - Remove punctuation at end
     */
    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        
        String normalized = text.toLowerCase().trim();
        normalized = normalized.replaceAll("\\s+", " ");
        normalized = normalized.replaceAll("[.!?]+$", "");
        
        return normalized;
    }

    /**
     * Ensure cache directory exists
     */
    private static void ensureCacheDirectory() throws IOException {
        Path dir = Paths.get(CACHE_DIR);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
            LOG.info("[SmallTalkCache] Created cache directory: {}", CACHE_DIR);
        }
    }

    /**
     * Load cache from disk
     */
    private static void loadFromDisk() {
        File file = new File(CACHE_FILE);
        if (!file.exists()) {
            LOG.info("[SmallTalkCache] No cache file found, starting fresh");
            return;
        }
        
        try {
            CacheData data = MAPPER.readValue(file, CacheData.class);
            if (data.entries != null) {
                for (CacheEntry entry : data.entries) {
                    cache.put(entry.normalizedText, entry);
                }
            }
            LOG.info("[SmallTalkCache] Loaded {} entries from disk", cache.size());
        } catch (IOException e) {
            LOG.error("[SmallTalkCache] Failed to load cache from disk", e);
        }
    }

    /**
     * Save cache to disk asynchronously
     */
    private static void saveToDiskAsync() {
        new Thread(() -> {
            try {
                ensureCacheDirectory();
                
                CacheData data = new CacheData();
                data.version = "1.0";
                data.lastUpdated = new Date().toString();
                data.entries = new ArrayList<>(cache.values());
                
                MAPPER.writeValue(new File(CACHE_FILE), data);
                LOG.debug("[SmallTalkCache] Saved {} entries to disk", data.entries.size());
            } catch (IOException e) {
                LOG.error("[SmallTalkCache] Failed to save cache to disk", e);
            }
        }).start();
    }

    /**
     * Cache entry model
     */
    public static class CacheEntry {
        public String originalText;
        public String normalizedText;
        public String response;
        public long createdTime;
        public long lastAccessTime;
        public long hitCount;
    }

    /**
     * Cache data model for persistence
     */
    private static class CacheData {
        public String version;
        public String lastUpdated;
        public List<CacheEntry> entries;
    }

    /**
     * Cache statistics model
     */
    public static class CacheStats {
        public int size;
        public long totalHits;
        public String mostUsedQuery;
        public long mostUsedHits;
    }
}
