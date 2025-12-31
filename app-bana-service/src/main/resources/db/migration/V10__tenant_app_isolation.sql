-- V10__tenant_app_isolation.sql
-- Migration to enforce tenant and app isolation for all entity tables
-- This migration adds tenant_id and app_id columns to enable multi-tenant data isolation

-- ============================================================================
-- PART 1: Update appbana_schemas table to store tenant/app context
-- ============================================================================

-- Add tenant_id and app_id to schema metadata table (required, no defaults)
-- This allows schemas to be scoped to specific apps
ALTER TABLE appbana_schemas 
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50) NOT NULL;

ALTER TABLE appbana_schemas 
    ADD COLUMN IF NOT EXISTS app_id VARCHAR(50) NOT NULL;

-- Create composite index for efficient tenant/app filtering
CREATE INDEX IF NOT EXISTS idx_schema_tenant_app ON appbana_schemas(tenant_id, app_id, name);

-- ============================================================================
-- PART 2: Instructions for Entity Tables
-- ============================================================================

-- NOTE: This migration does NOT alter existing entity tables directly because:
-- 1. Entity tables are created dynamically by SchemaManager based on EntitySchema
-- 2. The names and structure of entity tables are not known at migration time
-- 3. SchemaManager automatically adds tenant_id/app_id to all new entity tables
--
-- TEMPLATE for entity tables (applied by SchemaManager on table creation):
--
-- CREATE TABLE {entity_table} (
--   tenant_id VARCHAR(50) NOT NULL,
--   app_id VARCHAR(50) NOT NULL,
--   {user_defined_columns}...
-- );
-- CREATE INDEX idx_{entity}_tenant_app ON {entity_table}(tenant_id, app_id);

-- ============================================================================
-- PART 3: Update System Tables (Workflow, etc.)
-- ============================================================================

-- Strategy: Add columns as nullable, populate existing rows, then make NOT NULL
-- This handles existing data from V6 seed migrations

-- Update workflow definition table
ALTER TABLE appbana_wf_definition 
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50);

ALTER TABLE appbana_wf_definition 
    ADD COLUMN IF NOT EXISTS app_id VARCHAR(50);

-- Populate existing rows with default values (for seed data from V6)
UPDATE appbana_wf_definition SET tenant_id = 'default' WHERE tenant_id IS NULL;
UPDATE appbana_wf_definition SET app_id = 'default' WHERE app_id IS NULL;

-- Now make columns NOT NULL
ALTER TABLE appbana_wf_definition ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE appbana_wf_definition ALTER COLUMN app_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_wf_def_tenant_app ON appbana_wf_definition(tenant_id, app_id);

-- Update workflow instance table
ALTER TABLE appbana_wf_instance 
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50);

ALTER TABLE appbana_wf_instance 
    ADD COLUMN IF NOT EXISTS app_id VARCHAR(50);

-- Populate existing rows
UPDATE appbana_wf_instance SET tenant_id = 'default' WHERE tenant_id IS NULL;
UPDATE appbana_wf_instance SET app_id = 'default' WHERE app_id IS NULL;

-- Make NOT NULL
ALTER TABLE appbana_wf_instance ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE appbana_wf_instance ALTER COLUMN app_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_wf_inst_tenant_app ON appbana_wf_instance(tenant_id, app_id);

-- Update workflow token table
ALTER TABLE appbana_wf_token 
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50);

ALTER TABLE appbana_wf_token 
    ADD COLUMN IF NOT EXISTS app_id VARCHAR(50);

-- Populate existing rows
UPDATE appbana_wf_token SET tenant_id = 'default' WHERE tenant_id IS NULL;
UPDATE appbana_wf_token SET app_id = 'default' WHERE app_id IS NULL;

-- Make NOT NULL
ALTER TABLE appbana_wf_token ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE appbana_wf_token ALTER COLUMN app_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_wf_token_tenant_app ON appbana_wf_token(tenant_id, app_id);

-- Note: appbana_wf_history already has tenant_id via workflow_id FK relationship
-- SELECT instance_id, tenant_id, app_id FROM appbana_wf_instance LIMIT 5;
-- SELECT token_id, tenant_id, app_id FROM appbana_wf_token LIMIT 5;
--
-- 3. Check indexes were created:
-- SHOW INDEXES FROM appbana_schemas WHERE Key_name = 'idx_schema_tenant_app';

-- ============================================================================
-- ============================================================================
-- PART 4: Verification Queries (for manual testing)
-- ============================================================================

-- To verify migration success, run these queries:
--
-- 1. Check appbana_schemas has tenant_id and app_id (required columns):
-- SELECT name, tenant_id, app_id FROM appbana_schemas LIMIT 5;
--
-- 2. Check workflow tables have tenant_id and app_id (required columns):
-- SELECT id, tenant_id, app_id FROM appbana_wf_definition LIMIT 5;
-- SELECT instance_id, tenant_id, app_id FROM appbana_wf_instance LIMIT 5;
-- SELECT token_id, tenant_id, app_id FROM appbana_wf_token LIMIT 5;
--
-- 3. Check indexes were created:
-- SHOW INDEXES FROM appbana_schemas WHERE Key_name = 'idx_schema_tenant_app';
--
-- 4. Verify NOT NULL constraint (should fail without tenant_id/app_id):
-- INSERT INTO appbana_schemas (name, json) VALUES ('test', '{}'); -- Should fail!
--
-- DROP INDEX IF EXISTS idx_wf_token_tenant_app;
-- ALTER TABLE appbana_wf_token DROP COLUMN IF EXISTS tenant_id;
-- ALTER TABLE appbana_wf_token DROP COLUMN IF EXISTS app_id;

-- ============================================================================
-- Migration Complete
-- ============================================================================
