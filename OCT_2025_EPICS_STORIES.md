# October 2025 — Enterprise Foundation (MVP)

Status: Planned (Q4 2025)
Owner: Product/Engineering
Last updated: 2025-09-21

Purpose
- Define the epics and user stories required to deliver the October phase of the accelerated roadmap.
- Align deliverables with Product_AppBana.md §5 (October) and the Angular 21 UI foundation epic.

References
- Product roadmap: Product_AppBana.md §5 (October) and cross-refs
- UI execution prompt: UI_Development_Plan.md (Angular 21, Node LTS)
- Delivery plan: TODO.md (Q4 2025)
- Specs: FUNCTIONAL_SPEC.md §15–§19, LOW_LEVEL_DESIGN.md (Q4 Addenda)

KPIs (October)
- Angular workspace compiles and serves a minimal runtime + designer shell.
- Workflow Engine APIs usable from a small UI smoke (start/advance, history).
- 100% of CRUD + workflow transitions audited; CSV export works.
- FLS enforced on read/write; runtime hides/disables fields per rule.

---

EPIC O1 — Angular 21 UI Foundation (MVP)
Objective
- Establish the Angular 21 workspace, a minimal runtime renderer, and a designer shell as the platform for Q4 features.

Epic acceptance criteria
- Nx workspace created with apps/studio and libs/runtime, libs/designer, libs/ui-schema.
- Runtime renders a schema with Container/Text/Button; designer shell present; Settings panel stores token securely.
- HttpInterceptor injects X-AppBana-Token; build/test/lint scripts available; Node LTS honored via .nvmrc.

Stories
#### O1-S1 Scaffold Angular workspace (Nx)
- Description: Create Nx workspace under ui-builder/, add apps/studio and libs (runtime, designer, ui-schema). Configure tsconfig, lint, unit test runner.
- Acceptance: `npm run build` passes; `npm run lint` passes; apps/studio serves.
- Tasks: Nx init; lib/app gen; tsconfig path mapping; base scripts; README quickstart.
- Dependencies: Node LTS (.nvmrc), none else.
- Estimate: 3 pts
- Labels: angular, tooling, nx

#### O1-S2 Minimal runtime renderer
- Description: Implement runtime lib: component registry + ViewContainerRef-based renderer for Container/Text/Button.
- Acceptance: "Hello from Runtime" renders from a JSON schema; no designer deps in runtime.
- Tasks: runtime service + renderer component; basic components; sample schema.
- Dependencies: O1-S1
- Estimate: 5 pts
- Labels: angular, runtime

#### O1-S3 Designer shell + Settings (token)
- Description: Implement basic designer shell UI with canvas placeholder and a Settings panel that saves X-AppBana-Token to secure storage and updates interceptor.
- Acceptance: token persists; interceptor sends header; canvas shows live preview area.
- Tasks: designer shell component; settings service; secure storage; wiring to interceptor.
- Dependencies: O1-S1, O1-S2
- Estimate: 5 pts
- Labels: angular, designer, security

#### O1-S4 HttpInterceptor + scripts + CI stub
- Description: Add HttpInterceptor; ensure scripts for build/test/lint; add minimal CI config (stub) and Node LTS via .nvmrc mention.
- Acceptance: requests include X-AppBana-Token when set; scripts run locally; CI stub file present.
- Tasks: interceptor; package scripts; ci.yml (stub)
- Dependencies: O1-S3
- Estimate: 2 pts
- Labels: angular, security, ci

#### O1-S5 Audit UI scaffold (list/export stub)
- Description: Create a basic Audit page in studio app: filters (user/entity/date), list, and Export CSV button that calls backend stub.
- Acceptance: page loads; filters debounce; export triggers call.
- Tasks: route/page; table; export call; lightweight error toast.
- Dependencies: O3 epics (backend) in parallel, can stub
- Estimate: 3 pts
- Labels: angular, audit, ux

---

EPIC O2 — Stateful Workflow Engine (MVP)
Objective
- Enable long-running workflows with resumable state, auditable transitions, and UI schema bindings.

Epic acceptance criteria
- WorkflowDefinition + WorkflowInstance tables exist; APIs for definition CRUD, start instance, transition, and history work.
- UI schema supports `workflows` and runtime can call start/approve actions.
- Transitions idempotent; permission checked; audited.

Stories
#### O2-S1 Schema + migrations for workflows
- Description: Add tables appbana_workflow_def, appbana_workflow_instance; migration utilities.
- Acceptance: tables created; CRUD works in dev; covered by unit tests.
- Tasks: model classes; DDL creation; migration path; repository tests.
- Dependencies: none
- Estimate: 5 pts
- Labels: backend, db, workflow

#### O2-S2 Definition APIs
- Description: Implement POST/GET for /workflows/definitions with pagination/search.
- Acceptance: create/list works; validation errors return 400; auth enforced.
- Tasks: handlers; validation; tests.
- Dependencies: O2-S1
- Estimate: 3 pts
- Labels: backend, api, workflow

#### O2-S3 Instance + transition APIs
- Description: Implement start instance; get instance; transitions POST with idempotency; history endpoint.
- Acceptance: start→approve/ reject flows pass; duplicate transition suppressed; history logs.
- Tasks: service + repo; idempotency key; tests.
- Dependencies: O2-S1
- Estimate: 8 pts
- Labels: backend, api, workflow

