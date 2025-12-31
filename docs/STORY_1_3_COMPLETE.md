# Story 1.3 Complete: Database Migration V10

**Date:** December 31, 2025  
**Branch:** multitenent-feature  
**Commit:** 6e25c31 (refactor version without backward compatibility)  
**Status:** ✅ Complete

---

## Summary

Implemented V10 Flyway migration to add tenant and app isolation columns to system tables. Per user feedback ("no existing app live"), removed all backward compatibility code for clean implementation.

## Changes Made

### 1. V10__tenant_app_isolation.sql

**Strategy:** 3-step migration to handle seed data from V6:
1. ADD COLUMN nullable
2. UPDATE existing rows with defaults
3. ALTER COLUMN SET NOT NULL

**Tables Modified:**
- `appbana_schemas`: Added tenant_id, app_id (NOT NULL)
- `appbana_wf_definition`: Added tenant_id, app_id with 3-step strategy
- `appbana_wf_instance`: Added tenant_id, app_id with 3-step strategy
- `appbana_wf_token`: Added tenant_id, app_id with 3-step strategy

**Indexes Created:**
- `idx_schema_tenant_app` ON appbana_schemas(tenant_id, app_id, name)
- `idx_wf_def_tenant_app` ON appbana_wf_definition(tenant_id, app_id)
- `idx_wf_inst_tenant_app` ON appbana_wf_instance(tenant_id, app_id)
- `idx_wf_token_tenant_app` ON appbana_wf_token(tenant_id, app_id)

### 2. SchemaManager.java

**Simplifications:**
- `createTable()`: Removed DEFAULT values from tenant_id/app_id columns
- `ensureTable()`: Removed 52 lines of backward compatibility logic
  - No longer checks for missing tenant_id/app_id on existing tables
  - No longer adds columns with ALTER TABLE
  - No longer creates indexes on existing tables
  - Only handles user-defined field evolution

**Rationale:** No existing apps means no legacy tables to migrate.

### 3. ApiServer.java

**Flyway Configuration:**
```java
Flyway flyway = Flyway.configure()
    .dataSource(cfg.getJdbcUrl(), cfg.getUsername(), cfg.getPassword())
    .locations("classpath:db/migration")
    .cleanDisabled(cfg.getFlywayCleanOnStart() == null || !cfg.getFlywayCleanOnStart())
    .baselineOnMigrate(true)   // Allow migrations on non-empty schemas
    .baselineVersion("0")      // Run all migrations including V1
    .load();
```

**Purpose:** Handles AppManager creating tables before Flyway runs.

### 4. V10MigrationTest.java

**Changed Test:**
- Removed: `testDefaultValuesAreApplied()` (tested INSERT without tenant_id succeeded)
- Added: `testTenantAndAppColumnsAreRequired()` (verifies NOT NULL constraint)

**New Test Logic:**
```java
assertThrows(SQLException.class, () -> {
    // INSERT without tenant_id/app_id should FAIL
    ps.setString(1, "test_schema");
    ps.setString(2, "{}");
    ps.executeUpdate();
}, "Insert without tenant_id/app_id should fail (NOT NULL constraint)");

// Verify INSERT with tenant_id/app_id succeeds
ps.setString(1, "test_schema");
ps.setString(2, "{}");
ps.setString(3, "tenant1");
ps.setString(4, "app1");
assertEquals(1, ps.executeUpdate());
```

## Test Results

### ✅ V10MigrationTest: 7/7 Passing

1. `testSchemasTableHasTenantAndAppColumns` ✅
2. `testWorkflowDefinitionTableHasTenantAndAppColumns` ✅
3. `testWorkflowInstanceTableHasTenantAndAppColumns` ✅
4. `testWorkflowTokenTableHasTenantAndAppColumns` ✅
5. `testIndexesWereCreated` ✅
6. `testTenantAndAppColumnsAreRequired` ✅ (NEW)
7. `testCompositeIndexOnSchemasTable` ✅

### ⏳ SchemaManagerTenantTest: 4/5 Passing

**Passing:**
1. `testCreateTableIncludesTenantAndApp` ✅
2. `testEnsureTableAddsTenantAndAppColumns` ✅
3. `testLoadSchemaByTenantAndApp` ✅
4. `testSaveSchemaStoresTenantAndAppIds` ✅

