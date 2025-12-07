// Workflow Node Component
// Draggable node that represents a workflow step

import { LitElement, html, css } from 'lit';
import { customElement, property } from 'lit/decorators.js';
import { NodeMetadata } from '../models/WorkflowMetadata';
import { getNodeIcon, getNodeColor } from '../models/NodeTypes';

@customElement('workflow-node')
export class WorkflowNode extends LitElement {
    @property({ type: Object }) metadata!: NodeMetadata;
    @property({ type: Boolean }) selected = false;

    static styles = css`
    :host {
      display: block;
      position: absolute;
      cursor: move;
      user-select: none;
    }

    .node {
      background: white;
      border: 2px solid var(--node-color);
      border-radius: 8px;
      padding: 12px 16px;
      min-width: 150px;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
      transition: all 0.2s;
    }

    .node:hover {
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
      transform: translateY(-2px);
    }

    .node.selected {
      border-color: #2563eb;
      box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.2);
    }

    .node-header {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 4px;
    }

    .node-icon {
      font-size: 20px;
    }

    .node-label {
      font-weight: 600;
      font-size: 14px;
      color: #1e293b;
      flex: 1;
    }

    .node-body {
      font-size: 12px;
      color: #64748b;
      margin-top: 4px;
    }

    /* Node type specific styles */
    :host([type="START"]) .node {
      border-radius: 50px;
    }

    :host([type="END"]) .node {
      border-radius: 50px;
    }

    :host([type="DECISION"]) .node {
      transform: rotate(45deg);
    }

    :host([type="DECISION"]) .node-header,
    :host([type="DECISION"]) .node-body {
      transform: rotate(-45deg);
    }

    /* Connection handles */
    .handles {
      position: absolute;
      inset: 0;
      pointer-events: none;
    }

    .handle {
      position: absolute;
      width: 12px;
      height: 12px;
      background: white;
      border: 2px solid var(--node-color);
      border-radius: 50%;
      pointer-events: all;
      cursor: crosshair;
      opacity: 0;
      transition: opacity 0.2s;
    }

    :host(:hover) .handle {
      opacity: 1;
    }

    .handle.top { top: -6px; left: 50%; transform: translateX(-50%); }
    .handle.bottom { bottom: -6px; left: 50%; transform: translateX(-50%); }
    .handle.left { left: -6px; top: 50%; transform: translateY(-50%); }
    .handle.right { right: -6px; top: 50%; transform: translateY(-50%); }

    .handle:hover {
      background: var(--node-color);
      transform: scale(1.3) translate(-25%, -25%);
    }
  `;

    render() {
        const nodeColor = getNodeColor(this.metadata.type);
        const style = `
      left: ${this.metadata.position.x}px;
      top: ${this.metadata.position.y}px;
      --node-color: ${nodeColor};
    `;

        return html`
      <div 
        class="node ${this.selected ? 'selected' : ''}"
        style=${style}
        draggable="true"
        @dragstart=${this.handleDragStart}
      >
        <div class="node-header">
          <span class="node-icon">${getNodeIcon(this.metadata.type)}</span>
          <span class="node-label">${this.metadata.label}</span>
        </div>
        
        ${this.renderNodeBody()}

        <!-- Connection handles -->
        <div class="handles">
          <div class="handle top" @mousedown=${this.handleConnectionStart}></div>
          <div class="handle bottom" @mousedown=${this.handleConnectionStart}></div>
          <div class="handle left" @mousedown=${this.handleConnectionStart}></div>
          <div class="handle right" @mousedown=${this.handleConnectionStart}></div>
        </div>
      </div>
    `;
    }

    private renderNodeBody() {
        switch (this.metadata.type) {
            case 'USER_TASK':
                const assignee = this.metadata.properties.assignedUserId ||
                    this.metadata.properties.assignedRole ||
                    'Unassigned';
                return html`
          <div class="node-body">
            👤 ${assignee}
          </div>
        `;

            case 'SERVICE_TASK':
                const action = this.metadata.properties.serviceAction || 'No action';
                return html`
          <div class="node-body">
            ${action}
          </div>
        `;

            default:
                return '';
        }
    }

    private handleDragStart(e: DragEvent) {
        e.stopPropagation();
        e.dataTransfer!.effectAllowed = 'move';
        e.dataTransfer!.setData('node-id', this.metadata.id);

        this.dispatchEvent(new CustomEvent('node-drag-start', {
            detail: { nodeId: this.metadata.id },
            bubbles: true,
            composed: true
        }));
    }

    private handleConnectionStart(e: MouseEvent) {
        e.stopPropagation();
        e.preventDefault();

        this.dispatchEvent(new CustomEvent('connection-start', {
            detail: { nodeId: this.metadata.id, event: e },
            bubbles: true,
            composed: true
        }));
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'workflow-node': WorkflowNode;
    }
}
