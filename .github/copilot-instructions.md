# GitHub Copilot Instructions for AppBana

## Project Overview
AppBana is a **metadata-driven platform** that generates end-to-end functionality from a single source of truth. The core flow: `Entity Definition (Business Layer) → Schema (Technical Layer) → Database Table → REST CRUD APIs → UI Pages (Runtime)`. Changes to metadata propagate automatically through all layers.

## Technology Stack
- **Backend**: Java 21 LTS (corrected from 25), JDK HttpServer (default) or Tomcat, H2 embedded database, HikariCP, Jackson, Maven multi-module build
- **Frontend**: TypeScript 5.2.2+, Lit 3.1.4 Web Components, Vite 5.3.1+ dev server, Vitest 1.5.0+ testing
- **Architecture**: Shadow DOM component system, custom element registry, metadata-driven rendering, dual-layer abstraction (Entity → Schema), universal datasource adapters
- **Persistence**: Backend filesystem (apps/{appId}/app.json + pages/{pageId}.json) - migrated from localStorage Nov 2025

## Critical Development Patterns

### 1. Universal Datasource Adapter System (NEW - Nov 8, 2025)
**Entities can now work with ANY backend** - not just databases:
```
EntityMeta (Business Definition)
    ↓ Choose Datasource Type
REST API | SQL DB | NoSQL | Files | LocalStorage
    ↓ DataSourceAdapter Interface
Universal CRUD Operations
```

**Key Files**:
- `src/core/DataSourceAdapter.ts`: Universal CRUD interface (590 lines)
- `src/core/AdapterRegistry.ts`: Singleton adapter registry (255 lines)
- `src/core/adapters/RestApiAdapter.ts`: External REST APIs with auth, rate limiting (396 lines)
- `src/core/adapters/JsonFileAdapter.ts`: File/LocalStorage/SessionStorage (334 lines)
- `src/core/adapter-bootstrap.ts`: Auto-registers 7+ datasource types
# AppBana — Quick AI Agent Instructions

These brief instructions give an AI coding agent what it needs to be productive in AppBana.

High level: metadata-first platform — EntityMeta -> Schema -> DDL/DB -> auto-generated REST CRUD -> runtime UI. Backend is Java (JDK HttpServer), frontend is TypeScript + Lit (Vite dev).

Key files to know (jump-to):
- `app-bana-service/src/main/java/com/appbana/ApiServer.java` — HTTP routes, /apps endpoints
- `apps/` — repo-backed app persistence: `apps/{appId}/app.json` and `pages/{pageId}.json`
- `app-bana-ui/src/builder/store/AppStore.ts` — single source of truth for apps/pages/entities (use its methods: `createApp`, `setCurrentApp`, `getCurrentApp`, `updateApp`)
- `app-bana-ui/src/builder/components/EntityManager.ts` — entity editor & SQL preview
- `app-bana-ui/src/core/registry.ts` — component registration + lazy imports (call `ensureCoreRegistered()` before use)
- `app-bana-ui/src/core/DataSourceAdapter.ts` and `src/core/adapters/` — universal datasource adapters (RestApi, JsonFile, SQL, NoSQL)

Project-specific conventions (important):
- API list response is wrapped: `GET /apps` returns `{ apps: AppListItem[] }` (frontend expects this)
- `PageMeta` uses `nodes` (not `components`) and requires `rootId` — breaking this breaks page loading
- Prefer Lit components; legacy `BaseElement` components exist and return HTML strings
- CSS imports use `?inline` so they are bundled into components
- `AppStore.setCurrentApp()` must load full app (entities/pages) — components subscribe to `appStore` for reactivity

Dev & run shortcuts (tested):
- Backend: `mvn clean package -DskipTests` then `java -jar app-bana-service/target/app-bana-service-1.0-SNAPSHOT.jar` (port 8080)
- Frontend (dev): `cd app-bana-ui` then `npm run dev` (Vite on 5173). Studio UI: `/studio.html`
- Full-stack local dev: build backend JAR, run it; in parallel run Vite dev server for HMR

Integration points to watch:
- OpenAI: config via `config.json` or env vars (`OPEN_API_KEY`, `OPEN_AI_KEY`, `OPENAI_API_KEY`). AI provider selection in `config.json` (`openai` vs `ollama`).
- Adapters: `AdapterRegistry` instantiates adapters from `src/core/adapters/` — follow `adapter-bootstrap.ts` for registration
- Backend persistence: editing files under `apps/` is the source of truth; `ApiServer` and `AppManager` read/write these files

