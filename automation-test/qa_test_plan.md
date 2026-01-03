# QA Test Plan: Entity-Field Binding & Multi-Entity Save

**Feature**: Multi-Entity Form Save with Visual Binding Indicators  
**Test Date**: 2026-01-03  
**Tester**: QE Engineer  
**Environment**: Local Development (localhost:5173)  
**Test Type**: End-to-End Functional Testing

---

## Test Objectives

1. ✅ Verify field name display shows human-readable names (not technical names)
2. ✅ Verify multi-entity save with progress tracking works correctly
3. ✅ Verify visual binding indicators appear in builder
4. ✅ Verify dark mode support for progress toast and badges
5. ✅ Verify error handling and validation
6. ✅ Verify published app runtime functionality

---

## Pre-Test Setup

### Environment Check
- [ ] Studio running: `http://localhost:5173/studio`
- [ ] Backend running: `http://localhost:8080`
- [ ] Browser: Chrome/Edge (latest version)
- [ ] Browser console open (for debugging)
- [ ] Dark mode: Test in both light and dark modes

### Test Credentials
- **Email**: `test@example.com`
- **Password**: `Password123`

---

## Test Scenario 1: Login & Authentication

### Steps

**1.1 Navigate to Application**
```
Action: Open http://localhost:5173/studio in browser
Expected: Login page loads successfully
Validation:
  - Login form visible with email and password fields
  - "Sign In" or "Login" button present
  - No console errors
```

**1.2 Test Autofill Functionality**
```
Action: Click on email field
Expected: Browser offers autofill with test@example.com
Validation:
  - Autofill dropdown appears (if credentials saved)
  - Can select and autofill both email and password
Note: If first time, autofill won't work (expected)
```

**1.3 Perform Login**
```
Action: 
  1. Click email field
  2. **CRITICAL**: Clear the field manually (CMD+A -> Backspace) to remove any browser autofill
  3. Enter email: test@example.com
  4. Click password field and clear it
  5. Enter password: Password123
  6. Click "Sign In" button
Expected: Successfully logged in and redirected
Validation:
  - Redirected to app dashboard/home page
  - User name or avatar visible (indicates logged in)
  - No error messages
  - Console shows no authentication errors
```

**🐛 Bug Criteria**: Login fails, error shown, or console errors

---

## Test Scenario 2: App Creation

### Steps

**2.1 Identify Navigation Options**
```
Action: Observe landing page after login
Expected: See clear options for app management
Validation:
  - "New App" button visible (for creating new app)
  - "Open App" button/option visible (for existing apps)
  - Clear visual distinction between the two
```

**2.2 Create New App**
```
Action: Click "New App" button
Expected: App creation dialog/form appears
Validation:
  - Modal or form with app name field
  - Option to set app description (optional)
  - "Create" or "Save" button
  - "Cancel" button
```

**2.3 Enter App Details**
```
Action:
  1. Enter App Name: "Test Multi-Entity Form"
  2. (Optional) Description: "Testing entity binding and save features"
  3. Click "Create" button
Expected: App created and builder interface opens
Validation:
  - App builder interface loads
  - Left sidebar shows:
    - "Components" tab
    - "Entities" tab
    - "Workflow" tab (if applicable)
  - Center: Visual canvas (empty or with default components)
  - Right: Properties panel
  - App name shown in header/title
```

**🐛 Bug Criteria**: App not created, error shown, or builder doesn't load

---

## Test Scenario 3: Entity Creation

### Steps

**3.1 Navigate to Entities**
```
Action: Click "Entities" tab in left sidebar
Expected: Entity management view appears
Validation:
  - "New Entity" button visible
  - Empty state or list of existing entities
  - Search/filter options (if applicable)
```

**3.2 Create First Entity - User**
```
Action: Click "New Entity" button
Expected: Entity creation modal/form appears
Validation:
  - Entity name field
  - Display name field (optional)
  - Field list area
  - Save/Create button
```

