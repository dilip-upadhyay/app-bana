package com.appbana.ai.api;

import com.appbana.ai.agent.AgentContext;
import com.appbana.ai.agent.AgentResponse;
import com.appbana.ai.agent.AiAgent;
import com.appbana.ai.agent.StreamEmitter;
import com.appbana.ai.api.dto.ChatRequest;
import com.appbana.ai.dialogue.DialogueManager;
import com.appbana.ai.rag.ConversationMemory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiConsumer;

/**
 * SSE streaming endpoint for the AI agent loop.
 *
 * Route: POST /api/ai/chat/agent/stream
 *
 * The response is a text/event-stream with events:
 *   token           – LLM text chunks (streamed as they arrive)
 *   tool_call_start – { id, name, args }
 *   tool_call_end   – { id, status: "ok"|"error", result }
 *   state           – { conversationState }
 *   done            – { conversationId, finalMessage }
 *
 * The existing sync endpoint (POST /api/ai/chat/agent) is kept intact.
 */
@Slf4j
public class AgentStreamController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AiAgent agent;
    private final ConversationMemory conversationMemory;
    private final DialogueManager dialogueManager;

    public AgentStreamController(AiAgent agent,
                                 ConversationMemory conversationMemory,
                                 DialogueManager dialogueManager) {
        this.agent = agent;
        this.conversationMemory = conversationMemory;
        this.dialogueManager = dialogueManager;
    }

    /**
     * Returns a handler that:
     *  1. Parses the same ChatRequest body as the sync endpoint
     *  2. Opens an SSE stream
     *  3. Runs the agent loop via AiAgent#processWithStream, emitting events in real time
     */
    public BiConsumer<Router.HttpRequest, Router.HttpResponse> stream() {
        return (req, res) -> {
            // We need the raw HttpExchange to write SSE manually
            HttpExchange exchange = req.exchange();
            if (exchange == null) {
                res.json(501, Map.of("error", "SSE streaming requires the built-in httpserver, not servlet mode"));
                return;
            }

            ChatRequest request;
            try {
                request = req.readJson(new TypeReference<>() {});
            } catch (Exception e) {
                res.json(400, Map.of("error", "Invalid request body: " + e.getMessage()));
                return;
            }

            String tenantId  = request.getTenantId()  != null ? request.getTenantId()  : "default";
            String appId     = request.getAppId()     != null ? request.getAppId()     : "default";
            String userId    = request.getUserId()    != null ? request.getUserId()    : "anonymous";
            String sessionId = request.getSessionId() != null ? request.getSessionId() : UUID.randomUUID().toString();
            String token     = request.getToken();

            List<ConversationMemory.Conversation> history;
            try {
                history = conversationMemory != null
                        ? conversationMemory.getSessionHistory(UUID.fromString(sessionId))
                        : new ArrayList<>();
            } catch (Exception e) {
                log.warn("[STREAM] Could not load history for session {}: {}", sessionId, e.getMessage());
                history = new ArrayList<>();
            }

            DialogueManager.ConversationState conversationState =
                    dialogueManager.resolveState(sessionId, history, request.getMessage());

            AgentContext agentContext = AgentContext
                    .create(tenantId, appId, userId, sessionId, token)
                    .withVariable("chat_history", history)
                    .withVariable("conversation_state", conversationState.name())
                    .withVariable("app_name",
                            request.getAppName() != null ? request.getAppName() : "");

            // Open SSE stream — must be done before writing any body
            try {
                exchange.getResponseHeaders().set("Content-Type",  "text/event-stream");
                exchange.getResponseHeaders().set("Cache-Control", "no-cache");
                exchange.getResponseHeaders().set("Connection",    "keep-alive");
                exchange.getResponseHeaders().set("X-Accel-Buffering", "no"); // Nginx: disable buffering
                // Use -1 (unknown length) so the server sends chunks without a Content-Length header
                exchange.sendResponseHeaders(200, 0);
            } catch (IOException ioe) {
                log.error("[STREAM] Failed to open SSE response", ioe);
                return;
            }

            StreamEmitter emitter;
            try (OutputStream out = exchange.getResponseBody()) {
                emitter = buildEmitter(out, sessionId);
                AgentResponse result = null;
                try {
                    result = agent.processWithStream(
                            request.getMessage(), agentContext,
                            request.getProvider(), request.getImages(),
                            emitter);

                    // Store conversation history (non-fatal if it fails)
                    if (result.isSuccess() && conversationMemory != null) {
                        try {
                            ConversationMemory.Conversation conv = new ConversationMemory.Conversation();
                            conv.setUserId(userId);
                            conv.setSessionId(UUID.fromString(sessionId));
                            conv.setAppId(appId);
                            conv.setMessage(request.getMessage());
                            conv.setResponse(result.getFinalAnswer());
                            conv.setIntent("agent_stream");
                            conversationMemory.store(conv);
                        } catch (Exception storeEx) {
                            log.warn("[STREAM] Failed to store conversation history: {}", storeEx.getMessage());
                        }
                    }

                    // Advance dialogue state (non-fatal)
                    if (result.isSuccess()) {
                        try {
                            String lower = result.getFinalAnswer() != null
                                    ? result.getFinalAnswer().toLowerCase() : "";
                            if (lower.contains("scaffold") || lower.contains("app created")
                                    || lower.contains("successfully created")) {
                                dialogueManager.notifyScaffolding(sessionId);
                            }
                        } catch (Exception dlgEx) {
                            log.warn("[STREAM] Dialogue state update failed: {}", dlgEx.getMessage());
                        }
                    }

                } catch (Exception agentEx) {
                    log.error("[STREAM] Agent execution failed", agentEx);
                } finally {
                    // Always send done — client EventSource must not hang waiting
                    emitter.complete();
                }

            } catch (Exception e) {
                log.error("[STREAM] Fatal streaming error (SSE stream may already be open)", e);
            }
        };
    }

    // -------------------------------------------------------------------------
    // StreamEmitter backed by a raw HttpExchange OutputStream
    // -------------------------------------------------------------------------

    private StreamEmitter buildEmitter(OutputStream out, String sessionId) {
        final Object writeLock = new Object();
        return new StreamEmitter() {
            private volatile boolean doneEmitted = false;

            @Override
            public void emit(String eventName, Object payload) {
                try {
                    String json = MAPPER.writeValueAsString(payload);
                    String sseFrame = "event: " + eventName + "\ndata: " + json + "\n\n";
                    byte[] bytes = sseFrame.getBytes(StandardCharsets.UTF_8);
                    synchronized (writeLock) {
                        out.write(bytes);
                        out.flush();
                    }
                    // Track terminal event so complete() doesn't fire a duplicate
                    if ("done".equals(eventName)) {
                        doneEmitted = true;
                    }
                } catch (IOException e) {
                    // Client disconnected — log quietly, don't throw
                    log.debug("[STREAM] Client disconnected while emitting '{}': {}", eventName, e.getMessage());
                } catch (Exception e) {
                    log.warn("[STREAM] Failed to emit '{}': {}", eventName, e.getMessage());
                }
            }

            @Override
            public void complete() {
                if (doneEmitted) return;
                // Fallback done — only emitted when the agent loop never sent one
                // (e.g. exception path). Client EventSource must not hang open.
                emit("done", Map.of("conversationId", sessionId, "finalMessage", ""));
            }
        };
    }
}
