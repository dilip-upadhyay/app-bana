package com.appbana.ai.server;

import com.appbana.ai.agent.AiAgent;
import com.appbana.ai.agent.AgentConfig;
import com.appbana.ai.agent.tool.*;
import com.appbana.ai.api.AiChatController;
import com.appbana.ai.api.ChatHistoryController;
import com.appbana.ai.api.Router;
import com.appbana.ai.config.AiConfig;
import com.appbana.ai.knowledge.AppBanaKnowledgeLoader;
import com.appbana.ai.knowledge.AppBanaPromptEnhancer;
import com.appbana.ai.knowledge.AppBanaSchemaLoader;
import com.appbana.ai.knowledge.KnowledgeBaseService;
import com.appbana.ai.knowledge.MetadataValidator;
import com.appbana.ai.llm.AdvancedPromptEngine;
// IntentClassifier import removed
import com.appbana.ai.llm.OpenAiLlmService;
import com.appbana.ai.learning.UserPreferenceEngine;
import com.appbana.ai.optimization.DirectAnswerService;
import com.appbana.ai.optimization.PatternExecutor;
import com.appbana.ai.rag.EmbeddingService;
import com.appbana.ai.rag.QdrantService;
import com.appbana.ai.rag.VectorStoreService;
import com.appbana.ai.rag.ConversationMemory;
import org.flywaydb.core.Flyway;
import com.sun.net.httpserver.HttpServer;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
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
    private HikariDataSource dataSource; // Keep reference for closing

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

            // Database Connection (HikariCP)
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(config.getDatabaseUrl());
            hikariConfig.setUsername(config.getDatabaseUser());
            hikariConfig.setPassword(config.getDatabasePassword());
            hikariConfig.setMaximumPoolSize(config.getDatabasePoolSize());
            this.dataSource = new HikariDataSource(hikariConfig);
            log.info("Database connection pool initialized");

            // Run DB Migrations (Flyway)
            log.info("Running database migrations...");
            Flyway.configure()
                    .dataSource(this.dataSource)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .baselineVersion("0")
                    .cleanDisabled(false)
                    .load()
                    .migrate();
            log.info("Database migrations complete");

            // User Preference Engine
            UserPreferenceEngine userPreferenceEngine = new UserPreferenceEngine(dataSource, config);

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

            // Load AppBana knowledge into vector database only if not already loaded
            if (knowledgeBaseService.initializeIfPopulated()) {
                log.info("AppBana knowledge base already populated ({} schemas) - skipping initialization to save time and API costs", 
                         knowledgeBaseService.getIndexedCount());
            } else {
                AppBanaKnowledgeLoader knowledgeLoader = new AppBanaKnowledgeLoader(knowledgeBaseService);
                knowledgeLoader.loadAllKnowledge();
                log.info("AppBana knowledge base loaded");
            }

            // Optimization Services (RAG-First cost optimization)
            DirectAnswerService directAnswerService = new DirectAnswerService(knowledgeBaseService);
            PatternExecutor patternExecutor = new PatternExecutor(
                    userPreferenceEngine,
                    embeddingService,
                    qdrantService);
            log.info("Optimization services initialized (DirectAnswer + PatternExecutor)");

            // Prompt Enhancer
            AppBanaPromptEnhancer promptEnhancer = new AppBanaPromptEnhancer(knowledgeBaseService);

            // Conversation Memory — backed by PostgreSQL for persistence + Qdrant for semantic search
            ConversationMemory conversationMemory = new ConversationMemory(
                    dataSource,        // PostgreSQL: persists history across restarts
                    embeddingService,
                    vectorStoreService,
                    qdrantService,
                    config);

            // Intent Classifier removed

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
            // Story 7: Register compounds tool FIRST (prioritized for LLM)
            toolRegistry.register(new ScaffoldAppTool(metadataValidator, backendUrl));

            // Register granular tools (used by ScaffoldAppTool internally or individually)
            toolRegistry.register(new CreateAppTool(backendUrl));
            toolRegistry.register(new CreateEntityTool(metadataValidator, backendUrl));
            toolRegistry.register(new ListEntitiesTool(backendUrl));
            toolRegistry.register(new GetEntityDetailsTool(backendUrl));
            // New context-aware listing tools
            toolRegistry.register(new ListPagesTool(backendUrl));
            toolRegistry.register(new ListWorkflowsTool(backendUrl));
            toolRegistry.register(new ListAppsTool(backendUrl));

            toolRegistry.register(new GeneratePageTool(metadataValidator, backendUrl));
            toolRegistry.register(new DeployAppTool(backendUrl));
            toolRegistry.register(new SearchKnowledgeTool(knowledgeBaseService));
            
            // Cost Optimization: Compound tools for batch operations (Phase 2)
            toolRegistry.register(new BatchUpdateEntitiesTool(metadataValidator, backendUrl));

            log.info("Registered {} tools", toolRegistry.getToolCount());

            // Agent Config
            AgentConfig agentConfig = AgentConfig.defaults();

            // AI Agent
            AiAgent agent = new AiAgent(llmService, toolRegistry, agentConfig);

            // AI Chat Controller
            AiChatController chatController = new AiChatController(
                    llmService,
                    promptEngine,
                    conversationMemory,
                    agent,
                    userPreferenceEngine,
                    directAnswerService,
                    patternExecutor);

            // Chat History Controller
            ChatHistoryController historyController = new ChatHistoryController(conversationMemory);

            log.info("AI services initialized successfully");

            // Register routes
            registerRoutes(router, chatController, historyController);

        } catch (Exception e) {
            log.error("Failed to initialize AI services", e);
            throw new RuntimeException("AI service initialization failed", e);
        }

        return router;
    }

    /**
     * Register all AI endpoints
     */
    private void registerRoutes(Router router, AiChatController chatController,
                                ChatHistoryController historyController) {
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

        // Chat history endpoints
        router.get("/api/ai/chat/history",  historyController.getHistory());
        router.get("/api/ai/chat/sessions", historyController.getSessions());

        log.info("Registered AI routes:");
        log.info("  GET  /health");
        log.info("  POST /api/ai/chat");
        log.info("  POST /api/ai/chat/agent");
        log.info("  GET  /api/ai/chat/history");
        log.info("  GET  /api/ai/chat/sessions");
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
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            log.info("Database connection pool closed");
        }
    }

    /**
     * Main entry point for the AI Builder Server
     */
    public static void main(String[] args) {
        try {
            log.info("Starting AI Builder Server...");

            // Load configuration
            AiConfig config = AiConfig.load();
            log.info("Configuration loaded - Port: {}, Model: {}", config.getPort(), config.getOpenaiModel());

            // Initialize Qdrant
            QdrantService qdrantService = new QdrantService(config);
            log.info("Qdrant service initialized");

            // Create and start server
            AiServer server = new AiServer(config, qdrantService);
            server.start();

            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutting down AI Builder Server...");
                server.stop();
            }));

            // Keep the main thread alive
            Thread.currentThread().join();

        } catch (Exception e) {
            log.error("Failed to start AI Builder Server", e);
            System.exit(1);
        }
    }
}
