import { FormElement } from './FormElement';
import { registerComponent } from '../core/registry';

/**
 * Material Design Input Component
 * Features: Floating label, Outlined style, Error state, Helper text
 */
export class InputElement extends FormElement {
  static get observedAttributes() {
    return ['label', 'value', 'placeholder', 'type', 'required', 'disabled', 'readonly', 'name', 'error', 'helper-text'];
  }

  attributeChangedCallback(name: string, oldValue: string | null, newValue: string | null) {
    if (name === 'value') {
      const input = this.shadowRoot?.querySelector('input');
      if (input && newValue !== null && input.value !== newValue) {
        input.value = newValue;
      }
      // Toggle 'has-value' class for label floating if value is set programmatically
      this.updateState();
      return;
    }
    this.requestRender();
  }

  connectedCallback() {
    super.connectedCallback();
    this.shadowRoot?.addEventListener('input', this.handleInput.bind(this));
    this.shadowRoot?.addEventListener('focusout', this.handleBlur.bind(this));
  }

  private handleInput(e: Event) {
    const input = e.target as HTMLInputElement;
    this.setAttribute('value', input.value);
    this.updateState();

    // Clear error while typing if it exists
    if (this.hasAttribute('error')) {
      this.validate();
    }
  }

  private handleBlur(e: Event) {
    this.touched = true;
    this.validate();
  }

  private validate() {
    const value = this.value;
    const required = this.hasAttribute('required');
    const type = this.getAttribute('type');
    const label = this.getAttribute('label') || 'This field';

    let error = '';

    if (required && !value.trim()) {
      error = `${label} is required.`;
    } else if (value && type === 'email') {
      const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
      if (!emailPattern.test(value)) {
        error = `Please enter a valid email address.`;
      }
    }

    if (error) {
      this.setAttribute('error', error);
    } else {
      this.removeAttribute('error');
    }
  }

  private touched = false;

  private updateState() {
    const input = this.shadowRoot?.querySelector('input');
    const container = this.shadowRoot?.querySelector('.input-container');
    if (input && container) {
      if (input.value) {
        container.classList.add('has-value');
      } else {
        container.classList.remove('has-value');
      }
    }
  }

  get value(): string {
    const input = this.shadowRoot?.querySelector('input');
    return input ? input.value : this.getAttribute('value') || '';
  }

  set value(val: string) {
    const input = this.shadowRoot?.querySelector('input');
    if (input) {
      input.value = val;
    }
    this.setAttribute('value', val);
    this.updateState();
  }

  protected render(): string {
    const fieldName = this.getAttribute('name') || '';
    if (this.isFieldHidden(fieldName)) return this.renderHiddenField();

    const label = this.getAttribute('label') || '';
    const value = this.getAttribute('value') || '';
    const placeholder = this.getAttribute('placeholder') || ' ';
    const type = this.getAttribute('type') || 'text';
    const required = this.hasAttribute('required');
    const disabled = this.hasAttribute('disabled');
    const readonly = this.hasAttribute('readonly') || this.hasAttribute('readOnly');
    const error = this.getAttribute('error');
    const helperText = this.getAttribute('helper-text');

    const flsDisabled = this.isFieldDisabled(fieldName);
    const isDisabled = disabled || flsDisabled;
    const lockIcon = flsDisabled ? this.getLockIcon() : '';

    // CSS Classes
    const classes = ['input-container'];
    if (error) classes.push('error');
    if (isDisabled) classes.push('disabled');
    if (value) classes.push('has-value');

    const inputAttrs = [
      `type="${type}"`,
      `value="${value}"`,
      `placeholder="${placeholder}"`,
      required ? 'required' : '',
      isDisabled ? 'disabled' : '',
      readonly ? 'readonly' : '',
      `id="input-${fieldName || 'field'}"`
    ].filter(Boolean).join(' ');

    const labelText = `${label} ${required ? '*' : ''}`;

    return `
      <div class="${classes.join(' ')}">
        <div class="relative-wrapper">
          <input part="input" ${inputAttrs}>
          <label part="label" for="input-${fieldName || 'field'}">
            ${labelText} ${lockIcon}
          </label>
          <fieldset aria-hidden="true">
            <legend><span>${labelText}</span></legend>
          </fieldset>
        </div>
        ${error
        ? `<div class="message error-message">${error}</div>`
        : (helperText ? `<div class="message helper-text">${helperText}</div>` : '')}
      </div>
    `;
  }

