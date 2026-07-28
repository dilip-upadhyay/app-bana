import { useEffect, useMemo, useState } from 'react';
import {
  listEntities,
  getEntitySchema,
  fetchEntityRows,
  fetchEntityRowCount,
  insertEntityRow,
  type EntitySchema,
  type EntityField,
} from '@appbana/shared';
import { useSessionStore } from '../../stores/session';
import { useWorkspaceStore } from '../../stores/workspace';
import { useDrawerStore } from '../../stores/drawer';

const PAGE_SIZE = 25;

type SortDir = 'asc' | 'desc';
interface SortState { col: string; dir: SortDir; }

/**
 * Slide-in data drawer — lists all entities in the current app on the left
 * (each with its row count), shows the first N rows of the selected entity
 * on the right in a sortable, paged table, and lets the user insert a row
 * via a schema-driven form or ask the AI to seed data.
 */
export function DataDrawer() {
  const { token, tenantId } = useSessionStore();
  const { currentApp } = useWorkspaceStore();
  const { dataOpen, closeAll } = useDrawerStore();

  const [entities, setEntities] = useState<EntitySchema[]>([]);
  const [entityCounts, setEntityCounts] = useState<Record<string, number>>({});
  const [selectedKey, setSelectedKey] = useState<string | null>(null);
  const [schema, setSchema] = useState<EntitySchema | null>(null);
  const [rows, setRows] = useState<any[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState<SortState | null>(null);
  const [loading, setLoading] = useState(false);
  const [showAddForm, setShowAddForm] = useState(false);

  const canWork = !!(token && tenantId && currentApp);

  // Load entity list AND row counts when the drawer opens or the app changes
  useEffect(() => {
    if (!dataOpen || !canWork) return;
    setLoading(true);
    listEntities(tenantId!, currentApp!.id, token!)
      .then((list) => {
        setEntities(list);
        // Fetch counts in parallel; keep name unaffected if any single call fails
        Promise.all(
          list.map(async (e) => {
            const key = `${tenantId}_${currentApp!.id}_${e.name}`;
            const count = await fetchEntityRowCount(key, token!);
            return [e.name, count] as const;
          })
        ).then((pairs) => {
          setEntityCounts(Object.fromEntries(pairs));
        });
        // Auto-select the first entity if none picked yet or previous is gone
        if (list.length > 0) {
          const key = `${tenantId}_${currentApp!.id}_${list[0].name}`;
          setSelectedKey(key);
        } else {
          setSelectedKey(null);
          setSchema(null);
          setRows([]);
        }
      })
      .catch(() => {
        setEntities([]);
        setEntityCounts({});
      })
      .finally(() => setLoading(false));
    // Only refetch when the drawer opens or the app changes
  }, [dataOpen, currentApp?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  // Load schema + rows for the selected entity (resets sort + page)
  useEffect(() => {
    if (!selectedKey || !token) return;
    setLoading(true);
    setPage(0);
    setSort(null);
    Promise.all([
      getEntitySchema(selectedKey, token).catch(() => null),
      fetchEntityRows(selectedKey, token, { limit: PAGE_SIZE, offset: 0 }),
    ])
      .then(([sch, res]) => {
        setSchema(sch);
        setRows(res.rows);
        setTotal(res.total);
      })
      .catch(() => {
        setSchema(null);
        setRows([]);
        setTotal(0);
      })
      .finally(() => setLoading(false));
  }, [selectedKey, token]);

  // Refetch rows when page OR sort changes (skip initial mount handled above)
  useEffect(() => {
    if (!selectedKey || !token) return;
    // Skip the very first render — schema-load effect above already fetched page 0
    if (page === 0 && sort === null) return;
    setLoading(true);
    const params: Record<string, string | number> = {
      limit: PAGE_SIZE,
      offset: page * PAGE_SIZE,
    };
    if (sort) params.sort = `${sort.col}:${sort.dir}`;
    fetchEntityRows(selectedKey, token, params)
      .then((res) => {
        setRows(res.rows);
        setTotal(res.total);
      })
      .catch(() => {
        // Keep previous data on transient failure
      })
      .finally(() => setLoading(false));
  }, [page, sort, selectedKey, token]);

  function toggleSort(col: string) {
    setPage(0);
    setSort((cur) => {
      if (!cur || cur.col !== col) return { col, dir: 'asc' };
      if (cur.dir === 'asc') return { col, dir: 'desc' };
      return null; // asc → desc → cleared
    });
  }

  const columns = useMemo<EntityField[]>(() => {
    if (schema?.fields?.length) return schema.fields;
    // Fallback: infer columns from row 0 when schema fetch failed
    if (rows[0]) return Object.keys(rows[0]).map((k) => ({ name: k, type: 'text' as const }));
    return [];
  }, [schema, rows]);

  async function handleInsert(values: Record<string, unknown>) {
    if (!selectedKey || !token) return;
    try {
      await insertEntityRow(selectedKey, values, token);
      // Refresh page 0 to show new row
      const res = await fetchEntityRows(selectedKey, token, { limit: PAGE_SIZE, offset: 0 });
      setRows(res.rows);
      setTotal(res.total);
      setPage(0);
      setShowAddForm(false);
    } catch (err) {
      alert(`Insert failed: ${err instanceof Error ? err.message : 'Unknown error'}`);
    }
  }

  function askAiToSeed() {
    if (!schema || !currentApp) return;
    const entityName = schema.name;
    const prompt = `Generate 20 realistic rows of mock data for the ${entityName} entity in the ${currentApp.name} app.`;
    // Simple handoff: put a hint into the composer via a custom event ChatPane listens for
    window.dispatchEvent(new CustomEvent('studio:composer:set', { detail: prompt }));
    closeAll();
  }

  if (!dataOpen) return null;

  return (
    <>
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-black/40 z-40"
        onClick={closeAll}
        role="presentation"
        aria-hidden="true"
      />
      {/* Panel */}
      <aside
        className="fixed top-0 right-0 h-full w-full sm:w-[720px] bg-gray-900 border-l border-gray-800
                   z-50 flex flex-col shadow-2xl"
        role="dialog"
        aria-label="Data drawer"
      >
        {/* Header */}
        <div className="h-12 shrink-0 flex items-center px-4 border-b border-gray-800">
          <span className="text-sm font-semibold text-white">Data</span>
          {currentApp && (
            <span className="ml-2 text-xs text-gray-500">· {currentApp.name}</span>
          )}
          <div className="flex-1" />
          <button
            onClick={closeAll}
            className="text-gray-400 hover:text-white text-lg leading-none px-2"
            aria-label="Close"
          >
            ×
          </button>
        </div>

        {!canWork && (
          <div className="p-6 text-gray-500 text-sm">Select an app to view its data.</div>
        )}

        {canWork && (
          <div className="flex flex-1 overflow-hidden">
            {/* Entity list */}
            <div className="w-52 shrink-0 border-r border-gray-800 overflow-y-auto py-1">
              {entities.length === 0 && !loading && (
                <div className="px-3 py-4 text-xs text-gray-500">No entities yet.</div>
              )}
              {entities.map((e) => {
                const key = `${tenantId}_${currentApp!.id}_${e.name}`;
                const active = key === selectedKey;
                const count = entityCounts[e.name];
                return (
                  <button
                    key={e.name}
                    onClick={() => setSelectedKey(key)}
                    className={`w-full flex items-center gap-2 px-3 py-2 text-xs border-l-2 transition-colors
                      ${active
                        ? 'bg-gray-800 border-indigo-500 text-white'
                        : 'border-transparent text-gray-300 hover:bg-gray-800/60 hover:text-white'}`}
                  >
                    <span className="truncate flex-1 text-left">{e.name}</span>
                    {count !== undefined && (
                      <span className={`text-[10px] px-1.5 py-0.5 rounded shrink-0
                        ${active ? 'bg-indigo-500/30 text-indigo-100' : 'bg-gray-800 text-gray-500'}`}>
                        {count}
                      </span>
                    )}
                  </button>
                );
              })}
            </div>

            {/* Data view */}
            <div className="flex-1 flex flex-col overflow-hidden">
              <div className="h-10 shrink-0 flex items-center gap-2 px-3 border-b border-gray-800">
                <span className="text-xs text-gray-400">
                  {schema?.name ?? '—'} · {total} row{total === 1 ? '' : 's'}
                </span>
                <div className="flex-1" />
                <button
                  onClick={() => setShowAddForm((v) => !v)}
                  disabled={!schema}
                  className="text-xs bg-gray-800 hover:bg-gray-700 text-gray-200 px-2 py-1 rounded
                             border border-gray-700 disabled:opacity-40"
                >
                  {showAddForm ? 'Cancel' : '+ Add row'}
                </button>
                <button
                  onClick={askAiToSeed}
                  disabled={!schema}
                  className="text-xs bg-indigo-600 hover:bg-indigo-500 text-white px-2 py-1 rounded
                             disabled:opacity-40"
                >
                  Ask AI to seed
                </button>
              </div>

              {showAddForm && schema && (
                <AddRowForm fields={schema.fields} onSubmit={handleInsert} onCancel={() => setShowAddForm(false)} />
              )}

              <div className="flex-1 overflow-auto">
                {loading && <div className="p-4 text-xs text-gray-500">Loading…</div>}
                {!loading && rows.length === 0 && (
                  <div className="p-6 text-xs text-gray-500 text-center">No data yet.</div>
                )}
                {!loading && rows.length > 0 && (
                  <table className="w-full text-xs">
                    <thead className="bg-gray-800 text-gray-400 sticky top-0">
                      <tr>
                        {columns.map((c) => {
                          const isSorted = sort?.col === c.name;
                          let arrow = '';
                          if (isSorted) arrow = sort.dir === 'asc' ? ' ↑' : ' ↓';
                          return (
                            <th
                              key={c.name}
                              onClick={() => toggleSort(c.name)}
                              className={`text-left px-3 py-2 font-medium border-b border-gray-700
                                cursor-pointer select-none hover:text-white
                                ${isSorted ? 'text-indigo-300' : ''}`}
                              title={`Sort by ${c.label ?? c.name}`}
                            >
                              {(c.label ?? c.name) + arrow}
                            </th>
                          );
                        })}
                      </tr>
                    </thead>
                    <tbody>
                      {rows.map((r, i) => (
                        <tr key={r.id ?? i} className="border-b border-gray-800 hover:bg-gray-800/40">
                          {columns.map((c) => (
                            <td key={c.name} className="px-3 py-1.5 text-gray-200 truncate max-w-xs">
                              {formatCell(r[c.name])}
                            </td>
                          ))}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>

              {/* Pagination */}
              {total > PAGE_SIZE && (
                <div className="h-9 shrink-0 flex items-center justify-end gap-2 px-3 border-t border-gray-800 text-xs">
                  <button
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                    disabled={page === 0 || loading}
                    className="text-gray-400 hover:text-white disabled:opacity-30 px-2"
                  >
                    ← Prev
                  </button>
                  <span className="text-gray-500">
                    {page * PAGE_SIZE + 1}–{Math.min((page + 1) * PAGE_SIZE, total)} of {total}
                  </span>
                  <button
                    onClick={() => setPage((p) => p + 1)}
                    disabled={(page + 1) * PAGE_SIZE >= total || loading}
                    className="text-gray-400 hover:text-white disabled:opacity-30 px-2"
                  >
                    Next →
                  </button>
                </div>
              )}
            </div>
          </div>
        )}
      </aside>
    </>
  );
}

function formatCell(v: unknown): string {
  if (v === null || v === undefined) return '';
  if (typeof v === 'boolean') return v ? '✓' : '';
  if (typeof v === 'object') return JSON.stringify(v);
  return String(v);
}

function AddRowForm({
  fields,
  onSubmit,
  onCancel,
}: {
  fields: EntityField[];
  onSubmit: (values: Record<string, unknown>) => void;
  onCancel: () => void;
}) {
  const [values, setValues] = useState<Record<string, unknown>>({});
  const writable = fields.filter((f) => !f.autoIncrement);

  function set(name: string, v: unknown) {
    setValues((prev) => ({ ...prev, [name]: v }));
  }

  function coerce(f: EntityField, raw: string): unknown {
    if (raw === '') return undefined;
    if (f.type === 'number') return Number(raw);
    if (f.type === 'decimal') return Number(raw);
    if (f.type === 'boolean') return raw === 'true';
    return raw;
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const cleaned: Record<string, unknown> = {};
    for (const f of writable) {
      const v = values[f.name];
      if (v !== undefined && v !== '') cleaned[f.name] = v;
    }
    onSubmit(cleaned);
  }

  return (
    <form onSubmit={handleSubmit} className="border-b border-gray-800 bg-gray-950/50 p-3 space-y-2">
      <div className="grid grid-cols-2 gap-2">
        {writable.map((f) => (
          <label key={f.name} className="text-xs text-gray-400 flex flex-col gap-1">
            <span>{f.label ?? f.name}{f.required && <span className="text-red-400"> *</span>}</span>
            {f.type === 'boolean' ? (
              <select
                value={String(values[f.name] ?? '')}
                onChange={(e) => set(f.name, coerce(f, e.target.value))}
                className="bg-gray-900 border border-gray-700 rounded px-2 py-1 text-white text-xs"
              >
                <option value="">—</option>
                <option value="true">true</option>
                <option value="false">false</option>
              </select>
            ) : f.type === 'status' && f.options ? (
              <select
                value={String(values[f.name] ?? '')}
                onChange={(e) => set(f.name, e.target.value)}
                className="bg-gray-900 border border-gray-700 rounded px-2 py-1 text-white text-xs"
              >
                <option value="">—</option>
                {f.options.map((o) => (
                  <option key={o} value={o}>{o}</option>
                ))}
              </select>
            ) : f.type === 'longtext' ? (
              <textarea
                rows={2}
                onChange={(e) => set(f.name, e.target.value)}
                className="bg-gray-900 border border-gray-700 rounded px-2 py-1 text-white text-xs resize-none"
              />
            ) : (
              <input
                type={inputTypeFor(f.type)}
                required={f.required}
                onChange={(e) => set(f.name, coerce(f, e.target.value))}
                className="bg-gray-900 border border-gray-700 rounded px-2 py-1 text-white text-xs"
              />
            )}
          </label>
        ))}
      </div>
      <div className="flex justify-end gap-2 pt-1">
        <button
          type="button"
          onClick={onCancel}
          className="text-xs text-gray-400 hover:text-white px-3 py-1"
        >
          Cancel
        </button>
        <button
          type="submit"
          className="text-xs bg-indigo-600 hover:bg-indigo-500 text-white px-3 py-1 rounded"
        >
          Insert
        </button>
      </div>
    </form>
  );
}

function inputTypeFor(t: EntityField['type']): string {
  switch (t) {
    case 'email': return 'email';
    case 'number':
    case 'decimal': return 'number';
    case 'date': return 'date';
    case 'datetime': return 'datetime-local';
    case 'phone': return 'tel';
    default: return 'text';
  }
}
