package com.appbana.ai;

import com.appbana.AiAppGeneratorService;
import com.appbana.model.EntitySchema;
import com.appbana.workflow.model.WorkflowDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Intelligent engine to answer questions about the current conversation context
 * (e.g., pending implementation plans, existing app details).
 * Prevents the AI from "hallucinating" generic answers when specific context
 * exists.
 */
public class ContextIntelligenceEngine {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Attempts to answer a user prompt using only the current session context.
     * Returns null if the prompt is not context-related or no context exists.
     */
    public static String resolveContextualQuery(String prompt, AiAppGeneratorService.ConversationContext context) {
        if (context == null || context.pendingResult == null) {
            return null; // No context to answer from
        }

        String lowerPrompt = prompt.toLowerCase();
        AiAppGeneratorService.GenerationResult plan = context.pendingResult;

        // 1. Questions about "Entities" or "Fields"
        if (lowerPrompt.contains("field") || lowerPrompt.contains("entity") || lowerPrompt.contains("property")
                || lowerPrompt.contains("column")) {
            // Check if user is asking about a specific entity
            if (plan.entities != null) {
                for (EntitySchema entity : plan.entities) {
                    if (lowerPrompt.contains(entity.getName().toLowerCase())) {
                        return formatEntityDetails(entity);
                    }
                }
                // Fallback: list all entities if no specific one mentioned
                if (lowerPrompt.contains("what entities") || lowerPrompt.contains("list entities")) {
                    return "In the current plan, we are creating: " +
                            plan.entities.stream().map(EntitySchema::getName).collect(Collectors.joining(", ")) + ".";
                }
            }
        }

        // 2. Questions about "Workflows"
        if (lowerPrompt.contains("workflow") || lowerPrompt.contains("process") || lowerPrompt.contains("logic")) {
            if (plan.workflows != null && !plan.workflows.isEmpty()) {
                StringBuilder sb = new StringBuilder("The current plan includes these workflows:\n");
                for (WorkflowDefinition wf : plan.workflows) {
                    sb.append("- **").append(wf.getName()).append("**: ").append(wf.getDescription()).append("\n");
                }
                return sb.toString();
            } else {
                return "The current plan does not have any workflows yet. Shall we add one?";
            }
        }

        return null; // Prompt didn't match any known context patterns
    }

    private static String formatEntityDetails(EntitySchema entity) {
        StringBuilder sb = new StringBuilder();
        sb.append("The **").append(entity.getName()).append("** entity has the following fields:\n\n");
        if (entity.getFields() != null) {
            entity.getFields().forEach(f -> {
                sb.append("- **").append(f.getName()).append("** (").append(f.getType()).append(")");
                if (f.isRequired())
                    sb.append(" *Required*");
                sb.append("\n");
            });
        }
        return sb.toString();
    }
}
