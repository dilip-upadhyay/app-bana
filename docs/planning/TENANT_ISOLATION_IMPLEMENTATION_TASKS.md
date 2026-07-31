# Tenant & App Isolation — Implementation Task Tracker

**Source design doc:** [`TENANT_ISOLATION_SECURITY_PLAN.md`](./TENANT_ISOLATION_SECURITY_PLAN.md) (DRAFT, six review rounds closed, `92b20ba`). This document does not repeat that plan's rationale, review history, or the evidence behind each finding — it exists purely to break the plan's six sub-phases into independently-committable, independently-reviewable units of work, track their status, and record what actually landed.

**How to review this:** each task below lands as its own commit on `feature/tenant-security`, with a commit message prefixed `feat(S#.#):` / `test(S#.#):` / `fix(S#.#):` / `docs(S#.#):` naming the task ID — use `git log --oneline --grep="S1.2"` (etc.) to find a specific task's commit, or `git log --oneline` for the full sequence. A task is only marked ✅ once its own exit-criteria checks pass and (where the task touches a route or UI-visible behavior) it has been driven through the real Studio/Runtime UI per this repo's testing convention, not backend tests alone. See **Testing doctrine** below for exactly how each task is proven — including the small number that have no end-user UI surface at all, which are labeled as such, never silently skipped.

**Status legend:** ⬜ not started · 🔄 in progress · ✅ done (committed) · ⏸️ blocked (see note)

**Total scope:** ~40.75 hr across 46 tasks. Rollout constraints carried over from the plan (do not lose these when executing):
- S0 must land before S1–S3 are written (its identity resolver + route census are inputs to them).
- **S1 and S2 ship as one deployable unit** — do not deploy S1 alone to any environment with live deployed apps (every real end-user is a foreign-tenant session by construction until S2.6 lands).
- **S3 completion is a deliberate one-time access reset** — every deployed app's end-users lose access until their owner re-grants via S2.7. Communicate before enabling.
- S4 is independent/parallel-safe. S5 is last (needs S1–S3; its ai-builder half can start once S2 exists).

---

## Testing doctrine — every task is proven live in the browser, not by tests alone

This repo's standing rule (`/memories/repo/testing-conventions.md`): verification means opening Studio (5174) or Runtime (5175), performing the exact action a real user would, and observing the actual rendered result — never a backend/API/DB check substituting for that. This section makes the rule concrete for every task above, so ✅ always means "actually clicked/typed through it," not "a test asserted it."

**Three honest categories — tagged inline in each script below, not one blanket rule:**