**3.3 Configure User Entity**
```
Action: Configure entity as follows
  
Entity Name: UserInformation (or User_Information)
Display Name: User Information

Fields to add:
1. Name
   - Type: text
   - Label: "Full Name"
   - Required: Yes
   
2. Email
   - Type: email
   - Label: "Email Address"
   - Required: Yes
   
3. Phone
   - Type: text
   - Label: "Phone Number"
   - Required: No

Expected: Fields added successfully
Validation:
  - Each field appears in field list
  - Field types shown correctly
  - Required indicators visible
```

**3.4 Create Second Entity - Address**
```
Action: Create another entity

Entity Name: AddressInformation
Display Name: Address Information

Fields:
1. Street
   - Type: text
   - Label: "Street Address"
   - Required: Yes
   
2. City
   - Type: text
   - Label: "City"
   - Required: Yes
   
3. ZipCode
   - Type: text
   - Label: "ZIP Code"
   - Required: Yes

Expected: Second entity created
Validation:
  - Both entities visible in entity list
  - Can switch between entities
  - Field counts correct
```

**3.5 Save Entities**
```
Action: Click Save/Create for each entity
Expected: Entities saved successfully
Validation:
  - Success message/toast shown
  - Entities persist in list
  - No console errors
```

**🐛 Bug Criteria**: Entity not saved, fields missing, or errors shown

---

## Test Scenario 4: Page & Form Creation

### Steps

**4.1 Create New Page**
```
Action:
  1. Navigate to Pages section (might be a tab or button)
  2. Click "New Page" button
Expected: Page creation dialog appears
Validation:
  - Page name field
  - Page type/template selector (if applicable)
  - Create button
```

**4.2 Configure Page**
```
Action:
  Page Name: "Multi-Entity Form Page"
  Page Type: Blank (or Form template)
Expected: Page created and canvas appears
Validation:
  - Empty canvas ready for components
  - Component library accessible in sidebar
  - Properties panel available
```

**4.3 Add Form Container (if needed)**
```
Action: 
  - If not using form template, add a Form container from components
  - Drag/drop or click to add
Expected: Form container added to canvas
Validation:
  - Form visible on canvas
  - Can select form to see properties
```

**4.4 Add Input Fields for User Entity**
```
Action: Add 3 input components to form

Input 1 (Name):
  1. Drag "Input" component to canvas
  2. In Properties panel, set:
     - Label: "Full Name"
     - Name: "name"
     - Type: text
  3. In Entity Binding section:
     - Select Entity: "User Information"
     - Select Field: Should show "Full Name (text)" ← KEY TEST
     
Input 2 (Email):
  1. Add another Input
  2. Properties:
     - Label: "Email Address"
     - Name: "email"
     - Type: email
  3. Entity Binding:
     - Entity: "User Information"
     - Field: "Email Address (email)" ← KEY TEST
     
Input 3 (Phone):
  1. Add third Input
  2. Properties:
     - Label: "Phone Number"  
     - Name: "phone"
     - Type: text
  3. Entity Binding:
     - Entity: "User Information"
     - Field: "Phone Number (text)" ← KEY TEST

Expected: All inputs added with bindings
Validation:
  ✅ CRITICAL: Field dropdown shows "Full Name (text)" NOT "field1 (text)"
  ✅ CRITICAL: Field dropdown shows "Email Address (email)" NOT "field2 (email)"
  ✅ CRITICAL: Field names are human-readable and match entity schema
  - Each input shows binding confirmation: "✓ Binds to: UserInformation.fieldName"
  - No generic names like "Field 1", "Field 2"
```

**🚨 CRITICAL VALIDATION POINT - Field Name Display**
```
Expected in Field Dropdown:
  ✅ "Full Name (text)"
  ✅ "Email Address (email)"  
  ✅ "Phone Number (text)"

NOT Expected:
  ❌ "Field 1 (text)"
  ❌ "Field 2 (text)"
  ❌ "field1 (text)"
  ❌ "name (text)" (unless that's the display label)

If you see generic names → BUG - report immediately
```

