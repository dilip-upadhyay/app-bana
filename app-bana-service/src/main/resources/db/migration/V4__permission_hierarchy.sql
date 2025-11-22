-- ============================================================================
-- V4: Permission Hierarchy - Wildcard and Parent-Child Relationships
-- ============================================================================
-- Purpose: Enable permission inheritance (project:* includes project:read, project:create, etc.)
-- Benefits: Simplified permission management, flexible wildcard grants
-- Author: AppBana Auth Team
-- Date: 2025-11-23
-- ============================================================================

-- ============================================================================
-- Table: permission_hierarchy
-- Description: Defines parent-child relationships between permissions
-- ============================================================================
CREATE TABLE IF NOT EXISTS permission_hierarchy (
    id VARCHAR(36) PRIMARY KEY,
    parent_permission_id VARCHAR(36) NOT NULL,
    child_permission_id VARCHAR(36) NOT NULL,
    depth INT DEFAULT 1,  -- How many levels deep (1 = direct child, 2+ = grandchild)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_permission_hierarchy_parent FOREIGN KEY (parent_permission_id) REFERENCES permission(id) ON DELETE CASCADE,
    CONSTRAINT fk_permission_hierarchy_child FOREIGN KEY (child_permission_id) REFERENCES permission(id) ON DELETE CASCADE,
    CONSTRAINT uk_permission_hierarchy UNIQUE (parent_permission_id, child_permission_id)
);

-- Indexes for permission resolution
CREATE INDEX IF NOT EXISTS idx_permission_hierarchy_parent ON permission_hierarchy(parent_permission_id);
CREATE INDEX IF NOT EXISTS idx_permission_hierarchy_child ON permission_hierarchy(child_permission_id);
CREATE INDEX IF NOT EXISTS idx_permission_hierarchy_depth ON permission_hierarchy(depth);

-- ============================================================================
-- Step 1: Add Wildcard Permissions (Missing from V1)
-- ============================================================================

-- Add wildcard permissions for resources that already have specific actions
INSERT INTO permission (id, name, resource, action, description) VALUES
(RANDOM_UUID(), 'user:*', 'user', '*', 'All user operations'),
(RANDOM_UUID(), 'role:*', 'role', '*', 'All role operations'),
(RANDOM_UUID(), 'permission:*', 'permission', '*', 'All permission operations'),
(RANDOM_UUID(), 'app:*', 'app', '*', 'All app operations');

-- ============================================================================
-- Step 2: Seed Data - Permission Hierarchy for Existing Permissions
-- ============================================================================

-- User Permissions Hierarchy
-- user:* → user:read, user:create, user:update, user:delete
INSERT INTO permission_hierarchy (id, parent_permission_id, child_permission_id, depth)
SELECT 
    RANDOM_UUID(),
    (SELECT id FROM permission WHERE name = 'user:*'),
    child.id,
    1
FROM permission child
WHERE child.name IN ('user:read', 'user:create', 'user:update', 'user:delete');

-- Role Permissions Hierarchy
-- role:* → role:read, role:create, role:update, role:delete
INSERT INTO permission_hierarchy (id, parent_permission_id, child_permission_id, depth)
SELECT 
    RANDOM_UUID(),
    (SELECT id FROM permission WHERE name = 'role:*'),
    child.id,
    1
FROM permission child
WHERE child.name IN ('role:read', 'role:create', 'role:update', 'role:delete');

-- Permission Permissions Hierarchy
-- permission:* → permission:read, permission:create, permission:update, permission:delete
INSERT INTO permission_hierarchy (id, parent_permission_id, child_permission_id, depth)
SELECT 
    RANDOM_UUID(),
    (SELECT id FROM permission WHERE name = 'permission:*'),
    child.id,
    1
FROM permission child
WHERE child.name IN ('permission:read', 'permission:create', 'permission:update', 'permission:delete');

-- App Permissions Hierarchy
-- app:* → app:read, app:create, app:update, app:delete
INSERT INTO permission_hierarchy (id, parent_permission_id, child_permission_id, depth)
SELECT 
    RANDOM_UUID(),
    (SELECT id FROM permission WHERE name = 'app:*'),
    child.id,
    1
FROM permission child
WHERE child.name IN ('app:read', 'app:create', 'app:update', 'app:delete');

-- ============================================================================
-- Step 3: Add New Resource Permissions (Project, Task, Report)
-- ============================================================================

-- Project wildcard permissions
INSERT INTO permission (id, name, description, resource, action) VALUES
(RANDOM_UUID(), 'project:*', 'All project operations', 'project', '*'),
(RANDOM_UUID(), 'project:read', 'View projects', 'project', 'read'),
(RANDOM_UUID(), 'project:create', 'Create projects', 'project', 'create'),
(RANDOM_UUID(), 'project:update', 'Update projects', 'project', 'update'),
(RANDOM_UUID(), 'project:delete', 'Delete projects', 'project', 'delete');

-- Task wildcard permissions
INSERT INTO permission (id, name, description, resource, action) VALUES
(RANDOM_UUID(), 'task:*', 'All task operations', 'task', '*'),
(RANDOM_UUID(), 'task:read', 'View tasks', 'task', 'read'),
(RANDOM_UUID(), 'task:create', 'Create tasks', 'task', 'create'),
(RANDOM_UUID(), 'task:update', 'Update tasks', 'task', 'update'),
(RANDOM_UUID(), 'task:delete', 'Delete tasks', 'task', 'delete');

