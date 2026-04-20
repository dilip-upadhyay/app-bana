package com.appbana.ai.llm;

import com.appbana.ai.cache.LlmCacheService;
import com.appbana.ai.config.AiConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.completion.chat.*;
import com.theokanning.openai.service.OpenAiService;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * OpenAI GPT-4 integration service with Hybrid Model Routing
 * Story: 4.1 - Implement GPT-4 Integration
 * Enhancement: Cost Optimization via ModelRouter
 */
@Slf4j
public class OpenAiLlmService implements LlmService, AutoCloseable {

    private static final String OPENAI_CHAT_URL = "https://api.openai.com/v1/chat/completions";

    private final AiConfig config;
    private final OpenAiService openAiService;
    private final LlmCacheService cacheService;
    private final ModelRouter modelRouter;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private boolean cacheEnabled = true; // Feature flag
    private boolean hybridModeEnabled = true; // Model switching feature flag

    public OpenAiLlmService(AiConfig config) {
        this.config = config;
        this.openAiService = new OpenAiService(config.getOpenaiApiKey(), Duration.ofSeconds(60));
        this.cacheService = new LlmCacheService(100_000, Duration.ofHours(6));
        this.modelRouter = new ModelRouter(config.getOpenaiPremiumModel(), config.getOpenaiModel());
        this.hybridModeEnabled = config.isHybridModeEnabled();
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        this.objectMapper = new ObjectMapper();
        log.info("OpenAI LLM Service initialized - standard: {}, premium: {}, hybrid: {}, cache: enabled", 
                config.getOpenaiModel(), config.getOpenaiPremiumModel(), hybridModeEnabled);
    }

    /**
     * Enable or disable caching (for testing/debugging)
     */
    public void setCacheEnabled(boolean enabled) {
        this.cacheEnabled = enabled;
        log.info("LLM cache {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Enable or disable hybrid model routing
     */
    public void setHybridModeEnabled(boolean enabled) {
        this.hybridModeEnabled = enabled;
        log.info("Hybrid model routing {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Get the model router for external configuration
     */
    public ModelRouter getModelRouter() {
        return modelRouter;
    }

    /**
     * Get cache metrics
     */
    public String getCacheMetrics() {
        return cacheService.getMetrics();
    }

    @Override
    public String chat(String prompt) throws Exception {
        return chatWithOptions(prompt, null, null, null);
    }

    @Override
    public String chat(String prompt, List<String> images) throws Exception {
        return chatWithOptions(prompt, null, null, images);
    }

    @Override
    public String chat(String prompt, String taskType) throws Exception {
        return chatWithOptions(prompt, taskType, null, null);
    }

    @Override
    public String chat(String prompt, List<String> images, String taskType) throws Exception {
        return chatWithOptions(prompt, taskType, null, images);
    }

    @Override
    public String chatWithJsonMode(String prompt) throws Exception {
        return executeChatRequest(prompt, modelRouter.getStandardModel(), true, null);
    }

    @Override
    public String chatWithJsonMode(String prompt, List<String> images) throws Exception {
        // vision models usually require premium models (gpt-4o)
        return executeChatRequest(prompt, modelRouter.getPremiumModel(), true, images);
    }

    @Override
    public String chatStructured(String prompt, String schemaName, String schema) throws Exception {
        // Using GPT-4o for structured outputs
        String model = modelRouter.getPremiumModel();
        return executeChatRequest(prompt + "\n\nSchema:\n" + schema, model, true, null);
    }

    @Override
    public String chatWithOptions(String prompt, String taskType, Map<String, Object> options, List<String> images) throws Exception {
        String model = hybridModeEnabled && taskType != null ? modelRouter.selectModel(taskType) : config.getOpenaiModel();
        boolean jsonMode = (taskType != null && taskType.contains("json")) || (options != null && options.containsKey("schema"));
        return executeChatRequest(prompt, model, jsonMode, images);
    }

    private String executeChatRequest(String prompt, String model, boolean jsonMode, List<String> images) throws Exception {
        if (config.getOpenaiApiKey() == null) {
            throw new LlmException("OpenAI API key not configured");
        }

        // Construct Content (Multimodal support)
        Object content;
        if (images == null || images.isEmpty()) {
            content = prompt;
        } else {
            List<Map<String, Object>> contentList = new ArrayList<>();
            contentList.add(Map.of("type", "text", "text", prompt));
            
            for (String base64Image : images) {
                // Ensure correct data URI format
                String dataUri = base64Image.startsWith("data:") ? base64Image : "data:image/png;base64," + base64Image;
                contentList.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", dataUri, "detail", "auto")
                ));
            }
            content = contentList;
        }

        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", content);

        return callOpenAiDirectly(message, model, jsonMode);
    }

    private String callOpenAiDirectly(Map<String, Object> message, String model, boolean jsonMode)
            throws Exception {
        int maxRetries = 3;
        long backoffMillis = 2000;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return executeOpenAiHttpRequest(message, model, jsonMode);
            } catch (RateLimitException e) {
                if (attempt == maxRetries) throw new LlmException("Rate limit after " + maxRetries + " retries");
                log.warn("[Direct] Rate limit (429). Retry {}/{} in {}ms", attempt + 1, maxRetries, backoffMillis);
                backoffMillis = sleepAndDouble(backoffMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LlmException("Interrupted during HTTP call", e);
            } catch (Exception e) {
                if (attempt == maxRetries) throw new LlmException("Direct HTTP call failed: " + e.getMessage(), e);
                log.warn("[Direct] Attempt {}/{} failed: {}. Retrying in {}ms",
                        attempt + 1, maxRetries, e.getMessage(), backoffMillis);
                backoffMillis = sleepAndDouble(backoffMillis);
            }
        }
        throw new LlmException("Direct HTTP call failed after all retries");
    }

    @SuppressWarnings("unchecked")
    private String executeOpenAiHttpRequest(Map<String, Object> message, String model, boolean jsonMode)
            throws LlmException, RateLimitException, java.io.IOException, InterruptedException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(message));
        body.put("temperature", config.getOpenaiTemperature());
        body.put("max_tokens", config.getOpenaiMaxTokens());
        
        if (jsonMode) {
            Map<String, Object> responseFormat = new HashMap<>();
            responseFormat.put("type", "json_object");
            body.put("response_format", responseFormat);
        }

        String requestJson = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OPENAI_CHAT_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.getOpenaiApiKey())
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (httpResponse.statusCode() == 429) throw new RateLimitException();
        if (httpResponse.statusCode() != 200) {
            throw new LlmException("OpenAI HTTP " + httpResponse.statusCode() + ": " + httpResponse.body());
        }

        Map<String, Object> parsed = objectMapper.readValue(httpResponse.body(), Map.class);
        List<Map<String, Object>> choices = (List<Map<String, Object>>) parsed.get("choices");
        if (choices == null || choices.isEmpty()) throw new LlmException("No choices in response");
        Map<String, Object> choiceMessage = (Map<String, Object>) choices.get(0).get("message");
        return (String) choiceMessage.get("content");
    }

    private long sleepAndDouble(long backoffMillis) throws Exception {
        try {
            Thread.sleep(backoffMillis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new LlmException("Interrupted during backoff", ie);
        }
        return backoffMillis * 2;
    }

    private static class RateLimitException extends Exception {
        RateLimitException() { super("rate_limit"); }
    }

    @Override
    public void close() {
        log.info("Closing OpenAI LLM Service");
    }

    public static class LlmException extends Exception {
        public LlmException(String message) { super(message); }
        public LlmException(String message, Throwable cause) { super(message, cause); }
    }
}
