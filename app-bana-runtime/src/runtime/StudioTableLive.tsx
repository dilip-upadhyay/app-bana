/**
 * StudioTableLive.tsx — React port of app-bana-ui's LitElement table.
 *
 * Fetches rows from /api/{entityKey}, supports pagination, and — as of the
 * Runtime UX Overhaul (Sprint 1) — renders:
 *   - human-formatted dates (§1.2)
 *   - resolved FK labels via a background lookup (§1.3)
 *   - sentence-case column headers, no ALL-CAPS in the DOM (§1.4)
 *   - status pills for `type: "status"` columns (§1.5)
 *   - hover-revealed row actions with a proper empty state (§1.9)
 *   - viewport-filling shell (§1.10)
 */
import { useEffect, useMemo, useState, useCallback } from 'react';
import type { ComponentNode } from '@appbana/shared';
import { fetchEntityRows, deleteEntityRow, insertEntityRow } from '@appbana/shared';
import { qualifyEntityKey, getRuntimeToken } from './qualifyEntityKey';
import { formatDate, humanizeHeader, pickReferenceLabel } from './cell-formatters';
import { StatusPill } from './StatusPill';
import { RowActions } from './RowActions';
import { toast } from './Toaster';
import { EmptyState } from './EmptyState';
import { TableSkeleton } from './Skeleton';
import { useRuntimeNavigation } from './runtime-navigation';
import { useConfirm } from './ConfirmDialog';
import { useEntityRows } from './useEntityRows';
import { TableHeader } from './TableHeader';
import { PaginationBar } from './PaginationBar';
import {
  entityNameFromKey,
  findAddPageForEntity,
  findDetailPageForEntity,
} from './page-classifier';

interface Props {
  readonly node: ComponentNode;
  readonly pageId: string;
}

interface FieldMeta {
  readonly name: string;
  readonly label?: string;
  readonly type?: string;
  readonly referenceEntity?: string;
}

const getToken = getRuntimeToken;
const PAGE_SIZE = 25;

