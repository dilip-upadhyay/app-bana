/**
 * PageActions.tsx — Sprint 2 Task 2.8.
 *
 * Renders the context-appropriate button set for the current page into the
 * PageShell `actions` slot:
 *
 *   List page   →  "New {Entity}" primary button that navigates to the
 *                  matching Add page (via RuntimeNavigationContext + the
 *                  `findAddPageForEntity` helper). If no Add page exists
 *                  in the app, we render nothing rather than a broken CTA.
 *
 *   Detail page →  "Edit" + "Delete" button pair. Detail pages in the
 *                  current runtime are already editable forms, and no
 *                  record-selection layer exists yet, so both buttons
 *                  dispatch custom events (`appbana:page:edit`,
 *                  `appbana:page:delete`) that future record-aware code
 *                  can hook into, and fall back to a toast so the user
 *                  gets clear feedback today.
 *
 *   Anything else →  no actions.
 *
 * The component is deliberately dumb — no data fetching, no record ids.
 * That work will land alongside a proper detail-view route.
 */
import type { PageMeta } from '@appbana/shared';
import {
  classifyKind,
  extractEntity,
  findAddPageForEntity,
} from './page-classifier';
import { useRuntimeNavigation } from './runtime-navigation';
import { toast } from './Toaster';

export interface PageActionsProps {
  readonly page: PageMeta;
}

/** Custom-event names emitted by the Detail-page action buttons. */
export const PAGE_EDIT_EVENT = 'appbana:page:edit';
export const PAGE_DELETE_EVENT = 'appbana:page:delete';

interface PageActionsInternals {
  readonly kind: ReturnType<typeof classifyKind>;
  readonly entity: string | null;
}

/** Pure helper — pulled out so the classification path is unit-testable. */
export function classifyPageActions(page: PageMeta): PageActionsInternals {
  const kind = classifyKind(page.name);
  const entity = extractEntity(page.name, kind);
  return { kind, entity };
}

export function PageActions({ page }: Readonly<PageActionsProps>) {
  const { kind, entity } = classifyPageActions(page);
  const nav = useRuntimeNavigation();

  if (kind === 'list' && entity) {
    const addPage = nav ? findAddPageForEntity(entity, nav.pages) : null;
    if (!addPage) return null;
    return (
      <button
        type="button"
        className="appbana-button"
        onClick={() => nav?.navigateToPage(addPage)}
      >
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.75"
          strokeLinecap="round"
          strokeLinejoin="round"
          className="w-4 h-4"
          aria-hidden="true"
        >
          <line x1="12" y1="5" x2="12" y2="19" />
          <line x1="5" y1="12" x2="19" y2="12" />
        </svg>
        New {entity}
      </button>
    );
  }

  if (kind === 'detail') {
    const label = entity ?? 'record';
    return (
      <>
        <button
          type="button"
          className="appbana-button secondary"
          onClick={() => {
            window.dispatchEvent(
              new CustomEvent(PAGE_EDIT_EVENT, { detail: { pageId: page.id, entity } }),
            );
            toast.info('You are already in edit mode');
          }}
        >
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.75"
            strokeLinecap="round"
            strokeLinejoin="round"
            className="w-4 h-4"
            aria-hidden="true"
          >
            <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
            <path d="M18.5 2.5a2.121 2.121 0 1 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
          </svg>
          Edit
        </button>
        <button
          type="button"
          className="appbana-button danger"
          onClick={() => {
            window.dispatchEvent(
              new CustomEvent(PAGE_DELETE_EVENT, { detail: { pageId: page.id, entity } }),
            );
            toast.info(`Deleting ${label} needs a selected record`, {
              description:
                'Detail-view record wiring is not implemented yet — this button is a placeholder for the future record-aware route.',
            });
          }}
        >
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.75"
            strokeLinecap="round"
            strokeLinejoin="round"
            className="w-4 h-4"
            aria-hidden="true"
          >
            <polyline points="3 6 5 6 21 6" />
            <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6" />
            <path d="M10 11v6" />
            <path d="M14 11v6" />
            <path d="M9 6V4a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v2" />
          </svg>
          Delete
        </button>
      </>
    );
  }

  return null;
}
