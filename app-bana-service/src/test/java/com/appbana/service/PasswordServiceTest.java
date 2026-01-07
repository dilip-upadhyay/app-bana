package com.appbana.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PasswordService
 * Tests all scenarios from Story 1.1: Password Field Security
 */
@DisplayName("PasswordService Tests")
public class PasswordServiceTest {

    // ==================== Story 1.1.1: Password Hashing ====================

    @Test
    @DisplayName("1.1.1 - Hash password with BCrypt")
    public void testHashPassword() {
        String plainPassword = "MySecurePass123";
        String hash = PasswordService.hashPassword(plainPassword);

        // Verify hash is generated
        assertNotNull(hash, "Hash should not be null");
        assertFalse(hash.isEmpty(), "Hash should not be empty");

        // Verify BCrypt format: starts with $2a$10$ or $2a$12$
        assertTrue(hash.startsWith("$2a$"), "Hash should start with $2a$ (BCrypt identifier)");

        // Verify hash does NOT contain plain password
        assertFalse(hash.contains(plainPassword), "Hash should not contain plain-text password");

        // Verify hash length (BCrypt hashes are always 60 characters)
        assertEquals(60, hash.length(), "BCrypt hash should be 60 characters");
    }

    @Test
    @DisplayName("1.1.1 - Plain password never stored in database")
    public void testPlainPasswordNotInHash() {
        String plainPassword = "MySecurePass123";
        String hash = PasswordService.hashPassword(plainPassword);

        // The hash should be cryptographically transformed
        assertNotEquals(plainPassword, hash, "Hash must not equal plain password");
        assertFalse(hash.toLowerCase().contains("mysecurepass"), 
                    "Hash should not contain any part of the plain password");
    }

    @Test
    @DisplayName("1.1.1 - Each hash is unique (salt verification)")
    public void testUniqueSalts() {
        String password = "TestPassword123";
        String hash1 = PasswordService.hashPassword(password);
        String hash2 = PasswordService.hashPassword(password);

        // BCrypt generates unique salt each time
        assertNotEquals(hash1, hash2, "Two hashes of the same password should differ (unique salts)");

        // Both should still verify correctly
        assertTrue(PasswordService.verifyPassword(password, hash1));
        assertTrue(PasswordService.verifyPassword(password, hash2));
    }

    // ==================== Story 1.1.2: Password Verification ====================

    @Test
    @DisplayName("1.1.2 - Verify correct password")
    public void testVerifyCorrectPassword() {
        String plainPassword = "Test123";
        String hash = PasswordService.hashPassword(plainPassword);

        boolean result = PasswordService.verifyPassword(plainPassword, hash);
        assertTrue(result, "Correct password should verify successfully");
    }

    @Test
    @DisplayName("1.1.2 - Reject incorrect password")
    public void testVerifyIncorrectPassword() {
        String correctPassword = "Test123";
        String wrongPassword = "WrongPass";
        String hash = PasswordService.hashPassword(correctPassword);

        boolean result = PasswordService.verifyPassword(wrongPassword, hash);
        assertFalse(result, "Incorrect password should fail verification");
    }

    @Test
    @DisplayName("1.1.2 - Handle case-sensitive passwords")
    public void testCaseSensitivePasswords() {
        String password = "Test123";
        String hash = PasswordService.hashPassword(password);

        // Different case should fail
        assertFalse(PasswordService.verifyPassword("test123", hash), "Lowercase should fail");
        assertFalse(PasswordService.verifyPassword("TEST123", hash), "Uppercase should fail");
        assertTrue(PasswordService.verifyPassword("Test123", hash), "Exact case should succeed");
    }

    // ==================== Story 1.1.4: Password Strength Validation ====================

    @Test
    @DisplayName("1.1.4 - Reject weak password (too short)")
    public void testWeakPasswordTooShort() {
        assertFalse(PasswordService.isPasswordStrong("weak"), 
                    "Password shorter than 8 chars should be rejected");
        assertFalse(PasswordService.isPasswordStrong("Test1!"), 
                    "Password with 6 chars should be rejected");
    }

    @Test
    @DisplayName("1.1.4 - Reject password without uppercase")
    public void testPasswordWithoutUppercase() {
        assertFalse(PasswordService.isPasswordStrong("test1234!"), 
                    "Password without uppercase letter should be rejected");
    }

    @Test
    @DisplayName("1.1.4 - Reject password without lowercase")
    public void testPasswordWithoutLowercase() {
        assertFalse(PasswordService.isPasswordStrong("TEST1234!"), 
                    "Password without lowercase letter should be rejected");
    }

    @Test
    @DisplayName("1.1.4 - Reject password without digit")
    public void testPasswordWithoutDigit() {
        assertFalse(PasswordService.isPasswordStrong("TestPassword!"), 
                    "Password without digit should be rejected");
    }

    @Test
    @DisplayName("1.1.4 - Reject password without special character")
    public void testPasswordWithoutSpecialChar() {
        assertFalse(PasswordService.isPasswordStrong("TestPassword123"), 
                    "Password without special character should be rejected");
    }

