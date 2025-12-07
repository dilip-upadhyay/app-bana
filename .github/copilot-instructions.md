# GitHub Copilot Instructions for AppBana

## Project Overview
AppBana is a **metadata-driven platform** generating end-to-end functionality from a single source: `Entity Definition → Schema → Database → REST APIs → UI Pages`. Changes to metadata propagate automatically.

---

## CURRENT PRIORITIES (December 2025)

### 1. Workflow Automation Phase 1 🔴 ACTIVE SPRINT
**Status**: 95% Complete (Runtime verification pending)  
**Branch**: dev-workflow  
**Impact**: Core platform capability - enables approval flows, maker-checker patterns, SLA tracking

**Completed** (December 7, 2025):
- ✅ **Database**: 4 workflow tables (`appbana_wf_definition`, `appbana_wf_instance`, `appbana_wf_token`, `appbana_wf_history`)
- ✅ **REST API**: 8 endpoints (workflow CRUD, publish, start, task management)
- ✅ **Services**: WorkflowService (CRUD + publish), WorkflowExecutionService (trigger + execute)
- ✅ **Triggers**: PostOperationHooks integration for entity creation events
- ✅ **Bug Fixes**: Flyway placeholders, H2 compatibility, CLOB serialization, Jackson LocalDateTime

**Remaining** (5%):
- ⏳ Runtime verification: Full end-to-end workflow test (create → publish → trigger → task → complete)
- ⏳ Debug logging: Ensure trigger logic fires correctly

**Next Steps**:
1. Test complete workflow execution cycle
2. Fix any runtime issues discovered
3. Begin Phase 2: SLA tracking, timeouts, parallel task execution

**Reference**: [WORKFLOW_PHASE1_STATUS_DEC7_2025.md](../docs/WORKFLOW_PHASE1_STATUS_DEC7_2025.md)

### 2. Authentication Phase 1 - Field-Level Security ✅ PRODUCTION READY
**Status**: 90% Complete (JUnit tests pending)  
**Impact**: $500K-2M ARR unlock (Healthcare/Finance compliance: HIPAA, PCI-DSS)

**Completed**:
- ✅ Database: `field_permission` table with indexes and views
- ✅ Service: `PermissionService` (400+ lines, cached, admin bypass)
- ✅ REST API: GET/PUT filtering + 5 FLS CRUD endpoints
- ✅ UI: StudioTableLive field hiding and disabling with 🔒 icon
- ✅ AI Integration: Intent patterns for "hide salary" phrases

**Remaining**:
- ⏳ JUnit tests for PermissionService (7 test scenarios - HIGH PRIORITY)

**Reference**: [FIELD_LEVEL_SECURITY.md](../docs/FIELD_LEVEL_SECURITY.md)

### 3. AI Builder Experience (ONGOING)
For **any new feature**:
- Surface via AI Builder conversational interface
- Update `AiAppGeneratorService` for AI-driven capability
- Update `builder-database/*.json` with machine-readable spec
- Prefer solutions that enhance conversational builder UX

---

## Technology Stack
- **Backend**: Java 21 LTS, JDK HttpServer, H2 database, HikariCP, Jackson, Lombok 1.18.30
- **Frontend**: TypeScript 5.2.2+, Lit 3.1.4, Vite 5.3.1, Shadow DOM
- **Architecture**: Metadata-driven, dual-layer abstraction (Entity → Schema)

---

## Development Workflows

### Starting Application (Windows PowerShell)
```powershell
# Terminal 1: Backend (continuous logs, port 8080) - NEVER RUN COMMANDS HERE!
.\start-backend.bat

# Terminal 2: Frontend dev server (port 5173)
cd app-bana-ui; npm run dev

# Terminal 3: Testing/commands (USE THIS FOR ALL COMMANDS!)
mvn clean compile
Invoke-WebRequest -Uri "http://localhost:8080/apps"
```

**⚠️ CRITICAL**: 
- **NEVER run commands in Terminal 1 (backend)** - it will stop the server!
- **ALWAYS use Terminal 3** for testing, API calls, database queries
- Backend runs continuously with live logs - don't interrupt it
- **AI AGENTS**: When testing APIs, ALWAYS open a new terminal first!

### Build Process
```powershell
cd app-bana-ui; npm run build           # Frontend → src/main/resources/ui/dist/
cd ..; mvn clean package -DskipTests    # Fat JAR with UI embedded
```
**Never manually copy UI files!** Maven auto-includes UI JAR.

---

## Key Files

**Backend (Java 21)**:
- `ApiServer.java` - HTTP routes, virtual threads, Flyway migrations
- `AppManager.java` - App/page CRUD
- `api/Router.java` - Custom router with CORS

**Database Migrations (Flyway)**:
- `V1__auth_schema.sql` - User/Role/Permission tables with seed data
- `V2__field_level_security.sql` - Field-level permissions (FLS) for HIPAA/PCI-DSS
- Flyway runs automatically on startup with `.clean()` in development mode

