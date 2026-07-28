/**
 * hardening-h2-master-detail.spec.ts
 *
 * H2 hardening — the runtime's `Renderer.tsx` case for `child_table`
 * auto-injects `parentId` from the enclosing `RecordContextProvider`,
 * so a master-detail page can drop a `child_table` node inside a
 * detail page and the wiring "just works".
 *
 * This wire test proves the API surface a detail page depends on:
 *   1. A page with a `child_table` node lists all children whose FK
 *      column matches the parent row id, scoped via `?filter={fkField}:={parentId}`
 *      — the `filter=` clause is the only door the handler reads for a
 *      field-level scope (see `entity-query.ts` / `ChildTable.tsx`).
 *   2. Filtering by FK returns exactly the rows for that parent — no
 *      cross-row leaks.
 *   3. C3.10 — a bare `?{fkField}={parentId}` param, which `ChildTable.tsx`
 *      sent before this fix, is outside the handler's query-param allowlist
 *      and is silently ignored. `ApprovalRoutesSecurityTest` pins the same
 *      contract on the Java side (`testBareFieldParamIsIgnoredButFilterParamScopesTheList`);
 *      this assertion exists so the two suites agree instead of silently
 *      asserting opposite behaviour of the same endpoint.
 */
import { expect, test } from '@playwright/test';
import {
  BACKEND_URL,
  healthCheck,
  newHardeningFixture,
  disposeFixture,
  saveSchema,
} from './fixtures';

test.describe('H2 — master-detail child rows filter by parent FK', () => {
  test.beforeAll(async () => {
    if (!(await healthCheck())) {
      test.skip(true, `Backend unreachable at ${BACKEND_URL} — start the stack first`);
    }
  });

  test('GET /api/{child}?filter={fkField}:={parentId} returns only that parent\'s rows', async () => {
    const fx = await newHardeningFixture('h2-md');
    if (!fx) { test.skip(true, 'Registration rate-limited'); return; }

    try {
      const parentKey = await saveSchema(fx, 'Invoice', [
        { name: 'id', type: 'integer', primaryKey: true, autoIncrement: true },
        { name: 'number', type: 'text' },
      ]);
      const childKey = await saveSchema(fx, 'LineItem', [
        { name: 'id', type: 'integer', primaryKey: true, autoIncrement: true },
        { name: 'description', type: 'text' },
        { name: 'invoice_id', type: 'reference', referenceEntity: 'Invoice', onDelete: 'cascade' },
      ]);

      // Two invoices, two line-items each.
      const idsByInvoice: Record<string, number[]> = {};
      for (const num of ['INV-A', 'INV-B']) {
        const p = await fx.api.post(`${BACKEND_URL}/api/${parentKey}`, {
          headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${fx.token}` },
          data: { number: num },
        });
        const parent = await p.json();
        const parentId = Number(parent.id ?? parent.record?.id);
        idsByInvoice[num] = [parentId];

        for (const desc of [`${num}-line1`, `${num}-line2`]) {
          const c = await fx.api.post(`${BACKEND_URL}/api/${childKey}`, {
            headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${fx.token}` },
            data: { description: desc, invoice_id: parentId },
          });
          expect(c.status(), await c.text()).toBeLessThan(300);
        }
      }

      // Fetch only the children of INV-A via the `filter=` door.
      const aParentId = idsByInvoice['INV-A'][0];
      const res = await fx.api.get(
        `${BACKEND_URL}/api/${childKey}?filter=invoice_id:=${aParentId}`,
        { headers: { Authorization: `Bearer ${fx.token}` } },
      );
      expect(res.status()).toBe(200);
      const body = await res.json();
      const rows = Array.isArray(body) ? body : (body.rows ?? []);
      expect(rows.length, 'child_table must see exactly this parent\'s 2 rows').toBe(2);
      for (const row of rows as Array<Record<string, unknown>>) {
        const desc = String(row.description ?? row.DESCRIPTION ?? '');
        expect(desc.startsWith('INV-A-'), `unexpected row leaked in: ${desc}`).toBe(true);
      }

      // A bare field param (the pre-fix `ChildTable.tsx` request shape) is
      // outside the allowlist and must be ignored, not silently mis-scoped —
      // it should come back as the unfiltered first page (all 4 rows here).
      const bare = await fx.api.get(
        `${BACKEND_URL}/api/${childKey}?invoice_id=${aParentId}`,
        { headers: { Authorization: `Bearer ${fx.token}` } },
      );
      expect(bare.status()).toBe(200);
      const bareBody = await bare.json();
      const bareRows = Array.isArray(bareBody) ? bareBody : (bareBody.rows ?? []);
      expect(bareRows.length, 'a bare field param is not read by the handler, so nothing is scoped').toBe(4);
    } finally {
      await disposeFixture(fx);
    }
  });
});

