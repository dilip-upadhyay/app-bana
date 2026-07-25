# Multi-Tenant Architecture - Visual Summary
## Quick Reference Guide with ASCII Diagrams

**Companion to**: COMPREHENSIVE_MULTI_TENANT_ARCHITECTURE.md  
**Date**: December 31, 2025

---

## 1. System Layers Overview

```
┌────────────────────────────────────────────────────────────────┐
│                   APPBANA PLATFORM (Layer 0)                    │
│  What: The AppBana application itself                          │
│  Who: System administrators                                     │
│  Data: Platform configuration, billing, analytics              │
└────────────────────────────────────────────────────────────────┘
                              ↓
┌────────────────────────────────────────────────────────────────┐
│                   TENANT LAYER (Layer 1)                        │
│  What: Organizations using AppBana (SaaS model)                │
│  Who: Tenant admins, developers                                │
│  Data: appbana_tenants, appbana_builder_users                 │
│  Isolation: tenant_id in all tables                            │
│  Example: "acme-corp", "xyz-inc", "startup-abc"               │
└────────────────────────────────────────────────────────────────┘
                              ↓
┌────────────────────────────────────────────────────────────────┐
│                APPLICATION LAYER (Layer 2)                      │
│  What: Apps created in Studio Builder                          │
│  Who: Developers (builder users)                               │
│  Data: appbana_apps, appbana_pages, appbana_schemas           │
│  Isolation: app_id in all tables                               │
│  Example: "hr-app", "crm-app", "inventory-app"                │
└────────────────────────────────────────────────────────────────┘
                              ↓
┌────────────────────────────────────────────────────────────────┐
│                   ENTITY LAYER (Layer 3)                        │
│  What: Data models defined in apps                             │
│  Who: App users (runtime users)                                │
│  Data: {entity}_{tenant}_{app} tables                          │
│  Isolation: WHERE tenant_id = ? AND app_id = ?                │
│  Example: employee, customer, product, order                   │
└────────────────────────────────────────────────────────────────┘
                              ↓
┌────────────────────────────────────────────────────────────────┐
│                   RECORD LAYER (Layer 4)                        │
│  What: Actual data records                                     │
│  Who: Individual end users                                     │
│  Data: Rows in entity tables                                   │
│  Isolation: WHERE created_by = ? (optional RLS)                │
│  Example: John Doe's employee record                           │
└────────────────────────────────────────────────────────────────┘
```

---

## 2. API Structure

```
https://appbana.io
├── /studio                             ← Builder Platform APIs
│   ├── /auth
│   │   ├── POST /login                 (Builder login)
│   │   ├── POST /logout
│   │   └── POST /register              (Create builder account)
│   │
│   ├── /tenants
│   │   ├── GET  /                      (List my tenants)
│   │   ├── POST /                      (Create tenant - admin only)
│   │   └── PUT  /{tenantId}/switch     (Switch active tenant)
│   │
│   ├── /apps
│   │   ├── GET  /                      (List apps in current tenant)
│   │   ├── POST /                      (Create app)
│   │   ├── GET  /{appId}               (Get app metadata)
│   │   ├── PUT  /{appId}               (Update app)
│   │   └── DELETE /{appId}             (Delete app)
│   │
│   └── /apps/{appId}
│       ├── /schemas
│       │   ├── GET  /                  (List entities in app)
│       │   ├── POST /                  (Create entity)
│       │   ├── GET  /{entity}          (Get schema)
│       │   ├── PUT  /{entity}          (Update schema)
│       │   └── DELETE /{entity}        (Delete schema)
│       │
│       ├── /pages
│       │   ├── GET  /                  (List pages in app)
│       │   ├── POST /                  (Create page)
│       │   └── ...
│       │
│       ├── /workflows
│       │   ├── GET  /                  (List workflows)
│       │   ├── POST /                  (Create workflow)
│       │   └── ...
│       │
│       ├── /entities/{entity}/seed     (Magic Seed Data - FIXED!)
│       │   └── POST /                  (Generate and save seed data)
│       │
│       └── /deploy
│           └── POST /                  (Deploy app to runtime)
│
└── /runtime                            ← Runtime APIs (End Users)
    └── /apps/{appId}
        ├── /auth
        │   ├── POST /login             (App-specific login)
        │   ├── POST /register          (App-specific signup)
        │   └── POST /logout
        │
        ├── /{entity}                   ← Entity CRUD (app-scoped!)
        │   ├── GET  /                  (List records)
        │   ├── POST /                  (Create record)
        │   ├── GET  /{id}              (Get record)
        │   ├── PUT  /{id}              (Update record)
        │   └── DELETE /{id}            (Delete record)
        │
        ├── /workflows
        │   ├── POST /start             (Start workflow instance)
        │   └── GET  /tasks             (My pending tasks)
        │
        └── /metadata
            └── GET /                   (App config for runtime)
```

