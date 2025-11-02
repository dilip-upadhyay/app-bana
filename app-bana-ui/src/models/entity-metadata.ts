/**
 * Entity Metadata Models
 * Business-friendly abstraction layer over technical schemas
 * 
 * Philosophy: Power users can model ANY business domain without SQL knowledge
 * Once entities are defined, metadata drives auto-generation of everything
 */

/**
 * Business field types (user-friendly naming)
 * Maps to technical SQL types under the hood
 */
export type EntityFieldType =
  // Text types
  | 'text'              // Short text (VARCHAR 255) - names, titles
  | 'longtext'          // Long text (TEXT) - descriptions, notes
  | 'email'             // Email with validation
  | 'phone'             // Phone number with formatting
  | 'url'               // URL with validation
  | 'color'             // Color picker (#RRGGBB)
  
  // Numeric types
  | 'number'            // Integer (BIGINT)
  | 'decimal'           // Decimal/float (DECIMAL)
  | 'currency'          // Money (DECIMAL 19,4)
  | 'percentage'        // Percentage (0-100)
  
  // Date/Time types
  | 'date'              // Date only (DATE)
  | 'datetime'          // Date and time (TIMESTAMP)
  | 'time'              // Time only (TIME)
  | 'duration'          // Duration in minutes/hours
  
  // Selection types
  | 'boolean'           // Yes/No checkbox (BOOLEAN)
  | 'status'            // Dropdown with predefined options (VARCHAR + CHECK)
  | 'radio'             // Radio buttons (VARCHAR + CHECK)
  | 'multiselect'       // Multiple selections (JSON or junction table)
  
  // Rich types
  | 'file'              // File upload (stores URL/path)
  | 'image'             // Image upload with preview
  | 'json'              // JSON data (JSON/TEXT)
  | 'markdown'          // Markdown editor
  | 'richtext'          // WYSIWYG editor
  
  // Relationship types
  | 'reference'         // Foreign key (BIGINT) - links to another entity
  | 'lookup'            // Same as reference but shows related data
  
  // Calculated/System types
  | 'formula'           // Calculated field (computed)
  | 'autoincrement'     // Auto-incrementing ID (BIGINT AUTO_INCREMENT)
  | 'uuid'              // UUID/GUID (VARCHAR 36)
  | 'createdAt'         // Auto-set creation timestamp
  | 'updatedAt'         // Auto-update modified timestamp
  | 'createdBy'         // Auto-set creator user ID
  | 'updatedBy';        // Auto-update modifier user ID

/**
 * Validation rules for entity fields
 * Business-friendly validation without regex knowledge
 */
export interface EntityFieldValidation {
  // Text validation
  minLength?: number;
  maxLength?: number;
  pattern?: string;           // Regex pattern (for power users)
  format?: 'email' | 'phone' | 'url' | 'ssn' | 'zip' | 'creditcard';
  
  // Numeric validation
  min?: number;
  max?: number;
  step?: number;              // Increment step (e.g., 0.01 for currency)
  
  // Custom validation
  customMessage?: string;     // User-friendly error message
  customRule?: string;        // JavaScript expression (for power users)
  
  // List validation (for status/multiselect)
  allowedValues?: string[];   // Whitelist of allowed values
}

/**
 * Display configuration for entity fields
 * Controls how fields appear in generated UIs
 */
export interface EntityFieldDisplay {
  label?: string;             // Display label (auto-generated from name if not provided)
  placeholder?: string;       // Placeholder text for inputs
  helpText?: string;          // Tooltip/help text
  icon?: string;              // Icon (emoji or icon name)
  
  // UI behavior
  hidden?: boolean;           // Hide from forms (but store in DB)
  readOnly?: boolean;         // Display only, not editable
  showInTable?: boolean;      // Show in list/table views (default: true)
  showInForm?: boolean;       // Show in create/edit forms (default: true)
  showInDetail?: boolean;     // Show in detail/read views (default: true)
  
  // Layout hints
  width?: 'full' | 'half' | 'third' | 'quarter'; // Field width in forms
  order?: number;             // Display order (lower = earlier)
  group?: string;             // Group name (for field grouping in UI)
}

/**
 * Entity field definition
 * Each field represents a data attribute of the business entity
 */
