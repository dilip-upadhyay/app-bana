# AppBana — Architect and Developer Guide

This document provides a comprehensive overview of the AppBana application, intended for architects, developers, and product owners who need to understand the system's design, capabilities, and technical direction.

## 1. Product Vision & Architecture

**Vision:** AppBana aims to be the dominant platform for rapidly building secure, complex, and scalable enterprise solutions, with a focus on Healthcare, Logistics, and HR Management.

**Core Architecture:** AppBana is a **metadata-driven platform**. The core principle is to design data schemas (and, evolving now, UI pages) as metadata, which the system then uses to automatically:
1.  Persist the schema.
2.  Create or migrate a backing database table.
3.  Expose a full set of runtime CRUD (Create, Read, Update, Delete) APIs for that schema.
4.  (Emerging) Render UI pages from JSON page metadata using a lightweight custom runtime.

This end-to-end cohesion, from database to UI, is the primary competitive advantage.

**Tech Stack (Current Oct 2025):**
- **Backend:** Java 25 with virtual threads, using the built-in `HttpServer`. Lightweight, framework-free core.
- **Database:** H2 (embedded file-based) by default; any JDBC-compliant database (Postgres, MySQL, etc.) supported.
- **Connection Pooling:** HikariCP for efficient database connection management.
- **JSON Processing:** Jackson (`jackson-databind`).
- **Logging:** SLF4J (simple binding).
- **Build:** Maven (multi-module) with the Shade plugin to create an executable "uber jar" for the service module.
- **Frontend:** Custom TypeScript + Web Components framework "Studio" (Phase A) using Vite; legacy HTML builders still shipped for schemas & datasources.

## 2. Key Features and Capabilities

### 2.1 Dynamic Schema Management
- Visual schema builder (legacy UI) allows defining entity models.
- Preview migration before applying (DDL plan generation).
- Schema persistence and migration history tracking.
- Auto-generates tables and applies safe ALTER statements (rename, add column).

### 2.2 Datasource Management
- Support for multiple datasources (H2, PostgreSQL, MySQL, MariaDB, SQL Server, Oracle, SQLite).
- UI for adding, testing, and managing database connections.
- Switch the active datasource at runtime.
- Connection pooling with HikariCP, configurable per datasource.
- URL Builder to assist with JDBC connection strings (server-side build fallback).

### 2.3 API Generation
- Automatic CRUD API endpoints for each relational schema.
- Advanced query params: pagination, search, projection, sorting, filters, count-only.
- Live OpenAPI 3.0 specification (`/openapi.json`).
- Embedded Swagger UI for API exploration.
- Health and readiness endpoints.

### 2.4 Security & Auditing (Baseline)
- Optional token-based authentication (admin & read-only tokens).
- Baseline CRUD audit logging (single & batch inserts, update, delete) capturing before/after & per-field diff.
- Audit query endpoint (`/audit`) with basic filtering (entity/pk) — roadmap includes export, extended filters.
- HTTPS support via configuration.
- SQL injection protection via prepared statements.

### 2.5 Planned Features (Q4 2025 Roadmap Snapshot)
- Stateful Workflow Engine (definitions, instances, transitions, history).
- Advanced auditing (workflow transitions, CSV export, actor filters, date range).
- Field-Level Security (FLS) engine + runtime redaction.
- Studio Builder MVP (canvas, inspector, undo/redo, local draft persistence).
- Plugin architecture (components, data connectors, actions) + example Signature Pad.
- PWA offline caching & queued write replay (Nov).
- Real-time data via WebSockets / MQTT (Nov) and rule-based alerts (Dec Logistics addendum).
- Healthcare interoperability via FHIR (read-only; Dec).
- Reporting & export engine (tabular CSV/Excel; Nov).

## 3. System Architecture

### 3.1 Current Architecture (Service Layer)
The current codebase is a functional MVP with intentionally minimal abstractions:

- `ApiServer.java`: HTTP routing & handlers (schema CRUD, entity CRUD, health, audit).
- `SchemaManager.java`: Schema persistence & migration planning/execution.
- `JdbcManager.java`: Database connection acquisition & HikariCP lifecycle (active datasource centric).
- `ConfigManager.java`: Multi-datasource configuration load/save + test result persistence.
- `OpenApiGenerator.java`: Generates the OpenAPI specification (CRUD endpoints only).
- `AuditLog` integration (inside handlers) writes rows to `appbana_audit` post-commit; failures are non-fatal.

### 3.2 Emerging Frontend (Studio) Architecture (Phase A)
"Studio" comprises two cooperating runtimes:
- **Builder (design-time)** — Will offer a structured tree editor (Phase B) for page metadata.
- **Runtime (render-time)** — Walks page metadata (ComponentNode graph) to instantiate registered Web Components.

Current state (Oct 1, 2025):
- Implemented: `BaseElement` abstraction (shadow DOM + minimal state), dynamic component registry, core components (Container / Text / Button), demo metadata file, unknown component fallback.
- Pending (Phase A exit): recursive renderer implementation, first renderer Vitest test, packaging `/ui/studio` entry.
- Future phases introduce bindings, actions, expressions, theming, versioning, and plugin loading.

