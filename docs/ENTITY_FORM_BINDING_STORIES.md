# Entity Form Binding - User Stories & Acceptance Criteria

**Created:** December 30, 2025  
**Status:** 🔴 ACTIVE - Complete Test Coverage  
**Related:** [ENTITY_FORM_BINDING_ARCHITECTURE.md](./ENTITY_FORM_BINDING_ARCHITECTURE.md)

---

## 📋 Epic Overview

**Epic:** Secure, Metadata-Driven Entity-Form Binding  
**Goal:** Enable no-code users to create fully functional, secure forms that auto-bind to database entities  
**Business Value:** $500K+ ARR unlock (Healthcare/Finance compliance ready)

---

## 🎯 Sprint 1: Security & Core Functionality (Week 1)

### Story 1.1: Password Field Security 🔴 CRITICAL

**As a** platform user  
**I want** my password to be securely hashed  
**So that** even if the database is compromised, passwords remain protected

#### Acceptance Criteria

**Scenario 1.1.1: Password is hashed before database storage**
```gherkin
Given I am on the signup form
When I enter password "MySecurePass123"
And I submit the form
Then the password should be hashed using BCrypt
And the database should contain a hash starting with "$2a$10$"
And the database should NOT contain "MySecurePass123" in plain text
```

**Scenario 1.1.2: Password field maps to passwordHash entity field**
```gherkin
Given the signup form has fieldMapping: { "password": "passwordHash" }
When I submit the form with password "Test123"
Then the form data should be transformed before database insert
And the "password" key should be removed from the request
And the "passwordHash" key should contain the BCrypt hash
```

**Scenario 1.1.3: confirmPassword is excluded from entity**
```gherkin
Given the signup form has fieldMapping: { "confirmPassword": null }
When I submit the form with confirmPassword "Test123"
Then the confirmPassword field should NOT be sent to backend
And the user entity should NOT have a confirmPassword column
```

**Scenario 1.1.4: Password strength validation**
```gherkin
Given the form has validationRule: minLength=8, pattern with letter+digit
When I enter password "weak"
Then I should see error "Password must be 8+ chars with letter and number"
And the submit button should be disabled
When I enter password "StrongPass123"
Then the error should disappear
And the submit button should be enabled
```

**Scenario 1.1.5: Password confirmation matching**
```gherkin
Given the form has validationRule: confirmPassword matches password
When I enter password "Test123" and confirmPassword "Test456"
Then I should see error "Passwords must match"
When I change confirmPassword to "Test123"
Then the error should disappear
```

#### Technical Tasks
- [ ] Add `fieldMapping` to signup.json form props
- [ ] Add `validationRules` for password (minLength, pattern)
- [ ] Add `validationRules` for confirmPassword (matches)
- [ ] Create `PasswordService.java` with BCrypt hashing
- [ ] Update `ApiServer.java` POST /api/user to hash password
- [ ] Update entity schema: `password` → `passwordHash`
- [ ] Write unit test: `testPasswordHashing()`
- [ ] Write integration test: `testSignupWithPassword()`

#### Definition of Done
- [ ] All 5 scenarios pass
- [ ] Code review approved
- [ ] Unit tests pass (coverage >80%)
- [ ] Integration test passes
- [ ] Security audit: No plain-text passwords in logs/DB
- [ ] Documentation updated

**Story Points:** 8  
**Priority:** P0 - CRITICAL

---

### Story 1.2: CSRF Protection 🔴 CRITICAL

**As a** security engineer  
**I want** all forms to include CSRF tokens  
**So that** cross-site request forgery attacks are prevented

#### Acceptance Criteria

**Scenario 1.2.1: CSRF token auto-injected in form**
```gherkin
Given the form has security.csrfToken = true
When the form loads in the browser
Then a hidden input field named "_csrf" should be present
And the CSRF token should be fetched from the server
And the token should match the user's session
```

**Scenario 1.2.2: Form submission includes CSRF token**
```gherkin
Given I am on the signup form
When I submit the form
Then the request should include header "X-CSRF-Token"
And the token value should match the session token
```

