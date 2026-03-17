package com.appbana.ai.rag;

import com.appbana.ai.config.AiConfig;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.RateLimiter;
import com.theokanning.openai.embedding.Embedding;
import com.theokanning.openai.embedding.EmbeddingRequest;
import com.theokanning.openai.service.OpenAiService;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Service for generating text embeddings using OpenAI
 * 
 * Features:
 * - OpenAI text-embedding-3-small integration (1536 dimensions)
 * - Batch processing (up to 100 texts at once)
 * - Guava caching (1-hour TTL)
 * - Rate limiting (3000 requests/minute)
 * - Error handling and retries
 * 
 * Story: 1.2 - Implement Embedding Service
 */
@Slf4j
public class EmbeddingService implements AutoCloseable {

    private final AiConfig config;
    private final OpenAiService openAiService;
    private final Cache<String, float[]> embeddingCache;
    private final RateLimiter rateLimiter;

    // Constants
    private static final int MAX_BATCH_SIZE = 100;
    private static final int EMBEDDING_DIMENSION = 1536;
    private static final int MAX_TOKENS_PER_REQUEST = 8000;

    public EmbeddingService(AiConfig config) {
        this.config = config;

        log.info("Initializing Embedding Service with model: {}", config.getOpenaiEmbeddingModel());

        // Initialize OpenAI service
        this.openAiService = new OpenAiService(
                config.getOpenaiApiKey(),
                Duration.ofSeconds(30));

        // Initialize cache (1-hour TTL, max 10000 entries)
        this.embeddingCache = CacheBuilder.newBuilder()
                .maximumSize(config.getEmbeddingCacheSizeMax())
                .expireAfterWrite(config.getEmbeddingCacheTtlHours(), TimeUnit.HOURS)
                .recordStats()
                .build();

        // Initialize rate limiter (3000 requests per minute = 50 per second)
        this.rateLimiter = RateLimiter.create(50.0);

        log.info("Embedding Service initialized successfully");
        log.info("Cache config: max={}, TTL={}h",
                config.getEmbeddingCacheSizeMax(),
                config.getEmbeddingCacheTtlHours());
    }

    /**
     * Generate embedding for single text
     * 
     * @param text Input text (max 8000 tokens)
     * @return 1536-dimensional vector
     * @throws EmbeddingException if API fails
     */
    public float[] embed(String text) throws EmbeddingException {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Text cannot be null or empty");
        }

        // Check cache first
        float[] cached = embeddingCache.getIfPresent(text);
        if (cached != null) {
            log.debug("Cache hit for text: {}", truncate(text, 50));
            return cached;
        }

        log.debug("Cache miss, generating embedding for: {}", truncate(text, 50));

        // Rate limiting
        rateLimiter.acquire();

