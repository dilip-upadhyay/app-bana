package com.appbana.ai.knowledge;

import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Comprehensive knowledge loader for AppBana AI Builder
 * Loads all platform capabilities into the vector database for RAG-based answers
 * 
 * Categories:
 * - Platform Architecture & Concepts
 * - Entity & Field Types
 * - UI Components
 * - Page Templates
 * - API Endpoints
 * - Security Features
 * - Workflow Automation
 * - Multi-Tenancy
 * - Best Practices
 * - Troubleshooting
 */
@Slf4j
public class ComprehensiveKnowledgeLoader {

    private final Map<String, SchemaDefinition> schemas = new HashMap<>();

    public ComprehensiveKnowledgeLoader() {
        loadAllKnowledge();
        log.info("ComprehensiveKnowledgeLoader initialized with {} knowledge items", schemas.size());
    }

    public List<SchemaDefinition> getAllSchemas() {
        return new ArrayList<>(schemas.values());
    }

    public SchemaDefinition getSchema(String id) {
        return schemas.get(id);
    }

    private void loadAllKnowledge() {
        loadPlatformArchitecture();
        loadEntityFieldTypes();
        loadUIComponents();
        loadPageTemplates();
        loadAPIEndpoints();
        loadSecurityFeatures();
        loadWorkflowCapabilities();
        loadMultiTenancy();
        loadBestPractices();
        loadTroubleshooting();
        loadHowToGuides();
        loadAIBuilderCapabilities();
    }

    // ==================== PLATFORM ARCHITECTURE ====================

    private void loadPlatformArchitecture() {
        addKnowledge("arch_overview", "AppBana Platform Overview",
                SchemaDefinition.SchemaType.ENTITY,
                "AppBana is a metadata-driven multi-tenant application platform. It generates end-to-end functionality from a single source: Entity Definition → Schema → Database → REST APIs → UI Pages. Changes to metadata propagate automatically through all layers. The platform uses physical table isolation for multi-tenancy, meaning each tenant's data is stored in separate tables.",
                List.of(
                        "Create an entity called 'Customer' with name, email, phone fields",
                        "Build a CRM application with customers, orders, and products",
                        "What is AppBana and how does it work?"
                ),
                Map.of("category", "architecture", "importance", "high"));

        addKnowledge("arch_metadata_driven", "Metadata-Driven Architecture",
                SchemaDefinition.SchemaType.ENTITY,
                "AppBana follows a metadata-driven architecture where all application behavior is defined through metadata (JSON). This includes: Entity definitions with field types and validations, Page layouts with component trees, Workflow definitions with nodes and transitions, Security rules with role-based permissions. The metadata is stored in files (apps/{appId}/) and synchronized to the database.",
                List.of(
                        "How does metadata work in AppBana?",
                        "What is metadata-driven development?",
                        "How are changes propagated in AppBana?"
                ),
                Map.of("category", "architecture", "importance", "high"));

        addKnowledge("arch_dual_layer", "Dual-Layer Entity Abstraction",
                SchemaDefinition.SchemaType.ENTITY,
                "AppBana uses a dual-layer abstraction: 1) Business Layer - Users define entities with friendly field types like 'email', 'phone', 'currency', 'reference'. 2) Technical Layer - The system converts these to relational schema with SQL types, foreign keys, and junction tables. This separation allows business users to work without SQL knowledge while ensuring proper database design.",
                List.of(
                        "How do entities work in AppBana?",
                        "What is the difference between entity and schema?",
                        "How are foreign keys created?"
                ),
                Map.of("category", "architecture", "importance", "high"));

        addKnowledge("arch_tech_stack", "Technology Stack",
                SchemaDefinition.SchemaType.ENTITY,
                "AppBana Technology Stack: Backend - Java 21 LTS with virtual threads, JDK HttpServer, Maven build, PostgreSQL/H2 database, HikariCP connection pool, Flyway migrations. Frontend - TypeScript 5.2+, Lit Web Components with Shadow DOM, Vite 5.3+ build tool. AI Builder - OpenAI GPT-4o, Qdrant vector database, MVEL expressions for workflows.",
                List.of(
                        "What technology does AppBana use?",
                        "What is the tech stack?",
                        "What database does AppBana support?"
                ),
                Map.of("category", "architecture", "importance", "medium"));
    }

    // ==================== ENTITY FIELD TYPES ====================

