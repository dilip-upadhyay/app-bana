# AppBana — Copilot Guide

<!-- Updated 2025-10-01: Phase A progress + next steps -->
**Recent Enhancements (Q4 2025 incremental)**
- Schema edit (non‑PK rename with `existingName` + auto DDL)
- Migration preview & history
- Schema delete (+ optional table drop)
- Schema summaries endpoint
- Field reorder (display only)
- Duplicate field validation
- Inline JSON import/export
- Helper script `./run-ui.sh`
- Baseline CRUD Audit Logging (single + batch insert rows, update, delete) with per-field diff
- Studio: BaseElement, component registry, core components (Container/Text/Button), demo metadata, unknown component placeholder

This guide provides a technical snapshot for AI assistants. Keep responses aligned with the authoritative deeper docs (`docs/UI_Development_Plan.md`, `docs/PRODUCT_PLAN.md`, `docs/TODO.md`).

## 1. Project Overview

- **What it is:** A metadata-driven application platform. Design schemas in a UI, and the backend auto-creates tables and exposes CRUD APIs.
- **Core Stack:** Java 25 (LTS, virtual threads if available), H2 (default), Jackson, SLF4J, HikariCP, Maven multi-module.
- **Modules:**
  - `app-bana-ui` (groupId: `com.appbana`) – Contains the frontend application source (`src/`) and packages the final static UI resources for the backend.
  - `app-bana-service` – backend service (depends on ui module; shaded runnable JAR).
- **Frontend:** A custom, lightweight UI framework ("Studio") is being developed using TypeScript and native Web Components. It uses `vite` for development and builds. `lit` is used as a temporary helper, with the long-term goal of being replaced by a minimal internal `BaseElement` core.

## 2. Current Status (Oct 1, 2025)
- Backend: stable MVP; audit baseline shipped.
- Frontend (Studio): Phase A partially complete (renderer & test pending).
- Legacy HTML UIs (builder/datasource/swagger) still active; Angular plan deprecated.
- NOTE: Earlier references to `docs/REFACTOR_PROPOSAL.md` are legacy — file removed; refactor intent summarized in section 9.

## 3. How to Build and Run

### Backend
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

### Frontend (Development)
Existing manual steps remain valid. Prefer helper:
```bash
./run-ui.sh            # dev (vite)
./run-ui.sh build      # production build (dist/)
./run-ui.sh preview    # serve built build
USE_SYSTEM_NODE=1 UI_PORT=5190 ./run-ui.sh dev
```

## 4. Key Endpoints (Augmented)
- /schema (GET list, POST save/preview)
- /schema/summaries (GET name + datasource list)
- /schema/{name} (GET detail)
- /schema/{name}/migrations (GET executed DDL history)
- /schema/{name} (DELETE with optional dropTable flag)
- /api/{entity} CRUD
- /api/endpoints (enumerates dynamic CRUD)
- /openapi.json (excludes admin-only migration/delete endpoints by design)

## 5–9 (Configuration, Datasources, Schema Format, Structure, Refactor Trajectory)

- Config file (default): `data/appbana-config.json` (override via `APPBANA_CONFIG` or `-Dappbana.config`)
- Env/System overrides: JDBC URL/credentials, tokens, HTTPS flags
- Auth headers accepted: `X-AppBana-Token` or `Authorization: Bearer <token>`

- Multi-datasource JSON config with active selection
- URL Builder (types: h2/postgres/mysql/mariadb/mssql/oracle/sqlite)
- Test / Activate / Delete endpoints
- HikariCP pool settings per datasource (defaults documented in README)

Field rename during edit:
```json
{
  "name": "email_address",
  "existingName": "email",
  "type": "string",
  "length": 255
}
```
Backend applies `ALTER TABLE ... RENAME COLUMN` if old column exists and new does not.

- `app-bana-service/src/main/java/com/appbana/ApiServer.java` (new delete + migrations + summaries routes)
- `app-bana-service/src/main/java/com/appbana/SchemaManager.java` (generateMigrationPlan, listMigrations, deleteSchema, listSchemaSummaries)
- `run-ui.sh` (root) helper for frontend lifecycle.

## 9. Refactor Trajectory (High Level)

