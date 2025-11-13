// StudioTableLive.ts - Lit component for runtime table rendering with live data
import { LitElement, html, css } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { fetchTableData, bulkDelete, bulkExport } from '../../core/api-client';
import type { ComponentNode } from '../../models/metadata';

@customElement('studio-table-live')
export class StudioTableLive extends LitElement {
  @property({ type: Object }) node!: ComponentNode;
  @state() private data: any = null;
  @state() private loading: boolean = false;
  @state() private error: string = '';
  @state() private page: number = 1;
  @state() private pageSize: number = 25;
  @state() private total: number = 0;
  @state() private filters: Record<string,string> = {};
  @state() private effectiveTheme: string = 'default';
  @state() private runtimeThemeOverride: string | null = null;
  @state() private runtimeThemeTokens: Record<string,string> | null = null;
  @state() private selectedIds: Set<string> = new Set<string>();

  public static readonly styles = css`
    .table-container {
      background: var(--tbl-container-bg, linear-gradient(135deg, #e2e8f0 0%, #f8fafc 100%));
      border-radius: 18px;
      box-shadow: 0 6px 28px rgba(30,41,59,0.10);
      padding: 1.5rem 1.25rem 2rem;
      margin: 2.5rem 0;
      display: flex;
      flex-direction: column;
      align-items: stretch;
    }
    .table-wrapper {
      max-height: 560px; /* adjustable viewport height */
      overflow-y: auto;
      overflow-x: hidden;
      border-radius: 14px;
      box-shadow: 0 2px 6px rgba(0,0,0,0.06);
      background: #fff;
      position: relative;
      /* Create its own stacking context to help sticky headers */
      will-change: scroll-position;
    }
    .table-live {
      width: 100%;
      border-collapse: separate;
      border-spacing: 0;
      background: #fff;
      /* Remove overflow so sticky headers aren't clipped */
      border-radius: 14px;
      font-size: 15px;
      transition: box-shadow 0.2s;
    }
      /* THEME: minimal */
      .table-theme-minimal.table-container { background: #ffffff; box-shadow: 0 1px 4px rgba(0,0,0,0.08); border: 1px solid #e2e8f0; }
      .table-theme-minimal .table-live th { background: #f8fafc; color:#1e293b; border-bottom:1px solid #e2e8f0; }
      .table-theme-minimal .table-live tbody tr:hover td { background:#f1f5f9; }
      .table-theme-minimal .pagination-bar { background:#f8fafc; }

      /* THEME: dark */
      .table-theme-dark.table-container { background: linear-gradient(135deg,#1e293b,#0f172a); box-shadow: 0 6px 32px rgba(0,0,0,0.45); }
      .table-theme-dark .table-wrapper { background:#0f172a; }
      .table-theme-dark .table-live { background:#1e293b; color:#e2e8f0; }
      .table-theme-dark .table-live th { background: linear-gradient(90deg,#0f172a,#1e293b,#334155); color:#f8fafc; border-bottom:2px solid #334155; }
      .table-theme-dark .table-live td { background:#1e293b; color:#f1f5f9; border-bottom:1px solid #334155; }
      .table-theme-dark .table-live tbody tr:nth-child(even) td { background:#0f1f31; }
      .table-theme-dark .table-live tbody tr:hover td { background:#243549; }
      .table-theme-dark .pagination-bar { background:rgba(30,41,59,0.85); border-color:#334155; }
      .table-theme-dark .filter-row th { background:#243549; border-bottom:2px solid #334155; }
      .table-theme-dark .filter-row input { background:#1e293b; color:#f8fafc; border-color:#334155; }

      /* THEME: striped (strong zebra + subtle gradient header) */
      .table-theme-striped .table-live th { background: linear-gradient(90deg,#475569,#64748b); }
      .table-theme-striped .table-live tbody tr:nth-child(odd) td { background:#ffffff; }
      .table-theme-striped .table-live tbody tr:nth-child(even) td { background:#f1f5f9; }
      .table-theme-striped .table-live tbody tr:hover td { background:#e2e8f0; }

      /* THEME: compact (reduced paddings, smaller font) */
      .table-theme-compact .table-live th { padding:10px 12px; font-size:14px; }
      .table-theme-compact .table-live td { padding:8px 12px; font-size:13px; }
      .table-theme-compact .filter-row th { top: 42px; padding:4px 8px; }
      .table-theme-compact .filter-row input { padding:4px 6px; font-size:12px; }
      .table-theme-compact .pagination-bar { padding:0.35rem 0.6rem; }

      /* THEME: soft (pastel palette) */
      .table-theme-soft.table-container { background: linear-gradient(135deg,#fdf2f8,#f0f9ff); }
      .table-theme-soft .table-live th { background: linear-gradient(90deg,#f472b6,#60a5fa); color:#ffffff; }
      .table-theme-soft .table-live td { background:#ffffff; }
      .table-theme-soft .table-live tbody tr:nth-child(even) td { background:#fdf2f8; }
      .table-theme-soft .table-live tbody tr:hover td { background:#f0f9ff; }
      .table-theme-soft .pagination-bar { background:rgba(255,255,255,0.7); }
      .table-theme-soft .filter-row th { background:#fdf2f8; }

      @media (prefers-color-scheme: dark) {
        .table-theme-soft.table-container { background: linear-gradient(135deg,#3b0d3f,#102a43); }
      }

    .table-live th {
      background: var(--tbl-header-bg, linear-gradient(90deg, #1e293b 0%, #1d4ed8 50%, #2563eb 100%));
      color: var(--tbl-header-color, #fff);
      font-weight: 700;
      padding: 16px 18px;
      border-bottom: 2px solid #334155;
      text-align: left;
      letter-spacing: 0.02em;
      font-size: 16px;
      position: sticky;
      top: 0; /* header sticks to top of scroll container */
      z-index: 10;
      /* Ensure header covers cell content during scroll */
      background-clip: padding-box;
    }
    .filter-row th {
      background: #f1f5f9;
      border-bottom: 2px solid #e2e8f0;
      padding: 6px 10px;
      position: sticky;
      top: 56px; /* below main header row (approx header height) */
      z-index: 9;
    }
    .filter-row input {
      width: 100%;
      box-sizing: border-box;
      padding: 6px 8px;
      border-radius: 6px;
      border: 1px solid #cbd5e1;
      font-size: 13px;
      background: #fff;
      color: #334155;
    }
    .filter-row input:focus {
      outline: 2px solid #93c5fd;
      outline-offset: 1px;
    }
    .table-live td {
      padding: 14px 18px;
      border-bottom: 1px solid var(--tbl-border-color, #e2e8f0);
      background: var(--tbl-row-odd-bg, #ffffff);
      color: var(--tbl-cell-color, #334155);
      transition: background 0.2s;
      font-size: 15px;
    }
    .table-live tbody tr:last-child td {
      border-bottom: none;
    }
    .table-live tbody tr:hover td { background: var(--tbl-row-hover-bg, #f1f5f9); }
    .table-live thead th:first-child { border-top-left-radius: 14px; }
    .table-live thead th:last-child { border-top-right-radius: 14px; }
  .table-live tbody tr:nth-child(even) td { background: var(--tbl-row-even-bg, #f8fafc); }
    .table-actions button {
      margin-right: 8px;
      padding: 7px 16px;
      border: none;
      border-radius: 6px;
      background: #2563eb;
      color: #fff;
      font-size: 15px;
      cursor: pointer;
      font-weight: 600;
      box-shadow: 0 1px 4px rgba(37,99,235,0.08);
      transition: background 0.2s, box-shadow 0.2s;
    }
    .table-actions button:last-child {
      margin-right: 0;
    }
    .table-actions button:hover {
      background: #1e40af;
      box-shadow: 0 2px 8px rgba(30,64,175,0.12);
    }
    .table-actions button:focus-visible {
      outline: 3px solid #93c5fd;
      outline-offset: 2px;
    }
    .table-error {
      color: #ef4444;
      background: #fef2f2;
      border-radius: 8px;
      padding: 1rem;
      margin: 1rem 0;
      font-weight: 500;
    }
    .pagination-bar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      flex-wrap: wrap;
      margin: 0 0 1rem 0;
      background: var(--tbl-pagination-bg, rgba(255,255,255,0.75));
      backdrop-filter: blur(4px);
      padding: 0.5rem 0.75rem;
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      position: sticky;
      top: 0;
      z-index: 2;
    }
    .pagination-bar button[disabled] {
      opacity: 0.45;
      cursor: not-allowed;
    }
    .pagination-bar button {
      padding: 6px 14px;
      border-radius: 6px;
      border: 1px solid #cbd5e1;
      background: #ffffff;
      color: #1e293b;
      font-weight: 600;
      cursor: pointer;
      box-shadow: 0 1px 2px rgba(0,0,0,0.05);
      transition: background 0.15s, box-shadow 0.15s;
    }
    .pagination-bar button:hover:not([disabled]) {
      background: #f1f5f9;
      box-shadow: 0 2px 6px rgba(0,0,0,0.08);
    }
    .pagination-bar select {
      padding: 4px 6px;
      border-radius: 6px;
      border: 1px solid #cbd5e1;
      background: #fff;
      color: #1e293b;
    }
    @media (max-width: 700px) {
      .table-container {
        padding: 1rem 0.5rem 1.25rem;
      }
      .table-live th, .table-live td {
        padding: 8px 6px;
        font-size: 13px;
      }
      .pagination-bar {
        position: static;
      }
    }
  `;

