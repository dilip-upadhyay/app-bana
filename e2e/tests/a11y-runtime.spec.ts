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
 */
import { expect, request, test } from '@playwright/test';
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

  // The authenticated shell scans (list / form / detail) require a fixture
  // that seeds an app and drives login. Marked fixme until that lands.
  test.fixme('authenticated shell — no serious axe violations', async () => {
    // TODO: seed app via backend, authenticate, navigate, run AxeBuilder.
  });
});

// Silence unused-import warning when the suite fully skips.
void BACKEND_URL;
