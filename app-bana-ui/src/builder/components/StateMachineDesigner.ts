/**
 * State Machine Designer
 * Visual editor for creating state machines with states and transitions
 */

import { LitElement, html, css, svg } from 'lit';
import { customElement, state, property } from 'lit/decorators.js';
import type { StateMachine, State, Transition, TransitionCondition, FieldType } from '../../models/workflow';
import { workflowStorage } from '../../services/WorkflowStorage';
import './ConditionBuilder';

@customElement('state-machine-designer')
export class StateMachineDesigner extends LitElement {

  @property({ type: String }) entityName?: string;
  @property({ type: String }) machineId?: string;

  @state() private machine: StateMachine | null = null;
  @state() private selectedState: string | null = null;
  @state() private selectedTransition: string | null = null;
  @state() private editMode: 'state' | 'transition' | null = null;

  // Drag and drop state
  @state() private draggingStateId: string | null = null;
  @state() private dragOffset: { x: number; y: number } = { x: 0, y: 0 };
  @state() private scale: number = 1;
  @state() private panOffset: { x: number; y: number } = { x: 0, y: 0 };
  @state() private isPanning: boolean = false;
  @state() private lastMousePos: { x: number; y: number } = { x: 0, y: 0 };

  static styles = css`
    :host {
      display: flex;
      flex-direction: column;
      height: 100%;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    }

    .designer-container {
      display: flex;
      flex: 1;
      gap: 1.5rem;
      padding: 1.5rem;
      overflow: hidden;
      min-height: 0;
    }

    .canvas-panel {
      flex: 1;
      background: #ffffff;
      border-radius: 16px;
      border: none;
      padding: 0;
      overflow: auto;
      position: relative;
      height: 100%;
      box-sizing: border-box;
      box-shadow: 0 20px 60px rgba(0,0,0,0.15), 0 0 0 1px rgba(0,0,0,0.05);
      background-image: 
        radial-gradient(circle at 1px 1px, rgba(0,0,0,0.03) 1px, transparent 0);
      background-size: 20px 20px;
    }

    .properties-panel {
      width: 380px;
      background: white;
      border-radius: 16px;
      border: none;
      padding: 0;
      display: flex;
      flex-direction: column;
      overflow: hidden;
      height: 100%;
      box-sizing: border-box;
      box-shadow: 0 20px 60px rgba(0,0,0,0.15);
    }

    .toolbar {
      display: flex;
      gap: 0.75rem;
      padding: 1.5rem;
      background: rgba(255,255,255,0.95);
      backdrop-filter: blur(10px);
      border-bottom: 1px solid rgba(0,0,0,0.06);
      align-items: center;
    }

    .toolbar-title {
      font-size: 1.1rem;
      font-weight: 600;
      color: #1e293b;
      margin-right: auto;
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    .btn {
      padding: 0.625rem 1.25rem;
      border: none;
      border-radius: 10px;
      background: white;
      cursor: pointer;
      font-size: 0.875rem;
      font-weight: 500;
      transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
      box-shadow: 0 1px 3px rgba(0,0,0,0.1), 0 0 0 1px rgba(0,0,0,0.05);
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    .btn:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(0,0,0,0.15);
    }

    .btn:active {
      transform: translateY(0);
    }

    .btn-primary {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
    }

    .btn-primary:hover {
      box-shadow: 0 6px 20px rgba(102, 126, 234, 0.5);
    }

    .btn-success {
      background: linear-gradient(135deg, #10b981 0%, #059669 100%);
      color: white;
      box-shadow: 0 4px 12px rgba(16, 185, 129, 0.4);
    }

    .btn-success:hover {
      box-shadow: 0 6px 20px rgba(16, 185, 129, 0.5);
    }

    .state {
      position: absolute;
      padding: 0;
      width: 200px;
      border-radius: 16px;
      background: white;
      cursor: grab;
      box-shadow: 0 8px 24px rgba(0,0,0,0.12), 0 0 0 1px rgba(0,0,0,0.05);
      transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
      user-select: none;
      overflow: hidden;
      z-index: 5;
    }

    .state:active {
      cursor: grabbing;
    }

    .state:hover {
      box-shadow: 0 16px 48px rgba(0,0,0,0.18), 0 0 0 2px rgba(102, 126, 234, 0.3);
      transform: translateY(-4px) scale(1.02);
    }

    .state.selected {
      box-shadow: 0 16px 48px rgba(102, 126, 234, 0.3), 0 0 0 3px #667eea;
      transform: scale(1.05);
    }

    .state-header {
      padding: 1rem 1.25rem;
      color: white;
      font-weight: 600;
      font-size: 1rem;
      display: flex;
      align-items: center;
      justify-content: space-between;
      background: linear-gradient(135deg, var(--state-color, #667eea) 0%, var(--state-color-dark, #764ba2) 100%);
    }

    .state-body {
      padding: 1rem 1.25rem;
      font-size: 0.875rem;
      color: #64748b;
      min-height: 60px;
      background: white;
    }

    .state-actions {
      display: flex;
      gap: 0.5rem;
      margin-top: 0.75rem;
      padding-top: 0.75rem;
      border-top: 1px solid #e2e8f0;
    }

    .state-action-btn {
      padding: 0.375rem 0.75rem;
      border-radius: 8px;
      border: none;
      background: #f1f5f9;
      color: #475569;
      font-size: 0.75rem;
      cursor: pointer;
      transition: all 0.2s;
      font-weight: 500;
    }

    .state-action-btn:hover {
      background: #e2e8f0;
      transform: translateY(-1px);
    }

    .connector-dot {
      width: 16px;
      height: 16px;
      background: white;
      border: 3px solid #667eea;
      border-radius: 50%;
      position: absolute;
      transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
      cursor: crosshair;
      z-index: 10;
      box-shadow: 0 2px 8px rgba(0,0,0,0.15);
    }

    .connector-dot:hover {
      background: #667eea;
      border-color: #667eea;
      transform: scale(1.4);
      box-shadow: 0 4px 16px rgba(102, 126, 234, 0.4);
    }

    .connector-dot.active {
      background: #10b981;
      border-color: #10b981;
      animation: pulse 1s infinite;
    }

    @keyframes pulse {
      0%, 100% { transform: scale(1); }
      50% { transform: scale(1.3); }
    }

    .transitions-list {
      margin-top: 0;
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
      padding: 1.25rem;
      overflow-y: auto;
      flex: 1;
    }

    .transition-item {
      padding: 1rem;
      background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
      border-radius: 12px;
      border: 2px solid transparent;
      cursor: pointer;
      font-size: 0.875rem;
      transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
      position: relative;
      overflow: hidden;
    }

    .transition-item::before {
      content: '';
      position: absolute;
      left: 0;
      top: 0;
      bottom: 0;
      width: 4px;
      background: linear-gradient(180deg, #667eea 0%, #764ba2 100%);
      opacity: 0;
      transition: opacity 0.2s;
    }

    .transition-item:hover {
      background: white;
      border-color: #e2e8f0;
      transform: translateX(4px);
      box-shadow: 0 4px 12px rgba(0,0,0,0.08);
    }

    .transition-item:hover::before {
      opacity: 1;
    }

    .transition-item.selected {
      background: linear-gradient(135deg, #eef2ff 0%, #e0e7ff 100%);
      border-color: #667eea;
      box-shadow: 0 4px 16px rgba(102, 126, 234, 0.2);
    }

    .transition-item.selected::before {
      opacity: 1;
    }

    .transition-label {
      font-weight: 600;
      color: #1e293b;
      margin-bottom: 0.5rem;
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    .transition-path {
      font-size: 0.75rem;
      color: #64748b;
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    .transition-badge {
      display: inline-flex;
      align-items: center;
      gap: 0.25rem;
      padding: 0.25rem 0.5rem;
      border-radius: 6px;
      font-size: 0.7rem;
      font-weight: 500;
      margin-top: 0.5rem;
    }

    .badge-condition {
      background: #dbeafe;
      color: #1e40af;
    }

    .badge-role {
      background: #fef3c7;
      color: #92400e;
    }

    .badge-fallback {
      background: #f3e8ff;
      color: #6b21a8;
    }

    .badge-priority {
      background: #fee2e2;
      color: #991b1b;
    }

    .form-group {
      display: flex;
      flex-direction: column;
      gap: 0.625rem;
    }

    label {
      font-weight: 600;
      font-size: 0.875rem;
      color: #1e293b;
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    input,
    select {
      border-radius: 10px;
      border: 2px solid #e2e8f0;
      padding: 0.75rem 1rem;
      font-size: 0.875rem;
      transition: all 0.2s;
      background: white;
      color: #1e293b;
    }

    input:focus,
    select:focus {
      outline: none;
      border-color: #667eea;
      box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
    }

    input[type="checkbox"] {
      width: 18px;
      height: 18px;
      cursor: pointer;
      accent-color: #667eea;
    }

    small {
      color: #64748b;
      font-size: 0.75rem;
      line-height: 1.4;
    }

    .panel-header {
      padding: 1.25rem;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      font-weight: 600;
      font-size: 1.1rem;
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }

    .panel-content {
      padding: 1.25rem;
      overflow-y: auto;
      flex: 1;
    }

    .empty-state {
      text-align: center;
      padding: 4rem 2rem;
      color: #94a3b8;
    }

    .empty-state-icon {
      font-size: 3rem;
      margin-bottom: 1rem;
      opacity: 0.5;
    }

    .btn-delete {
      background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);
      color: white;
      margin-top: auto;
      box-shadow: 0 4px 12px rgba(239, 68, 68, 0.4);
    }

    .btn-delete:hover {
      box-shadow: 0 6px 20px rgba(239, 68, 68, 0.5);
    }
  `;

