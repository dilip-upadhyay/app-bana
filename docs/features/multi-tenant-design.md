> **✅ CURRENT concepts · ⚠️ Some diagrams show port 5173.** The multi-tenant physical-isolation model (`app_{tenant}_{app}_{entity}` table naming, schema key format) is accurate and unchanged. Diagrams that reference the old `app-bana-ui/` frontend should be read as `app-bana-studio/` (5174) + `app-bana-runtime/` (5175).
>
> **See:** [`docs/README.md`](../README.md) for the full documentation currency table.

---

# AppBana Comprehensive Multi-Tenant Architecture
## The Complete Blueprint for Builder & Runtime Isolation

**Date**: December 31, 2025  
**Author**: Senior Solution Architect  
**Status**: ARCHITECTURAL SPECIFICATION  
**Version**: 1.0  

---

## 🎯 Executive Summary

This document defines the **complete architectural blueprint** for transforming AppBana from a single-user platform into a **secure, scalable, multi-tenant enterprise system** that clearly separates:

1. **Builder Platform** - Where users create applications
2. **Runtime Applications** - The applications that end-users interact with
3. **Tenant Isolation** - Complete data segregation between organizations
4. **Security Layers** - Authentication, authorization, and data protection at all levels

**Key Architectural Decisions**:
- ✅ **Three-Layer Isolation**: Tenant → Application → Entity
- ✅ **Dual Security Contexts**: Builder users vs Runtime users
- ✅ **Metadata-Driven**: All isolation enforced via metadata annotations
- ✅ **Backward Compatible**: Phased migration with zero downtime
- ✅ **Future-Proof**: Scalable to 1000+ tenants, 10,000+ apps

---

## 📖 Table of Contents

