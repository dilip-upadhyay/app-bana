-- AppBana Authentication & RBAC Schema
-- Version: 1.0
-- Date: 2025-11-22

-- =====================================================
-- 1. USER TABLE
-- =====================================================
CREATE TABLE IF NOT EXISTS app_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_email (email),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
);

-- =====================================================
-- 2. ROLE TABLE
-- =====================================================
CREATE TABLE IF NOT EXISTS role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL,
    description TEXT,
    is_system BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_name (name)
);

-- Insert default system roles
INSERT INTO role (name, description, is_system) VALUES
('admin', 'Full system administrator with all permissions', true),
('manager', 'Manager with permissions to manage resources', true),
('user', 'Standard user with basic read/write access', true)
ON DUPLICATE KEY UPDATE description=VALUES(description);

-- =====================================================
-- 3. USER_ROLE TABLE (Many-to-Many Junction)
-- =====================================================
CREATE TABLE IF NOT EXISTS user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    assigned_by BIGINT NULL,
    
    FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE,
    FOREIGN KEY (assigned_by) REFERENCES app_user(id) ON DELETE SET NULL,
    
    UNIQUE KEY unique_user_role (user_id, role_id),
    INDEX idx_user (user_id),
    INDEX idx_role (role_id)
);

-- =====================================================
-- 4. PERMISSION TABLE
-- =====================================================
CREATE TABLE IF NOT EXISTS permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    resource VARCHAR(100) NOT NULL,
    action VARCHAR(50) NOT NULL,
    scope VARCHAR(50) NOT NULL DEFAULT 'all',
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE KEY unique_permission (resource, action, scope),
    INDEX idx_resource (resource),
    INDEX idx_action (action)
);

-- Insert default permissions
INSERT INTO permission (resource, action, scope, description) VALUES
-- System permissions
('*', '*', 'all', 'Full access to all resources'),
('system', 'admin', 'all', 'System administration'),

-- Generic CRUD permissions
('*', 'create', 'all', 'Create any resource'),
('*', 'read', 'all', 'Read any resource'),
('*', 'update', 'all', 'Update any resource'),
('*', 'delete', 'all', 'Delete any resource'),

-- Own records only
('*', 'read', 'own', 'Read own records only'),
('*', 'update', 'own', 'Update own records only'),
('*', 'delete', 'own', 'Delete own records only')
ON DUPLICATE KEY UPDATE description=VALUES(description);

-- =====================================================
-- 5. ROLE_PERMISSION TABLE (Many-to-Many Junction)
-- =====================================================
CREATE TABLE IF NOT EXISTS role_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    
    FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permission(id) ON DELETE CASCADE,
    
    UNIQUE KEY unique_role_permission (role_id, permission_id),
    INDEX idx_role (role_id),
    INDEX idx_permission (permission_id)
);

-- Assign permissions to default roles
-- Admin: Full access
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r, permission p
WHERE r.name = 'admin' AND p.resource = '*' AND p.action = '*' AND p.scope = 'all'
ON DUPLICATE KEY UPDATE role_id=role_id;

-- Manager: Create, read, update all; delete own
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r, permission p
WHERE r.name = 'manager' AND p.resource = '*' AND p.action IN ('create', 'read', 'update') AND p.scope = 'all'
ON DUPLICATE KEY UPDATE role_id=role_id;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r, permission p
WHERE r.name = 'manager' AND p.resource = '*' AND p.action = 'delete' AND p.scope = 'own'
ON DUPLICATE KEY UPDATE role_id=role_id;

-- User: Read all; create, update, delete own
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r, permission p
WHERE r.name = 'user' AND p.resource = '*' AND p.action = 'read' AND p.scope = 'all'
ON DUPLICATE KEY UPDATE role_id=role_id;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r, permission p
WHERE r.name = 'user' AND p.resource = '*' AND p.action IN ('create', 'update', 'delete') AND p.scope = 'own'
ON DUPLICATE KEY UPDATE role_id=role_id;

-- =====================================================
-- 6. SESSION TABLE (Optional - for tracking active sessions)
-- =====================================================
CREATE TABLE IF NOT EXISTS user_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    last_activity TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
    INDEX idx_user (user_id),
    INDEX idx_token (token_hash),
    INDEX idx_expires (expires_at)
);

-- =====================================================
-- 7. AUDIT LOG TABLE (Track all security events)
-- =====================================================
CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL,
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100),
    resource_id VARCHAR(100),
    details TEXT,
    ip_address VARCHAR(45),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE SET NULL,
    INDEX idx_user (user_id),
    INDEX idx_action (action),
    INDEX idx_resource (resource_type, resource_id),
    INDEX idx_created_at (created_at)
);

-- =====================================================
-- UTILITY VIEWS
-- =====================================================

-- View: User with their roles
CREATE OR REPLACE VIEW v_user_roles AS
SELECT 
    u.id as user_id,
    u.email,
    u.name,
    u.status,
    r.id as role_id,
    r.name as role_name,
    r.description as role_description,
    ur.assigned_at
FROM app_user u
LEFT JOIN user_role ur ON u.id = ur.user_id
LEFT JOIN role r ON ur.role_id = r.id;

