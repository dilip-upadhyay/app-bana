package com.appbana.server.routes;

import com.appbana.AiAppGeneratorService;
import com.appbana.ai.*;
import com.appbana.api.Router;
import com.appbana.config.AppConfig;
import com.appbana.config.ConfigManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * AI generation and processing routes
 */
public class AiRoutes {
    private static final Logger LOG = LoggerFactory.getLogger(AiRoutes.class);
    private static final ObjectMapper M = new ObjectMapper();

    public static void register(Router router) {
        // AI-powered app generation
        router.post("/api/ai/generate", (req, res) -> {
            try {
                AiAppGeneratorService.GenerationRequest genReq = req.readJson(new TypeReference<>() {
                });

                // Allow action-only requests (e.g., { action: "listApps" })
                if ((genReq.description == null || genReq.description.trim().isEmpty())
                        && (genReq.action == null || genReq.action.trim().isEmpty())) {
                    res.json(400, Map.of("error", "description is required"));
                    return;
                }

                AiAppGeneratorService.GenerationResult result = AiAppGeneratorService.generateApp(genReq);
                res.json(200, result);
            } catch (Exception e) {
                LOG.error("AI generation failed", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // Generate App Theme
        router.post("/api/ai/theme-generate", (req, res) -> {
            try {
                Map<String, String> body = req.readJson(new TypeReference<>() {
                });
                String desc = body.get("description");
                if (desc == null || desc.isBlank()) {
                    res.json(400, Map.of("error", "Description is required"));
                    return;
                }

                Map<String, Object> theme = AiThemeService.generateTheme(desc);
                res.json(200, theme);
            } catch (Exception e) {
                LOG.error("Theme generation failed", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // Generate Seed Data
        router.post("/api/ai/seed-data", (req, res) -> {
            try {
                Map<String, Object> body = req.readJson(new TypeReference<>() {
                });
                String entityName = (String) body.get("entityName");
                String schema = M.writeValueAsString(body.get("schema"));
                Integer count = (Integer) body.getOrDefault("count", 5);

                if (entityName == null || schema == null) {
                    res.json(400, Map.of("error", "Entity Name and Schema are required"));
                    return;
                }

                List<Map<String, Object>> data = DataSeederService.generateData(entityName, schema, count);
                res.json(200, data);
            } catch (Exception e) {
                LOG.error("Data seeding failed", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // Get AI configuration
        router.get("/api/ai/config", (req, res) -> {
            try {
                AppConfig config = ConfigManager.getConfig();
                Map<String, Object> aiConfig = Map.of(
                        "provider", config.getAiProvider() != null ? config.getAiProvider() : "",
                        "openaiModel", config.getOpenaiModel(),
                        "anthropicModel", config.getAnthropicModel(),
                        "ollamaUrl", config.getOllamaUrl(),
                        "ollamaModel", config.getOllamaModel(),
                        "isEnabled", AiProviderFactory.isAiEnabled(config),
                        "hasOpenaiKey", config.getOpenaiApiKey() != null && !config.getOpenaiApiKey().isEmpty(),
                        "hasAnthropicKey",
                        config.getAnthropicApiKey() != null && !config.getAnthropicApiKey().isEmpty());
                res.json(200, aiConfig);
            } catch (Exception e) {
                LOG.error("Failed to get AI config", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // Update AI configuration
        router.put("/api/ai/config", (req, res) -> {
            try {
                Map<String, Object> updates = req.readJson(new TypeReference<>() {
                });
                AppConfig config = ConfigManager.getConfig();

                if (updates.containsKey("provider")) {
                    config.setAiProvider((String) updates.get("provider"));
                }
                if (updates.containsKey("openaiApiKey")) {
                    config.setOpenaiApiKey((String) updates.get("openaiApiKey"));
                }
                if (updates.containsKey("openaiModel")) {
                    config.setOpenaiModel((String) updates.get("openaiModel"));
                }
                if (updates.containsKey("anthropicApiKey")) {
                    config.setAnthropicApiKey((String) updates.get("anthropicApiKey"));
                }
                if (updates.containsKey("anthropicModel")) {
                    config.setAnthropicModel((String) updates.get("anthropicModel"));
                }
                if (updates.containsKey("ollamaUrl")) {
                    config.setOllamaUrl((String) updates.get("ollamaUrl"));
                }
                if (updates.containsKey("ollamaModel")) {
                    config.setOllamaModel((String) updates.get("ollamaModel"));
                }

                ConfigManager.saveConfig(config);
                res.json(200, Map.of("success", true, "message", "AI configuration updated"));
            } catch (Exception e) {
                LOG.error("Failed to update AI config", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // Test AI connection
        router.post("/api/ai/test", (req, res) -> {
            try {
                AppConfig config = ConfigManager.getConfig();

                if (!AiProviderFactory.isAiEnabled(config)) {
                    res.json(400, Map.of("success", false, "message", "AI provider not configured"));
                    return;
                }

                AiProvider provider = AiProviderFactory.createProvider(config);
                boolean connected = provider.testConnection();

                res.json(200, Map.of(
                        "success", connected,
                        "provider", provider.getProviderName(),
                        "message", connected ? "Connection successful" : "Connection failed"));
            } catch (Exception e) {
                LOG.error("AI connection test failed", e);
                res.json(500, Map.of("success", false, "message", e.getMessage()));
            }
        });

        // List available AI providers
        router.get("/api/ai/providers", (req, res) -> {
            List<Map<String, Object>> providers = List.of(
                    Map.of(
                            "id", "openai",
                            "name", "OpenAI",
                            "description", "GPT-4 and other OpenAI models (requires API key)",
                            "models", List.of("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-3.5-turbo")),
                    Map.of(
                            "id", "anthropic",
                            "name", "Anthropic",
                            "description", "Claude 3.5 Sonnet and other Anthropic models (requires API key)",
                            "models", List.of("claude-3-5-sonnet-20241022", "claude-3-opus-20240229",
                                    "claude-3-sonnet-20240229", "claude-3-haiku-20240307")),
                    Map.of(
                            "id", "ollama",
                            "name", "Ollama",
                            "description", "Local AI models (requires Ollama installation)",
                            "models", List.of("llama3.1", "llama3.2", "mistral", "codellama", "phi3")));
            res.json(200, providers);
        });

        // Intent Cache Stats
        router.get("/api/ai/cache/stats", (req, res) -> {
            try {
                IntentCache.CacheStats stats = IntentCache.getStats();
                res.json(200, Map.of("success", true, "stats", stats));
            } catch (Exception e) {
                LOG.error("Failed to get cache stats", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // Clear Intent Cache
        router.post("/api/ai/cache/clear", (req, res) -> {
            try {
                IntentCache.clear();
                res.json(200, Map.of("success", true, "message", "Cache cleared successfully"));
            } catch (Exception e) {
                LOG.error("Failed to clear cache", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // Remove Cache Entry
        router.delete("/api/ai/cache/entry", (req, res) -> {
            try {
                String text = req.query("text");
                if (text == null || text.isEmpty()) {
                    res.json(400, Map.of("error", "Missing 'text' query parameter"));
                    return;
                }
                IntentCache.remove(text);
                res.json(200, Map.of("success", true, "message", "Entry removed successfully"));
            } catch (Exception e) {
                LOG.error("Failed to remove cache entry", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // SmallTalk Cache Stats
        router.get("/api/ai/smalltalk-cache/stats", (req, res) -> {
            try {
                SmallTalkCache.CacheStats stats = SmallTalkCache.getStats();
                res.json(200, Map.of("success", true, "stats", stats));
            } catch (Exception e) {
                LOG.error("Failed to get smalltalk cache stats", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // Clear SmallTalk Cache
        router.post("/api/ai/smalltalk-cache/clear", (req, res) -> {
            try {
                SmallTalkCache.clear();
                res.json(200, Map.of("success", true, "message", "SmallTalk cache cleared successfully"));
            } catch (Exception e) {
                LOG.error("Failed to clear smalltalk cache", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // Remove SmallTalk Cache Entry
        router.delete("/api/ai/smalltalk-cache/entry", (req, res) -> {
            try {
                String text = req.query("text");
                if (text == null || text.isEmpty()) {
                    res.json(400, Map.of("error", "Missing 'text' query parameter"));
                    return;
                }
                SmallTalkCache.remove(text);
                res.json(200, Map.of("success", true, "message", "SmallTalk entry removed successfully"));
            } catch (Exception e) {
                LOG.error("Failed to remove smalltalk cache entry", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // Agent memory endpoints
        router.get("/api/agent/memory", com.appbana.api.AgentMemoryApi.memoryHandler());
        router.post("/api/agent/memory/clear", com.appbana.api.AgentMemoryApi.clearMemoryHandler());
        router.get("/api/agent/preferences", com.appbana.api.AgentMemoryApi.preferencesHandler());
        router.post("/api/agent/preferences", com.appbana.api.AgentMemoryApi.setPreferenceHandler());
        router.get("/api/agent/feedback", com.appbana.api.AgentMemoryApi.feedbackHandler());
        router.post("/api/agent/feedback", com.appbana.api.AgentMemoryApi.recordFeedbackHandler());

        // Metadata intelligence endpoints
        router.post("/api/meta-intelligence/reload", com.appbana.api.MetadataIntelligenceApi.reloadHandler());
        router.get("/api/meta-intelligence/classify", com.appbana.api.MetadataIntelligenceApi.classifyHandler());
    }
}
