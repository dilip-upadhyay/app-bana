// Workflow Connection Component
// SVG line connecting two workflow nodes

import { LitElement, html, svg, css } from 'lit';
import { customElement, property } from 'lit/decorators.js';
import { ConnectionMetadata, NodeMetadata } from '../models/WorkflowMetadata';

@customElement('workflow-connection')
export class WorkflowConnection extends LitElement {
  @property({ type: Object }) metadata!: ConnectionMetadata;
  @property({ type: Array }) nodes: NodeMetadata[] = [];
  @property({ type: Boolean }) selected = false;

  static styles = css`
    :host {
      display: block;
      pointer-events: none;
    }

    svg {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      pointer-events: none;
      overflow: visible;
    }

    path {
      pointer-events: stroke;
      cursor: pointer;
      transition: stroke-width 0.2s;
    }

    path:hover {
      stroke-width: 3;
    }

    path.selected {
      stroke: #2563eb;
      stroke-width: 3;
    }

    .arrow {
      fill: #64748b;
    }

    .arrow.selected {
      fill: #2563eb;
    }
  `;

  render() {
    const sourceNode = this.nodes.find(n => n.id === this.metadata.from);
    const targetNode = this.nodes.find(n => n.id === this.metadata.to);

    if (!sourceNode || !targetNode) {
      return html``;
    }

    // Calculate center points of nodes
    const sourceCenter = {
      x: sourceNode.position.x + 75, // Assuming 150px node width
      y: sourceNode.position.y + 40  // Assuming 80px node height
    };

    const targetCenter = {
      x: targetNode.position.x + 75,
      y: targetNode.position.y + 40
    };

    // Calculate bezier curve control points for smooth connections
    const dx = targetCenter.x - sourceCenter.x;
    const dy = targetCenter.y - sourceCenter.y;
    
    // Control points for horizontal curves
    const controlOffset = Math.abs(dx) * 0.5;
    const control1 = {
      x: sourceCenter.x + controlOffset,
      y: sourceCenter.y
    };
    const control2 = {
      x: targetCenter.x - controlOffset,
      y: targetCenter.y
    };

    const pathD = `M ${sourceCenter.x} ${sourceCenter.y} 
                   C ${control1.x} ${control1.y}, 
                     ${control2.x} ${control2.y}, 
                     ${targetCenter.x} ${targetCenter.y}`;

    // Calculate arrow position (at target)
    const arrowAngle = Math.atan2(dy, dx);
    const arrowSize = 10;

    return html`
      <svg>
        <defs>
          <marker
            id="arrowhead-${this.metadata.id}"
            markerWidth="10"
            markerHeight="10"
            refX="9"
            refY="3"
            orient="auto"
            markerUnits="strokeWidth"
          >
            <path
              d="M0,0 L0,6 L9,3 z"
              class="arrow ${this.selected ? 'selected' : ''}"
            />
          </marker>
        </defs>

        <path
          d="${pathD}"
          fill="none"
          stroke="#64748b"
          stroke-width="2"
          marker-end="url(#arrowhead-${this.metadata.id})"
          class="${this.selected ? 'selected' : ''}"
          @click=${this.handleClick}
        />

        ${this.metadata.label ? this.renderLabel(sourceCenter, targetCenter) : ''}
      </svg>
    `;
  }

  private renderLabel(source: { x: number; y: number }, target: { x: number; y: number }) {
    const midX = (source.x + target.x) / 2;
    const midY = (source.y + target.y) / 2;

    return svg`
      <text
        x="${midX}"
        y="${midY - 10}"
        text-anchor="middle"
        font-size="12"
        fill="#64748b"
        style="pointer-events: none;"
      >
        ${this.metadata.label}
      </text>
    `;
  }

  private handleClick(e: MouseEvent) {
    e.stopPropagation();
    this.dispatchEvent(new CustomEvent('connection-click', {
      detail: { connectionId: this.metadata.id },
      bubbles: true,
      composed: true
    }));
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'workflow-connection': WorkflowConnection;
  }
}