**Scenario 1.2.3: Backend validates CSRF token**
```gherkin
Given a form submission with valid CSRF token
When the backend receives the request
Then the request should be processed successfully
Given a form submission with invalid CSRF token
When the backend receives the request
Then the request should be rejected with 403 Forbidden
And the response should contain error "Invalid CSRF token"
```

**Scenario 1.2.4: Token expiration handling**
```gherkin
Given I have a form open for 30 minutes
When the CSRF token expires
And I submit the form
Then I should see error "Session expired. Please refresh the page."
And the form should NOT submit
```

#### Technical Tasks
- [ ] Create `CsrfService.java` with token generation
- [ ] Add CSRF token endpoint: GET /api/csrf-token
- [ ] Update `FormComponent.ts` to fetch and inject token
- [ ] Add CSRF validation middleware in `ApiServer.java`
- [ ] Add `security.csrfToken` prop to signup.json
- [ ] Write unit test: `testCsrfTokenGeneration()`
- [ ] Write integration test: `testCsrfValidation()`

#### Definition of Done
- [ ] All 4 scenarios pass
- [ ] OWASP CSRF guidelines followed
- [ ] Token rotation on login/logout
- [ ] Tests pass

**Story Points:** 5  
**Priority:** P0 - CRITICAL

---

### Story 1.3: Rate Limiting 🔴 CRITICAL

**As a** system administrator  
**I want** signup forms to be rate-limited  
**So that** brute force and spam attacks are prevented

#### Acceptance Criteria

**Scenario 1.3.1: Rate limit enforced per IP**
```gherkin
Given the form has security.rateLimit = { maxAttempts: 5, windowMinutes: 1 }
When I submit the signup form 5 times from IP 192.168.1.100
Then all 5 submissions should be processed
When I submit a 6th time within 1 minute
Then the request should be rejected with 429 Too Many Requests
And I should see error "Too many signup attempts. Try again in 1 minute."
```

**Scenario 1.3.2: Rate limit resets after window expires**
```gherkin
Given I have exceeded rate limit (5 attempts in 1 minute)
When I wait for 1 minute
And I submit the form again
Then the request should be accepted
And the rate limit counter should reset
```

**Scenario 1.3.3: Rate limit per endpoint**
```gherkin
Given I have reached rate limit for /api/user (signup)
When I access /api/login endpoint
Then the request should be allowed
Because rate limits are per-endpoint, not global
```

**Scenario 1.3.4: Client-side warning before hard limit**
```gherkin
Given the form has rate limit of 5 attempts
When I submit the form 3 times unsuccessfully
Then I should see warning "2 attempts remaining (resets in 45 seconds)"
```

#### Technical Tasks
- [ ] Create `RateLimitService.java` with IP-based tracking
- [ ] Use in-memory map with TTL or Redis for distributed systems
- [ ] Add rate limit middleware in `ApiServer.java`
- [ ] Add `security.rateLimit` prop to signup.json
- [ ] Update `FormComponent.ts` to show remaining attempts
- [ ] Write unit test: `testRateLimitEnforcement()`
- [ ] Write integration test: `testRateLimitReset()`

#### Definition of Done
- [ ] All 4 scenarios pass
- [ ] Rate limits configurable per form
- [ ] Distributed rate limiting works (future: Redis)
- [ ] Tests pass

**Story Points:** 5  
**Priority:** P0 - CRITICAL

---

### Story 1.4: Validation Error Feedback 🟡 HIGH

**As a** form user  
**I want** to see clear error messages for invalid fields  
**So that** I can correct my mistakes and submit successfully

#### Acceptance Criteria

**Scenario 1.4.1: Backend validation errors displayed**
```gherkin
Given I submit a signup form with email "john@example.com" (already exists)
When the backend returns validationErrors: { "email": "Email already exists" }
Then the email input field should show red border
And the error message "Email already exists" should appear below the field
And the helper text should be replaced with the error
```

**Scenario 1.4.2: Multiple errors displayed simultaneously**
```gherkin
Given I submit a form with:
  - email: "invalid-email" (invalid format)
  - password: "123" (too short)
When the backend returns validation errors for both fields
Then both email and password fields should show red borders
And both error messages should be visible
```

