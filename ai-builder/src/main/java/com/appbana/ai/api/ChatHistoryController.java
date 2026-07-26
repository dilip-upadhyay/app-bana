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
     * GET /api/ai/chat/sessions?userId=X&appId=Y&limit=20
     * Returns one summary per session (title, appId, lastActivity, turnCount).
     * Deleted sessions are excluded.
     */
    public BiConsumer<Router.HttpRequest, Router.HttpResponse> getSessions() {
        return (req, res) -> {
            try {
                String userId   = req.query("userId");
                String appId    = req.query("appId");
                String limitStr = req.query("limit");

                if (userId == null || userId.isBlank()) {
                    res.json(400, Map.of("error", "userId is required"));
                    return;
                }

                int limit = 20;
                if (limitStr != null) {
                    try { limit = Integer.parseInt(limitStr); } catch (NumberFormatException ignored) { /* keep default */ }
                }

                log.info("[ChatHistory] Loading sessions user={} appId={} limit={}", userId, appId, limit);

                List<ConversationMemory.SessionSummary> summaries =
                        conversationMemory.listSessionSummaries(userId, appId, limit);

                List<Map<String, Object>> sessions = new ArrayList<>();
                for (ConversationMemory.SessionSummary s : summaries) {
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("sessionId",    s.getSessionId());
                    out.put("title",        s.getTitle());
                    out.put("appId",        s.getAppId());
                    out.put("lastActivity", s.getLastActivity());
                    out.put("turnCount",    s.getTurnCount());
                    sessions.add(out);
                }

                res.json(200, Map.of(
                        "userId",   userId,
                        "sessions", sessions,
                        "count",    sessions.size()
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

    /**
     * PUT /api/ai/chat/sessions/{sessionId}
     * Body: { "userId": "...", "title": "New title" }
     */
    public BiConsumer<Router.HttpRequest, Router.HttpResponse> renameSession() {
        return (req, res) -> {
            try {
                String sessionId = req.pathParam("sessionId");
                Map<String, Object> body = req.readJson(new TypeReference<Map<String, Object>>() {});
                String userId = body != null ? (String) body.get("userId") : null;
                String title  = body != null ? (String) body.get("title")  : null;

                if (userId == null || userId.isBlank() || title == null || title.isBlank()) {
                    res.json(400, Map.of("error", "userId and title are required"));
                    return;
                }
                UUID sid;
                try { sid = UUID.fromString(sessionId); }
                catch (IllegalArgumentException e) {
                    res.json(400, Map.of("error", "sessionId must be a valid UUID"));
                    return;
                }

                conversationMemory.renameSession(userId, sid, title.trim());
                res.json(200, Map.of("sessionId", sessionId, "title", title.trim()));

            } catch (ConversationMemory.ConversationMemoryException e) {
                log.error("[ChatHistory] Rename failed", e);
                res.json(500, Map.of("error", "Rename failed: " + e.getMessage()));
            } catch (Exception e) {
                log.error("[ChatHistory] Unexpected error on rename", e);
                res.json(500, Map.of("error", "Internal error: " + e.getMessage()));
            }
        };
    }

    /**
     * DELETE /api/ai/chat/sessions/{sessionId}?userId=X
     * Soft-deletes: session is hidden from the picker, history rows are retained.
     */
    public BiConsumer<Router.HttpRequest, Router.HttpResponse> deleteSession() {
        return (req, res) -> {
            try {
                String sessionId = req.pathParam("sessionId");
                String userId    = req.query("userId");

                if (userId == null || userId.isBlank()) {
                    res.json(400, Map.of("error", "userId is required"));
                    return;
                }
                UUID sid;
                try { sid = UUID.fromString(sessionId); }
                catch (IllegalArgumentException e) {
                    res.json(400, Map.of("error", "sessionId must be a valid UUID"));
                    return;
                }

                conversationMemory.deleteSession(userId, sid);
                res.json(200, Map.of("sessionId", sessionId, "deleted", true));

            } catch (ConversationMemory.ConversationMemoryException e) {
                log.error("[ChatHistory] Delete failed", e);
                res.json(500, Map.of("error", "Delete failed: " + e.getMessage()));
            } catch (Exception e) {
                log.error("[ChatHistory] Unexpected error on delete", e);
                res.json(500, Map.of("error", "Internal error: " + e.getMessage()));
            }
        };
    }
}
