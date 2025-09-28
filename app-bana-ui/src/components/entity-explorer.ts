import { LitElement, html, css } from 'lit';
import { customElement, state } from 'lit/decorators.js';

interface QueryState {
  entity: string;
  limit: number;
  offset: number;
  q: string;
  fields: string;
  sort: string;
  count: boolean;
  filters: { field: string; value: string; id: string }[];
}

@customElement('entity-explorer')
export class EntityExplorer extends LitElement {
  static styles = css`
    :host { display:block; font-family: system-ui, sans-serif; padding:16px; }
    h1 { margin:0 0 12px; font-size:20px; }
    .grid { display:grid; grid-template-columns: 260px 1fr; gap:16px; align-items:start; }
    .panel { background:#fff; border:1px solid #d0d4db; border-radius:6px; padding:12px; box-shadow:0 1px 2px rgba(0,0,0,.05); }
    .panel h2 { margin:0 0 8px; font-size:15px; font-weight:600; }
    label.small { font-size:11px; font-weight:600; text-transform:uppercase; display:block; margin-bottom:4px; color:#555; }
    input, select, textarea { font: inherit; padding:6px 8px; border:1px solid #b6bcc6; border-radius:4px; width:100%; box-sizing:border-box; }
    textarea { resize: vertical; }
    button { cursor:pointer; border:1px solid #646b74; background:#f8f9fa; border-radius:4px; padding:6px 12px; font:inherit; font-size:13px; }
    button.primary { background:#2962ff; color:#fff; border-color:#1e55ea; }
    button.danger { background:#c62828; color:#fff; border-color:#b71c1c; }
    button:disabled { opacity:.55; cursor:not-allowed; }
    table { width:100%; border-collapse:collapse; font-size:12.5px; }
    th,td { border:1px solid #e1e4e9; padding:4px 6px; text-align:left; vertical-align:top; }
    th { background:#f5f6f8; position:sticky; top:0; z-index:1; }
    .toolbar { display:flex; gap:6px; flex-wrap:wrap; align-items:center; margin-bottom:8px; }
    .filters-table { width:100%; border-collapse:collapse; margin-top:4px; }
    .filters-table th, .filters-table td { border:1px solid #e1e4e9; padding:2px 4px; font-size:12px; }
    .chip { display:inline-block; background:#eef2f8; border-radius:12px; padding:2px 8px; font-size:11px; margin:2px 4px 2px 0; }
    pre { background:#111; color:#cfd8dc; padding:8px; border-radius:4px; max-height:280px; overflow:auto; font-size:11px; }
    .mono { font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace; }
    .row { display:flex; gap:8px; }
    .grow { flex:1; }
    .sep { border-top:1px solid #e0e3e8; margin:12px 0; }
    .small { font-size:12px; color:#566; }
    .pill { background:#2962ff; color:#fff; padding:2px 8px; border-radius:20px; font-size:11px; }
    .muted { color:#777; }
    .right { text-align:right; }
    .warn { color:#ad6800; font-size:11px; }
    .success { color:#2e7d32; font-size:12px; }
    .error { color:#b00020; font-size:12px; }
    .nowrap { white-space:nowrap; }
  `;

  @state() private entities: string[] = [];
  @state() private loadingEntities = false;
  @state() private loadingData = false;
  @state() private result: any = null;
  @state() private rawResponse = '';
  @state() private query: QueryState = {
    entity: '', limit: 25, offset: 0, q: '', fields: '', sort: '', count: false,
    filters: []
  };
  @state() private batchInput = '[\n  { }\n]';
  @state() private batchResult: any = null;
  @state() private batchError: string | null = null;
  @state() private authToken: string = '';

  connectedCallback(): void {
    super.connectedCallback();
    this.authToken = localStorage.getItem('appbana_token') || '';
    // restore last query
    try { const saved = localStorage.getItem('appbana_explorer_query'); if (saved) { const obj = JSON.parse(saved); this.query = { ...this.query, ...obj }; } } catch {}
    this.loadEntities();
  }

  private setToken() {
    const clean = (this.authToken||'').replace(/[^\x20-\x7E]/g,'').trim();
    this.authToken = clean;
    if (clean) localStorage.setItem('appbana_token', clean); else localStorage.removeItem('appbana_token');
    this.requestUpdate();
  }

  private authHeaders(): Record<string,string> { return this.authToken? { 'X-AppBana-Token': this.authToken } : {}; }

