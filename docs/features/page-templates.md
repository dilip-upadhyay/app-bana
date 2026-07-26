> **📚 HISTORICAL DOCUMENT.** Describes the canvas-era Studio's page-template picker. In the AI-Native rebuild, pages are generated on demand by [`GeneratePageTool`](../../ai-builder/src/main/java/com/appbana/ai/agent/tool/GeneratePageTool.java) in the AI Builder — the user describes what they want in chat, no template picker required.
>
> **New compound page types (wizard, master-detail, list-with-filters) are being added in [Phase B (Complex UI Plan)](../planning/COMPLEX_UI_PLAN.md).**
>
> **See:** [`docs/README.md`](../README.md) for the full documentation currency table.

---

# AppBana Page Templates

This directory contains **system templates** - pre-built page layouts that users can select when creating new pages in the AppBana Studio Builder.

## Overview

AppBana uses a **metadata-driven template system** where templates are JSON files containing component node trees. These templates are loaded by the backend `TemplateService` and served via REST API to the frontend.

## Architecture

```
Backend (Java)
├── TemplateService.java           # Template management service
│   ├── /resources/page-templates/  # System templates (this directory)
│   └── data/user-templates/        # User-created templates
│
└── AppRoutes.java                  # REST API endpoints

Frontend (TypeScript)
├── TemplateStore.ts                # Fetches templates from API
├── PageManager.ts                  # Displays template gallery
└── BuilderCanvas.ts                # Renders selected template
```

## Template Structure

Each template is a JSON file with the following structure:

```json
{
  "id": "template-id",              // Unique identifier (lowercase, hyphenated)
  "name": "Template Name",          // Display name
  "description": "Description...",  // Short description for UI
  "category": "auth|dashboard|...", // Category for organization
  "isSystem": true,                 // true for system, false for user
  "nodes": [                        // Component node tree
    {
      "id": "root",                 // Root node (required)
      "type": "container",          // Component type
      "props": {                    // Component properties
        "style": "display: flex; ..."
      },
      "children": ["child-1", "child-2"]  // Child node IDs
    },
    {
      "id": "child-1",
      "type": "text",
      "props": {
        "tag": "h1",
        "text": "Welcome",
        "style": "font-size: 2rem; ..."
      }
    }
    // ... more nodes
  ]
}
```

## Available Templates

| Template | File | Description | Use Case |
|----------|------|-------------|----------|
| **Login** | `login.json` | Side-by-side login page with brand panel (45/55 split) | User authentication |
| **Sign Up** | `signup.json` | Modern registration with feature highlights | User registration |
| **Dashboard** | `dashboard.json` | Admin dashboard with sidebar and KPI cards | Admin panels |
| **Contact** | `contact.json` | Contact form with name, email, message | Customer support |
| **Landing** | `landing.json` | Marketing landing page with hero, features, CTA | Product marketing |
| **Profile** | `profile.json` | User profile with avatar, bio, stats | User profiles |
| **Data Table** | `data-table.json` | Table with search, filters, pagination | Reports, lists |

## Template Design Guidelines

### 1. Responsive Layout
- Use `flex` or `grid` for responsive layouts
- Always specify `min-height: 100vh` for full-screen pages
- Include responsive breakpoints in `@media` styles (if supported)
- Test on desktop (1920px), tablet (768px), mobile (375px)

### 2. Two-Column Layouts (Login/Signup)
- **Recommended split**: 45% left panel / 55% right form
- **Left panel**: Brand messaging, features, gradient background
- **Right panel**: Form with proper spacing, white background
- **Mobile**: Stack vertically, hide features if needed

### 3. Forms
- **Labels**: 0.9rem font, 500 weight, proper color contrast
- **Inputs**: 
  - 2px borders with #e2e8f0 default color
  - 8px border radius
  - 0.75rem padding
  - Focus states with color change
- **Buttons**: 
  - Full width or auto depending on context
  - Gradient backgrounds for primary actions
  - 8px border radius
  - Hover/active states

### 4. Color Palette
- **Primary gradient**: `linear-gradient(135deg, #667eea 0%, #764ba2 100%)`
- **Text colors**: 
  - Headings: `#1a202c` (dark)
  - Body: `#2d3748` (medium)
  - Muted: `#718096` (gray)
- **Borders**: `#e2e8f0` (light gray)
- **Background**: `white` or `#f7fafc` (light)

### 5. Typography
- **Headings**: 
  - H1: 1.75-2rem, 700 weight
  - H2: 1.5rem, 600 weight
  - H3: 1.25rem, 600 weight
- **Body**: 1rem (16px), 400 weight
- **Small**: 0.875-0.9rem, 400 weight
- **Line height**: 1.5-1.6 for body, 1.2 for headings

### 6. Spacing
- **Section padding**: 2.5-3rem
- **Form gaps**: 1.5rem between fields
- **Element gaps**: 0.5-1rem
- **Max widths**: 
  - Form containers: 480px
  - Content sections: 1200px

## REST API Endpoints

### GET /api/templates
Returns all templates (system + user).