**Scenario 1.4.3: First error field receives focus**
```gherkin
Given I submit a form with 3 validation errors (firstName, email, password)
When the errors are displayed
Then the first error field (firstName) should receive keyboard focus
And the page should scroll to that field if not visible
```

**Scenario 1.4.4: Error clears when field is corrected**
```gherkin
Given the email field shows error "Email already exists"
When I change the email to "newemail@example.com"
And I blur the field (tab away)
Then the error message should disappear
And the red border should be removed
```

**Scenario 1.4.5: Error persists if still invalid**
```gherkin
Given the email field shows error "Invalid email format"
When I change the email to "still-invalid" (no @ symbol)
And I blur the field
Then the error should remain
Or update to new error if different validation rule fails
```

#### Technical Tasks
- [ ] Add `error` and `aria-invalid` bindings to all inputs in signup.json
- [ ] Update `FormComponent.ts` with validationErrors state
- [ ] Add error display logic in input components
- [ ] Add focus management after error response
- [ ] Update `Renderer.ts` to support `${}` template expressions
- [ ] Write unit test: `testErrorBinding()`
- [ ] Write E2E test: `testValidationErrorDisplay()`

#### Definition of Done
- [ ] All 5 scenarios pass
- [ ] Errors visible and accessible (screen readers)
- [ ] Focus management works
- [ ] Tests pass

**Story Points:** 5  
**Priority:** P1 - HIGH

---

### Story 1.5: Accessibility (WCAG 2.1 AA) 🟡 HIGH

**As a** user with disabilities  
**I want** forms to be fully accessible  
**So that** I can use screen readers and keyboard navigation

#### Acceptance Criteria

**Scenario 1.5.1: Labels linked to inputs**
```gherkin
Given the signup form is rendered
Then every input should have a corresponding <label> element
And the label's "for" attribute should match the input's "id"
When I click on the label "First Name"
Then the first name input field should receive focus
```

**Scenario 1.5.2: Required fields announced**
```gherkin
Given the email field is required
Then the input should have attribute aria-required="true"
And the screen reader should announce "Email, required, edit text"
```

**Scenario 1.5.3: Error states announced**
```gherkin
Given the email field has validation error "Email already exists"
Then the input should have attribute aria-invalid="true"
And the input should have attribute aria-describedby="email-error"
And the screen reader should announce "Email, invalid, Email already exists"
```

**Scenario 1.5.4: Helper text associated**
```gherkin
Given the password field has helperText "Min 8 characters"
Then the helper text should have unique id "password-helper"
And the input should have attribute aria-describedby="password-helper"
```

**Scenario 1.5.5: Keyboard navigation**
```gherkin
Given I am on the signup form
When I press Tab repeatedly
Then focus should move through fields in logical order:
  1. First Name
  2. Last Name
  3. Email
  4. Phone
  5. Password
  6. Confirm Password
  7. Submit button
And I should be able to submit using Enter or Space on button
```

**Scenario 1.5.6: Form validation announced to screen readers**
```gherkin
Given I submit an invalid form
When validation errors appear
Then the screen reader should announce "Form has 3 errors"
And focus should move to the first error field
```

#### Technical Tasks
- [ ] Add `id` prop to all inputs in signup.json
- [ ] Add `for` prop to all labels
- [ ] Add `aria-label`, `aria-required`, `aria-invalid` to inputs
- [ ] Add `aria-describedby` linking to helper text and errors
- [ ] Add `role="alert"` to error message containers
- [ ] Test with NVDA (Windows), VoiceOver (Mac), JAWS
- [ ] Run axe-core accessibility audit
- [ ] Write accessibility tests with @axe-core/playwright

#### Definition of Done
- [ ] All 6 scenarios pass
- [ ] Lighthouse accessibility score ≥90
- [ ] axe-core audit: 0 violations
- [ ] Manual screen reader testing passed
- [ ] Keyboard navigation works without mouse

**Story Points:** 3  
**Priority:** P1 - HIGH

---

## 🎯 Sprint 2: UX Polish & Client Validation (Week 2)

### Story 2.1: Loading States & Optimistic UI 🟡 MEDIUM

**As a** form user  
**I want** to see feedback when submitting  
**So that** I know the form is processing and not frozen

#### Acceptance Criteria

