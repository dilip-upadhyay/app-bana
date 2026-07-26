/**
 * runtime-navigation.tsx — React context that lets any renderer child ask the
 * shell to switch pages. Used by the Sprint 2 Task 2.3 empty-state CTA
 * ("Add {Entity}") to route the user to the matching Add page without
 * plumbing setCurrentPage through every intermediate component.
 */
import { createContext, useContext, useMemo, type ReactNode } from 'react';
import type { PageMeta } from '@appbana/shared';

export interface RuntimeNavigation {
  readonly pages: readonly PageMeta[];
  readonly navigateToPage: (page: PageMeta) => void;
}

const RuntimeNavigationContext = createContext<RuntimeNavigation | null>(null);

export function RuntimeNavigationProvider({
  pages,
  navigateToPage,
  children,
}: Readonly<{
  pages: readonly PageMeta[];
  navigateToPage: (page: PageMeta) => void;
  children: ReactNode;
}>) {
  const value = useMemo<RuntimeNavigation>(
    () => ({ pages, navigateToPage }),
    [pages, navigateToPage],
  );
  return (
    <RuntimeNavigationContext.Provider value={value}>
      {children}
    </RuntimeNavigationContext.Provider>
  );
}

/** Returns the navigation handle, or `null` when rendered outside the shell. */
export function useRuntimeNavigation(): RuntimeNavigation | null {
  return useContext(RuntimeNavigationContext);
}
