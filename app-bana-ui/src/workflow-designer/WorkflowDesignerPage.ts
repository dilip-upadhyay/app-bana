// Workflow Designer Page Component
// Main container that orchestrates all workflow designer components

import { LitElement, html, css } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { WorkflowMetadata, NodeMetadata, ConnectionMetadata } from './models/WorkflowMetadata';
import { WorkflowValidator } from './utils/WorkflowValidator';
import { WorkflowHistory } from './utils/WorkflowHistory';
import './components/NodePalette';
import './components/WorkflowCanvas';

const STORAGE_KEY = 'workflow-designer-draft';

@customElement('workflow-designer-page')
export class WorkflowDesignerPage extends LitElement {
  @state() private workflowMetadata: WorkflowMetadata = {
    id: `workflow-${Date.now()}`,
    name: 'Untitled Workflow',
    version: 1,
    schemaVersion: '1.0.0',
    nodes: [],
    connections: []
  };

  @state() private selectedNodeId?: string;
  @state() private selectedConnectionId?: string;
  @state() private validationResult?: { errors: string[], warnings: string[] };

  private history = new WorkflowHistory();

  static styles = css`
    :host {
      display: block;
      height: 100%;
      width: 100%;
      background: #f8fafc;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
    }

    .grid-container {
      display: grid;
      grid-template-columns: 250px 1fr 300px;
      grid-template-rows: 60px 1fr;
      height: 100%;
      width: 100%;
      gap: 0;
    }

    .toolbar {
      grid-column: 1 / -1;
      background: white;
      border-bottom: 1px solid #e2e8f0;
      padding: 0 16px;
      display: flex;
      align-items: center;
      justify-content: space-between;
    }

    .toolbar-left {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .workflow-name {
      font-size: 16px;
      font-weight: 600;
      color: #1e293b;
    }

    .workflow-status {
      font-size: 12px;
      padding: 4px 8px;
      background: #f1f5f9;
      border-radius: 4px;
      color: #64748b;
    }

    .toolbar-right {
      display: flex;
      gap: 8px;
    }

    .btn {
      padding: 8px 16px;
      border: none;
      border-radius: 6px;
      font-size: 13px;
      font-weight: 500;
      cursor: pointer;
      transition: all 0.2s;
    }

    .btn-secondary {
      background: #f1f5f9;
      color: #475569;
    }

    .btn-secondary:hover {
      background: #e2e8f0;
    }

    .btn-primary {
      background: #3b82f6;
      color: white;
    }

    .btn-primary:hover {
      background: #2563eb;
    }

    .palette {
      background: white;
      border-right: 1px solid #e2e8f0;
      overflow-y: auto;
      grid-row: 2;
      grid-column: 1;
    }

    .canvas {
      position: relative;
      overflow: hidden;
      grid-row: 2;
      grid-column: 2;
    }

    .properties {
      background: white;
      border-left: 1px solid #e2e8f0;
      overflow-y: auto;
      padding: 16px;
      grid-row: 2;
      grid-column: 3;
    }

    .properties-empty {
      text-align: center;
      padding: 32px 16px;
      color: #94a3b8;
      font-size: 13px;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 12px;
    }

    .empty-icon {
      font-size: 24px;
      color: #cbd5e1;
    }

    .properties-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 20px;
      padding-bottom: 12px;
      border-bottom: 1px solid #f1f5f9;
    }

    .properties-header h3 {
      margin: 0;
      font-size: 16px;
      font-weight: 600;
      color: #1e293b;
    }

    .badge {
      font-size: 11px;
      font-weight: 500;
      padding: 2px 8px;
      background: #f1f5f9;
      color: #64748b;
      border-radius: 12px;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }

    .form-group {
      margin-bottom: 16px;
    }

    .form-group label {
      display: block;
      font-size: 12px;
      font-weight: 500;
      color: #64748b;
      margin-bottom: 6px;
    }

    .form-group input,
    .form-group select,
    .form-group textarea {
      width: 100%;
      padding: 8px 10px;
      border: 1px solid #e2e8f0;
      border-radius: 6px;
      font-size: 13px;
      color: #1e293b;
      background: white;
      transition: all 0.2s;
      box-sizing: border-box;
    }

    .form-group input:focus,
    .form-group select:focus,
    .form-group textarea:focus {
      outline: none;
      border-color: #3b82f6;
      box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1);
    }

    .form-group input:disabled {
      background: #f8fafc;
      color: #94a3b8;
    }

    .divider {
      height: 1px;
      background: #f1f5f9;
      margin: 20px 0;
    }

    .actions-footer {
      margin-top: 32px;
      padding-top: 16px;
      border-top: 1px solid #f1f5f9;
    }

    .btn-danger {
      width: 100%;
      background: #fef2f2;
      color: #ef4444;
      border: 1px solid #fee2e2;
    }

    .btn-danger:hover {
      background: #fee2e2;
      border-color: #fecaca;
    }

    .validation-toast {
      position: absolute;
      bottom: 24px;
      left: 50%;
      transform: translateX(-50%);
      background: white;
      border-radius: 8px;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
      padding: 16px;
      width: 400px;
      z-index: 100;
      animation: slide-up 0.3s ease-out;
    }

    @keyframes slide-up {
      from { transform: translate(-50%, 20px); opacity: 0; }
      to { transform: translate(-50%, 0); opacity: 1; }
    }

    .validation-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 12px;
      font-weight: 600;
    }

    .validation-close {
      cursor: pointer;
      color: #94a3b8;
    }

    .validation-list {
      display: flex;
      flex-direction: column;
      gap: 8px;
      max-height: 200px;
      overflow-y: auto;
    }

    .validation-item {
      display: flex;
      align-items: flex-start;
      gap: 8px;
      font-size: 13px;
      padding: 8px;
      border-radius: 6px;
    }

    .validation-item.error {
      background: #fef2f2;
      color: #991b1b;
    }

    .validation-item.warning {
      background: #fffbeb;
      color: #92400e;
    }
  `;

