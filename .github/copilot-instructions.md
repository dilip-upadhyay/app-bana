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
- `src/core/ADAPTER_GUIDE.md`: Comprehensive usage guide (500+ lines)

**Adapter Pattern**:
```typescript
// Get adapter from registry
const adapter = AdapterRegistry.getInstance().create('rest-api', config);
await adapter.connect(config);

// Universal CRUD
const result = await adapter.query('users', {
  filters: [{ field: 'status', operator: 'eq', value: 'active' }],
  sort: [{ field: 'createdAt', desc: true }],
  limit: 10
});
```

**Supported Datasources**:
- SQL: `h2`, `postgres`, `mysql`, `oracle`, `mssql`, `sqlite`, `mariadb`
- NoSQL: `mongodb`, `dynamodb`, `cassandra`, `couchdb`, `redis`
- APIs: `rest-api`, `graphql`, `soap`, `grpc`, `odata`
- Files: `json-file`, `csv-file`, `excel-file`, `xml-file`
- Browser: `in-memory`, `localstorage`, `sessionstorage`
- Cloud: `salesforce`, `google-sheets`, `airtable`, `s3`

### 2. Dual-Layer Abstraction (Nov 2025)
**Entity Abstraction Layer** sits above the technical schema layer:
```
Business User View (EntityMeta)
    ↓ EntitySchemaConverter
Technical View (RelationalSchema)  
    ↓ Backend DDL Generation
Database Tables
```

**Key Files**:
- `src/models/entity-metadata.ts`: 30+ business-friendly field types (text, email, phone, currency, reference, etc.)
- `src/core/EntitySchemaConverter.ts`: Converts EntityMeta ↔ RelationalSchema
- `src/core/backend-sync.ts`: Syncs EntityMeta to backend `/api/schema` endpoint (NEW - Nov 8)
- `src/builder/components/EntityManager.ts`: Visual entity CRUD with relationship editor (1500+ lines)

**Entity Auto-includes**:
- Every entity gets `id` field (autoincrement, primary key) - PROTECTED, cannot modify/delete
- Soft delete: `deleted` boolean field (if enabled)
- Versioning: `version` int field (if enabled)
- `datasourceType` and `datasourceConfig` fields for adapter selection (NEW)

**Relationships**:
- Visual relationship editor with 4 types: one-to-one, one-to-many, many-to-one, many-to-many
- Foreign keys auto-generated in DDL with CASCADE DELETE support
- Junction tables auto-created for many-to-many relationships
- Field mapping: fromField → toField (defaults to `{entityName}Id` → `id`)

**Backend Sync** (NEW):
```typescript
import { syncEntityToBackend, previewBackendSchema } from '../core/backend-sync';

// Preview SQL without creating table
const sqlStatements = await previewBackendSchema(userEntity);

// Create table in backend database
await syncEntityToBackend(userEntity);
```

### 3. Component System (Lit vs BaseElement)
Two component patterns exist - **prefer Lit for new components**:

**Lit Components** (Modern, Preferred):
```typescript
import { html, LitElement, css } from 'lit';
import { customElement, state } from 'lit/decorators.js';

@customElement('my-component')
export class MyComponent extends LitElement {
  static readonly styles = css`/* scoped styles */`;
  
  @state()
  private data: any;
  
  render() {
    return html`<div>${this.data}</div>`;
  }
}
```

**BaseElement Components** (Legacy):
```typescript
import { BaseElement } from '../core/BaseElement';

@customElement('my-component')
export class MyComponent extends BaseElement {
  static styles = styles; // CSS string
  
  protected render(): string {
    return `<div>Content</div>`; // Returns HTML string
  }
}
```

### 4. Component Registration
All components must register in `src/core/registry.ts`:
```typescript
registerComponent('my-component', () => import('../components/MyComponent'));
```
The registry uses dynamic imports for lazy-loading. Call `ensureCoreRegistered()` before using components.

