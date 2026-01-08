package com.appbana.ai.knowledge;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Enhances prompts with relevant AppBana schemas and examples
 * Story 7.3: RAG-Enhanced Prompt Engineering
 * 
 * Features:
 * - Retrieves relevant schemas using semantic search
 * - Injects component capabilities and field types
 * - Adds few-shot examples
 * - Manages token limits
 */
@Slf4j
public class AppBanaPromptEnhancer {

    private final KnowledgeBaseService knowledgeBaseService;

    // Token limits
    private static final int MAX_SCHEMA_TOKENS = 1000;
    private static final int AVG_TOKENS_PER_SCHEMA = 100;
    private static final int MAX_SCHEMAS = MAX_SCHEMA_TOKENS / AVG_TOKENS_PER_SCHEMA;

    public AppBanaPromptEnhancer(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
        log.info("AppBanaPromptEnhancer initialized");
    }

    /**
     * Enhance a prompt with relevant AppBana context
     * 
     * @param userMessage The user's original message
     * @param basePrompt  The base prompt to enhance
     * @return Enhanced prompt with AppBana schemas and examples
     */
    public String enhancePrompt(String userMessage, String basePrompt) {
        try {
            log.debug("Enhancing prompt for message: {}", userMessage);

            // Get relevant schemas
            List<SchemaDefinition> relevantSchemas = getRelevantSchemas(userMessage);

            if (relevantSchemas.isEmpty()) {
                log.debug("No relevant schemas found, returning base prompt");
                return basePrompt;
            }

            // Build enhanced prompt
            StringBuilder enhanced = new StringBuilder();

            // Add AppBana capabilities overview
            enhanced.append(buildCapabilitiesSection());
            enhanced.append("\n\n");

            // Add relevant schemas
            enhanced.append(buildSchemaContext(relevantSchemas));
            enhanced.append("\n\n");

            // Add examples
            enhanced.append(buildExamplesSection(relevantSchemas));
            enhanced.append("\n\n");

            // Add base prompt
            enhanced.append(basePrompt);

            log.debug("Prompt enhanced with {} schemas", relevantSchemas.size());
            return enhanced.toString();

        } catch (Exception e) {
            log.error("Failed to enhance prompt, returning base prompt", e);
            return basePrompt;
        }
    }

    /**
     * Get relevant schemas for the user message using semantic search
     */
    private List<SchemaDefinition> getRelevantSchemas(String userMessage) {
        try {
            // Search for relevant schemas
            List<SchemaDefinition> schemas = knowledgeBaseService.searchRelevantSchemas(
                    userMessage,
                    MAX_SCHEMAS);

            log.debug("Found {} relevant schemas", schemas.size());
            return schemas;

        } catch (Exception e) {
            log.error("Failed to retrieve relevant schemas", e);
            return Collections.emptyList();
        }
    }

    /**
     * Build AppBana capabilities overview section
     */
    private String buildCapabilitiesSection() {
        return """
                AppBana Platform Context:
                AppBana is a metadata-driven low-code platform for building business applications.

                Available Components:
                - input: Text input fields with validation
                - button: Action buttons (save, navigate, API calls)
                - table: Data tables with pagination and actions
                - app-grid: Responsive grid layout for forms
                - container: Container for grouping components

                Field Types: 39 types available including text, email, phone, number, date, boolean, reference, etc.

                Metadata Structure:
                - Entities: Define data models with fields
                - Pages: Define UI layouts with components
                - Components: Nested structure with props and children
                """;
    }

