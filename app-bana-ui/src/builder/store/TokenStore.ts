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

export function initTokens() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw);
      if (parsed && typeof parsed === 'object') tokens = { ...DEFAULT_TOKENS, ...parsed };
    }
  } catch {}
  try {
    const r = localStorage.getItem(RECENT_KEY);
    if (r) { const arr = JSON.parse(r); if (Array.isArray(arr)) recentKeys = arr.filter(k=> typeof k === 'string'); }
  } catch {}
  applyTokens();
}

export function getTokens(): DesignTokens { return { ...tokens }; }
export function getRecentlyEdited(): string[] { return [...recentKeys]; }
export function canUndo(): boolean { return history.length > 0; }
export function canRedo(): boolean { return future.length > 0; }

export function updateToken(key: string, value: string) {
  const prev = tokens[key];
  if (prev === value) return; // no-op
  tokens = { ...tokens, [key]: value };
  pushHistory({ key, prev, next: value });
  recordRecent(key);
  persist();
  applyTokens();
  broadcast();
}

export function resetTokens() {
  const prevAll = { ...tokens };
  tokens = { ...DEFAULT_TOKENS };
  pushHistory({ key: '*reset*', prev: JSON.stringify(prevAll), next: JSON.stringify(tokens) });
  recentKeys = Object.keys(tokens);
  persist();
  applyTokens();
  broadcast();
}

export function undoTokenChange() {
  const op = history.pop();
  if (!op) return;
  future.push(op);
  if (op.key === '*reset*') {
    // restore entire snapshot
    try { const prevObj = op.prev ? JSON.parse(op.prev) : {}; tokens = { ...prevObj }; } catch { tokens = { ...DEFAULT_TOKENS }; }
  } else {
    if (op.prev === undefined) {
      // newly created previously? (not currently used)
      const { [op.key]:_, ...rest } = tokens; tokens = rest;
    } else {
      tokens = { ...tokens, [op.key]: op.prev };
    }
  }
  recordRecent(op.key);
  persist(false); // still persist updated token snapshot
  applyTokens();
  broadcast();
}

export function redoTokenChange() {
  const op = future.pop();
  if (!op) return;
  // push back onto history when reapplied
  history.push(op);
  if (op.key === '*reset*') {
    try { const nextObj = op.next ? JSON.parse(op.next) : {}; tokens = { ...nextObj }; } catch { tokens = { ...DEFAULT_TOKENS }; }
  } else {
    if (op.next === undefined) {
      const { [op.key]:_, ...rest } = tokens; tokens = rest; // deletion scenario
    } else {
      tokens = { ...tokens, [op.key]: op.next };
    }
  }
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

export default { initTokens, getTokens, updateToken, resetTokens, undoTokenChange, redoTokenChange, canUndo, canRedo, getRecentlyEdited, onTokensChange };