  private async loadEntities() {
    this.loadingEntities = true;
    try {
      const r = await fetch('/schema', { headers: this.authHeaders() });
      if (r.ok) {
        const names: string[] = await r.json();
        this.entities = names;
        if (!this.query.entity && names.length) {
          this.query = { ...this.query, entity: names[0] };
        }
      }
    } catch (e) {
      console.error(e);
    } finally {
      this.loadingEntities = false;
    }
  }

  private updateQuery<K extends keyof QueryState>(key: K, value: QueryState[K]) {
    this.query = { ...this.query, [key]: value } as QueryState;
    // persist lightweight snapshot (exclude filters with empty field)
    const snapshot = { ...this.query, filters: this.query.filters.filter(f=>f.field.trim()) };
    localStorage.setItem('appbana_explorer_query', JSON.stringify(snapshot));
  }

  private addFilterRow() {
    const id = crypto.randomUUID();
    this.updateQuery('filters', [...this.query.filters, { field: '', value: '', id }]);
  }
  private updateFilter(id: string, part: 'field'|'value', value: string) {
    this.updateQuery('filters', this.query.filters.map(f => f.id === id ? { ...f, [part]: value } : f));
  }
  private removeFilter(id: string) { this.updateQuery('filters', this.query.filters.filter(f => f.id !== id)); }

  private buildFilterParam(): string | undefined {
    const parts: string[] = [];
    for (const f of this.query.filters) {
      const field = f.field.trim();
      if (!field) continue;
      parts.push(field + ':' + f.value.trim());
    }
    return parts.length ? parts.join(',') : undefined;
  }

  private buildQueryUrl(): string {
    const { entity, limit, offset, q, fields, sort, count } = this.query;
    if (!entity) return '';
    const params = new URLSearchParams();
    if (limit != null) params.set('limit', String(limit));
    if (offset) params.set('offset', String(offset));
    if (q.trim()) params.set('q', q.trim());
    if (fields.trim()) params.set('fields', fields.trim());
    if (sort.trim()) params.set('sort', sort.trim());
    const filterParam = this.buildFilterParam();
    if (filterParam) params.set('filter', filterParam);
    if (count) params.set('count', 'true');
    return `/api/${encodeURIComponent(entity)}${params.toString() ? ('?' + params.toString()) : ''}`;
  }

  private async runQuery() {
    if (!this.query.entity) return;
    this.loadingData = true; this.result = null; this.rawResponse='';
    try {
      const url = this.buildQueryUrl();
      const r = await fetch(url, { headers: { 'Accept':'application/json', ...this.authHeaders() }});
      const text = await r.text();
      this.rawResponse = text;
      try { this.result = JSON.parse(text); } catch { this.result = { raw: text }; }
    } catch (e:any) {
      this.result = { error: e.message || String(e) };
    } finally {
      this.loadingData = false;
    }
  }

  private page(delta: number) {
    const newOffset = Math.max(0, this.query.offset + delta * this.query.limit);
    this.updateQuery('offset', newOffset);
    this.runQuery();
  }

  private resetPaging() {
    this.updateQuery('offset', 0);
  }

  private deriveDisplayedRows(): any[] {
    if (!this.result) return [];
    if (Array.isArray(this.result)) return this.result;
    if (this.result.rows && Array.isArray(this.result.rows)) return this.result.rows;
    return [];
  }

  private canPrev(): boolean { return this.query.offset > 0; }
  private canNext(): boolean {
    if (!this.result) return false;
    const total = (this.result.total ?? (Array.isArray(this.result)? this.result.length : 0)) as number;
    return (this.query.offset + this.query.limit) < total;
  }

  private async doBatchInsert() {
    if (!this.query.entity) return;
    this.batchError = null; this.batchResult = null;
    let parsed: any;
    try { parsed = JSON.parse(this.batchInput); if (!Array.isArray(parsed)) throw new Error('JSON must be an array'); }
    catch(e:any){ this.batchError = 'Parse error: '+(e.message||String(e)); return; }
    try {
      const r = await fetch(`/api/${encodeURIComponent(this.query.entity)}/batch`, { method:'POST', headers:{'Content-Type':'application/json', ...this.authHeaders()}, body: JSON.stringify(parsed) });
      const body = await r.text();
      try { this.batchResult = JSON.parse(body); } catch { this.batchResult = { raw: body }; }
      if (!r.ok) this.batchError = 'HTTP '+r.status;
      // refresh data after batch insert (reset offset if count only or initial page)
      if (!this.query.count) this.runQuery();
    } catch (e:any) {
      this.batchError = e.message || String(e);
    }
  }

