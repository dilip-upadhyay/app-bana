-- ============================================================================
-- V3: Profile Layer - Collections of Permissions
-- ============================================================================
-- Purpose: Enable reusable permission sets that can be assigned to roles
-- Benefits: Easier maintenance, reusability, compliance, scalability
-- Author: AppBana Auth Team
-- Date: 2025-11-23
-- ============================================================================

-- ============================================================================
-- Table: profile
-- Description: Named collection of permissions (e.g., "Sales Profile", "Manager Profile")
-- ============================================================================
CREATE TABLE IF NOT EXISTS profile (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    updated_by VARCHAR(36)
);

-- Index for active profile lookups
CREATE INDEX IF NOT EXISTS idx_profile_active ON profile(is_active);
CREATE INDEX IF NOT EXISTS idx_profile_name ON profile(name);

-- ============================================================================
-- Table: profile_permission
-- Description: Many-to-many relationship between profiles and permissions
-- ============================================================================
CREATE TABLE IF NOT EXISTS profile_permission (
    id VARCHAR(36) PRIMARY KEY,
    profile_id VARCHAR(36) NOT NULL,
    permission_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    CONSTRAINT fk_profile_permission_profile FOREIGN KEY (profile_id) REFERENCES profile(id) ON DELETE CASCADE,
    CONSTRAINT fk_profile_permission_permission FOREIGN KEY (permission_id) REFERENCES permission(id) ON DELETE CASCADE,
    CONSTRAINT uk_profile_permission UNIQUE (profile_id, permission_id)
);

-- Indexes for permission resolution
CREATE INDEX IF NOT EXISTS idx_profile_permission_profile ON profile_permission(profile_id);
CREATE INDEX IF NOT EXISTS idx_profile_permission_permission ON profile_permission(permission_id);

-- ============================================================================
-- Table: role_profile
-- Description: Many-to-many relationship between roles and profiles
-- ============================================================================
CREATE TABLE IF NOT EXISTS role_profile (
    id VARCHAR(36) PRIMARY KEY,
    role_id VARCHAR(36) NOT NULL,
    profile_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    CONSTRAINT fk_role_profile_role FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE,
    CONSTRAINT fk_role_profile_profile FOREIGN KEY (profile_id) REFERENCES profile(id) ON DELETE CASCADE,
    CONSTRAINT uk_role_profile UNIQUE (role_id, profile_id)
);

-- Indexes for role permission resolution
CREATE INDEX IF NOT EXISTS idx_role_profile_role ON role_profile(role_id);
CREATE INDEX IF NOT EXISTS idx_role_profile_profile ON role_profile(profile_id);

-- ============================================================================
-- Seed Data: Common Profiles
-- ============================================================================

-- Profile 1: System Administrator Profile (Full Access)
INSERT INTO profile (id, name, description, is_active, created_by) VALUES 
(RANDOM_UUID(), 'System Administrator', 'Full system access - all permissions', TRUE, 'system');

-- Profile 2: Manager Profile (Management Operations)
INSERT INTO profile (id, name, description, is_active, created_by) VALUES 
(RANDOM_UUID(), 'Manager', 'Team and user management capabilities', TRUE, 'system');

-- Profile 3: User Management Profile
INSERT INTO profile (id, name, description, is_active, created_by) VALUES 
(RANDOM_UUID(), 'User Management', 'Create, read, update users', TRUE, 'system');

-- Profile 4: Role Management Profile
INSERT INTO profile (id, name, description, is_active, created_by) VALUES 
(RANDOM_UUID(), 'Role Management', 'Create, read, update roles', TRUE, 'system');

-- Profile 5: Basic User Profile (Read-only)
INSERT INTO profile (id, name, description, is_active, created_by) VALUES 
(RANDOM_UUID(), 'Basic User', 'Basic read-only access', TRUE, 'system');

-- Profile 6: Application Builder Profile
INSERT INTO profile (id, name, description, is_active, created_by) VALUES 
(RANDOM_UUID(), 'Application Builder', 'Create and manage applications', TRUE, 'system');

-- ============================================================================
-- Assign Permissions to Profiles
-- ============================================================================

