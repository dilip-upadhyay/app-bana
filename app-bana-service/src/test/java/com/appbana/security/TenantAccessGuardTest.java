package com.appbana.security;

import com.appbana.JdbcManager;
import com.appbana.api.Router;
import com.appbana.config.AppConfig;
import com.appbana.service.SessionService;
import com.appbana.service.SessionService.SessionData;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for TenantAccessGuard (Task S1.2 — Tenant Isolation Security Plan).
 *
 * Covers the full check order: admit-first admin/service token, 401 with no resolved session,
 * 403 on a genuine tenant mismatch, allow on a tenant match, and (since S2.6) that the membership
 * exception is genuinely wired to {@code AppMembershipService.isMember} — both directions: a real
 * membership row admits a cross-tenant caller, and the absence of one still denies.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TenantAccessGuardTest {

    private static final String MEMBER_TENANT = "t-s26-guard-fixture";
    private static final String MEMBER_APP = "s26-guard-fixture-app";

    private Router.HttpRequest req;
    private AppConfig cfg;

    @BeforeEach
    void setUp() {
        SessionService.clearAllSessions();
        req = mock(Router.HttpRequest.class);
        cfg = new AppConfig();
        cfg.setAdminToken("admin-token-xyz");
        cfg.setReadToken("read-token-abc");
        cleanUpFixtureMembership();
    }

    @AfterEach
    void tearDown() {
        SessionService.clearAllSessions();
        cleanUpFixtureMembership();
    }

    // Scoped to this test class's own fixture tenant/app only — never a blanket DELETE against the
    // shared dev Postgres (see RoleRoutesSecurityTest for why that matters).
    private void cleanUpFixtureMembership() {
        try (Connection c = JdbcManager.getConnection("default");
             Statement s = c.createStatement()) {
            s.execute("DELETE FROM appbana_app_members WHERE tenant_id = '" + MEMBER_TENANT
                    + "' AND app_id = '" + MEMBER_APP + "'");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ========================================
    // (0) Admit-first: service/admin token bypasses tenant check entirely
    // ========================================

    @Test
    @DisplayName("Valid admin token via X-AppBana-Token admits regardless of path tenant, no session needed")
    void testAdminTokenViaHeaderAdmitsRegardlessOfTenant() {
        when(req.header("X-AppBana-Token")).thenReturn("admin-token-xyz");

        TenantAccessGuard.Result result = TenantAccessGuard.requireOwnTenant(req, cfg, "t-any-tenant", "app-1");

        assertTrue(result.allowed());
    }

    @Test
    @DisplayName("Valid admin token via Authorization: Bearer admits regardless of path tenant")
    void testAdminTokenViaBearerAdmits() {
        when(req.header("Authorization")).thenReturn("Bearer admin-token-xyz");

        TenantAccessGuard.Result result = TenantAccessGuard.requireOwnTenant(req, cfg, "t-any-tenant", null);

        assertTrue(result.allowed());
    }

    @Test
    @DisplayName("Admin token is checked before the session, even when a mismatched-tenant session is also present")
    void testAdminTokenCheckedBeforeSessionEvenWhenBothPresent() {
        SessionData session = SessionService.createSession("user-A", "t-A");
        when(req.header("X-AppBana-Token")).thenReturn("admin-token-xyz");
        when(req.header("X-Session-Token")).thenReturn(session.sessionId());

        TenantAccessGuard.Result result = TenantAccessGuard.requireOwnTenant(req, cfg, "t-completely-different", "app-1");

        assertTrue(result.allowed());
    }

    @Test
    @DisplayName("A read-only token (not admin) does NOT satisfy the admit-first branch")
    void testReadTokenAloneDoesNotBypassTenantCheck() {
        when(req.header("X-AppBana-Token")).thenReturn("read-token-abc");

        TenantAccessGuard.Result result = TenantAccessGuard.requireOwnTenant(req, cfg, "t-A", null);

        assertFalse(result.allowed());
        assertEquals(401, result.statusCode());
    }

    // ========================================
    // (1) No resolved session at all => 401
    // ========================================

    @Test
    @DisplayName("No credentials at all => 401, not 403")
    void testNoCredentialsReturns401() {
        TenantAccessGuard.Result result = TenantAccessGuard.requireOwnTenant(req, cfg, "t-A", "app-1");

        assertFalse(result.allowed());
        assertEquals(401, result.statusCode());
        assertNotNull(result.message());
    }

    @Test
    @DisplayName("Invalid/unknown session credential => 401")
    void testInvalidSessionCredentialReturns401() {
        when(req.header("X-Session-Token")).thenReturn("not-a-real-session-id");

        TenantAccessGuard.Result result = TenantAccessGuard.requireOwnTenant(req, cfg, "t-A", "app-1");

        assertFalse(result.allowed());
        assertEquals(401, result.statusCode());
    }

    // ========================================
    // (2) Own tenant => allow
    // ========================================

    @Test
    @DisplayName("Session's own tenant matches path tenant => allowed, via SessionMiddleware-attached attribute")
    void testOwnTenantAllowedViaSessionAttribute() {
        SessionData session = SessionService.createSession("user-A", "t-A");
        when(req.getAttribute("sessionId")).thenReturn(session.sessionId());

        TenantAccessGuard.Result result = TenantAccessGuard.requireOwnTenant(req, cfg, "t-A", "app-1");

        assertTrue(result.allowed());
    }

    @Test
    @DisplayName("Session's own tenant matches path tenant => allowed, via credential fallback (middleware-excluded route)")
    void testOwnTenantAllowedViaCredentialFallback() {
        SessionData session = SessionService.createSession("user-A", "t-A");
        when(req.header("X-Session-Token")).thenReturn(session.sessionId());

        TenantAccessGuard.Result result = TenantAccessGuard.requireOwnTenant(req, cfg, "t-A", null);

        assertTrue(result.allowed());
    }

    @Test
    @DisplayName("A session created with no tenantId (legacy/back-compat) never matches a real path tenant")
    void testSessionWithNullTenantIdDoesNotMatchRealTenant() {
        SessionData session = SessionService.createSession("user-A"); // no tenantId overload
        when(req.header("X-Session-Token")).thenReturn(session.sessionId());

        TenantAccessGuard.Result result = TenantAccessGuard.requireOwnTenant(req, cfg, "t-A", null);

        assertFalse(result.allowed());
        assertEquals(403, result.statusCode());
    }

    @Test
    @DisplayName("M1 (review round 1): a null session tenantId must fail closed even when pathTenantId is ALSO " +
            "null — Objects.equals(null, null) == true must not be allowed to leak through as an allow")
    void testNullSessionTenantIdDoesNotMatchNullPathTenant() {
        SessionData session = SessionService.createSession("user-A"); // no tenantId overload
        when(req.header("X-Session-Token")).thenReturn(session.sessionId());

        TenantAccessGuard.Result result = TenantAccessGuard.requireOwnTenant(req, cfg, null, null);

        assertFalse(result.allowed());
        assertEquals(403, result.statusCode());
    }

    // ========================================
    // (3)/(4) Tenant mismatch: 403, and the S2.6 membership exception ships permanently inert
    // ========================================

    @Test
    @DisplayName("Tenant mismatch with no pathAppId (bare tenant-wide route) => 403, no membership exception possible")
    void testTenantMismatchNoAppIdReturns403() {
        SessionData session = SessionService.createSession("user-B", "t-B");
        when(req.header("X-Session-Token")).thenReturn(session.sessionId());

        TenantAccessGuard.Result result = TenantAccessGuard.requireOwnTenant(req, cfg, "t-A", null);

        assertFalse(result.allowed());
        assertEquals(403, result.statusCode());
        assertNotNull(result.message());
    }

    @Test
    @DisplayName("Tenant mismatch WITH pathAppId but NO membership row => still 403 — the exception only fires for a real grant")
    void testTenantMismatchWithAppIdAndNoMembershipRowStillDenied() {
        SessionData session = SessionService.createSession("user-B", "t-B");
        when(req.header("X-Session-Token")).thenReturn(session.sessionId());

        TenantAccessGuard.Result result = TenantAccessGuard.requireOwnTenant(req, cfg, "t-A", "app-owned-by-t-A");

        assertFalse(result.allowed());
        assertEquals(403, result.statusCode());
    }

    @Test
    @DisplayName("S2.6: tenant mismatch WITH a real membership row on that specific app is admitted — the exception is now live, not inert")
    void testTenantMismatchWithRealMembershipRowIsAdmitted() {
        AppMembershipService.grant(MEMBER_TENANT, MEMBER_APP, "user-cross-tenant-member",
                AppMembershipService.Role.MEMBER, "test-setup");
        SessionData session = SessionService.createSession("user-cross-tenant-member", "t-not-" + MEMBER_TENANT);
        when(req.header("X-Session-Token")).thenReturn(session.sessionId());

        TenantAccessGuard.Result result = TenantAccessGuard.requireOwnTenant(req, cfg, MEMBER_TENANT, MEMBER_APP);

        assertTrue(result.allowed(), "a real membership row on this exact app must admit despite the tenant mismatch");
    }

    @Test
    @DisplayName("S2.6: an end-user role also satisfies the tenant-gate membership exception — isMember is permissive by role; the owner-or-system split lives in AppAuthorization, not here")
    void testTenantMismatchWithEndUserMembershipRowIsAlsoAdmittedPastTheTenantGate() {
        AppMembershipService.grant(MEMBER_TENANT, MEMBER_APP, "user-cross-tenant-enduser",
                AppMembershipService.Role.END_USER, "test-setup");
        SessionData session = SessionService.createSession("user-cross-tenant-enduser", "t-not-" + MEMBER_TENANT);
        when(req.header("X-Session-Token")).thenReturn(session.sessionId());

        TenantAccessGuard.Result result = TenantAccessGuard.requireOwnTenant(req, cfg, MEMBER_TENANT, MEMBER_APP);

        assertTrue(result.allowed());
    }
}
