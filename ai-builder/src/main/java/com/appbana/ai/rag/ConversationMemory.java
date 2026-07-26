package com.appbana.ai.rag;

import com.appbana.ai.config.AiConfig;
import com.appbana.ai.rag.EmbeddingService.EmbeddingException;
import com.appbana.ai.rag.VectorStoreService.SearchResult;
import com.appbana.ai.rag.VectorStoreService.VectorStoreException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * Service for managing conversation memory
 * 
 * Features:
 * - Store conversations in PostgreSQL
 * - Auto-generate and store embeddings in Qdrant
 * - Search conversations semantically
 * - Get session history
 * - GDPR-compliant deletion
 * 
 * Story: 1.4 - Implement Conversation Memory
 */
@Slf4j
public class ConversationMemory {

    private final DataSource dataSource;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final QdrantService qdrantService;
    private final AiConfig config;

    private static final String COLLECTION_NAME = "conversations";

    public ConversationMemory(DataSource dataSource, EmbeddingService embeddingService,
            VectorStoreService vectorStoreService, QdrantService qdrantService,
            AiConfig config) {
        this.dataSource = dataSource;
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
        this.qdrantService = qdrantService;
        this.config = config;

        log.info("Conversation Memory initialized");
    }

    /**
     * Store a conversation with auto-embedding
     * 
     * @param conversation Conversation to store
     * @return Stored conversation with ID
     * @throws ConversationMemoryException if storage fails
     */
    public Conversation store(Conversation conversation) throws ConversationMemoryException {
        try {
            log.debug("Storing conversation for user {} in session {}",
                    conversation.getUserId(), conversation.getSessionId());

            // Generate embedding for the message
            String textToEmbed = conversation.getMessage() + " " + conversation.getResponse();
            float[] embedding = embeddingService.embed(textToEmbed);

            // Generate UUID for this conversation
            String conversationId = UUID.randomUUID().toString();
            conversation.setId(conversationId);
            conversation.setCreatedAt(Instant.now());

            // Store in database if available
            if (dataSource != null) {
                storeInDatabase(conversation);
            }

            // Store embedding in Qdrant
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("userId", conversation.getUserId());
            metadata.put("sessionId", conversation.getSessionId().toString());
            metadata.put("timestamp", conversation.getCreatedAt().toEpochMilli());
            metadata.put("text", textToEmbed);
            metadata.put("intent", conversation.getIntent() != null ? conversation.getIntent() : "");

            // Add message and response for retrieval when DB is missing
            metadata.put("message", conversation.getMessage());
            metadata.put("response", conversation.getResponse());

            vectorStoreService.store(COLLECTION_NAME, conversationId, embedding, metadata);

            log.debug("Successfully stored conversation {}", conversationId);
            return conversation;

        } catch (EmbeddingException e) {
            log.error("Failed to generate embedding for conversation", e);
            throw new ConversationMemoryException("Failed to generate embedding", e);
        } catch (VectorStoreException e) {
            log.error("Failed to store embedding in vector store", e);
            throw new ConversationMemoryException("Failed to store embedding", e);
        } catch (SQLException e) {
            log.error("Failed to store conversation in database", e);
            throw new ConversationMemoryException("Failed to store in database", e);
        }
    }

    /**
     * Search for similar conversations
     * 
     * @param query  Search query
     * @param userId Optional user filter
     * @param topK   Number of results
     * @return List of similar conversations
     * @throws ConversationMemoryException if search fails
     */
    public List<Conversation> search(String query, String userId, int topK)
            throws ConversationMemoryException {
        try {
            log.debug("Searching conversations: query='{}', userId={}, topK={}",
                    query, userId, topK);

            // Generate embedding for query
            float[] queryEmbedding = embeddingService.embed(query);

            // Search in vector store
            List<SearchResult> results;
            if (userId != null) {
                results = vectorStoreService.searchByUser(COLLECTION_NAME, queryEmbedding, topK, userId);
            } else {
                results = vectorStoreService.search(COLLECTION_NAME, queryEmbedding, topK, null);
            }

            // Fetch full conversations
            List<Conversation> conversations = new ArrayList<>();
            for (SearchResult result : results) {
                Conversation conv;
                if (dataSource != null) {
                    conv = getById(result.id());
                } else {
                    // Start: Fallback to metadata for search results
                    conv = new Conversation();
                    conv.setId(result.id());
                    conv.setUserId(result.getUserId());
                    
                    Object msg = result.metadata().get("message");
                    if (msg instanceof String) conv.setMessage((String) msg);
                    
                    Object resp = result.metadata().get("response");
                    if (resp instanceof String) conv.setResponse((String) resp);
                    
                    Object intent = result.metadata().get("intent");
                    if (intent instanceof String) conv.setIntent((String) intent);
                    
                    Long ts = result.getTimestamp();
                    if (ts != null) conv.setCreatedAt(Instant.ofEpochMilli(ts));
                    
                    Object sessionIdStr = result.metadata().get("sessionId");
                    if (sessionIdStr instanceof String && !((String) sessionIdStr).isEmpty()) {
                        try {
                            conv.setSessionId(UUID.fromString((String) sessionIdStr));
                        } catch (Exception e) {}
                    }
                }

                if (conv != null) {
                    conversations.add(conv);
                }
            }

            log.debug("Found {} similar conversations", conversations.size());
            return conversations;

        } catch (EmbeddingException e) {
            log.error("Failed to generate query embedding", e);
            throw new ConversationMemoryException("Failed to generate query embedding", e);
        } catch (VectorStoreException e) {
            log.error("Failed to search vector store", e);
            throw new ConversationMemoryException("Failed to search", e);
        }
    }

