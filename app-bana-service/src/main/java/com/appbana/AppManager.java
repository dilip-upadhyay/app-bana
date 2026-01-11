package com.appbana;

import com.appbana.model.AppMetadata;
import com.appbana.model.EntitySchema;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * AppManager - Manages application metadata persistence via Database (V9
 * Migration)
 * 
 * Apps are stored in: appbana_apps table
 * Pages are stored in: appbana_pages table
 */
public class AppManager {
    private static final Logger LOG = LoggerFactory.getLogger(AppManager.class);
    private static final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private AppManager() {
        // Private constructor
    }

    /**
     * Initialize - Ensure database tables exist
     */
    public static void initialize() {
        LOG.info("[AppManager] Initializing database persistence layer...");
        // Tables are now created by Flyway migrations (V1-V11)
        // ensureTables(); // Removed - Flyway handles table creation
        LOG.info("[AppManager] Database tables managed by Flyway");
    }

    private static void ensureTables() {
        String createAppsSql = "CREATE TABLE IF NOT EXISTS appbana_apps (" +
                "id VARCHAR(100) NOT NULL, " +
                "tenant_id VARCHAR(50) DEFAULT 'default', " +
                "name VARCHAR(255), " +
                "description CLOB, " +
                "version VARCHAR(50), " +
                "author VARCHAR(100), " +
                "created_at BIGINT, " +
                "updated_at BIGINT, " +
                "json_metadata CLOB, " +
                "PRIMARY KEY (id, tenant_id))";

        String createPagesSql = "CREATE TABLE IF NOT EXISTS appbana_pages (" +
                "id VARCHAR(100) NOT NULL, " +
                "app_id VARCHAR(100) NOT NULL, " +
                "tenant_id VARCHAR(50) DEFAULT 'default', " +
                "name VARCHAR(255), " +
                "type VARCHAR(50), " +
                "json_metadata CLOB, " +
                "updated_at BIGINT, " +
                "PRIMARY KEY (id, app_id, tenant_id))";

        String createWorkflowsSql = "CREATE TABLE IF NOT EXISTS appbana_app_workflows (" +
                "app_id VARCHAR(100) NOT NULL, " +
                "tenant_id VARCHAR(50) DEFAULT 'default', " +
                "json_metadata CLOB, " +
                "updated_at BIGINT, " +
                "PRIMARY KEY (app_id, tenant_id))";

        try (Connection conn = JdbcManager.getConnection();
                java.sql.Statement s = conn.createStatement()) {
            s.execute(createAppsSql);
            s.execute(createPagesSql);
            s.execute(createWorkflowsSql);
            LOG.info("[AppManager] Database tables verified");
        } catch (SQLException e) {
            LOG.error("[AppManager] Failed to ensure database tables", e);
            throw new RuntimeException("DB Initialization failed", e);
        }
    }

