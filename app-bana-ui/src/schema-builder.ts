import { LitElement, html, css } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import './components/StudioWelcome';
import type { RelationalField } from './models/schema';

interface DatasourceDto {
  name: string;
  type?: string;
  jdbcUrl?: string;
  driver?: string;
  active?: boolean;
}

interface ExistingSchemaSummary {
  name: string;
}

@customElement('schema-builder')
export class SchemaBuilder extends LitElement {
  static styles = css`
    :host { display: block; font-family: system-ui, sans-serif; padding: 16px; }
    h1 { margin: 0 0 12px; font-size: 20px; }
    .layout { display: grid; grid-template-columns: 260px 1fr; gap: 16px; align-items: start; }
    .panel { background: #fff; border: 1px solid #d0d4db; border-radius: 6px; padding: 12px; box-shadow: 0 1px 2px rgba(0,0,0,.05); }
    .panel h2 { margin: 0 0 8px; font-size: 15px; font-weight: 600; }
    .small { font-size: 12px; color: #555; }
    table { width: 100%; border-collapse: collapse; font-size: 13px; }
    th, td { border: 1px solid #e1e4e9; padding: 4px 6px; text-align: left; }
    th { background:#f5f6f8; }
    button { cursor: pointer; border-radius: 4px; border: 1px solid #888; background: #f9f9f9; padding: 4px 10px; font-size: 13px; }
    button.primary { background: #2962ff; color:#fff; border-color:#1e55ea; }
    button.danger { background:#c62828; color:#fff; border-color:#b71c1c; }
    button:disabled { opacity:.5; cursor: not-allowed; }
    input, select { font: inherit; padding: 4px 6px; border:1px solid #b6bcc6; border-radius:4px; width:100%; box-sizing: border-box; }
    .fields-grid { display: grid; grid-template-columns: 140px 90px 60px 70px 70px 46px; gap:4px; align-items: center; }
    .fields-grid header { font-size:11px; font-weight:600; text-transform: uppercase; color:#555; }
    .row { display: contents; }
    .badge { display:inline-block; padding:2px 6px; background:#eef2f8; border-radius:12px; font-size:11px; margin-right:4px; }
    .active { background:#2962ff; color:#fff; }
    .schema-row { cursor: pointer; }
    .schema-row:hover { background:#f2f6ff; }
    pre { background:#111; color:#cfd8dc; padding:8px; border-radius:4px; max-height:180px; overflow:auto; font-size:11px; }
    .flex { display:flex; gap:6px; }
    .gap { gap:8px; }
    .justify-between { justify-content: space-between; }
    .mb { margin-bottom:8px; }
    .mt { margin-top:8px; }
    .nowrap { white-space:nowrap; }
    .divider { border-top:1px solid #e0e3e8; margin:12px 0; }
    .muted { color:#777; }
    .error { color:#b00020; font-size:12px; margin-top:4px; }
    .dup input { border-color:#b00020; background:#ffecec; }
    .tag { display:inline-block; background:#eef; padding:2px 6px; border-radius:10px; font-size:11px; margin-right:4px; }
    .toolbar { display:flex; gap:6px; margin-bottom:8px; }
    .reorder-btn { padding:0 4px; font-size:11px; }
    .danger-outline { background:#fff; color:#c62828; border-color:#c62828; }
    .pill { background:#2962ff; color:#fff; padding:2px 8px; border-radius:20px; font-size:11px; }
    .mini { font-size:11px; }
    textarea.json { width:100%; min-height:140px; font-family:monospace; font-size:11px; }
  `;

