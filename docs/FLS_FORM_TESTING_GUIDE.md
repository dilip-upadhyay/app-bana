# FLS Form Components Testing Guide

**Date**: November 22, 2025  
**Status**: Ready for Manual Testing  
**Phase**: Authentication Phase 1 - Field-Level Security (FLS)

---

## Overview

This guide provides step-by-step instructions for testing Field-Level Security (FLS) integration in all 5 form components.

**Components Updated**:
1. ✅ InputElement (text, email, password, number, date, etc.)
2. ✅ TextareaElement (multi-line text)
3. ✅ SelectElement (dropdown)
4. ✅ CheckboxElement (single checkbox)
5. ✅ RadioGroupElement (radio button group)

**FLS Features Implemented**:
- **Field Hiding**: Non-readable fields are hidden with `display: none`
- **Field Disabling**: Read-only fields are disabled with 🔒 icon
- **Tooltips**: Disabled fields show "Field is read-only (no edit permission)"
- **Graceful Degradation**: On error, defaults to full access (no breaking changes)

---

## Architecture

### FormElement Base Class
All 5 components now extend `FormElement` (abstract base class) which provides:

```typescript
// Permission Loading
async loadFieldPermissions(entityName: string): Promise<void>
async loadFieldPermissionsFromAttribute(): Promise<void>

// Permission Checks (internal, called by component)
protected canReadFieldInternal(fieldName: string): boolean
protected canEditFieldInternal(fieldName: string): boolean

// Convenience Methods (used in render())
protected isFieldHidden(fieldName: string): boolean  // !canRead
protected isFieldDisabled(fieldName: string): boolean  // canRead && !canEdit

// UI Helpers
protected renderHiddenField(): string  // Returns display:none placeholder
protected getLockIcon(): string  // Returns ' 🔒'
protected getDisabledTooltip(): string  // Returns tooltip text

// Cache Management
clearPermissions(): void  // Call after role change
```

### Component Usage Pattern
Each component follows this pattern:

```typescript
export class InputElement extends FormElement {
  static get observedAttributes() {
    return [...otherAttrs, 'entity'];  // Add 'entity' attribute
  }

  async connectedCallback() {
    await this.loadFieldPermissionsFromAttribute();  // Load permissions
  }

  protected render(): string {
    const fieldName = this.getAttribute('name') || '';
    
    // 1. Check if field should be hidden
    if (this.isFieldHidden(fieldName)) {
      return this.renderHiddenField();
    }
    
    // 2. Check if field should be disabled
    const flsDisabled = this.isFieldDisabled(fieldName);
    const lockIcon = flsDisabled ? this.getLockIcon() : '';
    const title = flsDisabled ? this.getDisabledTooltip() : '';
    
    // 3. Render with FLS attributes
    return `
      <label>${label}${lockIcon}</label>
      <input disabled="${userDisabled || flsDisabled}" title="${title}" />
    `;
  }
}
```

---

## Testing Prerequisites

### 1. Backend Setup
Ensure backend is running with FLS migrations applied:

```bash
# Check backend is running
curl http://localhost:8080/health

# Verify FLS API endpoint
curl -H "Authorization: Bearer <token>" \
  http://localhost:8080/api/field-permissions/readable?entityName=Employee

# Expected: JSON array of readable field names
```

### 2. Test Users
Use these pre-configured users (from V1__auth_schema.sql):

| Email | Password | Roles | Field Permissions |
|-------|----------|-------|-------------------|
| admin@appbana.com | admin123 | admin | All fields (bypass FLS) |
| manager@appbana.com | manager123 | manager | Most fields, some read-only |
| user@appbana.com | user123 | user | Limited fields |
| hr@appbana.com | hr123 | hr | HR-specific fields (salary, department) |
| finance@appbana.com | finance123 | finance | Finance-specific fields (budget) |

### 3. Test Entities
Seed data includes FLS rules for these entities:

**Employee Entity** (most comprehensive):
- `id`, `first_name`, `last_name`, `email` - Readable by all
- `phone`, `address`, `hire_date` - Readable by user+
- `salary`, `performance_rating` - HR/Finance only (read)
- `ssn`, `bank_account` - Admin only

**Project Entity**:
- `budget` - Finance only (editable)
- `deadline`, `priority` - Manager+ (editable)

**Customer Entity**:
- `credit_card` - Admin only
- `loyalty_points` - User+ (read-only)

---

## Test Scenarios

### Scenario 1: InputElement - Text Input with FLS

**Objective**: Verify text input fields hide/disable based on FLS rules

