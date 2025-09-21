# Q4 2025 Delivery Plan — Aligned with Product_AppBana

This plan is the actionable backlog for October–December 2025. Keep it synchronized with `Product_AppBana.md` (§5 acceptance criteria; §17 logistics) and update checkboxes as items are delivered.

## October 2025 — Enterprise Foundation (MVP)
- [ ] Angular 21 UI Foundation (MVP)
  - [ ] Scaffold Angular workspace (Nx): apps/studio; libs/runtime, libs/designer, libs/ui-schema.
  - [ ] Implement minimal runtime renderer ("Hello from Runtime") and designer shell with Container/Text/Button + Settings (token input).
  - [ ] Wire HttpInterceptor for X-AppBana-Token; ensure Node LTS via `.nvmrc`; add dev/test/lint scripts.
  - [ ] Basic audit log UI scaffold (list/export CSV stub).
- [ ] Server-side Workflow Engine (MVP)
  - [ ] Persist workflow instances and transitions (draft/submitted/approved/rejected) with idempotency.
  - [ ] UI schema: add `workflows` and map actions to transitions; designer bindings.
  - [ ] Runtime: start/approve actions; resume by owner.
- [ ] Advanced Security & Auditing
  - [ ] Server-side audit records for all CRUD and workflow transitions (who/when/what/entity/id/IP/UA + before/after hash).
  - [ ] Export CSV + filters (user/entity/date).
  - [ ] Field-Level Security (FLS): enforce on read (redact/omit) and write (reject), and honor hide/disable in runtime.
- [ ] Foundational Plugin API
  - [ ] Component/data-connector/action registration (DI multi-providers) with docs.
  - [ ] Example component: Signature Pad shipped.
  - [ ] Data connector skeleton ready.

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
