package com.appbana.ai;

import com.appbana.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating AI provider instances based on configuration
 */
public class AiProviderFactory {
    private static final Logger LOG = LoggerFactory.getLogger(AiProviderFactory.class);
    
    /**
     * Create AI provider based on app configuration
     * 
     * @param config Application configuration
     * @return Configured AI provider
     * @throws IllegalArgumentException if provider is not configured or invalid
     */
    public static AiProvider createProvider(AppConfig config) {
        if (config.getAiProvider() == null || config.getAiProvider().isBlank()) {
            throw new IllegalArgumentException("AI provider not configured. Set aiProvider in config.json");
        }
        
        String provider = config.getAiProvider().toLowerCase();
        
        switch (provider) {
            case "openai":
                String openaiKey = config.getOpenaiApiKey();
                if (openaiKey == null || openaiKey.isBlank()) {
                    throw new IllegalArgumentException("OpenAI API key not configured. Set openaiApiKey in config.json or OPENAI_API_KEY environment variable");
                }
                LOG.info("Using OpenAI provider with model: {}", config.getOpenaiModel());
                return new OpenAiProvider(openaiKey, config.getOpenaiModel());
                
            case "anthropic":
                String anthropicKey = config.getAnthropicApiKey();
                if (anthropicKey == null || anthropicKey.isBlank()) {
                    throw new IllegalArgumentException("Anthropic API key not configured. Set anthropicApiKey in config.json or ANTHROPIC_API_KEY environment variable");
                }
                LOG.info("Using Anthropic provider with model: {}", config.getAnthropicModel());
                return new AnthropicProvider(anthropicKey, config.getAnthropicModel());
                
            case "ollama":
                LOG.info("Using Ollama provider at {} with model: {}", config.getOllamaUrl(), config.getOllamaModel());
                return new OllamaProvider(config.getOllamaUrl(), config.getOllamaModel());
                
            default:
                throw new IllegalArgumentException("Unknown AI provider: " + provider + ". Supported: openai, anthropic, ollama");
        }
    }
    
    /**
     * Check if AI is enabled in configuration
     */
    public static boolean isAiEnabled(AppConfig config) {
        return config.getAiProvider() != null && !config.getAiProvider().isBlank();
    }
}
