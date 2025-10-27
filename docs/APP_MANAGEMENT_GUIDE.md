# App Management Guide

## 🎯 Overview

AppBana Studio now supports **multi-app management** - you can create multiple applications, each containing multiple pages, all organized and accessible from one interface.

## 📱 Key Concepts

### What is an App?
An **App** is a container for multiple pages that share:
- Common settings (theme, routing, metadata)
- Organizational context (name, description, version)
- Navigation structure (default page, page ordering)

### What is a Page?
A **Page** is a single view/screen within your app containing:
- Component tree (your visual layout)
- Page-specific metadata (name, path, settings)
- Nodes and their properties

### Hierarchy
```
App (e.g., "My CRM")
├── Page 1 (e.g., "Dashboard")
├── Page 2 (e.g., "Contacts")
└── Page 3 (e.g., "Reports")
```

## 🚀 Quick Start

### Creating Your First App

1. **Open Studio**
   - Navigate to http://localhost:5173/studio.html

2. **Click "➕ New App"**
   - Located in the top App Manager bar

3. **Fill in App Details**
   - **Name**: "My First App" (required)
   - **Description**: Optional description
   - **Template**: Choose starting template:
     - **Blank**: Empty container
     - **Single Page**: Header + Content + Footer
     - **Dashboard**: Sidebar + Content area

4. **Click "Create App"**
   - Your app is created with one initial page
   - The app becomes the active/current app
   - You can now add pages and components

### Switching Between Apps

1. **Click "📂 Open App"**
   - In the App Manager bar

2. **Select an App**
   - Click on any app in the list
   - The selected app becomes active

3. **View App Details**
   - See page count
   - See last update time
   - Delete apps (🗑️ icon)

## 📋 Features

### App Manager (Top Bar)

**Current App Display**
- Shows active app name
- Shows page count
- Visual indicator (📦 icon)

**Actions**
- **📂 Open App**: Switch to a different app
- **➕ New App**: Create a new application

### App Templates

#### 1. Blank Template
Creates an empty app with minimal structure:
```
Container (root)
└── Text: "Welcome to [Your App Name]"
```

Best for: Starting from scratch, full creative control

#### 2. Single Page Template
Creates a traditional web page structure:
```
Container (root)
├── Header
│   └── Title (h1)
├── Main
│   └── Content (p)
└── Footer
    └── Footer Text (p)
```

Best for: Marketing sites, landing pages, simple apps

#### 3. Dashboard Template
Creates a sidebar layout:
```
Container (root)
├── Sidebar
│   └── Navigation Title (h2)
└── Content Area
    └── Dashboard Title (h1)
```

Best for: Admin panels, data dashboards, complex apps

## 🎨 Working with Apps

### Page Management Within Apps

Once you have an app selected:

1. **View Pages**
   - Pages are shown in the Page Manager (second bar)
   - Switch between pages by clicking tabs

2. **Add Pages**
   - Click "➕ New Page" in Page Manager
   - Pages belong to the current app

3. **Delete Pages**
   - Click ✖ on a page tab
   - Cannot delete the last page
   - Cannot delete the default page without setting another as default

### App Settings (Coming Soon)

- **Theme Configuration**: Colors, fonts, dark mode
- **Routing Setup**: URL paths, routing mode
- **Default Page**: Set which page loads first
- **App Metadata**: Version, author, custom data

## 💾 Data Storage

### Where Apps are Stored
- **localStorage**: Browser-based storage
- **Key Format**: `appbana.apps.{app-id}`
- **Pages**: Stored per app: `appbana.apps.{app-id}.page.{page-id}`

### Persistence
- ✅ Apps persist across browser sessions
- ✅ Pages persist within their app
- ✅ Current app selection remembered
- ⚠️ Clearing browser data deletes all apps

### Backup & Export (Coming Soon)
- Export app as JSON file
- Import app from JSON
- Share apps between browsers/users

## 🔧 Advanced Usage

### App ID Generation
- Generated from app name
- Lowercase, hyphenated
- Auto-incremented if duplicate (e.g., `my-app`, `my-app-1`)

### Multiple Apps Workflow

**Scenario: Building Multiple Projects**

```
App 1: "Client Portal"
├── Login Page
├── Dashboard
└── Settings

App 2: "Admin Panel"  
├── Users Management
├── Analytics
└── Configuration

App 3: "Marketing Site"
├── Home
├── Features
└── Contact
```

Switch between them using "📂 Open App"

### Deleting Apps

**Warning**: Deleting an app:
- ❌ Deletes ALL pages in the app
- ❌ Cannot be undone
- ⚠️ Confirmation required

**Protection**:
- Cannot delete if it's the only app (creates empty state)
- Must confirm with app name
- Auto-switches to another app after deletion

## 📊 App Management Best Practices

### Naming Conventions
- ✅ Use clear, descriptive names
- ✅ Include project/client name
- ✅ Use versioning if needed: "My App v2"

### Organization
- 📁 One app per project/client
- 📁 Related pages in same app
- 📁 Use descriptions to document purpose

### Templates
- 🎨 Start with closest template
- 🎨 Customize from there
- 🎨 Create reusable patterns within pages

## 🐛 Troubleshooting

### "No app selected" Message
**Cause**: No apps exist or app was deleted  
**Solution**: Click "➕ New App" to create one

### Can't Delete App
**Cause**: Trying to delete default page or last page  
**Solution**: Set another page as default first, or create another page

### App Not Showing
**Cause**: Browser localStorage cleared  
**Solution**: Apps are gone; create new ones or restore from backup (when available)

### Pages Disappeared
**Cause**: Switched to different app  
**Solution**: Use "📂 Open App" to switch back

## 🎯 Next Steps

After mastering app management:

1. **Learn Page Management** → See [PAGE_MANAGER_GUIDE.md]
2. **Add Components** → See [STUDIO_FOCUS_POINTS.md]
3. **Build Complex Layouts** → See [STUDIO_DRAG_DROP_GUIDE.md]
4. **Use Shortcuts** → See [UI_BUILDER_SHORTCUTS.md]

## 📝 Summary

**Key Takeaways:**
- ✅ Apps contain multiple pages
- ✅ Create unlimited apps for different projects
- ✅ Switch between apps easily
- ✅ Each app has its own theme and settings
- ✅ Data persists in browser localStorage
- ✅ Templates help you start quickly

**Remember:**
> "Apps organize projects, pages organize content within projects"

---

**Happy Building! 🚀**

