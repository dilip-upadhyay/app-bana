package com.appbana.server.routes;

import com.appbana.AppManager;
import com.appbana.JdbcManager;
import com.appbana.SchemaManager;
import com.appbana.api.Router;
import com.appbana.model.AppMetadata;
import com.appbana.model.AppVersion;
import com.appbana.model.DeploymentResult;
import com.appbana.model.TenantContext;
import com.appbana.service.AppPublishService;
import com.appbana.service.AuthService;
import com.appbana.service.ReleaseService;
import com.appbana.service.TemplateService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * App CRUD and management routes
 */
public class AppRoutes {
    private static final Logger LOG = LoggerFactory.getLogger(AppRoutes.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final String DEFAULT_TENANT = "default";

    /**
     * Get tenant ID from TenantContext or use default.
     * For now, we use default tenant for all app operations.
     * In future, this can be extracted from authentication token.
     */
    private static String getTenantId() {
        TenantContext ctx = TenantContext.get();
        return ctx != null ? ctx.getTenantId() : DEFAULT_TENANT;
    }

    public static void register(Router router) {
        ReleaseService releaseService = new ReleaseService();
        String dataDir = Optional.ofNullable(System.getProperty("appbana.dataDir")).orElse("./data");
        TemplateService templateService = new TemplateService(dataDir);

        // ==================== RELEASE MANAGEMENT ====================

        // Publish app to environment (NEW - Phase 3)
        router.post("/api/{tenantId}/apps/{id}/publish", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("id");
            String envParam = req.query("env");

            if (tenantId == null || tenantId.isBlank()) {
                res.json(400, Map.of("error", "tenantId required"));
                return;
            }

            if (envParam == null || envParam.isBlank()) {
                res.json(400, Map.of("error", "env query parameter required (DEV, SIT, or PROD)"));
                return;
            }

            // Parse environment
            AppVersion.Environment environment;
            try {
                environment = AppVersion.Environment.valueOf(envParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                res.json(400, Map.of("error", "Invalid environment: " + envParam + " (must be DEV, SIT, or PROD)"));
                return;
            }

            try {
                // Read AppMeta from request body (optional for backward compatibility)
                Map<String, Object> appMetaMap = null;
                try {
                    appMetaMap = req.readJson(new TypeReference<Map<String, Object>>() {
                    });
                } catch (Exception e) {
                    // Ignore - body might be empty
                }

                // If body is empty/missing, fetch from database
                if (appMetaMap == null || appMetaMap.isEmpty()) {
                    LOG.info("[PUBLISH-ENDPOINT] No body provided, fetching metadata from DB for app {}", appId);
                    AppMetadata metadata = AppManager.getAppFullMetadata(tenantId, appId);
                    if (metadata == null) {
                        res.json(404, Map.of("error", "App not found: " + appId));
                        return;
                    }
                    appMetaMap = MAPPER.convertValue(metadata, new TypeReference<Map<String, Object>>() {
                    });
                    LOG.info("[PUBLISH-ENDPOINT] Loaded app metadata from DB with {} entities",
                            metadata.getEntities() != null ? metadata.getEntities().size() : 0);
                }

                String appMetaJson = MAPPER.writeValueAsString(appMetaMap);

                // Get user ID from auth
                String userId = AuthService.extractUserId(req, com.appbana.config.ConfigManager.getConfig());
                if (userId == null) {
                    userId = "system";
                }

                // Get database connection and initialize publish service
                try (Connection conn = JdbcManager.getConnection()) {
                    AppPublishService publishService = new AppPublishService(conn, new SchemaManager());

                    // Publish app
                    LOG.info("[PUBLISH-ENDPOINT] Publishing app {} to {} for tenant {}", appId, environment, tenantId);
                    DeploymentResult result = publishService.publishApp(appMetaJson, appId, tenantId, environment,
                            userId);

                    if (result.isSuccess()) {
                        LOG.info("[PUBLISH-ENDPOINT] ✅ Publish successful: {}", result.getSummary());
                        res.json(200, Map.of(
                                "success", true,
                                "versionId", result.getVersionId(),
                                "version", result.getVersion(),
                                "environment", result.getEnvironment().name(),
                                "tablesCreated", result.getTablesCreated(),
                                "durationMs", result.getDurationMs(),
                                "summary", result.getSummary()));
                    } else {
                        LOG.error("[PUBLISH-ENDPOINT] ❌ Publish failed: {}", result.getSummary());
                        res.json(500, Map.of(
                                "success", false,
                                "error", result.getErrorMessage(),
                                "details", result.getErrorDetails(),
                                "summary", result.getSummary()));
                    }
                }
            } catch (Exception e) {
                LOG.error("[PUBLISH-ENDPOINT] Exception during publish", e);
                res.json(500, Map.of("error", "Publish failed: " + e.getMessage()));
            }
        });

        // Auto-deploy to LOCAL environment (NEW - for Studio auto-deploy)
        router.put("/api/{tenantId}/apps/{id}/deploy/local", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("id");

            if (tenantId == null || tenantId.isBlank()) {
                res.json(400, Map.of("error", "tenantId required"));
                return;
            }

            try {
                LOG.info("[LOCAL-DEPLOY] Starting LOCAL deployment for app {} (tenant: {})", appId, tenantId);

                // Fetch current app metadata from database
                AppMetadata metadata = AppManager.getAppFullMetadata(tenantId, appId);
                if (metadata == null) {
                    res.json(404, Map.of("error", "App not found: " + appId));
                    return;
                }

                // Convert to JSON
                String appMetaJson = MAPPER.writeValueAsString(metadata);

                // Deploy to LOCAL with sample data
                try (Connection conn = JdbcManager.getConnection()) {
                    AppPublishService publishService = new AppPublishService(conn, new SchemaManager());
                    DeploymentResult result = publishService.publishToLocal(appMetaJson, appId, tenantId);

                    if (result.isSuccess()) {
                        LOG.info("[LOCAL-DEPLOY] ✅ LOCAL deployment successful");
                        res.json(200, Map.of(
                                "success", true,
                                "environment", "LOCAL",
                                "tablesCreated", result.getTablesCreated(),
                                "durationMs", result.getDurationMs(),
                                "message", "Deployed to LOCAL with sample data"));
                    } else {
                        LOG.error("[LOCAL-DEPLOY] ❌ LOCAL deployment failed: {}", result.getErrorMessage());
                        res.json(500, Map.of(
                                "success", false,
                                "error", result.getErrorMessage()));
                    }
                }
            } catch (Exception e) {
                LOG.error("[LOCAL-DEPLOY] Exception during LOCAL deployment", e);
                res.json(500, Map.of("error", "LOCAL deployment failed: " + e.getMessage()));
            }
        });

        // ==================== AI COMMITS & ROLLBACK ====================

        // Create an AI commit snapshot
        router.post("/api/{tenantId}/apps/{id}/commits", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("id");
            if (tenantId == null || tenantId.isBlank()) {
                res.json(400, Map.of("error", "tenantId required"));
                return;
            }

            try {
                Map<String, String> body = req.readJson(new TypeReference<>() {});
                String message = body.getOrDefault("message", "AI Snapshot");
                String userId = AuthService.extractUserId(req, com.appbana.config.ConfigManager.getConfig());
                if (userId == null) userId = "system";

                try (Connection conn = JdbcManager.getConnection()) {
                    AppPublishService publishService = new AppPublishService(conn, new SchemaManager());
                    AppVersion version = publishService.createCommit(tenantId, appId, message, userId);
                    res.json(201, Map.of("version", version.getVersion(), "message", version.getErrorMessage(), "status", "commit_created"));
                }
            } catch (Exception e) {
                LOG.error("Failed to create commit", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // Rollback to a specific AI commit
        router.post("/api/{tenantId}/apps/{id}/commits/rollback", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("id");
            if (tenantId == null || tenantId.isBlank()) {
                res.json(400, Map.of("error", "tenantId required"));
                return;
            }

            try {
                Map<String, Object> body = req.readJson(new TypeReference<>() {});
                if (!body.containsKey("version")) {
                    res.json(400, Map.of("error", "target 'version' number is required"));
                    return;
                }
                int targetVersion = ((Number) body.get("version")).intValue();

                try (Connection conn = JdbcManager.getConnection()) {
                    AppPublishService publishService = new AppPublishService(conn, new SchemaManager());
                    AppVersion newCommit = publishService.rollbackToCommit(tenantId, appId, targetVersion);
                    res.json(200, Map.of("status", "rolled_back", "newCommitVersion", newCommit.getVersion(), "rolledBackTo", targetVersion));
                }
            } catch (Exception e) {
                LOG.error("Failed to rollback", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // Create app version
        router.post("/api/{tenantId}/apps/{id}/versions", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("id");
            if (tenantId == null || tenantId.isBlank()) {
                res.json(400, Map.of("error", "tenantId required"));
                return;
            }
            try {
                Map<String, String> body = req.readJson(new TypeReference<>() {
                });
                String label = body.get("label");
                String desc = body.get("description");
                String userId = AuthService.extractUserId(req, com.appbana.config.ConfigManager.getConfig());

                String versionId = releaseService.createVersion(tenantId, appId, label, desc, userId);
                res.json(201, Map.of("id", versionId, "status", "created"));
            } catch (Exception e) {
                LOG.error("Failed to create version", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // List app versions
        router.get("/api/{tenantId}/apps/{id}/versions", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("id");
            if (tenantId == null || tenantId.isBlank()) {
                res.json(400, Map.of("error", "tenantId required"));
                return;
            }
            try {
                res.json(200, releaseService.listVersions(appId));
            } catch (Exception e) {
                LOG.error("Failed to list versions", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // Deploy version
        router.post("/api/{tenantId}/apps/{id}/deploy/{versionId}", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("id");
            String versionId = req.pathParam("versionId");
            if (tenantId == null || tenantId.isBlank()) {
                res.json(400, Map.of("error", "tenantId required"));
                return;
            }
            try {
                String env = "PROD";

                try {
                    Map<String, String> body = req.readJson(new TypeReference<>() {
                    });
                    if (body != null && body.containsKey("environment")) {
                        env = body.get("environment");
                    }
                } catch (Exception ignored) {
                }

                if (req.query("env") != null) {
                    env = req.query("env");
                }

                String userId = AuthService.extractUserId(req, com.appbana.config.ConfigManager.getConfig());
                releaseService.deployVersion(appId, versionId, userId, env);
                res.json(200, Map.of("status", "deployed", "versionId", versionId, "env", env));
            } catch (Exception e) {
                LOG.error("Failed to deploy version", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // Get pipeline status
        router.get("/api/{tenantId}/apps/{id}/pipeline", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("id");
            if (tenantId == null || tenantId.isBlank()) {
                res.json(400, Map.of("error", "tenantId required"));
                return;
            }
            try {
                res.json(200, releaseService.getPipelineStatus(appId, tenantId));
            } catch (Exception e) {
                LOG.error("Failed to get pipeline status", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // Get app snapshot
        router.get("/api/{tenantId}/apps/{id}/env/{env}/full", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("id");
            String env = req.pathParam("env").toUpperCase();
            if (tenantId == null || tenantId.isBlank()) {
                res.json(400, Map.of("error", "tenantId required"));
                return;
            }
            try {
                Map<String, Object> snapshot = releaseService.getAppSnapshot(appId, env);
                if (snapshot == null) {
                    res.json(404, Map.of("error", "App not deployed to " + env));
                } else {
                    res.json(200, snapshot);
                }
            } catch (Exception e) {
                LOG.error("Failed to get app snapshot", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // Restore schemas from snapshot
        router.post("/api/{tenantId}/apps/{id}/restore-schemas", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("id");
            if (tenantId == null || tenantId.isBlank()) {
                res.json(400, Map.of("error", "tenantId required"));
                return;
            }
            String env = req.query("env");
            if (env == null)
                env = "DEV";

            try {
                Map<String, Object> snapshot = releaseService.getAppSnapshot(appId, env);
                if (snapshot == null) {
                    res.json(404, Map.of("error", "App not deployed to " + env));
                    return;
                }

                Object entitiesObj = snapshot.get("entities");
                int restored = 0;

                if (entitiesObj instanceof List) {
                    List<?> list = (List<?>) entitiesObj;
                    for (Object item : list) {
                        try {
                            com.appbana.model.EntitySchema schema = null;
                            if (item instanceof Map) {
                                schema = MAPPER.convertValue(item, com.appbana.model.EntitySchema.class);
                            } else if (item instanceof com.appbana.model.EntitySchema) {
                                schema = (com.appbana.model.EntitySchema) item;
                            }

                            if (schema != null) {
                                com.appbana.SchemaManager.saveSchema(schema);
                                restored++;
                                LOG.info("Restored schema: {}", schema.getName());
                            }
                        } catch (Exception ex) {
                            LOG.error("Failed to restore schema item", ex);
                        }
                    }
                }

                res.json(200, Map.of("message", "Restored " + restored + " schemas", "count", restored));
            } catch (Exception e) {
                LOG.error("Restore failed", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // ==================== STUDIO BUILDER APIs (Authenticated) ====================

        // List all apps for tenant
        router.get("/appbana-studio/{tenantId}/apps", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            if (tenantId == null || tenantId.isBlank()) {
                res.json(400, Map.of("error", "tenantId required"));
                return;
            }
            try {
                List<Map<String, Object>> apps = AppManager.listApps(tenantId);
                res.json(200, Map.of("apps", apps));
            } catch (Exception e) {
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // Get app by ID
        router.get("/appbana-studio/{tenantId}/apps/{id}", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("id");
            if (tenantId == null || tenantId.isBlank()) {
                res.json(400, Map.of("error", "tenantId required"));
                return;
            }
            try {
                AppMetadata app = AppManager.getAppFullMetadata(tenantId, appId);
                if (app == null) {
                    res.json(404, Map.of("error", "App not found: " + appId));
                    return;
                }
                res.json(200, app);
            } catch (Exception e) {
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // ==================== PUBLIC RUNTIME APIs (No Auth Required)
        // ====================

        // Get app with all pages (for runtime)
        router.get("/api/{tenantId}/apps/{id}/full", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("id");
            if (tenantId == null || tenantId.isBlank()) {
                res.json(400, Map.of("error", "tenantId required"));
                return;
            }
            try {
                Map<String, Object> appObject = AppManager.getAppWithPages(tenantId, appId);
                if (appObject == null) {
                    res.json(404, Map.of("error", "App not found: " + appId));
                    return;
                }
                res.json(200, appObject);
            } catch (Exception e) {
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // Get deployed app snapshot (PUBLIC - for end users running published apps)
        router.get("/api/{tenantId}/apps/{id}/env/{env}/full", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("id");
            String env = req.pathParam("env").toUpperCase();
            if (tenantId == null || tenantId.isBlank()) {
                res.json(400, Map.of("error", "tenantId required"));
                return;
            }
            try {
                Map<String, Object> snapshot = releaseService.getAppSnapshot(appId, env);
                if (snapshot == null) {
                    res.json(404, Map.of("error", "App not deployed to " + env));
                } else {
                    res.json(200, snapshot);
                }
            } catch (Exception e) {
                LOG.error("Failed to get app snapshot", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // Create new app
        router.post("/appbana-studio/{tenantId}/apps", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            if (tenantId == null || tenantId.isBlank()) {
                res.json(400, Map.of("error", "tenantId required"));
                return;
            }
            try {
                AppMetadata app = req.readJson(new TypeReference<AppMetadata>() {
                });

                if (AppManager.getApp(tenantId, app.getId()) != null) {
                    res.json(409, Map.of("error", "App with ID " + app.getId() + " already exists"));
                    return;
                }
                if (app.getId() == null || app.getId().isBlank()) {
                    res.json(400, Map.of("error", "App ID is required"));
                    return;
                }
                if (app.getName() == null || app.getName().isEmpty()) {
                    res.json(400, Map.of("error", "App name is required"));
                    return;
                }
                String creatorUserId = AuthService.extractUserId(req, com.appbana.config.ConfigManager.getConfig());
                if (creatorUserId == null || creatorUserId.isBlank()) {
                    res.json(401, Map.of("error", "Unauthorized: valid session required"));
                    return;
                }

                // Enforce author field to authenticated creator (cannot be spoofed by client payload)
                app.setAuthor(creatorUserId);

                AppMetadata created = AppManager.createApp(tenantId, app);

                // Task C1.5 — Bootstrap: app creator automatically gets 'both' (maker + checker) role on all entities in app
                if (created.getEntities() != null) {
                    java.util.Set<String> entityNames = new java.util.HashSet<>();
                    for (Object obj : created.getEntities()) {
                        if (obj instanceof com.appbana.model.EntitySchema es && es.getName() != null) {
                            entityNames.add(es.getName());
                        } else if (obj instanceof Map<?, ?> m) {
                            Object name = m.get("name");
                            if (name != null) entityNames.add(name.toString());
                        }
                    }
                    com.appbana.approval.UserRoleService.grantCreatorRoles(tenantId, created.getId(), creatorUserId, entityNames);
                }

                res.json(201, created);
            } catch (IllegalStateException e) {
                res.json(409, Map.of("error", e.getMessage()));
            } catch (Exception e) {
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // Update app
        router.put("/appbana-studio/{tenantId}/apps/{id}", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("id");
            if (tenantId == null || tenantId.isBlank()) {
                res.json(400, Map.of("error", "tenantId required"));
                return;
            }
            try {
                AppMetadata updates = req.readJson(new TypeReference<AppMetadata>() {
                });
                AppMetadata updated = AppManager.updateApp(tenantId, appId, updates);
                res.json(200, updated);
            } catch (IllegalArgumentException e) {
                res.json(404, Map.of("error", e.getMessage()));
            } catch (Exception e) {
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // Delete app
        router.delete("/appbana-studio/{tenantId}/apps/{id}", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("id");
            if (tenantId == null || tenantId.isBlank()) {
                res.json(400, Map.of("error", "tenantId required"));
                return;
            }
            try {
                boolean deleted = AppManager.deleteApp(tenantId, appId);
                if (!deleted) {
                    res.json(404, Map.of("error", "App not found: " + appId));
                    return;
                }
                res.json(200, Map.of("status", "deleted", "id", appId));
            } catch (Exception e) {
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // ==================== STUDIO WORKFLOW APIs ====================

        // Get app workflow
        router.get("/appbana-studio/{tenantId}/apps/{id}/workflow", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("id");
            if (tenantId == null || tenantId.isBlank()) {
                res.json(400, Map.of("error", "tenantId required"));
                return;
            }
            try {
                Map<String, Object> workflow = AppManager.getWorkflow(tenantId, appId);
                if (workflow == null) {
                    res.json(200, Map.of());
                    return;
                }
                res.json(200, workflow);
            } catch (Exception e) {
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // Save app workflow
        router.put("/appbana-studio/{tenantId}/apps/{id}/workflow", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("id");
            if (tenantId == null || tenantId.isBlank()) {
                res.json(400, Map.of("error", "tenantId required"));
                return;
            }
            try {
                Map<String, Object> workflow = req.readJson(new TypeReference<Map<String, Object>>() {
                });
                AppManager.saveWorkflow(tenantId, appId, workflow);
                res.json(200, Map.of("status", "ok"));
            } catch (Exception e) {
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // ==================== STUDIO PAGE APIs ====================

        // Get page
        router.get("/appbana-studio/{tenantId}/apps/{appId}/pages/{pageId}", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("appId");
            String pageId = req.pathParam("pageId");
            if (tenantId == null || tenantId.isBlank()) {
                res.json(400, Map.of("error", "tenantId required"));
                return;
            }
            try {
                Map<String, Object> page = AppManager.getPage(tenantId, appId, pageId);
                if (page == null) {
                    res.json(404, Map.of("error", "Page not found: " + appId + "/" + pageId));
                    return;
                }
                res.json(200, page);
            } catch (Exception e) {
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // Save page
        router.put("/appbana-studio/{tenantId}/apps/{appId}/pages/{pageId}", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("appId");
            String pageId = req.pathParam("pageId");
            if (tenantId == null || tenantId.isBlank()) {
                res.json(400, Map.of("error", "tenantId required"));
                return;
            }
            try {
                Map<String, Object> page = req.readJson(new TypeReference<Map<String, Object>>() {
                });
                AppManager.savePage(tenantId, appId, pageId, page);
                res.json(200, Map.of("status", "saved", "appId", appId, "pageId", pageId));
            } catch (Exception e) {
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // Delete page
        router.delete("/appbana-studio/{tenantId}/apps/{appId}/pages/{pageId}", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("appId");
            String pageId = req.pathParam("pageId");
            if (tenantId == null || tenantId.isBlank()) {
                res.json(400, Map.of("error", "tenantId required"));
                return;
            }
            try {
                boolean deleted = AppManager.deletePage(tenantId, appId, pageId);
                if (!deleted) {
                    res.json(404, Map.of("error", "Page not found: " + appId + "/" + pageId));
                    return;
                }
                res.json(200, Map.of("status", "deleted", "appId", appId, "pageId", pageId));
            } catch (Exception e) {
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // ==================== TEMPLATES ====================

        router.get("/api/templates", (req, res) -> {
            try {
                List<Map<String, Object>> templates = templateService.getAllTemplates();
                res.json(200, templates);
            } catch (Exception e) {
                LOG.error("Failed to get templates", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        router.get("/api/templates/{id}", (req, res) -> {
            try {
                String templateId = req.pathParam("id");
                Map<String, Object> template = templateService.getTemplate(templateId);
                if (template == null) {
                    res.json(404, Map.of("error", "Template not found"));
                    return;
                }
                res.json(200, template);
            } catch (Exception e) {
                LOG.error("Failed to get template", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        router.post("/api/templates", (req, res) -> {
            try {
                Map<String, Object> templateData = req.readJson(new TypeReference<>() {
                });
                Map<String, Object> created = templateService.createUserTemplate(templateData);
                res.json(201, created);
            } catch (Exception e) {
                LOG.error("Failed to create template", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        router.put("/api/templates/{id}", (req, res) -> {
            try {
                String templateId = req.pathParam("id");
                Map<String, Object> templateData = req.readJson(new TypeReference<>() {
                });
                Map<String, Object> updated = templateService.updateUserTemplate(templateId, templateData);
                res.json(200, updated);
            } catch (Exception e) {
                LOG.error("Failed to update template", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        router.delete("/api/templates/{id}", (req, res) -> {
            try {
                String templateId = req.pathParam("id");
                templateService.deleteUserTemplate(templateId);
                res.json(200, Map.of("status", "deleted"));
            } catch (Exception e) {
                LOG.error("Failed to delete template", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });
    }
}
