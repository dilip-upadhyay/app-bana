# Maker-Checker Epic — Implementation Plan

**Status:** 📝 Spec approved 2026-07-26 · 🟡 In progress — C1 ✅ · C2 ✅ · C3 ✅ · C4 ✅ **done, exit criteria verified end-to-end 29-07-2026** (C4.1–C4.2 done, C4.3 superseded by C4.6, C4.6 done, C4.4 done incl. C4.4a/b/c/d/e/f, C4.5 closed as obsolete) · C5 ⬜ (v1.1-optional). The gate that closed C4 also found C4.4c — the blueprint retrieval had been returning the right templates and rendering an empty string, so the feature had never once produced its intended output despite three review rounds calling it complete — then C4.4d, where the auth fix shipped alongside it repaired one instance of a five-member defect class, then C4.4e, where the fix for *that* turned out to have the same failure mode a third time (the header was made conditional on a token nothing upstream guaranteed), and then C4.4f, where round 12 asked the next question — a present token can still be *invalid* mid-conversation, and the agent loop itself now aborts on iteration 1 with a distinct "session ended" message and SSE `auth_expired` event instead of surfacing that as a generic 3-strikes failure. Live status and commit trail: [ACTIVE_TASKS.md](../ACTIVE_TASKS.md).
**Owner:** AppBana core team
**Position in master roadmap:** Phase C of the post-Stage-4 forward plan (see [ACTIVE_TASKS.md](../ACTIVE_TASKS.md)). Depends on Phase A (Runtime UX Sprint 2) and Phase B (Complex UI Epic) completing. Runs *before* Phase D — approvals are AppBana's differentiator (the *product*), while D is enterprise packaging.
**Trigger:** Every regulated customer-facing workflow — KYC, loan origination, account opening, policy issuance, claims processing — has a mandatory two-person integrity control: a **maker** creates or edits a record and a **checker** approves it before it becomes live. AppBana today has no concept of `submitted`, `pending approval`, `approved`, or `rejected`. Without maker-checker, we cannot ship into any regulated vertical, which is the majority of the customer-onboarding market.

**Supersedes:** [`docs/specs/WORKFLOW.md`](../specs/WORKFLOW.md) for the approval-flow use case. The original workflow-engine spec is retained as historical context.

**Related active plans:**
- [Runtime UX Overhaul Plan](./RUNTIME_UX_OVERHAUL_PLAN.md) — **Phase A**, prerequisite.
- [Complex UI Plan](./COMPLEX_UI_PLAN.md) — **Phase B**, prerequisite. Master-detail and file-upload from B4/B3 underpin many maker-checker screens; B5 list views power the checker's inbox.
- [Enterprise Capabilities Plan](./ENTERPRISE_CAPABILITIES_PLAN.md) — **Phase D**, follows this epic. C5 notifications ship with a simple polling badge inside C; once D3 lands, C5 gets swapped for the durable rule-driven notification substrate. Throwaway cost: negligible (2–3 hr).
- [AI-Native UI Rebuild Plan](./AI_NATIVE_UI_REBUILD_PLAN.md) — the master rebuild plan; this epic is a post-Stage-4 extension.
- Live status: [`ACTIVE_TASKS.md`](../ACTIVE_TASKS.md).

---

## Table of Contents

