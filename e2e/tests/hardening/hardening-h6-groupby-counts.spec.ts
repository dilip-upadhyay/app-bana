/**
 * hardening-h6-groupby-counts.spec.ts
 *
 * H6 hardening — `/api/{entity}?groupBy=col` now returns a top-level
 * `groupCounts` field with TRUE counts per bucket across the entire
 * filtered dataset (not just the current page). Before H6 the response
 * only carried a per-page `groups` map, so counts >= page size were
 * silently wrong.
 *
 * Also verifies the SQL-injection guard: an unknown / malicious column
 * name yields an empty `groupCounts` map instead of interpolating raw
 * text into SQL.
 */
import { expect, test } from '@playwright/test';
import {
  BACKEND_URL,
  healthCheck,
  newHardeningFixture,
  disposeFixture,
  saveSchema,
} from './fixtures';

test.describe('H6 — SQL GROUP BY counts across full dataset', () => {
  test.beforeAll(async () => {
    if (!(await healthCheck())) {
      test.skip(true, `Backend unreachable at ${BACKEND_URL} — start the stack first`);
    }
  });

  test('groupCounts totals span the whole dataset even when it exceeds the page size', async () => {
    const fx = await newHardeningFixture('h6-counts');
    if (!fx) { test.skip(true, 'Registration rate-limited'); return; }

    try {
      const key = await saveSchema(fx, 'Ticket', [
        { name: 'id', type: 'integer', primaryKey: true, autoIncrement: true },
        { name: 'status', type: 'text' },
      ]);

      // Insert 100 rows (60 active, 30 pending, 10 closed) — well past the
      // default page size of 50.
      const inserts: Promise<unknown>[] = [];
      const push = (status: string, count: number) => {
        for (let i = 0; i < count; i++) {
          inserts.push(fx.api.post(`${BACKEND_URL}/api/${key}`, {
            headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${fx.token}` },
            data: { status },
          }));
        }
      };
      push('active', 60);
      push('pending', 30);
      push('closed', 10);
      await Promise.all(inserts);

      const listRes = await fx.api.get(
        `${BACKEND_URL}/api/${key}?groupBy=status&limit=50`,
        { headers: { Authorization: `Bearer ${fx.token}` } },
      );
      expect(listRes.status()).toBe(200);
      const body = await listRes.json();

      // groupCounts is the H6 contract — an object of { bucket: totalCount }.
      expect(body).toHaveProperty('groupCounts');
      const counts = body.groupCounts as Record<string, number>;
      expect(counts.active).toBe(60);
      expect(counts.pending).toBe(30);
      expect(counts.closed).toBe(10);

      // Existing per-page `groups` field is still present (backwards compat).
      expect(body).toHaveProperty('groups');
    } finally {
      await disposeFixture(fx);
    }
  });

  test('unknown / malicious groupBy column returns empty groupCounts, not SQL error', async () => {
    const fx = await newHardeningFixture('h6-inject');
    if (!fx) { test.skip(true, 'Registration rate-limited'); return; }

    try {
      const key = await saveSchema(fx, 'Ticket', [
        { name: 'id', type: 'integer', primaryKey: true, autoIncrement: true },
        { name: 'status', type: 'text' },
      ]);
      await fx.api.post(`${BACKEND_URL}/api/${key}`, {
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${fx.token}` },
        data: { status: 'active' },
      });

      const malicious = encodeURIComponent('status; DROP TABLE ticket; --');
      const res = await fx.api.get(
        `${BACKEND_URL}/api/${key}?groupBy=${malicious}&limit=10`,
        { headers: { Authorization: `Bearer ${fx.token}` } },
      );
      expect(res.status(), 'must not blow up — injection is guarded').toBe(200);
      const body = await res.json();
      const counts = (body.groupCounts as Record<string, number> | undefined) ?? {};
      expect(Object.keys(counts).length, 'malicious column must produce empty counts').toBe(0);
    } finally {
      await disposeFixture(fx);
    }
  });
});
