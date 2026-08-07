package com.appbana;

import com.appbana.model.AppMetadata;
import com.appbana.security.AppMembershipService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S3.10 — {@link AppManager#deleteApp} must cascade to {@code appbana_app_members}.
 *
 * Before this fix, {@code deleteApp} removed a row from {@code appbana_pages} and
 * {@code appbana_apps} but never touched {@code appbana_app_members}. There is no FK
 * on {@code appbana_app_members.app_id} at all ({@code V19__appbana_app_members.sql}),
 * so every membership grant for a deleted app was orphaned permanently — not a security
 * hole (the {@code (tenantId, appId)} pair no longer resolves to a live app), but a real,
 * unbounded data-hygiene leak on every app delete.
 *
 * <p>Deliberately does not declare its own {@code appbana_app_members}/{@code appbana_apps}
 * DDL — relying on the real Liquibase changesets is the point (same rationale as
 * {@code AppMembershipServiceTest}).
 */
public class AppManagerDeleteAppCascadeTest {

    private static final String TENANT = "s310-tenant";

    @BeforeEach
    public void cleanFixtureRows() throws Exception {
        deleteFixtureRows();
    }

    @AfterAll
    public static void sweepFixtureRows() throws Exception {
        deleteFixtureRows();
    }

    private static void deleteFixtureRows() throws Exception {
        try (Connection c = JdbcManager.getConnection("default");
             Statement s = c.createStatement()) {
            s.execute("DELETE FROM appbana_app_members WHERE tenant_id = '" + TENANT + "'");
            s.execute("DELETE FROM appbana_pages WHERE tenant_id = '" + TENANT + "'");
            s.execute("DELETE FROM appbana_apps WHERE tenant_id = '" + TENANT + "'");
        }
    }

    @Test
    public void deleteAppRemovesItsMembershipGrants() throws Exception {
        String appId = "s310-app-" + UUID.randomUUID().toString().substring(0, 8);
        AppMetadata app = new AppMetadata(appId, "Cascade Test App", "1.0.0");
        AppManager.createApp(TENANT, app);

        AppMembershipService.grant(TENANT, appId, "owner-user", AppMembershipService.Role.OWNER, "system");
        AppMembershipService.grant(TENANT, appId, "cross-tenant-user", AppMembershipService.Role.END_USER, "owner-user");
        assertTrue(AppMembershipService.isMember(TENANT, appId, "owner-user"), "grant should be visible before delete");
        assertTrue(AppMembershipService.isMember(TENANT, appId, "cross-tenant-user"),
                "second grant should be visible before delete");

        boolean deleted = AppManager.deleteApp(TENANT, appId);
        assertTrue(deleted, "deleteApp should report success");

        assertFalse(AppMembershipService.isMember(TENANT, appId, "owner-user"),
                "deleteApp must cascade-delete the app's membership grants, not orphan them");
        assertFalse(AppMembershipService.isMember(TENANT, appId, "cross-tenant-user"),
                "deleteApp must cascade-delete every member, not just the first row");
        assertTrue(AppMembershipService.listMembers(TENANT, appId).isEmpty(),
                "no membership rows should remain for a deleted app");
    }
}
