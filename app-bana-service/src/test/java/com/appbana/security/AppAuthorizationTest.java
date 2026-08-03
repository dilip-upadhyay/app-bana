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
 * S2.5 — {@link AppAuthorization#isAppOwnerOrSystem} is now membership-aware.
 *
 * <p>Key contract (round 39 reviewer, S2.5 spec): once the membership table has any row for an
 * app it is authoritative — {@code AppMetadata.getAuthor()} is NOT consulted, preventing a
 * demoted-owner from regaining management rights via a stale author field.
 */
public class AppAuthorizationTest {

    private static final String TENANT    = "s25-tenant";
    private static final String APP_ID    = "s25-app";
    private static final String AUTHOR    = "s25-author-user";
    private static final String OTHER     = "s25-other-user";

    @BeforeEach
    public void cleanFixtureRows() throws Exception {
        deleteFixtureRows();
        // Insert a minimal app row so the fallback path can consult author.
        try (Connection c = JdbcManager.getConnection("default");
             Statement s = c.createStatement()) {
            s.execute("INSERT INTO appbana_apps (id, tenant_id, name, author, created_at, updated_at) "
                    + "VALUES ('" + APP_ID + "', '" + TENANT + "', 's25 fixture app', '" + AUTHOR + "', 0, 0) "
                    + "ON CONFLICT (id, tenant_id) DO UPDATE SET author = EXCLUDED.author");
        }
    }

    @AfterAll
    public static void sweepFixtureRows() throws Exception {
        deleteFixtureRows();
    }

    private static void deleteFixtureRows() throws Exception {
        try (Connection c = JdbcManager.getConnection("default");
             Statement s = c.createStatement()) {
            s.execute("DELETE FROM appbana_app_members WHERE tenant_id = '" + TENANT + "'");
            s.execute("DELETE FROM appbana_apps WHERE tenant_id = '" + TENANT + "'");
        }
    }

    // ── membership-aware path ────────────────────────────────────────────────

    @Test
    public void ownerMembershipGrantsOwnership() {
        AppMembershipService.grant(TENANT, APP_ID, AUTHOR, AppMembershipService.Role.OWNER, "test");
        assertTrue(AppAuthorization.isAppOwnerOrSystem(TENANT, APP_ID, AUTHOR));
    }

    @Test
    public void memberRoleDoesNotGrantOwnership() {
        AppMembershipService.grant(TENANT, APP_ID, AUTHOR, AppMembershipService.Role.MEMBER, "test");
        assertFalse(AppAuthorization.isAppOwnerOrSystem(TENANT, APP_ID, AUTHOR),
                "MEMBER role must not satisfy isAppOwnerOrSystem");
    }

    @Test
    public void endUserRoleDoesNotGrantOwnership() {
        AppMembershipService.grant(TENANT, APP_ID, AUTHOR, AppMembershipService.Role.END_USER, "test");
        assertFalse(AppAuthorization.isAppOwnerOrSystem(TENANT, APP_ID, AUTHOR),
                "end-user role must not satisfy isAppOwnerOrSystem");
    }

    @Test
    public void membershipExistsButCallerNotOwnerReturnsFalse() {
        AppMembershipService.grant(TENANT, APP_ID, OTHER, AppMembershipService.Role.OWNER, "test");
        assertFalse(AppAuthorization.isAppOwnerOrSystem(TENANT, APP_ID, AUTHOR),
                "caller is not the owner in the membership table, even though they are the author");
    }

    /**
     * Core S2.5 contract: once any membership row exists the author field is NOT consulted.
     * A user who is recorded as author but holds only MEMBER in the membership table must be
     * denied management rights — the membership table is authoritative.
     */
    @Test
    public void membershipExistsDoesNotFallBackToAuthorField() {
        // AUTHOR is the app's recorded author, but holds only MEMBER in the membership table.
        AppMembershipService.grant(TENANT, APP_ID, AUTHOR, AppMembershipService.Role.MEMBER, "test");
        assertFalse(AppAuthorization.isAppOwnerOrSystem(TENANT, APP_ID, AUTHOR),
                "author field must not be consulted once the membership table has rows for this app");
    }

    // ── pre-S2.4 fallback path (no membership rows) ──────────────────────────

    @Test
    public void noMembershipFallsBackToAppAuthor() {
        // No membership rows — falls back to AppMetadata.getAuthor().
        assertTrue(AppAuthorization.isAppOwnerOrSystem(TENANT, APP_ID, AUTHOR),
                "author field is the fallback when no membership row exists");
    }

    @Test
    public void noMembershipNonMatchingAuthorReturnsFalse() {
        assertFalse(AppAuthorization.isAppOwnerOrSystem(TENANT, APP_ID, OTHER),
                "non-author caller must be denied when no membership row exists");
    }

    // ── fast-path and edge cases (no DB access) ───────────────────────────────

    @Test
    public void systemCallerAlwaysReturnsTrue() {
        assertTrue(AppAuthorization.isAppOwnerOrSystem(TENANT, APP_ID, "system"));
        assertTrue(AppAuthorization.isAppOwnerOrSystem(TENANT, APP_ID, "SYSTEM"),
                "system check is case-insensitive");
    }

    @Test
    public void nullOrBlankCallerReturnsFalse() {
        assertFalse(AppAuthorization.isAppOwnerOrSystem(TENANT, APP_ID, null));
        assertFalse(AppAuthorization.isAppOwnerOrSystem(TENANT, APP_ID, ""));
        assertFalse(AppAuthorization.isAppOwnerOrSystem(TENANT, APP_ID, "   "));
    }
}
