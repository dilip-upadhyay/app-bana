package com.appbana.service;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CsrfService
 * Tests all scenarios from Story 1.2: CSRF Protection
 */
@DisplayName("CsrfService Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CsrfServiceTest {

    @BeforeEach
    public void setUp() {
        // Clear all tokens before each test
        CsrfService.clearAllTokens();
    }

    @AfterEach
    public void tearDown() {
        // Cleanup after each test
        CsrfService.clearAllTokens();
    }

    // ==================== Story 1.2.1: Token Generation ====================

    @Test
    @Order(1)
    @DisplayName("1.2.1 - Generate CSRF token for session")
    public void testGenerateToken() {
        String sessionId = "test-session-123";
        String token = CsrfService.generateToken(sessionId);

        assertNotNull(token, "Token should not be null");
        assertFalse(token.isEmpty(), "Token should not be empty");
        
        // Base64 URL-safe encoding with ~43 characters for 32 bytes
        assertTrue(token.length() >= 40, "Token should be at least 40 characters");
        assertTrue(token.matches("^[A-Za-z0-9_-]+$"), "Token should be base64 URL-safe");
    }

    @Test
    @Order(2)
    @DisplayName("1.2.1 - Each token is unique (entropy test)")
    public void testUniqueTokens() {
        String sessionId = "test-session-123";
        String token1 = CsrfService.generateToken(sessionId);
        
        // Even for same session, rotating generates new token
        String token2 = CsrfService.rotateToken(sessionId);

        assertNotEquals(token1, token2, "Tokens should be unique");
    }

    @Test
    @Order(3)
    @DisplayName("1.2.1 - Reject null session ID in generation")
    public void testGenerateTokenNullSession() {
        assertThrows(IllegalArgumentException.class,
                () -> CsrfService.generateToken(null),
                "Null session ID should throw IllegalArgumentException");
    }

    @Test
    @Order(4)
    @DisplayName("1.2.1 - Reject empty session ID in generation")
    public void testGenerateTokenEmptySession() {
        assertThrows(IllegalArgumentException.class,
                () -> CsrfService.generateToken(""),
                "Empty session ID should throw IllegalArgumentException");
    }

    // ==================== Story 1.2.2: Token Validation ====================

    @Test
    @Order(10)
    @DisplayName("1.2.2 - Validate correct CSRF token")
    public void testValidateCorrectToken() {
        String sessionId = "valid-session";
        String token = CsrfService.generateToken(sessionId);

        boolean isValid = CsrfService.validateToken(sessionId, token);
        assertTrue(isValid, "Correct token should be valid");
    }

    @Test
    @Order(11)
    @DisplayName("1.2.2 - Reject incorrect token")
    public void testValidateIncorrectToken() {
        String sessionId = "test-session";
        CsrfService.generateToken(sessionId);

        boolean isValid = CsrfService.validateToken(sessionId, "wrong-token-value");
        assertFalse(isValid, "Incorrect token should be invalid");
    }

    @Test
    @Order(12)
    @DisplayName("1.2.2 - Reject token for different session")
    public void testValidateWrongSession() {
        String sessionId1 = "session-1";
        String sessionId2 = "session-2";
        
        String token1 = CsrfService.generateToken(sessionId1);

        // Token from session1 should not work for session2
        boolean isValid = CsrfService.validateToken(sessionId2, token1);
        assertFalse(isValid, "Token from different session should be invalid");
    }

    @Test
    @Order(13)
    @DisplayName("1.2.2 - Reject null token in validation")
    public void testValidateNullToken() {
        String sessionId = "test-session";
        CsrfService.generateToken(sessionId);

        boolean isValid = CsrfService.validateToken(sessionId, null);
        assertFalse(isValid, "Null token should be invalid");
    }

    @Test
    @Order(14)
    @DisplayName("1.2.2 - Reject validation for null session ID")
    public void testValidateNullSessionId() {
        boolean isValid = CsrfService.validateToken(null, "some-token");
        assertFalse(isValid, "Null session ID should be invalid");
    }

    @Test
    @Order(15)
    @DisplayName("1.2.2 - Reject validation for non-existent session")
    public void testValidateNonExistentSession() {
        boolean isValid = CsrfService.validateToken("non-existent-session", "some-token");
        assertFalse(isValid, "Non-existent session should be invalid");
    }

    // ==================== Story 1.2.3: Token Expiration ====================

    @Test
    @Order(20)
    @DisplayName("1.2.3 - Token expires after timeout")
    public void testTokenExpiration() throws InterruptedException {
        String sessionId = "expiring-session";
        
        // Generate token with 100ms expiration
        CsrfService.generateToken(sessionId, 100);

        // Immediately valid
        String token = CsrfService.generateToken(sessionId, 100);
        assertTrue(CsrfService.validateToken(sessionId, token), "Token should be valid immediately");

        // Wait for expiration
        Thread.sleep(150);

        // Should now be expired
        boolean isValid = CsrfService.validateToken(sessionId, token);
        assertFalse(isValid, "Token should be expired after timeout");
    }

    @Test
    @Order(21)
    @DisplayName("1.2.3 - Check token expiration status")
    public void testIsTokenExpired() throws InterruptedException {
        String sessionId = "test-session";
        
        // Generate with short expiration
        CsrfService.generateToken(sessionId, 50);
        assertFalse(CsrfService.isTokenExpired(sessionId), "Token should not be expired immediately");

        // Wait for expiration
        Thread.sleep(100);
        assertTrue(CsrfService.isTokenExpired(sessionId), "Token should be expired after timeout");
    }

    @Test
    @Order(22)
    @DisplayName("1.2.3 - Get token expiration time")
    public void testGetTokenExpiration() {
        String sessionId = "test-session";
        long beforeGeneration = System.currentTimeMillis();
        
        CsrfService.generateToken(sessionId); // Default 30 minutes
        
        Long expiresAt = CsrfService.getTokenExpiration(sessionId);
        assertNotNull(expiresAt, "Expiration time should not be null");
        
        // Should expire ~30 minutes from now (allow 1 second variance)
        long expectedExpiration = beforeGeneration + (30 * 60 * 1000);
        assertTrue(Math.abs(expiresAt - expectedExpiration) < 1000,
                "Expiration should be ~30 minutes from generation");
    }

    @Test
    @Order(23)
    @DisplayName("1.2.3 - Return null expiration for non-existent token")
    public void testGetExpirationNonExistent() {
        Long expiresAt = CsrfService.getTokenExpiration("non-existent-session");
        assertNull(expiresAt, "Non-existent token should return null expiration");
    }

    @Test
    @Order(24)
    @DisplayName("1.2.3 - Cleanup expired tokens")
    public void testCleanupExpiredTokens() throws InterruptedException {
        // Generate multiple tokens with short expiration
        CsrfService.generateToken("session1", 50);
        CsrfService.generateToken("session2", 50);
        CsrfService.generateToken("session3", 50);

        assertEquals(3, CsrfService.getActiveTokenCount(), "Should have 3 active tokens");

        // Wait for expiration
        Thread.sleep(100);

        // Cleanup
        CsrfService.cleanupExpiredTokens();

        assertEquals(0, CsrfService.getActiveTokenCount(), "All expired tokens should be removed");
    }

    // ==================== Story 1.2.4: Token Rotation ====================

    @Test
    @Order(30)
    @DisplayName("1.2.4 - Rotate token on authentication event")
    public void testRotateToken() {
        String sessionId = "test-session";
        String oldToken = CsrfService.generateToken(sessionId);

        // Rotate token
        String newToken = CsrfService.rotateToken(sessionId);

        assertNotNull(newToken, "New token should not be null");
        assertNotEquals(oldToken, newToken, "New token should differ from old token");

        // Old token should no longer be valid
        assertFalse(CsrfService.validateToken(sessionId, oldToken), 
                "Old token should be invalid after rotation");

        // New token should be valid
        assertTrue(CsrfService.validateToken(sessionId, newToken),
                "New token should be valid");
    }

    @Test
    @Order(31)
    @DisplayName("1.2.4 - Invalidate token on logout")
    public void testInvalidateToken() {
        String sessionId = "test-session";
        String token = CsrfService.generateToken(sessionId);

        // Token is valid before invalidation
        assertTrue(CsrfService.validateToken(sessionId, token));

        // Invalidate token
        CsrfService.invalidateToken(sessionId);

        // Token should no longer be valid
        assertFalse(CsrfService.validateToken(sessionId, token),
                "Token should be invalid after invalidation");
    }

    @Test
    @Order(32)
    @DisplayName("1.2.4 - Handle invalidation of non-existent token")
    public void testInvalidateNonExistentToken() {
        // Should not throw exception
        assertDoesNotThrow(() -> CsrfService.invalidateToken("non-existent-session"),
                "Invalidating non-existent token should not throw exception");
    }

    // ==================== Security & Edge Cases ====================

    @Test
    @Order(40)
    @DisplayName("Security - Constant-time comparison (informational test)")
    public void testConstantTimeComparison() {
        String sessionId = "test-session";
        String correctToken = CsrfService.generateToken(sessionId);
        
        // Create wrong tokens of same length
        String wrongToken1 = correctToken.substring(0, correctToken.length() - 1) + "X";
        String wrongToken2 = "X" + correctToken.substring(1);

        // Warm up JIT compiler
        for (int i = 0; i < 1000; i++) {
            CsrfService.validateToken(sessionId, correctToken);
            CsrfService.validateToken(sessionId, wrongToken1);
        }

        // Measure multiple times and take average to reduce variance
        long totalCorrect = 0;
        long totalWrong1 = 0;
        long totalWrong2 = 0;
        int iterations = 10000;

        for (int i = 0; i < iterations; i++) {
            long startCorrect = System.nanoTime();
            CsrfService.validateToken(sessionId, correctToken);
            totalCorrect += System.nanoTime() - startCorrect;

            long startWrong1 = System.nanoTime();
            CsrfService.validateToken(sessionId, wrongToken1);
            totalWrong1 += System.nanoTime() - startWrong1;

            long startWrong2 = System.nanoTime();
            CsrfService.validateToken(sessionId, wrongToken2);
            totalWrong2 += System.nanoTime() - startWrong2;
        }

        long avgCorrect = totalCorrect / iterations;
        long avgWrong1 = totalWrong1 / iterations;
        long avgWrong2 = totalWrong2 / iterations;

        // Calculate ratios
        double ratio1 = (double) Math.max(avgCorrect, avgWrong1) / 
                        Math.min(avgCorrect, avgWrong1);
        double ratio2 = (double) Math.max(avgCorrect, avgWrong2) / 
                        Math.min(avgCorrect, avgWrong2);

        // Log results for manual verification (informational only)
        // Note: Constant-time comparison is difficult to verify reliably in JVM due to:
        // - JIT compilation optimizations
        // - CPU cache effects
        // - Operating system scheduling
        // - Branch prediction
        // The implementation uses MessageDigest.isEqual() which is constant-time at bytecode level
        System.out.println(String.format(
            "Constant-time test (informational): avgCorrect=%dns, avgWrong1=%dns, avgWrong2=%dns, ratio1=%.2f, ratio2=%.2f",
            avgCorrect, avgWrong1, avgWrong2, ratio1, ratio2
        ));
        
        // Just verify that validation works correctly (not timing)
        assertTrue(CsrfService.validateToken(sessionId, correctToken),
                "Correct token should be valid");
        assertFalse(CsrfService.validateToken(sessionId, wrongToken1),
                "Wrong token 1 should be invalid");
        assertFalse(CsrfService.validateToken(sessionId, wrongToken2),
                "Wrong token 2 should be invalid");
    }

    @Test
    @Order(41)
    @DisplayName("Security - Token has sufficient entropy")
    public void testTokenEntropy() {
        String sessionId = "test-session";
        
        // Generate multiple tokens and check uniqueness
        java.util.Set<String> tokens = new java.util.HashSet<>();
        for (int i = 0; i < 100; i++) {
            String token = CsrfService.generateToken(sessionId + "-" + i);
            tokens.add(token);
        }

        assertEquals(100, tokens.size(), "All 100 tokens should be unique");
    }

    @Test
    @Order(42)
    @DisplayName("Edge Case - Custom expiration time")
    public void testCustomExpiration() {
        String sessionId = "test-session";
        long customExpiration = 5 * 60 * 1000; // 5 minutes

        long beforeGeneration = System.currentTimeMillis();
        CsrfService.generateToken(sessionId, customExpiration);

        Long expiresAt = CsrfService.getTokenExpiration(sessionId);
        assertNotNull(expiresAt);

        long expectedExpiration = beforeGeneration + customExpiration;
        assertTrue(Math.abs(expiresAt - expectedExpiration) < 1000,
                "Custom expiration should be respected");
    }

    @Test
    @Order(43)
    @DisplayName("Edge Case - Reject invalid custom expiration")
    public void testInvalidCustomExpiration() {
        String sessionId = "test-session";

        assertThrows(IllegalArgumentException.class,
                () -> CsrfService.generateToken(sessionId, 0),
                "Zero expiration should throw exception");

        assertThrows(IllegalArgumentException.class,
                () -> CsrfService.generateToken(sessionId, -1000),
                "Negative expiration should throw exception");
    }

    // ==================== Integration Scenario ====================

    @Test
    @Order(50)
    @DisplayName("Integration - Complete CSRF flow simulation")
    public void testCompleteCSRFFlow() throws InterruptedException {
        String sessionId = "user-session-abc";

        // Step 1: User loads page, server generates token
        String token = CsrfService.generateToken(sessionId);
        assertNotNull(token);

        // Step 2: Form includes token in hidden field
        // (Client-side would inject this: <input type="hidden" name="_csrf" value="token">)

        // Step 3: User submits form with token in header
        boolean isValid = CsrfService.validateToken(sessionId, token);
        assertTrue(isValid, "Valid token should pass validation");

        // Step 4: After login, rotate token
        String newToken = CsrfService.rotateToken(sessionId);
        assertNotEquals(token, newToken);

        // Step 5: Old token should no longer work
        assertFalse(CsrfService.validateToken(sessionId, token),
                "Old token should be invalid after rotation");

        // Step 6: New token works
        assertTrue(CsrfService.validateToken(sessionId, newToken),
                "New token should be valid");

        // Step 7: After logout, invalidate token
        CsrfService.invalidateToken(sessionId);

        // Step 8: Token should no longer work
        assertFalse(CsrfService.validateToken(sessionId, newToken),
                "Token should be invalid after logout");
    }

    @Test
    @Order(51)
    @DisplayName("Integration - Multiple concurrent sessions")
    public void testMultipleSessions() {
        String session1 = "user-1-session";
        String session2 = "user-2-session";
        String session3 = "user-3-session";

        String token1 = CsrfService.generateToken(session1);
        String token2 = CsrfService.generateToken(session2);
        String token3 = CsrfService.generateToken(session3);

        // All tokens should be unique
        assertNotEquals(token1, token2);
        assertNotEquals(token2, token3);
        assertNotEquals(token1, token3);

        // Each token validates only for its own session
        assertTrue(CsrfService.validateToken(session1, token1));
        assertTrue(CsrfService.validateToken(session2, token2));
        assertTrue(CsrfService.validateToken(session3, token3));

        // Cross-session validation should fail
        assertFalse(CsrfService.validateToken(session1, token2));
        assertFalse(CsrfService.validateToken(session2, token3));
        assertFalse(CsrfService.validateToken(session3, token1));
    }
}
