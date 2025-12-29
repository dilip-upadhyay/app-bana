# 🚀 START HERE - Implementation Guide

**Created:** December 30, 2025  
**Status:** ✅ Ready to Code  
**Sprint Start:** Day 1

---

## ⚠️ CRITICAL: Always Check Current Status First

**BEFORE STARTING ANY WORK** (even in new sessions), run these commands:

```bash
# Navigate to project root
cd /Users/dilipupadhyay/github/app-bana

# 1. Check which files already exist
echo "=== Checking existing implementation files ==="
find . -type f \( -name "PasswordService.java" -o -name "PasswordServiceTest.java" \
  -o -name "CsrfService.java" -o -name "RateLimitService.java" \
  -o -name "ValidationService.java" -o -name "TransactionService.java" \
  -o -name "FileUploadService.java" -o -name "SecurityMiddleware.java" \) 2>/dev/null

# 2. Check if dependencies are installed
echo -e "\n=== Checking backend dependencies ==="
cd app-bana-service
mvn dependency:tree 2>/dev/null | grep -E "(bcrypt|junit|h2)" || echo "Dependencies not installed"

echo -e "\n=== Checking frontend dependencies ==="
cd ../app-bana-ui
npm list 2>/dev/null | grep -E "(@testing-library|playwright|axe)" || echo "Dependencies not installed"

# 3. Check test status
echo -e "\n=== Checking backend test status ==="
cd ../app-bana-service
mvn test 2>&1 | grep -E "(Tests run:|Failures:|Errors:|BUILD SUCCESS|BUILD FAILURE)" | tail -5

echo -e "\n=== Checking frontend test status ==="
cd ../app-bana-ui
npm test -- --run 2>&1 | grep -E "(Test Files|Tests|PASS|FAIL)" | tail -5

# 4. Check for TODO/FIXME comments (indicates incomplete work)
echo -e "\n=== Checking for incomplete work ==="
cd ..
grep -r "TODO\|FIXME\|Story 1\." app-bana-service/src/main/java/com/appbana/service/ 2>/dev/null | head -5 || echo "No TODOs found"

# 5. Check git status
echo -e "\n=== Git status ==="
git status --short | head -10
```

**Use this output to determine:**

| What You See | What It Means | What To Do |
|--------------|---------------|------------|
| ❌ PasswordService.java not found | Not started yet | Follow Day 1 guide below |
| ✅ PasswordService.java exists, 6 tests pass | Day 1 complete | Skip to Day 2-3 |
| ✅ PasswordService.java exists, 0 tests pass | Created but not tested | Debug tests, then continue |
| ❌ bcrypt not in dependencies | Dependencies not installed | Run Step 1 first |
| ✅ CsrfService.java exists | Working on Story 1.2 | Continue with CSRF |
| ✅ RateLimitService.java exists | Working on Story 1.3 | Continue with rate limiting |
| ⚠️ Tests run: 15, Failures: 3 | Some tests failing | Fix failures before continuing |

**Decision Tree:**
```
START
  ↓
Are foundation files present? (TestFixtures, ValidationResult, etc.)
  ├─ NO → Create them first (see "Foundation Files" section)
  └─ YES → Continue
      ↓
Are dependencies installed? (BCrypt, JUnit, @testing-library/lit)
  ├─ NO → Run Step 1: Add Dependencies
  └─ YES → Continue
      ↓
Does PasswordService.java exist?
  ├─ NO → Start Day 1 (Step 2)
  ├─ YES → Do PasswordServiceTest tests pass?
      ├─ NO → Debug tests before continuing
      └─ YES → Does CsrfService.java exist?
          ├─ NO → Start Story 1.2 (Day 4)
          └─ YES → Continue with next story...
```

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

---

## 🔄 Session Resume Checklist (For New Sessions)

**If you're starting a new coding session or AI agent session, ALWAYS run this checklist:**

### 1. Run Status Check Script (5 minutes)

