CREATE TABLE IF NOT EXISTS appbana_user_roles (
    tenant_id    VARCHAR(255) NOT NULL,
    app_id       VARCHAR(255) NOT NULL,
    entity_name  VARCHAR(255) NOT NULL,
    user_id      VARCHAR(255) NOT NULL,
    role         VARCHAR(20) NOT NULL,
    granted_by   VARCHAR(255) NOT NULL,
    granted_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, app_id, entity_name, user_id)
);