**4.5 Add Input Fields for Address Entity**
```
Action: Add 3 more inputs for Address

Input 4 (Street):
  - Label: "Street Address"
  - Entity: "Address Information"
  - Field: "Street Address (text)" ← Verify readable name
  
Input 5 (City):
  - Label: "City"
  - Entity: "Address Information"
  - Field: "City (text)" ← Verify readable name
  
Input 6 (Zip):
  - Label: "ZIP Code"
  - Entity: "Address Information"
  - Field: "ZIP Code (text)" ← Verify readable name

Expected: All 6 inputs configured
Validation:
  - 3 inputs bound to UserInformation
  - 3 inputs bound to AddressInformation
  - All field names human-readable
  - All show binding confirmation messages
```

**🐛 Bug Criteria**: Generic field names shown, bindings don't save, or fields don't appear in dropdown

---

## Test Scenario 5: Visual Binding Indicators (Builder)

### Steps

**5.1 Check Binding Status Badges**
```
Action: 
  1. Select each input component on canvas (one at a time)
  2. Observe visual indicators on the component

Expected for BOUND inputs (with entity + field set):
  ✅ Green badge visible on component (top-right corner)
  ✅ Badge shows: "✓ U" (for UserInformation)
  ✅ Badge shows: "✓ A" (for AddressInformation)
  ✅ Badge has entity initial (U, A, etc.)
  
Expected for UNBOUND inputs (no entity/field):
  ⚠️ Yellow badge visible
  ⚠️ Badge shows: "⚠" warning icon
  ⚠️ Indicates missing binding

Validation:
  - All 6 inputs show green badges (all bound)
  - Badges have correct entity initials
  - Badges positioned at top-right
  - Fade-in animation plays when binding set
```

**5.2 Test Badge Color Coding**
```
Action: 
  1. Add a new Input without binding
  2. Leave entity and field empty
  3. Observe badge

Expected:
  - Yellow/amber warning badge appears
  - Indicates "requires binding"
  
Then:
  4. Set entity to "User Information"
  5. Set field to "Full Name"
  6. Observe badge change

Expected:
  - Badge changes from yellow to green
  - Shows "✓ U" with green background
  - Animation plays during transition
```

**5.3 Test Dark Mode (Visual Indicators)**
```
Action:
  1. Enable dark mode in OS/browser
  2. Refresh builder page
  3. Observe badge colors

Expected:
  - Bound badges: Darker green (#059669) with good contrast
  - Unbound badges: Darker orange/amber (#d97706) 
  - Text remains readable
  - Shadows more pronounced
  
Validation:
  - Badges visible in both light and dark modes
  - Color contrast meets accessibility standards
  - No visual glitches
```

**🐛 Bug Criteria**: Badges don't appear, wrong colors, or don't update when binding changes

---

## Test Scenario 6: Save Button Configuration

### Steps

**6.1 Add Save Button**
```
Action:
  1. Drag "Button" component to canvas (below inputs)
  2. In Properties panel, configure:
     - Label: "Save Information"
     - Action Type: "save-entity"
     
Expected: Button added to form
Validation:
  - Button visible on canvas
  - Properties panel shows action configuration
  - "Entities" section appears in properties
```

**6.2 Configure Multi-Entity Save**
```
Action: In button properties, Entity Binding section

  Select entities to save (checkbox list):
    ☑️ UserInformation
    ☑️ AddressInformation
    
Expected: Both entities selected
Validation:
  - Both checkboxes checked
  - Button shows: "Will save: UserInformation, AddressInformation"
  - or similar confirmation message
  - No duplicate entities in list
```

**6.3 Verify Entity Selection**
```
Action: 
  1. Uncheck one entity
  2. Observe confirmation message
  3. Re-check entity
  
Expected:
  - Can toggle entities on/off
  - Confirmation updates immediately
  - Changes persist when clicking away and back
  
Validation:
  - Entity list updates correctly
  - No errors when changing selection
  - Final state: Both entities selected
```

**🐛 Bug Criteria**: Can't select entities, duplicates appear, or selection doesn't persist

---

## Test Scenario 7: App Publishing

### Steps

**7.1 Save Page**
```
Action: Click "Save" or auto-save triggers
Expected: Page changes saved
Validation:
  - Save indication (success toast or icon)
  - No unsaved changes indicator
  - Console shows no errors
```