- Introduce Router & Request/Response wrappers
- Extract Dialect strategy (H2, Postgres, etc.)
- Split schema logic (SchemaService + MigrationPlanner)
- Stronger validation / type coercion utilities
- Add automated tests (unit + integration with alternate DB)

## 10. Q4 2025 Development Focus (Rebased – Custom UI Core)
### Progress Snapshot vs Goals
| Item | Status | Notes |
|------|--------|-------|
| BaseElement core | ✅ Done | Minimal lifecycle present |
| Component registry | ✅ Done | Dynamic import bootstrap |
| Core components (container/text/button) | ✅ Done | Registered lazily |
| Unknown component placeholder | ✅ Done | Graceful fallback ensures forward compatibility |
| Demo metadata JSON | ✅ Done | `src/demo/demo-page.json` includes unknown type test |
| Recursive runtime renderer | ✅ Done | Implemented in `runtime/renderer/Renderer.ts` + facade `app-renderer.ts` |
| Vitest renderer test | ✅ Done | Demo + Unknown fallback + Renderer tests pass |
| /ui/studio packaging (HTML + bootstrap) | ✅ Done | `studio.html` fallback served; dist preferred |
| Builder canvas skeleton | ☐ Pending | Begins Phase B after renderer/test |
| Audit UI integration | ☐ Pending | Depends on audit export endpoint |

### Immediate Next Action Checklist (Assistant / Dev)
1. Implement recursive renderer in `app-bana-ui/src/app-renderer.ts`:
   - Load demo JSON (import) & index nodes
   - DFS build DOM: create element per node type via registry, apply simple props (e.g., text -> attribute)
   - Append children
2. Create `studio.html` (or `index-studio.html`) + bootstrap script that calls renderer (copy to service resources at build).
3. Update build pipeline to copy new studio assets into `app-bana-service` JAR (parallel to existing `/ui/*`).
4. Add Vitest test: import demo metadata, run renderer into a detached DOM container (jsdom), assert:
   - Container exists
   - Text node content matches demo JSON
   - Unknown component placeholder rendered (e.g., tag `studio-unknown` present)
5. Mark Phase A tasks complete in `TODO.md` and this guide.
6. THEN begin Builder canvas store + selection model (Phase B) — not before.

## 10.1 Studio Status (Snapshot)
This is a concise assistant-facing mirror of the deeper `docs/UI_Development_Plan.md`.

Current Phase: A (Foundation – in progress)

| Area | Status | Notes |
|------|--------|-------|
| BaseElement | Seeded | Minimal lifecycle + render hook |
| Registry | Seeded | No plugin discovery yet |
| Renderer | Minimal | Container/Text/Button only |
| Metadata TS models | Partial | Extended shapes (Page/Theme/Nav) pending |
| Page persistence endpoints | Missing | Planned Phase C design/runtime split |
| Builder canvas/inspector | Missing | Phase B scope |
| Bindings/actions | Missing | Phase C/D |
| Theme tokens | Missing | Phase D |
| Expression sandbox | Missing | Phase D (security gate) |
| Plugin boundary | Planned | Phase E |
| Tests (UI) | Missing | Vitest harness part of Phase A exit |

Immediate Phase A Completion Tasks (must be green before advancing):
1. Add `models/metadata.ts` (PageMeta, ComponentNode union, ThemeMeta, NavigationMeta, Binding, Action). ✅
2. Registry bootstrap autoload built-ins. ✅
3. `demo-page.json` + load in `app-renderer.ts`. ✅
4. Recursive renderer (children traversal + prop mapping). ✅
5. Vitest + first renderer test. ✅
6. Unknown component placeholder. ✅
7. Copy demo page into packaged JAR for `/ui/studio`. ✅
8. Update README + this guide with component contribution workflow. ☐ (outstanding)

Exit Criterion Phase A: `/ui/studio` renders demo JSON page + Vitest suite passes.

See `docs/UI_Development_Plan.md` for the authoritative extended plan (phases, risks, metrics).

## 11. Testing & Verification

Quick smoke:
```bash
./app-bana-service/mvnw -q -DskipTests package
java -jar app-bana-service/target/app-bana-1.0-SNAPSHOT-fat.jar &
sleep 2
curl -s localhost:8080/health
```

