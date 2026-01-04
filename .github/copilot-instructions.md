# GitHub Copilot Instructions for AppBana

## Project Overview
AppBana is a **metadata-driven platform** generating end-to-end functionality from a single source: `Entity Definition → Schema → Database → REST APIs → UI Pages`. Changes to metadata propagate automatically.

**Core Architecture Pattern**:
```
Metadata JSON (builder-database/*.json)
    ↓
Backend Services (Java 21 - ApiServer, SchemaManager)
    ↓
Database (H2/PostgreSQL + Flyway migrations)
    ↓
REST APIs (Auto-generated CRUD + OpenAPI spec)
    ↓
Frontend (Lit Web Components + TypeScript)
    ↓
Runtime UI (Rendered from PageMeta/ComponentNode tree)
```

---

## CURRENT PRIORITIES (January 2026)

### 1. Multi-Tenant Architecture 🔴 ACTIVE SPRINT
**Status**: Runtime publishing and URL tenant isolation  
**Branch**: runtime-publish  
**Key Pattern**: `TenantContext` ThreadLocal for request-scoped tenant isolation

**Critical Files**:
- `model/TenantContext.java` - ThreadLocal tenant context holder
- `model/AppMetadata.java` - Has `tenantId` field
- `model/User.java` - Has `tenantId` field for tenant isolation
- `AppManager.java` - App CRUD with tenant filtering

**Usage Pattern**:
```java
// Set tenant context (in middleware/request handler)
TenantContext.set(new TenantContext("tenant-123", "app-hr"));
try {
    // All operations now tenant-scoped
    crud.insertRecord(TenantContext.get(), schema, data);
} finally {
    TenantContext.clear(); // Always cleanup
}
```

### 2. Workflow Automation Phase 1 ✅ PRODUCTION READY
**Status**: 95% Complete (Runtime verification pending)  
**Components**: WorkflowEngine, WorkflowService, WorkflowExecutionService  
**Database**: 4 tables via Flyway migration V6  
**Reference**: [WORKFLOW_FEATURE_SPEC.md](../docs/WORKFLOW_FEATURE_SPEC.md)

### 3. Security Suite ✅ PRODUCTION READY
**Status**: 156/156 tests passing (100% coverage)  
**Components**: CSRF, Session Management, Rate Limiting, Field-Level Security  
**Reference**: [SECURITY_FEATURES.md](../docs/SECURITY_FEATURES.md)

### 4. AI Builder Experience (ONGOING)
**Key**: `builder-database/*.json` files drive ALL AI behavior through metadata  
**Pattern**: Metadata Intelligence Engine hot-reloads JSON without restart  
**Files**: `11-intent-patterns.json`, `AiAppGeneratorService.java`, `MetadataIntelligenceEngine.java`

---

## Technology Stack & Build System

### Backend (Java 21 LTS)
- **HTTP Server**: JDK HttpServer (default) or Tomcat (configurable via `config.json`)
- **Entry Point**: `com.appbana.Main` → starts server on port 8080 (configurable)
- **Database**: H2 (default file-based), PostgreSQL (production)
- **Connection Pool**: HikariCP with per-datasource configuration
- **Migrations**: Flyway OSS 10.4.1 (auto-runs on startup, `.clean()` in dev mode)
- **JSON**: Jackson 2.18.2 with JSR310 module for Java 8 date/time
- **Logging**: SLF4J-simple
- **Virtual Threads**: `server.setExecutor(r -> Thread.ofVirtual().start(r))` for 10K+ concurrent requests

**Build Tool**: Maven multi-module project
```bash
# Build everything from root
mvn clean package -DskipTests

# Produces: app-bana-service/target/app-bana-1.0-SNAPSHOT-fat.jar
```

### Frontend (TypeScript + Lit)
- **Framework**: Lit 3.1.4 (Web Components with Shadow DOM)
- **Build**: Vite 5.3.1 (fast dev server, optimized production builds)
- **Testing**: Vitest 1.5.0 + jsdom
- **Package Manager**: npm (Node 18.17+ required)

