package com.appbana.server.routes;

import com.appbana.AppManager;
import com.appbana.SchemaManager;
import com.appbana.api.Router;
import com.appbana.approval.UserRoleService;
import com.appbana.model.AppMetadata;
import com.appbana.model.EntitySchema;
import com.appbana.service.AuthService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

/**
 * RoleRoutes — Sub-phase C1.6 & C1.7 (Tech Lead Review updates)
 *
 * REST API for user role management with tenant scoping and authorization guards:
 * GET    /api/tenants/{tenantId}/apps/{appId}/roles?entityName=Y&userId=Z
 * POST   /api/tenants/{tenantId}/apps/{appId}/roles (body: { entityName, userId, role })
 * DELETE /api/tenants/{tenantId}/apps/{appId}/roles?entityName=Y&userId=Z
 *
 * Backwards-compatible legacy fallbacks:
 * GET/POST/DELETE /api/apps/{appId}/roles (defaults tenantId="default")
 */
public class RoleRoutes {
    private static final Logger LOG = LoggerFactory.getLogger(RoleRoutes.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    public static void register(Router router) {

        // --- Standard multi-tenant endpoints ---

        // GET /api/tenants/{tenantId}/apps/{appId}/roles
        router.get("/api/tenants/{tenantId}/apps/{appId}/roles", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("appId");
            handleGetRoles(req, res, tenantId, appId);
        });

        // POST /api/tenants/{tenantId}/apps/{appId}/roles
        router.post("/api/tenants/{tenantId}/apps/{appId}/roles", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("appId");
            handlePostRole(req, res, tenantId, appId);
        });

