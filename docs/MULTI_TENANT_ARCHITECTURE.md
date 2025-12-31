# Multi-Tenant Architecture for AppBana

**Date**: December 31, 2025  
**Status**: Implementation Required  
**Priority**: HIGH (Blocker for Magic Seed Data)

---

## Problem Statement

Currently, AppBana has NO tenant or application isolation:
- All entities are stored globally at `/api/{entity}`
- No separation between different tenants (companies/organizations)
- No separation between different applications created by the same tenant
- Magic Seed Data fails because it tries to save to `/api/user` which doesn't have tenant/app context

## Required Architecture

### URL Structure

```
Builder Platform APIs (AppBana itself):
/api/auth/*                           # Authentication
/api/apps                             # App management
/api/schemas                          # Schema management
/api/ai/*                             # AI features
/api/templates/*                      # Page templates

Runtime Application APIs (User-created apps):
/api/tenants/{tenantId}/apps/{appId}/{entity}   # Full isolation
OR (simplified for single-tenant mode):
/api/apps/{appId}/{entity}                      # Application-scoped
```

### Database Structure

#### Option 1: Tenant + App Columns (Recommended)
```sql
-- Add to ALL entity tables
ALTER TABLE {entity_name} ADD COLUMN tenant_id VARCHAR(50);
ALTER TABLE {entity_name} ADD COLUMN app_id VARCHAR(50);
ALTER TABLE {entity_name} ADD INDEX idx_tenant_app (tenant_id, app_id);

-- All queries become:
SELECT * FROM user_information 
WHERE tenant_id = ? AND app_id = ?
```

#### Option 2: Schema Prefix (More Isolation)
```sql
-- Each tenant gets a schema
CREATE SCHEMA tenant_acme;
CREATE TABLE tenant_acme.user_information (...);

-- Each app within tenant gets table prefix
CREATE TABLE tenant_acme.app_hr_user_information (...);
CREATE TABLE tenant_acme.app_crm_customer (...);
```

---

## Implementation Plan

### Phase 1: Add Tenant/App Context (IMMEDIATE)

#### 1.1 Update EntityCrudService
```java
public class EntityCrudService {
    // Add context parameters
    public Object insertRecord(
        String tenantId,      // NEW
        String appId,         // NEW
        EntitySchema schema, 
        Map<String, Object> data
    ) throws SQLException {
        // Automatically add tenant_id and app_id to data
        data.put("tenant_id", tenantId);
        data.put("app_id", appId);
        
        // Rest of insert logic...
    }
    
    public List<Map<String, Object>> listAll(
        String tenantId,     // NEW
        String appId,        // NEW
        EntitySchema schema
    ) throws SQLException {
        String sql = "SELECT * FROM " + quote(schema.getName()) + 
                    " WHERE tenant_id = ? AND app_id = ?";
        // Execute with tenant_id and app_id params
    }
}
```

#### 1.2 Update Routes

**New Route Structure:**
```java
// GenericEntityRoutes.java

// OPTION A: Full multi-tenant (for SaaS)
router.post("/api/tenants/{tenantId}/apps/{appId}/{entity}", (req, res) -> {
    String tenantId = req.pathParam("tenantId");
    String appId = req.pathParam("appId");
    String entity = req.pathParam("entity");
    
    // Validate tenant access
    if (!hasAccessToTenant(currentUser, tenantId)) {
        res.json(403, Map.of("error", "Access denied to tenant"));
        return;
    }
    
    // Validate app access
    if (!hasAccessToApp(currentUser, appId)) {
        res.json(403, Map.of("error", "Access denied to app"));
        return;
    }
    
    // Insert with tenant/app context
    Object id = crud.insertRecord(tenantId, appId, schema, data);
    res.json(201, Map.of("id", id));
});

// OPTION B: Simplified (app-scoped only for now)
router.post("/api/apps/{appId}/{entity}", (req, res) -> {
    String appId = req.pathParam("appId");
    String tenantId = getCurrentTenantId(req); // From session/auth
    String entity = req.pathParam("entity");
    
    // Validate app access
    if (!hasAccessToApp(currentUser, appId)) {
        res.json(403, Map.of("error", "Access denied to app"));
        return;
    }
    
    // Insert with tenant/app context
    Object id = crud.insertRecord(tenantId, appId, schema, data);
    res.json(201, Map.of("id", id));
});
```

