/**
 * hardening-h3-list-views.spec.ts
 *
 * H3 hardening — `StudioTableLive` now honours `props.filters` and
 * saved views by passing extra query params into `useEntityRows`.
 * The wire contract this depends on:
 *
 *   1. `/api/{entity}?<field>=<value>` narrows the result set to
 *      matching rows (the `filters` prop path in useEntityRows).
 *   2. Advanced filter operators (`<field>:in=a,b`) work at the wire
 *      layer — the SavedViewsBar can materialize a view as a set of
 *      simple query params without having to serialize opaque filter
 *      objects.
 *   3. Combining filters is AND, so a saved view with two clauses
 *      returns only rows matching both.
 */
import { expect, test } from '@playwright/test';
import {
  BACKEND_URL,
  healthCheck,
  newHardeningFixture,
  disposeFixture,
  saveSchema,
} from './fixtures';

test.describe('H3 — list-views filter wiring on /api/{entity}', () => {
  test.beforeAll(async () => {
    if (!(await healthCheck())) {
      test.skip(true, `Backend unreachable at ${BACKEND_URL} — start the stack first`);
    }
  });

  test('field filters (AND) narrow the result set exactly', async () => {
    const fx = await newHardeningFixture('h3-filters');
    if (!fx) { test.skip(true, 'Registration rate-limited'); return; }

    try {
      const key = await saveSchema(fx, 'Task', [
        { name: 'id', type: 'integer', primaryKey: true, autoIncrement: true },
        { name: 'status', type: 'text' },
        { name: 'priority', type: 'text' },
      ]);

      const rows = [
        { status: 'open', priority: 'high' },
        { status: 'open', priority: 'low' },
        { status: 'done', priority: 'high' },
        { status: 'done', priority: 'low' },
        { status: 'open', priority: 'high' },
      ];
      for (const row of rows) {
        await fx.api.post(`${BACKEND_URL}/api/${key}`, {
          headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${fx.token}` },
          data: row,
        });
      }

      // Filter #1: status=open — 3 rows.
      const openRes = await fx.api.get(
        `${BACKEND_URL}/api/${key}?status=open&limit=50`,
        { headers: { Authorization: `Bearer ${fx.token}` } },
      );
      const openBody = await openRes.json();
      const openRows = Array.isArray(openBody) ? openBody : (openBody.rows ?? []);
      expect(openRows.length).toBe(3);

      // Filter #2: status=open AND priority=high — 2 rows (the AND that
      // a two-clause SavedView expands to).
      const combinedRes = await fx.api.get(
        `${BACKEND_URL}/api/${key}?status=open&priority=high&limit=50`,
        { headers: { Authorization: `Bearer ${fx.token}` } },
      );
      const combinedBody = await combinedRes.json();
      const combinedRows = Array.isArray(combinedBody) ? combinedBody : (combinedBody.rows ?? []);
      expect(combinedRows.length,
        'multi-field filter must AND — saved views depend on this',
      ).toBe(2);
      for (const row of combinedRows as Array<Record<string, unknown>>) {
        expect(String(row.status ?? row.STATUS)).toBe('open');
        expect(String(row.priority ?? row.PRIORITY)).toBe('high');
      }
    } finally {
      await disposeFixture(fx);
    }
  });
});
