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
}
