/**
 * hardening-h4-fk-constraints.spec.ts
 *
 * H4 hardening — `EntityField.referenceEntity` + `onDelete` are now
 * real FOREIGN KEY constraints at the database level. Before H4 the
 * onDelete metadata was pure documentation — a DELETE against the
 * parent row silently orphaned children.
 *
 * We can't peek into DatabaseMetaData over the wire, so this spec
 * proves the behaviour end-to-end via inserts + deletes:
 *
 *   - `onDelete: "cascade"` — deleting parent removes children
 *   - `onDelete: "restrict"` (or unset) — deleting parent with children
 *     returns a 500 / 4xx from the DB engine, and the child rows are
 *     still there afterwards
 */
import { expect, test } from '@playwright/test';
import {
  BACKEND_URL,
  healthCheck,
  newHardeningFixture,
  disposeFixture,
  saveSchema,
} from './fixtures';

test.describe('H4 — real FK constraints from SchemaManager', () => {
  test.beforeAll(async () => {
    if (!(await healthCheck())) {
      test.skip(true, `Backend unreachable at ${BACKEND_URL} — start the stack first`);
    }
  });

  test('onDelete=cascade deletes children with the parent', async () => {
    const fx = await newHardeningFixture('h4-cascade');
    if (!fx) { test.skip(true, 'Registration rate-limited'); return; }

    try {
      const parentKey = await saveSchema(fx, 'Customer', [
        { name: 'id', type: 'integer', primaryKey: true, autoIncrement: true },
        { name: 'name', type: 'text' },
      ]);
      const childKey = await saveSchema(fx, 'Order', [
        { name: 'id', type: 'integer', primaryKey: true, autoIncrement: true },
        { name: 'sku', type: 'text' },
        { name: 'customer_id', type: 'reference', referenceEntity: 'Customer', onDelete: 'cascade' },
      ]);

      const parentIns = await fx.api.post(`${BACKEND_URL}/api/${parentKey}`, {
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${fx.token}` },
        data: { name: 'Acme' },
      });
      expect(parentIns.status(), await parentIns.text()).toBeLessThan(300);
      const parent = await parentIns.json();
      const parentId = String(parent.id ?? parent.record?.id ?? '');
      expect(parentId).not.toBe('');

      const childIns = await fx.api.post(`${BACKEND_URL}/api/${childKey}`, {
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${fx.token}` },
        data: { sku: 'SKU-1', customer_id: Number(parentId) },
      });
      expect(childIns.status(), await childIns.text()).toBeLessThan(300);

      // Delete parent — cascade must remove the child too.
      const del = await fx.api.delete(`${BACKEND_URL}/api/${parentKey}/${parentId}`, {
        headers: { Authorization: `Bearer ${fx.token}` },
      });
      expect(del.status(), await del.text()).toBeLessThan(300);

      const childListRes = await fx.api.get(
        `${BACKEND_URL}/api/${childKey}?customer_id=${parentId}`,
        { headers: { Authorization: `Bearer ${fx.token}` } },
      );
      expect(childListRes.status()).toBe(200);
      const body = await childListRes.json();
      const rows = Array.isArray(body) ? body : (body.rows ?? []);
      expect(rows.length, 'cascade must have removed the child rows').toBe(0);
    } finally {
      await disposeFixture(fx);
    }
  });

  test('onDelete=restrict blocks parent delete when children exist', async () => {
    const fx = await newHardeningFixture('h4-restrict');
    if (!fx) { test.skip(true, 'Registration rate-limited'); return; }

    try {
      const parentKey = await saveSchema(fx, 'Author', [
        { name: 'id', type: 'integer', primaryKey: true, autoIncrement: true },
        { name: 'name', type: 'text' },
      ]);
      const childKey = await saveSchema(fx, 'Book', [
        { name: 'id', type: 'integer', primaryKey: true, autoIncrement: true },
        { name: 'title', type: 'text' },
        { name: 'author_id', type: 'reference', referenceEntity: 'Author', onDelete: 'restrict' },
      ]);

      const parentIns = await fx.api.post(`${BACKEND_URL}/api/${parentKey}`, {
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${fx.token}` },
        data: { name: 'Rowling' },
      });
      const parent = await parentIns.json();
      const parentId = String(parent.id ?? parent.record?.id ?? '');

      const childIns = await fx.api.post(`${BACKEND_URL}/api/${childKey}`, {
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${fx.token}` },
        data: { title: 'Ink & Ashes', author_id: Number(parentId) },
      });
      expect(childIns.status(), await childIns.text()).toBeLessThan(300);

      // Delete parent — restrict must reject.
      const del = await fx.api.delete(`${BACKEND_URL}/api/${parentKey}/${parentId}`, {
        headers: { Authorization: `Bearer ${fx.token}` },
      });
      expect(del.status(), 'RESTRICT must block parent delete').toBeGreaterThanOrEqual(400);

      // Child row must still be there.
      const childCheckRes = await fx.api.get(
        `${BACKEND_URL}/api/${childKey}?author_id=${parentId}`,
        { headers: { Authorization: `Bearer ${fx.token}` } },
      );
      const body = await childCheckRes.json();
      const rows = Array.isArray(body) ? body : (body.rows ?? []);
      expect(rows.length, 'child row must survive a blocked parent delete').toBe(1);
    } finally {
      await disposeFixture(fx);
    }
  });
});
