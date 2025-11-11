# App Preview & Runtime Functionality - Analysis & Roadmap

**Date:** November 11, 2025  
**Focus:** Complete app preview with full functionality (data, navigation, forms, etc.)  
**Status:** 🔴 Partially Implemented - Needs Full Runtime System

---

## 📊 Current State Assessment

### ✅ What EXISTS (Foundation)

#### 1. **Runtime Renderer** (`src/runtime/renderer/Renderer.ts`)
- ✅ Core page rendering from metadata
- ✅ Component instantiation (buttons, text, containers)
- ✅ Component tree traversal
- ✅ Data attribute injection for studio selection
- ⚠️ **BUT**: Static only - no interactivity

#### 2. **Runtime State Models** (`src/models/runtime-state.ts`)
- ✅ `AppRuntimeState` interface (complete app context)
- ✅ `AppRuntimeStateCompact` for URL serialization
- ✅ Helper functions: `createRuntimeState()`, `encodeRuntimeState()`, `decodeRuntimeState()`
- ✅ Navigation history tracking
- ✅ Mode support: preview | production | development

#### 3. **Preview Launch System** (`src/builder/components/LivePreview.ts`)
- ✅ `handlePreview()` method - opens new tab
- ✅ Creates runtime state with all pages
- ✅ Base64 URL encoding
- ✅ Opens `/index.html?state={base64JSON}`

#### 4. **App Loading** (`src/index.ts`)
- ✅ `loadAppRuntime()` method exists
- ✅ Decodes URL state parameter
- ✅ Loads app and pages
- ⚠️ **BUT**: Only renders first page statically

#### 5. **LivePreview in Studio** (`src/builder/components/LivePreview.ts`)
- ✅ WYSIWYG canvas for page editing
- ✅ Component drag-drop
- ✅ Selection and hover states
- ✅ Delete with keyboard
- ✅ Real-time preview during editing
- ✅ Connected to TreeStore for reactivity

---

### ❌ What's MISSING (Critical Gaps)

#### 1. **No Full App Runtime Component** 🔴 CRITICAL
**Problem**: App opens in `/index.html` but there's no dedicated runtime component

**What we have**:
- `<app-renderer>` exists but is just a shell in `index.ts`
- It loads the page but doesn't provide app chrome (header, nav, etc.)

**What we need**:
- `<app-runtime-shell>` component that provides:
  - App header with name and logo
  - Page navigation (tabs or menu)
  - Current page rendering area
  - "Back to Studio" button (in preview mode)
  - Context/state management

#### 2. **No Data Binding** 🔴 CRITICAL
**Problem**: Pages render but don't fetch or display real data

**What we have**:
- Components render with static props
- No connection to backend APIs

**What we need**:
- Data binding system that connects components to entity data
- Automatic CRUD API calls based on entity metadata
- Data store/cache for runtime
- Loading states and error handling

#### 3. **No Form Handling** 🔴 CRITICAL
**Problem**: Forms render but don't submit or validate

**What we have**:
- Form inputs render as static HTML

**What we need**:
- Form submission handlers
- Client-side validation (based on entity field types)
- API integration for create/update operations
- Success/error feedback

#### 4. **No Navigation Between Pages** 🔴 CRITICAL
**Problem**: Can't navigate between pages in preview mode

**What we have**:
- Runtime state has all pages
- No UI to switch between them

**What we need**:
- Page navigation component (tabs, sidebar menu, or header links)
- Route management (update URL when page changes)
- Back/forward navigation
- Active page highlighting

#### 5. **No Action Handlers** 🟡 HIGH
**Problem**: Buttons don't do anything

**What we have**:
- Buttons render with labels

**What we need**:
- Action system that handles:
  - Navigation actions (go to page X)
  - API actions (create, update, delete)
  - Custom actions (show modal, validate form)
- Event binding based on metadata

#### 6. **No Authentication** 🟡 MEDIUM
**Problem**: No login or user context

**What we need** (future):
- Login page support
- Session management
- Protected routes
- User context in runtime state

---

## 🏗️ Implementation Roadmap

### Phase 1: Core Runtime Shell (Day 1-2) 🎯 NEXT

**Goal**: Create full app runtime component with navigation

**Tasks**:
1. **Create `AppRuntimeShell.ts`** component:
   ```typescript
   <app-runtime-shell>
     <app-runtime-header>       // App name, logo, nav
       <app-nav-tabs>            // Page navigation
     </app-runtime-header>
     <app-runtime-content>      // Current page renders here
       <app-page-renderer>
     </app-runtime-content>
   </app-runtime-shell>
   ```

2. **Implement page navigation**:
   - Tab-based navigation for multiple pages
   - Click tab → change currentPageId → re-render
   - Update URL with current page
   - Highlight active page

3. **Add preview mode UI**:
   - "PREVIEW" badge in header
   - "Back to Studio" button
   - "Edit This Page" button (opens in studio)

4. **Update `index.ts`**:
   - Replace simple renderer with `<app-runtime-shell>`
   - Pass full runtime state to shell
   - Handle URL state updates

**Deliverable**: Can open app preview and navigate between pages

---

### Phase 2: Data Binding System (Day 3-4)

**Goal**: Connect components to real data from backend

**Tasks**:
1. **Create `DataBindingEngine`**:
   - Parse component props for data bindings
   - Example: `<data-table entity="Customer" />`
   - Fetch data from `/api/Customer`
   - Inject data into component

2. **Implement data fetching**:
   - Auto-generate API calls from entity metadata
   - Handle loading states
   - Cache fetched data
   - Refresh on demand

3. **Update components to support data props**:
   - `DataTableElement` accepts `data` prop
   - `TextElement` supports `{{variable}}` syntax
   - `FormElement` binds to entity fields

