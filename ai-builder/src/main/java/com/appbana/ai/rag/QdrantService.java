package com.appbana.ai.rag;

import com.appbana.ai.config.AiConfig;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.*;
import io.qdrant.client.grpc.Points.*;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutionException;

/**
 * Service for managing Qdrant vector database
 * 
 * Responsibilities:
 * - Initialize connection to Qdrant
 * - Create and manage collections
 * - Health checks
 * - Connection pooling
 * 
 * Story: 1.1 - Set Up Qdrant Vector Database
 */
@Slf4j
public class QdrantService implements AutoCloseable {

    private final AiConfig config;
    private final QdrantClient client;

    // Collection names
    private static final String COLLECTION_CONVERSATIONS = "conversations";
    private static final String COLLECTION_PATTERNS = "app_patterns";
    private static final String COLLECTION_APPBANA_KNOWLEDGE = "appbana_knowledge";
    private static final int VECTOR_SIZE = 1536; // OpenAI text-embedding-3-small dimension

    public QdrantService(AiConfig config) {
        this.config = config;

        log.info("Initializing Qdrant client: {}:{}", config.getQdrantHost(), config.getQdrantPort());

        // Initialize Qdrant client
        QdrantGrpcClient.Builder builder = QdrantGrpcClient.newBuilder(
                config.getQdrantHost(),
                config.getQdrantPort(),
                false // useTls
        );

        // Add API key if configured
        if (config.getQdrantApiKey() != null && !config.getQdrantApiKey().isEmpty()) {
            builder.withApiKey(config.getQdrantApiKey());
        }

        this.client = new QdrantClient(builder.build());

        log.info("Qdrant client initialized successfully");
    }

    /**
     * Initialize collections (create if they don't exist)
     * This is called during service startup
     */
    public void initializeCollections() {
        try {
            log.info("Initializing Qdrant collections...");

            // Create conversations collection
            createCollectionIfNotExists(
                    COLLECTION_CONVERSATIONS,
                    VECTOR_SIZE,
                    Distance.Cosine);

            // Create payload indexes for reliable filtering
            createPayloadIndex(COLLECTION_CONVERSATIONS, "sessionId", PayloadSchemaType.Keyword);
            createPayloadIndex(COLLECTION_CONVERSATIONS, "userId", PayloadSchemaType.Keyword);

            // Create app patterns collection
            createCollectionIfNotExists(
                    COLLECTION_PATTERNS,
                    VECTOR_SIZE,
                    Distance.Cosine);

            // Create AppBana knowledge collection
            createCollectionIfNotExists(
                    COLLECTION_APPBANA_KNOWLEDGE,
                    VECTOR_SIZE,
                    Distance.Cosine);

            // Payload indexes for filtered queries on knowledge collection
            createPayloadIndex(COLLECTION_APPBANA_KNOWLEDGE, "category", PayloadSchemaType.Keyword);
            createPayloadIndex(COLLECTION_APPBANA_KNOWLEDGE, "schemaType", PayloadSchemaType.Keyword);

            log.info("Qdrant collections initialized successfully");

        } catch (Exception e) {
            log.error("Failed to initialize Qdrant collections", e);
            throw new RuntimeException("Failed to initialize Qdrant collections", e);
        }
    }

    /**
     * Create collection if it doesn't exist
     */
    private void createCollectionIfNotExists(String collectionName, int vectorSize, Distance distance)
            throws ExecutionException, InterruptedException {

        // Check if collection exists
        if (collectionExists(collectionName)) {
            log.info("Collection '{}' already exists", collectionName);

            // Verify collection configuration
            CollectionInfo info = getCollectionInfo(collectionName);
            log.info("Collection '{}' has {} points", collectionName, info.getPointsCount());
            return;
        }

        log.info("Creating collection '{}' with vector size {} and distance {}",
                collectionName, vectorSize, distance);

        // Create collection
        VectorParams vectorParams = VectorParams.newBuilder()
                .setSize(vectorSize)
                .setDistance(distance)
                .build();

        CreateCollection createCollection = CreateCollection.newBuilder()
                .setCollectionName(collectionName)
                .setVectorsConfig(VectorsConfig.newBuilder()
                        .setParams(vectorParams)
                        .build())
                .build();

        client.createCollectionAsync(createCollection).get();

        log.info("Collection '{}' created successfully", collectionName);
    }

    /**
     * Check if collection exists
     */
    public boolean collectionExists(String collectionName) {
        try {
            java.util.List<String> collections = client.listCollectionsAsync().get();
            return collections.contains(collectionName);
        } catch (Exception e) {
            log.error("Error checking if collection exists: {}", collectionName, e);
            return false;
        }
    }

    /**
     * Get collection information
     */
    public CollectionInfo getCollectionInfo(String collectionName)
            throws ExecutionException, InterruptedException {
        return client.getCollectionInfoAsync(collectionName).get();
    }

    /**
     * Delete collection (for testing/cleanup)
     */
    public void deleteCollection(String collectionName) {
        try {
            log.warn("Deleting collection: {}", collectionName);
            client.deleteCollectionAsync(collectionName).get();
            log.info("Collection '{}' deleted", collectionName);
        } catch (Exception e) {
            log.error("Error deleting collection: {}", collectionName, e);
            throw new RuntimeException("Failed to delete collection: " + collectionName, e);
        }
    }

    /**
     * Health check - verify connection to Qdrant
     */
    public boolean healthCheck() {
        try {
            // Try to list collections as a health check
            client.listCollectionsAsync().get();
            return true;
        } catch (Exception e) {
            log.error("Qdrant health check failed", e);
            return false;
        }
    }

    /**
     * Get the underlying Qdrant client
     * Used by other services (VectorStoreService, etc.)
     */
    public QdrantClient getClient() {
        return client;
    }

    /**
     * Get conversations collection name
     */
    public String getConversationsCollection() {
        return COLLECTION_CONVERSATIONS;
    }

    /**
     * Get patterns collection name
     */
    public String getPatternsCollection() {
        return COLLECTION_PATTERNS;
    }

    /**
     * Create payload index for a field
     */
    private void createPayloadIndex(String collectionName, String fieldName, PayloadSchemaType fieldType) {
        try {
            log.info("Creating payload index for field '{}' in collection '{}'", fieldName, collectionName);
            client.createPayloadIndexAsync(collectionName, fieldName, fieldType, null, null, null, null).get();
            log.info("Payload index created successfully");
        } catch (Exception e) {
            // Log but don't fail startup if index already exists or error occurs
            log.warn("Failed to create payload index for field '{}' (might already exist): {}", fieldName,
                    e.getMessage());
        }
    }

    /**
     * Get AppBana knowledge collection name
     */
    public String getAppBanaKnowledgeCollection() {
        return COLLECTION_APPBANA_KNOWLEDGE;
    }

    @Override
    public void close() {
        try {
            log.info("Closing Qdrant client");
            client.close();
        } catch (Exception e) {
            log.error("Error closing Qdrant client", e);
        }
    }
}
