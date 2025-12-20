package com.appbana;

import com.appbana.ai.AiProvider;
import com.appbana.ai.AiProviderFactory;
import com.appbana.ai.AiSystemPrompts;
import com.appbana.ai.AgentMemoryService;
import com.appbana.ai.SmallTalkEngine;
import com.appbana.ai.IntentCache;
import com.appbana.ai.MetadataIntelligenceEngine;
import com.appbana.config.AppConfig;
import com.appbana.config.ConfigManager;
import com.appbana.model.EntitySchema;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.appbana.model.AppMetadata; // added for persistence defaultPage update
import com.appbana.workflow.model.WorkflowDefinition; // added for workflow generation
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule; // added for date handling
import com.fasterxml.jackson.databind.SerializationFeature; // added for robustness

// added for AI result validation

/**
 * AI-powered app generation service.
 * Resolves conversational intent into structured actions and falls back to
 * template-based generation.
 */
public class AiAppGeneratorService {

    private static final Logger LOG = LoggerFactory.getLogger(AiAppGeneratorService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {
    };

    private static final String ACTION_LIST_APPS = "listApps";
    private static final String ACTION_LOAD_APP = "loadApp";
    private static final String ACTION_DELETE_APP = "deleteApp";
    private static final String ACTION_LIST_PAGES = "listPages";
    private static final String ACTION_OPEN_PAGE = "openPage";
    private static final String ACTION_GENERATE_APP = "generateApp";
    private static final String ACTION_DESCRIBE_APP = "describeApp";
    private static final String PAYLOAD_APPS = "apps";
    private static final String PAYLOAD_ACTION = "action";
    private static final String PAYLOAD_REPLY = "reply";
    private static final String PAYLOAD_SMALL_TALK = "smallTalk";
    private static final String DEFAULT_USER = "default";
    private static final String DOMAIN_TEMPLATES_PATH = "../builder-database/10-domain-templates.json";
    private static List<Map<String, Object>> cachedDomainTemplates;

    // Conversation context tracking for continuity across requests
    private static final Map<String, ConversationContext> sessionContexts = new HashMap<>();
    private static final long CONTEXT_TIMEOUT_MS = 10 * 60 * 1000; // 10 minutes

    /**
     * Conversation context to maintain state across multiple requests
     */
    public static class ConversationContext {
        public String lastDiscussedAppType;
        public String lastDiscussedAppDescription;
        public List<String> discussedEntities;
        public String lastCreatedAppId;
        public String lastOpenedAppId;
        public GenerationResult pendingResult; // Staged app generation waiting for approval
        public long timestamp;

        public ConversationContext() {
            this.discussedEntities = new ArrayList<>();
            this.timestamp = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CONTEXT_TIMEOUT_MS;
        }

        public void refresh() {
            this.timestamp = System.currentTimeMillis();
        }
    }

    public static GenerationResult generateApp(GenerationRequest request) {
        // Initialize metadata intelligence engine
        MetadataIntelligenceEngine.initialize();

        LOG.info("[AI] Incoming GenerationRequest: action={}, description={}, options={}",
                request != null ? request.action : null,
                request != null ? request.description : null,
                request != null ? request.description : null,
                request != null ? request.options : null);
        try {
            // FIX: Handle small talk check BEFORE context merging to avoid "Hi" becoming
            // 1. Resolve User and Context first
            String userId = resolveUserId(request);
            ConversationContext ctx = getContext(userId);

            // FIX: Sync context with frontend "currentAppId" if provided
            // This handles manual UI selection updates so the AI knows which app is active
            if (request.options != null && request.options.containsKey("currentAppId")) {
                String explicitAppId = String.valueOf(request.options.get("currentAppId"));
                if (explicitAppId != null && !explicitAppId.equals("null") && !explicitAppId.isBlank()) {
                    updateOpenedApp(userId, explicitAppId);
                    // Re-fetch context to ensure we have the latest state (though it should be
                    // shared ref)
                    ctx = getContext(userId);
                }
            }

            // FIX: Check for confirmation of pending plan BEFORE Router/SmallTalk
            // This prevents "create it" being intercepted as Small Talk
            boolean isHeuristicApproval = ctx.pendingResult != null && isConfirmationPhrase(request.description);

            if (isHeuristicApproval) {
                LOG.info("[AI] User confirmed pending generation plan (via Heuristic detection)");
                GenerationResult pending = ctx.pendingResult;
                postProcessAndPersistIfNeeded(pending, request);
                ctx.pendingResult = null; // Clear after processing
                return pending;
            }

            // ==================================================================================
            // NEW: BRAIN-FIRST SEMANTIC ROUTING
            // We use the LLM to classify intent BEFORE running any regex rules.
            // ==================================================================================
            com.appbana.ai.SemanticRouter.RouterResult route = com.appbana.ai.SemanticRouter.classify(userId,
                    request.description, ctx);

            if (route.intent == com.appbana.ai.SemanticRouter.Intent.SMALL_TALK) {
                return handleSmallTalkIfNeeded(request, null);
            } else if (route.intent == com.appbana.ai.SemanticRouter.Intent.QUERY_CONTEXT) {
                // If router says it's a query, force context engine to answer
                String contextAnswer = com.appbana.ai.ContextIntelligenceEngine
                        .resolveContextualQuery(request.description, ctx);
                if (contextAnswer != null) {
                    GenerationResult res = new GenerationResult();
                    res.success = true;
                    res.payload = new HashMap<>();
                    res.payload.put(PAYLOAD_REPLY, contextAnswer);
                    res.payload.put(PAYLOAD_ACTION, ACTION_GENERATE_APP);
                    return res;
                }
                // Fallback: If context engine has no answer, assume it is a
                // Refinement/Modification
                // e.g. "This is for the Salon App" -> updates target context
                LOG.info("[AI] Context Query unresolved, treating as MODIFY_PLAN (Context Refinement)");
                route.intent = com.appbana.ai.SemanticRouter.Intent.MODIFY_PLAN;
                // Continue to MODIFY_PLAN block logic below
            } else if (route.intent == com.appbana.ai.SemanticRouter.Intent.MODIFY_PLAN) {
                // EXPLICITLY set action to update_plan
                request.action = "update_plan";
                // Inject AI-extracted target app ID into request options for downstream logic
                // Inject AI-extracted target app ID into request options for downstream logic
                if (route.parameters != null) {
                    if (route.parameters.containsKey("targetAppId")) {
                        if (request.options == null)
                            request.options = new HashMap<>();
                        request.options.put("targetAppId", route.parameters.get("targetAppId"));
                    }

                    // FIX: Check for explicit "isApproval" from Semantic Router
                    // If router says this is an approval (e.g. "yes, do it"), treat it as
                    // confirmation
                    if (ctx.pendingResult != null &&
                            "true".equalsIgnoreCase(route.parameters.get("isApproval"))) {
                        LOG.info("[AI] User confirmed pending plan (via Semantic Router isApproval)");
                        GenerationResult pending = ctx.pendingResult;
                        postProcessAndPersistIfNeeded(pending, request);
                        ctx.pendingResult = null;
                        return pending;
                    }
                }
            }

            // SAFETY LATCH: If we have a pending plan, and logic drifted to "Application"
            // (generic fallback) because input was ambiguous (e.g. "yes", "go ahead"),
            // FORCE confirmation of the pending plan instead of creating a new empty app.
            // FIX: Only trigger this if it's ACTUALLY a confirmation phrase. If it's a
            // modification ("add login"),
            // let it fall through to Sticky Context.
            if (ctx.pendingResult != null && "Application".equals(parseIntent(request.description).appName)
                    && isConfirmationPhrase(request.description)) {
                LOG.info(
                        "[AI] Ambiguous input '{}' resolved to Generic App, but Pending Plan exists. Interpreting as CONFIRMATION.",
                        request.description);
                GenerationResult pending = ctx.pendingResult;
                postProcessAndPersistIfNeeded(pending, request);
                ctx.pendingResult = null;
                return pending;
            }

            // STICKY CONTEXT: If user is "inside" an app (lastOpenedAppId is set),
            // and the intent is ambiguous (Generic Application) or NULL,
            // we MUST assume they mean to MODIFY the active app.
            // This fixes "Context Loss" where "add a form" -> "Created Generic App"
            if (ctx.lastOpenedAppId != null &&
                    (route.intent == com.appbana.ai.SemanticRouter.Intent.UNKNOWN
                            || "Application".equals(parseIntent(request.description).appName))) {
                LOG.info("[AI] Ambiguous output with Sticky Context '{}'. Defaulting to UPDATE_PLAN.",
                        ctx.lastOpenedAppId);
                request.action = "update_plan";
                if (request.options == null)
                    request.options = new HashMap<>();
                request.options.put("targetAppId", ctx.lastOpenedAppId);
                // We do NOT return here; we let it fall through to processAppGeneration
                // where it will now see Action=update_plan
            }

            // Legacy Fallback (keeping for safety during transition, but Router acts first)
            String normalizedAction = resolveAction(request);

            // SPECIAL CASE: Check for "explain/describe" in text if metadata missed it
            if (normalizedAction == null && (request.description.toLowerCase().contains("explain") ||
                    request.description.toLowerCase().contains("describe") ||
                    request.description.toLowerCase().contains("summary of"))) {
                GenerationResult desc = handleStructuredAction(ACTION_DESCRIBE_APP, request);
                if (desc != null && desc.success)
                    return desc;
            }

            // FIX #3: Handle "create pages" / "regenerate pages" requests VIA AI PARAMETERS
            // BUT: Only if NOT already classified as a full creation/modification plan
            boolean isExplicitGen = (route.intent == com.appbana.ai.SemanticRouter.Intent.CREATE_APP ||
                    route.intent == com.appbana.ai.SemanticRouter.Intent.MODIFY_PLAN);

            if (!isExplicitGen && route.parameters != null && (route.parameters.containsKey("pageName") ||
                    (route.reasoning != null && route.reasoning.toLowerCase().contains("page")))) {

                // If AI extracted an App ID, prioritize it
                if (route.parameters.containsKey("targetAppId")) {
                    request.options.put("targetAppId", route.parameters.get("targetAppId"));
                }

                return handleRegeneratePagesRequest(request);
            }

            // FIX: Only check small talk if NOT an explicit generation/modification intent
            if (!isExplicitGen) {
                GenerationResult smallTalk = handleSmallTalkIfNeeded(request, normalizedAction);
                if (smallTalk != null) {
                    return smallTalk;
                }
            }

            // Extract app type from description/context and track it
            // BUT: Don't overwrite context if this is a continuation request (handled by
            // router now)
            String appType = extractAppType(request != null ? request.description : null);
            if (appType != null && request.description != null) {
                updateDiscussedApp(request.userId, appType, request.description);
            }

            // ALSO: If description mentions entities/features, store it even if no app type
            // extracted. USING AI PARAMETERS if available.
            if (route.parameters != null && route.parameters.containsKey("entityName")) {
                String descAppType = appType != null ? appType : "application";
                updateDiscussedApp(request.userId, descAppType, request.description);
                LOG.info("[AI Context] Stored detailed app description in context (entity detected)");
            } else if (request.description != null &&
                    (request.description.toLowerCase().contains("entity") ||
                            request.description.toLowerCase().contains("entities") ||
                            request.description.toLowerCase().matches(
                                    ".*\\b(customer|user|product|order|item|service|appointment|project|task)\\b.*"))) {
                String descAppType = appType != null ? appType : "application";
                updateDiscussedApp(request.userId, descAppType, request.description);
                LOG.info("[AI Context] Stored detailed app description in context");
            }

            if (route.intent == com.appbana.ai.SemanticRouter.Intent.CREATE_APP ||
                    route.intent == com.appbana.ai.SemanticRouter.Intent.MODIFY_PLAN) {
                GenerationResult gen = runGenerationPipelines(request);
                if (appType != null && !appType.isBlank()) {
                    gen.appType = appType;
                    if (gen.payload == null)
                        gen.payload = new HashMap<>();
                    gen.payload.put("appType", appType);
                }
                ensureStructuralMinimum(gen, appType, request);

                // NEW FLOW: Do NOT persist immediately. Stage for review.
                ctx.pendingResult = gen;

                if (gen.payload == null)
                    gen.payload = new HashMap<>();

                // FIX: If this is an UPDATE and AI provided a coherent reply, use it!
                // Don't overwrite it with the generic "Implementation Plan" boilerplate.
                boolean isUpdate = "update_plan".equals(request.action);
                boolean hasAiReply = gen.payload.containsKey(PAYLOAD_REPLY) &&
                        gen.payload.get(PAYLOAD_REPLY) != null &&
                        !String.valueOf(gen.payload.get(PAYLOAD_REPLY)).isBlank();

                if (isUpdate && hasAiReply) {
                    // Append a consistent "Call to Action" if usage dictates, or just respect AI's
                    // voice
                    String aiReply = String.valueOf(gen.payload.get(PAYLOAD_REPLY));
                    if (!aiReply.contains("Create it") && !aiReply.contains("Yes")) {
                        aiReply += "\n\n**Ready?** Say **'Yes'** or **'Create it'** to apply these changes.";
                    }
                    gen.payload.put(PAYLOAD_REPLY, aiReply);
                } else {
                    // Build Review Message (Legacy/New App logic)
                    StringBuilder plan = new StringBuilder();
                    plan.append("### 📋 Implementation Plan\n\n");
                    plan.append("**App Name:** ").append(gen.appName).append("\n");
                    plan.append("**Description:** ").append(gen.appDescription).append("\n\n");

                    if (gen.entities != null && !gen.entities.isEmpty()) {
                        plan.append("**Entities to Create:**\n");
                        for (EntitySchema e : gen.entities) {
                            plan.append("- **").append(e.getName()).append("** (");
                            if (e.getFields() != null)
                                plan.append(e.getFields().size()).append(" fields");
                            plan.append(")\n");
                        }
                        plan.append("\n");
                    }

                    if (gen.workflows != null && !gen.workflows.isEmpty()) {
                        plan.append("**Workflows:**\n");
                        for (WorkflowDefinition w : gen.workflows) {
                            if (w == null)
                                continue;
                            plan.append("- **").append(w.getName()).append("**\n");
                            plan.append("  Trigger: ")
                                    .append(w.getTriggerEvent() != null ? w.getTriggerEvent() : "Manual")
                                    .append("\n");
                            if (w.getDescription() != null) {
                                plan.append("  Description: ").append(w.getDescription()).append("\n");
                            }
                            // Parse definitionJson to show steps
                            if (w.getDefinitionJson() != null && !w.getDefinitionJson().isEmpty()) {
                                try {
                                    com.fasterxml.jackson.databind.JsonNode root = MAPPER
                                            .readTree(w.getDefinitionJson());
                                    if (root.has("nodes") && root.get("nodes").isArray()) {
                                        plan.append("  Steps:\n");
                                        for (com.fasterxml.jackson.databind.JsonNode node : root.get("nodes")) {
                                            String type = node.has("type") ? node.get("type").asText() : "Unknown";
                                            String label = node.has("label") ? node.get("label").asText() : type;
                                            if (!"START".equalsIgnoreCase(type) && !"END".equalsIgnoreCase(type)) {
                                                plan.append("    - ").append(label).append(" (").append(type)
                                                        .append(")\n");
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    // Ignore parsing errors for preview
                                }
                            }
                        }
                        plan.append("\n");
                    }

                    if (gen.pages != null && !gen.pages.isEmpty()) {
                        plan.append("**Pages to Create:**\n");
                        for (Map<String, Object> p : gen.pages) {
                            plan.append("- ").append(p.get("name")).append("\n");
                        }
                    } else if (gen.suggestedPages != null) {
                        plan.append("**Suggested Pages:**\n");
                        for (String p : gen.suggestedPages)
                            plan.append("- ").append(p).append("\n");
                    }

                    plan.append(
                            "\n**Look good?** Say **'Yes'** or **'Create it'** to proceed, or tell me what to change.");
                    gen.payload.put(PAYLOAD_REPLY, plan.toString());
                }

                // postProcessAndPersistIfNeeded(gen, request); // DISABLED for review step
                return gen;
            }

            if (ACTION_LIST_APPS.equals(normalizedAction)) {
                GenerationResult list = buildAppsListResult();
                // annotate context hints
                attachContextHints(list, null);
                return list;
            }
            if (normalizedAction != null) {
                GenerationResult actionResult = handleStructuredAction(normalizedAction, request);
                if (actionResult != null) {
                    attachContextHints(actionResult, normalizedAction);
                    return actionResult;
                }
            }
            GenerationResult generated = runGenerationPipelines(request);

            // FIX: Do NOT persist immediately in the fallback path. Stage for review!
            // This mirrors the Semantic Router logic and enables the confirmation loop.
            ctx.pendingResult = generated;

            // Add "Ready?" prompt if not present
            if (generated.payload == null)
                generated.payload = new HashMap<>();
            String reply = String.valueOf(generated.payload.get(PAYLOAD_REPLY));
            if (reply == null || "null".equals(reply) || (!reply.contains("Create it") && !reply.contains("Yes"))) {
                if (reply == null || "null".equals(reply))
                    reply = "";
                // Only add prompt if it's a plan/proposal
                if (generated.appName != null) {
                    reply += "\n\n**Ready?** Say **'Yes'** or **'Create it'** to proceed, or tell me what to change.";
                }
                generated.payload.put(PAYLOAD_REPLY, reply);
            }

            // Explicit Draft Mode Flag for Frontend UI (triggers "Create App" button)
            generated.payload.put("showConfirmation", true);

            // postProcessAndPersistIfNeeded(generated, request); // REMOVED: Must wait for
            // confirmation
            attachContextHints(generated, ACTION_GENERATE_APP);
            return generated;
        } catch (

        Exception ex) {
            GenerationResult err = new GenerationResult();
            err.success = false;
            err.error = ex.getMessage();
            err.payload = new HashMap<>();
            err.payload.put(PAYLOAD_REPLY, "Something went wrong generating your app: " + ex.getMessage());
            LOG.error("[AI] Generation failed", ex);
            return err;
        }
    }

    /**
     * Convert ConversationContext to Map for MetadataIntelligenceEngine
     */

    /**
     * Handle intent classified by metadata engine
     */

    private static GenerationResult buildSmallTalkResult(String reply, String userId, String input) {
        GenerationResult result = new GenerationResult();
        result.success = true;
        result.payload = new HashMap<>();
        result.payload.put(PAYLOAD_SMALL_TALK, true);
        result.payload.put(PAYLOAD_REPLY, reply);
        AgentMemoryService.record(userId, input, reply);
        return result;
    }

    // Adds conversational context hints for frontend to update memory
    private static void attachContextHints(GenerationResult result, String action) {
        if (result == null || !result.success)
            return;
        if (result.payload == null)
            result.payload = new HashMap<>();
        if (ACTION_LIST_APPS.equals(action) && result.payload.get(PAYLOAD_APPS) instanceof List) {
            result.payload.put("lastAppList", result.payload.get(PAYLOAD_APPS));
        }
        if (ACTION_LOAD_APP.equals(action) && result.payload.get("app") instanceof Map) {
            Object appObj = result.payload.get("app");
            if (appObj instanceof Map) {
                Object id = ((Map<?, ?>) appObj).get("id");
                Object name = ((Map<?, ?>) appObj).get("name");
                if (id != null)
                    result.payload.put("currentAppId", id);
                if (name != null)
                    result.payload.put("currentAppName", name);
            }
        }
        if (ACTION_GENERATE_APP.equals(action) && result.appName != null && result.payload.get("appId") != null) {
            result.payload.put("currentAppId", result.payload.get("appId"));
            result.payload.put("currentAppName", result.appName);
        }
    }

    private static String resolveAction(GenerationRequest request) {
        if (request == null) {
            return null;
        }

        if (request.action != null && !request.action.isBlank()) {
            request.action = normalizeActionLabel(request.action);
            return request.action;
        }

        if (request.description == null || request.description.isBlank()) {
            return null;
        }

        // Try rule-based classification first (no GPT needed)
        Map<String, Object> ruleBasedResult = classifyWithRules(request.description);
        if (ruleBasedResult != null) {
            LOG.info("[AI] Rule-based classification succeeded for: {}", request.description);
            Object classifiedAction = ruleBasedResult.get("action");
            if (classifiedAction != null) {
                request.action = normalizeActionLabel(String.valueOf(classifiedAction));
            }
            if ((request.options == null || request.options.isEmpty()) && ruleBasedResult.get("options") != null) {
                request.options = MAPPER.convertValue(ruleBasedResult.get("options"), MAP_TYPE);
            }
            return request.action;
        }

        // Try intent cache
        IntentCache.ActionDescriptor cached = IntentCache.get(request.description);
        if (cached != null) {
            LOG.info("[AI] Intent cache hit for: {}", request.description);
            request.action = normalizeActionLabel(cached.action);
            if (request.options == null || request.options.isEmpty()) {
                request.options = cached.options;
            }
            return request.action;
        }

        // Fall back to GPT classification
        LOG.info("[AI] Using GPT classification for: {}", request.description);
        Map<String, Object> classification = classifyActionSafe(request.description);
        if (classification == null) {
            return null;
        }

        Object classifiedAction = classification.get("action");
        if (classifiedAction != null) {
            request.action = normalizeActionLabel(String.valueOf(classifiedAction));
        }

        if ((request.options == null || request.options.isEmpty()) && classification.get("options") != null) {
            request.options = MAPPER.convertValue(classification.get("options"), MAP_TYPE);
        }

        // Store in cache for future use
        if (request.action != null) {
            IntentCache.ActionDescriptor descriptor = new IntentCache.ActionDescriptor(request.action);
            descriptor.options = request.options != null ? request.options : new HashMap<>();
            IntentCache.put(request.description, descriptor);
            LOG.info("[AI] Stored GPT classification in cache: {} -> {}", request.description, request.action);
        }

        return request.action;
    }

    /**
     * Rule-based classification - handles common commands without GPT
     * Returns Map with "action" and optional "options" keys, or null if no match
     */
    private static Map<String, Object> classifyWithRules(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }

        String lower = description.toLowerCase(Locale.ROOT).trim();
        Map<String, Object> result = new HashMap<>();

        // List apps commands
        if (lower.matches("^(show|list|display|get|view|see) (my |all )?apps?$")
                || lower.equals("apps")
                || lower.equals("my apps")
                || lower.equals("what apps do i have")
                || lower.equals("show me my apps")
                || lower.contains("list all apps")
                || lower.contains("show all apps")) {
            result.put("action", ACTION_LIST_APPS);
            return result;
        }

        // Show fields commands
        if (lower.matches("^(show|list|describe|what are the) fields? (of|for|in) \\w+$")
                || lower.contains("show fields")
                || lower.contains("list fields")
                || lower.contains("what fields does")
                || lower.contains("fields in")) {
            result.put("action", "listFields");
            return result;
        }

        // Help commands
        if (lower.matches("^(help|what can you do|capabilities|commands)$")
                || lower.equals("?")
                || lower.contains("what can i")
                || lower.contains("how do i")
                || lower.contains("show me help")) {
            result.put("action", "help");
            return result;
        }

        return null; // No rule match
    }

    private static Map<String, Object> classifyActionSafe(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        try {
            return classifyAction(description);
        } catch (Exception e) {
            LOG.warn("[AI] Action classification failed: {}", e.getMessage());
            return null;
        }
    }

    private static GenerationResult handleSmallTalkIfNeeded(GenerationRequest request, String normalizedAction) {
        // Direct small talk handling without regex checks, as Router handles intent
        // routing
        String userId = resolveUserId(request);
        String reply = SmallTalkEngine.getSmallTalkResponse(request.description, userId);

        if (reply == null) {
            return null;
        }

        GenerationResult result = new GenerationResult();
        result.success = true;
        result.payload = new HashMap<>();
        result.payload.put(PAYLOAD_SMALL_TALK, true);
        result.payload.put(PAYLOAD_REPLY, reply);
        LOG.info("[AI] Small talk detected, responding: {}", reply);
        AgentMemoryService.record(userId, request.description, reply);
        return result;
    }

    private static GenerationResult runGenerationPipelines(GenerationRequest request) {
        AppConfig config = getConfig();
        if (AiProviderFactory.isAiEnabled(config)) {
            try {
                LOG.info("[AI] Attempting AI generation with provider: {}", config.getAiProvider());
                GenerationResult aiResult = generateWithAi(request, config, null);

                // First attempt validation
                String validationErrors = AiResultValidator.getValidationErrors(aiResult, request);
                if (validationErrors == null) {
                    LOG.info("[AI] ✓ AI result validated successfully on first attempt");
                    return aiResult;
                }

                // First attempt failed - try self-correction
                LOG.warn("[AI] ⚠ First attempt validation failed: {}", validationErrors);
                LOG.info("[AI] Attempting self-correction with error feedback...");

                GenerationResult correctedResult = generateWithAi(request, config, validationErrors);
                String retryValidation = AiResultValidator.getValidationErrors(correctedResult, request);

                if (retryValidation == null) {
                    LOG.info("[AI] ✓ Self-correction successful! Validation passed on retry.");
                    return correctedResult;
                }

                LOG.warn("[AI] ✗ Self-correction failed: {}", retryValidation);
                LOG.warn("[AI] Falling back to templates after 2 attempts");

            } catch (Exception e) {
                LOG.error("[AI] AI generation failed with exception", e);
            }
        } else {
            LOG.warn("[AI] AI provider not enabled, will use template-based generation");
        }
        LOG.info("[AI] Using template-based generation as fallback");
        return generateFromTemplates(request);
    }

    private static GenerationResult generateWithAi(GenerationRequest request, AppConfig config, String previousErrors)
            throws Exception {
        AiProvider provider = AiProviderFactory.createProvider(config);
        String systemPrompt = AiSystemPrompts.getAppGenerationPrompt();

        // Inject conversation context (and app schema if modifying) into system prompt
        String contextPrompt = buildContextPrompt(request);
        if (contextPrompt != null && !contextPrompt.isBlank()) {
            systemPrompt = contextPrompt + "\n\n" + systemPrompt;
            LOG.info("[AI Context] Injected conversation context into system prompt");
        }

        String userPrompt = request != null ? request.description : "";

        // If this is a retry with error feedback, append correction instructions
        if (previousErrors != null && !previousErrors.isBlank()) {
            userPrompt = userPrompt + "\n\n" +
                    "⚠️ IMPORTANT: Your previous response had validation errors:\n" +
                    previousErrors + "\n\n" +
                    "Please generate the app structure again, fixing these issues. " +
                    "Ensure all fields are properly formatted and match the schema requirements.";
            LOG.info("[AI] Retry attempt with error feedback ({} chars)", previousErrors.length());
        } else {
            LOG.info("[AI] First generation attempt");
        }

        LOG.info("[AI] Calling AI provider: {} with enhanced builder-database prompt", provider.getProviderName());
        String jsonResponse = provider.generateAppStructure(userPrompt, systemPrompt);
        LOG.info("[AI] Raw AI response: {}", jsonResponse);
        GenerationResult result = parseAiResponse(jsonResponse);
        LOG.info("[AI] Parsed GenerationResult: {}", result);
        return result;
    }

    private static GenerationResult handleListPages(GenerationRequest request) {
        GenerationResult pageResult = new GenerationResult();
        String appId = resolveAppIdForPages(request);
        if (appId == null || appId.isBlank()) {
            pageResult.success = false;
            pageResult.error = "appId or appName is required for listPages";
            pageResult.payload = new HashMap<>();
            pageResult.payload.put(PAYLOAD_REPLY,
                    "I need the app context to list pages. Try 'open the first app' then 'list pages'.");
            pageResult.payload.put(PAYLOAD_ACTION, ACTION_LIST_PAGES);
            LOG.warn("[AI] listPages missing appId/appName");
            return pageResult;
        }
        try {
            Map<String, Object> appWithPages = AppManager.getAppWithPages("default", appId);
            if (appWithPages == null) {
                pageResult.success = false;
                pageResult.error = "App not found: " + appId;
                pageResult.payload = new HashMap<>();
                pageResult.payload.put(PAYLOAD_REPLY, "App not found: " + appId);
                pageResult.payload.put(PAYLOAD_ACTION, ACTION_LIST_PAGES);
                LOG.warn("[AI] App not found: {}", appId);
            } else {
                List<?> pages = (List<?>) appWithPages.get("pages");
                pageResult.success = true;
                pageResult.payload = new HashMap<>();
                pageResult.payload.put("appId", appId);
                pageResult.payload.put("pageCount", pages != null ? pages.size() : 0);
                pageResult.payload.put("pages", pages);
                pageResult.payload.put(PAYLOAD_REPLY, "App has " + (pages != null ? pages.size() : 0) + " pages.");
                pageResult.payload.put(PAYLOAD_ACTION, ACTION_LIST_PAGES);
                LOG.info("[AI] App {} has {} pages", appId, pages != null ? pages.size() : 0);
            }
        } catch (Exception e) {
            pageResult.success = false;
            pageResult.error = "Failed to list pages: " + e.getMessage();
            pageResult.payload = new HashMap<>();
            pageResult.payload.put(PAYLOAD_REPLY, "Failed to list pages: " + e.getMessage());
            pageResult.payload.put(PAYLOAD_ACTION, ACTION_LIST_PAGES);
            LOG.error("[AI] Failed to list pages", e);
        }
        return pageResult;
    }

    private static String resolveAppIdForPages(GenerationRequest request) {
        if (request == null)
            return null;
        if (request.options != null) {
            Object appId = request.options.get("appId");
            if (appId != null && !String.valueOf(appId).isBlank())
                return String.valueOf(appId);

            // FIX: Check for currentAppId as well
            Object currentAppId = request.options.get("currentAppId");
            if (currentAppId != null && !String.valueOf(currentAppId).isBlank())
                return String.valueOf(currentAppId);

            Object appName = request.options.get("appName");
            if (appName != null) {
                List<Map<String, Object>> apps = safeListApps();
                for (Map<String, Object> app : apps) {
                    if (appName.toString().equalsIgnoreCase(String.valueOf(app.get("name")))) {
                        return String.valueOf(app.get("id"));
                    }
                }
            }
        }
        if (request.conversationContext != null && request.conversationContext.get("currentAppId") != null) {
            return String.valueOf(request.conversationContext.get("currentAppId"));
        }
        return null;
    }

    private static String resolveLoadAppId(GenerationRequest request) {
        if (request == null)
            return null;
        String desc = request.description != null ? request.description.toLowerCase(Locale.ROOT) : "";
        Integer indexFromText = extractOrdinalIndex(desc);
        if (indexFromText != null && request.conversationContext != null) {
            Object lastAppsObj = request.conversationContext.get("lastAppList");
            if (lastAppsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> lastApps = (List<Map<String, Object>>) lastAppsObj;
                int idx = indexFromText - 1;
                if (idx >= 0 && idx < lastApps.size()) {
                    Object id = lastApps.get(idx).get("id");
                    if (id != null) {
                        LOG.info("[AI] Resolved ordinal '{}' to app id '{}'", indexFromText, id);
                        return String.valueOf(id);
                    }
                }
            }
        }
        if (request.conversationContext != null) {
            Object lastAppsObj = request.conversationContext.get("lastAppList");
            if (lastAppsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> lastApps = (List<Map<String, Object>>) lastAppsObj;
                for (Map<String, Object> app : lastApps) {
                    Object nameObj = app.get("name");
                    if (nameObj != null && desc.contains(nameObj.toString().toLowerCase(Locale.ROOT))) {
                        Object id = app.get("id");
                        if (id != null) {
                            LOG.info("[AI] Resolved app by name '{}' to id '{}' from lastAppList", nameObj, id);
                            return String.valueOf(id);
                        }
                    }
                }
            }
        }
        if (request.options != null && request.options.get("appId") != null) {
            Object rawAppId = request.options.get("appId");
            String appIdStr = String.valueOf(rawAppId);
            String lowered = appIdStr.toLowerCase(Locale.ROOT);
            if (!lowered.contains(" ") && !lowered.contains("first") && !lowered.contains("second")
                    && !lowered.contains("third") && !lowered.contains("fourth") && !lowered.contains("fifth")
                    && !lowered.contains("app")) {
                LOG.info("[AI] Using appId from classifier: {}", appIdStr);
                return appIdStr;
            }
            LOG.info("[AI] Ignoring non-id appId from classifier: {}", appIdStr);
        }
        if (request.description != null && request.description.toLowerCase(Locale.ROOT).contains("first app")) {
            List<Map<String, Object>> apps = safeListApps();
            if (apps != null && !apps.isEmpty() && apps.get(0).get("id") != null) {
                String pickedId = String.valueOf(apps.get(0).get("id"));
                LOG.info("[AI] Auto-selected first app for load request: {}", pickedId);
                return pickedId;
            }
        }
        return null;
    }

    private static Integer extractOrdinalIndex(String text) {
        if (text == null)
            return null;
        if (text.contains("first"))
            return 1;
        if (text.contains("second"))
            return 2;
        if (text.contains("third"))
            return 3;
        if (text.contains("fourth"))
            return 4;
        if (text.contains("fifth"))
            return 5;
        Matcher m = Pattern.compile("\\b(\\d+)(st|nd|rd|th)?\\b").matcher(text);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private static List<Map<String, Object>> safeListApps() {
        try {
            return AppManager.listApps("default");
        } catch (Exception e) {
            LOG.error("[AI] Failed to list apps", e);
            return Collections.emptyList();
        }
    }

    private static GenerationResult buildAppsListResult() {
        GenerationResult listResult = new GenerationResult();
        listResult.success = true;
        List<Map<String, Object>> apps = safeListApps();
        listResult.payload = new HashMap<>();
        listResult.payload.put(PAYLOAD_APPS, apps);
        listResult.payload.put(PAYLOAD_ACTION, ACTION_LIST_APPS);
        if (apps.isEmpty()) {
            listResult.payload.put(PAYLOAD_REPLY,
                    "You don't have any apps yet. Describe an app you want to build, for example 'Create a project management app with projects and tasks'.");
        } else {
            listResult.payload.put(PAYLOAD_REPLY,
                    "Here are your apps. You can say 'open the second app' or 'delete the project management app'.");
        }
        LOG.info("[AI] Returning apps list: {}", apps);
        return listResult;
    }

    private static GenerationResult handleLoadApp(GenerationRequest request) {
        GenerationResult loadResult = new GenerationResult();
        String appId = resolveLoadAppId(request);
        if (appId == null || appId.isBlank()) {
            loadResult.success = false;
            loadResult.error = "Could not determine which app to load.";
            Map<String, Object> payload = new HashMap<>();
            payload.put(PAYLOAD_REPLY,
                    "I couldn't tell which app you meant. Try 'open the second app' or 'open Restaurant Management App'.");
            payload.put(PAYLOAD_ACTION, ACTION_LOAD_APP);
            loadResult.payload = payload;
            LOG.warn("[AI] loadApp missing resolvable appId");
            return loadResult;
        }
        try {
            Map<String, Object> appWithPages = AppManager.getAppWithPages("default", appId);
            if (appWithPages == null) {
                loadResult.success = false;
                loadResult.error = "App not found: " + appId;
                loadResult.payload = new HashMap<>();
                loadResult.payload.put(PAYLOAD_REPLY, "App not found: " + appId);
                loadResult.payload.put(PAYLOAD_ACTION, ACTION_LOAD_APP);
                LOG.warn("[AI] App not found: {}", appId);
            } else {
                loadResult.success = true;
                loadResult.payload = new HashMap<>();
                loadResult.payload.put("app", appWithPages.get("app"));
                loadResult.payload.put("pages", appWithPages.get("pages"));
                loadResult.payload.put(PAYLOAD_REPLY, "Opened app '" + appWithPages.getOrDefault("name", appId) + "'.");
                loadResult.payload.put(PAYLOAD_ACTION, ACTION_LOAD_APP);

                // Track opened app in context
                updateOpenedApp(request.userId, appId);

                LOG.info("[AI] Loaded app: {}", appId);
            }
        } catch (Exception e) {
            loadResult.success = false;
            loadResult.error = "Failed to load app: " + e.getMessage();
            loadResult.payload = new HashMap<>();
            loadResult.payload.put(PAYLOAD_REPLY, "Failed to load app: " + e.getMessage());
            loadResult.payload.put(PAYLOAD_ACTION, ACTION_LOAD_APP);
            LOG.error("[AI] Failed to load app", e);
        }
        return loadResult;
    }

    private static GenerationResult handleDeleteApp(GenerationRequest request) {
        GenerationResult result = new GenerationResult();
        String resolvedId = resolveDeleteAppId(request);
        if (resolvedId == null || resolvedId.isBlank()) {
            result.success = false;
            result.error = "Could not determine which app to delete.";
            result.payload = new HashMap<>();
            result.payload.put("deleted", false);
            result.payload.put(PAYLOAD_REPLY,
                    "I couldn't tell which app you want to delete. Try 'delete the second app' after listing or 'delete Restaurant Management App'.");
            result.payload.put(PAYLOAD_ACTION, ACTION_DELETE_APP);
            return result;
        }
        try {
            boolean deleted = AppManager.deleteApp("default", resolvedId);
            result.success = deleted;
            result.payload = new HashMap<>();
            result.payload.put("deleted", deleted);
            result.payload.put(PAYLOAD_ACTION, ACTION_DELETE_APP);
            if (deleted) {
                result.payload.put(PAYLOAD_REPLY,
                        "Deleted app '" + resolvedId + "'. You can say 'show my apps' to confirm.");
            } else {
                result.payload.put(PAYLOAD_REPLY, "App not found: " + resolvedId);
            }
            LOG.info("[AI] Delete attempt for app '{}': {}", resolvedId, deleted);
        } catch (Exception e) {
            result.success = false;
            result.error = "Failed to delete app: " + e.getMessage();
            result.payload = new HashMap<>();
            result.payload.put("deleted", false);
            result.payload.put(PAYLOAD_REPLY, "Failed to delete app: " + e.getMessage());
            result.payload.put(PAYLOAD_ACTION, ACTION_DELETE_APP);
            LOG.error("[AI] Failed to delete app", e);
        }
        return result;
    }

    // Resolve delete app id using conversation context similar to loadApp
    @SuppressWarnings("unchecked")
    private static String resolveDeleteAppId(GenerationRequest request) {
        if (request == null)
            return null;
        String desc = request.description != null ? request.description.toLowerCase(Locale.ROOT) : "";
        // 'this app'
        if (desc.contains("this app") && request.conversationContext != null
                && request.conversationContext.get("currentAppId") != null) {
            return String.valueOf(request.conversationContext.get("currentAppId"));
        }
        // ordinal resolution
        Integer ordinal = extractOrdinalIndex(desc);
        if (ordinal != null && request.conversationContext != null) {
            Object lastAppsObj = request.conversationContext.get("lastAppList");
            if (lastAppsObj instanceof List) {
                List<Map<String, Object>> lastApps = (List<Map<String, Object>>) lastAppsObj;
                int idx = ordinal - 1;
                if (idx >= 0 && idx < lastApps.size()) {
                    Object id = lastApps.get(idx).get("id");
                    if (id != null)
                        return String.valueOf(id);
                }
            }
        }
        // name resolution against lastAppList
        if (request.conversationContext != null) {
            Object lastAppsObj = request.conversationContext.get("lastAppList");
            if (lastAppsObj instanceof List) {
                List<Map<String, Object>> lastApps = (List<Map<String, Object>>) lastAppsObj;
                for (Map<String, Object> app : lastApps) {
                    Object nameObj = app.get("name");
                    Object idObj = app.get("id");
                    if (nameObj != null && idObj != null
                            && desc.contains(nameObj.toString().toLowerCase(Locale.ROOT))) {
                        return String.valueOf(idObj);
                    }
                }
            }
        }
        // explicit option appId
        if (request.options != null && request.options.get("appId") != null) {
            return String.valueOf(request.options.get("appId"));
        }
        return null;
    }

    private static GenerationResult handleStructuredAction(String action, GenerationRequest request) {
        switch (action) {
            case ACTION_LIST_APPS:
                return buildAppsListResult();
            case ACTION_LOAD_APP:
                return handleLoadApp(request);
            case ACTION_DELETE_APP:
                return handleDeleteApp(request);
            case ACTION_LIST_PAGES:
                return handleListPages(request);
            case "describeApp":
                return handleDescribeApp(request);
            case "listFields":
                return handleListFields(request);
            case "refactor_entity":
            case "add_relationship":
                // Allow fall-through to generation pipelines which will use the injected
                // context
                return null;
            case "help":
                return handleHelp();
            default:
                LOG.info("[AI] Unknown action '{}'", action);
                return null;
        }
    }

    private static GenerationResult handleDescribeApp(GenerationRequest request) {
        GenerationResult result = new GenerationResult();
        String appId = resolveAppIdForPages(request);

        if (appId == null || appId.isBlank()) {
            result.success = false;
            result.error = "No app context. Please open an app first.";
            result.payload = new HashMap<>();
            result.payload.put(PAYLOAD_REPLY, "I need to know which app to describe. Try 'open the first app' first.");
            result.payload.put(PAYLOAD_ACTION, "describeApp");
            return result;
        }

        try {
            AppMetadata app = AppManager.getApp("default", appId);
            if (app == null) {
                result.success = false;
                result.error = "App not found: " + appId;
                result.payload = new HashMap<>();
                result.payload.put(PAYLOAD_REPLY, "App not found: " + appId);
                result.payload.put(PAYLOAD_ACTION, "describeApp");
                return result;
            }

            StringBuilder description = new StringBuilder();
            description.append("**").append(app.getName()).append("**\n\n");
            description.append("**Description:** ")
                    .append(app.getDescription() != null ? app.getDescription() : "No description").append("\n\n");

            if (app.getEntities() != null && !app.getEntities().isEmpty()) {
                description.append("**Entities:** ").append(app.getEntities().size()).append("\n");
                for (Object entityObj : app.getEntities()) {
                    if (entityObj instanceof Map) {
                        Map<?, ?> entity = (Map<?, ?>) entityObj;
                        description.append("  - ").append(entity.get("name")).append("\n");
                    }
                }
            } else {
                description.append("**Entities:** None\n");
            }

            if (app.getPages() != null && !app.getPages().isEmpty()) {
                description.append("\n**Pages:** ").append(app.getPages().size()).append("\n");
                for (String pageId : app.getPages()) {
                    description.append("  - ").append(pageId).append("\n");
                }
            } else {
                description.append("\n**Pages:** None\n");
            }

            result.success = true;
            result.payload = new HashMap<>();
            result.payload.put("app", app);
            result.payload.put(PAYLOAD_REPLY, description.toString());
            result.payload.put(PAYLOAD_ACTION, "describeApp");
            LOG.info("[AI] Described app: {}", appId);

        } catch (Exception e) {
            result.success = false;
            result.error = "Failed to describe app: " + e.getMessage();
            result.payload = new HashMap<>();
            result.payload.put(PAYLOAD_REPLY, "Failed to describe app: " + e.getMessage());
            result.payload.put(PAYLOAD_ACTION, "describeApp");
            LOG.error("[AI] Failed to describe app", e);
        }

        return result;
    }

    private static GenerationResult handleListFields(GenerationRequest request) {
        GenerationResult result = new GenerationResult();
        result.success = false;
        result.error = "Entity field listing not yet implemented";
        result.payload = new HashMap<>();
        result.payload.put(PAYLOAD_REPLY,
                "Entity field listing is coming soon. For now, use 'describe app' to see all entities.");
        result.payload.put(PAYLOAD_ACTION, "listFields");
        return result;
    }

    private static GenerationResult handleHelp() {
        GenerationResult result = new GenerationResult();
        result.success = true;
        result.payload = new HashMap<>();

        StringBuilder help = new StringBuilder();
        help.append("**Available Commands:**\n\n");
        help.append("**App Management:**\n");
        help.append("  - 'show my apps' - List all apps\n");
        help.append("  - 'open the second app' - Open app by index\n");
        help.append("  - 'open Restaurant App' - Open app by name\n");
        help.append("  - 'delete this app' - Delete current app\n\n");
        help.append("**App Creation:**\n");
        help.append("  - 'create a blog app' - Generate new app\n");
        help.append("  - 'build a task manager' - Generate task app\n\n");
        help.append("**App Info:**\n");
        help.append("  - 'describe app' - Show app details\n");
        help.append("  - 'list pages' - Show all pages\n");
        help.append("  - 'show entities' - List entities\n\n");
        help.append("**Other:**\n");
        help.append("  - 'help' - Show this help\n");

        result.payload.put(PAYLOAD_REPLY, help.toString());
        result.payload.put(PAYLOAD_ACTION, "help");
        LOG.info("[AI] Displayed help");

        return result;
    }

    private static String resolveUserId(GenerationRequest request) {
        if (request == null)
            return DEFAULT_USER;
        if (request.options != null && request.options.get("userId") != null)
            return String.valueOf(request.options.get("userId"));
        if (request.conversationContext != null && request.conversationContext.get("userId") != null)
            return String.valueOf(request.conversationContext.get("userId"));
        if (request.userId != null && !request.userId.isBlank())
            return request.userId;
        return DEFAULT_USER;
    }

    private static void postProcessAndPersistIfNeeded(GenerationResult result, GenerationRequest request) {
        if (result == null || !result.success)
            return;

        // Persist App Structure
        if (result.appName != null && (result.entities != null && !result.entities.isEmpty())) {
            try {
                String baseName = result.appName;
                String slug;
                if (request.options != null && request.options.containsKey("currentAppId")) {
                    slug = (String) request.options.get("currentAppId");
                    LOG.info("[AI] Processing UPDATE for existing app '{}'", slug);
                } else {
                    slug = generateUniqueAppId(sanitizeAppId(baseName));
                }

                persistGeneratedApp(slug, result, request);
                if (result.payload == null)
                    result.payload = new HashMap<>();
                result.payload.put("appId", slug);

                // Persist Workflows if present
                if (result.workflows != null && !result.workflows.isEmpty()) {
                    saveWorkflows(slug, result.workflows);
                    LOG.info("[AI] Persisted {} workflows for app {}", result.workflows.size(), slug);
                }

                if (!result.payload.containsKey(PAYLOAD_REPLY)) {
                    int entityCount = result.entities != null ? result.entities.size() : 0;
                    int pageCount = result.pages != null ? result.pages.size()
                            : (result.suggestedPages != null ? result.suggestedPages.size() : 0);
                    int wfCount = result.workflows != null ? result.workflows.size() : 0;

                    String reply = "Created app '" + result.appName + "' with " + entityCount + " entities, "
                            + pageCount + " pages";
                    if (wfCount > 0) {
                        reply += ", and " + wfCount + " workflows ⚡";
                    }
                    reply += ". Say 'show my apps' or 'open the first app'.";

                    result.payload.put(PAYLOAD_REPLY, reply);
                }
            } catch (Exception e) {
                LOG.error("[AI] Failed to persist generated app", e);
                if (result.payload == null)
                    result.payload = new HashMap<>();
                result.payload.put(PAYLOAD_REPLY, "Generated app structure but failed to save: " + e.getMessage());
            }
        }
    }

    private static void saveWorkflows(String appId, List<WorkflowDefinition> workflows) {
        if (workflows == null || workflows.isEmpty())
            return;

        try {
            // Convert definitions to Maps
            List<Map<String, Object>> wfMaps = new ArrayList<>();
            for (WorkflowDefinition wf : workflows) {
                if (wf.getId() == null)
                    wf.setId(UUID.randomUUID().toString());
                if (wf.getStatus() == null)
                    wf.setStatus(WorkflowDefinition.WorkflowStatus.ACTIVE);

                // Ensure JSON definition is robust
                if (wf.getDefinitionJson() == null) {
                    wf.setDefinitionJson("{\"nodes\":{},\"transitions\":[]}");
                }

                Map<String, Object> wfMap = new HashMap<>();
                wfMap.put("id", wf.getId());
                wfMap.put("name", wf.getName());
                wfMap.put("description", wf.getDescription());
                wfMap.put("triggerEntity", wf.getTriggerEntity());
                wfMap.put("triggerEvent", wf.getTriggerEvent() != null ? wf.getTriggerEvent() : "MANUAL");
                wfMap.put("triggerCondition", wf.getTriggerCondition());
                wfMap.put("status", wf.getStatus().name());
                wfMap.put("definitionJson", wf.getDefinitionJson());
                wfMap.put("version", 1);

                wfMaps.add(wfMap);
            }

            // Wrap in container map
            Map<String, Object> container = new HashMap<>();
            container.put("workflows", wfMaps);

            // Persist via AppManager (to metadata table)
            AppManager.saveWorkflow("default", appId, container);

        } catch (Exception e) {
            LOG.error("Failed to save workflows for app " + appId, e);
            throw new RuntimeException("Workflow save failed", e);
        }
    }

    private static void persistGeneratedApp(String appId, GenerationResult result, GenerationRequest request)
            throws IOException {

        // Check if updating existing app
        com.appbana.model.AppMetadata existing = null;
        try {
            existing = AppManager.getApp("default", appId);
        } catch (Exception ignored) {
        }

        // Build AppMetadata
        com.appbana.model.AppMetadata meta = new com.appbana.model.AppMetadata();
        meta.setId(appId);
        meta.setName(result.appName);
        meta.setDescription(result.appDescription != null ? result.appDescription : "Generated application");
        meta.setVersion("1.0.0");

        // Preserve existing pages if updating
        if (existing != null && existing.getPages() != null) {
            meta.setPages(new ArrayList<>(existing.getPages()));
        } else {
            meta.setPages(new ArrayList<>());
        }

        // Convert entities (EntitySchema -> Map) for storage
        List<Object> entityMaps = new ArrayList<>();
        if (result.entities != null) {
            for (EntitySchema es : result.entities) {
                Map<String, Object> em = new LinkedHashMap<>();
                em.put("name", es.getName());
                List<Map<String, Object>> fields = new ArrayList<>();
                if (es.getFields() != null) {
                    for (EntitySchema.Field f : es.getFields()) {
                        Map<String, Object> fm = new LinkedHashMap<>();
                        fm.put("name", f.getName());
                        fm.put("type", f.getType());
                        fm.put("required", f.isRequired());
                        fm.put("primaryKey", f.isPrimaryKey());
                        fm.put("autoIncrement", f.isAutoIncrement());
                        fields.add(fm);
                    }
                }
                em.put("fields", fields);
                entityMaps.add(em);
            }
        }
        meta.setEntities(entityMaps);
        // Simple routes config
        com.appbana.model.AppMetadata.AppRoutes routes = new com.appbana.model.AppMetadata.AppRoutes();
        routes.setBasePath("/" + appId);
        meta.setRoutes(routes);

        if (existing != null) {
            AppManager.updateApp("default", appId, meta);
        } else {
            AppManager.createApp("default", meta);
        }

        // Persist pages
        if (result.pages != null && !result.pages.isEmpty()) {
            for (Map<String, Object> pg : result.pages) {
                Map<String, Object> normalized = ensurePageMeta(pg);
                // Auto-complete missing components for data-table pages
                autoCompletePageComponents(normalized, entityMaps);
                AppManager.savePage("default", appId, String.valueOf(normalized.get("id")), normalized);
            }
        } else if (result.suggestedPages != null && !result.suggestedPages.isEmpty()) {
            for (String suggested : result.suggestedPages) {
                Map<String, Object> scaffold = scaffoldPage(suggested);
                AppManager.savePage("default", appId, String.valueOf(scaffold.get("id")), scaffold);
            }
        } else {
            // Fallback single Home page
            Map<String, Object> scaffold = scaffoldPage("Home Page");
            AppManager.savePage("default", appId, String.valueOf(scaffold.get("id")), scaffold);
        }

        // Set defaultPage if any pages exist
        AppMetadata persisted = AppManager.getApp("default", appId);
        if (persisted != null && persisted.getPages() != null && !persisted.getPages().isEmpty()) {
            persisted.setDefaultPage(persisted.getPages().get(0));
            AppManager.updateApp("default", appId, persisted);
        }

        // Track created app in context
        updateCreatedApp(request.userId, appId);

        // Return currentAppName in payload for frontend
        if (result.payload == null) {
            result.payload = new HashMap<>();
        }
        result.payload.put("currentAppName", result.appName);

        // FIX: Force reload if updating existing app
        if (existing != null) {
            result.payload.put(PAYLOAD_ACTION, ACTION_LOAD_APP);
            result.payload.put("appId", appId); // Ensure appId is present for loadApp
            LOG.info("[AI] Added ACTION_LOAD_APP to payload to force UI refresh");
        }

        LOG.info("[AI] Persisted generated app '{}'", appId);
    }

    private static void ensureStructuralMinimum(GenerationResult gen, String appType, GenerationRequest request) {
        if (gen == null)
            return;
        if (gen.appName == null) {
            // derive appName from appType or description
            String derived = null;
            if (appType != null) {
                derived = Arrays.stream(appType.replace(" app", " ").trim().split("[ -]+"))
                        .filter(s -> !s.isBlank())
                        .map(s -> s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1))
                        .reduce("", (a, b) -> a + (a.isEmpty() ? "" : " ") + b) + " App";
            } else if (request != null && request.description != null) {
                // take first two words before 'app'
                Matcher m = Pattern.compile("(create|build|generate|make) (.+?) app")
                        .matcher(request.description.toLowerCase(Locale.ROOT));
                if (m.find()) {
                    String core = m.group(2).trim();
                    derived = Arrays.stream(core.split("[ -]+"))
                            .filter(s -> !s.isBlank())
                            .map(s -> s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1))
                            .reduce("", (a, b) -> a + (a.isEmpty() ? "" : " ") + b) + " App";
                }
            }
            gen.appName = derived != null ? derived : "Application";
        }
        if (gen.entities == null || gen.entities.isEmpty()) {
            gen.entities = new ArrayList<>();
            if (appType != null && appType.contains("running")) {
                gen.entities.add(buildEntity("RunSession", new String[][] {
                        { "id", "long", "pk", "auto" },
                        { "date", "date" },
                        { "distance_km", "decimal" },
                        { "duration_min", "int" },
                        { "pace", "string" },
                        { "notes", "string" }
                }));
                gen.entities.add(buildEntity("TrainingPlan", new String[][] {
                        { "id", "long", "pk", "auto" },
                        { "name", "string" },
                        { "goal_distance_km", "decimal" },
                        { "target_race", "string" },
                        { "weeks", "int" }
                }));
            } else if (appType != null && appType.contains("dance")) {
                gen.entities.add(buildEntity("DanceVideo", new String[][] {
                        { "id", "long", "pk", "auto" },
                        { "title", "string" },
                        { "style", "string" },
                        { "difficulty", "string" },
                        { "length_min", "int" }
                }));
                gen.entities.add(buildEntity("Event", new String[][] {
                        { "id", "long", "pk", "auto" },
                        { "name", "string" },
                        { "date", "date" },
                        { "location", "string" }
                }));
            } else {
                gen.entities.add(buildEntity("Item", new String[][] {
                        { "id", "long", "pk", "auto" },
                        { "name", "string" },
                        { "description", "string" }
                }));
            }
        }
        if ((gen.pages == null || gen.pages.isEmpty())
                && (gen.suggestedPages == null || gen.suggestedPages.isEmpty())) {
            gen.suggestedPages = Arrays.asList(
                    (appType != null && appType.contains("running")) ? "Run Sessions List (Data Table)"
                            : "Items List (Data Table)",
                    "Dashboard Page",
                    "Create Form Page");
        }
        if (gen.payload == null)
            gen.payload = new HashMap<>();
        if (!gen.payload.containsKey(PAYLOAD_REPLY)) {
            gen.payload.put(PAYLOAD_REPLY,
                    "Generated skeleton for " + gen.appName + ". I inferred " + gen.entities.size() + " entities and "
                            + (gen.pages != null ? gen.pages.size()
                                    : gen.suggestedPages != null ? gen.suggestedPages.size() : 0)
                            + " pages. Say 'show my apps' or 'open the first app'.");
        }
    }

    private static AppIntent parseIntent(String input) {
        AppIntent intent = new AppIntent();
        intent.originalInput = input;
        if (input == null) {
            intent.appType = "generic";
            intent.appName = "Application";
            return intent;
        }
        if (Pattern.compile("running|run tracker|runner|fitness|workout").matcher(input).find()) {
            intent.appType = "running";
            intent.appName = "Running Tracker";
        } else if (Pattern.compile("dance|choreo|choreography").matcher(input).find()) {
            intent.appType = "dance";
            intent.appName = "Dance Application";
        } else if (Pattern.compile("blog|post|article|comment").matcher(input).find()) {
            intent.appType = "blog";
            intent.appName = "Blog Application";
        } else if (Pattern.compile("task|todo|checklist").matcher(input).find()) {
            intent.appType = "task";
            intent.appName = "Task Manager";
        } else {
            intent.appType = "generic";
            intent.appName = "Application";
        }
        return intent;
    }

    private static GenerationResult generateRunningApp(AppIntent intent) {
        GenerationResult result = new GenerationResult();
        result.success = true;
        result.appName = intent.appName;
        result.appDescription = "Track runs, training plans, and performance metrics";
        result.entities = new ArrayList<>();
        EntitySchema session = new EntitySchema();
        session.setName("RunSession");
        session.setFields(Arrays.asList(
                createField("id", "long", true, true),
                createField("date", "date", false, false),
                createField("distance_km", "decimal", false, false),
                createField("duration_min", "int", false, false),
                createField("pace", "string", false, false),
                createField("notes", "string", false, false)));
        result.entities.add(session);
        EntitySchema plan = new EntitySchema();
        plan.setName("TrainingPlan");
        plan.setFields(Arrays.asList(
                createField("id", "long", true, true),
                createField("name", "string", false, false),
                createField("goal_distance_km", "decimal", false, false),
                createField("target_race", "string", false, false),
                createField("weeks", "int", false, false)));
        result.entities.add(plan);
        result.relationships = Arrays.asList("RunSession plan reference (future expansion)");
        result.suggestedPages = Arrays.asList("Run Sessions List (Data Table)", "Run Session Detail (Profile)",
                "Create Run Session (Form)");
        return result;
    }

    private static GenerationResult generateBlogApp(AppIntent intent) {
        GenerationResult result = new GenerationResult();
        result.success = true;
        result.appName = intent.appName;
        result.appDescription = "A blog application with posts and comments";
        result.entities = new ArrayList<>();

        EntitySchema post = new EntitySchema();
        post.setName("Post");
        post.setFields(Arrays.asList(
                createField("id", "long", true, true),
                createField("title", "string", false, false),
                createField("content", "string", false, false),
                createField("author", "string", false, false),
                createField("published_at", "date", false, false),
                createField("status", "string", false, false)));
        result.entities.add(post);

        EntitySchema comment = new EntitySchema();
        comment.setName("Comment");
        comment.setFields(Arrays.asList(
                createField("id", "long", true, true),
                createField("content", "string", false, false),
                createField("author", "string", false, false),
                createField("post_id", "long", false, false)));
        result.entities.add(comment);

        result.relationships = Arrays.asList(
                "Comment.post_id → Post.id (many-to-one, CASCADE DELETE)");

        result.suggestedPages = Arrays.asList(
                "Posts List (Data Table)",
                "Post Detail (Profile)",
                "Create Post (Form)");

        return result;
    }

    private static GenerationResult generateTaskApp(AppIntent intent) {
        GenerationResult result = new GenerationResult();
        result.success = true;
        result.appName = intent.appName;
        result.appDescription = "Task and todo management application";
        result.entities = new ArrayList<>();

        EntitySchema task = new EntitySchema();
        task.setName("Task");
        task.setFields(Arrays.asList(
                createField("id", "long", true, true),
                createField("title", "string", false, false),
                createField("description", "string", false, false),
                createField("status", "string", false, false),
                createField("priority", "string", false, false),
                createField("due_date", "date", false, false)));
        result.entities.add(task);

        result.suggestedPages = Arrays.asList(
                "Task List (Data Table)",
                "Task Detail (Profile)",
                "Create Task (Form)");

        return result;
    }

    private static GenerationResult generateEcommerceApp(AppIntent intent) {
        GenerationResult result = new GenerationResult();
        result.success = true;
        result.appName = intent.appName;
        result.appDescription = "Online store with product catalog";
        result.entities = new ArrayList<>();

        EntitySchema product = new EntitySchema();
        product.setName("Product");
        product.setFields(Arrays.asList(
                createField("id", "long", true, true),
                createField("name", "string", false, false),
                createField("description", "string", false, false),
                createField("price", "long", false, false),
                createField("stock", "int", false, false),
                createField("category", "string", false, false)));
        result.entities.add(product);

        result.suggestedPages = Arrays.asList(
                "Product Catalog (Data Table)",
                "Product Detail (Profile)");

        return result;
    }

    private static GenerationResult generateCrmApp(AppIntent intent) {
        GenerationResult result = new GenerationResult();
        result.success = true;
        result.appName = intent.appName;
        result.appDescription = "Customer relationship management system";
        result.entities = new ArrayList<>();

        EntitySchema contact = new EntitySchema();
        contact.setName("Contact");
        contact.setFields(Arrays.asList(
                createField("id", "long", true, true),
                createField("first_name", "string", false, false),
                createField("last_name", "string", false, false),
                createField("email", "string", false, false),
                createField("phone", "string", false, false),
                createField("company", "string", false, false),
                createField("status", "string", false, false)));
        result.entities.add(contact);

        result.suggestedPages = Arrays.asList(
                "Contacts List (Data Table)",
                "Contact Detail (Profile)");

        return result;
    }

    private static GenerationResult generateGenericApp(AppIntent intent) {
        GenerationResult result = new GenerationResult();
        result.success = true;
        result.appName = intent.appName != null ? intent.appName : "New Application";
        result.appDescription = "Custom application";
        result.entities = new ArrayList<>();
        result.suggestedPages = new ArrayList<>();
        result.payload = new HashMap<>();
        result.payload.put(PAYLOAD_REPLY, "I've created a basic starting point for your " + result.appName
                + "! 🚀 You can now add entities and pages to customize it further.");
        return result;
    }

    private static EntitySchema.Field createField(String name, String type, boolean isPrimaryKey,
            boolean isAutoIncrement) {
        EntitySchema.Field field = new EntitySchema.Field();
        field.setName(name);
        field.setType(type);
        field.setPrimaryKey(isPrimaryKey);
        field.setAutoIncrement(isAutoIncrement);
        field.setRequired(!isPrimaryKey);
        return field;
    }

    private static AppConfig getConfig() {
        return ConfigManager.getConfig();
    }

    // --- Restored utility + classifier methods (re-added) ---
    private static String normalizeActionLabel(String action) {
        if (action == null)
            return null;
        switch (action.trim().toLowerCase(Locale.ROOT)) {
            case "list":
            case "listapps":
            case "list_apps":
            case "list-apps":
            case "showapps":
            case "show_apps":
            case "show-apps":
            case "show my apps":
            case "list my apps":
            case "list all apps":
            case "show all apps":
                return ACTION_LIST_APPS;
            case "load":
            case "open":
            case "loadapp":
            case "load_app":
            case "load-app":
                return ACTION_LOAD_APP;
            case "delete":
            case "deleteapp":
            case "delete_app":
            case "delete-app":
                return ACTION_DELETE_APP;
            case "pages":
            case "listpages":
            case "list_pages":
            case "list-pages":
                return ACTION_LIST_PAGES;
            case "generate":
            case "generateapp":
            case "generate_app":
            case "generate-app":
                return ACTION_GENERATE_APP;
            default:
                return action;
        }
    }

    private static Map<String, Object> classifyAction(String userText) throws Exception {
        // Simple heuristic-only fallback (full AI classification may be added later)
        return heuristicClassification(userText);
    }

    private static Map<String, Object> heuristicClassification(String userText) {
        String lower = userText == null ? "" : userText.toLowerCase(Locale.ROOT);
        Map<String, Object> out = new HashMap<>();
        if (lower.matches(".*(list|show).*(apps|app list).*") || lower.contains("my apps")) {
            out.put("action", ACTION_LIST_APPS);
            out.put("options", new HashMap<>());
            return out;
        }

        if (lower.matches(".*(describe|explain|summary|overview).*(app|application|this).*") ||
                lower.equals("explain app") || lower.equals("describe app") ||
                lower.matches(".*(what|tell|talk).*(about).*(app|application|this|selected|current).*") ||
                lower.contains("app selected") ||
                lower.contains("current app") ||
                lower.contains("this app")) {
            out.put("action", ACTION_DESCRIBE_APP);
            out.put("options", new HashMap<>());
            return out;
        }

        if (lower.matches(".*(open|load).*(app).*")) {
            out.put("action", ACTION_LOAD_APP);
            out.put("options", new HashMap<>());
            return out;
        }
        if (lower.matches(".*delete.*app.*")) {
            out.put("action", ACTION_DELETE_APP);
            out.put("options", new HashMap<>());
            return out;
        }
        if (lower.matches(".*(list|show|how many|count|number of).*pages.*")) {
            Map<String, Object> opts = new HashMap<>();
            Matcher m = Pattern.compile("(in|for|of) ([A-Za-z0-9 _-]+)").matcher(lower);
            if (m.find())
                opts.put("appName", m.group(2).trim());
            out.put("action", ACTION_LIST_PAGES);
            out.put("options", opts);
            return out;
        }

        // Handle "open/show/go to [page name] page"
        if (lower.matches(".*(open|show|load|view|goto|display).*(page).*")) {
            Map<String, Object> opts = new HashMap<>();
            // Extract page name: "open Playlist List page" -> "Playlist List"
            // Regex strategies:
            // 1. "open [X] page"
            Matcher m1 = Pattern.compile("(open|show|load|view|goto|display) (?:the )?([A-Za-z0-9 _-]+?) page")
                    .matcher(lower);
            if (m1.find()) {
                opts.put("pageName", m1.group(2).trim());
            } else {
                // 2. Just capture everything between verb and "page"
                Matcher m2 = Pattern.compile("(?:open|show|load|view|goto|display) (?:the )?(.+?) page").matcher(lower);
                if (m2.find()) {
                    opts.put("pageName", m2.group(1).trim());
                }
            }
            out.put("action", ACTION_OPEN_PAGE);
            out.put("options", opts);
            return out;
        }

        out.put("action", ACTION_GENERATE_APP);
        out.put("options", new HashMap<>());
        return out;
    }

    public static String sanitizeAiJson(String raw) { // kept public for other components referencing it
        if (raw == null)
            return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty())
            return trimmed;
        int firstFence = trimmed.indexOf("```");
        int lastFence = trimmed.lastIndexOf("```");
        if (firstFence >= 0 && lastFence > firstFence) {
            String inside = trimmed.substring(firstFence + 3, lastFence).trim();
            if (inside.startsWith("json"))
                inside = inside.substring(4).trim();
            if (!inside.isEmpty())
                return inside;
        }
        int firstBrace = trimmed.indexOf('{');
        int firstBracket = trimmed.indexOf('[');
        int start;
        if (firstBrace == -1)
            start = firstBracket;
        else if (firstBracket == -1)
            start = firstBrace;
        else
            start = Math.min(firstBrace, firstBracket);
        if (start == -1)
            return trimmed;
        char open = trimmed.charAt(start);
        char close = open == '{' ? '}' : ']';
        int depth = 0;
        for (int i = start; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == open)
                depth++;
            else if (c == close) {
                depth--;
                if (depth == 0)
                    return trimmed.substring(start, i + 1);
            }
        }
        return trimmed.substring(start);
    }

