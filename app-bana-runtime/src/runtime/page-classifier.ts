/**
 * page-classifier.ts — Pure helpers for classifying deployed-runtime pages
 * by intent (list / add / detail / dashboard / …) and extracting the entity
 * a page belongs to.
 *
 * Shared by `RuntimeSidebar` (Task 2.2 sidebar grouping) and `StudioTableLive`
 * (Task 2.3 empty-state "Add {Entity}" CTAs). Kept as a standalone module so
 * both consumers depend on the same regexes and both are covered by the same
 * `RuntimeSidebar.test.ts` suite.
 */
import type { PageMeta } from '@appbana/shared';

export type PageKind =
  | 'list'
  | 'add'
  | 'detail'
  | 'dashboard'
  | 'chart'
  | 'settings'
  | 'other';

/** Classify a page's action verb from its name. */
export function classifyKind(name: string | undefined): PageKind {
  const n = (name ?? '').toLowerCase();
  if (/\b(list|table|browse|index)s?\b|\ball\b/.test(n)) return 'list';
  if (/\b(add|new|create|register|onboard|start)\b/.test(n)) return 'add';
  if (/\b(detail|view|edit|profile|inspect|show)s?\b/.test(n)) return 'detail';
  if (/\b(dashboard|home|overview)s?\b/.test(n)) return 'dashboard';
  if (/\b(report|analytic|chart|metric|stat)s?\b/.test(n)) return 'chart';
  if (/\b(setting|config|admin|preference)s?\b/.test(n)) return 'settings';
  return 'other';
}

/** Simple English pluraliser — good enough for auto-generated app titles. */
export function pluralize(word: string): string {
  if (!word) return word;
  const lower = word.toLowerCase();
  if (lower.endsWith('s') || lower.endsWith('x') || lower.endsWith('z') ||
      lower.endsWith('ch') || lower.endsWith('sh')) return `${word}es`;
  if (/[^aeiou]y$/i.test(word)) return `${word.slice(0, -1)}ies`;
  return `${word}s`;
}

/** Depluralize simple English plurals so "Customers" and "Customer" cluster. */
export function singularize(word: string): string {
  const lower = word.toLowerCase();
  if (lower.endsWith('ies') && word.length > 3) return `${word.slice(0, -3)}y`;
  if (lower.endsWith('ches') || lower.endsWith('shes')) return word.slice(0, -2);
  if (lower.endsWith('ses')) return word.slice(0, -2);
  if (lower.endsWith('s') && !lower.endsWith('ss') && word.length > 1) return word.slice(0, -1);
  return word;
}

function titleCase(raw: string): string {
  return raw
    .split(/\s+/)
    .map((w) => (w ? w[0].toUpperCase() + w.slice(1).toLowerCase() : w))
    .join(' ')
    .trim();
}

/**
 * Extract the entity token a page belongs to, by stripping the leading
 * verb ("Add", "New") or the trailing kind noun ("List", "Detail").
 * Returns `null` for pages that don't obviously belong to an entity
 * (Dashboard, Settings, Reports, ad-hoc pages).
 */
export function extractEntity(name: string | undefined, kind: PageKind): string | null {
  if (!name) return null;
  if (kind === 'dashboard' || kind === 'chart' || kind === 'settings') return null;

  const trimmed = name.trim();

  const addMatch = /^(add|new|create|register|onboard|start)\s+(.+)$/i.exec(trimmed);
  if (addMatch) return titleCase(addMatch[2]);

  const listMatch = /^(.+?)\s+(list|table|index|browse|all)$/i.exec(trimmed);
  if (listMatch) return titleCase(listMatch[1]);

  const detailMatch = /^(.+?)\s+(detail|details|view|edit|profile|show)$/i.exec(trimmed);
  if (detailMatch) return titleCase(detailMatch[1]);

  if (kind === 'other' && /^[A-Za-z][A-Za-z\s]*$/.test(trimmed) && trimmed.split(/\s+/).length <= 3) {
    return titleCase(trimmed);
  }

  return null;
}

/**
 * Given the fully-qualified entity key stored on a StudioTable node
 * (`{tenantId}_{appId}_{EntityName}`), return the plain entity name.
 * If the key is already just the entity name, returns it unchanged.
 */
export function entityNameFromKey(entityKey: string): string {
  if (!entityKey) return '';
  const parts = entityKey.split('_');
  // Fully qualified: {tenant}_{uuid...}_{Entity}. UUID chunk splits into 5
  // hyphen-separated segments joined by the same '_' separator, so the safest
  // heuristic is "everything after the last underscore where the following
  // segment starts with an uppercase letter". Fall back to the whole string.
  for (let i = parts.length - 1; i > 0; i--) {
    const p = parts[i];
    if (p && /^[A-Z][A-Za-z0-9]*$/.test(p)) return p;
  }
  return parts.at(-1) ?? entityKey;
}

/**
 * Normalize an entity token for comparison: singularize, lowercase, and
 * strip whitespace. Required because `entityNameFromKey()` yields a
 * PascalCase, space-free token (e.g. "ITAccessRequest") while
 * `extractEntity()` yields a title-cased, space-separated one derived from
 * the page name (e.g. "It Access Request") — without stripping spaces here,
 * every multi-word entity name ("Equipment Request", "IT Access Request",
 * "Onboarding Task") fails to match and silently hides Edit/Add routing
 * (and everything gated behind it, e.g. reaching a record's Detail page to
 * Submit/Approve it) for that entity only.
 */
function normalizeEntityToken(word: string): string {
  return singularize(word).toLowerCase().replace(/\s+/g, '');
}

/**
 * Find the "Add {entity}" page in the app's page list, if any. Case-insensitive
 * match on the singularised entity token. Returns `null` when no matching
 * page exists (e.g. the app was scaffolded without an add-page).
 */
export function findAddPageForEntity(
  entityName: string,
  pages: readonly PageMeta[],
): PageMeta | null {
  if (!entityName || !pages.length) return null;
  const target = normalizeEntityToken(entityName);
  for (const p of pages) {
    const kind = classifyKind(p.name);
    if (kind !== 'add') continue;
    const entity = extractEntity(p.name, kind);
    if (!entity) continue;
    if (normalizeEntityToken(entity) === target) return p;
  }
  return null;
}

/**
 * Sprint 3 task 3.6 — Find the "Detail" / "View" / "Edit" page for an
 * entity. Symmetric with {@link findAddPageForEntity}; used by row-actions
 * and PageActions to route Edit clicks to the right destination.
 *
 * When a page carries the authoritative `PageMeta.kind === 'detail'`
 * (Sprint 3 task 3.2), we prefer that over sniffing the name.
 */
export function findDetailPageForEntity(
  entityName: string,
  pages: readonly PageMeta[],
): PageMeta | null {
  if (!entityName || !pages.length) return null;
  const target = normalizeEntityToken(entityName);
  for (const p of pages) {
    if (p.kind === 'detail') {
      // Trust the authored kind but still verify the entity matches so a
      // Customer row doesn't route to the Order detail page.
      const derived = extractEntity(p.name, 'detail');
      if (derived && normalizeEntityToken(derived) === target) return p;
      // Kind-only match (no entity in the name) — accept when there is
      // only one detail page for the app.
      if (!derived) return p;
    }
  }
  for (const p of pages) {
    const kind = classifyKind(p.name);
    if (kind !== 'detail') continue;
    const entity = extractEntity(p.name, kind);
    if (!entity) continue;
    if (normalizeEntityToken(entity) === target) return p;
  }
  return null;
}