**HTML**:
```html
<studio-input 
  entity="Employee" 
  name="first_name" 
  label="First Name" 
  type="text"
></studio-input>

<studio-input 
  entity="Employee" 
  name="salary" 
  label="Salary" 
  type="number"
></studio-input>

<studio-input 
  entity="Employee" 
  name="ssn" 
  label="SSN" 
  type="text"
></studio-input>
```

**Test Steps**:
1. Login as `user@appbana.com` (user role)
2. Navigate to Employee form page
3. Observe:
   - ✅ `first_name` is **visible and editable**
   - ✅ `salary` is **hidden** (not in DOM)
   - ✅ `ssn` is **hidden** (not in DOM)

4. Logout, login as `hr@appbana.com` (hr role)
5. Observe:
   - ✅ `first_name` is **visible and editable**
   - ✅ `salary` is **visible with 🔒 icon, disabled** (hr can read, not edit)
   - ✅ Hover over `salary`: tooltip shows "Field is read-only (no edit permission)"
   - ✅ `ssn` is **hidden** (hr cannot read)

6. Logout, login as `admin@appbana.com` (admin role)
7. Observe:
   - ✅ All fields **visible and editable** (admin bypasses FLS)

**Expected DOM** (user role):
```html
<studio-input entity="Employee" name="first_name">
  #shadow-root
    <label>First Name</label>
    <input type="text" />
</studio-input>

<studio-input entity="Employee" name="salary">
  #shadow-root
    <div style="display: none;"></div>  <!-- Hidden -->
</studio-input>
```

**Expected DOM** (hr role):
```html
<studio-input entity="Employee" name="salary">
  #shadow-root
    <label>Salary 🔒</label>  <!-- Lock icon -->
    <input type="number" disabled title="Field is read-only (no edit permission)" />
</studio-input>
```

---

### Scenario 2: TextareaElement - Multi-line Text with FLS

**HTML**:
```html
<studio-textarea 
  entity="Employee" 
  name="address" 
  label="Address" 
  rows="3"
></studio-textarea>

<studio-textarea 
  entity="Employee" 
  name="performance_notes" 
  label="Performance Notes" 
  rows="5"
></studio-textarea>
```

**Test Steps**:
1. Login as `user@appbana.com`
2. Observe:
   - ✅ `address` is **visible and editable**
   - ✅ `performance_notes` is **hidden** (user cannot read)

3. Login as `manager@appbana.com`
4. Observe:
   - ✅ `address` is **visible and editable**
   - ✅ `performance_notes` is **visible with 🔒 icon, disabled**

---

### Scenario 3: SelectElement - Dropdown with FLS

**HTML**:
```html
<studio-select 
  entity="Employee" 
  name="department" 
  label="Department"
  options='[{"value":"eng","label":"Engineering"},{"value":"hr","label":"HR"}]'
></studio-select>

<studio-select 
  entity="Project" 
  name="priority" 
  label="Priority"
  options='[{"value":"low","label":"Low"},{"value":"high","label":"High"}]'
></studio-select>
```

**Test Steps**:
1. Login as `user@appbana.com`
2. Observe:
   - ✅ `department` is **visible with 🔒 icon, disabled** (user can read, not edit)
   - ✅ `priority` is **hidden** (user cannot read Project.priority)

3. Login as `manager@appbana.com`
4. Observe:
   - ✅ `department` is **visible and editable**
   - ✅ `priority` is **visible and editable**

---

### Scenario 4: CheckboxElement - Checkbox with FLS

**HTML**:
```html
<studio-checkbox 
  entity="Employee" 
  name="is_active" 
  label="Active"
></studio-checkbox>

<studio-checkbox 
  entity="Employee" 
  name="has_security_clearance" 
  label="Security Clearance"
></studio-checkbox>
```

**Test Steps**:
1. Login as `user@appbana.com`
2. Observe:
   - ✅ `is_active` is **visible with 🔒 icon, disabled**
   - ✅ `has_security_clearance` is **hidden**

3. Login as `admin@appbana.com`
4. Observe:
   - ✅ Both checkboxes **visible and editable**

---

### Scenario 5: RadioGroupElement - Radio Buttons with FLS

**HTML**:
```html
<studio-radio-group 
  entity="Employee" 
  name="employment_type" 
  label="Employment Type"
  layout="horizontal"
  options='[{"value":"ft","label":"Full-Time"},{"value":"pt","label":"Part-Time"}]'
></studio-radio-group>

<studio-radio-group 
  entity="Employee" 
  name="performance_rating" 
  label="Performance Rating"
  layout="vertical"
  options='[{"value":"1","label":"Exceeds"},{"value":"2","label":"Meets"}]'
></studio-radio-group>
```