**Component Pattern**:
```typescript
import { LitElement, html, css, unsafeCSS } from 'lit';
import { customElement, state, property } from 'lit/decorators.js';
import styles from './MyComponent.css?inline';

@customElement('my-component')
export class MyComponent extends LitElement {
  static styles = css`${unsafeCSS(styles)}`;
  
  @property({ type: String }) label = '';
  @state() private isActive = false;
  
  render() {
    return html`<button @click=${this.handleClick}>${this.label}</button>`;
  }
}
```

**Build Commands**:
```bash
cd app-bana-ui
npm install
npm run dev      # Development server on http://localhost:5173
npm run build    # Production build → src/main/resources/ui/dist/
npm test         # Run Vitest tests
```

---

## Development Workflows

### Quick Start Scripts (macOS/Linux)

**Backend Restart** (ALWAYS USE THIS):
```bash
./restart-backend.sh
```
- Manages PostgreSQL Docker container (auto-starts if needed)
- Stops running backend gracefully (via PID file)
- Builds: `mvn clean package -DskipTests`
- Starts backend with logging to `backend.log`
- Saves PID to `backend.pid`

**Frontend Dev Server**:
```bash
./run-ui.sh         # Start Vite dev server
./run-ui.sh build   # Production build
./run-ui.sh preview # Preview production build
./run-ui.sh clean   # Clean node_modules and dist
```
- Auto-kills existing server on port 5173
- Checks Node version (18.17+ required)
- Installs dependencies if needed
- **DO NOT manually kill Vite processes** - script handles it

### Windows PowerShell Workflow

**⚠️ CRITICAL PATTERN**:
```powershell
# Terminal 1: Backend (continuous logs) - NEVER RUN COMMANDS HERE!
.\start-backend.bat

# Terminal 2: Commands and testing - ALWAYS USE THIS TERMINAL
mvn clean compile
Invoke-WebRequest -Uri "http://localhost:8080/apps"
```

**Why**: Running commands in Terminal 1 stops the backend server

### Database Migration Workflow

**Location**: `app-bana-service/src/main/resources/db/migration/`  
**Convention**: `V{version}__{description}.sql`

**Current Migrations**:
- V1: Auth schema (User/Role/Permission + seed data)
- V2: Field-level security tables
- V3-V5: System tables
- V6: Workflow tables (definition, instance, token, history)

**Development Pattern**:
```bash
# Clean slate testing
rm -f data/appbana.*          # Delete H2 database files
./restart-backend.sh          # Rebuild + restart (Flyway auto-runs)
```

**⚠️ Development Mode**: Flyway runs `.clean()` on startup (drops all objects)  
**Production**: Disable `.clean()` in ConfigManager before deploying

**Column Naming Convention**: Use `can_read`, `can_edit` (not `readable`, `editable`) to match Java getter conventions

---

## Key Architecture Patterns

### 1. Metadata-Driven Entity-to-UI Flow

**The Core Pattern**:
```
1. Define Entity → builder-database/03-entities.json (AI-readable)
2. Create Schema → POST /schema (SchemaManager.saveSchema())
3. Auto-create Table → Flyway migration or ALTER TABLE
4. Generate APIs → GET/POST/PUT/DELETE /api/{entity}
5. Render UI → PageMeta + ComponentNode tree → Lit components
```

**Critical Classes**:
- `SchemaManager.java` - Schema lifecycle, DDL generation, migration planning
- `JdbcManager.java` - Database abstraction, HikariCP pool management
- `ApiServer.java` - HTTP routing, CRUD endpoint generation
- `AppStore.ts` - Frontend global state (singleton, never instantiate)
- `app-renderer.ts` - Runtime page renderer from metadata

### 2. Builder Database Pattern (AI Intelligence)

**Location**: `builder-database/*.json`

**Purpose**: Machine-readable capability definitions that drive AI behavior

