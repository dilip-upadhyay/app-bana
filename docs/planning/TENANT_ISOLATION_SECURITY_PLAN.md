# Tenant & App Isolation Security Plan

**Status:** 📝 DRAFT — 🔴 **Review round 2 complete (2026-07-31): S0/S1/S2/S4 accepted as written; S3 needs one more task (Runtime principal migration, S3.7) before it can be estimated or started — see Review round 2 below.**
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
6. [Review round 2 — blockers and additional findings](#review-round-2--blockers-and-additional-findings)
7. [Review round 3 — blockers and additional findings](#review-round-3--blockers-and-additional-findings)
8. [Review round 4 — blockers and additional findings](#review-round-4--blockers-and-additional-findings)
9. [Review round 5 — blockers and additional findings](#review-round-5--blockers-and-additional-findings)
10. [Review round 6 — blockers and additional findings](#review-round-6--blockers-and-additional-findings)
11. [Target model](#target-model)
12. [Data model additions](#data-model-additions)
13. [Sub-phase S0 — Unify identity resolution + route census](#sub-phase-s0--unify-identity-resolution--route-census)
14. [Sub-phase S1 — Tenant boundary on app management](#sub-phase-s1--tenant-boundary-on-app-management)
15. [Sub-phase S2 — Per-app membership model](#sub-phase-s2--per-app-membership-model)
16. [Sub-phase S3 — Entity data API enforcement](#sub-phase-s3--entity-data-api-enforcement)
17. [Sub-phase S4 — Credential hygiene](#sub-phase-s4--credential-hygiene)
18. [Sub-phase S5 — Capstone tests + ai-builder trust chain](#sub-phase-s5--capstone-tests--ai-builder-trust-chain)
19. [Cross-cutting concerns](#cross-cutting-concerns)
20. [File-level change map](#file-level-change-map)
21. [Open decisions still needed from product](#open-decisions-still-needed-from-product)

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

**Review round 2 (2026-07-31) accepted S0/S1/S2/S4 as written and added one blocking task to S3.**
Tracing actual client call graphs (not just route shapes) found the shipped `app-bana-runtime` doesn't
use the scoped-session login this plan's target model assumed — it authenticates via the platform login
and loads its app metadata via a Studio management route. Without S3.7 (new), finishing S3 exactly as
originally scoped would 403 every real deployed app. See
[Review round 2](#review-round-2--blockers-and-additional-findings) for full detail.

**Review round 3 (2026-07-31) found round 2's own fix for S3.7 unusable, and replaced it.** The
scoped-login migration assumed every deployed app has a `User` entity with an `email`/`password` column
to authenticate against. A direct query against live `appbana_schemas` found **zero** real apps with
one — only 3 obviously-synthetic test fixtures. S3.7 no longer touches the Runtime's login at all: an
end-user is now a **role** (`end-user`) on the same `appbana_app_members` row S2 already builds, so the
Runtime keeps using the ordinary platform login unchanged. This also *removes* the `AppRoutes` carve-out
S3.7 previously needed. See [Review round 3](#review-round-3--blockers-and-additional-findings) for full
detail.

**Review round 4 (2026-07-31) found round 3's own fix incomplete — the two guards it lives beside
disagree, and they disagree specifically for the principal S3.7 exists to serve.** S1.2's tenant guard
403s on any tenant mismatch with no exception; S2.6 was specified as a *second* check layered after it,
not a modification to it. Since this system provisions a brand-new tenant per user registration
(confirmed live: 5 apps, 5 tenants, one user each), every real `end-user`/collaborator membership grant
is cross-tenant by construction — so S1.2 rejects the exact principal S2/S3.7 were built to admit,
before S2.6 is ever reached. See [Review round 4](#review-round-4--blockers-and-additional-findings) for
full detail; S1.2 and S2.6 are revised to close this, and S2's motivating exit criterion is restated
against a scenario the product can actually construct.

**Review round 5 (2026-08-01) found the same blind spot in two more principals, both of which have no
meaningful tenant to compare.** The break-glass admin/service token this plan already promises
(Non-goals, finding #3, cross-cutting concerns) has no admit branch anywhere in `TenantAccessGuard` —
confirmed `extractUserId` resolves it to a bare `"admin"`/`X-User-Id` string with no `SessionData` at
all, so there is nothing for a tenant-comparison guard to even read. Separately, S1 completes (before S2
exists) in a state where its own exit criteria certify every deployed app's real end-user — by
construction a foreign-tenant session, per round 4 — as correctly 403'd; if S1 ships alone, every
deployed app breaks for everyone but its creator until S2.6 lands. No blocker: S1.2 gains an explicit
admin-token branch, and S1+S2 are declared a single deployable unit. See
[Review round 5](#review-round-5--blockers-and-additional-findings) for the full principal × guard walk
and two smaller findings about cross-tenant app discovery.

**Review round 6 (2026-08-01) found no blocker and no new principal — two consistency gaps in sections
the plan already had, and a performance deferral worth writing down.** `readToken`, the separate
read-only credential, gets no admit branch in either new guard, so it would work in `SchemaRoutes` and
403 everywhere else; retired instead, uniformly. S3's completion is a one-time access reset for every
deployed app's end-users (no backfill is possible, by design); now stated explicitly in Rollout order so
it isn't mistaken for a regression. See
[Review round 6](#review-round-6--blockers-and-additional-findings) for detail. **The reviewer's own
verdict: the plan is done — execute it.**

| # | Sub-phase | Deliverable | Est. |
|---|---|---|---|
| S0 | Unify identity resolution + route census | One `resolveIdentity()` every gate uses; machine-generated census of every registered route, now including **known callers** and **what data must exist for it to succeed** columns | ~10.17 hr |
| S1 | Tenant boundary on app management | `AppRoutes` + `SchemaRoutes`, **every route per the S0 census** (not just list/get/update/delete) can no longer be pointed at another tenant's data, **except through an explicit per-app membership grant (review round 4, R4-1) or a valid break-glass admin/service token (review round 5, R5-1)** | ~14.75 hr |
| S2 | Per-app membership model | `appbana_app_members` table (`owner`/`member`/**`end-user`**), `AppMembershipService`, `isAppOwnerOrSystem` becomes membership-aware everywhere it's called, bootstrap + backfill, activates S1.2's membership exception so a cross-tenant grant actually works (S2.6, review round 4, R4-1), **and gives that cross-tenant member a way to actually find the app they were granted (S2.10, review round 5, R5-3)** | ~13.75 hr |
| S3 | Entity data API enforcement | Every route in `GenericEntityRoutes` per the S0 census (three route families, not one) requires real membership or a scoped runtime session; **the shipped Runtime keeps its existing login and gets an `end-user` app-membership row instead of a new session type (S3.7, revised)** | ~12.75 hr |
| S4 | Credential hygiene | Real BCrypt hashing (transparent migration), CSRF decision + doc correction, audit-log actor/tenant hygiene | ~5.5 hr |
| S5 | Capstone tests + ai-builder trust chain | Cross-tenant test suite, ai-builder trusts a verified identity instead of client-supplied ids | ~4.0 hr |

**Total scope:** ~60.92 hours (was ~27 hr pre-review, ~36 hr after round 1, ~38 hr after round 2, ~37.5
hr after round 3, ~38.5 hr after round 4; round 5 adds ~2.25 hr — an admin-token admit branch in S1.2
plus its test, and a cross-tenant discovery query/endpoint plus an index fix in S2 — the fifth
consecutive round to add scope, though the first with no blocker; **round 6 adds none** — both findings
are same-estimate wording/consistency fixes, the first round to add zero net scope; **S1 implementation
review round 2 adds ~1 hr** — S3.4's `authEnabled`-block count corrected from an approximate "16+" to
the ratchet-verified 21, +30 min, plus new task S1.16 for two further unconditionally-open
`SchemaRoutes.java` routes found while re-verifying that count, +30 min; **S1 implementation review
round 3 adds ~0.5 hr** — new task S1.17, deleting `SchemaRoutes.java`'s two now-redundant `authEnabled`
wrappers once S1.15/S1.16 land (S1.16 itself is a text/severity correction this round, no estimate
change). **S1 implementation review round 4 reconciles this entire table against the tracker's own
task-row estimates** — an independent line-item sum of every task row (literally adding up the
tracker's own numbers, not a re-estimate) found every phase but S3 understated relative to its own line
items; S3 is the only one a review had already forced a manual re-derivation of (round 2, S3.4). The
count is 50 tasks, not 49: this pass also caught `S0.1b`, a letter-suffixed task id that a
straightforward `S\d+\.\d+` extraction silently skips — a mistake this verification made on its own
first attempt too, before re-checking for lettered ids. Corrected: S0 ~5→~8.67 hr, S1 ~7.75→~13.42 hr,
S2 ~10→~11.25 hr, S4 ~4.5→~5.5 hr, S5 ~3→~4.0 hr (S3 unchanged at ~11.25 hr, already correct). New
total: ~54.08 hr across 50 tasks (+~11.83 hr / +28% over the pre-round-4 figure) — a correction of a
pre-existing measurement gap, not new work discovered this round. The fix for recurrence is registered
as new task **S0.5**: derive this table from the task rows via an automated check, the same move
S0.2/S0.3 already made for the route census, instead of two hand-maintained numbers that can silently
drift apart. **S1 implementation review round 5 corrects a self-inconsistency in round 4's own edit**:
S0.5 was registered as a new S0 row but its 90 min was never folded into either S0's total or the grand
total (round 4's total summed only S0.0–S0.4). Corrected: S0 ~8.67→~10.17 hr (7 tasks, S0.0's upper
bound + S0.5), new grand total ~55.92 hr across 51 tasks. This round also closes review of S0–S1.6;
resuming only once S1.7 lands code to check docs against. **S1 implementation review round 6 (response
to commit `7bedbb5`, S1.7's file-upload fix) adds 1 hr** — S1.7 itself accepted with no changes
requested; new task S1.18 registered for a file-download authentication gap the review's own live-probe
confirmed (the route requires a session but both places that render its URL use a plain anchor tag that
can't carry one, so every real download 401s — a decision + fix deferred, not made this round). S1
~13.42→~14.42 hr, new grand total ~56.92 hr across 52 tasks. **S1.8 follow-up review (round 2) adds
~0.5 hr** — S2.6's estimate becomes a range, "60–90 min" (mirrors the existing S0.0 range convention),
to cover the now-explicit `SavedViewRoutes.LIST_SQL` owner-model decision folded into its scope; summed
at the established upper-bound convention (S0.5). S2 ~11.25→~11.75 hr, new grand total ~57.42 hr across
the same 52 tasks (no task added, only S2.6's own estimate widened). **S0.5 implemented (S1.8 review
round 3) corrects a compounding drift that predates this fix**: a fresh ground-up sum of every task
row (52 tasks, upper bound for `S0.0`/`S2.6`'s ranges) totals 3425 minutes — **~57.08 hr**, not the
~57.42 hr the entry immediately above states, which itself compounded an error already present in the
~56.92 hr baseline it started from. Not traced to a specific earlier round — consistent with this
task's own point: derive the total mechanically going forward rather than re-auditing five rounds of
hand arithmetic to find exactly where it drifted. New grand total: **~57.08 hr across 52 tasks** (S0
~10.17 hr, S1 ~14.42 hr, S2 ~11.75 hr, S3 ~11.25 hr, S4 ~5.5 hr, S5 ~4.0 hr), now asserted automatically
by `EstimateReconciliationTest` rather than hand-summed. **S1.18 review round 16 adds ~1.5 hr** — new
task S3.8 registered for an absence-census finding surfaced while reviewing S1.18
(`PermissionServiceTest` silently reports `Tests run: 0`, gutted by the H2→PostgreSQL migration and
never restored): a port-to-Testcontainers-or-delete decision, summed at the established upper-bound
convention (S0.5). S3 ~11.25→~12.75 hr, new grand total **~58.92 hr across 54 tasks**. **S2.1 review
round 23 adds ~1.0 hr** — new task S2.11 registered for an absence-census finding: no automated guard
exists for the "changelog migrates a genuinely empty database" rule (the same rule the V0 bootstrap
incident violated), which stops being tolerable once S2.4 lands a data-backfill migration of that exact
shape. S2 ~11.75→~12.75 hr, new grand total **~59.92 hr across 55 tasks**. **S2.1 review round 25 adds
~1.0 hr** — new task S2.12 registered for an absence-census finding: the round-23 schema-block
reconciliation itself still had cosmetic drift from `V19` even under maximum attention, proving nothing
guards that claim from recurring. S2 ~12.75→~13.75 hr, new grand total **~60.92 hr across 56 tasks**.
S0 → S1 → S2 → S3
is the strict serial *authoring* path; **S1 and S2 are additionally a single deployable unit (review
round 5, R5-2)** — S1 must not ship to any environment with live deployed apps on its own; **S3's
completion is additionally a one-time access reset with no backfill (review round 6, R6-2)** — see
Rollout order. S4 is independent and parallel-safe. S5 needs S1–S3 finished; its ai-builder half can
start once S2 exists. S3.7 now depends on S2 (the `end-user` role, its grant endpoint, and S1.2's
now-activated membership exception), not on `scopedAppId`.

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
6. **The deployed Runtime authenticates and loads its app through routes this plan almost guarded
   against the wrong client.** Review round 2 traced actual caller graphs (not just route shapes) and
   found `app-bana-runtime` never calls the scoped runtime login this plan's own target model assumed —
   it uses the platform login and a Studio management route instead. Without an explicit migration task
   (S3.7), finishing S3 exactly as originally written would 403 every real deployed app the day it ships.
7. **The fix for #6 was itself checked against a description, not the data.** Review round 3 queried the
   live `appbana_schemas` table directly (not just source code) and found the entity/credential shape
   round 2's own S3.7 assumed — a `User` entity with an `email`/`password` column per deployed app —
   exists in **zero** of the 120 real apps in this database. A plan that reads correctly at every layer
   can still be wrong about the data underneath it; this is now checked at each phase, not assumed.
8. **The fix for #7 was itself checked against the wrong layer.** Review round 4 found round 3's
   `end-user` membership design is unreachable in practice: `UserManager.register` auto-generates a
   fresh tenant for every signup (its only caller hardcodes `tenantId=null`), so this system is
   tenant-per-user today — confirmed live, 5 apps across 5 distinct tenants, one each. Every membership
   grant beyond an app's own auto-granted owner is therefore necessarily cross-tenant, yet the tenant
   guard (S1.2) rejected a cross-tenant session before the membership check (S2.6) was ever consulted.
   A design can be correct two layers down and still fail one layer up if nobody walks every principal
   through every guard in request order.
9. **The principal walk itself had two blind spots: principals with no tenant at all.** Review round 5
   applied round 4's own checklist exhaustively — every principal this plan admits, through every guard,
   in request order — and found it had never been run against the admin/service token (predates the
   tenant model entirely) or checked for what state a partially-deployed S1 leaves a real end-user in.
   Every guard so far is specified as a predicate over a tenant comparison; the two principals with no
   comparable tenant are exactly the ones a comparison-shaped guard cannot express an answer for.

---

## Non-goals

- **SSO / OAuth / SAML integration.** Out of scope; separate initiative if/when needed.
- **A general RBAC/permission-scopes system** beyond "is this user a member of this app" plus the
  existing maker/checker entity roles. Role *granularity* within an app (e.g. "can edit schema" vs.
  "can only view data") is a future extension of the `appbana_app_members.role` column, not built here.
- **Removing the global `adminToken`/`readToken` model.** `adminToken` is repurposed as an optional
  platform-operator break-glass override (S1.2 and S3.2, revised review round 5, R5-1 — the override
  needs an explicit admit branch at the tenant-boundary layer too, not only at the entity-data layer),
  not deleted. **`readToken`, the separate read-only tier, is retired instead (review round 6, R6-1):**
  neither new guard gives it an admit branch, so it would otherwise keep working in `SchemaRoutes` (S1.9
  only fixed its token-extraction bug there) while 403ing everywhere else — a half-migration this plan
  is otherwise careful to avoid. S1.9 now converts its six `SchemaRoutes.java` call sites to `hasAdmin`
  uniformly, leaving `AuthService.hasRead`/`cfg.getReadToken()` with no remaining callers. If a scoped,
  read-only operator credential is wanted later, add it as an explicit new admit rule in both guards,
  not by reviving `readToken` in just one file.
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
| 3 | The admin/read token has no tenant concept — one shared secret unlocks every tenant's data | S1, S3 (repurposed as break-glass, decoupled from "is auth even checked"; S1.2 needed its own admit branch — review round 5, R5-1) |
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
| M1 | ✅ **Fixed (S0.4)**. The Tomcat/servlet `Router.handle(HttpServletRequest,...)` overload calls the route handler directly with **no middleware chain at all** — confirmed by direct read, it does not reference `middlewares`/`RateLimitMiddleware`/`SessionMiddleware` anywhere. Latent today only because `serverType` defaults to `jdk` (confirmed in `Main.java` and `config.json`); flipping that one config value would silently disable all auth. Fenced rather than retrofitted: `TomcatServer` has zero callers besides `Main.java`'s switch and zero test coverage anywhere in the repo, confirming it was genuinely dormant. `Main.java`'s `case "tomcat":` now logs an explicit error naming this finding and calls `System.exit(1)` before `TomcatServer.start(port)` — and therefore before any port is bound — so the bypass is unreachable via config. Live-verified as two real OS processes (not just a unit test): `serverType="tomcat"` → exit code 1, no port bound; reverted → normal `jdk` boot confirmed on a scratch port; the actual dev backend on 8080 confirmed undisturbed throughout via `/health`. | S0.4 ✅ |
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

## Review round 2 — blockers and additional findings

**Reviewer:** Tech Lead / Architect review, 2026-07-31, second pass. Method: read the fully revised
plan, diffed every task table against the routes it now names, and — the axis round 1's census
didn't have — **traced which client actually calls each route**, across `app-bana-studio`,
`app-bana-runtime`, `app-bana-shared`, `e2e`, and `ai-builder`.

Round 1's dispositions held up under this second pass: `/api/templates` was confirmed to have zero
callers anywhere in the repo (H4's pushback stands), and making `isAppOwnerOrSystem` membership-aware
rather than adding a parallel guard was confirmed to be the better design (H6). S0, S1, S2, and S4 are
accepted as written. I re-verified the new findings below directly against source before accepting them.

### 🔴 Blocker (new)

**R2-1 — S3's accepted principals exclude the principal the shipped Runtime actually presents.**
Confirmed by direct read of `app-bana-runtime/src/runtime/AppRuntimeShell.tsx` and
`app-bana-shared/src/api-client.ts`:

| What the plan's target model assumes | What the shipped code does |
|---|---|
| Runtime end-users log in via `GenericAppAuthController` (`POST /api/runtime/auth/login`) | That route has **zero callers**. `AppRuntimeShell.handleLogin` calls the shared `login()`, which calls `POST /api/auth/login` — the **platform** login, producing an ordinary tenant-wide session |
| A runtime session is "valid only for that app's entity routes, never for `AppRoutes`/Studio management endpoints" (Target model) | The Runtime's *only* way to load its own app is `AppRuntimeShell.loadApp` → `getApp()` → `GET /appbana-studio/{tenantId}/apps/{appId}` — an `AppRoutes`/Studio management endpoint |

Two breakages follow once S1–S3 are enforced as written:
1. **Deployed apps become unrenderable.** Whatever session the Runtime ends up holding, it must call
   an `AppRoutes` route to fetch its page metadata — and the plan's own rule forbids a scoped session
   from ever touching `AppRoutes`.
2. **S3.2's allow-rule 403s every real end-user.** Rule (i) requires `appbana_app_members` membership
   (end-users aren't builders and will never be members); rule (ii) requires a `scopedAppId` session
   that nothing today mints (because login never reaches `GenericAppAuthController`). A real end-user
   matches neither, and `publicRead` (S3.5) only rescues `GET`s on apps explicitly marked public —
   every runtime write stays broken regardless.

This is B1's failure mode one level up: **a guard specified against an assumed client instead of the
shipped one.** S3 was about to harden an endpoint with no callers while leaving the path the Runtime
actually uses unmodelled. Fixed by a new task, S3.7 (see below) — S3 is not fully scoped/estimated
without it.

### 🟠 High (new)

| # | Finding | Verified | Fixed in |
|---|---|---|---|
| R2-2 | S1.9 (round 1) said to keep one `.../full` registration "explicitly marked PUBLIC for runtime." Both `env/{env}/full` registrations (and the sibling `.../full`) have **no callers anywhere in the repo** — R2-1 established the Runtime uses the Studio route instead. Inheriting a public exemption from a stale comment isn't the same as the route being needed. | ✅ confirmed no caller in `app-bana-studio`, `app-bana-runtime`, `app-bana-shared`, `e2e`, or `ai-builder`; confirmed both registrations exist in `AppRoutes.java` | S1.9 (revised — guard or delete, don't just dedupe) |
| R2-3 | The tests that look like coverage for the public-runtime case are tautological. `SessionMiddlewareTest`'s `testPublicRuntimeAppsPathExcluded`/`testPublicDeployedAppsPathExcluded` assert on `/api/apps/hr-management-app/full` and `/api/apps/hr-management-app/env/DEV/full` — path shapes with **no `{tenantId}` segment**, which no registered route actually has (the real routes are `/api/{tenantId}/apps/{id}/full` and `/api/{tenantId}/apps/{id}/env/{env}/full`). They stay green no matter what S1.9 does to the real routes. Separately, `testTemplatesPathExcluded` positively asserts `/api/templates` needs no session at all — S1.6 makes writes require auth, so this test must be split, not left to silently regress to "green because unchanged." | ✅ confirmed by direct read of both the test file and the real route registrations | S1.12 (new) |

### 🟡 Medium (new)

| # | Finding | Fixed in |
|---|---|---|
| R2-4 | `extractServiceToken` already treats `Authorization: Bearer` as a possible **admin/service** token, and its own javadoc's safety claim for `hasAdmin()` rests specifically on "never reads X-Session-Token or cookies" — not on Bearer being exclusively one thing. S0.1 gives that same header a second meaning (a session id). The regression test (S0.1b) must prove the two interpretations never collide, and S0.1 must preserve the existing priority order (service-token check before any new Bearer-as-session-id fallback), not replace it. | S0.1, S0.1b (both revised) |
| R2-5 | S0.3's "assert route count from `Router` reflection matches census row count" passes when a commit deletes one route and adds another — the exact shape of an ordinary refactor. Must assert on the **set** of route signatures (method + path), not a count. | S0.3 (revised) |

### 🟢 Nit

**R2-6** — `api-client.ts`'s `login()`/`register()` default `tenantId` to `'default'` when the backend
response omits it. Today this is harmless-looking; post-S1 it would silently turn into a confusing 403
against the user's real tenant instead of a clear error at login time. One line, folded into S1 while
S1 is being tested anyway.

### Meta-observation — adopted

Round 1's census classified routes by *who could call them*; it had no column for *who actually does*.
That single gap produced R2-1, R2-2, and R2-3: `/full` was flagged as merely unguarded without noticing
it's uncalled, and S3's two-principal model was validated against the plan's own description of the
Runtime instead of the Runtime's source. **Adopted directly: S0.2's census gains a "known callers"
column** (which of `app-bana-studio` / `app-bana-runtime` / `app-bana-shared` / `ai-builder` / `e2e`
actually invoke each route, or "none found"). A route with no known caller is a guard-or-delete
decision, not a default-to-public one — and this column is what would have caught R2-1 before S3 was
estimated at all, not after.

---

## Review round 3 — blockers and additional findings

**Reviewer:** Tech Lead / Architect review, 2026-07-31, third pass. Method: a third distinct axis from
the previous two — round 1 checked route shapes against source, round 2 traced client call graphs
against source, round 3 queried **live data** (`appbana_schemas` in the shared dev Postgres) directly,
because a design can be internally consistent with both the code and the client and still be wrong
about the data those two agree to depend on. Scope: only the tasks round 2 added or revised (S0.1,
S0.1b, S0.2, S0.3, S1.9, S1.12, S1.13) plus S3.7 — no findings against any of them individually, they
are all correctly specified. S2, S4, S5 were re-confirmed unchanged and accepted.

I independently re-verified every claim below before accepting it: read `GenericAppAuthController.java`
in full, read `AuthRoutes.java` to confirm the registered auth routes, read `data/users.json`, and ran
direct queries against the live `appbana-postgres` container (`appbana_schemas` grouped by entity name,
and the three real apps' own entity lists) rather than taking the reviewer's numbers on faith. Every
figure below — 211 schemas, 120 apps, the exact entity-name counts, the three real app names and their
actual entities, the zero-non-fixture `User`/`user` result — matched exactly.

### 🔴 Blocker (new)

**R3-1 — S3.7 (review round 2) migrates the Runtime onto a login that no real app can serve.**
`GenericAppAuthController.login()` resolves an auth entity defaulting to `"User"`
(`body.getOrDefault("entity", "User")`) and queries `SELECT * FROM "<table>" WHERE email = ? AND
password = ?` against that entity's physical table. Verified against the live database:

- Grouping `appbana_schemas` by entity name across all **211 schemas / 120 apps**: `Ticket`(62),
  `Customer`(28), `Product`(15), `Book`/`Order`/`Author`/`Category`(14 each), … The only entities with
  "user" in the name anywhere in the table are 3 rows named `test_users`, all under `tenant-1`/`tenant-2`
  × `app-a`/`app-b` — obviously synthetic test fixtures, not real apps.
- The three real deployed apps (`Inventory Tracker`, `Employee Onboarding`, `IT Helpdesk System`) have
  entities `Product`/`Supplier`; `Department`/`Document`/`Employee`/`EquipmentRequest`/
  `ITAccessRequest`/`OnboardingTask`; and `Employee`/`ITAsset`/`SupportTicket`, respectively. None has a
  `User` entity or a `password` column. `runtimeLogin()` would 404 for every one of them.
- `AuthRoutes.java` registers exactly `/api/auth/register`, `/api/auth/login`, `/api/auth/profile`, and
  `/api/runtime/auth/login` — there is no runtime-scoped register/invite/provisioning path anywhere.
- Today's deployed-app users are platform users in `data/users.json` (confirmed by direct read); nothing
  copies them into a per-app table, and round 2's S3.7 had no such migration task.

Same failure class as R2-1 one level deeper: R2-1 was "the guard doesn't match the client," R3-1 is "the
replacement client doesn't match the data." **Resolution adopted — no second identity store.** An
end-user is now a **role** (`end-user`) on `appbana_app_members`, the table S2 already builds:

- S3.2 rule (i) already reads "a Studio session's user is an `appbana_app_members` member of that
  `(tenantId, appId)`" — role-agnostic as originally written, so **no change** is needed there; it's
  clarified below to say so explicitly.
- S2.6 (revised) already lets **any** membership row — `owner`/`member`/`end-user` alike — pass
  `GET`/list `AppRoutes`, so the `AppRoutes` carve-out old-S3.7(b) needed **disappears entirely**.
  Update/delete/schema-management stay `owner`/`member`-only — an `end-user` grant must never satisfy
  those.
- The Runtime needs **zero frontend changes** — it keeps calling the shared platform `login()` exactly
  as it does today. Old-S3.7(a)'s `runtimeLogin()`/`AppRuntimeShell` switch is dropped.
- `scopedAppId`/`GenericAppAuthController` are not deleted — they remain a legitimate, independently
  hardened (S3.3, S4.2) option for a future app that wants its own dedicated user table instead of
  platform-user + membership. They are simply no longer the Runtime's load-bearing path, so their
  hardening is no longer *blocking* S3's exit criteria — it's hygiene on a live, currently-uncalled-by-
  any-shipped-client endpoint.
- Who grants an `end-user` row is the pre-existing "self-registration policy" open decision
  (owner-invite via S2.7's existing endpoint is sufficient for v1; true self-service signup is a later
  product call) — recorded there rather than decided here.

See the revised S3.7 in [Sub-phase S3](#sub-phase-s3--entity-data-api-enforcement) and the revised
`appbana_app_members` role column in [Data model additions](#data-model-additions).

### 🟡 Medium (new)

**R3-2** — `e2e/tests/hardening/fixtures.ts`'s `newHardeningFixture` has the identical silent default
S1.13 removes from `api-client.ts`: `const tenantId = (loginBody.tenantId as string | undefined) ??
'default'`. Confirmed by direct read. Fixing only the production client leaves the hardening suite
itself exposed to the same failure mode post-S1 — a login response that omits `tenantId` would make the
suite silently probe tenant `default` and read any resulting 403 as a guard bug rather than a fixture
bug. Folded into S1.13 as the same one-line fix, same task, second file.

### 🟢 Nit

**R3-3** — `e2e/tests/a11y-runtime.spec.ts` has the only Runtime e2e that logs in, and it's
`test.fixme('authenticated shell — ...')`, deferred with a `TODO: seed app via backend, authenticate,
navigate...` — confirmed by direct read. The only flow S3.7 actually changes (an end-user logging in and
loading their app) has no automated coverage today. Landing this deferred test as part of S3.7 is the
cheapest available regression guard for exactly the change S3.7 makes, and S3's exit criteria already
require verifying against the running Runtime — this would make that verification repeatable instead of
one-time.

### Meta-observation — adopted

Three rounds, three deeper instances of the same pattern: round 1 found guards designed against an
incomplete *route* list; round 2 found them designed against an assumed *client*; round 3 found the
replacement client designed against assumed *data*. Each artefact was internally consistent, and each
assumption was one hop outside where anyone had looked. What broke the chain each time was the same
move — stop reading the description and query the thing itself. **Adopted: S0.2's census gains a second
new column, "what data must exist for this route to succeed"** (e.g. `GenericAppAuthController.login`
needs an entity named per its `entity`/default-`"User"` param, with `email`/`password` columns, in that
app's own tenant schema) alongside round 2's "known callers" column — because "who calls it" alone would
not have caught R3-1 either. The Runtime *would* have called `runtimeLogin()`; there was simply nothing
on the other end for it to authenticate against.

---

## Review round 4 — blockers and additional findings

**Reviewer:** Tech Lead / Architect review, 2026-07-31, fourth pass. Scope: the round-3 deltas only —
S2.6's capability split, S3.2's role-agnostic clarification, S1.13's widening, S3.7's rewrite, and the
second census column are all confirmed correctly specified; no findings against any of them
individually. S2, S4, S5 re-confirmed good to execute, conditional on the two findings below.

I independently re-verified both findings before accepting them: read `UserManager.register` and
`AuthenticationController.java` in full, confirmed `UserRoutes.java` registers exactly one route via a
direct grep across the file for every `router.get/post/put/delete` call, and queried the live
`appbana_apps` table grouped by `tenant_id` rather than taking the reviewer's "5 apps, 5 tenants" claim
on faith. Every fact below checked out exactly.

### 🔴 Blocker (new)

**R4-1 — S1.2's tenant guard and S2.6's membership check compose as AND, and for the one principal
S3.7 exists to serve, they always disagree.** Verified against source and live data:

- `UserManager.register(name, email, password, tenantId)` auto-generates `"t-" +
  UUID.randomUUID().toString().substring(0, 8)` whenever `tenantId` is null or blank. Its **only**
  caller in the codebase, `AuthenticationController.register()`, calls it with `tenantId` hardcoded to
  `null` — the request body is never consulted for a tenant.
- `UserRoutes.register()` wires exactly one route (`GET /api/users/me`) — there is no invite,
  user-admin, or join-tenant path anywhere in the codebase.
- Live `appbana_apps`, grouped by `tenant_id`: **5 apps, across 5 distinct tenants, exactly one app per
  tenant.** This system is tenant-per-user today, not tenant-per-organization.
- S1.2 (`TenantAccessGuard.requireOwnTenant`) is specified as a flat mismatch→403 with no exception
  clause. S2.6 was specified as membership checks wired "into the S1.3 routes that only got a tenant
  check in S1" — i.e. a *second*, independent check layered after the first, not a modification to it.

Since every real `end-user`/collaborator membership grant is necessarily cross-tenant (there is no
product path to a second user in the same tenant), S1.2 403s that session before S2.6's membership
check — which would have admitted it — is ever reached. **S3.7 is still not executable; it now fails
one layer higher than it did in round 2's version.** This traces back to my own round-3 recommendation,
not a new independent bug — see the meta-observation below.

**Resolution adopted:**
1. **Precedence, stated in S1.2 itself.** `requireOwnTenant` gains an explicit membership exception: a
   session whose tenant does not match the path tenant is still admitted if an `appbana_app_members` row
   exists for `(pathTenantId, pathAppId, session.userId)` — membership is an alternate path through the
   tenant gate, not a second gate behind it. This exception only applies where the route carries a
   specific `appId` (every route S1.3 lists except the bare tenant-wide app list, which stays
   own-tenant-only — a cross-tenant member of one app is not a member of the tenant's entire app list).
   S1 ships this branch inert (nothing to consult yet, so behavior is identical to today's plan); **S2.6
   is what activates it**, by wiring `AppMembershipService.isMember` into this same method — not by
   adding a separate, parallel check. S2.6 is revised below to say so explicitly.
2. **Which tenant `isMember` takes, stated in S2.2 itself.** Always the **app's** tenant (the path/
   `AppMetadata` value), never the session's own tenant — the PK is `(tenant_id, app_id, user_id)`, so a
   session-tenant lookup is a guaranteed miss: fails closed, no security hole, but the feature silently
   does nothing.

See the revised S1.2, S1.11, S2.2, S2.6, and S2.9 in their respective sub-phases below.

### 🟠 High (new)

**R4-2 — S2's motivating scenario is unconstructible through any product code path, and its exit
criterion tests a state the system can't reach.** With tenant-per-user (confirmed above), "another user
in my tenant" cannot be produced by registration, invite, or any other route — only by hand-editing
`data/users.json` or seeding `appbana_app_members` directly. The existing exit criterion ("A Tenant A
user who is not a member of Tenant A's App 2 gets 403 managing App 2, while still managing their own
App 1 normally") is exactly that unconstructible shape.

This is bigger than the one test: **`appbana_app_members` is, in practice, entirely a cross-tenant
sharing model, not an intra-tenant partitioning model** — every grant it will ever hold (an `end-user`,
or any future collaborator) is cross-tenant by construction, which makes R4-1's precedence rule the
*primary* path S2/S3 rely on, not an edge case. Restated below rather than adding a net-new
"invite a colleague into my own tenant" feature, which is a materially larger, separate product
decision — recorded as an addendum to the existing self-registration open decision, not solved here.

### Verdict

S0, S1, S2, S4, S5 remain good to execute, with R4-1's precedence rule folded into S1.2 (a contract
clarification landing two phases before S2, not new scope) and R4-2's exit criterion restated against a
scenario the product can actually produce (S2.2/S2.6/S2.9 revised accordingly; S1.11 gains the positive
case a foreign-tenant member must pass). S3.7 remains blocked until this precedence rule ships — the
membership-grant design itself is still right, it just needed one more layer resolved, and that
resolution belongs in S1, not S3.

Applying the meta-observation's own checklist immediately: I traced the same `end-user` principal
through S3.2's `EntityAccessGuard` as well. It does not share this bug — S3.2's rule (i) is written as
one disjunctive condition (membership, **or** `scopedAppId` match, **or** `publicRead`), not a
membership check layered behind a separate tenant-only AND gate the way `AppRoutes`/`TenantAccessGuard`
was. No change needed there.

### Meta-observation — adopted

R4-1 traces back to my own round-3 recommendation, checked against the layers it lived in (S2's table
shape, S3.2's allow rule) but not against S1.2's tenant guard, which sits above both and had been signed
off two rounds earlier. **A settled section quietly stops being re-examined precisely because it's
settled — but every later decision still changes what flows through it.** The census columns added in
rounds 2 and 3 (known callers, required data) both guard against assumptions about the outside world;
R4-1 came from an assumption about the plan's own internals. **Adopted as a standing check for any
future principal this plan admits:** walk it through every guard in request order — not just the guard
being added for it — and write down the verdict at each hop, before considering a design change final.
For the `end-user` principal that walk is `resolveIdentity` → `requireOwnTenant` → membership →
`EntityAccessGuard`; the second hop is where it was failing.

---

## Review round 5 — blockers and additional findings

**Reviewer:** Tech Lead / Architect review, 2026-08-01, fifth pass. This round built a single exhaustive
artifact instead of an opportunistic read: every principal this plan will ever see, walked through every
guard in request order (the checklist review round 4 adopted). No blocker — S0, S2, S3, S4, S5 confirmed
executable as written; S1 needs two additions before it starts.

I independently re-verified both findings against source, not just the plan's own text: read
`AuthService.extractUserId` in full (confirms the admin/service-token path returns a bare `"admin"`/
`X-User-Id` string with no `SessionData` constructed at all — there is genuinely nothing for a
tenant-comparison guard to read), and read `AppManager.listApps(tenantId)` (confirms the only
app-listing query in the codebase is single-tenant-scoped with no user-based cross-tenant path). I also
independently re-checked the claim about the ai-builder agent forwarding a real bearer token — its
citation link in the pasted review didn't resolve to anything (a paste artifact, not a concern in
itself), so I grepped every tool in `ai-builder/.../agent/tool/` directly: all twelve forward
`Authorization: Bearer <context.token()>`, and `AiChatController` 401s a blank token before any tool
runs. Confirmed exactly as claimed.

### Principal × guard walk (request order)

| Principal | `resolveIdentity` (S0.1) | `requireOwnTenant` (S1.2) | capability check (S2.6) | `EntityAccessGuard` (S3.2) |
|---|---|---|---|---|
| App owner, own tenant | ✅ | ✅ own-tenant | ✅ `owner` | ✅ rule (i) |
| Cross-tenant Studio `member` | ✅ | ✅ via membership exception | ✅ list/get; denies management | ✅ rule (i) |
| Deployed-app end-user (`end-user` role, foreign tenant by construction) | ✅ | ⚠️ **403 for the entire S1→S2 window** | ✅ list/get only, once reached | ✅ rule (i) |
| Anonymous | 401 at `resolveIdentity` | — | — | — |
| Admin break-glass token | ✅ → literal `"admin"` (or `X-User-Id`), no `SessionData`, no `tenantId` | ❌ **no branch exists for this principal** | — | ✅ documented fall-through (evaluated last) |
| ai-builder agent | ✅ — every tool forwards `Bearer context.token()`; `AiChatController` rejects a blank token before any tool runs | acts as whatever the forwarded token resolves to — same as its own owner | ✅ | ✅ rule (i) |

Two cells fail. Both are principals nobody had walked through before this round.

### 🟠 High

**R5-1 — `TenantAccessGuard` has no admin branch, so the documented break-glass override dies at S1,
and no S1 exit criterion would ever notice.** The plan promises this override in four places —
Non-goals, finding #3, cross-cutting concerns' security bullets, and S3.2's fall-through — but S1.2's
contract is 401 (no identity) → membership exception (if `pathAppId`) → 403 (mismatch), with no admit
path for a principal that has no session at all. Confirmed against source: `AuthService.extractUserId`'s
priority-1 branch resolves a valid service token to a bare `"admin"`/`X-User-Id` string without ever
constructing a `SessionData` — so `TenantAccessGuard.requireOwnTenant(session, ...)` has nothing to read
a `tenantId` from for this caller, only a 403 (or worse, an NPE, depending how the eventual
implementation reaches into `session`). The only exit criterion that tests break-glass today lives under
S3 and exercises `EntityAccessGuard` only — nothing exercises it on `AppRoutes`/`SchemaRoutes`. From S1
onward, the documented support-tooling escape hatch would be broken on every app-management and schema
route, and the plan's own test plan is structurally unable to catch it.

**Resolution adopted:** S1.2 gains an explicit first branch, checked *before* any tenant comparison: a
valid service/admin token (`extractServiceToken` + `hasAdmin`) admits immediately, regardless of path
tenant — this is the one principal in the walk above with no tenant to compare, so it must be handled
before the guard tries to compare one. A new test (S1.14) and a new S1 exit criterion make this
checkable at the layer where it was actually missing.

**R5-2 — S1 completes in a state where every deployed app is broken for its own end-users, and S1's own
exit criteria certify that as correct.** S1.2 ships its membership branch *"permanently inert… S1's own
behavior is unchanged,"* and S1's first exit criterion requires a Tenant B session to get 403 on every
`AppRoutes`/`SchemaRoutes` route the census lists against Tenant A — which, per round 4's tenant-per-user
finding, includes `GET /appbana-studio/{t}/apps/{id}` for every real deployed-app end-user, since every
non-creator is by construction a foreign-tenant session. S1's remaining safety criterion ("Tenant A's own
users are unaffected") certifies the owner and says nothing about the principal that actually breaks. If
S1 merges and deploys on its own, the Runtime cannot load any deployed app for anyone but its creator for
the entire S1→S2 interval — and the exit criteria, read literally, call that a pass.

**Resolution adopted:** declare **S1 and S2 a single deployable unit** — S1 must not ship to any
environment with live deployed apps on its own. (The alternatives considered — deferring the one route's
wiring to S2.6, or a flag flipped at the end of S2 — both work but add machinery to unwind later for no
benefit once S2 is this close behind; treating S1+S2 as one atomic release is the simplest statement that
matches how the phases already depend on each other.) Recorded as a deployment note under S1's goal, a
new S1 exit criterion, and a clause in the Rollout order section.

### 🟡 Medium

**R5-3 — nothing lets a cross-tenant member find the app they've been granted.** Confirmed: S1.2 states
the bare app-list route has no membership exception (own-tenant only); S2.7 manages membership on an app
you already know the ID of; S2.8 is about not over-showing, not discovery; and `AppManager.listApps` —
the only app-listing query in the codebase — takes a single `tenantId` and nothing else. After R4-2
restated S2 as a cross-tenant sharing model, a cross-tenant `member` can only ever reach their app if
someone hands them the URL directly. Fine for the Runtime end-user (URL-driven by construction); broken
for the Studio collaborator the membership model now exists to serve. Added as S2.10: a
`listAppsForUser(userId)` query (cross-tenant, unlike every other lookup in this table) and an endpoint
the Studio app switcher unions with its existing tenant-owned list.

**R5-4 — the members index leads with the wrong column for the axis this table now serves.** The schema
above defines `CREATE INDEX idx_app_members_user ON appbana_app_members(tenant_id, user_id)` —
confirmed, this is the exact text already in this plan. The query R5-3 needs, and the natural "what am I
a member of" lookup, filters on `user_id` alone across tenants, which a `(tenant_id, user_id)` index
cannot serve without a full scan. Leading column flipped to `user_id` below — a small fix, but a concrete
sign this table was designed for an intra-tenant axis and not revisited when R4-2 flipped it to
cross-tenant.

### Verdict

**No blocker.** S0, S2 (with R5-3/R5-4 folded in), S3, S4, S5 remain executable as written. S1 needs
R5-1's admin branch and R5-2's deployment-unit decision before it starts — both are contract/rollout
statements, not new engineering, consistent with how round 4's fix landed. This is the first round with
no finding that changes what any guard does once reached — every finding here is a missing statement
about a principal or a rollout step.

### Meta-observation — adopted

Both failures this round are principals with no tenant: the admin token has none because it predates the
tenant model; the end-user has the *wrong* one because tenant-per-user makes every non-creator foreign.
Every guard in this plan is specified as a predicate over a tenant comparison, so the two principals that
don't have a meaningful tenant are exactly the ones that fall through the specification — four rounds
apart, for the same underlying reason. **Adopted:** the durable fix is not another checklist item but a
naming/framing one. What actually decides these requests is *"does this principal have a relationship to
this app"* — ownership, membership, or operator override — with same-tenant being the cheapest of several
ways to satisfy that, not the question itself. `TenantAccessGuard` is named and specified around the
thing that turns out not to be the discriminator; four rounds have each added one exception to it, which
is usually the signal that a guard is factored around the wrong noun. **Not renaming it this round** —
S1.2/S1.3 are already fully specified and about to be built; a rename now is churn with no behavior
change. Recorded here so that if a sixth round finds a *third* exception to this guard, renaming it to
something like `AppAccessGuard` (tenant-match as the first and cheapest of several admit rules, not a
tenant guard with exceptions bolted on) is the fix, not a fourth patch.

Noting for calibration, since this is the fifth round: blockers have converged 4 → 1 → 1 → 1 → 0. My own
first-pass hit rate hasn't changed — each round still found the thing one layer outside wherever the
previous round looked — but this round's principal-walk table is the first artefact in this review that
was exhaustive over a dimension rather than opportunistic, which is why it's also the first round I'd bet
on being close to complete. A round 6 is unlikely to find something of this shape again; if one happens,
it's more likely to be the tenant-per-user-vs-membership discovery gap surfacing somewhere else, or a
genuinely new category, than a third exception to `TenantAccessGuard`.

---

## Review round 6 — blockers and additional findings

**Reviewer:** Tech Lead / Architect review, 2026-08-01, sixth pass. No blockers. Two Medium findings and
a Nit — the reviewer's own framing: "consequences of decisions already made, not defects in them."
Verdict: the plan is done, execute it; no round 7 expected.

I independently verified both Mediums against source, not just the plan's own text. Read `AuthService.java`
in full to confirm `hasRead`/`hasWrite`/`hasAdmin`'s exact semantics: `hasRead` is a genuinely separate,
weaker credential (true on `hasAdmin` **or** an exact `readToken` match), `hasWrite` is defined as exactly
`hasAdmin`. Grepped every `hasRead`/`getReadToken` call site across `app-bana-service`: four of
`SchemaRoutes.java`'s six M2 call sites check `hasRead` (the other two check `hasWrite`, i.e. already
admin-only); `GenericEntityRoutes.java` has four more, all inside the `authEnabled` blocks S3.4 already
replaces wholesale with `EntityAccessGuard` — so those disappear regardless of R6-1, and `SchemaRoutes.java`
is the only file where a `readToken` holder would still be admitted after S3, if nothing here changes. For
R6-2, re-read S2.4: confirmed it backfills `owner` rows only, from `AppMetadata.getAuthor()` — no
`end-user` backfill task exists anywhere in S2, exactly as claimed.

### 🟡 Medium

**R6-1 — `readToken` survives in `SchemaRoutes` and nowhere else once S1/S3 land, and Non-goals still
describes one break-glass model, not two.** Confirmed: S1.2's admit-first branch and S3.2's fall-through
both check `hasAdmin` only; S1.9 (as originally scoped) only swaps `extractToken`→`extractServiceToken`
for the six `SchemaRoutes.java` call sites, it doesn't touch which tier they check — so a `readToken`
holder keeps `GET /schema` after S1.9 but gets 403 on every `AppRoutes` route (S1.2) and every entity
route (S3.2/S3.4) once those land. Two working definitions of "break-glass" would exist at once, in
different files, with nothing explaining the difference to whoever holds a `readToken`.

**Resolution adopted — retire `readToken`:** the alternative (a `GET`-only admit branch for `readToken`
in both new guards) would add a real branch and a test to each of S1.2 and S3.2 for a capability nothing
in this plan otherwise uses — S3.5's `publicRead` flag already exists for "this data should be readable
without full admin," so a second, parallel read-only credential is redundant with a mechanism this plan
already built. Non-goals now states the retirement plainly, and S1.9 is revised to convert all six
`SchemaRoutes.java` call sites to `hasAdmin` via `extractServiceToken()` (not just fix their extraction
bug), leaving `AuthService.hasRead`/`cfg.getReadToken()` with no remaining callers. Estimate unchanged —
same file, same-shaped fix, one tier instead of two.

**R6-2 — S3 completion is a one-time access reset, and Rollout order doesn't say so.** Confirmed: S2.4
backfills `owner` rows only, from `AppMetadata.getAuthor()`; no `end-user` backfill task exists, and none
is possible — today, any registered user can open any deployed app, so there is no record of *legitimate*
end-user access anywhere to migrate (that unrestricted access is precisely the vulnerability S3 closes).
So the moment `EntityAccessGuard` goes live, every deployed app 403s every end-user until its owner grants
each one explicitly via S2.7 — the correct end state, but a visible, one-time event that S3's exit criteria
(which only exercise the happy path with a grant row already present) never describe, and that whoever
enables S3 could easily mistake for a new regression.

**Resolution adopted:** one paragraph added to Rollout order stating this plainly — S3 is a deliberate
one-time access reset, no end-user backfill is possible or intended, and this should be communicated to
whoever operates deployed apps before S3 is enabled.

### 🟢 Nit

**R6-3 — `EntityAccessGuard` rule (i) is a per-request DB round-trip on the path the plan already flagged
as worth avoiding one on.** The Data model section caches `tenantId` on `SessionData` explicitly to
"avoid a DB round-trip on every tenant-boundary check" (S1.2's comparison). Confirmed: S3.2 rule (i) does
exactly such a round-trip — an `appbana_app_members` lookup — on every entity list/get, a hotter path than
app management by construction (every page load of deployed-app data hits it, not just Studio management
actions). Not a correctness issue, and the existing Performance section already notes this is a single
indexed PK lookup at `<5ms`, same order as `ApprovalGuard`'s existing filtering — so this is a deferral,
not a gap.

**Resolution adopted:** one sentence added to the Performance section naming the asymmetry and the
future fix (a session-scoped membership cache, invalidated on grant/revoke) as a deliberate deferral
rather than an oversight, so a future reader doesn't rediscover this from scratch.

### Verdict

**Adopted in full, no blocker.** All three findings are documentation/consistency fixes to sections this
plan already has — Non-goals, Rollout order, Performance — none change a task's scope in a way that
affects estimate or design (S1.9's revision is a same-shaped, same-estimate fix to a task that already
existed). Total scope is unchanged at ~40.75 hr — the first round to add zero net scope, after five
consecutive rounds that each added some.

### Meta-observation — adopted

Worth recording what the shape of this six-round review turned out to be, since it wasn't what it looked
like at the start. Round 1 read as a security review — seven findings, four blockers, anonymous
cross-tenant writes proven live. Rounds 2–5 found nothing further about attackers; every finding from
round 2 onward was a **legitimate principal wrongly denied** — the Studio/ai-builder caller (round 1's
own B1), the Runtime (R2-1), a login with no data behind it (R3-1), the end-user's own tenant (R4-1), the
operator's break-glass token (R5-1). This round's two Mediums continue that pattern one level further:
not a principal denied outright, but a principal (`readToken`) denied *inconsistently*, and a denial
(S3's access reset) that is correct but undocumented. The plan was consistently good at deciding who to
keep out, and had to be walked, layer by layer, into being equally complete about who it must let in and
how that admission actually arrives operationally.

**Adopted, not acted on further:** the cause is structural, not a lapse — deny cases are enumerable from
the code under review; admit cases live in clients, data, and operations the code never mentions, and
only surface by leaving the server and checking `git grep` for callers, `psql` for what data actually
exists, or a login flow end-to-end. The one artefact that reliably found these — the principal × guard
walk table (round 5) — is adopted as standing practice: any future guard or migration touching this
plan's guards should list every principal that must succeed, walk each through every gate in request
order, and record the verdict per hop, before calling the change done.

**Closing this review at six rounds**, per the reviewer's own recommendation: blockers converged
4 → 1 → 1 → 1 → 0 → 0, and this round found no new principal and no new guard behavior — only two
consistency gaps in sections the plan already had. Further value from re-reading this document is
judged to be exhausted; the next checks that matter are the S0.2 census (the first artefact in this plan
to contain facts nobody has written down yet) and S3 end-to-end verification against the running Runtime
— both contact with reality, not further reading.

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

> **Correction (review round 2):** the "Runtime end-user session" path above (issued by
> `GenericAppAuthController`, scoped via `scopedAppId`) is the **target** this plan builds — it is not
> what the shipped `app-bana-runtime` uses today. The real client authenticates via the platform login
> and loads its app metadata via a Studio management route, neither of which this diagram accounts for.
> See [Review round 2, R2-1](#review-round-2--blockers-and-additional-findings) and the new S3.7.
>
> **Correction (review round 3):** the right-hand "Runtime end-user" path in this diagram is not what
> S3.7 builds either. `GenericAppAuthController` has no real entity to authenticate against in any live
> app (Review round 3, R3-1), so it remains a future option, not the shipped Runtime's path. The shipped
> Runtime in fact follows the **left-hand** path in this diagram — an ordinary Studio-shaped session —
> with the membership check (step 2) accepting an `end-user`-role row exactly as it accepts
> `owner`/`member`. See [Review round 3](#review-round-3--blockers-and-additional-findings).

---

## Data model additions

### `appbana_app_members` (new platform table — mirrors `appbana_user_roles`'s shape)

> [!NOTE]
> **Reconciled against the shipped `V19__appbana_app_members.sql` (S2.1 review round 23)** — the block
> below now matches the real DDL exactly. It previously showed `DEFAULT 'member'` with no `CHECK`; V19
> shipped with NO default and a real `CHECK (role IN (...))` instead, following `V16`'s established
> precedent rather than this block's original sketch. Both changes are strictly safer: a `NOT NULL`
> column with no default forces every caller to state a role explicitly, so nothing can silently mint
> an unintended `member` row (which, per the paragraph below, already carries build/view/edit rights)
> just by omitting the column. `V19` is the source of truth; if they ever disagree again, trust `V19`.

```sql
CREATE TABLE appbana_app_members (
  tenant_id    VARCHAR(255) NOT NULL,
  app_id       VARCHAR(255) NOT NULL,
  user_id      VARCHAR(255) NOT NULL,
  role         VARCHAR(20) NOT NULL CHECK (role IN ('owner', 'member', 'end-user')),
  granted_by   VARCHAR(255) NOT NULL,
  granted_at   TIMESTAMP NOT NULL DEFAULT now(),
  PRIMARY KEY (tenant_id, app_id, user_id)
);
-- Leads with user_id (review round 5, R5-4): the cross-tenant "what am I a member of" lookup (S2.10)
-- filters on user_id alone, which a (tenant_id, user_id) index cannot serve without a full scan.
CREATE INDEX idx_app_members_user ON appbana_app_members(user_id, tenant_id);
```

`owner` may manage membership and delete the app; `member` may build/view/edit but not remove other
members or delete the app. **`end-user` (added review round 3, R3-1)** may access this app's own entity
data once S3 wires it, and nothing else — no schema view/edit, no membership management, no delete; a
data-access grant must never satisfy an `isAppOwnerOrSystem`/management check (see S2.6, revised).
Finer roles beyond these three (e.g. schema-editor vs. data-only *within* the builder roles) remain a
future extension of this same column — not built in v1.

### `SessionData` (extend existing `SessionService`)

- Add `tenantId` (captured once at login from `User.tenantId`) — avoids a DB round-trip on every
  tenant-boundary check.
- Add optional `scopedAppId` (null for a normal Studio session; set to a specific `appId` for an
  end-user session created by `GenericAppAuthController`). A non-null `scopedAppId` means this session
  is valid **only** for that app's entity routes (S3.2 rule ii) — never any `AppRoutes`/Studio
  management endpoint. Nothing mints this session type today (review round 2, R2-1). **Revised, review
  round 3 (R3-1):** the shipped Runtime does not use this path — `GenericAppAuthController` has no real
  entity to authenticate against in any live app (see Review round 3). The Runtime instead keeps its
  ordinary platform session and relies on an `end-user`-role `appbana_app_members` row (S2), so no
  `AppRoutes` carve-out is needed for it. `scopedAppId` stays reserved for a future app that wants a
  dedicated, separate end-user table instead of platform-user + membership.

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
| S0.0 | **Prerequisite.** Fix the Maven toolchain so `mvn test` compiles under this repo's configured `release` version — currently fails with "release version 25 not supported." No exit-criteria test in S0–S5 can run until this is fixed. | build config (`pom.xml`, `app-bana-service/pom.xml` / toolchain) | 30–90 min (unscoped until root-caused) |
| S0.1 | `AuthService.resolveIdentity(req, cfg)` — a single method accepting `X-Session-Token`, `Authorization: Bearer`, and the `session_id` cookie (same three forms `SessionMiddleware.extractSessionToken` already supports), returning a resolved principal. Replace `extractUserId`'s broken X-Session-Token-only Priority 3 fallback with a call to this method; `SessionMiddleware.create()` also delegates to it so there is exactly one implementation of "how do we read the caller's credential" in the codebase. **Must preserve the existing priority order** (review round 2, R2-4): `extractServiceToken` already treats a Bearer value as a possible admin/service token, so a Bearer-carried session id is only attempted *after* ruling out the admin/service-token interpretation, never instead of it. | new method in `AuthService.java` (or a small extracted `IdentityResolver`), `SessionMiddleware.java` | 100 min |
| S0.1b | Regression test: all three token forms, sent against the same valid session, yield the same principal on a route excluded from `SessionMiddleware` (e.g. `/api/{tenantId}/apps/{appId}/{entity}`). This is the test that would have caught B1. **Add three more cases (review round 2, R2-4):** an admin/service token sent via Bearer with `X-User-Id` still resolves to that `X-User-Id` through priority 1; a session id sent via Bearer (not equal to the admin token) resolves through the new fallback to that session's user; and neither value is ever misread as the other. | new test | 45 min |
| S0.2 | Machine-generated route census: enumerate every `router.get/post/put/delete(...)` registration across every `*Routes.java` file. Columns: path, middleware-excluded?, identity gate present?, tenant/app check present?, tenant/app source (`path` \| `query` \| `body` \| `header` \| `none`), **known callers** (which of `app-bana-studio` / `app-bana-runtime` / `app-bana-shared` / `ai-builder` / `e2e` actually invoke it, or "none found" — review round 2 meta-observation), **data preconditions** (what must already exist for a call to succeed, e.g. a specific entity/column shape — review round 3 meta-observation: "who calls it" doesn't catch a route whose caller exists but whose required data doesn't, which is exactly how R3-1 slipped past round 2). Predicate is **any client-controlled tenant/app identifier**, not just path params (fixes H5). A route with no known caller is flagged for a guard-or-delete decision, not left to default to whatever its current state happens to be. Attach the generated table to this plan. | new `RouteCensus` (small script or JUnit-generated report), appended to this doc | 165 min |
| S0.3 | A test that fails when a route is registered without a corresponding census entry. **Assert on the set of route signatures (HTTP method + path) from `Router` reflection matching the census exactly, not a row/route count** (review round 2, R2-5: a count is unchanged when a commit deletes one route and adds another — an ordinary refactor would pass silently). This is what stops the "found gaps sit just outside the plan's own boundary" pattern from recurring on a route added, renamed, or removed next month. | new test | 75 min |
| S0.4 | Fix or fence `Router.handle(HttpServletRequest, HttpServletResponse)` (M1): either route it through the same middleware chain `handle(HttpExchange)` uses, or make `serverType=jdk` an explicit, enforced deployment constraint (fail fast at startup if `serverType` is set to anything else while this gap remains). Confirmed by direct read: this overload calls `r.handler.accept(...)` directly with no reference to any middleware. | `Router.java`, `Main.java` | 45 min |

### Exit criteria — S0

- [ ] `mvn test` compiles and runs on this repo's toolchain.
- [ ] All three credential forms resolve to the same principal on at least one middleware-excluded route.
- [ ] A Bearer-carried admin/service token still resolves via priority 1, never misread as a session
      lookup; a Bearer-carried session id never satisfies `hasAdmin()` (review round 2, R2-4).
- [ ] The route census exists, is attached to this plan, lists every registered route with a non-empty
      tenant/app-source classification, and has both its **known-callers** (review round 2) and
      **data-preconditions** (review round 3) columns populated for every row — not left blank.
- [ ] Registering, renaming, or removing a route without updating the census fails CI (set comparison,
      not a count — review round 2, R2-5).
- [ ] `serverType` other than `jdk` either goes through the same middleware or refuses to start.

**Everything in S1–S3 below is scoped by the S0.2 census, not by the finding tables above** — the
tables identify *why* this work is needed; the census is what tells S1–S3 *where* to apply it.

---

## S0.2 Route census (generated 2026-08-01)

**Methodology:** Every `router.get/post/put/delete(...)` registration across all 14 `*Routes.java`
files was enumerated by direct file reads (not grep sampling), classified against
`SessionMiddleware.isExcludedPath`'s actual rule order (reproduced below), and checked for an
identity-gate call, a tenant/app enforcement comparison, and the source of any client-controlled
tenant/app identifier (predicate: **any** such identifier — path, query, body, or header — not just
literal `{tenantId}` path params, per H5). "Known callers" was verified by grep across
`app-bana-studio/src`, `app-bana-runtime/src`, `app-bana-shared/src`, `ai-builder/src`, and `e2e/`.
**Which `Router` overload is actually live was independently confirmed this session** (not assumed):
`logs/backend.log` from a live `start-everything` boot reads `"AppBana (JDK HTTP) running on port
8080"`, confirming `Router.handle(HttpExchange)` — which runs the middleware chain — is what's
running, not the servlet overload from M1/S0.4 that bypasses it. The classification below is valid
for this environment on that basis.

**`SessionMiddleware.isExcludedPath` rule order** (exact precedence, first match wins):
1. Path contains `/roles`, equals `/schema`, contains `/approvals`, or ends with `/submit`,
   `/approve`, `/reject` → **NOT excluded** (always requires a session), regardless of anything below.
2. Path equals `/api/users/me` or starts with `/api/users/me/` → **NOT excluded**.
3. Path matches `^/api/[A-Za-z0-9_.-]+(/([A-Za-z0-9_.-]+))?/?$` (1–2 segments after `/api/`) →
   **EXCLUDED**.
4. Path starts with `/api/` and contains `/apps/` → **EXCLUDED**.
5. Path matches a literal `EXCLUDED_PATHS` entry (`/api/auth/login`, `/api/auth/register`,
   `/api/auth/refresh`, `/health`, `/ready`, `/ui/`, `/openapi.json`, `/api/csrf/token`,
   `/api/templates`, `/api/apps/`, `/api/ai/`, `/api/tenants/*/branding`, `/api/app-context`,
   `/*.html`, `/*.js`, `/*.css`, `/assets/`) → **EXCLUDED**.
6. Else **NOT excluded** (default — requires a valid session).

Column legend: **Mw-excl.** = middleware-excluded (skips `SessionMiddleware`'s session check
entirely) · **Id. gate** = an identity-resolution call is present in the handler · **T/A check** =
an actual comparison of the target tenant/app against the caller's own identity/ownership, used to
allow-or-deny (not just a read-back or a default fallback) · **T/A source** = how the tenant and/or
app identifier this route acts on reaches the handler.

> [!NOTE]
> **This census is a dated snapshot, not a live document.** The `Mw-excl.`/`Id. gate`/`T/A check`
> columns below describe the system exactly as observed on 2026-08-01, before any S1 task landed —
> deriving that one-time scope is what S0.2 was for. They are **not** updated as S1's own tasks
> (S1.3 onward) add guards to routes listed here: `RouteCensusTest` (S0.3) only guards the route
> **set** (added/renamed/removed), never these classification columns, and S0.5's reconciliation
> covers only task estimates and Files/Where lists — no mechanism keeps these columns current.
> **For a route's actual, current guard status, read `TENANT_ISOLATION_IMPLEMENTATION_TASKS.md`'s own
> per-task rows and write-ups**, not this table. Confirmed already drifting two different ways as of
> S1.9: the `POST /appbana-studio/{tenantId}/apps` row below was hand-corrected during the B1 fix
> (the one partial update that's already happened, and exactly the inconsistency a frozen snapshot
> avoids), while `GET /api/{tenantId}/apps/{id}/full` and `.../env/{env}/full` still read
> `Id. gate? No` / `T/A check? No` below despite both requiring
> `TenantAccessGuard.requireOwnTenant` since S1.9 (review round, S1.9). Left as originally generated
> rather than patched further — the point of freezing this table is to stop hand-patching it.

### GenericEntityRoutes.java

| Method | Path | Mw-excl.? | Id. gate? | T/A check? | T/A source | Known callers | Data preconditions |
|---|---|---|---|---|---|---|---|
| GET | `/audit` | No (default) | Yes (`hasRead`, conditional on `authEnabled`) | No | query (optional) | none found | none |
| GET | `/api/field-permissions` | Yes (rule 3) | Yes (`hasAdmin`, conditional) | No | query (optional) | none found | none |
| GET | `/api/field-permissions/readable` | Yes (rule 3) | Yes (`extractUserId`, conditional) | No | query (`entity` required) | none found | `entity` query param; `PermissionService` initialized |
| GET | `/api/field-permissions/editable` | Yes (rule 3) | Yes (`extractUserId`, conditional) | No | query (`entity` required) | none found | same as above |
| GET | `/api/field-permissions/{id}` | Yes (rule 3) | Yes (`hasAdmin`, conditional) | No | none (own PK, not tenant/app) | none found | row must exist |
| POST | `/api/field-permissions` | Yes (rule 3) | Yes (`hasAdmin`, conditional) | No | body | none found | `roleId`/`entityName`/`fieldName` required |
| PUT | `/api/field-permissions/{id}` | Yes (rule 3) | Yes (`hasAdmin`, conditional) | No | none | none found | row must exist |
| DELETE | `/api/field-permissions/{id}` | Yes (rule 3) | Yes (`hasAdmin`, conditional) | No | none | none found | row must exist |
| POST | `/api/{entity}` | Yes (rule 3) | Yes (`hasAdmin` + `extractUserId` for `submitted_by`) | No | path (packed `{tenantId}_{appId}_{entityName}` key) | studio, runtime, shared, e2e | schema must be registered under this key |
| POST | `/api/{entity}/batch` | Yes (rule 3) | Yes (`hasAdmin` + `extractUserId` per element) | No | path | ai-builder, e2e | schema must exist; body ≤1000 elements |
| GET | `/api/{entity}` | Yes (rule 3) | Yes (`hasRead` + `extractUserId` for field filtering) | **Partial** — only on the `_approvalStatus=PENDING*` branch (checker/owner role check) | path + query | studio, runtime, shared, e2e | schema must exist |
| GET | `/api/{entity}/{id}` | Yes (rule 3) | Yes (`hasRead` + `extractUserId`) | No | path | runtime, shared, e2e | schema + row must exist |
| PUT | `/api/{entity}/{id}` | Yes (rule 3) | Yes (`hasAdmin` + `extractUserId`) | No | path | runtime, shared, e2e | schema + row must exist |
| DELETE | `/api/{entity}/{id}` | Yes (rule 3) | Yes (`hasAdmin`) | No | path | runtime, shared, e2e | schema must exist; not `PENDING` if approval-required |
| POST | `/api/{entity}/bulk-delete` | Yes (rule 3) | Yes (`hasAdmin` + `extractUserId` for audit) | No | path + body (`ids`) | none found | schema must exist; body ≤1000 ids |
| POST | `/api/{entity}/bulk-export` | Yes (rule 3) | Yes (`hasRead` + `extractUserId`) | No | path + body (`ids`) | none found | schema must exist; body ≤5000 ids |
| POST | `/appbana-studio/{tenantId}/apps/{appId}/{entity}` | No (default) | Yes (`extractUserId`, 401 if blank) | **No — IDOR** | path | none found | valid session; schema must exist |
| GET | `/appbana-studio/{tenantId}/apps/{appId}/{entity}` | No (default) | **No** (relies solely on `SessionMiddleware`, any tenant's session passes) | **No — IDOR** | path | none found | valid session; schema must exist |
| GET | `/appbana-studio/{tenantId}/apps/{appId}/{entity}/{id}` | No (default) | **No** | **No — IDOR** | path | none found | valid session; schema + row must exist |
| PUT | `/appbana-studio/{tenantId}/apps/{appId}/{entity}/{id}` | No (default) | Yes (`extractUserId`, 401 if blank) | **No — IDOR** | path | none found | valid session; schema + row must exist |
| DELETE | `/appbana-studio/{tenantId}/apps/{appId}/{entity}/{id}` | No (default) | Yes (`extractUserId`, 401 if blank) | **No — IDOR** | path | none found | valid session; schema must exist |
| POST | `/api/{tenantId}/apps/{appId}/{entity}` | Yes (rule 4) | Yes (`extractUserId`, 401 if blank) | **No — IDOR** | path | none found | valid session; schema must exist |
| POST | `/api/{tenantId}/apps/{appId}/env/{env}/{entity}` | Yes (rule 4) | Yes (`extractUserId`, 401 if blank) | **No — IDOR** | path | none found | valid session; schema must exist |
| GET | `/api/{tenantId}/apps/{appId}/env/{env}/{entity}` | Yes (rule 4) | **No — ⚠️ zero auth of any kind** | No | path | none found | schema must exist |
| GET | `/api/{tenantId}/apps/{appId}/env/{env}/{entity}/{id}` | Yes (rule 4) | **No — ⚠️ zero auth of any kind** | No | path | none found | schema must exist |
| PUT | `/api/{tenantId}/apps/{appId}/env/{env}/{entity}/{id}` | Yes (rule 4) | Yes (`extractUserId`, 401 if blank) | **No — IDOR** | path | none found | schema must exist |
| DELETE | `/api/{tenantId}/apps/{appId}/env/{env}/{entity}/{id}` | Yes (rule 4) | Yes (`extractUserId`, 401 if blank) | **No — IDOR** | path | none found | schema must exist |

### AppRoutes.java

| Method | Path | Mw-excl.? | Id. gate? | T/A check? | T/A source | Known callers | Data preconditions |
|---|---|---|---|---|---|---|---|
| POST | `/api/{tenantId}/apps/{id}/publish` | Yes (rule 4) | Weak (`extractUserId`, defaults to `"system"` on null) | No | path + query (`env`) | shared→studio (`Header.tsx`), ai-builder (`DeployAppTool`), e2e | app must exist; `env` ∈ DEV/SIT/PROD |
| PUT | `/api/{tenantId}/apps/{id}/deploy/local` | Yes (rule 4) | **No** | No | path | none found | app must exist |
| POST | `/api/{tenantId}/apps/{id}/commits` | Yes (rule 4) | Weak (defaults to `"system"`) | No | path + body | none found | none enforced |
| POST | `/api/{tenantId}/apps/{id}/commits/rollback` | Yes (rule 4) | **No** | No | path + body (`version`) | ai-builder (`RollbackAppTool`) | body `version` required |
| POST | `/api/{tenantId}/apps/{id}/versions` | Yes (rule 4) | Weak (no null-check) | No | path + body | none found | none enforced |
| GET | `/api/{tenantId}/apps/{id}/versions` | Yes (rule 4) | **No** | No | path | none found | none |
| POST | `/api/{tenantId}/apps/{id}/deploy/{versionId}` | Yes (rule 4) | Weak (no null-check) | No | path + query/body (`env`) | none found | `versionId` must reference an existing version |
| GET | `/api/{tenantId}/apps/{id}/pipeline` | Yes (rule 4) | **No** | No | path | none found | none |
| GET | `/api/{tenantId}/apps/{id}/env/{env}/full` (was 2 identical registrations at generation time — deduped by S1.9, see note above) | Yes (rule 4) | **No** | No | path | none found | prior deployment snapshot must exist |
| POST | `/api/{tenantId}/apps/{id}/restore-schemas` | Yes (rule 4) | **No** | No | path + query (`env`) | none found | prior deployment snapshot must exist |
| GET | `/appbana-studio/{tenantId}/apps` | No (default) | **No** (relies solely on session) | No | path (`tenantId` only) | shared→studio (`ChatPane`, `Header`), ai-builder (`ListAppsTool`) | none |
| GET | `/appbana-studio/{tenantId}/apps/{id}` | No (default) | **No** | **No — any valid session, any tenant, passes** | path | shared→runtime (`AppRuntimeShell`), 8 ai-builder tools | app must exist |
| GET | `/api/{tenantId}/apps/{id}/full` (public runtime API) | Yes (rule 4) | No (intentionally public) | No | path | **none found** — Runtime actually calls the Studio route above instead | app must exist |
| POST | `/appbana-studio/{tenantId}/apps` | No (default) | **Yes** — real gate, 401 if blank; sets `author` from caller (anti-spoof) | **Yes** — `TenantAccessGuard`, `pathAppId=null` (see footnote²) | path + body (`id`, `name`) | shared→studio (`Header`), ai-builder (`CreateAppTool`, `ScaffoldAppTool`), e2e | body `id` unique, non-blank |
| PUT | `/appbana-studio/{tenantId}/apps/{id}` | No (default) | **No** | No | path | ai-builder (`UpdateAppTool`, `CreateEntityTool`) — no Studio/Runtime UI path | app must exist |
| DELETE | `/appbana-studio/{tenantId}/apps/{id}` | No (default) | **No** | No | path | ai-builder (`ScaffoldAppTool` rollback-only), e2e cleanup — no Studio/Runtime UI path | app must exist |
| GET | `/appbana-studio/{tenantId}/apps/{id}/workflow` | No (default) | **No** | No | path | ai-builder (`ListWorkflowsTool`) | returns `{}` if none |
| PUT | `/appbana-studio/{tenantId}/apps/{id}/workflow` | No (default) | **No** | No | path + body | none found | none enforced (blind upsert) |
| GET | `/appbana-studio/{tenantId}/apps/{appId}/pages/{pageId}` | No (default) | **No** | No | path | none found (`getPage()` client wrapper exists but is never called) | app/page must exist |
| PUT | `/appbana-studio/{tenantId}/apps/{appId}/pages/{pageId}` | No (default) | **No** | No | path + body | ai-builder (`GeneratePageTool`, `BatchUpdateEntitiesTool`) | none enforced (blind upsert) |
| DELETE | `/appbana-studio/{tenantId}/apps/{appId}/pages/{pageId}` | No (default) | **No** | No | path | none found | app/page must exist |
| GET | `/api/templates` | Yes (rules 3+5) | **No** | No | N/A (no tenant/app param) | none found | none |
| GET | `/api/templates/{id}` | Yes (rule 3) | **No** | No | N/A | none found | must exist |
| POST | `/api/templates` | Yes (rule 3) | **No** | No | N/A | none found | none enforced |
| PUT | `/api/templates/{id}` | Yes (rule 3) | **No** | No | N/A | none found | none enforced |
| DELETE | `/api/templates/{id}` | Yes (rule 3) | **No** | No | N/A | none found | none enforced |

*Footnote: `SessionMiddleware`'s own comment claims `/appbana-studio/*` is "currently public for
development," but no rule in the actual `isExcludedPath` logic matches that prefix — it falls to the
rule-6 default (session required). Code behavior, not the stale comment, is reflected above.*

*Footnote²: **CORRECTED — S1 review round 1, finding B1 (2026-08-01).** This cell originally read
"N/A (creation)", reasoning that there's no *existing* app yet to check ownership of. That was true
but beside the point — there is no existing app, but there IS a target tenant supplied by the client
(the path segment) and a caller with a tenant of their own, and nothing compared the two. The route
shipped with zero `TenantAccessGuard` call at all, so any authenticated session for tenant A could
create an app inside tenant B's path (`author` forced to the real caller, so the planted row would
later qualify as an `owner` app-membership row once S2.4 backfills memberships — a durable foothold,
not just switcher pollution). Fixed by calling `TenantAccessGuard.requireOwnTenant(req, cfg,
pathTenantId, null)` — `pathAppId=null` because no app exists yet for the S2.6 membership exception
to apply to. Recorded here so a future "N/A" cell on a creation route is read as "no ownership check
applies" only if there is also no target tenant to compare — never as a blanket exemption from the
tenant check itself.*

### ApprovalRoutes.java

| Method | Path | Mw-excl.? | Id. gate? | T/A check? | T/A source | Known callers | Data preconditions |
|---|---|---|---|---|---|---|---|
| POST | `/api/tenants/{tenantId}/apps/{appId}/entities/{entityName}/records/{id}/submit` | No (rule 1) | Yes (`extractUserId`, 401) | No (delegates to `ApprovalService`'s per-tuple MAKER/BOTH role check, not a tenant match) | path | runtime (`RecordApprovalPanel`, `StudioTableLive`) | row exists, DRAFT/REJECTED; caller holds MAKER/BOTH role |
| POST | `/api/tenants/{tenantId}/apps/{appId}/entities/{entityName}/records/{id}/approve` | No (rule 1) | Yes (`extractUserId`, 401) | No (same delegation) | path | runtime (`CheckerQueuePage`, `StudioTableLive`) | row PENDING; caller CHECKER/CHECKER_L2; caller ≠ submitter |
| POST | `/api/tenants/{tenantId}/apps/{appId}/entities/{entityName}/records/{id}/reject` | No (rule 1) | Yes (`extractUserId`, 401) | No (same delegation) | path | runtime (`CheckerQueuePage`, `StudioTableLive`) | row PENDING; caller CHECKER; body `reason` required |
| GET | `/api/tenants/{tenantId}/apps/{appId}/entities/{entityName}/approvals/pending` | No (rule 1) | Yes (`extractUserId`, 401) | No (same delegation) | path | runtime (`CheckerQueuePage`, `usePendingCounts`) | caller holds CHECKER/CHECKER_L2 role |
| GET | `/api/tenants/{tenantId}/apps/{appId}/entities/{entityName}/records/{id}/approvals/audit` | No (rule 1) | Yes (`extractUserId`, 401) | No (same delegation) | path | runtime (`AuditDrawer`) | row exists; caller holds some role on the tuple |

### SchemaRoutes.java

| Method | Path | Mw-excl.? | Id. gate? | T/A check? | T/A source | Known callers | Data preconditions |
|---|---|---|---|---|---|---|---|
| GET | `/api/endpoints` | Yes (rule 3) | Conditional `hasRead` (off by default) | No | none | none found | none |
| GET | `/openapi.json` | Yes (rule 5) | Conditional `hasRead` (off by default) | No | none | none found | none — spans all tenants |
| GET | `/schema` | No (rule 1) | Conditional `hasRead` (off by default) | No | none — lists ALL tenants' schema names | studio (`DataDrawer`), ai-builder (`ListEntitiesTool`) | none |
| GET | `/schema/{name}` | No (rule 6 default) | Conditional `hasRead` (off by default) | **No — any session can fetch any other tenant's schema by key** | path (packed key) | studio, runtime, ai-builder, e2e | schema with that key must exist |
| POST | `/schema` | No (rule 1) | **Yes** — mandatory `extractUserId` (401) + `isAppOwnerOrSystem` (403) | **Yes** (ownership-only, not tenant-match) | **body** (`tenantId`/`appId` JSON fields — canonical body-sourced example) | ai-builder (`CreateEntityTool`, `BatchUpdateEntitiesTool`), e2e — **no Studio/Runtime UI caller** | app must exist with matching `author`, or caller `"system"` |
| DELETE | `/schema/{name}` | No (rule 6 default) | **No unconditional gate at all** (only a conditional `hasWrite`, off by default) | **No — zero ownership check (unlike POST)** | path (packed key) | ai-builder (`BatchUpdateEntitiesTool`), e2e — no Studio/Runtime UI caller | schema must exist; **⚠️ any authenticated user of any tenant can delete/drop any other tenant's entity by guessing its key** |

### RoleRoutes.java

| Method | Path | Mw-excl.? | Id. gate? | T/A check? | T/A source | Known callers | Data preconditions |
|---|---|---|---|---|---|---|---|
| GET | `/api/tenants/{tenantId}/apps/{appId}/roles` | No (rule 1) | Yes (`extractUserId`, 401) | No (ownership-only via `isAppOwnerOrSystem`, or self-read bypass) | path | **none found** | app exists with caller as author/system, or self-query |
| POST | `/api/tenants/{tenantId}/apps/{appId}/roles` | No (rule 1) | Yes (`extractUserId`, 401) | No (ownership-only, pre-lookup ordering; body `tenantId` explicitly discarded) | path | **none found** | app exists with caller as author/system; target entity schema exists |
| DELETE | `/api/tenants/{tenantId}/apps/{appId}/roles` | No (rule 1) | Yes (`extractUserId`, 401) | No (ownership-only) | path | **none found** | app exists with caller as author/system; role row must exist |

### AppMembershipRoutes.java (S2.7, S2.10)

| Method | Path | Mw-excl.? | Id. gate? | T/A check? | T/A source | Known callers | Data preconditions |
|---|---|---|---|---|---|---|---|
| GET | `/api/tenants/{tenantId}/apps/{appId}/members` | No (rule 1) | Yes (`TenantAccessGuard.requireOwnTenant`, 401/403) | Yes — tenant guard, then owner-only (`isAppOwnerOrSystem`, 403; deliberately stricter than `AppRoutes`'s owner-or-member gate, S2.6) | path | **none found** — no members/invite panel exists yet (S2.7 Cat. 3) | app's own tenant matches, or caller holds a membership row on this app; caller must additionally be `owner` or `system` |
| POST | `/api/tenants/{tenantId}/apps/{appId}/members` | No (rule 1) | Yes (`TenantAccessGuard.requireOwnTenant`, 401/403) | Yes — same as GET; body `userId`/`role` explicitly required, path `tenantId`/`appId` authoritative | path | **none found** | same as GET; accepts all 3 roles (`owner`/`member`/`end-user`) on grant |
| DELETE | `/api/tenants/{tenantId}/apps/{appId}/members` | No (rule 1) | Yes (`TenantAccessGuard.requireOwnTenant`, 401/403) | Yes — same as GET; `userId` query param required | path + query | **none found** | same as GET |
| GET | `/api/users/me/apps` | No (rule 2) | Yes (`AuthService.resolveSession`, 401) | No — by design, the ONE deliberately non-tenant-scoped app-listing route in the plan (S2.10). The own-tenant half is an unfiltered `AppManager.listApps(ownTenantId)` dump, but `ownTenantId` is session-derived and NEVER a client query param — unlike `GET /api/users/me` below, a client-supplied tenant id here would let any caller enumerate an arbitrary tenant's full app roster. The cross-tenant half is inherently self-scoped: `listAppsForUser(callerUserId)` can only ever return the CALLER's OWN membership grants | session (`AuthService.resolveSession`) | studio (`Header.tsx`, `ChatPane.tsx`'s `syncNewlyCreatedApp`) via shared `listMyApps()` | valid session only |

### UserRoutes.java

| Method | Path | Mw-excl.? | Id. gate? | T/A check? | T/A source | Known callers | Data preconditions |
|---|---|---|---|---|---|---|---|
| GET | `/api/users/me` | No (rule 2) | Yes (`extractUserId`, 401) | No (`isAppOwnerOrSystem`/`tenantId` are read-back only, never gate the request) | query (response-scoping only, not a gate) | runtime (`useCurrentUser` hook — `AppRuntimeShell`, `CheckerQueuePage`, `PendingCountsProvider`, `StudioTableLive`) — **not called anywhere in studio** | valid session only |

### AuthRoutes.java

| Method | Path | Mw-excl.? | Id. gate? | T/A check? | T/A source | Known callers | Data preconditions |
|---|---|---|---|---|---|---|---|
| POST | `/api/auth/register` | Yes (rule 3) | No (N/A — establishes identity) | No | none (tenantId always `null` client-side) | shared→studio (`AuthGate`), e2e (5 specs) | email must not already exist |
| POST | `/api/auth/login` | Yes (rule 3) | No (N/A) | No | none | shared→studio (`AuthGate`), e2e | user row + matching plaintext password |
| GET | `/api/auth/profile` | Yes (rule 3, not on literal list — segment-count side effect) | **No — dead code**, reads a `"session"` attribute nothing ever sets | No | none | **none found — always 401s, unreachable** | n/a |
| POST | `/api/runtime/auth/login` | No (default — 3 segments, no `/apps/`) | No handler check; **self-contradictory**: not middleware-excluded, so requires a pre-existing session the intended anonymous end-user can't have | No | body (`appId`, `tenantId`, `entity`) | **none found** — Runtime's real login uses `/api/auth/login` instead | caller must already hold a session (contradicts its own purpose) |
| GET | `/api/csrf-token` | Yes (rule 3; literal-list entry is stale/mismatched, moot) | No — accepts unvalidated `X-Session-Id` | No | header (unvalidated) | none found | none |
| POST | `/api/csrf-validate` | Yes (rule 3) | No | No | header | none found | a token previously issued by csrf-token for same id |

### AiRoutes.java

| Method | Path | Mw-excl.? | Id. gate? | T/A check? | T/A source | Known callers | Data preconditions |
|---|---|---|---|---|---|---|---|
| POST | `/api/ai/chat` | Yes (rule 3) | No — dead placeholder stub | No | none | **none found** — real chat lives entirely in the separate `ai-builder` service | none — static canned response |
| POST | `/api/ai/chat/agent` | Yes (rule 5) | No — dead placeholder stub | No | none | **none found** for this stub (ai-builder registers its own real route at the same path on a different port) | none — static canned response |

### WorkflowRoutes.java

| Method | Path | Mw-excl.? | Id. gate? | T/A check? | T/A source | Known callers | Data preconditions |
|---|---|---|---|---|---|---|---|
| POST | `/api/workflows` | Yes (rule 3) | **No** | No | body (`appId`, optional, unvalidated) | **none found** | none enforced; `created_by` hardcoded `"system"` |
| GET | `/api/workflows` | Yes (rule 3) | **No** | No | query (`appId`, optional — omit for every workflow system-wide) | **none found** | none |
| GET | `/api/workflows/{id}` | Yes (rule 3) | **No** | No | path (no cross-check vs. row's own `app_id`) | **none found** | must exist |
| POST | `/api/workflows/{id}/publish` | No (default — 3 segments) | **No** | No | path | **none found** | must exist, `status='DRAFT'` |
| POST | `/api/workflows/{id}/start` | No (default) | **No** | No | path + body | **none found** | id must exist |
| GET | `/api/my-tasks` | Yes (rule 3) | **No — `userId` is a plain unauthenticated query param** (`// TODO: Get from JWT`) | No | query (`userId`, spoofable) | **none found** | user/role row exists |
| POST | `/api/my-tasks/{tokenId}/complete` | No (default — 3 segments) | **No — `userId` hardcoded `"system"`**, no ownership check | No | path | **none found** | pending token must exist |
| GET | `/api/my-requests` | Yes (rule 3) | **No — same unauthenticated `userId` pattern** | No | query (spoofable) | **none found** | none |
| GET | `/api/workflow-instances` | Yes (rule 3) | **No** | No | query (**no `appId`/tenant filter supported at all** despite the row having an `app_id` column) | **none found** | none |

*This entire 9-route family has zero real callers anywhere in studio/runtime/shared/ai-builder/e2e —
see "no known caller" list below. Highest-severity guard-or-delete cluster in this census: multiple
routes are simultaneously public (middleware-excluded) and trust unauthenticated client-supplied
identity.*

### SavedViewRoutes.java

| Method | Path | Mw-excl.? | Id. gate? | T/A check? | T/A source | Known callers | Data preconditions |
|---|---|---|---|---|---|---|---|
| GET | `/api/saved-views` | Yes (rule 3) | **No** — list is not even filtered by `owner_user_id` despite the file's own header comment claiming it is | No | query (`tenantId`, `appId`, `entityKey` — any caller with a guessed triple sees every owner's views) | runtime (`SavedViewsBar` in `StudioTableLive`) | ≥1 saved-view row for that triple |
| POST | `/api/saved-views` | Yes (rule 3) | **Yes, but bypassable** — defaults `ownerUserId` from `req.getAttribute("userId")` but a client-supplied `ownerUserId` in the body silently overrides it | No | body (`tenantId`, `appId`, `entityKey`, spoofable `ownerUserId`) | runtime (`SavedViewsBar`) | none — no existence validation |
| DELETE | `/api/saved-views/{viewId}` | Yes (rule 3) | **No** | **No — not even a viewId-ownership check; `DELETE FROM ... WHERE view_id = ?` with no tenant/app/owner clause at all** | path (`viewId` only) | runtime (`SavedViewsBar`) | view must exist — **⚠️ any caller with any valid `viewId` can delete any other tenant's/user's saved view** |

### FileRoutes.java

| Method | Path | Mw-excl.? | Id. gate? | T/A check? | T/A source | Known callers | Data preconditions |
|---|---|---|---|---|---|---|---|
| POST | `/api/files/upload` | Yes (rule 3) | Weak — `req.getAttribute("userId")` read for an audit column only, no 401 on null | **No** — `tenantId`/`appId` from body validated only against a shape regex, no owner/existence check | body (`tenantId`, `appId`) | runtime (`FileUploadField.tsx`) | none — always accepts |
| GET | `/api/files/{tenantId}/{appId}/{fileId}` | **No** (4 segments exceeds rule 3's 1–2 segment limit — class Javadoc is stale here, still describes an older 2-segment shape) | **No** — no identity call at all | **Yes, but data-consistency only, not caller-authorization** — `WHERE file_id=? AND tenant_id=? AND app_id=?` must all match, else 404 (deliberately not 403, to avoid an existence-leak); does **not** verify the caller/session belongs to `tenantId` | path (`tenantId`, `appId`, `fileId`) | runtime (`FileUploadField.tsx`, `StudioTableLive.tsx`) | row matching all 3 values must exist; protection rests on `fileId` being an unguessable UUID, not on authorization |

**⚠️ Test/implementation mismatch found and independently verified this session:**
`e2e/tests/hardening/hardening-h1-file-tenant-isolation.spec.ts` POSTs multipart to
`${BACKEND_URL}/api/files/${tenantId}/${appId}/upload` (a 3-segment path with tenant/app **before**
`upload`) — but the only registered upload route is `POST /api/files/upload` (**zero** path params;
tenant/app must be JSON body fields). Confirmed via direct grep of both files — no route matches the
test's URL shape. The test's first assertion (`expect(upload.status()).toBeLessThan(300)`) should
receive a 404 and fail immediately, meaning **the H1 file-tenant-isolation hardening test is either
currently failing, silently skipped, or not part of the executed suite** — it is very likely not
actually proving what its own docstring claims ("tenant A's files must never be downloadable by
tenant B"). This needs investigation as its own follow-up (not fixed here — S0.2 is a census, not a
fix); flagged prominently because it directly undermines confidence in existing tenant-isolation test
coverage, which is the entire subject of this plan.

### AppContextRoutes.java

| Method | Path | Mw-excl.? | Id. gate? | T/A check? | T/A source | Known callers | Data preconditions |
|---|---|---|---|---|---|---|---|
| GET | `/api/app-context` | Yes (rules 3+5) | **No** | No — never denies, falls back to defaults | query (`tenantId`, `appId`, `host`) | shared defines `fetchAppContext()` but **zero call sites anywhere in the repo** — all real callers use the unrelated client-side `resolveAppContext(window.location)` instead | none |

### HealthRoutes.java

| Method | Path | Mw-excl.? | Id. gate? | T/A check? | T/A source | Known callers | Data preconditions |
|---|---|---|---|---|---|---|---|
| GET | `/health` | Yes (rule 5) | No | No | none | e2e (pre-flight ping only, 5 specs) | none |
| GET | `/ready` | Yes (rule 5) | No | No | none | **none found** | working JDBC connection (else 503 body, not thrown) |

### TenantBrandingRoutes.java

| Method | Path | Mw-excl.? | Id. gate? | T/A check? | T/A source | Known callers | Data preconditions |
|---|---|---|---|---|---|---|---|
| GET | `/api/tenants/{tenantId}/branding` | Yes (rule 5, wildcard) | No (by design — public display data only) | No — any caller may fetch any tenant's branding | path (`tenantId`) | studio (`AuthGate`, `Header`), runtime (`LoginPage`, `AppRuntimeShell`), shared (`fetchBranding`), e2e | none — unknown tenantId returns defaults, not an error |

### Routes with no known caller (flag for guard-or-delete decision)

- `GET /audit`, all 4 `/api/field-permissions*` GET/PUT/DELETE routes, `POST /api/field-permissions`,
  `POST /api/{entity}/bulk-delete`, `POST /api/{entity}/bulk-export` (GenericEntityRoutes)
- All 5 `/appbana-studio/{tenantId}/apps/{appId}/{entity}...` routes and all 4
  `/api/{tenantId}/apps/{appId}/{entity|env}...` write routes (GenericEntityRoutes) — **plus the two
  fully-unauthenticated env-scoped GET routes flagged above**
- `PUT .../deploy/local`, `POST .../commits`, `POST .../versions`, `GET .../versions`,
  `POST .../deploy/{versionId}`, `GET .../pipeline`, `GET .../env/{env}/full` (1st, live),
  `POST .../restore-schemas`, `GET /api/{tenantId}/apps/{id}/full`, `GET .../env/{env}/full` (2nd,
  dead code), `PUT .../workflow`, `GET`/`DELETE .../pages/{pageId}`, all 5 `/api/templates*` routes
  (AppRoutes)
- `GET /api/endpoints`, `GET /openapi.json`, `GET /api/debug/schemas`, `GET /api/debug/schemas/names`
  (SchemaRoutes)
- All 3 routes in RoleRoutes.java (entire file has zero known callers, despite the Maker-Checker plan
  depending on it)
- `GET /api/auth/profile` (dead code), `POST /api/runtime/auth/login`, `GET /api/csrf-token`,
  `POST /api/csrf-validate` (AuthRoutes)
- Both routes in AiRoutes.java (superseded by the real `ai-builder` service)
- **All 9 routes in WorkflowRoutes.java** (highest-priority cluster — see file section above)
- `GET /ready` (HealthRoutes)
- `GET /api/app-context` (AppContextRoutes — has a defined client wrapper but zero call sites)

### Critical findings surfaced by this census (beyond the route-inventory purpose itself)

1. **Two fully-unauthenticated data routes**: `GET /api/{tenantId}/apps/{appId}/env/{env}/{entity}`
   and `.../{entity}/{id}` (GenericEntityRoutes) are simultaneously middleware-excluded AND have no
   in-handler identity gate — reachable with **zero** authentication of any kind, not just zero
   tenant check.
2. **`GET /api/debug/schemas`** (SchemaRoutes) is likewise fully public and returns a cross-tenant
   schema summary to anyone.
3. **`DELETE /schema/{name}`** (SchemaRoutes) has no ownership check at all, unlike its `POST`
   sibling — any authenticated user of any tenant can delete (and optionally drop the table of) any
   other tenant's entity by guessing its key.
4. **`DELETE /api/saved-views/{viewId}`** has no tenant/app/owner clause in its SQL whatsoever — any
   caller with any valid `viewId` can delete any other tenant's saved view. `POST /api/saved-views`'s
   `ownerUserId` default is also client-overridable.
5. **The entire `WorkflowRoutes` family (9/9 routes)** has no identity gate, no tenant/app enforcement
   (despite instances having an `app_id` column), and zero known callers anywhere in the codebase —
   the single highest-priority guard-or-delete cluster in this census.
6. **File upload/download test-vs-implementation mismatch** (see FileRoutes section) — the existing
   H1 hardening e2e test targets a URL shape that doesn't match the registered route, meaning this
   plan's baseline "is file tenant isolation already tested" assumption needs verification, not
   trust.
7. **Two dead-code routes confirmed**: `GET /api/auth/profile` (reads an attribute nothing sets,
   always 401s) and the second, byte-identical registration of
   `GET /api/{tenantId}/apps/{id}/env/{env}/full` in AppRoutes.java (unreachable — `Router` is
   first-match-wins and the first registration always wins).
8. **`POST /api/runtime/auth/login`** is self-contradictory by construction: it is not
   middleware-excluded, so it demands a valid pre-existing platform session before its handler (which
   exists specifically to *establish* a session for an anonymous end-user) ever runs. Zero callers;
   confirms the Runtime's real login uses ordinary `/api/auth/login` instead, consistent with review
   round 3's finding.
9. **`GET /appbana-studio/{tenantId}/apps/{id}`** — the route the real Runtime shell actually calls
   to load app metadata — has no tenant check at all: any valid session, for any tenant, can fetch any
   other tenant's app metadata by id. This is the concrete route review round 2's S3.7 finding was
   about.

**None of the above are fixed in this task** — S0.2 is a census, not a remediation. Every item above
is now traceable to a specific S1/S2/S3 task (or, for the zero-caller clusters, a guard-or-delete
product decision) rather than sitting as an unscoped worry.

---

## Sub-phase S1 — Tenant boundary on app management

**Goal:** No session can act on a tenant other than its own, anywhere in `AppRoutes` or
`SchemaRoutes` — **every route the S0.2 census lists for these two files**, not only list/get/update/delete.
Review round 1 (B2, B4) found the originally-named four routes are a strict subset of what needs
guarding in these two files alone.

**Deployment note (review round 5, R5-2):** S1.2 ships its membership-exception branch permanently
inert, so at S1 completion every session from a different tenant — including every real deployed-app
end-user, who is a foreign-tenant session by construction (round 4) — gets 403 on `AppRoutes`/
`SchemaRoutes`, with no membership check yet available to admit them. **S1 and S2 are therefore a single
deployable unit: S1 must not ship to any environment serving live deployed apps on its own**, or the
Runtime stops loading any app but its creator's until S2.6 lands. Writing S1 before S2 is fine; deploying
S1 before S2 is not, once real end-user traffic exists.

| # | Task | Where | Est. |
|---|---|---|---|
| S1.1 | Add `tenantId` (and reserve `scopedAppId`) to `SessionData`; populate `tenantId` at login from `User.tenantId` | `SessionService.java`, `AuthenticationController.java` | 45 min |
| S1.2 | New `TenantAccessGuard.requireOwnTenant(session, pathTenantId, pathAppId)`. **(Revised, review round 5, R5-1 — gains an admit-first branch.)** Check order: **(0) a valid service/admin token** (`extractServiceToken` + `hasAdmin`, no `SessionData` required) **admits immediately, regardless of path tenant** — the break-glass override this plan already promises (Non-goals, finding #3) has no tenant to compare, so it is checked before any tenant logic runs, not folded into it. Then: **401 if there is no resolved identity at all** (via S0.1's `resolveIdentity`), **403 on a tenant mismatch**. Two distinct outcomes, not one mismatch-only comparison (fixes M7). **(Revised, review round 4, R4-1.)** A tenant mismatch is not immediately 403: when `pathAppId` is present, first check whether an `appbana_app_members` row exists for `(the app's own tenant, pathAppId, session.userId)` — a membership hit admits the request despite the mismatch; only a miss falls through to 403. The bare tenant-wide app-list route (no `pathAppId`) has no such exception — own-tenant only. S1 ships the membership branch permanently inert (nothing to consult until S2's table exists); **S2.6 is what activates it**, wiring `AppMembershipService.isMember` into this exact method rather than adding a second, parallel check. The admin branch above is fully active from S1, since it needs no membership data. | new `com.appbana.security.TenantAccessGuard` | 75 min |
| S1.3 | Wire the guard into **every** `AppRoutes` handler per the S0.2 census — not just list/get/update/delete: also `publish`, `deploy/local`, `commits`, `commits/rollback`, `versions`, `deploy/{versionId}`, `pipeline`, `restore-schemas`, `workflow` GET/PUT, `pages/{pageId}` GET/PUT/DELETE (B2). `restore-schemas` in particular currently mutates schema state with zero authentication. | [`AppRoutes.java`](../../app-bana-service/src/main/java/com/appbana/server/routes/AppRoutes.java) | 120 min |
| S1.4 | Add the missing ownership check to `DELETE /schema/{name}` (B4) — today only `POST /schema` calls `isAppOwnerOrSystem`; the delete/drop-table path does not. Moved up from S2 since it's a same-shape fix to the same file already being touched. | [`SchemaRoutes.java`](../../app-bana-service/src/main/java/com/appbana/server/routes/SchemaRoutes.java) | 30 min |
| S1.5 | `GET /api/debug/schemas` (H1): require the same session check its sibling `/names` endpoint already gets. Root-cause fix: don't let a route's exclusion from `SessionMiddleware` depend on incidental path-segment count — name debug/admin routes in `EXCLUDED_PATHS`'s complement explicitly rather than relying on `ENTITY_API_PATTERN` to *not* match them. | `SchemaRoutes.java`, `SessionMiddleware.java` | 30 min |
| S1.6 | Gate `POST/PUT/DELETE /api/templates` (H4) behind an authenticated (admin, for now) identity — reads stay public pending the open decision on whether templates get a tenant dimension. | `AppRoutes.java` | 30 min |
| S1.7 | `POST /api/files/upload` (H2): require a resolved identity and derive `tenantId`/`appId` from it (or from the authenticated user's own app membership once S2 lands) instead of trusting the request body. Add an upload-path test to `FileRoutesTenantIsolationTest`, which today only covers downloads. | `FileRoutes.java` | 45 min |
| S1.8 | `SavedViewRoutes` (H3): require a resolved identity on all three routes; add `tenant_id`/`app_id`/`owner_user_id` to `DELETE_SQL`'s `WHERE` clause (today: `view_id` alone). | `SavedViewRoutes.java` | 45 min |
| S1.9 | Dedupe the two byte-identical `GET /api/{tenantId}/apps/{id}/env/{env}/full` registrations into one, **and guard both it and its `.../full` sibling with the same S1.3 tenant+membership check — not a public carve-out** (review round 2, R2-2: confirmed zero callers anywhere in `app-bana-studio`, `app-bana-runtime`, `app-bana-shared`, `e2e`, or `ai-builder`; a stale "PUBLIC RUNTIME API" comment is not a reason to keep an unauthenticated route). If a deliberately public app-metadata endpoint is wanted later, add it explicitly under S3.5's `publicRead` flag rather than reviving this one. Also fix the six `SchemaRoutes.java` call sites passing `extractToken()`'s output to `hasRead`/`hasWrite` (M2) — **and, since `readToken` is retired (review round 6, R6-1), replace all six with `hasAdmin` via `extractServiceToken()` rather than preserving the separate `hasRead` tier** — leaving `AuthService.hasRead`/`cfg.getReadToken()` with no remaining callers anywhere in the codebase. | `AppRoutes.java`, `SchemaRoutes.java` | 60 min |
| S1.10 | Startup warning: log a loud, repeated `WARN` while `AuthService.authEnabled(cfg)==false` so this is never silently shipped to production | `ApiServer.java` startup path | 30 min |
| S1.11 | `CrossTenantAppAccessTest` + `CrossTenantSchemaAccessTest` — tenant B's session must not list/get/update/delete/publish/deploy/rollback/restore tenant A's apps, nor read/delete tenant A's schemas. **Gains a positive case (review round 4, R4-1):** a tenant B session that **is** a member of one specific tenant A app must be allowed through `requireOwnTenant` for that app's routes (list/get, per S2.6's split) — proving the guard is membership-aware, not a pure mismatch check. This positive case cannot go green until S2's membership table exists; it is written here, alongside its sibling deny cases, and finished once S2.6 activates S1.2's exception. | new tests | 105 min |
| S1.12 | **(New, review round 2, R2-3)** Fix `SessionMiddlewareTest`'s tautological assertions: `testPublicRuntimeAppsPathExcluded`/`testPublicDeployedAppsPathExcluded` assert path shapes missing the `{tenantId}` segment every real route has, so they stay green regardless of what S1.9 does to the actual routes — rewrite against the real shapes and flip the expectation to "requires session" now that S1.9 removes the public carve-out. Split `testTemplatesPathExcluded` into a read-still-excluded case and a write-requires-auth case, since S1.6 makes writes require auth but this test currently asserts the whole path needs no session. | `SessionMiddlewareTest.java` | 30 min |
| S1.13 | **(New, review round 2, R2-6; widened review round 3, R3-2; widened again review round 10, R10-1)** `login()`/`register()` in `api-client.ts` default `tenantId` to `'default'` when the backend response omits it — post-S1 this silently becomes a confusing 403 against the user's real tenant instead of a clear login-time error. Throw instead of defaulting when the response is missing it. The identical pattern exists in `e2e/tests/hardening/fixtures.ts`'s `newHardeningFixture` (`loginBody.tenantId ?? 'default'`) — same fix, same task, so a hardening-suite failure post-S1 reads as a fixture bug fixed once, not a new guard bug to chase. Review round 10 found the byte-identical line in a second, non-hardening spec (`e2e/tests/sprint-3-crud-roundtrip.spec.ts`), understating the original blast-radius claim, plus now-dead `?? 'default'` fallbacks one call downstream of `login()`/`register()` in `AuthGate.tsx` worth deleting while in the area. | `app-bana-shared/src/api-client.ts`, `e2e/tests/hardening/fixtures.ts`, `e2e/tests/sprint-3-crud-roundtrip.spec.ts`, `app-bana-studio/src/features/auth/AuthGate.tsx` | 25 min |
| S1.14 | **(New, review round 5, R5-1)** `BreakGlassAdminBypassesTenantGuardTest` — a request carrying a valid service/admin token (with or without `X-User-Id`) is admitted by `TenantAccessGuard.requireOwnTenant` on an `AppRoutes`/`SchemaRoutes` route regardless of path tenant, proving the override this plan already promises actually exists at this layer — until now only S3's `EntityAccessGuard` exit criterion tested it, and this guard had no admin branch to test at all. | new tests | 30 min |

### Exit criteria — S1

- [ ] A session for Tenant B gets 403 (not 404, not 200) on every `AppRoutes`/`SchemaRoutes` route the
      S0.2 census lists against Tenant A, including `restore-schemas` and `DELETE /schema/{name}`.
- [ ] A request with **no** resolved identity gets 401, distinctly from the 403 a wrong-tenant identity gets.
- [ ] `GET /api/debug/schemas` requires the same session its `/names` sibling already requires.
- [ ] `POST/PUT/DELETE /api/templates` require an authenticated admin identity.
- [ ] `POST /api/files/upload` and all of `SavedViewRoutes` require a resolved identity; the saved-view
      delete path is scoped by tenant+app+owner, not `view_id` alone.
- [ ] Both `GET .../full` routes require the same tenant+membership check as the rest of `AppRoutes` —
      no more unauthenticated 200, and only one registration remains (review round 2, R2-2).
- [ ] `SessionMiddlewareTest`'s public-runtime and templates assertions match the real route shapes and
      post-S1 behavior, not a shape no route has (review round 2, R2-3).
- [ ] Tenant A's own users are unaffected on every route above.
- [ ] Server logs a visible warning on every boot where global auth is disabled.
- [ ] **(New, review round 5, R5-1)** A request carrying a valid service/admin token is admitted by
      `TenantAccessGuard` regardless of path tenant — the break-glass override applies at this layer,
      not only at S3's `EntityAccessGuard`.
- [ ] **(New, review round 5, R5-2)** S1 is not deployed alone to any environment serving live
      deployed-app traffic — S1 and S2 ship as a single atomic unit; this is a release-process
      criterion, not a route-level test.

---

## Sub-phase S2 — Per-app membership model

**Goal:** A user can only manage the specific apps they've been granted membership on — not every app
in a tenant. **Restated, review round 4 (R4-2):** with today's tenant-per-user registration (every
signup gets its own fresh tenant — see Review round 4), a tenant only ever contains one user, so in
practice this is **entirely a cross-tenant sharing model**: an app's own creator is auto-granted `owner`
in their own tenant, and every other grant — an `end-user`, or any future collaborator — is necessarily
a user from a *different* tenant. (Per product decision: **explicit per-app membership**, not blanket
tenant-wide access — that remains true; it's the *within one tenant* framing that no longer matches how
the system is actually populated.)

**Design change from the original draft (review round 1, H6):** rather than adding a second, parallel
`AppAccessGuard` and migrating only the two call sites (`SchemaRoutes`, `RoleRoutes`) this plan
originally named, `AppAuthorization.isAppOwnerOrSystem` itself becomes membership-aware. It has 4
call-site files today (`ApprovalService` ×3, `RoleRoutes`, `SchemaRoutes`, `UserRoutes`), not 2 — making
the existing helper consult `appbana_app_members` (falling back to `AppMetadata.getAuthor()` only where
no membership row exists yet) means all 4 upgrade together and there is exactly one authority for
"is this caller allowed to act on this app", not two that can drift apart.

**Design change (review round 3, R3-1):** `role` gains a third value, `end-user`, pulled forward from
the "future extension, not built in v1" status the Data model section originally gave it — because
S3.7 now depends on it: a deployed app's end-user is a member with `role='end-user'` rather than a
session from a separate identity store. This changes nothing about S2's architecture (same table, same
service, same grant endpoint); it only means S2.6's wiring must now explicitly decide, per route,
whether an `end-user` grant is enough — see S2.6 below.

**Design change (review round 5, R5-3/R5-4):** R4-2 established this table is, in practice, a
cross-tenant sharing model — but nothing in S2 as originally scoped let a cross-tenant member actually
*find* the app they'd been granted (confirmed: the app-list route stays own-tenant-only per S1.2, and
`AppManager.listApps` — the only app-listing query in the codebase — takes a single `tenantId` and
nothing else). S2.2 and S2.8 gain a cross-tenant lookup and its UI surfacing (S2.10, new), and the
table's own index is corrected to lead with `user_id` — the column this new lookup actually filters on
— instead of `tenant_id`.

| # | Task | Where | Est. |
|---|---|---|---|
| S2.1 | Liquibase changeset for `appbana_app_members` (schema above, now with `end-user` as a valid `role`, review round 3, R3-1) | `app-bana-service/src/main/resources/db/changelog/` | 30 min |
| S2.2 | `AppMembershipService` — `grant/revoke/listMembers/isMember(appTenantId, appId, userId)/isOwner(...)`, mirroring `UserRoleService`'s shape. **`appTenantId` (renamed from `tenantId`, review round 4, R4-1)** is always the app's own tenant — from `AppMetadata`/the path, never `session.tenantId` — since the table's PK is `(tenant_id, app_id, user_id)` and a session-tenant lookup on a cross-tenant grant is a guaranteed, silent miss. **Gains `listAppsForUser(userId)` (review round 5, R5-3)** — the one method in this service deliberately *not* scoped to a single tenant, since its purpose is finding a member's cross-tenant grants; backed by the corrected `(user_id, tenant_id)` index (R5-4). | new `com.appbana.security.AppMembershipService` | 90 min |
| S2.3 | Bootstrap: app creator is auto-granted `owner` membership at creation time (same pattern as maker-checker's C1.5) | [`AppRoutes.java`](../../app-bana-service/src/main/java/com/appbana/server/routes/AppRoutes.java) create handler | 30 min |
| S2.4 | **Backfill migration** — for every pre-existing app row, insert an `owner` membership from `AppMetadata.getAuthor()`. Must tolerate the live data shape (review round 1, M8: some apps have numeric-string authors, some have arbitrary strings). Where the author is null/blank, assign the global `"system"` sentinel as user_id and record `granted_by = 'system-backfill'` as the audit marker (round-40 reconciliation: no per-tenant-admin concept exists in this codebase to assign instead — see the implementation tracker's S2.4 round-40 response for the full rationale) rather than failing the migration. | new Liquibase data migration or one-time startup task | 90 min |
| S2.5 | Make `AppAuthorization.isAppOwnerOrSystem` membership-aware: check `appbana_app_members` first, fall back to the existing `AppMetadata.getAuthor()` comparison only when no membership row exists for that app yet (pre-backfill safety net). No call site needs to change — all 4 (`ApprovalService`, `RoleRoutes`, `SchemaRoutes`, `UserRoutes`) upgrade automatically. `end-user` never satisfies this check — it stays `owner`-or-system exactly as today, so the new role cannot grant management rights by accident (review round 3, R3-1). | `AppAuthorization.java` | 75 min |
| S2.6 | **(Revised, review round 3, R3-1 — split by capability, not applied uniformly; revised again, review round 4, R4-1 — activates S1.2's exception, doesn't layer beside it.)** This task **completes `TenantAccessGuard.requireOwnTenant`** by wiring `AppMembershipService.isMember` into the membership-exception branch S1.2 ships inert — it is not a second, independent check applied after S1.3's tenant gate (that framing is exactly what R4-1 found broken: an AND-composition that rejects the cross-tenant member before this task's checks ever run). Once active: `AppRoutes` **list/get** accept **any** membership row (`owner`/`member`/`end-user` alike) — this is also what S3.7 relies on for `GET /appbana-studio/{t}/apps/{id}`, so no separate runtime-session carve-out is needed there. `AppRoutes` **update/delete and the release-management family** (`publish`/`deploy`/`commits`/`rollback`/`versions`/`pipeline`/`restore-schemas`/`workflow`/`pages`) require the existing `isAppOwnerOrSystem` (`owner`, or `member` where S1.3 already allows build/edit) and explicitly exclude `end-user` — a data-access-only grant must never satisfy a management or destructive check. **Also resolve the S1.8-review-flagged `SavedViewRoutes.LIST_SQL` owner-model gap (S1.8 follow-up review, round 1)** — no `owner_user_id` filter today, harmless only while tenant-per-user holds; once a second member can list the same app's views, either add an owner/`is_shared` filter or explicitly document saved views as tenant-shared. | `AppRoutes.java`, `TenantAccessGuard.java`, `SavedViewRoutes.java` | 60–90 min |
| S2.7 | `GET/POST/DELETE /api/tenants/{t}/apps/{a}/members` — membership management endpoints, `owner`-only. Accepts any of the three role values on grant, including `end-user` (review round 3, R3-1) — an owner invites a deployed-app end-user through this existing endpoint; no new endpoint is needed. Whether a user can ever get `end-user` access *without* an owner's grant (self-service signup) is the pre-existing "self-registration policy" open decision, which now also gates this. | new `AppMembershipRoutes.java` | 60 min |
| S2.8 | Studio frontend: verify the app switcher/list only ever renders what the (now correctly filtered) server response contains — no client-side "show all tenant apps" assumption left over. **Extended (review round 5, R5-3):** union the tenant-owned list with S2.10's cross-tenant `listAppsForUser` result, so an app a user is a member of in another tenant actually appears in their switcher instead of being reachable only by direct URL. | `app-bana-studio/src/features/**` (session/workspace store) | 60 min |
| S2.9 | Tests: `AppMembershipGuardTest`, `AppRoutesMembershipTest`, `IsAppOwnerOrSystemConsultsMembershipTest` (all 4 call sites agree once membership exists), plus `EndUserMembershipCannotManageAppTest` (review round 3: an `end-user` grant gets 403 on update/delete/schema-management but 200 on list/get), plus **`CrossTenantMembershipAllowsAccessTest` (review round 4, R4-1)** — a cross-tenant member (the realistic case per R4-2: a user from a *different* tenant than the app's own) successfully lists/gets the app they're a member of, proving S1.2's membership exception actually admits them past the tenant gate, not only that S2.6 restricts what they can do once admitted; this also finishes S1.11's positive case. The route-census regression test lives in S0.3, not here — this phase only needs to prove membership is actually consulted. | new tests | 120 min |
| S2.10 | **(New, review round 5, R5-3)** `GET /api/users/me/apps` (or equivalent) — returns the union of the caller's own-tenant apps and every app they hold cross-tenant membership on via `listAppsForUser`, for the Studio app switcher (S2.8) to consume. This is the only app-listing route in the plan that is deliberately not tenant-scoped. | new route in `AppMembershipRoutes.java` (or `AppRoutes.java`) | 60 min |
| S2.11 | **(New, S2.1 review round 23; hazards fixed + reprioritized, round 25)** No automated guard exists for the "changelog migrates a genuinely empty database" rule that caused the V0 bootstrap incident — every test runs against the shared, already-migrated dev Postgres. Tolerable while S2.1 was a self-contained `CREATE TABLE`; not tolerable once S2.4 lands a data-backfill migration of exactly the shape that broke fresh provisioning before. **Take before S2.2** (round 25). New test opens its **own dedicated `Connection`** to a **uniquely-named, throwaway** database (raw JDBC `CREATE DATABASE` — Testcontainers deliberately not adopted in this module, see calibration below) and asserts it is actually connected there, never falling back to `JdbcManager`'s shared dev datasource (which would make the test a silent no-op). Runs **only** `liquibase.update(...)` — never `ApiServer.startJdk`'s neighboring `dropAll()` branch. Force-terminates lingering backends before dropping the throwaway database, so a run killed mid-migration can't leave an undroppable database or changelog lock for the next run. | new test in `app-bana-service/src/test/java/com/appbana/server/` | 60 min |
| S2.12 | **(New, S2.1 review round 25)** The round-23 schema-block reconciliation (this doc's "Data model additions" → `appbana_app_members`) was performed by hand and, even under maximum attention on the round meant to fix this exact class of drift, still left cosmetic differences from `V19` — proof that nothing guards the claim from recurring, unlike route census (S0.3) or estimate reconciliation (S0.5). New test: extract the fenced SQL block, normalize (lowercase, collapse whitespace, strip `IF NOT EXISTS`), compare against `V19` normalized the same way, fail on difference. | new test in `app-bana-service/src/test/java/com/appbana/server/` | 45–60 min |

### Exit criteria — S2

- [ ] **(Restated, review round 4, R4-2 — the original wording described a state tenant-per-user can't
      produce.)** A user from Tenant B who is **not** a member of Tenant A's App 2 gets 403 managing
      App 2; the same user, granted `member` on Tenant A's App 1, can manage App 1 normally despite the
      tenant mismatch — proving membership, not tenant identity, is what gates access once granted.
- [ ] Every app that existed before this migration is still manageable by its original creator
      immediately after deploy (backfill verified against a copy of production-shaped data, including
      apps with non-numeric authors).
- [ ] An app whose recorded author no longer resolves to a real user is backfilled to a designated
      fallback owner and logged, not silently dropped or left to crash the migration.
- [ ] Only an `owner` member can grant/revoke membership or delete the app.
- [ ] All 4 `isAppOwnerOrSystem` call sites (`ApprovalService`, `RoleRoutes`, `SchemaRoutes`, `UserRoutes`)
      agree on the same answer for the same (tenant, app, user) once membership data exists — proving
      there's one authority, not two.
- [ ] An `end-user`-role member can list/get the app's own metadata but gets 403 on update, delete, and
      every release-management route — the role split is enforced, not just declared (review round 3,
      R3-1).
- [ ] **(New, review round 5, R5-3)** A user with membership on an app in a tenant other than their own
      sees that app in their own app switcher/list (via S2.10), not only when handed the URL directly.

---

## Sub-phase S3 — Entity data API enforcement

**Goal:** Every entity-data route in `GenericEntityRoutes` — **all three route families the S0.2 census
lists**, not only the 21 places an `authEnabled` block already exists — stops trusting an optional
global token and instead requires either real Studio app-membership or a runtime session correctly
scoped to that one app.

**Scope correction (review round 1, B3):** the original S3.4 ("replace the 16+ `authEnabled` blocks")
is a find-and-replace over *existing* checks, which cannot reach the 11 routes that have no such block
to replace: the studio-scoped `/appbana-studio/{t}/apps/{a}/{entity}(/{id})` family (5 routes) and the
env-scoped `/api/{t}/apps/{a}/env/{env}/{entity}(/{id})` family (6 routes). Two of the unguarded GETs in
the latter family are also middleware-excluded — anonymous, cross-tenant reads today. S3.4 below is
re-specified against the S0.2 census, enumerated, not against a grep for `authEnabled`.

**Scope correction (review round 2, R2-1):** S3's two accepted principals — a Studio member, or a
`scopedAppId` runtime session — were designed against the plan's own target model, not against what
`app-bana-runtime` actually sends. Traced end to end: the shipped Runtime logs in via the platform
`POST /api/auth/login` (never `GenericAppAuthController`) and loads its app via `GET
/appbana-studio/{t}/apps/{id}` (an `AppRoutes` route, not an entity route). Neither principal covers
this client, so S3 as originally written would 403 every real deployed app. **S3 is not fully
scoped/estimated without S3.7**, added at the end of this list — see there for the fix. (S3.7 is listed
last only because it depends on `scopedAppId` existing from S3.1/S3.3; it is not lower priority than
the other tasks.)

**Scope correction (review round 3, R3-1):** round 2's own fix for S3.7 doesn't work either.
`GenericAppAuthController.login()` defaults to authenticating against a `User` entity with an
`email`/`password` column — verified against the live `appbana_schemas` table that **zero** of the 120
real apps in this database have one (only 3 obviously-synthetic `test_users` fixtures do), and there is
no register/invite path that could ever create one. S3.7 is rewritten below: the Runtime keeps its
existing platform login unchanged, and an end-user is instead an `end-user`-role row on
`appbana_app_members` (S2, revised). This also means S3.2 rule (i) below needs no change — it was
already role-agnostic — and S3.7's old `AppRoutes` carve-out is no longer needed at all, since S2.6
(revised) already lets any membership role through `GET /appbana-studio/{t}/apps/{id}`. S3.7 now depends
on S2 (the `end-user` role + grant endpoint), not on `scopedAppId`/S3.1/S3.3.

| # | Task | Where | Est. |
|---|---|---|---|
| S3.1 | Use S2's `scopedAppId` field on `SessionData` for runtime sessions (already reserved in S1.1's model) — this remains for the optional, separate-user-table path (see S3.7), not the shipped Runtime's path (review round 3, R3-1) | `SessionService.java` | 30 min |
| S3.2 | `EntityAccessGuard` with **two entry points**, not one: (a) `check(entityKey, ...)` — parses the packed `{tenantId}_{appId}_{entityName}` key, for the `/api/{entity}` family; (b) `check(tenantId, appId, entityName, ...)` — for the two path-segmented families, which never had a key to parse. Both apply the same allow rule: (i) a Studio session's user is an `appbana_app_members` member of that `(tenantId, appId)` — **any role**, `owner`/`member`/`end-user` alike, since this guard is data-access-only, not a management check (review round 3, R3-1) — **or** (ii) a runtime session whose `scopedAppId` equals that `appId`, **or** (iii) the app is explicitly marked publicly readable (S3.5) and the request is a `GET`. Optional global admin token remains a break-glass override, evaluated only if none of the above match. | new `com.appbana.security.EntityAccessGuard` | 150 min |
| S3.3 | `GenericAppAuthController.login()`: (a) issues a real session via `SessionService.createSession(...)` with `scopedAppId` set to that app; (b) rewrite the login query to fetch-by-email and verify the password in Java (BCrypt hashes cannot be compared in a `WHERE password = ?` clause — review round 1, M5, means this is not a drop-in swap); (c) normalize the response so a nonexistent entity/app and a wrong password both produce the same generic 401 — today they're distinguishable (404 vs 401), making this endpoint a cross-tenant/cross-app existence oracle (M6). | [`GenericAppAuthController.java`](../../app-bana-service/src/main/java/com/appbana/api/GenericAppAuthController.java) | 105 min |
| S3.4 | Wire `EntityAccessGuard` into **every route in `GenericEntityRoutes`, per the S0.2 census** — the 21 existing `authEnabled` blocks (ratchet-verified, `AuthEnabledAntiPatternTest` baseline — S1 external review round 2) *and* the 11 routes in the studio-scoped and env-scoped families that have no block to replace. Auth is now always evaluated for all three families, not conditionally skipped for some. | [`GenericEntityRoutes.java`](../../app-bana-service/src/main/java/com/appbana/server/routes/GenericEntityRoutes.java) | 180 min |
| S3.5 | Add an explicit `publicRead: boolean` flag on app/entity metadata (default `false`) for the legitimate "this app's data should be publicly browsable" use case — without this, S3 would break intentionally-public apps | `AppMetadata`/`EntitySchema` + `SchemaRoutes.java` | 45 min |
| S3.6 | Tests: `CrossTenantEntityAccessTest`, `CrossAppEntityAccessTest` (App 1 member cannot touch App 2's entities in the *same* tenant) — run against **all three route families**, not just `/api/{entity}` — plus `RuntimeSessionScopedToSingleAppTest` and `LoginDoesNotLeakEntityExistenceTest` (M6) | new tests | 120 min |
| S3.7 | **(Rewritten, review round 3, R3-1 — no longer a client/login migration, now a membership grant)** Round 2's fix assumed a per-app `User`/password entity that no real app has (R3-1). The Runtime needs **no frontend change** — it keeps calling the shared platform `login()` exactly as today. What's actually needed: (a) confirm S2.6's revised `AppRoutes` list/get wiring accepts an `end-user`-role membership for `GET /appbana-studio/{t}/apps/{id}` (no new carve-out — this is S2.6, already specified there); (b) land the deferred `e2e/tests/a11y-runtime.spec.ts` authenticated-shell test (review round 3, R3-3) as the regression guard for this flow, since it's the only place an end-user login-and-load is ever exercised; (c) end-to-end verification against the real running Runtime with an `end-user`-role membership row (not `owner`), proving list/get succeed and update/delete/schema-management 403. Depends on S2 (the `end-user` role and S2.6's revised wiring), not on `scopedAppId`/S3.1/S3.3. | `AppRoutes.java` (verify only — no code change expected beyond S2.6), `e2e/tests/a11y-runtime.spec.ts` | 45 min |

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
      "wrong password" — it can no longer be used to enumerate other tenants' apps/entities. (This
      endpoint is no longer the Runtime's path — review round 3, R3-1 — but it stays live and must not
      regress while it isn't the primary focus.)
- [ ] The shipped `app-bana-runtime` — not a hypothetical scoped client — successfully logs in (via its
      existing platform login, unchanged) and loads its own app end-to-end against a running backend
      (5175 against 8080) using an `end-user`-role membership row, and the same end-user gets 403
      updating/deleting the app or loading a second app they aren't a member of (review round 3, R3-1).
      Verified in the running apps, not guard unit tests alone.

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
- Runtime end-user sessions are single-app-scoped by construction (`scopedAppId`) for the optional
  separate-user-table path (S3.1/S3.3). The shipped Runtime instead relies on `appbana_app_members`
  row-level scoping (review round 3, R3-1): an `end-user` grant is scoped to exactly one `(tenantId,
  appId)` row and carries no capability beyond that app's own entity data, so a compromised end-user
  credential for one app cannot be replayed against a sibling app either way.

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
  entry fails CI rather than relying on the next manual audit to notice; the census's known-callers
  column (review round 2) is what tells S1–S3 "guard" vs. "guard or delete" for a given route, and its
  data-preconditions column (review round 3) is what would have flagged S3.7's original design before
  it was ever estimated.
- **S3.7 specifically must be verified against the actual running `app-bana-runtime` (5175) hitting a
  real `app-bana-service` (8080), with an `end-user`-role membership row, not guard unit tests alone** —
  B1, R2-1, and R3-1 are three instances of a guard or migration that looked correct in isolation but
  was built against an assumed client or assumed data instead of the shipped reality; a unit test that
  also assumes the client/data would not have caught any of the three.
- **Principal walk-through (review round 4 meta-observation):** for any new principal type this plan
  admits (e.g. a cross-tenant app member), trace it through every guard in request order —
  `resolveIdentity` → `requireOwnTenant` → membership → `EntityAccessGuard` — not just the guard being
  added for it, before treating a design change as final. R4-1 is the one instance in four rounds where
  this specific walk was skipped, and it was the only guard-*composition* bug found so far — the other
  three rounds each found a single-guard or single-client mistake instead.
- This agent's own working notes (not a tracked repo file) record a standing project convention: once
  implementation begins, fixes must additionally be verified through the actual browser UI (Studio 5174
  / Runtime 5175), not backend/API tests alone.

### Performance
- Both new guards are a single indexed PK lookup (`appbana_app_members`) or an in-memory `SessionData`
  field check — same order of overhead as `ApprovalGuard`'s existing filtering (<5ms).
- **Deferred, not overlooked (review round 6, R6-3):** `EntityAccessGuard` rule (i) is a DB round-trip
  on every entity list/get — a hotter path than the app-management checks this plan cached `tenantId`
  on `SessionData` specifically to avoid one for. Acceptable at today's scale (<5ms, indexed); if it
  stops being acceptable, cache the membership result on `SessionData` too, invalidated on grant/revoke
  (S2.6/S2.7), rather than re-deriving it every request.

### Rollout order
**Strict serial (authoring):** S0 → S1 → S2 → S3 (S0's identity resolver and route census are
prerequisites for writing any of S1–S3's guards correctly; S3's membership check depends on S2's table).
**S1 and S2 are additionally a single deployable unit (review round 5, R5-2):** S1.2 ships its
membership-exception branch inert, so releasing S1 alone to any environment with live deployed apps
403s every real end-user until S2.6 activates it — write them in order, but release them together.
**S3 completion is a deliberate one-time access reset (review round 6, R6-2):** no `end-user` backfill
is possible or intended — S2.4 seeds `owner` rows only, and today's unrestricted "any registered user
can open any deployed app" is the vulnerability S3 closes, so no record of legitimate end-user access
exists anywhere to migrate. Every deployed app's end-user access must be re-granted explicitly by its
owner via S2.7 the moment `EntityAccessGuard` goes live. Communicate this before enabling S3 — it will
otherwise read as a bug, not the intended result.
**Parallel-safe:** S4 (independent of the others, though S4.6/S4.7 touch code S1/S3 also touch —
coordinate to avoid merge conflicts, not for correctness reasons). **Last:** S5 (the capstone test needs
S1–S3 to exist to have something real to assert; the ai-builder half can start as soon as S2's
membership model is in place).

### Documentation
- `.github/copilot-instructions.md` — new section on the enforced tenant/app isolation model (S5.4).
- `docs/features/SECURITY_FEATURES.md` — corrected in place (S4.4), not superseded by a new doc.

---

## File-level change map

**New files (backend):**
- `app-bana-service/src/main/java/com/appbana/security/TenantAccessGuard.java` (S1; membership
  exception ships inert, activated in S2.6 — review round 4, R4-1)
- `app-bana-service/src/main/java/com/appbana/security/AppMembershipService.java` (S2)
- `app-bana-service/src/main/java/com/appbana/security/EntityAccessGuard.java` (S3 — two entry points, see S3.2)
- `app-bana-service/src/main/java/com/appbana/server/routes/AppMembershipRoutes.java` (S2; gains
  the cross-tenant `listAppsForUser` route in S2.10 — review round 5, R5-3)
- Liquibase changesets: `appbana_app_members` table + backfill data migration (S2); `appbana_audit`
  `tenant_id`/`app_id` columns (S4.6)
- Route census artifact/script + regression test (S0.2, S0.3)

**Modified files (backend):**
- `SessionService.java` — `tenantId` + `scopedAppId` on `SessionData` (S1, S3)
- `AuthenticationController.java` — populate `tenantId` at login (S1)
- `AuthService.java` — new `resolveIdentity()` (S0.1, preserving priority order per R2-4); fix six
  `extractToken`→`hasRead/hasWrite` call sites in `SchemaRoutes.java`, converting all six to
  `extractServiceToken()` + `hasAdmin` now that `readToken` is retired (S1.9, M2, review round 6, R6-1)
- `SessionMiddleware.java` — delegate to `resolveIdentity()` (S0.1); fix the dead `/api/csrf/token`
  exclusion entry (S4.3)
- `SessionMiddlewareTest.java` — rewrite the tautological public-runtime/templates assertions to match
  real route shapes and post-S1 behavior (S1.12, R2-3)
- `Router.java` — fix or fence the servlet `handle(...)` overload that bypasses all middleware (S0.4)
- `AppRoutes.java` — guard wiring across the full route set, not just list/get/update/delete (S1, S2);
  gate `/api/templates` writes (S1.6); dedupe and guard both `.../full` routes instead of keeping a
  public carve-out (S1.9, R2-2); list/get accept **any** `appbana_app_members` role including
  `end-user`, update/delete/release-management require `owner`/`member` only (S2.6, revised review
  round 3, R3-1 — no separate runtime-session carve-out needed here, unlike round 2's plan). The tenant
  gate itself (`TenantAccessGuard`, not this file) is what actually admits a cross-tenant member before
  any of the above is reached — see S1.2/S2.6, revised review round 4, R4-1.
- `SchemaRoutes.java` — add ownership check to `DELETE /schema/{name}` (S1.4); fix `/api/debug/schemas`
  exclusion (S1.5)
- `SavedViewRoutes.java` — require identity; scope `DELETE_SQL` by tenant+app+owner (S1.8)
- `FileRoutes.java` — require identity on upload; stop trusting body-supplied tenant/app (S1.7)
- `AppAuthorization.java` — `isAppOwnerOrSystem` becomes membership-aware (S2.5); all 4 existing call
  sites (`ApprovalService`, `RoleRoutes`, `SchemaRoutes`, `UserRoutes`) upgrade with no code change
- `GenericEntityRoutes.java` — wire `EntityAccessGuard` across all three route families, not only the
  21 `authEnabled` blocks (S3.4); stop writing raw tokens into `actor` (S4.7)
- `GenericAppAuthController.java` — issue scoped session; fetch-by-email + verify in Java; normalize
  404-vs-401 (S3.3, S4.2). No longer the shipped Runtime's path (review round 3, R3-1) — kept as a
  hardened, optional future option for an app that wants its own separate user table.
- `UserManager.java` — BCrypt wiring + transparent rehash (S4)
- `AuditLogService.java` — write `tenant_id`/`app_id` (S4.6)
- `CsrfMiddleware.java` — remove or genuinely wire in (S4)
- `docs/features/SECURITY_FEATURES.md` — corrected (S4)
- `ApiServer.java` — startup warning when auth disabled (S1)

**Frontend:**
- `app-bana-studio/src/features/**` (session/workspace store) — verify no client-side "all tenant apps" assumption remains (S2); union in S2.10's cross-tenant apps for the switcher (review round 5, R5-3)
- `app-bana-shared/src/api-client.ts` — stop silently defaulting `tenantId` to `'default'` on a
  login/register response that omits it (S1.13, R2-6). No `runtimeLogin()` after all — review round 3
  (R3-1) found the Runtime has no per-app credential to call it with; see `GenericAppAuthController.java`
  above.
- `app-bana-studio/src/features/auth/AuthGate.tsx` — delete the two now-dead `result.tenantId ?? 'default'`
  fallbacks immediately downstream of `login()`/`register()`, unreachable once those throw instead of
  returning with a missing `tenantId` (S1.13, R10-1)
- `app-bana-runtime/src/runtime/AppRuntimeShell.tsx` — **no change** (review round 3, R3-1 supersedes
  round 2's planned `handleLogin` switch; the Runtime keeps calling the shared platform `login()`)

**Tests:**
- `e2e/tests/hardening/fixtures.ts` — same `tenantId ?? 'default'` fix as `api-client.ts` (S1.13, R3-2)
- `e2e/tests/sprint-3-crud-roundtrip.spec.ts` — same fix again; byte-identical line found by review
  round 10 in a spec outside `hardening/` (S1.13, R10-1)
- `e2e/tests/a11y-runtime.spec.ts` — land the deferred authenticated-shell test as S3.7's regression
  guard (S3.7, R3-3)

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
   admin-approval gate is wanted as a follow-up. **Now also decides S3.7's shape (review round 3,
   R3-1):** an app owner granting `end-user` membership one at a time via S2.7's existing endpoint is
   sufficient for v1 without resolving this; a true self-service "sign up and immediately use this
   deployed app" flow is a materially different, larger change and should be treated as a follow-up to
   this plan, not a blocker for S3. **Review round 4 addendum:** today's registration is tenant-per-user
   (every signup gets its own fresh tenant, confirmed live — see Review round 4), so "invite a second
   user into my own tenant" and "grant a user from another tenant access to my app" are two structurally
   different features. This plan builds only the second (S2/S3's membership model); the first remains
   unbuilt and is a separate product decision, not implied by anything in this plan.
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

*Last updated: 2026-08-01 (review round 6 incorporated — reviewer recommends closing the review; plan
ready for execution) · Author: AppBana core team · Status: DRAFT — awaiting approval before S0 begins.*
