# AppBana â€” AI Copilot Master Instructions

> **Read this entire document before touching any code.** It is specifically written so that any AI Agent, Copilot, or Developer joining this project can get up to speed in under 10 minutes and never break the environment.

---

## Table of Contents
1. [What is AppBana?](#1-what-is-appbana)
2. [Monorepo Structure](#2-monorepo-structure)
3. [How to Start the Application](#3-how-to-start-the-application)
4. [Key Configuration Files](#4-key-configuration-files)
5. [Architecture Deep Dive](#5-architecture-deep-dive)
6. [The AI Builder Engine (ai-builder/)](#6-the-ai-builder-engine-ai-builder)
7. [Agent Tool System](#7-agent-tool-system)
8. [Critical Rules â€” Multi-Tenant Entity Endpoints](#8-critical-rules--multi-tenant-entity-endpoints)
9. [Backend API Reference](#9-backend-api-reference)
10. [Frontend Architecture](#10-frontend-architecture)
11. [Database & Schema Management](#11-database--schema-management)
12. [Active Work & Known Issues](#12-active-work--known-issues)
13. [Development Conventions](#13-development-conventions)
14. [Common Pitfalls](#14-common-pitfalls)

---

## 1. What is AppBana?

AppBana is a **metadata-driven, AI-powered application builder**. Non-technical users describe what they want in natural language and the AI Agent autonomously:
1. Defines the data model
2. Creates PostgreSQL tables (via `SchemaManager`)
3. Generates REST CRUD APIs (automatically)
4. Renders UI pages (metadata → React runtime, see §10)

**Core Principle:** A single schema definition drives the entire stack â€” database, API, and UI are all metadata-driven.

---

## 2. Monorepo Structure

```
app-bana/
├── ai-builder/                  ← AI LLM engine (port 8081, Java 21)
│   └── src/main/java/com/appbana/ai/
│       ├── agent/               ← AiAgent.java — the Think/Act/Observe loop
│       │   └── tool/            ← All agent tools (scaffold, mock data, update_app, etc.)
│       ├── api/                 ← REST endpoints (AiChatController.java)
│       ├── dialogue/            ← DialogueManager.java — conversation state machine
│       ├── llm/                 ← AdvancedPromptEngine.java, OpenAiLlmService.java
│       ├── rag/                 ← ConversationMemory.java, Qdrant vector store
│       ├── learning/            ← UserPreferenceEngine.java
│       ├── optimization/        ← DirectAnswerService.java, SemanticCache.java
│       └── server/              ← AiServer.java, ToolRegistry.java
│
├── app-bana-service/            ← Core backend API (port 8080, Java 21)
│   └── src/main/java/com/appbana/
│       ├── ApiServer.java       ← HTTP server entry point (Tomcat embedded)
│       ├── SchemaManager.java   ← CREATE TABLE, migrations, multi-tenant isolation
│       ├── JdbcManager.java     ← Database connection pools (HikariCP)
│       ├── AppManager.java      ← App/entity lifecycle management
│       └── server/routes/
│           ├── GenericEntityRoutes.java  ← /api/{entity}/* CRUD
│           ├── AppRoutes.java            ← /appbana-studio/* app management
│           └── SchemaRoutes.java         ← /schema/* management
│
├── app-bana-shared/             ← Shared TS package (pnpm workspace, `@appbana/shared`)
│   └── src/
│       ├── api-client.ts        ← All frontend API access. `authedFetch()` fires
│       │                          `appbana:auth:expired` on 401 for global auth recovery
│       ├── app-context.ts       ← resolveAppContext() — path today, hostname-ready
│       ├── metadata.ts          ← PageMeta / ComponentNode / EntitySchema types
│       ├── postmessage.ts       ← Studio ↔ runtime postMessage schema
│       └── index.ts             ← Barrel export
│
├── app-bana-studio/             ← AI-native Studio (port 5174, React 18 + Vite + Tailwind + Zustand)
│   └── src/
│       ├── features/
│       │   ├── auth/            ← AuthGate.tsx (login + session-expired banner)
│       │   ├── chat/            ← ChatPane.tsx (streaming SSE + image paste)
│       │   ├── data-drawer/     ← DataDrawer.tsx (entity rows, add-row form, "Ask AI to seed")
│       │   ├── header/          ← Header.tsx
│       │   ├── preview/         ← PreviewPane.tsx (iframe → 5175 runtime)
│       │   └── sessions/        ← SessionPicker.tsx (search + rename + delete)
│       └── stores/              ← Zustand stores (session, workspace, chat, drawer)
│
├── app-bana-runtime/            ← Standalone deployed-app runtime (port 5175, React 18 + Vite)
│   └── src/
│       ├── runtime/
│       │   ├── AppRuntimeShell.tsx  ← Shell (sidebar + page slot)
│       │   ├── Renderer.tsx         ← Walks PageMeta → React tree
│       │   ├── StudioTableLive.tsx  ← Live entity tables (FK resolution, status pills, row actions)
│       │   ├── PageShell.tsx        ← Title / subtitle / breadcrumb / actions wrapper
│       │   ├── FormActions.tsx      ← Sticky Save/Cancel bar
│       │   ├── RowActions.tsx       ← ⋯ hover menu (Edit / Copy ID / Delete)
│       │   ├── Toaster.tsx          ← Zero-dep toast system
│       │   ├── RuntimeSidebar.tsx   ← Left-nav
│       │   ├── cell-formatters.ts   ← Pure helpers: humanizeHeader, formatDate, pickReferenceLabel
│       │   └── qualifyEntityKey.ts  ← Ensures `{tenantId}_{appId}_{entity}` prefix
│       └── pages/               ← Route entry (`/run/:tenant/:app`)
│
├── e2e/                         ← Playwright tests against Studio 5174 + Runtime 5175
│
├── docs/                        ← Architecture, planning, guides
│   ├── planning/
│   │   ├── AI_NATIVE_UI_REBUILD_PLAN.md   ← Primary rebuild plan
│   │   └── RUNTIME_UX_OVERHAUL_PLAN.md    ← Runtime polish sprints
│   ├── ACTIVE_TASKS.md
│   └── session_summary.md
│
├── config.json                  ← Database + OpenAI config (DO NOT commit secrets)
├── pnpm-workspace.yaml          ← app-bana-shared / -studio / -runtime
├── pom.xml                      ← Maven parent for ai-builder + app-bana-service
├── scripts/                     ← All launch scripts (.bat for Windows, .sh for macOS/Linux)
│   ├── start-everything.{bat,sh}    ← Master orchestrator (all four services in order)
│   ├── start-ai-builder.{bat,sh}    ← Restart AI Builder + Qdrant + Postgres
│   ├── start-backend.{bat,sh}       ← Restart backend + Postgres
│   ├── start-studio.{bat,sh}        ← Restart Studio (Vite 5174)
│   └── start-runtime.{bat,sh}       ← Restart Runtime (Vite 5175)
└── .github/copilot-instructions.md  ← This file
```

**Historical note:** The old monolithic `app-bana-ui/` (Vite + LitElement, port 5173) was retired in July 2026. Its runtime was ported to `app-bana-runtime/` (React) and its builder was replaced by the AI-native `app-bana-studio/` (React). Anything referring to port 5173 or `app-bana-ui/` in older docs is out of date.

---

## 3. How to Start the Application

All launch scripts live in `scripts/`. Every script has a **Windows (`.bat`)** and **macOS/Linux (`.sh`)** version. Each `start-*` script is idempotent — it stops any existing instance, ensures its dependencies, builds if needed, then launches. Scripts are location-aware, so they work regardless of the current directory.

### Start everything (recommended):

| Windows | macOS / Linux |
|---------|---------------|
| `.\scripts\start-everything.bat` | `./scripts/start-everything.sh` |

### Restart a single module:

| Module | Windows | macOS / Linux | Port |
|--------|---------|---------------|------|
| AI Builder | `.\scripts\start-ai-builder.bat` | `./scripts/start-ai-builder.sh` | 8081 |
| Backend | `.\scripts\start-backend.bat` | `./scripts/start-backend.sh` | 8080 |
| Studio | `.\scripts\start-studio.bat` | `./scripts/start-studio.sh` | 5174 |
| Runtime | `.\scripts\start-runtime.bat` | `./scripts/start-runtime.sh` | 5175 |

### What each module script does

Every `start-*` script self-contains these steps:
1. **Stop** any process already bound to its port.
2. **Ensure dependencies** are up — Docker containers (Postgres, Qdrant), Node modules, `OPENAI_API_KEY`.
3. **Build** its Maven module (or `pnpm install`) if artifacts / `node_modules` are missing.
4. **Launch** the service in the foreground.

### What `start-everything` does

Delegates to the four module scripts in the correct order and waits for each port to open before proceeding:
1. AI Builder (port 8081) — also brings up Qdrant + Postgres.
2. Backend (port 8080).
3. Studio (port 5174).
4. Runtime (port 5175).

On Windows each service opens in a new terminal window. On macOS/Linux each service is backgrounded with logs written to `dev-logs/*.log` (folder is gitignored).

### Service Ports Summary

| Service | Port | URL |
|---------|------|-----|
| AI Builder | 8081 | http://localhost:8081/health |
| Core API Backend | 8080 | http://localhost:8080/health |
| Studio (React) | 5174 | http://localhost:5174 |
| Runtime (React) | 5175 | http://localhost:5175/run/{tenantId}/{appId} |
| Qdrant Vector DB | 6333 | http://localhost:6333/dashboard |

### After Changes to Java Code:
Always restart with `.\scripts\start-everything.bat` (or the module-specific script) to trigger Maven recompile and clean restart.

### After Changes to Frontend Code:
Vite HMR handles studio + runtime automatically. Note that `@appbana/shared` is consumed **as source** (`"main": "./src/index.ts"`), so edits there hot-reload without an intermediate build step.

---

## 4. Key Configuration Files

### `config.json` (root directory)
The single source of truth for all runtime configuration:

```json
{
  "jdbcUrl": "jdbc:postgresql://localhost:5432/appbana",
  "username": "appbana",
  "password": "appbana_dev_2026",
  "driver": "org.postgresql.Driver",
  "name": "default",
  "aiProvider": "openai",
  "openaiApiKey": "YOUR_OPENAI_API_KEY_HERE",
  "openaiModel": "gpt-4o-mini",
  "anthropicApiKey": null,
  "anthropicModel": "claude-3-5-sonnet-20241022",
  "ollamaUrl": "http://localhost:11434",
  "ollamaModel": "llama3.1",
  "flywayCleanOnStart": false
}
```

> [!WARNING]
> `flywayCleanOnStart: false` must stay `false` in dev. Setting it to `true` drops and recreates ALL database tables, destroying all user data.

### Database
- **PostgreSQL 16** running locally
- DB name: `appbana`
- User: `appbana` / Password: `appbana_dev_2026`
- Schema migrations use **two different tools**, one per Java module — do not assume:
  - `app-bana-service` â†’ **Liquibase**. Changesets in `app-bana-service/src/main/resources/db/changelog/db.changelog-master.xml`, SQL in `.../db/migration/` (`V0`, `V1`, â€¦). Run from `ApiServer.startJdk()`.
  - `ai-builder` â†’ **Flyway**. SQL in `ai-builder/src/main/resources/db/migration/` (`V001`, `V002`, â€¦).
  - Both share the path suffix `db/migration/` and both share the `appbana` database. The zero-padded (`V001`) vs unpadded (`V1`) numbering is the quickest way to tell which module a file belongs to.

---

## 5. Architecture Deep Dive

### Metadata-Driven Flow

```
User (natural language)
        ↓
  app-bana-studio (port 5174)
  [ChatPane → SSE stream → AI Builder]
        ↓
  ai-builder (port 8081)
  [AiAgent → Tools → scaffold_app]
        ↓
  app-bana-service (port 8080)
  [SchemaManager creates table]
  [GenericEntityRoutes auto-generates CRUD API]
        ↓
  app-bana-runtime (port 5175)
  [Renderer + StudioTableLive render pages from schema metadata]
```

### Multi-Tenant Isolation
Every app created in AppBana is completely isolated at the database level. The `SchemaManager` prefixes every table name:

```
Physical table name = app_{envPrefix}{tenantId}_{appId}_{entityName}
```

**Example:**
- `tenantId = default`
- `appId = 7495460a-bc30-40e9-8235-9ddb08720b2a`
- `entityName = Customer`
- **Physical PostgreSQL table = `APP_DEFAULT_7495460A_BC30_40E9_8235_9DDB08720B2A_CUSTOMER`**

The schema key stored in `appbana_schemas` table also follows:
```
schema key = {tenantId}_{appId}_{entityName}
```

---

## 6. The AI Builder Engine (`ai-builder/`)

### The Agent Loop (`AiAgent.java`)

The agent implements a **Think â†’ Act â†’ Observe** loop:

1. **THINK**: `buildAgentPrompt()` constructs a rich prompt including:
   - System instructions (`AdvancedPromptEngine.java`)
   - Available tools (from `ToolRegistry`)
   - Current execution context (appId, tenantId, userId)
   - Conversation history (last 5 messages)
   - Execution progress (tool results from prior iterations)
   
2. **ACT**: LLM returns a JSON response with either `tool_calls` or `final_answer`

3. **OBSERVE**: Tool results are collected and fed back into next iteration

### The Two Phases (CRITICAL WORKFLOW)

The agent enforces a mandatory two-phase app creation process:

**Phase 1 â€” Specification (TALK, no tools):**
- User describes what they want ("I want a spice selling app")
- Agent responds with plain English business description (NO technical terms)
- Asks: "Does this match? Say **Yes, let's build it!** when ready"

**Phase 2 â€” Execution (ACT, tools):**
- User says "yes" / "build it" / "proceed"
- Agent calls `scaffold_app` ONCE with the complete specification
- **NEVER** use `create_entity` or `generate_page` individually for new apps

### Important Agent Configuration

```java
// AiAgent.java:174 (processWithStream) and :546 (process) — the cap is duplicated, change both
int effectiveMaxIterations = Math.min(config.getMaxIterations(), 10);
```
- Maximum **10** iterations per request (cost control)
- If every tool call in an iteration fails, the agent increments `consecutiveFailures` and aborts at
  **3 consecutive** all-failed iterations — it does not "retry once more" and stop

> [!WARNING]
> `think()` runs **once per iteration**, so anything paid placed inside it is bought up to 10 times
> for one identical result. `userMessage` never changes across the loop. Per-request retrieval
> (e.g. the domain-blueprint RAG lookup) is computed in `process`/`processWithStream` *after* the
> pattern-match short-circuit and passed into `think()`. See C4.4b in
> [`docs/planning/MAKER_CHECKER_PLAN.md`](../docs/planning/MAKER_CHECKER_PLAN.md).

### Tool Execution
- **Sequential** (mandatory): `create_entity`, `generate_page`, `create_app`
- **Parallel** (virtual threads): Everything else including `generate_mock_data`

---

## 7. Agent Tool System

All tools live in `ai-builder/src/main/java/com/appbana/ai/agent/tool/` and implement the `Tool` interface.

### Registered Tools (in `AiServer.java`)

| Tool Name | Class | Purpose |
|-----------|-------|---------|
| `scaffold_app` | `ScaffoldAppTool` | **Primary tool** — Creates entire app (App + Entities + Pages) in one shot. Each entity accepts `approvalRequired: boolean` (Phase C4) |
| `create_app` | `CreateAppTool` | Creates just the app shell |
| `create_entity` | `CreateEntityTool` | Creates a single entity/table. Accepts `approvalRequired: boolean` (Phase C4) |
| `generate_page` | `GeneratePageTool` | Generates a UI page for an entity |
| `list_apps` | `ListAppsTool` | Lists all apps for this tenant |
| `list_entities` | `ListEntitiesTool` | Lists entities in an app |
| `get_entity_details` | `GetEntityDetailsTool` | Gets schema fields for an entity |
| `list_pages` | `ListPagesTool` | Lists pages in an app |
| `generate_mock_data` | `GenerateMockDataTool` | Seeds database with AI-generated records |
| `deploy_app` | `DeployAppTool` | Deploys/publishes an app |
| `list_workflows` | `ListWorkflowsTool` | Lists automation workflows |
| `search_knowledge` | `SearchKnowledgeTool` | Searches RAG knowledge base |
| `batch_update_entities` | `BatchUpdateEntitiesTool` | Bulk updates entity schemas |

### Creating a New Tool (Guidelines)

1. Implement `Tool` interface with `getName()`, `getDescription()`, `getParameterSchema()`, `execute()`
2. Register it in `AiServer.java` inside `toolRegistry.register(new MyTool(backendBaseUrl))`
3. Add clear JSON schema for parameters â€” the LLM reads this to know how to call the tool
4. Always handle `ConnectException` â€” the ai-builder and app-bana-service are separate processes
5. Return `ToolResult.error(name, message)` on failure â€” never throw exceptions silently
> [!WARNING]
> **Adding a parameter to a tool's JSON schema does not make it reach the backend.** A tool typically has a
> separate method that builds the HTTP body (e.g. `CreateEntityTool.buildEntityMetadata`) by copying named
> keys one at a time — anything not explicitly copied there is dropped with no error. Task C4.1 exists
> because `approvalRequired` was accepted, read by `SchemaEnricher` (which injected the 8 approval columns,
> so the table came out approval-shaped) and then dropped before the `/schema` POST, leaving
> `approvalRequired=false` on the schema record that all 13 backend guards actually branch on. The entity
> looked approval-enabled and behaved as if it were not. When you add a tool parameter, assert end-to-end
> that it survives into the request body — a schema-only test proves nothing.

> [!IMPORTANT]
> **`SchemaManager` — not `SchemaEnricher` — owns approval-column injection (Task C4.6).** Setting
> `approvalRequired: true` on a schema is the *only* thing a caller must do; `SchemaManager` materialises
> the eight physical approval columns on create, on alter, and in the dry-run preview, deduping against
> whatever the schema already declares. Do not re-add injection on the ai-builder side: it was reachable
> from only one of the four writers of the flag, which is how `create_entity` came to emit entities that
> accept records and then 500 on the first workflow action.
>
> Two consequences worth knowing before you touch this area:
> - The eight columns are **physical-only** — deliberately *not* members of `EntitySchema.getFields()`.
>   Read paths get them from `EntityCrudService.getQueryableFields()`; the insert path writes them through
>   a separate guarded pass. Never merge `ApprovalColumns.asFields()` into an insert/update/validation
>   field list, or clients could write `approval_status` directly through the generic entity API.
> - Never answer "does this entity have an approval workflow?" by probing `getFields()` for
>   `approval_parent_id` or friends. `schema.isApprovalRequired()` is the authority. Two call sites did
>   the former and silently downgraded a new DRAFT revision into an in-place edit of a live APPROVED row.
>
> When writing tests, set the flag and let `SchemaManager` create the columns. Hand-declaring them in a
> fixture is what kept this defect invisible across 281 green backend tests.
---

## 8. Critical Rules â€” Multi-Tenant Entity Endpoints

> [!CAUTION]
> This is the #1 source of bugs. Read carefully.

### âŒ WRONG (will return 404):
```java
String url = baseUrl + "/api/Customer/batch";
String url = baseUrl + "/api/Spice/batch";
```

### âœ… CORRECT (how `GenerateMockDataTool` does it):
```java
String appId = context.appId();
String tenantId = context.tenantId();

String tenantPart = (tenantId != null && !tenantId.isEmpty()) ? tenantId : "default";
String targetSchemaId = tenantPart + "_" + appId + "_" + entityName;

String url = String.format("%s/api/%s/batch", baseUrl, targetSchemaId);
// Result: http://localhost:8080/api/default_7495460a-bc30-40e9-8235-9ddb08720b2a_Customer/batch
```

### Why this exists
`SchemaManager.loadSchema()` looks up entities by key, not by raw name. The key **must** include the tenant and app prefix to uniquely identify it across multiple tenants/apps.

---

## 9. Backend API Reference

### Studio (App Management)
```
GET    /appbana-studio/{tenantId}/apps              â†’ List all apps
GET    /appbana-studio/{tenantId}/apps/{appId}      â†’ Get app with entities and pages
POST   /appbana-studio/{tenantId}/apps              â†’ Create new app
PUT    /appbana-studio/{tenantId}/apps/{appId}      â†’ Update app
DELETE /appbana-studio/{tenantId}/apps/{appId}      â†’ Delete app
```

### Schema Management
```
GET    /schema                  â†’ List schema names
GET    /schema/{name}           â†’ Get schema definition
POST   /schema                  â†’ Create/update schema (or preview with ?preview=true)
DELETE /schema/{name}           â†’ Delete schema
```

### Dynamic Entity CRUD (auto-generated per schema)
```
GET    /api/{entity}            â†’ Query (pagination, search, filters, projection, sort)
GET    /api/{entity}/{id}       â†’ Get single record
POST   /api/{entity}            â†’ Insert record
POST   /api/{entity}/batch      â†’ Insert multiple records (JSON array)
PUT    /api/{entity}/{id}       â†’ Update record
DELETE /api/{entity}/{id}       â†’ Delete record
```

**Remember:** `{entity}` must follow the multi-tenant format: `{tenantId}_{appId}_{entityName}`

### Query Parameters
```
?limit=50&offset=0             → Pagination (max limit 500)
?q=John                        → Full-text-ish search across text columns
?filter=name:John,status:active → Field-level filters (comma-separated). An unrecognized field name
                                  400s — see Review #5/#6/#7 — EXCEPT the 8 injected approval columns
                                  (approval_status, submitted_by, ...), which are accepted even though
                                  they're absent from EntitySchema.getFields(), but ONLY when the entity
                                  has approvalRequired: true. The same exemption (and the same 400 for a
                                  genuine typo) applies to sort=, fields= and groupBy= — see Review #7.
?filter=name:=John             → Exact match — leading '=' on the value. Default (no '=') is
                                  a case-insensitive substring LIKE, which over-matches identity
                                  fields (submitted_by:bob would also match "bobby"). approval_status
                                  goes through the same DRAFT/PENDING/APPROVED/REJECTED validation
                                  and checker-only-PENDING gate either way — an invalid value's 400
                                  names whichever of filter=approval_status: / _approvalStatus= the
                                  caller actually used (Review #7 D9).
?filter=price:100..300         → Range filter — "min..max" (double-dot separator), parsed by
                                  EntityCrudService.parseRange and only accepted for orderable column
                                  kinds (integer/bigint/decimal/timestamp/reference). Either bound may be
                                  omitted for an open-ended range (`price:100..` or `price:..300`); both
                                  empty (`price:..`) is a 400, same as any other empty filter value. The
                                  runtime's TableHeader.tsx / entity-query.ts (`range()`) is the canonical
                                  client-side helper for building this — route any new range-capable
                                  filter UI through it. A comma inside either bound is rejected, not
                                  silently truncated, same hazard as a plain filter value (see below).
?fields=name,email             → Column projection. Omitting fields= returns only the declared schema
                                  fields — approval columns are opt-in via an explicit fields= only, so
                                  they never leak into a caller that didn't ask for them.
?sort=-name,+age               → Sorting. NOT `name:asc,age:desc` — that colon syntax is silently
                                  ignored (a field literally named "name:asc" is looked up, missed, and
                                  dropped) and was wrong in this doc for six rounds (Review #7 D10). No
                                  prefix or a leading `+` means ascending; a leading `-` means descending.
?count=true                    → Count only
?groupBy=status                → Group rows by one column. An unrecognized column 400s (Review #7 D7) —
                                  it used to silently bucket every row into one group with an empty key.
                                  Review #8 made groupBy= itself trigger the advanced-query path: a bare
                                  `?groupBy=X` with no other param used to fall through to the simple
                                  SELECT-all branch and be silently ignored entirely (no `groups`, no
                                  `groupCounts`, no error). It now always returns the paginated shape
                                  (`{rows, total, limit: 50 by default, offset, ...}` plus `groupCounts`)
                                  instead of a bare array — a public response-shape change, and a silent
                                  50-row cap on `rows` if the caller doesn't also pass `limit=`. Per-page
                                  `groups` is populated only when the grouped column is actually present on
                                  the returned rows (a declared field, or one explicitly requested via
                                  fields=); for an approval column with no explicit fields=, `groups` is
                                  omitted rather than fabricated and `groupCounts` (whole-dataset, SQL,
                                  projection-independent) is the only source of truth (Review #8 High).
                                  `groupBy=` is only recognized on the top-level `/api/{entity}` route —
                                  the app-scoped and env-scoped list routes accept and silently ignore it.
?_approvalStatus=PENDING       -> Approval-state filter (PENDING is checker-only; 403 otherwise).
                                  Conflicts with a simultaneous filter=approval_status:=X for a
                                  different X 400 instead of silently picking one (Review #7 D8).
```

> [!WARNING]
> A bare `GET /api/{entity}` with **no query parameters at all** takes a different code path
> (`EntityCrudService.listAll()`) than every other request shape above. Review #7's default-projection
> leak guardrail (approval columns excluded unless explicitly requested via `fields=`) was enforced in
> `listAdvanced()`'s default path but not here — `listAll()` used to be a bare `SELECT *`, returning every
> physical column including all 8 approval columns with raw uppercase DB keys, on any approval-required
> entity. Fixed in Review #9 to project `schema.getFields()` explicitly, same as `listAdvanced()`'s default
> projection. **Lesson**: a parameter-by-parameter review sweep has no cell for "the caller sent nothing at
> all" — that code path must be probed as its own case, not assumed to share behavior with the parameterized
> paths just because it lives in the same route handler.

> [!WARNING]
> The handler reads a **fixed allowlist** of query params (`limit`, `offset`, `q`, `fields`, `sort`, `filter`,
> `count`, `groupBy`, `_approvalStatus`). A bare field-level param — `?status=active`, `?submitted_by=alice` —
> is **silently ignored**; the response is 200 with an *unfiltered* body, not an error. Field filters must go
> through `filter=`. There is no `?_fields=`, `?_sort=`, `?_count=`, or `?name=value` shorthand, despite older
> docs and some now-fixed callers assuming otherwise (C3.9/C3.10). The runtime's `entity-query.ts`
> (`toEntityQueryParams`) is the canonical client-side helper — route any new list-fetching UI through it
> instead of hand-building query params.

> [!WARNING]
> **`filter=` values must be RFC 3986 percent-encoded, not form-encoded.** The server decodes the query
> string exactly once (`URI.getQuery()`), which treats a literal `+` as a literal `+`, not a space. A space
> in a filter value MUST be sent as `%20`. Do **not** build this query string with a bare
> `URLSearchParams(...).toString()` — its default `application/x-www-form-urlencoded` output encodes a space
> as `+`, which the server will store as a literal `+`, not decode back to a space, silently corrupting the
> filter (zero matches, no error). `app-bana-shared/api-client.ts`'s `fetchEntityRows()` post-processes with
> `.replace(/\+/g, '%20')` for exactly this reason — copy that pattern (or route through `toEntityQueryParams`)
> for any new caller that builds a `filter=` string with `URLSearchParams`.


### Approval Endpoints (maker-checker)
```
POST   /api/tenants/{tenantId}/apps/{appId}/entities/{entity}/records/{id}/submit
POST   /api/tenants/{tenantId}/apps/{appId}/entities/{entity}/records/{id}/approve
POST   /api/tenants/{tenantId}/apps/{appId}/entities/{entity}/records/{id}/reject    (body: {"reason": "..."} — required)
GET    /api/tenants/{tenantId}/apps/{appId}/entities/{entity}/approvals/pending      (checker-only queue)
GET    /api/tenants/{tenantId}/apps/{appId}/entities/{entity}/records/{id}/approvals/audit  (most-recent-first)
```

Status codes are deliberately distinct — do not collapse them:

| Code | Meaning |
|------|---------|
| 401 | No valid session |
| 403 | Authorization failure — missing MAKER/CHECKER role, or separation-of-duties violation (maker approving own row) |
| **409** | **Workflow conflict** — record is not in the required state (e.g. approving a non-`PENDING` row, submitting an already-`PENDING` row, losing an approve/reject race). Thrown as `ApprovalConflictException` |
| 400 | Malformed request — record not found, missing rejection reason |

### AI Endpoints
```
POST   /api/ai/chat             â†’ General chat
POST   /api/ai/chat/agent       â†’ Agent loop (full tool execution)
GET    /api/ai/chat/history     â†’ Conversation history
GET    /api/ai/chat/sessions    â†’ Past sessions
```

---

## 10. Frontend Architecture

### Technology Stack
- **Framework**: React 18 + TypeScript (LitElement was retired with `app-bana-ui/` — see §2)
- **Build**: Vite 5 · **Styling**: Tailwind · **Studio state**: Zustand
- **Testing**: Vitest, **with no DOM shim**. There is no `vitest.config.ts` and no `jsdom` /
  `happy-dom` / Testing Library dependency; component tests render with `react-dom/server`'s
  `renderToStaticMarkup` and assert on the emitted markup. A test that needs to click, focus, or
  observe an effect cannot be written this way — that behaviour is covered by Playwright in `e2e/`,
  or not at all. See the limitation note below.

### Key Source Files

| File | Purpose |
|------|---------|
| [`app-bana-runtime/src/runtime/StudioTableLive.tsx`](../app-bana-runtime/src/runtime/StudioTableLive.tsx) | Live entity table — fetch, per-column filters, sort, pagination, row actions |
| [`app-bana-runtime/src/runtime/TableHeader.tsx`](../app-bana-runtime/src/runtime/TableHeader.tsx) | Sort-toggle header row + the per-column filter control row |
| [`app-bana-runtime/src/runtime/useEntityRows.ts`](../app-bana-runtime/src/runtime/useEntityRows.ts) | Paginated row fetching + row-lifecycle auto-refresh |
| [`app-bana-runtime/src/runtime/entity-query.ts`](../app-bana-runtime/src/runtime/entity-query.ts) | Canonical `{field: value}` → `GET /api/{entity}` query-param translator |
| [`app-bana-runtime/src/runtime/Renderer.tsx`](../app-bana-runtime/src/runtime/Renderer.tsx) | Walks `PageMeta` → React tree |
| [`app-bana-runtime/src/runtime/AppRuntimeShell.tsx`](../app-bana-runtime/src/runtime/AppRuntimeShell.tsx) | Shell — sidebar, appbar, login, page slot |
| [`app-bana-shared/src/api-client.ts`](../app-bana-shared/src/api-client.ts) | All frontend API access (`authedFetch`, `fetchEntityRows`, …) |

### `StudioTableLive.tsx` — critical knowledge

The live data table rendered in deployed apps. It reads `node.props.entityName` (the multi-tenant
entity key), `node.props.fields` and `node.props.tableColumns` — all sourced from the backend entity
schema. **If you change how schemas are saved, verify `tableColumns` still arrives**, or data
rendering breaks silently.

Every displayed column **except the approval-status column** is filterable, and the same set is
sortable one column at a time — both **server-side**. The table never filters or sorts the current
page client-side, because that page is one `limit`/`offset` slice of a dataset that may be
arbitrarily large. (Approval status is excluded deliberately: it has its own `_approvalStatus=`
parameter and system views — see §9.) Filter state is debounced 400 ms
([`useDebouncedValue.ts`](../app-bana-runtime/src/runtime/useDebouncedValue.ts)) and merged with any
page-level filters before being handed to `toEntityQueryParams`. The wire format for each control is
in §9.

> [!WARNING]
> **`StudioTableLive` swaps its entire `<table>` subtree for `<TableSkeleton>` on every load**
> (`{loading && <TableSkeleton/>}` / `{!loading && rows.length > 0 && <table>…}`). Because a filter or
> sort change sets `loading` true→false, this **unmounts and remounts `TableHeader` and every filter
> control on every keystroke-batch** — not just on first load. Any component under that boundary that
> holds edit state in `useState` will have it silently reset mid-interaction.
>
> This is not hypothetical: the column-filter range inputs originally kept their min/max in local
> `useState`, and each remount reset that draft to `''`, so the *next* bound's `onChange` read the
> other bound through a stale closure over the reset value and dropped whatever the user had just
> typed. The visible symptom was a filter that would not clear. **Rule:** anything rendered under
> `StudioTableLive`'s loading boundary must derive its displayed value from props on every render, not
> from local state.

> [!IMPORTANT]
> **`StudioTableLive.tsx` has no unit test, and cannot have a meaningful one** under the no-DOM-shim
> setup above — it is a fetch-driven, effect-heavy, interaction-heavy component. The remount defect
> above passed 276 green runtime tests and a full backend suite; it was only ever going to be caught
> by driving the real browser. Changes to this file must be verified in the running runtime (5175),
> not by unit tests alone.

### Adding a New Field Type
1. Add the SQL type mapping in `SchemaManager.sqlType()` (Java) and classify it in
   `SchemaManager.classifyFieldType()` — the latter decides indexability and whether `filter=`
   range queries are allowed (§9, §11)
2. Add the cell renderer in `StudioTableLive.tsx` and the filter control in `TableHeader.tsx`
   (`ColumnFilterControl`) — a type with no branch there falls through to a plain text filter
3. Add the input component in `Renderer.tsx`'s field-renderer cases

---

## 11. Database & Schema Management

### EntitySchema Field Types (valid values)
```
text        â†’ VARCHAR(255)
longtext    â†’ TEXT
number      â†’ INTEGER
decimal     â†’ NUMERIC(19,4)     â† Use for money/prices, NOT "currency" or "float"
boolean     â†’ BOOLEAN
date        â†’ TIMESTAMP (date only)
datetime    â†’ TIMESTAMP
email       â†’ VARCHAR(255) (no built-in email-format validation — set `pattern` explicitly if you need one)
phone       â†’ VARCHAR(255) (length is only honoured for "string"/"varchar"; other STRING-kind aliases are fixed at 255)
status      â†’ VARCHAR(255) with options[]
reference   â†’ INTEGER (H4 hardening — must match the parent's PK type for a real FOREIGN KEY)
```

> [!WARNING]
> Never use types like `money`, `currency`, `float`, or `string` in schemas. These are not mapped and will default to `VARCHAR(255)` silently.

### Schema Validation Rules
- Every entity MUST have at least one Primary Key field with `autoIncrement: true`
- Field names must be snake_case (e.g., `first_name`, NOT `firstName`)
- Relationship fields must include `referenceEntity` (e.g., `{"type": "reference", "referenceEntity": "Customer"}`)
- Never use regex patterns for human name fields (names contain spaces, hyphens, etc.)

### Migration Strategy
AppBana uses **safe, non-destructive migrations**:
- New fields â†’ `ALTER TABLE ADD COLUMN`
- Renamed fields â†’ `ALTER TABLE RENAME COLUMN` (tracked via `existingName` property)
- No production drops â€” data is preserved

### Automatic indexing (`SchemaManager.syncIndexes`)

Every `ensureTable` call — create *and* the self-heal path on an existing table — also syncs indexes
for the entity, so the per-column filter/sort UI (§10) and the approval queues stay index-served as
tenant tables grow. Two families, both `IF NOT EXISTS`, so re-running is cheap:

| Index | On | Serves |
|---|---|---|
| B-tree | every non-PK, non-`FILE` column (plus the 8 approval columns when `approvalRequired`) | `=`, `filter=col:min..max` ranges, `ORDER BY`, FK/status lookups |
| GIN `gin_trgm_ops` | STRING/TEXT-kind columns, **Postgres only** | the default substring `ILIKE '%value%'` filter, which a B-tree cannot serve at all |

Three things to know before changing this:

1. **It needs the `pg_trgm` extension.** `syncIndexes` issues `CREATE EXTENSION IF NOT EXISTS pg_trgm`
   first. If the DB user lacks rights, it logs `[INDEX] Could not enable pg_trgm extension` and skips
   **only** the trigram family — B-trees still get created and everything still works, just with
   sequential scans for substring filters. Index failures are logged and swallowed by design: an
   indexing failure is a performance problem, never a correctness one, and must not fail a schema save.
2. **Index names end in a short SHA-256 hash of `table.column.kind`** (after a truncated
   `IDX_<col>_<kind>` label), not a naive concatenate-then-truncate. Physical table names already run
   near Postgres's 63-char identifier limit (§5's UUID-heavy naming), so plain truncation makes two
   columns collide — and `CREATE INDEX IF NOT EXISTS` treats "that name exists" as success regardless
   of what it actually indexes, silently leaving one column unindexed.
3. **Deliberately not `CREATE INDEX CONCURRENTLY`** — that cannot run inside a transaction, which does
   not fit the `Statement`-per-DDL pattern the rest of `ensureTable` uses. Revisit if a very large
   tenant table ever needs altering in place.

**Liquibase changesets** (`app-bana-service` only — `ai-builder` uses Flyway, see §4) live in
`app-bana-service/src/main/resources/db/changelog/` and run from `ApiServer.startJdk()` *before* any
service initialises. Two rules:

1. **Never edit a changeset that has already been applied.** Liquibase checksums each one; editing it
   breaks every existing database. Add a new changeset instead.
2. **The chain must migrate an empty database.** A changeset may only reference objects created by an
   earlier changeset — never one created lazily at runtime by Java. `V0__bootstrap_meta_tables.sql`
   exists because V10 violated this and no fresh environment could be provisioned. Verify by pointing
   `config.json` at a brand-new database and running the suite.

---

## 12. Active Work & Known Issues

### 🚧 IN PROGRESS: AI-Native UI Rebuild (Primary Initiative)

**Primary reference:** [`docs/planning/AI_NATIVE_UI_REBUILD_PLAN.md`](../docs/planning/AI_NATIVE_UI_REBUILD_PLAN.md)

Rebuild AppBana Studio as an AI-native frontend. Chat drives everything — no canvas, no palette, no property inspector. Segregate the current monolithic [`app-bana-ui/`](../app-bana-ui) into three pnpm workspace packages:

| Package | Purpose | Port |
|---|---|---|
| `app-bana-shared` | Types + api client + postMessage schema + app-context resolver | — |
| `app-bana-studio` | AI-native builder (streaming chat + tool cards + preview iframe + data drawer) | 5174 |
| `app-bana-runtime` | Standalone renderer for deployed apps (own login, tenant-branded) | 5175 |

**Stages:** 0 (backend prep — SSE, branding, app-context) → 1 (workspace + Studio MVP) → 2 (standalone runtime) → 3 (studio v1.1) → 4 (retire old UI) → 5 (subdomain deploy) → 6 (select-and-instruct UX).

**Locked stack (do not change without approval):**
- Vite 5 + React 18 + TypeScript + Tailwind + shadcn/ui + Zustand
- pnpm workspaces
- Native `fetch` + `ReadableStream` for streaming (Vercel AI SDK is **not** used — custom event shape)
- `postMessage` handshake for studio→runtime token (NOT URL hash — security)

**Backend additions in Stage 0 (in scope, do not treat as "no backend changes"):**
- New endpoint `POST /api/ai/chat/agent/stream` (SSE with events `token`, `tool_call_start`, `tool_call_end`, `state`, `done`)
- New endpoint `GET /api/tenants/{tenantId}/branding` (public, pre-login)
- New endpoint `GET /api/app-context` (subdomain-ready)
- Liquibase changeset for `tenants` branding columns

**When working on this initiative, always consult the plan doc first.** Sections 2 (Monorepo Structure) and 3 (How to Start) of this instructions file describe the **pre-rebuild** layout and will be rewritten at the end of Stage 1. Until then, treat the plan doc as authoritative for the new package structure.

### 🚧 In Progress: DialogueManager Integration (Story 3.1)
**File**: `ai-builder/src/main/java/com/appbana/ai/dialogue/DialogueManager.java`

**Goal**: Enforce strict conversation state transitions in `AiChatController` using a Java-level state machine instead of relying solely on LLM prompt engineering.

**Planned States**:
```java
INITIAL â†’ GATHERING_INFO â†’ CONFIRMING_DETAILS â†’ CREATING â†’ COMPLETED
```

**Impact**: The `AiAgent.buildAgentPrompt()` will dynamically expose different tools based on the active `ConversationState` (e.g., only show `scaffold_app` when in `CREATING` state).

**Files to modify**:
- `DialogueManager.java` â€” implement LLM-based intent classification
- `AiChatController.java` â€” integrate state checks before delegating to `AiAgent`
- `AiAgent.java` â€” inject state into `buildAgentPrompt()`

### âœ… Recently Fixed
- **Mock data 404 bug**: `GenerateMockDataTool` now correctly prefixes entity URLs with `tenantId_appId_`
- **Boot race condition**: `start-everything.bat` now kills all stale Java/Node processes before starting
- **Scaffold entity fields**: `ScaffoldAppTool` now propagates `entityFields` to `GeneratePageTool` for `tableProps`
- **Fresh-database provisioning** (`13bd762`): `appbana_schemas` / `appbana_migrations` / `appbana_audit` were created lazily by `JdbcManager.ensureMetaTable()`, which runs *after* Liquibase, so changeset V10 could never migrate an empty database. Changeset **V0** now creates them first. Never edit an already-applied changeset to fix this class of problem — it changes the checksum and breaks every existing database.
- **`ai-builder` test suite** (`100b676`): Testcontainers mapped Qdrant's HTTP port 6333 instead of the gRPC port **6334**, which `QdrantService` actually speaks. Repairing the suite exposed two production bugs: `SchemaType.valueOf` threw on the `field-type` wire value (31 of 47 schemas returned `type == null`), and user preferences were loaded onto the agent context but never referenced by `AiAgent`.

### âš ï¸ Known Limitations
- **Agent iteration limit**: Capped at 10 iterations per request. Complex multi-entity scaffolding may hit this limit.
- **Semantic cache**: Enabled by default (`SemanticCache.java`). If you see stale LLM responses, disable it temporarily for debugging.
- **Schema pluralization**: Entity names must be used exactly as saved â€” no auto-pluralization is applied.

---

## 13. Development Conventions

### Java Code Style
- **Java 21**: Use virtual threads, records, sealed classes, and switch expressions where appropriate
- **Logging**: Always use `@Slf4j` (Lombok) â€” never `System.out.println`
- **Error handling**: Catch specific exceptions, not bare `Exception`, except at controller boundaries
- **Cross-service calls**: Always wrap HTTP calls in try-catch for `ConnectException` â€” the two Java services are on different ports

### When Adding a Backend Feature
1. Add route in appropriate `*Routes.java` file
2. Implement service logic in `service/` or directly in service class
3. Add integration test in `src/test/`
4. Update `GenericEntityRoutes.java` only if it's a generic entity operation

### When Adding an AI Tool
1. Create `MyTool.java` in `ai-builder/.../agent/tool/`
2. Register in `AiServer.java`: `toolRegistry.register(new MyTool(backendBaseUrl))`
3. Keep tool descriptions concise â€” they go into the LLM context window
4. Limit batch operations to **10-20 records max** (see `GenerateMockDataTool` for reference)

### Commit Message Convention
```
feat: add GenerateMockDataTool for AI database seeding
fix: correct multi-tenant URL prefix in GenerateMockDataTool
chore: update copilot instructions with DialogueManager plans
```

### Always Commit and Push

**Always commit and push after completing a unit of work.** Do not leave finished work sitting uncommitted in the working tree.

1. Run the affected module's tests first (`mvn -pl app-bana-service test`, `pnpm test`, etc.) and confirm they pass.
2. Commit using the message convention above, with a body explaining *why* when the change is non-obvious.
3. Push to the current branch immediately — `git push origin $(git branch --show-current)`.

Exceptions (ask first, don't auto-commit):
- The user explicitly said not to commit.
- Tests are failing or the build is broken.
- The change contains secrets, credentials, or generated artifacts that belong in `.gitignore`.

Note: `RevisionFlowTest` and the other DB-backed tests need PostgreSQL running (`docker start appbana-postgres`). A "Connection to localhost:5432 refused" failure is environmental, not a code regression — but do not commit until you have actually verified against a live DB.

### Always Write Up After Acting on a Review

**Whenever you take action in response to a review or critique** (a pasted external-reviewer critique, PR feedback, an audit finding, etc.), **always end with a written summary in chat** — never just silently fix things and stop, and never respond with only a terse "done."

- Mirror the review's own structure where one exists (severity tags, finding IDs) so each point maps 1:1 to something the reviewer can check off.
- For each finding: what you verified against source (don't just trust the claim), what the fix was, how it was tested/verified, and which commit it landed in.
- Explicitly surface anything you discovered as a side effect that wasn't part of the original ask (e.g. a fix's investigation revealing a larger latent issue elsewhere) — call it out in the chat response, don't bury it only in a docs file.
- State plainly what remains open/deferred vs. fully closed.
- Docs/tracker updates are for future sessions; the chat writeup is for the person reading right now — do both, one doesn't substitute for the other.

### Backend Testing Traps (found during the tenant-isolation work, S1.9)

> [!WARNING]
> **A route can be protected by more than one independent layer — a matching status code alone never proves which one fired.** `SchemaRoutes.java`'s `GET`/`POST /schema` are gated *both* by `SessionMiddleware` (which unconditionally requires a real session — `isExcludedPath` hard-excludes `/schema` from every carve-out: "schema APIs MUST ALWAYS require session authentication") *and* by the route's own internal `hasAdmin()` check. A test that sends only a bogus `X-Session-Token` value and asserts a 401 will pass forever without ever exercising the route's own admin-token logic — `SessionMiddleware` rejects the bad session before `SchemaRoutes.java`'s code runs at all. Before trusting a security test's status-code assertion, confirm *which layer actually produced it* (e.g. by first proving the other layer would pass on its own with a real session, or by isolating the layer you mean to test) — don't assume a matching status code means the intended check ran.

> [!WARNING]
> **A new consistency/reconciliation check's first red run is more likely to be the checker's own bug than real drift in the data.** Confirmed twice while building `EstimateReconciliationTest` (`app-bana-service/src/test/java/com/appbana/server/`, S0.5): both of its first two failing runs were parser bugs, not real drift — a naive `line.split("|")` silently mis-split a cell containing escaped pipes (`` `path`\|`query`\|... ``), and a raw backtick-token comparison flagged a deliberate "(or `AlternativeFile.java`)" design note as if it were a required file. Before "fixing" whatever a brand-new check flags as wrong, dump what each side of the comparison actually parsed and eyeball it against the real source — only trust a new check once it's been proven to pass *and* to fail correctly (introduce a deliberate, known drift, confirm it's caught with the right message, then revert).

> [!WARNING]
> **Adding, deleting, or renaming a route — or adding/removing a tracker task row — breaks two mechanical doc-consistency tests that a "frozen" planning doc does NOT exempt you from.** `RouteCensusTest` (S0.3) does a live SET comparison of `Router`'s actual registrations against `TENANT_ISOLATION_SECURITY_PLAN.md`'s "S0.2 Route census" table on every run; `EstimateReconciliationTest` (S0.5) sums every task row's estimate in the tracker and cross-checks it against that same plan doc's S0–S5 summary table *and* both docs' own "Total scope" headlines (hours **and** task count). The plan doc's census carries a `[!NOTE]` saying it's "a dated snapshot, not a live document" — but that note explicitly scopes itself to the `Mw-excl.`/`Id. gate`/`T/A check` *classification columns only* ("`RouteCensusTest` only guards the route **set** ... never these classification columns"). Deleting `/api/debug/schemas` + `/api/debug/schemas/names` (S1.19) broke both tests: the census still listed the two removed rows, and the tracker's new task added 20 min nobody had propagated into the plan doc's S1 phase total or either doc's grand-total headline. Fix is mechanical, not a design decision: remove the exact table row(s) for a deleted route (add row(s) for a new one), and recompute every headline/summary-table figure the failing assertion names — don't just re-read the `[!NOTE]` and assume the whole table is exempt from every test.

---

## 14. Common Pitfalls

| Pitfall | Symptom | Fix |
|---------|---------|-----|
| Starting services individually | Port conflicts, wrong startup order | Always use `.\start-everything.bat` |
| Using raw entity name in API URL | `404 {"error":"unknown entity"}` | Prefix with `{tenantId}_{appId}_` |
| Using wrong field type in schema | Silent type mismatch / VARCHAR fallback | Only use types in the approved list (Section 11) |
| Modifying `ApiServer.java` directly | Boot failures | It's a thin wrapper â€” delegate logic to `*Routes.java` |
| Not restarting after Java changes | Old code runs silently | Always restart with `.\start-everything.bat` |
| Adding `regex` pattern to name fields | Validation failures for normal names | Set `pattern: null` for human-readable name fields |
| Calling `create_entity` for new apps | Mismatched entities without pages | Use `scaffold_app` â€” it creates App + Entities + Pages atomically |
| LLM returns old cached response | Debugging frustration | Disable `SemanticCache` or call `semanticCache.clear()` |

---

## 15. Two-Agent Review Loop (`loop_status.json`)

Some work in this repo runs as a two-agent loop: a **developer** agent implements exactly one task,
then hands off to a **reviewer** agent, which verifies it and hands back. Shared state lives in
`loop_status.json` at the repo root.

> [!IMPORTANT]
> `loop_status.json` is **local, ephemeral, and gitignored — never commit it.** It is per-machine
> turn-taking state, not project history. Committing a half-finished handoff produces merge conflicts
> on a file that has no meaningful merge resolution. Durable history belongs in
> `docs/planning/TENANT_ISOLATION_IMPLEMENTATION_TASKS.md` (or the equivalent tracker), not here.

### Schema

| Field | Meaning |
|---|---|
| `active_agent` | Whose turn it is: `"developer"` or `"reviewer"`. **Roles, never model names** — the assignment changes, the roles don't |
| `status` | `"in_progress"` · `"ready_for_review"` · `"idle"` |
| `current_task` | Task id + one-line title, e.g. `"S1.11 - cross-tenant capstone tests"` |
| `task_summary` | What was done, what was independently verified, the commit hash |
| `review_comments` | Array of `{severity, item, detail}` for the current round. Severities in use: `verdict`, `blocker`, `high`, `medium`, `nit`, `verified-live`, `praise`, `housekeeping`, `next` |
| `history` | Append-only log of `{round, agent, task, commit, outcome}` |

### Turn-taking

- **Developer finishes a task** → set `active_agent: "reviewer"`, `status: "ready_for_review"`, fill
  `current_task` + `task_summary`, append a `history` entry. Clear `review_comments` only when
  *starting* the next task, after its previous entries have been actioned.
- **Reviewer finishes a review** → write findings to `review_comments`, set
  `active_agent: "developer"`, `status: "idle"`, append a `history` entry.
- **`status: "idle"` with a non-empty `review_comments` means:** read the comments, action them, then
  begin the item tagged `severity: "next"`.

The prompt for either side is just the filename: `#loop_status.json`. Everything needed to act should
already be in the file — if it isn't, that's a defect in the handoff, not something to ask about.

### Reviewer protocol

The checks below are what actually found defects during the tenant-isolation work; they are not
generic advice. Blockers found this way include a cross-tenant app-creation hole, an admin gate that
was inert under the shipped config, and a guard that rejected every real client.

1. **Absence-census first.** Enumerate every route/call-site in each touched file and ask which ones
   *lack* the new guard. Auditing the sites that have it can never find the one that doesn't.
2. **Check guard ordering against side effects.** A guard that runs after the write still writes.
3. **Break the guard on purpose.** Neuter it, confirm the tests fail *with the right message*, revert.
   A guard never observed failing is not verified. For a *deletion*, re-register a stub at the deleted
   path — otherwise a test asserting 404 passes trivially forever.
4. **Verify against `config.json` as shipped** (`adminToken: null`). A check wrapped in
   `if (authEnabled(cfg))` reads correctly and does nothing in the default configuration.
5. **Live-probe where a real client exists**; say plainly when one doesn't rather than manufacturing a
   request. A negative result is a valid result.
6. **Restore the environment** and report it as *checked*, not remembered — stop servers you started,
   revert perturbations, confirm `git status` is clean, name any fixture data left behind.

---

*Last updated: April 2026 | Maintained by: AppBana Development Team*
*For session history and task tracking, see `docs/ACTIVE_TASKS.md` and `docs/session_summary.md`*