-- System Administrator: All permissions
INSERT INTO profile_permission (id, profile_id, permission_id, created_by)
SELECT RANDOM_UUID(), p.id, perm.id, 'system'
FROM profile p
CROSS JOIN permission perm
WHERE p.name = 'System Administrator';

-- Manager Profile: User and role management
INSERT INTO profile_permission (id, profile_id, permission_id, created_by)
SELECT RANDOM_UUID(), p.id, perm.id, 'system'
FROM profile p
CROSS JOIN permission perm
WHERE p.name = 'Manager'
  AND perm.name IN ('user:read', 'user:create', 'user:update', 'role:read', 'app:read');

-- User Management Profile
INSERT INTO profile_permission (id, profile_id, permission_id, created_by)
SELECT RANDOM_UUID(), p.id, perm.id, 'system'
FROM profile p
CROSS JOIN permission perm
WHERE p.name = 'User Management'
  AND perm.name IN ('user:read', 'user:create', 'user:update');

-- Role Management Profile
INSERT INTO profile_permission (id, profile_id, permission_id, created_by)
SELECT RANDOM_UUID(), p.id, perm.id, 'system'
FROM profile p
CROSS JOIN permission perm
WHERE p.name = 'Role Management'
  AND perm.name IN ('role:read', 'role:create', 'role:update');

-- Basic User Profile
INSERT INTO profile_permission (id, profile_id, permission_id, created_by)
SELECT RANDOM_UUID(), p.id, perm.id, 'system'
FROM profile p
CROSS JOIN permission perm
WHERE p.name = 'Basic User'
  AND perm.name IN ('app:read');

-- Application Builder Profile
INSERT INTO profile_permission (id, profile_id, permission_id, created_by)
SELECT RANDOM_UUID(), p.id, perm.id, 'system'
FROM profile p
CROSS JOIN permission perm
WHERE p.name = 'Application Builder'
  AND perm.name IN ('app:read', 'app:create', 'app:update', 'app:delete');

-- ============================================================================
-- Assign Profiles to Roles
-- ============================================================================

-- Admin role: System Administrator Profile
INSERT INTO role_profile (id, role_id, profile_id, created_by)
SELECT RANDOM_UUID(), r.id, p.id, 'system'
FROM role r
CROSS JOIN profile p
WHERE r.name = 'admin' AND p.name = 'System Administrator';

-- Manager role: Manager + User Management + Application Builder Profiles
INSERT INTO role_profile (id, role_id, profile_id, created_by)
SELECT RANDOM_UUID(), r.id, p.id, 'system'
FROM role r
CROSS JOIN profile p
WHERE r.name = 'manager' AND p.name IN ('Manager', 'User Management', 'Application Builder');

-- User role: Basic User Profile
INSERT INTO role_profile (id, role_id, profile_id, created_by)
SELECT RANDOM_UUID(), r.id, p.id, 'system'
FROM role r
CROSS JOIN profile p
WHERE r.name = 'user' AND p.name = 'Basic User';

-- ============================================================================
-- View: v_user_profiles
-- Description: Get all profiles for a user (through their roles)
-- ============================================================================
CREATE VIEW IF NOT EXISTS v_user_profiles AS
SELECT DISTINCT
    ur.user_id,
    p.id AS profile_id,
    p.name AS profile_name,
    p.description AS profile_description
FROM user_role ur
INNER JOIN role_profile rp ON ur.role_id = rp.role_id
INNER JOIN profile p ON rp.profile_id = p.id
WHERE p.is_active = TRUE;

-- ============================================================================
-- View: v_user_permissions_from_profiles
-- Description: Get all permissions for a user from their profiles
-- ============================================================================
CREATE VIEW IF NOT EXISTS v_user_permissions_from_profiles AS
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
WHERE p.is_active = TRUE;

-- ============================================================================
-- View: v_effective_user_permissions
-- Description: Combine permissions from both direct role assignment AND profiles
-- ============================================================================
CREATE VIEW IF NOT EXISTS v_effective_user_permissions AS
-- Permissions from direct role assignments
SELECT 
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
SELECT 
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
WHERE p.is_active = TRUE;

-- ============================================================================
-- Migration Complete
-- ============================================================================
