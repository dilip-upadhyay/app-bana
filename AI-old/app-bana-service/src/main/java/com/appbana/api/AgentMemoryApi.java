package com.appbana.api;

import com.appbana.ai.AgentMemoryService;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    // BiConsumer-based handlers for Router
    public static java.util.function.BiConsumer<Router.HttpRequest, Router.HttpResponse> memoryHandler() {
        return (req, res) -> {
            String userId = req.query("userId");
            if (userId == null) userId = "default";
            var history = AgentMemoryService.getHistory(userId);
            res.json(200, history);
        };
    }

    public static java.util.function.BiConsumer<Router.HttpRequest, Router.HttpResponse> clearMemoryHandler() {
        return (req, res) -> {
            String userId = req.query("userId");
            if (userId == null) userId = "default";
            AgentMemoryService.clearHistory(userId);
            res.json(200, Map.of("ok", true));
        };
    }

    public static java.util.function.BiConsumer<Router.HttpRequest, Router.HttpResponse> preferencesHandler() {
        return (req, res) -> {
            String userId = req.query("userId");
            if (userId == null) userId = "default";
            var prefs = AgentMemoryService.getAllPreferences(userId);
            res.json(200, prefs);
        };
    }

    public static java.util.function.BiConsumer<Router.HttpRequest, Router.HttpResponse> setPreferenceHandler() {
        return (req, res) -> {
            String userId = req.query("userId");
            if (userId == null) userId = "default";
            Map<String, Object> body = req.readJson(new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            String key = (String) body.get("key");
            Object value = body.get("value");
            AgentMemoryService.setPreference(userId, key, value);
            res.json(200, Map.of("ok", true));
        };
    }

    public static java.util.function.BiConsumer<Router.HttpRequest, Router.HttpResponse> feedbackHandler() {
        return (req, res) -> {
            String userId = req.query("userId");
            if (userId == null) userId = "default";
            var feedback = AgentMemoryService.getFeedback(userId);
            res.json(200, feedback);
        };
    }

    public static java.util.function.BiConsumer<Router.HttpRequest, Router.HttpResponse> recordFeedbackHandler() {
        return (req, res) -> {
            String userId = req.query("userId");
            if (userId == null) userId = "default";
            Map<String, Object> body = req.readJson(new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            String input = (String) body.get("input");
            String response = (String) body.get("response");
            boolean positive = Boolean.TRUE.equals(body.get("positive"));
            String comment = (String) body.getOrDefault("comment", "");
            AgentMemoryService.recordFeedback(userId, input, response, positive, comment);
            res.json(200, Map.of("ok", true));
        };
    }

    // Old HttpHandler-based methods can be removed if not used elsewhere
}
