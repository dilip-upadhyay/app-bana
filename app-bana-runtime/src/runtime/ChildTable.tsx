/**
 * Phase B4 — Master–detail: renders the child rows of a parent record.
 *
 * Given a child entity key, the foreign-key column that points at the parent,
 * and the current parent row's id, this component:
 *   1. Fetches child rows filtered by `?{fkField}={parentId}`
 *   2. Renders them in a compact table with humanized headers
 *   3. Refreshes on `appbana:row-inserted/updated/deleted` events for the
 *      child entity, so add-row-and-return flows stay in sync
 *   4. H2 hardening — offers per-row Delete + Copy ID via {@link RowActions}
 *      so the child table is actually interactive, not just a read view.
 *
 * Intentionally scoped: does NOT wrap the full StudioTableLive column
 * inference (status pills / date formatting are still handled by cell
 * formatters used inline here). Row-level Edit is deferred because it
 * needs a mini-form modal — tracked separately, not part of H2.
 */
import { useCallback, useEffect, useState } from 'react';
import { deleteEntityRow, fetchEntityRows } from '@appbana/shared';
import { qualifyEntityKey, getRuntimeToken } from './qualifyEntityKey';
import { humanizeHeader, formatDate } from './cell-formatters';
import { Skeleton } from './Skeleton';
import { StatusPill } from './StatusPill';
import { RowActions } from './RowActions';
import { useConfirm } from './ConfirmDialog';
import { toast } from './Toaster';
export interface ChildTableProps {
  readonly entityName: string;     // child entity, bare or qualified
  readonly fkField: string;        // e.g. "customer_id"
  readonly parentId: string | number;
  readonly displayFields?: readonly string[];  // subset to show; falls back to first 4 non-id fields
  readonly pageSize?: number;
  readonly emptyLabel?: string;
}

export function ChildTable(props: Readonly<ChildTableProps>) {
  const { entityName, fkField, parentId, displayFields, pageSize = 20, emptyLabel } = props;
  const [rows, setRows] = useState<Record<string, unknown>[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const confirm = useConfirm();
  const qualified = qualifyEntityKey(entityName);

  const load = useCallback(async () => {
    if (!entityName || !fkField || parentId === undefined || parentId === null || parentId === '') return;
    setLoading(true);
    setError('');
    try {
      const result = await fetchEntityRows(
        qualified,
        getRuntimeToken(),
        { [fkField]: String(parentId), limit: pageSize, offset: 0 }
      );
      setRows(result.rows);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load related rows');
    } finally {
      setLoading(false);
    }
  }, [entityName, fkField, parentId, pageSize, qualified]);

  useEffect(() => { load().catch(() => {}); }, [load]);

  useEffect(() => {
    const handler = (e: Event) => {
      const detail = (e as CustomEvent<{ entity?: string }>).detail;
      if (!detail?.entity) return;
      if (detail.entity === entityName || detail.entity === qualified) {
        load().catch(() => {});
      }
    };
    window.addEventListener('appbana:row-inserted', handler);
    window.addEventListener('appbana:row-updated', handler);
    window.addEventListener('appbana:row-deleted', handler);
    return () => {
      window.removeEventListener('appbana:row-inserted', handler);
      window.removeEventListener('appbana:row-updated', handler);
      window.removeEventListener('appbana:row-deleted', handler);
    };
  }, [entityName, qualified, load]);

  const handleDelete = useCallback(async (rowId: string) => {
    const ok = await confirm({
      title: 'Delete this row?',
      message: 'This cannot be undone.',
      confirmLabel: 'Delete',
      danger: true,
    });
    if (!ok) return;
    setDeletingId(rowId);
    try {
      await deleteEntityRow(qualified, rowId, getRuntimeToken());
      window.dispatchEvent(new CustomEvent('appbana:row-deleted', {
        detail: { entity: qualified, id: rowId },
      }));
      toast.success('Row deleted');
    } catch (e) {
      toast.error('Delete failed', {
        description: e instanceof Error ? e.message : String(e),
      });
    } finally {
      setDeletingId(null);
    }
  }, [qualified, confirm]);

  if (loading && rows.length === 0) {
    return <Skeleton className="h-16 w-full" />;
  }

  if (error) {
    return <div className="text-sm text-rose-600">{error}</div>;
  }

  if (rows.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-slate-300 bg-slate-50 px-4 py-8 text-center text-sm text-slate-500">
        {emptyLabel ?? 'No related records yet.'}
      </div>
    );
  }

  const columns = pickColumns(rows[0], displayFields);

  return (
    <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white">
      <table className="min-w-full text-sm">
        <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
          <tr>
            {columns.map((c) => (
              <th key={c} className="px-3 py-2 font-medium">{humanizeHeader(c)}</th>
            ))}
            <th aria-label="Row actions" className="w-10 px-2 py-2" />
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {rows.map((row, i) => {
            const rid = row.id;
            const key = typeof rid === 'string' || typeof rid === 'number' ? String(rid) : String(i);
            const rowId = typeof rid === 'string' || typeof rid === 'number' ? String(rid) : '';
            return (
              <tr
                key={key}
                className={`hover:bg-slate-50 ${deletingId === rowId ? 'opacity-50' : ''}`}
              >
                {columns.map((c) => (
                  <td key={c} className="px-3 py-2 align-top">{renderCell(c, row[c])}</td>
                ))}
                {rowId ? (
                  <RowActions
                    rowId={rowId}
                    onDelete={() => { void handleDelete(rowId); }}
                  />
                ) : (
                  <td />
                )}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function pickColumns(sample: Record<string, unknown>, requested?: readonly string[]): string[] {
  if (requested && requested.length > 0) return [...requested];
  const keys = Object.keys(sample).filter((k) => k.toLowerCase() !== 'id');
  return keys.slice(0, 4);
}

function renderCell(name: string, raw: unknown): React.ReactElement {
  if (raw === null || raw === undefined || raw === '') {
    return <span className="text-slate-400">—</span>;
  }
  const scalar = typeof raw === 'object' ? JSON.stringify(raw) : (raw as string | number | boolean).toString();
  // status-ish
  if (name.toLowerCase().includes('status')) {
    return <StatusPill value={scalar} />;
  }
  // date-ish
  if (/_(at|on|date)$/i.test(name)) {
    const f = formatDate(raw, 'datetime');
    if (!f.label) return <span className="text-slate-400">—</span>;
    return <span title={f.title}>{f.label}</span>;
  }
  if (typeof raw === 'boolean') {
    return <StatusPill value={raw ? 'Yes' : 'No'} tone={raw ? 'success' : 'neutral'} />;
  }
  return <span>{scalar}</span>;
}
