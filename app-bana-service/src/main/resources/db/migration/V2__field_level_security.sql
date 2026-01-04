-- ============================================================================
-- Field-Level Security (FLS) Migration
-- Week 1-2 of Phase 1: Enterprise Critical Features
-- Purpose: Granular field permissions for HIPAA/PCI-DSS compliance
-- ============================================================================

-- Create field_permission table
CREATE TABLE IF NOT EXISTS field_permission (
    id VARCHAR(36) PRIMARY KEY,
    role_id VARCHAR(36) NOT NULL,
    entity_name VARCHAR(100) NOT NULL,
    field_name VARCHAR(100) NOT NULL,
    can_read BOOLEAN DEFAULT TRUE,
    can_edit BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE,  -- Disabled for now
    UNIQUE(role_id, entity_name, field_name)
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_field_perm_role ON field_permission(role_id);
CREATE INDEX IF NOT EXISTS idx_field_perm_entity ON field_permission(entity_name);
CREATE INDEX IF NOT EXISTS idx_field_perm_field ON field_permission(field_name);
CREATE INDEX IF NOT EXISTS idx_field_perm_lookup ON field_permission(role_id, entity_name);

-- ============================================================================
-- Seed Default Field Permissions
-- ============================================================================

-- Get role IDs (assumes roles already exist from V1__auth_schema.sql)
-- Admin role: Full access to ALL fields
INSERT INTO field_permission (id, role_id, entity_name, field_name, can_read, can_edit)
SELECT 
    gen_random_uuid() as id,
    r.id as role_id,
    'User' as entity_name,
    '*' as field_name,  -- Wildcard for all fields
    TRUE as can_read,
    TRUE as can_edit
FROM role r WHERE r.name = 'admin';

-- Manager role: Can read all User fields, edit most (not salary/performance_review)
INSERT INTO field_permission (id, role_id, entity_name, field_name, can_read, can_edit)
SELECT 
    gen_random_uuid() as id,
    r.id as role_id,
    'User' as entity_name,
    'name' as field_name,
    TRUE as can_read,
    TRUE as can_edit
FROM role r WHERE r.name = 'manager'
UNION ALL
SELECT 
    gen_random_uuid(), r.id, 'User', 'email', TRUE, TRUE
FROM role r WHERE r.name = 'manager'
UNION ALL
SELECT 
    gen_random_uuid(), r.id, 'User', 'status', TRUE, TRUE
FROM role r WHERE r.name = 'manager'
UNION ALL
SELECT 
    gen_random_uuid(), r.id, 'User', 'salary', TRUE, FALSE  -- Can read but not edit
FROM role r WHERE r.name = 'manager'
UNION ALL
SELECT 
    gen_random_uuid(), r.id, 'User', 'department', TRUE, TRUE
FROM role r WHERE r.name = 'manager';

-- Standard User role: Can only read/edit own basic fields
INSERT INTO field_permission (id, role_id, entity_name, field_name, can_read, can_edit)
SELECT 
    gen_random_uuid() as id,
    r.id as role_id,
    'User' as entity_name,
    'name' as field_name,
    TRUE as can_read,
    TRUE as can_edit
FROM role r WHERE r.name = 'user'
UNION ALL
SELECT 
    gen_random_uuid(), r.id, 'User', 'email', TRUE, FALSE  -- Read-only
FROM role r WHERE r.name = 'user'
UNION ALL
SELECT 
    gen_random_uuid(), r.id, 'User', 'phone', TRUE, TRUE
FROM role r WHERE r.name = 'user';

-- ============================================================================
-- HR Role with Salary Access (if HR role exists)
-- ============================================================================

-- Create HR role if not exists
INSERT INTO role (id, name, description, created_at)
SELECT 
    gen_random_uuid() as id,
    'hr' as name,
    'Human Resources - salary and benefits management' as description,
    CURRENT_TIMESTAMP as created_at
WHERE NOT EXISTS (SELECT 1 FROM role WHERE name = 'hr');

-- HR permissions: Full access to salary, benefits, but not performance reviews
INSERT INTO field_permission (id, role_id, entity_name, field_name, can_read, can_edit)
SELECT 
    gen_random_uuid() as id,
    r.id as role_id,
    'User' as entity_name,
    'name' as field_name,
    TRUE as can_read,
    TRUE as can_edit
FROM role r WHERE r.name = 'hr'
UNION ALL
SELECT 
    gen_random_uuid(), r.id, 'User', 'email', TRUE, TRUE
FROM role r WHERE r.name = 'hr'
UNION ALL
SELECT 
    gen_random_uuid(), r.id, 'User', 'salary', TRUE, TRUE  -- Full salary access
FROM role r WHERE r.name = 'hr'
UNION ALL
SELECT 
    gen_random_uuid(), r.id, 'User', 'benefits', TRUE, TRUE
FROM role r WHERE r.name = 'hr'
UNION ALL
SELECT 
    gen_random_uuid(), r.id, 'User', 'hire_date', TRUE, TRUE
FROM role r WHERE r.name = 'hr';

-- ============================================================================
-- Finance Role with Budget Access
-- ============================================================================

-- Create Finance role if not exists
INSERT INTO role (id, name, description, created_at)
SELECT 
    gen_random_uuid() as id,
    'finance' as name,
    'Finance Team - budget and cost analysis' as description,
    CURRENT_TIMESTAMP as created_at
WHERE NOT EXISTS (SELECT 1 FROM role WHERE name = 'finance');

-- Finance permissions: Read-only access to salary for budget planning
INSERT INTO field_permission (id, role_id, entity_name, field_name, can_read, can_edit)
SELECT 
    gen_random_uuid() as id,
    r.id as role_id,
    'User' as entity_name,
    'name' as field_name,
    TRUE as can_read,
    FALSE as can_edit
FROM role r WHERE r.name = 'finance'
UNION ALL
SELECT 
    gen_random_uuid(), r.id, 'User', 'salary', TRUE, FALSE  -- Read-only for budget
FROM role r WHERE r.name = 'finance'
UNION ALL
SELECT 
    gen_random_uuid(), r.id, 'User', 'department', TRUE, FALSE
FROM role r WHERE r.name = 'finance';

-- ============================================================================
-- View for Effective Field Permissions (combines user's all roles)
-- ============================================================================

CREATE OR REPLACE VIEW v_effective_field_permissions AS
SELECT DISTINCT
    ur.user_id,
    fp.entity_name,
    fp.field_name,
    MAX(CAST(fp.can_read AS INT)) as can_read,  -- TRUE if ANY role grants read
    MAX(CAST(fp.can_edit AS INT)) as can_edit   -- TRUE if ANY role grants edit
FROM user_role ur
INNER JOIN field_permission fp ON ur.role_id = fp.role_id
GROUP BY ur.user_id, fp.entity_name, fp.field_name;

-- ============================================================================
-- Verification Queries (for testing)
-- ============================================================================

-- Test 1: Verify admin has wildcard access
-- SELECT * FROM field_permission fp 
-- INNER JOIN role r ON fp.role_id = r.id 
-- WHERE r.name = 'admin' AND fp.field_name = '*';

-- Test 2: Verify manager can read salary but not edit
-- SELECT * FROM field_permission fp 
-- INNER JOIN role r ON fp.role_id = r.id 
-- WHERE r.name = 'manager' AND fp.field_name = 'salary';
-- Expected: can_read=TRUE, can_edit=FALSE

-- Test 3: Verify user cannot see salary
-- SELECT * FROM field_permission fp 
-- INNER JOIN role r ON fp.role_id = r.id 
-- WHERE r.name = 'user' AND fp.field_name = 'salary';
-- Expected: 0 rows

-- Test 4: Count total field permissions
-- SELECT COUNT(*) FROM field_permission;
-- Expected: ~20-25 permissions across 5 roles