**7.2 Publish App**
```
Action: Click "Publish" button (usually in top-right)
Expected: Publish dialog/confirmation appears
Validation:
  - Publish confirmation modal
  - Version information (if versioned)
  - Environment selection (dev, staging, prod)
  - "Publish" confirmation button
```

**7.3 Confirm Publish**
```
Action: Click "Publish" in confirmation dialog
Expected: Publishing process starts
Validation:
  - Progress indicator (spinner/progress bar)
  - "Publishing..." message
  - Success notification when complete
  - "View in Pipeline" or similar option
```

**7.4 Access Pipeline Dashboard**
```
Action: 
  1. Click "View in Pipeline" or navigate to deployment pipeline
  2. Or click "Pipeline" tab/menu

Expected: Pipeline dashboard opens showing DevOps Pipeline
Validation:
  - Pipeline modal appears: "DevOps Pipeline: [App Name]"
  - Three deployment environments visible:
    * Development (DEV) - Left column
    * SIT / QA (SIT) - Middle column
    * Production (PR) - Right column
  - Development environment shows latest deployment
  - Version number displayed (e.g., v20260101, Version #1)
  - Deployment timestamp and user shown
  - "Open App" button visible in Development environment
  
NOTE: For automation testing, use the Development environment
```

**🐛 Bug Criteria**: Publish fails, timeout, no deployment shown in pipeline, or errors

---

## Test Scenario 8: Runtime Testing (Development Environment)

**IMPORTANT**: All runtime testing should be performed in the **Development (DEV)** environment, NOT SIT/QA or Production.

### Steps

**8.1 Open Published App from Development**
```
Action: 
  1. In pipeline dashboard (DevOps Pipeline modal)
  2. Locate "Development" column (left side, blue "DEV" badge)
  3. Click "Open App" button in Development environment

Expected: App opens in new tab/window
Validation:
  - App loads successfully from Development environment
  - Form page accessible
  - URL contains development environment identifier
  - No builder tools visible (runtime mode)
  - Development version number shown (if applicable)
```

**8.2 Navigate to Form Page**
```
Action: 
  - If multiple pages, navigate to "Multi-Entity Form Page"
  - Or if default page, form should be visible

Expected: Form renders correctly
Validation:
  - All 6 input fields visible
  - Labels match configured labels:
    ✓ "Full Name"
    ✓ "Email Address"
    ✓ "Phone Number"
    ✓ "Street Address"
    ✓ "City"
    ✓ "ZIP Code"
  - "Save Information" button visible
  - Form layout matches builder preview
```

**8.3 Fill Out Form**
```
Action: Enter test data

UserInformation fields:
  - Full Name: "John Doe"
  - Email Address: "john.doe@example.com"
  - Phone Number: "555-1234"

AddressInformation fields:
  - Street Address: "123 Main Street"
  - City: "San Francisco"
  - ZIP Code: "94105"

Expected: All fields accept input
Validation:
  - Can type in all fields
  - Email field validates email format (if configured)
  - No input errors
```

**8.4 Test Save with Progress Tracking**
```
Action:
  1. Open browser DevTools → Network tab
  2. Click "Save Information" button
  3. Observe button, progress toast, and network requests

Expected Sequence:

IMMEDIATE (< 200ms):
  1. Button changes to "Saving..." and disables
  2. Progress toast appears: "📤 Saving entities... 0/2 saved"
  3. Progress bar at 0%

DURING SAVE (1-5 seconds):
  4. Network tab shows 2 POST requests:
     - POST /api/t-xxx/apps/xxx/UserInformation
     - POST /api/t-xxx/apps/xxx/AddressInformation
  5. Progress updates as each saves:
     - "📤 Saving entities... 1/2 saved" (50% progress bar)
     - "📤 Saving entities... 2/2 saved" (100% progress bar)
  6. Progress bar animates smoothly

ON SUCCESS (after ~2-5 seconds):
  7. Toast transforms to success:
     - "✅ All 2 entities saved!"
     - Green checkmark icon
     - Progress bar hidden
  8. Button resets to "Save Information" and re-enables
  9. Success toast auto-dismisses after 3 seconds

Validation Checkpoints:
  ✅ Progress toast appears immediately
  ✅ Shows correct entity count (2 entities)
  ✅ Updates after each entity saves
  ✅ Progress bar fills from 0% → 50% → 100%
  ✅ Smooth animations, no flicker
  ✅ Both API requests succeed (200 status)
  ✅ Success state shown
  ✅ Button restored to original state
  ✅ No console errors
```

