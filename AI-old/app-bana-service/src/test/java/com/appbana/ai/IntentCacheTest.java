package com.appbana.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test IntentCache functionality
 */
public class IntentCacheTest {

    @BeforeEach
    public void setup() {
        IntentCache.initialize();
        IntentCache.clear();
    }

    @Test
    public void testBasicCacheOperations() {
        // Create action descriptor
        IntentCache.ActionDescriptor action = new IntentCache.ActionDescriptor("listApps");
        
        // Store in cache
        IntentCache.put("show my apps", action);
        
        // Verify cache contains entry
        assertTrue(IntentCache.contains("show my apps"));
        
        // Retrieve from cache
        IntentCache.ActionDescriptor cached = IntentCache.get("show my apps");
        assertNotNull(cached);
        assertEquals("listApps", cached.action);
    }

    @Test
    public void testNormalization() {
        // Create action descriptor
        IntentCache.ActionDescriptor action = new IntentCache.ActionDescriptor("listApps");
        
        // Store with one format
        IntentCache.put("Show My Apps", action);
        
        // Retrieve with different format (should normalize to same key)
        IntentCache.ActionDescriptor cached = IntentCache.get("show my apps");
        assertNotNull(cached);
        assertEquals("listApps", cached.action);
        
        // Try with extra whitespace
        cached = IntentCache.get("  show   my   apps  ");
        assertNotNull(cached);
        assertEquals("listApps", cached.action);
    }

    @Test
    public void testCacheMiss() {
        IntentCache.ActionDescriptor cached = IntentCache.get("non existent command");
        assertNull(cached);
    }

    @Test
    public void testRemove() {
        IntentCache.ActionDescriptor action = new IntentCache.ActionDescriptor("listApps");
        IntentCache.put("show apps", action);
        
        assertTrue(IntentCache.contains("show apps"));
        
        IntentCache.remove("show apps");
        
        assertFalse(IntentCache.contains("show apps"));
    }

    @Test
    public void testCacheStats() {
        // Add some entries
        IntentCache.put("list apps", new IntentCache.ActionDescriptor("listApps"));
        IntentCache.put("open app", new IntentCache.ActionDescriptor("loadApp"));
        IntentCache.put("delete app", new IntentCache.ActionDescriptor("deleteApp"));
        
        // Get some to increment hit counts
        IntentCache.get("list apps");
        IntentCache.get("list apps");
        IntentCache.get("open app");
        
        IntentCache.CacheStats stats = IntentCache.getStats();
        
        assertEquals(3, stats.totalEntries);
        assertEquals(3, stats.totalHits);
        assertFalse(stats.topEntries.isEmpty());
    }

    @Test
    public void testClearCache() {
        // Add entries
        IntentCache.put("list apps", new IntentCache.ActionDescriptor("listApps"));
        IntentCache.put("open app", new IntentCache.ActionDescriptor("loadApp"));
        
        IntentCache.CacheStats stats = IntentCache.getStats();
        assertEquals(2, stats.totalEntries);
        
        // Clear cache
        IntentCache.clear();
        
        stats = IntentCache.getStats();
        assertEquals(0, stats.totalEntries);
    }
}
