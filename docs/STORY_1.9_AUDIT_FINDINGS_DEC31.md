# Story 1.9 - Integration Testing & Final Audit - Findings

**Date**: December 31, 2025  
**Story**: 1.9 - Integration Testing  
**Status**: CRITICAL ISSUES FOUND  
**Engineer**: AI Senior Engineer

---

## Executive Summary

During Story 1.9 integration testing audit, discovered **CRITICAL ARCHITECTURAL MISMATCH** between frontend runtime components and backend multi-tenant routes.

### Critical Issue

**Frontend runtime components** (FormContainer, api-client entity functions) are calling **old global entity routes** (`/api/{entity}`), but backend **only provides app-scoped routes** (`/appbana-studio/{tenantId}/apps/{appId}/{entity}`).

**Impact**: Runtime forms and entity CRUD will **fail with 404 errors** because routes don't exist.

---

## Detailed Findings

### 1. Backend Routes (GenericEntityRoutes.java) ✅

**Status**: CORRECT - All 5 entity CRUD routes are properly app-scoped

```java
// Story 1.5 - App-scoped entity routes (CORRECT)
POST   /appbana-studio/{tenantId}/apps/{appId}/{entity}        // Create
GET    /appbana-studio/{tenantId}/apps/{appId}/{entity}        // List
GET    /appbana-studio/{tenantId}/apps/{appId}/{entity}/{id}   // Get by ID
PUT    /appbana-studio/{tenantId}/apps/{appId}/{entity}/{id}   // Update
DELETE /appbana-studio/{tenantId}/apps/{appId}/{entity}/{id}   // Delete
```

**Key Features**:
- All routes extract tenantId and appId from path
- Validate both parameters (not null/blank)
- Set TenantContext with both IDs
- Scope all queries to tenant + app

---

### 2. Frontend Studio (AppStore.ts) ✅

**Status**: CORRECT - All 13 API calls properly use tenant-scoped routes

```typescript
// Example: AppStore.ts uses correct tenant-scoped routes
const tenantId = 'default';
await apiClient.post(`/appbana-studio/${tenantId}/apps`, appData);
```

**No issues found** in Studio builder code.

---

### 3. Frontend Runtime Components ❌ CRITICAL

**Status**: BROKEN - Using old global routes that don't exist

#### 3.1 FormContainer.ts (Lines 216, 382, 385)

**Problem**: Calls old global entity routes
```typescript
// WRONG - These routes don't exist on backend!
const data = await apiClient.get<any>(`/api/${entity}/${id}`);                  // Load record
await apiClient.put(`/api/${entity}/${recordId}`, formData, { headers });       // Update
await apiClient.post(`/api/${entity}`, formData, { headers });                  // Create
```

**Should be** (app-scoped):
```typescript
// CORRECT - App-scoped routes
const { tenantId, appId } = this.getRuntimeContext();
const data = await apiClient.get<any>(`/appbana-studio/${tenantId}/apps/${appId}/${entity}/${id}`);
await apiClient.put(`/appbana-studio/${tenantId}/apps/${appId}/${entity}/${recordId}`, formData, { headers });
await apiClient.post(`/appbana-studio/${tenantId}/apps/${appId}/${entity}`, formData, { headers });
```

**Impact**: ALL runtime forms will fail with 404 errors

---

#### 3.2 api-client.ts Entity Functions (Lines 337, 343, 349, 355, 361)

**Problem**: All entity CRUD helper functions use global routes
```typescript
// WRONG - These helper functions don't work!
export async function listEntities(entity: string, params?: any) {
  const base = (globalThis.location?.port === '5173') ? 'http://localhost:8080' : '';
  return apiClient.get(`${base}/api/${entity}`, params);  // ❌ 404 Error
}

export async function bulkDelete(entity: string, ids: (string | number)[]) {
  return apiClient.post(`${base}/api/${entity}/bulk-delete`, { ids });  // ❌ 404 Error
}

export async function bulkExport(entity: string, ids: (string | number)[]) {
  return apiClient.post(`${base}/api/${entity}/bulk-export`, { ids });  // ❌ 404 Error
}

export async function createRow(entity: string, data: Record<string, any>) {
  return apiClient.post(`${base}/api/${entity}`, data);  // ❌ 404 Error
}

export async function updateRow(entity: string, id: string | number, data: Record<string, any>) {
  return apiClient.put(`${base}/api/${entity}/${id}`, data);  // ❌ 404 Error
}
```

**Should be** (app-scoped):
```typescript
// CORRECT - App-scoped entity functions
export async function listEntities(tenantId: string, appId: string, entity: string, params?: any) {
  const base = (globalThis.location?.port === '5173') ? 'http://localhost:8080' : '';
  return apiClient.get(`${base}/appbana-studio/${tenantId}/apps/${appId}/${entity}`, params);
}
// ... etc for all functions
```

**Impact**: ALL entity operations from runtime will fail with 404 errors

---

### 4. Files Confirmed OK ✅

These files use **system-level APIs** that don't need tenant isolation:

| File | API Endpoint | Scope | Status |
|------|--------------|-------|--------|
| ThemeEditor.ts | `/api/ai/theme-generate` | AI system | ✅ OK |
| EntityManager.ts | `/api/ai/seed-data` | AI system | ✅ OK |
| TemplateStore.ts | `/api/templates` | System templates | ✅ OK |
| AppRuntimeShell.ts | `/api/ai/theme-generate` | AI system | ✅ OK |
| AuthGuard.ts | `/api/auth/profile` | User auth | ✅ OK |
| AiChatBuilder.ts | `/api/ai/*` (5 calls) | AI system | ✅ OK |
| api-examples.ts | `/api/users` | Example code | ⚠️ Example only |

**Note**: api-examples.ts is example/documentation code, not used in production runtime.

---

## Architecture Problem Analysis

### Current Broken Flow

```
User fills form in runtime
    ↓
FormContainer submits to /api/{entity}
    ↓
❌ Backend: No such route! (404 error)
    ↓
Form submission fails
```

### Required Flow (Correct)

```
User fills form in runtime
    ↓
Runtime Shell provides: { tenantId: 'xyz', appId: 'app123' }
    ↓
FormContainer submits to /appbana-studio/{tenantId}/apps/{appId}/{entity}
    ↓
✅ Backend: Route exists, validates tenant/app context
    ↓
Data saved successfully
```

---

## Root Cause Analysis

### Why This Happened

1. **Phase 1 (Stories 1.1-1.4)**: Backend infrastructure created (TenantContext, EntityCrudService, migrations)
2. **Phase 2 (Stories 1.5-1.7)**: Backend routes updated to use tenant/app isolation
3. **Phase 3 (Story 1.8)**: Frontend **Studio** routes updated for tenant isolation
4. **❌ MISSED**: Frontend **Runtime** components not updated to match backend architecture

### Design Decision Needed

**Question**: Should runtime components use:
- **Option A**: Studio builder routes (`/appbana-studio/...`) - Same as design-time
- **Option B**: Separate runtime routes (`/api/{tenantId}/apps/{appId}/...`) - Different endpoint
- **Option C**: Environment-aware routes (`/api/{tenantId}/apps/{appId}/env/{env}/...`) - For deployed apps

**Recommendation**: **Option A** for now (simplicity), move to Option C when deployment feature is complete.

---

## Solution Plan

### Phase 1: Add Runtime Context Provider (1 hour)

**1.1 Create RuntimeContext Service** (20 minutes)
```typescript
// src/runtime/RuntimeContext.ts
export class RuntimeContext {
  private static instance: RuntimeContext;
  private tenantId: string = 'default';
  private appId: string | null = null;
  private env: string = 'dev';

  static getInstance(): RuntimeContext {
    if (!RuntimeContext.instance) {
      RuntimeContext.instance = new RuntimeContext();
    }
    return RuntimeContext.instance;
  }

  setContext(tenantId: string, appId: string, env?: string) {
    this.tenantId = tenantId;
    this.appId = appId;
    if (env) this.env = env;
  }

  getContext(): { tenantId: string, appId: string, env: string } {
    if (!this.appId) {
      throw new Error('Runtime context not initialized');
    }
    return { tenantId: this.tenantId, appId: this.appId, env: this.env };
  }
}
```

**1.2 Initialize in AppRuntimeShell** (10 minutes)
```typescript
// AppRuntimeShell.ts
import { RuntimeContext } from '../RuntimeContext';

updated() {
  if (this.runtimeState?.app) {
    RuntimeContext.getInstance().setContext(
      'default',                          // tenantId (from state or default)
      this.runtimeState.app.id,          // appId
      this.runtimeState.env || 'dev'     // env
    );
  }
}
```

**1.3 Test Runtime Context** (30 minutes)
- Unit test: RuntimeContext singleton
- Integration test: Context set on app load
- Edge cases: Missing appId, multiple apps

---

### Phase 2: Update FormContainer (30 minutes)

**2.1 Add Context Access** (15 minutes)
```typescript
// FormContainer.ts
import { RuntimeContext } from '../runtime/RuntimeContext';

private getRuntimeContext() {
  try {
    return RuntimeContext.getInstance().getContext();
  } catch (e) {
    // Fallback for development/testing
    console.warn('Runtime context not available, using defaults');
    return { tenantId: 'default', appId: 'test-app', env: 'dev' };
  }
}
```

**2.2 Fix Load Record** (5 minutes)
```typescript
// Line 216
private async loadRecord(id: string) {
  const entity = this.getAttribute('entity');
  if (!entity) return;

  try {
    const { tenantId, appId } = this.getRuntimeContext();
    const data = await apiClient.get<any>(
      `/appbana-studio/${tenantId}/apps/${appId}/${entity}/${id}`
    );
    this.populateForm(data);
  } catch (e) {
    console.error('Failed to load record', e);
    this.showError('Failed to load record');
  }
}
```

