/**
 * ApprovalStatusPill.tsx — Phase C3.1.
 *
 * Renders the maker-checker approval state of a row.
 *
 * This is deliberately *not* the generic `StatusPill`. Approval state is a
 * closed four-value state machine owned by the platform, not a user-defined
 * status field, and it differs from `classifyStatus` in two ways that matter:
 *
 *   1. Tone. `classifyStatus` maps "draft" to `info` (blue), because for a
 *      user-defined status "draft" is a meaningful starting state. For approval
 *      state, DRAFT means "not yet in the workflow" — it should recede, not
 *      draw attention. C3.1 specifies slate, i.e. `neutral`.
 *   2. Label. The backend persists the state uppercase (`PENDING`), which reads
 *      as shouting in a table cell. We render title case, and expand PENDING to
 *      "Pending approval" so a checker scanning a list knows it is actionable.
 *
 * Unknown values fall back to the raw string with a neutral tone rather than
 * being swallowed — if the backend ever adds a fifth state, it stays visible
 * instead of silently rendering blank.
 */
import * as React from 'react';
import type { StatusTone } from './cell-formatters';

export type ApprovalState = 'DRAFT' | 'PENDING' | 'APPROVED' | 'REJECTED';

interface ApprovalPresentation {
  readonly label: string;
  readonly tone: StatusTone;
  readonly title: string;
}

const PRESENTATION: Record<ApprovalState, ApprovalPresentation> = {
  DRAFT: {
    label: 'Draft',
    tone: 'neutral',
    title: 'Not yet submitted for approval',
  },
  PENDING: {
    label: 'Pending approval',
    tone: 'warning',
    title: 'Submitted and awaiting a checker',
  },
  APPROVED: {
    label: 'Approved',
    tone: 'success',
    title: 'Approved and live',
  },
  REJECTED: {
    label: 'Rejected',
    tone: 'danger',
    title: 'Returned to the maker for rework',
  },
};

/** Normalises any casing/whitespace to a known state, or null if unrecognised. */
export function toApprovalState(value: unknown): ApprovalState | null {
  if (value == null) return null;
  const s = String(value).trim().toUpperCase();
  if (!s) return null;
  // `hasOwnProperty` rather than `in`: `in` walks the prototype chain, so a
  // value of "TOSTRING" or "CONSTRUCTOR" would test true and then be cast to
  // an ApprovalState it is not. (`Object.hasOwn` would be the modern spelling
  // but the package's `lib` target predates it.)
  return Object.prototype.hasOwnProperty.call(PRESENTATION, s) ? (s as ApprovalState) : null;
}

export interface ApprovalStatusPillProps {
  /** Raw `approval_status` value. Any casing; null/undefined/empty tolerated. */
  readonly value: unknown;
  /** Extra classes appended to the pill root. */
  readonly className?: string;
  /**
   * How to represent an empty / null value.
   *   - `'dash'` (default): muted em-dash, so a column never collapses
   *   - `'hide'`: render nothing
   */
  readonly emptyMode?: 'dash' | 'hide';
}

export function ApprovalStatusPill(props: Readonly<ApprovalStatusPillProps>) {
  const { value, className = '', emptyMode = 'dash' } = props;

  const raw = value == null ? '' : String(value).trim();
  if (!raw) {
    if (emptyMode === 'hide') return null;
    return <span className="text-slate-400">—</span>;
  }

  const state = toApprovalState(raw);
  const presentation: ApprovalPresentation = state
    ? PRESENTATION[state]
    : { label: raw, tone: 'neutral', title: `Unrecognised approval state: ${raw}` };

  return (
    <span
      className={`appbana-status-pill status-${presentation.tone} ${className}`.trim()}
      title={presentation.title}
      data-approval-state={state ?? 'UNKNOWN'}
    >
      {presentation.label}
    </span>
  );
}