  // Use lifecycle without returning a Promise type per lint rule; wrap async logic
  firstUpdated() {
    this.initializeTable();
  }

  private async initializeTable() {
    if (!this.node?.props?.entity || !Array.isArray(this.node.props.fields) || this.node.props.fields.length === 0) {
      this.error = 'No entity or columns selected.';
      return;
    }
    this.loadRuntimeThemeOverrides();
    const rawTheme = (this.runtimeThemeOverride ?? this.node.props.theme ?? 'default').toLowerCase();
    this.applyTheme(rawTheme);
    this.pageSize = this.node.props.pageSize || 25;
    await this.loadPage(1);
  }

  private loadRuntimeThemeOverrides() {
    const key = `table-theme-${this.node?.id}`;
    try {
      const saved = globalThis.localStorage?.getItem(key);
      if (saved) {
        const obj = JSON.parse(saved);
        if (obj?.theme) this.runtimeThemeOverride = String(obj.theme);
        if (obj?.tokens && typeof obj.tokens === 'object') this.runtimeThemeTokens = obj.tokens as Record<string,string>;
      }
    } catch (e) {
      console.error('Failed to load theme overrides', e);
    }
  }

  private applyTheme(theme: string) {
    if (theme === 'auto') {
      const mm = globalThis.matchMedia ? globalThis.matchMedia('(prefers-color-scheme: dark)') : null;
      this.effectiveTheme = mm?.matches ? 'dark' : 'default';
      if (mm) {
        const listener = (ev: MediaQueryListEvent) => {
          this.effectiveTheme = ev.matches ? 'dark' : 'default';
          this.requestUpdate();
        };
        mm.addEventListener('change', listener);
      }
    } else {
      this.effectiveTheme = theme;
    }
  }

