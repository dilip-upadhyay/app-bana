/**
 * Sprint 2 Task 2.9 — Runtime WCAG 2.1 AA smoke test.
 *
 * Uses @axe-core/playwright to scan the runtime for critical / serious
 * accessibility violations against the `wcag2a` + `wcag2aa` rule sets.
 *
 * Prerequisites: the full stack must be running via
 *   .\scripts\start-everything.bat
 * (Studio 5174, Runtime 5175, Backend 8080, AI Builder 8081). If the
 * runtime is unreachable the suite skips rather than failing.
 *
 * We only fail on `serious` or `critical` violations — moderate issues
 * (mostly color-contrast edge cases and third-party widgets) are surfaced
 * in the console for triage without breaking CI.
 *
 * S3.7 (docs/planning/TENANT_ISOLATION_IMPLEMENTATION_TASKS.md) lands the
 * authenticated-shell scan below, previously a `test.fixme` stub. It seeds a
 * real app via the backend, grants a fresh "User C" an `end-user` role on
 * that app only, and logs in through the real Runtime login form — the
 * tracker's own "literal-browser-tab proof" for the whole S3 initiative.
 * The same test then reuses that real (not hand-minted) session to assert
 * the rest of S3.7(c): update/delete/schema-management 403, and a second
 * app the end-user was never granted also 403s.
 */
import { expect, request, test, type APIRequestContext } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

const RUNTIME_URL = process.env.APPBANA_RUNTIME_URL ?? 'http://localhost:5175';
const BACKEND_URL = process.env.APPBANA_BACKEND_URL ?? 'http://localhost:8080';
const TENANT_ID   = process.env.APPBANA_A11Y_TENANT ?? 'default';

test.describe('Runtime WCAG 2.1 AA', () => {
  test.beforeAll(async () => {
    const api = await request.newContext();
    try {
      const res = await api.get(`${RUNTIME_URL}/`).catch(() => null);
      if (!res || !res.ok()) test.skip(true, `Runtime unreachable at ${RUNTIME_URL}`);
    } finally {
      await api.dispose();
    }
  });

  test('login page has no serious axe violations', async ({ page }) => {
    // The runtime renders LoginPage before any authenticated app content, so
    // hitting any /run/:tenant/:app path with no token shows the login form.
    await page.goto(`${RUNTIME_URL}/run/${TENANT_ID}/a11y-probe`);

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .analyze();

    const blocking = results.violations.filter(
      (v) => v.impact === 'serious' || v.impact === 'critical',
    );

    if (blocking.length > 0) {
      // eslint-disable-next-line no-console
      console.error('axe violations:', JSON.stringify(blocking, null, 2));
    }
    expect(blocking, 'no serious/critical WCAG 2 AA violations on login').toEqual([]);
  });

  test('authenticated shell — no serious axe violations, and end-user isolation holds', async ({ page }) => {
    if (!(await backendHealthCheck())) {
      test.skip(true, `Backend unreachable at ${BACKEND_URL} — start the stack first`);
      return;
    }
    const fx = await setupS37Fixture();
    if (!fx) { test.skip(true, 'Registration rate-limited — re-run after 60 min'); return; }

    try {
      // ── Log in as the end-user through the real Runtime login form ──
      await page.goto(`${RUNTIME_URL}/run/${fx.ownerTenantId}/${fx.appAId}`);
      await expect(page.getByPlaceholder('you@example.com')).toBeVisible({ timeout: 10_000 });
      await page.getByPlaceholder('you@example.com').fill(fx.endUser.email);
      await page.getByPlaceholder('••••••••').fill(fx.endUser.password);
      await page.locator('button[type="submit"]').click();

      // ── S3.7(c) "list/get succeed": the seeded row renders in a real ──
      // ── browser tab for a session that only holds an end-user grant. ──
      await expect(page.getByText(fx.seededWidgetName)).toBeVisible({ timeout: 15_000 });

      // ── S3.7(b): a11y scan of the authenticated shell ──
      const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze();
      const blocking = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
      if (blocking.length > 0) {
        // eslint-disable-next-line no-console
        console.error('axe violations:', JSON.stringify(blocking, null, 2));
      }
      expect(blocking, 'no serious/critical WCAG 2 AA violations on authenticated shell').toEqual([]);

      // ── Extract the REAL token the browser is actually using — proves ──
      // ── the checks below exercise the same session Runtime itself holds ──
      // ── rather than a guard-unit-test-minted one. ──
      const endUserToken = await page.evaluate(() => localStorage.getItem('appbana_token'));
      expect(endUserToken, 'runtime should have stored a token after login').toBeTruthy();

      // ── S3.7(c) "update/delete/schema-management 403" ──
      const api = await request.newContext();
      try {
        const authScheme = 'Bearer';
        const headers = { Authorization: `${authScheme} ${endUserToken}`, 'Content-Type': 'application/json' };

        const updateRes = await api.put(`${BACKEND_URL}/appbana-studio/${fx.ownerTenantId}/apps/${fx.appAId}`, {
          headers, data: { name: 'hijacked' },
        });
        expect(updateRes.status(), await updateRes.text()).toBe(403);

        const schemaGetRes = await api.get(`${BACKEND_URL}/schema/${fx.entityKey}`, { headers });
        expect(schemaGetRes.status(), await schemaGetRes.text()).toBe(403);

        const schemaDeleteRes = await api.delete(`${BACKEND_URL}/schema/${fx.entityKey}`, { headers });
        expect(schemaDeleteRes.status(), await schemaDeleteRes.text()).toBe(403);

        // DELETE app last so a hypothetical guard failure here doesn't
        // remove the fixture out from under the assertions above it.
        const deleteRes = await api.delete(`${BACKEND_URL}/appbana-studio/${fx.ownerTenantId}/apps/${fx.appAId}`, { headers });
        expect(deleteRes.status(), await deleteRes.text()).toBe(403);
      } finally {
        await api.dispose();
      }

      // ── S3.7(c) "navigate to a second app with no grant (must 403)" ──
      await page.goto(`${RUNTIME_URL}/run/${fx.ownerTenantId}/${fx.appBId}`);
      await expect(page.getByText('Error loading app')).toBeVisible({ timeout: 15_000 });
    } finally {
      await teardownS37Fixture(fx);
    }
  });
});

