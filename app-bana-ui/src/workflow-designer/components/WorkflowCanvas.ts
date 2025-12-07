// Workflow Canvas Component
// Main canvas area with zoom/pan controls

import { LitElement, html, css } from 'lit';
import { customElement, property, state, query } from 'lit/decorators.js';
import Panzoom from '@panzoom/panzoom';
import { WorkflowMetadata, NodeMetadata, ConnectionMetadata } from '../models/WorkflowMetadata';
import './WorkflowNode';
import './WorkflowConnection';

@customElement('workflow-canvas')
export class WorkflowCanvas extends LitElement {
  @property({
    type: Object,
    hasChanged: () => true  // Force update even if object reference is same
  })
  metadata!: WorkflowMetadata;
  @property() selectedNodeId?: string;
  @property() selectedConnectionId?: string;

  @state() private isDragging = false;
  @state() private dragOffset = { x: 0, y: 0 };
  @state() private connectionDraft?: { sourceNodeId: string; startX: number; startY: number; mouseX: number; mouseY: number };

  @query('.canvas-container') private canvasEl?: HTMLDivElement;
  private panzoom?: any;

  static styles = css`
    :host {
      display: block;
      width: 100%;
      height: 100%;
      min-height: 500px;
      position: relative;
      background: #f8fafc;
      overflow: hidden;
    }

    .canvas-container {
      width: 100%;
      height: 100%;
      position: relative;
      background-image: 
        radial-gradient(circle, #cbd5e1 1px, transparent 1px);
      background-size: 20px 20px;
      cursor: grab;
      transform-origin: 0 0; /* Important for Panzoom */
    }

    .canvas-container.dragging {
      cursor: grabbing;
    }

    .canvas-container.drag-over {
      background-color: #eff6ff;
    }

    .connections-layer {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      pointer-events: none;
      z-index: 1;
    }
    
    .connections-layer workflow-connection {
      pointer-events: all;
    }
    
    .connections-layer svg {
      pointer-events: none;
    }

    .nodes-layer {
      position: relative;
      width: 100%;
      height: 100%;
      pointer-events: none;
      z-index: 2;
    }
    
    .nodes-layer > * {
      pointer-events: all;
    }

    .zoom-controls {
      position: absolute;
      bottom: 16px;
      right: 16px;
      display: flex;
      flex-direction: column;
      gap: 8px;
      background: white;
      border-radius: 8px;
      padding: 8px;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
      z-index: 10;
    }

    .zoom-btn {
      width: 36px;
      height: 36px;
      border: none;
      background: white;
      border-radius: 6px;
      cursor: pointer;
      font-size: 18px;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.2s;
    }

    .zoom-btn:hover {
      background: #f1f5f9;
    }

    .zoom-level {
      font-size: 11px;
      text-align: center;
      color: #64748b;
      padding: 4px;
    }

    .empty-state {
      position: absolute;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      text-align: center;
      color: #94a3b8;
      pointer-events: none;
    }

    .empty-state-icon {
      font-size: 48px;
      margin-bottom: 12px;
    }

    .empty-state-text {
      font-size: 14px;
    }
  `;

  connectedCallback() {
    super.connectedCallback();
    // Add keyboard event listener for deletion and cancel
    window.addEventListener('keydown', this.handleKeyDown);
  }

  firstUpdated() {
    if (this.canvasEl) {
      // Initialize PanZoom
      this.panzoom = Panzoom(this.canvasEl, {
        maxScale: 2,
        minScale: 0.3,
        step: 0.1,
        canvas: true,
        panOnlyWhenZoomed: true, // Allow interaction with nodes without pressing keys
        excludeClass: 'node', // Allow dragging nodes
      });

      // Enable zoom with mouse wheel
      this.canvasEl.addEventListener('wheel', (e) => {
        if (!e.ctrlKey && !e.metaKey) {
          // If NOT holding ctrl/meta, we pan? No, usually wheel zooms or scrolls. 
          // Let's standard: Wheel = Zoom (Google Maps style) or Ctrl+Wheel = Zoom
          // For now, let's say Ctrl+Wheel = Zoom, Wheel = Pan?
          // Actually, PanZoom handles wheel zooming if enabled.

          if (e.ctrlKey) {
            this.panzoom?.zoomWithWheel(e);
          }
        }
      });
    }
  }

  disconnectedCallback() {
    super.disconnectedCallback();
    this.panzoom?.destroy();
    window.removeEventListener('keydown', this.handleKeyDown);
  }

