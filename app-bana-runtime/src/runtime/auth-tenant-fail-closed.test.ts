/**
 * auth-tenant-fail-closed.test.ts — S1.13.
 *
 * login()/register() must throw when the backend response's `user.tenantId`
 * is missing, rather than silently defaulting to the literal string
 * 'default' — that would place the caller in a real, populated tenant
 * namespace instead of surfacing an unexpected response shape.
 *
 * Not naturally triggerable against the real running backend: UserDTO's
 * compact constructor rejects a null/blank tenantId server-side, so a
 * genuine 200 response always carries one (see docs/planning/
 * TENANT_ISOLATION_IMPLEMENTATION_TASKS.md, S1.13's Cat. 2 note). Covered
 * here by mocking the response shape directly, following the same
 * `globalThis.fetch` stubbing pattern as AuditDrawer.test.tsx's envelope
 * tests.
 */
import { describe, it, expect, afterEach, vi } from 'vitest';
import { login, register } from '@appbana/shared';

describe('login()/register() tenantId fail-closed behavior (S1.13)', () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  function stubJson(payload: unknown) {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => payload,
    }) as unknown as typeof fetch;
  }

  it('login() throws when user.tenantId is missing rather than defaulting to "default"', async () => {
    stubJson({ token: 'tok', user: { id: 1, email: 'a@b.com', name: 'A' } });
    await expect(login('a@b.com', 'pw')).rejects.toThrow('user.tenantId');
  });

  it('login() resolves normally when user.tenantId is present', async () => {
    stubJson({ token: 'tok', user: { id: 1, email: 'a@b.com', name: 'A', tenantId: 't-abc123' } });
    const result = await login('a@b.com', 'pw');
    expect(result.tenantId).toBe('t-abc123');
  });

  it('register() throws when user.tenantId is missing rather than defaulting to "default"', async () => {
    stubJson({ token: 'tok', user: { id: 1, email: 'a@b.com', name: 'A' } });
    await expect(register('A', 'a@b.com', 'pw')).rejects.toThrow('user.tenantId');
  });

  it('register() resolves normally when user.tenantId is present', async () => {
    stubJson({ token: 'tok', user: { id: 1, email: 'a@b.com', name: 'A', tenantId: 't-xyz789' } });
    const result = await register('A', 'a@b.com', 'pw');
    expect(result.tenantId).toBe('t-xyz789');
  });
});
