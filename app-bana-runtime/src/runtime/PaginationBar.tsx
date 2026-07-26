/**
 * PaginationBar.tsx — Sprint 3 task 3.12.
 *
 * Extracted from StudioTableLive so future callers (list pages, drawers,
 * combobox result panes) can reuse the same layout + a11y treatment.
 */
import type { CSSProperties } from 'react';

export interface PaginationBarProps {
  readonly page: number;
  readonly totalPages: number;
  readonly onPrev: () => void;
  readonly onNext: () => void;
  readonly style?: CSSProperties;
  readonly className?: string;
}

export function PaginationBar({
  page,
  totalPages,
  onPrev,
  onNext,
  style,
  className = '',
}: Readonly<PaginationBarProps>) {
  if (totalPages <= 1) return null;
  return (
    <nav
      className={`flex items-center justify-between px-5 py-3 border-t border-slate-100 text-xs text-slate-500 ${className}`.trim()}
      style={style}
      aria-label="Pagination"
    >
      <span aria-live="polite">Page {page} of {totalPages}</span>
      <div className="flex gap-2">
        <button
          type="button"
          onClick={onPrev}
          disabled={page === 1}
          className="px-2 py-1 rounded border border-slate-200 disabled:opacity-40 hover:bg-slate-100"
          aria-label="Previous page"
        >
          ←
        </button>
        <button
          type="button"
          onClick={onNext}
          disabled={page === totalPages}
          className="px-2 py-1 rounded border border-slate-200 disabled:opacity-40 hover:bg-slate-100"
          aria-label="Next page"
        >
          →
        </button>
      </div>
    </nav>
  );
}
