# Entity Form Binding - Implementation File Structure

**Created:** December 30, 2025  
**Status:** 🎯 Ready for Implementation  
**Purpose:** Complete file structure with scaffolding for all 9 stories

---

## 📁 Backend File Structure

### Core Services (Sprint 1 - Week 1)

```
app-bana-service/src/main/java/com/appbana/
├── service/
│   ├── PasswordService.java               [Story 1.1 - NEW]
│   ├── CsrfService.java                   [Story 1.2 - NEW]
│   ├── RateLimitService.java              [Story 1.3 - NEW]
│   ├── ValidationService.java             [Story 1.4 - NEW]
│   ├── TransactionService.java            [Story 3.1 - NEW]
│   └── FileUploadService.java             [Story 3.2 - NEW]
│
├── middleware/
│   ├── SecurityMiddleware.java            [Story 1.2 - NEW]
│   └── RateLimitMiddleware.java           [Story 1.3 - NEW]
│
├── model/
│   ├── User.java                          [EXISTS - UPDATE]
│   ├── ValidationResult.java              [Story 1.1 - NEW]
│   └── UploadedFile.java                  [Story 3.2 - NEW]
│
└── ApiServer.java                         [EXISTS - UPDATE]
```

### Test Files (Backend)

```
app-bana-service/src/test/java/com/appbana/
├── test/
│   └── TestFixtures.java                  [NEW - Test Data]
│
├── service/
│   ├── PasswordServiceTest.java           [Story 1.1 - NEW]
│   ├── CsrfServiceTest.java               [Story 1.2 - NEW]
│   ├── RateLimitServiceTest.java          [Story 1.3 - NEW]
│   ├── ValidationServiceTest.java         [Story 1.4 - NEW]
│   ├── TransactionServiceTest.java        [Story 3.1 - NEW]
│   └── FileUploadServiceTest.java         [Story 3.2 - NEW]
│
└── integration/
    ├── UserApiTest.java                   [Story 1.1 - NEW]
    ├── CsrfMiddlewareTest.java            [Story 1.2 - NEW]
    ├── RateLimitMiddlewareTest.java       [Story 1.3 - NEW]
    ├── ValidationApiTest.java             [Story 1.4 - NEW]
    ├── TransactionApiTest.java            [Story 3.1 - NEW]
    └── TestDatabase.java                  [NEW - Test Helper]
```

---

## 📁 Frontend File Structure

### Core Components (Sprint 1-2)

```
app-bana-ui/src/
├── components/
│   ├── FormComponent.ts                   [Story 1.1-1.5 - NEW]
│   ├── FormComponent.css                  [Story 1.1-1.5 - NEW]
│   ├── FileUploadComponent.ts             [Story 3.2 - NEW]
│   └── FileUploadComponent.css            [Story 3.2 - NEW]
│
└── test/
    └── fixtures/
        └── test-fixtures.ts               [NEW - Test Data]
```

### Test Files (Frontend)

```
app-bana-ui/src/components/
├── FormComponent.test.ts                  [Story 1.1 - NEW]
├── FormComponent.csrf.test.ts             [Story 1.2 - NEW]
├── FormComponent.validation.test.ts       [Story 1.4 - NEW]
├── FormComponent.a11y.test.ts             [Story 1.5 - NEW]
├── FormComponent.loading.test.ts          [Story 2.1 - NEW]
├── FormComponent.progressive-validation.test.ts [Story 2.2 - NEW]
└── FileUpload.test.ts                     [Story 3.2 - NEW]
```

### E2E Tests

```
app-bana-ui/tests/e2e/
└── form-accessibility.e2e.test.ts         [Story 1.5 - NEW]
```

---

## 🏗️ Implementation Order

### **Phase 1: Foundation (Day 1-2)**

**Goal:** Set up test infrastructure and shared utilities

1. **Create Test Fixtures**
   ```bash
   # Backend
   app-bana-service/src/test/java/com/appbana/test/TestFixtures.java
   app-bana-service/src/test/java/com/appbana/integration/TestDatabase.java
   
   # Frontend
   app-bana-ui/src/test/fixtures/test-fixtures.ts
   ```

