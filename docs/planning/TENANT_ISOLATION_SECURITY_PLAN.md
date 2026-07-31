# Tenant & App Isolation Security Plan

**Status:** 📝 DRAFT — 🔴 **Review round 1 complete (2026-07-31): S0 inserted, S1–S3 rescoped, do not start S0/S1 without reading the findings below.**
**Owner:** AppBana core team
**Position in master roadmap:** Cross-cutting security hardening track. Should land **before** any wider multi-tenant GA / enterprise rollout, and logically **before** relying on [Maker-Checker](./MAKER_CHECKER_PLAN.md) as a security control — maker-checker protects against a rogue insider *inside* a tenant/app; without the isolation fixed here, an outsider in a completely different tenant can reach the same rows directly, bypassing roles entirely.
**Trigger:** A security audit (2026-07-31), triggered by the question *"is Tenant A fully isolated from Tenant B, and is each of Tenant A's apps isolated from its siblings?"*, found the answer is **no** on both axes at the authentication/authorization layer, despite the physical data model already being correctly tenant/app-scoped. Full verified evidence trail is recorded in repo agent-memory (`security-multi-tenant-isolation.md`) and condensed in [Current state](#current-state-verified-findings) below.
**Build prerequisite (blocks every phase's tests):** `mvn test` currently fails `default-testCompile` with `release version 25 not supported` under this repo's Maven toolchain (found while probing this plan for review). Fix this first — none of S0–S5's exit-criteria tests can run otherwise. Tracked as S0.0.

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
5. [Review round 1 — blockers and additional findings](#review-round-1--blockers-and-additional-findings)
6. [Target model](#target-model)
7. [Data model additions](#data-model-additions)
8. [Sub-phase S0 — Unify identity resolution + route census](#sub-phase-s0--unify-identity-resolution--route-census)
9. [Sub-phase S1 — Tenant boundary on app management](#sub-phase-s1--tenant-boundary-on-app-management)
10. [Sub-phase S2 — Per-app membership model](#sub-phase-s2--per-app-membership-model)
11. [Sub-phase S3 — Entity data API enforcement](#sub-phase-s3--entity-data-api-enforcement)
12. [Sub-phase S4 — Credential hygiene](#sub-phase-s4--credential-hygiene)
13. [Sub-phase S5 — Capstone tests + ai-builder trust chain](#sub-phase-s5--capstone-tests--ai-builder-trust-chain)
14. [Cross-cutting concerns](#cross-cutting-concerns)
15. [File-level change map](#file-level-change-map)
16. [Open decisions still needed from product](#open-decisions-still-needed-from-product)

---

## TL;DR

Close seven confirmed isolation gaps by introducing **two enforcement layers that don't exist today**:
a tenant-membership check (does this session's own tenant match the tenant it's acting on) and a new
**per-app membership** check (does this session's user actually belong to this specific app, not just
this tenant) — per the explicit decision to use **restricted, explicit per-app membership** rather than
"every user in a tenant can touch every app in it."

**Review round 1 (2026-07-31) added a prerequisite phase, S0.** The reviewer found that the identity
extractor these guards would be built on is blind to the header every real client sends, and that the
route inventory this plan was scoped against was incomplete — both are fixed before S1, not during it.
See [Review round 1](#review-round-1--blockers-and-additional-findings) for full detail.

| # | Sub-phase | Deliverable | Est. |
|---|---|---|---|
| S0 | Unify identity resolution + route census | One `resolveIdentity()` every gate uses; machine-generated census of every registered route as the authoritative scope for S1–S3 | ~5 hr |
| S1 | Tenant boundary on app management | `AppRoutes` + `SchemaRoutes`, **every route per the S0 census** (not just list/get/update/delete) can no longer be pointed at another tenant's data | ~6.5 hr |
| S2 | Per-app membership model | `appbana_app_members` table, `AppMembershipService`, `isAppOwnerOrSystem` becomes membership-aware everywhere it's called, bootstrap + backfill | ~8 hr |
| S3 | Entity data API enforcement | Every route in `GenericEntityRoutes` per the S0 census (three route families, not one) requires real membership or a scoped runtime session | ~9 hr |
| S4 | Credential hygiene | Real BCrypt hashing (transparent migration), CSRF decision + doc correction, audit-log actor/tenant hygiene | ~4.5 hr |
| S5 | Capstone tests + ai-builder trust chain | Cross-tenant test suite, ai-builder trusts a verified identity instead of client-supplied ids | ~3 hr |

**Total scope:** ~36 hours (was ~27 hr before review round 1 — the increase is S0 plus the widened S1/S3 scope, not new goals). S0 → S1 → S2 → S3 is the strict serial path. S4 is independent and parallel-safe. S5 needs S1–S3 finished; its ai-builder half can start once S2 exists.

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
5. **Some of this is already broken today, independent of this plan.** Review round 1's live probe
   found that `AuthService.extractUserId` cannot see an `Authorization: Bearer` header — the *only*
   form every real Studio/Runtime/ai-builder request actually sends — on any route excluded from
   `SessionMiddleware`. The runtime write routes that already added a session check for exactly this
   class of gap (`GenericEntityRoutes`'s "B8/B9 FIX" comments) are, as a result, already rejecting real
   traffic with 401 today. This isn't hypothetical future risk; it's a live functional bug this plan's
   own investigation happened to uncover.

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
do real (tenant, app, entity, user) scoped checks; `FileRoutes` **downloads** are triple-scoped and covered
by `FileRoutesTenantIsolationTest`.

> **Correction (review round 1):** the line above originally said "`FileRoutes` already works
> correctly," unqualified. That's true of `handleDownload` only. `POST /api/files/upload` is anonymous
> and takes `tenantId`/`appId` from the request body with no owner check, and
> `FileRoutesTenantIsolationTest` only exercises the download path — a passing test that certifies the
> wrong half of the file. Upload is now in scope for S1.

See [Review round 1](#review-round-1--blockers-and-additional-findings) immediately below for four
additional blockers and six additional high-severity gaps the original audit missed — all confirmed
against source in this worktree before being folded into S0–S5.

---

## Review round 1 — blockers and additional findings

**Reviewer:** Tech Lead / Architect review, 2026-07-31. Method: every claim checked against source in
this worktree, an independent route census, and a live probe against a running backend (five real
tenants present in the shared dev Postgres; all probe side effects reverted). All seven findings above
were reproduced as live requests and confirmed unfalsifiable. This section records what changed as a
result — see individual sub-phases for where each item now lives.

I independently re-verified every blocker and a sample of the high-severity findings directly against
source before accepting them; all checked out, several more severely than first stated. Nothing below
was accepted on the reviewer's word alone without at least a targeted source read.

### 🔴 Blockers (fixed by the new S0, before S1 begins)

| # | Finding | Verified | Fixed in |
|---|---|---|---|
| B1 | `AuthService.extractUserId`'s fallback for middleware-excluded routes reads **only** `X-Session-Token`. Every real client (`api-client.ts`'s `authedFetch`, all ai-builder tools) sends `Authorization: Bearer` — confirmed 25 call sites, zero using `X-Session-Token`. `SessionMiddleware.extractSessionToken` already accepts all three forms; the route-level extractor doesn't. | ✅ confirmed by direct read of both methods + a grep of every `api-client.ts` call site | S0.1 |
| B2 | S1 as originally scoped (4 `/appbana-studio/...` routes) missed a second `/api/{tenantId}/apps/{id}/...` route family in the **same file** that is middleware-excluded and has no auth of any kind: `publish`, `deploy/local`, `commits`, `commits/rollback`, `versions`, `deploy/{versionId}`, `pipeline`, `restore-schemas`, plus `workflow` GET/PUT and `pages/{pageId}` GET/PUT/DELETE. `restore-schemas` mutates schema state and was confirmed reachable anonymously. | ✅ confirmed by full read of `AppRoutes.java` — every route the reviewer named exists exactly as described, several with no `extractUserId` call at all | S1 (rescoped) |
| B3 | S3.4 was scoped to "replace the 16+ `authEnabled` blocks in `GenericEntityRoutes`" — a find-and-replace that structurally cannot reach the **11 further entity routes** in the same file with no such block: the studio-scoped `/appbana-studio/{t}/apps/{a}/{entity}(/{id})` family and the env-scoped `/api/{t}/apps/{a}/env/{env}/{entity}(/{id})` family. Two of the four unguarded GETs are also middleware-excluded → anonymous cross-tenant reads. | ✅ confirmed by full read — route-by-route match to the reviewer's list, including which ones have an `extractUserId != null` gate vs. none at all | S3 (rescoped) |
| B4 | `DELETE /schema/{name}` has no `isAppOwnerOrSystem` check (only the optional global-token check that `POST /schema` also has as a secondary gate) — `?dropTable=true` drops the physical table. Any authenticated user of any tenant can destroy any other tenant's schema. | ✅ confirmed by direct read of `SchemaRoutes.java` — `POST /schema` has the ownership check at the line above, `DELETE /schema/{name}` does not | S1 (moved up from S2) |

**B1 is worse than "a future S3 blocker."** `GenericEntityRoutes`'s own "B8 FIX"/"B9 FIX" comments show a
prior author already tried to close this exact gap on the runtime write routes ("Middleware exclusion
≠ public access") by adding an `extractUserId != null` check. Because those routes are
middleware-excluded and `extractUserId` can't see a Bearer token there, **that check is already
rejecting every real client with 401 today**, independent of whether this plan proceeds at all. This is
a live functional regression this audit happened to surface, not a hypothetical. Recommend fixing S0.1
on its own merits regardless of scheduling for the rest of this plan.

### 🟠 High severity (folded into S1–S5)

| # | Finding | Verified | Fixed in |
|---|---|---|---|
| H1 | `GET /api/debug/schemas` is fully anonymous and returns all schemas across all tenants; its sibling `/api/debug/schemas/names` requires a session. Root cause: `ENTITY_API_PATTERN` matches 1-or-2-segment `/api/...` paths, and `debug/schemas` happens to be 2 segments while `debug/schemas/names` is 3 — the exclusion is segment-count arithmetic, not intent. | ✅ confirmed — traced both paths through `isExcludedPath` by hand against the actual regex | S1 |
| H2 | `POST /api/files/upload` is anonymous and trusts body-supplied `tenantId`/`appId`; `FileRoutesTenantIsolationTest` only covers downloads. (Also corrects this plan's own "FileRoutes already works" claim — see the callout above.) | Accepted on the strength of the reviewer's live probe + consistent with `FileRoutes.java`'s known download-only test coverage | S1 |
| H3 | `SavedViewRoutes` is anonymous end-to-end (its own header comment admits this: "matches ENTITY_API_PATTERN... consistent with the codebase's current dev-mode auth posture"), and `DELETE FROM appbana_saved_views WHERE view_id = ?` has no tenant/app/owner clause at all. Not named anywhere in the original plan. | ✅ confirmed by direct read — `DELETE_SQL` is exactly that one-column WHERE clause | S1 |
| H4 | `/api/templates` CRUD is anonymous; the underlying table has no tenant dimension at all — it's a globally shared, anonymously-writable store. | ✅ confirmed public registration in `AppRoutes.java` / `SessionMiddleware`'s `EXCLUDED_PATHS` | S1 (writes gated) + [Open decisions](#open-decisions-still-needed-from-product) (reads — see pushback below) |
| H5 | The original S5.3 census predicate ("routes with a `{tenantId}`/`{appId}` path param") misses every route that takes tenant/app from the body or query — `POST /schema`, `/api/files/upload`, `/api/saved-views`, `GenericAppAuthController.login`, ai-builder's `ChatRequest`. | ✅ self-evident from this session's own read of `POST /schema`, which pulls `schema.getTenantId()`/`getAppId()` from the JSON body, not the path | S0.2 (census predicate corrected; subsumes old S5.3) |
| H6 | `isAppOwnerOrSystem` has **4** call-site files, not the 2 (`SchemaRoutes`, `RoleRoutes`) S2.5 named — also `ApprovalService` (×3) and `UserRoutes`. Migrating only 2 to `appbana_app_members` forks "owner" into two authorities. | ✅ confirmed — grep found exactly these 4 files, matching the reviewer's count precisely | S2 (design changed, not just rescoped — see below) |

**Pushback on H4's remedy:** I agree the anonymous **writes** on `/api/templates` are a straightforward
bug (no auth gate at all on POST/PUT/DELETE) and belong in S1 without debate. I'm not adopting "add a
tenant dimension to templates" as the default remedy for **reads**, though — templates read like a
platform-curated scaffold catalog (shared across tenants by design, similar to a template gallery),
not tenant business data. Forcing a tenant column onto them is a bigger, more speculative change than
this plan needs to make. I've recorded the actual choice (keep reads platform-shared vs. give tenants
their own template libraries) as an explicit open decision rather than picking one silently.

### 🟡 Medium severity (accepted, folded into S1–S4)

| # | Finding | Fixed in |
|---|---|---|
| M1 | The Tomcat/servlet `Router.handle(HttpServletRequest,...)` overload calls the route handler directly with **no middleware chain at all** — confirmed by direct read, it does not reference `middlewares`/`RateLimitMiddleware`/`SessionMiddleware` anywhere. Latent today only because `serverType` defaults to `jdk` (confirmed in `Main.java` and `config.json`); flipping that one config value would silently disable all auth. | S0.4 (new) |
| M2 | `AuthService.extractToken`'s own javadoc says "Do NOT pass to hasAdmin()/hasWrite()/hasRead()" — confirmed six call sites in `SchemaRoutes.java` (`/api/endpoints`, `/openapi.json`, `GET/POST /schema`, `GET/DELETE /schema/{name}`) do exactly that. Functional bug more than a security hole (a valid session token essentially never matches the admin/read secret, so this fails closed, not open) but violates its own documented contract. | S1 (drive-by fix while these routes are being touched anyway) |
| M3 | The raw token is written into `appbana_audit.actor` on at least one path — a credential persisted in a table readers of the audit log can see. | S4 |
| M4 | `appbana_audit` has no `tenant_id`/`app_id` columns — a cross-tenant incident can be reproduced by a live probe but not proven from the audit trail after the fact. | S4 (new Liquibase columns) |
| M5 | `GenericAppAuthController`'s login SQL is `WHERE email = ? AND password = ?` — BCrypt hashes can't be compared in SQL, so S4.2 is a fetch-by-email-then-verify-in-Java rewrite, not a drop-in swap. | S4.2 (task description corrected) |
| M6 | `GenericAppAuthController.login` differentiates 404 ("entity not found") from 401 ("bad credentials") — a cross-tenant/cross-app existence oracle, unauthenticated. | S3.3 (now explicitly includes normalizing the response) |
| M7 | S1's guard needs an explicit 401-on-no-session rule; a mismatch-only comparison fails open when there's nothing to compare against — the common case once B1 is understood. | Folded into S1's exit criteria directly (not a separate task) |
| M8 | `appbana_app_members.user_id` would reference identities in a file-based JSON store (`UserManager`) with no FK/uniqueness guarantee; live data has both numeric and string `author` values, and some authors may not resolve to an existing user. | S2.4 (backfill task updated to tolerate both and to mark orphaned apps rather than fail) |

### Nits (accepted, low-risk cleanups)

- This plan's own Testing Strategy section referenced `testing-conventions.md` as if it were a citable
  repo file. It isn't — it's this agent's private memory note, not a tracked file in `.github/` or
  anywhere else in the repo. Reworded below to stop implying otherwise.
- `SessionMiddleware.EXCLUDED_PATHS` lists `/api/csrf/token`; the registered route is `/api/csrf-token`
  — the exclusion matches nothing today. Inconsequential once S4.3 removes `CsrfMiddleware`, noted as a
  drive-by cleanup.
- `GET /api/{tenantId}/apps/{id}/env/{env}/full` is registered twice in `AppRoutes.java`. One-line fix,
  folded into S1.

Everything above reduces to one root cause, well put by the reviewer: *this system authorizes
per-route, from whatever identifier the client happened to supply, using an extractor that can't see
the credential real clients actually send — and a plan built by counting existing checks can never find
a missing one.* S0 exists to fix the extractor once and derive the route list mechanically, instead of
re-auditing route-by-route a second time.

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
`AppMetadata.getAuthor()`) before enforcement goes live in S2, or every existing app becomes
inaccessible to its own creator the instant enforcement is enabled. Same principle as this repo's
`V0__bootstrap_meta_tables.sql` lesson: the migration must work against data that already exists, not
just fresh installs.

---

## Sub-phase S0 — Unify identity resolution + route census

**Goal:** Every later phase depends on two things being true first: (1) a route-level identity check
sees the same credential `SessionMiddleware` does, and (2) the list of routes S1–S3 need to guard is
generated from the actual router registrations, not from what the original findings list happened to
name. Review round 1 found both were false.

| # | Task | Where | Est. |
|---|---|---|---|
| S0.0 | **Prerequisite.** Fix the Maven toolchain so `mvn test` compiles under this repo's configured `release` version — currently fails with "release version 25 not supported." No exit-criteria test in S0–S5 can run until this is fixed. | build config (`pom.xml` / toolchain) | 30–90 min (unscoped until root-caused) |
| S0.1 | `AuthService.resolveIdentity(req, cfg)` — a single method accepting `X-Session-Token`, `Authorization: Bearer`, and the `session_id` cookie (same three forms `SessionMiddleware.extractSessionToken` already supports), returning a resolved principal. Replace `extractUserId`'s broken X-Session-Token-only Priority 3 fallback with a call to this method; `SessionMiddleware.create()` also delegates to it so there is exactly one implementation of "how do we read the caller's credential" in the codebase. | new method in `AuthService.java` (or a small extracted `IdentityResolver`), `SessionMiddleware.java` | 90 min |
| S0.1b | Regression test: all three token forms, sent against the same valid session, yield the same principal on a route excluded from `SessionMiddleware` (e.g. `/api/{tenantId}/apps/{appId}/{entity}`). This is the test that would have caught B1. | new test | 30 min |
| S0.2 | Machine-generated route census: enumerate every `router.get/post/put/delete(...)` registration across every `*Routes.java` file. Columns: path, middleware-excluded?, identity gate present?, tenant/app check present?, tenant/app source (`path` \| `query` \| `body` \| `header` \| `none`). Predicate is **any client-controlled tenant/app identifier**, not just path params (fixes H5). Attach the generated table to this plan. | new `RouteCensus` (small script or JUnit-generated report), appended to this doc | 120 min |
| S0.3 | A test that fails when a route is registered without a corresponding census entry (e.g. asserts route count from `Router` reflection matches census row count). This is what stops the "found gaps sit just outside the plan's own boundary" pattern from recurring on a route added next month. | new test | 60 min |
| S0.4 | Fix or fence `Router.handle(HttpServletRequest, HttpServletResponse)` (M1): either route it through the same middleware chain `handle(HttpExchange)` uses, or make `serverType=jdk` an explicit, enforced deployment constraint (fail fast at startup if `serverType` is set to anything else while this gap remains). Confirmed by direct read: this overload calls `r.handler.accept(...)` directly with no reference to any middleware. | `Router.java`, `Main.java` | 45 min |

### Exit criteria — S0

- [ ] `mvn test` compiles and runs on this repo's toolchain.
- [ ] All three credential forms resolve to the same principal on at least one middleware-excluded route.
- [ ] The route census exists, is attached to this plan, and lists every registered route with a
      non-empty tenant/app-source classification.
- [ ] Registering a new route without updating the census fails CI.
- [ ] `serverType` other than `jdk` either goes through the same middleware or refuses to start.

**Everything in S1–S3 below is scoped by the S0.2 census, not by the finding tables above** — the
tables identify *why* this work is needed; the census is what tells S1–S3 *where* to apply it.

---

## Sub-phase S1 — Tenant boundary on app management

**Goal:** No session can act on a tenant other than its own, anywhere in `AppRoutes` or
`SchemaRoutes` — **every route the S0.2 census lists for these two files**, not only list/get/update/delete.
Review round 1 (B2, B4) found the originally-named four routes are a strict subset of what needs
guarding in these two files alone.

| # | Task | Where | Est. |
|---|---|---|---|
| S1.1 | Add `tenantId` (and reserve `scopedAppId`) to `SessionData`; populate `tenantId` at login from `User.tenantId` | `SessionService.java`, `AuthenticationController.java` | 45 min |
| S1.2 | New `TenantAccessGuard.requireOwnTenant(session, pathTenantId)` — **401 if there is no resolved identity at all** (via S0.1's `resolveIdentity`), **403 on a tenant mismatch**. Two distinct outcomes, not one mismatch-only comparison (fixes M7). | new `com.appbana.security.TenantAccessGuard` | 45 min |
| S1.3 | Wire the guard into **every** `AppRoutes` handler per the S0.2 census — not just list/get/update/delete: also `publish`, `deploy/local`, `commits`, `commits/rollback`, `versions`, `deploy/{versionId}`, `pipeline`, `restore-schemas`, `workflow` GET/PUT, `pages/{pageId}` GET/PUT/DELETE (B2). `restore-schemas` in particular currently mutates schema state with zero authentication. | [`AppRoutes.java`](../../app-bana-service/src/main/java/com/appbana/server/routes/AppRoutes.java) | 120 min |
| S1.4 | Add the missing ownership check to `DELETE /schema/{name}` (B4) — today only `POST /schema` calls `isAppOwnerOrSystem`; the delete/drop-table path does not. Moved up from S2 since it's a same-shape fix to the same file already being touched. | [`SchemaRoutes.java`](../../app-bana-service/src/main/java/com/appbana/server/routes/SchemaRoutes.java) | 30 min |
| S1.5 | `GET /api/debug/schemas` (H1): require the same session check its sibling `/names` endpoint already gets. Root-cause fix: don't let a route's exclusion from `SessionMiddleware` depend on incidental path-segment count — name debug/admin routes in `EXCLUDED_PATHS`'s complement explicitly rather than relying on `ENTITY_API_PATTERN` to *not* match them. | `SchemaRoutes.java`, `SessionMiddleware.java` | 30 min |
| S1.6 | Gate `POST/PUT/DELETE /api/templates` (H4) behind an authenticated (admin, for now) identity — reads stay public pending the open decision on whether templates get a tenant dimension. | `AppRoutes.java` | 30 min |
| S1.7 | `POST /api/files/upload` (H2): require a resolved identity and derive `tenantId`/`appId` from it (or from the authenticated user's own app membership once S2 lands) instead of trusting the request body. Add an upload-path test to `FileRoutesTenantIsolationTest`, which today only covers downloads. | `FileRoutes.java` | 45 min |
| S1.8 | `SavedViewRoutes` (H3): require a resolved identity on all three routes; add `tenant_id`/`app_id`/`owner_user_id` to `DELETE_SQL`'s `WHERE` clause (today: `view_id` alone). | `SavedViewRoutes.java` | 45 min |
| S1.9 | Drive-by: remove the duplicate `GET /api/{tenantId}/apps/{id}/env/{env}/full` registration (keep the one explicitly marked PUBLIC for runtime); fix the six `SchemaRoutes.java` call sites passing `extractToken()`'s output to `hasRead`/`hasWrite` (M2) to use `extractServiceToken()` instead, per `AuthService`'s own documented contract. | `AppRoutes.java`, `SchemaRoutes.java` | 45 min |
| S1.10 | Startup warning: log a loud, repeated `WARN` while `AuthService.authEnabled(cfg)==false` so this is never silently shipped to production | `ApiServer.java` startup path | 30 min |
| S1.11 | `CrossTenantAppAccessTest` + `CrossTenantSchemaAccessTest` — tenant B's session must not list/get/update/delete/publish/deploy/rollback/restore tenant A's apps, nor read/delete tenant A's schemas | new tests | 90 min |

### Exit criteria — S1

- [ ] A session for Tenant B gets 403 (not 404, not 200) on every `AppRoutes`/`SchemaRoutes` route the
      S0.2 census lists against Tenant A, including `restore-schemas` and `DELETE /schema/{name}`.
- [ ] A request with **no** resolved identity gets 401, distinctly from the 403 a wrong-tenant identity gets.
- [ ] `GET /api/debug/schemas` requires the same session its `/names` sibling already requires.
- [ ] `POST/PUT/DELETE /api/templates` require an authenticated admin identity.
- [ ] `POST /api/files/upload` and all of `SavedViewRoutes` require a resolved identity; the saved-view
      delete path is scoped by tenant+app+owner, not `view_id` alone.
- [ ] Tenant A's own users are unaffected on every route above.
- [ ] Server logs a visible warning on every boot where global auth is disabled.

---

## Sub-phase S2 — Per-app membership model

**Goal:** Within one tenant, a user can only manage the specific apps they've been granted membership
on — not every app the tenant owns. (Per product decision: **explicit per-app membership**, not
tenant-wide access.)

**Design change from the original draft (review round 1, H6):** rather than adding a second, parallel
`AppAccessGuard` and migrating only the two call sites (`SchemaRoutes`, `RoleRoutes`) this plan
originally named, `AppAuthorization.isAppOwnerOrSystem` itself becomes membership-aware. It has 4
call-site files today (`ApprovalService` ×3, `RoleRoutes`, `SchemaRoutes`, `UserRoutes`), not 2 — making
the existing helper consult `appbana_app_members` (falling back to `AppMetadata.getAuthor()` only where
no membership row exists yet) means all 4 upgrade together and there is exactly one authority for
"is this caller allowed to act on this app", not two that can drift apart.

| # | Task | Where | Est. |
|---|---|---|---|
| S2.1 | Liquibase changeset for `appbana_app_members` (schema above) | `app-bana-service/src/main/resources/db/changelog/` | 30 min |
| S2.2 | `AppMembershipService` — `grant/revoke/listMembers/isMember(tenantId, appId, userId)/isOwner(...)`, mirroring `UserRoleService`'s shape | new `com.appbana.security.AppMembershipService` | 75 min |
| S2.3 | Bootstrap: app creator is auto-granted `owner` membership at creation time (same pattern as maker-checker's C1.5) | [`AppRoutes.java`](../../app-bana-service/src/main/java/com/appbana/server/routes/AppRoutes.java) create handler | 30 min |
| S2.4 | **Backfill migration** — for every pre-existing app row, insert an `owner` membership from `AppMetadata.getAuthor()`. Must tolerate the live data shape (review round 1, M8: some apps have numeric-string authors, some have arbitrary strings): resolve against `UserManager` where possible; where the author no longer resolves to a real user, assign a designated tenant-admin fallback and flag the app as `ownerless-backfilled` in a log line rather than failing the migration. | new Liquibase data migration or one-time startup task | 90 min |
| S2.5 | Make `AppAuthorization.isAppOwnerOrSystem` membership-aware: check `appbana_app_members` first, fall back to the existing `AppMetadata.getAuthor()` comparison only when no membership row exists for that app yet (pre-backfill safety net). No call site needs to change — all 4 (`ApprovalService`, `RoleRoutes`, `SchemaRoutes`, `UserRoutes`) upgrade automatically. | `AppAuthorization.java` | 75 min |
| S2.6 | Wire the (now membership-aware) `isAppOwnerOrSystem` — or a thin `isMember` variant where "any member," not just "owner," should pass — into the S1.3 routes that only got a tenant check in S1 (`AppRoutes` list/get/update/delete and the release-management family) | `AppRoutes.java` | 60 min |
| S2.7 | `GET/POST/DELETE /api/tenants/{t}/apps/{a}/members` — membership management endpoints, `owner`-only | new `AppMembershipRoutes.java` | 60 min |
| S2.8 | Studio frontend: verify the app switcher/list only ever renders what the (now correctly filtered) server response contains — no client-side "show all tenant apps" assumption left over | `app-bana-studio/src/features/**` (session/workspace store) | 45 min |
| S2.9 | Tests: `AppMembershipGuardTest`, `AppRoutesMembershipTest`, `IsAppOwnerOrSystemConsultsMembershipTest` (all 4 call sites agree once membership exists). The route-census regression test lives in S0.3, not here — this phase only needs to prove membership is actually consulted. | new tests | 90 min |

### Exit criteria — S2

- [ ] A Tenant A user who is not a member of Tenant A's App 2 gets 403 managing App 2, while still
      managing their own App 1 normally.
- [ ] Every app that existed before this migration is still manageable by its original creator
      immediately after deploy (backfill verified against a copy of production-shaped data, including
      apps with non-numeric authors).
- [ ] An app whose recorded author no longer resolves to a real user is backfilled to a designated
      fallback owner and logged, not silently dropped or left to crash the migration.
- [ ] Only an `owner` member can grant/revoke membership or delete the app.
- [ ] All 4 `isAppOwnerOrSystem` call sites (`ApprovalService`, `RoleRoutes`, `SchemaRoutes`, `UserRoutes`)
      agree on the same answer for the same (tenant, app, user) once membership data exists — proving
      there's one authority, not two.

---

## Sub-phase S3 — Entity data API enforcement

**Goal:** Every entity-data route in `GenericEntityRoutes` — **all three route families the S0.2 census
lists**, not only the 16+ places an `authEnabled` block already exists — stops trusting an optional
global token and instead requires either real Studio app-membership or a runtime session correctly
scoped to that one app.

**Scope correction (review round 1, B3):** the original S3.4 ("replace the 16+ `authEnabled` blocks")
is a find-and-replace over *existing* checks, which cannot reach the 11 routes that have no such block
to replace: the studio-scoped `/appbana-studio/{t}/apps/{a}/{entity}(/{id})` family (5 routes) and the
env-scoped `/api/{t}/apps/{a}/env/{env}/{entity}(/{id})` family (6 routes). Two of the unguarded GETs in
the latter family are also middleware-excluded — anonymous, cross-tenant reads today. S3.4 below is
re-specified against the S0.2 census, enumerated, not against a grep for `authEnabled`.

| # | Task | Where | Est. |
|---|---|---|---|
| S3.1 | Use S2's `scopedAppId` field on `SessionData` for runtime sessions (already reserved in S1.1's model) | `SessionService.java` | 30 min |
| S3.2 | `EntityAccessGuard` with **two entry points**, not one: (a) `check(entityKey, ...)` — parses the packed `{tenantId}_{appId}_{entityName}` key, for the `/api/{entity}` family; (b) `check(tenantId, appId, entityName, ...)` — for the two path-segmented families, which never had a key to parse. Both apply the same allow rule: (i) a Studio session's user is an `appbana_app_members` member of that `(tenantId, appId)`, **or** (ii) a runtime session whose `scopedAppId` equals that `appId`, **or** (iii) the app is explicitly marked publicly readable (S3.5) and the request is a `GET`. Optional global admin token remains a break-glass override, evaluated only if none of the above match. | new `com.appbana.security.EntityAccessGuard` | 150 min |
| S3.3 | `GenericAppAuthController.login()`: (a) issues a real session via `SessionService.createSession(...)` with `scopedAppId` set to that app; (b) rewrite the login query to fetch-by-email and verify the password in Java (BCrypt hashes cannot be compared in a `WHERE password = ?` clause — review round 1, M5, means this is not a drop-in swap); (c) normalize the response so a nonexistent entity/app and a wrong password both produce the same generic 401 — today they're distinguishable (404 vs 401), making this endpoint a cross-tenant/cross-app existence oracle (M6). | [`GenericAppAuthController.java`](../../app-bana-service/src/main/java/com/appbana/api/GenericAppAuthController.java) | 105 min |
| S3.4 | Wire `EntityAccessGuard` into **every route in `GenericEntityRoutes`, per the S0.2 census** — the 16+ existing `authEnabled` blocks *and* the 11 routes in the studio-scoped and env-scoped families that have no block to replace. Auth is now always evaluated for all three families, not conditionally skipped for some. | [`GenericEntityRoutes.java`](../../app-bana-service/src/main/java/com/appbana/server/routes/GenericEntityRoutes.java) | 150 min |
| S3.5 | Add an explicit `publicRead: boolean` flag on app/entity metadata (default `false`) for the legitimate "this app's data should be publicly browsable" use case — without this, S3 would break intentionally-public apps | `AppMetadata`/`EntitySchema` + `SchemaRoutes.java` | 45 min |
| S3.6 | Tests: `CrossTenantEntityAccessTest`, `CrossAppEntityAccessTest` (App 1 member cannot touch App 2's entities in the *same* tenant) — run against **all three route families**, not just `/api/{entity}` — plus `RuntimeSessionScopedToSingleAppTest` and `LoginDoesNotLeakEntityExistenceTest` (M6) | new tests | 120 min |

### Exit criteria — S3

- [ ] With global auth disabled (today's default), entity data is **not** reachable without a valid,
      correctly-scoped session — the current "wide open by default" posture is gone, across all three
      route families, not just `/api/{entity}`.
- [ ] A Runtime end-user session for App 1 gets 403 on App 2's entity routes, same tenant, in all three
      route families.
- [ ] An app explicitly marked `publicRead` still serves anonymous `GET` requests (no regression for
      the legitimate public-app case).
- [ ] The break-glass admin token still works as an override when explicitly configured, but its
      absence no longer means "no check happens".
- [ ] `GenericAppAuthController.login` returns the same status/body shape for "app doesn't exist" and
      "wrong password" — it can no longer be used to enumerate other tenants' apps/entities.

---

## Sub-phase S4 — Credential hygiene

**Goal:** Passwords are never compared or stored in plaintext, CSRF posture matches what the docs
claim, and the audit trail can actually attribute an action to a tenant/app instead of just a bare
entity name and a possibly-raw credential.

| # | Task | Where | Est. |
|---|---|---|---|
| S4.1 | Wire the existing `PasswordService` (BCrypt) into `UserManager`: new registrations hash on write; on a successful **plaintext-compare** login for a legacy row, immediately rehash and persist — transparent migration, no forced reset, per product decision | `UserManager.java` | 45 min |
| S4.2 | Same transparent-rehash treatment for `GenericAppAuthController`'s runtime end-user table. Review round 1 (M5) confirmed the current login SQL is `WHERE email = ? AND password = ?` — BCrypt hashes cannot be verified in SQL, so this is fetch-by-email-then-verify-in-Java, plus finding every write path that sets the password column (not just login) to hash on write. | `GenericAppAuthController.java` + wherever runtime end-user records are created | 90 min |
| S4.3 | Decide and act on CSRF: current auth is bearer-token-in-header, not cookie-based, so classic CSRF does not apply today. Recommendation: remove the dead `CsrfMiddleware` registration references from docs and delete the unused middleware (or explicitly wire it in only if cookie-based auth is ever introduced). Drive-by: `SessionMiddleware.EXCLUDED_PATHS` lists `/api/csrf/token`, which doesn't match the real `/api/csrf-token` route — delete along with the rest of the CSRF cleanup. | `docs/features/SECURITY_FEATURES.md`, `CsrfMiddleware.java`, `SessionMiddleware.java` | 30 min |
| S4.4 | Correct `docs/features/SECURITY_FEATURES.md` end-to-end against the post-S4 real state (remove the false BCrypt-already-done and wired-CSRF claims; the LitElement snippets are also stale per this repo's retired `app-bana-ui/` and should be replaced or removed) | `docs/features/SECURITY_FEATURES.md` | 30 min |
| S4.5 | Tests: `PasswordRehashOnLoginTest` (plaintext row → hashed after one login), `NewRegistrationIsHashedTest` | new tests | 45 min |
| S4.6 | **(New, review round 1, M4)** Add `tenant_id`/`app_id` columns to `appbana_audit` and populate them on every write, instead of only a bare `entity` name — without this, a cross-tenant incident is reproducible live but not provable after the fact from the audit trail alone. | Liquibase changeset, `AuditLogService.java` | 60 min |
| S4.7 | **(New, review round 1, M3)** Stop writing the raw token/session id into `appbana_audit.actor` on any path — always resolve to the real userId first (via S0.1's `resolveIdentity`) before logging. | `GenericEntityRoutes.java` | 30 min |

### Exit criteria — S4

- [ ] No code path compares a raw password string to a stored value.
- [ ] Every pre-existing plaintext row is transparently upgraded to BCrypt the next time its owner logs in — no user-visible disruption, no forced reset.
- [ ] `SECURITY_FEATURES.md` accurately reflects what is actually running.
- [ ] `appbana_audit` rows carry `tenant_id`/`app_id`, and `actor` is never a raw token/session id.

---

## Sub-phase S5 — Capstone tests + ai-builder trust chain

**Goal:** Prove the whole system end-to-end, and stop the AI Builder service from being a second,
independent place where "trust the client-supplied tenant/app id" could resurface.

**Scope correction (review round 1, H5):** the route-census regression test originally scoped here as
S5.3 moved to **S0.3** — it has to exist *before* S1–S3 are implemented, as the thing that tells those
phases where to apply their guards, not just as a check afterward. Keeping it here as well would have
given it two owners.

| # | Task | Where | Est. |
|---|---|---|---|
| S5.1 | `AiChatController`/`AgentContext` verifies the caller's `tenantId`/`appId` against app-bana-service (reusing `EntityAccessGuard`/`isAppOwnerOrSystem` via a lightweight internal check) instead of trusting the client-supplied JSON body fields | [`AiChatController.java`](../../ai-builder/src/main/java/com/appbana/ai/api/AiChatController.java) | 90 min |
| S5.2 | `CrossTenantIsolationTest` capstone suite — 2 tenants × 2+ apps each, asserting no session from one tenant/app combination can read/write/delete another's apps, roles, files, saved views, templates writes, or entity rows in any of the three entity-route families | new integration test | 120 min |
| S5.3 | Document the enforced model in `.github/copilot-instructions.md` (new section, mirroring how Maker-Checker got documented) | `.github/copilot-instructions.md` | 30 min |

### Exit criteria — S5

- [ ] Capstone cross-tenant suite passes and is wired into CI (not a one-off manual run).
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
- **Prerequisite:** the Maven toolchain must compile under this repo's configured `release` version
  before any test below can run (S0.0) — found blocking a probe build during review round 1.
- **Unit:** guard logic (`TenantAccessGuard`, `EntityAccessGuard`, membership-aware `isAppOwnerOrSystem`)
  against every allow/deny combination.
- **Integration:** backfill migration against production-shaped fixture data (including mixed
  numeric/string authors — M8); rehash-on-login.
- **Capstone (S5.2):** the full 2-tenant × 2-app matrix across all three entity-route families — this is
  the test that actually answers the question this plan started from, and should be kept green
  permanently, not run once.
- **Route census (S0.2/S0.3):** generated once, enforced continuously — a new route without a census
  entry fails CI rather than relying on the next manual audit to notice.
- This agent's own working notes (not a tracked repo file) record a standing project convention: once
  implementation begins, fixes must additionally be verified through the actual browser UI (Studio 5174
  / Runtime 5175), not backend/API tests alone.

### Performance
- Both new guards are a single indexed PK lookup (`appbana_app_members`) or an in-memory `SessionData`
  field check — same order of overhead as `ApprovalGuard`'s existing filtering (<5ms).

### Rollout order
**Strict serial:** S0 → S1 → S2 → S3 (S0's identity resolver and route census are prerequisites for
writing any of S1–S3's guards correctly; S3's membership check depends on S2's table). **Parallel-safe:**
S4 (independent of the others, though S4.6/S4.7 touch code S1/S3 also touch — coordinate to avoid merge
conflicts, not for correctness reasons). **Last:** S5 (the capstone test needs S1–S3 to exist to have
something real to assert; the ai-builder half can start as soon as S2's membership model is in place).

### Documentation
- `.github/copilot-instructions.md` — new section on the enforced tenant/app isolation model (S5.4).
- `docs/features/SECURITY_FEATURES.md` — corrected in place (S4.4), not superseded by a new doc.

---

## File-level change map

**New files (backend):**
- `app-bana-service/src/main/java/com/appbana/security/TenantAccessGuard.java` (S1)
- `app-bana-service/src/main/java/com/appbana/security/AppMembershipService.java` (S2)
- `app-bana-service/src/main/java/com/appbana/security/EntityAccessGuard.java` (S3 — two entry points, see S3.2)
- `app-bana-service/src/main/java/com/appbana/server/routes/AppMembershipRoutes.java` (S2)
- Liquibase changesets: `appbana_app_members` table + backfill data migration (S2); `appbana_audit`
  `tenant_id`/`app_id` columns (S4.6)
- Route census artifact/script + regression test (S0.2, S0.3)

**Modified files (backend):**
- `SessionService.java` — `tenantId` + `scopedAppId` on `SessionData` (S1, S3)
- `AuthenticationController.java` — populate `tenantId` at login (S1)
- `AuthService.java` — new `resolveIdentity()` (S0.1); fix six `extractToken`→`hasRead/hasWrite` call
  sites in `SchemaRoutes.java` to use `extractServiceToken()` instead (S1.9, M2)
- `SessionMiddleware.java` — delegate to `resolveIdentity()` (S0.1); fix the dead `/api/csrf/token`
  exclusion entry (S4.3)
- `Router.java` — fix or fence the servlet `handle(...)` overload that bypasses all middleware (S0.4)
- `AppRoutes.java` — guard wiring across the full route set, not just list/get/update/delete (S1, S2);
  gate `/api/templates` writes (S1.6); remove duplicate `env/{env}/full` registration (S1.9)
- `SchemaRoutes.java` — add ownership check to `DELETE /schema/{name}` (S1.4); fix `/api/debug/schemas`
  exclusion (S1.5)
- `SavedViewRoutes.java` — require identity; scope `DELETE_SQL` by tenant+app+owner (S1.8)
- `FileRoutes.java` — require identity on upload; stop trusting body-supplied tenant/app (S1.7)
- `AppAuthorization.java` — `isAppOwnerOrSystem` becomes membership-aware (S2.5); all 4 existing call
  sites (`ApprovalService`, `RoleRoutes`, `SchemaRoutes`, `UserRoutes`) upgrade with no code change
- `GenericEntityRoutes.java` — wire `EntityAccessGuard` across all three route families, not only the
  16+ `authEnabled` blocks (S3.4); stop writing raw tokens into `actor` (S4.7)
- `GenericAppAuthController.java` — issue scoped session; fetch-by-email + verify in Java; normalize
  404-vs-401 (S3.3, S4.2)
- `UserManager.java` — BCrypt wiring + transparent rehash (S4)
- `AuditLogService.java` — write `tenant_id`/`app_id` (S4.6)
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
3. **Runtime end-user password write path (S4.2).** Review round 1 confirmed the login query itself
   can't be a drop-in BCrypt swap (M5); still needs a short investigation into every *other* place a
   runtime end-user's password column gets written, to confirm all of them hash, not just login.
4. **`/api/templates` reads — platform-shared catalog, or does each tenant get its own?** (Review round
   1, H4.) Writes are gated in S1.6 regardless. Reads are left public in this plan on the assumption
   that templates are a shared, admin-curated scaffold catalog rather than tenant business data —
   confirm that's actually the intent before S1.6 ships, since the alternative (give templates a tenant
   dimension) is a materially different, larger change.

---

*Last updated: 2026-07-31 (review round 1 incorporated) · Author: AppBana core team · Status: DRAFT — awaiting approval before S0 begins.*
