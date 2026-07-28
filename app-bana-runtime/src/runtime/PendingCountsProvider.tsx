/**
 * PendingCountsProvider.tsx — Task C3.9.
 *
 * Runs the pending-approval poll exactly once per app shell.
 *
 * C3.7 put `usePendingCounts` inside the sidebar wrapper. The shell renders
 * that wrapper twice — once for the desktop rail and once for the mobile drawer
 * — and both are in the DOM at all times, hidden by CSS rather than unmounted.
 * The result was two independent 30-second loops issuing two count requests per
 * checker entity per tick, forever, on every open tab. Nothing surfaced it
 * because the two loops agreed with each other.
 *
 * Hoisting the poll here makes the number of loops a property of the shell
 * rather than of how many places happen to render a sidebar.
 */
import { createContext, useContext } from 'react';
import type { ReactNode } from 'react';
import { useCurrentUser } from './useCurrentUser';
import { usePendingCounts } from './usePendingCounts';

const PendingCountsContext = createContext<Record<string, number>>({});

export function PendingCountsProvider(props: Readonly<{
  tenantId: string | undefined;
  appId: string | undefined;
  children: ReactNode;
}>) {
  const { tenantId, appId, children } = props;
  const { checkerEntities } = useCurrentUser();
  const counts = usePendingCounts(tenantId, appId, checkerEntities);
  return (
    <PendingCountsContext.Provider value={counts}>
      {children}
    </PendingCountsContext.Provider>
  );
}

/** Pending counts by entity name. `{}` outside a provider, which renders no badges. */
export function usePendingCountsValue(): Record<string, number> {
  return useContext(PendingCountsContext);
}
