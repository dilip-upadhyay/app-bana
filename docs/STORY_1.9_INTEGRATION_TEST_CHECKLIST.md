# Story 1.9 - Integration Testing Checklist

**Date:** December 31, 2025  
**Status:** In Progress  
**Testing Environment:**
- Frontend: http://localhost:5173/studio
- Backend: http://localhost:8080 (PID: 58402, Status: UP)
- Browser: Simple Browser (VS Code)

---

## ✅ Pre-Test Verification

- [x] Backend running (port 8080)
- [x] Frontend dev server running (port 5173)
- [x] Studio Builder accessible
- [x] Browser DevTools ready (Console tab open)
- [x] RuntimeContext service implemented
- [x] FormContainer fixed
- [x] api-client.ts fixed
- [x] AppRuntimeShell integrated

---

## 🧪 Test Scenarios

### Test 1: Create App with Entity ⏳

**Steps:**
1. [ ] Click "+ New App" button
2. [ ] Enter app name: "Test Customer App"
3. [ ] Click "Create"
4. [ ] Verify app appears in app list
5. [ ] Go to "Schemas" or "Entities" section
6. [ ] Click "+ New Entity"
7. [ ] Enter entity name: "customer"
8. [ ] Add fields:
   - [ ] name (type: string, required)
   - [ ] email (type: string, required)
9. [ ] Click "Save"
10. [ ] Verify entity created successfully

**Expected Results:**
- ✅ App created with unique ID
- ✅ Entity schema saved
- ✅ No errors in browser console
- ✅ API calls use correct URL pattern: `/appbana-studio/default/apps/{appId}/...`

**Browser Console Checks:**
- [ ] RuntimeContext initialization message visible
- [ ] No 404 errors
- [ ] No "undefined" errors
- [ ] No CORS errors

**Actual Results:**
```
[Record your observations here after testing]
```

---

### Test 2: Add Page with FormContainer ⏳

**Steps:**
1. [ ] Click "+ New Page"
2. [ ] Enter name: "Customer Form"
3. [ ] Enter path: "/customer-form"
4. [ ] Choose template: Blank or Contact Form
5. [ ] Click "Create"
6. [ ] Drag "Form Container" to canvas
7. [ ] Select form container
8. [ ] Set property `entity`: "customer"
9. [ ] Set property `submitAction`: "create"
10. [ ] Click "👁️ Preview" button

**Expected Results:**
- ✅ Page created successfully
- ✅ FormContainer renders in canvas
- ✅ Properties panel shows entity binding
- ✅ Preview opens in new tab
- ✅ Form renders correctly in preview

**Browser Console Checks:**
- [ ] Preview page loads without errors
- [ ] RuntimeContext set in preview: `{ tenantId: 'default', appId: '<app-id>', env: 'dev' }`
- [ ] No 404 errors
- [ ] No JavaScript errors

**Actual Results:**
```
[Record your observations here after testing]
```

---

### Test 3: Submit Form (Create Record) ⏳

**Steps:**
1. [ ] In preview tab, enter form data:
   - Name: "John Doe"
   - Email: "john@example.com"
2. [ ] Click "Submit" button
3. [ ] Observe success message or toast
4. [ ] Check browser DevTools Network tab:
   - [ ] Find POST request
   - [ ] Verify URL: `/appbana-studio/default/apps/{appId}/customer`
   - [ ] Verify status: 200 or 201
   - [ ] Check request payload
   - [ ] Check response body

**Expected Results:**
- ✅ Form submits without errors
- ✅ Success message displayed
- ✅ POST request to correct URL with tenantId and appId
- ✅ Response status: 200/201
- ✅ Response contains created record with ID

**Network Request Details:**
```
Method: POST
URL: /appbana-studio/default/apps/<app-id>/customer
Headers:
  Content-Type: application/json
  X-CSRF-Token: <csrf-token>
  X-Session-Token: <session-token>
  X-Session-Id: <session-id>
Request Body:
{
  "name": "John Doe",
  "email": "john@example.com"
}
```

**Actual Results:**
```
[Record your observations here after testing]
```

---

### Test 4: Verify Data Saved with Tenant/App Scope ⏳

**Steps:**
1. [ ] Check backend logs for POST request
2. [ ] Verify TenantContext was set correctly
3. [ ] Query database directly to verify data:
   ```sql
   SELECT * FROM customer WHERE tenant_id = 'default';
   ```
4. [ ] Verify record has correct app_id
5. [ ] Verify no records exist without tenant_id/app_id

**Expected Results:**
- ✅ Backend logs show TenantContext set: `tenantId=default, appId=<app-id>`
- ✅ Record saved with tenant_id='default'
- ✅ Record saved with app_id=<actual-app-id>
- ✅ No global records created (tenant_id cannot be null)