### 5. Metadata Structure
Core interfaces in `src/models/metadata.ts`:
```typescript
interface ComponentNode {
  id: string;           // Unique identifier
  type: string;         // Component type (e.g., 'container', 'text', 'button')
  props?: Record<string, any>;
  children?: string[];  // Array of child node IDs
  style?: Record<string, string>;
}

interface PageMeta {
  metaVersion?: string;
  id: string;
  name: string;
  path: string;
  rootId: string;       // REQUIRED - ID of root node
  nodes: ComponentNode[]; // Array of all nodes in page tree
  type?: 'page' | 'component';
}
```
**Common Error**: Using `components` property instead of `nodes` in PageMeta.

### 6. API Client Pattern
Use `ApiClient` from `src/core/api-client.ts` for all HTTP requests:
```typescript
import { apiClient } from './api-client';

// GET request
const data = await apiClient.get<MyType>('/api/resource');

// POST with body
const result = await apiClient.post('/api/resource', { name: 'value' });
```
Backend API runs on port 8080, Vite proxies requests in dev mode.

## Build & Run Commands

### Backend (Java)
```powershell
# Windows PowerShell commands
# Build shaded JAR with UI assets (from root)
mvn clean package

# Run backend server (port 8080)
java -jar app-bana-service/target/app-bana-service-1.0-SNAPSHOT.jar

# Skip tests for faster builds
mvn clean package -DskipTests
```

### Frontend (TypeScript/Vite)
```powershell
cd app-bana-ui

# Development server (port 5173)
npm run dev

# Production build → ../app-bana-service/src/main/resources/ui/dist/
npm run build

# Run tests
npm test
```

### Full Stack Development
```powershell
# Terminal 1 (Backend): 
mvn clean package -DskipTests ; java -jar app-bana-service/target/app-bana-service-1.0-SNAPSHOT.jar

# Terminal 2 (Frontend):
cd app-bana-ui ; npm run dev

# Access:
# - Studio Builder: http://localhost:5173/studio.html
# - Backend API: http://localhost:8080/apps
# - OpenAPI/Swagger: http://localhost:8080/ui/swagger
```

**CRITICAL**: Java version is 21 (LTS), not 25. Parent pom.xml enforces Java 21 compatibility.

## Studio Builder Architecture
The visual page builder (`src/builder/components/`) has 3-panel layout:
- **Left**: Component library (component-gallery.ts), Entity Manager
- **Center**: Canvas with drag-drop (BuilderCanvas.ts)
- **Right**: Properties inspector (property-panel.ts)

Key state management:
- `TreeStore`: Component tree structure
- `AppStore`: Global app state (pages, routing, **entities**)
- `PageManager`: Page creation/template wizard
- `EntityManager`: Visual entity CRUD with field/relationship editor

**Entity Manager** (`EntityManager.ts` - 1500+ lines):
- Full CRUD for entities (create, read, update, delete)
- Visual field editor with 30+ field types
- Visual relationship editor (one-to-one, one-to-many, many-to-one, many-to-many)
- Real-time SQL preview with foreign keys and junction tables
- Protected `id` field (auto-generated, cannot be modified)
- Collapsible sections for fields, relationships, SQL preview

## Key Files Reference
- **Core Framework**: `src/core/BaseElement.ts`, `src/core/registry.ts`
- **Metadata Types**: `src/models/metadata.ts`, `src/models/entity-metadata.ts`
- **Entity System**: `src/core/EntitySchemaConverter.ts`, `src/builder/components/EntityManager.ts`
- **Datasource Adapters**: `src/core/DataSourceAdapter.ts`, `src/core/AdapterRegistry.ts`, `src/core/adapters/`
- **Backend Sync**: `src/core/backend-sync.ts` (EntityMeta → Backend schema converter)
- **API Client**: `src/core/api-client.ts`
- **Studio Builder**: `src/builder/components/PageManager.ts`, `BuilderCanvas.ts`
- **Architecture Docs**: `docs/01-ARCHITECTURE.md`, `docs/02-DEVELOPMENT_GUIDE.md`
- **Roadmap**: `docs/03-ROADMAP.md`
- **Session Summaries**: `docs/SESSION_SUMMARY_NOV05_2025.md`, `docs/ADAPTER_IMPLEMENTATION_COMPLETE.md`
- **Test Apps**: `registration-test.html` (User registration with LocalStorage adapter)