    /**
     * Search past conversations semantically with time range filter
     */
    public List<Conversation> search(String userId, String query, TimeRange timeRange, int topK)
            throws ConversationMemoryException {
        try {
            log.debug("Searching conversations: query='{}', userId={}, timeRange={}, topK={}",
                    query, userId, timeRange, topK);

            float[] queryEmbedding = embeddingService.embed(query);

            Map<String, Object> filter = new HashMap<>();
            if (userId != null) {
                filter.put("userId", userId);
            }

            if (timeRange != null && timeRange != TimeRange.ALL_TIME) {
                Instant now = Instant.now();
                Instant from = now;
                if (timeRange == TimeRange.LAST_7_DAYS) {
                    from = now.minus(java.time.Duration.ofDays(7));
                } else if (timeRange == TimeRange.LAST_30_DAYS) {
                    from = now.minus(java.time.Duration.ofDays(30));
                }
                filter.put("timestamp_gte", from.toEpochMilli());
            }

            List<SearchResult> results = vectorStoreService.search(COLLECTION_NAME, queryEmbedding, topK, filter);

            List<Conversation> conversations = new ArrayList<>();
            for (SearchResult result : results) {
                Conversation conv;
                if (dataSource != null) {
                    conv = getById(result.id());
                } else {
                    conv = new Conversation();
                    conv.setId(result.id());
                    conv.setUserId(result.getUserId());

                    Object msg = result.metadata().get("message");
                    if (msg instanceof String) conv.setMessage((String) msg);

                    Object resp = result.metadata().get("response");
                    if (resp instanceof String) conv.setResponse((String) resp);

                    Object intent = result.metadata().get("intent");
                    if (intent instanceof String) conv.setIntent((String) intent);

                    Long ts = result.getTimestamp();
                    if (ts != null) conv.setCreatedAt(Instant.ofEpochMilli(ts));

                    Object sessionIdStr = result.metadata().get("sessionId");
                    if (sessionIdStr instanceof String && !((String) sessionIdStr).isEmpty()) {
                        try {
                            conv.setSessionId(UUID.fromString((String) sessionIdStr));
                        } catch (Exception e) {}
                    }
                }

                if (conv != null) {
                    conversations.add(conv);
                }
            }

            log.debug("Found {} similar conversations within time range", conversations.size());
            return conversations;

        } catch (EmbeddingException e) {
            log.error("Failed to generate query embedding", e);
            throw new ConversationMemoryException("Failed to generate query embedding", e);
        } catch (VectorStoreException e) {
            log.error("Failed to search vector store", e);
            throw new ConversationMemoryException("Failed to search", e);
        }
    }