**Key Files**:
- `01-core-concepts.json` - Fundamental concepts
- `02-components.json` - All UI components (19 types)
- `11-intent-patterns.json` - AI intent classification rules
- `99-capabilities-index.json` - Quick reference index

**Pattern**: Metadata Intelligence Engine hot-reloads JSON without restart
```java
// MetadataIntelligenceEngine.java
POST /api/meta-intelligence/reload    // Reload JSON files
GET /api/meta-intelligence/classify?input=xxx  // Classify intent
```

**AI Builder Integration**:
```typescript
// AiChatBuilder.ts
@customElement('ai-chat-builder')
export class AiChatBuilder extends LitElement {
  // Connects to AiAppGeneratorService.java
  // Uses intent patterns from 11-intent-patterns.json
}
```

### 3. Component Registration System

**Pattern**: Dynamic component registry for runtime extensibility

```typescript
// core/registry.ts
export const registry = {
  register(type: string, componentClass: CustomElementConstructor): void,
  get(type: string): CustomElementConstructor | undefined,
  list(): string[]
};

// Usage in components
registry.register('my-button', MyButtonComponent);
```

**Fallback Component**:
```typescript
// components/UnknownElement.ts
@customElement('unknown-element')
export class UnknownElement extends LitElement {
  // Renders when component type not found
  // Shows placeholder with component type name
}
```

### 4. Multi-Tenant Isolation Pattern

**Key**: `TenantContext` ThreadLocal for request-scoped tenant isolation

```java
// model/TenantContext.java
public class TenantContext {
    private static final ThreadLocal<TenantContext> CONTEXT = new ThreadLocal<>();
    
    private final String tenantId;
    private final String appId;
    
    public static void set(TenantContext context) {
        CONTEXT.set(context);
    }
    
    public static TenantContext get() {
        return CONTEXT.get();
    }
    
    public static void clear() {
        CONTEXT.remove(); // ALWAYS cleanup in finally block
    }
}
```

**Usage Pattern** (in middleware/handlers):
```java
TenantContext.set(new TenantContext(tenantId, appId));
try {
    // All operations inherit tenant context
    List<Map<String, Object>> data = crud.query(TenantContext.get(), entity, filters);
} finally {
    TenantContext.clear(); // Prevent thread pool pollution
}
```

**Tenant-Aware Models**:
- `AppMetadata.java` - Has `tenantId` field
- `User.java` - Has `tenantId` field
- `FieldPermission.java` - Scoped by tenant

### 5. Workflow Engine Pattern

**Location**: `app-bana-service/src/main/java/com/appbana/workflow/`

**Tables** (Flyway V6):
- `appbana_wf_definition` - Workflow templates
- `appbana_wf_instance` - Running workflows
- `appbana_wf_token` - Current execution state
- `appbana_wf_history` - Audit trail

**Key Classes**:
- `WorkflowEngine.java` - State machine execution
- `WorkflowService.java` - CRUD + publish
- `WorkflowExecutionService.java` - Trigger + execute
- `ExpressionEvaluator.java` - Condition evaluation

**Trigger Pattern**:
```java
// Workflows trigger on entity creation events
// PostOperationHooks integration
POST /api/{entity} → trigger workflows → create tasks
```

---

## Java 21 Best Practices (ALWAYS USE)

### Modern Language Features

**Virtual Threads** (already implemented):
```java
// ApiServer.java
server.setExecutor(r -> Thread.ofVirtual().start(r));
// Handles 10,000+ concurrent requests vs 200 with platform threads
```

**Records for Immutable DTOs**:
```java
// model/dto/UserDTO.java
public record UserDTO(Long id, String email, String name) {
    public static UserDTO fromUser(User user) {
        return new UserDTO(user.getId(), user.getEmail(), user.getName());
    }
}
```

