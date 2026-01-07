-- =====================================================
-- Release Management Schema (Phase 1)
-- =====================================================
-- Purpose: Application Versioning, Snapshots, and Deployments
-- Architecture: Immutable History + Deployment Pointers
-- =====================================================

-- =====================================================
-- Table 1: App Versions (Immutable Snapshots)
-- =====================================================
-- Stores the complete state of an application at a point in time.
-- All JSON fields contain the full serialized state.
CREATE TABLE IF NOT EXISTS app_version (
    id VARCHAR(36) PRIMARY KEY,
    app_id VARCHAR(36) NOT NULL,
    version_number INTEGER NOT NULL,
    label VARCHAR(100),               -- User-friendly tag (e.g., "v1.0-MVP", "Emergency Fix")
    description TEXT,                 -- Release notes
    
    -- Structure Snapshots (JSON Blobs)
    -- We store these as TEXT (CLOB) to ensure database compatibility.
    metadata_json TEXT NOT NULL,      -- Copy of AppMetadata (name, theme, navigation, etc.)
    pages_json TEXT NOT NULL,         -- List of all Page JSONs
    entities_json TEXT NOT NULL,      -- List of all EntitySchema JSONs
    workflows_json TEXT NOT NULL,     -- List of all WorkflowDefinition JSONs
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    
    -- Ensure version numbers are sequential per app
    CONSTRAINT uq_app_version UNIQUE (app_id, version_number)
);

-- Index for listing versions of an app
CREATE INDEX IF NOT EXISTS idx_app_version_app ON app_version(app_id, version_number DESC);

-- =====================================================
-- Table 2: App Deployments (Environment State)
-- =====================================================
-- Tracks which version is currently "Live" for a given environment.
-- For Phase 1, we assume a single "Production" environment implied by the record existence.
-- To rollback, we simply update the 'live_version_id'.
CREATE TABLE IF NOT EXISTS app_deployment (
    app_id VARCHAR(36) PRIMARY KEY,   -- One active deployment per app
    live_version_id VARCHAR(36) NOT NULL,
    environment VARCHAR(50) DEFAULT 'production', -- Prepared for future multi-env support
    
    deployed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deployed_by VARCHAR(255),
    
    CONSTRAINT fk_deployment_version FOREIGN KEY (live_version_id) 
        REFERENCES app_version(id) ON DELETE CASCADE
);

-- Note: We do not add a foreign key to 'app_metadata' or 'app_id' because apps are currently 
-- file-based (or not rigorously enforcing referential integrity in the DB for the app ID itself).
-- The service layer will ensure app_id consistency.
