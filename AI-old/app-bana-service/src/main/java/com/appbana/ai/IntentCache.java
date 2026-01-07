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
 * IntentCache - Caches normalized user text to ActionDescriptor mappings
 * to avoid repeated GPT calls for common commands.
 * 
 * Features:
 * - In-memory cache with file-based persistence
 * - Automatic normalization of user input
 * - Usage tracking for cache analytics
 * - Thread-safe operations
 */
public class IntentCache {
    private static final Logger LOG = LoggerFactory.getLogger(IntentCache.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final String CACHE_DIR = "app-bana-service/ai-mem";
    private static final String CACHE_FILE = CACHE_DIR + "/intent-cache.json";
    
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
            LOG.info("[IntentCache] Initialized with {} entries", cache.size());
        } catch (Exception e) {
            LOG.error("[IntentCache] Failed to initialize cache", e);
            initialized = true; // Continue with empty cache
        }
    }

    /**
     * Get cached action for normalized text
     * Returns null if not found or if cache is stale
     */
    public static ActionDescriptor get(String text) {
        if (!initialized) {
            initialize();
        }
        
        String normalized = normalize(text);
        CacheEntry entry = cache.get(normalized);
        
        if (entry == null) {
            LOG.debug("[IntentCache] Cache miss for: {}", normalized);
            return null;
        }
        
        // Update hit count and last access time
        entry.hitCount++;
        entry.lastAccessTime = System.currentTimeMillis();
        
        LOG.info("[IntentCache] Cache hit for: {} (hits: {})", normalized, entry.hitCount);
        saveToDiskAsync(); // Save updated stats
        
        return entry.action;
    }

    /**
     * Store action descriptor for given text
     */
    public static void put(String text, ActionDescriptor action) {
        if (!initialized) {
            initialize();
        }
        
        String normalized = normalize(text);
        
        CacheEntry entry = new CacheEntry();
        entry.normalizedText = normalized;
        entry.action = action;
        entry.createdTime = System.currentTimeMillis();
        entry.lastAccessTime = entry.createdTime;
        entry.hitCount = 0;
        
        cache.put(normalized, entry);
        
        LOG.info("[IntentCache] Stored action for: {} -> {}", normalized, action.action);
        saveToDiskAsync();
    }

    /**
     * Check if text exists in cache
     */
    public static boolean contains(String text) {
        if (!initialized) {
            initialize();
        }
        return cache.containsKey(normalize(text));
    }

    /**
     * Get cache statistics
     */
    public static CacheStats getStats() {
        if (!initialized) {
            initialize();
        }
        
        CacheStats stats = new CacheStats();
        stats.totalEntries = cache.size();
        stats.totalHits = cache.values().stream().mapToLong(e -> e.hitCount).sum();
        
        // Find most used entries
        cache.values().stream()
            .sorted((a, b) -> Long.compare(b.hitCount, a.hitCount))
            .limit(10)
            .forEach(entry -> stats.topEntries.add(
                new CacheStats.TopEntry(entry.normalizedText, entry.hitCount, entry.action.action)
            ));
        
        return stats;
    }

    /**
     * Clear entire cache
     */
    public static void clear() {
        if (!initialized) {
            initialize();
        }
        
        cache.clear();
        saveToDiskAsync();
        LOG.info("[IntentCache] Cache cleared");
    }

    /**
     * Remove specific entry
     */
    public static void remove(String text) {
        if (!initialized) {
            initialize();
        }
        
        String normalized = normalize(text);
        cache.remove(normalized);
        saveToDiskAsync();
        LOG.info("[IntentCache] Removed entry: {}", normalized);
    }

    /**
     * Normalize text for consistent cache lookup
     * - Lowercase
     * - Trim whitespace
     * - Remove extra spaces
     * - Remove punctuation
     */
    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        
        return text.toLowerCase(Locale.ROOT)
            .trim()
            .replaceAll("\\s+", " ")
            .replaceAll("[.!?,;:]+$", "") // Remove trailing punctuation
            .replaceAll("^[.!?,;:]+", ""); // Remove leading punctuation
    }

    /**
     * Ensure cache directory exists
     */
    private static void ensureCacheDirectory() throws IOException {
        Path dirPath = Paths.get(CACHE_DIR);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
            LOG.info("[IntentCache] Created cache directory: {}", CACHE_DIR);
        }
    }

    /**
     * Load cache from disk
     */
    private static void loadFromDisk() {
        File file = new File(CACHE_FILE);
        if (!file.exists()) {
            LOG.info("[IntentCache] No cache file found, starting with empty cache");
            return;
        }

        try {
            CacheData data = MAPPER.readValue(file, CacheData.class);
            if (data != null && data.entries != null) {
                cache.clear();
                for (CacheEntry entry : data.entries) {
                    cache.put(entry.normalizedText, entry);
                }
                LOG.info("[IntentCache] Loaded {} entries from disk", cache.size());
            }
        } catch (IOException e) {
            LOG.error("[IntentCache] Failed to load cache from disk", e);
        }
    }

    /**
     * Save cache to disk asynchronously
     */
    private static void saveToDiskAsync() {
        // Save in background thread to avoid blocking
        new Thread(() -> {
            try {
                saveToDisk();
            } catch (Exception e) {
                LOG.error("[IntentCache] Failed to save cache to disk", e);
            }
        }).start();
    }

    /**
     * Save cache to disk
     */
    private static void saveToDisk() throws IOException {
        ensureCacheDirectory();
        
        CacheData data = new CacheData();
        data.lastUpdated = System.currentTimeMillis();
        data.entries = new ArrayList<>(cache.values());
        
        MAPPER.writeValue(new File(CACHE_FILE), data);
        LOG.debug("[IntentCache] Saved {} entries to disk", data.entries.size());
    }

    /**
     * Internal cache entry with metadata
     */
    public static class CacheEntry {
        public String normalizedText;
        public ActionDescriptor action;
        public long createdTime;
        public long lastAccessTime;
        public long hitCount;
        
        public CacheEntry() {}
    }

    /**
     * Action descriptor for cache storage
     */
    public static class ActionDescriptor {
        public String action;
        public Map<String, Object> target;
        public Map<String, Object> options;
        
        public ActionDescriptor() {
            this.target = new HashMap<>();
            this.options = new HashMap<>();
        }
        
        public ActionDescriptor(String action) {
            this();
            this.action = action;
        }
    }

    /**
     * Cache data for serialization
     */
    private static class CacheData {
        public long lastUpdated;
        public List<CacheEntry> entries;
        
        public CacheData() {
            this.entries = new ArrayList<>();
        }
    }

    /**
     * Cache statistics for analytics
     */
    public static class CacheStats {
        public int totalEntries;
        public long totalHits;
        public List<TopEntry> topEntries = new ArrayList<>();
        
        public static class TopEntry {
            public String text;
            public long hits;
            public String action;
            
            public TopEntry(String text, long hits, String action) {
                this.text = text;
                this.hits = hits;
                this.action = action;
            }
        }
    }
}
