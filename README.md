# AppBana — Metadata-driven UI → API → Database

<!-- Updated 2025-10-01: Added Phase A progress + corrections -->
**Latest feature highlights**
- Schema editing (rename non‑PK fields with automatic migration)
- Migration preview (dry-run DDL plan) & migration history per schema
- Schema deletion (optionally drop underlying table)
- Field reordering (UI ordering only)
- Search & datasource filtering for schemas
- Duplicate field name validation (client-side)
- Inline JSON import/export of schema definitions
- New helper script `./run-ui.sh` for UI dev/build/preview
- **Baseline CRUD Audit Logging** (INSERT / UPDATE / DELETE + batch insert; before/after & field diff — see `docs/AUDIT_LOGGING.md`, query via `GET /audit`)
- **Studio Builder productivity (planned)**: shortcuts documented in `docs/UI_BUILDER_SHORTCUTS.md` (foundation features being ported into new Studio)

Metadata-driven MVP: design forms in a minimal UI builder, persist the schema, auto-create/migrate a backing table, and expose runtime CRUD APIs. Implemented with plain Java SE (no heavy frameworks).

Quick summary
- Frontend: A custom, lightweight UI framework ("Studio") is under active development (Phase A). Core pieces landed: `BaseElement`, component registry, demo metadata (`demo-page.json`), placeholder Unknown component. Pending: recursive runtime renderer wiring + first renderer test + /ui/studio packaging.
- Backend: Java (HttpServer) that persists schemas, auto-creates/migrates tables via JDBC, and exposes generic CRUD endpoints at runtime.
- DB: H2 embedded (file) by default; JDBC usage allows swapping to Postgres/MySQL/etc.
- Datasources: built-in UI to add/manage multiple datasources (by name and type) and select the active one at runtime.
- Pooling: HikariCP connection pool with configurable settings per datasource.
- OpenAPI: live spec at /openapi.json and an embedded Swagger UI at /ui/swagger.
- New endpoints: `/schema/summaries`, `/schema/{name}/migrations`, `DELETE /schema/{name}`.

Status of repository
- Fully working MVP backend and enhanced schema builder (legacy HTML) remain operational.
- Studio Phase A partially complete (see below). Legacy builder continues to function until Studio Builder MVP (Phase B) is ready.
- Swagger/OpenAPI spec available at `/openapi.json` and browsable at `/ui/swagger`.
- Datasource management UI available at `/ui/datasource`.
- HikariCP pool initialized based on the current active datasource; reconfigured when settings change.
- Built fat JAR available under `app-bana-service/target/` after building.
- `.github/COPILOT_GUIDE.md` contains an assistant-facing snapshot of current progress & next steps (kept in sync with this README + `docs/UI_Development_Plan.md`).
- For a step-by-step walkthrough, see `docs/USER_GUIDE.md`.

## Studio (Custom UI Framework) Status
Current Phase: A (Foundation — in progress)

Phase A Exit Criteria (updated):
- ✅ Metadata interfaces file (`models/metadata.ts`)
- ✅ Component registry bootstrap (`core/registry.ts`) with dynamic import of core components
- ✅ Demo metadata JSON (`src/demo/demo-page.json`) including an unknown component case
- ✅ Unknown component placeholder (`UnknownElement`)
- ✅ Base components: Container / Text / Button
- ❌ Runtime recursive renderer (currently `src/app-renderer.ts` is a stub; next task)
- ❌ Vitest renderer test (Vitest harness exists; builder tests present; need renderer spec)
- ❌ Packaged `/ui/studio` entry (pending renderer + simple index wiring)

See full plan: `docs/UI_Development_Plan.md` (authoritative), quick snapshot: `.github/COPILOT_GUIDE.md`.

