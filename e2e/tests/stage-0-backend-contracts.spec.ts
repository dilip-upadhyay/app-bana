/**
 * Stage 0 — Backend contract tests
 *
 * Pure REST (no browser). Verifies the endpoints added in Stage 0 of the
 * AI-Native UI Rebuild plan:
 *
 *   1. GET /api/tenants/{tenantId}/branding    (public, tenant-branding)
 *   2. GET /api/app-context                    (subdomain-ready context)
 *   3. POST /api/ai/chat/agent/stream          (SSE with 5-event contract)
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
test('SSE endpoint returns text/event-stream with the 5-event contract and exactly one done', async () => {
  test.setTimeout(60_000);

  const payload = {
    message:   'hi',
    sessionId: crypto.randomUUID(),
    userId:    'e2e-stage-0',
    tenantId:  'default',
    appId:     '',
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

  // Every event name must be from the documented 5-event contract
  const allowed = new Set(['token', 'tool_call_start', 'tool_call_end', 'state', 'done']);
  for (const name of seenEvents) {
    expect.soft(allowed.has(name), `unexpected SSE event name: ${name}`).toBe(true);
  }
});
