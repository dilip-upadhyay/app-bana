# 🚀 START HERE - Implementation Guide

**Created:** December 30, 2025  
**Status:** ✅ Ready to Code  
**Sprint Start:** Day 1

---

## ✅ What's Been Set Up

### 1. **Documentation Complete** (3 files, 5,000+ lines)
- ✅ `ENTITY_FORM_BINDING_ARCHITECTURE.md` - System architecture
- ✅ `ENTITY_FORM_BINDING_STORIES.md` - User stories (9 stories, 38 scenarios)
- ✅ `ENTITY_FORM_BINDING_TEST_PLAN.md` - Test code (100+ test cases)
- ✅ `IMPLEMENTATION_FILE_STRUCTURE.md` - File structure roadmap

### 2. **Foundation Files Created** (4 files)
- ✅ `app-bana-service/src/test/java/com/appbana/test/TestFixtures.java`
- ✅ `app-bana-service/src/test/java/com/appbana/test/TestDatabase.java`
- ✅ `app-bana-service/src/main/java/com/appbana/model/ValidationResult.java`
- ✅ `app-bana-ui/src/test/fixtures/test-fixtures.ts`

### 3. **Directory Structure Created**
```
✅ app-bana-service/src/main/java/com/appbana/service/
✅ app-bana-service/src/main/java/com/appbana/middleware/
✅ app-bana-service/src/test/java/com/appbana/service/
✅ app-bana-service/src/test/java/com/appbana/integration/
✅ app-bana-ui/src/test/fixtures/
✅ app-bana-ui/tests/e2e/
```

---

## 🎯 Day 1: Start Here (2-4 hours)

### Step 1: Add Dependencies (15 minutes)

**Backend - Add to `app-bana-service/pom.xml`:**

```xml
<!-- Add inside <dependencies> section -->

<!-- BCrypt for password hashing (Story 1.1) -->
<dependency>
    <groupId>org.mindrot</groupId>
    <artifactId>jbcrypt</artifactId>
    <version>0.4</version>
</dependency>

<!-- JUnit 5 for testing -->
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.9.3</version>
    <scope>test</scope>
</dependency>

<!-- H2 Database for testing -->
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <version>2.2.222</version>
    <scope>test</scope>
</dependency>
```

**Frontend - Add to `app-bana-ui/package.json`:**

```bash
cd app-bana-ui

# Install test dependencies
npm install --save-dev @testing-library/lit @open-wc/testing
npm install --save-dev @playwright/test axe-playwright

# Initialize Playwright
npx playwright install
```

**Verify:**
```bash
# Backend
cd app-bana-service
mvn clean compile  # Should succeed

# Frontend
cd app-bana-ui
npm test  # Should pass (0 tests)
```

---

### Step 2: Create PasswordService (1 hour)

**File:** `app-bana-service/src/main/java/com/appbana/service/PasswordService.java`

```java
package com.appbana.service;

import com.appbana.model.ValidationResult;
import org.mindrot.jbcrypt.BCrypt;
import java.util.regex.Pattern;

/**
 * Password hashing and validation service.
 * Uses BCrypt for secure password hashing.
 * 
 * Story 1.1: Password Security
 * @see docs/ENTITY_FORM_BINDING_ARCHITECTURE.md Issue #1
 */
public class PasswordService {
    
    // Password must be 8+ chars with at least one letter and one number
    private static final Pattern PASSWORD_PATTERN = 
        Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d@$!%*#?&]{8,}$");
    
    /**
     * Hashes a plain text password using BCrypt.
     * @param plainPassword The plain text password
     * @return BCrypt hashed password (60 chars, starts with $2a$10$)
     */
    public String hashPassword(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(10));
    }
    
    /**
     * Verifies a plain text password against a BCrypt hash.
     * @param plainPassword The plain text password to verify
     * @param hashedPassword The BCrypt hash to verify against
     * @return true if password matches, false otherwise
     */
    public boolean verifyPassword(String plainPassword, String hashedPassword) {
        return BCrypt.checkpw(plainPassword, hashedPassword);
    }
    
    /**
     * Validates password strength.
     * Password must be 8+ characters with at least one letter and one number.
     */
    public ValidationResult validatePasswordStrength(String password) {
        if (password == null || password.isEmpty()) {
            return ValidationResult.error("Password is required");
        }
        
        if (password.length() < 8) {
            return ValidationResult.error("Password must be at least 8 characters");
        }
        
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            return ValidationResult.error("Password must be 8+ chars with letter and number");
        }
        
        return ValidationResult.success();
    }
}
```