2. **Add Test Dependencies**
   ```xml
   <!-- Backend: pom.xml -->
   <dependency>
     <groupId>org.mindrot</groupId>
     <artifactId>jbcrypt</artifactId>
     <version>0.4</version>
   </dependency>
   <dependency>
     <groupId>org.junit.jupiter</groupId>
     <artifactId>junit-jupiter</artifactId>
     <version>5.9.3</version>
     <scope>test</scope>
   </dependency>
   ```
   
   ```json
   // Frontend: package.json
   {
     "devDependencies": {
       "@testing-library/lit": "^1.0.0",
       "@open-wc/testing": "^4.0.0",
       "axe-playwright": "^2.0.0"
     }
   }
   ```

---

### **Phase 2: Sprint 1 - Story 1.1 (Password Security)**

**Files to Create (Order):**

1. **Backend Service**
   ```
   ✅ Create: app-bana-service/src/main/java/com/appbana/service/PasswordService.java
   ✅ Create: app-bana-service/src/main/java/com/appbana/model/ValidationResult.java
   ✅ Create test: app-bana-service/src/test/java/com/appbana/service/PasswordServiceTest.java
   ```

2. **Update ApiServer**
   ```
   ✅ Modify: app-bana-service/src/main/java/com/appbana/ApiServer.java
      - Add field mapping logic in POST /api/user
      - Hash password before insert
      - Exclude confirmPassword from entity
   ```

3. **Integration Tests**
   ```
   ✅ Create: app-bana-service/src/test/java/com/appbana/integration/UserApiTest.java
   ```

4. **Frontend Component**
   ```
   ✅ Create: app-bana-ui/src/components/FormComponent.ts
   ✅ Create: app-bana-ui/src/components/FormComponent.css
   ✅ Create test: app-bana-ui/src/components/FormComponent.test.ts
   ```

5. **Update signup.json Template**
   ```
   ✅ Modify: resources/page-templates/signup.json
      - Add fieldMapping: { "password": "passwordHash", "confirmPassword": null }
      - Add validationRules for password strength
   ```

**Verification:**
```bash
# Backend
mvn test -Dtest=PasswordServiceTest
mvn test -Dtest=UserApiTest

# Frontend
npm test -- FormComponent.test.ts
```

---

### **Phase 3: Sprint 1 - Story 1.2 (CSRF Protection)**

**Files to Create:**

1. **Backend Services**
   ```
   ✅ Create: app-bana-service/src/main/java/com/appbana/service/CsrfService.java
   ✅ Create: app-bana-service/src/main/java/com/appbana/middleware/SecurityMiddleware.java
   ✅ Create test: app-bana-service/src/test/java/com/appbana/service/CsrfServiceTest.java
   ✅ Create test: app-bana-service/src/test/java/com/appbana/integration/CsrfMiddlewareTest.java
   ```

2. **Update ApiServer**
   ```
   ✅ Modify: ApiServer.java
      - Add GET /api/csrf-token endpoint
      - Add CSRF validation middleware to POST routes
   ```

3. **Frontend Component**
   ```
   ✅ Update: FormComponent.ts
      - Fetch CSRF token on mount
      - Inject token as hidden input
      - Include token in form submission headers
   ✅ Create test: app-bana-ui/src/components/FormComponent.csrf.test.ts
   ```

**Verification:**
```bash
mvn test -Dtest=CsrfServiceTest,CsrfMiddlewareTest
npm test -- FormComponent.csrf.test.ts
```

---

### **Phase 4: Sprint 1 - Story 1.3 (Rate Limiting)**

**Files to Create:**

```
✅ Create: app-bana-service/src/main/java/com/appbana/service/RateLimitService.java
✅ Create: app-bana-service/src/main/java/com/appbana/middleware/RateLimitMiddleware.java
✅ Create test: app-bana-service/src/test/java/com/appbana/service/RateLimitServiceTest.java
✅ Create test: app-bana-service/src/test/java/com/appbana/integration/RateLimitMiddlewareTest.java
✅ Update: ApiServer.java (add rate limit middleware)
```

**Verification:**
```bash
mvn test -Dtest=RateLimitServiceTest,RateLimitMiddlewareTest
```

---

### **Phase 5: Sprint 1 - Story 1.4 (Validation Feedback)**

**Files to Create:**