    private void loadEntityFieldTypes() {
        // Text Types
        addFieldType("field_text", "text", "TEXT field type",
                "Short text field (VARCHAR 255). Use for names, titles, labels, and short descriptions. Supports minLength, maxLength, and pattern validation.",
                List.of("name", "title", "label", "firstName", "lastName", "company"));

        addFieldType("field_longtext", "longtext", "LONGTEXT field type",
                "Long text field (TEXT). Use for descriptions, notes, comments, and multi-line content. Renders as textarea in forms.",
                List.of("description", "notes", "comments", "bio", "summary"));

        addFieldType("field_email", "email", "EMAIL field type",
                "Email field with automatic validation. Checks for valid email format (user@domain.com). Use for user emails, contact emails.",
                List.of("email", "contactEmail", "workEmail", "user@example.com"));

        addFieldType("field_phone", "phone", "PHONE field type",
                "Phone number field with formatting. Supports international formats. Use for mobile, landline, fax numbers.",
                List.of("phone", "mobile", "fax", "+1-555-0123", "(555) 123-4567"));

        addFieldType("field_url", "url", "URL field type",
                "URL field with validation. Validates http/https URLs. Use for websites, links, social profiles.",
                List.of("website", "linkedin", "portfolio", "https://example.com"));

        // Numeric Types
        addFieldType("field_number", "number", "NUMBER field type",
                "Integer field (BIGINT). Use for counts, quantities, IDs. Supports min, max, step validation.",
                List.of("quantity", "count", "age", "years", "42", "1000"));

        addFieldType("field_decimal", "decimal", "DECIMAL field type",
                "Decimal/float field (DECIMAL 19,4). Use for precise calculations, measurements. Supports step validation.",
                List.of("price", "weight", "height", "3.14", "99.99"));

        addFieldType("field_currency", "currency", "CURRENCY field type",
                "Money field (DECIMAL 19,4). Use for prices, amounts, salaries. Displays with currency symbol. Supports min, max validation.",
                List.of("price", "salary", "amount", "total", "$1299.99", "$49.95"));

        addFieldType("field_percentage", "percentage", "PERCENTAGE field type",
                "Percentage field (0-100). Use for discounts, completion rates, scores. Displays with % symbol.",
                List.of("discount", "progress", "completion", "75%", "100%"));

        // Date/Time Types
        addFieldType("field_date", "date", "DATE field type",
                "Date only field (DATE). Use for birthdates, due dates, event dates. Format: YYYY-MM-DD.",
                List.of("birthDate", "dueDate", "startDate", "2024-01-15"));

        addFieldType("field_datetime", "datetime", "DATETIME field type",
                "Date and time field (TIMESTAMP). Use for appointments, events, logs. Format: YYYY-MM-DDTHH:mm:ss.",
                List.of("createdAt", "updatedAt", "appointmentTime", "2024-01-15T14:30:00"));

        addFieldType("field_time", "time", "TIME field type",
                "Time only field (TIME). Use for schedules, hours, durations. Format: HH:mm.",
                List.of("startTime", "endTime", "openingHours", "14:30", "09:00"));

        // Selection Types
        addFieldType("field_boolean", "boolean", "BOOLEAN field type",
                "Yes/No checkbox field (BOOLEAN). Use for flags, toggles, options. Renders as checkbox in forms.",
                List.of("isActive", "isPublished", "hasSubscription", "true", "false"));

        addFieldType("field_status", "status", "STATUS field type",
                "Dropdown with predefined options. Use for workflow states, categories. Define options in field config.",
                List.of("status", "priority", "category", "active|pending|completed"));

        addFieldType("field_multiselect", "multiselect", "MULTISELECT field type",
                "Multiple selection field (JSON array). Use for tags, categories, features. Allows selecting multiple options.",
                List.of("tags", "categories", "features", "tag1,tag2,tag3"));

        // Relationship Types
        addFieldType("field_reference", "reference", "REFERENCE field type",
                "Foreign key linking to another entity. Use for one-to-many relationships. Specify referenceEntity in config. Creates dropdown showing related records.",
                List.of("customerId", "orderId", "categoryId", "Reference to Customer entity"));

        addFieldType("field_lookup", "lookup", "LOOKUP field type",
                "Same as reference but displays related data inline. Use when you need to show related fields. Specify referenceEntity and referenceDisplay.",
                List.of("customer lookup showing name", "product lookup showing title"));

        // System Types
        addFieldType("field_autoincrement", "autoincrement", "AUTOINCREMENT field type",
                "Auto-incrementing ID field (BIGINT). Read-only, auto-generated. Use for primary keys.",
                List.of("id", "1", "2", "3"));

        addFieldType("field_uuid", "uuid", "UUID field type",
                "UUID/GUID field (VARCHAR 36). Auto-generated unique identifier. Use for distributed systems.",
                List.of("uuid", "550e8400-e29b-41d4-a716-446655440000"));

        addFieldType("field_createdat", "createdAt", "CREATEDAT field type",
                "Auto-set creation timestamp. Read-only, set when record is created. Use for audit trail.",
                List.of("createdAt", "2024-01-15T10:30:00"));

        addFieldType("field_updatedat", "updatedAt", "UPDATEDAT field type",
                "Auto-update modified timestamp. Read-only, updated on every save. Use for audit trail.",
                List.of("updatedAt", "2024-01-15T14:45:00"));

        // Rich Types
        addFieldType("field_file", "file", "FILE field type",
                "File upload field. Stores file path/URL. Use for documents, attachments. Supports fileType and maxSize validation.",
                List.of("document", "attachment", "/uploads/document.pdf"));

        addFieldType("field_image", "image", "IMAGE field type",
                "Image upload with preview. Stores image path/URL. Use for photos, avatars, logos.",
                List.of("photo", "avatar", "logo", "/uploads/image.jpg"));

        addFieldType("field_json", "json", "JSON field type",
                "JSON data field. Stores structured data as JSON. Use for flexible metadata, configs.",
                List.of("config", "metadata", "{\"key\": \"value\"}"));

        addFieldType("field_richtext", "richtext", "RICHTEXT field type",
                "WYSIWYG editor field. Stores HTML content. Use for formatted content, articles.",
                List.of("content", "article", "<p>Rich <strong>text</strong></p>"));
    }

    // ==================== UI COMPONENTS ====================

