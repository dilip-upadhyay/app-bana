> **📚 HISTORICAL BACKLOG (Dec 2025).** Canvas-era multi-tenant migration stories. Superseded by the four active plans:
> - [`AI_NATIVE_UI_REBUILD_PLAN.md`](./AI_NATIVE_UI_REBUILD_PLAN.md)
> - [`RUNTIME_UX_OVERHAUL_PLAN.md`](./RUNTIME_UX_OVERHAUL_PLAN.md)
> - [`COMPLEX_UI_PLAN.md`](./COMPLEX_UI_PLAN.md)
> - [`MAKER_CHECKER_PLAN.md`](./MAKER_CHECKER_PLAN.md)
>
> **For live status, see:** [`docs/ACTIVE_TASKS.md`](../ACTIVE_TASKS.md).
> **See:** [`docs/README.md`](../README.md) for the full documentation currency table.

---

# Multi-Tenant Architecture - Implementation Stories

**Project**: AppBana Multi-Tenant Migration  
**Branch**: multitenent-feature  
**Start Date**: December 31, 2025  
**Target Completion**: February 28, 2026 (8 weeks)

---

## 📋 Epic Overview

**Epic 1**: Foundation & Magic Seed Data Fix (Week 1-2) - CRITICAL  
**Epic 2**: Runtime Authentication (Week 3-4)  
**Epic 3**: Multi-Tenant Platform (Week 5-6)  
**Epic 4**: Production Ready (Week 7-8)

---

## 🔥 EPIC 1: Foundation & Magic Seed Data Fix

**Goal**: Enable basic tenant/app isolation and fix Magic Seed Data  
**Priority**: CRITICAL  
**Timeline**: Week 1-2 (10 working days)  
**Acceptance**: Magic Seed Data works, entities are app-scoped

---

### Story 1.1: Create TenantContext Domain Model

**As a** developer  
**I want** a TenantContext class to hold tenant/app/user context  
**So that** all operations can be properly scoped

**Tasks**:
- [ ] 1.1.1: Create `TenantContext.java` class with required fields
- [ ] 1.1.2: Add thread-local storage for context propagation
- [ ] 1.1.3: Add validation (tenant_id and app_id required)
- [ ] 1.1.4: Create `TenantContextTest.java` with unit tests
- [ ] 1.1.5: Test thread-local set/get/clear operations

**Files to Create**:
- `app-bana-service/src/main/java/com/appbana/model/TenantContext.java`
- `app-bana-service/src/test/java/com/appbana/model/TenantContextTest.java`

**Acceptance Criteria**:
- ✅ TenantContext validates required fields
- ✅ Thread-local storage works correctly
- ✅ All unit tests pass (5+ tests)
- ✅ No memory leaks (clear() works)

**Estimate**: 0.5 days  
**Dependencies**: None

---

### Story 1.2: Update EntityCrudService for Context-Aware Operations

**As a** developer  
**I want** EntityCrudService to accept TenantContext  
**So that** all CRUD operations are tenant/app scoped

**Tasks**:
- [ ] 1.2.1: Add `insertRecord(TenantContext, schema, data)` method
- [ ] 1.2.2: Auto-inject `tenant_id` and `app_id` into data
- [ ] 1.2.3: Add `listAll(TenantContext, schema, params)` method
- [ ] 1.2.4: Add WHERE clause filtering by tenant/app
- [ ] 1.2.5: Add `getById(TenantContext, schema, id)` method
- [ ] 1.2.6: Add `updateById(TenantContext, schema, id, data)` method
- [ ] 1.2.7: Add `deleteById(TenantContext, schema, id)` method
- [ ] 1.2.8: Create helper method `getTableName(context, schema)`
- [ ] 1.2.9: Update unit tests for all methods
- [ ] 1.2.10: Keep backward-compatible overloads (without context)

**Files to Modify**:
- `app-bana-service/src/main/java/com/appbana/service/EntityCrudService.java`
- `app-bana-service/src/test/java/com/appbana/service/EntityCrudServiceTest.java`

**Acceptance Criteria**:
- ✅ All CRUD methods accept TenantContext
- ✅ tenant_id/app_id auto-injected in INSERT
- ✅ WHERE clauses include tenant_id/app_id in SELECT/UPDATE/DELETE
- ✅ Backward compatibility maintained
- ✅ All unit tests pass (10+ tests)

**Estimate**: 1 day  
**Dependencies**: Story 1.1 (TenantContext)

---

### Story 1.3: Database Migration for Tenant/App Columns

**As a** DBA  
**I want** tenant_id and app_id columns in all entity tables  
**So that** data isolation is enforced at database level

