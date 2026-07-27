package com.appbana.ai.knowledge;

import com.appbana.ai.config.AiConfig;
import com.appbana.ai.rag.EmbeddingService;
import com.appbana.ai.rag.QdrantService;
import com.appbana.ai.rag.VectorStoreService;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for KnowledgeBaseService with real Qdrant and OpenAI
 * Story 7.2: Vector Store Integration
 * 
 * Requirements:
 * - Qdrant running on localhost:6334
 * - OPENAI_API_KEY environment variable set
 * 
 * Run with: mvn test -Dtest=KnowledgeBaseServiceIntegrationTest
 */
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KnowledgeBaseServiceIntegrationTest {

    private static KnowledgeBaseService knowledgeBaseService;
    private static QdrantService qdrantService;
    private static VectorStoreService vectorStoreService;
    private static EmbeddingService embeddingService;
    private static AppBanaSchemaLoader schemaLoader;

    @BeforeAll
    static void setUp() {
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
        schemaLoader = new AppBanaSchemaLoader();

        knowledgeBaseService = new KnowledgeBaseService(
                qdrantService,
                vectorStoreService,
                embeddingService,
                schemaLoader);

        System.out.println("Integration test setup complete");
    }

    @AfterAll
    static void tearDown() {
        // Clean up
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
    void testIndexAndSearch_RealEmbeddings() throws Exception {
        System.out.println("\n=== Test 1: Index and Search with Real Embeddings ===");

        // Index all schemas
        knowledgeBaseService.indexAllSchemas();

        // Verify indexing
        assertTrue(knowledgeBaseService.isInitialized());
        assertEquals(39, knowledgeBaseService.getIndexedCount());

        System.out.println("✓ Indexed " + knowledgeBaseService.getIndexedCount() + " schemas");

        // Verify collection in Qdrant
        String collectionName = qdrantService.getAppBanaKnowledgeCollection();
        assertTrue(qdrantService.collectionExists(collectionName));

        long pointCount = vectorStoreService.getCount(collectionName);
        assertEquals(39, pointCount);

        System.out.println("✓ Qdrant collection has " + pointCount + " points");
    }

    @Test
    @Order(2)
    void testSearchSemanticSimilarity() throws Exception {
        System.out.println("\n=== Test 2: Semantic Search Quality ===");

        // Test 1: Search for email field
        List<SchemaDefinition> emailResults = knowledgeBaseService.searchRelevantSchemas(
                "store user email address",
                5);

        assertNotNull(emailResults);
        assertFalse(emailResults.isEmpty());

        // First result should be email field type
        SchemaDefinition topResult = emailResults.get(0);
        System.out.println("Query: 'store user email address'");
        System.out.println("  → Top result: " + topResult.getName() + " (" + topResult.getType() + ")");
        System.out.println("  → Description: " + topResult.getDescription());

        // Verify email field is in top results
        boolean foundEmail = emailResults.stream()
                .anyMatch(s -> s.getName().equals("email"));
        assertTrue(foundEmail, "Email field should be in top 5 results");

        // Test 2: Search for input component
        List<SchemaDefinition> inputResults = knowledgeBaseService.searchRelevantSchemas(
                "form input field",
                5);

        assertNotNull(inputResults);
        assertFalse(inputResults.isEmpty());

        topResult = inputResults.get(0);
        System.out.println("\nQuery: 'form input field'");
        System.out.println("  → Top result: " + topResult.getName() + " (" + topResult.getType() + ")");

        // Verify input component is in results
        boolean foundInput = inputResults.stream()
                .anyMatch(s -> s.getName().equals("input"));
        assertTrue(foundInput, "Input component should be in top 5 results");

        // Test 3: Search for table component
        List<SchemaDefinition> tableResults = knowledgeBaseService.searchRelevantSchemas(
                "data table with rows and columns",
                5);

        assertNotNull(tableResults);
        assertFalse(tableResults.isEmpty());

        topResult = tableResults.get(0);
        System.out.println("\nQuery: 'data table with rows and columns'");
        System.out.println("  → Top result: " + topResult.getName() + " (" + topResult.getType() + ")");

        // Verify table component is in results
        boolean foundTable = tableResults.stream()
                .anyMatch(s -> s.getName().equals("table"));
        assertTrue(foundTable, "Table component should be in top 5 results");
    }

    @Test
    @Order(3)
    void testSearchByType_Filtering() throws Exception {
        System.out.println("\n=== Test 3: Type-Filtered Search ===");

        // Search only for components
        List<SchemaDefinition> componentResults = knowledgeBaseService.searchByType(
                SchemaDefinition.SchemaType.COMPONENT,
                "user interface element",
                10);

        assertNotNull(componentResults);
        assertFalse(componentResults.isEmpty());

        System.out.println("Query: 'user interface element' (COMPONENT only)");
        System.out.println("  → Found " + componentResults.size() + " components");

        // Verify all results are components
        for (SchemaDefinition schema : componentResults) {
            assertEquals("component", schema.getType());
            System.out.println("    - " + schema.getName());
        }

        // Search only for entity fields
        List<SchemaDefinition> fieldResults = knowledgeBaseService.searchByType(
                SchemaDefinition.SchemaType.ENTITY_FIELD,
                "text input",
                10);

        assertNotNull(fieldResults);
        assertFalse(fieldResults.isEmpty());

        System.out.println("\nQuery: 'text input' (ENTITY_FIELD only)");
        System.out.println("  → Found " + fieldResults.size() + " field types");

        // Verify all results are entity fields
        for (SchemaDefinition schema : fieldResults) {
            assertEquals("field-type", schema.getType());
        }
    }

    @Test
    @Order(4)
    void testRefreshKnowledge_RealQdrant() throws Exception {
        System.out.println("\n=== Test 4: Refresh Knowledge Base ===");

        String collectionName = qdrantService.getAppBanaKnowledgeCollection();

        // Get initial count
        long initialCount = vectorStoreService.getCount(collectionName);
        System.out.println("Initial point count: " + initialCount);

        // Refresh knowledge base
        knowledgeBaseService.refreshKnowledge();

        // Verify re-indexing
        assertTrue(knowledgeBaseService.isInitialized());
        assertEquals(39, knowledgeBaseService.getIndexedCount());

        long finalCount = vectorStoreService.getCount(collectionName);
        System.out.println("Final point count: " + finalCount);

        assertEquals(initialCount, finalCount);
        System.out.println("✓ Knowledge base refreshed successfully");
    }

    @Test
    @Order(5)
    void testGetExamples_RealData() throws Exception {
        System.out.println("\n=== Test 5: Get Component Examples ===");

        // Get examples for input component
        List<String> inputExamples = knowledgeBaseService.getExamples("input");
        assertNotNull(inputExamples);
        assertFalse(inputExamples.isEmpty());

        System.out.println("Input component examples:");
        for (String example : inputExamples) {
            System.out.println("  " + example);
        }

        // Verify examples are valid JSON
        assertTrue(inputExamples.get(0).contains("\"type\":\"input\""));

        // Get examples for button component
        List<String> buttonExamples = knowledgeBaseService.getExamples("button");
        assertNotNull(buttonExamples);
        assertFalse(buttonExamples.isEmpty());

        System.out.println("\nButton component examples:");
        for (String example : buttonExamples) {
            System.out.println("  " + example);
        }
    }
}
