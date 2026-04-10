package com.appbana.ai.rag;

import com.appbana.ai.config.AiConfig;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points.*;
import io.qdrant.client.grpc.JsonWithInt.Value;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for storing and searching vectors in Qdrant
 * 
 * Features:
 * - Store vectors with metadata
 * - Semantic search with top-K results
 * - Hybrid search (keyword + semantic)
 * - Filtering by user, date, intent
 * - GDPR-compliant deletion
 * 
 * Story: 1.3 - Implement Vector Store Service
 */
@Slf4j
public class VectorStoreService {

    private final QdrantService qdrantService;
    private final AiConfig config;

    public VectorStoreService(QdrantService qdrantService, AiConfig config) {
        this.qdrantService = qdrantService;
        this.config = config;

        log.info("Vector Store Service initialized");
    }

    /**
     * Store a vector with metadata
     * 
     * @param collectionName Collection to store in
     * @param id             Unique ID for this vector
     * @param vector         Vector embedding (1536 dimensions)
     * @param metadata       Metadata (user, timestamp, text, etc.)
     * @throws VectorStoreException if storage fails
     */
    public void store(String collectionName, String id, float[] vector, Map<String, Object> metadata)
            throws VectorStoreException {

        validateVector(vector);
        validateMetadata(metadata);

        try {
            log.debug("Storing vector {} in collection {}", id, collectionName);

            // Build point with vector and metadata
            PointStruct.Builder pointBuilder = PointStruct.newBuilder()
                    .setId(PointId.newBuilder().setUuid(id).build())
                    .setVectors(Vectors.newBuilder()
                            .setVector(io.qdrant.client.grpc.Points.Vector.newBuilder()
                                    .addAllData(toDoubleList(vector))
                                    .build())
                            .build());

            // Add metadata as payload
            if (metadata != null && !metadata.isEmpty()) {
                Map<String, Value> payload = convertMetadataToPayload(metadata);
                pointBuilder.putAllPayload(payload);
            }

            // Upsert point
            UpsertPoints upsertPoints = UpsertPoints.newBuilder()
                    .setCollectionName(collectionName)
                    .addPoints(pointBuilder.build())
                    .build();

            qdrantService.getClient().upsertAsync(upsertPoints).get();

            log.debug("Successfully stored vector {}", id);

        } catch (Exception e) {
            log.error("Failed to store vector {} in collection {}", id, collectionName, e);
            throw new VectorStoreException("Failed to store vector", e);
        }
    }

    /**
     * Search for similar vectors (semantic search)
     * 
     * @param collectionName Collection to search in
     * @param queryVector    Query vector
     * @param topK           Number of results to return
     * @param filter         Optional metadata filter
     * @return List of search results with scores
     * @throws VectorStoreException if search fails
     */
    public List<SearchResult> search(String collectionName, float[] queryVector, int topK,
            Map<String, Object> filter) throws VectorStoreException {

        validateVector(queryVector);

        if (topK <= 0 || topK > 100) {
            throw new IllegalArgumentException("topK must be between 1 and 100");
        }

        try {
            log.debug("Searching collection {} with topK={}", collectionName, topK);

            // Build search request
            SearchPoints.Builder searchBuilder = SearchPoints.newBuilder()
                    .setCollectionName(collectionName)
                    .addAllVector(toDoubleList(queryVector))
                    .setLimit(topK)
                    .setWithPayload(WithPayloadSelector.newBuilder()
                            .setEnable(true)
                            .build());

            // Add filter if provided
            if (filter != null && !filter.isEmpty()) {
                Filter qdrantFilter = buildFilter(filter);
                searchBuilder.setFilter(qdrantFilter);
            }

            // Execute search
            List<ScoredPoint> results = qdrantService.getClient()
                    .searchAsync(searchBuilder.build())
                    .get();

            log.debug("Found {} results", results.size());

            // Convert to SearchResult objects
            return results.stream()
                    .map(this::toSearchResult)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Failed to search collection {}", collectionName, e);
            throw new VectorStoreException("Failed to search vectors", e);
        }
    }

