/**
 * CheckerQueuePage.tsx — Tasks C3.3 / C3.4.
 *
 * Everything awaiting the signed-in checker for one entity, with approve and
 * reject in place.
 *
 * Two behaviours are worth calling out:
 *
 *   Separation of duties is enforced by the backend, which refuses to let a
 *   submitter approve their own record. Rather than hide those rows — leaving
 *   the checker wondering why the queue looks short — they are shown with the
 *   action disabled and an explanation. The queue stays an honest picture of
 *   what is outstanding.
 *
 *   A 409 means the record moved underneath us (another checker got there
 *   first, or the maker withdrew it). That is not an error the user caused, so
 *   it refreshes the queue and says so, rather than showing a failure.
 */
import { useCallback, useEffect, useState } from 'react';
import type { ApprovalTarget } from '@appbana/shared';
import {
  fetchPendingApprovals,
  approveRecord,
  rejectRecord,
  ApprovalConflictError,
} from '@appbana/shared';
import { getRuntimeToken } from './qualifyEntityKey';
import { humanizeHeader, formatDate } from './cell-formatters';
import { ApprovalStatusPill } from './ApprovalStatusPill';
import { isApprovalColumn, readRowValue } from './approval-columns';
import { Button } from './Button';
import { RejectDialog } from './RejectDialog';
import { EmptyState } from './EmptyState';
import { TableSkeleton } from './Skeleton';
import { PageShell } from './PageShell';
import { toast } from './Toaster';
import { useCurrentUser } from './useCurrentUser';

interface Props {
  readonly tenantId: string;
  readonly appId: string;
  readonly entityName: string;
}

type Row = Record<string, unknown>;

/** Columns worth showing in a queue: the business fields, not the plumbing. */
function displayColumns(rows: readonly Row[]): string[] {
  const first = rows[0];
  if (!first) return [];
  return Object.keys(first).filter((k) => {
    const lower = k.toLowerCase();
    return lower !== 'id' && !isApprovalColumn(k);
  });
}

function rowId(row: Row): string {
  const raw = readRowValue(row, 'id');
  return raw == null ? '' : String(raw);
}

