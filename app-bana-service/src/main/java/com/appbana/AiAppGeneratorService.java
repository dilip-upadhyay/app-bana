package com.appbana;

import com.appbana.ai.AiProvider;
import com.appbana.ai.AiProviderFactory;
import com.appbana.ai.AiSystemPrompts;
import com.appbana.config.AppConfig;
import com.appbana.config.ConfigManager;
import com.appbana.model.EntitySchema;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

/**
 * AI-powered app generation service
 * Parses natural language input and generates app metadata
 */
public class AiAppGeneratorService {
    
    private static final Logger LOG = LoggerFactory.getLogger(AiAppGeneratorService.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    
    /**
     * Generate app from natural language description
     * Uses AI if configured, otherwise falls back to template-based generation
     */
    public static GenerationResult generateApp(GenerationRequest request) {
        // Enhanced logging for debugging
        LOG.info("[AI] Incoming GenerationRequest: action={}, description={}, options={}", request != null ? request.action : null, request != null ? request.description : null, request != null ? request.options : null);
        try {
            // Small talk detection (before intent classification)
            if (request != null && request.description != null) {
                // Use request.userId if available, else fallback to 'default'
                String userId = "default";
                if (request.options != null && request.options.containsKey("userId")) {
                    userId = String.valueOf(request.options.get("userId"));
                } else if (request.conversationContext != null && request.conversationContext.containsKey("userId")) {
                    userId = String.valueOf(request.conversationContext.get("userId"));
                }
                String smallTalkReply = com.appbana.ai.SmallTalkEngine.getSmallTalkResponse(request.description, userId);
                if (smallTalkReply != null) {
                    GenerationResult result = new GenerationResult();
                    result.success = true;
                    result.payload = new HashMap<>();
                    result.payload.put("smallTalk", true);
                    result.payload.put("reply", smallTalkReply);
                    LOG.info("[AI] Small talk detected, responding: {}", smallTalkReply);
                    // Record in agent memory
                    com.appbana.ai.AgentMemoryService.record(userId, request.description, smallTalkReply);
                    return result;
                }
            }
            // If no explicit action, ask the AI to classify the user's intent into an action+options JSON
            if ((request == null || request.action == null) && request != null && request.description != null) {
                try {
                    Map<String, Object> classification = classifyAction(request.description);
                    if (classification != null && classification.containsKey("action")) {
                        Object a = classification.get("action");
                        if (a != null) request.action = String.valueOf(a);
                        if (classification.containsKey("options") && classification.get("options") instanceof Map) {
                            request.options = (Map<String, Object>) classification.get("options");
                        }
                        LOG.info("[AI] AI classified action: {} with options: {}", request.action, request.options);
                    }
                } catch (Exception e) {
                    LOG.warn("[AI] Action classification failed: {}", e.getMessage());
                }
            }
            if (request != null && request.action != null) {
                String action = request.action.trim().toLowerCase();
                LOG.info("[AI] Handling action request: {}", action);
                switch (action) {
                    case "listapps":
                    case "list_apps":
                    case "list-apps":
                        GenerationResult listResult = new GenerationResult();
                        listResult.success = true;
                        List<Map<String, Object>> apps = AppManager.listApps();
                        listResult.payload = new HashMap<>();
                        listResult.payload.put("apps", apps);
                        LOG.info("[AI] Returning apps list: {}", apps);
                        return listResult;
                    case "loadapp":
                    case "load_app":
                    case "load-app":
                        String appId = null;
                        if (request.options != null && request.options.containsKey("appId")) {
                            appId = String.valueOf(request.options.get("appId"));
                        }
                        GenerationResult loadResult = new GenerationResult();
                        if (appId == null || appId.isEmpty()) {
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
                            LOG.error("[AI] Failed to load app: {}", e.getMessage());
                        }
                        return loadResult;
                    case "deleteapp":
                    case "delete_app":
                    case "delete-app":
                        String appToDelete = null;
                        if (request.options != null && request.options.containsKey("appId")) {
                            appToDelete = String.valueOf(request.options.get("appId"));
                        }
                        GenerationResult delResult = new GenerationResult();
                        if (appToDelete == null || appToDelete.isEmpty()) {
                            delResult.success = false;
                            delResult.error = "appId option is required for deleteApp";
                            LOG.warn("[AI] deleteApp missing appId");
                            return delResult;
                        }
                        try {
                            boolean deleted = AppManager.deleteApp(appToDelete);
                            delResult.success = deleted;
                            delResult.payload = new HashMap<>();
                            delResult.payload.put("deleted", deleted);
                            LOG.info("[AI] Deleted app: {}", appToDelete);
                        } catch (Exception e) {
                            delResult.success = false;
                            delResult.error = "Failed to delete app: " + e.getMessage();
                            LOG.error("[AI] Failed to delete app: {}", e.getMessage());
                        }
                        return delResult;
                    case "listpages":
                    case "list_pages":
                    case "list-pages":
                    case "getapppages":
                    case "get_app_pages":
                    case "get-app-pages": {
                        String foundAppId = null;
                        if (request.options != null && request.options.containsKey("appId")) {
                            foundAppId = String.valueOf(request.options.get("appId"));
                        } else if (request.options != null && request.options.containsKey("appName")) {
                            // Find appId by name
                            String appName = String.valueOf(request.options.get("appName"));
                            List<Map<String, Object>> appList = AppManager.listApps();
                            for (Map<String, Object> app : appList) {
                                if (appName.equalsIgnoreCase(String.valueOf(app.get("name")))) {
                                    foundAppId = String.valueOf(app.get("id"));
                                    break;
                                }
                            }
                        } else if (request.conversationContext != null && request.conversationContext.containsKey("currentAppId")) {
                            // Use currently selected app from conversation context
                            foundAppId = String.valueOf(request.conversationContext.get("currentAppId"));
                            LOG.info("[AI] Using currentAppId from conversation context: {}", foundAppId);
                        }
                        GenerationResult pageResult = new GenerationResult();
                        if (foundAppId == null || foundAppId.isEmpty()) {
                            pageResult.success = false;
                            pageResult.error = "appId or appName is required for listPages";
                            LOG.warn("[AI] listPages missing appId/appName");
                            return pageResult;
                        }
                        try {
                            Map<String, Object> appWithPages = AppManager.getAppWithPages(foundAppId);
                            if (appWithPages == null) {
                                pageResult.success = false;
                                pageResult.error = "App not found: " + foundAppId;
                                LOG.warn("[AI] App not found: {}", foundAppId);
                            } else {
                                List<?> pages = (List<?>) appWithPages.get("pages");
                                pageResult.success = true;
                                pageResult.payload = new HashMap<>();
                                pageResult.payload.put("appId", foundAppId);
                                pageResult.payload.put("pageCount", pages != null ? pages.size() : 0);
                                pageResult.payload.put("pages", pages);
                                LOG.info("[AI] App {} has {} pages", foundAppId, pages != null ? pages.size() : 0);
                            }
                        } catch (Exception e) {
                            pageResult.success = false;
                            pageResult.error = "Failed to list pages: " + e.getMessage();
                            LOG.error("[AI] Failed to list pages: {}", e.getMessage());
                        }
                        return pageResult;
                    }
                    case "createpage": {
                        // Stub: create a new page in the app
                        GenerationResult result = new GenerationResult();
                        result.success = false;
                        result.error = "createPage not yet implemented";
                        return result;
                    }
                    case "deletepage": {
                        // Stub: delete a page from the app
                        GenerationResult result = new GenerationResult();
                        result.success = false;
                        result.error = "deletePage not yet implemented";
                        return result;
                    }
                    case "listentities": {
                        // Stub: list all entities in the app
                        GenerationResult result = new GenerationResult();
                        result.success = false;
                        result.error = "listEntities not yet implemented";
                        return result;
                    }
                    case "getentitydetails": {
                        // Stub: get details for a specific entity
                        GenerationResult result = new GenerationResult();
                        result.success = false;
                        result.error = "getEntityDetails not yet implemented";
                        return result;
                    }
                    case "addentity": {
                        // Stub: add a new entity to the app
                        GenerationResult result = new GenerationResult();
                        result.success = false;
                        result.error = "addEntity not yet implemented";
                        return result;
                    }
                    case "deleteentity": {
                        // Stub: delete an entity from the app
                        GenerationResult result = new GenerationResult();
                        result.success = false;
                        result.error = "deleteEntity not yet implemented";
                        return result;
                    }
                    case "updateentity": {
                        // Stub: update an entity in the app
                        GenerationResult result = new GenerationResult();
                        result.success = false;
                        result.error = "updateEntity not yet implemented";
                        return result;
                    }
                    case "listcomponents": {
                        // Stub: list available UI components
                        GenerationResult result = new GenerationResult();
                        result.success = false;
                        result.error = "listComponents not yet implemented";
                        return result;
                    }
                    case "getappsummary": {
                        // Stub: return app summary
                        GenerationResult result = new GenerationResult();
                        result.success = false;
                        result.error = "getAppSummary not yet implemented";
                        return result;
                    }
                    case "exportapp": {
                        // Stub: export app metadata
                        GenerationResult result = new GenerationResult();
                        result.success = false;
                        result.error = "exportApp not yet implemented";
                        return result;
                    }
                    case "importapp": {
                        // Stub: import app metadata
                        GenerationResult result = new GenerationResult();
                        result.success = false;
                        result.error = "importApp not yet implemented";
                        return result;
                    }
                    case "cloneapp": {
                        // Stub: clone an app
                        GenerationResult result = new GenerationResult();
                        result.success = false;
                        result.error = "cloneApp not yet implemented";
                        return result;
                    }
                    case "getpagedetails": {
                        // Stub: get details for a specific page
                        GenerationResult result = new GenerationResult();
                        result.success = false;
                        result.error = "getPageDetails not yet implemented";
                        return result;
                    }
                    case "previewpage": {
                        // Stub: render a live preview of a page
                        GenerationResult result = new GenerationResult();
                        result.success = false;
                        result.error = "previewPage not yet implemented";
                        return result;
                    }
                    case "runvalidation": {
                        // Stub: validate app/page/entity
                        GenerationResult result = new GenerationResult();
                        result.success = false;
                        result.error = "runValidation not yet implemented";
                        return result;
                    }
                    default:
                        // fall through to AI/template generation
                        LOG.info("[AI] Unknown action '{}', falling back to generation flow", action);
                }
            }

            // Try AI generation first
            AppConfig config = getConfig();
            if (AiProviderFactory.isAiEnabled(config)) {
                try {
                    LOG.info("[AI] Attempting AI generation with provider: {}", config.getAiProvider());
                    GenerationResult aiResult = generateWithAi(request, config);
                    LOG.info("[AI] AI GenerationResult: {}", aiResult);
                    
                    // Validate AI result before returning
                    if (AiResultValidator.validateAiResult(aiResult, request)) {
                        LOG.info("[AI] AI result validated successfully");
                        return aiResult;
                    } else {
                        LOG.warn("[AI] AI result validation failed, will use templates as fallback");
                    }
                } catch (Exception e) {
                    LOG.error("[AI] AI generation failed with exception: {}", e.getMessage(), e);
                }
            } else {
                LOG.warn("[AI] AI provider not enabled, will use template-based generation");
            }

            // Fall back to template-based generation
            LOG.info("[AI] Using template-based generation as fallback");
            GenerationResult templateResult = generateFromTemplates(request);
            LOG.info("[AI] Template-based GenerationResult: {}", templateResult);
            return templateResult;
        } catch (Exception ex) {
            GenerationResult err = new GenerationResult();
            err.success = false;
            err.error = ex.getMessage();
            LOG.error("[AI] Generation failed: {}", ex.getMessage());
            return err;
        }
    }
    
    /**
     * Generate app using AI provider
     */
    private static GenerationResult generateWithAi(GenerationRequest request, AppConfig config) throws Exception {
        AiProvider provider = AiProviderFactory.createProvider(config);
        
        // Use enhanced prompt with builder-database integration
        String systemPrompt = AiSystemPrompts.getAppGenerationPrompt();
        String userPrompt = request.description;
        
        LOG.info("[AI] Calling AI provider: {} with enhanced builder-database prompt", provider.getProviderName());
        String jsonResponse = provider.generateAppStructure(userPrompt, systemPrompt);
        LOG.info("[AI] Raw AI response: {}", jsonResponse);
        String sanitized = sanitizeAiJson(jsonResponse);
        LOG.info("[AI] Sanitized AI response: {}", sanitized);
        GenerationResult result = parseAiResponse(jsonResponse);
        LOG.info("[AI] Parsed GenerationResult: {}", result);
        return result;
    }

    /**
     * Ask the AI to classify a free-text user request into an action + options JSON
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> classifyAction(String userText) throws Exception {
        AppConfig config = getConfig();
        if (!AiProviderFactory.isAiEnabled(config)) return null;
        AiProvider provider = AiProviderFactory.createProvider(config);

        String systemPrompt = AiSystemPrompts.getActionClassifierPrompt();
        String jsonResponse = provider.generateAppStructure(userText, systemPrompt);
        LOG.debug("Raw classifier response: {}", jsonResponse);
        String sanitized = sanitizeAiJson(jsonResponse);
        LOG.debug("Sanitized classifier JSON: {}", sanitized);

        Map<String, Object> parsed = null;
        try {
            parsed = mapper.readValue(sanitized, Map.class);
        } catch (Exception e) {
            LOG.warn("Failed to parse action classification JSON: {}", e.getMessage());
            parsed = null;
        }

        // Validate parsed result
        if (parsed != null && parsed.containsKey("action")) {
            Object a = parsed.get("action");
            if (a != null) {
                String act = String.valueOf(a).trim();
                // normalize action names
                switch (act.toLowerCase()) {
                    case "listapps":
                    case "list_apps":
                    case "list-apps":
                    case "list":
                        parsed.put("action", "listApps");
                        return parsed;
                    case "loadapp":
                    case "load_app":
                    case "load-app":
                    case "open":
                        parsed.put("action", "loadApp");
                        return parsed;
                    case "deleteapp":
                    case "delete_app":
                    case "delete-app":
                    case "delete":
                        parsed.put("action", "deleteApp");
                        return parsed;
                    case "listpages":
                    case "list_pages":
                    case "list-pages":
                    case "pages":
                        parsed.put("action", "listPages");
                        return parsed;
                    case "generateapp":
                    case "generate_app":
                    case "generate-app":
                    case "generate":
                        parsed.put("action", "generateApp");
                        return parsed;
                    default:
                        // unknown action - we'll fallback to heuristics below
                        LOG.info("Classifier returned unknown action '{}', falling back to heuristics", act);
                }
            }
        }

        // If classifier failed or returned nothing useful, use simple heuristics as a fallback
        String lower = userText == null ? "" : userText.toLowerCase();
        Map<String, Object> fallback = new HashMap<>();

        LOG.info("[AI] classifyAction: userText='{}'", userText);
        LOG.info("[AI] classifyAction: lower='{}'", lower);
        // Heuristic: page count or list pages for an app
        boolean isPageCountQuery = lower.matches(".*(how many|count|number of) pages.*(in|for|of) .*") || lower.matches(".*show pages.*(in|for|of) .*") || lower.matches(".*list pages.*(in|for|of) .*");
        LOG.info("[AI] classifyAction: isPageCountQuery={}", isPageCountQuery);
        if (isPageCountQuery) {
            // Try to extract app name
            String appName = null;
            Pattern p = Pattern.compile("(in|for|of) ([A-Za-z0-9 _-]+)");
            java.util.regex.Matcher m = p.matcher(userText);
            if (m.find()) {
                appName = m.group(2).trim();
                LOG.info("[AI] classifyAction: extracted appName='{}'", appName);
            } else {
                LOG.info("[AI] classifyAction: appName not found in userText");
            }
            Map<String, Object> opts = new HashMap<>();
            if (appName != null && !appName.isEmpty()) opts.put("appName", appName);
            Map<String, Object> result = new HashMap<>();
            result.put("action", "listPages");
            result.put("options", opts);
            LOG.info("[AI] classifyAction: returning listPages action with options={}", opts);
            // Always return here for page-count queries
            return result;
        }

        // default: generateApp
        fallback.put("action", "generateApp");
        fallback.put("options", new HashMap<>());
        LOG.info("No classifier match; defaulting to generateApp for input: {}", userText);
        return fallback;
    }
    
    /**
     * Parse AI-generated JSON into GenerationResult
     */
    private static GenerationResult parseAiResponse(String jsonResponse) throws Exception {
        // Sanitize AI output: strip markdown code fences and extract the JSON block
        String sanitized = sanitizeAiJson(jsonResponse);
        JsonNode root = mapper.readTree(sanitized);
        
        GenerationResult result = new GenerationResult();
        
        // Check if AI is asking for more information
        if (root.has("needsMoreInfo") && root.get("needsMoreInfo").asBoolean()) {
            result.success = true;
            result.needsMoreInfo = true;
            
            // Parse follow-up questions
            result.followUpQuestions = new ArrayList<>();
            JsonNode questionsNode = root.get("followUpQuestions");
            if (questionsNode != null && questionsNode.isArray()) {
                for (JsonNode questionNode : questionsNode) {
                    result.followUpQuestions.add(questionNode.asText());
                }
            }
            
            // Store partial structure if provided
            if (root.has("partialStructure")) {
                JsonNode partialNode = root.get("partialStructure");
                if (partialNode.has("appName")) {
                    result.appName = partialNode.get("appName").asText();
                }
            }
            
            return result;
        }
        
        // Parse complete app structure
        result.success = true;
        result.needsMoreInfo = false;
        result.appName = root.get("appName").asText();
        result.appDescription = root.get("appDescription").asText();
        
        // Parse entities
        result.entities = new ArrayList<>();
        JsonNode entitiesNode = root.get("entities");
        if (entitiesNode != null && entitiesNode.isArray()) {
            for (JsonNode entityNode : entitiesNode) {
                String entityName = entityNode.get("name").asText();
                List<EntitySchema.Field> fields = new ArrayList<>();
                
                JsonNode fieldsNode = entityNode.get("fields");
                if (fieldsNode != null && fieldsNode.isArray()) {
                    for (JsonNode fieldNode : fieldsNode) {
                        String fieldName = fieldNode.get("name").asText();
                        String fieldType = fieldNode.get("type").asText();
                        boolean required = fieldNode.get("required").asBoolean(false);
                        
                        EntitySchema.Field field = new EntitySchema.Field();
                        field.setName(fieldName);
                        field.setType(fieldType);
                        field.setRequired(required);
                        field.setPrimaryKey(false);
                        field.setAutoIncrement(false);
                        
                        fields.add(field);
                    }
                }
                
                result.entities.add(new EntitySchema(entityName, fields));
            }
        }
        
        // Parse relationships
        result.relationships = new ArrayList<>();
        JsonNode relationshipsNode = root.get("relationships");
        if (relationshipsNode != null && relationshipsNode.isArray()) {
            for (JsonNode relNode : relationshipsNode) {
                result.relationships.add(relNode.asText());
            }
        }
        
        // Parse suggested pages (now with more detail)
        result.suggestedPages = new ArrayList<>();
        JsonNode pagesNode = root.get("suggestedPages");
        if (pagesNode != null && pagesNode.isArray()) {
            for (JsonNode pageNode : pagesNode) {
                if (pageNode.isTextual()) {
                    // Simple string format (legacy)
                    result.suggestedPages.add(pageNode.asText());
                } else if (pageNode.isObject()) {
                    // Detailed object format
                    result.suggestedPages.add(mapper.writeValueAsString(pageNode));
                }
            }
        }
        
        // Parse detailed pages metadata
        result.pages = new ArrayList<>();
        JsonNode pagesMetaNode = root.get("pages");
        if (pagesMetaNode != null && pagesMetaNode.isArray()) {
            for (JsonNode pageNode : pagesMetaNode) {
                result.pages.add(mapper.convertValue(pageNode, Map.class));
            }
        }
        
        return result;
    }

    /**
     * Attempt to extract a clean JSON substring from AI output.
     * Handles triple-backtick fenced blocks (```json ... ```), and
     * falls back to extracting the first {...} or [...] block found.
     */
    public static String sanitizeAiJson(String raw) {
        if (raw == null) return null;
        String s = raw.trim();

        // If there are triple-backtick fences, extract content between the first and last fence
        int firstFence = s.indexOf("```");
        int lastFence = s.lastIndexOf("```");
        if (firstFence != -1 && lastFence != -1 && lastFence > firstFence) {
            String inside = s.substring(firstFence + 3, lastFence).trim();
            // If the block starts with a language hint like "json", remove it
            if (inside.startsWith("json")) {
                inside = inside.substring(4).trim();
            }
            return inside.trim();
        }

        // Otherwise try to find a JSON object or array by locating the first { or [ and the matching last } or ]
        int firstBrace = s.indexOf('{');
        int firstBracket = s.indexOf('[');
        int start = -1;
        char endChar = 0;
        if (firstBrace != -1 && (firstBracket == -1 || firstBrace < firstBracket)) {
            start = firstBrace;
            endChar = '}';
        } else if (firstBracket != -1) {
            start = firstBracket;
            endChar = ']';
        }

        if (start != -1) {
            int end = s.lastIndexOf(endChar);
            if (end != -1 && end >= start) {
                return s.substring(start, end + 1).trim();
            }
        }

        // Fall back to returning the raw trimmed string
        return s;
    }
    
    /**
     * Get config with fallback to defaults
     */
    private static AppConfig getConfig() {
        try {
            return ConfigManager.getConfig();
        } catch (Exception e) {
            LOG.warn("ConfigManager not available, using defaults");
            return new AppConfig();
        }
    }
    
    /**
     * Generate app from templates (original regex-based logic)
     */
    private static GenerationResult generateFromTemplates(GenerationRequest request) {
        String input = request.description.toLowerCase();
        
        // Parse intent
        AppIntent intent = parseIntent(input);
        
        // Generate based on type
        GenerationResult result = new GenerationResult();
        result.success = true;
        
        switch (intent.appType) {
            case "blog":
                result = generateBlogApp(intent);
                break;
            case "task":
                result = generateTaskApp(intent);
                break;
            case "ecommerce":
                result = generateEcommerceApp(intent);
                break;
            case "crm":
                result = generateCrmApp(intent);
                break;
            default:
                result = generateGenericApp(intent);
        }
        
        return result;
    }
    
    /**
     * Parse user intent from natural language
     */
    private static AppIntent parseIntent(String input) {
        AppIntent intent = new AppIntent();
        intent.originalInput = input;
        
        // Detect app type
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
            intent.appType = "blog"; // CMS similar to blog
            intent.appName = "Content Management System";
        } else {
            intent.appType = "generic";
            intent.appName = "Application";
        }
        
        return intent;
    }
    
