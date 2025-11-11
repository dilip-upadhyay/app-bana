# GitHub Copilot Instructions for AppBana

## Project Overview
AppBana is a **metadata-driven platform** that generates end-to-end functionality from a single source of truth. The core flow: `Entity Definition (Business Layer) → Schema (Technical Layer) → Database Table → REST CRUD APIs → UI Pages (Runtime)`. Changes to metadata propagate automatically through all layers.

## Technology Stack
- **Backend**: Java 21 LTS, JDK HttpServer with CORS support, H2 embedded database, HikariCP, Jackson, Maven multi-module
- **Frontend**: TypeScript 5.2.2+, Lit 3.1.4 Web Components, Vite 5.3.1+ dev server, Shadow DOM component system
- **Architecture**: Metadata-driven rendering, dual-layer abstraction (Entity → Schema), universal datasource adapters
- **Persistence**: Backend filesystem (`app-bana-service/apps/{appId}/app.json` + `pages/{pageId}.json`)

## Critical Development Workflows

### Starting the Application (CRITICAL)
**Windows**: Always use helper scripts, NEVER manual commands
```powershell
# Terminal 1: Backend (runs continuously on port 8080)
.\start-backend.bat

# Terminal 2: Frontend dev server (SEPARATE terminal!)
cd app-bana-ui
npm run dev                    # Vite dev server on port 5173

# Terminal 3: API testing (NEVER use Terminal 1!)
Invoke-WebRequest -Uri "http://localhost:8080/apps"
```

**⚠️ CRITICAL RULES**:
1. Backend runs in its own terminal showing server logs continuously
2. **NEVER** run PowerShell commands in the backend terminal - it exits the server!
3. Always use `.\start-backend.bat` (not manual `java -jar` commands)
4. JAR file: `app-bana-service/target/app-bana-1.0-SNAPSHOT-fat.jar` (note: `-fat.jar` suffix)

### CORS Configuration (NEW - Nov 11, 2025)
Backend now includes CORS headers in `app-bana-service/src/main/java/com/appbana/api/Router.java`:
```java
// In handle(HttpExchange ex) method:
Headers headers = ex.getResponseHeaders();
headers.add("Access-Control-Allow-Origin", "*");
headers.add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
headers.add("Access-Control-Allow-Headers", "Content-Type, Authorization");

// Handle preflight OPTIONS requests
if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
    ex.sendResponseHeaders(204, -1);
    return;
}
```
This enables frontend (port 5173) to call backend APIs (port 8080) during development.

## Key Files to Know

**Backend (Java)**:
- `app-bana-service/src/main/java/com/appbana/ApiServer.java` - HTTP routes, /apps endpoints
- `app-bana-service/src/main/java/com/appbana/AppManager.java` - App/page CRUD with auto-linking
- `app-bana-service/src/main/java/com/appbana/api/Router.java` - Custom HTTP router with CORS
- `app-bana-service/src/main/java/com/appbana/ai/AiSystemPrompts.java` - AI generation prompts
- `app-bana-service/src/main/java/com/appbana/AiResultValidator.java` - Validates AI responses

**Frontend (TypeScript)**:
- `app-bana-ui/src/builder/store/AppStore.ts` - Global state singleton (use `appStore` import)
- `app-bana-ui/src/builder/components/AiChatBuilder.ts` - AI chat interface with app generation
- `app-bana-ui/src/runtime/shell/AppRuntimeShell.ts` - App preview/runtime shell (NEW - Nov 11)
- `app-bana-ui/src/index.ts` - App loader, handles `/index.html?state=...` preview URLs
- `app-bana-ui/src/core/registry.ts` - Component registration + lazy loading

**Storage** (Backend filesystem):
- `app-bana-service/apps/{appId}/app.json` - App metadata
- `app-bana-service/apps/{appId}/pages/{pageId}.json` - Page metadata with ComponentNode trees

## Project-Specific Conventions

### PageMeta Structure (CRITICAL)
```typescript
interface PageMeta {
  id: string;
  name: string;
  rootId: string;              // MUST match root node's ID in nodes array
  nodes: ComponentNode[];      // Component tree structure
  metaVersion: string;
  type: string;                // 'list' | 'detail' | 'form' | 'dashboard' | etc.
}
```

