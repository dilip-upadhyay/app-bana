package com.appbana.ai;

import com.appbana.AiAppGeneratorService;
import com.appbana.generator.ConversationManager.ConversationContext;
import com.appbana.model.EntitySchema;
import com.appbana.workflow.model.WorkflowDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.stream.Collectors;

/**
 * Intelligent engine to answer questions about the current conversation context
 * (e.g., pending implementation plans, existing app details).
 * Prevents the AI from "hallucinating" generic answers when specific context
 * exists.
 */
public class ContextIntelligenceEngine {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ContextIntelligenceEngine.class);

    /**
     * Attempts to answer a user prompt using only the current session context.
     * Returns null if the prompt is not context-related or no context exists.
     */
    public static String resolveContextualQuery(String prompt, ConversationContext context) {
        if (context == null) {
            LOG.warn("[ContextIntelligence] Context is null");
            return null;
        }
        if (context.pendingResult == null) {
            LOG.warn("[ContextIntelligence] PendingResult is null");
            return null;
        }

        String lowerPrompt = prompt.toLowerCase().trim();
        AiAppGeneratorService.GenerationResult plan = context.pendingResult;

        LOG.info("[ContextIntelligence] Resolving query: '{}', Entities avail: {}", lowerPrompt,
                plan.entities != null
                        ? plan.entities.stream().map(EntitySchema::getName).collect(Collectors.joining(","))
                        : "null");

        // CRITICAL FIX: proper feedback loop
        // If the user is critiquing ("missing", "gap", "review") or asking for changes,
        // we MUST return null so the LLM processes it as a modification request.
        boolean isModification = lowerPrompt.contains("change") || lowerPrompt.contains("update") ||
                lowerPrompt.contains("add ") || lowerPrompt.contains("remove") ||
                lowerPrompt.contains("delete") || lowerPrompt.contains("missing") ||
                lowerPrompt.contains("gap") || lowerPrompt.contains("review") ||
                lowerPrompt.contains("critique") || lowerPrompt.contains("too few") ||
                lowerPrompt.contains("issue") || lowerPrompt.contains("wrong");

        if (isModification) {
            LOG.info("[ContextIntelligence] Detected modification/critique intent. Yielding to AI Generator.");
            return null;
        }

        // 1. Questions about "Entities" or "Fields"
        // Expanded trigger words: "show me", "what about", "fields for", "entity"
        boolean isEntityQuery = lowerPrompt.contains("field") || lowerPrompt.contains("entity") ||
                lowerPrompt.contains("property") || lowerPrompt.contains("column") ||
                lowerPrompt.startsWith("show me") || lowerPrompt.startsWith("what about") ||
                lowerPrompt.contains("describe");

        LOG.info("[ContextIntelligence] isEntityQuery: {}", isEntityQuery);

        if (isEntityQuery) {
            // Check if user is asking about a specific entity
            if (plan.entities != null) {
                for (EntitySchema entity : plan.entities) {
                    LOG.info("Checking entity: {} vs prompt", entity.getName());
                    if (lowerPrompt.contains(entity.getName().toLowerCase())) {
                        LOG.info("Match found for entity: {}", entity.getName());
                        return formatEntityDetails(entity);
                    }
                }
                // Fallback: list all entities if no specific one mentioned
                if (lowerPrompt.contains("what entities") || lowerPrompt.contains("list entities")
                        || lowerPrompt.contains("show entities")) {
                    LOG.info("Listing all entities.");
                    return "In the current plan, we are creating: " +
                            plan.entities.stream().map(EntitySchema::getName).collect(Collectors.joining(", ")) + ".";
                }
            }
        }

        // 2. Questions about "Workflows"
        // Expanded trigger words: "explain", "how does", "workflow", "process", "logic"
        boolean isWorkflowQuery = lowerPrompt.contains("workflow") || lowerPrompt.contains("process") ||
                lowerPrompt.contains("logic") || lowerPrompt.contains("explain") ||
                lowerPrompt.contains("how does") || lowerPrompt.contains("what happens");

        if (isWorkflowQuery) {
            if (plan.workflows != null && !plan.workflows.isEmpty()) {
                // Check if asking about a specific workflow
                for (WorkflowDefinition wf : plan.workflows) {
                    if (lowerPrompt.contains(wf.getName().toLowerCase())) {
                        return formatWorkflowDetails(wf);
                    }
                }

                // Fallback: list all workflows
                StringBuilder sb = new StringBuilder("The current plan includes these workflows:\n");
                for (WorkflowDefinition wf : plan.workflows) {
                    sb.append("- **").append(wf.getName()).append("**: ").append(wf.getDescription()).append("\n");
                }
                return sb.toString();
            } else if (lowerPrompt.contains("workflow")) {
                return "The current plan does not have any workflows yet. Shall we add one?";
            }
        }

        return null;
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

    private static String formatWorkflowDetails(WorkflowDefinition wf) {
        StringBuilder sb = new StringBuilder();
        sb.append("### Workflow: ").append(wf.getName()).append("\n");
        sb.append("**Trigger:** ").append(wf.getTriggerEvent()).append("\n");
        sb.append("**Description:** ").append(wf.getDescription()).append("\n\n");

        if (wf.getDefinitionJson() != null && !wf.getDefinitionJson().isEmpty()) {
            try {
                JsonNode root = MAPPER.readTree(wf.getDefinitionJson());
                if (root.has("nodes") && root.get("nodes").isArray()) {
                    sb.append("**Logic Steps:**\n");
                    int stepNum = 1;
                    for (JsonNode node : root.get("nodes")) {
                        String type = node.has("type") ? node.get("type").asText() : "Unknown";
                        String label = node.has("label") ? node.get("label").asText() : type;

                        if ("START".equalsIgnoreCase(type) || "END".equalsIgnoreCase(type)) {
                            // Skip start/end for natural explanation unless essential
                            continue;
                        }

                        sb.append(stepNum++).append(". **").append(label).append("**");
                        // Add details based on type
                        if ("DECISION".equalsIgnoreCase(type)) {
                            sb.append(" (Condition Check)");
                        } else if ("USER_TASK".equalsIgnoreCase(type)) {
                            sb.append(" (Wait for User Action)");
                        } else if ("SERVICE_TASK".equalsIgnoreCase(type)) {
                            sb.append(" (System Action)");
                        } else if ("NOTIFICATION".equalsIgnoreCase(type)) {
                            sb.append(" (Send Alert)");
                        }
                        sb.append("\n");
                    }
                }
            } catch (Exception e) {
                sb.append("(Could not parse detailed steps: ").append(e.getMessage()).append(")");
            }
        }
        return sb.toString();
    }
}
