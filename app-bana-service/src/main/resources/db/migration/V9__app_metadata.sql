-- V9__app_metadata.sql
-- Migration to move App and Page metadata from filesystem to database

-- Apps Table
CREATE TABLE IF NOT EXISTS appbana_apps (
    id VARCHAR(100) NOT NULL,
    tenant_id VARCHAR(50) DEFAULT 'default',
    name VARCHAR(255),
    description CLOB,
    version VARCHAR(50),
    author VARCHAR(100),
    created_at BIGINT,
    updated_at BIGINT,
    json_metadata CLOB, -- Stores full JSON serialization of AppMetadata
    PRIMARY KEY (id, tenant_id)
);

-- Pages Table
CREATE TABLE IF NOT EXISTS appbana_pages (
    id VARCHAR(100) NOT NULL,
    app_id VARCHAR(100) NOT NULL,
    tenant_id VARCHAR(50) DEFAULT 'default',
    name VARCHAR(255),
    type VARCHAR(50),
    json_metadata CLOB, -- Stores full JSON serialization of Page
    updated_at BIGINT,
    PRIMARY KEY (id, app_id, tenant_id)
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_app_tenant ON appbana_apps(tenant_id);
CREATE INDEX IF NOT EXISTS idx_page_app ON appbana_pages(app_id, tenant_id);

-- Workflows Table
CREATE TABLE IF NOT EXISTS appbana_app_workflows (
    app_id VARCHAR(100) NOT NULL,
    tenant_id VARCHAR(50) DEFAULT 'default',
    json_metadata CLOB, -- Stores full JSON serialization of workflows list
    updated_at BIGINT,
    PRIMARY KEY (app_id, tenant_id)
);