**Tasks**:
- [ ] 1.3.1: Create V10 migration script
- [ ] 1.3.2: Add tenant_id column with default 'default'
- [ ] 1.3.3: Add app_id column with default 'legacy'
- [ ] 1.3.4: Create composite index (tenant_id, app_id)
- [ ] 1.3.5: Make columns NOT NULL after backfill
- [ ] 1.3.6: Update appbana_schemas table structure
- [ ] 1.3.7: Test migration on local H2 database
- [ ] 1.3.8: Test migration rollback
- [ ] 1.3.9: Document migration in changelog

**Files to Create**:
- `app-bana-service/src/main/resources/db/migration/V10__tenant_app_isolation.sql`

**Acceptance Criteria**:
- ✅ Migration runs without errors
- ✅ All entity tables have tenant_id/app_id
- ✅ Indexes created successfully
- ✅ Existing data preserved
- ✅ Rollback works if needed

**Estimate**: 0.5 days  
**Dependencies**: None (can run in parallel)

---

### Story 1.4: Update SchemaManager for App-Scoped Schemas

**As a** developer  
**I want** schemas to be linked to specific apps  
**So that** each app has its own isolated schemas

**Tasks**:
- [ ] 1.4.1: Update `saveSchema()` to accept TenantContext
- [ ] 1.4.2: Store tenant_id/app_id in schema metadata
- [ ] 1.4.3: Update `loadSchema()` to filter by tenant/app
- [ ] 1.4.4: Update `loadSchemaForApp(context, entityName)` method
- [ ] 1.4.5: Update `listSchemas()` to filter by app
- [ ] 1.4.6: Update schema storage key generation
- [ ] 1.4.7: Add validation: schema must belong to app
- [ ] 1.4.8: Update unit tests
- [ ] 1.4.9: Test schema isolation between apps

**Files to Modify**:
- `app-bana-service/src/main/java/com/appbana/service/SchemaManager.java`
- `app-bana-service/src/test/java/com/appbana/service/SchemaManagerTest.java`

**Acceptance Criteria**:
- ✅ Schemas stored with tenant_id/app_id
- ✅ Queries filter by tenant/app
- ✅ No cross-app schema access
- ✅ All unit tests pass (8+ tests)

**Estimate**: 1 day  
**Dependencies**: Story 1.1 (TenantContext), Story 1.3 (Migration)

---

### Story 1.5: Create Studio Entity Routes (Magic Seed Data Fix)

**As a** builder user  
**I want** Studio entity routes with app context  
**So that** Magic Seed Data saves to the correct app

**Tasks**:
- [ ] 1.5.1: Create `StudioEntityRoutes.java` class
- [ ] 1.5.2: Add POST `/studio/apps/:appId/entities/:entity/seed` endpoint
- [ ] 1.5.3: Extract tenant_id from JWT claims
- [ ] 1.5.4: Extract app_id from URL parameter
- [ ] 1.5.5: Validate builder has access to app
- [ ] 1.5.6: Create TenantContext from extracted values
- [ ] 1.5.7: Call EntityCrudService with context
- [ ] 1.5.8: Add GET `/studio/apps/:appId/entities/:entity` endpoint
- [ ] 1.5.9: Add error handling and logging
- [ ] 1.5.10: Register routes in ApiServer
- [ ] 1.5.11: Create integration tests
- [ ] 1.5.12: Test with Postman/curl

**Files to Create**:
- `app-bana-service/src/main/java/com/appbana/api/StudioEntityRoutes.java`
- `app-bana-service/src/test/java/com/appbana/api/StudioEntityRoutesTest.java`

**Files to Modify**:
- `app-bana-service/src/main/java/com/appbana/ApiServer.java`

**Acceptance Criteria**:
- ✅ POST endpoint accepts seed data with context
- ✅ Data saved with correct tenant_id/app_id
- ✅ GET endpoint returns app-scoped data
- ✅ Proper error messages for validation failures
- ✅ Integration tests pass

**Estimate**: 1.5 days  
**Dependencies**: Story 1.2 (EntityCrudService), Story 1.4 (SchemaManager)

---

### Story 1.6: Update Frontend EntityManager Component

**As a** builder user  
**I want** EntityManager to use new Studio APIs  
**So that** Magic Seed Data works correctly