**Test Steps**:
1. Login as `user@appbana.com`
2. Observe:
   - ✅ `employment_type` is **visible and editable**
   - ✅ `performance_rating` is **hidden** (user cannot read)

3. Login as `hr@appbana.com`
4. Observe:
   - ✅ `employment_type` is **visible and editable**
   - ✅ `performance_rating` is **visible with 🔒 icon, all radios disabled**

---

## Browser DevTools Verification

### Check API Calls
Open Chrome DevTools → Network tab:

**Expected API Call** (on form load):
```http
GET /api/field-permissions/Employee HTTP/1.1
Authorization: Bearer eyJhbGc...
```

**Expected Response** (user role):
```json
{
  "permissions": {
    "id": { "canRead": true, "canEdit": false },
    "first_name": { "canRead": true, "canEdit": true },
    "last_name": { "canRead": true, "canEdit": true },
    "email": { "canRead": true, "canEdit": true },
    "phone": { "canRead": true, "canEdit": true },
    "address": { "canRead": true, "canEdit": true },
    "hire_date": { "canRead": true, "canEdit": false }
    // Note: salary, ssn, etc. are NOT in response (user cannot read)
  }
}
```

### Check Component State
Open Chrome DevTools → Elements tab → Select component → Properties:

**Expected Properties**:
```javascript
studio-input
  ├─ entity: "Employee"
  ├─ name: "salary"
  └─ fieldPermissions: {  // Cached permissions
      "first_name": { canRead: true, canEdit: true },
      "salary": { canRead: false, canEdit: false },  // Computed as false
      ...
    }
```

### Check Shadow DOM
Inspect component → Expand `#shadow-root`:

**Hidden Field** (non-readable):
```html
#shadow-root
  <div style="display: none;"></div>
```

**Disabled Field** (read-only):
```html
#shadow-root
  <label part="label">Salary 🔒</label>
  <input part="input" type="number" disabled 
         title="Field is read-only (no edit permission)" />
```

---

## Error Handling Tests

### Scenario 6: Network Failure (Graceful Degradation)

**Test Steps**:
1. Stop backend server
2. Reload form page
3. Observe:
   - ✅ Form loads without errors
   - ✅ All fields are **visible and editable** (default behavior)
   - ✅ Console shows: `[FormElement] Failed to load field permissions for Employee: ...`

**Expected Behavior**: FLS errors do NOT break the form (fail-safe mode)

---

### Scenario 7: Missing 'entity' Attribute

**HTML**:
```html
<studio-input name="first_name" label="First Name"></studio-input>
<!-- Note: No 'entity' attribute -->
```

**Test Steps**:
1. Load form
2. Observe:
   - ✅ Field is **visible and editable** (no FLS applied)
   - ✅ No API calls to `/api/field-permissions`

**Expected Behavior**: Components work normally without FLS if `entity` is omitted

---

### Scenario 8: Invalid Entity Name

**HTML**:
```html
<studio-input entity="NonExistentEntity" name="field1" label="Field 1"></studio-input>
```

**Test Steps**:
1. Load form
2. Observe:
   - ✅ Field is **visible and editable** (default to full access)
   - ✅ Console shows: `[FormElement] Failed to load field permissions for NonExistentEntity`

**Expected Behavior**: Invalid entities don't break forms

---

## Performance Tests

### Scenario 9: Permission Caching

**Test Steps**:
1. Open Chrome DevTools → Network tab
2. Load form with 10 input fields (same entity)
3. Observe:
   - ✅ Only **1 API call** to `/api/field-permissions/Employee`
   - ✅ All 10 fields share cached permissions (Promise deduplication)

4. Wait 5 minutes (cache TTL)
5. Trigger re-render (change attribute)
6. Observe:
   - ✅ New API call made (cache expired)

---

### Scenario 10: Multi-Entity Form

**HTML**:
```html
<studio-input entity="Employee" name="first_name"></studio-input>
<studio-input entity="Employee" name="last_name"></studio-input>
<studio-input entity="Project" name="name"></studio-input>
<studio-input entity="Project" name="budget"></studio-input>
```

**Test Steps**:
1. Open Chrome DevTools → Network tab
2. Load form
3. Observe:
   - ✅ **2 API calls**:
     - `GET /api/field-permissions/Employee`
     - `GET /api/field-permissions/Project`
   - ✅ Each entity loaded once (deduplication works across entities)

---

## Visual Regression Tests

### Scenario 11: Lock Icon Styling

