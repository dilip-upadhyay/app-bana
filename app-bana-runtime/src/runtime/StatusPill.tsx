/**
 * StatusPill.tsx — Sprint 2 Task 2.6.
 *
 * Coloured badge for status-typed values. Delegates tone classification to
 * `classifyStatus` in cell-formatters so tables, detail views, and any
 * future consumers share one source of truth for the mapping:
 *
 *   New / Draft / Open        → info    (blue)
 *   In Progress / Processing  → warning (amber)
 *   Completed / Approved      → success (green)
 *   Blocked / Cancelled       → danger  (red)
 *   fallback                  → neutral (slate)
 *
 * Empty / null values render as a muted em-dash so a status column never
 * collapses to whitespace. Pass `showEmpty={false}` to render nothing at
 * all for empty values.
 *
 * The pill inherits its colour from CSS custom properties defined in
 * `globals.css` via `.appbana-status-pill.status-{tone}`.
 */
import * as React from 'react';
import { classifyStatus, type StatusTone } from './cell-formatters';

export interface StatusPillProps {
  /** Raw status value (string, number, null, undefined all handled). */
  readonly value: unknown;
  /** Override auto-detected tone. Rarely needed. */
  readonly tone?: StatusTone;
  /** Extra classes appended to the pill root. */
  readonly className?: string;
  /**
   * How to represent an empty / null value.
   *   - `'dash'` (default): render `<span>—</span>` in muted colour
   *   - `'hide'`: render `null`
   */
  readonly emptyMode?: 'dash' | 'hide';
}

export function StatusPill(props: Readonly<StatusPillProps>) {
  const { value, tone: explicitTone, className = '', emptyMode = 'dash' } = props;
  const label = value == null ? '' : String(value).trim();
  if (!label) {
    if (emptyMode === 'hide') return null;
    return <span className="text-slate-400">—</span>;
  }
  const tone = explicitTone ?? classifyStatus(label);
  return (
    <span className={`appbana-status-pill status-${tone} ${className}`.trim()}>
      {label}
    </span>
  );
}
