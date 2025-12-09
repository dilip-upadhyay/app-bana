import { FormElement } from './FormElement';
import { registerComponent } from '../core/registry';

/**
 * StudioInput - Text input component with Field-Level Security (FLS)
 */
export class InputElement extends FormElement {
  static get observedAttributes() {
    return ['label', 'value', 'placeholder', 'type', 'required', 'disabled', 'name', 'entity'];
  }

  attributeChangedCallback(name: string, _oldValue: string | null, _newValue: string | null) {
    if (name === 'value') {
      if (this.shadowRoot) {
        const input = this.shadowRoot.querySelector('input');
        if (input && _newValue !== null && input.value !== _newValue) {
          input.value = _newValue;
        }
      }
      // Skip re-render to preserve focus/cursor
      return;
    }
    this.requestRender();
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
  }

  async connectedCallback() {
    await this.loadFieldPermissionsFromAttribute();
  }

  protected render(): string {
    const fieldName = this.getAttribute('name') || '';

    // FLS: Hide non-readable fields
    if (this.isFieldHidden(fieldName)) {
      return this.renderHiddenField();
    }

    const label = this.getAttribute('label') || '';
    const value = this.getAttribute('value') || '';
    const placeholder = this.getAttribute('placeholder') || '';
    const type = this.getAttribute('type') || 'text';
    const required = this.hasAttribute('required');
    const disabled = this.hasAttribute('disabled');

    // FLS: Disable non-editable fields
    const flsDisabled = this.isFieldDisabled(fieldName);
    const isDisabled = disabled || flsDisabled;
    const lockIcon = flsDisabled ? this.getLockIcon() : '';
    const title = flsDisabled ? this.getDisabledTooltip() : '';

    const inputAttrs = [
      `type="${type}"`,
      `value="${value}"`,
      placeholder ? `placeholder="${placeholder}"` : '',
      required ? 'required' : '',
      isDisabled ? 'disabled' : '',
      title ? `title="${title}"` : ''
    ].filter(Boolean).join(' ');

    return `
      ${label ? `<label part="label">${label}${lockIcon}${required ? '<span class="required">*</span>' : ''}</label>` : ''}
      <input part="input" ${inputAttrs}>
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
    `;
  }
}

if (!customElements.get('studio-input')) {
  customElements.define('studio-input', InputElement);
}
registerComponent('input', InputElement);
