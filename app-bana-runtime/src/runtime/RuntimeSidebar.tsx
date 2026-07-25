/**
 * RuntimeSidebar.tsx — Left navigation rail for the deployed runtime.
 *
 * Standard business-app layout convention (Material 3 Navigation Rail /
 * MUI Dashboard / Retool / Airtable). Renders one link per page with an
 * inferred icon so users can distinguish "list" vs "add" vs "dashboard"
 * pages at a glance without depending on an icon library.
 */
import type { PageMeta } from '@appbana/shared';
import type { ReactNode } from 'react';

interface RuntimeSidebarProps {
  readonly pages: PageMeta[];
  readonly currentPageId: string | null;
  readonly onSelect: (page: PageMeta) => void;
  /** Optional close handler — supplied when rendered inside the mobile drawer. */
  readonly onClose?: () => void;
}

/** Zero-dep icons. All 24×24, currentColor stroke, matches Tailwind sizing. */
const ICONS = {
  list: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"
         strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <line x1="8" y1="6"  x2="21" y2="6" />
      <line x1="8" y1="12" x2="21" y2="12" />
      <line x1="8" y1="18" x2="21" y2="18" />
      <circle cx="3.5" cy="6"  r="1.5" fill="currentColor" stroke="none" />
      <circle cx="3.5" cy="12" r="1.5" fill="currentColor" stroke="none" />
      <circle cx="3.5" cy="18" r="1.5" fill="currentColor" stroke="none" />
    </svg>
  ),
  plus: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"
         strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="12" cy="12" r="9" />
      <line x1="12" y1="8"  x2="12" y2="16" />
      <line x1="8"  y1="12" x2="16" y2="12" />
    </svg>
  ),
  home: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"
         strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M3 11l9-8 9 8" />
      <path d="M5 10v10a1 1 0 001 1h4v-6h4v6h4a1 1 0 001-1V10" />
    </svg>
  ),
  chart: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"
         strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <line x1="4" y1="20" x2="20" y2="20" />
      <rect x="6"  y="12" width="3" height="6" rx="0.5" />
      <rect x="11" y="7"  width="3" height="11" rx="0.5" />
      <rect x="16" y="14" width="3" height="4" rx="0.5" />
    </svg>
  ),
  gear: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"
         strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="12" cy="12" r="3" />
      <path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 01-2.83 2.83l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09a1.65 1.65 0 00-1-1.51 1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83-2.83l.06-.06A1.65 1.65 0 004.6 15a1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 012.83-2.83l.06.06A1.65 1.65 0 009 4.6a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 2.83l-.06.06A1.65 1.65 0 0019.4 9c.36 0 .7.11 1 .3" />
    </svg>
  ),
  doc: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"
         strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M14 3H7a2 2 0 00-2 2v14a2 2 0 002 2h10a2 2 0 002-2V8z" />
      <polyline points="14 3 14 8 19 8" />
      <line x1="9"  y1="13" x2="15" y2="13" />
      <line x1="9"  y1="17" x2="15" y2="17" />
    </svg>
  ),
} as const;

function iconForPage(name: string | undefined): ReactNode {
  const n = (name ?? '').toLowerCase();
  if (/\b(list|table|browse|all)\b/.test(n)) return ICONS.list;
  if (/\b(add|new|create|register|onboard)\b/.test(n)) return ICONS.plus;
  if (/\b(dashboard|home|overview)\b/.test(n)) return ICONS.home;
  if (/\b(report|analytics|chart|metric|stat)\b/.test(n)) return ICONS.chart;
  if (/\b(setting|config|admin|preference)\b/.test(n)) return ICONS.gear;
  return ICONS.doc;
}

export function RuntimeSidebar({
  pages,
  currentPageId,
  onSelect,
  onClose,
}: RuntimeSidebarProps) {
  return (
    <nav className="appbana-sidebar" aria-label="App pages">
      <ul className="flex flex-col gap-0.5 p-3">
        {pages.map((p) => {
          const active = p.id === currentPageId;
          return (
            <li key={p.id}>
              <button
                type="button"
                onClick={() => {
                  onSelect(p);
                  onClose?.();
                }}
                className={`appbana-sidebar-link ${active ? 'appbana-sidebar-link-active' : ''}`}
                aria-current={active ? 'page' : undefined}
              >
                <span className="appbana-sidebar-icon">{iconForPage(p.name)}</span>
                <span className="truncate">{p.name}</span>
              </button>
            </li>
          );
        })}
        {pages.length === 0 && (
          <li className="px-3 py-2 text-xs text-gray-400">No pages yet.</li>
        )}
      </ul>
    </nav>
  );
}
