/**
 * AI Builder chat E2E flow
 *
 * 1. Registers a fresh user via the backend REST API (unique email per run)
 * 2. Logs in through the Studio UI (AuthGuard shadow DOM)
 * 3. Opens the AI Builder chat panel
 * 4. Sends a prompt and waits for the assistant to respond
 *
 * The stack (UI 5173, Backend 8080, AI Builder 8081) must already be
 * running via `.\scripts\start-everything.bat`.
 */
import { expect, request, test } from '@playwright/test';

const BACKEND_URL = process.env.APPBANA_BACKEND_URL ?? 'http://localhost:8080';
const AI_BUILDER_URL = process.env.APPBANA_AI_BUILDER_URL ?? 'http://localhost:8081';

function uniqueUser() {
  const stamp = Date.now();
  return {
    name: `E2E Tester ${stamp}`,
    // Backend enforces email format; keep the local part clearly unique.
    email: `e2e.tester+${stamp}@appbana.test`,
    // Backend requires 8+ chars with letters and numbers.
    password: `Passw0rd-${stamp}`,
  };
}

test.beforeAll(async () => {
  // Fail fast if the stack isn't running.
  const api = await request.newContext();
  for (const [name, url] of [
    ['Backend', `${BACKEND_URL}/health`],
    ['AI Builder', `${AI_BUILDER_URL}/health`],
  ] as const) {
    const res = await api.get(url).catch((e) => {
      throw new Error(`${name} unreachable at ${url}: ${(e as Error).message}`);
    });
    if (!res.ok()) {
      throw new Error(`${name} health check failed at ${url}: HTTP ${res.status()}`);
    }
  }
  await api.dispose();
});

test('registers a new user, logs in, and gets a reply from AI Builder chat', async ({ page }) => {
  const user = uniqueUser();

  // --- Step 1: register via REST -------------------------------------------
  const api = await request.newContext();
  const registerRes = await api.post(`${BACKEND_URL}/api/auth/register`, {
    data: { email: user.email, password: user.password, name: user.name },
  });
  expect(registerRes.status(), await registerRes.text()).toBeLessThan(400);
  await api.dispose();

  // --- Step 2: open Studio and sign in via the UI --------------------------
  const consoleErrors: string[] = [];
  page.on('console', (msg) => {
    if (msg.type() === 'error') consoleErrors.push(msg.text());
  });

  await page.goto('/studio.html');

  // AuthGuard renders inside its shadow DOM. Playwright's default locators
  // pierce shadow DOM, so we can address inputs by placeholder.
  // Default AuthGuard tab is "Sign In" (login), so no tab click needed.
  const authGuard = page.locator('auth-guard');
  await expect(authGuard).toBeVisible({ timeout: 10_000 });

  await page.getByPlaceholder('you@example.com').fill(user.email);
  await page.getByPlaceholder('••••••••').first().fill(user.password);

  // Click the form submit button (not the tab with the same label).
  await page.locator('button[type="submit"].submit-btn').click();

  // Wait for the AuthGuard to render the builder shell.
  await expect(page.locator('appbana-builder-shell')).toBeVisible({ timeout: 15_000 });

  // --- Step 3: activate the AI Agent tab ----------------------------------
  // The Studio sidebar has Components / Entities / AI Agent / Workflow tabs.
  const aiAgentTab = page.getByRole('button', { name: /AI Agent/i }).first();
  if (await aiAgentTab.isVisible().catch(() => false)) {
    await aiAgentTab.click();
  }

  // --- Step 4: type a prompt and send -------------------------------------
  const chatInput = page.getByPlaceholder('Type or paste images to analyze...');
  await expect(chatInput).toBeVisible({ timeout: 15_000 });

  const prompt = 'I want a simple contact list app to track names and phone numbers.';
  await chatInput.fill(prompt);
  await page.getByRole('button', { name: 'Send', exact: true }).click();

  // The user message should render immediately.
  await expect(page.getByText(prompt, { exact: false })).toBeVisible({ timeout: 5_000 });

  // --- Step 5: assert the assistant replies with something meaningful ------
  // The reply must NOT be the pre-auth guard message, and must NOT be the
  // generic error fallback ("Sorry, I encountered an error").
  const forbiddenReplies = [
    'Please log in to use the AI Builder.',
    'Sorry, I encountered an error. Please try again.',
  ];

  // Wait up to 45s for a new assistant bubble to appear beyond the welcome one.
  // Chat messages render as <ai-message role="..." content="..."> custom
  // elements. We reach into the property (not attribute) via evaluateAll so
  // we don't depend on the internal shadow-DOM markup.
  let observedBubbles: Array<{ role: string; content: string }> = [];
  await expect
    .poll(
      async () => {
        observedBubbles = await page.locator('ai-message').evaluateAll((els) =>
          els.map((el: any) => ({
            role: String(el.role ?? ''),
            content: String(el.content ?? ''),
          })),
        );
        const meaningful = observedBubbles.filter(
          (b) =>
            b.role === 'assistant' &&
            b.content.trim().length > 20 &&
            !forbiddenReplies.some((f) => b.content.includes(f)) &&
            !b.content.includes("I'm here to help you build"),
        );
        return meaningful.length;
      },
      { timeout: 45_000, message: 'AI Builder never returned a reply' },
    )
    .toBeGreaterThan(0);

  // Fail loudly if the assistant emitted a forbidden reply anywhere.
  for (const forbidden of forbiddenReplies) {
    const hit = observedBubbles.find((b) => b.content.includes(forbidden));
    expect(hit, `Assistant returned forbidden reply: ${forbidden}`).toBeUndefined();
  }

  // Surface any console errors as test annotations (not fatal).
  if (consoleErrors.length > 0) {
    test.info().annotations.push({
      type: 'console-errors',
      description: consoleErrors.slice(0, 10).join('\n---\n'),
    });
  }
});
