# AppBana Studio - Drag & Drop UI Builder Guide

## 🎨 New Visual Builder Interface

The Studio UI has been completely redesigned with a modern drag-and-drop interface that removes the tree structure and provides a true WYSIWYG (What You See Is What You Get) experience.

## 📐 Layout Overview

The new interface has a clean 3-panel layout:

```
┌─────────────────────────────────────────────────────────┐
│                    Page Manager                          │
│              (Templates & Page Selection)                │
├──────────────┬───────────────────────┬──────────────────┤
│              │                       │                  │
│  Component   │   Visual Canvas       │   Properties     │
│  Library     │   (Drag & Drop)       │   Inspector      │
│              │                       │                  │
│  📦 Layouts  │   [Your Page Here]    │   Edit Selected  │
│  📝 Forms    │                       │   Component      │
│  📊 Data     │   Drop components     │                  │
│  🎨 Content  │   to build your UI    │   • Props        │
│              │                       │   • Styles       │
│              │                       │   • Events       │
└──────────────┴───────────────────────┴──────────────────┘
```

## 🚀 Quick Start

### 1. Choose a Template
- Click the **"📋 Templates"** button at the top
- Select from pre-built templates:
  - **🔐 Login Page** - Authentication form
  - **📝 Registration Form** - User signup
  - **📊 Dashboard** - Admin dashboard with stats
  - **🚀 Landing Page** - Marketing page with hero section

### 2. Drag & Drop Components

#### Component Categories:

**📦 Layout Components:**
- Container - Generic wrapper
- Flex Row - Horizontal layout
- Flex Column - Vertical layout
- Grid - CSS Grid layout
- Section - Semantic section

**📝 Form Components:**
- Text Input - Single line input
- Text Area - Multi-line input
- Button - Action button
- Checkbox - Boolean input
- Select - Dropdown

**🎨 Content Components:**
- Text - Paragraph/heading
- Heading - H1, H2, etc.
- Image - Pictures
- Link - Hyperlinks

**📊 Data Components:**
- Table - Data tables
- List - Unordered lists
- Card - Styled card container

### 3. How to Drag & Drop

1. **Select a component** from the Component Library (left panel)
2. **Drag it** to the Visual Canvas (center panel)
3. **Watch for drop indicators**:
   - 🟢 **Green line at top** = Drop before element
   - 🟢 **Green line at bottom** = Drop after element
   - 🟢 **Green highlight** = Drop inside container

4. **Release** to add the component
5. **Click** on the component to select and edit it

### 4. Visual Feedback

- **Blue border** = Hovered element
- **Purple border** = Selected element
- **Green highlight** = Valid drop zone
- **Dashed border** = Empty container (ready for drops)

## 💡 Tips & Tricks

### Building a Form
1. Drag a **Container** or **Flex Column** to the canvas
2. Drag **Text Input** components inside it
3. Add a **Button** at the bottom
4. Select each element to customize in the Properties panel

### Creating Layouts
1. Start with a **Flex Row** or **Grid** container
2. Drag multiple components inside
3. They'll automatically arrange based on the layout type

### Nesting Components
- **Containers** can hold other containers
- Drop components **inside** containers for nested layouts
- Use the **before/after** drop positions to add siblings

### Empty Containers
- Empty containers show a **"Drop components here"** hint
- They're larger and easier to target with drag-and-drop
- Perfect starting points for building sections

## 🎯 Drop Zone Behavior

### Smart Drop Detection
The canvas intelligently detects where you want to drop:

- **Container elements**: Prefer "inside" drops (middle area)
- **Leaf elements** (buttons, inputs): Prefer "before/after" (top/bottom edges)
- **Edge zones**: Top 30% and bottom 30% trigger before/after
- **Center zone**: Middle 40% triggers inside (for containers)

## 🔧 Page Templates Explained

### Login Page
Perfect for authentication. Includes:
- Centered card layout
- Email input
- Password input
- Sign-in button
- Gradient background

### Registration Form
User signup form with:
- First/Last name fields
- Email input
- Password fields
- Submit button
- Modern styling

### Dashboard
Admin interface with:
- Header bar
- Stats grid (4 metrics)
- Chart section
- Professional layout

### Landing Page
Marketing page with:
- Hero section (gradient background)
- Feature grid (3 features)
- Call-to-action button
- Responsive design

## 🎨 Customization

### Editing Properties
1. Click any element on the canvas
2. Properties panel (right) shows editable properties
3. Modify text, styles, classes, etc.
4. Changes appear instantly

### Styling
Each component supports:
- **className** - CSS class names
- **style** - Inline CSS
- Custom props per component type

## ⌨️ Keyboard Shortcuts

- **Cmd/Ctrl + P** - Open component palette (from tree view)
- **Cmd/Ctrl + D** - Duplicate selected component
- **Delete/Backspace** - Remove selected component
- **Escape** - Deselect

## 🐛 Troubleshooting

### Can't drop components?
- Make sure you're dragging from the Component Library
- Look for the green drop indicators
- Try dropping on the root container first

### Component not visible?
- Click on the canvas to select elements
- Empty containers have a dashed border
- Check if the component is inside a hidden container

### Drop in wrong position?
- Drag slowly to see drop indicators clearly
- Green line at top = before
- Green line at bottom = after
- Green fill = inside

## 🎯 Best Practices

1. **Start with containers** - Build structure first
2. **Use templates** - Quick start for common pages
3. **Test drop zones** - Hover to see where components will go
4. **Nest wisely** - Use containers for grouping related elements
5. **Name components** - Use meaningful IDs for organization

## 🔄 What Changed?

### Removed:
- ❌ Tree structure view (complex, not visual)
- ❌ Token panel (moved to properties)
- ❌ Text-based editing

### Added:
- ✅ Visual drag-and-drop canvas
- ✅ Real-time WYSIWYG preview
- ✅ Page templates
- ✅ Clear drop indicators
- ✅ Better visual feedback

## 📝 Next Steps

1. **Try a template** - Click "📋 Templates" and load one
2. **Experiment** - Drag components around
3. **Build your page** - Start with a container, add components
4. **Customize** - Select elements and edit properties

Happy building! 🚀

