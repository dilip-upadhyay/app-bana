# Page Management Guide

## 🎯 Overview

The Page Manager in AppBana Studio handles loading, creating, and deleting pages within your applications. Pages are automatically loaded when you select or create an app.

## 📋 How Page Loading Works

### When an App is Selected

1. **App Selection Triggers Page Load**
   - User clicks "📂 Open App" and selects an app
   - OR user creates a new app with "➕ New App"
   - AppStore notifies all listeners of the change

2. **PageManager Responds**
   - Detects the app change via `appStore.onChange()`
   - Calls `loadPages()` to load all pages for the selected app
   - Automatically switches to the default page or first page

3. **Page Loading Process**
   ```
   ┌─────────────────────────────────────┐
   │  App Selected/Created               │
   └──────────────┬──────────────────────┘
                  │
                  ▼
   ┌─────────────────────────────────────┐
   │  Load all page IDs from app.pages   │
   └──────────────┬──────────────────────┘
                  │
                  ▼
   ┌─────────────────────────────────────┐
   │  Load PageMeta for each page ID     │
   └──────────────┬──────────────────────┘
                  │
                  ▼
   ┌─────────────────────────────────────┐
   │  Set current page (default or first)│
   └──────────────┬──────────────────────┘
                  │
                  ▼
   ┌─────────────────────────────────────┐
   │  Initialize TreeStore with page     │
   └──────────────┬──────────────────────┘
                  │
                  ▼
   ┌─────────────────────────────────────┐
   │  Render page in canvas              │
   └─────────────────────────────────────┘
   ```

### New App Created

When you create a new app:

1. **App Creation**
   - Click "➕ New App" and fill in app details
   - App is created with **0 pages** (no initial page)
   - Ready for you to add pages as needed

2. **Adding Your First Page**
   - Click "➕ New Page" to create your first page
   - Fill in page name and URL path
   - Page is created and displayed

3. **Ready to Edit**
   - You can immediately start adding components
   - Or create more pages using "➕ New Page"

### No App Selected

When no app is selected:
- Page Manager shows: "📱 No app selected"
- Message: "Create or select an app to manage pages"
- No pages are displayed
- Canvas is empty

## 🎨 Page Management Features

### Viewing Pages

**Page Tabs Display**
- All pages shown as tabs in the Page Manager bar
- Active page highlighted with gradient background
- Click any tab to switch pages
- Inactive pages shown in gray

**Visual Indicators**
```
┌────────────────────────────────────────────────────┐
│ [Home] [Dashboard*] [Settings] [➕ New Page]       │
│   ↑         ↑            ↑            ↑            │
│ Inactive  Active      Inactive    Create Button   │
└────────────────────────────────────────────────────┘
```

### Creating Pages

1. **Click "➕ New Page"**
   - Opens the Create Page modal

2. **Fill in Page Details**
   - **Page Name** (required): Descriptive name like "Dashboard"
   - **URL Path** (required): URL path like "/dashboard"

3. **Page Template**
   - Always creates a blank container with welcome text
   - Can be customized after creation

4. **Page ID Generation**
   - Auto-generated from page name
   - Lowercase, hyphenated (e.g., "My Dashboard" → "my-dashboard")
   - Auto-incremented if duplicate (e.g., "dashboard-1", "dashboard-2")

**Example:**
```
Name: "User Dashboard"
Path: "/dashboard"
→ Creates page ID: "user-dashboard"
→ Accessible at: #/dashboard (hash mode) or /app/dashboard (history mode)
```

### Deleting Pages

**Delete Button**
- Click the ✕ button on a page tab
- Confirmation dialog appears

**Protections**
- ⚠️ Confirmation required before deletion
- ✅ Can delete all pages, including the last one
- ✅ App can have 0 pages after deletion

**Process:**
1. Click ✕ on a page tab
2. Confirm deletion dialog
3. Page removed from app
4. Page deleted from localStorage
5. If pages remain, switches to first remaining page
6. If no pages left, shows "📄 No pages yet" message

### Switching Between Pages

**Manual Switch**
- Click on any page tab
- Page immediately loads in the canvas

**Automatic Switch**
- When creating a new page (switches to new page)
- When deleting current page (switches to first remaining)
- When selecting an app (switches to default or first page)

**Switch Process:**
1. Save current page changes
2. Load new page from storage
3. Initialize TreeStore with new page data
4. Update canvas display
5. Update property inspector

## 💾 Data Persistence

### Storage Structure

```
localStorage:
├── appbana.apps.list → ["my-app", "other-app"]
├── appbana.current.app → "my-app"
├── appbana.apps.my-app → { id, name, pages: ["home", "about"] }
├── appbana.apps.my-app.page.home → { PageMeta }
└── appbana.apps.my-app.page.about → { PageMeta }
```

### Auto-Save

