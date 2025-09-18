# AppBana — User Guide

Welcome! This guide walks you through installing, configuring, and using AppBana end to end.

What is AppBana?
- A metadata-driven MVP. Design entities in a browser, AppBana persists the schema, auto-creates/migrates a table, and serves generic CRUD APIs.
- No heavy framework; it’s plain Java + HttpServer + JDBC + HikariCP.

Contents
- Prerequisites
- Quick start
- Configuration (port, config file, environment overrides)
- Datasource management (UI + API)
- Designing entities (Schema Builder)
- Using the runtime CRUD APIs
- OpenAPI & Swagger UI
- Troubleshooting & tips
- Security notes

Prerequisites
- Java 25 (required)
- macOS/Linux or Windows
- Internet access (for Swagger UI CDN), optional
- Optional: Docker (to run Postgres locally)

Quick start
1) Build the app
```bash
./mvnw -DskipTests package
```
2) Run it
```bash
java -jar target/app-bana-1.0-SNAPSHOT-fat.jar
# If 8080 is busy, override the port
java -Dappbana.port=8081 -jar target/app-bana-1.0-SNAPSHOT-fat.jar
```
3) Open these URLs
- Datasource UI: http://localhost:8080/ui/datasource
- Schema Builder: http://localhost:8080/ui/builder
- Swagger UI: http://localhost:8080/ui/swagger
- OpenAPI JSON: http://localhost:8080/openapi.json

Configuration
- Port
  - System property: `-Dappbana.port=9090`
  - Env var: `APPBANA_PORT=9090`
- Config file
  - Default: `data/appbana-config.json`
  - Override path: env `APPBANA_CONFIG` or `-Dappbana.config=...`
- Env overrides for connection (optional)
  - APPBANA_JDBC_URL, APPBANA_DB_USER, APPBANA_DB_PASS, APPBANA_DB_DRIVER

Datasource management
Use the Datasource UI at /ui/datasource to:
- Add/Update
- Activate (switch active datasource)
- Delete
- Test Connection (form-level, and per-row in the list)

Fields
- Required basics: name, type, JDBC URL (or build it), username, password, driver
- JDBC URL Builder (optional):
  - Builds URLs for H2 (file/mem), Postgres, MySQL, MariaDB, SQL Server, Oracle, SQLite from host/port/db + params
  - Enable Auto-build to keep URL synced as you edit
- Server-side URL build (API):
  - If you POST to save without `url`, the server builds it from components: { type, host, port, dbname, params }, plus H2/SQLite extras.
- Pool settings (optional): maxPoolSize, minIdle, connectionTimeoutMs, idleTimeoutMs, maxLifetimeMs, autoCommit, poolName

Run Postgres in Docker (zsh-safe quoting for #)
```bash
docker run --name appbana-pg -d \
  -e POSTGRES_USER=sa \
  -e POSTGRES_PASSWORD='Password_123#' \
  -e POSTGRES_DB=appbana \
  -p 5432:5432 \
  postgres:16
```
Create a Postgres datasource (API)
```bash
curl -sS -X POST http://localhost:8080/ui/datasource/save \
  -H 'Content-Type: application/json' \
  --data-binary '{
    "name": "pg",
    "type": "postgres",
    "host": "localhost",
    "port": "5432",
    "dbname": "appbana",
    "username": "sa",
    "password": "Password_123#",
    "driver": "org.postgresql.Driver"
  }'
```
Test the connection (UI)
- Click “Test Connection” on the form (uses current inputs)
- Click “Test” in the list row (tests a saved datasource by name)

Test the connection (API)
```bash
curl -sS -X POST http://localhost:8080/ui/datasource/test \
  -H 'Content-Type: application/json' \
  --data-binary '{"name":"pg"}'
# Or provide components to have the server build the URL:
curl -sS -X POST http://localhost:8080/ui/datasource/test \
  -H 'Content-Type: application/json' \
  --data-binary '{
    "type":"postgres","host":"localhost","port":"5432","dbname":"appbana",
    "username":"sa","password":"Password_123#","driver":"org.postgresql.Driver"
  }'
```

Designing entities (Schema Builder)
1) Visit /ui/builder
2) Define a schema (example):
```json
{
  "name": "contact",
  "fields": [
    {"name":"id","type":"long","primaryKey":true,"autoIncrement":true},
    {"name":"firstName","type":"string","length":100,"required":true},
    {"name":"age","type":"int","min":0}
  ]
}
```
3) Preview migration
- POST to `/schema?preview=true` with the JSON; you’ll get a list of DDL statements.
4) Save schema
- POST to `/schema` with the JSON to persist and apply migrations.

Using the runtime CRUD APIs
- After saving a schema named `contact`, CRUD endpoints are available:
  - POST /api/contact — create
  - GET /api/contact — list
  - GET /api/contact/{id} — get
  - PUT /api/contact/{id} — update
  - DELETE /api/contact/{id} — delete

Examples (replace port if overridden)
```bash
# Create
curl -sS -X POST http://localhost:8080/api/contact \
  -H 'Content-Type: application/json' \
  -d '{"firstName":"Ada","age":36}'

# List
curl -sS http://localhost:8080/api/contact

# Get by id
curl -sS http://localhost:8080/api/contact/1

# Update
curl -sS -X PUT http://localhost:8080/api/contact/1 \
  -H 'Content-Type: application/json' \
  -d '{"age":37}'

# Delete
curl -sS -X DELETE http://localhost:8080/api/contact/1
```

OpenAPI & Swagger UI
- Spec: GET /openapi.json
- UI: /ui/swagger (renders the spec)
- Tip: Re-open Swagger after adding schemas to see new endpoints.

Troubleshooting & tips
- Wrong credentials
  - Use /ui/datasource “Test Connection” (form or per-row) and fix settings.
  - The server still starts even if init fails; check logs.
- Port already in use
  - Run with `-Dappbana.port=8081`.
- Driver not found
  - Ensure the `driver` matches the type (e.g., Postgres → org.postgresql.Driver).
- H2 file locks
  - Close other processes using the file; use H2 mem mode for quick tests.
- Slow or failing connections
  - Use the Test endpoint (it times out quickly). Consider tuning pool settings.
- Config file shape
  - If only root fields are present, AppBana seeds a default datasource and marks it active.

Security notes
- No auth by default. Don’t expose /schema and /ui/datasource in untrusted networks.
- Add a reverse proxy with auth or network restrictions for production.

Where to go next
- Explore the backlog in `TODO.md` (auth, health checks, last-tested badges, CI doc checks).
- Review the schema and datasource management sections in README.md for deeper details.

