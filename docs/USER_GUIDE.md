# AppBana — User Guide

Welcome! This guide walks you through installing, configuring, and using AppBana end to end.

What is AppBana?
- A metadata-driven MVP. Design entities in a browser, AppBana persists the schema, auto-creates/migrates a table, and serves generic CRUD APIs.
- No heavy framework; it’s plain Java + HttpServer + JDBC + HikariCP.

Contents
- Prerequisites
- Quick start
- Configuration (port, config file, environment overrides)
- Authentication (optional)
- HTTPS (optional)
- Datasource management (UI + API)
- Designing entities (Schema Builder)
- Using the runtime CRUD APIs
- OpenAPI & Swagger UI
- Health & readiness
- Troubleshooting & tips
- Security notes
- UI step-by-step walkthroughs (all features)
- Recipes (common tasks)

Prerequisites
- Java 25 (LTS)
- macOS/Linux or Windows
- Internet access (for Swagger UI CDN), optional
- Optional: Docker (to run Postgres locally)

Quick start
1) Build the app
```bash
./app-bana-service/mvnw -DskipTests package
```
2) Run it
```bash
java -jar target/app-bana-1.0-SNAPSHOT-fat.jar
# If 8080 is busy, override the port
java -Dappbana.port=8081 -jar target/app-bana-1.0-SNAPSHOT-fat.jar
```
3) Open these URLs
- Datasource UI: http://localhost:8080/ui/datasource
- Schema Builder: http://localhost:8080/ui/builder
- Swagger UI: http://localhost:8080/ui/swagger
- OpenAPI JSON: http://localhost:8080/openapi.json
- API endpoints index (machine-readable): http://localhost:8080/api/endpoints
- Health: http://localhost:8080/health and readiness: http://localhost:8080/ready

Smoke test (optional)
- For a fast, repeatable end-to-end check of existing UIs and key routes, follow `UI_SMOKE.md`. Run it against http://localhost:8080 (or your configured port).

Note: If authentication is enabled (see below), enter your token in the top bar of the UIs (builder, datasource, swagger) to authorize requests.

## Token quickstart (optional)
- Set tokens when starting the server (zsh)
```bash
export APPBANA_ADMIN_TOKEN=admin123
export APPBANA_READ_TOKEN=read123
java -jar target/app-bana-1.0-SNAPSHOT-fat.jar
```
- In the UIs (builder, datasource, swagger): paste your token in the “Auth token” box and click “Save token”.
- With curl (either header works)
```bash
export TOKEN=admin123
# Using custom header
curl -sS http://localhost:8080/schema -H "X-AppBana-Token: $TOKEN"
# Or using Bearer auth
curl -sS http://localhost:8080/schema -H "Authorization: Bearer $TOKEN"
```

Configuration
- Port
  - System property: `-Dappbana.port=9090`
  - Env var: `APPBANA_PORT=9090`
- Config file
  - Default: `data/appbana-config.json`
  - Override path: env `APPBANA_CONFIG` or `-Dappbana.config=...`
- Env overrides for connection (optional)
  - APPBANA_JDBC_URL, APPBANA_DB_USER, APPBANA_DB_PASS, APPBANA_DB_DRIVER
  - Or equivalent system properties: `-Dappbana.jdbc.url`, `-Dappbana.db.user`, `-Dappbana.db.pass`, `-Dappbana.db.driver`

HTTPS (optional)
- AppBana can serve HTTPS alongside HTTP when enabled.
- Config fields: `httpsEnabled`, `httpsPort` (default 8443), `keystorePath`, `keystorePassword`, `keyPassword?`, `redirectHttpToHttps`.
- Env/system props: `APPBANA_HTTPS_ENABLED`, `APPBANA_HTTPS_PORT`, `APPBANA_KEYSTORE_PATH`, `APPBANA_KEYSTORE_PASSWORD`, `APPBANA_KEY_PASSWORD`, `APPBANA_REDIRECT_HTTP_TO_HTTPS` (or `-Dappbana.*` equivalents).
- Quickstart (self-signed PKCS12):
```bash
# 1) Generate a local PKCS12 keystore (macOS zsh)
mkdir -p certs
keytool -genkeypair -alias appbana -keyalg RSA -keysize 2048 -storetype PKCS12 \
  -keystore certs/keystore.p12 -storepass changeit -keypass changeit \
  -dname "CN=localhost, OU=Dev, O=AppBana, L=Local, S=Local, C=US"

# 2) Start the app with HTTPS enabled (and redirect HTTP→HTTPS)
APPBANA_HTTPS_ENABLED=true \
APPBANA_KEYSTORE_PATH=certs/keystore.p12 \
APPBANA_KEYSTORE_PASSWORD=changeit \
APPBANA_KEY_PASSWORD=changeit \
APPBANA_HTTPS_PORT=8443 \
APPBANA_REDIRECT_HTTP_TO_HTTPS=true \
java -jar target/app-bana-1.0-SNAPSHOT-fat.jar

# 3) Visit
open https://localhost:8443/ui/builder  # accept the self-signed cert
```
- Notes: With `redirectHttpToHttps=true`, any request on HTTP port (default 8080) returns 308 to the HTTPS URL.

