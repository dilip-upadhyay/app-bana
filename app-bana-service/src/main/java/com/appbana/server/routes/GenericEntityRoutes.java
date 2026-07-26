package com.appbana.server.routes;

import com.appbana.AuditLogService;
import com.appbana.JdbcManager;
import com.appbana.SchemaManager;
import com.appbana.api.Router;
import com.appbana.config.AppConfig;
import com.appbana.config.ConfigManager;
import com.appbana.model.EntitySchema;
import com.appbana.model.TenantContext;
import com.appbana.service.AuthService;
import com.appbana.service.EntityCrudService;
import com.appbana.service.ErrorHandler;
import com.appbana.service.PermissionService;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Generic entity CRUD routes (dynamic entities based on schemas)
 * 
 * ## STATUS: Awaiting EntityCrudService Extraction (Phase 2)
 * 
 * These routes were intentionally removed during refactoring to eliminate
 * code duplication. Full implementation requires extracting EntityCrudService
 * from ApiServer first (~300 lines, 15+ methods).
 * 
 * ## Current Dependencies Extracted:
 * - ✅ AuthService - Authentication and authorization
 * - ✅ ErrorHandler - Error formatting
 * - ⏳ EntityCrudService - CRUD operations (IN PROGRESS)
 * 
 * ## Supported Operations (once EntityCrudService is extracted):
 * - POST /api/{entity} - Create entity
 * - GET /api/{entity} - List entities with pagination/filtering
 * - GET /api/{entity}/{id} - Get entity by ID
 * - PUT /api/{entity}/{id} - Update entity
 * - DELETE /api/{entity}/{id} - Delete entity
 * - POST /api/{entity}/batch - Batch create
 * - POST /api/{entity}/bulk-delete - Bulk delete by IDs
 * - POST /api/{entity}/bulk-export - Export entities
 * 
 * ## Additional Routes:
 * - Field-level permissions (POST/GET /api/field-permissions)
 * - Datasource configuration (/ui/datasource/*)
 * 
 * ## Next Steps:
 * 1. Extract ApiServer CRUD methods into EntityCrudService:
 * - insertRecord(), getById(), updateById(), deleteById()
 * - listAll(), listAdvanced(), insertBatch()
 * - parseFilters(), buildWhere(), countOnly()
 * - quote(), parseId(), toList(), coerceAndValidate()
 * 
 * 2. Implement routes using:
 * - AuthService for authentication
 * - EntityCrudService for database ops
 * - ErrorHandler for error responses
 * 
 * 3. Register in RouteRegistry
 * 
 * ## Estimated Effort:
 * - EntityCrudService extraction: 20-30 minutes
 * - Route implementation: 15-20 minutes
 * - Total: 35-50 minutes
 * 
 * @see com.appbana.service.AuthService
 * @see com.appbana.service.ErrorHandler
 */
public class GenericEntityRoutes {

    private static final Logger LOG = LoggerFactory.getLogger(GenericEntityRoutes.class);

    public static void register(Router router) {
        EntityCrudService crud = new EntityCrudService();
        PermissionService permissionService = com.appbana.server.ServerBootstrap
                .initializePermissionService(ConfigManager.getConfig());

        // ==================== AUDIT ====================
        router.get("/audit", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            if (AuthService.authEnabled(cfg)) {
                String tok = AuthService.extractToken(req);
                if (!AuthService.hasRead(tok, cfg)) {
                    res.json(401, Map.of("error", "unauthorized"));
                    return;
                }
            }

            String entity = req.query("entity");
            String pk = req.query("pk");
            int limit = 50;
            int offset = 0;
            try {
                String ls = req.query("limit");
                if (ls != null)
                    limit = Integer.parseInt(ls);
            } catch (Exception ignored) {
            }
            try {
                String os = req.query("offset");
                if (os != null)
                    offset = Integer.parseInt(os);
            } catch (Exception ignored) {
            }
            if (limit <= 0)
                limit = 50;
            if (limit > 500)
                limit = 500;
            if (offset < 0)
                offset = 0;

            try {
                Map<String, Object> out = AuditLogService.query(entity, pk, limit, offset);
                res.json(200, out);
            } catch (Exception e) {
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // ==================== FIELD-LEVEL SECURITY (FLS) ADMIN ====================
        router.get("/api/field-permissions", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            if (AuthService.authEnabled(cfg)) {
                String tok = AuthService.extractToken(req);
                if (!AuthService.hasAdmin(tok, cfg)) {
                    res.json(401, Map.of("error", "unauthorized"));
                    return;
                }
            }

            String roleId = req.query("roleId");
            String entityName = req.query("entityName");

            try (Connection conn = JdbcManager.getConnection()) {
                StringBuilder sql = new StringBuilder("SELECT * FROM field_permission WHERE 1=1");
                List<Object> params = new ArrayList<>();

                if (roleId != null && !roleId.isBlank()) {
                    sql.append(" AND role_id = ?");
                    params.add(roleId);
                }
                if (entityName != null && !entityName.isBlank()) {
                    sql.append(" AND entity_name = ?");
                    params.add(entityName);
                }

                sql.append(" ORDER BY entity_name, field_name");

                try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                    for (int i = 0; i < params.size(); i++) {
                        stmt.setObject(i + 1, params.get(i));
                    }

                    try (ResultSet rs = stmt.executeQuery()) {
                        List<Map<String, Object>> permissions = new ArrayList<>();
                        while (rs.next()) {
                            Map<String, Object> perm = new LinkedHashMap<>();
                            perm.put("id", rs.getString("id"));
                            perm.put("roleId", rs.getString("role_id"));
                            perm.put("entityName", rs.getString("entity_name"));
                            perm.put("fieldName", rs.getString("field_name"));
                            perm.put("canRead", rs.getBoolean("can_read"));
                            perm.put("canEdit", rs.getBoolean("can_edit"));
                            perm.put("createdAt", rs.getTimestamp("created_at"));
                            perm.put("updatedAt", rs.getTimestamp("updated_at"));
                            permissions.add(perm);
                        }
                        res.json(200, Map.of("permissions", permissions, "total", permissions.size()));
                    }
                }
            } catch (SQLException e) {
                LOG.error("Failed to fetch field permissions", e);
                res.json(500, ErrorHandler.errorDetails(e));
            }
        });

        router.get("/api/field-permissions/readable", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            String userId;

            if (AuthService.authEnabled(cfg)) {
                userId = AuthService.extractUserId(req, cfg);
                if (userId == null) {
                    res.json(401, Map.of("error", "unauthorized"));
                    return;
                }
            } else {
                res.json(200, Map.of("fields", List.of("*"), "message",
                        "Authentication disabled - all fields readable"));
                return;
            }

            String entityName = req.query("entity");
            if (entityName == null || entityName.isBlank()) {
                res.json(400, Map.of("error", "entity parameter required"));
                return;
            }

            if (permissionService == null) {
                res.json(503, Map.of("error", "Permission service not available"));
                return;
            }

            try {
                List<String> readableFields = permissionService.getReadableFields(userId, entityName);
                res.json(200, Map.of(
                        "entity", entityName,
                        "userId", userId,
                        "fields", readableFields,
                        "count", readableFields.size()));
            } catch (Exception e) {
                LOG.error("Failed to get readable fields for user {} entity {}", userId, entityName, e);
                res.json(500, ErrorHandler.errorDetails(e));
            }
        });

        router.get("/api/field-permissions/editable", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            String userId;

            if (AuthService.authEnabled(cfg)) {
                userId = AuthService.extractUserId(req, cfg);
                if (userId == null) {
                    res.json(401, Map.of("error", "unauthorized"));
                    return;
                }
            } else {
                res.json(200, Map.of("fields", List.of("*"), "message",
                        "Authentication disabled - all fields editable"));
                return;
            }

            String entityName = req.query("entity");
            if (entityName == null || entityName.isBlank()) {
                res.json(400, Map.of("error", "entity parameter required"));
                return;
            }

            if (permissionService == null) {
                res.json(503, Map.of("error", "Permission service not available"));
                return;
            }

            try {
                List<String> editableFields = permissionService.getEditableFields(userId, entityName);
                res.json(200, Map.of(
                        "entity", entityName,
                        "userId", userId,
                        "fields", editableFields,
                        "count", editableFields.size()));
            } catch (Exception e) {
                LOG.error("Failed to get editable fields for user {} entity {}", userId, entityName, e);
                res.json(500, ErrorHandler.errorDetails(e));
            }
        });

        router.get("/api/field-permissions/{id}", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            if (AuthService.authEnabled(cfg)) {
                String tok = AuthService.extractToken(req);
                if (!AuthService.hasAdmin(tok, cfg)) {
                    res.json(401, Map.of("error", "unauthorized"));
                    return;
                }
            }

            String id = req.pathParam("id");
            try (Connection conn = JdbcManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement("SELECT * FROM field_permission WHERE id = ?")) {
                stmt.setString(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Map<String, Object> perm = new LinkedHashMap<>();
                        perm.put("id", rs.getString("id"));
                        perm.put("roleId", rs.getString("role_id"));
                        perm.put("entityName", rs.getString("entity_name"));
                        perm.put("fieldName", rs.getString("field_name"));
                        perm.put("canRead", rs.getBoolean("can_read"));
                        perm.put("canEdit", rs.getBoolean("can_edit"));
                        perm.put("createdAt", rs.getTimestamp("created_at"));
                        perm.put("updatedAt", rs.getTimestamp("updated_at"));
                        res.json(200, perm);
                    } else {
                        res.json(404, Map.of("error", "Field permission not found"));
                    }
                }
            } catch (SQLException e) {
                LOG.error("Failed to fetch field permission", e);
                res.json(500, ErrorHandler.errorDetails(e));
            }
        });

        router.post("/api/field-permissions", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            if (AuthService.authEnabled(cfg)) {
                String tok = AuthService.extractToken(req);
                if (!AuthService.hasAdmin(tok, cfg)) {
                    res.json(401, Map.of("error", "unauthorized"));
                    return;
                }
            }

            Map<String, Object> data = req.readJson(new TypeReference<>() {
            });

            String roleId = (String) data.get("roleId");
            String entityName = (String) data.get("entityName");
            String fieldName = (String) data.get("fieldName");
            Boolean canRead = (Boolean) data.getOrDefault("canRead", false);
            Boolean canEdit = (Boolean) data.getOrDefault("canEdit", false);

            if (roleId == null || entityName == null || fieldName == null) {
                res.json(400, Map.of("error", "roleId, entityName, and fieldName are required"));
                return;
            }

            try (Connection conn = JdbcManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(
                            "INSERT INTO field_permission (id, role_id, entity_name, field_name, can_read, can_edit, created_at, updated_at) "
                                    + "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
                String id = java.util.UUID.randomUUID().toString();
                stmt.setString(1, id);
                stmt.setString(2, roleId);
                stmt.setString(3, entityName);
                stmt.setString(4, fieldName);
                stmt.setBoolean(5, canRead);
                stmt.setBoolean(6, canEdit);
                int inserted = stmt.executeUpdate();
                if (inserted > 0) {
                    if (permissionService != null) {
                        permissionService.clearAllCaches();
                    }
                    res.json(201, Map.of("id", id, "message", "Field permission created"));
                } else {
                    res.json(500, Map.of("error", "Failed to create field permission"));
                }
            } catch (SQLException e) {
                LOG.error("Failed to create field permission", e);
                res.json(500, ErrorHandler.errorDetails(e));
            }
        });

        router.put("/api/field-permissions/{id}", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            if (AuthService.authEnabled(cfg)) {
                String tok = AuthService.extractToken(req);
                if (!AuthService.hasAdmin(tok, cfg)) {
                    res.json(401, Map.of("error", "unauthorized"));
                    return;
                }
            }

            String id = req.pathParam("id");
            Map<String, Object> data = req.readJson(new TypeReference<>() {
            });
            Boolean canRead = (Boolean) data.get("canRead");
            Boolean canEdit = (Boolean) data.get("canEdit");

            if (canRead == null && canEdit == null) {
                res.json(400, Map.of("error", "At least one of canRead or canEdit must be provided"));
                return;
            }

            try (Connection conn = JdbcManager.getConnection()) {
                StringBuilder sql = new StringBuilder("UPDATE field_permission SET updated_at = CURRENT_TIMESTAMP");
                List<Object> params = new ArrayList<>();
                if (canRead != null) {
                    sql.append(", can_read = ?");
                    params.add(canRead);
                }
                if (canEdit != null) {
                    sql.append(", can_edit = ?");
                    params.add(canEdit);
                }
                sql.append(" WHERE id = ?");
                params.add(id);

                try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                    for (int i = 0; i < params.size(); i++) {
                        stmt.setObject(i + 1, params.get(i));
                    }
                    int updated = stmt.executeUpdate();
                    if (updated > 0) {
                        if (permissionService != null) {
                            permissionService.clearAllCaches();
                        }
                        res.json(200, Map.of("updated", updated, "message", "Field permission updated"));
                    } else {
                        res.json(404, Map.of("error", "Field permission not found"));
                    }
                }
            } catch (SQLException e) {
                LOG.error("Failed to update field permission", e);
                res.json(500, ErrorHandler.errorDetails(e));
            }
        });

        router.delete("/api/field-permissions/{id}", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            if (AuthService.authEnabled(cfg)) {
                String tok = AuthService.extractToken(req);
                if (!AuthService.hasAdmin(tok, cfg)) {
                    res.json(401, Map.of("error", "unauthorized"));
                    return;
                }
            }

            String id = req.pathParam("id");
            try (Connection conn = JdbcManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement("DELETE FROM field_permission WHERE id = ?")) {
                stmt.setString(1, id);
                int deleted = stmt.executeUpdate();
                if (deleted > 0) {
                    if (permissionService != null) {
                        permissionService.clearAllCaches();
                    }
                    res.json(200, Map.of("deleted", deleted, "message", "Field permission deleted"));
                } else {
                    res.json(404, Map.of("error", "Field permission not found"));
                }
            } catch (SQLException e) {
                LOG.error("Failed to delete field permission", e);
                res.json(500, ErrorHandler.errorDetails(e));
            }
        });

        // ==================== ENTITY CRUD ====================
        router.post("/api/{entity}", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            String actor = "anonymous";
            if (AuthService.authEnabled(cfg)) {
                String tok = AuthService.extractToken(req);
                actor = (tok != null && !tok.isBlank()) ? tok : "anonymous";
                if (!AuthService.hasAdmin(tok, cfg)) {
                    res.json(401, Map.of("error", "unauthorized"));
                    return;
                }
            }

            String entity = req.pathParam("entity");
            EntitySchema schema = SchemaManager.loadSchema(entity);
            if (schema == null) {
                res.json(404, Map.of("error", "unknown entity"));
                return;
            }

            Map<String, Object> data = req.readJson(new TypeReference<>() {
            });
            try {
                Object idObj = crud.insertRecord(schema, data);
                Map<String, Object> after = crud.getById(schema, idObj);
                String id = String.valueOf(idObj);
                AuditLogService.log("INSERT", schema.getName(), id, actor, null, after);

                if (after != null) {
                    try {
                        com.appbana.workflow.api.WorkflowApi.checkAndStartWorkflows(
                                schema.getName(), "ON_CREATE", id, after);
                    } catch (Exception e) {
                        LOG.warn("Workflow trigger failed ON_CREATE {} id={}: {}", schema.getName(), id,
                                e.getMessage());
                    }
                }

                Map<String, Object> response = new LinkedHashMap<>();
                response.put("id", idObj);
                res.json(201, response);
            } catch (IllegalArgumentException e) {
                // Sprint 3 task 3.1 — validation errors surface as 400 with
                // a structured {errors: {fieldName: reason}} payload so the
                // runtime can render them inline under the offending input.
                LOG.warn("Insert validation failed for entity={}: {}", entity, e.getMessage());
                res.json(400, ErrorHandler.fieldValidationError(e));
            } catch (Exception e) {
                LOG.error("Insert failed for entity={}", entity, e);
                res.json(500, ErrorHandler.errorDetails(e));
            }
        });

        router.post("/api/{entity}/batch", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            String actor = "anonymous";
            if (AuthService.authEnabled(cfg)) {
                String tok = AuthService.extractToken(req);
                actor = (tok != null && !tok.isBlank()) ? tok : "anonymous";
                if (!AuthService.hasAdmin(tok, cfg)) {
                    res.json(401, Map.of("error", "unauthorized"));
                    return;
                }
            }

            String entity = req.pathParam("entity");
            EntitySchema schema = SchemaManager.loadSchema(entity);
            if (schema == null) {
                res.json(404, Map.of("error", "unknown entity"));
                return;
            }

            List<Map<String, Object>> payload;
            try {
                payload = req.readJson(new TypeReference<>() {
                });
            } catch (Exception e) {
                res.json(400, Map.of("error", "invalid json array"));
                return;
            }
            if (payload == null) {
                res.json(400, Map.of("error", "array required"));
                return;
            }
            int max = 1000;
            if (payload.size() > max) {
                res.json(400, Map.of("error", "batch too large", "max", max));
                return;
            }

            try {
                Map<String, Object> out = crud.insertBatch(schema, payload);
                Object idsObj = out.get("ids");
                if (idsObj instanceof List<?> idList) {
                    for (Object idVal : idList) {
                        if (idVal == null)
                            continue;
                        try {
                            Map<String, Object> after = crud.getById(schema, String.valueOf(idVal));
                            AuditLogService.log("INSERT", schema.getName(), String.valueOf(idVal), actor, null, after);
                        } catch (Exception ignore) {
                        }
                    }
                }
                res.json(201, out);
            } catch (Exception e) {
                LOG.error("Batch insert failed for {}", entity, e);
                res.json(500, ErrorHandler.errorDetails(e));
            }
        });

        router.get("/api/{entity}", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            if (AuthService.authEnabled(cfg)) {
                String tok = AuthService.extractToken(req);
                if (!AuthService.hasRead(tok, cfg)) {
                    res.json(401, Map.of("error", "unauthorized"));
                    return;
                }
            }

            String entity = req.pathParam("entity");
            EntitySchema schema = SchemaManager.loadSchema(entity);
            if (schema == null) {
                res.json(404, Map.of("error", "unknown entity"));
                return;
            }

            String limitS = req.query("limit");
            String offsetS = req.query("offset");
            String q = req.query("q");
            String fieldsParam = req.query("fields");
            String sortParam = req.query("sort");
            String filterParam = req.query("filter");
            String countFlag = req.query("count");
            // Phase B5 — group by a single column. When set, we fetch the
            // filtered rows (respecting limit) and bucket them in Java.
            String groupByParam = req.query("groupBy");

            Map<String, Object> filters = crud.parseFilters(filterParam, schema);
            boolean countOnly = "true".equalsIgnoreCase(countFlag) || (countFlag != null && countFlag.equals("1"));

            Integer limit = null;
            Integer offset = null;
            boolean anyAdv = countOnly || q != null || fieldsParam != null || sortParam != null || filterParam != null
                    || limitS != null || offsetS != null;
            if (limitS != null || offsetS != null || q != null || fieldsParam != null || sortParam != null
                    || filterParam != null) {
                try {
                    limit = limitS != null ? Integer.parseInt(limitS) : 50;
                } catch (Exception ignore) {
                    limit = 50;
                }
                try {
                    offset = offsetS != null ? Integer.parseInt(offsetS) : 0;
                } catch (Exception ignore) {
                    offset = 0;
                }
                if (limit <= 0)
                    limit = 50;
                if (limit > 500)
                    limit = 500;
                if (offset < 0)
                    offset = 0;
            }

            try {
                if (!anyAdv) {
                    List<Map<String, Object>> rows = crud.listAll(schema);
                    if (permissionService != null && AuthService.authEnabled(cfg)) {
                        String userId = AuthService.extractUserId(req, cfg);
                        if (userId != null) {
                            List<Map<String, Object>> filtered = new ArrayList<>();
                            for (Map<String, Object> row : rows) {
                                filtered.add(permissionService.filterReadableFields(userId, entity, row));
                            }
                            rows = filtered;
                        }
                    }
                    res.json(200, rows);
                } else {
                    if (countOnly) {
                        long total = crud.countOnly(schema, q, filters);
                        Map<String, Object> out = new LinkedHashMap<>();
                        out.put("total", total);
                        if (q != null && !q.isBlank())
                            out.put("query", q);
                        if (!filters.isEmpty())
                            out.put("filters", filters);
                        res.json(200, out);
                    } else {
                        Map<String, Object> out = crud.listAdvanced(schema,
                                limit != null ? limit : 50,
                                offset != null ? offset : 0,
                                q,
                                fieldsParam,
                                sortParam,
                                filters);

                        if (permissionService != null && AuthService.authEnabled(cfg)) {
                            String userId = AuthService.extractUserId(req, cfg);
                            Object rowsObj = out.get("rows");
                            if (userId != null && rowsObj instanceof List<?> rowsList) {
                                List<Map<String, Object>> filtered = new ArrayList<>();
                                for (Object item : rowsList) {
                                    if (item instanceof Map<?, ?> row) {
                                        @SuppressWarnings("unchecked")
                                        Map<String, Object> typedRow = (Map<String, Object>) row;
                                        filtered.add(permissionService.filterReadableFields(userId, entity, typedRow));
                                    }
                                }
                                out.put("rows", filtered);
                            }
                        }

                        // Phase B5 — bucket the returned rows by a single column
                        // when the caller asked for it. Kept in Java so we don't
                        // have to teach every JDBC dialect a new GROUP BY path.
                        //
                        // H6 hardening: also compute TRUE per-group counts across
                        // the whole filtered dataset (not just the current page)
                        // via a real SQL GROUP BY. The `groups` field remains a
                        // per-page bucketing for backwards compat; the new
                        // `groupCounts` map is what UIs should show for accurate
                        // totals ("Active (127)", "Pending (43)", ...) even when
                        // there are more rows than the page size.
                        if (groupByParam != null && !groupByParam.isBlank()) {
                            Object rowsObj = out.get("rows");
                            if (rowsObj instanceof List<?> rowsList) {
                                Map<String, List<Object>> buckets = new LinkedHashMap<>();
                                for (Object item : rowsList) {
                                    if (item instanceof Map<?, ?> row) {
                                        Object key = row.get(groupByParam);
                                        String keyStr = key == null ? "" : String.valueOf(key);
                                        buckets.computeIfAbsent(keyStr, k -> new ArrayList<>()).add(item);
                                    }
                                }
                                List<Map<String, Object>> groups = new ArrayList<>(buckets.size());
                                for (Map.Entry<String, List<Object>> entry : buckets.entrySet()) {
                                    Map<String, Object> g = new LinkedHashMap<>();
                                    g.put("key", entry.getKey());
                                    g.put("count", entry.getValue().size());
                                    g.put("rows", entry.getValue());
                                    groups.add(g);
                                }
                                out.put("groups", groups);
                                out.put("groupBy", groupByParam);
                                // H6 — true, whole-dataset counts. Silently skipped
                                // (empty map) if the column doesn't exist on the
                                // schema, matching the guard in countByGroup.
                                try {
                                    Map<String, Long> trueCounts = crud.countByGroup(schema, groupByParam, q, filters);
                                    out.put("groupCounts", trueCounts);
                                } catch (SQLException groupErr) {
                                    LOG.warn("[GROUP-BY] countByGroup failed for {}.{}: {}",
                                            entity, groupByParam, groupErr.getMessage());
                                }
                            }
                        }

                        res.json(200, out);
                    }
                }
            } catch (SQLException e) {
                LOG.error("List failed for entity {}", entity, e);
                res.json(500, ErrorHandler.errorDetails(e));
            }
        });

        router.get("/api/{entity}/{id}", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            if (AuthService.authEnabled(cfg)) {
                String tok = AuthService.extractToken(req);
                if (!AuthService.hasRead(tok, cfg)) {
                    res.json(401, Map.of("error", "unauthorized"));
                    return;
                }
            }

            String entity = req.pathParam("entity");
            String idStr = req.pathParam("id");
            EntitySchema schema = SchemaManager.loadSchema(entity);
            if (schema == null) {
                res.json(404, Map.of("error", "unknown entity"));
                return;
            }

            try {
                Map<String, Object> row = crud.getById(schema, idStr);
                if (row == null) {
                    res.json(404, Map.of("error", "not found"));
                } else {
                    if (permissionService != null && AuthService.authEnabled(cfg)) {
                        String userId = AuthService.extractUserId(req, cfg);
                        if (userId != null) {
                            row = permissionService.filterReadableFields(userId, entity, row);
                        }
                    }
                    res.json(200, row);
                }
            } catch (SQLException e) {
                LOG.error("Get by id failed for entity {} id {}", entity, idStr, e);
                res.json(500, ErrorHandler.errorDetails(e));
            }
        });

        router.put("/api/{entity}/{id}", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            String actor = "anonymous";
            if (AuthService.authEnabled(cfg)) {
                String tok = AuthService.extractToken(req);
                actor = (tok != null && !tok.isBlank()) ? tok : "anonymous";
                if (!AuthService.hasAdmin(tok, cfg)) {
                    res.json(401, Map.of("error", "unauthorized"));
                    return;
                }
            }

            String entity = req.pathParam("entity");
            String idStr = req.pathParam("id");
            EntitySchema schema = SchemaManager.loadSchema(entity);
            if (schema == null) {
                res.json(404, Map.of("error", "unknown entity"));
                return;
            }

            Map<String, Object> data = req.readJson(new TypeReference<>() {
            });

            try {
                if (permissionService != null && AuthService.authEnabled(cfg)) {
                    String userId = AuthService.extractUserId(req, cfg);
                    if (userId != null) {
                        try {
                            permissionService.validateEditableFields(userId, entity, data);
                        } catch (SecurityException se) {
                            res.json(403, Map.of("error", "forbidden", "message", se.getMessage()));
                            return;
                        }
                    }
                }

                Map<String, Object> before = crud.getById(schema, idStr);
                int updated = crud.updateById(schema, idStr, data);
                Map<String, Object> after = updated > 0 ? crud.getById(schema, idStr) : null;
                if (updated > 0) {
                    AuditLogService.log("UPDATE", schema.getName(), idStr, actor, before, after);
                    try {
                        com.appbana.workflow.api.WorkflowApi.checkAndStartWorkflows(
                                schema.getName(), "ON_UPDATE", idStr, after);
                    } catch (Exception e) {
                        LOG.warn("Workflow trigger failed ON_UPDATE {} id={}: {}", schema.getName(), idStr,
                                e.getMessage());
                    }
                }
                res.json(200, Map.of("updated", updated));
            } catch (IllegalArgumentException e) {
                // Sprint 3 task 3.1 — same structured 400 shape as POST.
                LOG.warn("Update validation failed for entity {} id {}: {}", entity, idStr, e.getMessage());
                res.json(400, ErrorHandler.fieldValidationError(e));
            } catch (SQLException e) {
                LOG.error("Update failed for entity {} id {}", entity, idStr, e);
                res.json(500, ErrorHandler.errorDetails(e));
            }
        });

        router.delete("/api/{entity}/{id}", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            String actor = "anonymous";
            if (AuthService.authEnabled(cfg)) {
                String tok = AuthService.extractToken(req);
                actor = (tok != null && !tok.isBlank()) ? tok : "anonymous";
                if (!AuthService.hasAdmin(tok, cfg)) {
                    res.json(401, Map.of("error", "unauthorized"));
                    return;
                }
            }

            String entity = req.pathParam("entity");
            String idStr = req.pathParam("id");
            EntitySchema schema = SchemaManager.loadSchema(entity);
            if (schema == null) {
                res.json(404, Map.of("error", "unknown entity"));
                return;
            }

            try {
                Map<String, Object> before = crud.getById(schema, idStr);
                int deleted = crud.deleteById(schema, idStr);
                if (deleted > 0) {
                    AuditLogService.log("DELETE", schema.getName(), idStr, actor, before, null);
                }
                res.json(200, Map.of("deleted", deleted));
            } catch (SQLException e) {
                LOG.error("Delete failed for entity {} id {}", entity, idStr, e);
                res.json(500, ErrorHandler.errorDetails(e));
            }
        });

        router.post("/api/{entity}/bulk-delete", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            String actor = "anonymous";
            if (AuthService.authEnabled(cfg)) {
                String tok = AuthService.extractToken(req);
                actor = (tok != null && !tok.isBlank()) ? tok : "anonymous";
                if (!AuthService.hasAdmin(tok, cfg)) {
                    res.json(401, Map.of("error", "unauthorized"));
                    return;
                }
            }

            String entity = req.pathParam("entity");
            EntitySchema schema = SchemaManager.loadSchema(entity);
            if (schema == null) {
                res.json(404, Map.of("error", "unknown entity"));
                return;
            }

            Map<String, Object> body = req.readJson(new TypeReference<>() {
            });
            Object idsObj = body != null ? body.get("ids") : null;
            if (!(idsObj instanceof List<?> ids)) {
                res.json(400, Map.of("error", "ids array required"));
                return;
            }
            int max = 1000;
            if (ids.size() > max) {
                res.json(400, Map.of("error", "too many ids", "max", max));
                return;
            }

            int deletedCount = 0;
            List<Object> deletedIds = new ArrayList<>();
            for (Object idVal : ids) {
                if (idVal == null)
                    continue;
                String idStr = String.valueOf(idVal);
                try {
                    Map<String, Object> before = crud.getById(schema, idStr);
                    int d = crud.deleteById(schema, idStr);
                    if (d > 0) {
                        deletedCount += d;
                        deletedIds.add(idVal);
                        AuditLogService.log("DELETE", schema.getName(), idStr, actor, before, null);
                    }
                } catch (SQLException e) {
                    LOG.warn("Bulk delete failed for {} id {}: {}", entity, idStr, e.getMessage());
                }
            }
            res.json(200, Map.of("deleted", deletedCount, "ids", deletedIds));
        });

        router.post("/api/{entity}/bulk-export", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            if (AuthService.authEnabled(cfg)) {
                String tok = AuthService.extractToken(req);
                if (!AuthService.hasRead(tok, cfg)) {
                    res.json(401, Map.of("error", "unauthorized"));
                    return;
                }
            }

            String entity = req.pathParam("entity");
            EntitySchema schema = SchemaManager.loadSchema(entity);
            if (schema == null) {
                res.json(404, Map.of("error", "unknown entity"));
                return;
            }

            Map<String, Object> body = req.readJson(new TypeReference<>() {
            });
            Object idsObj = body != null ? body.get("ids") : null;
            if (!(idsObj instanceof List<?> ids)) {
                res.json(400, Map.of("error", "ids array required"));
                return;
            }
            int max = 5000;
            if (ids.size() > max) {
                res.json(400, Map.of("error", "too many ids", "max", max));
                return;
            }

            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object idVal : ids) {
                if (idVal == null)
                    continue;
                String idStr = String.valueOf(idVal);
                try {
                    Map<String, Object> row = crud.getById(schema, idStr);
                    if (row != null)
                        rows.add(row);
                } catch (SQLException e) {
                    LOG.warn("Bulk export failed for {} id {}: {}", entity, idStr, e.getMessage());
                }
            }

            if (permissionService != null && AuthService.authEnabled(cfg)) {
                String userId = AuthService.extractUserId(req, cfg);
                if (userId != null) {
                    List<Map<String, Object>> filtered = new ArrayList<>();
                    for (Map<String, Object> row : rows) {
                        filtered.add(permissionService.filterReadableFields(userId, entity, row));
                    }
                    rows = filtered;
                }
            }

            res.json(200, Map.of("count", rows.size(), "rows", rows));
        });

        // ==================== APP-SCOPED ENTITY ROUTES (Story 1.5)
        // ====================
        // These routes fix the "Magic Seed Data" bug by ensuring entities are properly
        // scoped to apps using TenantContext.
        //
        // Pattern: /studio/apps/{appId}/{entity}
        // - Extract appId from URL path
        // - Set TenantContext (tenant="default", app=appId)
        // - Call EntityCrudService which auto-filters by tenant_id/app_id
        // - Return only entities scoped to that app

        // POST /appbana-studio/{tenantId}/apps/{appId}/{entity} - Create entity scoped
        // to tenant and app
        router.post("/appbana-studio/{tenantId}/apps/{appId}/{entity}", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("appId");
            String entity = req.pathParam("entity");

            if (tenantId == null || tenantId.isBlank()) {
                res.json(400, Map.of("error", "tenantId required"));
                return;
            }
            if (appId == null || appId.isBlank()) {
                res.json(400, Map.of("error", "appId required"));
                return;
            }

            EntitySchema schema = SchemaManager.loadSchema(appId, entity, tenantId);
            if (schema == null) {
                res.json(404, Map.of("error", "unknown entity: " + entity));
                return;
            }

            Map<String, Object> data = req.readJson(new TypeReference<>() {
            });

            try {
                // Set TenantContext for this request from URL path parameters
                TenantContext ctx = new TenantContext(tenantId, appId);
                TenantContext.set(ctx);

                try {
                    // EntityCrudService will auto-inject tenant_id and app_id
                    Object idObj = crud.insertRecord(schema, data);
                    Map<String, Object> after = crud.getById(schema, idObj);
                    String id = String.valueOf(idObj);

                    // Audit logging
                    AuditLogService.log("INSERT", schema.getName(), id, "studio", null, after);

                    res.json(201, Map.of("id", idObj, "appId", appId));
                } finally {
                    // Always clear context
                    TenantContext.clear();
                }
            } catch (Exception e) {
                LOG.error("App-scoped insert failed for app={} entity={}", appId, entity, e);
                res.json(500, ErrorHandler.errorDetails(e));
            }
        });

        // GET /appbana-studio/{tenantId}/apps/{appId}/{entity} - List entities scoped
        // to tenant and app (supports filtering, sorting, pagination)
        router.get("/appbana-studio/{tenantId}/apps/{appId}/{entity}", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("appId");
            String entity = req.pathParam("entity");

            if (tenantId == null || tenantId.isBlank()) {
                res.json(400, Map.of("error", "tenantId required"));
                return;
            }
            if (appId == null || appId.isBlank()) {
                res.json(400, Map.of("error", "appId required"));
                return;
            }

            EntitySchema schema = SchemaManager.loadSchema(appId, entity, tenantId);
            if (schema == null) {
                res.json(404, Map.of("error", "unknown entity: " + entity));
                return;
            }

            // Parse query parameters for filtering, sorting, and pagination
            String limitS = req.query("limit");
            String offsetS = req.query("offset");
            String q = req.query("q");
            String fieldsParam = req.query("fields");
            String sortParam = req.query("sort");
            String filterParam = req.query("filter");

            LOG.info("[STUDIO-LIST] entity={}, filter={}, sort={}, limit={}, offset={}", 
                     entity, filterParam, sortParam, limitS, offsetS);

            Map<String, Object> filters = crud.parseFilters(filterParam, schema);

            Integer limit = null;
            Integer offset = null;
            boolean anyAdv = q != null || fieldsParam != null || sortParam != null || filterParam != null
                    || limitS != null || offsetS != null;
            if (anyAdv) {
                try {
                    limit = limitS != null ? Integer.parseInt(limitS) : 50;
                } catch (Exception ignore) {
                    limit = 50;
                }
                try {
                    offset = offsetS != null ? Integer.parseInt(offsetS) : 0;
                } catch (Exception ignore) {
                    offset = 0;
                }
                if (limit <= 0) limit = 50;
                if (limit > 500) limit = 500;
                if (offset < 0) offset = 0;
            }

            try {
                // Set TenantContext for this request from URL path parameters
                TenantContext ctx = new TenantContext(tenantId, appId);
                TenantContext.set(ctx);

                try {
                    if (!anyAdv) {
                        // Simple list without filtering/pagination
                        List<Map<String, Object>> rows = crud.listAll(schema);
                        res.json(200, Map.of("appId", appId, "entity", entity, "count", rows.size(), "rows", rows));
                    } else {
                        // Advanced list with filtering, sorting, pagination
                        Map<String, Object> out = crud.listAdvanced(schema,
                                limit != null ? limit : 50,
                                offset != null ? offset : 0,
                                q,
                                fieldsParam,
                                sortParam,
                                filters);
                        out.put("appId", appId);
                        out.put("entity", entity);
                        res.json(200, out);
                    }
                } finally {
                    TenantContext.clear();
                }
            } catch (SQLException e) {
                LOG.error("App-scoped list failed for app={} entity={}", appId, entity, e);
                res.json(500, ErrorHandler.errorDetails(e));
            }
        });

        // GET /appbana-studio/{tenantId}/apps/{appId}/{entity}/{id} - Get entity by ID
        // scoped to tenant and app
        router.get("/appbana-studio/{tenantId}/apps/{appId}/{entity}/{id}", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("appId");
            String entity = req.pathParam("entity");
            String idStr = req.pathParam("id");

            if (tenantId == null || tenantId.isBlank()) {
                res.json(400, Map.of("error", "tenantId required"));
                return;
            }
            if (appId == null || appId.isBlank()) {
                res.json(400, Map.of("error", "appId required"));
                return;
            }

            EntitySchema schema = SchemaManager.loadSchema(appId, entity, tenantId);
            if (schema == null) {
                res.json(404, Map.of("error", "unknown entity: " + entity));
                return;
            }

            try {
                TenantContext ctx = new TenantContext(tenantId, appId);
                TenantContext.set(ctx);

                try {
                    Map<String, Object> row = crud.getById(schema, idStr);
                    if (row == null) {
                        res.json(404, Map.of("error", "not found", "appId", appId, "entity", entity, "id", idStr));
                    } else {
                        res.json(200, row);
                    }
                } finally {
                    TenantContext.clear();
                }
            } catch (SQLException e) {
                LOG.error("App-scoped get failed for app={} entity={} id={}", appId, entity, idStr, e);
                res.json(500, ErrorHandler.errorDetails(e));
            }
        });

        // PUT /appbana-studio/{tenantId}/apps/{appId}/{entity}/{id} - Update entity
        // scoped to tenant and app
        router.put("/appbana-studio/{tenantId}/apps/{appId}/{entity}/{id}", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("appId");
            String entity = req.pathParam("entity");
            String idStr = req.pathParam("id");

            if (tenantId == null || tenantId.isBlank()) {
                res.json(400, Map.of("error", "tenantId required"));
                return;
            }
            if (appId == null || appId.isBlank()) {
                res.json(400, Map.of("error", "appId required"));
                return;
            }

            EntitySchema schema = SchemaManager.loadSchema(appId, entity, tenantId);
            if (schema == null) {
                res.json(404, Map.of("error", "unknown entity: " + entity));
                return;
            }

            Map<String, Object> data = req.readJson(new TypeReference<>() {
            });

            try {
                TenantContext ctx = new TenantContext(tenantId, appId);
                TenantContext.set(ctx);

                try {
                    Map<String, Object> before = crud.getById(schema, idStr);
                    int updated = crud.updateById(schema, idStr, data);
                    Map<String, Object> after = updated > 0 ? crud.getById(schema, idStr) : null;

                    if (updated > 0) {
                        AuditLogService.log("UPDATE", schema.getName(), idStr, "studio", before, after);
                    }

                    res.json(200, Map.of("updated", updated, "appId", appId));
                } finally {
                    TenantContext.clear();
                }
            } catch (SQLException e) {
                LOG.error("App-scoped update failed for app={} entity={} id={}", appId, entity, idStr, e);
                res.json(500, ErrorHandler.errorDetails(e));
            }
        });

        // DELETE /appbana-studio/{tenantId}/apps/{appId}/{entity}/{id} - Delete entity
        // scoped to tenant and app
        router.delete("/appbana-studio/{tenantId}/apps/{appId}/{entity}/{id}", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("appId");
            String entity = req.pathParam("entity");
            String idStr = req.pathParam("id");

            if (tenantId == null || tenantId.isBlank()) {
                res.json(400, Map.of("error", "tenantId required"));
                return;
            }
            if (appId == null || appId.isBlank()) {
                res.json(400, Map.of("error", "appId required"));
                return;
            }

            EntitySchema schema = SchemaManager.loadSchema(appId, entity, tenantId);
            if (schema == null) {
                res.json(404, Map.of("error", "unknown entity: " + entity));
                return;
            }

            try {
                TenantContext ctx = new TenantContext(tenantId, appId);
                TenantContext.set(ctx);

                try {
                    Map<String, Object> before = crud.getById(schema, idStr);
                    int deleted = crud.deleteById(schema, idStr);

                    if (deleted > 0) {
                        AuditLogService.log("DELETE", schema.getName(), idStr, "studio", before, null);
                    }

                    res.json(200, Map.of("deleted", deleted, "appId", appId));
                } finally {
                    TenantContext.clear();
                }
            } catch (SQLException e) {
                LOG.error("App-scoped delete failed for app={} entity={} id={}", appId, entity, idStr, e);
                res.json(500, ErrorHandler.errorDetails(e));
            }
        });

        // ==================== RUNTIME APP-SCOPED ENTITY ROUTES ====================
        // Similar to studio routes above, but exposed at /api for runtime apps
        // No authentication required - handled by SessionMiddleware exclusion

        // POST /api/{tenantId}/apps/{appId}/{entity} - Runtime entity creation
        router.post("/api/{tenantId}/apps/{appId}/{entity}", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("appId");
            String entity = req.pathParam("entity");

            if (tenantId == null || tenantId.isBlank()) {
                res.json(400, Map.of("error", "tenantId required"));
                return;
            }
            if (appId == null || appId.isBlank()) {
                res.json(400, Map.of("error", "appId required"));
                return;
            }

            EntitySchema schema = SchemaManager.loadSchema(appId, entity, tenantId);
            if (schema == null) {
                res.json(404, Map.of("error", "unknown entity: " + entity));
                return;
            }

            Map<String, Object> data = req.readJson(new TypeReference<>() {
            });

            try {
                // Set TenantContext for this request from URL path parameters
                TenantContext ctx = new TenantContext(tenantId, appId);
                TenantContext.set(ctx);

                try {
                    // EntityCrudService will auto-inject tenant_id and app_id
                    Object idObj = crud.insertRecord(schema, data);
                    Map<String, Object> after = crud.getById(schema, idObj);
                    String id = String.valueOf(idObj);

                    // Audit logging
                    AuditLogService.log("INSERT", schema.getName(), id, "runtime", null, after);

                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("id", idObj);
                    response.put("appId", appId);
                    res.json(201, response);
                } finally {
                    // Always clear context
                    TenantContext.clear();
                }
            } catch (Exception e) {
                LOG.error("Runtime app-scoped insert failed for app={} entity={}", appId, entity, e);
                res.json(500, ErrorHandler.errorDetails(e));
            }
        });