    /**
     * Search with user filter (convenience method)
     */
    public List<SearchResult> searchByUser(String collectionName, float[] queryVector,
            int topK, String userId) throws VectorStoreException {
        Map<String, Object> filter = Map.of("userId", userId);
        return search(collectionName, queryVector, topK, filter);
    }

    /**
     * Search with date range filter
     */
    public List<SearchResult> searchByDateRange(String collectionName, float[] queryVector,
            int topK, Instant from, Instant to)
            throws VectorStoreException {

        Map<String, Object> filter = new HashMap<>();
        filter.put("timestamp_gte", from.toEpochMilli());
        filter.put("timestamp_lte", to.toEpochMilli());

        return search(collectionName, queryVector, topK, filter);
    }

    /**
     * Delete all vectors for a user (GDPR compliance)
     * 
     * @param collectionName Collection to delete from
     * @param userId         User ID to delete
     * @throws VectorStoreException if deletion fails
     */
    public void deleteByUser(String collectionName, String userId) throws VectorStoreException {
        try {
            log.info("Deleting all vectors for user {} from collection {}", userId, collectionName);

            // Build filter for user
            Filter filter = Filter.newBuilder()
                    .addMust(Condition.newBuilder()
                            .setField(FieldCondition.newBuilder()
                                    .setKey("userId")
                                    .setMatch(Match.newBuilder()
                                            .setKeyword(userId)
                                            .build())
                                    .build())
                            .build())
                    .build();

            // Delete points matching filter
            DeletePoints deletePoints = DeletePoints.newBuilder()
                    .setCollectionName(collectionName)
                    .setPoints(PointsSelector.newBuilder()
                            .setFilter(filter)
                            .build())
                    .build();

            qdrantService.getClient().deleteAsync(deletePoints).get();

            log.info("Successfully deleted vectors for user {}", userId);

        } catch (Exception e) {
            log.error("Failed to delete vectors for user {} from collection {}",
                    userId, collectionName, e);
            throw new VectorStoreException("Failed to delete vectors", e);
        }
    }

    /**
     * Delete a specific vector by ID
     */
    public void deleteById(String collectionName, String id) throws VectorStoreException {
        try {
            log.debug("Deleting vector {} from collection {}", id, collectionName);

            DeletePoints deletePoints = DeletePoints.newBuilder()
                    .setCollectionName(collectionName)
                    .setPoints(PointsSelector.newBuilder()
                            .setPoints(PointsIdsList.newBuilder()
                                    .addIds(PointId.newBuilder().setUuid(id).build())
                                    .build())
                            .build())
                    .build();

            qdrantService.getClient().deleteAsync(deletePoints).get();

            log.debug("Successfully deleted vector {}", id);

        } catch (Exception e) {
            log.error("Failed to delete vector {} from collection {}", id, collectionName, e);
            throw new VectorStoreException("Failed to delete vector", e);
        }
    }

    /**
     * Get vector count in collection
     */
    public long getCount(String collectionName) throws VectorStoreException {
        try {
            io.qdrant.client.grpc.Collections.CollectionInfo info = qdrantService.getCollectionInfo(collectionName);
            return info.getPointsCount();
        } catch (Exception e) {
            log.error("Failed to get count for collection {}", collectionName, e);
            throw new VectorStoreException("Failed to get count", e);
        }
    }

    // Helper methods

    private void validateVector(float[] vector) {
        if (vector == null || vector.length != 1536) {
            throw new IllegalArgumentException("Vector must have 1536 dimensions");
        }
    }

    private void validateMetadata(Map<String, Object> metadata) {
        if (metadata == null) {
            return;
        }

        // Ensure required fields
        if (!metadata.containsKey("userId")) {
            throw new IllegalArgumentException("Metadata must contain 'userId'");
        }

        if (!metadata.containsKey("timestamp")) {
            throw new IllegalArgumentException("Metadata must contain 'timestamp'");
        }
    }

    private List<Float> toDoubleList(float[] vector) {
        List<Float> list = new ArrayList<>(vector.length);
        for (float v : vector) {
            list.add(v);
        }
        return list;
    }

