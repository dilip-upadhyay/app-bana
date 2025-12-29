# Entity Form Binding - Test Implementation Plan

**Created:** December 30, 2025  
**Status:** 🔴 ACTIVE - Copy-Paste Ready Test Code  
**Related:** [Architecture](./ENTITY_FORM_BINDING_ARCHITECTURE.md) | [Stories](./ENTITY_FORM_BINDING_STORIES.md)

---

## 📋 Quick Reference

| Test Suite | Location | Command | Coverage Target |
|------------|----------|---------|-----------------|
| **Backend Unit** | `app-bana-service/src/test/java/` | `mvn test` | >80% |
| **Frontend Unit** | `app-bana-ui/src/components/` | `npm test` | >80% |
| **Integration** | `app-bana-service/src/test/java/integration/` | `mvn verify` | >70% |
| **E2E** | `app-bana-ui/tests/e2e/` | `npm run test:e2e` | Critical paths |
| **Security** | `app-bana-service/src/test/java/security/` | `mvn test -Psecurity` | 100% |
| **Accessibility** | `app-bana-ui/tests/a11y/` | `npm run test:a11y` | axe-core: 0 violations |

---

## 🧪 Test Data Fixtures

### `test-fixtures.ts` (Frontend)

```typescript
// app-bana-ui/src/test/fixtures/test-fixtures.ts

export const VALID_USER = {
  firstName: "John",
  lastName: "Doe",
  email: "john@example.com",
  phone: "+1234567890",
  password: "SecurePass123",
  confirmPassword: "SecurePass123"
};

export const INVALID_EMAIL_USER = {
  ...VALID_USER,
  email: "invalid-email"  // Missing @ symbol
};

export const WEAK_PASSWORD_USER = {
  ...VALID_USER,
  password: "123"  // Too short
};

export const MISMATCHED_PASSWORD_USER = {
  ...VALID_USER,
  password: "SecurePass123",
  confirmPassword: "DifferentPass456"
};

export const DUPLICATE_EMAIL_USER = {
  ...VALID_USER,
  email: "existing@example.com"  // Already in DB
};

export const SQL_INJECTION_USER = {
  ...VALID_USER,
  firstName: "Robert'; DROP TABLE user;--"
};

export const XSS_USER = {
  ...VALID_USER,
  email: "<script>alert('XSS')</script>@example.com"
};

export const MOCK_BCRYPT_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

export const MOCK_CSRF_TOKEN = "a1b2c3d4e5f6g7h8i9j0";

export const MOCK_API_SUCCESS_RESPONSE = {
  ok: true,
  data: {
    id: 123,
    firstName: "John",
    lastName: "Doe",
    email: "john@example.com",
    createdAt: "2025-12-30T10:00:00Z"
  }
};

export const MOCK_API_VALIDATION_ERROR = {
  ok: false,
  validationErrors: {
    email: "Email already exists",
    password: "Password must be at least 8 characters"
  }
};

export const MOCK_API_RATE_LIMIT_ERROR = {
  ok: false,
  error: "Too many signup attempts. Try again in 1 minute.",
  retryAfter: 60
};
```

### `TestFixtures.java` (Backend)

```java
// app-bana-service/src/test/java/com/appbana/test/TestFixtures.java

package com.appbana.test;

import com.appbana.model.User;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class TestFixtures {
    
    public static final String VALID_PASSWORD = "SecurePass123";
    public static final String WEAK_PASSWORD = "123";
    public static final String BCRYPT_HASH_OF_VALID_PASSWORD = 
        "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
    
    public static Map<String, Object> validUserData() {
        Map<String, Object> data = new HashMap<>();
        data.put("firstName", "John");
        data.put("lastName", "Doe");
        data.put("email", "john@example.com");
        data.put("phone", "+1234567890");
        data.put("password", VALID_PASSWORD);
        return data;
    }
    
    public static Map<String, Object> invalidEmailData() {
        Map<String, Object> data = validUserData();
        data.put("email", "invalid-email");
        return data;
    }
    
    public static Map<String, Object> sqlInjectionData() {
        Map<String, Object> data = validUserData();
        data.put("firstName", "Robert'; DROP TABLE user;--");
        return data;
    }
    
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
}
```

---

## 🔴 Sprint 1: Security & Core Functionality

### Story 1.1: Password Security Tests

#### **Backend: `PasswordServiceTest.java`**

```java
// app-bana-service/src/test/java/com/appbana/service/PasswordServiceTest.java

package com.appbana.service;

import com.appbana.test.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;
import org.mindrot.jbcrypt.BCrypt;

@DisplayName("Password Service - Security Tests")
class PasswordServiceTest {
    
    private PasswordService passwordService;
    
    @BeforeEach
    void setUp() {
        passwordService = new PasswordService();
    }
    
    @Test
    @DisplayName("Scenario 1.1.1: Password is hashed with BCrypt")
    void testPasswordHashing() {
        // Given
        String plainPassword = TestFixtures.VALID_PASSWORD;
        
        // When
        String hashedPassword = passwordService.hashPassword(plainPassword);
        
        // Then
        assertNotNull(hashedPassword);
        assertTrue(hashedPassword.startsWith("$2a$10$"), 
            "Hash should start with BCrypt identifier");
        assertNotEquals(plainPassword, hashedPassword, 
            "Hash should not equal plain text");
        assertEquals(60, hashedPassword.length(), 
            "BCrypt hash should be 60 characters");
        assertTrue(BCrypt.checkpw(plainPassword, hashedPassword), 
            "Password should verify against hash");
    }
    
    @Test
    @DisplayName("Scenario 1.1.1: Same password produces different hashes (salt)")
    void testPasswordSalting() {
        // Given
        String password = TestFixtures.VALID_PASSWORD;
        
        // When
        String hash1 = passwordService.hashPassword(password);
        String hash2 = passwordService.hashPassword(password);
        
        // Then
        assertNotEquals(hash1, hash2, 
            "Same password should produce different hashes due to salt");
        assertTrue(BCrypt.checkpw(password, hash1));
        assertTrue(BCrypt.checkpw(password, hash2));
    }
    
    @Test
    @DisplayName("Scenario 1.1.4: Weak password validation fails")
    void testWeakPasswordValidation() {
        // Given
        String weakPassword = TestFixtures.WEAK_PASSWORD;
        
        // When
        ValidationResult result = passwordService.validatePasswordStrength(weakPassword);
        
        // Then
        assertFalse(result.isValid(), "Weak password should fail validation");
        assertEquals("Password must be 8+ chars with letter and number", 
            result.getErrorMessage());
    }
    
    @Test
    @DisplayName("Scenario 1.1.4: Strong password validation passes")
    void testStrongPasswordValidation() {
        // Given
        String strongPassword = "StrongPass123";
        
        // When
        ValidationResult result = passwordService.validatePasswordStrength(strongPassword);
        
        // Then
        assertTrue(result.isValid(), "Strong password should pass validation");
        assertNull(result.getErrorMessage());
    }
    
    @Test
    @DisplayName("Password verification with correct password")
    void testPasswordVerificationSuccess() {
        // Given
        String password = TestFixtures.VALID_PASSWORD;
        String hash = passwordService.hashPassword(password);
        
        // When
        boolean verified = passwordService.verifyPassword(password, hash);
        
        // Then
        assertTrue(verified, "Correct password should verify");
    }
    
    @Test
    @DisplayName("Password verification with incorrect password")
    void testPasswordVerificationFailure() {
        // Given
        String correctPassword = TestFixtures.VALID_PASSWORD;
        String wrongPassword = "WrongPassword123";
        String hash = passwordService.hashPassword(correctPassword);
        
        // When
        boolean verified = passwordService.verifyPassword(wrongPassword, hash);
        
        // Then
        assertFalse(verified, "Wrong password should not verify");
    }
}
```

#### **Backend: `UserApiTest.java` (Integration)**

