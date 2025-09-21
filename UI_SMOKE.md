# UI Smoke Test — Existing UIs and /ui/designer Hosting

Status: Ready to run
Last updated: 2025-09-22

Purpose
- Provide a fast, repeatable smoke checklist to validate that the existing UIs continue to work, and that the Angular app (when added) is correctly hosted at /ui/designer without regressions.

Scope
- Existing UIs: /ui/builder, /ui/datasource, /ui/swagger
- Backing API health: /health, /ready, /openapi.json, datasource endpoints
- Angular studio (when present): /ui/designer

Prerequisites
- Java 25 available on PATH.
- Build artifacts present under `target/` or build locally.
- Optional: set tokens if your environment requires auth.

Build & run (one-time per session)
```bash
./mvnw -DskipTests package
java -jar target/app-bana-1.0-SNAPSHOT-fat.jar
```

Optional: tokens for auth-enabled environments
```bash
# in a separate shell or prefix the java command
export APPBANA_ADMIN_TOKEN=admin123
export APPBANA_READ_TOKEN=read123
```

Base URL
- Default: http://localhost:8080
- If you changed the port, adapt the URLs below accordingly.

1) Liveness and readiness
- Liveness should always be UP
```bash
curl -s http://localhost:8080/health | jq .
```
- Readiness should be ok=true when the DB is reachable
```bash
curl -s http://localhost:8080/ready | jq .
```

2) OpenAPI and Swagger UI
- If tokens are set, include the header
```bash
curl -s -H "X-AppBana-Token: ${APPBANA_READ_TOKEN:-admin123}" http://localhost:8080/openapi.json | head -n 5
```
- Open the embedded Swagger UI (manually in a browser):
  - http://localhost:8080/ui/swagger
  - Enter your token in the on-page box and confirm that the spec loads.

3) Datasource UI and endpoints
- Open the UI in a browser: http://localhost:8080/ui/datasource
  - Confirm the page loads and shows an empty or existing list.
  - If auth is enabled, enter the token in the on-page box; the UI should store and reuse it.
- List datasources via API (read or admin token):
```bash
curl -s -H "X-AppBana-Token: ${APPBANA_READ_TOKEN:-admin123}" http://localhost:8080/ui/datasource/list | jq '.[0] // {}'
```
- Health ping (active or named datasource):
```bash
curl -s -H "X-AppBana-Token: ${APPBANA_READ_TOKEN:-admin123}" "http://localhost:8080/ui/datasource/health" | jq .
```

4) Builder UI
- Open the UI in a browser: http://localhost:8080/ui/builder
  - Enter the token if required and save.
  - Create a minimal schema (e.g., contact with id/firstName) and POST it.
- Confirm schema saved via API:
```bash
curl -s -H "X-AppBana-Token: ${APPBANA_READ_TOKEN:-admin123}" http://localhost:8080/schema | jq .
```
- Confirm CRUD list works:
```bash
curl -s -H "X-AppBana-Token: ${APPBANA_READ_TOKEN:-admin123}" http://localhost:8080/api/contact | jq .
```

5) Regression-safe checks (headers)
- Verify that the server still accepts Authorization: Bearer for non-UI clients:
```bash
curl -s -H "Authorization: Bearer ${APPBANA_READ_TOKEN:-admin123}" http://localhost:8080/openapi.json | head -n 3
```
- Verify that built-in UIs work with X-AppBana-Token only (done in the browser by saving the token in the UI’s token box).

6) Angular studio host (when present)
- After the Angular app is added (apps/studio), it should be accessible at /ui/designer.
- Open in browser: http://localhost:8080/ui/designer
  - Expect the Angular app shell to render.
  - Use Settings to enter your token; requests should include X-AppBana-Token.
- Regression: confirm existing UIs still work after hosting is enabled:
  - http://localhost:8080/ui/builder
  - http://localhost:8080/ui/datasource
  - http://localhost:8080/ui/swagger

6b) Studio SSR (optional quick check)
- The Studio app can also be run as a standalone SSR server during UI development.
- Build & run from the repo root:
```zsh
cd /Users/dilip/git/app-bana
./build.sh --clean
./run.sh --port 4000 --open
```
- Then open: http://localhost:4000/
- Note: This SSR server is separate from the Java server hosting /ui/* on port 8080.

7) Optional HTTPS quick check (if enabled)
- If you started with HTTPS settings, verify redirects/served pages:
```bash
curl -I http://localhost:8080/ui/builder | head -n 5
curl -k -I https://localhost:8443/ui/builder | head -n 5
```

Troubleshooting
- If /ready returns ok=false, fix datasource settings in /ui/datasource and retest.
- If token prompts persist, clear site data (localStorage) and re-enter the token.
- If /ui/designer 404s, it likely means the Angular app hasn’t been added/hosted yet—skip step 6.

Notes
- Keep this checklist updated as we add features. For October, ensure steps 1–5 pass; add step 6 once /ui/designer is wired.
- This is a smoke test, not a full regression; add deeper tests as needed.
