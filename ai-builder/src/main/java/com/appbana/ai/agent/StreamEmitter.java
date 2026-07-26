package com.appbana.ai.agent;

import java.util.Map;

/**
 * Callback interface for streaming agent events to the client via SSE.
 *
 * Implementations write to an open HTTP response stream; each method
 * is called synchronously from the agent loop (virtual-thread safe).
 *
 * Event contract (matches the frontend EventSource listener):
 *   event: token            → { text: "..." }
 *   event: tool_call_start  → { id, name, args }
 *   event: tool_call_end    → { id, status: "ok"|"error", result }
 *   event: state            → { conversationState: "GATHERING_REQUIREMENTS" }
 *   event: done             → { conversationId, finalMessage }
 */
public interface StreamEmitter {

    /** Emit a named SSE event with an arbitrary JSON-serialisable payload. */
    void emit(String eventName, Object payload);

    /** Signal that the stream is complete — implementations should flush and close. */
    void complete();

    // ---- Typed helpers ------------------------------------------------

    default void token(String text) {
        emit("token", Map.of("text", text));
    }

    default void toolCallStart(String id, String name, Object args) {
        emit("tool_call_start", Map.of("id", id, "name", name, "args", args));
    }

    default void toolCallEnd(String id, String status, Object result) {
        emit("tool_call_end", Map.of("id", id, "status", status, "result", result == null ? "" : result));
    }

    default void state(String conversationState) {
        emit("state", Map.of("conversationState", conversationState));
    }

    default void done(String conversationId, String finalMessage) {
        emit("done", Map.of(
            "conversationId", conversationId != null ? conversationId : "",
            "finalMessage",   finalMessage   != null ? finalMessage   : ""
        ));
    }

    /** No-op emitter — used when no streaming is needed (e.g., sync endpoint). */
    StreamEmitter NOOP = new StreamEmitter() {
        @Override public void emit(String eventName, Object payload) {}
        @Override public void complete() {}
    };
}
