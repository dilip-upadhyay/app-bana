package com.appbana.server.routes;

import com.appbana.UserManager;
import com.appbana.api.Router;
import com.appbana.approval.UserRoleService;
import com.appbana.config.ConfigManager;
import com.appbana.model.User;
import com.appbana.security.AppAuthorization;
import com.appbana.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * UserRoutes — Task C3.3.
 *
 * <pre>
 * GET /api/users/me[?tenantId=&amp;appId=]
 * </pre>
 *
 * Answers "who am I, and what may I do here?" in a single call.
 *
 * <p>The runtime needs the caller's per-entity maker/checker roles to decide
 * which entities get a checker queue and which rows offer approve/reject. Until
 * now no role information reached the frontend at all: the login response
 * carries identity only, and {@code /api/auth/profile} likewise. The
 * alternative — calling {@code /api/tenants/../roles} once per entity on every
 * page load — costs a round trip per entity and leaks the decision logic into
 * the client.</p>
 *
 * <p>Roles are scoped to a single app, so {@code appId} is what makes the
 * {@code entityRoles} block meaningful. Without it the endpoint still answers
 * the identity half, which keeps it usable as a generic session probe.</p>
 *
 * <p>This deliberately reports only the <em>caller's own</em> roles. It is not
 * a role-administration API — that remains {@link RoleRoutes}, which is guarded
 * by app ownership.</p>
 */
public class UserRoutes {
    private static final Logger LOG = LoggerFactory.getLogger(UserRoutes.class);

    public static void register(Router router) {
        router.get("/api/users/me", (req, res) -> {
            String callerUserId = AuthService.extractUserId(req, ConfigManager.getConfig());
            if (callerUserId == null || callerUserId.isBlank()) {
                res.json(401, Map.of("error", "Unauthorized: valid session required"));
                return;
            }

            try {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("userId", callerUserId);

                // Identity enrichment is best-effort. `callerUserId` can be a
                // service principal ("admin"/"system") with no row in the users
                // table, and that must still get a 200 with its roles — those
                // callers are exactly the ones that own apps.
                User user = lookupUser(callerUserId);
                if (user != null) {
                    out.put("email", user.getEmail());
                    out.put("name", user.getName());
                    out.put("tenantId", user.getTenantId());
                }

                String tenantId = req.query("tenantId");
                String appId = req.query("appId");
                if (tenantId == null || tenantId.isBlank()) {
                    tenantId = user != null ? user.getTenantId() : "default";
                }
                out.put("tenantId", tenantId);

                if (appId != null && !appId.isBlank()) {
                    out.put("appId", appId);
                    out.put("isAppOwner", AppAuthorization.isAppOwnerOrSystem(tenantId, appId, callerUserId));
                    out.put("entityRoles", buildEntityRoles(tenantId, appId, callerUserId));
                } else {
                    out.put("entityRoles", Map.of());
                }

                res.json(200, out);
            } catch (Exception e) {
                LOG.error("[UserRoutes] Failed to resolve current user", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });
    }

    /**
     * entityName -&gt; {roles, isMaker, isChecker}. The booleans are derived here
     * rather than in the client so the BOTH-expands-to-maker+checker rule stays
     * on the server, next to the rest of the role semantics.
     */
    private static Map<String, Object> buildEntityRoles(String tenantId, String appId, String userId) {
        Map<String, Set<UserRoleService.Role>> raw =
                UserRoleService.getUserRolesForApp(tenantId, appId, userId);

        Map<String, Object> byEntity = new LinkedHashMap<>();
        for (Map.Entry<String, Set<UserRoleService.Role>> e : raw.entrySet()) {
            Set<UserRoleService.Role> roles = e.getValue();
            List<String> names = new ArrayList<>();
            for (UserRoleService.Role r : roles) {
                names.add(r.getValue());
            }
            names.sort(String::compareTo);
            byEntity.put(e.getKey(), Map.of(
                    "roles", names,
                    "isMaker", roles.contains(UserRoleService.Role.MAKER),
                    "isChecker", roles.contains(UserRoleService.Role.CHECKER)
            ));
        }
        return byEntity;
    }

    /** Null for service principals and any id that isn't a real user row. */
    private static User lookupUser(String callerUserId) {
        try {
            return UserManager.getUser(Long.parseLong(callerUserId));
        } catch (NumberFormatException e) {
            return null;
        } catch (Exception e) {
            LOG.warn("[UserRoutes] User lookup failed for '{}': {}", callerUserId, e.getMessage());
            return null;
        }
    }
}
