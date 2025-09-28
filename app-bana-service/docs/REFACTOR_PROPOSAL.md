# AppBana Refactoring Proposal

Author: Technical Architecture
Date: 2025-09-19
Scope: Refactor the Java codebase for maintainability, correctness across databases, security, and operability, while keeping the MVP fast and simple.

Executive summary
- Objective: Evolve the current MVP into a maintainable, testable, and secure service with a small, clear architecture. Keep zero-frills runtime, but remove ad-hoc coupling and H2-centric assumptions while retaining portability.
- Approach: Incremental, low-risk refactors with green builds at every step. Start by standardizing request/response handling, isolating DB dialect logic, and hardening error paths. Then add tests (unit + Testcontainers), CI quality gates, and optional adapters.
- Outcomes:
  - Clear module boundaries (api, schema, db, config, openapi, util)
  - Dialect-safe schema/migration engine
  - Consistent HTTP error model and request parsing
  - Validated DTOs and stricter typing
  - Automated tests with coverage and DB matrix
  - CI/CD improvements and release artifacts

Guiding principles
- Keep it simple: No heavy frameworks unless value is clear and incremental.
- Separation of concerns: Isolate I/O (HTTP), domain (schema), and infrastructure (DB/JDBC, config).
- Defensive coding: Validate inputs, sanitize outputs, fail fast with clear errors.
- Testability first: Small, deterministic units; integration tests for key paths.
- Backward compatibility: Don’t break APIs silently; add opt-in changes behind flags when needed.

Current state assessment (as-is)
- HTTP: com.sun.net.httpserver used directly; handlers contain routing, parsing, business logic, and error handling interleaved.
- Schema engine: Works across H2/Postgres with recent fixes; dialect-specific DDL logic is embedded in SchemaManager and JdbcManager.
- DB access: Centralized in JdbcManager with HikariCP; pool rebuilt lazily; driver inference implemented.
- Config: JSON file with env/system overrides; multi-DS support; normalization logic exists.
- OpenAPI: Generated programmatically; basic coverage.
- Security: No auth; admin endpoints exposed.
- Observability: Basic logging (slf4j-simple); no metrics/health.
- Testing: No unit/integration tests included.

Target architecture (lightweight)
- Packages
  - com.appbana.app: Main, wiring/bootstrap
  - com.appbana.api: Router, middleware, handlers (SchemaHandler, EntityHandler, DatasourceHandler, OpenApiHandler)
  - com.appbana.schema: SchemaService, MigrationPlanner, OpenApiGenerator (moved), model
  - com.appbana.db: JdbcManager, Dialect (strategy), SqlBuilder, RowMapper utils
  - com.appbana.config: AppConfig, DatasourceConfig, ConfigManager
  - com.appbana.util: Json (ObjectMapper), Http helpers, Validation utils
- Cross-cutting
  - Error model: ApiError {timestamp, path, status, code, message, details?}
  - Validation: DTO validation using Jakarta Validation (optional), plus explicit checks
  - Logging: Request ID correlation, concise structured logs (MDC)
  - Security: Minimal auth gate (optional feature flag) for admin routes
  - Observability: /health (liveness) and /ready (readiness), basic metrics counters

Key design changes
1) HTTP layer modernization (without frameworks)
- Introduce a tiny Router that maps (method, path pattern) → Handler function with Request/Response wrappers.
- Centralize error handling and JSON serialization in middleware; consistent JSON errors; set CORS if needed.
- Thin handlers delegate to services (schema, entity, datasource) with no JDBC code in handlers.

2) Dialect strategy for DDL/SQL
- Create interface Dialect with methods like:
  - createMetaTables(), upsertSchema(), sqlType(Field), alterColumnTypeSql(table, column, type), quoteIdent(name)
- Implement dialects: H2Dialect, PostgresDialect, MySqlDialect, MariaDbDialect, SqlServerDialect, OracleDialect, SqliteDialect.
- JdbcManager selects Dialect once per active datasource signature and exposes Dialect to SchemaService.

3) Schema engine split
- SchemaService: saveSchema, loadSchema, listSchemas, generatePlan, applyPlan.
- MigrationPlanner: produce DDL (using Dialect) from desired/current state.
- Keep recordMigration; add versioning field for future evolutions (non-breaking now).

4) Stronger typing and validation
- EntitySchema.Field.type → enum FieldType { STRING, INT, LONG, BOOLEAN, TIMESTAMP, TEXT }
- Validation annotations (optional) + programmatic checks; centralize regex/length/min/max validation.
- Coercion consolidated in a TypeCoercion utility, decoupled from handlers.

5) Data access hygiene
- Row mapping in a small utility; always use PreparedStatement; avoid ad-hoc conversions.
- Add a simple QueryBuilder for CRUD based on schema; no ORM.

6) Configuration consolidation
- Immutable runtime config view; hide env/system lookups behind ConfigManager.
- Mask secrets in logs; make timeouts and pool defaults constants.

7) OpenAPI improvements
- Add schemas and examples for CRUD; include pagination query params once implemented.
- Keep embedded Swagger UI; optionally bundle assets locally to remove CDN dependency.