**Test Steps**:
1. Login as `hr@appbana.com`
2. Load Employee form
3. Observe `salary` field label
4. Verify:
   - ✅ Lock icon 🔒 appears **after label text**
   - ✅ Lock icon has **same font size** as label
   - ✅ Lock icon has **slight left margin** (0.25rem)
   - ✅ No layout shift (icon doesn't cause line break)

---

### Scenario 12: Disabled Input Styling

**Test Steps**:
1. Compare enabled vs disabled input
2. Verify:
   - ✅ Disabled input has **grey background** (`background: var(--color-disabled, #f5f5f5)`)
   - ✅ Disabled input has **grey text** (`color: var(--color-text-disabled, #999)`)
   - ✅ Disabled input has **not-allowed cursor** (`cursor: not-allowed`)
   - ✅ Tooltip shows on hover

---

## Integration Tests (with AI Builder)

### Scenario 13: AI-Generated Form with FLS

**Test Steps**:
1. Open AI Chat Builder
2. Say: "Create an employee management app"
3. Verify generated form includes:
   - ✅ `entity="Employee"` attribute on all form fields
   - ✅ Fields render with FLS (salary hidden for non-HR users)

4. Say: "Add a salary field"
5. Verify:
   - ✅ AI recognizes sensitive field
   - ✅ Generates with `entity="Employee"` attribute
   - ✅ FLS automatically applied

---

## Acceptance Criteria

### ✅ All Components Updated
- [x] InputElement extends FormElement
- [x] TextareaElement extends FormElement
- [x] SelectElement extends FormElement
- [x] CheckboxElement extends FormElement
- [x] RadioGroupElement extends FormElement

### ✅ Functional Requirements
- [x] Non-readable fields are hidden (display: none)
- [x] Read-only fields are disabled with 🔒 icon
- [x] Disabled fields show tooltip on hover
- [x] Permissions are cached at component level (5-min TTL)
- [x] Promise deduplication prevents redundant API calls
- [x] Errors default to full access (no breaking changes)

### ✅ Visual Requirements
- [x] Lock icon 🔒 appears in label
- [x] Disabled inputs have grey styling
- [x] Tooltip shows "Field is read-only (no edit permission)"
- [x] No layout shifts when icon appears

### ⏳ Testing Requirements (PENDING)
- [ ] All 13 scenarios tested manually
- [ ] Screenshots captured for documentation
- [ ] JUnit tests added for FormElement (7 test cases)
- [ ] Integration tests with StudioTableLive

---

## Next Steps After Testing

1. **Add Screenshots**: Replace mockups in FLS_ADMIN_GUIDE.md with real screenshots
2. **JUnit Tests**: Add unit tests for FormElement permission methods
3. **Integration Tests**: Test FLS with StudioTableLive (table + form combo)
4. **Performance Validation**: Measure actual cache hit rate in production
5. **Update Session Summary**: Document test results in SESSION_SUMMARY_NOV22.md

---

## Troubleshooting

### Problem 1: Fields Not Hiding/Disabling

**Diagnosis**:
- Check Network tab: Is `/api/field-permissions/Employee` returning data?
- Check Console: Any `[FormElement] Failed to load...` errors?
- Check Component Properties: Is `fieldPermissions` populated?

**Solution**:
- Verify backend is running: `curl http://localhost:8080/health`
- Verify JWT token is valid (check Authorization header)
- Check entity name matches database: `SELECT DISTINCT entity_name FROM field_permission;`

---

### Problem 2: Lock Icon Not Showing

**Diagnosis**:
- Check DevTools Elements → Shadow DOM: Is `disabled` attribute present?
- Check label HTML: Does it include `🔒`?

**Solution**:
- Ensure `isFieldDisabled()` returns true: Open DevTools Console, run:
  ```javascript
  document.querySelector('studio-input').isFieldDisabled('salary')  // Should return true for hr role
  ```

---

### Problem 3: Tooltip Not Appearing

**Diagnosis**:
- Check input element: Does it have `title` attribute?

**Solution**:
- Verify `getDisabledTooltip()` is called in render():
  ```typescript
  const title = flsDisabled ? this.getDisabledTooltip() : '';
  ```

---

## Contact

For questions or issues with FLS form integration:
- **Feature Owner**: Dilip Upadhyay
- **Documentation**: `docs/FLS_ADMIN_GUIDE.md`, `docs/AUTH_PHASE1_IMPLEMENTATION.md`
- **Code**: `app-bana-ui/src/components/FormElement.ts`

---

**Testing Complete?** Mark tasks in SESSION_SUMMARY_NOV22.md:
```markdown
- [x] All 5 form components updated with FLS
- [x] Manual testing complete (13 scenarios)
- [x] Screenshots captured
- [x] JUnit tests added
```

**Next Phase**: Profile Layer (Week 2-3) - Create V3__profile_layer.sql migration
