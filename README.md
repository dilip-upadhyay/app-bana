# AppBana — Metadata-driven UI → API → Database

Metadata-driven MVP: design forms in a minimal UI builder, persist the schema, auto-create/migrate a backing table, and expose runtime CRUD APIs. Implemented with plain Java SE (no heavy frameworks).

Quick summary
- Frontend: minimal UI builder (vanilla JS) that emits schema JSON.
- Backend: Java (HttpServer) that persists schemas, auto-creates/migrates tables via JDBC, and exposes generic CRUD endpoints at runtime.
- DB: H2 embedded (file) by default; JDBC usage allows swapping to Postgres/MySQL/etc.
- Datasources: built-in UI to add/manage multiple datasources (by name and type) and select the active one at runtime.

Status of repository
- Fully working MVP backend and minimal frontend builder included.
- Basic builder-v1 UI is present; advanced builder-v2 files were removed.
- Swagger/OpenAPI spec is available at `/openapi.json` for all generated REST endpoints.
- Datasource management UI available at `/ui/datasource` with list/activate/delete actions.
- Built fat JAR available under `target/` after building.
- COPILOT_NOTES.md contains an agent-friendly snapshot of the current state.

Tech stack
- Java 21 (LTS)
- H2 (embedded) for development
- Jackson (jackson-databind) for JSON
- SLF4J simple for logging
- Maven build with Shade plugin (uber jar)

Build & run
- With system Maven installed:
  - mvn -DskipTests package
  - java -jar target/app-bana-1.0-SNAPSHOT-fat.jar
- With the provided wrapper:
  - chmod +x mvnw
  - ./mvnw -DskipTests package
  - java -jar target/app-bana-1.0-SNAPSHOT-fat.jar
- From IDE: run org.example.Main

Default runtime behavior
- On startup the app attempts to ensure two metadata tables (in the active datasource):
  - `appbana_schemas(name PK, json CLOB)` — stores schema JSON
  - `appbana_migrations(id IDENTITY, schema_name, sql CLOB, executed_at TIMESTAMP)` — records DDL executed
- Embedded HTTP server listens on port 8080.
- UI builder: http://localhost:8080/ui/builder
- Datasource UI: http://localhost:8080/ui/datasource
- OpenAPI: http://localhost:8080/openapi.json

Datasource management
- UI: `/ui/datasource` supports Add/Update, List, Activate, and Delete.
- Each datasource has: name, type (h2/postgres/mysql/mariadb/mssql/oracle/sqlite/custom), jdbcUrl, username, password, driver.
- Driver inference: if driver is blank, the system infers it from `type` or the JDBC URL.
- Active datasource: the server uses the currently active datasource for all DB operations.

Datasource API (JSON)
- GET `/ui/datasource/list` → array of datasources (without passwords), each has {name,type,jdbcUrl,username,driver,active}.
- GET `/ui/datasource/config` → current active datasource details (without password).
- POST `/ui/datasource/save` body: {name, type?, url, username?, password?, driver?}
  - Upserts the datasource by name; if password is empty/missing it isn’t overwritten; activates the saved datasource.
- POST `/ui/datasource/activate` body: {name}
- POST `/ui/datasource/delete` body: {name}

Configuration
- Config file path: `APPBANA_CONFIG` env var or `-Dappbana.config=...` system property (default: `data/appbana-config.json`).
- Environment overrides (optional):
  - APPBANA_JDBC_URL — override JDBC URL
  - APPBANA_DB_USER — override username
  - APPBANA_DB_PASS — override password
  - APPBANA_DB_DRIVER — override driver class
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
      "driver": "org.h2.Driver"
    }
  ],
  "activeDatasource": "primary"
}
```
- Backward compatibility: if only root fields are present (jdbcUrl/username/password/driver/name), the app seeds a default datasource and marks it active.

API endpoints (runtime generic CRUD)
- POST /schema — save schema or preview migration with `?preview=true`
- GET /schema — list schema names (supports `?page=&size=&q=`)
- GET /schema/{name} — return schema JSON
- POST /api/{entity} — insert
- GET /api/{entity} — list
- GET /api/{entity}/{id} — get by id
- PUT /api/{entity}/{id} — update by id
- DELETE /api/{entity}/{id} — delete by id
- GET /openapi.json — OpenAPI 3.0 spec for all generated endpoints

Schema JSON (example)
```
{
  "name": "contact",
  "fields": [
    {"name":"id","type":"long","primaryKey":true,"autoIncrement":true},
    {"name":"firstName","type":"string","length":100,"required":true},
    {"name":"age","type":"int","min":0}
  ]
}
```

Where to change common settings
- Port: src/main/java/org/example/Main.java (argument to ApiServer.start)
- Datasource resolution: src/main/java/org/example/JdbcManager.java
- Config: src/main/java/org/example/ConfigManager.java and src/main/java/org/example/AppConfig.java
- Datasource UI: src/main/resources/ui/datasource.html

Key files
- src/main/java/org/example/ApiServer.java — HTTP handlers (schema, entity CRUD, datasource management, openapi)
- src/main/java/org/example/SchemaManager.java — schema persistence and migration
- src/main/java/org/example/JdbcManager.java — JDBC connection (uses active datasource)
- src/main/java/org/example/ConfigManager.java — loads/saves config; normalizes multi-datasource format
- src/main/java/org/example/AppConfig.java, DatasourceConfig.java — config models
- src/main/java/org/example/model/EntitySchema.java — schema model
- src/main/resources/ui/builder.html — minimal schema builder
- src/main/resources/ui/datasource.html — datasource management UI

Notes
- If DB credentials are wrong at startup, the app still starts the server so you can fix settings via `/ui/datasource`.
- Identifier quoting uses double-quoted UPPERCASE to avoid reserved word/case issues in H2.

Next recommended enhancements
- Add authentication and role-based access to schema and datasource management.
- Add connection pooling (HikariCP) and health checks per datasource.
- Add Swagger UI to visualize `/openapi.json`.
