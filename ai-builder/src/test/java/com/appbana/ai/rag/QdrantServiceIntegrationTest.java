package com.appbana.ai.rag;

import com.appbana.ai.config.AiConfig;
import io.qdrant.client.grpc.Collections.CollectionInfo;
import org.junit.jupiter.api.*;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.qdrant.QdrantContainer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for QdrantService with real Qdrant instance
 * Uses Testcontainers to spin up Qdrant in Docker
 * 
 * Story: 1.1 - Set Up Qdrant Vector Database
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class QdrantServiceIntegrationTest {

    @Container
    static QdrantContainer qdrantContainer = new QdrantContainer("qdrant/qdrant:latest");

    private static QdrantService qdrantService;
    private static AiConfig config;

    @BeforeAll
    static void setUpAll() {
        // Create configuration pointing to test container
        config = new AiConfig();
        config.setQdrantHost(qdrantContainer.getHost());
        config.setQdrantPort(qdrantContainer.getGrpcPort());
        config.setQdrantApiKey(null);

        // Initialize service
        qdrantService = new QdrantService(config);
    }

    @AfterAll
    static void tearDownAll() {
        if (qdrantService != null) {
            qdrantService.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("Should perform health check successfully")
    void testHealthCheck() {
        // When
        boolean healthy = qdrantService.healthCheck();

        // Then
        assertTrue(healthy, "Qdrant should be healthy");
    }

    @Test
    @Order(2)
    @DisplayName("Should initialize collections successfully")
    void testInitializeCollections() {
        // When
        qdrantService.initializeCollections();

        // Then
        assertTrue(qdrantService.collectionExists("conversations"));
        assertTrue(qdrantService.collectionExists("app_patterns"));
    }

    @Test
    @Order(3)
    @DisplayName("Should get collection info for conversations")
    void testGetCollectionInfo_Conversations() throws Exception {
        // When
        CollectionInfo info = qdrantService.getCollectionInfo("conversations");

        // Then
        assertNotNull(info);
        assertEquals(0, info.getPointsCount(), "New collection should have 0 points");
    }

    @Test
    @Order(4)
    @DisplayName("Should get collection info for patterns")
    void testGetCollectionInfo_Patterns() throws Exception {
        // When
        CollectionInfo info = qdrantService.getCollectionInfo("app_patterns");

        // Then
        assertNotNull(info);
        assertEquals(0, info.getPointsCount(), "New collection should have 0 points");
    }

    @Test
    @Order(5)
    @DisplayName("Should handle re-initialization gracefully")
    void testReinitializeCollections() {
        // When - initialize again
        qdrantService.initializeCollections();

        // Then - should still exist
        assertTrue(qdrantService.collectionExists("conversations"));
        assertTrue(qdrantService.collectionExists("app_patterns"));
    }

    @Test
    @Order(6)
    @DisplayName("Should check non-existent collection")
    void testCollectionExists_NonExistent() {
        // When
        boolean exists = qdrantService.collectionExists("non_existent_collection");

        // Then
        assertFalse(exists);
    }

    @Test
    @Order(7)
    @DisplayName("Should delete collection")
    void testDeleteCollection() {
        // Given - create a test collection first
        String testCollection = "test_collection_to_delete";

        // When
        qdrantService.deleteCollection(testCollection);

        // Then
        assertFalse(qdrantService.collectionExists(testCollection));
    }
}