Authentication (optional)
- AppBana supports simple token-based auth. When no tokens are configured, all endpoints are open (development mode).
- Configure tokens in the config file or via environment/system properties:
  - Config fields: `adminToken` (read-write) and `readToken` (read-only)
  - Env vars: `APPBANA_ADMIN_TOKEN`, `APPBANA_READ_TOKEN`
  - System props: `-Dappbana.admin.token=...`, `-Dappbana.read.token=...`
- Client headers (either works):
  - `X-AppBana-Token: <token>`
  - `Authorization: Bearer <token>`
- Authorization rules when tokens are set:
  - Read-only (readToken or adminToken): GET /schema, GET /schema/{name}, GET /api/*, GET /openapi.json, GET /ui/datasource/list|config|health
  - Admin (adminToken only): POST /schema (apply/preview), POST /api/* (writes), PUT/DELETE /api/*, POST /ui/datasource/save|test|activate|delete
- UIs: builder.html, datasource.html, and swagger.html include an “Auth token” box. Saving the token stores it in localStorage so all UI requests send it automatically.
- Curl examples with token
```bash
# set your token for convenience
export TOKEN=admin123
# Save a schema (admin)
curl -sS -X POST http://localhost:8080/schema \
  -H 'Content-Type: application/json' \
  -H "X-AppBana-Token: $TOKEN" \
  --data-binary '{"name":"contact","fields":[{"name":"id","type":"long","primaryKey":true,"autoIncrement":true}]}'
# List schemas (read)
curl -sS http://localhost:8080/schema -H "Authorization: Bearer $TOKEN"
```

Datasource management
Use the Datasource UI at /ui/datasource to:
- Add/Update
- Activate (switch active datasource)
- Delete
- Test Connection (form-level, and per-row in the list)

Fields
- Required basics: name, type, JDBC URL (or build it), username, password, driver
- JDBC URL Builder (optional):
  - Builds URLs for H2 (file/mem), Postgres, MySQL, MariaDB, SQL Server, Oracle, SQLite from host/port/db + params
  - Enable Auto-build to keep URL synced as you edit
- Server-side URL build (API):
  - If you POST to save without `url`, the server builds it from components: { type, host, port, dbname, params }, plus H2/SQLite extras.
- Pool settings (optional): maxPoolSize, minIdle, connectionTimeoutMs, idleTimeoutMs, maxLifetimeMs, autoCommit, poolName
  - Defaults when omitted: maxPoolSize=10, minIdle=2, connectionTimeoutMs=30000, idleTimeoutMs=600000, maxLifetimeMs=1800000, autoCommit=true, poolName="appbana-<name>"

Run Postgres in Docker (zsh-safe quoting for #)
```bash
docker run --name appbana-pg -d \
  -e POSTGRES_USER=sa \
  -e POSTGRES_PASSWORD='Password_123#' \
  -e POSTGRES_DB=appbana \
  -p 5432:5432 \
  postgres:16
```
Create a Postgres datasource (API)
```bash
curl -sS -X POST http://localhost:8080/ui/datasource/save \
  -H 'Content-Type: application/json' \
  --data-binary '{
    "name": "pg",
    "type": "postgres",
    "host": "localhost",
    "port": "5432",
    "dbname": "appbana",
    "username": "sa",
    "password": "Password_123#",
    "driver": "org.postgresql.Driver"
  }'
```
Test the connection (UI)
- Click “Test Connection” on the form (uses current inputs)
- Click “Test” in the list row (tests a saved datasource by name)

Test the connection (API)
```bash
curl -sS -X POST http://localhost:8080/ui/datasource/test \
  -H 'Content-Type: application/json' \
  --data-binary '{"name":"pg"}'
# Or provide components to have the server build the URL:
curl -sS -X POST http://localhost:8080/ui/datasource/test \
  -H 'Content-Type: application/json' \
  --data-binary '{
    "type":"postgres","host":"localhost","port":"5432","dbname":"appbana",
    "username":"sa","password":"Password_123#","driver":"org.postgresql.Driver"
  }'
```

Designing entities (Schema Builder)
1) Visit /ui/builder
2) Create a new schema or select an existing one from the left pane.
3) Features:
   - Create: define name, datasource, and fields (exactly one PK).
   - Edit: open a saved schema, click Edit to modify non‑PK fields.
   - Rename: change the field name; backend uses `existingName` to emit a RENAME COLUMN.
   - Reorder: use ▲ / ▼ controls (visual only; does not change physical column order).
   - Duplicate detection: duplicates are highlighted and block Preview/Save.
   - Preview: generates a migration DDL plan (POST /schema?preview=true) without applying.
   - Save: applies only necessary ALTER statements and records them in migration history.
   - History: toggle "Show Migrations" to view executed DDL (GET /schema/{name}/migrations).
   - Delete: removes schema metadata; optionally drop underlying table (DELETE /schema/{name}?dropTable=true|false).
   - JSON import/export: toggle panel to edit raw JSON or copy current definition.
4) Example schema
```json
{
  "name": "contact",
  "fields": [
    {"name":"id","type":"long","primaryKey":true,"autoIncrement":true},
    {"name":"firstName","type":"string","length":100,"required":true},
    {"name":"age","type":"int","min":0}
  ]
}
```
5) Field rename example
```json
{"name":"email_address","existingName":"email","type":"string","length":255}
```
6) Deleting a schema (with table drop)
```bash
curl -X DELETE "http://localhost:8080/schema/contact?dropTable=true"
```
7) Migration history
```bash
curl -s http://localhost:8080/schema/contact/migrations
```

New / updated endpoints
- GET /schema/summaries — list `{name,datasource}` for filtering/grouping.
- GET /schema/{name}/migrations — migration history (ordered by execution time).
- DELETE /schema/{name}?dropTable=true|false — delete schema metadata (and optionally its table).

Using the runtime CRUD APIs
- After saving *or editing* a schema, CRUD endpoints reflect the updated columns. Only additive / rename / type changes are applied; destructive changes (field removal) are not yet supported.

OpenAPI & Swagger UI
- Refresh after edits to view updated model in the spec. Migration operations (preview / history / delete) are intentionally not included in the OpenAPI spec (admin-only operational endpoints).

Troubleshooting & tips
- Rename didn’t apply? Ensure you previewed & saved (not only previewed). Field removal currently unsupported (request ignored) — use rename or plan a future destructive migration process.
- Duplicate name error: resolve highlighted fields; preview is blocked until resolved.

Security notes
- DELETE /schema/{name} requires admin token when auth is enabled.

UI step-by-step walkthroughs (all features)

A) Datasource UI — Add a new datasource

![Datasource — Add or Update](app-bana-service/docs/screenshots/datasource-add.svg)

1) Open http://localhost:8080/ui/datasource
2) In “Add or Update”, fill:
   - Name: a short identifier (e.g., pg)
   - Type: select your DB (e.g., PostgreSQL)
   - JDBC URL: either paste the full URL, or leave it empty and use the Builder below
   - Username / Password: your DB credentials
   - Driver: leave blank to use the suggested placeholder, or set explicitly
3) (Optional) Use the JDBC URL Builder section:
   - Tick “Auto-build URL from fields” to keep URL synced
   - For Postgres: Host=localhost, Port=5432, Database=appbana → URL becomes jdbc:postgresql://localhost:5432/appbana
   - For H2:
     * File mode: set File path (e.g., ./data/appbana), AUTO_SERVER=TRUE is added
     * In-Memory: choose mem mode and set the memory DB name (e.g., demo), DB_CLOSE_DELAY=-1 is added
   - For SQL Server: params are appended with ;key=value automatically
4) Click “Test Connection” to verify; fix any errors shown
5) (Optional) Expand “Connection Pool” and tune settings (maxPoolSize, minIdle, timeouts, etc.)
6) Click “Save” — this upserts and activates the datasource by name
7) Check “Datasources” table below to confirm it’s listed and marked Active

B) Datasource UI — Work with saved datasources

![Datasources — List](app-bana-service/docs/screenshots/datasource-list.svg)

- Load: Click “Load” to copy a row’s values back into the form (password not shown)
- Activate: Click “Activate” to switch the active datasource
- Test: Click “Test” to validate connectivity for that saved datasource
- Delete: Click “Delete” to remove it (if it was active, another datasource will be auto-selected if available)

C) Datasource UI — Build JDBC URLs by example

![Datasource — Test Connection](app-bana-service/docs/screenshots/datasource-test.svg)

- Postgres: Host=localhost, Port=5432, DB=appbana → jdbc:postgresql://localhost:5432/appbana
- MySQL: Host=localhost, Port=3306, DB=appbana → jdbc:mysql://localhost:3306/appbana
- MariaDB: Host=localhost, Port=3306, DB=appbana → jdbc:mariadb://localhost:3306/appbana
- SQL Server: Host=localhost, Port=1433, DB=appbana → jdbc:sqlserver://localhost:1433;databaseName=appbana
- Oracle: Host=localhost, Port=1521, Service=orcl → jdbc:oracle:thin:@localhost:1521/orcl
- SQLite: File=/path/to/file.db → jdbc:sqlite:/path/to/file.db
- H2 (file): File=./data/appbana → jdbc:h2:./data/appbana;AUTO_SERVER=TRUE
- H2 (mem): Name=demo → jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1

D) Schema Builder UI — Create and evolve a schema

![Schema Builder](app-bana-service/docs/screenshots/builder.svg)

1) Open http://localhost:8080/ui/builder
2) Enter a Schema name (e.g., contact)
3) Add fields:
   - id (type=long, primaryKey=true, autoIncrement=true)
   - firstName (type=string, length=100, required=true)
   - age (type=int, min=0)
4) Preview the migration plan: click Preview or POST to /schema?preview=true
5) Save the schema: click Save or POST to /schema
6) Evolve safely: adjust fields and preview again to see DDL before applying

E) Swagger UI — Explore and test APIs

![Swagger UI](app-bana-service/docs/screenshots/swagger.svg)

1) Open http://localhost:8080/ui/swagger (loads /openapi.json)
2) If auth is enabled, enter your token in the top bar and click Save token
3) After saving schemas, hit refresh in Swagger UI to load new endpoints
4) Expand your entity (e.g., contact) and click “Try it out” for POST/GET/PUT/DELETE
5) Use JSON bodies that match your schema; submit and inspect responses
6) Errors will be shown with details — fix input or schema accordingly

Recipes (common tasks)

1) Quick smoke test with H2 (in-memory)
- In Datasource UI: Type=H2, Mode=In-Memory, Name=smoke, Username=sa → Test Connection → Save
- In Builder UI: Create a small schema → Save
- In Swagger UI: Enter token if configured → POST a record, then GET the list

2) Switch to Postgres running in Docker
- Run the Docker command above (sa / Password_123#)
- In Datasource UI: Name=pg, Type=PostgreSQL, Host=localhost, Port=5432, DB=appbana, Username=sa, Password=Password_123#, Driver=org.postgresql.Driver
- Test Connection → Save (activates pg)
- Use Builder + Swagger as usual

3) Tune pooling when traffic increases
- Open the active datasource → set maxPoolSize to a higher value (e.g., 20) and minIdle to 5
- Save and monitor DB performance

Add recipe: Preview + Apply rename
```bash
# Preview rename email -> email_address
curl -s -X POST 'http://localhost:8080/schema?preview=true' \
 -H 'Content-Type: application/json' \
 --data '{"name":"contact","fields":[{"name":"id","type":"long","primaryKey":true,"autoIncrement":true},{"name":"email_address","existingName":"email","type":"string","length":255}]}'
# Apply
curl -s -X POST http://localhost:8080/schema \
 -H 'Content-Type: application/json' \
 --data '{"name":"contact","fields":[{"name":"id","type":"long","primaryKey":true,"autoIncrement":true},{"name":"email_address","existingName":"email","type":"string","length":255}]}'
```

Add recipe: View migration history
```bash
curl -s http://localhost:8080/schema/contact/migrations
```

Add recipe: Delete schema only (keep table)
```bash
curl -X DELETE http://localhost:8080/schema/contact
```

Add recipe: Delete schema and drop table
```bash
curl -X DELETE 'http://localhost:8080/schema/contact?dropTable=true'
```

Where to go next
- Explore the backlog in `TODO.md`.
- Review README.md for deeper details and API references.

Note: The images above will automatically use the bundled SVGs under `docs/screenshots/`.

---

Angular Studio (UI) quickstart — optional
- The repository includes an Angular workspace under `ui/` with a Studio app (SSR).
- Use these scripts from the repo root to build libraries + Studio and run the SSR server:

```zsh
cd /Users/dilip/git/app-bana
./build.sh --clean        # build all Angular libs and the Studio app
./run.sh --port 4000 --open
```

- After the server logs “Node Express server listening on http://localhost:4000”, open:
  - http://localhost:4000

Notes
- You can also run from the UI workspace after a build:
  - `cd ui && npm run serve:ssr:studio`
- The Studio app includes Google Material Icons via a link tag in `projects/studio/src/index.html` so `<mat-icon>` ligatures work.
