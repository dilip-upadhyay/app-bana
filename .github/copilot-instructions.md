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
4. Renders UI pages (via LitElement web components)

**Core Principle:** A single schema definition drives the entire stack â€” database, API, and UI are all metadata-driven.

---

## 2. Monorepo Structure

```
app-bana/
â”œâ”€â”€ ai-builder/                  â† AI LLM engine (port 8081)
â”‚   â””â”€â”€ src/main/java/com/appbana/ai/
â”‚       â”œâ”€â”€ agent/               â† AiAgent.java â€” the Think/Act/Observe loop
â”‚       â”‚   â””â”€â”€ tool/            â† All agent tools (scaffold, mock data, etc.)
â”‚       â”œâ”€â”€ api/                 â† REST endpoints (AiChatController.java)
â”‚       â”œâ”€â”€ dialogue/            â† DialogueManager.java â€” conversation state machine
â”‚       â”œâ”€â”€ llm/                 â† AdvancedPromptEngine.java, OpenAiLlmService.java
â”‚       â”œâ”€â”€ rag/                 â† ConversationMemory.java, Qdrant vector store
â”‚       â”œâ”€â”€ learning/            â† UserPreferenceEngine.java
â”‚       â”œâ”€â”€ optimization/        â† DirectAnswerService.java, SemanticCache.java
â”‚       â””â”€â”€ server/              â† AiServer.java, ToolRegistry.java
â”‚
â”œâ”€â”€ app-bana-service/            â† Core backend API (port 8080)
â”‚   â””â”€â”€ src/main/java/com/appbana/
â”‚       â”œâ”€â”€ ApiServer.java       â† HTTP server entry point
â”‚       â”œâ”€â”€ SchemaManager.java   â† CREATE TABLE, migrations, multi-tenant isolation
â”‚       â”œâ”€â”€ JdbcManager.java     â† Database connection pools (HikariCP)
â”‚       â”œâ”€â”€ AppManager.java      â† App/entity lifecycle management
â”‚       â””â”€â”€ server/routes/
â”‚           â”œâ”€â”€ GenericEntityRoutes.java  â† /api/{entity}/* CRUD
â”‚           â”œâ”€â”€ AppRoutes.java            â† /appbana-studio/* app management
â”‚           â””â”€â”€ SchemaRoutes.java         â† /schema/* management
â”‚
â”œâ”€â”€ app-bana-ui/                 â† Frontend Studio (port 5173, Vite + LitElement)
â”‚   â””â”€â”€ src/
â”‚       â”œâ”€â”€ builder/             â† Visual app builder (AppManager, PageManager)
â”‚       â”œâ”€â”€ runtime/             â† Live renderers (StudioTableLive.ts is key)
â”‚       â”œâ”€â”€ services/            â† API clients
â”‚       â””â”€â”€ main/                â† AI chat UI (AiChatBuilder.ts)
â”‚
â”œâ”€â”€ docs/                        â† Architecture & story documentation
â”‚   â”œâ”€â”€ 01-ARCHITECTURE.md       â† Full system architecture reference
â”‚   â”œâ”€â”€ AI_AGENT_ARCHITECTURE.md â† Agent design details
â”‚   â”œâ”€â”€ ACTIVE_TASKS.md          â† Current sprint tasks
â”‚   â””â”€â”€ session_summary.md       â† Latest session notes
â”‚
â”œâ”€â”€ config.json                  â† Database + OpenAI config (DO NOT commit secrets)
â”œâ”€â”€ start-everything.{bat,sh}    â† Master orchestrator (starts all modules in order)
â”œâ”€â”€ start-ai-builder.{bat,sh}    â† Restart AI Builder + Qdrant + Postgres
â”œâ”€â”€ start-backend.{bat,sh}       â† Restart backend + Postgres
â”œâ”€â”€ start-ui.{bat,sh}            â† Restart UI (Vite dev server)
â””â”€â”€ .github/copilot-instructions.md  â† This file
```

---

## 3. How to Start the Application

Every script has a **Windows (.bat)** and **macOS/Linux (.sh)** version. Each `start-*` script is idempotent - it stops any existing instance, ensures its dependencies, builds if needed, then launches.

### Start everything (recommended):

| Windows | macOS / Linux |
|---------|---------------|
| `.\start-everything.bat` | `./start-everything.sh` |

### Restart a single module:

| Module | Windows | macOS / Linux | Port |
|--------|---------|---------------|------|
| AI Builder | `.\start-ai-builder.bat` | `./start-ai-builder.sh` | 8081 |
| Backend | `.\start-backend.bat` | `./start-backend.sh` | 8080 |
| UI | `.\start-ui.bat` | `./start-ui.sh` | 5173 |

### What each module script does

Every `start-*` script self-contains these steps:
1. **Stop** any process already bound to its port.
2. **Ensure dependencies** are up - Docker containers (Postgres, Qdrant), Node modules, `OPENAI_API_KEY`.
3. **Build** its Maven module (or `npm install`) if artifacts / `node_modules` are missing.
4. **Launch** the service in the foreground.