export interface EntityField {
  id: string;                 // Unique field identifier within entity
  name: string;               // Technical name (camelCase, used in code/DB)
  type: EntityFieldType;      // Field type
  
  // Core properties
  required: boolean;          // Is this field required?
  unique: boolean;            // Must values be unique?
  indexed?: boolean;          // Create database index for faster queries
  defaultValue?: any;         // Default value for new records
  
  // Validation
  validation?: EntityFieldValidation;
  
  // Display configuration
  display?: EntityFieldDisplay;
  
  // Reference configuration (for reference/lookup types)
  referenceEntity?: string;   // Entity ID this field references
  referenceDisplay?: string;  // Field from referenced entity to display (default: 'name')
  cascadeDelete?: boolean;    // Delete this record when referenced record is deleted
  
  // Status/Select configuration (for status/radio/multiselect types)
  options?: EntityFieldOption[]; // Available options for selection fields
  
  // Formula configuration (for formula type)
  formula?: string;           // JavaScript expression (e.g., "price * quantity")
  formulaDependencies?: string[]; // Field names this formula depends on
  
  // System metadata
  created?: number;           // Creation timestamp
  updated?: number;           // Last update timestamp
  metadata?: Record<string, any>; // Custom metadata
}

/**
 * Option for status/radio/multiselect fields
 */
export interface EntityFieldOption {
  value: string;              // Internal value
  label: string;              // Display label
  color?: string;             // Color for status badges (#RRGGBB)
  icon?: string;              // Icon (emoji or icon name)
  order?: number;             // Display order
}

/**
 * Entity relationship types
 * Defines how entities are connected
 */
export type RelationshipType =
  | 'one-to-one'              // 1:1 - Each record relates to exactly one other
  | 'one-to-many'             // 1:N - One record relates to many others
  | 'many-to-one'             // N:1 - Many records relate to one other
  | 'many-to-many';           // N:M - Many records relate to many others (junction table)

/**
 * Entity relationship definition
 * Defines connections between entities
 */
export interface EntityRelationship {
  id: string;                 // Unique relationship identifier
  name: string;               // Relationship name (e.g., "Customer has Orders")
  type: RelationshipType;
  
  // Entities involved
  fromEntity: string;         // Source entity ID
  toEntity: string;           // Target entity ID
  
  // Foreign key configuration
  fromField?: string;         // Field in source entity (for many-to-one, many-to-many)
  toField?: string;           // Field in target entity (usually 'id')
  
  // Junction table (for many-to-many)
  junctionTable?: string;     // Name of junction table
  junctionFromField?: string; // Foreign key to source entity
  junctionToField?: string;   // Foreign key to target entity
  
  // Behavior
  cascadeDelete?: boolean;    // Delete related records when source is deleted
  required?: boolean;         // Is relationship required?
  
  // Display
  displayName?: string;       // Human-readable name (auto-generated if not provided)
  inverseDisplayName?: string; // Name from opposite direction (e.g., "Order belongs to Customer")
  
  // System metadata
  created?: number;
  updated?: number;
  metadata?: Record<string, any>;
}

/**
 * Entity business rules
 * Declarative rules for business logic
 */
export interface EntityRule {
  id: string;                 // Unique rule identifier
  name: string;               // Rule name
  description?: string;       // Rule description
  
  // Trigger
  trigger: 'before-create' | 'after-create' | 
           'before-update' | 'after-update' | 
           'before-delete' | 'after-delete' |
           'on-change';       // When does this rule fire?
  
  // Conditions (when to apply rule)
  condition?: string;         // JavaScript expression (e.g., "status === 'approved'")
  
  // Actions
  actions: EntityRuleAction[];
  
  // Control
  enabled: boolean;           // Is rule active?
  order?: number;             // Execution order
  
  // System metadata
  created?: number;
  updated?: number;
  metadata?: Record<string, any>;
}

/**
 * Rule action types
 */
export interface EntityRuleAction {
  type: 'set-field' |         // Set field value
        'validate' |          // Custom validation
        'send-email' |        // Send email notification
        'call-webhook' |      // Call external API
        'create-record' |     // Create related record
        'update-record' |     // Update related record
        'show-message';       // Show user message
  
  config: Record<string, any>; // Action-specific configuration
}

