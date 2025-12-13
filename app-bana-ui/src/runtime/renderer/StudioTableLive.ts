// StudioTableLive.ts - Lit component for runtime table rendering with live data
import { LitElement, html, css } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { fetchTableData, bulkDelete, bulkExport, updateRow, getFieldPermissions, canReadField, canEditField } from '../../core/api-client';
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
  @state() private filters: Record<string, string> = {};
  @state() private effectiveTheme: string = 'default';
  @state() private runtimeThemeOverride: string | null = null;
  @state() private runtimeThemeTokens: Record<string, string> | null = null;
  @state() private selectedIds: Set<string> = new Set<string>();
  @state() private confirmOpen: boolean = false;
  @state() private confirmMessage: string = '';
  @state() private pendingDeleteIds: string[] = [];
  @state() private toastOpen: boolean = false;
  @state() private toastMessage: string = '';
  @state() private viewOpen: boolean = false;
  @state() private viewRow: any = null;
  @state() private editMode: boolean = false;
  @state() private editValues: Record<string, any> = {};
  @state() private fieldPermissions: { readable: string[], editable: string[] } | null = null;

  private get entityName(): string | undefined {
    return this.node?.props?.entity?.replace(/=$/, '');
  }

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
      background: var(--tbl-header-bg, var(--color-surface-alt, #f8fafc));
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
      background: var(--color-brand, #2563eb);
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
      filter: brightness(0.9);
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
    /* Confirmation Modal */
    .modal-overlay {
      position: fixed;
      inset: 0;
      background: rgba(15, 23, 42, 0.45);
      backdrop-filter: blur(2px);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
      animation: fadeIn 120ms ease-out;
    }
    .modal-card {
      background: #ffffff;
      color: #0f172a;
      border-radius: 12px;
      border: 1px solid #e2e8f0;
      box-shadow: 0 20px 60px rgba(0,0,0,0.20);
      padding: 1rem 1rem 0.75rem;
      width: min(420px, 90vw);
      transform: translateY(8px);
      opacity: 0;
      animation: slideUp 160ms ease-out forwards;
      outline: none;
    }
    .modal-title { font-weight: 700; font-size: 1rem; margin-bottom: 0.5rem; color: #1e293b; }
    .modal-message { font-size: 0.95rem; color: #334155; margin-bottom: 1rem; }
    .modal-actions { display: flex; justify-content: flex-end; gap: 0.5rem; }
    .btn-secondary {
      padding: 6px 12px; border-radius: 6px; border: 1px solid #cbd5e1; background: #fff; color: #1e293b; font-weight: 600; cursor: pointer;
    }
    .btn-secondary:hover { background: #f1f5f9; }
    .btn-danger {
      padding: 6px 12px; border-radius: 6px; border: 1px solid #ef4444; background: #ef4444; color: #fff; font-weight: 700; cursor: pointer;
    }
    .btn-danger:hover { background: #dc2626; }
    @keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
    @keyframes slideUp { from { transform: translateY(8px); opacity: 0; } to { transform: translateY(0); opacity: 1; } }
    /* Snackbar */
    .snackbar {
      position: fixed;
      right: 20px;
      top: 20px;
      z-index: 1100;
      background: #0f172a;
      color: #f8fafc;
      border: 1px solid #334155;
      border-radius: 10px;
      box-shadow: 0 8px 24px rgba(0,0,0,0.25);
      padding: 10px 14px;
      font-size: 0.9rem;
      display: flex;
      align-items: center;
      gap: 0.5rem;
      animation: slideUp 160ms ease-out;
    }
    .snackbar button {
      margin-left: 0.5rem;
      padding: 4px 8px;
      border-radius: 6px;
      border: 1px solid #475569;
      background: #1e293b;
      color: #f8fafc;
      cursor: pointer;
      font-size: 0.8rem;
    }
    .snackbar button:hover { background: #0f172a; }
    /* View Form Modal */
    .view-modal-card {
      background:#ffffff;
      color:#0f172a;
      border-radius:14px;
      border:1px solid #e2e8f0;
      box-shadow:0 24px 70px rgba(0,0,0,0.25);
      width:min(640px,95vw);
      padding:1.2rem 1.25rem 1.4rem;
      display:flex;
      flex-direction:column;
      gap:1rem;
      transform:translateY(10px);opacity:0;animation:slideUp 180ms ease-out forwards;
      max-height:80vh;overflow:auto;
    }
    .view-form-grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(240px,1fr)); gap:0.75rem 1rem; }
    .view-field { display:flex; flex-direction:column; gap:4px; background:#f8fafc; border:1px solid #e2e8f0; padding:8px 10px; border-radius:8px; }
    .view-field label { font-size:0.7rem; font-weight:600; letter-spacing:0.03em; color:#475569; text-transform:uppercase; }
    .view-value { font-size:0.85rem; color:#1e293b; word-break:break-word; white-space:pre-wrap; }
  /* Inline Cell Editing */
  .cell { position: relative; cursor: pointer; }
  .cell.editing { background: #fff7ed !important; outline: 2px solid #fdba74; }
  .inline-editor { width:100%; box-sizing:border-box; font-size:0.75rem; padding:4px 6px; border:1px solid #fbbf24; border-radius:4px; background:#ffffff; color:#1e293b; }
  .inline-editor:focus { outline:2px solid #f59e0b; }
  .inline-edit-actions { position:absolute; top:2px; right:4px; display:flex; gap:2px; }
  .inline-edit-actions button { padding:2px 6px; font-size:10px; border-radius:4px; border:1px solid #e2e8f0; background:#fff; cursor:pointer; }
  .inline-edit-actions button:hover { background:#f1f5f9; }
    .view-modal-header { display:flex; justify-content:space-between; align-items:center; }
    .close-btn { background:#fff; border:1px solid #cbd5e1; border-radius:6px; padding:4px 10px; cursor:pointer; font-size:0.75rem; }
    .close-btn:hover { background:#f1f5f9; }
  `;

  // Use lifecycle without returning a Promise type per lint rule; wrap async logic
  firstUpdated() {
    this.initializeTable();
  }

  async connectedCallback() {
    super.connectedCallback();
    console.log('[StudioTableLive] Connected. Node:', this.node);
    // Load field permissions for FLS
    await this.loadFieldPermissions();
  }

  private async loadFieldPermissions() {
    const entity = this.entityName;
    if (!entity) return;
    try {
      this.fieldPermissions = await getFieldPermissions(entity);
    } catch (error) {
      console.warn('Failed to load field permissions, defaulting to full access:', error);
      this.fieldPermissions = { readable: ['*'], editable: ['*'] };
    }
  }

  protected updated(changedProperties: Map<string, any>): void {
    if (changedProperties.has('node')) {
      console.log('[StudioTableLive] Node property changed:', this.node);
      this.initializeTable();
    }

    if (this.confirmOpen) {
      const card = this.shadowRoot?.getElementById('confirmCard') as HTMLElement | null;
      card?.focus?.();
    }
  }

  private showToast(message: string) {
    this.toastMessage = message;
    this.toastOpen = true;
    // Auto-hide after 3 seconds
    setTimeout(() => {
      this.toastOpen = false;
      this.toastMessage = '';
      this.requestUpdate();
    }, 3000);
  }

  private async initializeTable() {
    console.log('[StudioTableLive] Initializing table...');
    if (!this.entityName || !Array.isArray(this.node?.props?.fields) || this.node?.props?.fields.length === 0) {
      console.error('[StudioTableLive] Configuration Missing:', {
        entityName: this.entityName,
        fields: this.node?.props?.fields
      });
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
        if (obj?.tokens && typeof obj.tokens === 'object') this.runtimeThemeTokens = obj.tokens as Record<string, string>;
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
      const entity = this.entityName;
      if (!entity) {
        this.error = 'Entity not configured.';
        return;
      }
      const fields = Array.isArray(this.node?.props?.fields) ? this.node?.props?.fields.map((f: any) => f.name) : [];
      const sort = this.node?.props?.sort || '';
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
          ${[10, 25, 50, 100].map(size => html`<option value=${size} ?selected=${size === this.pageSize}>${size}</option>`)}
        </select>
      </label>
      <label style="font-size:0.75rem;color:#475569;display:flex;align-items:center;gap:4px;">Theme:
        <select @change=${this.onThemeChange} aria-label="Select theme">
          ${['default', 'minimal', 'dark', 'striped', 'compact', 'soft', 'auto', 'custom'].map(t => html`<option value=${t} ?selected=${(this.runtimeThemeOverride ?? this.node?.props?.theme ?? 'default').toLowerCase() === t}>${t}</option>`)}
        </select>
      </label>
      ${((this.runtimeThemeOverride ?? this.node?.props?.theme ?? 'default').toLowerCase() === 'custom') ? html`
        <button @click=${this.promptCustomTokens} aria-label="Edit custom theme" style="padding:4px 10px;border-radius:6px;border:1px solid #cbd5e1;background:#fff;color:#1e293b;">Edit Tokens</button>
      `: ''}
    </div>`;
  }

  private buildHeader(fields: any[], actions: (string | { label: string; onClick: string })[], multiSelect: boolean, rowsOnPage: any[]) {
    const allChecked = multiSelect && rowsOnPage.length > 0 && rowsOnPage.every(r => this.selectedIds.has(String(this.getRowId(r))));
    return html`<tr>
      ${multiSelect ? html`<th><input type="checkbox" aria-label="Select all on page" .checked=${allChecked} @change=${this.onSelectAllChange}></th>` : ''}
      ${fields.map((f: any) => html`<th>${f.label || f.name}</th>`)}
      ${actions.length > 0 ? html`<th>Actions</th>` : ''}
    </tr>`;
  }

  private buildFilterRow(fields: any[], actions: (string | { label: string; onClick: string })[], multiSelect: boolean) {
    return html`<tr class="filter-row">
      ${multiSelect ? html`<th></th>` : ''}
      ${fields.map((f: any) => html`<th>
        <input type="text" aria-label="Filter ${f.label || f.name}" placeholder="Filter..." @input=${(e: Event) => this.onFilterInput(f.name, e)} .value=${this.filters[f.name] || ''}>
      </th>`)}
      ${actions.length > 0 ? html`<th></th>` : ''}
    </tr>`;
  }

  private buildBody(rows: any[], fields: any[], actions: (string | { label: string; onClick: string })[], multiSelect: boolean) {
    return rows.map((row: any) => {
      const id = String(this.getRowId(row));
      const checked = this.selectedIds.has(id);
      return html`<tr>
        ${multiSelect ? html`<td><input type="checkbox" aria-label="Select row" .checked=${checked} @change=${(e: Event) => this.onRowSelectChange(id, e)}></td>` : ''}
        ${fields.map((f: any) => html`<td class="cell" @dblclick=${() => this.startInlineCellEdit(id, f.name, row[f.name])}>${this.renderCellContent(id, f.name, row[f.name])}</td>`)}
        ${actions.length > 0 ? html`<td class="table-actions">
          ${actions.map((action) => {
        const isString = typeof action === 'string';
        const label = isString ? (action as string).charAt(0).toUpperCase() + (action as string).slice(1) : (action as { label: string }).label;
        const handler = isString ? () => this.handleRowAction(action as string, row) : () => this.handleCustomAction(action as { onClick: string }, row);
        return html`<button aria-label="${label} row" @click=${handler}>${label}</button>`;
      })}
        </td>` : ''}
      </tr>`;
    });
  }

  private computeCustomStyle(rawTheme: string): string {
    if (rawTheme !== 'custom') return '';
    const map: Record<string, string> = this.runtimeThemeTokens ?? (this.node?.props?.themeTokens as Record<string, string>) ?? {};
    const tokenToVar: Record<string, string> = {
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
    for (const [k, v] of Object.entries(map)) if (v && tokenToVar[k]) parts.push(`${tokenToVar[k]}:${v}`);
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
    } catch { }
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
        this.runtimeThemeTokens = obj as Record<string, string>;
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
    const actions: (string | { label: string; onClick: string })[] = this.node?.props?.actions || [];
    const multiSelect: boolean = Boolean(this.node?.props?.multiSelect);
    const rows = (this.data?.rows && this.data.rows.length > 0)
      ? this.data.rows
      : [1, 2, 3].map(i => Object.fromEntries(fields.map((f: any) => [f.name, `Sample ${i}`])));
    const activeFilters = Object.entries(this.filters).filter(([_, v]) => v && v.trim() !== '');
    const totalPages = Math.max(1, Math.ceil(this.total / this.pageSize));
    const startIdx = (this.page - 1) * this.pageSize + 1;
    const endIdx = Math.min(this.total, this.page * this.pageSize);
    const rawTheme = (this.node?.props?.theme || 'default').toLowerCase();
    const themeClass = ['table-container', `table-theme-${this.effectiveTheme}`].join(' ');
    const customStyle = this.computeCustomStyle(rawTheme);
    const bulkActions: string[] = Array.isArray(this.node?.props?.bulkActions) ? this.node.props.bulkActions : ['delete', 'export'];
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
      ${activeFilters.length > 0 ? html`<div style="margin-top:0.5rem;font-size:0.7rem;color:#475569;">Applied ${activeFilters.length} filter(s). Showing rows ${startIdx}-${endIdx} of ${this.total}.</div>` : ''}
      ${this.confirmOpen ? html`
        <div class="modal-overlay" role="presentation" @click=${this.closeConfirm}>
          <div id="confirmCard" class="modal-card" role="dialog" aria-modal="true" aria-labelledby="confirmTitle" tabindex="0" @click=${(e: Event) => e.stopPropagation()}>
            <div id="confirmTitle" class="modal-title">Confirm Delete</div>
            <div class="modal-message">${this.confirmMessage}</div>
            <div class="modal-actions">
              <button class="btn-secondary" @click=${this.closeConfirm} aria-label="Cancel delete">Cancel</button>
              <button class="btn-danger" @click=${this.executePendingDelete} aria-label="Confirm delete">Delete</button>
            </div>
          </div>
        </div>
      ` : ''}
      ${this.toastOpen ? html`
        <div class="snackbar" role="status" aria-live="polite">
          <span>${this.toastMessage}</span>
          <button @click=${() => { this.toastOpen = false; this.toastMessage = ''; }}>Dismiss</button>
        </div>
      ` : ''}
      ${this.viewOpen ? html`
        <div class="modal-overlay" role="presentation" @click=${() => this.closeViewForm()}>
          <div class="view-modal-card" role="dialog" aria-modal="true" aria-labelledby="viewFormTitle" tabindex="0" @click=${(e: Event) => e.stopPropagation()}>
            <div class="view-modal-header">
              <h3 id="viewFormTitle" style="margin:0;font-size:1rem;">Row Details</h3>
              <div style="display:flex;gap:0.5rem;align-items:center;">
                ${this.renderViewHeaderButtons()}
              </div>
            </div>
            <div class="view-form-grid">
              ${this.renderViewFields()}
            </div>
          </div>
        </div>
      `: ''}
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

  // Inline cell editing state helpers
  @state() private inlineEditing: { rowId: string; field: string } | null = null;
  @state() private inlineDraft: any = '';

  private startInlineCellEdit(rowId: string, field: string, currentValue: any) {
    // Prevent editing id field or while modal edit active
    if (field === (this.node?.props?.idField || 'id') || this.editMode) return;
    this.inlineEditing = { rowId, field };
    this.inlineDraft = currentValue ?? '';
    // Dispatch edit start
    this.dispatchEvent(new CustomEvent('cell-edit-start', { detail: { rowId, field }, bubbles: true, composed: true }));
  }

  private cancelInlineEdit() {
    if (this.inlineEditing) {
      const { rowId, field } = this.inlineEditing;
      this.dispatchEvent(new CustomEvent('cell-edit-cancel', { detail: { rowId, field }, bubbles: true, composed: true }));
    }
    this.inlineEditing = null;
    this.inlineDraft = '';
  }

  private async commitInlineEdit() {
    if (!this.inlineEditing) return;
    const { rowId, field } = this.inlineEditing;
    const entity = this.entityName;
    if (!entity) { this.cancelInlineEdit(); return; }
    try {
      await updateRow(entity, rowId, { [field]: this.inlineDraft });
      // Update local data
      if (Array.isArray(this.data?.rows)) {
        const idx = this.data.rows.findIndex((r: any) => String(this.getRowId(r)) === rowId);
        if (idx >= 0) this.data.rows[idx][field] = this.inlineDraft;
      }
      this.showToast('Cell updated');
      this.dispatchEvent(new CustomEvent('cell-edit-save', { detail: { rowId, field, value: this.inlineDraft }, bubbles: true, composed: true }));
    } catch (e) {
      this.error = (e as any)?.message || 'Update failed.';
      setTimeout(() => { this.error = ''; this.requestUpdate(); }, 3000);
    } finally {
      this.inlineEditing = null;
      this.inlineDraft = '';
      this.requestUpdate();
    }
  }

  private onInlineEditorKey(e: KeyboardEvent) {
    if (e.key === 'Enter') { e.preventDefault(); this.commitInlineEdit(); }
    else if (e.key === 'Escape') { e.preventDefault(); this.cancelInlineEdit(); }
  }

  private renderCellContent(rowId: string, field: string, value: any) {
    const editing = this.inlineEditing?.rowId === rowId && this.inlineEditing?.field === field;
    if (!editing) {
      return html`<div>${value == null ? '' : String(value)}</div>`;
    }
    return html`<div class="inline-cell-wrapper">
      <input class="inline-editor" .value=${this.inlineDraft} @input=${(e: Event) => this.inlineDraft = (e.target as HTMLInputElement).value} @keydown=${(e: KeyboardEvent) => this.onInlineEditorKey(e)} />
      <div class="inline-edit-actions">
        <button @click=${() => this.commitInlineEdit()} aria-label="Save cell">✔</button>
        <button @click=${() => this.cancelInlineEdit()} aria-label="Cancel cell edit">✖</button>
      </div>
    </div>`;
  }

  private handleCustomAction(action: { onClick: string }, row: any) {
    try {
      // Evaluate the onClick string in a context where 'row' and 'navigate' are available
      const navigate = (path: string) => {
        // Prefer window.navigate if available (Runtime Shell shim)
        if ((window as any).navigate) {
          (window as any).navigate(path);
        } else {
          // Fallback to custom event
          this.dispatchEvent(new CustomEvent('navigate', { detail: { path }, bubbles: true, composed: true }));
        }
      };

      // Create a function that takes 'row' and 'navigate' as arguments
      // The action.onClick string (e.g., "navigate('/foo')") matches this if 'navigate' is in scope
      // We pass 'navigate' as a parameter to the dynamic function so it's available
      const func = new Function('row', 'navigate', action.onClick);
      func(row, navigate);
    } catch (e) {
      console.error('Failed to execute custom action', e);
      this.showToast('Action failed: ' + (e as Error).message);
    }
  }

  private readonly handleRowAction = (action: string, row: any) => {
    if (action === 'view') {
      this.openViewForm(row);
      return;
    }
    // Placeholder for other actions (edit/delete) - can be implemented later
    alert(action + ' not implemented yet.');
  };

  private openViewForm(row: any) {
    this.viewRow = row;
    this.viewOpen = true;
    this.editMode = false;
    this.editValues = { ...row };
    // Dispatch event for external listeners
    this.dispatchEvent(new CustomEvent('row-view', { detail: { row }, bubbles: true, composed: true }));
  }

  private closeViewForm() {
    this.viewOpen = false;
    this.viewRow = null;
    this.editMode = false;
    this.editValues = {};
  }

  private readonly onBulkAction = async (action: string) => {
    const ids = Array.from(this.selectedIds);
    const entity = this.entityName;
    // Fire event regardless, to allow external listeners
    this.dispatchEvent(new CustomEvent('bulk-action', { detail: { action, selectedIds: ids, entity }, bubbles: true, composed: true }));
    if (!entity || ids.length === 0) return;
    try {
      if (action === 'delete') {
        const shouldConfirm = this.node?.props?.confirmDelete !== false;
        if (shouldConfirm) {
          this.pendingDeleteIds = ids;
          this.confirmMessage = `Delete ${ids.length} selected ${entity} record(s)? This cannot be undone.`;
          this.confirmOpen = true;
          return;
        }
        await bulkDelete(entity, ids);
        await this.loadPage(this.page);
        this.selectedIds = new Set<string>();
        this.error = '';
        this.showToast(`Deleted ${ids.length} ${entity} record(s).`);
      } else if (action === 'export') {
        const res = await bulkExport(entity, ids);
        const rows = Array.isArray(res?.rows) ? res.rows : [];
        if (rows.length > 0) {
          const csv = this.toCsv(rows);
          const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
          const url = URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = `${entity}-export-${new Date().toISOString().slice(0, 19).replaceAll(/[:T]/g, '-')}.csv`;
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

  private readonly closeConfirm = () => {
    this.confirmOpen = false;
    this.confirmMessage = '';
    this.pendingDeleteIds = [];
  };

  private readonly executePendingDelete = async () => {
    const entity = this.node?.props?.entity;
    const ids = this.pendingDeleteIds;
    if (!entity || ids.length === 0) { this.closeConfirm(); return; }
    try {
      await bulkDelete(entity, ids);
      await this.loadPage(this.page);
      this.selectedIds = new Set<string>();
      this.error = '';
      this.showToast(`Deleted ${ids.length} ${entity} record(s).`);
    } catch (e) {
      this.error = (e as any)?.message || 'Bulk delete failed.';
      setTimeout(() => { this.error = ''; this.requestUpdate(); }, 3000);
    } finally {
      this.closeConfirm();
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

  private renderViewFields() {
    if (!this.viewRow) return html``;
    const mode = (this.node?.props?.viewMode || 'dynamic');
    let fieldDefs: any[] = [];
    if (mode === 'custom' && Array.isArray(this.node?.props?.viewFormFields) && this.node.props.viewFormFields.length > 0) {
      fieldDefs = this.node.props.viewFormFields;
    } else {
      // dynamic: use selected table fields
      fieldDefs = Array.isArray(this.node?.props?.fields) ? this.node.props.fields.map((f: any) => ({ name: f.name, label: f.label || f.name })) : [];
    }
    if (!fieldDefs.length) return html`<div style="font-size:0.85rem;color:#64748b;">No fields defined for view.</div>`;
    return fieldDefs.map(fd => {
      const label = fd.label || fd.name;
      const rawValue = this.viewRow[fd.name];
      const type = fd.type || this.inferValueType(rawValue);

      // Apply FLS: Hide non-readable fields
      if (this.fieldPermissions && !canReadField(fd.name, this.fieldPermissions.readable)) {
        return html``; // Field hidden (user cannot read)
      }

      if (this.editMode) return this.renderEditableField(fd, label, type);
      const display = this.formatDisplayValue(type, rawValue);
      return html`<div class="view-field"><label>${label}</label><div class="view-value">${display}</div></div>`;
    });
  }

  private renderEditableField(fd: any, label: string, type: string) {
    const current = this.editValues[fd.name];

    // Apply FLS: Disable non-editable fields
    const disabled = this.fieldPermissions && !canEditField(fd.name, this.fieldPermissions.editable);
    const lockIcon = disabled ? ' 🔒' : '';

    if (type === 'textarea') {
      return html`<div class="view-field"><label>${label}${lockIcon}</label><textarea style="resize:vertical;min-height:70px;font-size:0.8rem;" .value=${current ?? ''} ?disabled=${disabled} title=${disabled ? 'Field is read-only (no edit permission)' : ''} @input=${(e: Event) => this.onEditInput(fd.name, (e.target as HTMLTextAreaElement).value)}></textarea></div>`;
    }
    if (type === 'date') {
      return html`<div class="view-field"><label>${label}${lockIcon}</label><input type="date" .value=${this.toDateInputValue(current)} ?disabled=${disabled} title=${disabled ? 'Field is read-only (no edit permission)' : ''} @input=${(e: Event) => this.onEditInput(fd.name, (e.target as HTMLInputElement).value)}></div>`;
    }
    if (type === 'number') {
      return html`<div class="view-field"><label>${label}${lockIcon}</label><input type="number" .value=${current ?? ''} ?disabled=${disabled} title=${disabled ? 'Field is read-only (no edit permission)' : ''} @input=${(e: Event) => this.onEditInput(fd.name, (e.target as HTMLInputElement).value)}></div>`;
    }
    return html`<div class="view-field"><label>${label}${lockIcon}</label><input type="text" .value=${current ?? ''} ?disabled=${disabled} title=${disabled ? 'Field is read-only (no edit permission)' : ''} @input=${(e: Event) => this.onEditInput(fd.name, (e.target as HTMLInputElement).value)}></div>`;
  }

  private formatDisplayValue(type: string, rawValue: any) {
    if (type === 'date') return this.formatDate(rawValue);
    if (rawValue == null) return '';
    return String(rawValue);
  }

  private renderViewHeaderButtons() {
    if (this.editMode) {
      return [
        html`<button class="close-btn" @click=${() => this.cancelEdit()} aria-label="Cancel editing">Cancel</button>`,
        html`<button class="btn-secondary" style="font-size:0.75rem;" @click=${() => this.saveEdit()} aria-label="Save changes">Save</button>`,
        html`<button class="close-btn" @click=${() => this.closeViewForm()} aria-label="Close view form">Close</button>`
      ];
    }
    return [
      html`<button class="close-btn" @click=${() => this.startEdit()} aria-label="Start editing">Edit</button>`,
      html`<button class="close-btn" @click=${() => this.closeViewForm()} aria-label="Close view form">Close</button>`
    ];
  }

  private inferValueType(v: any): string {
    if (v == null) return 'text';
    if (typeof v === 'number') return 'number';
    if (typeof v === 'string') {
      if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(v)) return 'date';
      if (v.length > 120) return 'textarea';
    }
    return 'text';
  }

  private formatDate(v: any): string {
    if (!v) return '';
    try { const d = new Date(v); if (!Number.isNaN(d.getTime())) return d.toLocaleString(); } catch { }
    return String(v);
  }

  private toDateInputValue(v: any): string {
    if (!v) return '';
    try { const d = new Date(v); if (!Number.isNaN(d.getTime())) return d.toISOString().slice(0, 10); } catch { }
    return '';
  }

  private onEditInput(field: string, value: any) {
    this.editValues = { ...this.editValues, [field]: value };
  }

  private startEdit() {
    this.editMode = true;
  }

  private cancelEdit() {
    this.editMode = false;
    this.editValues = { ...this.viewRow };
  }

  private async saveEdit() {
    const entity = this.node?.props?.entity;
    if (!entity || !this.viewRow) { this.editMode = false; return; }
    const id = String(this.getRowId(this.viewRow));
    try {
      await updateRow(entity, id, this.editValues);
      // Update local dataset row
      if (Array.isArray(this.data?.rows)) {
        const idx = this.data.rows.findIndex((r: any) => String(this.getRowId(r)) === id);
        if (idx >= 0) this.data.rows[idx] = { ...this.data.rows[idx], ...this.editValues };
      }
      this.viewRow = { ...this.viewRow, ...this.editValues };
      this.editMode = false;
      this.showToast('Changes saved.');
      this.requestUpdate();
    } catch (e) {
      this.error = (e as any)?.message || 'Update failed.';
      setTimeout(() => { this.error = ''; this.requestUpdate(); }, 3000);
    }
  }
}
