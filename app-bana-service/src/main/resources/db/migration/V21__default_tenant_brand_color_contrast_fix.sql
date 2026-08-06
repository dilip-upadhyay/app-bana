-- V21__default_tenant_brand_color_contrast_fix.sql
-- S3.7: V12 seeded the 'default' tenant's primary_color as '#6366f1'
-- (Tailwind indigo-500). White text on that background measures 4.46:1
-- contrast — just under WCAG 2 AA's 4.5:1 minimum — and was failing
-- e2e/tests/a11y-runtime.spec.ts's axe scan on the login page's Sign In
-- button. Nudge to '#6163f0' (4.61:1), matching the same darkened shade
-- now used as globals.css's --color-brand fallback and the Java-side
-- unknown-tenant defaults in TenantBrandingRoutes / AppContextRoutes.
--
-- Guarded so it only touches the still-unmodified V12 seed value, never a
-- deliberate customization made by a tenant admin after V12 ran.

UPDATE appbana_tenants
SET primary_color = '#6163f0',
    updated_at = NOW()
WHERE tenant_id = 'default'
  AND primary_color = '#6366f1';
