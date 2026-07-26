/**
 * EmptyState.tsx — Illustrated empty-state primitive for the deployed runtime.
 *
 * Sprint 2 Task 2.3 + Sprint 3 task 3.8. The CTA now emits the unified
 * <Button variant="primary"> so it picks up tenant branding and matches the
 * Save / Sign-in / Add-row buttons pixel-for-pixel.
 */
import type { ReactNode } from 'react';
import { Illustration, illustrationFor, type IllustrationKey } from './illustrations';
import { Button } from './Button';

export interface EmptyStateProps {
  readonly title: string;
  readonly description?: string;
  /** Explicit illustration override; defaults to entity-based routing. */
  readonly illustration?: IllustrationKey;
  /** Used to auto-pick an illustration when `illustration` isn't set. */
  readonly entityName?: string;
  /** Optional call-to-action rendered as a brand-primary <Button>. */
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
        <Button
          variant="primary"
          onClick={action.onClick}
          icon={action.icon ?? <PlusIcon />}
        >
          {action.label}
        </Button>
      )}
    </div>
  );
}
