-- V10__tenant_app_isolation.sql
-- Migration to enforce tenant and app isolation for all entity tables
-- This migration adds tenant_id and app_id columns to enable multi-tenant data isolation

-- ============================================================================
-- PART 1: Update appbana_schemas table to store tenant/app context
-- ============================================================================

-- Add tenant_id and app_id to schema metadata table
-- This allows schemas to be scoped to specific apps
ALTER TABLE appbana_schemas 
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50) DEFAULT 'default';

ALTER TABLE appbana_schemas 
    ADD COLUMN IF NOT EXISTS app_id VARCHAR(50) DEFAULT 'legacy';

-- Make columns NOT NULL after backfill
ALTER TABLE appbana_schemas 
    ALTER COLUMN tenant_id SET NOT NULL;

ALTER TABLE appbana_schemas 
    ALTER COLUMN app_id SET NOT NULL;

-- Update primary key to include tenant_id and app_id
-- Note: We keep 'name' in the key for backward compatibility with existing code
-- The 'name' column already has unique schema keys like "tenant_app_entityName"
CREATE INDEX IF NOT EXISTS idx_schema_tenant_app ON appbana_schemas(tenant_id, app_id, name);

-- ============================================================================
-- PART 2: Instructions for Entity Tables
-- ============================================================================

-- NOTE: This migration does NOT alter existing entity tables directly because:
-- 1. Entity tables are created dynamically by SchemaManager based on EntitySchema
-- 2. The names and structure of entity tables are not known at migration time
-- 3. Adding columns to unknown tables could cause errors
--
-- Instead, this migration provides the TEMPLATE for what SchemaManager should do
-- when creating NEW entity tables going forward.
--
-- For EXISTING entity tables, the approach is:
-- 1. SchemaManager will detect missing tenant_id/app_id columns
-- 2. SchemaManager will automatically add them with ALTER TABLE
-- 3. SchemaManager will backfill with default values
-- 4. This happens lazily on first access to each entity table
--
-- TEMPLATE for entity tables (applied by SchemaManager):
--
-- For H2 dialect:
-- ALTER TABLE {entity_table} ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50) DEFAULT 'default' NOT NULL;
-- ALTER TABLE {entity_table} ADD COLUMN IF NOT EXISTS app_id VARCHAR(50) DEFAULT 'legacy' NOT NULL;
-- CREATE INDEX IF NOT EXISTS idx_{entity}_tenant_app ON {entity_table}(tenant_id, app_id);
--
-- For PostgreSQL dialect:
-- ALTER TABLE {entity_table} ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50) DEFAULT 'default' NOT NULL;
-- ALTER TABLE {entity_table} ADD COLUMN IF NOT EXISTS app_id VARCHAR(50) DEFAULT 'legacy' NOT NULL;
-- CREATE INDEX IF NOT EXISTS idx_{entity}_tenant_app ON {entity_table}(tenant_id, app_id);

-- ============================================================================
-- PART 3: Update System Tables (Workflow, etc.)
-- ============================================================================

-- Update workflow definition table
ALTER TABLE appbana_wf_definition 
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50) DEFAULT 'default';

ALTER TABLE appbana_wf_definition 
    ADD COLUMN IF NOT EXISTS app_id VARCHAR(50) DEFAULT 'legacy';

ALTER TABLE appbana_wf_definition 
    ALTER COLUMN tenant_id SET NOT NULL;

ALTER TABLE appbana_wf_definition 
    ALTER COLUMN app_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_wf_def_tenant_app ON appbana_wf_definition(tenant_id, app_id);

-- Update workflow instance table
ALTER TABLE appbana_wf_instance 
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50) DEFAULT 'default';

ALTER TABLE appbana_wf_instance 
    ADD COLUMN IF NOT EXISTS app_id VARCHAR(50) DEFAULT 'legacy';

ALTER TABLE appbana_wf_instance 
    ALTER COLUMN tenant_id SET NOT NULL;

ALTER TABLE appbana_wf_instance 
    ALTER COLUMN app_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_wf_inst_tenant_app ON appbana_wf_instance(tenant_id, app_id);

-- Update workflow token table
ALTER TABLE appbana_wf_token 
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50) DEFAULT 'default';

ALTER TABLE appbana_wf_token 
    ADD COLUMN IF NOT EXISTS app_id VARCHAR(50) DEFAULT 'legacy';

ALTER TABLE appbana_wf_token 
    ALTER COLUMN tenant_id SET NOT NULL;

ALTER TABLE appbana_wf_token 
    ALTER COLUMN app_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_wf_token_tenant_app ON appbana_wf_token(tenant_id, app_id);

-- Note: appbana_wf_history already has tenant_id via workflow_id FK relationship

-- ============================================================================
-- PART 4: Verification Queries (for manual testing)
-- ============================================================================

-- To verify migration success, run these queries:
--
-- 1. Check appbana_schemas has tenant_id and app_id:
-- SELECT name, tenant_id, app_id FROM appbana_schemas LIMIT 5;
--
-- 2. Check workflow tables have tenant_id and app_id:
-- SELECT id, tenant_id, app_id FROM appbana_wf_definition LIMIT 5;
-- SELECT instance_id, tenant_id, app_id FROM appbana_wf_instance LIMIT 5;
-- SELECT token_id, tenant_id, app_id FROM appbana_wf_token LIMIT 5;
--
-- 3. Check indexes were created:
-- SHOW INDEXES FROM appbana_schemas WHERE Key_name = 'idx_schema_tenant_app';

-- ============================================================================
-- ROLLBACK Instructions (if needed)
-- ============================================================================

-- To rollback this migration, execute:
--
-- DROP INDEX IF EXISTS idx_schema_tenant_app;
-- ALTER TABLE appbana_schemas DROP COLUMN IF EXISTS tenant_id;
-- ALTER TABLE appbana_schemas DROP COLUMN IF EXISTS app_id;
--
-- DROP INDEX IF EXISTS idx_wf_def_tenant_app;
-- ALTER TABLE appbana_wf_definition DROP COLUMN IF EXISTS tenant_id;
-- ALTER TABLE appbana_wf_definition DROP COLUMN IF EXISTS app_id;
--
-- DROP INDEX IF EXISTS idx_wf_inst_tenant_app;
-- ALTER TABLE appbana_wf_instance DROP COLUMN IF EXISTS tenant_id;
-- ALTER TABLE appbana_wf_instance DROP COLUMN IF EXISTS app_id;
--
-- DROP INDEX IF EXISTS idx_wf_token_tenant_app;
-- ALTER TABLE appbana_wf_token DROP COLUMN IF EXISTS tenant_id;
-- ALTER TABLE appbana_wf_token DROP COLUMN IF EXISTS app_id;

-- ============================================================================
-- Migration Complete
-- ============================================================================
