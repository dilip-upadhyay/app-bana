# Tenant & App Isolation Security Plan

**Status:** 📝 DRAFT — awaiting approval before S1 begins.
**Owner:** AppBana core team
**Position in master roadmap:** Cross-cutting security hardening track. Should land **before** any wider multi-tenant GA / enterprise rollout, and logically **before** relying on [Maker-Checker](./MAKER_CHECKER_PLAN.md) as a security control — maker-checker protects against a rogue insider *inside* a tenant/app; without the isolation fixed here, an outsider in a completely different tenant can reach the same rows directly, bypassing roles entirely.
**Trigger:** A security audit (2026-07-31), triggered by the question *"is Tenant A fully isolated from Tenant B, and is each of Tenant A's apps isolated from its siblings?"*, found the answer is **no** on both axes at the authentication/authorization layer, despite the physical data model already being correctly tenant/app-scoped. Full verified evidence trail is recorded in repo agent-memory (`security-multi-tenant-isolation.md`) and condensed in [Current state](#current-state-verified-findings) below.

**Related active plans:**
- [Maker-Checker Plan](./MAKER_CHECKER_PLAN.md) — sibling epic. Its `appbana_user_roles` table (tenant+app+entity scoped) is the direct precedent for this plan's `appbana_app_members` table (tenant+app scoped). Reuses the same service/guard/bootstrap shape deliberately.
- [Enterprise Capabilities Plan](./ENTERPRISE_CAPABILITIES_PLAN.md) — assumes tenant isolation is solved; this plan is a prerequisite, not a follow-on.
- `ACTIVE_TASKS.md` — should be updated with live status once S1 begins (not modified by this document).

---

## Table of Contents

1. [TL;DR](#tldr)
2. [Why we are doing this now](#why-we-are-doing-this-now)
3. [Non-goals](#non-goals)
4. [Current state (verified findings)](#current-state-verified-findings)
5. [Target model](#target-model)
6. [Data model additions](#data-model-additions)
7. [Sub-phase S1 — Tenant boundary on app management](#sub-phase-s1--tenant-boundary-on-app-management)
8. [Sub-phase S2 — Per-app membership model](#sub-phase-s2--per-app-membership-model)
9. [Sub-phase S3 — Entity data API enforcement](#sub-phase-s3--entity-data-api-enforcement)
10. [Sub-phase S4 — Credential hygiene](#sub-phase-s4--credential-hygiene)
11. [Sub-phase S5 — Regression guards + ai-builder trust chain](#sub-phase-s5--regression-guards--ai-builder-trust-chain)
12. [Cross-cutting concerns](#cross-cutting-concerns)
13. [File-level change map](#file-level-change-map)
14. [Open decisions still needed from product](#open-decisions-still-needed-from-product)

---

## TL;DR

Close seven confirmed isolation gaps by introducing **two enforcement layers that don't exist today**:
a tenant-membership check (does this session's own tenant match the tenant it's acting on) and a new
**per-app membership** check (does this session's user actually belong to this specific app, not just
this tenant) — per the explicit decision to use **restricted, explicit per-app membership** rather than
"every user in a tenant can touch every app in it."

| # | Sub-phase | Deliverable | Est. |
|---|---|---|---|
| S1 | Tenant boundary on app management | `AppRoutes` list/get/update/delete can no longer be pointed at another tenant's data | ~4 hr |
| S2 | Per-app membership model | `appbana_app_members` table, `AppMembershipService`, `AppAccessGuard`, bootstrap + backfill | ~7.5 hr |
| S3 | Entity data API enforcement | `/api/{tenant}_{app}_{entity}` requires real membership or a scoped runtime session, not an optional global token | ~7.25 hr |
| S4 | Credential hygiene | Real BCrypt hashing (transparent migration), CSRF decision + doc correction | ~3.5 hr |
| S5 | Regression guards + ai-builder | Capstone cross-tenant test suite, route census guard, ai-builder trusts a verified identity, not client-supplied ids | ~4.5 hr |

**Total scope:** ~27 hours. S1 → S2 → S3 is the strict serial path (each is a prerequisite for the next). S4 is independent and parallel-safe. S5's test suite needs S1–S3 finished; its ai-builder half can start once S2 exists.

---

## Why we are doing this now

AppBana's pitch is "each tenant's apps are independent, and tenants are independent of each other." Today
that is true **only at the physical-storage level** (separate tables per `{tenantId}_{appId}_{entity}`).
It is not true at the layer that actually decides who may call the API that reaches those tables:

1. **The exploit chain requires zero privilege escalation.** Self-registration is open, it hands out a
   fresh `tenantId` for free, and from there `AppRoutes` and the entity-data API both trust path
   parameters over the caller's real identity. A brand-new, legitimate signup can already list, edit,
   and delete another customer's apps and data.
2. **Maker-checker is meaningless without this.** Phase C's roles answer "may *this tenant's* user
   approve *this tenant's* record", which presumes the request is even scoped to that tenant. It isn't
   enforced yet — an attacker doesn't need to fight the role model, they can walk around it.
3. **Every regulated-vertical conversation asks about this before it asks about approvals.** Data
   segregation between customers is usually the first security question in a vendor review, ahead of
   workflow controls.
4. **The fix compounds.** The same per-app membership table this plan introduces (S2) is also the
   natural place to eventually hang Studio-side RBAC (who can edit schema vs. only view data), so this
   is infrastructure the platform needs regardless.

---

## Non-goals

- **SSO / OAuth / SAML integration.** Out of scope; separate initiative if/when needed.
- **A general RBAC/permission-scopes system** beyond "is this user a member of this app" plus the
  existing maker/checker entity roles. Role *granularity* within an app (e.g. "can edit schema" vs.
  "can only view data") is a future extension of the `appbana_app_members.role` column, not built here.
- **Removing the global `adminToken`/`readToken` model.** Repurposed as an optional platform-operator
  break-glass override (S3), not deleted — some deployments may still want it for support tooling.
- **Rate limiting / WAF / network-level protections.** `RateLimitMiddleware` already exists and is out
  of scope for this plan.
- **Encryption at rest, secrets management overhaul, key rotation.** Separate concern.
- **Multi-level org hierarchies** (teams within a tenant, nested permission groups). `appbana_app_members`
  is intentionally a flat (tenant, app, user) → role mapping in v1.

---

## Current state (verified findings)

Condensed from the full audit (file:line evidence in repo agent-memory). Each row names the file that
owns the fix.

| # | Finding | Fixed in |
|---|---|---|
| 1 | `AppRoutes` list/get/update/delete trust the path `tenantId` with no check against the caller's own tenant; update/delete have **no ownership check at all** | S1, S2 |
| 2 | `/api/{tenant}_{app}_{entity}` is excluded from `SessionMiddleware` entirely and gated only by an optional **global** admin/read token, off by default | S3 |
| 3 | The admin/read token has no tenant concept — one shared secret unlocks every tenant's data | S3 (repurposed as break-glass, decoupled from "is auth even checked") |
| 4 | Passwords compared in plaintext in `UserManager` and `GenericAppAuthController` | S4 |
| 5 | Self-registration is fully open with no invite/approval gate | Accepted as a product decision (out of scope — flagged, not fixed, see [Open decisions](#open-decisions-still-needed-from-product)) |
| 6 | `CsrfMiddleware` is coded but never registered in the real router pipeline; docs claim otherwise | S4 |
| 7 | `GenericAppAuthController.login()` (runtime end-user login) issues no session/token at all | S3 |

What already works correctly and must not regress: `AppManager`'s SQL is tenant-filtered; `SchemaManager`'s
3-arg `loadSchema` builds a properly scoped key; `RoleRoutes`/`ApprovalService` (maker-checker) already
do real (tenant, app, entity, user) scoped checks; `FileRoutes` downloads are triple-scoped and covered
by `FileRoutesTenantIsolationTest`.

---

## Target model

Two distinct callers hit the same entity-data API today, and the fix must keep telling them apart:

```
┌─────────────────────────────┐          ┌──────────────────────────────┐
│   Studio user (builder)     │          │  Runtime end-user (deployed  │
│   session: tenant-wide      │          │  app's own login)            │
└──────────────┬───────────────┘          └───────────────┬──────────────┘
               │                                          │
               ▼                                          ▼
   1. TenantAccessGuard                         3. Runtime session must be
      session.tenantId == path tenantId?           SCOPED to exactly this appId
               │ yes                                       │ yes
               ▼                                          ▼
   2. AppAccessGuard                             4. entity key's (tenant, app)
      (tenantId, appId, userId) is a                must equal the session's
      row in appbana_app_members?                    scoped (tenantId, appId)
               │ yes                                       │ yes
               └───────────────┬──────────────────────────┘
                                ▼
                  GenericEntityRoutes handler runs
                  (falls through to optional break-glass
                   admin token only if neither path matches)
```

**Key rule:** a Studio session (tenant-wide) and a Runtime end-user session (single-app-scoped) are
never interchangeable. A Runtime session for App 1 must be rejected by App 2's entity routes even
though both apps belong to the same tenant — this is what actually delivers "the tenant's apps are
independent of each other" for end-users, not just for the Studio builder.

---

## Data model additions

### `appbana_app_members` (new platform table — mirrors `appbana_user_roles`'s shape)

```sql
CREATE TABLE appbana_app_members (
  tenant_id    VARCHAR(255) NOT NULL,
  app_id       VARCHAR(255) NOT NULL,
  user_id      VARCHAR(255) NOT NULL,
  role         VARCHAR(20) NOT NULL DEFAULT 'member',  -- 'owner' | 'member'
  granted_by   VARCHAR(255) NOT NULL,
  granted_at   TIMESTAMP NOT NULL DEFAULT now(),
  PRIMARY KEY (tenant_id, app_id, user_id)
);
CREATE INDEX idx_app_members_user ON appbana_app_members(tenant_id, user_id);
```

`owner` may manage membership and delete the app; `member` may build/view/edit but not remove other
members or delete the app. Finer roles (e.g. schema-editor vs. data-only) are a future extension of
this same column — not built in v1.

### `SessionData` (extend existing `SessionService`)

- Add `tenantId` (captured once at login from `User.tenantId`) — avoids a DB round-trip on every
  tenant-boundary check.
- Add optional `scopedAppId` (null for a normal Studio session; set to a specific `appId` for a Runtime
  end-user session created by `GenericAppAuthController`). A non-null `scopedAppId` means this session
  is valid **only** for that app's entity routes, never for `AppRoutes`/Studio management endpoints.

### Backfill requirement

`appbana_app_members` must be seeded for every **existing** app row (`owner` membership from
`AppMetadata.getAuthor()`) before `AppAccessGuard` goes live in S2, or every existing app becomes
inaccessible to its own creator the instant enforcement is enabled. Same principle as this repo's
`V0__bootstrap_meta_tables.sql` lesson: the migration must work against data that already exists, not
just fresh installs.

---

## Sub-phase S1 — Tenant boundary on app management

**Goal:** No session can act on a tenant other than its own, anywhere in `AppRoutes`. This alone closes
the worst cross-tenant IDOR without waiting on the full per-app membership model.

| # | Task | Where | Est. |
|---|---|---|---|
| S1.1 | Add `tenantId` (and reserve `scopedAppId`) to `SessionData`; populate `tenantId` at login from `User.tenantId` | `SessionService.java`, `AuthenticationController.java` | 45 min |
| S1.2 | New `TenantAccessGuard.requireOwnTenant(session, pathTenantId)` — 403 on mismatch, with a clear error body (not a silent empty list) | new `com.appbana.security.TenantAccessGuard` | 45 min |
| S1.3 | Wire the guard into every `AppRoutes` handler that takes `{tenantId}` in its path: list, get, update, delete | [`AppRoutes.java`](../../app-bana-service/src/main/java/com/appbana/server/routes/AppRoutes.java) | 60 min |
| S1.4 | Startup warning: log a loud, repeated `WARN` while `AuthService.authEnabled(cfg)==false` so this is never silently shipped to production | `ApiServer.java` startup path | 30 min |
| S1.5 | `CrossTenantAppAccessTest` — tenant B's session must not list/get/update/delete tenant A's apps (currently would pass trivially; must fail before the fix, pass after) | new test | 60 min |

### Exit criteria — S1

- [ ] A session for Tenant B gets 403 on `GET/PUT/DELETE /appbana-studio/{TenantA}/apps/...` regardless of appId.
- [ ] Tenant A's own users are unaffected.
- [ ] Server logs a visible warning on every boot where global auth is disabled.

---

## Sub-phase S2 — Per-app membership model

**Goal:** Within one tenant, a user can only manage the specific apps they've been granted membership
on — not every app the tenant owns. (Per product decision: **explicit per-app membership**, not
tenant-wide access.)

| # | Task | Where | Est. |
|---|---|---|---|
| S2.1 | Liquibase changeset for `appbana_app_members` (schema above) | `app-bana-service/src/main/resources/db/changelog/` | 30 min |
| S2.2 | `AppMembershipService` — `grant/revoke/listMembers/isMember(tenantId, appId, userId)/isOwner(...)`, mirroring `UserRoleService`'s shape | new `com.appbana.security.AppMembershipService` | 75 min |
| S2.3 | Bootstrap: app creator is auto-granted `owner` membership at creation time (same pattern as maker-checker's C1.5) | [`AppRoutes.java`](../../app-bana-service/src/main/java/com/appbana/server/routes/AppRoutes.java) create handler | 30 min |
| S2.4 | **Backfill migration** — for every pre-existing app row, insert an `owner` membership from `AppMetadata.getAuthor()` (fallback: any tenant admin if author is missing/`"system"`) | new Liquibase data migration or one-time startup task | 60 min |
| S2.5 | `AppAccessGuard` (supersedes S1's tenant-only check with tenant **+** membership): wire into `AppRoutes` list/get/update/delete, and into the `SchemaRoutes`/`RoleRoutes` call sites that currently only call `AppAuthorization.isAppOwnerOrSystem` | `AppRoutes.java`, `SchemaRoutes.java`, `RoleRoutes.java` | 90 min |
| S2.6 | `GET/POST/DELETE /api/tenants/{t}/apps/{a}/members` — membership management endpoints, `owner`-only | new `AppMembershipRoutes.java` | 60 min |
| S2.7 | Studio frontend: verify the app switcher/list only ever renders what the (now correctly filtered) server response contains — no client-side "show all tenant apps" assumption left over | `app-bana-studio/src/features/**` (session/workspace store) | 45 min |
| S2.8 | Tests: `AppMembershipGuardTest`, `AppRoutesMembershipTest`, plus a **route census test** enumerating every `{appId}`-path route registration and asserting each passes through `AppAccessGuard` | new tests | 90 min |

### Exit criteria — S2

- [ ] A Tenant A user who is not a member of Tenant A's App 2 gets 403 managing App 2, while still
      managing their own App 1 normally.
- [ ] Every app that existed before this migration is still manageable by its original creator
      immediately after deploy (backfill verified against a copy of production-shaped data).
- [ ] Only an `owner` member can grant/revoke membership or delete the app.
- [ ] Route census test fails if a new `{appId}` route is added without the guard.

---

## Sub-phase S3 — Entity data API enforcement

**Goal:** `/api/{tenantId}_{appId}_{entityName}` stops trusting an optional global token and instead
requires either real Studio app-membership or a runtime session correctly scoped to that one app.

| # | Task | Where | Est. |
|---|---|---|---|
| S3.1 | Use S2's `scopedAppId` field on `SessionData` for runtime sessions (already reserved in S1.1's model) | `SessionService.java` | 30 min |
| S3.2 | `EntityAccessGuard` — parses the entity key back into `(tenantId, appId, entityName)` and allows the request if: (a) a Studio session's user is an `appbana_app_members` member of that `(tenantId, appId)`, **or** (b) a runtime session whose `scopedAppId` equals that `appId`, **or** (c) the app is explicitly marked publicly readable (S3.5) and the request is a `GET`. Optional global admin token remains as a break-glass override, evaluated only if none of the above match — not as a bypass for the whole check | new `com.appbana.security.EntityAccessGuard` | 120 min |
| S3.3 | `GenericAppAuthController.login()` issues a real session via `SessionService.createSession(...)` with `scopedAppId` set to that app — this is what makes one deployed app's login unable to reach a sibling app's data | [`GenericAppAuthController.java`](../../app-bana-service/src/main/java/com/appbana/api/GenericAppAuthController.java) | 60 min |
| S3.4 | Replace the 16+ `if (AuthService.authEnabled(cfg)) {...}` blocks in `GenericEntityRoutes` with calls into `EntityAccessGuard` (auth is now always evaluated, not conditionally skipped) | [`GenericEntityRoutes.java`](../../app-bana-service/src/main/java/com/appbana/server/routes/GenericEntityRoutes.java) | 90 min |
| S3.5 | Add an explicit `publicRead: boolean` flag on app/entity metadata (default `false`) for the legitimate "this app's data should be publicly browsable" use case — without this, S3 would break intentionally-public apps | `AppMetadata`/`EntitySchema` + `SchemaRoutes.java` | 45 min |
| S3.6 | Tests: `CrossTenantEntityAccessTest`, `CrossAppEntityAccessTest` (App 1 member cannot touch App 2's entities in the *same* tenant), `RuntimeSessionScopedToSingleAppTest` | new tests | 90 min |

### Exit criteria — S3

- [ ] With global auth disabled (today's default), entity data is **not** reachable without a valid,
      correctly-scoped session — the current "wide open by default" posture is gone.
- [ ] A Runtime end-user session for App 1 gets 403 on App 2's entity routes, same tenant.
- [ ] An app explicitly marked `publicRead` still serves anonymous `GET` requests (no regression for
      the legitimate public-app case).
- [ ] The break-glass admin token still works as an override when explicitly configured, but its
      absence no longer means "no check happens".

---

## Sub-phase S4 — Credential hygiene

**Goal:** Passwords are never compared or stored in plaintext, and CSRF posture matches what the docs
claim (fix the code or fix the doc — not left contradictory).

| # | Task | Where | Est. |
|---|---|---|---|
| S4.1 | Wire the existing `PasswordService` (BCrypt) into `UserManager`: new registrations hash on write; on a successful **plaintext-compare** login for a legacy row, immediately rehash and persist — transparent migration, no forced reset, per product decision | `UserManager.java` | 45 min |
| S4.2 | Same transparent-rehash treatment for `GenericAppAuthController`'s runtime end-user table — needs a short investigation into how runtime end-user rows get their password column written (likely a plain entity insert on a `password`-typed field) to find every write path that needs hashing, not just login | `GenericAppAuthController.java` + wherever runtime end-user records are created | 60 min |
| S4.3 | Decide and act on CSRF: current auth is bearer-token-in-header, not cookie-based, so classic CSRF does not apply today. Recommendation: remove the dead `CsrfMiddleware` registration references from docs and delete the unused middleware (or explicitly wire it in only if cookie-based auth is ever introduced) | `docs/features/SECURITY_FEATURES.md`, `CsrfMiddleware.java` | 30 min |
| S4.4 | Correct `docs/features/SECURITY_FEATURES.md` end-to-end against the post-S4 real state (remove the false BCrypt-already-done and wired-CSRF claims; the LitElement snippets are also stale per this repo's retired `app-bana-ui/` and should be replaced or removed) | `docs/features/SECURITY_FEATURES.md` | 30 min |
| S4.5 | Tests: `PasswordRehashOnLoginTest` (plaintext row → hashed after one login), `NewRegistrationIsHashedTest` | new tests | 45 min |

### Exit criteria — S4

- [ ] No code path compares a raw password string to a stored value.
- [ ] Every pre-existing plaintext row is transparently upgraded to BCrypt the next time its owner logs in — no user-visible disruption, no forced reset.
- [ ] `SECURITY_FEATURES.md` accurately reflects what is actually running.

---

## Sub-phase S5 — Regression guards + ai-builder trust chain

**Goal:** This defect class (a route trusts a client-supplied id instead of a verified identity) cannot
quietly reappear on a new route, and the AI Builder service stops being a second, independent place
where the same mistake could be made.

| # | Task | Where | Est. |
|---|---|---|---|
| S5.1 | `AiChatController`/`AgentContext` verifies the caller's `tenantId`/`appId` against app-bana-service (reusing `AppAccessGuard`/`EntityAccessGuard` via a lightweight internal check) instead of trusting the client-supplied JSON body fields | [`AiChatController.java`](../../ai-builder/src/main/java/com/appbana/ai/api/AiChatController.java) | 90 min |
| S5.2 | `CrossTenantIsolationTest` capstone suite — 2 tenants × 2+ apps each, asserting no session from one tenant/app combination can read/write/delete another's apps, roles, files, or entity rows | new integration test | 90 min |
| S5.3 | Route **census** regression test — enumerates every route registration carrying a `{tenantId}` and/or `{appId}` path parameter and asserts each is wrapped by the appropriate guard, so a future new route can't silently skip it | new test (builds on S2.8's narrower version) | 60 min |
| S5.4 | Document the enforced model in `.github/copilot-instructions.md` (new section, mirroring how Maker-Checker got documented) | `.github/copilot-instructions.md` | 30 min |

### Exit criteria — S5

- [ ] Capstone cross-tenant suite passes and is wired into CI (not a one-off manual run).
- [ ] Adding a new route with an unwrapped `{tenantId}`/`{appId}` path param fails the census test.
- [ ] ai-builder no longer trusts client-supplied tenant/app identity for any tool call.

---

## Cross-cutting concerns

### Security
- `AppAccessGuard`/`EntityAccessGuard` become the **sole** place these checks happen — a bypass means
  breaking one file, not auditing every route by hand again.
- The break-glass admin/read token is decoupled from "is authorization checked at all"; its absence no
  longer disables enforcement, only removes the override.
- Runtime end-user sessions are single-app-scoped by construction (`scopedAppId`), so a compromised
  runtime credential for one app cannot be replayed against a sibling app.

### Backward compatibility — this is a breaking change for today's default (no-auth) local/dev setup
Right now, local development works with `authEnabled()==false` and no session on entity routes at all.
After S3, that stops working by design. This must ship with:
- The S2.4 backfill so every existing local app keeps working for its creator.
- Clear updates to `scripts/start-everything.*` / onboarding docs if a first-run bootstrap step (e.g.
  auto-creating a default tenant admin + membership) is needed to keep `pnpm dev`-style local setup
  frictionless.

### Testing strategy
- **Unit:** guard logic (`TenantAccessGuard`, `AppAccessGuard`, `EntityAccessGuard`) against every
  allow/deny combination.
- **Integration:** backfill migration against production-shaped fixture data; rehash-on-login.
- **Capstone (S5.2):** the full 2-tenant × 2-app matrix — this is the test that actually answers the
  user's original question, and should be kept green permanently, not run once.
- Per repo convention (`testing-conventions.md`): once implementation begins, fixes must additionally be
  verified through the actual browser UI (Studio 5174 / Runtime 5175), not backend/API tests alone.

### Performance
- Both new guards are a single indexed PK lookup (`appbana_app_members`) or an in-memory `SessionData`
  field check — same order of overhead as `ApprovalGuard`'s existing filtering (<5ms).

### Rollout order
**Strict serial:** S1 → S2 → S3 (each is a prerequisite — S3's membership check depends on S2's table,
S2's guard supersedes S1's). **Parallel-safe:** S4 (independent of the others). **Last:** S5 (the capstone
test needs S1–S3 to exist to have something real to assert; the ai-builder half can start as soon as S2's
membership model is in place).

### Documentation
- `.github/copilot-instructions.md` — new section on the enforced tenant/app isolation model (S5.4).
- `docs/features/SECURITY_FEATURES.md` — corrected in place (S4.4), not superseded by a new doc.

---

## File-level change map

**New files (backend):**
- `app-bana-service/src/main/java/com/appbana/security/TenantAccessGuard.java` (S1)
- `app-bana-service/src/main/java/com/appbana/security/AppMembershipService.java` (S2)
- `app-bana-service/src/main/java/com/appbana/security/AppAccessGuard.java` (S2)
- `app-bana-service/src/main/java/com/appbana/security/EntityAccessGuard.java` (S3)
- `app-bana-service/src/main/java/com/appbana/server/routes/AppMembershipRoutes.java` (S2)
- Liquibase changeset: `appbana_app_members` table + backfill data migration (S2)

**Modified files (backend):**
- `SessionService.java` — `tenantId` + `scopedAppId` on `SessionData` (S1, S3)
- `AuthenticationController.java` — populate `tenantId` at login (S1)
- `AppRoutes.java` — guard wiring (S1, S2)
- `SchemaRoutes.java`, `RoleRoutes.java` — upgrade to `AppAccessGuard` (S2)
- `GenericEntityRoutes.java` — replace `authEnabled()` blocks with `EntityAccessGuard` (S3)
- `GenericAppAuthController.java` — issue scoped session; hash passwords (S3, S4)
- `UserManager.java` — BCrypt wiring + transparent rehash (S4)
- `CsrfMiddleware.java` — remove or genuinely wire in (S4)
- `docs/features/SECURITY_FEATURES.md` — corrected (S4)
- `ApiServer.java` — startup warning when auth disabled (S1)

**Frontend:**
- `app-bana-studio/src/features/**` (session/workspace store) — verify no client-side "all tenant apps" assumption remains (S2)

**AI Builder:**
- `ai-builder/src/main/java/com/appbana/ai/api/AiChatController.java` — verified identity instead of trusted body fields (S5)

**Docs:**
- `.github/copilot-instructions.md` — new isolation-model section (S5)

---

## Open decisions still needed from product

These were **not** blocking for writing this plan but do affect scope/sequencing before implementation
starts on the affected phase:

1. **Self-registration policy (Finding #5).** Currently fully open with no invite/approval gate. This
   plan does not close it — closing the tenant/app IDOR means an open signup can no longer reach *other*
   tenants' data, but the registration flow itself is unchanged. Confirm whether an invite-only or
   admin-approval gate is wanted as a follow-up.
2. **First-run local/dev experience post-S3.** Once entity routes require real membership, what should
   a fresh `pnpm dev` / `start-everything` checkout do out of the box — auto-create a default tenant +
   membership, or require an explicit signup step? Affects S2.4/S3 rollout docs.
3. **Runtime end-user password write path (S4.2).** Needs a short investigation (not yet done) into
   exactly where runtime end-user records get their password set, to confirm every write path is
   covered by the hashing fix, not just login.

---

*Last updated: 2026-07-31 · Author: AppBana core team · Status: DRAFT — awaiting approval before S1 begins.*
