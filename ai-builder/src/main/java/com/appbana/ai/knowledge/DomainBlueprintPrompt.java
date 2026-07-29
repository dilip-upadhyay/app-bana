package com.appbana.ai.knowledge;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Renders retrieved domain blueprints into prompt text, including which of their entities need a
 * maker-checker approval flow.
 *
 * <p>This lives in its own class rather than on {@link AppBanaPromptEnhancer} because the two
 * callers are on opposite sides of a live/dead split, and colocating them hid that fact for a
 * commit. {@code AppBanaPromptEnhancer.enhancePrompt} is reachable only from
 * {@code AdvancedPromptEngine.buildPrompt}, which has <b>zero call sites</b> — the engine is
 * constructed in {@code AiServer} and handed to {@code AiChatController}, which does not even store
 * it. The live path is {@code AiAgent.think()}, which assembles its own prompt and, until C4.4a,
 * never read the {@code KnowledgeBaseService} that {@code AiServer} had already injected into it.
 *
 * <p>So the rule for anything added here: it reaches the model only via {@code AiAgent}. If you find
 * yourself verifying a prompt change through {@code enhancePrompt}, you are testing a chain
 * production does not execute.
 */
public final class DomainBlueprintPrompt {

    private static final String DOMAIN_TEMPLATE_CATEGORY = "domain-template";

    private DomainBlueprintPrompt() {
    }

    /**
     * @param schemas retrieved schemas, of which only the domain templates are rendered
     * @return the blueprint section, or {@code ""} when there is nothing to say (so callers can
     *         append unconditionally without emitting an empty heading and wasting tokens)
     */
    public static String render(List<SchemaDefinition> schemas) {
        if (schemas == null || schemas.isEmpty()) {
            return "";
        }

        List<SchemaDefinition> templates = schemas.stream()
                .filter(s -> s != null && DOMAIN_TEMPLATE_CATEGORY.equals(s.getCategory()))
                .toList();

        if (templates.isEmpty()) {
            return "";
        }

        StringBuilder section = new StringBuilder(
                "\nSimilar app blueprints (adapt to the user's wording; do not copy names verbatim):\n");

        for (SchemaDefinition template : templates) {
            section.append("- ").append(template.getName()).append(": ")
                    .append(template.getDescription()).append("\n");

            Map<String, Object> metadata = template.getMetadata();
            if (metadata == null) {
                continue;
            }

            appendEntities(section, metadata.get("entities"));
            appendApprovalInstruction(section, metadata.get("approvalRequiredEntities"));
        }

        return section.toString();
    }

    private static void appendEntities(StringBuilder section, Object entities) {
        if (!(entities instanceof Map<?, ?> entityMap)) {
            return;
        }
        entityMap.forEach((entityName, fields) -> {
            // String.valueOf, not implicit toString of an arbitrary Object: a nested map would
            // otherwise emit Java's `{a=b}` syntax into the prompt as if it were the field DSL.
            // Guarded rather than rendered, so a malformed template is silently skipped instead of
            // teaching the model a syntax the tools cannot parse.
            if (fields instanceof String dsl && !dsl.isBlank()) {
                section.append("    ").append(entityName).append(": ").append(dsl).append("\n");
            }
        });
    }

    private static void appendApprovalInstruction(StringBuilder section, Object approvalEntities) {
        // Collection<?>, not Set<String>: this round-trips through JSON in the Qdrant payload, so a
        // Set pattern match would pass in-process and fail against a real vector store.
        if (!(approvalEntities instanceof Collection<?> names) || names.isEmpty()) {
            return;
        }
        String joined = names.stream().map(String::valueOf).collect(Collectors.joining(", "));
        section.append("    approvalRequired: true — pass this for ").append(joined)
                .append(" when calling scaffold_app. In Phase 1, describe it in plain business ")
                .append("language (\"one team member creates it, another approves it before it ")
                .append("goes live\") and confirm before building. Do NOT add approval_status, ")
                .append("submitted_by or approval_parent_id as fields — the platform creates ")
                .append("those from the flag.\n");
    }
}
