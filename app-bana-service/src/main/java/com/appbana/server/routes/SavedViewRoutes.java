package com.appbana.server.routes;

import com.appbana.JdbcManager;
import com.appbana.api.Router;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phase B5 — persisted list views (filters + sort + groupBy + aggregates).
 *
 * A "view" is an opaque JSON blob (`viewJson`) that the runtime writes and
 * reads verbatim; the backend only enforces the (tenant, app, entity) triple
 * plus an owner_user_id so lists can be filtered per user.
 *
 * Endpoints (all under /api/saved-views — matches ENTITY_API_PATTERN and
 * therefore falls in the same public bucket as /api/files, /api/apps/*, etc.
 * consistent with the codebase's current dev-mode auth posture):
 *
 *   GET    /api/saved-views?tenantId=&appId=&entityKey=
 *   POST   /api/saved-views                  body: full view record
 *   DELETE /api/saved-views/{viewId}
 */
public class SavedViewRoutes {

    private static final Logger LOG = LoggerFactory.getLogger(SavedViewRoutes.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String INSERT_SQL =
            "INSERT INTO appbana_saved_views " +
            "(view_id, tenant_id, app_id, entity_key, owner_user_id, name, view_json, is_default) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String LIST_SQL =
            "SELECT view_id, tenant_id, app_id, entity_key, owner_user_id, name, view_json, is_default, " +
            "       created_at, updated_at " +
            "FROM appbana_saved_views " +
            "WHERE tenant_id = ? AND app_id = ? AND entity_key = ? " +
            "ORDER BY is_default DESC, name ASC";

    private static final String DELETE_SQL =
            "DELETE FROM appbana_saved_views WHERE view_id = ?";

    private SavedViewRoutes() {}

    public static void register(Router router) {
        router.get("/api/saved-views", SavedViewRoutes::handleList);
        router.post("/api/saved-views", SavedViewRoutes::handleUpsert);
        router.delete("/api/saved-views/{viewId}", SavedViewRoutes::handleDelete);
        LOG.info("Registered saved-view routes: GET/POST /api/saved-views, DELETE /api/saved-views/{{viewId}}");
    }

    private static void handleList(Router.HttpRequest req, Router.HttpResponse res) {
        String tenantId = req.query("tenantId");
        String appId = req.query("appId");
        String entityKey = req.query("entityKey");
        if (tenantId == null || appId == null || entityKey == null) {
            res.json(400, Map.of("error", "tenantId, appId and entityKey are required"));
            return;
        }

        List<Map<String, Object>> out = new ArrayList<>();
        try (Connection conn = JdbcManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(LIST_SQL)) {
            ps.setString(1, tenantId);
            ps.setString(2, appId);
            ps.setString(3, entityKey);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("viewId", rs.getString("view_id"));
                    row.put("tenantId", rs.getString("tenant_id"));
                    row.put("appId", rs.getString("app_id"));
                    row.put("entityKey", rs.getString("entity_key"));
                    row.put("ownerUserId", rs.getString("owner_user_id"));
                    row.put("name", rs.getString("name"));
                    row.put("isDefault", rs.getBoolean("is_default"));
                    // Rehydrate the opaque view JSON into a nested object so the
                    // caller doesn't have to double-parse.
                    String json = rs.getString("view_json");
                    row.put("view", json == null ? Map.of() : parseJsonSafe(json));
                    row.put("createdAt", String.valueOf(rs.getTimestamp("created_at")));
                    row.put("updatedAt", String.valueOf(rs.getTimestamp("updated_at")));
                    out.add(row);
                }
            }
        } catch (Exception e) {
            LOG.error("Failed to list saved views", e);
            res.json(500, Map.of("error", "Failed to list views"));
            return;
        }
        res.json(200, Map.of("views", out));
    }

    private static void handleUpsert(Router.HttpRequest req, Router.HttpResponse res) {
        Map<String, Object> body;
        try {
            body = req.readJson(new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            res.json(400, Map.of("error", "Invalid JSON body"));
            return;
        }
        if (body == null) {
            res.json(400, Map.of("error", "Empty body"));
            return;
        }

        String tenantId = asString(body.get("tenantId"));
        String appId = asString(body.get("appId"));
        String entityKey = asString(body.get("entityKey"));
        String name = asString(body.get("name"));
        Object view = body.get("view");
        boolean isDefault = Boolean.TRUE.equals(body.get("isDefault"));
        String ownerUserId = asString(body.getOrDefault("ownerUserId", req.getAttribute("userId")));

        if (tenantId == null || appId == null || entityKey == null || name == null) {
            res.json(400, Map.of("error", "tenantId, appId, entityKey and name are required"));
            return;
        }
        String viewJson;
        try {
            viewJson = MAPPER.writeValueAsString(view == null ? Map.of() : view);
        } catch (Exception e) {
            res.json(400, Map.of("error", "view is not serialisable"));
            return;
        }

        String viewId = UUID.randomUUID().toString().replace("-", "");
        try (Connection conn = JdbcManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
            ps.setString(1, viewId);
            ps.setString(2, tenantId);
            ps.setString(3, appId);
            ps.setString(4, entityKey);
            ps.setString(5, ownerUserId);
            ps.setString(6, name);
            ps.setString(7, viewJson);
            ps.setBoolean(8, isDefault);
            ps.executeUpdate();
        } catch (Exception e) {
            LOG.error("Failed to persist saved view", e);
            res.json(500, Map.of("error", "Failed to save view"));
            return;
        }

        res.json(201, Map.of("viewId", viewId));
    }

    private static void handleDelete(Router.HttpRequest req, Router.HttpResponse res) {
        String viewId = req.pathParam("viewId");
        if (viewId == null || viewId.isBlank()) {
            res.json(400, Map.of("error", "viewId is required"));
            return;
        }
        try (Connection conn = JdbcManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE_SQL)) {
            ps.setString(1, viewId);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                res.json(404, Map.of("error", "Unknown viewId"));
                return;
            }
        } catch (Exception e) {
            LOG.error("Failed to delete view {}", viewId, e);
            res.json(500, Map.of("error", "Failed to delete view"));
            return;
        }
        res.json(200, Map.of("deleted", viewId));
    }

    private static Object parseJsonSafe(String json) {
        try {
            return MAPPER.readValue(json, new TypeReference<Object>() {});
        } catch (Exception e) {
            return Map.of("raw", json);
        }
    }

    private static String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
