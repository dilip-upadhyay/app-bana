# 🎨 Page Template Selection - User Guide

**Date:** October 30, 2025  
**Status:** ✅ Implemented

## Overview

When creating a new page, you can now **choose which sections to include** using an intuitive template selection interface. Select from **Nav**, **Sidenav**, **Main**, and **Footer** sections to build your page structure instantly!

---

## 🌟 Key Features

### 1. **Two-Step Page Creation**
- **Step 1:** Enter page name and URL path
- **Step 2:** Choose page sections (template)

### 2. **Visual Section Selection**
Interactive cards with checkboxes:
- **🧭 Navigation Bar** - Top nav with logo and menu
- **📁 Side Navigation** - Left sidebar for secondary nav
- **📄 Main Content** - Primary content area (always included)
- **📝 Footer** - Bottom footer section

### 3. **Live Preview**
See your page layout structure before creating it!

### 4. **Smart Layout**
The page automatically arranges sections:
- Nav at top
- Sidenav + Main in flex layout
- Footer at bottom
- Full viewport height layout

---

## 📖 How to Use

### Creating a Page with Template

1. **Click "➕ New Page"** button in Page Manager

2. **Step 1: Basic Info**
   - Enter **Page Name** (e.g., "Dashboard")
   - Enter **URL Path** (e.g., "/dashboard")
   - Click **"Next →"**

3. **Step 2: Choose Sections**
   - Click on sections you want:
     - **Nav** - Top navigation bar
     - **Sidenav** - Left sidebar
     - **Footer** - Bottom footer
   - **Main** is always included
   - See preview update in real-time

4. **Click "Create Page"**
   - Page appears on canvas immediately
   - All selected sections are visible
   - Ready to add components!

---

## 🎯 Common Page Templates

### 1. **Simple Page** (Main Only)
```
Sections: Main
Use case: Landing page, simple content page
```

### 2. **Standard Page** (Nav + Main + Footer)
```
Sections: Nav, Main, Footer
Use case: About page, contact page, general content
```

### 3. **Dashboard** (Nav + Sidenav + Main)
```
Sections: Nav, Sidenav, Main
Use case: Admin dashboard, app interface
```

### 4. **Full Layout** (All Sections)
```
Sections: Nav, Sidenav, Main, Footer
Use case: Complete application layout
```

### 5. **Documentation** (Nav + Sidenav + Main + Footer)
```
Sections: Nav, Sidenav, Main, Footer
Use case: Documentation site, knowledge base
```

---

## 🎨 Section Details

