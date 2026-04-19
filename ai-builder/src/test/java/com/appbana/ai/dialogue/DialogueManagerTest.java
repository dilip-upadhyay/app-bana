package com.appbana.ai.dialogue;

import com.appbana.ai.rag.ConversationMemory;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DialogueManager — Story 3.1
 *
 * Covers per-session state isolation, automatic ConversationSpec-driven
 * transitions, explicit notify*() lifecycle hooks, and tool-set gating.
 */
class DialogueManagerTest {

    private DialogueManager dialogueManager;

    @BeforeEach
    void setUp() {
        dialogueManager = new DialogueManager();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Initial state
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("New session should start in GREETING state")
    void testInitialStateIsGreeting() {
        String sessionId = newSession();

        DialogueManager.ConversationState state = dialogueManager.getCurrentState(sessionId);

        assertEquals(DialogueManager.ConversationState.GREETING, state);
    }

    @Test
    @DisplayName("resolveState on empty message should stay GREETING")
    void testGreetingWithNoSignalsStaysGreeting() {
        String sessionId = newSession();

        DialogueManager.ConversationState state = dialogueManager.resolveState(sessionId, empty(), "Hello!");

        assertEquals(DialogueManager.ConversationState.GREETING, state);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GREETING → GATHERING_REQUIREMENTS
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GREETING → GATHERING_REQUIREMENTS when entities are mentioned")
    void testTransitionToGatheringWhenEntitiesMentioned() {
        String sessionId = newSession();

        DialogueManager.ConversationState state = dialogueManager.resolveState(
                sessionId, empty(), "I want to build a CRM to manage customers and orders");

        assertEquals(DialogueManager.ConversationState.GATHERING_REQUIREMENTS, state);
    }

    @Test
    @DisplayName("GREETING → GATHERING_REQUIREMENTS on entity-related history")
    void testTransitionToGatheringFromHistory() {
        String sessionId = newSession();
        List<ConversationMemory.Conversation> history = List.of(turn("I need to track employees"));

        DialogueManager.ConversationState state = dialogueManager.resolveState(
                sessionId, history, "What fields should I include?");

        assertEquals(DialogueManager.ConversationState.GATHERING_REQUIREMENTS, state);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GATHERING_REQUIREMENTS → CONFIRMING
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GATHERING_REQUIREMENTS → CONFIRMING when user says 'yes'")
    void testTransitionToConfirmingOnYes() {
        String sessionId = newSession();
        List<ConversationMemory.Conversation> history = List.of(turn("I want to manage products and invoices"));
        // First call to land in GATHERING_REQUIREMENTS
        dialogueManager.resolveState(sessionId, history, "I want to manage products and invoices");

        // Now user confirms
        DialogueManager.ConversationState state = dialogueManager.resolveState(
                sessionId, history, "yes, sounds good, build it");

        assertEquals(DialogueManager.ConversationState.CONFIRMING, state);
    }

    @Test
    @DisplayName("GATHERING_REQUIREMENTS → CONFIRMING on 'go ahead'")
    void testTransitionToConfirmingOnGoAhead() {
        String sessionId = newSession();
        List<ConversationMemory.Conversation> history = List.of(turn("Track orders and customers"));
        dialogueManager.resolveState(sessionId, history, "Track orders and customers");

        DialogueManager.ConversationState state = dialogueManager.resolveState(
                sessionId, history, "go ahead");

        assertEquals(DialogueManager.ConversationState.CONFIRMING, state);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Explicit notify*() transitions
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("notifyScaffolding() forces state to GENERATING")
    void testNotifyScaffoldingForcesGenerating() {
        String sessionId = newSession();
        dialogueManager.resolveState(sessionId, empty(), "I want a product catalog");
        dialogueManager.resolveState(sessionId, empty(), "yes build it");

        dialogueManager.notifyScaffolding(sessionId);

        assertEquals(DialogueManager.ConversationState.GENERATING, dialogueManager.getCurrentState(sessionId));
    }

    @Test
    @DisplayName("notifyCompleted() forces state to COMPLETED")
    void testNotifyCompletedForcesCompleted() {
        String sessionId = newSession();
        dialogueManager.notifyScaffolding(sessionId);

        dialogueManager.notifyCompleted(sessionId);

        assertEquals(DialogueManager.ConversationState.COMPLETED, dialogueManager.getCurrentState(sessionId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GENERATING / COMPLETED are locked (no auto-regression)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GENERATING state does not regress via resolveState")
    void testGeneratingStateIsLocked() {
        String sessionId = newSession();
        dialogueManager.notifyScaffolding(sessionId);

        // Calling resolveState should not change state
        dialogueManager.resolveState(sessionId, empty(), "How long until it's done?");

        assertEquals(DialogueManager.ConversationState.GENERATING, dialogueManager.getCurrentState(sessionId));
    }

    @Test
    @DisplayName("COMPLETED state does not regress via resolveState")
    void testCompletedStateIsLocked() {
        String sessionId = newSession();
        dialogueManager.notifyCompleted(sessionId);

        dialogueManager.resolveState(sessionId, empty(), "I want to build another app");

        assertEquals(DialogueManager.ConversationState.COMPLETED, dialogueManager.getCurrentState(sessionId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tool set gating
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GREETING exposes only minimal tools")
    void testGreetingToolSet() {
        Set<String> allowed = dialogueManager.getAllowedTools(DialogueManager.ConversationState.GREETING);

        assertFalse(allowed.contains("scaffold_app"), "scaffold_app must be hidden in GREETING");
        assertFalse(allowed.contains("create_app"),   "create_app must be hidden in GREETING");
        assertFalse(allowed.contains("deploy_app"),   "deploy_app must be hidden in GREETING");
        assertTrue(allowed.contains("list_apps"),     "list_apps should be available in GREETING");
    }

    @Test
    @DisplayName("GATHERING_REQUIREMENTS exposes read-only tools only")
    void testGatheringToolSet() {
        Set<String> allowed = dialogueManager.getAllowedTools(DialogueManager.ConversationState.GATHERING_REQUIREMENTS);

        assertFalse(allowed.contains("scaffold_app"),    "scaffold_app must be hidden in GATHERING_REQUIREMENTS");
        assertTrue(allowed.contains("list_entities"),    "list_entities should be visible");
        assertTrue(allowed.contains("get_entity_details"), "get_entity_details should be visible");
    }

    @Test
    @DisplayName("CONFIRMING unlocks all build tools")
    void testConfirmingToolSet() {
        Set<String> allowed = dialogueManager.getAllowedTools(DialogueManager.ConversationState.CONFIRMING);

        assertTrue(allowed.contains("scaffold_app"),         "scaffold_app must be unlocked in CONFIRMING");
        assertTrue(allowed.contains("create_app"),           "create_app must be unlocked in CONFIRMING");
        assertTrue(allowed.contains("deploy_app"),           "deploy_app must be unlocked in CONFIRMING");
        assertTrue(allowed.contains("generate_mock_data"),   "generate_mock_data must be unlocked in CONFIRMING");
    }

    @Test
    @DisplayName("GENERATING and COMPLETED also expose all build tools")
    void testGeneratingAndCompletedToolSet() {
        for (DialogueManager.ConversationState state : List.of(
                DialogueManager.ConversationState.GENERATING,
                DialogueManager.ConversationState.COMPLETED)) {
            Set<String> allowed = dialogueManager.getAllowedTools(state);
            assertTrue(allowed.contains("scaffold_app"), state + " should allow scaffold_app");
            assertTrue(allowed.contains("deploy_app"),   state + " should allow deploy_app");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Session isolation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Different sessions are completely isolated")
    void testSessionIsolation() {
        String sessionA = newSession();
        String sessionB = newSession();

        // Session A advances to CONFIRMING
        dialogueManager.resolveState(sessionA, empty(), "I want to manage inventory");
        dialogueManager.resolveState(sessionA, empty(), "yes, build it");

        // Session B stays in GREETING
        DialogueManager.ConversationState stateB = dialogueManager.getCurrentState(sessionB);

        assertEquals(DialogueManager.ConversationState.GREETING, stateB,
                "Session B state should not be affected by Session A");
    }

    @Test
    @DisplayName("resolveAllowedTools convenience method returns correct set")
    void testResolveAllowedTools() {
        String sessionId = newSession();
        List<ConversationMemory.Conversation> history = List.of(turn("I need to track employees and payroll"));

        Set<String> tools = dialogueManager.resolveAllowedTools(sessionId, history, "employees");

        // Should be GATHERING_REQUIREMENTS → no build tools
        assertFalse(tools.contains("scaffold_app"));
        assertTrue(tools.contains("list_entities"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static String newSession() {
        return UUID.randomUUID().toString();
    }

    private static List<ConversationMemory.Conversation> empty() {
        return Collections.emptyList();
    }

    private static ConversationMemory.Conversation turn(String message) {
        ConversationMemory.Conversation conv = new ConversationMemory.Conversation();
        conv.setMessage(message);
        conv.setResponse("Understood. Please tell me more.");
        return conv;
    }
}