  @state() private datasources: DatasourceDto[] = [];
  @state() private schemas: ExistingSchemaSummary[] = [];
  @state() private loadingSchemas = false;
  @state() private loadingDs = false;
  @state() private selectedSchemaName: string | null = null;
  @state() private selectedSchemaDetail: any = null;
  @state() private createName = '';
  @state() private createDatasource = '';
  @state() private createFields: RelationalField[] = [this.blankField(true)];
  @state() private saving = false;
  @state() private previewPlan: string[] | null = null;
  @state() private mode: 'list' | 'create' | 'detail' | 'edit' = 'list';
  @state() private errorMsg: string | null = null;
  @state() private schemaSummaries: {name:string; datasource:string}[] = [];
  @state() private schemaSearch = '';
  @state() private schemaDsFilter = '';
  @state() private migrations: any[] | null = null;
  @state() private showMigrations = false;
  @state() private deleteConfirm = false;
  @state() private deleteDropTable = false;
  @state() private importExportOpen = false;
  @state() private editOriginal = false; // true when editing existing schema

  firstUpdated() {
    this.refreshDatasources();
    this.refreshSchemas();
  }

  private blankField(pk = false): RelationalField {
    return { name: pk ? 'id' : '', type: pk ? 'long' : 'string', primaryKey: pk, autoIncrement: pk, required: pk, length: pk ? undefined : 255 };
  }

  private async refreshDatasources() {
    this.loadingDs = true;
    try {
      const r = await fetch('/ui/datasource/list');
      if (r.ok) {
        const list = await r.json();
        // Only relational show for now (filter by type heuristics)
        this.datasources = (list || []).map((d: any) => ({ name: d.name, type: d.type, jdbcUrl: d.jdbcUrl, driver: d.driver, active: d.active }));
        if (!this.createDatasource && this.datasources.length) {
          const active = this.datasources.find(d => d.active) || this.datasources[0];
            this.createDatasource = active.name;
        }
      }
    } catch (e) {
      console.error(e);
    } finally {
      this.loadingDs = false;
    }
  }

  private async refreshSchemas() {
    this.loadingSchemas = true;
    try {
      const r = await fetch('/schema');
      if (r.ok) {
        const names: string[] = await r.json();
        this.schemas = names.map(n => ({ name: n }));
      }
      // summaries (datasource grouping)
      const s = await fetch('/schema/summaries');
      if (s.ok) {
        this.schemaSummaries = await s.json();
      }
    } catch (e) {
      console.error(e);
    } finally {
      this.loadingSchemas = false;
    }
  }

  // --- helpers for duplicates ---
  private duplicateFieldNames(): Set<string> {
    const counts: Record<string, number> = {};
    for (const f of this.createFields) {
      const n = f.name?.trim().toLowerCase();
      if (!n) continue; counts[n] = (counts[n]||0)+1;
    }
    return new Set(Object.keys(counts).filter(k => counts[k] > 1));
  }

  private addField() {
    this.createFields = [...this.createFields, this.blankField(false)];
  }
  private removeField(idx: number) {
    if (this.createFields[idx].primaryKey) return; // cannot remove pk row
    this.createFields = this.createFields.filter((_, i) => i !== idx);
  }
  private updateField(idx: number, key: keyof RelationalField, value: any) {
    const clone = [...this.createFields];
    const f = { ...clone[idx], [key]: value };
    // enforce only one primary key
    if (key === 'primaryKey' && value) {
      clone.forEach((c, i) => { if (i !== idx) c.primaryKey = false; });
      if (!f.type) f.type = 'long';
    }
    // auto adjust autoIncrement for pk types
    if (key === 'primaryKey') f.autoIncrement = !!value && (f.type === 'int' || f.type === 'integer' || f.type === 'long');
    clone[idx] = f;
    this.createFields = clone;
  }

  private canSave(): boolean {
    if (!this.createName.trim()) return false;
    if (!this.createDatasource) return false;
    if (!this.createFields.length) return false;
    if (!this.createFields.some(f => f.primaryKey)) return false;
    const dups = this.duplicateFieldNames();
    if (dups.size) return false;
    return this.createFields.every(f => f.name && f.type);
  }

