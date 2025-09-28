# AppBana — Copilot Guide

This guide provides a technical snapshot for AI assistants to understand and interact with the AppBana project.

## 1. Project Overview

- **What it is:** A metadata-driven application platform. Design schemas in a UI, and the backend auto-creates tables and exposes CRUD APIs.
- **Core Stack:** Java 25 (virtual threads), H2 (default), Jackson, SLF4J, HikariCP, Maven.
- **Frontend:** Current UIs are plain HTML/vanilla JS (builder, datasource, swagger). A custom in-house UI “Studio” will supersede the deprecated previous framework plan.

## 2. Current Status

- Fully working MVP backend.
- Minimalist schema builder at `/ui/builder`.
- Datasource management UI at `/ui/datasource`.
- Swagger UI at `/ui/swagger` and OpenAPI spec at `/openapi.json`.
- Refactor initiative in `docs/REFACTOR_PROPOSAL.md`.
- Legacy external-framework plan deprecated; custom lightweight Studio approach is authoritative.

## 3. How to Build and Run

1.  Build backend
    ```bash
    ./mvnw -DskipTests package
    ```
2.  Run backend
    ```bash
    java -jar target/app-bana-1.0-SNAPSHOT-fat.jar
    ```
3.  Options
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

- `ApiServer.java` – lightweight HTTP dispatch
- `SchemaManager.java` – schema persistence + migration
- `JdbcManager.java` – active datasource + pooling
- `ConfigManager.java` / `AppConfig.java` – config load/save
- `OpenApiGenerator.java` – dynamic spec
- UI assets under `src/main/resources/ui/`

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

Refer to `UI_SMOKE.md` for quick manual checks. Add scripted tests as refactor advances:
- Schema CRUD + entity CRUD cycle
- Datasource add/test/activate fallback behavior
- OpenAPI generation stability snapshot

## 12. Assistant Guidance

When generating code or changes:
- Preserve public endpoint signatures
- Avoid introducing heavy frameworks; stay minimal unless value justified
- Keep auth optional; gate new admin endpoints consistently
- Mask secrets in logs; never echo raw passwords/tokens in examples
- Prefer incremental refactors (compile + run smoke after each logical step)

Priority for contributions:
1. Safety & correctness (validation, error mapping)
2. Refactor boundaries (Router, Dialect separation)
3. Workflow + auditing foundations
4. Security (FLS) enforcement completeness
5. Extensibility (plugin surface) without over-abstraction

## 13. Known Gaps / Backlog Seeds

- Pagination/sorting/filtering for list endpoints
- Import/export (schemas & datasources) bundle
- Migration plan enhancements (rename detection, rollback preview)
- Testcontainers DB matrix
- Advanced reporting (PDF) – deferred
- FHIR write & SMART auth – deferred

## 14. De-scoped / Removed

- Prior external framework (and related workspace/SSR build scripts) fully removed
- Framework-specific theming (replaced by generic CSS variable token approach)

## 15. Style & Theming (Current Interim)

- Use CSS variables (tokens) for color/spacing/typography
- Minimal utility classes limited to internal UIs; no user-supplied arbitrary class injection
- Future: theme JSON → CSS variable injection pipeline (planned)

## 16. Quick Smoke (Inline)

```bash
./mvnw -DskipTests package
java -jar target/app-bana-1.0-SNAPSHOT-fat.jar &
curl -s localhost:8080/health
curl -s localhost:8080/ui/datasource/list
curl -s -X POST localhost:8080/schema \
  -H 'Content-Type: application/json' \
  --data '{"name":"smoke","fields":[{"name":"id","type":"long","primaryKey":true,"autoIncrement":true}]}'
curl -s localhost:8080/api/smoke
```

---
All prior framework-specific references were removed/replaced. Use this guide as the single source for assistant context going forward.
