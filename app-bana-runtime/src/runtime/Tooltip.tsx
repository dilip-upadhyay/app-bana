/**
 * Tooltip.tsx — Sprint 3 task 3.11.
 *
 * Zero-dep tooltip that appears on both hover AND focus (WCAG 2.1.1). The
 * previous icon-rail sidebar used the native `title` attribute, which only
 * fires on hover — keyboard users tabbing through the rail got no label.
 *
 * Positioning: the tooltip is a sibling that sits to the right of the
 * trigger. The CSS in `.appbana-tooltip` handles the visibility transition
 * so this component is purely structural; no JS state, no portals, no
 * measurement. If the rail moves to a different edge we'll wrap this in a
 * variant prop then.
 */
import type { ReactNode } from 'react';

export interface TooltipProps {
  /** The visible label the tooltip announces. */
  readonly label: string;
  readonly children: ReactNode;
}

export function Tooltip({ label, children }: Readonly<TooltipProps>) {
  return (
    <span className="appbana-tooltip-wrap">
      {children}
      <span role="tooltip" className="appbana-tooltip">
        {label}
      </span>
    </span>
  );
}
