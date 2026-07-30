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
import type { ComponentNode, FilterDef, ApprovalTarget } from '@appbana/shared';
import {
  fetchEntityRows,
  deleteEntityRow,
  insertEntityRow,
  resolveAppContext,
  getEntitySchema,
  submitForApproval,
  approveRecord,
  rejectRecord,
  ApprovalConflictError,
} from '@appbana/shared';
import { qualifyEntityKey, getRuntimeToken } from './qualifyEntityKey';
import { formatDate, humanizeHeader, pickReferenceLabel } from './cell-formatters';
import { StatusPill } from './StatusPill';
import { ApprovalStatusPill, toApprovalState } from './ApprovalStatusPill';
import {
  isApprovalColumn,
  isApprovalStatusColumn,
  rowsHaveApprovalColumns,
  readRowValue,
  APPROVAL_STATUS_COLUMN,
} from './approval-columns';
import { RowActions, type RowActionItem } from './RowActions';
import { RejectDialog } from './RejectDialog';
import { AuditDrawer } from './AuditDrawer';
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
  const { user, isMaker, isChecker } = useCurrentUser();

  // C3.9 — filters go out as `filter=name:value`, not as bare `?name=value`.
  // The backend reads a fixed param allowlist and drops everything outside it,
  // so bare params were being discarded in transit and the list came back
  // unfiltered with a 200. See entity-query.ts for the full account.
  const { params: fetchParams, rejected: rejectedFilters } = useMemo(
    () => toEntityQueryParams(filterValues),
    [filterValues],
  );

  // Default-behavior fix — the plain List page never showed a Status column
  // for maker-checker entities. Root cause: the backend keeps the 8 approval
  // columns opt-in (excluded from the default projection so they never leak
  // into callers that didn't ask), so the un-parameterized fetch below always
  // came back without `approval_status`, `rowsHaveApprovalColumns()` was
  // always false, and the status affordance the rendering code already knows
  // how to draw never had data to draw from. Fetch the schema once per entity
  // and, when it's approval-required, explicitly ask for `approval_status`
  // (plus every already-visible column, since specifying `fields=` replaces
  // the default projection rather than adding to it) so the List view shows
  // maker/checker status by default, same as the Detail page and checker queue.
  const [schemaApprovalRequired, setSchemaApprovalRequired] = useState(false);
  useEffect(() => {
    if (!entityKey) return;
    let cancelled = false;
    getEntitySchema(qualifyEntityKey(entityKey), getToken())
      .then((schema) => { if (!cancelled) setSchemaApprovalRequired(Boolean(schema?.approvalRequired)); })
      .catch(() => { if (!cancelled) setSchemaApprovalRequired(false); });
    return () => { cancelled = true; };
  }, [entityKey]);

  const listParams = useMemo(() => {
    if (!schemaApprovalRequired || fields.length === 0) return fetchParams;
    // `submitted_by` is requested too (though not displayed as a column) so
    // the row menu can enforce "you cannot approve your own submission" from
    // the List view without a per-row follow-up fetch.
    const names = new Set<string>(['id', ...fields.map((f) => f.name)]);
    names.add(APPROVAL_STATUS_COLUMN);
    names.add('submitted_by');
    return { ...fetchParams, fields: Array.from(names).join(',') };
  }, [fetchParams, schemaApprovalRequired, fields]);

  // Sprint 3 task 3.12 — pagination + lifecycle refresh live in a hook now.
  const {
    rows,
    total,
    page,
    totalPages,
    loading,
    error,
    setPage,
  } = useEntityRows(entityKey, PAGE_SIZE, listParams);

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

  // Default-behavior fix — the "⋯" row menu only ever offered Edit / Copy ID /
  // Delete, even for approval-required entities. A maker had to open the
  // record's Detail page to find "Submit for approval", and a checker had to
  // leave the List entirely and find the separate approval-queue page just to
  // Approve/Reject a row they could already see. Surface the same actions
  // RecordApprovalPanel (Detail page) and CheckerQueuePage already offer,
  // directly in the row menu, gated by the caller's maker/checker role for
  // this entity — same backend, same 409-conflict handling, same
  // separation-of-duties rule, just reachable from one more place.
  const [rejectingRow, setRejectingRow] = useState<Record<string, unknown> | null>(null);
  const [auditingRow, setAuditingRow] = useState<Record<string, unknown> | null>(null);
  const [busyRowId, setBusyRowId] = useState<string | null>(null);

  const targetFor = useCallback((rowId: string): ApprovalTarget | null => {
    const ctx = resolveAppContext(window.location);
    const name = entityNameFromKey(entityKey);
    if (!ctx?.tenantId || !ctx?.appId || !name) return null;
    return { tenantId: ctx.tenantId, appId: ctx.appId, entityName: name, rowId };
  }, [entityKey]);

  // Rows refresh through the same event bus handleDelete/insertEntityRow
  // already use, rather than a direct refetch — useEntityRows is already
  // listening for it, so every table that mounts this entity stays in sync.
  const notifyRowUpdated = useCallback(() => {
    window.dispatchEvent(new CustomEvent('appbana:row-updated', {
      detail: { entity: qualifyEntityKey(entityKey) },
    }));
  }, [entityKey]);

  const handleApprovalConflict = useCallback((e: unknown, fallbackTitle: string) => {
    if (e instanceof ApprovalConflictError) {
      toast.info('This record already moved on', {
        description: e.message || 'Its approval state changed while this page was open. Refreshed.',
      });
      notifyRowUpdated();
      return;
    }
    toast.error(fallbackTitle, { description: e instanceof Error ? e.message : undefined });
  }, [notifyRowUpdated]);

  const handleSubmitRow = useCallback(async (rowId: string, resubmit: boolean) => {
    const target = targetFor(rowId);
    if (!target) return;
    setBusyRowId(rowId);
    try {
      await submitForApproval(target, getToken());
      toast.success(resubmit ? 'Resubmitted for approval' : 'Submitted for approval', {
        description: 'A checker will review it. You cannot edit it while it is pending.',
      });
      notifyRowUpdated();
    } catch (e) {
      handleApprovalConflict(e, 'Submit failed');
    } finally {
      setBusyRowId(null);
    }
  }, [targetFor, notifyRowUpdated, handleApprovalConflict]);

  const handleApproveRow = useCallback(async (rowId: string) => {
    const target = targetFor(rowId);
    if (!target) return;
    setBusyRowId(rowId);
    try {
      await approveRecord(target, getToken());
      toast.success('Approved', { description: `Record #${rowId} is now live.` });
      notifyRowUpdated();
    } catch (e) {
      handleApprovalConflict(e, 'Approve failed');
    } finally {
      setBusyRowId(null);
    }
  }, [targetFor, notifyRowUpdated, handleApprovalConflict]);

  const handleRejectConfirm = useCallback(async (reason: string) => {
    if (!rejectingRow) return;
    const rowId = String(rejectingRow.id ?? '');
    const target = targetFor(rowId);
    if (!target) return;
    setBusyRowId(rowId);
    try {
      await rejectRecord(target, getToken(), reason);
      setRejectingRow(null);
      toast.success('Sent back to the maker', {
        description: `Record #${rowId} was rejected with your reason.`,
      });
      notifyRowUpdated();
    } catch (e) {
      // Keep the dialog open on a plain failure so the typed reason survives;
      // close it on a conflict, where retrying the same action is pointless.
      if (e instanceof ApprovalConflictError) setRejectingRow(null);
      handleApprovalConflict(e, 'Reject failed');
    } finally {
      setBusyRowId(null);
    }
  }, [rejectingRow, targetFor, notifyRowUpdated, handleApprovalConflict]);

  /** Builds the maker/checker menu items for one row, or an empty array when
   *  the entity isn't approval-required or the caller holds neither role. */
  const buildApprovalActions = useCallback((row: Record<string, unknown>, rowId: string, entityName: string): RowActionItem[] => {
    const state = toApprovalState(readRowValue(row, APPROVAL_STATUS_COLUMN));
    if (!state) return [];
    const actions: RowActionItem[] = [
      { label: 'History', onClick: () => setAuditingRow(row) },
    ];
    const busy = busyRowId === rowId;
    if (isMaker(entityName) && (state === 'DRAFT' || state === 'REJECTED')) {
      actions.push({
        label: state === 'REJECTED' ? 'Resubmit for approval' : 'Submit for approval',
        onClick: () => { void handleSubmitRow(rowId, state === 'REJECTED'); },
        disabled: busy,
      });
    }
    if (isChecker(entityName) && state === 'PENDING') {
      const submittedBy = readRowValue(row, 'submitted_by');
      const ownSubmission = Boolean(user?.userId) && String(submittedBy ?? '') === user?.userId;
      actions.push(
        {
          label: 'Approve',
          onClick: () => { void handleApproveRow(rowId); },
          disabled: busy || ownSubmission,
          title: ownSubmission ? 'You cannot approve your own submission.' : undefined,
        },
        {
          label: 'Reject',
          onClick: () => setRejectingRow(row),
          disabled: busy || ownSubmission,
          title: ownSubmission ? 'You cannot reject your own submission.' : undefined,
          tone: 'danger',
        },
      );
    }
    return actions;
  }, [busyRowId, isMaker, isChecker, user, handleSubmitRow, handleApproveRow]);


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
  // schemaApprovalRequired is the authoritative, data-independent signal (see
  // the fetch above); rowsHaveApprovalColumns/approvalSeen stay as a fallback
  // for the moment a page first mounts, before the schema fetch resolves.
  const approvalEnabled = schemaApprovalRequired || approvalSeen || rowsHaveApprovalColumns(rows);

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
                      extraActions={buildApprovalActions(row, rowId, entityName)}
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

      <RejectDialog
        open={rejectingRow !== null}
        recordLabel={rejectingRow ? `Record #${rejectingRow.id}` : undefined}
        submitting={busyRowId !== null && rejectingRow !== null}
        onCancel={() => setRejectingRow(null)}
        onConfirm={(reason) => { void handleRejectConfirm(reason); }}
      />
      <AuditDrawer
        open={auditingRow !== null}
        target={auditingRow ? targetFor(String(auditingRow.id ?? '')) : null}
        recordLabel={auditingRow ? `Record #${auditingRow.id}` : undefined}
        onClose={() => setAuditingRow(null)}
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