---

## 3. Data Flow: Magic Seed Data (BEFORE FIX)

```
┌─────────────────────────────────────────────────────────────────┐
│  EntityManager.ts - handleMagicSeed()                            │
│  ❌ PROBLEM: No app context                                     │
└──────────────┬──────────────────────────────────────────────────┘
               │
               │ POST /api/user_information
               │ Body: { name: "John", email: "..." }
               │ ❌ No tenant_id or app_id!
               │
               ▼
┌─────────────────────────────────────────────────────────────────┐
│  Backend: GenericEntityRoutes.java                              │
│  Route: /api/{entity}                                            │
│  ❌ PROBLEM: Global route, no app context                       │
└──────────────┬──────────────────────────────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────────────────────────────┐
│  EntityCrudService.insertRecord()                               │
│  ❌ PROBLEM: No tenant_id or app_id to filter by                │
│  Result: 404 Not Found (entity doesn't exist globally)          │
└─────────────────────────────────────────────────────────────────┘
```

## 4. Data Flow: Magic Seed Data (AFTER FIX)

```
┌─────────────────────────────────────────────────────────────────┐
│  EntityManager.ts - handleMagicSeed()                            │
│  ✅ FIX: Get context from AppStore                              │
│  - tenantId = tenantStore.currentTenant.id                      │
│  - appId = appStore.currentApp.id                               │
└──────────────┬──────────────────────────────────────────────────┘
               │
               │ POST /studio/apps/hr-app/entities/employee/seed
               │ Body: {
               │   tenantId: "acme-corp",
               │   appId: "hr-app",
               │   data: [{ name: "John", ... }]
               │ }
               │
               ▼
┌─────────────────────────────────────────────────────────────────┐
│  Backend: StudioEntityRoutes.java                               │
│  Route: /studio/apps/{appId}/entities/{entity}/seed             │
│  ✅ FIX: Extract appId from URL, tenantId from JWT              │
│  Create TenantContext(tenantId, appId)                          │
└──────────────┬──────────────────────────────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────────────────────────────┐
│  EntityCrudService.insertRecords(context, schema, data)         │
│  ✅ FIX: Auto-inject tenant_id and app_id                       │
│  INSERT INTO employee_acme_corp_hr_app                          │
│    (tenant_id, app_id, name, email)                             │
│  VALUES                                                          │
│    ('acme-corp', 'hr-app', 'John', 'john@acme.com'),           │
│    ('acme-corp', 'hr-app', 'Jane', 'jane@acme.com')            │
│  Result: ✅ SUCCESS                                             │
└─────────────────────────────────────────────────────────────────┘
```

---

## 5. Security: Dual JWT System

### Builder JWT (Studio Users)

```
Header:
{
  "alg": "HS256",
  "typ": "JWT"
}

Payload:
{
  "sub": "builder-user-123",           ← Builder user ID
  "email": "dev@acme.com",
  "aud": "appbana-builder",            ← Audience: builder platform
  "tenant_id": "acme-corp",            ← Which organization
  "role": "developer",                 ← Builder role
  "permissions": [
    "app:create",
    "app:update",
    "schema:manage"
  ],
  "iat": 1704067200,
  "exp": 1704153600                    ← 24 hours
}

Signature: HMACSHA256(...)

Stored: localStorage.builderJwt
Used for: /studio/* APIs
```

### Runtime JWT (End Users)

