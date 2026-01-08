package com.appbana.ai.dialogue;

import lombok.extern.slf4j.Slf4j;
import java.util.*;

/**
 * Dialogue manager for conversation state management
 * Story: 3.1 - Implement Dialogue Manager
 */
@Slf4j
public class DialogueManager {

    public enum ConversationState {
        GREETING, GATHERING_REQUIREMENTS, CLARIFYING, CONFIRMING,
        GENERATING, COMPLETED
    }

    private ConversationState currentState = ConversationState.GREETING;
    private final Map<String, Object> context = new HashMap<>();

    public DialogueManager() {
        log.info("Dialogue Manager initialized");
    }

    public String handle(String userMessage, ConversationState state) {
        this.currentState = state;

        return switch (state) {
            case GREETING -> handleGreeting(userMessage);
            case GATHERING_REQUIREMENTS -> handleGathering(userMessage);
            case CLARIFYING -> handleClarifying(userMessage);
            case CONFIRMING -> handleConfirming(userMessage);
            case GENERATING -> handleGenerating(userMessage);
            case COMPLETED -> handleCompleted(userMessage);
        };
    }

    private String handleGreeting(String message) {
        context.put("started", true);
        return "Hello! I'll help you build your application. What would you like to create?";
    }

    private String handleGathering(String message) {
        context.put("requirements", message);
        return "I understand. Let me clarify a few details...";
    }

    private String handleClarifying(String message) {
        return "Thank you for clarifying. Let me confirm what I understood...";
    }

    private String handleConfirming(String message) {
        return "Great! I'll start generating your application now.";
    }

    private String handleGenerating(String message) {
        return "Your application is being generated...";
    }

    private String handleCompleted(String message) {
        return "Your application is ready! Would you like to make any changes?";
    }

    public ConversationState getCurrentState() {
        return currentState;
    }

    public Map<String, Object> getContext() {
        return new HashMap<>(context);
    }
}
