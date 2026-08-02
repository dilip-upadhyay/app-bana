package com.appbana.security;

import com.appbana.JdbcManager;
import org.junit.jupiter.api.AfterAll;
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
 *
 * <p>Deliberately does NOT declare its own {@code appbana_app_members} DDL (round 33 review, MEDIUM):
 * relying on V19 (S2.11-proven to apply to a genuinely empty database) is the entire point of that
 * migration existing, and a fixture re-declaring the schema is a second, driftable source of truth
 * for it -- exactly the pattern that kept an approval-column defect invisible across 281 green tests.
 */
public class AppMembershipServiceTest {

    private static final String TENANT_A = "s22-tenant-a";
    private static final String TENANT_B = "s22-tenant-b";
    private static final String APP_1 = "s22-app-1";
    private static final String APP_2 = "s22-app-2";

    @BeforeEach
    public void cleanFixtureRows() throws Exception {
        deleteFixtureRows();
    }

    @AfterAll
    public static void sweepFixtureRows() throws Exception {
        // @BeforeEach cleans BEFORE each test, so whichever test runs last always leaves its rows
        // behind for the shared dev Postgres instance -- this closes that gap (round 33 housekeeping).
        deleteFixtureRows();
    }

    private static void deleteFixtureRows() throws Exception {
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

    /**
     * Round 33 review, HIGH: tenant_id/app_id are the actual isolation this table exists to enforce,
     * and were previously untested -- proven by a mutation making both predicates vacuous, which left
     * every prior test (and the full 442-test suite) green. A grant in (TENANT_A, APP_1) must not be
     * visible under the right app in the wrong tenant, nor the wrong app in the right tenant.
     */
    @Test
    public void isMemberIsFalseForTheRightAppInTheWrongTenant() {
        AppMembershipService.grant(TENANT_A, APP_1, "user1", AppMembershipService.Role.OWNER, "admin");
        assertFalse(AppMembershipService.isMember(TENANT_B, APP_1, "user1"),
                "a grant scoped to TENANT_A must not be visible under TENANT_B, same app id");
    }

    @Test
    public void isMemberIsFalseForTheWrongAppInTheRightTenant() {
        AppMembershipService.grant(TENANT_A, APP_1, "user1", AppMembershipService.Role.OWNER, "admin");
        assertFalse(AppMembershipService.isMember(TENANT_A, APP_2, "user1"),
                "a grant scoped to APP_1 must not be visible under APP_2, same tenant");
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

    /** Round 33 review, HIGH: same scope-vacuity mutation also hit isOwner -- same two negatives. */
    @Test
    public void isOwnerIsFalseForTheRightAppInTheWrongTenant() {
        AppMembershipService.grant(TENANT_A, APP_1, "owner-user", AppMembershipService.Role.OWNER, "admin");
        assertFalse(AppMembershipService.isOwner(TENANT_B, APP_1, "owner-user"),
                "an owner grant scoped to TENANT_A must not be visible under TENANT_B, same app id");
    }

    @Test
    public void isOwnerIsFalseForTheWrongAppInTheRightTenant() {
        AppMembershipService.grant(TENANT_A, APP_1, "owner-user", AppMembershipService.Role.OWNER, "admin");
        assertFalse(AppMembershipService.isOwner(TENANT_A, APP_2, "owner-user"),
                "an owner grant scoped to APP_1 must not be visible under APP_2, same tenant");
    }

    @Test
    public void revokeRemovesMembership() {
        AppMembershipService.grant(TENANT_A, APP_1, "user1", AppMembershipService.Role.MEMBER, "admin");
        assertTrue(AppMembershipService.isMember(TENANT_A, APP_1, "user1"));

        AppMembershipService.revoke(TENANT_A, APP_1, "user1");
        assertFalse(AppMembershipService.isMember(TENANT_A, APP_1, "user1"));
    }

    /**
     * Round 33 review, HIGH: the same mutation also made revoke's WHERE clause scope-vacuous, which
     * would delete every grant the user holds anywhere. Revoking one exact (tenant, app, user) triple
     * must leave that same user's grant on another app, that user's grant in another tenant, and
     * another user's grant on the same app all intact.
     */
    @Test
    public void revokeOnlyRemovesTheExactTenantAppUserTripleAndLeavesEverythingElseIntact() {
        AppMembershipService.grant(TENANT_A, APP_1, "user1", AppMembershipService.Role.MEMBER, "admin");
        AppMembershipService.grant(TENANT_A, APP_2, "user1", AppMembershipService.Role.MEMBER, "admin");
        AppMembershipService.grant(TENANT_B, APP_1, "user1", AppMembershipService.Role.MEMBER, "admin");
        AppMembershipService.grant(TENANT_A, APP_1, "user2", AppMembershipService.Role.MEMBER, "admin");

        AppMembershipService.revoke(TENANT_A, APP_1, "user1");

        assertFalse(AppMembershipService.isMember(TENANT_A, APP_1, "user1"), "the revoked triple itself");
        assertTrue(AppMembershipService.isMember(TENANT_A, APP_2, "user1"),
                "the same user's grant on a DIFFERENT app must survive");
        assertTrue(AppMembershipService.isMember(TENANT_B, APP_1, "user1"),
                "the same user's grant in a DIFFERENT tenant must survive");
        assertTrue(AppMembershipService.isMember(TENANT_A, APP_1, "user2"),
                "a DIFFERENT user's grant on the same (tenant, app) must survive");
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
