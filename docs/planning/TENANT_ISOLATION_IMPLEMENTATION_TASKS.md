# Tenant & App Isolation — Implementation Task Tracker

**Source design doc:** [`TENANT_ISOLATION_SECURITY_PLAN.md`](./TENANT_ISOLATION_SECURITY_PLAN.md) (DRAFT, six review rounds closed, `92b20ba`). This document does not repeat that plan's rationale, review history, or the evidence behind each finding — it exists purely to break the plan's six sub-phases into independently-committable, independently-reviewable units of work, track their status, and record what actually landed.

**How to review this:** each task below lands as its own commit on `feature/tenant-security`, with a commit message prefixed `feat(S#.#):` / `test(S#.#):` / `fix(S#.#):` / `docs(S#.#):` naming the task ID — use `git log --oneline --grep="S1.2"` (etc.) to find a specific task's commit, or `git log --oneline` for the full sequence. A task is only marked ✅ once its own exit-criteria checks pass and (where the task touches a route or UI-visible behavior) it has been driven through the real Studio/Runtime UI per this repo's testing convention, not backend tests alone. See **Testing doctrine** below for exactly how each task is proven — including the small number that have no end-user UI surface at all, which are labeled as such, never silently skipped.

**Status legend:** ⬜ not started · 🔄 in progress · ✅ done (committed) · ⏸️ blocked (see note)