8) Security & hardening (feature-flagged)
- Option A: HTTP Basic token for admin routes (/schema, /ui/datasource/*), configured in config.
- Option B: Reverse proxy is recommended; document.
- Always hide stack traces from clients; log them server-side.

9) Observability
- /health: returns up if process is alive.
- /ready: verifies can get a DB connection; returns details per datasource (without secrets).
- Add lightweight request timing logs; basic counters (requests, errors) — optional.

10) Testing strategy
- Unit tests for: Schema validation, Type coercion, Dialect SQL generation, OpenApiGenerator.
- Integration tests with Testcontainers for Postgres, H2, MySQL (at least one alt DB): save schema → CRUD → openapi.
- Smoke test script to POST schema and CRUD.

11) CI/CD
- Maven Enforcer for Java version; SpotBugs and Checkstyle; Jacoco coverage (threshold minimal initially).
- CI matrix (JDK 21, 25) optional; keep 25 primary.
- Publish fat JAR artifact; optional Docker image build.

Refactoring roadmap (phased)
Phase 0 — Stabilize and guard rails (1–2 days)
- Add consistent JSON error responses, central error utility in ApiServer (no behavior change)
- Add /health and /ready endpoints (simple for now)
- Add basic tests for Utils and error model; wire CI quality gates (enforcer, checkstyle, spotbugs)

Phase 1 — HTTP layer cleanup (2–3 days)
- Introduce Request/Response wrappers and a tiny Router; refactor ApiServer handlers to use them
- Extract datasource handlers into a separate class; centralize send/sendJson
- Keep routes and signatures identical; no public API changes

Phase 2 — Dialect strategy extraction (3–4 days)
- Introduce Dialect interface and implementations; move SQL branching out of SchemaManager/JdbcManager
- Make SchemaService consume Dialect; keep current behavior; add unit tests for DDL strings per dialect

Phase 3 — Schema engine and typing (3–4 days)
- Split SchemaManager into SchemaService + MigrationPlanner; introduce FieldType enum; TypeCoercion utility
- Update EntityHandler to use TypeCoercion; improve validation messages
- Add tests for coercion and constraints

Phase 4 — CRUD enhancements and pagination (2–3 days)
- Add pagination/query options to GET /api/{entity} (page,size,sort,filter minimal)
- Update OpenAPI and USER_GUIDE/README

Phase 5 — Security and observability (2–3 days)
- Optional basic auth/token for admin endpoints; document reverse proxy recommendation
- Add request IDs (MDC), timing logs, simple counters

Phase 6 — Test hardening and DB matrix (ongoing)
- Add Testcontainers matrix (H2 + Postgres baseline); extend as needed
- Coverage baseline via Jacoco; enforce modest threshold

Risk & mitigation
- Risk: Dialect drift breaking existing DBs. Mitigation: unit snapshots for DDL and Testcontainers for Postgres/H2; feature flags.
- Risk: Public behavior changes; Mitigation: keep endpoints, payloads, and default behaviors identical during refactor; document any opt-in changes.
- Risk: Over-engineering; Mitigation: keep abstractions minimal (interfaces with few methods), measure value.

Acceptance criteria
- Code passes CI with static analysis; fat JAR artifact produced
- /openapi.json works against H2 and Postgres without errors
- Consistent error responses across endpoints; no stack traces leaked
- Basic unit/integration tests green on CI; coverage report published
- Docs (README, USER_GUIDE, FUNCTIONAL_SPEC, LOW_LEVEL_DESIGN) updated

Deliverables
- New packages and classes: Router, Request/Response wrappers, Dialect interface + impls, SchemaService, MigrationPlanner, TypeCoercion
- Tests: unit + minimal Testcontainers suite
- CI: Enforcer, Checkstyle, SpotBugs, Jacoco; matrix optional
- Docs: Updated guides and design docs; this proposal tracked under docs/

High-level class sketch (non-binding)
- com.appbana.api.Router: register(method, pattern, handler)
- com.appbana.api.HttpRequest/HttpResponse: wrappers around HttpExchange
- com.appbana.db.Dialect: createMetaTables(), upsertSchema(), sqlType(Field), alterColumnTypeSql(), quote()
- com.appbana.schema.SchemaService: save/load/list/plan/apply
- com.appbana.schema.MigrationPlanner: compute DDL based on Dialect
- com.appbana.schema.TypeCoercion: coerce(value, FieldType, constraints)

Coding standards and tools
- Style: Google Java Style (Checkstyle/Spotless); final classes where possible; avoid static state except constants
- Nullability: Optional and explicit null checks; prefer immutable DTOs for responses
- Logging: Parameterized logs; no PII/secrets; mask passwords
- Exceptions: Use IllegalArgumentException for 400, not-found for 404, RuntimeException for 500; map centrally

Timeline & effort (T-shirt sizes)
- Phase 0–1: S
- Phase 2–3: M
- Phase 4–5: M
- Phase 6: ongoing

Appendix: Immediate quick wins
- Centralize JSON send helper and error mapper (already partially present)
- Move repeated driver inference maps to a single utility
- Replace literal strings for endpoints with constants to avoid typos
- Add small smoke tests executing POST /schema?preview=true and /api CRUD against H2

Appendix: Optional Spring Boot adapter (deferred)
- Provide a Spring Boot module exposing the same handlers via Spring MVC/WebFlux while keeping the core (schema/db) portable
- Value: production-grade features (actuator, config, DI) without locking core to Spring
