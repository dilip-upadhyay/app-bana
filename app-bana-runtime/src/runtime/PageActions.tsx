/**
 * PageActions.tsx — Sprint 2 Task 2.8 + Sprint 3 tasks 3.6 + 3.8.
 *
 * Renders the context-appropriate button set for the current page into the
 * PageShell `actions` slot:
 *
 *   List page   →  "New {Entity}" primary button that navigates to the
 *                  matching Add page (via RuntimeNavigationContext + the
 *                  `findAddPageForEntity` helper). If no Add page exists
 *                  in the app, we render nothing rather than a broken CTA.
 *
 *   Detail page →  When the user opened the page directly (no selected
 *                  record) we render "Edit" + "Delete" as placeholders that
 *                  toast a hint — DetailPage owns the real edit/delete flow
 *                  once a record is selected via `navigateToRecord`, and the
 *                  shell renders <DetailPage> instead of this component in
 *                  that case.
 *
 *   Anything else →  no actions.
 *
 * Every rendered control now uses the unified <Button> primitive (task 3.8)
 * so branded tenants get branded action bars for free.
 */
import type { PageMeta } from '@appbana/shared';
import {
  classifyKind,
  extractEntity,
  findAddPageForEntity,
} from './page-classifier';
import { useRuntimeNavigation } from './runtime-navigation';
import { toast } from './Toaster';
import { Button } from './Button';

export interface PageActionsProps {
  readonly page: PageMeta;
}

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

function PlusIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"
         strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round"
         className="w-4 h-4" aria-hidden="true">
      <line x1="12" y1="5" x2="12" y2="19" />
      <line x1="5" y1="12" x2="19" y2="12" />
    </svg>
  );
}

function EditIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"
         strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round"
         className="w-4 h-4" aria-hidden="true">
      <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
      <path d="M18.5 2.5a2.121 2.121 0 1 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
    </svg>
  );
}

function TrashIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"
         strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round"
         className="w-4 h-4" aria-hidden="true">
      <polyline points="3 6 5 6 21 6" />
      <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6" />
      <path d="M10 11v6" />
      <path d="M14 11v6" />
      <path d="M9 6V4a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v2" />
    </svg>
  );
}

export function PageActions({ page }: Readonly<PageActionsProps>) {
  const { kind, entity } = classifyPageActions(page);
  const nav = useRuntimeNavigation();

  if (kind === 'list' && entity) {
    const addPage = nav ? findAddPageForEntity(entity, nav.pages) : null;
    if (!addPage) return null;
    return (
      <Button
        variant="primary"
        icon={<PlusIcon />}
        onClick={() => nav?.navigateToPage(addPage)}
      >
        New {entity}
      </Button>
    );
  }

  if (kind === 'detail') {
    const label = entity ?? 'record';
    return (
      <>
        <Button
          variant="secondary"
          icon={<EditIcon />}
          onClick={() => {
            toast.info('Select a record from the list to edit it');
          }}
        >
          Edit
        </Button>
        <Button
          variant="danger"
          icon={<TrashIcon />}
          onClick={() => {
            toast.info(`Select a ${label} to delete`, {
              description: 'Open a row from the list to enable this action.',
            });
          }}
        >
          Delete
        </Button>
      </>
    );
  }

  return null;
}
