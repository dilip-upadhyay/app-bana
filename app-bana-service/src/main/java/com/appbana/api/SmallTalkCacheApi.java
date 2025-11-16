package com.appbana.api;

import com.appbana.ai.SmallTalkCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * API handlers for SmallTalkCache management
 */
public class SmallTalkCacheApi {
    private static final Logger LOG = LoggerFactory.getLogger(SmallTalkCacheApi.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * GET /api/ai/smalltalk-cache/stats
     * Returns cache statistics
     */
    public static void handleGetStats(HttpExchange exchange) throws IOException {
        SmallTalkCache.CacheStats stats = SmallTalkCache.getStats();
        
        String json = MAPPER.writeValueAsString(Map.of(
            "success", true,
            "stats", stats
        ));
        
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, json.getBytes(StandardCharsets.UTF_8).length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
        
        LOG.info("[SmallTalkCacheApi] Returned cache stats: size={}, totalHits={}", stats.size, stats.totalHits);
    }

    /**
     * POST /api/ai/smalltalk-cache/clear
     * Clears all cache entries
     */
    public static void handleClearCache(HttpExchange exchange) throws IOException {
        SmallTalkCache.clear();
        
        String json = MAPPER.writeValueAsString(Map.of(
            "success", true,
            "message", "SmallTalk cache cleared successfully"
        ));
        
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, json.getBytes(StandardCharsets.UTF_8).length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
        
        LOG.info("[SmallTalkCacheApi] Cache cleared");
    }

    /**
     * DELETE /api/ai/smalltalk-cache/entry
     * Removes a specific cache entry
     * Request body: { "text": "query to remove" }
     */
    public static void handleRemoveEntry(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> request = MAPPER.readValue(body, Map.class);
            String text = (String) request.get("text");
            
            if (text == null || text.isBlank()) {
                sendError(exchange, 400, "Missing required field: text");
                return;
            }
            
            SmallTalkCache.remove(text);
            
            String json = MAPPER.writeValueAsString(Map.of(
                "success", true,
                "message", "Cache entry removed successfully"
            ));
            
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
            
            LOG.info("[SmallTalkCacheApi] Removed cache entry: {}", text);
        } catch (Exception e) {
            LOG.error("[SmallTalkCacheApi] Error removing cache entry", e);
            sendError(exchange, 500, "Failed to remove cache entry: " + e.getMessage());
        }
    }

    private static void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        String json = MAPPER.writeValueAsString(Map.of(
            "success", false,
            "error", message
        ));
        
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, json.getBytes(StandardCharsets.UTF_8).length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }
}
