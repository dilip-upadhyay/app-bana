package com.appbana.ai.knowledge;

import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Comprehensive knowledge loader for AppBana platform capabilities
 * Loads detailed knowledge about all platform features into Qdrant vector database
 */
@Slf4j
public class AppBanaKnowledgeLoader {

    private final KnowledgeBaseService knowledgeBaseService;

    public AppBanaKnowledgeLoader(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    /**
     * Load all AppBana knowledge into the vector database
     */
    public void loadAllKnowledge() {
        log.info("Starting comprehensive AppBana knowledge loading...");
        
        int totalLoaded = 0;
        
        // Load each category of knowledge
        totalLoaded += loadPlatformOverview();
        totalLoaded += loadEntityKnowledge();
        totalLoaded += loadFieldTypes();
        totalLoaded += loadUIComponents();
        totalLoaded += loadPageTypes();
        totalLoaded += loadSecurityFeatures();
        totalLoaded += loadWorkflowKnowledge();
        totalLoaded += loadAPIKnowledge();
        totalLoaded += loadMultiTenantKnowledge();
        totalLoaded += loadStudioBuilderKnowledge();
        totalLoaded += loadDataBindingPatterns();
        totalLoaded += loadValidationRules();
        totalLoaded += loadBestPractices();
        totalLoaded += loadTemplates();
        
        log.info("Completed loading {} knowledge entries into vector database", totalLoaded);
    }

    // ==================== PLATFORM OVERVIEW ====================
    
    private int loadPlatformOverview() {
        List<SchemaDefinition> schemas = new ArrayList<>();
        
        schemas.add(SchemaDefinition.builder()
            .id("platform-overview")
            .name("AppBana Platform")
            .type("platform")
            .description("AppBana is a metadata-driven platform for building enterprise applications. " +
                "It follows the pattern: Entity Definition → Schema → Database → REST APIs → UI Pages. " +
                "Changes to metadata propagate automatically through the entire stack.")
            .category("platform")
            .examples(List.of(
                "Create apps visually without coding",
                "Define entities and get automatic CRUD APIs",
                "Build pages with drag-and-drop components",
                "Deploy apps with one-click publish"
            ))
            .metadata(Map.of(
                "tech_stack", "Java 21, TypeScript, Lit Web Components, PostgreSQL/H2",
                "architecture", "metadata-driven",
                "multi_tenant", true
            ))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("platform-architecture")
            .name("Platform Architecture")
            .type("architecture")
            .description("AppBana uses a layered architecture with physical table isolation for multi-tenancy. " +
                "Each app gets its own database tables with naming convention: app_{appId}_{entityName}. " +
                "The platform provides automatic schema management, API generation, and UI rendering.")
            .category("architecture")
            .examples(List.of(
                "Physical table isolation: app_abc123_customer",
                "Automatic API endpoints: /api/{tenantId}/{appId}/{entity}",
                "Runtime page rendering from metadata"
            ))
            .metadata(Map.of(
                "isolation_pattern", "physical_tables",
                "api_pattern", "REST",
                "ui_framework", "Lit Web Components"
            ))
            .build());

        return indexSchemas(schemas, "platform-overview");
    }

    // ==================== ENTITY KNOWLEDGE ====================
    
    private int loadEntityKnowledge() {
        List<SchemaDefinition> schemas = new ArrayList<>();
        
        schemas.add(SchemaDefinition.builder()
            .id("entity-definition")
            .name("Entity Definition")
            .type("entity")
            .description("Entities in AppBana represent business objects with fields. " +
                "Define entities with name, fields, and optional relationships. " +
                "Entities automatically get database tables, CRUD APIs, and can be used in pages.")
            .category("entity")
            .examples(List.of(
                "Customer entity with name, email, phone fields",
                "Order entity with amount, status, customerId fields",
                "Product entity with title, price, description, category"
            ))
            .metadata(Map.of(
                "auto_api", true,
                "auto_table", true,
                "supports_relationships", true
            ))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("entity-fields")
            .name("Entity Fields")
            .type("entity")
            .description("Entity fields define the data structure. Each field has a name, type, and optional constraints. " +
                "Supported types include text, number, email, date, boolean, select, multi-select, reference, and more.")
            .category("entity")
            .examples(List.of(
                "{ \"name\": \"email\", \"type\": \"email\", \"required\": true }",
                "{ \"name\": \"status\", \"type\": \"select\", \"options\": [\"active\", \"inactive\"] }",
                "{ \"name\": \"amount\", \"type\": \"number\", \"min\": 0 }"
            ))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("entity-relationships")
            .name("Entity Relationships")
            .type("entity")
            .description("Entities can have relationships to other entities. " +
                "Supported relationship types: one-to-one, one-to-many, many-to-many. " +
                "Use 'reference' field type with 'relatedEntity' to create relationships.")
            .category("entity")
            .examples(List.of(
                "Order has customerId referencing Customer entity",
                "Product belongs to Category (many-to-one)",
                "User has many Orders (one-to-many)"
            ))
            .metadata(Map.of(
                "supports_cascade", true,
                "supports_lookup", true
            ))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("entity-crud-operations")
            .name("Entity CRUD Operations")
            .type("api")
            .description("AppBana automatically generates CRUD operations for each entity: " +
                "Create (POST), Read (GET), Update (PUT), Delete (DELETE). " +
                "APIs support filtering, pagination, sorting, and projection.")
            .category("entity")
            .examples(List.of(
                "POST /api/{tenantId}/{appId}/customer - Create customer",
                "GET /api/{tenantId}/{appId}/customer?limit=10&offset=0 - List customers",
                "GET /api/{tenantId}/{appId}/customer/{id} - Get single customer",
                "PUT /api/{tenantId}/{appId}/customer/{id} - Update customer",
                "DELETE /api/{tenantId}/{appId}/customer/{id} - Delete customer"
            ))
            .build());

        return indexSchemas(schemas, "entity-knowledge");
    }

    // ==================== FIELD TYPES ====================
    
    private int loadFieldTypes() {
        List<SchemaDefinition> schemas = new ArrayList<>();
        
        // Text field
        schemas.add(SchemaDefinition.builder()
            .id("field-type-text")
            .name("Text Field")
            .type("field_type")
            .description("Standard text input field for short text. Supports minLength, maxLength, pattern validations.")
            .category("field_type")
            .examples(List.of("name", "title", "description", "address"))
            .metadata(Map.of("html_type", "text", "db_type", "VARCHAR"))
            .build());

        // Number field
        schemas.add(SchemaDefinition.builder()
            .id("field-type-number")
            .name("Number Field")
            .type("field_type")
            .description("Numeric field for integers or decimals. Supports min, max, step validations.")
            .category("field_type")
            .examples(List.of("age", "price", "quantity", "amount"))
            .metadata(Map.of("html_type", "number", "db_type", "NUMERIC"))
            .build());

        // Email field
        schemas.add(SchemaDefinition.builder()
            .id("field-type-email")
            .name("Email Field")
            .type("field_type")
            .description("Email input with automatic email format validation.")
            .category("field_type")
            .examples(List.of("email", "contactEmail", "workEmail"))
            .metadata(Map.of("html_type", "email", "db_type", "VARCHAR", "validation", "email_format"))
            .build());

        // Phone field
        schemas.add(SchemaDefinition.builder()
            .id("field-type-phone")
            .name("Phone Field")
            .type("field_type")
            .description("Phone number input with optional format validation.")
            .category("field_type")
            .examples(List.of("phone", "mobile", "workPhone", "fax"))
            .metadata(Map.of("html_type", "tel", "db_type", "VARCHAR"))
            .build());

        // Date field
        schemas.add(SchemaDefinition.builder()
            .id("field-type-date")
            .name("Date Field")
            .type("field_type")
            .description("Date picker for date values. Supports min/max date constraints.")
            .category("field_type")
            .examples(List.of("birthDate", "createdAt", "dueDate", "startDate"))
            .metadata(Map.of("html_type", "date", "db_type", "DATE"))
            .build());

        // DateTime field
        schemas.add(SchemaDefinition.builder()
            .id("field-type-datetime")
            .name("DateTime Field")
            .type("field_type")
            .description("Date and time picker for timestamp values.")
            .category("field_type")
            .examples(List.of("createdAt", "updatedAt", "scheduledAt", "appointmentTime"))
            .metadata(Map.of("html_type", "datetime-local", "db_type", "TIMESTAMP"))
            .build());

        // Boolean field
        schemas.add(SchemaDefinition.builder()
            .id("field-type-boolean")
            .name("Boolean Field")
            .type("field_type")
            .description("True/false toggle or checkbox field.")
            .category("field_type")
            .examples(List.of("isActive", "isVerified", "acceptTerms", "isPublished"))
            .metadata(Map.of("html_type", "checkbox", "db_type", "BOOLEAN"))
            .build());

        // Select field
        schemas.add(SchemaDefinition.builder()
            .id("field-type-select")
            .name("Select/Dropdown Field")
            .type("field_type")
            .description("Single selection from predefined options. Define options as array of strings or objects.")
            .category("field_type")
            .examples(List.of(
                "status: ['pending', 'approved', 'rejected']",
                "category: ['electronics', 'clothing', 'food']",
                "priority: ['low', 'medium', 'high']"
            ))
            .metadata(Map.of("html_type", "select", "db_type", "VARCHAR"))
            .build());

        // Multi-select field
        schemas.add(SchemaDefinition.builder()
            .id("field-type-multiselect")
            .name("Multi-Select Field")
            .type("field_type")
            .description("Multiple selection from predefined options. Values stored as JSON array.")
            .category("field_type")
            .examples(List.of(
                "tags: ['urgent', 'important', 'review']",
                "skills: ['java', 'python', 'javascript']"
            ))
            .metadata(Map.of("html_type", "select-multiple", "db_type", "JSON"))
            .build());

        // Reference field
        schemas.add(SchemaDefinition.builder()
            .id("field-type-reference")
            .name("Reference/Relation Field")
            .type("field_type")
            .description("Reference to another entity. Creates foreign key relationship.")
            .category("field_type")
            .examples(List.of(
                "customerId references Customer",
                "categoryId references Category",
                "assigneeId references User"
            ))
            .metadata(Map.of("html_type", "lookup", "db_type", "BIGINT", "creates_fk", true))
            .build());

        // Textarea field
        schemas.add(SchemaDefinition.builder()
            .id("field-type-textarea")
            .name("Textarea/Long Text Field")
            .type("field_type")
            .description("Multi-line text input for longer content. Maps to TEXT in database.")
            .category("field_type")
            .examples(List.of("description", "notes", "bio", "comments"))
            .metadata(Map.of("html_type", "textarea", "db_type", "TEXT"))
            .build());

        // Rich text field
        schemas.add(SchemaDefinition.builder()
            .id("field-type-richtext")
            .name("Rich Text Field")
            .type("field_type")
            .description("Rich text editor with formatting support (bold, italic, lists, etc.).")
            .category("field_type")
            .examples(List.of("content", "body", "article", "htmlContent"))
            .metadata(Map.of("html_type", "richtext", "db_type", "TEXT"))
            .build());

        // URL field
        schemas.add(SchemaDefinition.builder()
            .id("field-type-url")
            .name("URL Field")
            .type("field_type")
            .description("URL input with format validation.")
            .category("field_type")
            .examples(List.of("website", "linkedinUrl", "imageUrl", "documentUrl"))
            .metadata(Map.of("html_type", "url", "db_type", "VARCHAR"))
            .build());

        // Currency field
        schemas.add(SchemaDefinition.builder()
            .id("field-type-currency")
            .name("Currency Field")
            .type("field_type")
            .description("Monetary value with currency formatting. Supports currency symbol configuration.")
            .category("field_type")
            .examples(List.of("price", "salary", "totalAmount", "discount"))
            .metadata(Map.of("html_type", "number", "db_type", "DECIMAL(10,2)"))
            .build());

        // Percentage field
        schemas.add(SchemaDefinition.builder()
            .id("field-type-percentage")
            .name("Percentage Field")
            .type("field_type")
            .description("Percentage value (0-100). Displays with % symbol.")
            .category("field_type")
            .examples(List.of("discount", "completion", "taxRate", "margin"))
            .metadata(Map.of("html_type", "number", "db_type", "DECIMAL(5,2)"))
            .build());

        // File/Image field
        schemas.add(SchemaDefinition.builder()
            .id("field-type-file")
            .name("File/Image Field")
            .type("field_type")
            .description("File upload field. Stores file reference (URL or path).")
            .category("field_type")
            .examples(List.of("avatar", "document", "attachment", "logo"))
            .metadata(Map.of("html_type", "file", "db_type", "VARCHAR"))
            .build());

        // JSON field
        schemas.add(SchemaDefinition.builder()
            .id("field-type-json")
            .name("JSON Field")
            .type("field_type")
            .description("Structured JSON data field for complex nested data.")
            .category("field_type")
            .examples(List.of("metadata", "settings", "preferences", "config"))
            .metadata(Map.of("html_type", "json", "db_type", "JSON"))
            .build());

        return indexSchemas(schemas, "field-types");
    }

    // ==================== UI COMPONENTS ====================
    
    private int loadUIComponents() {
        List<SchemaDefinition> schemas = new ArrayList<>();

        // Container
        schemas.add(SchemaDefinition.builder()
            .id("component-container")
            .name("Container Component")
            .type("component")
            .description("Layout container for grouping components. Supports flex and grid layouts.")
            .category("component")
            .examples(List.of(
                "<container layout=\"flex\" direction=\"column\">",
                "Container with padding and border",
                "Nested containers for complex layouts"
            ))
            .metadata(Map.of("category", "layout"))
            .build());

        // Text
        schemas.add(SchemaDefinition.builder()
            .id("component-text")
            .name("Text Component")
            .type("component")
            .description("Display text content. Supports headings (h1-h6), paragraphs, and styled text.")
            .category("component")
            .examples(List.of("Heading text", "Paragraph text", "Bold and italic text"))
            .metadata(Map.of("category", "basic"))
            .build());

        // Button
        schemas.add(SchemaDefinition.builder()
            .id("component-button")
            .name("Button Component")
            .type("component")
            .description("Interactive button. Types: primary, secondary, danger, ghost. Supports icons and loading state.")
            .category("component")
            .examples(List.of("Submit button", "Cancel button", "Delete button with confirmation"))
            .metadata(Map.of("category", "basic", "interactive", true))
            .build());

        // Input
        schemas.add(SchemaDefinition.builder()
            .id("component-input")
            .name("Input Component")
            .type("component")
            .description("Text input field. Supports label, placeholder, validation, and helper text.")
            .category("component")
            .examples(List.of("Text input with label", "Email input with validation", "Password input"))
            .metadata(Map.of("category", "form", "form_element", true))
            .build());

        // Select/Dropdown
        schemas.add(SchemaDefinition.builder()
            .id("component-select")
            .name("Select/Dropdown Component")
            .type("component")
            .description("Dropdown selection. Supports single and multi-select modes.")
            .category("component")
            .examples(List.of("Country selector", "Category dropdown", "Multi-select tags"))
            .metadata(Map.of("category", "form", "form_element", true))
            .build());

        // Table
        schemas.add(SchemaDefinition.builder()
            .id("component-table")
            .name("Table Component")
            .type("component")
            .description("Data table with sorting, filtering, and pagination. Binds to entity data.")
            .category("component")
            .examples(List.of(
                "Customer list table",
                "Order table with status filtering",
                "Product table with inline editing"
            ))
            .metadata(Map.of("category", "data", "supports_binding", true))
            .build());

        // Form
        schemas.add(SchemaDefinition.builder()
            .id("component-form")
            .name("Form Component")
            .type("component")
            .description("Form container that handles submission and validation. Auto-generates fields from entity schema.")
            .category("component")
            .examples(List.of(
                "Customer registration form",
                "Order creation form",
                "Settings form with sections"
            ))
            .metadata(Map.of("category", "form", "auto_generates", true))
            .build());

        // Card
        schemas.add(SchemaDefinition.builder()
            .id("component-card")
            .name("Card Component")
            .type("component")
            .description("Card container with optional header, body, and footer sections. Good for detail views.")
            .category("component")
            .examples(List.of("Profile card", "Product card", "Stats card"))
            .metadata(Map.of("category", "layout"))
            .build());

        // Grid
        schemas.add(SchemaDefinition.builder()
            .id("component-grid")
            .name("Grid Component")
            .type("component")
            .description("CSS Grid layout for responsive multi-column layouts. Configurable columns and gaps.")
            .category("component")
            .examples(List.of("2-column form", "3-column card grid", "Dashboard layout"))
            .metadata(Map.of("category", "layout"))
            .build());

        // Modal
        schemas.add(SchemaDefinition.builder()
            .id("component-modal")
            .name("Modal/Dialog Component")
            .type("component")
            .description("Modal dialog for overlays. Supports different sizes and close behaviors.")
            .category("component")
            .examples(List.of("Confirmation modal", "Form modal", "Detail view modal"))
            .metadata(Map.of("category", "overlay"))
            .build());

        // Tabs
        schemas.add(SchemaDefinition.builder()
            .id("component-tabs")
            .name("Tabs Component")
            .type("component")
            .description("Tabbed interface for organizing content into sections.")
            .category("component")
            .examples(List.of("Profile tabs (info, settings, activity)", "Product tabs (details, reviews)"))
            .metadata(Map.of("category", "navigation"))
            .build());

        // Navigation
        schemas.add(SchemaDefinition.builder()
            .id("component-navigation")
            .name("Navigation Component")
            .type("component")
            .description("Top navigation bar with logo, menu items, and user menu.")
            .category("component")
            .examples(List.of("Main navigation", "Admin navigation with dropdown"))
            .metadata(Map.of("category", "navigation"))
            .build());

        // Sidebar
        schemas.add(SchemaDefinition.builder()
            .id("component-sidebar")
            .name("Sidebar Component")
            .type("component")
            .description("Side navigation panel with collapsible menu items.")
            .category("component")
            .examples(List.of("Admin sidebar", "Settings sidebar"))
            .metadata(Map.of("category", "navigation"))
            .build());

        // Chart
        schemas.add(SchemaDefinition.builder()
            .id("component-chart")
            .name("Chart Component")
            .type("component")
            .description("Data visualization charts. Types: bar, line, pie, donut, area.")
            .category("component")
            .examples(List.of("Sales bar chart", "Revenue line chart", "Category pie chart"))
            .metadata(Map.of("category", "data", "supports_binding", true))
            .build());

        // Image
        schemas.add(SchemaDefinition.builder()
            .id("component-image")
            .name("Image Component")
            .type("component")
            .description("Image display with alt text and sizing options.")
            .category("component")
            .examples(List.of("Avatar image", "Product image", "Hero image"))
            .metadata(Map.of("category", "media"))
            .build());

        return indexSchemas(schemas, "ui-components");
    }

    // ==================== PAGE TYPES ====================
    
    private int loadPageTypes() {
        List<SchemaDefinition> schemas = new ArrayList<>();

        schemas.add(SchemaDefinition.builder()
            .id("page-list")
            .name("List Page")
            .type("page")
            .description("Page that displays a list of entity records in a table. Includes search, filter, and pagination.")
            .category("page")
            .examples(List.of("Customer list", "Order list", "Product catalog"))
            .metadata(Map.of("template", "list", "components", List.of("table", "search", "filters")))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("page-form")
            .name("Form Page")
            .type("page")
            .description("Page with a form for creating or editing entity records.")
            .category("page")
            .examples(List.of("New customer form", "Edit order form", "User registration"))
            .metadata(Map.of("template", "form", "components", List.of("form", "buttons")))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("page-detail")
            .name("Detail Page")
            .type("page")
            .description("Page that displays detailed information about a single entity record.")
            .category("page")
            .examples(List.of("Customer profile", "Order details", "Product details"))
            .metadata(Map.of("template", "detail", "components", List.of("card", "tabs")))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("page-dashboard")
            .name("Dashboard Page")
            .type("page")
            .description("Page with multiple widgets, charts, and KPIs for overview.")
            .category("page")
            .examples(List.of("Sales dashboard", "Admin dashboard", "Analytics dashboard"))
            .metadata(Map.of("template", "dashboard", "components", List.of("cards", "charts", "tables")))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("page-login")
            .name("Login Page")
            .type("page")
            .description("Authentication page with email/password login form.")
            .category("page")
            .examples(List.of("User login", "Admin login"))
            .metadata(Map.of("template", "login", "auth_required", false))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("page-signup")
            .name("Sign Up Page")
            .type("page")
            .description("User registration page with form and validation.")
            .category("page")
            .examples(List.of("User registration", "Account signup"))
            .metadata(Map.of("template", "signup", "auth_required", false))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("page-landing")
            .name("Landing Page")
            .type("page")
            .description("Marketing landing page with hero section, features, and CTA.")
            .category("page")
            .examples(List.of("Product landing", "Company homepage"))
            .metadata(Map.of("template", "landing", "public", true))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("page-profile")
            .name("Profile Page")
            .type("page")
            .description("User profile page with avatar, bio, and settings.")
            .category("page")
            .examples(List.of("User profile", "Team member profile"))
            .metadata(Map.of("template", "profile"))
            .build());

        return indexSchemas(schemas, "page-types");
    }

    // ==================== SECURITY FEATURES ====================
    
    private int loadSecurityFeatures() {
        List<SchemaDefinition> schemas = new ArrayList<>();

        schemas.add(SchemaDefinition.builder()
            .id("security-overview")
            .name("Security Overview")
            .type("security")
            .description("AppBana provides enterprise-grade security: BCrypt password hashing, CSRF protection, " +
                "session management, rate limiting, and field-level security. All 156 security tests passing.")
            .category("security")
            .examples(List.of(
                "Password hashing with BCrypt (work factor 12)",
                "CSRF tokens on all POST/PUT/DELETE",
                "Session timeout with sliding window"
            ))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("security-csrf")
            .name("CSRF Protection")
            .type("security")
            .description("Cross-Site Request Forgery protection with 256-bit tokens. " +
                "Tokens auto-expire after 30 minutes. All state-changing requests require valid token.")
            .category("security")
            .examples(List.of(
                "X-CSRF-Token header required",
                "Token fetched via GET /api/csrf/token",
                "Auto-refresh on form mount"
            ))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("security-session")
            .name("Session Management")
            .type("security")
            .description("Secure session management with 30-minute sliding window timeout. " +
                "Sessions auto-renew on activity. Stored in X-Session-Token header.")
            .category("security")
            .examples(List.of(
                "30-minute inactivity timeout",
                "Sliding window renewal",
                "Secure session storage"
            ))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("security-rate-limiting")
            .name("Rate Limiting")
            .type("security")
            .description("Rate limiting at 100 requests per minute per IP per endpoint. " +
                "Prevents brute force and DDoS attacks.")
            .category("security")
            .examples(List.of(
                "100 req/min per endpoint",
                "429 Too Many Requests response",
                "Retry-After header provided"
            ))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("security-fls")
            .name("Field-Level Security")
            .type("security")
            .description("Fine-grained access control at field level. Hide or mask sensitive fields " +
                "based on user roles. HIPAA and PCI-DSS compliant.")
            .category("security")
            .examples(List.of(
                "Hide SSN field from non-admin users",
                "Mask credit card numbers",
                "Role-based field visibility"
            ))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("security-rbac")
            .name("Role-Based Access Control")
            .type("security")
            .description("RBAC with Users, Roles, and Permissions. Assign roles to users, " +
                "permissions to roles. Check permissions on API and UI level.")
            .category("security")
            .examples(List.of(
                "Admin role with full access",
                "Editor role with create/edit",
                "Viewer role with read-only"
            ))
            .build());

        return indexSchemas(schemas, "security-features");
    }

    // ==================== WORKFLOW KNOWLEDGE ====================
    
    private int loadWorkflowKnowledge() {
        List<SchemaDefinition> schemas = new ArrayList<>();

        schemas.add(SchemaDefinition.builder()
            .id("workflow-overview")
            .name("Workflow Automation")
            .type("workflow")
            .description("AppBana supports workflow automation with state machines. " +
                "Define workflows with states, transitions, and conditions. " +
                "Workflows trigger on entity events (create, update, delete).")
            .category("workflow")
            .examples(List.of(
                "Order approval workflow",
                "Leave request approval",
                "Document review process"
            ))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("workflow-states")
            .name("Workflow States")
            .type("workflow")
            .description("Workflow states represent the status of a process. " +
                "Types: USER_TASK (human action), SERVICE_TASK (automatic), DECISION (branching), WAIT (delay).")
            .category("workflow")
            .examples(List.of(
                "Draft -> Pending Review -> Approved/Rejected",
                "New -> In Progress -> Done",
                "Submitted -> Under Review -> Approved"
            ))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("workflow-transitions")
            .name("Workflow Transitions")
            .type("workflow")
            .description("Transitions connect states and define when to move between them. " +
                "Can have conditions, role requirements, and automatic triggers.")
            .category("workflow")
            .examples(List.of(
                "Approve: Pending -> Approved (requires Manager role)",
                "Auto-reject: if amount > limit",
                "Escalate: after 24 hours"
            ))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("workflow-triggers")
            .name("Workflow Triggers")
            .type("workflow")
            .description("Workflows can be triggered automatically on entity events: " +
                "ON_CREATE, ON_UPDATE, ON_DELETE, or MANUAL trigger.")
            .category("workflow")
            .examples(List.of(
                "Start approval on order creation",
                "Notify on status change",
                "Archive after deletion"
            ))
            .build());

        return indexSchemas(schemas, "workflow-knowledge");
    }

    // ==================== API KNOWLEDGE ====================
    
    private int loadAPIKnowledge() {
        List<SchemaDefinition> schemas = new ArrayList<>();

        schemas.add(SchemaDefinition.builder()
            .id("api-crud")
            .name("CRUD API Endpoints")
            .type("api")
            .description("Automatic REST APIs for all entities. " +
                "Base pattern: /api/{tenantId}/{appId}/{entity}")
            .category("api")
            .examples(List.of(
                "GET /api/t1/app1/customer - List all customers",
                "POST /api/t1/app1/customer - Create customer",
                "GET /api/t1/app1/customer/123 - Get customer by ID",
                "PUT /api/t1/app1/customer/123 - Update customer",
                "DELETE /api/t1/app1/customer/123 - Delete customer"
            ))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("api-query-params")
            .name("API Query Parameters")
            .type("api")
            .description("Filter, sort, and paginate API results using query parameters.")
            .category("api")
            .examples(List.of(
                "?limit=10&offset=20 - Pagination",
                "?sort=name:asc - Sorting",
                "?name=John - Filtering",
                "?_fields=id,name,email - Projection"
            ))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("api-batch")
            .name("Batch Operations")
            .type("api")
            .description("Batch create, update, or delete multiple records in single request.")
            .category("api")
            .examples(List.of(
                "POST /api/.../customer with array body",
                "DELETE /api/.../customer?ids=1,2,3"
            ))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("api-schema")
            .name("Schema API")
            .type("api")
            .description("APIs for managing entity schemas and database structure.")
            .category("api")
            .examples(List.of(
                "GET /schema - List all schemas",
                "POST /schema - Create/update schema",
                "GET /schema/{name} - Get schema definition"
            ))
            .build());

        return indexSchemas(schemas, "api-knowledge");
    }

    // ==================== MULTI-TENANT KNOWLEDGE ====================
    
    private int loadMultiTenantKnowledge() {
        List<SchemaDefinition> schemas = new ArrayList<>();

        schemas.add(SchemaDefinition.builder()
            .id("multitenant-overview")
            .name("Multi-Tenant Architecture")
            .type("architecture")
            .description("AppBana uses physical table isolation for multi-tenancy. " +
                "Each app gets separate database tables with naming: app_{appId}_{entityName}. " +
                "This provides complete data isolation and better performance.")
            .category("architecture")
            .examples(List.of(
                "Table: app_abc123_customer",
                "Zero data leakage between tenants",
                "No tenant_id filtering needed"
            ))
            .metadata(Map.of(
                "isolation_type", "physical_tables",
                "performance_benefit", "20-30% faster queries"
            ))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("multitenant-publish")
            .name("App Publishing")
            .type("feature")
            .description("Publishing an app creates the physical database tables and deploys the runtime. " +
                "Transactional deployment ensures consistency. Version tracking for rollback.")
            .category("feature")
            .examples(List.of(
                "Click Publish in Studio",
                "Tables created automatically",
                "Runtime available immediately"
            ))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("multitenant-context")
            .name("Tenant Context")
            .type("architecture")
            .description("TenantContext ThreadLocal holds current tenant/app context. " +
                "Set on request entry, cleared on exit. All operations use this context.")
            .category("architecture")
            .examples(List.of(
                "TenantContext.set(tenantId, appId)",
                "EntityCrudService uses context for table names",
                "Always clear in finally block"
            ))
            .build());

        return indexSchemas(schemas, "multitenant-knowledge");
    }

    // ==================== STUDIO BUILDER KNOWLEDGE ====================
    
    private int loadStudioBuilderKnowledge() {
        List<SchemaDefinition> schemas = new ArrayList<>();

        schemas.add(SchemaDefinition.builder()
            .id("studio-overview")
            .name("Studio Builder")
            .type("feature")
            .description("Visual application builder with drag-and-drop interface. " +
                "Create apps, define entities, design pages, and publish - all without coding.")
            .category("studio")
            .examples(List.of(
                "Access at /studio",
                "3-panel layout: components, canvas, properties",
                "Live preview of changes"
            ))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("studio-app-manager")
            .name("App Manager")
            .type("feature")
            .description("Create and manage applications. Each app has entities, pages, and settings.")
            .category("studio")
            .examples(List.of(
                "Create new app with name and description",
                "Switch between apps",
                "Delete apps"
            ))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("studio-page-builder")
            .name("Page Builder")
            .type("feature")
            .description("Visual page designer with drag-drop components. " +
                "8 pre-built templates available. Properties panel for customization.")
            .category("studio")
            .examples(List.of(
                "Drag button to canvas",
                "Edit properties in right panel",
                "Preview with Cmd+Enter"
            ))
            .metadata(Map.of("templates", 8))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("studio-entity-builder")
            .name("Entity Builder")
            .type("feature")
            .description("Define entities with fields visually. Add fields, set types, configure validations.")
            .category("studio")
            .examples(List.of(
                "Add field: name (text, required)",
                "Add field: email (email, required)",
                "Add field: status (select with options)"
            ))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("studio-keyboard-shortcuts")
            .name("Keyboard Shortcuts")
            .type("feature")
            .description("Keyboard shortcuts for efficient Studio usage.")
            .category("studio")
            .examples(List.of(
                "Cmd+S - Save",
                "Cmd+Z - Undo",
                "Cmd+D - Duplicate",
                "Delete - Remove component",
                "Cmd+P - Search palette"
            ))
            .build());

        return indexSchemas(schemas, "studio-knowledge");
    }

    // ==================== DATA BINDING PATTERNS ====================
    
    private int loadDataBindingPatterns() {
        List<SchemaDefinition> schemas = new ArrayList<>();

        schemas.add(SchemaDefinition.builder()
            .id("binding-property")
            .name("Property Binding")
            .type("pattern")
            .description("Bind component properties to data. Use ${entity.field} syntax.")
            .category("binding")
            .examples(List.of(
                "${customer.name} - Display customer name",
                "${order.total} - Display order total",
                "${user.isActive} - Boolean binding"
            ))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("binding-list")
            .name("List Binding")
            .type("pattern")
            .description("Bind lists/arrays to table or repeater components.")
            .category("binding")
            .examples(List.of(
                "Table bound to customers list",
                "Card repeater for products",
                "Select options from API"
            ))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("binding-form")
            .name("Form Binding")
            .type("pattern")
            .description("Two-way binding for form inputs. Changes update the model automatically.")
            .category("binding")
            .examples(List.of(
                "Input bound to customer.name",
                "Select bound to order.status",
                "Checkbox bound to user.isActive"
            ))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("binding-event")
            .name("Event Binding")
            .type("pattern")
            .description("Bind events to actions. Common events: click, change, submit.")
            .category("binding")
            .examples(List.of(
                "@click=${handleSubmit}",
                "@change=${updateField}",
                "@submit=${saveForm}"
            ))
            .build());

        return indexSchemas(schemas, "binding-patterns");
    }

    // ==================== VALIDATION RULES ====================
    
    private int loadValidationRules() {
        List<SchemaDefinition> schemas = new ArrayList<>();

        schemas.add(SchemaDefinition.builder()
            .id("validation-required")
            .name("Required Validation")
            .type("validation")
            .description("Field must have a value. Shows error if empty on submit.")
            .category("validation")
            .examples(List.of(
                "{ \"required\": true }",
                "name: required",
                "Error: This field is required"
            ))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("validation-length")
            .name("Length Validation")
            .type("validation")
            .description("Validate text length with minLength and maxLength constraints.")
            .category("validation")
            .examples(List.of(
                "{ \"minLength\": 3, \"maxLength\": 100 }",
                "password: minLength 8",
                "description: maxLength 500"
            ))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("validation-range")
            .name("Range Validation")
            .type("validation")
            .description("Validate numeric values with min and max constraints.")
            .category("validation")
            .examples(List.of(
                "{ \"min\": 0, \"max\": 100 }",
                "age: min 18",
                "quantity: min 1"
            ))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("validation-pattern")
            .name("Pattern Validation")
            .type("validation")
            .description("Validate using regular expression pattern.")
            .category("validation")
            .examples(List.of(
                "{ \"pattern\": \"^[A-Z]{2}[0-9]{4}$\" }",
                "zipCode: pattern ^\\d{5}$",
                "phone: pattern ^\\+?[0-9]{10,}$"
            ))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("validation-email")
            .name("Email Validation")
            .type("validation")
            .description("Built-in email format validation.")
            .category("validation")
            .examples(List.of(
                "type: email",
                "Validates: user@example.com",
                "Rejects: invalid-email"
            ))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("validation-url")
            .name("URL Validation")
            .type("validation")
            .description("Built-in URL format validation.")
            .category("validation")
            .examples(List.of(
                "type: url",
                "Validates: https://example.com",
                "Rejects: not-a-url"
            ))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("validation-unique")
            .name("Unique Validation")
            .type("validation")
            .description("Ensure field value is unique across all records.")
            .category("validation")
            .examples(List.of(
                "{ \"unique\": true }",
                "email: unique",
                "username: unique"
            ))
            .build());

        return indexSchemas(schemas, "validation-rules");
    }

    // ==================== BEST PRACTICES ====================
    
    private int loadBestPractices() {
        List<SchemaDefinition> schemas = new ArrayList<>();

        schemas.add(SchemaDefinition.builder()
            .id("practice-entity-naming")
            .name("Entity Naming Conventions")
            .type("best_practice")
            .description("Use singular PascalCase for entity names. Use camelCase for field names.")
            .category("best_practice")
            .examples(List.of(
                "Entity: Customer (not customers)",
                "Field: firstName (not first_name)",
                "Entity: OrderItem (not order_item)"
            ))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("practice-field-design")
            .name("Field Design Best Practices")
            .type("best_practice")
            .description("Always add validations. Use appropriate field types. Consider required fields.")
            .category("best_practice")
            .examples(List.of(
                "Use email type for email fields",
                "Add required to essential fields",
                "Use select for limited options"
            ))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("practice-page-design")
            .name("Page Design Best Practices")
            .type("best_practice")
            .description("Start with templates. Keep pages focused. Use consistent layouts.")
            .category("best_practice")
            .examples(List.of(
                "One form per page",
                "List -> Detail navigation",
                "Dashboard for overview"
            ))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("practice-security")
            .name("Security Best Practices")
            .type("best_practice")
            .description("Use RBAC for access control. Apply field-level security for sensitive data. " +
                "Always validate on server side.")
            .category("best_practice")
            .examples(List.of(
                "Hide SSN from non-admin roles",
                "Require authentication for write operations",
                "Rate limit public endpoints"
            ))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("practice-workflow")
            .name("Workflow Best Practices")
            .type("best_practice")
            .description("Keep workflows simple. Use meaningful state names. Always have an end state.")
            .category("best_practice")
            .examples(List.of(
                "Maximum 5-7 states",
                "Clear state names: Draft, Pending, Approved",
                "Handle rejection paths"
            ))
            .build());

        return indexSchemas(schemas, "best-practices");
    }

    // ==================== TEMPLATES ====================
    
    private int loadTemplates() {
        List<SchemaDefinition> schemas = new ArrayList<>();

        schemas.add(SchemaDefinition.builder()
            .id("template-crm")
            .name("CRM App Template")
            .type("template")
            .description("Customer Relationship Management template with Customer, Contact, and Deal entities.")
            .category("template")
            .examples(List.of(
                "Entities: Customer, Contact, Deal, Activity",
                "Pages: Customer list, Customer detail, Deal pipeline",
                "Workflows: Deal approval, Follow-up reminders"
            ))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("template-inventory")
            .name("Inventory Management Template")
            .type("template")
            .description("Inventory tracking with Product, Category, and StockMovement entities.")
            .category("template")
            .examples(List.of(
                "Entities: Product, Category, Supplier, StockMovement",
                "Pages: Product catalog, Stock levels, Low stock alerts",
                "Features: Barcode support, Stock count"
            ))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("template-hr")
            .name("HR Management Template")
            .type("template")
            .description("Human Resources template with Employee, Department, and LeaveRequest entities.")
            .category("template")
            .examples(List.of(
                "Entities: Employee, Department, LeaveRequest, Attendance",
                "Pages: Employee directory, Leave calendar, Approvals",
                "Workflows: Leave approval, Onboarding"
            ))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("template-project")
            .name("Project Management Template")
            .type("template")
            .description("Project tracking with Project, Task, and Milestone entities.")
            .category("template")
            .examples(List.of(
                "Entities: Project, Task, Milestone, Team",
                "Pages: Project board, Task list, Timeline view",
                "Features: Kanban board, Time tracking"
            ))
            .build());

        schemas.add(SchemaDefinition.builder()
            .id("template-support")
            .name("Support Ticket Template")
            .type("template")
            .description("Customer support with Ticket, Customer, and Agent entities.")
            .category("template")
            .examples(List.of(
                "Entities: Ticket, Customer, Agent, Comment",
                "Pages: Ticket list, Ticket detail, Agent dashboard",
                "Workflows: Ticket assignment, Escalation"
            ))
            .build());

        return indexSchemas(schemas, "templates");
    }

    // ==================== HELPER METHODS ====================
    
    private int indexSchemas(List<SchemaDefinition> schemas, String category) {
        int count = 0;
        for (SchemaDefinition schema : schemas) {
            try {
                knowledgeBaseService.indexSchema(schema);
                count++;
                log.debug("Indexed: {} ({})", schema.getName(), category);
            } catch (Exception e) {
                log.error("Failed to index {}: {}", schema.getId(), e.getMessage());
            }
        }
        log.info("Loaded {} entries for category: {}", count, category);
        return count;
    }
}