```java
// app-bana-service/src/test/java/com/appbana/integration/UserApiTest.java

package com.appbana.integration;

import com.appbana.test.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.ResultSet;

@DisplayName("User API - Password Security Integration Tests")
class UserApiTest {
    
    private static final String BASE_URL = "http://localhost:8080";
    private HttpClient httpClient;
    private ObjectMapper objectMapper;
    private Connection dbConnection;
    
    @BeforeEach
    void setUp() throws Exception {
        httpClient = HttpClient.newHttpClient();
        objectMapper = new ObjectMapper();
        dbConnection = TestDatabase.getConnection();
        TestDatabase.cleanDatabase(dbConnection);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        TestDatabase.cleanDatabase(dbConnection);
        dbConnection.close();
    }
    
    @Test
    @DisplayName("Scenario 1.1.1: POST /api/user stores hashed password")
    void testUserCreationWithHashedPassword() throws Exception {
        // Given
        var userData = TestFixtures.validUserData();
        String requestBody = objectMapper.writeValueAsString(userData);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/user"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
        
        // When
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        // Then
        assertEquals(201, response.statusCode(), "Should return 201 Created");
        
        // Verify in database
        var stmt = dbConnection.prepareStatement(
            "SELECT passwordHash FROM user WHERE email = ?");
        stmt.setString(1, "john@example.com");
        ResultSet rs = stmt.executeQuery();
        
        assertTrue(rs.next(), "User should exist in database");
        String storedHash = rs.getString("passwordHash");
        
        assertTrue(storedHash.startsWith("$2a$10$"), 
            "Stored password should be BCrypt hash");
        assertNotEquals(TestFixtures.VALID_PASSWORD, storedHash, 
            "Plain password should NOT be stored");
        
        // Verify response does NOT include passwordHash
        Map<String, Object> responseData = objectMapper.readValue(
            response.body(), Map.class);
        assertFalse(responseData.containsKey("passwordHash"), 
            "Response should NOT include passwordHash");
        assertFalse(responseData.containsKey("password"), 
            "Response should NOT include password");
    }
    
    @Test
    @DisplayName("Scenario 1.1.2: Field mapping transforms password to passwordHash")
    void testFieldMapping() throws Exception {
        // Given
        var userData = TestFixtures.validUserData();
        userData.put("confirmPassword", "SecurePass123");  // Should be excluded
        String requestBody = objectMapper.writeValueAsString(userData);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/user"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
        
        // When
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        // Then
        assertEquals(201, response.statusCode());
        
        // Verify confirmPassword NOT in database
        var stmt = dbConnection.prepareStatement(
            "SELECT * FROM user WHERE email = ?");
        stmt.setString(1, "john@example.com");
        ResultSet rs = stmt.executeQuery();
        
        assertTrue(rs.next());
        var metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        
        for (int i = 1; i <= columnCount; i++) {
            String columnName = metaData.getColumnName(i);
            assertNotEquals("confirmPassword", columnName, 
                "confirmPassword should NOT exist as column");
            assertNotEquals("password", columnName, 
                "password should NOT exist as column");
        }
        
        assertTrue(rs.getString("passwordHash") != null, 
            "passwordHash should exist");
    }
    
    @Test
    @DisplayName("Scenario 1.1.4: Weak password rejected by validation")
    void testWeakPasswordRejection() throws Exception {
        // Given
        var userData = TestFixtures.validUserData();
        userData.put("password", "123");  // Weak password
        String requestBody = objectMapper.writeValueAsString(userData);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/user"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
        
        // When
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        // Then
        assertEquals(400, response.statusCode(), "Should return 400 Bad Request");
        
        Map<String, Object> errorResponse = objectMapper.readValue(
            response.body(), Map.class);
        
        assertFalse((Boolean) errorResponse.get("ok"));
        Map<String, String> validationErrors = 
            (Map<String, String>) errorResponse.get("validationErrors");
        
        assertEquals("Password must be 8+ chars with letter and number", 
            validationErrors.get("password"));
    }
}
```

#### **Frontend: `FormComponent.test.ts`**

```typescript
// app-bana-ui/src/components/FormComponent.test.ts

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { FormComponent } from './FormComponent';
import { VALID_USER, WEAK_PASSWORD_USER, MISMATCHED_PASSWORD_USER } from '../test/fixtures/test-fixtures';

describe('FormComponent - Password Security', () => {
  let component: FormComponent;
  let container: HTMLElement;
  
  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
    
    component = document.createElement('form-component') as FormComponent;
    component.fieldMapping = {
      firstName: 'firstName',
      lastName: 'lastName',
      email: 'email',
      phone: 'phone',
      password: 'passwordHash',  // Maps to hashed field
      confirmPassword: null      // Excluded from submission
    };
    component.validationRules = {
      password: {
        minLength: 8,
        pattern: '^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d@$!%*#?&]{8,}$',
        errorMessage: 'Password must be 8+ chars with letter and number'
      },
      confirmPassword: {
        matches: 'password',
        errorMessage: 'Passwords must match'
      }
    };
    
    container.appendChild(component);
  });
  
  afterEach(() => {
    container.remove();
  });
  
  it('Scenario 1.1.2: Maps password to passwordHash in form data', () => {
    // Given
    const formData = { ...VALID_USER };
    
    // When
    const mapped = component.mapFields(formData);
    
    // Then
    expect(mapped.passwordHash).toBe('SecurePass123');
    expect(mapped.password).toBeUndefined();
  });
  
  it('Scenario 1.1.3: Excludes confirmPassword from submission', () => {
    // Given
    const formData = { ...VALID_USER };
    
    // When
    const mapped = component.mapFields(formData);
    
    // Then
    expect(mapped.confirmPassword).toBeUndefined();
  });
  
  it('Scenario 1.1.4: Validates password strength', () => {
    // Given
    component.setFieldValue('password', WEAK_PASSWORD_USER.password);
    
    // When
    const result = component.validateField('password');
    
    // Then
    expect(result.isValid).toBe(false);
    expect(result.error).toBe('Password must be 8+ chars with letter and number');
  });
  
  it('Scenario 1.1.5: Validates password confirmation matching', () => {
    // Given
    component.setFieldValue('password', 'SecurePass123');
    component.setFieldValue('confirmPassword', 'DifferentPass456');
    
    // When
    const result = component.validateField('confirmPassword');
    
    // Then
    expect(result.isValid).toBe(false);
    expect(result.error).toBe('Passwords must match');
  });
  
  it('Scenario 1.1.5: Passes when passwords match', () => {
    // Given
    component.setFieldValue('password', 'SecurePass123');
    component.setFieldValue('confirmPassword', 'SecurePass123');
    
    // When
    const result = component.validateField('confirmPassword');
    
    // Then
    expect(result.isValid).toBe(true);
    expect(result.error).toBeNull();
  });
});
```

---

### Story 1.2: CSRF Protection Tests

#### **Backend: `CsrfServiceTest.java`**

```java
// app-bana-service/src/test/java/com/appbana/service/CsrfServiceTest.java

package com.appbana.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;

@DisplayName("CSRF Service Tests")
class CsrfServiceTest {
    
    private CsrfService csrfService;
    
    @BeforeEach
    void setUp() {
        csrfService = new CsrfService();
    }
    
    @Test
    @DisplayName("Scenario 1.2.1: Generates valid CSRF token")
    void testTokenGeneration() {
        // Given
        String sessionId = UUID.randomUUID().toString();
        
        // When
        String token = csrfService.generateToken(sessionId);
        
        // Then
        assertNotNull(token);
        assertEquals(64, token.length(), "Token should be 64 characters");
        assertTrue(token.matches("[A-Za-z0-9]+"), "Token should be alphanumeric");
    }
    
    @Test
    @DisplayName("Scenario 1.2.2: Same session generates same token (cached)")
    void testTokenCaching() {
        // Given
        String sessionId = UUID.randomUUID().toString();
        
        // When
        String token1 = csrfService.generateToken(sessionId);
        String token2 = csrfService.generateToken(sessionId);
        
        // Then
        assertEquals(token1, token2, "Same session should return same token");
    }
    
    @Test
    @DisplayName("Scenario 1.2.3: Valid token passes verification")
    void testTokenVerificationSuccess() {
        // Given
        String sessionId = UUID.randomUUID().toString();
        String token = csrfService.generateToken(sessionId);
        
        // When
        boolean isValid = csrfService.verifyToken(token, sessionId);
        
        // Then
        assertTrue(isValid, "Valid token should pass verification");
    }
    
    @Test
    @DisplayName("Scenario 1.2.3: Invalid token fails verification")
    void testTokenVerificationFailure() {
        // Given
        String sessionId = UUID.randomUUID().toString();
        csrfService.generateToken(sessionId);
        String fakeToken = "invalid-token-123";
        
        // When
        boolean isValid = csrfService.verifyToken(fakeToken, sessionId);
        
        // Then
        assertFalse(isValid, "Invalid token should fail verification");
    }
    
    @Test
    @DisplayName("Scenario 1.2.4: Expired token fails verification")
    void testTokenExpiration() throws InterruptedException {
        // Given
        String sessionId = UUID.randomUUID().toString();
        csrfService.setTokenTTL(1000); // 1 second TTL
        String token = csrfService.generateToken(sessionId);
        
        // When
        Thread.sleep(1100); // Wait for expiration
        boolean isValid = csrfService.verifyToken(token, sessionId);
        
        // Then
        assertFalse(isValid, "Expired token should fail verification");
    }
}
```