```
Header:
{
  "alg": "HS256",
  "typ": "JWT"
}

Payload:
{
  "sub": "runtime-user-456",           ← Runtime user ID
  "email": "john@acme.com",
  "aud": "appbana-runtime-hr-app",     ← Audience: specific app
  "tenant_id": "acme-corp",            ← Which organization
  "app_id": "hr-app",                  ← Which app
  "role": "employee",                  ← App-specific role
  "permissions": [
    "employee:view-self",
    "timesheet:submit"
  ],
  "iat": 1704067200,
  "exp": 1704070800                    ← 1 hour (shorter!)
}

Signature: HMACSHA256(...)

Stored: localStorage.runtimeJwt_hr_app
Used for: /runtime/apps/hr-app/* APIs
```

**Why Two Systems?**
- Different lifetimes (builder: 24h, runtime: 1h)
- Different permissions models
- Security: Builder cannot impersonate runtime users
- Audit: Clear separation in logs

---

## 6. Database Schema Relationships

```
┌───────────────────────────────────────────────────────────────┐
│  appbana_tenants                                               │
├───────────────────────────────────────────────────────────────┤
│  PK: id (varchar)                                             │
│  - name, domain, subdomain, plan, status                      │
└─────────────┬─────────────────────────────────────────────────┘
              │
              │ 1:N
              │
┌─────────────▼─────────────────────────────────────────────────┐
│  appbana_builder_users                                         │
├───────────────────────────────────────────────────────────────┤
│  PK: id                                                        │
│  FK: tenant_id → appbana_tenants.id                           │
│  - email, password_hash, role, status                         │
└───────────────────────────────────────────────────────────────┘

┌───────────────────────────────────────────────────────────────┐
│  appbana_apps                                                  │
├───────────────────────────────────────────────────────────────┤
│  PK: (tenant_id, id)                                          │
│  FK: tenant_id → appbana_tenants.id                           │
│  - name, description, version, status, deployment_url         │
└─────┬─────────────────────────────────────────────────────────┘
      │
      │ 1:N
      │
      ├─────────────────────────────────────────────────────────┐
      │                                                           │
      ▼                                                           ▼
┌─────────────────────────────┐      ┌─────────────────────────────┐
│  appbana_schemas             │      │  appbana_pages              │
├─────────────────────────────┤      ├─────────────────────────────┤
│  PK: id                      │      │  PK: (tenant_id, app_id, id)│
│  FK: (tenant_id, app_id)     │      │  FK: (tenant_id, app_id)    │
│      → appbana_apps          │      │      → appbana_apps         │
│  - entity_name, json_schema  │      │  - name, path, json_metadata│
└──────────┬──────────────────┘      └─────────────────────────────┘
           │
           │ 1:1 (defines structure)
           │
           ▼
┌─────────────────────────────────────────────────────────────┐
│  {entity}_{tenant_id}_{app_id}                               │
│  (Dynamic entity tables - created at runtime)               │
├─────────────────────────────────────────────────────────────┤
│  PK: id                                                      │
│  - tenant_id (NOT NULL, indexed)                            │
│  - app_id (NOT NULL, indexed)                               │
│  - {user-defined fields}                                    │
│  - created_at, created_by, updated_at, updated_by (audit)  │
└─────────────────────────────────────────────────────────────┘

┌───────────────────────────────────────────────────────────────┐
│  runtime_users_{tenant_id}_{app_id}                            │
│  (Per-app user tables)                                         │
├───────────────────────────────────────────────────────────────┤
│  PK: id                                                        │
│  - tenant_id, app_id (composite FK to appbana_apps)           │
│  - email, password_hash, name, role, status                   │
└───────────────────────────────────────────────────────────────┘
```

---

## 7. Query Isolation Examples

### Without Isolation (BEFORE) ❌

```sql
-- User queries "employees" in HR App
SELECT * FROM employee;

-- PROBLEM: Returns ALL employees from ALL tenants and ALL apps!
-- Result: Data leakage, security violation
```

### With Isolation (AFTER) ✅

