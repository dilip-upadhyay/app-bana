package com.appbana.middleware;

import com.appbana.api.Router;
import com.appbana.service.SessionService;
import com.appbana.service.SessionService.SessionData;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for SessionMiddleware (Story 2.1: Session Management).
 *
 * <p>This class unit-tests {@link SessionMiddleware#create()} in isolation — it never touches
 * {@code Router}, {@code AppRoutes}, or {@code TenantAccessGuard}. Its {@code isExcludedPath}
 * check is path-only and method-blind (it never reads {@code req.method()}), so "excluded here"
 * means only "this class itself never requires a session for this path" — it does NOT mean the
 * path is unauthenticated end-to-end. Several excluded-path tests below cover routes that a
 * SEPARATE, later layer (route-level {@code TenantAccessGuard} or an admin-token gate) protects
 * instead (S1.12, correcting S1.9-era assumptions that this class's own carve-outs had changed —
 * they hadn't; only the route layer gained a second, independent check on top).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SessionMiddlewareTest {

    private Router.HttpRequest req;
    private Router.HttpResponse res;
    private Map<String, Object> attributes;

    @BeforeEach
    void setUp() {
        SessionService.clearAllSessions();

        // Create mock request and response
        req = Mockito.mock(Router.HttpRequest.class);
        res = Mockito.mock(Router.HttpResponse.class);

        // Track attributes set on request
        attributes = new HashMap<>();
        doAnswer(inv -> {
            attributes.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(req).setAttribute(anyString(), any());

        when(req.getAttribute(anyString())).thenAnswer(inv -> attributes.get(inv.getArgument(0)));
    }

    @AfterEach
    void tearDown() {
        SessionService.clearAllSessions();
    }

    // ========================================
    // Test Group 1: Excluded Paths (No Auth Required)
    // ========================================

    @Test
    @DisplayName("Should allow /api/auth/login without session")
    void testLoginPathExcluded() {
        when(req.path()).thenReturn("/api/auth/login");

        SessionMiddleware.create().accept(req, res);

        verify(res, never()).json(anyInt(), any());
        assertNull(attributes.get("userId"));
    }

    @Test
    @DisplayName("Should allow /api/auth/register without session")
    void testRegisterPathExcluded() {
        when(req.path()).thenReturn("/api/auth/register");

        SessionMiddleware.create().accept(req, res);

        verify(res, never()).json(anyInt(), any());
        assertNull(attributes.get("userId"));
    }

    @Test
    @DisplayName("Should allow /health without session")
    void testHealthPathExcluded() {
        when(req.path()).thenReturn("/health");

        SessionMiddleware.create().accept(req, res);

        verify(res, never()).json(anyInt(), any());
    }

    @Test
    @DisplayName("Should allow /ui/ paths without session")
    void testUiPathExcluded() {
        when(req.path()).thenReturn("/ui/swagger");

        SessionMiddleware.create().accept(req, res);

        verify(res, never()).json(anyInt(), any());
    }

    @Test
    @DisplayName("Should allow /api/csrf/token without session")
    void testCsrfTokenPathExcluded() {
        when(req.path()).thenReturn("/api/csrf/token");

        SessionMiddleware.create().accept(req, res);

        verify(res, never()).json(anyInt(), any());
    }

    @Test
    @DisplayName("Should allow /api/templates (read shape) without session")
    void testTemplatesReadPathExcluded() {
        when(req.path()).thenReturn("/api/templates");

        SessionMiddleware.create().accept(req, res);

        verify(res, never()).json(anyInt(), any());
    }

    @Test
    @DisplayName("/api/templates/{id} (write shape) is ALSO excluded here — this class is method-blind")
    void testTemplatesWritePathAlsoExcludedAtThisLayer() {
        // isExcludedPath() never reads req.method(), so it cannot and does not distinguish
        // GET /api/templates/{id} from POST/PUT/DELETE on the same path — both are excluded from
        // THIS class's own session check. Real write protection for POST/PUT/DELETE
        // /api/templates(/{id}) is a separate, unconditional admin-token gate
        // (AuthService.hasAdmin) inside AppRoutes.java itself (S1.6, hardened by the B2 fix),
        // exercised end-to-end by AppRoutesTenantIsolationTest — not by this class or this test.
        when(req.path()).thenReturn("/api/templates/some-template-id");

        SessionMiddleware.create().accept(req, res);

        verify(res, never()).json(anyInt(), any());
    }

    @Test
    @DisplayName("/api/{tenantId}/apps/{id}/full is excluded HERE, but TenantAccessGuard requires a session at the route")
    void testPublicRuntimeAppsPathExcluded() {
        // Real registered route (AppRoutes.java): GET /api/{tenantId}/apps/{id}/full — the fake
        // 3-segment /api/apps/{id}/full shape this test used before S1.12 matches no real route.
        // SessionMiddleware's own "/apps/" carve-out (below) is unchanged since before S1.9 and
        // still excludes this shape unconditionally; S1.9 added a SEPARATE, route-level
        // TenantAccessGuard.requireOwnTenant call inside AppRoutes.java's handler that now 401s an
        // unauthenticated caller anyway. Verified live (S1.12): an unauthenticated GET against the
        // real running route returns 401 with TenantAccessGuard's message shape
        // ({"error":"Unauthorized: valid session required"}), not SessionMiddleware's — proving
        // the 401 comes from the route layer, not this class. That end-to-end behavior is covered
        // by CrossTenantAppAccessTest (S1.11), not here — this test only proves THIS class's own
        // carve-out still exists.
        when(req.path()).thenReturn("/api/default/apps/hr-management-app/full");

        SessionMiddleware.create().accept(req, res);

        verify(res, never()).json(anyInt(), any());
        assertNull(attributes.get("userId"));
    }

    @Test
    @DisplayName("/api/{tenantId}/apps/{id}/env/{env}/full is excluded HERE, but TenantAccessGuard requires a session at the route")
    void testPublicDeployedAppsPathExcluded() {
        // Same story as testPublicRuntimeAppsPathExcluded above, for AppRoutes.java's sibling
        // GET /api/{tenantId}/apps/{id}/env/{env}/full route.
        when(req.path()).thenReturn("/api/default/apps/hr-management-app/env/DEV/full");

        SessionMiddleware.create().accept(req, res);

        verify(res, never()).json(anyInt(), any());
        assertNull(attributes.get("userId"));
    }

    // ========================================
    // Test Group 2: Protected Paths (Auth Required)
    // ========================================

    @Test
    @DisplayName("Should reject a session-required path when no session provided")
    void testProtectedPathRejectsNoSession() {
        when(req.path()).thenReturn("/dashboard");
        when(req.header("X-Session-Token")).thenReturn(null);

        SessionMiddleware.create().accept(req, res);

        verify(res).json(eq(401), any(Map.class));
        verify(res).setHeader("WWW-Authenticate", "Session realm=\"AppBana\"");
    }

    // ========================================
    // Test Group 3: Studio Builder Paths (Require Auth)
    // ========================================

    @Test
    @DisplayName("Should require session for /appbana-studio/apps paths")
    void testStudioAppsPathRequiresSession() {
        when(req.path()).thenReturn("/appbana-studio/apps");
        when(req.header("X-Session-Token")).thenReturn(null);

        SessionMiddleware.create().accept(req, res);

        verify(res).json(eq(401), any(Map.class));
    }

    @Test
    @DisplayName("Should require session for /appbana-studio/apps/{id} paths")
    void testStudioAppByIdRequiresSession() {
        when(req.path()).thenReturn("/appbana-studio/apps/my-app-id");
        when(req.header("X-Session-Token")).thenReturn(null);

        SessionMiddleware.create().accept(req, res);

        verify(res).json(eq(401), any(Map.class));
    }

    @Test
    @DisplayName("Should require session for /appbana-studio/apps/{id}/pages/{pageId} paths")
    void testStudioPagesRequireSession() {
        when(req.path()).thenReturn("/appbana-studio/apps/my-app/pages/home");
        when(req.header("X-Session-Token")).thenReturn(null);

        SessionMiddleware.create().accept(req, res);

        verify(res).json(eq(401), any(Map.class));
    }

    // ========================================
    // Test Group 4: Session Token Extraction
    // ========================================

    @Test
    @DisplayName("Should extract token from X-Session-Token header")
    void testExtractFromHeader() {
        SessionData session = SessionService.createSession("user123");
        when(req.path()).thenReturn("/dashboard");
        when(req.header("X-Session-Token")).thenReturn(session.sessionId());

        SessionMiddleware.create().accept(req, res);

        verify(res, never()).json(anyInt(), any());
        assertEquals("user123", attributes.get("userId"));
    }

    @Test
    @DisplayName("Should extract token from Authorization Bearer")
    void testExtractFromAuthBearer() {
        SessionData session = SessionService.createSession("user123");
        when(req.path()).thenReturn("/dashboard");
        when(req.header("X-Session-Token")).thenReturn(null);
        when(req.header("Authorization")).thenReturn("Bearer " + session.sessionId());

        SessionMiddleware.create().accept(req, res);

        verify(res, never()).json(anyInt(), any());
        assertEquals("user123", attributes.get("userId"));
    }

    // ========================================
    // Test Group 4: Session Validation
    // ========================================

    @Test
    @DisplayName("Should validate valid session")
    void testValidatesValidSession() {
        SessionData session = SessionService.createSession("user123");
        when(req.path()).thenReturn("/dashboard");
        when(req.header("X-Session-Token")).thenReturn(session.sessionId());

        SessionMiddleware.create().accept(req, res);

        verify(res, never()).json(anyInt(), any());
        assertEquals("user123", attributes.get("userId"));
        assertEquals(session.sessionId(), attributes.get("sessionId"));
    }

    @Test
    @DisplayName("Should reject invalid session token")
    void testRejectsInvalidSession() {
        when(req.path()).thenReturn("/dashboard");
        when(req.header("X-Session-Token")).thenReturn("invalid-session-id");

        SessionMiddleware.create().accept(req, res);

        verify(res).json(eq(401), any(Map.class));
    }

    // ========================================
    // Test Group 5: Session Renewal
    // ========================================

    @Test
    @DisplayName("Should renew session on valid access")
    void testRenewsSessionOnAccess() throws InterruptedException {
        SessionData session = SessionService.createSession("user123");
        long originalExpiration = session.expiresAt();

        Thread.sleep(100);

        when(req.path()).thenReturn("/dashboard");
        when(req.header("X-Session-Token")).thenReturn(session.sessionId());

        SessionMiddleware.create().accept(req, res);

        verify(res, never()).json(anyInt(), any());

        SessionData renewed = SessionService.validateSession(session.sessionId());
        assertNotNull(renewed);
        assertTrue(renewed.expiresAt() > originalExpiration);
    }

    // ========================================
    // Test Group 6: Request Context
    // ========================================

    @Test
    @DisplayName("Should attach userId and sessionId to request")
    void testAttachesUserContext() {
        SessionData session = SessionService.createSession("user123");
        when(req.path()).thenReturn("/dashboard");
        when(req.header("X-Session-Token")).thenReturn(session.sessionId());

        SessionMiddleware.create().accept(req, res);

        assertEquals("user123", attributes.get("userId"));
        assertEquals(session.sessionId(), attributes.get("sessionId"));
    }
}
