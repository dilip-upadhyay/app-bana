# AppBana — Metadata-driven UI → API → Database

Small metadata-driven platform (MVP) that lets you design a UI/form, persist a schema, auto-create/migrate a backing table and expose generic runtime CRUD APIs — implemented with plain Java SE (no heavy frameworks).

Contents
- src/main/java/org/example — main server and components
- src/main/resources/ui/builder.html — minimal UI builder
- dist/app-bana.jar — built fat JAR (if present)
- .mvn/maven download (created by mvnw when needed)

Quick overview
- Designer creates an entity schema (JSON) describing fields and validations.
- POST /schema stores the schema JSON and ensures the underlying DB table exists (or is migrated).
- Generic CRUD endpoints are exposed under /api/{entity} and operate using schema metadata.
- No heavy framework: uses com.sun.net.httpserver.HttpServer, JDBC (H2 by default), and Jackson.

Tech stack
- Java 17 (compiled with --release 17)
- H2 embedded database (JDBC)
- Jackson databind for JSON
- SLF4J simple logger
- Maven build with Shade plugin to produce an executable fat JAR

Build and run
- With installed Maven
  - mvn -DskipTests package
  - java -jar target/original-app-bana-1.0-SNAPSHOT-fat.jar
- Without Maven (provided wrapper)
  - chmod +x mvnw
  - ./mvnw -DskipTests package
  - java -jar dist/app-bana.jar
- Or run from your IDE: run org.example.Main

What the server does on startup
- Creates/ensures metadata tables: appbana_schemas and appbana_migrations
- Starts an HTTP server on port 8080 (by default)
- Serves UI builder at /ui/builder

Endpoints
- POST /schema
  - Request body: EntitySchema JSON (see example below)
  - Action: validates schema, stores JSON in appbana_schemas, creates/migrates the backing table
  - Response: 201 on success or 400/500 on error

- GET /schema/{name}
  - Returns stored schema JSON or 404 if not found

- POST /api/{entity}
  - Body: JSON object with field values (omit auto-increment PK)
  - Inserts record into the entity table (validation + coercion applied)
  - Returns generated id (if any)

- GET /api/{entity}
  - Lists all rows for the entity

- GET /api/{entity}/{id}
  - Fetch record by primary key

- PUT /api/{entity}/{id}
  - Update fields for the entity (validated/coerced)

- DELETE /api/{entity}/{id}
  - Delete record by primary key

Schema JSON (example)
{
  "name": "contact",
  "fields": [
    {"name":"id","type":"long","primaryKey":true,"autoIncrement":true},
    {"name":"firstName","type":"string","length":100,"required":true},
    {"name":"age","type":"int","min":0}
  ]
}

Notes on types and validation
- Supported field types: string, text, int/integer, long, boolean, date/timestamp
- Validation fields available per field: required, min, max, pattern, length
- Coercion rules:
  - int/long: numeric strings or numbers
  - boolean: true/false/1/0
  - date/timestamp: ISO-8601 or epoch millis
  - string/text: truncated or rejected by length validation

Migrations
- When saving a schema, SchemaManager will:
  - CREATE TABLE if missing
  - ALTER TABLE ADD COLUMN for newly added fields
  - If a Field includes existingName, SchemaManager will attempt a column rename (ALTER ... RENAME TO)
  - Attempt ALTER COLUMN SET DATA TYPE for simple type changes; complex conversions may fail and should be done manually
  - All executed DDL statements are recorded in appbana_migrations for audit

Database
- Default: H2 embedded file DB at ./data/appbana (AUTO_SERVER=TRUE)
- For production, replace the JDBC URL in JdbcManager and ensure JDBC driver is on the classpath

Code generation
- CodeGenerator generates simple POJO Java sources into generated-sources/org/example/generated/
- It does not auto-compile or classload generated code yet — future enhancement

Frontend UI builder
- Minimal single-file builder at src/main/resources/ui/builder.html
- Use it to add fields and POST the schema to /schema
- UI is intentionally minimal; can be extended with drag-drop and editing existing schemas

Files of interest
- ApiServer.java — HTTP server, endpoints, input coercion + validation
- SchemaManager.java — schema persistence and migration logic
- JdbcManager.java — H2 connection and meta table creation
- CodeGenerator.java — simple Java POJO generator
- model/EntitySchema.java — schema model and field metadata

Repository hygiene
- .gitignore added to exclude target/, dist/, generated-sources/, .mvn downloads, /data/ and IDE files

Security / production considerations
- This MVP is not hardened. Before production use, add:
  - Authentication & authorization
  - Input size limits and stricter validation
  - SQL migration approval workflow
  - Connection pooling (e.g. HikariCP)
  - Proper logging, monitoring and error handling

Next steps you can ask me to implement
- Add official Maven Wrapper files (.mvn/wrapper/*)
- UI: drag/drop, schema load/edit, reorder fields
- Migration UX: preview DDL and approve before applying
- Codegen: generate DAOs/controllers and auto-compile
- Add auth and RBAC for APIs

Contact
- This README (and COPILOT_NOTES.md) is intended for the Copilot agent to pick up where work left off.

---

Generated: snapshot for Copilot continuation

