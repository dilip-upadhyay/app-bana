# Quick Start Implementation Guide
## How to Fix Magic Seed Data & Begin Multi-Tenant Migration

**Date**: December 31, 2025  
**Priority**: HIGH  
**Estimated Time**: Phase 1 = 2 weeks

---

## 🎯 Immediate Goal

**Fix Magic Seed Data** so generated records save correctly to the right app.

---

## 📋 Phase 1: Foundation (Week 1-2)

### Day 1: Create TenantContext

**File**: `app-bana-service/src/main/java/com/appbana/model/TenantContext.java`

```java
package com.appbana.model;

public class TenantContext {
    private final String tenantId;
    private final String appId;
    private final String userId;
    private final String requestId;
    
    private static final ThreadLocal<TenantContext> CONTEXT = new ThreadLocal<>();
    
    public TenantContext(String tenantId, String appId) {
        this(tenantId, appId, null, null);
    }
    
    public TenantContext(String tenantId, String appId, String userId, String requestId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (appId == null || appId.isBlank()) {
            throw new IllegalArgumentException("appId is required");
        }
        this.tenantId = tenantId;
        this.appId = appId;
        this.userId = userId;
        this.requestId = requestId;
    }
    
    // Getters
    public String getTenantId() { return tenantId; }
    public String getAppId() { return appId; }
    public String getUserId() { return userId; }
    public String getRequestId() { return requestId; }
    
    // Thread-local management
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
    
    public static TenantContext getOrNull() {
        return CONTEXT.get();
    }
    
    public static void clear() {
        CONTEXT.remove();
    }
    
    @Override
    public String toString() {
        return String.format("TenantContext{tenant=%s, app=%s, user=%s}", 
            tenantId, appId, userId);
    }
}
```

**Test**: `TenantContextTest.java`

```java
package com.appbana.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TenantContextTest {
    
    @Test
    void testCreateContext() {
        TenantContext ctx = new TenantContext("acme-corp", "hr-app");
        assertEquals("acme-corp", ctx.getTenantId());
        assertEquals("hr-app", ctx.getAppId());
    }
    
    @Test
    void testRequiredFields() {
        assertThrows(IllegalArgumentException.class, () -> 
            new TenantContext(null, "hr-app"));
        assertThrows(IllegalArgumentException.class, () -> 
            new TenantContext("acme-corp", null));
    }
    
    @Test
    void testThreadLocal() {
        TenantContext ctx = new TenantContext("acme-corp", "hr-app");
        TenantContext.set(ctx);
        
        TenantContext retrieved = TenantContext.get();
        assertEquals("acme-corp", retrieved.getTenantId());
        
        TenantContext.clear();
        assertThrows(IllegalStateException.class, () -> TenantContext.get());
    }
}
```

---

### Day 2: Update EntityCrudService

**File**: `app-bana-service/src/main/java/com/appbana/service/EntityCrudService.java`

**Add method signature overloads**:

```java
// NEW: Accept TenantContext
public Map<String, Object> insertRecord(
    TenantContext context, 
    EntitySchema schema, 
    Map<String, Object> data
) throws Exception {
    // Auto-inject tenant_id and app_id
    data.put("tenant_id", context.getTenantId());
    data.put("app_id", context.getAppId());
    data.put("created_at", Instant.now());
    if (context.getUserId() != null) {
        data.put("created_by", context.getUserId());
    }
    
    return insertRecord(schema, data); // Call existing method
}

// NEW: List with context
public List<Map<String, Object>> listAll(
    TenantContext context,
    EntitySchema schema,
    QueryParams params
) throws Exception {
    // Build SQL with WHERE clause
    String sql = buildSelectQuery(schema, params);
    sql += " WHERE tenant_id = ? AND app_id = ?";
    
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
        pstmt.setString(1, context.getTenantId());
        pstmt.setString(2, context.getAppId());
        
        ResultSet rs = pstmt.executeQuery();
        return toList(rs);
    }
}

// Helper: Build table name
private String getTableName(TenantContext context, EntitySchema schema) {
    return String.format("%s_%s_%s", 
        schema.getName(),
        context.getTenantId().replaceAll("[^a-zA-Z0-9]", "_"),
        context.getAppId().replaceAll("[^a-zA-Z0-9]", "_")
    );
}
```

