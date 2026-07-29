/**
 * Stage 0 — Backend contract tests
 *
 * Pure REST (no browser). Verifies the endpoints added in Stage 0 of the
 * AI-Native UI Rebuild plan:
 *
 *   1. GET /api/tenants/{tenantId}/branding    (public, tenant-branding)
 *   2. GET /api/app-context                    (subdomain-ready context)
 *   3. POST /api/ai/chat/agent/stream          (SSE with 6-event contract,
 *                                               requires a session token —
 *                                               C4.4e Review #12)
 *
 * Prerequisites: backend 8080, ai-builder 8081 must be running.
 */
import { expect, request, test } from '@playwright/test';

const BACKEND_URL = process.env.APPBANA_BACKEND_URL ?? 'http://localhost:8080';
const AI_URL      = process.env.APPBANA_AI_BUILDER_URL ?? 'http://localhost:8081';

test.beforeAll(async () => {
  const api = await request.newContext();
  for (const [name, url] of [
    ['Backend',    `${BACKEND_URL}/health`],
    ['AI Builder', `${AI_URL}/health`],
  ] as const) {
    const res = await api.get(url).catch((e) => {
      throw new Error(`${name} unreachable at ${url}: ${(e as Error).message}`);
    });
    if (!res.ok()) throw new Error(`${name} health check failed: HTTP ${res.status()}`);
  }
  await api.dispose();
});

