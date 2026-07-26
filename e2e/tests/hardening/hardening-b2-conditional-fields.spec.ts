/**
 * hardening-b2-conditional-fields.spec.ts
 *
 * Complex-UI Epic B2 — a schema's field-level `conditions{}` block
 * (`showWhen` / `requiredWhen` / `disabledWhen`) must survive a
 * schema-save → schema-fetch round-trip. If the backend strips the
 * conditions on the way through, ConditionalField will render every
 * field unconditionally at runtime and the H5 hidden-field validation
 * strip has nothing to hide.
 *
 * This spec locks in the wire contract for the metadata pass-through.
 */
import { expect, test } from '@playwright/test';
import {
  BACKEND_URL,
  healthCheck,
  newHardeningFixture,
  disposeFixture,
} from './fixtures';

test.describe('B2 — conditional-field metadata round-trip', () => {
  test.beforeAll(async () => {
    if (!(await healthCheck())) {
      test.skip(true, `Backend unreachable at ${BACKEND_URL} — start the stack first`);
    }
  });

  test('conditions{} survive save → fetch on /schema', async () => {
    const fx = await newHardeningFixture('b2-cond');
    if (!fx) { test.skip(true, 'Registration rate-limited'); return; }

    try {
      const entity = 'Signup';
      const key = `${fx.tenantId}_${fx.appId}_${entity}`;
      const schema = {
        name: entity,
        appId: fx.appId,
        tenantId: fx.tenantId,
        fields: [
          { name: 'id', type: 'integer', primaryKey: true, autoIncrement: true },
          { name: 'plan', type: 'text' },
          {
            name: 'company_name',
            type: 'text',
            conditions: {
              // Only shown when the user picked the business plan.
              showWhen: { field: 'plan', equals: 'business' },
              // And required only in that case.
              requiredWhen: { field: 'plan', equals: 'business' },
              disabledWhen: { field: 'plan', equals: 'legacy' },
            },
          },
        ],
      };

      const save = await fx.api.post(`${BACKEND_URL}/schema`, {
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${fx.token}` },
        data: schema,
      });
      expect(save.status(), await save.text()).toBeLessThan(300);

      const fetched = await fx.api.get(`${BACKEND_URL}/schema/${key}`, {
        headers: { Authorization: `Bearer ${fx.token}` },
      });
      expect(fetched.status()).toBe(200);
      const body = await fetched.json();
      const fields: Array<Record<string, unknown>> = body.fields ?? body.schema?.fields ?? [];
      const company = fields.find((f) => (f.name as string).toLowerCase() === 'company_name');
      expect(company, 'company_name field must round-trip').toBeDefined();

      const conditions = company!.conditions as Record<string, unknown> | undefined;
      expect(conditions, 'conditions{} must survive the round-trip').toBeDefined();
      expect(conditions).toHaveProperty('showWhen');
      expect(conditions).toHaveProperty('requiredWhen');
      expect(conditions).toHaveProperty('disabledWhen');
    } finally {
      await disposeFixture(fx);
    }
  });
});