### Immediate Next (Phase A remaining tasks)
1. Implement recursive renderer in `app-renderer.ts` (walk nodes, instantiate elements, attach props, resolve children by id).
2. Add a minimal bootstrap script + HTML (served at `/ui/studio`) loading demo page JSON and invoking renderer.
3. Write first Vitest test asserting rendered DOM structure (`demo-page.json` → expected node count & text content).
4. Update build to copy needed studio assets into service JAR (parallel to existing UI assets).
5. Add contribution doc snippet once renderer API stabilized (component prop schema pattern).

### Contribution (Components)
1. Create component in `app-bana-ui/src/components/` extending `BaseElement`.
2. Define behavior in `render()` and optional `styles()`.
3. Register in `core/registry.ts` (dynamic import guard automatically loads if missing).
4. Add example usage to demo metadata (during Phase A) or page JSON (later phases).
5. Add/extend test (Vitest) validating render or behavior.

Tech stack
- Java 25 (virtual threads for HTTP request handling)
- Frontend: TypeScript, Vite, minimal custom Web Components framework (lit retained temporarily for legacy pieces; new code avoids it)
- H2 (embedded) for development
- Jackson (jackson-databind) for JSON
- SLF4J simple for logging
- HikariCP for JDBC connection pooling
- Maven build with Shade plugin (uber jar)

## Build & run

### Backend
1. Build the entire project (including frontend assets):
   ```bash
   ./app-bana-service/mvnw clean package -DskipTests
   ```
2. Run the backend server:
   ```bash
   java -jar app-bana-service/target/app-bana-1.0-SNAPSHOT-fat.jar
   ```

### Frontend (for Development)
For a live development server with hot-reloading:
1. Navigate to the UI module:
   ```bash
   cd app-bana-ui
   ```
2. Install dependencies:
   ```bash
   npm install
   ```
3. Run the dev server (proxies to the backend):
   ```bash
   npm run dev
   ```

### Fast helper (recommended)
Use the provided script (auto-detects nvm / Node, installs deps, runs the right command):
```bash
./run-ui.sh            # start dev server (vite)
./run-ui.sh build      # production build (dist/)
./run-ui.sh preview    # serve built assets
UI_PORT=5180 ./run-ui.sh dev   # custom port
```
Set `USE_SYSTEM_NODE=1` to skip nvm detection.

Port configuration
- Default HTTP port: 8080
- Override via system property: -Dappbana.port=9090
- Or via environment variable: APPBANA_PORT=9090

HTTPS (optional)
- The server can also listen on HTTPS when enabled via config or env/system props.
- Config fields in appbana-config.json:
  - httpsEnabled: true|false (default: false)
  - httpsPort: number (default: 8443)
  - keystorePath: path to JKS or PKCS12 keystore (e.g., certs/keystore.p12)
  - keystorePassword: password for the keystore
  - keyPassword: password for the key (defaults to keystorePassword if omitted)
  - redirectHttpToHttps: true|false — if true, the HTTP server responds with 308 redirect to the HTTPS URL
- Environment variables / system properties:
  - APPBANA_HTTPS_ENABLED=true | -Dappbana.https.enabled=true
  - APPBANA_HTTPS_PORT=8443 | -Dappbana.https.port=8443
  - APPBANA_KEYSTORE_PATH=certs/keystore.p12 | -Dappbana.keystore.path=...
  - APPBANA_KEYSTORE_PASSWORD=changeit | -Dappbana.keystore.password=...
  - APPBANA_KEY_PASSWORD=changeit | -Dappbana.key.password=...
  - APPBANA_REDIRECT_HTTP_TO_HTTPS=true | -Dappbana.redirect.http.to.https=true
- Quickstart (self-signed, PKCS12)
  1) Generate a keystore:
     keytool -genkeypair -alias appbana -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore certs/keystore.p12 -storepass changeit -keypass changeit -dname "CN=localhost, OU=Dev, O=AppBana, L=Local, S=Local, C=US"
  2) Run with env vars:
     APPBANA_HTTPS_ENABLED=true \
     APPBANA_KEYSTORE_PATH=certs/keystore.p12 \
     APPBANA_KEYSTORE_PASSWORD=changeit \
     APPBANA_KEY_PASSWORD=changeit \
     APPBANA_HTTPS_PORT=8443 \
     APPBANA_REDIRECT_HTTP_TO_HTTPS=true \
     java -jar target/app-bana-1.0-SNAPSHOT-fat.jar
  3) Open https://localhost:8443/ui/builder (accept your self-signed cert in the browser).

