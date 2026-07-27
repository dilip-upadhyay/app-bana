package com.appbana.server.routes;

import com.appbana.AuditLogService;
import com.appbana.JdbcManager;
import com.appbana.SchemaManager;
import com.appbana.api.Router;
import com.appbana.approval.ApprovalService;
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

            // C2.15 — Use shared enforceApprovalPreInsert helper (same guard as batch/studio/runtime/env routes).
            // The actor variable already holds extractUserId; re-use it here as callerUserId.
            enforceApprovalPreInsert(schema, data, AuthService.extractUserId(req, cfg));
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

            // C2.15 — Use shared enforceApprovalPreInsert helper for every batch element.
            // Same guard as single POST, studio POST, runtime POST, env POST.
            {
                String batchUserId = AuthService.extractUserId(req, cfg);
                for (Map<String, Object> element : payload) {
                    enforceApprovalPreInsert(schema, element, batchUserId);
                }
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
            // C2.7 — approval-state filter, checker-only for PENDING.
            String approvalStatusParam = req.query("_approvalStatus");

            Map<String, Object> filters = crud.parseFilters(filterParam, schema);
            boolean approvalFilterApplied;
            try {
                approvalFilterApplied = applyApprovalStatusFilter(schema, null, null, approvalStatusParam, filters,
                        AuthService.extractUserId(req, cfg), AuthService.authEnabled(cfg));
            } catch (ApprovalFilterException afe) {
                res.json(afe.status(), Map.of("error", afe.getMessage()));
                return;
            }
            boolean countOnly = "true".equalsIgnoreCase(countFlag) || (countFlag != null && countFlag.equals("1"));

            Integer limit = null;
            Integer offset = null;
            boolean anyAdv = countOnly || q != null || fieldsParam != null || sortParam != null || filterParam != null
                    || limitS != null || offsetS != null || approvalFilterApplied;
            if (limitS != null || offsetS != null || q != null || fieldsParam != null || sortParam != null
                    || filterParam != null || approvalFilterApplied) {
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

            if (schema.isApprovalRequired()) {
                try {
                    ApprovalPutResult guard = applyApprovalPutGuard(crud, schema, idStr, data,
                            AuthService.extractUserId(req, cfg));
                    switch (guard.action()) {
                        case BLOCKED_PENDING -> {
                            res.json(400, guard.body());
                            return;
                        }
                        case CONFLICT -> {
                            res.json(409, guard.body());
                            return;
                        }
                        case REVISION -> {
                            res.json(200, guard.body());
                            return;
                        }
                        case PROCEED -> { /* fall through to the normal update below */ }
                    }
                } catch (SQLException e) {
                    LOG.error("Failed to check approval status on PUT for entity {} id {}", entity, idStr, e);
                    res.json(500, ErrorHandler.errorDetails(e));
                    return;
                }
            }

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

            // B6 FIX — Block DELETE on approval-enabled entities when record is PENDING.
            // Deleting a PENDING record would orphan the audit trail and bypass the checker queue.
            if (schema.isApprovalRequired()) {
                try {
                    Map<String, Object> existing = crud.getById(schema, idStr);
                    if (existing != null) {
                        Object statusObj = existing.get("approval_status");
                        if (statusObj == null) statusObj = existing.get("APPROVAL_STATUS");
                        String currentStatus = statusObj != null ? String.valueOf(statusObj) : "DRAFT";
                        if ("PENDING".equalsIgnoreCase(currentStatus)) {
                            LOG.warn("[SECURITY] DELETE blocked: entity={} id={} is in PENDING state", entity, idStr);
                            res.json(400, Map.of("error", "Cannot delete record while approval is PENDING"));
                            return;
                        }
                    }
                } catch (SQLException e) {
                    LOG.error("Failed to check approval status on DELETE for entity {} id {}", entity, idStr, e);
                    res.json(500, ErrorHandler.errorDetails(e));
                    return;
                }
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
                // H8: use extractServiceToken for hasAdmin — never pass session IDs to admin gate.
                String tok = AuthService.extractServiceToken(req);
                if (!AuthService.hasAdmin(tok, cfg)) {
                    res.json(401, Map.of("error", "unauthorized"));
                    return;
                }
                // Use authenticated identity for audit, not the raw token literal.
                String uid = AuthService.extractUserId(req, cfg);
                actor = (uid != null && !uid.isBlank()) ? uid : "admin";
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

            // B11 FIX — Block bulk-delete on any PENDING record in an approval-required entity.
            // Same invariant as DELETE /api/{entity}/{id}: deleting PENDING rows orphans the audit trail.
            int deletedCount = 0;
            List<Object> deletedIds = new ArrayList<>();
            List<Object> blockedIds = new ArrayList<>();
            for (Object idVal : ids) {
                if (idVal == null)
                    continue;
                String idStr = String.valueOf(idVal);
                try {
                    Map<String, Object> before = crud.getById(schema, idStr);

                    // Check approval state before deleting if schema requires approval
                    if (schema.isApprovalRequired() && before != null) {
                        Object statusObj = before.get("approval_status");
                        if (statusObj == null) statusObj = before.get("APPROVAL_STATUS");
                        String currentStatus = statusObj != null ? String.valueOf(statusObj) : "DRAFT";
                        if ("PENDING".equalsIgnoreCase(currentStatus)) {
                            LOG.warn("[SECURITY] Bulk-delete blocked for entity={} id={}: record is PENDING", entity, idStr);
                            blockedIds.add(idVal);
                            continue; // skip this ID — do NOT delete
                        }
                    }

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
            Map<String, Object> bulkResult = new LinkedHashMap<>();
            bulkResult.put("deleted", deletedCount);
            bulkResult.put("ids", deletedIds);
            if (!blockedIds.isEmpty()) {
                bulkResult.put("blocked", blockedIds);
                bulkResult.put("blockedReason", "PENDING approval — cannot delete records awaiting checker review");
            }
            res.json(200, bulkResult);
        });

        router.post("/api/{entity}/bulk-export", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            if (AuthService.authEnabled(cfg)) {
                // H8 consistency: use extractServiceToken so session IDs never reach hasRead.
                String tok = AuthService.extractServiceToken(req);
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
        // B10 FIX — Studio POST now applies approval guard + uses authenticated user as audit actor.
        // SessionMiddleware already validated the session for /appbana-studio/* paths, so
        // req.getAttribute("userId") is already populated by the time we arrive here.
        router.post("/appbana-studio/{tenantId}/apps/{appId}/{entity}", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("appId");
            String entity = req.pathParam("entity");

            // Derive caller identity (SessionMiddleware has already validated; this reads the attribute).
            String studioInsertUserId = AuthService.extractUserId(req, cfg);
            if (studioInsertUserId == null || studioInsertUserId.isBlank()) {
                // SessionMiddleware should have caught this; treat as double-check.
                res.json(401, Map.of("error", "Authentication required for studio mutations"));
                return;
            }

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

            // B10 FIX — Apply approval pre-insert guard: strip client-supplied approval columns,
            // force DRAFT status, and bind submitted_by to the authenticated user.
            enforceApprovalPreInsert(schema, data, studioInsertUserId);

            try {
                TenantContext ctx = new TenantContext(tenantId, appId);
                TenantContext.set(ctx);

                try {
                    Object idObj = crud.insertRecord(schema, data);
                    Map<String, Object> after = crud.getById(schema, idObj);
                    String id = String.valueOf(idObj);

                    // M9 FIX — Audit actor is the authenticated studioUserId, not hardcoded "studio".
                    AuditLogService.log("INSERT", schema.getName(), id, studioInsertUserId, null, after);

                    res.json(201, Map.of("id", idObj, "appId", appId));
                } finally {
                    TenantContext.clear();
                }
            } catch (Exception e) {
                LOG.error("Studio insert failed for app={} entity={}", appId, entity, e);
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

            // C2.7 — approval-state filter, checker-only for PENDING.
            String approvalStatusParam = req.query("_approvalStatus");
            boolean approvalFilterApplied;
            try {
                approvalFilterApplied = applyApprovalStatusFilter(schema, tenantId, appId, approvalStatusParam, filters,
                        AuthService.extractUserId(req, ConfigManager.getConfig()),
                        AuthService.authEnabled(ConfigManager.getConfig()));
            } catch (ApprovalFilterException afe) {
                res.json(afe.status(), Map.of("error", afe.getMessage()));
                return;
            }

            Integer limit = null;
            Integer offset = null;
            boolean anyAdv = q != null || fieldsParam != null || sortParam != null || filterParam != null
                    || limitS != null || offsetS != null || approvalFilterApplied;
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
        // B7 FIX — Studio PUT now enforces approval guard and session auth.
        router.put("/appbana-studio/{tenantId}/apps/{appId}/{entity}/{id}", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("appId");
            String entity = req.pathParam("entity");
            String idStr = req.pathParam("id");

            // Session auth required for studio mutations
            String studioUserId = AuthService.extractUserId(req, cfg);
            if (studioUserId == null || studioUserId.isBlank()) {
                res.json(401, Map.of("error", "Authentication required for studio mutations"));
                return;
            }

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

            // B7 FIX — Apply approval guard to studio PUT (same logic as /api/{entity}/{id} PUT).
            // C2.3 — an edit to a live APPROVED row becomes a DRAFT revision instead of an overwrite.
            if (schema.isApprovalRequired()) {
                try {
                    TenantContext.set(new TenantContext(tenantId, appId));
                    try {
                        ApprovalPutResult guard = applyApprovalPutGuard(crud, schema, idStr, data, studioUserId);
                        switch (guard.action()) {
                            case BLOCKED_PENDING -> {
                                res.json(400, guard.body());
                                return;
                            }
                            case CONFLICT -> {
                                res.json(409, guard.body());
                                return;
                            }
                            case REVISION -> {
                                Map<String, Object> body = new LinkedHashMap<>(guard.body());
                                body.put("appId", appId);
                                res.json(200, body);
                                return;
                            }
                            case PROCEED -> { /* fall through to the normal update below */ }
                        }
                    } finally {
                        TenantContext.clear();
                    }
                } catch (SQLException e) {
                    LOG.error("Failed to check approval status on studio PUT for app={} entity={} id={}", appId, entity, idStr, e);
                    res.json(500, ErrorHandler.errorDetails(e));
                    return;
                }
            }

            try {
                TenantContext ctx = new TenantContext(tenantId, appId);
                TenantContext.set(ctx);

                try {
                    Map<String, Object> before = crud.getById(schema, idStr);
                    int updated = crud.updateById(schema, idStr, data);
                    Map<String, Object> after = updated > 0 ? crud.getById(schema, idStr) : null;

                    if (updated > 0) {
                        AuditLogService.log("UPDATE", schema.getName(), idStr, studioUserId, before, after);
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
        // B7 FIX — Studio DELETE now enforces PENDING gate and session auth.
        router.delete("/appbana-studio/{tenantId}/apps/{appId}/{entity}/{id}", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("appId");
            String entity = req.pathParam("entity");
            String idStr = req.pathParam("id");

            // Session auth required for studio mutations
            String studioDeleteUserId = AuthService.extractUserId(req, cfg);
            if (studioDeleteUserId == null || studioDeleteUserId.isBlank()) {
                res.json(401, Map.of("error", "Authentication required for studio mutations"));
                return;
            }

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

            // B7 FIX — Block DELETE on PENDING records via studio route.
            if (schema.isApprovalRequired()) {
                try {
                    TenantContext.set(new TenantContext(tenantId, appId));
                    try {
                        Map<String, Object> existing = crud.getById(schema, idStr);
                        if (existing != null) {
                            Object statusObj = existing.get("approval_status");
                            if (statusObj == null) statusObj = existing.get("APPROVAL_STATUS");
                            String currentStatus = statusObj != null ? String.valueOf(statusObj) : "DRAFT";
                            if ("PENDING".equalsIgnoreCase(currentStatus)) {
                                LOG.warn("[SECURITY] Studio DELETE blocked: app={} entity={} id={} is PENDING", appId, entity, idStr);
                                res.json(400, Map.of("error", "Cannot delete record while approval is PENDING"));
                                return;
                            }
                        }
                    } finally {
                        TenantContext.clear();
                    }
                } catch (SQLException e) {
                    LOG.error("Failed to check approval status on studio DELETE for app={} entity={} id={}", appId, entity, idStr, e);
                    res.json(500, ErrorHandler.errorDetails(e));
                    return;
                }
            }

            try {
                TenantContext ctx = new TenantContext(tenantId, appId);
                TenantContext.set(ctx);

                try {
                    Map<String, Object> before = crud.getById(schema, idStr);
                    int deleted = crud.deleteById(schema, idStr);

                    if (deleted > 0) {
                        AuditLogService.log("DELETE", schema.getName(), idStr, studioDeleteUserId, before, null);
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
        // These routes are hit by deployed runtime applications (real end-users).
        // SessionMiddleware excludes /api/{tenantId}/apps/ paths to allow end-user sessions,
        // but exclusion ≠ "no auth required". Each mutation route MUST enforce its own auth gate.

        // POST /api/{tenantId}/apps/{appId}/{entity} - Runtime entity creation
        // B8 FIX — This is the primary runtime write path. Previously unauthenticated and ungated.
        router.post("/api/{tenantId}/apps/{appId}/{entity}", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("appId");
            String entity = req.pathParam("entity");

            // B8 FIX — Route-level session auth gate. Middleware exclusion ≠ public access.
            String runtimeUserId = AuthService.extractUserId(req, cfg);
            if (runtimeUserId == null || runtimeUserId.isBlank()) {
                res.json(401, Map.of("error", "Authentication required"));
                return;
            }

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

            // B8 FIX — Apply approval pre-insert guard.
            enforceApprovalPreInsert(schema, data, runtimeUserId);

            try {
                TenantContext ctx = new TenantContext(tenantId, appId);
                TenantContext.set(ctx);

                try {
                    Object idObj = crud.insertRecord(schema, data);
                    Map<String, Object> after = crud.getById(schema, idObj);
                    String id = String.valueOf(idObj);

                    AuditLogService.log("INSERT", schema.getName(), id, runtimeUserId, null, after);

                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("id", idObj);
                    response.put("appId", appId);
                    res.json(201, response);
                } finally {
                    TenantContext.clear();
                }
            } catch (Exception e) {
                LOG.error("Runtime app-scoped insert failed for app={} entity={}", appId, entity, e);
                res.json(500, ErrorHandler.errorDetails(e));
            }
        });
        // ==================== ENVIRONMENT-SPECIFIC ENTITY CRUD ====================
        // These routes handle SIT/PROD environments with separate data isolation.
        // URL pattern: /api/{tenantId}/apps/{appId}/env/{env}/{entity}
        // SessionMiddleware excludes these paths; each mutation route enforces its own auth gate.

        // POST /api/{tenantId}/apps/{appId}/env/{env}/{entity} - Create entity in specific environment
        // B9 FIX — This is the primary deployed-app write path for SIT/PROD. Previously unauthenticated and ungated.
        router.post("/api/{tenantId}/apps/{appId}/env/{env}/{entity}", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("appId");
            String env = req.pathParam("env");
            String entity = req.pathParam("entity");

            // B9 FIX — Route-level session auth gate. Exclusion from SessionMiddleware ≠ public write access.
            String envInsertUserId = AuthService.extractUserId(req, cfg);
            if (envInsertUserId == null || envInsertUserId.isBlank()) {
                res.json(401, Map.of("error", "Authentication required"));
                return;
            }

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
                res.json(404, Map.of("error", "unknown entity: " + entity));
                return;
            }

            Map<String, Object> data = req.readJson(new TypeReference<>() {
            });

            // B9 FIX — Apply approval pre-insert guard before any DB write.
            enforceApprovalPreInsert(schema, data, envInsertUserId);

            try {
                TenantContext ctx = new TenantContext(tenantId, appId, env);
                TenantContext.set(ctx);

                try {
                    Object idObj = crud.insertRecord(schema, data);
                    Map<String, Object> after = crud.getById(schema, idObj);
                    String id = String.valueOf(idObj);

                    AuditLogService.log("INSERT", schema.getName(), id, envInsertUserId + "/env-" + env, null, after);

                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("id", idObj);
                    response.put("appId", appId);
                    response.put("env", env);
                    res.json(201, response);
                } finally {
                    TenantContext.clear();
                }
            } catch (Exception e) {
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

                    // C2.7 — approval-state filter, checker-only for PENDING.
                    String approvalStatusParam = req.query("_approvalStatus");
                    try {
                        applyApprovalStatusFilter(schema, tenantId, appId, approvalStatusParam, filters,
                                AuthService.extractUserId(req, ConfigManager.getConfig()),
                                AuthService.authEnabled(ConfigManager.getConfig()));
                    } catch (ApprovalFilterException afe) {
                        res.json(afe.status(), Map.of("error", afe.getMessage()));
                        return;
                    }

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
        // B7 FIX — Env-scoped PUT now enforces approval guard and session auth.
        router.put("/api/{tenantId}/apps/{appId}/env/{env}/{entity}/{id}", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("appId");
            String env = req.pathParam("env");
            String entity = req.pathParam("entity");
            String idStr = req.pathParam("id");

            // Session auth required for env-scoped mutations
            String envPutUserId = AuthService.extractUserId(req, cfg);
            if (envPutUserId == null || envPutUserId.isBlank()) {
                res.json(401, Map.of("error", "Authentication required for data mutations"));
                return;
            }

            EntitySchema schema = SchemaManager.loadSchema(appId, entity, tenantId);
            if (schema == null) {
                res.json(404, Map.of("error", "unknown entity: " + entity));
                return;
            }

            Map<String, Object> data = req.readJson(new TypeReference<>() {
            });

            // B7 FIX — Apply same approval guard as /api/{entity}/{id} PUT.
            // C2.3 — an edit to a live APPROVED row becomes a DRAFT revision instead of an overwrite.
            if (schema.isApprovalRequired()) {
                try {
                    TenantContext.set(new TenantContext(tenantId, appId, env));
                    try {
                        ApprovalPutResult guard = applyApprovalPutGuard(crud, schema, idStr, data, envPutUserId);
                        switch (guard.action()) {
                            case BLOCKED_PENDING -> {
                                res.json(400, guard.body());
                                return;
                            }
                            case CONFLICT -> {
                                res.json(409, guard.body());
                                return;
                            }
                            case REVISION -> {
                                res.json(200, guard.body());
                                return;
                            }
                            case PROCEED -> { /* fall through to the normal update below */ }
                        }
                    } finally {
                        TenantContext.clear();
                    }
                } catch (SQLException e) {
                    LOG.error("Failed to check approval status on env PUT for app={} env={} entity={} id={}", appId, env, entity, idStr, e);
                    res.json(500, ErrorHandler.errorDetails(e));
                    return;
                }
            }

            try {
                TenantContext ctx = new TenantContext(tenantId, appId, env);
                TenantContext.set(ctx);

                try {
                    Map<String, Object> before = crud.getById(schema, idStr);
                    int updated = crud.updateById(schema, idStr, data);
                    Map<String, Object> after = updated > 0 ? crud.getById(schema, idStr) : null;
                    
                    if (updated > 0) {
                        AuditLogService.log("UPDATE", schema.getName(), idStr, envPutUserId + "/env-" + env, before, after);
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
        // B7 FIX — Env-scoped DELETE now enforces PENDING gate and session auth.
        router.delete("/api/{tenantId}/apps/{appId}/env/{env}/{entity}/{id}", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("appId");
            String env = req.pathParam("env");
            String entity = req.pathParam("entity");
            String idStr = req.pathParam("id");

            // Session auth required for env-scoped mutations
            String envDeleteUserId = AuthService.extractUserId(req, cfg);
            if (envDeleteUserId == null || envDeleteUserId.isBlank()) {
                res.json(401, Map.of("error", "Authentication required for data mutations"));
                return;
            }

            EntitySchema schema = SchemaManager.loadSchema(appId, entity, tenantId);
            if (schema == null) {
                res.json(404, Map.of("error", "unknown entity: " + entity));
                return;
            }

            // B7 FIX — Block DELETE on PENDING records via env-scoped route.
            if (schema.isApprovalRequired()) {
                try {
                    TenantContext.set(new TenantContext(tenantId, appId, env));
                    try {
                        Map<String, Object> existing = crud.getById(schema, idStr);
                        if (existing != null) {
                            Object statusObj = existing.get("approval_status");
                            if (statusObj == null) statusObj = existing.get("APPROVAL_STATUS");
                            String currentStatus = statusObj != null ? String.valueOf(statusObj) : "DRAFT";
                            if ("PENDING".equalsIgnoreCase(currentStatus)) {
                                LOG.warn("[SECURITY] Env DELETE blocked: app={} env={} entity={} id={} is PENDING", appId, env, entity, idStr);
                                res.json(400, Map.of("error", "Cannot delete record while approval is PENDING"));
                                return;
                            }
                        }
                    } finally {
                        TenantContext.clear();
                    }
                } catch (SQLException e) {
                    LOG.error("Failed to check approval status on env DELETE for app={} env={} entity={} id={}", appId, env, entity, idStr, e);
                    res.json(500, ErrorHandler.errorDetails(e));
                    return;
                }
            }

            try {
                TenantContext ctx = new TenantContext(tenantId, appId, env);
                TenantContext.set(ctx);

                try {
                    Map<String, Object> before = crud.getById(schema, idStr);
                    int deleted = crud.deleteById(schema, idStr);
                    
                    if (deleted > 0) {
                        AuditLogService.log("DELETE", schema.getName(), idStr, envDeleteUserId + "/env-" + env, before, null);
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

    private static final List<String> APPROVAL_COLUMNS = List.of(
            "approval_status", "APPROVAL_STATUS",
            "approval_revision", "APPROVAL_REVISION",
            "approval_parent_id", "APPROVAL_PARENT_ID",
            "submitted_by", "SUBMITTED_BY",
            "submitted_at", "SUBMITTED_AT",
            "approved_by", "APPROVED_BY",
            "approved_at", "APPROVED_AT",
            "rejection_reason", "REJECTION_REASON"
    );

    /** Lower-cased approval column names, for schema-field comparisons. */
    private static final Set<String> APPROVAL_FIELD_NAMES = Set.of(
            "approval_status", "approval_revision", "approval_parent_id",
            "submitted_by", "submitted_at", "approved_by", "approved_at", "rejection_reason"
    );

    private static final Set<String> VALID_APPROVAL_STATUSES = Set.of("DRAFT", "PENDING", "APPROVED", "REJECTED");

    /** What the caller should do after {@link #applyApprovalPutGuard} has inspected a PUT. */
    enum ApprovalPutAction {
        /** Not an approval entity, or the row is safe to update in place. */
        PROCEED,
        /** Row is awaiting approval — reject the edit (400). */
        BLOCKED_PENDING,
        /** Row was live and APPROVED — a DRAFT revision was written instead (200). */
        REVISION,
        /** A revision of this row is already PENDING — reject the edit (409). */
        CONFLICT
    }

    record ApprovalPutResult(ApprovalPutAction action, Map<String, Object> body) {
        static final ApprovalPutResult PROCEED = new ApprovalPutResult(ApprovalPutAction.PROCEED, null);
    }

    /**
     * C2.3 — approval guard for every {@code PUT .../{entity}/{id}} handler.
     *
     * <p>Behaviour by current {@code approval_status} of the target row:
     * <ul>
     *   <li><b>PENDING</b> → {@link ApprovalPutAction#BLOCKED_PENDING}. Editing a row a checker
     *       is looking at would let a maker swap the payload after submission.</li>
     *   <li><b>DRAFT / REJECTED</b> → {@link ApprovalPutAction#PROCEED}. These rows are not live,
     *       so they are edited in place and reset to DRAFT. The revision counter is deliberately
     *       left alone; {@code ApprovalService.submitForApproval} owns bumping it on resubmit.</li>
     *   <li><b>APPROVED</b> → the row is live and must not be mutated. A separate DRAFT revision
     *       row is created (or the existing open one refreshed) with
     *       {@code approval_parent_id = <liveId>}, and {@link ApprovalPutAction#REVISION} is
     *       returned. If an open revision is already PENDING, {@link ApprovalPutAction#CONFLICT}.</li>
     * </ul>
     *
     * <p>Client-supplied approval columns are always stripped first, so a maker can never
     * hand-craft a payload that pre-approves itself or re-points {@code approval_parent_id}.
     *
     * <p>Callers must have set the appropriate {@link TenantContext} beforehand where their
     * route requires one.
     *
     * @return what the caller should do; {@code body} is the JSON payload for non-PROCEED outcomes
     */
    static ApprovalPutResult applyApprovalPutGuard(EntityCrudService crud, EntitySchema schema,
                                                          String idStr, Map<String, Object> data,
                                                          String callerUserId) throws SQLException {
        if (schema == null || !schema.isApprovalRequired() || data == null) {
            return ApprovalPutResult.PROCEED;
        }

        stripApprovalColumns(data);

        Map<String, Object> existing = crud.getById(schema, idStr);
        if (existing == null) {
            return ApprovalPutResult.PROCEED;
        }

        String currentStatus = approvalString(existing, "approval_status", "DRAFT");
        int rev = approvalInt(existing, "approval_revision", 1);

        if ("PENDING".equalsIgnoreCase(currentStatus)) {
            return new ApprovalPutResult(ApprovalPutAction.BLOCKED_PENDING,
                    Map.of("error", "Cannot update record while approval is PENDING"));
        }

        boolean hasParentColumn = schema.getFields().stream()
                .anyMatch(f -> "approval_parent_id".equalsIgnoreCase(f.getName()));

        if (!"APPROVED".equalsIgnoreCase(currentStatus) || !hasParentColumn) {
            if ("APPROVED".equalsIgnoreCase(currentStatus)) {
                LOG.warn("[APPROVAL] Entity {} has no approval_parent_id column — falling back to in-place edit "
                        + "of APPROVED row {}. Re-run the scaffolder to enable revisions.", schema.getName(), idStr);
            }
            // DRAFT / REJECTED rows are private to the maker: edit in place and return to DRAFT.
            data.put("approval_status", "DRAFT");
            data.put("rejection_reason", null);
            if (callerUserId != null && !callerUserId.isBlank()) {
                data.put("submitted_by", callerUserId);
            }
            return ApprovalPutResult.PROCEED;
        }

        Map<String, Object> openRevision = crud.findOpenRevision(schema, idStr);
        if (openRevision != null
                && "PENDING".equalsIgnoreCase(approvalString(openRevision, "approval_status", "DRAFT"))) {
            return new ApprovalPutResult(ApprovalPutAction.CONFLICT, Map.of(
                    "error", "A revision of this record is already awaiting approval",
                    "revisionId", String.valueOf(rowValue(openRevision, primaryKeyName(schema)))));
        }

        int nextRevision = rev + 1;
        Map<String, Object> revisionData = new LinkedHashMap<>();
        String pkName = primaryKeyName(schema);
        for (EntitySchema.Field f : schema.getFields()) {
            String name = f.getName();
            if (f.isPrimaryKey() || APPROVAL_FIELD_NAMES.contains(name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            revisionData.put(name, rowValue(existing, name));
        }
        // The caller's edits win over the carried-over live values.
        for (Map.Entry<String, Object> e : data.entrySet()) {
            if (pkName != null && pkName.equalsIgnoreCase(e.getKey())) {
                continue;
            }
            revisionData.put(e.getKey(), e.getValue());
        }

        revisionData.put("approval_status", "DRAFT");
        revisionData.put("approval_revision", nextRevision);
        revisionData.put("approval_parent_id", idStr);
        revisionData.put("rejection_reason", null);
        revisionData.put("submitted_at", null);
        revisionData.put("approved_by", null);
        revisionData.put("approved_at", null);
        if (callerUserId != null && !callerUserId.isBlank()) {
            revisionData.put("submitted_by", callerUserId);
        }

        String revisionId;
        if (openRevision != null) {
            revisionId = String.valueOf(rowValue(openRevision, pkName));
            crud.updateById(schema, revisionId, revisionData);
        } else {
            Object generated = crud.insertRecord(schema, revisionData);
            revisionId = generated != null ? String.valueOf(generated) : null;
        }

        LOG.info("[APPROVAL] PUT on APPROVED row {} of {} produced DRAFT revision {} (revision {})",
                idStr, schema.getName(), revisionId, nextRevision);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("updated", 0);
        body.put("revision", true);
        body.put("revisionId", revisionId);
        body.put("parentId", idStr);
        body.put("approvalStatus", "DRAFT");
        body.put("approvalRevision", nextRevision);
        return new ApprovalPutResult(ApprovalPutAction.REVISION, body);
    }

    private static String primaryKeyName(EntitySchema schema) {
        return schema.getFields().stream()
                .filter(EntitySchema.Field::isPrimaryKey)
                .map(EntitySchema.Field::getName)
                .findFirst()
                .orElse(null);
    }

    /** Row maps come back with either exact-case or UPPER_CASE keys depending on the driver path. */
    private static Object rowValue(Map<String, Object> row, String column) {
        if (row == null || column == null) {
            return null;
        }
        if (row.containsKey(column)) {
            return row.get(column);
        }
        String upper = column.toUpperCase(Locale.ROOT);
        if (row.containsKey(upper)) {
            return row.get(upper);
        }
        String lower = column.toLowerCase(Locale.ROOT);
        return row.get(lower);
    }

    private static String approvalString(Map<String, Object> row, String column, String fallback) {
        Object v = rowValue(row, column);
        return v != null ? String.valueOf(v) : fallback;
    }

    private static int approvalInt(Map<String, Object> row, String column, int fallback) {
        Object v = rowValue(row, column);
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v != null) {
            try {
                return Integer.parseInt(String.valueOf(v));
            } catch (NumberFormatException ignore) {
                return fallback;
            }
        }
        return fallback;
    }

    /** Signals that {@code ?_approvalStatus=} was invalid or not permitted for this caller. */
    static class ApprovalFilterException extends RuntimeException {
        private final int status;

        ApprovalFilterException(int status, String message) {
            super(message);
            this.status = status;
        }

        int status() {
            return status;
        }
    }

    /**
     * C2.7 — validates and authorizes the {@code ?_approvalStatus=} list filter.
     *
     * <p>Rules:
     * <ul>
     *   <li>Absent/blank → {@code null} (no filter applied).</li>
     *   <li>Entity has no approval workflow → 400. Silently ignoring it would return the
     *       full unfiltered table, which a caller asking for PENDING must never receive.</li>
     *   <li>Value outside DRAFT/PENDING/APPROVED/REJECTED → 400.</li>
     *   <li>{@code PENDING} → caller must hold the checker role (or own the app). The pending
     *       queue is the checker's view; makers must not be able to enumerate it.</li>
     * </ul>
     *
     * @return the canonical upper-case status to filter on, or {@code null}
     */
    static String resolveApprovalStatusFilter(EntitySchema schema, String tenantId, String appId,
                                                      String raw, String callerUserId, boolean authEnabled) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (schema == null || !schema.isApprovalRequired()) {
            throw new ApprovalFilterException(400,
                    "_approvalStatus is not supported: entity does not have an approval workflow");
        }
        String status = raw.trim().toUpperCase(Locale.ROOT);
        if (!VALID_APPROVAL_STATUSES.contains(status)) {
            throw new ApprovalFilterException(400,
                    "invalid _approvalStatus '" + raw + "' (expected DRAFT, PENDING, APPROVED or REJECTED)");
        }

        if ("PENDING".equals(status) && authEnabled) {
            String t = (tenantId != null && !tenantId.isBlank()) ? tenantId : schema.getTenantId();
            String a = (appId != null && !appId.isBlank()) ? appId : schema.getAppId();
            boolean allowed = callerUserId != null && !callerUserId.isBlank()
                    && ApprovalService.hasCheckerOrOwnerPermission(t, a, schema.getName(), callerUserId);
            if (!allowed) {
                LOG.warn("[SECURITY] _approvalStatus=PENDING denied for user={} on entity={}",
                        callerUserId, schema.getName());
                throw new ApprovalFilterException(403,
                        "forbidden: only checkers may list PENDING records for " + schema.getName());
            }
        }
        return status;
    }

    /**
     * C2.7 — applies and authorizes the approval-state list filter.
     *
     * <p>Two doors lead to the same predicate and both must be guarded:
     * <ol>
     *   <li>the dedicated {@code ?_approvalStatus=} parameter, and</li>
     *   <li>the generic {@code ?filter=approval_status:PENDING} parameter, which
     *       {@code parseFilters} happily accepts because {@code approval_status} is a real
     *       schema field.</li>
     * </ol>
     * Guarding only the first would leave the checker queue enumerable by any maker.
     *
     * @param filters mutated in place when an explicit {@code _approvalStatus} was supplied
     * @return true if {@code _approvalStatus} was supplied (callers use this to force the
     *         advanced query path, which is the only one that honours filters)
     * @throws ApprovalFilterException if the value is invalid or the caller may not see it
     */
    static boolean applyApprovalStatusFilter(EntitySchema schema, String tenantId, String appId,
                                             String raw, Map<String, Object> filters,
                                             String callerUserId, boolean authEnabled) {
        String explicit = resolveApprovalStatusFilter(schema, tenantId, appId, raw, callerUserId, authEnabled);
        if (explicit != null) {
            filters.put("approval_status", explicit);
            return true;
        }

        if (schema != null && schema.isApprovalRequired() && filters != null) {
            for (Map.Entry<String, Object> e : filters.entrySet()) {
                if ("approval_status".equalsIgnoreCase(e.getKey()) && e.getValue() != null) {
                    // Same validation + checker gate as the dedicated parameter.
                    resolveApprovalStatusFilter(schema, tenantId, appId, String.valueOf(e.getValue()),
                            callerUserId, authEnabled);
                }
            }
        }
        return false;
    }

    private static void stripApprovalColumns(Map<String, Object> data) {
        if (data == null) return;
        for (String col : APPROVAL_COLUMNS) {
            data.remove(col);
        }
    }

    /**
     * enforceApprovalPreInsert — C2.15 shared guard applied before every insertRecord/insertBatch call.
     *
     * Invariant: if schema.isApprovalRequired(), NO client-supplied approval metadata survives into the DB.
     * - Strips all 8 approval columns from the payload (prevents forged status/actor injection):
     *   approval_status, approval_revision, approval_parent_id, submitted_by, submitted_at,
     *   approved_by, approved_at, rejection_reason (+ UPPER_CASE variants).
     * - Forces approval_status = DRAFT (new records must begin in DRAFT, never APPROVED/PENDING).
     * - Forces approval_revision = 1.
     * - Binds submitted_by to the authenticated callerUserId (prevents forged submitter).
     *
     * Bypass-attempt detection: logs WARN if the client payload contained ANY of the 8 approval
     * columns (not just the first 3 — covers approved_at, submitted_at, rejection_reason too).     *
     * If schema does NOT require approval, this method is a no-op (strips nothing).
     *
     * Called from: single POST, batch POST (per element), studio POST, runtime POST, env-scoped POST.
     */
    private static void enforceApprovalPreInsert(EntitySchema schema, Map<String, Object> data, String callerUserId) {
        if (schema == null || !schema.isApprovalRequired() || data == null) return;

        // Check all 8 approval columns (both lower and UPPER variants) — not just the first 3.
        boolean hadApprovalCols = APPROVAL_COLUMNS.stream().anyMatch(data::containsKey);
        if (hadApprovalCols) {
            LOG.warn("[SECURITY] enforceApprovalPreInsert: client attempted to set approval columns on entity={} — stripping",
                    schema.getName());
        }

        stripApprovalColumns(data);
        data.put("approval_status", "DRAFT");
        data.put("approval_revision", 1);
        if (callerUserId != null && !callerUserId.isBlank()) {
            data.put("submitted_by", callerUserId);
        }
    }
}