---

### Step 3: Create PasswordServiceTest (30 minutes)

**File:** `app-bana-service/src/test/java/com/appbana/service/PasswordServiceTest.java`

**👉 Copy test code from `ENTITY_FORM_BINDING_TEST_PLAN.md` Story 1.1**

**Quick verification:**
```bash
cd app-bana-service
mvn test -Dtest=PasswordServiceTest
```

**Expected output:**
```
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
✅ All password security tests passing
```

---

### Step 4: Update User Model (15 minutes)

**File:** `app-bana-service/src/main/java/com/appbana/model/User.java`

**Add this field:**
```java
// CRITICAL: Use passwordHash, NOT password
private String passwordHash;  // Stores BCrypt hash

// Add getter/setter
public String getPasswordHash() {
    return passwordHash;
}

public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
}
```

**❌ DO NOT add:**
```java
private String password;  // NEVER store plain text passwords!
```

---

### Step 5: Update ApiServer (1 hour)

**File:** `app-bana-service/src/main/java/com/appbana/ApiServer.java`

**Find the POST /api/user handler and add password hashing:**

```java
// In buildRouter() method, update POST /api/user handler

router.post("/api/user", (req, res) -> {
    try {
        String body = new String(req.body(), StandardCharsets.UTF_8);
        Map<String, Object> data = objectMapper.readValue(body, Map.class);
        
        // NEW: Field mapping - password → passwordHash
        if (data.containsKey("password")) {
            String plainPassword = (String) data.get("password");
            
            // Validate password strength
            PasswordService passwordService = new PasswordService();
            ValidationResult validation = passwordService.validatePasswordStrength(plainPassword);
            
            if (!validation.isValid()) {
                res.json(400, Map.of(
                    "ok", false,
                    "validationErrors", Map.of("password", validation.getErrorMessage())
                ));
                return;
            }
            
            // Hash password before database insert
            String hashedPassword = passwordService.hashPassword(plainPassword);
            data.put("passwordHash", hashedPassword);
            data.remove("password");  // Remove plain text
        }
        
        // NEW: Exclude confirmPassword from entity
        data.remove("confirmPassword");
        
        // Existing insert logic...
        String entityName = "user";
        long id = insert(entityName, data);
        
        // CRITICAL: Do NOT return passwordHash in response
        Map<String, Object> responseData = getById(entityName, id);
        responseData.remove("passwordHash");
        
        res.json(201, Map.of("ok", true, "data", responseData));
        
    } catch (Exception e) {
        LOG.error("POST /api/user error", e);
        res.json(500, errorDetails(e));
    }
});
```

---

### Step 6: Update signup.json Template (15 minutes)

**File:** `resources/page-templates/signup.json`

**Add metadata at top level:**
```json
{
  "id": "signup",
  "name": "Sign Up Page",
  
  "entityBinding": {
    "entity": "user",
    "action": "create",
    "fieldMapping": {
      "password": "passwordHash",
      "confirmPassword": null
    },
    "validationRules": {
      "password": {
        "minLength": 8,
        "pattern": "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d@$!%*#?&]{8,}$",
        "errorMessage": "Password must be 8+ chars with letter and number"
      },
      "confirmPassword": {
        "matches": "password",
        "errorMessage": "Passwords must match"
      }
    }
  },
  
  "nodes": [
    // ... existing nodes
  ]
}
```

