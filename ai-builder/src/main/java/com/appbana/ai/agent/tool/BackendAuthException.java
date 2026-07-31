package com.appbana.ai.agent.tool;

/**
 * Thrown by a tool's backend HTTP call when the response is {@code 401}.
 *
 * <p>C4.4e's door guard (see {@code AgentContext}'s compact canonical constructor) guarantees a
 * token is <em>present</em> on every {@link com.appbana.ai.agent.AgentContext} — it says nothing
 * about whether that token is still <em>valid</em>. AppBana sessions expire after 30 minutes of
 * inactivity (see {@code ComprehensiveKnowledgeLoader}), and a token can also be revoked or
 * outlived by a backend restart mid-conversation. Any of those turns a request that was fine when
 * the chat started into a 401 partway through the agent loop.
 *
 * <p>Without this, that 401 looked exactly like any other backend failure: the tool returned a
 * generic {@link ToolResult#error}, the agent retried up to 3 times (three paid LLM round-trips),
 * and the user was told to "rephrase or try again" — the same as if the backend were simply down.
 * The chat's own SSE transport had already returned {@code 200} by the time this happens, so the
 * frontend's {@code appbana:auth:expired} recovery (triggered by {@code authedFetch} on a 401
 * transport response) can never fire; nothing else tells the Studio the session ended.
 *
 * <p>Throwing this from the point where the 401 is detected — rather than threading a status code
 * through every tool's own ad hoc error-message format — lets each tool's top-level
 * {@code execute()} catch it once, before the generic {@code catch (Exception e)}, and return
 * {@link ToolResult#authError} so {@code AiAgent} can recognise the failure by type and abort the
 * loop on iteration 1 with a distinct message, instead of retrying and burning
 * {@code consecutiveFailures} toward the abort-at-3.
 */
public class BackendAuthException extends RuntimeException {

    public BackendAuthException(String message) {
        super(message);
    }
}
