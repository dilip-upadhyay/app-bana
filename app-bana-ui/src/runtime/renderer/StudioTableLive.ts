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

  static styles = css`
    .table-live { width: 100%; border-collapse: collapse; }
    .table-live th, .table-live td { padding: 8px; border: 1px solid #222; }
    .table-live th { background: #222; color: #fff; }
    .table-error { color: red; padding: 1rem; }
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
      this.error = err?.message || 'Failed to fetch table data.';
    } finally {
      this.loading = false;
    }
  }

  render() {
    if (this.error) return html`<div class="table-error">${this.error}</div>`;
    if (this.loading) return html`<div>Loading table data...</div>`;
    const fields = Array.isArray(this.node?.props?.fields) ? this.node.props.fields : [];
    const actions = this.node?.props?.actions || [];
    return html`
      <table class="table-live">
        <thead>
          <tr>
            ${fields.map((field: any) => html`<th>${field.label || field.name}</th>`)}
            ${actions.length > 0 ? html`<th>Actions</th>` : ''}
          </tr>
        </thead>
        <tbody>
          ${(this.data?.rows || []).map((row: any) => html`
            <tr>
              ${fields.map((field: any) => html`<td>${row[field.name] ?? ''}</td>`)}
              ${actions.length > 0 ? html`<td class="table-actions">
                ${actions.map((action: string) => html`<button @click=${() => alert(`${action} not implemented yet.`)}>${action.charAt(0).toUpperCase() + action.slice(1)}</button>`)}
              </td>` : ''}
            </tr>
          `)}
        </tbody>
      </table>
    `;
  }
}
