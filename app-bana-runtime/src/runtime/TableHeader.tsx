/**
 * TableHeader.tsx — Sprint 3 task 3.12; extended by the column-filter/sort/
 * scale hardening pass.
 *
 * Header row for entity tables. Still kept dumb: the caller owns all state
 * (sort direction, filter values) and this component only renders controls
 * and reports intent via callbacks — same philosophy as `FilterBar.tsx`.
 *
 * All new props are optional so a caller that only ever passed `columns` +
 * `labelFor` (the original contract) keeps rendering exactly as before.
 * When `onFilterChange` is supplied, a second `<tr>` of per-column filter
 * controls renders below the labels — the "filter on every column" feature.
 * When `onSortToggle` is supplied, column labels become click targets that
 * cycle asc → desc → none, driving the server-side `sort=` param (see
 * `entity-query.ts` / `useEntityRows.ts`) — filtering/sorting at the scale
 * this is built for (potentially millions of rows) must stay server-side,
 * never a client-side sort/filter of an in-memory array.
 */
import { humanizeHeader } from './cell-formatters';
import { range } from './entity-query';

export interface TableColumnMeta {
  readonly name: string;
  readonly label?: string;
}

export interface ColumnSort {
  readonly field: string;
  readonly direction: 'asc' | 'desc';
}

/** One reference-field dropdown option — id/label pair sourced from the FK label cache. */
export interface ReferenceOption {
  readonly value: string;
  readonly label: string;
}

export interface TableHeaderProps {
  readonly columns: readonly string[];
  readonly labelFor: (name: string) => string | undefined;
  /**
   * Column names that get a sort affordance + filter control. Defaults to
   * all `columns`. Used to exclude columns with their own dedicated
   * filtering UI (e.g. `approval_status`, already served by SavedViewsBar's
   * system views) from the generic mechanism, to avoid two competing filter
   * paths for the same column.
   */
  readonly filterableColumns?: readonly string[];
  /** Field type per column (from schema metadata) — selects which control renders. */
  readonly typeFor?: (name: string) => string | undefined;
  /** For `type === 'reference'` columns — options sourced from the already-fetched FK label cache (no extra request). */
  readonly referenceOptionsFor?: (name: string) => readonly ReferenceOption[] | undefined;
  readonly sort?: ColumnSort | null;
  readonly onSortToggle?: (field: string) => void;
  readonly filterValues?: Readonly<Record<string, unknown>>;
  readonly onFilterChange?: (field: string, value: unknown) => void;
}

export function TableHeader({
  columns,
  labelFor,
  filterableColumns,
  typeFor,
  referenceOptionsFor,
  sort = null,
  onSortToggle,
  filterValues = {},
  onFilterChange,
}: Readonly<TableHeaderProps>) {
  const filterable = new Set(filterableColumns ?? columns);
  const showFilterRow = Boolean(onFilterChange);

  return (
    <thead>
      <tr>
        {columns.map((name) => {
          const label = humanizeHeader(labelFor(name) ?? name);
          const sortable = Boolean(onSortToggle) && filterable.has(name);
          const active = sort?.field === name ? sort.direction : null;
          if (!sortable) {
            return (
              <th key={name} className="appbana-table-th" scope="col">
                {label}
              </th>
            );
          }
          return (
            <th key={name} className="appbana-table-th" scope="col">
              <button
                type="button"
                className="flex items-center gap-1 hover:text-slate-900"
                onClick={() => onSortToggle?.(name)}
                aria-label={sortAriaLabel(label, active)}
                data-appbana-sort={name}
                data-appbana-sort-direction={active ?? 'none'}
              >
                {label}
                <span aria-hidden="true" className={active ? 'text-slate-700' : 'text-slate-300'}>
                  {sortGlyph(active)}
                </span>
              </button>
            </th>
          );
        })}
        {/* Row-actions column header — visually blank but accessible. */}
        <th className="appbana-table-th w-10" scope="col">
          <span className="sr-only">Actions</span>
        </th>
      </tr>
      {showFilterRow && (
        <tr className="appbana-table-filter-row" data-appbana-filter-row>
          {columns.map((name) => (
            <th key={`filter-${name}`} className="appbana-table-th-filter" scope="col">
              {filterable.has(name) && onFilterChange ? (
                <ColumnFilterControl
                  name={name}
                  label={humanizeHeader(labelFor(name) ?? name)}
                  type={typeFor?.(name)}
                  referenceOptions={referenceOptionsFor?.(name)}
                  value={filterValues[name]}
                  onChange={(next) => onFilterChange(name, next)}
                />
              ) : null}
            </th>
          ))}
          <th className="appbana-table-th-filter w-10" />
        </tr>
      )}
    </thead>
  );
}

const inputCls =
  'w-full min-w-0 px-1.5 py-1 rounded border border-slate-200 bg-white text-slate-900 text-xs '
  + 'focus:outline-none focus:ring-2 focus:ring-indigo-500 placeholder:text-slate-400';

/** Accessible name for a sortable column header button. */
function sortAriaLabel(label: string, active: 'asc' | 'desc' | null): string {
  if (active === 'asc') return `Sort by ${label}, currently ascending`;
  if (active === 'desc') return `Sort by ${label}, currently descending`;
  return `Sort by ${label}`;
}