Migration preview:
```bash
curl -s -X POST 'http://localhost:8080/schema?preview=true' \
 -H 'Content-Type: application/json' \
 --data '{"name":"demo","fields":[{"name":"id","type":"long","primaryKey":true,"autoIncrement":true}]}'
```
Migration history:
```bash
curl -s http://localhost:8080/schema/demo/migrations
```
Delete (keep table):
```bash
curl -X DELETE http://localhost:8080/schema/demo
```
Delete (drop table):
```bash
curl -X DELETE 'http://localhost:8080/schema/demo?dropTable=true'
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
Updated for multi-module + groupId change to `com.appbana` and Java 25.

## 17. Studio Domain Model & Metadata Roadmap (Notes)
No functional change yet; relational path extended with edit/delete lifecycle. Non-relational kinds still UI-only modeling; backend ignores `modelKind` != relational.

### 17.1 Hierarchy
Project
  └─ App
       ├─ Schemas (data models powering CRUD APIs)
       ├─ Pages (metadata-driven UI trees)
       ├─ Navigation (menus / route map)
       ├─ Themes (token sets)
       ├─ DataSources (optional overrides)
       ├─ Workflows (future)
       └─ Settings (auth, default page, feature flags)

### 17.2 Core Metadata Shapes (draft)
- Project: { id, name, description, apps: [] }
- App: { id, projectId, code, name, version, defaultPageId, activeThemeId, settings }
- Schema: (existing) + projectId, appId extensions
- Page: { id, appId, path, name, type, rootId, nodes: ComponentNode[], layout }
- ComponentNode: { id, type, props, children[], bindings, events, security, style }
- Navigation: { items: NavItem[] } (nested structure)
- Theme: { id, name, tokens: { colorPrimary, colorBg, spacing[], typography, radii, shadows } }
- Binding: { kind: 'schemaField'|'apiResult'|'formState'|'expression'|'globalState'|'pageParam', ref?, expr?, transform? }
- Action: { type, config, conditions?[] }

### 17.3 Proposed File Layout (design-time)
/design/
  project.json
  apps/<appCode>/
    app.json
    schemas/*.schema.json
    pages/*.page.json
    navigation.json
    themes/*.theme.json
    datasources.json
    state-model.json
    workflows/*.workflow.json (future)

### 17.4 New Backend Endpoint Plan (admin unless noted)
Design-time:
- POST /design/project | GET /design/projects | GET /design/project/{id}
- POST /design/app | GET /design/apps?projectId= | GET /design/app/{id}
- POST /design/app/{id}/publish
- POST /design/schema (extends existing) | GET /design/app/{id}/schemas
- POST /design/page | GET /design/app/{id}/pages | GET /design/page/{id}
- POST /design/theme | GET /design/app/{id}/themes
- POST /design/navigation | GET /design/app/{id}/navigation

Runtime (read or public):
- GET /runtime/app/{code}/manifest
- GET /runtime/app/{code}/page/{path}
- GET /runtime/app/{code}/page-id/{id}
- GET /runtime/app/{code}/theme/{id}

### 17.5 Phased Frontend Delivery (incremental)
Phase A (Foundation):
- TypeScript metadata interfaces (models/metadata.ts)
- Component registry skeleton (core/registry.ts)
- Minimal runtime walker (runtime/renderer/) for Container/Text/Button

Phase B (Builder MVP):
- Canvas: add/remove/move nodes (in-memory)
- Property inspector (schema-driven for props & styles)
- Local draft persistence (localStorage) + basic import/export

Phase C (Runtime MVP):
- Load page JSON
- Instantiate components
- Basic bindings: static + form state
- Simple actions: navigate, setState

Phase D (Enhancements):
- Expressions, validation, event chains
- Navigation + theme token application

Phase E (Advanced):
- Versioning & publish snapshots
- Plugin system (external custom elements)
- Real-time datasources & workflow hooks

### 17.6 Immediate TODO (for agent execution)
1. Add `src/models/metadata.ts` with interfaces outlined above.
2. Add `src/core/registry.ts` with register/get patterns.
3. Add `src/runtime/renderer/Renderer.ts` basic walker (Container/Text/Button only).
4. Wire demo page JSON & render via new runtime path (temporary dev injection).
5. Keep existing lit-based root; progressively replace.

### 17.7 Conventions
- All metadata JSON objects include `metaVersion:1` (future migration hook).
- IDs: prefix by type (page-, node-, app-, proj-).
- Avoid circular references; use IDs & lookup maps.
- Expression sandbox to be introduced before enabling user-defined expressions.

### 17.8 Non-Goals (Now)
- Full designer drag & drop (later)
- Workflow execution engine (separate track)
- Real-time streaming connectors

### 17.9 Risks
- Scope creep → enforce phase checklists.
- Security (eval) → never raw eval; sandbox later.
- Performance for large trees → store normalized internally.

### 17.10 Success (Short-Term)
Display a page with 3 component nodes rendered from static metadata JSON using custom registry + BaseElement components.

---

## 17.11 Datasource Strategy (Proposed Enhancement)
Schemas must explicitly associate with a datasource so multi‑DB apps (or split read/write, operational vs analytical stores) are supported.

#### Goals
- Allow multiple datasources per Project / App.
- Each Schema can target a specific datasource; default inheritance keeps authoring simple.
- Support environment overrides (dev/test/prod) without duplicating logical datasource definitions.
- Keep migration logic confined to managed datasources (flag to opt out if an external DBA controls the schema).

#### Metadata Additions
DatasourceMeta (design-time JSON):
```
{
  "id": "ds-primary",              // unique within project
  "scope": "project" | "app",      // project = shared; app = isolated
  "name": "Primary DB",
  "type": "h2" | "postgres" | "mysql" | "mariadb" | "mssql" | "oracle" | "sqlite" | "custom",
  "driver": "org.postgresql.Driver",
  "connection": {
    "jdbcUrl": "jdbc:postgresql://localhost:5432/appbana" | null,
    "parts": { "host":"localhost", "port":5432, "database":"appbana" } // optional structured form
  },
  "auth": { "username": "app", "passwordRef": "secret://vault/appbana" },
  "pool": { "maxPoolSize": 10, "minIdle": 2 },
  "defaultForApp": true,            // exactly one per app *may* be default
  "managed": true,                  // if false: no automatic migrations
  "env": {                          // environment overrides (optional)
    "dev":   { "connection": { "jdbcUrl": "jdbc:postgresql://localhost:5432/appbana_dev" } },
    "test":  { "connection": { "jdbcUrl": "jdbc:postgresql://localhost:5432/appbana_test" } },
    "prod":  { "connection": { "jdbcUrl": "jdbc:postgresql://prod:5432/appbana" } }
  },
  "rolesAllowed": ["admin"]        // restrict who can bind schemas (optional)
}
```

Schema extension:
```
{
  ...existing schema fields...,
  "datasourceId": "ds-primary",   // optional; if omitted resolves to app default
  "migrationPolicy": "managed" | "external"  // overrides datasource.managed when needed
}
```

#### Resolution Algorithm (Runtime)
1. Page / API request loads schema metadata.
2. If schema.datasourceId present, use that datasource; else use app.defaultDatasourceId.
3. Merge base + environment override (based on APPBANA_ENV or system property) to build final JDBC settings.
4. Pool keyed by (datasourceId, env) to isolate configs.

#### Backend Endpoint Additions
Design-time (admin):
- POST /design/datasource            (create/update)
- GET  /design/project/{id}/datasources
- GET  /design/app/{id}/datasources  (includes inherited project-scoped + app-scoped)
- POST /design/app/{id}/datasource/set-default  { datasourceId }

Runtime (read):
- GET /runtime/app/{code}/datasources              (list minimal info)
- GET /runtime/app/{code}/datasource/{id}/health   (ping; masks secrets)

#### Migration Considerations
- New schema with managed=true → included in migration plan for its datasource only.
- Changing a schema's datasourceId (move) is a special operation:
  - Phase 1: disallow (return 409) unless "allowMove": true flag is passed AND schema has no data (quick row count check) OR policy is external.
  - Future: implement copy+recreate path with optional data transfer.
- Deleting a datasource that has bound schemas → reject unless force=true and all bound schemas are external or moved.

#### Environment Override Strategy
- At runtime determine env via (precedence): system property `appbana.env` → env var `APPBANA_ENV` → default `dev`.
- When override exists, shallow merge: base + env[envName].
- Password resolution: if passwordRef starts with `secret://` delegate to secret provider (future hook) else treat as literal.