Debugging tips & quick checks:
- If UI shows no apps: curl `http://localhost:8080/apps` and ensure it returns `{ apps: [...] }`
- If entities missing: curl `http://localhost:8080/apps/{appId}` to confirm `entities` present
- Watch frontend console for `[AppStore]` logs (AppStore contains helpful debug logs when loading apps)
- Backend logs print to the terminal running the JAR; use `Invoke-WebRequest` / `curl` to reproduce API calls

If anything here is unclear or you'd like extra examples (common PR patterns, preferred tests to add, or a short checklist for reviewing AI-generated changes), tell me which area to expand.


**Issue**: Lint warnings in backend (System.out, try-with-resources)
- **Cause**: Rapid prototyping prioritized over code cleanliness
- **Impact**: Non-blocking, code is functionally correct
- **Status**: ⚠️ Tech debt, cleanup deferred

## Documentation Reference

**Primary Docs** (comprehensive, up-to-date):
- `docs/01-ARCHITECTURE.md` - System design, tech stack, architecture decisions
- `docs/02-DEVELOPMENT_GUIDE.md` - Build, run, develop, keyboard shortcuts
- `docs/03-ROADMAP.md` - Product vision, Q4 2025 delivery plan

**Session Summaries** (implementation details):
- `docs/E2E_TEST_RESULTS_NOV08_2025.md` - Backend persistence testing
- `docs/APP_PAGE_RELATIONSHIP.md` - Auto-linking architecture
- `docs/BACKEND_APP_PERSISTENCE_COMPLETE.md` - Persistence implementation
- `docs/SESSION_SUMMARY_NOV05_2025.md` - Relationship editor implementation
- `docs/ENTITY_MANAGER_SQL_PREVIEW_COMPLETE.md` - SQL preview feature

**Guides**:
- `app-bana-ui/src/core/ADAPTER_GUIDE.md` - Datasource adapter usage (500+ lines)
- `app-bana-ui/README.md` - Component architecture, adding components

## Quick Reference

### Important File Locations
```
Backend:
├── app-bana-service/src/main/java/com/appbana/
│   ├── ApiServer.java        # HTTP routing, request handling
│   ├── AppManager.java       # App/page persistence with auto-linking
│   ├── SchemaManager.java    # DDL generation, migrations
│   ├── JdbcManager.java      # Connection pooling
│   └── model/
│       ├── AppMetadata.java  # App metadata model
│       └── EntitySchema.java # Entity schema model

Frontend:
├── app-bana-ui/src/
│   ├── builder/
│   │   ├── store/AppStore.ts              # Global state management
│   │   └── components/
│   │       ├── BuilderShell.ts            # Main builder layout
│   │       ├── PageManager.ts             # Page CRUD + templates
│   │       ├── EntityManager.ts           # Entity CRUD + relationships
│   │       └── BuilderCanvas.ts           # Drag-drop canvas
│   ├── core/
│   │   ├── registry.ts                    # Component registration
│   │   ├── api-client.ts                  # HTTP client
│   │   ├── DataSourceAdapter.ts           # Adapter interface
│   │   ├── AdapterRegistry.ts             # Adapter registry
│   │   ├── EntitySchemaConverter.ts       # Entity → Schema converter
│   │   └── backend-sync.ts                # Entity → Backend sync
│   ├── models/
│   │   ├── metadata.ts                    # PageMeta, ComponentNode
│   │   └── entity-metadata.ts             # EntityMeta, FieldMeta
│   └── runtime/
│       └── renderer/                      # Metadata → DOM renderer

Storage:
└── apps/{appId}/
    ├── app.json                          # App metadata
    └── pages/{pageId}.json               # Page metadata
```

### Key Patterns to Follow

