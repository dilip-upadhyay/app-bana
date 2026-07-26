-- V13__file_storage.sql
-- Phase B3: File Upload
-- Registry of uploaded files. The physical bytes live on disk (or later, blob
-- storage) via a FileStorageAdapter; this table is the addressable index so
-- entity rows can reference a file by fileId (VARCHAR) and the runtime can
-- resolve download URLs + display metadata.

CREATE TABLE IF NOT EXISTS appbana_files (
    file_id       VARCHAR(64) PRIMARY KEY,
    tenant_id     VARCHAR(100) NOT NULL,
    app_id        VARCHAR(100) NOT NULL,
    entity_key    VARCHAR(255),
    field_name    VARCHAR(255),
    original_name VARCHAR(512) NOT NULL,
    mime_type     VARCHAR(255) NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    storage_path  VARCHAR(1024) NOT NULL,
    uploaded_by   VARCHAR(255),
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_files_tenant_app
    ON appbana_files (tenant_id, app_id);

CREATE INDEX IF NOT EXISTS idx_files_entity
    ON appbana_files (tenant_id, app_id, entity_key);