#### Security & Access
- `rolesAllowed` gate which authenticated roles can assign new schemas to the datasource via builder UI.
- Runtime always enforces schema→datasource binding; no client override.

#### Updated Success Criterion (Short-Term)
Render two schemas each bound to different datasources, verify CRUD works independently, and switching app default does not affect explicitly bound schema.

#### Immediate Additions to Phase A TODO
6. Extend `metadata.ts` with `DatasourceMeta` + update `PageMeta` optional `datasourceOverrides?` (future multi-source widgets).
7. Add placeholder resolution util (JDBC URL with ${VAR} expansion from env).

---
End of Section 17 additions.

## 17.12 Non-Relational Datasource Runtime Plan (DECISIONS)

Phase decision scope = Documentation + Frontend modeling only. Backend persists only relational schemas for now; non-relational support is additive and gated.

### A. Categories & Schema Mapping
| Category    | Representation (Phase Now)           | Future Runtime Strategy |
|-------------|--------------------------------------|-------------------------|
| relational  | Existing EntitySchema (modelKind=relational) | Full CRUD + migration |
| nosql       | DocumentSchema (modelKind=document)  | Validation + UI forms; no DDL |
| rest        | ResourceSchema (modelKind=apiResource) | Proxy + response shaping |
| soap        | ResourceSchema (apiResource, wsdl=true) | WSDL introspection cache |
| mcp         | ResourceSchema (apiResource, protocol=mcp) | Adapter dispatch |

