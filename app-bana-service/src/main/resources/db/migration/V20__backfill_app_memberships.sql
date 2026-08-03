-- V20__backfill_app_memberships.sql
-- S2.4: Backfill owner membership for every app that has no owner row in appbana_app_members.
-- Covers apps predating S2.3 and any app where the S2.3 grant() call failed (orphaned row).
-- Scope: ALL apps with no owner row, not just ones created before S2.3 deployment.
-- 'system-backfill' in granted_by identifies rows written here vs real grants, for future audit.
-- Where author is null/blank, 'system' is used as user_id (no real creator can be determined).

INSERT INTO appbana_app_members (tenant_id, app_id, user_id, role, granted_by, granted_at)
SELECT
    a.tenant_id,
    a.id                                                  AS app_id,
    COALESCE(NULLIF(TRIM(a.author), ''), 'system')        AS user_id,
    'owner',
    'system-backfill',
    NOW()
FROM appbana_apps a
WHERE NOT EXISTS (
    SELECT 1
    FROM appbana_app_members m
    WHERE m.tenant_id = a.tenant_id
      AND m.app_id    = a.id
      AND m.role      = 'owner'
)
ON CONFLICT (tenant_id, app_id, user_id) DO UPDATE
    SET role       = EXCLUDED.role,
        granted_by = EXCLUDED.granted_by,
        granted_at = EXCLUDED.granted_at;