#### O2-S4 UI schema + runtime actions
- Description: Extend UI schema with `workflows`; wire runtime actions startWorkflow/approveStep.
- Acceptance: designer can bind a button to startWorkflow; runtime invokes backend; result visible.
- Tasks: schema types; runtime action service; sample binding.
- Dependencies: O1-S2, O2-S3
- Estimate: 5 pts
- Labels: angular, runtime, schema

---

EPIC O3 — Advanced Security & Auditing
Objective
- Provide HIPAA-aligned auditing and Field-Level Security with runtime enforcement and basic UI tooling.

Epic acceptance criteria
- Audit log records CRUD + workflow transitions with who/when/what/entity/id/IP/UA and before/after hash.
- CSV export endpoint streams; filter query works.
- FLS enforced on read (redact/omit) and write (reject); runtime hides/disables restricted fields.

Stories
#### O3-S1 Audit model + repository + service
- Description: Implement appbana_audit_log and AuditService; wrap CRUD + workflow actions.
- Acceptance: every create/update/delete and transition appends a log; unit tests validate schema.
- Tasks: table; service; integrations; tests.
- Dependencies: none; integrate with O2-S3 later.
- Estimate: 5 pts
- Labels: backend, audit, security

#### O3-S2 Audit query + CSV export
- Description: Implement POST /audit/query (filters) and GET /audit/export.csv.
- Acceptance: filter by user/entity/date returns results; CSV downloads; performance acceptable for 100k rows (streamed).
- Tasks: query builder; CSV streaming; tests.
- Dependencies: O3-S1
- Estimate: 5 pts
- Labels: backend, api, audit

#### O3-S3 Field-Level Security engine (backend)
- Description: FLS rule storage + resolver; Redactor (read) and WriteEnforcer (write) integrated in CRUD handlers.
- Acceptance: restricted fields omitted/redacted; write attempts rejected with 403/422.
- Tasks: rules model/storage; redactor; write enforcement; tests.
- Dependencies: none
- Estimate: 8 pts
- Labels: backend, security

#### O3-S4 Runtime FLS enforcement (UI)
- Description: Hide/disable field inputs in runtime based on FLS; add designer preview toggle for permissions.
- Acceptance: fields respect FLS visibility/disabled states; preview works.
- Tasks: runtime binding; preview UI; tests.
- Dependencies: O1-S2, O3-S3
- Estimate: 5 pts
- Labels: angular, runtime, security

#### O3-S5 Audit UI integration
- Description: Connect O1-S5 Audit page to /audit/query and /audit/export.csv.
- Acceptance: list updates by filters; export downloads CSV.
- Tasks: API integration; UX polish (loading/error states).
- Dependencies: O1-S5, O3-S2
- Estimate: 3 pts
- Labels: angular, audit, ux

---

EPIC O4 — Foundational Plugin API
Objective
- Establish a plugin architecture for components, data connectors, and action types, and ship an example plugin.

Epic acceptance criteria
- DI-based registry for component/data/action types with docs.
- Signature Pad component works as a plugin; data connector skeleton demonstrated.

Stories
#### O4-S1 Define DI tokens + registry (UI)
- Description: Create injection tokens and registries for component, data connector, and action plugins.
- Acceptance: core components register via the same path; unit test covers registration/lookup.
- Tasks: tokens; registries; tests.
- Dependencies: O1-S1
- Estimate: 3 pts
- Labels: angular, extensibility

#### O4-S2 Signature Pad component plugin
- Description: Build a plugin that provides a SignaturePad component with outputs and form bindings; document usage.
- Acceptance: component renders; emits image/data; bindable in designer.
- Tasks: component; plugin wrapper; docs.
- Dependencies: O4-S1
- Estimate: 5 pts
- Labels: angular, component, plugin

#### O4-S3 Data connector skeleton plugin
- Description: Create a skeleton data connector plugin (e.g., simple REST wrapper) demonstrating registration and binding.
- Acceptance: connector registers; appears in data source picker; fetches sample data.
- Tasks: plugin interface; example; docs.
- Dependencies: O4-S1
- Estimate: 3 pts
- Labels: angular, data, plugin

---

Traceability
- Story IDs used in `TODO.md` link to the anchors in this file for quick navigation:
  - O1-S1, O1-S2, O1-S3, O1-S4, O1-S5
  - O2-S1, O2-S2, O2-S3, O2-S4
  - O3-S1, O3-S2, O3-S3, O3-S4, O3-S5
  - O4-S1, O4-S2, O4-S3
- Open `TODO.md` → October 2025 → each checklist item includes a markdown link to the corresponding story anchor in this file.
- Use these anchors for deep links in tickets/PRs as needed (e.g., `#o3-s3-field-level-security-engine-backend`).

---

Risks & Mitigations (October)
- Scope overlap between O1 (UI) and O3 (Audit UI): coordinate to avoid duplicate effort; use stubs early.
- FLS complexity: start with rule model that covers common cases; defer advanced conditions to November if needed.
- Idempotency correctness: enforce server-side keys and test high-frequency duplicate transition requests.

Definition of Done (per story)
- Code merged with CI green; unit/integration tests updated and passing.
- Docs updated (README, FUNCTIONAL_SPEC or LOW_LEVEL_DESIGN as needed).
- Manual smoke where applicable; acceptance criteria met.
