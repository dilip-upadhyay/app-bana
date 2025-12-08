package com.appbana;

import com.appbana.model.AppMetadata;
import com.appbana.model.EntitySchema;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * AppManager - Manages application metadata persistence
 * 
 * Apps are stored in: app-bana-service/apps/{appId}/app.json
 * Pages are stored in: app-bana-service/apps/{appId}/pages/{pageId}.json
 */
public class AppManager {
    private static final String APPS_DIR = "apps";
    private static final String APP_FILE = "app.json";
    private static final String WORKFLOW_FILE = "workflow.json";
    private static final String PAGES_DIR = "pages";
    private static final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private AppManager() {
        // Private constructor to prevent instantiation
    }

    /**
     * Get the apps directory path
     */
    private static Path getAppsDirectory() {
        return Paths.get(APPS_DIR);
    }

    /**
     * Get app directory path
     */
    private static Path getAppDirectory(String appId) {
        return getAppsDirectory().resolve(appId);
    }

    /**
     * Get app metadata file path
     */
    private static Path getAppMetadataPath(String appId) {
        return getAppDirectory(appId).resolve(APP_FILE);
    }

    /**
     * Get pages directory for an app
     */
    private static Path getPagesDirectory(String appId) {
        return getAppDirectory(appId).resolve(PAGES_DIR);
    }

    /**
     * Get page file path
     */
    private static Path getPagePath(String appId, String pageId) {
        return getPagesDirectory(appId).resolve(pageId + ".json");
    }

    /**
     * Initialize apps directory if it doesn't exist
     */
    public static void initialize() {
        try {
            Files.createDirectories(getAppsDirectory());
            System.out.println("[AppManager] Apps directory initialized: " + getAppsDirectory().toAbsolutePath());
        } catch (IOException e) {
            System.err.println("[AppManager] Failed to initialize apps directory: " + e.getMessage());
        }
    }

    /**
     * List all apps (returns summary info)
     */
    public static List<Map<String, Object>> listApps() throws IOException {
        List<Map<String, Object>> apps = new ArrayList<>();
        Path appsDir = getAppsDirectory();

        if (!Files.exists(appsDir)) {
            return apps;
        }

        Files.list(appsDir)
                .filter(Files::isDirectory)
                .forEach(appDir -> {
                    try {
                        Path appFile = appDir.resolve(APP_FILE);
                        if (Files.exists(appFile)) {
                            AppMetadata app = mapper.readValue(appFile.toFile(), AppMetadata.class);
                            Map<String, Object> summary = new HashMap<>();
                            summary.put("id", app.getId());
                            summary.put("name", app.getName());
                            summary.put("description", app.getDescription());
                            summary.put("version", app.getVersion());
                            summary.put("created", app.getCreated());
                            summary.put("updated", app.getUpdated());
                            summary.put("pageCount", app.getPages() != null ? app.getPages().size() : 0);
                            apps.add(summary);
                        }
                    } catch (IOException e) {
                        System.err.println("[AppManager] Failed to read app: " + appDir.getFileName());
                    }
                });

        return apps;
    }

    /**
     * Get app metadata by ID
     */
    public static AppMetadata getApp(String appId) throws IOException {
        Path appFile = getAppMetadataPath(appId);
        if (!Files.exists(appFile)) {
            return null;
        }
        return mapper.readValue(appFile.toFile(), AppMetadata.class);
    }

    /**
     * Get app with all its pages
     */
    public static Map<String, Object> getAppWithPages(String appId) throws IOException {
        AppMetadata app = getApp(appId);
        if (app == null) {
            return null;
        }
        // Convert AppMetadata to Map
        Map<String, Object> appMap = mapper.convertValue(app, Map.class);

        // Load all pages
        List<Map<String, Object>> pages = new ArrayList<>();
        Path pagesDir = getPagesDirectory(appId);
        if (Files.exists(pagesDir)) {
            Files.list(pagesDir)
                    .filter(p -> p.toString().endsWith(".json"))
                    .forEach(pageFile -> {
                        try {
                            Map<String, Object> page = mapper.readValue(pageFile.toFile(), Map.class);
                            pages.add(page);
                        } catch (IOException e) {
                            System.err.println("[AppManager] Failed to read page: " + pageFile.getFileName());
                        }
                    });
        }
        appMap.put("pages", pages);
        return appMap;
    }

    /**
     * Create a new app
     */
    public static AppMetadata createApp(AppMetadata app) throws IOException {
        if (app.getId() == null || app.getId().isEmpty()) {
            throw new IllegalArgumentException("App ID is required");
        }

        Path appDir = getAppDirectory(app.getId());
        if (Files.exists(appDir)) {
            throw new IllegalStateException("App already exists: " + app.getId());
        }

        // Set timestamps
        long now = System.currentTimeMillis();
        if (app.getCreated() == null) {
            app.setCreated(now);
        }
        app.setUpdated(now);

        // Default version
        if (app.getVersion() == null) {
            app.setVersion("1.0.0");
        }

        // Create directory structure
        Files.createDirectories(appDir);
        Files.createDirectories(getPagesDirectory(app.getId()));

        // Save app metadata
        saveApp(app);

        // Register entities as schemas so API endpoints work
        if (app.getEntities() != null && !app.getEntities().isEmpty()) {
            registerAppEntities(app);
        }

        System.out.println("[AppManager] Created app: " + app.getId());
        return app;
    }

