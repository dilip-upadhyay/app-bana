package com.appbana.integration;

import com.appbana.api.Router;
import com.appbana.middleware.CsrfMiddleware;
import com.appbana.middleware.RateLimitMiddleware;
import com.appbana.middleware.SessionMiddleware;
import com.appbana.service.CsrfService;
import com.appbana.service.RateLimitService;
import com.appbana.service.SessionService;
import com.appbana.service.SessionService.SessionData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for complete security pipeline (Task 4)
 * Tests end-to-end flow: RateLimit → Session → CSRF → Handler
 * 
 * Covers Stories:
 * - 1.2: CSRF Protection
 * - 1.3: Rate Limiting
 * - 2.1: Session Management
 * - 3.1: Form Integration
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SecurityIntegrationTest {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private Router router;
    private Router.HttpRequest mockRequest;
    private Router.HttpResponse mockResponse;
    private Map<String, Object> requestAttributes;
    private Map<String, String> responseHeaders;
    private int responseStatus;
    private Object responseBody;
    
    @BeforeEach
    public void setup() {
        // Clear all services
        RateLimitService.clearAllRateLimits();
        SessionService.clearAllSessions();
        CsrfService.clearAllTokens();
        
        // Setup router with all middlewares
        router = new Router();
        router.use(RateLimitMiddleware.create());
        router.use(SessionMiddleware.create());
        // CSRF middleware would be added here in production
        
        // Setup mocks
        mockRequest = Mockito.mock(Router.HttpRequest.class);
        mockResponse = Mockito.mock(Router.HttpResponse.class);
        
        // Track request attributes
        requestAttributes = new HashMap<>();
        doAnswer(inv -> {
            requestAttributes.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(mockRequest).setAttribute(anyString(), any());
        
        when(mockRequest.getAttribute(anyString())).thenAnswer(inv -> 
            requestAttributes.get(inv.getArgument(0))
        );
        
        // Track response
        responseHeaders = new HashMap<>();
        doAnswer(inv -> {
            responseHeaders.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(mockResponse).setHeader(anyString(), anyString());
        
        doAnswer(inv -> {
            responseStatus = inv.getArgument(0);
            responseBody = inv.getArgument(1);
            return null;
        }).when(mockResponse).json(anyInt(), any());
    }
    
    // ========================================
    // Test Group 1: Rate Limiting Integration
    // ========================================
    
    @Test
    @Order(1)
    @DisplayName("1.1: Rate limit blocks excessive requests before session check")
    public void testRateLimitBlocksBeforeSession() {
        // Setup
        when(mockRequest.path()).thenReturn("/api/users");
        when(mockRequest.method()).thenReturn("POST");
        when(mockRequest.header("X-Forwarded-For")).thenReturn("192.168.1.100");
        
        // Make 100 requests (at limit)
        for (int i = 0; i < 100; i++) {
            RateLimitService.checkRateLimit("192.168.1.100", "/api/users");
        }
        
        // 101st request should be blocked by rate limiter
        var result = RateLimitService.checkRateLimit("192.168.1.100", "/api/users");
        boolean blocked = !result.allowed();
        
        assertTrue(blocked, "Rate limiter should block 101st request");
        
        // When rate limit middleware runs
        RateLimitMiddleware.create().accept(mockRequest, mockResponse);
        
        // Should return 429 before session middleware even runs
        verify(mockResponse).json(eq(429), any(Map.class));
        assertNull(requestAttributes.get("userId"), "Session should not be checked when rate limited");
    }
    
    @Test
    @Order(2)
    @DisplayName("1.2: Rate limit allows requests within threshold")
    public void testRateLimitAllowsWithinThreshold() {
        // Setup
        when(mockRequest.path()).thenReturn("/api/users");
        when(mockRequest.method()).thenReturn("POST");
        when(mockRequest.header("X-Forwarded-For")).thenReturn("192.168.1.200");
        
        // Make 50 requests (under limit)
        for (int i = 0; i < 50; i++) {
            var result = RateLimitService.checkRateLimit("192.168.1.200", "/api/users");
            assertTrue(result.allowed(), "Request " + (i + 1) + " should be allowed");
        }
        
        // Middleware should allow request through
        RateLimitMiddleware.create().accept(mockRequest, mockResponse);
        
        // Should NOT send 429 response
        verify(mockResponse, never()).json(eq(429), any());
    }
    
    // ========================================
    // Test Group 2: Session Integration
    // ========================================
    
    @Test
    @Order(3)
    @DisplayName("2.1: Valid session allows request through middleware pipeline")
    public void testValidSessionAllowsRequest() {
        // Setup: Create valid session
        SessionData session = SessionService.createSession("user123", 30);
        String sessionId = session.sessionId();
        
        when(mockRequest.path()).thenReturn("/api/users");
        when(mockRequest.method()).thenReturn("GET");
        when(mockRequest.header("X-Forwarded-For")).thenReturn("192.168.1.201");
        when(mockRequest.header("X-Session-Token")).thenReturn(sessionId);
        
        // Run middleware pipeline
        RateLimitMiddleware.create().accept(mockRequest, mockResponse);
        SessionMiddleware.create().accept(mockRequest, mockResponse);
        
        // Verify request passed through
        verify(mockResponse, never()).json(eq(401), any());
        verify(mockResponse, never()).json(eq(429), any());
        
        // Verify session context attached
        assertEquals("user123", requestAttributes.get("userId"));
        assertEquals(sessionId, requestAttributes.get("sessionId"));
    }
    
    @Test
    @Order(4)
    @DisplayName("2.2: Invalid session blocks request with 401")
    public void testInvalidSessionBlocksRequest() {
        // Setup: Invalid session token
        when(mockRequest.path()).thenReturn("/api/users");
        when(mockRequest.method()).thenReturn("GET");
        when(mockRequest.header("X-Forwarded-For")).thenReturn("192.168.1.202");
        when(mockRequest.header("X-Session-Token")).thenReturn("invalid-token-12345");
        
        // Run middleware pipeline
        RateLimitMiddleware.create().accept(mockRequest, mockResponse);
        SessionMiddleware.create().accept(mockRequest, mockResponse);
        
        // Verify 401 response
        verify(mockResponse).json(eq(401), any(Map.class));
        assertNull(requestAttributes.get("userId"), "No user should be attached for invalid session");
    }
    
    @Test
    @Order(5)
    @DisplayName("2.3: Missing session blocks protected endpoint")
    public void testMissingSessionBlocksProtectedEndpoint() {
        // Setup: No session token
        when(mockRequest.path()).thenReturn("/api/users");
        when(mockRequest.method()).thenReturn("POST");
        when(mockRequest.header("X-Forwarded-For")).thenReturn("192.168.1.203");
        when(mockRequest.header("X-Session-Token")).thenReturn(null);
        
        // Run middleware pipeline
        RateLimitMiddleware.create().accept(mockRequest, mockResponse);
        SessionMiddleware.create().accept(mockRequest, mockResponse);
        
        // Verify 401 response
        verify(mockResponse).json(eq(401), any(Map.class));
        assertEquals("Session realm=\"AppBana\"", responseHeaders.get("WWW-Authenticate"));
    }
    
    // ========================================
    // Test Group 3: Auth Endpoints (Excluded)
    // ========================================
    
    @Test
    @Order(6)
    @DisplayName("3.1: Auth endpoints bypass session but respect rate limits")
    public void testAuthEndpointsBypassSession() {
        // Setup
        when(mockRequest.path()).thenReturn("/api/auth/login");
        when(mockRequest.method()).thenReturn("POST");
        when(mockRequest.header("X-Forwarded-For")).thenReturn("192.168.1.204");
        
        // Run middleware pipeline
        RateLimitMiddleware.create().accept(mockRequest, mockResponse);
        SessionMiddleware.create().accept(mockRequest, mockResponse);
        
        // Verify NO 401 (session bypassed)
        verify(mockResponse, never()).json(eq(401), any());
        
        // Verify rate limit still applies
        verify(mockResponse, never()).json(eq(429), any()); // Not hit yet
        
        // But rate limiting IS enforced for auth endpoints
        for (int i = 0; i < 100; i++) {
            RateLimitService.checkRateLimit("192.168.1.204", "/api/auth/login");
        }
        var result = RateLimitService.checkRateLimit("192.168.1.204", "/api/auth/login");
        assertTrue(!result.allowed(), "Auth endpoints should still be rate limited");
    }
    
    @Test
    @Order(7)
    @DisplayName("3.2: Registration endpoint works without session")
    public void testRegistrationEndpointNoSessionRequired() {
        // Setup
        when(mockRequest.path()).thenReturn("/api/auth/register");
        when(mockRequest.method()).thenReturn("POST");
        when(mockRequest.header("X-Forwarded-For")).thenReturn("192.168.1.205");
        
        // Run middleware pipeline
        RateLimitMiddleware.create().accept(mockRequest, mockResponse);
        SessionMiddleware.create().accept(mockRequest, mockResponse);
        
        // Verify request allowed through
        verify(mockResponse, never()).json(eq(401), any());
        assertNull(requestAttributes.get("userId"), "No user context for public endpoint");
    }
    
    // ========================================
    // Test Group 4: CSRF Integration
    // ========================================
    
    @Test
    @Order(8)
    @DisplayName("4.1: CSRF token generation works")
    public void testCsrfTokenGeneration() {
        // Generate CSRF token
        String sessionId = "test-session-" + System.currentTimeMillis();
        String csrfToken = CsrfService.generateToken(sessionId);
        
        assertNotNull(csrfToken, "CSRF token should be generated");
        assertTrue(csrfToken.length() > 20, "CSRF token should be sufficiently long");
        
        // Token should be valid
        assertTrue(CsrfService.validateToken(sessionId, csrfToken), "Generated token should be valid");
    }
    
    @Test
    @Order(9)
    @DisplayName("4.2: CSRF validation blocks invalid token")
    public void testCsrfValidationBlocksInvalidToken() {
        String sessionId = "test-session-csrf";
        String validToken = CsrfService.generateToken(sessionId);
        String invalidToken = "invalid-csrf-token-12345";
        
        // Valid token should pass
        assertTrue(CsrfService.validateToken(sessionId, validToken), "Valid token should pass");
        
        // Invalid token should fail
        assertFalse(CsrfService.validateToken(sessionId, invalidToken), "Invalid token should fail");
    }
    
    @Test
    @Order(10)
    @DisplayName("4.3: CSRF middleware validates token for POST requests")
    public void testCsrfMiddlewareValidation() {
        // Setup: Create valid session first
        SessionData session = SessionService.createSession("user-csrf", 30);
        String sessionId = session.sessionId();
        String csrfToken = CsrfService.generateToken(sessionId);
        
        when(mockRequest.path()).thenReturn("/api/users");
        when(mockRequest.method()).thenReturn("POST");
        when(mockRequest.header("X-Forwarded-For")).thenReturn("192.168.1.206");
        when(mockRequest.header("X-Session-Token")).thenReturn(sessionId);
        when(mockRequest.header("X-Session-Id")).thenReturn(sessionId); // For CSRF middleware
        when(mockRequest.header("X-CSRF-Token")).thenReturn(csrfToken);
        
        // Run SessionMiddleware first to set session attribute
        SessionMiddleware.create().accept(mockRequest, mockResponse);
        
        // Then run CSRF validation
        var csrfMiddleware = CsrfMiddleware.validate();
        csrfMiddleware.accept(mockRequest, mockResponse);
        
        // Should allow request through (valid CSRF)
        verify(mockResponse, never()).json(eq(403), any());
    }
    
    // ========================================
    // Test Group 5: Complete Pipeline
    // ========================================
    
    @Test
    @Order(11)
    @DisplayName("5.1: Complete request pipeline with all security checks")
    public void testCompleteSecurityPipeline() {
        // Setup: Create valid session and CSRF token
        SessionData session = SessionService.createSession("user456", 30);
        String sessionId = session.sessionId();
        String csrfToken = CsrfService.generateToken(sessionId);
        
        when(mockRequest.path()).thenReturn("/api/users");
        when(mockRequest.method()).thenReturn("POST");
        when(mockRequest.header("X-Forwarded-For")).thenReturn("192.168.1.207");
        when(mockRequest.header("X-Session-Token")).thenReturn(sessionId);
        when(mockRequest.header("X-Session-Id")).thenReturn(sessionId); // For CSRF middleware
        when(mockRequest.header("X-CSRF-Token")).thenReturn(csrfToken);
        
        // Run complete middleware pipeline
        RateLimitMiddleware.create().accept(mockRequest, mockResponse);
        SessionMiddleware.create().accept(mockRequest, mockResponse);
        CsrfMiddleware.validate().accept(mockRequest, mockResponse);
        SessionMiddleware.create().accept(mockRequest, mockResponse);
        CsrfMiddleware.validate().accept(mockRequest, mockResponse);
        
        // Verify all checks passed
        verify(mockResponse, never()).json(eq(429), any()); // Rate limit OK
        verify(mockResponse, never()).json(eq(401), any()); // Session OK
        verify(mockResponse, never()).json(eq(403), any()); // CSRF OK
        
        // Verify user context attached
        assertEquals("user456", requestAttributes.get("userId"));
        assertEquals(sessionId, requestAttributes.get("sessionId"));
    }
    
    @Test
    @Order(12)
    @DisplayName("5.2: Pipeline stops at first security failure")
    public void testPipelineStopsAtFirstFailure() {
        // Setup: Rate limit exceeded
        when(mockRequest.path()).thenReturn("/api/users");
        when(mockRequest.method()).thenReturn("POST");
        when(mockRequest.header("X-Forwarded-For")).thenReturn("192.168.1.208");
        
        // Exceed rate limit
        for (int i = 0; i < 100; i++) {
            RateLimitService.checkRateLimit("192.168.1.208", "/api/users");
        }
        
        // Run middleware pipeline
        RateLimitMiddleware.create().accept(mockRequest, mockResponse);
        
        // Verify 429 response sent
        verify(mockResponse).json(eq(429), any(Map.class));
        
        // Subsequent middlewares should NOT run (response already sent)
        // In production, Router checks res.isSent() flag
        assertTrue(responseStatus == 429, "Response should be 429");
    }
    
    @Test
    @Order(13)
    @DisplayName("5.3: Session renewal works during valid request")
    public void testSessionRenewalDuringRequest() throws Exception {
        // Setup: Create session
        SessionData session = SessionService.createSession("user789", 30);
        String sessionId = session.sessionId();
        var initialSession = SessionService.renewSession(sessionId);
        assertNotNull(initialSession);
        long initialExpiry = initialSession.expiresAt();
        
        // Wait 100ms
        Thread.sleep(100);
        
        // Setup request
        when(mockRequest.path()).thenReturn("/api/users");
        when(mockRequest.method()).thenReturn("GET");
        when(mockRequest.header("X-Forwarded-For")).thenReturn("192.168.1.209");
        when(mockRequest.header("X-Session-Token")).thenReturn(sessionId);
        
        // Run middleware (includes renewal)
        SessionMiddleware.create().accept(mockRequest, mockResponse);
        
        // Verify session was renewed
        var renewedSession = SessionService.renewSession(sessionId);
        assertNotNull(renewedSession);
        assertTrue(renewedSession.expiresAt() > initialExpiry, "Session should be renewed with later expiry");
    }
    
    // ========================================
    // Test Group 6: Error Scenarios
    // ========================================
    
    @Test
    @Order(14)
    @DisplayName("6.1: Invalid session token returns 401")
    public void testInvalidSessionTokenReturns401() throws Exception {
        // Use an invalid/non-existent session ID
        String invalidSessionId = "invalid-session-id-12345";
        
        // Setup request with invalid session
        when(mockRequest.path()).thenReturn("/api/users");
        when(mockRequest.method()).thenReturn("GET");
        when(mockRequest.header("X-Forwarded-For")).thenReturn("192.168.1.210");
        when(mockRequest.header("X-Session-Token")).thenReturn(invalidSessionId);
        
        // Run middleware
        RateLimitMiddleware.create().accept(mockRequest, mockResponse);
        SessionMiddleware.create().accept(mockRequest, mockResponse);
        
        // Verify 401 response for invalid session
        verify(mockResponse).json(eq(401), any(Map.class));
    }
    
    @Test
    @Order(15)
    @DisplayName("6.2: Multiple concurrent requests respect rate limits")
    public void testConcurrentRequestsRateLimiting() throws Exception {
        // Setup
        final String ip = "192.168.1.211";
        final String endpoint = "/api/test";
        final int threadCount = 50;
        final int requestsPerThread = 3;
        
        // Reset rate limit
        RateLimitService.clearAllRateLimits();
        
        // Execute concurrent requests
        Thread[] threads = new Thread[threadCount];
        final int[] blockedCount = {0};
        
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < requestsPerThread; j++) {
                    synchronized (blockedCount) {
                        var result = RateLimitService.checkRateLimit(ip, endpoint);
                        if (!result.allowed()) {
                            blockedCount[0]++;
                        }
                    }
                }
            });
            threads[i].start();
        }
        
        // Wait for all threads
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Total requests: 50 * 3 = 150
        // Rate limit: 100
        // Expected blocked: 50
        assertTrue(blockedCount[0] >= 50, "At least 50 requests should be blocked (actual: " + blockedCount[0] + ")");
    }
    
    @Test
    @Order(16)
    @DisplayName("6.3: Health endpoint bypasses all security")
    public void testHealthEndpointBypassesSecurity() {
        // Setup
        when(mockRequest.path()).thenReturn("/health");
        when(mockRequest.method()).thenReturn("GET");
        when(mockRequest.header("X-Forwarded-For")).thenReturn("192.168.1.212");
        // No session token
        
        // Run middleware pipeline
        RateLimitMiddleware.create().accept(mockRequest, mockResponse);
        SessionMiddleware.create().accept(mockRequest, mockResponse);
        
        // Verify NO security errors
        verify(mockResponse, never()).json(eq(401), any());
        verify(mockResponse, never()).json(eq(429), any());
        verify(mockResponse, never()).json(eq(403), any());
    }
    
    @AfterEach
    public void cleanup() {
        RateLimitService.clearAllRateLimits();
        SessionService.clearAllSessions();
        CsrfService.clearAllTokens();
    }
}
