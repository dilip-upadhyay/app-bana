// Workflow Designer Page Component
// Main container that orchestrates all workflow designer components

import { LitElement, html, css, PropertyValues } from 'lit';
import { customElement, state, property, query } from 'lit/decorators.js';
import { WorkflowMetadata, NodeMetadata, ConnectionMetadata } from './models/WorkflowMetadata';
import { WorkflowValidator } from './utils/WorkflowValidator';
import { WorkflowHistory } from './utils/WorkflowHistory';
import './components/NodePalette';
import './components/WorkflowCanvas';
import './components/WorkflowMinimap';
import { WorkflowCanvas } from './components/WorkflowCanvas';


import { apiClient } from '../core/api-client';

const STORAGE_KEY_PREFIX = 'workflow-designer-draft-';


@customElement('workflow-designer-page')
export class WorkflowDesignerPage extends LitElement {
  @property({ type: String }) appId: string | null = null;
  @query('workflow-canvas') private canvas?: WorkflowCanvas;

  @state() private workflowMetadata: WorkflowMetadata = {
    id: `workflow-${Date.now()}`,
    name: 'Untitled Workflow',
    version: 1,
    schemaVersion: '1.0.0',
    nodes: [],
    connections: []
  };

  @state() private selectedNodeIds = new Set<string>();
  @state() private selectedConnectionId?: string;
  @state() private validationResult?: { errors: string[], warnings: string[] };
  @state() private viewport = { x: 0, y: 0, width: 0, height: 0, scale: 1 };
  @state() private availableEntities: string[] = [];
  @state() private isSettingsOpen = false;

  @state() private preloadedWorkflows: any[] = [];
  @state() private isWorkflowSelectorOpen = false;

  private clipboard?: { nodes: NodeMetadata[], connections: ConnectionMetadata[] };

  private history = new WorkflowHistory();

  static styles = css`
    :host {
      display: block;
      height: 100%;
      width: 100%;
      background: #f8fafc;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      position: relative;
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

    .modal-overlay {
      position: fixed;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: rgba(0, 0, 0, 0.5);
      z-index: 1000;
      display: flex;
      align-items: center;
      justify-content: center;
      animation: fade-in 0.2s;
    }

    .modal-content {
      background: white;
      width: 500px;
      border-radius: 8px;
      box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
      padding: 24px;
      display: flex;
      flex-direction: column;
      gap: 16px;
      animation: scale-up 0.2s cubic-bezier(0.18, 0.89, 0.32, 1.28);
    }

    .modal-header {
      font-size: 18px;
      font-weight: 600;
      color: #1e293b;
      margin-bottom: 8px;
    }

    .modal-footer {
      display: flex;
      justify-content: flex-end;
      gap: 8px;
      margin-top: 16px;
    }

    @keyframes fade-in {
      from { opacity: 0; }
      to { opacity: 1; }
    }

    @keyframes scale-up {
      from { transform: scale(0.9); opacity: 0; }
      to { transform: scale(1); opacity: 1; }
    }
  `;

  connectedCallback() {
    super.connectedCallback();
    this.loadFromStorage();
    this.loadEntities(); // Load available entities
    window.addEventListener('keydown', this.handleKeyDown);
  }

  disconnectedCallback() {
    super.disconnectedCallback();
    window.removeEventListener('keydown', this.handleKeyDown);
  }

  private async loadEntities() {
    try {
      console.log('Loading entities from /schema...');
      const entities = await apiClient.get<string[]>('/schema');
      console.log('Entities loaded:', entities);

      if (!entities || entities.length === 0) {
        console.warn('No entities returned from API. Using fallback/mock data for development.');
        this.availableEntities = ['User', 'Order', 'Payment', 'Product', 'Customer', 'Invoice'];
      } else {
        this.availableEntities = entities;
      }
    } catch (err) {
      console.error('Failed to load entities:', err);
      // Fallback for debugging
      this.availableEntities = ['User', 'Order', 'Payment', 'Product'];
    }
  }

