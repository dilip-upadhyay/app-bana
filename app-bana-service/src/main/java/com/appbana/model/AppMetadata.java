package com.appbana.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * Application metadata model
 * Represents a complete AppBana application with pages, entities, and settings
 * Stored as JSON files in app-bana-service/apps/{appId}/app.json
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AppMetadata {
    private String id;
    private String name;
    private String description;
    private String version;
    private String author;
    private Long created;
    private Long updated;

    // Pages
    private List<String> pages;
    private String defaultPage;

    // Entities
    private List<Object> entities;  // EntityMeta - will be Map for now
    private List<String> schemas;

    // Navigation
    private Object navigation;  // NavigationMeta

    // Settings
    private AppTheme theme;
    private AppRoutes routes;
    private Map<String, Object> metadata;

    public AppMetadata() {}

    public AppMetadata(String id, String name, String version) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.created = System.currentTimeMillis();
        this.updated = System.currentTimeMillis();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public Long getCreated() { return created; }
    public void setCreated(Long created) { this.created = created; }

    public Long getUpdated() { return updated; }
    public void setUpdated(Long updated) { this.updated = updated; }

    public List<String> getPages() { return pages; }
    public void setPages(List<String> pages) { this.pages = pages; }

    public String getDefaultPage() { return defaultPage; }
    public void setDefaultPage(String defaultPage) { this.defaultPage = defaultPage; }

    public List<Object> getEntities() { return entities; }
    public void setEntities(List<Object> entities) { this.entities = entities; }

    public List<String> getSchemas() { return schemas; }
    public void setSchemas(List<String> schemas) { this.schemas = schemas; }

    public Object getNavigation() { return navigation; }
    public void setNavigation(Object navigation) { this.navigation = navigation; }

    public AppTheme getTheme() { return theme; }
    public void setTheme(AppTheme theme) { this.theme = theme; }

    public AppRoutes getRoutes() { return routes; }
    public void setRoutes(AppRoutes routes) { this.routes = routes; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    /**
     * App theme configuration
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AppTheme {
        private String primaryColor;
        private String secondaryColor;
        private String fontFamily;
        private Boolean darkMode;
        private String customCSS;

        public String getPrimaryColor() { return primaryColor; }
        public void setPrimaryColor(String primaryColor) { this.primaryColor = primaryColor; }

        public String getSecondaryColor() { return secondaryColor; }
        public void setSecondaryColor(String secondaryColor) { this.secondaryColor = secondaryColor; }

        public String getFontFamily() { return fontFamily; }
        public void setFontFamily(String fontFamily) { this.fontFamily = fontFamily; }

        public Boolean getDarkMode() { return darkMode; }
        public void setDarkMode(Boolean darkMode) { this.darkMode = darkMode; }

        public String getCustomCSS() { return customCSS; }
        public void setCustomCSS(String customCSS) { this.customCSS = customCSS; }
    }

    /**
     * App routing configuration
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AppRoutes {
        private String basePath;
        private Map<String, String> pageRoutes;

        public String getBasePath() { return basePath; }
        public void setBasePath(String basePath) { this.basePath = basePath; }

        public Map<String, String> getPageRoutes() { return pageRoutes; }
        public void setPageRoutes(Map<String, String> pageRoutes) { this.pageRoutes = pageRoutes; }
    }
}