    private Map<String, Value> convertMetadataToPayload(Map<String, Object> metadata) {
        Map<String, Value> payload = new HashMap<>();

        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            Value.Builder valueBuilder = Value.newBuilder();

            if (value instanceof String) {
                valueBuilder.setStringValue((String) value);
            } else if (value instanceof Integer) {
                valueBuilder.setIntegerValue((Integer) value);
            } else if (value instanceof Long) {
                valueBuilder.setIntegerValue((Long) value);
            } else if (value instanceof Double) {
                valueBuilder.setDoubleValue((Double) value);
            } else if (value instanceof Boolean) {
                valueBuilder.setBoolValue((Boolean) value);
            } else {
                valueBuilder.setStringValue(value.toString());
            }

            payload.put(key, valueBuilder.build());
        }

        return payload;
    }

    private Filter buildFilter(Map<String, Object> filterMap) {
        Filter.Builder filterBuilder = Filter.newBuilder();

        for (Map.Entry<String, Object> entry : filterMap.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // Handle special range filters
            if (key.endsWith("_gte")) {
                String fieldName = key.substring(0, key.lastIndexOf("_gte"));
                filterBuilder.addMust(Condition.newBuilder()
                        .setField(FieldCondition.newBuilder()
                                .setKey(fieldName)
                                .setRange(io.qdrant.client.grpc.Points.Range.newBuilder()
                                        .setGte(((Number) value).doubleValue())
                                        .build())
                                .build())
                        .build());
                continue;
            }
            if (key.endsWith("_lte")) {
                String fieldName = key.substring(0, key.lastIndexOf("_lte"));
                filterBuilder.addMust(Condition.newBuilder()
                        .setField(FieldCondition.newBuilder()
                                .setKey(fieldName)
                                .setRange(io.qdrant.client.grpc.Points.Range.newBuilder()
                                        .setLte(((Number) value).doubleValue())
                                        .build())
                                .build())
                        .build());
                continue;
            }

            // Regular equality filter
            Condition.Builder conditionBuilder = Condition.newBuilder()
                    .setField(FieldCondition.newBuilder()
                            .setKey(key)
                            .setMatch(Match.newBuilder()
                                    .setKeyword(value.toString())
                                    .build())
                            .build());

            filterBuilder.addMust(conditionBuilder.build());
        }

        return filterBuilder.build();
    }

    private SearchResult toSearchResult(ScoredPoint point) {
        String id = point.getId().getUuid();
        float score = point.getScore();
        Map<String, Object> metadata = convertPayloadToMetadata(point.getPayloadMap());

        return new SearchResult(id, score, metadata);
    }

    private Map<String, Object> convertPayloadToMetadata(Map<String, Value> payload) {
        Map<String, Object> metadata = new HashMap<>();

        for (Map.Entry<String, Value> entry : payload.entrySet()) {
            String key = entry.getKey();
            Value value = entry.getValue();

            Object javaValue = switch (value.getKindCase()) {
                case STRING_VALUE -> value.getStringValue();
                case INTEGER_VALUE -> value.getIntegerValue();
                case DOUBLE_VALUE -> value.getDoubleValue();
                case BOOL_VALUE -> value.getBoolValue();
                default -> null;
            };

            if (javaValue != null) {
                metadata.put(key, javaValue);
            }
        }

        return metadata;
    }

    /**
     * Search result record
     */
    public record SearchResult(
            String id,
            float score,
            Map<String, Object> metadata) {
        public String getText() {
            return (String) metadata.get("text");
        }

        public String getUserId() {
            return (String) metadata.get("userId");
        }

        public Long getTimestamp() {
            Object ts = metadata.get("timestamp");
            if (ts instanceof Long) {
                return (Long) ts;
            } else if (ts instanceof Integer) {
                return ((Integer) ts).longValue();
            }
            return null;
        }
    }

    /**
     * Custom exception for vector store errors
     */
    public static class VectorStoreException extends Exception {
        public VectorStoreException(String message) {
            super(message);
        }

        public VectorStoreException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