    static GenerationResult parseAiResponse(String jsonResponse) throws Exception {
        // Minimal parser; expect structured JSON or fallback to generic app
        GenerationResult result = new GenerationResult();
        if (jsonResponse == null || jsonResponse.isBlank()) {
            result.success = true;
            result.appName = "Application";
            result.entities = new ArrayList<>();
            return result;
        }
        String sanitized = sanitizeAiJson(jsonResponse);
        JsonNode root = MAPPER.readTree(sanitized);
        result.success = true;
        result.appName = root.path("appName").asText(null);
        result.appDescription = root.path("appDescription").asText(null);

        // SUPPORT FOR CONVERSATIONAL REPLY
        if (root.has("reply")) {
            if (result.payload == null)
                result.payload = new HashMap<>();
            result.payload.put(PAYLOAD_REPLY, root.get("reply").asText());
        }

        // Entities
        result.entities = new ArrayList<>();
        JsonNode entitiesNode = root.get("entities");
        if (entitiesNode != null && entitiesNode.isArray()) {
            for (JsonNode eNode : entitiesNode) {
                String name = eNode.path("name").asText("Entity");
                List<EntitySchema.Field> fields = new ArrayList<>();
                JsonNode fieldsNode = eNode.get("fields");
                if (fieldsNode != null && fieldsNode.isArray()) {
                    for (JsonNode fNode : fieldsNode) {
                        EntitySchema.Field f = new EntitySchema.Field();
                        f.setName(fNode.path("name").asText("field"));
                        f.setType(fNode.path("type").asText("string"));
                        f.setRequired(fNode.path("required").asBoolean(false));
                        f.setPrimaryKey(fNode.path("primaryKey").asBoolean(false));
                        f.setAutoIncrement(fNode.path("autoIncrement").asBoolean(false));
                        fields.add(f);
                    }
                }
                result.entities.add(new EntitySchema(name, fields));
            }
        }
        // Pages - handle both detailed pages and simple suggested pages
        JsonNode detailedPagesNode = root.get("pages");
        if (detailedPagesNode != null && detailedPagesNode.isArray()) {
            // GPT returned detailed page definitions with metadata
            result.pages = new ArrayList<>();
            for (JsonNode pageNode : detailedPagesNode) {
                try {
                    Map<String, Object> pageMap = MAPPER.convertValue(pageNode, MAP_TYPE);
                    result.pages.add(pageMap);
                } catch (Exception e) {
                    LOG.warn("[AI] Failed to parse page node: {}", e.getMessage());
                }
            }
            LOG.info("[AI] Parsed {} detailed pages from AI response", result.pages.size());
        }

        // Suggested pages (fallback if no detailed pages)
        result.suggestedPages = new ArrayList<>();
        JsonNode suggestedPagesNode = root.get("suggestedPages");
        if (suggestedPagesNode != null && suggestedPagesNode.isArray()) {
            for (JsonNode pNode : suggestedPagesNode) {
                result.suggestedPages.add(pNode.asText());
            }
            LOG.info("[AI] Parsed {} suggested pages from AI response", result.suggestedPages.size());
            LOG.info("[AI] Parsed {} suggested pages from AI response", result.suggestedPages.size());
        }

        // Workflows
        JsonNode workflowsNode = root.get("workflows");
        if (workflowsNode != null && workflowsNode.isArray()) {
            result.workflows = new ArrayList<>();
            for (JsonNode wfNode : workflowsNode) {
                try {
                    WorkflowDefinition wf = new WorkflowDefinition();
                    wf.setId(wfNode.has("id") ? wfNode.get("id").asText() : UUID.randomUUID().toString());
                    wf.setName(wfNode.path("name").asText("Untitled Workflow"));
                    wf.setDescription(wfNode.path("description").asText(""));
                    wf.setTriggerEntity(wfNode.path("triggerEntity").asText());
                    wf.setTriggerEvent(wfNode.path("triggerEvent").asText("MANUAL"));
                    wf.setTriggerCondition(wfNode.path("triggerCondition").asText(""));

                    String statusStr = wfNode.path("status").asText("ACTIVE");
                    try {
                        wf.setStatus(WorkflowDefinition.WorkflowStatus.valueOf(statusStr));
                    } catch (Exception e) {
                        wf.setStatus(WorkflowDefinition.WorkflowStatus.ACTIVE);
                    }

                    // Serialize 'definition' object to string for storage
                    // Serialize 'definition' object to string for storage
                    JsonNode defNode = wfNode.get("definition");
                    boolean hasValidDef = defNode != null && defNode.has("nodes") && defNode.get("nodes").isArray()
                            && defNode.get("nodes").size() > 0;

                    if (hasValidDef) {
                        wf.setDefinitionJson(MAPPER.writeValueAsString(defNode));
                    } else {
                        // FIX: Generate a synthetic definition based on description so the "Explain"
                        // feature has content
                        wf.setDefinitionJson(generateSyntheticWorkflowDefinition(wf));
                    }

                    result.workflows.add(wf);
                } catch (Exception e) {
                    LOG.warn("[AI] Failed to parse workflow node", e);
                }
            }
            LOG.info("[AI] Parsed {} workflows from AI response", result.workflows.size());
        }

        return result;
    }

