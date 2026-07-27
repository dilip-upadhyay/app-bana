package com.appbana.ai.rag;

import com.appbana.ai.config.AiConfig;
import com.appbana.ai.rag.VectorStoreService.SearchResult;
import com.appbana.ai.rag.VectorStoreService.VectorStoreException;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for VectorStoreService with real Qdrant instance
 * 
 * Story: 1.3 - Implement Vector Store Service
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VectorStoreServiceTest {

    @Container
    static GenericContainer<?> qdrantContainer = new GenericContainer<>(DockerImageName.parse("qdrant/qdrant:latest"))
            .withExposedPorts(6333, 6334)
            .withReuse(true);

    private static QdrantService qdrantService;
    private static VectorStoreService vectorStoreService;
    private static AiConfig config;
    private static final String TEST_COLLECTION = "test_vectors";

    @BeforeAll
    static void setUpAll() throws Exception {
        // Create configuration
        config = new AiConfig();
        config.setQdrantHost(qdrantContainer.getHost());
        // 6334 is the gRPC port. QdrantService speaks gRPC, so mapping 6333 (the HTTP/REST
        // port) makes every call fail with "INTERNAL: http2 exception".
        config.setQdrantPort(qdrantContainer.getMappedPort(6334));
        config.setQdrantApiKey(null);

        // Initialize services
        qdrantService = new QdrantService(config);
        vectorStoreService = new VectorStoreService(qdrantService, config);

        // Create test collection
        qdrantService.initializeCollections();
    }

    @AfterAll
    static void tearDownAll() {
        if (qdrantService != null) {
            qdrantService.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("Should store vector with metadata")
    void testStore() throws Exception {
        // Given
        String id = UUID.randomUUID().toString();
        float[] vector = createRandomVector();
        Map<String, Object> metadata = Map.of(
                "userId", "user-123",
                "timestamp", System.currentTimeMillis(),
                "text", "Hello, world!",
                "intent", "greeting");

        // When
        vectorStoreService.store("conversations", id, vector, metadata);

        // Then - verify count increased
        long count = vectorStoreService.getCount("conversations");
        assertTrue(count > 0, "Collection should have at least one vector");
    }

    @Test
    @Order(2)
    @DisplayName("Should search for similar vectors")
    void testSearch() throws Exception {
        // Given - store a vector first
        String id = UUID.randomUUID().toString();
        float[] vector = createRandomVector();
        Map<String, Object> metadata = Map.of(
                "userId", "user-456",
                "timestamp", System.currentTimeMillis(),
                "text", "Test search query");
        vectorStoreService.store("conversations", id, vector, metadata);

        // When - search with same vector
        List<SearchResult> results = vectorStoreService.search("conversations", vector, 5, null);

        // Then
        assertNotNull(results);
        assertFalse(results.isEmpty(), "Should find at least one result");

        // First result should be exact match
        SearchResult firstResult = results.get(0);
        assertEquals(id, firstResult.id());
        assertTrue(firstResult.score() > 0.99, "Exact match should have score close to 1.0");
        assertEquals("Test search query", firstResult.getText());
    }

    @Test
    @Order(3)
    @DisplayName("Should filter search by user")
    void testSearchByUser() throws Exception {
        // Given - store vectors for different users
        String user1 = "user-search-1";
        String user2 = "user-search-2";

        float[] vector1 = createRandomVector();
        vectorStoreService.store("conversations", UUID.randomUUID().toString(), vector1, Map.of(
                "userId", user1,
                "timestamp", System.currentTimeMillis(),
                "text", "User 1 message"));

        float[] vector2 = createRandomVector();
        vectorStoreService.store("conversations", UUID.randomUUID().toString(), vector2, Map.of(
                "userId", user2,
                "timestamp", System.currentTimeMillis(),
                "text", "User 2 message"));

        // When - search for user1 only
        List<SearchResult> results = vectorStoreService.searchByUser("conversations", vector1, 10, user1);

        // Then - should only find user1's vectors
        assertFalse(results.isEmpty());
        for (SearchResult result : results) {
            assertEquals(user1, result.getUserId(), "All results should be from user1");
        }
    }

    @Test
    @Order(4)
    @DisplayName("Should delete vector by ID")
    void testDeleteById() throws Exception {
        // Given - store a vector
        String id = UUID.randomUUID().toString();
        float[] vector = createRandomVector();
        vectorStoreService.store("conversations", id, vector, Map.of(
                "userId", "user-delete-test",
                "timestamp", System.currentTimeMillis(),
                "text", "To be deleted"));

        long countBefore = vectorStoreService.getCount("conversations");

        // When - delete it
        vectorStoreService.deleteById("conversations", id);

        // Then - count should decrease
        long countAfter = vectorStoreService.getCount("conversations");
        assertTrue(countAfter < countBefore, "Count should decrease after deletion");
    }

    @Test
    @Order(5)
    @DisplayName("Should delete all vectors for a user (GDPR)")
    void testDeleteByUser() throws Exception {
        // Given - store multiple vectors for a user
        String userId = "user-gdpr-test";

        for (int i = 0; i < 3; i++) {
            vectorStoreService.store("conversations", UUID.randomUUID().toString(), createRandomVector(), Map.of(
                    "userId", userId,
                    "timestamp", System.currentTimeMillis(),
                    "text", "Message " + i));
        }

        // Verify they exist
        List<SearchResult> beforeDelete = vectorStoreService.searchByUser(
                "conversations", createRandomVector(), 10, userId);
        assertTrue(beforeDelete.size() >= 3, "Should have at least 3 vectors for user");

        // When - delete all for user
        vectorStoreService.deleteByUser("conversations", userId);

        // Then - should not find any
        List<SearchResult> afterDelete = vectorStoreService.searchByUser(
                "conversations", createRandomVector(), 10, userId);
        assertTrue(afterDelete.isEmpty() || afterDelete.stream().noneMatch(r -> userId.equals(r.getUserId())),
                "Should not find any vectors for deleted user");
    }

    @Test
    @Order(6)
    @DisplayName("Should validate vector dimensions")
    void testValidateVector() {
        // Given
        float[] invalidVector = new float[100]; // Wrong size
        Map<String, Object> metadata = Map.of(
                "userId", "user-123",
                "timestamp", System.currentTimeMillis());

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> {
            vectorStoreService.store("conversations", UUID.randomUUID().toString(), invalidVector, metadata);
        });
    }

    @Test
    @Order(7)
    @DisplayName("Should accept metadata without conversation-specific fields")
    void testValidateMetadata() {
        // userId/timestamp were deliberately relaxed to warn-only in VectorStoreService
        // because knowledge/schema vectors legitimately carry neither. This test previously
        // asserted the old strict behaviour and only went unnoticed because the whole class
        // was being skipped by a broken setUpAll.
        float[] vector = createRandomVector();
        Map<String, Object> sparseMetadata = Map.of("text", "No userId and no timestamp");

        assertDoesNotThrow(() ->
                vectorStoreService.store("conversations", UUID.randomUUID().toString(), vector, sparseMetadata));
    }

    @Test
    @Order(8)
    @DisplayName("Should validate topK parameter")
    void testValidateTopK() {
        // Given
        float[] vector = createRandomVector();

        // When/Then - topK too small
        assertThrows(IllegalArgumentException.class, () -> {
            vectorStoreService.search("conversations", vector, 0, null);
        });

        // When/Then - topK too large
        assertThrows(IllegalArgumentException.class, () -> {
            vectorStoreService.search("conversations", vector, 101, null);
        });
    }

    @Test
    @Order(9)
    @DisplayName("Should handle search with no results")
    void testSearchNoResults() throws Exception {
        // Given - an empty collection. It has to actually be created; searching a
        // non-existent collection raises NOT_FOUND, which is a different scenario.
        String emptyCollection = "empty_test";
        qdrantService.getClient().createCollectionAsync(
                io.qdrant.client.grpc.Collections.CreateCollection.newBuilder()
                        .setCollectionName(emptyCollection)
                        .setVectorsConfig(io.qdrant.client.grpc.Collections.VectorsConfig.newBuilder()
                                .setParams(io.qdrant.client.grpc.Collections.VectorParams.newBuilder()
                                        .setSize(1536)
                                        .setDistance(io.qdrant.client.grpc.Collections.Distance.Cosine)
                                        .build())
                                .build())
                        .build()).get();

        // When
        List<SearchResult> results = vectorStoreService.search(emptyCollection, createRandomVector(), 5, null);

        // Then
        assertNotNull(results);
        assertTrue(results.isEmpty(), "Should return empty list for collection with no data");
    }

    @Test
    @Order(10)
    @DisplayName("Should get collection count")
    void testGetCount() throws Exception {
        // When
        long count = vectorStoreService.getCount("conversations");

        // Then
        assertTrue(count >= 0, "Count should be non-negative");
    }

    // Helper methods

    private static float[] createRandomVector() {
        float[] vector = new float[1536];
        Random random = new Random();
        for (int i = 0; i < vector.length; i++) {
            vector[i] = random.nextFloat() * 2 - 1; // Range: -1 to 1
        }
        // Normalize
        float norm = 0;
        for (float v : vector) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= norm;
        }
        return vector;
    }
}
