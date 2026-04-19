package com.appbana.ai.dialogue;

import com.appbana.ai.rag.ConversationMemory;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DialogueManager — Story 3.1: Implement Dialogue Manager
 *
 * A per-session state machine that autonomously classifies conversation phases
 * so that the {@code AiAgent} does not rely solely on system-prompting to
 * remember whether it is gathering requirements or building an app.
 *
 * <h3>State Transitions</h3>
 * <pre>
 *   GREETING
 *     │  (entities detected in conversation)
 *     ▼
 *   GATHERING_REQUIREMENTS
 *     │  (user says "yes", "build it", "proceed", etc.)
 *     ▼
 *   CONFIRMING
 *     │  (controller calls notifyScaffolding after scaffold_app succeeds)
 *     ▼
 *   GENERATING
 *     │  (controller calls notifyCompleted after full agent response succeeds)
 *     ▼
 *   COMPLETED
 * </pre>
 *
 * The only forward-only rule: once a session reaches CONFIRMING or beyond,
 * confirmation keywords in subsequent messages do NOT reset it back.
 *
 * <h3>Thread Safety</h3>
 * Uses a {@link ConcurrentHashMap} so multiple concurrent requests for
 * different sessions are safe without global locking.
 */
@Slf4j
public class DialogueManager {

    // ── State Definition ───────────────────────────────────────────────────────

    public enum ConversationState {
        /** New session — no requirements captured yet. */
        GREETING,

        /** At least one entity/domain topic has been mentioned. */
        GATHERING_REQUIREMENTS,

        /** User has explicitly confirmed the proposed plan. */
        CONFIRMING,

        /** scaffold_app (or equivalent) has been called successfully. */
        GENERATING,

        /** App generation completed. User may request modifications. */
        COMPLETED
    }

    // ── Tool Buckets per State ─────────────────────────────────────────────────

    /**
     * Read-only tools — safe to expose in any state.
     */
    public static final Set<String> READ_ONLY_TOOLS = Set.of(
            "search_knowledge",
            "list_apps",
            "list_entities",
            "get_entity_details",
            "list_pages",
            "list_workflows"
    );

    /**
     * Write/build tools — only unlocked once the user has confirmed (CONFIRMING+).
     */
    public static final Set<String> BUILD_TOOLS = Set.of(
            "scaffold_app",
            "create_app",
            "create_entity",
            "generate_page",
            "deploy_app",
            "generate_mock_data",
            "batch_update_entities"
    );

    // ── Per-session state storage ──────────────────────────────────────────────

    private final ConcurrentHashMap<String, ConversationState> sessionStates = new ConcurrentHashMap<>();

    public DialogueManager() {
        log.info("[DialogueManager] Initialized — per-session state machine ready");
    }

    // ── Core API ───────────────────────────────────────────────────────────────

    /**
     * Derive the current {@link ConversationState} for a session, automatically
     * advancing it based on the conversation history and the latest user message.
     *
     * <p>This method is idempotent: calling it multiple times with the same inputs
     * produces the same state (it only advances, never regresses mid-conversation).
     *
     * @param sessionId   unique session identifier
     * @param history     conversation turns so far (may be empty)
     * @param userMessage current message from the user
     * @return the resolved state for this session
     */
    public ConversationState resolveState(String sessionId,
                                          List<ConversationMemory.Conversation> history,
                                          String userMessage) {
        ConversationState current = sessionStates.getOrDefault(sessionId, ConversationState.GREETING);

        // States GENERATING and COMPLETED are only set via explicit notify*() calls.
        // We don't auto-transition into them from text analysis.
        if (current == ConversationState.GENERATING || current == ConversationState.COMPLETED) {
            log.debug("[DialogueManager] session={} state={} (locked — manual transition only)", sessionId, current);
            return current;
        }

        // Run keyword analysis on the full conversation corpus
        ConversationSpec spec = ConversationSpec.analyse(history, userMessage);

        ConversationState next = computeNextState(current, spec);

        if (next != current) {
            sessionStates.put(sessionId, next);
            log.info("[DialogueManager] session={} transition: {} → {}", sessionId, current, next);
        } else {
            log.debug("[DialogueManager] session={} state={} (unchanged)", sessionId, current);
        }

        return next;
    }

    /**
     * Force state to {@code GENERATING} after a successful scaffold/build tool call.
     * Called by the controller, not by text analysis.
     */
    public void notifyScaffolding(String sessionId) {
        ConversationState prev = sessionStates.put(sessionId, ConversationState.GENERATING);
        log.info("[DialogueManager] session={} notifyScaffolding: {} → GENERATING", sessionId, prev);
    }

    /**
     * Force state to {@code COMPLETED} after the agent reports full success.
     * Called by the controller, not by text analysis.
     */
    public void notifyCompleted(String sessionId) {
        ConversationState prev = sessionStates.put(sessionId, ConversationState.COMPLETED);
        log.info("[DialogueManager] session={} notifyCompleted: {} → COMPLETED", sessionId, prev);
    }

    /**
     * Return the current persisted state for a session without running analysis.
     * Returns {@code GREETING} for unknown sessions.
     */
    public ConversationState getCurrentState(String sessionId) {
        return sessionStates.getOrDefault(sessionId, ConversationState.GREETING);
    }

    /**
     * Returns the set of tool names that the LLM is allowed to call in a given state.
     * Used by {@code AiAgent.buildAgentPrompt()} to produce a filtered tool list.
     */
    public Set<String> getAllowedTools(ConversationState state) {
        return switch (state) {
            case GREETING -> Set.of("search_knowledge", "list_apps");
            case GATHERING_REQUIREMENTS -> READ_ONLY_TOOLS;
            case CONFIRMING, GENERATING, COMPLETED -> {
                Set<String> all = new HashSet<>(READ_ONLY_TOOLS);
                all.addAll(BUILD_TOOLS);
                yield Collections.unmodifiableSet(all);
            }
        };
    }

    /**
     * Convenience: resolve state and immediately return the allowed tool set.
     */
    public Set<String> resolveAllowedTools(String sessionId,
                                            List<ConversationMemory.Conversation> history,
                                            String userMessage) {
        ConversationState state = resolveState(sessionId, history, userMessage);
        return getAllowedTools(state);
    }

    // ── Internal Transition Logic ──────────────────────────────────────────────

    private ConversationState computeNextState(ConversationState current, ConversationSpec spec) {
        return switch (current) {
            case GREETING -> {
                // Advance if user has started describing their domain
                if (spec.isEntitiesDiscussed()) {
                    yield ConversationState.GATHERING_REQUIREMENTS;
                }
                yield ConversationState.GREETING;
            }

            case GATHERING_REQUIREMENTS -> {
                // Advance when the user explicitly confirms the plan
                if (spec.isUserConfirmed()) {
                    yield ConversationState.CONFIRMING;
                }
                yield ConversationState.GATHERING_REQUIREMENTS;
            }

            // CONFIRMING, GENERATING, COMPLETED: never auto-regress
            default -> current;
        };
    }
}
