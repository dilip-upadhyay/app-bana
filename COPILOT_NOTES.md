# Copilot Notes — project snapshot

This file summarizes the current state of the project so an automated agent (Copilot) can resume work from here.

## Change Log (recent)
- 2025-09-14: Fixed Java syntax errors (missing/stray braces) in ApiServer and SchemaManager that prevented compilation.
- 2025-09-14: Normalized SQL identifier quoting to use double-quoted UPPERCASE identifiers (e.g. "USER","NAME") across SchemaManager and ApiServer to avoid H2 reserved-word / case-sensitivity issues.
- 2025-09-14: Added UI features in ui/builder.html:
  - "Generated API Endpoints" panel (GET /api/endpoints).
  - Clickable endpoints that open a minimal API Tester.
  - API Tester supports GET/POST/PUT with sample body generation from /schema/{name}, ID input, send button, and response display.
- 2025-09-14: Implemented migration preview (POST /schema?preview=true) and applied UI preview/apply flow.
- 2025-09-14: Updated COPILOT_NOTES.md and FUNCTIONAL_SPEC.md to reflect changes and recommendations.

## Key components (src/main/java/org/example)
- Main.java — application entrypoint (SchemaManager.init(); ApiServer.start(8080)).
- ApiServer.java — embedded HTTP server exposing endpoints and static UI route (see FUNCTIONAL_SPEC.md for details).
- SchemaManager.java — stores schema JSON in appbana_schemas, validates schema, creates/migrates tables, records executed DDL in appbana_migrations.
- JdbcManager.java — H2 connection and ensures meta tables (appbana_schemas, appbana_migrations).
- CodeGenerator.java — simple POJO generator.
- model/EntitySchema.java — schema model with Field metadata.

Frontend
- src/main/resources/ui/builder.html — minimal vanilla JS UI builder that emits schema JSON and POSTs to /schema. Recent UI changes:
  - Added "Generated API Endpoints" panel showing endpoints for saved entities (GET /api/endpoints).
  - Endpoints are clickable and open a minimal API Tester panel.
  - API Tester supports GET/POST/PUT requests, shows an ID input when endpoint requires {id}, auto-generates a sample JSON body for POST/PUT by fetching the entity schema (/schema/{name}), and displays status + response body.
  - Preview Migration and Apply Migration flow retained (POST /schema?preview=true then POST /schema to apply).

Build & run
- pom.xml — dependencies (jackson-databind, h2, slf4j-simple) and maven-shade-plugin producing an uber jar.
- mvnw — wrapper script; project builds with Java 21 as configured.
- After recent fixes the project builds cleanly (mvn package) and the server starts: java -jar dist/app-bana.jar

Local DB and migration notes
- H2 file DB at ./data/appbana (AUTO_SERVER=TRUE).
- Metadata tables:
  - appbana_schemas(name PK, json CLOB)
  - appbana_migrations(id, schema_name, sql, executed_at)
- Important: because identifier quoting behavior changed to use double-quoted UPPERCASE identifiers, existing tables created earlier with different quoting/casing may cause SQL errors when applying migrations or inserting rows. If you encounter errors (column not found / syntax error) do one of the following:
  - Recommended for development: DROP the problematic table and re-apply the schema so the server creates it with the new quoting. Example via H2 Console or JDBC:
    DROP TABLE IF EXISTS "USER";
    DROP TABLE IF EXISTS USER;
  - Use the UI Preview (POST /schema?preview=true) to inspect the planned DDL and, if safe, click Apply in the UI to execute it.

Notes and validation
- I fixed compile-time issues introduced during edits (missing brace) and ensured the code compiles.
- The identifier quoting change prevents H2 reserved-word and case-sensitivity issues for identifiers like USER or Name.
- The UI tester provides a quick way to exercise runtime APIs without external tools.

Next suggested changes
- Add example curl/snippets per endpoint in the API Tester UI.
- Persist recent tester requests/responses in browser localStorage.
- Add an option in SchemaManager to choose identifier quoting strategy (configurable) to assist migrations when moving between DBs.

If you want, I can:
- Restart the server and run a safe migration (rename columns or drop/recreate a table) against your local H2 DB and report results.
- Add example curl commands to the API Tester UI.

Contact & continuation notes
- Use this file as the up-to-date snapshot reflecting the recent code changes (API Tester, clickable endpoints, quoting fixes, compilation fixes, migration preview/apply flow).
- When implementing further features, keep changes small and testable. Ensure migration preview is used before applying changes in environments with existing data.

## Files changed in the recent work (for quick reference)
- src/main/java/org/example/ApiServer.java — fixed missing brace; updated quote() to produce double-quoted UPPERCASE identifiers to match SchemaManager; added /api/endpoints handler (if not present earlier).
- src/main/java/org/example/SchemaManager.java — fixed stray brace; changed quote() to return double-quoted UPPERCASE identifiers; added generateMigrationPlan and paginated listSchemaNames.
- src/main/resources/ui/builder.html — added endpoints panel, clickable links, and API Tester UI (sample body generation, send, response display).
- COPILOT_NOTES.md, FUNCTIONAL_SPEC.md — documentation updates reflecting current behavior.

## Rollback / revert notes
- If you need to revert quoting strategy to previous behavior, revert quote() in both SchemaManager and ApiServer to previous implementation and re-run migration preview to assess DB differences.
- The UI source is idempotent; if changes need to be undone, restore src/main/resources/ui/builder.html from VCS history.

## DB migration caution (future reference)
- Identifier quoting change may require manual reconciliation for existing H2 data files. If you see errors such as "Column "Name" not found" or SQL syntax errors referencing USER:
  - Preferred dev path: drop the problematic table(s) and re-save schema via UI to re-create them with the new quoting.
  - Non-destructive path: use POST /schema?preview=true to inspect planned DDL; verify ALTER/RENAME statements before applying.

## Quick recovery commands (for local development)
- Build and run:
  ./mvnw -DskipTests package && java -jar dist/app-bana.jar
- Drop a problematic table in H2 Console or JDBC:
  DROP TABLE IF EXISTS "USER";
  DROP TABLE IF EXISTS USER;

## Recommendations for future work (short)
- Add a configurable quoting strategy stored in metadata to assist migrations between quoting modes.
- Add a migration-diff UI that highlights destructive changes and requires explicit confirmation with checkboxes for destructive statements.
- Persist API Tester history in localStorage and add example curl snippets per endpoint in the UI.

## Future reference: commit & code review checklist
- Run mvn -DskipTests package after edits and ensure no compilation errors.
- Run manual end-to-end test: POST /schema -> Apply Migration -> POST /api/{entity} (create) -> GET /api/{entity} and GET /api/{entity}/{id}.
- Inspect appbana_migrations table to verify recorded DDL statements.

End of snapshot.