#### **Backend: `CsrfMiddlewareTest.java` (Integration)**

```java
// app-bana-service/src/test/java/com/appbana/integration/CsrfMiddlewareTest.java

package com.appbana.integration;

import com.appbana.test.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@DisplayName("CSRF Middleware Integration Tests")
class CsrfMiddlewareTest {
    
    private static final String BASE_URL = "http://localhost:8080";
    private HttpClient httpClient;
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        httpClient = HttpClient.newHttpClient();
        objectMapper = new ObjectMapper();
    }
    
    @Test
    @DisplayName("Scenario 1.2.1: GET /api/csrf-token returns token")
    void testCsrfTokenEndpoint() throws Exception {
        // When
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/csrf-token"))
            .GET()
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        // Then
        assertEquals(200, response.statusCode());
        
        Map<String, String> data = objectMapper.readValue(
            response.body(), Map.class);
        
        assertTrue(data.containsKey("token"));
        assertEquals(64, data.get("token").length());
    }
    
    @Test
    @DisplayName("Scenario 1.2.2: POST with valid CSRF token succeeds")
    void testValidCsrfToken() throws Exception {
        // Given - Get CSRF token
        HttpRequest tokenRequest = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/csrf-token"))
            .GET()
            .build();
        
        HttpResponse<String> tokenResponse = httpClient.send(tokenRequest, 
            HttpResponse.BodyHandlers.ofString());
        
        Map<String, String> tokenData = objectMapper.readValue(
            tokenResponse.body(), Map.class);
        String csrfToken = tokenData.get("token");
        String sessionCookie = tokenResponse.headers()
            .firstValue("Set-Cookie").orElseThrow();
        
        // When - Submit form with CSRF token
        var userData = TestFixtures.validUserData();
        String requestBody = objectMapper.writeValueAsString(userData);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/user"))
            .header("Content-Type", "application/json")
            .header("X-CSRF-Token", csrfToken)
            .header("Cookie", sessionCookie)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        // Then
        assertEquals(201, response.statusCode(), 
            "Request with valid CSRF token should succeed");
    }
    
    @Test
    @DisplayName("Scenario 1.2.3: POST without CSRF token fails with 403")
    void testMissingCsrfToken() throws Exception {
        // Given
        var userData = TestFixtures.validUserData();
        String requestBody = objectMapper.writeValueAsString(userData);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/user"))
            .header("Content-Type", "application/json")
            // No X-CSRF-Token header
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
        
        // When
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        // Then
        assertEquals(403, response.statusCode(), "Should return 403 Forbidden");
        
        Map<String, Object> errorData = objectMapper.readValue(
            response.body(), Map.class);
        
        assertEquals("Invalid CSRF token", errorData.get("error"));
    }
    
    @Test
    @DisplayName("Scenario 1.2.3: POST with invalid CSRF token fails")
    void testInvalidCsrfToken() throws Exception {
        // Given
        var userData = TestFixtures.validUserData();
        String requestBody = objectMapper.writeValueAsString(userData);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/user"))
            .header("Content-Type", "application/json")
            .header("X-CSRF-Token", "fake-invalid-token")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
        
        // When
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        // Then
        assertEquals(403, response.statusCode());
    }
}
```

#### **Frontend: `FormComponent.csrf.test.ts`**

```typescript
// app-bana-ui/src/components/FormComponent.csrf.test.ts

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { FormComponent } from './FormComponent';
import { MOCK_CSRF_TOKEN } from '../test/fixtures/test-fixtures';

describe('FormComponent - CSRF Protection', () => {
  let component: FormComponent;
  let fetchMock: any;
  
  beforeEach(() => {
    component = document.createElement('form-component') as FormComponent;
    component.security = { csrfToken: true };
    document.body.appendChild(component);
    
    // Mock fetch
    fetchMock = vi.spyOn(global, 'fetch');
  });
  
  afterEach(() => {
    component.remove();
    vi.restoreAllMocks();
  });
  
  it('Scenario 1.2.1: Fetches CSRF token on mount', async () => {
    // Given
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ token: MOCK_CSRF_TOKEN })
    });
    
    // When
    await component.connectedCallback();
    
    // Then
    expect(fetchMock).toHaveBeenCalledWith('/api/csrf-token');
    expect(component.csrfToken).toBe(MOCK_CSRF_TOKEN);
  });
  
  it('Scenario 1.2.1: Injects CSRF token as hidden input', async () => {
    // Given
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ token: MOCK_CSRF_TOKEN })
    });
    
    // When
    await component.connectedCallback();
    await component.updateComplete;
    
    // Then
    const hiddenInput = component.shadowRoot?.querySelector(
      'input[name="_csrf"]') as HTMLInputElement;
    
    expect(hiddenInput).toBeTruthy();
    expect(hiddenInput.type).toBe('hidden');
    expect(hiddenInput.value).toBe(MOCK_CSRF_TOKEN);
  });
  
  it('Scenario 1.2.2: Includes CSRF token in form submission', async () => {
    // Given
    component.csrfToken = MOCK_CSRF_TOKEN;
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ data: { id: 123 } })
    });
    
    // When
    await component.submit();
    
    // Then
    expect(fetchMock).toHaveBeenCalledWith(
      expect.any(String),
      expect.objectContaining({
        headers: expect.objectContaining({
          'X-CSRF-Token': MOCK_CSRF_TOKEN
        })
      })
    );
  });
  
  it('Scenario 1.2.4: Shows error when token expired', async () => {
    // Given
    component.csrfToken = 'expired-token';
    fetchMock.mockResolvedValueOnce({
      ok: false,
      status: 403,
      json: async () => ({ error: 'Invalid CSRF token' })
    });
    
    // When
    await component.submit();
    
    // Then
    const errorMessage = component.shadowRoot?.querySelector('.error-message');
    expect(errorMessage?.textContent).toContain('Session expired');
  });
});
```

---

### Story 1.3: Rate Limiting Tests

#### **Backend: `RateLimitServiceTest.java`**