/**
 * Entity permissions/security
 * Row-level and field-level security
 */
export interface EntityPermissions {
  // Role-based access
  roles?: {
    [roleName: string]: {
      create?: boolean;
      read?: boolean;
      update?: boolean;
      delete?: boolean;
      fields?: {
        [fieldName: string]: 'read' | 'write' | 'none';
      };
    };
  };
  
  // Row-level security
  rowLevelSecurity?: {
    read?: string;            // JavaScript expression (e.g., "record.ownerId === currentUser.id")
    update?: string;
    delete?: string;
  };
  
  // Audit
  auditLog?: boolean;         // Enable audit logging for this entity
}

/**
 * Entity metadata (business object definition)
 * This is the core abstraction that represents a business concept
 */
export interface EntityMeta {
  id: string;                 // Unique entity identifier (e.g., "customer", "order")
  name: string;               // Technical name (singular, camelCase)
  displayName: string;        // Business-friendly name (e.g., "Customer")
  pluralName?: string;        // Plural form (e.g., "Customers") - auto-generated if not provided
  description?: string;       // Entity description
  
  // Visual
  icon?: string;              // Icon (emoji or icon name)
  color?: string;             // Color for UI (#RRGGBB)
  
  // Fields
  fields: EntityField[];      // Field definitions
  
  // Relationships
  relationships?: EntityRelationship[];
  
  // Business rules
  rules?: EntityRule[];
  
  // Permissions
  permissions?: EntityPermissions;
  
  // Database configuration
  datasource: string;         // Datasource name (default: 'default')
  tableName?: string;         // Database table name (auto-generated from name if not provided)
  schema?: string;            // Database schema (for multi-schema DBs)
  
  // Behavior
  softDelete?: boolean;       // Use soft delete (add 'deleted' flag instead of hard delete)
  versioning?: boolean;       // Enable version history
  
  // UI generation hints
  defaultSort?: {
    field: string;
    direction: 'asc' | 'desc';
  };
  searchableFields?: string[]; // Fields to include in search
  displayField?: string;      // Primary field to display (default: 'name')
  
  // System metadata
  created?: number;           // Creation timestamp
  updated?: number;           // Last update timestamp
  createdBy?: string;         // Creator user ID
  updatedBy?: string;         // Last modifier user ID
  version?: number;           // Entity definition version
  
  // Custom metadata
  metadata?: Record<string, any>;
  
  // Migration state
  migrationState?: 'pending' | 'in-progress' | 'completed' | 'failed';
  lastMigration?: number;     // Last migration timestamp
}

/**
 * Navigation item types
 */
export type NavItemType = 
  | 'entity'                  // Link to entity (auto-generates list/create/edit pages)
  | 'page'                    // Link to custom page
  | 'group'                   // Navigation group/folder
  | 'separator'               // Visual separator
  | 'external';               // External URL

/**
 * Navigation item definition
 */
export interface NavigationItem {
  id: string;                 // Unique nav item ID
  type: NavItemType;
  label: string;              // Display label
  icon?: string;              // Icon (emoji or icon name)
  
  // Target (depends on type)
  entityId?: string;          // For type='entity'
  pageId?: string;            // For type='page'
  url?: string;               // For type='external'
  
  // Hierarchy
  children?: NavigationItem[]; // Nested items (for groups)
  order?: number;             // Display order
  
  // Behavior
  visible?: boolean;          // Show/hide (default: true)
  permission?: string;        // Required permission/role
  badge?: string | number;    // Badge content (e.g., unread count)
  
  // System metadata
  metadata?: Record<string, any>;
}

/**
 * Navigation structure for the app
 */
export interface NavigationMeta {
  items: NavigationItem[];    // Top-level navigation items
  layout?: 'sidebar' | 'topbar' | 'both'; // Navigation layout
  collapsible?: boolean;      // Can sidebar be collapsed?
  defaultExpanded?: boolean;  // Is sidebar expanded by default?
  
  // Branding
  logo?: string;              // Logo URL
  title?: string;             // App title in nav
  
  // System metadata
  metadata?: Record<string, any>;
}

/**
 * Helper functions for field type conversion
 */
