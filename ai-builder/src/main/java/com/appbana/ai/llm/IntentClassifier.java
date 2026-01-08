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
        return String.format("""
                Classify the following user message into one of these intents:
                - create_app: User wants to create a new application
                - modify_app: User wants to modify an existing application
                - ask_question: User is asking a question
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
                """, userMessage);
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