1. [Current State Analysis](#1-current-state-analysis)
2. [Core Architectural Principles](#2-core-architectural-principles)
3. [System Architecture Overview](#3-system-architecture-overview)
4. [Data Model & Isolation Strategy](#4-data-model--isolation-strategy)
5. [API Design & Routing](#5-api-design--routing)
6. [Security Architecture](#6-security-architecture)
7. [Database Schema Design](#7-database-schema-design)
8. [Frontend Architecture](#8-frontend-architecture)
9. [Workflow & Business Process Isolation](#9-workflow--business-process-isolation)
10. [Implementation Roadmap](#10-implementation-roadmap)
11. [Migration Strategy](#11-migration-strategy)
12. [Trade-offs & Design Decisions](#12-trade-offs--design-decisions)

---

## 1. Current State Analysis

### 1.1 What We Have Today

**✅ Strengths:**
- Metadata-driven end-to-end flow (Schema → DB → API → UI)
- Complete Studio Builder with AppManager, PageManager, EntityManager
- Security suite (Password, CSRF, Sessions, Rate Limiting) - 156 tests passing
- Field-Level Security (FLS) for HIPAA compliance
- Workflow automation with USER_TASK, SERVICE_TASK, versioning
- App persistence in database (V9 migration with tenant_id column)
- Page templates and visual builder

**❌ Critical Gaps:**
- **No tenant isolation in entity data** - All records mixed globally
- **No app-scoped APIs** - `/api/user` is global, not `/api/apps/{appId}/user`
- **No runtime user authentication** - Only builder users (admins)
- **Schema not linked to apps** - Schemas are global, not app-specific
- **No segregation between builder and runtime** - Same user pool
- **Magic Seed Data fails** - Cannot save generated data to correct app

### 1.2 Business Requirements

**From Product Perspective:**
1. **Multiple Tenants** - Support SaaS model (Acme Corp, XYZ Inc, etc.)
2. **Multiple Apps Per Tenant** - HR App, CRM App, Inventory App
3. **Isolated Data** - Tenant A cannot see Tenant B's data
4. **Builder vs Runtime** - Different users, different permissions
5. **App-Specific Schemas** - Each app has its own entities
6. **App-Specific Security** - Each runtime app has its own users/roles
7. **Cross-App Workflows** - Future: workflows spanning multiple apps

**From Technical Perspective:**
1. **Data Integrity** - ACID compliance, no data leakage
2. **Performance** - Sub-100ms queries even with 10,000 apps
3. **Scalability** - Horizontal scaling via tenant sharding
4. **Security** - OAuth2, JWT, field-level encryption
5. **Auditability** - Who did what, when, in which tenant/app
6. **Backward Compatibility** - Migrate existing data gracefully

---

## 2. Core Architectural Principles

### 2.1 Separation of Concerns

```
┌──────────────────────────────────────────────────────────────────┐
│                    AppBana Platform Layer                          │
│  (Builder Users, App Management, Schema Design, Page Builder)     │
└──────────────────────────────────────────────────────────────────┘
                              ↓
              ┌───────────────────────────────┐
              │   Tenant Isolation Layer      │
              │  (Org A, Org B, Org C, ...)   │
              └───────────────────────────────┘
                              ↓
        ┌─────────────────────────────────────────────┐
        │         Application Isolation Layer          │
        │  (HR App, CRM App, Inventory App, ...)      │
        └─────────────────────────────────────────────┘
                              ↓
          ┌───────────────────────────────────────┐
          │      Runtime User Isolation Layer      │
          │  (End users, customers, employees)    │
          └───────────────────────────────────────┘
```

### 2.2 Design Principles

**1. Explicit is Better Than Implicit**
- Every entity record MUST have `tenant_id` and `app_id`
- Never rely on session context alone
- Always validate access at database level

**2. Defense in Depth**
- Multiple layers: API, Service, Database
- Fail closed: If tenant_id missing, reject request
- Audit everything: Every access logged

**3. Metadata-Driven Isolation**
- Schema annotations: `@TenantIsolated`, `@AppScoped`
- Auto-inject tenant/app context in queries
- Developer doesn't write isolation logic manually

**4. Clear Boundaries**
- Builder APIs: `/studio/*`, `/apps`, `/schemas`
- Runtime APIs: `/runtime/apps/{appId}/*`
- Never mix builder and runtime concerns

**5. Future-Proof**
- Design for 1000 tenants today
- Easy to add row-level security (RLS) later
- Easy to shard by tenant when needed

---

## 3. System Architecture Overview

### 3.1 High-Level Architecture

```
                    ┌─────────────────────────────────────┐
                    │        Internet / CDN                │
                    └────────────┬────────────────────────┘
                                 │
                    ┌────────────▼────────────────────────┐
                    │     Load Balancer (HTTPS)           │
                    └────────────┬────────────────────────┘
                                 │
         ┌───────────────────────┴───────────────────────┐
         │                                                 │
┌────────▼──────────┐                         ┌──────────▼──────────┐
│  Builder Frontend  │                         │  Runtime Frontend   │
│  (Studio Builder)  │                         │  (End User Apps)    │
│  localhost:5173    │                         │  {app}.appbana.io   │
└────────┬───────────┘                         └──────────┬──────────┘
         │                                                 │
         │  /studio/*, /apps/*                            │  /runtime/*
         │                                                 │
┌────────▼─────────────────────────────────────────────────▼──────────┐
│                      AppBana Backend                                 │
│                  (Java 21 + Virtual Threads)                         │
│                                                                       │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │             Authentication & Authorization Layer              │  │
│  │  - Builder JWT (admin, developer roles)                       │  │
│  │  - Runtime JWT (app-specific users)                          │  │
│  │  - Tenant Context Extraction                                  │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                       │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                    Service Layer                              │  │
│  │  - AppManager, SchemaManager, EntityCrudService               │  │
│  │  - WorkflowExecutionService, PermissionService               │  │
│  │  - TenantContextService (NEW)                                │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                       │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                   Data Access Layer                           │  │
│  │  - Auto-inject tenant_id/app_id in WHERE clauses            │  │
│  │  - Query interceptors for isolation                          │  │
│  │  - Multi-datasource support (per tenant if needed)           │  │
│  └──────────────────────────────────────────────────────────────┘  │
└──────────────────────────┬───────────────────────────────────────────┘
                           │
            ┌──────────────┴──────────────────┐
            │                                  │
┌───────────▼──────────┐         ┌───────────▼──────────┐
│  Platform Database   │         │  Tenant Databases    │
│  (AppBana Builder)   │         │  (Runtime Apps)      │
│                      │         │                      │
│  - app_user          │         │  - {app}_entities    │
│  - appbana_apps      │         │  - {app}_data        │
│  - appbana_pages     │         │  - runtime_users     │
│  - appbana_schemas   │         │  - workflows         │
└──────────────────────┘         └──────────────────────┘
```

### 3.2 Request Flow Example

**Scenario: End user loads "Employee List" page in HR App**

```
1. Browser → GET /runtime/apps/hr-app/employees?page=1
2. Runtime Frontend checks localStorage for runtime_jwt
3. Backend receives request:
   - Extract tenant_id from JWT (e.g., "acme-corp")
   - Extract app_id from URL (e.g., "hr-app")
   - Extract user_id from JWT (e.g., "emp-123")
4. AuthenticationMiddleware validates:
   - JWT signature valid?
   - User has access to this tenant?
   - User has access to this app?
5. TenantContextMiddleware injects:
   - req.setAttribute("tenant_id", "acme-corp")
   - req.setAttribute("app_id", "hr-app")
   - req.setAttribute("user_id", "emp-123")
6. EntityCrudService.listAll() executes:
   - SELECT * FROM employees 
     WHERE tenant_id = 'acme-corp' 
     AND app_id = 'hr-app'
     AND (visibility = 'public' OR owner_id = 'emp-123')
7. PermissionService.filterFields():
   - User role = 'employee'
   - Hide fields: salary, ssn
8. Response JSON sent to frontend
9. RuntimeRenderer displays filtered data
```

---

## 4. Data Model & Isolation Strategy

### 4.1 Three-Layer Isolation Model

**Layer 1: Tenant Isolation (Organization Level)**
- Every table has `tenant_id VARCHAR(50) NOT NULL`
- Multi-tenant SaaS: `tenant_id` = unique org identifier
- Single-tenant: `tenant_id` = 'default'
- Index: `CREATE INDEX idx_tenant ON {table}(tenant_id)`

**Layer 2: Application Isolation (App Level)**
- Every table has `app_id VARCHAR(100) NOT NULL`
- Composite index: `CREATE INDEX idx_tenant_app ON {table}(tenant_id, app_id)`
- Apps within same tenant are isolated
- Schema: `tenant_acme_app_hr_employees`

**Layer 3: User Isolation (Record Level)**
- Optional: `owner_id`, `created_by`, `visibility`
- For apps that need row-level security
- Example: Users see only their own orders

### 4.2 Entity Schema Metadata

**Current Schema:**
```java
public class EntitySchema {
    private String name;              // "employee"
    private List<Field> fields;
    private String datasourceName;
}
```

**Enhanced Schema (NEW):**
```java
public class EntitySchema {
    private String name;              // "employee"
    private String tenantId;          // NEW: "acme-corp" or null (global)
    private String appId;             // NEW: "hr-app"
    private List<Field> fields;
    private String datasourceName;
    
    // Isolation configuration
    private IsolationConfig isolation; // NEW
}

public class IsolationConfig {
    private boolean tenantIsolated = true;    // 99% of entities
    private boolean appScoped = true;         // 99% of entities
    private RowLevelSecurity rowLevelSecurity; // Optional
}

public class RowLevelSecurity {
    private String ownerField;        // "created_by"
    private String visibilityField;   // "visibility" (public/private/shared)
    private String sharingRules;      // JSON expression
}
```

### 4.3 Automatic Field Injection

**When creating any entity table:**
```sql
CREATE TABLE {tenant_id}_{app_id}_{entity_name} (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,     -- AUTO-INJECTED
    app_id VARCHAR(100) NOT NULL,       -- AUTO-INJECTED
    
    -- User-defined fields
    name VARCHAR(255),
    email VARCHAR(255),
    ...
    
    -- Audit fields (AUTO-INJECTED)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by VARCHAR(100),
    
    -- Indexes
    INDEX idx_tenant_app (tenant_id, app_id),
    INDEX idx_created_by (created_by)
);
```

**Developer Experience:**
- Developer defines: `{ name: "employee", fields: [{name: "name", type: "string"}] }`
- System automatically adds: `tenant_id`, `app_id`, audit fields
- Developer never writes isolation logic

---

## 5. API Design & Routing

### 5.1 URL Structure

**Builder Platform APIs** (for AppBana Studio)
```
/studio/auth/login                    # Builder login
/studio/apps                          # List/create apps
/studio/apps/{appId}                  # Get/update/delete app
/studio/apps/{appId}/schemas          # App schemas
/studio/apps/{appId}/pages            # App pages
/studio/apps/{appId}/workflows        # App workflows
/studio/apps/{appId}/deploy           # Deploy to runtime
/studio/apps/{appId}/preview          # Preview mode
```

**Runtime APIs** (for end-user apps)
```
/runtime/apps/{appId}/auth/login      # App-specific login
/runtime/apps/{appId}/auth/register   # App-specific signup
/runtime/apps/{appId}/{entity}        # CRUD operations
/runtime/apps/{appId}/{entity}/{id}   # Get/update/delete
/runtime/apps/{appId}/workflows/start # Start workflow
/runtime/apps/{appId}/tasks           # My tasks
```

**Why separate `/studio` and `/runtime`?**
- Clear security boundaries
- Different JWT audiences
- Different rate limits
- Easy to deploy on different domains/ports

### 5.2 Request Context Extraction

**Builder Request:**
```java
@Path("/studio/apps")
public class StudioAppRoutes {
    
    @POST
    public Response createApp(CreateAppRequest req, @Context SecurityContext sec) {
        // Extract builder user from JWT
        String builderUserId = sec.getUserPrincipal().getName();
        String tenantId = extractTenantFromJWT(sec); // from custom claim
        
        // Validate: builder user belongs to this tenant
        if (!hasAccessToTenant(builderUserId, tenantId)) {
            return Response.status(403).build();
        }
        
        // Create app with tenant context
        TenantContext ctx = new TenantContext(tenantId, req.appId);
        AppMeta app = appManager.createApp(ctx, req);
        
        return Response.ok(app).build();
    }
}
```

**Runtime Request:**
```java
@Path("/runtime/apps/{appId}")
public class RuntimeEntityRoutes {
    
    @GET
    @Path("/{entity}")
    public Response listEntities(
        @PathParam("appId") String appId,
        @PathParam("entity") String entity,
        @Context SecurityContext sec
    ) {
        // Extract runtime user from JWT
        String runtimeUserId = sec.getUserPrincipal().getName();
        
        // Extract tenant from JWT custom claim
        String tenantId = extractTenantFromJWT(sec);
        
        // Validate: user has access to this app
        if (!hasAccessToApp(runtimeUserId, tenantId, appId)) {
            return Response.status(403).build();
        }
        
        // Create context
        TenantContext ctx = new TenantContext(tenantId, appId);
        
        // Load schema for this app
        EntitySchema schema = schemaManager.loadSchemaForApp(ctx, entity);
        if (schema == null) {
            return Response.status(404).entity("Entity not found in app").build();
        }
        
        // Execute query with auto-injected WHERE clauses
        List<Map<String, Object>> rows = entityCrudService.listAll(ctx, schema, runtimeUserId);
        
        return Response.ok(rows).build();
    }
}
```

### 5.3 Tenant Context Propagation

**New Class: TenantContext**
```java
public class TenantContext {
    private final String tenantId;
    private final String appId;
    private final String userId;      // Optional: runtime user
    private final String requestId;   // For tracing
    
    // Thread-local storage for implicit propagation
    private static final ThreadLocal<TenantContext> CONTEXT = new ThreadLocal<>();
    
    public static void set(TenantContext ctx) {
        CONTEXT.set(ctx);
    }
    
    public static TenantContext get() {
        TenantContext ctx = CONTEXT.get();
        if (ctx == null) {
            throw new IllegalStateException("No tenant context set");
        }
        return ctx;
    }
    
    public static void clear() {
        CONTEXT.remove();
    }
}
```

**Middleware Sets Context:**
```java
public class TenantContextMiddleware implements Filter {
    
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) {
        HttpServletRequest httpReq = (HttpServletRequest) req;
        
        try {
            // Extract from JWT claims
            String tenantId = extractTenantId(httpReq);
            String appId = extractAppId(httpReq);
            String userId = extractUserId(httpReq);
            
            // Set thread-local context
            TenantContext ctx = new TenantContext(tenantId, appId, userId);
            TenantContext.set(ctx);
            
            // Continue chain
            chain.doFilter(req, res);
        } finally {
            // Always clear context
            TenantContext.clear();
        }
    }
}
```

---

## 6. Security Architecture

### 6.1 Dual Authentication System

**Builder Authentication** (for Studio users)
```
┌────────────────────────────────────────────┐
│         Builder User (Developer)            │
│  - Login: /studio/auth/login                │
│  - JWT Audience: "appbana-builder"         │
│  - Claims:                                  │
│    * sub: builderUserId                     │
│    * tenant_id: "acme-corp"                │
│    * role: "admin" | "developer" | "viewer"│
│    * permissions: ["create_app", ...]      │
│  - Stored: localStorage.builderJwt         │
└────────────────────────────────────────────┘
```

**Runtime Authentication** (for end users)
```
┌────────────────────────────────────────────┐
│          Runtime User (End User)            │
│  - Login: /runtime/apps/{appId}/auth/login  │
│  - JWT Audience: "appbana-runtime-{appId}" │
│  - Claims:                                  │
│    * sub: runtimeUserId                     │
│    * tenant_id: "acme-corp"                │
│    * app_id: "hr-app"                      │
│    * role: "employee" | "manager"          │
│  - Stored: localStorage.runtimeJwt_{appId} │
└────────────────────────────────────────────┘
```

**Why Two JWT Systems?**
- **Security**: Builder cannot impersonate runtime users
- **Permissions**: Different permission models
- **Revocation**: Revoking builder access doesn't affect runtime
- **Audit**: Clear separation in logs

### 6.2 Role-Based Access Control

**Builder Roles** (Global to AppBana)
```typescript
{
  "admin": {
    "permissions": ["*"],  // Full access to all tenants
    "description": "System administrator"
  },
  "tenant-admin": {
    "permissions": [
      "tenant:view",
      "tenant:manage-users",
      "app:create",
      "app:update",
      "app:delete",
      "app:deploy"
    ],
    "scope": "tenant",  // Only this tenant's apps
    "description": "Organization administrator"
  },
  "developer": {
    "permissions": [
      "app:view",
      "app:update",
      "schema:create",
      "schema:update",
      "page:create",
      "page:update"
    ],
    "scope": "app",  // Only assigned apps
    "description": "Application developer"
  },
  "viewer": {
    "permissions": ["app:view", "schema:view", "page:view"],
    "scope": "app",
    "description": "Read-only access"
  }
}
```

**Runtime Roles** (Defined per app)
```typescript
// HR App roles
{
  "hr-admin": {
    "permissions": ["employee:*", "salary:view", "salary:edit"],
    "description": "HR Administrator"
  },
  "manager": {
    "permissions": ["employee:view", "employee:update", "timesheet:approve"],
    "description": "Department Manager"
  },
  "employee": {
    "permissions": ["employee:view-self", "timesheet:submit"],
    "description": "Regular Employee"
  }
}
```

### 6.3 Field-Level Security (FLS) with Tenant Context

**Current FLS Table:**
```sql
CREATE TABLE appbana_field_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_name VARCHAR(100) NOT NULL,
    field_name VARCHAR(100) NOT NULL,
    role_name VARCHAR(100) NOT NULL,
    can_read BOOLEAN DEFAULT FALSE,
    can_edit BOOLEAN DEFAULT FALSE
);
```

**Enhanced FLS Table (with tenant/app scope):**
```sql
CREATE TABLE appbana_field_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,          -- NEW
    app_id VARCHAR(100) NOT NULL,            -- NEW
    entity_name VARCHAR(100) NOT NULL,
    field_name VARCHAR(100) NOT NULL,
    role_name VARCHAR(100) NOT NULL,
    can_read BOOLEAN DEFAULT FALSE,
    can_edit BOOLEAN DEFAULT FALSE,
    
    INDEX idx_tenant_app_entity (tenant_id, app_id, entity_name),
    INDEX idx_tenant_app_role (tenant_id, app_id, role_name),
    UNIQUE KEY uk_permission (tenant_id, app_id, entity_name, field_name, role_name)
);
```

**Usage:**
```java
// Check if user can read field
PermissionService.canRead(
    tenantId: "acme-corp",
    appId: "hr-app",
    entityName: "employee",
    fieldName: "salary",
    roleNames: ["employee", "manager"]
) // Returns: false for employee, true for manager
```

---

## 7. Database Schema Design

### 7.1 Platform Tables (AppBana Builder)

**appbana_builder_users** - Who can use Studio
```sql
CREATE TABLE appbana_builder_users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    role VARCHAR(50) NOT NULL,  -- admin, tenant-admin, developer, viewer
    status VARCHAR(50) DEFAULT 'active',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP,
    
    INDEX idx_tenant (tenant_id),
    INDEX idx_email (email)
);
```

**appbana_tenants** - Organizations using AppBana
```sql
CREATE TABLE appbana_tenants (
    id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    domain VARCHAR(255),           -- acme.com
    subdomain VARCHAR(100),        -- acme.appbana.io
    plan VARCHAR(50),              -- free, pro, enterprise
    status VARCHAR(50) DEFAULT 'active',
    max_apps INT DEFAULT 10,
    max_users INT DEFAULT 100,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_subdomain (subdomain)
);
```

**appbana_apps** - Apps created in Studio
```sql
CREATE TABLE appbana_apps (
    id VARCHAR(100) NOT NULL,
    tenant_id VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    version VARCHAR(50) DEFAULT '1.0.0',
    status VARCHAR(50) DEFAULT 'draft',  -- draft, published, archived
    deployment_url VARCHAR(500),         -- https://hr.acme.appbana.io
    created_by VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deployed_at TIMESTAMP,
    json_metadata JSON,                  -- Full app metadata
    
    PRIMARY KEY (tenant_id, id),
    FOREIGN KEY (tenant_id) REFERENCES appbana_tenants(id),
    INDEX idx_status (status),
    INDEX idx_created_by (created_by)
);
```

**appbana_schemas** - Entity schemas per app
```sql
CREATE TABLE appbana_schemas (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,
    app_id VARCHAR(100) NOT NULL,
    entity_name VARCHAR(100) NOT NULL,
    json_schema JSON NOT NULL,
    version INT DEFAULT 1,
    status VARCHAR(50) DEFAULT 'active',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    FOREIGN KEY (tenant_id, app_id) REFERENCES appbana_apps(tenant_id, id),
    UNIQUE KEY uk_tenant_app_entity (tenant_id, app_id, entity_name),
    INDEX idx_tenant_app (tenant_id, app_id)
);
```

**appbana_pages** - Pages per app
```sql
CREATE TABLE appbana_pages (
    id VARCHAR(100) NOT NULL,
    tenant_id VARCHAR(50) NOT NULL,
    app_id VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    path VARCHAR(500) NOT NULL,          -- /dashboard
    type VARCHAR(50) DEFAULT 'page',
    json_metadata JSON,                  -- Full page metadata
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    PRIMARY KEY (tenant_id, app_id, id),
    FOREIGN KEY (tenant_id, app_id) REFERENCES appbana_apps(tenant_id, id),
    INDEX idx_app_path (tenant_id, app_id, path)
);
```

### 7.2 Runtime Tables (Per-App Data)

**runtime_users_{appId}** - End users of the app
```sql
CREATE TABLE runtime_users_{tenant_id}_{app_id} (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,
    app_id VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    role VARCHAR(100),                   -- App-specific roles
    status VARCHAR(50) DEFAULT 'active',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP,
    
    INDEX idx_tenant_app (tenant_id, app_id),
    UNIQUE KEY uk_email_per_app (tenant_id, app_id, email)
);
```

**{entity}_{tenant_id}_{app_id}** - Entity data tables
```sql
-- Example: employee table for HR app
CREATE TABLE employee_acme_corp_hr_app (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL DEFAULT 'acme-corp',
    app_id VARCHAR(100) NOT NULL DEFAULT 'hr-app',
    
    -- User-defined fields
    name VARCHAR(255),
    email VARCHAR(255),
    department VARCHAR(100),
    salary DECIMAL(10,2),
    
    -- Audit fields
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by VARCHAR(100),
    
    INDEX idx_tenant_app (tenant_id, app_id),
    INDEX idx_created_by (created_by)
);
```

### 7.3 Workflow Tables (Already Multi-Tenant in V6)

**appbana_wf_definition** - Already has tenant_id
```sql
CREATE TABLE appbana_wf_definition (
    id VARCHAR(100) NOT NULL,
    tenant_id VARCHAR(50) NOT NULL,      -- ✅ Already exists
    app_id VARCHAR(100) NOT NULL,        -- ✅ Already exists
    name VARCHAR(255),
    version INT DEFAULT 1,
    status VARCHAR(50),
    json_metadata JSON,
    
    PRIMARY KEY (tenant_id, app_id, id),
    INDEX idx_tenant_app (tenant_id, app_id)
);
```

**appbana_wf_instance, appbana_wf_token, appbana_wf_history** - Same pattern

---

## 8. Frontend Architecture

### 8.1 Dual Frontend Structure

**Builder Frontend** (`/studio`)
```
app-bana-ui/
├── src/
│   ├── builder/              # Studio Builder (for developers)
│   │   ├── components/
│   │   │   ├── AppManager.ts       # Manage apps
│   │   │   ├── PageManager.ts      # Manage pages
│   │   │   ├── EntityManager.ts    # Manage schemas
│   │   │   ├── WorkflowDesigner.ts # Manage workflows
│   │   │   └── TenantSwitcher.ts   # NEW: Switch tenants
│   │   ├── store/
│   │   │   ├── AppStore.ts         # App state
│   │   │   ├── TenantStore.ts      # NEW: Tenant state
│   │   │   └── AuthStore.ts        # Builder auth
│   │   └── studio.ts               # Entry point
│   │
│   ├── runtime/              # Runtime Renderer (for end users)
│   │   ├── shell/
│   │   │   ├── RuntimeShell.ts     # App container
│   │   │   ├── RuntimeAuth.ts      # NEW: Runtime login/signup
│   │   │   └── RuntimeNav.ts       # App navigation
│   │   ├── components/
│   │   │   ├── RuntimeForm.ts      # Form renderer
│   │   │   ├── RuntimeTable.ts     # Table renderer
│   │   │   └── RuntimePage.ts      # Page renderer
│   │   ├── store/
│   │   │   ├── RuntimeStore.ts     # NEW: Runtime state
│   │   │   └── RuntimeAuth.ts      # NEW: Runtime auth
│   │   └── runtime.ts              # Entry point
│   │
│   └── core/                 # Shared core
│       ├── api-client.ts
│       ├── router.ts
│       └── registry.ts
```

### 8.2 Context Management

**Builder Context:**
```typescript
// TenantStore.ts (NEW)
export class TenantStore {
  private currentTenant: Tenant | null = null;
  private availableTenants: Tenant[] = [];
  
  async switchTenant(tenantId: string) {
    // Load tenant info
    const tenant = await apiClient.get(`/studio/tenants/${tenantId}`);
    this.currentTenant = tenant;
    
    // Reload apps for new tenant
    await appStore.loadApps();
    
    // Update JWT with new tenant claim
    await authStore.refreshToken();
  }
}

// AppStore.ts (Enhanced)
export class AppStore {
  async loadApps() {
    const tenantId = tenantStore.currentTenant?.id || 'default';
    const response = await apiClient.get(`/studio/apps?tenant=${tenantId}`);
    this.apps = response.apps;
  }
  
  async createApp(request: CreateAppRequest) {
    const tenantId = tenantStore.currentTenant?.id || 'default';
    const response = await apiClient.post('/studio/apps', {
      ...request,
      tenantId
    });
    return response.app;
  }
}
```

**Runtime Context:**
```typescript
// RuntimeStore.ts (NEW)
export class RuntimeStore {
  private currentAppId: string | null = null;
  private tenantId: string | null = null;
  private runtimeUser: RuntimeUser | null = null;
  
  async loadApp(appId: string) {
    // Extract tenant from subdomain or config
    this.tenantId = this.extractTenantFromDomain();
    this.currentAppId = appId;
    
    // Load app metadata
    const app = await apiClient.get(`/runtime/apps/${appId}/metadata`);
    return app;
  }
  
  async login(email: string, password: string) {
    const response = await apiClient.post(
      `/runtime/apps/${this.currentAppId}/auth/login`,
      { email, password }
    );
    
    // Store runtime JWT (app-specific)
    localStorage.setItem(`runtime_jwt_${this.currentAppId}`, response.token);
    this.runtimeUser = response.user;
  }
  
  async fetchEntity(entityName: string, params: any) {
    const response = await apiClient.get(
      `/runtime/apps/${this.currentAppId}/${entityName}`,
      { params }
    );
    return response.data;
  }
}
```

### 8.3 Magic Seed Data Fix

**EntityManager.ts (Fixed):**
```typescript
export class EntityManager extends LitElement {
  async handleMagicSeed() {
    try {
      // 1. Get current context
      const tenantId = tenantStore.currentTenant?.id || 'default';
      const appId = appStore.currentApp?.id;
      
      if (!appId) {
        this.showError('No app selected');
        return;
      }
      
      // 2. Generate seed data (AI)
      const generateResponse = await apiClient.post('/studio/ai/seed-data', {
        entityName: this.selectedEntity.name,
        schema: this.selectedEntity,
        count: 10
      });
      
      const generatedData = generateResponse.data;
      
      // 3. Save with proper context (FIX!)
      const saveResponse = await apiClient.post(
        `/studio/apps/${appId}/entities/${this.selectedEntity.name}/seed`,
        {
          tenantId,
          appId,
          data: generatedData
        }
      );
      
      if (saveResponse.ok) {
        this.showSuccess(`Generated ${generatedData.length} records`);
        await this.loadEntityData(); // Refresh
      }
    } catch (error) {
      this.showError(`Seed failed: ${error.message}`);
    }
  }
}
```

---

## 9. Workflow & Business Process Isolation

### 9.1 Workflow Scope

**Workflows belong to specific apps:**
```typescript
export interface WorkflowMeta {
  id: string;
  tenantId: string;     // NEW
  appId: string;        // NEW
  name: string;
  version: number;
  trigger: {
    entityId: string;
    event: 'ON_CREATE' | 'ON_UPDATE' | 'ON_DELETE';
    condition?: string;
  };
  nodes: WorkflowNode[];
  transitions: WorkflowTransition[];
}
```

**Storage:**
```
Database: appbana_wf_definition
Primary Key: (tenant_id, app_id, id, version)
```

### 9.2 Cross-App Workflows (Future)

**Phase 3: Allow workflows to span multiple apps in same tenant**
```typescript
export interface CrossAppWorkflow {
  id: string;
  tenantId: string;
  apps: string[];  // ["hr-app", "finance-app"]
  trigger: {
    appId: string;
    entityId: string;
    event: string;
  };
  nodes: WorkflowNode[];  // Nodes can reference entities from different apps
}
```

**Example: "Employee Onboarding" workflow**
```
1. HR App: Create employee record (trigger)
2. IT App: Create email account (service task)
3. Finance App: Setup payroll (service task)
4. Manager: Approve laptop request (user task in HR app)
5. IT App: Provision laptop (service task)
```

---

## 10. Implementation Roadmap

### Phase 1: Foundation (Week 1-2) - CRITICAL

**Goal**: Enable basic tenant/app isolation

**Tasks**:
1. ✅ Create TenantContext.java class
2. ✅ Update EntityCrudService to accept TenantContext
3. ✅ Add tenant_id/app_id columns to all entity tables (migration)
4. ✅ Update GenericEntityRoutes with `/studio/apps/{appId}/{entity}` routes
5. ✅ Update SchemaManager to link schemas to apps
6. ✅ Fix Magic Seed Data in EntityManager.ts
7. ✅ Add TenantContextMiddleware to inject context
8. ✅ Update all SELECT/INSERT/UPDATE/DELETE to include tenant_id/app_id

**Deliverable**: Magic Seed Data works, entities are app-scoped

### Phase 2: Runtime Authentication (Week 3-4)

**Goal**: Enable end users to login to runtime apps

**Tasks**:
1. Create RuntimeAuthController with `/runtime/apps/{appId}/auth/login`
2. Create runtime_users table (per tenant/app)
3. Generate app-specific JWTs with `app_id` claim
4. Create RuntimeShell.ts with login/signup UI
5. Update RuntimeStore.ts with runtime auth state
6. Add RuntimeAuthMiddleware for validation
7. Update PermissionService to support runtime roles

**Deliverable**: End users can login to deployed apps

### Phase 3: Builder Multi-Tenant (Week 5-6)

**Goal**: Support multiple organizations using AppBana

**Tasks**:
1. Create appbana_tenants table
2. Create appbana_builder_users table with tenant_id
3. Add TenantSwitcher.ts component in Studio
4. Update AppStore to filter by tenant
5. Add tenant validation in all Studio APIs
6. Implement tenant-scoped billing/limits
7. Add subdomain routing (acme.appbana.io)

**Deliverable**: Multiple orgs can use AppBana independently

### Phase 4: Advanced Features (Week 7-8)

**Goal**: Production-ready features

**Tasks**:
1. Row-level security (owner-based filtering)
2. Sharing rules between users
3. Cross-app workflows
4. Tenant data export (GDPR compliance)
5. Tenant migration tools
6. Performance optimization (query plan analysis)
7. Comprehensive audit logging

**Deliverable**: Enterprise-ready platform

---

## 11. Migration Strategy

### 11.1 Backward Compatibility

**Challenge**: Existing apps have no tenant_id/app_id

**Solution**: Default migration
```sql
-- Add columns with defaults
ALTER TABLE {entity} ADD COLUMN tenant_id VARCHAR(50) DEFAULT 'default';
ALTER TABLE {entity} ADD COLUMN app_id VARCHAR(100) DEFAULT 'legacy';

-- Backfill from app metadata
UPDATE {entity} SET app_id = (
    SELECT app_id FROM appbana_apps WHERE legacy = true LIMIT 1
);

-- Make required after backfill
ALTER TABLE {entity} MODIFY tenant_id VARCHAR(50) NOT NULL;
ALTER TABLE {entity} MODIFY app_id VARCHAR(100) NOT NULL;

-- Add indexes
CREATE INDEX idx_tenant_app ON {entity}(tenant_id, app_id);
```

### 11.2 Migration Script

```bash
#!/bin/bash
# migrate-to-multitenant.sh

echo "Starting multi-tenant migration..."

# 1. Backup database
pg_dump appbana > backup_$(date +%Y%m%d_%H%M%S).sql

# 2. Add tenant_id/app_id to all entity tables
for table in $(psql -t -c "SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename NOT LIKE 'appbana_%'"); do
    echo "Migrating table: $table"
    psql -c "ALTER TABLE $table ADD COLUMN tenant_id VARCHAR(50) DEFAULT 'default'"
    psql -c "ALTER TABLE $table ADD COLUMN app_id VARCHAR(100) DEFAULT 'legacy'"
done

# 3. Backfill app_id from context
# (Manual step: map tables to apps)

# 4. Make columns required
for table in $(psql -t -c "SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename NOT LIKE 'appbana_%'"); do
    psql -c "ALTER TABLE $table MODIFY tenant_id VARCHAR(50) NOT NULL"
    psql -c "ALTER TABLE $table MODIFY app_id VARCHAR(100) NOT NULL"
    psql -c "CREATE INDEX idx_tenant_app_$table ON $table(tenant_id, app_id)"
done

echo "Migration complete!"
```

### 11.3 Gradual Rollout

**Stage 1**: Add columns, keep old APIs working
- New columns added but not enforced
- Old `/api/{entity}` still works (default tenant/app)
- New `/studio/apps/{appId}/{entity}` works in parallel

**Stage 2**: Migrate existing apps
- Update app metadata with tenant_id/app_id
- Backfill entity data
- Test thoroughly

**Stage 3**: Enforce isolation
- Make tenant_id/app_id required
- Deprecate old APIs with 6-month sunset
- Add monitoring for any missing context

**Stage 4**: Full multi-tenant
- Enable tenant onboarding
- Add tenant management UI
- Launch SaaS pricing

---

## 12. Trade-offs & Design Decisions

### 12.1 Design Decision Matrix

| Decision | Option A | Option B | Chosen | Rationale |
|----------|----------|----------|--------|-----------|
| **Isolation Method** | Row-level (tenant_id column) | Schema-per-tenant | **A** | Simpler queries, easier backups, cost-effective |
| **App Storage** | Filesystem (JSON) | Database (SQL) | **B** | Already implemented (V9), better querying, ACID |
| **URL Structure** | `/apps/{appId}/{entity}` | `/runtime/{tenantId}/{appId}/{entity}` | **A** | Simpler, tenant in JWT, less verbose |
| **JWT Strategy** | Single JWT for all | Separate builder/runtime JWTs | **B** | Better security boundaries, clearer audit |
| **Schema Naming** | `{tenant}_{app}_{entity}` | `{entity}` with columns | **B** | Cleaner, standard SQL, easier to query |
| **User Tables** | Shared users table | Separate builder/runtime tables | **B** | Clear separation, different auth flows |
| **Tenant ID Source** | Custom JWT claim | Subdomain parsing | **A** | More flexible, works with custom domains |

### 12.2 Key Trade-offs

**1. Performance vs. Isolation**
- **Choice**: Row-level isolation with indexed queries
- **Trade-off**: Slightly slower queries (~5-10ms) vs. perfect isolation
- **Mitigation**: Composite indexes on (tenant_id, app_id), query plan optimization

**2. Complexity vs. Features**
- **Choice**: Phased rollout (4 phases over 8 weeks)
- **Trade-off**: Takes longer vs. getting it right once
- **Mitigation**: Each phase delivers value, backward compatible

**3. Storage Cost vs. Scalability**
- **Choice**: Single database with row-level isolation
- **Trade-off**: Higher storage in one DB vs. easier sharding later
- **Mitigation**: Plan for tenant sharding at 1000+ tenants

**4. Developer Experience vs. Safety**
- **Choice**: Auto-inject tenant_id/app_id in queries
- **Trade-off**: Developer doesn't see isolation logic vs. could be forgotten
- **Mitigation**: Middleware enforces, unit tests verify, audit logs catch violations

### 12.3 Future Considerations

**Scaling to 10,000+ Tenants**
- Shard database by tenant_id (hash or range partitioning)
- Dedicated databases for premium tenants
- Read replicas per region
- Redis cache layer for metadata

**Multi-Region Deployment**
- Deploy backend in US, EU, APAC
- Tenant affinity (EU tenants → EU DB)
- GDPR compliance: data residency
- Cross-region replication for disaster recovery

**Advanced Security**
- Encryption at rest for PHI/PII fields
- Key per tenant (KMS integration)
- IP whitelisting per tenant
- OAuth2/SAML SSO for enterprise tenants
- Certificate pinning for mobile apps

---

## 13. Visual Diagrams

### 13.1 Data Flow: Create Entity in App

```
┌─────────────┐
│   Builder   │ 1. Open EntityManager for "HR App"
│   (Studio)  │
└──────┬──────┘
       │
       │ 2. Click "Add Entity" → Name: "employee"
       │
       ▼
┌─────────────────────────────────────────────────────────┐
│  EntityManager.ts                                        │
│  - currentApp = "hr-app"                                │
│  - tenantId = "acme-corp" (from TenantStore)           │
│  - Sends: POST /studio/apps/hr-app/schemas             │
│  - Body: { name: "employee", fields: [...] }           │
└──────────────────────┬──────────────────────────────────┘
                       │
                       ▼
┌───────────────────────────────────────────────────────────┐
│  Backend: SchemaRoutes.java                               │
│  1. Extract tenantId from JWT: "acme-corp"               │
│  2. Extract appId from URL: "hr-app"                     │
│  3. Validate: builder has access to this tenant/app      │
│  4. Create TenantContext(tenantId, appId)                │
│  5. Call: SchemaManager.saveSchema(context, schema)      │
└──────────────────────┬────────────────────────────────────┘
                       │
                       ▼
┌───────────────────────────────────────────────────────────┐
│  SchemaManager.java                                       │
│  1. Add tenant_id/app_id to schema metadata              │
│  2. Auto-inject system fields (tenant_id, app_id, audit) │
│  3. Generate DDL:                                         │
│     CREATE TABLE employee_acme_corp_hr_app (              │
│       id BIGINT PRIMARY KEY,                              │
│       tenant_id VARCHAR(50) NOT NULL DEFAULT 'acme-corp', │
│       app_id VARCHAR(100) NOT NULL DEFAULT 'hr-app',     │
│       name VARCHAR(255),                                  │
│       ...                                                 │
│     )                                                     │
│  4. Execute DDL                                           │
│  5. Save schema metadata to appbana_schemas table        │
└──────────────────────┬────────────────────────────────────┘
                       │
                       ▼
┌───────────────────────────────────────────────────────────┐
│  Database: appbana (Platform DB)                          │
│  ┌─────────────────────────────────────────────────────┐ │
│  │ appbana_schemas                                      │ │
│  ├─────────────────────────────────────────────────────┤ │
│  │ id | tenant_id  | app_id | entity_name | json_schema│ │
│  │ 1  | acme-corp  | hr-app | employee    | {...}      │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                            │
│  ┌─────────────────────────────────────────────────────┐ │
│  │ employee_acme_corp_hr_app (Entity Table)            │ │
│  ├─────────────────────────────────────────────────────┤ │
│  │ id | tenant_id | app_id | name | email | ...        │ │
│  │ (empty initially)                                    │ │
│  └─────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────┘
```

### 13.2 Data Flow: Runtime User Fetches Data

```
┌─────────────┐
│  End User   │ 1. Opens HR App: https://hr.acme.appbana.io
│  (Browser)  │ 2. Logged in as: john@acme.com (employee)
└──────┬──────┘
       │
       │ 3. Navigate to "Employee List" page
       │
       ▼
┌─────────────────────────────────────────────────────────┐
│  RuntimeShell.ts                                         │
│  - runtimeJwt stored in localStorage                    │
│  - Sends: GET /runtime/apps/hr-app/employees            │
│  - Headers: Authorization: Bearer {runtimeJwt}          │
└──────────────────────┬──────────────────────────────────┘
                       │
                       ▼
┌───────────────────────────────────────────────────────────┐
│  Backend: RuntimeEntityRoutes.java                        │
│  1. TenantContextMiddleware extracts:                    │
│     - tenantId: "acme-corp" (from JWT claim)            │
│     - appId: "hr-app" (from URL)                         │
│     - userId: "john@acme.com" (from JWT sub)            │
│     - role: "employee" (from JWT claim)                 │
│  2. Validate: user has access to this app                │
│  3. Set TenantContext.set(context)                       │
│  4. Call: EntityCrudService.listAll(context, "employee") │
└──────────────────────┬────────────────────────────────────┘
                       │
                       ▼
┌───────────────────────────────────────────────────────────┐
│  EntityCrudService.java                                   │
│  1. Load schema: SchemaManager.loadSchema(                │
│       tenantId: "acme-corp",                              │
│       appId: "hr-app",                                    │
│       entity: "employee"                                  │
│     )                                                     │
│  2. Build query with auto-injection:                     │
│     SELECT * FROM employee_acme_corp_hr_app              │
│     WHERE tenant_id = 'acme-corp'                        │
│       AND app_id = 'hr-app'                              │
│       AND (visibility = 'public'                         │
│            OR created_by = 'john@acme.com')              │
│  3. Execute query                                         │
│  4. Call: PermissionService.filterFields(                │
│       role: "employee",                                   │
│       entity: "employee",                                 │
│       rows: [...]                                         │
│     )                                                     │
└──────────────────────┬────────────────────────────────────┘
                       │
                       ▼
┌───────────────────────────────────────────────────────────┐
│  PermissionService.java                                   │
│  1. Query FLS: appbana_field_permission                  │
│     WHERE tenant_id = 'acme-corp'                        │
│       AND app_id = 'hr-app'                              │
│       AND entity_name = 'employee'                       │
│       AND role_name = 'employee'                         │
│  2. Found rules:                                          │
│     - salary: can_read = false                           │
│     - ssn: can_read = false                              │
│  3. Filter out restricted fields from each row           │
│  4. Return filtered data                                 │
└──────────────────────┬────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────┐
│  Response to Browser                                     │
│  [                                                       │
│    {                                                     │
│      "id": 1,                                            │
│      "name": "John Doe",                                │
│      "email": "john@acme.com",                          │
│      "department": "Engineering"                        │
│      // salary and ssn fields hidden                   │
│    },                                                    │
│    ...                                                   │
│  ]                                                       │
└──────────────────────────────────────────────────────────┘
```

---

## 14. Summary & Next Steps

### What We've Designed

✅ **Complete multi-tenant architecture** with three-layer isolation  
✅ **Dual authentication system** for builder vs runtime users  
✅ **Clear API boundaries** between `/studio` and `/runtime`  
✅ **Automatic tenant/app injection** in all queries  
✅ **Comprehensive security model** with RBAC and FLS  
✅ **Phased implementation plan** over 8 weeks  
✅ **Backward compatible** migration strategy  
✅ **Scalable to 10,000+ tenants** with sharding plan  

### Immediate Next Steps (This Week)

1. **Review this document** - Discuss any concerns or questions
2. **Approve architecture** - Get sign-off from all stakeholders
3. **Start Phase 1** - Begin implementation:
   - Day 1-2: Create TenantContext, update EntityCrudService
   - Day 3-4: Update database schema (add columns)
   - Day 5: Add middleware, update routes
   - Day 6-7: Fix Magic Seed Data, test end-to-end

### Success Criteria

After Phase 1 (Week 2):
- ✅ Magic Seed Data works correctly
- ✅ Entities are scoped to apps
- ✅ No data leakage between apps
- ✅ All existing tests pass
- ✅ New tests for isolation added

After Full Implementation (Week 8):
- ✅ Multiple tenants can use AppBana
- ✅ End users can login to runtime apps
- ✅ Complete data isolation
- ✅ Production-ready security
- ✅ Comprehensive documentation

---

## 15. Appendix

### A. Glossary

- **Tenant**: An organization using AppBana (e.g., Acme Corp)
- **Builder User**: Developer who creates apps in Studio
- **Runtime User**: End user who uses deployed apps
- **App**: Application created in Studio (e.g., HR App)
- **Entity**: Data model/schema (e.g., Employee, Customer)
- **TenantContext**: Data structure holding tenant_id/app_id/user_id
- **Isolation**: Data segregation to prevent cross-tenant access
- **FLS**: Field-Level Security (hide fields based on role)
- **RLS**: Row-Level Security (show only user's own records)

### B. References

- **Current Architecture**: `docs/01-ARCHITECTURE.md`
- **Security Features**: `docs/SECURITY_FEATURES.md`
- **Workflow Spec**: `docs/specs/WORKFLOW.md`
- **Auth Design**: `docs/specs/AUTH.md`
- **Database Migrations**: `app-bana-service/src/main/resources/db/migration/`

---

**Document Status**: DRAFT for Review  
**Next Review**: January 2, 2026  
**Approval Required**: Product Owner, CTO, Lead Architect  

**Questions?** Contact the architecture team for clarification.
