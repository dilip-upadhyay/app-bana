// Workflow Designer Page Component
// Main container that orchestrates all workflow designer components

import { LitElement, html, css } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { WorkflowMetadata, NodeMetadata } from './models/WorkflowMetadata';
import './components/NodePalette';
import './components/WorkflowCanvas';

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

  static styles = css`
    :host {
      display: block;
      height: 100vh;
      background: #f8fafc;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    }

    .grid-container {
      display: grid;
      grid-template-columns: 250px 1fr 300px;
      grid-template-rows: 60px 1fr;
      height: 100%;
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
    }
  `;

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
          @node-add=${this.handleNodeAdd}
          @node-select=${this.handleNodeSelect}
        ></workflow-canvas>

        <div class="properties">
          ${this.renderPropertiesPanel()}
        </div>
      </div>
    `;
  }

  private renderPropertiesPanel() {
    if (!this.selectedNodeId) {
      return html`
        <div class="properties-empty">
          Select a node to edit its properties
        </div>
      `;
    }

    const node = this.workflowMetadata.nodes.find((n: NodeMetadata) => n.id === this.selectedNodeId);
    if (!node) return '';

    return html`
      <h3>${node.type} Properties</h3>
      <p>Properties panel coming in next phase...</p>
      <p>Node: ${node.label}</p>
    `;
  }

  private handleNodeAdd(e: CustomEvent) {
    const { node } = e.detail;
    this.workflowMetadata = {
      ...this.workflowMetadata,
      nodes: [...this.workflowMetadata.nodes, node]
    };

    // Select the newly added node
    this.selectedNodeId = node.id;
  }

  private handleNodeSelect(e: CustomEvent) {
    this.selectedNodeId = e.detail.nodeId;
  }

  private handleValidate() {
    alert('Validation coming in Phase 4...');
  }

  private async handleSave() {
    alert('Save functionality coming in Phase 3...');
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
