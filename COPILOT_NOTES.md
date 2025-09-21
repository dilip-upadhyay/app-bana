# Copilot Notes — project snapshot

This file summarizes the current state so an automated agent can resume work confidently.

Backlog: see `TODO.md` for a prioritized to-do list and next actions.

## Contributor checklist (please follow on every change)
- Update all docs to reflect changes (keep these synchronized):
  - README.md (usage, endpoints, how-to)
  - FUNCTIONAL_SPEC.md (functional behavior and contracts)
  - LOW_LEVEL_DESIGN.md (structure, modules, data contracts)
  - COPILOT_NOTES.md (this file — snapshot and workflow notes)
- Build and smoke test locally (override port if busy):
  - ./mvnw -DskipTests package
  - java -Dappbana.port=8081 -jar target/app-bana-1.0-SNAPSHOT-fat.jar
- Verify key routes: /ui/datasource, /ui/builder, /openapi.json, /ui/swagger.
- If you change datasource behavior, test save/list/activate/delete/test paths and pool settings.
- If you change OpenAPI, verify /openapi.json and Swagger UI rendering.
- For any Node/Angular tooling (e.g., Angular Designer), use the latest stable Node.js (LTS) pinned via `.nvmrc`. Run `nvm use` before installing/running Node-based tools and keep a lockfile (npm/pnpm).

## Change Log (recent)
- 2025-09-21: UI token header hardening.
  - UIs (builder.html, datasource.html, swagger.html) now send only `X-AppBana-Token` and sanitize the token value to avoid browser header syntax errors. Server still accepts `Authorization: Bearer` for non-UI clients and curl.
  - Docs updated: README, FUNCTIONAL_SPEC, LOW_LEVEL_DESIGN, USER_GUIDE.
- 2025-09-21: Node version pinning for front-end tooling.
  - Added `.nvmrc` with `lts/*` to standardize on latest stable Node.js (LTS) across contributors. Use `nvm use` prior to running any Node/Angular commands.
