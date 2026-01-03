# Visual Test Guide: AppBana UI Reference

**Purpose**: Visual reference guide showing actual AppBana UI for test execution  
**Date**: 2026-01-03  
**Screenshots**: Actual production UI

---

## Screen 1: Post-Login - App Manager

After successful login to `http://localhost:5173/studio`, you'll see:

![App Manager Interface](/Users/dilipupadhyay/.gemini/antigravity/brain/8992e0fb-29af-43c5-9425-d415f1a81796/uploaded_image_1767431300459.png)

### Key UI Elements

**Top Toolbar** (Purple header):
- **App Manager** title (left)
- **HR Management App** (current app badge)
- **Open App** button (white)
- **New App** button (green) ← Use this to create new app
- **Save** button (white)
- **Publish** button (green) ← Publish to pipeline
- **Pipeline** button (white) ← View deployments
- **Theme** button (white)

**Left Panel** - Entity Tabs:
- **User Information** tab (purple, active)
- **New Page** button (green)

**Main Tabs**:
- **Components** tab
- **Entities** tab ← Click here to manage entities
- **Workflow** tab
- **AI Builder** tab

**Center Area**:
- **Visual Canvas** - Where you design pages
- **TEST ADD** button (green, top-right)

**Right Panel**:
- **Properties** panel - Shows component properties when selected

---

## Screen 2: Entities Tab - Entity List

Click **Entities** tab to see entity management:

![Entities Tab](/Users/dilipupadhyay/.gemini/antigravity/brain/8992e0fb-29af-43c5-9425-d415f1a81796/uploaded_image_0_1767431406221.png)

### Key UI Elements

**Entities Panel** (Left sidebar):
- **Entities** tab (active, blue highlight)
- **Search entities...** search box
- **New Entity** button (blue) ← Click to create new entity
- **User Information** entity card
  - Icon: 📦 (box emoji)
  - Edit icon (pencil)
  - Delete icon (trash)
  - Field count: "5 fields"

**Expected for Testing**:
- After creating entities, they appear in this list
- Each entity shows icon, name, and field count
- Click entity name or edit icon to modify

---

## Screen 3: Edit Entity Modal - Basic Info

Click **New Entity** or click existing entity:

![Edit Entity Modal](/Users/dilipupadhyay/.gemini/antigravity/brain/8992e0fb-29af-43c5-9425-d415f1a81796/uploaded_image_1_1767431406221.png)

### Modal Structure

**Header**:
- Title: "Edit Entity"
- Close button (X)

**Form Fields**:

**1. Entity Name** (required, marked with *)
- Technical name (cannot be changed after creation)
- Example: "User"
- Note: "Entity name cannot be changed"

**2. Display Name** (required, marked with *)
- User-friendly name
- Example: "User Information"
- Note: "User-friendly name"

**3. Description**
- Optional text area
- Placeholder: "Describe what this entity represents..."

**4. Icon**
- Emoji or icon selector
- Shows current: 📦
- Note: "Emoji or icon name"

**5. Datasource**
- Dropdown selection
- Default: "Default"

**Expandable Sections**:
- **📋 Fields (5)** ← Expand to add/edit fields
- **🔗 Relationships (0)** ← For entity relationships
- **📊 SQL Preview** ← View generated SQL

**Footer Buttons**:
- **Cancel** (gray)
- **Update Entity** (blue, right) ← Save changes

---

## Screen 4: Fields Section - Field Configuration

Click **Fields (5)** to expand:

![Fields Configuration](/Users/dilipupadhyay/.gemini/antigravity/brain/8992e0fb-29af-43c5-9425-d415f1a81796/uploaded_image_2_1767431406221.png)

### Fields List

Each field shows:
- **Field Name** (left)
- **Type** (dropdown - Text, Email, etc.)
- **Required** checkbox (checked = required)
- **Settings** icon (gear)

**Example Fields**:

1. **id**
   - Type: Auto Increment
   - Required: ✓ (checked)

2. **Name**
   - Type: Text dropdown
   - Required: ✓ (checked)

3. **Email**
   - Type: Text dropdown
   - Required: ✓ (checked)

4. **Phone**
   - Type: Text dropdown
   - Required: ☐ (unchecked) ← Optional field

5. **Address**
   - Type: Text dropdown
   - Required: ☐ (unchecked)

**Action Button**:
- **+ Add Field** link (bottom)

### Field Types Available
Based on dropdown, types include:
- Auto Increment (for IDs)
- Text
- Email
- Number
- Date
- Boolean
- And more...

---

## Screen 5: SQL Preview - Generated Schema

Click **SQL Preview** to see generated SQL:

![SQL Preview](/Users/dilipupadhyay/.gemini/antigravity/brain/8992e0fb-29af-43c5-9425-d415f1a81796/uploaded_image_3_1767431406221.png)

### SQL Display

Shows auto-generated CREATE TABLE statement:

```sql
CREATE TABLE User (
  id VARCHAR(255) NOT NULL PRIMARY KEY AUTO_INCREMENT,
  Name VARCHAR(255) NOT NULL,
  Email VARCHAR(255) NOT NULL,
  Phone VARCHAR(255),
  Address VARCHAR(255),
  deleted BOOLEAN,
  version VARCHAR(255) NOT NULL
);
```

**Key Points**:
- Auto-generated based on fields
- Shows data types (VARCHAR, BOOLEAN)
- Shows constraints (NOT NULL, PRIMARY KEY)
- Includes system fields (deleted, version)

---

## Entity Creation Flow - Step by Step

### Scenario 3: Entity Creation (Updated)

**Step 1: Navigate to Entities**
1. Click **Entities** tab in left sidebar
2. You'll see entity list (initially empty or with existing entities)

**Step 2: Click New Entity**
1. Click **New Entity** button (blue)
2. **Edit Entity** modal appears

**Step 3: Fill Basic Information**
```
Entity Name: User
Display Name: User Information
Description: User contact information
Icon: 📦 (leave default or change)
Datasource: Default
```

**Step 4: Add Fields**
1. Click **Fields (0)** to expand
2. Click **+ Add Field**
3. Add fields one by one:

Field 1:
- Name: id
- Type: Auto Increment
- Required: ✓

Field 2:
- Name: Name
- Type: Text
- Required: ✓

Field 3:
- Name: Email
- Type: Text (or Email type if available)
- Required: ✓

Field 4:
- Name: Phone
- Type: Text
- Required: ☐ (leave unchecked)

Field 5:
- Name: Address
- Type: Text
- Required: ☐ (leave unchecked)

**Step 5: Review SQL (Optional)**
1. Click **SQL Preview** to expand
2. Verify generated SQL looks correct
3. Check field names, types, and constraints

**Step 6: Save Entity**
1. Click **Update Entity** button (blue)
2. Modal closes
3. Entity appears in entity list with "5 fields"

**Step 7: Verify**
1. Entity visible in left panel
2. Shows icon 📦
3. Displays "User Information"
4. Shows "5 fields" count
5. Can click to edit again

---

## Key Differences from Original Test Plan

### What Changed:

**Original Assumption**:
- Separate "Create" and "Edit" modals
- Fields added in separate dialog
- Display labels configured per field

**Actual UI**:
- Single "Edit Entity" modal for both create and edit
- Entity Name locked after creation
- Fields configured inline with expandable section
- Simple Name/Type/Required interface
- SQL Preview available for verification

### For Testers:

✅ **Use these screenshots as reference**  
✅ **Follow the actual UI flow shown**  
✅ **Entity creation is simpler than test plan described**  
✅ **All configuration in one modal**

---

## Field Name Display - The Key Test

### Critical Test Point

When binding form inputs to entity fields, the dropdown should show:

**Expected** (after our fix):
- Name (text)
- Email (text)
- Phone (text)
- Address (text)

**NOT** (the bug we fixed):
- Field 1 (text)
- Field 2 (text)
- field1 (text)

### How to Verify:

1. Create entity with fields (as shown above)
2. Create a page with form
3. Add Input component
4. In Properties panel, select **Entity Binding**
5. Choose Entity: "User Information"
6. Check Field dropdown ← **CRITICAL CHECK**
7. Should show: "Name (text)", "Email (text)", etc.
8. Should NOT show: "field1", "Field 1", etc.

---

## Testing Tips

### Navigation
- **Entities Tab**: Manage all entities
- **Components Tab**: Add form elements to pages
- **Properties Panel**: Configure selected components
- **Top Toolbar**: App-level actions (Save, Publish, Pipeline)

### Keyboard Shortcuts
- **Tab**: Navigate between fields in modal
- **Enter**: Submit form (careful in modals)
- **Esc**: Close modal (usually)

### Common Actions
- **Create Entity**: Entities tab → New Entity button
- **Edit Entity**: Click entity name or edit icon
- **Add Field**: Expand Fields section → + Add Field
- **Save**: Update Entity button (entity modal) or Save button (top toolbar)
- **Publish**: Publish button (top toolbar)
- **View Pipeline**: Pipeline button (top toolbar)

---

## Visual Checklist for Testers

After login, verify you see:
- [ ] Purple top toolbar with app name
- [ ] Left panel with entity/page tabs
- [ ] Center visual canvas
- [ ] Right properties panel
- [ ] Green "New App" and "Publish" buttons

In Entities tab:
- [ ] Entity list on left
- [ ] "New Entity" blue button
- [ ] Search entities box
- [ ] Existing entities with icons and field counts

In Edit Entity modal:
- [ ] Entity Name and Display Name fields
- [ ] Expandable Fields section
- [ ] Expandable SQL Preview
- [ ] Update Entity button (blue)

When adding fields:
- [ ] + Add Field link appears
- [ ] Each field shows Name, Type, Required
- [ ] Can set Required checkbox
- [ ] Can change field type from dropdown

---

**Screenshot Credits**: Actual AppBana UI (Development environment)  
**Date Captured**: 2026-01-03  
**Version**: Current development build  
**Use**: Reference for test execution and automation
