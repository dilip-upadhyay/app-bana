/**
 * entity-query.ts — Task C3.9.
 *
 * Translates a `{ field: value }` filter map into the query parameters that
 * `GET /api/{entity}` actually reads.
 *
 * This exists because of a wire-level defect found in review. The runtime used
 * to flatten filters into bare params — `?status=open`, `?submitted_by=u1` —
 * and the backend handler reads a fixed allowlist (`limit`, `offset`, `q`,
 * `fields`, `sort`, `filter`, `count`, `groupBy`, `_approvalStatus`) and
 * nothing else. Every bare field param was therefore dropped in transit and the
 * response came back 200 with an *unfiltered* body. Nothing failed; the list
 * was just wrong, and silently so.
 *
 * The concrete symptom was the "Needs rework" system view, which is scoped by
 * `submitted_by` and so showed every maker's rejected records to every maker.
 * The same defect affected the H3 FilterBar and every saved view built on bare
 * field filters, so the fix belongs here rather than in `approval-views.ts`.
 *
 * The working door is `filter=name:value,name:value`, parsed by
 * `EntityCrudService.parseFilters` and validated against the schema fields.
 *
 * Known limitation, inherited from that parser: it splits on `,` before it
 * splits on `:`, so a filter *value* containing a comma cannot be expressed.
 * Such values are refused rather than sent, because half-applying a filter is
 * the failure mode this module was written to remove — see `rejected`.
 */

/**
 * Params the backend reads directly. They must stay bare and must not be
 * folded into `filter=`.
 */
export const RESERVED_QUERY_PARAMS: readonly string[] = [
  '_approvalStatus',
  'q',
  'fields',
  'sort',
  'count',
  'groupBy',
  'limit',
  'offset',
];

export interface EntityQuery {
  /** Query params ready to hand to `fetchEntityRows`. */
  readonly params: Record<string, string | number>;
  /** Field names dropped because their value is not expressible in `filter=`. */
  readonly rejected: readonly string[];
}

/**
 * Marks a filter value that must match exactly.
 *
 * String filters default to a case-insensitive substring match, which is right
 * for a search box and wrong for anything identity-shaped: scoping a list to
 * `submitted_by = bob` under a substring match also returns everything
 * submitted by `bobby`. Written on the wire as a leading `=` on the value and
 * unwrapped by `EntityCrudService.parseFilters`.
 */
export interface ExactFilterValue {
  readonly __exact: string;
}

export function exact(value: string): ExactFilterValue {
  return { __exact: value };
}

function isExact(value: unknown): value is ExactFilterValue {
  return typeof value === 'object' && value !== null && typeof (value as ExactFilterValue).__exact === 'string';
}

/**
 * Written on the wire as "min..max" (double-dot separator), parsed by
 * `EntityCrudService.parseRange` and only accepted for orderable column
 * kinds (integer/bigint/decimal/timestamp/reference). Either bound may be
 * omitted for an open-ended range; both empty means "no filter" and is
 * dropped like any other empty value.
 */
export interface RangeFilterValue {
  readonly __range: { readonly min?: unknown; readonly max?: unknown };
}

export function range(min: unknown, max: unknown): RangeFilterValue {
  return { __range: { min, max } };
}

function isRange(value: unknown): value is RangeFilterValue {
  return typeof value === 'object' && value !== null && '__range' in (value as Record<string, unknown>);
}

function stringify(value: unknown): string {
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  return JSON.stringify(value);
}

/** Outcome of turning one `[key, value]` filter entry into a `filter=` clause. */
type ClauseResult =
  | { readonly kind: 'skip' }
  | { readonly kind: 'clause'; readonly text: string }
  | { readonly kind: 'rejected' };

function rangeClauseResult(key: string, raw: RangeFilterValue): ClauseResult {
  const { min, max } = raw.__range;
  if (min == null && max == null) return { kind: 'skip' };
  const lo = min == null ? '' : stringify(min);
  const hi = max == null ? '' : stringify(max);
  // A comma in either bound would be read as a clause separator, same hazard
  // as the plain-value case below — refuse rather than truncate silently.
  if (lo.includes(',') || hi.includes(',')) return { kind: 'rejected' };
  return { kind: 'clause', text: `${key}:${lo}..${hi}` };
}

function plainClauseResult(key: string, raw: unknown): ClauseResult {
  const value = isExact(raw) ? raw.__exact : stringify(raw);
  if (value === '') return { kind: 'skip' };
  // A comma would be read as a clause separator and silently truncate the
  // value, so the filter is dropped loudly instead of applied wrongly.
  if (value.includes(',')) return { kind: 'rejected' };
  return { kind: 'clause', text: `${key}:${isExact(raw) ? '=' : ''}${value}` };
}

/**
 * Handles the two "pass through untouched" cases: an explicit `filter` entry
 * (appended to rather than replacing whatever else builds the clause list)
 * and the backend's reserved bare params (`sort`, `limit`, `q`, ...). Returns
 * whether it consumed the entry, so the caller knows to move on.
 */
function applyReservedParam(
  key: string,
  raw: unknown,
  params: Record<string, string | number>,
  clauses: string[],
): boolean {
  if (key === 'filter') {
    clauses.push(stringify(raw));
    return true;
  }
  if (RESERVED_QUERY_PARAMS.includes(key)) {
    params[key] = typeof raw === 'number' ? raw : stringify(raw);
    return true;
  }
  return false;
}

/**
 * Build the query for a list fetch.
 *
 * Reserved params pass through untouched; everything else becomes one
 * `filter=` string. An explicit `filter` entry is preserved and appended to.
 */
export function toEntityQueryParams(filterValues: Record<string, unknown>): EntityQuery {
  const params: Record<string, string | number> = {};
  const clauses: string[] = [];
  const rejected: string[] = [];

  for (const [key, raw] of Object.entries(filterValues)) {
    if (raw == null || raw === '') continue;
    if (applyReservedParam(key, raw, params, clauses)) continue;

    const result = isRange(raw) ? rangeClauseResult(key, raw) : plainClauseResult(key, raw);
    if (result.kind === 'rejected') {
      rejected.push(key);
    } else if (result.kind === 'clause') {
      clauses.push(result.text);
    }
  }

  if (clauses.length > 0) params.filter = clauses.join(',');

  return { params, rejected };
}