  private async loadPage(page: number) {
    this.loading = true;
    this.error = '';
    try {
      if (!this.node?.props) {
        this.error = 'Component configuration missing.';
        return;
      }
      const entity = this.node.props.entity;
      const fields = Array.isArray(this.node.props.fields) ? this.node.props.fields.map((f: any) => f.name) : [];
      const sort = this.node.props.sort || '';
      const result = await fetchTableData(entity, fields, { pageSize: this.pageSize, sort, page, filters: this.filters });
      this.data = result;
      this.total = typeof result.total === 'number' ? result.total : (result.rows?.length || 0);
      this.page = page;
      // Clear selection on page/fetch change
      this.selectedIds = new Set<string>();
    } catch (err: any) {
      this.error = err?.message || 'Failed to fetch table data.';
    } finally {
      this.loading = false;
    }
  }

  private changePage(delta: number) {
    const target = this.page + delta;
    const totalPages = Math.max(1, Math.ceil(this.total / this.pageSize));
    if (target < 1 || target > totalPages) return;
    this.loadPage(target);
  }

  private onPageSizeChange(e: Event) {
    const value = Number.parseInt((e.target as HTMLSelectElement).value, 10);
    if (!Number.isNaN(value) && value > 0) {
      this.pageSize = value;
      this.loadPage(1);
    }
  }

  private onFilterInput(fieldName: string, e: Event) {
    const value = (e.target as HTMLInputElement).value;
    this.filters = { ...this.filters, [fieldName]: value };
    // Reset to first page and re-fetch server-side filtered data
    this.loadPage(1);
  }

