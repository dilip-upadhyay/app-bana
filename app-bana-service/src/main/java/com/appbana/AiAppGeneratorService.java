package com.appbana;

import com.appbana.ai.AiProvider;
import com.appbana.ai.AiProviderFactory;
import com.appbana.ai.AiSystemPrompts;
import com.appbana.ai.AgentMemoryService;
import com.appbana.ai.SmallTalkEngine;
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

/**
 * AI-powered app generation service.
 * Resolves conversational intent into structured actions and falls back to template-based generation.
 */
public class AiAppGeneratorService {

    private static final Logger LOG = LoggerFactory.getLogger(AiAppGeneratorService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {};

    private static final String ACTION_LIST_APPS = "listApps";
    private static final String ACTION_LOAD_APP = "loadApp";
    private static final String ACTION_DELETE_APP = "deleteApp";
    private static final String ACTION_LIST_PAGES = "listPages";
    private static final String ACTION_GENERATE_APP = "generateApp";
    private static final String PAYLOAD_APPS = "apps";
    private static final String PAYLOAD_ACTION = "action";
    private static final String PAYLOAD_REPLY = "reply";
    private static final String PAYLOAD_SMALL_TALK = "smallTalk";
    private static final String DEFAULT_USER = "default";

    public static GenerationResult generateApp(GenerationRequest request) {
        LOG.info("[AI] Incoming GenerationRequest: action={}, description={}, options={}",
            request != null ? request.action : null,
            request != null ? request.description : null,
            request != null ? request.options : null);
        try {
            String normalizedAction = resolveAction(request);

            if (ACTION_LIST_APPS.equals(normalizedAction)) {
                return buildAppsListResult();
            }

            GenerationResult smallTalk = handleSmallTalkIfNeeded(request, normalizedAction);
            if (smallTalk != null) {
                return smallTalk;
            }

            if (normalizedAction != null) {
                GenerationResult actionResult = handleStructuredAction(normalizedAction, request);
                if (actionResult != null) {
                    return actionResult;
                }
            }

            return runGenerationPipelines(request);
        } catch (Exception ex) {
            GenerationResult err = new GenerationResult();
            err.success = false;
            err.error = ex.getMessage();
            LOG.error("[AI] Generation failed", ex);
            return err;
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

        return request.action;
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
        if (normalizedAction != null) {
            return false;
        }
        return !isAppCreationRequest(request.description.toLowerCase(Locale.ROOT));
    }

    private static boolean isAppCreationRequest(String lowerDescription) {
        if (lowerDescription == null) {
            return false;
        }
        return lowerDescription.contains("create the app")
            || lowerDescription.contains("build the app")
            || lowerDescription.contains("generate the app")
            || lowerDescription.contains("make the app")
            || lowerDescription.startsWith("create app")
            || lowerDescription.startsWith("build app")
            || lowerDescription.startsWith("generate app")
            || lowerDescription.startsWith("make app");
    }

    private static String resolveUserId(GenerationRequest request) {
        if (request == null) {
            return DEFAULT_USER;
        }
        if (request.options != null && request.options.get("userId") != null) {
            return String.valueOf(request.options.get("userId"));
        }
        if (request.conversationContext != null && request.conversationContext.get("userId") != null) {
            return String.valueOf(request.conversationContext.get("userId"));
        }
        if (request.userId != null && !request.userId.isBlank()) {
            return request.userId;
        }
        return DEFAULT_USER;
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
            default:
                LOG.info("[AI] Unknown action '{}', falling back to generation flow", action);
                return null;
        }
    }

    private static GenerationResult buildAppsListResult() {
        GenerationResult listResult = new GenerationResult();
        listResult.success = true;
        List<Map<String, Object>> apps = safeListApps();
        listResult.payload = new HashMap<>();
        listResult.payload.put(PAYLOAD_APPS, apps);
        listResult.payload.put(PAYLOAD_ACTION, "list");
        listResult.payload.put(PAYLOAD_REPLY, "Fetched your apps via GET /apps");
        LOG.info("[AI] Returning apps list: {}", apps);
        return listResult;
    }

    private static GenerationResult handleLoadApp(GenerationRequest request) {
        GenerationResult loadResult = new GenerationResult();
        String appId = resolveLoadAppId(request);
        if (appId == null || appId.isBlank()) {
            loadResult.success = false;
            loadResult.error = "appId option is required for loadApp";
            LOG.warn("[AI] loadApp missing appId");
            return loadResult;
        }
        try {
            Map<String, Object> appWithPages = AppManager.getAppWithPages(appId);
            if (appWithPages == null) {
                loadResult.success = false;
                loadResult.error = "App not found: " + appId;
                LOG.warn("[AI] App not found: {}", appId);
            } else {
                loadResult.success = true;
                loadResult.payload = new HashMap<>();
                loadResult.payload.put("app", appWithPages.get("app"));
                loadResult.payload.put("pages", appWithPages.get("pages"));
                LOG.info("[AI] Loaded app: {}", appId);
            }
        } catch (Exception e) {
            loadResult.success = false;
            loadResult.error = "Failed to load app: " + e.getMessage();
            LOG.error("[AI] Failed to load app", e);
        }
        return loadResult;
    }

    private static String resolveLoadAppId(GenerationRequest request) {
        if (request == null) {
            return null;
        }
        if (request.options != null && request.options.get("appId") != null) {
            return String.valueOf(request.options.get("appId"));
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

    private static GenerationResult handleDeleteApp(GenerationRequest request) {
        GenerationResult result = new GenerationResult();
        String appId = request != null && request.options != null ? String.valueOf(request.options.get("appId")) : null;
        if (appId == null || appId.isBlank()) {
            result.success = false;
            result.error = "appId option is required for deleteApp";
            LOG.warn("[AI] deleteApp missing appId");
            return result;
        }
        try {
            boolean deleted = AppManager.deleteApp(appId);
            result.success = deleted;
            result.payload = new HashMap<>();
            result.payload.put("deleted", deleted);
            LOG.info("[AI] Deleted app: {}", appId);
        } catch (Exception e) {
            result.success = false;
            result.error = "Failed to delete app: " + e.getMessage();
            LOG.error("[AI] Failed to delete app", e);
        }
        return result;
    }

    private static GenerationResult handleListPages(GenerationRequest request) {
        GenerationResult pageResult = new GenerationResult();
        String appId = resolveAppIdForPages(request);
        if (appId == null || appId.isBlank()) {
            pageResult.success = false;
            pageResult.error = "appId or appName is required for listPages";
            LOG.warn("[AI] listPages missing appId/appName");
            return pageResult;
        }
        try {
            Map<String, Object> appWithPages = AppManager.getAppWithPages(appId);
            if (appWithPages == null) {
                pageResult.success = false;
                pageResult.error = "App not found: " + appId;
                LOG.warn("[AI] App not found: {}", appId);
            } else {
                List<?> pages = (List<?>) appWithPages.get("pages");
                pageResult.success = true;
                pageResult.payload = new HashMap<>();
                pageResult.payload.put("appId", appId);
                pageResult.payload.put("pageCount", pages != null ? pages.size() : 0);
                pageResult.payload.put("pages", pages);
                LOG.info("[AI] App {} has {} pages", appId, pages != null ? pages.size() : 0);
            }
        } catch (Exception e) {
            pageResult.success = false;
            pageResult.error = "Failed to list pages: " + e.getMessage();
            LOG.error("[AI] Failed to list pages", e);
        }
        return pageResult;
    }

    private static String resolveAppIdForPages(GenerationRequest request) {
        if (request == null) {
            return null;
        }
        if (request.options != null) {
            Object appId = request.options.get("appId");
            if (appId != null && !String.valueOf(appId).isBlank()) {
                return String.valueOf(appId);
            }
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

    private static List<Map<String, Object>> safeListApps() {
        try {
            return AppManager.listApps();
        } catch (IOException e) {
            LOG.error("[AI] Failed to list apps", e);
            return Collections.emptyList();
        }
    }

    private static GenerationResult runGenerationPipelines(GenerationRequest request) {
        AppConfig config = getConfig();
        if (AiProviderFactory.isAiEnabled(config)) {
            try {
                LOG.info("[AI] Attempting AI generation with provider: {}", config.getAiProvider());
                GenerationResult aiResult = generateWithAi(request, config);
                if (AiResultValidator.validateAiResult(aiResult, request)) {
                    LOG.info("[AI] AI result validated successfully");
                    return aiResult;
                }
                LOG.warn("[AI] AI result validation failed, will use templates as fallback");
            } catch (Exception e) {
                LOG.error("[AI] AI generation failed", e);
            }
        } else {
            LOG.warn("[AI] AI provider not enabled, will use template-based generation");
        }

        LOG.info("[AI] Using template-based generation as fallback");
        GenerationResult templateResult = generateFromTemplates(request);
        LOG.info("[AI] Template-based GenerationResult: {}", templateResult);
        return templateResult;
    }

    private static GenerationResult generateWithAi(GenerationRequest request, AppConfig config) throws Exception {
        AiProvider provider = AiProviderFactory.createProvider(config);
        String systemPrompt = AiSystemPrompts.getAppGenerationPrompt();
        String userPrompt = request != null ? request.description : "";

        LOG.info("[AI] Calling AI provider: {} with enhanced builder-database prompt", provider.getProviderName());
        String jsonResponse = provider.generateAppStructure(userPrompt, systemPrompt);
        LOG.info("[AI] Raw AI response: {}", jsonResponse);
        GenerationResult result = parseAiResponse(jsonResponse);
        LOG.info("[AI] Parsed GenerationResult: {}", result);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> classifyAction(String userText) throws Exception {
        AppConfig config = getConfig();
        Map<String, Object> parsed = null;

        if (AiProviderFactory.isAiEnabled(config)) {
            AiProvider provider = AiProviderFactory.createProvider(config);
            String systemPrompt = AiSystemPrompts.getActionClassifierPrompt();
            String jsonResponse = provider.generateAppStructure(userText, systemPrompt);
            LOG.debug("Raw classifier response: {}", jsonResponse);
            String sanitized = sanitizeAiJson(jsonResponse);
            LOG.debug("Sanitized classifier JSON: {}", sanitized);
            try {
                parsed = MAPPER.readValue(sanitized, Map.class);
            } catch (Exception e) {
                LOG.warn("Failed to parse action classification JSON: {}", e.getMessage());
                parsed = null;
            }
        }

        if (parsed != null && parsed.containsKey("action")) {
            Object a = parsed.get("action");
            if (a != null) {
                parsed.put("action", normalizeActionLabel(String.valueOf(a)));
            }
            return parsed;
        }

        return heuristicClassification(userText);
    }

    private static Map<String, Object> heuristicClassification(String userText) {
        String lower = userText == null ? "" : userText.toLowerCase(Locale.ROOT);
        Map<String, Object> fallback = new HashMap<>();

        if (lower.matches(".*(list|show).*(apps|app list).*")) {
            fallback.put("action", ACTION_LIST_APPS);
            fallback.put("options", new HashMap<>());
            return fallback;
        }
        if (lower.matches(".*(load|open).*(app).*")) {
            fallback.put("action", ACTION_LOAD_APP);
            fallback.put("options", new HashMap<>());
            return fallback;
        }
        if (lower.matches(".*delete.*app.*")) {
            fallback.put("action", ACTION_DELETE_APP);
            fallback.put("options", new HashMap<>());
            return fallback;
        }
        if (lower.matches(".*(how many|count|number of).*pages.*") || lower.matches(".*(list|show).*pages.*")) {
            Map<String, Object> opts = new HashMap<>();
            Matcher matcher = Pattern.compile("(in|for|of) ([A-Za-z0-9 _-]+)").matcher(userText == null ? "" : userText);
            if (matcher.find()) {
                opts.put("appName", matcher.group(2).trim());
            }
            Map<String, Object> result = new HashMap<>();
            result.put("action", ACTION_LIST_PAGES);
            result.put("options", opts);
            return result;
        }

        fallback.put("action", ACTION_GENERATE_APP);
        fallback.put("options", new HashMap<>());
        return fallback;
    }

    private static String normalizeActionLabel(String action) {
        if (action == null) {
            return null;
        }
        switch (action.trim().toLowerCase(Locale.ROOT)) {
            case "list":
            case "listapps":
            case "list_apps":
            case "list-apps":
            case "showapps":
            case "show_apps":
            case "show-apps":
            case "list tab":
            case "show my apps":
            case "list my apps":
            case "list all apps":
            case "show all apps":
                return ACTION_LIST_APPS;
            case "loadapp":
            case "load_app":
            case "load-app":
            case "open":
                return ACTION_LOAD_APP;
            case "deleteapp":
            case "delete_app":
            case "delete-app":
            case "delete":
                return ACTION_DELETE_APP;
            case "listpages":
            case "list_pages":
            case "list-pages":
            case "pages":
                return ACTION_LIST_PAGES;
            case "generateapp":
            case "generate_app":
            case "generate-app":
            case "generate":
                return ACTION_GENERATE_APP;
            default:
                return action;
        }
    }

    private static GenerationResult parseAiResponse(String jsonResponse) throws Exception {
        String sanitized = sanitizeAiJson(jsonResponse);
        JsonNode root = MAPPER.readTree(sanitized);

        GenerationResult result = new GenerationResult();

        if (root.has("needsMoreInfo") && root.get("needsMoreInfo").asBoolean()) {
            result.success = true;
            result.needsMoreInfo = true;
            result.followUpQuestions = new ArrayList<>();
            JsonNode questionsNode = root.get("followUpQuestions");
            if (questionsNode != null && questionsNode.isArray()) {
                for (JsonNode questionNode : questionsNode) {
                    result.followUpQuestions.add(questionNode.asText());
                }
            }
            if (root.has("partialStructure")) {
                JsonNode partialNode = root.get("partialStructure");
                if (partialNode.has("appName")) {
                    result.appName = partialNode.get("appName").asText();
                }
            }
            return result;
        }

        result.success = true;
        result.needsMoreInfo = false;
        result.appName = root.path("appName").asText(null);
        result.appDescription = root.path("appDescription").asText(null);

        result.entities = new ArrayList<>();
        JsonNode entitiesNode = root.get("entities");
        if (entitiesNode != null && entitiesNode.isArray()) {
            for (JsonNode entityNode : entitiesNode) {
                String entityName = entityNode.get("name").asText();
                List<EntitySchema.Field> fields = new ArrayList<>();
                JsonNode fieldsNode = entityNode.get("fields");
                if (fieldsNode != null && fieldsNode.isArray()) {
                    for (JsonNode fieldNode : fieldsNode) {
                        EntitySchema.Field field = new EntitySchema.Field();
                        field.setName(fieldNode.get("name").asText());
                        field.setType(fieldNode.get("type").asText());
                        field.setRequired(fieldNode.path("required").asBoolean(false));
                        field.setPrimaryKey(fieldNode.path("primaryKey").asBoolean(false));
                        field.setAutoIncrement(fieldNode.path("autoIncrement").asBoolean(false));
                        fields.add(field);
                    }
                }
                result.entities.add(new EntitySchema(entityName, fields));
            }
        }

        result.relationships = new ArrayList<>();
        JsonNode relationshipsNode = root.get("relationships");
        if (relationshipsNode != null && relationshipsNode.isArray()) {
            for (JsonNode relNode : relationshipsNode) {
                result.relationships.add(relNode.asText());
            }
        }

        result.suggestedPages = new ArrayList<>();
        JsonNode pagesNode = root.get("suggestedPages");
        if (pagesNode != null && pagesNode.isArray()) {
            for (JsonNode pageNode : pagesNode) {
                result.suggestedPages.add(pageNode.isTextual() ? pageNode.asText() : MAPPER.writeValueAsString(pageNode));
            }
        }

        result.pages = new ArrayList<>();
        JsonNode pagesMetaNode = root.get("pages");
        if (pagesMetaNode != null && pagesMetaNode.isArray()) {
            for (JsonNode pageNode : pagesMetaNode) {
                result.pages.add(MAPPER.convertValue(pageNode, MAP_TYPE));
            }
        }

        return result;
    }

    public static String sanitizeAiJson(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }

        int firstFence = trimmed.indexOf("```");
        int lastFence = trimmed.lastIndexOf("```");
        if (firstFence >= 0 && lastFence > firstFence) {
            String inside = trimmed.substring(firstFence + 3, lastFence).trim();
            if (inside.startsWith("json")) {
                inside = inside.substring(4).trim();
            }
            if (!inside.isEmpty()) {
                return inside;
            }
        }

        int firstBrace = trimmed.indexOf('{');
        int firstBracket = trimmed.indexOf('[');
        int start;
        if (firstBrace == -1) {
            start = firstBracket;
        } else if (firstBracket == -1) {
            start = firstBrace;
        } else {
            start = Math.min(firstBrace, firstBracket);
        }
        if (start == -1) {
            return trimmed;
        }

        char open = trimmed.charAt(start);
        char close = open == '{' ? '}' : ']';
        int depth = 0;
        for (int i = start; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return trimmed.substring(start, i + 1);
                }
            }
        }
        return trimmed.substring(start);
    }

    private static GenerationResult generateFromTemplates(GenerationRequest request) {
        String description = request != null && request.description != null ? request.description : "";
        AppIntent intent = parseIntent(description.toLowerCase(Locale.ROOT));
        switch (intent.appType) {
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

    private static AppIntent parseIntent(String input) {
        AppIntent intent = new AppIntent();
        intent.originalInput = input;
        if (input == null) {
            intent.appType = "generic";
            intent.appName = "Application";
            return intent;
        }
        if (Pattern.compile("blog|post|article|comment").matcher(input).find()) {
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
            createField("status", "string", false, false)
        ));
        result.entities.add(post);

        EntitySchema comment = new EntitySchema();
        comment.setName("Comment");
        comment.setFields(Arrays.asList(
            createField("id", "long", true, true),
            createField("content", "string", false, false),
            createField("author", "string", false, false),
            createField("post_id", "long", false, false)
        ));
        result.entities.add(comment);

        result.relationships = Arrays.asList(
            "Comment.post_id → Post.id (many-to-one, CASCADE DELETE)"
        );

        result.suggestedPages = Arrays.asList(
            "Posts List (Data Table)",
            "Post Detail (Profile)",
            "Create Post (Form)"
        );

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
            createField("due_date", "date", false, false)
        ));
        result.entities.add(task);

        result.suggestedPages = Arrays.asList(
            "Task List (Data Table)",
            "Task Detail (Profile)",
            "Create Task (Form)"
        );

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
            createField("category", "string", false, false)
        ));
        result.entities.add(product);

        result.suggestedPages = Arrays.asList(
            "Product Catalog (Data Table)",
            "Product Detail (Profile)"
        );

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
            createField("status", "string", false, false)
        ));
        result.entities.add(contact);

        result.suggestedPages = Arrays.asList(
            "Contacts List (Data Table)",
            "Contact Detail (Profile)"
        );

        return result;
    }

    private static GenerationResult generateGenericApp(AppIntent intent) {
        GenerationResult result = new GenerationResult();
        result.success = true;
        result.appName = intent.appName != null ? intent.appName : "New Application";
        result.appDescription = "Custom application";
        result.entities = new ArrayList<>();
        result.suggestedPages = new ArrayList<>();
        return result;
    }

    private static EntitySchema.Field createField(String name, String type, boolean isPrimaryKey, boolean isAutoIncrement) {
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GenerationRequest {
        public String description;
        public String userId;
        public Map<String, Object> options;
        public String action;
        public Map<String, Object> conversationContext;
        public String mode;
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
}