  connectedCallback() {
    super.connectedCallback();
    this.loadFromStorage();
    window.addEventListener('keydown', this.handleKeyDown);
  }

  disconnectedCallback() {
    super.disconnectedCallback();
    window.removeEventListener('keydown', this.handleKeyDown);
  }

  private loadFromStorage() {
    try {
      const saved = localStorage.getItem(STORAGE_KEY);
      if (saved) {
        this.workflowMetadata = JSON.parse(saved);
        // Clear history on load so we don't undo into empty state
        this.history = new WorkflowHistory();
      }
    } catch (e) {
      console.error('Failed to load workflow draft', e);
    }
  }

  private saveToStorage() {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(this.workflowMetadata));
    } catch (e) {
      console.error('Failed to save workflow draft', e);
    }
  }

  private updateMetadata(newMetadata: WorkflowMetadata) {
    this.history.push(this.workflowMetadata);
    this.workflowMetadata = newMetadata;
    this.saveToStorage();
    this.requestUpdate();
  }

  private handleKeyDown = (e: KeyboardEvent) => {
    // Undo: Cmd+Z or Ctrl+Z
    if ((e.metaKey || e.ctrlKey) && e.key === 'z' && !e.shiftKey) {
      e.preventDefault();
      this.undo();
    }
    // Redo: Cmd+Shift+Z or Ctrl+Shift+Z or Ctrl+Y
    if ((e.metaKey || e.ctrlKey) && (e.key === 'y' || (e.key === 'z' && e.shiftKey))) {
      e.preventDefault();
      this.redo();
    }
  };

  private undo() {
    const prev = this.history.undo(this.workflowMetadata);
    if (prev) {
      this.workflowMetadata = prev;
      this.saveToStorage();
      this.selectedNodeId = undefined;
      this.selectedConnectionId = undefined;
    }
  }

  private redo() {
    const next = this.history.redo(this.workflowMetadata);
    if (next) {
      this.workflowMetadata = next;
      this.saveToStorage();
    }
  }

  render() {
    return html`
      <div class="grid-container">
        <div class="toolbar">
          <div class="toolbar-left">
            <span class="workflow-name">${this.workflowMetadata.name}</span>
            <span class="workflow-status">Draft v${this.workflowMetadata.version}</span>
          </div>
          <div class="toolbar-right">
            <button class="btn btn-secondary" @click=${this.handleValidate}>
              ✓ Validate
            </button>
            <button class="btn btn-secondary" @click=${this.handleSave}>
              💾 Save
            </button>
            <button class="btn btn-primary" @click=${this.handlePublish}>
              🚀 Publish
            </button>
          </div>
        </div>

        <node-palette class="palette"></node-palette>

        <workflow-canvas
          class="canvas"
          .metadata=${this.workflowMetadata}
          .selectedNodeId=${this.selectedNodeId}
          .selectedConnectionId=${this.selectedConnectionId}
          @node-add=${this.handleNodeAdd}
          @node-move=${this.handleNodeMove}
          @node-select=${this.handleNodeSelect}
          @connection-add=${this.handleConnectionAdd}
          @connection-select=${this.handleConnectionSelect}
          @connection-delete=${this.handleConnectionDelete}
        ></workflow-canvas>

        <div class="properties">
          ${this.renderPropertiesPanel()}
        </div>

        ${this.renderValidationToast()}
      </div>
    `;
  }

  private renderPropertiesPanel() {
    if (this.selectedConnectionId) {
      const conn = this.workflowMetadata.connections.find(c => c.id === this.selectedConnectionId);
      if (conn) {
        return html`
          <div class="properties-header">
            <h3>Connection</h3>
            <span class="badge">Connection</span>
          </div>
          
          <div class="form-group">
            <label>ID</label>
            <input type="text" value="${conn.id}" readonly disabled />
          </div>

          <div class="form-group">
            <label>From Node</label>
            <input type="text" value="${conn.from}" readonly disabled />
          </div>

          <div class="form-group">
            <label>To Node</label>
            <input type="text" value="${conn.to}" readonly disabled />
          </div>

          <div class="actions-footer">
            <button class="btn btn-secondary btn-danger" @click=${() => this.deleteConnection(conn.id)}>
              Delete Connection
            </button>
          </div>
        `;
      }
    }

    if (!this.selectedNodeId) {
      return html`
        <div class="properties-empty">
          <div class="empty-icon">⚙️</div>
          <p>Select a node or connection to edit its properties</p>
        </div>
      `;
    }

    const node = this.workflowMetadata.nodes.find((n: NodeMetadata) => n.id === this.selectedNodeId);
    if (!node) return '';

    return html`
      <div class="properties-header">
        <h3>${node.type}</h3>
        <span class="badge">${node.type}</span>
      </div>

      <div class="form-group">
        <label>Label</label>
        <input 
          type="text" 
          .value=${node.label} 
          @input=${(e: Event) => this.handlePropertyChange(node.id, 'label', (e.target as HTMLInputElement).value)}
        />
      </div>

      <div class="form-group">
        <label>ID</label>
        <input type="text" value="${node.id}" readonly disabled />
      </div>

      <div class="divider"></div>

      ${this.renderTypeSpecificProperties(node)}
    `;
  }

  private renderTypeSpecificProperties(node: NodeMetadata) {
    switch (node.type) {
      case 'USER_TASK':
        return html`
          <div class="form-group">
            <label>Assigned User / Role</label>
            <input 
              type="text" 
              placeholder="e.g. admin, manager"
              .value=${node.properties.assignedUserId || node.properties.assignedRole || ''}
              @input=${(e: Event) => this.handleNodePropertyChange(node.id, 'assignedRole', (e.target as HTMLInputElement).value)}
            />
          </div>
          <div class="form-group">
            <label>Description</label>
            <textarea 
              rows="3"
              .value=${node.properties.description || ''}
              @input=${(e: Event) => this.handleNodePropertyChange(node.id, 'description', (e.target as HTMLTextAreaElement).value)}
            ></textarea>
          </div>
        `;

      case 'SERVICE_TASK':
        return html`
          <div class="form-group">
            <label>Service Action</label>
            <select 
              .value=${node.properties.serviceAction || ''}
              @change=${(e: Event) => this.handleNodePropertyChange(node.id, 'serviceAction', (e.target as HTMLSelectElement).value)}
            >
              <option value="">Select Action...</option>
              <option value="send-email">Send Email</option>
              <option value="update-database">Update Database</option>
              <option value="call-api">Call External API</option>
              <option value="generate-report">Generate Report</option>
            </select>
          </div>
        `;

      case 'DECISION':
        return html`
          <div class="form-group">
            <label>Condition Expression (MVEL)</label>
            <textarea 
              rows="3"
              placeholder="e.g. amount > 1000"
              .value=${node.properties.condition || ''}
              @input=${(e: Event) => this.handleNodePropertyChange(node.id, 'condition', (e.target as HTMLTextAreaElement).value)}
            ></textarea>
          </div>
        `;

      default:
        return html``;
    }
  }

  private renderValidationToast() {
    if (!this.validationResult) return '';

    const { errors, warnings } = this.validationResult;

    if (errors.length === 0 && warnings.length === 0) {
      return html`
        <div class="validation-toast">
          <div class="validation-header">
            <span style="color: #16a34a">✓ Workflow is valid</span>
            <span class="validation-close" @click=${() => this.validationResult = undefined}>×</span>
          </div>
        </div>
      `;
    }

    return html`
      <div class="validation-toast">
        <div class="validation-header">
          <span>Validation Issues</span>
          <span class="validation-close" @click=${() => this.validationResult = undefined}>×</span>
        </div>
        <div class="validation-list">
          ${errors.map(err => html`
            <div class="validation-item error">
              <span>🚫</span>
              <span>${err}</span>
            </div>
          `)}
          ${warnings.map(warn => html`
            <div class="validation-item warning">
              <span>⚠️</span>
              <span>${warn}</span>
            </div>
          `)}
        </div>
      </div>
    `;
  }

  private handlePropertyChange(nodeId: string, field: keyof NodeMetadata, value: any) {
    const newMetadata = {
      ...this.workflowMetadata,
      nodes: this.workflowMetadata.nodes.map(n =>
        n.id === nodeId ? { ...n, [field]: value } : n
      )
    };
    this.updateMetadata(newMetadata);
  }

  private handleNodePropertyChange(nodeId: string, propertyName: string, value: any) {
    const node = this.workflowMetadata.nodes.find(n => n.id === nodeId);
    if (!node) return;

    const newMetadata = {
      ...this.workflowMetadata,
      nodes: this.workflowMetadata.nodes.map(n =>
        n.id === nodeId ? {
          ...n,
          properties: { ...n.properties, [propertyName]: value }
        } : n
      )
    };
    this.updateMetadata(newMetadata);
  }

  private handleNodeAdd(e: CustomEvent) {
    const { node } = e.detail;
    const newMetadata = {
      ...this.workflowMetadata,
      nodes: [...this.workflowMetadata.nodes, node]
    };
    this.updateMetadata(newMetadata);
    this.selectedNodeId = node.id;
    this.selectedConnectionId = undefined;
  }

  private handleNodeMove(e: CustomEvent) {
    const { nodeId, position } = e.detail;
    // Update local state without history to prevent flooding stack
    this.workflowMetadata = {
      ...this.workflowMetadata,
      nodes: this.workflowMetadata.nodes.map((n: NodeMetadata) =>
        n.id === nodeId ? { ...n, position } : n
      )
    };
    this.saveToStorage();
  }

  private handleNodeSelect(e: CustomEvent) {
    this.selectedNodeId = e.detail.nodeId;
    this.selectedConnectionId = undefined;
  }

  private handleConnectionAdd(e: CustomEvent) {
    const { connection } = e.detail;
    const exists = this.workflowMetadata.connections.some(
      c => c.from === connection.from && c.to === connection.to
    );
    if (!exists) {
      const newMetadata = {
        ...this.workflowMetadata,
        connections: [...this.workflowMetadata.connections, connection]
      };
      this.updateMetadata(newMetadata);
    }
    this.selectedConnectionId = connection.id;
    this.selectedNodeId = undefined;
  }

  private handleConnectionSelect(e: CustomEvent) {
    this.selectedConnectionId = e.detail.connectionId;
    this.selectedNodeId = undefined;
  }

  private handleConnectionDelete(e: CustomEvent) {
    this.deleteConnection(e.detail.connectionId);
  }

  private deleteConnection(connectionId: string) {
    const newMetadata = {
      ...this.workflowMetadata,
      connections: this.workflowMetadata.connections.filter(c => c.id !== connectionId)
    };
    this.updateMetadata(newMetadata);
    this.selectedConnectionId = undefined;
  }

  private handleValidate() {
    const result = WorkflowValidator.validate(this.workflowMetadata);
    this.validationResult = {
      errors: result.errors,
      warnings: result.warnings
    };

    if (result.valid) {
      setTimeout(() => {
        if (this.validationResult && this.validationResult.errors.length === 0) {
          this.validationResult = undefined;
        }
      }, 3000);
    }
  }

  private async handleSave() {
    // Explicit save (mostly for UX as we auto-save)
    this.saveToStorage();
    alert('Workflow saved!');
  }

  private async handlePublish() {
    alert('Publish functionality coming in Phase 3...');
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'workflow-designer-page': WorkflowDesignerPage;
  }
}
