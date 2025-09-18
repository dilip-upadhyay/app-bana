# Low Level Design — AppBana

Purpose
- Provide a concrete, code-oriented map of the project so an automated agent can navigate, extend, and debug efficiently.

Runtime contract
- Input: HTTP requests to:
  - /schema (JSON body: EntitySchema) — create/migrate (POST) and list/load (GET)
  - /api/{entity}[/{id}] — CRUD over JDBC using metadata-defined tables
  - /ui/* — static HTML UIs (builder, datasource, swagger)
  - /openapi.json — generated OpenAPI 3.0 spec
- Output: JSON for API responses, HTML for UI pages.
- Errors: JSON {"error":"message"}; appropriate HTTP status (400/404/405/500).

Ports and processes
- Default HTTP port 8080. Override with -Dappbana.port or APPBANA_PORT.
- Single JVM process. Request handling uses virtual threads (Thread.ofVirtual()).

Threading model
- com.sun.net.httpserver.HttpServer with a per-request virtual thread executor: server.setExecutor(r -> Thread.ofVirtual().start(r)).
- All handlers are short-lived, synchronous JDBC calls; blocking is fine with virtual threads.

Key packages and classes (src/main/java/org/example)
- Main.java — Entrypoint. Initializes SchemaManager.init() and starts ApiServer on configured port.
- ApiServer.java — Binds routes and serves static UIs. Handlers:
  - /schema — SchemaHandler: POST save/preview; GET list; GET by name
  - /api — EntityHandler: POST create; GET list; GET/PUT/DELETE by id
  - /openapi.json — builds OpenAPI from stored schemas
  - /ui/builder — serves builder.html
  - /ui/datasource — serves datasource.html
  - /ui/swagger — serves swagger.html (embedded Swagger UI loading /openapi.json)
  - /ui/datasource/config|list|save|activate|delete — JSON endpoints for multi-DS management (no passwords returned)
- SchemaManager.java — Schema persistence/migrations:
  - init(): ensures meta tables
  - saveSchema(EntitySchema): validates, persists JSON, applies DDL changes
  - generateMigrationPlan(EntitySchema): preview DDL without applying
  - listSchemaNames([page,size,q]): list stored schemas
  - loadSchema(name): load JSON into EntitySchema
- JdbcManager.java — Connection acquisition via HikariCP. Responsibilities:
  - Build/maintain a HikariDataSource for the active datasource
  - Infer driver from type/URL if missing; apply pool settings and sane defaults
  - Expose getConnection(); rebuild pool lazily on config change
- ConfigManager.java — Load/save config JSON (data/appbana-config.json default), normalize into multi-DS shape, apply env overrides.
- AppConfig.java — Root config model (legacy single-DS fields kept for back-compat) + datasources[] + activeDatasource.
- DatasourceConfig.java — DS model: {name,type,jdbcUrl,username,password,driver,maxPoolSize?,minIdle?,connectionTimeoutMs?,idleTimeoutMs?,maxLifetimeMs?,autoCommit?,poolName?}.
- OpenApiGenerator.java — Converts stored EntitySchema list into a minimal OpenAPI 3.0 document.
- model/EntitySchema.java — Schema model with Field sub-class (name,type,length,required,primaryKey,autoIncrement,min,max,pattern).

Static resources (src/main/resources/ui)
- builder.html — Minimal schema builder UI (posts JSON to /schema; also useful for preview).
- datasource.html — Multi-datasource management UI. Fields: name, type, jdbcUrl, username, password, driver, and Pool section (maxPoolSize, minIdle, connectionTimeoutMs, idleTimeoutMs, maxLifetimeMs, autoCommit, poolName). Actions: Save/Activate/Delete/List/Load.
- swagger.html — Embedded Swagger UI for /openapi.json.

Config resolution
- Path: APPBANA_CONFIG or -Dappbana.config; default data/appbana-config.json.
- When no file exists, ConfigManager returns defaults and normalizes to a single H2 datasource named "default".
- Env overrides on root fields only: APPBANA_JDBC_URL, APPBANA_DB_USER, APPBANA_DB_PASS, APPBANA_DB_DRIVER.

Database mapping rules
- Table name = UPPERCASE(schema.name). Column names = UPPERCASE(field.name). Quoted identifiers with double-quotes.
- Supported field types → SQL types (simplified):
  - string/text → VARCHAR(length or default)
  - int/integer → INTEGER
  - long → BIGINT
  - boolean → BOOLEAN
  - date/timestamp → TIMESTAMP
- Primary key: first field with primaryKey=true. Auto-increment supported for integer/long on H2 and common RDBMS.

Validation and coercion (EntityHandler)
- Required fields enforced; numbers range-checked (min/max), strings length-checked, regex pattern supported; timestamps accept epoch millis or ISO-8601.
- Bad input → 400 with {error}.

OpenAPI generation
- Paths for each stored entity: /api/{entity} (GET,POST) and /api/{entity}/{id} (GET,PUT,DELETE).
- Components.schemas include object model per entity based on fields.
- Served at /openapi.json; Swagger UI at /ui/swagger.

Error handling
- send() and sendJson() helpers. 405 for wrong methods; 404 for unknown entity or missing schema; 500 for unexpected exceptions.

Performance notes
- Virtual threads minimize thread overhead for blocking JDBC per request.
- HikariCP settings default to conservative values; configurable per datasource.

Observability
- SLF4J simple logs. TODO: add health endpoints and per-DS connectivity check.

Security considerations
- No auth by default; avoid exposing in untrusted networks. TODO: add authN/Z on schema and datasource endpoints.

Developer workflows
- Build: ./mvnw -DskipTests package → target/app-bana-1.0-SNAPSHOT-fat.jar
- Run: java -jar target/app-bana-1.0-SNAPSHOT-fat.jar (override port with -Dappbana.port=8081)
- Edit UI: update files in resources/ui; ApiServer serves them at /ui/*

Edge cases to watch
- Port conflicts → BindException; use a different -Dappbana.port
- Wrong DB credentials → startup warns; fix via /ui/datasource and retry
- Missing password on save → does not overwrite existing (by design)
- No schemas stored → /openapi.json returns an empty paths object (valid)

Test checklist (manual)
- GET /ui/datasource/list returns array (default H2 present)
- POST /ui/datasource/save with new name creates + activates; list shows it
- POST /schema?preview=true returns DDL plan; POST /schema applies
- CRUD endpoints operate as expected; GET /openapi.json reflects entities
- /ui/swagger renders the spec

