# Low Level Design — AppBana

Purpose
- Provide a concrete, code-oriented map of the project so an automated agent can navigate, extend, and debug efficiently.

Runtime contract
- Input: HTTP requests to:
  - /schema (JSON body: EntitySchema) — create/migrate (POST) and list/load (GET)
  - /api/{entity}[/{id}] — CRUD over JDBC using metadata-defined tables
  - /ui/* — static HTML UIs (builder, datasource, swagger)
  - /openapi.json — generated OpenAPI 3.0 spec
  - /health, /ready — liveness/readiness
  - /ui/datasource/health — per-datasource DB ping
- Auth (optional): when tokens configured, clients must send either header `X-AppBana-Token: <token>` or `Authorization: Bearer <token>`.
  - readToken grants read-only access; adminToken grants write and read.
  - Built-in UIs send only `X-AppBana-Token` (sanitized) to avoid browser header syntax errors; API clients may use either header.
- HTTPS (optional): when enabled and keystore is configured, an HTTPS listener is started; optionally, HTTP requests are redirected (308) to HTTPS.
- Output: JSON for API responses, HTML for UI pages.
- Errors: JSON {"error":"message"}; appropriate HTTP status (400/404/405/500). Health endpoints return 200 with ok=false payloads for failures except /ready uses 503 when DB is not ready.

Ports and processes
- HTTP: default 8080. Override with -Dappbana.port or APPBANA_PORT.
- HTTPS (optional): default 8443 when httpsEnabled=true; override with httpsPort. When redirectHttpToHttps=true, HTTP responds 308 to the HTTPS URL.
- Single JVM process. Request handling uses virtual threads (Thread.ofVirtual()).

Threading model
- com.sun.net.httpserver.HttpServer with a per-request virtual thread executor: server.setExecutor(r -> Thread.ofVirtual().start(r)).
- When HTTPS is enabled, an HttpsServer is created with SSLContext built from the provided keystore; same executor and contexts are configured on both servers.

Key packages and classes (src/main/java/org/example)
- Main.java — Entrypoint. Initializes SchemaManager.init() and starts ApiServer on configured port.
- ApiServer.java — Binds routes and serves static UIs. Handlers:
  - /schema — SchemaHandler: POST save/preview; GET list; GET by name (auth enforced when enabled)
  - /api — EntityHandler: POST create; GET list; GET/PUT/DELETE by id (auth enforced when enabled)
  - /api/endpoints — returns generated CRUD endpoints (also handled under /api special-case)
  - /openapi.json — builds OpenAPI from stored schemas (auth enforced when enabled)
  - /ui/builder — serves builder.html
  - /ui/datasource — serves datasource.html
  - /ui/swagger — serves swagger.html (embedded Swagger UI loading /openapi.json)
  - /ui/datasource/config|list|save|test|activate|delete — JSON endpoints for multi-DS management (auth enforced when enabled)
  - /ui/datasource/health — GET; ping a datasource by name (or active if omitted)
  - HTTPS: when httpsEnabled=true, starts HttpsServer with keystore-based TLS; optional HTTP→HTTPS redirect.
- SchemaManager.java — Schema persistence/migrations (init/save/generateMigrationPlan/list/load)
- JdbcManager.java — Connection acquisition via HikariCP. Uses DriverUtil to infer driver/type; rebuilds pool lazily on config change
- ConfigManager.java — Load/save config JSON (default path), normalize into multi-DS shape; infer missing datasource types via DriverUtil; apply env overrides; apply token and HTTPS env overrides
- DriverUtil.java — Centralized mapping for DB type and JDBC driver inference.
- AppConfig.java — Root config model + datasources[] + activeDatasource + adminToken + readToken + HTTPS fields (httpsEnabled, httpsPort, keystorePath, keystorePassword, keyPassword, redirectHttpToHttps).
- DatasourceConfig.java — DS model: {name,type,jdbcUrl,username,password,driver,maxPoolSize?,minIdle?,connectionTimeoutMs?,idleTimeoutMs?,maxLifetimeMs?,autoCommit?,poolName?, lastTest*?}.
- OpenApiGenerator.java — Converts stored EntitySchema list into a minimal OpenAPI 3.0 document.
- model/EntitySchema.java — Schema model with Field sub-class (name,type,length,required,primaryKey,autoIncrement,min,max,pattern).

Static resources (src/main/resources/ui)
- builder.html — Minimal schema builder UI (posts JSON to /schema; includes an Auth token box; sends only X-AppBana-Token header).
- datasource.html — Multi-datasource management UI with a JDBC URL Builder and a Test Connection button, plus per-row Test and Ping actions. Shows Status chip and Last tested. Fields: name, type, jdbcUrl, username, password, driver, and Pool section (maxPoolSize, minIdle, connectionTimeoutMs, idleTimeoutMs, maxLifetimeMs, autoCommit, poolName). Actions: Save/Activate/Delete/List/Load/Test/Ping. Includes Auth token box and sends only X-AppBana-Token header.
- swagger.html — Embedded Swagger UI for /openapi.json with an Auth token box; uses requestInterceptor to attach only X-AppBana-Token on all requests (including initial spec fetch).

Config resolution
- Path: APPBANA_CONFIG or -Dappbana.config; default data/appbana-config.json.
- Env overrides on root fields: APPBANA_JDBC_URL, APPBANA_DB_USER, APPBANA_DB_PASS, APPBANA_DB_DRIVER. Token overrides: APPBANA_ADMIN_TOKEN, APPBANA_READ_TOKEN. HTTPS overrides: APPBANA_HTTPS_ENABLED, APPBANA_HTTPS_PORT, APPBANA_KEYSTORE_PATH, APPBANA_KEYSTORE_PASSWORD, APPBANA_KEY_PASSWORD, APPBANA_REDIRECT_HTTP_TO_HTTPS.

Security considerations
- Token-based authentication (optional) is implemented. If adminToken/readToken in AppConfig (or env/system props) are blank, auth is disabled (development mode). If configured:
  - Read operations require readToken or adminToken; write operations require adminToken.
  - Clients may send `X-AppBana-Token` or `Authorization: Bearer`.
  - UIs (builder, datasource, swagger) include a token input that stores the token in localStorage and injects headers into requests.
- HTTPS (optional): use keystore-based TLS; for production, prefer real certificates and enable HTTP→HTTPS redirect.

Developer workflows
- Build: ./mvnw -DskipTests package → target/app-bana-1.0-SNAPSHOT-fat.jar
- Run (HTTP only): java -jar target/app-bana-1.0-SNAPSHOT-fat.jar
- Run (HTTPS): set APPBANA_HTTPS_ENABLED=true and keystore env vars, then run the JAR; visit https://localhost:<httpsPort>/ui/builder

Angular UI development (workspace)
- UI workspace lives under `ui/` (Angular CLI workspace).
- One-command build (libraries + Studio app) and run (SSR):
```zsh
cd /Users/dilip/git/app-bana
./build.sh --clean
./run.sh --port 4000 --open
```
- NPM aliases from repo root (equivalent):
```zsh
npm run ui:build
npm run ui:run -- --port 4000
```
- Direct SSR run from UI workspace (after a build):
```zsh
cd /Users/dilip/git/app-bana/ui
npm run serve:ssr:studio
```
- Notes:
  - Studio SSR default port is 4000 (override with `--port`).
  - `projects/studio/src/index.html` includes Google Material Icons so `<mat-icon>` ligatures render correctly.

Test checklist (manual)
- With tokens set, ensure 401 is returned when missing/invalid token; success when valid.
- With HTTPS enabled, confirm HTTPS listener works and optional HTTP→HTTPS redirect returns 308 with Location.
- GET /ui/datasource/list returns array with lastTest* fields
- POST /ui/datasource/save with new name creates + activates; list shows it
- POST /ui/datasource/test with name persists lastTest*; list shows Status and Last tested updated
- GET /ui/datasource/health?name=<ds> shows live/down with elapsedMs; Ping button updates the row
- POST /schema?preview=true returns DDL plan; POST /schema applies
- CRUD endpoints operate as expected; GET /openapi.json reflects entities; /ui/swagger renders the spec

---

## Q4 2025 Addenda — New Modules and Contracts (planned)

These sections describe low-level structures for roadmap features. They are additive and do not change current MVP behavior until implemented.

### Workflow Engine (October)
- Packages
  - `org.example.workflow`
    - `WorkflowService` — orchestrates start/advance; enforces idempotency and permissions
    - `WorkflowDefinitionRepository` — CRUD for definitions (JSON payload persisted in appbana_workflow_def)
    - `WorkflowInstanceRepository` — CRUD for instances (state, assignees, timestamps; appbana_workflow_instance)
    - `TransitionApplier` — validates guard, applies effects, writes audit
- Storage
  - Tables: `appbana_workflow_def`, `appbana_workflow_instance` (see Functional Spec §17)
- Enforcement points
  - ApiServer binds `/workflows/*` routes to WorkflowService (see Functional Spec §16.1)
  - Tenant scoping via `TenantContext` (see below)
- Idempotency
  - Transition requests include clientId or transition UUID; repository ensures single-apply per (instance, transitionId)

### Audit Logging (October)
- Packages
  - `org.example.audit`
    - `AuditService` — append-only logging API (async queue optional later)
    - `AuditLogRepository` — persists to `appbana_audit_log`; CSV export streaming
    - `AuditFilters` — utilities for querying by user/entity/date/action
- Integration points
  - Wrap Entity CRUD handlers; log before/after hashes (or row snapshot hashes)
  - Log workflow transitions, document downloads, report exports, connector access (FHIR)
- Data minimization
  - Store hashes for large payloads; PHI flag on entries for healthcare auditing

### Field-Level Security (October)
- Packages
  - `org.example.security`
    - `FlsService` — resolves FLS rules for (entity, role/user)
    - `FlsRulesRepository` — persists rules (JSON in config or table later `appbana_fls_rules` if needed)
    - `Redactor` — masks/omits fields on read; `WriteEnforcer` — blocks writes to restricted fields
- Enforcement points
  - Entity read: after DB read and before response → Redactor
  - Entity write: validate request against rules → WriteEnforcer
  - UI guidance: designer/runtime hide/disable (covered in UI plan)

### Multi-tenant Scoping (Q4)
- Packages
  - `org.example.tenant`
    - `TenantContext` — resolves tenant from token or `X-AppBana-Tenant` header; stored in ThreadLocal per request
    - `TenantScope` — helpers to inject `tenantId` filter into queries and to stamp new rows
- Enforcement points
  - All repositories (Entity CRUD, workflows, docs, alerts) include tenant filters when enabled
  - Admin overrides require explicit flag; audits record tenant

### Real-time & MQTT (November)
- WebSocket/SSE
  - `org.example.realtime` (planned)
    - MVP option A: Server-Sent Events endpoint `/rt/sse` for broadcast topics (simpler with HttpServer)
    - Option B: WebSocket endpoint `/rt/ws` using a lightweight WS server (dependency TBD) — target per Product plan; SSE acceptable fallback
  - Auth: tokens validated at connect; topic authorization TBD
- MQTT (UI-side)
  - UI uses `mqtt.js` over wss:// to external broker; server provides helper config endpoint if needed; messages transformed client-side

### Reporting Engine (November)
- Packages
  - `org.example.reporting`
    - `ReportDefinitionRepository` — stores report JSON definitions
    - `ReportExportService` — streaming CSV (MVP); Excel optional later (Apache POI if added)
- Endpoints
  - `/reports/definitions` (CRUD); `/reports/{id}/export.csv` (see Functional Spec §16.5)
- Auditing
  - Log report exports with filter params and counts

### EDI Intake (November)
- Packages
  - `org.example.edi`
    - `EdiIngestService` — orchestrates parsing and mapping to normalized events
    - `EdifactParser` — pluggable parsers for COARRI/CODECO; tolerant to variations
    - `EdiRepository` — persists normalized event rows or forwards to event pipeline
- Endpoints & sources
  - `/edi/upload` (MVP) — REST file upload; optional FS/S3 watcher adapter (deferred)
- Idempotency
  - Control number + file checksum to suppress duplicates

### Document Store (December)
- Packages
  - `org.example.docs`
    - `DocumentService` — metadata CRUD; content streaming; checksum verification
    - `DocumentStorage` (interface) — `FsDocumentStorage` implementation (MVP)
- Endpoints
  - `/documents` (POST metadata+file), `/documents/{id}`, `/documents/{id}/content`
- Auditing
  - All content reads/writes audited; PHI flag if configured

### Alerts & Rules (December)
- Packages
  - `org.example.alerts`
    - `RuleEngine` — evaluates conditions against events; triggers actions
    - `RulesRepository` — stores rules JSON
    - `ActionDispatch` — connectors for email/SMS (pluggable)
- Endpoints
  - `/alerts/rules` CRUD; `/alerts/test`

### FHIR Connector (December)
- Packages
  - `org.example.connectors.fhir`
    - `FhirProxyController` — whitelisted GET proxy to external FHIR base; injects auth; audits access
    - `FhirConfig` — base URL, auth token/flow per environment
- Notes
  - Read-only MVP; prefer proxy mode for consistent auditing

### Design Versioning & Marketplace (December)
- Packages
  - `org.example.design`
    - `DesignVersionService` — save/list/diff/rollback of app designs
  - `org.example.marketplace`
    - `PluginCatalogService` — lists first-party plugins with signed manifests

### Data Schema Additions (planned)
- See Functional Spec §17 — mirror tables and columns; initialize via SchemaManager migrations when features enabled

### Cross-cutting Concerns
- Security
  - Token handling unchanged; add `X-AppBana-Tenant` header support; CSP recommendations (enforced at reverse proxy or future filter)
- Observability
  - Add OpenTelemetry hooks in ApiServer handlers and services (latency, errors, attributes: tenantId, entity, action)
- Error handling
  - Keep JSON error shape; for streaming endpoints, map to 4xx/5xx with small JSON body
- Styling policy
  - Follow `docs/STYLE_GUIDE.md`: Angular Material + CSS variables as theme tokens; tiny local utilities in the studio app; runtime is token‑driven and avoids arbitrary utility classes from design JSON.
- Plugin boundary via Web Components
  - Runtime can host plugins authored as Angular Elements or native/Lit custom elements. Use a small registry (type → tagName), inputs via properties/attributes, outputs via CustomEvent, theming via CSS variables, and data access via a small SDK bridge for auth/base URL. See `UI_Development_Plan.md` (Plugin boundary via Web Components).

### Sequences (abridged)
- Barcode scan → MQTT → UI table update
  1) Mobile PWA scanner component publishes to `wss://broker/topic`
  2) UI MQTT DataSource receives message → transform → setState/table row update
  3) Optional `/api/{entity}` write queued offline if offline; replay on reconnect
- EDI file → normalized events
  1) POST `/edi/upload` file → EdiIngestService → EdifactParser (COARRI/CODECO)
  2) Normalized events persisted; idempotency via control number + checksum
  3) Alerts Rules evaluate exceptions; optional emails sent
- Document view
  1) UI requests `/documents/{id}/content` with token+tenant
  2) DocumentService streams content; AuditService logs PHI access

### Acceptance Criteria Trace
- Map each module’s MVP acceptance criteria to Product_AppBana.md §5 and §17.4.

### Open Questions / Deferred
- WebSocket server choice for `/rt/ws` with current HttpServer (SSE as fallback acceptable for MVP)
- Storage backend abstraction for documents beyond filesystem (S3, etc.)
- Excel export feasibility in November vs CSV-only