```java
// app-bana-service/src/test/java/com/appbana/service/RateLimitServiceTest.java

package com.appbana.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Rate Limit Service Tests")
class RateLimitServiceTest {
    
    private RateLimitService rateLimitService;
    private static final String TEST_IP = "192.168.1.100";
    private static final String TEST_ENDPOINT = "/api/user";
    
    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService();
    }
    
    @Test
    @DisplayName("Scenario 1.3.1: First 5 requests allowed")
    void testRateLimitAllowsInitialRequests() {
        // When/Then
        for (int i = 0; i < 5; i++) {
            boolean allowed = rateLimitService.checkLimit(
                TEST_IP, TEST_ENDPOINT, 5, 1);
            assertTrue(allowed, "Request " + (i+1) + " should be allowed");
        }
    }
    
    @Test
    @DisplayName("Scenario 1.3.1: 6th request blocked")
    void testRateLimitBlocksExcessRequests() {
        // Given - Make 5 requests
        for (int i = 0; i < 5; i++) {
            rateLimitService.checkLimit(TEST_IP, TEST_ENDPOINT, 5, 1);
        }
        
        // When - 6th request
        boolean allowed = rateLimitService.checkLimit(
            TEST_IP, TEST_ENDPOINT, 5, 1);
        
        // Then
        assertFalse(allowed, "6th request should be blocked");
    }
    
    @Test
    @DisplayName("Scenario 1.3.2: Rate limit resets after window")
    void testRateLimitReset() throws InterruptedException {
        // Given - Exceed limit
        for (int i = 0; i < 6; i++) {
            rateLimitService.checkLimit(TEST_IP, TEST_ENDPOINT, 5, 1);
        }
        
        // When - Wait for window to expire (1 minute = 60000ms)
        Thread.sleep(61000);
        boolean allowed = rateLimitService.checkLimit(
            TEST_IP, TEST_ENDPOINT, 5, 1);
        
        // Then
        assertTrue(allowed, "Request should be allowed after window reset");
    }
    
    @Test
    @DisplayName("Scenario 1.3.3: Different endpoints have separate limits")
    void testSeparateLimitsPerEndpoint() {
        // Given - Exceed limit on /api/user
        for (int i = 0; i < 6; i++) {
            rateLimitService.checkLimit(TEST_IP, "/api/user", 5, 1);
        }
        
        // When - Try different endpoint
        boolean allowed = rateLimitService.checkLimit(
            TEST_IP, "/api/login", 5, 1);
        
        // Then
        assertTrue(allowed, "/api/login should have separate limit");
    }
    
    @Test
    @DisplayName("Different IPs have separate limits")
    void testSeparateLimitsPerIP() {
        // Given - Exceed limit for IP1
        for (int i = 0; i < 6; i++) {
            rateLimitService.checkLimit("192.168.1.100", TEST_ENDPOINT, 5, 1);
        }
        
        // When - Try different IP
        boolean allowed = rateLimitService.checkLimit(
            "192.168.1.101", TEST_ENDPOINT, 5, 1);
        
        // Then
        assertTrue(allowed, "Different IP should have separate limit");
    }
    
    @Test
    @DisplayName("Scenario 1.3.4: Get remaining attempts")
    void testRemainingAttempts() {
        // Given
        for (int i = 0; i < 3; i++) {
            rateLimitService.checkLimit(TEST_IP, TEST_ENDPOINT, 5, 1);
        }
        
        // When
        int remaining = rateLimitService.getRemainingAttempts(
            TEST_IP, TEST_ENDPOINT, 5, 1);
        
        // Then
        assertEquals(2, remaining, "Should have 2 attempts remaining");
    }
}
```

#### **Backend: `RateLimitMiddlewareTest.java` (Integration)**

```java
// app-bana-service/src/test/java/com/appbana/integration/RateLimitMiddlewareTest.java

package com.appbana.integration;

import com.appbana.test.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@DisplayName("Rate Limit Middleware Integration Tests")
class RateLimitMiddlewareTest {
    
    private static final String BASE_URL = "http://localhost:8080";
    private HttpClient httpClient;
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        httpClient = HttpClient.newHttpClient();
        objectMapper = new ObjectMapper();
    }
    
    @Test
    @DisplayName("Scenario 1.3.1: Rate limit enforced (5 req/min)")
    void testRateLimitEnforcement() throws Exception {
        // Given
        var userData = TestFixtures.validUserData();
        String requestBody = objectMapper.writeValueAsString(userData);
        
        // When - Make 6 requests rapidly
        for (int i = 0; i < 5; i++) {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/user"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
            
            HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());
            
            assertTrue(response.statusCode() == 201 || response.statusCode() == 400,
                "Requests 1-5 should be processed (201 or 400 for duplicate email)");
        }
        
        // 6th request
        HttpRequest request6 = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/user"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
        
        HttpResponse<String> response6 = httpClient.send(request6, 
            HttpResponse.BodyHandlers.ofString());
        
        // Then
        assertEquals(429, response6.statusCode(), 
            "6th request should return 429 Too Many Requests");
        
        Map<String, Object> errorData = objectMapper.readValue(
            response6.body(), Map.class);
        
        assertEquals("Too many signup attempts. Try again in 1 minute.", 
            errorData.get("error"));
        assertTrue(errorData.containsKey("retryAfter"));
    }
    
    @Test
    @DisplayName("Scenario 1.3.4: X-RateLimit headers included")
    void testRateLimitHeaders() throws Exception {
        // Given
        var userData = TestFixtures.validUserData();
        String requestBody = objectMapper.writeValueAsString(userData);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/user"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
        
        // When
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        // Then
        assertTrue(response.headers().firstValue("X-RateLimit-Limit").isPresent());
        assertTrue(response.headers().firstValue("X-RateLimit-Remaining").isPresent());
        assertTrue(response.headers().firstValue("X-RateLimit-Reset").isPresent());
        
        assertEquals("5", response.headers().firstValue("X-RateLimit-Limit").get());
    }
}
```

---

## 🧪 Test Commands Cheat Sheet

### Backend Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=PasswordServiceTest

# Run tests with coverage
mvn test jacoco:report
# View: target/site/jacoco/index.html

# Run integration tests only
mvn verify -Pintegration

# Run security tests only
mvn test -Psecurity

# Run tests with debug logging
mvn test -X
```

### Frontend Tests

```bash
# Run all tests
npm test

# Run specific test file
npm test -- FormComponent.test.ts

# Run tests in watch mode (re-run on file change)
npm test -- --watch

# Run tests with coverage
npm run test:coverage
# View: coverage/index.html

# Run E2E tests
npm run test:e2e

# Run accessibility tests
npm run test:a11y

# Run tests in specific browser (E2E)
npm run test:e2e -- --browser=firefox
```

### Combined Commands

```bash
# Run all tests (backend + frontend)
mvn test && cd ../app-bana-ui && npm test

# Run coverage for both
mvn test jacoco:report && cd ../app-bana-ui && npm run test:coverage

# Quick smoke test (fast unit tests only)
mvn test -Dtest=*ServiceTest && cd ../app-bana-ui && npm test -- --run
```

---

## 📊 Coverage Requirements

| Test Type | Minimum Coverage | Tool | Report Location |
|-----------|-----------------|------|-----------------|
| Backend Unit | 80% | JaCoCo | `target/site/jacoco/index.html` |
| Frontend Unit | 80% | Vitest | `coverage/index.html` |
| Integration | 70% | JaCoCo | `target/site/jacoco-it/index.html` |
| E2E | Critical paths | Playwright | `test-results/index.html` |

---

---

### Story 1.4: Validation Feedback Tests

#### **Backend: `ValidationServiceTest.java`**

```java
// app-bana-service/src/test/java/com/appbana/service/ValidationServiceTest.java

package com.appbana.service;

import com.appbana.test.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;

@DisplayName("Validation Service Tests")
class ValidationServiceTest {
    
    private ValidationService validationService;
    
    @BeforeEach
    void setUp() {
        validationService = new ValidationService();
    }
    
    @Test
    @DisplayName("Scenario 1.4.1: Returns validation errors for invalid email")
    void testEmailValidation() {
        // Given
        var userData = TestFixtures.invalidEmailData();
        
        // When
        Map<String, String> errors = validationService.validate(userData, "user");
        
        // Then
        assertTrue(errors.containsKey("email"), "Should have email error");
        assertEquals("Invalid email format", errors.get("email"));
    }
    
    @Test
    @DisplayName("Scenario 1.4.2: Returns multiple validation errors")
    void testMultipleValidationErrors() {
        // Given
        Map<String, Object> userData = Map.of(
            "firstName", "",  // Required
            "email", "bad-email",  // Invalid format
            "password", "123"  // Too weak
        );
        
        // When
        Map<String, String> errors = validationService.validate(userData, "user");
        
        // Then
        assertEquals(3, errors.size(), "Should have 3 errors");
        assertTrue(errors.containsKey("firstName"));
        assertTrue(errors.containsKey("email"));
        assertTrue(errors.containsKey("password"));
    }
    
    @Test
    @DisplayName("Scenario 1.4.3: Returns empty map for valid data")
    void testValidDataNoErrors() {
        // Given
        var userData = TestFixtures.validUserData();
        
        // When
        Map<String, String> errors = validationService.validate(userData, "user");
        
        // Then
        assertTrue(errors.isEmpty(), "Should have no errors for valid data");
    }
    
    @Test
    @DisplayName("Scenario 1.4.4: Validates required fields")
    void testRequiredFieldValidation() {
        // Given
        Map<String, Object> userData = Map.of(
            "email", "john@example.com"
            // Missing firstName, lastName (required)
        );
        
        // When
        Map<String, String> errors = validationService.validate(userData, "user");
        
        // Then
        assertTrue(errors.containsKey("firstName"));
        assertTrue(errors.containsKey("lastName"));
        assertEquals("First name is required", errors.get("firstName"));
        assertEquals("Last name is required", errors.get("lastName"));
    }
    
