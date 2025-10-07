// Design token persistence (Phase A/B bridge) with undo/redo and recent edits tracking
// Simple key-value token store persisted in localStorage and applied to :root.
export interface DesignTokens { [key: string]: string; }

const STORAGE_KEY = 'studio.tokens.v1';
const RECENT_KEY = 'studio.tokens.recent.v1';
const HISTORY_LIMIT = 100;

const DEFAULT_TOKENS: DesignTokens = {
  'color-brand': '#2563eb',
  'color-brand-accent': '#1d4ed8',
  'color-brand-muted': '#dbeafe',
  'color-surface': '#ffffff',
  'color-surface-alt': '#f1f5f9',
  'color-border': '#cbd5e1',
  'color-text': '#1e293b',
  'color-text-secondary': '#475569',
  'color-danger': '#dc2626',
  'color-success': '#059669',
  'radius-sm': '3px',
  'radius-md': '6px',
  'font-sans': 'system-ui, sans-serif'
};

let tokens: DesignTokens = { ...DEFAULT_TOKENS };
let listeners = new Set<() => void>();
let recentKeys: string[] = []; // persisted order: most recent first (unique)

interface TokenOp { key: string; prev?: string; next?: string; } // prev undefined => new key, next undefined => deletion (not used yet)
let history: TokenOp[] = [];
let future: TokenOp[] = [];

interface Revision { id: number; ts: number; key: string; prev?: string; next?: string; reason: 'update'|'reset'|'import'|'merge-import'; }
let revisions: Revision[] = [];
let revSeq = 1;
interface DiffEntry { prev: string; current: string; ts: number; }
const lastDiff: Record<string, DiffEntry> = {};

function pushRevision(r: Omit<Revision,'id'|'ts'>) {
  revisions.push({ id: revSeq++, ts: Date.now(), ...r });
  // cap revisions to last 500 to avoid unbounded growth
  if (revisions.length > 500) revisions.splice(0, revisions.length - 500);
}

function recordDiff(key: string, prev: string|undefined, next: string|undefined) {
  if (prev === undefined || next === undefined || prev === next) return;
  lastDiff[key] = { prev, current: next, ts: Date.now() };
}

export function getDiff(key: string): DiffEntry | undefined { return lastDiff[key]; }
export function getRevisions(limit?: number): Revision[] { return limit ? revisions.slice(-limit) : [...revisions]; }

export function exportTokenSnapshot(label?: string) {
  return { meta: { label: label || 'snapshot', ts: Date.now(), count: Object.keys(tokens).length }, tokens: getTokens() };
}

export function importTokenSnapshot(snapshot: any, opts: { merge?: boolean } = {}) {
  if (!snapshot || typeof snapshot !== 'object' || !snapshot.tokens) throw new Error('invalid snapshot');
  const newTokens = snapshot.tokens as DesignTokens;
  const prevAll = { ...tokens };
  if (opts.merge) {
    for (const [k,v] of Object.entries(newTokens)) {
      const prev = tokens[k];
      tokens[k] = v;
      recordDiff(k, prev, v);
      pushHistory({ key: k, prev, next: v });
      pushRevision({ key: k, prev, next: v, reason: 'merge-import' });
    }
  } else {
    tokens = { ...newTokens };
    pushHistory({ key: '*import*', prev: JSON.stringify(prevAll), next: JSON.stringify(tokens) });
    pushRevision({ key: '*import*', prev: JSON.stringify(prevAll), next: JSON.stringify(tokens), reason: 'import' });
  }
  persist();
  applyTokens();
  broadcast();
}

function categorizeKey(key: string): string {
  if (key.startsWith('color-')) return 'Colors';
  if (key.startsWith('space-')) return 'Spacing';
  if (key.startsWith('radius-')) return 'Radii';
  if (key.startsWith('font-') || key.startsWith('text-')) return 'Typography';
  return 'Other';
}
export function getCategories(): Record<string,string[]> {
  const map: Record<string,string[]> = {};
  for (const k of Object.keys(tokens)) {
    const cat = categorizeKey(k);
    (map[cat] ||= []).push(k);
  }
  for (const list of Object.values(map)) list.sort();
  return map;
}

// Extend DEFAULT_TOKENS with spacing/typography if absent
['space-1','space-2','space-3','space-4','space-5','space-6','text-xs','text-sm','text-md','text-lg'].forEach(k=>{
  if (!(k in DEFAULT_TOKENS)) DEFAULT_TOKENS[k] = getComputedStyle?.(document.documentElement).getPropertyValue('--'+k).trim() || '';
});