// ==================== ENVIRONMENT-SPECIFIC ENTITY CRUD ====================
        // These routes handle SIT/PROD environments with separate data isolation
        // URL pattern: /api/{tenantId}/apps/{appId}/env/{env}/{entity}
        
        // POST /api/{tenantId}/apps/{appId}/env/{env}/{entity} - Create entity in specific environment
        router.post("/api/{tenantId}/apps/{appId}/env/{env}/{entity}", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("appId");
            String env = req.pathParam("env");
            String entity = req.pathParam("entity");

            LOG.info("[ENV-CREATE] Request: tenant={}, app={}, env={}, entity={}", tenantId, appId, env, entity);
            if (tenantId == null || tenantId.isBlank()) {
                res.json(400, Map.of("error", "tenantId required"));
                return;
            }
            if (appId == null || appId.isBlank()) {
                res.json(400, Map.of("error", "appId required"));
                return;
            }
            if (env == null || env.isBlank()) {
                res.json(400, Map.of("error", "env required"));
                return;
            }

            EntitySchema schema = SchemaManager.loadSchema(appId, entity, tenantId);
            if (schema == null) {
                LOG.warn("[ENV-CREATE] Schema not found: tenant={}, app={}, entity={}", tenantId, appId, entity);
                res.json(404, Map.of("error", "unknown entity: " + entity));
                return;
            }
            LOG.debug("[ENV-CREATE] Schema loaded successfully for entity: {}", entity);

            Map<String, Object> data = req.readJson(new TypeReference<>() {
            });

            try {
                // Set TenantContext with environment for data isolation
                // Table naming: env_tenant_app_entity (e.g., SIT_t-123_app-456_User)
                LOG.info("[ENV-CREATE] Setting TenantContext: tenant={}, app={}, env={}", tenantId, appId, env);
                LOG.debug("[ENV-CREATE] TenantContext will create table with env prefix if env != DEV");
                TenantContext ctx = new TenantContext(tenantId, appId, env);
                TenantContext.set(ctx);

                try {
                    Object idObj = crud.insertRecord(schema, data);
                    Map<String, Object> after = crud.getById(schema, idObj);
                    String id = String.valueOf(idObj);
                    LOG.info("[ENV-CREATE] Successfully created entity: id={}, entity={}, env={}", idObj, entity, env);
                    LOG.debug("[ENV-CREATE] Audit logged for entity: {} id={}", entity, id);

                    AuditLogService.log("INSERT", schema.getName(), id, "runtime-" + env, null, after);

                    LOG.debug("[ENV-CREATE] Clearing TenantContext for env={}", env);
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("id", idObj);
                    response.put("appId", appId);
                    response.put("env", env);
                    res.json(201, response);
                } finally {
                    TenantContext.clear();
                }
            } catch (Exception e) {
                LOG.error("[ENV-CREATE] Failed to create entity: tenant={}, app={}, env={}, entity={}", tenantId, appId, env, entity, e);
                LOG.error("Env-scoped insert failed for app={} env={} entity={}", appId, env, entity, e);
                res.json(500, ErrorHandler.errorDetails(e));
            }
        });

        // GET /api/{tenantId}/apps/{appId}/env/{env}/{entity} - List entities in specific environment
        router.get("/api/{tenantId}/apps/{appId}/env/{env}/{entity}", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("appId");
            String env = req.pathParam("env");
            String entity = req.pathParam("entity");

            EntitySchema schema = SchemaManager.loadSchema(appId, entity, tenantId);
            if (schema == null) {
                res.json(404, Map.of("error", "unknown entity: " + entity));
                return;
            }

            try {
                TenantContext ctx = new TenantContext(tenantId, appId, env);
                TenantContext.set(ctx);

                try {
                    // Parse query parameters for pagination, filtering, sorting
                    String limitS = req.query("limit");
                    String offsetS = req.query("offset");
                    String fieldsParam = req.query("fields");
                    String sortParam = req.query("sort");
                    String filterParam = req.query("filter");
                    String q = req.query("q");

                    Integer limit = null;
                    Integer offset = null;
                    try {
                        limit = limitS != null ? Integer.parseInt(limitS) : 50;
                    } catch (Exception ignore) {
                        limit = 50;
                    }
                    try {
                        offset = offsetS != null ? Integer.parseInt(offsetS) : 0;
                    } catch (Exception ignore) {
                        offset = 0;
                    }

                    Map<String, Object> filters = crud.parseFilters(filterParam, schema);
                    
                    // Get total count for pagination
                    long total = crud.countOnly(schema, q, filters);
                    
                    // Get paginated rows - listAdvanced returns Map with rows
                    Map<String, Object> advResult = crud.listAdvanced(
                            schema, limit, offset, q, fieldsParam, sortParam, filters);
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> rows = (List<Map<String, Object>>) advResult.get("rows");

                    // Return proper response with rows and total
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("rows", rows);
                    response.put("total", total);
                    response.put("limit", limit);
                    response.put("offset", offset);
                    if (!filters.isEmpty()) {
                        response.put("filters", filters);
                    }
                    res.json(200, response);
                } finally {
                    TenantContext.clear();
                }
            } catch (SQLException e) {
                LOG.error("Env-scoped list failed for app={} env={} entity={}", appId, env, entity, e);
                res.json(500, ErrorHandler.errorDetails(e));
            }
        });

        // GET /api/{tenantId}/apps/{appId}/env/{env}/{entity}/{id} - Get by ID in specific environment
        router.get("/api/{tenantId}/apps/{appId}/env/{env}/{entity}/{id}", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("appId");
            String env = req.pathParam("env");
            String entity = req.pathParam("entity");
            String idStr = req.pathParam("id");

            EntitySchema schema = SchemaManager.loadSchema(appId, entity, tenantId);
            if (schema == null) {
                res.json(404, Map.of("error", "unknown entity: " + entity));
                return;
            }

            try {
                TenantContext ctx = new TenantContext(tenantId, appId, env);
                TenantContext.set(ctx);

                try {
                    Map<String, Object> row = crud.getById(schema, idStr);
                    if (row == null) {
                        res.json(404, Map.of("error", "not found"));
                    } else {
                        res.json(200, row);
                    }
                } finally {
                    TenantContext.clear();
                }
            } catch (SQLException e) {
                LOG.error("Env-scoped get failed for app={} env={} entity={} id={}", appId, env, entity, idStr, e);
                res.json(500, ErrorHandler.errorDetails(e));
            }
        });

        // PUT /api/{tenantId}/apps/{appId}/env/{env}/{entity}/{id} - Update in specific environment
        router.put("/api/{tenantId}/apps/{appId}/env/{env}/{entity}/{id}", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("appId");
            String env = req.pathParam("env");
            String entity = req.pathParam("entity");
            String idStr = req.pathParam("id");

            EntitySchema schema = SchemaManager.loadSchema(appId, entity, tenantId);
            if (schema == null) {
                res.json(404, Map.of("error", "unknown entity: " + entity));
                return;
            }

            Map<String, Object> data = req.readJson(new TypeReference<>() {
            });

            try {
                TenantContext ctx = new TenantContext(tenantId, appId, env);
                TenantContext.set(ctx);

                try {
                    Map<String, Object> before = crud.getById(schema, idStr);
                    int updated = crud.updateById(schema, idStr, data);
                    Map<String, Object> after = updated > 0 ? crud.getById(schema, idStr) : null;
                    
                    if (updated > 0) {
                        AuditLogService.log("UPDATE", schema.getName(), idStr, "runtime-" + env, before, after);
                    }
                    
                    res.json(200, Map.of("updated", updated));
                } finally {
                    TenantContext.clear();
                }
            } catch (SQLException e) {
                LOG.error("Env-scoped update failed for app={} env={} entity={} id={}", appId, env, entity, idStr, e);
                res.json(500, ErrorHandler.errorDetails(e));
            }
        });

        // DELETE /api/{tenantId}/apps/{appId}/env/{env}/{entity}/{id} - Delete in specific environment
        router.delete("/api/{tenantId}/apps/{appId}/env/{env}/{entity}/{id}", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("appId");
            String env = req.pathParam("env");
            String entity = req.pathParam("entity");
            String idStr = req.pathParam("id");

            EntitySchema schema = SchemaManager.loadSchema(appId, entity, tenantId);
            if (schema == null) {
                res.json(404, Map.of("error", "unknown entity: " + entity));
                return;
            }

            try {
                TenantContext ctx = new TenantContext(tenantId, appId, env);
                TenantContext.set(ctx);

                try {
                    Map<String, Object> before = crud.getById(schema, idStr);
                    int deleted = crud.deleteById(schema, idStr);
                    
                    if (deleted > 0) {
                        AuditLogService.log("DELETE", schema.getName(), idStr, "runtime-" + env, before, null);
                    }
                    
                    res.json(200, Map.of("deleted", deleted));
                } finally {
                    TenantContext.clear();
                }
            } catch (SQLException e) {
                LOG.error("Env-scoped delete failed for app={} env={} entity={} id={}", appId, env, entity, idStr, e);
                res.json(500, ErrorHandler.errorDetails(e));
            }
        });
    }
}
