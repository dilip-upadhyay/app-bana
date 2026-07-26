/**
 * conditions.ts — Phase B2. Pure evaluator for the declarative Expression
 * grammar defined in `@appbana/shared` (metadata.ts). Handles the visibility,
 * requiredness, and disabled state of a field given the current form values.
 *
 * Design notes:
 *   - No `eval`, no `Function` — the grammar is finite and safely walkable.
 *   - Missing operands (e.g. field not in values) are treated as `undefined`.
 *     `equals undefined`, `isEmpty`, etc. follow the obvious semantics.
 *   - `in` / `notIn` accept an array; anything else is treated as no-match.
 *   - Numeric comparisons coerce both sides via `Number(...)`. Non-numeric
 *     inputs yield NaN → false comparisons (safe fail-closed).
 */
import type { Expression } from '@appbana/shared';

export type FormValues = Record<string, unknown>;

function isEmptyLike(v: unknown): boolean {
  if (v === null || v === undefined) return true;
  if (typeof v === 'string') return v.trim() === '';
  if (Array.isArray(v)) return v.length === 0;
  return false;
}

function toNumber(v: unknown): number {
  if (typeof v === 'number') return v;
  if (typeof v === 'string' && v.trim() !== '') return Number(v);
  return Number.NaN;
}

function eq(a: unknown, b: unknown): boolean {
  if (a === b) return true;
  // Loose numeric equality — form values arrive as strings via FormData.
  if (typeof a === 'string' && typeof b === 'number') return a === String(b);
  if (typeof a === 'number' && typeof b === 'string') return String(a) === b;
  // Loose boolean equality — form checkbox comes as 'on' | undefined.
  if (typeof a === 'boolean' || typeof b === 'boolean') {
    return Boolean(a) === Boolean(b);
  }
  return false;
}

export function evaluateExpression(expr: Expression | undefined, values: FormValues): boolean {
  if (!expr) return true;

  if ('and' in expr) {
    return expr.and.every((e) => evaluateExpression(e, values));
  }
  if ('or' in expr) {
    return expr.or.some((e) => evaluateExpression(e, values));
  }
  if ('not' in expr) {
    return !evaluateExpression(expr.not, values);
  }

  const actual = values[expr.field];
  switch (expr.op) {
    case 'equals':    return eq(actual, expr.value);
    case 'notEquals': return !eq(actual, expr.value);
    case 'in':
      return Array.isArray(expr.value) && expr.value.some((v) => eq(actual, v));
    case 'notIn':
      return Array.isArray(expr.value) && !expr.value.some((v) => eq(actual, v));
    case 'gt':  return toNumber(actual) >  toNumber(expr.value);
    case 'lt':  return toNumber(actual) <  toNumber(expr.value);
    case 'gte': return toNumber(actual) >= toNumber(expr.value);
    case 'lte': return toNumber(actual) <= toNumber(expr.value);
    case 'contains': {
      const s = typeof actual === 'string' ? actual : '';
      const needle = typeof expr.value === 'string' ? expr.value : String(expr.value ?? '');
      return s.toLowerCase().includes(needle.toLowerCase());
    }
    case 'isEmpty':    return isEmptyLike(actual);
    case 'isNotEmpty': return !isEmptyLike(actual);
    default:
      // Exhaustiveness: unreachable when metadata is well-formed. Fail
      // closed (hide by default) so a malformed expression can't leak
      // fields.
      return false;
  }
}

/**
 * Snapshot a form's current values into a plain object. Used by callers
 * that need to re-evaluate conditions on every input change without
 * threading each control's value through React state.
 */
export function readFormValues(form: HTMLFormElement): FormValues {
  const fd = new FormData(form);
  const out: FormValues = {};
  for (const [k, v] of fd.entries()) {
    // FormData returns FormDataEntryValue (string | File). We coerce File
    // to filename for expression purposes — file-vs-empty is what
    // typical conditions care about.
    out[k] = v instanceof File ? v.name : v;
  }
  return out;
}