    @Test
    @DisplayName("Validates unique email constraint")
    void testUniqueEmailValidation() {
        // Given - Email already exists in DB
        var userData = TestFixtures.duplicateEmailData();
        
        // When
        Map<String, String> errors = validationService.validate(userData, "user");
        
        // Then
        assertTrue(errors.containsKey("email"));
        assertEquals("Email already exists", errors.get("email"));
    }
}
```

#### **Backend: `ValidationApiTest.java` (Integration)**

```java
// app-bana-service/src/test/java/com/appbana/integration/ValidationApiTest.java

package com.appbana.integration;

import com.appbana.test.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@DisplayName("Validation API Integration Tests")
class ValidationApiTest {
    
    private static final String BASE_URL = "http://localhost:8080";
    private HttpClient httpClient;
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        httpClient = HttpClient.newHttpClient();
        objectMapper = new ObjectMapper();
    }
    
    @Test
    @DisplayName("Scenario 1.4.1: POST returns 400 with validation errors")
    void testValidationErrorResponse() throws Exception {
        // Given
        var userData = TestFixtures.invalidEmailData();
        String requestBody = objectMapper.writeValueAsString(userData);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/user"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
        
        // When
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        // Then
        assertEquals(400, response.statusCode(), "Should return 400 Bad Request");
        
        Map<String, Object> responseData = objectMapper.readValue(
            response.body(), Map.class);
        
        assertFalse((Boolean) responseData.get("ok"));
        assertTrue(responseData.containsKey("validationErrors"));
        
        Map<String, String> errors = 
            (Map<String, String>) responseData.get("validationErrors");
        assertTrue(errors.containsKey("email"));
    }
    
    @Test
    @DisplayName("Scenario 1.4.2: Response includes all validation errors")
    void testMultipleValidationErrorsInResponse() throws Exception {
        // Given
        Map<String, Object> userData = Map.of(
            "firstName", "",
            "email", "bad-email",
            "password", "123"
        );
        String requestBody = objectMapper.writeValueAsString(userData);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/user"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
        
        // When
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        // Then
        Map<String, Object> responseData = objectMapper.readValue(
            response.body(), Map.class);
        
        Map<String, String> errors = 
            (Map<String, String>) responseData.get("validationErrors");
        
        assertTrue(errors.size() >= 3, "Should return all validation errors");
        assertTrue(errors.containsKey("firstName"));
        assertTrue(errors.containsKey("email"));
        assertTrue(errors.containsKey("password"));
    }
}
```

#### **Frontend: `FormComponent.validation.test.ts`**

```typescript
// app-bana-ui/src/components/FormComponent.validation.test.ts

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { FormComponent } from './FormComponent';
import { MOCK_API_VALIDATION_ERROR } from '../test/fixtures/test-fixtures';

describe('FormComponent - Validation Feedback', () => {
  let component: FormComponent;
  let fetchMock: any;
  
  beforeEach(() => {
    component = document.createElement('form-component') as FormComponent;
    component.validationRules = {
      email: {
        pattern: '^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$',
        errorMessage: 'Invalid email format'
      },
      password: {
        minLength: 8,
        errorMessage: 'Password must be at least 8 characters'
      }
    };
    document.body.appendChild(component);
    
    fetchMock = vi.spyOn(global, 'fetch');
  });
  
  afterEach(() => {
    component.remove();
    vi.restoreAllMocks();
  });
  
  it('Scenario 1.4.1: Displays validation error below field', async () => {
    // Given
    fetchMock.mockResolvedValueOnce({
      ok: false,
      status: 400,
      json: async () => MOCK_API_VALIDATION_ERROR
    });
    
    // When
    await component.submit();
    await component.updateComplete;
    
    // Then
    const emailError = component.shadowRoot?.querySelector(
      '[data-field="email"] .error-message') as HTMLElement;
    
    expect(emailError).toBeTruthy();
    expect(emailError.textContent).toContain('Email already exists');
    expect(emailError.classList.contains('error')).toBe(true);
  });
  
  it('Scenario 1.4.2: Displays multiple validation errors', async () => {
    // Given
    fetchMock.mockResolvedValueOnce({
      ok: false,
      status: 400,
      json: async () => MOCK_API_VALIDATION_ERROR
    });
    
    // When
    await component.submit();
    await component.updateComplete;
    
    // Then
    const errorMessages = component.shadowRoot?.querySelectorAll('.error-message');
    expect(errorMessages?.length).toBeGreaterThanOrEqual(2);
  });
  
  it('Scenario 1.4.3: Focuses first error field', async () => {
    // Given
    fetchMock.mockResolvedValueOnce({
      ok: false,
      status: 400,
      json: async () => MOCK_API_VALIDATION_ERROR
    });
    
    const focusSpy = vi.fn();
    const emailInput = component.shadowRoot?.querySelector(
      '[name="email"]') as HTMLInputElement;
    emailInput.focus = focusSpy;
    
    // When
    await component.submit();
    
    // Then
    expect(focusSpy).toHaveBeenCalled();
  });
  
  it('Scenario 1.4.4: Clears error when field corrected', async () => {
    // Given - Show error first
    component.validationErrors = { email: 'Invalid email format' };
    await component.updateComplete;
    
    // When - User corrects the field
    component.setFieldValue('email', 'valid@example.com');
    await component.validateField('email');
    await component.updateComplete;
    
    // Then
    const emailError = component.shadowRoot?.querySelector(
      '[data-field="email"] .error-message') as HTMLElement;
    
    expect(emailError).toBeFalsy();
    expect(component.validationErrors.email).toBeUndefined();
  });
  
  it('Scenario 1.4.5: Error has red styling', async () => {
    // Given
    component.validationErrors = { email: 'Invalid email' };
    await component.updateComplete;
    
    // Then
    const errorElement = component.shadowRoot?.querySelector(
      '[data-field="email"] .error-message') as HTMLElement;
    
    const styles = getComputedStyle(errorElement);
    expect(styles.color).toBe('rgb(220, 38, 38)'); // red-600
  });
});
```

---

### Story 1.5: Accessibility Tests

#### **Frontend: `FormComponent.a11y.test.ts`**

```typescript
// app-bana-ui/src/components/FormComponent.a11y.test.ts

import { describe, it, expect, beforeEach } from 'vitest';
import { fixture, html } from '@open-wc/testing';
import { FormComponent } from './FormComponent';

describe('FormComponent - Accessibility', () => {
  let component: FormComponent;
  
  beforeEach(async () => {
    component = await fixture(html`
      <form-component>
        <input name="email" id="email-input" />
        <label for="email-input">Email</label>
      </form-component>
    `);
  });
  
  it('Scenario 1.5.1: Label linked to input via for/id', () => {
    // When
    const label = component.shadowRoot?.querySelector('label') as HTMLLabelElement;
    const input = component.shadowRoot?.querySelector('input') as HTMLInputElement;
    
    // Then
    expect(label.htmlFor).toBe(input.id);
    expect(input.id).toBe('email-input');
  });
  
  it('Scenario 1.5.2: Required input has aria-required', () => {
    // Given
    component.validationRules = {
      email: { required: true }
    };
    component.requestUpdate();
    
    // Then
    const input = component.shadowRoot?.querySelector(
      '[name="email"]') as HTMLInputElement;
    
    expect(input.getAttribute('aria-required')).toBe('true');
  });
  
  it('Scenario 1.5.3: Invalid input has aria-invalid', async () => {
    // Given
    component.validationErrors = { email: 'Invalid email' };
    await component.updateComplete;
    
    // Then
    const input = component.shadowRoot?.querySelector(
      '[name="email"]') as HTMLInputElement;
    
    expect(input.getAttribute('aria-invalid')).toBe('true');
  });
  
  it('Scenario 1.5.4: Error message has aria-describedby', async () => {
    // Given
    component.validationErrors = { email: 'Invalid email' };
    await component.updateComplete;
    
    // Then
    const input = component.shadowRoot?.querySelector(
      '[name="email"]') as HTMLInputElement;
    const errorId = input.getAttribute('aria-describedby');
    
    expect(errorId).toBeTruthy();
    
    const errorElement = component.shadowRoot?.querySelector(`#${errorId}`);
    expect(errorElement).toBeTruthy();
    expect(errorElement?.textContent).toContain('Invalid email');
  });
  
  it('Scenario 1.5.5: Tab navigation follows logical order', () => {
    // Given
    const inputs = component.shadowRoot?.querySelectorAll('input');
    
    // Then
    inputs?.forEach((input, index) => {
      const tabIndex = input.tabIndex;
      expect(tabIndex).toBe(0); // Natural tab order
    });
  });
  
  it('Scenario 1.5.6: Submit button accessible via Enter key', () => {
    // Given
    const button = component.shadowRoot?.querySelector('button') as HTMLButtonElement;
    const submitSpy = vi.fn();
    component.addEventListener('submit', submitSpy);
    
    // When - Press Enter on button
    const enterEvent = new KeyboardEvent('keydown', { key: 'Enter' });
    button.dispatchEvent(enterEvent);
    
    // Then
    expect(submitSpy).toHaveBeenCalled();
  });
});
```

#### **E2E: `form-accessibility.e2e.test.ts`**

```typescript
// app-bana-ui/tests/e2e/form-accessibility.e2e.test.ts