4. **Create runtime data store**:
   - Similar to AppStore but for runtime data
   - Caches fetched entities
   - Reactive updates when data changes

**Deliverable**: Data tables show real data from backend

---

### Phase 3: Form Handling (Day 5)

**Goal**: Make forms functional with submission and validation

**Tasks**:
1. **Create `FormController`**:
   - Collect form values
   - Validate based on entity field types
   - Show validation errors
   - Submit to backend API

2. **Implement validation rules**:
   - Required fields
   - Email format
   - Min/max length
   - Custom patterns

3. **Add form actions**:
   - Submit button → POST/PUT to API
   - Cancel button → reset form
   - Success message → redirect or refresh

4. **Error handling**:
   - Show API errors
   - Highlight invalid fields
   - Prevent submission if validation fails

**Deliverable**: Can create/edit entities through forms

---

### Phase 4: Action System (Day 6)

**Goal**: Make buttons and links trigger actions

**Tasks**:
1. **Define action metadata structure**:
   ```typescript
   interface Action {
     type: 'navigate' | 'api' | 'custom';
     config: {
       // Navigate: target page ID
       // API: endpoint, method, data
       // Custom: handler name
     };
   }
   ```

2. **Create `ActionExecutor`**:
   - Parse action from component metadata
   - Execute based on action type
   - Handle async operations
   - Show feedback (success/error)

3. **Implement action types**:
   - **Navigate**: Change to different page
   - **API Call**: CRUD operations
   - **Show Modal**: Open dialog
   - **Validate Form**: Trigger validation
   - **Export Data**: Download CSV/PDF

4. **Wire actions to components**:
   - Buttons get `onClick` → execute action
   - Links get `href` → navigate
   - Forms get `onSubmit` → API call

**Deliverable**: Interactive app with working buttons and actions

---

### Phase 5: Advanced Features (Day 7-8)

**Goal**: Polish and advanced functionality

**Tasks**:
1. **Search and filters**:
   - Add search bar to data tables
   - Filter by entity fields
   - Sort by columns

2. **Pagination**:
   - Page through large datasets
   - Load more button
   - Infinite scroll option

3. **Relationships**:
   - Display related entities (e.g., Order.customer)
   - Drill-down to related records
   - Breadcrumbs for navigation

4. **Modal dialogs**:
   - Create/edit forms in modal
   - Confirmation dialogs
   - Detail views

5. **Toast notifications**:
   - Success messages
   - Error alerts
   - Loading indicators

**Deliverable**: Production-ready runtime with UX polish

---

### Phase 6: Authentication & Security (Future)

**Goal**: Add user management and security

**Tasks**:
1. Login/logout functionality
2. Session management
3. Protected routes (requires auth)
4. Role-based access control
5. User profile pages

---

## 📁 File Structure for Runtime System

```
app-bana-ui/src/
├── runtime/
│   ├── shell/
│   │   ├── AppRuntimeShell.ts        # NEW - Main app runtime component
│   │   ├── AppRuntimeHeader.ts       # NEW - App header with nav
│   │   ├── AppNavTabs.ts             # NEW - Page navigation tabs
│   │   └── AppRuntimeShell.css
│   │
│   ├── data/
│   │   ├── DataBindingEngine.ts      # NEW - Data binding system
│   │   ├── RuntimeDataStore.ts       # NEW - Data cache for runtime
│   │   └── ApiClient.ts              # NEW - Wrapper for API calls
│   │
│   ├── forms/
│   │   ├── FormController.ts         # NEW - Form handling
│   │   ├── ValidationEngine.ts       # NEW - Field validation
│   │   └── FormSubmitHandler.ts      # NEW - API submission
│   │
│   ├── actions/
│   │   ├── ActionExecutor.ts         # NEW - Execute actions
│   │   ├── NavigateAction.ts         # NEW - Page navigation
│   │   ├── ApiAction.ts              # NEW - API calls
│   │   └── CustomAction.ts           # NEW - Custom handlers
│   │
│   └── renderer/
│       ├── Renderer.ts               # EXISTS - Page renderer
│       └── UnknownFallback.ts        # EXISTS
│
└── index.ts                          # UPDATE - Use AppRuntimeShell
```

---

## 🎯 Success Criteria

### Phase 1 Success:
- ✅ Open preview in new tab
- ✅ See app name in header
- ✅ Navigate between pages with tabs
- ✅ "Back to Studio" button works

### Phase 2 Success:
- ✅ Data tables show real data from backend
- ✅ Can see list of entities (Customers, Orders, etc.)
- ✅ Loading states work correctly

### Phase 3 Success:
- ✅ Can fill out and submit forms
- ✅ Validation prevents invalid submissions
- ✅ Success message after save

### Phase 4 Success:
- ✅ Buttons navigate to other pages
- ✅ Delete button removes record
- ✅ Edit button opens form with data

### Final Success (All Phases):
- ✅ App works like a real application
- ✅ Can perform full CRUD operations
- ✅ Professional UX with loading/error states
- ✅ Ready for production deployment

---

## 🚀 Getting Started

**Next Steps**:
1. Create `AppRuntimeShell.ts` component
2. Update `index.ts` to use the shell
3. Test basic navigation between pages
4. Then move to data binding

**Estimated Timeline**: 7-8 days for full implementation

**Priority**: HIGH - This unlocks the full value of the platform

---

## 📝 Notes

- LivePreview in Studio is separate from App Runtime
- LivePreview = WYSIWYG editing canvas
- App Runtime = Full functional preview/production mode
- Both use the same Renderer but different contexts
- Keep them separate to avoid complexity

---

**Author**: AI Assistant  
**Review Status**: Ready for implementation  
**Next Action**: Create Phase 1 components