        // DELETE /api/tenants/{tenantId}/apps/{appId}/roles
        router.delete("/api/tenants/{tenantId}/apps/{appId}/roles", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("appId");
            handleDeleteRole(req, res, tenantId, appId);
        });

        // --- Backwards compatibility fallbacks ---
        router.get("/api/apps/{appId}/roles", (req, res) -> {
            String tenantId = req.query("tenantId");
            if (tenantId == null || tenantId.isBlank()) tenantId = "default";
            handleGetRoles(req, res, tenantId, req.pathParam("appId"));
        });

        router.post("/api/apps/{appId}/roles", (req, res) -> {
            String tenantId = "default";
            handlePostRole(req, res, tenantId, req.pathParam("appId"));
        });

        router.delete("/api/apps/{appId}/roles", (req, res) -> {
            String tenantId = req.query("tenantId");
            if (tenantId == null || tenantId.isBlank()) tenantId = "default";
            handleDeleteRole(req, res, tenantId, req.pathParam("appId"));
        });
    }

    private static void handleGetRoles(Router.HttpRequest req, Router.HttpResponse res, String tenantId, String appId) {
        String entityName = req.query("entityName");
        String userId = req.query("userId");

        if (entityName == null || entityName.isBlank() || userId == null || userId.isBlank()) {
            res.json(400, Map.of("error", "entityName and userId query parameters required"));
            return;
        }

        try {
            EntitySchema schema = SchemaManager.loadSchema(appId, entityName, tenantId);
            if (schema == null) {
                res.json(404, Map.of("error", "Entity not found: " + entityName));
                return;
            }

            Set<UserRoleService.Role> roles = UserRoleService.getUserRoles(tenantId, appId, entityName, userId);
            boolean isMaker = UserRoleService.isMaker(tenantId, appId, entityName, userId);
            boolean isChecker = UserRoleService.isChecker(tenantId, appId, entityName, userId);

            res.json(200, Map.of(
                    "tenantId", tenantId,
                    "appId", appId,
                    "entityName", entityName,
                    "userId", userId,
                    "roles", roles.stream().map(UserRoleService.Role::getValue).toList(),
                    "isMaker", isMaker,
                    "isChecker", isChecker
            ));
        } catch (Exception e) {
            LOG.error("[RoleRoutes] Failed to get roles", e);
            res.json(500, Map.of("error", e.getMessage()));
        }
    }

    private static void handlePostRole(Router.HttpRequest req, Router.HttpResponse res, String tenantId, String appId) {
        String callerUserId = AuthService.extractUserId(req, com.appbana.config.ConfigManager.getConfig());
        if (callerUserId == null || callerUserId.isBlank()) callerUserId = "system";

        try {
            Map<String, String> body = req.readJson(new TypeReference<>() {});
            String bodyTenantId = body.get("tenantId");
            String effectiveTenantId = (bodyTenantId != null && !bodyTenantId.isBlank()) ? bodyTenantId : tenantId;
            String entityName = body.get("entityName");
            String targetUserId = body.get("userId");
            String roleStr = body.get("role");

            if (entityName == null || entityName.isBlank() || targetUserId == null || targetUserId.isBlank() || roleStr == null || roleStr.isBlank()) {
                res.json(400, Map.of("error", "entityName, userId, and role required"));
                return;
            }

            EntitySchema schema = SchemaManager.loadSchema(appId, entityName, effectiveTenantId);
            if (schema == null) {
                res.json(404, Map.of("error", "Entity not found: " + entityName));
                return;
            }

            // Blocker 1: Authorization check — caller must be creator or existing checker/both on entity
            if (!isAuthorizedToManageRoles(effectiveTenantId, appId, entityName, callerUserId)) {
                res.json(403, Map.of("error", "Forbidden: caller cannot manage roles for entity " + entityName));
                return;
            }

            UserRoleService.Role role = UserRoleService.Role.fromValue(roleStr);
            UserRoleService.grantRole(effectiveTenantId, appId, entityName, targetUserId, role, callerUserId);

            res.json(200, Map.of(
                    "status", "granted",
                    "tenantId", effectiveTenantId,
                    "appId", appId,
                    "entityName", entityName,
                    "userId", targetUserId,
                    "role", role.getValue()
            ));
        } catch (IllegalArgumentException e) {
            res.json(400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            LOG.error("[RoleRoutes] Failed to grant role", e);
            res.json(500, Map.of("error", e.getMessage()));
        }
    }

    private static void handleDeleteRole(Router.HttpRequest req, Router.HttpResponse res, String tenantId, String appId) {
        String callerUserId = AuthService.extractUserId(req, com.appbana.config.ConfigManager.getConfig());
        if (callerUserId == null || callerUserId.isBlank()) callerUserId = "system";

        String entityName = req.query("entityName");
        String targetUserId = req.query("userId");

        if (entityName == null || entityName.isBlank() || targetUserId == null || targetUserId.isBlank()) {
            res.json(400, Map.of("error", "entityName and userId query parameters required"));
            return;
        }

        try {
            EntitySchema schema = SchemaManager.loadSchema(appId, entityName, tenantId);
            if (schema == null) {
                res.json(404, Map.of("error", "Entity not found: " + entityName));
                return;
            }

            // Blocker 1: Authorization check
            if (!isAuthorizedToManageRoles(tenantId, appId, entityName, callerUserId)) {
                res.json(403, Map.of("error", "Forbidden: caller cannot manage roles for entity " + entityName));
                return;
            }

            UserRoleService.revokeRole(tenantId, appId, entityName, targetUserId);

            res.json(200, Map.of(
                    "status", "revoked",
                    "tenantId", tenantId,
                    "appId", appId,
                    "entityName", entityName,
                    "userId", targetUserId
            ));
        } catch (Exception e) {
            LOG.error("[RoleRoutes] Failed to revoke role", e);
            res.json(500, Map.of("error", e.getMessage()));
        }
    }

    /**
     * Checks if callerUserId is authorized to grant or revoke roles on (tenantId, appId, entityName).
     * Authorized if:
     * 1. caller is "system"
     * 2. caller is the author/creator of the app
     * 3. caller already has CHECKER or BOTH role on the entity
     */
    public static boolean isAuthorizedToManageRoles(String tenantId, String appId, String entityName, String callerUserId) {
        if ("system".equalsIgnoreCase(callerUserId)) {
            return true;
        }
        try {
            AppMetadata app = AppManager.getApp(tenantId, appId);
            if (app != null && app.getAuthor() != null) {
                if (callerUserId.equalsIgnoreCase(app.getAuthor())) {
                    return true;
                }
            }
        } catch (Exception e) {
            LOG.warn("[RoleRoutes] Authorization check failed to retrieve app metadata: {}", e.getMessage());
        }
        return UserRoleService.isChecker(tenantId, appId, entityName, callerUserId);
    }
}