  updated(changedProperties: Map<string, any>) {
    if (changedProperties.has('appId')) {
      const oldAppId = changedProperties.get('appId');
      if (oldAppId !== this.appId) {
        // App switched, reload data
        this.history = new WorkflowHistory(); // Reset history

        if (this.appId) {
          this.loadFromStorage();
        } else {
          // No app selected, reset to default state
          this.workflowMetadata = this.createEmptyWorkflow();
          this.selectedNodeIds = new Set();
          this.selectedConnectionId = undefined;
        }
      }
    }
  }

  private createEmptyWorkflow(): WorkflowMetadata {
    return {
      id: `workflow-${Date.now()}`,
      name: 'Untitled Workflow',
      version: 1,
      schemaVersion: '1.0.0',
      nodes: [],
      connections: []
    };
  }

  private getStorageKey(): string | null {
    if (!this.appId) return null;
    return `${STORAGE_KEY_PREFIX}${this.appId}`;
  }

  private async loadFromStorage() {
    if (!this.appId) return;

    try {
      // 1. Try to load from API
      // Backend returns { workflows: [...] } or just the workflow object
      const response: any = await apiClient.get<any>(`/apps/${this.appId}/workflow`);

      let workflow: any = response;
      this.preloadedWorkflows = [];

      // Handle list format (take the first one for now)
      if (response && response.workflows && Array.isArray(response.workflows)) {
        this.preloadedWorkflows = response.workflows;
        if (response.workflows.length > 0) {
          workflow = response.workflows[0];
        } else {
          workflow = null;
        }
      }

      if (workflow && workflow.id) {
        this.loadWorkflowIntoEditor(workflow);
      } else {
        // 2. If no workflow on server, check for local draft (migration path)
        const key = this.getStorageKey();
        if (key) {
          const saved = localStorage.getItem(key);
          if (saved) {
            this.workflowMetadata = JSON.parse(saved);
            this.history = new WorkflowHistory();
            // Consolidate to server
            this.saveToStorage();
          } else {
            // New workflow
            this.workflowMetadata = this.createEmptyWorkflow();
          }
        } else {
          this.workflowMetadata = this.createEmptyWorkflow();
        }
      }
    } catch (e) {
      console.error('Failed to load workflow', e);
      // Fallback to local or empty on error
      this.workflowMetadata = this.createEmptyWorkflow();
    }
  }

