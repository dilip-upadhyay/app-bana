-- V11: App Versioning and Deployment Tracking (PostgreSQL)
-- Purpose: Track all app deployments with full snapshots and audit trail
-- Author: AppBana Team
-- Date: 2026-01-04

-- Create app_versions table
CREATE TABLE IF NOT EXISTS app_versions (
    id BIGSERIAL PRIMARY KEY,
    app_id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(100) NOT NULL,
    version INTEGER NOT NULL,
    environment VARCHAR(20) NOT NULL CHECK (environment IN ('DEV', 'SIT', 'PROD')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('SUCCESS', 'FAILED', 'ROLLED_BACK')),
    
    -- Full app snapshot as JSONB (entire AppMeta structure)
    app_snapshot JSONB NOT NULL,
    
    -- Deployment metadata
    tables_created TEXT[],  -- PostgreSQL native array
    deployed_by VARCHAR(255),
    deployed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    duration_ms BIGINT,
    
    -- Error tracking
    error_message TEXT,
    error_stack_trace TEXT,
    
    -- Audit trail
    notes TEXT,
    
    CONSTRAINT unique_version_per_app_env UNIQUE (app_id, tenant_id, environment, version)
);

-- Indexes for fast lookups
CREATE INDEX idx_app_versions_app_tenant ON app_versions(app_id, tenant_id);
CREATE INDEX idx_app_versions_environment ON app_versions(environment);
CREATE INDEX idx_app_versions_deployed_at ON app_versions(deployed_at DESC);
CREATE INDEX idx_app_versions_status ON app_versions(status);

-- GIN index for JSONB queries
CREATE INDEX idx_app_versions_snapshot ON app_versions USING GIN (app_snapshot);

-- Index for finding latest version
CREATE INDEX idx_app_versions_latest ON app_versions(app_id, tenant_id, environment, version DESC);

