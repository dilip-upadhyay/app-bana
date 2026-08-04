package com.appbana.security;

import com.appbana.JdbcManager;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * AppMembershipService — Task S2.2 (Tenant Isolation Security Plan).
 *
 * Manages per-app membership grants in {@code appbana_app_members} (schema: S2.1/{@code V19}).
 * A grant is independent of {@code session.tenantId} — the whole point of this table (per the
 * plan's review round 4, R4-2 restatement) is letting a user from one tenant manage a specific
 * app that belongs to a different tenant.
 *
 * <p><b>{@code appTenantId} is always the app's own tenant</b> — from {@code AppMetadata}/the
 * path, never {@code session.tenantId} — since the table's PK is {@code (tenant_id, app_id,
 * user_id)} and a session-tenant lookup on a cross-tenant grant is a guaranteed, silent miss.
 *
 * <p><b>{@link #isMember} vs {@link #isOwner}</b> — deliberately opposite in how they treat
 * {@code end-user}: {@code isMember} is permissive (true for ANY valid role, including
 * {@code end-user} — S2.6 wires this into {@code TenantAccessGuard} for list/get access, and
 * S3.7's deployed-app end-user relies on exactly this permissiveness; a corrupt-role row is
 * tolerated and yields false, consistent with the other three readers). {@code isOwner} is
 * strict (true only for {@code owner} — S2.5 wires this into {@code AppAuthorization
 * .isAppOwnerOrSystem}, which must never be satisfiable by a data-access-only grant). Getting
 * either backwards either silently blocks a legitimate end-user or silently grants management
 * rights to one — S2.6/S2.5 must never call the wrong one of these two.
 */
@Slf4j
public class AppMembershipService {

    public enum Role {
        OWNER("owner"),
        MEMBER("member"),
        END_USER("end-user");

        private final String value;

        Role(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static Role fromValue(String text) {
            if (text == null) {
                throw new IllegalArgumentException("Role value must not be null");
            }
            for (Role r : Role.values()) {
                if (r.value.equalsIgnoreCase(text.trim())) {
                    return r;
                }
            }
            throw new IllegalArgumentException("Unknown role value: " + text);
        }
    }

    /** One row of {@link #listAppsForUser}: a user's grant on an app, possibly cross-tenant. */
    public record MembershipGrant(String tenantId, String appId, Role role) {
    }

    /** One row of {@link #listMembers}: a member of a specific app. */
    public record Member(String userId, Role role, String grantedBy) {
    }

    /**
     * Tolerate-and-log parse (round 37 review, MEDIUM): {@code null} on an unrecognized or {@code
     * NULL} role instead of throwing, structurally shared by every reader so the policy can't drift
     * or silently revert to strict at one call site. {@link Role#fromValue} keeps throwing -- that
     * strict contract is still correct for callers that want it.
     */
    static Role parseRoleOrTolerate(String rawRole, String userId) {
        try {
            return Role.fromValue(rawRole);
        } catch (IllegalArgumentException e) {
            log.warn("[AppMembershipService] Ignoring unknown role '{}' for user '{}'", rawRole, userId);
            return null;
        }
    }

    /**
     * S2.7 review round 43 (LOW): true when {@code userId} is the app's ONLY {@code owner} row —
     * used by {@code AppMembershipRoutes} to refuse a revoke or a demoting grant that would leave
     * the app with zero owners (an operator-only-recoverable self-lockout, since every management
     * route including this one requires an existing owner). Deliberately membership-count-based,
     * not identity-based, so a second real owner can always revoke/demote the first freely.
     */
    public static boolean isSoleOwner(String appTenantId, String appId, String userId) {
        List<Member> members = listMembers(appTenantId, appId);
        long ownerCount = members.stream().filter(m -> m.role() == Role.OWNER).count();
        return ownerCount == 1 && members.stream()
                .anyMatch(m -> m.role() == Role.OWNER && m.userId().equals(userId));
    }

    public static void grant(String appTenantId, String appId, String userId, Role role, String grantedBy) {
        Objects.requireNonNull(appTenantId, "appTenantId required");
        Objects.requireNonNull(appId, "appId required");
        Objects.requireNonNull(userId, "userId required");
        Objects.requireNonNull(role, "role required");
        String grantedByUserId = (grantedBy != null && !grantedBy.isBlank()) ? grantedBy : "system";

        String sql = "INSERT INTO appbana_app_members (tenant_id, app_id, user_id, role, granted_by, granted_at) "
                + "VALUES (?, ?, ?, ?, ?, NOW()) "
                + "ON CONFLICT (tenant_id, app_id, user_id) "
                + "DO UPDATE SET role = EXCLUDED.role, granted_by = EXCLUDED.granted_by, granted_at = NOW()";

        try (Connection c = JdbcManager.getConnection("default");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, appTenantId);
            ps.setString(2, appId);
            ps.setString(3, userId);
            ps.setString(4, role.getValue());
            ps.setString(5, grantedByUserId);
            ps.executeUpdate();
            log.info("[AppMembershipService] Granted role '{}' to user '{}' on app '{}_{}'",
                    role.getValue(), userId, appTenantId, appId);
        } catch (SQLException e) {
            log.error("[AppMembershipService] Failed to grant membership to user '{}': {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to grant membership", e);
        }
    }

    public static void revoke(String appTenantId, String appId, String userId) {
        Objects.requireNonNull(appTenantId, "appTenantId required");
        Objects.requireNonNull(appId, "appId required");
        Objects.requireNonNull(userId, "userId required");

        String sql = "DELETE FROM appbana_app_members WHERE tenant_id = ? AND app_id = ? AND user_id = ?";

        try (Connection c = JdbcManager.getConnection("default");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, appTenantId);
            ps.setString(2, appId);
            ps.setString(3, userId);
            int rows = ps.executeUpdate();
            log.info("[AppMembershipService] Revoked membership for user '{}' on app '{}_{}' (rows affected: {})",
                    userId, appTenantId, appId, rows);
        } catch (SQLException e) {
            log.error("[AppMembershipService] Failed to revoke membership for user '{}': {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to revoke membership", e);
        }
    }

    /**
     * True for ANY valid role, including {@code end-user}. A corrupt-role row (tolerated per
     * {@link #parseRoleOrTolerate}) yields false — consistent with the other three readers.
     * See the class Javadoc's isMember-vs-isOwner note.
     */
    public static boolean isMember(String appTenantId, String appId, String userId) {
        Objects.requireNonNull(appTenantId, "appTenantId required");
        Objects.requireNonNull(appId, "appId required");
        if (userId == null || userId.isBlank()) {
            return false;
        }

        String sql = "SELECT role FROM appbana_app_members WHERE tenant_id = ? AND app_id = ? AND user_id = ?";
        try (Connection c = JdbcManager.getConnection("default");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, appTenantId);
            ps.setString(2, appId);
            ps.setString(3, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                return parseRoleOrTolerate(rs.getString(1), userId) != null;
            }
        } catch (SQLException e) {
            log.error("[AppMembershipService] Failed to check membership for user '{}': {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to check membership", e);
        }
    }

    /** True ONLY for {@code owner}. See the class Javadoc's isMember-vs-isOwner note. */
    public static boolean isOwner(String appTenantId, String appId, String userId) {
        Objects.requireNonNull(appTenantId, "appTenantId required");
        Objects.requireNonNull(appId, "appId required");
        if (userId == null || userId.isBlank()) {
            return false;
        }

        String sql = "SELECT role FROM appbana_app_members WHERE tenant_id = ? AND app_id = ? AND user_id = ?";
        try (Connection c = JdbcManager.getConnection("default");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, appTenantId);
            ps.setString(2, appId);
            ps.setString(3, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                return parseRoleOrTolerate(rs.getString(1), userId) == Role.OWNER;
            }
        } catch (SQLException e) {
            log.error("[AppMembershipService] Failed to check ownership for user '{}': {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to check ownership", e);
        }
    }

    public static List<Member> listMembers(String appTenantId, String appId) {
        Objects.requireNonNull(appTenantId, "appTenantId required");
        Objects.requireNonNull(appId, "appId required");

        List<Member> members = new ArrayList<>();
        String sql = "SELECT user_id, role, granted_by FROM appbana_app_members "
                + "WHERE tenant_id = ? AND app_id = ? ORDER BY user_id";

        try (Connection c = JdbcManager.getConnection("default");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, appTenantId);
            ps.setString(2, appId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Role role = parseRoleOrTolerate(rs.getString(2), rs.getString(1));
                    if (role == null) {
                        continue;
                    }
                    members.add(new Member(rs.getString(1), role, rs.getString(3)));
                }
            }
        } catch (SQLException e) {
            log.error("[AppMembershipService] Failed to list members for app '{}_{}': {}", appTenantId, appId, e.getMessage(), e);
            throw new RuntimeException("Failed to list members", e);
        }

        return members;
    }

    /**
     * The one deliberately cross-tenant lookup in this service (review round 5, R5-3): every
     * app a user holds ANY membership on, regardless of that app's own tenant. Backed by
     * {@code idx_app_members_user}, which leads with {@code user_id} for exactly this query.
     */
    public static List<MembershipGrant> listAppsForUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }

        List<MembershipGrant> grants = new ArrayList<>();
        String sql = "SELECT tenant_id, app_id, role FROM appbana_app_members WHERE user_id = ? ORDER BY tenant_id, app_id";

        try (Connection c = JdbcManager.getConnection("default");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Role role = parseRoleOrTolerate(rs.getString(3), userId);
                    if (role == null) {
                        continue;
                    }
                    grants.add(new MembershipGrant(rs.getString(1), rs.getString(2), role));
                }
            }
        } catch (SQLException e) {
            log.error("[AppMembershipService] Failed to list apps for user '{}': {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to list apps for user", e);
        }

        return grants;
    }
}
