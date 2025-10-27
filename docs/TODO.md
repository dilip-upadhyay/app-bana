# Q4 2025 Delivery Plan — Aligned with Product_AppBana

**🎯 PRIMARY FOCUS: Making Studio Builder extremely powerful and user-friendly**

This plan is the actionable backlog for October–December 2025. Keep it synchronized with `Product_AppBana.md` (§5 acceptance criteria; §17 logistics) and update checkboxes as items are delivered.

**ALL NEW DEVELOPMENT EFFORT IS FOCUSED ON STUDIO BUILDER** - creating the most powerful, intuitive visual builder for metadata-driven applications.

## October 2025 — Enterprise Foundation (MVP)
- [ ] **Custom UI Studio Foundation (MVP) — PRIMARY FOCUS**  
  **Progress: Phase A (Foundation) complete; now transitioning to Phase B (Power User Features)**
  - [x] Scaffold minimal Studio workspace (plain TS/JS + build script). [O1-S1](OCT_2025_EPICS_STORIES.md#o1-s1-scaffold-studio-workspace)
  - [x] Implement component registry + core components Container/Text/Button. [O1-S2](OCT_2025_EPICS_STORIES.md#o1-s2-minimal-runtime-renderer)
  - [x] Demo metadata JSON + unknown component placeholder. [O1-S2](OCT_2025_EPICS_STORIES.md#o1-s2-minimal-runtime-renderer)
  - [x] Recursive runtime renderer (walk nodes, attach props, children).
  - [x] Vitest renderer test (assert demo renders & unknown placeholder used).
  - [x] Builder Shell with interactive canvas, inspector panel, token editor. [O1-S3](OCT_2025_EPICS_STORIES.md#o1-s3-designer-shell--settings)
  - [x] Full keyboard navigation (⌘P palette, ⌘D duplicate, shortcuts for all actions).
  - [x] Drag-drop components, inline editing, undo/redo (100 operations).
  - [ ] `/ui/studio` packaging into fat JAR (final build configuration).
  - [ ] **PHASE B FEATURES (NEW FOCUS):**
    - [ ] Visual WYSIWYG canvas with live component rendering
    - [ ] Click-to-select on rendered components (sync with tree)
    - [ ] Multi-select for bulk operations
    - [ ] Copy/paste components within and across pages
    - [ ] Property editor with type-specific controls (color picker, slider)
    - [ ] Data binding visual editor (connect to API endpoints)
    - [ ] Action builder (event handlers, navigation, forms)
    - [ ] Responsive preview modes (mobile/tablet/desktop)
    - [ ] Component library panel with drag-to-add
    - [ ] Template/snippet system for reusable patterns
  - [ ] Basic audit log UI scaffold (list/export stub). [O1-S5](OCT_2025_EPICS_STORIES.md#o1-s5-audit-ui-scaffold)
  - [ ] Styling baseline: tokens (CSS variables) + minimal utility classes per `docs/STYLE_GUIDE.md`.
- [ ] Server-side Workflow Engine (MVP)
  - [ ] Persist workflow definitions/instances & transitions (draft/submitted/approved/rejected) with idempotency. [O2-S1](OCT_2025_EPICS_STORIES.md#o2-s1-schema--migrations-for-workflows)
  - [ ] Definition APIs (create/list). [O2-S2](OCT_2025_EPICS_STORIES.md#o2-s2-definition-apis)
  - [ ] Instance + transition APIs with history. [O2-S3](OCT_2025_EPICS_STORIES.md#o2-s3-instance--transition-apis)
  - [ ] UI schema extension + runtime actions (start/approve). [O2-S4](OCT_2025_EPICS_STORIES.md#o2-s4-ui-schema--runtime-actions)
- [ ] Advanced Security & Auditing
  - [x] Baseline CRUD audit (INSERT/UPDATE/DELETE + batch rows). [O3-S1]
  - [ ] Workflow transition audit linkage. [O3-S1]
  - [ ] Export CSV + filters (user/entity/date/op). [O3-S2](OCT_2025_EPICS_STORIES.md#o3-s2-audit-query--csv-export)
  - [ ] Field-Level Security (FLS) engine backend. [O3-S3](OCT_2025_EPICS_STORIES.md#o3-s3-field-level-security-engine-backend)
  - [ ] Runtime FLS enforcement (hide/disable / redact). [O3-S4](OCT_2025_EPICS_STORIES.md#o3-s4-runtime-fls-enforcement-ui)
  - [ ] Audit UI integration. [O3-S5](OCT_2025_EPICS_STORIES.md#o3-s5-audit-ui-integration)
- [ ] Foundational Plugin API
  - [x] Component registration mechanism (registry) established. [O4-S1](OCT_2025_EPICS_STORIES.md#o4-s1-define-plugin-registry)
  - [ ] Data connector skeleton plugin. [O4-S3](OCT_2025_EPICS_STORIES.md#o4-s3-data-connector-skeleton-plugin)
  - [ ] Example component: Signature Pad plugin. [O4-S2](OCT_2025_EPICS_STORIES.md#o4-s2-signature-pad-component-plugin)
  - [ ] Document plugin contract (loading boundary, theming, lifecycle).

Acceptance criteria: see `Product_AppBana.md` §5 October.

## November 2025 — Logistics & HR Acceleration (MVP)
- [ ] PWA/Offline
  - [ ] Installable, cache static assets and last-used pages; queue-and-replay writes; background sync.
- [ ] Real-time Connectors
  - [ ] WebSocket DataSource stable with backoff; auth header propagation.
  - [ ] MQTT DataSource (wss) with reconnect/backoff; message transform; auth headers.
- [ ] Barcode/QR Scanner Component
  - [ ] Mobile-friendly capture; debounce; events wired to forms/actions.
- [ ] Reporting & Export (MVP)
  - [ ] Visual report designer (columns/groups/totals) and CSV/Excel export.
  - [ ] Audit report access.
- [ ] Workflows & Permissions
  - [ ] Multi-actor approvals with role assignment and SLA timers + escalations.
  - [ ] Relationship-based permission checks (e.g., "manager of").
- [ ] Multi-tenant Scoping (MVP)
  - [ ] tenantId propagation in auth/session; UI/API query scoping; designer simulation for role/tenant.

Acceptance criteria: see `Product_AppBana.md` §5 November and §17.4.

## December 2025 — Healthcare & Platform Leadership (MVP)
- [ ] FHIR R4 Connector (read-only)
  - [ ] Configure base URL + auth; Patient/Observation/Encounter reads + search params.
  - [ ] All access audited as PHI.
- [ ] Patient History Timeline Component
  - [ ] Render encounters/observations; filters; a11y; handle 1k+ events smoothly.
- [ ] Design Versioning & Marketplace
  - [ ] Save with semantic version + notes; diff + rollback; publish permissions.
  - [ ] Marketplace (MVP) for enabling first-party plugins (Signature Pad, Barcode, Timeline, FHIR) with signed manifests.
- [ ] Logistics Addendum
  - [ ] Document Store (MVP): upload/view PDFs/images; checksums; audited access.
  - [ ] Exception Rules & Alerts (MVP): rule DSL; sendEmail/sendSms actions; alert audit entries.
  - [ ] Emissions Estimator (MVP): voyage CO2e calculator (guidance).

Acceptance criteria: see `Product_AppBana.md` §5 December and §17.4.

---

## Phase A (Studio Foundation) – Alignment Snapshot
(See `docs/UI_Development_Plan.md` §7 for detailed exit criteria.)
- [x] metadata.ts (PageMeta, ComponentNode, ThemeMeta, NavigationMeta, Binding, Action)
- [x] registry bootstrap (auto-register core built-ins)
- [x] demo-page.json created (demo)
- [x] Renderer recursive children traversal + prop application
- [x] Unknown component placeholder element
- [x] Vitest harness + first renderer test
- [x] Builder Shell integration (canvas with tree view, inspector panel, token panel)
- [x] Studio entry point (studio-entry.ts bootstraps BuilderShell)
- [x] Interactive component tree (selection, expand/collapse, drag-drop, inline edit)
- [x] Keyboard shortcuts (⌘P search, ⌘D duplicate, ⌘⇧C copy ID, Delete/Backspace remove)
- [x] **THREE-PANEL SPLIT-SCREEN VIEW** (tree + live preview + inspector)
- [x] **Live WYSIWYG preview** with click-to-select and real-time updates
- [x] **Responsive preview modes** (desktop/tablet/mobile) with zoom controls
- [ ] `/ui/studio` packaging into fat JAR
- [ ] README + Copilot Guide component contribution update (partial; finalize after packaging)

**Progress: Phase A Builder foundation complete with professional three-panel UI! Ready for packaging and Phase B power features.**

## Backlog (Post-Q4 or Nice-to-Haves)
- OpenAPI enhancements (response schemas/examples); optionally bundle Swagger UI locally.
- Pagination/sorting/filtering for GET /api/{entity} with OpenAPI reflection.
- Import/export for datasources and schemas (JSON) via UI & API.
- Optional Spring Boot adapter; Docker/Compose polish; Helm chart.
- Unit tests: OpenApiGenerator, SchemaManager.generateMigrationPlan, ApiServer handlers, URL builder.
- Migration engine improvements (safe rename, rollback preview, dry-run per DB).
- PDF report rendering; FHIR write operations and SMART on FHIR; DICOM viewer; real-time designer collaboration.

---

## Notes
- Keep this file aligned with `Product_AppBana.md` and `UI_Development_Plan.md`.
- Update docs and change logs when checking off items.
- Legend: ✅ = done, ☐ = pending.
