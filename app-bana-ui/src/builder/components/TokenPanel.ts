import { LitElement, html, css } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import {
  getTokens,
  updateToken,
  resetTokens,
  onTokensChange,
  initTokens,
  undoTokenChange,
  redoTokenChange,
  canUndo,
  canRedo,
  getRecentlyEdited,
  exportTokenSnapshot,
  importTokenSnapshot,
  getCategories,
  getDiff,
  getRevisions
} from '../store/TokenStore';

@customElement('studio-token-panel')
export class StudioTokenPanel extends LitElement {
  static styles = [css`
    :host { display:block; background:#fff; border:1px solid #e2e8f0; border-radius:4px; padding:8px; font:12px/1.4 system-ui,sans-serif; position:relative; }
    h4 { margin:0 0 6px; font-size:13px; }
    section.cat { margin-top:8px; }
    section.cat:first-of-type { margin-top:0; }
    h5 { margin:8px 0 4px; font-size:11px; text-transform:uppercase; letter-spacing:.6px; color:#475569; display:flex; align-items:center; gap:6px; }
    table { width:100%; border-collapse:collapse; font-size:11px; }
    th { text-align:left; font-weight:600; padding:4px 4px; font-size:10px; text-transform:uppercase; letter-spacing:.5px; color:#475569; }
    td { padding:2px 4px; vertical-align:middle; }
    tr + tr td { border-top:1px solid #f1f5f9; }
    input { width:100%; box-sizing:border-box; font:11px system-ui,sans-serif; padding:3px 4px; }
    .actions { display:flex; gap:6px; margin-top:6px; flex-wrap:wrap; }
    button { font:11px system-ui,sans-serif; padding:4px 8px; border:1px solid #94a3b8; background:#f8fafc; border-radius:4px; cursor:pointer; display:inline-flex; align-items:center; gap:4px; }
    button.primary { background:#1e3a8a; color:#fff; border-color:#1e3a8a; }
    button:disabled { opacity:.55; cursor:not-allowed; }
    .recent { background: var(--color-brand-muted); transition: background .4s ease; }
    .recent input { background:#fff; }
    .diff { font-size:10px; opacity:.75; display:inline-block; margin-left:4px; color:#475569; }
    .diff del { color:#dc2626; text-decoration:line-through; margin-right:2px; }
    .diff ins { color:#059669; text-decoration:none; }
    .export-box { margin-top:6px; display:flex; gap:6px; }
    textarea.snapshot { width:100%; min-height:90px; font:11px/1.4 ui-monospace,monospace; resize:vertical; }
    details.rev { margin-top:10px; }
    details.rev summary { cursor:pointer; font-size:11px; }
    ul.revs { list-style:none; padding:0; margin:4px 0 0; max-height:140px; overflow:auto; }
    ul.revs li { font-size:10px; padding:3px 4px; border:1px solid #e2e8f0; border-radius:3px; margin-bottom:4px; background:#f8fafc; display:flex; justify-content:space-between; gap:8px; }
    code.key { font-weight:600; }
    .sr { position:absolute; left:-9999px; width:1px; height:1px; overflow:hidden; }
    .cat-toggle { background:transparent; border:none; cursor:pointer; font:10px system-ui; color:#475569; }
  `];

  @state() private tokens: Record<string,string> = {};
  @state() private recent: string[] = [];
  @state() private undoable = false;
  @state() private redoable = false;
  @state() private announce = '';
  @state() private snapshotText = '';
  @state() private importMerge = false;
  @state() private categories: Record<string,string[]> = {};
  @state() private collapsedCats = new Set<string>();
  @state() private revisions = [] as ReturnType<typeof getRevisions>;

  private unsub: (()=>void)|null = null;

  connectedCallback(): void {
    super.connectedCallback();
    this.tabIndex = 0; // focusable for shortcuts
    if (!Object.keys(this.tokens).length) { initTokens(); }
    this.refreshState();
    this.unsub = onTokensChange(()=> this.refreshState());
    this.addEventListener('keydown', this.onKeyDown as any);
  }
  disconnectedCallback(): void { super.disconnectedCallback(); this.unsub?.(); this.removeEventListener('keydown', this.onKeyDown as any); }

  private refreshState() {
    this.tokens = getTokens();
    this.recent = getRecentlyEdited();
    this.undoable = canUndo();
    this.redoable = canRedo();
    this.categories = getCategories();
    this.revisions = getRevisions(50).slice().reverse();
  }