**Authentication (Phase 1)**:
- `model/User.java`, `Role.java`, `Permission.java`, `FieldPermission.java` (Lombok @Data)
- `model/dto/UserDTO.java`, `RoleDTO.java`, `PermissionDTO.java`, `JwtClaims.java` (Java 21 records)
- `service/PasswordService.java` (BCrypt), `JwtService.java` (JWT), `PermissionService.java` (FLS)
- `V2__field_level_security.sql` - FLS schema

**AI System**:
- `ai/AiAppGeneratorService.java` - AI classification/generation
- `ai/AiSystemPrompts.java` - AI prompts
- `builder-database/*.json` - Machine-readable capabilities

**Frontend (TypeScript)**:
- `builder/store/AppStore.ts` - Global state (use `appStore` import)
- `builder/components/AiChatBuilder.ts` - AI chat interface
- `runtime/shell/AppRuntimeShell.ts` - App preview

---

## Database Migrations (Flyway)

**Status**: ✅ Integrated (Flyway OSS 10.4.1)  
**Location**: `src/main/resources/db/migration/`  
**Convention**: `V{version}__{description}.sql`

**Current Migrations**:
- **V1__auth_schema.sql** - User/Role/Permission tables + seed data
- **V2__field_level_security.sql** - FLS tables + 20+ permissions + view
- **V3-V5__** - Additional system tables
- **V6__workflow_tables.sql** - Workflow tables (definition, instance, token, history)

**Development Mode**: Flyway runs `.clean()` on startup (drops all objects - **DEV ONLY!**)  
⚠️ **DO NOT use in production** - would delete all data!

**Testing Migrations**:
```bash
# macOS/Linux
cd $PROJECT_ROOT
rm -f data/appbana.*
mvn clean package -DskipTests
java -jar app-bana-service/target/app-bana-1.0-SNAPSHOT-fat.jar
```

**Column Naming**: Use `can_read`, `can_edit` (not `readable`, `editable`) - matches Java getter conventions

**Detailed Guide**: See [DEVELOPMENT_GUIDE.md](../docs/02-DEVELOPMENT_GUIDE.md#database-migrations) for:
- Creating new migrations (templates, best practices)
- Production configuration (disable `.clean()`)
- Troubleshooting migration failures

---

## Java 21 Best Practices (ALWAYS USE)

**Modern Features**:
```java
// Virtual Threads (already in ApiServer)
server.setExecutor(r -> Thread.ofVirtual().start(r));

// Records for DTOs (immutable, auto-equals/hashCode)
public record UserDTO(Long id, String email, String name) {
    public static UserDTO fromUser(User user) { ... }
}

// Switch Expressions (no break, no fall-through)
String url = switch (type) {
    case "h2" -> { yield "jdbc:h2:..."; }
    case "postgres" -> { yield "jdbc:postgresql://..."; }
    default -> null;
};

// Pattern Matching
if (obj instanceof String s && s.length() > 5) { ... }

// Text Blocks
String sql = """
    SELECT * FROM users
    WHERE status = 'active'
    """;
```

**Strategy**:
- Lombok `@Data` for **mutable entities** (User, Role, Permission)
- Java 21 `record` for **immutable DTOs** (UserDTO, JwtClaims)
- Use `Optional<T>` instead of null returns
- Prefer streams over loops

---

## Frontend Best Practices

**TypeScript/Lit**:
- Import `appStore` singleton (never instantiate)
- Use `@state()` for reactive properties
- Subscribe to AppStore in `connectedCallback()`
- Use `?inline` for CSS imports: `import styles from './MyComponent.css?inline';`
- Register components in `registry.ts` before using

**Metadata**:
- `PageMeta.rootId` must match root node ID in `nodes` array
- `ComponentNode` uses `children: string[]` for node IDs
- `EntityMeta` auto-includes protected `id` field

---

## Common Issues

**Backend Exits Immediately**: Commands run in backend terminal → Use separate terminal  
**CORS Error**: `Router.java` includes CORS → Rebuild if missing  
**Root Node Not Found**: `PageMeta.rootId` mismatch → Verify root node ID  

---

## Quick Commands

```powershell
# Port check
Get-NetTCPConnection -LocalPort 8080 -State Listen

# Kill stuck backend
Get-NetTCPConnection -LocalPort 8080 | Select-Object -ExpandProperty OwningProcess | Stop-Process -Force

# Test API
Invoke-WebRequest -Uri "http://localhost:8080/apps" | Select-Object StatusCode

# Pretty JSON
(Invoke-WebRequest -Uri "http://localhost:8080/apps").Content | ConvertFrom-Json | ConvertTo-Json -Depth 5
```

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

**Documentation Status (December 7, 2025)**:
- ✅ Consolidated from 70+ files to 15 essential docs
- ✅ Zero duplicate content
- ✅ Clear hierarchy by domain
- ✅ Role-based navigation in README

---

**Last Updated**: December 7, 2025  
**Current Sprint**: Workflow Phase 1 completion (95% → 100%) + Runtime verification  
**Next Sprint**: Workflow Phase 2 (SLA, timeouts, parallel execution) + Auth JUnit tests