```bash
cd /Users/dilipupadhyay/github/app-bana

# Create quick status check script
cat > check_status.sh << 'EOF'
#!/bin/bash
echo "=========================================="
echo "Entity Form Binding - Implementation Status"
echo "=========================================="

# Story completion status
echo -e "\n📋 STORY COMPLETION STATUS:"
echo "Story 1.1 (Password Security):"
[[ -f "app-bana-service/src/main/java/com/appbana/service/PasswordService.java" ]] && echo "  ✅ PasswordService.java" || echo "  ❌ PasswordService.java"
[[ -f "app-bana-service/src/test/java/com/appbana/service/PasswordServiceTest.java" ]] && echo "  ✅ PasswordServiceTest.java" || echo "  ❌ PasswordServiceTest.java"

echo -e "\nStory 1.2 (CSRF Protection):"
[[ -f "app-bana-service/src/main/java/com/appbana/service/CsrfService.java" ]] && echo "  ✅ CsrfService.java" || echo "  ❌ CsrfService.java"
[[ -f "app-bana-service/src/main/java/com/appbana/middleware/SecurityMiddleware.java" ]] && echo "  ✅ SecurityMiddleware.java" || echo "  ❌ SecurityMiddleware.java"

echo -e "\nStory 1.3 (Rate Limiting):"
[[ -f "app-bana-service/src/main/java/com/appbana/service/RateLimitService.java" ]] && echo "  ✅ RateLimitService.java" || echo "  ❌ RateLimitService.java"

echo -e "\nStory 1.4 (Validation Feedback):"
[[ -f "app-bana-service/src/main/java/com/appbana/service/ValidationService.java" ]] && echo "  ✅ ValidationService.java" || echo "  ❌ ValidationService.java"

# Test status
echo -e "\n🧪 TEST STATUS:"
cd app-bana-service
TEST_OUTPUT=$(mvn test -q 2>&1 | grep -E "Tests run:|BUILD")
if [[ $? -eq 0 ]]; then
  echo "$TEST_OUTPUT"
else
  echo "  ⚠️ No tests run or build failed"
fi

# Dependency status
echo -e "\n📦 DEPENDENCIES:"
mvn dependency:tree -q 2>&1 | grep -q "bcrypt" && echo "  ✅ BCrypt installed" || echo "  ❌ BCrypt missing"
mvn dependency:tree -q 2>&1 | grep -q "junit-jupiter" && echo "  ✅ JUnit 5 installed" || echo "  ❌ JUnit 5 missing"

cd ../app-bana-ui
npm list @testing-library/lit >/dev/null 2>&1 && echo "  ✅ @testing-library/lit installed" || echo "  ❌ @testing-library/lit missing"
npm list playwright >/dev/null 2>&1 && echo "  ✅ Playwright installed" || echo "  ❌ Playwright missing"

# Git status
echo -e "\n📂 GIT STATUS:"
cd ..
git status --short | head -10

echo -e "\n=========================================="
echo "Use this information to pick up where you left off!"
echo "=========================================="
EOF

chmod +x check_status.sh
./check_status.sh
```

### 2. Interpret Status Output

**Scenario A: Fresh Start (Nothing exists)**
```
❌ PasswordService.java
❌ PasswordServiceTest.java
❌ BCrypt missing
```
→ **Action:** Follow Day 1 guide from Step 1 (Add Dependencies)

**Scenario B: Dependencies installed, no code**
```
✅ BCrypt installed
✅ JUnit 5 installed
❌ PasswordService.java
```
→ **Action:** Skip Step 1, start at Step 2 (Create PasswordService.java)

**Scenario C: PasswordService exists, tests fail**
```
✅ PasswordService.java
✅ PasswordServiceTest.java
Tests run: 6, Failures: 3, Errors: 0
```
→ **Action:** Debug failing tests, check:
  - Is BCrypt imported correctly? (`import at.favre.lib.crypto.bcrypt.BCrypt;`)
  - Is ValidationResult in correct package? (`com.appbana.model`)
  - Are test methods public and annotated with `@Test`?

**Scenario D: Story 1.1 complete, ready for 1.2**
```
✅ PasswordService.java
✅ PasswordServiceTest.java
Tests run: 6, Failures: 0
❌ CsrfService.java
```
→ **Action:** Skip to Day 4 (Story 1.2 - CSRF Protection)

**Scenario E: Multiple stories started**
```
✅ PasswordService.java (6 tests pass)
✅ CsrfService.java (5 tests pass)
⚠️ RateLimitService.java (2 tests fail)
```
→ **Action:** Fix RateLimitService tests, then continue with Story 1.4

### 3. Update This Document

After each major milestone, update the checklist:

```bash
# Add TODO comments to track progress
echo "// TODO: Story 1.1 complete - $(date)" >> notes.txt
echo "// NEXT: Story 1.2 CSRF Protection" >> notes.txt
```

### 4. Common Resume Issues

**Issue:** "I forgot which story I was working on"
- **Solution:** Run `check_status.sh` script above
- **Prevention:** Add TODO comments in code with story numbers

**Issue:** "Tests were passing, now they fail"
- **Solution:** Check git diff: `git diff HEAD`
- **Check:** Was ApiServer.java modified? Revert changes and test again

**Issue:** "New session, AI agent doesn't know progress"
- **Solution:** Share output of `check_status.sh` with agent
- **Prevention:** This document now includes status check instructions

**Issue:** "Can't remember which dependencies to install"
- **Solution:** Check `check_status.sh` dependency section
- **Alternative:** See Step 1 in Day 1 guide above

### 5. Quick Commands Reference

```bash
# Check test status for specific story
mvn test -Dtest=PasswordServiceTest  # Story 1.1
mvn test -Dtest=CsrfServiceTest      # Story 1.2
mvn test -Dtest=RateLimitServiceTest # Story 1.3

# Run all backend tests
cd app-bana-service && mvn test

# Run all frontend tests
cd app-bana-ui && npm test

# Check code coverage
cd app-bana-service && mvn clean test jacoco:report
open target/site/jacoco/index.html

# View test plan for specific story
grep -A 50 "Story 1.1" docs/ENTITY_FORM_BINDING_TEST_PLAN.md
```

---

🚀 **Let's code!**
