-- ============================================================================
-- Authentication Schema Migration (Phase 1)
-- Purpose: User management, RBAC, JWT authentication
-- ============================================================================

-- Create user table
CREATE TABLE IF NOT EXISTS "user" (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    status VARCHAR(50) DEFAULT 'active',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create role table
CREATE TABLE IF NOT EXISTS role (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create permission table
CREATE TABLE IF NOT EXISTS permission (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    resource VARCHAR(100) NOT NULL,
    action VARCHAR(50) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(resource, action)
);

-- Create user_role junction table
CREATE TABLE IF NOT EXISTS user_role (
    user_id VARCHAR(36) NOT NULL,
    role_id VARCHAR(36) NOT NULL,
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES "user"(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE
);

-- Create role_permission junction table
CREATE TABLE IF NOT EXISTS role_permission (
    role_id VARCHAR(36) NOT NULL,
    permission_id VARCHAR(36) NOT NULL,
    granted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id, permission_id),
    FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permission(id) ON DELETE CASCADE
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_user_email ON "user"(email);
CREATE INDEX IF NOT EXISTS idx_user_status ON "user"(status);
CREATE INDEX IF NOT EXISTS idx_user_role_user ON user_role(user_id);
CREATE INDEX IF NOT EXISTS idx_user_role_role ON user_role(role_id);
CREATE INDEX IF NOT EXISTS idx_role_perm_role ON role_permission(role_id);
CREATE INDEX IF NOT EXISTS idx_role_perm_perm ON role_permission(permission_id);
CREATE INDEX IF NOT EXISTS idx_permission_resource ON permission(resource);

-- ============================================================================
-- Seed Default Roles
-- ============================================================================

INSERT INTO role (id, name, description, created_at) VALUES
    (RANDOM_UUID(), 'admin', 'System Administrator - full access to all resources', CURRENT_TIMESTAMP),
    (RANDOM_UUID(), 'manager', 'Manager - can manage team members and view reports', CURRENT_TIMESTAMP),
    (RANDOM_UUID(), 'user', 'Standard User - basic access to own resources', CURRENT_TIMESTAMP);

-- ============================================================================
-- Seed Default Permissions
-- ============================================================================

-- User management permissions
INSERT INTO permission (id, name, resource, action, description, created_at) VALUES
    (RANDOM_UUID(), 'user:create', 'user', 'create', 'Create new users', CURRENT_TIMESTAMP),
    (RANDOM_UUID(), 'user:read', 'user', 'read', 'View user information', CURRENT_TIMESTAMP),
    (RANDOM_UUID(), 'user:update', 'user', 'update', 'Update user information', CURRENT_TIMESTAMP),
    (RANDOM_UUID(), 'user:delete', 'user', 'delete', 'Delete users', CURRENT_TIMESTAMP);

-- Role management permissions
INSERT INTO permission (id, name, resource, action, description, created_at) VALUES
    (RANDOM_UUID(), 'role:create', 'role', 'create', 'Create new roles', CURRENT_TIMESTAMP),
    (RANDOM_UUID(), 'role:read', 'role', 'read', 'View roles', CURRENT_TIMESTAMP),
    (RANDOM_UUID(), 'role:update', 'role', 'update', 'Update roles', CURRENT_TIMESTAMP),
    (RANDOM_UUID(), 'role:delete', 'role', 'delete', 'Delete roles', CURRENT_TIMESTAMP);

-- Permission management permissions
INSERT INTO permission (id, name, resource, action, description, created_at) VALUES
    (RANDOM_UUID(), 'permission:read', 'permission', 'read', 'View permissions', CURRENT_TIMESTAMP),
    (RANDOM_UUID(), 'permission:manage', 'permission', 'manage', 'Manage permissions', CURRENT_TIMESTAMP);

-- App management permissions
INSERT INTO permission (id, name, resource, action, description, created_at) VALUES
    (RANDOM_UUID(), 'app:create', 'app', 'create', 'Create new apps', CURRENT_TIMESTAMP),
    (RANDOM_UUID(), 'app:read', 'app', 'read', 'View apps', CURRENT_TIMESTAMP),
    (RANDOM_UUID(), 'app:update', 'app', 'update', 'Update apps', CURRENT_TIMESTAMP),
    (RANDOM_UUID(), 'app:delete', 'app', 'delete', 'Delete apps', CURRENT_TIMESTAMP);

-- ============================================================================
-- Assign Permissions to Roles
-- ============================================================================

-- Admin gets ALL permissions
INSERT INTO role_permission (role_id, permission_id, granted_at)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM role r
CROSS JOIN permission p
WHERE r.name = 'admin';

-- Manager gets read/update on users and apps
INSERT INTO role_permission (role_id, permission_id, granted_at)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM role r
CROSS JOIN permission p
WHERE r.name = 'manager' 
  AND p.name IN ('user:read', 'user:update', 'app:read', 'app:update', 'role:read');

-- Standard user gets basic permissions
INSERT INTO role_permission (role_id, permission_id, granted_at)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM role r
CROSS JOIN permission p
WHERE r.name = 'user' 
  AND p.name IN ('user:read', 'app:read');

-- ============================================================================
-- Create Default Admin User
-- Password: admin123 (BCrypt hash with cost 10)
-- ============================================================================

INSERT INTO "user" (id, email, password_hash, name, status, created_at) VALUES
    (RANDOM_UUID(), 
     'admin@appbana.com', 
     '$2a$10$N9qo8uLOickgx2ZMRZoMye6J9mKu3fJ4Y5r8S5K5K5K5K5K5K5K5K5', 
     'System Administrator', 
     'active', 
     CURRENT_TIMESTAMP);

-- Assign admin role to admin user
INSERT INTO user_role (user_id, role_id, assigned_at)
SELECT u.id, r.id, CURRENT_TIMESTAMP
FROM "user" u
CROSS JOIN role r
WHERE u.email = 'admin@appbana.com' 
  AND r.name = 'admin';
