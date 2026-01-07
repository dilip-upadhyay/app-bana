# 4. USER MANUAL — Studio Builder

**Last Updated:** December 28, 2025  
**Status:** Active - User Guide for Studio Builder  
**Audience:** Business Users, Designers, Non-Technical Users

---

## Welcome to AppBana Studio Builder! 🎨

AppBana Studio Builder is a **visual, no-code tool** for creating web applications. Design beautiful pages by dragging and dropping components—no programming required!

### What You Can Do

✅ Build forms, dashboards, and web pages visually  
✅ Drag and drop components onto a canvas  
✅ Customize colors, text, and layouts  
✅ Create responsive designs (desktop, tablet, mobile)  
✅ Connect to your data sources  
✅ Preview and publish your apps  

**No coding experience needed!**

---

## 🆕 What's New in December 2025!

**Major Updates to Speed Up Your Workflow:**

### 1. **Real Template Previews** 🎨✨ (NEW!)
- **See actual HTML content** before selecting a template
- **Login Page:** Preview shows real email/password fields and button
- **Sign Up Page:** Preview shows split-screen layout with form fields
- **Dashboard:** Preview shows header, sidebar, and KPI cards
- **All templates:** Real components rendered at 40% scale in preview window
- **Custom Builder:** Layout diagram with customizable sections
- **Instant feedback:** Know exactly what you're getting before clicking "Create"

### 2. **Optimized Modal Layout** 📐 (NEW!)
- **2×2 Grid for checkboxes:** Saved 100px vertical space
- **Larger preview area:** Increased from 150px to 200px height (33% bigger)
- **Side-by-side design:** Templates on left (5-column), preview on right
- **Fits on one screen:** No scrolling needed on standard laptops
- **85% viewport height:** Modal uses more screen space efficiently

### 3. **Pre-Built Page Templates** 🎨
- **8 professional templates** ready to use (Login, Sign Up, Dashboard, Landing, Contact, Profile, Data Table, Blank)
- **2-step wizard** for faster page creation
- **Visual gallery** with template cards and descriptions
- **93% time savings** - create pages in 2 minutes instead of 30!

### 4. **Enhanced Preview** 👁️
- Preview now opens in **new browser tab** (not in-canvas)
- See your page with **full app context** (header, navigation, app name)
- **Navigate between pages** right in the preview
- **PREVIEW badge** shows you're not in live mode
- Perfect for testing user experience before publishing

### 5. **Faster Workflow** ⚡
- Improved page creation experience
- Better validation and error messages
- Auto-save every 2 minutes
- Cleaner, more intuitive UI

**These updates make building apps faster and easier than ever!**

---

## Table of Contents

