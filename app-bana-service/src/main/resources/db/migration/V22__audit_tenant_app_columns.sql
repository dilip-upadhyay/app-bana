-- V22__audit_tenant_app_columns.sql
-- Task S4.6 (Tenant Isolation Security Plan) — add tenant_id/app_id to appbana_audit.
--
-- Before this migration, appbana_audit recorded only a bare entity short-name (e.g. "Customer"),
-- never which tenant/app it belonged to. A cross-tenant incident was reproducible live (S3's own
-- capstone tests do this) but not provable after the fact from the audit trail alone, since the
-- same entity short-name is shared by every tenant/app that has ever created an entity with that
-- name. AuditLogService.log() now always supplies both values on every write going forward
-- (see AuditLogService.java, GenericEntityRoutes.java call sites).
--
-- Unlike V10's appbana_schemas backfill (which set pre-migration rows to 'default' because that
-- data really did all belong to the single implicit tenant/app that existed before multi-tenancy),
-- existing appbana_audit rows are NOT backfilled here: an audit row predating this column could
-- already belong to ANY tenant/app that existed at the time, and writing a guessed 'default' value
-- would misrepresent forensic history rather than honestly recording "unknown, column didn't exist
-- yet". For the same reason, no NOT NULL constraint is added.

ALTER TABLE appbana_audit
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(200);

ALTER TABLE appbana_audit
    ADD COLUMN IF NOT EXISTS app_id VARCHAR(200);

CREATE INDEX IF NOT EXISTS idx_audit_tenant_app ON appbana_audit(tenant_id, app_id);
