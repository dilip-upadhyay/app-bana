package com.appbana;

import com.appbana.model.EntitySchema;

import java.util.*;
import java.util.regex.Pattern;

/**
 * AI-powered app generation service
 * Parses natural language input and generates app metadata
 */
public class AiAppGeneratorService {
    
    /**
     * Generate app from natural language description
     */
    public static GenerationResult generateApp(GenerationRequest request) {
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
    
    public static class GenerationRequest {
        public String description;
        public String userId;
        public Map<String, Object> options;
    }
    
    public static class GenerationResult {
        public boolean success;
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
