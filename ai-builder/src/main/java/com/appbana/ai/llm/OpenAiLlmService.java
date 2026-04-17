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
public class OpenAiLlmService implements AutoCloseable {

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

    public String chat(String prompt) throws LlmException {
        return chatWithOptions(prompt, null, null);
    }

    /**
     * Chat with task-type awareness for model routing
     */
    public String chat(String prompt, String taskType) throws LlmException {
        return chatWithOptions(prompt, taskType, null);
    }

    public String chat(String prompt, Map<String, Object> options) throws LlmException {
        String taskType = options != null ? (String) options.get("taskType") : null;
        return chatWithOptions(prompt, taskType, options);
    }

    /**
     * Core chat method with hybrid model selection
     */
    private String chatWithOptions(String prompt, String taskType, Map<String, Object> options) throws LlmException {
        double temperature = 0.7;
        
        // Select model based on task type (hybrid mode)
        String model;
        if (hybridModeEnabled && taskType != null) {
            model = modelRouter.selectModel(taskType);
            log.debug("[Hybrid] Task '{}' -> Model '{}'", taskType, model);
        } else if (hybridModeEnabled) {
            model = modelRouter.selectModelForPrompt(prompt, null);
            log.debug("[Hybrid] Prompt analysis -> Model '{}'", model);
        } else {
            model = config.getOpenaiModel();
        }

        // Check cache first (if enabled)
        if (cacheEnabled) {
            var cached = cacheService.get(prompt, model, temperature);
            if (cached.isPresent()) {
                log.info("[Cache HIT] Returning cached response for prompt (length: {})", prompt.length());
                return cached.get();
            }
            log.debug("[Cache MISS] Calling OpenAI API");
        }

        try {
            log.debug("Sending chat request to OpenAI");

            ChatMessage message = new ChatMessage(ChatMessageRole.USER.value(), prompt);

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(model)
                    .messages(List.of(message))
                    .temperature(temperature)
                    .maxTokens(config.getOpenaiMaxTokens())
                    .build();

            int maxRetries = 5;
            long backoffMillis = 2000;

            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                try {
                    ChatCompletionResult result = openAiService.createChatCompletion(request);

                    if (result.getChoices() == null || result.getChoices().isEmpty()) {
                        throw new LlmException("No response from OpenAI");
                    }

                    String response = result.getChoices().get(0).getMessage().getContent();

                    log.debug("Received response from OpenAI ({} tokens)",
                            result.getUsage().getTotalTokens());

                    // Cache the response (if enabled)
                    if (cacheEnabled) {
                        cacheService.put(prompt, model, temperature, response);
                        log.debug("[Cache PUT] Cached response for future requests");
                    }

                    return response;

                } catch (com.theokanning.openai.OpenAiHttpException e) {
                    if (e.statusCode == 429) {
                        if (attempt == maxRetries) {
                            log.error("Rate limit exceeded. Max retries reached.");
                            throw new LlmException("Rate limit exceeded after " + maxRetries + " retries", e);
                        }
                        log.warn("Rate limit exceeded (429). Retrying in {} ms (Attempt {}/{})", backoffMillis,
                                attempt + 1, maxRetries);
                        try {
                            Thread.sleep(backoffMillis);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new LlmException("Interrupted during backoff", ie);
                        }
                        backoffMillis *= 2; // Exponential backoff
                    } else {
                        log.error("OpenAI API error: {}", e.statusCode, e);
                        throw new LlmException("OpenAI API error: " + e.statusCode, e);
                    }
                } catch (Exception e) {
                    // Start checking for rate limit in cause chain if wrapped
                    if (isRateLimit(e)) {
                        if (attempt == maxRetries) {
                            log.error("Rate limit exceeded (wrapped). Max retries reached.");
                            throw new LlmException("Rate limit exceeded after " + maxRetries + " retries", e);
                        }
                        log.warn("Rate limit exceeded (wrapped). Retrying in {} ms (Attempt {}/{})", backoffMillis,
                                attempt + 1, maxRetries);
                        try {
                            Thread.sleep(backoffMillis);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new LlmException("Interrupted during backoff", ie);
                        }
                        backoffMillis *= 2;
                    } else {
                        log.error("Failed to get chat completion from OpenAI", e);
                        throw new LlmException("Failed to get chat completion", e);
                    }
                }
            }
            throw new LlmException("Failed to get response after retries");
        } catch (Exception e) {
            log.error("Unexpected error in chat", e);
            throw new LlmException("Unexpected error", e);
        }
    }

    private boolean isRateLimit(Throwable t) {
        if (t instanceof com.theokanning.openai.OpenAiHttpException) {
            return ((com.theokanning.openai.OpenAiHttpException) t).statusCode == 429;
        }
        if (t.getCause() != null && t.getCause() != t) {
            return isRateLimit(t.getCause());
        }
        return false;
    }

    /**
     * Chat with JSON mode enforced (response_format: json_object).
     * Guarantees syntactically valid JSON output from the model.
     * The prompt MUST instruct the model to return JSON (OpenAI requirement).
     *
     * Uses direct HTTP because theokanning/openai-gpt3-java v0.18.2 does not
     * support response_format.
     */
    public String chatWithJsonMode(String prompt) throws LlmException {
        String model = hybridModeEnabled
                ? modelRouter.selectModel("agent_think")
                : config.getOpenaiModel();

        if (cacheEnabled) {
            var cached = cacheService.get(prompt, model + ":json_mode", 0.7);
            if (cached.isPresent()) {
                log.info("[ChatJsonMode] Cache HIT");
                return cached.get();
            }
        }

        Map<String, Object> responseFormat = Map.of("type", "json_object");
        String response = callOpenAiDirectly(prompt, model, responseFormat);

        if (cacheEnabled) {
            cacheService.put(prompt, model + ":json_mode", 0.7, response);
        }
        return response;
    }

    /**
     * Chat with strict JSON schema (response_format: json_schema, strict: true).
     * The model is mathematically constrained to only output tokens that conform
     * to the provided JSON schema — invalid field types become structurally
     * impossible.
     *
     * Uses direct HTTP because theokanning/openai-gpt3-java v0.18.2 does not
     * support response_format.
     *
     * @param prompt     the user/system prompt
     * @param schemaName a short identifier for the schema (e.g. "ScaffoldSpec")
     * @param schema     JSON Schema string the response must conform to
     */
    public String chatStructured(String prompt, String schemaName, String schema) throws LlmException {
        // Structured outputs require gpt-4o or gpt-4o-mini (not older models)
        String model = config.getOpenaiPremiumModel();

        try {
            Object parsedSchema = objectMapper.readValue(schema, Object.class);
            Map<String, Object> jsonSchema = new LinkedHashMap<>();
            jsonSchema.put("name", schemaName);
            jsonSchema.put("strict", true);
            jsonSchema.put("schema", parsedSchema);

            Map<String, Object> responseFormat = Map.of(
                    "type", "json_schema",
                    "json_schema", jsonSchema);

            return callOpenAiDirectly(prompt, model, responseFormat);
        } catch (Exception e) {
            throw new LlmException("Failed structured chat: " + e.getMessage(), e);
        }
    }

    /**
     * Low-level direct HTTP call to OpenAI chat completions endpoint.
     * Supports any response_format map, including json_object and json_schema.
     */
    private String callOpenAiDirectly(String prompt, String model, Map<String, Object> responseFormat)
            throws LlmException {
        int maxRetries = 5;
        long backoffMillis = 2000;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return executeOpenAiHttpRequest(prompt, model, responseFormat);
            } catch (RateLimitException e) {
                if (attempt == maxRetries) throw new LlmException("Rate limit after " + maxRetries + " retries");
                log.warn("[Direct] Rate limit (429). Retry {}/{} in {}ms", attempt + 1, maxRetries, backoffMillis);
                backoffMillis = sleepAndDouble(backoffMillis);
            } catch (LlmException e) {
                throw e;
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
    private String executeOpenAiHttpRequest(String prompt, String model, Map<String, Object> responseFormat)
            throws LlmException, RateLimitException, java.io.IOException, InterruptedException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        body.put("temperature", config.getOpenaiTemperature());
        body.put("max_tokens", config.getOpenaiMaxTokens());
        body.put("response_format", responseFormat);

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
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        return (String) message.get("content");
    }

    private long sleepAndDouble(long backoffMillis) throws LlmException {
        try {
            Thread.sleep(backoffMillis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new LlmException("Interrupted during backoff", ie);
        }
        return backoffMillis * 2;
    }

    /** Sentinel exception used internally to signal HTTP 429 without losing context. */
    private static class RateLimitException extends Exception {
        RateLimitException() { super("rate_limit"); }
    }

    @Override
    public void close() {
        log.info("Closing OpenAI LLM Service");
        // OpenAiService doesn't need explicit closing
    }

    public static class LlmException extends Exception {
        public LlmException(String message) {
            super(message);
        }

        public LlmException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