    /**
     * Register all entities from an app as database schemas
     */
    private static void registerAppEntities(AppMetadata app) {
        if (app.getEntities() == null) {
            return;
        }

        for (Object entityObj : app.getEntities()) {
            try {
                // Convert entity object to EntitySchema
                EntitySchema schema = mapper.convertValue(entityObj, EntitySchema.class);
                SchemaManager.saveSchema(schema);
                System.out.println("[AppManager] Registered entity schema: " + schema.getName());
            } catch (Exception e) {
                System.err.println("[AppManager] Failed to register entity: " + e.getMessage());
            }
        }
    }

    /**
     * Update app metadata
     */
    public static AppMetadata updateApp(String appId, AppMetadata updates) throws IOException {
        AppMetadata existing = getApp(appId);
        if (existing == null) {
            throw new IllegalArgumentException("App not found: " + appId);
        }

        // Update fields
        if (updates.getName() != null)
            existing.setName(updates.getName());
        if (updates.getDescription() != null)
            existing.setDescription(updates.getDescription());
        if (updates.getVersion() != null)
            existing.setVersion(updates.getVersion());
        if (updates.getAuthor() != null)
            existing.setAuthor(updates.getAuthor());
        if (updates.getPages() != null)
            existing.setPages(updates.getPages());
        if (updates.getDefaultPage() != null)
            existing.setDefaultPage(updates.getDefaultPage());
        if (updates.getEntities() != null)
            existing.setEntities(updates.getEntities());
        if (updates.getSchemas() != null)
            existing.setSchemas(updates.getSchemas());
        if (updates.getNavigation() != null)
            existing.setNavigation(updates.getNavigation());
        if (updates.getTheme() != null)
            existing.setTheme(updates.getTheme());
        if (updates.getRoutes() != null)
            existing.setRoutes(updates.getRoutes());
        if (updates.getMetadata() != null)
            existing.setMetadata(updates.getMetadata());

        existing.setUpdated(System.currentTimeMillis());

        saveApp(existing);
        return existing;
    }

    /**
     * Delete an app and all its pages
     */
    public static boolean deleteApp(String appId) throws IOException {
        Path appDir = getAppDirectory(appId);
        if (!Files.exists(appDir)) {
            return false;
        }

        // Delete recursively
        deleteDirectory(appDir.toFile());
        System.out.println("[AppManager] Deleted app: " + appId);
        return true;
    }

    /**
     * Save app metadata to file
     */
    private static void saveApp(AppMetadata app) throws IOException {
        Path appFile = getAppMetadataPath(app.getId());
        mapper.writeValue(appFile.toFile(), app);
    }

    /**
     * Get page metadata
     */
    public static Map<String, Object> getPage(String appId, String pageId) throws IOException {
        Path pageFile = getPagePath(appId, pageId);
        if (!Files.exists(pageFile)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> page = mapper.readValue(pageFile.toFile(), Map.class);
        return page;
    }

    /**
     * Save page metadata
     * Automatically adds page to app's pages array if not already present
     */
    public static void savePage(String appId, String pageId, Map<String, Object> page) throws IOException {
        Path pagesDir = getPagesDirectory(appId);
        Files.createDirectories(pagesDir);

        Path pageFile = getPagePath(appId, pageId);
        mapper.writeValue(pageFile.toFile(), page);

        // Auto-update app's pages array (if page not already in list)
        try {
            AppMetadata app = getApp(appId);
            if (app != null && !app.getPages().contains(pageId)) {
                app.getPages().add(pageId);
                app.setUpdated(System.currentTimeMillis());
                saveApp(app);
                System.out.println("[AppManager] Added page '" + pageId + "' to app '" + appId + "' pages list");
            }
        } catch (Exception e) {
            System.err.println("[AppManager] Warning: Failed to update app pages list: " + e.getMessage());
            // Continue - page file was saved successfully
        }

        System.out.println("[AppManager] Saved page: " + appId + "/" + pageId);
    }

    /**
     * Delete page
     * Automatically removes page from app's pages array
     */
    public static boolean deletePage(String appId, String pageId) throws IOException {
        Path pageFile = getPagePath(appId, pageId);
        if (!Files.exists(pageFile)) {
            return false;
        }
        Files.delete(pageFile);

        // Auto-update app's pages array (remove deleted page)
        try {
            AppMetadata app = getApp(appId);
            if (app != null && app.getPages().contains(pageId)) {
                app.getPages().remove(pageId);
                app.setUpdated(System.currentTimeMillis());
                saveApp(app);
                System.out.println("[AppManager] Removed page '" + pageId + "' from app '" + appId + "' pages list");
            }
        } catch (Exception e) {
            System.err.println("[AppManager] Warning: Failed to update app pages list: " + e.getMessage());
            // Continue - page file was deleted successfully
        }

        System.out.println("[AppManager] Deleted page: " + appId + "/" + pageId);
        return true;
    }

    /**
     * Get workflow file path
     */
    private static Path getWorkflowPath(String appId) {
        return getAppDirectory(appId).resolve(WORKFLOW_FILE);
    }

    /**
     * Get workflow metadata
     */
    public static Map<String, Object> getWorkflow(String appId) throws IOException {
        Path workflowFile = getWorkflowPath(appId);
        if (!Files.exists(workflowFile)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> workflow = mapper.readValue(workflowFile.toFile(), Map.class);
        return workflow;
    }

    /**
     * Save workflow metadata
     */
    public static void saveWorkflow(String appId, Map<String, Object> workflow) throws IOException {
        Path workflowFile = getWorkflowPath(appId);
        // Ensure app directory exists
        Files.createDirectories(getAppDirectory(appId));

        mapper.writeValue(workflowFile.toFile(), workflow);
        System.out.println("[AppManager] Saved workflow for app: " + appId);
    }

    /**
     * Helper method to delete directory recursively
     */
    private static void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }
}
