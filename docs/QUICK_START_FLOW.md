# AppBana Quick Start Flow

## 📊 Visual Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                        APPBANA CREATION FLOW                         │
└─────────────────────────────────────────────────────────────────────┘

Step 1: CREATE APP
├─ Open Studio (http://localhost:5173/studio.html)
├─ Click "AI Builder" tab
└─ Say: "Create a project management app"
          ↓
          ↓ AI generates app structure
          ↓
┌─────────────────────────────────────────────────────────────────────┐
│ App Metadata Created:                                                │
│ - appId: "project-management"                                        │
│ - appName: "Project Management App"                                 │
│ - File: apps/project-management/app.json                            │
└─────────────────────────────────────────────────────────────────────┘
          ↓
          ↓
Step 2: CREATE ENTITIES
├─ AI generates entities from your description
├─ Each entity becomes a database table
└─ Fields mapped to SQL columns
          ↓
┌─────────────────────────────────────────────────────────────────────┐
│ Entity: Project                                                      │
│ ├─ id (BIGINT, auto-generated)                                      │
│ ├─ name (VARCHAR, required)                                         │
│ ├─ description (TEXT)                                               │
│ ├─ startDate (DATE, required)                                       │
│ ├─ budget (DECIMAL)                                                 │
│ ├─ status (VARCHAR, required)                                       │
│ └─ clientId (BIGINT, foreign key)                                   │
│                                                                      │
│ Entity: Task                                                         │
│ ├─ id (BIGINT, auto-generated)                                      │
│ ├─ title (VARCHAR, required)                                        │
│ ├─ description (TEXT)                                               │
│ ├─ status (VARCHAR)                                                 │
│ ├─ dueDate (DATE)                                                   │
│ ├─ projectId (BIGINT, foreign key → Project)                       │
│ └─ assignedTo (BIGINT, foreign key → TeamMember)                   │
│                                                                      │
│ Entity: TeamMember                                                   │
│ ├─ id (BIGINT, auto-generated)                                      │
│ ├─ name (VARCHAR, required)                                         │
│ ├─ email (VARCHAR, required)                                        │
│ ├─ phone (VARCHAR)                                                  │
│ └─ role (VARCHAR)                                                   │
└─────────────────────────────────────────────────────────────────────┘
          ↓
          ↓ Platform auto-creates
          ↓
┌─────────────────────────────────────────────────────────────────────┐
│ DATABASE TABLES                                                      │
│ ✓ CREATE TABLE project (...)                                        │
│ ✓ CREATE TABLE task (...)                                           │
│ ✓ CREATE TABLE team_member (...)                                    │
│ ✓ Foreign keys configured                                           │
│ ✓ Indexes created                                                   │
└─────────────────────────────────────────────────────────────────────┘
          ↓
          ↓ Platform auto-creates
          ↓
┌─────────────────────────────────────────────────────────────────────┐
│ REST API ENDPOINTS                                                   │
│                                                                      │
│ Project APIs:                                                        │
│ ✓ GET    /api/apps/{appId}/data/Project                            │
│ ✓ GET    /api/apps/{appId}/data/Project/{id}                       │
│ ✓ POST   /api/apps/{appId}/data/Project                            │
│ ✓ PUT    /api/apps/{appId}/data/Project/{id}                       │
│ ✓ DELETE /api/apps/{appId}/data/Project/{id}                       │
│                                                                      │
│ Task APIs:                                                           │
│ ✓ GET    /api/apps/{appId}/data/Task                               │
│ ✓ POST   /api/apps/{appId}/data/Task                               │
│ ✓ PUT    /api/apps/{appId}/data/Task/{id}                          │
│ ✓ DELETE /api/apps/{appId}/data/Task/{id}                          │
│                                                                      │
│ TeamMember APIs:                                                     │
│ ✓ GET    /api/apps/{appId}/data/TeamMember                         │
│ ✓ POST   /api/apps/{appId}/data/TeamMember                         │
│ ✓ PUT    /api/apps/{appId}/data/TeamMember/{id}                    │
│ ✓ DELETE /api/apps/{appId}/data/TeamMember/{id}                    │
└─────────────────────────────────────────────────────────────────────┘
          ↓
          ↓
Step 3: CREATE PAGES
├─ AI generates page definitions
├─ Each page has type, entity, fields, actions
└─ Pages auto-link to entities
          ↓
┌─────────────────────────────────────────────────────────────────────┐
│ Page: project-list (data-table)                                     │
│ ├─ Entity: Project                                                  │
│ ├─ Columns: [name, status, startDate, budget]                      │
│ ├─ Actions: [view, edit, delete, create]                           │
│ └─ File: apps/project-management/pages/project-list.json           │
│                                                                      │
│ Page: task-board (board/kanban)                                     │
│ ├─ Entity: Task                                                     │
│ ├─ Group By: status                                                 │
│ ├─ Card Fields: [title, priority, dueDate, assignedTo]            │
│ └─ File: apps/project-management/pages/task-board.json             │
│                                                                      │
│ Page: project-form (form)                                           │
│ ├─ Entity: Project                                                  │
│ ├─ Fields:                                                          │
│ │   ├─ name → <studio-input type="text">                           │
│ │   ├─ description → <studio-textarea>                             │
│ │   ├─ startDate → <studio-input type="date">                      │
│ │   ├─ budget → <studio-input type="number">                       │
│ │   ├─ status → <studio-select>                                    │
│ │   └─ clientId → <studio-select datasource="Client">             │
│ └─ File: apps/project-management/pages/project-form.json           │
│                                                                      │
│ Page: team-directory (data-table)                                   │
│ ├─ Entity: TeamMember                                               │
│ ├─ Columns: [name, email, role]                                    │
│ └─ File: apps/project-management/pages/team-directory.json         │
└─────────────────────────────────────────────────────────────────────┘
          ↓
          ↓ Runtime renders
          ↓
┌─────────────────────────────────────────────────────────────────────┐
│ UI COMPONENTS (Auto-rendered from metadata)                          │
│                                                                      │
│ project-list page renders:                                          │
│ <data-table-element                                                 │
│   entityName="Project"                                              │
│   columns='["name", "status", "startDate", "budget"]'              │
│   actions='["view", "edit", "delete", "create"]'>                  │
│ </data-table-element>                                               │
│                                                                      │
│ project-form page renders:                                          │
│ <studio-input type="text" label="Name" required>                   │
│ <studio-textarea label="Description" rows="5">                     │
│ <studio-input type="date" label="Start Date" required>            │
│ <studio-input type="number" label="Budget">                        │
│ <studio-select label="Status" options='[...]'>                     │
│ <studio-button variant="primary">Create Project</studio-button>    │
│                                                                      │
│ task-board page renders:                                            │
│ <kanban-board-element                                               │
│   entityName="Task"                                                 │
│   groupByField="status">                                            │
│ </kanban-board-element>                                             │
└─────────────────────────────────────────────────────────────────────┘
          ↓
          ↓
Step 4: PREVIEW & TEST
├─ Click "Preview" button in Studio
├─ App opens in runtime shell
└─ Test all CRUD operations
          ↓
┌─────────────────────────────────────────────────────────────────────┐
│ RUNNING APPLICATION                                                  │
│                                                                      │
│ ┌─────────────────────────────────────────────────────────┐        │
│ │ Project Management App                          [Menu] │        │
│ ├─────────────────────────────────────────────────────────┤        │
│ │                                                           │        │
│ │  Projects                                                │        │
│ │  ┌──────────────┬──────────┬────────────┬──────────┐   │        │
│ │  │ Name         │ Status   │ Start Date │ Budget   │   │        │
│ │  ├──────────────┼──────────┼────────────┼──────────┤   │        │
│ │  │ Website      │ Active   │ 2025-01-15 │ $50,000 │   │        │
│ │  │ Mobile App   │ Planning │ 2025-02-01 │ $80,000 │   │        │
│ │  │ Marketing    │ Active   │ 2025-01-10 │ $30,000 │   │        │
│ │  └──────────────┴──────────┴────────────┴──────────┘   │        │
│ │                                                           │        │
│ │  [+ New Project]  [Edit]  [Delete]  [View Details]     │        │
│ │                                                           │        │
│ └───────────────────────────────────────────────────────────┘        │
│                                                                      │
│ ✓ Create new projects                                               │
│ ✓ Edit existing projects                                            │
│ ✓ Delete projects                                                   │
│ ✓ View project details                                              │
│ ✓ Navigate to task board                                            │
│ ✓ Manage team members                                               │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 🎯 Simple 4-Step Flow

```
1. CREATE APP
   └─ "Create a [domain] app"
        ↓
2. ENTITIES AUTO-GENERATED
   ├─ Database tables created
   ├─ REST APIs created
   └─ Relationships configured
        ↓
3. PAGES AUTO-GENERATED
   ├─ List pages (data tables)
   ├─ Form pages (create/edit)
   ├─ Detail pages (view)
   └─ Dashboards/boards
        ↓
4. PREVIEW & USE
   └─ Fully functional app ready!
```

---

## 📋 Detailed Step-by-Step

### **Step 1: Create App**
```bash
User Input: "Create a project management app"
```

**AI Response:**
```json
{
  "appId": "project-management",
  "appName": "Project Management App",
  "appDescription": "Manage projects, tasks, and team members"
}
```

**Result:**
- ✅ App folder created: `apps/project-management/`
- ✅ App metadata: `apps/project-management/app.json`

---

### **Step 2: Entities Generated**

**From User Description → AI Extracts Entities:**

```json
{
  "entities": [
    {
      "name": "Project",
      "fields": [
        {"name": "name", "type": "text", "required": true},
        {"name": "description", "type": "longtext"},
        {"name": "startDate", "type": "date", "required": true},
        {"name": "budget", "type": "currency"},
        {"name": "status", "type": "status", "required": true}
      ]
    },
    {
      "name": "Task",
      "fields": [
        {"name": "title", "type": "text", "required": true},
        {"name": "description", "type": "longtext"},
        {"name": "status", "type": "status"},
        {"name": "dueDate", "type": "date"},
        {"name": "projectId", "type": "long", "required": true}
      ]
    },
    {
      "name": "TeamMember",
      "fields": [
        {"name": "name", "type": "text", "required": true},
        {"name": "email", "type": "email", "required": true},
        {"name": "role", "type": "text"}
      ]
    }
  ]
}
```

**Platform Auto-Creates:**

1. **Database Tables**
   ```sql
   CREATE TABLE project (id, name, description, start_date, budget, status);
   CREATE TABLE task (id, title, description, status, due_date, project_id);
   CREATE TABLE team_member (id, name, email, role);
   ```

2. **REST APIs** (auto-generated for each entity)
   ```
   GET/POST    /api/apps/project-management/data/Project
   GET/PUT/DEL /api/apps/project-management/data/Project/{id}
   GET/POST    /api/apps/project-management/data/Task
   GET/POST    /api/apps/project-management/data/TeamMember
   ```

3. **Relationships** (inferred from foreign keys)
   ```
   Task.projectId → Project.id (many-to-one)
   ```

---

### **Step 3: Pages Generated**

**AI Creates Page Metadata:**

```json
{
  "pages": [
    {
      "id": "project-list",
      "name": "Projects",
      "type": "data-table",
      "entity": "Project",
      "columns": ["name", "status", "startDate", "budget"],
      "actions": ["view", "edit", "delete", "create"]
    },
    {
      "id": "project-form",
      "name": "New Project",
      "type": "form",
      "entity": "Project",
      "fields": [
        {"name": "name", "component": "input", "type": "text", "required": true},
        {"name": "description", "component": "textarea"},
        {"name": "startDate", "component": "input", "type": "date", "required": true},
        {"name": "budget", "component": "input", "type": "number"},
        {"name": "status", "component": "select", "options": ["Planning", "Active", "Completed"]}
      ]
    },
    {
      "id": "task-board",
      "name": "Task Board",
      "type": "board",
      "entity": "Task",
      "groupBy": "status"
    }
  ]
}
```

**Runtime Renders:**
- `project-list` → `<data-table-element>` with columns
- `project-form` → Form with `<studio-input>`, `<studio-textarea>`, `<studio-select>`
- `task-board` → `<kanban-board-element>` with drag-drop

---

### **Step 4: Preview & Test**

Click **"Preview"** button:

1. **App Shell Loads**
   - Navigation menu with all pages
   - Runtime shell at `/runtime/`

2. **Pages Render**
   - Data table shows records from database
   - Form inputs create new records
   - Board displays tasks grouped by status

3. **Full CRUD Works**
   - ✅ Create: Fill form → Submit → POST to API → Insert to DB
   - ✅ Read: Load page → GET from API → Display records
   - ✅ Update: Edit form → Submit → PUT to API → Update DB
   - ✅ Delete: Click delete → DELETE to API → Remove from DB

---

## 🔄 Complete Data Flow

```
User Action          →  Frontend          →  Backend           →  Database
─────────────────────────────────────────────────────────────────────────────
"Create project"    →  <form> submits    →  POST /data/Project → INSERT INTO project
                       with JSON data        validates fields      (id, name, status, ...)

"View projects"     →  <data-table>      →  GET /data/Project  → SELECT * FROM project
                       loads                 returns JSON array    WHERE ...

"Edit project"      →  <form> submits    →  PUT /data/Project/5→ UPDATE project
                       with changes          validates updates     SET ... WHERE id=5

"Delete project"    →  <button> clicks   →  DELETE /data/.../5 → DELETE FROM project
                       confirmation          cascades to tasks     WHERE id=5
```

---

## 💡 Key Points

### **Entity → Everything**
One entity definition creates:
- ✅ Database table with columns
- ✅ REST API endpoints (GET, POST, PUT, DELETE)
- ✅ Data model for queries
- ✅ Validation rules
- ✅ Form field mappings

### **Page Metadata → UI**
One page definition creates:
- ✅ Component tree (buttons, inputs, tables)
- ✅ Data bindings (entity → component props)
- ✅ Event handlers (submit → API call)
- ✅ Navigation links

### **Zero Code Required**
Everything is metadata:
- ✅ No Java code for APIs
- ✅ No SQL scripts for tables
- ✅ No TypeScript for components
- ✅ Just JSON metadata!

---

## 📁 File Structure After Creation

```
apps/project-management/
├─ app.json                    # App metadata
│   ├─ id, name, description
│   └─ entities: [Project, Task, TeamMember]
│
└─ pages/
    ├─ project-list.json       # Data table page
    │   └─ type: "data-table", entity: "Project"
    │
    ├─ project-form.json       # Create/edit form
    │   └─ type: "form", fields with components
    │
    ├─ task-board.json         # Kanban board
    │   └─ type: "board", groupBy: "status"
    │
    └─ team-directory.json     # Team list
        └─ type: "data-table", entity: "TeamMember"
```

---

## 🎯 That's It!

**Input**: Natural language description  
**Output**: Fully functional enterprise application

**Time**: 2-5 minutes  
**Code Written**: 0 lines  
**Result**: Database + APIs + UI + CRUD operations

**The entire flow is metadata-driven, from app creation to entity generation to page rendering!** 🚀
