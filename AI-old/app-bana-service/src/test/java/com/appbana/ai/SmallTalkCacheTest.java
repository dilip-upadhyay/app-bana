package com.appbana.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for SmallTalkCache
 */
class SmallTalkCacheTest {

    @BeforeEach
    void setUp() {
        SmallTalkCache.clear();
    }

    @Test
    void testGetMissReturnsNull() {
        String result = SmallTalkCache.get("what can you do?");
        assertNull(result, "Cache miss should return null");
    }

    @Test
    void testPutAndGet() {
        String query = "what can you do?";
        String response = "I can help you build amazing apps!";
        
        SmallTalkCache.put(query, response);
        String result = SmallTalkCache.get(query);
        
        assertEquals(response, result, "Should return cached response");
    }

    @Test
    void testCaseInsensitiveMatch() {
        String query = "what can you do?";
        String response = "I can help you build amazing apps!";
        
        SmallTalkCache.put(query, response);
        
        // Different case should match
        String result = SmallTalkCache.get("What Can You Do?");
        assertEquals(response, result, "Should match case-insensitively");
    }

    @Test
    void testPunctuationNormalization() {
        String query = "what is your name";
        String response = "I'm Studio!";
        
        SmallTalkCache.put(query, response);
        
        // With punctuation should match
        String result = SmallTalkCache.get("what is your name?");
        assertEquals(response, result, "Should normalize trailing punctuation");
    }

    @Test
    void testWhitespaceNormalization() {
        String query = "what   can   you   do";
        String response = "I can help!";
        
        SmallTalkCache.put(query, response);
        
        // Normal spacing should match
        String result = SmallTalkCache.get("what can you do");
        assertEquals(response, result, "Should normalize whitespace");
    }

    @Test
    void testRemove() {
        String query = "what can you do?";
        String response = "I can help!";
        
        SmallTalkCache.put(query, response);
        SmallTalkCache.remove(query);
        
        String result = SmallTalkCache.get(query);
        assertNull(result, "Removed entry should return null");
    }

    @Test
    void testClear() {
        SmallTalkCache.put("query1", "response1");
        SmallTalkCache.put("query2", "response2");
        
        SmallTalkCache.clear();
        
        assertNull(SmallTalkCache.get("query1"), "Cache should be empty after clear");
        assertNull(SmallTalkCache.get("query2"), "Cache should be empty after clear");
    }

    @Test
    void testStatsAfterMultipleAccesses() {
        String query = "what can you do?";
        String response = "I can help!";
        
        SmallTalkCache.put(query, response);
        
        // Access multiple times
        SmallTalkCache.get(query);
        SmallTalkCache.get(query);
        SmallTalkCache.get(query);
        
        SmallTalkCache.CacheStats stats = SmallTalkCache.getStats();
        assertEquals(1, stats.size, "Should have 1 entry");
        assertEquals(3, stats.totalHits, "Should have 3 hits");
        assertEquals(query, stats.mostUsedQuery, "Most used query should match");
        assertEquals(3, stats.mostUsedHits, "Most used hits should be 3");
    }
}