  // --- Rendering helpers to reduce cognitive complexity ---
  private buildPagination(startIdx: number, endIdx: number, totalPages: number) {
    if (this.error || this.total === 0) return null;
    return html`<div class="pagination-bar" role="navigation" aria-label="Pagination">
      <div style="font-size:0.8rem;color:#475569;">Rows ${startIdx}-${endIdx} of ${this.total}</div>
      <div style="display:flex;align-items:center;gap:0.5rem;">
        <button @click=${() => this.changePage(-1)} ?disabled=${this.page === 1} aria-label="Previous page">Prev</button>
        <span style="min-width:70px;text-align:center;font-size:0.75rem;color:#334155;">Page ${this.page} / ${totalPages}</span>
        <button @click=${() => this.changePage(1)} ?disabled=${this.page >= totalPages} aria-label="Next page">Next</button>
      </div>
      <label style="font-size:0.75rem;color:#475569;display:flex;align-items:center;gap:4px;">Page size:
        <select @change=${this.onPageSizeChange} aria-label="Select page size">
          ${[10,25,50,100].map(size => html`<option value=${size} ?selected=${size===this.pageSize}>${size}</option>`)}
        </select>
      </label>
      <label style="font-size:0.75rem;color:#475569;display:flex;align-items:center;gap:4px;">Theme:
        <select @change=${this.onThemeChange} aria-label="Select theme">
          ${['default','minimal','dark','striped','compact','soft','auto','custom'].map(t => html`<option value=${t} ?selected=${(this.runtimeThemeOverride ?? this.node?.props?.theme ?? 'default').toLowerCase()===t}>${t}</option>`)}
        </select>
      </label>
      ${((this.runtimeThemeOverride ?? this.node?.props?.theme ?? 'default').toLowerCase()==='custom') ? html`
        <button @click=${this.promptCustomTokens} aria-label="Edit custom theme" style="padding:4px 10px;border-radius:6px;border:1px solid #cbd5e1;background:#fff;color:#1e293b;">Edit Tokens</button>
      `: ''}
    </div>`;
  }

  private buildHeader(fields: any[], actions: string[], multiSelect: boolean, rowsOnPage: any[]) {
    const allChecked = multiSelect && rowsOnPage.length > 0 && rowsOnPage.every(r => this.selectedIds.has(String(this.getRowId(r))));
    return html`<tr>
      ${multiSelect ? html`<th><input type="checkbox" aria-label="Select all on page" .checked=${allChecked} @change=${this.onSelectAllChange}></th>` : ''}
      ${fields.map((f: any) => html`<th>${f.label || f.name}</th>`)}
      ${actions.length > 0 ? html`<th>Actions</th>` : ''}
    </tr>`;
  }

  private buildFilterRow(fields: any[], actions: string[], multiSelect: boolean) {
    return html`<tr class="filter-row">
      ${multiSelect ? html`<th></th>` : ''}
      ${fields.map((f: any) => html`<th>
        <input type="text" aria-label="Filter ${f.label || f.name}" placeholder="Filter..." @input=${(e: Event) => this.onFilterInput(f.name, e)} .value=${this.filters[f.name] || ''}>
      </th>`)}
      ${actions.length > 0 ? html`<th></th>` : ''}
    </tr>`;
  }

  private buildBody(rows: any[], fields: any[], actions: string[], multiSelect: boolean) {
    return rows.map((row: any) => {
      const id = String(this.getRowId(row));
      const checked = this.selectedIds.has(id);
      return html`<tr>
        ${multiSelect ? html`<td><input type="checkbox" aria-label="Select row" .checked=${checked} @change=${(e: Event) => this.onRowSelectChange(id, e)}></td>` : ''}
        ${fields.map((f: any) => html`<td>${row[f.name] ?? ''}</td>`)}
        ${actions.length > 0 ? html`<td class="table-actions">
          ${actions.map((action: string) => html`<button aria-label="${action} row" @click=${() => alert(action + ' not implemented yet.')}>${action.charAt(0).toUpperCase() + action.slice(1)}</button>`)}
        </td>` : ''}
      </tr>`;
    });
  }

