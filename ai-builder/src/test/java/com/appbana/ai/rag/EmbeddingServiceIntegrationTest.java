package com.appbana.ai.rag;

import com.appbana.ai.config.AiConfig;
import com.appbana.ai.rag.EmbeddingService.EmbeddingException;
import com.appbana.ai.rag.EmbeddingService.CacheStats;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for EmbeddingService with real OpenAI API
 * 
 * IMPORTANT: These tests require a valid OPENAI_API_KEY environment variable
 * and will make real API calls (costs money).
 * 
 * Run with: mvn test -Dtest=EmbeddingServiceIntegrationTest
 * 
 * Story: 1.2 - Implement Embedding Service
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EmbeddingServiceIntegrationTest {

    private static EmbeddingService embeddingService;
    private static AiConfig config;

    @BeforeAll
    static void setUpAll() {
        // Check if API key is available
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException(
                    "OPENAI_API_KEY environment variable is required for integration tests");
        }

        // Create configuration
        config = new AiConfig();
        config.setOpenaiApiKey(apiKey);
        config.setOpenaiEmbeddingModel("text-embedding-3-small");
        config.setEmbeddingCacheSizeMax(100);
        config.setEmbeddingCacheTtlHours(1);

        // Initialize service
        embeddingService = new EmbeddingService(config);
    }

    @AfterAll
    static void tearDownAll() {
        if (embeddingService != null) {
            embeddingService.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("Should generate embedding for simple text")
    void testEmbed_SimpleText() throws Exception {
        // Given
        String text = "Hello, world!";

        // When
        float[] embedding = embeddingService.embed(text);

        // Then
        assertNotNull(embedding);
        assertEquals(1536, embedding.length, "Should have 1536 dimensions");

        // Verify it's not all zeros
        boolean hasNonZero = false;
        for (float value : embedding) {
            if (value != 0.0f) {
                hasNonZero = true;
                break;
            }
        }
        assertTrue(hasNonZero, "Embedding should contain non-zero values");
    }

    @Test
    @Order(2)
    @DisplayName("Should cache embeddings")
    void testEmbed_Caching() throws Exception {
        // Given
        String text = "This text should be cached";

        // When - First call (cache miss)
        long startTime1 = System.currentTimeMillis();
        float[] embedding1 = embeddingService.embed(text);
        long duration1 = System.currentTimeMillis() - startTime1;

        // When - Second call (cache hit)
        long startTime2 = System.currentTimeMillis();
        float[] embedding2 = embeddingService.embed(text);
        long duration2 = System.currentTimeMillis() - startTime2;

        // Then
        assertArrayEquals(embedding1, embedding2, "Cached embedding should be identical");
        assertTrue(duration2 < duration1 / 10,
                "Cached call should be much faster (was " + duration2 + "ms vs " + duration1 + "ms)");

        // Verify cache stats
        CacheStats stats = embeddingService.getCacheStats();
        assertTrue(stats.hits() > 0, "Should have cache hits");
        assertTrue(stats.hitRate() > 0, "Hit rate should be positive");
    }

    @Test
    @Order(3)
    @DisplayName("Should generate embeddings for batch")
    void testEmbedBatch_MultipleTexts() throws Exception {
        // Given
        List<String> texts = Arrays.asList(
                "First text",
                "Second text",
                "Third text");

        // When
        List<float[]> embeddings = embeddingService.embedBatch(texts);

        // Then
        assertNotNull(embeddings);
        assertEquals(3, embeddings.size());

        // Verify each embedding
        for (int i = 0; i < embeddings.size(); i++) {
            float[] embedding = embeddings.get(i);
            assertNotNull(embedding, "Embedding " + i + " should not be null");
            assertEquals(1536, embedding.length, "Embedding " + i + " should have 1536 dimensions");
        }

        // Verify embeddings are different
        assertFalse(Arrays.equals(embeddings.get(0), embeddings.get(1)),
                "Different texts should have different embeddings");
    }

    @Test
    @Order(4)
    @DisplayName("Should handle batch with some cached texts")
    void testEmbedBatch_PartialCache() throws Exception {
        // Given
        String cachedText = "This was cached earlier";
        embeddingService.embed(cachedText); // Cache it

        List<String> texts = Arrays.asList(
                cachedText, // This one is cached
                "New text 1", // These are not
                "New text 2");

        CacheStats statsBefore = embeddingService.getCacheStats();

        // When
        List<float[]> embeddings = embeddingService.embedBatch(texts);

        // Then
        assertEquals(3, embeddings.size());

        CacheStats statsAfter = embeddingService.getCacheStats();
        assertTrue(statsAfter.hits() > statsBefore.hits(),
                "Should have cache hit for the cached text");
    }

    @Test
    @Order(5)
    @DisplayName("Should handle large batch by chunking")
    void testEmbedBatch_LargeBatch() throws Exception {
        // Given - Create 150 texts (exceeds max batch size of 100)
        List<String> texts = new java.util.ArrayList<>();
        for (int i = 0; i < 150; i++) {
            texts.add("Text number " + i);
        }

        // When
        List<float[]> embeddings = embeddingService.embedBatch(texts);

        // Then
        assertEquals(150, embeddings.size());
        for (float[] embedding : embeddings) {
            assertNotNull(embedding);
            assertEquals(1536, embedding.length);
        }
    }

    @Test
    @Order(6)
    @DisplayName("Should generate similar embeddings for similar texts")
    void testEmbed_SimilarTexts() throws Exception {
        // Given
        String text1 = "The cat sits on the mat";
        String text2 = "A cat is sitting on a mat";
        String text3 = "Python programming language";

        // When
        float[] embedding1 = embeddingService.embed(text1);
        float[] embedding2 = embeddingService.embed(text2);
        float[] embedding3 = embeddingService.embed(text3);

        // Then - Calculate cosine similarity
        double similarity12 = cosineSimilarity(embedding1, embedding2);
        double similarity13 = cosineSimilarity(embedding1, embedding3);

        assertTrue(similarity12 > 0.8,
                "Similar texts should have high similarity (was " + similarity12 + ")");
        assertTrue(similarity13 < similarity12,
                "Dissimilar texts should have lower similarity");
    }

    @Test
    @Order(7)
    @DisplayName("Should clear cache")
    void testClearCache() throws Exception {
        // Given
        embeddingService.embed("Text to cache");
        CacheStats statsBefore = embeddingService.getCacheStats();
        assertTrue(statsBefore.size() > 0, "Cache should have entries");

        // When
        embeddingService.clearCache();

        // Then
        CacheStats statsAfter = embeddingService.getCacheStats();
        assertEquals(0, statsAfter.size(), "Cache should be empty after clear");
    }

    /**
     * Calculate cosine similarity between two vectors
     */
    private double cosineSimilarity(float[] a, float[] b) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