**Blocked on Story 1.4:**
5. `testSchemaKeyIncludesTenantAndApp` ⏳
   - Requires: `loadSchema(appId, name, tenantId)` method (Story 1.4)
   - Reason: Current method signature is `loadSchema(name)` only

### ✅ Backend Startup

```
✅ Backend started successfully!
📋 PID: 33955
🌐 URL: http://localhost:8080
```

### ✅ Flyway Migrations

```
[main] INFO com.appbana.ApiServer - Flyway migrations complete: 10 migrations applied
```

## Issues Resolved

### Issue #1: Flyway baselineOnMigrate Error

**Error:**
```
Found non-empty schema(s) "PUBLIC" but no schema history table.
Use baseline() or set baselineOnMigrate to true to initialize the schema history table.
```

**Root Cause:** AppManager creates tables before Flyway runs, leaving schema non-empty.

**Solution:** 
- Set `baselineOnMigrate(true)` to allow migrations on non-empty schemas
- Set `baselineVersion("0")` to ensure all migrations run (including V1)

### Issue #2: V1 Migration Skipped

**Error:**
```
Successfully baselined schema with version: 1
Migrating schema "PUBLIC" to version "2 - field level security"
Table "ROLE" not found
```

**Root Cause:** Default `baselineVersion=1` caused Flyway to skip V1.

**Solution:** Changed to `baselineVersion("0")` to run all migrations.

### Issue #3: Duplicate ALTER TABLE Statements

**Error:**
```
Syntax error in SQL statement "ALTER TABLE appbana_wf_definition ... "
```

**Root Cause:** Incomplete removal of old code left duplicate statements.

**Solution:** Removed entire old PART 3 section (lines 42-47 with DEFAULT values).

### Issue #4: NOT NULL Constraint on Existing Data

**Error:**
```
NULL not allowed for column "TENANT_ID"
INSERT INTO "PUBLIC"."APPBANA_WF_DEFINITION_COPY_6_4"
```

**Root Cause:** V6 migration seeds workflow data, but V10 tried to add NOT NULL columns without defaults.

**Solution:** 3-step migration strategy:
1. ADD COLUMN nullable
2. UPDATE existing rows: `UPDATE table SET tenant_id = 'default' WHERE tenant_id IS NULL`
3. ALTER COLUMN SET NOT NULL

## Lessons Learned

1. **Migration Order Matters:** V6 seed data affected V10 migration strategy
2. **3-Step Strategy for NOT NULL:** Always populate existing rows before adding constraint
3. **Flyway Configuration:** baselineOnMigrate + baselineVersion="0" handles AppManager init order
4. **Test First:** V10MigrationTest caught constraint violations before manual testing

## Next Steps (Story 1.4)

**Task:** Update SchemaManager load/save methods to accept TenantContext

**Files to modify:**
1. `SchemaManager.java`
   - `saveSchema(EntitySchema)` → `saveSchema(TenantContext, EntitySchema)`
   - `loadSchema(String name)` → `loadSchema(TenantContext, String name)`
   - `listSchemas()` → `listSchemas(TenantContext)`
   
2. **Estimated Effort:** 0.5 days
3. **Tests:** Fix SchemaManagerTenantTest.testSchemaKeyIncludesTenantAndApp

## Git History

```bash
# Current branch
git log --oneline multitenent-feature -3

# Output:
6e25c31 refactor(Story 1.3): Remove backward compatibility from V10 migration
225965e feat(Story 1.3): Add database migration V10 for tenant/app isolation
8a4c2f2 feat(Story 1.2): Add TenantContext-aware EntityCrudService methods
c7b4a1f feat(Story 1.1): Add TenantContext for multi-tenant data isolation
```

## Verification Commands

```bash
# Check backend status
curl http://localhost:8080/health

# Verify schemas table structure
psql -h localhost -p 8080 -c "SELECT * FROM information_schema.columns WHERE table_name = 'appbana_schemas'"

# Verify workflow tables
psql -h localhost -p 8080 -c "SELECT * FROM information_schema.columns WHERE table_name = 'appbana_wf_definition'"

# Test NOT NULL constraint
curl -X POST http://localhost:8080/api/schemas \
  -H 'Content-Type: application/json' \
  -d '{"name":"test"}' 
# Expected: 500 error (missing tenant_id/app_id)
```

---

**Story 1.3 Status:** ✅ COMPLETE  
**Overall Progress:** 3/10 stories complete (30%)  
**Next:** Story 1.4 - SchemaManager context-aware methods
