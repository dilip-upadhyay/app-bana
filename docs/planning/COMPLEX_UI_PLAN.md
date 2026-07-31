# Complex UI Epic — Implementation Plan

**Status:** 📝 Spec approved 2026-07-26 · ⏳ Execution not started (blocked on Phase A2)
**Owner:** AppBana core team
**Position in master roadmap:** Phase B of the post-Stage-4 forward plan (see [ACTIVE_TASKS.md](../ACTIVE_TASKS.md)). Depends on Phase A (Runtime UX Sprint 2 — ✅ shipped 2026-07-26) **and Phase A2 (Runtime UX Sprint 3 — "Runtime Foundations")** completing. A2 delivers the record-level R/U/D primitives (`DetailPage`, edit mode, `ConfirmDialog` + Undo toast, wired `RowActions`, `ReferenceCombobox`, unified `Button`) that B4 (Master-Detail) consumes directly and B5 (List Views) leans on. Blocks Phase C (Maker-Checker) and the first-customer launch.
**Trigger:** The current runtime renders exactly one page shape per entity: a flat form or a flat list. Real customer-onboarding, KYC, loan-origination, or claims-processing apps require compound page types (wizards, master-detail, conditional fields, document upload, saved filter views). Without these, the AI Builder can scaffold "an app" but not the app a real customer actually needs.

