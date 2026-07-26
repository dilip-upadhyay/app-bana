/**
 * illustrations.tsx — Bundled inline SVG illustrations for empty states.
 *
 * Style: flat, single-line strokes with one accent fill, in the spirit of
 * unDraw (https://undraw.co). Kept as raw inline SVG rather than an npm
 * package to keep the bundle small and avoid external asset dependencies.
 *
 * All illustrations use `currentColor` for the primary stroke and a fixed
 * indigo accent so they inherit dark/light context but stay on-brand.
 * Sized via viewBox — consumers control display width with CSS.
 */
import type { ReactElement } from 'react';

const ACCENT = '#6366F1';    // indigo-500
const ACCENT_SOFT = '#E0E7FF'; // indigo-100

export type IllustrationKey =
  | 'records'   // generic "no rows" table
  | 'form'      // start-filling-a-form vibe
  | 'customer'  // people / contacts
  | 'tasks';    // checklist

interface IllustrationProps {
  readonly className?: string;
}

/** Empty desk/paper stack — the default "no records yet" illustration. */
function Records({ className }: IllustrationProps): ReactElement {
  return (
    <svg viewBox="0 0 240 160" xmlns="http://www.w3.org/2000/svg" className={className} aria-hidden="true">
      <ellipse cx="120" cy="145" rx="90" ry="6" fill={ACCENT_SOFT} />
      {/* Back sheet */}
      <rect x="70" y="30" width="110" height="80" rx="6" fill="#fff" stroke="currentColor" strokeWidth="2" />
      <line x1="82" y1="46" x2="150" y2="46" stroke="currentColor" strokeWidth="2" strokeLinecap="round" opacity="0.5" />
      <line x1="82" y1="58" x2="140" y2="58" stroke="currentColor" strokeWidth="2" strokeLinecap="round" opacity="0.3" />
      <line x1="82" y1="70" x2="160" y2="70" stroke="currentColor" strokeWidth="2" strokeLinecap="round" opacity="0.3" />
      {/* Front card */}
      <rect x="55" y="55" width="110" height="70" rx="6" fill="#fff" stroke="currentColor" strokeWidth="2" />
      <rect x="67" y="70" width="30" height="8" rx="2" fill={ACCENT_SOFT} />
      <line x1="67" y1="90" x2="150" y2="90" stroke="currentColor" strokeWidth="2" strokeLinecap="round" opacity="0.35" />
      <line x1="67" y1="102" x2="130" y2="102" stroke="currentColor" strokeWidth="2" strokeLinecap="round" opacity="0.35" />
      {/* Plus badge */}
      <circle cx="180" cy="55" r="18" fill={ACCENT} />
      <line x1="180" y1="47" x2="180" y2="63" stroke="#fff" strokeWidth="3" strokeLinecap="round" />
      <line x1="172" y1="55" x2="188" y2="55" stroke="#fff" strokeWidth="3" strokeLinecap="round" />
    </svg>
  );
}

/** Clipboard with a starter checklist — for form-oriented pages. */
function Form({ className }: IllustrationProps): ReactElement {
  return (
    <svg viewBox="0 0 240 160" xmlns="http://www.w3.org/2000/svg" className={className} aria-hidden="true">
      <ellipse cx="120" cy="145" rx="80" ry="5" fill={ACCENT_SOFT} />
      <rect x="80" y="25" width="90" height="110" rx="8" fill="#fff" stroke="currentColor" strokeWidth="2" />
      <rect x="105" y="18" width="40" height="14" rx="3" fill={ACCENT} />
      <rect x="93" y="50" width="12" height="12" rx="3" stroke="currentColor" strokeWidth="2" fill="#fff" />
      <line x1="112" y1="56" x2="155" y2="56" stroke="currentColor" strokeWidth="2" strokeLinecap="round" opacity="0.5" />
      <rect x="93" y="72" width="12" height="12" rx="3" stroke="currentColor" strokeWidth="2" fill={ACCENT} />
      <polyline points="96,78 100,82 108,74" stroke="#fff" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" fill="none" />
      <line x1="112" y1="78" x2="150" y2="78" stroke="currentColor" strokeWidth="2" strokeLinecap="round" opacity="0.5" />
      <rect x="93" y="94" width="12" height="12" rx="3" stroke="currentColor" strokeWidth="2" fill="#fff" />
      <line x1="112" y1="100" x2="145" y2="100" stroke="currentColor" strokeWidth="2" strokeLinecap="round" opacity="0.5" />
      <rect x="93" y="116" width="12" height="12" rx="3" stroke="currentColor" strokeWidth="2" fill="#fff" />
      <line x1="112" y1="122" x2="140" y2="122" stroke="currentColor" strokeWidth="2" strokeLinecap="round" opacity="0.5" />
    </svg>
  );
}

