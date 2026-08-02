-- V19__appbana_app_members.sql
-- S2.1: per-app membership model. A user's grant on one app is independent of
-- session.tenantId, letting a member manage an app outside their own tenant
-- once S2.6 wires AppMembershipService.isMember into TenantAccessGuard.

CREATE TABLE IF NOT EXISTS appbana_app_members (
    tenant_id    VARCHAR(255) NOT NULL,
    app_id       VARCHAR(255) NOT NULL,
    user_id      VARCHAR(255) NOT NULL,
    role         VARCHAR(20) NOT NULL CHECK (role IN ('owner', 'member', 'end-user')),
    granted_by   VARCHAR(255) NOT NULL,
    granted_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, app_id, user_id)
);

-- Leads with user_id (not tenant_id) because S2.2's listAppsForUser(userId) is the
-- one cross-tenant lookup this table serves - it has no tenant_id to filter by first.
CREATE INDEX IF NOT EXISTS idx_app_members_user ON appbana_app_members(user_id, tenant_id);