// ── /api/tenants/{tenantId}/branding ─────────────────────────────────────────
test('branding endpoint returns seeded row for the default tenant', async ({ request }) => {
  const res = await request.get(`${BACKEND_URL}/api/tenants/default/branding`);
  expect(res.status()).toBe(200);
  const body = await res.json();

  expect(body).toMatchObject({
    tenantId:     'default',
    displayName:  expect.any(String),
    primaryColor: expect.stringMatching(/^#[0-9a-fA-F]{6}$/),
  });
  // logoUrl is optional — may be null
  expect(body).toHaveProperty('logoUrl');
});

test('branding endpoint returns sensible defaults for an unknown tenant', async ({ request }) => {
  const res = await request.get(`${BACKEND_URL}/api/tenants/does-not-exist-xyz/branding`);
  expect(res.status()).toBe(200);
  const body = await res.json();

  // Response is not a 404 — it degrades to defaults (safe for pre-login UX)
  expect(body).toMatchObject({
    tenantId:     'does-not-exist-xyz',
    displayName:  expect.any(String),
    primaryColor: expect.stringMatching(/^#[0-9a-fA-F]{6}$/),
  });
});

// ── /api/app-context ─────────────────────────────────────────────────────────
test('app-context endpoint returns defaults when no params are supplied', async ({ request }) => {
  const res = await request.get(`${BACKEND_URL}/api/app-context`);
  expect(res.status()).toBe(200);
  const body = await res.json();

  expect(body).toMatchObject({
    tenantId: expect.any(String),
    appId:    expect.any(String),
    branding: {
      displayName:  expect.any(String),
      primaryColor: expect.stringMatching(/^#[0-9a-fA-F]{6}$/),
    },
  });
});

test('app-context echoes tenantId and appId when passed as query params', async ({ request }) => {
  const res = await request.get(
    `${BACKEND_URL}/api/app-context?tenantId=default&appId=demo-app`,
  );
  expect(res.status()).toBe(200);
  const body = await res.json();

  expect(body.tenantId).toBe('default');
  expect(body.appId).toBe('demo-app');
  expect(body.branding).toBeTruthy();
});

// ── /api/{tenantId}/apps/{id}/publish ────────────────────────────────────────
// Regression guard for the deploy 400 bug: backend expects `env` as a QUERY
// parameter, not a body field. This test proves the two behaviours so the
// shared client stays honest.
test('publish endpoint rejects requests without the ?env= query parameter', async ({ request }) => {
  const res = await request.post(
    `${BACKEND_URL}/api/does-not-matter/apps/does-not-matter/publish`,
    { data: { environment: 'DEV' } }, // env in body only — should 400 before hitting DB
  );
  expect(res.status()).toBe(400);
  const body = await res.json().catch(() => ({}));
  // Backend returns { error: "env query parameter required (DEV, SIT, or PROD)" }
  // or "tenantId required" depending on validation order. Either way it's a 400
  // from missing required inputs, which is exactly what we want to guard.
  expect(String(body.error ?? '')).toMatch(/env|tenant/i);
});

// ── /api/{entityKey} (public for runtime apps) ───────────────────────────────
// Regression guard for the "Failed to fetch rows: 401" bug.
//
// Entity URLs are SINGLE path segment (underscore-joined
// "{tenantId}_{appId}_{entityName}") — not two slash segments. The original
// SessionMiddleware regex `^/api/[^/]+/[^/]+/?$` required two segments, so
// runtime data fetches always got 401 even with a valid Bearer token.
//
// Public here means "no session cookie required" (defense in depth still comes
// from route-level admin/read tokens when configured in production).
test('runtime entity API is public — no session token required, no 401', async ({ request }) => {
  // Unknown entity is fine — we're proving the middleware doesn't 401 us.
  // Backend should reply with 200/404/500 from the ROUTE handler, never 401
  // from the session middleware.
  const res = await request.get(
    `${BACKEND_URL}/api/default_stage-0-nonexistent-app_DoesNotExist?limit=1`,
  );
  expect.soft(res.status(), 'entity API must not return 401 without a session').not.toBe(401);
  expect(res.status()).toBeLessThan(500);
});

test('runtime entity API /{rowId} sub-path is public — no 401', async ({ request }) => {
  const res = await request.get(
    `${BACKEND_URL}/api/default_stage-0-nonexistent-app_DoesNotExist/some-row-id`,
  );
  expect.soft(res.status(), 'entity /{rowId} must not return 401 without a session').not.toBe(401);
  expect(res.status()).toBeLessThan(500);
});

test('runtime entity API /batch sub-path is public — no 401', async ({ request }) => {
  const res = await request.post(
    `${BACKEND_URL}/api/default_stage-0-nonexistent-app_DoesNotExist/batch`,
    { data: [] },
  );
  expect.soft(res.status(), 'entity /batch must not return 401 without a session').not.toBe(401);
  expect(res.status()).toBeLessThan(500);
});

/**
 * Registers + logs in a fresh user against the core backend and returns a live
 * session token. Mirrors hardening/fixtures.ts's newHardeningFixture() but
 * skips app creation — the SSE contract test only needs a valid token.
 */
async function getFreshSessionToken(): Promise<string> {
  const api = await request.newContext();
  try {
    const stamp = Date.now();
    const suffix = Math.random().toString(36).slice(2, 8);
    const email = `stage0-sse+${stamp}-${suffix}@appbana.test`;
    const password = `Passw0rd-${stamp}`;

    const reg = await api.post(`${BACKEND_URL}/api/auth/register`, {
      data: { email, password, name: `stage0-sse ${stamp}` },
    });
    if (!reg.ok()) {
      throw new Error(`register failed HTTP ${reg.status()}: ${await reg.text()}`);
    }

    const login = await api.post(`${BACKEND_URL}/api/auth/login`, {
      data: { email, password },
    });
    if (!login.ok()) {
      throw new Error(`login failed HTTP ${login.status()}: ${await login.text()}`);
    }
    const loginBody = await login.json();
    return loginBody.token as string;
  } finally {
    await api.dispose();
  }
}

test('SSE endpoint returns text/event-stream with the 5-event contract and exactly one done', async () => {
  test.setTimeout(60_000);

  const token = await getFreshSessionToken();
  const payload = {
    message:   'hi',
    sessionId: crypto.randomUUID(),
    userId:    'e2e-stage-0',
    tenantId:  'default',
    appId:     '',
    token,
  };

  const res = await fetch(`${AI_URL}/api/ai/chat/agent/stream`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Accept': 'text/event-stream' },
    body: JSON.stringify(payload),
  });

  expect(res.status).toBe(200);
  expect(res.headers.get('content-type') ?? '').toContain('text/event-stream');
  expect(res.body).not.toBeNull();

  // Read the full stream and count event names.
  const reader = res.body!.getReader();
  const decoder = new TextDecoder();
  const seenEvents: string[] = [];
  let buffer = '';

  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });

      // Extract every `event: <name>` line as it appears
      const lines = buffer.split(/\r?\n/);
      buffer = lines.pop() ?? ''; // keep the (possibly partial) last line
      for (const line of lines) {
        const m = /^event:\s*(\S+)/.exec(line);
        if (m) seenEvents.push(m[1]);
      }

      // The server explicitly emits `done` before closing — bail as soon as we see it
      if (seenEvents.includes('done')) break;
    }
  } finally {
    reader.releaseLock();
  }

  // Contract: exactly one done, at least one state and one token
  const only = (name: string) => seenEvents.filter((e) => e === name).length;
  expect.soft(only('done'), `expected exactly 1 done event, saw ${only('done')} in ${JSON.stringify(seenEvents)}`).toBe(1);
  expect.soft(only('state'), 'expected at least 1 state event').toBeGreaterThanOrEqual(1);
  expect.soft(only('token'), 'expected at least 1 token event').toBeGreaterThanOrEqual(1);

  // Every event name must be from the documented event contract (the base 5
  // plus `auth_expired`, added by C4.4e Review #12 for mid-stream 401s).
  const allowed = new Set(['token', 'tool_call_start', 'tool_call_end', 'state', 'done', 'auth_expired']);
  for (const name of seenEvents) {
    expect.soft(allowed.has(name), `unexpected SSE event name: ${name}`).toBe(true);
  }
});

// C4.4e Review #12 (DoD item 4) — the SSE endpoint requires a session token
// (AgentStreamController rejects a missing/blank token before opening the
// stream at all). A tokenless request must 401, never silently open a stream.
test('SSE endpoint rejects a tokenless request with 401', async () => {
  const payload = {
    message:   'hi',
    sessionId: crypto.randomUUID(),
    userId:    'e2e-stage-0-tokenless',
    tenantId:  'default',
    appId:     '',
    // token intentionally omitted
  };

  const res = await fetch(`${AI_URL}/api/ai/chat/agent/stream`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Accept': 'text/event-stream' },
    body: JSON.stringify(payload),
  });

  expect(res.status).toBe(401);
  const body = await res.json();
  expect(String(body.error ?? '')).toMatch(/unauthorized/i);
});
