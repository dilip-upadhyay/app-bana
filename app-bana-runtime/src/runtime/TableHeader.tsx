/**
 * Header row for entity tables. Kept dumb: the caller owns all state (sort
 * direction, filter values) and this only renders controls and reports intent
 * via callbacks.
 *
 * Sorting and filtering drive server-side `sort=`/`filter=` params rather than
 * reordering the fetched page, because that page is one `limit`/`offset` slice
 * of a dataset that may hold millions of rows.
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

export interface ReferenceOption {
  readonly value: string;
  readonly label: string;
}

export interface TableHeaderProps {
  readonly columns: readonly string[];
  readonly labelFor: (name: string) => string | undefined;
  /**
   * Columns that get a sort affordance + filter control. Defaults to all
   * `columns`. Used to exclude columns that already have dedicated filtering UI
   * (e.g. `approval_status`, served by SavedViewsBar's system views), so the two
   * paths never compete for the same column.
   */
  readonly filterableColumns?: readonly string[];
  readonly typeFor?: (name: string) => string | undefined;
  /** Sourced from the already-fetched FK label cache, so this costs no extra request. */
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

function sortAriaLabel(label: string, active: 'asc' | 'desc' | null): string {
  if (active === 'asc') return `Sort by ${label}, currently ascending`;
  if (active === 'desc') return `Sort by ${label}, currently descending`;
  return `Sort by ${label}`;
}

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

function rangeBounds(value: unknown): { min?: unknown; max?: unknown } | undefined {
  if (value && typeof value === 'object' && '__range' in (value as Record<string, unknown>)) {
    return (value as { __range: { min?: unknown; max?: unknown } }).__range;
  }
  return undefined;
}

function boundToDisplayString(bound: unknown): string {
  if (bound == null) return '';
  if (typeof bound === 'string' || typeof bound === 'number') return String(bound);
  return '';
}

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
    // Derive from the `value` prop, never local state: this control is unmounted
    // and remounted on every filter change, because the parent swaps the whole
    // table for a loading skeleton while refetching. Local draft state resets to
    // '' on that remount, and the next bound's onChange then reads the other
    // bound through a stale closure over '' — silently dropping a just-typed
    // filter. Found live-testing; the unit suite has no DOM and cannot reach it.
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
    // Prop-derived for the same reason as the number range above.
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

  // Fallback for string/longtext/email/phone/status/unknown.
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