## Common Pitfalls
1. **Forgetting `rootId`**: PageMeta requires `rootId` field pointing to root node
2. **Wrong property names**: Use `nodes` not `components` in PageMeta
3. **Missing registration**: Components must be registered in registry.ts
4. **CSS imports**: Always use `?inline` suffix for CSS imports
5. **Async registry**: Call `ensureCoreRegistered()` before accessing components
6. **Port conflicts**: Backend uses 8080, frontend dev server uses 5173
7. **Entity ID field**: Every entity auto-includes `id` field (protected, cannot modify)
8. **Relationship field mapping**: Foreign key fields must exist before creating relationships
9. **SQL DDL Generation**: Use `EntitySchemaConverter.generateDDL()` for SQL preview (includes FK constraints)
10. **Junction tables**: Many-to-many relationships auto-generate junction tables in DDL
11. **Backend auto-linking** (NEW - Nov 8): Page creation/deletion automatically updates app.pages array - don't manually manage this relationship
12. **Maven enforcer warning**: pom.xml enforces Java 25 but project actually uses Java 21 - this is a known config mismatch

## Backend Architecture (Java Service Layer)

### App Persistence System (Nov 8, 2025)
```
apps/{appId}/
├── app.json          # App metadata with auto-maintained pages[] array
└── pages/
    ├── {pageId}.json # Individual page metadata
    └── ...
```

**Auto-Update Pattern** (CRITICAL - recently implemented):
- `AppManager.savePage()` **automatically** adds pageId to app.pages[] if not present
- `AppManager.deletePage()` **automatically** removes pageId from app.pages[]
- This ensures atomic app-page relationship without frontend coordination
- Frontend still makes 2 API calls (page save + app update), but backend ensures consistency
- Manual testing via curl: single `PUT /apps/{appId}/pages/{pageId}` is sufficient

**Key Backend Services**:
- `AppManager`: App/page filesystem persistence with auto-linking
- `SchemaManager`: Entity schema DDL generation and migrations
- `JdbcManager`: Database connection pooling (HikariCP)
- `ApiServer`: HTTP request routing (JDK HttpServer, port 8080)
- `AuditLogService`: CRUD audit logging (INSERT/UPDATE/DELETE tracking)

### REST API Endpoints
```
GET    /apps                           # List all apps
POST   /apps                           # Create new app
GET    /apps/{appId}                   # Get app metadata
PUT    /apps/{appId}                   # Update app metadata
DELETE /apps/{appId}                   # Delete app and all pages
GET    /apps/{appId}/pages/{pageId}   # Get page
PUT    /apps/{appId}/pages/{pageId}   # Save page (auto-updates app.pages)
DELETE /apps/{appId}/pages/{pageId}   # Delete page (auto-removes from app.pages)
POST   /api/schema                     # Create entity schema
GET    /audit                          # Query audit logs
```

## Frontend Architecture (TypeScript/Lit)

### State Management Pattern
**AppStore** (`app-bana-ui/src/builder/store/AppStore.ts`) is the **single source of truth**:
```typescript
// Singleton pattern - import everywhere
import { appStore } from '../store/AppStore';

// Key methods
appStore.getCurrentApp()           // Get active app
appStore.createApp(request)        // Create app (auto-loading state)
appStore.addPage(appId, page)      // Add page (2 API calls internally)
appStore.loadPage(appId, pageId)   // Load page from backend
appStore.updateApp(appId, updates) // Update app metadata
appStore.subscribe(callback)       // Listen for state changes
appStore.isLoading()               // Check loading state (NEW - Nov 8)
```

**Loading State Infrastructure** (NEW - Nov 8):
```typescript
// AppStore manages loading state for async operations
this.setLoading(true);
try {
  // ... async operation
} finally {
  this.setLoading(false);
}

// Components can check: if (appStore.isLoading()) { /* show spinner */ }
```

