package com.appbana.ai;

import com.appbana.ai.config.AiConfig;
import com.appbana.ai.rag.QdrantService;
import com.appbana.ai.server.AiServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for AI Builder Service
 * 
 * This service provides intelligent AI-powered app building capabilities
 * using GPT-4, RAG (Retrieval Augmented Generation), and learning from user
 * interactions.
 */
public class AiBuilderMain {
    private static final Logger log = LoggerFactory.getLogger(AiBuilderMain.class);

    public static void main(String[] args) {
        QdrantService qdrantService = null;
        AiServer server = null;

        try {
            log.info("🚀 Starting AI Builder Service...");

            // Load configuration
            AiConfig config = AiConfig.load();
            log.info("✅ Configuration loaded: {}", config);

            // Initialize Qdrant
            log.info("📦 Initializing Qdrant vector database...");
            qdrantService = new QdrantService(config);

            // Health check
            if (!qdrantService.healthCheck()) {
                throw new RuntimeException("Qdrant health check failed");
            }
            log.info("✅ Qdrant health check passed");

            // Initialize collections
            qdrantService.initializeCollections();
            log.info("✅ Qdrant collections initialized");

            // Start server
            server = new AiServer(config, qdrantService);
            server.start();

            log.info("✅ AI Builder Service started successfully on port {}", config.getPort());
            log.info("📍 Health check: http://localhost:{}/health", config.getPort());
            log.info("📍 API endpoint: http://localhost:{}/api/ai/chat", config.getPort());

            // Keep references for shutdown hook
            final QdrantService finalQdrantService = qdrantService;
            final AiServer finalServer = server;

            // Shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("🛑 Shutting down AI Builder Service...");
                if (finalServer != null) {
                    finalServer.stop();
                }
                if (finalQdrantService != null) {
                    finalQdrantService.close();
                }
                log.info("✅ AI Builder Service stopped");
            }));

        } catch (Exception e) {
            log.error("❌ Failed to start AI Builder Service", e);

            // Cleanup on error
            if (qdrantService != null) {
                qdrantService.close();
            }
            if (server != null) {
                server.stop();
            }

            System.exit(1);
        }
    }
}