    private void loadUIComponents() {
        addComponent("comp_container", "container", "Container Component",
                "Generic container for grouping child components with layout control. Supports vertical, horizontal, and grid layouts. Use as the base for all page layouts.",
                Map.of(
                        "layout", "vertical|horizontal|grid",
                        "gap", "sm|md|lg",
                        "padding", "none|sm|md|lg",
                        "align", "start|center|end|stretch"
                ),
                List.of(
                        "{\"type\":\"container\",\"props\":{\"layout\":\"vertical\",\"gap\":\"md\"}}",
                        "Create a container with horizontal layout"
                ));

        addComponent("comp_form", "appbana-form", "Form Container Component",
                "Smart form container that handles data loading and submission. Automatically loads record data, collects input values, and submits to API. Use for all CRUD forms.",
                Map.of(
                        "entity", "Entity name (required)",
                        "record-id", "Record ID for edit mode",
                        "redirect-on-success", "URL after save",
                        "title", "Form title",
                        "submitLabel", "Submit button text"
                ),
                List.of(
                        "{\"type\":\"appbana-form\",\"props\":{\"entity\":\"Customer\",\"title\":\"New Customer\"}}",
                        "Create a form for Customer entity"
                ));

        addComponent("comp_input", "input", "Input Field Component",
                "Text input field with label and validation. Supports text, email, password, number types. Use inside forms for data entry.",
                Map.of(
                        "label", "Field label",
                        "name", "Field name for binding",
                        "type", "text|email|password|number|tel",
                        "placeholder", "Placeholder text",
                        "required", "true|false"
                ),
                List.of(
                        "{\"type\":\"input\",\"props\":{\"label\":\"Email\",\"name\":\"email\",\"type\":\"email\",\"required\":true}}",
                        "Create an email input field"
                ));

        addComponent("comp_textarea", "textarea", "Textarea Component",
                "Multi-line text input for long content. Use for descriptions, notes, comments.",
                Map.of(
                        "label", "Field label",
                        "name", "Field name",
                        "rows", "Number of rows",
                        "placeholder", "Placeholder text"
                ),
                List.of(
                        "{\"type\":\"textarea\",\"props\":{\"label\":\"Description\",\"name\":\"description\",\"rows\":5}}",
                        "Create a description textarea"
                ));

        addComponent("comp_select", "select", "Select/Dropdown Component",
                "Dropdown select field with options. Use for status, category, and choice fields.",
                Map.of(
                        "label", "Field label",
                        "name", "Field name",
                        "options", "Array of {value, label}",
                        "placeholder", "Placeholder text"
                ),
                List.of(
                        "{\"type\":\"select\",\"props\":{\"label\":\"Status\",\"name\":\"status\",\"options\":[{\"value\":\"active\",\"label\":\"Active\"}]}}",
                        "Create a status dropdown"
                ));

        addComponent("comp_button", "button", "Button Component",
                "Interactive button with variants. Supports primary, danger, success, outline, ghost styles. Use for actions and navigation.",
                Map.of(
                        "label", "Button text",
                        "variant", "primary|danger|success|outline|ghost",
                        "size", "sm|md|lg",
                        "disabled", "true|false"
                ),
                List.of(
                        "{\"type\":\"button\",\"props\":{\"label\":\"Save\",\"variant\":\"primary\"}}",
                        "Create a save button"
                ));

        addComponent("comp_table", "appbana-table-live", "Data Table Component",
                "Live data table that fetches and displays entity records. Supports pagination, sorting, search, and row actions. Use for list pages.",
                Map.of(
                        "entity", "Entity name to display",
                        "fields", "Array of field configs",
                        "actions", "edit|delete|view",
                        "pageSize", "Rows per page"
                ),
                List.of(
                        "{\"type\":\"appbana-table-live\",\"props\":{\"entity\":\"Customer\",\"fields\":[{\"name\":\"name\"},{\"name\":\"email\"}]}}",
                        "Create a customer table"
                ));

        addComponent("comp_text", "text", "Text Component",
                "Display text, headings, paragraphs. Supports size variants for typography hierarchy.",
                Map.of(
                        "content", "Text content",
                        "variant", "h1|h2|h3|body|small",
                        "color", "Text color"
                ),
                List.of(
                        "{\"type\":\"text\",\"props\":{\"content\":\"Welcome\",\"variant\":\"h1\"}}",
                        "Create a heading"
                ));

        addComponent("comp_grid", "app-grid", "Grid Layout Component",
                "Responsive grid layout for organizing content in rows and columns. Use for dashboards and card layouts.",
                Map.of(
                        "cols", "Number of columns",
                        "rows", "Number of rows",
                        "gap", "Gap between cells"
                ),
                List.of(
                        "{\"type\":\"app-grid\",\"props\":{\"cols\":3,\"gap\":\"1rem\"}}",
                        "Create a 3-column grid"
                ));

        addComponent("comp_card", "card", "Card Component",
                "Content card with border and shadow. Use for grouping related content in a visually distinct box.",
                Map.of(
                        "title", "Card title",
                        "padding", "Internal padding",
                        "shadow", "Shadow level"
                ),
                List.of(
                        "{\"type\":\"card\",\"props\":{\"title\":\"Statistics\",\"padding\":\"lg\"}}",
                        "Create a statistics card"
                ));
    }

    // ==================== PAGE TEMPLATES ====================

