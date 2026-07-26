-- V14__saved_views.sql
-- Phase B5: List Views (filters + saved views)
-- Per-user saved list views: a stored (filters, sort, groupBy, aggregates)
-- payload the runtime can restore in one click.

CREATE TABLE IF NOT EXISTS appbana_saved_views (
    view_id       VARCHAR(64) PRIMARY KEY,
    tenant_id     VARCHAR(100) NOT NULL,
    app_id        VARCHAR(100) NOT NULL,
    entity_key    VARCHAR(255) NOT NULL,
    owner_user_id VARCHAR(255),
    name          VARCHAR(255) NOT NULL,
    view_json     TEXT NOT NULL,
    is_default    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_saved_views_lookup
    ON appbana_saved_views (tenant_id, app_id, entity_key);

CREATE INDEX IF NOT EXISTS idx_saved_views_owner
    ON appbana_saved_views (tenant_id, app_id, entity_key, owner_user_id);
