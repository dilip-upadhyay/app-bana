/**
 * RuntimeSidebar.tsx — Left navigation rail for the deployed runtime.
 *
 * Sprint 2 Task 2.2 rewrite:
 *   - Width bumped to w-64 (was w-60) so page labels rarely truncate.
 *   - Pages are auto-grouped by inferred entity: "Add Customer",
 *     "Customer List", "Customer Detail" all cluster under a single
 *     "Customers" section header.
 *   - Pages that don't cluster (Dashboard, Settings, standalone reports)
 *     go under an "Other" section at the bottom.
 *   - Every truncatable label carries a native `title` tooltip so users
 *     can hover to reveal the full name.
 *   - Icons follow the plan: List → list-lines, Add → plus, Detail → eye,
 *     with home / chart / gear / doc fallbacks. Zero external icon
 *     dependency — inline SVGs matching Lucide's stroke conventions.
 */
import type { PageMeta } from '@appbana/shared';
import type { ReactNode } from 'react';
import {
  classifyKind,
  extractEntity,
  pluralize,
  singularize,
  type PageKind,
} from './page-classifier';
import { formatBadgeCount } from './usePendingCounts';

interface RuntimeSidebarProps {
  readonly pages: PageMeta[];
  readonly currentPageId: string | null;
  readonly onSelect: (page: PageMeta) => void;
  /** Optional close handler — supplied when rendered inside the mobile drawer. */
  readonly onClose?: () => void;
  /**
   * Task C3.3 — bare entity names the signed-in user may check. Rendered as an
   * "Approvals" section above the page groups: reviewing other people's work is
   * a distinct job from browsing your own data, and burying it inside the
   * entity groups would make an outstanding queue easy to miss.
   */
  readonly checkerEntities?: readonly string[];
  readonly currentQueueEntity?: string | null;
  readonly onSelectQueue?: (entityName: string) => void;
  /** Task C3.7 — pending count per entity, keyed as in `checkerEntities`. */
  readonly pendingCounts?: Readonly<Record<string, number>>;
}

/** Zero-dep icons. 24×24 viewbox, currentColor stroke, Lucide conventions. */
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
  eye: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"
         strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M1.5 12S5.5 5 12 5s10.5 7 10.5 7-4 7-10.5 7S1.5 12 1.5 12z" />
      <circle cx="12" cy="12" r="3" />
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
  check: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"
         strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M9 11l3 3 8-8" />
      <path d="M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h11" />
    </svg>
  ),
} as const;

function iconForKind(kind: PageKind): ReactNode {
  switch (kind) {
    case 'list':      return ICONS.list;
    case 'add':       return ICONS.plus;
    case 'detail':    return ICONS.eye;
    case 'dashboard': return ICONS.home;
    case 'chart':     return ICONS.chart;
    case 'settings':  return ICONS.gear;
    default:          return ICONS.doc;
  }
}

interface PageGroup {
  entity: string | null; // null == "Other"
  label: string;
  pages: PageMeta[];
}

/** Cluster pages into entity groups, preserving first-appearance order. */
function groupPages(pages: PageMeta[]): PageGroup[] {
  const byKey = new Map<string, PageGroup>();
  const others: PageMeta[] = [];
  const orderedKeys: string[] = [];

  for (const p of pages) {
    const kind = classifyKind(p.name);
    const raw = extractEntity(p.name, kind);
    if (!raw) {
      others.push(p);
      continue;
    }
    const key = singularize(raw);
    if (!byKey.has(key)) {
      byKey.set(key, { entity: key, label: pluralize(key), pages: [] });
      orderedKeys.push(key);
    }
    byKey.get(key)!.pages.push(p);
  }

  // Prefer list → add → detail ordering inside each group.
  const kindRank: Record<PageKind, number> = {
    list: 0, add: 1, detail: 2, dashboard: 3, chart: 4, settings: 5, other: 6,
  };
  for (const g of byKey.values()) {
    g.pages.sort((a, b) => kindRank[classifyKind(a.name)] - kindRank[classifyKind(b.name)]);
  }

  const groups: PageGroup[] = orderedKeys.map((k) => byKey.get(k)!);
  if (others.length) groups.push({ entity: null, label: 'Other', pages: others });
  return groups;
}

