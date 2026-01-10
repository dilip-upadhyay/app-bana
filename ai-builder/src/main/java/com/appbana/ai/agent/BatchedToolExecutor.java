package com.appbana.ai.agent;

import com.appbana.ai.llm.OpenAiLlmService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Batched Tool Executor for complex workflows
 * Combines multiple tool calls into single LLM request to reduce API calls
 */
@Slf4j
public class BatchedToolExecutor {

    private final OpenAiLlmService llmService;
    private final ObjectMapper objectMapper;

    public BatchedToolExecutor(OpenAiLlmService llmService) {
        this.llmService = llmService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Batch create app workflow: app + entities + pages in one LLM call
     * Reduces 8-12 API calls to 1-2
     */
    public Map<String, Object> batchCreateApp(String appName, String appDescription, List<String> entityNames)
            throws OpenAiLlmService.LlmException {
        log.info("[BatchedExecutor] Creating app '{}' with {} entities in single LLM call",
                appName, entityNames.size());

        String prompt = buildBatchedAppPrompt(appName, appDescription, entityNames);

        try {
            String response = llmService.chat(prompt);
            Map<String, Object> result = parseBatchedResponse(response);

            log.info("[BatchedExecutor] Batched response received: {} entities, {} pages",
                    ((List<?>) result.getOrDefault("entities", List.of())).size(),
                    ((List<?>) result.getOrDefault("pages", List.of())).size());

            return result;
        } catch (Exception e) {
            log.error("[BatchedExecutor] Failed to parse batched response", e);
            throw new OpenAiLlmService.LlmException("Failed to execute batched app creation", e);
        }
    }

    /**
     * Build prompt for batched app creation
     */
    private String buildBatchedAppPrompt(String appName, String appDescription, List<String> entityNames) {
        String entitiesStr = entityNames.isEmpty() ? "User, Product, Order (default entities)"
                : String.join(", ", entityNames);

        return String.format("""
                Create a complete application with the following specifications:

                App Name: %s
                Description: %s
                Entities: %s

                Generate a complete JSON response with:
                1. App metadata (name, description, version)
                2. Entity schemas (fields with types, labels, validation)
                3. Page metadata (list and form pages for each entity)

                Return ONLY valid JSON in this exact structure:
                {
                  "app": {
                    "name": "string",
                    "description": "string",
                    "version": "1.0.0"
                  },
                  "entities": [
                    {
                      "name": "EntityName",
                      "fields": [
                        {
                          "name": "fieldName",
                          "type": "text|number|email|date|boolean",
                          "label": "Field Label",
                          "required": true|false,
                          "length": 255
                        }
                      ]
                    }
                  ],
                  "pages": [
                    {
                      "name": "EntityList",
                      "type": "list",
                      "entityName": "Entity",
                      "title": "Entity List"
                    },
                    {
                      "name": "EntityForm",
                      "type": "form",
                      "entityName": "Entity",
                      "title": "Entity Form"
                    }
                  ]
                }

                Guidelines:
                - Each entity should have 4-6 relevant fields
                - Include common fields: id (auto), createdAt, updatedAt
                - Use appropriate field types (text, number, email, date, boolean)
                - All fields must have labels
                - Set length=255 for text fields
                - Create both list and form pages for each entity
                - Use professional, business-appropriate naming

                Return ONLY the JSON, no explanations.
                """, appName, appDescription != null ? appDescription : "A business application", entitiesStr);
    }

    /**
     * Parse batched LLM response into structured data
     */
    private Map<String, Object> parseBatchedResponse(String response) throws Exception {
        // Clean response (remove markdown code blocks if present)
        String cleaned = response.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        }
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        cleaned = cleaned.trim();

        // Parse JSON
        Map<String, Object> result = objectMapper.readValue(cleaned, new TypeReference<Map<String, Object>>() {
        });

        // Validate structure
        if (!result.containsKey("app") || !result.containsKey("entities") || !result.containsKey("pages")) {
            throw new IllegalArgumentException(
                    "Invalid batched response structure. Missing required keys: app, entities, pages");
        }

        return result;
    }

    /**
     * Check if a user message is suitable for batched execution
     */
    public static boolean isBatchableCreateApp(String message) {
        String lower = message.toLowerCase();
        return (lower.contains("create") || lower.contains("build") || lower.contains("make")) &&
                (lower.contains("app") || lower.contains("application"));
    }
}
