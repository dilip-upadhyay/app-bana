# Proper GenericEntityRoutes Extraction - Implementation Plan

## Objective
Extract GenericEntityRoutes from ApiServer backup with **zero coupling** to ApiServer by creating proper service layer.

## Current Problem
- GenericEntityRoutes code (~560 lines) uses 20+ ApiServer static utility methods
- Cannot extract routes without creating tight coupling
- Need to extract utilities first

## Phased Approach

### Phase 1: Extract Authentication Service
**Create:** `com.appbana.service.AuthService`

**Methods to extract from ApiServer:**
- `extractToken(Request)` - Extract JWT from request
- `extractUserId(Request, AppConfig)` - Get user ID from token
- `authEnabled(AppConfig)` - Check if auth is enabled
- `hasRead(String token, AppConfig)` - Check read permission
- `hasWrite(String token, AppConfig)` - Check write permission
- `hasAdmin(String token, AppConfig)` - Check admin permission

**Lines:** ~100 lines

---

### Phase 2: Extract CRUD Service  
**Create:** `com.appbana.service.EntityCrudService`

**Methods to extract from ApiServer:**
- `insertRecord(EntitySchema, Map)` - Create entity
- `getById(EntitySchema, String)` - Read entity
- `updateRecord(EntitySchema, String, Map)` - Update entity
- `deleteRecord(EntitySchema, String)` - Delete entity
- `insertBatch(EntitySchema, List)` - Batch insert
- `queryRecords(EntitySchema, filters, pagination)` - Query with filters

**Lines:** ~200 lines

---

### Phase 3: Extract Error Handling
**Create:** `com.appbana.service.ErrorHandler`

**Methods to extract:**
- `errorDetails(Exception)` - Format error response
- Standard error response builders
- Exception to HTTP status mapping

**Lines:** ~50 lines

---

### Phase 4: Extract & Populate GenericEntityRoutes
**Update:** `com.appbana.server.routes.GenericEntityRoutes`

**Routes to implement:**
- `POST /api/{entity}` - Create
- `GET /api/{entity}` - List with pagination
- `GET /api/{entity}/{id}` - Get by ID
- `PUT /api/{entity}/{id}` - Update
- `DELETE /api/{entity}/{id}` - Delete
- `POST /api/{entity}/batch` - Batch create
- `POST /api/{entity}/bulk-delete` - Bulk delete
- `POST /api/{entity}/bulk-export` - Export
- `POST /api/field-permissions` - Permissions CRUD
- Datasource management routes

**Dependencies:** AuthService, EntityCrudService, ErrorHandler

**Lines:** ~400 lines (routes only, no business logic)

---

## Benefits

**Before:**
```
GenericEntityRoutes → ApiServer.static methods
                    ↓
              Tight coupling
```

**After:**
```
GenericEntityRoutes → AuthService
                   → EntityCrudService  
                   → ErrorHandler
                    ↓
              Proper OOP, testable
```

---

## Verification

1. ✅ Compile successfully
2. ✅ All tests pass
3. ✅ No `ApiServer.` calls in GenericEntityRoutes
4. ✅ Services are unit testable
5. ✅ Routes are thin (no business logic)

---

## Estimated Effort
- Phase 1: 10 minutes
- Phase 2: 15 minutes  
- Phase 3: 5 minutes
- Phase 4: 10 minutes
**Total: 40 minutes**
