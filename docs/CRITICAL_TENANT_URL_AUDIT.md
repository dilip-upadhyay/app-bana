# CRITICAL: Tenant ID URL Audit & Fix Plan

**Date:** December 31, 2025  
**Severity:** CRITICAL - Architectural Design Flaw  
**Impact:** All multi-tenant isolation compromised

---

## Problem Statement

**ALL URLS are missing `{tenantId}` path parameter.** This is a fundamental architectural flaw that prevents proper multi-tenant isolation.

### Correct URL Pattern

```
Builder/Studio: /studio/{tenantId}/apps/{appId}/...
Runtime/API:    /api/{tenantId}/apps/{appId}/...
```

---

## Current Status

### ✅ FIXED (Story 1.5)
**File:** `GenericEntityRoutes.java`  
**Routes Fixed:** 5/5

1. ✅ POST   `/studio/{tenantId}/apps/{appId}/{entity}`
2. ✅ GET    `/studio/{tenantId}/apps/{appId}/{entity}`
3. ✅ GET    `/studio/{tenantId}/apps/{appId}/{entity}/{id}`
4. ✅ PUT    `/studio/{tenantId}/apps/{appId}/{entity}/{id}`
5. ✅ DELETE `/studio/{tenantId}/apps/{appId}/{entity}/{id}`

**Commit:** 90c96b8

---

## 🚨 PENDING FIXES

### Story 1.6/1.7: AppRoutes.java  
**File:** `AppRoutes.java`  
**Routes to Fix:** 15+ routes

#### Studio Builder Routes (Currently: `/appbana-studio/...`)

**Must become: `/studio/{tenantId}/apps/...`**

1. ❌ POST   `/appbana-studio/apps` → `/studio/{tenantId}/apps`
2. ❌ PUT    `/appbana-studio/apps/{id}` → `/studio/{tenantId}/apps/{id}`
3. ❌ DELETE `/appbana-studio/apps/{id}` → `/studio/{tenantId}/apps/{id}`
4. ❌ GET    `/appbana-studio/apps/{id}/workflow` → `/studio/{tenantId}/apps/{id}/workflow`
5. ❌ PUT    `/appbana-studio/apps/{id}/workflow` → `/studio/{tenantId}/apps/{id}/workflow`
6. ❌ GET    `/appbana-studio/apps/{appId}/pages/{pageId}` → `/studio/{tenantId}/apps/{appId}/pages/{pageId}`
7. ❌ PUT    `/appbana-studio/apps/{appId}/pages/{pageId}` → `/studio/{tenantId}/apps/{appId}/pages/{pageId}`
8. ❌ DELETE `/appbana-studio/apps/{appId}/pages/{pageId}` → `/studio/{tenantId}/apps/{appId}/pages/{pageId}`

**Note:** These routes already have `GET /studio/{tenantId}/apps` and `GET /studio/{tenantId}/apps/{id}` from earlier partial fix.

#### Runtime Public Routes (Currently: `/api/apps/...`)

**Must become: `/api/{tenantId}/apps/...`**

1. ❌ GET    `/api/apps/{id}/full` → `/api/{tenantId}/apps/{id}/full`
2. ❌ GET    `/api/apps/{id}/env/{env}/full` → `/api/{tenantId}/apps/{id}/env/{env}/full`

#### Release Management Routes (DevOps)

**Must become: `/api/{tenantId}/apps/{id}/...`**

1. ❌ POST   `/api/apps/{id}/versions` → `/api/{tenantId}/apps/{id}/versions`
2. ❌ GET    `/api/apps/{id}/versions` → `/api/{tenantId}/apps/{id}/versions`
3. ❌ POST   `/api/apps/{id}/deploy/{versionId}` → `/api/{tenantId}/apps/{id}/deploy/{versionId}`
4. ❌ GET    `/api/apps/{id}/pipeline` → `/api/{tenantId}/apps/{id}/pipeline`
5. ❌ POST   `/api/apps/{id}/restore-schemas` → `/api/{tenantId}/apps/{id}/restore-schemas`

---

## Implementation Pattern

### For Each Route:

**BEFORE:**
```java
router.post("/appbana-studio/apps", (req, res) -> {
    try {
        String tenantId = getTenantId();  // Hardcoded or from context
        CreateAppRequest createReq = req.readJson(CreateAppRequest.class);
        AppMetadata app = AppManager.createApp(tenantId, createReq);
        // ...
    }
});
```

**AFTER:**
```java
router.post("/studio/{tenantId}/apps", (req, res) -> {
    String tenantId = req.pathParam("tenantId");
    if (tenantId == null || tenantId.isBlank()) {
        res.json(400, Map.of("error", "tenantId required"));
        return;
    }
    try {
        CreateAppRequest createReq = req.readJson(CreateAppRequest.class);
        AppMetadata app = AppManager.createApp(tenantId, createReq);
        // ...
    }
});
```

### Key Changes:
1. Add `{tenantId}` to URL path
2. Extract `tenantId` from `req.pathParam("tenantId")`
3. Validate `tenantId` is not null/blank → 400 error
4. Remove `getTenantId()` helper calls (no longer needed)
5. Pass explicit `tenantId` to all manager methods

---

## Estimated Effort

- **AppRoutes.java fixes:** 15 routes × 5 min = 75 minutes
- **Testing:** 15 minutes
- **Total:** ~90 minutes (1.5 hours)

---

## Next Steps

1. Fix remaining 6 `/appbana-studio/` routes in AppRoutes.java
2. Fix 2 runtime `/api/apps/` routes  
3. Fix 5 release management routes
4. Update tests to use new URL patterns
5. Update frontend UI to include tenantId in API calls (Story 1.8)
6. Full integration test

---

## Risk Assessment

**Current Risk:** HIGH  
- All apps/pages currently share data across "tenants"
- Data leakage possible if multiple tenants exist
- No enforcement of tenant isolation at URL level

**After Fix:** LOW  
- Tenant ID enforced at routing layer
- Explicit validation before processing
- Clear separation of concerns

---

## Status

- [x] Story 1.5: GenericEntityRoutes.java (5/5 routes) - Commit 90c96b8
- [ ] Story 1.6: AppRoutes.java Studio routes (6/6 routes)
- [ ] Story 1.7: AppRoutes.java Page routes (3/3 routes) [Duplicate of 1.6]
- [ ] Runtime routes (2/2 routes)
- [ ] Release management routes (5/5 routes)
- [ ] Story 1.8: Frontend UI updates
- [ ] Story 1.9: Tenant management UI
- [ ] Story 1.10: Documentation & testing

---

**Last Updated:** December 31, 2025 21:10 IST  
**Next Action:** Continue fixing AppRoutes.java routes systematically