  private renderFilters() {
    return html`
      <table class="filters-table">
        <thead><tr><th style="width:120px">Field</th><th>Value</th><th style="width:38px"></th></tr></thead>
        <tbody>
          ${this.query.filters.map(f => html`<tr>
            <td><input .value=${f.field} @input=${(e:any)=>this.updateFilter(f.id,'field', e.target.value)} placeholder="status" /></td>
            <td><input .value=${f.value} @input=${(e:any)=>this.updateFilter(f.id,'value', e.target.value)} placeholder="ACTIVE" /></td>
            <td class="right"><button @click=${()=>this.removeFilter(f.id)} title="Remove" style="padding:2px 6px">×</button></td>
          </tr>`)}
          <tr><td colspan="3"><button @click=${this.addFilterRow} style="width:100%">+ Add Filter</button></td></tr>
        </tbody>
      </table>
    `;
  }

  private renderResultTable() {
    const rows = this.deriveDisplayedRows();
    if (!rows.length) return html`<div class="small muted">${this.loadingData? 'Loading...' : 'No rows.'}</div>`;
    const cols = new Set<string>();
    rows.slice(0, 20).forEach(r => Object.keys(r).forEach(k => cols.add(k)));
    const headers = Array.from(cols);
    return html`
      <div style="overflow:auto; max-height:420px; border:1px solid #e1e4e9; border-radius:4px;">
        <table>
          <thead><tr>${headers.map(h=> html`<th>${h}</th>`)}</tr></thead>
          <tbody>
            ${rows.map(r => html`<tr>${headers.map(h=> html`<td class="mono">${this.formatCell(r[h])}</td>`)}</tr>`)}
          </tbody>
        </table>
      </div>
      <div class="small" style="margin-top:6px; display:flex; justify-content:space-between; align-items:center;">
        <div>
          ${this.result && !Array.isArray(this.result) && this.result.total != null ? html`<span>Total: <strong>${this.result.total}</strong></span>`:''}
          ${this.result?.query ? html`<span class="chip">q=${this.result.query}</span>`:''}
          ${this.result?.fields ? html`<span class="chip">fields=${this.result.fields.join(',')}</span>`:''}
          ${this.result?.sort ? html`<span class="chip">sort=${(this.result.sort as string[]).join(',')}</span>`:''}
        </div>
        <div style="display:flex; gap:6px;">
          <button @click=${()=>this.page(-1)} ?disabled=${!this.canPrev()}>Prev</button>
          <button @click=${()=>this.page(1)}  ?disabled=${!this.canNext()}>Next</button>
        </div>
      </div>
    `;
  }

  private formatCell(v:any) {
    if (v == null) return html`<span class="muted">null</span>`;
    if (typeof v === 'object') return JSON.stringify(v);
    return String(v);
  }

  private buildCurl(url: string): string {
    const headers: string[] = [];
    if (this.authToken) headers.push(`-H 'X-AppBana-Token: ${this.authToken}'`);
    return `curl -s ${headers.join(' ')} '${url}'`;
  }
  private copy(text: string) {
    navigator.clipboard.writeText(text).catch(err=> console.warn('Clipboard failed', err));
  }

