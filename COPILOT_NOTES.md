# Copilot Notes — project snapshot

This file summarizes the current state of the project so an automated agent (Copilot) can resume work from here.

Project overview
- Metadata-driven runtime API (MVP): UI builder -> schema JSON -> backend persists schema, auto-creates/migrates DB tables, exposes generic CRUD endpoints.
- No heavy frameworks: Java SE HttpServer + JDBC + small libraries (Jackson, H2, SLF4J).

Key components (src/main/java/org/example)
- Main.java — application entrypoint (SchemaManager.init(); ApiServer.start(8080)).
- ApiServer.java — embedded HTTP server exposing endpoints and static UI route:
  - POST /schema — save schema JSON; triggers create/migrate table
  - GET /schema/{name} — retrieve saved schema
  - POST /api/{entity} — insert record
  - GET /api/{entity} — list records
  - GET/PUT/DELETE /api/{entity}/{id} — record-level operations
  - GET /ui/builder — serves the minimal UI builder page
  - Includes input coercion and validation (required, min/max, length, pattern) and returns 400 on validation errors.
- SchemaManager.java — stores schema JSON in appbana_schemas, validates schema, creates tables, adds columns, supports renames (existingName) and ALTER TYPE, records executed DDL in appbana_migrations.
- JdbcManager.java — H2 connection and ensures meta tables (appbana_schemas, appbana_migrations).
- CodeGenerator.java — simple POJO generator (writes files into generated-sources/...); does not compile/load classes.
- model/EntitySchema.java — schema model with Field metadata (type, pk, autoIncrement, length, required, min, max, pattern, label, placeholder, order, existingName).

Frontend
- src/main/resources/ui/builder.html — minimal vanilla JS UI builder that emits schema JSON and POSTs to /schema.
- UI accessible at /ui/builder (static handler in ApiServer).

Build & run
- pom.xml — dependencies (jackson-databind, h2, slf4j-simple) and maven-shade-plugin producing an uber jar.
- mvnw — wrapper script that prefers system mvn; if missing, downloads Apache Maven binary into .mvn/apache-maven and runs it.
- Artifacts produced by build:
  - target/original-app-bana-1.0-SNAPSHOT-fat.jar (or shaded jar)
  - dist/app-bana.jar — copy of built fat jar for convenience

Local DB
- H2 file-based DB at ./data/appbana (AUTO_SERVER=TRUE) created on runtime.
- Metadata tables:
  - appbana_schemas(name PK, json CLOB)
  - appbana_migrations(id, schema_name, sql, executed_at)

.gitignore
- .gitignore added to ignore target/, dist/, .mvn downloads, generated-sources/, /data/, and IDE files.

Limitations & caveats
- Migrations are basic: add-column, rename (via existingName), alter column type attempted automatically — may fail for complex incompatible changes.
- No automated rollback or advanced migration planning UI (can be added).
- Code generator produces sources only; no compile/load step implemented.
- UI builder is minimal (no drag/drop, ordering via order property only).
- mvnw uses a downloader script rather than official maven-wrapper jar; works if curl/wget and tar available.

Next recommended tasks (pick and implement)
- Add official Maven Wrapper files (.mvn/wrapper/*) instead of downloader script.
- Improve migration UX: preview DDL and require user approval before executing.
- Extend UI builder: drag/drop, reorder, field labels and validations UI, load/edit existing schema.
- Extend CodeGenerator: generate DAOs/controllers and optionally compile/load generated code.
- Add auth and audit for schema and data changes.

Useful commands (for humans)
- Build: ./mvnw -DskipTests package  OR  mvn -DskipTests package
- Run: java -jar dist/app-bana.jar
- Open UI builder: http://localhost:8080/ui/builder
- Example: post schema with curl: curl -X POST -H "Content-Type: application/json" --data @schema.json http://localhost:8080/schema

Where to look (files)
- pom.xml
- mvnw
- src/main/java/org/example/{Main,ApiServer,SchemaManager,JdbcManager,CodeGenerator}
- src/main/java/org/example/model/EntitySchema.java
- src/main/resources/ui/builder.html
- .gitignore
- dist/app-bana.jar (built artifact)

If you want, I will:
- untrack build/generated files and commit .gitignore (run git commands), or
- add the official Maven Wrapper files, or
- implement one of the next recommended tasks above.

End of snapshot.
