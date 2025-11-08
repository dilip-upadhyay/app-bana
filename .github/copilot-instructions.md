# GitHub Copilot Instructions for AppBana

## Project Overview
AppBana is a **metadata-driven platform** that generates end-to-end functionality from a single source of truth. The core flow: `Entity Definition (Business Layer) → Schema (Technical Layer) → Database Table → REST CRUD APIs → UI Pages (Runtime)`. Changes to metadata propagate automatically through all layers.

## Technology Stack
- **Backend**: Java 25 LTS with virtual threads, JDK HttpServer (default) or Tomcat, H2 embedded database, HikariCP, Jackson, Maven multi-module build
- **Frontend**: TypeScript 5.2.2+, Lit 3.1.4 Web Components, Vite 5.3.1+ dev server, Vitest 1.5.0+ testing
- **Architecture**: Shadow DOM component system, custom element registry, metadata-driven rendering, dual-layer abstraction (Entity → Schema), universal datasource adapters

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
```bash
# Build shaded JAR with UI assets
mvn clean package

# Run backend server (port 8080)
java -jar app-bana-service/target/app-bana-service-1.0-SNAPSHOT.jar
```

### Frontend (TypeScript/Vite)
```bash
cd app-bana-ui

# Development server (port 5173)
npm run dev

# Production build → src/main/resources/ui/dist/
npm run build

# Run tests
npm test
```

### Full Stack
```bash
# Start UI dev server (root of project)
./run-ui.sh dev     # or ./start-dev.sh

# Backend + Frontend together
# Terminal 1: java -jar app-bana-service/target/*.jar
# Terminal 2: cd app-bana-ui && npm run dev
```

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

