-- ============================================================================
-- V5: Permission Scopes - Multi-Tenancy and Data Boundaries
-- ============================================================================
-- Purpose: Limit permission scope to specific organizations, departments, teams
-- Benefits: Multi-tenant security, data isolation, flexible access control
-- Author: AppBana Auth Team
-- Date: 2025-11-23
-- ============================================================================

-- ============================================================================
-- Table: organization
-- Description: Top-level tenant boundary (for SaaS multi-tenancy)
-- ============================================================================
CREATE TABLE IF NOT EXISTS organization (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(100) NOT NULL UNIQUE,  -- URL-friendly identifier
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_organization_slug ON organization(slug);
CREATE INDEX IF NOT EXISTS idx_organization_active ON organization(is_active);

-- ============================================================================
-- Table: department
-- Description: Department within an organization
-- ============================================================================
CREATE TABLE IF NOT EXISTS department (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    parent_department_id VARCHAR(36),  -- For hierarchical departments
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_department_organization FOREIGN KEY (organization_id) REFERENCES organization(id) ON DELETE CASCADE,
    CONSTRAINT fk_department_parent FOREIGN KEY (parent_department_id) REFERENCES department(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_department_org ON department(organization_id);
CREATE INDEX IF NOT EXISTS idx_department_parent ON department(parent_department_id);
CREATE INDEX IF NOT EXISTS idx_department_active ON department(is_active);

-- ============================================================================
-- Table: team
-- Description: Team within a department (project teams, work groups)
-- ============================================================================
CREATE TABLE IF NOT EXISTS team (
    id VARCHAR(36) PRIMARY KEY,
    department_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_team_department FOREIGN KEY (department_id) REFERENCES department(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_team_department ON team(department_id);
CREATE INDEX IF NOT EXISTS idx_team_active ON team(is_active);

-- ============================================================================
-- Table: permission_scope
-- Description: Limit permission to specific organization/department/team
-- ============================================================================
CREATE TABLE IF NOT EXISTS permission_scope (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    permission_id VARCHAR(36) NOT NULL,
    scope_type VARCHAR(50) NOT NULL,  -- 'global', 'organization', 'department', 'team'
    scope_entity_id VARCHAR(36),       -- ID of organization/department/team (NULL for global)
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    CONSTRAINT fk_permission_scope_user FOREIGN KEY (user_id) REFERENCES "user"(id) ON DELETE CASCADE,
    CONSTRAINT fk_permission_scope_permission FOREIGN KEY (permission_id) REFERENCES permission(id) ON DELETE CASCADE
);

-- Indexes for scope resolution
CREATE INDEX IF NOT EXISTS idx_permission_scope_user ON permission_scope(user_id);
CREATE INDEX IF NOT EXISTS idx_permission_scope_permission ON permission_scope(permission_id);
CREATE INDEX IF NOT EXISTS idx_permission_scope_type ON permission_scope(scope_type);
CREATE INDEX IF NOT EXISTS idx_permission_scope_entity ON permission_scope(scope_entity_id);
CREATE INDEX IF NOT EXISTS idx_permission_scope_active ON permission_scope(is_active);

-- ============================================================================
-- Table: user_organization (Link users to organizations)
-- ============================================================================
CREATE TABLE IF NOT EXISTS user_organization (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    organization_id VARCHAR(36) NOT NULL,
    is_primary BOOLEAN DEFAULT FALSE,  -- User's primary organization
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_org_user FOREIGN KEY (user_id) REFERENCES "user"(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_org_organization FOREIGN KEY (organization_id) REFERENCES organization(id) ON DELETE CASCADE,
    CONSTRAINT uk_user_organization UNIQUE (user_id, organization_id)
);

CREATE INDEX IF NOT EXISTS idx_user_org_user ON user_organization(user_id);
CREATE INDEX IF NOT EXISTS idx_user_org_organization ON user_organization(organization_id);

-- ============================================================================
-- Table: user_department (Link users to departments)
-- ============================================================================
CREATE TABLE IF NOT EXISTS user_department (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    department_id VARCHAR(36) NOT NULL,
    is_primary BOOLEAN DEFAULT FALSE,  -- User's primary department
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_dept_user FOREIGN KEY (user_id) REFERENCES "user"(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_dept_department FOREIGN KEY (department_id) REFERENCES department(id) ON DELETE CASCADE,
    CONSTRAINT uk_user_department UNIQUE (user_id, department_id)
);

CREATE INDEX IF NOT EXISTS idx_user_dept_user ON user_department(user_id);
CREATE INDEX IF NOT EXISTS idx_user_dept_department ON user_department(department_id);

-- ============================================================================
-- Table: user_team (Link users to teams)
-- ============================================================================
CREATE TABLE IF NOT EXISTS user_team (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    team_id VARCHAR(36) NOT NULL,
    role VARCHAR(50),  -- 'member', 'lead', 'manager'
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_team_user FOREIGN KEY (user_id) REFERENCES "user"(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_team_team FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE CASCADE,
    CONSTRAINT uk_user_team UNIQUE (user_id, team_id)
);

CREATE INDEX IF NOT EXISTS idx_user_team_user ON user_team(user_id);
CREATE INDEX IF NOT EXISTS idx_user_team_team ON user_team(team_id);

-- ============================================================================
-- Seed Data: Sample Multi-Tenant Structure
-- ============================================================================

-- Organizations
INSERT INTO organization (id, name, slug, is_active) VALUES
(gen_random_uuid(), 'Acme Corporation', 'acme-corp', TRUE),
(gen_random_uuid(), 'Tech Startup Inc', 'tech-startup', TRUE),
(gen_random_uuid(), 'Global Enterprises', 'global-enterprises', TRUE);

-- Departments for Acme Corporation
INSERT INTO department (id, organization_id, name, is_active)
SELECT 
    gen_random_uuid(),
    org.id,
    dept_name,
    TRUE
FROM organization org
CROSS JOIN (VALUES 
    ('Engineering'),
    ('Sales'),
    ('Marketing'),
    ('Finance'),
    ('Human Resources')
) AS depts(dept_name)
WHERE org.slug = 'acme-corp';

-- Teams for Engineering Department
INSERT INTO team (id, department_id, name, is_active)
SELECT 
    gen_random_uuid(),
    dept.id,
    team_name,
    TRUE
FROM department dept
CROSS JOIN (VALUES 
    ('Platform Team'),
    ('Frontend Team'),
    ('Backend Team'),
    ('DevOps Team')
) AS teams(team_name)
WHERE dept.name = 'Engineering';

-- Teams for Sales Department
INSERT INTO team (id, department_id, name, is_active)
SELECT 
    gen_random_uuid(),
    dept.id,
    team_name,
    TRUE
FROM department dept
CROSS JOIN (VALUES 
    ('Enterprise Sales'),
    ('SMB Sales'),
    ('Sales Operations')
) AS teams(team_name)
WHERE dept.name = 'Sales';

-- ============================================================================
-- Seed Data: Sample Permission Scopes
-- ============================================================================

-- Note: In a real application, permission scopes would be created dynamically
-- when users are assigned to organizations/departments/teams.
-- The examples below demonstrate the schema structure.

-- Example: Admin user gets global scope on all permissions (no restrictions)
-- Uncomment and replace with actual user_id when needed:
-- INSERT INTO permission_scope (id, user_id, permission_id, scope_type, scope_entity_id, created_by)
-- SELECT 
--     gen_random_uuid(),
--     (SELECT id FROM "user" WHERE email = 'admin@appbana.com'),
--     perm.id,
--     'global',
--     NULL,
--     'system'
-- FROM permission perm
-- WHERE perm.name LIKE 'user:%' OR perm.name LIKE 'role:%';

-- Example: Manager user with organization-scoped permissions
-- INSERT INTO permission_scope (id, user_id, permission_id, scope_type, scope_entity_id, created_by)
-- SELECT 
--     gen_random_uuid(),
--     (SELECT id FROM "user" WHERE email = 'manager@appbana.com'),
--     perm.id,
--     'organization',
--     org.id,
--     'system'
-- FROM permission perm
-- CROSS JOIN organization org
-- WHERE perm.name IN ('user:read', 'app:read', 'app:create')
--   AND org.slug = 'acme-corp';

-- Example: Regular user with department-scoped permissions
-- INSERT INTO permission_scope (id, user_id, permission_id, scope_type, scope_entity_id, created_by)
-- SELECT 
--     gen_random_uuid(),
--     (SELECT id FROM "user" WHERE email = 'user@appbana.com'),
--     perm.id,
--     'department',
--     dept.id,
--     'system'
-- FROM permission perm
-- CROSS JOIN department dept
-- WHERE perm.name IN ('app:read', 'project:read')
--   AND dept.name = 'Engineering';

-- ============================================================================
-- Views for Scope Resolution
-- ============================================================================

-- View: v_user_scoped_permissions
-- Description: Get user's permissions with their scopes
CREATE OR REPLACE VIEW v_user_scoped_permissions AS
SELECT 
    ps.user_id,
    ps.permission_id,
    perm.name AS permission_name,
    perm.resource,
    perm.action,
    ps.scope_type,
    ps.scope_entity_id,
    CASE 
        WHEN ps.scope_type = 'organization' THEN org.name
        WHEN ps.scope_type = 'department' THEN dept.name
        WHEN ps.scope_type = 'team' THEN team.name
        ELSE 'Global'
    END AS scope_name
FROM permission_scope ps
INNER JOIN permission perm ON ps.permission_id = perm.id
LEFT JOIN organization org ON ps.scope_entity_id = org.id AND ps.scope_type = 'organization'
LEFT JOIN department dept ON ps.scope_entity_id = dept.id AND ps.scope_type = 'department'
LEFT JOIN team ON ps.scope_entity_id = team.id AND ps.scope_type = 'team'
WHERE ps.is_active = TRUE;

-- View: v_user_accessible_organizations
-- Description: Organizations a user can access based on permission scopes
CREATE OR REPLACE VIEW v_user_accessible_organizations AS
SELECT DISTINCT
    ps.user_id,
    org.id AS organization_id,
    org.name AS organization_name,
    org.slug AS organization_slug
FROM permission_scope ps
INNER JOIN organization org ON ps.scope_entity_id = org.id
WHERE ps.scope_type = 'organization' AND ps.is_active = TRUE

UNION

SELECT DISTINCT
    uo.user_id,
    org.id AS organization_id,
    org.name AS organization_name,
    org.slug AS organization_slug
FROM user_organization uo
INNER JOIN organization org ON uo.organization_id = org.id
WHERE org.is_active = TRUE;

-- ============================================================================
-- Migration Complete
-- ============================================================================
