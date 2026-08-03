package com.appbana.security;

import com.appbana.JdbcManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

/**
 * AppAuthorization — Task C1.10 & C1.12 / S2.5 (membership-aware ownership check).
 *
 * Centralized authorization helper for app-level ownership checks.
 */
public final class AppAuthorization {
    private static final Logger LOG = LoggerFactory.getLogger(AppAuthorization.class);

    private AppAuthorization() {
    }

    /**
     * S2.5: True if callerUserId is "system", holds an {@code owner} membership row on the app,
     * or (pre-S2.4 safety net only) matches the app's recorded {@code author} field when the
     * membership table has no rows for this app yet.
     *
     * <p>{@code end-user} membership never satisfies this check — it stays owner-or-system only,
     * so a data-access-only grant cannot escalate to management rights.
     *
     * <p>Once the membership table has any row for this app it is treated as authoritative;
     * the {@code AppMetadata.getAuthor()} fallback is not consulted, preventing a demoted-owner
     * situation where the old author string still grants management access.
     */
    public static boolean isAppOwnerOrSystem(String tenantId, String appId, String callerUserId) {
        if ("system".equalsIgnoreCase(callerUserId)) {
            return true;
        }
        if (callerUserId == null || callerUserId.isBlank()) {
            return false;
        }
        try {
            List<AppMembershipService.Member> members = AppMembershipService.listMembers(tenantId, appId);
            if (!members.isEmpty()) {
                // Membership table has data — authoritative for this app.
                return members.stream().anyMatch(
                        m -> m.userId().equals(callerUserId) && m.role() == AppMembershipService.Role.OWNER);
            }
            // Pre-S2.4 safety net: no membership rows yet, fall back to recorded author column.
            try (Connection conn = JdbcManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT author FROM appbana_apps WHERE tenant_id = ? AND id = ?")) {
                ps.setString(1, tenantId);
                ps.setString(2, appId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String author = rs.getString("author");
                        return author != null && callerUserId.equals(author);
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("[AppAuthorization] Failed to check ownership for ({}, {}): {}", tenantId, appId, e.getMessage());
        }
        return false;
    }

    /**
     * S2.6: gate for {@code AppRoutes} management/destructive routes (update/delete and the
     * release-management family) once {@link TenantAccessGuard#requireOwnTenant}'s membership
     * exception admits a cross-tenant caller past the tenant gate. Deliberately NOT the same check
     * as {@link #isAppOwnerOrSystem} — that stays owner-or-system-only (S2.5) so a data-access-only
     * grant can never satisfy the maker-checker/role-management call sites it guards. This method
     * instead preserves the pre-S2 same-tenant trust model (a same-tenant caller with no membership
     * row at all is still admitted, exactly as before S2 existed) while adding exactly one new
     * restriction: a caller who holds an explicit {@code end-user} row for this specific app is
     * denied management rights regardless of tenant, because that role is a data-access-only grant
     * by design (S2.6 spec, {@code TENANT_ISOLATION_IMPLEMENTATION_TASKS.md}).
     *
     * <p>Returns true when: caller is {@code "system"}; OR caller has no membership row for this
     * app (no row => nothing to restrict, same as before S2); OR caller's row is {@code owner} or
     * {@code member}. Returns false only when caller's row is explicitly {@code end-user} (or the
     * check itself fails open-to-false on error, matching {@link #isAppOwnerOrSystem}'s posture).
     */
    public static boolean isManagerOrSystem(String tenantId, String appId, String callerUserId) {
        if ("system".equalsIgnoreCase(callerUserId)) {
            return true;
        }
        if (callerUserId == null || callerUserId.isBlank()) {
            return false;
        }
        try {
            List<AppMembershipService.Member> members = AppMembershipService.listMembers(tenantId, appId);
            return members.stream()
                    .filter(m -> m.userId().equals(callerUserId))
                    .noneMatch(m -> m.role() == AppMembershipService.Role.END_USER);
        } catch (Exception e) {
            LOG.warn("[AppAuthorization] Failed to check manager rights for ({}, {}): {}", tenantId, appId, e.getMessage());
        }
        return false;
    }
}
