/**
 * useEntityRows.ts — Sprint 3 task 3.12.
 *
 * Encapsulates the paginated + refresh-aware fetching that StudioTableLive
 * used to inline. Also listens on the row-lifecycle events dispatched by
 * forms and RowActions (`appbana:row-inserted|updated|deleted`) so any
 * table that mounts this hook stays consistent with the rest of the runtime.
 *
 * Deliberately narrow — no sort/filter yet; those will land alongside the
 * table-header interactions that the current UI does not expose.
 */
import { useCallback, useEffect, useState } from 'react';
import { fetchEntityRows } from '@appbana/shared';
import { qualifyEntityKey, getRuntimeToken } from './qualifyEntityKey';

export interface UseEntityRowsResult {
  readonly rows: Record<string, unknown>[];
  readonly total: number;
  readonly page: number;
  readonly totalPages: number;
  readonly loading: boolean;
  readonly error: string;
  setPage(next: number): void;
  refetch(): void;
}

export function useEntityRows(
  entityKey: string,
  pageSize: number,
): UseEntityRowsResult {
  const [rows, setRows] = useState<Record<string, unknown>[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    if (!entityKey) return;
    setLoading(true);
    setError('');
    try {
      const result = await fetchEntityRows(qualifyEntityKey(entityKey), getRuntimeToken(), {
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
  }, [entityKey, page, pageSize]);

  useEffect(() => { load().catch(() => {}); }, [load]);

  // Sprint 3 task 3.6 — react to row mutations from anywhere in the runtime.
  useEffect(() => {
    if (!entityKey) return;
    const qualified = qualifyEntityKey(entityKey);
    const handler = (e: Event) => {
      const detail = (e as CustomEvent<{ entity?: string }>).detail;
      if (!detail?.entity) return;
      if (detail.entity === entityKey || detail.entity === qualified) {
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
  }, [entityKey, load]);

  const totalPages = Math.max(1, Math.ceil(total / pageSize));

  return {
    rows,
    total,
    page,
    totalPages,
    loading,
    error,
    setPage,
    refetch: () => { load().catch(() => {}); },
  };
}