export function RuntimeSidebar({
  pages,
  currentPageId,
  onSelect,
  onClose,
  checkerEntities = [],
  currentQueueEntity = null,
  onSelectQueue,
  pendingCounts = {},
}: RuntimeSidebarProps) {
  const groups = groupPages(pages);
  const showHeaders = groups.length > 1 || (groups.length === 1 && groups[0].entity !== null);
  const showApprovals = checkerEntities.length > 0 && Boolean(onSelectQueue);

  return (
    <nav className="appbana-sidebar" aria-label="App pages">
      <div className="flex flex-col gap-4 p-3">
        {showApprovals && (
          <section className="flex flex-col gap-0.5" aria-label="Approvals">
            <h3 className="appbana-sidebar-section" title="Approvals">Approvals</h3>
            <ul className="flex flex-col gap-0.5">
              {checkerEntities.map((entity) => {
                const active = currentQueueEntity === entity;
                const pending = pendingCounts[entity] ?? 0;
                const badge = formatBadgeCount(pending);
                // The count belongs in the accessible name, not only in a
                // decorative pill: a screen reader user should not have to open
                // the queue to learn there is nothing in it.
                const label = `${pluralize(singularize(entity))} to review`;
                const ariaLabel = pending > 0 ? `${label}, ${pending} pending` : label;
                return (
                  <li key={entity}>
                    <button
                      type="button"
                      onClick={() => {
                        onSelectQueue?.(entity);
                        onClose?.();
                      }}
                      className={`appbana-sidebar-link ${active ? 'appbana-sidebar-link-active' : ''}`}
                      aria-current={active ? 'page' : undefined}
                      aria-label={ariaLabel}
                      title={ariaLabel}
                      data-approval-queue-link={entity}
                      data-pending-count={pending}
                    >
                      <span className="appbana-sidebar-icon">{ICONS.check}</span>
                      <span className="truncate">{label}</span>
                      {badge && (
                        <span className="appbana-nav-badge" aria-hidden="true">{badge}</span>
                      )}
                    </button>
                  </li>
                );
              })}
            </ul>
          </section>
        )}
        {groups.length === 0 && (
          <p className="px-3 py-2 text-xs text-gray-400">No pages yet.</p>
        )}
        {groups.map((group) => (
          <section
            key={group.entity ?? '__other__'}
            className="flex flex-col gap-0.5"
            aria-label={group.label}
          >
            {showHeaders && (
              <h3 className="appbana-sidebar-section" title={group.label}>
                {group.label}
              </h3>
            )}
            <ul className="flex flex-col gap-0.5">
              {group.pages.map((p) => {
                const active = p.id === currentPageId;
                const kind = classifyKind(p.name);
                const label = p.name ?? p.id;
                return (
                  <li key={p.id}>
                    {/* Sprint 3 task 3.11(b) — aria-label lets AT announce
                        the destination; native title kept as a secondary
                        hover-only hint. In collapsed icon-rail mode the CSS
                        hides the label span, and screen readers still get
                        the aria-label. */}
                    <button
                      type="button"
                      onClick={() => {
                        onSelect(p);
                        onClose?.();
                      }}
                      className={`appbana-sidebar-link ${active ? 'appbana-sidebar-link-active' : ''}`}
                      aria-current={active ? 'page' : undefined}
                      aria-label={label}
                      title={label}
                    >
                      <span className="appbana-sidebar-icon">{iconForKind(kind)}</span>
                      <span className="truncate">{label}</span>
                    </button>
                  </li>
                );
              })}
            </ul>
          </section>
        ))}
      </div>
    </nav>
  );
}

// Exported for unit tests; not part of the component's public API.
export const __test__ = {
  classifyKind,
  extractEntity,
  pluralize,
  singularize,
  groupPages,
};