    /**
     * Get conversation history for a session
     * 
     * @param sessionId Session ID
     * @return List of conversations in chronological order
     * @throws ConversationMemoryException if retrieval fails
     */
    public List<Conversation> getSessionHistory(UUID sessionId) throws ConversationMemoryException {
        if (dataSource == null) {
            return getSessionHistoryFromQdrant(sessionId.toString());
        }

        String sql = """
                SELECT id, user_id, session_id, app_id, message, response, intent, feedback, created_at, metadata
                FROM ai_conversations
                WHERE session_id = ?
                ORDER BY created_at ASC
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, sessionId);

            List<Conversation> conversations = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    conversations.add(mapResultSetToConversation(rs));
                }
            }

            log.debug("Retrieved {} conversations for session {}", conversations.size(), sessionId);
            return conversations;

        } catch (SQLException e) {
            log.error("Failed to get session history for {}", sessionId, e);
            throw new ConversationMemoryException("Failed to get session history", e);
        }
    }

    private List<Conversation> getSessionHistoryFromQdrant(String sessionId) throws ConversationMemoryException {
        try {
            // Filter by session ID
            io.qdrant.client.grpc.Points.Filter filter = io.qdrant.client.grpc.Points.Filter.newBuilder()
                    .addMust(io.qdrant.client.grpc.Points.Condition.newBuilder()
                            .setField(io.qdrant.client.grpc.Points.FieldCondition.newBuilder()
                                    .setKey("sessionId")
                                    .setMatch(io.qdrant.client.grpc.Points.Match.newBuilder()
                                            .setKeyword(sessionId)
                                            .build())
                                    .build())
                            .build())
                    .build();

            // Scroll points (fetch all)
            // Note: This fetches top 100 by default. Should be enough for recent context.
            io.qdrant.client.grpc.Points.ScrollPoints scrollPoints = io.qdrant.client.grpc.Points.ScrollPoints
                    .newBuilder()
                    .setCollectionName(COLLECTION_NAME)
                    .setFilter(filter)
                    .setLimit(100)
                    .setWithPayload(
                            io.qdrant.client.grpc.Points.WithPayloadSelector.newBuilder().setEnable(true).build())
                    .build();

            io.qdrant.client.grpc.Points.ScrollResponse response = qdrantService.getClient().scrollAsync(scrollPoints)
                    .get();

            List<Conversation> conversations = new ArrayList<>();
            for (io.qdrant.client.grpc.Points.RetrievedPoint point : response.getResultList()) {
                Map<String, io.qdrant.client.grpc.JsonWithInt.Value> payload = point.getPayloadMap();

                Conversation conv = new Conversation();
                conv.setId(point.getId().getUuid()); // Assuming UUID IDs
                conv.setUserId(payload
                        .getOrDefault("userId",
                                io.qdrant.client.grpc.JsonWithInt.Value.newBuilder().setStringValue("").build())
                        .getStringValue());
                conv.setSessionId(UUID.fromString(sessionId));

                // Extract message and response from payload (we need to ensure store() saves
                // them)
                conv.setMessage(payload
                        .getOrDefault("message",
                                io.qdrant.client.grpc.JsonWithInt.Value.newBuilder().setStringValue("").build())
                        .getStringValue());
                conv.setResponse(payload
                        .getOrDefault("response",
                                io.qdrant.client.grpc.JsonWithInt.Value.newBuilder().setStringValue("").build())
                        .getStringValue());

                long timestamp = payload
                        .getOrDefault("timestamp",
                                io.qdrant.client.grpc.JsonWithInt.Value.newBuilder().setIntegerValue(0).build())
                        .getIntegerValue();
                conv.setCreatedAt(Instant.ofEpochMilli(timestamp));

                conversations.add(conv);
            }

            // Sort by timestamp ASC
            conversations.sort(Comparator.comparing(Conversation::getCreatedAt));

            return conversations;

        } catch (Exception e) {
            log.error("Failed to get session history from Qdrant", e);
            throw new ConversationMemoryException("Failed to get session history from Qdrant", e);
        }
    }

    /**
     * Get recent conversations for a user
     * 
     * @param userId User ID
     * @param limit  Maximum number of conversations
     * @return List of recent conversations
     * @throws ConversationMemoryException if retrieval fails
     */
    public List<Conversation> getRecentByUser(String userId, int limit)
            throws ConversationMemoryException {
        if (dataSource == null) {
            return new ArrayList<>();
        }

        String sql = """
                SELECT id, user_id, session_id, app_id, message, response, intent, feedback, created_at, metadata
                FROM ai_conversations
                WHERE user_id = ?
                ORDER BY created_at DESC
                LIMIT ?
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, userId);
            stmt.setInt(2, limit);

