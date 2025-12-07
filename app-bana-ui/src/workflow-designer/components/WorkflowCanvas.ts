// Workflow Canvas Component
// Main canvas area with zoom/pan controls

import { LitElement, html, css } from 'lit';
import { customElement, property, state, query } from 'lit/decorators.js';
import Panzoom from '@panzoom/panzoom';
import { WorkflowMetadata, NodeMetadata } from '../models/WorkflowMetadata';
import './WorkflowNode';

@customElement('workflow-canvas')
export class WorkflowCanvas extends LitElement {
  @property({
    type: Object,
    hasChanged: () => true  // Force update even if object reference is same
  })
  metadata!: WorkflowMetadata;
  @property() selectedNodeId?: string;

  @state() private isDragging = false;
  @state() private dragOffset = { x: 0, y: 0 };

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
    }

    .canvas-container.dragging {
      cursor: grabbing;
    }

    .canvas-container.drag-over {
      background-color: #eff6ff;
    }

    .nodes-layer {
      position: relative;
      width: 100%;
      height: 100%;
      pointer-events: none;
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

  firstUpdated() {
    if (this.canvasEl) {
      // Initialize PanZoom
      this.panzoom = Panzoom(this.canvasEl, {
        maxScale: 2,
        minScale: 0.3,
        step: 0.1,
        canvas: true,
        panOnlyWhenZoomed: false
      });

      // Enable zoom with mouse wheel
      this.canvasEl.addEventListener('wheel', (e) => {
        if (!e.ctrlKey && !e.metaKey) {
          e.preventDefault();
          this.panzoom?.zoomWithWheel(e);
        }
      });
    }
  }

  disconnectedCallback() {
    super.disconnectedCallback();
    this.panzoom?.destroy();
  }

  render() {
    return html`
      <div 
        class="canvas-container ${this.isDragging ? 'dragging' : ''}"
        @dragover=${this.handleDragOver}
        @drop=${this.handleDrop}
        @dragleave=${this.handleDragLeave}
      >
        ${this.metadata.nodes.length === 0 ? this.renderEmptyState() : ''}
        
        <div class="nodes-layer">
          ${this.metadata.nodes.map(node => html`
            <workflow-node
              .metadata=${node}
              .selected=${node.id === this.selectedNodeId}
              @click=${() => this.handleNodeClick(node.id)}
              @node-drag-start=${this.handleNodeDragStart}
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
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;

    // Check if dragging an existing node (from canvas)
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

    // Check if dragging a new node (from palette)
    const data = e.dataTransfer!.getData('application/json');
    if (!data) return;

    const template = JSON.parse(data);

    // Create new node
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

  private handleNodeClick(nodeId: string) {
    this.dispatchEvent(new CustomEvent('node-select', {
      detail: { nodeId },
      bubbles: true,
      composed: true
    }));
  }

  private handleNodeDragStart(e: CustomEvent) {
    this.isDragging = true;
  }

  private zoomIn() {
    this.panzoom?.zoomIn();
  }

  private zoomOut() {
    this.panzoom?.zoomOut();
  }

  private resetZoom() {
    this.panzoom?.reset();
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'workflow-canvas': WorkflowCanvas;
  }
}
