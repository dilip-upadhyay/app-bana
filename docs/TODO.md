# Q4 2025 Delivery Plan — Aligned with Product_AppBana

This plan is the actionable backlog for October–December 2025. Keep it synchronized with `Product_AppBana.md` (§5 acceptance criteria; §17 logistics) and update checkboxes as items are delivered.

## October 2025 — Enterprise Foundation (MVP)
- [ ] Custom UI Studio Foundation (MVP)
  - [ ] Scaffold minimal Studio workspace (plain TS/JS + build script). [O1-S1](OCT_2025_EPICS_STORIES.md#o1-s1-scaffold-studio-workspace)
  - [ ] Implement minimal runtime renderer (Container/Text/Button) + designer shell with Settings (token persistence). [O1-S2](OCT_2025_EPICS_STORIES.md#o1-s2-minimal-runtime-renderer) · [O1-S3](OCT_2025_EPICS_STORIES.md#o1-s3-designer-shell--settings)
  - [ ] Request helper injecting X-AppBana-Token; add dev/test scripts. [O1-S4](OCT_2025_EPICS_STORIES.md#o1-s4-request-helper--scripts)
  - [ ] Basic audit log UI scaffold (list/export stub). [O1-S5](OCT_2025_EPICS_STORIES.md#o1-s5-audit-ui-scaffold)
  - [ ] Styling baseline: tokens (CSS variables) + minimal utility classes per `docs/STYLE_GUIDE.md`.
- [ ] Server-side Workflow Engine (MVP)
  - [ ] Persist workflow instances and transitions (draft/submitted/approved/rejected) with idempotency. [O2-S1](OCT_2025_EPICS_STORIES.md#o2-s1-schema--migrations-for-workflows)
  - [ ] UI schema: add `workflows` and map actions to transitions; designer bindings. [O2-S4](OCT_2025_EPICS_STORIES.md#o2-s4-ui-schema--runtime-actions)
  - [ ] Runtime: start/approve actions; resume by owner. [O2-S3](OCT_2025_EPICS_STORIES.md#o2-s3-instance--transition-apis)
  - [ ] Definition APIs (create/list) available. [O2-S2](OCT_2025_EPICS_STORIES.md#o2-s2-definition-apis)
- [ ] Advanced Security & Auditing
  - [ ] Server-side audit records for all CRUD and workflow transitions (who/when/what/entity/id/IP/UA + before/after hash). [O3-S1](OCT_2025_EPICS_STORIES.md#o3-s1-audit-model--repository--service)
  - [ ] Export CSV + filters (user/entity/date). [O3-S2](OCT_2025_EPICS_STORIES.md#o3-s2-audit-query--csv-export)
  - [ ] Field-Level Security (FLS): enforce on read (redact/omit) and write (reject), and honor hide/disable in runtime. [O3-S3](OCT_2025_EPICS_STORIES.md#o3-s3-field-level-security-engine-backend) · [O3-S4](OCT_2025_EPICS_STORIES.md#o3-s4-runtime-fls-enforcement-ui)
  - [ ] Hook Audit UI into backend. [O3-S5](OCT_2025_EPICS_STORIES.md#o3-s5-audit-ui-integration)
- [ ] Foundational Plugin API
  - [ ] Component/data-connector/action registration registry with docs. [O4-S1](OCT_2025_EPICS_STORIES.md#o4-s1-define-plugin-registry)
  - [ ] Example component: Signature Pad shipped. [O4-S2](OCT_2025_EPICS_STORIES.md#o4-s2-signature-pad-component-plugin)
  - [ ] Data connector skeleton ready. [O4-S3](OCT_2025_EPICS_STORIES.md#o4-s3-data-connector-skeleton-plugin)
  - [ ] Define plugin boundary (Web Components/module contract) and document inputs/outputs/theming/SDK bridge.

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
