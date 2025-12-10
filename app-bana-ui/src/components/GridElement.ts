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
  @property({ type: String }) minCellWidth = 'auto';
  @property({ type: String }) minCellHeight = 'auto';

  static styles = css`
    :host {
      display: block;
      width: 100%;
    }

    .grid-container {
      display: grid;
      width: 100%;
      position: relative;
      align-items: start; /* align-items: start allows cells to be only as tall as their content */
    }

    .grid-cell {
      min-height: var(--min-cell-height, auto);
      /* Remove default styling for filled cells */
      border: 1px solid transparent; 
      border-radius: 4px;
      padding: 0; /* Remove padding so input fits tight */
      background: transparent;
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      position: relative;
      transition: all 0.2s ease;
    }

    .grid-cell.empty {
       /* Style only empty cells to look like drop targets */
       min-height: 60px; /* Minimum height for drop target visibility */
       border: 2px dashed #e5e7eb;
       background: #f9fafb;
       padding: 0.5rem;
    }

    .grid-cell:hover {
      /* Minimal hover effect for filled cells */
      border-color: transparent; 
    }
    
    .grid-cell.empty:hover {
       border-color: #6366f1;
       background: #eef2ff;
    }

    .grid-cell.drag-over {
      border-color: #10b981 !important;
      background: #d1fae5 !important;
      border-style: solid !important;
      min-height: 60px;
    }

    .grid-cell.has-content {
      border: none;
      background: transparent;
    }

    .grid-cell.has-content:hover {
      /* Show subtle outline on hover for selection feedback if needed, else transparent */
      border: 1px dashed #d1d5db;
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
      /* removed min-height to allow auto-sizing */
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
      /* Force override legacy inline styles from old grids */
      min-height: auto !important; 
      height: auto !important;
      border: none !important; /* Remove double borders from legacy containers */
      background: transparent !important; /* Remove legacy background */
      box-shadow: none !important;
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
            class="grid-cell empty"
            data-cell="${cellIndex}"
            data-row="${row}"
            data-col="${col}"
          >
            <span class="cell-label">R${row + 1}C${col + 1}</span>
            <div class="cell-content">
              <slot name="cell-${cellIndex}" @slotchange=${this.handleSlotChange}>
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

  handleSlotChange(e: Event) {
    const slot = e.target as HTMLSlotElement;
    const cell = slot.closest('.grid-cell');
    if (cell) {
      const assignedNodes = slot.assignedNodes({ flatten: true });
      // Filter out empty text nodes to prevent false positives
      const hasContent = assignedNodes.some(n =>
        n.nodeType === Node.ELEMENT_NODE || (n.nodeType === Node.TEXT_NODE && n.textContent?.trim())
      );

      if (hasContent) {
        cell.classList.remove('empty');
        cell.classList.add('has-content');
      } else {
        cell.classList.add('empty');
        cell.classList.remove('has-content');
      }
    }
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'app-grid': GridElement;
  }
}