    private static Map<String, Object> findDomainTemplate(String domain) {
        if (domain == null)
            return null;
        List<Map<String, Object>> templates = loadDomainTemplates();
        for (Map<String, Object> t : templates) {
            Object d = t.get("domain");
            if (d != null && domain.equalsIgnoreCase(String.valueOf(d)))
                return t;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> loadDomainTemplates() {
        if (cachedDomainTemplates != null)
            return cachedDomainTemplates;
        try {
            java.nio.file.Path p = java.nio.file.Paths.get(DOMAIN_TEMPLATES_PATH);
            if (!java.nio.file.Files.exists(p)) {
                LOG.warn("[AI] Domain templates file not found: {}", p.toAbsolutePath());
                cachedDomainTemplates = List.of();
                return cachedDomainTemplates;
            }
            String json = java.nio.file.Files.readString(p);
            Map<String, Object> root = MAPPER.readValue(json, Map.class);
            Object arr = root.get("templates");
            if (arr instanceof List) {
                cachedDomainTemplates = (List<Map<String, Object>>) arr;
            } else {
                cachedDomainTemplates = List.of();
            }
        } catch (Exception e) {
            LOG.error("[AI] Failed to load domain templates", e);
            cachedDomainTemplates = List.of();
        }
        return cachedDomainTemplates;
    }

    private static GenerationResult generateFromTemplateDomain(String domain) {
        Map<String, Object> tpl = findDomainTemplate(domain);
        if (tpl == null)
            return null;
        GenerationResult result = new GenerationResult();
        result.success = true;
        result.appName = String.valueOf(tpl.getOrDefault("appName", domain + " App"));
        result.appDescription = String.valueOf(tpl.getOrDefault("description", result.appName));
        // entities
        result.entities = new ArrayList<>();
        Object entsObj = tpl.get("entities");
        if (entsObj instanceof List) {
            for (Object eo : (List<?>) entsObj) {
                if (!(eo instanceof Map))
                    continue;
                Map<String, Object> em = (Map<String, Object>) eo;
                EntitySchema schema = new EntitySchema();
                schema.setName(String.valueOf(em.getOrDefault("name", "Entity")));
                List<EntitySchema.Field> fields = new ArrayList<>();
                Object fieldsObj = em.get("fields");
                if (fieldsObj instanceof List) {
                    for (Object fo : (List<?>) fieldsObj) {
                        if (!(fo instanceof Map))
                            continue;
                        Map<String, Object> fm = (Map<String, Object>) fo;
                        EntitySchema.Field f = new EntitySchema.Field();
                        f.setName(String.valueOf(fm.getOrDefault("name", "field")));
                        f.setType(String.valueOf(fm.getOrDefault("type", "string")));
                        f.setPrimaryKey(Boolean.TRUE.equals(fm.get("primaryKey")));
                        f.setAutoIncrement(Boolean.TRUE.equals(fm.get("autoIncrement")));
                        f.setRequired(!Boolean.TRUE.equals(fm.get("primaryKey")));
                        fields.add(f);
                    }
                }
                schema.setFields(fields);
                result.entities.add(schema);
            }
        }
        // relationships
        result.relationships = new ArrayList<>();
        Object relObj = tpl.get("relationships");
        if (relObj instanceof List) {
            for (Object ro : (List<?>) relObj)
                result.relationships.add(String.valueOf(ro));
        }
        // suggested pages
        result.suggestedPages = new ArrayList<>();
        Object pagesObj = tpl.get("suggestedPages");
        if (pagesObj instanceof List) {
            for (Object po : (List<?>) pagesObj)
                result.suggestedPages.add(String.valueOf(po));
        }
        return result;
    }

    private static GenerationResult generateFromTemplates(GenerationRequest request) {
        String description = request != null && request.description != null ? request.description : "";
        AppIntent intent = parseIntent(description.toLowerCase(Locale.ROOT));
        // Try domain template first
        GenerationResult domainResult = generateFromTemplateDomain(intent.appType);
        if (domainResult != null)
            return domainResult;
        switch (intent.appType) {
            case "running":
                return generateRunningApp(intent); // fallback legacy
            case "dance":
                return generateDanceApp(intent);
            case "blog":
                return generateBlogApp(intent);
            case "task":
                return generateTaskApp(intent);
            case "ecommerce":
                return generateEcommerceApp(intent);
            case "crm":
                return generateCrmApp(intent);
            default:
                return generateGenericApp(intent);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GenerationRequest {
        public String description;
        public String userId;
        public Map<String, Object> options;
        public String action;
        public String normalizedAction; // Added for metadata engine compatibility
        public Map<String, Object> conversationContext;
        public String mode;
        public List<Map<String, String>> messages; // Captured from frontend
    }

    public static class GenerationResult {
        public boolean success;
        public boolean needsMoreInfo;
        public List<String> followUpQuestions;
        public String appName;
        public String appDescription;
        public List<EntitySchema> entities;
        public List<String> relationships;
        public List<String> suggestedPages;
        public List<Map<String, Object>> pages;
        public String error;
        public Map<String, Object> payload;

        public String appType; // added for app type extraction
        public List<WorkflowDefinition> workflows; // added for Conversation-to-Workflow

        @Override
        public String toString() {
            return "GenerationResult{" +
                    "success=" + success +
                    ", needsMoreInfo=" + needsMoreInfo +
                    ", appName='" + appName + '\'' +
                    ", payload=" + payload +
                    '}';
        }
    }

    private static class AppIntent {
        String originalInput;
        String appType;
        String appName;
        List<String> detectedEntities;
    }

    // Extracts app type from user description (e.g., 'running app', 'dance app')
    private static String extractAppType(String description) {
        if (description == null || description.isBlank())
            return null;
        String lower = description.toLowerCase(Locale.ROOT);
        Pattern p = Pattern.compile("(\\w+ app)");
        Matcher m = p.matcher(lower);
        if (m.find()) {
            return m.group(1);
        }
        // fallback: look for 'create a/an ... app'
        p = Pattern.compile("create (?:a|an) ([\\w\\s]+) app");
        m = p.matcher(lower);
        if (m.find()) {
            return m.group(1).trim() + " app";
        }
        return null;
    }

    private static GenerationResult generateDanceApp(AppIntent intent) {
        GenerationResult result = new GenerationResult();
        result.success = true;
        result.appName = intent.appName != null ? intent.appName : "Dance Application";
        result.appDescription = "Dance tutorial and event application";
        result.entities = new ArrayList<>();
        // Entities: DanceVideo, Event
        result.entities.add(buildEntity("DanceVideo", new String[][] {
                { "id", "long", "pk", "auto" },
                { "title", "string" },
                { "style", "string" },
                { "difficulty", "string" },
                { "length_min", "int" }
        }));
        result.entities.add(buildEntity("Event", new String[][] {
                { "id", "long", "pk", "auto" },
                { "name", "string" },
                { "date", "date" },
                { "location", "string" }
        }));
        result.suggestedPages = Arrays.asList(
                "Videos List (Data Table)",
                "Video Detail (Profile)",
                "Create Video (Form)");
        return result;
    }

    // Build an EntitySchema from 2D array field definitions
    private static EntitySchema buildEntity(String name, String[][] fieldDefs) {
        EntitySchema e = new EntitySchema();
        e.setName(name);
        List<EntitySchema.Field> fields = new ArrayList<>();
        for (String[] def : fieldDefs) {
            if (def.length < 2)
                continue;
            String fname = def[0];
            String ftype = def[1];
            boolean pk = Arrays.asList(def).contains("pk");
            boolean auto = Arrays.asList(def).contains("auto");
            EntitySchema.Field f = new EntitySchema.Field();
            f.setName(fname);
            f.setType(ftype);
            f.setPrimaryKey(pk);
            f.setAutoIncrement(auto);
            f.setRequired(!pk);
            fields.add(f);
        }
        e.setFields(fields);
        return e;
    }

    // Ensure page meta has required fields and a minimal node tree
    private static Map<String, Object> ensurePageMeta(Map<String, Object> page) {
        Map<String, Object> out = new LinkedHashMap<>(page);
        if (!out.containsKey("id"))
            out.put("id", "page-" + System.currentTimeMillis());
        if (!out.containsKey("name"))
            out.put("name", String.valueOf(out.get("id")));
        if (!out.containsKey("rootId"))
            out.put("rootId", "root-" + System.currentTimeMillis());
        if (!out.containsKey("metaVersion"))
            out.put("metaVersion", "1.0.0");
        if (!out.containsKey("type"))
            out.put("type", guessPageType(String.valueOf(out.get("name"))));

        if (!out.containsKey("nodes")) {
            String rootId = String.valueOf(out.get("rootId"));
            List<Map<String, Object>> nodes = new ArrayList<>();

            // Root Container
            Map<String, Object> root = new HashMap<>();
            root.put("id", rootId);
            root.put("type", "container");
            Map<String, Object> rootProps = new HashMap<>();
            rootProps.put("layout", "vertical");
            rootProps.put("gap", "lg");
            rootProps.put("padding", "xl");
            root.put("props", rootProps);
            List<String> rootChildren = new ArrayList<>();
            rootChildren.add("heading-" + rootId);
            root.put("children", rootChildren);
            nodes.add(root);

            // Heading
            Map<String, Object> heading = new HashMap<>();
            heading.put("id", "heading-" + rootId);
            heading.put("type", "text");
            Map<String, Object> headingProps = new HashMap<>();
            headingProps.put("content", out.get("name"));
            headingProps.put("tag", "h1");
            heading.put("props", headingProps);
            nodes.add(heading);

            out.put("nodes", nodes);
        } else {
            // If nodes exist, ensure they are mutable (deep copy or wrapping)
            Object nodesObj = out.get("nodes");
            if (nodesObj instanceof List) {
                List<Object> mutableNodes = new ArrayList<>();
                for (Object nodeObj : (List<?>) nodesObj) {
                    if (nodeObj instanceof Map) {
                        mutableNodes.add(new HashMap<>((Map<?, ?>) nodeObj));
                    } else {
                        mutableNodes.add(nodeObj);
                    }
                }
                out.put("nodes", mutableNodes);
            }
        }
        return out;
    }

    // Scaffold a page from a simple name
    private static Map<String, Object> scaffoldPage(String name) {
        String base = (name == null || name.isBlank()) ? "Page" : name.trim();
        String id = "page-" + System.currentTimeMillis();
        String rootId = "root-" + System.currentTimeMillis();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("id", rootId);
        root.put("type", "container");
        root.put("props", Map.of("layout", "vertical", "gap", "lg", "padding", "xl"));
        root.put("children", List.of("heading-" + rootId));
        Map<String, Object> heading = new LinkedHashMap<>();
        heading.put("id", "heading-" + rootId);
        heading.put("type", "text");
        heading.put("props", Map.of("content", base, "tag", "h1"));
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("id", id);
        page.put("name", base);
        page.put("rootId", rootId);
        page.put("nodes", List.of(root, heading));
        page.put("metaVersion", "1.0.0");
        page.put("type", guessPageType(base));
        return page;
    }

    // Sanitize appId slug
    private static String sanitizeAppId(String raw) {
        if (raw == null)
            return "app" + System.currentTimeMillis();
        String s = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("-+", "-");
        s = s.replaceAll("^-", "").replaceAll("-$", "");
        if (s.isBlank())
            s = "app" + System.currentTimeMillis();
        return s;
    }

    // Generate unique slug avoiding collisions
    private static String generateUniqueAppId(String base) {
        String candidate = base;
        int counter = 1;
        try {
            while (AppManager.getApp("default", candidate) != null) {
                candidate = base + "-" + counter++;
            }
        } catch (IOException ignored) {
        }
        return candidate;
    }

    // Helper to generate a plausible workflow definition from description (for
    // explanation purposes)
    private static String generateSyntheticWorkflowDefinition(WorkflowDefinition wf) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode root = MAPPER.createObjectNode();
            com.fasterxml.jackson.databind.node.ArrayNode nodes = root.putArray("nodes");
            String desc = wf.getDescription() != null ? wf.getDescription().toLowerCase() : "";

            // 1. Start Node
            com.fasterxml.jackson.databind.node.ObjectNode start = nodes.addObject();
            start.put("id", "start");
            start.put("type", "START");
            start.put("label", "Start");

            // 2. Main Action (heuristic based on type)
            com.fasterxml.jackson.databind.node.ObjectNode action = nodes.addObject();
            action.put("id", "main_action");
            if (desc.contains("approval") || desc.contains("review") || wf.getName().toLowerCase().contains("review")) {
                action.put("type", "USER_TASK");
                action.put("label", "Review Request");
            } else if (desc.contains("email") || desc.contains("notify")) {
                action.put("type", "NOTIFICATION");
                action.put("label", "Send Notification");
            } else {
                action.put("type", "SERVICE_TASK");
                action.put("label", "Process Action");
            }

            // 3. Decision (if approval)
            if (desc.contains("approval") || desc.contains("review") || wf.getName().toLowerCase().contains("review")) {
                com.fasterxml.jackson.databind.node.ObjectNode decision = nodes.addObject();
                decision.put("id", "decision");
                decision.put("type", "DECISION");
                decision.put("label", "Approved?");
            }

            // 4. End Node
            com.fasterxml.jackson.databind.node.ObjectNode end = nodes.addObject();
            end.put("id", "end");
            end.put("type", "END");
            end.put("label", "End");

            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            return "{\"nodes\":[]}";
        }
    }

    private static String guessPageType(String name) {
        if (name == null)
            return "blank";
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("dashboard"))
            return "dashboard";
        if (lower.contains("list") || lower.contains("table"))
            return "list";
        if (lower.contains("detail") || lower.contains("profile") || lower.contains("view"))
            return "detail";
        if (lower.contains("form") || lower.contains("create") || lower.contains("add") || lower.contains("new"))
            return "form";
        return "blank";
    }

    /**
     * Auto-complete missing components in page nodes based on page metadata.
     * Critical fix: AI often generates page metadata (type, entity, columns) but
     * forgets
     * to add the actual data component to the nodes array.
     */
    private static void autoCompletePageComponents(Map<String, Object> page, List<Object> entities) {
        String pageType = String.valueOf(page.get("type"));
        LOG.info("[AI] Processing page '{}' with type '{}'", page.get("name"), pageType);

        // Heuristic: If page name contains "Registration" or "Sign Up", treat as
        // form/registration
        // This fixes the issue where AI generates "Customer Registration" with generic
        // type (e.g. "content" or "blank")
        String pageNameLower = String.valueOf(page.get("name")).toLowerCase();
        if (pageNameLower.contains("registration") || pageNameLower.contains("register")
                || pageNameLower.contains("sign up")) {
            if (!"registration".equals(pageType) && !"form".equals(pageType) && !"profile".equals(pageType)) {
                LOG.info("[AI] Page '{}' detected as Registration form override (original type: {})", page.get("name"),
                        pageType);
                pageType = "registration";
                page.put("type", "registration"); // Update metadata
            }
        }

        if ("dashboard".equals(pageType)) {
            autoCompleteDashboard(page);
            return;
        }

        if ("board".equals(pageType)) {
            // Fallback for board: treat as table for now until Kanban component exists
            // We need to fetch the entity name to build the table
            String entityName = String.valueOf(page.get("entity"));
            if (entityName == null || "null".equals(entityName) || entityName.isEmpty()) {
                entityName = inferEntityFromPageName(String.valueOf(page.get("name")), entities);
                if (entityName != null)
                    page.put("entity", entityName);
            }
            if (entityName != null && !"null".equals(entityName) && !entityName.isEmpty()) {
                autoCompleteTable(page, entities, entityName);
            }
            return;
        }

        // Only process data-table/list pages
        boolean isTable = "data-table".equals(pageType) || "list".equals(pageType);
        boolean isForm = "form".equals(pageType) || "profile".equals(pageType) || "create-form".equals(pageType)
                || "registration".equals(pageType);

        if (!isTable && !isForm) {
            LOG.warn("[AI] Page '{}' skipped: type '{}' is not auto-completable", page.get("name"), pageType);
            return;
        }

        // FIX: Validate rootId/nodes consistency and deduplicate BEFORE processing
        // Logic: if rootId points to X, but nodes list has root Y, we fix it.
        validateAndFixRootId(page);

        String entityName = String.valueOf(page.get("entity"));

        // Try to infer entity from page name if not explicitly set
        if (entityName == null || "null".equals(entityName) || entityName.isEmpty()) {
            entityName = inferEntityFromPageName(String.valueOf(page.get("name")), entities);
            if (entityName != null) {
                page.put("entity", entityName); // Add entity to metadata
                LOG.info("[AI] Inferred entity '{}' from page name '{}'", entityName, page.get("name"));
            }
        }

        // If still no entity, can't auto-complete
        if (entityName == null || "null".equals(entityName) || entityName.isEmpty()) {
            return;
        }

        if (isTable) {
            autoCompleteTable(page, entities, entityName);
        } else if (isForm) {
            autoCompleteForm(page, entities, entityName);
        }
    }

    private static void autoCompleteForm(Map<String, Object> page, List<Object> entities, String entityName) {
        Object nodesObj = page.get("nodes");
        List<Object> nodes;

        if (nodesObj instanceof List) {
            nodes = new ArrayList<>((List<?>) nodesObj);
        } else {
            nodes = new ArrayList<>();
        }
        page.put("nodes", nodes);

        // Check if form already exists
        boolean hasForm = false;
        for (Object n : nodes) {
            if (n instanceof Map) {
                Map<?, ?> m = (Map<?, ?>) n;
                if ("form".equals(m.get("type")) || "studio-form-live".equals(m.get("type"))) {
                    hasForm = true;
                    break;
                }
            }
        }
        if (hasForm) {
            LOG.info("[AI] Page '{}' already has a form. Skipping auto-completion.", page.get("name"));
            return;
        }

        LOG.info("[AI] Auto-completing missing form component for page '{}'", page.get("name"));

        Map<String, Object> entityMap = findEntityByName(entities, entityName);
        if (entityMap == null) {
            LOG.warn("[AI] Cannot auto-complete form: entity '{}' not found", entityName);
            return;
        }

        List<Map<String, Object>> fields = buildFormFields(entityMap);
        String formId = "form-" + page.get("rootId");

        Map<String, Object> formNode = new LinkedHashMap<>();
        formNode.put("id", formId);
        formNode.put("type", "form");

        Map<String, Object> formProps = new LinkedHashMap<>();
        formProps.put("entity", entityName);
        formProps.put("fields", fields);
        formProps.put("layout", "vertical");
        formProps.put("submitLabel", "Save");
        // FIX: Ensure button is explicitly part of form structure if needed by
        // frontend,
        // though "submitLabel" should trigger it in the Form component.
        // Also ensure form has a title if missing.
        if (!hasHeader(nodes)) {
            formProps.put("title", capitalizeWords(entityName));
        }
        formNode.put("props", formProps);

        // FIX: Clear garbage nodes (text descriptors) if we are injecting a real form
        // AI often generates "text" nodes saying "email field", "password field".
        // We should remove these to avoid double rendering (Text + Form).
        // Keep "heading" or "title" nodes.
        nodes.removeIf(n -> {
            if (n instanceof Map) {
                Map<?, ?> m = (Map<?, ?>) n;
                String t = String.valueOf(m.get("type"));
                String id = String.valueOf(m.get("id"));
                // Remove generic text nodes or "container" nodes that aren't the root
                if ("text".equals(t) && !id.contains("header") && !id.contains("title"))
                    return true;
                if ("container".equals(t) && !id.contains("root"))
                    return true;
            }
            return false;
        });

        nodes.add(formNode);
        // Assuming flat nodes list or rootId logic from autoCompleteTable.
        // Usually we just add to nodes list if it's the structure used.
        // But autoCompleteTable calls addChildToRoot. Let's do the same if needed.
        if (page.containsKey("rootId")) {
            addChildToRoot(nodes, String.valueOf(page.get("rootId")), formId);
        }

        LOG.info("[AI] ✓ Auto-completed form component with {} fields", fields.size());
    }

    /**
     * Ensures page.rootId points to a valid node in nodes list.
     * Fixes "Root node not found" errors caused by ID mismatch or missing root.
     * Also deduplicates nodes.
     */
    private static void validateAndFixRootId(Map<String, Object> page) {
        Object nodesObj = page.get("nodes");
        if (!(nodesObj instanceof List)) {
            page.put("nodes", new ArrayList<>());
            nodesObj = page.get("nodes");
        }
        @SuppressWarnings("unchecked")
        List<Object> nodes = (List<Object>) nodesObj;

        // 1. Deduplicate by ID
        Map<String, Map<String, Object>> uniqueNodes = new LinkedHashMap<>();
        Iterator<Object> it = nodes.iterator();
        while (it.hasNext()) {
            Object o = it.next();
            if (o instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) o;
                String id = String.valueOf(m.get("id"));
                if (uniqueNodes.containsKey(id)) {
                    it.remove(); // Duplicate
                } else {
                    uniqueNodes.put(id, m);
                }
            } else {
                it.remove(); // Invalid node
            }
        }

        String declaredRoot = String.valueOf(page.get("rootId"));
        boolean rootExists = uniqueNodes.containsKey(declaredRoot);

        if (!rootExists) {
            LOG.warn("[AI] Declared rootId '{}' NOT found in nodes. Attempting fix...", declaredRoot);

            // Search for candidate
            String candidateId = null;
            for (String id : uniqueNodes.keySet()) {
                if (id.startsWith("root") || id.equals("root")) {
                    candidateId = id;
                    break;
                }
            }

            if (candidateId != null) {
                LOG.info("[AI] Found alternative root node '{}'. Updating metadata.", candidateId);
                page.put("rootId", candidateId);
            } else {
                // No root at all? Create one.
                LOG.warn("[AI] No root node found. Creating new root.");
                String newRootId = "root-" + System.currentTimeMillis();
                Map<String, Object> newRoot = new LinkedHashMap<>();
                newRoot.put("id", newRootId);
                newRoot.put("type", "container");
                // Add all existing top-level components as children?
                // Too complex for now, just set it as empty root.
                // Or try to wrap them? For safety, just empty root.
                // The autoComplete logic might add children later.
                nodes.add(newRoot);
                page.put("rootId", newRootId);
            }
        }
    }

