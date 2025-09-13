# AppBana Functional Specification

Version: 1.0 (snapshot)
Date: 2025-09-14

Purpose
- Describe current, working functionality of the AppBana MVP (metadata-driven UI → API → DB), and provide a prioritized set of recommended future enhancements with brief implementation notes and rough effort estimates.

1. Overview
- Runtime is metadata-driven: a UI builder emits an entity schema (JSON). Backend persists the schema and automatically creates/migrates a backing SQL table. Generic CRUD endpoints are exposed at runtime for each saved entity.
- No heavy frameworks used: Java SE com.sun.net.httpserver.HttpServer, JDBC (H2 by default), Jackson for JSON.

2. High-level architecture
- Frontend: static HTML/JS UI builder (served from resources) that creates EntitySchema JSON and POSTs to /schema.
- Backend services:
  - ApiServer — embedded HTTP server; handlers for /schema and /api/* and static UI route /ui/builder.
  - SchemaManager — validates schema JSON, persists schema (appbana_schemas), and creates/migrates tables (records DDLs in appbana_migrations).
  - JdbcManager — manages JDBC connection (H2 by default) and ensures metadata tables exist.
  - CodeGenerator — simple generator that emits POJO Java sources into generated-sources/ (no compile/load).
- Database: H2 file DB (./data/appbana) with metadata and entity tables created per schema.

3. Key runtime endpoints
- POST /schema
  - Input: JSON EntitySchema (see section 5)
  - Action: validate and persist schema + create or migrate table
  - Responses: 201 on success; 400 for validation errors; 500 for server errors
- GET /schema/{name}
  - Return stored schema JSON or 404
- POST /api/{entity}
  - Insert record. Body is JSON object mapping field names to values.
  - Server coercion & validation applied; uses PreparedStatement for SQL.
  - Returns generated PK id when available.
- GET /api/{entity}
  - List all records
- GET /api/{entity}/{id}
  - Get record by primary key
- PUT /api/{entity}/{id}
  - Update fields (validated & coerced)
- DELETE /api/{entity}/{id}
  - Delete record by primary key
- GET /ui/builder
  - Serves the minimal UI builder (static HTML) to create schemas

4. EntitySchema model (fields and semantics)
- Root: { name: string, fields: [ Field ] }
- Field properties (supported):
  - name (string) — column name
  - type (string) — string, text, int/integer, long, boolean, date/timestamp
  - primaryKey (boolean)
  - autoIncrement (boolean)
  - length (int) — for string types
  - required (boolean)
  - min, max (long) — numeric bounds
  - pattern (string) — regex for strings
  - label, placeholder (UI metadata)
  - order (int) — optional order hint
  - existingName (string) — optional: used during migrations to rename an existing column

5. Server-side validation and coercion
- Validation: required, duplicate field names, single PK enforcement, autoIncrement only on numeric PKs, length/min/max logic, min<=max check, valid types.
- Coercion rules on insert/update:
  - int/long: accept numbers or numeric strings
  - boolean: accept true/false or 1/0
  - date/timestamp: accept ISO-8601 or epoch millis; coerced to java.sql.Timestamp
  - strings: enforce length and optional regex pattern
- Validation failures produce HTTP 400 with JSON { "error": "message" }.

6. Database mapping & migrations
- Default mapping (examples): string → VARCHAR(length), text → CLOB, int → INT, long → BIGINT, boolean → BOOLEAN, date/timestamp → TIMESTAMP.
- On POST /schema
  - If table not present: CREATE TABLE with fields and PK constraint.
  - If table exists: SchemaManager will
    - Add missing columns via ALTER TABLE ADD COLUMN
    - If a field declares existingName and existingName exists in DB, attempt rename: ALTER TABLE ... ALTER COLUMN "old" RENAME TO "new"
    - Detect simple type mismatches and attempt ALTER COLUMN ... SET DATA TYPE (DB-dependent; may fail)
  - Every executed DDL is inserted into appbana_migrations(schema_name, sql) for audit/history.
- Limitations: migrations are conservative (adds and simple renames/types) — complex migrations and data transformations must be manual or via migration preview and approval.

7. Code generation
- CodeGenerator creates POJO Java source files for an EntitySchema into generated-sources/org/example/generated/.
- No automatic compilation or classloading is implemented (future enhancement).

8. Frontend UI builder
- Minimal single-file builder at src/main/resources/ui/builder.html.
- Features: add fields, set type/length/PK/auto/required, export JSON, POST to /schema.
- Limitations: no drag/drop, no load/edit existing schema, limited UI metadata editing.

9. Build, run, environment
- Build: Maven with Shade plugin; a runnable fat JAR is produced.
- Scripts: mvnw (downloader-style wrapper) provided; SDKMAN is recommended to manage Java versions.
- Project includes .sdkmanrc (java=21.0.8-tem). CI workflow uses JDK 21 (Temurin).
- Example local run: ./mvnw -DskipTests package && java -jar dist/app-bana.jar

10. Security and production considerations
- No authentication, authorization, or role-based access control implemented — add before exposing to untrusted networks.
- Input validation implemented but should be hardened (sanitization, size checks, rate limits).
- Use connection pooling for production (HikariCP) and external RDBMS (Postgres recommended).
- Migrations should require approval in production; maintain backups before applying DDL.

11. Logging and monitoring
- Uses SLF4J simple binding currently (stdout). Replace with a production logger (Logback) and add metrics (Prometheus / JMX) if needed.

12. Artifacts, files, and locations
- Key files:
  - src/main/java/org/example/ApiServer.java
  - src/main/java/org/example/SchemaManager.java
  - src/main/java/org/example/JdbcManager.java
  - src/main/java/org/example/CodeGenerator.java
  - src/main/java/org/example/model/EntitySchema.java
  - src/main/resources/ui/builder.html
  - pom.xml, mvnw, .sdkmanrc, README.md, COPILOT_NOTES.md
- Built artifacts:
  - target/original-app-bana-1.0-SNAPSHOT-fat.jar
  - dist/app-bana.jar (copy for convenience)

13. Recommended enhancements (prioritized)
Priority A — Safety & environment (small effort, high value)
- Add migration preview endpoint and UI (GET /schema/{name}/plan or POST /schema?preview=true) that returns the DDL statements the system would execute without applying them; require explicit user approval to apply. (Effort: 2–4 days)
- Replace downloader mvnw with the official Maven Wrapper (.mvn/wrapper/*) for standard behavior. (Effort: <1 day)
- Add a pre-commit or CI check to ensure java.version matches project (.sdkmanrc or pom property). (Effort: <1 day)

Priority B — Developer experience & typing (medium effort)
- Extend the UI builder: drag/drop, reorder fields, labels/placeholders, validations UI, load/edit existing schema, export/import. (Effort: 3–7 days)
- Expand CodeGenerator to emit DAOs and controller templates and optionally compile generated sources with the Java Compiler API; produce an artefact module for developers to extend. (Effort: 5–10 days)

Priority C — Production readiness (larger effort)
- Add authentication + RBAC for schema management and data APIs; integrate with OAuth2 / OpenID Connect or JWT-based auth. (Effort: 3–7 days)
- Replace H2 with production RDBMS connector templates and add connection pool (HikariCP); add environment-based config for DB credentials. (Effort: 2–4 days)
- Implement a robust migrations engine: build a migration plan, support reversible migrations, logging, dry-run, and rollback. (Effort: 7–14 days)

Priority D — Advanced features (optional)
- Plugin architecture to add custom validators, field types, or persistence strategies (NoSQL, file, external REST). (Effort: 7–14 days)
- Live code generation with compile+classload so teams can generate typed Java APIs and extend them at runtime. (Complex, risky). (Effort: 10–20 days)
- Multi-tenant support: namespace schemas and data per tenant, isolation and per-tenant migrations. (Effort: 10–20 days)

14. Suggested immediate next tasks
- Implement migration preview and approval UX (safety-first).
- Add official Maven Wrapper files and commit .sdkmanrc and CI workflow to the repo.
- Add a simple integration test that exercises POST /schema + POST /api/{entity} + GET to validate end-to-end flow.

15. Contact & continuation notes
- COPILOT_NOTES.md is maintained as the machine-friendly snapshot. Use it to resume development.
- When implementing features, follow the priorities above and keep changes small and testable.

End of functional specification

