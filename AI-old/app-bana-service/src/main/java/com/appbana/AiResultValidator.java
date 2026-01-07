package com.appbana;

import com.appbana.model.EntitySchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates AI-generated results to ensure quality and correctness
 * Prevents template fallback when AI produces poor results
 */
public class AiResultValidator {
    
    private static final Logger LOG = LoggerFactory.getLogger(AiResultValidator.class);
    
    /**
     * Validate AI-generated result comprehensively
     */
    public static boolean validateAiResult(AiAppGeneratorService.GenerationResult result, 
                                          AiAppGeneratorService.GenerationRequest request) {
        return getValidationErrors(result, request) == null;
    }
    
    /**
     * Get detailed validation errors, or null if validation passes.
     * Returns a formatted string describing all validation failures.
     */
    public static String getValidationErrors(AiAppGeneratorService.GenerationResult result, 
                                            AiAppGeneratorService.GenerationRequest request) {
        if (result == null || !result.success) {
            return "Result is null or not successful";
        }
        
        // If AI is asking for more info, that's valid
        if (result.needsMoreInfo) {
            return null;  // Valid response
        }
        
        StringBuilder errors = new StringBuilder();
        
        // Validate app name
        String nameError = validateAppNameForErrors(result, request);
        if (nameError != null) {
            errors.append("- App Name: ").append(nameError).append("\n");
        }
        
        // Validate entities
        String entitiesError = validateEntitiesForErrors(result);
        if (entitiesError != null) {
            errors.append("- Entities: ").append(entitiesError).append("\n");
        }
        
        // Validate pages
        String pagesError = validatePagesForErrors(result);
        if (pagesError != null) {
            errors.append("- Pages: ").append(pagesError).append("\n");
        }
        
        if (errors.length() == 0) {
            LOG.info("[AI Validation] ✓ Validation passed");
            return null;  // No errors
        }
        
        return errors.toString().trim();
    }
    
    /**
     * Validate app name and return error message, or null if valid
     */
    private static String validateAppNameForErrors(AiAppGeneratorService.GenerationResult result, 
                                                   AiAppGeneratorService.GenerationRequest request) {
        if (result.appName == null || result.appName.isBlank()) {
            return "App name is missing or empty";
        }
        
        String userDesc = request.description != null ? request.description.toLowerCase() : "";
        String aiAppName = result.appName.toLowerCase();
        
        // Check for generic template names that don't match user intent
        String[] genericNames = {
            "task manager", "blog application", "crm application", 
            "e-commerce store", "new application", "application"
        };
        
        for (String generic : genericNames) {
            if (aiAppName.contains(generic) && !userDesc.contains(generic.replace(" ", ""))) {
                return String.format("App name '%s' is too generic for user request: '%s'", 
                    result.appName, request.description);
            }
        }
        
        return null;  // Valid
    }
    
    /**
     * Validate entities and return error message, or null if valid
     */
    private static String validateEntitiesForErrors(AiAppGeneratorService.GenerationResult result) {
        if (result.entities == null || result.entities.isEmpty()) {
            return "No entities generated. At least one entity with fields is required.";
        }
        
        // Validate each entity has name and fields
        for (EntitySchema entity : result.entities) {
            if (entity.getName() == null || entity.getName().isBlank()) {
                return "Found entity with missing name";
            }
            
            if (entity.getFields() == null || entity.getFields().isEmpty()) {
                return String.format("Entity '%s' has no fields", entity.getName());
            }
            
            // Validate fields have proper types
            for (EntitySchema.Field field : entity.getFields()) {
                if (field.getType() == null || field.getType().isBlank()) {
                    return String.format("Entity '%s' field '%s' has missing type", 
                        entity.getName(), field.getName());
                }
            }
        }
        
        return null;  // Valid
    }
    
    /**
     * Validate pages and return error message, or null if valid
     */
    private static String validatePagesForErrors(AiAppGeneratorService.GenerationResult result) {
        // Prefer detailed pages metadata
        if (result.pages != null && !result.pages.isEmpty()) {
            return null;  // Valid - has detailed pages
        }
        
        // Fall back to suggestedPages (less ideal but acceptable)
        if (result.suggestedPages != null && !result.suggestedPages.isEmpty()) {
            return null;  // Valid - has suggested pages
        }
        
        return "No pages generated. Must provide either 'pages' array with metadata or 'suggestedPages' array.";
    }
}
