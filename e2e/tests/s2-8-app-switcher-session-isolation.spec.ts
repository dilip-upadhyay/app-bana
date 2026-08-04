/**
 * S2.8 — Studio app switcher + chat history session isolation
 *
 * Verifies:
 *   1. The app switcher renders exactly the current user's own apps — the
 *      server-filtered `GET /appbana-studio/{tenantId}/apps` response — with
 *      no client-side "all tenant apps" assumption.
 *   2. A stale app list/selection from a previous session is never rendered
 *      as valid after a session boundary. Regression guard for
 *      `useWorkspaceStore.resetWorkspace()`: apps/currentApp/branding must be
 *      cleared on the `appbana:auth:expired` recovery path in AuthGate.tsx
 *      (which, unlike the explicit Sign out flow, does NOT hard-reload the
 *      page) so a different user re-authenticating in the same tab never
 *      briefly sees the previous user's app list/selection rendered as if
 *      the server had already confirmed it for them.
 *   3. A previous session's chat conversation (message content/attachments)
 *      never survives that same auth-expiry boundary either. Regression
 *      guard for the S2.8 review follow-up: `useChatStore`'s `messages[]`
 *      is an in-memory singleton just like `useWorkspaceStore`'s state, and
 *      was found still leaking full conversation content across the exact
 *      same no-reload recovery path after the app-switcher leak above was
 *      fixed. `resetSessionScopedState()` (stores/sessionBoundary.ts) now
 *      clears both stores from one call site.
 *
 * Prerequisites: backend 8080 and studio 5174 must be running. AI Builder is
 * NOT required — this spec never depends on receiving an AI response; the
 * user's own message renders synchronously (`addUserMessage`) before any
 * network call is made, which is all test 3 below needs.
 */
import { expect, request, test } from '@playwright/test';

const BACKEND_URL = process.env.APPBANA_BACKEND_URL ?? 'http://localhost:8080';
const STUDIO_URL  = process.env.APPBANA_STUDIO_URL  ?? 'http://localhost:5174';

interface TestUser {
  email: string;
  password: string;
  appName: string;
}

test.beforeAll(async () => {
  const api = await request.newContext();
  for (const [name, url] of [
    ['Backend', `${BACKEND_URL}/health`],
    ['Studio',  STUDIO_URL],
  ] as const) {
    const res = await api.get(url).catch((e) => {
      throw new Error(`${name} unreachable at ${url}: ${(e as Error).message}`);
    });
    if (!res.ok()) throw new Error(`${name} health check failed: HTTP ${res.status()}`);
  }
  await api.dispose();
});

/** Registers a fresh user via REST and creates one app owned by their own tenant. */
async function registerUserWithApp(prefix: string): Promise<TestUser | null> {
  const stamp = Date.now();
  const suffix = Math.random().toString(36).slice(2, 8);
  const email = `${prefix}+${stamp}-${suffix}@appbana.test`;
  const password = `Passw0rd-${stamp}`;
  const name = `${prefix} ${stamp}`;

  const api = await request.newContext();
  const reg = await api.post(`${BACKEND_URL}/api/auth/register`, { data: { email, password, name } });
  if (reg.status() === 429) { await api.dispose(); return null; }
  expect(reg.ok(), await reg.text()).toBeTruthy();

  const login = await api.post(`${BACKEND_URL}/api/auth/login`, { data: { email, password } });
  expect(login.ok(), await login.text()).toBeTruthy();
  const loginBody = await login.json();
  const token = loginBody.token as string;
  const tenantId = loginBody.user?.tenantId as string;
  expect(tenantId, 'login response missing user.tenantId').toBeTruthy();

  const appName = `${prefix}-App-${stamp}`;
  const appId = `${prefix}-${stamp}-${suffix}`.toLowerCase();
  const createApp = await api.post(`${BACKEND_URL}/appbana-studio/${tenantId}/apps`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { id: appId, name: appName },
  });
  expect(createApp.ok(), await createApp.text()).toBeTruthy();
  await api.dispose();

  return { email, password, appName };
}

test('app switcher renders only the logged-in user\u2019s own apps', async ({ page }) => {
  const userA = await registerUserWithApp('s28a');
  if (!userA) { test.skip(true, 'Registration rate-limited — re-run after 60 min'); return; }

  await page.goto(STUDIO_URL);
  await expect(page.getByPlaceholder('you@example.com')).toBeVisible({ timeout: 10_000 });
  await page.getByPlaceholder('you@example.com').fill(userA.email);
  await page.getByPlaceholder('••••••••').fill(userA.password);
  await page.locator('button[type="submit"]').click();
  await expect(page.getByPlaceholder('Describe your app or ask anything…')).toBeVisible({ timeout: 15_000 });

  // The app switcher is the first `.relative`-wrapped dropdown in the header
  // (logo -> divider -> switcher -> spacer -> Data -> Sessions -> Deploy -> user menu).
  const switcherWrapper = page.locator('header div.relative').first();
  const switcherTrigger = switcherWrapper.locator('button').first();
  await switcherTrigger.click();
  await expect(switcherWrapper.getByText(userA.appName)).toBeVisible({ timeout: 10_000 });
});

