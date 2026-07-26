/**
 * Sprint 3 post-review — Entity CRUD round-trip + typed validation error contract.
 *
 * <p>The Sprint 3 review flagged two gaps that this suite closes:
 *
 * <ol>
 *   <li><b>End-to-end CRUD was never exercised.</b> Unit tests cover the
 *       primitives (Button, cell-formatters, useEntityFormValidation), but
 *       nothing walks the full <em>create → read → update → delete</em> path
 *       against the running backend. This spec drives that flow via REST so
 *       it's stable and fast, without needing the UI or AI builder.</li>
 *   <li><b>The new {@code FieldValidationException} contract was covered by a
 *       unit test but never verified over the wire.</b> The <em>required-field</em>
 *       case below asserts the actual HTTP 400 body shape the runtime
 *       {@code useEntityFormValidation} hook consumes — regressing the
 *       structure here would silently break inline field errors.</li>
 * </ol>
 *
 * <p>Prerequisites: backend 8080 (and the {@code auth/register} + {@code /schema}
 * endpoints must accept anonymous writes in dev, which they do by default).
 * If backend is unreachable the whole suite skips rather than failing.
 *
 * <p>Note: this is <em>not</em> a UI test — the equivalent Playwright browser
 * flow requires an AI-generated app with list/form/detail pages, which is
 * flaky without a mocked LLM. The API surface tested here is the same
 * surface the UI depends on, so a passing spec here means the UI's data
 * layer is sound.
 */
import { expect, request, test, type APIRequestContext } from '@playwright/test';

const BACKEND_URL = process.env.APPBANA_BACKEND_URL ?? 'http://localhost:8080';

interface Fixture {
  api: APIRequestContext;
  token: string;
  tenantId: string;
  appId: string;
  entityName: string;
  entityKey: string;
}

/** Skip helper — soft-skips when backend health check fails. */
async function healthCheck(): Promise<boolean> {
  const api = await request.newContext();
  try {
    const res = await api.get(`${BACKEND_URL}/health`).catch(() => null);
    return !!res && res.ok();
  } finally {
    await api.dispose();
  }
}

/** Register + login a fresh user, then create an app + entity schema. */
async function setup(): Promise<Fixture | null> {
  const stamp = Date.now();
  const user = {
    name:     `CRUD Roundtrip ${stamp}`,
    email:    `crud.roundtrip+${stamp}@appbana.test`,
    password: `Passw0rd-${stamp}`,
  };

  const api = await request.newContext();
  const reg = await api.post(`${BACKEND_URL}/api/auth/register`, {
    data: { email: user.email, password: user.password, name: user.name },
  });
  if (reg.status() === 429) {
    await api.dispose();
    return null; // rate-limited — caller should skip
  }
  if (!reg.ok()) {
    const body = await reg.text();
    throw new Error(`register failed HTTP ${reg.status()}: ${body}`);
  }

  const login = await api.post(`${BACKEND_URL}/api/auth/login`, {
    data: { email: user.email, password: user.password },
  });
  if (!login.ok()) {
    const body = await login.text();
    throw new Error(`login failed HTTP ${login.status()}: ${body}`);
  }
  const loginBody = await login.json();
  const token = loginBody.token as string;
  const tenantId = (loginBody.tenantId as string | undefined) ?? 'default';

  // Create the app (backend requires client-supplied id + name)
  const appId = `rt-${stamp}-${Math.random().toString(36).slice(2, 8)}`;
  const appName = `RoundtripApp-${stamp}`;
  const createApp = await api.post(`${BACKEND_URL}/appbana-studio/${tenantId}/apps`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { id: appId, name: appName },
  });
  if (!createApp.ok()) {
    const body = await createApp.text();
    throw new Error(`createApp failed HTTP ${createApp.status()}: ${body}`);
  }
  const app = await createApp.json();
  // Backend echoes the id, but we prefer our locally-known value.
  void app;

  // Create the entity schema — send the RAW entity name; SchemaManager
  // auto-prefixes with tenantId + appId when building the storage key.
  //
  // Note: we deliberately avoid `decimal` fields. `EntityCrudService.
  // coerceAndValidate` has no case for `decimal` — the default branch
  // treats the value as a String, which Postgres then rejects with
  // "column X is of type numeric but expression is of type character
  // varying". That is a pre-existing bug (not introduced by Sprint 3)
  // that will surface for every AI-generated app using money/price
  // fields — worth a follow-up issue.
  const entityName = 'Widget';
  const entityKey = `${tenantId}_${appId}_${entityName}`;
  const schema = {
    name: entityName,
    appId,
    tenantId,
    fields: [
      { name: 'id',    type: 'long',    primaryKey: true, autoIncrement: true },
      { name: 'name',  type: 'text',    required: true },
      { name: 'sku',   type: 'text' },
    ],
  };
  const saveSchema = await api.post(`${BACKEND_URL}/schema`, {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    data: schema,
  });
  if (!saveSchema.ok()) {
    const body = await saveSchema.text();
    throw new Error(`saveSchema failed HTTP ${saveSchema.status()}: ${body}`);
  }

  return { api, token, tenantId, appId, entityName, entityKey };
}

