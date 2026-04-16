# AppBana — AI Copilot Master Instructions

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
8. [Critical Rules — Multi-Tenant Entity Endpoints](#8-critical-rules--multi-tenant-entity-endpoints)
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

**Core Principle:** A single schema definition drives the entire stack — database, API, and UI are all metadata-driven.

---

## 2. Monorepo Structure

```
app-bana/
├── ai-builder/                  ← AI LLM engine (port 8081)
│   └── src/main/java/com/appbana/ai/
│       ├── agent/               ← AiAgent.java — the Think/Act/Observe loop
│       │   └── tool/            ← All agent tools (scaffold, mock data, etc.)
│       ├── api/                 ← REST endpoints (AiChatController.java)
│       ├── dialogue/            ← DialogueManager.java — conversation state machine
│       ├── llm/                 ← AdvancedPromptEngine.java, OpenAiLlmService.java
│       ├── rag/                 ← ConversationMemory.java, Qdrant vector store
│       ├── learning/            ← UserPreferenceEngine.java
│       ├── optimization/        ← DirectAnswerService.java, SemanticCache.java
│       └── server/              ← AiServer.java, ToolRegistry.java
│
├── app-bana-service/            ← Core backend API (port 8080)
│   └── src/main/java/com/appbana/
│       ├── ApiServer.java       ← HTTP server entry point
│       ├── SchemaManager.java   ← CREATE TABLE, migrations, multi-tenant isolation
│       ├── JdbcManager.java     ← Database connection pools (HikariCP)
│       ├── AppManager.java      ← App/entity lifecycle management
│       └── server/routes/
│           ├── GenericEntityRoutes.java  ← /api/{entity}/* CRUD
│           ├── AppRoutes.java            ← /appbana-studio/* app management
│           └── SchemaRoutes.java         ← /schema/* management
│
├── app-bana-ui/                 ← Frontend Studio (port 5173, Vite + LitElement)
│   └── src/
│       ├── builder/             ← Visual app builder (AppManager, PageManager)
│       ├── runtime/             ← Live renderers (StudioTableLive.ts is key)
│       ├── services/            ← API clients
│       └── main/                ← AI chat UI (AiChatBuilder.ts)
│
├── docs/                        ← Architecture & story documentation
│   ├── 01-ARCHITECTURE.md       ← Full system architecture reference
│   ├── AI_AGENT_ARCHITECTURE.md ← Agent design details
│   ├── ACTIVE_TASKS.md          ← Current sprint tasks
│   └── session_summary.md       ← Latest session notes
│
├── config.json                  ← Database + OpenAI config (DO NOT commit secrets)
├── start-everything.bat         ← Master startup script (Windows)
├── start-ai-builder.bat         ← AI Builder startup (Maven compile + run)
└── .github/copilot-instructions.md  ← This file
```

---

## 3. How to Start the Application

### ✅ The ONLY correct way to start locally (Windows):

```powershell
.\start-everything.bat
```

### What this script does (step-by-step):

| Step | Action | Why |
|------|--------|-----|
| `[0/3]` | `Stop-Process -Name java -Force` AND `Stop-Process -Name node -Force` | **Kills ALL old Java + Node processes.** Without this, stale processes occupy ports 8080 and 8081 causing race conditions. |
| `[1/3]` | Launches `start-ai-builder.bat` in a new window | Compiles `ai-builder` with Maven, connects to Qdrant on ports 6333/6334, binds port **8081** |
| `[wait]` | Polls `netstat` for port 8081 in a loop | **Only when 8081 is confirmed open** does it proceed. This prevents the backend from starting before the AI engine is ready. |
| `[2/3]` | Launches `app-bana-service` jar in a new window | Starts the core API on port **8080** |
| `[3/3]` | Launches `npm run dev` in `app-bana-ui/` | Starts the Vite frontend on port **5173** |

> [!CAUTION]
> **NEVER** start services individually unless debugging a single module in isolation. Always use `.\start-everything.bat` to ensure correct port sequencing.

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
- Schema migrations managed by **Liquibase** (not Flyway) — changesets live in `app-bana-service/src/main/resources/db/changelog/`

---

## 5. Architecture Deep Dive

### Metadata-Driven Flow

```
User (natural language)
        ↓
  ai-builder (port 8081)
  [AiAgent → Tools → scaffold_app]
        ↓
  app-bana-service (port 8080)
  [SchemaManager creates table]
  [GenericEntityRoutes auto-generates CRUD API]
        ↓
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

The agent implements a **Think → Act → Observe** loop:

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

**Phase 1 — Specification (TALK, no tools):**
- User describes what they want ("I want a spice selling app")
- Agent responds with plain English business description (NO technical terms)
- Asks: "Does this match? Say **Yes, let's build it!** when ready"

**Phase 2 — Execution (ACT, tools):**
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
| `scaffold_app` | `ScaffoldAppTool` | **Primary tool** — Creates entire app (App + Entities + Pages) in one shot |
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
3. Add clear JSON schema for parameters — the LLM reads this to know how to call the tool
4. Always handle `ConnectException` — the ai-builder and app-bana-service are separate processes
5. Return `ToolResult.error(name, message)` on failure — never throw exceptions silently

---

## 8. Critical Rules — Multi-Tenant Entity Endpoints

> [!CAUTION]
> This is the #1 source of bugs. Read carefully.

### ❌ WRONG (will return 404):
```java
String url = baseUrl + "/api/Customer/batch";
String url = baseUrl + "/api/Spice/batch";
```

### ✅ CORRECT (how `GenerateMockDataTool` does it):
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
GET    /appbana-studio/{tenantId}/apps              → List all apps
GET    /appbana-studio/{tenantId}/apps/{appId}      → Get app with entities and pages
POST   /appbana-studio/{tenantId}/apps              → Create new app
PUT    /appbana-studio/{tenantId}/apps/{appId}      → Update app
DELETE /appbana-studio/{tenantId}/apps/{appId}      → Delete app
```

### Schema Management
```
GET    /schema                  → List schema names
GET    /schema/{name}           → Get schema definition
POST   /schema                  → Create/update schema (or preview with ?preview=true)
DELETE /schema/{name}           → Delete schema
```

### Dynamic Entity CRUD (auto-generated per schema)
```
GET    /api/{entity}            → Query (pagination, search, filters, projection, sort)
GET    /api/{entity}/{id}       → Get single record
POST   /api/{entity}            → Insert record
POST   /api/{entity}/batch      → Insert multiple records (JSON array)
PUT    /api/{entity}/{id}       → Update record
DELETE /api/{entity}/{id}       → Delete record
```

**Remember:** `{entity}` must follow the multi-tenant format: `{tenantId}_{appId}_{entityName}`

### Query Parameters
```
?limit=50&offset=0             → Pagination
?search=John                   → Full-text search
?name=John&status=active       → Field-level filters (AND logic)
?name:like=%oh%                → Advanced filters (:like, :>, :<, :in)
?_fields=name,email            → Column projection
?_sort=name:asc,age:desc       → Sorting
?_count=true                   → Count only
```

### AI Endpoints
```
POST   /api/ai/chat             → General chat
POST   /api/ai/chat/agent       → Agent loop (full tool execution)
GET    /api/ai/chat/history     → Conversation history
GET    /api/ai/chat/sessions    → Past sessions
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

### StudioTableLive.ts — Critical Knowledge
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
text        → VARCHAR(255)
longtext    → TEXT
number      → INTEGER
decimal     → NUMERIC(19,4)     ← Use for money/prices, NOT "currency" or "float"
boolean     → BOOLEAN
date        → TIMESTAMP (date only)
datetime    → TIMESTAMP
email       → VARCHAR(255) with email validation
phone       → VARCHAR(50)
status      → VARCHAR(100) with options[]
reference   → VARCHAR(255) referencing another entity
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
- New fields → `ALTER TABLE ADD COLUMN`
- Renamed fields → `ALTER TABLE RENAME COLUMN` (tracked via `existingName` property)
- No production drops — data is preserved

---

## 12. Active Work & Known Issues

### 🚧 In Progress: DialogueManager Integration (Story 3.1)
**File**: `ai-builder/src/main/java/com/appbana/ai/dialogue/DialogueManager.java`

**Goal**: Enforce strict conversation state transitions in `AiChatController` using a Java-level state machine instead of relying solely on LLM prompt engineering.

**Planned States**:
```java
INITIAL → GATHERING_INFO → CONFIRMING_DETAILS → CREATING → COMPLETED
```

**Impact**: The `AiAgent.buildAgentPrompt()` will dynamically expose different tools based on the active `ConversationState` (e.g., only show `scaffold_app` when in `CREATING` state).

**Files to modify**:
- `DialogueManager.java` — implement LLM-based intent classification
- `AiChatController.java` — integrate state checks before delegating to `AiAgent`
- `AiAgent.java` — inject state into `buildAgentPrompt()`

### ✅ Recently Fixed
- **Mock data 404 bug**: `GenerateMockDataTool` now correctly prefixes entity URLs with `tenantId_appId_`
- **Boot race condition**: `start-everything.bat` now kills all stale Java/Node processes before starting
- **Scaffold entity fields**: `ScaffoldAppTool` now propagates `entityFields` to `GeneratePageTool` for `tableProps`

### ⚠️ Known Limitations
- **Agent iteration limit**: Capped at 5 iterations per request. Complex multi-entity scaffolding may hit this limit.
- **Semantic cache**: Enabled by default (`SemanticCache.java`). If you see stale LLM responses, disable it temporarily for debugging.
- **Schema pluralization**: Entity names must be used exactly as saved — no auto-pluralization is applied.

---

## 13. Development Conventions

### Java Code Style
- **Java 21**: Use virtual threads, records, sealed classes, and switch expressions where appropriate
- **Logging**: Always use `@Slf4j` (Lombok) — never `System.out.println`
- **Error handling**: Catch specific exceptions, not bare `Exception`, except at controller boundaries
- **Cross-service calls**: Always wrap HTTP calls in try-catch for `ConnectException` — the two Java services are on different ports

### When Adding a Backend Feature
1. Add route in appropriate `*Routes.java` file
2. Implement service logic in `service/` or directly in service class
3. Add integration test in `src/test/`
4. Update `GenericEntityRoutes.java` only if it's a generic entity operation

### When Adding an AI Tool
1. Create `MyTool.java` in `ai-builder/.../agent/tool/`
2. Register in `AiServer.java`: `toolRegistry.register(new MyTool(backendBaseUrl))`
3. Keep tool descriptions concise — they go into the LLM context window
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
| Modifying `ApiServer.java` directly | Boot failures | It's a thin wrapper — delegate logic to `*Routes.java` |
| Not restarting after Java changes | Old code runs silently | Always restart with `.\start-everything.bat` |
| Adding `regex` pattern to name fields | Validation failures for normal names | Set `pattern: null` for human-readable name fields |
| Calling `create_entity` for new apps | Mismatched entities without pages | Use `scaffold_app` — it creates App + Entities + Pages atomically |
| LLM returns old cached response | Debugging frustration | Disable `SemanticCache` or call `semanticCache.clear()` |

---

*Last updated: April 2026 | Maintained by: AppBana Development Team*
*For session history and task tracking, see `docs/ACTIVE_TASKS.md` and `docs/session_summary.md`*
