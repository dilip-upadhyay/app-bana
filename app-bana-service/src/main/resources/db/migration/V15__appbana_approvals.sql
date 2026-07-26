CREATE TABLE IF NOT EXISTS appbana_approvals (
    id                UUID PRIMARY KEY,
    tenant_id         VARCHAR(255) NOT NULL,
    app_id            VARCHAR(255) NOT NULL,
    entity_name       VARCHAR(255) NOT NULL,
    row_id            VARCHAR(255) NOT NULL,
    revision          INTEGER NOT NULL,
    from_state        VARCHAR(20),
    to_state          VARCHAR(20) NOT NULL,
    actor_user_id     VARCHAR(255) NOT NULL,
    actor_role        VARCHAR(50) NOT NULL,
    reason            TEXT,
    diff              JSONB,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_appr_row ON appbana_approvals(tenant_id, app_id, entity_name, row_id);