**🚨 CRITICAL TEST: Multi-Entity Save Progress**
```
This is the core feature we implemented today!

Must see:
  1. Progress toast with real-time updates
  2. "0/2" → "1/2" → "2/2" count
  3. Animated progress bar
  4. Two separate API calls
  5. Success transformation

If ANY of these fail → HIGH PRIORITY BUG
```

**8.5 Test Dark Mode (Runtime)**
```
Action:
  1. Enable dark mode in OS/browser
  2. Refresh app page
  3. Fill form and save again

Expected:
  - Progress toast has dark background (#1f2937)
  - Light text color (#f3f4f6)
  - Progress bar darker gray (#374151)
  - Success toast also dark-themed
  - Good contrast and readability

Validation:
  - All toast states readable in dark mode
  - No bright flashes or poor contrast
  - Animations still smooth
```

**🐛 Bug Criteria**: 
  - Progress toast doesn't appear
  - Count doesn't update (stays 0/2)
  - Only one API call sent
  - Success state not shown
  - Errors in console
  - Dark mode not working

---

## Test Scenario 9: Error Handling

### Steps

**9.1 Test Required Field Validation**
```
Action:
  1. Clear all form fields
  2. Click "Save Information" button

Expected: Client-side validation
Validation:
  - Alert or toast shows required field errors
  - Lists missing fields:
    • Full Name (required)
    • Email Address (required)
    • Street Address (required) 
    • City (required)
    • ZIP Code (required)
  - No API calls sent (check Network tab)
  - Button stays enabled
```

**9.2 Test Partial Form Data**
```
Action:
  1. Fill only UserInformation fields (Name, Email, Phone)
  2. Leave AddressInformation fields empty
  3. Click Save

Expected: Validation error for Address fields
Validation:
  - Error lists missing Address fields
  - UserInformation would save but Address blocks it
  - Clear error messaging
```

**9.3 Test Empty Optional Fields**
```
Action:
  1. Fill all required fields
  2. Leave "Phone Number" empty (optional field)
  3. Click Save

Expected: Save succeeds, empty field skipped
Validation:
  - Both entities save successfully
  - Phone field sent as empty/null (acceptable)
  - or Phone field not sent at all (also acceptable)
  - No errors
  - Progress tracking works
```

**9.4 Test Network Failure (Simulated)**
```
Action:
  1. Open DevTools → Network tab
  2. Enable "Offline" mode (throttle to offline)
  3. Fill form and click Save

Expected: Graceful error handling
Validation:
  - Progress toast shows error state
  - "❌ Error saving entity 1/2" or similar
  - Error message describes network failure
  - Button re-enables
  - User can retry after reconnecting
  - 30-second timeout triggers if hung
```

**9.5 Test Server Error (if possible)**
```
Action: (If you can simulate a 500 error)
  - Modify backend to return error for one entity
  - Fill form and save

Expected: Partial save with error notification
Validation:
  - First entity may save
  - Second entity fails
  - Error toast shows which entity failed
  - Clear error message
  - Button re-enables  
```

**🐛 Bug Criteria**: 
  - No validation shown
  - API called with empty required fields
  - Errors not displayed to user
  - App crashes or freezes
  - No timeout protection

---

## Test Scenario 10: Data Verification

### Steps

**10.1 VerifyDatabase/API**
```
Action:
  1. After successful save, check backend
  2. Methods:
     a) Check database directly (if accessible)
     b) Call GET API: /api/t-xxx/apps/xxx/UserInformation
     c) Call GET API: /api/t-xxx/apps/xxx/AddressInformation

Expected: Data persisted correctly
Validation:
  - UserInformation record created with:
    • name: "John Doe"
    • email: "john.doe@example.com"
    • phone: "555-1234" (or null if empty)
  - AddressInformation record created with:
    • street: "123 Main Street"
    • city: "San Francisco"
    • zipCode: "94105"
  - Correct tenant and app scoping
  - Timestamps set (createdAt, updatedAt)
```

