# 1. ARCHITECTURE & SYSTEM DESIGN

**Last Updated:** October 31, 2025  
**Status:** Active - Primary Reference for System Design  
**Audience:** Architects, Tech Leads, Developers

---

## Table of Contents

1. [Product Vision & Core Architecture](#product-vision--core-architecture)
2. [Studio Builder Architecture](#studio-builder-architecture-httplocalhost5173studio)
3. [Tech Stack & Dependencies](#tech-stack--dependencies)
4. [System Layers](#system-layers)
5. [Database & Datasources](#database--datasources)
6. [API Design](#api-design)
7. [UI/Frontend Architecture](#uifrontend-architecture)
8. [Component System](#component-system)
9. [Data Binding Patterns](#data-binding-patterns)
10. [Security & Auditing](#security--auditing)
11. [Configuration Management](#configuration-management)
12. [Integration Points](#integration-points)

---

## Product Vision & Core Architecture

### Vision

AppBana is a **metadata-driven platform** enabling rapid development of secure, scalable enterprise solutions with strategic focus on **Healthcare, Logistics, and HR Management**.

### Core Principle (End-to-End Cohesion)

Single metadata source drives the entire stack:

```
Schema Definition (Metadata)
    ↓
Database Table (Auto-created + migrated)
    ↓
REST CRUD APIs (Auto-generated)
    ↓
UI Pages (Runtime rendering from metadata)
```

This **metadata-driven end-to-end flow** is the primary competitive advantage.

### Key Characteristics

- **Lightweight**: No heavy frameworks; plain Java + custom Web Components
- **Enterprise-Ready**: Security, auditing, compliance built-in
- **Multi-datasource**: Support for H2, PostgreSQL, MySQL, Oracle, SQL Server, SQLite, etc.
- **Extensible**: Plugin architecture (Phase D), custom components, interceptors

---

## Studio Builder Architecture (http://localhost:5173/studio)

### Overview

The **Studio Builder** is the visual application development environment where users create and manage apps. It implements the complete app lifecycle: Create App → Define Schemas → Build Pages → Design Navigation → Preview → Publish.

### Current Implementation Status (October 31, 2025)

**Overall Alignment: 75% Complete (6/8 requirements fully implemented)**

| Feature | Status | Implementation | Gap |
|---------|--------|----------------|-----|
| **App Has Name** | ✅ Complete | AppMeta with name, description, version, author, timestamps | None |
| **App Has Schema** | ⚠️ Partial | Schema builder exists but not app-scoped | Need to link schemas to apps |
| **App Has Pages** | ✅ Complete | Full page management with 7 templates, 2-step wizard | None |
| **App Has Navigation** | ⚠️ Partial | Runtime nav works, no design UI | Need navigation builder UI |
| **Page Builder** | ✅ Complete | 3-panel drag-drop builder with live preview | None |
| **Forms in Pages** | ✅ Complete | All form components available (input, dropdown, etc.) | None |
| **Data Grids** | ✅ Complete | Table component + Data Table template | None |
| **Nav/Sidenav** | ✅ Complete | Navigation and sidebar components + templates | None |

### App Metadata Structure

```typescript
export interface AppMeta {
  id: string;                    // Unique identifier (e.g., "my-crm-app")
  name: string;                  // Display name (e.g., "My CRM Application")
  description?: string;          // Optional description
  version: string;               // Semantic version (e.g., "1.0.0")
  author?: string;               // Author name or organization
  created: number;               // Creation timestamp (ms)
  updated: number;               // Last update timestamp (ms)
  
  // App structure
  pages: string[];               // Array of page IDs ✅
  defaultPage?: string;          // Default/home page ID
  
  // Future enhancements
  schemas?: string[];            // Array of schema names (⚠️ TODO)
  navigation?: NavigationMeta;   // Custom navigation structure (⚠️ TODO)
  
  // App-wide settings
  theme?: AppTheme;              // Visual theme settings
  routes?: AppRoutes;            // Routing configuration
  metadata?: Record<string, any>; // Custom metadata
}
```

### Page Metadata Structure

```typescript
export interface PageMeta {
  metaVersion?: number;          // Migration hook
  id: string;                    // Unique page identifier
  name: string;                  // Display name
  path: string;                  // URL path (e.g., "/dashboard")
  rootId: string;                // Root component node ID
  nodes: ComponentNode[];        // All component nodes in page tree
  type?: string;                 // 'page' | 'component' (for reusability)
}

export interface ComponentNode {
  id: string;                    // Unique node identifier
  type: string;                  // 'container' | 'text' | 'button' | etc.
  props?: Record<string, any>;   // Component properties
  children?: string[];           // Child node IDs
  style?: Record<string, string>; // Inline styles
}
```

### Storage Architecture

Hierarchical localStorage pattern for efficient app/page management:

```typescript
// App registry
localStorage['appbana.apps.list'] = ['app1', 'app2', 'app3'];

// App metadata
localStorage['appbana.apps.app1'] = { 
  id: 'app1', 
  name: 'CRM App', 
  pages: ['home', 'dashboard', 'contacts'] 
};

// Individual pages (stored separately for performance)
localStorage['appbana.apps.app1.page.home'] = { /* PageMeta */ };
localStorage['appbana.apps.app1.page.dashboard'] = { /* PageMeta */ };
localStorage['appbana.apps.app1.page.contacts'] = { /* PageMeta */ };

// Current app context
localStorage['appbana.current.app'] = 'app1';
```

**Benefits:**
- **Lazy loading:** Load pages on-demand, not all at once
- **Isolation:** Changes to one page don't affect others
- **Scalability:** Supports apps with 50+ pages efficiently
- **Version control:** Easy to export/import individual pages

### Studio Components

#### 1. AppManager (`src/builder/components/AppManager.ts`)
**Purpose:** App lifecycle management

**Features:**
- Create new apps with templates (Blank, Single-page, Dashboard)
- Select/switch between apps
- View app list with page counts
- Delete apps and all associated pages

**Templates:**
- **Blank:** Empty app with no initial pages
- **Single-page:** App with header, content, footer structure
- **Dashboard:** App with sidebar navigation layout

#### 2. PageManager (`src/builder/components/PageManager.ts`)
**Purpose:** Page creation and management within an app

**Features:**
- 2-step page creation wizard:
  - Step 1: Basic info (name, path)
  - Step 2: Template selection (7 templates)
- Page tabs for quick switching
- 7 pre-built templates:
  - **Blank:** Empty canvas
  - **Login:** Email/password form with submit
  - **Dashboard:** Header + sidebar + 3 KPI cards
  - **Contact:** Name/email/message form
  - **Landing:** Hero + features + CTA + footer
  - **Profile:** Avatar + bio + stats grid
  - **Data Table:** Search + filters + table + pagination

**Time Savings:** 93% reduction (30 min → 2 min per page)

#### 3. BuilderCanvas (`src/builder/components/BuilderCanvas.ts`)
**Purpose:** Visual page editor with drag-drop

**Features:**
- 3-panel layout:
  - Left: Component library (drag source)
  - Center: Live canvas (drop target)
  - Right: Properties inspector (edit selected)
- Drag-drop component placement
- Visual tree view of component hierarchy
- Real-time preview
- Undo/Redo (Cmd/Ctrl+Z)
- Copy/Duplicate (Cmd/Ctrl+D)

#### 4. LivePreview (`src/builder/components/LivePreview.ts`)
**Purpose:** Preview pages with full app context

**Features:**
- Opens preview in new browser tab
- Includes full app metadata (name, pages, navigation)
- Renders header with app name, PREVIEW badge
- Page navigation links (switch between pages)
- Proper runtime environment

**Architecture:**
```typescript
// Preview state passed via URL parameter
const runtimeState = {
  appId: 'app1',
  pageId: 'dashboard',
  mode: 'preview'
};
const encoded = btoa(JSON.stringify(runtimeState));
window.open(`/index.html?state=${encoded}`);
```

#### 5. AppStore (`src/builder/store/AppStore.ts`)
**Purpose:** Centralized state management for apps and pages

**Key Methods:**
```typescript
// App operations
createApp(request: CreateAppRequest): AppMeta
updateApp(appId: string, updates: UpdateAppRequest): AppMeta
deleteApp(appId: string): void
getApp(appId: string): AppMeta | undefined
setCurrentApp(appId: string): void
listApps(): AppListItem[]

// Page operations
addPage(appId: string, page: PageMeta): void
removePage(appId: string, pageId: string): void
loadPage(appId: string, pageId: string): PageMeta | undefined
savePage(appId: string, page: PageMeta): void
```

### Architecture Gaps & Roadmap

#### Gap #1: App-Scoped Schema Management ⚠️

**Current State:**
- Schema builder exists (`src/schema-builder.ts`)
- Supports Relational, Document, Resource schemas
- **BUT:** Schemas are global, not linked to apps

**Target State:**
```typescript
export interface AppMeta {
  schemas: string[];  // Array of schema names linked to this app
}

// Storage pattern
localStorage['appbana.apps.app1.schema.users'] = { /* Schema */ };
localStorage['appbana.apps.app1.schema.orders'] = { /* Schema */ };
```

**Implementation Plan (1-2 days):**
1. Add `schemas: string[]` to AppMeta interface
2. Create SchemaManager component for Studio
3. Add "Schemas" tab next to "Pages" in Studio Builder
4. Update storage to be app-scoped
5. Link existing schema-builder.ts with app context

**Business Value:**
- Data-driven pages (connect forms/tables to schemas)
- Better organization (schemas grouped by app)
- Enables form auto-generation from schema

#### Gap #2: Navigation Builder UI ⚠️

**Current State:**
- NavigationMeta interfaces exist in `src/models/metadata.ts`
- Runtime preview auto-generates navigation from pages
- **BUT:** No UI to design/customize navigation structure

**Target State:**
```typescript
export interface AppMeta {
  navigation?: NavigationMeta;  // Custom navigation structure
}

export interface NavigationMeta {
  items: NavigationItem[];
}

export interface NavigationItem {
  id: string;
  label: string;           // Display text (may differ from page name)
  path: string;            // URL path
  icon?: string;           // Optional icon
  children?: NavigationItem[]; // Nested menus (dropdowns)
  visible?: boolean;       // Show/hide in menu
}
```

**Implementation Plan (2-3 days):**
1. Create NavigationBuilder component with drag-drop tree editor
2. Add "Navigation" tab in Studio Builder
3. Features:
   - Reorder items (drag-drop)
   - Edit labels, icons, paths
   - Create nested menus (dropdowns)
   - Toggle visibility
   - Add external links (not tied to pages)
4. Integrate with runtime renderer

**Business Value:**
- Custom menu structures (mega menus, dropdowns)
- Hide pages from navigation (admin pages, etc.)
- Multi-level navigation for complex apps
- Better UX control

### Component Library

**Available in Builder:**

| Category | Components |
|----------|-----------|
| **Basic** | Text, Button, Image, Container |
| **Forms** | Text Input, Dropdown, Checkbox, Radio, Textarea, Date Picker |
| **Layout** | Grid, Flex Column, Flex Row, Card |
| **Display** | Table, Chart, Map, Timeline |
| **Navigation** | Nav Bar, Sidebar, Breadcrumbs |

**Extensibility:**
- Custom components via plugin architecture (Phase D)
- Component registration system in `src/core/registry.ts`
- Lazy-loading for performance

### Success Metrics

**Current (October 2025):**
- ✅ Can create multi-page apps visually
- ✅ 7 pre-built templates available
- ✅ 93% time savings for page creation
- ✅ Full drag-drop page builder
- ✅ Preview with app context
- ⚠️ Cannot manage app-specific schemas (yet)
- ⚠️ Cannot customize navigation structure (yet)

**Target (Q1 2026):**
- ✅ All 8 architectural requirements implemented
- ✅ Schema builder integrated with apps
- ✅ Visual navigation designer available
- ✅ Full app lifecycle: Create → Schemas → Pages → Navigation → Publish

---

## Tech Stack & Dependencies

### Backend
| Component | Technology | Version | Purpose |
|-----------|-----------|---------|---------|
| **Runtime** | Java | 25 LTS | Virtual threads support; framework-free HTTP server |
| **HTTP Server** | JDK HttpServer or Tomcat | Built-in / 10.1.25 | Lightweight HTTP handling |
| **Database** | H2 (default) | 2.2.222 | Embedded dev/test database |
| **Connection Pool** | HikariCP | Latest | Efficient multi-datasource pooling |
| **JSON** | Jackson | 2.15.2 | Type-safe JSON processing |
| **Logging** | SLF4J | Latest | Simple event logging |
| **Build** | Maven | Multi-module parent | Orchestrates UI + service builds |

### Frontend
| Component | Technology | Version | Purpose |
|-----------|-----------|---------|---------|
| **Language** | TypeScript | 5.2.2+ | Type-safe UI development |
| **Framework** | Lit + Web Components | 3.1.4 | Reactive component model |
| **Build Tool** | Vite | 5.3.1+ | Fast dev server + optimized prod build |
| **Testing** | Vitest + jsdom | 1.5.0+ | Fast unit tests with DOM simulation |
| **Styling** | CSS (scoped) | Native | Shadow DOM style encapsulation |

### Database Support

| Database | Driver | Status | Notes |
|----------|--------|--------|-------|
| H2 | Embedded | ✅ Default | File-based; zero config |
| PostgreSQL | JDBC | ✅ Tested | Recommended for production |
| MySQL / MariaDB | JDBC | ✅ Supported | Drop-in replacement |
| SQL Server | JDBC | ⏳ Planned | Dialect adjustments pending |
| Oracle | JDBC | ⏳ Planned | Identifier quoting nuances |
| SQLite | JDBC | ✅ Mobile support | Future mobile targets |

---

## System Layers

### Layer Overview

```
┌─────────────────────────────────────────────┐
│          Frontend (Web Components)          │
│  (TypeScript, Lit, Vite, Vitest)           │
├─────────────────────────────────────────────┤
│   REST API Layer (OpenAPI 3.0 spec)        │
│  (/schema, /api/*, /audit, /openapi.json) │
├─────────────────────────────────────────────┤
│   Backend Service Layer                     │
│  (ApiServer, SchemaManager, JdbcManager)   │
├─────────────────────────────────────────────┤
│   Datasource Abstraction Layer              │
│  (ConfigManager, HikariCP, per-DS config)  │
├─────────────────────────────────────────────┤
│   Database Layer (JDBC)                     │
│  (H2, PostgreSQL, MySQL, Oracle, etc.)     │
└─────────────────────────────────────────────┘
```

### Backend Modules

#### `ApiServer.java`
- **Purpose:** Core HTTP server, routing, middleware chain
- **Key Responsibilities:**
  - HTTP request routing (schema CRUD, entity CRUD, audit, health)
  - Authentication (token extraction, permission checks)
  - Response serialization (JSON, HTML)
  - Serves UI assets (`/ui/*`)
- **Key Methods:**
  - `startJdk()` - JDK HttpServer initialization
  - `buildRouter()` - Route configuration
  - `authEnabled()`, `extractToken()`, `hasAdmin()`, `hasRead()` - Auth helpers

#### `SchemaManager.java`
- **Purpose:** Schema lifecycle and migration planning
- **Key Responsibilities:**
  - Load/save schema definitions
  - Generate DDL statements
  - Plan safe migrations (rename, add column, reorder)
  - Track migration history
  - Validate schema changes
- **Key Methods:**
  - `init()` - Bootstrap DB metadata
  - `saveSchema()` - Persist schema
  - `getSchema()` - Retrieve schema
  - `generateMigrationPlan()` - Plan DDL changes
  - `listMigrations()` - History

#### `JdbcManager.java`
- **Purpose:** Database abstraction and connection lifecycle
- **Key Responsibilities:**
  - JDBC connection acquisition
  - HikariCP pool initialization and configuration
  - Execute CRUD operations
  - ResultSet conversion to Maps
- **Key Methods:**
  - `getConnection()` - Active datasource connection
  - `execute()` - Generic SQL execution
  - `toList()` - ResultSet → List<Map>

#### `ConfigManager.java` & `AppConfig.java`
- **Purpose:** Multi-datasource configuration management
- **Key Responsibilities:**
  - Load config from file / environment variables
  - Persist datasource settings
  - Test datasource connectivity
  - Switch active datasource
  - HTTPS/auth token configuration
- **Key Methods:**
  - `getConfig()` - Current AppConfig singleton
  - `loadDatasources()` - Restore persisted datasources
  - `saveDatasources()` - Persist datasource list
  - `testDatasource()` - Connection test

#### `AuditLogService.java`
- **Purpose:** CRUD audit logging (baseline)
- **Key Responsibilities:**
  - Write INSERT/UPDATE/DELETE operations
  - Capture before/after snapshots
  - Track field-level diffs
  - Support batch operations
  - Persist to `appbana_audit` table
- **Query:** Via `/api/audit` with entity/pk filtering

#### `OpenApiGenerator.java`
- **Purpose:** Generate OpenAPI 3.0 specification
- **Key Responsibilities:**
  - Introspect loaded schemas
  - Generate component schemas for each entity
  - Build path specs (CRUD operations)
  - Live endpoint: `/openapi.json`
  - Browsable via `/ui/swagger`

---

## Database & Datasources

### Schema Model (EntitySchema.java)

```typescript
interface EntitySchema {
  name: string;
  fields: Field[];
  // Optional metadata
  description?: string;
  createdAt?: Instant;
  updatedAt?: Instant;
}

interface Field {
  name: string;
  type: string;          // int, long, string, boolean, date, timestamp, text
  primaryKey?: boolean;
  autoIncrement?: boolean;
  length?: number;
  required?: boolean;
  pattern?: string;      // Regex validation
  min?: number;
  max?: number;
  existingName?: string; // For rename support
}
```

### Datasource Configuration

```typescript
interface DatasourceConfig {
  name: string;
  type: 'h2' | 'postgresql' | 'mysql' | 'oracle' | 'sqlserver' | 'sqlite';
  url: string;
  username: string;
  password: string;
  poolSize?: number;     // HikariCP
  connectionTimeout?: number;
  idleTimeout?: number;
}
```

### Auto-Migration Strategy

- **No Down-Time:** Uses ALTER TABLE (H2, PostgreSQL) instead of recreate
- **Field Rename:** Tracks `existingName` to generate safe RENAME SQL
- **Add Column:** Generates ADD COLUMN with default values if required
- **Field Reorder:** UI-only reordering (does not affect schema)
- **Validation:** Pre-checks for unsafe operations (drop, type change incompatibility)

### Audit Table

```sql
CREATE TABLE appbana_audit (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  entity_name VARCHAR(255) NOT NULL,
  entity_id VARCHAR(255) NOT NULL,
  action VARCHAR(50) NOT NULL,  -- INSERT, UPDATE, DELETE
  before_values JSON,
  after_values JSON,
  field_diffs JSON,              -- { field: { old, new } }
  user_id VARCHAR(255),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## API Design

### Core Endpoints

#### Schema Management
```
GET    /schema              → List schema names
GET    /schema/{name}       → Get schema definition
GET    /schema/summaries    → Get all schemas with field count
POST   /schema              → Create/update schema (or preview with ?preview=true)
POST   /schema/{name}/migrations → Get migration history
DELETE /schema/{name}       → Delete schema (optionally drop table)
```

#### Entity CRUD
```
GET    /api/{entity}        → Query entities (pagination, search, projection, filters)
GET    /api/{entity}/{id}   → Get single entity
POST   /api/{entity}        → Insert single or batch
PUT    /api/{entity}/{id}   → Update entity
DELETE /api/{entity}/{id}   → Delete entity
DELETE /api/{entity}        → Batch delete (with filters)
```

#### Audit & Health
```
GET    /api/audit           → Query audit logs (entity, pk, action, date range)
GET    /api/audit/{id}      → Get audit entry by ID
GET    /health              → Health check
GET    /ready               → Readiness (DB + datasource OK)
```

#### OpenAPI & Swagger
```
GET    /openapi.json        → OpenAPI 3.0 specification
GET    /ui/swagger          → Interactive Swagger UI
```

#### Datasource Management
```
GET    /ui/datasource               → List datasources
GET    /ui/datasource/{id}          → Get datasource config
POST   /ui/datasource               → Create datasource
PUT    /ui/datasource/{id}          → Update datasource
DELETE /ui/datasource/{id}          → Delete datasource
POST   /ui/datasource/test          → Test connection
PUT    /ui/datasource/{id}/activate → Switch active datasource
```

### Query Parameters (Entity CRUD)

```
GET /api/person?
  limit=50                   → Pagination
  offset=100
  search=John                → Full-text search on searchable fields
  name=John&age=30           → Field filters (AND logic)
  name:like=%oh%             → Advanced filters (:like, :>, :<, :in, etc.)
  _fields=name,email         → Projection (limit columns)
  _sort=name:asc,age:desc    → Sorting
  _count=true                → Return only count
```

### Error Response Format

```json
{
  "ok": false,
  "error": "...",
  "sqlState": "...",
  "errorCode": 123
}
```

---

## UI/Frontend Architecture

### Studio Framework (Custom, Phase A→B)

AppBana's UI is built on a **custom lightweight runtime** ("Studio") using Web Components + Lit, not a heavy SPA framework.

#### Two Cooperating Runtimes

1. **Builder (Design-time)**
   - Visual editor for pages/components
   - Tree view with drag-drop
   - Property inspector
   - Design token management
   - Local draft persistence
   - Keyboard shortcuts

2. **Runtime (Render-time)**
   - Walks page metadata (ComponentNode graph)
   - Instantiates registered Web Components
   - Binds data to props
   - Handles events
   - Applies design tokens

#### Phase Progression

| Phase | Timeline | Focus | Status |
|-------|----------|-------|--------|
| **A** | Oct 2025 | Foundation (BaseElement, registry, core components) | ✅ Complete |
| **B** | Oct-Nov 2025 | Builder MVP (canvas, inspector, undo/redo) | 🔄 In Progress |
| **C** | Nov 2025 | Advanced Features (bindings, actions, plugins) | ⏳ Pending |
| **D** | Dec 2025 | Plugin Marketplace & Governance | ⏳ Pending |

---

## Component System

### 3-File Component Pattern

All components follow a clean three-file structure (similar to Angular):

```
ComponentName/
├── ComponentName.ts      # Logic, state, templates (Lit)
├── ComponentName.css     # Scoped styles (Shadow DOM)
└── ComponentName.html    # Reference documentation
```

### TypeScript Component Template

```typescript
import { LitElement, html, css, unsafeCSS } from 'lit';
import { customElement, state, property } from 'lit/decorators.js';
import styles from './ComponentName.css?inline';

@customElement('component-name')
export class ComponentName extends LitElement {
  static styles = css`${unsafeCSS(styles)}`;
  
  // Public API
  @property({ type: String }) label = '';
  
  // Internal state
  @state() private isActive = false;

  // Lifecycle
  connectedCallback() {
    super.connectedCallback();
    // Initialize
  }

  // Event handlers
  private handleClick() {
    this.isActive = !this.isActive;
    this.dispatchEvent(new CustomEvent('toggle', {
      detail: { active: this.isActive },
      bubbles: true
    }));
  }

  // Render
  render() {
    return html`
      <div class="container ${this.isActive ? 'active' : ''}">
        <button @click=${this.handleClick}>${this.label}</button>
      </div>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'component-name': ComponentName;
  }
}
```

### CSS Module Import

CSS files are imported with the `?inline` suffix to load as strings:

```typescript
import styles from './ComponentName.css?inline';

// In component:
static styles = css`${unsafeCSS(styles)}`;
```

**Type Declaration** (`vite-env.d.ts`):
```typescript
declare module '*.css?inline' {
  const content: string;
  export default content;
}
```

### Component Registry

Dynamic component registration for plugin support:

```typescript
// core/registry.ts
export interface ComponentRegistry {
  register(type: string, componentClass: CustomElementConstructor): void;
  get(type: string): CustomElementConstructor | undefined;
  list(): string[];
}

// Usage
registry.register('my-button', MyButtonComponent);
const Comp = registry.get('my-button'); // Later instantiate
```

### Core Components (Phase A)

| Component | Purpose |
|-----------|---------|
| `ContainerElement` | Flexbox layout wrapper |
| `TextElement` | Text/heading display |
| `ButtonElement` | Interactive button |
| `UnknownElement` | Fallback for missing components |

### Lifecycle Hooks

- `connectedCallback()` - Component inserted into DOM (initialize)
- `disconnectedCallback()` - Component removed (cleanup)
- `attributeChangedCallback()` - Attribute changes (reflection)
- `updated()` - After Lit's update cycle (optional custom logic)

---

## Data Binding Patterns

### 1. Reactive State (`@state`)

Internal state that triggers automatic re-renders:

```typescript
@state() private count = 0;

private increment() {
  this.count++; // Auto re-render
}
```

**Use Cases:** UI toggles, form inputs, component visibility, pagination

### 2. Properties (`@property`)

Component's public API:

```typescript
@property({ type: String }) label = '';
@property({ type: Number }) max = 100;
@property({ type: Boolean }) disabled = false;

// Can be set from outside:
element.label = 'Click Me';
element.disabled = true;
```

**Use Cases:** Configuration, initialization, component reusability

### 3. One-Way Property Binding (`.prop=`)

Bind component state to DOM element properties:

```typescript
render() {
  return html`
    <input .value=${this.inputText} />
    <select .value=${this.selectedOption}>
      ${this.options.map(opt => html`<option>${opt}</option>`)}
    </select>
  `;
}
```

**Use Cases:** Form control synchronization, list selection

### 4. Event Binding (`@event`)

Listen to DOM events and update state:

```typescript
render() {
  return html`
    <input @input=${e => this.inputText = e.target.value} />
    <button @click=${() => this.handleClick()}>Click</button>
  `;
}
```

**Use Cases:** User interactions, form submission, drag-drop

### 5. Two-Way Binding (Manual)

Combine property + event for controlled component:

```typescript
@state() private value = '';

render() {
  return html`
    <input 
      .value=${this.value}
      @input=${e => this.value = e.target.value} />
  `;
}
```

**Use Cases:** Form fields, search inputs, filters

### 6. Conditional Rendering

Show/hide elements based on state:

```typescript
render() {
  return html`
    ${this.isLoading ? html`<div>Loading...</div>` : ''}
    ${this.hasError ? html`<div class="error">${this.error}</div>` : ''}
    ${this.data ? html`<div>${this.data}</div>` : html`<div>No data</div>`}
  `;
}
```

### 7. List Rendering

Iterate over arrays:

```typescript
render() {
  return html`
    <ul>
      ${this.items.map((item, idx) => html`
        <li @click=${() => this.selectItem(idx)}>
          ${item.name}
        </li>
      `)}
    </ul>
  `;
}
```

**Best Practice:** Include unique keys for list items in future optimization

### 8. Attribute Binding

Set HTML attributes dynamically:

```typescript
render() {
  return html`
    <div 
      id=${this.id}
      class=${this.isActive ? 'active' : ''}
      ?disabled=${this.isDisabled}
      aria-label=${this.label}>
    </div>
  `;
}
```

**Binding Types:**
- `attr=${value}` - String attribute
- `?attr=${bool}` - Boolean attribute (presence/absence)
- `.prop=${value}` - Property assignment

---

## Security & Auditing

### Authentication & Authorization

#### Token-Based Auth
- Optional (disabled by default)
- Token sources: `X-AppBana-Token` header or `Authorization: Bearer <token>`
- Tokens: `APPBANA_ADMIN_TOKEN`, `APPBANA_READ_TOKEN` (env vars)

#### Permission Model
```
Admin: Full access (create/read/update/delete schema, data, audit)
Read: Query-only access (read schema, data, audit)
None: Public access (if auth disabled)
```

#### Request-Level Checks
```java
// In handlers
if (!hasAdmin(token, config) && operation.isAdmin()) {
  res.status(403);
}
```

### Baseline CRUD Audit Logging

#### Captured Operations
- INSERT (single & batch)
- UPDATE
- DELETE

#### Audit Record
```json
{
  "entityName": "person",
  "entityId": "123",
  "action": "UPDATE",
  "beforeValues": { "name": "John", "email": "john@old.com" },
  "afterValues": { "name": "John", "email": "john@new.com" },
  "fieldDiffs": {
    "email": { "old": "john@old.com", "new": "john@new.com" }
  },
  "userId": "user-456",
  "createdAt": "2025-10-30T12:34:56Z"
}
```

#### Query Audit Logs
```
GET /api/audit?entity=person&entityId=123&action=UPDATE&limit=50
```

### SQL Injection Protection

All user input is parameterized:

```java
// ✅ Safe
PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM ? WHERE id = ?");
pstmt.setString(1, tableName);  // Entity name
pstmt.setObject(2, id);         // Filter value

// ❌ Unsafe (not done in AppBana)
String query = "SELECT * FROM " + userInput + " WHERE id = " + id;
```

### Future Security Enhancements

- **Field-Level Security (FLS):** Redact/restrict field access per user role
- **Encryption at Rest:** Encrypt sensitive data in database
- **HIPAA Compliance:** PHI handling, break-glass access, session timeout

---

## Configuration Management

### Configuration Sources (Priority Order)

1. **Environment Variables** (highest priority)
   - `APPBANA_PORT`
   - `APPBANA_CONFIG` (path to config file)
   - `APPBANA_ADMIN_TOKEN`, `APPBANA_READ_TOKEN`
   - `APPBANA_HTTPS_ENABLED`, `APPBANA_KEYSTORE_PATH`, etc.

2. **System Properties** (JVM flags)
   - `-Dappbana.port=9090`
   - `-Dappbana.config=/path/to/config.json`

3. **Config File** (JSON)
   ```json
   {
     "port": 8080,
     "httpsEnabled": false,
     "datasources": [
       {
         "name": "default",
         "type": "h2",
         "url": "jdbc:h2:file:./appbana-db",
         "poolSize": 10
       }
     ]
   }
   ```

4. **Defaults** (lowest priority)
   - Port: 8080
   - Database: H2 in-memory
   - Auth: Disabled

### Datasource Persistence

- Datasources saved to file after creation/update
- Location: `${appbana.config.dir}/datasources.json`
- Restored on server restart

---

## Integration Points

### 1. Plugin Architecture (Future Phase D)

Components can be registered dynamically:

```typescript
// External plugin
const MyPlugin = {
  components: [{ type: 'my-chart', class: MyChartComponent }],
  dataConnectors: [{ type: 'my-api', class: MyApiConnector }],
};

// Load
registry.loadPlugin(MyPlugin);
```

### 2. API Interceptors (Client-Side)

Hook into request/response lifecycle:

```typescript
apiClient.interceptors.request((req) => {
  req.headers['X-Custom-Header'] = 'value';
  return req;
});

apiClient.interceptors.response((res) => {
  console.log('Response:', res);
  return res;
});

apiClient.interceptors.error((err) => {
  showErrorToast(err.message);
  throw err;
});
```

### 3. Datasource Connectors

Support multiple backend types:

```typescript
interface DataConnector {
  query(datasource: DatasourceConfig, sql: string): Promise<any[]>;
  execute(datasource: DatasourceConfig, sql: string): Promise<void>;
}

// JDBC (default)
// REST API (future)
// GraphQL (future)
```

### 4. Custom Actions (Builder)

Trigger workflows or integrations:

```typescript
interface Action {
  name: string;
  trigger: 'click' | 'change' | 'submit';
  handler: (context: ActionContext) => Promise<void>;
}

// Examples: Submit form, Fetch data, Navigate, Trigger workflow
```

---

## Summary

AppBana's architecture is built on **metadata-driven end-to-end cohesion**:

- **Database Layer:** JDBC abstraction with multi-datasource support
- **API Layer:** Auto-generated REST CRUD endpoints with OpenAPI spec
- **Service Layer:** Schema management, audit logging, configuration
- **UI Layer:** Custom lightweight framework (Studio) with Web Components + Lit
- **Security:** Token auth, CRUD audit logging, parameterized queries
- **Extensibility:** Plugin architecture (Phase D), custom components, interceptors

This design prioritizes **simplicity**, **enterprise readiness**, and **vertical-specific extensibility**.

---

**Related Documents:**
- `02-DEVELOPMENT_GUIDE.md` - Development setup, building, testing
- `03-ROADMAP.md` - Product roadmap and feature delivery timeline
