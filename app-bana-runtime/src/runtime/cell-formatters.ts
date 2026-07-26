/**
 * cell-formatters.ts — Pure, side-effect-free helpers for rendering entity
 * data in tables and detail views.
 *
 * All exported functions are pure: same input → same output, no I/O, no React.
 * That's what makes them cheap to unit-test with `node:test` + `tsx`.
 *
 * Runtime UX Overhaul Plan §1.2 / §1.3 / §1.4.
 */

// ─── Header humanisation ─────────────────────────────────────────────────────

/**
 * Convert a database column name (snake_case / kebab-case / camelCase) into a
 * short, sentence-case label suitable for a table header.
 *
 * Rules:
 *   - `full_name` / `full-name` → "Full name"
 *   - `firstName` → "First name"
 *   - `id` → "ID"      (common all-caps acronym)
 *   - `_at` suffixes handled: `created_at` → "Created at"
 *
 * NOTE: We deliberately do NOT ALL-CAPS the output. Uppercasing is a visual
 * style applied by CSS (`tracking-wider uppercase`), which is a11y-friendly.
 * Keep the DOM copy-pasteable and screen-reader-friendly by using sentence case.
 */
export function humanizeHeader(name: string | undefined | null): string {
  if (!name) return '';
  const cleaned = String(name).trim();
  if (!cleaned) return '';

  const acronyms = new Set(['ID', 'URL', 'API', 'UUID']);
  if (acronyms.has(cleaned.toUpperCase())) return cleaned.toUpperCase();

  const withSpaces = cleaned
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')      // camelCase split
    .replace(/([A-Z]+)([A-Z][a-z])/g, '$1 $2')   // acronym boundary
    .replace(/[_-]+/g, ' ')                       // snake/kebab → space
    .replace(/\s+/g, ' ')
    .trim();

  if (!withSpaces) return cleaned;
  return withSpaces[0].toUpperCase() + withSpaces.slice(1).toLowerCase();
}

// ─── Date formatting ─────────────────────────────────────────────────────────

/**
 * Format a value that MIGHT be a date/datetime into a human-readable label.
 * Returns `{ label, title }` — `label` is short (for the cell), `title` is
 * the full ISO for hover tooltip.
 *
 * Passes through unchanged when the value isn't a recognisable date.
 */
export interface FormattedDate {
  readonly label: string;
  readonly title: string;
  readonly isDate: boolean;
}

const ISO_DATE_RE = /^\d{4}-\d{2}-\d{2}([T ]\d{2}:\d{2}(:\d{2})?(\.\d+)?(Z|[+-]\d{2}:?\d{2})?)?$/;

/** Cache one shared formatter — Intl.DateTimeFormat is not cheap to construct. */
let _dtFmt: Intl.DateTimeFormat | null = null;
let _dFmt:  Intl.DateTimeFormat | null = null;
function dtFmt(): Intl.DateTimeFormat {
  if (!_dtFmt) _dtFmt = new Intl.DateTimeFormat('en-US', {
    year: 'numeric', month: 'short', day: 'numeric',
    hour: 'numeric', minute: '2-digit', hour12: true,
  });
  return _dtFmt;
}
function dFmt(): Intl.DateTimeFormat {
  if (!_dFmt) _dFmt = new Intl.DateTimeFormat('en-US', {
    year: 'numeric', month: 'short', day: 'numeric',
  });
  return _dFmt;
}

/**
 * @param value      the raw cell value
 * @param columnType optional field.type hint from the schema. When provided,
 *                   we trust it: `type=date` uses date-only format even if the
 *                   backend echoes back a full datetime.
 */
export function formatDate(value: unknown, columnType?: string): FormattedDate {
  if (value == null || value === '') {
    return { label: '', title: '', isDate: false };
  }
  const asString = String(value);
  if (!ISO_DATE_RE.test(asString)) {
    return { label: asString, title: asString, isDate: false };
  }
  const d = new Date(asString);
  if (Number.isNaN(d.getTime())) {
    return { label: asString, title: asString, isDate: false };
  }
  const dateOnly = columnType === 'date' || !asString.includes('T');
  const fmt = dateOnly ? dFmt() : dtFmt();
  return {
    label: fmt.format(d),
    title: asString,
    isDate: true,
  };
}

// ─── Reference (FK) label resolution ─────────────────────────────────────────

/**
 * Choose the best human-readable label for a row of another entity, so a
 * foreign-key cell renders "Alice Johnson" instead of `1`.
 *
 * Priority order matches the ReferenceField dropdown so the label in a list
 * cell always matches what the user picked in the form.
 */
const LABEL_CANDIDATES = [
  'name', 'full_name', 'fullname',
  'title', 'label',
  'email', 'email_address',
  'code',
] as const;

export function pickReferenceLabel(row: Record<string, unknown> | null | undefined): string {
  if (!row || typeof row !== 'object') return '';
  const lower: Record<string, unknown> = {};
  for (const key of Object.keys(row)) {
    lower[key.toLowerCase()] = row[key];
  }
  for (const key of LABEL_CANDIDATES) {
    const v = lower[key];
    if (v != null && String(v).trim()) return String(v);
  }
  const id = lower['id'];
  return id != null ? `#${String(id)}` : '';
}

// ─── Status pill classification ──────────────────────────────────────────────

export type StatusTone = 'success' | 'warning' | 'danger' | 'info' | 'neutral';

const TONE_RULES: Array<{ readonly pattern: RegExp; readonly tone: StatusTone }> = [
  { pattern: /^(done|completed|complete|closed|approved|active|success|paid|shipped)$/i, tone: 'success' },
  { pattern: /^(in\s*progress|pending|processing|onboarding|open|new|draft)$/i, tone: 'info' },
  { pattern: /^(on\s*hold|waiting|review|blocked|delayed|warning)$/i, tone: 'warning' },
  { pattern: /^(cancelled|canceled|failed|rejected|error|deleted|inactive)$/i, tone: 'danger' },
];

export function classifyStatus(value: unknown): StatusTone {
  if (value == null) return 'neutral';
  const s = String(value).trim();
  if (!s) return 'neutral';
  for (const rule of TONE_RULES) {
    if (rule.pattern.test(s)) return rule.tone;
  }
  return 'neutral';
}