            List<Conversation> conversations = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    conversations.add(mapResultSetToConversation(rs));
                }
            }

            log.debug("Retrieved {} recent conversations for user {}", conversations.size(), userId);
            return conversations;

        } catch (SQLException e) {
            log.error("Failed to get recent conversations for user {}", userId, e);
            throw new ConversationMemoryException("Failed to get recent conversations", e);
        }
    }

    /**
     * Delete all conversations for a user (GDPR compliance)
     * 
     * @param userId User ID
     * @throws ConversationMemoryException if deletion fails
     */
    public void deleteByUser(String userId) throws ConversationMemoryException {
        try {
            log.info("Deleting all conversations for user {}", userId);

            // Delete from vector store
            vectorStoreService.deleteByUser(COLLECTION_NAME, userId);

            // Delete from database if available
            if (dataSource != null) {
                String sql = "DELETE FROM ai_conversations WHERE user_id = ?";
                try (Connection conn = dataSource.getConnection();
                        PreparedStatement stmt = conn.prepareStatement(sql)) {

                    stmt.setString(1, userId);
                    int deleted = stmt.executeUpdate();

                    log.info("Deleted {} conversations for user {}", deleted, userId);
                }
            }

        } catch (VectorStoreException e) {
            log.error("Failed to delete from vector store for user {}", userId, e);
            throw new ConversationMemoryException("Failed to delete from vector store", e);
        } catch (SQLException e) {
            log.error("Failed to delete from database for user {}", userId, e);
            throw new ConversationMemoryException("Failed to delete from database", e);
        }
    }

    /**
     * Update feedback for a conversation
     * 
     * @param conversationId Conversation ID
     * @param feedback       Feedback value (-1, 0, 1)
     * @throws ConversationMemoryException if update fails
     */
    public void updateFeedback(String conversationId, int feedback)
            throws ConversationMemoryException {
        if (feedback < -1 || feedback > 1) {
            throw new IllegalArgumentException("Feedback must be -1, 0, or 1");
        }

        String sql = "UPDATE ai_conversations SET feedback = ? WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, feedback);
            stmt.setObject(2, UUID.fromString(conversationId));

            int updated = stmt.executeUpdate();
            if (updated == 0) {
                log.warn("No conversation found with ID {}", conversationId);
            } else {
                log.debug("Updated feedback for conversation {}", conversationId);
            }

        } catch (SQLException e) {
            log.error("Failed to update feedback for conversation {}", conversationId, e);
            throw new ConversationMemoryException("Failed to update feedback", e);
        }
    }

    // Private helper methods

    private void storeInDatabase(Conversation conversation) throws SQLException {
        String sql = """
                INSERT INTO ai_conversations
                    (id, user_id, session_id, app_id, message, response, intent, feedback, created_at, metadata)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, UUID.fromString(conversation.getId()));
            stmt.setString(2, conversation.getUserId());
            stmt.setObject(3, conversation.getSessionId());
            stmt.setString(4, conversation.getAppId());
            stmt.setString(5, conversation.getMessage());
            stmt.setString(6, conversation.getResponse());
            stmt.setString(7, conversation.getIntent());
            stmt.setInt(8, conversation.getFeedback());
            stmt.setTimestamp(9, Timestamp.from(conversation.getCreatedAt()));
            stmt.setString(10, conversation.getMetadata() != null ? conversation.getMetadata().toString() : "{}");

            stmt.executeUpdate();
        }
    }

    private Conversation getById(String id) {
        String sql = """
                SELECT id, user_id, session_id, app_id, message, response, intent, feedback, created_at, metadata
                FROM ai_conversations
                WHERE id = ?
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, UUID.fromString(id));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToConversation(rs);
                }
            }

        } catch (SQLException e) {
            log.error("Failed to get conversation by ID {}", id, e);
        }

        return null;
    }

    private Conversation mapResultSetToConversation(ResultSet rs) throws SQLException {
        Conversation conv = new Conversation();
        conv.setId(rs.getObject("id", UUID.class).toString());
        conv.setUserId(rs.getString("user_id"));
        conv.setSessionId(rs.getObject("session_id", UUID.class));
        // app_id column added in V005 â€” tolerate older rows where it may be missing
        try {
            conv.setAppId(rs.getString("app_id"));
        } catch (SQLException ignored) {
            // Older ResultSet may not include the column; leave appId null.
        }
        conv.setMessage(rs.getString("message"));
        conv.setResponse(rs.getString("response"));
        conv.setIntent(rs.getString("intent"));
        conv.setFeedback(rs.getInt("feedback"));
        conv.setCreatedAt(rs.getTimestamp("created_at").toInstant());

        String metadataJson = rs.getString("metadata");
        if (metadataJson != null && !metadataJson.isEmpty()) {
            // Parse JSON metadata if needed
            conv.setMetadata(new HashMap<>());
        }

        return conv;
    }

    // -------------------------------------------------------------------------
    // Session summary + metadata management (Stage 3 — session picker)
    // -------------------------------------------------------------------------

    /**
     * Return one row per session for the given user, optionally scoped to an
     * app. Includes the persisted title (from ai_chat_session_meta) or falls
     * back to the first user message. Deleted sessions are excluded.
     */
    public List<SessionSummary> listSessionSummaries(String userId, String appId, int limit)
            throws ConversationMemoryException {
        if (dataSource == null) return new ArrayList<>();

        // Deterministic aggregation: pick each session's earliest message as
        // the preview, and the latest created_at as the sort key.
        String sql = """
                SELECT c.session_id                       AS session_id,
                       MAX(c.created_at)                  AS last_activity,
                       MIN(c.created_at)                  AS first_activity,
                       (ARRAY_AGG(c.message ORDER BY c.created_at ASC))[1] AS first_message,
                       MAX(c.app_id)                      AS app_id,
                       COUNT(*)                           AS turn_count,
                       m.title                            AS title
                FROM ai_conversations c
                LEFT JOIN ai_chat_session_meta m ON m.session_id = c.session_id
                WHERE c.user_id = ?
                  AND (?::text IS NULL OR c.app_id = ?)
                  AND COALESCE(m.is_deleted, FALSE) = FALSE
                GROUP BY c.session_id, m.title
                ORDER BY last_activity DESC
                LIMIT ?
                """;

        List<SessionSummary> out = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            if (appId == null || appId.isBlank()) {
                stmt.setNull(2, java.sql.Types.VARCHAR);
                stmt.setNull(3, java.sql.Types.VARCHAR);
            } else {
                stmt.setString(2, appId);
                stmt.setString(3, appId);
            }
            stmt.setInt(4, Math.clamp(limit, 1, 200));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    SessionSummary s = new SessionSummary();
                    s.sessionId    = rs.getObject("session_id", UUID.class).toString();
                    s.lastActivity = rs.getTimestamp("last_activity") != null
                            ? rs.getTimestamp("last_activity").toInstant().toEpochMilli() : 0L;
                    s.appId        = rs.getString("app_id");
                    s.turnCount    = rs.getInt("turn_count");
                    String title   = rs.getString("title");
                    String first   = rs.getString("first_message");
                    s.title        = (title != null && !title.isBlank())
                            ? title
                            : truncate(first, 60);
                    out.add(s);
                }
            }
        } catch (SQLException e) {
            log.error("[ConversationMemory] Failed to list session summaries", e);
            throw new ConversationMemoryException("Failed to list session summaries", e);
        }
        return out;
    }

    /** Rename (or create) a session-level title. */
    public void renameSession(String userId, UUID sessionId, String newTitle)
            throws ConversationMemoryException {
        if (dataSource == null) return;
        String sql = """
                INSERT INTO ai_chat_session_meta (session_id, user_id, title, is_deleted, updated_at)
                VALUES (?, ?, ?, FALSE, NOW())
                ON CONFLICT (session_id) DO UPDATE
                    SET title = EXCLUDED.title, updated_at = NOW()
                """;
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, sessionId);
            stmt.setString(2, userId);
            stmt.setString(3, newTitle);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("[ConversationMemory] Failed to rename session {}", sessionId, e);
            throw new ConversationMemoryException("Failed to rename session", e);
        }
    }

    /** Soft-delete a session so it disappears from the picker. */
    public void deleteSession(String userId, UUID sessionId)
            throws ConversationMemoryException {
        if (dataSource == null) return;
        String sql = """
                INSERT INTO ai_chat_session_meta (session_id, user_id, title, is_deleted, updated_at)
                VALUES (?, ?, NULL, TRUE, NOW())
                ON CONFLICT (session_id) DO UPDATE
                    SET is_deleted = TRUE, updated_at = NOW()
                """;
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, sessionId);
            stmt.setString(2, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("[ConversationMemory] Failed to soft-delete session {}", sessionId, e);
            throw new ConversationMemoryException("Failed to delete session", e);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "\u2026";
    }

    /** Flat summary row used by the studio session picker. */
    @Data
    public static class SessionSummary {
        private String sessionId;
        private String title;
        private String appId;
        private long lastActivity;
        private int turnCount;
    }

    /**
     * Conversation data class
     */
    @Data
    public static class Conversation {
        private String id;
        private String userId;
        private UUID sessionId;
        private String appId;
        private String message;
        private String response;
        private String intent;
        private int feedback = 0;
        private Instant createdAt;
        private Map<String, Object> metadata;
    }

    /**
     * Custom exception for conversation memory errors
     */
    public static class ConversationMemoryException extends Exception {
        public ConversationMemoryException(String message) {
            super(message);
        }

        public ConversationMemoryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