**Switch Expressions** (no fall-through):
```java
String url = switch (type) {
    case "h2" -> { yield "jdbc:h2:file:./data/appbana"; }
    case "postgres" -> { yield "jdbc:postgresql://localhost:5432/appbana"; }
    case "mysql" -> { yield "jdbc:mysql://localhost:3306/appbana"; }
    default -> null;
};
```

**Pattern Matching**:
```java
if (obj instanceof String s && s.length() > 5) {
    // Use 's' directly
}
```

**Text Blocks** (for SQL/JSON):
```java
String sql = """
    SELECT u.id, u.email, r.name as role
    FROM users u
    JOIN roles r ON u.role_id = r.id
    WHERE u.status = 'active'
    """;
```

### Lombok vs Records Strategy

| Use Case | Tool | Reason |
|----------|------|--------|
| **Mutable entities** (User, Role, Permission) | Lombok `@Data` | Need setters, builders |
| **Immutable DTOs** (UserDTO, API responses) | Java 21 `record` | Immutable, less code |
| **Configuration** (AppConfig, DatasourceConfig) | Java 21 `record` | Immutable config |

**Example**:
```java
// Mutable entity
@Data
public class User {
    private Long id;
    private String email;
    private String tenantId;
}

// Immutable DTO
public record UserDTO(Long id, String email) {
    public static UserDTO fromUser(User user) { ... }
}
```

---

## Frontend Patterns

### Component Lifecycle

```typescript
@customElement('my-component')
export class MyComponent extends LitElement {
  
  // 1. Constructor (rarely used, prefer properties/state)
  constructor() {
    super();
  }
  
  // 2. Connected to DOM
  connectedCallback() {
    super.connectedCallback();
    // Subscribe to stores, add event listeners
    appStore.subscribe(this.handleStoreChange);
  }
  
  // 3. Disconnected from DOM
  disconnectedCallback() {
    super.disconnectedCallback();
    // Cleanup: unsubscribe, remove listeners
    appStore.unsubscribe(this.handleStoreChange);
  }
  
  // 4. After each render
  updated(changedProperties: Map<string, any>) {
    super.updated(changedProperties);
    // React to property changes
  }
}
```

### State Management Pattern

**Global State**: Use `AppStore.ts` singleton
```typescript
// WRONG: Never instantiate
const store = new AppStore(); // ❌

// CORRECT: Import singleton
import { appStore } from '../store/AppStore';
appStore.setCurrentApp('my-app'); // ✅
```

**Component State**:
```typescript
@state() private isLoading = false;  // Triggers re-render
@property({ type: String }) label = '';  // Public API
```

### CSS Import Pattern

**ALWAYS use `?inline` suffix**:
```typescript
import styles from './MyComponent.css?inline';

static styles = css`${unsafeCSS(styles)}`;
```

**Type Declaration** (`vite-env.d.ts`):
```typescript
declare module '*.css?inline' {
  const content: string;
  export default content;
}
```

### Metadata-Driven Rendering

**Page Structure**:
```typescript
interface PageMeta {
  id: string;
  name: string;
  rootId: string;  // MUST match root node ID in nodes array
  nodes: ComponentNode[];
}

interface ComponentNode {
  id: string;
  type: string;  // Matches @customElement name
  props?: Record<string, any>;
  children?: string[];  // Array of node IDs (not objects!)
}
```

**Renderer Pattern**:
```typescript
// runtime/renderer/Renderer.ts
class Renderer {
  render(pageMeta: PageMeta): HTMLElement {
    const rootNode = pageMeta.nodes.find(n => n.id === pageMeta.rootId);
    return this.renderNode(rootNode, pageMeta.nodes);
  }
  
  private renderNode(node: ComponentNode, allNodes: ComponentNode[]): HTMLElement {
    const ComponentClass = registry.get(node.type) || UnknownElement;
    const element = new ComponentClass();
    
    // Set properties
    Object.entries(node.props || {}).forEach(([key, value]) => {
      element[key] = value;
    });
    
    // Render children recursively
    (node.children || []).forEach(childId => {
      const childNode = allNodes.find(n => n.id === childId);
      element.appendChild(this.renderNode(childNode, allNodes));
    });
    
    return element;
  }
}
```