**Database Query Results:**
```
[Record query results here]
```

---

### Test 5: Load Existing Record (Edit Mode) ⏳

**Steps:**
1. [ ] Modify form to include recordId property
2. [ ] Set recordId to the ID of created record
3. [ ] Refresh preview or reload page
4. [ ] Check browser console for GET request:
   - [ ] URL: `/appbana-studio/default/apps/{appId}/customer/{recordId}`
   - [ ] Status: 200
5. [ ] Verify form fields populated with existing data

**Expected Results:**
- ✅ GET request to correct URL
- ✅ Record loaded successfully
- ✅ Form fields show: name="John Doe", email="john@example.com"
- ✅ No 404 errors

**Actual Results:**
```
[Record your observations here after testing]
```

---

### Test 6: Update Record ⏳

**Steps:**
1. [ ] With form in edit mode (recordId set)
2. [ ] Modify data:
   - Name: "Jane Doe"
   - Email: "jane@example.com"
3. [ ] Click "Submit" button
4. [ ] Check Network tab:
   - [ ] Method: PUT
   - [ ] URL: `/appbana-studio/default/apps/{appId}/customer/{recordId}`
   - [ ] Status: 200
5. [ ] Verify success message

**Expected Results:**
- ✅ PUT request to correct URL
- ✅ Record updated successfully
- ✅ Response contains updated record
- ✅ Database shows updated values
- ✅ tenant_id and app_id unchanged

**Actual Results:**
```
[Record your observations here after testing]
```

---

### Test 7: Table Data Loading (api-client.ts functions) ⏳

**Steps:**
1. [ ] Create a page with Table component
2. [ ] Bind table to "customer" entity
3. [ ] Preview the page
4. [ ] Check Network tab for GET request:
   - [ ] URL: `/appbana-studio/default/apps/{appId}/customer`
   - [ ] Status: 200
5. [ ] Verify table shows created records

**Expected Results:**
- ✅ fetchTableData() uses correct URL
- ✅ Records displayed in table
- ✅ Only records for current tenant/app shown
- ✅ No 404 errors

**Actual Results:**
```
[Record your observations here after testing]
```

---

### Test 8: Bulk Operations ⏳

**Steps:**
1. [ ] In table view, select multiple records
2. [ ] Test bulk delete:
   - [ ] Click "Delete Selected"
   - [ ] Check Network: POST to `/appbana-studio/default/apps/{appId}/customer/bulk-delete`
   - [ ] Verify status: 200
3. [ ] Test bulk export:
   - [ ] Click "Export Selected"
   - [ ] Check Network: POST to `/appbana-studio/default/apps/{appId}/customer/bulk-export`
   - [ ] Verify CSV download

**Expected Results:**
- ✅ Bulk delete uses correct URL
- ✅ Bulk export uses correct URL
- ✅ Operations succeed
- ✅ Only tenant/app-scoped records affected

**Actual Results:**
```
[Record your observations here after testing]
```

---

## 🐛 Issues Found

### Issue 1: [Title]
**Severity:** Critical / High / Medium / Low  
**Description:**  
**Steps to Reproduce:**  
**Expected Behavior:**  
**Actual Behavior:**  
**Fix Required:**  

---

## ✅ Test Summary

**Test Execution Date:** [Fill in after testing]  
**Total Tests:** 8  
**Passed:** 0  
**Failed:** 0  
**Blocked:** 0  

**Overall Status:** ⏳ In Progress

---

## 📋 Next Steps

After completing all tests:

1. [ ] Update this checklist with all actual results
2. [ ] Document any issues found
3. [ ] Create bug fixes if needed
4. [ ] Update Story 1.9 status to 100% complete
5. [ ] Mark Story 1.9 as "completed" in todo list
6. [ ] Commit final changes
7. [ ] Proceed to Story 1.10 (Tenant Management UI)

---

## 🔍 Key Things to Watch For

**Critical Success Indicators:**
- ✅ All URLs include `/appbana-studio/{tenantId}/apps/{appId}/...`
- ✅ No 404 errors for entity CRUD operations
- ✅ RuntimeContext properly initialized in preview
- ✅ TenantContext set on backend for all requests
- ✅ Data saved with correct tenant_id and app_id
- ✅ No global (non-scoped) data created

**Common Problems to Check:**
- ❌ 404 errors (wrong URL pattern)
- ❌ Undefined appId/tenantId errors
- ❌ CORS errors
- ❌ RuntimeContext not initialized
- ❌ Backend TenantContext not set
- ❌ Data saved without tenant/app scope

---

**Notes:**
- Use browser DevTools Network tab to inspect all requests
- Check Console tab for JavaScript errors
- Keep backend logs visible for server-side verification
- Test with multiple apps to verify isolation
