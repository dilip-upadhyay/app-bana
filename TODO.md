# TODO / Backlog

This backlog captures near-term and medium-term improvements. Check off items as they are completed and keep this list synchronized with docs.

Priority A (next)
- [ ] Add authentication and role-based access control to /schema, /api/*, and /ui/datasource/*.
- [ ] Add per-datasource health endpoint and surface status in UI (badge + last tested timestamp).
- [ ] Persist last test result/time for each datasource; show a “last tested” column in the list.
- [ ] Make Test Connection timeout configurable; improve error details and mask sensitive data.

Priority B
- [ ] Add CI checks and a PR template to enforce “docs updated” (README.md, FUNCTIONAL_SPEC.md, LOW_LEVEL_DESIGN.md, COPILOT_NOTES.md).
- [ ] Enhance OpenAPI: add response schemas/examples; optionally bundle Swagger UI locally (avoid CDN).
- [ ] Add pagination, sorting, and basic filtering to GET /api/{entity} (query params) and reflect in OpenAPI.
- [ ] Add audit logging for schema and datasource changes (who/when/what).
- [ ] Add import/export for datasources and schemas (JSON) via UI and API.

Priority C
- [ ] Multi-tenant support (namespacing per-tenant for schemas and data access).
- [ ] Optional Spring Boot adapter while retaining metadata-based design (same endpoints + swagger).
- [ ] Deployment: Docker/Compose polish, sample Postgres + app compose file; Helm chart (optional).
- [ ] Unit tests: OpenApiGenerator, SchemaManager.generateMigrationPlan, ApiServer handlers (schema/datasource/test), URL builder.
- [ ] Improve migration engine (rename columns safely, rollback plan preview, dry-run SQL validation per DB type).

Housekeeping
- [ ] Add “last tested” visual indicator in the UI list; allow manual refresh; debounce repeat tests.
- [ ] Add connectivity indicator chip (Live/Down) that pings health endpoint.
- [ ] Centralize driver inference map; document supported DBs and jdbc-url examples in README.

Notes
- Keep this file aligned with the “Next recommended enhancements” sections in README.md and FUNCTIONAL_SPEC.md.
- After implementing a backlog item, update docs and the change logs accordingly.

