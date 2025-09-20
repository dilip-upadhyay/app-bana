# AppBana Functional Specification

Version: 1.4 (snapshot)
Date: 2025-09-20

Purpose
- Describe current, working functionality of the AppBana MVP (metadata-driven UI → API → DB), including multi-datasource management, connection pooling, Java 25 runtime, per-datasource health checks, and provide prioritized future enhancements.

1. Overview
- Runtime is metadata-driven: a UI builder emits an entity schema (JSON). Backend persists the schema and automatically creates/migrates a backing SQL table. Generic CRUD endpoints are exposed at runtime for each saved entity.
- No heavy frameworks used: Java SE com.sun.net.httpserver.HttpServer, JDBC (H2 by default), Jackson for JSON.
- Java 25 runtime: server handles each HTTP request on a virtual thread to optimize resource usage for blocking JDBC calls.
- Datasource management: built-in UI allows adding multiple datasources (name + type), activating one, and deleting; the active datasource is used for all DB operations.
- Connection pooling: HikariCP-based pool per active datasource; pool settings are configurable per datasource.
- OpenAPI: spec generated from stored schemas at /openapi.json; embedded Swagger UI at /ui/swagger.
- Health: liveness (/health), readiness with DB check (/ready), and per-datasource health (/ui/datasource/health).

