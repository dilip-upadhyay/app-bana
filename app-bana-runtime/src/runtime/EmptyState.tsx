/**
 * EmptyState.tsx — Illustrated empty-state primitive for the deployed runtime.
 *
 * Sprint 2 Task 2.3. Replaces the flat "No records yet" placeholder in
 * StudioTableLive with a friendly illustration + heading + optional CTA
 * button. The CTA is auto-derived: when the current app has a matching
 * "Add {Entity}" page, we render an indigo button that navigates to it
 * through `RuntimeNavigationContext`. If no add-page exists, we fall
 * back to a plain informational card so the user still gets guidance.
 */
import type { ReactNode } from 'react';
import { Illustration, illustrationFor, type IllustrationKey } from './illustrations';

export interface EmptyStateProps {
  readonly title: string;
  readonly description?: string;
  /** Explicit illustration override; defaults to entity-based routing. */
  readonly illustration?: IllustrationKey;
  /** Used to auto-pick an illustration when `illustration` isn't set. */
  readonly entityName?: string;
  /** Optional call-to-action rendered as a solid indigo button. */
  readonly action?: {
    readonly label: string;
    readonly onClick: () => void;
    readonly icon?: ReactNode;
  };
}

/** Small plus icon used as the default action-button glyph. */
function PlusIcon(): ReactNode {
  return (
    <svg
      viewBox="0 0 20 20"
      width="16"
      height="16"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      aria-hidden="true"
    >
      <line x1="10" y1="4" x2="10" y2="16" />
      <line x1="4" y1="10" x2="16" y2="10" />
    </svg>
  );
}

export function EmptyState({
  title,
  description,
  illustration,
  entityName,
  action,
}: Readonly<EmptyStateProps>) {
  const kind: IllustrationKey = illustration ?? illustrationFor(entityName);
  return (
    <div
      className="appbana-empty-state"
      role="status"
      aria-live="polite"
    >
      <Illustration kind={kind} className="appbana-empty-state-art" />
      <h3 className="appbana-empty-state-title">{title}</h3>
      {description && (
        <p className="appbana-empty-state-body">{description}</p>
      )}
      {action && (
        <button
          type="button"
          onClick={action.onClick}
          className="appbana-empty-state-cta"
        >
          <span className="appbana-empty-state-cta-icon">
            {action.icon ?? <PlusIcon />}
          </span>
          <span>{action.label}</span>
        </button>
      )}
    </div>
  );
}
