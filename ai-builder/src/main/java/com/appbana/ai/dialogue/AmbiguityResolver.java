package com.appbana.ai.dialogue;

import com.appbana.ai.llm.OpenAiLlmService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Ambiguity resolver for detecting and clarifying unclear user inputs
 * Story: 3.2 - Implement Ambiguity Resolver
 */
@Slf4j
public class AmbiguityResolver {

    private final OpenAiLlmService llmService;
    private final ObjectMapper objectMapper;

    public AmbiguityResolver(OpenAiLlmService llmService) {
        this.llmService = llmService;
        this.objectMapper = new ObjectMapper();
        log.info("Ambiguity Resolver initialized");
    }

    /**
     * Detect if user input is ambiguous
     */
    public AmbiguityResult detectAmbiguity(String userMessage, Map<String, Object> context) {
        try {
            String prompt = buildDetectionPrompt(userMessage, context);
            String response = llmService.chat(prompt);

            return parseAmbiguityResponse(response);

        } catch (Exception e) {
            log.warn("Failed to detect ambiguity, assuming clear", e);
            return new AmbiguityResult(false, null, null, 1.0);
        }
    }

    /**
     * Generate clarifying questions for ambiguous input
     */
    public List<String> generateClarifyingQuestions(String userMessage, List<String> ambiguousAspects) {
        try {
            String prompt = buildClarificationPrompt(userMessage, ambiguousAspects);
            String response = llmService.chat(prompt);

            return parseQuestions(response);

        } catch (Exception e) {
            log.error("Failed to generate clarifying questions", e);
            return List.of("Could you please provide more details about what you need?");
        }
    }

    /**
     * Offer multiple interpretations of ambiguous input
     */
    public List<Interpretation> offerInterpretations(String userMessage) {
        try {
            String prompt = buildInterpretationPrompt(userMessage);
            String response = llmService.chat(prompt);

            return parseInterpretations(response);

        } catch (Exception e) {
            log.error("Failed to generate interpretations", e);
            return List.of();
        }
    }

    /**
     * Learn from user's disambiguation choice
     */
    public void learnFromChoice(String userMessage, String chosenInterpretation, Map<String, Object> context) {
        // Store the preference for future reference
        log.info("Learning from disambiguation: '{}' -> '{}'", userMessage, chosenInterpretation);

        // TODO: Store in user preferences or pattern database
        // This could update the UserPreferenceEngine with learned patterns
    }

    private String buildDetectionPrompt(String userMessage, Map<String, Object> context) {
        return String.format("""
                Analyze if the following user message is ambiguous or unclear.

                User message: "%s"

                Context: %s

                Respond ONLY with valid JSON in this format:
                {
                  "isAmbiguous": true/false,
                  "ambiguousAspects": ["aspect1", "aspect2"],
                  "reason": "explanation",
                  "confidence": 0.0-1.0
                }

                Consider ambiguous if:
                - Multiple valid interpretations exist
                - Key details are missing (entity names, field types, etc.)
                - Pronouns without clear antecedents
                - Vague terms like "it", "that", "some"
                """,
                userMessage,
                context != null ? context.toString() : "none");
    }

    private String buildClarificationPrompt(String userMessage, List<String> ambiguousAspects) {
        return String.format("""
                Generate 2-3 clarifying questions for this ambiguous user message.

                User message: "%s"
                Ambiguous aspects: %s

                Respond ONLY with valid JSON array of questions:
                ["Question 1?", "Question 2?", "Question 3?"]

                Make questions:
                - Specific and actionable
                - Easy to answer
                - Focused on one aspect each
                """,
                userMessage,
                String.join(", ", ambiguousAspects));
    }

    private String buildInterpretationPrompt(String userMessage) {
        return String.format("""
                Provide 2-3 possible interpretations of this ambiguous message.

                User message: "%s"

                Respond ONLY with valid JSON array:
                [
                  {"interpretation": "...", "confidence": 0.0-1.0},
                  {"interpretation": "...", "confidence": 0.0-1.0}
                ]
                """,
                userMessage);
    }

    private AmbiguityResult parseAmbiguityResponse(String response) {
        try {
            // Try to extract JSON from response
            String json = extractJson(response);
            Map<String, Object> result = objectMapper.readValue(json, Map.class);

            boolean isAmbiguous = (Boolean) result.getOrDefault("isAmbiguous", false);
            List<String> aspects = (List<String>) result.getOrDefault("ambiguousAspects", List.of());
            String reason = (String) result.getOrDefault("reason", "");
            double confidence = ((Number) result.getOrDefault("confidence", 1.0)).doubleValue();

            return new AmbiguityResult(isAmbiguous, aspects, reason, confidence);

        } catch (Exception e) {
            log.warn("Failed to parse ambiguity response: {}", response, e);
            return new AmbiguityResult(false, null, null, 1.0);
        }
    }

    private List<String> parseQuestions(String response) {
        try {
            String json = extractJson(response);
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            log.warn("Failed to parse questions, using fallback", e);
            return List.of("Could you please clarify what you mean?");
        }
    }

    private List<Interpretation> parseInterpretations(String response) {
        try {
            String json = extractJson(response);
            List<Map<String, Object>> raw = objectMapper.readValue(json, List.class);

            List<Interpretation> interpretations = new ArrayList<>();
            for (Map<String, Object> item : raw) {
                String text = (String) item.get("interpretation");
                double confidence = ((Number) item.getOrDefault("confidence", 0.5)).doubleValue();
                interpretations.add(new Interpretation(text, confidence));
            }

            return interpretations;

        } catch (Exception e) {
            log.warn("Failed to parse interpretations", e);
            return List.of();
        }
    }

    private String extractJson(String response) {
        // Extract JSON from markdown code blocks or plain text
        if (response.contains("```json")) {
            int start = response.indexOf("```json") + 7;
            int end = response.indexOf("```", start);
            return response.substring(start, end).trim();
        } else if (response.contains("```")) {
            int start = response.indexOf("```") + 3;
            int end = response.indexOf("```", start);
            return response.substring(start, end).trim();
        } else if (response.contains("{") || response.contains("[")) {
            // Find first { or [ and last } or ]
            int start = Math.min(
                    response.indexOf("{") >= 0 ? response.indexOf("{") : Integer.MAX_VALUE,
                    response.indexOf("[") >= 0 ? response.indexOf("[") : Integer.MAX_VALUE);
            int end = Math.max(response.lastIndexOf("}"), response.lastIndexOf("]")) + 1;
            return response.substring(start, end).trim();
        }
        return response.trim();
    }

    @Data
    public static class AmbiguityResult {
        private final boolean isAmbiguous;
        private final List<String> ambiguousAspects;
        private final String reason;
        private final double confidence;
    }

    @Data
    public static class Interpretation {
        private final String text;
        private final double confidence;
    }
}
