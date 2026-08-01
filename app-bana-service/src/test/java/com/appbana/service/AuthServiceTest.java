package com.appbana.service;

import com.appbana.api.Router;
import com.appbana.config.AppConfig;
import com.appbana.service.SessionService.SessionData;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Regression test for S0.1 — AuthService.resolveIdentity()/extractUserId() unification.
 *
 * Confirms both session credential forms (X-Session-Token, Authorization: Bearer) resolve
 * to the same principal on a route excluded from SessionMiddleware (i.e. no pre-set
 * "userId" request attribute), that the admin/service token priority is preserved and
 * checked first, and that neither form is ever misread as the other (B1 fix).
 *
 * A third form, a session_id cookie, was supported here previously but has been removed
 * (post-S0.1 review fix): nothing in the codebase ever set that cookie.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthServiceTest {

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
    // Both credential forms → same principal
    // ========================================

    @Test
    @DisplayName("X-Session-Token header resolves to the session's user on a middleware-excluded route")
    void testResolvesViaXSessionTokenHeader() {
        SessionData session = SessionService.createSession("user-A");
        when(req.header("X-Session-Token")).thenReturn(session.sessionId());

        assertEquals("user-A", AuthService.resolveIdentity(req, cfg));
        assertEquals("user-A", AuthService.extractUserId(req, cfg));
    }

    @Test
    @DisplayName("Authorization: Bearer <sessionId> resolves to the session's user (B1 fix — previously dropped)")
    void testResolvesViaAuthorizationBearer() {
        SessionData session = SessionService.createSession("user-C");
        when(req.header("Authorization")).thenReturn("Bearer " + session.sessionId());

        assertEquals("user-C", AuthService.resolveIdentity(req, cfg));
        assertEquals("user-C", AuthService.extractUserId(req, cfg));
    }

    @Test
    @DisplayName("Bearer scheme match is case-insensitive, consistent with extractServiceToken")
    void testBearerCaseInsensitive() {
        SessionData session = SessionService.createSession("user-D");
        when(req.header("Authorization")).thenReturn("bearer " + session.sessionId());

        assertEquals("user-D", AuthService.resolveIdentity(req, cfg));
    }

    // ========================================
    // Admin/service token priority (must run before the session fallback)
    // ========================================

    @Test
    @DisplayName("Admin token via Bearer + X-User-Id resolves via priority 1, not session lookup")
    void testAdminTokenViaBearerWithXUserId() {
        when(req.header("Authorization")).thenReturn("Bearer admin-token-xyz");
        when(req.header("X-User-Id")).thenReturn("service-caller-1");

        assertEquals("service-caller-1", AuthService.resolveIdentity(req, cfg));
        assertEquals("service-caller-1", AuthService.extractUserId(req, cfg));
    }

    @Test
    @DisplayName("Admin token via X-AppBana-Token without X-User-Id resolves to literal \"admin\"")
    void testAdminTokenWithoutXUserId() {
        when(req.header("X-AppBana-Token")).thenReturn("admin-token-xyz");

        assertEquals("admin", AuthService.resolveIdentity(req, cfg));
    }

    @Test
    @DisplayName("A real session id sent via Bearer never satisfies hasAdmin() (neither form misread as the other)")
    void testSessionIdViaBearerNeverTreatedAsAdmin() {
        SessionData session = SessionService.createSession("user-E");
        when(req.header("Authorization")).thenReturn("Bearer " + session.sessionId());

        // Must resolve to the real session user, not "admin" or null via a false-positive hasAdmin() match.
        String resolved = AuthService.resolveIdentity(req, cfg);
        assertEquals("user-E", resolved);
        assertNotEquals("admin", resolved);
    }

    // ========================================
    // Session attribute (priority 2) wins over header re-resolution
    // ========================================

    @Test
    @DisplayName("Pre-set session attribute (from SessionMiddleware) takes priority over headers")
    void testSessionAttributeTakesPriority() {
        SessionData other = SessionService.createSession("user-F-from-header");
        when(req.getAttribute("userId")).thenReturn("user-F-from-attribute");
        when(req.header("X-Session-Token")).thenReturn(other.sessionId());

        assertEquals("user-F-from-attribute", AuthService.resolveIdentity(req, cfg));
    }

    // ========================================
    // Negative cases
    // ========================================

    @Test
    @DisplayName("No credentials at all resolves to null")
    void testNoCredentialsResolvesToNull() {
        assertNull(AuthService.resolveIdentity(req, cfg));
        assertNull(AuthService.extractUserId(req, cfg));
    }

    @Test
    @DisplayName("Invalid/unknown session token resolves to null")
    void testInvalidSessionTokenResolvesToNull() {
        when(req.header("X-Session-Token")).thenReturn("not-a-real-session-id");

        assertNull(AuthService.resolveIdentity(req, cfg));
    }

    // ========================================
    // extractSessionCredential — shared helper priority order
    // ========================================

    @Test
    @DisplayName("extractSessionCredential prefers X-Session-Token over Bearer")
    void testExtractSessionCredentialPriorityOrder() {
        when(req.header("X-Session-Token")).thenReturn("from-header");
        when(req.header("Authorization")).thenReturn("Bearer from-bearer");

        assertEquals("from-header", AuthService.extractSessionCredential(req));
    }

    @Test
    @DisplayName("extractSessionCredential falls back to Bearer when header is absent")
    void testExtractSessionCredentialFallsBackToBearer() {
        when(req.header("Authorization")).thenReturn("Bearer from-bearer");

        assertEquals("from-bearer", AuthService.extractSessionCredential(req));
    }
}