### Component State Reactivity
```typescript
// Lit components subscribe to AppStore changes
connectedCallback() {
  super.connectedCallback();
  appStore.subscribe(() => this.requestUpdate()); // Re-render on state changes
}
```

## Testing Conventions
- Unit tests: `*.test.ts` files colocated with source
- Run with: `npm test` (uses Vitest + jsdom)
- Test component behavior, not implementation details
- Mock API calls using Vitest's `vi.mock()`

## Code Style
- **TypeScript**: Strict mode enabled, prefer interfaces over types
- **Naming**: PascalCase for classes/components, camelCase for variables/functions
- **Imports**: Group by: external libs → core → components → styles
- **Comments**: Document complex logic, avoid obvious comments

## Integration Points
- **Metadata → Backend**: POST to `/api/schema` to create entities (use `backend-sync.ts`)
- **Backend → Frontend**: GET from `/api/{entity}` for CRUD operations
- **Studio → Runtime**: PageMeta serialized to JSON, loaded by Renderer
- **Registry → Components**: Dynamic imports ensure lazy-loading
- **Adapters → Data**: Universal interface for SQL/NoSQL/REST/Files via DataSourceAdapter

## Development Workflow & Debugging

### Testing Backend Changes
```powershell
# Rebuild and restart backend after Java changes
mvn clean package -DskipTests
java -jar app-bana-service/target/app-bana-service-1.0-SNAPSHOT.jar

# Test REST API with PowerShell
Invoke-WebRequest -Uri http://localhost:8080/apps -Method GET
Invoke-WebRequest -Uri http://localhost:8080/apps/test-app/pages/home -Method PUT -Body (Get-Content page.json)
```

### Testing Frontend Changes
```powershell
# Vite dev server has HMR - changes reflect immediately
cd app-bana-ui
npm run dev

# Run unit tests (Vitest)
npm test

# Build production bundle (goes to app-bana-service/src/main/resources/ui/dist/)
npm run build
```

### Common Development Tasks

**Add New Entity**:
1. Open Studio Builder: `http://localhost:5173/studio.html`
2. Click "Entities" tab (left sidebar)
3. Click "Create Entity" button
4. Add fields using visual field editor
5. Add relationships (optional)
6. Click "Save" - SQL preview shown automatically
7. Backend creates table when entity is synced

**Add New Page**:
1. Open Studio Builder, select app
2. Click "Pages" tab (left sidebar)
3. Click "New Page" button
4. Choose template (Login/Dashboard/etc.) or Blank Canvas
5. Page metadata saved to `apps/{appId}/pages/{pageId}.json`
6. Page auto-linked to app (backend handles this)

**Debug Component Issues**:
1. Check browser DevTools console for errors
2. Verify component registered in `src/core/registry.ts`
3. Check Shadow DOM in Elements tab (components use Shadow DOM)
4. Verify CSS imported with `?inline` suffix
5. Check network tab for failed API calls

### Critical Files for Debugging
- Backend logs: Check terminal running `java -jar ...`
- Frontend logs: Browser DevTools console
- App metadata: `apps/{appId}/app.json` (pretty-printed JSON)
- Page metadata: `apps/{appId}/pages/{pageId}.json`
- Network requests: Browser DevTools Network tab (XHR filter)

### Known Issues & Workarounds

**Issue**: "No App Selected" after creating app
- **Cause**: Race condition in AppStore state update
- **Fix**: Implemented Nov 3, 2025 - AppStore now properly updates after app creation
- **Status**: ✅ RESOLVED

**Issue**: Pages array empty in app.json
- **Cause**: Page creation didn't update app.pages array
- **Fix**: Backend auto-update implemented Nov 8, 2025 in `AppManager.savePage()`
- **Status**: ✅ RESOLVED

**Issue**: Java version mismatch (pom.xml says 25, actually using 21)
- **Cause**: Maven enforcer config not updated after Java 21 decision
- **Workaround**: Ignore enforcer warnings, project works with Java 21
- **Status**: ⚠️ Known config mismatch, functionally correct

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