export function CheckerQueuePage({ tenantId, appId, entityName }: Readonly<Props>) {
  const { user } = useCurrentUser();
  const [rows, setRows] = useState<Row[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [rejecting, setRejecting] = useState<Row | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const pending = await fetchPendingApprovals({ tenantId, appId, entityName }, getRuntimeToken());
      setRows(Array.isArray(pending) ? pending : []);
    } catch (e) {
      // A 403 here means "not a checker for this entity". That is a legitimate
      // answer, not a failure, and an empty queue communicates it better than
      // an error banner.
      const status = (e as { status?: number })?.status;
      if (status === 403) {
        setRows([]);
      } else {
        setError(e instanceof Error ? e.message : 'Failed to load the approval queue');
      }
    } finally {
      setLoading(false);
    }
  }, [tenantId, appId, entityName]);

  useEffect(() => { void load(); }, [load]);

  function targetFor(row: Row): ApprovalTarget {
    return { tenantId, appId, entityName, rowId: rowId(row) };
  }

  /** True when the caller submitted this row, so the backend will refuse them. */
  function isOwnSubmission(row: Row): boolean {
    const submitter = readRowValue(row, 'submitted_by');
    return Boolean(user?.userId) && String(submitter ?? '') === user?.userId;
  }

  function handleConflict(e: unknown, fallbackTitle: string): boolean {
    if (e instanceof ApprovalConflictError) {
      toast.info('This record already moved on', {
        description: e.message || 'Another checker acted on it first. The queue has been refreshed.',
      });
      void load();
      return true;
    }
    toast.error(fallbackTitle, {
      description: e instanceof Error ? e.message : undefined,
    });
    return false;
  }

  async function handleApprove(row: Row) {
    const id = rowId(row);
    setBusyId(id);
    try {
      await approveRecord(targetFor(row), getRuntimeToken());
      toast.success('Approved', { description: `${entityName} #${id} is now live.` });
      await load();
    } catch (e) {
      handleConflict(e, 'Approve failed');
    } finally {
      setBusyId(null);
    }
  }

  async function handleReject(reason: string) {
    const row = rejecting;
    if (!row) return;
    const id = rowId(row);
    setBusyId(id);
    try {
      await rejectRecord(targetFor(row), getRuntimeToken(), reason);
      setRejecting(null);
      toast.success('Sent back to the maker', {
        description: `${entityName} #${id} was rejected with your reason.`,
      });
      await load();
    } catch (e) {
      // Keep the dialog open on a plain failure so the typed reason survives;
      // close it on a conflict, where retrying the same action is pointless.
      if (handleConflict(e, 'Reject failed')) setRejecting(null);
    } finally {
      setBusyId(null);
    }
  }

  const columns = displayColumns(rows);
  const label = humanizeHeader(entityName);

  return (
    <PageShell
      title={`${label} approvals`}
      subtitle={loading ? undefined : `${rows.length} awaiting your review`}
    >
      {loading && <TableSkeleton columns={4} />}

      {!loading && error && (
        <div className="p-4 rounded-lg border border-red-200 bg-red-50 text-sm text-red-700">
          {error}
          <button type="button" className="ml-3 underline" onClick={() => void load()}>
            Retry
          </button>
        </div>
      )}

      {!loading && !error && rows.length === 0 && (
        <EmptyState
          title="Nothing awaiting approval"
          description={`No ${label} records are pending your review right now.`}
        />
      )}

      {!loading && !error && rows.length > 0 && (
        <div className="rounded-xl bg-white border border-slate-200 shadow-sm overflow-hidden">
          <table className="appbana-table w-full" data-approval-queue={entityName}>
            <thead>
              <tr>
                {columns.map((c) => (
                  <th key={c} scope="col">{humanizeHeader(c)}</th>
                ))}
                <th scope="col">Submitted</th>
                <th scope="col">Status</th>
                <th scope="col"><span className="sr-only">Actions</span></th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => {
                const id = rowId(row);
                const own = isOwnSubmission(row);
                const busy = busyId === id;
                const submittedAt = formatDate(readRowValue(row, 'submitted_at'), 'datetime');
                return (
                  <tr key={id} data-row-id={id}>
                    {columns.map((c) => {
                      const v = row[c];
                      return (
                        <td key={c}>
                          {v == null || v === '' ? <span className="text-slate-400">—</span> : String(v)}
                        </td>
                      );
                    })}
                    <td title={submittedAt.title}>
                      {submittedAt.label || <span className="text-slate-400">—</span>}
                      {readRowValue(row, 'submitted_by') != null && (
                        <span className="block text-xs text-slate-500">
                          by {String(readRowValue(row, 'submitted_by'))}
                        </span>
                      )}
                    </td>
                    <td>
                      <ApprovalStatusPill value={readRowValue(row, 'approval_status')} />
                    </td>
                    <td>
                      <div className="flex gap-2 justify-end">
                        <Button
                          size="sm"
                          variant="secondary"
                          disabled={own || busy}
                          title={own ? 'You submitted this record, so you cannot review it' : undefined}
                          onClick={() => setRejecting(row)}
                        >
                          Reject
                        </Button>
                        <Button
                          size="sm"
                          variant="primary"
                          disabled={own || busy}
                          loading={busy}
                          title={own ? 'You submitted this record, so you cannot review it' : undefined}
                          onClick={() => void handleApprove(row)}
                        >
                          Approve
                        </Button>
                      </div>
                      {own && (
                        <p className="text-xs text-slate-500 text-right mt-1">
                          You submitted this
                        </p>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      <RejectDialog
        open={rejecting !== null}
        recordLabel={rejecting ? `${label} #${rowId(rejecting)}` : undefined}
        submitting={busyId !== null && rejecting !== null}
        onCancel={() => setRejecting(null)}
        onConfirm={(reason) => void handleReject(reason)}
      />
    </PageShell>
  );
}
