-- V12__tenant_branding.sql
-- Stage 0: AI-Native UI Rebuild
-- Creates a minimal tenants table to store per-tenant display branding.
-- The branding data is served publicly (no auth required) so the runtime
-- can render tenant logo + primary colour on its login screen before the
-- user authenticates.

CREATE TABLE IF NOT EXISTS appbana_tenants (
    tenant_id     VARCHAR(100) PRIMARY KEY,
    display_name  VARCHAR(255),
    logo_url      VARCHAR(1024),
    primary_color VARCHAR(20),
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seed the implicit 'default' tenant so calls for it always return a row
INSERT INTO appbana_tenants (tenant_id, display_name, primary_color)
VALUES ('default', 'AppBana', '#6366f1')
ON CONFLICT (tenant_id) DO NOTHING;

-- Index for fast lookup (PRIMARY KEY already creates one, but explicit for clarity)
CREATE INDEX IF NOT EXISTS idx_tenants_tenant_id ON appbana_tenants (tenant_id);