    private void loadPageTemplates() {
        addKnowledge("template_list", "List Page Template",
                SchemaDefinition.SchemaType.PAGE,
                "List page template for displaying entity records in a table. Includes: Header with title and 'Add New' button, Search bar, Data table with columns, Pagination controls, Row actions (edit, delete). Use for: Customer list, Order list, Product catalog.",
                List.of(
                        "Create a customer list page",
                        "Build a page to show all orders",
                        "List page template"
                ),
                Map.of("category", "template", "pageType", "list"));

        addKnowledge("template_form", "Form Page Template",
                SchemaDefinition.SchemaType.PAGE,
                "Form page template for creating/editing entity records. Includes: Header with title, Form container with appbana-form, Input fields for entity fields, Submit and Cancel buttons, Validation messages. Use for: Add customer, Edit order, New product.",
                List.of(
                        "Create a customer form page",
                        "Build an edit order page",
                        "Form page template"
                ),
                Map.of("category", "template", "pageType", "form"));

        addKnowledge("template_dashboard", "Dashboard Page Template",
                SchemaDefinition.SchemaType.PAGE,
                "Dashboard template with metrics and charts. Includes: Header with welcome message, Grid layout with KPI cards, Charts section, Recent activity list, Quick action buttons. Use for: Home page, Admin dashboard, Analytics.",
                List.of(
                        "Create a dashboard page",
                        "Build an analytics dashboard",
                        "Dashboard template"
                ),
                Map.of("category", "template", "pageType", "dashboard"));

        addKnowledge("template_detail", "Detail Page Template",
                SchemaDefinition.SchemaType.PAGE,
                "Detail page template for viewing single record. Includes: Header with title and edit button, Field-value pairs display, Related records section, Action buttons (edit, delete, back). Use for: Customer detail, Order detail, Product view.",
                List.of(
                        "Create a customer detail page",
                        "Build an order view page",
                        "Detail page template"
                ),
                Map.of("category", "template", "pageType", "detail"));

        addKnowledge("template_login", "Login Page Template",
                SchemaDefinition.SchemaType.PAGE,
                "Login page template with authentication form. Includes: Centered card layout, Email and password fields, Remember me checkbox, Login button, Forgot password link, Register link. Security features: CSRF token, rate limiting.",
                List.of(
                        "Create a login page",
                        "Build authentication page",
                        "Login template"
                ),
                Map.of("category", "template", "pageType", "auth"));

        addKnowledge("template_signup", "Sign Up Page Template",
                SchemaDefinition.SchemaType.PAGE,
                "Sign up / registration page template. Includes: 45/55 split layout, Brand panel with logo and features, Registration form with name, email, password, Terms checkbox, Already have account link. Validation: Password strength, email format.",
                List.of(
                        "Create a signup page",
                        "Build registration page",
                        "Sign up template"
                ),
                Map.of("category", "template", "pageType", "auth"));
    }

    // ==================== API ENDPOINTS ====================

    private void loadAPIEndpoints() {
        addKnowledge("api_apps", "App Management API",
                SchemaDefinition.SchemaType.ENTITY,
                "REST API for managing applications. Endpoints: GET /apps - List all apps, POST /apps - Create new app, GET /apps/{appId} - Get app details, PUT /apps/{appId} - Update app, DELETE /apps/{appId} - Delete app, POST /apps/publish - Publish app to environment (DEV/SIT/PROD).",
                List.of(
                        "How do I create an app via API?",
                        "List all apps endpoint",
                        "Publish app API"
                ),
                Map.of("category", "api", "apiGroup", "apps"));

        addKnowledge("api_pages", "Page Management API",
                SchemaDefinition.SchemaType.ENTITY,
                "REST API for managing pages within apps. Endpoints: GET /apps/{appId}/pages/{pageId} - Get page, PUT /apps/{appId}/pages/{pageId} - Save page (auto-updates app.pages[]), DELETE /apps/{appId}/pages/{pageId} - Delete page.",
                List.of(
                        "How do I save a page via API?",
                        "Page API endpoints",
                        "Auto-link pages to app"
                ),
                Map.of("category", "api", "apiGroup", "pages"));

        addKnowledge("api_schema", "Schema Management API",
                SchemaDefinition.SchemaType.ENTITY,
                "REST API for entity schema management. Endpoints: POST /api/schema - Create schema (executes CREATE TABLE), GET /api/schema - List all schemas, GET /api/schema/{name} - Get schema, DELETE /api/schema/{name}?dropTable=true - Delete schema.",
                List.of(
                        "How do I create a database table?",
                        "Schema API endpoints",
                        "Create entity via API"
                ),
                Map.of("category", "api", "apiGroup", "schema"));

        addKnowledge("api_crud", "Entity CRUD API",
                SchemaDefinition.SchemaType.ENTITY,
                "REST API for entity data operations. Endpoints: GET /api/{entity} - Query records (?limit, ?offset, ?sort), GET /api/{entity}/{id} - Get single record, POST /api/{entity} - Create record, PUT /api/{entity}/{id} - Update record, DELETE /api/{entity}/{id} - Delete record. All operations trigger audit logs.",
                List.of(
                        "How do I query entity data?",
                        "CRUD API endpoints",
                        "Create a record via API"
                ),
                Map.of("category", "api", "apiGroup", "crud"));

        addKnowledge("api_auth", "Authentication API",
                SchemaDefinition.SchemaType.ENTITY,
                "REST API for authentication. Endpoints: POST /api/auth/register - Create user account, POST /api/auth/login - Login and get JWT token, POST /api/auth/logout - Logout and invalidate session, GET /api/auth/me - Get current user. Requires CSRF token for POST requests.",
                List.of(
                        "How do I login via API?",
                        "Authentication endpoints",
                        "Register user API"
                ),
                Map.of("category", "api", "apiGroup", "auth"));

        addKnowledge("api_workflow", "Workflow API",
                SchemaDefinition.SchemaType.ENTITY,
                "REST API for workflow management. Endpoints: GET /api/workflows - List workflows, POST /api/workflows - Create workflow, POST /api/workflows/{id}/start - Start workflow instance, GET /api/workflows/{id}/instances - Get instances, POST /api/workflows/{id}/tasks/{taskId}/complete - Complete task.",
                List.of(
                        "How do I start a workflow?",
                        "Workflow API endpoints",
                        "Complete workflow task"
                ),
                Map.of("category", "api", "apiGroup", "workflow"));
    }

