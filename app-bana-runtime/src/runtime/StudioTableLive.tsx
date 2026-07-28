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
 *   - maker-checker approval state (C3.1)
 */
import { useEffect, useMemo, useState, useCallback } from 'react';
import type { ComponentNode, FilterDef, SavedViewRecord } from '@appbana/shared';
import { fetchEntityRows, deleteEntityRow, insertEntityRow, resolveAppContext } from '@appbana/shared';
import { qualifyEntityKey, getRuntimeToken } from './qualifyEntityKey';
import { formatDate, humanizeHeader, pickReferenceLabel } from './cell-formatters';
import { StatusPill } from './StatusPill';
import { ApprovalStatusPill } from './ApprovalStatusPill';
import {
  isApprovalColumn,
  isApprovalStatusColumn,
  rowsHaveApprovalColumns,
  readRowValue,
  APPROVAL_STATUS_COLUMN,
} from './approval-columns';
import { RowActions } from './RowActions';
import { toast } from './Toaster';
import { EmptyState } from './EmptyState';
import { TableSkeleton } from './Skeleton';
import { useRuntimeNavigation } from './runtime-navigation';
import { useConfirm } from './ConfirmDialog';
import { useEntityRows } from './useEntityRows';
import { TableHeader } from './TableHeader';
import { PaginationBar } from './PaginationBar';
import { FilterBar } from './FilterBar';
import { SavedViewsBar } from './SavedViewsBar';
import { buildApprovalSystemViews, isSystemView } from './approval-views';
import { toEntityQueryParams } from './entity-query';
import { useCurrentUser } from './useCurrentUser';
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

  // H3 hardening — filter chips + saved views.
  // `props.filters` is the FilterDef[] the scaffolder attaches to list-page
  // table nodes (see ai-builder GeneratePageTool line ~325). When empty the
  // FilterBar renders nothing, so this is safe on tables that never opted in.
  const filterDefs: FilterDef[] = useMemo(
    () => (Array.isArray(props.filters) ? (props.filters as FilterDef[]) : []),
    [props.filters],
  );
  const [filterValues, setFilterValues] = useState<Record<string, unknown>>(() => {
    // Seed with any `default` on each FilterDef so the initial fetch is scoped.
    const seed: Record<string, unknown> = {};
    for (const f of filterDefs) {
      if (f.default !== undefined) seed[f.field] = f.default;
    }
    return seed;
  });
  // C3.6 — which system view is applied, purely for the chip's active state.
  const [activeViewId, setActiveViewId] = useState<string | null>(null);
  const { user } = useCurrentUser();

  // C3.9 — filters go out as `filter=name:value`, not as bare `?name=value`.
  // The backend reads a fixed param allowlist and drops everything outside it,
  // so bare params were being discarded in transit and the list came back
  // unfiltered with a 200. See entity-query.ts for the full account.
  const { params: fetchParams, rejected: rejectedFilters } = useMemo(
    () => toEntityQueryParams(filterValues),
    [filterValues],
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
  } = useEntityRows(entityKey, PAGE_SIZE, fetchParams);

  const [fkMaps, setFkMaps] = useState<Record<string, Map<string, string>>>({});
  // Sprint 3 task 3.6 — RowActions navigation + destructive confirm.
  const nav = useRuntimeNavigation();
  const confirm = useConfirm();

  // Sprint 3 task 3.6 — delete a single row with confirm dialog + recreate toast.
  //
  // Post-review note: the "Undo" action re-inserts the row payload, which
  // means the restored record gets a *new* primary key. Any foreign keys
  // that pointed at the original row stay orphaned. The copy below is
  // careful not to promise a true undo — call it out as "Recreate" and warn
  // that links won't come back. A real undo needs a soft-delete column and
  // a POST /restore endpoint (deferred, tracked in the follow-up backlog).
  const handleDelete = useCallback(async (row: Record<string, unknown>, rowId: string) => {
    const entityName = entityNameFromKey(entityKey) || 'record';
    const ok = await confirm({
      title: `Delete ${entityName}?`,
      message: 'This cannot be truly undone — the notification lets you recreate the row with the same fields, but any links to other records will not be restored.',
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
        description: 'Use Recreate to insert the same fields back as a new record (with a new id).',
        // Sprint 3 task 3.10 — action slot re-inserts the row as a fresh record.
        action: {
          label: 'Recreate',
          onClick: () => {
            insertEntityRow(qualifyEntityKey(entityKey), row, token)
              .then(() => {
                window.dispatchEvent(new CustomEvent('appbana:row-inserted', {
                  detail: { entity: qualifyEntityKey(entityKey) },
                }));
                toast.info(`${entityName} recreated as a new record`);
              })
              .catch((err) => {
                toast.error('Recreate failed', {
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
  // C3.1: pages are generated from the entity's user-defined fields, so
  // `props.fields` never lists the approval columns the backend injects. If we
  // took `fields` at face value an approval-required entity would render with
  // no approval state visible at all. So: derive approval-awareness from the
  // data, surface `approval_status` as a trailing column, and keep the other
  // seven injected columns hidden — they are workflow plumbing, not content.
  // C3.9 — this is latched. It used to be recomputed from the current page of
  // rows on every render, which made the approval affordances erase themselves:
  // select "Drafts", get zero rows, `rows` no longer proves anything, the
  // system-view chips disappear — while the filter stays applied. The user was
  // stranded on an empty table with no way back but a page reload. Approval
  // support is a property of the entity, not of the current result set, so once
  // observed it holds until the entity changes.
  const [approvalSeen, setApprovalSeen] = useState(false);
  useEffect(() => { setApprovalSeen(false); }, [entityKey]);
  useEffect(() => {
    if (rowsHaveApprovalColumns(rows)) setApprovalSeen(true);
  }, [rows]);
  const approvalEnabled = approvalSeen || rowsHaveApprovalColumns(rows);

  const declaredFieldNames: string[] = fields.length > 0
    ? fields.map((f) => f.name)
    : (rows[0] ? Object.keys(rows[0]).filter((k) => k.toLowerCase() !== 'id') : []);

  const displayFieldNames: string[] = (() => {
    const visible = declaredFieldNames.filter((n) => !isApprovalColumn(n));
    if (approvalEnabled) visible.push(APPROVAL_STATUS_COLUMN);
    return visible;
  })();

  const fieldByName = new Map(fields.map((f) => [f.name, f]));

  function renderCell(fieldName: string, row: Record<string, unknown>) {
    const meta = fieldByName.get(fieldName);
    const raw = row[fieldName];
    const type = meta?.type ?? inferTypeFromName(fieldName);

    // Approval state — checked before the generic status branch, which would
    // otherwise render "PENDING" shouty and colour DRAFT blue instead of slate.
    if (isApprovalStatusColumn(fieldName)) {
      return <ApprovalStatusPill value={readRowValue(row, fieldName)} />;
    }

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

    // Phase B3 — file link. H1 hardening: URL includes tenant + app so the
    // backend can enforce the (tenant, app, fileId) triple and refuse
    // cross-tenant reads. We resolve tenant/app from the same context the
    // entity fetch uses, so both are always in sync.
    if (type === 'file') {
      const fileId = raw == null ? '' : String(raw);
      if (!fileId) return <span className="text-slate-400">—</span>;
      const ctx = resolveAppContext(window.location);
      const tenantId = ctx?.tenantId ?? 'default';
      const appId = ctx?.appId ?? '';
      const href = appId
        ? `/api/files/${tenantId}/${appId}/${fileId}`
        : `/api/files/${fileId}`; // legacy fallback — will 404 on the new backend
      return (
        <a
          href={href}
          target="_blank"
          rel="noopener noreferrer"
          className="text-indigo-600 hover:underline"
          title={fileId}
        >
          Download
        </a>
      );
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

      {/* H3 hardening — saved views chips (fetched from /api/saved-views).
          Only rendered when we can resolve the tenant + app context; on
          runtimes where that's missing the bar quietly hides. */}
      {(() => {
        const ctx = resolveAppContext(window.location);
        if (!ctx) return null;
        const currentView = Object.keys(filterValues).length > 0
          ? { filters: filterValues }
          : undefined;
        // C3.6 — approval system views appear only once the rows prove this
        // entity actually has the workflow enabled, for the same reason the
        // status column does: page metadata never mentions approval.
        const systemViews = approvalEnabled ? buildApprovalSystemViews(user?.userId) : [];
        return (
          <SavedViewsBar
            tenantId={ctx.tenantId}
            appId={ctx.appId}
            entityKey={qualifyEntityKey(entityKey)}
            currentView={currentView}
            systemViews={systemViews}
            activeViewId={activeViewId}
            onSelect={(v) => {
              setActiveViewId(isSystemView(v.viewId) ? v.viewId : null);
              setFilterValues(v.view.filters ?? {});
            }}
          />
        );
      })()}

      {/* H3 hardening — filter chips row. Renders nothing when the page
          meta didn't declare any filters (see FilterBar guard). */}
      <FilterBar
        filters={filterDefs}
        values={filterValues}
        onChange={(next) => {
          // Editing filters by hand means the applied system view no longer
          // describes what is on screen, so drop its active state.
          setActiveViewId(null);
          setFilterValues(next);
        }}
      />

      {/* Error */}
      {error && (
        <div className="mx-5 my-3 p-3 bg-rose-50 text-rose-700 rounded-lg text-sm">
          {error}
        </div>
      )}

      {/* C3.9 — a filter we cannot express on the wire is announced, never
          dropped quietly. A silently unapplied filter shows a wider list than
          the user asked for, and nothing on screen would say so. */}
      {rejectedFilters.length > 0 && (
        <div className="mx-5 my-3 p-3 bg-amber-50 text-amber-800 rounded-lg text-sm">
          {`Not filtering by ${rejectedFilters.join(', ')} — the value contains a comma, which this filter cannot express. The list below is not narrowed by it.`}
        </div>
      )}

      {/* Loading skeleton */}
      {loading && (
        <div className="px-5 py-4">
          <TableSkeleton columns={displayFieldNames.length} rows={5} />
        </div>
      )}

      {/* Phase B5 — client-side group-by. When the page metadata says
          `groupBy`, we bucket the already-fetched rows by that column and
          render one <tbody> per bucket with a sticky header row. */}
      {!loading && rows.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <TableHeader
              columns={displayFieldNames}
              labelFor={(name) => fieldByName.get(name)?.label}
            />
            {(() => {
              const groupByField = typeof props.groupBy === 'string' ? props.groupBy : '';
              if (!groupByField) return null;
              const buckets = new Map<string, typeof rows>();
              for (const row of rows) {
                const key = row[groupByField];
                const keyStr = key == null || key === '' ? '—' : String(key);
                const list = buckets.get(keyStr) ?? [];
                list.push(row);
                buckets.set(keyStr, list);
              }
              return Array.from(buckets.entries()).map(([key, groupRows]) => (
                <tbody key={`grp-${key}`} data-appbana-group={key}>
                  <tr className="bg-slate-50">
                    <td
                      colSpan={displayFieldNames.length + 1}
                      className="px-5 py-2 text-xs font-semibold text-slate-600 uppercase tracking-wide"
                    >
                      {humanizeHeader(groupByField)}: {key}
                      <span className="ml-2 text-slate-400 normal-case">
                        {groupRows.length} record{groupRows.length !== 1 ? 's' : ''}
                      </span>
                    </td>
                  </tr>
                  {groupRows.map((row, idx) => {
                    const rowId = String(row.id ?? `${key}-${idx}`);
                    return (
                      <tr
                        key={rowId}
                        className="appbana-table-row group"
                        data-appbana-entity={entityKey}
                      >
                        {displayFieldNames.map((name) => (
                          <td key={name} className="appbana-table-td" data-appbana-field={name}>
                            {renderCell(name, row)}
                          </td>
                        ))}
                        <td className="appbana-table-td" />
                      </tr>
                    );
                  })}
                </tbody>
              ));
            })()}
            {typeof props.groupBy === 'string' && props.groupBy ? null : (
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
            )}
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