    /**
     * Build schema context section with relevant schemas
     */
    private String buildSchemaContext(List<SchemaDefinition> schemas) {
        StringBuilder context = new StringBuilder();
        context.append("Relevant AppBana Schemas:\n");

        // Group schemas by type
        Map<SchemaDefinition.SchemaType, List<SchemaDefinition>> byType = schemas.stream()
                .collect(Collectors.groupingBy(SchemaDefinition::getType));

        // Add field types
        if (byType.containsKey(SchemaDefinition.SchemaType.ENTITY_FIELD)) {
            context.append("\nField Types:\n");
            for (SchemaDefinition schema : byType.get(SchemaDefinition.SchemaType.ENTITY_FIELD)) {
                context.append(String.format("- %s: %s\n",
                        schema.getName(),
                        schema.getDescription()));

                // Add metadata if available
                if (schema.getMetadata() != null && schema.getMetadata().containsKey("htmlType")) {
                    context.append(String.format("  HTML type: %s\n",
                            schema.getMetadata().get("htmlType")));
                }
            }
        }

        // Add components
        if (byType.containsKey(SchemaDefinition.SchemaType.COMPONENT)) {
            context.append("\nComponents:\n");
            for (SchemaDefinition schema : byType.get(SchemaDefinition.SchemaType.COMPONENT)) {
                context.append(String.format("- %s: %s\n",
                        schema.getName(),
                        schema.getDescription()));

                // Add key properties if available
                if (schema.getProperties() != null && !schema.getProperties().isEmpty()) {
                    context.append("  Key properties:\n");
                    schema.getProperties().entrySet().stream()
                            .limit(5) // Limit to avoid token overflow
                            .forEach(entry -> {
                                SchemaDefinition.PropertyDefinition prop = entry.getValue();
                                context.append(String.format("    - %s (%s): %s%s\n",
                                        entry.getKey(),
                                        prop.getType(),
                                        prop.getDescription(),
                                        prop.isRequired() ? " [required]" : ""));
                            });
                }
            }
        }

        // Add pages
        if (byType.containsKey(SchemaDefinition.SchemaType.PAGE)) {
            context.append("\nPage Structure:\n");
            for (SchemaDefinition schema : byType.get(SchemaDefinition.SchemaType.PAGE)) {
                context.append(String.format("- %s\n", schema.getDescription()));
            }
        }

        // Add validations
        if (byType.containsKey(SchemaDefinition.SchemaType.VALIDATION)) {
            context.append("\nValidation Rules:\n");
            for (SchemaDefinition schema : byType.get(SchemaDefinition.SchemaType.VALIDATION)) {
                context.append(String.format("- %s\n", schema.getDescription()));
            }
        }

        return context.toString();
    }

    /**
     * Build examples section with few-shot examples
     */
    private String buildExamplesSection(List<SchemaDefinition> schemas) {
        StringBuilder examples = new StringBuilder();
        examples.append("Examples:\n");

        // Collect examples from schemas
        List<String> allExamples = schemas.stream()
                .filter(s -> s.getExamples() != null && !s.getExamples().isEmpty())
                .flatMap(s -> s.getExamples().stream())
                .limit(5) // Limit examples to avoid token overflow
                .collect(Collectors.toList());

        if (allExamples.isEmpty()) {
            examples.append("(No specific examples available)\n");
        } else {
            for (int i = 0; i < allExamples.size(); i++) {
                examples.append(String.format("%d. %s\n", i + 1, allExamples.get(i)));
            }
        }

        return examples.toString();
    }

    /**
     * Get examples for a specific component type
     */
    public List<String> getComponentExamples(String componentType) {
        try {
            return knowledgeBaseService.getExamples(componentType);
        } catch (Exception e) {
            log.error("Failed to get examples for component: {}", componentType, e);
            return Collections.emptyList();
        }
    }

    /**
     * Search for schemas by type
     */
    public List<SchemaDefinition> searchSchemasByType(
            SchemaDefinition.SchemaType type,
            String query,
            int limit) {
        try {
            return knowledgeBaseService.searchByType(type, query, limit);
        } catch (Exception e) {
            log.error("Failed to search schemas by type", e);
            return Collections.emptyList();
        }
    }

    /**
     * Get relevant schemas formatted as string for agent prompts
     * Story 8.4: Agent-LLM Integration
     */
    public String getRelevantSchemas(String userMessage, int limit) {
        try {
            List<SchemaDefinition> schemas = knowledgeBaseService.searchRelevantSchemas(
                    userMessage,
                    Math.min(limit, MAX_SCHEMAS));

            if (schemas.isEmpty()) {
                return "";
            }

            return buildSchemaContext(schemas);

        } catch (Exception e) {
            log.error("Failed to get relevant schemas", e);
            return "";
        }
    }
}
