# IMMEDIATE FIX: Magic Seed Data - App Isolation

**Date**: December 31, 2025  
**Issue**: Magic Seed Data generates data but fails to save via `/api/user`  
**Root Cause**: No tenant/application context in API calls  
**Status**: IMPLEMENTING NOW

---

## 🔴 The Problem

When you click "Magic Seed Data":
1. ✅ AI generates realistic data successfully
2. ❌ Frontend tries to POST to `/api/user_information`
3. ❌ Backend has NO context about which app this data belongs to
4. ❌ Save fails with 404 "Not Found" because entity path is wrong

**Current Wrong API**: `/api/user` (global, no app context)  
**Required API**: `/api/apps/{appId}/user` (app-scoped)

---

## ✅ The Solution (Implemented)

### 1. Created TenantContext Class
Location: `app-bana-service/src/main/java/com/appbana/model/TenantContext.java`

```java
// For single-tenant mode (default for now)
TenantContext context = TenantContext.forApp("hr-management-app");

// For multi-tenant mode (future)
TenantContext context = new TenantContext("acme-corp", "hr-management-app");
```

### 2. New API Routes Required

**Add to GenericEntityRoutes.java:**
```java
// App-scoped entity routes (NEW)
router.post("/api/apps/{appId}/{entity}", (req, res) -> {
    String appId = req.pathParam("appId");
    String entity = req.pathParam("entity");
    
    // Create context (default tenant for now)
    TenantContext context = TenantContext.forApp(appId);
    
    // Validate schema belongs to this app
    EntitySchema schema = SchemaManager.loadSchemaForApp(appId, entity);
    if (schema == null) {
        res.json(404, Map.of("error", "Entity not found in app"));
        return;
    }
    
    // Insert with context
    Map<String, Object> data = req.readJson(new TypeReference<>() {});
    Object id = crud.insertRecord(context, schema, data);
    res.json(201, Map.of("id", id));
});

router.get("/api/apps/{appId}/{entity}", ...);
router.get("/api/apps/{appId}/{entity}/{id}", ...);
router.put("/api/apps/{appId}/{entity}/{id}", ...);
router.delete("/api/apps/{appId}/{entity}/{id}", ...);
```

### 3. Update EntityCrudService

**Add context parameter to all methods:**
```java
public Object insertRecord(
    TenantContext context,    // NEW
    EntitySchema schema,
    Map<String, Object> data
) throws SQLException {
    // Add tenant_id and app_id to data
    data.put("tenant_id", context.getTenantId());
    data.put("app_id", context.getAppId());
    
    // Rest of insert logic...
}
```

### 4. Update SchemaManager

**Link schemas to apps:**
```java
public class SchemaManager {
    // Current: loadSchema(entityName)
    // NEW: loadSchemaForApp(appId, entityName)
    
    public static EntitySchema loadSchemaForApp(String appId, String entityName) {
        // Load schema and verify it belongs to this app
        // Schema file: schemas/{appId}_{entityName}.json
        // OR: Query app_schemas table WHERE app_id = ?
    }
}
```

### 5. Update Frontend EntityManager.ts

**Fix Magic Seed Data save:**
```typescript
async handleMagicSeed() {
    // 1. Get current app context
    const appId = appStore.currentApp?.id || 'default';
    
    // 2. Generate seed data (this already works)
    const response = await fetch('/api/ai/seed-data', {
        method: 'POST',
        body: JSON.stringify({
            entityName: this.selectedEntity.name,
            schema: this.selectedEntity,
            count: 10
        })
    });
    
    const generatedData = await response.json();
    
    // 3. Save with app context (FIX THIS LINE)
    const saveResponse = await fetch(
        `/api/apps/${appId}/${this.selectedEntity.name}`,  // NEW: App-scoped
        {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(generatedData)
        }
    );
    
    if (saveResponse.ok) {
        this.showSuccess('Seed data saved successfully!');
    } else {
        const error = await saveResponse.json();
        this.showError(`Failed to save: ${error.message}`);
    }
}
```

