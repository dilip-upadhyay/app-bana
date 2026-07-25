/**
 * AI-native Studio smoke test
 *
 * Verifies:
 * 1. Studio login screen loads at http://localhost:5174
 * 2. A new user can register and land in the studio shell
 * 3. Sending a chat message returns SSE events and renders at least one tool card
 *    or assistant reply (proves the SSE pipeline is wired end-to-end)
 *
 * Prerequisites: all three services must be running
 *   backend 8080, ai-builder 8081, studio 5174
 */
import { expect, request, test } from '@playwright/test';

const BACKEND_URL = process.env.APPBANA_BACKEND_URL ?? 'http://localhost:8080';
const STUDIO_URL  = process.env.APPBANA_STUDIO_URL  ?? 'http://localhost:5174';
const AI_URL      = process.env.APPBANA_AI_BUILDER_URL ?? 'http://localhost:8081';

function uniqueUser() {
  const stamp = Date.now();
  return {
    name:     `Studio Tester ${stamp}`,
    email:    `studio.tester+${stamp}@appbana.test`,
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

test('registers a new user, logs into the AI studio, and gets a reply via SSE', async ({ page }) => {
  const user = uniqueUser();

  // ── Step 1: register via REST ──────────────────────────────────────────────
  const api = await request.newContext();
  const reg = await api.post(`${BACKEND_URL}/api/auth/register`, {
    data: { email: user.email, password: user.password, name: user.name },
  });
  expect(reg.status(), await reg.text()).toBeLessThan(400);
  await api.dispose();

  // ── Step 2: open studio and sign in ───────────────────────────────────────
  await page.goto(STUDIO_URL);

  // React auth gate: no shadow DOM — plain CSS selectors
  await expect(page.getByPlaceholder('you@example.com')).toBeVisible({ timeout: 10_000 });

  await page.getByPlaceholder('you@example.com').fill(user.email);
  await page.getByPlaceholder('••••••••').fill(user.password);
  await page.getByRole('button', { name: 'Sign In' }).click();

  // Studio shell — chat textarea appears
  const chatInput = page.getByPlaceholder('Describe your app or ask anything…');
  await expect(chatInput).toBeVisible({ timeout: 15_000 });

  // ── Step 3: send a prompt ─────────────────────────────────────────────────
  const prompt = 'I want a simple contact list app to track names and phone numbers.';
  await chatInput.fill(prompt);
  await chatInput.press('Enter');

  // User message bubble appears immediately
  await expect(page.getByText(prompt, { exact: false })).toBeVisible({ timeout: 5_000 });

  // ── Step 4: wait for assistant reply ──────────────────────────────────────
  // Either a non-empty assistant bubble OR a tool-call card (summary element)
  // signals that the SSE pipeline delivered at least one event to the UI.
  await expect
    .poll(
      async () => {
        const bubbles = await page.locator('.prose-chat').count();
        const toolCards = await page.locator('details').count();
        return bubbles + toolCards;
      },
      { timeout: 45_000, message: 'Studio never rendered an assistant reply or tool card' }
    )
    .toBeGreaterThan(0);
});
