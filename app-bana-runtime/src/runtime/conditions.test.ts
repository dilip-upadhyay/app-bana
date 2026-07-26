import { describe, it, expect } from 'vitest';
import { evaluateExpression } from './conditions';

describe('evaluateExpression', () => {
  describe('leaf operators', () => {
    it('equals: string equality', () => {
      expect(evaluateExpression({ field: 'type', op: 'equals', value: 'business' }, { type: 'business' })).toBe(true);
      expect(evaluateExpression({ field: 'type', op: 'equals', value: 'business' }, { type: 'individual' })).toBe(false);
    });
    it('equals: loose numeric equality between string and number', () => {
      expect(evaluateExpression({ field: 'age', op: 'equals', value: 18 }, { age: '18' })).toBe(true);
      expect(evaluateExpression({ field: 'age', op: 'equals', value: '18' }, { age: 18 })).toBe(true);
    });
    it('notEquals', () => {
      expect(evaluateExpression({ field: 't', op: 'notEquals', value: 'a' }, { t: 'b' })).toBe(true);
      expect(evaluateExpression({ field: 't', op: 'notEquals', value: 'a' }, { t: 'a' })).toBe(false);
    });
    it('in / notIn', () => {
      const e = { field: 's', op: 'in' as const, value: ['a', 'b', 'c'] };
      expect(evaluateExpression(e, { s: 'a' })).toBe(true);
      expect(evaluateExpression(e, { s: 'z' })).toBe(false);
      const n = { field: 's', op: 'notIn' as const, value: ['a', 'b'] };
      expect(evaluateExpression(n, { s: 'a' })).toBe(false);
      expect(evaluateExpression(n, { s: 'x' })).toBe(true);
      // non-array value fails closed
      expect(evaluateExpression({ field: 's', op: 'in', value: 'a' }, { s: 'a' })).toBe(false);
    });
    it('gt / lt / gte / lte', () => {
      expect(evaluateExpression({ field: 'n', op: 'gt', value: 10 }, { n: '20' })).toBe(true);
      expect(evaluateExpression({ field: 'n', op: 'gt', value: 10 }, { n: '5' })).toBe(false);
      expect(evaluateExpression({ field: 'n', op: 'lt', value: 10 }, { n: '5' })).toBe(true);
      expect(evaluateExpression({ field: 'n', op: 'gte', value: 10 }, { n: '10' })).toBe(true);
      expect(evaluateExpression({ field: 'n', op: 'lte', value: 10 }, { n: '10' })).toBe(true);
      expect(evaluateExpression({ field: 'n', op: 'gt', value: 10 }, { n: 'abc' })).toBe(false);
    });
    it('contains: case-insensitive substring', () => {
      expect(evaluateExpression({ field: 'x', op: 'contains', value: 'foo' }, { x: 'FooBar' })).toBe(true);
      expect(evaluateExpression({ field: 'x', op: 'contains', value: 'zzz' }, { x: 'FooBar' })).toBe(false);
      expect(evaluateExpression({ field: 'x', op: 'contains', value: 'foo' }, {})).toBe(false);
    });
    it('isEmpty / isNotEmpty', () => {
      expect(evaluateExpression({ field: 'x', op: 'isEmpty' }, { x: '' })).toBe(true);
      expect(evaluateExpression({ field: 'x', op: 'isEmpty' }, { x: '   ' })).toBe(true);
      expect(evaluateExpression({ field: 'x', op: 'isEmpty' }, {})).toBe(true);
      expect(evaluateExpression({ field: 'x', op: 'isEmpty' }, { x: 'hi' })).toBe(false);
      expect(evaluateExpression({ field: 'x', op: 'isNotEmpty' }, { x: 'hi' })).toBe(true);
      expect(evaluateExpression({ field: 'x', op: 'isEmpty' }, { x: [] })).toBe(true);
    });
  });

  describe('combinators', () => {
    it('and', () => {
      const e = {
        and: [
          { field: 'a', op: 'equals' as const, value: 1 },
          { field: 'b', op: 'equals' as const, value: 2 },
        ],
      };
      expect(evaluateExpression(e, { a: 1, b: 2 })).toBe(true);
      expect(evaluateExpression(e, { a: 1, b: 3 })).toBe(false);
      expect(evaluateExpression({ and: [] }, {})).toBe(true); // vacuously true
    });
    it('or', () => {
      const e = {
        or: [
          { field: 'a', op: 'equals' as const, value: 1 },
          { field: 'b', op: 'equals' as const, value: 2 },
        ],
      };
      expect(evaluateExpression(e, { a: 1, b: 99 })).toBe(true);
      expect(evaluateExpression(e, { a: 99, b: 2 })).toBe(true);
      expect(evaluateExpression(e, { a: 99, b: 99 })).toBe(false);
      expect(evaluateExpression({ or: [] }, {})).toBe(false); // vacuously false
    });
    it('not', () => {
      expect(evaluateExpression({ not: { field: 'a', op: 'equals', value: 1 } }, { a: 2 })).toBe(true);
      expect(evaluateExpression({ not: { field: 'a', op: 'equals', value: 1 } }, { a: 1 })).toBe(false);
    });
    it('nested and/or/not', () => {
      const e = {
        and: [
          { field: 'type', op: 'equals' as const, value: 'business' },
          {
            or: [
              { field: 'country', op: 'equals' as const, value: 'US' },
              { not: { field: 'taxId', op: 'isEmpty' as const } },
            ],
          },
        ],
      };
      expect(evaluateExpression(e, { type: 'business', country: 'US', taxId: '' })).toBe(true);
      expect(evaluateExpression(e, { type: 'business', country: 'UK', taxId: '123' })).toBe(true);
      expect(evaluateExpression(e, { type: 'business', country: 'UK', taxId: '' })).toBe(false);
      expect(evaluateExpression(e, { type: 'individual', country: 'US', taxId: '123' })).toBe(false);
    });
  });

  describe('edge cases', () => {
    it('undefined expression = visible/truthy', () => {
      expect(evaluateExpression(undefined, {})).toBe(true);
    });
    it('missing field treated as undefined', () => {
      expect(evaluateExpression({ field: 'ghost', op: 'equals', value: 'x' }, {})).toBe(false);
      expect(evaluateExpression({ field: 'ghost', op: 'isEmpty' }, {})).toBe(true);
    });
  });
});
