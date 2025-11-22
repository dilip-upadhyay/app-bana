import { BaseElement } from '../core/BaseElement';
import { registerComponent } from '../core/registry';

/**
 * StudioInput - Universal text input component
 * Supports: text, email, password, number, tel, url, date, datetime-local
 */
export class InputElement extends BaseElement {
  static get observedAttributes() {
    return ['type', 'label', 'placeholder', 'value', 'required', 'disabled', 'min', 'max', 'pattern', 'name'];
  }

  attributeChangedCallback(name: string, _oldValue: string | null, _newValue: string | null) {
    this.requestRender();
  }

  connectedCallback() {
    this.setupEventListeners();
  }

  private setupEventListeners() {
    // Delegate input events from shadow DOM to host
    this.shadowRoot?.addEventListener('input', (e: Event) => {
      const input = e.target as HTMLInputElement;
      this.setAttribute('value', input.value);
      this.dispatchEvent(new CustomEvent('input', {
        detail: { value: input.value, name: this.getAttribute('name') },
        bubbles: true,
        composed: true
      }));
    });

    this.shadowRoot?.addEventListener('change', (e: Event) => {
      const input = e.target as HTMLInputElement;
      this.dispatchEvent(new CustomEvent('change', {
        detail: { value: input.value, name: this.getAttribute('name') },
        bubbles: true,
        composed: true
      }));
    });
  }

  private getInputType(): string {
    const type = this.getAttribute('type') || 'text';
    const validTypes = ['text', 'email', 'password', 'number', 'tel', 'url', 'date', 'datetime-local', 'time'];
    return validTypes.includes(type) ? type : 'text';
  }

  protected render(): string {
    const label = this.getAttribute('label') || '';
    const placeholder = this.getAttribute('placeholder') || '';
    const value = this.getAttribute('value') || '';
    const required = this.hasAttribute('required');
    const disabled = this.hasAttribute('disabled');
    const min = this.getAttribute('min') || '';
    const max = this.getAttribute('max') || '';
    const pattern = this.getAttribute('pattern') || '';
    const type = this.getInputType();

    const inputAttrs = [
      `type="${type}"`,
      `placeholder="${placeholder}"`,
      `value="${value}"`,
      required ? 'required' : '',
      disabled ? 'disabled' : '',
      min ? `min="${min}"` : '',
      max ? `max="${max}"` : '',
      pattern ? `pattern="${pattern}"` : ''
    ].filter(Boolean).join(' ');

    return `
      ${label ? `<label part="label">${label}${required ? '<span class="required">*</span>' : ''}</label>` : ''}
      <input part="input" ${inputAttrs} />
    `;
  }

  protected styles(): string {
    return `
      :host {
        display: block;
        font-family: var(--font-sans, system-ui);
      }
      label {
        display: block;
        margin-bottom: 0.5rem;
        font-size: var(--text-sm, 0.875rem);
        font-weight: 500;
        color: var(--color-text, #333);
      }
      .required {
        color: var(--color-danger, #e74c3c);
        margin-left: 0.25rem;
      }
      input {
        width: 100%;
        padding: 0.625rem 0.75rem;
        border: 1px solid var(--color-border, #d1d5db);
        border-radius: var(--radius-sm, 4px);
        font-size: var(--text-base, 1rem);
        font-family: inherit;
        color: var(--color-text, #333);
        background: var(--color-surface, #fff);
        box-sizing: border-box;
        transition: border-color 0.2s, box-shadow 0.2s;
      }
      input:focus {
        outline: none;
        border-color: var(--color-brand, #3498db);
        box-shadow: 0 0 0 3px var(--color-focus-ring, rgba(52, 152, 219, 0.1));
      }
      input:disabled {
        background-color: var(--color-surface-muted, #f5f5f5);
        cursor: not-allowed;
        opacity: 0.6;
      }
      input::placeholder {
        color: var(--color-text-muted, #9ca3af);
      }
      input:invalid:not(:placeholder-shown) {
        border-color: var(--color-danger, #e74c3c);
      }
    `;
  }
}

customElements.define('studio-input', InputElement);
registerComponent('input', InputElement);
