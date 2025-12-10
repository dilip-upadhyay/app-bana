import { BaseElement } from '../core/BaseElement';
import { apiClient } from '../core/api-client';
import { registerComponent } from '../core/registry';

/**
 * StudioForm - A container that handles data submission for an entity.
 * 
 * Props:
 * - entity: The name of the entity (e.g., "LoanApplication")
 * - recordId: Optional ID for editing an existing record. If omitted, creates new.
 * - redirectOnSuccess: generic path (client-side router) to go to after success.
 */
export class FormContainer extends BaseElement {
    static get observedAttributes() {
        return ['entity', 'record-id', 'redirect-on-success'];
    }

    protected render(): string {
        // We render a generic slot. 
        // The form submission is handled by listening to buttons with type="submit".
        return `
      <form style="display: block;" onsubmit="return false;">
        <slot></slot>
        <div id="error-msg" style="color: red; margin-top: 10px; display: none;"></div>
      </form>
    `;
    }

    protected styles(): string {
        return `
      :host { display: block; }
    `;
    }

    async connectedCallback() {
        super.connectedCallback();
        this.addEventListener('click', this.handleClick.bind(this));
        // Load data if recordId is present
        this.checkAndLoadRecord();
    }

    attributeChangedCallback(name: string, oldValue: string | null, newValue: string | null) {
        if (name === 'record-id' && newValue && newValue !== oldValue) {
            this.loadRecord(newValue);
        }
    }

    private checkAndLoadRecord() {
        const recordId = this.getAttribute('record-id') || this.getAttribute('recordId');
        if (recordId && recordId !== 'undefined' && recordId !== 'null') {
            this.loadRecord(recordId);
        }
    }

    private async loadRecord(id: string) {
        const entity = this.getAttribute('entity');
        if (!entity) return;

        try {
            const data = await apiClient.get<any>(`/api/${entity}/${id}`);
            this.populateForm(data);
        } catch (e) {
            console.error('Failed to load record', e);
            this.showError('Failed to load record');
        }
    }

    private populateForm(data: any) {
        // Create a normalized map of data keys for case-insensitive lookup
        const normalizedData: Record<string, any> = {};
        if (data) {
            Object.keys(data).forEach(key => {
                normalizedData[key.toLowerCase()] = data[key];
            });
        }

        // Helper to find value case-insensitively
        const getValue = (name: string) => {
            if (!name) return undefined;
            // Try exact match first
            if (data[name] !== undefined) return data[name];
            // Try lowercase match
            return normalizedData[name.toLowerCase()];
        };

        // Find all studio-input, studio-select, etc.
        const inputs = this.querySelectorAll('studio-input, studio-select, studio-textarea');
        inputs.forEach((el: any) => {
            const name = el.getAttribute('name');
            const val = getValue(name);
            if (name && val !== undefined) {
                el.setAttribute('value', String(val));
                // If it supports .value property setting
                if (el.value !== undefined) el.value = val;
            }
        });

        // Also populate standard inputs if any
        const stdInputs = this.querySelectorAll('input, select, textarea');
        stdInputs.forEach((el: any) => {
            const val = getValue(el.name);
            if (el.name && val !== undefined) {
                el.value = val;
            }
        });
    }

    private async handleClick(e: Event) {
        const target = e.target as HTMLElement;
        // Walk up to find the button if clicked on icon inside
        const btn = target.closest('studio-button') || target.closest('button');
        if (!btn) return;

        // Check for field updates (e.g. Approve/Reject buttons setting status)
        const updateField = btn.getAttribute('update-field');
        const updateValue = btn.getAttribute('update-value');

        if (updateField && updateValue) {
            // Find the input and update it
            const input = this.querySelector(`[name="${updateField}"]`) as any;
            if (input) {
                if (input.value !== undefined) input.value = updateValue;
                else input.setAttribute('value', updateValue);
            } else {
                // Create hidden input if it doesn't exist
                // (Though ideally it should exist).
            }
        }

        // Check if it's a submit button
        const type = btn.getAttribute('type');
        if (type === 'submit') {
            e.preventDefault();
            await this.handleSubmit();
        }
    }

    private async handleSubmit() {
        const entity = this.getAttribute('entity');
        const recordId = this.getAttribute('record-id') || this.getAttribute('recordId');
        const redirect = this.getAttribute('redirect-on-success') || this.getAttribute('redirectOnSuccess');

        if (!entity) {
            this.showError('Configuration Error: No entity specified for form.');
            return;
        }

        // Collect data
        const formData: Record<string, any> = {};
        const inputs = this.querySelectorAll('studio-input, studio-select, studio-textarea, input, select, textarea');

        inputs.forEach((el: any) => {
            const name = el.getAttribute('name') || el.name;
            const val = el.getAttribute('value') || el.value;
            if (name) {
                formData[name] = val;
            }
        });

        // Sanitize: Convert empty strings to null for numbers? (Simplification: send as is, backend handles or fails)

        this.showError(''); // Clear error

        try {
            if (recordId) {
                // Update
                await apiClient.put(`/api/${entity}/${recordId}`, formData);
            } else {
                // Create
                await apiClient.post(`/api/${entity}`, formData);
            }

            // Success!
            alert('Saved successfully!');
            if (redirect) {
                // Use global navigation if available or location assignment
                // Since this is runtime, we might use history API if we are in SPA mode
                // Simplest:
                const url = new URL(window.location.href);
                // If redirect starts with /, it's a page path.
                if (redirect.startsWith('/')) {
                    // Check if we are in "runtime state" mode url?state=...
                    // If so, we need to update the state to change pageId.
                    // But navigation is complex.
                    // Studio Runtime Shim:
                    // If window['navigate'] exists (injected by runtime), use it.
                    if ((window as any).navigate) {
                        (window as any).navigate(redirect);
                    } else {
                        // Fallback: dispatch custom event in case window.navigate is not bound yet or we are in a shadow root issue
                        this.dispatchEvent(new CustomEvent('navigate', {
                            bubbles: true,
                            composed: true,
                            detail: { path: redirect }
                        }));
                    }
                    // Do NOT use window.location.href here as it breaks the preview state.
                } else {
                    window.location.href = redirect;
                }
            }
        } catch (e: any) {
            console.error('Submit error:', e);
            this.showError(`Error: ${e.message || 'Unknown error'}`);
        }
    }

    private showError(msg: string) {
        const el = this.shadowRoot?.getElementById('error-msg');
        if (el) {
            el.textContent = msg;
            el.style.display = msg ? 'block' : 'none';
        }
    }
}

if (!customElements.get('studio-form')) {
    customElements.define('studio-form', FormContainer);
}
registerComponent('form', FormContainer);
