package com.appbana.ai.llm;

import com.appbana.ai.cache.LlmCacheService;
import com.appbana.ai.config.AiConfig;
import com.theokanning.openai.completion.chat.*;
import com.theokanning.openai.service.OpenAiService;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.*;

/**
 * OpenAI GPT-4 integration service with Hybrid Model Routing
 * Story: 4.1 - Implement GPT-4 Integration
 * Enhancement: Cost Optimization via ModelRouter
 */
@Slf4j
public class OpenAiLlmService implements AutoCloseable {

    private final AiConfig config;
    private final OpenAiService openAiService;
    private final LlmCacheService cacheService;
    private final ModelRouter modelRouter;
    private boolean cacheEnabled = true; // Feature flag
    private boolean hybridModeEnabled = true; // Model switching feature flag

    public OpenAiLlmService(AiConfig config) {
        this.config = config;
        this.openAiService = new OpenAiService(config.getOpenaiApiKey(), Duration.ofSeconds(60));
        this.cacheService = new LlmCacheService(100_000, Duration.ofHours(6));
        this.modelRouter = new ModelRouter(config.getOpenaiPremiumModel(), config.getOpenaiModel());
        this.hybridModeEnabled = config.isHybridModeEnabled();
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