1. [TL;DR](#tldr)
2. [Why we are doing this now](#why-we-are-doing-this-now)
3. [Non-goals](#non-goals)
4. [State machine](#state-machine)
5. [Data model additions](#data-model-additions)
6. [Sub-phase C1 — DB migration + role model](#sub-phase-c1--db-migration--role-model)
7. [Sub-phase C2 — Backend state machine + permissions guard](#sub-phase-c2--backend-state-machine--permissions-guard)
8. [Sub-phase C3 — Runtime: approval-aware lists, approve/reject dialog](#sub-phase-c3--runtime-approval-aware-lists-approvereject-dialog)
9. [Sub-phase C4 — AI Builder: `approvalRequired` flag](#sub-phase-c4--ai-builder-approvalrequired-flag)
10. [Sub-phase C5 — Notifications](#sub-phase-c5--notifications)
11. [Cross-cutting concerns](#cross-cutting-concerns)
12. [File-level change map](#file-level-change-map)

---

## TL;DR

Add a **first-class approval workflow** to any entity. Marking an entity `approvalRequired: true` at scaffold time causes:

1. Every row born in `DRAFT` state.
2. A `PENDING` state on submit — visible in the checker's queue, invisible to production reads.
3. `APPROVED` → row becomes visible everywhere as if it were a normal row.
4. `REJECTED` → returns to the maker with a rejection reason.
5. Every transition audited (who, when, what changed, why).

Five sub-phases:

| # | Sub-phase | Deliverable | Est. |
|---|---|---|---|
| C1 | DB migration + role model | Approval columns injected on flagged entities, `appbana_approvals` audit table, `appbana_user_roles` per-entity roles | ~5 hr |
| C2 | Backend state machine + permissions | `ApprovalService`, transition guards, permission middleware, filtered reads | ~7 hr |
| C3 | Runtime UI | Approval status pill, submit-for-approval action, checker queue page, approve/reject dialog with diff, audit-trail drawer | ~9 hr |
| C4 | AI Builder | `scaffold_app` accepts `approvalRequired` per entity; agent asks "who approves this?" when the domain implies it | ~4 hr |
| C5 | Notifications | Email/in-app notification on state transitions (opt-in v1) | ~5 hr |

**Total scope:** ~30 hours of focused work. C1 → C2 → C3 is the strict serial path. C4 can start in parallel with C2. C5 is optional for v1 launch.

---

## Why we are doing this now

Every conversation with a prospective customer in banking / insurance / lending / healthcare converges on the same question: *"Does this support maker-checker?"* Today the answer is no. That single word closes those markets.

The specific consequences of not having it:

1. **No regulated vertical adoption.** RBI / MAS / FCA / MAS-compliant onboarding demands two-person integrity by regulation, not preference.
2. **No enterprise adoption.** Even non-regulated enterprises apply SoD (Separation of Duties) as a matter of internal audit policy.
3. **Every customer builds their own hack.** Without a first-class primitive, we'd see customers adding `status = "pending"` fields and inventing broken half-workflows in every app.
4. **Audit trail is table-stakes.** Even for non-approval flows, "who created / edited / deleted this row and when" is a hard requirement.

Building this once at the platform level means every app the agent scaffolds inherits it for free.

---

## Non-goals

- **Multi-level approval chains** (maker → checker-1 → checker-2 → final signoff). v1 is single-level maker → checker. Multi-level becomes v2.
- **Parallel approvals** (any 2 of 5 checkers approve). v1 is any-one-checker.
- **Delegation** (checker A on leave, delegate to B). Manual reassignment only in v1.
- **SLA enforcement** (auto-escalate after 24h). Notifications only in v1.
- **Approval workflow editor** in the Studio. Configuration is via `approvalRequired` metadata; visual workflow builder deferred to v2.
- **Role hierarchies / groups**. Direct user-role mapping only in v1.
- **Bulk approvals**. Checker approves one row at a time in v1.

---

## State machine

```
                   ┌───────────────────────────────┐
                   │             DRAFT             │◄─────────┐
                   │  (maker editing, private)     │          │
                   └───────────────┬───────────────┘          │
                                   │ submit                    │
                                   ▼                           │
                   ┌───────────────────────────────┐           │
                   │           PENDING             │           │
                   │  (visible to checkers only)   │           │
                   └────────┬───────────────┬──────┘           │
                    approve │               │ reject           │
                            ▼               ▼                  │
                   ┌────────────────┐  ┌────────────────┐      │
                   │    APPROVED    │  │    REJECTED    │──────┘
                   │  (live, all    │  │  (returns to   │  resubmit
                   │   readers see) │  │   maker w/ ✏️)  │
                   └───────┬────────┘  └────────────────┘
                           │ edit (produces new DRAFT record)
                           ▼
                   ┌───────────────────────────────┐
                   │       DRAFT (revision N+1)    │
                   │  original APPROVED stays live │
                   │  until this revision approved │
                   └───────────────────────────────┘
```

**Key rule:** editing an already-approved row does **not** mutate the live row. It creates a new **revision** in `DRAFT` state. The current live row remains readable until the revision is `APPROVED`, at which point it atomically replaces the previous version. The previous version is retained in the audit table.

---

## Data model additions

Two mechanisms combined:

### Injected columns (per approval-required entity)

`SchemaEnricher` adds these columns automatically when an entity has `approvalRequired: true`:

| Column | Type | Purpose |
|---|---|---|
| `approval_status` | `VARCHAR(20)` | `DRAFT` / `PENDING` / `APPROVED` / `REJECTED` |
| `approval_revision` | `INTEGER` | Monotonic revision counter (1 for first record) |
| `approval_parent_id` | `INTEGER` NULL | For revisions, points at the currently-live approved row |
| `submitted_by` | `VARCHAR(255)` NULL | User ID |
| `submitted_at` | `TIMESTAMP` NULL | |
| `approved_by` | `VARCHAR(255)` NULL | Checker user ID |
| `approved_at` | `TIMESTAMP` NULL | |
| `rejection_reason` | `TEXT` NULL | Populated when `approval_status = REJECTED` |

### Platform tables

Two new tables live in the platform schema (not per-app):

```sql
CREATE TABLE appbana_approvals (
  id                UUID PRIMARY KEY,
  tenant_id         VARCHAR(255) NOT NULL,
  app_id            VARCHAR(255) NOT NULL,
  entity_name       VARCHAR(255) NOT NULL,
  row_id            VARCHAR(255) NOT NULL,
  revision          INTEGER NOT NULL,
  from_state        VARCHAR(20),
  to_state          VARCHAR(20) NOT NULL,
  actor_user_id     VARCHAR(255) NOT NULL,
  actor_role        VARCHAR(50) NOT NULL,  -- 'maker' | 'checker' | 'system'
  reason            TEXT,
  diff              JSONB,                 -- field-level before/after
  created_at        TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_appr_row ON appbana_approvals(tenant_id, app_id, entity_name, row_id);

CREATE TABLE appbana_user_roles (
  tenant_id    VARCHAR(255) NOT NULL,
  app_id       VARCHAR(255) NOT NULL,
  entity_name  VARCHAR(255) NOT NULL,
  user_id      VARCHAR(255) NOT NULL,
  role         VARCHAR(20) NOT NULL,       -- 'maker' | 'checker' | 'both'
  granted_by   VARCHAR(255) NOT NULL,
  granted_at   TIMESTAMP NOT NULL DEFAULT now(),
  PRIMARY KEY (tenant_id, app_id, entity_name, user_id)
);
```

The audit table is append-only. Row-level revisions live in the app's own entity table (with `approval_revision` and `approval_parent_id`), so a full timeline of any row is reconstructable via one join.

---

## Sub-phase C1 — DB migration + role model

**Goal:** New tables exist, `SchemaEnricher` knows how to inject approval columns, and per-entity roles can be granted.

| # | Task | Where | Est. |
|---|---|---|---|
| C1.1 | Liquibase changeset — `V15_appbana_approvals.xml` + `V16_appbana_user_roles.xml` (schemas above) | `app-bana-service/src/main/resources/db/changelog/` | 45 min |
| C1.2 | `SchemaEnricher` — when entity metadata has `approvalRequired: true`, append the 8 approval columns to the field list before `SchemaManager` writes the table | [`SchemaEnricher.java`](../../ai-builder/src/main/java/com/appbana/ai/agent/tool/SchemaEnricher.java) | 60 min |
| C1.3 | `SchemaManager` — recognise the `approvalRequired` entity-level flag, persist it in `appbana_schemas` metadata so downstream consumers know | [`SchemaManager.java`](../../app-bana-service/src/main/java/com/appbana/SchemaManager.java) | 45 min |
| C1.4 | `UserRoleService` — CRUD for `appbana_user_roles`. `grant(tenantId, appId, entityName, userId, role)`, `revoke(...)`, `getUserRoles(userId, tenantId, appId, entityName) → Set<Role>`, `isChecker(...)`, `isMaker(...)` | new `com.appbana.approval.UserRoleService` | 60 min |
| C1.5 | Bootstrap — app creator (`AppRoutes` app-create handler) automatically gets `role: 'both'` on every entity in the app they created | [`AppRoutes.java`](../../app-bana-service/src/main/java/com/appbana/server/routes/AppRoutes.java) | 30 min |
| C1.6 | `GET/POST/DELETE /api/apps/{appId}/roles` endpoints for role management (auth-guarded: only admins of an app) | new `RoleRoutes.java` | 60 min |

### Exit criteria — C1

- [ ] A new entity with `approvalRequired: true` gets the 8 approval columns automatically at create time.
- [ ] The app creator can grant `checker` role to another user via API.
- [ ] Attempting to grant a role on an entity that doesn't exist returns 404, not silent success.
- [ ] Backend regression tests confirm existing non-approval entities are unchanged.

---

## Sub-phase C2 — Backend state machine + permissions guard

**Goal:** Row writes and reads on approval-required entities respect the state machine and the user's role.

| # | Task | Where | Est. |
|---|---|---|---|
| C2.1 | `ApprovalService` — the state machine. Methods: `submit(row)`, `approve(row, checkerId)`, `reject(row, checkerId, reason)`, `resubmit(row)`. Enforces valid transitions, writes to `appbana_approvals`, updates row columns atomically in one transaction | new `com.appbana.approval.ApprovalService` | 120 min |
| C2.2 | `ApprovalGuard` — middleware/interceptor on `GenericEntityRoutes`. On write, checks role; on read, filters by state (non-checkers see only `APPROVED` rows they created, plus their own `DRAFT` / `REJECTED` rows; checkers additionally see `PENDING`) | new `com.appbana.approval.ApprovalGuard` + wire into [`GenericEntityRoutes.java`](../../app-bana-service/src/main/java/com/appbana/server/routes/GenericEntityRoutes.java) | 90 min |
| C2.3 | Revision handling — `PUT /api/{entity}/{id}` on an `APPROVED` row does NOT overwrite; it clones the row into a new `DRAFT` revision with `approval_parent_id = originalId`. On approve, the new revision replaces the parent atomically | `ApprovalService` + `GenericEntityRoutes` | 90 min |
| C2.4 | New action endpoints: `POST /api/{entity}/{id}/submit`, `POST /api/{entity}/{id}/approve`, `POST /api/{entity}/{id}/reject` (body: `{reason}`) | `GenericEntityRoutes` | 60 min |
| C2.5 | Diff computation — when submitting a revision, compute field-level diff vs. the parent and store in `appbana_approvals.diff` | `ApprovalService` | 45 min |
| C2.6 | Query endpoint `GET /api/{entity}/{id}/audit` — returns full transition history from `appbana_approvals`, most recent first | `GenericEntityRoutes` | 30 min |
| C2.7 | Filter query params `?_approvalStatus=PENDING` — allowed only for users with checker role on that entity | `GenericEntityRoutes` | 30 min |
| C2.8 | Unit + integration tests — every transition, every permission denial, revision-replacement atomicity | `ApprovalServiceTest`, `ApprovalGuardTest`, `RevisionFlowIntegrationTest` | 90 min |

### Exit criteria — C2

- [x] Maker cannot approve their own row (403).
- [x] Non-checker cannot see `PENDING` rows via `?_approvalStatus=PENDING` (403) — including the
      side door `?filter=approval_status:PENDING`.
- [x] Approving a revision atomically replaces the live row; the previous version is retained and queryable via audit.
- [x] `GET .../records/{id}/approvals/audit` returns the full state-transition timeline,
      most recent first.
- [x] Editing a live `APPROVED` row never mutates it; it produces a `DRAFT` revision.
- [x] Concurrent approve + reject on the same row: one wins, the other returns 409 Conflict.
      *(`SELECT ... FOR UPDATE` serialises them; the loser gets an `ApprovalConflictException`,
      a subtype of `IllegalStateException` that `ApprovalRoutes` maps to 409. Authorization
      failures keep their distinct 403.)*

### Implemented behaviour — revisions (C2.3)

**Edit of a live `APPROVED` row (`PUT`, all three route families):**

| Current state of target row | Result |
|---|---|
| `PENDING` | `400` — a row under review cannot be edited |
| `DRAFT` / `REJECTED` | edited in place, reset to `DRAFT`, `rejection_reason` cleared. Revision counter untouched — `submitForApproval` owns bumping it on resubmit |
| `APPROVED` | live row untouched; a **separate** `DRAFT` row is written with `approval_parent_id = <liveId>` and `approval_revision = n+1`. Response: `{"updated":0,"revision":true,"revisionId":…,"parentId":…,"approvalStatus":"DRAFT","approvalRevision":n+1}` |
| `APPROVED` with a revision already `PENDING` | `409` — one open revision per row |

A second edit while a `DRAFT`/`REJECTED` revision is open **refreshes that revision** rather than
creating another row, so form autosave cannot spam the table.

**Approving a revision** (`ApprovalService.approveRecord`, single transaction):
1. `SELECT … FOR UPDATE` the revision, then the parent.
2. Copy every business column from revision → parent. Business = all columns except the PK,
   `created_at`, and the eight approval columns.
3. Mark the parent `APPROVED` at the revision's revision number; null out `approval_parent_id`.
4. `DELETE` the revision row.
5. Write an audit entry against the **parent** row id whose `diff` carries the complete
   pre-merge snapshot (`before`) and the merged values (`after`).

> **Deviation from the original plan — no `superseded_by` column.**
> The plan said the parent should be soft-marked `superseded_by`, which only makes sense if the
> revision's id becomes the new canonical id. Phase B.H4 added real PostgreSQL `FOREIGN KEY`
> constraints for reference fields, so re-pointing the canonical id would break every FK aimed at
> the parent. Instead the **parent id is preserved** and the revision is folded into it. The
> previous version stays recoverable from `appbana_approvals.diff.before`, which satisfies
> "the previous version is retained in the audit table". Consequently the injected-column list
> stays at the eight columns documented above.

If a revision's parent has been deleted meanwhile, the dangling `approval_parent_id` is nulled and
the revision simply becomes the live row (logged as a WARN) — data is never dropped.

~~**Legacy tables** created before C2.3 have no `approval_parent_id` column. `applyApprovalPutGuard`
detects this, logs a WARN and falls back to the old in-place edit rather than failing.~~

**C4.6c — retraction: that fallback no longer exists and, since C4.6, could not execute.** The
detection it describes was a `schema.getFields()` probe for `approval_parent_id`, which C4.6 replaced
with `schema.isApprovalRequired()` — tautologically true by the time control reaches that line, so the
branch was dead and the WARN unreachable. The dead code is now removed. Legacy tables are handled
earlier and more reliably instead: `SchemaManager.syncApprovalColumns` materialises the column on any
schema save, so a pre-C2.3 table is migrated rather than special-cased at write time. There is no
in-place-edit fallback for an APPROVED row, and there must not be one — that fallback was the
data-integrity failure C4.6 fixed.

### Implemented behaviour — `?_approvalStatus=` (C2.7)

`GET /api/{entity}`, `GET /appbana-studio/{tenantId}/apps/{appId}/{entity}` and
`GET /api/{tenantId}/apps/{appId}/env/{env}/{entity}` accept `?_approvalStatus=DRAFT|PENDING|APPROVED|REJECTED`.

- Entity has no approval workflow → `400` (silently ignoring it would return the whole table to a
  caller who asked for a subset).
- Value outside the four states → `400`.
- `PENDING` → caller must hold the `checker` role on that entity, or own the app → otherwise `403`.
- The generic `?filter=approval_status:…` parameter is routed through the **same** check, so it
  cannot be used to enumerate the checker queue.

Revision rows are deliberately **not** hidden from unfiltered list queries — the state machine above
shows parent and revision coexisting, and the maker needs to see their pending edit.
`?_approvalStatus=APPROVED` is the supported way to get live-rows-only.

---

## Sub-phase C3 — Runtime: approval-aware lists, approve/reject dialog

**Status: ✅ Complete 2026-07-28** (except the Playwright round-trip, tracked below).

**Goal:** Every UI surface reflects approval state. Makers see their queue, checkers see theirs, approve/reject is a single-click action.

| # | Task | Where | Est. |
|---|---|---|---|
| C3.1 | `ApprovalStatusPill.tsx` — colored badge component. Draft (slate), Pending (amber), Approved (green), Rejected (red). Renders on every entity whose schema has approval columns | new component; auto-wire into `StudioTableLive.tsx` cell renderer | 45 min |
| C3.2 | Form footer — for approval-required entities, replace `Save` button with `Save as draft` + `Submit for approval` buttons. `Submit` disabled until required fields valid | modify `FormActions.tsx` (from Runtime UX Sprint 1) | 60 min |
| C3.3 | Checker queue page — auto-generated when an entity is `approvalRequired`. Route: `/queue/{entityName}`. Lists all `PENDING` rows scoped to entities where current user has `checker` role. Sortable by `submitted_at`. Row click → approval detail view | new `CheckerQueuePage.tsx` + new PageMeta layout `checker_queue` | 90 min |
| C3.4 | Approval detail view — side-by-side diff (old vs. new) for revisions, or full row view for new records. Approve button (green) + Reject button (red). Reject opens dialog for reason (required) | new `ApprovalDetail.tsx` + `RejectDialog.tsx` | 90 min |
| C3.5 | Audit trail drawer — right-side slide-out on any approval-required row's detail page. Timeline of all `appbana_approvals` entries with actor avatar, action, timestamp, reason, diff (collapsible) | new `AuditDrawer.tsx` | 90 min |
| C3.6 | Maker "My Drafts" and "Needs Rework" system views — auto-added as saved views (leverages B5 SavedViews) on any approval-required list page | integrate with `SavedViewsBar.tsx` | 45 min |
| C3.7 | Global "Pending my approval" badge in top nav — small pill with count next to user menu, click → checker queue router page (lists all entities where user has pending items) | modify `AppRuntimeShell.tsx`; new `PendingCountService.tsx` polling every 30s | 60 min |
| C3.8 | Toast integration — on submit/approve/reject success, fire toast (Sprint 1 wiring already exists) | inline | 15 min |

### Exit criteria — C3

- [x] Every list page for an approval-required entity shows the status pill on every row.
- [x] Maker sees `Save as draft` + `Submit for approval`; checker sees `Approve` + `Reject` on a pending row.
- [x] Reject requires a reason (no submit without text).
- [x] Checker queue page ranks by oldest-submitted first.
- [x] Audit drawer shows every state transition with actor + timestamp.
- [x] Global pending-count badge updates within 30s of a submit.
- [ ] Playwright: full round-trip (maker A submits → checker B rejects with reason → A resubmits → B approves → row visible in live list) passes.

### Deviations from plan — C3

| Task | Planned | Shipped | Why |
|---|---|---|---|
| C3.3 | Route `/queue/{entityName}`, new PageMeta layout `checker_queue` | Shell state, no route, no PageMeta | The queue has no page metadata behind it and its visibility is per-user, so synthesising a PageMeta would mean inventing metadata and filtering it back out per caller. The runtime has no router, so no URL was lost. |
| C3.4 | `ApprovalDetail.tsx` with side-by-side diff | Approve/reject in place in the queue; no diff view | The diff needs a pre-edit snapshot that `appbana_approvals.diff` does not yet populate. Deferred rather than faked. |
| C3.6 | "My Drafts" | "Drafts" | Entity tables have no `created_by`; the workflow only records `submitted_by`, which is null for a never-submitted draft. Scoping by submitter would hide most drafts; calling an unscoped list "mine" would be undetectably wrong. "Needs rework" *is* scoped, because a REJECTED row must have been submitted. |
| C3.7 | `PendingCountService.tsx` | `usePendingCounts.ts` + new `?countOnly=true` on the pending endpoint | A polling badge that reused the queue endpoint would ship up to 500 rows per entity per user per tick to read a number. |

### Defects found and fixed during C3

- `GET /api/tenants/{t}/apps/{a}/roles` had **no authentication**, while POST and DELETE on the same path were guarded by C1.9 — any caller could read any user's maker/checker grants in any tenant (`89aac8e`).
- `fetchPendingApprovals` and `fetchApprovalAudit` returned the `{count, records}` / `{count, history}` envelope while typed as arrays. The checker queue therefore rendered as permanently empty (`943c18e`).
- The pending queue was ordered `SUBMITTED_AT DESC`, contradicting the exit criterion above and starving the longest-waiting record (`84561a5`).
- `/api/users/me` matches `SessionMiddleware.ENTITY_API_PATTERN` as `entity=users, id=me`, which skipped session validation and would have 401'd every caller regardless of token (`d5f5247`).
- A failed submit-for-approval after a successful insert was reported as "Save failed", inviting the user to retype a record that was already saved as a draft (`8560ac0`).

---

## Sub-phase C4 — AI Builder: `approvalRequired` flag

**Goal:** The agent generates approval-required entities from natural language, without the user needing to know the platform primitive exists.

| # | Task | Where | Est. |
|---|---|---|---|
| C4.1 | `ScaffoldAppTool` + `CreateEntityTool` parameter schemas accept `approvalRequired: boolean` per entity | [`ScaffoldAppTool.java`](../../ai-builder/src/main/java/com/appbana/ai/agent/tool/ScaffoldAppTool.java), [`CreateEntityTool.java`](../../ai-builder/src/main/java/com/appbana/ai/agent/tool/CreateEntityTool.java) | 45 min |
| C4.2 | Agent prompt — when the domain implies approval (customer onboarding, loan application, KYC, expense claim, purchase order, policy issuance, employee onboarding, contract), the agent's Phase 1 spec proposal includes: *"This app will use a two-person approval flow — one team member creates a customer profile, another approves it before it goes live. Sound right?"* | [`AdvancedPromptEngine.java`](../../ai-builder/src/main/java/com/appbana/ai/llm/AdvancedPromptEngine.java) + explicit RAG few-shot | 60 min |
| C4.3 | `SchemaEnricher` — validate that entities flagged `approvalRequired` don't also request incompatible custom `status` fields (auto-resolve: rename the user's status to `workflow_status` and inject `approval_status` cleanly) | [`SchemaEnricher.java`](../../ai-builder/src/main/java/com/appbana/ai/agent/tool/SchemaEnricher.java) | 45 min |
| C4.4 | Two new RAG domain templates — `customer_onboarding_with_approval`, `loan_origination_with_approval` — showing the agent what an approval-required schema + page set looks like | ~~`builder-database/`~~ → [`AppBanaSchemaLoader.java`](../../ai-builder/src/main/java/com/appbana/ai/knowledge/AppBanaSchemaLoader.java) + [`AppBanaPromptEnhancer.java`](../../ai-builder/src/main/java/com/appbana/ai/knowledge/AppBanaPromptEnhancer.java) | 60 min |
| C4.5 | Auto-generate checker queue page — when `scaffold_app` sees `approvalRequired: true` on any entity, `GeneratePageTool` also emits a `checker_queue` page for that entity | [`GeneratePageTool.java`](../../ai-builder/src/main/java/com/appbana/ai/agent/tool/GeneratePageTool.java) | 30 min |
| C4.6 | **Added 2026-07-30 by review of `5ce6bb4`.** Give the `approvalRequired` ⇒ eight-physical-columns invariant a single owner: inject in `SchemaManager` (the chokepoint every writer of the flag passes through), delete injection from `SchemaEnricher`, and fix the consumers that inferred approval capability from `schema.getFields()` | [`SchemaManager.java`](../../app-bana-service/src/main/java/com/appbana/SchemaManager.java), [`EntityCrudService.java`](../../app-bana-service/src/main/java/com/appbana/service/EntityCrudService.java), [`GenericEntityRoutes.java`](../../app-bana-service/src/main/java/com/appbana/server/routes/GenericEntityRoutes.java), [`SchemaEnricher.java`](../../ai-builder/src/main/java/com/appbana/ai/agent/tool/SchemaEnricher.java) | — |

**Progress (2026-07-30):** C4.1 ✅ · C4.2 ✅ · C4.3 ⚠️ superseded by C4.6 · C4.6 ✅ · C4.4 ✅ · C4.5 ❌ closed as obsolete (see deviations below). C4.6 was sequenced before C4.4 deliberately — see the C4.3/C4.6 deviation.

### C4.4 deviation — the stated location was inert, and so was the delivery path

~~The templates were to be authored as `customer-onboarding-with-approval.json` and
`loan-origination-with-approval.json` under `builder-database/`.~~ **Both halves of that were wrong,
and each would have shipped an artifact that looks correct and reaches nothing:**

1. **`builder-database/` is not loaded by any code.** All 17 repo-wide references to it are docs,
   READMEs, or self-references. The live loader is `AppBanaSchemaLoader.loadDomainTemplates()`, where
   the eight existing domain templates are Java-embedded and registered under category
   `domain-template`. Two new JSON files would have been a verbatim repeat of C4.1 — a parameter
   accepted, apparently correct, silently dropped before the consumer.
2. **Even correctly located, the content could not reach the model.** Domain templates carry the wire
   type `"domain-template"`, which has no `SchemaDefinition.SchemaType` constant, so
   `getTypeAsEnum()` returns `null` and `AppBanaPromptEnhancer.buildSchemaContext()` discarded every
   template at its `.filter(s -> s.getTypeAsEnum() != null)` before any render branch ran. The only
   trace reaching the prompt was each template's search keywords via `buildExamplesSection` — which
   are echoes of the user's own query and carry no structure. `KnowledgeBaseService` also exposes
   `getDomainExamples()`, purpose-built for this and with **zero callers**, and `SearchKnowledgeTool`
   returns only `name`/`type`/`description`, dropping the `entities` metadata entirely.

**Delivered instead:** the two blueprints in `AppBanaSchemaLoader`, an `addDomainTemplate` overload
carrying `approvalRequiredEntities`, that signal appended to `buildSearchableText()` so it is in the
**embedding** (a query like "loan approval workflow" must outrank the plain `finance` template), and
`DomainBlueprintPrompt.render()` invoked from `AiAgent.think()` so entity structure and the flag
actually reach the model.

#### C4.4a — the first fix rendered into a prompt nobody builds

Review of `c7a5adb` found the tests drove `AppBanaPromptEnhancer.enhancePrompt`, which nothing calls.
Tracing it out revealed the delivery path was **entirely severed**, not merely filtered:

| Hop | Status |
|---|---|
| `AdvancedPromptEngine.buildPrompt(...)` | **zero call sites repo-wide** |
| `AiChatController` | takes `AdvancedPromptEngine` as a constructor parameter it never stores |
| `AppBanaPromptEnhancer.enhancePrompt` | reachable only from `buildPrompt` — therefore dead |
| `AiAgent.knowledgeBase` | assigned by `AiServer:189` via `withKnowledgeBase(...)`, **never read** |
| `AiAgent.think()` | the live prompt — contained no RAG of any kind |

So the C4.4 renderer sat on a branch no request executes, and the review's own "the feature genuinely
works, both entry points funnel into `buildSchemaContext`" was optimistic: there is no second entry
point. `buildSchemaContext` has exactly one caller, which has exactly one caller, which has none.

**Fixed by** extracting `DomainBlueprintPrompt` and calling it from `AiAgent.think()` through the
already-wired-but-unread `knowledgeBase` field, via `getDomainExamples()` — which was itself a
zero-caller method built for exactly this. Two dead paths became live; `AppBanaPromptEnhancer` now
delegates to the same renderer and is documented as dead.

The tests now capture the literal string passed to `llmService.chatWithJsonMode(...)`. Mutation check:
restoring `knowledgeBase` to unread turns exactly the three prompt-content tests red and leaves the
five layer-independent ones green.

**Still dead, deliberately not fixed:** `AdvancedPromptEngine.buildPrompt` /
`AppBanaPromptEnhancer.enhancePrompt` (delete or wire in a later task), and `SearchKnowledgeTool`
returning only `name`/`type`/`description`.

**Lesson:** before choosing the layer to test at, grep for callers of the entry point — the same
query that produced this task's best insight (`git grep builder-database -- '*.java'` → empty) would
have returned empty for `buildPrompt` in the same second. Grep for **call sites**, not for the
symbol: three files (`ToolRegistry:97`, `ConversationSpec:14`, `DialogueManager:161`) carry javadoc
reading `{@code AiAgent.buildAgentPrompt()}` for a method with zero callers. A symbol search returns
those comments and they read as confirmation. Documentation is written by the producer and stays
locally coherent after the chain is cut — it is evidence about intent, never about reachability.

#### C4.4b — the retrieval was paid for once per iteration

C4.4a wired the lookup into `think()`, which runs once per agent iteration (up to 10). `userMessage`
is fixed for the whole loop and `getDomainExamples` has no cache, so an n-iteration request bought n
identical OpenAI embedding calls and n identical Qdrant searches to build n identical strings. Not a
correctness bug — the prompt was right — but a paid network round-trip multiplied by the iteration
cap, on the hot path.

**Fixed by** computing the section once in each entry point, after the pattern-match short-circuit
(so a pattern-matched request pays nothing), and passing it into `think()`. Guarded by
`theBlueprintLookupIsPaidForOncePerRequestNotPerIteration`, which drives a real 2-iteration loop and
asserts `times(1)` on the knowledge base and `times(2)` on the LLM — a claim about the consumer, so
it fails if the lookup is re-inlined anywhere per-iteration. Mutation: re-inlining into `think()`
turns exactly that one test red.

The blueprints deliberately declare **no** approval columns — only the flag. `SchemaManager` owns
column injection (C4.6); a template that declared them would converge on the same physical table via
dedupe but would teach the agent the ownership-ambiguous shape C4.6 removed.

#### C4.4c — the blueprint retrieval worked and rendered nothing

Running the exit-criteria gate for the first time produced a Phase 1 reply with no maker-checker in
it at all, on the query the feature was designed for. One log line separated the two possible
causes:

```
[AGENT] Domain blueprints: matched=[customer_onboarding_with_approval, loan_origination_with_approval] rendered=0 chars
```

Retrieval was perfect — both maker-checker blueprints, ranked #1 and #2, out of ten domain
templates. The render was empty.

`KnowledgeBaseService.searchResultToSchema` reads `schemaId`, `schemaName`, `description`,
`schemaType`, `examples` and `schemaMetadata` back out of the Qdrant payload. It never read
**`category`** — the very key the filtered search matches on, written one method away by
`indexSchemaInternal`. So *every* schema returned by *every* search in the system had
`category == null`, and `DomainBlueprintPrompt.render`, which selects domain templates by category,
discarded all of them and returned `""`. Nothing threw. The prompt simply had nothing in it.

**Fixed** by reading `category` back. Post-fix the same query renders 2269 chars and the Phase 1
reply proposes the two-person flow in business English.

**Why nine passing tests missed it:** `ApprovalDomainTemplateTest` mocks `getDomainExamples` and
hands the agent `SchemaDefinition`s it builds itself — and *its fixtures set `category`*. The fixture
was more complete than production. This is the same shape as the C4.6 finding ("a fixture that
supplies the invariant under test cannot witness that invariant being broken"), recurring one layer
up: there, fixtures declared columns production was supposed to inject; here, a fixture populated a
field production was supposed to read back.

The new guard is structural rather than another assertion about a hand-built object.
`KnowledgeBaseRoundTripTest` runs a real shipped blueprint through the real indexing path, captures
**the exact metadata map the writer handed the vector store**, feeds that same map back through the
real search-result conversion, and asserts the rendered section is non-empty. Producer and consumer
share one map, so a key written by one and ignored by the other cannot survive — whatever the key is
called. Mutation: removing the `setCategory` line fails it with
`expected: <domain-template> but was: <null>`.

Two further defects surfaced from the same gate run, both invisible to unit tests:

- **`GeneratePageTool` never sent the session token.** It was the only *writer* in the scaffold chain
  building its request without the `Authorization: Bearer` header that `CreateAppTool` and
  `CreateEntityTool` both send. App creation and all three entity creations succeeded, then every
  page 401'd — a half-built app rolled back on an auth error dressed up as a modelling one. It had
  been latent because nothing previously drove the scaffold path with a real token.
  **See C4.4d below: fixing only the writer was itself an incomplete fix.**
- **Deploy-time foreign-key failure** (`foreign key constraint ... cannot be implemented`) on
  `reference` columns whose type does not match the parent's PK. Pre-existing, unrelated to
  maker-checker, and out of scope for C4 — logged here because the gate is where it surfaced.

**Lesson:** three rounds of review agreed C4.4 was code-complete, and the feature had never once
produced its intended output. Every hop was individually verified — the blueprint is indexed, the
metadata round-trips, the section is injected, the lookup is hoisted — and the chain was still
severed, because "the payload contains `category`" and "the reader asks for `category`" were checked
by different people at different times and never against each other. The end-to-end gate was not
a formality after the unit tests; it was the only thing that ran the whole chain.

#### C4.4d — the auth fix stopped at writers; five requests were unauthenticated

Review round 7 accepted C4.4c and rejected the auth fix shipped alongside it. The claim
*"`GeneratePageTool` was the only writer missing the header"* was true as written, and the wrong
frame: the defect class is *a tool issuing a backend request without the session token*, and
scoping the sweep to writers left the read tools untouched. This is the round-2 shape again
(`insertRecordLegacy` fixed, `insertBatch` missed) — one instance repaired, siblings not enumerated.

Enumerating every `HttpRequest.newBuilder()` in the tool package found **five** unauthenticated
sites, not the three the review named:

| Tool | Effect of the missing header |
|---|---|
| `list_apps` | 401 on every invocation |
| `list_pages` | 401 on every invocation |
| `list_workflows` | 401 on every invocation |
| `list_entities` (app-context branch) | 401; the `/schema` fallback branch in the same file *does* authenticate |
| `get_entity_details` (app-context branch) | 401 **silently swallowed** |

The two the review missed are the more interesting ones, because each file contains a *second*
request that does send the token. A per-file question — "does this tool authenticate?" — answers
yes for both while the branch the agent actually takes when an app is selected 401s.

`get_entity_details` is the worst case and the only one that returns a wrong answer rather than an
error: its 401 fails the `statusCode() == 200` check and falls through to the global `/schema`
lookup, which authenticates correctly but searches by the bare entity name with no
`{tenantId}_{appId}_` prefix — so it misses too, and the tool reports "entity not found" for an
entity that demonstrably exists. The other four surface as a failed tool call, which burns an agent
iteration and increments `consecutiveFailures` toward the abort-at-3, so the user sees the agent
give up vaguely instead of an auth error.

**Why the gate missed it:** all three C4 exit criteria are create/scaffold flows. None of them asks
*"what apps do I have?"* — the same reason the token defect stayed latent in `GeneratePageTool`
until something finally drove that path.

**The guard is two tests, because neither is sufficient alone** —
[`ToolAuthHeaderTest`](../../ai-builder/src/test/java/com/appbana/ai/agent/tool/ToolAuthHeaderTest.java):

1. `everyDrivableToolPutsTheTokenOnTheWire` stands up a real `HttpServer`, drives the five tools,
   and asserts every request it *received* carried the header. This is the seam test: it proves the
   header reaches the socket, which no amount of source inspection can. It also asserts each tool
   made at least one request, so it cannot pass vacuously for a tool that fails before calling out.
2. `noToolBuildsARequestWithoutAttachingTheToken` scans the tool sources for every
   `HttpRequest.newBuilder()` and requires an `Authorization` header within the same builder. This
   covers the nine tools that need an LLM or a validator to drive and so cannot be exercised
   behaviourally — which is exactly where tool number fourteen would reintroduce this.

Both mutations were run. Neutering `ListAppsTool`'s header guard (leaving the source text intact)
fails test 1 only; deleting `DeployAppTool`'s header — a tool test 1 cannot reach — fails test 2
only, naming the file and line. Neither test subsumes the other.

Live-verified against the running stack on the exact route `ListAppsTool` builds:
`GET /appbana-studio/{tenant}/apps` returns `401 {"error":"Unauthorized","message":"Missing session
token"}` with no header and `200` with the app listed with one.

**Lesson:** the fix for a seam defect has the same failure mode as the defect. C4.4c was found by
asking "which components disagree?"; the accompanying auth fix was scoped by asking "which component
was broken?" — and repaired one instance of a class with five members. When a defect is a missing
call at a boundary, the unit of repair is *every crossing of that boundary*, enumerated
mechanically, and the guard belongs on the boundary rather than on the instance.

#### C4.4e — the header was conditional on a token nothing guaranteed

Review round 11 rejected C4.4d. The same lesson applied one level up: C4.4d enumerated mechanically,
but over a hand-picked set. **Mechanical enumeration over a hand-picked scope is still a hand-picked
scope; it just feels rigorous.**

**The blocker — a guard checked in one direction only.** Thirteen of the fourteen sites attach the
header inside `if (token != null && !token.isEmpty())`. Every check confirmed that a *present* token
reaches the socket; nothing confirmed a token is ever present. It was not guaranteed anywhere:
`AgentContext.create` accepted `null`; both `AiChatController` and `AgentStreamController`
null-coalesce *every other field* to a default and pass `token` through raw; `ChatPane.tsx` sends
`token: token ?? undefined`. A tokenless chat request was therefore accepted, opened `200`, and every
tool inside it 401'd — **the pre-C4.4d behaviour reproduced symptom for symptom, through the fix.**
Neither guard could see it: test 1 always supplied a good token, and test 2 only required the literal
text `header("Authorization"` to appear. `ScaffoldAppTool.rollback` was worse still — it attached the
header *unconditionally*, so a tokenless rollback sent the literal string `Bearer null`.

**Fixed at the door, not at the sites,** in three layers so that no single omission is load-bearing:

| Layer | Change |
|---|---|
| Type | `AgentContext`'s **compact canonical constructor** rejects a null/blank token. Invalid state is now unrepresentable, and the check is inherited by `withVariable` and by any construction path added later — unlike a check in `create`, which the next author routes around by calling the constructor. |
| Boundary | Both controllers return `401 {"error":"Unauthorized"}` before doing any work — in `AiChatController` *ahead of the pattern-executor short-circuit*, which also creates apps. A 200 cannot trigger the Studio's `appbana:auth:expired` recovery; a 401 can. |
| Site | `ScaffoldAppTool.rollback` now uses the same conditional as the other thirteen. |

**The masking, deleted.** Round 7 removed one *trigger* of `get_entity_details`' silent fall-through
(the 401) and left the fall-through itself, so 500, 502, 403 and a mid-restart connection reset all
still produced "entity not found" for an entity that exists. It now returns an explicit transport/
authorization error on any non-200, and — critically — returns `Entity 'X' does not exist in app 'Y'`
instead of falling through on a successful-but-empty lookup. The global `/schema/{bareName}` fallback
is reachable only when no app is selected, which is the only case where it can succeed: app-scoped
keys are `{tenantId}_{appId}_{entityName}`, so for a selected app that fallback was *by construction*
incapable of finding anything. It existed solely to convert errors into a plausible wrong answer.

**The guard covered a directory, not a boundary.** `Files.list` over `agent/tool/` is not "every
ai-builder request to the backend": there are **25** `HttpRequest.newBuilder()` sites across 17 files
under `com/appbana/ai`, and the old scan saw 22 in 14. `AiAgent.loadEntitySummary` (line ~1116) calls
the backend and authenticates correctly today, but was invisible to both tests — as a tool added in a
subpackage would have been. The scan now walks `com/appbana/ai` recursively and excludes `llm/`
**by name, with the reason in a javadoc**: `OpenAiLlmService` and `GeminiLlmService` call third-party
APIs with their own provider keys, so a session token there would be meaningless. That is a decision
on the record, not an artefact of where the walk happened to start.

**Third test, and all three mutations re-run** —
`aTokenlessContextCannotProduceARequest` feeds `null`, `""` and `"   "`, and when construction
succeeds it *drives `ListAppsTool` and reports the leaked request* rather than merely noting a missing
exception (which is why it is a `try`/`fail` and not `assertThrows` — the consequence is the point):

| Mutation | Result |
|---|---|
| Remove the `AgentContext` constructor throw | test 3 only — `AgentContext accepted a blank token (null) and list_apps then sent 1 request(s), the first with Authorization=null` |
| Delete `DeployAppTool`'s header (a tool test 1 cannot drive) | test 2 only — `[DeployAppTool.java @ line 76]` |
| Run test 1 with `AgentContext.create(..., null)` | errors at the *construction* line, before any tool executes — it cannot reach the socket at all |

No two of the three tests subsume each other.

**Lesson:** a guard that only ever sees valid input verifies nothing about invalid input. Ask of every
guard: *what makes its precondition true?* — and if the answer is "nothing", the guard is decoration.
Prefer making the invalid state unrepresentable in the type over validating at one entry point, since
the type is inherited by paths that do not exist yet. And when removing a defect, **delete the masking
that hid it, not just the trigger that exposed it** — the thread running through C4.4c, C4.4d and
C4.4e is that each fix repaired the defect and left the masking in place, which is precisely why the
next one stayed invisible.

#### C4.4f — presence vs validity: a token can go bad mid-conversation, after the door already let it through

Review round 12 accepted C4.4e's "is a token present" invariant and asked the next question: a *present*
token can still be *invalid* — expired or revoked — at any point in an already-running conversation. The
SSE stream opens with a 200 (the token was present and, at that instant, valid), and a tool call several
iterations later gets a 401 from the backend. `authedFetch()`'s existing `appbana:auth:expired` recovery
fires on the *opening* request's transport status; it cannot see a 401 discovered inside a stream that
already returned 200. Before this round, that 401 was indistinguishable from any other tool failure: it
burned an iteration, incremented `consecutiveFailures`, and — if it kept happening for 3 iterations — the
agent gave up with "I'm having trouble, could you rephrase?", which is actively misleading for a dead
session.

**Fix — an unchecked exception carries the signal through helpers that can't change their return type.**
`ToolResult` gained `authFailure` (Lombok `isAuthFailure()`) and a `ToolResult.authError(name, msg)`
factory. New `BackendAuthException` (unchecked) is thrown at the exact point any of the 14 tools detects
a 401, however deeply nested behind a private helper — some of those helpers return `boolean` or a
nullable `Map` and already use that return shape to mean "not found"/"other failure", so threading a new
parameter through every signature would have been a much larger change. Each tool's outer `execute()`
catches `BackendAuthException` **before** its generic `catch (Exception e)` and returns
`ToolResult.authError(...)`.

Both `AiAgent` loops (`process`, `processWithStream`) check `result.isAuthFailure()` per tool result and,
if set, abort **immediately after iteration 1** — bypassing the `consecutiveFailures>=3` gate entirely —
with *"Your session has ended, so I can't continue with that request. Please sign in again to keep
working."*, never the generic rephrase message. `StreamEmitter` gained a 6th event, `authExpired(message)`
→ `{event: "auth_expired", data: {message}}`, which `ChatPane.tsx` maps to
`window.dispatchEvent(new CustomEvent('appbana:auth:expired'))` — the same recovery `AuthGate.tsx` already
listens for, now reachable from inside an open stream, not just from the opening request.

**Hardest file:** `BatchUpdateEntitiesTool`'s per-update loop deliberately treats an auth failure
differently from every other exception in that loop — it does not append to `failedUpdates` and continue
to the next item; it returns immediately. A dead token fails every remaining update identically, so
finishing the loop would only produce N confusing per-item 401 strings instead of one clear signal.

**An unrelated orphan-app bug, found while touching `ScaffoldAppTool` for this round:** when
`context.appId()` was already set (a caller re-using an id), the tool reused it without checking an
`appbana_apps` row actually existed for that id, so entities/pages could be created under an id with no
backing app record. Fixed with `appRowExists()` (GET the app; 401 also throws `BackendAuthException`) and
`createAppRowWithId()` (POST with `id` forced to the caller's id — the one place this class of tool must
*not* mint a fresh UUID).

**Regression tests** (`AiAgentTest`): `testAgentLoop_AuthFailureAbortsOnFirstIterationNotThird` and
`testAgentLoopStream_AuthFailureAbortsAndEmitsAuthExpiredEvent` register a tool that always returns
`ToolResult.authError(...)`, then assert the LLM was called exactly **once** (not up to 3 times), the
returned message contains "session" and never "rephrase", and — for the streaming variant — that an
`auth_expired` event was actually emitted alongside `done`.

**Meta-lesson (carried forward from C4.4c/d/e):** the invariant that gets enforced first is the one that
is easy to state in one line with no I/O — "a token must be present" is checkable locally, for free.
"A token must be *valid*" is the property the system actually depends on, and validity lives in another
service, discoverable only by making the call and seeing what comes back. Presence-checks are seductive
because they are cheap and total; validity is the one that actually protects anything, and it is always
the harder one to reach.

#### Review #13 — the boundary the fix draws is always the noun the last review named, and the defect is one noun down

C4.4f's fix was scoped to "tools": every `Tool.execute()` now catches `BackendAuthException` before its
generic `catch (Exception e)`. Review #13 found the exact orphan-shaped defect it had just fixed —
schema/record written, a dependent linkage silently lost, tool reports success anyway — **one tool over**,
inside a *method* that isn't itself a tool: `CreateEntityTool.linkEntityToApp` makes a GET (fetch the app)
and a PUT (save it back with the entity linked) and had wrapped both in a try/catch that logged any
failure, including 401, and returned `void`. `execute()`'s 2xx-on-`/schema` branch reached
`ToolResult.success(...)` regardless of whether the link step actually happened.

It found a second instance in a place "tools" cannot even describe: `AiAgent.loadEntitySummary` — called
from `buildExecutionContext()`, called from `think()`, called from *both* agent loops — fetches the
app to build the "ENTITIES IN THIS APP" prompt block, and silently swallowed every non-200 (401
included) into `log.debug` plus a cached empty string. This runs during **prompt building, before any
tool executes**, so the tool-result-based `isAuthFailure()` abort from C4.4f can never see it: on a dead
session the entity block just vanishes from the prompt, the LLM answers from nothing, no tool is ever
called, no auth check fires, and the user gets a confidently wrong answer ("your app has no entities").

**Fix, same shape as C4.4f, applied to both:**
- `linkEntityToApp` now declares `throws Exception` and throws `BackendAuthException` on a 401 from
  either the GET or the PUT, and a plain `IllegalStateException` on any other non-2xx — no more
  try/catch to swallow either into a log line. `execute()`'s existing `catch (BackendAuthException) {…}
  catch (Exception e) {…}` (already correctly ordered by C4.4f) needed zero changes to handle it.
- `loadEntitySummary` now throws `BackendAuthException` specifically on 401 (every *other* non-200
  still logs-and-returns-empty as before — this fix is scoped to the auth case the reviewer found, not
  a blanket "any failure aborts the conversation"). `think()`'s blanket `catch (Exception e) { return
  null; }` sat directly between that throw and both loop call sites, so it gained a
  `catch (BackendAuthException authEx) { throw authEx; }` **before** the generic catch (required order:
  more specific catch first). Both `process()` and `processWithStream()` now wrap their `think(...)` call
  in a `try { … } catch (BackendAuthException authEx) { … }` that aborts with the exact same
  `SESSION_ENDED_MESSAGE` (new shared constant — also closes the C4.4f nit about the message being
  duplicated verbatim in four places) as the tool-result path, and — for the streaming loop —
  `emitter.authExpired(...)`.

**Regression tests:**
- `CreateEntityToolLinkFailureTest` (new file) drives the real HTTP path with a stub `HttpServer`
  (source-scan cannot prove a swallowed exception was actually swallowed) asserting a 401 on the link GET
  *or* PUT makes `create_entity` report `isAuthFailure()==true`/`isSuccess()==false`, and that a 500
  (non-auth) still fails the tool rather than merely logging.
- `AiAgentTest.testAgentLoop_LoadEntitySummaryAuthFailureAbortsBeforeAnyLlmCall` and its streaming
  counterpart stand up a stub backend returning 401 on the app-fetch GET, and assert the agent aborts
  with the session-ended message **and `verify(llmService, times(0))`** — the LLM must never be called,
  not merely receive the right final message after being called anyway.

**Reviewer's own methodology, worth keeping:** the review enumerated all 27 `HttpRequest.newBuilder()`
call sites across 17 files under `com.appbana.ai` (2 excluded by design — LLM provider services), and
of the remaining 25, 21 already handled 401 correctly and exactly 3 did not (the two above, plus
`ScaffoldAppTool.rollback`, judged safe/unreachable on the auth path by design). Enumerating *operations*
(every HTTP call site) rather than *nouns* (tools, or "the loop", or whatever the last review happened to
name) is what makes that closure claim — *"fix these three and the class is closed, with nothing left for
me to find in it"* — actually checkable, instead of one more round of careful reading that will, per this
epic's track record, find the next noun down.

**Meta-lesson:** across C4.4c → C4.4f, each review scoped its fix to the noun the *previous* review had
named (first "header attachment", then "tools", now — almost — "AiAgent"), and each time the defect
that survived lived at the next noun down inside that boundary (a helper inside a tool; a prompt-building
fetch inside the agent that no fix ever called a "tool" because it isn't one). The mechanical antidote is
to stop enumerating nouns and start counting operations: every `HttpRequest.newBuilder()` (or equivalent)
in the codebase is one row in a table, with one column for "detects 401" and one for "acts on it
correctly" — a defect class closes when every row is checked, not when every *name someone has used so
far* is checked.

#### Review #14 — Approved; the census that closed Review #13 was still just a command, not a guard

Review #14 re-verified all three Review #13 fixes by mutation testing (reverting each one and confirming
the specific regression test goes red with the expected message) and found no defect in any of them —
first "Approved" verdict in this family. It flagged one thing left undone from Review #13's own stated
scope: Review #13's census (27 `HttpRequest.newBuilder()` sites, 24 correct 401 checks, 3 named
exceptions) was run by hand, wired into the write-up, and then never turned into anything that runs
again. Nothing stops a fourteenth call site from omitting the check; the only way to notice would be
re-running the same by-hand enumeration next round.

**Fix:** `ToolAuthHeaderTest.everyRequestSiteChecksFor401ExceptTheDocumentedAllowList` (new test) walks
`com/appbana/ai` recursively, and for every `.java` file counts occurrences of `HttpRequest.newBuilder(`
and `statusCode() == 401`. The two counts must match, per file, except an explicit allow-list with the
gap and the reason for each written directly into the test's javadoc and its assertion message:
- `llm/GeminiLlmService.java`, `llm/OpenAiLlmService.java` (gap 1 each) — third-party provider APIs,
  authenticated with their own provider key; there is no session token to be invalid.
- `agent/tool/ScaffoldAppTool.java` (gap 1) — `rollback()`'s delete deliberately skips the check; it
  only runs after a failure the caller already handled, and a 401 during rollback itself is reported by
  its own call-site catch, not silently retried.

Mutation-verified before landing: temporarily changed `CreateEntityTool`'s GET-side check from
`getRes.statusCode() == 401` to an unreachable literal, re-ran only the new test, confirmed it failed
naming `agent/tool/CreateEntityTool.java` with the exact expected/actual gap, then reverted.

**Two non-blocking 🟢 nits noted, deliberately not fixed this round** (per the review: "not worth a
round on their own — worth doing next time either loop is opened"): tracked as backlog in
[`ACTIVE_TASKS.md`](../ACTIVE_TASKS.md), not written up further here.

**Meta-lesson:** *"the census existed, it worked, it was in the review — and what got implemented was
the three fixes it pointed at, not the census itself. Even when the durable artefact is handed over
ready-made, the instinct is to consume it as a to-do list and let it evaporate."* A census is only a
guard once it is re-runnable and wired into the test suite; otherwise it is a very good one-time answer
to a question nobody will remember to ask again the same way.

#### Review #15 — Approved; the C4.4 auth family closes, and a mutation-testing pitfall caught against itself

Review #15 attacked the one design decision that could have made Review #14's guard decorative: it
injected a *second*, unchecked `HttpRequest.newBuilder()` call into `ScaffoldAppTool.java` — a file
already on the allow-list, the exact case a naive "is this file exempt?" implementation would wave
through — and confirmed the guard still failed, naming the file and the exact arithmetic. Two tests
failed on that mutation, not one: the new census and the pre-existing behavioural header source-scan,
confirming they check different properties (site attaches a token / site handles a rejected one) and
neither subsumes the other. Verdict: **Approved**, no production change, tree clean, 178/0/2 confirmed.

**One javadoc sentence added in response to a 🟢 nit:** the guard matches the literal text
`statusCode() == 401`; a stylistic variant at a genuinely-correct call site (`401 == resp.statusCode()`,
`HttpURLConnection.HTTP_UNAUTHORIZED`, or hoisting the status into a local before comparing) reads as a
missing check and fails the build. Documented in
`ToolAuthHeaderTest.everyRequestSiteChecksFor401ExceptTheDocumentedAllowList`'s javadoc as a deliberate
fail-safe direction — match the existing spelling rather than loosen the test. The other 🟢 nit
("counting is a proxy for pairing, not per-site coverage") was left as pure knowledge, per the review's
own framing, layered against the fact that the per-site source scan already covers that gap.

**Meta-observation, worth keeping — a mutation test has to verify its own mutation landed.** The
reviewer's first attempt at the `ScaffoldAppTool` mutation used a string-replace anchor that didn't
exist in the file; the replace silently no-op'd, the guard ran against the unmodified source, and
passed — producing, for a moment, false evidence that the new test was decorative. This is the exact
same defect shape the whole epic has been about (`linkEntityToApp` returning `void` on 401 looked like
success; `loadEntitySummary` caching `""` looked like an app with no entities): a check that never ran
looks identical, from outside, to a check that ran and passed. The fix is one line at every layer:
assert the setup actually happened before trusting the result of what depends on it. Applied here: print
the post-mutation occurrence count and abort if it isn't exactly one more than before, before trusting a
red or green verdict from the guard itself.

**Outstanding, unchanged across every round of this epic:** the C3 maker-checker Playwright round-trip
(maker submits → checker rejects → resubmit → approve) remains the one unticked exit criterion, and CI
still runs `mvn -B verify` only — no vitest, no Playwright. The new guard lives in the Maven reactor, so
it is enforced on every push; the frontend guards still are not.


> **Deviation from plan — C4.1 was larger than "parameter schemas accept the flag".**
> Accepting `approvalRequired` in the two parameter schemas was necessary but not sufficient. `CreateEntityTool.buildEntityMetadata` constructs the *entire* body POSTed to `/schema` and silently drops anything it does not explicitly copy, so the flag never reached the backend. Because `SchemaEnricher` read the flag independently and injected the 8 approval columns anyway, the failure was invisible from the outside: the physical table came out approval-shaped while the schema record carried `approvalRequired=false`, and all 13 backend guards branch on `schema.isApprovalRequired()` rather than on the presence of the columns. The entity *looked* approval-enabled and behaved as if it were not. Fixed, with `CreateEntityToolApprovalTest` pinning the payload.

> **Deviation from plan — C4.3 was inverted, then superseded by C4.6.**
> The task reads "validate that entities flagged `approvalRequired` don't also request incompatible custom `status` fields". The real hazard was narrower and worse: `SchemaEnricher.injectApprovalFields` used to *skip* injecting any column whose name already existed, so an LLM that invented its own `approval_status` (type `text`, options `Yes`/`No`) suppressed the canonical definition entirely. C4.3 inverted that to inject unconditionally and rename the colliding user field to `workflow_<name>`.
>
> **That inversion was itself a defect, and the whole mechanism has since been deleted.** A lenient→strict change shipped without a census of its inputs: the very next task, C4.4, adds RAG templates showing the agent what an approval schema looks like — and the natural way to write one is to declare the eight columns. Under unconditional rename, every such template would have produced eight junk `workflow_*` columns in every table of every app built from it, permanently. Under the old skip-if-exists rule that same template was a harmless no-op.
>
> C4.6 removes the conflict at its root: `SchemaEnricher` no longer injects approval columns at all, and `SchemaManager` — which every writer of the flag passes through — materialises them, deduping against whatever the schema already declares. A template that declares the columns and one that omits them now converge on the same physical table. `SchemaEnricher.RESERVED_APPROVAL_COLUMNS` survives as a recognition-only constant (a documented mirror of `ApprovalColumns.NAMES`, since ai-builder has no Maven dependency on `app-bana-service`), used by `BatchUpdateEntitiesTool` to refuse deleting an approval column while the flag is still set.

> **Deviation from plan — C4.6 was not in the original spec.**
> C4.1 made `approvalRequired` reachable from `create_entity`, but only half the invariant was owned: `ApprovalColumns`' javadoc claimed "the eight system columns **SchemaManager injects**", while `SchemaManager` contained no reference to approvals whatsoever. The sole producer was `SchemaEnricher`, in the separate ai-builder process, on the `scaffold_app` path only — one of four writers of the flag (the others being `create_entity`, `batch_update_entities`, and any direct `POST /schema` from Studio, a script or a test). Before C4.1 the gap was harmless *by accident*, because the flag was being dropped anyway; C4.1 removed that accidental protection without moving the injection, so `create_entity` began emitting entities that accept records and then fail on the first workflow action.
>
> Fixing the producer exposed three consumers that had silently depended on the columns being *declared schema fields* — which they only ever were on enricher-built entities:
> - `EntityCrudService.insertRecordLegacy` builds its column list from `schema.getFields()`, so the server-assigned `approval_status=DRAFT` / `approval_revision=1` / `submitted_by` that `enforceApprovalPreInsert` injects were being dropped, landing every new row with a NULL status.
> - `GenericEntityRoutes.applyApprovalPutGuard` and `EntityCrudService.findOpenRevision` both answered "does this entity support revisions?" by probing `getFields()` for `approval_parent_id`. Post-C4.6 that reported *no* for every correctly-provisioned approval entity, downgrading what should be a new DRAFT revision into an in-place edit of a live APPROVED row. `approvalRequired` is now the authority in both.
> - `approval_parent_id` is INTEGER (matching the PK it points at), but `findOpenRevision` bound the id as a String — masked until now because fixtures declared the column as text. Coercion is centralised in `ApprovalColumns.parentIdValue`.
>
> **Why the existing suite never caught any of this:** every approval fixture hand-declared the eight columns, so the tests supplied the exact invariant production code was supposed to guarantee. 281 green backend tests could only ever exercise the already-correct shape.
>
> **C4.6a (follow-up) — the fixture conversion was incomplete, and it hid two more instances of the same defect.** The C4.6 commit message and this plan both claimed "those fixtures now set the flag and nothing else". That was true of `RevisionFlowTest` and of one `ApprovalRoutesSecurityTest` fixture, and **false** of the other two in that file, which still declared seven of the eight columns. Because the only batch-insert test in the codebase lives there, the declared columns kept re-entering `getFields()` and the following stayed invisible:
> - `EntityCrudService.insertBatch` built its column list from `getFields()` exactly as `insertRecordLegacy` had, so every server-assigned approval value was dropped. Blast radius: `GenerateMockDataTool` posts to `/api/{entity}/batch`, so every AI-seeded approval app got NULL-status rows — and `ApprovalService.Status.fromValue(null)` returns `DRAFT`, so `submit` on those rows returned 200 and nothing surfaced the corruption.
> - `EntityCrudService.updateById` had the same shape, reached from the revision branch of `applyApprovalPutGuard`. Re-editing a **REJECTED** revision silently lost the `approval_status → DRAFT` reset and the `rejection_reason → null` clear, stranding the revision as un-resubmittable. `repeatedPutsReuseTheSameOpenRevision` covered that code path but asserted only the business column.
>
> Both now derive their column list from one builder, `EntityCrudService.writableFields()`. `updateById` takes an explicit `allowApprovalColumns` opt-in.
>
> **Lesson:** "the sweep is complete" is itself a testable claim, and cheaper to check than to have a reviewer disprove. A fixture that supplies the invariant under test cannot witness that invariant being broken — so converting fixtures is not cleanup, it is the experiment.
>
> ~~`allowApprovalColumns` defaults to `false`, because there is no `enforceApprovalPreUpdate` counterpart — the exclusion on client-facing PUTs *is* the guard against a body of `{"approval_status":"APPROVED"}`.~~
>
> **C4.6b — retraction: the sentence struck through above was wrong, and writing the test that was supposed to *confirm* it disproved it instead.** Review round 3 asked for the `false` default to be pinned by a test rather than a comment. Mutating the default to `true` should have leaked a forged `approval_status=APPROVED`. It leaked exactly one column — `submitted_by=alice_maker`, the **server's own** value — and never the forged `eve_attacker`.
>
> The reason: `applyApprovalPutGuard` calls `stripApprovalColumns(data)` unconditionally for any approval-required entity before it returns, and all three client PUT routes run it. **That strip is the guard**; the column-list exclusion was never load-bearing for security. What the exclusion actually did was discard the three values the guard deliberately *re-stages* immediately after stripping — `approval_status=DRAFT`, `rejection_reason=null`, `submitted_by=<session user>`.
>
> So C4.6a fixed the revision path and left the identical bug on the in-place path: **a maker editing their own REJECTED row got a 200 with the business edit applied, while the row stayed REJECTED carrying its stale rejection reason** — the same un-resubmittable dead end C4.6a set out to fix, one branch over. The three PUT routes now pass `allowApprovalColumns=true`, which is safe precisely because the guard has already stripped client input. The `false` default is retained as defence-in-depth for a future caller that skips the guard.
>
> `RevisionFlowTest.putOnRejectedRowReturnsItToDraftInPlace` covered this exact scenario and stayed green throughout, because it asserts on the in-memory `data` map that the guard mutates rather than on the stored row — the guard's staging was always correct; the persistence was not.
>
> **Lesson:** a security claim stated as a mechanism ("the exclusion is the guard") rather than an outcome ("forged values must not persist; server-staged ones must") can be false while the outcome it describes is true, and the codebase will look fine either way. Mutation-testing the guard is what separated the two — and it cost one edit and one test run.

> **Deviation from plan — C4.5 as written is not implementable.**
> C4.5 assumes the checker queue is a page with `PageMeta` behind it. It is not: C3.3 shipped the queue as **shell state with no route and no `PageMeta`**, because its contents are per-user (a checker sees only rows they are eligible to approve) and the runtime has no router, so synthesising page metadata would mean inventing it at scaffold time and filtering it back out per caller. `GeneratePageTool` therefore has nothing meaningful to emit. The queue already appears automatically for any user holding the CHECKER role the moment an approval-required entity exists — which is the outcome C4.5 wanted — so **no scaffold-time work is required**. C4.5 is closed as obsolete rather than implemented. If the runtime gains a router later, revisit as a routing task, not a scaffolding one.

### Exit criteria — C4

Verified 29-07-2026 against the full stack (ai-builder 8081 + backend 8080 + Postgres + Qdrant) with
a live `OPENAI_API_KEY` and a real session token. All three passed only *after* C4.4c; on the
pre-C4.4c build every one of them failed.

- [x] User says *"I want a customer onboarding app"* → agent proposes maker-checker in Phase 1 → user says *"yes"* → scaffold produces approval-required entities. (The checker queue then appears on its own for CHECKER-role users — see the C4.5 deviation; it is not scaffolded.)
  - Phase 1 reply: *"allowing one team member to create customer records, which another team member must approve before they go live"* — business English, no `approvalRequired`, no column names.
  - On *"Yes, let's build it!"*: `create_entity` called with `approvalRequired=true` for `CustomerApplication` **only**, matching the blueprint's `approvalRequiredEntities`.
  - Persisted: `appbana_schemas.json->>'approvalRequired'` is `true` for `CustomerApplication`, `false` for `OnboardingDocument` and `OnboardingNote`.
  - Materialised: all eight approval columns present on the physical table (`APPROVAL_STATUS`, `APPROVAL_REVISION`, `APPROVAL_PARENT_ID`, `SUBMITTED_BY`, `SUBMITTED_AT`, `APPROVED_BY`, `APPROVED_AT`, `REJECTION_REASON`) — and absent from the other two.
- [x] User can override: *"no approval flow"* → agent produces flat entities.
  - *"...but no approval flow — keep it simple, one person does everything"* → two flat entities, no approval language anywhere in the reply. The override beats the retrieved blueprint.
- [x] Regression: apps that don't imply approval (blog, todo, spice shop) do NOT get the maker-checker prompt.
  - *"a spice shop app to sell spices online"* → `matched=[ecommerce, restaurant]`. The maker-checker blueprints were not retrieved at all, so the feared absence of a relevance floor in `getDomainExamples` did not bite; ranking alone was sufficient. Reply contains no approval concepts.

The flag surviving into the `/schema` payload, both tool schemas advertising it with an intact
decision rule, and the colliding-column rename remain covered by unit tests.


---

## Sub-phase C5 — Notifications (optional for v1 launch)

**Goal:** Actors get notified when they have an action pending.

| # | Task | Where | Est. |
|---|---|---|---|
| C5.1 | In-app notification model — `appbana_notifications` table (`id`, `user_id`, `title`, `body`, `link`, `read_at nullable`, `created_at`) + REST `GET /api/notifications` + `POST /api/notifications/{id}/read` | Liquibase changeset + new `NotificationRoutes.java` | 60 min |
| C5.2 | Notification hooks — `ApprovalService` emits notifications on: submit (all checkers), approve (maker), reject (maker with reason) | `ApprovalService` | 45 min |
| C5.3 | Runtime bell icon — in top nav, dropdown showing latest 10 unread, click → mark read + navigate | new `NotificationBell.tsx` in `AppRuntimeShell.tsx` | 60 min |
| C5.4 | Optional email adapter — pluggable interface `NotificationChannel`. `EmailChannel` implementation via SMTP config (opt-in per tenant). Silent fallback if no SMTP configured | new `com.appbana.notification` package | 90 min |
| C5.5 | Preference storage — per-user opt-in/out per event type via `appbana_notification_prefs` | Liquibase changeset + `NotificationPrefRoutes.java` | 45 min |

### Exit criteria — C5

- [ ] In-app: submitting a row generates a notification for every checker of that entity within 1 second.
- [ ] Bell icon shows unread count; clicking opens dropdown; clicking an item marks it read + navigates.
- [ ] Email (if configured): checker receives email within 30 seconds of submit.
- [ ] User can mute a specific event type without losing others.

**C5 is v1-optional.** If launch timeline is tight, in-app bell (C5.1, C5.2, C5.3) is the minimum; email (C5.4) and preferences (C5.5) can defer to v1.1.

---

## Cross-cutting concerns

### Security

- Every state transition endpoint validates the caller's role via `UserRoleService.isChecker(...)` / `isMaker(...)` — no client-supplied role claims.
- `ApprovalGuard.filterQuery()` is the sole place read-side filtering happens, so a bypass would require breaking one file, not many.
- Rejection reasons and diff payloads may contain PII — audit table access is admin-only via a separate role check.
- Concurrent approvals resolved with `SELECT ... FOR UPDATE` on the parent row (Postgres-native).

### Backward compatibility

- Entities without `approvalRequired: true` behave exactly as they do today — zero column additions, zero permission checks, zero UI difference.
- Existing apps continue to work unchanged after C1..C5 land.

### Testing strategy

- **Unit:** State machine transitions in `ApprovalServiceTest` (every valid + every invalid transition), diff computation, permission checks.
- **Integration:** `RevisionFlowIntegrationTest` covering the atomic revision-replacement across parallel actors.
- **E2E (Playwright):** `maker-checker-flow.spec.ts` — full round-trip described in C3 exit criteria.
- **Security:** `MakerCannotSelfApproveTest`, `NonCheckerCannotSeePendingTest`, `CrossTenantApprovalTest`.

### Performance

- `ApprovalGuard` filtering adds at most one `WHERE approval_status = ...` clause + one `IN` clause of user's checker-entities — indexed via `idx_appr_row` on the audit table and standard PK on entity tables. Expected overhead <5ms per query.
- Notification polling (`PendingCountService`) is 30s in v1. WebSocket push is a v2 enhancement.

### Rollout order

**Strict serial:** C1 → C2 → C3.
**Parallel-safe:** C4 can start once C1 lands (agent prompt work doesn't need running state machine).
**Deferrable:** C5 can slip to v1.1 without blocking launch.

### Documentation

- [`.github/copilot-instructions.md`](../../.github/copilot-instructions.md) — new Section: "Approval workflows (maker-checker)".
- New user-facing guide: `docs/guides/maker-checker.md` — how to grant roles, how to configure approval-required, how the audit trail works.
- `docs/ACTIVE_TASKS.md` — status tracker.

---

## File-level change map

**New files (backend):**
- `app-bana-service/src/main/java/com/appbana/approval/ApprovalService.java` (C2)
- `app-bana-service/src/main/java/com/appbana/approval/ApprovalGuard.java` (C2)
- `app-bana-service/src/main/java/com/appbana/approval/UserRoleService.java` (C1)
- `app-bana-service/src/main/java/com/appbana/server/routes/RoleRoutes.java` (C1)
- `app-bana-service/src/main/java/com/appbana/server/routes/NotificationRoutes.java` (C5)
- `app-bana-service/src/main/java/com/appbana/notification/NotificationChannel.java` (C5)
- `app-bana-service/src/main/java/com/appbana/notification/EmailChannel.java` (C5)
- Liquibase changesets: `V15_appbana_approvals.xml`, `V16_appbana_user_roles.xml`, `V17_appbana_notifications.xml`, `V18_appbana_notification_prefs.xml`

**Modified files (backend):**
- `app-bana-service/src/main/java/com/appbana/SchemaManager.java` — C1
- `app-bana-service/src/main/java/com/appbana/server/routes/GenericEntityRoutes.java` — C2
- `app-bana-service/src/main/java/com/appbana/server/routes/AppRoutes.java` — C1

**New files (runtime):**
- `app-bana-runtime/src/runtime/ApprovalStatusPill.tsx` (C3)
- `app-bana-runtime/src/runtime/CheckerQueuePage.tsx` (C3)
- `app-bana-runtime/src/runtime/ApprovalDetail.tsx` (C3)
- `app-bana-runtime/src/runtime/RejectDialog.tsx` (C3)
- `app-bana-runtime/src/runtime/AuditDrawer.tsx` (C3)
- `app-bana-runtime/src/runtime/PendingCountService.tsx` (C3)
- `app-bana-runtime/src/runtime/NotificationBell.tsx` (C5)

**Modified files (runtime):**
- `app-bana-runtime/src/runtime/Renderer.tsx` — C3 (new page layout `checker_queue`)
- `app-bana-runtime/src/runtime/StudioTableLive.tsx` — C3 (pill in cell renderer)
- `app-bana-runtime/src/runtime/FormActions.tsx` — C3 (submit-for-approval)
- `app-bana-runtime/src/runtime/AppRuntimeShell.tsx` — C3 + C5

**Shared types (`app-bana-shared/src/metadata.ts`):**
- `ApprovalStatus` enum
- `ApprovalMetadata` on `EntitySchema`
- `ApprovalAction` on API request/response
- `AuditEntry` type

**AI Builder:**
- `ai-builder/src/main/java/com/appbana/ai/agent/tool/ScaffoldAppTool.java` — C4
- `ai-builder/src/main/java/com/appbana/ai/agent/tool/CreateEntityTool.java` — C4
- `ai-builder/src/main/java/com/appbana/ai/agent/tool/GeneratePageTool.java` — C4
- `ai-builder/src/main/java/com/appbana/ai/agent/tool/SchemaEnricher.java` — C1, C4
- `ai-builder/src/main/java/com/appbana/ai/llm/AdvancedPromptEngine.java` — C4
- `ai-builder/src/main/java/com/appbana/ai/knowledge/AppBanaSchemaLoader.java` — C4.4 (the two blueprints)
- `ai-builder/src/main/java/com/appbana/ai/knowledge/DomainBlueprintPrompt.java` — C4.4a (renderer)
- `ai-builder/src/main/java/com/appbana/ai/agent/AiAgent.java` — C4.4a (the only live injection point), C4.4b (memoised per request)
- `ai-builder/src/main/java/com/appbana/ai/knowledge/AppBanaPromptEnhancer.java` — C4.4 (dead path; delegates)
- `ai-builder/src/main/java/com/appbana/ai/knowledge/KnowledgeBaseService.java` — C4.4 (approval signal into the embedding)
- ~~`builder-database/customer-onboarding-with-approval.json`~~ — see the C4.4 deviation: `builder-database/` is not read by any code
- ~~`builder-database/loan-origination-with-approval.json`~~ — same

---

*Last updated: 2026-07-26 · Author: AppBana core team · Status: DRAFT — awaiting approval before C1 begins.*
