// StudioTableLive.ts - Lit component for runtime table rendering with live data
import { LitElement, html, css } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { fetchTableData } from '../../core/api-client';
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

  public static readonly styles = css`
    .table-container {
      background: linear-gradient(135deg, #e2e8f0 0%, #f8fafc 100%);
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
    .table-live th {
      background: linear-gradient(90deg, #1e293b 0%, #1d4ed8 50%, #2563eb 100%);
      color: #fff;
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
      border-bottom: 1px solid #e2e8f0;
      background: #ffffff;
      color: #334155;
      transition: background 0.2s;
      font-size: 15px;
    }
    .table-live tbody tr:last-child td {
      border-bottom: none;
    }
    .table-live tbody tr:hover td {
      background: #f1f5f9;
    }
    .table-live thead th:first-child { border-top-left-radius: 14px; }
    .table-live thead th:last-child { border-top-right-radius: 14px; }
    .table-live tbody tr:nth-child(even) td { background: #f8fafc; }
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
      background: rgba(255,255,255,0.75);
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
    this.pageSize = this.node.props.pageSize || 25;
    await this.loadPage(1);
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
      const result = await fetchTableData(entity, fields, { pageSize: this.pageSize, sort, page });
      this.data = result;
      this.total = typeof result.total === 'number' ? result.total : (result.rows?.length || 0);
      this.page = page;
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
  }

  render() {
    if (this.loading) return html`<div>Loading table data...</div>`;
    const fields = Array.isArray(this.node?.props?.fields) ? this.node.props.fields : [];
    const actions = this.node?.props?.actions || [];
    const rows = (this.data?.rows && this.data.rows.length > 0)
      ? this.data.rows
      : [
          Object.fromEntries(fields.map((f: any) => [f.name, 'Sample 1'])),
          Object.fromEntries(fields.map((f: any) => [f.name, 'Sample 2'])),
          Object.fromEntries(fields.map((f: any) => [f.name, 'Sample 3']))
        ];

    // Client-side filtering; for large datasets/back-end filtering extend API later
    const activeFilters = Object.entries(this.filters).filter(([_,v]) => v && v.trim() !== '');
    const filteredRows = activeFilters.length > 0
      ? rows.filter((row: any) => {
          return activeFilters.every(([key, val]) => {
            const cell = row[key];
            if (cell === null || cell === undefined) return false;
            return String(cell).toLowerCase().includes(val.toLowerCase());
          });
        })
      : rows;
    const totalPages = Math.max(1, Math.ceil(this.total / this.pageSize));
    const startIdx = (this.page - 1) * this.pageSize + 1;
    const endIdx = Math.min(this.total, this.page * this.pageSize);
    return html`
      <div class="table-container">
        ${this.error ? html`<div class="table-error" style="background:#fef2f2;color:#b91c1c;border:1px solid #fee2e2;">Showing sample data (${this.error})</div>` : ''}
        ${!this.error && this.total > 0 ? html`
          <div class="pagination-bar" role="navigation" aria-label="Pagination">
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
          </div>
        `: ''}
        <div class="table-wrapper" role="region" aria-label="Data table scroll region">
        <table class="table-live">
          <thead>
            <tr>
              ${fields.map((field: any) => html`<th>${field.label || field.name}</th>`)}
              ${actions.length > 0 ? html`<th>Actions</th>` : ''}
            </tr>
            <tr class="filter-row">
              ${fields.map((field: any) => html`<th>
                <input type="text" aria-label="Filter ${field.label || field.name}" placeholder="Filter..." @input=${(e: Event) => this.onFilterInput(field.name, e)} .value=${this.filters[field.name] || ''}>
              </th>`)}
              ${actions.length > 0 ? html`<th></th>` : ''}
            </tr>
          </thead>
          <tbody>
            ${filteredRows.map((row: any) => html`
              <tr>
                ${fields.map((field: any) => html`<td>${row[field.name] ?? ''}</td>`)}
                ${actions.length > 0 ? html`<td class="table-actions">
                  ${actions.map((action: string) => html`<button aria-label="${action} row" @click=${() => alert(action + ' not implemented yet.')}>${action.charAt(0).toUpperCase() + action.slice(1)}</button>`)}
                </td>` : ''}
              </tr>
            `)}
          </tbody>
        </table>
        </div>
        ${activeFilters.length > 0 ? html`<div style="margin-top:0.5rem;font-size:0.7rem;color:#475569;">Filtered ${filteredRows.length} of ${rows.length} rows (page scope)</div>`: ''}
      </div>
    `;
  }
}
