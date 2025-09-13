# AppBana — Metadata-driven UI → API → Database

Metadata-driven MVP: design forms in a minimal UI builder, persist the schema, auto-create/migrate a backing table, and expose runtime CRUD APIs. Implemented with plain Java SE (no heavy frameworks).

Quick summary
- Frontend: minimal UI builder (vanilla JS) that emits schema JSON.
- Backend: Java (HttpServer) that persists schemas, auto-creates/migrates tables via JDBC, and exposes generic CRUD endpoints at runtime.
- DB: H2 embedded (file) by default; JDBC usage allows swapping to Postgres/MySQL in production.

Status of repository
- Fully working MVP backend and minimal frontend builder included.
- Built fat JAR available at `dist/app-bana.jar` (created by local build run) and under `target/` after building.
- .gitignore present to ignore build, generated sources, downloaded Maven and DB files.
- .sdkmanrc pins Java version for the project (java=21.0.8-tem).
- COPILOT_NOTES.md contains an agent-friendly snapshot of the current state.
- CI workflow added at `.github/workflows/ci.yml` to build using JDK 21 in CI.

Tech stack
- Java 21 (LTS) is the recommended and configured Java for the project.
- H2 (embedded) for development
- Jackson (jackson-databind) for JSON
- SLF4J simple for logging
- Maven build with Shade plugin (produces an uber jar)
- SDKMAN recommended for local Java version management (project .sdkmanrc provided)

Build & run
- Install SDKMAN (recommended) and ensure the project Java is available:
  - curl -s "https://get.sdkman.io" | bash
  - source "$HOME/.sdkman/bin/sdkman-init.sh"
  - sdk install java 21.0.8-tem
  - sdk default java 21.0.8-tem
  - cd into the repo and SDKMAN will pick up `.sdkmanrc` if you enable auto-env or run `sdk env`.
- With system Maven installed:
  - mvn -DskipTests package
  - java -jar target/original-app-bana-1.0-SNAPSHOT-fat.jar
- With the provided mvnw wrapper script (downloads Maven if missing):
  - chmod +x mvnw
  - ./mvnw -DskipTests package
  - java -jar dist/app-bana.jar
- From IDE: run org.example.Main

Notes about the mvnw wrapper and CI
- `mvnw` in this repo is a downloader-style wrapper (it will download Apache Maven if system `mvn` is not found). You can replace it with the official Maven Wrapper files if you prefer.
- GitHub Actions workflow (.github/workflows/ci.yml) is configured to use Temurin JDK 21 and will run `mvn -DskipTests package` on push/PR.

Default runtime behavior
- On startup the app ensures two metadata tables exist in the H2 DB:
  - `appbana_schemas(name PK, json CLOB)` — stores schema JSON
  - `appbana_migrations(id IDENTITY, schema_name, sql CLOB, executed_at TIMESTAMP)` — records DDL executed
- Embedded HTTP server listens on port 8080 by default.
- UI builder served at: http://localhost:8080/ui/builder

API endpoints (runtime generic)
- POST /schema
  - Body: EntitySchema JSON (see example below)
  - Action: validate schema, persist JSON, create or migrate backing table
  - Responses: 201 on success; 400 on validation error; 500 on server error
- GET /schema/{name} — returns stored schema JSON or 404

- POST /api/{entity}
  - Insert record. Body is JSON object of field values (omit auto-increment PK).
  - Validation and coercion happen according to schema metadata.
  - Returns generated PK value when available.
- GET /api/{entity} — list all rows
- GET /api/{entity}/{id} — fetch record by PK
- PUT /api/{entity}/{id} — update record (validated/coerced)
- DELETE /api/{entity}/{id} — delete record by PK

Schema JSON (recommended format)
- Example:
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
- Field properties supported (partial list): name, type, primaryKey, autoIncrement, length, required, min, max, pattern, label, placeholder, order, existingName
  - `existingName` is used to indicate a rename from an existing column when migrating.

Validation and coercion (server-side)
- Supported types: string/text, int/integer, long, boolean, date/timestamp
- Coercion rules:
  - int/long: accept numbers or numeric strings
  - boolean: accept true/false or 1/0
  - date/timestamp: accept ISO-8601 string or epoch millis