---

## 📋 Implementation Checklist

### Backend Changes
- [x] 1. Create TenantContext.java
- [ ] 2. Update EntityCrudService to accept TenantContext
- [ ] 3. Add app-scoped routes to GenericEntityRoutes
- [ ] 4. Update SchemaManager.loadSchemaForApp()
- [ ] 5. Add tenant_id/app_id columns to entity tables (migration)
- [ ] 6. Update all CRUD queries to filter by tenant_id/app_id

### Frontend Changes
- [ ] 7. Update EntityManager.ts Magic Seed save logic
- [ ] 8. Update all entity API calls to use `/api/apps/{appId}/{entity}`
- [ ] 9. Store current appId in AppStore context
- [ ] 10. Update EntityExplorer.ts to use app-scoped APIs

### Testing
- [ ] 11. Test Magic Seed Data end-to-end
- [ ] 12. Test entity CRUD with multiple apps
- [ ] 13. Verify data isolation between apps

---

## 🚀 Quick Start (Do This NOW)

### Step 1: Update EntityCrudService (5 minutes)

```bash
# Edit this file:
app-bana-service/src/main/java/com/appbana/service/EntityCrudService.java
```

**Change signature of insertRecord:**
```java
// OLD
public Object insertRecord(EntitySchema schema, Map<String, Object> data)

// NEW
public Object insertRecord(TenantContext context, EntitySchema schema, Map<String, Object> data)
```

**Add context fields to data:**
```java
// At start of insertRecord method
data.put("tenant_id", context.getTenantId());
data.put("app_id", context.getAppId());
```

### Step 2: Add App-Scoped Routes (10 minutes)

Edit: `GenericEntityRoutes.java`

Add after existing routes:
```java
// POST /api/apps/{appId}/{entity}
router.post("/api/apps/{appId}/{entity}", (req, res) -> {
    String appId = req.pathParam("appId");
    String entity = req.pathParam("entity");
    TenantContext context = TenantContext.forApp(appId);
    EntitySchema schema = SchemaManager.loadSchema(entity);
    if (schema == null) {
        res.json(404, Map.of("error", "Entity not found"));
        return;
    }
    Map<String, Object> data = req.readJson(new TypeReference<>() {});
    Object id = crud.insertRecord(context, schema, data);
    res.json(201, Map.of("id", id));
});
```

### Step 3: Update Frontend (5 minutes)

Edit: `app-bana-ui/src/builder/components/EntityManager.ts`

Find `handleMagicSeed()` method and update save URL:
```typescript
// OLD
const saveUrl = `/api/${this.selectedEntity.name}`;

// NEW
const appId = appStore.currentApp?.id || 'default';
const saveUrl = `/api/apps/${appId}/${this.selectedEntity.name}`;
```

### Step 4: Rebuild & Test (2 minutes)

```bash
# Restart backend
./restart-backend.sh

# Test Magic Seed Data again
```

---

## ⏭️ Next Steps (After Immediate Fix)

1. **Add Database Migration** for tenant_id/app_id columns
2. **Update all CRUD operations** to include WHERE tenant_id/app_id
3. **Add proper app validation** (verify user has access to app)
4. **Update SchemaManager** to store app association
5. **Add multi-tenant support** when needed (Phase 2)

---

## 🎯 Success Criteria

After implementation:
- ✅ Magic Seed Data generates data
- ✅ Magic Seed Data saves successfully to `/api/apps/{appId}/user`
- ✅ Data is scoped to specific app
- ✅ Multiple apps can have entities with same name
- ✅ No data leakage between apps

---

**Priority**: 🔴 HIGH (Blocker)  
**Estimated Time**: 30-45 minutes total  
**Impact**: Unblocks entire Magic Seed Data feature  

**Start with Step 1-3 above to get immediate fix!**
