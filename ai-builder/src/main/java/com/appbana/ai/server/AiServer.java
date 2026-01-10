package com.appbana.ai.server;

import com.appbana.ai.agent.AiAgent;
import com.appbana.ai.agent.AgentConfig;
import com.appbana.ai.agent.tool.*;
import com.appbana.ai.api.AiChatController;
import com.appbana.ai.api.Router;
import com.appbana.ai.config.AiConfig;
import com.appbana.ai.knowledge.AppBanaPromptEnhancer;
import com.appbana.ai.knowledge.AppBanaSchemaLoader;
import com.appbana.ai.knowledge.KnowledgeBaseService;
import com.appbana.ai.knowledge.MetadataValidator;
import com.appbana.ai.llm.AdvancedPromptEngine;
import com.appbana.ai.llm.IntentClassifier;
import com.appbana.ai.llm.OpenAiLlmService;
import com.appbana.ai.rag.EmbeddingService;
import com.appbana.ai.rag.QdrantService;
import com.appbana.ai.rag.VectorStoreService;
import com.appbana.ai.rag.ConversationMemory;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * AI Builder HTTP Server using Router pattern
 * Runs as a separate microservice on port 8081
 */
@Slf4j
public class AiServer {
    private final AiConfig config;
    private final QdrantService qdrantService;
    private final HttpServer httpServer;
    private final Router router;

    public AiServer(AiConfig config, QdrantService qdrantService) throws IOException {
        this.config = config;
        this.qdrantService = qdrantService;
        this.router = buildRouter();

        // Create HTTP server
        this.httpServer = HttpServer.create(new InetSocketAddress(config.getPort()), 0);
        this.httpServer.createContext("/", router::handle);
        this.httpServer.setExecutor(null); // Use default executor

        log.info("AI Server initialized on port {}", config.getPort());
    }

    /**
     * Build router with all AI endpoints
     */
    private Router buildRouter() {
        Router router = new Router();

        try {
            // Initialize services
            log.info("Initializing AI services...");

            // LLM Service
            OpenAiLlmService llmService = new OpenAiLlmService(config);

            // Embedding Service
            EmbeddingService embeddingService = new EmbeddingService(config);

            // Vector Store Service
            VectorStoreService vectorStoreService = new VectorStoreService(qdrantService, config);

            // Schema Loader
            AppBanaSchemaLoader schemaLoader = new AppBanaSchemaLoader();

            // Knowledge Base Service
            KnowledgeBaseService knowledgeBaseService = new KnowledgeBaseService(
                    qdrantService,
                    vectorStoreService,
                    embeddingService,
                    schemaLoader);

            // Prompt Enhancer
            AppBanaPromptEnhancer promptEnhancer = new AppBanaPromptEnhancer(knowledgeBaseService);

            // Conversation Memory (no DataSource required for Qdrant-only mode)
            ConversationMemory conversationMemory = new ConversationMemory(
                    null, // dataSource
                    embeddingService,
                    vectorStoreService,
                    qdrantService,
                    config);

            // Intent Classifier
            IntentClassifier intentClassifier = new IntentClassifier(llmService);

            // Prompt Engine (now with conversation memory)
            AdvancedPromptEngine promptEngine = new AdvancedPromptEngine(
                    config,
                    conversationMemory,
                    promptEnhancer);

            // Metadata Validator
            MetadataValidator metadataValidator = new MetadataValidator(schemaLoader);

            // Tool Registry
            ToolRegistry toolRegistry = new ToolRegistry();

            // Register essential tools
            String backendUrl = "http://localhost:8080"; // Main AppBana service
            toolRegistry.register(new CreateEntityTool(metadataValidator, backendUrl));
            toolRegistry.register(new ListEntitiesTool(backendUrl));
            toolRegistry.register(new GeneratePageTool(metadataValidator, backendUrl));
            toolRegistry.register(new DeployAppTool(backendUrl));
            toolRegistry.register(new SearchKnowledgeTool(knowledgeBaseService));

            log.info("Registered {} tools", toolRegistry.getToolCount());

            // Agent Config
            AgentConfig agentConfig = AgentConfig.defaults();

            // AI Agent
            AiAgent agent = new AiAgent(llmService, toolRegistry, agentConfig);

            // AI Chat Controller
            AiChatController chatController = new AiChatController(
                    llmService,
                    intentClassifier,
                    promptEngine,
                    conversationMemory,
                    agent);

            log.info("AI services initialized successfully");

            // Register routes
            registerRoutes(router, chatController);

        } catch (Exception e) {
            log.error("Failed to initialize AI services", e);
            throw new RuntimeException("AI service initialization failed", e);
        }

        return router;
    }

    /**
     * Register all AI endpoints
     */
    private void registerRoutes(Router router, AiChatController chatController) {
        // Health check
        router.get("/health", (req, res) -> {
            boolean qdrantHealthy = qdrantService.healthCheck();
            res.json(200, java.util.Map.of(
                    "status", qdrantHealthy ? "UP" : "DOWN",
                    "service", "ai-builder",
                    "qdrant", qdrantHealthy ? "UP" : "DOWN"));
        });

        // Regular chat endpoint
        router.post("/api/ai/chat", chatController.chat());

        // Agent-based chat endpoint
        router.post("/api/ai/chat/agent", chatController.chatAgent());

        log.info("Registered AI routes:");
        log.info("  GET  /health");
        log.info("  POST /api/ai/chat");
        log.info("  POST /api/ai/chat/agent");
    }

    public void start() {
        httpServer.start();
        log.info("✅ AI Builder Server started on port {}", config.getPort());
        log.info("📍 Health check: http://localhost:{}/health", config.getPort());
        log.info("📍 Chat endpoint: http://localhost:{}/api/ai/chat", config.getPort());
        log.info("📍 Agent endpoint: http://localhost:{}/api/ai/chat/agent", config.getPort());
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            log.info("AI Builder Server stopped");
        }
    }
}