  private computeCustomStyle(rawTheme: string): string {
    if (rawTheme !== 'custom') return '';
    const map: Record<string,string> = this.runtimeThemeTokens ?? (this.node?.props?.themeTokens as Record<string,string>) ?? {};
    const tokenToVar: Record<string,string> = {
      headerBg: '--tbl-header-bg',
      headerColor: '--tbl-header-color',
      rowEvenBg: '--tbl-row-even-bg',
      rowOddBg: '--tbl-row-odd-bg',
      rowHoverBg: '--tbl-row-hover-bg',
      cellColor: '--tbl-cell-color',
      borderColor: '--tbl-border-color',
      paginationBg: '--tbl-pagination-bg',
      containerBg: '--tbl-container-bg'
    };
    const parts: string[] = [];
    for (const [k,v] of Object.entries(map)) if (v && tokenToVar[k]) parts.push(`${tokenToVar[k]}:${v}`);
    return parts.join(';');
  }

  private readonly onThemeChange = (e: Event) => {
    const theme = String((e.target as HTMLSelectElement).value).toLowerCase();
    this.runtimeThemeOverride = theme;
    // Persist selection
    try {
      const key = `table-theme-${this.node?.id}`;
      const payload = { theme, tokens: this.runtimeThemeTokens };
      globalThis.localStorage?.setItem(key, JSON.stringify(payload));
    } catch {}
    // Resolve effective theme for 'auto' or explicit
    this.applyTheme(theme);
    this.requestUpdate();
  };

  private readonly promptCustomTokens = () => {
    const existing = JSON.stringify(this.runtimeThemeTokens ?? this.node?.props?.themeTokens ?? {
      headerBg: 'linear-gradient(90deg,#1e293b,#2563eb)',
      headerColor: '#ffffff',
      rowEvenBg: '#f8fafc',
      rowOddBg: '#ffffff',
      rowHoverBg: '#f1f5f9',
      cellColor: '#334155',
      borderColor: '#e2e8f0',
      paginationBg: 'rgba(255,255,255,0.75)',
      containerBg: 'linear-gradient(135deg,#e2e8f0,#f8fafc)'
    }, null, 2);
    const input = globalThis.prompt?.('Paste JSON theme tokens (keys: headerBg, headerColor, rowEvenBg, rowOddBg, rowHoverBg, cellColor, borderColor, paginationBg, containerBg):', existing);
    if (input == null) return;
    try {
      const obj = JSON.parse(input);
      if (obj && typeof obj === 'object') {
        this.runtimeThemeTokens = obj as Record<string,string>;
        // Persist
        const key = `table-theme-${this.node?.id}`;
        const payload = { theme: 'custom', tokens: this.runtimeThemeTokens };
        globalThis.localStorage?.setItem(key, JSON.stringify(payload));
        this.requestUpdate();
      }
    } catch (err) {
      console.error('Invalid custom tokens JSON', err);
      this.error = 'Invalid custom tokens JSON.';
      setTimeout(() => { this.error = ''; this.requestUpdate(); }, 3000);
    }
  };