**10.2 Test Form Reset & Re-Save**
```
Action:
  1. Clear form (or refresh page)
  2. Enter different data:
     - Name: "Jane Smith"
     - Email: "jane.smith@example.com"
     - Street: "456 Oak Avenue"
     - City: "New York"
     - ZIP: "10001"
  3. Click Save

Expected: Second save succeeds
Validation:
  - Progress tracking works again
  - New records created (or existing updated, depending on logic)
  - No interference from previous save
```

**🐛 Bug Criteria**: 
  - Data not saved to database
  - Wrong tenant/app ID
  - Data corrupted or missing fields
  - Can't save multiple times

---

## Test Execution Checklist

### Before Testing
- [ ] Both servers running (UI on 5173, Backend on 8080)
- [ ] Browser console open
- [ ] Network tab ready for monitoring
- [ ] Test credentials ready
- [ ] Screenshot tool ready for bugs

### During Testing
- [ ] Follow steps in sequence
- [ ] Mark each validation point as Pass/Fail
- [ ] Take screenshots of any failures
- [ ] Note console errors
- [ ] Check Network tab for API calls
- [ ] Test both light and dark modes

### After Testing
- [ ] Document all bugs with:
  - Steps to reproduce
  - Expected vs actual result
  - Screenshots
  - Console errors
  - Network request details
- [ ] Rate severity: Critical / High / Medium / Low
- [ ] Create bug tickets
- [ ] Summary report

---

## Expected Test Results

### Success Criteria
✅ All 10 test scenarios pass  
✅ Field names display as human-readable (KEY FIX)  
✅ Multi-entity save with progress tracking works  
✅ Visual binding indicators appear correctly  
✅ Dark mode support verified  
✅ Error handling graceful and informative  
✅ Data persists correctly to backend

### Known Issues (Expected)
- None currently documented

### Acceptance Criteria
**PASS**: All critical validations pass, no blocker bugs  
**CONDITIONAL PASS**: Minor bugs found but features functional  
**FAIL**: Critical bugs prevent core functionality

---

## Bug Severity Definitions

**CRITICAL** (P0): 
- Feature completely broken
- Data loss possible
- App crashes
- Security vulnerability

**HIGH** (P1):
- Major functionality impaired
- Workaround exists but difficult
- Affects majority of users

**MEDIUM** (P2):
- Functionality impaired but workaround easy
- Affects some users
- Visual/UX issues

**LOW** (P3):
- Minor inconvenience
- Rare edge case
- Cosmetic issues

---

## DevOps Pipeline Structure

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  Development    │    │    SIT / QA     │    │   Production    │
│     (DEV)       │ ──▶│     (SIT)       │ ──▶│      (PR)       │
│                 │    │                 │    │                 │
│  ✅ Test Here   │    │  Promote to →   │    │  Promote to →   │
│  [Open App]     │    │  [Open App]     │    │  [Open App]     │
│                 │    │  [Promote v##]  │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

**Testing Environment**: Always use **Development (DEV)** for:
- Manual testing
- Automation testing  
- Feature validation
- Bug reproduction

**Pipeline Flow**:
1. Publish → Deploys to Development
2. Test in Development → Click "Open App" (DEV)
3. Promote to SIT/QA → Click "Promote v##" button
4. QA testing in SIT/QA
5. Promote to Production → Final deployment

---

## Test Environment Details

**Studio (Builder)**: http://localhost:5173/studio  
**Development Runtime**: Access via "Open App" button in Development column of pipeline  
**Backend API**: http://localhost:8080  
**Browser**: Chrome/Edge (Chromium-based recommended)  
**OS**: macOS (or Windows/Linux)  
**Test Data**: Synthetic (no real PII)

**Pipeline Access**: After publishing, click pipeline icon or "View in Pipeline" to open DevOps Pipeline modal

---

**Test Plan Status**: ✅ Ready for Execution  
**Estimated Time**: 30-45 minutes (Scenarios 1-10 in Development environment)  
**Prerequisites**: Servers running, test account created  
**Testing Environment**: Development (DEV) - Use "Open App" button in pipeline
