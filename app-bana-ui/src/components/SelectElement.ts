import { FormElement } from './FormElement';
import { registerComponent } from '../core/registry';

/**
 * StudioSelect - Dropdown select component with Field-Level Security (FLS)
 * Options format: JSON array or comma-separated values
 */
export class SelectElement extends FormElement {
  static get observedAttributes() {
    return ['label', 'value', 'options', 'placeholder', 'required', 'disabled', 'name', 'entity'];
  }

  attributeChangedCallback(name: string, _oldValue: string | null, _newValue: string | null) {
    if (name === 'value') {
      if (this.shadowRoot) {
        const select = this.shadowRoot.querySelector('select');
        if (select && _newValue !== null && select.value !== _newValue) {
          select.value = _newValue;
        }
      }
      return;
    }
    this.requestRender();
  }

  get value(): string {
    const select = this.shadowRoot?.querySelector('select');
    return select ? select.value : this.getAttribute('value') || '';
  }

  set value(val: string) {
    const select = this.shadowRoot?.querySelector('select');
    if (select) {
      select.value = val;
    }
    this.setAttribute('value', val);
  }

  async connectedCallback() {
    await this.loadFieldPermissionsFromAttribute();
  }

  private parseOptions(): Array<{ value: string; label: string }> {
    const optionsAttr = this.getAttribute('options') || '';

    // Try to parse as JSON array
    try {
      const parsed = JSON.parse(optionsAttr);
      if (Array.isArray(parsed)) {
        return parsed.map(opt => {
          if (typeof opt === 'string') {
            return { value: opt, label: opt };
          }
          return { value: opt.value || opt.label, label: opt.label || opt.value };
        });
      }
    } catch (e) {
      // Not JSON, treat as comma-separated
    }

    // Parse as comma-separated values
    if (optionsAttr) {
      return optionsAttr.split(',').map(opt => {
        const trimmed = opt.trim();
        return { value: trimmed, label: trimmed };
      });
    }

    return [];
  }

  protected render(): string {
    const fieldName = this.getAttribute('name') || '';

    // FLS: Hide non-readable fields
    if (this.isFieldHidden(fieldName)) {
      return this.renderHiddenField();
    }

    const label = this.getAttribute('label') || '';
    const value = this.getAttribute('value') || '';
    const placeholder = this.getAttribute('placeholder') || 'Select an option';
    const required = this.hasAttribute('required');
    const disabled = this.hasAttribute('disabled');
    const options = this.parseOptions();

    // FLS: Disable non-editable fields
    const flsDisabled = this.isFieldDisabled(fieldName);
    const isDisabled = disabled || flsDisabled;
    const lockIcon = flsDisabled ? this.getLockIcon() : '';
    const title = flsDisabled ? this.getDisabledTooltip() : '';

    const selectAttrs = [
      required ? 'required' : '',
      isDisabled ? 'disabled' : '',
      title ? `title="${title}"` : ''
    ].filter(Boolean).join(' ');

    const optionsHtml = [
      placeholder ? `<option value="" disabled ${!value ? 'selected' : ''}>${placeholder}</option>` : '',
      ...options.map(opt =>
        `<option value="${opt.value}" ${value === opt.value ? 'selected' : ''}>${opt.label}</option>`
      )
    ].join('');

    return `
      ${label ? `<label part="label">${label}${lockIcon}${required ? '<span class="required">*</span>' : ''}</label>` : ''}
      <select part="select" ${selectAttrs}>
        ${optionsHtml}
      </select>
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
        margin-bottom: 0.2rem;
        font-size: var(--text-sm, 0.875rem);
        font-weight: 500;
        color: var(--color-text, #333);
      }
      .required {
        color: var(--color-danger, #e74c3c);
        margin-left: 0.25rem;
      }
      select {
        width: 100%;
        padding: 0.625rem 2rem 0.625rem 0.75rem;
        border: 1px solid var(--color-border, #d1d5db);
        border-radius: var(--radius-sm, 4px);
        font-size: var(--text-base, 1rem);
        font-family: inherit;
        color: var(--color-text, #333);
        background: var(--color-surface, #fff);
        background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%23333' d='M6 9L1 4h10z'/%3E%3C/svg%3E");
        background-repeat: no-repeat;
        background-position: right 0.75rem center;
        background-size: 12px;
        box-sizing: border-box;
        cursor: pointer;
        appearance: none;
        transition: border-color 0.2s, box-shadow 0.2s;
      }
      select:focus {
        outline: none;
        border-color: var(--color-brand, #3498db);
        box-shadow: 0 0 0 3px var(--color-focus-ring, rgba(52, 152, 219, 0.1));
      }
      select:disabled {
        background-color: var(--color-surface-muted, #f5f5f5);
        cursor: not-allowed;
        opacity: 0.6;
      }
      option {
        padding: 0.5rem;
      }
    `;
  }
}

if (!customElements.get('appbana-select')) {
  customElements.define('appbana-select', SelectElement);
}
registerComponent('select', SelectElement, 'appbana-select');