import { test, expect } from '@playwright/test';
import { injectAxe, checkA11y } from 'axe-playwright';

test.describe('Form Accessibility - E2E Tests', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('http://localhost:5173/signup');
    await injectAxe(page);
  });
  
  test('Scenario 1.5.7: Lighthouse accessibility score ≥90', async ({ page }) => {
    // When
    const lighthouse = await page.evaluate(async () => {
      // Run Lighthouse audit
      const { default: lighthouse } = await import('lighthouse');
      const result = await lighthouse(window.location.href);
      return result.lhr.categories.accessibility.score * 100;
    });
    
    // Then
    expect(lighthouse).toBeGreaterThanOrEqual(90);
  });
  
  test('Scenario 1.5.8: axe-core reports 0 violations', async ({ page }) => {
    // When/Then
    await checkA11y(page, null, {
      detailedReport: true,
      detailedReportOptions: { html: true }
    });
  });
  
  test('Scenario 1.5.9: Screen reader announces errors', async ({ page }) => {
    // Given - Submit form with errors
    await page.click('button[type="submit"]');
    await page.waitForSelector('.error-message');
    
    // When - Check ARIA live region
    const ariaLive = await page.getAttribute('[role="alert"]', 'aria-live');
    
    // Then
    expect(ariaLive).toBe('assertive');
    
    const errorCount = await page.locator('.error-message').count();
    const announcement = await page.textContent('[role="alert"]');
    expect(announcement).toContain(`${errorCount} error`);
  });
  
  test('Scenario 1.5.10: Keyboard-only navigation completes signup', async ({ page }) => {
    // Given - No mouse, keyboard only
    const inputs = [
      { name: 'firstName', value: 'John' },
      { name: 'lastName', value: 'Doe' },
      { name: 'email', value: 'john@example.com' },
      { name: 'phone', value: '+1234567890' },
      { name: 'password', value: 'SecurePass123' },
      { name: 'confirmPassword', value: 'SecurePass123' }
    ];
    
    // When - Tab through form and fill with keyboard
    await page.keyboard.press('Tab'); // Focus first input
    
    for (const input of inputs) {
      await page.keyboard.type(input.value);
      await page.keyboard.press('Tab');
    }
    
    await page.keyboard.press('Enter'); // Submit
    
    // Then
    await page.waitForURL('**/success');
    expect(page.url()).toContain('/success');
  });
});
```

---

## 🟡 Sprint 2: UX & Progressive Validation

### Story 2.1: Loading States Tests

#### **Frontend: `FormComponent.loading.test.ts`**

```typescript
// app-bana-ui/src/components/FormComponent.loading.test.ts

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { FormComponent } from './FormComponent';

describe('FormComponent - Loading States', () => {
  let component: FormComponent;
  let fetchMock: any;
  
  beforeEach(() => {
    component = document.createElement('form-component') as FormComponent;
    document.body.appendChild(component);
    
    fetchMock = vi.spyOn(global, 'fetch');
  });
  
  afterEach(() => {
    component.remove();
    vi.restoreAllMocks();
  });
  
  it('Scenario 2.1.1: Submit button shows "Submitting..." during API call', async () => {
    // Given
    fetchMock.mockImplementation(() => 
      new Promise(resolve => setTimeout(resolve, 1000))
    );
    
    const button = component.shadowRoot?.querySelector('button') as HTMLButtonElement;
    const originalText = button.textContent;
    
    // When
    const submitPromise = component.submit();
    await component.updateComplete;
    
    // Then - During submission
    expect(button.textContent).toContain('Submitting...');
    expect(button.disabled).toBe(true);
    
    await submitPromise;
    
    // After submission
    expect(button.textContent).toBe(originalText);
  });
  
  it('Scenario 2.1.2: Form inputs disabled during submission', async () => {
    // Given
    fetchMock.mockImplementation(() => 
      new Promise(resolve => setTimeout(resolve, 500))
    );
    
    const inputs = component.shadowRoot?.querySelectorAll('input');
    
    // When
    const submitPromise = component.submit();
    await component.updateComplete;
    
    // Then - During submission
    inputs?.forEach(input => {
      expect(input.disabled).toBe(true);
    });
    
    await submitPromise;
    
    // After submission
    inputs?.forEach(input => {
      expect(input.disabled).toBe(false);
    });
  });
  
  it('Scenario 2.1.3: Success message shows after successful submission', async () => {
    // Given
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ data: { id: 123 } })
    });
    
    // When
    await component.submit();
    await component.updateComplete;
    
    // Then
    const successMessage = component.shadowRoot?.querySelector('.success-message');
    expect(successMessage).toBeTruthy();
    expect(successMessage?.textContent).toContain('Account created successfully!');
    
    // Check icon
    const icon = successMessage?.querySelector('.icon-check');
    expect(icon).toBeTruthy();
  });
  
  it('Scenario 2.1.4: Error message shows after failed submission', async () => {
    // Given
    fetchMock.mockResolvedValueOnce({
      ok: false,
      status: 500,
      json: async () => ({ error: 'Server error' })
    });
    
    // When
    await component.submit();
    await component.updateComplete;
    
    // Then
    const errorMessage = component.shadowRoot?.querySelector('.error-banner');
    expect(errorMessage).toBeTruthy();
    expect(errorMessage?.textContent).toContain('Server error');
    
    // Check icon
    const icon = errorMessage?.querySelector('.icon-error');
    expect(icon).toBeTruthy();
  });
  
  it('Scenario 2.1.5: Loading spinner shows during submission', async () => {
    // Given
    fetchMock.mockImplementation(() => 
      new Promise(resolve => setTimeout(resolve, 500))
    );
    
    // When
    const submitPromise = component.submit();
    await component.updateComplete;
    
    // Then
    const spinner = component.shadowRoot?.querySelector('.loading-spinner');
    expect(spinner).toBeTruthy();
    expect(spinner?.classList.contains('animate-spin')).toBe(true);
    
    await submitPromise;
    
    // After submission
    const spinnerAfter = component.shadowRoot?.querySelector('.loading-spinner');
    expect(spinnerAfter).toBeFalsy();
  });
  
  it('Scenario 2.1.6: Double-submit prevented', async () => {
    // Given
    let resolveFirst: any;
    const firstRequest = new Promise(resolve => { resolveFirst = resolve; });
    fetchMock.mockReturnValueOnce(firstRequest);
    
    // When - Try to submit twice rapidly
    component.submit();
    await component.updateComplete;
    
    const secondSubmitResult = component.submit();
    
    // Then
    expect(secondSubmitResult).toBe(false); // Second submit rejected
    expect(fetchMock).toHaveBeenCalledTimes(1); // Only one API call
    
    resolveFirst({ ok: true, json: async () => ({}) });
  });
});
```

---

### Story 2.2: Progressive Client Validation Tests

#### **Frontend: `FormComponent.progressive-validation.test.ts`**

```typescript
// app-bana-ui/src/components/FormComponent.progressive-validation.test.ts

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { FormComponent } from './FormComponent';

