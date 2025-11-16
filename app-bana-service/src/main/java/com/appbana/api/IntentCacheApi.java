package com.appbana.api;

import com.appbana.ai.IntentCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * API endpoints for IntentCache management
 */
public class IntentCacheApi {
    private static final Logger LOG = LoggerFactory.getLogger(IntentCacheApi.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * GET /api/ai/cache/stats - Get cache statistics
     */
    public static void handleGetStats(HttpExchange exchange) throws IOException {
        try {
            IntentCache.CacheStats stats = IntentCache.getStats();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("stats", stats);
            
            String json = MAPPER.writeValueAsString(response);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(json.getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().close();
            
            LOG.info("[IntentCacheApi] Returned cache stats: {} entries, {} hits", 
                stats.totalEntries, stats.totalHits);
        } catch (Exception e) {
            LOG.error("[IntentCacheApi] Failed to get cache stats", e);
            sendError(exchange, 500, "Failed to get cache stats: " + e.getMessage());
        }
    }

    /**
     * POST /api/ai/cache/clear - Clear entire cache
     */
    public static void handleClearCache(HttpExchange exchange) throws IOException {
        try {
            IntentCache.clear();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Cache cleared successfully");
            
            String json = MAPPER.writeValueAsString(response);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(json.getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().close();
            
            LOG.info("[IntentCacheApi] Cache cleared");
        } catch (Exception e) {
            LOG.error("[IntentCacheApi] Failed to clear cache", e);
            sendError(exchange, 500, "Failed to clear cache: " + e.getMessage());
        }
    }

    /**
     * DELETE /api/ai/cache/entry?text=... - Remove specific cache entry
     */
    public static void handleRemoveEntry(HttpExchange exchange) throws IOException {
        try {
            String query = exchange.getRequestURI().getQuery();
            if (query == null || !query.startsWith("text=")) {
                sendError(exchange, 400, "Missing 'text' query parameter");
                return;
            }
            
            String text = java.net.URLDecoder.decode(query.substring(5), StandardCharsets.UTF_8);
            IntentCache.remove(text);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Entry removed successfully");
            
            String json = MAPPER.writeValueAsString(response);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(json.getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().close();
            
            LOG.info("[IntentCacheApi] Removed cache entry: {}", text);
        } catch (Exception e) {
            LOG.error("[IntentCacheApi] Failed to remove cache entry", e);
            sendError(exchange, 500, "Failed to remove cache entry: " + e.getMessage());
        }
    }

    private static void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        
        String json = MAPPER.writeValueAsString(error);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, json.getBytes(StandardCharsets.UTF_8).length);
        exchange.getResponseBody().write(json.getBytes(StandardCharsets.UTF_8));
        exchange.getResponseBody().close();
    }
}
