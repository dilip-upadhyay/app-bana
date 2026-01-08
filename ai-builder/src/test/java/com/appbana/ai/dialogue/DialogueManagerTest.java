package com.appbana.ai.dialogue;

import org.junit.jupiter.api.*;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DialogueManager
 */
class DialogueManagerTest {

    private DialogueManager dialogueManager;

    @BeforeEach
    void setUp() {
        dialogueManager = new DialogueManager();
    }

    @Test
    @DisplayName("Should initialize with GREETING state")
    void testInitialization() {
        assertEquals(DialogueManager.ConversationState.GREETING, dialogueManager.getCurrentState());
    }

    @Test
    @DisplayName("Should handle GREETING state")
    void testHandleGreeting() {
        // When
        String response = dialogueManager.handle("Hello", DialogueManager.ConversationState.GREETING);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("help"));
        assertEquals(DialogueManager.ConversationState.GREETING, dialogueManager.getCurrentState());
    }

    @Test
    @DisplayName("Should handle GATHERING_REQUIREMENTS state")
    void testHandleGathering() {
        // When
        String response = dialogueManager.handle("I want to build a CRM",
                DialogueManager.ConversationState.GATHERING_REQUIREMENTS);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("clarify"));

        Map<String, Object> context = dialogueManager.getContext();
        assertTrue(context.containsKey("requirements"));
    }

    @Test
    @DisplayName("Should handle CLARIFYING state")
    void testHandleClarifying() {
        // When
        String response = dialogueManager.handle("Yes, with contacts and deals",
                DialogueManager.ConversationState.CLARIFYING);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("confirm"));
    }

    @Test
    @DisplayName("Should handle CONFIRMING state")
    void testHandleConfirming() {
        // When
        String response = dialogueManager.handle("Yes, that's correct",
                DialogueManager.ConversationState.CONFIRMING);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("generating"));
    }

    @Test
    @DisplayName("Should handle GENERATING state")
    void testHandleGenerating() {
        // When
        String response = dialogueManager.handle("How long will it take?",
                DialogueManager.ConversationState.GENERATING);

        // Then
        assertNotNull(response);
        assertTrue(response.toLowerCase().contains("generat") || response.toLowerCase().contains("application"),
                "Response should mention generation or application, got: " + response);
    }

    @Test
    @DisplayName("Should handle COMPLETED state")
    void testHandleCompleted() {
        // When
        String response = dialogueManager.handle("Looks good!",
                DialogueManager.ConversationState.COMPLETED);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("ready"));
    }

    @Test
    @DisplayName("Should maintain context across states")
    void testContextPersistence() {
        // Given
        dialogueManager.handle("I want a CRM", DialogueManager.ConversationState.GATHERING_REQUIREMENTS);

        // When
        Map<String, Object> context = dialogueManager.getContext();

        // Then
        assertNotNull(context);
        assertTrue(context.containsKey("requirements"));
    }

    @Test
    @DisplayName("Should return immutable context copy")
    void testGetContext_Immutable() {
        // Given
        dialogueManager.handle("Test", DialogueManager.ConversationState.GREETING);
        Map<String, Object> context1 = dialogueManager.getContext();

        // When
        context1.put("test", "value");
        Map<String, Object> context2 = dialogueManager.getContext();

        // Then
        assertFalse(context2.containsKey("test"), "Context should be immutable");
    }
}