**Tasks**:
- [ ] 1.6.1: Update `handleMagicSeed()` method
- [ ] 1.6.2: Get current app_id from AppStore
- [ ] 1.6.3: Get tenant_id from TenantStore (or default)
- [ ] 1.6.4: Update API call to use new endpoint
- [ ] 1.6.5: Pass tenant_id and app_id in request body
- [ ] 1.6.6: Update error handling
- [ ] 1.6.7: Add loading states
- [ ] 1.6.8: Add success/error messages
- [ ] 1.6.9: Update `loadEntityData()` to use new GET endpoint
- [ ] 1.6.10: Add validation for missing app context
- [ ] 1.6.11: Update unit tests
- [ ] 1.6.12: Test in browser

**Files to Modify**:
- `app-bana-ui/src/builder/components/EntityManager.ts`
- `app-bana-ui/tests/EntityManager.test.ts`

**Acceptance Criteria**:
- ✅ Magic Seed Data button works
- ✅ Shows success message with count
- ✅ Data appears in entity table
- ✅ No 404 errors
- ✅ Proper error handling

**Estimate**: 1 day  
**Dependencies**: Story 1.5 (StudioEntityRoutes)

---

### Story 1.7: Create TenantStore for Frontend

**As a** developer  
**I want** TenantStore to manage tenant context in frontend  
**So that** all components can access current tenant

**Tasks**:
- [ ] 1.7.1: Create `TenantStore.ts` class
- [ ] 1.7.2: Add `currentTenant` state property
- [ ] 1.7.3: Add `loadTenants()` method
- [ ] 1.7.4: Add `switchTenant(tenantId)` method
- [ ] 1.7.5: Store tenant_id in localStorage
- [ ] 1.7.6: Integrate with AppStore
- [ ] 1.7.7: Add TypeScript interfaces
- [ ] 1.7.8: Add unit tests
- [ ] 1.7.9: Export singleton instance

**Files to Create**:
- `app-bana-ui/src/builder/store/TenantStore.ts`
- `app-bana-ui/src/models/tenant.ts`
- `app-bana-ui/tests/TenantStore.test.ts`

**Acceptance Criteria**:
- ✅ TenantStore manages current tenant
- ✅ Persists to localStorage
- ✅ All unit tests pass
- ✅ TypeScript compilation successful

**Estimate**: 0.5 days  
**Dependencies**: None (can run in parallel)

---

### Story 1.8: End-to-End Testing for Magic Seed Data

**As a** QA engineer  
**I want** comprehensive E2E tests for Magic Seed Data  
**So that** we verify complete functionality

**Tasks**:
- [ ] 1.8.1: Create test script `test-magic-seed.sh`
- [ ] 1.8.2: Test: Login as builder user
- [ ] 1.8.3: Test: Create app via API
- [ ] 1.8.4: Test: Create entity schema
- [ ] 1.8.5: Test: Generate seed data via AI
- [ ] 1.8.6: Test: Save seed data with app context
- [ ] 1.8.7: Test: Verify data in database
- [ ] 1.8.8: Test: Query data via GET endpoint
- [ ] 1.8.9: Test: Verify tenant_id/app_id in records
- [ ] 1.8.10: Test: No cross-app data leakage
- [ ] 1.8.11: Document test results
- [ ] 1.8.12: Run tests in CI/CD pipeline

**Files to Create**:
- `app-bana-service/scripts/test-magic-seed.sh`
- `docs/TESTING_RESULTS.md`

**Acceptance Criteria**:
- ✅ All test steps pass
- ✅ Data saved with correct context
- ✅ No errors in backend logs
- ✅ Test script documented

**Estimate**: 1 day  
**Dependencies**: Story 1.6 (Frontend complete)

---

### Story 1.9: Update Documentation

**As a** developer  
**I want** updated documentation for new architecture  
**So that** team understands the changes

**Tasks**:
- [ ] 1.9.1: Update README with new API endpoints
- [ ] 1.9.2: Document TenantContext usage
- [ ] 1.9.3: Update API documentation
- [ ] 1.9.4: Add migration guide
- [ ] 1.9.5: Update troubleshooting guide
- [ ] 1.9.6: Add architecture diagrams
- [ ] 1.9.7: Document testing procedures

**Files to Modify**:
- `README.md`
- `docs/01-ARCHITECTURE.md`
- `docs/02-DEVELOPMENT_GUIDE.md`

**Acceptance Criteria**:
- ✅ All docs updated
- ✅ API reference complete
- ✅ Clear examples provided

**Estimate**: 0.5 days  
**Dependencies**: Story 1.8 (Testing complete)

---

### Story 1.10: Epic 1 Demo & Retrospective

**As a** team  
**I want** to demo Epic 1 completion  
**So that** we can get feedback and plan improvements

