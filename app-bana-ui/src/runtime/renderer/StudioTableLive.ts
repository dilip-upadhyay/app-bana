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

  public static readonly styles = css`
    .table-container {
      background: linear-gradient(135deg, #e0e7ef 0%, #f8fafc 100%);
      border-radius: 16px;
      box-shadow: 0 4px 24px rgba(30,41,59,0.08);
      padding: 2.5rem 2rem;
      margin: 2.5rem 0;
      display: flex;
      flex-direction: column;
      align-items: stretch;
    }
    .table-live {
      width: 100%;
      border-collapse: separate;
      border-spacing: 0;
      background: #fff;
      box-shadow: 0 2px 8px rgba(0,0,0,0.07);
      border-radius: 12px;
      overflow: hidden;
      font-size: 15px;
      transition: box-shadow 0.2s;
    }
    .table-live th {
      background: linear-gradient(90deg, #1e293b 0%, #2563eb 100%);
      color: #fff;
      font-weight: 700;
      padding: 16px 18px;
      border-bottom: 2px solid #334155;
      text-align: left;
      letter-spacing: 0.02em;
      font-size: 16px;
    }
    .table-live td {
      padding: 14px 18px;
      border-bottom: 1px solid #e2e8f0;
      background: #f8fafc;
      transition: background 0.2s;
      font-size: 15px;
    }
    .table-live tr:last-child td {
      border-bottom: none;
    }
    .table-live tbody tr:hover td {
      background: #e0e7ef;
    }
    .table-live th:first-child, .table-live td:first-child {
      border-top-left-radius: 12px;
    }
    .table-live th:last-child, .table-live td:last-child {
      border-top-right-radius: 12px;
    }
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
    .table-error {
      color: #ef4444;
      background: #fef2f2;
      border-radius: 8px;
      padding: 1rem;
      margin: 1rem 0;
      font-weight: 500;
    }
    @media (max-width: 700px) {
      .table-container {
        padding: 1rem 0.5rem;
      }
      .table-live th, .table-live td {
        padding: 8px 6px;
        font-size: 13px;
      }
    }
  `;

  async firstUpdated() {
    if (!this.node?.props?.entity || !Array.isArray(this.node.props.fields) || this.node.props.fields.length === 0) {
      this.error = 'No entity or columns selected.';
      return;
    }
    this.loading = true;
    try {
      const entity = this.node.props.entity;
      const fields = this.node.props.fields.map((f: any) => f.name);
      const pageSize = this.node.props.pageSize || 25;
      const sort = this.node.props.sort || '';
      this.data = await fetchTableData(entity, fields, { pageSize, sort });
    } catch (err: any) {
      // Store error but allow render() to fallback to sample rows
      this.error = err?.message || 'Failed to fetch table data.';
    } finally {
      this.loading = false;
    }
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
    return html`
      <div class="table-container">
        ${this.error ? html`<div class="table-error" style="background:#fef2f2;color:#b91c1c;border:1px solid #fee2e2;">Showing sample data (${this.error})</div>` : ''}
        <table class="table-live">
          <thead>
            <tr>
              ${fields.map((field: any) => html`<th>${field.label || field.name}</th>`)}
              ${actions.length > 0 ? html`<th>Actions</th>` : ''}
            </tr>
          </thead>
          <tbody>
            ${rows.map((row: any) => html`
              <tr>
                ${fields.map((field: any) => html`<td>${row[field.name] ?? ''}</td>`)}
                ${actions.length > 0 ? html`<td class="table-actions">
                  ${actions.map((action: string) => html`<button @click=${() => alert(action + ' not implemented yet.')}>${action.charAt(0).toUpperCase() + action.slice(1)}</button>`)}
                </td>` : ''}
              </tr>
            `)}
          </tbody>
        </table>
      </div>
    `;
  }
}
