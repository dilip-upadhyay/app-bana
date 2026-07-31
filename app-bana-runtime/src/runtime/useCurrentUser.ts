/**
 * useCurrentUser.ts — Task C3.3.
 *
 * Loads the signed-in user's identity and per-entity workflow roles once per
 * app context and shares the result through React context, so the sidebar, the
 * checker queue and any row-level action controls all agree about what the
 * caller may do without each firing its own request.
 *
 * Failure is non-fatal by design. If the lookup fails the user is treated as
 * holding no roles: approval affordances disappear, but the rest of the app
 * keeps working. The backend is the actual authority — every approve/reject is
 * re-checked server-side — so a false negative here costs a feature, while a
 * false positive would only produce a confusing 403 at click time.
 */
import { createContext, useContext, useEffect, useState, createElement, type ReactNode } from 'react';
import type { CurrentUser, EntityRoleGrant } from '@appbana/shared';
import { fetchCurrentUser } from '@appbana/shared';

export interface CurrentUserState {
  readonly user: CurrentUser | null;
  readonly loading: boolean;
  /**
   * Bare entity names the caller may check, sorted for stable nav ordering.
   * Two-level checker chain: an entity where the caller only holds CHECKER_L2
   * (not CHECKER) appears here as `"{entityName}::L2"` so existing consumers
   * that expect a flat `string[]` don't need a type change — they parse/strip
   * the suffix as needed (see `parseCheckerEntityKey` in approval-columns.ts).
   */
  readonly checkerEntities: string[];
  readonly isChecker: (entityName: string) => boolean;
  readonly isCheckerL2: (entityName: string) => boolean;
  readonly isMaker: (entityName: string) => boolean;
}

const EMPTY: CurrentUserState = {
  user: null,
  loading: false,
  checkerEntities: [],
  isChecker: () => false,
  isCheckerL2: () => false,
  isMaker: () => false,
};

const CurrentUserContext = createContext<CurrentUserState>(EMPTY);

/** Entity-name lookup is case-insensitive: page metadata and role grants
 *  don't always agree on casing, and a mismatch would silently hide a queue. */
function findGrant(
  roles: Record<string, EntityRoleGrant> | undefined,
  entityName: string
): EntityRoleGrant | undefined {
  if (!roles || !entityName) return undefined;
  const direct = roles[entityName];
  if (direct) return direct;
  const lower = entityName.toLowerCase();
  for (const key of Object.keys(roles)) {
    if (key.toLowerCase() === lower) return roles[key];
  }
  return undefined;
}

export function buildCurrentUserState(user: CurrentUser | null, loading: boolean): CurrentUserState {
  const roles = user?.entityRoles ?? {};
  const l1Entities = Object.keys(roles).filter((name) => roles[name]?.isChecker);
  const l2Entities = Object.keys(roles)
    .filter((name) => roles[name]?.isCheckerL2)
    .map((name) => `${name}::L2`);
  const checkerEntities = [...l1Entities, ...l2Entities].sort((a, b) => a.localeCompare(b));

  return {
    user,
    loading,
    checkerEntities,
    isChecker: (entityName: string) => Boolean(findGrant(roles, entityName)?.isChecker),
    isCheckerL2: (entityName: string) => Boolean(findGrant(roles, entityName)?.isCheckerL2),
    isMaker: (entityName: string) => Boolean(findGrant(roles, entityName)?.isMaker),
  };
}

interface ProviderProps {
  readonly token: string | null;
  readonly tenantId: string | undefined;
  readonly appId: string | undefined;
  readonly children: ReactNode;
}

export function CurrentUserProvider({ token, tenantId, appId, children }: Readonly<ProviderProps>) {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!token || !appId) {
      setUser(null);
      return;
    }
    let cancelled = false;
    setLoading(true);
    fetchCurrentUser(token, { tenantId, appId })
      .then((u) => { if (!cancelled) setUser(u); })
      .catch(() => { if (!cancelled) setUser(null); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [token, tenantId, appId]);

  return createElement(
    CurrentUserContext.Provider,
    { value: buildCurrentUserState(user, loading) },
    children
  );
}

export function useCurrentUser(): CurrentUserState {
  return useContext(CurrentUserContext);
}