**Tasks**:
- [ ] 1.10.1: Prepare demo environment
- [ ] 1.10.2: Demo: Create app in Studio
- [ ] 1.10.3: Demo: Define entity
- [ ] 1.10.4: Demo: Click Magic Seed Data
- [ ] 1.10.5: Demo: Show data in table
- [ ] 1.10.6: Demo: Show database records
- [ ] 1.10.7: Collect feedback
- [ ] 1.10.8: Document learnings
- [ ] 1.10.9: Plan Epic 2

**Acceptance Criteria**:
- ✅ Successful demo to stakeholders
- ✅ Feedback documented
- ✅ Epic 2 planned

**Estimate**: 0.5 days  
**Dependencies**: All previous stories

---

## ⏸️ EPIC 2: Runtime Authentication (Week 3-4)

**Goal**: Enable end users to login to deployed apps  
**Status**: PLANNED (Start after Epic 1)  
**Timeline**: 2 weeks

### Stories (High-Level)
- Story 2.1: Create Runtime User Tables
- Story 2.2: Implement Runtime Authentication Endpoints
- Story 2.3: Generate App-Specific JWTs
- Story 2.4: Create RuntimeShell with Login UI
- Story 2.5: Implement RuntimeAuthMiddleware
- Story 2.6: Update PermissionService for Runtime Roles
- Story 2.7: Testing & Documentation

---

## ⏸️ EPIC 3: Multi-Tenant Platform (Week 5-6)

**Goal**: Support multiple organizations using AppBana  
**Status**: PLANNED  
**Timeline**: 2 weeks

### Stories (High-Level)
- Story 3.1: Create Tenant Management Tables
- Story 3.2: Implement Tenant Administration
- Story 3.3: Create TenantSwitcher UI Component
- Story 3.4: Implement Subdomain Routing
- Story 3.5: Add Tenant-Scoped Billing
- Story 3.6: Testing & Documentation

---

## ⏸️ EPIC 4: Production Ready (Week 7-8)

**Goal**: Enterprise-ready features  
**Status**: PLANNED  
**Timeline**: 2 weeks

### Stories (High-Level)
- Story 4.1: Row-Level Security Implementation
- Story 4.2: Sharing Rules Between Users
- Story 4.3: Cross-App Workflows
- Story 4.4: Tenant Data Export (GDPR)
- Story 4.5: Performance Optimization
- Story 4.6: Comprehensive Audit Logging
- Story 4.7: Production Deployment

---

## 📊 Progress Tracking

**Epic 1 Progress**: 0/10 stories complete (0%)

### Current Sprint: Epic 1 - Week 1

**Week 1 Goals**:
- ✅ Story 1.1: TenantContext (Day 1)
- ✅ Story 1.2: EntityCrudService (Day 2)
- ✅ Story 1.3: Database Migration (Day 2)
- ✅ Story 1.4: SchemaManager (Day 3)
- ✅ Story 1.5: StudioEntityRoutes (Day 4-5)

**Week 2 Goals**:
- ✅ Story 1.6: Frontend EntityManager (Day 6)
- ✅ Story 1.7: TenantStore (Day 6)
- ✅ Story 1.8: E2E Testing (Day 7-8)
- ✅ Story 1.9: Documentation (Day 9)
- ✅ Story 1.10: Demo & Retro (Day 10)

---

## 🎯 Definition of Done

For each story to be considered complete:

- [ ] All tasks checked off
- [ ] Code written and reviewed
- [ ] Unit tests written and passing
- [ ] Integration tests passing (if applicable)
- [ ] Documentation updated
- [ ] Code committed to `multitenent-feature` branch
- [ ] Peer review completed
- [ ] No compiler warnings or errors
- [ ] Backend logs clean (no errors)
- [ ] Frontend console clean (no errors)

---

## 🚀 How to Use This Document

1. **Start with Story 1.1** - Pick up the first story
2. **Check off tasks** as you complete them
3. **Mark story complete** when all tasks done
4. **Move to next story** in sequence
5. **Update progress** in this document
6. **Demo at end** of each epic

---

## 📝 Story Template (For Future Stories)

```markdown
### Story X.X: [Title]

**As a** [role]  
**I want** [feature]  
**So that** [benefit]

**Tasks**:
- [ ] X.X.1: Task description
- [ ] X.X.2: Task description

**Files to Create/Modify**:
- `path/to/file.java`

**Acceptance Criteria**:
- ✅ Criterion 1
- ✅ Criterion 2

**Estimate**: X days  
**Dependencies**: Story X.X
```

---

**Document Status**: ACTIVE - READY TO START  
**Next Action**: Begin Story 1.1 (Create TenantContext)  
**Current Story**: Story 1.1 (Not Started)

---

**Ready to start? Let me know and I'll begin with Story 1.1!**
