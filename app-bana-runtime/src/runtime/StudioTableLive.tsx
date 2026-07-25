/**
 * StudioTableLive.tsx — React port of app-bana-ui's LitElement table.
 *
 * Fetches rows from /api/{entityKey}, supports pagination and a basic
 * "New" record dialog.  Emits data-appbana-* for Stage 6 selection.
 */
import { useEffect, useState, useCallback } from 'react';
import type { ComponentNode } from '@appbana/shared';
import { fetchEntityRows } from '@appbana/shared';
import { qualifyEntityKey, getRuntimeToken } from './qualifyEntityKey';

interface Props {
  node: ComponentNode;
  pageId: string;
}

const getToken = getRuntimeToken;

export function StudioTableLive({ node, pageId }: Props) {
  const props = node.props ?? {};
  // Strip trailing '=' chars (Lit attribute binding suffix not needed in React)
  const rawEntity = String(props.entity ?? '');
  let entityKey = rawEntity;
  while (entityKey.endsWith('=')) entityKey = entityKey.slice(0, -1);
  const fields: Array<{ name: string; label?: string; type?: string }> =
    Array.isArray(props.fields) ? props.fields : [];

  const [rows, setRows] = useState<Record<string, unknown>[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const pageSize = 25;
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    if (!entityKey) return;
    setLoading(true);
    setError('');
    try {
      const result = await fetchEntityRows(qualifyEntityKey(entityKey), getToken(), {
        limit: pageSize,
        offset: (page - 1) * pageSize,
      });
      setRows(result.rows);
      setTotal(result.total);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load data');
    } finally {
      setLoading(false);
    }
  }, [entityKey, page]);

  useEffect(() => { load(); }, [load]);

  // Refresh when any form on the page inserts a row for this entity.
  useEffect(() => {
    const handler = (e: Event) => {
      const detail = (e as CustomEvent<{ entity?: string }>).detail;
      if (!detail?.entity) return;
      // Match either the bare name or the fully-qualified key.
      if (detail.entity === entityKey || detail.entity === qualifyEntityKey(entityKey)) {
        load();
      }
    };
    window.addEventListener('appbana:row-inserted', handler);
    return () => window.removeEventListener('appbana:row-inserted', handler);
  }, [entityKey, load]);

  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  const label = String(node.label ?? props.label ?? (entityKey ? `${entityKey} List` : 'Data Table'));

  if (!entityKey) {
    return (
      <div
        className="p-4 text-gray-500 border border-dashed rounded-lg"
        data-appbana-node={node.id}
        data-appbana-page={pageId}
      >
        No entity configured for this table.
      </div>
    );
  }

  return (
    <div
      className="rounded-2xl shadow-md bg-white overflow-hidden my-6"
      data-appbana-node={node.id}
      data-appbana-page={pageId}
      data-appbana-entity={entityKey}
    >
      {/* Header */}
      <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100">
        <h2 className="text-lg font-bold text-gray-800">{label}</h2>
      </div>

      {/* Error */}
      {error && (
        <div className="mx-5 my-3 p-3 bg-red-50 text-red-700 rounded-lg text-sm">
          {error}
        </div>
      )}

      {/* Loading */}
      {loading && (
        <div className="px-5 py-8 text-center text-gray-400 text-sm">Loading…</div>
      )}

      {/* Table */}
      {!loading && (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-gray-50 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">
                {fields.map((f) => (
                  <th key={f.name} className="px-4 py-3">{f.label ?? f.name}</th>
                ))}
                {fields.length === 0 && rows.length > 0 &&
                  Object.keys(rows[0]).filter((k) => k !== 'id').map((k) => (
                    <th key={k} className="px-4 py-3">{k}</th>
                  ))
                }
              </tr>
            </thead>
            <tbody>
              {rows.length === 0 && (
                <tr>
                  <td
                    colSpan={Math.max(fields.length, 1)}
                    className="px-4 py-8 text-center text-gray-400 italic"
                  >
                    No data available.
                  </td>
                </tr>
              )}
              {rows.map((row, idx) => {
                const displayFields = fields.length > 0
                  ? fields.map((f) => f.name)
                  : Object.keys(row).filter((k) => k !== 'id');
                return (
                  <tr
                    key={String(row.id ?? idx)}
                    className="border-t border-gray-100 hover:bg-gray-50 transition-colors"
                    data-appbana-entity={entityKey}
                    data-appbana-node={node.id}
                  >
                    {displayFields.map((key) => (
                      <td
                        key={key}
                        className="px-4 py-3 text-gray-700"
                        data-appbana-field={key}
                      >
                        {String(row[key] ?? '')}
                      </td>
                    ))}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between px-5 py-3 border-t border-gray-100 text-xs text-gray-500">
          <span>{total} record{total !== 1 ? 's' : ''}</span>
          <div className="flex gap-2">
            <button
              onClick={() => setPage((p) => Math.max(1, p - 1))}
              disabled={page === 1}
              className="px-2 py-1 rounded border disabled:opacity-40 hover:bg-gray-100"
            >
              ←
            </button>
            <span>Page {page} / {totalPages}</span>
            <button
              onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
              disabled={page === totalPages}
              className="px-2 py-1 rounded border disabled:opacity-40 hover:bg-gray-100"
            >
              →
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