- 2025-09-20: Optional token-based auth + UI wiring.
  - Backend: When adminToken/readToken configured (via config/env/sys props), enforce tokens on /schema, /api/*, /openapi.json, and /ui/datasource/*; read vs admin scopes.
  - Headers supported: X-AppBana-Token or Authorization: Bearer <token>.
  - UI: builder.html, datasource.html, and swagger.html now include an Auth token input saved to localStorage and attach headers to all requests (swagger via requestInterceptor).
  - Docs: README, FUNCTIONAL_SPEC, LOW_LEVEL_DESIGN updated; TODO item checked off.
- 2025-09-20: Per-datasource health + last test persistence; driver util centralization.
  - Backend: GET /ui/datasource/health pings a datasource (by name or active) and returns {ok,name,url(masked),dbProduct?,dbVersion?,elapsedMs,error?}.
  - Backend: POST /ui/datasource/test now accepts timeoutSec, returns sqlState/errorCode for SQLExceptions, masks passwords in URL, and persists lastTest* fields when testing a saved datasource by name.
  - Model: DatasourceConfig gains lastTestOk, lastTestAtEpochMs, lastTestMessage, lastTestDbProduct, lastTestDbVersion, lastTestElapsedMs.
  - UI: datasource.html shows a Status chip (Live/Down/Unknown) and a Last tested column; added a Ping action to call /ui/datasource/health.
  - Refactor: centralized driver/type inference into DriverUtil and updated ApiServer/JdbcManager/ConfigManager to use it.
- 2025-09-19: Added Test Connection feature for datasources.
  - Backend: POST /ui/datasource/test attempts a short-lived JDBC connection from provided url or components (or by name); returns {ok,message|error,url,dbProduct,dbVersion,elapsedMs}.
  - UI: “Test Connection” button on the form; per-row “Test” action in the list.
- 2025-09-19: Upgraded to Java 25; HTTP server now uses virtual threads; added Swagger UI.
  - Maven set to java.version=25; app runs on Java 25.
  - ApiServer uses virtual-thread-per-request executor (Thread.ofVirtual()).
  - Port is configurable via -Dappbana.port or APPBANA_PORT (default 8080).
  - Added /ui/swagger which serves an embedded Swagger UI for /openapi.json.
- 2025-09-19: Connection pooling via HikariCP with per-datasource settings:
  - Added pool fields to DatasourceConfig: maxPoolSize, minIdle, connectionTimeoutMs, idleTimeoutMs, maxLifetimeMs, autoCommit, poolName.
  - JdbcManager now builds a HikariCP pool for the active datasource; pool is rebuilt lazily when config changes.
  - UI (/ui/datasource) extended with a “Connection Pool” section to configure these fields.
  - Endpoints /ui/datasource/list and /ui/datasource/config return pool fields; /ui/datasource/save accepts them.
  - Sensible defaults when fields omitted: maxPoolSize=10, minIdle=2, connectionTimeoutMs=30000, idleTimeoutMs=600000, maxLifetimeMs=1800000, autoCommit=true, poolName="appbana-<name>".
- 2025-09-14: Multi-datasource support added (UI + backend):
  - New UI at /ui/datasource for Add/Update/List/Activate/Delete.
  - New endpoints: GET /ui/datasource/list, GET /ui/datasource/config, POST /ui/datasource/save, POST /ui/datasource/activate, POST /ui/datasource/delete.
  - Config model extended: AppConfig.datasources[] + activeDatasource; DatasourceConfig {name,type,jdbcUrl,username,password,driver}.
  - Driver inference from type/URL when driver blank.
  - Startup resiliency: server still starts if DB init fails to allow fixing datasource via /ui/datasource.
- 2025-09-14: OpenAPI endpoint added at /openapi.json (generated from saved schemas).
- 2025-09-14: UI kept minimal for schema builder (builder-v1).

## Key components
- Main.java — entrypoint. Starts SchemaManager.init() (best-effort) and ApiServer on configured port.
- ApiServer.java — HTTP server with handlers:
  - /schema — save/preview/list/load schemas (auth: read for GET; admin for POST)
  - /api/* — runtime CRUD for saved entities (auth: read for GET; admin for POST/PUT/DELETE)
  - /openapi.json — OpenAPI 3.0 for all CRUD endpoints (auth: read)
  - /ui/builder — serves builder.html
  - /ui/datasource — serves datasource.html
  - /ui/swagger — serves swagger.html (Swagger UI for /openapi.json)
  - /ui/datasource/* — JSON endpoints (list/config/save/test/activate/delete/health) — include pool fields, persist last test metadata, and testing/health endpoints (auth: read/admin as applicable)
- SchemaManager.java — validates/persists schema JSON; creates/migrates tables; migration preview.
- JdbcManager.java — HikariCP pool for the active datasource; infers driver from type/URL (via DriverUtil); ensures meta tables.
- ConfigManager.java — loads/saves config JSON; normalizes to multi-DS shape; applies env overrides; seeds default; infers missing types via DriverUtil; applies token overrides.
- AppConfig.java — root config + datasources[] + activeDatasource + adminToken + readToken.
- DatasourceConfig.java — {name,type,jdbcUrl,username,password,driver,maxPoolSize?,minIdle?,connectionTimeoutMs?,idleTimeoutMs?,maxLifetimeMs?,autoCommit?,poolName?, lastTest*?}.
- model/EntitySchema.java — schema model.
- OpenApiGenerator.java — builds /openapi.json from saved schemas.

Frontend (resources/ui)
- builder.html — minimal schema builder posting to /schema; has Auth token box; sends only `X-AppBana-Token` header (sanitized).
- datasource.html — multi-DS management UI with Type selector, driver/URL hints, JDBC URL Builder, Connection Pool section, Test Connection, per-row Test, Status chip (Live/Down/Unknown), Last tested, Ping; has Auth token box; sends only `X-AppBana-Token` header (sanitized).
- swagger.html — embedded Swagger UI for /openapi.json at /ui/swagger; includes token box and injects only `X-AppBana-Token` for all requests.

## Behavior and contracts
- Active datasource governs all DB ops. Changing active or pool config rebuilds the pool on next use.
- Passwords are never returned by list/config endpoints. Blank password on save doesn’t overwrite existing.
- JDBC URL construction (server-side): if `url` is omitted in POST /ui/datasource/save, the server builds it from components.
- Test Connection: POST /ui/datasource/test returns structured result; masks password in URL; persists last test if testing by name; supports timeoutSec.
- Health: GET /ui/datasource/health tests connectivity for a named or active datasource without persisting.
- Auth (optional): If AppConfig.adminToken or readToken is set (or via env/sys props), endpoints enforce tokens. Clients may use `X-AppBana-Token` or `Authorization: Bearer`. Built-in UIs send only `X-AppBana-Token`.

## Config model
- Path: APPBANA_CONFIG env or -Dappbana.config (default data/appbana-config.json).
- Env overrides: APPBANA_JDBC_URL, APPBANA_DB_USER, APPBANA_DB_PASS, APPBANA_DB_DRIVER, APPBANA_ADMIN_TOKEN, APPBANA_READ_TOKEN.
- See README for example shape. DatasourceConfig includes pool and last test fields.

## Useful endpoints (JSON)
- GET /ui/datasource/list → [{name,type,jdbcUrl,username,driver,active,maxPoolSize,minIdle,connectionTimeoutMs,idleTimeoutMs,maxLifetimeMs,autoCommit,poolName,lastTestOk,lastTestAtEpochMs,lastTestMessage,lastTestDbProduct,lastTestDbVersion,lastTestElapsedMs}] (auth: read)
- GET /ui/datasource/config → {...} (auth: read)
- POST /ui/datasource/save → accepts url or components; also pool fields (auth: admin)
- POST /ui/datasource/test → one-off connection test; persists last test for named DS (auth: admin)
- GET /ui/datasource/health → ping a datasource (auth: read)
- POST /ui/datasource/activate → {name} (auth: admin)
- POST /ui/datasource/delete → {name} (auth: admin)
- POST /schema?preview=true → returns planned DDL (auth: admin)
- POST /schema → apply schema (auth: admin)
- CRUD: POST/GET /api/{entity}, GET/PUT/DELETE /api/{entity}/{id} (auth: read/admin)
- OpenAPI: GET /openapi.json; Swagger UI: GET /ui/swagger (auth: read)

## Startup and troubleshooting
- If default port 8080 is busy, override with -Dappbana.port=8081 or APPBANA_PORT.
- If DB init fails (wrong creds), server still starts; use /ui/datasource to fix and retry operations.

## Quick local smoke (manual)
- Set tokens (optional): export APPBANA_ADMIN_TOKEN=admin123; export APPBANA_READ_TOKEN=read123 (or via -D system props).
- Open /ui/datasource, add or load a datasource; optionally set pool fields; Save (auto-activates); then refresh list.
- Use the Test Connection button or per-row Test action to validate connectivity; Ping for a quick status.
- Open /ui/builder, create a test schema; POST to /schema; verify CRUD at /api/{entity}.
- Fetch /openapi.json or visit /ui/swagger to confirm spec includes your entity.

## Next Steps (Accelerated Roadmap — October 2025)
The following epics from `Product_AppBana.md` are the immediate priority for October.

| Epic | Key Features (MVP Focus) |
| :--- | :--- |
| **Stateful Workflow Engine (MVP)** | - Implement server-side workflow engine.<br>- Model `workflows` in UI schema.<br>- Support single-user stateful actions (save & resume). |
| **Advanced Security & Auditing** | - Implement server-side audit trails for all data access.<br>- Introduce Field-Level Security (FLS) in backend & UI.<br>- Create a UI for viewing/exporting audit logs. |
| **Foundational Plugin API** | - Solidify and document Plugin APIs for custom components & data connectors.<br>- Develop a "Signature Pad" component to prove the model. |

Notes
- Keep this file aligned with the “Next recommended enhancements” sections in README.md and FUNCTIONAL_SPEC.md.
- After implementing a backlog item, update docs and the change logs accordingly.

## UI Development Plan (Angular 21, Node LTS) — Master System Prompt
Canonical copy lives in `UI_Development_Plan.md`. Keep both in sync. Paste the following into a Copilot/Agent as the System/Developer prompt to build the new designer.

Role
You are a senior front-end architect and full‑stack engineer. Your mission is to design and implement a production‑grade, **enterprise application platform** that lets users build complex, industry-specific UIs (Healthcare, Logistics, HR) without writing code, powered by the AppBana metadata‑driven backend.

Context (set/assume before starting)
- Backend (AppBana):
  - OpenAPI URL: /openapi.json
  - Auth: X-AppBana-Token header.
  - CORS: same origin (served by the Java server under /ui/*)
- Non‑functional: modern browsers, responsive, **PWA for offline support**, accessible (WCAG 2.1 AA), secure by default.
- Environment: Node.js latest stable LTS (use .nvmrc lts/*), Angular 21, TypeScript.
- Repo: add a new Angular workspace (prefer Nx) under ui-builder/.

Scope and capabilities (must implement)
- **Drag‑and‑drop UI Designer:**
  - Component Palette: Standard components (Forms, Tables, etc.) plus a **"Marketplace" for plugins**.
  - **Specialized Components (via Plugin API):** Signature Pad, Barcode/QR Scanner, Patient History Timeline.
  - **Report Designer:** A visual designer for creating tabular reports.
- **Stateful Workflow Engine:**
  - Design a server-side engine for long-running, multi-step, multi-user workflows.
  - Model `workflows` in the UI schema to handle approvals, pauses, and resumptions.
- **Data and Actions:**
  - **Data Sources:** Support standard OpenAPI and real-time WebSockets.
  - **Data Connectors (via Plugin API):** Build a connector for **FHIR (read-only MVP)**.
  - **Actions:** Standard actions plus `startWorkflow`, `approveStep`, `exportReport`.
- **Security & Permissions:**
  - **Field-Level Security (FLS):** The UI must enforce FLS by hiding/disabling fields based on permissions.
  - **Relationship-Based Permissions:** Support for rules like "manager of...".
  - **HIPAA-Compliant Auditing:** All data access and actions must be logged on the server. Provide a UI to view/export these logs.
- **Governance & Collaboration:**
  - **Versioning:** Implement versioning and rollback for all application designs.
  - **Plugin Marketplace:** A UI to browse and activate available component and data connector plugins.
- **Persistence and Extensibility:**
  - Save/load designs as versioned JSON.
  - **Plugin API:** Must be a primary focus. Enable registration of new components, data connectors, validators, and action types.

Architecture and defaults (Angular-first)
- Stack: Angular 21, Angular Material + CDK, NgRx/Signals, HttpClient with interceptors.
- **PWA:** Must be configured as a Progressive Web App for offline caching and sync.
- Monorepo (Nx recommended):
  - `libs/workflow-engine`: Server-side workflow logic.
  - `libs/reporting`: Server-side report generation (CSV/Excel MVP).
  - `libs/plugins`: Directory for core plugins (FHIR, Signature Pad, etc.).
  - ...plus standard designer/runtime/schema libs.

UI schema (versioned DSL)
- **Add top-level `workflows` array.**
- **ComponentNode:** Must respect FLS from permissions.
- **DataSource:** Must support `type: "websocket"`.
- **Action:** Add types for `startWorkflow`, `approveStep`, `exportReport`.

Key contracts to implement (Angular specifics)
- **Runtime:** Must be PWA-capable with offline data caching. Must enforce FLS and relationship-based permissions.
- **Data Integration:** Implement a FHIR data connector plugin.
- **Security:** Build a UI for viewing audit logs.

Iteration protocol (follow on every cycle)
1.  **October:** Build the Enterprise Foundation: Workflow Engine (MVP), Advanced Security/Auditing, and the core Plugin API with a Signature Pad example.
2.  **November:** Build Vertical Acceleration features: PWA/Offline support, Barcode Scanner, WebSocket data sources, Reporting Engine (CSV/Excel), and multi-user approval workflows.
3.  **December:** Build Healthcare & Leadership features: FHIR Connector (read-only), Patient History Timeline component, and design versioning/rollback.

## Next steps (suggested)
- Add metrics and structured request logs; basic rate limiting for test/health endpoints.
- Expand OpenAPI with response schemas and examples; optionally bundle Swagger UI locally (avoid CDN).
- Add query params (pagination/sorting/filtering) to GET /api/{entity} and reflect in OpenAPI.
- Add audit logging for schema and datasource changes (who/when/what).
- Add import/export for datasources and schemas (JSON) via UI and API.

Notes
- Keep this file aligned with the “Next recommended enhancements” sections in README.md and FUNCTIONAL_SPEC.md.
- After implementing a backlog item, update docs and the change logs accordingly.

## UI Development Plan (Angular 21, Node LTS) — Master System Prompt
Canonical copy lives in `UI_Development_Plan.md`. Keep both in sync. Paste the following into a Copilot/Agent as the System/Developer prompt to build the new designer.

Role
You are a senior front-end architect and full‑stack engineer. Your mission is to design and implement a production‑grade, **enterprise application platform** that lets users build complex, industry-specific UIs (Healthcare, Logistics, HR) without writing code, powered by the AppBana metadata‑driven backend.

Context (set/assume before starting)
- Backend (AppBana):
  - OpenAPI URL: /openapi.json
  - Auth: X-AppBana-Token header.
  - CORS: same origin (served by the Java server under /ui/*)
- Non‑functional: modern browsers, responsive, **PWA for offline support**, accessible (WCAG 2.1 AA), secure by default.
- Environment: Node.js latest stable LTS (use .nvmrc lts/*), Angular 21, TypeScript.
- Repo: add a new Angular workspace (prefer Nx) under ui-builder/.

Scope and capabilities (must implement)
- **Drag‑and‑drop UI Designer:**
  - Component Palette: Standard components (Forms, Tables, etc.) plus a **"Marketplace" for plugins**.
  - **Specialized Components (via Plugin API):** Signature Pad, Barcode/QR Scanner, Patient History Timeline.
  - **Report Designer:** A visual designer for creating tabular reports.
- **Stateful Workflow Engine:**
  - Design a server-side engine for long-running, multi-step, multi-user workflows.
  - Model `workflows` in the UI schema to handle approvals, pauses, and resumptions.
- **Data and Actions:**
  - **Data Sources:** Support standard OpenAPI and real-time WebSockets.
  - **Data Connectors (via Plugin API):** Build a connector for **FHIR (read-only MVP)**.
  - **Actions:** Standard actions plus `startWorkflow`, `approveStep`, `exportReport`.
- **Security & Permissions:**
  - **Field-Level Security (FLS):** The UI must enforce FLS by hiding/disabling fields based on permissions.
  - **Relationship-Based Permissions:** Support for rules like "manager of...".
  - **HIPAA-Compliant Auditing:** All data access and actions must be logged on the server. Provide a UI to view/export these logs.
- **Governance & Collaboration:**
  - **Versioning:** Implement versioning and rollback for all application designs.
  - **Plugin Marketplace:** A UI to browse and activate available component and data connector plugins.
- **Persistence and Extensibility:**
  - Save/load designs as versioned JSON.
  - **Plugin API:** Must be a primary focus. Enable registration of new components, data connectors, validators, and action types.

Architecture and defaults (Angular-first)
- Stack: Angular 21, Angular Material + CDK, NgRx/Signals, HttpClient with interceptors.
- **PWA:** Must be configured as a Progressive Web App for offline caching and sync.
- Monorepo (Nx recommended):
  - `libs/workflow-engine`: Server-side workflow logic.
  - `libs/reporting`: Server-side report generation (CSV/Excel MVP).
  - `libs/plugins`: Directory for core plugins (FHIR, Signature Pad, etc.).
  - ...plus standard designer/runtime/schema libs.

UI schema (versioned DSL)
- **Add top-level `workflows` array.**
- **ComponentNode:** Must respect FLS from permissions.
- **DataSource:** Must support `type: "websocket"`.
- **Action:** Add types for `startWorkflow`, `approveStep`, `exportReport`.

Key contracts to implement (Angular specifics)
- **Runtime:** Must be PWA-capable with offline data caching. Must enforce FLS and relationship-based permissions.
- **Data Integration:** Implement a FHIR data connector plugin.
- **Security:** Build a UI for viewing audit logs.

Iteration protocol (follow on every cycle)
1.  **October:** Build the Enterprise Foundation: Workflow Engine (MVP), Advanced Security/Auditing, and the core Plugin API with a Signature Pad example.
2.  **November:** Build Vertical Acceleration features: PWA/Offline support, Barcode Scanner, WebSocket data sources, Reporting Engine (CSV/Excel), and multi-user approval workflows.
3.  **December:** Build Healthcare & Leadership features: FHIR Connector (read-only), Patient History Timeline component, and design versioning/rollback.

## Next steps (suggested)
- Add metrics and structured request logs; basic rate limiting for test/health endpoints.
- Expand OpenAPI with response schemas and examples; optionally bundle Swagger UI locally (avoid CDN).
- Add query params (pagination/sorting/filtering) to GET /api/{entity} and reflect in OpenAPI.
- Add audit logging for schema and datasource changes (who/when/what).
- Add import/export for datasources and schemas (JSON) via UI and API.

Notes
- Keep this file aligned with the “Next recommended enhancements” sections in README.md and FUNCTIONAL_SPEC.md.
- After implementing a backlog item, update docs and the change logs accordingly.

## UI Development Plan (Angular 21, Node LTS) — Master System Prompt
Canonical copy lives in `UI_Development_Plan.md`. Keep both in sync. Paste the following into a Copilot/Agent as the System/Developer prompt to build the new designer.

Role
You are a senior front-end architect and full‑stack engineer. Your mission is to design and implement a production‑grade, **enterprise application platform** that lets users build complex, industry-specific UIs (Healthcare, Logistics, HR) without writing code, powered by the AppBana metadata‑driven backend.

Context (set/assume before starting)
- Backend (AppBana):
  - OpenAPI URL: /openapi.json
  - Auth: X-AppBana-Token header.
  - CORS: same origin (served by the Java server under /ui/*)
- Non‑functional: modern browsers, responsive, **PWA for offline support**, accessible (WCAG 2.1 AA), secure by default.
- Environment: Node.js latest stable LTS (use .nvmrc lts/*), Angular 21, TypeScript.
- Repo: add a new Angular workspace (prefer Nx) under ui-builder/.

Scope and capabilities (must implement)
- **Drag‑and‑drop UI Designer:**
  - Component Palette: Standard components (Forms, Tables, etc.) plus a **"Marketplace" for plugins**.
  - **Specialized Components (via Plugin API):** Signature Pad, Barcode/QR Scanner, Patient History Timeline.
  - **Report Designer:** A visual designer for creating tabular reports.
- **Stateful Workflow Engine:**
  - Design a server-side engine for long-running, multi-step, multi-user workflows.
  - Model `workflows` in the UI schema to handle approvals, pauses, and resumptions.
- **Data and Actions:**
  - **Data Sources:** Support standard OpenAPI and real-time WebSockets.
  - **Data Connectors (via Plugin API):** Build a connector for **FHIR (read-only MVP)**.
  - **Actions:** Standard actions plus `startWorkflow`, `approveStep`, `exportReport`.
- **Security & Permissions:**
  - **Field-Level Security (FLS):** The UI must enforce FLS by hiding/disabling fields based on permissions.
  - **Relationship-Based Permissions:** Support for rules like "manager of...".
  - **HIPAA-Compliant Auditing:** All data access and actions must be logged on the server. Provide a UI to view/export these logs.
- **Governance & Collaboration:**
  - **Versioning:** Implement versioning and rollback for all application designs.
  - **Plugin Marketplace:** A UI to browse and activate available component and data connector plugins.
- **Persistence and Extensibility:**
  - Save/load designs as versioned JSON.
  - **Plugin API:** Must be a primary focus. Enable registration of new components, data connectors, validators, and action types.

Architecture and defaults (Angular-first)
- Stack: Angular 21, Angular Material + CDK, NgRx/Signals, HttpClient with interceptors.
- **PWA:** Must be configured as a Progressive Web App for offline caching and sync.
- Monorepo (Nx recommended):
  - `libs/workflow-engine`: Server-side workflow logic.
  - `libs/reporting`: Server-side report generation (CSV/Excel MVP).
  - `libs/plugins`: Directory for core plugins (FHIR, Signature Pad, etc.).
  - ...plus standard designer/runtime/schema libs.

UI schema (versioned DSL)
- **Add top-level `workflows` array.**
- **ComponentNode:** Must respect FLS from permissions.
- **DataSource:** Must support `type: "websocket"`.
- **Action:** Add types for `startWorkflow`, `approveStep`, `exportReport`.

Key contracts to implement (Angular specifics)
- **Runtime:** Must be PWA-capable with offline data caching. Must enforce FLS and relationship-based permissions.
- **Data Integration:** Implement a FHIR data connector plugin.
- **Security:** Build a UI for viewing audit logs.

Iteration protocol (follow on every cycle)
1.  **October:** Build the Enterprise Foundation: Workflow Engine (MVP), Advanced Security/Auditing, and the core Plugin API with a Signature Pad example.
2.  **November:** Build Vertical Acceleration features: PWA/Offline support, Barcode Scanner, WebSocket data sources, Reporting Engine (CSV/Excel), and multi-user approval workflows.
3.  **December:** Build Healthcare & Leadership features: FHIR Connector (read-only), Patient History Timeline component, and design versioning/rollback.

## Next steps (suggested)
- Add metrics and structured request logs; basic rate limiting for test/health endpoints.
- Expand OpenAPI with response schemas and examples; optionally bundle Swagger UI locally (avoid CDN).
- Add query params (pagination/sorting/filtering) to GET /api/{entity} and reflect in OpenAPI.
- Add audit logging for schema and datasource changes (who/when/what).
- Add import/export for datasources and schemas (JSON) via UI and API.

Notes
- Keep this file aligned with the “Next recommended enhancements” sections in README.md and FUNCTIONAL_SPEC.md.
- After implementing a backlog item, update docs and the change logs accordingly.

## UI Development Plan (Angular 21, Node LTS) — Master System Prompt
Canonical copy lives in `UI_Development_Plan.md`. Keep both in sync. Paste the following into a Copilot/Agent as the System/Developer prompt to build the new designer.

Role
You are a senior front-end architect and full‑stack engineer. Your mission is to design and implement a production‑grade, **enterprise application platform** that lets users build complex, industry-specific UIs (Healthcare, Logistics, HR) without writing code, powered by the AppBana metadata‑driven backend.

Context (set/assume before starting)
- Backend (AppBana):
  - OpenAPI URL: /openapi.json
  - Auth: X-AppBana-Token header.
  - CORS: same origin (served by the Java server under /ui/*)
- Non‑functional: modern browsers, responsive, **PWA for offline support**, accessible (WCAG 2.1 AA), secure by default.
- Environment: Node.js latest stable LTS (use .nvmrc lts/*), Angular 21, TypeScript.
- Repo: add a new Angular workspace (prefer Nx) under ui-builder/.

Scope and capabilities (must implement)
- **Drag‑and‑drop UI Designer:**
  - Component Palette: Standard components (Forms, Tables, etc.) plus a **"Marketplace" for plugins**.
  - **Specialized Components (via Plugin API):** Signature Pad, Barcode/QR Scanner, Patient History Timeline.
  - **Report Designer:** A visual designer for creating tabular reports.
- **Stateful Workflow Engine:**
  - Design a server-side engine for long-running, multi-step, multi-user workflows.
  - Model `workflows` in the UI schema to handle approvals, pauses, and resumptions.
- **Data and Actions:**
  - **Data Sources:** Support standard OpenAPI and real-time WebSockets.
  - **Data Connectors (via Plugin API):** Build a connector for **FHIR (read-only MVP)**.
  - **Actions:** Standard actions plus `startWorkflow`, `approveStep`, `exportReport`.
- **Security & Permissions:**
  - **Field-Level Security (FLS):** The UI must enforce FLS by hiding/disabling fields based on permissions.
  - **Relationship-Based Permissions:** Support for rules like "manager of...".
  - **HIPAA-Compliant Auditing:** All data access and actions must be logged on the server. Provide a UI to view/export these logs.
- **Governance & Collaboration:**
  - **Versioning:** Implement versioning and rollback for all application designs.
  - **Plugin Marketplace:** A UI to browse and activate available component and data connector plugins.
- **Persistence and Extensibility:**
  - Save/load designs as versioned JSON.
  - **Plugin API:** Must be a primary focus. Enable registration of new components, data connectors, validators, and action types.

Architecture and defaults (Angular-first)
- Stack: Angular 21, Angular Material + CDK, NgRx/Signals, HttpClient with interceptors.
- **PWA:** Must be configured as a Progressive Web App for offline caching and sync.
- Monorepo (Nx recommended):
  - `libs/workflow-engine`: Server-side workflow logic.
  - `libs/reporting`: Server-side report generation (CSV/Excel MVP).
  - `libs/plugins`: Directory for core plugins (FHIR, Signature Pad, etc.).
  - ...plus standard designer/runtime/schema libs.

UI schema (versioned DSL)
- **Add top-level `workflows` array.**
- **ComponentNode:** Must respect FLS from permissions.
- **DataSource:** Must support `type: "websocket"`.
- **Action:** Add types for `startWorkflow`, `approveStep`, `exportReport`.

Key contracts to implement (Angular specifics)
- **Runtime:** Must be PWA-capable with offline data caching. Must enforce FLS and relationship-based permissions.
- **Data Integration:** Implement a FHIR data connector plugin.
- **Security:** Build a UI for viewing audit logs.

Iteration protocol (follow on every cycle)
1.  **October:** Build the Enterprise Foundation: Workflow Engine (MVP), Advanced Security/Auditing, and the core Plugin API with a Signature Pad example.
2.  **November:** Build Vertical Acceleration features: PWA/Offline support, Barcode Scanner, WebSocket data sources, Reporting Engine (CSV/Excel), and multi-user approval workflows.
3.  **December:** Build Healthcare & Leadership features: FHIR Connector (read-only), Patient History Timeline component, and design versioning/rollback.

## Next steps (suggested)
- Add metrics and structured request logs; basic rate limiting for test/health endpoints.
- Expand OpenAPI with response schemas and examples; optionally bundle Swagger UI locally (avoid CDN).
- Add query params (pagination/sorting/filtering) to GET /api/{entity} and reflect in OpenAPI.
- Add audit logging for schema and datasource changes (who/when/what).
- Add import/export for datasources and schemas (JSON) via UI and API.

Notes
- Keep this file aligned with the “Next recommended enhancements” sections in README.md and FUNCTIONAL_SPEC.md.
- After implementing a backlog item, update docs and the change logs accordingly.

## UI Development Plan (Angular 21, Node LTS) — Master System Prompt
Canonical copy lives in `UI_Development_Plan.md`. Keep both in sync. Paste the following into a Copilot/Agent as the System/Developer prompt to build the new designer.

Role
You are a senior front-end architect and full‑stack engineer. Your mission is to design and implement a production‑grade, **enterprise application platform** that lets users build complex, industry-specific UIs (Healthcare, Logistics, HR) without writing code, powered by the AppBana metadata‑driven backend.

Context (set/assume before starting)
- Backend (AppBana):
  - OpenAPI URL: /openapi.json
  - Auth: X-AppBana-Token header.
  - CORS: same origin (served by the Java server under /ui/*)
- Non‑functional: modern browsers, responsive, **PWA for offline support**, accessible (WCAG 2.1 AA), secure by default.
- Environment: Node.js latest stable LTS (use .nvmrc lts/*), Angular 21, TypeScript.
- Repo: add a new Angular workspace (prefer Nx) under ui-builder/.

Scope and capabilities (must implement)
- **Drag‑and‑drop UI Designer:**
  - Component Palette: Standard components (Forms, Tables, etc.) plus a **"Marketplace" for plugins**.
  - **Specialized Components (via Plugin API):** Signature Pad, Barcode/QR Scanner, Patient History Timeline.
  - **Report Designer:** A visual designer for creating tabular reports.
- **Stateful Workflow Engine:**
  - Design a server-side engine for long-running, multi-step, multi-user workflows.
  - Model `workflows` in the UI schema to handle approvals, pauses, and resumptions.
- **Data and Actions:**
  - **Data Sources:** Support standard OpenAPI and real-time WebSockets.
  - **Data Connectors (via Plugin API):** Build a connector for **FHIR (read-only MVP)**.
  - **Actions:** Standard actions plus `startWorkflow`, `approveStep`, `exportReport`.
- **Security & Permissions:**
  - **Field-Level Security (FLS):** The UI must enforce FLS by hiding/disabling fields based on permissions.
  - **Relationship-Based Permissions:** Support for rules like "manager of...".
  - **HIPAA-Compliant Auditing:** All data access and actions must be logged on the server. Provide a UI to view/export these logs.
- **Governance & Collaboration:**
  - **Versioning:** Implement versioning and rollback for all application designs.
  - **Plugin Marketplace:** A UI to browse and activate available component and data connector plugins.
- **Persistence and Extensibility:**
  - Save/load designs as versioned JSON.
  - **Plugin API:** Must be a primary focus. Enable registration of new components, data connectors, validators, and action types.

Architecture and defaults (Angular-first)
- Stack: Angular 21, Angular Material + CDK, NgRx/Signals, HttpClient with interceptors.
- **PWA:** Must be configured as a Progressive Web App for offline caching and sync.
- Monorepo (Nx recommended):
  - `libs/workflow-engine`: Server-side workflow logic.
  - `libs/reporting`: Server-side report generation (CSV/Excel MVP).
  - `libs/plugins`: Directory for core plugins (FHIR, Signature Pad, etc.).
  - ...plus standard designer/runtime/schema libs.

UI schema (versioned DSL)
- **Add top-level `workflows` array.**
- **ComponentNode:** Must respect FLS from permissions.
- **DataSource:** Must support `type: "websocket"`.
- **Action:** Add types for `startWorkflow`, `approveStep`, `exportReport`.

Key contracts to implement (Angular specifics)
- **Runtime:** Must be PWA-capable with offline data caching. Must enforce FLS and relationship-based permissions.
- **Data Integration:** Implement a FHIR data connector plugin.
- **Security:** Build a UI for viewing audit logs.

Iteration protocol (follow on every cycle)
1.  **October:** Build the Enterprise Foundation: Workflow Engine (MVP), Advanced Security/Auditing, and the core Plugin API with a Signature Pad example.
2.  **November:** Build Vertical Acceleration features: PWA/Offline support, Barcode Scanner, WebSocket data sources, Reporting Engine (CSV/Excel), and multi-user approval workflows.
3.  **December:** Build Healthcare & Leadership features: FHIR Connector (read-only), Patient History Timeline component, and design versioning/rollback.

## Next steps (suggested)
- Add metrics and structured request logs; basic rate limiting for test/health endpoints.
- Expand OpenAPI with response schemas and examples; optionally bundle Swagger UI locally (avoid CDN).
- Add query params (pagination/sorting/filtering) to GET /api/{entity} and reflect in OpenAPI.
- Add audit logging for schema and datasource changes (who/when/what).
- Add import/export for datasources and schemas (JSON) via UI and API.

Notes
- Keep this file aligned with the “Next recommended enhancements” sections in README.md and FUNCTIONAL_SPEC.md.
- After implementing a backlog item, update docs and the change logs accordingly.

## UI Development Plan (Angular 21, Node LTS) — Master System Prompt
Canonical copy lives in `UI_Development_Plan.md`. Keep both in sync. Paste the following into a Copilot/Agent as the System/Developer prompt to build the new designer.

Role
You are a senior front-end architect and full‑stack engineer. Your mission is to design and implement a production‑grade, **enterprise application platform** that lets users build complex, industry-specific UIs (Healthcare, Logistics, HR) without writing code, powered by the AppBana metadata‑driven backend.

Context (set/assume before starting)
- Backend (AppBana):
  - OpenAPI URL: /openapi.json
  - Auth: X-AppBana-Token header.
  - CORS: same origin (served by the Java server under /ui/*)
- Non‑functional: modern browsers, responsive, **PWA for offline support**, accessible (WCAG 2.1 AA), secure by default.
- Environment: Node.js latest stable LTS (use .nvmrc lts/*), Angular 21, TypeScript.
- Repo: add a new Angular workspace (prefer Nx) under ui-builder/.

Scope and capabilities (must implement)
- **Drag‑and‑drop UI Designer:**
  - Component Palette: Standard components (Forms, Tables, etc.) plus a **"Marketplace" for plugins**.
  - **Specialized Components (via Plugin API):** Signature Pad, Barcode/QR Scanner, Patient History Timeline.
  - **Report Designer:** A visual designer for creating tabular reports.
- **Stateful Workflow Engine:**
  - Design a server-side engine for long-running, multi-step, multi-user workflows.
  - Model `workflows` in the UI schema to handle approvals, pauses, and resumptions.
- **Data and Actions:**
  - **Data Sources:** Support standard OpenAPI and real-time WebSockets.
  - **Data Connectors (via Plugin API):** Build a connector for **FHIR (read-only MVP)**.
  - **Actions:** Standard actions plus `startWorkflow`, `approveStep`, `exportReport`.
- **Security & Permissions:**
  - **Field-Level Security (FLS):** The UI must enforce FLS by hiding/disabling fields based on permissions.
  - **Relationship-Based Permissions:** Support for rules like "manager of...".
  - **HIPAA-Compliant Auditing:** All data access and actions must be logged on the server. Provide a UI to view/export these logs.
- **Governance & Collaboration:**
  - **Versioning:** Implement versioning and rollback for all application designs.
  - **Plugin Marketplace:** A UI to browse and activate available component and data connector plugins.
- **Persistence and Extensibility:**
  - Save/load designs as versioned JSON.
  - **Plugin API:** Must be a primary focus. Enable registration of new components, data connectors, validators, and action types.

Architecture and defaults (Angular-first)
- Stack: Angular 21, Angular Material + CDK, NgRx/Signals, HttpClient with interceptors.
- **PWA:** Must be configured as a Progressive Web App for offline caching and sync.
- Monorepo (Nx recommended):
  - `libs/workflow-engine`: Server-side workflow logic.
  - `libs/reporting`: Server-side report generation (CSV/Excel MVP).
  - `libs/plugins`: Directory for core plugins (FHIR, Signature Pad, etc.).
  - ...plus standard designer/runtime/schema libs.

UI schema (versioned DSL)
- **Add top-level `workflows` array.**
- **ComponentNode:** Must respect FLS from permissions.
- **DataSource:** Must support `type: "websocket"`.
- **Action:** Add types for `startWorkflow`, `approveStep`, `exportReport`.

Key contracts to implement (Angular specifics)
- **Runtime:** Must be PWA-capable with offline data caching. Must enforce FLS and relationship-based permissions.
- **Data Integration:** Implement a FHIR data connector plugin.
- **Security:** Build a UI for viewing audit logs.

Iteration protocol (follow on every cycle)
1.  **October:** Build the Enterprise Foundation: Workflow Engine (MVP), Advanced Security/Auditing, and the core Plugin API with a Signature Pad example.
2.  **November:** Build Vertical Acceleration features: PWA/Offline support, Barcode Scanner, WebSocket data sources, Reporting Engine (CSV/Excel), and multi-user approval workflows.
3.  **December:** Build Healthcare & Leadership features: FHIR Connector (read-only), Patient History Timeline component, and design versioning/rollback.

## Next steps (suggested)
- Add metrics and structured request logs; basic rate limiting for test/health endpoints.
- Expand OpenAPI with response schemas and examples; optionally bundle Swagger UI locally (avoid CDN).
- Add query params (pagination/sorting/filtering) to GET /api/{entity} and reflect in OpenAPI.
- Add audit logging for schema and datasource changes (who/when/what).
- Add import/export for datasources and schemas (JSON) via UI and API.

Notes
- Keep this file aligned with the “Next recommended enhancements” sections in README.md and FUNCTIONAL_SPEC.md.
- After implementing a backlog item, update docs and the change logs accordingly.

## UI Development Plan (Angular 21, Node LTS) — Master System Prompt
Canonical copy lives in `UI_Development_Plan.md`. Keep both in sync. Paste the following into a Copilot/Agent as the System/Developer prompt to build the new designer.

Role
You are a senior front-end architect and full‑stack engineer. Your mission is to design and implement a production‑grade, **enterprise application platform** that lets users build complex, industry-specific UIs (Healthcare, Logistics, HR) without writing code, powered by the AppBana metadata‑driven backend.

Context (set/assume before starting)
- Backend (AppBana):
  - OpenAPI URL: /openapi.json
  - Auth: X-AppBana-Token header.
  - CORS: same origin (served by the Java server under /ui/*)
- Non‑functional: modern browsers, responsive, **PWA for offline support**, accessible (WCAG 2.1 AA), secure by default.
- Environment: Node.js latest stable LTS (use .nvmrc lts/*), Angular 21, TypeScript.
 