**When are pages saved?**
- ✅ When you add/remove/modify components
- ✅ When you switch to another page
- ✅ When you change component properties
- ✅ When TreeStore changes are detected

**What is saved?**
- Complete page structure (nodes tree)
- All component properties
- Page metadata (name, path, settings)

### Data Loading

**On App Switch:**
1. Load app metadata
2. Load all page IDs from `app.pages`
3. Load PageMeta for each page
4. Display pages as tabs

**On Page Switch:**
1. Save current page
2. Load new page from localStorage
3. Parse page nodes
4. Render in canvas

## 🔧 Advanced Usage

### Page Context

Each page maintains:
- **Component Tree**: All nodes and hierarchy
- **Root Node ID**: Starting point of the tree
- **Page Metadata**: Name, path, settings
- **Version**: For future migration support

### Multiple Pages Workflow

**Typical App Structure:**
```
E-Commerce App
├── Home (/)
├── Products (/products)
├── Product Detail (/product/:id)
├── Cart (/cart)
└── Checkout (/checkout)
```

**Navigation Between Pages:**
- Currently: Manual tab switching
- Future: Link components, routing, breadcrumbs

### Page Templates (Initial Creation)

**Blank Page:**
```
Container (root)
└── Text: "Welcome to [Page Name]"
```

**Customization:**
- Start with blank template
- Add any components from library
- Build custom layouts

## 🐛 Troubleshooting

### Pages Not Loading

**Issue:** Pages don't appear after selecting app

**Solutions:**
1. Check browser console for errors
2. Verify app exists in AppManager
3. Clear browser cache and reload
4. Check localStorage for page data

**Console Logs to Check:**
```
[PageManager] Loading pages for app: My App Pages: ["home", "about"]
[PageManager] Loaded 2 pages: ["Home", "About"]
[PageManager] Switching to new page: home
[PageManager] Switched to page: home
```

### Cannot Delete Page

**Issue:** Delete button doesn't work or shows an error

**Solutions:**
- ✅ All pages can now be deleted, including the last one
- ✅ Confirmation dialog must be accepted
- ✅ App will show "📄 No pages yet" if all pages are deleted

### Page Changes Not Saving

**Issue:** Changes disappear when switching pages

**Causes:**
- TreeStore not initialized
- Auto-save not triggering

**Solutions:**
1. Check TreeStore is connected
2. Verify `currentStore.onChange()` is firing
3. Check browser localStorage quota

### Pages Show for Wrong App

**Issue:** Seeing pages from a different app

**Solution:**
- This is now fixed with improved page loading
- Pages reset when switching apps
- Current page ID is validated against new app's pages

## 📊 Best Practices

### Naming Conventions

**Page Names:**
- ✅ Clear and descriptive: "User Dashboard", "Product List"
- ✅ Consistent naming: "Edit Profile" not "Profile Edit"
- ❌ Avoid generic: "Page 1", "New Page"

**URL Paths:**
- ✅ Use lowercase: "/dashboard", "/products"
- ✅ Use hyphens: "/user-profile", "/order-history"
- ✅ Be RESTful: "/products/:id", "/users/:userId/orders"
- ❌ Avoid spaces or special chars

### Organization

**Page Structure:**
- �� Group related functionality
- 📁 Use consistent layouts across pages
- 📁 Create reusable components
- 📁 Keep page count manageable (5-15 pages typical)

**Page Hierarchy:**
```
Main Pages (5-7)
├── Home
├── Dashboard
├── Settings
└── Help

Feature Pages (10-15)
├── Users List
├── User Detail
├── Create User
├── Products
└── Orders
```

### Performance

**Loading Time:**
- ✅ Pages load instantly from localStorage
- ✅ Switching pages is immediate
- ⚠️ Very large pages (1000+ nodes) may be slower

**Optimization:**
- Split complex layouts into multiple pages
- Use simpler component trees
- Avoid deeply nested structures

## 🎯 Summary

**Key Features:**
- ✅ **Automatic Loading**: Pages load when app is selected
- ✅ **Auto-Save**: Changes saved automatically
- ✅ **Easy Creation**: Simple modal for new pages
- ✅ **Safe Deletion**: Confirmation required, can delete all pages
- ✅ **Tab Navigation**: Quick switching between pages
- ✅ **Persistent Storage**: Everything saved in localStorage
- ✅ **Zero Pages Support**: Apps can have 0 pages and show helpful message

**Page Lifecycle:**
```
Create → Load → Edit → Save → Switch → Delete
   ↓      ↓      ↓      ↓       ↓       ↓
  New    Auto   Manual Auto    Auto   Confirm
  Modal  Load   Edit   Save    Switch  Dialog
```

**Remember:**
> "Pages automatically load when you select an app. New apps start with 0 pages - click '➕ New Page' to add your first page!"

---

**Happy Page Building! 📄**

