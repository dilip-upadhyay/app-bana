package com.appbana.test;

import com.appbana.model.User;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Test fixtures for entity form binding tests.
 * Provides consistent test data across all test files.
 * 
 * @see ENTITY_FORM_BINDING_TEST_PLAN.md for complete test plan
 */
public class TestFixtures {
    
    // Valid password for testing
    public static final String VALID_PASSWORD = "SecurePass123";
    public static final String WEAK_PASSWORD = "123";
    
    // BCrypt hash of "SecurePass123" with salt $2a$10$N9qo8uLOickgx2ZMRZoMye
    public static final String BCRYPT_HASH_OF_VALID_PASSWORD = 
        "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
    
    /**
     * Returns valid user data for successful signup.
     * All fields are valid and should pass validation.
     */
    public static Map<String, Object> validUserData() {
        Map<String, Object> data = new HashMap<>();
        data.put("firstName", "John");
        data.put("lastName", "Doe");
        data.put("email", "john@example.com");
        data.put("phone", "+1234567890");
        data.put("password", VALID_PASSWORD);
        return data;
    }
    
    /**
     * Returns user data with invalid email format.
     * Email is missing @ symbol.
     */
    public static Map<String, Object> invalidEmailData() {
        Map<String, Object> data = validUserData();
        data.put("email", "invalid-email");  // Missing @
        return data;
    }
    
    /**
     * Returns user data with SQL injection attempt.
     * Tests for SQL injection protection.
     */
    public static Map<String, Object> sqlInjectionData() {
        Map<String, Object> data = validUserData();
        data.put("firstName", "Robert'; DROP TABLE user;--");
        return data;
    }
    
    /**
     * Returns user data with XSS attempt.
     * Tests for XSS protection.
     */
    public static Map<String, Object> xssData() {
        Map<String, Object> data = validUserData();
        data.put("email", "<script>alert('XSS')</script>@example.com");
        return data;
    }
    
    /**
     * Returns user data with duplicate email.
     * Tests unique email constraint validation.
     */
    public static Map<String, Object> duplicateEmailData() {
        Map<String, Object> data = validUserData();
        data.put("email", "existing@example.com");  // Already in DB
        return data;
    }
    
    /**
     * Returns user data with weak password.
     * Tests password strength validation.
     */
    public static Map<String, Object> weakPasswordData() {
        Map<String, Object> data = validUserData();
        data.put("password", WEAK_PASSWORD);
        return data;
    }
    
    /**
     * Creates a test user entity with hashed password.
     * Use for testing user retrieval and authentication.
     */
    public static User createTestUser() {
        User user = new User();
        user.setId(123L);
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setEmail("john@example.com");
        user.setPhone("+1234567890");
        user.setPasswordHash(BCRYPT_HASH_OF_VALID_PASSWORD);
        user.setCreatedAt(Instant.now());
        return user;
    }
    
    /**
     * Creates a test user with custom email.
     * Useful for testing multiple users.
     */
    public static User createTestUser(String email) {
        User user = createTestUser();
        user.setEmail(email);
        return user;
    }
}