export function StudioTableLive({ node, pageId }: Readonly<Props>) {
  const props = node.props ?? {};
  const rawEntity = String(props.entity ?? '');
  let entityKey = rawEntity;
  while (entityKey.endsWith('=')) entityKey = entityKey.slice(0, -1);

  const fields: FieldMeta[] = useMemo(
    () => (Array.isArray(props.fields) ? (props.fields as FieldMeta[]) : []),
    [props.fields],
  );

  // Sprint 3 task 3.12 — pagination + lifecycle refresh live in a hook now.
  const {
    rows,
    total,
    page,
    totalPages,
    loading,
    error,
    setPage,
  } = useEntityRows(entityKey, PAGE_SIZE);

  const [fkMaps, setFkMaps] = useState<Record<string, Map<string, string>>>({});
  // Sprint 3 task 3.6 — RowActions navigation + destructive confirm.
  const nav = useRuntimeNavigation();
  const confirm = useConfirm();

  // Sprint 3 task 3.6 — delete a single row with confirm dialog + undo toast.
  const handleDelete = useCallback(async (row: Record<string, unknown>, rowId: string) => {
    const entityName = entityNameFromKey(entityKey) || 'record';
    const ok = await confirm({
      title: `Delete ${entityName}?`,
      message: 'This can be undone from the notification for a few seconds.',
      confirmLabel: 'Delete',
      danger: true,
    });
    if (!ok) return;
    const token = getToken();
    try {
      await deleteEntityRow(qualifyEntityKey(entityKey), rowId, token);
      window.dispatchEvent(new CustomEvent('appbana:row-deleted', {
        detail: { entity: qualifyEntityKey(entityKey), id: rowId },
      }));
      toast.success(`${entityName} deleted`, {
        // Sprint 3 task 3.10 — action slot restores the row.
        action: {
          label: 'Undo',
          onClick: () => {
            insertEntityRow(qualifyEntityKey(entityKey), row, token)
              .then(() => {
                window.dispatchEvent(new CustomEvent('appbana:row-inserted', {
                  detail: { entity: qualifyEntityKey(entityKey) },
                }));
                toast.info(`${entityName} restored`);
              })
              .catch((err) => {
                toast.error('Restore failed', {
                  description: err instanceof Error ? err.message : String(err),
                });
              });
          },
        },
      });
    } catch (err) {
      toast.error('Delete failed', {
        description: err instanceof Error ? err.message : String(err),
      });
    }
  }, [entityKey, confirm]);


  // ─── FK label prefetch ────────────────────────────────────────────────
  // For every reference column, load the target entity once so we can render
  // the human label ("Alice Johnson") instead of the raw FK id (`1`).
  useEffect(() => {
    let cancelled = false;
    const refFields = fields.filter((f) => f.type === 'reference');
    if (refFields.length === 0) return;

    Promise.all(
      refFields.map(async (f) => {
        const target = f.referenceEntity || f.name;
        try {
          const { rows: refRows } = await fetchEntityRows(
            qualifyEntityKey(target),
            getToken(),
            { limit: 500 },
          );
          const map = new Map<string, string>();
          for (const r of refRows) {
            const rec = r as Record<string, unknown>;
            const id = String(rec.id ?? rec.ID ?? '');
            if (id) map.set(id, pickReferenceLabel(rec) || `#${id}`);
          }
          return [f.name, map] as const;
        } catch {
          return [f.name, new Map<string, string>()] as const;
        }
      }),
    ).then((entries) => {
      if (cancelled) return;
      const next: Record<string, Map<string, string>> = {};
      for (const [k, v] of entries) next[k] = v;
      setFkMaps(next);
    });

    return () => { cancelled = true; };
  }, [fields, entityKey]);

  const label = String(
    node.label ?? props.label ?? (entityKey ? `${humanizeHeader(entityKey.split('_').pop())} List` : 'Data Table'),
  );

  if (!entityKey) {
    return (
      <div
        className="p-4 text-slate-500 border border-dashed rounded-lg"
        data-appbana-node={node.id}
        data-appbana-page={pageId}
      >
        No entity configured for this table.
      </div>
    );
  }

  // ─── Header / column resolution ───────────────────────────────────────
  const displayFieldNames: string[] = fields.length > 0
    ? fields.map((f) => f.name)
    : (rows[0] ? Object.keys(rows[0]).filter((k) => k.toLowerCase() !== 'id') : []);

  const fieldByName = new Map(fields.map((f) => [f.name, f]));

  function renderCell(fieldName: string, row: Record<string, unknown>) {
    const meta = fieldByName.get(fieldName);
    const raw = row[fieldName];
    const type = meta?.type ?? inferTypeFromName(fieldName);

    // Foreign-key label
    if (type === 'reference') {
      const map = fkMaps[fieldName];
      const idStr = raw == null ? '' : String(raw);
      const resolved = map?.get(idStr);
      if (resolved) return <span>{resolved}</span>;
      return <span className="text-slate-400">{idStr ? `#${idStr}` : '—'}</span>;
    }

    // Status pill
    if (type === 'status') {
      const s = raw == null ? '' : String(raw);
      return <StatusPill value={s} />;
    }

    // Date / datetime
    if (type === 'date' || type === 'datetime' || /_(at|on|date)$/i.test(fieldName)) {
      const f = formatDate(raw, type);
      if (!f.label) return <span className="text-slate-400">—</span>;
      return <span title={f.title}>{f.label}</span>;
    }

    // Boolean
    if (type === 'boolean') {
      const truthy = raw === true || raw === 'true' || raw === 1 || raw === '1';
      return <StatusPill value={truthy ? 'Yes' : 'No'} tone={truthy ? 'success' : 'neutral'} />;
    }

    // Default
    if (raw == null || raw === '') return <span className="text-slate-400">—</span>;
    return <span>{String(raw)}</span>;
  }

  return (
    <div
      className="rounded-xl bg-white border border-slate-200 shadow-sm overflow-hidden flex flex-col"
      data-appbana-node={node.id}
      data-appbana-page={pageId}
      data-appbana-entity={entityKey}
    >
      {/* Header */}
      <div className="flex items-center justify-between px-5 py-4 border-b border-slate-200">
        <div className="min-w-0">
          <h2 className="text-base font-semibold text-slate-900 truncate">{label}</h2>
          {!loading && total > 0 && (
            <p className="text-xs text-slate-500 mt-0.5">{total} record{total !== 1 ? 's' : ''}</p>
          )}
        </div>
      </div>

      {/* Error */}
      {error && (
        <div className="mx-5 my-3 p-3 bg-rose-50 text-rose-700 rounded-lg text-sm">
          {error}
        </div>
      )}

      {/* Loading skeleton */}
      {loading && (
        <div className="px-5 py-4">
          <TableSkeleton columns={displayFieldNames.length} rows={5} />
        </div>
      )}

      {/* Table (populated) */}
      {!loading && rows.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <TableHeader
              columns={displayFieldNames}
              labelFor={(name) => fieldByName.get(name)?.label}
            />
            <tbody>
              {rows.map((row, idx) => {
                const rowId = String(row.id ?? idx);
                // Sprint 3 task 3.6 — resolve the destination detail page
                // for Edit; when no detail page exists we fall back to
                // omitting `onEdit` so the menu item is hidden.
                const entityName = entityNameFromKey(entityKey);
                const detailPage = nav ? findDetailPageForEntity(entityName, nav.pages) : null;
                return (
                  <tr
                    key={rowId}
                    className="appbana-table-row group"
                    data-appbana-entity={entityKey}
                    data-appbana-node={node.id}
                  >
                    {displayFieldNames.map((name) => (
                      <td
                        key={name}
                        className="appbana-table-td"
                        data-appbana-field={name}
                      >
                        {renderCell(name, row)}
                      </td>
                    ))}
                    <RowActions
                      rowId={rowId}
                      onCopy={() => {
                        if (navigator?.clipboard) {
                          navigator.clipboard.writeText(rowId)
                            .then(() => toast.success('Copied', { description: `Row ID ${rowId}` }))
                            .catch(() => toast.error('Copy failed'));
                        }
                      }}
                      onEdit={detailPage && nav
                        ? () => nav.navigateToRecord(detailPage, rowId)
                        : undefined
                      }
                      onDelete={() => { handleDelete(row, rowId).catch(() => {}); }}
                    />
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* Empty state */}
      {!loading && rows.length === 0 && !error && (
        <EmptyStateBlock entityKey={entityKey} />
      )}

      <PaginationBar
        page={page}
        totalPages={totalPages}
        onPrev={() => setPage(Math.max(1, page - 1))}
        onNext={() => setPage(Math.min(totalPages, page + 1))}
      />
    </div>
  );
}

/** Fallback type detection when the schema doesn't include a type hint. */
function inferTypeFromName(name: string): string | undefined {
  const n = name.toLowerCase();
  if (n.endsWith('_at') || n.endsWith('_on') || n.endsWith('_date') || n === 'date') return 'datetime';
  if (n.includes('status')) return 'status';
  return undefined;
}

/**
 * EmptyStateBlock — renders the illustrated empty state, wiring in the
 * "Add {Entity}" CTA when a matching Add page exists in the current app.
 * Split out so the main component stays readable and so we don't call
 * `useRuntimeNavigation` conditionally inside the JSX tree.
 */
function EmptyStateBlock({ entityKey }: Readonly<{ entityKey: string }>) {
  const nav = useRuntimeNavigation();
  const entityName = entityNameFromKey(entityKey);
  const humanEntity = entityName || 'record';
  const addPage = nav ? findAddPageForEntity(entityName, nav.pages) : null;
  return (
    <EmptyState
      entityName={entityName}
      title={`No ${humanEntity.toLowerCase()} records yet`}
      description={
        addPage
          ? `Add your first ${humanEntity.toLowerCase()} to get started, or ask the AI builder to seed some data.`
          : `Use the form on this app or ask the AI builder to add your first ${humanEntity.toLowerCase()}.`
      }
      action={
        addPage && nav
          ? {
              label: `Add ${humanEntity}`,
              onClick: () => nav.navigateToPage(addPage),
            }
          : undefined
      }
    />
  );
}