```
✅ Create: app-bana-service/src/main/java/com/appbana/service/ValidationService.java
✅ Create test: app-bana-service/src/test/java/com/appbana/service/ValidationServiceTest.java
✅ Create test: app-bana-service/src/test/java/com/appbana/integration/ValidationApiTest.java
✅ Update: FormComponent.ts (error state management, error display)
✅ Create test: app-bana-ui/src/components/FormComponent.validation.test.ts
```

---

### **Phase 6: Sprint 1 - Story 1.5 (Accessibility)**

**Files to Create:**

```
✅ Update: FormComponent.ts (ARIA attributes, label linking, keyboard navigation)
✅ Create test: app-bana-ui/src/components/FormComponent.a11y.test.ts
✅ Create test: app-bana-ui/tests/e2e/form-accessibility.e2e.test.ts
```

**Install E2E Dependencies:**
```bash
npm install -D @playwright/test axe-playwright
npx playwright install
```

---

### **Phase 7: Sprint 2 - Story 2.1 (Loading States)**

**Files to Create:**

```
✅ Update: FormComponent.ts (loading states, button text, spinner)
✅ Create test: app-bana-ui/src/components/FormComponent.loading.test.ts
```

---

### **Phase 8: Sprint 2 - Story 2.2 (Progressive Validation)**

**Files to Create:**

```
✅ Update: FormComponent.ts (blur/change validation, async checks)
✅ Create test: app-bana-ui/src/components/FormComponent.progressive-validation.test.ts
```

---

### **Phase 9: Sprint 3 - Story 3.1 (Transactions)**

**Files to Create:**

```
✅ Create: app-bana-service/src/main/java/com/appbana/service/TransactionService.java
✅ Create test: app-bana-service/src/test/java/com/appbana/service/TransactionServiceTest.java
✅ Create test: app-bana-service/src/test/java/com/appbana/integration/TransactionApiTest.java
✅ Update: ApiServer.java (wrap user creation in transaction)
```

---

### **Phase 10: Sprint 3 - Story 3.2 (File Upload)**

**Files to Create:**

```
✅ Create: app-bana-service/src/main/java/com/appbana/service/FileUploadService.java
✅ Create: app-bana-service/src/main/java/com/appbana/model/UploadedFile.java
✅ Create test: app-bana-service/src/test/java/com/appbana/service/FileUploadServiceTest.java
✅ Create: app-bana-ui/src/components/FileUploadComponent.ts
✅ Create: app-bana-ui/src/components/FileUploadComponent.css
✅ Create test: app-bana-ui/src/components/FileUpload.test.ts
✅ Update: ApiServer.java (add POST /api/upload endpoint)
```

---

## 📊 File Creation Summary

| Category | Files to Create | Files to Modify | Total |
|----------|----------------|----------------|-------|
| **Backend Services** | 8 | 1 (ApiServer) | 9 |
| **Backend Tests** | 13 | 0 | 13 |
| **Frontend Components** | 3 | 0 | 3 |
| **Frontend Tests** | 8 | 0 | 8 |
| **Test Fixtures** | 3 | 0 | 3 |
| **Total** | **35** | **1** | **36** |

---

## 🚀 Quick Start Commands

### Backend Setup

```bash
cd app-bana-service

# Create directory structure
mkdir -p src/main/java/com/appbana/service
mkdir -p src/main/java/com/appbana/middleware
mkdir -p src/test/java/com/appbana/service
mkdir -p src/test/java/com/appbana/integration
mkdir -p src/test/java/com/appbana/test

# Install BCrypt dependency
# Add to pom.xml (see Phase 1)
mvn clean install
```

### Frontend Setup

```bash
cd app-bana-ui

# Create directory structure
mkdir -p src/test/fixtures
mkdir -p tests/e2e

# Install test dependencies
npm install --save-dev @testing-library/lit @open-wc/testing
npm install --save-dev @playwright/test axe-playwright

# Initialize Playwright
npx playwright install
```

---

## 🎯 Implementation Checklist

### Sprint 1 (Week 1)

- [ ] **Day 1: Foundation**
  - [ ] Create test fixtures (backend + frontend)
  - [ ] Set up TestDatabase helper
  - [ ] Install all dependencies
  - [ ] Verify test infrastructure works