  private loadWorkflowIntoEditor(workflow: any) {
    console.log('🔄 Loading workflow into editor:', workflow);

    // Adapter: Parse definitionJson if present (Backend stores it as string)
    if (typeof workflow.definitionJson === 'string') {
      try {
        const def = JSON.parse(workflow.definitionJson);
        console.log('📋 Parsed definitionJson:', def);

        // Adapter: Convert Backend Node Map to Frontend Node Array
        if (def.nodes && !Array.isArray(def.nodes)) {
          console.log('🗺️ Converting node Map to Array...');
          const nodesArray: any[] = [];
          let index = 0;
          Object.keys(def.nodes).forEach(key => {
            const n = def.nodes[key];
            console.log(`  Processing node '${key}':`, n);

            // Ensure it has an ID
            if (!n.id) n.id = key;
            // Ensure position exists
            if (!n.position) {
              // AUTO-LAYOUT: Stack horizontally if no position
              n.position = { x: 100 + (index * 250), y: 150 };
            }
            // TYPE NORMALIZATION: Backend generates lowercase types (e.g. "start", "task")
            // Frontend expects uppercase (e.g. "START", "TASK") for proper rendering
            const originalType = n.type;
            if (n.type && typeof n.type === 'string') {
              n.type = n.type.toUpperCase();
            }
            console.log(`    Type normalized: '${originalType}' → '${n.type}'`);
            console.log(`    Position: (${n.position.x}, ${n.position.y})`);

            // PROPERTIES NORMALIZATION: Backend stores fields directly on node
            // Frontend expects them nested under 'properties'
            if (!n.properties) {
              n.properties = {};
              // Move known backend fields into properties
              const backendFields = ['assignmentType', 'assignmentExpression', 'formFields', 'service', 'parameters', 'condition'];
              backendFields.forEach(field => {
                if (n[field] !== undefined) {
                  n.properties[field] = n[field];
                  delete n[field];
                }
              });
            }
            console.log(`    Properties:`, n.properties);

            nodesArray.push(n);
            index++;
          });
          workflow.nodes = nodesArray;
          console.log('✅ Converted nodes:', workflow.nodes);
        } else if (def.nodes) {
          console.log('📦 Nodes are already array, normalizing...');
          // Nodes are already an array, but still need type normalization and position checks
          workflow.nodes = def.nodes.map((n: any, index: number) => {
            console.log(`  Processing array node ${index}:`, n);

            // Ensure position exists
            if (!n.position) {
              n.position = { x: 100 + (index * 250), y: 150 };
            }
            // TYPE NORMALIZATION
            const originalType = n.type;
            if (n.type && typeof n.type === 'string') {
              n.type = n.type.toUpperCase();
            }
            console.log(`    Type normalized: '${originalType}' → '${n.type}'`);

            // PROPERTIES NORMALIZATION
            if (!n.properties) {
              n.properties = {};
              const backendFields = ['assignmentType', 'assignmentExpression', 'formFields', 'service', 'parameters', 'condition'];
              backendFields.forEach(field => {
                if (n[field] !== undefined) {
                  n.properties[field] = n[field];
                  delete n[field];
                }
              });
            }

            return n;
          });
          console.log('✅ Normalized nodes:', workflow.nodes);
        }

        // Adapter: Convert Backend Transitions to Frontend Connections
        if (def.transitions) {
          workflow.connections = def.transitions.map((t: any, idx: number) => ({
            id: t.id || `conn-${Date.now()}-${idx}`,
            from: t.from,
            to: t.to,
            label: t.label,
            condition: t.condition
          }));
        } else if (def.connections) {
          workflow.connections = def.connections;
        }
      } catch (e) {
        console.warn("Failed to parse definitionJson", e);
      }
    }

    // Ensure defaults
    if (!workflow.nodes) workflow.nodes = [];
    if (!workflow.connections) workflow.connections = [];

    console.log('🎯 Final workflow metadata:', workflow);
    console.log(`   - Nodes: ${workflow.nodes.length}`);
    console.log(`   - Connections: ${workflow.connections.length}`);

    this.workflowMetadata = workflow;
    this.history = new WorkflowHistory();
    this.selectedNodeIds = new Set();
    this.selectedConnectionId = undefined;
    this.requestUpdate();
  }

  private async saveToStorage() {
    if (!this.appId) return;

    try {
      // Save to server
      await apiClient.put(`/apps/${this.appId}/workflow`, this.workflowMetadata);

      // Keep local backup just in case (optional, maybe remove allowed?)
      const key = this.getStorageKey();
      if (key) {
        localStorage.setItem(key, JSON.stringify(this.workflowMetadata));
      }
    } catch (e) {
      console.error('Failed to save workflow', e);
    }
  }

  private updateMetadata(newMetadata: WorkflowMetadata) {
    this.history.push(this.workflowMetadata);
    this.workflowMetadata = newMetadata;
    this.saveToStorage();
    this.requestUpdate();
  }