  private async doPreview() {
    this.errorMsg = null;
    this.previewPlan = null;
    try {
      const payload = this.buildSchemaPayload();
      const r = await fetch('/schema?preview=true', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
      if (r.ok) {
        this.previewPlan = await r.json();
      } else {
        const e = await r.json().catch(() => ({}));
        this.errorMsg = e.error || ('Preview failed: ' + r.status);
      }
    } catch (e: any) {
      this.errorMsg = e.message || String(e);
    }
  }

  private buildSchemaPayload() {
    const payloadFields = this.createFields.map((f:any) => {
      const out: any = {
        name: f.name.trim(),
        type: f.type,
        primaryKey: !!f.primaryKey,
        autoIncrement: !!f.autoIncrement,
        length: f.length,
        required: !!f.required,
        min: f.min,
        max: f.max,
        pattern: f.pattern
      };
      if (f._originalName && f._originalName !== f.name) out.existingName = f._originalName;
      return out;
    });
    return {
      name: this.createName.trim(),
      datasourceName: this.createDatasource,
      modelKind: 'relational',
      fields: payloadFields
    };
  }

  private async doSave() {
    if (!this.canSave()) return;
    this.saving = true; this.errorMsg = null; this.previewPlan = null;
    try {
      const payload = this.buildSchemaPayload();
      const r = await fetch('/schema', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
      if (r.ok) {
        await this.refreshSchemas();
        this.mode = 'list';
        this.resetCreate();
      } else {
        const e = await r.json().catch(() => ({}));
        this.errorMsg = e.error || ('Save failed: ' + r.status);
      }
    } catch (e: any) {
      this.errorMsg = e.message || String(e);
    } finally {
      this.saving = false;
    }
  }

  private resetCreate() {
    this.createName = '';
    this.previewPlan = null;
    this.createFields = [this.blankField(true)];
  }

  private async selectSchema(name: string) {
    this.selectedSchemaName = name;
    this.mode = 'detail';
    this.selectedSchemaDetail = null;
    this.showMigrations = false; this.migrations = null;
    try {
      const r = await fetch(`/schema/${encodeURIComponent(name)}`);
      if (r.ok) this.selectedSchemaDetail = await r.json();
    } catch (e) { console.error(e); }
  }

  private startEdit() {
    if (!this.selectedSchemaDetail) return;
    const s = this.selectedSchemaDetail;
    this.mode = 'edit';
    this.editOriginal = true;
    this.createName = s.name;
    this.createDatasource = s.datasourceName || this.createDatasource;
    this.createFields = s.fields.map((f:any)=> ({...f, _originalName: f.name}));
    this.previewPlan = null;
    this.errorMsg = null;
  }

  private cancelEdit() {
    if (this.editOriginal) {
      this.mode = 'detail';
      this.createFields = [this.blankField(true)];
      this.editOriginal = false;
    } else {
      this.mode = 'list';
    }
  }

  private async loadMigrations() {
    if (!this.selectedSchemaName) return;
    try {
      const r = await fetch(`/schema/${encodeURIComponent(this.selectedSchemaName)}/migrations`);
      if (r.ok) this.migrations = await r.json();
    } catch (e) { console.error(e); }
  }

  private async doDelete() {
    if (!this.selectedSchemaName) return;
    try {
      const r = await fetch(`/schema/${encodeURIComponent(this.selectedSchemaName)}?dropTable=${this.deleteDropTable}`, { method: 'DELETE' });
      if (r.ok) {
        this.deleteConfirm = false; this.deleteDropTable = false;
        await this.refreshSchemas();
        this.mode = 'list';
        this.selectedSchemaName = null; this.selectedSchemaDetail = null;
      } else {
        const e = await r.json().catch(()=>({}));
        this.errorMsg = e.error || ('Delete failed: '+r.status);
      }
    } catch (e:any) {
      this.errorMsg = e.message || String(e);
    }
  }

  private exportJson() {
    const payload = this.buildSchemaPayload();
    return JSON.stringify(payload, null, 2);
  }
  private importJson(text: string) {
    try {
      const obj = JSON.parse(text);
      if (!obj.name || !Array.isArray(obj.fields)) throw new Error('Invalid schema JSON');
      this.createName = obj.name;
      if (obj.datasourceName) this.createDatasource = obj.datasourceName;
      this.createFields = obj.fields.map((f:any)=> ({...f, _originalName: f.name}));
      this.errorMsg = null;
    } catch (e:any) {
      this.errorMsg = 'Import error: ' + (e.message||String(e));
    }
  }

  private filteredSchemas() {
    let list = this.schemaSummaries.length ? this.schemaSummaries : this.schemas.map(s => ({name:s.name, datasource:''}));
    if (this.schemaSearch.trim()) {
      const q = this.schemaSearch.trim().toLowerCase();
      list = list.filter(s => s.name.toLowerCase().includes(q));
    }
    if (this.schemaDsFilter) list = list.filter(s => s.datasource === this.schemaDsFilter);
    return list;
  }

  private renderFieldEditor(editing = false) {
    const dups = this.duplicateFieldNames();
    return html`
      <div class="fields-grid mb">
        <header>Name</header>
        <header>Type</header>
        <header>Len</header>
        <header>PK</header>
        <header>Req</header>
        <header></header>
        ${this.createFields.map((f:any, idx) => html`
          <div class="row ${dups.has(f.name?.trim().toLowerCase()) ? 'dup' : ''}">
            <div style="display:flex; align-items:center; gap:4px;">
              <input .value=${f.name} @input=${(e: any) => this.updateField(idx,'name', e.target.value)} placeholder="field" ?disabled=${f.primaryKey && editing}>
              <div class="mini" style="display:flex; flex-direction:column; gap:2px;">
                <button class="reorder-btn" @click=${()=>this.reorderField(idx,-1)} title="Move Up">▲</button>
                <button class="reorder-btn" @click=${()=>this.reorderField(idx,1)} title="Move Down">▼</button>
              </div>
            </div>
            <div>
              <select .value=${f.type} @change=${(e: any) => this.updateField(idx,'type', e.target.value)} ?disabled=${f.primaryKey && editing}>
                ${['string','text','int','long','boolean','date','timestamp'].map(t => html`<option value=${t}>${t}</option>`)}
              </select>
            </div>
            <div><input type="number" .value=${f.length ?? ''} @input=${(e:any)=>this.updateField(idx,'length', e.target.value? Number(e.target.value): undefined)} ?disabled=${['string','text','varchar'].indexOf(f.type)<0}></div>
            <div style="text-align:center"><input type="checkbox" .checked=${!!f.primaryKey} @change=${(e:any)=>this.updateField(idx,'primaryKey', e.target.checked)} ?disabled=${editing}></div>
            <div style="text-align:center"><input type="checkbox" .checked=${!!f.required} @change=${(e:any)=>this.updateField(idx,'required', e.target.checked)}></div>
            <div style="text-align:center">
              ${f.primaryKey ? html`<span class="badge active">PK</span>` : html`<button @click=${() => this.removeField(idx)} title="Remove" class="danger" style="padding:0 6px">×</button>`}
            </div>
          </div>
        `)}
      </div>
      <button @click=${this.addField}>+ Add Field</button>
      ${dups.size? html`<div class="error">Duplicate names: ${Array.from(dups).join(', ')}</div>`:''}
    `;
  }

  private renderSchemaList() {
    const filtered = this.filteredSchemas();
    const datasources = Array.from(new Set(this.schemaSummaries.map(s=>s.datasource).filter(Boolean)));
    return html`
      <div class="panel">
        <h2>Schemas ${this.loadingSchemas ? html`<span class="small">(loading...)</span>`:''}</h2>
        <div class="toolbar">
          <button class="primary" @click=${() => (this.mode='create')}>New</button>
          <input style="flex:1" placeholder="Search" .value=${this.schemaSearch} @input=${(e:any)=>{this.schemaSearch=e.target.value; this.requestUpdate();}}>
          ${datasources.length? html`<select .value=${this.schemaDsFilter} @change=${(e:any)=>{this.schemaDsFilter=e.target.value;}}>
            <option value="">All DS</option>
            ${datasources.map(d=> html`<option value=${d}>${d}</option>`)}
          </select>`: ''}
          <button @click=${() => this.refreshSchemas()}>Reload</button>
        </div>
        <table>
          <thead><tr><th>Name</th><th>Datasource</th></tr></thead>
          <tbody>
            ${filtered.map(s => html`<tr class="schema-row" @click=${() => this.selectSchema(s.name)}><td>${s.name}</td><td class="small">${s.datasource||''}</td></tr>`)}
            ${!filtered.length && !this.loadingSchemas ? html`<tr><td colspan="2" class="small">No schemas.</td></tr>`: ''}
          </tbody>
        </table>
      </div>
    `;
  }

  private renderCreate(editing = false) {
    return html`
      <div class="panel">
        <h2>${editing? 'Edit Schema':'Create Relational Schema'}</h2>
        <div class="mb">
          <label class="small">Name</label>
          <input .value=${this.createName} @input=${(e:any)=>this.createName=e.target.value} placeholder="e.g. customer" ?disabled=${editing} />
        </div>
        <div class="mb">
          <label class="small">Datasource</label>
          <select .value=${this.createDatasource} @change=${(e:any)=>this.createDatasource=e.target.value} ?disabled=${editing}>
            ${this.datasources.map(d => html`<option value=${d.name}>${d.name}${d.active? ' (active)':''}</option>`)}
          </select>
          <div class="small muted">Only relational datasources are listed.</div>
        </div>
        <div class="divider"></div>
        <h3 class="small" style="margin:0 0 4px">Fields</h3>
        ${this.renderFieldEditor(editing)}
        <div class="mt small"><button class="mini" @click=${()=>this.importExportOpen=!this.importExportOpen}>${this.importExportOpen? 'Hide':'Import/Export JSON'}</button></div>
        ${this.importExportOpen ? html`<div class="mt">
          <textarea class="json" @change=${(e:any)=>this.importJson(e.target.value)}>${this.exportJson()}</textarea>
          <div class="small muted">Editing text will attempt import on change.</div>
        </div>`: ''}
        ${this.errorMsg ? html`<div class="error">${this.errorMsg}</div>`: ''}
        ${this.previewPlan ? html`<div class="mt"><strong>Migration Preview</strong><pre>${this.previewPlan.join('\n')}</pre></div>`: ''}
        <div class="mt flex gap">
          <button @click=${()=>this.cancelEdit()} >Cancel</button>
          <button @click=${()=>this.doPreview()} ?disabled=${!this.canSave() || this.saving}>Preview</button>
          <button class="primary" @click=${()=>this.doSave()} ?disabled=${!this.canSave() || this.saving}>${this.saving? 'Saving...': (editing? 'Save Changes':'Save')}</button>
        </div>
      </div>
    `;
  }

  private renderDetail() {
    const s = this.selectedSchemaDetail;
    return html`
      <div class="panel">
        <div class="flex justify-between mb">
          <h2>Schema: ${this.selectedSchemaName}</h2>
          <div class="flex gap">
            <button @click=${()=>{this.mode='list'; this.selectedSchemaName=null; this.selectedSchemaDetail=null;}}>Back</button>
            <button class="primary" @click=${()=>this.startEdit()}>Edit</button>
            <button class="danger-outline" @click=${()=>{this.deleteConfirm=true;}}>Delete</button>
          </div>
        </div>
        ${!s ? html`<div class="small">Loading...</div>`: html`
          <div class="mb small muted">Datasource: ${s.datasourceName || '(default)'} | ModelKind: ${s.modelKind || 'relational'}</div>
          <table class="mb">
            <thead><tr><th>Name</th><th>Type</th><th>PK</th><th>Auto</th><th>Req</th><th>Len</th></tr></thead>
            <tbody>
              ${s.fields.map((f:any)=> html`<tr><td>${f.name}</td><td>${f.type}</td><td>${f.primaryKey?'✔':''}</td><td>${f.autoIncrement?'✔':''}</td><td>${f.required?'✔':''}</td><td>${f.length??''}</td></tr>`)}
            </tbody>
          </table>
          <div class="mb flex gap">
            <button @click=${()=>{this.showMigrations=!this.showMigrations; if (this.showMigrations && !this.migrations) this.loadMigrations();}}>${this.showMigrations? 'Hide':'Show'} Migrations</button>
            <button @click=${()=>{this.importExportOpen=!this.importExportOpen;}}>${this.importExportOpen? 'Hide JSON':'Export JSON'}</button>
          </div>
          ${this.importExportOpen? html`<pre>${JSON.stringify(s, null, 2)}</pre>`:''}
          ${this.showMigrations ? html`<div class="mt">
            <h3 class="small" style="margin:0 0 4px">Migration History</h3>
            ${!this.migrations? html`<div class="small">Loading...</div>`: this.migrations.length? html`<pre>${this.migrations.map((m:any)=>`-- ${m.executedAt} [${m.datasource}]\n${m.sql}`).join('\n\n')}</pre>`: html`<div class="small muted">No migrations recorded.</div>`}
          </div>`: ''}
          <pre>${JSON.stringify(s, null, 2)}</pre>
          ${this.deleteConfirm ? html`<div class="mt" style="border:1px solid #f44336; padding:8px; border-radius:4px;">
            <div class="small"><strong>Confirm Delete</strong> – This removes schema metadata.${this.deleteDropTable? ' Table will be dropped.':''}</div>
            <label class="small" style="display:flex; align-items:center; gap:4px; margin:4px 0;"><input type="checkbox" .checked=${this.deleteDropTable} @change=${(e:any)=>this.deleteDropTable=e.target.checked}>Drop underlying table</label>
            <div class="flex gap">
              <button @click=${()=>{this.deleteConfirm=false; this.deleteDropTable=false;}}>Cancel</button>
              <button class="danger" @click=${()=>this.doDelete()}>Delete</button>
            </div>
          </div>`: ''}
        `}
      </div>
    `;
  }

  private reorderField(idx: number, dir: -1 | 1) {
    const arr = [...this.createFields];
    const ni = idx + dir; if (ni < 0 || ni >= arr.length) return;
    const tmp = arr[idx]; arr[idx] = arr[ni]; arr[ni] = tmp;
    this.createFields = arr;
  }

  render() {
    return html`
      <h1>Schema Builder</h1>
      <div class="layout">
        ${this.mode==='list' ? this.renderSchemaList() : this.mode==='create' ? this.renderCreate(false) : this.mode==='edit'? this.renderCreate(true) : this.renderDetail()}
        <div class="panel">
          <h2>Help</h2>
          <p class="small muted">Create & edit relational schemas. Other model kinds are planned and currently disabled.</p>
          <ul class="small">
            <li>Exactly one primary key required</li>
            <li>Auto-increment allowed on int/long PK</li>
            <li>Preview generates migration plan</li>
            <li>Save persists & applies migration</li>
            <li>Rename: edit field name (non-PK) – backend auto migration</li>
            <li>Reorder fields (affects future display only)</li>
          </ul>
          <div class="divider"></div>
          <h3 class="small">Environment</h3>
            <div class="small">Datasources loaded: ${this.datasources.length}</div>
            <div class="small">Schemas loaded: ${this.schemas.length}</div>
          <div class="divider"></div>
          <h3 class="small">Quick Tips</h3>
          <div class="small muted">Set PK first, then add fields. Duplicate names blocked. Use JSON import/export for rapid iteration.</div>
        </div>
      </div>
    `;
  }
}