### What `start-everything` does

Delegates to the three module scripts in the correct order and waits for each port to open before proceeding:
1. AI Builder (port 8081) - also brings up Qdrant + Postgres.
2. Backend (port 8080).
3. UI (port 5173).

On Windows each service opens in a new terminal window. On macOS/Linux each service is backgrounded with logs written to `dev-logs/*.log` (folder is gitignored).
### Service Ports Summary

| Service | Port | URL |
|---------|------|-----|
| AI Builder | 8081 | http://localhost:8081/health |
| Core API Backend | 8080 | http://localhost:8080/health |
| Vite Frontend | 5173 | http://localhost:5173 |
| Qdrant Vector DB | 6333 | http://localhost:6333/dashboard |

### After Changes to Java Code:
Always restart with `.\start-everything.bat` to trigger Maven recompile and clean restart.

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
- Schema migrations managed by **Liquibase** (not Flyway) â€” changesets live in `app-bana-service/src/main/resources/db/changelog/`

---

## 5. Architecture Deep Dive

### Metadata-Driven Flow

```
User (natural language)
        â†“
  ai-builder (port 8081)
  [AiAgent â†’ Tools â†’ scaffold_app]
        â†“
  app-bana-service (port 8080)
  [SchemaManager creates table]
  [GenericEntityRoutes auto-generates CRUD API]
        â†“
  app-bana-ui (port 5173)
  [StudioTableLive renders table from schema metadata]
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
// AiAgent.java line ~119
int effectiveMaxIterations = Math.min(config.getMaxIterations(), 5);
```
- Maximum 5 iterations per request (cost control)
- If all tools fail in an iteration, agent logs a warning and retries once more

### Tool Execution
- **Sequential** (mandatory): `create_entity`, `generate_page`, `create_app`
- **Parallel** (virtual threads): Everything else including `generate_mock_data`

---

## 7. Agent Tool System

All tools live in `ai-builder/src/main/java/com/appbana/ai/agent/tool/` and implement the `Tool` interface.

### Registered Tools (in `AiServer.java`)

| Tool Name | Class | Purpose |
|-----------|-------|---------|
| `scaffold_app` | `ScaffoldAppTool` | **Primary tool** â€” Creates entire app (App + Entities + Pages) in one shot |
| `create_app` | `CreateAppTool` | Creates just the app shell |
| `create_entity` | `CreateEntityTool` | Creates a single entity/table |
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
?limit=50&offset=0             â†’ Pagination
?search=John                   â†’ Full-text search
?name=John&status=active       â†’ Field-level filters (AND logic)
?name:like=%oh%                â†’ Advanced filters (:like, :>, :<, :in)
?_fields=name,email            â†’ Column projection
?_sort=name:asc,age:desc       â†’ Sorting
?_count=true                   â†’ Count only
```

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
- **Framework**: LitElement (Web Components) + TypeScript
- **Build**: Vite 5
- **Testing**: Vitest + jsdom

### Key Source Files

| File | Purpose |
|------|---------|
| `src/runtime/renderer/StudioTableLive.ts` | Renders dynamic entity tables from schema metadata |
| `src/builder/store/AppStore.ts` | Centralized state for apps and pages (REST-backed) |
| `src/builder/components/AppManager.ts` | App lifecycle UI component |
| `src/builder/components/PageManager.ts` | Page creation with 8 templates |
| `src/main/AiChatBuilder.ts` | AI chat interface component |
| `src/services/` | API client services |

### StudioTableLive.ts â€” Critical Knowledge
This is the live data table rendered in deployed apps. It relies on:
```typescript
// node.props.fields - comes from backend entity schema
// node.props.entityName - the multi-tenant entity key
// node.props.tableColumns - column definitions from schema
```
**Rule:** If you modify how backend schemas are saved, you MUST verify that `StudioTableLive` still receives `fields` metadata correctly in `node.props.tableColumns`. Failing this will silently break data rendering.

### Adding a New Field Type
1. Add the SQL type mapping in `SchemaManager.sqlType()` (Java)
2. Add the field renderer in `StudioTableLive.ts` (TypeScript)  
3. Add the input component in the form handling code

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
email       â†’ VARCHAR(255) with email validation
phone       â†’ VARCHAR(50)
status      â†’ VARCHAR(100) with options[]
reference   â†’ VARCHAR(255) referencing another entity
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

---

## 12. Active Work & Known Issues

### ðŸš§ In Progress: DialogueManager Integration (Story 3.1)
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

### âš ï¸ Known Limitations
- **Agent iteration limit**: Capped at 5 iterations per request. Complex multi-entity scaffolding may hit this limit.
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

*Last updated: April 2026 | Maintained by: AppBana Development Team*
*For session history and task tracking, see `docs/ACTIVE_TASKS.md` and `docs/session_summary.md`*

