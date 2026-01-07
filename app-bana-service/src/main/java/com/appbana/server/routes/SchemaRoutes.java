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

                SchemaManager.saveSchema(schema);
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