**Scenario 2.1.1: Button shows loading state**
```gherkin
Given I have filled out the signup form correctly
When I click the "Sign Up" button
Then the button text should change to "Creating Account..."
And a spinner icon should appear next to the text
And the button should be disabled during submission
```

**Scenario 2.1.2: Form fields disabled during submission**
```gherkin
Given I am submitting the form
When the form is in loading state
Then all input fields should be disabled
And I should NOT be able to edit any fields
```

**Scenario 2.1.3: Loading state clears on success**
```gherkin
Given the form is submitting
When the backend returns success response
Then the loading state should clear within 200ms
And I should be redirected to /dashboard
```

**Scenario 2.1.4: Loading state clears on error**
```gherkin
Given the form is submitting
When the backend returns validation errors
Then the loading state should clear
And the button should show "Sign Up" again
And the button should be enabled
And I should see the error messages
```

**Scenario 2.1.5: Prevent double submission**
```gherkin
Given I click the "Sign Up" button
When the form is submitting
And I click the button again rapidly (double click)
Then only ONE request should be sent to the backend
And subsequent clicks should be ignored
```

#### Technical Tasks
- [ ] Add `submitting` state to FormComponent
- [ ] Update button label with template expression: `${form.submitting ? 'Creating Account...' : 'Sign Up'}`
- [ ] Add `disabled` binding: `${form.submitting || !form.valid}`
- [ ] Create spinner component
- [ ] Add request deduplication logic
- [ ] Write unit test: `testLoadingState()`
- [ ] Write E2E test: `testDoubleSubmitPrevention()`

#### Definition of Done
- [ ] All 5 scenarios pass
- [ ] Loading state visible for minimum 300ms (UX)
- [ ] No double submissions
- [ ] Tests pass

**Story Points:** 3  
**Priority:** P2 - MEDIUM

---

### Story 2.2: Progressive Client-Side Validation 🟡 MEDIUM

**As a** form user  
**I want** immediate feedback on invalid fields  
**So that** I don't waste time submitting an invalid form

#### Acceptance Criteria

**Scenario 2.2.1: Validation on blur (first time)**
```gherkin
Given I am filling out the signup form
When I enter email "invalid-email" (no @ symbol)
And I tab to the next field (blur event)
Then I should see error "Invalid email format"
And the field should have a red border
```

**Scenario 2.2.2: No validation before first blur**
```gherkin
Given I am typing in the email field for the first time
When I type "inv" (incomplete email)
Then no error message should appear
Because validation only happens on blur, not while typing initially
```

**Scenario 2.2.3: Re-validation on change after error**
```gherkin
Given the email field shows error "Invalid email format"
When I start typing to correct it
Then the validation should re-run on every keystroke
And the error should disappear immediately when valid
```

**Scenario 2.2.4: Async validation (email uniqueness)**
```gherkin
Given I enter email "john@example.com"
When I blur the field
Then a debounced API call should check email uniqueness (wait 500ms)
When the backend returns "Email already exists"
Then I should see the error message
And the check should NOT happen on every keystroke (debounced)
```

**Scenario 2.2.5: Submit button disabled until form valid**
```gherkin
Given the form has 2 invalid fields (email, password)
Then the submit button should be disabled
And the button should show tooltip "Please fix errors before submitting"
When I correct both fields
Then the submit button should be enabled
```

**Scenario 2.2.6: All validations cleared on successful submit**
```gherkin
Given the form was previously submitted with errors
When I correct the errors and submit successfully
Then all error states should be cleared
And the form should reset to initial state
```

#### Technical Tasks
- [ ] Add `validationStrategy` prop to form: `{ validateOn: 'blur', revalidateOn: 'change' }`
- [ ] Implement blur event handlers in FormComponent
- [ ] Add debounced async validation for email uniqueness
- [ ] Add `touched` state tracking per field
- [ ] Add form-level `valid` computed property
- [ ] Bind submit button disabled to `!form.valid`
- [ ] Write unit test: `testProgressiveValidation()`
- [ ] Write E2E test: `testAsyncEmailValidation()`

#### Definition of Done
- [ ] All 6 scenarios pass
- [ ] Validation feels snappy (no lag)
- [ ] Async validation debounced properly
- [ ] Tests pass

