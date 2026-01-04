-- =====================================================
-- Multi-Environment Pipeline Schema (Phase 2)
-- =====================================================
-- Corrects V7 schema to support Composite ID for Multi-Env
-- =====================================================

-- 1. Ensure all existing records have a valid environment
UPDATE app_deployment SET environment = 'production' WHERE environment IS NULL;

-- 2. Make environment mandatory (Required for Primary Key)
ALTER TABLE app_deployment ALTER COLUMN environment SET NOT NULL;

-- 3. Drop old PK (app_id only) - PostgreSQL syntax
ALTER TABLE app_deployment DROP CONSTRAINT app_deployment_pkey;

-- 4. Add new Composite PK
ALTER TABLE app_deployment ADD PRIMARY KEY (app_id, environment);
