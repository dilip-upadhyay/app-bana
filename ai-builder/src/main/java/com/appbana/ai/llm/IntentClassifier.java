package com.appbana.ai.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import java.util.*;

/**
 * Intent classifier using LLM
 * Story: 4.2 - Implement Intent Classification
 */
@Slf4j
public class IntentClassifier {

    private final OpenAiLlmService llmService;
    private final ObjectMapper objectMapper;

    public IntentClassifier(OpenAiLlmService llmService) {
        this.llmService = llmService;
        this.objectMapper = new ObjectMapper();
        log.info("Intent Classifier initialized");
    }

    public IntentResult classifyIntent(String userMessage) throws Exception {
        String prompt = buildClassificationPrompt(userMessage);
        String response = llmService.chat(prompt);

        // Parse JSON response
        try {
            Map<String, Object> result = objectMapper.readValue(response, Map.class);

            IntentResult intentResult = new IntentResult();
            intentResult.setIntent((String) result.get("intent"));
            intentResult.setConfidence(((Number) result.get("confidence")).doubleValue());
            intentResult.setEntities((Map<String, Object>) result.get("entities"));

            log.debug("Classified intent: {} (confidence: {})",
                    intentResult.getIntent(), intentResult.getConfidence());

            return intentResult;

        } catch (Exception e) {
            log.warn("Failed to parse intent classification response, using fallback", e);
            return createFallbackIntent(userMessage);
        }
    }

    private String buildClassificationPrompt(String userMessage) {
        return String.format(
                """
                        You are an AppBana AI assistant. AppBana is a metadata-driven low-code platform for building business applications.

                        APPBANA CAPABILITIES:
                        - Entities with 39 field types (text, number, email, phone, date, select, etc.)
                        - Pages: List (tables), Form (create/edit), Detail (view)
                        - Components: table, form, input, button, grid, container, text
                        - Workflows and state machines
                        - Multi-tenancy and role-based access
                        - No custom code - everything is metadata-driven

                        YOUR ROLE:
                        - Help users build applications using ONLY AppBana features
                        - Guide them to use AppBana's metadata model
                        - Don't suggest external tools, custom code, or non-AppBana solutions

                        Classify the following user message into one of these intents:
                        - create_app: User wants to create a new application or entity
                        - modify_app: User wants to modify an existing application
                        - ask_question: User is asking about AppBana capabilities
                        - provide_feedback: User is providing feedback
                        - greeting: User is greeting
                        - other: None of the above

                        Respond ONLY with valid JSON in this format:
                        {
                          "intent": "create_app",
                          "confidence": 0.95,
                          "entities": {}
                        }

                        User message: "%s"
                        """,
                userMessage);
    }

    private IntentResult createFallbackIntent(String message) {
        IntentResult result = new IntentResult();
        result.setIntent("other");
        result.setConfidence(0.5);
        result.setEntities(new HashMap<>());
        return result;
    }

    @Data
    public static class IntentResult {
        private String intent;
        private double confidence;
        private Map<String, Object> entities;
    }
}