```sql
-- Same query, but auto-injected WHERE clause
SELECT * 
FROM employee_acme_corp_hr_app
WHERE tenant_id = 'acme-corp'      -- Auto-injected by middleware
  AND app_id = 'hr-app'            -- Auto-injected by middleware
  AND (
    visibility = 'public'           -- Row-level security
    OR created_by = 'john@acme.com' -- User can see their own
  );

-- Result: Only sees employees in Acme Corp's HR app
```

### Field-Level Security (FLS) ✅

```sql
-- After fetching rows, filter columns by role
-- User role: "employee"
-- Query appbana_field_permission:

SELECT field_name, can_read, can_edit
FROM appbana_field_permission
WHERE tenant_id = 'acme-corp'
  AND app_id = 'hr-app'
  AND entity_name = 'employee'
  AND role_name = 'employee';

-- Results:
--   salary: can_read = FALSE  → Hide from response
--   ssn: can_read = FALSE     → Hide from response
--   name: can_read = TRUE     → Include in response
--   email: can_read = TRUE    → Include in response

-- Final response to user:
{
  "id": 1,
  "name": "John Doe",
  "email": "john@acme.com"
  // salary and ssn fields removed
}
```

---

## 8. Implementation Phases

```
Week 1-2: FOUNDATION (CRITICAL PATH)
┌────────────────────────────────────────────────────┐
│  ✅ Create TenantContext.java                      │
│  ✅ Add tenant_id/app_id columns to all tables     │
│  ✅ Update EntityCrudService with auto-injection   │
│  ✅ Add /studio/apps/{appId}/{entity} routes       │
│  ✅ Fix Magic Seed Data in EntityManager.ts        │
│  ✅ Update SchemaManager to link schemas to apps   │
│  ✅ Add TenantContextMiddleware                    │
│                                                     │
│  DELIVERABLE: Magic Seed Data works!               │
└────────────────────────────────────────────────────┘

Week 3-4: RUNTIME AUTHENTICATION
┌────────────────────────────────────────────────────┐
│  □ Create runtime_users_{tenant}_{app} tables      │
│  □ Add /runtime/apps/{appId}/auth/* routes         │
│  □ Generate app-specific JWTs                      │
│  □ Create RuntimeShell.ts with login UI            │
│  □ Update RuntimeStore.ts with auth state          │
│  □ Add RuntimeAuthMiddleware                       │
│  □ Update PermissionService for runtime roles      │
│                                                     │
│  DELIVERABLE: End users can login to apps          │
└────────────────────────────────────────────────────┘

Week 5-6: BUILDER MULTI-TENANT
┌────────────────────────────────────────────────────┐
│  □ Create appbana_tenants table                    │
│  □ Create appbana_builder_users with tenant_id     │
│  □ Add TenantSwitcher.ts component                 │
│  □ Update AppStore to filter by tenant             │
│  □ Add tenant validation in all Studio APIs        │
│  □ Implement subdomain routing                     │
│  □ Add tenant-scoped billing/limits                │
│                                                     │
│  DELIVERABLE: Multiple orgs can use AppBana        │
└────────────────────────────────────────────────────┘

Week 7-8: PRODUCTION READY
┌────────────────────────────────────────────────────┐
│  □ Row-level security (owner-based filtering)      │
│  □ Sharing rules between users                     │
│  □ Cross-app workflows                             │
│  □ Tenant data export (GDPR)                       │
│  □ Tenant migration tools                          │
│  □ Performance optimization                        │
│  □ Comprehensive audit logging                     │
│                                                     │
│  DELIVERABLE: Enterprise-ready platform            │
└────────────────────────────────────────────────────┘
```

---

## 9. Critical Files to Modify

### Backend (Java)

```
app-bana-service/src/main/java/com/appbana/

NEW FILES:
├── model/TenantContext.java                   (Holds tenant/app context)
├── middleware/TenantContextMiddleware.java    (Extracts context from JWT)
├── api/StudioEntityRoutes.java                (Builder entity operations)
├── api/RuntimeEntityRoutes.java               (Runtime entity operations)
└── api/RuntimeAuthRoutes.java                 (Runtime authentication)

MODIFY:
├── service/EntityCrudService.java             (Add TenantContext parameter)
├── service/SchemaManager.java                 (Link schemas to apps)
├── service/PermissionService.java             (Add tenant/app scope)
├── manager/AppManager.java                    (Enforce tenant filtering)
└── ApiServer.java                             (Register new routes)
```

