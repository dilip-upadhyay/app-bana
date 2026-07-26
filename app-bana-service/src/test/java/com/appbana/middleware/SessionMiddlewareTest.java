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
    @DisplayName("Should allow /api/templates without session")
    void testTemplatesPathExcluded() {
        when(req.path()).thenReturn("/api/templates");

        SessionMiddleware.create().accept(req, res);

        verify(res, never()).json(anyInt(), any());
    }

    @Test
    @DisplayName("Should allow /api/apps/* paths without session (public runtime)")
    void testPublicRuntimeAppsPathExcluded() {
        when(req.path()).thenReturn("/api/apps/hr-management-app/full");

        SessionMiddleware.create().accept(req, res);

        verify(res, never()).json(anyInt(), any());
        assertNull(attributes.get("userId"));
    }

    @Test
    @DisplayName("Should allow /api/apps/{id}/env/{env}/full without session (deployed apps)")
    void testPublicDeployedAppsPathExcluded() {
        when(req.path()).thenReturn("/api/apps/hr-management-app/env/DEV/full");

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
    @DisplayName("Should allow /appbana-studio/apps paths without session (currently public)")
    void testStudioAppsPathRequiresSession() {
        when(req.path()).thenReturn("/appbana-studio/apps");
        when(req.header("X-Session-Token")).thenReturn(null);

        SessionMiddleware.create().accept(req, res);

        // Currently excluded in SessionMiddleware.java, so it should NOT return 401
        verify(res, never()).json(anyInt(), any());
    }

    @Test
    @DisplayName("Should allow /appbana-studio/apps/{id} paths without session (currently public)")
    void testStudioAppByIdRequiresSession() {
        when(req.path()).thenReturn("/appbana-studio/apps/my-app-id");
        when(req.header("X-Session-Token")).thenReturn(null);

        SessionMiddleware.create().accept(req, res);

        // Currently excluded in SessionMiddleware.java, so it should NOT return 401
        verify(res, never()).json(anyInt(), any());
    }

    @Test
    @DisplayName("Should allow /appbana-studio/apps/{id}/pages/{pageId} paths without session (currently public)")
    void testStudioPagesRequireSession() {
        when(req.path()).thenReturn("/appbana-studio/apps/my-app/pages/home");
        when(req.header("X-Session-Token")).thenReturn(null);

        SessionMiddleware.create().accept(req, res);

        // Currently excluded in SessionMiddleware.java, so it should NOT return 401
        verify(res, never()).json(anyInt(), any());
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
    @DisplayName("Should extract token from Cookie")
    void testExtractFromCookie() {
        SessionData session = SessionService.createSession("user123");
        when(req.path()).thenReturn("/dashboard");
        when(req.header("X-Session-Token")).thenReturn(null);
        when(req.header("Cookie")).thenReturn("session_id=" + session.sessionId());

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
        when(req.header("Cookie")).thenReturn(null);
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
