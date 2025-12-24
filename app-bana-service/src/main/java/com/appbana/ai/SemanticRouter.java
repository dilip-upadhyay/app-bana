package com.appbana.ai;

import com.appbana.AiAppGeneratorService;
import com.appbana.config.AppConfig;
import com.appbana.config.ConfigManager;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Semantic Router for classifying user intent via LLM.
 * Replaces legacy regex-based classification.
 */
public class SemanticRouter {

    private static final Logger LOG = LoggerFactory.getLogger(SemanticRouter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public enum Intent {
        CREATE_APP,
        MODIFY_PLAN,
        QUERY_CONTEXT,
        SMALL_TALK,
        UNKNOWN
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RouterResult {
        public Intent intent;
        public double confidence;
        public String reasoning;
        public java.util.Map<String, String> parameters;

        @Override
        public String toString() {
            return "RouterResult{intent=" + intent + ", confidence=" + confidence + ", reasoning='" + reasoning + "'}";
        }
    }

    public static RouterResult classify(String userId, String userPrompt,
            AiAppGeneratorService.ConversationContext context) {
        // HEURISTIC BYPASS: Handle explicit confirmation/creation phrases directly
        // This avoids LLM misclassification (e.g. treating "Create app" as small talk)
        String lower = userPrompt.toLowerCase().trim();
        if (lower.equals("create it") || lower.equals("create the app") || lower.equals("create the app now")
                || lower.equals("build it") || lower.equals("build the app") || lower.equals("yes, create it")) {
            LOG.info("[SemanticRouter] Heuristic Bypass: '{}' -> MODIFY_PLAN (Approval)", userPrompt);
            RouterResult result = new RouterResult();
            result.intent = Intent.MODIFY_PLAN;
            result.confidence = 1.0;
            result.reasoning = "Heuristic match for creation command";
            result.parameters = new java.util.HashMap<>();
            result.parameters.put("isApproval", "true");
            return result;
        }

        // HEURISTIC BYPASS: Detailed feature requests are NOT small talk
        // If user says "I want a feature to..." or "Track items...", force generation.
        if (lower.length() > 20 && (lower.contains("feature") || lower.contains("track") || lower.contains("manage")
                || lower.contains("entity") || lower.contains("field") || lower.contains("column"))) {
            LOG.info("[SemanticRouter] Heuristic Bypass: Detailed request -> MODIFY_PLAN");
            RouterResult result = new RouterResult();
            result.intent = Intent.MODIFY_PLAN; // Treat as modification/creation
            result.confidence = 0.9;
            result.reasoning = "Heuristic match for feature request";
            return result;
        }

        try {
            AppConfig config = ConfigManager.getConfig();
            if (!AiProviderFactory.isAiEnabled(config)) {
                LOG.warn("[SemanticRouter] AI disabled, falling back to UNKNOWN");
                return new RouterResult() {
                    {
                        intent = Intent.UNKNOWN;
                    }
                };
            }

            AiProvider provider = AiProviderFactory.createProvider(config);
            String systemPrompt = AiSystemPrompts.getSemanticRouterPrompt();

            // Augment prompt with context summary if available
            StringBuilder augmentedPrompt = new StringBuilder();
            if (context != null && context.pendingResult != null) {
                augmentedPrompt.append("CONTEXT: User has a pending app plan for '")
                        .append(context.pendingResult.appName).append("'.\n");
            } else if (context != null) {
                // Check created app first (most recent action), then opened app (sticky
                // context)
                String activeAppId = context.lastCreatedAppId != null ? context.lastCreatedAppId
                        : context.lastOpenedAppId;
                if (activeAppId != null) {
                    augmentedPrompt.append("CONTEXT: User is viewing/modifying app '")
                            .append(activeAppId).append("'.\n");
                }
            }
            augmentedPrompt.append("USER PROMPT: ").append(userPrompt);

            // Call AI
            long start = System.currentTimeMillis();
            String jsonResponse = provider.generateAppStructure(augmentedPrompt.toString(), systemPrompt);
            long elapsed = System.currentTimeMillis() - start;

            // Parse result
            String sanitized = sanitizeJson(jsonResponse);
            RouterResult result = MAPPER.readValue(sanitized, RouterResult.class);

            LOG.info("[SemanticRouter] Classified '{}' -> {} ({}ms). Reasoning: {}",
                    userPrompt.length() > 50 ? userPrompt.substring(0, 47) + "..." : userPrompt,
                    result.intent, elapsed, result.reasoning);

            return result;

        } catch (Exception e) {
            LOG.error("[SemanticRouter] Classification failed", e);
            // Default to UNKNOWN so downstream logic can try fallback
            RouterResult fallback = new RouterResult();
            fallback.intent = Intent.UNKNOWN;
            fallback.reasoning = "Classification error: " + e.getMessage();
            return fallback;
        }
    }

    private static String sanitizeJson(String input) {
        if (input == null)
            return "{}";
        String trimmed = input.trim();
        // Remove markdown code blocks
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }
}
