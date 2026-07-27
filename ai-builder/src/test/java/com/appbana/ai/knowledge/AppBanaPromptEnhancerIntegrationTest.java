package com.appbana.ai.knowledge;

import com.appbana.ai.config.AiConfig;
import com.appbana.ai.rag.EmbeddingService;
import com.appbana.ai.rag.QdrantService;
import com.appbana.ai.rag.VectorStoreService;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for AppBanaPromptEnhancer with real Qdrant and OpenAI
 * Story 7.3: RAG-Enhanced Prompt Engineering
 * 
 * Requirements:
 * - Qdrant running on localhost:6334
 * - OPENAI_API_KEY environment variable set
 * - KnowledgeBaseService initialized with schemas
 * 
 * Run with: mvn test -Dtest=AppBanaPromptEnhancerIntegrationTest
 */
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AppBanaPromptEnhancerIntegrationTest {

    private static AppBanaPromptEnhancer promptEnhancer;
    private static KnowledgeBaseService knowledgeBaseService;
    private static QdrantService qdrantService;
    private static VectorStoreService vectorStoreService;
    private static EmbeddingService embeddingService;

    @BeforeAll
    static void setUp() throws Exception {
        // These tests make real, billable OpenAI calls. Skip rather than fail when no key is
        // configured, so an unkeyed dev/CI machine still gets a green build.
        String apiKey = System.getenv("OPENAI_API_KEY");
        org.junit.jupiter.api.Assumptions.assumeTrue(apiKey != null && !apiKey.isEmpty(),
                "OPENAI_API_KEY not set — skipping OpenAI integration tests");

        // Initialize configuration
        AiConfig config = new AiConfig();
        config.setOpenaiApiKey(apiKey);
        config.setOpenaiEmbeddingModel("text-embedding-3-small");
        config.setQdrantHost("localhost");
        config.setQdrantPort(6334);
        config.setEmbeddingCacheSizeMax(1000);
        config.setEmbeddingCacheTtlHours(1);

        // Initialize services
        qdrantService = new QdrantService(config);
        qdrantService.initializeCollections();

        embeddingService = new EmbeddingService(config);
        vectorStoreService = new VectorStoreService(qdrantService, config);
        AppBanaSchemaLoader schemaLoader = new AppBanaSchemaLoader();

        knowledgeBaseService = new KnowledgeBaseService(
                qdrantService,
                vectorStoreService,
                embeddingService,
                schemaLoader);

        // Index schemas if not already done
        if (!knowledgeBaseService.isInitialized()) {
            knowledgeBaseService.indexAllSchemas();
        }

        promptEnhancer = new AppBanaPromptEnhancer(knowledgeBaseService);

        System.out.println("Integration test setup complete");
    }

    @AfterAll
    static void tearDown() {
        if (embeddingService != null) {
            embeddingService.close();
        }
        if (qdrantService != null) {
            qdrantService.close();
        }
        System.out.println("Integration test cleanup complete");
    }

    @Test
    @Order(1)
    void testEnhancePrompt_CustomerForm() {
        System.out.println("\n=== Test 1: Enhance Prompt for Customer Form ===");

        String userMessage = "create a customer form with email and phone fields";
        String basePrompt = "User: " + userMessage + "\nAssistant:";

        // Act
        String enhanced = promptEnhancer.enhancePrompt(userMessage, basePrompt);

        // Assert
        assertNotNull(enhanced);
        assertTrue(enhanced.length() > basePrompt.length());

        System.out.println("Base prompt length: " + basePrompt.length());
        System.out.println("Enhanced prompt length: " + enhanced.length());

        // Verify AppBana context is included
        assertTrue(enhanced.contains("AppBana"), "Should contain AppBana context");

        // Verify relevant schemas are included
        assertTrue(enhanced.contains("email") || enhanced.contains("phone"),
                "Should contain email or phone field types");

        System.out.println("✓ Prompt successfully enhanced with AppBana context");
    }

    @Test
    @Order(2)
    void testEnhancePrompt_DataTable() {
        System.out.println("\n=== Test 2: Enhance Prompt for Data Table ===");

        String userMessage = "build a data table to display products";
        String basePrompt = "User: " + userMessage + "\nAssistant:";

        // Act
        String enhanced = promptEnhancer.enhancePrompt(userMessage, basePrompt);

        // Assert
        assertNotNull(enhanced);
        assertTrue(enhanced.length() > basePrompt.length());

        // Verify table component is mentioned
        assertTrue(enhanced.contains("table"), "Should contain table component");

        System.out.println("✓ Prompt enhanced with table component schema");
    }

    @Test
    @Order(3)
    void testEnhancePrompt_TokenUsage() {
        System.out.println("\n=== Test 3: Verify Token Usage ===");

        String userMessage = "create a complex form with many fields";
        String basePrompt = "User: " + userMessage + "\nAssistant:";

        // Act
        String enhanced = promptEnhancer.enhancePrompt(userMessage, basePrompt);

        // Assert
        assertNotNull(enhanced);

        // Rough token estimate (1 token ≈ 4 characters)
        int estimatedTokens = enhanced.length() / 4;

        System.out.println("Enhanced prompt length: " + enhanced.length() + " chars");
        System.out.println("Estimated tokens: " + estimatedTokens);

        // Verify token usage is reasonable (< 2000 tokens)
        assertTrue(estimatedTokens < 2000,
                "Enhanced prompt should stay under 2000 tokens, got: " + estimatedTokens);

        System.out.println("✓ Token usage is within acceptable limits");
    }

    @Test
    @Order(4)
    void testGetComponentExamples() {
        System.out.println("\n=== Test 4: Get Component Examples ===");

        // Act
        var inputExamples = promptEnhancer.getComponentExamples("input");
        var buttonExamples = promptEnhancer.getComponentExamples("button");

        // Assert
        assertNotNull(inputExamples);
        assertNotNull(buttonExamples);
        assertFalse(inputExamples.isEmpty(), "Input examples should not be empty");
        assertFalse(buttonExamples.isEmpty(), "Button examples should not be empty");

        System.out.println("Input examples: " + inputExamples.size());
        System.out.println("Button examples: " + buttonExamples.size());

        // Verify examples are valid JSON
        assertTrue(inputExamples.get(0).contains("\"type\":\"input\""));
        assertTrue(buttonExamples.get(0).contains("\"type\":\"button\""));

        System.out.println("✓ Component examples retrieved successfully");
    }

    @Test
    @Order(5)
    void testSearchSchemasByType() {
        System.out.println("\n=== Test 5: Search Schemas by Type ===");

        // Act
        var components = promptEnhancer.searchSchemasByType(
                SchemaDefinition.SchemaType.COMPONENT,
                "user interface",
                5);

        var fieldTypes = promptEnhancer.searchSchemasByType(
                SchemaDefinition.SchemaType.ENTITY_FIELD,
                "text input",
                5);

        // Assert
        assertNotNull(components);
        assertNotNull(fieldTypes);
        assertFalse(components.isEmpty(), "Should find component schemas");
        assertFalse(fieldTypes.isEmpty(), "Should find field type schemas");

        System.out.println("Found " + components.size() + " component schemas");
        System.out.println("Found " + fieldTypes.size() + " field type schemas");

        // Verify all results are of correct type
        assertTrue(components.stream()
                .allMatch(s -> s.getTypeAsEnum() == SchemaDefinition.SchemaType.COMPONENT));
        assertTrue(fieldTypes.stream()
                .allMatch(s -> s.getTypeAsEnum() == SchemaDefinition.SchemaType.ENTITY_FIELD));

        System.out.println("✓ Type-filtered search working correctly");
    }
}
