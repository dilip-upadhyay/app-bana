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
        if (result == null || !result.success) {
            LOG.warn("[AI Validation] Result is null or not successful");
            return false;
        }
        
        // If AI is asking for more info, that's valid
        if (result.needsMoreInfo) {
            LOG.info("[AI Validation] AI requested more information - valid response");
            return true;
        }
        
        // Validate app name matches user request
        if (!validateAppName(result, request)) {
            return false;
        }
        
        // Validate entities
        if (!validateEntities(result)) {
            return false;
        }
        
        // Validate pages (prefer detailed pages over suggested)
        if (!validatePages(result)) {
            return false;
        }
        
        LOG.info("[AI Validation] ✓ Validation passed");
        return true;
    }
    
    /**
     * Validate app name is not generic and matches user request
     */
    private static boolean validateAppName(AiAppGeneratorService.GenerationResult result, 
                                           AiAppGeneratorService.GenerationRequest request) {
        if (result.appName == null || result.appName.isBlank()) {
            LOG.warn("[AI Validation] ✗ App name is missing");
            return false;
        }
        
        String userDesc = request.description != null ? request.description.toLowerCase() : "";
        String aiAppName = result.appName.toLowerCase();
        
        // Check for generic template names that don't match user intent
        String[] genericNames = {
            "task manager", "blog application", "crm application", 
            "e-commerce store", "new application"
        };
        
        for (String generic : genericNames) {
            if (aiAppName.contains(generic) && !userDesc.contains(generic.replace(" ", ""))) {
                LOG.warn("[AI Validation] ✗ App name '{}' appears to be a generic template, user requested: {}", 
                    result.appName, request.description);
                return false;
            }
        }
        
        // Extract domain keywords from user request
        String[] domainKeywords = {
            "restaurant", "project management", "inventory", "hospital", "school", 
            "library", "hotel", "booking", "reservation", "appointment", "clinic",
            "pharmacy", "gym", "fitness", "real estate", "rental", "property"
        };
        
        for (String keyword : domainKeywords) {
            if (userDesc.contains(keyword) && !aiAppName.contains(keyword)) {
                LOG.warn("[AI Validation] ⚠ App name '{}' missing domain keyword '{}' from user request", 
                    result.appName, keyword);
                // Don't fail - AI might use synonyms, but it's suspicious
            }
        }
        
        LOG.info("[AI Validation] ✓ App name '{}' validated", result.appName);
        return true;
    }
    
    /**
     * Validate entities are properly defined
     */
    private static boolean validateEntities(AiAppGeneratorService.GenerationResult result) {
        if (result.entities == null || result.entities.isEmpty()) {
            LOG.warn("[AI Validation] ✗ No entities generated");
            return false;
        }
        
        // Validate each entity has name and fields
        for (EntitySchema entity : result.entities) {
            if (entity.getName() == null || entity.getName().isBlank()) {
                LOG.warn("[AI Validation] ✗ Entity has no name");
                return false;
            }
            
            if (entity.getFields() == null || entity.getFields().isEmpty()) {
                LOG.warn("[AI Validation] ✗ Entity '{}' has no fields", entity.getName());
                return false;
            }
            
            // Validate fields have proper types
            for (EntitySchema.Field field : entity.getFields()) {
                if (field.getType() == null || field.getType().isBlank()) {
                    LOG.warn("[AI Validation] ✗ Entity '{}' field '{}' has no type", 
                        entity.getName(), field.getName());
                    return false;
                }
            }
        }
        
        LOG.info("[AI Validation] ✓ {} entities validated", result.entities.size());
        return true;
    }
    
    /**
     * Validate pages are defined (prefer detailed pages over suggested)
     */
    private static boolean validatePages(AiAppGeneratorService.GenerationResult result) {
        // Prefer detailed pages metadata
        if (result.pages != null && !result.pages.isEmpty()) {
            LOG.info("[AI Validation] ✓ {} detailed pages with metadata provided", result.pages.size());
            return true;
        }
        
        // Fall back to suggestedPages (less ideal but acceptable)
        if (result.suggestedPages != null && !result.suggestedPages.isEmpty()) {
            LOG.info("[AI Validation] ⚠ {} suggested pages (no metadata), consider requesting detailed pages", 
                result.suggestedPages.size());
            return true;
        }
        
        LOG.warn("[AI Validation] ✗ No pages (detailed or suggested) generated");
        return false;
    }
}