  private onKeyDown = (e: KeyboardEvent) => {
    if ((e.metaKey || e.ctrlKey) && !e.shiftKey && e.key.toLowerCase()==='z') { if (this.undoable) { undoTokenChange(); this.announce='Undo'; } e.preventDefault(); }
    if (((e.metaKey || e.ctrlKey) && e.key.toLowerCase()==='y') || ((e.metaKey||e.ctrlKey) && e.shiftKey && e.key.toLowerCase()==='z')) { if (this.redoable) { redoTokenChange(); this.announce='Redo'; } e.preventDefault(); }
  };

  private onInput(key: string, value: string) { updateToken(key, value); this.announce = `Updated ${key}`; }
  private undo() { undoTokenChange(); this.announce = 'Undo token change'; }
  private redo() { redoTokenChange(); this.announce = 'Redo token change'; }
  private toggleCat(cat: string) { if (this.collapsedCats.has(cat)) this.collapsedCats.delete(cat); else this.collapsedCats.add(cat); this.requestUpdate(); }

  private genSnapshot() { const snap = exportTokenSnapshot('manual'); this.snapshotText = JSON.stringify(snap, null, 2); this.announce='Snapshot generated'; }
  private copySnapshot() { if (!this.snapshotText) this.genSnapshot(); navigator.clipboard?.writeText(this.snapshotText); this.announce='Snapshot copied'; }
  private importSnapshot() {
    try { const parsed = JSON.parse(this.snapshotText); importTokenSnapshot(parsed, { merge: this.importMerge }); this.announce='Snapshot imported'; }
    catch(e:any){ this.announce = 'Import failed: '+e.message; }
  }

  private renderRow(key: string) {
    const v = this.tokens[key];
    const diff = getDiff(key);
    const isRecent = this.recent.slice(0,5).includes(key);
    return html`<tr class=${isRecent?'recent':''} title=${diff?`Prev: ${diff.prev}\nCurrent: ${diff.current}`:''}>
      <td><code>${key}</code>${diff? html`<span class="diff"><del>${diff.prev}</del><ins>${diff.current}</ins></span>`:null}</td>
      <td><input aria-label=${`Token ${key}`} .value=${v} @input=${(e:Event)=>this.onInput(key, (e.target as HTMLInputElement).value)} /></td>
    </tr>`;
  }

  private renderCategory(cat: string, keys: string[]) {
    const collapsed = this.collapsedCats.has(cat);
    return html`<section class="cat">
      <h5><button class="cat-toggle" @click=${()=>this.toggleCat(cat)} aria-label=${collapsed?`Expand ${cat}`:`Collapse ${cat}`}>${collapsed?'▶':'▼'}</button>${cat}</h5>
      ${collapsed? null: html`<table><thead><tr><th style="width:50%">Name</th><th>Value</th></tr></thead><tbody>
        ${keys.map(k=>this.renderRow(k))}
      </tbody></table>`}
    </section>`;
  }

  render() {
    return html`
      <h4>Design Tokens</h4>
      <div class="actions" style="margin-bottom:4px;">
        <button @click=${()=>this.undo()} ?disabled=${!this.undoable} title="Undo (Ctrl+Z)">Undo</button>
        <button @click=${()=>this.redo()} ?disabled=${!this.redoable} title="Redo (Ctrl+Y)">Redo</button>
        <button @click=${()=>resetTokens()} title="Reset to defaults (undoable)">Reset</button>
        <button @click=${()=>this.genSnapshot()}>Export</button>
        <button @click=${()=>this.copySnapshot()} ?disabled=${!this.snapshotText}>Copy</button>
        <button @click=${()=>this.importSnapshot()} ?disabled=${!this.snapshotText}>Import</button>
        <label style="display:inline-flex; align-items:center; gap:4px; font-size:10px;"><input type="checkbox" .checked=${this.importMerge} @change=${(e:Event)=>this.importMerge=(e.target as HTMLInputElement).checked} /> Merge</label>
      </div>
      ${Object.entries(this.categories).map(([cat, keys])=> this.renderCategory(cat, keys))}
      <div class="export-box">
        <textarea class="snapshot" placeholder="Snapshot JSON" .value=${this.snapshotText} @input=${(e:Event)=>this.snapshotText=(e.target as HTMLTextAreaElement).value}></textarea>
      </div>
      <details class="rev"><summary>Revision Timeline (${this.revisions.length})</summary>
        <ul class="revs">
          ${this.revisions.map(r=> html`<li><span><code class="key">${r.key}</code> <em>${r.reason}</em></span><span>${new Date(r.ts).toLocaleTimeString()}</span></li>`)}
        </ul>
      </details>
      <div class="actions">
        <small style="opacity:.7;">Recent changes highlighted • Hover row for diff</small>
      </div>
      <div class="sr" aria-live="polite">${this.announce}</div>
    `;
  }
}