describe('FormComponent - Progressive Validation', () => {
  let component: FormComponent;
  
  beforeEach(() => {
    component = document.createElement('form-component') as FormComponent;
    component.validationStrategy = {
      validateOn: 'blur',
      revalidateOn: 'change'
    };
    component.validationRules = {
      email: {
        pattern: '^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$',
        errorMessage: 'Invalid email format'
      },
      password: {
        minLength: 8,
        errorMessage: 'Password must be at least 8 characters'
      }
    };
    document.body.appendChild(component);
  });
  
  afterEach(() => {
    component.remove();
  });
  
  it('Scenario 2.2.1: Validation triggered on blur', async () => {
    // Given
    const input = component.shadowRoot?.querySelector(
      '[name="email"]') as HTMLInputElement;
    input.value = 'invalid-email';
    
    // When - Blur (focus out)
    input.dispatchEvent(new FocusEvent('blur'));
    await component.updateComplete;
    
    // Then
    expect(component.validationErrors.email).toBe('Invalid email format');
    expect(component.touched.email).toBe(true);
  });
  
  it('Scenario 2.2.2: No validation on typing (before blur)', async () => {
    // Given
    const input = component.shadowRoot?.querySelector(
      '[name="email"]') as HTMLInputElement;
    
    // When - Type (no blur yet)
    input.value = 'inv';
    input.dispatchEvent(new Event('input'));
    await component.updateComplete;
    
    // Then
    expect(component.validationErrors.email).toBeUndefined();
  });
  
  it('Scenario 2.2.3: Re-validation on change after initial blur', async () => {
    // Given - Blur with error
    const input = component.shadowRoot?.querySelector(
      '[name="email"]') as HTMLInputElement;
    input.value = 'invalid';
    input.dispatchEvent(new FocusEvent('blur'));
    await component.updateComplete;
    
    expect(component.validationErrors.email).toBeTruthy();
    
    // When - Type to correct
    input.value = 'valid@example.com';
    input.dispatchEvent(new Event('input'));
    await component.updateComplete;
    
    // Then
    expect(component.validationErrors.email).toBeUndefined();
  });
  
  it('Scenario 2.2.4: Async validation (email uniqueness check)', async () => {
    // Given
    const fetchMock = vi.spyOn(global, 'fetch');
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ exists: true })
    });
    
    component.validationRules.email.asyncCheck = '/api/check-email';
    
    const input = component.shadowRoot?.querySelector(
      '[name="email"]') as HTMLInputElement;
    input.value = 'existing@example.com';
    
    // When
    input.dispatchEvent(new FocusEvent('blur'));
    await component.updateComplete;
    await new Promise(resolve => setTimeout(resolve, 100)); // Wait for async
    
    // Then
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/check-email?email=existing@example.com'
    );
    expect(component.validationErrors.email).toBe('Email already exists');
  });
  
  it('Scenario 2.2.5: Async validation shows loading indicator', async () => {
    // Given
    const fetchMock = vi.spyOn(global, 'fetch');
    let resolveAsync: any;
    const asyncPromise = new Promise(resolve => { resolveAsync = resolve; });
    fetchMock.mockReturnValueOnce(asyncPromise);
    
    component.validationRules.email.asyncCheck = '/api/check-email';
    
    const input = component.shadowRoot?.querySelector(
      '[name="email"]') as HTMLInputElement;
    input.value = 'test@example.com';
    
    // When
    input.dispatchEvent(new FocusEvent('blur'));
    await component.updateComplete;
    
    // Then - During validation
    const loadingIcon = component.shadowRoot?.querySelector(
      '[data-field="email"] .validating-spinner');
    expect(loadingIcon).toBeTruthy();
    
    resolveAsync({ ok: true, json: async () => ({ exists: false }) });
    await component.updateComplete;
    
    // After validation
    const loadingIconAfter = component.shadowRoot?.querySelector(
      '[data-field="email"] .validating-spinner');
    expect(loadingIconAfter).toBeFalsy();
  });
  
  it('Scenario 2.2.6: Submit button disabled until all fields valid', async () => {
    // Given - Invalid email
    component.validationErrors = { email: 'Invalid email' };
    await component.updateComplete;
    
    // Then
    const button = component.shadowRoot?.querySelector(
      'button[type="submit"]') as HTMLButtonElement;
    expect(button.disabled).toBe(true);
    
    // When - Fix email
    component.validationErrors = {};
    await component.updateComplete;
    
    // Then
    expect(button.disabled).toBe(false);
  });
});
```

---

## 🟢 Sprint 3: Advanced Features

### Story 3.1: Transaction Boundaries Tests

#### **Backend: `TransactionServiceTest.java`**

```java
// app-bana-service/src/test/java/com/appbana/service/TransactionServiceTest.java

package com.appbana.service;

import com.appbana.test.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;

@DisplayName("Transaction Service Tests")
class TransactionServiceTest {
    
    private TransactionService transactionService;
    private Connection connection;
    
    @BeforeEach
    void setUp() throws Exception {
        transactionService = new TransactionService();
        connection = TestDatabase.getConnection();
        TestDatabase.cleanDatabase(connection);
    }
    
    @Test
    @DisplayName("Scenario 3.1.1: Multi-step operation completes atomically")
    void testAtomicTransaction() throws Exception {
        // Given
        var userData = TestFixtures.validUserData();
        
        // When
        transactionService.executeInTransaction(conn -> {
            // Step 1: Insert user
            long userId = insertUser(conn, userData);
            
            // Step 2: Send welcome email (simulated)
            sendWelcomeEmail(userId);
            
            // Step 3: Create default preferences
            createDefaultPreferences(conn, userId);
            
            return userId;
        });
        
        // Then - All steps committed
        var stmt = connection.prepareStatement(
            "SELECT COUNT(*) FROM user");
        var rs = stmt.executeQuery();
        rs.next();
        assertEquals(1, rs.getInt(1), "User should be inserted");
        
        var prefStmt = connection.prepareStatement(
            "SELECT COUNT(*) FROM user_preferences");
        var prefRs = prefStmt.executeQuery();
        prefRs.next();
        assertEquals(1, prefRs.getInt(1), "Preferences should be created");
    }
    
    @Test
    @DisplayName("Scenario 3.1.2: Rollback on failure in multi-step operation")
    void testTransactionRollback() throws Exception {
        // Given
        var userData = TestFixtures.validUserData();
        
        // When - Exception in step 3
        try {
            transactionService.executeInTransaction(conn -> {
                // Step 1: Insert user
                long userId = insertUser(conn, userData);
                
                // Step 2: Send email (succeeds)
                sendWelcomeEmail(userId);
                
                // Step 3: Create preferences (fails)
                throw new SQLException("Simulated database error");
            });
            fail("Should throw exception");
        } catch (SQLException e) {
            // Expected
        }
        
        // Then - Entire transaction rolled back
        var stmt = connection.prepareStatement(
            "SELECT COUNT(*) FROM user");
        var rs = stmt.executeQuery();
        rs.next();
        assertEquals(0, rs.getInt(1), "User should NOT be inserted (rollback)");
        
        var prefStmt = connection.prepareStatement(
            "SELECT COUNT(*) FROM user_preferences");
        var prefRs = prefStmt.executeQuery();
        prefRs.next();
        assertEquals(0, prefRs.getInt(1), "Preferences should NOT exist");
    }
    
    @Test
    @DisplayName("Scenario 3.1.3: Optional post-action does not affect transaction")
    void testOptionalPostAction() throws Exception {
        // Given
        var userData = TestFixtures.validUserData();
        
        // When - Email fails (optional action)
        Long userId = transactionService.executeInTransaction(conn -> {
            // Step 1: Insert user
            long id = insertUser(conn, userData);
            
            // Step 2: Optional email (fails, but continues)
            try {
                sendWelcomeEmail(id);
            } catch (Exception e) {
                // Log but don't throw
                System.out.println("Email failed (non-critical): " + e.getMessage());
            }
            
            return id;
        });
        
        // Then - Transaction still commits
        assertNotNull(userId);
        
        var stmt = connection.prepareStatement(
            "SELECT COUNT(*) FROM user");
        var rs = stmt.executeQuery();
        rs.next();
        assertEquals(1, rs.getInt(1), "User should be inserted despite email failure");
    }
}
```

#### **Backend: `TransactionApiTest.java` (Integration)**

```java
// app-bana-service/src/test/java/com/appbana/integration/TransactionApiTest.java

package com.appbana.integration;

import com.appbana.test.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;

@DisplayName("Transaction API Integration Tests")
class TransactionApiTest {
    
    private static final String BASE_URL = "http://localhost:8080";
    private HttpClient httpClient;
    private ObjectMapper objectMapper;
    private Connection dbConnection;
    