-- Report wildcard permissions
INSERT INTO permission (id, name, description, resource, action) VALUES
(RANDOM_UUID(), 'report:*', 'All report operations', 'report', '*'),
(RANDOM_UUID(), 'report:read', 'View reports', 'report', 'read'),
(RANDOM_UUID(), 'report:create', 'Create reports', 'report', 'create'),
(RANDOM_UUID(), 'report:export', 'Export reports', 'report', 'export');

-- ============================================================================
-- Step 4: Build Hierarchy for New Permissions
-- ============================================================================

-- Project hierarchy: project:* → project:read, create, update, delete
INSERT INTO permission_hierarchy (id, parent_permission_id, child_permission_id, depth)
SELECT 
    RANDOM_UUID(),
    (SELECT id FROM permission WHERE name = 'project:*'),
    child.id,
    1
FROM permission child
WHERE child.name IN ('project:read', 'project:create', 'project:update', 'project:delete');

-- Task hierarchy: task:* → task:read, create, update, delete
INSERT INTO permission_hierarchy (id, parent_permission_id, child_permission_id, depth)
SELECT 
    RANDOM_UUID(),
    (SELECT id FROM permission WHERE name = 'task:*'),
    child.id,
    1
FROM permission child
WHERE child.name IN ('task:read', 'task:create', 'task:update', 'task:delete');

-- Report hierarchy: report:* → report:read, create, export
INSERT INTO permission_hierarchy (id, parent_permission_id, child_permission_id, depth)
SELECT 
    RANDOM_UUID(),
    (SELECT id FROM permission WHERE name = 'report:*'),
    child.id,
    1
FROM permission child
WHERE child.name IN ('report:read', 'report:create', 'report:export');

-- ============================================================================
-- Step 5: Advanced Hierarchy - Multi-level (Write includes Create + Update)
-- ============================================================================

-- project:write → project:create, project:update
INSERT INTO permission (id, name, description, resource, action) VALUES
(RANDOM_UUID(), 'project:write', 'Create and update projects', 'project', 'write');

INSERT INTO permission_hierarchy (id, parent_permission_id, child_permission_id, depth)
SELECT 
    RANDOM_UUID(),
    (SELECT id FROM permission WHERE name = 'project:write'),
    child.id,
    1
FROM permission child
WHERE child.name IN ('project:create', 'project:update');

-- project:* now includes project:write (which includes create + update)
INSERT INTO permission_hierarchy (id, parent_permission_id, child_permission_id, depth)
VALUES (
    RANDOM_UUID(),
    (SELECT id FROM permission WHERE name = 'project:*'),
    (SELECT id FROM permission WHERE name = 'project:write'),
    1
);

-- ============================================================================
-- View: v_permission_tree
-- Description: Flattened view of all permissions with their inherited children
-- ============================================================================
CREATE VIEW IF NOT EXISTS v_permission_tree AS
SELECT 
    parent.id AS parent_id,
    parent.name AS parent_name,
    child.id AS child_id,
    child.name AS child_name,
    ph.depth
FROM permission_hierarchy ph
INNER JOIN permission parent ON ph.parent_permission_id = parent.id
INNER JOIN permission child ON ph.child_permission_id = child.id
ORDER BY parent.name, ph.depth, child.name;

-- ============================================================================
-- View: v_effective_user_permissions_with_hierarchy
-- Description: User permissions including inherited permissions from hierarchy
-- ============================================================================
CREATE VIEW IF NOT EXISTS v_effective_user_permissions_with_hierarchy AS
-- Direct permissions from roles
SELECT DISTINCT
    ur.user_id,
    perm.id AS permission_id,
    perm.name AS permission_name,
    perm.resource,
    perm.action,
    'direct' AS source
FROM user_role ur
INNER JOIN role_permission rp ON ur.role_id = rp.role_id
INNER JOIN permission perm ON rp.permission_id = perm.id

UNION

-- Permissions from profiles
SELECT DISTINCT
    ur.user_id,
    perm.id AS permission_id,
    perm.name AS permission_name,
    perm.resource,
    perm.action,
    'profile' AS source
FROM user_role ur
INNER JOIN role_profile rp ON ur.role_id = rp.role_id
INNER JOIN profile_permission pp ON rp.profile_id = pp.profile_id
INNER JOIN permission perm ON pp.permission_id = perm.id
INNER JOIN profile p ON rp.profile_id = p.id
WHERE p.is_active = TRUE

UNION

-- Inherited permissions via hierarchy (from direct role assignments)
SELECT DISTINCT
    ur.user_id,
    child.id AS permission_id,
    child.name AS permission_name,
    child.resource,
    child.action,
    'inherited' AS source
FROM user_role ur
INNER JOIN role_permission rp ON ur.role_id = rp.role_id
INNER JOIN permission_hierarchy ph ON rp.permission_id = ph.parent_permission_id
INNER JOIN permission child ON ph.child_permission_id = child.id

UNION

-- Inherited permissions via hierarchy (from profile assignments)
SELECT DISTINCT
    ur.user_id,
    child.id AS permission_id,
    child.name AS permission_name,
    child.resource,
    child.action,
    'inherited_profile' AS source
FROM user_role ur
INNER JOIN role_profile rp ON ur.role_id = rp.role_id
INNER JOIN profile_permission pp ON rp.profile_id = pp.profile_id
INNER JOIN permission_hierarchy ph ON pp.permission_id = ph.parent_permission_id
INNER JOIN permission child ON ph.child_permission_id = child.id
INNER JOIN profile p ON rp.profile_id = p.id
WHERE p.is_active = TRUE;

-- ============================================================================
-- Migration Complete
-- ============================================================================