---

### Day 3: Create Studio Entity Routes

**File**: `app-bana-service/src/main/java/com/appbana/api/StudioEntityRoutes.java`

```java
package com.appbana.api;

import com.appbana.model.TenantContext;
import com.appbana.service.EntityCrudService;
import com.appbana.service.SchemaManager;

public class StudioEntityRoutes {
    
    private final EntityCrudService entityCrudService;
    private final SchemaManager schemaManager;
    
    public void register(Router router) {
        
        // Magic Seed Data endpoint (FIX!)
        router.post("/studio/apps/:appId/entities/:entity/seed", (req, res) -> {
            try {
                String appId = req.param("appId");
                String entityName = req.param("entity");
                
                // Extract tenant from JWT
                String tenantId = extractTenantFromJWT(req);
                
                // Validate builder has access
                if (!hasAccessToApp(tenantId, appId)) {
                    res.status(403).send("Access denied");
                    return;
                }
                
                // Parse request
                Map<String, Object> body = req.bodyAsJson();
                List<Map<String, Object>> data = (List) body.get("data");
                
                // Create context
                TenantContext context = new TenantContext(tenantId, appId);
                
                // Load schema
                EntitySchema schema = schemaManager.loadSchemaForApp(
                    context, entityName);
                if (schema == null) {
                    res.status(404).send("Entity not found");
                    return;
                }
                
                // Insert records with context
                List<Map<String, Object>> inserted = new ArrayList<>();
                for (Map<String, Object> record : data) {
                    Map<String, Object> result = entityCrudService.insertRecord(
                        context, schema, record);
                    inserted.add(result);
                }
                
                res.json(Map.of(
                    "success", true,
                    "count", inserted.size(),
                    "data", inserted
                ));
                
            } catch (Exception e) {
                LOG.error("Seed data failed", e);
                res.status(500).json(Map.of("error", e.getMessage()));
            }
        });
        
        // Get entity data (for EntityManager preview)
        router.get("/studio/apps/:appId/entities/:entity", (req, res) -> {
            try {
                String appId = req.param("appId");
                String entityName = req.param("entity");
                String tenantId = extractTenantFromJWT(req);
                
                TenantContext context = new TenantContext(tenantId, appId);
                EntitySchema schema = schemaManager.loadSchemaForApp(
                    context, entityName);
                
                List<Map<String, Object>> rows = entityCrudService.listAll(
                    context, schema, QueryParams.parse(req));
                
                res.json(Map.of("data", rows));
                
            } catch (Exception e) {
                LOG.error("List entities failed", e);
                res.status(500).json(Map.of("error", e.getMessage()));
            }
        });
    }
    
    private String extractTenantFromJWT(Request req) {
        // Extract from JWT claims (implementation depends on your JWT library)
        String token = req.header("Authorization").replace("Bearer ", "");
        // Parse JWT and get custom claim "tenant_id"
        return "default"; // For now, return default
    }
    
    private boolean hasAccessToApp(String tenantId, String appId) {
        // Check if builder user has access to this app
        // Query appbana_apps WHERE tenant_id = ? AND id = ?
        return true; // For now, allow all
    }
}
```

---

### Day 4: Update Frontend EntityManager

**File**: `app-bana-ui/src/builder/components/EntityManager.ts`

```typescript
async handleMagicSeed() {
  try {
    this.setLoading(true);
    
    // 1. Get context
    const tenantId = tenantStore.currentTenant?.id || 'default';
    const appId = appStore.currentApp?.id;
    
    if (!appId) {
      this.showError('No app selected. Please select an app first.');
      return;
    }
    
    if (!this.selectedEntity) {
      this.showError('No entity selected');
      return;
    }
    
    // 2. Generate seed data via AI
    this.showInfo('Generating seed data with AI...');
    
    const generateResponse = await apiClient.post('/studio/ai/seed-data', {
      entityName: this.selectedEntity.name,
      schema: this.selectedEntity,
      count: 10
    });
    
    if (!generateResponse.ok) {
      throw new Error(generateResponse.error || 'Failed to generate data');
    }
    
    const generatedData = generateResponse.data;
    
    // 3. Save with proper context (FIX!)
    this.showInfo(`Saving ${generatedData.length} records...`);
    
    const saveResponse = await apiClient.post(
      `/studio/apps/${appId}/entities/${this.selectedEntity.name}/seed`,
      {
        tenantId,
        appId,
        data: generatedData
      }
    );
    
    if (saveResponse.ok) {
      this.showSuccess(
        `✅ Successfully generated and saved ${generatedData.length} records!`
      );
      
      // Refresh entity data
      await this.loadEntityData();
    } else {
      throw new Error(saveResponse.error || 'Failed to save data');
    }
    
  } catch (error: any) {
    console.error('Magic Seed Data failed:', error);
    this.showError(`Failed: ${error.message}`);
  } finally {
    this.setLoading(false);
  }
}
```

