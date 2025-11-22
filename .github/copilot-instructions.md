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
# Terminal 1: Backend (continuous logs, port 8080)
.\start-backend.bat

# Terminal 2: Frontend dev server (port 5173)
cd app-bana-ui; npm run dev

# Terminal 3: Testing/commands (NEVER use Terminal 1!)
mvn clean compile
Invoke-WebRequest -Uri "http://localhost:8080/apps"
```

**⚠️ CRITICAL**: Never run commands in backend terminal—it exits the server!

### Build Process
```powershell
cd app-bana-ui; npm run build           # Frontend → src/main/resources/ui/dist/
cd ..; mvn clean package -DskipTests    # Fat JAR with UI embedded
```
**Never manually copy UI files!** Maven auto-includes UI JAR.

---

## Key Files

**Backend (Java 21)**:
- `ApiServer.java` - HTTP routes, virtual threads
- `AppManager.java` - App/page CRUD
- `api/Router.java` - Custom router with CORS

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
