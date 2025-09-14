# Copilot Notes — project snapshot

This file summarizes the current state of the project so an automated agent (Copilot) can resume work from here.

## Change Log (recent)
- 2025-09-14: UI reverted to basic builder-v1 (minimal entity/fields builder, no advanced panels or API tester).
- 2025-09-14: All builder-v2 UI files removed from the project.
- 2025-09-14: Added Swagger/OpenAPI spec endpoint at `/openapi.json` (dynamically generated for all REST endpoints from metadata).
- 2025-09-14: Documentation updated in README.md and FUNCTIONAL_SPEC.md to reflect these changes.

## Key components (src/main/java/org/example)
- Main.java — application entrypoint (SchemaManager.init(); ApiServer.start(8080)).
- ApiServer.java — embedded HTTP server exposing endpoints and static UI route (see FUNCTIONAL_SPEC.md for details).
- OpenApiGenerator.java — generates OpenAPI 3.0 spec from all saved schemas, served at /openapi.json.
- SchemaManager.java — stores schema JSON in appbana_schemas, validates schema, creates/migrates tables, records executed DDL in appbana_migrations.
- JdbcManager.java — H2 connection and ensures meta tables (appbana_schemas, appbana_migrations).
- CodeGenerator.java — simple POJO generator.
- model/EntitySchema.java — schema model with Field metadata.

Frontend
- src/main/resources/ui/builder.html — minimal vanilla JS UI builder that emits schema JSON and POSTs to /schema. No advanced panels or API tester.

Build & run
- pom.xml — dependencies (jackson-databind, h2, slf4j-simple) and maven-shade-plugin producing an uber jar.
- mvnw — wrapper script; project builds with Java 21 as configured.
- After recent fixes the project builds cleanly (mvn package) and the server starts: java -jar dist/app-bana.jar

Local DB and migration notes
- H2 file DB at ./data/appbana (AUTO_SERVER=TRUE).
- Metadata tables:
  - appbana_schemas(name PK, json CLOB)
  - appbana_migrations(id, schema_name, sql, executed_at)
- Identifier quoting uses double-quoted UPPERCASE identifiers to avoid H2 reserved-word / case-sensitivity issues.

Notes and validation
- The UI is now minimal and basic; all advanced features have been removed.
- Swagger/OpenAPI spec is available at /openapi.json for all generated endpoints.
- The project builds and runs cleanly with these changes.

Next suggested changes
- Add Swagger UI static page to visualize the OpenAPI spec interactively (optional).
- Enhance the minimal UI builder with schema editing/loading and field reordering (optional).

Contact & continuation notes
- Use this file as the up-to-date snapshot reflecting the recent code changes (UI revert, OpenAPI spec endpoint, documentation updates).
- When implementing further features, keep changes small and testable. Ensure migration preview is used before applying changes in environments with existing data.

End of snapshot.