### Frontend (TypeScript)

```
app-bana-ui/src/

NEW FILES:
├── builder/store/TenantStore.ts               (Tenant state)
├── builder/components/TenantSwitcher.ts       (Switch tenants)
├── runtime/shell/RuntimeAuth.ts               (Runtime login/signup)
├── runtime/store/RuntimeStore.ts              (Runtime state)
└── runtime/store/RuntimeAuth.ts               (Runtime auth state)

MODIFY:
├── builder/store/AppStore.ts                  (Add tenant filtering)
├── builder/components/EntityManager.ts        (Fix Magic Seed Data!)
├── core/api-client.ts                         (Handle builder vs runtime JWTs)
└── runtime/shell/RuntimeShell.ts              (Add login UI)
```

### Database Migrations

```
app-bana-service/src/main/resources/db/migration/

NEW:
├── V10__tenant_app_isolation.sql              (Add columns to all tables)
├── V11__builder_users_tenants.sql             (Create tenant tables)
├── V12__runtime_users.sql                     (Create runtime user tables)
└── V13__update_fls.sql                        (Add tenant/app to FLS)
```

---

## 10. Testing Strategy

```
Unit Tests (JUnit)
├── TenantContextTest.java
├── EntityCrudServiceTest.java (with context)
├── SchemaManagerTest.java (with context)
└── PermissionServiceTest.java (tenant/app scope)

Integration Tests
├── MagicSeedDataIntegrationTest.java
│   ✅ Create app
│   ✅ Define schema
│   ✅ Generate seed data
│   ✅ Save with tenant/app context
│   ✅ Verify data saved correctly
│
├── TenantIsolationTest.java
│   ✅ Create two tenants
│   ✅ Create app in each tenant
│   ✅ Insert data in each app
│   ✅ Verify no cross-tenant data leakage
│
└── RuntimeAuthTest.java
    ✅ Create runtime user
    ✅ Login with app-specific JWT
    ✅ Access entity data
    ✅ Verify FLS applied

Frontend Tests (Vitest)
├── EntityManager.test.ts (Magic Seed Data)
├── AppStore.test.ts (Tenant filtering)
└── RuntimeAuth.test.ts (Login flow)

E2E Tests (Playwright - Future)
├── complete-workflow.spec.ts
│   ✅ Create app in Studio
│   ✅ Define entities
│   ✅ Generate seed data
│   ✅ Deploy app
│   ✅ Login as runtime user
│   ✅ View data (FLS applied)
```

---

## 11. Quick Commands

```bash
# Start backend with auto-restart
./restart-backend.sh

# Start frontend dev server
./run-ui.sh

# Run tests
cd app-bana-service
mvn test -Dtest=EntityCrudServiceTest

# Check database
psql appbana -c "SELECT table_name FROM information_schema.tables WHERE table_schema='public';"

# Verify tenant isolation
psql appbana -c "SELECT tenant_id, app_id, COUNT(*) FROM employee_acme_corp_hr_app GROUP BY tenant_id, app_id;"

# Tail logs
tail -f backend.log
tail -f app-bana-ui/node_modules/.vite/vite.log
```

---

## 12. Decision Checklist

Before implementing, confirm:

- [ ] Reviewed complete architecture document
- [ ] Understood all 4 layers (Platform → Tenant → App → Entity → Record)
- [ ] Agreed on dual JWT system (builder vs runtime)
- [ ] Confirmed API structure (/studio vs /runtime)
- [ ] Database schema design approved
- [ ] Migration strategy accepted
- [ ] Phase 1 priorities clear (Magic Seed Data fix)
- [ ] Testing strategy defined
- [ ] Ready to start implementation

---

**Status**: READY FOR IMPLEMENTATION  
**Estimated Time**: 8 weeks (4 phases)  
**Risk Level**: MEDIUM (well-documented, phased approach)  
**Next Step**: Review with team, get approval, start Phase 1

**Questions?** Refer to COMPREHENSIVE_MULTI_TENANT_ARCHITECTURE.md for complete details.
