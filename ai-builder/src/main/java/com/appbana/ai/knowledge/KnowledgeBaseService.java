package com.appbana.ai.knowledge;

import com.appbana.ai.knowledge.SchemaDefinition.SchemaType;
import com.appbana.ai.rag.EmbeddingService;
import com.appbana.ai.rag.EmbeddingService.EmbeddingException;
import com.appbana.ai.rag.QdrantService;
import com.appbana.ai.rag.VectorStoreService;
import com.appbana.ai.rag.VectorStoreService.SearchResult;
import com.appbana.ai.rag.VectorStoreService.VectorStoreException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for indexing and searching AppBana schemas in Qdrant
 * Story 7.2: Vector Store Integration
 * 
 * Features:
 * - Index all AppBana schemas (field types, components, pages, validations)
 * - Semantic search for relevant schemas
 * - Type-filtered search
 * - Example retrieval
 */
@Slf4j
public class KnowledgeBaseService {

    private final QdrantService qdrantService;
    private final VectorStoreService vectorStoreService;
    private final EmbeddingService embeddingService;
    private final AppBanaSchemaLoader schemaLoader;
    private final ObjectMapper objectMapper;

    private boolean initialized = false;
    private long indexedCount = 0;

    public KnowledgeBaseService(
            QdrantService qdrantService,
            VectorStoreService vectorStoreService,
            EmbeddingService embeddingService,
            AppBanaSchemaLoader schemaLoader) {
        this.qdrantService = qdrantService;
        this.vectorStoreService = vectorStoreService;
        this.embeddingService = embeddingService;
        this.schemaLoader = schemaLoader;
        this.objectMapper = new ObjectMapper();

        log.info("KnowledgeBaseService initialized");
    }

    /**
     * Index all schemas from AppBanaSchemaLoader into Qdrant
     * 
     * @throws KnowledgeBaseException if indexing fails
     */
    public void indexAllSchemas() throws KnowledgeBaseException {
        try {
            log.info("Starting to index all AppBana schemas...");

            String collectionName = qdrantService.getAppBanaKnowledgeCollection();
            List<SchemaDefinition> allSchemas = schemaLoader.getAllSchemas();

            log.info("Found {} schemas to index", allSchemas.size());

            int successCount = 0;
            int failCount = 0;

            for (SchemaDefinition schema : allSchemas) {
                try {
                    indexSchemaInternal(schema, collectionName);
                    successCount++;
                } catch (Exception e) {
                    log.error("Failed to index schema: {}", schema.getId(), e);
                    failCount++;
                }
            }

            indexedCount = successCount;
            initialized = true;

            log.info("Indexing complete: {} succeeded, {} failed", successCount, failCount);

            if (failCount > 0) {
                throw new KnowledgeBaseException(
                        String.format("Indexing partially failed: %d/%d schemas failed",
                                failCount, allSchemas.size()));
            }

        } catch (Exception e) {
            log.error("Failed to index schemas", e);
            throw new KnowledgeBaseException("Failed to index schemas", e);
        }
    }

    /**
     * Index a single schema into Qdrant (public method for external loaders)
     * Used by AppBanaKnowledgeLoader to add comprehensive platform knowledge
     * 
     * @param schema The schema to index
     * @throws KnowledgeBaseException if indexing fails
     */
    public void indexSchema(SchemaDefinition schema) throws KnowledgeBaseException {
        try {
            String collectionName = qdrantService.getAppBanaKnowledgeCollection();
            indexSchemaInternal(schema, collectionName);
            indexedCount++;
            initialized = true;
        } catch (Exception e) {
            log.error("Failed to index schema: {}", schema.getId(), e);
            throw new KnowledgeBaseException("Failed to index schema: " + schema.getId(), e);
        }
    }