  async connectedCallback() {
    super.connectedCallback();
    await this.loadStateMachine();
  }

  private async loadStateMachine() {
    if (this.machineId) {
      this.machine = await workflowStorage.getStateMachine(this.machineId);
    } else if (this.entityName) {
      this.machine = await workflowStorage.getStateMachineByEntity(this.entityName);
    }

    // Create default machine if none exists
    if (!this.machine && this.entityName) {
      this.machine = {
        id: this.generateId(),
        name: `${this.entityName} Workflow`,
        entityName: this.entityName,
        states: [
          { id: this.generateId(), name: 'Draft', color: '#6b7280' },
          { id: this.generateId(), name: 'Submitted', color: '#3b82f6' },
          { id: this.generateId(), name: 'Approved', color: '#10b981' },
          { id: this.generateId(), name: 'Rejected', color: '#ef4444' }
        ],
        transitions: [],
        initialState: ''
      };

      // Set initial state to first state
      if (this.machine.states.length > 0) {
        this.machine.initialState = this.machine.states[0].id;
      }
    }
  }

  private generateId(): string {
    return `${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }

  private async saveMachine() {
    if (!this.machine) return;

    await workflowStorage.saveStateMachine(this.machine);
    this.dispatchEvent(new CustomEvent('machine-saved', {
      detail: { machine: this.machine },
      bubbles: true,
      composed: true
    }));
  }

  private addState() {
    if (!this.machine) return;

    const newState: State = {
      id: this.generateId(),
      name: `State ${this.machine.states.length + 1}`,
      color: '#6366f1'
    };

    this.machine = {
      ...this.machine,
      states: [...this.machine.states, newState]
    };

    this.selectedState = newState.id;
    this.editMode = 'state';
  }

  private deleteState(stateId: string) {
    if (!this.machine) return;

    // Remove state
    this.machine = {
      ...this.machine,
      states: this.machine.states.filter(s => s.id !== stateId),
      // Remove transitions involving this state
      transitions: this.machine.transitions.filter(
        t => t.from !== stateId && t.to !== stateId
      )
    };

    if (this.selectedState === stateId) {
      this.selectedState = null;
    }
  }

  private addTransition() {
    if (!this.machine || this.machine.states.length < 2) return;

    const newTransition: Transition = {
      id: this.generateId(),
      from: this.machine.states[0].id,
      to: this.machine.states[1].id,
      label: 'Transition',
      roles: []
    };

    this.machine = {
      ...this.machine,
      transitions: [...this.machine.transitions, newTransition]
    };

    this.selectedTransition = newTransition.id;
    this.editMode = 'transition';
  }

  private deleteTransition(transitionId: string) {
    if (!this.machine) return;

    this.machine = {
      ...this.machine,
      transitions: this.machine.transitions.filter(t => t.id !== transitionId)
    };

    if (this.selectedTransition === transitionId) {
      this.selectedTransition = null;
    }
  }

  private updateStateName(stateId: string, name: string) {
    if (!this.machine) return;

    this.machine = {
      ...this.machine,
      states: this.machine.states.map(s =>
        s.id === stateId ? { ...s, name } : s
      )
    };
  }

  private updateStateColor(stateId: string, color: string) {
    if (!this.machine) return;

    this.machine = {
      ...this.machine,
      states: this.machine.states.map(s =>
        s.id === stateId ? { ...s, color } : s
      )
    };
  }

  private updateTransition(transitionId: string, updates: Partial<Transition>) {
    if (!this.machine) return;

    this.machine = {
      ...this.machine,
      transitions: this.machine.transitions.map(t =>
        t.id === transitionId ? { ...t, ...updates } : t
      )
    };
  }

  private renderToolbar() {
    return html`
      <div class="toolbar">
        <div class="toolbar-title">
          <span>🎨</span>
          <span>Workflow Designer</span>
        </div>
        <button class="btn btn-primary" @click=${this.addState}>
          ➕ Add State
        </button>
        <button 
          class="btn" 
          @click=${this.addTransition}
          ?disabled=${!this.machine || this.machine.states.length < 2}
        >
          🔀 Add Transition
        </button>
        <button class="btn btn-success" @click=${this.saveMachine}>
          💾 Save Workflow
        </button>
      </div>
    `;
  }

  private handleMouseDown(e: MouseEvent, stateId: string) {
    e.stopPropagation();
    this.draggingStateId = stateId;
    const state = this.machine?.states.find(s => s.id === stateId);
    if (state && state.position) {
      this.dragOffset = {
        x: e.clientX - state.position.x,
        y: e.clientY - state.position.y
      };
    } else {
      // Fallback if no position yet
      const el = (e.target as HTMLElement).closest('.state') as HTMLElement;
      const rect = el.getBoundingClientRect();
      this.dragOffset = {
        x: e.clientX - rect.left,
        y: e.clientY - rect.top
      };
    }

    this.selectedState = stateId;
    this.selectedTransition = null;
    this.editMode = 'state';
  }

  private handleMouseMove(e: MouseEvent) {
    if (this.draggingStateId && this.machine) {
      const x = e.clientX - this.dragOffset.x;
      const y = e.clientY - this.dragOffset.y;

      this.machine = {
        ...this.machine,
        states: this.machine.states.map(s =>
          s.id === this.draggingStateId
            ? { ...s, position: { x, y } }
            : s
        )
      };
    }
  }

  private handleMouseUp() {
    this.draggingStateId = null;
  }

  private renderCanvas() {
    if (!this.machine || this.machine.states.length === 0) {
      return html`
        <div class="empty-state">
          <p>No states defined</p>
          <p>Click "Add State" to get started</p>
        </div>
      `;
    }

    // Ensure all states have positions and calculate bounds
    let maxX = 0;
    let maxY = 0;

    this.machine.states.forEach((state, index) => {
      if (!state.position) {
        state.position = {
          x: 100 + (index % 4) * 250,
          y: 100 + Math.floor(index / 4) * 200
        };
      }

      // Update bounds (assuming card width ~200px, height ~150px)
      maxX = Math.max(maxX, state.position.x + 250);
      maxY = Math.max(maxY, state.position.y + 200);
    });

    // Add some padding
    const containerWidth = Math.max(100, maxX + 100);
    const containerHeight = Math.max(100, maxY + 100);

    return html`
      <div 
        style="
          min-width: 100%; 
          min-height: 100%; 
          width: ${containerWidth}px;
          height: ${containerHeight}px;
          position: relative; 
          overflow: visible;
        "
        @mousemove=${this.handleMouseMove}
        @mouseup=${this.handleMouseUp}
        @mouseleave=${this.handleMouseUp}
      >
        <!-- SVG layer for transitions (arrows) -->
        <svg 
          width="${containerWidth}" 
          height="${containerHeight}"
          viewBox="0 0 ${containerWidth} ${containerHeight}"
          style="position: absolute; top: 0; left: 0; z-index: 10; pointer-events: auto;"
          xmlns="http://www.w3.org/2000/svg"
        >
          <defs>
            <marker
              id="arrowhead"
              markerWidth="12"
              markerHeight="12"
              refX="10"
              refY="6"
              orient="auto"
            >
              <path d="M0,0 L12,6 L0,12 L3,6 Z" fill="#64748b" />
            </marker>
            <marker
              id="arrowhead-selected"
              markerWidth="12"
              markerHeight="12"
              refX="10"
              refY="6"
              orient="auto"
            >
              <path d="M0,0 L12,6 L0,12 L3,6 Z" fill="#3b82f6" />
            </marker>
          </defs>
          
          ${this.machine.transitions.map(transition => {
      const fromState = this.machine!.states.find(s => s.id === transition.from);
      const toState = this.machine!.states.find(s => s.id === transition.to);

      if (!fromState || !toState || !fromState.position || !toState.position) {
        return svg``;
      }

      const startX = fromState.position.x + 180; // Right side of source state
      const startY = fromState.position.y + 60;  // Middle height of state card
      const endX = toState.position.x;           // Left side of target state
      const endY = toState.position.y + 60;      // Middle height of state card

      // Bezier curve control points
      const dist = Math.abs(endX - startX);
      const controlDist = Math.max(dist * 0.5, 50);

      const cp1x = startX + controlDist;
      const cp1y = startY;
      const cp2x = endX - controlDist;
      const cp2y = endY;

      const pathData = `M ${startX},${startY} C ${cp1x},${cp1y} ${cp2x},${cp2y} ${endX},${endY}`;
      const isSelected = this.selectedTransition === transition.id;

      return svg`
              <g 
                class="transition-group"
                @click=${(e: Event) => {
          e.stopPropagation();
          this.selectedTransition = transition.id;
          this.selectedState = null;
          this.editMode = 'transition';
          this.requestUpdate();
        }}
              >
                <!-- Invisible wider path for easier clicking -->
                <path
                  d="${pathData}"
                  stroke="rgba(0,0,0,0.1)"
                  stroke-width="20"
                  fill="none"
                  style="cursor: pointer; pointer-events: stroke;"
                />
                
                <!-- Visible path with arrow -->
                <path
                  d="${pathData}"
                  stroke="${isSelected ? '#3b82f6' : '#64748b'}"
                  stroke-width="${isSelected ? '4' : '3'}"
                  fill="none"
                  marker-end="url(#arrowhead${isSelected ? '-selected' : ''})"
                  style="cursor: pointer; pointer-events: stroke;"
                />

                <!-- Label on path -->
                ${transition.label ? html`
                  <foreignObject 
                    x="${(startX + endX) / 2 - 50}" 
                    y="${(startY + endY) / 2 - 15}" 
                    width="100" 
                    height="30"
                    style="pointer-events: none;"
                  >
                    <div style="text-align: center;">
                      <span style="
                        background: white; 
                        padding: 2px 8px; 
                        border-radius: 12px; 
                        border: 1px solid ${isSelected ? '#3b82f6' : '#e2e8f0'};
                        font-size: 11px;
                        color: ${isSelected ? '#3b82f6' : '#64748b'};
                        box-shadow: 0 1px 2px rgba(0,0,0,0.05);
                      ">
                        ${transition.label}
                      </span>
                    </div>
                  </foreignObject>
                ` : ''}
              </g>
            `;
    })}
        </svg>

        <!-- State nodes -->
        ${this.machine.states.map((state) => {
      if (!state.position) return '';

      return html`
            <div
              class="state ${this.selectedState === state.id ? 'selected' : ''}"
              style="
                left: ${state.position.x}px; 
                top: ${state.position.y}px;
              "
              @mousedown=${(e: MouseEvent) => this.handleMouseDown(e, state.id)}
            >
              <div class="state-header" style="background: ${state.color || '#6366f1'}">
                <span>${state.name}</span>
                ${this.machine!.initialState === state.id ? '⭐' : ''}
              </div>
              <div class="state-body">
                ${state.description || 'No description'}
                <div class="state-actions">
                  <!-- Future: Add quick actions here -->
                </div>
              </div>
              
              <!-- Connectors -->
              <div class="connector-dot" style="top: 50%; right: -6px; transform: translateY(-50%);"></div>
              <div class="connector-dot" style="top: 50%; left: -6px; transform: translateY(-50%);"></div>
            </div>
          `;
    })}
      </div>
    `;
  }

  /**
   * Get available fields for condition building
   * TODO: Pull from actual entity schema
   */
  private getAvailableFields(): Array<{ name: string; type: FieldType; label: string }> {
    // For now, return sample fields for Appointment entity
    // In production, this would query the entity schema
    return [
      { name: 'amount', type: 'number', label: 'Amount' },
      { name: 'status', type: 'string', label: 'Status' },
      { name: 'customerType', type: 'string', label: 'Customer Type' },
      { name: 'priority', type: 'string', label: 'Priority' },
      { name: 'createdDate', type: 'date', label: 'Created Date' },
      { name: 'assignedTo', type: 'string', label: 'Assigned To' },
      { name: 'isUrgent', type: 'boolean', label: 'Is Urgent' },
    ];
  }

  private handleConditionChange(transitionId: string, condition: TransitionCondition) {
    this.updateTransition(transitionId, { condition });
  }

  private renderPropertiesPanel() {
    const selectedState = this.machine?.states.find(s => s.id === this.selectedState);
    const selectedTrans = this.machine?.transitions.find(t => t.id === this.selectedTransition);

    if (this.editMode === 'state' && selectedState) {
      return html`
        <div class="panel-header">
          <span>⚙️</span>
          <span>State Properties</span>
        </div>
        <div class="panel-content">
          <div class="form-group">
            <label>State Name</label>
            <input
              type="text"
              .value=${selectedState.name}
              @input=${(e: Event) =>
          this.updateStateName(selectedState.id, (e.target as HTMLInputElement).value)
        }
            />
          </div>

          <div class="form-group">
            <label>Color Theme</label>
            <input
              type="color"
              .value=${selectedState.color || '#6366f1'}
              @input=${(e: Event) =>
          this.updateStateColor(selectedState.id, (e.target as HTMLInputElement).value)
        }
            />
          </div>

          <div class="form-group">
            <label style="cursor: pointer;">
              <input
                type="checkbox"
                .checked=${this.machine!.initialState === selectedState.id}
                @change=${(e: Event) => {
          if ((e.target as HTMLInputElement).checked) {
            this.machine = {
              ...this.machine!,
              initialState: selectedState.id
            };
          }
        }}
              />
              <span>Set as Initial State ⭐</span>
            </label>
            <small>The workflow starts from this state</small>
          </div>

          <button 
            class="btn btn-delete"
            @click=${() => this.deleteState(selectedState.id)}
          >
            🗑️ Delete State
          </button>
        </div>
      `;
    }

    if (this.editMode === 'transition' && selectedTrans) {
      const fromState = this.machine!.states.find(s => s.id === selectedTrans.from);
      const toState = this.machine!.states.find(s => s.id === selectedTrans.to);

      return html`
        <div class="panel-header">
          <span>🔀</span>
          <span>Transition Properties</span>
        </div>
        <div class="panel-content">
          <div style="padding: 1rem; background: linear-gradient(135deg, #eef2ff 0%, #e0e7ff 100%); border-radius: 12px; margin-bottom: 1.5rem; border: 2px solid #c7d2fe;">
            <div class="transition-path">
              <strong style="color: #4338ca;">${fromState?.name || 'Unknown'}</strong> 
              <span style="color: #667eea; font-size: 1.2rem;">→</span> 
              <strong style="color: #4338ca;">${toState?.name || 'Unknown'}</strong>
            </div>
          </div>
          
          <div class="form-group">
            <label>From State</label>
            <select
              .value=${selectedTrans.from}
              @change=${(e: Event) =>
          this.updateTransition(selectedTrans.id, {
            from: (e.target as HTMLSelectElement).value
          })
        }
            >
              ${this.machine!.states.map(s => html`
                <option value=${s.id} ?selected=${s.id === selectedTrans.from}>
                  ${s.name}
                </option>
              `)}
            </select>
          </div>

          <div class="form-group">
            <label>To State</label>
            <select
              .value=${selectedTrans.to}
              @change=${(e: Event) =>
          this.updateTransition(selectedTrans.id, {
            to: (e.target as HTMLSelectElement).value
          })
        }
            >
              ${this.machine!.states.map(s => html`
                <option value=${s.id} ?selected=${s.id === selectedTrans.to}>
                  ${s.name}
                </option>
              `)}
            </select>
          </div>

          <div class="form-group">
            <label>Label</label>
            <input
              type="text"
              .value=${selectedTrans.label || ''}
              placeholder="e.g., Submit, Approve, Reject"
              @input=${(e: Event) =>
          this.updateTransition(selectedTrans.id, {
            label: (e.target as HTMLInputElement).value
          })
        }
            />
            <small>This label appears on the arrow</small>
          </div>

          <div class="form-group">
            <label>Priority</label>
            <input
              type="number"
              .value=${selectedTrans.priority || ''}
              placeholder="1 = highest priority"
              min="1"
              @input=${(e: Event) => {
          const value = (e.target as HTMLInputElement).value;
          this.updateTransition(selectedTrans.id, {
            priority: value ? parseInt(value) : undefined
          });
        }}
            />
            <small>💡 Lower numbers = higher priority when evaluating conditions</small>
          </div>

          <div class="form-group">
            <label>Allowed Roles</label>
            <input
              type="text"
              .value=${(selectedTrans.roles || []).join(', ')}
              placeholder="admin, manager, user"
              @input=${(e: Event) => {
          const value = (e.target as HTMLInputElement).value;
          const roles = value.split(',').map(r => r.trim()).filter(r => r);
          this.updateTransition(selectedTrans.id, { roles });
        }}
            />
            <small>🔒 Only these roles can trigger this transition</small>
          </div>

          <div class="form-group">
            <label style="display: flex; justify-content: space-between; align-items: center;">
              <span>Condition</span>
              ${selectedTrans.condition ? html`
                <span class="transition-badge badge-condition">✓ Active</span>
              ` : ''}
            </label>
            <condition-builder
              .condition=${selectedTrans.condition}
              .availableFields=${this.getAvailableFields()}
              .compact=${false}
              @condition-changed=${(e: CustomEvent) =>
          this.handleConditionChange(selectedTrans.id, e.detail)
        }
            ></condition-builder>
            <small>⚡ This transition only fires when the condition is true</small>
          </div>

          <div class="form-group">
            <label style="cursor: pointer;">
              <input
                type="checkbox"
                .checked=${selectedTrans.isFallback || false}
                @change=${(e: Event) => {
          this.updateTransition(selectedTrans.id, {
            isFallback: (e.target as HTMLInputElement).checked
          });
        }}
              />
              <span>Fallback Transition (ELSE) 🔄</span>
            </label>
            <small>Use this transition if no other conditions match</small>
          </div>

          <button 
            class="btn btn-delete"
            @click=${() => this.deleteTransition(selectedTrans.id)}
          >
            🗑️ Delete Transition
          </button>
        </div>
      `;
    }

    // Default: Show all transitions
    return html`
      <div class="panel-header">
        <span>📋</span>
        <span>Transitions (${this.machine?.transitions.length || 0})</span>
      </div>
      
      ${!this.machine || this.machine.transitions.length === 0 ? html`
        <div class="empty-state">
          <div class="empty-state-icon">🔀</div>
          <p style="font-weight: 600; margin-bottom: 0.5rem;">No transitions yet</p>
          <p style="font-size: 0.875rem;">Click "Add Transition" to connect your states</p>
        </div>
      ` : html`
        <div class="transitions-list">
          ${this.machine!.transitions.map(t => {
      const fromState = this.machine!.states.find(s => s.id === t.from);
      const toState = this.machine!.states.find(s => s.id === t.to);

      return html`
              <div 
                class="transition-item ${this.selectedTransition === t.id ? 'selected' : ''}"
                @click=${() => {
          this.selectedTransition = t.id;
          this.selectedState = null;
          this.editMode = 'transition';
        }}
              >
                <div class="transition-label">
                  <span>${t.label || 'Untitled Transition'}</span>
                  ${t.priority ? html`
                    <span class="transition-badge badge-priority">P${t.priority}</span>
                  ` : ''}
                </div>
                <div class="transition-path">
                  <span>${fromState?.name || 'Unknown'}</span>
                  <span style="color: #667eea;">→</span>
                  <span>${toState?.name || 'Unknown'}</span>
                </div>
                ${t.condition ? html`
                  <div class="transition-badge badge-condition">
                    ⚡ ${t.condition.naturalLanguage || t.condition.expression}
                  </div>
                ` : ''}
                ${t.isFallback ? html`
                  <div class="transition-badge badge-fallback">
                    🔄 ELSE (Fallback)
                  </div>
                ` : ''}
                ${t.roles && t.roles.length > 0 ? html`
                  <div class="transition-badge badge-role">
                    🔒 ${t.roles.join(', ')}
                  </div>
                ` : ''}
              </div>
            `;
    })}
        </div>
      `}
    `;
  }

  render() {
    return html`
      ${this.renderToolbar()}
      <div class="designer-container">
        <div class="canvas-panel">
          ${this.renderCanvas()}
        </div>
        <div class="properties-panel">
          ${this.renderPropertiesPanel()}
        </div>
      </div>
    `;
  }
}