  private handleKeyDown = (e: KeyboardEvent) => {
    // Ignore key events if user is typing in an input or textarea
    const target = e.target as HTMLElement;
    if (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA') return;

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
    // Delete: Backspace or Delete
    if (e.key === 'Backspace' || e.key === 'Delete') {
      e.preventDefault();
      if (this.selectedNodeIds.size > 0) {
        this.deleteSelectedNodes();
      } else if (this.selectedConnectionId) {
        this.deleteConnection(this.selectedConnectionId);
      }
    }

    // Copy: Cmd+C or Ctrl+C
    if ((e.metaKey || e.ctrlKey) && e.key === 'c') {
      e.preventDefault();
      this.handleCopy();
    }

    // Paste: Cmd+V or Ctrl+V
    if ((e.metaKey || e.ctrlKey) && e.key === 'v') {
      e.preventDefault();
      this.handlePaste();
    }
  };

  private handleCopy() {
    if (this.selectedNodeIds.size === 0) return;

    const nodesToCopy = this.workflowMetadata.nodes.filter(n => this.selectedNodeIds.has(n.id));
    // Copy connections ONLY if both source and target are in the selection
    const connectionsToCopy = this.workflowMetadata.connections.filter(c =>
      this.selectedNodeIds.has(c.from) && this.selectedNodeIds.has(c.to)
    );

    this.clipboard = {
      nodes: JSON.parse(JSON.stringify(nodesToCopy)),
      connections: JSON.parse(JSON.stringify(connectionsToCopy))
    };
    console.log('Copied to clipboard:', this.clipboard);
  }

  private handlePaste() {
    if (!this.clipboard || this.clipboard.nodes.length === 0) return;

    // Create ID mapping: oldId -> newId
    const idMap = new Map<string, string>();
    const timestamp = Date.now();

    // 1. Process Nodes
    const newNodes = this.clipboard.nodes.map((node, index) => {
      const newId = `${node.type.toLowerCase()}-${timestamp}-${index}`;
      idMap.set(node.id, newId);

      const GRID_SIZE = 20;
      const rawX = node.position.x + 20;
      const rawY = node.position.y + 20;

      return {
        ...node,
        id: newId,
        position: {
          x: Math.round(rawX / GRID_SIZE) * GRID_SIZE,
          y: Math.round(rawY / GRID_SIZE) * GRID_SIZE
        }
      };
    });

    // 2. Process Connections (relink to new IDs)
    const newConnections = this.clipboard.connections.map((conn, index) => {
      return {
        ...conn,
        id: `conn-${timestamp}-${index}`,
        from: idMap.get(conn.from)!,
        to: idMap.get(conn.to)!
      };
    });

    // 3. Add to metadata
    const newMetadata = {
      ...this.workflowMetadata,
      nodes: [...this.workflowMetadata.nodes, ...newNodes],
      connections: [...this.workflowMetadata.connections, ...newConnections]
    };

    this.updateMetadata(newMetadata);

    // 4. Select the new nodes
    this.selectedNodeIds = new Set(newNodes.map(n => n.id));
    this.selectedConnectionId = undefined;
  }

  private undo() {
    const prev = this.history.undo(this.workflowMetadata);
    if (prev) {
      this.workflowMetadata = prev;
      this.saveToStorage();
      this.saveToStorage();
      this.selectedNodeIds = new Set();
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

  private deleteNode(nodeId: string) {
    const newMetadata = {
      ...this.workflowMetadata,
      nodes: this.workflowMetadata.nodes.filter(n => n.id !== nodeId),
      connections: this.workflowMetadata.connections.filter(c => c.from !== nodeId && c.to !== nodeId)
    };
    this.updateMetadata(newMetadata);
    this.selectedNodeIds = new Set();
  }

  private deleteSelectedNodes() {
    if (this.selectedNodeIds.size === 0) return;

    const newMetadata = {
      ...this.workflowMetadata,
      nodes: this.workflowMetadata.nodes.filter(n => !this.selectedNodeIds.has(n.id)),
      // Remove connections linked to any deleted node
      connections: this.workflowMetadata.connections.filter(c =>
        !this.selectedNodeIds.has(c.from) && !this.selectedNodeIds.has(c.to)
      )
    };
    this.updateMetadata(newMetadata);
    this.selectedNodeIds = new Set();
  }

  private handleNodeSelect(e: CustomEvent) {
    const { nodeId, originalEvent } = e.detail;

    // Check for modifier keys (Shift or Cmd/Ctrl)
    const isMultiSelect = originalEvent?.shiftKey || originalEvent?.metaKey || originalEvent?.ctrlKey;

    if (isMultiSelect) {
      // Toggle selection
      const newSet = new Set(this.selectedNodeIds);
      if (nodeId) {
        if (newSet.has(nodeId)) {
          newSet.delete(nodeId);
        } else {
          newSet.add(nodeId);
        }
      }
      this.selectedNodeIds = newSet;
    } else {
      // Single selection (replace)
      if (nodeId) {
        this.selectedNodeIds = new Set([nodeId]);
      } else {
        this.selectedNodeIds = new Set();
      }
    }

    this.selectedConnectionId = undefined; // Clear connection selection when selecting nodes
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
            ${this.preloadedWorkflows.length > 1 ? html`
              <select 
                class="workflow-selector" 
                @change=${this.handleWorkflowChange} 
                .value=${this.workflowMetadata.id}
                style="padding: 6px; border-radius: 6px; border: 1px solid #e2e8f0; margin-right: 8px; max-width: 200px;"
              >
                ${this.preloadedWorkflows.map(wf => html`
                  <option value=${wf.id}>${wf.name || 'Untitled'}</option>
                `)}
              </select>
            ` : ''}

            <button class="btn btn-secondary" @click=${this.undo}>Undo</button>
            <button class="btn btn-secondary" @click=${this.redo}>Redo</button>
            <button class="btn btn-secondary" @click=${() => this.isSettingsOpen = true}>⚙ Settings</button>
            <button class="btn btn-secondary" @click=${this.handleValidate}>Validate</button>
            <button class="btn btn-secondary" @click=${this.handleExportJson}>Export JSON</button>
            <button class="btn btn-primary" @click=${this.handlePublish} ?disabled=${!this.appId}>Publish</button>
          </div>
        </div>

        <node-palette class="palette"></node-palette>

        <div class="canvas">
          <workflow-canvas 
            .metadata=${this.workflowMetadata}
            .selectedNodeIds=${this.selectedNodeIds}
            .selectedConnectionId=${this.selectedConnectionId}
            @node-add=${this.handleNodeAdd}
            @node-move=${this.handleNodeMove}
            @node-select=${this.handleNodeSelect}
            @connection-add=${this.handleConnectionAdd}
            @connection-select=${this.handleConnectionSelect}
            @connection-delete=${this.handleConnectionDelete}
            @viewport-change=${this.handleViewportChange}
          ></workflow-canvas>
          
          <workflow-minimap
            .nodes=${this.workflowMetadata.nodes}
            .viewport=${this.viewport}
            @minimap-nav=${this.handleMinimapNav}
          ></workflow-minimap>

          ${this.renderValidationToast()}
        </div>

        <div class="properties-panel">
          ${this.selectedConnectionId
        ? this.renderConnectionProperties()
        : this.renderPropertiesPanel()
      }
        </div>
      </div>
      
      ${this.renderSettingsModal()}
    `;
  }

  private renderSettingsModal() {
    if (!this.isSettingsOpen) return '';

    return html`
      <div class="modal-overlay" @click=${() => this.isSettingsOpen = false}>
        <div class="modal-content" @click=${(e: Event) => e.stopPropagation()}>
          <div class="modal-header">Workflow Settings</div>
          
          <div class="form-group">
            <label>Workflow Name</label>
            <input 
              type="text" 
              .value=${this.workflowMetadata.name}
              @input=${(e: Event) => this.handleMetadataChange('name', (e.target as HTMLInputElement).value)}
            />
          </div>

          <div class="form-group">
            <label>Trigger Entity</label>
            <select
              .value=${this.workflowMetadata.triggerEntity || ''}
              @change=${(e: Event) => this.handleMetadataChange('triggerEntity', (e.target as HTMLSelectElement).value)}
            >
              <option value="">-- No Auto-Trigger --</option>
              ${this.availableEntities.map(entity => html`
                <option value=${entity} ?selected=${entity === this.workflowMetadata.triggerEntity}>${entity}</option>
              `)}
            </select>
          </div>

          ${this.workflowMetadata.triggerEntity ? html`
            <div class="form-group">
              <label>Trigger Event</label>
              <select
                .value=${this.workflowMetadata.triggerEvent || ''}
                @change=${(e: Event) => this.handleMetadataChange('triggerEvent', (e.target as HTMLSelectElement).value)}
              >
                <option value="">Select Event...</option>
                <option value="ON_CREATE">On Create</option>
                <option value="ON_UPDATE">On Update</option>
                <option value="ON_DELETE">On Delete</option>
              </select>
            </div>

            <div class="form-group">
              <label>Trigger Condition (MVEL)</label>
              <textarea 
                rows="3"
                placeholder="e.g. status == 'PENDING' && amount > 1000"
                .value=${this.workflowMetadata.triggerCondition || ''}
                @input=${(e: Event) => this.handleMetadataChange('triggerCondition', (e.target as HTMLTextAreaElement).value)}
              ></textarea>
              <div class="help-text">Optional. Leave empty to trigger on all events.</div>
            </div>
          ` : ''}

          <div class="modal-footer">
            <button class="btn btn-primary" @click=${() => this.isSettingsOpen = false}>Close</button>
          </div>
        </div>
      </div>
    `;
  }

  private handleMetadataChange(field: keyof WorkflowMetadata, value: any) {
    const newMetadata = {
      ...this.workflowMetadata,
      [field]: value
    };
    this.updateMetadata(newMetadata);
  }

  private handleWorkflowChange(e: Event) {
    const select = e.target as HTMLSelectElement;
    const selectedId = select.value;
    const wf = this.preloadedWorkflows.find(w => w.id === selectedId);
    if (wf) {
      this.loadWorkflowIntoEditor(wf);
    }
  }

  private renderConnectionProperties() {
    if (!this.selectedConnectionId) return '';

    const conn = this.workflowMetadata.connections.find(c => c.id === this.selectedConnectionId);
    if (!conn) return '';

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

  private renderPropertiesPanel() {
    if (this.selectedNodeIds.size === 0) {
      return html`
        <div class="properties-empty">
          <div class="empty-icon">⚙️</div>
          <p>Select a node or connection to edit its properties</p>
        </div>
      `;
    }

    if (this.selectedNodeIds.size > 1) {
      return html`
        <div class="properties-empty">
          <div class="empty-icon">📚</div>
          <p>${this.selectedNodeIds.size} nodes selected</p>
          <div class="actions-footer" style="width: 100%">
            <button class="btn btn-secondary btn-danger" @click=${() => this.deleteSelectedNodes()}>
              Delete ${this.selectedNodeIds.size} Nodes
            </button>
          </div>
        </div>
      `;
    }

    // Single node selected
    const nodeId = Array.from(this.selectedNodeIds)[0];
    const node = this.workflowMetadata.nodes.find((n: NodeMetadata) => n.id === nodeId);
    if (!node) return html``;

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
      
      <div class="divider"></div>
      
      ${this.renderEntitySelector(node)}

      <div class="actions-footer">
        <button class="btn btn-secondary btn-danger" @click=${() => this.deleteNode(node.id)}>
          Delete Node
        </button>
      </div>
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

  private renderEntitySelector(node: NodeMetadata) {
    const currentEntity = node.properties.entityName || '';
    return html`
      <div class="form-group">
        <label>Associated Entity</label>
        <select 
          .value=${currentEntity} 
          @change=${(e: Event) => this.handleNodePropertyChange(node.id, 'entityName', (e.target as HTMLSelectElement).value)}
        >
          <option value="">-- Select Entity --</option>
          ${this.availableEntities.map(entity => html`
            <option value=${entity} ?selected=${entity === currentEntity}>${entity}</option>
          `)}
        </select>
        <div class="help-text">Associate this task with a data entity.</div>
      </div>
    `;
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
    this.updateMetadata(newMetadata);
    this.selectedNodeIds = new Set([node.id]);
    this.selectedConnectionId = undefined;
  }

  private handleNodeMove(e: CustomEvent) {
    const { nodeId, position } = e.detail;

    // Find the node being dragged to calculate delta
    const draggedNode = this.workflowMetadata.nodes.find(n => n.id === nodeId);
    if (!draggedNode) return;

    // Calculate delta (new position - old position)
    const dx = position.x - draggedNode.position.x;
    const dy = position.y - draggedNode.position.y;

    let newNodes = this.workflowMetadata.nodes;

    if (this.selectedNodeIds.has(nodeId)) {
      // If the dragged node is selected, move ALL selected nodes
      newNodes = this.workflowMetadata.nodes.map(n => {
        if (this.selectedNodeIds.has(n.id)) {
          return {
            ...n,
            position: {
              x: n.position.x + dx,
              y: n.position.y + dy
            }
          };
        }
        return n;
      });
    } else {
      // If dragging an unselected node, just move that one
      // (Optionally, we could select it here, but let's stick to simple move)
      newNodes = this.workflowMetadata.nodes.map(n =>
        n.id === nodeId ? { ...n, position } : n
      );
    }

    // Update local state without history to prevent flooding stack
    this.workflowMetadata = {
      ...this.workflowMetadata,
      nodes: newNodes
    };
    this.saveToStorage();
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
    this.selectedNodeIds = new Set();
  }

  private handleConnectionSelect(e: CustomEvent) {
    this.selectedConnectionId = e.detail.connectionId;
    this.selectedNodeIds = new Set();
  }

  private handleConnectionDelete(e: CustomEvent) {
    this.deleteConnection(e.detail.connectionId.toString());
  }

  private handleViewportChange(e: CustomEvent) {
    this.viewport = e.detail;
  }

  private handleMinimapNav(e: CustomEvent) {
    const { x, y } = e.detail;
    this.canvas?.setViewport(x, y);
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
    // 1. Validate
    const validation = WorkflowValidator.validate(this.workflowMetadata);
    this.validationResult = { errors: validation.errors, warnings: validation.warnings };

    if (!validation.valid && validation.errors.length > 0) {
      alert('Cannot publish invalid workflow. Please fix errors first.');
      return;
    }

    if (!this.appId) {
      alert('Cannot publish: App ID missing.');
      return;
    }

    try {
      // 2. FORCE SAVE to backend first
      // This ensures the backend has the workflow record before we try to publish it.
      await this.saveToStorage();

      // 3. Publish
      // Note: saveToStorage might have updated the ID if the backend assigned a real one.
      // But currently we use client-generated IDs. If backend expects something else, we might need to handle response from save.
      await apiClient.post(`/api/workflows/${this.workflowMetadata.id}/publish`, {});
      alert(`Workflow "${this.workflowMetadata.name}" published successfully!`);
    } catch (err) {
      console.error('Failed to publish workflow:', err);
      // @ts-ignore
      alert(`Failed to publish workflow: ${err.message || 'Unknown error'}`);
    }
  }

  private handleExportJson() {
    // 1. Export
    console.log('Exporting workflow JSON:', this.workflowMetadata);
    const blob = new Blob([JSON.stringify(this.workflowMetadata, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);

    // Auto-download
    const a = document.createElement('a');
    a.href = url;
    a.download = `${this.workflowMetadata.name.toLowerCase().replace(/\s+/g, '-')} -v${this.workflowMetadata.version}.json`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'workflow-designer-page': WorkflowDesignerPage;
  }
}
