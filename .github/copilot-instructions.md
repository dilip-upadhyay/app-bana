# GitHub Copilot Instructions for AppBana

## Project Overview
AppBana is a **metadata-driven platform** that generates end-to-end functionality from a single source of truth. The core flow: `Entity Definition (Business Layer) → Schema (Technical Layer) → Database Table → REST CRUD APIs → UI Pages (Runtime)`. Changes to metadata propagate automatically through all layers.

**CRITICAL PRIORITIES (November 2025)**

### 1. Authentication Phase 1 - Enterprise Features (HIGHEST PRIORITY) 🔴
**Status**: Week 1-2 of 6-week implementation ($80K-120K investment)  
**Current Grade**: 6/10 (MVP-Ready) → Target: 8.5/10 (Enterprise-Ready)  
**Business Impact**: Unlocks $500K-2M ARR (Healthcare $50M-100M TAM, Finance $30M-60M TAM)

**Active Implementation** (Week 1-2):
- **Field-Level Security (FLS)** - 40% complete
  - ✅ Database: `field_permission` table (V2__field_level_security.sql, 250+ lines)
  - ✅ Entity: `FieldPermission.java` (155 lines with Lombok + Java 21 records)
  - ✅ Service: `PermissionService.java` (400+ lines, 6 methods, 5-min cache)
  - ⏳ REST API: Filter GET responses, validate PUT requests (30%)
  - ⏳ UI: Field masking in FormElement (10%)
  - ❌ Tests: Integration tests (0%, 7 scenarios defined)

**Upcoming Features** (Weeks 2-6):
- Week 2-3: Profile Layer (user setup 2 hours → 10 minutes)
- Week 3-4: Role Hierarchy (managers see subordinates automatically)
- Week 4-5: Session Management (token revocation within 1 second)
- Week 5-6: Multi-Tenancy (SaaS-ready, zero cross-org data leakage)

**Files Modified**:
- `model/User.java`, `model/Role.java`, `model/Permission.java`, `model/FieldPermission.java` (Lombok refactored)
- `service/PasswordService.java`, `service/JwtService.java`, `service/PermissionService.java`
- `V2__field_level_security.sql` (migration with indexes and seed data)
- `builder-database/09-authentication.json` (FLS capabilities for AI)

**Next Steps**:
1. Integrate PermissionService into ApiServer REST endpoints (HIGH PRIORITY)
2. Create FLS CRUD endpoints (`/api/field-permissions`)
3. Add JUnit tests for PermissionService (7 scenarios)
4. Update FormElement.ts for field masking UI

### 2. AI Builder Experience (ONGOING)
- The **AI Builder experience is the highest-priority concern** in this project.
- For **any new feature, capability, or metadata change**, you **must**:
  - Consider how it should be surfaced and controlled via the AI Builder.
  - Update backend AI orchestration (e.g., `AiAppGeneratorService`, prompts, classifiers) so the new capability can be driven through conversation.
  - Update the builder database (`builder-database/*.json`) so AI has an accurate, machine-readable description of the new capability.
- When making trade-offs, prefer solutions that **improve or at least do not degrade** the conversational builder experience.

## Technology Stack
- **Backend**: Java 21 LTS, JDK HttpServer with CORS, H2 embedded database, HikariCP, Jackson, Maven multi-module
- **Frontend**: TypeScript 5.2.2+, Lit 3.1.4 Web Components, Vite 5.3.1+ dev server, Shadow DOM
- **Architecture**: Metadata-driven rendering, dual-layer abstraction (Entity → Schema), universal datasource adapters
- **Persistence**: Backend filesystem (`app-bana-service/apps/{appId}/app.json` + `pages/{pageId}.json`)

## Critical Development Workflows

### Starting the Application
**Windows PowerShell**:
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
1. Backend runs in its own terminal showing server logs continuously.
2. **NEVER** run PowerShell commands, tests, or API calls in the backend terminal — doing so exits the server.
3. Always use `.\start-backend.bat` (not manual `java -jar` commands) *only* in the dedicated backend terminal.
4. For development, use Vite dev server (`npm run dev`) on port 5173.
5. All testing, `mvn` commands, and `Invoke-WebRequest` API checks **must** be run in a separate terminal from the backend.

### Build & Deployment Architecture
**Maven Multi-Module Structure**:
```
app-bana/                           # Parent POM
├── app-bana-ui/                    # Frontend module (packages UI as JAR)
│   ├── src/main/resources/ui/dist/ # Vite build output (npm run build)
│   └── pom.xml                     # Creates app-bana-ui-1.0-SNAPSHOT.jar
└── app-bana-service/               # Backend module (depends on UI module)
    ├── src/main/resources/         # Empty (Maven auto-includes UI JAR)
    └── pom.xml                     # Maven Shade creates fat JAR with UI
```