### 🧭 Navigation Bar (Nav)
**Appearance:**
- Dark background (#1f2937)
- White text
- Flexbox layout (space-between)
- Min height: 60px
- Top of page

**Typical Contents:**
- Logo
- Menu items
- Search bar
- User profile

### 📁 Side Navigation (Sidenav)
**Appearance:**
- Light gray background (#f3f4f6)
- 250px width
- Left side of page
- Vertical layout

**Typical Contents:**
- Navigation links
- Menu items
- Category list
- Filters

### 📄 Main Content
**Appearance:**
- White background
- Flex: 1 (takes remaining space)
- Padding: 2rem
- Min height: 400px

**Typical Contents:**
- Page content
- Forms
- Data tables
- Cards and widgets

### 📝 Footer
**Appearance:**
- Dark background (#1f2937)
- White text
- Center aligned
- Min height: 80px
- Bottom of page

**Typical Contents:**
- Copyright notice
- Links
- Contact info
- Social media icons

---

## 💡 Pro Tips

### ✅ Best Practices

1. **Start with Template**
   - Choose sections upfront
   - Easier than adding them later
   - Gets structure right from the start

2. **Nav for All Public Pages**
   - Include Nav on user-facing pages
   - Provides consistent navigation
   - Improves UX

3. **Sidenav for Complex Apps**
   - Use for dashboards and admin panels
   - Great for hierarchical navigation
   - Keeps main content focused

4. **Footer for Branding**
   - Include on public-facing pages
   - Adds professionalism
   - Good for SEO links

### 📐 Layout Tips

**Dashboard Layout:**
```
✓ Nav
✓ Sidenav
✓ Main
✗ Footer
```
Maximizes content area, no footer distraction.

**Marketing Page:**
```
✓ Nav
✗ Sidenav
✓ Main
✓ Footer
```
Clean, simple, professional.

**Documentation:**
```
✓ Nav
✓ Sidenav
✓ Main
✓ Footer
```
Complete navigation structure.

---

## 🎬 Step-by-Step Example

### Creating a Dashboard Page

**Step 1: Basic Info**
```
Page Name: Dashboard
URL Path: /dashboard
→ Click "Next"
```

**Step 2: Choose Sections**
```
☑ Navigation Bar
☑ Side Navigation
☑ Main Content (always)
☐ Footer
→ Preview shows layout
→ Click "Create Page"
```

**Result:**
```
┌─────────────────────────────────┐
│  Nav (Dark, 60px height)        │
├──────┬──────────────────────────┤
│      │                          │
│ Side │     Main Content         │
│ Nav  │     (Your content here)  │
│      │                          │
│ 250px│     Flex: 1              │
└──────┴──────────────────────────┘
```

---

## 🔧 Technical Details

### Generated Structure

**Navigation:**
```typescript
{
  id: 'nav-1',
  type: 'container',
  props: {
    className: 'nav-container',
    style: 'display: flex; justify-content: space-between; ...',
    'data-section': 'nav'
  },
  children: []
}
```

**Sidenav:**
```typescript
{
  id: 'sidenav-2',
  type: 'container',
  props: {
    className: 'sidenav-container',
    style: 'width: 250px; background: #f3f4f6; ...',
    'data-section': 'sidenav'
  },
  children: []
}
```

**Main:**
```typescript
{
  id: 'main-3',
  type: 'container',
  props: {
    className: 'main-container',
    style: 'flex: 1; padding: 2rem; ...',
    'data-section': 'main'
  },
  children: []
}
```

**Footer:**
```typescript
{
  id: 'footer-4',
  type: 'container',
  props: {
    className: 'footer-container',
    style: 'padding: 2rem; background: #1f2937; ...',
    'data-section': 'footer'
  },
  children: []
}
```

### Root Container
```typescript
{
  id: 'root',
  type: 'container',
  props: {
    style: 'display: flex; flex-direction: column; min-height: 100vh;'
  },
  children: ['nav-1', 'content-wrapper-2', 'footer-3']
}
```

### Content Wrapper (when Sidenav included)
```typescript
{
  id: 'content-wrapper-2',
  type: 'container',
  props: {
    className: 'content-wrapper',
    style: 'display: flex; flex: 1;'
  },
  children: ['sidenav-3', 'main-4']
}
```

---

## 🎨 Customizing Sections

After page is created, you can:

1. **Resize Sections**
   - Select section container
   - Use Properties Inspector
   - Adjust width/height

2. **Add Components**
   - Drag components into sections
   - Build your layout
   - Style as needed

3. **Change Colors**
   - Select section
   - Modify style property
   - Update background colors

4. **Remove Sections**
   - Select section container
   - Press Delete key
   - Or click 🗑️ button

---

## 🐛 Troubleshooting

### Section not visible?
**Check:**
- Section was selected in template
- Canvas is showing the page
- Section has min-height set

### Can't add components to section?
**Solution:**
- Click on the section first
- Then drag component
- Make sure you're dropping inside section

### Layout looks wrong?
**Check:**
- Root container has flex column
- Sections have proper styling
- No conflicting CSS

### Want to add section later?
**Solution:**
- Manually add container
- Copy styles from template
- Add to root's children array

---

## 📊 Statistics

### Default Section Sizes

| Section | Width | Height |
|---------|-------|--------|
| Nav | 100% | 60px (min) |
| Sidenav | 250px | Auto (flex: 1) |
| Main | Flex: 1 | 400px (min) |
| Footer | 100% | 80px (min) |

### Typical Page Combinations

| Template | Sections | Use Cases |
|----------|----------|-----------|
| Simple | Main | Landing, Simple content |
| Standard | Nav, Main, Footer | About, Contact |
| Dashboard | Nav, Sidenav, Main | Admin, App |
| Full | All 4 | Documentation, Portal |

---

## 🚀 Coming Soon

Future enhancements:
- [ ] Save custom templates
- [ ] More preset templates
- [ ] Section color themes
- [ ] Responsive breakpoints
- [ ] Import/export templates
- [ ] Template marketplace

---

**Happy Page Building!** 🎉

Create pages with perfect structure in seconds!

