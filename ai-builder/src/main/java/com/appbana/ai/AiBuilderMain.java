package com.appbana.ai;

import com.appbana.ai.config.AiConfig;
import com.appbana.ai.server.AiServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for AI Builder Service
 * 
 * This service provides intelligent AI-powered app building capabilities
 * using GPT-4, RAG (Retrieval Augmented Generation), and learning from user interactions.
 */
public class AiBuilderMain {
    private static final Logger log = LoggerFactory.getLogger(AiBuilderMain.class);
    
    public static void main(String[] args) {
        try {
            log.info("Starting AI Builder Service...");
            
            // Load configuration
            AiConfig config = AiConfig.load();
            log.info("Configuration loaded: {}", config);
            
            // Start server
            AiServer server = new AiServer(config);
            server.start();
            
            log.info("AI Builder Service started successfully on port {}", config.getPort());
            log.info("Health check: http://localhost:{}/health", config.getPort());
            log.info("API endpoint: http://localhost:{}/api/ai/chat", config.getPort());
            
            // Shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutting down AI Builder Service...");
                server.stop();
                log.info("AI Builder Service stopped");
            }));
            
        } catch (Exception e) {
            log.error("Failed to start AI Builder Service", e);
            System.exit(1);
        }
    }
}
