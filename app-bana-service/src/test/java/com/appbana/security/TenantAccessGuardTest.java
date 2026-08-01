package com.appbana.security;

import com.appbana.api.Router;
import com.appbana.config.AppConfig;
import com.appbana.service.SessionService;
import com.appbana.service.SessionService.SessionData;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for TenantAccessGuard (Task S1.2 — Tenant Isolation Security Plan).
 *
 * Covers the full check order: admit-first admin/service token, 401 with no resolved session,
 * 403 on a genuine tenant mismatch, allow on a tenant match, and confirms the S2.6 membership
 * exception ships permanently inert in S1 (a mismatched tenant with a pathAppId present is still
 * denied — there is no AppMembershipService yet for it to consult).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TenantAccessGuardTest {

    private Router.HttpRequest req;
    private AppConfig cfg;

    @BeforeEach
    void setUp() {
        SessionService.clearAllSessions();
        req = mock(Router.HttpRequest.class);
        cfg = new AppConfig();
        cfg.setAdminToken("admin-token-xyz");
        cfg.setReadToken("read-token-abc");
    }

    @AfterEach
    void tearDown() {
        SessionService.clearAllSessions();
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
    @DisplayName("Tenant mismatch WITH pathAppId is still 403 in S1 — membership exception ships permanently inert until S2.6")
    void testTenantMismatchWithAppIdStillDeniedMembershipInert() {
        SessionData session = SessionService.createSession("user-B", "t-B");
        when(req.header("X-Session-Token")).thenReturn(session.sessionId());

        TenantAccessGuard.Result result = TenantAccessGuard.requireOwnTenant(req, cfg, "t-A", "app-owned-by-t-A");

        assertFalse(result.allowed());
        assertEquals(403, result.statusCode());
    }
}