---

## Testing Patterns

### Backend JUnit Tests

**Pattern**: JUnit 5 with `@BeforeEach` setup and `@Test` methods

```java
class MyServiceTest {
    private Connection testConnection;
    private MyService service;
    
    @BeforeEach
    void setUp() throws Exception {
        // Setup H2 in-memory database
        testConnection = DriverManager.getConnection("jdbc:h2:mem:test");
        service = new MyService(testConnection);
        
        // Create test tables
        try (Statement stmt = testConnection.createStatement()) {
            stmt.execute("""
                CREATE TABLE users (
                    id BIGINT PRIMARY KEY,
                    email VARCHAR(255),
                    tenant_id VARCHAR(255)
                )
                """);
        }
    }
    
    @Test
    void testSomething() {
        // Test implementation
        assertNotNull(service.doSomething());
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (testConnection != null) {
            testConnection.close();
        }
    }
}
```

**Test Location**: `app-bana-service/src/test/java/com/appbana/`

**Run Tests**:
```bash
mvn test                           # All tests
mvn test -Dtest=MyServiceTest     # Specific test
mvn test -Dtest=*Service*         # Pattern match
```

### Frontend Vitest Tests

**Pattern**: Vitest with jsdom for DOM simulation

```typescript
import { describe, it, expect, beforeEach } from 'vitest';
import { MyComponent } from './MyComponent';

describe('MyComponent', () => {
  let element: MyComponent;
  
  beforeEach(() => {
    element = document.createElement('my-component') as MyComponent;
    document.body.appendChild(element);
  });
  
  it('renders correctly', async () => {
    element.label = 'Test';
    await element.updateComplete; // Wait for Lit render
    
    const button = element.shadowRoot?.querySelector('button');
    expect(button?.textContent).toContain('Test');
  });
});
```

**Run Tests**:
```bash
cd app-bana-ui
npm test              # Run all tests
npm test -- --watch   # Watch mode
```

---

## Common Pitfalls & Solutions

### 1. Backend Process Won't Stop (Windows)
**Problem**: Old backend still running, port 8080 locked

**Solution**:
```powershell
# Kill all Java processes
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force

# Then rebuild
mvn clean package -DskipTests
```

### 2. CORS Errors in Frontend
**Problem**: Frontend (5173) can't call backend (8080)

**Solution**: Verify `Router.java` includes CORS headers
```java
// api/Router.java
res.header("Access-Control-Allow-Origin", "*");
res.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
res.header("Access-Control-Allow-Headers", "*");
```

### 3. Component Not Rendering
**Problem**: Custom element shows as `<unknown-element>`

**Solution**: Check registration
```typescript
// BEFORE using in PageMeta
import { MyComponent } from './components/MyComponent';
registry.register('my-component', MyComponent);

// THEN in PageMeta
{ type: 'my-component', props: { ... } }
```

### 4. TenantContext Leaking Between Requests
**Problem**: Request sees wrong tenant data

**Solution**: ALWAYS clear in finally block
```java
TenantContext.set(context);
try {
    // Do work
} finally {
    TenantContext.clear(); // Prevents thread pool pollution
}
```

### 5. Flyway Migration Fails
**Problem**: `V6__workflow_tables.sql` fails on restart

**Solution**: Clean H2 database
```bash
rm -f data/appbana.*
./restart-backend.sh
```

**Production**: Disable `.clean()` in ConfigManager:
```java
// ConfigManager.java
// flyway.clean(); // Comment out in production
flyway.migrate();
```

### 6. AppStore State Not Updating
**Problem**: Changes to appStore don't trigger re-render

**Solution**: Use reactive properties
```typescript
class MyComponent extends LitElement {
  @state() private apps: AppMeta[] = [];
  
  connectedCallback() {
    super.connectedCallback();
    // Subscribe to store changes
    appStore.subscribe(() => {
      this.apps = appStore.listApps(); // Triggers re-render
    });
  }
}
```