  render() {
    if (this.loading) return html`<div>Loading table data...</div>`;
    const fields = Array.isArray(this.node?.props?.fields) ? this.node.props.fields : [];
  const actions: string[] = this.node?.props?.actions || [];
  const multiSelect: boolean = Boolean(this.node?.props?.multiSelect);
    const rows = (this.data?.rows && this.data.rows.length > 0)
      ? this.data.rows
      : [1,2,3].map(i => Object.fromEntries(fields.map((f: any) => [f.name, `Sample ${i}`])));
    const activeFilters = Object.entries(this.filters).filter(([_,v]) => v && v.trim() !== '');
    const totalPages = Math.max(1, Math.ceil(this.total / this.pageSize));
    const startIdx = (this.page - 1) * this.pageSize + 1;
    const endIdx = Math.min(this.total, this.page * this.pageSize);
    const rawTheme = (this.node?.props?.theme || 'default').toLowerCase();
    const themeClass = ['table-container', `table-theme-${this.effectiveTheme}`].join(' ');
    const customStyle = this.computeCustomStyle(rawTheme);
    const bulkActions: string[] = Array.isArray(this.node?.props?.bulkActions) ? this.node.props.bulkActions : ['delete','export'];
    const selectedCount = this.selectedIds.size;
    return html`<div class="${themeClass}" style="${customStyle}">
      ${this.error ? html`<div class="table-error" style="background:#fef2f2;color:#b91c1c;border:1px solid #fee2e2;">Showing sample data (${this.error})</div>` : ''}
      ${this.buildPagination(startIdx, endIdx, totalPages)}
      ${multiSelect && selectedCount > 0 ? html`
        <div class="bulk-bar" role="region" aria-label="Bulk actions">
          <div style="font-size:0.8rem;color:#475569;">Selected ${selectedCount} row(s)</div>
          <div style="display:flex;align-items:center;gap:0.5rem;">
            ${bulkActions.map(a => html`<button @click=${() => this.onBulkAction(a)} aria-label="${a} selected">${a.charAt(0).toUpperCase() + a.slice(1)}</button>`)}
            <button @click=${this.onClearSelection} aria-label="Clear selection">Clear</button>
          </div>
        </div>
      ` : ''}
      <div class="table-wrapper" role="region" aria-label="Data table scroll region">
        <table class="table-live">
          <thead>
            ${this.buildHeader(fields, actions, multiSelect, rows)}
            ${this.buildFilterRow(fields, actions, multiSelect)}
          </thead>
          <tbody>
            ${this.buildBody(rows, fields, actions, multiSelect)}
          </tbody>
        </table>
      </div>
      ${activeFilters.length > 0 ? html`<div style="margin-top:0.5rem;font-size:0.7rem;color:#475569;">Applied ${activeFilters.length} filter(s). Showing rows ${startIdx}-${endIdx} of ${this.total}.</div>`: ''}
    </div>`;
  }

  private getRowId(row: any): string | number {
    const key = (this.node?.props?.idField || 'id');
    return row[key];
  }

  private readonly onRowSelectChange = (id: string, e: Event) => {
    const checked = (e.target as HTMLInputElement).checked;
    const next = new Set(this.selectedIds);
    if (checked) next.add(id); else next.delete(id);
    this.selectedIds = next;
  };

  private readonly onSelectAllChange = (e: Event) => {
    const checked = (e.target as HTMLInputElement).checked;
    const rows = (this.data?.rows && this.data.rows.length > 0) ? this.data.rows : [];
    const next = new Set(this.selectedIds);
    for (const row of rows as any[]) {
      const id = String(this.getRowId(row));
      if (checked) next.add(id); else next.delete(id);
    }
    this.selectedIds = next;
  };

  private readonly onClearSelection = () => {
    this.selectedIds = new Set<string>();
  };

  private readonly onBulkAction = async (action: string) => {
    const ids = Array.from(this.selectedIds);
    const entity = this.node?.props?.entity;
    // Fire event regardless, to allow external listeners
    this.dispatchEvent(new CustomEvent('bulk-action', { detail: { action, selectedIds: ids, entity }, bubbles: true, composed: true }));
    if (!entity || ids.length === 0) return;
    try {
      if (action === 'delete') {
  await bulkDelete(entity, ids);
        // optimistic refresh
        await this.loadPage(this.page);
        this.selectedIds = new Set<string>();
        // Optional: show ephemeral status
        this.error = '';
      } else if (action === 'export') {
        const res = await bulkExport(entity, ids);
        const rows = Array.isArray(res?.rows) ? res.rows : [];
        if (rows.length > 0) {
          const csv = this.toCsv(rows);
          const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
          const url = URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = `${entity}-export-${new Date().toISOString().slice(0,19).replaceAll(/[:T]/g,'-')}.csv`;
          document.body.appendChild(a);
          a.click();
          a.remove();
          URL.revokeObjectURL(url);
        }
      }
    } catch (e) {
      this.error = (e as any)?.message || 'Bulk operation failed.';
      setTimeout(() => { this.error = ''; this.requestUpdate(); }, 3000);
    }
  };

  private toCsv(rows: any[]): string {
    if (!rows.length) return '';
    const headers = Object.keys(rows[0]);
    const esc = (v: any) => {
      if (v == null) return '';
      const s = String(v);
      const needsQuote = /[",\n]/.test(s);
      const q = s.replaceAll('"', '""');
      return needsQuote ? `"${q}"` : q;
    };
    const lines = [headers.join(',')];
    for (const row of rows) {
      lines.push(headers.map(h => esc(row[h])).join(','));
    }
    return lines.join('\n');
  }
}