**Build Process**:
```powershell
# Frontend: Build TypeScript → JavaScript
cd app-bana-ui
npm run build                       # Output: src/main/resources/ui/dist/

# Backend: Package both modules into fat JAR
cd ..
mvn clean package -DskipTests       # Creates fat JAR with UI embedded

# Result: app-bana-service/target/app-bana-1.0-SNAPSHOT-fat.jar
```

**⚠️ NEVER manually copy UI files to backend!**
- ❌ Don't copy from `app-bana-ui/dist/` to `app-bana-service/src/main/resources/ui/`
- ✅ Maven automatically includes UI JAR resources during `mvn package`
- ✅ For development, use Vite dev server (`npm run dev`) on port 5173
- ✅ Only build fat JAR for production deployment or testing production bundle

## Key Files Reference

**Backend (Java)**:
- `ApiServer.java` - HTTP routes, /apps endpoints, virtual threads
- `AppManager.java` - App/page CRUD with auto-linking
- `api/Router.java` - Custom HTTP router with CORS
- `ai/AiSystemPrompts.java` - AI generation prompts
- `ai/AiAppGeneratorService.java` - AI action classification and app generation
- `AiResultValidator.java` - Validates AI responses

**Authentication (Phase 1 - NEW)**:
- `model/User.java` - User entity (Lombok @Data, 100 lines)
- `model/Role.java` - Role entity with permissions (Lombok @Data, 78 lines)
- `model/Permission.java` - Granular permission model (resource:action:scope, 120 lines)
- `model/FieldPermission.java` - Field-level security (Lombok @Data, 155 lines)
- `model/dto/UserDTO.java` - Safe user DTO (Java 21 record, no password hash)
- `model/dto/RoleDTO.java` - Role DTO (Java 21 record)
- `model/dto/PermissionDTO.java` - Permission DTO (Java 21 record)
- `model/dto/JwtClaims.java` - JWT payload (Java 21 record)
- `service/PasswordService.java` - BCrypt password hashing (cost factor 12)
- `service/JwtService.java` - JWT token generation/verification (HMAC-SHA256)
- `service/PermissionService.java` - FLS runtime filtering (6 methods, 5-min cache)
- `src/main/resources/db/migration/V2__field_level_security.sql` - FLS schema + seed data

**Frontend (TypeScript)**:
- `builder/store/AppStore.ts` - Global state singleton (use `appStore` import)
- `builder/components/AiChatBuilder.ts` - AI chat interface with app generation
- `builder/components/PropertiesPanel.ts` - Component property editor
- `runtime/shell/AppRuntimeShell.ts` - App preview/runtime shell
- `core/registry.ts` - Component registration + lazy loading

**Storage** (Backend filesystem):
- `app-bana-service/apps/{appId}/app.json` - App metadata
- `app-bana-service/apps/{appId}/pages/{pageId}.json` - Page metadata with ComponentNode trees

## Key Patterns & Conventions

### PageMeta Structure
```typescript
interface PageMeta {
  id: string;
  name: string;
  rootId: string;              // MUST match root node's ID in nodes array
  nodes: ComponentNode[];      // Component tree structure
  metaVersion: string;
  type: string;                // 'list' | 'detail' | 'form' | 'dashboard'
}
```

### API Response Format
```java
// GET /apps returns:
{ "apps": [AppListItem, ...] }

// GET /apps/{id}/full returns:
{ "id": "...", "name": "...", "entities": [...], "pages": [...] }
```

### Component Registration
```typescript
// In component file:
@customElement('my-component')
export class MyComponent extends LitElement { ... }

// In registry.ts:
if (!registry.has('my-component')) {
  proms.push(import('../components/MyComponent.js'));
}
```

### CSS Import Pattern
```typescript
import styles from './MyComponent.css?inline';

@customElement('my-component')
export class MyComponent extends LitElement {
  static readonly styles = unsafeCSS(styles);  // Note: unsafeCSS, not css``
}
```

## Builder Database (AI Reference)

**Location**: `builder-database/` directory  
**Purpose**: Machine-readable reference of ALL AppBana capabilities for AI agents

**Structure**:
```
builder-database/
├── 01-core-concepts.json     # Architecture, patterns
├── 02-components.json        # UI components (13+ types)
├── 03-entities.json          # Field types (38+ types), relationships
├── 04-pages.json             # Page templates (7+ templates)
├── 05-datasources.json       # Datasource adapters (25+ types)
├── 08-api-endpoints.json     # REST API reference
└── 99-capabilities-index.json # Quick lookup
```