---

## Critical File Reference

### Backend Core
| File | Purpose | Key Methods |
|------|---------|-------------|
| `Main.java` | Entry point | `main()` - starts server |
| `ApiServer.java` | HTTP routing | `startJdk()`, `buildRouter()` |
| `SchemaManager.java` | Schema lifecycle | `saveSchema()`, `generateMigrationPlan()` |
| `JdbcManager.java` | Database ops | `getConnection()`, `execute()` |
| `AppManager.java` | App CRUD | `createApp()`, `loadApp()` |
| `TenantContext.java` | Multi-tenant | `set()`, `get()`, `clear()` |
| `WorkflowEngine.java` | Workflow execution | `execute()`, `evaluateCondition()` |
| `PermissionService.java` | Field-level security | `filterFields()`, `canRead()` |

### Frontend Core
| File | Purpose | Key Components |
|------|---------|----------------|
| `index.ts` | Main entry | Router, app initialization |
| `studio-entry.ts` | Studio entry | Builder initialization |
| `AppStore.ts` | Global state | `appStore` singleton |
| `app-renderer.ts` | Page renderer | `renderPage()` |
| `core/registry.ts` | Component registry | `register()`, `get()` |
| `AiChatBuilder.ts` | AI interface | Chat, intent classification |
| `PageManager.ts` | Page management | Create, edit, template selection |

### Configuration & Data
| File | Purpose | Content |
|------|---------|---------|
| `config.json` | Server config | Port, datasources, HTTPS |
| `builder-database/02-components.json` | Component definitions | 19 UI components |
| `builder-database/11-intent-patterns.json` | AI intent rules | Classification patterns |
| `builder-database/99-capabilities-index.json` | Capability index | Quick reference |
| `V1__auth_schema.sql` | Auth migration | Users, roles, permissions |
| `V6__workflow_tables.sql` | Workflow migration | Definition, instance, token, history |

---

## 📚 Essential Documentation References

**ALWAYS refer to these docs when working on related features:**

### Core Architecture & Development
- **[docs/01-ARCHITECTURE.md](../docs/01-ARCHITECTURE.md)** - System architecture, metadata-driven flow, tech stack
- **[docs/02-DEVELOPMENT_GUIDE.md](../docs/02-DEVELOPMENT_GUIDE.md)** - Setup, build, run, troubleshooting
- **[docs/03-ROADMAP.md](../docs/03-ROADMAP.md)** - Product roadmap, Q4 2025 delivery phases
- **[docs/04-USER_MANUAL.md](../docs/04-USER_MANUAL.md)** - User guide for app builders

### Strategic Planning
- **[docs/STRATEGIC_PLAN_SUMMARY.md](../docs/STRATEGIC_PLAN_SUMMARY.md)** - 5-minute executive summary (5 critical gaps, ROI)
- **[docs/STRATEGIC_PLAN_FINAL.md](../docs/STRATEGIC_PLAN_FINAL.md)** - Complete strategic analysis (read for UX decisions)

### Security & Authentication
- **[docs/AUTH_RBAC_DESIGN.md](../docs/AUTH_RBAC_DESIGN.md)** - RBAC architecture (User/Role/Permission model)
- **[docs/AUTH_PHASE1_IMPLEMENTATION.md](../docs/AUTH_PHASE1_IMPLEMENTATION.md)** - 6-week auth implementation roadmap
- **[docs/SECURITY_FEATURES.md](../docs/SECURITY_FEATURES.md)** - ✅ Complete Security Suite Guide (Production Ready)
  - Password Security, CSRF Protection, Session Management, Rate Limiting
  - Middleware Pipeline, Frontend Integration, API Reference
  - 156/156 tests passing (100% coverage)
  - **Reference this for ANY security feature implementation**
