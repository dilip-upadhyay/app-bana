import { LitElement, html, css } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { getTokens, updateToken, resetTokens, onTokensChange, initTokens, undoTokenChange, redoTokenChange, canUndo, canRedo, getRecentlyEdited } from '../store/TokenStore';

@customElement('studio-token-panel')
export class StudioTokenPanel extends LitElement {
  static styles = css`
    :host { display:block; background:#fff; border:1px solid #e2e8f0; border-radius:4px; padding:8px; font:12px/1.4 system-ui,sans-serif; }
    h4 { margin:0 0 6px; font-size:13px; }
    table { width:100%; border-collapse:collapse; font-size:11px; }
    th { text-align:left; font-weight:600; padding:4px 4px; font-size:10px; text-transform:uppercase; letter-spacing:.5px; color:#475569; }
    td { padding:2px 4px; vertical-align:middle; }
    input { width:100%; box-sizing:border-box; font:11px system-ui,sans-serif; padding:3px 4px; }
    .actions { display:flex; gap:6px; margin-top:6px; }
    button { font:11px system-ui,sans-serif; padding:4px 8px; border:1px solid #94a3b8; background:#f8fafc; border-radius:4px; cursor:pointer; }
    button.primary { background:#1e3a8a; color:#fff; border-color:#1e3a8a; }
    .recent { background: var(--color-brand-muted); transition: background .4s ease; }
    .recent input { background: #fff; }
    .sr { position:absolute; left:-9999px; width:1px; height:1px; overflow:hidden; }
  `;
  @state() private tokens: Record<string,string> = {};
  @state() private recent: string[] = [];
  @state() private undoable = false;
  @state() private redoable = false;
  @state() private announce: string = '';
  private unsub: (()=>void)|null = null;

  connectedCallback(): void {
    super.connectedCallback();
    if (!Object.keys(this.tokens).length) { initTokens(); this.tokens = getTokens(); }
    this.unsub = onTokensChange(()=>{
      this.tokens = getTokens();
      this.recent = getRecentlyEdited();
      this.undoable = canUndo();
      this.redoable = canRedo();
      this.requestUpdate();
    });
    this.tokens = getTokens();
    this.recent = getRecentlyEdited();
    this.undoable = canUndo();
    this.redoable = canRedo();
  }
  disconnectedCallback(): void { super.disconnectedCallback(); this.unsub?.(); }

  private onInput(key: string, value: string) { updateToken(key, value); this.announce = `Updated ${key}`; }
  private undo() { undoTokenChange(); this.announce = 'Undo token change'; }
  private redo() { redoTokenChange(); this.announce = 'Redo token change'; }

  render() {
    const entries = Object.entries(this.tokens).sort((a,b)=>a[0].localeCompare(b[0]));
    const recentSet = new Set(this.recent.slice(0,5));
    return html`
      <h4>Design Tokens</h4>
      <div class="actions" style="margin-bottom:4px;">
        <button @click=${()=>this.undo()} ?disabled=${!this.undoable} title="Undo (Ctrl+Z)">Undo</button>
        <button @click=${()=>this.redo()} ?disabled=${!this.redoable} title="Redo (Ctrl+Y)">Redo</button>
        <span style="flex:1"></span>
        <button @click=${()=>resetTokens()} title="Reset to defaults (undoable)">Reset</button>
      </div>
      <table aria-label="Design Tokens"><thead><tr><th style="width:45%">Name</th><th>Value</th></tr></thead>
        <tbody>
          ${entries.map(([k,v])=> html`<tr class=${recentSet.has(k)?'recent':''}>
            <td><code>${k}</code></td>
            <td><input aria-label=${`Token ${k}`} .value=${v} @input=${(e:Event)=>this.onInput(k, (e.target as HTMLInputElement).value)} /></td>
          </tr>`)}
        </tbody>
      </table>
      <div class="actions">
        <small style="opacity:.7;">Recent changes highlighted</small>
      </div>
      <div class="sr" aria-live="polite">${this.announce}</div>
    `;
  }
}

// Design token persistence (Phase A/B bridge)
// Simple key-value token store persisted in localStorage and applied to :root.
export interface DesignTokens { [key: string]: string; }

const STORAGE_KEY = 'studio.tokens.v1';
const DEFAULT_TOKENS: DesignTokens = {
  'color-brand': '#2563eb',
  'color-brand-accent': '#1d4ed8',
  'color-brand-muted': '#dbeafe',
  'color-surface': '#ffffff',
  'color-surface-alt': '#f1f5f9',
  'color-border': '#cbd5e1',
  'radius-sm': '3px',
  'radius-md': '6px',
  'font-sans': 'system-ui, sans-serif'
};

let tokens: DesignTokens = { ...DEFAULT_TOKENS };
let listeners = new Set<() => void>();

export function initTokens() {
  try { const raw = localStorage.getItem(STORAGE_KEY); if (raw) { const parsed = JSON.parse(raw); if (parsed && typeof parsed === 'object') tokens = { ...DEFAULT_TOKENS, ...parsed }; } } catch {}
  applyTokens();
}

export function getTokens(): DesignTokens { return { ...tokens }; }
export function updateToken(key: string, value: string) {
  tokens = { ...tokens, [key]: value };
  persist();
  applyTokens();
  broadcast();
}
export function resetTokens() { tokens = { ...DEFAULT_TOKENS }; persist(); applyTokens(); broadcast(); }
export function onTokensChange(fn: () => void) { listeners.add(fn); return () => listeners.delete(fn); }

function broadcast() { for (const fn of listeners) fn(); }
function persist() { try { localStorage.setItem(STORAGE_KEY, JSON.stringify(tokens)); } catch {} }
function applyTokens() {
  const root = document.documentElement;
  for (const [k,v] of Object.entries(tokens)) root.style.setProperty(`--${k}`, v);
}

// auto init if running in browser (tests can call manually)
if (typeof window !== 'undefined') initTokens();

