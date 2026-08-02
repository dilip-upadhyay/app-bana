package com.appbana.security;

import com.appbana.JdbcManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S2.2 — {@link AppMembershipService}. {@code isMember} is hardcoded {@code false} in
 * {@link TenantAccessGuard} until S2.6 wires this service in, so nothing else in the suite
 * constrains its behavior — these tests are written alongside the service, not deferred to S2.6.
 */
public class AppMembershipServiceTest {

    private static final String TENANT_A = "s22-tenant-a";
    private static final String TENANT_B = "s22-tenant-b";
    private static final String APP_1 = "s22-app-1";
    private static final String APP_2 = "s22-app-2";

    @BeforeAll
    public static void setUpDb() throws Exception {
        try (Connection c = JdbcManager.getConnection("default");
             Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS appbana_app_members (" +
                    "tenant_id VARCHAR(255) NOT NULL, " +
                    "app_id VARCHAR(255) NOT NULL, " +
                    "user_id VARCHAR(255) NOT NULL, " +
                    "role VARCHAR(20) NOT NULL CHECK (role IN ('owner', 'member', 'end-user')), " +
                    "granted_by VARCHAR(255) NOT NULL, " +
                    "granted_at TIMESTAMP NOT NULL DEFAULT NOW(), " +
                    "PRIMARY KEY (tenant_id, app_id, user_id))");
        }
    }

    @BeforeEach
    public void cleanFixtureRows() throws Exception {
        try (Connection c = JdbcManager.getConnection("default");
             Statement s = c.createStatement()) {
            // Scoped to this test's own fixture tenants -- never a blanket DELETE, which would
            // wipe every real app's membership grants in the shared dev Postgres instance.
            s.execute("DELETE FROM appbana_app_members WHERE tenant_id IN ('" + TENANT_A + "', '" + TENANT_B + "')");
        }
    }

    @Test
    public void grantThenIsMemberIsTrue() {
        AppMembershipService.grant(TENANT_A, APP_1, "user1", AppMembershipService.Role.MEMBER, "admin");
        assertTrue(AppMembershipService.isMember(TENANT_A, APP_1, "user1"));
    }

    @Test
    public void isMemberIsFalseWithNoGrant() {
        assertFalse(AppMembershipService.isMember(TENANT_A, APP_1, "nobody"));
    }

    @Test
    public void isMemberIsFalseForBlankOrNullUserId() {
        assertFalse(AppMembershipService.isMember(TENANT_A, APP_1, null));
        assertFalse(AppMembershipService.isMember(TENANT_A, APP_1, ""));
    }

    /** The end-user trap, half 1: isMember must be permissive -- true for every role including end-user. */
    @Test
    public void isMemberIsTrueForEveryRoleIncludingEndUser() {
        AppMembershipService.grant(TENANT_A, APP_1, "owner-user", AppMembershipService.Role.OWNER, "admin");
        AppMembershipService.grant(TENANT_A, APP_1, "member-user", AppMembershipService.Role.MEMBER, "admin");
        AppMembershipService.grant(TENANT_A, APP_1, "end-user-user", AppMembershipService.Role.END_USER, "admin");

        assertTrue(AppMembershipService.isMember(TENANT_A, APP_1, "owner-user"));
        assertTrue(AppMembershipService.isMember(TENANT_A, APP_1, "member-user"));
        assertTrue(AppMembershipService.isMember(TENANT_A, APP_1, "end-user-user"),
                "isMember must be permissive -- an end-user grant is still a grant");
    }

