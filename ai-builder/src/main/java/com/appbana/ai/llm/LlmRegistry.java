package com.appbana.ai.llm;

import com.appbana.ai.config.AiConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for LLM Providers.
 * Manages instances of LlmService and allows dynamic switching.
 */
@Slf4j
public class LlmRegistry {

    public enum Provider {
        OPENAI,
        GEMINI
    }

    private final Map<Provider, LlmService> services = new ConcurrentHashMap<>();
    private Provider defaultProvider = Provider.OPENAI;

    public LlmRegistry(AiConfig config) {
        // Initialize available services
        services.put(Provider.OPENAI, new OpenAiLlmService(config));
        
        if (config.getGeminiApiKey() != null && !config.getGeminiApiKey().isEmpty()) {
            services.put(Provider.GEMINI, new GeminiLlmService(config));
            log.info("LlmRegistry: Gemini provider initialized and available");
        } else {
            log.warn("LlmRegistry: Gemini API key missing, only OpenAI will be available");
        }
    }

    /**
     * Get the service for a specific provider.
     * Falls back to default if provider is null or not available.
     */
    public LlmService getService(String providerName) {
        if (providerName == null || providerName.isEmpty()) {
            return services.get(defaultProvider);
        }

        try {
            Provider provider = Provider.valueOf(providerName.toUpperCase());
            LlmService service = services.get(provider);
            if (service != null) return service;
        } catch (IllegalArgumentException e) {
            log.warn("Unknown LLM provider requested: {}. Falling back to {}", providerName, defaultProvider);
        }

        return services.get(defaultProvider);
    }

    public LlmService getService(Provider provider) {
        return services.getOrDefault(provider, services.get(defaultProvider));
    }

    public void setDefaultProvider(Provider provider) {
        this.defaultProvider = provider;
        log.info("Default LLM provider set to: {}", provider);
    }
}