/** Decorative sort-direction glyph for a column header. */
function sortGlyph(active: 'asc' | 'desc' | null): string {
  if (active === 'asc') return '▲';
  if (active === 'desc') return '▼';
  return '⇅';
}

interface ColumnFilterControlProps {
  readonly name: string;
  readonly label: string;
  readonly type: string | undefined;
  readonly referenceOptions: readonly ReferenceOption[] | undefined;
  readonly value: unknown;
  readonly onChange: (next: unknown) => void;
}

/** Reads the `{min, max}` bounds out of a `RangeFilterValue`, or undefined for neither. */
function rangeBounds(value: unknown): { min?: unknown; max?: unknown } | undefined {
  if (value && typeof value === 'object' && '__range' in (value as Record<string, unknown>)) {
    return (value as { __range: { min?: unknown; max?: unknown } }).__range;
  }
  return undefined;
}

/** Stringifies a range bound for display in a text/number/date input, without risking "[object Object]". */
function boundToDisplayString(bound: unknown): string {
  if (bound == null) return '';
  if (typeof bound === 'string' || typeof bound === 'number') return String(bound);
  return '';
}

/** Renders the type-appropriate filter control for one column. */
function ColumnFilterControl({ name, label, type, referenceOptions, value, onChange }: Readonly<ColumnFilterControlProps>) {
  if (type === 'boolean') {
    return (
      <select
        className={inputCls}
        aria-label={`Filter ${label}`}
        data-appbana-column-filter={name}
        value={typeof value === 'string' ? value : ''}
        onChange={(e) => onChange(e.target.value === '' ? undefined : e.target.value)}
      >
        <option value="">Any</option>
        <option value="true">Yes</option>
        <option value="false">No</option>
      </select>
    );
  }

  if (type === 'reference') {
    return (
      <select
        className={inputCls}
        aria-label={`Filter ${label}`}
        data-appbana-column-filter={name}
        value={typeof value === 'string' ? value : ''}
        onChange={(e) => onChange(e.target.value === '' ? undefined : e.target.value)}
      >
        <option value="">Any</option>
        {(referenceOptions ?? []).map((opt) => (
          <option key={opt.value} value={opt.value}>{opt.label}</option>
        ))}
      </select>
    );
  }

  if (type === 'number' || type === 'decimal' || type === 'int' || type === 'integer' || type === 'long') {
    // Read straight from the `value` prop rather than local state — this
    // control gets unmounted/remounted whenever the parent's loading skeleton
    // swaps back in (see StudioTableLive.tsx's `{loading && <TableSkeleton/>}`),
    // which happens on every filter change since it triggers a refetch. Local
    // draft state was found live-testing to silently reset to '' on that
    // remount and then feed its stale '' back into the *other* bound's onChange
    // the next time the user typed, quietly dropping a just-applied filter.
    const bounds = rangeBounds(value);
    const minVal = boundToDisplayString(bounds?.min);
    const maxVal = boundToDisplayString(bounds?.max);
    return (
      <div className="flex items-center gap-1" data-appbana-column-filter={name}>
        <input
          type="number"
          className={inputCls}
          aria-label={`Minimum ${label}`}
          placeholder="Min"
          value={minVal}
          onChange={(e) => {
            onChange(e.target.value === '' && maxVal === '' ? undefined : range(e.target.value || undefined, maxVal || undefined));
          }}
        />
        <input
          type="number"
          className={inputCls}
          aria-label={`Maximum ${label}`}
          placeholder="Max"
          value={maxVal}
          onChange={(e) => {
            onChange(minVal === '' && e.target.value === '' ? undefined : range(minVal || undefined, e.target.value || undefined));
          }}
        />
      </div>
    );
  }

  if (type === 'date' || type === 'datetime') {
    // Same value-prop-derived approach as the number range above — see the
    // comment there for why local draft state is unsafe here.
    const bounds = rangeBounds(value);
    const minVal = boundToDisplayString(bounds?.min).slice(0, 10);
    const maxVal = boundToDisplayString(bounds?.max).slice(0, 10);
    return (
      <div className="flex items-center gap-1" data-appbana-column-filter={name}>
        <input
          type="date"
          className={inputCls}
          aria-label={`${label} from`}
          value={minVal}
          onChange={(e) => {
            const lo = e.target.value ? `${e.target.value}T00:00:00Z` : undefined;
            const hi = maxVal ? `${maxVal}T23:59:59Z` : undefined;
            onChange(lo === undefined && hi === undefined ? undefined : range(lo, hi));
          }}
        />
        <input
          type="date"
          className={inputCls}
          aria-label={`${label} to`}
          value={maxVal}
          onChange={(e) => {
            const lo = minVal ? `${minVal}T00:00:00Z` : undefined;
            const hi = e.target.value ? `${e.target.value}T23:59:59Z` : undefined;
            onChange(lo === undefined && hi === undefined ? undefined : range(lo, hi));
          }}
        />
      </div>
    );
  }

  // Default: text-contains substring filter (string/longtext/email/phone/status/unknown).
  return (
    <input
      type="text"
      className={inputCls}
      aria-label={`Filter ${label}`}
      placeholder="Contains…"
      data-appbana-column-filter={name}
      value={typeof value === 'string' ? value : ''}
      onChange={(e) => onChange(e.target.value === '' ? undefined : e.target.value)}
    />
  );
}
