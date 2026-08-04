package com.appbana.security;

import com.appbana.JdbcManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AppMembershipGuardTest — S2.9 (docs/planning/TENANT_ISOLATION_IMPLEMENTATION_TASKS.md).
 *
 * <p>Direct unit coverage for {@link AppMembershipService#isSoleOwner}, the "last-owner lockout
 * guard" introduced in S2.7 round-44 and used by {@code AppMembershipRoutes} to refuse a revoke
 * or demoting grant that would leave an app with zero owners. Until now this method had no direct
 * test — its only coverage was indirect, via {@code AppMembershipRoutesTest}'s HTTP-level 409
 * assertions (which exercise the whole request pipeline, not this method's own boundary
 * conditions in isolation: two-owner apps, member/end-user callers, zero-membership apps, and
 * tenant/app scoping).
 */
public class AppMembershipGuardTest {

    private static final String TENANT_A = "s29-guard-tenant-a";
    private static final String TENANT_B = "s29-guard-tenant-b";
    private static final String APP_1 = "s29-guard-app-1";
    private static final String APP_2 = "s29-guard-app-2";

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
            s.execute("DELETE FROM appbana_app_members WHERE tenant_id IN ('" + TENANT_A + "', '" + TENANT_B + "')");
        }
    }

    @Test
    public void isSoleOwnerTrueWhenExactlyOneOwnerAndCallerIsThatOwner() {
        AppMembershipService.grant(TENANT_A, APP_1, "owner1", AppMembershipService.Role.OWNER, "admin");
        assertTrue(AppMembershipService.isSoleOwner(TENANT_A, APP_1, "owner1"));
    }

    @Test
    public void isSoleOwnerFalseWhenTwoOwnersExist() {
        AppMembershipService.grant(TENANT_A, APP_1, "owner1", AppMembershipService.Role.OWNER, "admin");
        AppMembershipService.grant(TENANT_A, APP_1, "owner2", AppMembershipService.Role.OWNER, "admin");

        assertFalse(AppMembershipService.isSoleOwner(TENANT_A, APP_1, "owner1"),
                "with two owners, neither is the SOLE owner");
        assertFalse(AppMembershipService.isSoleOwner(TENANT_A, APP_1, "owner2"));
    }

    @Test
    public void isSoleOwnerFalseWhenCallerIsAMemberNotAnOwner() {
        AppMembershipService.grant(TENANT_A, APP_1, "owner1", AppMembershipService.Role.OWNER, "admin");
        AppMembershipService.grant(TENANT_A, APP_1, "member1", AppMembershipService.Role.MEMBER, "admin");

        assertFalse(AppMembershipService.isSoleOwner(TENANT_A, APP_1, "member1"),
                "a member role must never satisfy isSoleOwner, even though it's the only MEMBER row");
    }

    @Test
    public void isSoleOwnerFalseWhenCallerIsAnEndUserNotAnOwner() {
        AppMembershipService.grant(TENANT_A, APP_1, "owner1", AppMembershipService.Role.OWNER, "admin");
        AppMembershipService.grant(TENANT_A, APP_1, "enduser1", AppMembershipService.Role.END_USER, "admin");

        assertFalse(AppMembershipService.isSoleOwner(TENANT_A, APP_1, "enduser1"));
    }

    @Test
    public void isSoleOwnerFalseWhenCallerHasNoMembershipRowAtAll() {
        AppMembershipService.grant(TENANT_A, APP_1, "owner1", AppMembershipService.Role.OWNER, "admin");
        assertFalse(AppMembershipService.isSoleOwner(TENANT_A, APP_1, "nobody"));
    }

    @Test
    public void isSoleOwnerFalseWhenAppHasNoMembersAtAll() {
        assertFalse(AppMembershipService.isSoleOwner(TENANT_A, APP_1, "owner1"));
    }

    /** Same scoping hazard class as round 33/35's isMember/isOwner/listMembers tests. */
    @Test
    public void isSoleOwnerIsFalseForTheRightAppInTheWrongTenant() {
        AppMembershipService.grant(TENANT_A, APP_1, "owner1", AppMembershipService.Role.OWNER, "admin");
        assertFalse(AppMembershipService.isSoleOwner(TENANT_B, APP_1, "owner1"),
                "a sole-owner grant scoped to TENANT_A must not be visible under TENANT_B, same app id");
    }

    @Test
    public void isSoleOwnerIsFalseForTheWrongAppInTheRightTenant() {
        AppMembershipService.grant(TENANT_A, APP_1, "owner1", AppMembershipService.Role.OWNER, "admin");
        assertFalse(AppMembershipService.isSoleOwner(TENANT_A, APP_2, "owner1"),
                "a sole-owner grant scoped to APP_1 must not be visible under APP_2, same tenant");
    }

    /**
     * Documents, deterministically and without real thread concurrency, the known TOCTOU race
     * flagged in {@link AppMembershipService#isSoleOwner}'s own Javadoc (S2.7 round-45, LOW,
     * "flag for S2.9 if this initiative revisits hardening"): the check and the caller's
     * subsequent mutation are not one transaction, so two requests that each observe
     * {@code ownerCount == 2} before either mutates can both proceed, leaving zero owners.
     *
     * <p>This test simulates the interleaving a lock-free race would produce — check A, check B,
     * THEN act A, act B — using ordinary sequential calls (no {@code Thread}/{@code Future}
     * needed, and no flaky timing dependency): with two owners, {@code isSoleOwner} is false for
     * both, exactly as it would be if two concurrent requests each ran the check first. Revoking
     * both afterward (what each request's handler would do once its own check passed) leaves the
     * app with zero owners — reproducing the documented gap rather than merely asserting it exists
     * in prose. This is a demonstration of an accepted, deliberately-deferred LOW-severity
     * limitation, not a regression: a real fix needs a transactional {@code SELECT ... FOR UPDATE}
     * or a DB-level constraint, out of scope per S2.7/S2.9 (no cross-tenant or privilege-escalation
     * angle — self-inflicted, operator-recoverable).
     */
    @Test
    public void documentedRaceTwoInterleavedChecksBeforeEitherMutationCanLeaveZeroOwners() {
        AppMembershipService.grant(TENANT_A, APP_1, "owner1", AppMembershipService.Role.OWNER, "admin");
        AppMembershipService.grant(TENANT_A, APP_1, "owner2", AppMembershipService.Role.OWNER, "admin");

        // Both concurrent requests' guard checks run BEFORE either's mutation — this is the race
        // window. Each individually observes "not the sole owner" and so neither is blocked.
        boolean requestOneCheckPassed = !AppMembershipService.isSoleOwner(TENANT_A, APP_1, "owner1");
        boolean requestTwoCheckPassed = !AppMembershipService.isSoleOwner(TENANT_A, APP_1, "owner2");
        assertTrue(requestOneCheckPassed && requestTwoCheckPassed,
                "both racing requests' guard checks must pass individually — this is the documented gap, not a bug in this test");

        // Now each request performs the mutation its own (already-passed) check authorized.
        AppMembershipService.revoke(TENANT_A, APP_1, "owner1");
        AppMembershipService.revoke(TENANT_A, APP_1, "owner2");

        assertTrue(AppMembershipService.listMembers(TENANT_A, APP_1).isEmpty(),
                "reproduces the documented S2.7 round-45 limitation: without a transactional guard, "
                        + "two interleaved revokes can leave the app with zero owners");
    }
}