    @Test
    @DisplayName("1.1.4 - Accept strong password")
    public void testStrongPasswordAccepted() {
        assertTrue(PasswordService.isPasswordStrong("StrongPass123!"), 
                   "Valid strong password should be accepted");
        assertTrue(PasswordService.isPasswordStrong("MySecure@Pass2024"), 
                   "Valid strong password should be accepted");
        assertTrue(PasswordService.isPasswordStrong("Complex#Pass99"), 
                   "Valid strong password should be accepted");
    }

    // ==================== Story 1.1.5: Edge Cases & Security ====================

    @Test
    @DisplayName("1.1.5 - Handle null password in hashing")
    public void testHashNullPassword() {
        assertThrows(IllegalArgumentException.class, 
                     () -> PasswordService.hashPassword(null),
                     "Null password should throw IllegalArgumentException");
    }

    @Test
    @DisplayName("1.1.5 - Handle empty password in hashing")
    public void testHashEmptyPassword() {
        assertThrows(IllegalArgumentException.class, 
                     () -> PasswordService.hashPassword(""),
                     "Empty password should throw IllegalArgumentException");
    }

    @Test
    @DisplayName("1.1.5 - Handle null password in verification")
    public void testVerifyNullPassword() {
        String hash = PasswordService.hashPassword("Test123");
        assertThrows(IllegalArgumentException.class, 
                     () -> PasswordService.verifyPassword(null, hash),
                     "Null password in verification should throw IllegalArgumentException");
    }

    @Test
    @DisplayName("1.1.5 - Handle null hash in verification")
    public void testVerifyNullHash() {
        assertThrows(IllegalArgumentException.class, 
                     () -> PasswordService.verifyPassword("Test123", null),
                     "Null hash in verification should throw IllegalArgumentException");
    }

    @Test
    @DisplayName("1.1.5 - Handle invalid hash format")
    public void testVerifyInvalidHashFormat() {
        // Invalid BCrypt hash format should return false (not throw exception)
        boolean result = PasswordService.verifyPassword("Test123", "invalid-hash-format");
        assertFalse(result, "Invalid hash format should return false");
    }

    @Test
    @DisplayName("1.1.5 - Password strength for null input")
    public void testPasswordStrengthNull() {
        assertFalse(PasswordService.isPasswordStrong(null), 
                    "Null password should fail strength check");
    }

    // ==================== Performance & Security ====================

    @Test
    @DisplayName("Performance - Hash generation takes reasonable time")
    public void testHashPerformance() {
        String password = "TestPassword123!";
        long startTime = System.currentTimeMillis();
        PasswordService.hashPassword(password);
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // BCrypt with cost factor 12 should take 100-500ms
        assertTrue(duration >= 50, "Hashing should take at least 50ms (security requirement)");
        assertTrue(duration <= 1000, "Hashing should complete within 1 second");
        
        System.out.println("Hash generation took " + duration + "ms (expected: 100-500ms)");
    }

    @Test
    @DisplayName("Security - Constant-time comparison (timing attack prevention)")
    public void testConstantTimeComparison() {
        String password = "TestPassword123!";
        String hash = PasswordService.hashPassword(password);

        // Verify with wrong password (should take similar time as correct)
        long startCorrect = System.nanoTime();
        PasswordService.verifyPassword(password, hash);
        long durationCorrect = System.nanoTime() - startCorrect;

        long startWrong = System.nanoTime();
        PasswordService.verifyPassword("WrongPassword123!", hash);
        long durationWrong = System.nanoTime() - startWrong;

        // Both should take similar time (within 50% variance)
        double ratio = (double) Math.max(durationCorrect, durationWrong) / 
                       (double) Math.min(durationCorrect, durationWrong);
        
        assertTrue(ratio < 2.0, "Verification time should be constant (timing attack prevention)");
        
        System.out.printf("Correct: %.2fms, Wrong: %.2fms, Ratio: %.2fx%n", 
                         durationCorrect / 1_000_000.0, 
                         durationWrong / 1_000_000.0, 
                         ratio);
    }

    // ==================== Integration Scenario ====================

    @Test
    @DisplayName("Integration - Complete signup flow simulation")
    public void testCompleteSignupFlow() {
        // User enters password
        String userPassword = "MySecure@Pass2024";

        // Step 1: Validate strength
        assertTrue(PasswordService.isPasswordStrong(userPassword), 
                   "Password should meet strength requirements");

        // Step 2: Hash before storing
        String passwordHash = PasswordService.hashPassword(userPassword);
        assertNotNull(passwordHash);
        assertTrue(passwordHash.startsWith("$2a$"));

        // Simulate database storage (only hash is stored)
        String storedHash = passwordHash; // This would be stored in DB

        // Step 3: Simulate login - verify password
        String loginAttempt = "MySecure@Pass2024";
        boolean authenticated = PasswordService.verifyPassword(loginAttempt, storedHash);
        assertTrue(authenticated, "User should be authenticated with correct password");

        // Step 4: Reject wrong password
        String wrongLoginAttempt = "WrongPassword!";
        boolean failedAuth = PasswordService.verifyPassword(wrongLoginAttempt, storedHash);
        assertFalse(failedAuth, "User should NOT be authenticated with wrong password");
    }
}