**Backend Java**:
- Use `AppManager` for all app/page operations (don't write files directly)
- Jackson ObjectMapper with `INDENT_OUTPUT` for pretty JSON
- Try-catch warnings to avoid breaking changes
- System.out logging (logger migration deferred)

**Frontend TypeScript**:
- Import from `appStore` singleton, never instantiate new AppStore
- Use Lit's `@state()` for reactive properties
- Subscribe to AppStore changes in `connectedCallback()`
- Always use `?inline` for CSS imports
- Register components in `registry.ts` before using

**Metadata Design**:
- PageMeta must have `rootId` pointing to root ComponentNode
- ComponentNode uses `children: string[]` for node IDs
- EntityMeta auto-includes protected `id` field
- Use `datasourceType` + `datasourceConfig` for adapter selection

### PowerShell-Specific Commands
```powershell
# Check if port in use
Get-NetTCPConnection -LocalPort 8080 -State Listen

# Kill process on port
Get-NetTCPConnection -LocalPort 8080 | Select-Object -ExpandProperty OwningProcess | Stop-Process -Force

# JSON body in Invoke-WebRequest
$body = Get-Content app.json -Raw
Invoke-WebRequest -Uri http://localhost:8080/apps -Method POST -Body $body -ContentType "application/json"

# Pretty-print JSON response
(Invoke-WebRequest -Uri http://localhost:8080/apps).Content | ConvertFrom-Json | ConvertTo-Json -Depth 10
```

## Builder Database (AI App Builder Reference)

### Purpose
**Location**: `builder-database/` directory  
**Purpose**: Complete machine-readable reference of ALL AppBana capabilities for AI agents  
**Use Case**: AI chat-based app builder that generates apps from natural language

### Database Structure
```
builder-database/
├── README.md                      # Database documentation and update protocol
├── 01-core-concepts.json          # Architecture, patterns, build process
├── 02-components.json             # All UI components (13 components)
├── 03-entities.json               # Field types (38 types), relationships
├── 04-pages.json                  # Page templates (7 templates)
├── 05-datasources.json            # Datasource adapters (25 adapters)
├── 06-styling.json                # Design tokens, theme system
├── 07-validation.json             # Validation rules and patterns
├── 08-api-endpoints.json          # REST API reference (30 endpoints)
└── 99-capabilities-index.json     # Quick lookup index
```

### Update Protocol (CRITICAL)

**When codebase changes, AI agents MUST update builder database:**

1. **New Component** → Update `02-components.json`
   - Add component entry with type, props, examples
   - Increment version, update timestamp
   - Update `99-capabilities-index.json` summary

2. **New Entity Field Type** → Update `03-entities.json`
   - Add field type to appropriate category
   - Document SQL mapping and validation
   - Update field type count in index

3. **New Page Template** → Update `04-pages.json`
   - Add template with component tree
   - Document layout and purpose
   - Update template count in index

4. **New Datasource Adapter** → Update `05-datasources.json`
   - Add adapter to appropriate category
   - Document config properties
   - Update adapter count in index

5. **API Endpoint Changed** → Update `08-api-endpoints.json`
   - Update endpoint definition
   - Document new parameters or behavior
   - Note breaking changes

### Usage by AI Chat Builder

**Flow**:
1. User: "Create a blog app with posts and comments"
2. AI reads `99-capabilities-index.json` for overview
3. AI reads `03-entities.json` → understands field types and relationships
4. AI reads `04-pages.json` → finds CRUD page templates
5. AI generates valid metadata conforming to TypeScript interfaces
6. AI calls `appStore.createApp()`, `appStore.addPage()`, etc.
7. Backend auto-generates REST APIs from metadata
8. Runtime renders pages from metadata

**Example Generation**:
```typescript
// AI generates this metadata from "blog app" request
const postEntity: EntityMeta = {
  name: "Post",
  fields: [
    {name: "title", type: "text", required: true},
    {name: "content", type: "longtext", required: true},
    {name: "author", type: "text", required: true}
  ]
};

const commentEntity: EntityMeta = {
  name: "Comment",
  fields: [
    {name: "content", type: "text", required: true},
    {name: "author", type: "text", required: true},
    {name: "postId", type: "reference", required: true}
  ],
  relationships: [{
    type: "many-to-one",
    fromEntity: "Comment",
    toEntity: "Post",
    fromField: "postId",
    toField: "id"
  }]
};
```

### Update Example

When you add a new component `DataTableElement.ts`:

1. **Update `02-components.json`**:
```json
{
  "components": [
    // ... existing components
    {
      "type": "data-table",
      "name": "DataTableElement",
      "category": "Data Components",
      "file": "src/components/DataTableElement.ts",
      "description": "Sortable, filterable data table",
      "props": {
        "columns": {"type": "array", "description": "Column definitions"},
        "data": {"type": "array", "description": "Table data"}
      },
      "example": { /* ... */ }
    }
  ],
  "version": "1.0.1",  // increment version
  "lastUpdated": "2025-11-08T12:00:00Z"  // update timestamp
}
```

2. **Update `99-capabilities-index.json`**:
```json
{
  "summary": {
    "totalComponents": 14,  // increment count
    // ...
  }
}
```

### Validation

All generated metadata must conform to:
- `PageMeta` (src/models/metadata.ts)
- `EntityMeta` (src/models/entity-metadata.ts)
- `ComponentNode` (src/models/metadata.ts)
- `DataSourceConfig` (src/core/DataSourceAdapter.ts)

