// Node Palette Component
// Left sidebar with draggable node templates

import { LitElement, html, css } from 'lit';
import { customElement } from 'lit/decorators.js';
import { NODE_TEMPLATES } from '../models/NodeTypes';

@customElement('node-palette')
export class NodePalette extends LitElement {
    static styles = css`
    :host {
      display: block;
      padding: 16px;
      background: white;
      height: 100%;
      overflow-y: auto;
    }

    h3 {
      margin: 0 0 16px 0;
      font-size: 14px;
      font-weight: 600;
      color: #1e293b;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .node-list {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .node-template {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px;
      background: white;
      border: 2px solid #e2e8f0;
      border-radius: 8px;
      cursor: grab;
      transition: all 0.2s;
    }

    .node-template:hover {
      border-color: var(--node-color);
      transform: translateX(4px);
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
    }

    .node-template:active {
      cursor: grabbing;
    }

    .node-icon {
      font-size: 24px;
      flex-shrink: 0;
    }

    .node-info {
      flex: 1;
      min-width: 0;
    }

    .node-name {
      font-size: 13px;
      font-weight: 600;
      color: #1e293b;
      margin-bottom: 2px;
    }

    .node-desc {
      font-size: 11px;
      color: #64748b;
      line-height: 1.3;
    }
  `;

    render() {
        return html`
      <h3>Components</h3>
      <div class="node-list">
        ${NODE_TEMPLATES.map(template => html`
          <div
            class="node-template"
            style="--node-color: ${template.color}"
            draggable="true"
            @dragstart=${(e: DragEvent) => this.handleDragStart(e, template)}
          >
            <span class="node-icon">${template.icon}</span>
            <div class="node-info">
              <div class="node-name">${template.label}</div>
              <div class="node-desc">${template.description}</div>
            </div>
          </div>
        `)}
      </div>
    `;
    }

    private handleDragStart(e: DragEvent, template: any) {
        e.dataTransfer!.effectAllowed = 'copy';
        e.dataTransfer!.setData('application/json', JSON.stringify({
            type: template.type,
            label: template.label
        }));
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'node-palette': NodePalette;
    }
}