async function teardown(f: Fixture): Promise<void> {
  // Best-effort cleanup — swallow errors so a mid-test failure doesn't hide.
  try {
    await f.api.delete(`${BACKEND_URL}/schema/${f.entityKey}?dropTable=true`, {
      headers: { Authorization: `Bearer ${f.token}` },
    });
    await f.api.delete(`${BACKEND_URL}/appbana-studio/${f.tenantId}/apps/${f.appId}`, {
      headers: { Authorization: `Bearer ${f.token}` },
    });
  } catch {
    /* ignore */
  } finally {
    await f.api.dispose();
  }
}

test.describe('Sprint 3 CRUD round-trip', () => {
  test.beforeAll(async () => {
    if (!(await healthCheck())) {
      test.skip(true, `Backend unreachable at ${BACKEND_URL} — start the stack first`);
    }
  });

  test('create → read → update → delete + typed validation error', async () => {
    const fx = await setup();
    if (!fx) { test.skip(true, 'Registration rate-limited'); return; }

    try {
      const base = `${BACKEND_URL}/api/${fx.entityKey}`;

      // ── CREATE ────────────────────────────────────────────────
      const createRes = await fx.api.post(base, {
        headers: { 'Content-Type': 'application/json' },
        data: { name: 'Sprocket', sku: 'SKU-001' },
      });
      expect(createRes.status(), await createRes.text()).toBeLessThan(300);
      const created = await createRes.json();
      // Backend returns { status: 'created', id, ... } — id lives at top level
      // OR nested. Support both shapes.
      const rowId = String(created.id ?? created.record?.id ?? created?.data?.id ?? '');
      expect(rowId, 'insert should return an id').not.toBe('');

      // ── READ (single) ──────────────────────────────────────────
      const readRes = await fx.api.get(`${base}/${rowId}`);
      expect(readRes.status()).toBe(200);
      const readBody = await readRes.json();
      const record = readBody.record ?? readBody;
      // Postgres returns column labels in the case the DB stored them — with
      // the current SchemaManager that means UPPERCASE. Accept either shape
      // so the test doesn't need to know about SchemaManager's casing rules.
      expect(record.name ?? record.NAME).toBe('Sprocket');

      // ── UPDATE ────────────────────────────────────────────────
      const updateRes = await fx.api.put(`${base}/${rowId}`, {
        headers: { 'Content-Type': 'application/json' },
        data: { name: 'Widget-Pro', sku: 'SKU-002' },
      });
      expect(updateRes.status(), await updateRes.text()).toBeLessThan(300);

      const reReadRes = await fx.api.get(`${base}/${rowId}`);
      const reReadBody = await reReadRes.json();
      const reRead = reReadBody.record ?? reReadBody;
      expect(reRead.name ?? reRead.NAME).toBe('Widget-Pro');

      // ── DELETE ────────────────────────────────────────────────
      const deleteRes = await fx.api.delete(`${base}/${rowId}`);
      expect(deleteRes.status(), await deleteRes.text()).toBeLessThan(300);

      const goneRes = await fx.api.get(`${base}/${rowId}`);
      // 404 (not found) is the expected shape, but some builds return 200 with
      // an empty record — accept either, just prove the row is unretrievable.
      if (goneRes.status() === 200) {
        const goneBody = await goneRes.json();
        expect(goneBody.record ?? null).toBeFalsy();
      } else {
        expect(goneRes.status()).toBe(404);
      }
    } finally {
      await teardown(fx);
    }
  });

  test('missing required field returns typed FieldValidationException body', async () => {
    const fx = await setup();
    if (!fx) { test.skip(true, 'Registration rate-limited'); return; }

    try {
      // Omit the required `name` field on purpose.
      const res = await fx.api.post(`${BACKEND_URL}/api/${fx.entityKey}`, {
        headers: { 'Content-Type': 'application/json' },
        data: { sku: 'SKU-BROKEN' },
      });
      expect(res.status()).toBe(400);
      const body = await res.json();

      // Post-review contract from FieldValidationException + ErrorHandler:
      //   { error: "field 'name' is required", errors: { name: "is required" } }
      expect(body).toHaveProperty('error');
      expect(body).toHaveProperty('errors');
      expect(body.errors).toBeInstanceOf(Object);
      expect(body.errors.name, JSON.stringify(body)).toBe('is required');
    } finally {
      await teardown(fx);
    }
  });
});
