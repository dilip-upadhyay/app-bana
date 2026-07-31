/**
 * usePendingCounts.ts — Task C3.7.
 *
 * Polls how many records await the signed-in checker, per entity, so the nav
 * can show a badge.
 *
 * Design notes:
 *
 *   The count comes from a dedicated `countOnly` request rather than the queue
 *   endpoint. This polls forever in the background; fetching up to 500 fully
 *   materialised rows per entity per tick just to read `.length` would be a
 *   steady, pointless load on every session.
 *
 *   Polling pauses while the tab is hidden. A badge nobody can see is not worth
 *   a database round trip, and without this a forgotten background tab keeps
 *   querying indefinitely.
 *
 *   A failed poll leaves the previous counts in place rather than zeroing them.
 *   Showing "0 pending" because of a transient network error is worse than
 *   showing a slightly stale number: it actively tells the checker there is
 *   nothing to do.
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { fetchPendingApprovalCount } from '@appbana/shared';
import { getRuntimeToken } from './qualifyEntityKey';
import { parseCheckerEntityKey } from './approval-columns';

export const PENDING_POLL_MS = 30_000;

export type PendingCounts = Readonly<Record<string, number>>;

/** Total across every entity, for a single "N pending" badge. */
export function totalPending(counts: PendingCounts): number {
  return Object.values(counts).reduce((sum, n) => sum + (Number.isFinite(n) ? n : 0), 0);
}

/**
 * Format a count for a badge. Large numbers are capped so a long queue cannot
 * stretch the nav; the exact figure is on the queue page itself.
 */
export function formatBadgeCount(n: number): string {
  if (n <= 0) return '';
  return n > 99 ? '99+' : String(n);
}

export function usePendingCounts(
  tenantId: string | undefined,
  appId: string | undefined,
  entityNames: readonly string[]
): PendingCounts {
  const [counts, setCounts] = useState<PendingCounts>({});

  // Join the names so the effect depends on their content, not on the array
  // identity — callers rebuild this list on every render.
  const key = entityNames.join('\u0000');

  const cancelled = useRef(false);

  const poll = useCallback(async () => {
    if (!tenantId || !appId) return;
    const names = key ? key.split('\u0000') : [];
    if (names.length === 0) {
      setCounts({});
      return;
    }
    const token = getRuntimeToken();
    if (!token) return;

    const settled = await Promise.allSettled(
      names.map((key) => {
        const { entityName, level } = parseCheckerEntityKey(key);
        return fetchPendingApprovalCount({ tenantId, appId, entityName }, token, level)
          .then((count) => [key, count] as const);
      })
    );
    if (cancelled.current) return;

    const next: Record<string, number> = {};
    let anySucceeded = false;
    for (const r of settled) {
      if (r.status === 'fulfilled') {
        next[r.value[0]] = r.value[1];
        anySucceeded = true;
      }
    }
    // Merge rather than replace: an entity whose poll failed keeps its previous
    // count instead of vanishing from the badge.
    if (anySucceeded) setCounts((prev) => ({ ...prev, ...next }));
  }, [tenantId, appId, key]);

  useEffect(() => {
    cancelled.current = false;
    void poll();

    const timer = setInterval(() => {
      if (typeof document !== 'undefined' && document.hidden) return;
      void poll();
    }, PENDING_POLL_MS);

    // Refresh immediately on return to the tab, so a checker coming back does
    // not stare at a number up to 30s stale.
    const onVisible = () => {
      if (typeof document !== 'undefined' && !document.hidden) void poll();
    };
    if (typeof document !== 'undefined') {
      document.addEventListener('visibilitychange', onVisible);
    }

    return () => {
      cancelled.current = true;
      clearInterval(timer);
      if (typeof document !== 'undefined') {
        document.removeEventListener('visibilitychange', onVisible);
      }
    };
  }, [poll]);

  return counts;
}