    /**
     * Index a single schema into Qdrant (internal implementation)
     */
    private void indexSchemaInternal(SchemaDefinition schema, String collectionName)
            throws EmbeddingException, VectorStoreException, JsonProcessingException {

        // Generate searchable text from schema
        String searchableText = buildSearchableText(schema);

        log.debug("Indexing schema: {} - {}", schema.getId(), schema.getName());

        // Generate embedding
        float[] embedding = embeddingService.embed(searchableText);

        // Build metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("schemaId", schema.getId());
        metadata.put("schemaType", schema.getType() != null ? schema.getType() : "entity");
        metadata.put("schemaName", schema.getName());
        metadata.put("description", schema.getDescription());
        metadata.put("examples", objectMapper.writeValueAsString(schema.getExamples()));
        
        // Add category if available
        if (schema.getCategory() != null) {
            metadata.put("category", schema.getCategory());
        }

        // Add schema-specific metadata
        if (schema.getMetadata() != null) {
            metadata.put("schemaMetadata", objectMapper.writeValueAsString(schema.getMetadata()));
        }

        // Add required fields for VectorStoreService
        metadata.put("userId", "system");
        metadata.put("timestamp", System.currentTimeMillis());

        // Store in Qdrant - generate UUID from the ID since Qdrant requires UUID format
        String vectorId = generateUuidFromString(schema.getId());
        vectorStoreService.store(collectionName, vectorId, embedding, metadata);

        log.debug("Successfully indexed schema: {} (vectorId: {})", schema.getId(), vectorId);
    }
    
    /**
     * Generate a deterministic UUID from a string.
     * Uses UUID v5 (name-based UUID) with a namespace UUID.
     */
    private String generateUuidFromString(String input) {
        return UUID.nameUUIDFromBytes(input.getBytes(StandardCharsets.UTF_8)).toString();
    }

    /**
     * Build searchable text from schema definition
     */
    private String buildSearchableText(SchemaDefinition schema) {
        StringBuilder text = new StringBuilder();

        // Add name and description
        text.append(schema.getName()).append(" - ");
        text.append(schema.getDescription());

        // Add examples
        if (schema.getExamples() != null && !schema.getExamples().isEmpty()) {
            text.append(" - Examples: ");
            text.append(String.join(", ", schema.getExamples()));
        }

        // Add metadata for field types
        if (schema.getMetadata() != null && schema.getTypeAsEnum() == SchemaDefinition.SchemaType.ENTITY_FIELD) {
            String htmlType = (String) schema.getMetadata().get("htmlType");
            if (htmlType != null) {
                text.append(" - HTML type: ").append(htmlType);
            }
        }

        return text.toString();
    }

    /**
     * Search for relevant schemas using semantic search
     * 
     * @param query Natural language query
     * @param topK  Number of results to return
     * @return List of relevant schemas
     * @throws KnowledgeBaseException if search fails
     */
    public List<SchemaDefinition> searchRelevantSchemas(String query, int topK)
            throws KnowledgeBaseException {

        if (!initialized) {
            throw new KnowledgeBaseException("Knowledge base not initialized. Call indexAllSchemas() first.");
        }

        try {
            log.debug("Searching for schemas with query: {}", query);

            // Generate query embedding
            float[] queryEmbedding = embeddingService.embed(query);

            // Search in Qdrant
            String collectionName = qdrantService.getAppBanaKnowledgeCollection();
            List<SearchResult> results = vectorStoreService.search(
                    collectionName,
                    queryEmbedding,
                    topK,
                    null);

            log.debug("Found {} results", results.size());

            // Convert search results to SchemaDefinitions
            return results.stream()
                    .map(this::searchResultToSchema)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Failed to search schemas", e);
            throw new KnowledgeBaseException("Failed to search schemas", e);
        }
    }

