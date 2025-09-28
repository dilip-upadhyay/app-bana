# AppBana — Copilot Guide

This guide provides a technical snapshot for AI assistants to understand and interact with the AppBana project.

## 1. Project Overview

- **What it is:** A metadata-driven application platform. Design schemas in a UI, and the backend auto-creates tables and exposes CRUD APIs.
- **Core Stack:** Java 21 (LTS, virtual threads if available), H2 (default), Jackson, SLF4J, HikariCP, Maven multi-module.
- **Modules:**
  - `app-bana-ui` (groupId: `com.appbana`) – packs static UI resources.
  - `app-bana-service` – backend service (depends on ui module; shaded runnable JAR).
- **Frontend:** Current UIs are plain HTML/vanilla JS (builder, datasource, swagger). A custom in-house UI “Studio” will supersede the deprecated previous framework plan.

## 2. Current Status

- Fully working MVP backend.
- Minimalist schema builder at `/ui/builder`.
- Datasource management UI at `/ui/datasource`.
- Swagger UI at `/ui/swagger` and OpenAPI spec at `/openapi.json`.
- Refactor initiative in `docs/REFACTOR_PROPOSAL.md`.
- Legacy external-framework plan deprecated; custom lightweight Studio approach is authoritative.

## 3. How to Build and Run

1. Build (root is parent POM; wrapper lives in service module):
   ```bash
   ./app-bana-service/mvnw clean package -DskipTests
   ```
2. Run backend (fat JAR produced by service module):
   ```bash
   java -jar app-bana-service/target/app-bana-1.0-SNAPSHOT-fat.jar
   ```
3. Options
   - Custom port: `-Dappbana.port=9090` or env `APPBANA_PORT=9090`
   - Tokens (auth): `export APPBANA_ADMIN_TOKEN=admin123 APPBANA_READ_TOKEN=read123`
   - HTTPS env vars: `APPBANA_HTTPS_ENABLED=true APPBANA_KEYSTORE_PATH=certs/keystore.p12 ...`

## 4. Key Endpoints

- **UI:** `/ui/builder`, `/ui/datasource`, `/ui/swagger`
- **API:** `/schema`, `/api/{entity}`, `/openapi.json`, `/health`, `/ready`, datasource endpoints under `/ui/datasource/*`

## 5. Configuration

- Config file (default): `data/appbana-config.json` (override via `APPBANA_CONFIG` or `-Dappbana.config`)
- Env/System overrides: JDBC URL/credentials, tokens, HTTPS flags
- Auth headers accepted: `X-AppBana-Token` or `Authorization: Bearer <token>`

## 6. Datasource Management (Summary)

- Multi-datasource JSON config with active selection
- URL Builder (types: h2/postgres/mysql/mariadb/mssql/oracle/sqlite)
- Test / Activate / Delete endpoints
- HikariCP pool settings per datasource (defaults documented in README)

## 7. Schema Format (Example)

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

## 8. Codebase Structure (Key Files)

- `app-bana-service/src/main/java/org/example/ApiServer.java` – lightweight HTTP dispatch
- `SchemaManager.java` – schema persistence + migration
- `JdbcManager.java` – active datasource + pooling
- `ConfigManager.java` / `AppConfig.java` – config load/save
- `OpenApiGenerator.java` – dynamic spec
- UI assets packaged now from `app-bana-ui/src/main/resources/ui/`

## 9. Refactor Trajectory (High Level)

- Introduce Router & Request/Response wrappers
- Extract Dialect strategy (H2, Postgres, etc.)
- Split schema logic (SchemaService + MigrationPlanner)
- Stronger validation / type coercion utilities
- Add automated tests (unit + integration with alternate DB)

## 10. Q4 2025 Development Focus (Rebased – No Angular)

### October 2025 (Platform Foundation)
- Custom UI Framework Foundation (lightweight component/render core, schema-driven runtime stubs)
- Stateful Workflow Engine MVP (definitions, instances, transitions, history)
- Advanced Auditing + Field-Level Security (read redaction + write enforcement)
- Plugin & Extension Boundary (component + data connector registry via simple JS module contract / Web Components baseline)

### November 2025 (Vertical Enablement)
- Offline/PWA baseline (asset + last schema cache; queued write replay)
- Real-time channels (WebSocket + MQTT connector prototypes) and event distribution layer
- Barcode/QR capture component (camera API) feeding form fields / actions
- Reporting designer MVP (tabular config → CSV/Excel export service)
- Multi-actor workflow enhancements & relationship permissions

### December 2025 (Healthcare + Governance)
- FHIR (read-only) connector + mapping helpers
- Patient Timeline component (events over time, virtualization)
- Design versioning & rollback metadata
- Marketplace/Registry stub (enable first-party plugins securely)
- Document Store (upload/view) + audited access

## 11. Testing & Verification

Quick smoke:
```bash
./app-bana-service/mvnw -q -DskipTests package
java -jar app-bana-service/target/app-bana-1.0-SNAPSHOT-fat.jar &
sleep 2
curl -s localhost:8080/health
```

## 12. Assistant Guidance

- Use groupId `com.appbana` for new modules/dependencies.
- Keep endpoints stable; avoid heavyweight frameworks.
- Favor incremental, tested refactors.

## 13. Known Gaps / Backlog Seeds

(Refer to README / TODO)

## 14. De-scoped / Removed

- Prior external framework tooling & build pipeline.

## 15. Style & Theming

- CSS variables (tokens) approach; future: theme JSON → CSS pipeline.

## 16. Extended Smoke Example

```bash
./app-bana-service/mvnw clean package -DskipTests
java -jar app-bana-service/target/app-bana-1.0-SNAPSHOT-fat.jar &
PID=$!
sleep 3
curl -s localhost:8080/health
curl -s -X POST localhost:8080/schema -H 'Content-Type: application/json' \
  --data '{"name":"smoke","fields":[{"name":"id","type":"long","primaryKey":true,"autoIncrement":true}]}'
curl -s localhost:8080/api/smoke
kill $PID
```

---
Updated for multi-module + groupId change to `com.appbana` and Java 21.
