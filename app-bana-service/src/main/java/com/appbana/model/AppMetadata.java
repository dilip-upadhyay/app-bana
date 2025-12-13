package com.appbana.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * Application metadata model
 * Represents a complete AppBana application with pages, entities, and settings
 * Stored as JSON files in app-bana-service/apps/{appId}/app.json
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
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
    private List<Object> entities; // EntityMeta - will be Map for now
    private List<String> schemas;

    // Navigation
    private Object navigation; // NavigationMeta

    // Settings
    private AppTheme theme;
    private AppRoutes routes;
    private Map<String, Object> metadata;

    public AppMetadata() {
    }

    public AppMetadata(String id, String name, String version) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.created = System.currentTimeMillis();
        this.updated = System.currentTimeMillis();
    }

    /**
     * App theme configuration
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static class AppTheme {
        private String primaryColor;
        private String secondaryColor;
        private String fontFamily;
        private Boolean darkMode;
        private String customCSS;
        private String surfaceColor;
        private String textColor;
    }

    /**
     * App routing configuration
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static class AppRoutes {
        private String basePath;
        private Map<String, String> pageRoutes;
    }
}