    /**
     * Generate blog app with Post and Comment entities
     */
    private static GenerationResult generateBlogApp(AppIntent intent) {
        GenerationResult result = new GenerationResult();
        result.success = true;
        result.appName = intent.appName;
        result.appDescription = "A blog application with posts and comments";
        result.entities = new ArrayList<>();
        
        // Post entity
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
        
        // Comment entity
        EntitySchema comment = new EntitySchema();
        comment.setName("Comment");
        comment.setFields(Arrays.asList(
            createField("id", "long", true, true),
            createField("content", "string", false, false),
            createField("author", "string", false, false),
            createField("post_id", "long", false, false)
        ));
        result.entities.add(comment);
        
        // Add relationship info
        result.relationships = Arrays.asList(
            "Comment.post_id → Post.id (many-to-one, CASCADE DELETE)"
        );
        
        // Suggested pages
        result.suggestedPages = Arrays.asList(
            "Posts List (Data Table)",
            "Post Detail (Profile)",
            "Create Post (Form)"
        );
        
        return result;
    }
    
    /**
     * Generate task manager app
     */
    private static GenerationResult generateTaskApp(AppIntent intent) {
        GenerationResult result = new GenerationResult();
        result.success = true;
        result.appName = intent.appName;
        result.appDescription = "Task and todo management application";
        result.entities = new ArrayList<>();
        
        // Task entity
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
    
    /**
     * Generate e-commerce app
     */
    private static GenerationResult generateEcommerceApp(AppIntent intent) {
        GenerationResult result = new GenerationResult();
        result.success = true;
        result.appName = intent.appName;
        result.appDescription = "Online store with product catalog";
        result.entities = new ArrayList<>();
        
        // Product entity
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
    
    /**
     * Generate CRM app
     */
    private static GenerationResult generateCrmApp(AppIntent intent) {
        GenerationResult result = new GenerationResult();
        result.success = true;
        result.appName = intent.appName;
        result.appDescription = "Customer relationship management system";
        result.entities = new ArrayList<>();
        
        // Contact entity
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
    
    /**
     * Generate generic app
     */
    private static GenerationResult generateGenericApp(AppIntent intent) {
        GenerationResult result = new GenerationResult();
        result.success = true;
        result.appName = "New Application";
        result.appDescription = "Custom application";
        result.entities = new ArrayList<>();
        result.suggestedPages = new ArrayList<>();
        
        return result;
    }
    
    /**
     * Helper to create field definition
     */
    private static EntitySchema.Field createField(String name, String type, boolean isPrimaryKey, boolean isAutoIncrement) {
        EntitySchema.Field field = new EntitySchema.Field();
        field.setName(name);
        field.setType(type);
        field.setPrimaryKey(isPrimaryKey);
        field.setAutoIncrement(isAutoIncrement);
        field.setRequired(!isPrimaryKey); // All non-PK fields are required by default
        return field;
    }
    
    // Request/Response models
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GenerationRequest {
        public String description;
        public String userId;
        public Map<String, Object> options;
        // Optional action (listApps, loadApp, deleteApp, etc.)
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
        public List<Map<String, Object>> pages; // <-- Add detailed page metadata
        public String error;
        // Generic payload for action results (e.g., listApps -> { apps: [...] })
        public Map<String, Object> payload;
    }
    
    private static class AppIntent {
        String originalInput;
        String appType;
        String appName;
        List<String> detectedEntities;
    }
}