- Validation rules implemented: required, min, max, length, regex pattern
- On validation failure the server returns 400 with an error message.

Migration behavior (what the server does when POST /schema)
- If table missing: CREATE TABLE (fields mapped to SQL types).
- If table exists:
  - Add new columns (ALTER TABLE ADD COLUMN).
  - If a field contains `existingName`, the server attempts to RENAME that column to the new name.
  - Attempt to ALTER COLUMN SET DATA TYPE when a type differs (simple cases only).
  - Every DDL executed is recorded in `appbana_migrations` for audit.

Configuration
- Project Java default: Java 21 (pom property `java.version` = 21). You can override at build time with `-Djava.version=...` if needed.
- Environment variables (optional):
  - APPBANA_PORT — server port (default: 8080). The code sets port in ApiServer.start; change Main.java to read this env var if you need dynamic port configuration.
  - JDBC_URL — JDBC connection URL (default is defined in JdbcManager.java as jdbc:h2:./data/appbana;AUTO_SERVER=TRUE). To use another DB, update JdbcManager.getConnection or modify to read env variables.
  - DB_USER — DB username (default: "sa" for H2)
  - DB_PASS — DB password (default: empty string for H2)

Error response format
- On errors the server returns JSON with an "error" key, e.g.:
  { "error": "missing schema name" }
  Validation errors return 400 and the message describes the failing field.

Rename example (schema migration)
- To rename an existing column `firstname` to `firstName` include `existingName` on the new field:
```
{
  "name":"contact",
  "fields":[
    {"name":"id","type":"long","primaryKey":true,"autoIncrement":true},
    {"name":"firstName","type":"string","existingName":"firstname"}
  ]
}
```
- SchemaManager will attempt `ALTER TABLE ... ALTER COLUMN "firstname" RENAME TO "firstName"` when applying this schema.

Logs
- Uses SLF4J simple binding; output goes to stdout by default. Configure logging by replacing the SLF4J binding or adjusting JVM/system properties.

Git housekeeping (if needed)
- Remove already-tracked build artifacts after adding .gitignore:
  git rm -r --cached target/ dist/ generated-sources/ .mvn/apache-maven || true
  git commit -m "chore: remove generated/build artifacts"

Where to change common settings in code
- Port: src/main/java/org/example/Main.java — change the port passed to ApiServer.start
- JDBC URL / credentials: src/main/java/org/example/JdbcManager.java — update JDBC_URL, USER, PASS or modify to read env variables

Generated code and artifacts
- CodeGenerator writes POJO source files to `generated-sources/org/example/generated/` (no compile/load step yet).
- Shaded jar (uber jar) is produced by the Shade plugin; the build created `target/original-app-bana-1.0-SNAPSHOT-fat.jar` and `dist/app-bana.jar` (copy).

Repository hygiene
- .gitignore excludes: target/, dist/, generated-sources/, .mvn downloads (.mvn/apache-maven), /data/, IDE files etc.
- If you already committed build artifacts, remove them from Git history or untrack them locally:
  - git rm -r --cached target/ dist/ generated-sources/ .mvn/apache-maven || true
  - git commit -m "chore: remove generated/build artifacts from repo"

Where to look in code (key files)
- src/main/java/org/example/ApiServer.java — HTTP handlers, coercion and validation
- src/main/java/org/example/SchemaManager.java — schema persistence and migration logic
- src/main/java/org/example/JdbcManager.java — JDBC connection and meta table creation
- src/main/java/org/example/CodeGenerator.java — source generation for POJOs
- src/main/java/org/example/model/EntitySchema.java — schema model and field metadata
- src/main/resources/ui/builder.html — minimal UI builder

Next recommended enhancements (pick one to implement next)
- Add official Maven Wrapper files (.mvn/wrapper/*) instead of the downloader script.
- Enhance UI builder with drag/drop, reorder, labels/placeholders and schema editing/loading.
- Add a migration preview endpoint/UI that returns planned DDL and requires explicit approval.
- Extend CodeGenerator to produce DAOs/controllers and auto-compile (JavaCompiler API) or generate a separate module.
- Add authentication/authorization and rate-limiting to the API.

Contact
- COPILOT_NOTES.md contains an agent-oriented snapshot for continuation.

---

Generated snapshot for human and agent continuation.
