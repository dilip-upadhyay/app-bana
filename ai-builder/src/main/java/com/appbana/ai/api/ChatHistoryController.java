package com.appbana.ai.api;

import com.appbana.ai.rag.ConversationMemory;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * REST controller for chat history persistence.
 * <p>
 * GET /api/ai/chat/history?userId=X&sessionId=Y
 *   Returns all messages for a given session, ordered chronologically.
 *   Used by the frontend on startup to restore conversation continuity.
 *
 * GET /api/ai/chat/sessions?userId=X
 *   Returns a list of distinct sessions for the user (for future session-switcher UI).
 */
@Slf4j
public class ChatHistoryController {

    private final ConversationMemory conversationMemory;

    public ChatHistoryController(ConversationMemory conversationMemory) {
        this.conversationMemory = conversationMemory;
    }

    /**
     * GET /api/ai/chat/history?userId=X&sessionId=Y
     * Returns conversation history for the given session as a flat message list.
     */
    public BiConsumer<Router.HttpRequest, Router.HttpResponse> getHistory() {
        return (req, res) -> {
            try {
                String userId    = req.query("userId");
                String sessionId = req.query("sessionId");

                if (userId == null || userId.isBlank()) {
                    res.json(400, Map.of("error", "userId is required"));
                    return;
                }
                if (sessionId == null || sessionId.isBlank()) {
                    res.json(400, Map.of("error", "sessionId is required"));
                    return;
                }

                UUID sessionUuid;
                try {
                    sessionUuid = UUID.fromString(sessionId);
                } catch (IllegalArgumentException e) {
                    res.json(400, Map.of("error", "sessionId must be a valid UUID"));
                    return;
                }

                log.info("[ChatHistory] Loading history for user={} session={}", userId, sessionId);

                List<ConversationMemory.Conversation> history =
                        conversationMemory.getSessionHistory(sessionUuid);

                // Map to flat message list the frontend understands
                List<Map<String, Object>> messages = new ArrayList<>();
                for (ConversationMemory.Conversation conv : history) {
                    // User message
                    Map<String, Object> userMsg = new LinkedHashMap<>();
                    userMsg.put("role", "user");
                    userMsg.put("content", conv.getMessage());
                    userMsg.put("timestamp", conv.getCreatedAt() != null
                            ? conv.getCreatedAt().toEpochMilli() : null);
                    messages.add(userMsg);

                    // Assistant response
                    Map<String, Object> assistantMsg = new LinkedHashMap<>();
                    assistantMsg.put("role", "assistant");
                    assistantMsg.put("content", conv.getResponse());
                    assistantMsg.put("timestamp", conv.getCreatedAt() != null
                            ? conv.getCreatedAt().toEpochMilli() : null);
                    messages.add(assistantMsg);
                }

                log.info("[ChatHistory] Returning {} messages for session {}", messages.size(), sessionId);
                res.json(200, Map.of(
                        "sessionId", sessionId,
                        "userId", userId,
                        "messages", messages,
                        "count", messages.size()
                ));

            } catch (ConversationMemory.ConversationMemoryException e) {
                log.error("[ChatHistory] Failed to load history", e);
                res.json(500, Map.of("error", "Failed to load chat history: " + e.getMessage()));
            } catch (Exception e) {
                log.error("[ChatHistory] Unexpected error", e);
                res.json(500, Map.of("error", "Internal error: " + e.getMessage()));
            }
        };
    }

    /**
     * GET /api/ai/chat/sessions?userId=X&limit=20
     * Returns distinct recent sessions for the user (useful for a future session sidebar).
     */
    public BiConsumer<Router.HttpRequest, Router.HttpResponse> getSessions() {
        return (req, res) -> {
            try {
                String userId   = req.query("userId");
                String limitStr = req.query("limit");

                if (userId == null || userId.isBlank()) {
                    res.json(400, Map.of("error", "userId is required"));
                    return;
                }

                int limit = 20;
                if (limitStr != null) {
                    try { limit = Integer.parseInt(limitStr); } catch (NumberFormatException ignored) {}
                }

                log.info("[ChatHistory] Loading sessions for user={} limit={}", userId, limit);

                List<ConversationMemory.Conversation> recent =
                        conversationMemory.getRecentByUser(userId, limit * 2); // fetch more to de-dup

                // De-duplicate session IDs, preserve order (most recent first)
                LinkedHashSet<String> sessionIds = new LinkedHashSet<>();
                Map<String, Long> sessionTimestamps = new LinkedHashMap<>();

                for (ConversationMemory.Conversation conv : recent) {
                    if (conv.getSessionId() != null) {
                        String sid = conv.getSessionId().toString();
                        sessionIds.add(sid);
                        sessionTimestamps.put(sid,
                                conv.getCreatedAt() != null ? conv.getCreatedAt().toEpochMilli() : 0L);
                    }
                    if (sessionIds.size() >= limit) break;
                }

                List<Map<String, Object>> sessions = new ArrayList<>();
                for (String sid : sessionIds) {
                    Map<String, Object> s = new LinkedHashMap<>();
                    s.put("sessionId", sid);
                    s.put("lastActivity", sessionTimestamps.get(sid));
                    sessions.add(s);
                }

                res.json(200, Map.of(
                        "userId", userId,
                        "sessions", sessions,
                        "count", sessions.size()
                ));

            } catch (ConversationMemory.ConversationMemoryException e) {
                log.error("[ChatHistory] Failed to load sessions", e);
                res.json(500, Map.of("error", "Failed to load sessions: " + e.getMessage()));
            } catch (Exception e) {
                log.error("[ChatHistory] Unexpected error", e);
                res.json(500, Map.of("error", "Internal error: " + e.getMessage()));
            }
        };
    }
}