---

### Day 5: Database Migration

**File**: `app-bana-service/src/main/resources/db/migration/V10__tenant_app_isolation.sql`

```sql
-- Add tenant_id and app_id columns to all entity tables
-- This migration handles existing tables gracefully

-- Function to add columns to a table if they don't exist
DO $$
DECLARE
    r RECORD;
BEGIN
    -- Find all tables except system tables
    FOR r IN 
        SELECT tablename 
        FROM pg_tables 
        WHERE schemaname = 'public' 
        AND tablename NOT LIKE 'appbana_%'
        AND tablename NOT LIKE 'flyway_%'
    LOOP
        -- Add tenant_id if not exists
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_name = r.tablename 
            AND column_name = 'tenant_id'
        ) THEN
            EXECUTE format('ALTER TABLE %I ADD COLUMN tenant_id VARCHAR(50) DEFAULT ''default''', r.tablename);
        END IF;
        
        -- Add app_id if not exists
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_name = r.tablename 
            AND column_name = 'app_id'
        ) THEN
            EXECUTE format('ALTER TABLE %I ADD COLUMN app_id VARCHAR(100) DEFAULT ''legacy''', r.tablename);
        END IF;
        
        -- Create index if not exists
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_tenant_app_%I ON %I(tenant_id, app_id)', r.tablename, r.tablename);
        
        RAISE NOTICE 'Updated table: %', r.tablename;
    END LOOP;
END $$;

-- Make columns NOT NULL after backfill
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN 
        SELECT tablename 
        FROM pg_tables 
        WHERE schemaname = 'public' 
        AND tablename NOT LIKE 'appbana_%'
        AND tablename NOT LIKE 'flyway_%'
    LOOP
        EXECUTE format('ALTER TABLE %I ALTER COLUMN tenant_id SET NOT NULL', r.tablename);
        EXECUTE format('ALTER TABLE %I ALTER COLUMN app_id SET NOT NULL', r.tablename);
    END LOOP;
END $$;

-- Update appbana_schemas to link schemas to apps
ALTER TABLE appbana_schemas 
ADD COLUMN IF NOT EXISTS app_id VARCHAR(100),
ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50) DEFAULT 'default';

CREATE INDEX IF NOT EXISTS idx_schemas_tenant_app 
ON appbana_schemas(tenant_id, app_id);

COMMENT ON COLUMN appbana_schemas.app_id IS 'Links schema to specific app';
COMMENT ON COLUMN appbana_schemas.tenant_id IS 'Multi-tenant isolation';
```

---

### Day 6: Register Routes

**File**: `app-bana-service/src/main/java/com/appbana/ApiServer.java`

```java
// In buildRouter() method

// Add Studio Entity Routes (NEW)
StudioEntityRoutes studioEntityRoutes = new StudioEntityRoutes(
    entityCrudService, schemaManager);
studioEntityRoutes.register(router);

LOG.info("✅ Studio entity routes registered");
```

---

### Day 7: Test End-to-End

**Test Script**: `test-magic-seed.sh`

