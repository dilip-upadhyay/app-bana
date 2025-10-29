import { LitElement, html, css } from 'lit';
import { customElement, property } from 'lit/decorators.js';

/**
 * Enhanced Grid Element for Studio Builder
 * Provides a visual grid with configurable rows/cols and drop zones in each cell
 */
@customElement('app-grid')
export class GridElement extends LitElement {
  @property({ type: Number }) rows = 2;
  @property({ type: Number }) cols = 3;
  @property({ type: String }) gap = '1rem';
  @property({ type: String }) minCellWidth = '150px';
  @property({ type: String }) minCellHeight = '100px';

  static styles = css`
    :host {
      display: block;
      width: 100%;
    }

    .grid-container {
      display: grid;
      width: 100%;
      position: relative;
    }

    .grid-cell {
      min-height: var(--min-cell-height, 100px);
      border: 2px dashed #d1d5db;
      border-radius: 4px;
      padding: 0.5rem;
      background: #f9fafb;
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      position: relative;
      transition: all 0.2s ease;
    }

    .grid-cell:hover {
      border-color: #6366f1;
      background: #eef2ff;
    }

    .grid-cell.drag-over {
      border-color: #10b981;
      background: #d1fae5;
      border-style: solid;
    }

    .grid-cell.has-content {
      border-style: solid;
      border-color: #9ca3af;
      background: white;
    }

    .grid-cell.has-content:hover {
      border-color: #6366f1;
    }

    .cell-label {
      position: absolute;
      top: 2px;
      left: 4px;
      font-size: 10px;
      color: #9ca3af;
      font-weight: 600;
      pointer-events: none;
      z-index: 1;
    }

    .cell-content {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      min-height: 60px;
    }

    .empty-hint {
      color: #9ca3af;
      font-size: 12px;
      text-align: center;
      margin: auto;
      pointer-events: none;
    }

    .grid-cell-slot::slotted(*) {
      width: 100%;
    }
  `;

  render() {
    const cells = [];
    const gridTemplateColumns = `repeat(${this.cols}, minmax(${this.minCellWidth}, 1fr))`;

    for (let row = 0; row < this.rows; row++) {
      for (let col = 0; col < this.cols; col++) {
        const cellIndex = row * this.cols + col;
        cells.push(html`
          <div
            class="grid-cell"
            data-cell="${cellIndex}"
            data-row="${row}"
            data-col="${col}"
          >
            <span class="cell-label">R${row + 1}C${col + 1}</span>
            <div class="cell-content">
              <slot name="cell-${cellIndex}">
                <div class="empty-hint">Drop here</div>
              </slot>
            </div>
          </div>
        `);
      }
    }

    return html`
      <div
        class="grid-container"
        style="grid-template-columns: ${gridTemplateColumns}; gap: ${this.gap}; --min-cell-height: ${this.minCellHeight};"
      >
        ${cells}
      </div>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'app-grid': GridElement;
  }
}