**Story Points:** 5  
**Priority:** P2 - MEDIUM

---

## 🎯 Sprint 3: Advanced Features (Month 2)

### Story 3.1: Transaction Boundaries & Rollback 🟢 LOW

**As a** system administrator  
**I want** multi-step operations to be transactional  
**So that** partial failures don't leave data in inconsistent state

#### Acceptance Criteria

**Scenario 3.1.1: Successful multi-step operation**
```gherkin
Given the signup form has postActions:
  - createUser
  - sendWelcomeEmail
  - assignDefaultRole
When I submit a valid signup form
Then all 3 actions should execute in order
And the user should exist in database
And the welcome email should be sent
And the user should have role "customer"
```

**Scenario 3.1.2: Rollback on post-action failure**
```gherkin
Given the signup form has onError: "rollback"
When I submit the form
And the user is created successfully
But the sendWelcomeEmail action fails
Then the user record should be deleted (rollback)
And I should see error "Signup failed. Please try again."
And no partial data should remain in database
```

**Scenario 3.1.3: Continue on optional action failure**
```gherkin
Given the signup form has postActions with:
  - sendWelcomeEmail (optional: true)
When the email sending fails
Then the user creation should still succeed
And I should be redirected to /dashboard
And the error should be logged but not shown to user
```

#### Technical Tasks
- [ ] Add `postActions` prop to form metadata
- [ ] Create `TransactionService.java` with rollback support
- [ ] Implement database transaction wrapping
- [ ] Add compensation logic for failed actions
- [ ] Add `onError: 'rollback' | 'continue'` prop
- [ ] Write unit test: `testRollbackOnFailure()`
- [ ] Write integration test: `testMultiStepTransaction()`

#### Definition of Done
- [ ] All 3 scenarios pass
- [ ] Rollback works across multiple tables
- [ ] No orphaned records
- [ ] Tests pass

**Story Points:** 8  
**Priority:** P3 - LOW

---

### Story 3.2: File Upload Support 🟢 LOW

**As a** form user  
**I want** to upload files in forms  
**So that** I can submit profile photos, documents, etc.

#### Acceptance Criteria

**Scenario 3.2.1: File input accepts images**
```gherkin
Given the signup form has a file input for profile photo
And the input has accept="image/*"
When I click "Choose File"
Then only image files should be selectable in file picker
```

**Scenario 3.2.2: File size validation**
```gherkin
Given the file input has maxSize="5MB"
When I select a file larger than 5MB
Then I should see error "File must be less than 5MB"
And the file should NOT be uploaded
```

**Scenario 3.2.3: File upload progress**
```gherkin
Given I select a valid image file
When the upload starts
Then I should see a progress bar showing upload percentage
And the form should be disabled during upload
```

**Scenario 3.2.4: File stored separately from entity**
```gherkin
Given the file input has entity: null
When I upload a file
Then the file should be uploaded to storage service
And the entity should receive only the file URL/ID
And the binary data should NOT be in the entity table
```

#### Technical Tasks
- [ ] Add file input type to component library
- [ ] Create `FileUploadService.java`
- [ ] Add multipart/form-data handling
- [ ] Add file size and type validation
- [ ] Add progress event support
- [ ] Store files in S3/local storage
- [ ] Write unit test: `testFileValidation()`
- [ ] Write E2E test: `testFileUpload()`

#### Definition of Done
- [ ] All 4 scenarios pass
- [ ] Files stored securely
- [ ] Progress tracking works
- [ ] Tests pass

**Story Points:** 8  
**Priority:** P3 - LOW

---

## 🧪 Cross-Cutting Testing Scenarios

### Security Testing

**Scenario: SQL Injection Prevention**
```gherkin
Given I am on the signup form
When I enter firstName: "Robert'; DROP TABLE user;--"
And I submit the form
Then the input should be sanitized/escaped
And no SQL injection should occur
And the user table should still exist
```

**Scenario: XSS Prevention**
```gherkin
Given I enter email: "<script>alert('XSS')</script>@example.com"
When the form is submitted and data is displayed
Then the script tags should be escaped
And no JavaScript should execute
```

