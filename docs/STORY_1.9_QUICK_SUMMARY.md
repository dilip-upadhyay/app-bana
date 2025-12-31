# Story 1.9 - Quick Summary (1 Page)

**Status**: ❌ CRITICAL ISSUES FOUND  
**Priority**: P0 - BLOCKER  
**Estimated Fix Time**: 3h 15m

---

## The Problem (1 Minute Read)

**Backend** (Stories 1.5-1.7) uses **app-scoped routes**:
```
/appbana-studio/{tenantId}/apps/{appId}/{entity}
```

**Frontend Studio** (Story 1.8) correctly updated:
```typescript
✅ `/appbana-studio/${tenantId}/apps/${appId}/pages/${pageId}`
```

**Frontend Runtime** (NOT UPDATED!) still uses old global routes:
```typescript
❌ `/api/${entity}` - This route DOESN'T EXIST on backend!
```

**Result**: ALL runtime forms will fail with **404 errors**.

---

## What's Broken

### FormContainer.ts (3 API calls)
- Line 216: Load record → `/api/${entity}/${id}` ❌
- Line 382: Update record → `/api/${entity}/${recordId}` ❌
- Line 385: Create record → `/api/${entity}` ❌

### api-client.ts (5 helper functions)
- Line 337: listEntities → `/api/${entity}` ❌
- Line 343: bulkDelete → `/api/${entity}/bulk-delete` ❌
- Line 349: bulkExport → `/api/${entity}/bulk-export` ❌
- Line 355: createRow → `/api/${entity}` ❌
- Line 361: updateRow → `/api/${entity}/${id}` ❌

---

## The Fix (3 Steps)

### 1. Create RuntimeContext Service (1 hour)
Singleton service to provide `{ tenantId, appId, env }` to all runtime components.

### 2. Update FormContainer (30 minutes)
Add `getRuntimeContext()` helper, update all 3 API calls to use app-scoped routes.

### 3. Update api-client.ts (45 minutes)
Update 5 helper function signatures to accept `tenantId` and `appId` parameters.

### 4. Integration Test (1 hour)
End-to-end test: Create app → Add entity form → Submit → Verify data saved.

---

## Why This Happened

1. Backend updated (Stories 1.5-1.7) ✅
2. Frontend Studio updated (Story 1.8) ✅
3. **Frontend Runtime NOT updated** ❌ ← We forgot this!

---

## What Works

✅ Backend: All 23 routes correct  
✅ Frontend Studio: All 13 API calls correct  
✅ Frontend System APIs: All OK (AI, auth, templates)  
❌ Frontend Runtime: **8 API calls broken**

---

## Impact

**Severity**: CRITICAL  
**Affected Users**: Anyone using runtime forms  
**Data Risk**: None (404 errors, not data corruption)  
**Workaround**: None (forms won't work at all)

---

## Timeline

- **Now**: Audit complete, issues documented
- **Next 3 hours**: Implement fix
- **End of session**: Story 1.9 complete, all tests passing

---

See **STORY_1.9_AUDIT_FINDINGS_DEC31.md** for complete details.
