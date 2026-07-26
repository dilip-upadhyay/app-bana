package com.appbana.server.routes;

import com.appbana.SchemaManager;
import com.appbana.api.Router;
import com.appbana.approval.UserRoleService;
import com.appbana.model.EntitySchema;
import com.appbana.service.AuthService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

/**
 * RoleRoutes — Sub-phase C1.6
 *
 * REST API for user role management:
 * GET    /api/apps/{appId}/roles?tenantId=X&entityName=Y&userId=Z
 * POST   /api/apps/{appId}/roles (body: { tenantId, entityName, userId, role })
 * DELETE /api/apps/{appId}/roles?tenantId=X&entityName=Y&userId=Z
 */
public class RoleRoutes {
    private static final Logger LOG = LoggerFactory.getLogger(RoleRoutes.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    public static void register(Router router) {

        // GET /api/apps/{appId}/roles
        router.get("/api/apps/{appId}/roles", (req, res) -> {
            String appId = req.pathParam("appId");
            String tenantId = req.query("tenantId");
            String entityName = req.query("entityName");
            String userId = req.query("userId");

            if (tenantId == null || tenantId.isBlank()) tenantId = "default";
            if (entityName == null || entityName.isBlank() || userId == null || userId.isBlank()) {
                res.json(400, Map.of("error", "entityName and userId query parameters required"));
                return;
            }

            try {
                // Task C1 exit criteria check: entity must exist
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
        });

        // POST /api/apps/{appId}/roles
        router.post("/api/apps/{appId}/roles", (req, res) -> {
            String appId = req.pathParam("appId");
            String grantedBy = AuthService.extractUserId(req, com.appbana.config.ConfigManager.getConfig());

            try {
                Map<String, String> body = req.readJson(new TypeReference<>() {});
                String tenantId = body.getOrDefault("tenantId", "default");
                String entityName = body.get("entityName");
                String userId = body.get("userId");
                String roleStr = body.get("role");

                if (entityName == null || entityName.isBlank() || userId == null || userId.isBlank() || roleStr == null || roleStr.isBlank()) {
                    res.json(400, Map.of("error", "entityName, userId, and role required"));
                    return;
                }

                // Verify entity exists
                EntitySchema schema = SchemaManager.loadSchema(appId, entityName, tenantId);
                if (schema == null) {
                    res.json(404, Map.of("error", "Entity not found: " + entityName));
                    return;
                }

                UserRoleService.Role role = UserRoleService.Role.fromValue(roleStr);
                UserRoleService.grantRole(tenantId, appId, entityName, userId, role, grantedBy);

                res.json(200, Map.of(
                        "status", "granted",
                        "tenantId", tenantId,
                        "appId", appId,
                        "entityName", entityName,
                        "userId", userId,
                        "role", role.getValue()
                ));
            } catch (IllegalArgumentException e) {
                res.json(400, Map.of("error", e.getMessage()));
            } catch (Exception e) {
                LOG.error("[RoleRoutes] Failed to grant role", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // DELETE /api/apps/{appId}/roles
        router.delete("/api/apps/{appId}/roles", (req, res) -> {
            String appId = req.pathParam("appId");
            String tenantId = req.query("tenantId");
            String entityName = req.query("entityName");
            String userId = req.query("userId");

            if (tenantId == null || tenantId.isBlank()) tenantId = "default";
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

                UserRoleService.revokeRole(tenantId, appId, entityName, userId);

                res.json(200, Map.of(
                        "status", "revoked",
                        "tenantId", tenantId,
                        "appId", appId,
                        "entityName", entityName,
                        "userId", userId
                ));
            } catch (Exception e) {
                LOG.error("[RoleRoutes] Failed to revoke role", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });
    }
}