1. **[Cat. 1] Real, existing UI surface → must be driven live.** Confirmed to exist today by reading the actual source: Header's app switcher (`Header.tsx`), Studio's chat-driven create/scaffold/edit flow (`ChatPane.tsx`), Studio's `DataDrawer` (entity rows), Runtime's own login form (`AppRuntimeShell.tsx`), Runtime's `FileUploadField` (drag-and-drop), Runtime's `SavedViewsBar` (save/select filter views), Runtime's `StudioTableLive` entity grids. Any task whose behavior surfaces through one of these gets a real click-through script — no shortcuts.
2. **[Cat. 2] Ops/service mechanism with no end-user path by product design → verified as an operator, not a "user."** Debug routes, the break-glass admin/service token, audit-log storage, the dead `.../full` route (confirmed zero real callers in earlier review rounds), startup log lines, and CI-only drift/reflection tests are not things any end user ever reaches — through a button *or* a chat command (confirmed: no `delete_entity`/`invite_member`/service-token-login tool is registered anywhere, and AppBana's whole surface is chat-tool-driven, not hidden-menu-driven). There is no flow to shortcut here, so these are verified with a direct, deliberate HTTP call using the real credential against the real running backend, and always labeled as such — never passed off as a UI click-through.
3. **[Cat. 3] Real end-user capability the product hasn't built UI (or a chat tool) for yet → flagged, not assumed.** Exactly two tasks: **S1.6** (`/api/templates` writes) and **S2.7** (membership grant/revoke — no members/invite panel or AI tool exists anywhere). Building UI for these is scope beyond this security plan. Flagged in-place below; needs your decision before those two are marked done.

### Standing UI test fixture (created once at the start of execution, reused across every checkpoint)
Three real accounts, registered through Studio's actual sign-up screen — not the API. This dev environment already assigns every self-registered user their own distinct `tenantId` (confirmed in `data/users.json`, e.g. `t-3353c7f8`), so plain self-registration IS the "two tenants" fixture, no seeding script needed:
- **User A** — owns "QA Tenant A App" (one entity, e.g. `Widget`: name/price). Plays the legitimate owner in every deny scenario.
- **User B** — owns "QA Tenant B App" (one entity, e.g. `Gadget`). Plays the foreign-tenant session in every scenario.
- **User C** — registered once S2.6/S3.7 need a granted, non-owner identity; granted `member`/`end-user` role on User A's app via whichever mechanism the S2.7 decision lands on.

Credentials are recorded in session memory once actually created — not fabricated here since nothing has been implemented yet.

---

## Sub-phase S0 — Unify identity resolution + route census

| # | Task | Files | Est. | Status |
|---|---|---|---|---|
| S0.0 | Fix the Maven toolchain so `mvn test` compiles/runs on this repo's configured Java 25 | `pom.xml`, `app-bana-service/pom.xml` | 30–90 min | ✅ |
| S0.1 | `AuthService.resolveIdentity(req, cfg)` — single method for all 3 credential forms (`X-Session-Token`, `Authorization: Bearer`, `session_id` cookie); replace `extractUserId`'s broken Bearer-blind fallback; `SessionMiddleware.create()` delegates to it too. Preserve existing priority order — service/admin-token interpretation of Bearer is checked before the new session-id fallback, never replaced by it. | `AuthService.java`, `SessionMiddleware.java` | 100 min | ✅ |
| S0.1b | Regression test: all 3 token forms → same principal on a middleware-excluded route; admin token via Bearer + `X-User-Id` still resolves via priority 1; a session id via Bearer resolves via the new fallback; neither form is ever misread as the other. | new test | 45 min | ✅ |
| S0.2 | Machine-generated route census across every `*Routes.java`: path, middleware-excluded?, identity gate present?, tenant/app check present?, tenant/app source (`path`\|`query`\|`body`\|`header`\|`none`), **known callers** (studio/runtime/shared/ai-builder/e2e/"none found"), **data preconditions** (what must exist for the call to succeed). Predicate = any client-controlled tenant/app identifier, not just path params. Attach the generated table to the plan doc. | new `RouteCensus` tool/report, appended to plan doc | 165 min | ⬜ |
| S0.3 | Test that fails when a route is registered without a census entry — assert on the **set** of route signatures (method+path) via `Router` reflection vs. census, not a count. | new test | 75 min | ⬜ |
| S0.4 | Fix or fence `Router.handle(HttpServletRequest,...)` — it bypasses the middleware chain entirely (M1). Either route it through the same chain `handle(HttpExchange)` uses, or fail fast at startup if `serverType` is set to anything but `jdk`. | `Router.java`, `Main.java` | 45 min | ⬜ |

**Exit criteria — S0**
- [x] `mvn test` compiles and runs on this repo's toolchain. Confirmed via clean `mvn clean test` on JDK 25: `app-bana` 331/331 passing, `AI Builder Service` 197/197 (2 skipped), `BUILD SUCCESS`. Fix was binding `maven-enforcer-plugin` under the root pom's actual `<build><plugins>` (it was declared only in `pluginManagement`, which never executes on its own, so the Java-25 gate never fired). Live `start-everything` boot proof completed as part of S0.1's verification below — all four services booted clean from a cold shell with JAVA_HOME set to the JDK 25 install.
- [x] All three credential forms resolve to the same principal on a middleware-excluded route. Unit-proven by `AuthServiceTest` (13/13) and live-proven: two fixture users (see session memory's "Standing UI fixture") each logged into Studio via the real Create Account form, exercising the Bearer-token session path end-to-end through Studio → backend → ai-builder → runtime, both resolving correctly to their own tenant with zero cross-leakage.
- [x] Bearer-carried admin/service token resolves via priority 1, never as a session lookup; a Bearer session id never satisfies `hasAdmin()`. Unit-proven by `AuthServiceTest.testAdminTokenViaBearerWithXUserId`, `testAdminTokenWithoutXUserId`, `testSessionIdViaBearerNeverTreatedAsAdmin`.
- [ ] Route census exists, attached to the plan doc, every row has non-empty tenant/app-source, known-callers, and data-preconditions columns.
- [ ] Adding/renaming/removing a route without updating the census fails CI (set comparison).
- [ ] `serverType != jdk` either shares the middleware chain or refuses to start.

### UI verification script — S0
- **S0.0** ✅ [Cat. 2 — build tooling, no UI surface] `mvn -q -DskipTests compile` (then full `mvn test`) succeeds on a clean shell; `start-everything` boots all four services. Confirmed 2026-08-01: cold-started all four services (AI Builder 8081, Backend 8080, Studio 5174, Runtime 5175) via `start-everything.bat` with JAVA_HOME set to the JDK 25 install — all came up clean, Liquibase ran 19/19 changesets with no errors.
- **S0.1** ✅ [Cat. 1] Open Studio at http://localhost:5174, log in as an existing/fixture user, confirm the app switcher populates and a chat message round-trips — this exercises `resolveIdentity` on every request; a silent break shows up as a failed login or empty switcher. Confirmed 2026-08-01, browser-driven, for **two** independent fixture users (exceeding the minimum bar): User A registered fresh → switcher went from "Select app"/empty → populated with "Contact List" after one chat round-trip → runtime iframe rendered the live app with User A's name in its header. Repeated for User B (separate tenant) with the same result ("Customer Onboarding"). Zero cross-tenant leakage observed (User B's switcher was empty on first login, no trace of User A's app). See session memory for exact credentials/tenant IDs.
- **S0.1b** ✅ [Cat. 2 — same code path as S0.1] No separate script; green test (13/13) + S0.1's live smoke pass (both users) together are the proof.
- **S0.2** [Cat. 2 — generated report, not behavior] Read the generated census table for completeness against the actual registered route set — a structural review, not a browser action.
- **S0.3** [Cat. 2 — CI gate] Temporarily add/remove a dummy route locally, confirm the test fails, then revert. Not UI-observable by nature.
- **S0.4** [Cat. 2, dormant branch by design] Confirm `config.json`'s `serverType` is `jdk` here (the fail-fast branch is intentionally never exercised in this environment). Live proof is the same Studio login smoke as S0.1.

---

## Sub-phase S1 — Tenant boundary on app management

*Deployment note: ships together with S2, not before — see rollout constraints above.*

| # | Task | Files | Est. | Status |
|---|---|---|---|---|
| S1.1 | Add `tenantId` + reserve `scopedAppId` on `SessionData`; populate `tenantId` at login from `User.tenantId` | `SessionService.java`, `AuthenticationController.java` | 45 min | ⬜ |
| S1.2 | New `TenantAccessGuard.requireOwnTenant(session, pathTenantId, pathAppId)`. Order: **(0)** valid service/admin token (`extractServiceToken`+`hasAdmin`) admits immediately regardless of path tenant; **(1)** 401 if no resolved identity; **(2)** if `pathAppId` present, an `appbana_app_members` row for `(app's tenant, pathAppId, userId)` admits despite a tenant mismatch (ships inert until S2.6 wires `AppMembershipService.isMember` in); **(3)** otherwise 403 on mismatch. Bare tenant-wide app-list route has no membership exception — own-tenant only. | new `com.appbana.security.TenantAccessGuard` | 75 min | ⬜ |
| S1.3 | Wire the guard into **every** `AppRoutes` handler per the S0.2 census: list/get/update/delete plus `publish`, `deploy/local`, `commits`, `commits/rollback`, `versions`, `deploy/{versionId}`, `pipeline`, `restore-schemas`, `workflow` GET/PUT, `pages/{pageId}` GET/PUT/DELETE. | `AppRoutes.java` | 120 min | ⬜ |
| S1.4 | Add the missing `isAppOwnerOrSystem` check to `DELETE /schema/{name}` (today only `POST /schema` has it). | `SchemaRoutes.java` | 30 min | ⬜ |
| S1.5 | `GET /api/debug/schemas` requires the same session check its `/names` sibling already has; stop relying on `ENTITY_API_PATTERN` segment-count arithmetic — name debug/admin routes explicitly in `EXCLUDED_PATHS`'s complement. | `SchemaRoutes.java`, `SessionMiddleware.java` | 30 min | ⬜ |
| S1.6 | Gate `POST/PUT/DELETE /api/templates` behind an authenticated (admin, for now) identity; reads stay public pending the open product decision. | `AppRoutes.java` | 30 min | ⬜ |
| S1.7 | `POST /api/files/upload` requires a resolved identity; derive `tenantId`/`appId` from it instead of the request body. Add an upload-path test to `FileRoutesTenantIsolationTest` (today download-only). | `FileRoutes.java` | 45 min | ⬜ |
| S1.8 | `SavedViewRoutes`: require a resolved identity on all 3 routes; add `tenant_id`/`app_id`/`owner_user_id` to `DELETE_SQL`'s WHERE clause (today: `view_id` alone). | `SavedViewRoutes.java` | 45 min | ⬜ |
| S1.9 | Dedupe the two identical `GET .../env/{env}/full` registrations into one; guard it and its `.../full` sibling with the same tenant+membership check (no public carve-out — confirmed zero real callers). Fix the six `SchemaRoutes.java` call sites passing `extractToken()`'s output to `hasRead`/`hasWrite`: convert **all six to `hasAdmin` via `extractServiceToken()`** (readToken is retired — see plan Non-goals, R6-1), leaving `AuthService.hasRead`/`cfg.getReadToken()` with no remaining callers anywhere. | `AppRoutes.java`, `SchemaRoutes.java` | 60 min | ⬜ |
| S1.10 | Startup: log a loud repeated `WARN` while `AuthService.authEnabled(cfg)==false`. | `ApiServer.java` | 30 min | ⬜ |
| S1.11 | `CrossTenantAppAccessTest` + `CrossTenantSchemaAccessTest`: tenant B session must not list/get/update/delete/publish/deploy/rollback/restore tenant A's apps, nor read/delete tenant A's schemas. Positive case: a tenant B session that **is** a member of one specific tenant A app is admitted for that app's list/get (finishes once S2.6 activates the exception — write the deny cases now, finish the positive case in S2.9). | new tests | 105 min | ⬜ |
| S1.12 | Fix `SessionMiddlewareTest`'s tautological assertions (`testPublicRuntimeAppsPathExcluded`/`testPublicDeployedAppsPathExcluded` assert path shapes no real route has) — rewrite against real route shapes, flip expectation to "requires session" now that S1.9 removes the public carve-out. Split `testTemplatesPathExcluded` into read-still-excluded vs. write-requires-auth. | `SessionMiddlewareTest.java` | 30 min | ⬜ |
| S1.13 | `login()`/`register()` in `api-client.ts` must throw (not default `tenantId` to `'default'`) when the backend response omits `tenantId`. Same fix in `e2e/tests/hardening/fixtures.ts`'s `newHardeningFixture`. | `app-bana-shared/src/api-client.ts`, `e2e/tests/hardening/fixtures.ts` | 25 min | ⬜ |
| S1.14 | `BreakGlassAdminBypassesTenantGuardTest` — a valid service/admin token (with or without `X-User-Id`) is admitted by `TenantAccessGuard` on an `AppRoutes`/`SchemaRoutes` route regardless of path tenant. | new tests | 30 min | ⬜ |

**Exit criteria — S1**
- [ ] Tenant B session gets 403 (not 404/200) on every `AppRoutes`/`SchemaRoutes` route the census lists against Tenant A, including `restore-schemas` and `DELETE /schema/{name}`.
- [ ] No resolved identity → 401, distinct from a wrong-tenant 403.
- [ ] `GET /api/debug/schemas` requires the same session as `/names`.
- [ ] `POST/PUT/DELETE /api/templates` require an authenticated admin identity.
- [ ] `POST /api/files/upload` and all of `SavedViewRoutes` require identity; saved-view delete scoped by tenant+app+owner.
- [ ] Both `.../full` routes require tenant+membership check; only one registration remains.
- [ ] `SessionMiddlewareTest` matches real route shapes/behavior.
- [ ] Tenant A's own users unaffected on every route above.
- [ ] Server logs a visible warning whenever global auth is disabled.
- [ ] A valid service/admin token is admitted by `TenantAccessGuard` regardless of path tenant.
- [ ] **Release-process criterion:** S1 is not deployed alone to any environment serving live deployed-app traffic — ships as one unit with S2.

### UI verification script — S1
- **S1.1** [Cat. 2 — no observable behavior alone] Proof deferred to S1.3.
- **S1.2** [Cat. 2 — guard class not wired to a route yet] Proof deferred to S1.3.
- **S1.3** [Cat. 1 — first real cross-tenant checkpoint] Log into Studio as **User B**; open the Header app switcher — User A's app must not appear. Type User A's app URL/id directly into the address bar while logged in as User B — must show a blocked/error state, never App-A's real content. Confirm the non-regression case: User A opens/edits/publishes/deletes their own app normally.
- **S1.4** [Cat. 2 — confirmed no delete-entity chat tool or button anywhere in the product] Direct HTTP call from the real logged-in browser tab's own JS context (real session cookie/token, not a hand-built bearer context) — `DELETE /schema/{name}` as User B against User A's schema → 403; as User A against their own → success.
- **S1.5** [Cat. 2 — debug route, no UI] Same direct-call method as S1.4, against `GET /api/debug/schemas`.
- **S1.6** [Cat. 3 — flagged; pending your decision] See Testing doctrine above.
- **S1.7** [Cat. 1] On Runtime (5175), for an app with a `file`-type field, log in as an end-user, use the real drag-and-drop `FileUploadField` to upload a small file, confirm the success toast and that the link resolves. Confirm the stored tenant/app comes from the session, not any client-supplied value.
- **S1.8** [Cat. 1] On a Runtime entity list page, save a view via the real `SavedViewsBar` as User A; log in as User B, confirm it's not listed. The delete-someone-else's-view case has no button by definition — direct-call proof as in S1.4.
- **S1.9** [Cat. 2 — confirmed zero real callers for `.../full`; `readToken` is a service credential with no UI login anywhere] Structural proof: grep confirms no remaining `hasRead`/`getReadToken()` callers; direct-call check that the six converted routes now require `hasAdmin`.
- **S1.10** [Cat. 2 — ops log line] Start the backend with auth disabled, confirm the repeated WARN line in the terminal — an operator check, not a browser check.
- **S1.11 / S1.12** [Cat. 2 — automated tests] Formalize the scenarios already proven live in S1.3/S1.8; no new script.
- **S1.13** [Cat. 1 happy path / Cat. 2 failure branch] Proof: normal Studio login smoke. The fail-closed branch needs the backend to omit `tenantId`, not naturally triggerable against the real running backend — verified by its unit test only, noted rather than skipped silently.
- **S1.14** [Cat. 2 — no login screen for a raw admin/service token exists, by product design] Direct-call proof: the real `adminToken` from `config.json` as a header against a Tenant-A-owned route with no Studio session presented — confirms bypass still works.

---

## Sub-phase S2 — Per-app membership model

| # | Task | Files | Est. | Status |
|---|---|---|---|---|
| S2.1 | Liquibase changeset for `appbana_app_members` (`tenant_id, app_id, user_id, role['owner'\|'member'\|'end-user'], granted_by, granted_at`, PK `(tenant_id, app_id, user_id)`, index **leading with `user_id`**: `(user_id, tenant_id)`). | `app-bana-service/.../db/changelog/` | 30 min | ⬜ |
| S2.2 | `AppMembershipService` — `grant/revoke/listMembers/isMember(appTenantId, appId, userId)/isOwner(...)`. `appTenantId` is always the app's own tenant (from `AppMetadata`/path), never `session.tenantId`. Gains `listAppsForUser(userId)` — the one cross-tenant lookup in this service, backed by the `(user_id, tenant_id)` index. | new `com.appbana.security.AppMembershipService` | 90 min | ⬜ |
| S2.3 | Bootstrap: app creator auto-granted `owner` membership at creation time (mirrors maker-checker's C1.5). | `AppRoutes.java` create handler | 30 min | ⬜ |
| S2.4 | **Backfill migration** — every pre-existing app row gets an `owner` membership from `AppMetadata.getAuthor()`. Tolerate mixed numeric/string authors; where the author doesn't resolve to a real user, assign a designated tenant-admin fallback and log `ownerless-backfilled` rather than failing. | new Liquibase data migration / one-time startup task | 90 min | ⬜ |
| S2.5 | Make `AppAuthorization.isAppOwnerOrSystem` membership-aware: check `appbana_app_members` first, fall back to `AppMetadata.getAuthor()` only when no membership row exists yet. All 4 call sites (`ApprovalService`, `RoleRoutes`, `SchemaRoutes`, `UserRoutes`) upgrade with no code change. `end-user` never satisfies this check. | `AppAuthorization.java` | 75 min | ⬜ |
| S2.6 | **Completes `TenantAccessGuard.requireOwnTenant`** by wiring `AppMembershipService.isMember` into the membership-exception branch S1.2 ships inert (not a second check layered after — that composition is what R4-1 found broken). Once active: `AppRoutes` list/get accept **any** membership role; update/delete/release-management (`publish`/`deploy`/`commits`/`rollback`/`versions`/`pipeline`/`restore-schemas`/`workflow`/`pages`) require `owner`/`member` and explicitly exclude `end-user`. | `AppRoutes.java`, `TenantAccessGuard.java` | 60 min | ⬜ |
| S2.7 | `GET/POST/DELETE /api/tenants/{t}/apps/{a}/members` — membership management, `owner`-only, accepts all 3 roles including `end-user` on grant. | new `AppMembershipRoutes.java` | 60 min | ⬜ |
| S2.8 | Studio frontend: app switcher/list renders only the server-filtered response — no client-side "all tenant apps" assumption. Union in S2.10's cross-tenant `listAppsForUser` result. | `app-bana-studio/src/features/**` | 60 min | ⬜ |
| S2.9 | Tests: `AppMembershipGuardTest`, `AppRoutesMembershipTest`, `IsAppOwnerOrSystemConsultsMembershipTest` (all 4 call sites agree), `EndUserMembershipCannotManageAppTest` (list/get 200, update/delete/schema-mgmt 403), `CrossTenantMembershipAllowsAccessTest` (finishes S1.11's positive case). | new tests | 120 min | ⬜ |
| S2.10 | `GET /api/users/me/apps` (or equivalent) — union of own-tenant apps + `listAppsForUser` cross-tenant memberships, for the Studio switcher (S2.8) to consume. The only deliberately non-tenant-scoped app-listing route in the plan. | new route in `AppMembershipRoutes.java` | 60 min | ⬜ |

**Exit criteria — S2**
- [ ] A Tenant B user not a member of Tenant A's App 2 gets 403 managing App 2; granted `member` on Tenant A's App 1, manages App 1 normally despite the tenant mismatch.
- [ ] Every pre-existing app is still manageable by its original creator immediately after deploy (verified against production-shaped data, incl. non-numeric authors).
- [ ] An app whose recorded author doesn't resolve is backfilled to a fallback owner and logged, not dropped/crashed.
- [ ] Only an `owner` can grant/revoke membership or delete the app.
- [ ] All 4 `isAppOwnerOrSystem` call sites agree once membership data exists.
- [ ] An `end-user` member lists/gets the app but 403s on update/delete/release-management.
- [ ] A user with membership on an app outside their own tenant sees that app in their own switcher/list.

### UI verification script — S2
- **S2.1 / S2.2** [Cat. 2 — schema/service class, not wired to a route yet] Proof deferred to S2.3/S2.6.
- **S2.3** [Cat. 1, partial until S2.7 lands] Create a brand-new app via Studio's real chat-driven create flow as User A. Full confirmation that User A got an owner row needs S2.7's read route (DB check only as secondary corroboration); until then, indirect proof is that User A can still manage the app they just created.
- **S2.4** [Cat. 1 — against real pre-existing data, not the fixture] Take an app already in this dev database from before this migration existed (an account already in `data/users.json`), log in as its original creator through Studio's real login form after the migration runs, confirm they can still open/edit it — the single most important check in S2, since it's real data.
- **S2.5** [Cat. 2 — no isolated action] Proof deferred to S2.6/S2.9.
- **S2.6** [Cat. 1 — the central S2 checkpoint] Grant User B `member` on App-A (mechanism per the S2.7 decision). Log in as User B: App-A appears in the switcher and opens; clicking update/delete/publish/deploy from the real Studio UI must be blocked (403) despite the grant. Repeat with an `end-user`-role grant on a second app and confirm the same read-admit/write-deny split.
- **S2.7** [Cat. 3 — flagged; pending your decision] No members/invite panel exists anywhere in Studio today. See Testing doctrine above.
- **S2.8** [Cat. 1] The Header app switcher itself. Log in as User B, open it, confirm only User B's own apps plus any cross-tenant memberships (S2.6) appear.
- **S2.9** [Cat. 2 — automated tests] Formalizes S2.6/S2.7's already-proven scenarios.
- **S2.10** [Cat. 1 — same surface as S2.8] Proof is the same switcher click-through after S2.6's grant.

---

## Sub-phase S3 — Entity data API enforcement

*S3 completion is a one-time access reset for every deployed app's end-users — no backfill possible/intended. Communicate before enabling (see plan Rollout order).*

| # | Task | Files | Est. | Status |
|---|---|---|---|---|
| S3.1 | Reserve/use `scopedAppId` on `SessionData` for the optional separate-user-table path (not the shipped Runtime's path — that's S3.7). | `SessionService.java` | 30 min | ⬜ |
| S3.2 | `EntityAccessGuard` with two entry points: (a) `check(entityKey,...)` parsing `{tenantId}_{appId}_{entityName}` for `/api/{entity}`; (b) `check(tenantId, appId, entityName,...)` for the two path-segmented families. Allow rule: (i) Studio session is an `appbana_app_members` member of `(tenantId, appId)` — **any role** — **or** (ii) runtime session `scopedAppId` equals `appId` **or** (iii) app is `publicRead` and request is `GET`. Break-glass admin token is fall-through, evaluated last. | new `com.appbana.security.EntityAccessGuard` | 150 min | ⬜ |
| S3.3 | `GenericAppAuthController.login()`: (a) issues a real session via `SessionService.createSession(...)` with `scopedAppId` set; (b) fetch-by-email + verify password in Java (not SQL — BCrypt can't compare in `WHERE`); (c) normalize response so nonexistent-entity/app and wrong-password both produce the same generic 401. | `GenericAppAuthController.java` | 105 min | ⬜ |
| S3.4 | Wire `EntityAccessGuard` into **every** `GenericEntityRoutes` route per the S0.2 census — the 16+ existing `authEnabled` blocks *and* the 11 routes (studio-scoped + env-scoped families) with no such block today. | `GenericEntityRoutes.java` | 150 min | ⬜ |
| S3.5 | Add `publicRead: boolean` flag (default `false`) on app/entity metadata for legitimately public apps. | `AppMetadata`/`EntitySchema`, `SchemaRoutes.java` | 45 min | ⬜ |
| S3.6 | Tests: `CrossTenantEntityAccessTest`, `CrossAppEntityAccessTest` (same-tenant, different app) across all 3 route families, `RuntimeSessionScopedToSingleAppTest`, `LoginDoesNotLeakEntityExistenceTest`. | new tests | 120 min | ⬜ |
| S3.7 | (a) Confirm S2.6's `AppRoutes` list/get wiring accepts an `end-user`-role membership for `GET /appbana-studio/{t}/apps/{id}` (no new carve-out — already S2.6). (b) Land the deferred `e2e/tests/a11y-runtime.spec.ts` authenticated-shell test. (c) End-to-end verification against the real running Runtime with an `end-user`-role membership row: list/get succeed, update/delete/schema-management 403. No Runtime frontend change expected. | `AppRoutes.java` (verify only), `e2e/tests/a11y-runtime.spec.ts` | 45 min | ⬜ |

**Exit criteria — S3**
- [ ] With global auth disabled (today's default), entity data is not reachable without a valid, correctly-scoped session, across all 3 route families.
- [ ] A Runtime end-user session for App 1 gets 403 on App 2's entity routes, same tenant.
- [ ] A `publicRead` app still serves anonymous `GET`s.
- [ ] Break-glass admin token still works as an override; its absence no longer means "no check happens".
- [ ] `GenericAppAuthController.login` returns identical status/body for "app doesn't exist" vs. "wrong password".
- [ ] The shipped `app-bana-runtime` logs in (existing platform login, unchanged) and loads its own app end-to-end against a running backend using an `end-user`-role membership row; the same end-user 403s updating/deleting the app or loading a second app they aren't a member of. **Verified in the running apps (5175/8080), not guard unit tests alone.**

### UI verification script — S3
- **S3.1 / S3.2** [Cat. 2 — reserved field / guard class, not wired yet] Proof deferred to S3.3/S3.4.
- **S3.3** [Cat. 1] Runtime's real login form for a deployed app with its own end-user table. Confirm correct credentials succeed; confirm a wrong password on a real account and a request for a nonexistent account show the identical on-screen message/status (cross-check via the browser network tab).
- **S3.4** [Cat. 1 — both sides] Studio: as User B (no membership on App-A), Studio's `DataDrawer` for App-A's entity must fail/be empty. Runtime: an end-user session scoped to App-A works on App-A's own `StudioTableLive` grid; the same session 403s if pointed at App-B's entity route via a direct URL edit.
- **S3.5** [Cat. 1 for the read side] Mark one entity `publicRead: true` (via chat, or a direct schema PATCH if Studio has no toggle yet — flag if so), then load that Runtime page in a fresh, fully logged-out browser tab and confirm it renders with no session, while a non-`publicRead` entity on the same app still requires login.
- **S3.6** [Cat. 2 for route-family shapes with no dedicated screen, Cat. 1 for the ones that do] Formalizes S3.4's scenarios; any route family without a Runtime/Studio consumer is noted as such, not faked.
- **S3.7** [Cat. 1 — the primary proof for all of S3, explicitly called for by the plan's own exit criteria] Grant a fresh User C `end-user` role on App-A, log into Runtime as User C, list/view App-A's data (must succeed), attempt an edit/delete or navigate to a second app with no grant (must 403 both) — in the running apps, not guard unit tests alone.

---

## Sub-phase S4 — Credential hygiene *(independent, parallel-safe)*

| # | Task | Files | Est. | Status |
|---|---|---|---|---|
| S4.1 | Wire `PasswordService` (BCrypt) into `UserManager`: new registrations hash on write; a successful plaintext-compare login on a legacy row immediately rehashes and persists. | `UserManager.java` | 45 min | ⬜ |
| S4.2 | Same transparent-rehash treatment for `GenericAppAuthController`'s runtime end-user table: fetch-by-email-then-verify-in-Java, plus hash-on-write for every path that sets the password column, not just login. | `GenericAppAuthController.java` + password-write paths | 90 min | ⬜ |
| S4.3 | CSRF: remove dead `CsrfMiddleware` registration references from docs and delete the unused middleware (bearer-token auth today, not cookie-based — classic CSRF doesn't apply). Drive-by: fix the dead `/api/csrf/token` vs. real `/api/csrf-token` mismatch in `EXCLUDED_PATHS`. | `docs/features/SECURITY_FEATURES.md`, `CsrfMiddleware.java`, `SessionMiddleware.java` | 30 min | ⬜ |
| S4.4 | Correct `docs/features/SECURITY_FEATURES.md` end-to-end against post-S4 reality (remove false BCrypt-already-done / wired-CSRF claims; replace stale LitElement snippets). | `docs/features/SECURITY_FEATURES.md` | 30 min | ⬜ |
| S4.5 | Tests: `PasswordRehashOnLoginTest`, `NewRegistrationIsHashedTest`. | new tests | 45 min | ⬜ |
| S4.6 | Add `tenant_id`/`app_id` columns to `appbana_audit`; populate on every write. | Liquibase changeset, `AuditLogService.java` | 60 min | ⬜ |
| S4.7 | Stop writing the raw token/session id into `appbana_audit.actor` on any path — always resolve to the real userId via `resolveIdentity` first. | `GenericEntityRoutes.java` | 30 min | ⬜ |

**Exit criteria — S4**
- [ ] No code path compares a raw password string to a stored value.
- [ ] Every pre-existing plaintext row transparently upgrades to BCrypt on its owner's next login — no forced reset.
- [ ] `SECURITY_FEATURES.md` matches what's actually running.
- [ ] `appbana_audit` rows carry `tenant_id`/`app_id`; `actor` is never a raw token/session id.

### UI verification script — S4
- **S4.1** [Cat. 1 — against a real existing plaintext row] Log into Studio with one of `data/users.json`'s existing plaintext-password accounts using its real password, confirm login still succeeds, re-inspect the stored value afterward and confirm it's now a bcrypt hash, then log in a second time to confirm the now-hashed row still authenticates.
- **S4.2** [Cat. 1 — same pattern, Runtime end-user login] Using S3.3's fixture end-user account.
- **S4.3** [Cat. 2 — dead-code removal, no behavior change] Basic Studio login smoke (bearer-token auth unaffected).
- **S4.4** [Cat. 2 — documentation, not code] Read-through against S4.1–S4.3's actual landed behavior; not UI-testable by nature.
- **S4.5** [Cat. 2 — automated tests] Formalizes S4.1/S4.2's already-proven scenarios.
- **S4.6** [Cat. 2 — no audit-log viewer exists anywhere in Studio/Runtime] Perform any normal UI action (e.g., S1.3's app edit), then inspect the resulting audit row directly and confirm `tenant_id`/`app_id` are populated.
- **S4.7** [Cat. 2 — same reason as S4.6] Same method; confirm `actor` is the real userId, not a raw token.

---

## Sub-phase S5 — Capstone tests + ai-builder trust chain *(last — needs S1–S3)*

| # | Task | Files | Est. | Status |
|---|---|---|---|---|
| S5.1 | `AiChatController`/`AgentContext` verifies caller's `tenantId`/`appId` against app-bana-service (reusing `EntityAccessGuard`/`isAppOwnerOrSystem`) instead of trusting client-supplied JSON body fields. | `ai-builder/.../api/AiChatController.java` | 90 min | ⬜ |
| S5.2 | `CrossTenantIsolationTest` capstone suite — 2 tenants × 2+ apps each, no session from one tenant/app can read/write/delete another's apps, roles, files, saved views, template writes, or entity rows in any of the 3 entity-route families. | new integration test | 120 min | ⬜ |
| S5.3 | Document the enforced model in `.github/copilot-instructions.md` (mirrors how Maker-Checker was documented). | `.github/copilot-instructions.md` | 30 min | ⬜ |

**Exit criteria — S5**
- [ ] Capstone cross-tenant suite passes, wired into CI (not a one-off manual run).
- [ ] ai-builder no longer trusts client-supplied tenant/app identity for any tool call.

### UI verification script — S5
- **S5.1** [Cat. 1] As User B, use Studio's real ChatPane against App-A's context (however it's reachable, which after S1–S3 it shouldn't be), confirm the AI agent's tool calls are rejected/re-scoped server-side rather than trusting whatever the client sent.
- **S5.2** [Cat. 2 — automated capstone suite] Formalizes the full set of scenarios already individually proven live across S1–S3's own checkpoints; doesn't newly prove anything a browser hasn't already shown.
- **S5.3** [Cat. 2 — documentation] Not UI-testable by nature.

---

## Open product decisions this tracker will hit before certain tasks

These block nothing in S0, but the tasks noted should pause for a product answer rather than guess:
1. **Self-registration policy** — affects nothing directly (S2.7 owner-invite is sufficient for v1), informational only.
2. **First-run local/dev experience post-S3** — affects S2.4/S3 rollout docs only, not the guard code itself.
3. **Runtime end-user password write path (S4.2)** — needs a short investigation (folded into S4.2 itself) into every place a runtime end-user's password column is written, not just login.
4. **`/api/templates` reads — shared catalog or per-tenant?** — S1.6 proceeds with "writes gated, reads stay public" per the plan's own adopted default; revisit only if product says otherwise.
5. **⏸️ BLOCKS S1.6 and S2.7 specifically — no product UI (or chat tool) exists for either capability today, confirmed by source read.** Templates writes (S1.6) and membership grant/revoke (S2.7) have no button and no AI-tool path anywhere in Studio/Runtime/`ToolRegistry`. Options: (a) verify these two via a direct, real-credentialed HTTP call as a named, documented exception to the UI-first rule (Testing doctrine Cat. 3) — zero added scope; or (b) build minimal UI for one or both (e.g., a small "Members" panel in `Header.tsx`) — real scope beyond the security plan, own estimate needed. Defaulting to nothing until you answer; S1.6/S2.7 stay ⬜ past their code landing if code lands before this is answered.

---

*Created 2026-08-01, tracking execution of `TENANT_ISOLATION_SECURITY_PLAN.md` (92b20ba). Update the Status column and check off exit criteria as each task lands; do not batch multiple task IDs into one commit.*
