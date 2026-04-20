package com.appbana.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration for AI Builder Service
 */
@Data
public class AiConfig {
    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    // Server configuration
    private int port = 8081;
    private String host = "0.0.0.0";

    // OpenAI configuration
    private String openaiApiKey;
    private String openaiModel = "gpt-4o-mini";           // Standard model (cost-effective)
    private String openaiPremiumModel = "gpt-4o";         // Premium model (high-quality)
    private String openaiEmbeddingModel = "text-embedding-3-small";
    private int openaiMaxTokens = 4000;                   // Increased for multimodal/complex tasks
    private double openaiTemperature = 0.7;
    private boolean hybridModeEnabled = true;             // Enable model switching

    // Gemini configuration
    private String geminiApiKey;
    private String geminiModel = "gemini-1.5-flash";     // Standard model
    private String geminiPremiumModel = "gemini-1.5-pro"; // Premium model

    // Qdrant configuration
    private String qdrantHost = "localhost";
    private int qdrantPort = 6334; // Qdrant gRPC port (6333 is HTTP/REST API)
    private String qdrantApiKey;
    private String qdrantCollectionConversations = "conversations";
    private String qdrantCollectionPatterns = "app_patterns";

    // Database configuration
    private String databaseUrl;
    private String databaseUser;
    private String databasePassword;
    private int databasePoolSize = 10;

    // Feature flags
    private boolean enableLearning = true;
    private boolean enableVoice = true;
    private int maxContextMessages = 10;

    // Retries and Debugging
    private boolean retryOnError = false; // Disabled by default as per user request
    private int maxRetries = 0;
    private boolean debugMode = true;
    private int maxIterations = 5; // Default limit as per user request (was 25)

    // Caching
    private int embeddingCacheSizeMax = 10000;
    private int embeddingCacheTtlHours = 1;

    /**
     * Load configuration from environment variables and config file
     */
    public static AiConfig load() {
        AiConfig config = new AiConfig();

        // Load from environment variables (highest priority)
        config.openaiApiKey = getEnv("OPENAI_API_KEY", null);
        config.openaiModel = getEnv("OPENAI_MODEL", config.openaiModel);
        config.openaiPremiumModel = getEnv("OPENAI_PREMIUM_MODEL", config.openaiPremiumModel);
        config.openaiEmbeddingModel = getEnv("OPENAI_EMBEDDING_MODEL", config.openaiEmbeddingModel);
        config.openaiMaxTokens = Integer.parseInt(getEnv("AI_MAX_TOKENS", String.valueOf(config.openaiMaxTokens)));
        config.hybridModeEnabled = Boolean.parseBoolean(getEnv("AI_HYBRID_MODE", "true"));

        config.geminiApiKey = getEnv("GEMINI_API_KEY", null);
        config.geminiModel = getEnv("GEMINI_MODEL", config.geminiModel);
        config.geminiPremiumModel = getEnv("GEMINI_PREMIUM_MODEL", config.geminiPremiumModel);

        config.qdrantHost = getEnv("QDRANT_HOST", config.qdrantHost);
        config.qdrantPort = Integer.parseInt(getEnv("QDRANT_PORT", String.valueOf(config.qdrantPort)));
        config.qdrantApiKey = getEnv("QDRANT_API_KEY", null);

        config.databaseUrl = getEnv("DATABASE_URL", "jdbc:postgresql://localhost:5432/appbana");
        config.databaseUser = getEnv("DATABASE_USER", "appbana");
        config.databasePassword = getEnv("DATABASE_PASSWORD", "");

        config.port = Integer.parseInt(getEnv("AI_PORT", String.valueOf(config.port)));
        config.enableLearning = Boolean.parseBoolean(getEnv("AI_ENABLE_LEARNING", "true"));
        config.enableVoice = Boolean.parseBoolean(getEnv("AI_ENABLE_VOICE", "true"));
        config.maxIterations = Integer.parseInt(getEnv("AI_MAX_ITERATIONS", "5"));

        // Validate required configuration
        if (config.openaiApiKey == null || config.openaiApiKey.isEmpty()) {
            throw new IllegalStateException("OPENAI_API_KEY environment variable is required");
        }

        log.info("Configuration loaded successfully");
        return config;
    }

    private static String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null ? value : defaultValue;
    }

    @Override
    public String toString() {
        return String.format("AiConfig{port=%d, openaiModel=%s, qdrantHost=%s:%d, enableLearning=%s}",
                port, openaiModel, qdrantHost, qdrantPort, enableLearning);
    }
}