    // ==================== SECURITY FEATURES ====================

    private void loadSecurityFeatures() {
        addKnowledge("sec_overview", "Security Suite Overview",
                SchemaDefinition.SchemaType.PERMISSION,
                "AppBana provides enterprise-grade security: CSRF Protection (256-bit tokens, 30-min expiration), Session Management (sliding window, 30-min timeout), Rate Limiting (100 req/min per IP), Password Security (BCrypt work factor 12), RBAC (User/Role/Permission), Field-Level Security (HIPAA/PCI-DSS compliant). 156 tests passing.",
                List.of(
                        "What security features does AppBana have?",
                        "Is AppBana secure?",
                        "Security overview"
                ),
                Map.of("category", "security", "importance", "critical"));

        addKnowledge("sec_csrf", "CSRF Protection",
                SchemaDefinition.SchemaType.PERMISSION,
                "Cross-Site Request Forgery protection. All POST/PUT/DELETE requests require X-CSRF-Token header. Get token: GET /api/csrf/token. Token is bound to session and expires in 30 minutes. Frontend FormContainer auto-fetches and includes CSRF token.",
                List.of(
                        "How does CSRF protection work?",
                        "How to get CSRF token?",
                        "CSRF error 403"
                ),
                Map.of("category", "security", "feature", "csrf"));

        addKnowledge("sec_session", "Session Management",
                SchemaDefinition.SchemaType.PERMISSION,
                "Secure session management with sliding window. Sessions expire after 30 minutes of inactivity. Sessions auto-renew on activity. Send X-Session-Token header with all authenticated requests. Store token from login response in localStorage.",
                List.of(
                        "How do sessions work?",
                        "Session timeout settings",
                        "401 session expired error"
                ),
                Map.of("category", "security", "feature", "session"));

        addKnowledge("sec_ratelimit", "Rate Limiting",
                SchemaDefinition.SchemaType.PERMISSION,
                "IP-based request throttling. 100 requests per minute per IP per endpoint. Returns 429 Too Many Requests when exceeded. Response includes Retry-After header. Protects against DDoS and brute force attacks.",
                List.of(
                        "How does rate limiting work?",
                        "429 Too Many Requests error",
                        "Rate limit settings"
                ),
                Map.of("category", "security", "feature", "ratelimit"));

        addKnowledge("sec_rbac", "Role-Based Access Control",
                SchemaDefinition.SchemaType.PERMISSION,
                "RBAC with Users, Roles, and Permissions. Users can have multiple roles. Roles contain multiple permissions. Permissions control access to entities and operations. Built-in roles: Admin, User, Guest. Custom roles supported.",
                List.of(
                        "How does RBAC work?",
                        "Create custom role",
                        "Assign permissions to role"
                ),
                Map.of("category", "security", "feature", "rbac"));

        addKnowledge("sec_fls", "Field-Level Security",
                SchemaDefinition.SchemaType.PERMISSION,
                "Control field visibility per role. Can hide fields (salary, SSN), make read-only (manager sees salary, cannot edit), or grant full access. Supports wildcard (*) for admin. OR logic: accessible if ANY role grants permission. HIPAA and PCI-DSS compliant.",
                List.of(
                        "How to hide sensitive fields?",
                        "Field-level permissions",
                        "FLS configuration"
                ),
                Map.of("category", "security", "feature", "fls"));

        addKnowledge("sec_password", "Password Security",
                SchemaDefinition.SchemaType.PERMISSION,
                "Secure password handling with BCrypt. Work factor 12 (intentionally slow: 100-500ms). Unique salt per password. Constant-time comparison prevents timing attacks. Never store plain text. Frontend validates: 8+ chars, letters+numbers.",
                List.of(
                        "How are passwords stored?",
                        "Password requirements",
                        "BCrypt settings"
                ),
                Map.of("category", "security", "feature", "password"));
    }

    // ==================== WORKFLOW CAPABILITIES ====================