  render() {
    return html`
      <div 
        class="canvas-container ${this.isDragging ? 'dragging' : ''}"
        @dragover=${this.handleDragOver}
        @drop=${this.handleDrop}
        @dragleave=${this.handleDragLeave}
        @mousemove=${this.handleMouseMove}
        @mouseup=${this.handleMouseUp}
        @mousedown=${this.handleMouseDown}
      >
        ${this.metadata.nodes?.length === 0 ? this.renderEmptyState() : ''}
        
        <!-- Connections layer (SVG) - Below nodes -->
        <div class="connections-layer">
          ${this.renderConnections()}
          ${this.connectionDraft ? this.renderDraftConnection() : ''}
        </div>

        <!-- Nodes layer - Above connections -->
        <div class="nodes-layer">
          ${this.metadata.nodes?.map(node => html`
            <workflow-node
              .metadata=${node}
              .selected=${node.id === this.selectedNodeId}
              @click=${(e: Event) => this.handleNodeClick(e, node.id)}
              @node-drag-start=${this.handleNodeDragStart}
              @connection-start=${this.handleConnectionStart}
              type=${node.type}
            ></workflow-node>
          `)}
        </div>
      </div>

      <div class="zoom-controls">
        <button class="zoom-btn" @click=${this.zoomIn} title="Zoom In">
          +
        </button>
        <div class="zoom-level">100%</div>
        <button class="zoom-btn" @click=${this.zoomOut} title="Zoom Out">
          −
        </button>
        <button class="zoom-btn" @click=${this.resetZoom} title="Reset">
          ⊙
        </button>
      </div>
    `;
  }

  private renderConnections() {
    return this.metadata.connections?.map(conn => {
      const sourceNode = this.metadata.nodes.find(n => n.id === conn.from);
      const targetNode = this.metadata.nodes.find(n => n.id === conn.to);

      if (!sourceNode || !targetNode) return '';

      // Simple anchor calculation: Right of Source -> Left of Target
      // Assuming node width ~150px, height ~80px.
      // TODO: Get actual dimensions if possible or use smarter anchoring
      const startX = sourceNode.position.x + 160; // Right edge
      const startY = sourceNode.position.y + 40;  // Center Y

      const endX = targetNode.position.x;         // Left edge
      const endY = targetNode.position.y + 40;    // Center Y

      return html`
        <workflow-connection
          .startX=${startX}
          .startY=${startY}
          .endX=${endX}
          .endY=${endY}
          type="curved"
          @click=${(e: Event) => this.handleConnectionClick(e, conn.id)}
        ></workflow-connection>
      `;
    });
  }

  private renderDraftConnection() {
    if (!this.connectionDraft) return '';

    return html`
      <workflow-connection
        .startX=${this.connectionDraft.startX}
        .startY=${this.connectionDraft.startY}
        .endX=${this.connectionDraft.mouseX}
        .endY=${this.connectionDraft.mouseY}
        type="straight"
        style="opacity: 0.6; pointer-events: none;"
      ></workflow-connection>
    `;
  }

  private renderEmptyState() {
    return html`
      <div class="empty-state">
        <div class="empty-state-icon">📋</div>
        <div class="empty-state-text">
          Drag components from the palette to start building your workflow
        </div>
      </div>
    `;
  }

  private handleDragOver(e: DragEvent) {
    if (this.connectionDraft) return; // Don't handle dragover if drawing connection
    e.preventDefault();
    e.dataTransfer!.dropEffect = 'copy';
    this.canvasEl?.classList.add('drag-over');
  }

  private handleDragLeave(e: DragEvent) {
    this.canvasEl?.classList.remove('drag-over');
  }

  private handleDrop(e: DragEvent) {
    e.preventDefault();
    this.canvasEl?.classList.remove('drag-over');

    const rect = this.canvasEl!.getBoundingClientRect();
    // Use panzoom scale/pan to adjust coordinates
    const scale = this.panzoom?.getScale() || 1;
    const pan = this.panzoom?.getPan() || { x: 0, y: 0 };

    const x = (e.clientX - rect.left - pan.x) / scale;
    const y = (e.clientY - rect.top - pan.y) / scale;

    const nodeId = e.dataTransfer!.getData('node-id');
    if (nodeId) {
      // Moving existing node
      this.dispatchEvent(new CustomEvent('node-move', {
        detail: { nodeId, position: { x, y } },
        bubbles: true,
        composed: true
      }));
      return;
    }

    const data = e.dataTransfer!.getData('application/json');
    if (!data) return;

    const template = JSON.parse(data);
    const newNode: NodeMetadata = {
      id: `${template.type.toLowerCase()}-${Date.now()}`,
      type: template.type,
      position: { x, y },
      label: template.label,
      properties: {}
    };

    this.dispatchEvent(new CustomEvent('node-add', {
      detail: { node: newNode },
      bubbles: true,
      composed: true
    }));
  }

