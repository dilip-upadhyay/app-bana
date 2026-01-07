package com.appbana.middleware;

import com.appbana.api.Router;
import com.appbana.service.CsrfService;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for CsrfMiddleware.
 * Tests Story 1.2 Task 4: CSRF Validation Middleware
 * 
 * Test Scenarios:
 * 1. Safe methods (GET/HEAD/OPTIONS) bypass CSRF validation
 * 2. Unsafe methods (POST/PUT/DELETE) require CSRF validation
 * 3. Missing session ID returns 403
 * 4. Missing CSRF token returns 403
 * 5. Invalid CSRF token returns 403
 * 6. Valid CSRF token allows request to continue
 * 7. Excluded paths bypass CSRF validation
 * 8. Token expiration is detected
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CsrfMiddlewareTest {
    
    private Router.HttpRequest mockRequest;
    private Router.HttpResponse mockResponse;
    private String testSessionId;
    private String validToken;
    
    @BeforeEach
    void setUp() {
        // Create mock request and response
        mockRequest = Mockito.mock(Router.HttpRequest.class);
        mockResponse = Mockito.mock(Router.HttpResponse.class);
        
        // Generate a valid CSRF token for testing
        testSessionId = "test-session-" + System.currentTimeMillis();
        validToken = CsrfService.generateToken(testSessionId);
    }
    
    @AfterEach
    void tearDown() {
        // Clean up tokens after each test
        CsrfService.invalidateToken(testSessionId);
    }
    
    // ========== Test Group 1: Safe Methods (4 tests) ==========
    
    @Test
    @Order(1)
    @DisplayName("GET requests should bypass CSRF validation")
    void testGetRequestBypassesCsrf() {
        // Arrange
        when(mockRequest.method()).thenReturn("GET");
        when(mockRequest.path()).thenReturn("/api/users");
        
        // Act
        CsrfMiddleware.validate().accept(mockRequest, mockResponse);
        
        // Assert
        verify(mockResponse, never()).json(anyInt(), any());
        verify(mockRequest, never()).header("X-CSRF-Token");
    }
    
    @Test
    @Order(2)
    @DisplayName("HEAD requests should bypass CSRF validation")
    void testHeadRequestBypassesCsrf() {
        // Arrange
        when(mockRequest.method()).thenReturn("HEAD");
        when(mockRequest.path()).thenReturn("/api/users");
        
        // Act
        CsrfMiddleware.validate().accept(mockRequest, mockResponse);
        
        // Assert
        verify(mockResponse, never()).json(anyInt(), any());
    }
    
    @Test
    @Order(3)
    @DisplayName("OPTIONS requests should bypass CSRF validation")
    void testOptionsRequestBypassesCsrf() {
        // Arrange
        when(mockRequest.method()).thenReturn("OPTIONS");
        when(mockRequest.path()).thenReturn("/api/users");
        
        // Act
        CsrfMiddleware.validate().accept(mockRequest, mockResponse);
        
        // Assert
        verify(mockResponse, never()).json(anyInt(), any());
    }
    
    @Test
    @Order(4)
    @DisplayName("Case-insensitive method matching")
    void testCaseInsensitiveMethodMatching() {
        // Arrange
        when(mockRequest.method()).thenReturn("get"); // lowercase
        when(mockRequest.path()).thenReturn("/api/users");
        
        // Act
        CsrfMiddleware.validate().accept(mockRequest, mockResponse);
        
        // Assert
        verify(mockResponse, never()).json(anyInt(), any());
    }
    
    // ========== Test Group 2: Unsafe Methods Require CSRF (4 tests) ==========
    
    @Test
    @Order(5)
    @DisplayName("POST requests require CSRF validation")
    void testPostRequestRequiresCsrf() {
        // Arrange
        when(mockRequest.method()).thenReturn("POST");
        when(mockRequest.path()).thenReturn("/api/users");
        when(mockRequest.header("X-Session-Id")).thenReturn(null); // Missing session
        
        // Act
        CsrfMiddleware.validate().accept(mockRequest, mockResponse);
        
        // Assert
        verify(mockResponse).json(eq(403), argThat(obj -> {
            Map<String, Object> map = (Map<String, Object>) obj;
            return map.get("code").equals("CSRF_SESSION_MISSING");
        }));
    }
    
    @Test
    @Order(6)
    @DisplayName("PUT requests require CSRF validation")
    void testPutRequestRequiresCsrf() {
        // Arrange
        when(mockRequest.method()).thenReturn("PUT");
        when(mockRequest.path()).thenReturn("/api/users/123");
        when(mockRequest.header("X-Session-Id")).thenReturn(null);
        
        // Act
        CsrfMiddleware.validate().accept(mockRequest, mockResponse);
        
        // Assert
        verify(mockResponse).json(eq(403), any());
    }
    
    @Test
    @Order(7)
    @DisplayName("DELETE requests require CSRF validation")
    void testDeleteRequestRequiresCsrf() {
        // Arrange
        when(mockRequest.method()).thenReturn("DELETE");
        when(mockRequest.path()).thenReturn("/api/users/123");
        when(mockRequest.header("X-Session-Id")).thenReturn(null);
        
        // Act
        CsrfMiddleware.validate().accept(mockRequest, mockResponse);
        
        // Assert
        verify(mockResponse).json(eq(403), any());
    }
    
    @Test
    @Order(8)
    @DisplayName("PATCH requests require CSRF validation")
    void testPatchRequestRequiresCsrf() {
        // Arrange
        when(mockRequest.method()).thenReturn("PATCH");
        when(mockRequest.path()).thenReturn("/api/users/123");
        when(mockRequest.header("X-Session-Id")).thenReturn(null);
        
        // Act
        CsrfMiddleware.validate().accept(mockRequest, mockResponse);
        
        // Assert
        verify(mockResponse).json(eq(403), any());
    }
    
    // ========== Test Group 3: Missing Headers (3 tests) ==========
    
    @Test
    @Order(9)
    @DisplayName("Missing session ID returns 403 with CSRF_SESSION_MISSING code")
    void testMissingSessionId() {
        // Arrange
        when(mockRequest.method()).thenReturn("POST");
        when(mockRequest.path()).thenReturn("/api/users");
        when(mockRequest.header("X-Session-Id")).thenReturn(null);
        
        // Act
        CsrfMiddleware.validate().accept(mockRequest, mockResponse);
        
        // Assert
        verify(mockResponse).json(eq(403), argThat(obj -> {
            Map<String, Object> map = (Map<String, Object>) obj;
            return map.get("ok").equals(false) &&
                   map.get("code").equals("CSRF_SESSION_MISSING") &&
                   ((String) map.get("error")).contains("Session ID is required");
        }));
    }
    
    @Test
    @Order(10)
    @DisplayName("Empty session ID returns 403")
    void testEmptySessionId() {
        // Arrange
        when(mockRequest.method()).thenReturn("POST");
        when(mockRequest.path()).thenReturn("/api/users");
        when(mockRequest.header("X-Session-Id")).thenReturn("   "); // Whitespace only
        
        // Act
        CsrfMiddleware.validate().accept(mockRequest, mockResponse);
        
        // Assert
        verify(mockResponse).json(eq(403), argThat(obj -> {
            Map<String, Object> map = (Map<String, Object>) obj;
            return map.get("code").equals("CSRF_SESSION_MISSING");
        }));
    }
    
    @Test
    @Order(11)
    @DisplayName("Missing CSRF token returns 403 with CSRF_TOKEN_MISSING code")
    void testMissingCsrfToken() {
        // Arrange
        when(mockRequest.method()).thenReturn("POST");
        when(mockRequest.path()).thenReturn("/api/users");
        when(mockRequest.header("X-Session-Id")).thenReturn(testSessionId);
        when(mockRequest.header("X-CSRF-Token")).thenReturn(null);
        
        // Act
        CsrfMiddleware.validate().accept(mockRequest, mockResponse);
        
        // Assert
        verify(mockResponse).json(eq(403), argThat(obj -> {
            Map<String, Object> map = (Map<String, Object>) obj;
            return map.get("ok").equals(false) &&
                   map.get("code").equals("CSRF_TOKEN_MISSING") &&
                   ((String) map.get("error")).contains("CSRF token is required");
        }));
    }
    
    // ========== Test Group 4: Invalid Tokens (4 tests) ==========
    
    @Test
    @Order(12)
    @DisplayName("Invalid CSRF token returns 403 with CSRF_TOKEN_INVALID code")
    void testInvalidCsrfToken() {
        // Arrange
        when(mockRequest.method()).thenReturn("POST");
        when(mockRequest.path()).thenReturn("/api/users");
        when(mockRequest.header("X-Session-Id")).thenReturn(testSessionId);
        when(mockRequest.header("X-CSRF-Token")).thenReturn("invalid-token-12345");
        
        // Act
        CsrfMiddleware.validate().accept(mockRequest, mockResponse);
        
        // Assert
        verify(mockResponse).json(eq(403), argThat(obj -> {
            Map<String, Object> map = (Map<String, Object>) obj;
            return map.get("ok").equals(false) &&
                   map.get("code").equals("CSRF_TOKEN_INVALID") &&
                   ((String) map.get("error")).contains("Invalid or expired");
        }));
    }
    
    @Test
    @Order(13)
    @DisplayName("Expired CSRF token returns 403")
    void testExpiredCsrfToken() throws InterruptedException {
        // Arrange
        String shortLivedToken = CsrfService.generateToken(testSessionId, 100); // 100ms expiry
        Thread.sleep(150); // Wait for expiration
        
        when(mockRequest.method()).thenReturn("POST");
        when(mockRequest.path()).thenReturn("/api/users");
        when(mockRequest.header("X-Session-Id")).thenReturn(testSessionId);
        when(mockRequest.header("X-CSRF-Token")).thenReturn(shortLivedToken);
        
        // Act
        CsrfMiddleware.validate().accept(mockRequest, mockResponse);
        
        // Assert
        verify(mockResponse).json(eq(403), argThat(obj -> {
            Map<String, Object> map = (Map<String, Object>) obj;
            return map.get("code").equals("CSRF_TOKEN_INVALID");
        }));
    }
    
    @Test
    @Order(14)
    @DisplayName("Token for different session returns 403")
    void testTokenForDifferentSession() {
        // Arrange
        String otherSessionId = "other-session-" + System.currentTimeMillis();
        String otherToken = CsrfService.generateToken(otherSessionId);
        
        when(mockRequest.method()).thenReturn("POST");
        when(mockRequest.path()).thenReturn("/api/users");
        when(mockRequest.header("X-Session-Id")).thenReturn(testSessionId); // Different session
        when(mockRequest.header("X-CSRF-Token")).thenReturn(otherToken); // Token from other session
        
        // Act
        CsrfMiddleware.validate().accept(mockRequest, mockResponse);
        
        // Assert
        verify(mockResponse).json(eq(403), argThat(obj -> {
            Map<String, Object> map = (Map<String, Object>) obj;
            return map.get("code").equals("CSRF_TOKEN_INVALID");
        }));
        
        // Cleanup
        CsrfService.invalidateToken(otherSessionId);
    }
    
    @Test
    @Order(15)
    @DisplayName("Reused token after invalidation returns 403")
    void testReuseInvalidatedToken() {
        // Arrange
        String token = CsrfService.generateToken(testSessionId);
        CsrfService.invalidateToken(testSessionId); // Invalidate immediately
        
        when(mockRequest.method()).thenReturn("POST");
        when(mockRequest.path()).thenReturn("/api/users");
        when(mockRequest.header("X-Session-Id")).thenReturn(testSessionId);
        when(mockRequest.header("X-CSRF-Token")).thenReturn(token);
        
        // Act
        CsrfMiddleware.validate().accept(mockRequest, mockResponse);
        
        // Assert
        verify(mockResponse).json(eq(403), argThat(obj -> {
            Map<String, Object> map = (Map<String, Object>) obj;
            return map.get("code").equals("CSRF_TOKEN_INVALID");
        }));
    }
    
    // ========== Test Group 5: Valid Tokens (3 tests) ==========
    
    @Test
    @Order(16)
    @DisplayName("Valid CSRF token allows POST request to continue")
    void testValidCsrfTokenForPost() {
        // Arrange
        when(mockRequest.method()).thenReturn("POST");
        when(mockRequest.path()).thenReturn("/api/users");
        when(mockRequest.header("X-Session-Id")).thenReturn(testSessionId);
        when(mockRequest.header("X-CSRF-Token")).thenReturn(validToken);
        
        // Act
        CsrfMiddleware.validate().accept(mockRequest, mockResponse);
        
        // Assert - Should NOT return 403 response
        verify(mockResponse, never()).json(eq(403), any());
    }
    
    @Test
    @Order(17)
    @DisplayName("Valid CSRF token allows PUT request to continue")
    void testValidCsrfTokenForPut() {
        // Arrange
        when(mockRequest.method()).thenReturn("PUT");
        when(mockRequest.path()).thenReturn("/api/users/123");
        when(mockRequest.header("X-Session-Id")).thenReturn(testSessionId);
        when(mockRequest.header("X-CSRF-Token")).thenReturn(validToken);
        
        // Act
        CsrfMiddleware.validate().accept(mockRequest, mockResponse);
        
        // Assert
        verify(mockResponse, never()).json(eq(403), any());
    }
    
    @Test
    @Order(18)
    @DisplayName("Valid CSRF token allows DELETE request to continue")
    void testValidCsrfTokenForDelete() {
        // Arrange
        when(mockRequest.method()).thenReturn("DELETE");
        when(mockRequest.path()).thenReturn("/api/users/123");
        when(mockRequest.header("X-Session-Id")).thenReturn(testSessionId);
        when(mockRequest.header("X-CSRF-Token")).thenReturn(validToken);
        
        // Act
        CsrfMiddleware.validate().accept(mockRequest, mockResponse);
        
        // Assert
        verify(mockResponse, never()).json(eq(403), any());
    }
    
    // ========== Test Group 6: Excluded Paths (4 tests) ==========
    
    @Test
    @Order(19)
    @DisplayName("Login endpoint bypasses CSRF validation")
    void testLoginEndpointExcluded() {
        // Arrange
        when(mockRequest.method()).thenReturn("POST");
        when(mockRequest.path()).thenReturn("/api/auth/login");
        
        // Act
        CsrfMiddleware.validate().accept(mockRequest, mockResponse);
        
        // Assert
        verify(mockResponse, never()).json(anyInt(), any());
        verify(mockRequest, never()).header("X-CSRF-Token");
    }
    
    @Test
    @Order(20)
    @DisplayName("Register endpoint bypasses CSRF validation")
    void testRegisterEndpointExcluded() {
        // Arrange
        when(mockRequest.method()).thenReturn("POST");
        when(mockRequest.path()).thenReturn("/api/auth/register");
        
        // Act
        CsrfMiddleware.validate().accept(mockRequest, mockResponse);
        
        // Assert
        verify(mockResponse, never()).json(anyInt(), any());
    }
    
    @Test
    @Order(21)
    @DisplayName("CSRF token generation endpoint bypasses validation")
    void testCsrfTokenEndpointExcluded() {
        // Arrange
        when(mockRequest.method()).thenReturn("GET");
        when(mockRequest.path()).thenReturn("/api/csrf-token");
        
        // Act
        CsrfMiddleware.validate().accept(mockRequest, mockResponse);
        
        // Assert
        verify(mockResponse, never()).json(anyInt(), any());
    }
    
    @Test
    @Order(22)
    @DisplayName("Sub-paths of excluded paths are also excluded")
    void testSubPathsExcluded() {
        // Arrange
        when(mockRequest.method()).thenReturn("POST");
        when(mockRequest.path()).thenReturn("/api/auth/login/oauth"); // Sub-path
        
        // Act
        CsrfMiddleware.validate().accept(mockRequest, mockResponse);
        
        // Assert
        verify(mockResponse, never()).json(anyInt(), any());
    }
    
    // ========== Test Group 7: Edge Cases (2 tests) ==========
    
    @Test
    @Order(23)
    @DisplayName("Null method returns no validation")
    void testNullMethod() {
        // Arrange
        when(mockRequest.method()).thenReturn(null);
        when(mockRequest.path()).thenReturn("/api/users");
        
        // Act
        CsrfMiddleware.validate().accept(mockRequest, mockResponse);
        
        // Assert
        verify(mockResponse, never()).json(anyInt(), any());
    }
    
    @Test
    @Order(24)
    @DisplayName("Null path does not cause exception")
    void testNullPath() {
        // Arrange
        when(mockRequest.method()).thenReturn("POST");
        when(mockRequest.path()).thenReturn(null);
        when(mockRequest.header("X-Session-Id")).thenReturn(testSessionId);
        when(mockRequest.header("X-CSRF-Token")).thenReturn(validToken);
        
        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> {
            CsrfMiddleware.validate().accept(mockRequest, mockResponse);
        });
    }
}
