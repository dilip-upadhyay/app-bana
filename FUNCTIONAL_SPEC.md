# AppBana Functional Specification

Version: 1.2 (snapshot)
Date: 2025-09-19

Purpose
- Describe current, working functionality of the AppBana MVP (metadata-driven UI → API → DB), including multi-datasource management and connection pooling, and provide prioritized future enhancements.

1. Overview
- Runtime is metadata-driven: a UI builder emits an entity schema (JSON). Backend persists the schema and automatically creates/migrates a backing SQL table. Generic CRUD endpoints are exposed at runtime for each saved entity.
- No heavy frameworks used: Java SE com.sun.net.httpserver.HttpServer, JDBC (H2 by default), Jackson for JSON.
- Datasource management: built-in UI allows adding multiple datasources (name + type), activating one, and deleting; the active datasource is used for all DB operations.
- Connection pooling: HikariCP-based pool per active datasource; pool settings are configurable per datasource.

2. High-level architecture
- Frontend: static HTML/JS UIs served from resources:
  - builder.html — create EntitySchema JSON and POST to /schema
  - datasource.html — manage datasources (add/update/list/activate/delete) via /ui/datasource/* endpoints; supports configuring pool settings
- Backend services:
  - ApiServer — embedded HTTP server; handlers for /schema, /api/*, /openapi.json, and datasource routes under /ui/datasource/* (now includes pool fields in list/config/save)
  - SchemaManager — validates schema JSON, persists schema (appbana_schemas), creates/migrates tables (records DDLs in appbana_migrations), supports migration preview and list with pagination/search
  - JdbcManager — resolves the active datasource from config, infers JDBC driver from type/URL when missing, configures a HikariCP connection pool with per-datasource settings
  - ConfigManager — loads/saves config JSON (data/appbana-config.json by default), normalizes to multi-datasource format, applies env overrides
  - CodeGenerator — emits POJO sources (no compile/load)
- Database: active datasource (H2 by default). Other RDBMS supported via JDBC URL/driver.

3. Key runtime endpoints
- Schema and CRUD: unchanged (see 3.x)
- Datasource management (JSON):
  - GET /ui/datasource/list — list all datasources (no passwords), includes pool fields
  - GET /ui/datasource/config — current active datasource details (no password), includes pool fields
  - POST /ui/datasource/save — upsert datasource and set active
    - Body: { name, type?, url, username?, password?, driver?, maxPoolSize?, minIdle?, connectionTimeoutMs?, idleTimeoutMs?, maxLifetimeMs?, autoCommit?, poolName? }
    - Empty/missing password does not overwrite existing
  - POST /ui/datasource/activate — set active datasource by name
  - POST /ui/datasource/delete — delete datasource by name (reassigns active if needed)
- OpenAPI: GET /openapi.json — generated spec for CRUD endpoints

3.x Schema CRUD endpoints (unchanged)
- POST /schema — validate & persist schema, create/migrate table; `?preview=true` returns planned DDL
- GET /schema — list schema names; supports `?page=&size=&q=`
- GET /schema/{name} — return schema JSON
- POST /api/{entity} — insert; GET /api/{entity} — list; GET/PUT/DELETE /api/{entity}/{id} — record ops

4. EntitySchema model (fields and semantics)
- Unchanged; see previous version

5. Server-side validation and coercion
- Unchanged; see previous version

6. Database mapping & migrations
- Unchanged; see previous version (adds, simple renames, simple type changes). Migration preview supported.

7. Configuration model
- AppConfig supports multi-datasource:
  - datasources: [ { name, type, jdbcUrl, username, password, driver, maxPoolSize?, minIdle?, connectionTimeoutMs?, idleTimeoutMs?, maxLifetimeMs?, autoCommit?, poolName? } ]
  - activeDatasource: string (name)
- Backward compatibility: if only root fields are present (jdbcUrl/username/password/driver/name), ConfigManager seeds a default datasource and marks it active.
- Environment overrides (optional, applied to root fields): APPBANA_JDBC_URL, APPBANA_DB_USER, APPBANA_DB_PASS, APPBANA_DB_DRIVER.
- Config path: APPBANA_CONFIG env or -Dappbana.config (default data/appbana-config.json).

8. Frontend UIs
- builder.html — minimal schema builder (unchanged)
- datasource.html — multi-datasource management UI
  - Fields: name, type (h2/postgres/mysql/mariadb/mssql/oracle/sqlite/custom), jdbcUrl, username, password, driver
  - Pool subsection: maxPoolSize, minIdle, connectionTimeoutMs, idleTimeoutMs, maxLifetimeMs, autoCommit (checkbox), poolName
  - Driver/URL hints auto-fill based on type; password is not prefetched nor displayed
  - List table shows: Active, Name, Type, URL, User, Driver; actions: Load, Activate, Delete

9. Build, run, environment
- Build: Maven with Shade plugin; runnable fat JAR under target/
- Run: java -jar target/app-bana-1.0-SNAPSHOT-fat.jar (server on 8080)
- Startup behavior: if DB init fails (e.g., bad credentials), server still starts so you can fix the datasource via /ui/datasource

10. Security and production considerations
- Add authN/Z to schema and datasource endpoints; protect secrets; consider per-datasource health checks

11. Logging and monitoring
- Basic SLF4J simple logging; consider adding health endpoints and metrics

12. Artifacts, files, and locations (updated)
- Key files:
  - src/main/java/org/example/ApiServer.java — datasource endpoints include pool fields
  - src/main/java/org/example/JdbcManager.java — HikariCP pool configuration
  - src/main/java/org/example/ConfigManager.java — multi-datasource normalization & env overrides
  - src/main/java/org/example/AppConfig.java, DatasourceConfig.java — config models (DatasourceConfig includes pool fields)
  - src/main/resources/ui/datasource.html — UI for datasource management (pool settings)
- Built artifacts: target/app-bana-1.0-SNAPSHOT-fat.jar

13. Recommended enhancements (prioritized)
- A: AuthN/AuthZ for schema and datasource management; per-datasource health checks; TLS/HTTPS
- B: Enhanced builder UX; CRUD query params (paging/sorting/filtering)
- C: Migration engine improvements; rollback support; test coverage
- D: Swagger UI; multi-tenant isolation; plugin architecture

14. Suggested immediate next tasks
- Add authentication for /schema, /api, and /ui/datasource routes
- Add health/diagnostics endpoints per datasource and a simple connectivity test button in the UI

15. Change Log (recent)
- 2025-09-19: Added HikariCP connection pooling with per-datasource settings; UI and endpoints extended to handle pool fields.
- 2025-09-14: Added multi-datasource management (UI and endpoints); config format extended with datasources[] and activeDatasource; driver inference by type/URL; startup resiliency when DB init fails.
- 2025-09-14: Migration preview endpoint and UI flow; pagination/search for schema listing.
