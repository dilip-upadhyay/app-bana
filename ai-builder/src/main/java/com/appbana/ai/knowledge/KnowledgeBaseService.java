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
     * Checks if the Qdrant collection is already populated with data.
     * If so, sets the initialized flag to true to skip reloading on every boot.
     */
    public boolean initializeIfPopulated() {
        try {
            String collectionName = qdrantService.getAppBanaKnowledgeCollection();
            if (qdrantService.collectionExists(collectionName)) {
                long points = qdrantService.getCollectionInfo(collectionName).getPointsCount();
                if (points > 0) {
                    this.indexedCount = points;
                    this.initialized = true;
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to check collection info, assuming not populated: {}", e.getMessage());
        }
        return false;
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

            log.info("Found {} schemas to index — using batch embedding", allSchemas.size());

            // Build all searchable texts up-front
            List<String> searchableTexts = allSchemas.stream()
                    .map(this::buildSearchableText)
                    .collect(Collectors.toList());

            // Batch embed: 1 API call per 100 schemas instead of N sequential calls
            List<float[]> embeddings = embeddingService.embedBatch(searchableTexts);

            int successCount = 0;
            int failCount = 0;

            for (int i = 0; i < allSchemas.size(); i++) {
                try {
                    storeSchemaEmbedding(allSchemas.get(i), embeddings.get(i), collectionName);
                    successCount++;
                } catch (Exception e) {
                    log.error("Failed to store schema: {}", allSchemas.get(i).getId(), e);
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

        } catch (KnowledgeBaseException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to index schemas", e);
            throw new KnowledgeBaseException("Failed to index schemas", e);
        }
    }

    /**
     * Always sync domain templates on startup — upserts are idempotent so existing
     * installations pick up new templates without a full re-index.
     */
    public void syncDomainTemplates() {
        List<SchemaDefinition> templates = schemaLoader.getAllSchemas().stream()
                .filter(s -> "domain-template".equals(s.getCategory()))
                .collect(Collectors.toList());

        if (templates.isEmpty()) {
            return;
        }

        log.info("Syncing {} domain templates (upsert, idempotent)...", templates.size());
        String collectionName = qdrantService.getAppBanaKnowledgeCollection();
        int synced = 0;

        for (SchemaDefinition template : templates) {
            try {
                indexSchemaInternal(template, collectionName);
                synced++;
            } catch (Exception e) {
                log.warn("Failed to sync domain template '{}': {}", template.getId(), e.getMessage());
            }
        }

        initialized = true; // ensure search works even on existing populated DB
        log.info("Domain template sync complete: {}/{}", synced, templates.size());
    }

    /**
     * Store a schema with a pre-computed embedding (used by batch indexing path).
     */
    private void storeSchemaEmbedding(SchemaDefinition schema, float[] embedding, String collectionName)
            throws VectorStoreException, JsonProcessingException {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("schemaId", schema.getId());
        metadata.put("schemaType", schema.getType() != null ? schema.getType() : "entity");
        metadata.put("schemaName", schema.getName());
        metadata.put("description", schema.getDescription());
        metadata.put("examples", objectMapper.writeValueAsString(schema.getExamples()));
        if (schema.getCategory() != null) {
            metadata.put("category", schema.getCategory());
        }
        if (schema.getMetadata() != null) {
            metadata.put("schemaMetadata", objectMapper.writeValueAsString(schema.getMetadata()));
        }
        metadata.put("userId", "system");
        metadata.put("timestamp", System.currentTimeMillis());

        String vectorId = generateUuidFromString(schema.getId());
        vectorStoreService.store(collectionName, vectorId, embedding, metadata);
        log.debug("Stored schema: {}", schema.getId());
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

        // Add keyword examples
        if (schema.getExamples() != null && !schema.getExamples().isEmpty()) {
            text.append(" - Examples: ");
            text.append(String.join(", ", schema.getExamples()));
        }

        if (schema.getMetadata() != null) {
            // Domain templates: include entity field definitions — the richest semantic signal
            if ("domain-template".equals(schema.getCategory())) {
                @SuppressWarnings("unchecked")
                Map<String, String> entities = (Map<String, String>) schema.getMetadata().get("entities");
                if (entities != null) {
                    text.append(" - Entities: ");
                    entities.forEach((entityName, fields) ->
                            text.append(entityName).append("(").append(fields).append(") "));
                }
            }
            // Entity field types: include HTML input type
            if (schema.getTypeAsEnum() == SchemaDefinition.SchemaType.ENTITY_FIELD) {
                String htmlType = (String) schema.getMetadata().get("htmlType");
                if (htmlType != null) {
                    text.append(" - HTML type: ").append(htmlType);
                }
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
     * Retrieve domain-specific few-shot examples for the given user query.
     * Searches only among schemas of category "domain-template", returning the
     * most semantically similar domains to the user's app description.
     *
     * @param query the user's app description / message
     * @param topK  max number of domain examples to return
     * @return list of matching domain templates (may be empty if not initialized)
     */
    public List<SchemaDefinition> getDomainExamples(String query, int topK) {
        if (!initialized) {
            log.debug("[KnowledgeBase] Not initialized — skipping domain examples");
            return List.of();
        }
        try {
            float[] queryEmbedding = embeddingService.embed(query);
            String collectionName = qdrantService.getAppBanaKnowledgeCollection();
            Map<String, Object> filter = Map.of("category", "domain-template");

            List<VectorStoreService.SearchResult> results = vectorStoreService.search(
                    collectionName, queryEmbedding, topK, filter);

            return results.stream()
                    .map(this::searchResultToSchema)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.debug("[KnowledgeBase] getDomainExamples failed (non-critical): {}", e.getMessage());
            return List.of();
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

            // Parse schema type safely — Qdrant returns the wire value ("field-type"), which is
            // NOT the enum constant name (ENTITY_FIELD), so valueOf() would throw and silently
            // leave the type null on every field-type hit.
            String typeStr = (String) metadata.get("schemaType");
            SchemaType parsedType = SchemaType.fromValue(typeStr);
            if (parsedType != null) {
                schema.setType(parsedType);
            } else if (typeStr != null) {
                // Custom type like "domain-template" — not an enum value, keep the raw string.
                schema.setRawType(typeStr);
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