  protected styles(): string {
    return `
      :host {
        display: block;
        font-family: var(--font-sans, 'Inter', system-ui, sans-serif);
        margin-bottom: var(--margin-bottom, 1rem);
        width: 100%;
        max-width: 32rem;
        --color-brand: #6366f1;
        --color-error: #ef4444;
        --color-text: inherit;
        --color-text-light: #6b7280;
        --color-border: #d1d5db;
        --bg-color: transparent;
      }

      .input-container {
        position: relative;
        width: 100%;
      }

      .relative-wrapper {
        position: relative;
        display: flex;
        align-items: center;
      }

      /* Input Base */
      input {
        width: 100%;
        padding: 0.75rem 1rem;
        font-size: 1rem;
        line-height: 1.5;
        color: var(--color-text);
        background-color: var(--bg-color);
        border: none;
        border-radius: 0.5rem;
        box-sizing: border-box;
        appearance: none;
        z-index: 1;
        outline: none;
      }

      /* Fieldset Border Logic */
      fieldset {
        position: absolute;
        top: -5px;
        left: 0;
        right: 0;
        bottom: 0;
        margin: 0;
        padding: 0 8px;
        pointer-events: none;
        overflow: hidden;
        border: 1px solid var(--color-border);
        border-radius: 0.5rem;
        transition: border-color 0.2s;
        z-index: 0;
      }

      legend {
        width: auto;
        height: 11px;
        display: block;
        padding: 0;
        font-size: 0.85em; /* Matches loose label scale */
        max-width: 0.01px; /* Hidden default */
        text-align: left;
        transition: max-width 50ms;
        visibility: hidden;
        white-space: nowrap;
      }
      
      legend span {
        padding-left: 5px;
        padding-right: 5px;
        display: inline-block;
        opacity: 0; /* Just takes up space */
      }

      /* Hide placeholder when not focused */
      input:not(:focus)::placeholder {
        opacity: 0;
      }

      /* Float Logic: When focused, reveal legend hole */
      input:focus ~ fieldset legend,
      input:not(:placeholder-shown) ~ fieldset legend,
      .has-value fieldset legend {
        max-width: 100%;
      }

      /* Float Label */
      label {
        position: absolute;
        left: 1rem;
        top: 0.75rem;
        padding: 0;
        color: var(--color-text-light);
        font-size: 1rem;
        transition: all 0.2s ease-out;
        pointer-events: none;
        user-select: none;
        transform-origin: left top;
        z-index: 2;
      }

      input:focus ~ label,
      input:not(:placeholder-shown) ~ label,
      .has-value label {
        top: -0.6em;
        left: 0.85rem;
        font-size: 0.85rem;
        color: var(--color-brand);
        font-weight: 500;
        transform: translate(0, 0); /* Stabilize */
      }

      /* Focus State - Border on Fieldset */
      input:focus ~ fieldset {
        border-color: var(--color-brand);
        border-width: 2px;
      }
      /* Compensate padding for 2px border? */
      /* Actually standard border-box handles it OK usually */

      /* Error State */
      .error fieldset {
        border-color: var(--color-error);
      }
      .error input:focus ~ fieldset {
         border-color: var(--color-error);
      }
      .error label, 
      .error input:focus ~ label,
      .error input:not(:placeholder-shown) ~ label {
        color: var(--color-error);
      }

      /* Disabled State */
      .disabled input {
        color: #9ca3af;
        cursor: not-allowed;
      }
      .disabled fieldset {
        background-color: rgba(200,200,200, 0.1);
        border-color: #e5e7eb;
      }

      .message {
        font-size: 0.75rem;
        margin-top: 0.25rem;
        margin-left: 0.25rem;
      }
      .error-message { color: var(--color-error); }
      .helper-text { color: var(--color-text-light); }
      .required { color: var(--color-error); margin-left: 2px; }
    `;
  }
}

if (!customElements.get('appbana-input')) {
  customElements.define('appbana-input', InputElement);
}
registerComponent('input', InputElement, 'appbana-input');