**Scenario: HTTPS enforcement in production**
```gherkin
Given the app is deployed to production
When I access http://example.com/signup (no SSL)
Then I should be redirected to https://example.com/signup
And all form submissions should use HTTPS
```

### Performance Testing

**Scenario: Form loads in <1 second**
```gherkin
Given I navigate to /signup
When the page loads
Then the form should be fully interactive within 1 second
And all validation rules should be loaded
```

**Scenario: Validation runs in <100ms**
```gherkin
Given I blur a field with client-side validation
When the validation runs
Then the result should appear within 100ms
And the UI should not freeze
```

**Scenario: Form submission completes in <2s (p95)**
```gherkin
Given I submit a valid signup form
When the backend processes the request
Then 95% of requests should complete within 2 seconds
```

### Browser Compatibility

**Scenario: Works in all major browsers**
```gherkin
Given I test the signup form in:
  - Chrome 120+
  - Firefox 121+
  - Safari 17+
  - Edge 120+
Then all form features should work identically
And validation should display correctly
```

### Mobile Responsiveness

**Scenario: Form works on mobile devices**
```gherkin
Given I access the signup form on iPhone 14 (390x844)
Then the form should fit the screen without horizontal scroll
And all fields should be easily tappable (min 44x44px)
And the keyboard should not obscure input fields
```

---

## 📊 Test Coverage Matrix

| Story | Unit Tests | Integration Tests | E2E Tests | Security Tests | Accessibility Tests |
|-------|-----------|-------------------|-----------|----------------|-------------------|
| 1.1 Password | ✅ | ✅ | ✅ | ✅ | N/A |
| 1.2 CSRF | ✅ | ✅ | ✅ | ✅ | N/A |
| 1.3 Rate Limit | ✅ | ✅ | ✅ | N/A | N/A |
| 1.4 Validation | ✅ | ✅ | ✅ | N/A | ✅ |
| 1.5 Accessibility | N/A | N/A | N/A | N/A | ✅ |
| 2.1 Loading | ✅ | N/A | ✅ | N/A | N/A |
| 2.2 Client Val | ✅ | ✅ | ✅ | N/A | N/A |
| 3.1 Transactions | ✅ | ✅ | ✅ | N/A | N/A |
| 3.2 File Upload | ✅ | ✅ | ✅ | ✅ | N/A |

---

## 🎯 Acceptance Checklist (Master)

### Security ✅
- [ ] All passwords hashed with BCrypt
- [ ] No plain-text passwords in database or logs
- [ ] CSRF tokens on all forms
- [ ] Rate limiting active (5 req/min)
- [ ] SQL injection prevented
- [ ] XSS prevented
- [ ] HTTPS enforced in production

### Functionality ✅
- [ ] Forms bind to entities correctly
- [ ] Field mapping works (password → passwordHash)
- [ ] confirmPassword excluded from database
- [ ] Validation errors display correctly
- [ ] All form fields submit successfully
- [ ] Success redirect works

### UX ✅
- [ ] Loading states visible
- [ ] Double submission prevented
- [ ] Progressive validation works
- [ ] First error field gets focus
- [ ] Errors clear when corrected

### Accessibility ✅
- [ ] Lighthouse score ≥90
- [ ] axe-core: 0 violations
- [ ] Screen reader compatible
- [ ] Keyboard navigation works
- [ ] All inputs have labels

### Performance ✅
- [ ] Form loads <1s
- [ ] Validation <100ms
- [ ] Submission <2s (p95)
- [ ] No memory leaks

### Browser Support ✅
- [ ] Chrome 120+
- [ ] Firefox 121+
- [ ] Safari 17+
- [ ] Edge 120+
- [ ] Mobile responsive

---

## 📈 Progress Tracking

**Sprint 1:** 0/5 stories complete (0%)  
**Sprint 2:** 0/2 stories complete (0%)  
**Sprint 3:** 0/2 stories complete (0%)

**Overall:** 0/9 stories complete (0%)

---

## 🚨 Blocker Tracking

| Blocker ID | Story | Description | Owner | Status |
|-----------|-------|-------------|-------|--------|
| - | - | - | - | - |

---

**Next Review:** January 2, 2026  
**Document Version:** 1.0  
**Last Updated:** December 30, 2025
