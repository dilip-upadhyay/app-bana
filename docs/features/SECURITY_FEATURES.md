# AppBana Security Features - Complete Guide

**Last Updated:** April 2026 (CSRF section corrected — see S4.3 note below)  
**Status:** Production Ready  
**Test Coverage:** See [`docs/planning/TENANT_ISOLATION_IMPLEMENTATION_TASKS.md`](../planning/TENANT_ISOLATION_IMPLEMENTATION_TASKS.md) for the current, authoritative `app-bana-service` suite total — the count in [Testing](#testing) below covers only the security-feature test classes documented on this page (originally 156, now 131 after S4.3 removed `CsrfMiddlewareTest`), predating all of the S1–S4 tenant-isolation work, and was never meant to represent the whole module.

> [!IMPORTANT]
> **S4.3 (tenant-isolation security initiative):** `CsrfMiddleware` was deleted as dead code — it
> was fully coded and unit-tested (24 tests) but **never registered** in `RouteRegistry.buildRouter()`'s
> real middleware chain, so it never actually protected any request. This app authenticates via a
> bearer token in a request header (`X-Session-Token` / `Authorization: Bearer`), never via cookies
> (a legacy cookie-based fallback was already removed separately as dead-but-accepted attack
> surface), so classic browser CSRF does not apply here regardless. The standalone
> `CsrfService`/`CsrfController` token generate/validate endpoints (`GET /api/csrf-token`,
> `POST /api/csrf-validate`) remain — they are real and registered — but nothing calls or enforces
> them today. Every claim below that CSRF is actively "protecting" requests, wired into the
> middleware pipeline, or fetched by the frontend has been corrected to reflect this.

---

## Table of Contents

1. [Overview](#overview)
2. [Security Architecture](#security-architecture)
3. [Password Security](#password-security)
4. [CSRF Protection](#csrf-protection)
5. [Session Management](#session-management)
6. [Rate Limiting](#rate-limiting)
7. [Middleware Pipeline](#middleware-pipeline)
8. [Frontend Integration](#frontend-integration)
9. [API Reference](#api-reference)
10. [Testing](#testing)

---

## Overview

AppBana provides an **enterprise-grade security suite** with automatic protection for all endpoints.

### Security Features

✅ **Password Security** - BCrypt hashing with work factor 12  
⚠️ **CSRF Token Service** - `CsrfService`/`CsrfController` endpoints exist but are not currently wired into the middleware pipeline or consumed by any frontend (see [CSRF Protection](#csrf-protection))  
✅ **Session Management** - 30-minute sliding window with automatic renewal  
✅ **Rate Limiting** - 100 requests/minute per IP per endpoint  
✅ **Middleware Pipeline** - Automatic security checks on all requests  
✅ **Field-Level Security** - HIPAA/PCI-DSS compliant data protection  

### Compliance

- **HIPAA** - PHI protection with field-level security
- **PCI-DSS** - Secure password handling and data encryption
- **SOC 2** - Comprehensive audit logging and access controls
- **ISO 27001** - Need-to-know data access principles

---

## Security Architecture

### Middleware Pipeline

All requests flow through a layered security pipeline:

```
Client Request
    ↓
RateLimitMiddleware (100 req/min per IP)
    ↓
SessionMiddleware (Validate session token)
    ↓
Handler (Process request)
    ↓
Response
```

**Performance:** <3ms total overhead per request

### Excluded Paths

The following endpoints bypass authentication but still respect rate limiting:

- `/api/auth/login` - User login
- `/api/auth/register` - User registration
- `/api/csrf-token` - CSRF token generation (see [CSRF Protection](#csrf-protection) — endpoint exists but nothing enforces its use)
- `/health` - Health check
- `/ready` - Readiness check

---

## Password Security

### BCrypt Hashing

**File:** `com.appbana.service.PasswordService`  
**Tests:** `PasswordServiceTest.java` (21 tests)

```java
// Hash password
String hashedPassword = PasswordService.hashPassword("mySecurePassword123");

// Verify password
boolean isValid = PasswordService.verifyPassword("mySecurePassword123", hashedPassword);
```

### Features

- **BCrypt Algorithm** - Industry-standard password hashing
- **Work Factor 12** - 2^12 = 4096 iterations (intentionally slow)
- **Automatic Salting** - Unique salt per password
- **Constant-Time Comparison** - Prevents timing attacks
- **Performance** - 100-500ms per hash (intentional delay)

### Security Guarantees

✅ Passwords never stored in plain text  
✅ Salts are cryptographically random  
✅ Verification uses constant-time comparison  
✅ Invalid hashes return false (no exceptions)  
✅ Work factor can be increased in future  

---

## CSRF Protection

> [!NOTE]
> Despite the section title (kept for TOC/link stability), this describes a **standalone,
> not-currently-enforced** token service, not active CSRF protection. See the S4.3 callout at the
> top of this document.

### Token Generation & Validation

**File:** `com.appbana.service.CsrfService`  
**Tests:** `CsrfServiceTest.java` (24 tests)  
**Registered routes:** `GET /api/csrf-token`, `POST /api/csrf-validate` (`CsrfController.java`, wired in `AuthRoutes.java`)

```java
// Generate token for session
String csrfToken = CsrfService.generateToken(sessionId);

// Validate token
boolean isValid = CsrfService.validateToken(sessionId, csrfToken);

// Remove token (on logout)
CsrfService.removeToken(sessionId);
```

### Features

- **256-bit Tokens** - Cryptographically secure random generation
- **Session-Bound** - One token per session (cannot be reused)
- **30-Minute Expiration** - Automatic cleanup of old tokens
- **Thread-Safe** - Concurrent access with ConcurrentHashMap
- **Automatic Cleanup** - Expired tokens removed automatically

### Current Status

⚠️ Tokens can be generated and validated via the two endpoints above, on demand  
⚠️ **Nothing calls these endpoints today** - no frontend code fetches or sends a CSRF token, and no
middleware rejects a request for lacking one (`CsrfMiddleware`, which used to do this, was deleted
as dead code in S4.3 - it was never registered in the real request pipeline)  
✅ This is intentional, not a regression: auth is bearer-token/header-only, never cookie-based, so
classic Cross-Site Request Forgery does not apply

---

## Session Management

### Session Lifecycle

**File:** `com.appbana.service.SessionService`  
**Tests:** `SessionServiceTest.java` (33 tests)  
**Middleware:** `com.appbana.middleware.SessionMiddleware` (13 tests)

```java
// Create session
SessionData session = SessionService.createSession("user123");
String sessionId = session.sessionId();

// Validate session
SessionData validSession = SessionService.validateSession(sessionId);

// Renew session (sliding window)
SessionData renewed = SessionService.renewSession(sessionId);

// Invalidate session (logout)
SessionService.invalidateSession(sessionId);
```

### SessionData Structure

```java
public record SessionData(
    String sessionId,           // 256-bit secure token
    String userId,              // User identifier
    long createdAt,             // Creation timestamp
    long lastAccessedAt,        // Last activity
    long expiresAt,             // Expiration timestamp
    Map<String, Object> attributes  // Custom storage
) {
    public boolean isExpired() { ... }
}
```

### Features

- **Secure 256-bit IDs** - Cryptographically random session tokens
- **30-Minute Timeout** - Configurable inactivity timeout
- **Sliding Window** - Automatic renewal on activity
- **Custom Attributes** - Store custom data per session
- **Automatic Expiration** - Sessions cleaned up after timeout
- **Thread-Safe** - ConcurrentHashMap storage

### Protection

✅ Prevents session hijacking with secure random IDs  
✅ Enforces inactivity timeout (30 minutes)  
✅ Sliding window renewal keeps active users logged in  
✅ Sessions automatically expire and cleanup  

### Error Codes

- **SESSION_MISSING** (401) - Session token required
- **SESSION_INVALID** (401) - Invalid session ID
- **SESSION_EXPIRED** (401) - Session timed out

---

## Rate Limiting

### Request Throttling

**File:** `com.appbana.service.RateLimitService`  
**Tests:** `RateLimitServiceTest.java` (25 tests)  
**Middleware:** `com.appbana.middleware.RateLimitMiddleware`

```java
// Check rate limit
RateLimitResult result = RateLimitService.checkRateLimit(clientIp, endpoint);
if (result.allowed()) {
    int remaining = result.remaining();
    long resetTime = result.resetTimeMillis();
    // Process request
} else {
    // Return 429 Too Many Requests
}
```

### Configuration

- **Max Requests:** 100 per window
- **Time Window:** 1 minute
- **Scope:** Per IP address + per endpoint
- **Algorithm:** Sliding window (accurate rate limiting)
- **Cleanup:** Automatic removal of expired windows

### Features

- **Per-IP Tracking** - Each IP address tracked separately
- **Per-Endpoint** - Different limits for different endpoints
- **Sliding Window** - Accurate rate limiting (not fixed buckets)
- **Remaining Count** - Returns attempts left in current window
- **Reset Time** - When rate limit window resets
- **Thread-Safe** - Concurrent request handling

### Protection

✅ Prevents brute force attacks on login  
✅ Prevents DDoS attempts  
✅ Protects all endpoints automatically  
✅ Fair usage enforcement  

### Response Headers

```
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1703923200000
Retry-After: 60
```

### Error Code

- **RATE_LIMIT_EXCEEDED** (429) - Too many requests

---

## Middleware Pipeline

### Automatic Security Checks

**Integration Tests:** `SecurityIntegrationTest.java` (15 tests)

All requests automatically flow through:

1. **RateLimitMiddleware**
   - Checks IP-based rate limit
   - Returns 429 if exceeded
   - Continues if allowed

2. **SessionMiddleware** (if protected endpoint)
   - Validates session token from `X-Session-Token` header
   - Renews session if valid (sliding window)
   - Sets `request.attribute('session', sessionId)`
   - Returns 401 if invalid/missing

3. **Handler**
   - Processes the actual request
   - Has access to validated session

### Pipeline Registration

```java
// In RouteRegistry.java
Router router = new Router();
router.use(RateLimitMiddleware.create());
router.use(SessionMiddleware.create());
```

### Performance

- **Total Overhead:** <3ms per request
- **RateLimit Check:** <1ms (in-memory lookup)
- **Session Validation:** <2ms (in-memory lookup + renewal)

---

## Frontend Integration

### Automatic Security (shared api-client)

**File:** [`app-bana-shared/src/api-client.ts`](../../app-bana-shared/src/api-client.ts)  
**Auth UI:** [`app-bana-studio/src/features/auth/AuthGate.tsx`](../../app-bana-studio/src/features/auth/AuthGate.tsx)

All authed calls flow through `authedFetch()` which broadcasts a browser event on 401 so the auth gate can force re-login. Session headers are injected consistently.

> [!NOTE]
> **S4.3:** the code samples below predate the AI-native Studio/Runtime rebuild and describe the
> retired LitElement `app-bana-ui` client, not the current React `app-bana-studio`/`app-bana-runtime`
> frontends — treat them as illustrative of past patterns, not a literal reference for the current
> code. The CSRF-token-fetching step that used to appear here has been removed entirely: the current
> `api-client.ts` contains no CSRF references at all (confirmed by repo-wide search), consistent with
> `CsrfMiddleware` never having been wired into the backend pipeline (see the top-of-document
> callout).

#### 1. Session Token Inclusion

```typescript
const headers = {
    'Content-Type': 'application/json',
    'X-Session-Token': localStorage.getItem('appbana_token')
};
```

**Storage:** `localStorage` key: `appbana_token`  
**Lifetime:** 30 minutes with sliding window  

#### 2. Password Validation

```typescript
validateField(element: HTMLInputElement) {
    if (element.name === 'password') {
        // Min 8 characters
        if (element.value.length < 8) {
            this.showFieldError(element, 'Password must be at least 8 characters');
            return false;
        }
        // Letters + numbers
        if (!/[a-zA-Z]/.test(element.value) || !/\d/.test(element.value)) {
            this.showFieldError(element, 'Password must contain letters and numbers');
            return false;
        }
    }
    
    if (element.name === 'confirmPassword') {
        const password = this.querySelector('[name="password"]');
        if (element.value !== password.value) {
            this.showFieldError(element, 'Passwords do not match');
            return false;
        }
    }
}
```

**Timing:** On blur (after leaving field)  
**Clearing:** On input (as user types)  

#### 3. Error Handling

```typescript
// 401 - Session expired
if (response.status === 401) {
    this.showError('Session expired. Redirecting to login...');
    setTimeout(() => window.location.href = '/login', 2000);
    return;
}

// 429 - Rate limit
if (response.status === 429) {
    const retryAfter = response.headers.get('Retry-After') || '60';
    this.showError(`Too many requests. Please try again in ${retryAfter} seconds.`);
    return;
}
```

#### 4. Loading States

```typescript
setLoadingState(loading: boolean) {
    const submitButton = this.querySelector('button[type="submit"]');
    if (loading) {
        submitButton.textContent = 'Submitting...';
        submitButton.disabled = true;
        this.isSubmitting = true;
    } else {
        submitButton.textContent = this.submitButtonText;
        submitButton.disabled = false;
        this.isSubmitting = false;
    }
}
```

**Prevents:** Double-submit during request processing  

---

## API Reference

### CSRF Token Endpoint

> Real, registered endpoint (`CsrfController.generateToken()`, wired in `AuthRoutes.java`) — but
> nothing in the current frontend calls it (see the S4.3 callout at the top of this document).

```
GET /api/csrf-token
Headers:
  X-Session-Id: <session-id>

Response: 200 OK
{
  "ok": true,
  "token": "abc123def456...",
  "expiresAt": 1703923200000
}
```

### Session Creation (Login)

```
POST /api/auth/login
Body:
{
  "email": "user@example.com",
  "password": "myPassword123"
}

Response: 200 OK
{
  "token": "jwt-token-here",
  "user": {
    "id": 1,
    "email": "user@example.com",
    "name": "John Doe"
  },
  "sessionId": "session-token-here"
}
```

**Store:** `localStorage.setItem('appbana_token', sessionId)`

### Protected Request Example

```
POST /api/users
Headers:
  X-Session-Token: <session-token>
  Content-Type: application/json
  
Body:
{
  "name": "John Doe",
  "email": "john@example.com"
}

Response: 201 Created
{
  "id": 123,
  "name": "John Doe",
  "email": "john@example.com"
}
```

---

## Testing

### Test Coverage Summary

**Total (this table only — a security-feature subset, not the full `app-bana-service` suite):** 131 tests, 100% passing ✅

| Test Suite | Tests | Description |
|------------|-------|-------------|
| PasswordServiceTest | 21 | BCrypt hashing, verification, timing |
| CsrfServiceTest | 24 | Token generation, validation, expiration (standalone service — see [CSRF Protection](#csrf-protection)) |
| RateLimitServiceTest | 25 | Rate checking, sliding window, cleanup |
| SessionServiceTest | 33 | Session CRUD, validation, renewal, expiration |
| SessionMiddleware Test | 13 | Session validation, renewal, 401 responses |
| **SecurityIntegrationTest** | **15** | **End-to-end pipeline testing** |

### Integration Test Groups

**SecurityIntegrationTest.java** covers the security pipeline:

1. **Rate Limiting (2 tests)**
   - Blocks excessive requests (>100/min)
   - Allows requests within threshold

2. **Session Management (3 tests)**
   - Valid session allows request
   - Invalid session blocks with 401
   - Missing session blocks with 401

3. **Auth Endpoints (2 tests)**
   - Login/register bypass session check
   - But still enforce rate limits

4. **CSRF token service (2 tests, standalone — not part of the pipeline)**
   - Token generation works
   - Validation rejects invalid token

5. **Complete Pipeline (3 tests)**
   - All checks pass → request succeeds
   - Stops at first failure
   - Session renewal works during request

6. **Error Scenarios (3 tests)**
   - Invalid session returns 401
   - Concurrent requests handled correctly
   - Health endpoint bypasses all checks

### Running Tests

```bash
# All security tests
mvn test -Dtest="PasswordServiceTest,CsrfServiceTest,RateLimitServiceTest,SessionServiceTest,SessionMiddlewareTest,SecurityIntegrationTest"

# Integration tests only
mvn test -Dtest=SecurityIntegrationTest

# Specific service
mvn test -Dtest=SessionServiceTest
```

---

## Best Practices

### For AI Builder

When generating forms or authenticated features:

✅ **Always** include session token headers  
✅ **Always** validate passwords (8+ chars, letters+numbers)  
✅ **Always** handle 401/429 error responses  
✅ **Always** show loading states during submission  
✅ **Always** use constant-time password comparison  
✅ **Never** store passwords in plain text  
✅ **Never** bypass security checks on backend  
✅ **Never** re-introduce CSRF token fetching/headers unless the auth model changes to
cookie-based sessions — bearer-token auth makes it a no-op (see [CSRF Protection](#csrf-protection))

### For Developers

✅ Store session token in localStorage  
✅ Include security headers on all authenticated requests  
✅ Validate on backend, not just frontend  
✅ Use BCrypt for password hashing  
✅ Implement proper error handling for security failures  
✅ Test security features with integration tests  
✅ Log security events for audit trail  

---

## Performance Characteristics

| Feature | Overhead | Notes |
|---------|----------|-------|
| **Password Hashing** | 100-500ms | Intentionally slow (BCrypt) |
| **Password Verification** | 100-500ms | Same as hashing |
| **CSRF Token Generation** | <1ms | Secure random + storage — only if `/api/csrf-token` is called directly; not part of the request pipeline |
| **Session Creation** | <2ms | ID generation + storage |
| **Session Validation** | <2ms | In-memory lookup |
| **Session Renewal** | <3ms | Validation + update |
| **Rate Limit Check** | <1ms | In-memory counter |
| **Complete Pipeline** | <3ms | RateLimit + Session middleware combined |

---

## Security Checklist

For any new feature with user input:

- [ ] Forms include session token in headers
- [ ] Password fields validated (8+ chars, letters+numbers)
- [ ] confirmPassword field excluded from submission
- [ ] Error messages user-friendly (not exposing internals)
- [ ] Loading states prevent double-submit
- [ ] 401 redirects to login with delay
- [ ] 429 shows retry message
- [ ] Backend validates all security checks
- [ ] Tests cover security scenarios

---

## Troubleshooting

### Common Issues

**Q: Getting 401 session errors?**  
A: Check `localStorage.getItem('appbana_token')` exists and session hasn't expired (30 min)

**Q: Getting 429 rate limit errors?**  
A: Wait for the time specified in `Retry-After` header (max 1 minute)

**Q: Password validation not working?**  
A: Ensure `validateField()` is called on blur for password and confirmPassword fields

**Q: Session not renewing?**  
A: SessionMiddleware automatically renews valid sessions on each request (sliding window)

---

## References

- **Source Files:**
  - `com.appbana.service.PasswordService`
  - `com.appbana.service.CsrfService`
  - `com.appbana.api.CsrfController` (registers `GET /api/csrf-token`, `POST /api/csrf-validate` — see [CSRF Protection](#csrf-protection))
  - `com.appbana.service.RateLimitService`
  - `com.appbana.service.SessionService`
  - `com.appbana.middleware.SessionMiddleware`
  - `com.appbana.middleware.RateLimitMiddleware`
  - [`app-bana-shared/src/api-client.ts`](../../app-bana-shared/src/api-client.ts)
  - [`app-bana-studio/src/features/auth/AuthGate.tsx`](../../app-bana-studio/src/features/auth/AuthGate.tsx)

- **Test Files:**
  - All test files in `src/test/java/com/appbana/service/`
  - All test files in `src/test/java/com/appbana/middleware/`
  - `src/test/java/com/appbana/integration/SecurityIntegrationTest.java`

- **Builder Database:** (AI Builder RAG knowledge sources — out of scope for this doc's S4.3
  correction pass; still describe `CsrfMiddleware`/CSRF-fetching as if live and should be
  reconciled separately)
  - `builder-database/09-authentication.json` (v1.2.0)
  - `builder-database/10-form-patterns.json` (v1.1.0)
  - `builder-database/99-capabilities-index.json` (v1.4.0)

---

**Status:** Production Ready ✅  
**Last Tested:** April 2026 (this pass: `CsrfMiddleware` deletion verified via full `app-bana-service` suite — see `docs/planning/TENANT_ISOLATION_IMPLEMENTATION_TASKS.md` S4.3 for the exact count)  
**Test Results:** 131/131 passing for the security-feature subset in [Testing](#testing) (100%) — see the tracker doc for the full module total  
**Next Review:** Quarterly security audit
