package com.appbana.server.routes;

import com.appbana.AppManager;
import com.appbana.api.Router;
import com.appbana.model.AppMetadata;
import com.appbana.service.AuthService;
import com.appbana.service.ReleaseService;
import com.appbana.service.TemplateService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public static void register(Router router) {
        ReleaseService releaseService = new ReleaseService();
        String dataDir = Optional.ofNullable(System.getProperty("appbana.dataDir")).orElse("./data");
        TemplateService templateService = new TemplateService(dataDir);

        // ==================== RELEASE MANAGEMENT ====================

        // Create app version
        router.post("/api/apps/{id}/versions", (req, res) -> {
            try {
                String appId = req.pathParam("id");
                Map<String, String> body = req.readJson(new TypeReference<>() {
                });
                String label = body.get("label");
                String desc = body.get("description");
                String userId = AuthService.extractUserId(req, com.appbana.config.ConfigManager.getConfig());

                String versionId = releaseService.createVersion(DEFAULT_TENANT, appId, label, desc, userId);
                res.json(201, Map.of("id", versionId, "status", "created"));
            } catch (Exception e) {
                LOG.error("Failed to create version", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // List app versions
        router.get("/api/apps/{id}/versions", (req, res) -> {
            try {
                String appId = req.pathParam("id");
                res.json(200, releaseService.listVersions(appId));
            } catch (Exception e) {
                LOG.error("Failed to list versions", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // Deploy version
        router.post("/api/apps/{id}/deploy/{versionId}", (req, res) -> {
            try {
                String appId = req.pathParam("id");
                String versionId = req.pathParam("versionId");
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
        router.get("/api/apps/{id}/pipeline", (req, res) -> {
            try {
                String appId = req.pathParam("id");
                res.json(200, releaseService.getPipelineStatus(appId));
            } catch (Exception e) {
                LOG.error("Failed to get pipeline status", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // Get app snapshot
        router.get("/api/apps/{id}/env/{env}/full", (req, res) -> {
            try {
                String appId = req.pathParam("id");
                String env = req.pathParam("env").toUpperCase();
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
        router.post("/api/apps/{id}/restore-schemas", (req, res) -> {
            String appId = req.pathParam("id");
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

        // ==================== APP CRUD ====================

        // List all apps
        router.get("/apps", (req, res) -> {
            try {
                List<Map<String, Object>> apps = AppManager.listApps(DEFAULT_TENANT);
                res.json(200, Map.of("apps", apps));
            } catch (Exception e) {
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // Get app by ID
        router.get("/apps/{id}", (req, res) -> {
            String appId = req.pathParam("id");
            try {
                AppMetadata app = AppManager.getApp(DEFAULT_TENANT, appId);
                if (app == null) {
                    res.json(404, Map.of("error", "App not found: " + appId));
                    return;
                }
                res.json(200, app);
            } catch (Exception e) {
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // Get app with all pages
        router.get("/apps/{id}/full", (req, res) -> {
            String appId = req.pathParam("id");
            try {
                Map<String, Object> appObject = AppManager.getAppWithPages(DEFAULT_TENANT, appId);
                if (appObject == null) {
                    res.json(404, Map.of("error", "App not found: " + appId));
                    return;
                }
                res.json(200, appObject);
            } catch (Exception e) {
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // Create new app
        router.post("/apps", (req, res) -> {
            try {
                AppMetadata app = req.readJson(new TypeReference<AppMetadata>() {
                });

                if (AppManager.getApp(DEFAULT_TENANT, app.getId()) != null) {
                    res.json(409, Map.of("error", "App with ID " + app.getId() + " already exists"));
                    return;
                }
                if (app.getName() == null || app.getName().isEmpty()) {
                    res.json(400, Map.of("error", "App name is required"));
                    return;
                }
                if (app.getId() == null || app.getId().isEmpty()) {
                    res.json(400, Map.of("error", "App ID is required"));
                    return;
                }

                AppMetadata created = AppManager.createApp(DEFAULT_TENANT, app);
                res.json(201, created);
            } catch (IllegalStateException e) {
                res.json(409, Map.of("error", e.getMessage()));
            } catch (Exception e) {
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // Update app
        router.put("/apps/{id}", (req, res) -> {
            String appId = req.pathParam("id");
            try {
                AppMetadata updates = req.readJson(new TypeReference<AppMetadata>() {
                });
                AppMetadata updated = AppManager.updateApp(DEFAULT_TENANT, appId, updates);
                res.json(200, updated);
            } catch (IllegalArgumentException e) {
                res.json(404, Map.of("error", e.getMessage()));
            } catch (Exception e) {
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // Delete app
        router.delete("/apps/{id}", (req, res) -> {
            String appId = req.pathParam("id");
            try {
                boolean deleted = AppManager.deleteApp(DEFAULT_TENANT, appId);
                if (!deleted) {
                    res.json(404, Map.of("error", "App not found: " + appId));
                    return;
                }
                res.json(200, Map.of("status", "deleted", "id", appId));
            } catch (Exception e) {
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // ==================== WORKFLOW ====================

        // Get app workflow
        router.get("/apps/{id}/workflow", (req, res) -> {
            String appId = req.pathParam("id");
            try {
                Map<String, Object> workflow = AppManager.getWorkflow(DEFAULT_TENANT, appId);
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
        router.put("/apps/{id}/workflow", (req, res) -> {
            String appId = req.pathParam("id");
            try {
                Map<String, Object> workflow = req.readJson(new TypeReference<Map<String, Object>>() {
                });
                AppManager.saveWorkflow(DEFAULT_TENANT, appId, workflow);
                res.json(200, Map.of("status", "ok"));
            } catch (Exception e) {
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // ==================== PAGES ====================

        // Get page
        router.get("/apps/{appId}/pages/{pageId}", (req, res) -> {
            String appId = req.pathParam("appId");
            String pageId = req.pathParam("pageId");
            try {
                Map<String, Object> page = AppManager.getPage(DEFAULT_TENANT, appId, pageId);
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
        router.put("/apps/{appId}/pages/{pageId}", (req, res) -> {
            String appId = req.pathParam("appId");
            String pageId = req.pathParam("pageId");
            try {
                Map<String, Object> page = req.readJson(new TypeReference<Map<String, Object>>() {
                });
                AppManager.savePage(DEFAULT_TENANT, appId, pageId, page);
                res.json(200, Map.of("status", "saved", "appId", appId, "pageId", pageId));
            } catch (Exception e) {
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // Delete page
        router.delete("/apps/{appId}/pages/{pageId}", (req, res) -> {
            String appId = req.pathParam("appId");
            String pageId = req.pathParam("pageId");
            try {
                boolean deleted = AppManager.deletePage(DEFAULT_TENANT, appId, pageId);
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