  render() {
    const url = this.buildQueryUrl();
    const fullUrl = url ? (window.location.origin + url) : '/api/{entity}';
    const curl = url ? this.buildCurl(fullUrl) : '';
    return html`
      <h1>Entity Explorer</h1>
      <div class="grid">
        <div class="panel">
          <h2>Query</h2>
          <label class="small">Auth Token</label>
          <div class="row" style="margin-bottom:8px;">
            <input .value=${this.authToken} @input=${(e:any)=> this.authToken = e.target.value} placeholder="X-AppBana-Token" />
            <button @click=${this.setToken}>Save</button>
          </div>
          <label class="small">Entity</label>
          <select .value=${this.query.entity} @change=${(e:any)=>{this.updateQuery('entity', e.target.value); this.resetPaging();}}>
            ${this.entities.map(n=> html`<option value=${n}>${n}</option>`)}
          </select>
          ${this.loadingEntities ? html`<div class="small">Loading entities...</div>`:''}
          <div class="row" style="margin-top:8px;">
            <div class="grow">
              <label class="small">Search (q)</label>
              <input .value=${this.query.q} @input=${(e:any)=>{this.updateQuery('q', e.target.value); this.resetPaging();}} placeholder="substring" />
            </div>
            <div>
              <label class="small">Limit</label>
              <input type="number" style="width:90px" .value=${String(this.query.limit)} @input=${(e:any)=> this.updateQuery('limit', Math.max(1, Math.min(500, Number(e.target.value)||50)))} />
            </div>
            <div>
              <label class="small">Offset</label>
              <input type="number" style="width:90px" .value=${String(this.query.offset)} @input=${(e:any)=> this.updateQuery('offset', Math.max(0, Number(e.target.value)||0))} />
            </div>
          </div>
          <div class="row" style="margin-top:8px;">
            <div class="grow">
              <label class="small">Projection (fields)</label>
              <input .value=${this.query.fields} @input=${(e:any)=>{this.updateQuery('fields', e.target.value); this.resetPaging();}} placeholder="id,firstName,lastName" />
            </div>
          </div>
          <div class="row" style="margin-top:8px;">
            <div class="grow">
              <label class="small">Sort</label>
              <input .value=${this.query.sort} @input=${(e:any)=>{this.updateQuery('sort', e.target.value); this.resetPaging();}} placeholder="-createdAt,firstName" />
            </div>
            <div style="display:flex; flex-direction:column; justify-content:flex-end;">
              <label class="small" style="visibility:hidden;">Count</label>
              <label style="font-size:12px; display:flex; align-items:center; gap:4px;"> <input type="checkbox" .checked=${this.query.count} @change=${(e:any)=> this.updateQuery('count', e.target.checked)} /> count-only </label>
            </div>
          </div>
          <div class="sep"></div>
          <h3 style="margin:0 0 6px; font-size:13px;">Filters (field:value)</h3>
          ${this.renderFilters()}
          <div class="sep"></div>
          <div class="row" style="justify-content:space-between;">
            <div style="display:flex; gap:6px;">
              <button class="primary" @click=${this.runQuery} ?disabled=${!this.query.entity || this.loadingData}>${this.loadingData? 'Running...' : 'Run Query'}</button>
              <button @click=${()=>{this.updateQuery('offset',0); this.runQuery();}} ?disabled=${this.loadingData}>Reset Offset</button>
            </div>
            <button @click=${()=>{this.updateQuery('q',''); this.updateQuery('fields',''); this.updateQuery('sort',''); this.updateQuery('filters', []); this.updateQuery('count', false); this.updateQuery('offset',0);}}>Clear</button>
          </div>
          <div class="sep"></div>
          <h3 style="margin:0 0 4px; font-size:13px;">Batch Insert</h3>
          <textarea style="min-height:120px; font-family:monospace; font-size:11px;" .value=${this.batchInput} @input=${(e:any)=> this.batchInput = e.target.value}></textarea>
          <div class="row" style="margin-top:6px; align-items:center;">
            <button @click=${this.doBatchInsert} ?disabled=${!this.query.entity}>Insert Batch</button>
            ${this.batchError ? html`<span class="error">${this.batchError}</span>`:''}
            ${this.batchResult && !this.batchError ? html`<span class="success">Inserted: ${this.batchResult.inserted ?? '?'}</span>`:''}
          </div>
        </div>

        <div class="panel">
          <h2>Results</h2>
          <div class="small mono" style="margin-bottom:4px; display:flex; flex-wrap:wrap; gap:6px; align-items:center;">
            <span>GET <a href=${url||'#'} target="_blank" rel="noopener" style="text-decoration:none;">${url||'/api/{entity}'}</a></span>
            ${curl? html`<button style="font-size:11px; padding:2px 8px;" @click=${()=>this.copy(curl)} title="Copy curl">Copy cURL</button>`:''}
          </div>
          ${this.query.count && this.result && !Array.isArray(this.result) && this.result.total != null ? html`<div class="pill" style="margin-bottom:8px;">Count: ${this.result.total}</div>`:''}
          ${this.renderResultTable()}
          <details style="margin-top:12px;">
            <summary class="small">Raw Response JSON</summary>
            <pre>${this.rawResponse}</pre>
          </details>
          ${this.batchResult ? html`<details style="margin-top:12px;"><summary class="small">Batch Insert Response</summary><pre>${JSON.stringify(this.batchResult,null,2)}</pre></details>`:''}
        </div>
      </div>
    `;
  }
}

declare global { interface HTMLElementTagNameMap { 'entity-explorer': EntityExplorer; } }
