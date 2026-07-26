> **📚 HISTORICAL DOCUMENT (Dec 2025).** Describes how canvas-era LitElement forms bound to entities. The runtime today (React, `app-bana-runtime`) binds via `Renderer.tsx` + `StudioTableLive.tsx` directly against the schema metadata. The core binding **concepts** (schema-driven form generation, entity CRUD via `/api/{tenantId}_{appId}_{entity}`) remain accurate; file paths and component names are superseded.
>
> **A refreshed binding doc will land during [Phase B (Complex UI Plan)](../planning/COMPLEX_UI_PLAN.md).**
>
> **See:** [`docs/README.md`](../README.md) for the full documentation currency table.

---

# Entity Form Binding Architecture - Implementation Guide

**Created:** December 30, 2025  
**Status:** 🔴 IN PROGRESS - Critical Architecture Enhancement  
**Priority:** P0 - Security & Core Functionality  
**Owner:** Architecture Team

---

## 🎯 Executive Summary

This document outlines the complete architecture for **metadata-driven entity-form binding** in AppBana's no-code platform. It addresses critical security gaps, validation patterns, and UX enhancements identified during architectural review.

**⚠️ REFER TO THIS DOCUMENT until ALL 10 issues are resolved.**

---

## 📐 System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         USER INTERACTION LAYER                          │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │  Browser: http://localhost:5173/signup                          │  │
│  │  - User fills form (firstName, lastName, email, password, etc.) │  │
│  │  - Clicks "Sign Up" button                                      │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      FRONTEND RUNTIME LAYER (Lit)                       │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │  FormComponent (Web Component)                                   │  │
│  │  ┌────────────────────────────────────────────────────────────┐ │  │
│  │  │  1. Client-Side Validation (Progressive)                   │ │  │
│  │  │     - On blur: Check required, pattern, minLength         │ │  │
│  │  │     - On change: Re-validate after first error            │ │  │
│  │  │     - confirmPassword === password?                        │ │  │
│  │  │                                                             │ │  │
│  │  │  2. Field Mapping (Form → Entity)                         │ │  │
│  │  │     - firstName → firstName                               │ │  │
│  │  │     - password → passwordHash (transform on backend)      │ │  │
│  │  │     - confirmPassword → null (form-only, not saved)       │ │  │
│  │  │                                                             │ │  │
│  │  │  3. Security Layer                                         │ │  │
│  │  │     - Inject CSRF token                                   │ │  │
│  │  │     - Add Recaptcha if configured                         │ │  │
│  │  │     - Rate limit check (client-side warning)              │ │  │
│  │  │                                                             │ │  │
│  │  │  4. Optimistic UI                                          │ │  │
│  │  │     - Button: "Creating Account..." (loading state)       │ │  │
│  │  │     - Disable form during submission                      │ │  │
│  │  │     - Show spinner                                         │ │  │
│  │  └────────────────────────────────────────────────────────────┘ │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ POST /api/user
                                    │ {firstName, lastName, email, 
                                    │  password, phone}
                                    │ Headers: X-CSRF-Token, Recaptcha
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                     BACKEND API LAYER (Java 21)                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │  ApiServer.java - POST /api/user Handler                        │  │
│  │  ┌────────────────────────────────────────────────────────────┐ │  │
│  │  │  1. Security Validation                                    │ │  │
│  │  │     - Verify CSRF token                                    │ │  │
│  │  │     - Check rate limit (5 requests/min per IP)            │ │  │
│  │  │     - Validate Recaptcha token                            │ │  │
│  │  │                                                             │ │  │
│  │  │  2. Entity Schema Validation                              │ │  │
│  │  │     - Fetch "user" entity schema                          │ │  │
│  │  │     - Validate all required fields present               │ │  │
│  │  │     - Check field types match (email = string, etc.)     │ │  │
│  │  │     - Run custom validation rules                        │ │  │
│  │  │                                                             │ │  │
│  │  │  3. Business Logic                                         │ │  │
│  │  │     - Hash password (BCrypt)                              │ │  │
│  │  │     - Check email uniqueness                              │ │  │
│  │  │     - Generate user ID                                    │ │  │
│  │  │                                                             │ │  │
│  │  │  4. Database Transaction                                   │ │  │
│  │  │     - BEGIN TRANSACTION                                    │ │  │
│  │  │     - INSERT INTO user (...)                              │ │  │
│  │  │     - INSERT INTO user_profile (userId, ...)             │ │  │
│  │  │     - INSERT INTO user_roles (userId, role='customer')   │ │  │
│  │  │     - COMMIT or ROLLBACK on error                        │ │  │
│  │  │                                                             │ │  │
│  │  │  5. Post-Actions (Async)                                   │ │  │
│  │  │     - Send welcome email                                  │ │  │
│  │  │     - Trigger analytics event                             │ │  │
│  │  │     - Create notification preferences                     │ │  │
│  │  └────────────────────────────────────────────────────────────┘ │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        DATABASE LAYER (H2/PostgreSQL)                   │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │  Tables Created from Entity Schema                              │  │
│  │                                                                  │  │
│  │  CREATE TABLE user (                                            │  │
│  │    id BIGINT PRIMARY KEY AUTO_INCREMENT,                       │  │
│  │    firstName VARCHAR(100) NOT NULL,                            │  │
│  │    lastName VARCHAR(100) NOT NULL,                             │  │
│  │    email VARCHAR(255) NOT NULL UNIQUE,                         │  │
│  │    phone VARCHAR(20),                                          │  │
│  │    passwordHash VARCHAR(255) NOT NULL,  -- ❌ NOT "password"  │  │
│  │    createdAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP              │  │
│  │  );                                                             │  │
│  │                                                                  │  │
│  │  CREATE INDEX idx_user_email ON user(email);                   │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼ Success Response
┌─────────────────────────────────────────────────────────────────────────┐
│                        RESPONSE FLOW (Success)                          │
│                                                                         │
│  Backend → Frontend:                                                    │
│  {                                                                      │
│    "ok": true,                                                          │
│    "data": {                                                            │
│      "id": 123,                                                         │
│      "firstName": "John",                                               │
│      "lastName": "Doe",                                                 │
│      "email": "john@example.com"                                        │
│      // ❌ NO passwordHash returned                                    │
│    }                                                                    │
│  }                                                                      │
│                                                                         │
│  Frontend Action:                                                       │
│  - Show success message                                                 │
│  - Redirect to: /dashboard (or configured redirect path)               │
│  - Clear form state                                                     │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                        RESPONSE FLOW (Validation Error)                 │
│                                                                         │
│  Backend → Frontend:                                                    │
│  {                                                                      │
│    "ok": false,                                                         │
│    "validationErrors": {                                                │
│      "email": "Email already exists",                                   │
│      "password": "Password must be at least 8 characters"               │
│    }                                                                    │
│  }                                                                      │
│                                                                         │
│  Frontend Action:                                                       │
│  - Map errors to inputs: email-input-compact.error = "Email already..." │
│  - Show red border on invalid fields                                    │
│  - Focus first error field                                              │
│  - Keep form data (don't clear)                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 🔴 Critical Issues & Solutions (Priority Order)

### **Issue #1: Password Security** 🔴 P0

**Problem:**
```json
// ❌ DANGEROUS - Password stored in plain text
{
  "name": "user",
  "fields": [
    {"name": "password", "type": "string"}  // Plain text!
  ]
}
```

**Solution - Field Mapping + Backend Transform:**

#### Frontend Metadata (signup.json)
```json
{
  "id": "form-sign-compact",
  "type": "container",
  "props": {
    "tag": "form",
    "entity": "user",
    "action": "create",
    "fieldMapping": {
      "firstName": "firstName",
      "lastName": "lastName",
      "email": "email",
      "phone": "phone",
      "password": "passwordHash",      // ← Maps to hashed field
      "confirmPassword": null          // ← Form-only, not saved
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
  }
}
```

#### Backend Entity Schema (SECURE)
```json
{
  "name": "user",
  "fields": [
    {"name": "id", "type": "long", "primaryKey": true, "autoIncrement": true},
    {"name": "firstName", "type": "string", "length": 100, "required": true},
    {"name": "lastName", "type": "string", "length": 100, "required": true},
    {"name": "email", "type": "string", "length": 255, "required": true, "unique": true},
    {"name": "phone", "type": "string", "length": 20, "required": false},
    {"name": "passwordHash", "type": "string", "length": 255, "required": true}
  ]
}
```

#### Backend Transformation (ApiServer.java)
```java
// In POST /api/user handler
Map<String, Object> formData = objectMapper.readValue(body, Map.class);

// Transform password → passwordHash
if (formData.containsKey("password")) {
    String plainPassword = (String) formData.get("password");
    String hashedPassword = BCrypt.hashpw(plainPassword, BCrypt.gensalt());
    formData.remove("password");
    formData.put("passwordHash", hashedPassword);
}

// Remove form-only fields
formData.remove("confirmPassword");

// Now insert into database with hashed password
```

**Status:** ❌ Not Implemented  
**Effort:** 2 days  
**Files to Modify:**
- `signup.json` - Add fieldMapping and validationRules
- `login.json` - Add fieldMapping
- `FormComponent.ts` (NEW) - Form component with mapping logic
- `ApiServer.java` - Add password hashing + field mapping

---

### **Issue #2: Security Layer** 🔴 P0

**Problem:**
- No CSRF protection
- No rate limiting
- No captcha for public forms
- Anyone can spam `/api/user`

**Solution - Security Metadata:**

#### Frontend Metadata
```json
{
  "id": "form-sign-compact",
  "props": {
    "entity": "user",
    "security": {
      "csrfToken": true,
      "rateLimit": {
        "maxAttempts": 5,
        "windowMinutes": 1,
        "errorMessage": "Too many signup attempts. Try again in 1 minute."
      },
      "captcha": {
        "provider": "recaptcha",
        "siteKey": "${env.RECAPTCHA_SITE_KEY}",
        "action": "signup"
      },
      "allowAnonymous": true,
      "requireAuth": false
    }
  }
}
```

#### Backend Implementation
```java
// SecurityService.java (NEW)
public class SecurityService {
    private Map<String, List<Long>> rateLimitMap = new ConcurrentHashMap<>();
    
    public boolean checkRateLimit(String ip, int maxAttempts, int windowMinutes) {
        List<Long> attempts = rateLimitMap.computeIfAbsent(ip, k -> new ArrayList<>());
        long now = System.currentTimeMillis();
        long window = windowMinutes * 60 * 1000;
        
        // Remove old attempts
        attempts.removeIf(time -> now - time > window);
        
        if (attempts.size() >= maxAttempts) {
            return false;  // Rate limit exceeded
        }
        
        attempts.add(now);
        return true;
    }
    
    public boolean verifyCsrfToken(String token, String sessionToken) {
        // Verify CSRF token matches session
        return token != null && token.equals(sessionToken);
    }
    
    public boolean verifyRecaptcha(String token) {
        // Call Google Recaptcha API
        // https://www.google.com/recaptcha/api/siteverify
    }
}
```

**Status:** ❌ Not Implemented  
**Effort:** 3 days  
**Files to Create:**
- `SecurityService.java` - Rate limiting, CSRF, Recaptcha
- `SecurityMiddleware.java` - Request interceptor
- Update `ApiServer.java` - Add security checks

---

### **Issue #3: Validation Feedback** 🟡 P1

**Problem:**
Errors come from backend, but where do they appear in UI?

**Solution - Error Binding:**

#### Frontend Metadata (Enhanced Input)
```json
{
  "id": "email-input-compact",
  "type": "input",
  "props": {
    "name": "email",
    "error": "${validationErrors.email}",           // ← Bind to state
    "aria-invalid": "${!!validationErrors.email}",
    "helperText": "${validationErrors.email || 'We\\'ll never share your email'}"
  }
}
```

#### FormComponent State Management
```typescript
// FormComponent.ts
class FormComponent extends LitElement {
  @state() private validationErrors: Record<string, string> = {};
  @state() private touched: Record<string, boolean> = {};
  
  async handleSubmit(e: Event) {
    e.preventDefault();
    
    const response = await fetch(`/api/${this.entity}`, {
      method: 'POST',
      body: JSON.stringify(this.formData)
    });
    
    if (!response.ok) {
      const errorData = await response.json();
      this.validationErrors = errorData.validationErrors || {};
      
      // Focus first error field
      const firstError = Object.keys(this.validationErrors)[0];
      const input = this.querySelector(`[name="${firstError}"]`);
      input?.focus();
    }
  }
  
  handleBlur(fieldName: string) {
    this.touched[fieldName] = true;
    this.validateField(fieldName);
  }
}
```

**Status:** ❌ Not Implemented  
**Effort:** 2 days  
**Files to Modify:**
- `signup.json` - Add error bindings to all inputs
- `FormComponent.ts` - Add state management
- `Renderer.ts` - Support `${}` template expressions

---

### **Issue #4: Accessibility** 🟡 P1

**Problem:**
Labels not linked to inputs, no ARIA attributes

**Solution - Accessibility Metadata:**

#### Enhanced Label + Input Linking
```json
{
  "id": "firstname-label-compact",
  "type": "text",
  "props": {
    "tag": "label",
    "for": "firstname-input-compact",  // ← Link to input
    "text": "First Name"
  }
},
{
  "id": "firstname-input-compact",
  "type": "input",
  "props": {
    "id": "firstname-input-compact",           // ← Must match label's "for"
    "name": "firstName",
    "aria-label": "First Name",
    "aria-required": "true",
    "aria-describedby": "firstname-helper",
    "aria-invalid": "${!!validationErrors.firstName}"
  }
}
```

**Status:** ❌ Not Implemented  
**Effort:** 1 day  
**Files to Modify:**
- `signup.json` - Add id, for, aria-* attributes
- `login.json` - Same updates
- `Renderer.ts` - Ensure aria attributes rendered correctly

---

### **Issue #5: Loading States** 🟡 P2

**Problem:**
User clicks submit → waits → no feedback → frustrated

**Solution - Optimistic UI:**

```json
{
  "id": "submit-compact",
  "type": "button",
  "props": {
    "label": "${form.submitting ? 'Creating Account...' : 'Sign Up'}",
    "disabled": "${form.submitting || !form.valid}",
    "showSpinner": "${form.submitting}"
  }
}
```

```typescript
// FormComponent
@state() private submitting = false;

async handleSubmit(e: Event) {
  this.submitting = true;
  try {
    await fetch(...);
  } finally {
    this.submitting = false;
  }
}
```

**Status:** ❌ Not Implemented  
**Effort:** 1 day

---

### **Issue #6: Client-Side Validation** 🟡 P2

**Problem:**
Wait for backend to validate? Or validate on every keystroke?

**Solution - Progressive Validation:**

```json
{
  "validationStrategy": {
    "validateOn": "blur",        // First validation on blur
    "revalidateOn": "change",    // Re-check while typing after error
    "submitOn": "valid"          // Only allow submit when valid
  }
}
```

**Status:** ❌ Not Implemented  
**Effort:** 2 days

---

### **Issue #7: Transaction Boundaries** 🟢 P3

**Problem:**
User created but welcome email fails → inconsistent state

**Solution - Post-Actions with Rollback:**

```json
{
  "entity": "user",
  "action": "create",
  "postActions": [
    {"type": "sendEmail", "template": "welcome", "to": "${email}"},
    {"type": "assignRole", "role": "customer"},
    {"type": "createRelated", "entity": "userProfile", "fields": {"userId": "${id}"}}
  ],
  "onError": "rollback"
}
```

**Status:** ❌ Not Implemented  
**Effort:** 3 days

---

### **Issue #8: File Upload Support** 🟢 P3

**Future enhancement for profile photos**

**Status:** ⏳ Deferred to Phase 2

---

### **Issue #9: Data Adapter Abstraction** 🟢 P4

**Future support for GraphQL, gRPC**

**Status:** ⏳ Deferred to Phase 3

---

### **Issue #10: Multi-Step Forms** 🟢 P4

**Future wizard support**

**Status:** ⏳ Deferred to Phase 3

---

## 📋 Implementation Checklist

### Phase 1: Security & Validation (Week 1) 🔴 CRITICAL

- [ ] **Issue #1: Password Security**
  - [ ] Add `fieldMapping` to signup.json
  - [ ] Add `validationRules` to signup.json
  - [ ] Create `FormComponent.ts` with mapping logic
  - [ ] Add BCrypt hashing in ApiServer.java
  - [ ] Test: Password hashed in database
  - [ ] Test: confirmPassword NOT saved to database

- [ ] **Issue #2: Security Layer**
  - [ ] Create `SecurityService.java`
  - [ ] Add CSRF token generation
  - [ ] Add rate limiting (5 req/min)
  - [ ] Add Recaptcha integration (optional)
  - [ ] Update ApiServer.java with security checks
  - [ ] Test: Rate limit blocks excessive requests
  - [ ] Test: CSRF token required

- [ ] **Issue #4: Accessibility**
  - [ ] Add `id` to all inputs in signup.json
  - [ ] Add `for` to all labels
  - [ ] Add `aria-*` attributes
  - [ ] Test: Screen reader compatibility
  - [ ] Test: Keyboard navigation

- [ ] **Issue #3: Validation Feedback**
  - [ ] Add error bindings to all inputs
  - [ ] Update FormComponent with state management
  - [ ] Add template expression support in Renderer
  - [ ] Test: Errors appear below fields
  - [ ] Test: First error field gets focus

### Phase 2: UX Polish (Week 2) 🟡 HIGH

- [ ] **Issue #5: Loading States**
  - [ ] Add submitting state to FormComponent
  - [ ] Update button with dynamic label
  - [ ] Add spinner component
  - [ ] Test: Button shows "Creating Account..."

- [ ] **Issue #6: Client Validation**
  - [ ] Add progressive validation strategy
  - [ ] Validate on blur (first time)
  - [ ] Re-validate on change (after error)
  - [ ] Test: Immediate feedback on invalid fields

### Phase 3: Advanced Features (Month 2) 🟢 MEDIUM

- [ ] **Issue #7: Transactions**
  - [ ] Add postActions support
  - [ ] Add rollback logic
  - [ ] Test: Rollback on email failure

---

## 🧪 Testing Strategy

### Unit Tests
```typescript
// FormComponent.test.ts
describe('FormComponent', () => {
  it('should map password to passwordHash', () => {
    const form = new FormComponent();
    form.fieldMapping = { password: 'passwordHash' };
    const mapped = form.mapFields({ password: '12345' });
    expect(mapped).toEqual({ passwordHash: '12345' });
    expect(mapped.password).toBeUndefined();
  });
  
  it('should exclude confirmPassword from submission', () => {
    const form = new FormComponent();
    form.fieldMapping = { confirmPassword: null };
    const mapped = form.mapFields({ confirmPassword: '12345' });
    expect(mapped.confirmPassword).toBeUndefined();
  });
});
```

### Integration Tests
```bash
# Test signup flow end-to-end
curl -X POST http://localhost:8080/api/user \
  -H 'Content-Type: application/json' \
  -d '{
    "firstName": "John",
    "lastName": "Doe",
    "email": "john@example.com",
    "password": "SecurePass123"
  }'

# Verify in database
SELECT id, firstName, email, passwordHash FROM user WHERE email='john@example.com';
# Should see BCrypt hash like: $2a$10$...
```

---

## 📚 Reference Documentation

- **Password Hashing:** [BCrypt Java](https://github.com/patrickfav/bcrypt)
- **CSRF Protection:** [OWASP CSRF Guide](https://owasp.org/www-community/attacks/csrf)
- **Rate Limiting:** [Bucket4j](https://github.com/bucket4j/bucket4j)
- **Accessibility:** [ARIA APG](https://www.w3.org/WAI/ARIA/apg/)
- **Form Validation:** [HTML5 Constraint Validation](https://developer.mozilla.org/en-US/docs/Web/HTML/Constraint_validation)

---

## 🎯 Success Metrics

- [ ] All passwords hashed with BCrypt
- [ ] Zero plain-text passwords in database
- [ ] confirmPassword NOT saved to any table
- [ ] CSRF protection on all forms
- [ ] Rate limiting active (5 req/min)
- [ ] Validation errors display correctly
- [ ] Accessibility score 90+ (Lighthouse)
- [ ] Form submission <2s (p95)

---

## 🚨 Critical Reminders

**⚠️ DO NOT PROCEED without addressing:**
1. Password hashing (Issue #1)
2. Security layer (Issue #2)
3. Accessibility (Issue #4)

**⚠️ ALWAYS:**
- Hash passwords on backend, NEVER store plain text
- Validate on both client and server
- Use HTTPS in production
- Add CSRF tokens to all forms
- Rate limit public endpoints

---

**Next Review:** January 6, 2026  
**Document Version:** 1.0  
**Last Updated:** December 30, 2025