**Common Bug**: AI-generated pages had mismatched `rootId` vs root node ID due to multiple `Date.now()` calls.
**Solution**: Generate timestamp once, pass `rootId` to node builder (fixed Nov 11 in `AiChatBuilder.ts:1025-1046`)

### API Response Wrapping
Backend wraps list responses:
```java
// GET /apps returns:
{ "apps": [AppListItem, ...] }

// GET /apps/{id} returns full app object directly
{ "id": "...", "name": "...", "entities": [...], "pages": [...] }
```
Frontend expects this format in `AppStore.loadApps()` and `loadAppById()`.

### Component Registration Pattern
```typescript
// In component file:
@customElement('my-component')
export class MyComponent extends LitElement { ... }

// In registry.ts:
if (!registry.has('my-component')) {
  proms.push(import('../components/MyComponent.js'));
}

// Before using component:
await ensureCoreRegistered();
```

### CSS Import Pattern
Always use `?inline` suffix to bundle CSS into component:

### CSS Import Pattern
Always use `?inline` suffix to bundle CSS into component:
```typescript
import styles from './MyComponent.css?inline';

@customElement('my-component')
export class MyComponent extends LitElement {
  static readonly styles = unsafeCSS(styles);  // Note: unsafeCSS, not css``
}
```

## AI App Generation (Recent Fixes - Nov 11, 2025)

### Known Issues Fixed
1. **AI Template Substitution** - AI would return generic apps ("Task Manager") instead of user's domain
   - **Fix**: Enhanced `AiSystemPrompts.java` with explicit anti-substitution rules
   - **Validation**: New `AiResultValidator.java` rejects poor AI responses

2. **Pages Not Creating** - Frontend used `result.suggestedPages` (strings) not `result.pages` (full metadata)
   - **Fix**: `AiChatBuilder.ts` now prefers `result.pages` over `suggestedPages`

3. **Root Node Mismatch** - `rootId` didn't match root node's actual ID due to multiple `Date.now()` calls
   - **Fix**: Generate timestamp once, pass to node builder (lines 1025-1046 in `AiChatBuilder.ts`)

### AI Generation Flow
```
User Request → AI Provider → Parse JSON → AiResultValidator.validateAiResult()
                                               ↓
                                       Valid? → Return AI Result
                                       Invalid → Template Fallback
```

## App Preview/Runtime System (COMPLETED Phase 1 - Nov 11, 2025)

### Architecture
```
Studio (Builder) → Preview Button (👁️) → /index.html?state={base64}
                                              ↓
                                    AppRuntimeShell Component
                                    (header + tabs + page renderer)
```

### Current State
✅ **Phase 1 Complete**:
- `AppRuntimeShell.ts` - Full app preview shell with header, navigation tabs
- CORS enabled in backend `Router.java` (allows frontend → backend API calls)
- Preview button in `LivePreview.ts` toolbar (eye icon)
- URL-based state encoding/decoding

