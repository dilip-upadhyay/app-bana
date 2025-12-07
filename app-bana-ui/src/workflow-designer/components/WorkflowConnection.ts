import { LitElement, html, css, svg } from 'lit';
import { customElement, property } from 'lit/decorators.js';

@customElement('workflow-connection')
export class WorkflowConnection extends LitElement {
  @property({ type: Number }) startX = 0;
  @property({ type: Number }) startY = 0;
  @property({ type: Number }) endX = 0;
  @property({ type: Number }) endY = 0;
  @property({ type: String }) type: 'straight' | 'curved' = 'curved';

  static styles = css`
    :host {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      pointer-events: none;
      z-index: 5; /* Below nodes (10) but above background */
    }
  `;

  render() {
    const pathData = this.getPathData();

    return html`
      <svg width="100%" height="100%" style="overflow: visible;">
        <!-- Background thick line for easier selection (future) -->
        <!-- <path d="${pathData}" stroke="transparent" stroke-width="10" fill="none" style="pointer-events: stroke; cursor: pointer;" /> -->
        
        <!-- Visible connection line -->
        <path 
          d="${pathData}" 
          stroke="#94a3b8" 
          stroke-width="2" 
          fill="none"
          marker-end="url(#arrowhead)"
        />
        
        <defs>
          <marker id="arrowhead" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto">
            <polygon points="0 0, 10 3.5, 0 7" fill="#94a3b8" />
          </marker>
        </defs>
      </svg>
    `;
  }

  private getPathData(): string {
    const { startX, startY, endX, endY } = this;

    if (this.type === 'straight') {
      return `M ${startX} ${startY} L ${endX} ${endY}`;
    }

    // Curved (Bezier)
    // Calculate control points for a smooth curve
    const deltaX = Math.abs(endX - startX);
    const controlPointOffset = Math.max(deltaX * 0.5, 50);

    const cp1x = startX + controlPointOffset;
    const cp1y = startY;
    const cp2x = endX - controlPointOffset;
    const cp2y = endY;

    return `M ${startX} ${startY} C ${cp1x} ${cp1y}, ${cp2x} ${cp2y}, ${endX} ${endY}`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'workflow-connection': WorkflowConnection;
  }
}
