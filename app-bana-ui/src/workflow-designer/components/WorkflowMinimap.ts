
import { LitElement, html, css } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { NodeMetadata } from '../models/WorkflowMetadata';

@customElement('workflow-minimap')
export class WorkflowMinimap extends LitElement {
    @property({ type: Array }) nodes: NodeMetadata[] = [];
    @property({ type: Object }) viewport = { x: 0, y: 0, width: 0, height: 0, scale: 1 };

    static styles = css`
    :host {
      display: block;
      position: absolute;
      bottom: 16px;
      right: 16px; /* Adjust if zoom controls are here */
      width: 200px;
      height: 150px;
      background: rgba(255, 255, 255, 0.9);
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
      overflow: hidden;
      z-index: 50;
    }

    .map-container {
      width: 100%;
      height: 100%;
      position: relative;
      background: #f8fafc;
    }

    .mini-node {
      position: absolute;
      background: #cbd5e1;
      border-radius: 2px;
      pointer-events: none;
    }
    
    .mini-node.selected {
      background: #3b82f6;
    }

    .viewport-rect {
      position: absolute;
      border: 2px solid #3b82f6;
      background: rgba(59, 130, 246, 0.1);
      cursor: grab;
      transition: background 0.2s;
    }

    .viewport-rect:hover {
      background: rgba(59, 130, 246, 0.2);
    }
    
    .viewport-rect:active {
      cursor: grabbing;
    }
  `;

    render() {
        // 1. Calculate bounding box of all nodes
        const bounds = this.getBounds();

        // 2. Determine scale to fit bounds into minimap (with padding)
        const padding = 50; // Logical padding around nodes
        const mapWidth = 200;
        const mapHeight = 150;

        // Virtual world bounds
        const worldLeft = Math.min(bounds.minX, this.viewport.x);
        const worldTop = Math.min(bounds.minY, this.viewport.y);
        const worldRight = Math.max(bounds.maxX, this.viewport.x + this.viewport.width / this.viewport.scale);
        const worldBottom = Math.max(bounds.maxY, this.viewport.y + this.viewport.height / this.viewport.scale);

        const worldW = worldRight - worldLeft + padding * 2;
        const worldH = worldBottom - worldTop + padding * 2;

        const scaleX = mapWidth / worldW;
        const scaleY = mapHeight / worldH;
        const miniScale = Math.min(scaleX, scaleY);

        // Offset to center content if aspect ratio differs
        const offsetX = (mapWidth - worldW * miniScale) / 2;
        const offsetY = (mapHeight - worldH * miniScale) / 2;

        return html`
      <div class="map-container" @click=${(e: MouseEvent) => this.handleMapClick(e, worldLeft, worldTop, miniScale, offsetX, offsetY)}>
        ${this.nodes.map(node => {
            const x = (node.position.x - worldLeft + padding) * miniScale + offsetX;
            const y = (node.position.y - worldTop + padding) * miniScale + offsetY;
            const w = 150 * miniScale; // Approximate node size
            const h = 80 * miniScale;

            return html`
            <div 
              class="mini-node" 
              style="left: ${x}px; top: ${y}px; width: ${w}px; height: ${h}px;"
            ></div>
          `;
        })}
        
        <div 
          class="viewport-rect"
          style="
            left: ${(-this.viewport.x - worldLeft + padding) * miniScale + offsetX}px;
            top: ${(-this.viewport.y - worldTop + padding) * miniScale + offsetY}px;
            width: ${(this.viewport.width / this.viewport.scale) * miniScale}px;
            height: ${(this.viewport.height / this.viewport.scale) * miniScale}px;
          "
        ></div>
      </div>
    `;
    }

    private getBounds() {
        if (this.nodes.length === 0) return { minX: 0, maxX: 1000, minY: 0, maxY: 1000 };

        let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;

        this.nodes.forEach(n => {
            minX = Math.min(minX, n.position.x);
            minY = Math.min(minY, n.position.y);
            maxX = Math.max(maxX, n.position.x + 150);
            maxY = Math.max(maxY, n.position.y + 80);
        });

        return { minX, maxX, minY, maxY };
    }

    private handleMapClick(e: MouseEvent, worldLeft: number, worldTop: number, scale: number, offX: number, offY: number) {
        const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
        const clickX = e.clientX - rect.left;
        const clickY = e.clientY - rect.top;

        // Inverse transform to get world coordinates
        // clickX = (worldX - worldLeft + padding) * scale + offX
        // worldX = ((clickX - offX) / scale) + worldLeft - padding

        const padding = 50;
        const targetCenterX = ((clickX - offX) / scale) + worldLeft - padding;
        const targetCenterY = ((clickY - offY) / scale) + worldTop - padding;

        // Dispatch event to center viewport on these coordinates
        this.dispatchEvent(new CustomEvent('minimap-nav', {
            detail: { x: targetCenterX, y: targetCenterY },
            bubbles: true,
            composed: true
        }));
    }
}