❌ **Not Yet Implemented** (Phases 2-6):
- Data binding (components don't fetch real data from APIs)
- Form handling (forms render but don't submit)
- Action handlers (buttons don't trigger actions)
- See `docs/APP_PREVIEW_ANALYSIS.md` for full roadmap

### Testing Preview
1. Open `http://localhost:5173/studio.html`
2. Load an app from AI chat or app list
3. Click page in left sidebar
4. Click 👁️ (eye icon) in LivePreview toolbar
5. New tab opens with app header, page tabs, and rendered content

## Documentation Reference

**Primary Docs** (comprehensive):
- `docs/01-ARCHITECTURE.md` - System design, tech stack decisions
- `docs/02-DEVELOPMENT_GUIDE.md` - Build, run, develop, keyboard shortcuts
- `docs/APP_PREVIEW_ANALYSIS.md` - Runtime/preview system roadmap (8-day implementation plan)

**Session Summaries** (chronological implementation details):
- `docs/SESSION_SUMMARY_NOV11_2025.md` - AI fixes, Runtime Shell Phase 1, CORS, rootId bug

**Guides**:
- `app-bana-ui/src/core/ADAPTER_GUIDE.md` - Datasource adapter usage (500+ lines)
- `builder-database/README.md` - AI-readable capability reference

## Builder Database (AI App Generator Reference)

**Location**: `builder-database/` directory  
**Purpose**: Machine-readable reference of ALL AppBana capabilities for AI agents

**Structure**:
```
builder-database/
├── 01-core-concepts.json     # Architecture, patterns
├── 02-components.json        # UI components (13 types)
├── 03-entities.json          # Field types (38 types), relationships
├── 04-pages.json             # Page templates (7 templates)
├── 05-datasources.json       # Datasource adapters (25 types)
├── 08-api-endpoints.json     # REST API reference
└── 99-capabilities-index.json # Quick lookup
```

**Update Protocol**: When adding components/field types/page templates, update corresponding JSON + increment version + update capabilities index.

## Common Debugging Scenarios

### "Root node not found" Error
**Cause**: `PageMeta.rootId` doesn't match any node's ID in `nodes` array  
**Fix**: Verify `rootId` value matches first node's `id` in page JSON file

### CORS Error in Preview
**Symptom**: `Access to fetch at 'http://localhost:8080/apps/...' from origin 'http://localhost:5173' has been blocked by CORS policy`  
**Fix**: Backend `Router.java` should have CORS headers (added Nov 11). Rebuild backend if missing.

### Backend Exits Immediately
**Cause**: Running PowerShell commands in backend's terminal  
**Fix**: Use separate terminal for testing. Backend terminal shows logs only.

### AI Generates Wrong App Domain
**Cause**: AI substituting generic templates  
**Check**: `AiResultValidator.java` should be rejecting these (added Nov 11)  
**Logs**: Look for `[AI Validation]` messages in backend logs

### Frontend Build Warnings (Dynamic Imports)
**Message**: "C:/Users/.../ButtonElement.ts is dynamically imported... but also statically imported"  
**Status**: ⚠️ Expected warning, doesn't affect functionality (lazy loading + static imports coexist)

## PowerShell Commands Reference

```powershell
# Check if backend port is in use
Get-NetTCPConnection -LocalPort 8080 -State Listen

# Kill backend process (if stuck)
Get-NetTCPConnection -LocalPort 8080 | Select-Object -ExpandProperty OwningProcess | Stop-Process -Force

# Test backend API (from separate terminal!)
Invoke-WebRequest -Uri "http://localhost:8080/apps" | Select-Object StatusCode, @{N='Content';E={$_.Content | ConvertFrom-Json | ConvertTo-Json -Depth 5}}

# Check CORS headers
Invoke-WebRequest -Uri "http://localhost:8080/apps" -Method OPTIONS | Select-Object -ExpandProperty Headers
```

## Key File Locations

```
app-bana/
├── app-bana-service/          # Backend (Java)
│   ├── apps/                  # App storage (filesystem)
│   └── src/main/java/com/appbana/
│       ├── ApiServer.java     # Main server, routes
│       ├── AppManager.java    # App/page CRUD
│       ├── api/Router.java    # HTTP router + CORS
│       └── ai/                # AI generation system
│
├── app-bana-ui/               # Frontend (TypeScript + Lit)
│   └── src/
│       ├── builder/           # Studio builder components
│       │   ├── store/AppStore.ts          # Global state
│       │   └── components/
│       │       ├── AiChatBuilder.ts       # AI chat interface
│       │       ├── PageManager.ts         # Page CRUD
│       │       └── EntityManager.ts       # Entity CRUD
│       ├── runtime/           # App preview/runtime
│       │   └── shell/
│       │       ├── AppRuntimeShell.ts     # Preview shell (NEW)
│       │       └── AppRuntimeShell.css
│       └── core/
│           ├── registry.ts    # Component registration
│           └── adapters/      # Datasource adapters
│
├── builder-database/          # AI capability reference
├── docs/                      # Documentation
└── start-backend.bat          # Backend startup script
```

---

**Last Updated**: November 11, 2025  
**Major Changes**: CORS support, AppRuntimeShell (Phase 1), AI validation layer, rootId bug fixes

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