#### 1.3 Update Schema Creation

All entities must have tenant_id and app_id fields:

```java
public class SchemaManager {
    public static EntitySchema ensureTenantAppFields(EntitySchema schema) {
        List<EntitySchema.Field> fields = new ArrayList<>(schema.getFields());
        
        // Add tenant_id if not exists
        if (fields.stream().noneMatch(f -> "tenant_id".equals(f.getName()))) {
            EntitySchema.Field tenantField = new EntitySchema.Field();
            tenantField.setName("tenant_id");
            tenantField.setType("string");
            tenantField.setLength(50);
            tenantField.setRequired(true);
            fields.add(0, tenantField);
        }
        
        // Add app_id if not exists
        if (fields.stream().noneMatch(f -> "app_id".equals(f.getName()))) {
            EntitySchema.Field appField = new EntitySchema.Field();
            appField.setName("app_id");
            appField.setType("string");
            appField.setLength(50);
            appField.setRequired(true);
            fields.add(1, appField);
        }
        
        schema.setFields(fields);
        return schema;
    }
}
```

### Phase 2: Update Frontend (EntityManager)

```typescript
// EntityManager.ts
async handleMagicSeed() {
    const appId = this.currentAppId; // Get from context
    const tenantId = this.currentTenantId; // Get from session
    
    // Generate seed data
    const response = await fetch('/api/ai/seed-data', {
        method: 'POST',
        body: JSON.stringify({
            entityName: this.selectedEntity.name,
            schema: this.selectedEntity,
            count: 10
        })
    });
    
    const data = await response.json();
    
    // Save with proper context
    const saveResponse = await fetch(
        `/api/apps/${appId}/${this.selectedEntity.name}`,
        {
            method: 'POST',
            body: JSON.stringify(data)
        }
    );
}
```

---

## Migration Strategy

### Step 1: Add Columns to Existing Tables
```sql
-- Migration script
ALTER TABLE user_information ADD COLUMN tenant_id VARCHAR(50);
ALTER TABLE user_information ADD COLUMN app_id VARCHAR(50);
UPDATE user_information SET tenant_id = 'default', app_id = 'default';
ALTER TABLE user_information MODIFY tenant_id VARCHAR(50) NOT NULL;
ALTER TABLE user_information MODIFY app_id VARCHAR(50) NOT NULL;
CREATE INDEX idx_tenant_app ON user_information(tenant_id, app_id);
```

### Step 2: Update All Queries
- Add WHERE clauses for tenant_id and app_id
- Update INSERT statements to include both fields
- Update EntityCrudService methods

### Step 3: Frontend Context Management
- Store current tenant_id in session
- Store current app_id when working on an app
- Pass both in all API calls

---

## Benefits

✅ **Data Isolation**: Each tenant's data is completely separate  
✅ **Application Isolation**: Different apps can have entities with same names  
✅ **Security**: Cannot accidentally access another tenant's/app's data  
✅ **Scalability**: Easy to move tenants to different databases later  
✅ **Compliance**: GDPR, HIPAA - data residency per tenant  

---

## Recommended Approach

**Start with Option B (App-scoped):**
1. Simpler to implement immediately
2. Single tenant for now ("default")
3. Can add full multi-tenancy later
4. Unblocks Magic Seed Data feature TODAY

**API Structure:**
```
/api/apps/{appId}/user_information      # App-scoped entity
/api/apps/hr-app/employees              # HR app employees
/api/apps/crm-app/customers             # CRM app customers
```

---

## Next Actions

1. ✅ Create TenantContext class to hold tenant_id/app_id
2. ✅ Update EntityCrudService to accept tenant/app context
3. ✅ Update GenericEntityRoutes with new URL pattern
4. ✅ Add tenant_id/app_id columns via Flyway migration
5. ✅ Update frontend EntityManager to use new API
6. ✅ Test Magic Seed Data with proper isolation

---

**Estimated Time**: 4-6 hours for Phase 1 (App-scoped isolation)  
**Blocking Issue**: Magic Seed Data cannot save without this  
**Impact**: HIGH - Enables proper app development workflow
