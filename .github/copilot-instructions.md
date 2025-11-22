# GitHub Copilot Instructions for AppBana

## Project Overview
AppBana is a **metadata-driven platform** generating end-to-end functionality from a single source: `Entity Definition → Schema → Database → REST APIs → UI Pages`. Changes to metadata propagate automatically.

---

## CRITICAL PRIORITIES (November 2025)

### 1. Authentication Phase 1 - Enterprise Features 🔴 HIGHEST PRIORITY
**Status**: Week 1-2 of 6-week implementation  
**Grade**: 7.5/10 → 8.5/10 (Enterprise-Ready)  
**Impact**: $500K-2M ARR unlock (Healthcare/Finance TAM: $80M-160M)

**Field-Level Security (FLS)** - 70% Complete:
- ✅ Database: `field_permission` table + indexes + seed data
- ✅ Entity: `FieldPermission.java` (Lombok, 155 lines)
- ✅ Service: `PermissionService.java` (6 methods, 5-min cache)
- ✅ REST API: GET/PUT filtering + FLS CRUD endpoints (COMPLETE)
- ⏳ UI: Field masking in FormElement (20% - NEXT)
- ⏳ Tests: JUnit tests for PermissionService (0% - HIGH PRIORITY)

**Next Steps**:
1. Add JUnit tests for `PermissionService` (7 test scenarios)
2. Update `FormElement.ts` for field masking
3. Document FLS API in OpenAPI spec
4. Test with Postman/PowerShell

**Upcoming** (Weeks 2-6): Profile Layer → Role Hierarchy → Session Management → Multi-Tenancy

### 2. AI Builder Experience (ONGOING)
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

**Status**: ✅ Integrated (November 22, 2025)

**Overview**:
- Flyway OSS Edition 10.4.1 manages all database schema changes
- Migrations run automatically on application startup
- Location: `src/main/resources/db/migration/`
- Convention: `V{version}__{description}.sql` (e.g., `V1__auth_schema.sql`)

**Current Migrations**:
1. **V1__auth_schema.sql** - Authentication foundation
   - Tables: `user`, `role`, `permission`, `user_role`, `role_permission`
   - Seed data: 3 default roles (admin, manager, user)
   - Seed data: 14 default permissions (user:*, role:*, permission:*, app:*)
   - Default admin user: `admin@appbana.com` / `admin123`

2. **V2__field_level_security.sql** - Field-Level Security (FLS)
   - Table: `field_permission` (role_id, entity_name, field_name, can_read, can_edit)
   - Seed data: 20+ field permissions for 5 roles (admin, manager, user, hr, finance)
   - View: `v_effective_field_permissions` (combines user's all roles)

**Development Mode**:
- Flyway `.clean()` runs on every startup (drops all objects)
- All migrations re-run from scratch
- **DO NOT use in production** - would delete all data!

**Creating New Migrations**:
```sql
-- File: V3__your_feature_name.sql
-- Always use IF NOT EXISTS for safety
CREATE TABLE IF NOT EXISTS your_table (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Add indexes
CREATE INDEX IF NOT EXISTS idx_your_table_name ON your_table(name);

-- Seed data (optional)
INSERT INTO your_table (id, name) VALUES 
    (RANDOM_UUID(), 'Sample Data');
```

**Column Naming Convention**:
- Use `can_read`, `can_edit`, `can_delete` (not `readable`, `editable`)
- Matches Java boolean getter conventions: `canRead()`, `canEdit()`

**Testing Migrations**:
```powershell
# Terminal 3 (NOT Terminal 1!)
# 1. Stop backend
Stop-Process -Name java -Force

# 2. Delete database to test fresh migration
Remove-Item -Path "c:\Users\dilip\git\app-bana\data\appbana*" -Force

# 3. Rebuild
cd c:\Users\dilip\git\app-bana
mvn clean package -DskipTests

# 4. Start backend (Terminal 1 - don't run commands here!)
java -jar app-bana-service/target/app-bana-1.0-SNAPSHOT-fat.jar

# 5. Watch logs for Flyway output:
# [main] INFO org.flywaydb.core.internal.command.DbMigrate - Migrating schema "PUBLIC" to version "1 - auth schema"
# [main] INFO org.flywaydb.core.internal.command.DbMigrate - Migrating schema "PUBLIC" to version "2 - field level security"
# [main] INFO org.flywaydb.core.internal.command.DbMigrate - Successfully applied 2 migrations to schema "PUBLIC"
```

**Production Configuration** (Future):
```java
// Remove .cleanDisabled(false) and .clean() calls
Flyway flyway = Flyway.configure()
        .dataSource(cfg.getJdbcUrl(), cfg.getUsername(), cfg.getPassword())
        .locations("classpath:db/migration")
        .load();

int migrationsApplied = flyway.migrate().migrationsExecuted;
```

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

**Last Updated**: November 22, 2025  
**Current Sprint**: FLS REST API integration (Week 1-2)  
**Next Sprint**: Profile Layer (Week 2-3)
