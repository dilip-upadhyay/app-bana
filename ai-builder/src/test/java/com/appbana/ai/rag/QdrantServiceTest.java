package com.appbana.ai.rag;

import com.appbana.ai.config.AiConfig;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections.CollectionInfo;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for QdrantService
 * 
 * Story: 1.1 - Set Up Qdrant Vector Database
 */
class QdrantServiceTest {

    private QdrantService qdrantService;
    private AiConfig config;

    @BeforeEach
    void setUp() {
        // Create test configuration
        config = new AiConfig();
        config.setQdrantHost("localhost");
        config.setQdrantPort(6333);
        config.setQdrantApiKey(null);
    }

    @AfterEach
    void tearDown() {
        if (qdrantService != null) {
            qdrantService.close();
        }
    }

    @Test
    @DisplayName("Should initialize Qdrant client successfully")
    void testInitialization() {
        // When
        qdrantService = new QdrantService(config);

        // Then
        assertNotNull(qdrantService);
        assertNotNull(qdrantService.getClient());
    }

    @Test
    @DisplayName("Should return correct collection names")
    void testCollectionNames() {
        // Given
        qdrantService = new QdrantService(config);

        // When/Then
        assertEquals("conversations", qdrantService.getConversationsCollection());
        assertEquals("app_patterns", qdrantService.getPatternsCollection());
    }

    @Test
    @DisplayName("Should handle null API key")
    void testNullApiKey() {
        // Given
        config.setQdrantApiKey(null);

        // When
        qdrantService = new QdrantService(config);

        // Then
        assertNotNull(qdrantService);
    }

    @Test
    @DisplayName("Should handle empty API key")
    void testEmptyApiKey() {
        // Given
        config.setQdrantApiKey("");

        // When
        qdrantService = new QdrantService(config);

        // Then
        assertNotNull(qdrantService);
    }

    @Test
    @DisplayName("Should close client without errors")
    void testClose() {
        // Given
        qdrantService = new QdrantService(config);

        // When/Then - should not throw
        assertDoesNotThrow(() -> qdrantService.close());
    }

    // Note: Integration tests with real Qdrant instance are in
    // QdrantServiceIntegrationTest
}
