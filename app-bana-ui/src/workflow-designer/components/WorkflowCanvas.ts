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
  @property({ type: Object }) selectedNodeIds = new Set<string>();
  @property() selectedConnectionId?: string;

  @state() private isDragging = false;
  @state() private dragOffset = { x: 0, y: 0 };
  @state() private connectionDraft?: { sourceNodeId: string; startX: number; startY: number; mouseX: number; mouseY: number };

  // Selection Box State
  @state() private isSelecting = false;
  @state() private selectionBox?: { startX: number; startY: number; currentX: number; currentY: number };

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

    .selection-box {
      position: absolute;
      border: 1px solid #3b82f6;
      background: rgba(59, 130, 246, 0.1);
      pointer-events: none;
      z-index: 100;
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

      // Listen for panzoom changes
      this.canvasEl.addEventListener('panzoomchange', ((e: CustomEvent) => {
        const pan = this.panzoom.getPan();
        const scale = this.panzoom.getScale();

        // Canvas dimensions (viewport size)
        const rect = this.canvasEl!.getBoundingClientRect();

        this.dispatchEvent(new CustomEvent('viewport-change', {
          detail: {
            x: -pan.x / scale, // Convert to world coordinates
            y: -pan.y / scale,
            width: rect.width / scale,
            height: rect.height / scale,
            scale: scale
          },
          bubbles: true,
          composed: true
        }));
      }) as EventListener);
    }
  }

  public setViewport(x: number, y: number) {
    if (!this.panzoom || !this.canvasEl) return;

    const scale = this.panzoom.getScale();
    const rect = this.canvasEl.getBoundingClientRect();

    // We want (x, y) to be the CENTER of the viewport
    // Panzoom 'pan' is the offset of the top-left corner of the content
    // Pan = -WorldPos * Scale

    // Target Pan X = -(x - viewportWidth/2/scale) * scale ... almost
    // Let's deduce:
    // Viewport Width in World Units = rect.width / scale
    // Top-Left World X = x - (rect.width / scale) / 2

    const targetWorldLeft = x - (rect.width / scale) / 2;
    const targetWorldTop = y - (rect.height / scale) / 2;

    const targetPanX = -targetWorldLeft * scale;
    const targetPanY = -targetWorldTop * scale;

    this.panzoom.pan(targetPanX, targetPanY);
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
              .selected=${this.selectedNodeIds.has(node.id)}
              @click=${(e: Event) => this.handleNodeClick(e as MouseEvent, node)}
              @node-drag-start=${this.handleNodeDragStart}
              @connection-start=${this.handleConnectionStart}
              type=${node.type}
            ></workflow-node>
          `)}
        </div>
        </div>
        
        ${this.renderSelectionBox()}
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

    // Check if dragging an existing node or a new template
    const types = e.dataTransfer!.types;
    // Note: types is DOMStringList in some browsers, but Array.includes works in newer ones
    // Safer to use Array.from or just check logic
    if (types.includes && types.includes('node-id')) {
      e.dataTransfer!.dropEffect = 'move';
    } else if (types.indexOf && types.indexOf('node-id') !== -1) {
      e.dataTransfer!.dropEffect = 'move';
    } else {
      e.dataTransfer!.dropEffect = 'copy';
    }

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
    console.log('Drop event:', { nodeId, rawX: x, rawY: y });

    if (nodeId) {
      // Calculate correct position using the offset
      const offsetXStr = e.dataTransfer!.getData('drag-offset-x');
      const offsetYStr = e.dataTransfer!.getData('drag-offset-y');

      let finalX = x;
      let finalY = y;

      if (offsetXStr && offsetYStr) {
        const offsetX = parseFloat(offsetXStr) / scale;
        const offsetY = parseFloat(offsetYStr) / scale;
        finalX = x - offsetX;
        finalY = y - offsetY;
      }

      // Snap to Grid
      const GRID_SIZE = 20;
      finalX = Math.round(finalX / GRID_SIZE) * GRID_SIZE;
      finalY = Math.round(finalY / GRID_SIZE) * GRID_SIZE;

      console.log('Moving node (snapped):', { nodeId, finalX, finalY });

      // Moving existing node
      this.dispatchEvent(new CustomEvent('node-move', {
        detail: { nodeId, position: { x: finalX, y: finalY } },
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

  private handleNodeClick(e: MouseEvent, node: NodeMetadata) {
    e.stopPropagation();
    this.dispatchEvent(new CustomEvent('node-select', {
      detail: {
        nodeId: node.id,
        originalEvent: e
      },
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

  // ========== Selection Box ==========

  private renderSelectionBox() {
    if (!this.selectionBox) return '';

    // Calculate CSS styles
    const left = Math.min(this.selectionBox.startX, this.selectionBox.currentX);
    const top = Math.min(this.selectionBox.startY, this.selectionBox.currentY);
    const width = Math.abs(this.selectionBox.currentX - this.selectionBox.startX);
    const height = Math.abs(this.selectionBox.currentY - this.selectionBox.startY);

    return html`
      <div class="selection-box" style="left: ${left}px; top: ${top}px; width: ${width}px; height: ${height}px;"></div>
    `;
  }

  private handleMouseDown(e: MouseEvent) {
    // If clicking on canvas (not node/conn), start selection box
    // But only if Shift is NOT pressed? Usually selection box is default on background drag.
    // Panzoom handles panning unless we stop propagation or it's excluded.
    // We configured Panzoom with `panOnlyWhenZoomed: true`. 
    // This implies that normal drag on background DOES NOT PAN. It's free for us!

    if (e.target === this.canvasEl || e.target === this) {
      if (this.metadata.nodes.length === 0) return; // Optional

      const rect = this.canvasEl!.getBoundingClientRect();
      const x = e.clientX - rect.left;
      const y = e.clientY - rect.top;

      this.isSelecting = true;
      this.selectionBox = { startX: x, startY: y, currentX: x, currentY: y };

      // Clear selection if not holding shift/cmd
      if (!e.shiftKey && !e.metaKey && !e.ctrlKey) {
        this.dispatchEvent(new CustomEvent('node-select', { detail: { nodeId: undefined } }));
      }
    }
  }

  private handleMouseUp(e: MouseEvent) {
    if (this.connectionDraft) {
      // Handle connection drop
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
    } else if (this.isSelecting && this.selectionBox) {
      this.finalizeSelection();
    }

    this.isSelecting = false;
    this.selectionBox = undefined;
    this.isDragging = false;
  }

  private finalizeSelection() {
    if (!this.selectionBox) return;

    // Convert selection box to canvas coordinates (accounting for pan/zoom)
    const scale = this.panzoom?.getScale() || 1;
    const pan = this.panzoom?.getPan() || { x: 0, y: 0 };

    const boxLeft = Math.min(this.selectionBox.startX, this.selectionBox.currentX);
    const boxTop = Math.min(this.selectionBox.startY, this.selectionBox.currentY);
    const boxRight = Math.max(this.selectionBox.startX, this.selectionBox.currentX);
    const boxBottom = Math.max(this.selectionBox.startY, this.selectionBox.currentY);

    // Transform box to logical coordinates
    const logicalLeft = (boxLeft - pan.x) / scale;
    const logicalTop = (boxTop - pan.y) / scale;
    const logicalRight = (boxRight - pan.x) / scale;
    const logicalBottom = (boxBottom - pan.y) / scale;

    const selectedIds: string[] = [];

    // Check intersection with nodes
    // Assuming standard node size for hit testing (w: 180, h: 80)
    // Or better, checking center point.
    this.metadata.nodes.forEach(node => {
      const nx = node.position.x;
      const ny = node.position.y;
      const nw = 180;
      const nh = 80;

      // Check if node rect overlaps with selection rect
      const nodeRight = nx + nw;
      const nodeBottom = ny + nh;

      const overlaps = !(nx > logicalRight ||
        nodeRight < logicalLeft ||
        ny > logicalBottom ||
        nodeBottom < logicalTop);

      if (overlaps) {
        selectedIds.push(node.id);
      }
    });

    // We can't batch 'node-select' events easily for multi-select unless we change the event contract.
    // The current WorkflowDesignerPage handles 'node-select' one by one?
    // No, I refactored it: `handleNodeSelect` takes an event. If I fire it multiple times, it updates the set.
    // BUT! `this.selectedNodeIds` is a property. `WorkflowDesignerPage` updates it.
    // If I fire 5 events synchronously, `WorkflowDesignerPage` might process them, but `this.selectedNodeIds` prop update might be batched.
    // Actually, `WorkflowDesignerPage.handleNodeSelect` uses `this.selectedNodeIds` (the state).
    // If I fire multiple events, the state update cycle might not happen in between. 
    // It's better to fire a SINGLE event with ALL IDs?
    // Or fire one event per ID.

    // Current `node-select` expects `nodeId`.
    // I should iterate and fire.
    // BUT! `handleNodeSelect` logic:
    // if (isMultiSelect) { add/remove from CURRENT set }
    // else { replace with NEW set }

    // If i fire loop:
    // Event 1: Page sees current set (empty). Adds ID1.
    // Event 2: Page sees current set (empty/stale). Adds ID2.
    // Result: valid updates? No, if `this.selectedNodeIds` isn't updated, it will use the OLD value.
    // So the page handlers need to see the "live" set.

    // BETTER: Emit a NEW event `multi-select` with list of IDs.
    // OR: Emit `node-select` with `nodeIds` (plural).

    // I should update `WorkflowDesignerPage` to handle `nodeIds` in `detail` if present.
    // Let's assume for now I will fire multiple events with `ctrlKey` set to true?
    // No, sync issues.

    // I will modify `handleNodeSelect` in Page to accept `nodeIds` array.
    // But I just refactored Page.

    // Let's stick to modifying Page to accept `nodeIds`.

    // Wait, I can't modify Page in this specific tool call (it's for Canvas).
    // I will assume I CANNOT do that.

    // Alternative:
    // Emit `node-select` with `nodeId` for the FIRST one (clearing others), then `node-select` with `ctrlKey` simulated for others?
    // Still race condition on state.

    // I MUST support a multi-select event.
    // Let's implement `dispatchMultiSelect` in Canvas and update Page in next step or now?

    // Let's update `handleMouseMove` first.

    // ...
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

    this.selectedNodeIds = new Set();
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
    } else if (this.isSelecting && this.selectionBox) {
      const rect = this.canvasEl!.getBoundingClientRect();
      const x = e.clientX - rect.left;
      const y = e.clientY - rect.top;

      this.selectionBox = { ...this.selectionBox, currentX: x, currentY: y };
    }
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