    private static boolean hasHeader(List<Object> nodes) {
        if (nodes == null)
            return false;
        for (Object n : nodes) {
            if (n instanceof Map) {
                Map<?, ?> m = (Map<?, ?>) n;
                String type = String.valueOf(m.get("type"));
                if ("header".equals(type) || "heading".equals(type))
                    return true;
                // Also check if text node seems to be a title properties
                if ("text".equals(type)) {
                    String id = String.valueOf(m.get("id"));
                    if (id.contains("header") || id.contains("heading") || id.contains("title"))
                        return true;
                }
            }
        }
        return false;
    }

    private static List<Map<String, Object>> buildFormFields(Map<String, Object> entity) {
        List<Map<String, Object>> fields = new ArrayList<>();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entityFields = (List<Map<String, Object>>) entity.get("fields");
        if (entityFields != null) {
            for (Map<String, Object> field : entityFields) {
                String fieldName = String.valueOf(field.get("name"));
                String fieldType = String.valueOf(field.get("type"));
                // Skip IDs and system fields if needed, but usually we want them hidden or
                // read-only
                if ("id".equalsIgnoreCase(fieldName))
                    continue;

                Map<String, Object> formField = new LinkedHashMap<>();
                formField.put("name", fieldName);
                formField.put("label", capitalizeWords(fieldName));
                formField.put("type", mapFieldTypeForTable(fieldType)); // Reuse table mapping or simple mapping
                formField.put("required", field.get("required"));
                fields.add(formField);
            }
        }
        return fields;
    }