-- View: Role with their permissions
CREATE OR REPLACE VIEW v_role_permissions AS
SELECT 
    r.id as role_id,
    r.name as role_name,
    p.id as permission_id,
    p.resource,
    p.action,
    p.scope,
    p.description as permission_description
FROM role r
LEFT JOIN role_permission rp ON r.id = rp.role_id
LEFT JOIN permission p ON rp.permission_id = p.id;

-- View: User with all their effective permissions
CREATE OR REPLACE VIEW v_user_permissions AS
SELECT DISTINCT
    u.id as user_id,
    u.email,
    u.name,
    p.resource,
    p.action,
    p.scope
FROM app_user u
JOIN user_role ur ON u.id = ur.user_id
JOIN role_permission rp ON ur.role_id = rp.role_id
JOIN permission p ON rp.permission_id = p.id
WHERE u.status = 'active';

-- =====================================================
-- SAMPLE DATA (for testing)
-- =====================================================

-- Create a test admin user (password: Admin@123)
-- BCrypt hash generated with cost factor 12
INSERT INTO app_user (email, password_hash, name, status)
VALUES (
    'admin@appbana.local',
    '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyVPpLMcGMqS',
    'System Administrator',
    'active'
) ON DUPLICATE KEY UPDATE name=VALUES(name);

-- Assign admin role
INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id
FROM app_user u, role r
WHERE u.email = 'admin@appbana.local' AND r.name = 'admin'
ON DUPLICATE KEY UPDATE user_id=user_id;

-- Create a test manager user (password: Manager@123)
INSERT INTO app_user (email, password_hash, name, status)
VALUES (
    'manager@appbana.local',
    '$2a$12$K9YQdO.aqEJc1HSwOdU3qeKvIgfJO5wZk4xR2vN1IjF5a8eJxPqWC',
    'Test Manager',
    'active'
) ON DUPLICATE KEY UPDATE name=VALUES(name);

-- Assign manager role
INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id
FROM app_user u, role r
WHERE u.email = 'manager@appbana.local' AND r.name = 'manager'
ON DUPLICATE KEY UPDATE user_id=user_id;

-- Create a test regular user (password: User@123)
INSERT INTO app_user (email, password_hash, name, status)
VALUES (
    'user@appbana.local',
    '$2a$12$vZ8HdXqwCEyJ5LiPjFgN0OzQF3B5xJc7YeA2dGhKpL9mN4rWqT1vS',
    'Test User',
    'active'
) ON DUPLICATE KEY UPDATE name=VALUES(name);

-- Assign user role
INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id
FROM app_user u, role r
WHERE u.email = 'user@appbana.local' AND r.name = 'user'
ON DUPLICATE KEY UPDATE user_id=user_id;

-- =====================================================
-- STORED PROCEDURES (Useful utilities)
-- =====================================================

DELIMITER //

-- Check if user has specific permission
CREATE PROCEDURE sp_check_permission(
    IN p_user_id BIGINT,
    IN p_resource VARCHAR(100),
    IN p_action VARCHAR(50),
    IN p_record_owner_id BIGINT,
    OUT p_has_permission BOOLEAN
)
BEGIN
    DECLARE perm_count INT;
    
    -- Check for exact match or wildcard permissions
    SELECT COUNT(*) INTO perm_count
    FROM v_user_permissions
    WHERE user_id = p_user_id
      AND (resource = p_resource OR resource = '*')
      AND (action = p_action OR action = '*')
      AND (
          scope = 'all' OR 
          (scope = 'own' AND p_record_owner_id = p_user_id)
      );
    
    SET p_has_permission = (perm_count > 0);
END //

-- Get all permissions for a user
CREATE PROCEDURE sp_get_user_permissions(
    IN p_user_id BIGINT
)
BEGIN
    SELECT resource, action, scope
    FROM v_user_permissions
    WHERE user_id = p_user_id
    ORDER BY resource, action;
END //

-- Audit log helper
CREATE PROCEDURE sp_audit_log(
    IN p_user_id BIGINT,
    IN p_action VARCHAR(100),
    IN p_resource_type VARCHAR(100),
    IN p_resource_id VARCHAR(100),
    IN p_details TEXT,
    IN p_ip_address VARCHAR(45)
)
BEGIN
    INSERT INTO audit_log (user_id, action, resource_type, resource_id, details, ip_address)
    VALUES (p_user_id, p_action, p_resource_type, p_resource_id, p_details, p_ip_address);
END //

DELIMITER ;

-- =====================================================
-- VERIFICATION QUERIES
-- =====================================================

-- Verify roles
SELECT * FROM role;

-- Verify permissions
SELECT * FROM permission;

-- Verify role-permission mappings
SELECT r.name as role, p.resource, p.action, p.scope
FROM role r
JOIN role_permission rp ON r.id = rp.role_id
JOIN permission p ON rp.permission_id = p.id
ORDER BY r.name, p.resource, p.action;

-- Verify test users
SELECT u.email, u.name, r.name as role
FROM app_user u
LEFT JOIN user_role ur ON u.id = ur.user_id
LEFT JOIN role r ON ur.role_id = r.id;

-- Test permission check (example)
-- CALL sp_check_permission(1, 'Project', 'delete', 1, @result);
-- SELECT @result as has_permission;
