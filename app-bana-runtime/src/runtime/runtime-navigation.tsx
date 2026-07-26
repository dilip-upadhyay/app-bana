/**
 * runtime-navigation.tsx — React context that lets any renderer child ask the
 * shell to switch pages. Used by the Sprint 2 Task 2.3 empty-state CTA
 * ("Add {Entity}") to route the user to the matching Add page without
 * plumbing setCurrentPage through every intermediate component.
 *
 * Sprint 3 task 3.3 — Added `navigateToRecord(page, recordId)` so table rows
 * and PageActions can route the user to a detail view with a specific record
 * pre-selected. `selectedRecordId` is the parallel state the shell exposes;
 * the DetailPage reads it and hydrates the form.
 */
import { createContext, useContext, useMemo, type ReactNode } from 'react';
import type { PageMeta } from '@appbana/shared';

export interface RuntimeNavigation {
  readonly pages: readonly PageMeta[];
  readonly navigateToPage: (page: PageMeta) => void;
  /**
   * Sprint 3 task 3.3 — Route to a detail page and hydrate it with a
   * specific record. Callers pass either the detail page directly or the
   * entity name (the shell resolves the matching detail page).
   */
  readonly navigateToRecord: (page: PageMeta, recordId: string) => void;
  /** Current selected record id, or null when viewing a list/form page. */
  readonly selectedRecordId: string | null;
}

const RuntimeNavigationContext = createContext<RuntimeNavigation | null>(null);

export function RuntimeNavigationProvider({
  pages,
  navigateToPage,
  navigateToRecord,
  selectedRecordId = null,
  children,
}: Readonly<{
  pages: readonly PageMeta[];
  navigateToPage: (page: PageMeta) => void;
  /**
   * Optional so pre-Sprint-3 call sites (unit tests) keep compiling.
   * When omitted, callers of `navigateToRecord` become a no-op.
   */
  navigateToRecord?: (page: PageMeta, recordId: string) => void;
  selectedRecordId?: string | null;
  children: ReactNode;
}>) {
  const value = useMemo<RuntimeNavigation>(
    () => ({
      pages,
      navigateToPage,
      navigateToRecord: navigateToRecord ?? (() => { /* no-op */ }),
      selectedRecordId,
    }),
    [pages, navigateToPage, navigateToRecord, selectedRecordId],
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
