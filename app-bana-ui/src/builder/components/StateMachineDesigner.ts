/**
 * State Machine Designer
 * Visual editor for creating state machines with states and transitions
 */

import { LitElement, html, css } from 'lit';
import { customElement, state, property } from 'lit/decorators.js';
import type { StateMachine, State, Transition } from '../../models/workflow';
import { workflowStorage } from '../../services/WorkflowStorage';

@customElement('state-machine-designer')
export class StateMachineDesigner extends LitElement {

    @property({ type: String }) entityName?: string;
    @property({ type: String }) machineId?: string;

    @state() private machine: StateMachine | null = null;
    @state() private selectedState: string | null = null;
    @state() private selectedTransition: string | null = null;
    @state() private editMode: 'state' | 'transition' | null = null;

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
    }

    .canvas-panel {
      flex: 1;
      background: white;
      border-radius: 8px;
      border: 1px solid #e2e8f0;
      padding: 2rem;
      overflow: auto;
      position: relative;
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
      padding: 1rem 1.5rem;
      border-radius: 12px;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      cursor: move;
      box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
      min-width: 120px;
      text-align: center;
      user-select: none;
    }

    .state.selected {
      box-shadow: 0 0 0 3px #fbbf24;
    }

    .state-name {
      font-weight: 600;
      font-size: 0.95rem;
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

    private renderCanvas() {
        if (!this.machine || this.machine.states.length === 0) {
            return html`
        <div class="empty-state">
          <p>No states defined</p>
          <p>Click "Add State" to get started</p>
        </div>
      `;
        }

        // Simple grid layout for states
        return html`
      ${this.machine.states.map((state, index) => {
            const x = 50 + (index % 3) * 200;
            const y = 50 + Math.floor(index / 3) * 150;

            return html`
          <div
            class="state ${this.selectedState === state.id ? 'selected' : ''}"
            style="left: ${x}px; top: ${y}px; background: ${state.color || '#6366f1'}"
            @click=${() => {
                    this.selectedState = state.id;
                    this.editMode = 'state';
                }}
          >
            <div class="state-name">${state.name}</div>
            <div class="state-count">
              ${this.machine!.initialState === state.id ? '⭐ Initial' : ''}
            </div>
          </div>
        `;
        })}
    `;
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
            return html`
        <h3 style="margin: 0 0 1rem;">Transition Properties</h3>
        
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
              <option value=${s.id}>${s.name}</option>
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
              <option value=${s.id}>${s.name}</option>
            `)}
          </select>
        </div>

        <div class="form-group">
          <label>Label</label>
          <input
            type="text"
            .value=${selectedTrans.label || ''}
            @input=${(e: Event) =>
                    this.updateTransition(selectedTrans.id, {
                        label: (e.target as HTMLInputElement).value
                    })
                }
          />
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

        return html`
      <h3 style="margin: 0 0 1rem;">Transitions</h3>
      <div class="transitions-list">
        ${this.machine?.transitions.map(t => {
            const fromState = this.machine!.states.find(s => s.id === t.from);
            const toState = this.machine!.states.find(s => s.id === t.to);

            return html`
            <div 
              class="transition-item ${this.selectedTransition === t.id ? 'selected' : ''}"
              @click=${() => {
                    this.selectedTransition = t.id;
                    this.editMode = 'transition';
                }}
            >
              ${fromState?.name || 'Unknown'} → ${toState?.name || 'Unknown'}
              ${t.label ? `(${t.label})` : ''}
            </div>
          `;
        })}
      </div>
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
