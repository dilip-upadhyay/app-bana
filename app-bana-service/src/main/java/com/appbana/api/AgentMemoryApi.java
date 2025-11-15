package com.appbana.api;

import com.appbana.ai.AgentMemoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.Map;

/**
 * REST API for agent memory: history, preferences, feedback
 * Endpoints:
 *   GET    /api/agent/memory?userId=...         - get conversation history
 *   POST   /api/agent/memory/clear?userId=...   - clear history
 *   GET    /api/agent/preferences?userId=...    - get all preferences
 *   POST   /api/agent/preferences?userId=...    - set preference (key, value)
 *   GET    /api/agent/feedback?userId=...       - get feedback
 *   POST   /api/agent/feedback?userId=...       - record feedback (input, response, positive, comment)
 */
public class AgentMemoryApi {
    private static final ObjectMapper M = new ObjectMapper();

    public static HttpHandler getMemoryHandler() {
        return exchange -> {
            String userId = getQueryParam(exchange, "userId", "default");
            var history = AgentMemoryService.getHistory(userId);
            ApiServer.sendJson(exchange, 200, history);
        };
    }

    public static HttpHandler getClearMemoryHandler() {
        return exchange -> {
            String userId = getQueryParam(exchange, "userId", "default");
            AgentMemoryService.clearHistory(userId);
            ApiServer.sendJson(exchange, 200, Map.of("ok", true));
        };
    }

    public static HttpHandler getPreferencesHandler() {
        return exchange -> {
            String userId = getQueryParam(exchange, "userId", "default");
            var prefs = AgentMemoryService.getAllPreferences(userId);
            ApiServer.sendJson(exchange, 200, prefs);
        };
    }

    public static HttpHandler setPreferenceHandler() {
        return exchange -> {
            String userId = getQueryParam(exchange, "userId", "default");
            Map<String, Object> body = M.readValue(exchange.getRequestBody(), Map.class);
            String key = (String) body.get("key");
            Object value = body.get("value");
            AgentMemoryService.setPreference(userId, key, value);
            ApiServer.sendJson(exchange, 200, Map.of("ok", true));
        };
    }

    public static HttpHandler getFeedbackHandler() {
        return exchange -> {
            String userId = getQueryParam(exchange, "userId", "default");
            var feedback = AgentMemoryService.getFeedback(userId);
            ApiServer.sendJson(exchange, 200, feedback);
        };
    }

    public static HttpHandler recordFeedbackHandler() {
        return exchange -> {
            String userId = getQueryParam(exchange, "userId", "default");
            Map<String, Object> body = M.readValue(exchange.getRequestBody(), Map.class);
            String input = (String) body.get("input");
            String response = (String) body.get("response");
            boolean positive = Boolean.TRUE.equals(body.get("positive"));
            String comment = (String) body.getOrDefault("comment", "");
            AgentMemoryService.recordFeedback(userId, input, response, positive, comment);
            ApiServer.sendJson(exchange, 200, Map.of("ok", true));
        };
    }

    private static String getQueryParam(HttpExchange exchange, String key, String def) {
        String query = exchange.getRequestURI().getQuery();
        if (query == null) return def;
        for (String part : query.split("&")) {
            int i = part.indexOf('=');
            if (i > 0 && part.substring(0, i).equals(key)) {
                return part.substring(i + 1);
            }
        }
        return def;
    }
}