### 3.3 Planned Service Refactoring
Targeted after Phase A renderer completion:
- Clear package layering (api, schema, db, audit, security, config, openapi, util).
- Dialect abstraction for multi-DB nuance (identifier quoting, DDL variant generation, pagination syntax).
- Request/response normalization (typed wrappers, centralized error mapping).
- FLS redaction & write enforcement hooks.
- Structured audit service with queue or batch capability (optional reliability enhancement).

## 4. Studio UI Architecture (Replaces Prior Angular Plan)

### 4.1 Component Model
- Web Components (Custom Elements) registered via a lightweight registry; lazy-import ensures minimal startup cost.
- Component metadata (future): each component will declare a props schema enabling auto-generated inspector forms.
- Registry will evolve into a plugin boundary allowing external script modules to register new components.

### 4.2 Rendering Pipeline (Planned Implementation)
1. Load page metadata JSON (includes node collection).
2. Build in-memory index (id → node).
3. Create root host element and recursively instantiate child components.
4. Apply static props; future: evaluate bindings inside a sandbox.
5. Insert unknown component placeholder for unregistered `type` values (forward compatibility & plugin resilience).

### 4.3 Builder Canvas (Phase B Scope Outline)
- Tree store normalizing nodes for O(1) access.
- Selection model (single initially) + keyboard shortcuts (duplicate, delete, search palette, inline edit).
- Undo/redo via operation log (bounded stack, 100 entries default).
- Property inspector (auto-generated from component props schema) with immediate re-render.
- Local draft persistence (`localStorage`) prior to server design endpoints (Phase C).

### 4.4 Future Frontend Concerns
- Expression sandbox: deterministic evaluation (whitelisted functions, no global object leaks).
- Theme token injection: compile theme JSON to scoped CSS variables.
- Realtime binding: subscription adapters pushing updates to components.
- Versioning/publish: semantic version snapshots & diff preview before runtime activation.
- Plugin isolation: optional iframe/realm or capability-based access restrictions.

### 4.5 Historical Note (Angular Plan Deprecated)
A prior plan targeting an Angular 21 + Nx workspace has been **superseded** by the custom Studio approach for reduced runtime weight and tighter platform alignment. Remaining Angular references in historical documents should be interpreted as legacy and will be phased out. Epics have been re-mapped to Studio phases (see roadmap docs).

## 5. Vertical-Specific Features (Strategic Targets)
### 5.1 Healthcare
- HIPAA-compliant audit trails
- Field-level security for PHI
- FHIR connector for interoperability
- Patient History Timeline component

### 5.2 Logistics
- Real-time operations via WebSockets/MQTT
- PWA with offline support for field operations
- Barcode/QR scanner component
- Map components with geo-tracking
- Exception rules and alerts
- Multi-tenant data partitioning

### 5.3 HR Management
- Multi-step approval workflows
- Relationship-based permissions
- Report generation and export
- Document management

## 6. Development, Testing, and Deployment

### 6.1 Building and Running
- Java backend:
  ```bash
  ./mvnw -DskipTests package
  java -jar target/app-bana-1.0-SNAPSHOT-fat.jar
  ```
- Angular UI (future):
  ```bash
  ./build.sh --clean
  ./run.sh --port 4000 --open
  ```

### 6.2 Configuration Options
- Port: `-Dappbana.port=9090` or `APPBANA_PORT=9090`
- Config file: `APPBANA_CONFIG` or `-Dappbana.config=...`
- HTTPS: `APPBANA_HTTPS_ENABLED=true`, etc.
- Authentication: `APPBANA_ADMIN_TOKEN`, `APPBANA_READ_TOKEN`

### 6.3 Testing
- Smoke test available in `UI_SMOKE.md`
- Future: Unit tests and integration tests with Testcontainers

## 7. Security Considerations

- Token-based authentication should be enabled in production.
- Passwords are never exposed in API responses.
- HTTPS is recommended for production deployments.
- Audit logging will track all data access and changes.
- Field-Level Security will control granular data access.

## 8. Future Directions and Extensions (Updated Emphasis)
- Workflow Engine integration with UI actions & audit trails.
- Field-Level Security: runtime redaction + design-time preview mode.
- Plugin marketplace (signed manifests, integrity verification) — Dec prototype.
- FHIR connector (Patient, Observation, Encounter read-only) — Dec.
- Realtime connectors (WebSocket, MQTT) — Nov.
- Reporting engine (CSV/Excel streaming) — Nov.
- PWA offline cache + queued mutation replay — Nov.
- Design versioning & rollback — Dec.
- Extended audit export & filtering (cursor pagination, actor filter) — Oct/Nov.

## 9. Documentation and Resources (Updated)
- `README.md`: Main project documentation & current Phase A progress.
- `USER_GUIDE.md`: User onboarding and feature walkthrough (legacy builder + datasource UI).
- `UI_Development_Plan.md`: Deep Studio plan (authoritative for phases & risks).
- `.github/COPILOT_GUIDE.md`: Assistant-facing snapshot (condensed status + actionable tasks).
- `AUDIT_LOGGING.md`: Baseline CRUD audit logging spec & roadmap.
- `TODO.md`: Executable backlog with phase-linked checkboxes.
- `PRODUCT_PLAN.md` / `Product_AppBana.md`: High-level roadmap & vertical strategy.
- `OCT_2025_EPICS_STORIES.md`: Epic/story breakdown (Angular references now historical; mapping updated in notes).
