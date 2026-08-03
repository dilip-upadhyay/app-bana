package com.appbana.server.routes;

import com.appbana.JdbcManager;
import com.appbana.api.Router;
import com.appbana.config.AppConfig;
import com.appbana.config.ConfigManager;
import com.appbana.security.TenantAccessGuard;
import com.appbana.service.AuthService;
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
 * Endpoints (all under /api/saved-views):
 *
 *   GET    /api/saved-views?tenantId=&appId=&entityKey=
 *   POST   /api/saved-views                  body: full view record
 *   DELETE /api/saved-views/{viewId}
 *
 * S1.8 (tenant isolation hardening): all 3 routes now require a resolved identity
 * ({@link AuthService#resolveIdentity}) whose own tenant matches the tenantId/appId supplied in
 * the query string or body ({@link TenantAccessGuard#requireOwnTenant}) — this route matches
 * {@code ENTITY_API_PATTERN} and was previously reachable anonymously end-to-end, the same class
 * of gap S1.7 fixed for file uploads. DELETE additionally requires the caller to be the view's own
 * {@code owner_user_id} (or a break-glass admin/service token): the route only carries
 * {@code viewId} in the path, so {@code tenant_id}/{@code app_id}/{@code owner_user_id} are looked
 * up from the row itself first, then checked against the resolved identity — never trusted from
 * the client (mirrors S1.4's load-then-authorize pattern for {@code DELETE /schema/{name}}).
 * {@code ownerUserId} on the POST route is likewise now always the resolved identity, never a
 * client-supplied value — the same class of fix S1.7 applied to {@code uploadedBy}.
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

    // S2.6 decision (S1.8 round-1 forward note, `TENANT_ISOLATION_IMPLEMENTATION_TASKS.md`): saved
    // views are deliberately tenant/app-shared, NOT filtered by owner_user_id. Now that S2.6 wires
    // the membership exception in, a second app member really can list this app's views — that is
    // the intended behavior, not a leak: `is_default` only makes sense as a concept if a view can be
    // the shared default for everyone viewing that entity, and there is no product surface (no
    // "private view" toggle in the runtime UI) that ever asked for per-user visibility. DELETE stays
    // owner-only (see DELETE_SQL below) — sharing a view for everyone to see is not the same as
    // letting anyone but its owner remove it.

    private static final String LOOKUP_SQL =
            "SELECT tenant_id, app_id, owner_user_id FROM appbana_saved_views WHERE view_id = ?";

    // S1.8 — owner_user_id is nullable (legacy rows saved before this fix may have no owner
    // recorded at all), so a plain "owner_user_id = ?" would never match a NULL column even when
    // the looked-up value being bound is itself null (SQL NULL = NULL is UNKNOWN, not TRUE) — that
    // would make an already-authorized delete silently affect 0 rows and misreport 404. Bound with
    // the value just read by LOOKUP_SQL, never a client-supplied one.
    private static final String DELETE_SQL =
            "DELETE FROM appbana_saved_views " +
            "WHERE view_id = ? AND tenant_id = ? AND app_id = ? AND owner_user_id IS NOT DISTINCT FROM ?";

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

        // S1.8 — require a resolved identity whose own tenant matches the requested tenantId/appId,
        // instead of trusting them as handed to us in the query string. Mirrors S1.7's FileRoutes fix.
        AppConfig cfg = ConfigManager.getConfig();
        TenantAccessGuard.Result access = TenantAccessGuard.requireOwnTenant(req, cfg, tenantId, appId);
        if (!access.allowed()) {
            res.json(access.statusCode(), Map.of("error", access.message()));
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

        if (tenantId == null || appId == null || entityKey == null || name == null) {
            res.json(400, Map.of("error", "tenantId, appId, entityKey and name are required"));
            return;
        }

        // S1.8 — require a resolved identity whose own tenant matches tenantId/appId, instead of
        // trusting them as handed to us in the body. Mirrors S1.7's FileRoutes fix.
        AppConfig cfg = ConfigManager.getConfig();
        TenantAccessGuard.Result access = TenantAccessGuard.requireOwnTenant(req, cfg, tenantId, appId);
        if (!access.allowed()) {
            res.json(access.statusCode(), Map.of("error", access.message()));
            return;
        }
        // S1.8 — ownerUserId must reflect the resolved identity, never a client-supplied value (a
        // caller could otherwise stamp any user's id on a view as its owner). Mirrors S1.7's
        // uploadedBy fix in FileRoutes.
        String ownerUserId = AuthService.resolveIdentity(req, cfg);

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

        // S1.8 — this route only carries viewId in the path, so look the row's own tenant/app/owner
        // up FIRST and authorize against what it actually says — never trust a client-supplied
        // tenant/app/owner for a delete-by-id route. Mirrors S1.4's DELETE /schema/{name} pattern:
        // load, authorize, then act.
        String tenantId;
        String appId;
        String ownerUserId;
        try (Connection conn = JdbcManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(LOOKUP_SQL)) {
            ps.setString(1, viewId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    res.json(404, Map.of("error", "Unknown viewId"));
                    return;
                }
                tenantId = rs.getString("tenant_id");
                appId = rs.getString("app_id");
                ownerUserId = rs.getString("owner_user_id");
            }
        } catch (Exception e) {
            LOG.error("Failed to look up view {} for delete", viewId, e);
            res.json(500, Map.of("error", "Failed to delete view"));
            return;
        }

        AppConfig cfg = ConfigManager.getConfig();
        TenantAccessGuard.Result access = TenantAccessGuard.requireOwnTenant(req, cfg, tenantId, appId);
        if (!access.allowed()) {
            res.json(access.statusCode(), Map.of("error", access.message()));
            return;
        }

        // S1.8 — tenant match alone is not enough: a saved view also has an individual owner, and
        // only that owner (or a break-glass admin/service token) may delete it. A null ownerUserId
        // (a legacy row saved before this fix) fails closed rather than being treated as a wildcard
        // match — same M1 precedent as TenantAccessGuard's own null-tenant handling.
        String serviceToken = AuthService.extractServiceToken(req);
        boolean isAdmin = serviceToken != null && !serviceToken.isBlank() && AuthService.hasAdmin(serviceToken, cfg);
        String identity = AuthService.resolveIdentity(req, cfg);
        // requireOwnTenant only guarantees a session with a non-null tenantId, not a non-null userId
        // (review round 1 nit) — reject explicitly rather than Objects.equals(identity, ownerUserId),
        // which would let a null identity match a null (legacy) owner and wildcard-authorize the delete.
        if (identity == null) {
            res.json(401, Map.of("error", "Unauthorized: valid session required"));
            return;
        }
        if (!isAdmin && !identity.equals(ownerUserId)) {
            res.json(403, Map.of("error", "Forbidden: only the view's owner may delete it"));
            return;
        }

        try (Connection conn = JdbcManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE_SQL)) {
            ps.setString(1, viewId);
            ps.setString(2, tenantId);
            ps.setString(3, appId);
            ps.setString(4, ownerUserId);
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