    /**
     * Get workflow metadata
     */
    public static Map<String, Object> getWorkflow(String tenantId, String appId) throws IOException {
        if (tenantId == null)
            tenantId = "default";
        String sql = "SELECT json_metadata FROM appbana_app_workflows WHERE tenant_id = ? AND app_id = ?";

        try (Connection conn = JdbcManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, tenantId);
            ps.setString(2, appId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String json = rs.getString("json_metadata");
                    if (json != null) {
                        return mapper.readValue(json, new TypeReference<Map<String, Object>>() {
                        });
                    }
                }
            }
        } catch (SQLException e) {
            throw new IOException("Failed to get workflow for app: " + appId, e);
        }
        return null;
    }

    /**
     * Save workflow metadata
     */
    public static void saveWorkflow(String tenantId, String appId, Map<String, Object> workflow) throws IOException {
        if (tenantId == null)
            tenantId = "default";

        // Ensure app exists
        if (getApp(tenantId, appId) == null) {
            throw new IllegalStateException("App does not exist: " + appId);
        }

        // Use PostgreSQL's INSERT ... ON CONFLICT syntax (upsert)
        String sql = "INSERT INTO appbana_app_workflows (app_id, tenant_id, json_metadata, updated_at) " +
                "VALUES (?, ?, ?, ?) " +
                "ON CONFLICT (app_id, tenant_id) DO UPDATE SET " +
                "json_metadata = EXCLUDED.json_metadata, " +
                "updated_at = EXCLUDED.updated_at";

        try (Connection conn = JdbcManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, appId);
            ps.setString(2, tenantId);
            ps.setString(3, mapper.writeValueAsString(workflow));
            ps.setLong(4, System.currentTimeMillis());

            ps.executeUpdate();
            LOG.info("[AppManager] Saved workflow for app: {}", appId);
        } catch (SQLException e) {
            throw new IOException("Failed to save workflow for app: " + appId, e);
        }
    }

    /**
     * List all apps for a tenant
     */
    public static List<Map<String, Object>> listApps(String tenantId) throws IOException {
        if (tenantId == null)
            tenantId = "default";
        List<Map<String, Object>> apps = new ArrayList<>();

        String sql = "SELECT json_metadata FROM appbana_apps WHERE tenant_id = ?";

        try (Connection conn = JdbcManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String json = rs.getString("json_metadata");
                    if (json != null) {
                        try {
                            AppMetadata app = mapper.readValue(json, AppMetadata.class);
                            Map<String, Object> summary = new HashMap<>();
                            summary.put("id", app.getId());
                            summary.put("name", app.getName());
                            summary.put("description", app.getDescription());
                            summary.put("version", app.getVersion());
                            summary.put("created", app.getCreated());
                            summary.put("updated", app.getUpdated());
                            summary.put("pageCount", app.getPages() != null ? app.getPages().size() : 0);
                            apps.add(summary);
                        } catch (Exception e) {
                            LOG.error("[AppManager] Failed to parse app metadata in DB", e);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            throw new IOException("Failed to list apps", e);
        }
        return apps;
    }

    /**
     * Get app metadata by ID
     */
    public static AppMetadata getApp(String tenantId, String appId) throws IOException {
        if (tenantId == null)
            tenantId = "default";
        String sql = "SELECT json_metadata FROM appbana_apps WHERE tenant_id = ? AND id = ?";

        try (Connection conn = JdbcManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, tenantId);
            ps.setString(2, appId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String json = rs.getString("json_metadata");
                    if (json != null) {
                        return mapper.readValue(json, AppMetadata.class);
                    }
                }
            }
        } catch (SQLException e) {
            throw new IOException("Failed to get app: " + appId, e);
        }
        return null;
    }

    /**
     * Get app with all its pages
     */
    public static Map<String, Object> getAppWithPages(String tenantId, String appId) throws IOException {
        AppMetadata app = getApp(tenantId, appId);
        if (app == null)
            return null;

        Map<String, Object> appMap = mapper.convertValue(app, new TypeReference<Map<String, Object>>() {
        });

        // Load pages
        List<Map<String, Object>> pages = new ArrayList<>();
        String sql = "SELECT json_metadata FROM appbana_pages WHERE tenant_id = ? AND app_id = ?";

        try (Connection conn = JdbcManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, tenantId == null ? "default" : tenantId);
            ps.setString(2, appId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String json = rs.getString("json_metadata");
                    if (json != null) {
                        pages.add(mapper.readValue(json, new TypeReference<Map<String, Object>>() {
                        }));
                    }
                }
            }
        } catch (SQLException e) {
            LOG.warn("Failed to load pages for app {}", appId, e);
        }

        appMap.put("pages", pages);
        return appMap;
    }

    /**
     * Get app with full metadata including hydrated entities from schemas
     * Used by publish endpoint to fetch complete app data from DB
     */
    public static AppMetadata getAppFullMetadata(String tenantId, String appId) throws IOException {
        AppMetadata app = getApp(tenantId, appId);
        if (app == null) {
            return null;
        }

        // Hydrate entities from schema names
        if (app.getSchemas() != null && !app.getSchemas().isEmpty()) {
            List<Object> hydratedEntities = new ArrayList<>();

            for (String schemaName : app.getSchemas()) {
                try {
                    // Load schema from database
                    EntitySchema schema = SchemaManager.loadSchema(appId, schemaName, tenantId);
                    if (schema == null) {
                        LOG.warn("[AppManager] Schema not found: {} for app {}", schemaName, appId);
                        continue;
                    }

                    // Apply mandatory defaults to ensure data integrity
                    if (schema.getFields() != null) {
                        for (EntitySchema.Field field : schema.getFields()) {
                            // 1. Length (default 255 if missing/invalid)
                            if (field.getLength() == null || field.getLength() <= 0) {
                                field.setLength(255);
                            }

                            // 2. Label (auto-generate if missing)
                            if (field.getLabel() == null || field.getLabel().isEmpty()) {
                                String name = field.getName();
                                if (name != null && !name.isEmpty()) {
                                    String label = name.substring(0, 1).toUpperCase() +
                                            name.substring(1).replaceAll("([A-Z])", " $1").trim();
                                    field.setLabel(label);
                                }
                            }
                        }
                    }

                    hydratedEntities.add(schema);
                    LOG.debug("[AppManager] Hydrated schema: {}", schemaName);
                } catch (Exception e) {
                    LOG.error("[AppManager] Failed to hydrate schema: {}", schemaName, e);
                }
            }

            app.setEntities(hydratedEntities);
            LOG.info("[AppManager] Hydrated {} entities for app {}", hydratedEntities.size(), appId);
        }

        // Hydrate pages for publication
        try {
            String sqlPages = "SELECT json_metadata FROM appbana_pages WHERE tenant_id = ? AND app_id = ?";
            try (Connection conn = JdbcManager.getConnection();
                    PreparedStatement ps = conn.prepareStatement(sqlPages)) {

                ps.setString(1, tenantId == null ? "default" : tenantId);
                ps.setString(2, appId);

                List<String> pageIds = new ArrayList<>();
                List<Object> pagesData = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String json = rs.getString("json_metadata");
                        if (json != null) {
                            Map<String, Object> pageData = mapper.readValue(json,
                                    new TypeReference<Map<String, Object>>() {
                                    });
                            pagesData.add(pageData);
                            // Extract page ID for backward compatibility
                            String pageId = (String) pageData.get("id");
                            if (pageId != null) {
                                pageIds.add(pageId);
                            }
                        }
                    }
                }
                app.setPages(pageIds); // Set IDs for frontend compatibility
                app.setPagesData(pagesData); // Set full objects for optimization
                LOG.info("[AppManager] Hydrated {} pages for app {} (IDs: {}, Data: {})",
                        pageIds.size(), appId, pageIds.size(), pagesData.size());
            }
        } catch (Exception e) {
            LOG.error("[AppManager] Failed to hydrate pages for app {}", appId, e);
        }

        return app;
    }

    /**
     * Create a new app
     */
    public static AppMetadata createApp(String tenantId, AppMetadata app) throws IOException {
        if (tenantId == null)
            tenantId = "default";
        if (app.getId() == null || app.getId().isEmpty()) {
            throw new IllegalArgumentException("App ID is required");
        }

        if (getApp(tenantId, app.getId()) != null) {
            throw new IllegalStateException("App already exists: " + app.getId());
        }

        long now = System.currentTimeMillis();
        if (app.getCreated() == null)
            app.setCreated(now);
        app.setUpdated(now);
        if (app.getVersion() == null)
            app.setVersion("1.0.0");

        saveApp(tenantId, app);

        if (app.getEntities() != null && !app.getEntities().isEmpty()) {
            registerAppEntities(app);
        }

        LOG.info("[AppManager] Created app: {}", app.getId());
        return app;
    }

    /**
     * Update app metadata
     */
    public static AppMetadata updateApp(String tenantId, String appId, AppMetadata updates) throws IOException {
        AppMetadata existing = getApp(tenantId, appId);
        if (existing == null) {
            throw new IllegalArgumentException("App not found: " + appId);
        }

        // Apply updates
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
        saveApp(tenantId, existing);
        return existing;
    }

    /**
     * Delete an app and all its pages
     */
    public static boolean deleteApp(String tenantId, String appId) throws IOException {
        if (tenantId == null)
            tenantId = "default";

        String deletePagesSql = "DELETE FROM appbana_pages WHERE tenant_id = ? AND app_id = ?";
        String deleteAppSql = "DELETE FROM appbana_apps WHERE tenant_id = ? AND id = ?";

        try (Connection conn = JdbcManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Delete pages
                try (PreparedStatement ps1 = conn.prepareStatement(deletePagesSql)) {
                    ps1.setString(1, tenantId);
                    ps1.setString(2, appId);
                    ps1.executeUpdate();
                }

                // Delete app
                try (PreparedStatement ps2 = conn.prepareStatement(deleteAppSql)) {
                    ps2.setString(1, tenantId);
                    ps2.setString(2, appId);
                    int rows = ps2.executeUpdate();
                    if (rows == 0)
                        return false;
                }

                conn.commit();
                LOG.info("[AppManager] Deleted app: {}", appId);
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IOException("Failed to delete app: " + appId, e);
        }
    }

    /**
     * Save app metadata to DB
     */
    private static void saveApp(String tenantId, AppMetadata app) throws IOException {
        if (tenantId == null)
            tenantId = "default";

        // Use PostgreSQL's INSERT ... ON CONFLICT syntax (upsert)
        String sql = "INSERT INTO appbana_apps (id, tenant_id, name, description, version, author, created_at, updated_at, json_metadata) "
                +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (id, tenant_id) DO UPDATE SET " +
                "name = EXCLUDED.name, " +
                "description = EXCLUDED.description, " +
                "version = EXCLUDED.version, " +
                "author = EXCLUDED.author, " +
                "updated_at = EXCLUDED.updated_at, " +
                "json_metadata = EXCLUDED.json_metadata";

        try (Connection conn = JdbcManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, app.getId());
            ps.setString(2, tenantId);
            ps.setString(3, app.getName());
            ps.setString(4, app.getDescription());
            ps.setString(5, app.getVersion());
            ps.setString(6, app.getAuthor());
            ps.setLong(7, app.getCreated() != null ? app.getCreated() : System.currentTimeMillis());
            ps.setLong(8, app.getUpdated());
            ps.setString(9, mapper.writeValueAsString(app));

            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IOException("Failed to save app: " + app.getId(), e);
        }
    }

    /**
     * Get page metadata
     */
    public static Map<String, Object> getPage(String tenantId, String appId, String pageId) throws IOException {
        if (tenantId == null)
            tenantId = "default";

        String sql = "SELECT json_metadata FROM appbana_pages WHERE tenant_id = ? AND app_id = ? AND id = ?";

        try (Connection conn = JdbcManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, tenantId);
            ps.setString(2, appId);
            ps.setString(3, pageId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String json = rs.getString("json_metadata");
                    if (json != null) {
                        return mapper.readValue(json, new TypeReference<Map<String, Object>>() {
                        });
                    }
                }
            }
        } catch (SQLException e) {
            throw new IOException("Failed to get page: " + pageId, e);
        }
        return null;
    }

    /**
     * Save page metadata
     */
    public static void savePage(String tenantId, String appId, String pageId, Map<String, Object> page)
            throws IOException {
        if (tenantId == null)
            tenantId = "default";

        // Ensure app exists/update page list
        try {
            AppMetadata app = getApp(tenantId, appId);
            if (app != null) {
                if (app.getPages() == null)
                    app.setPages(new ArrayList<>());
                if (!app.getPages().contains(pageId)) {
                    app.getPages().add(pageId);
                    app.setUpdated(System.currentTimeMillis());
                    saveApp(tenantId, app); // Update app record
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to check app page registry for {}", pageId, e);
        }

        // Use PostgreSQL's INSERT ... ON CONFLICT syntax (upsert)
        String sql = "INSERT INTO appbana_pages (id, app_id, tenant_id, name, type, json_metadata, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (id, app_id, tenant_id) DO UPDATE SET " +
                "name = EXCLUDED.name, " +
                "type = EXCLUDED.type, " +
                "json_metadata = EXCLUDED.json_metadata, " +
                "updated_at = EXCLUDED.updated_at";

        try (Connection conn = JdbcManager.getConnection()) {
            // Disable auto-commit to control transaction explicitly
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                String name = (String) page.get("name");
                String type = (String) page.get("type");

                ps.setString(1, pageId);
                ps.setString(2, appId);
                ps.setString(3, tenantId);
                ps.setString(4, name);
                ps.setString(5, type);
                ps.setString(6, mapper.writeValueAsString(page));
                ps.setLong(7, System.currentTimeMillis());

                ps.executeUpdate();

                // CRITICAL: Explicitly commit the transaction to ensure data is persisted
                // before this method returns. This prevents race conditions where
                // createVersion() might read stale data.
                conn.commit();

                LOG.info("[AppManager] Saved and committed page: {}/{}", appId, pageId);
            } catch (SQLException e) {
                // Rollback on error
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IOException("Failed to save page: " + pageId, e);
        }
    }

    /**
     * Delete page
     */
    public static boolean deletePage(String tenantId, String appId, String pageId) throws IOException {
        if (tenantId == null)
            tenantId = "default";

        String sql = "DELETE FROM appbana_pages WHERE tenant_id = ? AND app_id = ? AND id = ?";

        try (Connection conn = JdbcManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, tenantId);
            ps.setString(2, appId);
            ps.setString(3, pageId);

            int rows = ps.executeUpdate();
            if (rows > 0) {
                // Remove from app's page list
                try {
                    AppMetadata app = getApp(tenantId, appId);
                    if (app != null && app.getPages() != null && app.getPages().contains(pageId)) {
                        app.getPages().remove(pageId);
                        app.setUpdated(System.currentTimeMillis());
                        saveApp(tenantId, app);
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to update app page registry after delete", e);
                }
                return true;
            }
        } catch (SQLException e) {
            throw new IOException("Failed to delete page: " + pageId, e);
        }
        return false;
    }

    // Keep registerAppEntities as it logic is sound
    private static void registerAppEntities(AppMetadata app) {
        if (app.getEntities() == null)
            return;
        for (Object entityObj : app.getEntities()) {
            try {
                EntitySchema schema = mapper.convertValue(entityObj, EntitySchema.class);
                if (schema.getAppId() == null) {
                    schema.setAppId(app.getId());
                }
                if (schema.getTenantId() == null) {
                    schema.setTenantId(app.getTenantId() != null ? app.getTenantId() : "default");
                }
                SchemaManager.saveSchema(schema);
                LOG.info("[AppManager] Registered entity schema: {}", schema.getName());
            } catch (Exception e) {
                LOG.error("[AppManager] Failed to register entity: {}", e.getMessage());
            }
        }
    }

}
