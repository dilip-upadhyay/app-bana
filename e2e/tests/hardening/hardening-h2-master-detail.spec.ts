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
 *      column matches the parent row id (query `?fkField=parentId`).
 *   2. Filtering by FK returns exactly the rows for that parent — no
 *      cross-row leaks.
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

  test('GET /api/{child}?{fkField}={parentId} returns only that parent\'s rows', async () => {
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

      // Fetch only the children of INV-A.
      const aParentId = idsByInvoice['INV-A'][0];
      const res = await fx.api.get(
        `${BACKEND_URL}/api/${childKey}?invoice_id=${aParentId}`,
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
    } finally {
      await disposeFixture(fx);
    }
  });
});