    private void loadWorkflowCapabilities() {
        addKnowledge("wf_overview", "Workflow Engine Overview",
                SchemaDefinition.SchemaType.ENTITY,
                "AppBana Workflow Engine automates business processes. Features: Visual workflow designer, Trigger on entity events (ON_CREATE, ON_UPDATE, ON_DELETE, MANUAL), Node types (Start, End, User Task, Service Task, Decision, Wait), MVEL expressions for conditions, SLA monitoring and escalation.",
                List.of(
                        "What is the workflow engine?",
                        "How do workflows work?",
                        "Workflow automation overview"
                ),
                Map.of("category", "workflow", "importance", "high"));

        addKnowledge("wf_user_task", "User Task Node",
                SchemaDefinition.SchemaType.ENTITY,
                "User Task pauses workflow until human action. Assignment types: USER (specific user), ROLE (any user with role), QUEUE (claim from pool), DYNAMIC (expression like ${entity.owner.manager}). Includes form fields for user input. Supports SLA with timeout actions.",
                List.of(
                        "How to create approval task?",
                        "User task assignment",
                        "Workflow human step"
                ),
                Map.of("category", "workflow", "nodeType", "USER_TASK"));

        addKnowledge("wf_service_task", "Service Task Node",
                SchemaDefinition.SchemaType.ENTITY,
                "Service Task executes automatic actions. Types: UPDATE_ENTITY (update record fields), SEND_EMAIL (send notification), WEBHOOK (call external API), GENERATE_DOC (create document). Runs without human intervention.",
                List.of(
                        "How to update record in workflow?",
                        "Send email from workflow",
                        "Automated workflow action"
                ),
                Map.of("category", "workflow", "nodeType", "SERVICE_TASK"));

        addKnowledge("wf_decision", "Decision Node",
                SchemaDefinition.SchemaType.ENTITY,
                "Decision node routes workflow based on conditions. Uses MVEL expressions: ${entity.amount > 1000}, ${outcome == 'APPROVE'}. Supports multiple branches with priority order. Always include ELSE path as fallback.",
                List.of(
                        "How to add condition to workflow?",
                        "Workflow branching",
                        "Decision node expressions"
                ),
                Map.of("category", "workflow", "nodeType", "DECISION"));

        addKnowledge("wf_triggers", "Workflow Triggers",
                SchemaDefinition.SchemaType.ENTITY,
                "Workflows start based on triggers. ON_CREATE: When entity record is created. ON_UPDATE: When record is updated. ON_DELETE: When record is deleted. MANUAL: User explicitly starts. Trigger conditions: ${entity.amount > 1000}.",
                List.of(
                        "How to trigger workflow?",
                        "Start workflow on create",
                        "Workflow trigger conditions"
                ),
                Map.of("category", "workflow", "feature", "triggers"));

        addKnowledge("wf_example", "Workflow Example: Approval Process",
                SchemaDefinition.SchemaType.ENTITY,
                "Example: Payment Approval Workflow. Trigger: PaymentRequest ON_CREATE when amount > 1000. Nodes: Start → Manager Review (User Task) → Decision (APPROVE/REJECT) → Update Status (Service Task) → End. Manager reviews, approves or rejects, status auto-updates.",
                List.of(
                        "Show me workflow example",
                        "Approval workflow",
                        "Payment approval process"
                ),
                Map.of("category", "workflow", "type", "example"));
    }

    // ==================== MULTI-TENANCY ====================

    private void loadMultiTenancy() {
        addKnowledge("mt_overview", "Multi-Tenancy Overview",
                SchemaDefinition.SchemaType.ENTITY,
                "AppBana uses Physical Table Isolation for multi-tenancy. Each tenant's data is stored in separate physical tables named: app_{env}{tenant}_{app}_{entity}. Example: app_t_acme_corp_crm_users. System tables (appbana_*) have tenant_id columns. Entity tables do NOT have tenant_id - isolation is via table name.",
                List.of(
                        "How does multi-tenancy work?",
                        "Tenant isolation",
                        "Physical table isolation"
                ),
                Map.of("category", "architecture", "importance", "high"));

        addKnowledge("mt_benefits", "Multi-Tenancy Benefits",
                SchemaDefinition.SchemaType.ENTITY,
                "Physical Table Isolation provides: 20-30% faster queries (no WHERE tenant_id filtering), 50% less storage (no redundant columns), Impossible to leak data (wrong table = error, not wrong data), Simple CRUD operations (no filtering logic), Database-level security.",
                List.of(
                        "Why physical table isolation?",
                        "Multi-tenancy benefits",
                        "Tenant performance"
                ),
                Map.of("category", "architecture", "feature", "benefits"));

        addKnowledge("mt_publish", "App Publishing",
                SchemaDefinition.SchemaType.ENTITY,
                "Publishing creates physical tables for tenant/app. POST /apps/publish with: appMetaJson, appId, tenantId, environment (DEV/SIT/PROD). Creates tables: app_{env}{tenant}_{app}_{entity}. Transactional: all-or-nothing. Versioned deployments with rollback.",
                List.of(
                        "How to publish app?",
                        "Deploy to production",
                        "Create tenant tables"
                ),
                Map.of("category", "architecture", "feature", "publish"));
    }

    // ==================== BEST PRACTICES ====================