    private static void autoCompleteDashboard(Map<String, Object> page) {
        Object metricsObj = page.get("metrics");
        if (!(metricsObj instanceof List))
            return;

        List<?> metrics = (List<?>) metricsObj;
        if (metrics.isEmpty())
            return;

        Object nodesObj = page.get("nodes");
        List<Object> nodes;
        if (nodesObj instanceof List) {
            nodes = new ArrayList<>((List<?>) nodesObj);
        } else {
            nodes = new ArrayList<>();
        }
        page.put("nodes", nodes);

        // Check if visible components already exist
        boolean hasContent = false;
        for (Object n : nodes) {
            if (n instanceof Map) {
                Map<?, ?> m = (Map<?, ?>) n;
                if ("grid".equals(m.get("type")) || "card".equals(m.get("type"))) {
                    hasContent = true;
                    break;
                }
            }
        }
        if (hasContent)
            return;

        LOG.info("[AI] Scaffolding missing components for dashboard '{}'", page.get("name"));

        String rootId = String.valueOf(page.get("rootId"));
        String gridId = "grid-" + System.currentTimeMillis();

        // Create Grid
        Map<String, Object> gridNode = new LinkedHashMap<>();
        gridNode.put("id", gridId);
        gridNode.put("type", "grid");
        gridNode.put("props", Map.of("cols", 4, "gap", "1rem"));
        List<String> gridChildren = new ArrayList<>();
        gridNode.put("children", gridChildren);
        nodes.add(gridNode);

        // Create Cards for Metrics
        for (int i = 0; i < metrics.size(); i++) {
            Object mObj = metrics.get(i);
            if (!(mObj instanceof Map))
                continue; // Skip non-map metrics

            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) mObj;

            String metricName = String.valueOf(m.getOrDefault("name", "Metric"));
            String cardId = "stat-card-" + i + "-" + System.currentTimeMillis();
            String labelId = cardId + "-label";
            String valueId = cardId + "-value";

            // Card Container
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("id", cardId);
            card.put("type", "container");
            card.put("props", Map.of("padding", "lg", "style",
                    "background: #1e293b; border-radius: 8px; border: 1px solid #334155;"));
            card.put("children", List.of(labelId, valueId));
            nodes.add(card);
            gridChildren.add(cardId);

            // Label
            Map<String, Object> label = new LinkedHashMap<>();
            label.put("id", labelId);
            label.put("type", "text");
            label.put("props", Map.of("content", metricName, "style", "color: #94a3b8; font-size: 0.875rem;"));
            nodes.add(label);

            // Value (Placeholder)
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", valueId);
            value.put("type", "text");
            value.put("props", Map.of("content", "0", "style", "color: white; font-size: 2rem; font-weight: bold;"));
            nodes.add(value);
        }