1. [Getting Started (5 Minutes)](#getting-started-5-minutes)
2. [Your First Page](#your-first-page)
3. [Understanding the Interface](#understanding-the-interface)
4. [Working with Components](#working-with-components)
5. [Creating Layouts](#creating-layouts)
6. [Using Templates](#using-templates)
7. [Component Gallery](#component-gallery)
8. [Keyboard Shortcuts](#keyboard-shortcuts)
9. [Tips & Best Practices](#tips--best-practices)
10. [Troubleshooting](#troubleshooting)

---

## Getting Started (5 Minutes)

### Accessing Studio Builder

1. Open your web browser
2. Go to: `http://your-appbana-server/studio`
3. You'll see the Studio Builder interface!

### What You'll See

The Studio Builder has **3 main panels**:

```
┌─────────────────────────────────────────────────────────────┐
│  [Component Library]  │  [Canvas/Preview]  │  [Properties]  │
│   (Drag from here)    │   (Design here)    │  (Edit here)   │
└─────────────────────────────────────────────────────────────┘
```

- **Left Panel:** Component library (buttons, text, forms, etc.)
- **Center Panel:** Your canvas (where you design)
- **Right Panel:** Properties inspector (customize selected items)

---

## Your First Page

Let's create your first page in **5 simple steps**!

### Step 1: Create a New App

1. Click **"+ New App"** button
2. Enter app name: `My First App`
3. Click **"Create"**

✅ **Done!** Your app is ready.

### Step 2: Create a Page

1. Click **"+ New Page"** button
2. Choose a template:
   - **Blank Page** (empty canvas)
   - **Login Page** (pre-built login form)
   - **Dashboard** (charts and widgets)
3. Click **"Create"**

✅ **Done!** You now have a blank canvas.

### Step 3: Add Your First Component

1. Look at the **Component Library** (left panel)
2. Find **"Button"**
3. **Drag** the button to the center canvas
4. **Drop** it where you see the green indicator

✅ **Done!** You've added a button!

### Step 4: Customize the Button

1. **Click** on the button you just added (it will have a purple border)
2. Look at the **Properties Panel** (right panel)
3. Find **"text"** property
4. Type: `Click Me!`
5. Press **Enter**

✅ **Done!** Your button now says "Click Me!"

### Step 5: Preview Your Page (NEW!)

1. Click the **"👁️ Preview"** icon button at the top of the canvas
2. A new browser tab opens showing your page **exactly as users will see it**
3. The preview includes:
   - ✅ **Full app context** with your app name in the header
   - ✅ **Navigation links** to switch between pages
   - ✅ **PREVIEW badge** so you know it's a preview (not live)
   - ✅ **Real page rendering** - exactly what users will experience

**What's New:**
- Preview now opens in a **separate tab** (used to be in-canvas)
- You can **navigate between pages** using the links in the header
- Preview works even if you have **multiple pages** in your app
- The URL includes all necessary context - you can even bookmark it!

🎉 **Congratulations!** You've created your first page!

---

## Understanding the Interface

### Top Toolbar

| Button | What It Does |
|--------|--------------|
| **📋 Templates** | Choose from pre-built page layouts |
| **👁️ Preview** | Opens page in new tab with full app context (NEW!) |
| **💾 Save** | Save your work (auto-saves too!) |
| **⏪ Undo** | Go back one step |
| **⏩ Redo** | Go forward one step |
| **🔍 Search (Cmd/Ctrl+P)** | Find components quickly |

### Left Panel — Component Library

This is your **toolbox**. All components you can add to your page:

**Basic Components:**
- Text (headings, paragraphs)
- Button (clickable actions)
- Image (photos, logos)
- Container (groups other components)

**Form Components:**
- Text Input (name, email fields)
- Dropdown (select from options)
- Checkbox (yes/no choices)
- Radio Button (choose one option)
- Date Picker (select dates)

**Layout Components:**
- Grid (organize in rows/columns)
- Flex Column (stack vertically)
- Flex Row (arrange horizontally)
- Card (content with border/shadow)

**Advanced Components:**
- Table (display data in rows)
- Chart (graphs and visualizations)
- Map (show locations)
- Timeline (events over time)

### Center Panel — Canvas/Preview

This is your **design area**:

- **Blue outline** = Component is selected
- **Green indicator** = Valid drop zone
- **Click** to select a component
- **Drag** to move components around

**Zoom Controls:**
- **50%** — Zoomed out (see more)
- **100%** — Actual size
- **200%** — Zoomed in (detail work)

**Responsive Modes:**
- 🖥️ **Desktop** (1920px wide)
- 📱 **Tablet** (768px wide)
- 📱 **Mobile** (375px wide)

### Right Panel — Properties Inspector

**Edit the selected component** here:

**Common Properties:**
- **text** — What the component displays
- **label** — Form field labels
- **placeholder** — Hint text in inputs
- **width** — Component width (px, %, auto)
- **height** — Component height
- **color** — Text/background color
- **fontSize** — Size of text
- **padding** — Space inside component
- **margin** — Space outside component

Properties change based on what you select!

---

## Working with Components

### Adding Components (Drag & Drop)

**Method 1: Drag & Drop**
1. Find component in left library
2. Click and hold
3. Drag to canvas
4. Release when you see **green indicator**

**Green Indicators Mean:**
- **Green line at top** = Drop BEFORE this component
- **Green line at bottom** = Drop AFTER this component
- **Green highlight** = Drop INSIDE this container

**Method 2: Click to Add** (coming soon)

### Selecting Components

**Click once** on any component to select it:
- Selected component has **blue border**
- Properties panel shows its settings

**Deselect:** Press `Escape` or click empty canvas area

### Moving Components

**Option 1: Drag**
1. Click and hold on component
2. Drag to new position
3. Drop where you see green indicator

**Option 2: Tree View** (left panel, tree icon)
- Drag components in the tree to reorder

### Editing Properties

1. **Select** a component (click it)
2. **Look** at Properties Panel (right side)
3. **Change** any value:
   - Type in text fields
   - Choose from dropdowns
   - Use color pickers
   - Adjust sliders
4. **Press Enter** or click outside to apply

✨ **Changes appear instantly!**

### Resizing Components

1. Select the component
2. In Properties Panel, find:
   - **Width:** `auto`, `100px`, `50%`, `full`
   - **Height:** `auto`, `200px`, `300px`
3. Or use quick presets:
   - **Small** (200px)
   - **Medium** (400px)
   - **Large** (600px)
   - **Full Width** (100%)

### Copying Components

1. Select component
2. Press **Cmd+D** (Mac) or **Ctrl+D** (Windows)
3. A copy appears below the original

### Deleting Components

**Option 1: Keyboard**
1. Select component
2. Press **Delete** or **Backspace**
3. Confirm if it has children inside

**Option 2: Delete Button**
1. Select component
2. Click **× Delete** button in Properties Panel

### Undo/Redo

**Made a mistake?**
- **Undo:** Cmd/Ctrl+Z
- **Redo:** Cmd/Ctrl+Shift+Z

Works for all actions: add, delete, edit, move!

---

## Creating Layouts

### Understanding Containers

**Containers** hold other components. Think of them as boxes:

```
Container
├── Heading
├── Text
└── Button
```

**To create layouts:**
1. Drag **Container** to canvas
2. Drag other components **inside** the container
3. Container groups them together

### Layout Patterns

#### **1. Header + Content + Footer**

```
Page
├── Header (logo, navigation)
├── Main Content (your page content)
└── Footer (copyright, links)
```

**How to build:**
1. Drag **Flex Column** to canvas
2. Drag **Container** into column (header)
3. Drag **Container** into column (content)
4. Drag **Container** into column (footer)
5. Add components inside each container

#### **2. Two-Column Layout**

```
Grid (2 columns)
├── Left Column
│   ├── Heading
│   └── Text
└── Right Column
    ├── Image
    └── Button
```

**How to build:**
1. Drag **Grid** to canvas
2. Set grid to **2 columns** in Properties
3. Drag **Container** into left cell
4. Drag **Container** into right cell
5. Add components inside each

#### **3. Form Layout**

```
Flex Column
├── Text (label: "Name")
├── Text Input
├── Text (label: "Email")
├── Text Input
├── Text (label: "Message")
├── Text Area
└── Button (Submit)
```

**How to build:**
1. Drag **Flex Column** to canvas
2. Drag **Text** component (set text: "Name")
3. Drag **Text Input** below it
4. Repeat for other fields
5. Add **Button** at bottom

#### **4. Card Grid**

```
Grid (3 columns)
├── Card
│   ├── Image
│   ├── Heading
│   └── Text
├── Card
│   ├── Image
│   ├── Heading
│   └── Text
└── Card
    ├── Image
    ├── Heading
    └── Text
```

**How to build:**
1. Drag **Grid** (set to 3 columns)
2. Drag **Card** into each grid cell
3. Add **Image**, **Heading**, **Text** inside each card

### Tips for Great Layouts

✅ **Use Containers** — Group related items  
✅ **Consistent Spacing** — Same padding/margin  
✅ **Align Items** — Use grids and flex layouts  
✅ **White Space** — Don't cram everything  
✅ **Test Responsive** — Check mobile view  

---

## Using Templates

Templates are **pre-built page layouts** to save you time!

### Available Templates (NEW: October 2025!)

AppBana Studio now includes **8 professional templates** that can save you hours of work!

| Template | What's Included | Use For |
|----------|-----------------|---------||
| **Blank** | Empty canvas | Starting from scratch |
| **Login Page** | Email field, password field, submit button, "Forgot Password?" link, footer | User authentication, sign-in pages |
| **Sign Up Page** | 45/55 split layout with brand panel (logo, tagline, feature list), two-column name fields, email, phone, password fields | User registration, account creation |
| **Dashboard** | Header with app name, sidebar navigation, 3 KPI cards (Users, Revenue, Growth) | Admin panels, analytics, business metrics |
| **Landing Page** | Hero section with headline/CTA, 3 feature cards, call-to-action section, footer | Marketing pages, product launches |
| **Contact Form** | Name field, email field, message textarea, submit button | Customer support, feedback forms |
| **Profile Page** | Avatar placeholder, bio section, stats grid (Posts/Followers/Following) | User profiles, team member pages |
| **Data Table** | Search bar, filter dropdowns, data table placeholder, pagination | Reports, user lists, inventory

### Using a Template (NEW 2-Step Wizard with Real Previews!)

**Creating a Page from Template:**

**Step 1: Basic Information**
1. Click **"+ New Page"** in Page Manager
2. Enter **page name** (e.g., "Contact Us")
3. Enter **path** (e.g., "/contact") - this will be the URL
4. Click **"Next"** button

**Step 2: Choose Your Template with Live Preview** ✨
1. You'll see a **visual gallery** with 8 template cards on the **left side**
2. Each card shows:
   - Template name (e.g., "Login Page" or "Sign Up Page")
   - Icon representing the template
   - Brief description of what's included
3. **Click on any template card** to select it (gets green border)
4. **Right side shows REAL PREVIEW:** 🎯
   - **Login:** See actual email field, password field, submit button, remember me checkbox
   - **Sign Up:** See split-screen layout with brand panel and form fields
   - **Dashboard:** See header, sidebar navigation, and KPI cards
   - **All templates:** Actual HTML components rendered at 40% scale
   - **Custom Builder:** Shows layout diagram with checkboxes (Nav/Side/Main/Footer in 2×2 grid)
5. **Switch between templates** and watch the preview update instantly
6. Click **"Create Page"** button when you've found the right template

**That's it!** Your page will be created with all components pre-configured. You can now customize it!

**Pro Tip:** Use the **real preview** to see exactly what you're getting before creating the page. No more surprises!

### Time Savings with Templates ⏱️

**Without Templates (Manual Build):**
- Login Page: ~30 minutes (fields, validation, styling)
- Dashboard: ~45 minutes (layout, cards, sidebar)
- Contact Form: ~20 minutes (form fields, labels, submit)
- **Average: 30 minutes per page**

**With Templates (NEW!):**
- Select template: **10 seconds**
- Customize text/colors: **2-3 minutes**
- **Average: 2 minutes per page**

**🎯 Result: 93% time reduction!** Spend your time on customization, not basic structure.

### Customizing Templates

Templates are just starting points! You can:

1. **Change colors** — Select components, edit color properties
2. **Replace text** — Click text, type new content
3. **Add/remove components** — Drag new ones, delete unwanted
4. **Resize sections** — Adjust widths/heights
5. **Add your logo** — Replace placeholder images

**Templates save you 80% of the work!**

---

## Component Gallery

### Text Components

#### **Text / Heading**
- **Use For:** Titles, paragraphs, labels
- **Key Properties:**
  - `text` — What to display
  - `fontSize` — Size (12px, 16px, 24px, etc.)
  - `fontWeight` — Bold, normal
  - `color` — Text color
  - `textAlign` — Left, center, right

**Example Uses:**
- Page titles (fontSize: 32px, fontWeight: bold)
- Paragraphs (fontSize: 16px)
- Labels (fontSize: 14px)

#### **Link**
- **Use For:** Navigate to other pages/URLs
- **Key Properties:**
  - `text` — Link text
  - `href` — Destination URL
  - `target` — Open in new tab (_blank)

---

### Form Components

#### **Text Input**
- **Use For:** Name, email, search fields
- **Key Properties:**
  - `placeholder` — Hint text ("Enter your name")
  - `type` — text, email, password, number
  - `required` — Must fill out
  - `pattern` — Validation (email format, etc.)

**Example Uses:**
- Name field: placeholder="Your Name"
- Email field: type="email", placeholder="you@example.com"
- Password: type="password"

#### **Button**
- **Use For:** Submit forms, trigger actions
- **Key Properties:**
  - `text` — Button label
  - `type` — primary, secondary, danger
  - `disabled` — Can't click
  - `onClick` — What happens when clicked

**Example Uses:**
- Submit button: text="Submit", type="primary"
- Cancel button: text="Cancel", type="secondary"

#### **Dropdown / Select**
- **Use For:** Choose from list (country, category, etc.)
- **Key Properties:**
  - `options` — List of choices
  - `placeholder` — "Select option"
  - `multiple` — Select multiple items

#### **Checkbox**
- **Use For:** Agree to terms, preferences
- **Key Properties:**
  - `label` — Text next to checkbox
  - `checked` — Default on/off

#### **Radio Button**
- **Use For:** Choose one option (Male/Female, Size: S/M/L)
- **Key Properties:**
  - `name` — Group related radios
  - `options` — List of choices

#### **Text Area**
- **Use For:** Long text (comments, messages)
- **Key Properties:**
  - `placeholder` — Hint text
  - `rows` — Height (number of lines)

---

### Layout Components

#### **Container**
- **Use For:** Group related components
- **Key Properties:**
  - `padding` — Space inside
  - `backgroundColor` — Fill color
  - `border` — Add border
  - `borderRadius` — Rounded corners

**Think of it as a box!**

#### **Grid**
- **Use For:** Organize in rows/columns
- **Key Properties:**
  - `columns` — Number of columns (2, 3, 4)
  - `gap` — Space between cells
  - `columnWidth` — equal, auto, custom

**Example:** Photo gallery (3 columns)

#### **Flex Column**
- **Use For:** Stack items vertically
- **Key Properties:**
  - `gap` — Space between items
  - `align` — left, center, right
  - `justify` — top, center, bottom

**Example:** Form with fields stacked

#### **Flex Row**
- **Use For:** Arrange items horizontally
- **Key Properties:**
  - `gap` — Space between items
  - `align` — top, center, bottom
  - `justify` — left, center, right, space-between

**Example:** Button group (Submit | Cancel)

#### **Card**
- **Use For:** Content blocks with visual separation
- **Key Properties:**
  - `padding` — Space inside
  - `shadow` — Drop shadow
  - `hover` — Effect on mouse over

**Example:** Product listings, blog posts

---

### Display Components

#### **Image**
- **Use For:** Photos, logos, icons
- **Key Properties:**
  - `src` — Image URL or file path
  - `alt` — Description for accessibility
  - `width` / `height` — Size
  - `objectFit` — cover, contain, fill

#### **Table**
- **Use For:** Data in rows/columns
- **Key Properties:**
  - `columns` — Column definitions
  - `data` — Row data
  - `sortable` — Click headers to sort
  - `pagination` — Show X rows per page

#### **Chart**
- **Use For:** Visualize data (graphs)
- **Types:** Bar, Line, Pie, Donut
- **Key Properties:**
  - `type` — Chart type
  - `data` — Data to visualize
  - `labels` — Axis labels

---

## Keyboard Shortcuts

Keyboard shortcuts make you **10x faster**!

### Essential Shortcuts (Learn These First!)

| Action | Mac | Windows/Linux | Why Use It |
|--------|-----|---------------|------------|
| **Add Component** | Drag from library | Drag from library | Core skill |
| **Select** | Click | Click | Essential |
| **Delete** | Delete or ⌫ | Delete or Backspace | Quick cleanup |
| **Deselect** | Esc | Esc | Clear selection |
| **Duplicate** | Cmd+D | Ctrl+D | Copy patterns |
| **Undo** | Cmd+Z | Ctrl+Z | Fix mistakes |
| **Redo** | Cmd+Shift+Z | Ctrl+Y | Restore changes |

### Power User Shortcuts

| Action | Mac | Windows/Linux | Why Use It |
|--------|-----|---------------|------------|
| **Search** | Cmd+P | Ctrl+P | Find components fast |
| **Save** | Cmd+S | Ctrl+S | Save work |
| **Preview** | Cmd+Enter | Ctrl+Enter | Quick preview |
| **Copy ID** | Shift+Cmd+C | Shift+Ctrl+C | For developers |
| **Inline Edit** | Enter (on text) | Enter | Quick text edit |

### Pro Tips

💡 **Practice makes perfect** — Use shortcuts for 1 week, they'll become automatic  
💡 **Start with Cmd+D** — Duplicate is the most useful!  
💡 **Cmd+Z is your friend** — Don't be afraid to experiment  

---

## Tips & Best Practices

### Design Tips

#### **1. Start with a Template**
Don't start from scratch! Use templates and customize.

#### **2. Use Consistent Spacing**
- Same padding on all cards
- Same margins between sections
- Creates professional look

#### **3. Limit Colors**
- Pick 2-3 main colors
- Use them consistently
- Too many colors = messy

#### **4. Typography Hierarchy**
- **Headings:** 24-32px, bold
- **Body text:** 16px, normal
- **Small text:** 12-14px

#### **5. White Space is Good**
- Don't fill every pixel
- Empty space helps users focus
- Cluttered = confusing

#### **6. Mobile-First**
- Design for mobile first
- Then adapt for desktop
- Most users are on phones!

### Workflow Tips

#### **Save Often**
- Studio auto-saves every 2 minutes
- But press **Cmd+S** manually to be safe
- Especially before major changes

#### **Use Preview (Enhanced!)**
- Preview often while designing - opens in new tab now!
- See what users will see with full app context
- Test navigation between pages
- Keep preview tab open and refresh after changes
- Catch issues early before publishing

#### **Name Your Pages**
- Use clear names: "Contact Form", "Dashboard"
- Not: "Page 1", "Untitled"
- Easier to find later

#### **Organize Components**
- Use containers to group related items
- Makes editing easier later
- Easier to move sections around

#### **Test Responsive**
- Check Desktop, Tablet, Mobile views
- Some layouts break on mobile
- Fix before publishing

### Common Mistakes to Avoid

❌ **Too much text** — Break into smaller paragraphs  
❌ **Tiny text** — Minimum 14px for body text  
❌ **Not testing mobile** — Always check mobile view  
❌ **Inconsistent spacing** — Use same padding everywhere  
❌ **Forgetting to save** — Save often!  
❌ **No visual hierarchy** — Use size/weight to show importance  

---

## Troubleshooting

### "I can't drop a component!"

**Problem:** Green indicator not showing when dragging

**Solutions:**
1. Make sure you're dragging **into a container** (not empty space)
2. Look for the **green indicator** (line or highlight)
3. Try dropping in different areas:
   - Top 30% = drops before
   - Middle 40% = drops inside (containers only)
   - Bottom 30% = drops after

### "My component disappeared!"

**Problem:** Added component but can't see it

**Solutions:**
1. Press **Cmd+Z** to undo
2. Check if it's **outside the visible area** (scroll)
3. Check if it's **inside a collapsed container** (click to expand)
4. Look in **tree view** (left panel) — it might be there

### "Drag & drop not working!"

**Problem:** Can't drag components at all

**Solutions:**
1. **Refresh the page** (F5)
2. Make sure you're **clicking and holding** on the component
3. Check your **mouse is working**
4. Try a different browser (Chrome, Firefox)

### "Changes not showing up!"

**Problem:** Edited property but nothing changed

**Solutions:**
1. Press **Enter** after typing
2. Click outside the input field
3. Wait 1-2 seconds (sometimes delayed)
4. Refresh preview

### "How do I undo?"

**Solution:** Press **Cmd+Z** (Mac) or **Ctrl+Z** (Windows)

Works for:
- Adding components
- Deleting components
- Editing properties
- Moving components

### "Can't select a component!"

**Problem:** Clicking component but it won't select

**Solutions:**
1. Try clicking **directly on the component** (not near it)
2. Click in the **tree view** (left panel) instead
3. Press **Escape** to deselect first, then try again
4. Component might be **inside a container** — select container first

### "Layout is broken on mobile!"

**Problem:** Page looks good on desktop, bad on mobile

**Solutions:**
1. Use **responsive containers** (Flex Column/Row instead of fixed widths)
2. Test in **mobile view** (click mobile icon at top)
3. Set component widths to **%** instead of **px**
4. Use **Grid** with auto-columns
5. Stack things vertically for mobile

### "Lost my work!"

**Problem:** Page is empty or wrong version

**Solutions:**
1. Check **auto-save** indicator (top right)
2. Click **page dropdown** — might have wrong page open
3. Studio saves every 2 minutes automatically
4. Check with your admin about backups

### "Button does nothing when clicked!"

**Problem:** Button added but doesn't work

**This is normal at this stage!**
- Studio Builder creates the **design/layout** only
- Button **actions** are connected later (by developers or in next phase)
- For now, focus on creating the visual design

---

## Need More Help?

### Quick Reference

- **Documentation Hub:** See `README.md` in docs folder
- **Developer Guide:** If you need technical help
- **Architecture Doc:** Understand how AppBana works

### Get Support

1. **Check this manual first** (use Ctrl+F to search)
2. **Ask your team admin**
3. **Report bugs** to your development team

---

## What's Next?

Now that you know the basics:

1. ✅ **Practice!** — The more you build, the faster you'll get
2. ✅ **Start with templates** — Customize instead of building from scratch
3. ✅ **Learn keyboard shortcuts** — Become a power user
4. ✅ **Experiment!** — Can't break anything, just press Undo
5. ✅ **Share your work** — Show your team what you've built

**Happy building! 🎨**

---

**Document Version:** 1.2  
**Last Updated:** December 28, 2025  
**What's New in v1.2:** Updated Sign Up template with modern 45/55 split layout, full-height brand panel, feature list, two-column name fields (8 templates total)  
**Previous (v1.1):** Pre-built templates (7 templates), enhanced preview with app context, 2-step page creation wizard, 93% time savings!  
**Feedback:** Share your thoughts with the AppBana team!