// Patch updateToken / resetTokens / undo/redo to push revisions & diffs
export function updateToken(key: string, value: string) {
  const prev = tokens[key];
  if (prev === value) return; // no-op
  tokens = { ...tokens, [key]: value };
  pushHistory({ key, prev, next: value });
  pushRevision({ key, prev, next: value, reason: 'update' });
  recordDiff(key, prev, value);
  recordRecent(key);
  persist();
  applyTokens();
  broadcast();
}

export function resetTokens() {
  const prevAll = { ...tokens };
  tokens = { ...DEFAULT_TOKENS };
  pushHistory({ key: '*reset*', prev: JSON.stringify(prevAll), next: JSON.stringify(tokens) });
  pushRevision({ key: '*reset*', prev: JSON.stringify(prevAll), next: JSON.stringify(tokens), reason: 'reset' });
  for (const k of Object.keys(tokens)) recordDiff(k, (prevAll as any)[k], (tokens as any)[k]);
  recentKeys = Object.keys(tokens);
  persist();
  applyTokens();
  broadcast();
}

export function undoTokenChange() {
  const op = history.pop();
  if (!op) return;
  future.push(op);
  if (op.key === '*reset*' || op.key === '*import*') {
    try { const prevObj = op.prev ? JSON.parse(op.prev) : {}; tokens = { ...prevObj }; } catch { /* ignore */ }
  } else {
    if (op.prev === undefined) {
      const { [op.key]:_, ...rest } = tokens; tokens = rest;
    } else {
      tokens = { ...tokens, [op.key]: op.prev };
    }
  }
  recordDiff(op.key, op.next, op.prev);
  recordRecent(op.key);
  persist(false);
  applyTokens();
  broadcast();
}

export function redoTokenChange() {
  const op = future.pop();
  if (!op) return;
  history.push(op);
  if (op.key === '*reset*' || op.key === '*import*') {
    try { const nextObj = op.next ? JSON.parse(op.next) : {}; tokens = { ...nextObj }; } catch { /* ignore */ }
  } else {
    if (op.next === undefined) {
      const { [op.key]:_, ...rest } = tokens; tokens = rest;
    } else {
      tokens = { ...tokens, [op.key]: op.next };
    }
  }
  recordDiff(op.key, op.prev, op.next);
  recordRecent(op.key);
  persist(false);
  applyTokens();
  broadcast();
}

export function onTokensChange(fn: () => void) { listeners.add(fn); return () => listeners.delete(fn); }

function pushHistory(op: TokenOp) {
  history.push(op);
  if (history.length > HISTORY_LIMIT) history.shift();
  future.length = 0; // clear redo stack
}

function recordRecent(key: string) {
  if (!key || key === '*reset*') return;
  recentKeys = [key, ...recentKeys.filter(k=>k!==key)].slice(0, 15);
  try { localStorage.setItem(RECENT_KEY, JSON.stringify(recentKeys)); } catch {}
}

function broadcast() { for (const fn of listeners) fn(); }
function persist(storeRecent: boolean = true) {
  try { localStorage.setItem(STORAGE_KEY, JSON.stringify(tokens)); if (storeRecent) localStorage.setItem(RECENT_KEY, JSON.stringify(recentKeys)); } catch {}
}
function applyTokens() {
  if (typeof document === 'undefined') return;
  const root = document.documentElement;
  for (const [k,v] of Object.entries(tokens)) root.style.setProperty(`--${k}`, v);
}

// auto init if running in browser
if (typeof window !== 'undefined') initTokens();

export function initTokens(forceReset: boolean = false) {
  if (forceReset) {
    tokens = { ...DEFAULT_TOKENS };
    recentKeys = [];
    history.length = 0; future.length = 0; revisions.length = 0; revSeq = 1;
  } else {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) {
        const parsed = JSON.parse(raw);
        if (parsed && typeof parsed === 'object') tokens = { ...DEFAULT_TOKENS, ...parsed };
      }
    } catch {}
    try {
      const r = localStorage.getItem(RECENT_KEY);
      if (r) { const arr = JSON.parse(r); if (Array.isArray(arr)) recentKeys = arr.filter(k=>typeof k==='string'); }
    } catch {}
  }
  applyTokens();
}

export function getTokens(): DesignTokens { return { ...tokens }; }

// Added utility state query helpers that were referenced but not implemented
export function canUndo(): boolean { return history.length > 0; }
export function canRedo(): boolean { return future.length > 0; }
export function getRecentlyEdited(): string[] { return [...recentKeys]; }

export default { initTokens, getTokens, updateToken, resetTokens, undoTokenChange, redoTokenChange, canUndo, canRedo, getRecentlyEdited, onTokensChange, exportTokenSnapshot, importTokenSnapshot, getRevisions, getCategories, getDiff };
