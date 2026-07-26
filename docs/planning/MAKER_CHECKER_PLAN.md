# Maker-Checker Epic — Implementation Plan

**Status:** DRAFT — pending approval
**Owner:** AppBana core team
**Position in master roadmap:** Phase C of the post-Stage-4 forward plan (see [ACTIVE_TASKS.md](../ACTIVE_TASKS.md)). Depends on Phase A (Runtime UX Sprint 2) and Phase B (Complex UI Epic) completing. Last epic before first-customer launch.
**Trigger:** Every regulated customer-facing workflow — KYC, loan origination, account opening, policy issuance, claims processing — has a mandatory two-person integrity control: a **maker** creates or edits a record and a **checker** approves it before it becomes live. AppBana today has no concept of `submitted`, `pending approval`, `approved`, or `rejected`. Without maker-checker, we cannot ship into any regulated vertical, which is the majority of the customer-onboarding market.

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
| `approval_parent_id` | `VARCHAR(255)` NULL | For revisions, points at the currently-live approved row |
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
| C2.3 | Revision handling — `PUT /api/{entity}/{id}` on an `APPROVED` row does NOT overwrite; it clones the row into a new `DRAFT` revision with `approval_parent_id = originalId`. On approve, the new revision replaces the parent atomically (parent row soft-marked `superseded_by`) | `ApprovalService` + `GenericEntityRoutes` | 90 min |
| C2.4 | New action endpoints: `POST /api/{entity}/{id}/submit`, `POST /api/{entity}/{id}/approve`, `POST /api/{entity}/{id}/reject` (body: `{reason}`) | `GenericEntityRoutes` | 60 min |
| C2.5 | Diff computation — when submitting a revision, compute field-level diff vs. the parent and store in `appbana_approvals.diff` | `ApprovalService` | 45 min |
| C2.6 | Query endpoint `GET /api/{entity}/{id}/audit` — returns full transition history from `appbana_approvals`, most recent first | `GenericEntityRoutes` | 30 min |
| C2.7 | Filter query params `?_approvalStatus=PENDING` — allowed only for users with checker role on that entity | `GenericEntityRoutes` | 30 min |
| C2.8 | Unit + integration tests — every transition, every permission denial, revision-replacement atomicity | `ApprovalServiceTest`, `ApprovalGuardTest`, `RevisionFlowIntegrationTest` | 90 min |

### Exit criteria — C2

- [ ] Maker cannot approve their own row (403).
- [ ] Non-checker cannot see `PENDING` rows in list queries.
- [ ] Approving a revision atomically replaces the live row; the previous version is retained and queryable via audit.
- [ ] `GET /api/{entity}/{id}/audit` returns the full state-transition timeline.
- [ ] Concurrent approve + reject on the same row: one wins, the other returns 409 Conflict.

---

## Sub-phase C3 — Runtime: approval-aware lists, approve/reject dialog

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

- [ ] Every list page for an approval-required entity shows the status pill on every row.
- [ ] Maker sees `Save as draft` + `Submit for approval`; checker sees `Approve` + `Reject` on a pending row.
- [ ] Reject requires a reason (no submit without text).
- [ ] Checker queue page ranks by oldest-submitted first.
- [ ] Audit drawer shows every state transition with actor + timestamp.
- [ ] Global pending-count badge updates within 30s of a submit.
- [ ] Playwright: full round-trip (maker A submits → checker B rejects with reason → A resubmits → B approves → row visible in live list) passes.

---

## Sub-phase C4 — AI Builder: `approvalRequired` flag

**Goal:** The agent generates approval-required entities from natural language, without the user needing to know the platform primitive exists.

| # | Task | Where | Est. |
|---|---|---|---|
| C4.1 | `ScaffoldAppTool` + `CreateEntityTool` parameter schemas accept `approvalRequired: boolean` per entity | [`ScaffoldAppTool.java`](../../ai-builder/src/main/java/com/appbana/ai/agent/tool/ScaffoldAppTool.java), [`CreateEntityTool.java`](../../ai-builder/src/main/java/com/appbana/ai/agent/tool/CreateEntityTool.java) | 45 min |
| C4.2 | Agent prompt — when the domain implies approval (customer onboarding, loan application, KYC, expense claim, purchase order, policy issuance, employee onboarding, contract), the agent's Phase 1 spec proposal includes: *"This app will use a two-person approval flow — one team member creates a customer profile, another approves it before it goes live. Sound right?"* | [`AdvancedPromptEngine.java`](../../ai-builder/src/main/java/com/appbana/ai/llm/AdvancedPromptEngine.java) + explicit RAG few-shot | 60 min |
| C4.3 | `SchemaEnricher` — validate that entities flagged `approvalRequired` don't also request incompatible custom `status` fields (auto-resolve: rename the user's status to `workflow_status` and inject `approval_status` cleanly) | [`SchemaEnricher.java`](../../ai-builder/src/main/java/com/appbana/ai/agent/tool/SchemaEnricher.java) | 45 min |
| C4.4 | Two new RAG domain templates — `customer-onboarding-with-approval.json`, `loan-origination-with-approval.json` — showing the agent what an approval-required schema + page set looks like | `builder-database/` | 60 min |
| C4.5 | Auto-generate checker queue page — when `scaffold_app` sees `approvalRequired: true` on any entity, `GeneratePageTool` also emits a `checker_queue` page for that entity | [`GeneratePageTool.java`](../../ai-builder/src/main/java/com/appbana/ai/agent/tool/GeneratePageTool.java) | 30 min |

### Exit criteria — C4

- [ ] User says *"I want a customer onboarding app"* → agent proposes maker-checker in Phase 1 → user says *"yes"* → scaffold produces approval-required entities + checker queue pages automatically.
- [ ] User can override: *"no approval flow"* → agent produces flat entities.
- [ ] Regression: apps that don't imply approval (blog, todo, spice shop) do NOT get the maker-checker prompt.

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
- `builder-database/customer-onboarding-with-approval.json` — C4 (new RAG example)
- `builder-database/loan-origination-with-approval.json` — C4 (new RAG example)

---

*Last updated: 2026-07-26 · Author: AppBana core team · Status: DRAFT — awaiting approval before C1 begins.*