// ── S3.7 fixture helpers ────────────────────────────────────────────────
//
// Registers a fresh app owner + a fresh end-user via the real
// /api/auth/register endpoint, has the owner create two apps (App-A grants
// the end-user membership, App-B does not), seed App-A with one entity, one
// row, and one list page, matching the multi-tenant URL/table-key
// conventions documented in .github/copilot-instructions.md §5/§8.

interface S37User {
  email: string;
  password: string;
  userId: string;
  tenantId: string;
  token: string;
}

interface S37Fixture {
  api: APIRequestContext;
  owner: S37User;
  endUser: S37User;
  ownerTenantId: string;
  appAId: string;
  appBId: string;
  entityKey: string;
  seededWidgetName: string;
}

async function backendHealthCheck(): Promise<boolean> {
  const api = await request.newContext();
  try {
    const res = await api.get(`${BACKEND_URL}/health`).catch(() => null);
    return !!res && res.ok();
  } finally {
    await api.dispose();
  }
}

async function registerS37User(api: APIRequestContext, prefix: string): Promise<S37User | null> {
  const stamp = Date.now();
  const suffix = Math.random().toString(36).slice(2, 8);
  const email = `${prefix}+${stamp}-${suffix}@appbana.test`;
  const password = `Passw0rd-${stamp}`;
  const reg = await api.post(`${BACKEND_URL}/api/auth/register`, {
    data: { email, password, name: `${prefix} ${stamp}` },
  });
  if (reg.status() === 429) return null;
  if (!reg.ok()) throw new Error(`register(${prefix}) failed HTTP ${reg.status()}: ${await reg.text()}`);
  const body = await reg.json();
  const tenantId = body.user?.tenantId as string | undefined;
  const userId = String(body.user?.id ?? '');
  if (!tenantId || !userId) {
    throw new Error(`register(${prefix}) response missing user.tenantId/id: ${JSON.stringify(body)}`);
  }
  return { email, password, userId, tenantId, token: (body.token ?? body.sessionId) as string };
}