2. High-level architecture
- Frontend: static HTML/JS UIs served from resources:
  - builder.html — create EntitySchema JSON and POST to /schema
  - datasource.html — manage datasources (add/update/list/activate/delete) via /ui/datasource/* endpoints; supports configuring pool settings; includes a JDBC URL Builder; shows Status and Last tested columns; has Test and Ping actions
  - swagger.html — embedded Swagger UI for /openapi.json
- Backend services:
  - ApiServer — embedded HTTP server; handlers for /schema, /api/*, /openapi.json, /ui/swagger, and datasource routes under /ui/datasource/* (includes pool fields in list/config/save). Also constructs JDBC URLs from components when url is omitted in /ui/datasource/save. Exposes per-datasource health endpoint.
  - SchemaManager — validates schema JSON, persists schema (appbana_schemas), creates/migrates tables (records DDLs in appbana_migrations), supports migration preview and list with pagination/search
  - JdbcManager — resolves the active datasource from config, infers JDBC driver from type/URL when missing (via DriverUtil), configures a HikariCP connection pool with per-datasource settings
  - ConfigManager — loads/saves config JSON; normalizes to multi-datasource format; applies env overrides; fills missing datasource types via DriverUtil
  - CodeGenerator — emits POJO sources (no compile/load)

3. Key runtime endpoints
- Schema and CRUD: unchanged (see 3.x)
- Datasource management (JSON):
  - GET /ui/datasource/list — list all datasources (no passwords), includes pool fields and last test metadata (lastTestOk, lastTestAtEpochMs, lastTestMessage, lastTestDbProduct, lastTestDbVersion, lastTestElapsedMs)
  - GET /ui/datasource/config — current active datasource details (no password), includes pool fields
  - POST /ui/datasource/save — upsert datasource and set active
    - Body supports EITHER a full URL OR components for server-side construction.
      - Full URL form: { name, type?, url, username?, password?, driver?, maxPoolSize?, minIdle?, connectionTimeoutMs?, idleTimeoutMs?, maxLifetimeMs?, autoCommit?, poolName? }
      - Components form (when url omitted): { name, type, host, port, dbname, params?, username?, password?, driver?, pool... }
        - H2 extras: h2Mode (file|mem), h2File, h2MemName; SQLite extra: sqliteFile
    - Empty/missing password does not overwrite existing
  - POST /ui/datasource/test — attempt a one-off JDBC connection using provided url or components (or by name); returns {ok,message|error,url,dbProduct,dbVersion,elapsedMs,sqlState?,errorCode?}; supports optional timeoutSec (default 5); masks passwords in returned URL; persists last test result if `name` refers to a saved datasource
  - GET /ui/datasource/health — ping a datasource (name param optional; defaults to active); returns {ok,name,url,dbProduct?,dbVersion?,elapsedMs,error?,sqlState?,errorCode?}
  - POST /ui/datasource/activate — set active datasource by name
  - POST /ui/datasource/delete — delete datasource by name (reassigns active if needed)
- OpenAPI: GET /openapi.json — generated spec for CRUD endpoints
- Swagger UI: GET /ui/swagger — renders /openapi.json

3.x Schema CRUD endpoints (unchanged)
- POST /schema — validate & persist schema, create/migrate table; `?preview=true` returns planned DDL
- GET /schema — list schema names; supports `?page=&size=&q=`
- GET /schema/{name} — return schema JSON
- POST /api/{entity} — insert; GET /api/{entity} — list; GET/PUT/DELETE /api/{entity}/{id} — record ops

4. EntitySchema model (fields and semantics)
- Unchanged; see model/EntitySchema.java

5. Server-side validation and coercion
- Required fields, numeric ranges, string length/patterns, timestamp parsing (epoch millis or ISO-8601). Bad input → 400 {error}.

6. Database mapping & migrations
- Table/column identifiers quoted and uppercased. Simple migrations and preview supported.

7. Configuration model
- AppConfig supports multi-datasource:
  - datasources: [ { name, type, jdbcUrl, username, password, driver, maxPoolSize?, minIdle?, connectionTimeoutMs?, idleTimeoutMs?, maxLifetimeMs?, autoCommit?, poolName?, lastTest*? } ]
  - activeDatasource: string (name)
- Backward compatibility: if only root fields are present (jdbcUrl/username/password/driver/name), ConfigManager seeds a default datasource and marks it active.
- Environment overrides (optional, applied to root fields): APPBANA_JDBC_URL, APPBANA_DB_USER, APPBANA_DB_PASS, APPBANA_DB_DRIVER.
- Config path: APPBANA_CONFIG env or -Dappbana.config (default data/appbana-config.json).

8. Frontend UIs
- builder.html — minimal schema builder (unchanged)
- datasource.html — multi-datasource management UI with pool settings, JDBC URL Builder, Test Connection button, per-row Test and Ping actions, status chip (Live/Down/Unknown), and Last tested column
- swagger.html — embedded Swagger UI for /openapi.json

9. Build, run, environment
- Build: Maven with Shade plugin; runnable fat JAR under target/
- Run: java -jar target/app-bana-1.0-SNAPSHOT-fat.jar
- Port: default 8080; override with -Dappbana.port or APPBANA_PORT
- Startup behavior: if DB init fails (e.g., bad credentials), server still starts so you can fix the datasource via /ui/datasource

10. Security and production considerations
- Add authN/Z to schema and datasource endpoints; protect secrets; consider rate-limiting Test/Health endpoints; TLS/HTTPS

11. Logging and monitoring
- Basic SLF4J simple logging; health endpoints for liveness/readiness; consider adding metrics and request logging

12. Artifacts, files, and locations (updated)
- Key files:
  - src/main/java/org/example/ApiServer.java — datasource endpoints include pool fields and last test metadata; exposes /ui/datasource/health; server-side JDBC URL construction
  - src/main/java/org/example/JdbcManager.java — HikariCP pool configuration; uses DriverUtil for driver/type inference
  - src/main/java/org/example/ConfigManager.java — multi-datasource normalization & env overrides; infers missing types via DriverUtil
  - src/main/java/org/example/DriverUtil.java — central driver/type inference mapping
  - src/main/java/org/example/AppConfig.java, DatasourceConfig.java — config models (DatasourceConfig includes pool and last test fields)
  - src/main/resources/ui/datasource.html — UI for datasource management (pool settings, status chip, last tested, Test/Ping actions)
  - src/main/resources/ui/swagger.html — Swagger UI
- Built artifacts: target/app-bana-1.0-SNAPSHOT-fat.jar

13. Recommended enhancements (prioritized)
- A: AuthN/AuthZ for schema and datasource management
- B: Enhanced builder UX; CRUD query params (paging/sorting/filtering)
- C: Migration engine improvements; rollback support; test coverage; CI/docs checks

14. Change Log (recent)
- 2025-09-20: Added per-datasource health endpoint (/ui/datasource/health); persisted last test metadata; masked sensitive URL parts; configurable test timeout; centralized driver/type inference via DriverUtil.
- 2025-09-19: Upgraded to Java 25, virtual threads; added Swagger UI (/ui/swagger).
- 2025-09-19: Added server-side JDBC URL construction in /ui/datasource/save; Test Connection endpoint and UI; HikariCP pooling with per-datasource settings; multi-datasource management.