**Response:**
```json
[
  {
    "id": "login",
    "name": "Login Page",
    "description": "Side-by-side login...",
    "category": "auth",
    "isSystem": true,
    "nodes": [...]
  },
  ...
]
```

### GET /api/templates/{id}
Returns a specific template by ID.

**Response:**
```json
{
  "id": "login",
  "name": "Login Page",
  "nodes": [...]
}
```

### POST /api/templates
Creates a new user template.

**Request:**
```json
{
  "name": "My Custom Template",
  "description": "Custom page layout",
  "category": "custom",
  "nodes": [...]
}
```

**Response:** Created template with auto-generated ID.

### PUT /api/templates/{id}
Updates an existing user template (cannot update system templates).

### DELETE /api/templates/{id}
Deletes a user template (cannot delete system templates).

## Frontend Integration

### TemplateStore Usage

```typescript
import { templateStore } from '../store/TemplateStore';

// Load all templates
const templates = await templateStore.loadTemplates();

// Get specific template
const loginTemplate = templateStore.getTemplate('login');

// Create user template
const newTemplate = await templateStore.createTemplate({
  name: 'My Custom Page',
  description: 'Custom layout',
  category: 'user',
  nodes: [...]
});
```

### PageManager Integration

The `PageManager` component automatically:
1. Loads templates on initialization
2. Displays template gallery in step 2 of page creation wizard
3. Applies selected template to new page
4. Saves page with template nodes

## Creating New System Templates

1. **Create JSON file** in this directory: `my-template.json`
2. **Follow the structure** shown above
3. **Add to TemplateService.java**: Update `systemTemplateIds` array:
   ```java
   String[] systemTemplateIds = { 
     "login", "signup", "dashboard", "contact", 
     "landing", "profile", "data-table", "my-template"  // Add here
   };
   ```
4. **Add icon mapping** in `PageManager.ts`:
   ```typescript
   private getTemplateIcon(templateId: string): string {
     const icons: Record<string, string> = {
       'login': '🔐',
       'signup': '📝',
       // ...
       'my-template': '🎨'  // Add here
     };
     return icons[templateId] || '📄';
   }
   ```
5. **Test**: Restart backend, refresh frontend, check template appears in gallery

## Testing Templates

### Backend Test
```bash
# List all templates
curl http://localhost:8080/api/templates

# Get specific template
curl http://localhost:8080/api/templates/signup
```

### Frontend Test
1. Open Studio Builder: `http://localhost:5173/studio`
2. Create new app
3. Click "New Page" → Step 2 should show template gallery
4. Select template → verify preview renders correctly

## Troubleshooting

### Template not appearing in UI
- Check `TemplateService.java` includes template ID in `systemTemplateIds`
- Verify JSON file exists in `/resources/page-templates/`
- Restart backend to reload resources
- Check browser console for errors

### Template renders incorrectly
- Validate JSON structure (use JSONLint)
- Verify all node IDs are unique
- Check `children` arrays reference existing node IDs
- Ensure `root` node exists and is referenced

### Form not submitting
- Verify form has `tag: "form"` in props
- Check button has `type: "submit"`
- Ensure inputs have proper `name` attributes
- Add event handlers in custom components if needed

## Best Practices

✅ **DO**:
- Keep templates focused on single use case
- Use semantic HTML tags (`h1`, `form`, `label`)
- Include descriptive node IDs (`firstname-input-1`, not `input-1`)
- Test on multiple screen sizes
- Document complex layouts

❌ **DON'T**:
- Hardcode data (use placeholders)
- Create overly complex nested structures
- Use inline JavaScript (not supported)
- Mix presentation and logic
- Create duplicate system templates

## Migration Notes

### From Hardcoded HTML to Metadata Templates

The old approach used standalone HTML files (e.g., `registration-test.html`) with inline CSS and JavaScript. This was **not part of the metadata system** and couldn't be managed through the Studio Builder.

**Problems with old approach:**
- ❌ No integration with AppBana's component system
- ❌ Not accessible via template API
- ❌ Cannot be modified in Studio Builder
- ❌ Not stored in database
- ❌ Not versioned with apps

**New metadata-driven approach:**
- ✅ Full integration with Studio Builder
- ✅ Served via REST API
- ✅ Editable in visual editor
- ✅ Stored with app metadata
- ✅ Version controlled

## See Also

- [TemplateService.java](../../java/com/appbana/service/TemplateService.java) - Backend service
- [TemplateStore.ts](../../../app-bana-ui/src/builder/store/TemplateStore.ts) - Frontend store
- [PageManager.ts](../../../app-bana-ui/src/builder/components/PageManager.ts) - UI component
- [01-ARCHITECTURE.md](../../../../docs/01-ARCHITECTURE.md) - System architecture
- [02-DEVELOPMENT_GUIDE.md](../../../../docs/02-DEVELOPMENT_GUIDE.md) - Development guide

---

**Last Updated:** December 28, 2025  
**Status:** Active  
**Maintainer:** AppBana Development Team
