package com.appbana.ai.optimization;

import com.appbana.ai.knowledge.KnowledgeBaseService;
import com.appbana.ai.knowledge.SchemaDefinition;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for providing direct answers to simple queries using RAG only (no
 * LLM)
 * Cost optimization: Zero LLM calls for knowledge queries
 */
@Slf4j
public class DirectAnswerService {

    private final KnowledgeBaseService knowledgeBase;

    // Knowledge query patterns
    private static final Set<String> KNOWLEDGE_PATTERNS = Set.of(
            "what is", "what are", "how do i", "how to",
            "show me", "list", "available", "supported",
            "can i", "does it", "explain", "tell me about");

    public DirectAnswerService(KnowledgeBaseService knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
        log.info("DirectAnswerService initialized");
    }

    /**
     * Try to answer query directly using RAG without LLM
     * 
     * @return Optional containing direct answer if query is suitable, empty
     *         otherwise
     */
    public Optional<DirectAnswer> tryDirectAnswer(String query) {
        if (!isKnowledgeQuery(query)) {
            return Optional.empty();
        }

        try {
            log.info("[DirectAnswer] Attempting RAG-only answer for: {}", query);

            // Search knowledge base
            List<SchemaDefinition> schemas = knowledgeBase.searchRelevantSchemas(query, 5);

            if (schemas.isEmpty()) {
                log.debug("[DirectAnswer] No relevant schemas found");
                return Optional.empty();
            }

            // Format direct answer
            String answer = formatDirectAnswer(query, schemas);

            log.info("[DirectAnswer] Successfully generated RAG-only answer (0 LLM cost)");
            return Optional.of(new DirectAnswer(answer, schemas.size()));

        } catch (Exception e) {
            log.warn("[DirectAnswer] Failed to generate direct answer: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Check if query is a simple knowledge request
     */
    private boolean isKnowledgeQuery(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }

        String lower = query.toLowerCase().trim();

        // Check for knowledge patterns
        for (String pattern : KNOWLEDGE_PATTERNS) {
            if (lower.contains(pattern)) {
                return true;
            }
        }

        // Check for question words at start
        if (lower.startsWith("what") || lower.startsWith("how") ||
                lower.startsWith("which") || lower.startsWith("show")) {
            return true;
        }

        return false;
    }

    /**
     * Format schemas into a readable answer
     */
    private String formatDirectAnswer(String query, List<SchemaDefinition> schemas) {
        StringBuilder answer = new StringBuilder();

        // Detect query type and customize response
        String lower = query.toLowerCase();

        if (lower.contains("field type") || lower.contains("data type")) {
            answer.append("**Available Field Types in AppBana:**\n\n");
            formatFieldTypes(schemas, answer);
        } else if (lower.contains("component") || lower.contains("ui")) {
            answer.append("**Available UI Components:**\n\n");
            formatComponents(schemas, answer);
        } else if (lower.contains("validation") || lower.contains("rule")) {
            answer.append("**Available Validation Rules:**\n\n");
            formatValidations(schemas, answer);
        } else if (lower.contains("page") || lower.contains("layout")) {
            answer.append("**Available Page Types:**\n\n");
            formatPageTypes(schemas, answer);
        } else {
            // Generic format
            answer.append("**Based on AppBana schemas:**\n\n");
            formatGeneric(schemas, answer);
        }

        // Add footer
        answer.append("\n\n*This answer was generated using RAG retrieval from AppBana's schema knowledge base.*");

        return answer.toString();
    }

    private void formatFieldTypes(List<SchemaDefinition> schemas, StringBuilder answer) {
        Map<String, String> fieldTypes = new LinkedHashMap<>();

        for (SchemaDefinition schema : schemas) {
            if ("field_type".equals(schema.getType())) {
                fieldTypes.put(schema.getName(), schema.getDescription());
            }
        }

        if (fieldTypes.isEmpty()) {
            answer.append("- text, number, email, date, boolean, select, multi-select\n");
        } else {
            fieldTypes.forEach((name, desc) -> answer.append(String.format("- **%s**: %s\n", name, desc)));
        }
    }

    private void formatComponents(List<SchemaDefinition> schemas, StringBuilder answer) {
        List<SchemaDefinition> components = schemas.stream()
                .filter(s -> "component".equals(s.getType()))
                .collect(Collectors.toList());

        if (components.isEmpty()) {
            answer.append("- Input, Button, Table, Form, Grid, Card, Modal\n");
        } else {
            components.forEach(
                    comp -> answer.append(String.format("- **%s**: %s\n", comp.getName(), comp.getDescription())));
        }
    }

    private void formatValidations(List<SchemaDefinition> schemas, StringBuilder answer) {
        List<SchemaDefinition> validations = schemas.stream()
                .filter(s -> "validation".equals(s.getType()))
                .collect(Collectors.toList());

        if (validations.isEmpty()) {
            answer.append("- required, minLength, maxLength, pattern, email, url\n");
        } else {
            validations.forEach(
                    val -> answer.append(String.format("- **%s**: %s\n", val.getName(), val.getDescription())));
        }
    }

    private void formatPageTypes(List<SchemaDefinition> schemas, StringBuilder answer) {
        List<SchemaDefinition> pages = schemas.stream()
                .filter(s -> "page".equals(s.getType()))
                .collect(Collectors.toList());

        if (pages.isEmpty()) {
            answer.append("- List Page, Form Page, Detail Page, Dashboard\n");
        } else {
            pages.forEach(
                    page -> answer.append(String.format("- **%s**: %s\n", page.getName(), page.getDescription())));
        }
    }

    private void formatGeneric(List<SchemaDefinition> schemas, StringBuilder answer) {
        for (SchemaDefinition schema : schemas) {
            answer.append(String.format("### %s\n", schema.getName()));
            answer.append(String.format("%s\n\n", schema.getDescription()));

            if (schema.getExample() != null) {
                answer.append("**Example:**\n```\n");
                answer.append(schema.getExample());
                answer.append("\n```\n\n");
            }
        }
    }

    /**
     * Result of a direct answer attempt
     */
    public static class DirectAnswer {
        private final String answer;
        private final int schemasUsed;
        private final long timestamp;

        public DirectAnswer(String answer, int schemasUsed) {
            this.answer = answer;
            this.schemasUsed = schemasUsed;
            this.timestamp = System.currentTimeMillis();
        }

        public String getAnswer() {
            return answer;
        }

        public int getSchemasUsed() {
            return schemasUsed;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}