    private void loadBestPractices() {
        addKnowledge("bp_entity_design", "Entity Design Best Practices",
                SchemaDefinition.SchemaType.ENTITY,
                "Best practices for designing entities: 1) Use meaningful names (Customer, not cust), 2) Include id field (auto-added), 3) Use appropriate field types (email for emails, not text), 4) Add createdAt/updatedAt for audit, 5) Use reference type for relationships, 6) Keep entities focused (single responsibility).",
                List.of(
                        "How to design entities?",
                        "Entity naming conventions",
                        "Best practices for entities"
                ),
                Map.of("category", "bestpractice", "topic", "entities"));

        addKnowledge("bp_page_design", "Page Design Best Practices",
                SchemaDefinition.SchemaType.PAGE,
                "Best practices for designing pages: 1) Use templates as starting point, 2) Group related fields in containers, 3) Use consistent spacing (gap: md), 4) Add validation to required fields, 5) Include success/error feedback, 6) Provide navigation (back, cancel buttons), 7) Use appbana-form for CRUD operations.",
                List.of(
                        "How to design pages?",
                        "Page layout best practices",
                        "Form design tips"
                ),
                Map.of("category", "bestpractice", "topic", "pages"));

        addKnowledge("bp_security", "Security Best Practices",
                SchemaDefinition.SchemaType.PERMISSION,
                "Security best practices: 1) Always use HTTPS in production, 2) Include CSRF token in all forms, 3) Validate input on backend (not just frontend), 4) Use role-based access control, 5) Hide sensitive fields with FLS, 6) Set appropriate session timeout, 7) Monitor rate limit violations, 8) Never log passwords.",
                List.of(
                        "Security best practices",
                        "How to secure my app?",
                        "Security checklist"
                ),
                Map.of("category", "bestpractice", "topic", "security"));

        addKnowledge("bp_workflow", "Workflow Best Practices",
                SchemaDefinition.SchemaType.ENTITY,
                "Workflow best practices: 1) Start simple, add complexity gradually, 2) Always include ELSE path in decisions, 3) Set realistic SLAs, 4) Use Service Tasks for automated updates, 5) Test with sample data before production, 6) Log all transitions for audit, 7) Handle edge cases (reject, cancel, timeout).",
                List.of(
                        "Workflow best practices",
                        "How to design workflows?",
                        "Workflow tips"
                ),
                Map.of("category", "bestpractice", "topic", "workflow"));
    }

    // ==================== TROUBLESHOOTING ====================

    private void loadTroubleshooting() {
        addKnowledge("ts_401_error", "401 Unauthorized Error",
                SchemaDefinition.SchemaType.ENTITY,
                "401 error means session is missing or expired. Solutions: 1) Check localStorage for appbana_token, 2) Verify X-Session-Token header is included, 3) Session expires after 30 min inactivity - re-login, 4) Check /api/auth/login response for token.",
                List.of(
                        "401 error fix",
                        "Session expired",
                        "Unauthorized error"
                ),
                Map.of("category", "troubleshoot", "errorCode", "401"));

        addKnowledge("ts_403_error", "403 Forbidden Error",
                SchemaDefinition.SchemaType.ENTITY,
                "403 error means CSRF token is missing or invalid. Solutions: 1) Fetch CSRF token: GET /api/csrf/token, 2) Include X-CSRF-Token header, 3) Also include X-Session-Id header, 4) Tokens expire after 30 min - refetch.",
                List.of(
                        "403 error fix",
                        "CSRF token error",
                        "Forbidden error"
                ),
                Map.of("category", "troubleshoot", "errorCode", "403"));

        addKnowledge("ts_429_error", "429 Too Many Requests Error",
                SchemaDefinition.SchemaType.ENTITY,
                "429 error means rate limit exceeded. Solutions: 1) Wait for Retry-After seconds, 2) Reduce request frequency, 3) Check for loops in your code, 4) Default limit: 100 req/min per IP per endpoint.",
                List.of(
                        "429 error fix",
                        "Rate limit exceeded",
                        "Too many requests"
                ),
                Map.of("category", "troubleshoot", "errorCode", "429"));

        addKnowledge("ts_form_not_saving", "Form Not Saving Data",
                SchemaDefinition.SchemaType.ENTITY,
                "Form not saving: 1) Use appbana-form component (not plain form), 2) Check entity prop matches existing entity, 3) Ensure field names match entity fields, 4) Check browser console for errors, 5) Verify API is running on port 8080, 6) Check CSRF token is included.",
                List.of(
                        "Form not saving",
                        "Data not being saved",
                        "Submit not working"
                ),
                Map.of("category", "troubleshoot", "issue", "form"));

        addKnowledge("ts_table_empty", "Table Showing No Data",
                SchemaDefinition.SchemaType.ENTITY,
                "Table showing empty: 1) Check entity prop is correct, 2) Verify entity has been published (tables created), 3) Check TenantContext is set correctly, 4) Verify data exists: GET /api/{entity}, 5) Check fields prop matches entity schema.",
                List.of(
                        "Table shows no data",
                        "Empty table",
                        "Data not loading"
                ),
                Map.of("category", "troubleshoot", "issue", "table"));
    }

    // ==================== HOW-TO GUIDES ====================