**Related active plans:**
- [Runtime UX Overhaul Plan](./RUNTIME_UX_OVERHAUL_PLAN.md) — **Phase A** (Sprint 2, ✅ shipped) + **Phase A2** (Sprint 3 — "Runtime Foundations"). A2 is the immediate prerequisite; see §Sprint 3 tasks 3.3–3.8 for the primitives B4 and B5 reuse.
- [Maker-Checker Plan](./MAKER_CHECKER_PLAN.md) — **Phase C**, follows this epic. Approval workflows are built on top of B3 (file upload), B4 (master-detail), and B5 (list views — the checker's inbox).
- [Enterprise Capabilities Plan](./ENTERPRISE_CAPABILITIES_PLAN.md) — **Phase D**, runs *after* Phase C. D is packaging on top of the differentiated product built in A + A2 + B + C.
- [AI-Native UI Rebuild Plan](./AI_NATIVE_UI_REBUILD_PLAN.md) — the master rebuild plan; this epic is a post-Stage-4 extension.
- Live status: [`ACTIVE_TASKS.md`](../ACTIVE_TASKS.md).

---

## Table of Contents

1. [TL;DR](#tldr)
2. [Why we are doing this now](#why-we-are-doing-this-now)
3. [Non-goals](#non-goals)
4. [Metadata contract additions](#metadata-contract-additions)
5. [Sub-phase B1 — Wizard / multi-step forms](#sub-phase-b1--wizard--multi-step-forms)
6. [Sub-phase B2 — Conditional fields](#sub-phase-b2--conditional-fields)
7. [Sub-phase B3 — File upload + preview](#sub-phase-b3--file-upload--preview)
8. [Sub-phase B4 — Master-detail (embedded child tables + tabs)](#sub-phase-b4--master-detail-embedded-child-tables--tabs)
9. [Sub-phase B5 — List views: grouping, filters, saved views](#sub-phase-b5--list-views-grouping-filters-saved-views)
10. [Cross-cutting concerns](#cross-cutting-concerns)
11. [AI Builder contribution](#ai-builder-contribution)
12. [File-level change map](#file-level-change-map)

---

## TL;DR

Five independently shippable sub-phases upgrade both the **runtime renderer** (so it can render compound pages) and the **AI Builder** (so `scaffold_app` / `generate_page` can produce those page types from a natural-language request).

| # | Sub-phase | Runtime deliverable | AI Builder deliverable | Est. |
|---|---|---|---|---|
| B1 | Wizard / multi-step forms | Stepper component, step-navigation state, per-step validation, draft persistence | `generate_page` accepts `layout: "wizard"` + `steps[]` | ~6 hr |
| B2 | Conditional fields | Expression evaluator (`showWhen`, `requiredWhen`, `disabledWhen`) on any field | Field metadata gains `conditions{}`; agent learns to emit them | ~4 hr |
| B3 | File upload + preview | Upload widget, backend `/api/files` endpoint, PDF/image preview, size/type validation | `file` field type in schemas, `attachment` field in forms | ~8 hr |
| B4 | Master-detail | Embedded child-table inside parent form, tabbed detail view, master row → child rows via FK | `scaffold_app` recognises 1:N relationships and generates linked pages | ~6 hr |
| B5 | List views: grouping, filters, saved views | Filter bar, group-by dropdown, saved-view chips, aggregate summary row | `generate_page` accepts `filters[]` + `groupBy` + `defaultSort` | ~5 hr |

**Total scope:** ~29 hours of focused work. Each sub-phase is a standalone PR with its own exit criteria and can ship without the others.

---

## Why we are doing this now

The reference use-case — a Customer Onboarding App — inherently needs all five of these:

1. **Wizard** — onboarding is never one 40-field form. It's: `Personal Info → Address → Documents → Consent → Review & Submit`.
2. **Conditional fields** — "Business tax ID" only appears when `customer_type == "business"`. "Guardian info" only when `age < 18`. Every real form has this.
3. **File upload** — onboarding = document collection. Passport, utility bill, income proof. Non-negotiable.
4. **Master-detail** — a Customer has N contacts, N addresses, N documents. The Customer detail page must show all of them inline.
5. **List filters + saved views** — the ops team needs "Pending KYC · Last 7 days · Assigned to me" as a one-click view.

The AI Builder currently generates none of these. The runtime cannot render any of these. Fixing this at the metadata layer (schema + PageMeta) means every future app benefits automatically, not just Customer Onboarding.

---

## Non-goals

- Real-time collaborative form editing.
- Offline form filling with sync.
- Drag-and-drop reordering of steps or fields in the runtime (that's Studio-side, deferred).
- Custom JavaScript escape hatches in field metadata — expressions are declarative only (see B2).
- Cross-entity workflow orchestration (that's Phase C).
- Excel-style spreadsheet paste-in for tables (nice-to-have, deferred).

---

## Metadata contract additions

All five sub-phases add optional fields to the existing [`app-bana-shared/src/metadata.ts`](../../app-bana-shared/src/metadata.ts) types. **Backward compatible** — existing apps continue to render unchanged.

```ts
// PageMeta additions
export interface PageMeta {
  // ...existing...
  layout?: 'form' | 'list' | 'detail' | 'wizard';   // B1
  steps?: WizardStep[];                              // B1
  filters?: FilterDef[];                             // B5
  groupBy?: string;                                  // B5
  defaultSort?: SortDef;                             // B5
  savedViews?: SavedView[];                          // B5
}

// New types
export interface WizardStep {
  id: string;
  title: string;
  subtitle?: string;
  fields: string[];        // field names from parent entity
  validation?: 'onNext' | 'onSubmit';
}

export interface FieldCondition {
  showWhen?: Expression;       // B2
  requiredWhen?: Expression;   // B2
  disabledWhen?: Expression;   // B2
}

// Expression grammar — declarative, no eval()
// Examples:
//   { field: 'customer_type', op: 'equals', value: 'business' }
//   { and: [ {...}, {...} ] }
//   { or: [ {...}, {...} ] }
//   { not: {...} }
export type Expression =
  | { field: string; op: 'equals' | 'notEquals' | 'in' | 'notIn' | 'gt' | 'lt' | 'isEmpty' | 'isNotEmpty'; value?: unknown }
  | { and: Expression[] }
  | { or: Expression[] }
  | { not: Expression };

// EntitySchema field additions
export interface EntityField {
  // ...existing...
  conditions?: FieldCondition;   // B2
  fileConstraints?: {            // B3
    maxSizeBytes: number;
    acceptedMimeTypes: string[];
    maxFiles?: number;
  };
  childEntity?: {                // B4 — for `type: 'child_table'`
    entityName: string;
    fkField: string;              // FK back to parent
    displayFields: string[];
  };
}

export interface FilterDef {     // B5
  field: string;
  op: 'equals' | 'in' | 'range' | 'contains' | 'dateRange';
  label: string;
  default?: unknown;
}

export interface SavedView {     // B5
  id: string;
  name: string;
  filters: Record<string, unknown>;
  groupBy?: string;
  sort?: SortDef;
  isDefault?: boolean;
}
```

These types land in `app-bana-shared` **before** any sub-phase begins so runtime and AI Builder speak the same vocabulary.

---

## Sub-phase B1 — Wizard / multi-step forms

**Goal:** Render a page as a step-by-step wizard when `page.layout === 'wizard'`.

### Runtime work

| # | Task | Where | Est. |
|---|---|---|---|
| B1.1 | `WizardShell.tsx` — top progress bar, current step title/subtitle, `Prev` / `Next` / `Submit` buttons, disable `Next` when current step invalid | new `app-bana-runtime/src/runtime/WizardShell.tsx` | 90 min |
| B1.2 | Wire `Renderer.tsx` `page` case — when `layout === 'wizard'`, render `WizardShell` around a per-step field subset instead of a flat form | [`Renderer.tsx`](../../app-bana-runtime/src/runtime/Renderer.tsx) | 45 min |
| B1.3 | Draft persistence — save wizard state to `localStorage` per `(userId, appId, entityName, mode)` so refresh doesn't lose progress. Clear on submit or explicit cancel | new `useWizardDraft.ts` hook | 60 min |
| B1.4 | Per-step validation on `Next` click. Uses field metadata + Zod schema (shared with `EntityForm`) | integrate with `useEntityForm` (Sprint 2 output) | 45 min |
| B1.5 | Final `Review & Submit` step — auto-injected as the last step. Shows all filled fields in a read-only summary card grouped by step title | inside `WizardShell.tsx` | 45 min |

### AI Builder work

| # | Task | Where | Est. |
|---|---|---|---|
| B1.6 | `GeneratePageTool` accepts `layout: "wizard"` + `steps: [{title, fields}]` in its parameter schema | [`GeneratePageTool.java`](../../ai-builder/src/main/java/com/appbana/ai/agent/tool/GeneratePageTool.java) | 60 min |
| B1.7 | Agent prompt update — when a user says "onboarding" / "multi-step" / "wizard" / "signup flow", the agent chooses wizard layout and groups fields into logical steps (personal / contact / documents / consent) | [`AdvancedPromptEngine.java`](../../ai-builder/src/main/java/com/appbana/ai/llm/AdvancedPromptEngine.java) + few-shot example in RAG | 45 min |

### Exit criteria — B1

> Implementation shipped in `8efc539`. Every box stays unticked because **no automated test asserts
> any of them**: [`WizardShell.tsx`](../../app-bana-runtime/src/runtime/WizardShell.tsx) has no test
> file, and the `e2e/tests/wizard-flow.spec.ts` named in the testing strategy was never written.

- [ ] `scaffold_app` for "customer onboarding" produces at least one wizard-layout page. (Agent output unasserted.)
- [ ] User can move Next / Prev without losing entered data. (Implemented; unverified.)
- [ ] Refreshing the browser mid-wizard restores state. (Implemented; unverified.)
- [ ] Submitting a wizard writes exactly one row to the parent entity (not one row per step). (Implemented; unverified.)
- [ ] Playwright: full wizard flow (4 steps, submit, verify row) passes. (Spec does not exist.)

---

## Sub-phase B2 — Conditional fields

**Goal:** A field can declare `showWhen` / `requiredWhen` / `disabledWhen` expressions evaluated against the current form values.

### Runtime work

| # | Task | Where | Est. |
|---|---|---|---|
| B2.1 | `evaluateExpression(expr, formValues)` — pure function walking the `Expression` type. No `eval`, no `Function` constructor. Unit tested for every operator + boolean combinator | new `app-bana-runtime/src/runtime/conditions.ts` + `conditions.test.ts` | 90 min |
| B2.2 | `Renderer.tsx` `input` / `select` / `textarea` cases — evaluate `field.conditions.showWhen` on every form-values change; skip render if false. Evaluate `requiredWhen` / `disabledWhen` similarly and pass to child input | [`Renderer.tsx`](../../app-bana-runtime/src/runtime/Renderer.tsx) | 60 min |
| B2.3 | Wizard integration — hidden fields count as valid regardless of Zod schema. Adjust `useEntityForm` to strip hidden fields before validation | `useEntityForm.ts` | 30 min |

### AI Builder work

| # | Task | Where | Est. |
|---|---|---|---|
| B2.4 | `create_entity` + `batch_update_entities` accept `conditions{}` in field definitions | [`CreateEntityTool.java`](../../ai-builder/src/main/java/com/appbana/ai/agent/tool/CreateEntityTool.java), [`BatchUpdateEntitiesTool.java`](../../ai-builder/src/main/java/com/appbana/ai/agent/tool/BatchUpdateEntitiesTool.java) | 45 min |
| B2.5 | Agent prompt — when the user says "only show X when Y", the agent emits the correct `Expression` JSON. Add two RAG few-shot examples | prompt engine + RAG | 45 min |

### Exit criteria — B2

- [x] `evaluateExpression` unit tests: 100% branch coverage on every operator. ([`conditions.test.ts`](../../app-bana-runtime/src/runtime/conditions.test.ts) exercises all 11 operators — `equals`, `notEquals`, `in`, `notIn`, `gt`, `lt`, `gte`, `lte`, `contains`, `isEmpty`, `isNotEmpty` — each with a true and a false case. The literal percentage is unmeasured: `@vitest/coverage-v8` is not installed.)
- [ ] Runtime shows/hides a field within 16ms of the triggering field changing (no flicker). (No timing assertion exists, and none is writable under the no-DOM-shim test setup.)
- [x] Zod validation ignores fields hidden by `showWhen: false`. ([`ConditionalField.tsx`](../../app-bana-runtime/src/runtime/ConditionalField.tsx) removes the subtree from the DOM; the H5 visibility strip that then skips it is covered by [`useEntityFormValidation.test.ts`](../../app-bana-runtime/src/runtime/useEntityFormValidation.test.ts).)
- [ ] User can say "show Business Tax ID only when customer type is business" and the agent produces the correct schema. (Agent output unasserted.)

---

## Sub-phase B3 — File upload + preview

**Goal:** New `file` field type that uploads to backend, previews the file, and stores a URL reference in the row.

### Backend work

| # | Task | Where | Est. |
|---|---|---|---|
| B3.1 | Liquibase changeset — new `appbana_files` table (`id`, `tenant_id`, `app_id`, `entity_name`, `row_id nullable`, `original_name`, `mime_type`, `size_bytes`, `storage_key`, `uploaded_by`, `uploaded_at`) | `app-bana-service/src/main/resources/db/changelog/` | 30 min |
| B3.2 | Storage abstraction — `FileStorageAdapter` interface, `LocalFilesystemAdapter` impl (writes to `app-bana-service/uploads/{tenant}/{app}/{uuid}`), config-selectable so S3 / Azure Blob can plug in later | new `com.appbana.storage` package | 90 min |
| B3.3 | `POST /api/files/upload` — multipart, returns `{fileId, url, mimeType, sizeBytes}`. Auth-guarded. Enforces per-tenant quota (soft limit, warn-log for v1) | new `FileRoutes.java` | 60 min |
| B3.4 | `GET /api/files/{fileId}` — streams file with correct MIME type + `Content-Disposition`. Auth-guarded — tenant-isolation check on `tenant_id` | `FileRoutes.java` | 45 min |
| B3.5 | `SchemaManager` — recognise `type: "file"`, store `VARCHAR(255)` for the `fileId` FK. On row delete, best-effort delete the file too | [`SchemaManager.java`](../../app-bana-service/src/main/java/com/appbana/SchemaManager.java) | 30 min |

### Runtime work

| # | Task | Where | Est. |
|---|---|---|---|
| B3.6 | `FileUploadField.tsx` — dropzone (`react-dropzone`), progress bar, thumbnail for images, PDF icon + filename for docs, size/type validation from `fileConstraints`, click-to-reupload | new component | 90 min |
| B3.7 | `Renderer.tsx` `input` case — when `field.type === "file"`, render `FileUploadField` | [`Renderer.tsx`](../../app-bana-runtime/src/runtime/Renderer.tsx) | 30 min |
| B3.8 | `StudioTableLive.tsx` cell renderer — for file columns show a thumbnail (image) or file-icon + name (link opens `/api/files/{id}`) | [`StudioTableLive.tsx`](../../app-bana-runtime/src/runtime/StudioTableLive.tsx) | 30 min |
| B3.9 | Multi-file variant — when `fileConstraints.maxFiles > 1`, render a grid of uploaded thumbnails with per-file remove buttons | `FileUploadField.tsx` | 45 min |

### AI Builder work

| # | Task | Where | Est. |
|---|---|---|---|
| B3.10 | `SchemaEnricher` — add `file` to accepted type aliases (also recognise `document`, `attachment`, `upload` → coerce to `file`). Set sensible default `fileConstraints` (10 MB, `image/*` + `application/pdf`) when absent | [`SchemaEnricher.java`](../../ai-builder/src/main/java/com/appbana/ai/agent/tool/SchemaEnricher.java) | 30 min |

### Exit criteria — B3

> Implementation shipped in `dd84257`, but
> [`FileRoutes.java`](../../app-bana-service/src/main/java/com/appbana/server/routes/FileRoutes.java)
> has **no `FileRoutesTest.java`** despite this plan's own testing strategy naming one — so the
> cross-tenant isolation criterion below is currently an *untested security claim*.

- [ ] Upload a 5 MB PDF via the runtime → row saves with `fileId` → detail view shows PDF filename with download link. (Implemented; unverified.)
- [ ] Upload an image → thumbnail appears in the list view. (Implemented; unverified.)
- [ ] Uploading a file over the size limit shows an inline error, no request fired. (Implemented; unverified.)
- [ ] Cross-tenant access to a file URL returns 403. (**No test covers this.** Verify manually before relying on it.)
- [ ] Agent produces a `file`-typed field when user says "customers upload their passport". (Agent output unasserted.)

---

## Sub-phase B4 — Master-detail (embedded child tables + tabs)

**Goal:** A parent entity's detail page shows related child entities inline — either as embedded tables or tabs.

**Reuses from Phase A2 (Runtime Foundations, [Sprint 3](./RUNTIME_UX_OVERHAUL_PLAN.md#sprint-3--runtime-foundations)):** B4 does **not** invent record-level R/U/D — it layers child-table framing on top of A2's primitives. Specifically:
- Task 3.3 `DetailPage` supplies the parent's detail page shell that B4.3's `DetailTabs` layout extends.
- Task 3.4 edit mode + `updateEntityRow` helper is what B4.1's inline "edit" action reuses per child row.
- Task 3.5 `ConfirmDialog` + Undo toast is what B4.1's inline "delete" action reuses per child row.
- Task 3.6 wired `RowActions` is the row-action pattern B4.1 clones for child rows.
- Task 3.8 unified `<Button>` is the button primitive B4's "Add child" and toolbar buttons use.
- Task 3.12 `useEntityRows` hook is what B4.1's child fetch reuses (with `?fk_field={parentId}` as the filter).

If any of these primitives is not shipped when B4 starts, B4 is blocked, not degraded — do not fork.

### Runtime work

| # | Task | Where | Est. |
|---|---|---|---|
| B4.1 | `ChildTable.tsx` — inline table rendered inside a parent form/detail. Fetches child rows via `?fk_field={parentId}` (via A2's `useEntityRows`), supports add / edit / delete inline reusing A2's `RowActions` + `DetailPage` + `ConfirmDialog` + Undo toast, shares `StudioTableLive` cell renderers | new component | 90 min |
| B4.2 | `Renderer.tsx` — new node type `child_table` with `entityName`, `fkField`, `displayFields`. Renders `ChildTable` | [`Renderer.tsx`](../../app-bana-runtime/src/runtime/Renderer.tsx) | 45 min |
| B4.3 | Tabbed detail layout — new `layout: "detail_tabs"` on PageMeta. First tab shows parent fields; subsequent tabs show one `ChildTable` each | new `DetailTabs.tsx` + `Renderer.tsx` wiring | 60 min |
| B4.4 | New-parent form → child tables disabled until parent saved (child rows need parent `id`). Show a friendly banner "Save the customer first, then add documents" | `ChildTable.tsx` | 30 min |

### Backend work

| # | Task | Where | Est. |
|---|---|---|---|
| B4.5 | Cascade rules on FK columns — `ON DELETE RESTRICT` by default (preserve child rows), configurable via `EntityField.onDelete: 'cascade' \| 'restrict' \| 'setNull'`. Default is safe | [`SchemaManager.java`](../../app-bana-service/src/main/java/com/appbana/SchemaManager.java) | 45 min |

### AI Builder work

| # | Task | Where | Est. |
|---|---|---|---|
| B4.6 | `scaffold_app` — when the natural-language spec implies 1:N ("customer has multiple documents"), generate both entities with the FK relationship AND generate a `detail_tabs`-layout page on the parent showing the child table | [`ScaffoldAppTool.java`](../../ai-builder/src/main/java/com/appbana/ai/agent/tool/ScaffoldAppTool.java) + prompt engine | 90 min |

### Exit criteria — B4

> Implementation shipped in `60a64aa`.
> [`ChildTable.test.tsx`](../../app-bana-runtime/src/runtime/ChildTable.test.tsx) covers only
> `renderChildTablesFromPage` node discovery and `RecordContext` propagation — not the rendered tab,
> the inline editor, or any write path.

- [ ] Customer detail page has an "Addresses" tab containing an inline editable table of `Address` rows. (Only the `child_table` node discovery is tested; the tab and inline editor are not.)
- [ ] Adding a new Address from within the Customer detail page persists with the correct FK. (Implemented; the write path has no test.)
- [ ] Deleting a Customer with existing Addresses shows a friendly "This customer has 3 addresses — delete them first" error, not a raw FK violation. (Implemented; unverified.)
- [ ] Agent produces the master-detail structure when user says "Customer has multiple addresses and multiple contact persons". (Agent output unasserted.)

---

## Sub-phase B5 — List views: grouping, filters, saved views

**Goal:** List pages become genuine ops surfaces — filter, group, save the current view, count aggregates.

### Runtime work

| # | Task | Where | Est. |
|---|---|---|---|
| B5.1 | `FilterBar.tsx` — chip-style filters above the table. Each `FilterDef` renders as a chip (`Status: Pending`, `Assigned To: Me`). Click chip → popover picker. Clear-all button | new component | 90 min |
| B5.2 | `groupBy` rendering — when set, the table body groups rows under sticky group-header rows with row counts (`Business (12)`, `Individual (34)`) | [`StudioTableLive.tsx`](../../app-bana-runtime/src/runtime/StudioTableLive.tsx) | 60 min |
| B5.3 | Aggregate summary row — when the list has a `number` or `decimal` column, show a sticky footer with `Sum` / `Avg` / `Count`. Config: `PageMeta.aggregates?: {field, agg}[]` | `StudioTableLive.tsx` | 45 min |
| B5.4 | Saved views — top of the list shows a row of view chips (`All`, `My Drafts`, `Pending Approval`, `+ New view`). Click applies its filters + groupBy + sort. `+ New view` opens a dialog to save the current filter state | new `SavedViewsBar.tsx`; backend endpoint below | 90 min |

### Backend work

| # | Task | Where | Est. |
|---|---|---|---|
| B5.5 | `appbana_saved_views` table + CRUD endpoints — `GET/POST/DELETE /api/saved-views?entityKey=...`. Scoped per user | Liquibase changeset + new `SavedViewRoutes.java` | 60 min |
| B5.6 | Query engine extension — `GenericEntityRoutes` already supports `?field:op=value`. Add `?_groupBy=field` returning `{groups: [{key, count, rows: [...]}]}` shape as opt-in | [`GenericEntityRoutes.java`](../../app-bana-service/src/main/java/com/appbana/server/routes/GenericEntityRoutes.java) | 60 min |

### AI Builder work

| # | Task | Where | Est. |
|---|---|---|---|
| B5.7 | `GeneratePageTool` accepts `filters[]`, `groupBy`, `defaultSort`, `aggregates[]` on list-layout pages | [`GeneratePageTool.java`](../../ai-builder/src/main/java/com/appbana/ai/agent/tool/GeneratePageTool.java) | 45 min |
| B5.8 | Agent prompt update — when user says "show pending items grouped by assignee", the agent emits the correct `FilterDef` + `groupBy` | prompt engine + RAG examples | 45 min |

### Exit criteria — B5

> Implementation shipped in `4ce56d0`; [`FilterBar`](../../app-bana-runtime/src/runtime/FilterBar.tsx)
> and [`SavedViewsBar`](../../app-bana-runtime/src/runtime/SavedViewsBar.tsx) were wired into
> `StudioTableLive` by H3 (`e3a129a`). Neither has a test file, and no `e2e/tests/list-views.spec.ts`
> exists.

- [ ] User can filter the Customers list by `Status = Pending KYC` via a chip. (Shipped and wired; the only assertion anywhere is the *negative* case — that `FilterBar` renders nothing when the page declares no filters.)
- [x] User can group Customers by `Business Type` and see counts per group. (Whole-dataset counts proven by [`EntityCrudGroupByTest.java`](../../app-bana-service/src/test/java/com/appbana/service/EntityCrudGroupByTest.java). Note the runtime *additionally* buckets only the current page client-side, so `groupCounts` from the API is the authoritative total — see §9 of [`copilot-instructions.md`](../../.github/copilot-instructions.md).)
- [ ] User can save the current filter set as "My Pending Queue" and it appears as a chip. (System views are covered by [`ApprovalViews.test.tsx`](../../app-bana-runtime/src/runtime/ApprovalViews.test.tsx); *user-created* views are not, and [`SavedViewRoutes.java`](../../app-bana-service/src/main/java/com/appbana/server/routes/SavedViewRoutes.java) has **no `SavedViewRoutesTest.java`** despite the testing strategy below naming one.)
- [ ] Aggregate footer shows `Sum of Loan Amount = $2.4M` when the aggregate is configured. (**Not implemented — see the deviation note below.**)
- [ ] Agent generates a list page with `Status: Pending` filter pre-applied when user says "checker queue page". ([`GeneratePageTool.java`](../../ai-builder/src/main/java/com/appbana/ai/agent/tool/GeneratePageTool.java) accepts and propagates `filters`; whether the agent *emits* them for that phrasing is unasserted.)

> [!WARNING]
> **Dead contract — the `aggregates` footer (B5.3).** `AggregateDef` is declared in
> [`metadata.ts`](../../app-bana-shared/src/metadata.ts), and `GeneratePageTool` accepts an
> `aggregates` parameter and propagates it into both `tableProps` and `page` — but **nothing under
> `app-bana-runtime/src/` reads it** (a repo-wide search for `aggregates` there returns zero hits).
> The agent can therefore emit a page whose stored metadata specifies a footer, the backend persists
> it faithfully, and the runtime silently renders nothing, with no error anywhere.
>
> Either implement the footer or remove `aggregates` from the tool schema. A parameter that is
> accepted, typed, and persisted but never consumed is exactly the failure shape that hid the
> `approvalRequired` defect — see §7 of [`copilot-instructions.md`](../../.github/copilot-instructions.md).

---

## Cross-cutting concerns

### Testing strategy

- **Unit**: `evaluateExpression` (B2), `FilterBar` state transitions (B5), `WizardShell` step-nav (B1) — Vitest.
- **Integration**: One Playwright spec per sub-phase in `e2e/tests/` — `wizard-flow.spec.ts`, `conditional-fields.spec.ts`, `file-upload.spec.ts`, `master-detail.spec.ts`, `list-views.spec.ts`.
- **Backend**: `FileRoutesTest`, `SavedViewRoutesTest`, `SchemaManagerFileTypeTest`.
- **AI Builder**: extend `SchemaEnricherAndPageToolFixTest` with cases for each new field/page type.

### Backward compatibility

Every metadata addition is optional. Existing apps continue to render exactly as they do today. The runtime feature-detects new fields (`if (page.layout === 'wizard') ...`) and falls back to current behaviour when absent.

### Rollout order

B1 → B2 → B3 → B4 → B5 is the recommended order because:
- B1 (wizard) has no dependencies and gives instant visible impact.
- B2 (conditional fields) enhances B1 (steps often need conditional fields).
- B3 (file upload) is often required inside wizard steps.
- B4 (master-detail) benefits from all three above — a parent's wizard, child tables show inline.
- B5 (list views) is orthogonal but naturally comes last, after the data model has variety worth filtering.

Sub-phases are independently shippable if a different order becomes necessary.

### Documentation

Each sub-phase updates:
- [`docs/planning/AI_NATIVE_UI_REBUILD_PLAN.md`](./AI_NATIVE_UI_REBUILD_PLAN.md) — cross-link from the "Post-launch enhancements" section.
- [`.github/copilot-instructions.md`](../../.github/copilot-instructions.md) — Section 11 (Database & Schema Management) gains rows for `file`, `child_table` in the "valid field types" table.
- [`docs/ACTIVE_TASKS.md`](../ACTIVE_TASKS.md) — status tracker.

---

## AI Builder contribution

Every sub-phase touches the AI Builder because the agent must **generate** the new page/field types from natural language, not just be capable of rendering them. Concretely, each sub-phase adds:

1. A new **tool parameter schema** so the LLM knows the new option exists.
2. A new **prompt hint** in [`AdvancedPromptEngine.java`](../../ai-builder/src/main/java/com/appbana/ai/llm/AdvancedPromptEngine.java) so the agent knows *when* to reach for it.
3. At least one **RAG few-shot example** in [`AppBanaSchemaLoader`](../../ai-builder/src/main/java/com/appbana/ai/rag/AppBanaSchemaLoader.java) so the agent has a concrete pattern to imitate.

Without all three, the runtime capability exists but users cannot access it through the AI-native flow — which defeats the point.

---

## File-level change map

**New files (runtime):**
- `app-bana-runtime/src/runtime/WizardShell.tsx` (B1)
- `app-bana-runtime/src/runtime/useWizardDraft.ts` (B1)
- `app-bana-runtime/src/runtime/conditions.ts` + `.test.ts` (B2)
- `app-bana-runtime/src/runtime/FileUploadField.tsx` (B3)
- `app-bana-runtime/src/runtime/ChildTable.tsx` (B4)
- `app-bana-runtime/src/runtime/DetailTabs.tsx` (B4)
- `app-bana-runtime/src/runtime/FilterBar.tsx` (B5)
- `app-bana-runtime/src/runtime/SavedViewsBar.tsx` (B5)

**Modified files (runtime):**
- `app-bana-runtime/src/runtime/Renderer.tsx` — B1, B2, B3, B4
- `app-bana-runtime/src/runtime/StudioTableLive.tsx` — B3, B5

**New files (backend):**
- `app-bana-service/src/main/java/com/appbana/storage/FileStorageAdapter.java` (B3)
- `app-bana-service/src/main/java/com/appbana/storage/LocalFilesystemAdapter.java` (B3)
- `app-bana-service/src/main/java/com/appbana/server/routes/FileRoutes.java` (B3)
- `app-bana-service/src/main/java/com/appbana/server/routes/SavedViewRoutes.java` (B5)
- Liquibase changesets: `V13_appbana_files.xml`, `V14_appbana_saved_views.xml`

**Modified files (backend):**
- `app-bana-service/src/main/java/com/appbana/SchemaManager.java` — B3, B4
- `app-bana-service/src/main/java/com/appbana/server/routes/GenericEntityRoutes.java` — B5

**Shared types:**
- `app-bana-shared/src/metadata.ts` — all sub-phases

**AI Builder:**
- `ai-builder/src/main/java/com/appbana/ai/agent/tool/CreateEntityTool.java` — B2
- `ai-builder/src/main/java/com/appbana/ai/agent/tool/BatchUpdateEntitiesTool.java` — B2
- `ai-builder/src/main/java/com/appbana/ai/agent/tool/GeneratePageTool.java` — B1, B5
- `ai-builder/src/main/java/com/appbana/ai/agent/tool/ScaffoldAppTool.java` — B4
- `ai-builder/src/main/java/com/appbana/ai/agent/tool/SchemaEnricher.java` — B3
- `ai-builder/src/main/java/com/appbana/ai/llm/AdvancedPromptEngine.java` — all sub-phases
- `ai-builder/src/main/java/com/appbana/ai/rag/AppBanaSchemaLoader.java` — all sub-phases (RAG examples)

---

*Last updated: 2026-07-26 · Author: AppBana core team · Status: DRAFT — awaiting approval before B1 begins.*