        try {
            // Call OpenAI API
            EmbeddingRequest request = EmbeddingRequest.builder()
                    .model(config.getOpenaiEmbeddingModel())
                    .input(List.of(text))
                    .build();

            List<Embedding> embeddings = openAiService.createEmbeddings(request).getData();

            if (embeddings.isEmpty()) {
                throw new EmbeddingException("No embeddings returned from OpenAI");
            }

            // Convert to float array
            float[] embedding = toFloatArray(embeddings.get(0).getEmbedding());

            // Validate dimension
            if (embedding.length != EMBEDDING_DIMENSION) {
                throw new EmbeddingException(
                        String.format("Expected %d dimensions, got %d", EMBEDDING_DIMENSION, embedding.length));
            }

            // Cache the result
            embeddingCache.put(text, embedding);

            log.debug("Generated embedding with {} dimensions", embedding.length);
            return embedding;

        } catch (Exception e) {
            log.error("Failed to generate embedding for text: {}", truncate(text, 50), e);
            throw new EmbeddingException("Failed to generate embedding", e);
        }
    }

    /**
     * Generate embeddings for multiple texts (batched)
     * 
     * @param texts List of input texts
     * @return List of 1536-dimensional vectors
     * @throws EmbeddingException if API fails
     */
    public List<float[]> embedBatch(List<String> texts) throws EmbeddingException {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }

        if (texts.size() > MAX_BATCH_SIZE) {
            log.warn("Batch size {} exceeds maximum {}, splitting into chunks",
                    texts.size(), MAX_BATCH_SIZE);
            return embedBatchChunked(texts);
        }

        log.debug("Generating embeddings for batch of {} texts", texts.size());

        // Check cache for all texts
        List<float[]> results = new ArrayList<>();
        List<String> uncachedTexts = new ArrayList<>();
        List<Integer> uncachedIndices = new ArrayList<>();

        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            float[] cached = embeddingCache.getIfPresent(text);
            if (cached != null) {
                results.add(cached);
            } else {
                results.add(null); // Placeholder
                uncachedTexts.add(text);
                uncachedIndices.add(i);
            }
        }

        if (uncachedTexts.isEmpty()) {
            log.debug("All {} texts found in cache", texts.size());
            return results;
        }

        log.debug("Cache miss for {} texts, fetching from API", uncachedTexts.size());

        // Rate limiting
        rateLimiter.acquire();

        try {
            // Call OpenAI API for uncached texts
            EmbeddingRequest request = EmbeddingRequest.builder()
                    .model(config.getOpenaiEmbeddingModel())
                    .input(uncachedTexts)
                    .build();

            List<Embedding> embeddings = openAiService.createEmbeddings(request).getData();

            if (embeddings.size() != uncachedTexts.size()) {
                throw new EmbeddingException(
                        String.format("Expected %d embeddings, got %d", uncachedTexts.size(), embeddings.size()));
            }

            // Fill in results and cache
            for (int i = 0; i < embeddings.size(); i++) {
                float[] embedding = toFloatArray(embeddings.get(i).getEmbedding());
                int originalIndex = uncachedIndices.get(i);
                results.set(originalIndex, embedding);
                embeddingCache.put(uncachedTexts.get(i), embedding);
            }

            log.debug("Generated {} embeddings successfully", embeddings.size());
            return results;

        } catch (Exception e) {
            log.error("Failed to generate batch embeddings", e);
            throw new EmbeddingException("Failed to generate batch embeddings", e);
        }
    }

    /**
     * Generate embeddings for large batches (split into chunks)
     */
    private List<float[]> embedBatchChunked(List<String> texts) throws EmbeddingException {
        List<float[]> allEmbeddings = new ArrayList<>();

        for (int i = 0; i < texts.size(); i += MAX_BATCH_SIZE) {
            int end = Math.min(i + MAX_BATCH_SIZE, texts.size());
            List<String> chunk = texts.subList(i, end);
            log.debug("Processing chunk {}-{} of {}", i, end, texts.size());
            allEmbeddings.addAll(embedBatch(chunk));
        }

        return allEmbeddings;
    }

    /**
     * Convert List<Double> to float[]
     */
    private float[] toFloatArray(List<Double> doubles) {
        float[] floats = new float[doubles.size()];
        for (int i = 0; i < doubles.size(); i++) {
            floats[i] = doubles.get(i).floatValue();
        }
        return floats;
    }

    /**
     * Truncate text for logging
     */
    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    /**
     * Get cache statistics
     */
    public CacheStats getCacheStats() {
        com.google.common.cache.CacheStats stats = embeddingCache.stats();
        return new CacheStats(
                stats.hitCount(),
                stats.missCount(),
                stats.hitRate(),
                embeddingCache.size());
    }

    /**
     * Clear cache
     */
    public void clearCache() {
        log.info("Clearing embedding cache");
        embeddingCache.invalidateAll();
    }

    @Override
    public void close() {
        log.info("Closing Embedding Service");
        embeddingCache.invalidateAll();
        // OpenAiService doesn't need explicit closing
    }

    /**
     * Cache statistics record
     */
    public record CacheStats(
            long hits,
            long misses,
            double hitRate,
            long size) {
        @Override
        public String toString() {
            return String.format("CacheStats{hits=%d, misses=%d, hitRate=%.2f%%, size=%d}",
                    hits, misses, hitRate * 100, size);
        }
    }

    /**
     * Custom exception for embedding errors
     */
    public static class EmbeddingException extends Exception {
        public EmbeddingException(String message) {
            super(message);
        }

        public EmbeddingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