- **[docs/FIELD_LEVEL_SECURITY.md](../docs/FIELD_LEVEL_SECURITY.md)** - ✅ FLS complete guide (HIPAA/PCI-DSS compliance)
  - Admin guide, API reference, testing scenarios
  - **Reference this for ANY FLS-related work**

### Workflow Automation
- **[docs/WORKFLOW_FEATURE_SPEC.md](../docs/WORKFLOW_FEATURE_SPEC.md)** - Complete workflow architecture (2200+ lines)
  - Task types, versioning, maker-checker patterns
  - **Reference this for ANY workflow-related work**
- **[docs/WORKFLOW_PHASE1_STATUS_DEC7_2025.md](../docs/WORKFLOW_PHASE1_STATUS_DEC7_2025.md)** - Current status (95% complete)
  - Achievement summary, bug fixes, next steps
  - **Check this before continuing workflow work**

### AI & Technical Reference
- **[docs/AI_BUILDER_SPEC.md](../docs/AI_BUILDER_SPEC.md)** - AI builder architecture, intent classification
- **[docs/JAVA21_QUICK_REFERENCE.md](../docs/JAVA21_QUICK_REFERENCE.md)** - Java 21 features (virtual threads, records, etc.)

### Complete Documentation Index
- **[docs/README.md](../docs/README.md)** - Complete navigation hub with role-based guides

**Documentation Status (December 30, 2025)**:
- ✅ Consolidated from 70+ files to 15 essential docs
- ✅ Security suite fully documented (SECURITY_FEATURES.md)
- ✅ Builder database updated with security APIs
- ✅ Zero duplicate content
- ✅ Clear hierarchy by domain
- ✅ Role-based navigation in README

---

## Quick Commands Reference

### Backend (macOS/Linux)
```bash
# Start/restart backend (PREFERRED METHOD)
./restart-backend.sh

# View logs
tail -f backend.log

# Stop backend
kill $(cat backend.pid)

# Clean database and restart
rm -f data/appbana.* && ./restart-backend.sh

# Run tests
cd app-bana-service
mvn test
mvn test -Dtest=PermissionServiceTest
```

### Backend (Windows PowerShell)
```powershell
# Start backend
.\start-backend.bat

# Kill stuck processes
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force

# Port check
Get-NetTCPConnection -LocalPort 8080 -State Listen

# Test API
Invoke-WebRequest -Uri "http://localhost:8080/apps" | Select-Object StatusCode

# Pretty JSON response
(Invoke-WebRequest -Uri "http://localhost:8080/apps").Content | ConvertFrom-Json | ConvertTo-Json -Depth 5
```

### Frontend
```bash
cd app-bana-ui

# Start dev server (PREFERRED METHOD)
./run-ui.sh              # macOS/Linux
npm run dev              # Windows or direct

# Build for production
npm run build

# Run tests
npm test
npm test -- --watch      # Watch mode
```

### Maven Build
```bash
# Full build (from project root)
mvn clean package -DskipTests

# Skip UI build (backend only)
cd app-bana-service
mvn clean package -DskipTests

# With tests
mvn clean verify
```

### Docker (PostgreSQL)
```bash
# Status check
docker ps | grep appbana-postgres

# View logs
docker logs appbana-postgres

# Stop container
docker stop appbana-postgres

# Remove container (caution: deletes data)
docker rm appbana-postgres

# Remove volume (caution: permanent data loss)
docker volume rm appbana-postgres-data
```

### API Testing
```bash
# Health check
curl http://localhost:8080/health

# List apps
curl http://localhost:8080/apps

# Create schema
curl -X POST http://localhost:8080/schema \
  -H 'Content-Type: application/json' \
  -d '{"name":"person","fields":[{"name":"id","type":"long","primaryKey":true}]}'

# Query entity
curl "http://localhost:8080/api/person?limit=10"
```

---

**Last Updated**: January 4, 2026  
**Current Sprint**: Multi-Tenant Architecture (runtime-publish branch)  
**Next Sprint**: Workflow Phase 2 (SLA, timeouts, parallel execution) + Tenant URL isolation