Authentication (optional)
- App supports simple token-based auth. When no tokens are configured, all endpoints are open (dev mode).
- Configure tokens in config file or via env/system properties:
  - Config fields: `adminToken` (read-write) and `readToken` (read-only)
  - Env vars: `APPBANA_ADMIN_TOKEN`, `APPBANA_READ_TOKEN`
  - System props: `-Dappbana.admin.token=...`, `-Dappbana.read.token=...`
- Client headers (either works on the server):
  - `X-AppBana-Token: <token>`
  - `Authorization: Bearer <token>`
- Built-in UIs (builder.html, datasource.html, swagger.html):
  - Use only `X-AppBana-Token` (token value sanitized to avoid browser header restrictions) and store it in localStorage.
  - For API clients and curl examples, `Authorization: Bearer` continues to work.
- Authorization rules when tokens are set:
  - Read-only (readToken or adminToken): GET /schema, GET /schema/{name}, GET /api/*, GET /openapi.json, GET /ui/datasource/list|config|health
  - Admin (adminToken only): POST /schema (apply/preview), POST /api/* (writes), PUT/DELETE /api/*, POST /ui/datasource/save|test|activate|delete
- UIs: builder.html and datasource.html include an “Auth token” box; swagger.html also has one. Saving the token stores it in localStorage and all UI requests send the header automatically.

Default runtime behavior
- Metadata tables: `appbana_schemas`, `appbana_migrations` (DDL recorded).  
- Migration history endpoint: `GET /schema/{name}/migrations` returns ordered executed SQL.
- Summaries endpoint: `GET /schema/summaries` for name + datasource (supports UI filtering).  
- Delete: `DELETE /schema/{name}?dropTable=true|false` (admin token required when auth enabled).
- On startup the app attempts to ensure two metadata tables (in the active datasource):
  - `appbana_schemas(name PK, json CLOB)` — stores schema JSON
  - `appbana_migrations(id IDENTITY, schema_name, sql CLOB, executed_at TIMESTAMP)` — records DDL executed
- Embedded HTTP server listens on the configured port and uses Java virtual threads for request handling.
- UI builder: http://localhost:8080/ui/builder
- Datasource UI: http://localhost:8080/ui/datasource
- Swagger UI: http://localhost:8080/ui/swagger
- OpenAPI: http://localhost:8080/openapi.json (requires token when auth is enabled)
- Health: http://localhost:8080/health (liveness), http://localhost:8080/ready (readiness), and http://localhost:8080/ui/datasource/health (per-datasource DB ping)

Health & readiness
- GET `/health` → `{ "status": "UP" }` (process liveness)
- GET `/ready` → `{ ok: boolean, activeDatasource?: string, dbProduct?: string, dbVersion?: string, elapsedMs: number, error?: string }`
  - Attempts a DB connection using the active datasource. Returns HTTP 200 when ok=true, or 503 with error details when ok=false.
- GET `/ui/datasource/health?name=<ds>&timeoutSec=<n>` → `{ ok: boolean, name: string, url: string (masked), dbProduct?: string, dbVersion?: string, elapsedMs: number, error?: string, sqlState?: string, errorCode?: number }`
  - Pings the specified datasource (or the active one if name omitted). `timeoutSec` optional (default 3; max 60). URL is masked to avoid leaking passwords.

Datasource management
- UI: `/ui/datasource` supports Add/Update, List, Activate, Delete, and Test Connection (both from the form and via a per-datasource “Test” action in the list). Each row shows a Status badge (Live/Down/Unknown) and a “Last tested” column.
- Each datasource has: name, type (h2/postgres/mysql/mariadb/mssql/oracle/sqlite/custom), jdbcUrl, username, password, driver.
- JDBC URL Builder: optional helper in the form that builds the JDBC URL from fields (type, host, port, database/service, and extra params). Supports H2 (file/mem), Postgres, MySQL, MariaDB, SQL Server, Oracle, and SQLite. Enable Auto-build to keep the URL in sync as you edit fields.
- Server-side URL build (API): if you POST to `/ui/datasource/save` without `url`, the server will construct it from components. Accepted fields:
  - Common: `type`, `host`, `port`, `dbname`, `params`
  - H2-specific: `h2Mode` (file|mem), `h2File`, `h2MemName`
  - SQLite-specific: `sqliteFile`
  Example (Postgres):
  ```json
  {
    "name":"pg","type":"postgres","host":"localhost","port":"5432","dbname":"appbana",
    "username":"sa","password":"Password_123#","driver":"org.postgresql.Driver"
  }
  ```
- Test connection: click “Test Connection” in the form or the per-row “Test” action to attempt a short-lived connection (uses the URL or builds one from components). The API is also available at `POST /ui/datasource/test`.
  - Request body: either {url, username?, password?, driver?, type?} or components {type, host, port, dbname, params?, username?, password?, driver?}; `name` can also be provided to test an existing saved datasource.
  - Optional `timeoutSec` limits the attempt (default 5, max 60).
  - Response: `{ ok: boolean, message?: string, error?: string, url: string (masked), dbProduct?: string, dbVersion?: string, elapsedMs: number, sqlState?: string, errorCode?: number }`
  - When testing a saved datasource by `name`, the last test result is persisted to config and shown in the list.
- Optional pool settings per datasource: maxPoolSize, minIdle, connectionTimeoutMs, idleTimeoutMs, maxLifetimeMs, autoCommit, poolName.
- Driver inference: if driver is blank, the system infers it from `type` or the JDBC URL.
- Active datasource: the server uses the currently active datasource for all DB operations.
- Pool reconfiguration: any change to the active datasource (including pool fields) rebuilds the Hikari pool lazily on the next getConnection().

Datasource API (JSON)
- GET `/ui/datasource/list` → array of datasources (without passwords), each has {name,type,jdbcUrl,username,driver,active,maxPoolSize,minIdle,connectionTimeoutMs,idleTimeoutMs,maxLifetimeMs,autoCommit,poolName,
  lastTestOk?, lastTestAtEpochMs?, lastTestMessage?, lastTestDbProduct?, lastTestDbVersion?, lastTestElapsedMs?}.
- GET `/ui/datasource/config` → current active datasource details (without password), includes the pool fields.
- POST `/ui/datasource/save` body: {name, type?, url, username?, password?, driver?, maxPoolSize?, minIdle?, connectionTimeoutMs?, idleTimeoutMs?, maxLifetimeMs?, autoCommit?, poolName?}
  - Upserts the datasource by name; if password is empty/missing it isn’t overwritten; activates the saved datasource.
- POST `/ui/datasource/test` body: {url?, type?, host?, port?, dbname?, params?, username?, password?, driver?, name?, timeoutSec?}
  - Attempts a one-off connection; returns `{ok, ...}` with DB product/version on success; masks sensitive parts of the URL.
  - If `name` is provided and matches a saved datasource, the last test result is persisted to config.
- GET `/ui/datasource/health?name=<ds>&timeoutSec=<n>` — ping a datasource (or active if name omitted) and return status.
- POST `/ui/datasource/activate` body: {name}
- POST `/ui/datasource/delete` body: {name}

Pooling defaults (if a field is omitted)
- maxPoolSize: 10
- minIdle: 2
- connectionTimeoutMs: 30000
- idleTimeoutMs: 600000 (10 minutes)
- maxLifetimeMs: 1800000 (30 minutes)
- autoCommit: true
- poolName: `appbana-<datasourceName>`

Configuration
- Config file path: `APPBANA_CONFIG` env var or `-Dappbana.config=...` system property (default: `data/appbana-config.json`).
- Environment overrides (optional):
  - APPBANA_JDBC_URL — override JDBC URL
  - APPBANA_DB_USER — override username
  - APPBANA_DB_PASS — override password
  - APPBANA_DB_DRIVER — override driver class
  - APPBANA_ADMIN_TOKEN — set admin token
  - APPBANA_READ_TOKEN — set read-only token
  - APPBANA_HTTPS_ENABLED, APPBANA_HTTPS_PORT, APPBANA_KEYSTORE_PATH, APPBANA_KEYSTORE_PASSWORD, APPBANA_KEY_PASSWORD, APPBANA_REDIRECT_HTTP_TO_HTTPS
- Config file format (example):
```
{
  "datasources": [
    {
      "name": "primary",
      "type": "h2",
      "jdbcUrl": "jdbc:h2:./data/appbana;AUTO_SERVER=TRUE",
      "username": "sa",
      "password": "secret",
      "driver": "org.h2.Driver",
      "maxPoolSize": 10,
      "minIdle": 2,
      "connectionTimeoutMs": 30000,
      "idleTimeoutMs": 600000,
      "maxLifetimeMs": 1800000,
      "autoCommit": true,
      "poolName": "appbana-primary"
    }
  ],
  "activeDatasource": "primary",
  "adminToken": "change-me-admin",
  "readToken": "change-me-read",
  "httpsEnabled": true,
  "httpsPort": 8443,
  "keystorePath": "certs/keystore.p12",
  "keystorePassword": "changeit",
  "keyPassword": "changeit",
  "redirectHttpToHttps": true
}
```
- Backward compatibility: if only root fields are present (jdbcUrl/username/password/driver/name), the app seeds a default datasource and marks it active.

API endpoints (runtime generic CRUD & schema management)
- POST /schema — save schema (or preview with `?preview=true` returns DDL plan)
- GET /schema — list schema names (supports `?page=&size=&q=`)
- GET /schema/summaries — list objects `{name,datasource}`
- GET /schema/{name} — return schema JSON
- GET /schema/{name}/migrations — migration history (ordered executed SQL)
- DELETE /schema/{name}?dropTable=true|false — delete schema metadata (and optionally drop table)
- POST /api/{entity} — insert
- GET /api/{entity} — list
- GET /api/{entity}/{id} — get by id
- PUT /api/{entity}/{id} — update by id
- DELETE /api/{entity}/{id} — delete by id
- GET /openapi.json — OpenAPI 3.0 spec
- GET /api/endpoints — machine-readable list of CRUD endpoints
- Health: /health, /ready

## Advanced Entity Query Parameters
The `GET /api/{entity}` endpoint supports advanced server-side querying. Without any parameters it returns a legacy plain JSON array. Supplying *any* advanced parameter switches to an object response (unless only `count=true`, which returns just a count object).

Query parameters:
- limit: integer (default 50, max 500) – page size.
- offset: integer (default 0) – zero-based row offset.
- q: string – case-insensitive substring match across textual (string/text/varchar) fields. Ignored gracefully if entity has no textual fields.
- fields: comma list of field names for projection (e.g. `fields=id,firstName`). Order preserved; duplicates ignored after first. If blank (e.g. `fields=`) projection defaults to all and the response omits `fields` key.
- sort: comma list (e.g. `sort=-createdAt,firstName`). Prefix `-` for DESC, optional `+` for ASC. Duplicates ignored after first, preserving order.
- filter: comma-separated equality filters `field:value` (e.g. `filter=status:ACTIVE,age:30`). Types auto-coerced for int/long/boolean/date/timestamp. Date/timestamp requires ISO-8601; otherwise treated as a literal string.
- count: `true` / `1` – when set returns `{ total, query?, filters? }` instead of rows.

Response shapes:
1) Legacy (no advanced params):
```
[ { ...row }, { ...row } ]
```
2) Advanced:
```
{
  "rows": [ { ...projectedRow } ],
  "total": 123,
  "limit": 50,
  "offset": 0,
  "query": "abc",              // only if q supplied
  "fields": ["id","firstName"], // only if projection applied
  "sort": ["\"FIRSTNAME\" ASC"], // ORDER BY fragments (quoted, upper-cased identifiers)
  "filters": { "status": "ACTIVE" } // only if filters applied
}
```
3) Count-only:
```
{
  "total": 123,
  "query": "abc",      // optional
  "filters": { ... }    // optional
}
```

Batch insert:
- `POST /api/{entity}/batch` – body is JSON array of row objects (max 1000). Auto-increment PK fields may be omitted.
- Response: `{ "inserted": N, "ids": [ ... ] }` (ids only if driver returns generated keys).

Examples:
```
GET /api/customer?sort=-createdAt,firstName&fields=id,firstName,lastName&limit=25&offset=50
GET /api/order?filter=status:OPEN,priority:HIGH&q=urgent&limit=20
GET /api/logs?count=true&filter=level:ERROR
POST /api/customer/batch  (array body)
```

Notes:
- Search (`q`) ignored silently if no textual columns exist (still echoed as `query` in advanced response).
- Projection and sort keep first occurrence only; later duplicates dropped.
- Timestamp filter values must be ISO-8601 to be parsed; bad values are left as raw strings (DB may reject or coerce).
- ORDER BY fragments (`sort` array) use quoted, upper-cased identifiers to match internal canonical form.

Renaming a field
- Edit in UI or send updated schema with the field's new `name` and `existingName` set to the old column. Example field object:
```json
{"name":"email_address","existingName":"email","type":"string","length":255}
```
The backend emits an `ALTER TABLE ... RENAME COLUMN` migration if applicable.

Deleting a schema
```bash
curl -X DELETE "http://localhost:8080/schema/contact?dropTable=true"
```

Migration preview
```bash
curl -s -X POST 'http://localhost:8080/schema?preview=true' \
  -H 'Content-Type: application/json' \
  --data '{"name":"demo","fields":[{"name":"id","type":"long","primaryKey":true,"autoIncrement":true}]}'
```
Returns array of SQL statements (no changes applied).

Where to change common settings
- Code packages now under `com.appbana` (earlier docs referencing `org.example` updated).  
- See: `com/appbana/ApiServer.java`, `SchemaManager.java`, `JdbcManager.java`.

## Key files (updated package paths)
- `app-bana-service/src/main/java/com/appbana/ApiServer.java` — HTTP handlers
- `app-bana-service/src/main/java/com/appbana/SchemaManager.java` — schema & migrations
- `app-bana-service/src/main/java/com/appbana/JdbcManager.java` — JDBC + pooling (active datasource)
- `app-bana-service/src/main/java/com/appbana/ConfigManager.java` — loads/saves config; normalizes multi-datasource format
- `app-bana-service/src/main/java/com/appbana/AppConfig.java`, `DatasourceConfig.java` — config models
- `app-bana-service/src/main/java/com/appbana/model/EntitySchema.java` — schema model (relational; non-rel kinds future)
- Legacy UIs: `app-bana-service/src/main/resources/ui/*.html` (builder, datasource, swagger)
- Studio core (new): `app-bana-ui/src/core/`, `app-bana-ui/src/models/metadata.ts`, `app-bana-ui/src/demo/demo-page.json`

Notes
- Audit logging baseline (including batch insert one-row-per-generated-key) complete; roadmap extensions in `docs/AUDIT_LOGGING.md`.
- Remaining Phase A tasks intentionally narrow; do not begin Builder MVP features (drag/drop & inspector) until renderer + test are green.
- Backlog: see `docs/TODO.md` for prioritized next steps and enhancements.

---

(Angular-specific build/run instructions were previously removed; custom Studio implementation supersedes earlier Angular/Nx plan.)

## Cross-Reference
- Deep Studio Plan: `docs/UI_Development_Plan.md`
- Assistant Snapshot: `.github/COPILOT_GUIDE.md`
- Implementation Backlog: `docs/TODO.md`

---
