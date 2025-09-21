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
- UI workspace: Angular Nx workspace lives under `ui/` (not `ui-builder/`). To scaffold or update it quickly, run `scripts/scaffold-ui.sh` after `nvm use`.

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
  - UI: datasource.html, /ui/datasource, supports a “Connection Pool” section to configure these fields.
  - Endpoints /ui/datasource/list and /ui/datasource/config return pool fields; /ui/datasource/save accepts them.
  - Sensible defaults when fields omitted: maxPoolSize=10, minIdle=2, connectionTimeoutMs=30000, idleTimeoutMs=600000, maxLifetimeMs=1800000, autoCommit=true, poolName="appbana-<name>".
- 2025-09-14: Multi-datasource support added (UI + backend):
  - New UI at /ui/datasource for Add/Update/List/Activate/Delete.
  - New endpoints: GET /ui/datasource/list, GET /ui/datasource/config, POST /ui/datasource/save, POST /ui/datasource/test, POST /ui/datasource/activate, POST /ui/datasource/delete.
  - Config model extended: AppConfig.datasources[] + activeDatasource; DatasourceConfig {name,type,jdbcUrl,username,password,driver}.
  - Driver inference from type/URL when driver blank.
  - Startup resiliency: server still starts if DB init fails to allow fixing datasource via /ui/datasource.
- 2025-09-14: OpenAPI endpoint added at /openapi.json (generated from saved schemas).

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
- Guide location: `UI_SMOKE.md` at the repo root. Run this against your local server.
- Server URL: open your browser at http://localhost:8080 (or your configured port) and exercise the UIs below.
- Steps overview (see UI_SMOKE.md for the full checklist):
  - Open /ui/datasource → add or load a datasource; optionally set pool fields; Save (auto-activates); refresh list.
  - Use Test Connection or per-row Test to validate connectivity; use Ping for a quick status.
  - Open /ui/builder → create a test schema; POST to /schema; verify CRUD at /api/{entity}.
  - Fetch /openapi.json or visit /ui/swagger → confirm spec includes your entity.
- Note: When the Angular Designer is introduced, it will be hosted at /ui/designer. The smoke remains focused on existing UIs to ensure no regressions.

## Next Steps (Accelerated Roadmap — October 2025)
The following epics from `Product_AppBana.md` are the immediate priority for October.

| Epic | Key Features (MVP Focus) |
| :--- | :--- |
| **Stateful Workflow Engine (MVP)** | - Implement server-side workflow engine.<br>- Model `workflows` in UI schema.<br>- Support single-user stateful actions (save & resume). |
| **Advanced Security & Auditing** | - Implement server-side audit trails for all data access.<br>- Introduce Field-Level Security (FLS) in backend & UI.<br>- Create a UI for viewing/exporting audit logs. |
| **Foundational Plugin API** | - Solidify and document Plugin APIs for custom components & data connectors.<br>- Develop a "Signature Pad" component to prove the model. |
| **Angular 21 UI Foundation** | - Scaffold Nx workspace and minimal runtime/designer shell; wire HttpInterceptor; host at /ui/designer; run `UI_SMOKE.md` to verify existing UIs remain functional. |

Validation
- Before merging October work, explicitly run the UI Smoke Test guide (`UI_SMOKE.md` at repo root) against http://localhost:8080 (or your configured port) and verify: /ui/builder, /ui/datasource, /ui/swagger, and (when present) /ui/designer. Record results in PR notes.

Notes
- Keep this file aligned with the “Next recommended enhancements” sections in README.md and FUNCTIONAL_SPEC.md.
- After implementing a backlog item, update docs and the change logs accordingly.

## Roadmap checkpoints — November and December 2025
- November (Logistics & HR):
  - PWA/offline (installable, cache, queue-and-replay writes, background sync)
  - WebSocket + MQTT DataSources; Barcode/QR scanner component
  - Reporting CSV/Excel; multi-actor approvals; relationship permissions; multi-tenant scoping
  - Acceptance criteria: `Product_AppBana.md` §5 (Nov) and §17.4 (Logistics)
- December (Healthcare & Leadership):
  - FHIR R4 (read-only) connector; Patient History Timeline component
  - Versioning/rollback; Marketplace (first-party plugins)
  - Logistics addendum: Document store/viewer, Exception rules + alerts, Emissions estimator
  - Acceptance criteria: `Product_AppBana.md` §5 (Dec) and §17.4

## Master system prompt (canonical reference)
To launch the engineering agent, use the master prompt in `UI_Development_Plan.md`.
- Do not embed the prompt here to avoid drift; this file summarizes priorities only.

## Notes
- Keep this file aligned with the “Next recommended enhancements” sections in README.md and FUNCTIONAL_SPEC.md.
- After implementing a backlog item, update docs and the change logs accordingly.