---

### Step 7: Verify End-to-End (30 minutes)

**Start backend:**
```bash
cd app-bana-service
mvn clean package -DskipTests
java -jar target/app-bana-1.0-SNAPSHOT-fat.jar
```

**Test with curl:**
```bash
# Test 1: Valid signup (should succeed)
curl -X POST http://localhost:8080/api/user \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "John",
    "lastName": "Doe",
    "email": "john@example.com",
    "phone": "+1234567890",
    "password": "SecurePass123",
    "confirmPassword": "SecurePass123"
  }'

# Expected: 201 Created with user data (NO passwordHash in response)

# Test 2: Check database (password should be hashed)
# Open H2 console or query database
# SELECT passwordHash FROM user WHERE email = 'john@example.com'
# Should start with: $2a$10$
```

**Verify:**
- ✅ User created successfully
- ✅ Password is hashed (starts with `$2a$10$`)
- ✅ Plain password NOT in database
- ✅ Response does NOT include `passwordHash`
- ✅ `confirmPassword` NOT in database

---

## 📊 Day 1 Success Metrics

After completing Day 1, you should have:

- ✅ **6 passing tests** (`PasswordServiceTest`)
- ✅ **Password hashing works** (BCrypt with salt)
- ✅ **Field mapping works** (password → passwordHash)
- ✅ **confirmPassword excluded** (not in DB)
- ✅ **Validation works** (weak passwords rejected)
- ✅ **End-to-end tested** (curl requests succeed)

**Coverage:**
- Backend: `PasswordService` + `ApiServer` password logic
- Tests: 6 unit tests
- Integration: Manual curl testing

---

## 📅 Next Steps (Day 2-3)

### Day 2: Story 1.1 Integration Tests
- Create `UserApiTest.java` (3 integration tests)
- Create `FormComponent.ts` (frontend component skeleton)
- Create `FormComponent.test.ts` (6 frontend tests)

### Day 3: Story 1.2 CSRF Protection
- Create `CsrfService.java` + tests
- Create `SecurityMiddleware.java` + tests
- Add GET /api/csrf-token endpoint
- Update FormComponent with CSRF logic

**Full roadmap:** See `IMPLEMENTATION_FILE_STRUCTURE.md`

---

## 🆘 Need Help?

### Documentation References

1. **Architecture:** `docs/ENTITY_FORM_BINDING_ARCHITECTURE.md`
2. **User Stories:** `docs/ENTITY_FORM_BINDING_STORIES.md`
3. **Test Code:** `docs/ENTITY_FORM_BINDING_TEST_PLAN.md`
4. **File Structure:** `docs/IMPLEMENTATION_FILE_STRUCTURE.md`

### Common Issues

**Problem:** `mvn test` fails with "class not found"
**Solution:** Run `mvn clean compile` first

**Problem:** BCrypt import not found
**Solution:** Add BCrypt dependency to pom.xml (see Step 1)

**Problem:** Tests compile but fail
**Solution:** Check test fixtures are created correctly

**Problem:** Password still plain text in DB
**Solution:** Verify ApiServer update in Step 5

---

## ✅ Ready to Start!

**Commands to run RIGHT NOW:**

```bash
# 1. Install dependencies
cd app-bana-service
mvn clean install

cd ../app-bana-ui
npm install

# 2. Verify foundation
cd ../app-bana-service
mvn test  # Should find 0 tests (none written yet)

# 3. Start implementing Story 1.1
# Create PasswordService.java (see Step 2 above)
# Copy test code from ENTITY_FORM_BINDING_TEST_PLAN.md
# Run: mvn test -Dtest=PasswordServiceTest
```

**Time estimate:** 2-4 hours for Day 1  
**Expected result:** 6 passing tests + working password hashing

🚀 **Let's code!**