    @BeforeEach
    void setUp() throws Exception {
        httpClient = HttpClient.newHttpClient();
        objectMapper = new ObjectMapper();
        dbConnection = TestDatabase.getConnection();
        TestDatabase.cleanDatabase(dbConnection);
    }
    
    @Test
    @DisplayName("Scenario 3.1.1: POST /api/user with post-actions succeeds")
    void testUserCreationWithPostActions() throws Exception {
        // Given - Request with post-actions
        var userData = TestFixtures.validUserData();
        userData.put("postActions", List.of(
            Map.of("type", "sendEmail", "template", "welcome"),
            Map.of("type", "createPreferences", "theme", "light")
        ));
        String requestBody = objectMapper.writeValueAsString(userData);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/user"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
        
        // When
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        // Then
        assertEquals(201, response.statusCode());
        
        // Verify user exists
        var stmt = dbConnection.prepareStatement(
            "SELECT * FROM user WHERE email = ?");
        stmt.setString(1, "john@example.com");
        var rs = stmt.executeQuery();
        assertTrue(rs.next(), "User should exist");
        
        // Verify preferences created
        Long userId = rs.getLong("id");
        var prefStmt = dbConnection.prepareStatement(
            "SELECT * FROM user_preferences WHERE user_id = ?");
        prefStmt.setLong(1, userId);
        var prefRs = prefStmt.executeQuery();
        assertTrue(prefRs.next(), "Preferences should exist");
        assertEquals("light", prefRs.getString("theme"));
    }
}
```

---

### Story 3.2: File Upload Tests

#### **Backend: `FileUploadServiceTest.java`**

```java
// app-bana-service/src/test/java/com/appbana/service/FileUploadServiceTest.java

package com.appbana.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

@DisplayName("File Upload Service Tests")
class FileUploadServiceTest {
    
    private FileUploadService fileUploadService;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        fileUploadService = new FileUploadService(tempDir.toString());
    }
    
    @Test
    @DisplayName("Scenario 3.2.1: Accepts JPEG file upload")
    void testAcceptJpegUpload() throws Exception {
        // Given
        byte[] jpegData = createMockJpegData();
        String filename = "profile.jpg";
        
        // When
        String savedPath = fileUploadService.uploadFile(
            new ByteArrayInputStream(jpegData), filename, "image/jpeg");
        
        // Then
        assertNotNull(savedPath);
        assertTrue(savedPath.endsWith(".jpg"));
        assertTrue(Files.exists(Path.of(tempDir.toString(), savedPath)));
    }
    
    @Test
    @DisplayName("Scenario 3.2.2: Rejects invalid file types")
    void testRejectInvalidFileType() {
        // Given
        byte[] exeData = new byte[100];
        String filename = "malware.exe";
        
        // When/Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> fileUploadService.uploadFile(
                new ByteArrayInputStream(exeData), filename, "application/x-msdownload")
        );
        
        assertEquals("File type not allowed: application/x-msdownload", 
            exception.getMessage());
    }
    
    @Test
    @DisplayName("Scenario 3.2.3: Enforces 5MB file size limit")
    void testEnforceFileSizeLimit() {
        // Given - 6MB file (exceeds limit)
        byte[] largeFile = new byte[6 * 1024 * 1024];
        String filename = "large.jpg";
        
        // When/Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> fileUploadService.uploadFile(
                new ByteArrayInputStream(largeFile), filename, "image/jpeg")
        );
        
        assertEquals("File size exceeds 5MB limit", exception.getMessage());
    }
    
    @Test
    @DisplayName("Scenario 3.2.4: Generates unique filenames")
    void testUniqueFilenames() throws Exception {
        // Given
        byte[] data = createMockJpegData();
        String filename = "profile.jpg";
        
        // When - Upload same file twice
        String path1 = fileUploadService.uploadFile(
            new ByteArrayInputStream(data), filename, "image/jpeg");
        String path2 = fileUploadService.uploadFile(
            new ByteArrayInputStream(data), filename, "image/jpeg");
        
        // Then
        assertNotEquals(path1, path2, "Filenames should be unique");
        assertTrue(Files.exists(Path.of(tempDir.toString(), path1)));
        assertTrue(Files.exists(Path.of(tempDir.toString(), path2)));
    }
    
    private byte[] createMockJpegData() {
        // JPEG file signature: 0xFF 0xD8 0xFF
        byte[] jpegHeader = new byte[] { (byte)0xFF, (byte)0xD8, (byte)0xFF };
        byte[] data = new byte[1000];
        System.arraycopy(jpegHeader, 0, data, 0, jpegHeader.length);
        return data;
    }
}
```

#### **Frontend: `FileUpload.test.ts`**

```typescript
// app-bana-ui/src/components/FileUpload.test.ts

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { FileUploadComponent } from './FileUploadComponent';

describe('FileUploadComponent - File Upload', () => {
  let component: FileUploadComponent;
  let fetchMock: any;
  
  beforeEach(() => {
    component = document.createElement('file-upload') as FileUploadComponent;
    component.accept = 'image/jpeg,image/png';
    component.maxSize = 5 * 1024 * 1024; // 5MB
    document.body.appendChild(component);
    
    fetchMock = vi.spyOn(global, 'fetch');
  });
  
  afterEach(() => {
    component.remove();
    vi.restoreAllMocks();
  });
  
  it('Scenario 3.2.1: Accepts JPEG file', async () => {
    // Given
    const file = new File(['fake-jpeg-data'], 'profile.jpg', { type: 'image/jpeg' });
    
    // When
    const result = component.validateFile(file);
    
    // Then
    expect(result.isValid).toBe(true);
    expect(result.error).toBeNull();
  });
  
  it('Scenario 3.2.2: Rejects .exe file', () => {
    // Given
    const file = new File(['exe-data'], 'malware.exe', { 
      type: 'application/x-msdownload' 
    });
    
    // When
    const result = component.validateFile(file);
    
    // Then
    expect(result.isValid).toBe(false);
    expect(result.error).toBe('File type not allowed. Allowed: image/jpeg, image/png');
  });
  
  it('Scenario 3.2.3: Rejects 6MB file', () => {
    // Given
    const largeData = new Uint8Array(6 * 1024 * 1024);
    const file = new File([largeData], 'large.jpg', { type: 'image/jpeg' });
    
    // When
    const result = component.validateFile(file);
    
    // Then
    expect(result.isValid).toBe(false);
    expect(result.error).toBe('File size exceeds 5MB limit');
  });
  
  it('Scenario 3.2.4: Shows upload progress', async () => {
    // Given
    const file = new File(['data'], 'profile.jpg', { type: 'image/jpeg' });
    
    const xhrMock = {
      open: vi.fn(),
      send: vi.fn(),
      upload: {
        addEventListener: vi.fn((event, handler) => {
          if (event === 'progress') {
            setTimeout(() => handler({ loaded: 500, total: 1000 }), 100);
          }
        })
      }
    };
    
    vi.stubGlobal('XMLHttpRequest', vi.fn(() => xhrMock));
    
    // When
    component.uploadFile(file);
    await new Promise(resolve => setTimeout(resolve, 150));
    
    // Then
    const progressBar = component.shadowRoot?.querySelector(
      '.upload-progress') as HTMLElement;
    expect(progressBar).toBeTruthy();
    expect(component.uploadProgress).toBe(50); // 500/1000
  });
  
  it('Scenario 3.2.5: Displays uploaded file preview', async () => {
    // Given
    const file = new File(['data'], 'profile.jpg', { type: 'image/jpeg' });
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ url: '/uploads/profile-123.jpg' })
    });
    
    // When
    await component.uploadFile(file);
    await component.updateComplete;
    
    // Then
    const preview = component.shadowRoot?.querySelector(
      '.file-preview img') as HTMLImageElement;
    expect(preview).toBeTruthy();
    expect(preview.src).toContain('/uploads/profile-123.jpg');
  });
});
```

---

## 🎯 Complete Test Implementation

**Document Status:** ✅ COMPLETE - All 9 Stories Covered  
**Last Updated:** December 30, 2025  
**Total Test Files:** 18 (Backend: 10, Frontend: 7, E2E: 1)  
**Total Test Cases:** 100+ across all stories  
**Ready for Sprint:** YES - Copy-paste and implement!