  private handleNodeClick(e: Event, nodeId: string) {
    e.stopPropagation();
    this.dispatchEvent(new CustomEvent('node-select', {
      detail: { nodeId },
      bubbles: true,
      composed: true
    }));
  }

  private handleConnectionClick(e: Event, connectionId: string) {
    e.stopPropagation();
    this.dispatchEvent(new CustomEvent('connection-select', {
      detail: { connectionId },
      bubbles: true,
      composed: true
    }));
  }

  private handleNodeDragStart(e: CustomEvent) {
    this.isDragging = true;
  }

  private handleMouseDown(e: MouseEvent) {
    // If clicking on canvas (not node/conn), clear selection
    if (e.target === this.canvasEl || e.target === this) {
      this.dispatchEvent(new CustomEvent('node-select', { detail: { nodeId: undefined } }));
    }
  }

  // ========== Connection Management ==========

  private handleConnectionStart(e: CustomEvent) {
    e.stopPropagation();
    const { nodeId, event } = e.detail;
    const mouseEvent = event as MouseEvent;

    // Get source position
    const sourceNode = this.metadata.nodes.find(n => n.id === nodeId);
    if (!sourceNode) return;

    // Use actual mouse position for start of drag? 
    // Or snap to handle? Snapping to handle is better visually.
    // For now, lets use the calculated "Right" handle position of the source node
    const startX = sourceNode.position.x + 160;
    const startY = sourceNode.position.y + 40;

    // Calculate canvas-relative mouse position for end
    const rect = this.canvasEl!.getBoundingClientRect();
    const scale = this.panzoom?.getScale() || 1;
    const pan = this.panzoom?.getPan() || { x: 0, y: 0 };

    const mouseX = (mouseEvent.clientX - rect.left - pan.x) / scale;
    const mouseY = (mouseEvent.clientY - rect.top - pan.y) / scale;

    this.connectionDraft = {
      sourceNodeId: nodeId,
      startX,
      startY,
      mouseX,
      mouseY
    };

    this.selectedNodeId = undefined;
    this.selectedConnectionId = undefined;
  }

  private handleMouseMove(e: MouseEvent) {
    if (this.connectionDraft) {
      const rect = this.canvasEl!.getBoundingClientRect();
      const scale = this.panzoom?.getScale() || 1;
      const pan = this.panzoom?.getPan() || { x: 0, y: 0 };

      const mouseX = (e.clientX - rect.left - pan.x) / scale;
      const mouseY = (e.clientY - rect.top - pan.y) / scale;

      this.connectionDraft = {
        ...this.connectionDraft,
        mouseX, // Update end position
        mouseY
      };
    }
  }

  private handleMouseUp(e: MouseEvent) {
    if (this.connectionDraft) {
      // Logic to find if we dropped on a handle/node
      // Since SVG overlay might block mouse events or we are dragging, we use elementFromPoint
      // But we are in Shadow DOM, so standard elementFromPoint might be tricky?
      // Actually, e.target should work if we are listening on canvas.

      // We need to see if we possess a node under the mouse.
      // e.target might be the canvas because the draft connection line pointer-events: none.

      // Let's use `composedPath` to find if we are over a node handle.
      const path = e.composedPath();
      const handle = path.find(el => (el as Element).classList?.contains('handle'));

      if (handle) {
        // Find the workflow-node parent
        const nodeEl = path.find(el => (el as Element).tagName === 'WORKFLOW-NODE') as any;
        if (nodeEl && nodeEl.metadata) {
          const targetNodeId = nodeEl.metadata.id;

          if (targetNodeId !== this.connectionDraft.sourceNodeId) {
            // Create connection
            const newConnection: ConnectionMetadata = {
              id: `conn-${Date.now()}`,
              from: this.connectionDraft.sourceNodeId,
              to: targetNodeId
            };

            this.dispatchEvent(new CustomEvent('connection-add', {
              detail: { connection: newConnection },
              bubbles: true,
              composed: true
            }));
          }
        }
      }

      this.connectionDraft = undefined;
    }

    this.isDragging = false;
  }

  // ========== Zoom Controls ==========

  private zoomIn() { this.panzoom?.zoomIn(); }
  private zoomOut() { this.panzoom?.zoomOut(); }
  private resetZoom() { this.panzoom?.reset(); }

  private handleKeyDown = (e: KeyboardEvent) => {
    if (e.key === 'Delete' || e.key === 'Backspace') {
      if (this.selectedConnectionId) {
        e.preventDefault();
        this.dispatchEvent(new CustomEvent('connection-delete', {
          detail: { connectionId: this.selectedConnectionId },
          bubbles: true,
          composed: true
        }));
        this.selectedConnectionId = undefined;
      }
    }
    if (e.key === 'Escape') {
      this.connectionDraft = undefined;
    }
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'workflow-canvas': WorkflowCanvas;
  }
}
