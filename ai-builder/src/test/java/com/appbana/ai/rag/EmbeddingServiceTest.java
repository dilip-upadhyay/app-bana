package com.appbana.ai.rag;

import com.appbana.ai.config.AiConfig;
import com.appbana.ai.rag.EmbeddingService.EmbeddingException;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EmbeddingService
 * 
 * Note: These are unit tests with mocked OpenAI API.
 * Integration tests with real API are in EmbeddingServiceIntegrationTest.
 * 
 * Story: 1.2 - Implement Embedding Service
 */
class EmbeddingServiceTest {

    private EmbeddingService embeddingService;
    private AiConfig config;

    @BeforeEach
    void setUp() {
        // Create test configuration
        config = new AiConfig();
        config.setOpenaiApiKey("sk-test-key-for-unit-tests");
        config.setOpenaiEmbeddingModel("text-embedding-3-small");
        config.setEmbeddingCacheSizeMax(100);
        config.setEmbeddingCacheTtlHours(1);

        // Note: We can't easily mock OpenAiService, so these tests focus on
        // validation logic, caching behavior, and error handling
    }

    @AfterEach
    void tearDown() {
        if (embeddingService != null) {
            embeddingService.close();
        }
    }

    @Test
    @DisplayName("Should initialize with correct configuration")
    void testInitialization() {
        // When
        embeddingService = new EmbeddingService(config);

        // Then
        assertNotNull(embeddingService);
    }

    @Test
    @DisplayName("Should throw exception for null text")
    void testEmbed_NullText_ThrowsException() {
        // Given
        embeddingService = new EmbeddingService(config);

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> {
            embeddingService.embed(null);
        });
    }

    @Test
    @DisplayName("Should throw exception for empty text")
    void testEmbed_EmptyText_ThrowsException() {
        // Given
        embeddingService = new EmbeddingService(config);

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> {
            embeddingService.embed("");
        });
    }

    @Test
    @DisplayName("Should throw exception for whitespace-only text")
    void testEmbed_WhitespaceText_ThrowsException() {
        // Given
        embeddingService = new EmbeddingService(config);

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> {
            embeddingService.embed("   ");
        });
    }

    @Test
    @DisplayName("Should return empty list for null batch")
    void testEmbedBatch_NullList_ReturnsEmpty() throws Exception {
        // Given
        embeddingService = new EmbeddingService(config);

        // When
        List<float[]> result = embeddingService.embedBatch(null);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should return empty list for empty batch")
    void testEmbedBatch_EmptyList_ReturnsEmpty() throws Exception {
        // Given
        embeddingService = new EmbeddingService(config);

        // When
        List<float[]> result = embeddingService.embedBatch(List.of());

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should get cache stats")
    void testGetCacheStats() {
        // Given
        embeddingService = new EmbeddingService(config);

        // When
        EmbeddingService.CacheStats stats = embeddingService.getCacheStats();

        // Then
        assertNotNull(stats);
        assertEquals(0, stats.hits());
        assertEquals(0, stats.misses());
        assertEquals(0, stats.size());
    }

    @Test
    @DisplayName("Should clear cache")
    void testClearCache() {
        // Given
        embeddingService = new EmbeddingService(config);

        // When/Then - should not throw
        assertDoesNotThrow(() -> embeddingService.clearCache());
    }

    @Test
    @DisplayName("Should close without errors")
    void testClose() {
        // Given
        embeddingService = new EmbeddingService(config);

        // When/Then - should not throw
        assertDoesNotThrow(() -> embeddingService.close());
    }

    @Test
    @DisplayName("Cache stats should have meaningful toString")
    void testCacheStats_ToString() {
        // Given
        EmbeddingService.CacheStats stats = new EmbeddingService.CacheStats(10, 5, 0.666, 15);

        // When
        String str = stats.toString();

        // Then
        assertTrue(str.contains("hits=10"));
        assertTrue(str.contains("misses=5"));
        assertTrue(str.contains("66.6"));
        assertTrue(str.contains("size=15"));
    }

    // Note: Integration tests with real OpenAI API are in
    // EmbeddingServiceIntegrationTest
    // Those tests will verify:
    // - Actual embedding generation
    // - Caching behavior
    // - Batch processing
    // - Rate limiting
    // - Error handling with real API
}