### B. Discriminator
`modelKind` field added to schema objects:
- `relational` (default if missing)
- `document`
- `apiResource`

Relational-only paths (current backend) ignore non-relational schemas; UI builder will prevent saving unsupported kinds until backend phase is enabled.

### C. Separate Metadata Shapes (Frontend Only Now)
- RelationalSchema: fields[] identical to existing EntitySchema fields.
- DocumentSchema: optional fields[] (acts as validation / form blueprint). If omitted → schemaless (UI infers via sample documents later).
- ResourceSchema: defines operations array.
  ```json
  {
    "name": "ordersApi",
    "modelKind": "apiResource",
    "datasourceName": "ds-orders-rest",
    "basePath": "/orders",
    "operations": [
      { "name":"list", "method":"GET", "path":"/", "params":[{"in":"query","name":"status"}], "responseType":"array" },
      { "name":"create", "method":"POST", "path":"/", "bodySchemaRef":"orderCreate" }
    ]
  }
  ```

### D. Adapter Interface (Phase Later)
Planned TypeScript interface:
```ts
export interface DatasourceAdapter<TMeta=any> {
  category: 'relational'|'nosql'|'rest'|'soap'|'mcp';
  test(meta: TMeta): Promise<{ ok: boolean; message?: string; details?: any }>;
  fetch?(opts: AdapterFetchOptions): Promise<any>;
  mutate?(opts: AdapterMutateOptions): Promise<any>;
  migrate?(schema: RelationalSchema): Promise<MigrationResult>; // relational only
}
```
Adapter registry will allow dynamic selection: `(ds.category) -> adapter`.

### E. Runtime Enforcement (Deferred)
Backend guard (future): reject save of non-relational schema unless feature flag `appbana.experimental.nonrel` enabled.

### F. UI Builder Changes (Incremental)
1. Schema creation dialog includes datasource picker filtered to `{ category: 'relational' }` initially.
2. Future toggle: "Advanced" reveals non-relational kinds (disabled tooltip until backend supports).
3. Datasource form switches fields by category:
   - relational: JDBC builder
   - nosql: connectionString + database + engine select
   - rest/soap: baseUrl/wsdlUrl + auth config
   - mcp: endpoint + protocol notes
4. ModelKind stored in schema JSON for future migration; omitted => relational.

### G. Migration & Naming Implications
- Non-relational schemas never appear in `appbana_migrations`.
- OpenAPI generation (current) uses only relational schemas; will later include `apiResource` operations in a merged spec view.

### H. TODO Additions
- [ ] Add `modelKind` to Java `EntitySchema` (DONE)
- [ ] Frontend: introduce `schema.ts` with discriminated union
- [ ] Add adapter interface scaffold in `runtime/datasource`
- [ ] Guard in backend (future flag) before accepting non-relational modelKind.
