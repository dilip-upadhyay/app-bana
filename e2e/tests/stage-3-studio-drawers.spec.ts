/**
 * Stage 3 — Studio drawer + session picker UI tests
 *
 * Verifies the AI-native studio v1.1 UI additions:
 *   - Sessions button is always visible in the header
 *   - Clicking Sessions opens the SessionPicker dropdown with search + empty state
 *   - Data button is HIDDEN for a fresh workspace (no currentApp)
 *   - User menu opens and contains a Log out action
 *
 * Prerequisites: backend 8080, ai-builder 8081, studio 5174 must be running.
 * (Runtime on 5175 is not required for this suite.)
 */
import { expect, request, test } from '@playwright/test';

const BACKEND_URL = process.env.APPBANA_BACKEND_URL ?? 'http://localhost:8080';
const STUDIO_URL  = process.env.APPBANA_STUDIO_URL  ?? 'http://localhost:5174';
const AI_URL      = process.env.APPBANA_AI_BUILDER_URL ?? 'http://localhost:8081';

function uniqueUser() {
  const stamp = Date.now();
  return {
    name:     `Drawer Tester ${stamp}`,
    email:    `drawer.tester+${stamp}@appbana.test`,
    password: `Passw0rd-${stamp}`,
  };
}

test.beforeAll(async () => {
  const api = await request.newContext();
  for (const [name, url] of [
    ['Backend',    `${BACKEND_URL}/health`],
    ['AI Builder', `${AI_URL}/health`],
    ['Studio',     STUDIO_URL],
  ] as const) {
    const res = await api.get(url).catch((e) => {
      throw new Error(`${name} unreachable at ${url}: ${(e as Error).message}`);
    });
    if (!res.ok()) throw new Error(`${name} health check failed: HTTP ${res.status()}`);
  }
  await api.dispose();
});

/**
 * Shared login helper: registers a fresh user via REST, then signs in through
 * the studio UI. Returns `null` if the backend rate-limits registrations, so
 * the caller can `test.skip` gracefully.
 */
async function loginFreshUser(page: import('@playwright/test').Page) {
  const user = uniqueUser();
  const api = await request.newContext();
  const reg = await api.post(`${BACKEND_URL}/api/auth/register`, {
    data: { email: user.email, password: user.password, name: user.name },
  });
  if (reg.status() === 429) {
    await api.dispose();
    return null;
  }
  expect(reg.status(), await reg.text()).toBeLessThan(400);
  await api.dispose();

  await page.goto(STUDIO_URL);
  await expect(page.getByPlaceholder('you@example.com')).toBeVisible({ timeout: 10_000 });
  await page.getByPlaceholder('you@example.com').fill(user.email);
  await page.getByPlaceholder('••••••••').fill(user.password);
  await page.locator('button[type="submit"]').click();
  await expect(page.getByPlaceholder('Describe your app or ask anything…')).toBeVisible({ timeout: 15_000 });
  return user;
}

test('header Sessions button opens the session picker with an empty state for a fresh user', async ({ page }) => {
  const user = await loginFreshUser(page);
  if (!user) { test.skip(true, 'Registration rate-limited — re-run after 60 min'); return; }

  // Sessions button is always in the header (doesn't depend on currentApp)
  const sessionsBtn = page.getByRole('button', { name: /sessions/i });
  await expect(sessionsBtn).toBeVisible();

  await sessionsBtn.click();

  // Picker dropdown mounts: search input + New button + empty state
  await expect(page.getByPlaceholder('Search sessions…')).toBeVisible({ timeout: 5_000 });
  await expect(page.getByRole('button', { name: /\+\s*New/ })).toBeVisible();
  await expect(page.getByText('No sessions yet.')).toBeVisible({ timeout: 5_000 });
});

test('Data button is hidden when the workspace has no current app', async ({ page }) => {
  const user = await loginFreshUser(page);
  if (!user) { test.skip(true, 'Registration rate-limited — re-run after 60 min'); return; }

  // The Data button is rendered only when `currentApp` is set — a fresh user
  // has no apps, so it must be absent. This guards against regressions where
  // the button is rendered before an app is selected (would show a dead
  // drawer with the "Select an app…" placeholder).
  await expect(page.getByRole('button', { name: /^\s*(?:▦\s*)?Data\s*$/ })).toHaveCount(0);
});

test('user menu opens and exposes a Sign out action', async ({ page }) => {
  const user = await loginFreshUser(page);
  if (!user) { test.skip(true, 'Registration rate-limited — re-run after 60 min'); return; }

  // Avatar button is a 7x7 circle with the user initial. It's the only
  // rounded-full button in the header — find it and open the menu.
  const avatar = page.locator('header button.rounded-full').first();
  await expect(avatar).toBeVisible();
  await avatar.click();

  await expect(page.getByRole('button', { name: /sign out/i })).toBeVisible({ timeout: 5_000 });
});