    /** The end-user trap, half 2: isOwner must be strict -- true ONLY for the owner role. */
    @Test
    public void isOwnerIsTrueOnlyForOwnerRole() {
        AppMembershipService.grant(TENANT_A, APP_1, "owner-user", AppMembershipService.Role.OWNER, "admin");
        AppMembershipService.grant(TENANT_A, APP_1, "member-user", AppMembershipService.Role.MEMBER, "admin");
        AppMembershipService.grant(TENANT_A, APP_1, "end-user-user", AppMembershipService.Role.END_USER, "admin");

        assertTrue(AppMembershipService.isOwner(TENANT_A, APP_1, "owner-user"));
        assertFalse(AppMembershipService.isOwner(TENANT_A, APP_1, "member-user"),
                "a member grant must never satisfy isOwner");
        assertFalse(AppMembershipService.isOwner(TENANT_A, APP_1, "end-user-user"),
                "an end-user grant must never satisfy isOwner -- a data-access-only grant must never "
                        + "imply management rights");
    }

    @Test
    public void revokeRemovesMembership() {
        AppMembershipService.grant(TENANT_A, APP_1, "user1", AppMembershipService.Role.MEMBER, "admin");
        assertTrue(AppMembershipService.isMember(TENANT_A, APP_1, "user1"));

        AppMembershipService.revoke(TENANT_A, APP_1, "user1");
        assertFalse(AppMembershipService.isMember(TENANT_A, APP_1, "user1"));
    }

    @Test
    public void reGrantingUpdatesTheExistingRoleRatherThanErroring() {
        AppMembershipService.grant(TENANT_A, APP_1, "user1", AppMembershipService.Role.MEMBER, "admin");
        assertFalse(AppMembershipService.isOwner(TENANT_A, APP_1, "user1"));

        AppMembershipService.grant(TENANT_A, APP_1, "user1", AppMembershipService.Role.OWNER, "admin");
        assertTrue(AppMembershipService.isOwner(TENANT_A, APP_1, "user1"));
    }

    @Test
    public void listMembersReturnsEveryGrantForOneApp() {
        AppMembershipService.grant(TENANT_A, APP_1, "user1", AppMembershipService.Role.OWNER, "admin");
        AppMembershipService.grant(TENANT_A, APP_1, "user2", AppMembershipService.Role.MEMBER, "admin");
        // A grant on a DIFFERENT app must not appear in APP_1's listing.
        AppMembershipService.grant(TENANT_A, APP_2, "user3", AppMembershipService.Role.OWNER, "admin");

        List<AppMembershipService.Member> members = AppMembershipService.listMembers(TENANT_A, APP_1);

        assertEquals(2, members.size());
        assertTrue(members.stream().anyMatch(m -> m.userId().equals("user1") && m.role() == AppMembershipService.Role.OWNER));
        assertTrue(members.stream().anyMatch(m -> m.userId().equals("user2") && m.role() == AppMembershipService.Role.MEMBER));
    }

    /** The one deliberately cross-tenant lookup: a user's grants across DIFFERENT tenants' apps. */
    @Test
    public void listAppsForUserFindsGrantsAcrossDifferentTenants() {
        AppMembershipService.grant(TENANT_A, APP_1, "cross-tenant-user", AppMembershipService.Role.MEMBER, "admin");
        AppMembershipService.grant(TENANT_B, APP_2, "cross-tenant-user", AppMembershipService.Role.END_USER, "admin");
        // A grant belonging to someone else must not leak into this user's own list.
        AppMembershipService.grant(TENANT_A, APP_1, "someone-else", AppMembershipService.Role.OWNER, "admin");

        List<AppMembershipService.MembershipGrant> grants = AppMembershipService.listAppsForUser("cross-tenant-user");

        assertEquals(2, grants.size());
        assertTrue(grants.stream().anyMatch(g -> g.tenantId().equals(TENANT_A) && g.appId().equals(APP_1)
                && g.role() == AppMembershipService.Role.MEMBER));
        assertTrue(grants.stream().anyMatch(g -> g.tenantId().equals(TENANT_B) && g.appId().equals(APP_2)
                && g.role() == AppMembershipService.Role.END_USER));
    }

    @Test
    public void listAppsForUserIsEmptyForBlankOrNullUserId() {
        assertTrue(AppMembershipService.listAppsForUser(null).isEmpty());
        assertTrue(AppMembershipService.listAppsForUser("").isEmpty());
    }
}