test('a previous session\u2019s app selection never survives an auth-expiry re-login as a different user', async ({ page }) => {
  const userA = await registerUserWithApp('s28b1');
  if (!userA) { test.skip(true, 'Registration rate-limited — re-run after 60 min'); return; }
  const userB = await registerUserWithApp('s28b2');
  if (!userB) { test.skip(true, 'Registration rate-limited — re-run after 60 min'); return; }

  await page.goto(STUDIO_URL);
  await expect(page.getByPlaceholder('you@example.com')).toBeVisible({ timeout: 10_000 });
  await page.getByPlaceholder('you@example.com').fill(userA.email);
  await page.getByPlaceholder('••••••••').fill(userA.password);
  await page.locator('button[type="submit"]').click();
  await expect(page.getByPlaceholder('Describe your app or ask anything…')).toBeVisible({ timeout: 15_000 });

  const switcherWrapper = page.locator('header div.relative').first();
  const switcherTrigger = switcherWrapper.locator('button').first();

  // Select App-A so apps[] + currentApp are populated with User A's data.
  await switcherTrigger.click();
  await switcherWrapper.getByText(userA.appName).click();
  await expect(switcherTrigger).toContainText(userA.appName);

  // Simulate the transport-level 401 recovery path: authedFetch dispatches
  // this event on any backend 401 (e.g. after a restart invalidates the
  // persisted token). AuthGate's listener clears the session and shows the
  // login form again, without a page reload.
  await page.evaluate(() => window.dispatchEvent(new CustomEvent('appbana:auth:expired')));
  await expect(page.getByText('Your session expired. Please sign in again to continue.')).toBeVisible({ timeout: 5_000 });

  // Delay the next apps-list fetch so the pre-fetch-resolution DOM state is
  // observable deterministically, regardless of how fast the local backend
  // actually responds.
  await page.route('**/appbana-studio/*/apps', async (route) => {
    if (route.request().method() === 'GET') {
      await new Promise((r) => setTimeout(r, 3000));
    }
    await route.continue();
  });

  await page.getByPlaceholder('you@example.com').fill(userB.email);
  await page.getByPlaceholder('••••••••').fill(userB.password);
  await page.locator('button[type="submit"]').click();

  // Studio chrome (Header) remounts as soon as `token` is set, before the
  // delayed apps fetch resolves. It must never show User A's app here —
  // that would mean workspace state survived the session boundary.
  await expect(page.getByPlaceholder('Describe your app or ask anything…')).toBeVisible({ timeout: 15_000 });
  await expect(switcherTrigger).not.toContainText(userA.appName, { timeout: 500 });
  await expect(switcherTrigger).toContainText('Select app');

  // After the delayed fetch resolves, User A's app must still never appear.
  await page.waitForTimeout(3500);
  await switcherTrigger.click();
  await expect(switcherWrapper.getByText(userA.appName)).toHaveCount(0);
});

test('a previous session\u2019s chat history never survives an auth-expiry re-login as a different user', async ({ page }) => {
  const userA = await registerUserWithApp('s28c1');
  if (!userA) { test.skip(true, 'Registration rate-limited — re-run after 60 min'); return; }
  const userB = await registerUserWithApp('s28c2');
  if (!userB) { test.skip(true, 'Registration rate-limited — re-run after 60 min'); return; }

  const secretMessage = `USER-A-CONFIDENTIAL-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

  await page.goto(STUDIO_URL);
  await expect(page.getByPlaceholder('you@example.com')).toBeVisible({ timeout: 10_000 });
  await page.getByPlaceholder('you@example.com').fill(userA.email);
  await page.getByPlaceholder('••••••••').fill(userA.password);
  await page.locator('button[type="submit"]').click();
  const composer = page.getByPlaceholder('Describe your app or ask anything… (paste an image to attach)');
  await expect(composer).toBeVisible({ timeout: 15_000 });

  // useChatStore.addUserMessage() renders the bubble synchronously, before any
  // network call to the AI Builder is made — so this assertion holds even
  // when the AI Builder isn't running (see file header).
  await composer.fill(secretMessage);
  await composer.press('Enter');
  await expect(page.getByText(secretMessage)).toBeVisible({ timeout: 5_000 });

  // Same transport-level 401 recovery path as the app-switcher test above.
  await page.evaluate(() => window.dispatchEvent(new CustomEvent('appbana:auth:expired')));
  await expect(page.getByText('Your session expired. Please sign in again to continue.')).toBeVisible({ timeout: 5_000 });

  await page.getByPlaceholder('you@example.com').fill(userB.email);
  await page.getByPlaceholder('••••••••').fill(userB.password);
  await page.locator('button[type="submit"]').click();
  await expect(composer).toBeVisible({ timeout: 15_000 });

  // User B must never see User A's message content anywhere on the page —
  // that would mean the chat store survived the session boundary.
  await expect(page.getByText(secretMessage)).toHaveCount(0);
});
