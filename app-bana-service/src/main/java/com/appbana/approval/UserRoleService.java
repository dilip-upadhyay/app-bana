package com.appbana.approval;

import com.appbana.JdbcManager;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * UserRoleService — Sub-phase C1.4
 *
 * Manages per-entity user roles for maker-checker approval workflows.
 * Roles stored in appbana_user_roles platform table.
 *
 * Valid roles: 'maker', 'checker', 'both'.
 */
@Slf4j
public class UserRoleService {

    public enum Role {
        MAKER("maker"),
        CHECKER("checker"),
        BOTH("both");

        private final String value;

        Role(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static Role fromValue(String text) {
            if (text == null) return null;
            for (Role r : Role.values()) {
                if (r.value.equalsIgnoreCase(text.trim())) {
                    return r;
                }
            }
            throw new IllegalArgumentException("Unknown role value: " + text);
        }
    }

    public static void grantRole(String tenantId, String appId, String entityName, String userId, Role role, String grantedBy) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(appId, "appId required");
        Objects.requireNonNull(entityName, "entityName required");
        Objects.requireNonNull(userId, "userId required");
        Objects.requireNonNull(role, "role required");
        String grantedByUserId = (grantedBy != null && !grantedBy.isBlank()) ? grantedBy : "system";

        String sql = "INSERT INTO appbana_user_roles (tenant_id, app_id, entity_name, user_id, role, granted_by, granted_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, NOW()) " +
                "ON CONFLICT (tenant_id, app_id, entity_name, user_id) " +
                "DO UPDATE SET role = EXCLUDED.role, granted_by = EXCLUDED.granted_by, granted_at = NOW()";

        try (Connection c = JdbcManager.getConnection("default");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            ps.setString(2, appId);
            ps.setString(3, entityName);
            ps.setString(4, userId);
            ps.setString(5, role.getValue());
            ps.setString(6, grantedByUserId);
            ps.executeUpdate();
            log.info("[UserRoleService] Granted role '{}' to user '{}' on entity '{}_{}_{}'",
                    role.getValue(), userId, tenantId, appId, entityName);
        } catch (SQLException e) {
            log.error("[UserRoleService] Failed to grant role to user '{}': {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to grant role", e);
        }
    }

    public static void revokeRole(String tenantId, String appId, String entityName, String userId) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(appId, "appId required");
        Objects.requireNonNull(entityName, "entityName required");
        Objects.requireNonNull(userId, "userId required");

        String sql = "DELETE FROM appbana_user_roles WHERE tenant_id = ? AND app_id = ? AND entity_name = ? AND user_id = ?";

        try (Connection c = JdbcManager.getConnection("default");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            ps.setString(2, appId);
            ps.setString(3, entityName);
            ps.setString(4, userId);
            int rows = ps.executeUpdate();
            log.info("[UserRoleService] Revoked role for user '{}' on entity '{}_{}_{}' (rows affected: {})",
                    userId, tenantId, appId, entityName, rows);
        } catch (SQLException e) {
            log.error("[UserRoleService] Failed to revoke role for user '{}': {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to revoke role", e);
        }
    }

    public static Set<Role> getUserRoles(String tenantId, String appId, String entityName, String userId) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(appId, "appId required");
        Objects.requireNonNull(entityName, "entityName required");
        Objects.requireNonNull(userId, "userId required");

        Set<Role> roles = new HashSet<>();
        String sql = "SELECT role FROM appbana_user_roles WHERE tenant_id = ? AND app_id = ? AND entity_name = ? AND user_id = ?";

        try (Connection c = JdbcManager.getConnection("default");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            ps.setString(2, appId);
            ps.setString(3, entityName);
            ps.setString(4, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String val = rs.getString(1);
                    Role r = Role.fromValue(val);
                    if (r == Role.BOTH) {
                        roles.add(Role.MAKER);
                        roles.add(Role.CHECKER);
                        roles.add(Role.BOTH);
                    } else if (r != null) {
                        roles.add(r);
                    }
                }
            }
        } catch (SQLException e) {
            log.error("[UserRoleService] Failed to query roles for user '{}': {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to query user roles", e);
        }

        return roles;
    }

    public static boolean isMaker(String tenantId, String appId, String entityName, String userId) {
        Set<Role> roles = getUserRoles(tenantId, appId, entityName, userId);
        return roles.contains(Role.MAKER) || roles.contains(Role.BOTH);
    }

    public static boolean isChecker(String tenantId, String appId, String entityName, String userId) {
        Set<Role> roles = getUserRoles(tenantId, appId, entityName, userId);
        return roles.contains(Role.CHECKER) || roles.contains(Role.BOTH);
    }

    public static void grantCreatorRoles(String tenantId, String appId, String creatorUserId, Set<String> entityNames) {
        if (creatorUserId == null || creatorUserId.isBlank() || entityNames == null || entityNames.isEmpty()) {
            return;
        }
        for (String entityName : entityNames) {
            grantRole(tenantId, appId, entityName, creatorUserId, Role.BOTH, "system");
        }
    }

    /**
     * Task C3.3 — every entity role a user holds within one app, keyed by entity name.
     *
     * The runtime needs this to decide which entities get a checker queue. Doing
     * it per-entity would mean one round trip per entity on every page load, so
     * this is a single query and BOTH is expanded the same way
     * {@link #getUserRoles} expands it, keeping the two consistent.
     *
     * @return entityName -&gt; roles held. Entities with no grant are absent.
     */
    public static Map<String, Set<Role>> getUserRolesForApp(String tenantId, String appId, String userId) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(appId, "appId required");
        Objects.requireNonNull(userId, "userId required");

        Map<String, Set<Role>> byEntity = new LinkedHashMap<>();
        String sql = "SELECT entity_name, role FROM appbana_user_roles "
                + "WHERE tenant_id = ? AND app_id = ? AND user_id = ? ORDER BY entity_name";

        try (Connection c = JdbcManager.getConnection("default");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            ps.setString(2, appId);
            ps.setString(3, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String entityName = rs.getString(1);
                    Role r;
                    try {
                        r = Role.fromValue(rs.getString(2));
                    } catch (IllegalArgumentException e) {
                        // A role value we don't understand must not sink the whole
                        // lookup — skip it and keep the rest of the user's roles.
                        log.warn("[UserRoleService] Ignoring unknown role '{}' on entity '{}'", rs.getString(2), entityName);
                        continue;
                    }
                    Set<Role> roles = byEntity.computeIfAbsent(entityName, k -> new HashSet<>());
                    if (r == Role.BOTH) {
                        roles.add(Role.MAKER);
                        roles.add(Role.CHECKER);
                        roles.add(Role.BOTH);
                    } else if (r != null) {
                        roles.add(r);
                    }
                }
            }
        } catch (SQLException e) {
            log.error("[UserRoleService] Failed to query app roles for user '{}': {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to query user roles for app", e);
        }

        return byEntity;
    }
}
