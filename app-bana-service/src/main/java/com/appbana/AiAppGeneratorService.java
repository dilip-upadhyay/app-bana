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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
// added for AI result validation

/**
 * AI-powered app generation service.
 * Resolves conversational intent into structured actions and falls back to
 * template-based generation.
 */
public class AiAppGeneratorService {

    private static final Logger LOG = LoggerFactory.getLogger(AiAppGeneratorService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
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
    private static final String DOMAIN_TEMPLATES_PATH = "builder-database/10-domain-templates.json";
    private static List<Map<String, Object>> cachedDomainTemplates;

    // Conversation context tracking for continuity across requests
    private static final Map<String, ConversationContext> sessionContexts = new HashMap<>();
    private static final long CONTEXT_TIMEOUT_MS = 10 * 60 * 1000; // 10 minutes

    /**
     * Conversation context to maintain state across multiple requests
     */
    private static class ConversationContext {
        String lastDiscussedAppType;
        String lastDiscussedAppDescription;
        List<String> discussedEntities;
        String lastCreatedAppId;
        String lastOpenedAppId;
        GenerationResult pendingResult; // Staged app generation waiting for approval
        long timestamp;

        ConversationContext() {
            this.discussedEntities = new ArrayList<>();
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CONTEXT_TIMEOUT_MS;
        }

        void refresh() {
            this.timestamp = System.currentTimeMillis();
        }
    }

    public static GenerationResult generateApp(GenerationRequest request) {
        // Initialize metadata intelligence engine
        MetadataIntelligenceEngine.initialize();

        LOG.info("[AI] Incoming GenerationRequest: action={}, description={}, options={}",
                request != null ? request.action : null,
                request != null ? request.description : null,
                request != null ? request.options : null);
        try {
            // FIX #1: Use contextual description if continuation request
            String effectiveDescription = buildContextualDescription(request);
            if (!effectiveDescription.equals(request.description)) {
                // Update request with context
                GenerationRequest contextualRequest = new GenerationRequest();
                contextualRequest.description = effectiveDescription;
                contextualRequest.userId = request.userId;
                contextualRequest.action = request.action;
                contextualRequest.options = request.options;
                contextualRequest.conversationContext = request.conversationContext;
                contextualRequest.mode = request.mode;
                request = contextualRequest;
            }

            GenerationResult earlySmallTalk = handleSmallTalkIfNeeded(request, null);
            if (earlySmallTalk != null) {
                return earlySmallTalk;
            }

            // NEW: Use metadata-driven intelligence for intent classification
            String userId = resolveUserId(request);
            ConversationContext ctx = getContext(userId);
            Map<String, Object> contextMap = convertContextToMap(ctx);

            MetadataIntelligenceEngine.IntentResult intentResult = MetadataIntelligenceEngine
                    .classifyIntent(request.description, contextMap);

            LOG.info("[AI] MetaAI classified as: {} (confidence: {:.2f})",
                    intentResult.intent, intentResult.confidence);

            // Handle metadata-classified intents
            GenerationResult metaResult = handleMetadataIntent(intentResult, request, ctx);
            if (metaResult != null) {
                return metaResult;
            }

            // Fallback to original flow for backward compatibility
            String normalizedAction = resolveAction(request);

            // SPECIAL CASE: Check for "explain/describe" in text if metadata missed it
            if (normalizedAction == null && (request.description.toLowerCase().contains("explain") ||
                    request.description.toLowerCase().contains("describe") ||
                    request.description.toLowerCase().contains("summary of"))) {
                GenerationResult desc = handleStructuredAction(ACTION_DESCRIBE_APP, request);
                if (desc != null && desc.success)
                    return desc;
            }

            // FIX #3: Handle "create pages" / "regenerate pages" requests
            if (isRegeneratePageRequest(request.description)) {
                return handleRegeneratePagesRequest(request);
            }

            GenerationResult smallTalk = handleSmallTalkIfNeeded(request, normalizedAction);
            if (smallTalk != null) {
                return smallTalk;
            }

            // Extract app type from description/context and track it
            // BUT: Don't overwrite context if this is a continuation request
            String appType = extractAppType(request != null ? request.description : null);
            if (appType != null && request.description != null && !isContinuationRequest(request)) {
                updateDiscussedApp(request.userId, appType, request.description);
            }

            // ALSO: If description mentions entities/features, store it even if no app type
            // extracted
            if (request.description != null && !isContinuationRequest(request) &&
                    (request.description.toLowerCase().contains("entity") ||
                            request.description.toLowerCase().contains("entities") ||
                            request.description.toLowerCase().matches(
                                    ".*\\b(customer|user|product|order|item|service|appointment|project|task)\\b.*"))) {
                String descAppType = appType != null ? appType : "application";
                updateDiscussedApp(request.userId, descAppType, request.description);
                LOG.info("[AI Context] Stored detailed app description in context");
            }

            // FIX: Check for confirmation of pending plan
            if (isContinuationRequest(request) && ctx.pendingResult != null) {
                LOG.info("[AI] User confirmed pending generation plan");
                GenerationResult pending = ctx.pendingResult;
                postProcessAndPersistIfNeeded(pending, request);
                ctx.pendingResult = null; // Clear after processing
                return pending;
            }

            if (isAppCreationRequest(
                    request != null ? request.description == null ? null : request.description.toLowerCase(Locale.ROOT)
                            : null)) {
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

                // Build Review Message
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

                plan.append("\n**Look good?** Say **'Yes'** or **'Create it'** to proceed, or tell me what to change.");

                if (gen.payload == null)
                    gen.payload = new HashMap<>();
                gen.payload.put(PAYLOAD_REPLY, plan.toString());

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
            postProcessAndPersistIfNeeded(generated, request);
            attachContextHints(generated, ACTION_GENERATE_APP);
            return generated;
        } catch (Exception ex) {
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
    private static Map<String, Object> convertContextToMap(ConversationContext ctx) {
        Map<String, Object> map = new HashMap<>();
        if (ctx == null)
            return map;

        map.put("lastDiscussedAppType", ctx.lastDiscussedAppType);
        map.put("lastDiscussedAppDescription", ctx.lastDiscussedAppDescription);
        map.put("discussed_entities", ctx.discussedEntities);
        map.put("lastCreatedAppId", ctx.lastCreatedAppId);
        map.put("lastOpenedAppId", ctx.lastOpenedAppId);

        return map;
    }

    /**
     * Handle intent classified by metadata engine
     */
    private static GenerationResult handleMetadataIntent(MetadataIntelligenceEngine.IntentResult intentResult,
            GenerationRequest request,
            ConversationContext ctx) {
        if (intentResult == null || intentResult.intent == null) {
            return null;
        }

        String intent = intentResult.intent;

        // Handle specific intents from metadata
        switch (intent) {
            case "describe_app":
                return handleStructuredAction(ACTION_DESCRIBE_APP, request);
            case "list_apps":
                return handleStructuredAction(ACTION_LIST_APPS, request);
            case "load_app":
                return handleStructuredAction(ACTION_LOAD_APP, request);
            case "delete_app":
                return handleStructuredAction(ACTION_DELETE_APP, request);
            case "list_pages":
                return handleStructuredAction(ACTION_LIST_PAGES, request);
            case "greeting":
            case "smalltalk":
                if (intentResult.shouldUseSmallTalk()) {
                    String reply = SmallTalkEngine.getSmallTalkResponse(request.description, request.userId);
                    if (reply != null) {
                        return buildSmallTalkResult(reply, request.userId, request.description);
                    }
                }
                return null;

            case "approve_continue":
                // User approved - create app from context
                if (ctx.lastDiscussedAppDescription != null) {
                    LOG.info("[AI] Approval detected via metadata, creating app from context");
                    // Don't return here - let it flow through to app creation
                    return null;
                }
                break;

            case "refine_app":
                // User wants to refine the app structure
                // Let it flow through to GPT generation with context
                LOG.info("[AI] Refinement request detected, will regenerate with context");
                return null;

            case "request_final_version":
                // Show final structure or regenerate
                if (ctx.lastDiscussedAppDescription != null) {
                    LOG.info("[AI] Final version requested, regenerating structure");
                    return null; // Flow through to generation
                }
                break;

            case "create_app":
                // Explicit app creation - flow through
                return null;

            case "gpt_fallback":
                // Low confidence - let GPT handle it
                LOG.info("[AI] Low confidence from metadata ({}), using GPT", intentResult.confidence);
                return null;

            case "refactor_entity":
                request.action = "refactor_entity";
                return null; // Fall through to runGenerationPipelines with specific action

            case "add_relationship":
                request.action = "add_relationship";
                return null; // Fall through to runGenerationPipelines with specific action

            case "unknown":
                // Unknown intent - use GPT
                return null;
        }

        return null;
    }

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
        if (!shouldHandleSmallTalk(request, normalizedAction)) {
            return null;
        }

        String userId = resolveUserId(request);

        // Check if this is approval of a previously discussed app
        if (isApprovalResponse(request)) {
            ConversationContext ctx = getContext(userId);
            if (ctx.lastDiscussedAppType != null || ctx.lastDiscussedAppDescription != null) {
                // DO NOT return here - let the approval continue to app creation
                // by returning null so the main flow can handle it as a continuation request
                LOG.info("[AI] Approval detected with context, allowing app creation flow");
                return null;
            }
        }

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

    private static boolean shouldHandleSmallTalk(GenerationRequest request, String normalizedAction) {
        if (request == null || request.description == null) {
            return false;
        }

        String lower = request.description.toLowerCase(Locale.ROOT).trim();

        // Skip small talk if this is clearly an app creation request
        if (isAppCreationRequest(lower)) {
            return false;
        }

        // FIX #5: Skip small talk if requesting page operations
        if (lower.contains("page") && (lower.contains("create") ||
                lower.contains("generate") ||
                lower.contains("do not see") ||
                lower.contains("don't see") ||
                lower.contains("missing"))) {
            return false;
        }

        // FIX: Skip small talk for specific intent patterns (describe, context)
        if (lower.matches(".*(which|what).*app.*(working|opened|editing|modifying).*") ||
                lower.matches(".*current.*app.*") ||
                lower.matches(".*app.*context.*") ||
                lower.matches(".*describe.*app.*") ||
                lower.matches(".*explain.*app.*") ||
                lower.matches(".*explian.*app.*") ||
                lower.matches(".*app.*summary.*") ||
                lower.matches(".*app.*overview.*") ||
                lower.matches(".*app.*stats.*") ||
                lower.matches(".*show.*details.*") ||
                lower.contains("selected") ||
                lower.contains("this app")) {
            return false;
        }

        // PRIORITY: Check for explicit small talk patterns FIRST (ignore
        // normalizedAction)
        // These should ALWAYS be handled as small talk, even if classifier thinks
        // otherwise
        if (lower.matches("^(hi|hello|hey|hiya|howdy|greetings)[!. ]*$")
                || lower.matches("^(good morning|good afternoon|good evening)[!. ]*$")
                || lower.matches("^(how are you\\??|how's it going\\??|what's up\\??|sup\\??)$")
                || lower.matches("^(thanks|thank you|thank you so much|thx|ty)[!. ]*$")
                || lower.matches("^(bye|goodbye|see you|cya|later)[!. ]*$")
                || lower.matches("^(ok|okay|sure|alright)[!. ]*$")) {
            return true;
        }

        // If classifier detected a specific action (other than listApps), don't treat
        // as small talk
        if (normalizedAction != null && !ACTION_LIST_APPS.equals(normalizedAction)) {
            return false;
        }

        // If classifier did not confidently detect an action, might be chit-chat
        return normalizedAction == null;
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
        } else if (Pattern.compile("shop|store|ecommerce|e-commerce|product|cart").matcher(input).find()) {
            intent.appType = "ecommerce";
            intent.appName = "E-Commerce Store";
        } else if (Pattern.compile("crm|customer|contact|lead|client").matcher(input).find()) {
            intent.appType = "crm";
            intent.appName = "CRM Application";
        } else if (Pattern.compile("cms|content").matcher(input).find()) {
            intent.appType = "blog";
            intent.appName = "Content Management System";
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
                    JsonNode defNode = wfNode.get("definition");
                    if (defNode != null) {
                        wf.setDefinitionJson(MAPPER.writeValueAsString(defNode));
                    } else {
                        wf.setDefinitionJson("{\"nodes\":{},\"transitions\":[]}");
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
        if (!"data-table".equals(pageType) && !"list".equals(pageType)) {
            return;
        }

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

        autoCompleteTable(page, entities, entityName);
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

        // Try exact match first
        for (Object entityObj : entities) {
            @SuppressWarnings("unchecked")
            Map<String, Object> entity = (Map<String, Object>) entityObj;
            String entityName = String.valueOf(entity.get("name"));
            if (normalized.equalsIgnoreCase(entityName)) {
                return entityName;
            }
        }

        // Try partial match (e.g., "customer" matches "Customer")
        for (Object entityObj : entities) {
            @SuppressWarnings("unchecked")
            Map<String, Object> entity = (Map<String, Object>) entityObj;
            String entityName = String.valueOf(entity.get("name"));
            if (entityName.toLowerCase().contains(normalized) ||
                    normalized.contains(entityName.toLowerCase())) {
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
            @SuppressWarnings("unchecked")
            Map<String, Object> entity = (Map<String, Object>) entityObj;
            if (name.equals(entity.get("name"))) {
                return entity;
            }
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
    private static boolean isRegeneratePageRequest(String description) {
        if (description == null)
            return false;

        String lower = description.toLowerCase();

        // Patterns for page regeneration requests
        return (lower.contains("page") && (lower.contains("do not see") ||
                lower.contains("don't see") ||
                lower.contains("not see") ||
                lower.contains("no page") ||
                lower.contains("missing page") ||
                lower.contains("create page") ||
                lower.contains("generate page") ||
                lower.contains("add page") ||
                lower.contains("if not created")));
    }

    /**
     * Handle request to regenerate pages for an app
     */
    private static GenerationResult handleRegeneratePagesRequest(GenerationRequest request) {
        GenerationResult result = new GenerationResult();
        result.payload = new HashMap<>();

        // Extract app ID from description
        String appId = extractAppIdFromDescription(request.description);

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

    /**
     * Extract app ID from description text (e.g., "inside
     * salon-appointment-booking-app")
     */
    private static String extractAppIdFromDescription(String description) {
        if (description == null)
            return null;

        // Pattern: "inside {app-id}" or "in {app-id}" or "{app-id}"
        Pattern pattern = Pattern.compile("(?:inside|in)\\s+([a-z0-9][a-z0-9-]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(description);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
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
     * Check if the user input implies an app creation request.
     * Supports strict patterns ("create app") and natural language ("I need a
     * system...").
     */
    private static boolean isAppCreationRequest(String description) {
        if (description == null || description.isBlank()) {
            return false;
        }
        String lower = description.toLowerCase(Locale.ROOT).trim();

        // Explicit commands
        if (lower.startsWith("create ") || lower.startsWith("build ") ||
                lower.startsWith("generate ") || lower.startsWith("make ")) {
            return true;
        }

        // Natural language needs
        if (lower.startsWith("i need ") || lower.startsWith("i want ") ||
                lower.startsWith("we need ") || lower.startsWith("we want ")) {
            if (lower.contains("app") || lower.contains("application") ||
                    lower.contains("system") || lower.contains("platform") ||
                    lower.contains("software") || lower.contains("tool")) {
                return true;
            }
        }

        // Broad context match (fallback)
        // If it looks like a detailed requirement spec, treat it as creation/refinement
        if (lower.length() > 50 && (lower.contains("store") || lower.contains("track") || lower.contains("manage"))) {
            return true;
        }

        return false;
    }

    /**
     * Update context when app is opened
     */
    private static void updateOpenedApp(String userId, String appId) {
        ConversationContext ctx = getContext(userId);
        ctx.lastOpenedAppId = appId;
        LOG.info("[AI Context] Tracked opened app: {}", appId);
    }

    /**
     * Check if user is expressing approval/confirmation
     */
    private static boolean isApprovalResponse(GenerationRequest request) {
        if (request == null || request.description == null)
            return false;

        String desc = request.description.toLowerCase().trim();

        // Approval patterns
        String[] approvalPatterns = {
                "looks ok", "looks good", "looks great", "sounds good", "sounds great",
                "sounds ok", "sounds okay",
                "that's fine", "that's good", "that's great", "that works", "that's perfect",
                "perfect", "excellent", "awesome", "nice", "cool",
                "yes", "yep", "yeah", "sure", "ok", "okay",
                "i like it", "i love it", "i agree"
        };

        for (String pattern : approvalPatterns) {
            if (desc.equals(pattern) || desc.equals(pattern + "!")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if request is a continuation (e.g., "create the app" after discussing
     * requirements)
     */
    private static boolean isContinuationRequest(GenerationRequest request) {
        if (request == null || request.description == null)
            return false;

        // Check for approval first
        if (isApprovalResponse(request)) {
            return true;
        }

        String desc = request.description.toLowerCase().trim();

        // Patterns indicating continuation
        String[] continuationPatterns = {
                "go ahead",
                "go ahead and create",
                "create the app",
                "create it",
                "create app",
                "build the app",
                "build it",
                "build app",
                "make the app",
                "make it",
                "yes create",
                "yes build",
                "go ahead",
                "proceed"
        };

        for (String pattern : continuationPatterns) {
            if (desc.equals(pattern) || desc.startsWith(pattern + " ") || desc.endsWith(" " + pattern)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Build context-aware description from conversation history
     */
    private static String buildContextualDescription(GenerationRequest request) {
        ConversationContext ctx = getContext(request.userId);

        // If continuation request and we have context, use it
        if (isContinuationRequest(request) && ctx.lastDiscussedAppDescription != null) {
            LOG.info("[AI Context] Using previous description from context for continuation request");
            return ctx.lastDiscussedAppDescription;
        }

        return request.description;
    }

    /**
     * Build a prompt suggesting the user to create the discussed app
     */
    private static String buildAppCreationPrompt(String appType) {
        return String.format(
                "Great! I'm glad you like the design. Would you like me to create the %s now? " +
                        "Just say 'yes, create it' or 'build the app' and I'll generate it for you!",
                appType);
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

            sb.append("Existing Pages: ");
            if (app.getPages() != null) {
                sb.append(String.join(", ", app.getPages()));
            } else {
                sb.append("None");
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
                "update_entity".equals(request.action);

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
}