        // Add grid to root children
        addChildToRoot(nodes, rootId, gridId);
    }

    private static void addChildToRoot(List<Object> nodes, String rootId, String childId) {
        for (Object nodeObj : nodes) {
            if (!(nodeObj instanceof Map))
                continue;

            @SuppressWarnings("unchecked")
            Map<String, Object> node = (Map<String, Object>) nodeObj;

            if (rootId.equals(node.get("id"))) {
                Object childrenObj = node.get("children");
                List<String> children;

                if (childrenObj instanceof List) {
                    // Create mutable copy
                    children = new ArrayList<>();
                    for (Object c : (List<?>) childrenObj) {
                        children.add(String.valueOf(c));
                    }
                } else {
                    children = new ArrayList<>();
                }

                if (!children.contains(childId)) {
                    children.add(childId);
                }
                node.put("children", children);
                break;
            }
        }
    }

    private static void autoCompleteTable(Map<String, Object> page, List<Object> entities, String entityName) {
        Object nodesObj = page.get("nodes");
        List<Object> nodes;

        if (nodesObj instanceof List) {
            nodes = new ArrayList<>((List<?>) nodesObj);
        } else {
            return;
        }
        page.put("nodes", nodes);

        boolean hasTable = false;
        for (Object n : nodes) {
            if (n instanceof Map) {
                Map<?, ?> m = (Map<?, ?>) n;
                if ("table".equals(m.get("type")) || "studio-table-live".equals(m.get("type"))) {
                    hasTable = true;
                    break;
                }
            }
        }
        if (hasTable)
            return;

        LOG.info("[AI] Auto-completing missing table component for page '{}' with entity '{}'", page.get("name"),
                entityName);

        Map<String, Object> entityMap = findEntityByName(entities, entityName);
        if (entityMap == null) {
            LOG.warn("[AI] Cannot auto-complete table: entity '{}' not found", entityName);
            return;
        }

        List<Map<String, Object>> fields = buildTableFields(entityMap);
        String tableId = "table-" + page.get("rootId");

        Map<String, Object> tableNode = new LinkedHashMap<>();
        tableNode.put("id", tableId);
        tableNode.put("type", "table");

        Map<String, Object> tableProps = new LinkedHashMap<>();
        tableProps.put("entity", entityName);
        tableProps.put("fields", fields);
        tableProps.put("pageSize", 25);
        tableProps.put("multiSelect", true);
        tableProps.put("actions", new ArrayList<>(Arrays.asList("view")));
        tableProps.put("bulkActions", new ArrayList<>(Arrays.asList("delete", "export")));
        tableProps.put("confirmDelete", true);
        tableProps.put("viewMode", "dynamic");
        tableProps.put("theme", "default");
        tableNode.put("props", tableProps);

        nodes.add(tableNode);
        addChildToRoot(nodes, String.valueOf(page.get("rootId")), tableId);

        LOG.info("[AI] ✓ Auto-completed table component with {} fields", fields.size());
    }

    /**
     * Infer entity name from page name (e.g., "Customer List" -> "Customer")
     */
    private static String inferEntityFromPageName(String pageName, List<Object> entities) {
        if (pageName == null || entities == null)
            return null;

        String normalized = pageName.toLowerCase()
                .replace("list", "")
                .replace("table", "")
                .replace("page", "")
                .trim();

        // Special mappings for User/Auth pages
        if (normalized.equals("registration") || normalized.equals("sign up") || normalized.equals("register")
                || normalized.equals("login") || normalized.equals("sign in") || normalized.equals("profile")) {
            return "User";
        }

        // Try exact match first
        for (Object entityObj : entities) {
            String entityName = getNameFromEntity(entityObj);
            if (entityName != null && normalized.equalsIgnoreCase(entityName)) {
                return entityName;
            }
        }

        // Try partial match (e.g., "customer" matches "Customer")
        for (Object entityObj : entities) {
            String entityName = getNameFromEntity(entityObj);
            if (entityName != null && (entityName.toLowerCase().contains(normalized) ||
                    normalized.contains(entityName.toLowerCase()))) {
                return entityName;
            }
        }

        return null;
    }

    /**
     * Find entity map by name from entities list
     */
    private static Map<String, Object> findEntityByName(List<Object> entities, String name) {
        if (entities == null || name == null)
            return null;

        for (Object entityObj : entities) {
            String entityName = getNameFromEntity(entityObj);
            if (name.equals(entityName)) {
                if (entityObj instanceof Map) {
                    return (Map<String, Object>) entityObj;
                } else if (entityObj instanceof com.appbana.model.EntitySchema) {
                    ObjectMapper m = new ObjectMapper();
                    return m.convertValue(entityObj, Map.class);
                }
            }
        }
        return null;
    }

    private static String getNameFromEntity(Object entityObj) {
        if (entityObj instanceof Map) {
            return String.valueOf(((Map<?, ?>) entityObj).get("name"));
        } else if (entityObj instanceof com.appbana.model.EntitySchema) {
            return ((com.appbana.model.EntitySchema) entityObj).getName();
        }
        return null;
    }

    /**
     * Build table fields array from entity definition
     */
    private static List<Map<String, Object>> buildTableFields(Map<String, Object> entity) {
        List<Map<String, Object>> fields = new ArrayList<>();

        // Always include id field first
        Map<String, Object> idField = new LinkedHashMap<>();
        idField.put("name", "id");
        idField.put("label", "ID");
        idField.put("type", "text");
        fields.add(idField);

        // Add entity fields
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entityFields = (List<Map<String, Object>>) entity.get("fields");
        if (entityFields != null) {
            for (Map<String, Object> field : entityFields) {
                String fieldName = String.valueOf(field.get("name"));
                String fieldType = String.valueOf(field.get("type"));

                Map<String, Object> tableField = new LinkedHashMap<>();
                tableField.put("name", fieldName);
                tableField.put("label", capitalizeWords(fieldName));
                tableField.put("type", mapFieldTypeForTable(fieldType));
                fields.add(tableField);
            }
        }

        return fields;
    }

    /**
     * Map entity field type to table display type
     */
    private static String mapFieldTypeForTable(String entityType) {
        if (entityType == null)
            return "text";

        switch (entityType.toLowerCase()) {
            case "datetime":
            case "createdat":
            case "updatedat":
                return "datetime";
            case "date":
                return "date";
            case "boolean":
                return "boolean";
            case "image":
                return "image";
            default:
                return "text";
        }
    }

    /**
     * Capitalize words for field labels (e.g., "firstName" -> "First Name")
     */
    private static String capitalizeWords(String input) {
        if (input == null || input.isEmpty())
            return input;

        // Handle camelCase: insert space before capitals
        String spaced = input.replaceAll("([a-z])([A-Z])", "$1 $2");

        // Capitalize first letter of each word
        String[] words = spaced.split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (result.length() > 0)
                result.append(" ");
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
            }
        }
        return result.toString();
    }

    // ========== Conversation Context Management ==========

    /**
     * Check if request is asking to regenerate/create pages for an app
     */

    /**
     * Handle request to regenerate pages for an app
     */
    private static GenerationResult handleRegeneratePagesRequest(GenerationRequest request) {
        GenerationResult result = new GenerationResult();
        result.payload = new HashMap<>();

        // Extract app ID from options (populated by SemanticRouter)
        String appId = request.options != null ? (String) request.options.get("targetAppId") : null;

        // If no app ID in description, use last opened app from context
        if (appId == null) {
            ConversationContext ctx = getContext(request.userId);
            appId = ctx.lastOpenedAppId;
        }

        if (appId == null) {
            result.success = false;
            result.payload.put(PAYLOAD_REPLY,
                    "I couldn't determine which app you're referring to. Please specify the app name or open the app first.");
            result.payload.put(PAYLOAD_ACTION, "regeneratePages");
            return result;
        }

        try {
            // Get app to check entities
            AppMetadata app = AppManager.getApp("default", appId);
            if (app == null) {
                result.success = false;
                result.payload.put(PAYLOAD_REPLY, "App not found: " + appId);
                return result;
            }

            // Check if app has entities but no pages
            if (app.getEntities() != null && !app.getEntities().isEmpty()) {
                List<String> entityNames = new ArrayList<>();
                for (Object entityObj : app.getEntities()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> entity = (Map<String, Object>) entityObj;
                    String entityName = String.valueOf(entity.get("name"));
                    entityNames.add(entityName);
                }

                // Generate list pages for each entity
                int createdCount = 0;
                for (String entityName : entityNames) {
                    String pageId = entityName.toLowerCase() + "-list";
                    String pageName = entityName + " List";

                    // Check if page already exists
                    try {
                        Map<String, Object> existing = AppManager.getPage("default", appId, pageId);
                        if (existing != null) {
                            LOG.info("[AI] Page {} already exists, skipping", pageId);
                            continue;
                        }
                    } catch (Exception ignored) {
                    }

                    // Create page metadata
                    Map<String, Object> page = scaffoldPage(pageName);
                    page.put("id", pageId);
                    page.put("type", "data-table");
                    page.put("entity", entityName);

                    // Auto-complete will add the table component
                    autoCompletePageComponents(page, app.getEntities());

                    // Save page
                    AppManager.savePage("default", appId, pageId, page);
                    createdCount++;

                    // Add page to app's pages list
                    if (app.getPages() == null) {
                        app.setPages(new ArrayList<>());
                    }
                    if (!app.getPages().contains(pageId)) {
                        app.getPages().add(pageId);
                    }
                }

                // Update app metadata
                if (createdCount > 0) {
                    AppManager.updateApp("default", appId, app);
                }

                result.success = true;
                result.payload.put(PAYLOAD_REPLY,
                        createdCount > 0
                                ? "Created " + createdCount + " page(s) for " + app.getName() + ". Refresh to see them!"
                                : "All pages already exist for " + app.getName() + ".");
                result.payload.put(PAYLOAD_ACTION, "regeneratePages");
                result.payload.put("createdCount", createdCount);

                LOG.info("[AI] Regenerated {} pages for app {}", createdCount, appId);

            } else {
                result.success = false;
                result.payload.put(PAYLOAD_REPLY, "Cannot create pages: app has no entities defined.");
            }

        } catch (Exception e) {
            LOG.error("[AI] Failed to regenerate pages", e);
            result.success = false;
            result.payload.put(PAYLOAD_REPLY, "Failed to create pages: " + e.getMessage());
        }

        return result;
    }

    // ========== Conversation Context Management ==========

    /**
     * Get or create conversation context for a user session
     */
    private static ConversationContext getContext(String userId) {
        String key = userId != null ? userId : DEFAULT_USER;
        ConversationContext ctx = sessionContexts.get(key);

        if (ctx == null || ctx.isExpired()) {
            ctx = new ConversationContext();
            sessionContexts.put(key, ctx);
            LOG.debug("[AI Context] Created new context for user: {}", key);
        } else {
            ctx.refresh();
        }

        return ctx;
    }

    /**
     * Update context when app type/description is discussed
     */
    private static void updateDiscussedApp(String userId, String appType, String description) {
        ConversationContext ctx = getContext(userId);
        ctx.lastDiscussedAppType = appType;
        ctx.lastDiscussedAppDescription = description;
        LOG.info("[AI Context] Updated discussed app: type='{}', desc='{}'", appType,
                description != null && description.length() > 50 ? description.substring(0, 50) + "..." : description);
    }

    /**
     * Update context when app is created
     */
    private static void updateCreatedApp(String userId, String appId) {
        ConversationContext ctx = getContext(userId);
        ctx.lastCreatedAppId = appId;
        LOG.info("[AI Context] Tracked created app: {}", appId);
    }

    /**
     * Update context when app is opened
     */
    private static void updateOpenedApp(String userId, String appId) {
        ConversationContext ctx = getContext(userId);
        ctx.lastOpenedAppId = appId;
        LOG.info("[AI Context] Tracked opened app: {}", appId);
    }

    private static String buildAppSchemaContext(com.appbana.model.AppMetadata app) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("📝 CURRENT APP CONTEXT (You are modifying this app):\n");
            sb.append("AppName: ").append(app.getName()).append("\n");
            sb.append("Description: ").append(app.getDescription() != null ? app.getDescription() : "").append("\n");

            sb.append("Existing Entities:\n");
            if (app.getEntities() != null) {
                for (Object entityObj : app.getEntities()) {
                    sb.append(MAPPER.writeValueAsString(entityObj)).append("\n");
                }
            }

            sb.append("Existing Pages (Definitions):\\n");
            if (app.getPages() != null && !app.getPages().isEmpty()) {
                // FIX: Load actual page metadata so AI knows what is on the page
                int pCount = 0;
                for (String pId : app.getPages()) {
                    if (pCount++ > 10) { // Limit to 10 pages to save tokens
                        sb.append("... (and ").append(app.getPages().size() - 10).append(" more pages)\\n");
                        break;
                    }
                    try {
                        java.util.Map<String, Object> pageData = AppManager.getPage("default", app.getId(), pId);
                        if (pageData != null) {
                            sb.append(MAPPER.writeValueAsString(pageData)).append("\\n");
                        } else {
                            sb.append("- ").append(pId).append(" (Metadata missing)\\n");
                        }
                    } catch (Exception e) {
                        sb.append("- ").append(pId).append(" (Error loading)\\n");
                    }
                }
            } else {
                sb.append("None\\n");
            }
            sb.append("\n\n");
            sb.append("\n\n");
            sb.append("🚨 MODIFICATION MODE - CRITICAL INSTRUCTIONS:\n");
            sb.append("1. The user wants to MODIFY this existing app. Do NOT create a new one.\n");
            sb.append("2. PRESERVE existing entities and fields unless explicitly asked to delete/change them.\n");
            sb.append("3. ADD the requested new features (entities, fields, pages) to the existing structure.\n");
            sb.append("4. Return the COMPLETE updated JSON structure (Merging existing + new).\n");
            sb.append("5. appName MUST match the Current App context ('").append(app.getName())
                    .append("'). Do NOT change it.");
            return sb.toString();
        } catch (Exception e) {
            LOG.warn("Failed to build app schema context", e);
            return "Current App: " + app.getName();
        }
    }

    /**
     * Build context prompt from conversation history to inject into system prompt
     */
    private static String buildContextPrompt(GenerationRequest request) {
        String userId = resolveUserId(request);
        ConversationContext ctx = getContext(userId);

        StringBuilder contextBuilder = new StringBuilder();

        // CRitICAL: If this is a modification request (refactor, add relationship),
        // inject the FULL schema
        boolean isModification = "refactor_entity".equals(request.action) ||
                "add_relationship".equals(request.action) ||
                "update_entity".equals(request.action) ||
                "update_plan".equals(request.action);

        String targetAppId = null;
        if (request.options != null && request.options.get("currentAppId") != null) {
            targetAppId = String.valueOf(request.options.get("currentAppId"));
        }
        if (targetAppId == null || "null".equals(targetAppId)) {
            targetAppId = ctx.lastOpenedAppId != null ? ctx.lastOpenedAppId : ctx.lastCreatedAppId;
        }

        if (isModification && targetAppId != null) {
            try {
                com.appbana.model.AppMetadata app = AppManager.getApp("default", targetAppId);
                if (app != null) {
                    String schemaContext = buildAppSchemaContext(app);
                    contextBuilder.append(schemaContext);
                    LOG.info("[AI Context] Injected FULL APP SCHEMA for modification: {}", targetAppId);
                }
            } catch (Exception e) {
                LOG.warn("[AI Context] Failed to inject app schema for modification", e);
            }
        }

        // FIX: Inject PENDING PLAN (Draft) if available.
        // This is critical for iterative refinement (e.g. "Add login") BEFORE the app
        // is persisted.
        if (ctx.pendingResult != null && ctx.pendingResult.appName != null) {
            contextBuilder.append("\n🚧 CURRENT DRAFT PLAN (Proposed but not saved):\n");
            contextBuilder.append("App Name: ").append(ctx.pendingResult.appName).append("\n");
            if (ctx.pendingResult.appDescription != null)
                contextBuilder.append("Description: ").append(ctx.pendingResult.appDescription).append("\n");

            if (ctx.pendingResult.entities != null && !ctx.pendingResult.entities.isEmpty()) {
                contextBuilder.append("Entities:\n");
                for (EntitySchema e : ctx.pendingResult.entities) {
                    contextBuilder.append("  - ").append(e.getName()).append(" (");
                    if (e.getFields() != null) {
                        for (int i = 0; i < e.getFields().size(); i++) {
                            contextBuilder.append(e.getFields().get(i).getName());
                            if (i < e.getFields().size() - 1)
                                contextBuilder.append(", ");
                        }
                    }
                    contextBuilder.append(")\n");
                }
            }
            if (ctx.pendingResult.pages != null && !ctx.pendingResult.pages.isEmpty()) {
                contextBuilder.append("Pages: ");
                for (Map<String, Object> p : ctx.pendingResult.pages) {
                    contextBuilder.append(p.get("name")).append(", ");
                }
                contextBuilder.append("\n");
            }
            contextBuilder
                    .append("\nINSTRUCTION: The user is modifying THIS draft. Apply changes to this structure.\n");
            LOG.info("[AI Context] Injected PENDING DRAFT PLAN for iterative refinement.");
        }

        contextBuilder.append("📝 CONVERSATION CONTEXT (for continuity):\n");

        if (ctx.lastDiscussedAppType != null) {
            contextBuilder.append("- User previously discussed: ").append(ctx.lastDiscussedAppType).append("\n");
        }

        if (ctx.lastDiscussedAppDescription != null && !ctx.lastDiscussedAppDescription.isBlank()) {
            String shortDesc = ctx.lastDiscussedAppDescription.length() > 100
                    ? ctx.lastDiscussedAppDescription.substring(0, 100) + "..."
                    : ctx.lastDiscussedAppDescription;
            contextBuilder.append("- Description: \"").append(shortDesc).append("\"\n");
        }

        if (!ctx.discussedEntities.isEmpty()) {
            contextBuilder.append("- Entities mentioned: ").append(String.join(", ", ctx.discussedEntities))
                    .append("\n");
        }

        if (ctx.lastCreatedAppId != null) {
            contextBuilder.append("- Last created app ID: ").append(ctx.lastCreatedAppId).append("\n");
        }

        if (ctx.lastOpenedAppId != null) {
            contextBuilder.append("- Currently opened app ID: ").append(ctx.lastOpenedAppId).append("\n");
        }

        contextBuilder.append(
                "\nUSE THIS CONTEXT: If user's request is vague or a continuation (e.g., 'create the app', 'add more entities'), ");
        contextBuilder.append("refer to the above context to understand what they're asking for.\n");

        // NEW: Append actual chat transcript if available
        if (request.messages != null && !request.messages.isEmpty()) {
            contextBuilder.append("\n📜 RECENT CHAT HISTORY:\n");
            // Take last 10 messages max to save tokens
            int start = Math.max(0, request.messages.size() - 10);
            for (int i = start; i < request.messages.size(); i++) {
                Map<String, String> msg = request.messages.get(i);
                String role = msg.getOrDefault("role", "unknown");
                String content = msg.getOrDefault("content", "").replaceAll("\n", " ");
                if (!content.isBlank()) {
                    contextBuilder.append(role.toUpperCase()).append(": ").append(content).append("\n");
                }
            }
            contextBuilder.append("--------------------------------------------------\n");
        }

        return contextBuilder.toString();
    }

    /**
     * Helper to detect confirmation phrases when AI doesn't explicitly flag
     * isApproval
     */
    private static boolean isConfirmationPhrase(String input) {
        if (input == null)
            return false;
        String normalized = input.trim().toLowerCase();

        // Relaxed length check to allow for "ok, looks good, please create it now"
        if (normalized.length() > 100)
            return false;

        // Check for specific confirmation phrases/patterns
        return normalized.equals("yes") ||
                normalized.startsWith("yes ") ||
                normalized.startsWith("yes,") ||
                normalized.equals("sure") ||
                normalized.equals("ok") ||
                normalized.contains("create it") ||
                normalized.contains("create the app") ||
                normalized.contains("build it") ||
                normalized.contains("build the app") ||
                normalized.contains("approve") ||
                normalized.contains("apply") ||
                normalized.contains("confirm") ||
                normalized.contains("proceed") ||
                normalized.contains("go ahead") ||
                normalized.contains("looks good") ||
                normalized.contains("make it") ||
                (normalized.contains("create") && normalized.contains("now"));
    }
}