    /**
     * Search for schemas filtered by type
     * 
     * @param type  Schema type to filter by
     * @param query Natural language query
     * @param topK  Number of results to return
     * @return List of relevant schemas of the specified type
     * @throws KnowledgeBaseException if search fails
     */
    public List<SchemaDefinition> searchByType(
            SchemaDefinition.SchemaType type,
            String query,
            int topK) throws KnowledgeBaseException {

        if (!initialized) {
            throw new KnowledgeBaseException("Knowledge base not initialized. Call indexAllSchemas() first.");
        }

        try {
            log.debug("Searching for {} schemas with query: {}", type, query);

            // Generate query embedding
            float[] queryEmbedding = embeddingService.embed(query);

            // Build filter for schema type - use getValue() to match stored values
            Map<String, Object> filter = Map.of("schemaType", type.getValue());

            // Search in Qdrant with filter
            String collectionName = qdrantService.getAppBanaKnowledgeCollection();
            List<SearchResult> results = vectorStoreService.search(
                    collectionName,
                    queryEmbedding,
                    topK,
                    filter);

            log.debug("Found {} {} results", results.size(), type);

            // Convert search results to SchemaDefinitions
            return results.stream()
                    .map(this::searchResultToSchema)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Failed to search schemas by type", e);
            throw new KnowledgeBaseException("Failed to search schemas by type", e);
        }
    }

    /**
     * Get examples for a specific component type
     * 
     * @param componentType Component type (e.g., "input", "button")
     * @return List of example JSON strings
     * @throws KnowledgeBaseException if retrieval fails
     */
    public List<String> getExamples(String componentType) throws KnowledgeBaseException {
        try {
            log.debug("Getting examples for component: {}", componentType);

            // Get the schema for this component
            SchemaDefinition schema = schemaLoader.getSchema("component_" + componentType);

            if (schema == null) {
                log.warn("No schema found for component: {}", componentType);
                return Collections.emptyList();
            }

            return schema.getExamples() != null ? schema.getExamples() : Collections.emptyList();

        } catch (Exception e) {
            log.error("Failed to get examples for component: {}", componentType, e);
            throw new KnowledgeBaseException("Failed to get examples", e);
        }
    }

    /**
     * Refresh knowledge base (clear and re-index all schemas)
     * 
     * @throws KnowledgeBaseException if refresh fails
     */
    public void refreshKnowledge() throws KnowledgeBaseException {
        try {
            log.info("Refreshing knowledge base...");

            String collectionName = qdrantService.getAppBanaKnowledgeCollection();

            // Delete and recreate collection
            if (qdrantService.collectionExists(collectionName)) {
                log.info("Deleting existing collection: {}", collectionName);
                qdrantService.deleteCollection(collectionName);
            }

            // Re-initialize collections (will recreate the deleted one)
            qdrantService.initializeCollections();

            // Re-index all schemas
            initialized = false;
            indexedCount = 0;
            indexAllSchemas();

            log.info("Knowledge base refreshed successfully");

        } catch (Exception e) {
            log.error("Failed to refresh knowledge base", e);
            throw new KnowledgeBaseException("Failed to refresh knowledge base", e);
        }
    }

    /**
     * Convert SearchResult to SchemaDefinition
     */
    private SchemaDefinition searchResultToSchema(SearchResult result) {
        try {
            Map<String, Object> metadata = result.metadata();

            SchemaDefinition schema = new SchemaDefinition();
            schema.setId((String) metadata.get("schemaId"));
            schema.setName((String) metadata.get("schemaName"));
            schema.setDescription((String) metadata.get("description"));

            // Parse schema type - use String directly (not enum) to support all knowledge types
            SchemaType type = (SchemaType) metadata.get("schemaType");
            if (type != null) {
                schema.setType(type);
            }

            // Parse examples
            String examplesJson = (String) metadata.get("examples");
            if (examplesJson != null) {
                List<String> examples = objectMapper.readValue(
                        examplesJson,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                schema.setExamples(examples);
            }

            // Parse schema metadata
            String schemaMetadataJson = (String) metadata.get("schemaMetadata");
            if (schemaMetadataJson != null) {
                Map<String, Object> schemaMetadata = objectMapper.readValue(
                        schemaMetadataJson,
                        objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
                schema.setMetadata(schemaMetadata);
            }

            return schema;

        } catch (Exception e) {
            log.error("Failed to convert search result to schema", e);
            return null;
        }
    }

    /**
     * Get the number of indexed schemas
     */
    public long getIndexedCount() {
        return indexedCount;
    }

    /**
     * Check if knowledge base is initialized
     */
    public boolean isInitialized() {
        return initialized;
    }
}
