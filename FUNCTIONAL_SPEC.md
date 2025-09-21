# AppBana Functional Specification

Version: 1.6 (snapshot)
Date: 2025-09-21

Purpose
- Describe current, working functionality of the AppBana MVP (metadata-driven UI → API → DB), including multi-datasource management, connection pooling, Java 25 runtime, per-datasource health checks, optional token-based authentication, and provide prioritized future enhancements.

1. Overview
- Runtime is metadata-driven: a UI builder emits an entity schema (JSON). Backend persists the schema and automatically creates/migrates a backing SQL table. Generic CRUD endpoints are exposed at runtime for each saved entity.
- No heavy frameworks used: Java SE com.sun.net.httpserver.HttpServer, JDBC (H2 by default), Jackson for JSON.
- Java 25 runtime: server handles each HTTP request on a virtual thread to optimize resource usage for blocking JDBC calls.
- Datasource management: built-in UI allows adding multiple datasources (name + type), activating one, and deleting; the active datasource is used for all DB operations.
- Connection pooling: HikariCP-based pool per active datasource; pool settings are configurable per datasource.
- OpenAPI: spec generated from stored schemas at /openapi.json; embedded Swagger UI at /ui/swagger.
- Health: liveness (/health), readiness with DB check (/ready), and per-datasource health (/ui/datasource/health).
- Authentication (optional): token-based auth for /schema, /api/*, /openapi.json, and /ui/datasource/*.
- HTTPS (optional): server can expose an HTTPS listener (TLS) with a provided keystore and optionally redirect HTTP to HTTPS.

2. High-level architecture
- Frontend: static HTML/JS UIs served from resources:
  - builder.html — create EntitySchema JSON and POST to /schema (topbar includes an Auth token box; sends token headers automatically)
  - datasource.html — manage datasources (add/update/list/activate/delete) via /ui/datasource/* endpoints; supports pool settings and Test/Ping; includes Auth token box
  - swagger.html — embedded Swagger UI for /openapi.json with a token box; injects token headers via requestInterceptor
- Backend services:
  - ApiServer — embedded HTTP server; handlers for /schema, /api/*, /openapi.json, /ui/swagger, and datasource routes under /ui/datasource/* (includes pool fields in list/config/save). Also constructs JDBC URLs from components when url is omitted in /ui/datasource/save. Exposes per-datasource health endpoint. Enforces optional token-based auth. Starts optional HTTPS server and optional redirect from HTTP to HTTPS.
  - SchemaManager — validates schema JSON, persists schema (appbana_schemas), creates/migrates tables (records DDLs in appbana_migrations), supports migration preview and list with pagination/search
  - JdbcManager — resolves the active datasource from config, infers JDBC driver from type/URL when missing (via DriverUtil), configures a HikariCP connection pool with per-datasource settings
  - ConfigManager — loads/saves config JSON; normalizes to multi-datasource format; applies env overrides; fills missing datasource types via DriverUtil
  - CodeGenerator — emits POJO sources (no compile/load)

3. Key runtime endpoints
- Schema and CRUD (authn/az rules below):
  - POST /schema — save schema or preview migration with `?preview=true` (admin token)
  - GET /schema — list schema names (read or admin token)
  - GET /schema/{name} — return schema JSON (read or admin token)
  - POST /api/{entity} — insert (admin token)
  - GET /api/{entity} — list (read or admin token)
  - GET /api/{entity}/{id} — get by id (read or admin token)
  - PUT /api/{entity}/{id} — update by id (admin token)
  - DELETE /api/{entity}/{id} — delete by id (admin token)
- Datasource management (JSON):
  - GET /ui/datasource/list — list all datasources (no passwords), includes pool fields and last test metadata (read or admin token)
  - GET /ui/datasource/config — current active datasource details (no password), includes pool fields (read or admin token)
  - POST /ui/datasource/save — upsert datasource and set active (admin token)
  - POST /ui/datasource/test — connection test; persists lastTest* when testing by name (admin token)
  - GET /ui/datasource/health — ping a datasource (read or admin token)
  - POST /ui/datasource/activate — set active datasource (admin token)
  - POST /ui/datasource/delete — delete datasource (admin token)
- OpenAPI & Swagger:
  - GET /openapi.json — generated spec for CRUD endpoints (read or admin token)
  - GET /ui/swagger — renders Swagger UI for /openapi.json

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
  - Optional: adminToken (read-write), readToken (read-only)
  - HTTPS: httpsEnabled (bool), httpsPort (int), keystorePath (string), keystorePassword (string), keyPassword (string?), redirectHttpToHttps (bool)
- Backward compatibility: if only root fields are present (jdbcUrl/username/password/driver/name), ConfigManager seeds a default datasource and marks it active.
- Environment overrides (optional, applied to root fields and tokens): APPBANA_JDBC_URL, APPBANA_DB_USER, APPBANA_DB_PASS, APPBANA_DB_DRIVER, APPBANA_ADMIN_TOKEN, APPBANA_READ_TOKEN.
- Config path: APPBANA_CONFIG env or -Dappbana.config (default data/appbana-config.json).

8. Frontend UIs
- builder.html — minimal schema builder with auth token box; sends only `X-AppBana-Token` header (token value sanitized) to avoid browser header restrictions; server also accepts `Authorization: Bearer` for non-UI clients
- datasource.html — multi-datasource management UI with pool settings, JDBC URL Builder, Test/Ping, status chip and Last tested; includes auth token box; sends only `X-AppBana-Token` header (sanitized)
- swagger.html — embedded Swagger UI; auth token box with requestInterceptor that injects only `X-AppBana-Token` for all requests (including initial /openapi.json)

9. Build, run, environment
- Build: Maven with Shade plugin; runnable fat JAR under target/
- Run: java -jar target/app-bana-1.0-SNAPSHOT-fat.jar
- Ports:
  - HTTP: default 8080; override with -Dappbana.port or APPBANA_PORT
  - HTTPS (optional): default 8443; enable with httpsEnabled=true and configure keystore
- HTTPS configuration
  - AppConfig fields: httpsEnabled (bool), httpsPort (int), keystorePath (string), keystorePassword (string), keyPassword (string?), redirectHttpToHttps (bool)
  - Env/system props: APPBANA_HTTPS_ENABLED | -Dappbana.https.enabled, APPBANA_HTTPS_PORT | -Dappbana.https.port, APPBANA_KEYSTORE_PATH | -Dappbana.keystore.path, APPBANA_KEYSTORE_PASSWORD | -Dappbana.keystore.password, APPBANA_KEY_PASSWORD | -Dappbana.key.password, APPBANA_REDIRECT_HTTP_TO_HTTPS | -Dappbana.redirect.http.to.https
  - Behavior: when enabled and keystore is valid, an HTTPS server starts; if redirect is true, HTTP returns 308 with Location to HTTPS URL.

10. Security and production considerations
- Auth model (optional): when either adminToken or readToken is configured in AppConfig (or via env/system properties), endpoints enforce tokens.
  - Clients may send token via `X-AppBana-Token: <token>` or `Authorization: Bearer <token>`.
  - Built-in UIs send only `X-AppBana-Token` (sanitized) to avoid browser header syntax errors.
  - readToken grants read-only access; adminToken grants full read/write (and also satisfies read checks).
  - If no tokens are configured, all endpoints are open (development mode).
- HTTPS: expose an HTTPS listener (TLS) with a provided keystore; optionally redirect HTTP to HTTPS.
- Additional recommendations: protect config files; add TLS/HTTPS via reverse proxy; rate limit expensive endpoints; structured request logs and metrics.

11. Logging and monitoring
- Basic SLF4J simple logging; health endpoints for liveness/readiness; consider adding metrics and request logging

12. Artifacts, files, and locations (updated)
- Key files:
  - src/main/java/org/example/ApiServer.java — datasource endpoints include pool fields and last test metadata; exposes /ui/datasource/health; server-side JDBC URL construction; enforces token auth; starts optional HttpsServer and optional redirect
  - src/main/java/org/example/JdbcManager.java — HikariCP pool configuration; uses DriverUtil for driver/type inference
  - src/main/java/org/example/ConfigManager.java — multi-datasource normalization & env overrides; infers missing types via DriverUtil; applies token env overrides; reads HTTPS env/system properties
  - src/main/java/org/example/DriverUtil.java — central driver/type inference mapping
  - src/main/java/org/example/AppConfig.java, DatasourceConfig.java — config models (DatasourceConfig includes pool and last test fields); HTTPS fields added
  - src/main/resources/ui/datasource.html — UI for datasource management (pool settings, status chip, last tested, Test/Ping, auth token box)
  - src/main/resources/ui/builder.html — schema builder UI with auth token box
  - src/main/resources/ui/swagger.html — Swagger UI with auth token box and requestInterceptor
- Built artifacts: target/app-bana-1.0-SNAPSHOT-fat.jar

13. Recommended enhancements (prioritized)
- A: Metrics and structured request logs; CRUD query params (paging/sorting/filtering) + OpenAPI
- B: Swagger examples/response schemas; bundle Swagger assets locally (avoid CDN)
- C: Migration engine improvements; rollback support; test coverage; CI/docs checks

14. Change Log (recent)
- 2025-09-21: Optional HTTPS support with keystore-based TLS and optional HTTP→HTTPS redirect; env/system properties for configuration.
- 2025-09-21: UI token header hardening — built-in UIs now send only X-AppBana-Token with sanitized value to avoid browser header syntax errors; server still accepts Authorization: Bearer for API clients.
- 2025-09-20: Optional token-based authentication implemented. Builder/datasource/swagger UIs include token box and send headers; backend enforces read/admin tokens for /schema, /api/*, /openapi.json, and /ui/datasource/*.
- 2025-09-20: Added per-datasource health endpoint (/ui/datasource/health); persisted last test metadata; masked sensitive URL parts; configurable test timeout; centralized driver/type inference via DriverUtil.
- 2025-09-19: Upgraded to Java 25, virtual threads; added Swagger UI (/ui/swagger).
- 2025-09-19: Added server-side JDBC URL construction in /ui/datasource/save; Test Connection endpoint and UI; HikariCP pooling with per-datasource settings; multi-datasource management.