- [ ] **Day 2-3: Story 1.1 (Password Security)**
  - [ ] Create PasswordService + tests (6 tests)
  - [ ] Update ApiServer with field mapping
  - [ ] Create UserApiTest (3 integration tests)
  - [ ] Create FormComponent with basic structure
  - [ ] Create FormComponent.test.ts (6 tests)
  - [ ] Verify: `mvn test` + `npm test` all pass

- [ ] **Day 4: Story 1.2 (CSRF Protection)**
  - [ ] Create CsrfService + tests (5 tests)
  - [ ] Create SecurityMiddleware + tests (4 tests)
  - [ ] Add GET /api/csrf-token endpoint
  - [ ] Update FormComponent with CSRF logic
  - [ ] Create FormComponent.csrf.test.ts (4 tests)

- [ ] **Day 5: Story 1.3 (Rate Limiting)**
  - [ ] Create RateLimitService + tests (6 tests)
  - [ ] Create RateLimitMiddleware + tests (2 tests)
  - [ ] Add rate limit headers to responses
  - [ ] Verify rate limiting works end-to-end

### Sprint 1 (Week 2)

- [ ] **Day 6: Story 1.4 (Validation Feedback)**
  - [ ] Create ValidationService + tests (5 tests)
  - [ ] Create ValidationApiTest (2 tests)
  - [ ] Update FormComponent with error display
  - [ ] Create FormComponent.validation.test.ts (5 tests)

- [ ] **Day 7: Story 1.5 (Accessibility)**
  - [ ] Add ARIA attributes to FormComponent
  - [ ] Create FormComponent.a11y.test.ts (6 tests)
  - [ ] Create form-accessibility.e2e.test.ts (4 tests)
  - [ ] Run Lighthouse audit (target: ≥90)
  - [ ] Run axe-core scan (target: 0 violations)

### Sprint 2 (Week 3)

- [ ] **Day 8: Story 2.1 (Loading States)**
  - [ ] Update FormComponent with loading UI
  - [ ] Create FormComponent.loading.test.ts (6 tests)

- [ ] **Day 9: Story 2.2 (Progressive Validation)**
  - [ ] Implement blur/change validation strategy
  - [ ] Add async validation support
  - [ ] Create FormComponent.progressive-validation.test.ts (6 tests)

### Sprint 3 (Month 2)

- [ ] **Week 4: Story 3.1 (Transactions)**
  - [ ] Create TransactionService + tests (3 tests)
  - [ ] Create TransactionApiTest (1 test)
  - [ ] Wrap user creation in transaction
  - [ ] Test rollback scenarios

- [ ] **Week 5: Story 3.2 (File Upload)**
  - [ ] Create FileUploadService + tests (4 tests)
  - [ ] Create FileUploadComponent + tests (5 tests)
  - [ ] Add POST /api/upload endpoint
  - [ ] Test file type validation, size limits

---

## 📝 Key Files Reference

### Must-Read Before Implementation

1. **[ENTITY_FORM_BINDING_ARCHITECTURE.md](./ENTITY_FORM_BINDING_ARCHITECTURE.md)**
   - System architecture diagram
   - All 10 critical issues with solutions
   - Implementation checklist

2. **[ENTITY_FORM_BINDING_STORIES.md](./ENTITY_FORM_BINDING_STORIES.md)**
   - 9 user stories with acceptance criteria
   - 38 Gherkin scenarios
   - Technical tasks per story

3. **[ENTITY_FORM_BINDING_TEST_PLAN.md](./ENTITY_FORM_BINDING_TEST_PLAN.md)**
   - Complete test code (100+ test cases)
   - Test fixtures and mock data
   - Test commands and coverage requirements

---

## 🎬 Next Actions

1. **Create Foundation Files First:**
   ```bash
   # Copy test fixtures from TEST_PLAN.md
   # Create TestFixtures.java
   # Create test-fixtures.ts
   # Create TestDatabase.java helper
   ```

2. **Verify Test Infrastructure:**
   ```bash
   mvn test  # Should find 0 tests (no tests yet)
   npm test  # Should pass with 0 tests
   ```

3. **Start Story 1.1 Implementation:**
   ```bash
   # Create PasswordService.java
   # Copy test code from TEST_PLAN.md
   # Run tests: mvn test -Dtest=PasswordServiceTest
   ```

**Ready to proceed?** Start with foundation files or jump straight to Story 1.1?
