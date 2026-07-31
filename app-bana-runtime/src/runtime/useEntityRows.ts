/**
 * useEntityRows.ts — Sprint 3 task 3.12.
 *
 * Encapsulates the paginated + refresh-aware fetching that StudioTableLive
 * used to inline. Also listens on the row-lifecycle events dispatched by
 * forms and RowActions (`appbana:row-inserted|updated|deleted`) so any
 * table that mounts this hook stays consistent with the rest of the runtime.
 *
 * H3 hardening — accepts optional `extraParams` that get merged into the
 * fetch query. When the params object changes, page is reset to 1 so
 * stale offsets don't hide fresh rows.
 */
import { useCallback, useEffect, useRef, useState } from 'react';
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
  extraParams?: Record<string, string | number>,
): UseEntityRowsResult {
  const [rows, setRows] = useState<Record<string, unknown>[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // Stringify params so we can use it as an effect dependency and reset the
  // page whenever the caller's filters actually change (React would otherwise
  // treat a new object literal every render as "different").
  const paramsKey = JSON.stringify(extraParams ?? {});
  const paramsKeyPrev = useRef(paramsKey);
  useEffect(() => {
    if (paramsKeyPrev.current !== paramsKey) {
      paramsKeyPrev.current = paramsKey;
      setPage(1);
    }
  }, [paramsKey]);

  // Guards against out-of-order responses: rapid filter/sort/page changes each
  // start their own fetch, and nothing guarantees they resolve in order. A slow
  // older request settling last would otherwise overwrite the UI with stale
  // rows — e.g. clearing a filter but keeping the old filtered count.
  const requestIdRef = useRef(0);

  const load = useCallback(async () => {
    if (!entityKey) return;
    const requestId = ++requestIdRef.current;
    setLoading(true);
    setError('');
    try {
      const params: Record<string, string | number> = {
        ...(extraParams ?? {}),
        limit: pageSize,
        offset: (page - 1) * pageSize,
      };
      const result = await fetchEntityRows(qualifyEntityKey(entityKey), getRuntimeToken(), params);
      if (requestIdRef.current !== requestId) return; // a newer request has since been issued — ignore this stale response
      setRows(result.rows);
      setTotal(result.total);
    } catch (e) {
      if (requestIdRef.current !== requestId) return;
      setError(e instanceof Error ? e.message : 'Failed to load data');
    } finally {
      if (requestIdRef.current === requestId) setLoading(false);
    }
    // paramsKey is included so filter changes trigger a fetch even when the
    // caller passes a fresh object literal each render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [entityKey, page, pageSize, paramsKey]);

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