**Update Protocol**: When adding components/field types/page templates, update corresponding JSON + increment version + update capabilities index.

## Conversational Builder Alignment

- **Persona Reference**: Studio now follows the `Studio Conversational LAN` (see `docs/STUDIO-CONVERSATIONAL-LAN.md`) so AI builders should greet users, offer guided ideas, and narrate metadata decisions.
- **AiChatBuilder Focus**: Keep `/api/ai/generate` calls focused on structured intent flows; handle greetings, idea suggestions, and simple directives client-side before backend generation, but delegate any `list`-style commands (e.g., asking to "list apps" or "show my apps") entirely to the backend so it can return the real `/apps` data.
- **List instructions**: Avoid introducing new heuristics for detecting list requests on the frontend—backend classification should mark them with the `list` action and return `payload.apps`, and the UI should simply render whatever metadata the server returns. Stick with this backend-led pattern going forward.
- **Builder Database Sync**: When introducing new conversational cues (greetings, ideas, persona states), capture them in `builder-database/02-components.json` so AI agents understand the policy that drives the UI builder.

## Common Debugging Scenarios

### Backend Exits Immediately
**Cause**: Running PowerShell commands in backend's terminal  
**Fix**: Use separate terminal for testing. Backend terminal shows logs only.

### CORS Error in Preview
**Symptom**: `Access to fetch blocked by CORS policy`  
**Fix**: Backend `Router.java` includes CORS headers. Rebuild backend if missing.

### "Root node not found" Error
**Cause**: `PageMeta.rootId` doesn't match any node's ID in `nodes` array  
**Fix**: Verify `rootId` value matches first node's `id` in page JSON file

### Frontend Build Warnings (Dynamic Imports)
**Message**: "...is dynamically imported... but also statically imported"  
**Status**: ⚠️ Expected warning, doesn't affect functionality

## PowerShell Commands Reference

```powershell
# Check if backend port is in use
Get-NetTCPConnection -LocalPort 8080 -State Listen

# Kill backend process (if stuck)
Get-NetTCPConnection -LocalPort 8080 | Select-Object -ExpandProperty OwningProcess | Stop-Process -Force

# Test backend API (from separate terminal!)
Invoke-WebRequest -Uri "http://localhost:8080/apps" | Select-Object StatusCode

# Pretty-print JSON response
(Invoke-WebRequest -Uri "http://localhost:8080/apps").Content | ConvertFrom-Json | ConvertTo-Json -Depth 5
```

## Best Practices

**Backend Java (Java 21 LTS)**:
- Use `AppManager` for all app/page operations (don't write files directly)
- Jackson ObjectMapper with `INDENT_OUTPUT` for pretty JSON
- Use System.out logging (logger migration deferred)

**Java 21 Modern Features (ALWAYS USE)**:
- **Virtual Threads**: Use `Thread.ofVirtual().start(r)` for executors (already in ApiServer)
- **Records for DTOs**: Use `record` for immutable API responses, never expose entities with sensitive data
  ```java
  public record UserDTO(Long id, String email, String name) {
      public static UserDTO fromUser(User user) { return new UserDTO(user.getId(), ...); }
  }
  ```
- **Switch Expressions**: Use `switch` as expression with `yield`, not statements with `break`
  ```java
  String url = switch (type) {
      case "h2" -> { yield "jdbc:h2:..."; }
      case "postgres" -> { yield "jdbc:postgresql://..."; }
      default -> null;
  };
  ```
- **Pattern Matching**: Use `instanceof` with pattern variables
  ```java
  if (obj instanceof String s && s.length() > 5) { ... }
  ```
- **Text Blocks**: Use `"""` for multi-line SQL, JSON, HTML
  ```java
  String sql = """
      SELECT * FROM users
      WHERE status = 'active'
      """;
  ```
- **Lombok + Records**: Keep Lombok `@Data` for mutable entities (User, Role), use records for immutable DTOs
- **NO null returns**: Use `Optional<T>` for potentially absent values
- **Stream API**: Prefer streams over loops for collections

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

---

**Last Updated**: November 22, 2025  
**Major Changes**: 
- **Authentication Phase 1**: FLS implementation (database/entity/service complete, REST API in progress)
- **Java 21 Modernization**: Virtual threads, records for DTOs, switch expressions
- **Lombok Integration**: All entities refactored (322 lines eliminated, 42% reduction)
- **Current Priority**: Complete FLS REST API integration + field masking UI (Week 1-2)
- **Next Sprint**: Profile Layer (Week 2-3), Role Hierarchy (Week 3-4)