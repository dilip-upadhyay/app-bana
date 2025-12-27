package com.appbana.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for managing page templates (system and user-defined)
 */
public class TemplateService {

    private static final String SYSTEM_TEMPLATES_PATH = "/page-templates";
    private static final String USER_TEMPLATES_DIR = "user-templates";
    private final ObjectMapper objectMapper;
    private final String dataDir;

    public TemplateService(String dataDir) {
        this.dataDir = dataDir;
        this.objectMapper = new ObjectMapper();
        ensureUserTemplatesDir();
    }

    private void ensureUserTemplatesDir() {
        try {
            Path userTemplatesPath = Paths.get(dataDir, USER_TEMPLATES_DIR);
            if (!Files.exists(userTemplatesPath)) {
                Files.createDirectories(userTemplatesPath);
            }
        } catch (Exception e) {
            System.err.println("Failed to create user templates directory: " + e.getMessage());
        }
    }

    /**
     * Get all templates (system + user)
     */
    public List<Map<String, Object>> getAllTemplates() {
        List<Map<String, Object>> templates = new ArrayList<>();

        // Load system templates from resources
        templates.addAll(loadSystemTemplates());

        // Load user templates from file system
        templates.addAll(loadUserTemplates());

        return templates;
    }

    /**
     * Get a specific template by ID
     */
    public Map<String, Object> getTemplate(String templateId) {
        // First check system templates
        Map<String, Object> systemTemplate = loadSystemTemplate(templateId);
        if (systemTemplate != null) {
            return systemTemplate;
        }

        // Then check user templates
        return loadUserTemplate(templateId);
    }

    /**
     * Create a new user template
     */
    public Map<String, Object> createUserTemplate(Map<String, Object> template) throws Exception {
        if (!template.containsKey("id") || !template.containsKey("name")) {
            throw new IllegalArgumentException("Template must have 'id' and 'name' fields");
        }

        String templateId = (String) template.get("id");

        // Ensure it's marked as user template
        template.put("isSystem", false);

        // Save to file system
        Path templatePath = Paths.get(dataDir, USER_TEMPLATES_DIR, templateId + ".json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(templatePath.toFile(), template);

        return template;
    }

    /**
     * Update an existing user template
     */
    public Map<String, Object> updateUserTemplate(String templateId, Map<String, Object> template) throws Exception {
        // Check if it's a system template
        Map<String, Object> existing = loadSystemTemplate(templateId);
        if (existing != null) {
            throw new IllegalArgumentException("Cannot update system template: " + templateId);
        }

        // Ensure ID matches
        template.put("id", templateId);
        template.put("isSystem", false);

        // Save to file system
        Path templatePath = Paths.get(dataDir, USER_TEMPLATES_DIR, templateId + ".json");
        if (!Files.exists(templatePath)) {
            throw new IllegalArgumentException("Template not found: " + templateId);
        }

        objectMapper.writerWithDefaultPrettyPrinter().writeValue(templatePath.toFile(), template);

        return template;
    }

    /**
     * Delete a user template
     */
    public void deleteUserTemplate(String templateId) throws Exception {
        // Check if it's a system template
        Map<String, Object> existing = loadSystemTemplate(templateId);
        if (existing != null) {
            throw new IllegalArgumentException("Cannot delete system template: " + templateId);
        }

        Path templatePath = Paths.get(dataDir, USER_TEMPLATES_DIR, templateId + ".json");
        if (!Files.exists(templatePath)) {
            throw new IllegalArgumentException("Template not found: " + templateId);
        }

        Files.delete(templatePath);
    }

    // Private helper methods

    private List<Map<String, Object>> loadSystemTemplates() {
        List<Map<String, Object>> templates = new ArrayList<>();
        String[] systemTemplateIds = { "login", "signup", "dashboard", "contact", "landing", "profile", "data-table" };

        for (String templateId : systemTemplateIds) {
            Map<String, Object> template = loadSystemTemplate(templateId);
            if (template != null) {
                templates.add(template);
            }
        }

        return templates;
    }

    private Map<String, Object> loadSystemTemplate(String templateId) {
        try {
            String resourcePath = SYSTEM_TEMPLATES_PATH + "/" + templateId + ".json";
            InputStream is = getClass().getResourceAsStream(resourcePath);

            if (is == null) {
                return null;
            }

            return objectMapper.readValue(is, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            System.err.println("Failed to load system template " + templateId + ": " + e.getMessage());
            return null;
        }
    }

    private List<Map<String, Object>> loadUserTemplates() {
        List<Map<String, Object>> templates = new ArrayList<>();

        try {
            Path userTemplatesPath = Paths.get(dataDir, USER_TEMPLATES_DIR);
            if (!Files.exists(userTemplatesPath)) {
                return templates;
            }

            Files.list(userTemplatesPath)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            Map<String, Object> template = objectMapper.readValue(path.toFile(),
                                    new TypeReference<Map<String, Object>>() {
                                    });
                            templates.add(template);
                        } catch (Exception e) {
                            System.err.println("Failed to load user template from " + path + ": " + e.getMessage());
                        }
                    });
        } catch (Exception e) {
            System.err.println("Failed to load user templates: " + e.getMessage());
        }

        return templates;
    }

    private Map<String, Object> loadUserTemplate(String templateId) {
        try {
            Path templatePath = Paths.get(dataDir, USER_TEMPLATES_DIR, templateId + ".json");
            if (!Files.exists(templatePath)) {
                return null;
            }

            return objectMapper.readValue(templatePath.toFile(), new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            System.err.println("Failed to load user template " + templateId + ": " + e.getMessage());
            return null;
        }
    }
}