async function setupS37Fixture(): Promise<S37Fixture | null> {
  const api = await request.newContext();
  const owner = await registerS37User(api, 's37owner');
  if (!owner) { await api.dispose(); return null; }
  const endUser = await registerS37User(api, 's37enduser');
  if (!endUser) { await api.dispose(); return null; }

  const stamp = Date.now();
  const appAId = `s37a-${stamp}`;
  const appBId = `s37b-${stamp}`;
  const entityName = 'S37RuntimeWidget';
  const entityKey = `${owner.tenantId}_${appAId}_${entityName}`;
  const seededWidgetName = `S3.7 Probe Widget ${stamp}`;
  const authScheme = 'Bearer';
  const ownerHeaders = { Authorization: `${authScheme} ${owner.token}` };

  const createAppA = await api.post(`${BACKEND_URL}/appbana-studio/${owner.tenantId}/apps`, {
    headers: ownerHeaders, data: { id: appAId, name: 'S3.7 App A' },
  });
  if (!createAppA.ok()) throw new Error(`createApp(A) failed HTTP ${createAppA.status()}: ${await createAppA.text()}`);

  const createAppB = await api.post(`${BACKEND_URL}/appbana-studio/${owner.tenantId}/apps`, {
    headers: ownerHeaders, data: { id: appBId, name: 'S3.7 App B (no grant)' },
  });
  if (!createAppB.ok()) throw new Error(`createApp(B) failed HTTP ${createAppB.status()}: ${await createAppB.text()}`);

  const saveSchema = await api.post(`${BACKEND_URL}/schema`, {
    headers: { ...ownerHeaders, 'Content-Type': 'application/json' },
    data: {
      name: entityName,
      tenantId: owner.tenantId,
      appId: appAId,
      fields: [
        { name: 'id', type: 'integer', primaryKey: true, autoIncrement: true },
        { name: 'widget_name', type: 'text', required: true },
      ],
    },
  });
  if (!saveSchema.ok()) throw new Error(`saveSchema failed HTTP ${saveSchema.status()}: ${await saveSchema.text()}`);

  const insertRow = await api.post(`${BACKEND_URL}/api/${entityKey}`, {
    headers: { ...ownerHeaders, 'Content-Type': 'application/json' },
    data: { widget_name: seededWidgetName },
  });
  if (!insertRow.ok()) throw new Error(`insertRow failed HTTP ${insertRow.status()}: ${await insertRow.text()}`);

  const pageId = 's37-widget-list';
  const savePage = await api.put(`${BACKEND_URL}/appbana-studio/${owner.tenantId}/apps/${appAId}/pages/${pageId}`, {
    headers: { ...ownerHeaders, 'Content-Type': 'application/json' },
    data: {
      id: pageId,
      name: 'S3.7 Widget List',
      path: '/s37-widgets',
      rootId: 'root',
      nodes: [{
        id: 'root',
        type: 'table',
        props: {
          entity: entityName,
          fields: [
            { name: 'id', label: 'ID', type: 'integer' },
            { name: 'widget_name', label: 'Widget Name', type: 'text' },
          ],
        },
      }],
    },
  });
  if (!savePage.ok()) throw new Error(`savePage failed HTTP ${savePage.status()}: ${await savePage.text()}`);

  const grant = await api.post(`${BACKEND_URL}/api/tenants/${owner.tenantId}/apps/${appAId}/members`, {
    headers: { ...ownerHeaders, 'Content-Type': 'application/json' },
    data: { userId: endUser.userId, role: 'end-user' },
  });
  if (!grant.ok()) throw new Error(`grant membership failed HTTP ${grant.status()}: ${await grant.text()}`);

  return { api, owner, endUser, ownerTenantId: owner.tenantId, appAId, appBId, entityKey, seededWidgetName };
}

async function teardownS37Fixture(fx: S37Fixture | null): Promise<void> {
  if (!fx) return;
  const authScheme = 'Bearer';
  const ownerHeaders = { Authorization: `${authScheme} ${fx.owner.token}` };
  try {
    await fx.api.delete(`${BACKEND_URL}/schema/${fx.entityKey}?dropTable=true`, { headers: ownerHeaders });
    await fx.api.delete(`${BACKEND_URL}/appbana-studio/${fx.ownerTenantId}/apps/${fx.appAId}`, { headers: ownerHeaders });
    await fx.api.delete(`${BACKEND_URL}/appbana-studio/${fx.ownerTenantId}/apps/${fx.appBId}`, { headers: ownerHeaders });
  } catch {
    /* best-effort cleanup — a failed cleanup must not fail the test itself */
  } finally {
    await fx.api.dispose();
  }
}
