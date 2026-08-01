package com.appbana.server.routes;

import com.appbana.SchemaManager;
import com.appbana.api.Router;
import com.appbana.config.AppConfig;
import com.appbana.config.ConfigManager;
import com.appbana.model.EntitySchema;
import com.appbana.service.AuthService;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Schema management routes
 */
public class SchemaRoutes {
    private static final Logger LOG = LoggerFactory.getLogger(SchemaRoutes.class);

    public static void register(Router router) {
        // List API endpoints for all schemas
        router.get("/api/endpoints", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            if (AuthService.authEnabled(cfg)) {
                String tok = AuthService.extractToken(req);
                if (!AuthService.hasRead(tok, cfg)) {
                    res.json(401, Map.of("error", "unauthorized"));
                    return;
                }
            }
            try {
                List<String> names = SchemaManager.listSchemaNames();
                List<Map<String, Object>> out = new ArrayList<>();
                for (String n : names) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("entity", n);
                    List<String> eps = new ArrayList<>();
                    eps.add("POST /api/" + n);
                    eps.add("GET /api/" + n);
                    eps.add("GET /api/" + n + "/{id}");
                    eps.add("PUT /api/" + n + "/{id}");
                    eps.add("DELETE /api/" + n + "/{id}");
                    m.put("endpoints", eps);
                    out.add(m);
                }
                res.json(200, out);
            } catch (Exception e) {
                LOG.error("Failed to build /api/endpoints response", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // OpenAPI spec generation
        router.get("/openapi.json", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            if (AuthService.authEnabled(cfg)) {
                String tok = AuthService.extractToken(req);
                if (!AuthService.hasRead(tok, cfg)) {
                    res.json(401, Map.of("error", "unauthorized"));
                    return;
                }
            }
            try {
                List<String> names = SchemaManager.listSchemaNames();
                List<EntitySchema> schemas = new ArrayList<>();
                for (String n : names) {
                    EntitySchema s = SchemaManager.loadSchema(n);
                    if (s != null)
                        schemas.add(s);
                }
                String spec = com.appbana.OpenApiGenerator.generate(schemas);
                res.text(200, spec, "application/json; charset=utf-8");
            } catch (Exception e) {
                LOG.error("Failed to serve OpenAPI spec", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // List schemas
        router.get("/schema", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            if (AuthService.authEnabled(cfg)) {
                String tok = AuthService.extractToken(req);
                if (!AuthService.hasRead(tok, cfg)) {
                    res.json(401, Map.of("error", "unauthorized"));
                    return;
                }
            }

            String pageS = req.query("page");
            String sizeS = req.query("size");
            String q = req.query("q");

            if (pageS != null || sizeS != null || q != null) {
                int page = 1, size = 10;
                try {
                    if (pageS != null)
                        page = Integer.parseInt(pageS);
                } catch (Exception ignored) {
                }
                try {
                    if (sizeS != null)
                        size = Integer.parseInt(sizeS);
                } catch (Exception ignored) {
                }

                List<String> names = SchemaManager.listSchemaNames(page, size, q);
                res.json(200, names);
            } else {
                List<String> names = SchemaManager.listSchemaNames();
                res.json(200, names);
            }
        });

        // Get schema by name
        router.get("/schema/{name}", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            if (AuthService.authEnabled(cfg)) {
                String tok = AuthService.extractToken(req);
                if (!AuthService.hasRead(tok, cfg)) {
                    res.json(401, Map.of("error", "unauthorized"));
                    return;
                }
            }

            String name = req.pathParam("name");
            EntitySchema schema = SchemaManager.loadSchema(name);
            if (schema == null) {
                res.json(404, Map.of("error", "not found"));
                return;
            }
            res.json(200, schema);
        });

        // Create/Update schema
        router.post("/schema", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            if (AuthService.authEnabled(cfg)) {
                String tok = AuthService.extractToken(req);
                if (!AuthService.hasWrite(tok, cfg)) {
                    res.json(401, Map.of("error", "unauthorized"));
                    return;
                }
            }

            try {
                EntitySchema schema = req.readJson(new TypeReference<EntitySchema>() {
                });
                if (schema.getName() == null || schema.getName().isEmpty()) {
                    res.json(400, Map.of("error", "Schema name is required"));
                    return;
                }

                // Task C1.9 Fix: Reject schemas missing tenantId or appId (no phantom default fallbacks)
                if (schema.getTenantId() == null || schema.getTenantId().isBlank() ||
                    schema.getAppId() == null || schema.getAppId().isBlank()) {
                    res.json(400, Map.of("error", "tenantId and appId are required in schema payload"));
                    return;
                }

                String tenantId = schema.getTenantId();
                String appId = schema.getAppId();
                String userId = AuthService.extractUserId(req, cfg);
                if (userId == null || userId.isBlank()) {
                    res.json(401, Map.of("error", "Unauthorized: valid session required"));
                    return;
                }

                // Task C1.10 Fix: Enforce app ownership authorization before saving schema
                if (!com.appbana.security.AppAuthorization.isAppOwnerOrSystem(tenantId, appId, userId)) {
                    res.json(403, Map.of("error", "Forbidden: caller is not authorized to create or modify entity schemas for app " + appId));
                    return;
                }

                // Check if this entity schema is NEW before saving (upsert)
                boolean isNewEntity = SchemaManager.loadSchema(appId, schema.getName(), tenantId) == null;

                SchemaManager.saveSchema(schema);

                // Task C1.9 Fix: ONLY bootstrap creator role on INSERT (new entity creation), NOT on updates.
                if (isNewEntity) {
                    com.appbana.approval.UserRoleService.grantRole(tenantId, appId, schema.getName(), userId, com.appbana.approval.UserRoleService.Role.BOTH, userId);
                }

                res.json(200, Map.of("status", "saved", "name", schema.getName()));
            } catch (Exception e) {
                LOG.error("Failed to save schema", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // Delete schema
        router.delete("/schema/{name}", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            if (AuthService.authEnabled(cfg)) {
                String tok = AuthService.extractToken(req);
                if (!AuthService.hasWrite(tok, cfg)) {
                    res.json(401, Map.of("error", "unauthorized"));
                    return;
                }
            }

            String name = req.pathParam("name");
            try {
                EntitySchema existing = SchemaManager.loadSchema(name);
                if (existing == null) {
                    res.json(404, Map.of("error", "Schema not found: " + name));
                    return;
                }

                // Task S1.4 Fix: DELETE must enforce the same app ownership authorization as
                // POST /schema (Task C1.10). Previously only the create/update path checked
                // isAppOwnerOrSystem, so any caller who could name a schema key could drop another
                // tenant's table.
                String userId = AuthService.extractUserId(req, cfg);
                if (userId == null || userId.isBlank()) {
                    res.json(401, Map.of("error", "Unauthorized: valid session required"));
                    return;
                }
                if (!com.appbana.security.AppAuthorization.isAppOwnerOrSystem(existing.getTenantId(), existing.getAppId(), userId)) {
                    res.json(403, Map.of("error", "Forbidden: caller is not authorized to delete entity schema for app " + existing.getAppId()));
                    return;
                }

                boolean dropTable = "true".equalsIgnoreCase(req.query("dropTable"))
                        || "1".equals(req.query("dropTable"));
                boolean deleted = SchemaManager.deleteSchema(name, dropTable);
                if (!deleted) {
                    res.json(404, Map.of("error", "Schema not found: " + name));
                    return;
                }
                res.json(200, Map.of("status", "deleted", "name", name));
            } catch (Exception e) {
                LOG.error("Failed to delete schema", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // Debug endpoints
        router.get("/api/debug/schemas", (req, res) -> {
            try {
                res.json(200, SchemaManager.listSchemaSummaries());
            } catch (Exception e) {
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        router.get("/api/debug/schemas/names", (req, res) -> {
            try {
                res.json(200, SchemaManager.listSchemaNames());
            } catch (Exception e) {
                res.json(500, Map.of("error", e.getMessage()));
            }
        });
    }
}
