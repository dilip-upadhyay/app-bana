package com.appbana.api;

import com.appbana.ai.MetadataIntelligenceEngine;
import com.appbana.api.Router;

import java.util.HashMap;
import java.util.Map;

/**
 * API endpoints for managing metadata intelligence patterns
 */
public class MetadataIntelligenceApi {
    
    /**
     * POST /api/meta-intelligence/reload
     * Reload intent patterns from disk without restart
     */
    public static java.util.function.BiConsumer<Router.HttpRequest, Router.HttpResponse> reloadHandler() {
        return (req, res) -> {
            try {
                MetadataIntelligenceEngine.reload();
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Intent patterns reloaded successfully");
                
                res.json(200, response);
                
            } catch (Exception e) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Failed to reload patterns: " + e.getMessage());
                res.json(500, error);
            }
        };
    }
    
    /**
     * GET /api/meta-intelligence/classify?input=xxx
     * Test intent classification
     */
    public static java.util.function.BiConsumer<Router.HttpRequest, Router.HttpResponse> classifyHandler() {
        return (req, res) -> {
            String input = req.query("input");
            
            if (input == null || input.isEmpty()) {
                res.json(400, Map.of("error", "Missing 'input' parameter"));
                return;
            }
            
            try {
                MetadataIntelligenceEngine.IntentResult result = 
                    MetadataIntelligenceEngine.classifyIntent(input, new HashMap<>());
                
                Map<String, Object> response = new HashMap<>();
                response.put("input", input);
                response.put("intent", result.intent);
                response.put("confidence", result.confidence);
                response.put("explanation", result.explanation);
                
                if (result.definition != null) {
                    Map<String, Object> def = new HashMap<>();
                    def.put("id", result.definition.id);
                    def.put("name", result.definition.name);
                    def.put("threshold", result.definition.confidence_threshold);
                    response.put("definition", def);
                }
                
                res.json(200, response);
                
            } catch (Exception e) {
                res.json(500, Map.of("error", "Classification failed: " + e.getMessage()));
            }
        };
    }
}
