/**
 * FilterBar.tsx — Phase B5 chip-style filter row for list pages.
 *
 * Reads `FilterDef[]` from a page's metadata contract and renders one
 * control per filter. Emits changes via a single callback so parent
 * containers can push the values into their fetch layer (or into a
 * saved view).
 *
 * Kept intentionally stateless: the parent owns the current values and
 * this component is a pure presentational primitive.
 */
import type { FilterDef } from '@appbana/shared';

interface FilterBarProps {
  readonly filters: FilterDef[];
  readonly values: Record<string, unknown>;
  readonly onChange: (next: Record<string, unknown>) => void;
}

export function FilterBar({ filters, values, onChange }: Readonly<FilterBarProps>) {
  if (!filters || filters.length === 0) return null;

  function updateOne(field: string, next: unknown) {
    onChange({ ...values, [field]: next });
  }

  return (
    <div
      className="flex flex-wrap items-center gap-2 px-5 py-3 border-b border-slate-200 bg-slate-50"
      data-appbana-filter-bar
    >
      {filters.map((f) => (
        <label
          key={f.field}
          className="flex items-center gap-1.5 text-xs text-slate-600"
          data-appbana-filter={f.field}
        >
          <span className="font-medium">{f.label}</span>
          <input
            type="text"
            className="px-2 py-1 rounded-md border border-slate-300 bg-white text-slate-900 text-xs focus:outline-none focus:ring-2 focus:ring-indigo-500"
            value={toInputValue(values[f.field])}
            onChange={(e) => { updateOne(f.field, e.target.value || undefined); }}
            placeholder={f.op}
          />
        </label>
      ))}
      {Object.keys(values).length > 0 && (
        <button
          type="button"
          className="text-xs text-slate-500 hover:text-slate-900 underline"
          onClick={() => { onChange({}); }}
        >
          Clear
        </button>
      )}
    </div>
  );
}

function toInputValue(v: unknown): string {
  if (v == null) return '';
  if (typeof v === 'string' || typeof v === 'number' || typeof v === 'boolean') return String(v);
  return JSON.stringify(v);
}