```bash
#!/bin/bash

echo "Testing Magic Seed Data with Multi-Tenant Context"

# 1. Login as builder (get JWT)
echo "1. Logging in..."
LOGIN_RESPONSE=$(curl -s -X POST http://localhost:8080/studio/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"dev@acme.com","password":"password"}')

TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.token')
echo "✅ Got JWT: ${TOKEN:0:20}..."

# 2. Create app
echo "2. Creating app..."
APP_RESPONSE=$(curl -s -X POST http://localhost:8080/studio/apps \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "id": "test-app",
    "name": "Test App",
    "description": "Testing magic seed"
  }')

APP_ID=$(echo $APP_RESPONSE | jq -r '.id')
echo "✅ Created app: $APP_ID"

# 3. Create entity schema
echo "3. Creating entity schema..."
SCHEMA_RESPONSE=$(curl -s -X POST "http://localhost:8080/studio/apps/$APP_ID/schemas" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "name": "employee",
    "fields": [
      {"name": "name", "type": "string", "required": true},
      {"name": "email", "type": "string", "required": true},
      {"name": "department", "type": "string"}
    ]
  }')

echo "✅ Created schema"

# 4. Generate seed data (AI)
echo "4. Generating seed data..."
SEED_DATA_RESPONSE=$(curl -s -X POST http://localhost:8080/studio/ai/seed-data \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "entityName": "employee",
    "count": 5
  }')

SEED_DATA=$(echo $SEED_DATA_RESPONSE | jq -r '.data')
echo "✅ Generated 5 records"

# 5. Save seed data with context
echo "5. Saving seed data with app context..."
SAVE_RESPONSE=$(curl -s -X POST "http://localhost:8080/studio/apps/$APP_ID/entities/employee/seed" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d "{\"data\": $SEED_DATA}")

COUNT=$(echo $SAVE_RESPONSE | jq -r '.count')

if [ "$COUNT" == "5" ]; then
  echo "✅ SUCCESS! Saved 5 records"
else
  echo "❌ FAILED! Expected 5, got $COUNT"
  echo "Response: $SAVE_RESPONSE"
  exit 1
fi

# 6. Verify data saved correctly
echo "6. Verifying data..."
VERIFY_RESPONSE=$(curl -s -X GET "http://localhost:8080/studio/apps/$APP_ID/entities/employee" \
  -H "Authorization: Bearer $TOKEN")

ACTUAL_COUNT=$(echo $VERIFY_RESPONSE | jq -r '.data | length')

if [ "$ACTUAL_COUNT" == "5" ]; then
  echo "✅ VERIFIED! Found 5 records in database"
  echo ""
  echo "🎉 MAGIC SEED DATA IS NOW WORKING!"
else
  echo "❌ VERIFICATION FAILED! Expected 5, found $ACTUAL_COUNT"
  exit 1
fi
```

**Run**:
```bash
chmod +x test-magic-seed.sh
./test-magic-seed.sh
```

---

## ✅ Success Criteria

After Phase 1, you should be able to:

1. ✅ Create an app in Studio
2. ✅ Define an entity schema
3. ✅ Click "Magic Seed Data"
4. ✅ See "✅ Successfully generated and saved 10 records!"
5. ✅ Verify records appear in entity data table
6. ✅ Records have correct `tenant_id` and `app_id`
7. ✅ No 404 errors
8. ✅ No cross-app data leakage

---

## 🚀 Next Steps

After Phase 1 works:

1. **Phase 2** (Week 3-4): Runtime authentication
2. **Phase 3** (Week 5-6): Multi-tenant (multiple orgs)
3. **Phase 4** (Week 7-8): Production features

---

## 📞 Troubleshooting

**Problem**: Still getting 404 on seed endpoint

```bash
# Check routes registered
curl http://localhost:8080/debug/routes | grep seed

# Check backend logs
tail -f backend.log | grep "seed"
```

**Problem**: Data saved but can't retrieve

```bash
# Check database
psql appbana -c "SELECT table_name FROM information_schema.tables WHERE table_name LIKE '%employee%';"

# Check data
psql appbana -c "SELECT * FROM employee_default_test_app;"
```

**Problem**: JWT doesn't have tenant_id claim

```bash
# Decode JWT
echo $TOKEN | cut -d'.' -f2 | base64 -d | jq
```

---

**Document Status**: READY TO IMPLEMENT  
**Estimated Time**: 7 days  
**Priority**: HIGH (Blocks Magic Seed Data)

**Start Now**: Begin with Day 1 (TenantContext.java)