export class EntityFieldTypeHelper {
  /**
   * Convert business-friendly field type to technical SQL type
   */
  static toSQLType(fieldType: EntityFieldType, field?: EntityField): string {
    // Text types
    const textTypes: Record<string, string> = {
      'text': field?.validation?.maxLength ? `VARCHAR(${field.validation.maxLength})` : 'VARCHAR(255)',
      'longtext': 'TEXT',
      'email': 'VARCHAR(255)',
      'phone': 'VARCHAR(20)',
      'url': 'VARCHAR(500)',
      'color': 'VARCHAR(7)',
      'markdown': 'TEXT',
      'richtext': 'TEXT',
    };
    
    // Numeric types
    const numericTypes: Record<string, string> = {
      'number': 'BIGINT',
      'decimal': 'DECIMAL(19,4)',
      'currency': 'DECIMAL(19,4)',
      'percentage': 'DECIMAL(5,2)',
      'duration': 'INT',
    };
    
    // Date/Time types
    const dateTypes: Record<string, string> = {
      'date': 'DATE',
      'datetime': 'TIMESTAMP',
      'time': 'TIME',
    };
    
    // Selection types
    const selectionTypes: Record<string, string> = {
      'boolean': 'BOOLEAN',
      'status': 'VARCHAR(50)',
      'radio': 'VARCHAR(50)',
      'multiselect': 'JSON',
    };
    
    // Rich types
    const richTypes: Record<string, string> = {
      'file': 'VARCHAR(500)',
      'image': 'VARCHAR(500)',
      'json': 'JSON',
    };
    
    // Relationship types
    const relationTypes: Record<string, string> = {
      'reference': 'BIGINT',
      'lookup': 'BIGINT',
    };
    
    // System types
    const systemTypes: Record<string, string> = {
      'formula': 'VARCHAR(255)',
      'autoincrement': 'BIGINT AUTO_INCREMENT',
      'uuid': 'VARCHAR(36)',
      'createdAt': 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP',
      'updatedAt': 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP',
      'createdBy': 'BIGINT',
      'updatedBy': 'BIGINT',
    };
    
    // Lookup in all type maps
    return textTypes[fieldType] || 
           numericTypes[fieldType] || 
           dateTypes[fieldType] || 
           selectionTypes[fieldType] || 
           richTypes[fieldType] || 
           relationTypes[fieldType] || 
           systemTypes[fieldType] || 
           'VARCHAR(255)';
  }
  
  /**
   * Get default validation for field type
   */
  static getDefaultValidation(fieldType: EntityFieldType): EntityFieldValidation | undefined {
    switch (fieldType) {
      case 'email': return { format: 'email', maxLength: 255 };
      case 'phone': return { format: 'phone', maxLength: 20 };
      case 'url': return { format: 'url', maxLength: 500 };
      case 'percentage': return { min: 0, max: 100 };
      case 'color': return { pattern: '^#[0-9A-Fa-f]{6}$' };
      default: return undefined;
    }
  }
  
  /**
   * Get display configuration hints for field type
   */
  static getDisplayHints(fieldType: EntityFieldType): Partial<EntityFieldDisplay> {
    switch (fieldType) {
      case 'longtext': return { width: 'full' };
      case 'markdown': return { width: 'full' };
      case 'richtext': return { width: 'full' };
      case 'boolean': return { width: 'quarter' };
      case 'color': return { width: 'quarter' };
      case 'createdAt': return { readOnly: true, showInForm: false };
      case 'updatedAt': return { readOnly: true, showInForm: false };
      case 'createdBy': return { readOnly: true, showInForm: false };
      case 'updatedBy': return { readOnly: true, showInForm: false };
      case 'formula': return { readOnly: true, showInForm: false };
      default: return { width: 'half' };
    }
  }
}

/**
 * Type guards
 */
export function isReferenceField(field: EntityField): boolean {
  return field.type === 'reference' || field.type === 'lookup';
}

export function isCalculatedField(field: EntityField): boolean {
  return field.type === 'formula' || 
         field.type === 'autoincrement' ||
         field.type === 'createdAt' ||
         field.type === 'updatedAt' ||
         field.type === 'createdBy' ||
         field.type === 'updatedBy';
}

export function isSelectionField(field: EntityField): boolean {
  return field.type === 'status' || 
         field.type === 'radio' || 
         field.type === 'multiselect';
}