/** People silhouettes — for customer / contact style entities. */
function Customer({ className }: IllustrationProps): ReactElement {
  return (
    <svg viewBox="0 0 240 160" xmlns="http://www.w3.org/2000/svg" className={className} aria-hidden="true">
      <ellipse cx="120" cy="140" rx="90" ry="6" fill={ACCENT_SOFT} />
      <circle cx="90" cy="70" r="20" fill={ACCENT_SOFT} stroke="currentColor" strokeWidth="2" />
      <path d="M60 130 Q60 100 90 100 Q120 100 120 130 Z" fill="#fff" stroke="currentColor" strokeWidth="2" />
      <circle cx="155" cy="80" r="16" fill={ACCENT} stroke="currentColor" strokeWidth="2" />
      <path d="M132 130 Q132 106 155 106 Q178 106 178 130 Z" fill={ACCENT} stroke="currentColor" strokeWidth="2" />
      <circle cx="200" cy="40" r="14" fill={ACCENT} />
      <line x1="200" y1="32" x2="200" y2="48" stroke="#fff" strokeWidth="2.5" strokeLinecap="round" />
      <line x1="192" y1="40" x2="208" y2="40" stroke="#fff" strokeWidth="2.5" strokeLinecap="round" />
    </svg>
  );
}

/** Checklist / task-list — for task or checklist style entities. */
function Tasks({ className }: IllustrationProps): ReactElement {
  return (
    <svg viewBox="0 0 240 160" xmlns="http://www.w3.org/2000/svg" className={className} aria-hidden="true">
      <ellipse cx="120" cy="145" rx="85" ry="6" fill={ACCENT_SOFT} />
      <rect x="60" y="35" width="120" height="100" rx="8" fill="#fff" stroke="currentColor" strokeWidth="2" />
      <circle cx="80" cy="58" r="6" fill={ACCENT} />
      <line x1="94" y1="58" x2="165" y2="58" stroke="currentColor" strokeWidth="2" strokeLinecap="round" opacity="0.55" />
      <circle cx="80" cy="80" r="6" stroke="currentColor" strokeWidth="2" fill="#fff" />
      <line x1="94" y1="80" x2="150" y2="80" stroke="currentColor" strokeWidth="2" strokeLinecap="round" opacity="0.55" />
      <circle cx="80" cy="102" r="6" stroke="currentColor" strokeWidth="2" fill="#fff" />
      <line x1="94" y1="102" x2="160" y2="102" stroke="currentColor" strokeWidth="2" strokeLinecap="round" opacity="0.55" />
      <circle cx="80" cy="124" r="6" stroke="currentColor" strokeWidth="2" fill="#fff" />
      <line x1="94" y1="124" x2="140" y2="124" stroke="currentColor" strokeWidth="2" strokeLinecap="round" opacity="0.55" />
    </svg>
  );
}

/**
 * Choose an illustration based on the entity name — best-effort semantic
 * routing so a "Customer" list uses the people picture and a "Task" list
 * uses the checklist. Anything unknown falls back to the generic records
 * illustration.
 */
export function illustrationFor(entityName: string | undefined): IllustrationKey {
  const n = (entityName ?? '').toLowerCase();
  if (/(customer|contact|user|member|client|person|people|employee|staff)/.test(n)) return 'customer';
  if (/(task|todo|job|ticket|issue|activity)/.test(n)) return 'tasks';
  if (/(form|entry|application|submission)/.test(n)) return 'form';
  return 'records';
}

export function Illustration({ kind, className }: Readonly<{ kind: IllustrationKey; className?: string }>): ReactElement {
  switch (kind) {
    case 'form':     return <Form className={className} />;
    case 'customer': return <Customer className={className} />;
    case 'tasks':    return <Tasks className={className} />;
    case 'records':
    default:         return <Records className={className} />;
  }
}
