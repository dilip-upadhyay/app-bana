/**
 * hardening/fixtures.ts — shared test fixtures for the H7 hardening sprint
 * spec suite. Every hardening spec talks directly to the running backend
 * (:8080) rather than driving the UI, because the hardening items are
 * wire-level contracts (tenant isolation, real FKs, accurate group counts,
 * schema round-trip) and browser flows would need an AI-generated app that
 * makes the suite flaky.
 *
 * If the backend is unreachable, `healthCheck()` returns false and the
 * caller test.skips the whole file — matching the pattern used by
 * `sprint-3-crud-roundtrip.spec.ts`.
 */
import { request, type APIRequestContext } from '@playwright/test';

export const BACKEND_URL =
  process.env.APPBANA_BACKEND_URL ?? 'http://localhost:8080';

export interface HardeningFixture {
  api: APIRequestContext;
  token: string;
  tenantId: string;
  appId: string;
}

/** Ping /health; a false result should trigger test.skip in the caller. */
export async function healthCheck(): Promise<boolean> {
  const api = await request.newContext();
  try {
    const res = await api.get(`${BACKEND_URL}/health`).catch(() => null);
    return !!res && res.ok();
  } finally {
    await api.dispose();
  }
}

/**
 * Register + login a fresh user, then create an isolated app.
 * Returns null if the backend rate-limits us (429) — caller should skip.
 */
export async function newHardeningFixture(prefix: string): Promise<HardeningFixture | null> {
  const stamp = Date.now();
  const suffix = Math.random().toString(36).slice(2, 8);
  const email = `${prefix}+${stamp}-${suffix}@appbana.test`;
  const password = `Passw0rd-${stamp}`;

  const api = await request.newContext();
  const reg = await api.post(`${BACKEND_URL}/api/auth/register`, {
    data: { email, password, name: `${prefix} ${stamp}` },
  });
  if (reg.status() === 429) {
    await api.dispose();
    return null;
  }
  if (!reg.ok()) {
    const body = await reg.text();
    await api.dispose();
    throw new Error(`register failed HTTP ${reg.status()}: ${body}`);
  }

  const login = await api.post(`${BACKEND_URL}/api/auth/login`, {
    data: { email, password },
  });
  if (!login.ok()) {
    const body = await login.text();
    await api.dispose();
    throw new Error(`login failed HTTP ${login.status()}: ${body}`);
  }
  const loginBody = await login.json();
  const token = loginBody.token as string;
  const tenantId = (loginBody.tenantId as string | undefined) ?? 'default';

  const appId = `${prefix}-${stamp}-${suffix}`.toLowerCase();
  const createApp = await api.post(`${BACKEND_URL}/appbana-studio/${tenantId}/apps`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { id: appId, name: `${prefix}-${stamp}` },
  });
  if (!createApp.ok()) {
    const body = await createApp.text();
    await api.dispose();
    throw new Error(`createApp failed HTTP ${createApp.status()}: ${body}`);
  }

  return { api, token, tenantId, appId };
}

export async function disposeFixture(f: HardeningFixture | null): Promise<void> {
  if (!f) return;
  try {
    await f.api.delete(`${BACKEND_URL}/appbana-studio/${f.tenantId}/apps/${f.appId}`, {
      headers: { Authorization: `Bearer ${f.token}` },
    });
  } catch {
    /* best-effort cleanup */
  } finally {
    await f.api.dispose();
  }
}

/** Save an entity schema. Prefixed key is auto-derived by the backend. */
export async function saveSchema(
  f: HardeningFixture,
  entityName: string,
  fields: Array<Record<string, unknown>>,
): Promise<string> {
  const res = await f.api.post(`${BACKEND_URL}/schema`, {
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${f.token}` },
    data: { name: entityName, appId: f.appId, tenantId: f.tenantId, fields },
  });
  if (!res.ok()) {
    const body = await res.text();
    throw new Error(`saveSchema(${entityName}) failed HTTP ${res.status()}: ${body}`);
  }
  return `${f.tenantId}_${f.appId}_${entityName}`;
}
