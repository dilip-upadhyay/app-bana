# AppBana Security Features - Complete Guide

**Last Updated:** April 2026 (CSRF section corrected — S4.3; Password Security + Frontend Integration corrected end-to-end — S4.4)  
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

> [!IMPORTANT]
> **S4.4 note:** the guarantees below describe `PasswordService` itself, which has always hashed
> correctly in isolation — but before S4.1/S4.2 (tenant-isolation security initiative) neither of the
> two real login/write paths actually called it: `UserManager` (Studio users) and
> `GenericAppAuthController` (runtime end-user auth + generic-entity `password`-typed fields) compared
> and stored raw plaintext directly, bypassing `PasswordService` entirely. So "passwords never stored
> in plain text" was aspirational documentation, not yet a true statement about this app. S4.1 and
> S4.2 wired both call sites to `PasswordService` — see [Where Hashing Is Applied](#where-hashing-is-applied)
> below — so the guarantees are now genuinely true end-to-end, not just true of one isolated class.

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

### Where Hashing Is Applied

**S4.1 — `UserManager.java`** (Studio/AI-Builder users): `register()` hashes on write; `authenticate()`
verifies against either a BCrypt hash or a legacy plaintext value (`looksLikeBcryptHash()` checks the
`$2a$`/`$2b$`/`$2y$` prefix), and on a **successful** legacy-plaintext login, transparently rehashes and
persists the BCrypt value immediately — no forced password reset, no dual-write path. Every row is
migrated the first time its owner logs in; unvisited legacy rows remain plaintext until then.

**S4.2 — `GenericAppAuthController.java`** (runtime end-user auth + any generic entity with a
`password`-typed field): the same fetch-by-email/verify/transparent-rehash treatment, plus hash-on-write
for **every** path that sets a password column — not just login — so a record created or edited through
the generic entity API is hashed the moment it's written, never only at first login.

**S4.8 — read-path redaction:** hashing on write is a separate concern from whether the hash is ever
returned to a client. `EntityCrudService.redactSensitiveColumns()`/`redactSensitiveColumnsFromList()`
omit any column whose name contains `password` or `secret` (case-insensitive) from every client-facing
GET response (simple/advanced list, single record, bulk export, generic audit, and the approval
pending-queue/audit-trail routes) — the hash is written and used internally (e.g. for approval
revision-merges), but a client never sees it at all.

### Security Guarantees

✅ Passwords never stored in plain text (as of S4.1/S4.2 — see the note above)  
✅ Salts are cryptographically random  
✅ Verification uses constant-time comparison  
✅ Invalid hashes return false (no exceptions)  
✅ Work factor can be increased in future  
✅ Password/secret columns are omitted from every client-facing read (S4.8)  

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
> **S4.4:** the code samples below now cite the current React `app-bana-studio`/`app-bana-runtime`
> frontends directly (file + line references given for each), replacing the retired LitElement
> `app-bana-ui` samples S4.3 flagged. Two real gaps were found while rewriting this section and are
> documented honestly rather than papered over: neither frontend does any client-side
> password-strength/confirm-password validation (§3 below), and neither handles HTTP 429 at all
> despite the backend's rate limiter genuinely returning it (§4 below, see also
> [Rate Limiting](#rate-limiting)).

#### 1. Session Token Inclusion

```typescript
// app-bana-shared/src/api-client.ts:97-100 — every authed call attaches a bearer token
export async function listApps(tenantId: string, token: string): Promise<AppMeta[]> {
  const res = await authedFetch(`${BACKEND}/appbana-studio/${tenantId}/apps`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  ...
```

**Header:** `Authorization: Bearer <token>` — not `X-Session-Token` as this section previously showed.
Both forms are accepted by the backend (`SessionMiddleware.create()` checks `X-Session-Token` first,
then falls back to `Authorization: Bearer`), but the frontend has only ever used the latter.  
**Storage:** Studio persists the token via Zustand's `persist` middleware, `localStorage` key
`appbana-session` ([`app-bana-studio/src/stores/session.ts`](../../app-bana-studio/src/stores/session.ts)).
Runtime uses its own plain `localStorage` key `appbana_token`, read via `getRuntimeToken()`
([`app-bana-runtime/src/runtime/qualifyEntityKey.ts`](../../app-bana-runtime/src/runtime/qualifyEntityKey.ts))
— the two frontends do not share a token store.  
**Lifetime:** 30 minutes with sliding window  

#### 2. Session-Expiry Recovery (401)

```typescript
// app-bana-shared/src/api-client.ts:16-22
export async function authedFetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  const res = await fetch(input, init);
  if (res.status === 401 && typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('appbana:auth:expired'));
  }
  return res;
}
```

```tsx
// app-bana-studio/src/features/auth/AuthGate.tsx:23-41
useEffect(() => {
  const handler = () => {
    if (useSessionStore.getState().token) {
      setSessionExpired(true);
      clearSession();
      resetSessionScopedState(); // also clears every session-scoped store — S2.8 fix
    }
  };
  window.addEventListener('appbana:auth:expired', handler);
  return () => window.removeEventListener('appbana:auth:expired', handler);
}, [clearSession]);
```

**Studio:** listens globally (above) and falls through to the login form with a "Your session expired"
banner — no hard redirect. `ChatPane.tsx` also re-dispatches this same event on an in-stream
`auth_expired` SSE event, since that HTTP response itself is a 200.  
**Runtime:** has **no equivalent listener** — confirmed via a repo-wide search for `auth:expired` in
`app-bana-runtime/src` (zero matches). A 401 there is handled per-call by whichever hook/component made
the request, not through this shared recovery path.

#### 3. Password Validation

> [!WARNING]
> **Not currently implemented in either frontend.** Both signup/login forms —
> [`AuthGate.tsx`](../../app-bana-studio/src/features/auth/AuthGate.tsx) (Studio, `<input type="password">`)
> and [`LoginPage.tsx`](../../app-bana-runtime/src/pages/LoginPage.tsx) (Runtime) — mark the password
> field `required` only: no minimum-length check, no letters+numbers check, and no confirm-password
> field exists anywhere in either frontend. `PasswordService`'s server-side hashing
> (§ [Password Security](#password-security)) is unconditional and doesn't depend on any client-side
> check, so a weak password is still hashed safely — the user just gets no feedback before submitting
> one. The previous version of this section showed a LitElement `validateField()` method; it described
> a component that no longer exists in either React frontend and has been removed rather than repeated.

#### 4. Rate-Limit (429) Handling

> [!WARNING]
> **Not currently implemented in either frontend.** The backend's `RateLimitMiddleware` genuinely
> returns a 429 (§ [Rate Limiting](#rate-limiting)), but `authedFetch()` only special-cases 401 — there
> is no `response.status === 429` handling anywhere in `app-bana-shared/src`, `app-bana-studio/src`, or
> `app-bana-runtime/src`. A rate-limited request today surfaces as a generic fetch failure to whatever
> error handling the calling component already has, with no `Retry-After` messaging. The e2e suite
> already defensively skips a 429 it wasn't expecting; a real UI affordance does not exist yet. The
> previous version of this section showed a matching `response.status === 429` code sample; it did not
> correspond to any real source file and has been removed rather than repeated here.

#### 5. Loading / Submit-Disabled State

```tsx
// app-bana-studio/src/features/auth/AuthGate.tsx:15, 48, 65, 128-135
const [loading, setLoading] = useState(false);
// ...
setLoading(true);
try { /* login/register call */ } finally { setLoading(false); }
// ...
<button type="submit" disabled={loading} className="... disabled:opacity-50 ...">
  {loading ? 'Please wait…' : tab === 'login' ? 'Sign In' : 'Create Account'}
</button>
```

**Prevents:** double-submit during the login/register request. Runtime's equivalent form uses a shared
`<Button loading={loading}>` component
([`app-bana-runtime/src/runtime/Button.tsx`](../../app-bana-runtime/src/runtime/Button.tsx)) that applies
the same `disabled={disabled || loading}` pattern internally.

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
- [ ] Password fields validated (8+ chars, letters+numbers) — not currently done anywhere; see [§3](#frontend-integration)
- [ ] confirmPassword field excluded from submission
- [ ] Error messages user-friendly (not exposing internals)
- [ ] Loading states prevent double-submit
- [ ] 401 triggers `appbana:auth:expired` → session clear → login form (no hard redirect — see [§2](#frontend-integration))
- [ ] 429 shows retry message — not currently done anywhere; see [§4](#frontend-integration)
- [ ] Backend validates all security checks
- [ ] Tests cover security scenarios

---

## Troubleshooting

### Common Issues

**Q: Getting 401 session errors?**  
A: Check your session token is present and not expired (30 min sliding window). Studio persists it via
Zustand under the `appbana-session` `localStorage` key; Runtime uses a plain `localStorage.getItem('appbana_token')` (see [Frontend Integration](#frontend-integration)).

**Q: Getting 429 rate limit errors?**  
A: Wait for the time specified in the response's `Retry-After` header (max 1 minute). Neither frontend
currently surfaces this automatically — see [§4 Rate-Limit (429) Handling](#frontend-integration) — so
today this means inspecting the raw HTTP response (e.g. browser devtools' Network tab).

**Q: Password validation not working?**  
A: There is no client-side password-strength or confirm-password validation in either frontend today
(see [§3 Password Validation](#frontend-integration)) — `PasswordService` hashes whatever is submitted
regardless of strength, so a weak password is still stored safely, but nothing currently blocks
submitting one.

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

- **Builder Database:** (AI Builder RAG knowledge sources — reconciled with `CsrfMiddleware`'s S4.3
  deletion by S4.9; no longer describe it as live)
  - `builder-database/09-authentication.json` (v1.2.1)
  - `builder-database/10-form-patterns.json` (v1.1.1)
  - `builder-database/99-capabilities-index.json` (v1.5.1)

---

**Status:** Production Ready ✅  
**Last Tested:** April 2026 (this pass: `CsrfMiddleware` deletion verified via full `app-bana-service` suite — see `docs/planning/TENANT_ISOLATION_IMPLEMENTATION_TASKS.md` S4.3 for the exact count)  
**Test Results:** 131/131 passing for the security-feature subset in [Testing](#testing) (100%) — see the tracker doc for the full module total  
**Next Review:** Quarterly security audit
