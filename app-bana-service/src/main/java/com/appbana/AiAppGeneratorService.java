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
        // Try AI generation first
        AppConfig config = getConfig();
        if (AiProviderFactory.isAiEnabled(config)) {
            try {
                LOG.info("Attempting AI generation with provider: {}", config.getAiProvider());
                return generateWithAi(request, config);
            } catch (Exception e) {
                LOG.error("AI generation failed, falling back to templates", e);
            }
        }
        
        // Fall back to template-based generation
        LOG.info("Using template-based generation");
        return generateFromTemplates(request);
    }
    
    /**
     * Generate app using AI provider
     */
    private static GenerationResult generateWithAi(GenerationRequest request, AppConfig config) throws Exception {
        AiProvider provider = AiProviderFactory.createProvider(config);
        
        // Use enhanced prompt with builder-database integration
        String systemPrompt = AiSystemPrompts.getAppGenerationPrompt();
        String userPrompt = request.description;
        
        LOG.info("Calling AI provider: {} with enhanced builder-database prompt", provider.getProviderName());
        String jsonResponse = provider.generateAppStructure(userPrompt, systemPrompt);
        
        LOG.debug("AI response: {}", jsonResponse);
        
        // Parse AI JSON response
        return parseAiResponse(jsonResponse);
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
        
        return result;
    }

    /**
     * Attempt to extract a clean JSON substring from AI output.
     * Handles triple-backtick fenced blocks (```json ... ```), and
     * falls back to extracting the first {...} or [...] block found.
     */
    private static String sanitizeAiJson(String raw) {
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
        public String error;
    }
    
    private static class AppIntent {
        String originalInput;
        String appType;
        String appName;
        List<String> detectedEntities;
    }
}