**Total scope:** ~56.92 hr across 52 tasks. Rollout constraints carried over from the plan (do not lose these when executing):
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
| S0.2 | Machine-generated route census across every `*Routes.java`: path, middleware-excluded?, identity gate present?, tenant/app check present?, tenant/app source (`path`\|`query`\|`body`\|`header`\|`none`), **known callers** (studio/runtime/shared/ai-builder/e2e/"none found"), **data preconditions** (what must exist for the call to succeed). Predicate = any client-controlled tenant/app identifier, not just path params. Attach the generated table to the plan doc. | new `RouteCensus` tool/report, appended to plan doc | 165 min | ✅ |
| S0.3 | Test that fails when a route is registered without a census entry — assert on the **set** of route signatures (method+path) via `Router` reflection vs. census, not a count. | new test | 75 min | ✅ |
| S0.4 | Fix or fence `Router.handle(HttpServletRequest,...)` — it bypasses the middleware chain entirely (M1). Either route it through the same chain `handle(HttpExchange)` uses, or fail fast at startup if `serverType` is set to anything but `jdk`. | `Router.java`, `Main.java` | 45 min | ✅ |
| S0.5 | **(New, review round 4; scope corrected round 5)** Add an automated check that sums every task row's estimate in this tracker per sub-phase and asserts the total against the plan doc's S0–S5 summary table (and the grand Total scope figure) — the same "derive it, don't hand-maintain it" fix S0.2/S0.3 already applied to the route census. **Range convention (round 5):** where an estimate is a range (only `S0.0`'s "30–90 min"), take the upper bound — the convention already used by hand for every reconciliation so far, now written down so the check and any manual cross-check can't land on different totals depending on which end someone picks. **Non-gating (round 5):** this task's own 90 min counts toward S0's total below, but its ⬜ status does not reopen S0's phase-completion gate for S1 — S0.0–S0.4's exit criteria were independently reviewer-accepted before S1 began (see "Post-acceptance external review of S0"); this task is scope added after that acceptance, like S1.15–S1.17 are for S1, not a retroactive condition on it. Registered after an independent line-item sum turned up a ~28% aggregate understatement across every phase but S3 (review round 4). | new test/script, `TENANT_ISOLATION_IMPLEMENTATION_TASKS.md`, `TENANT_ISOLATION_SECURITY_PLAN.md` | 90 min | ⬜ |

**Exit criteria — S0**
- [x] `mvn test` compiles and runs on this repo's toolchain. Confirmed via clean `mvn clean test` on JDK 25: `app-bana` 331/331 passing, `AI Builder Service` 197/197 (2 skipped), `BUILD SUCCESS`. Fix was binding `maven-enforcer-plugin` under the root pom's actual `<build><plugins>` (it was declared only in `pluginManagement`, which never executes on its own, so the Java-25 gate never fired). Live `start-everything` boot proof completed as part of S0.1's verification below — all four services booted clean from a cold shell with JAVA_HOME set to the JDK 25 install.
- [x] All three credential forms resolve to the same principal on a middleware-excluded route. Unit-proven by `AuthServiceTest` (13/13) and live-proven: two fixture users (see session memory's "Standing UI fixture") each logged into Studio via the real Create Account form, exercising the Bearer-token session path end-to-end through Studio → backend → ai-builder → runtime, both resolving correctly to their own tenant with zero cross-leakage.
- [x] Bearer-carried admin/service token resolves via priority 1, never as a session lookup; a Bearer session id never satisfies `hasAdmin()`. Unit-proven by `AuthServiceTest.testAdminTokenViaBearerWithXUserId`, `testAdminTokenWithoutXUserId`, `testSessionIdViaBearerNeverTreatedAsAdmin`.
- [x] Route census exists, attached to the plan doc, every row has non-empty tenant/app-source, known-callers, and data-preconditions columns. Confirmed: all 14 `*Routes.java` files enumerated (97 routes total, verified against a fresh `file_search` of the routes directory), census appended to `TENANT_ISOLATION_SECURITY_PLAN.md` under "S0.2 Route census." Live Router-overload uncertainty resolved this session via direct `logs/backend.log` inspection (`handle(HttpExchange)` confirmed live). One census finding (FileRoutes/e2e URL-shape mismatch) independently spot-verified via grep before being written up.
- [x] Adding/renaming/removing a route without updating the census fails CI (set comparison). `RouteCensusTest.registeredRoutesMatchCensusExactly` reflects `Router`'s private route table via `RouteRegistry.buildRouter()`, builds the live (method,path) signature set, and diffs it against a hardcoded 96-entry expected set (97 registration call-sites collapse to 96 unique signatures — one confirmed duplicate in AppRoutes.java). Verified live: added a throwaway `GET /api/__census_drift_probe` route, test failed and named it precisely ("Registered in Router but MISSING from the S0.2 census"), reverted (clean `git diff`), full suite green again at 345/345.
- [x] `serverType != jdk` either shares the middleware chain or refuses to start. Chose fail-fast (the plan's own "either/or"): `TomcatServer` has zero callers besides `Main.java`'s switch and zero test coverage, confirming it's genuinely dormant, so fencing it off is lower-risk than retrofitting an unverified middleware integration. `Main.java`'s `case "tomcat":` now logs an explicit M1-referencing error and calls `System.exit(1)` **before** `TomcatServer.start(port)` is ever reached — no port is bound, no request is ever served unprotected. Any other unrecognized value still safely falls through to the pre-existing `default` → `jdk` behavior, unchanged (only the one genuinely-vulnerable value is fenced).
- [ ] *(Non-gating — see S0.5's row and round 5's note below)* The tracker's summary table (this doc's headline + the plan doc's S0–S5 table) matches an automated sum of every task row's estimate, not a hand-maintained figure (review round 4, S0.5). S0's substantive exit criteria above are already met and reviewer-accepted; this bullet tracks only S0.5's own follow-through.

### UI verification script — S0
- **S0.0** ✅ [Cat. 2 — build tooling, no UI surface] `mvn -q -DskipTests compile` (then full `mvn test`) succeeds on a clean shell; `start-everything` boots all four services. Confirmed 2026-08-01: cold-started all four services (AI Builder 8081, Backend 8080, Studio 5174, Runtime 5175) via `start-everything.bat` with JAVA_HOME set to the JDK 25 install — all came up clean, Liquibase ran 19/19 changesets with no errors.
- **S0.1** ✅ [Cat. 1] Open Studio at http://localhost:5174, log in as an existing/fixture user, confirm the app switcher populates and a chat message round-trips — this exercises `resolveIdentity` on every request; a silent break shows up as a failed login or empty switcher. Confirmed 2026-08-01, browser-driven, for **two** independent fixture users (exceeding the minimum bar): User A registered fresh → switcher went from "Select app"/empty → populated with "Contact List" after one chat round-trip → runtime iframe rendered the live app with User A's name in its header. Repeated for User B (separate tenant) with the same result ("Customer Onboarding"). Zero cross-tenant leakage observed (User B's switcher was empty on first login, no trace of User A's app). See session memory for exact credentials/tenant IDs.
- **S0.1b** ✅ [Cat. 2 — same code path as S0.1] No separate script; green test (13/13) + S0.1's live smoke pass (both users) together are the proof.
- **S0.2** ✅ [Cat. 2 — generated report, not behavior] Read the generated census table for completeness against the actual registered route set — a structural review, not a browser action. Confirmed 2026-08-01: 5 parallel research passes covered all 14 route files (97 routes); cross-checked file count via `file_search` (14/14 match); one flagged discrepancy (FileRoutes upload path vs. the H1 e2e test's URL shape) independently re-verified via direct `grep_search` of both the route registration and the spec file, confirming a genuine test/implementation mismatch rather than a research error. Census appended to the plan doc with a consolidated "no known caller" list and a 9-point critical-findings summary for S1–S3 to scope against.
- **S0.3** ✅ [Cat. 2 — CI gate] Temporarily add/remove a dummy route locally, confirm the test fails, then revert. Not UI-observable by nature. Confirmed 2026-08-01: added `router.get("/api/__census_drift_probe", ...)` to `RouteRegistry.buildRouter()`, ran `RouteCensusTest` — failed with `Tests run: 1, Failures: 1` and the message pinpointed `+ GET /api/__census_drift_probe` under "MISSING from the S0.2 census"; reverted the line, confirmed `git diff --stat` showed no changes, re-ran the full `app-bana-service` suite — 345/345 passing, `BUILD SUCCESS`.
- **S0.4** ✅ [Cat. 2, dormant branch by design] Confirm `config.json`'s `serverType` is `jdk` here (the fail-fast branch is intentionally never exercised in this environment). Live proof is the same Studio login smoke as S0.1. Confirmed 2026-08-01, both directions, as real separate OS processes (not just unit tests) — repackaged the fat jar with the new `Main.java`, then: (1) confirmed `config.json`'s `serverType` here is `null` (defaults to `jdk`, matching the doc's premise); (2) temporarily set it to `"tomcat"`, ran `java -jar app-bana-1.0-SNAPSHOT-fat.jar` on a scratch port — process exited with code 1 and logged `serverType="tomcat" is disabled: ... see TENANT_ISOLATION_SECURITY_PLAN.md finding M1 / task S0.4`, confirmed via `Get-NetTCPConnection` that no port was ever bound; (3) reverted `config.json` (`git diff --stat` clean); (4) re-ran the same jar on the scratch port — booted clean, logged `AppBana (JDK HTTP) running on port 18080`, killed the transient process; (5) confirmed the actual dev backend on 8080 was undisturbed throughout (`GET /health` → `{"status":"UP"}` before and after). Full `app-bana-service` suite re-confirmed green (345/345) after the `Main.java` change, before repackaging.
- **S0.5** [Cat. 2 — doc/build-tooling consistency check, no UI surface] Structural proof: run the check, confirm it currently passes against the round-4-corrected figures; temporarily bump one task row's estimate without updating the summary table, confirm it fails with a clear per-phase mismatch (not just a bare boolean), then revert.

### Post-acceptance external review of S0 — findings and remediation

An external reviewer examined commits `5b4def5`..`468beb3` (all of S0.0–S0.4) and returned a verdict of
**"S0 is accepted,"** conditioned on closing one 🟠 High finding before S1 begins, plus one 🟡 Medium
and two 🟢 Nits that could "ride along." All four are resolved as follows:

- 🟠 **High — S0.3's `EXPECTED_ROUTES` was a hardcoded copy, not a read of the actual census doc.**
  The guard only ever checked `Router` against a set a developer had typed once; the real
  `TENANT_ISOLATION_SECURITY_PLAN.md` census table it was meant to enforce could drift under it with
  nothing to catch that — and in fact already had 4 `ApprovalRoutes` rows abbreviated with a `.../`
  shorthand plus one genuine `{entity}`/`{entityName}` mismatch on the `submit` row. **Fixed:**
  expanded/corrected those 5 doc rows to full literal paths, then rewrote `RouteCensusTest` to parse
  the "S0.2 Route census" section of the plan doc directly at test-run time instead of maintaining a
  second copy. Re-verified the Cat.2 drift-detection protocol on the new implementation (temporarily
  renamed a censused route in the doc; test failed with both a missing-from-census and a
  no-longer-registered line; reverted; full 345/345 suite green again). Commit `0ddb747`.
- 🟡 **Medium — the `session_id` cookie credential form was accepted end-to-end but never actually set
  by any client**, dead-but-accepted attack surface that also undermined the CSRF-non-applicability
  premise elsewhere in this plan (cookie-based auth is what makes CSRF a real concern). Independently
  re-confirmed via a fresh repo-wide grep (studio, runtime, shared, ai-builder, e2e) before acting —
  zero cookie-setting call sites anywhere. **Fixed:** removed the cookie-parsing branch from
  `AuthService.extractSessionCredential`, updated all "3 credential forms" doc comments (`AuthService`,
  `SessionMiddleware`) to describe the 2 remaining forms, and removed/adjusted the 3 cookie-specific
  tests (`AuthServiceTest` 13→11, `SessionMiddlewareTest` 19→18; full suite 345→342, exactly accounting
  for the 3 removed tests). Full suite re-confirmed green post-change.
- 🟢 **Nit — three stale "Java 21" mentions in `.github/copilot-instructions.md`** (contradicts the
  now-enforced Java 25 requirement). Not fixed standalone, per the reviewer's own recommendation —
  folded into S5.3 (already the task that documents this plan's model in that file) as an explicit
  sub-item so it isn't lost as a driveby edit.
- 🟢 **Nit — `PermissionServiceTest` reports "Tests run: 0."** Explicitly out of scope: pre-existing,
  unrelated to S0, reviewer's own words were "worth a glance sometime," not a blocking or scheduled item.

**Reviewer sign-off received:** independently re-verified every item above against the artefact rather
than the writeup — including perturbing the census doc a second time (`DELETE /schema/{name}` →
`{nameTYPO}`) and confirming `RouteCensusTest` failed both directions again, and re-reading
`AuthService.java` to confirm the cookie branch was deleted, not commented out. Verdict: **"S0 is
closed. S1 is unblocked — start it."** One new 🟢 nit raised (recorded below, not actioned now) and two
reminders for S1 specifically — both already encoded in this doc's S1 exit criteria and the plan doc's
R5-1/R5-2 (`TenantAccessGuard`'s admit-first admin branch; S1+S2 as one deployable unit) — no new work
item needed for either, just flagged here as what to double check when S1.2/S1.14 land.

---

## Sub-phase S1 — Tenant boundary on app management

*Deployment note: ships together with S2, not before — see rollout constraints above.*

| # | Task | Files | Est. | Status |
|---|---|---|---|---|
| S1.1 | Add `tenantId` + reserve `scopedAppId` on `SessionData`; populate `tenantId` at login from `User.tenantId` | `SessionService.java`, `AuthenticationController.java` | 45 min | ✅ |
| S1.2 | New `TenantAccessGuard.requireOwnTenant(session, pathTenantId, pathAppId)`. Order: **(0)** valid service/admin token (`extractServiceToken`+`hasAdmin`) admits immediately regardless of path tenant; **(1)** 401 if no resolved identity; **(2)** if `pathAppId` present, an `appbana_app_members` row for `(app's tenant, pathAppId, userId)` admits despite a tenant mismatch (ships inert until S2.6 wires `AppMembershipService.isMember` in); **(3)** otherwise 403 on mismatch. Bare tenant-wide app-list route has no membership exception — own-tenant only. | new `com.appbana.security.TenantAccessGuard` | 75 min | ✅ |
| S1.3 | Wire the guard into **every** `AppRoutes` handler per the S0.2 census: list/get/update/delete plus `publish`, `deploy/local`, `commits`, `commits/rollback`, `versions`, `deploy/{versionId}`, `pipeline`, `restore-schemas`, `workflow` GET/PUT, `pages/{pageId}` GET/PUT/DELETE. | `AppRoutes.java` | 120 min | ✅ |
| S1.4 | Add the missing `isAppOwnerOrSystem` check to `DELETE /schema/{name}` (today only `POST /schema` has it). | `SchemaRoutes.java` | 30 min | ✅ |
| S1.5 | `GET /api/debug/schemas` requires the same session check its `/names` sibling already has; stop relying on `ENTITY_API_PATTERN` segment-count arithmetic — name debug/admin routes explicitly in `EXCLUDED_PATHS`'s complement. | `SchemaRoutes.java`, `SessionMiddleware.java` | 30 min | ✅ |
| S1.6 | Gate `POST/PUT/DELETE /api/templates` behind an authenticated (admin, for now) identity; reads stay public pending the open product decision. | `AppRoutes.java` | 30 min | ✅ |
| S1.7 | `POST /api/files/upload` requires a resolved identity; derive `tenantId`/`appId` from it instead of the request body. Add an upload-path test to `FileRoutesTenantIsolationTest` (today download-only). | `FileRoutes.java` | 45 min | ✅ |
| S1.8 | `SavedViewRoutes`: require a resolved identity on all 3 routes; add `tenant_id`/`app_id`/`owner_user_id` to `DELETE_SQL`'s WHERE clause (today: `view_id` alone). | `SavedViewRoutes.java` | 45 min | ✅ |
| S1.9 | Dedupe the two identical `GET .../env/{env}/full` registrations into one; guard it and its `.../full` sibling with the same tenant+membership check (no public carve-out — confirmed zero real callers). Fix the six `SchemaRoutes.java` call sites passing `extractToken()`'s output to `hasRead`/`hasWrite`: convert **all six to `hasAdmin` via `extractServiceToken()`** (readToken is retired — see plan Non-goals, R6-1), leaving `AuthService.hasRead`/`cfg.getReadToken()` with no remaining callers anywhere. | `AppRoutes.java`, `SchemaRoutes.java` | 60 min | ⬜ |
| S1.10 | Startup: log a loud repeated `WARN` while `AuthService.authEnabled(cfg)==false`. | `ApiServer.java` | 30 min | ⬜ |
| S1.11 | `CrossTenantAppAccessTest` + `CrossTenantSchemaAccessTest`: tenant B session must not list/get/update/delete/publish/deploy/rollback/restore tenant A's apps, nor read/delete tenant A's schemas. Positive case: a tenant B session that **is** a member of one specific tenant A app is admitted for that app's list/get (finishes once S2.6 activates the exception — write the deny cases now, finish the positive case in S2.9). | new tests | 105 min | ⬜ |
| S1.12 | Fix `SessionMiddlewareTest`'s tautological assertions (`testPublicRuntimeAppsPathExcluded`/`testPublicDeployedAppsPathExcluded` assert path shapes no real route has) — rewrite against real route shapes, flip expectation to "requires session" now that S1.9 removes the public carve-out. Split `testTemplatesPathExcluded` into read-still-excluded vs. write-requires-auth. | `SessionMiddlewareTest.java` | 30 min | ⬜ |
| S1.13 | `login()`/`register()` in `api-client.ts` must throw (not default `tenantId` to `'default'`) when the backend response omits `tenantId`. Same fix in `e2e/tests/hardening/fixtures.ts`'s `newHardeningFixture`. | `app-bana-shared/src/api-client.ts`, `e2e/tests/hardening/fixtures.ts` | 25 min | ⬜ |
| S1.14 | `BreakGlassAdminBypassesTenantGuardTest` — a valid service/admin token (with or without `X-User-Id`) is admitted by `TenantAccessGuard` on an `AppRoutes`/`SchemaRoutes` route regardless of path tenant. | new tests | 30 min | ⬜ |
| S1.15 | Add tenant-filtering to `GET /schema` (only list the caller's own tenant's schema names, not all tenants') and an ownership check to `GET /schema/{name}` (403 if the caller doesn't own the app), mirroring S1.4's `DELETE /schema/{name}` fix. Found unfixed by any existing S1 task — review round 1, finding H1. | `SchemaRoutes.java` | 45 min | ⬜ |
| S1.16 | **(Revised, review round 3 — severity split + `/openapi.json` correction)** `GET /api/endpoints` and `GET /openapi.json` are each gated only by the optional `authEnabled(cfg)` block (same shape S1.4/S1.15 found on their `SchemaRoutes.java` siblings) with no fallback check beneath it. **`GET /api/endpoints` is the higher-severity of the two**: it returns every schema's full tenant-qualified key (`SchemaManager.listSchemaNames()`, unfiltered) pre-formatted as ready-to-call `POST /api/{key}`, `GET /api/{key}`, `GET /api/{key}/{id}`, `PUT /api/{key}/{id}`, `DELETE /api/{key}/{id}` strings — the enumeration primitive for the anonymous entity data plane (round 1 needed a direct Postgres query to obtain these keys; this route hands out the entire list). **`GET /openapi.json` is narrower than first described**: `OpenApiGenerator` keys `paths`/`components.schemas` by `schema.getName()` — the bare entity name, not the tenant-qualified key — so it discloses the union of field-level shapes across all tenants with no tenant attribution (a name collision across tenants silently last-write-wins, not a per-tenant enumeration); still real scope because it confirms which entity names exist platform-wide, and because every anonymous call loads *all* schemas from the DB and serializes the full result (~340 KB at today's ~455-schema count) — an unauthenticated amplification vector independent of the disclosure itself. Require a resolved identity unconditionally on both (admin, for now — mirrors S1.6's precedent for introspection/ops-facing routes) instead of the optional gate. Not covered by S1.9 (only changes which credential tier the *optional* gate checks) or S1.15 (scoped to `/schema`, `/schema/{name}` only). Found via `AuthEnabledAntiPatternTest` ratchet re-verification — S1 external review round 2; severity/text corrected round 3. **Priority note: despite the task number, `/api/endpoints`'s severity means this task should land before or alongside S1.15, not after it** (review round 3). | `SchemaRoutes.java` | 30 min | ⬜ |
| S1.17 | **(New, review round 3)** Once S1.15 + S1.16 land, `SchemaRoutes.java`'s remaining two `authEnabled(cfg)` gates (`POST /schema`, `DELETE /schema/{name}`) are pure dead weight — both already have a separate, unconditional `isAppOwnerOrSystem` ownership check beneath them (S1.4's fix, and the pre-existing `POST /schema` check), so the optional gate contributes nothing. Delete both wrappers, taking this file's `authEnabled` count to zero, and **remove its entry from `AuthEnabledAntiPatternTest.BASELINE` entirely** rather than setting it to `0` — **(corrected, review round 4)** both a removed entry and a `0` entry fail the test on any future occurrence (an absent key via the "new file" branch, a `0` entry via the existing `count > max` branch — not a stronger guarantee, as this row originally claimed); removal is simply the more honest representation (the file genuinely has zero occurrences left) and lets it drop out of the map entirely. Makes the test's own docstring ("S3.4 is the task that removes this pattern repo-wide") true for this file specifically, ahead of S3.4. | `SchemaRoutes.java`, `AuthEnabledAntiPatternTest.java` | 30 min | ⬜ |
| S1.18 | **(New, review round 6)** `GET /api/files/{tenantId}/{appId}/{fileId}` requires a session (confirmed: its path falls outside every `SessionMiddleware` exclusion rule), but both `FileUploadField.tsx`'s preview link and `StudioTableLive.tsx`'s download column use a plain `<a href target="_blank">`, which can never carry the required `Authorization` header — every real download click 401s. Decide and implement one of: (a) whitelist the route in `SessionMiddleware` to restore the anonymous access `FileRoutes.java`'s Javadoc originally documented, or (b) switch both render sites to an authenticated `fetch` + `URL.createObjectURL` download. | `SessionMiddleware.java` or `FileUploadField.tsx` + `StudioTableLive.tsx` (per decision) | 60 min | ⬜ |

**Exit criteria — S1**
- [ ] Tenant B session gets 403 (not 404/200) on every `AppRoutes`/`SchemaRoutes` route the census lists against Tenant A, including `restore-schemas`, `DELETE /schema/{name}`, and (review round 1, H1) `GET /schema` (tenant-filtered) and `GET /schema/{name}` (ownership check) — S1.15.
- [ ] `GET /api/endpoints` and `GET /openapi.json` require a resolved identity unconditionally, not only when `authEnabled(cfg)` is true (review round 2, S1.16).
- [ ] `SchemaRoutes.java`'s two remaining `authEnabled(cfg)` wrappers (`POST /schema`, `DELETE /schema/{name}`) are deleted and the file's `AuthEnabledAntiPatternTest.BASELINE` entry is removed entirely, not set to `0` (review round 3, S1.17).
- [ ] No resolved identity → 401, distinct from a wrong-tenant 403.
- [x] `GET /api/debug/schemas` requires the same session as `/names`.
- [x] `POST/PUT/DELETE /api/templates` require an authenticated admin identity.
- [x] `POST /api/files/upload` and all of `SavedViewRoutes` require identity; saved-view delete scoped by tenant+app+owner.
- [ ] File downloads (`GET /api/files/{tenantId}/{appId}/{fileId}`) are actually reachable by a real logged-in end user, not just protected from anonymous/cross-tenant access (review round 6, S1.18).
- [ ] Both `.../full` routes require tenant+membership check; only one registration remains.
- [ ] `SessionMiddlewareTest` matches real route shapes/behavior.
- [ ] Tenant A's own users unaffected on every route above.
- [ ] Server logs a visible warning whenever global auth is disabled.
- [ ] A valid service/admin token is admitted by `TenantAccessGuard` regardless of path tenant.
- [ ] **Release-process criterion:** S1 is not deployed alone to any environment serving live deployed-app traffic — ships as one unit with S2.

### UI verification script — S1
- **S1.1** [Cat. 2 — no observable behavior alone] Proof deferred to S1.3.
- **S1.2** [Cat. 2 — guard class not wired to a route yet] Proof deferred to S1.3.
- **S1.3** [Cat. 1 — first real cross-tenant checkpoint] ✅ Done live 2026-08-01: logged into Studio as User B (session token pulled from the real `appbana-session` localStorage entry, no hand-built auth) — Header app switcher listed only "Customer Onboarding", User A's "Contact List" never appeared. From User B's own authenticated page context, called `GET /appbana-studio/t-bf0c8f57/apps/eac3fd22-d6af-4e8c-911c-71b6a6a95b3a` (User A's real app) — **403 `{"error":"Forbidden: caller's tenant does not match the requested app's tenant"}`**, never App A's content, never a 404. Non-regression: same call shape against User B's own app (`GET /appbana-studio/t-fc8d39e7/apps/05298dfa-...`) → 200 with correct data; own tenant-wide list → 200, only own app listed. Logged out, signed in as User A, opened "Contact List" via the Header switcher — full real content rendered in the preview iframe (own sidebar/pages/account name), confirming the now-guarded `GET .../apps/{id}` route still serves the owner normally.
- **S1.4** [Cat. 2 — confirmed no delete-entity chat tool or button anywhere in the product] ✅ Done live 2026-08-01: as User A, created a disposable throwaway entity `ScratchDeleteTarget` on the real "Contact List" app via `POST /schema` (real session token, real browser context) so the standing fixture's own entities were never touched. Logged in as User B, called `DELETE /schema/t-bf0c8f57_eac3fd22-...-71b6a6a95b3a_ScratchDeleteTarget` (User A's real schema) from User B's own authenticated page context → **403 `{"error":"Forbidden: caller is not authorized to delete entity schema for app eac3fd22-..."}`**. Confirmed via `GET /schema/{name}` → 200 that the schema was untouched/still existed after the blocked attempt. Logged back in as User A, confirmed still-200, then `DELETE ...?dropTable=true` as the real owner → 200 `{"status":"deleted"}`, then confirmed a follow-up `GET` → 404, proving the legitimate owner path still works end-to-end and the schema/table were actually removed.
- **S1.5** [Cat. 2 — debug route, no UI] ✅ Done live 2026-08-01: root cause confirmed by hand-tracing `SessionMiddleware.isExcludedPath` — `/api/debug/schemas` (2 path segments) matched `ENTITY_API_PATTERN` and was treated as a public entity-API path (fully anonymous), while its sibling `/api/debug/schemas/names` (3 segments) fell outside the pattern and correctly required a session — pure segment-count accident, not intent. Fixed by adding an explicit `path.startsWith("/api/debug/")` → always-requires-session branch in `isExcludedPath`, ahead of the `ENTITY_API_PATTERN` check, mirroring the existing roles/schema/approvals always-protected block. Live evidence: anonymous `GET /api/debug/schemas` (no auth header at all) → **401 `{"error":"Unauthorized","message":"Missing session token"}`** (previously 200, full cross-tenant schema dump). With a real session token from the live browser (User A, freshly logged in): `GET /api/debug/schemas` → 200 and `GET /api/debug/schemas/names` → 200 — both routes now behave identically, achieving the doc's exact exit criterion.
- **S1.6** [Cat. 3 — resolved: you selected **Option (a), direct HTTP call verification only** — gate the backend routes now, verify via real authenticated direct API calls as a documented Cat. 3 exception, no new UI/scope added; the same decision also applies to S2.7] ✅ Done live 2026-08-01: wrapped all 3 template-write routes (`POST`/`PUT`/`DELETE /api/templates`) with the established `extractServiceToken`+`hasAdmin` H8 pattern (same shape as existing `GenericEntityRoutes.java` admin gates); `GET /api/templates` and `GET /api/templates/{id}` left untouched/public. Verified with a temporary throwaway `adminToken` in `config.json` (reverted immediately after, confirmed clean via `git status --porcelain config.json`), backend rebuilt+restarted before and after: (1) `POST` with no token → **401**; (2) `POST` with a wrong token → **401**; (3) `POST` with the correct admin token → **201**, throwaway template created; (4) `PUT` with no token → **401**; (5) `PUT` with the correct admin token → **200**, updated; (6) `GET /api/templates` with no token at all → **200**, confirming reads stay public/unaffected; (7) `DELETE` with no token → **401**; (8) `DELETE` with the correct admin token → **200** `{"status":"deleted"}`, cleaning up the throwaway template; (9) follow-up `GET /api/templates/{id}` → **404**, confirming actual removal. Full test suite 357/357, `BUILD SUCCESS`, before commit.
- **S1.7** [Cat. 1] ✅ Done live 2026-08-01: `handleUpload` in `FileRoutes.java` now calls `TenantAccessGuard.requireOwnTenant(req, cfg, tenantId, appId)` right after the existing format checks — a request with no resolved identity gets 401, one whose resolved identity's own tenant doesn't match the body's `tenantId` gets 403, same shape as every other S1 guard call site (break-glass admin/service token still admits; `appId` ownership/existence itself is deferred to S2's membership model, consistent with S1.2's own documented scope). Independently, `uploadedBy` was hardcoded to always resolve `null` — it read `req.getAttribute("userId")`, an attribute `SessionMiddleware` never sets for this route because the route matches `ENTITY_API_PATTERN` and is skipped; fixed by calling `AuthService.resolveIdentity(req, cfg)` directly, the same fix pattern S0.1 established. **Frontend gap found and fixed in the same pass:** `FileUploadField.tsx`'s `upload()` sent no `Authorization` header at all — every real upload would have 401'd against the newly-guarded backend the moment this landed. Fixed by importing `getRuntimeToken()` (the same helper `useEntityRows`/`entity-query.ts` already use) and conditionally adding `Authorization: Bearer <token>` alongside the existing `Content-Type` header. Three new tests added to `FileRoutesTenantIsolationTest.java`, written and passing negative-before-positive per the round-5 protocol: `uploadWithoutSessionIsRejected` (401), `uploadToAnotherTenantIsRejected` (403), `uploadToOwnTenantSucceedsAndRecordsResolvedUploader` (201, then a direct SQL read confirms `tenant_id`/`app_id`/`uploaded_by` on the `appbana_files` row). Full suite green before and after: backend 370/370 (`BUILD SUCCESS`), runtime 276/276 Vitest + clean `tsc -b`. `config.json` reconfirmed unedited (`adminToken`/`readToken` both still `null`) both before and during. **Live browser proof:** neither standing fixture app had a real `file`-type field, so — rather than hand-editing a fixture's schema via raw API calls — used the real Studio chat as fixture User A to scaffold a new small app ("Document Library": one `Document` entity with a `file`-type `attachment` field, an "Upload Document" page), the same product surface any real user would use. Logged in, opened "Upload Document," used the real file-chooser dialog (native OS picker, triggered by the actual dropzone control, not a raw `<input>` DOM manipulation) to attach a small test file: got the real "File uploaded" toast, an "Attached: … · Replace" state, and a preview link shaped `/api/files/{tenantId}/{appId}/{fileId}` whose tenant/app segments matched the logged-in user's own — matching by construction, since `TenantAccessGuard` would have rejected any other value with 403. Confirmed directly against Postgres: the `appbana_files` row had `tenant_id='t-bf0c8f57'`, `app_id` = the new app's real id, and **`uploaded_by='42'`** (User A's real numeric user id from the session, previously always `null` — the H2 finding this task exists to fix). Test file + DB row deleted after verification; the "Document Library" app itself was left in place (harmless, and doubles as a ready-made file-field fixture for S1.8/S1.9's own flagged extra scrutiny).
  **Two pre-existing issues found as a side effect of live verification, both explicitly out of scope for this task and not introduced by it — flagged for a separate decision, not fixed here:**
  1. The download link itself (`GET /api/files/{tenantId}/{appId}/{fileId}`) 401s for a real end user. Root cause: the path has 4 segments after `/api/`, which is outside `SessionMiddleware.ENTITY_API_PATTERN`'s 2-segment max and doesn't match any other exclusion rule, so a session **is** required — but both places that render this URL (`FileUploadField.tsx`'s own "Preview" link and `StudioTableLive.tsx`'s "Download" column) emit a plain `<a href=... target="_blank">`, and a raw browser navigation can never attach the `Authorization` header this app's header-based (not cookie-based) auth model needs. This directly contradicts this same file's own class Javadoc ("remains anonymous end-to-end … protection rests on the (tenantId, appId, fileId) triple"), which describes the apparent original design intent, not current behavior. Confirmed via a direct unauthenticated request (401 `Missing session token`) and via inspecting the live anchor's attributes in-browser. Two valid fixes exist (whitelist the route in `SessionMiddleware` to restore the documented anonymous intent, vs. switch both render sites to an authenticated `fetch`+blob-URL download) with different security tradeoffs — a real design decision, not made unilaterally here.
  2. Saving the "Document" record via the real form's Save button failed both attempts with "is required" attached to the file field (even though a file was attached) and the Title/Description values not present in the submitted payload — looks like a pre-existing form-state wiring gap for the `file` field type on a create form, unrelated to tenant/identity handling. Not investigated further; the upload call itself (this task's actual scope) is already proven end-to-end above independently of whether the parent record saves.
- **S1.18** [Cat. 1 — pending the fix-approach decision above] Click the file's Preview/Download link as a real logged-in end user; today it 401s in a new tab (confirmed live during S1.7's review). Re-verify after the fix lands: the same click must actually retrieve the file, and an anonymous or cross-tenant request to the same URL must still be rejected.
- **S1.8** [Cat. 1] ✅ Done live 2026-08-01: `SavedViewRoutes.java`'s `handleList`/`handleUpsert` now call `TenantAccessGuard.requireOwnTenant` right after the null-check (same shape as every other S1 guard site); `ownerUserId` on upsert is now always server-derived via `AuthService.resolveIdentity` (client-supplied value ignored). `handleDelete` was rewritten load-then-authorize: a new `LOOKUP_SQL` fetches the row's real tenant_id/app_id/owner_user_id first (404 if missing), then the same tenant guard, then an admin-bypass-aware owner check (403 if the caller isn't the owner and isn't admin/service), then a parameterized `DELETE_SQL` now scoped by `view_id AND tenant_id AND app_id AND owner_user_id IS NOT DISTINCT FROM ?` (previously `view_id` alone — any authenticated caller who guessed/enumerated a viewId could delete anyone's view). All 4 guard call sites individually break-tested (neutered on purpose, confirmed the exact expected test(s) fail, reverted) across both routes and the delete path. 12 new tests in `SavedViewRoutesTenantIsolationTest`, full suite 382/382 `BUILD SUCCESS`.
  **Live browser proof:** the standing "Contact List" fixture page had no page-metadata `filters` array (an unrelated, pre-existing gap — see note below), so `SavedViewsBar`'s "+ Save current" button never rendered from typing into the per-column filters alone. As User A (real owner), added a minimal filter descriptor (`field: category, op: contains, label: Category`) to the page's table node via the same authenticated `PUT /appbana-studio/{tenantId}/apps/{appId}/pages/{pageId}` call the product's own save-page flow uses (real session token, real ownership) — a one-time, legitimate metadata change, not a security-relevant action, and reverted immediately after this verification. With the FilterBar now rendering, typed "VIP" into the real Category chip filter (real UI) → "+ Save current" appeared → clicked it (real UI click) → handled the resulting `window.prompt` via a standard Playwright prompt-stub (the `handle_dialog` tool did not intercept this app's prompt in time; stubbing the browser API before the click is the standard fallback and does not touch any backend/security code path) → saved view "S1.8 QA View" appeared as a real chip. Reloaded the page from scratch (fresh network fetch, not client cache) → chip still present, confirming server-side persistence; screenshotted. Logged into Studio as User B (real login, real session) and, from User B's own authenticated context, called `GET /api/saved-views` for User A's real tenant/app/entity → **403 "Forbidden: caller's tenant does not match the requested app's tenant"**, never the view data. Same context, `DELETE /api/saved-views/{User A's real viewId}` → **403**, same message. Re-checked from User A's own tab: the view still existed (1 row) — User B's blocked delete attempt had zero effect, directly proving the new `DELETE_SQL` WHERE clause. As User A (real UI), clicked the view chip's own "×" delete button → chip disappeared; confirmed via a follow-up list call that the row was actually gone (0 rows), proving the legitimate owner-delete path still works end-to-end. Afterward, reverted the Contact List page's metadata back to its original state (`filters` key removed) so the standing fixture is unchanged for future verification work.
  **Pre-existing gap found as a side effect, out of scope for this task, not fixed here:** `SavedViewsBar`'s "+ Save current" button is gated purely on `StudioTableLive.tsx`'s page-metadata-driven `filterValues` (fed only by `<FilterBar>`, which only renders when the page's `props.filters` array is non-empty) — the separate, more commonly-populated per-column `columnFilterValues`/`TableHeader` filter state is never folded into `filterValues`, so on any scaffolded list page without explicit `filters` metadata (most of them, including both standing fixture apps), "+ Save current" is unreachable no matter what a real user types into the visible per-column filter row. Worth a future task if saved views are meant to work broadly, not just on pages that opted into the separate `FilterBar` feature.
- **S1.9** [Cat. 2 — confirmed zero real callers for `.../full`; `readToken` is a service credential with no UI login anywhere] Structural proof: grep confirms no remaining `hasRead`/`getReadToken()` callers; direct-call check that the six converted routes now require `hasAdmin`.
- **S1.10** [Cat. 2 — ops log line] Start the backend with auth disabled, confirm the repeated WARN line in the terminal — an operator check, not a browser check.
- **S1.11 / S1.12** [Cat. 2 — automated tests] Formalize the scenarios already proven live in S1.3/S1.8; no new script. S1.11's "nor read/delete tenant A's schemas" clause depends on S1.15 landing first (review round 1, H1) — `GET /schema/{name}` has no ownership check today, so that clause would fail without it.
- **S1.13** [Cat. 1 happy path / Cat. 2 failure branch] Proof: normal Studio login smoke. The fail-closed branch needs the backend to omit `tenantId`, not naturally triggerable against the real running backend — verified by its unit test only, noted rather than skipped silently.
- **S1.14** [Cat. 2 — no login screen for a raw admin/service token exists, by product design] Direct-call proof: the real `adminToken` from `config.json` as a header against a Tenant-A-owned route with no Studio session presented — confirms bypass still works.
- **S1.15** [Cat. 2 — `GET /schema` has no per-tenant UI surface of its own; `DataDrawer` calls it in an already-app-scoped context] Direct-call proof once implemented, same style as S1.4: a tenant B session must not see tenant A's schema names in the `GET /schema` list, and must get 403 (not the real schema) from `GET /schema/{name}` on a tenant A key.
- **S1.16** [Cat. 2 — `/api/endpoints` and `/openapi.json` are ops/introspection routes, no end-user UI] Direct-call proof: both return 401 with no token under the shipped config (today they return 200 with full cross-tenant data); confirm a valid admin token still gets 200.
- **S1.17** [Cat. 2 — dead-code removal + test-baseline change, no end-user behavior] Structural proof: `grep` confirms zero remaining `authEnabled(cfg)` conditional gates in `SchemaRoutes.java`; `AuthEnabledAntiPatternTest` passes with no `SchemaRoutes.java` entry in `BASELINE` at all; direct-call re-confirmation that `POST /schema`/`DELETE /schema/{name}` still 403 a non-owner (unchanged behavior — the real check was always the unconditional one).

### Post-acceptance external review of S1 (round 1) — findings and remediation

An external reviewer examined commits `564ec59`..`f1c61b2` (S1.1–S1.6). S1.1–S1.5 were confirmed
correct as implemented. S1.6 was found to have a genuine gap, plus one route outside the S1.1–S1.6
diff (app creation) was found unguarded, plus one latent guard-logic trap and one coverage hole in the
still-unscheduled remainder of S1. Two 🔴 Blockers, one 🟠 High, one 🟡 Medium, all resolved as follows
(all four independently re-verified against source before any fix was written, not taken at face value):

- 🔴 **Blocker B1 — `POST /appbana-studio/{tenantId}/apps` had zero `TenantAccessGuard` call.** Root
  cause: the S0.2 census marked this row's "T/A check?" column **"N/A (creation)"**, reasoning there's
  no existing app yet to own — true, but beside the point, since there IS a target tenant (the path
  segment) and a caller with a tenant of their own, and nothing compared the two. Any authenticated
  session for tenant A could create an app inside tenant B's path, with `author` correctly set to the
  real attacker (anti-spoof working as designed) — meaning the planted app would later qualify as a
  genuine `owner` membership row once S2.4 backfills memberships, a durable foothold rather than mere
  switcher-list pollution. **Fixed:** added `TenantAccessGuard.requireOwnTenant(req, cfg, tenantId,
  null)` immediately after the route's existing blank-tenantId check, before any body is read or
  `AppManager.createApp` runs (`pathAppId=null` — no app exists yet for the S2.6 membership exception
  to apply to). Census cell corrected in `TENANT_ISOLATION_SECURITY_PLAN.md` with an explanatory
  footnote so a future "N/A (creation)" cell is read narrowly, not as a blanket exemption.
- 🔴 **Blocker B2 — S1.6's admin-gate on `/api/templates` writes never runs under the shipped
  config.** The gate was wrapped in `if (AuthService.authEnabled(cfg))`, which evaluates `false` under
  the actual shipped `config.json` (`adminToken: null`, `readToken: null`) — meaning the original
  "live verification" only exercised the gate because a throwaway `adminToken` was temporarily set for
  that session, then reverted. That proved the gate works under a config the product doesn't ship, not
  under the config it does. **Fixed:** removed the `authEnabled(cfg)` wrapper from all 3 template-write
  routes — `hasAdmin` now runs unconditionally. Documented, deliberate consequence: with the shipped
  `adminToken: null`, `hasAdmin` always returns `false`, so these 3 routes are now closed to *everyone*
  by default until an operator configures a real admin token — acceptable per S0.2's census (zero
  known callers repo-wide for these routes).
- 🟠 **High H1 — `GET /schema` and `GET /schema/{name}` are unguarded, and no S1.7–S1.14 task
  covered fixing them.** `GET /schema` returns schema names with no tenant filtering; `GET
  /schema/{name}` has only the same inert `authEnabled`-gated legacy token check as S1.4 found on its
  `DELETE` sibling, no ownership check. Confirmed via direct read of `SchemaRoutes.java` and a
  cross-check against the full S1 task list — genuinely no task closes this gap. **Registered as new
  task S1.15** (added above), not fixed in this round (code fix deferred; only the gap itself and its
  task registration are resolved now). Also noted: S1.11's planned test spec ("nor read/delete tenant
  A's schemas") already assumes this fix exists — flagged inline on both S1.11 and S1.15 above so the
  dependency isn't missed when S1.11 is written.
- 🟡 **Medium M1 — `TenantAccessGuard`'s tenant-match check fails open on two null tenants.**
  `Objects.equals(session.tenantId(), pathTenantId)` returns `true` when both are `null`, which would
  wrongly *allow* rather than deny. Not reachable via any production path today (every real session has
  a non-null `tenantId` since S1.1), but a null-tenant session is constructible, and S3 reuses this same
  comparison shape for `scopedAppId` (which is `null` by default) — worth closing before that reuse
  happens. **Fixed:** added an explicit `session.tenantId() == null → deny(403)` check before the
  `Objects.equals` comparison.
- **Meta-observation, addressed as a new ratchet test.** The reviewer noted all four findings trace to
  one root cause — the `if (authEnabled(cfg))` conditional-gate anti-pattern — and suggested a test
  that fails on any new occurrence outside the ones S3.4 already plans to delete. A full repo-wide grep
  before writing that test found the anti-pattern far more widespread than previously scoped: **21 real
  conditional-gate occurrences in `GenericEntityRoutes.java` alone** (the busiest route file in the
  system — the generic per-entity CRUD API), on top of the previously-known ~6 in `SchemaRoutes.java`
  (32 raw `authEnabled(` matches across 4 files; 3 of those are non-gating value-passing calls in
  `GenericEntityRoutes.java`, correctly excluded). This is new scope information for S3.4, not yet
  reflected in that task's own estimate — flagged here rather than silently absorbed into a baseline.
  **New `AuthEnabledAntiPatternTest`** (`com.appbana.server`, mirrors `RouteCensusTest`'s file-scanning
  style): walks `src/main/java`, regex-matches only actual `if (...authEnabled(...))` conditional gates
  (not value-passing usages), fails if a new file gains the anti-pattern or a known baseline file's count
  *increases* (`GenericEntityRoutes.java`: 21, `SchemaRoutes.java`: 6) — a decrease (i.e. progress) is
  fine and doesn't fail. Permits today's known baseline while blocking regression, pending S3.4's full
  removal.
- **Fixes verified:** new `AppRoutesTenantIsolationTest` (8 tests: cross-tenant creation rejected/403,
  same-tenant creation still works/201, unauthenticated/401, all 3 template writes rejected with no
  token/401, template reads still public/200, plus an explicit assertion that `authEnabled(cfg)` is
  `false` under the shipped config — guards the fixture's own assumption), 1 new
  `TenantAccessGuardTest` case for M1, 1 new `AuthEnabledAntiPatternTest`. Full `app-bana-service` suite:
  **367/367, `BUILD SUCCESS`** (357 prior baseline + 10 new). Fat jar rebuilt, dev backend relaunched,
  confirmed healthy.
- **Live re-verification performed exactly as the reviewer required — "with `config.json` as
  shipped," no throwaway token this time**, directly correcting B2's own methodological gap: logged in
  as the real standing fixture users (User A `t-bf0c8f57`, User B `t-fc8d39e7`) via `POST
  /api/auth/login` against the freshly rebuilt, running backend. (1) User B → `POST
  /appbana-studio/t-bf0c8f57/apps` (User A's tenant) → **403**; (2) User A → `POST
  /appbana-studio/t-bf0c8f57/apps` (own tenant) → **201**, then deleted as cleanup by the same owner;
  (3) no session at all → **401**; (4) `POST /api/templates` with zero token, real unmodified
  `config.json` — **401**; (5) same for `PUT`/`DELETE /api/templates/{id}` — **401** both; (6) `GET
  /api/templates` with zero token — **200** (reads still public, non-regression). No `config.json`
  edit of any kind was needed for this round's verification — the point of the B2 fix.
- **Process note:** every one of the reviewer's four claims was independently re-confirmed against
  source (`AppRoutes.java`, `TenantAccessGuard.java`, the full S1 task list) before any fix was written,
  continuing this project's established norm from the S0 review round.

### Post-acceptance external review of S1 (round 2) — plan-doc accuracy + a new S1.16 finding

A second external review pass re-examined the round-1 remediation (commits `160e7a3`, `ffe9f64`,
`09a3af2`, `815b202`) plus the plan/tasks docs those commits touched. All of its substantive claims were
independently re-verified against source before any doc edit was made, continuing this project's
established norm.

- ✅ **Confirmed as fixed and durable:** B1 (`POST /appbana-studio/{tenantId}/apps` tenant guard), B2
  (template-write gate now unconditional), M1 (null-tenant fail-open closed), and the
  `AuthEnabledAntiPatternTest` ratchet (fires on any *increase*, permits decreases). Re-read
  `AppRoutes.java`, `TenantAccessGuard.java`, and the test itself directly rather than trusting the
  round-1 writeup's own claims a second time.
- 🟡 **S3.4's "16+" wording was stale, but not for the reason first assumed.** The review's own math
  ("~70% understatement", implying 16→27) treats `GenericEntityRoutes.java`'s 21 gates and
  `SchemaRoutes.java`'s 6 as one combined pool. Re-checked S3.4's own **Where**/file-scope column in
  both docs: it scopes the task to `GenericEntityRoutes.java` only. The ratchet-verified, correct number
  for S3.4's own text is **21**, not 27 — corrected in both docs, along with the 3 further "16+"
  mentions that describe the current state rather than quoting the round-1 finding verbatim (the
  round-1 B3 quote and its finding-table row are left untouched — a historical record of the *old*,
  wrong task title being critiqued at the time, not a live claim).
  Re-derived S3.4's estimate proportionally rather than guessing: the original conception was ~16
  (fuzzy) + 11 (exact) ≈ 27 reference items for 150 min ≈ 5.5 min/item; the corrected exact scope is
  21 + 11 = 32 items ⇒ 150 × 32⁄27 ≈ 177.7 min, rounded to this doc's existing 15-minute-increment
  convention → **180 min**. Propagated through the S3 subtotal (~10.5 hr → ~11.25 hr) and the grand
  Total scope (see below).
- 🟢 **Real, separate gap surfaced by chasing the "27" arithmetic — registered as new task S1.16.**
  The reviewer's instinct that something was undercounted was right, just not about S3.4. Reading all 6
  `SchemaRoutes.java` `authEnabled(cfg)` gate sites individually (not assuming uniform treatment) found:
  `GET /schema` and `GET /schema/{name}` are S1.15's exact target; `POST /schema` and `DELETE
  /schema/{name}` each already have a separate, unconditional `isAppOwnerOrSystem` ownership check
  beneath the optional gate, so they're vestigial-but-harmless; but **`GET /api/endpoints` and `GET
  /openapi.json` have no fallback check of any kind.** Neither is covered by S1.9 (only changes which
  credential tier the still-optional gate checks, doesn't remove its conditionality) or S1.15 (scoped
  only to `/schema`, `/schema/{name}`). Under the shipped config both remain fully open,
  unauthenticated, and cross-tenant after every currently-planned S1/S3 task lands — `/openapi.json` in
  particular discloses complete field-level schema definitions for every tenant's every app. **Not fixed
  in this round** (code fix deferred, mirroring how H1 became S1.15 in round 1) — **registered as new
  task S1.16** (added above, `SchemaRoutes.java`, 30 min), with its own exit-criteria bullet and Cat. 2
  verification-approach note.
- **Status/ordering note:** the reviewer flagged that S1.11's dependency on S1.15 landing first needs to
  be explicit so the tasks aren't executed out of order. Checked: this dependency is already recorded,
  both on the S1.11 task row itself and in its Cat. 2 verification-approach bullet above — no further
  edit needed.
- **Environment note (unresolved, non-blocking):** the reviewer mentioned a residual
  `s1probe-…@example.com` fixture user left in `data/users.json`. Checked both the main workspace's
  `data/users.json` (no match for `probe`) and the reviewer's own named worktree
  (`tenant-isolation-security-review`) — which has **no `data/` directory at all**, so the file the
  reviewer is describing isn't reachable from here to inspect. Noted transparently rather than guessed
  at; not blocking, since no test or doc depends on that row's absence.
- **Meta-observation (reusable lesson):** two independent verification disciplines carried this round
  and are worth keeping permanent: (1) when a reviewer's arithmetic implies combining two
  separately-scoped items (here, two files' gate counts), check each item's own explicit scope column
  before accepting the combined figure — the combination can still point at a real, separate gap even
  when the combined number itself is wrong. (2) A conditional gate is not automatically "still
  vulnerable" just because the ratchet counts it — some are redundant leftovers sitting behind a separate
  unconditional check (`POST`/`DELETE /schema/{name}`), so each site needs individual reading, never
  uniform treatment by raw count alone.

**Total scope after this round:** ~41.75 hr (S3.4 +30 min, S1.16 +30 min — see the plan doc's Total
scope paragraph for the full running history). No source code changed this round — plan-text and
task-registration only, so the existing 367/367 suite is unaffected and was not re-run.

### Post-acceptance external review of S1 (round 3) — S1.16 severity correction + new S1.17

A third external review pass probed S1.16's two routes live against the shipped config, and re-checked
my round-2 arithmetic. All claims independently verified against source before any doc edit.

- ✅ **Round 2 closed.** The reviewer confirmed their own round-2 "16+→27" arithmetic was the error
  (pooling two separately-scoped files instead of checking S3.4's own Where column), and that the
  ratchet-sourced correction, proportional re-derivation, and leaving the round-1 B3 historical quote
  untouched were all the right calls.
- 🟠 **`GET /api/endpoints` graded more severely than S1.16 originally described — corrected.**
  Verified directly against `SchemaRoutes.java`: the handler calls `SchemaManager.listSchemaNames()`
  with no tenant filter and, for every schema, emits `POST /api/{key}`, `GET /api/{key}`, `GET
  /api/{key}/{id}`, `PUT /api/{key}/{id}`, `DELETE /api/{key}/{id}` using the **full tenant-qualified
  key**. This is the enumeration primitive for the anonymous entity data plane the round-1 review had
  to reconstruct by querying Postgres directly — this route hands the whole list out, pre-formatted,
  to anyone. S1.16's text is corrected to grade this route as the higher-severity half of the task,
  ahead of S1.15's `/schema` reads.
- 🟡 **`GET /openapi.json`'s justification was overclaimed — corrected, scope unchanged.** Verified
  directly against `OpenApiGenerator.java`: `paths`/`components.schemas` are keyed by `schema.getName()`
  — the bare entity name, not the tenant-qualified key — so the output is a union of field-level shapes
  across every tenant with **no tenant attribution**, and a name collision across tenants silently
  last-write-wins rather than enumerating every tenant's version. "Complete field-level schema
  definitions for every tenant" (S1.16's original text) overstated this. Kept in scope for two
  correctly-identified reasons instead: it confirms which entity names exist platform-wide, and every
  anonymous call loads *all* schemas from the DB and serializes the full result (~340 KB at today's
  ~455-schema count) — an unauthenticated amplification vector independent of the disclosure. Estimate
  unchanged at 30 min for both routes, per the reviewer's own assessment.
- 🟢 **New task S1.17 registered** — once S1.15 + S1.16 land, `SchemaRoutes.java`'s remaining two
  `authEnabled(cfg)` gates (`POST /schema`, `DELETE /schema/{name}`) are pure dead weight, each already
  behind a separate unconditional `isAppOwnerOrSystem` check (confirmed by direct read, round 2).
  Deleting both wrappers takes this file's ratchet count to zero; **removing its `BASELINE` entry
  entirely** (rather than setting it to `0`) was confirmed against `AuthEnabledAntiPatternTest`'s own
  logic — an absent key fails the "new file" branch on any future occurrence, which is a strictly
  stronger guard than a `0` entry. This also makes the test's own docstring claim ("S3.4 is the task
  that removes this pattern repo-wide") true for `SchemaRoutes.java` specifically, ahead of S3.4.
- **Priority/ordering note:** noted directly on S1.16's row rather than renumbering the task IDs —
  `/api/endpoints`'s severity means S1.16 should land before or alongside S1.15, despite the higher
  number. Task IDs in this doc are stable identifiers, not an execution-order promise (see S3.7's
  existing precedent for the same distinction).
- **Environment note — resolved.** The reviewer located and removed their own worktree's leftover
  `app-bana-service/data/users.json` fixture-probe row (untracked, `.gitignore:29 **/data/`, created
  relative to wherever the jar was launched from). Checked this workspace's own copy of the
  same-shaped file at the same relative path — it exists (real, actively-used dev/e2e fixture accounts,
  not a throwaway), but a direct search for `probe` inside it found no match. The probe-user leftover
  was specific to the reviewer's own worktree/launch directory, not shared with this workspace's copy —
  nothing to clean up here.
- **Meta-observation, saved to memory:** the reviewer explicitly flagged their own round-2 number as
  wrong and asked to be held to the same verify-before-adopt standard as this project already applies
  to itself — recorded as a durable process note (see repo memory) rather than only in this doc.

**Total scope after this round:** ~42.25 hr across 49 tasks (S1.17 +30 min). No source code changed
this round — S1.16's text correction, S1.17's registration, and this section are documentation only.

### Post-acceptance external review of S1 (round 4) — estimate reconciliation across all phases

A fourth external review pass confirmed round 3's findings, caught one remaining imprecision in this
doc's own S1.17 text, and then did something no prior round had: mechanically summed every task row's
own estimate against the plan doc's phase-level summary table. All of it independently re-verified
against source before any doc edit, including re-deriving the sums by hand rather than trusting either
the reviewer's figures or this doc's own prior "~13.4 hr" note at face value.

- ✅ **Confirmed, no changes needed:** the S1.16 severity split, the `/openapi.json` correction, S1.17's
  registration, the priority/ordering note, and the environment resolution (the reviewer's own worktree
  fixture-probe leftover, removed on their side).
- 🟡 **S1.17's "strictly stronger" claim was itself an overclaim — corrected.** Re-read
  `AuthEnabledAntiPatternTest` line by line: a `0`-valued `BASELINE` entry also fails the test on any
  future occurrence, via the existing `count > max` branch — not just an absent key's "new file" branch.
  Both stop the regression equally; removing the entry is the clearer, more specific failure message and
  the more honest representation (the file genuinely has zero occurrences left), not a *stronger*
  guarantee. S1.17's row text corrected above; round 3's own text a few paragraphs up is left as-is —
  an accurate record of what was believed at the time, not rewritten.
- 🟠 **Estimate drift confirmed real, and larger than previously scoped — independently re-derived, not
  copied from the reviewer's figures.** Summed all 50 task rows (49 sequentially-numbered plus `S0.1b`,
  a letter-suffixed id this verification's own first extraction attempt also missed, before re-checking
  for lettered ids — see below) against the plan doc's S0–S5 summary table:

  | Phase | Task-row sum (this doc) | Plan doc, pre-round-4 | Delta |
  |---|---|---|---|
  | S0 | ~8.67 hr (6 tasks) | ~5 hr | +3.67 hr |
  | S1 | ~13.42 hr (17 tasks) | ~7.75 hr | +5.67 hr |
  | S2 | ~11.25 hr (10 tasks) | ~10 hr | +1.25 hr |
  | S3 | ~11.25 hr (7 tasks) | ~11.25 hr | 0 — the only phase a review had already forced a manual re-derivation of (round 2, S3.4) |
  | S4 | ~5.5 hr (7 tasks) | ~4.5 hr | +1.00 hr |
  | S5 | ~4.0 hr (3 tasks) | ~3 hr | +1.00 hr |
  | **Total** | **~54.08 hr (50 tasks)** | **~42.25 hr (49 tasks)** | **+11.83 hr (+28%)** |

  S1+S2 combined — the plan's one hard operational commitment ("ship as one deployable unit," review
  round 5 R5-2) — was stated at ~17.75 hr and actually totals ~24.67 hr, a 39% understatement on the
  exact figure someone would use to size a deployment window.
- 🟢 **Bonus finding, self-caught, not part of the reviewer's ask:** the task count was 50, not 49, even
  before this round. `S0.1b` is a letter-suffixed task id sitting between S0.1 and S0.2 (45 min,
  already ✅, done as part of S0's original acceptance) that a plain `S\d+\.\d+` extraction silently
  skips — this verification's own first pass made exactly that mistake, and only caught it on a
  follow-up grep for lettered suffixes. It is the only letter-suffixed id anywhere in the tracker. Left
  as a live illustration of the reviewer's own meta-point below, not just a footnote.
- 🟢 **Plan doc corrected:** the S0–S5 summary table and the Total scope paragraph now match the sums
  above (S0 ~5→~8.67 hr, S1 ~7.75→~13.42 hr, S2 ~10→~11.25 hr, S4 ~4.5→~5.5 hr, S5 ~3→~4.0 hr; S3
  unchanged). This is a correction of a pre-existing measurement gap that accumulated silently across
  six plan-drafting rounds plus three implementation-review rounds, not new work discovered this round.
- 🟢 **New task S0.5 registered** — an automated check that sums this tracker's own task rows per phase
  and asserts the total against the plan doc's summary table (and the grand Total scope figure), the
  same "derive it, don't hand-maintain it" move S0.2/S0.3 already made for the route census. 90 min.
  Placed in S0 (not S1) because it's a tracker-wide tooling concern, not S1-specific.
- **Status:** round 3 is fully closed per the reviewer; this reconciliation is planning-hygiene, not a
  gate on S1.7 — proceeding to S1.7 next, per the reviewer's own framing, with S0.5 queued as ⬜.
- **Meta-observation, saved to memory:** the reviewer's own point — that four review rounds (six,
  counting the plan-drafting rounds) each correctly updated the one number they were asked about while
  the aggregate silently drifted 28% because no round owned it — is the same shape as S0.2's "no known
  caller" census cells and earlier rounds' "locally verified, globally unchecked" findings. Registering
  S0.5 rather than hand-patching the numbers is a direct response to that pattern, not just this one
  instance of it. The `S0.1b` miss above is a second, smaller instance of the identical shape one level
  down (in the verification tooling itself, not the tracker). Also noted for the next review pass, per
  the reviewer's own suggestion: after S1.7–S1.9 land, favor an absence-census (enumerate every route,
  ask which still lack the new guard) over further prose review.

**Total scope after this round:** ~54.08 hr across 50 tasks (S0.5 +90 min; every other phase's figure
corrected to match its own task rows, which already existed — not new scope). No source code changed
this round — doc corrections, one new task registration, and this section are documentation only.

### Post-acceptance external review of S1 (round 5, closing) — S0.5 scope correction + review pause

A fifth review pass confirmed round 4's fixes, then caught that round 4's own edit was internally
inconsistent: it added task S0.5 as a new S0 row in the same commit that restated the grand total, but
the restated total (~54.08 hr / 50 tasks, in the section directly above) summed only S0's original six
rows — S0.5's own 90 min was registered but never folded into either S0's phase total or the grand
total. Notably, a parallel hand-count of the same table, made independently by the reviewer, landed on
a *different* wrong number too (30 min low on S0, from resolving `S0.0`'s "30–90 min" range at its lower
bound instead of the upper bound this doc has used throughout) — two independent manual passes over the
same six-then-seven rows, two different errors, neither self-caught by the person who made it. Taken
together, that's a better argument for S0.5 than either table on its own: hand-reconciliation of this
table is demonstrably unreliable even done carefully, twice, by two different people.

- 🟢 **S0.5's own estimate folded into S0's total and the grand total**, corrected in place (this is
  live bookkeeping, not narrative prose — round 4's own already-closed narrative a few paragraphs up is
  left untouched, same "don't rewrite history" precedent as every prior round). S0 becomes **~10.17 hr
  across 7 tasks** (610 min: the original 520 min for S0.0–S0.4/S0.1b at S0.0's upper bound, plus
  S0.5's 90 min). Grand total becomes **~55.92 hr across 51 tasks** (3335 min). Corrected: this doc's
  line-9 headline and the plan doc's S0 summary-table row + Total scope figure.
- 🟢 **Range convention now stated explicitly in S0.5's own task text**: take the upper bound of any
  ranged estimate (`S0.0`'s "30–90 min" is the only one anywhere in the tracker) — the convention
  already used by hand for every reconciliation so far, now written down so the automated check and any
  future manual cross-check can't land ±60 min apart depending on which end someone picks.
- 🟢 **S0.5 marked explicitly non-gating.** S0.5 sitting at ⬜ inside Sub-phase S0 — whose completion
  was the prior gate for starting S1, and S1 is six tasks in (S1.1–S1.6 ✅) — was ambiguous for a reader
  with no other context: did S1 start before S0 was done? Added an explicit note on S0.5's row and its
  exit-criteria bullet: S0's substantive exit criteria (S0.0–S0.4) were independently reviewer-accepted
  before S1 began (see "Post-acceptance external review of S0" above) and stand as met; S0.5 is scope
  added after that acceptance, exactly like S1.15–S1.17 are scope added to S1 after *its* acceptance —
  neither retroactively reopens its own phase's gate. Same frozen-acceptance pattern already established
  for past narrative sections, applied here to a still-open task instead.
- **Status: round 4 closed. Reviewer is pausing review here** — nothing outstanding, nothing blocking
  S1.7. Explicit assessment taken at face value: the last two rounds (prose/arithmetic review of docs
  with no new code) reached diminishing returns; this round's own findings (a 90-minute row
  misalignment, a range/units question) are not where the remaining risk is.
- **Protocol for resuming at S1.7–S1.9**, per the reviewer's own three-part method that found B1/B2 in
  round 1 — recorded here and in session memory so it carries forward to that work:
  1. **Absence-census first** — enumerate every route in each touched file, ask which ones *lack* the
     new guard, before checking whether existing wiring is correct. This found B1 (a completely
     unguarded creation route); checking only already-believed-guarded routes would not have.
  2. **Verify against `config.json` as shipped** (`adminToken: null`), never a temporarily-edited
     config. This found B2 (a guard wrapped in `authEnabled(cfg)`, dead by default under the real
     shipped config) and is now the exact assumption `AppRoutesTenantIsolationTest`'s fixture-guard
     test pins.
  3. **Break each new guard on purpose before trusting it** — a deliberate negative test, not only a
     positive pass.
  Extra attention flagged for **S1.7 (file upload) and S1.8 (saved views)**: both take their tenant
  identifier from the request body/query rather than a path param — exactly the shape S0.2's own census
  predicate ("any client-controlled tenant/app identifier, not just path params") was written to catch.
  Worth deliberately re-checking *where* the tenant value is read from before checking whether it's
  compared correctly.
- **Meta-observation, saved to memory**: every finding across all rounds — six plan-drafting plus five
  implementation-review — came from checking one artifact against something outside itself: plan vs.
  code, guard vs. caller, login vs. data, census vs. router, ratchet vs. a deliberate break, summary
  table vs. its own rows, and (twice now) one hand-count vs. another. Nothing was found by re-reading a
  single artifact more carefully in isolation. This is also the stated reason review is pausing here:
  there's no further external referent for these two docs to be checked against until S1.7's code exists
  beside them.
- **Environment**: reviewer's worktree (`agents/tenant-isolation-security-review`) confirmed clean,
  backend stopped, probe artifacts removed; `appbana-postgres` left running (shared container, correctly
  left up, not this reviewer's to stop).

**Total scope after this round:** ~55.92 hr across 51 tasks (S0.5's own 90 min folded into S0's and the
grand total — round 4 had registered the task but not its estimate). No source code changed this round
— doc corrections and this section are documentation only. **Review paused here per the reviewer's own
recommendation; next work is S1.7, not further doc review.**

### Post-acceptance external review of S1.7 (round 6) — accepted; S1.18 registered for a download-auth gap

An external reviewer examined commit `7bedbb5` (S1.7) against source directly, including deliberately
neutering the new guard (`Result.allow()`) and re-running the tests to confirm they fail on the actual
security property, not just a superficial assertion. **Verdict: S1.7 accepted, no changes requested.**
Six items independently re-verified and confirmed correct: the absence-census on `FileRoutes.java`
(exactly two routes, no gap), guard placement **before** `storage.save()` (a denied upload writes no
blob and no row — the harder-to-get-right ordering), body-supplied `tenantId`/`appId` compared against
the resolved identity's own tenant (not the storage triple), the tests genuinely fail when the guard is
neutered, `uploadToOwnTenantSucceedsAndRecordsResolvedUploader`'s DB-state assertion (pins the
`uploadedBy` fix — a status-only assertion would have passed with `null` still being written), and the
frontend `Authorization` header fix caught in the same pass rather than shipped broken.

- 🟡 **Finding — the download-link 401 gap (already flagged in S1.7's own writeup) is promoted from a
  footnote to its own task.** Independently re-confirmed: `/api/files/` appears nowhere in
  `SessionMiddleware`'s excluded paths, and the download route's 4 path segments match neither
  `ENTITY_API_PATTERN` (1–2 segments) nor the `/apps/` rule, so a session is required — while both
  `FileUploadField.tsx`'s preview link and `StudioTableLive.tsx`'s download column use a plain
  `<a href target="_blank">`, which cannot carry one. Rationale for its own task rather than a
  mention: S1.7 makes upload work; download is the other half of the same feature, and S1.7's own live
  verification (proving the upload path + DB row) could not have caught this, because it never
  round-tripped through a download. **Registered as new task S1.18** (below) — decision + fix deferred,
  not made unilaterally. `FileRoutes.java`'s Javadoc corrected in this round (it claimed the download
  route "remains anonymous end-to-end," which is what made the gap hard to see; now documents the
  actual current behavior and points at S1.18).
- 🟢 **Forward note for S2.6, not actionable now.** `TenantAccessGuard.requireOwnTenant`'s membership
  branch is called with the **body-supplied** `appId` as `pathAppId`. Harmless today (the own-tenant
  branch decides every real request; the membership branch is inert until S2.6, and S1.2's own comment
  already flags that app-ownership isn't verified here). Once S2.6 wires `AppMembershipService.isMember`
  in, this is the one call site where **both** halves of `(app's tenant, appId)` are attacker-controlled
  — `isMember` must resolve the app's real tenant from the app record itself, never from the tenant the
  request body asserts alongside the appId. Filed against S2.6's own task row below so it isn't lost
  between now and then.
- 🟢 **`app-bana-service/uploads/` was not gitignored.** The happy-path test cleans up its own blob, so a
  green run leaves nothing but empty (untracked-by-git) directories — but a *failing* upload test can
  leave real blobs behind. Added `app-bana-service/uploads/` to `.gitignore` this round.

**New task S1.18 registered** (file-download authentication gap): `GET
/api/files/{tenantId}/{appId}/{fileId}` requires a session today, but no real user can ever reach it,
because both places that render its URL use a plain anchor tag that cannot carry the required
`Authorization` header. Needs a decision between (a) whitelisting the route in `SessionMiddleware` to
restore the anonymous access `FileRoutes.java`'s Javadoc originally documented, or (b) switching both
render sites to an authenticated `fetch` + `URL.createObjectURL` download — different security
tradeoffs, not decided in this round. Est. 60 min, added to S1's table above.

**Edits made:** `FileRoutes.java` (Javadoc corrected — no longer claims anonymous end-to-end download).
`.gitignore` (`app-bana-service/uploads/` added). `TENANT_ISOLATION_SECURITY_PLAN.md` (S1 summary row
~13.42→~14.42 hr; Total scope headline + new round-6-of-S1-implementation-review clause,
~55.92→~56.92 hr). `TENANT_ISOLATION_IMPLEMENTATION_TASKS.md` (this doc: headline ~55.92hr/51→
~56.92hr/52 tasks; new S1.18 row; new S1 exit-criteria bullet; new S1.18 UI-verification-script bullet;
this section).

No test code changed this round — S1.7's own 370/370 backend + 276/276 runtime baseline is unaffected
(re-confirmed via the reviewer's neutered-guard re-run, not re-run again independently here).

Still to do: commit (`docs`/`fix`: gitignore + Javadoc), push, deliver chat writeup, per established
convention. Then: **on to S1.8**, same three-part protocol (absence-census first, negative tests before
positive, break each new guard on purpose before trusting a pass) — per the reviewer's own explicit
framing, with two things to watch going in: the `DELETE_SQL` fix must be proven by a test that attempts
a *foreign* delete and then asserts **the row still exists** (a 403 with the row already gone would pass
a status-only assertion), and `ownerUserId` must stop being read from the client payload the same way
`uploadedBy` just stopped being.

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
| S3.4 | Wire `EntityAccessGuard` into **every** `GenericEntityRoutes` route per the S0.2 census — the 21 existing `authEnabled` blocks (ratchet-verified, `AuthEnabledAntiPatternTest` baseline — S1 external review round 2) *and* the 11 routes (studio-scoped + env-scoped families) with no such block today. | `GenericEntityRoutes.java` | 180 min | ⬜ |
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
| S5.3 | Document the enforced model in `.github/copilot-instructions.md` (mirrors how Maker-Checker was documented). **Fold in while here:** correct the file's 3 stale "Java 21" mentions (Section 13's "Development Conventions" and others) to "Java 25" — flagged as a 🟢 nit in the post-S0 external review; deliberately not fixed standalone so it doesn't get lost as a driveby edit outside its own commit. | `.github/copilot-instructions.md` | 30 min | ⬜ |

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
6. **🟢 (New, S0 sign-off nit) `RouteCensusTest` is coupled by path to `TENANT_ISOLATION_SECURITY_PLAN.md`** — correct while the plan is active, but the census will outlive the plan; when this initiative closes and the plan is archived/renamed, the test breaks (arguably correct behavior — loud beats silent — but a deliberate S5 decision, not a surprise during a later docs tidy-up). Candidate: move the census to a permanent home (e.g. its own doc, or a generated file) and have the plan link to it instead. Related and low-risk: the parser accepts any `| METHOD | \`path\` |` row inside the census section, so a second method/path table added under that same heading in S1–S3 would be misread as census entries — just don't add one there. Not actioned now; revisit at S5.

---

*Created 2026-08-01, tracking execution of `TENANT_ISOLATION_SECURITY_PLAN.md` (92b20ba). Update the Status column and check off exit criteria as each task lands; do not batch multiple task IDs into one commit.*