    private void loadHowToGuides() {
        addKnowledge("howto_create_entity", "How to Create an Entity",
                SchemaDefinition.SchemaType.ENTITY,
                "To create an entity: 1) Define entity with name and fields, 2) Each field needs: name, type, and optional validation, 3) Common types: text, email, number, date, boolean, reference, 4) POST to /api/schema to create table, 5) Use POST /apps/publish to create tenant tables. Example: Customer entity with name (text), email (email), phone (phone).",
                List.of(
                        "How to create an entity?",
                        "Create customer entity",
                        "Define entity fields"
                ),
                Map.of("category", "howto", "topic", "entity"));

        addKnowledge("howto_create_page", "How to Create a Page",
                SchemaDefinition.SchemaType.PAGE,
                "To create a page: 1) Choose template (list, form, dashboard), 2) Define page metadata: id, name, path, 3) Build component tree with nodes, 4) Each node has: id, type, props, children, 5) PUT to /apps/{appId}/pages/{pageId}. Page auto-links to app.pages[] array.",
                List.of(
                        "How to create a page?",
                        "Create list page",
                        "Build form page"
                ),
                Map.of("category", "howto", "topic", "page"));

        addKnowledge("howto_create_workflow", "How to Create a Workflow",
                SchemaDefinition.SchemaType.ENTITY,
                "To create a workflow: 1) Define trigger: entity + event (ON_CREATE), 2) Add Start node, 3) Add User Task for approvals, 4) Add Decision for branching, 5) Add Service Task for automation, 6) Add End node, 7) Connect with transitions. POST to /api/workflows.",
                List.of(
                        "How to create a workflow?",
                        "Create approval workflow",
                        "Build automation"
                ),
                Map.of("category", "howto", "topic", "workflow"));

        addKnowledge("howto_add_security", "How to Add Security to App",
                SchemaDefinition.SchemaType.PERMISSION,
                "To secure your app: 1) Use appbana-form component (auto-includes CSRF), 2) Store session token from login, 3) Include X-Session-Token header, 4) Define roles and permissions, 5) Configure FLS for sensitive fields, 6) Use HTTPS in production.",
                List.of(
                        "How to add security?",
                        "Secure my application",
                        "Enable authentication"
                ),
                Map.of("category", "howto", "topic", "security"));

        addKnowledge("howto_publish", "How to Publish an App",
                SchemaDefinition.SchemaType.ENTITY,
                "To publish an app: 1) Complete entity definitions, 2) Build pages for each entity (list, form), 3) POST /apps/publish with: appMetaJson (full app JSON), appId, tenantId, environment (DEV/SIT/PROD), 4) System creates physical tables, 5) App is now accessible at runtime.",
                List.of(
                        "How to publish app?",
                        "Deploy application",
                        "Go live with app"
                ),
                Map.of("category", "howto", "topic", "publish"));
    }

    // ==================== AI BUILDER CAPABILITIES ====================

    private void loadAIBuilderCapabilities() {
        addKnowledge("ai_overview", "AI Builder Overview",
                SchemaDefinition.SchemaType.ENTITY,
                "AppBana AI Builder uses natural language to create apps. Features: Create entities from descriptions, Generate pages automatically, Build workflows from requirements, Cost optimization (RAG-first, caching, pattern matching). Just describe what you want: 'Create a customer entity with name, email, phone'.",
                List.of(
                        "What can AI Builder do?",
                        "AI capabilities",
                        "Natural language app building"
                ),
                Map.of("category", "ai", "importance", "high"));

        addKnowledge("ai_create_entity", "AI: Create Entity",
                SchemaDefinition.SchemaType.ENTITY,
                "Ask AI to create entities: 'Create a Customer entity with name, email, and phone fields'. AI will: Determine appropriate field types, Add validation rules, Create the schema, Generate list and form pages. You can refine: 'Add an address field and make email required'.",
                List.of(
                        "Create entity with AI",
                        "AI create customer",
                        "Natural language entity"
                ),
                Map.of("category", "ai", "capability", "create_entity"));

        addKnowledge("ai_create_page", "AI: Create Page",
                SchemaDefinition.SchemaType.PAGE,
                "Ask AI to create pages: 'Create a customer list page' or 'Build a form to add new orders'. AI uses templates and generates: Container layouts, Form fields, Tables, Buttons with actions. Specify details: 'Include search and export buttons'.",
                List.of(
                        "Create page with AI",
                        "AI generate form",
                        "Natural language page"
                ),
                Map.of("category", "ai", "capability", "create_page"));

        addKnowledge("ai_scaffold_app", "AI: Scaffold Complete App",
                SchemaDefinition.SchemaType.ENTITY,
                "Ask AI to build complete app: 'Build a CRM application with customers, orders, and products'. AI will: Create all entities with fields, Generate relationships (order belongs to customer), Create list and form pages, Build navigation. Then refine as needed.",
                List.of(
                        "Build complete app with AI",
                        "Scaffold CRM app",
                        "AI create full application"
                ),
                Map.of("category", "ai", "capability", "scaffold_app"));

        addKnowledge("ai_available_tools", "AI Builder Available Tools",
                SchemaDefinition.SchemaType.ENTITY,
                "AI Builder tools: create_entity (create entity with fields), create_app (create new application), scaffold_app (build complete app), generate_page (create page from template), list_entities (show all entities), list_apps (show all apps), list_pages (show app pages), deploy_app (publish to environment), search_knowledge (search documentation).",
                List.of(
                        "What tools does AI have?",
                        "AI capabilities list",
                        "Available AI functions"
                ),
                Map.of("category", "ai", "capability", "tools"));
    }

    // ==================== HELPER METHODS ====================

    private void addKnowledge(String id, String name, SchemaDefinition.SchemaType type,
                              String description, List<String> examples, Map<String, Object> metadata) {
        SchemaDefinition schema = new SchemaDefinition();
        schema.setId(id);
        schema.setName(name);
        schema.setType(type);
        schema.setDescription(description);
        schema.setExamples(examples);
        schema.setMetadata(metadata);
        schemas.put(id, schema);
    }

    private void addFieldType(String id, String fieldType, String name, String description, List<String> examples) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("category", "fieldType");
        metadata.put("fieldType", fieldType);
        addKnowledge(id, name, SchemaDefinition.SchemaType.ENTITY_FIELD, description, examples, metadata);
    }

    private void addComponent(String id, String componentType, String name, String description,
                              Map<String, String> props, List<String> examples) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("category", "component");
        metadata.put("componentType", componentType);
        props.forEach((k, v) -> metadata.put("prop_" + k, v));
        addKnowledge(id, name, SchemaDefinition.SchemaType.COMPONENT, description, examples, metadata);
    }
}
