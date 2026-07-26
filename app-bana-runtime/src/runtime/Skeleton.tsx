/**
 * Skeleton.tsx — Zero-dependency loading-shimmer primitive.
 *
 * Sprint 2 Task 2.4. API mirrors shadcn/ui's `<Skeleton />`: a rectangular
 * placeholder that pulses to signal loading. Width / height / rounding come
 * from Tailwind utility classes on the caller so the same primitive can
 * mock a text line, an input, or a table cell.
 */
import type { CSSProperties, ReactElement } from 'react';

export interface SkeletonProps {
  readonly className?: string;
  readonly style?: CSSProperties;
  /** Accessible label announced to screen readers. Defaults to "Loading". */
  readonly ariaLabel?: string;
}

export function Skeleton({
  className = '',
  style,
  ariaLabel = 'Loading',
}: Readonly<SkeletonProps>): ReactElement {
  return (
    <span
      className={`appbana-skeleton ${className}`}
      style={style}
      role="status"
      aria-live="polite"
      aria-label={ariaLabel}
    />
  );
}

/**
 * TableSkeleton — five shimmer rows sized to a supplied column count.
 * Used by StudioTableLive during the initial fetch.
 */
export function TableSkeleton({
  columns,
  rows = 5,
}: Readonly<{ columns: number; rows?: number }>): ReactElement {
  const colCount = Math.max(columns, 3);
  return (
    <div className="appbana-table-skeleton" aria-hidden="true">
      {/* Header row */}
      <div className="appbana-table-skeleton-row appbana-table-skeleton-head">
        {Array.from({ length: colCount }).map((_, j) => (
          <Skeleton key={`skel-head-${j}`} className="h-3 w-24 rounded" />
        ))}
      </div>
      {/* Body rows */}
      {Array.from({ length: rows }).map((_, i) => (
        <div key={`skel-row-${i}`} className="appbana-table-skeleton-row">
          {Array.from({ length: colCount }).map((_, j) => (
            <Skeleton
              key={`skel-cell-${i}-${j}`}
              className={`h-4 rounded ${j === 0 ? 'w-32' : 'w-full max-w-[10rem]'}`}
            />
          ))}
        </div>
      ))}
    </div>
  );
}

/**
 * FormSkeleton — label + input pairs. Used inside forms while a
 * dependent schema (or reference dropdown) is still loading.
 */
export function FormSkeleton({
  fields = 4,
}: Readonly<{ fields?: number }>): ReactElement {
  return (
    <div className="appbana-form-skeleton" aria-hidden="true">
      {Array.from({ length: fields }).map((_, i) => (
        <div key={`skel-field-${i}`} className="appbana-form-skeleton-field">
          <Skeleton className="h-3 w-24 rounded" />
          <Skeleton className="h-9 w-full rounded-md" />
        </div>
      ))}
    </div>
  );
}

/**
 * AppLoadingSkeleton — full-page shell skeleton shown while the app
 * metadata is being fetched. Mocks the appbar, sidebar, and main pane.
 */
export function AppLoadingSkeleton(): ReactElement {
  return (
    <div
      className="appbana-app-skeleton"
      role="status"
      aria-live="polite"
      aria-label="Loading app"
    >
      {/* Appbar */}
      <div className="appbana-app-skeleton-bar">
        <Skeleton className="h-6 w-6 rounded md:hidden" />
        <Skeleton className="h-4 w-40 rounded" />
      </div>
      <div className="appbana-app-skeleton-body">
        {/* Sidebar */}
        <aside className="appbana-app-skeleton-side">
          <Skeleton className="h-3 w-16 rounded" />
          <Skeleton className="h-8 w-full rounded-md" />
          <Skeleton className="h-8 w-full rounded-md" />
          <Skeleton className="h-8 w-11/12 rounded-md" />
          <Skeleton className="h-3 w-16 rounded mt-4" />
          <Skeleton className="h-8 w-full rounded-md" />
          <Skeleton className="h-8 w-10/12 rounded-md" />
        </aside>
        {/* Main pane */}
        <main className="appbana-app-skeleton-main">
          <Skeleton className="h-6 w-56 rounded" />
          <Skeleton className="h-4 w-72 rounded mt-2" />
          <div className="appbana-app-skeleton-card">
            <TableSkeleton columns={5} />
          </div>
        </main>
      </div>
    </div>
  );
}