**2.3 Fix Submit** (10 minutes)
```typescript
// Lines 382, 385
const { tenantId, appId } = this.getRuntimeContext();

if (action) {
  // Custom action endpoints remain unchanged
  await apiClient.post(action, formData, { headers });
} else if (recordId) {
  // Update - app-scoped
  await apiClient.put(
    `/appbana-studio/${tenantId}/apps/${appId}/${entity}/${recordId}`, 
    formData, 
    { headers }
  );
} else {
  // Create - app-scoped
  await apiClient.post(
    `/appbana-studio/${tenantId}/apps/${appId}/${entity}`, 
    formData, 
    { headers }
  );
}
```

---

### Phase 3: Update api-client.ts Entity Functions (45 minutes)

**3.1 Update Function Signatures** (15 minutes)
```typescript
// Before (WRONG)
export async function listEntities(entity: string, params?: any)

// After (CORRECT)
export async function listEntities(
  tenantId: string, 
  appId: string, 
  entity: string, 
  params?: any
)
```

**3.2 Update All 5 Functions** (20 minutes)
- listEntities
- bulkDelete
- bulkExport
- createRow
- updateRow

**3.3 Find All Callers** (10 minutes)
```bash
grep -r "listEntities\|bulkDelete\|bulkExport\|createRow\|updateRow" app-bana-ui/src/
```

Update all call sites to pass tenantId and appId.

---

### Phase 4: Integration Testing (1 hour)

**4.1 Test Scenarios**
1. ✅ Create app with entity in Studio
2. ✅ Add page with FormContainer bound to entity
3. ✅ Preview page
4. ✅ Fill form and submit (create)
5. ✅ Verify record created with correct tenant/app scope
6. ✅ Load existing record (edit mode)
7. ✅ Update record
8. ✅ Verify update scoped to correct tenant/app
9. ✅ Test bulk operations
10. ✅ Check browser console for 404 errors

**4.2 Error Cases**
- Missing tenantId → 400 error
- Missing appId → 400 error
- Wrong tenant → 404 (data isolation verified)
- Wrong app → 404 (data isolation verified)

**4.3 Performance**
- Check runtime context access overhead (<1ms expected)
- Verify no memory leaks with singleton pattern

---

## Estimated Effort

| Phase | Task | Time | Total |
|-------|------|------|-------|
| 1 | Runtime Context Provider | 1 hour | 1h |
| 2 | Update FormContainer | 30 min | 30m |
| 3 | Update api-client.ts | 45 min | 45m |
| 4 | Integration Testing | 1 hour | 1h |
| **TOTAL** | | | **3h 15m** |

---

## Success Criteria

### Must Have (Story 1.9)
- ✅ Runtime context provider working
- ✅ FormContainer uses tenant/app-scoped routes
- ✅ api-client.ts entity functions use tenant/app-scoped routes
- ✅ End-to-end test: Create/read/update entity in runtime
- ✅ No 404 errors in browser console
- ✅ All existing tests still pass

### Nice to Have
- ⏳ Runtime context available in all components (custom property)
- ⏳ Environment-aware routes for deployed apps
- ⏳ Better error messages when context missing

---

## Next Steps

### Immediate (This Session)
1. ✅ Create this audit document
2. ⏳ Implement RuntimeContext service
3. ⏳ Update FormContainer with context
4. ⏳ Update api-client.ts functions
5. ⏳ Run integration tests
6. ⏳ Commit all fixes

### Follow-up (Next Session - Story 1.10)
1. Environment-aware routes (`/api/{tenantId}/apps/{appId}/env/{env}/...`)
2. Tenant Management UI
3. Tenant switching in Studio
4. Production deployment with environment isolation

---

## Related Files

### Backend
- ✅ GenericEntityRoutes.java - App-scoped entity routes (CORRECT)
- ✅ AppRoutes.java - App/page management routes (CORRECT)

### Frontend - Studio (CORRECT)
- ✅ AppStore.ts - All 13 API calls tenant-scoped

### Frontend - Runtime (NEEDS FIX)
- ❌ FormContainer.ts - 3 API calls need tenant/app context
- ❌ api-client.ts - 5 helper functions need tenant/app context
- ⏳ RuntimeContext.ts - NEW FILE to create

### Frontend - System APIs (OK)
- ✅ ThemeEditor.ts, EntityManager.ts, TemplateStore.ts, AppRuntimeShell.ts, AuthGuard.ts, AiChatBuilder.ts

---

## Conclusion

**Critical architectural mismatch discovered** between frontend runtime and backend routes. Backend is correctly multi-tenant, but frontend runtime is still using old global routes.

**Fix required** to make runtime work correctly. Estimated **3h 15m** to complete.

**Priority**: **P0 - Blocker** for Story 1.9 completion.

---

**Document Status**: Complete  
**Next Action**: Begin Phase 1 - Runtime Context Provider  
**Estimated Completion**: End of current session

---

**Signed**: AI Senior Engineer  
**Reviewed By**: Awaiting user approval  
**Date**: December 31, 2025
