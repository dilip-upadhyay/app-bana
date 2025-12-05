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
      background: #f8f9fa;
    }

    .designer-container {
      display: flex;
      flex: 1;
      gap: 1rem;
      padding: 1rem;
      overflow: hidden;
      min-height: 0; /* Important for nested flex scrolling */
    }

    .canvas-panel {
      flex: 1;
      background: white;
      border-radius: 8px;
      border: 1px solid #e2e8f0;
      padding: 2rem;
      overflow: auto;
      position: relative;
      height: 100%; /* Ensure it fills container */
      box-sizing: border-box;
    }

    .properties-panel {
      width: 300px;
      background: white;
      border-radius: 8px;
      border: 1px solid #e2e8f0;
      padding: 1rem;
      display: flex;
      flex-direction: column;
      gap: 1rem;
      overflow-y: auto; /* Enable scrolling for long lists */
      height: 100%; /* Ensure it fills container */
      box-sizing: border-box;
    }

    .toolbar {
      display: flex;
      gap: 0.5rem;
      padding: 1rem;
      background: white;
      border-bottom: 1px solid #e2e8f0;
    }

    .btn {
      padding: 0.5rem 1rem;
      border: 1px solid #cbd5e0;
      border-radius: 4px;
      background: white;
      cursor: pointer;
      font-size: 0.875rem;
      transition: all 0.2s;
    }

    .btn:hover {
      background: #f7fafc;
      border-color: #4299e1;
    }

    .btn-primary {
      background: #3b82f6;
      color: white;
      border-color: #3b82f6;
    }

    .btn-primary:hover {
      background: #2563eb;
    }

    .state {
      position: absolute;
      padding: 0;
      width: 180px;
      border-radius: 12px;
      background: white;
      cursor: grab;
      box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
      transition: box-shadow 0.2s, transform 0.2s;
      user-select: none;
      border: 1px solid rgba(0,0,0,0.05);
      overflow: hidden;
      z-index: 5;
    }

    .state:active {
      cursor: grabbing;
    }

    .state:hover {
      box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05);
      transform: translateY(-2px);
    }

    .state.selected {
      box-shadow: 0 0 0 2px #3b82f6, 0 10px 15px -3px rgba(0, 0, 0, 0.1);
    }

    .state-header {
      padding: 0.75rem 1rem;
      color: white;
      font-weight: 600;
      font-size: 0.95rem;
      display: flex;
      align-items: center;
      justify-content: space-between;
    }

    .state-body {
      padding: 0.75rem 1rem;
      font-size: 0.8rem;
      color: #475569;
    }

    .state-actions {
      display: flex;
      gap: 0.5rem;
      margin-top: 0.5rem;
    }

    .connector-dot {
      width: 12px;
      height: 12px;
      background: white;
      border: 2px solid #94a3b8;
      border-radius: 50%;
      position: absolute;
      transition: all 0.2s;
    }

    .connector-dot:hover {
      background: #3b82f6;
      border-color: #3b82f6;
      transform: scale(1.2);
    }

    .transitions-list {
      margin-top: 1rem;
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }

    .transition-item {
      padding: 0.75rem;
      background: #f8fafc;
      border-radius: 6px;
      border: 1px solid #e2e8f0;
      cursor: pointer;
      font-size: 0.85rem;
    }

    .transition-item:hover {
      background: #edf2f7;
      border-color: #cbd5e0;
    }

    .transition-item.selected {
      background: #dbeafe;
      border-color: #3b82f6;
    }

    .form-group {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }

    .form-group label {
      font-size: 0.85rem;
      font-weight: 500;
      color: #475569;
    }

    .form-group input,
    .form-group select {
      padding: 0.5rem;
      border: 1px solid #cbd5e0;
      border-radius: 4px;
      font-size: 0.875rem;
    }

    .empty-state {
      text-align: center;
      padding: 3rem;
      color: #94a3b8;
    }

    .state-count {
      font-size: 0.75rem;
      color: rgba(255, 255, 255, 0.8);
      margin-top: 0.25rem;
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
        <button class="btn btn-primary" @click=${this.saveMachine}>
          💾 Save
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
        <h3 style="margin: 0 0 1rem;">State Properties</h3>
        
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
          <label>Color</label>
          <input
            type="color"
            .value=${selectedState.color || '#6366f1'}
            @input=${(e: Event) =>
          this.updateStateColor(selectedState.id, (e.target as HTMLInputElement).value)
        }
          />
        </div>

        <div class="form-group">
          <label>
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
            Set as Initial State
          </label>
        </div>

        <button 
          class="btn" 
          style="background: #ef4444; color: white; margin-top: auto;"
          @click=${() => this.deleteState(selectedState.id)}
        >
          🗑️ Delete State
        </button>
      `;
    }

    if (this.editMode === 'transition' && selectedTrans) {
      const fromState = this.machine!.states.find(s => s.id === selectedTrans.from);
      const toState = this.machine!.states.find(s => s.id === selectedTrans.to);

      return html`
        <h3 style="margin: 0 0 1rem;">Transition Properties</h3>
        
        <div style="padding: 0.75rem; background: #f1f5f9; border-radius: 6px; margin-bottom: 1rem;">
          <strong>${fromState?.name || 'Unknown'}</strong> 
          <span style="color: #64748b;">→</span> 
          <strong>${toState?.name || 'Unknown'}</strong>
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
          <label>Label (e.g., "Submit", "Approve")</label>
          <input
            type="text"
            .value=${selectedTrans.label || ''}
            placeholder="Transition action"
            @input=${(e: Event) =>
          this.updateTransition(selectedTrans.id, {
            label: (e.target as HTMLInputElement).value
          })
        }
          />
        </div>

        <div class="form-group">
          <label>Priority (optional)</label>
          <input
            type="number"
            .value=${selectedTrans.priority || ''}
            placeholder="1 = highest priority"
            @input=${(e: Event) => {
          const value = (e.target as HTMLInputElement).value;
          this.updateTransition(selectedTrans.id, {
            priority: value ? parseInt(value) : undefined
          });
        }}
          />
          <small style="color: #64748b; font-size: 0.75rem;">
            Lower numbers = higher priority when evaluating conditions
          </small>
        </div>

        <div class="form-group">
          <label>Allowed Roles (comma-separated)</label>
          <input
            type="text"
            .value=${(selectedTrans.roles || []).join(', ')}
            placeholder="admin, manager"
            @input=${(e: Event) => {
          const value = (e.target as HTMLInputElement).value;
          const roles = value.split(',').map(r => r.trim()).filter(r => r);
          this.updateTransition(selectedTrans.id, { roles });
        }}
          />
          <small style="color: #64748b; font-size: 0.75rem;">
            Only these roles can trigger this transition
          </small>
        </div>

        <!-- Condition Builder -->
        <div class="form-group">
          <label style="display: flex; justify-content: space-between; align-items: center;">
            <span>Condition (optional)</span>
            ${selectedTrans.condition ? html`
              <span style="font-size: 0.7rem; color: #10b981;">✓ Condition set</span>
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
        </div>

        <div class="form-group">
          <label>
            <input
              type="checkbox"
              .checked=${selectedTrans.isFallback || false}
              @change=${(e: Event) => {
          this.updateTransition(selectedTrans.id, {
            isFallback: (e.target as HTMLInputElement).checked
          });
        }}
            />
            Fallback transition (ELSE)
          </label>
          <small style="color: #64748b; font-size: 0.75rem;">
            Use this transition if no other conditions match
          </small>
        </div>

        <button 
          class="btn" 
          style="background: #ef4444; color: white; margin-top: auto;"
          @click=${() => this.deleteTransition(selectedTrans.id)}
        >
          🗑️ Delete Transition
        </button>
      `;
    }

    // Default: Show all transitions
    return html`
      <h3 style="margin: 0 0 1rem;">
        Transitions (${this.machine?.transitions.length || 0})
      </h3>
      
      ${!this.machine || this.machine.transitions.length === 0 ? html`
        <p style="color: #94a3b8; font-size: 0.875rem; text-align: center; padding: 2rem 0;">
          No transitions defined.<br/>
          Click "Add Transition" to create one.
        </p>
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
                <div style="display: flex; justify-content: space-between; align-items: start; margin-bottom: 0.25rem;">
                  <div style="font-weight: 600;">
                    ${t.label || 'Untitled'}
                  </div>
                  ${t.priority ? html`
                    <span style="font-size: 0.7rem; background: #fbbf24; color: white; padding: 0.125rem 0.375rem; border-radius: 3px;">
                      P${t.priority}
                    </span>
                  ` : ''}
                </div>
                <div style="font-size: 0.75rem; color: #64748b;">
                  ${fromState?.name || 'Unknown'} → ${toState?.name || 'Unknown'}
                </div>
                ${t.condition ? html`
                  <div style="font-size: 0.7rem; color: #10b981; margin-top: 0.25rem;">
                    ⚡ ${t.condition.naturalLanguage || t.condition.expression}
                  </div>
                ` : ''}
                ${t.isFallback ? html`
                  <div style="font-size: 0.7rem; color: #8b5cf6; margin-top: 0.25rem;">
                    🔄 ELSE (Fallback)
                  </div>
                ` : ''}
                ${t.roles && t.roles.length > 0 ? html`
                  <div style="font-size: 0.7rem; color: #3b82f6; margin-top: 0.25rem;">
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
