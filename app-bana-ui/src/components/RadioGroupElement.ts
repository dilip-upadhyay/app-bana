import { BaseElement } from '../core/BaseElement';
import { registerComponent } from '../core/registry';

/**
 * StudioRadioGroup - Radio button group component
 * Options format: JSON array or comma-separated values
 */
export class RadioGroupElement extends BaseElement {
  static get observedAttributes() {
    return ['label', 'name', 'value', 'options', 'required', 'disabled', 'layout'];
  }

  attributeChangedCallback(name: string, _oldValue: string | null, _newValue: string | null) {
    this.requestRender();
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
    const label = this.getAttribute('label') || '';
    const name = this.getAttribute('name') || 'radio-group';
    const value = this.getAttribute('value') || '';
    const required = this.hasAttribute('required');
    const disabled = this.hasAttribute('disabled');
    const layout = this.getAttribute('layout') || 'vertical';
    const options = this.parseOptions();

    const optionsHtml = options.map((opt, index) => {
      const radioId = `${name}-${index}`;
      const inputAttrs = [
        `type="radio"`,
        `name="${name}"`,
        `value="${opt.value}"`,
        `id="${radioId}"`,
        value === opt.value ? 'checked' : '',
        required && index === 0 ? 'required' : '',
        disabled ? 'disabled' : ''
      ].filter(Boolean).join(' ');

      return `
        <label part="radio-label" class="radio-option">
          <input part="radio-input" ${inputAttrs} />
          <span class="radio-mark"></span>
          <span class="radio-text">${opt.label}</span>
        </label>
      `;
    }).join('');

    return `
      ${label ? `<div part="group-label" class="group-label">${label}${required ? '<span class="required">*</span>' : ''}</div>` : ''}
      <div part="options-container" class="options-container ${layout}">
        ${optionsHtml}
      </div>
    `;
  }

  protected styles(): string {
    return `
      :host {
        display: block;
        font-family: var(--font-sans, system-ui);
      }
      .group-label {
        display: block;
        margin-bottom: 0.75rem;
        font-size: var(--text-sm, 0.875rem);
        font-weight: 500;
        color: var(--color-text, #333);
      }
      .required {
        color: var(--color-danger, #e74c3c);
        margin-left: 0.25rem;
      }
      .options-container {
        display: flex;
        gap: 1rem;
      }
      .options-container.vertical {
        flex-direction: column;
      }
      .options-container.horizontal {
        flex-direction: row;
        flex-wrap: wrap;
      }
      .radio-option {
        display: inline-flex;
        align-items: center;
        gap: 0.5rem;
        cursor: pointer;
        user-select: none;
        position: relative;
      }
      input[type="radio"] {
        position: absolute;
        opacity: 0;
        cursor: pointer;
        height: 0;
        width: 0;
      }
      .radio-mark {
        display: inline-block;
        width: 1.125rem;
        height: 1.125rem;
        border: 2px solid var(--color-border, #d1d5db);
        border-radius: 50%;
        background: var(--color-surface, #fff);
        position: relative;
        transition: all 0.2s;
        flex-shrink: 0;
      }
      .radio-mark::after {
        content: '';
        position: absolute;
        display: none;
        left: 50%;
        top: 50%;
        width: 8px;
        height: 8px;
        border-radius: 50%;
        background: white;
        transform: translate(-50%, -50%);
      }
      input[type="radio"]:checked ~ .radio-mark {
        background: var(--color-brand, #3498db);
        border-color: var(--color-brand, #3498db);
      }
      input[type="radio"]:checked ~ .radio-mark::after {
        display: block;
      }
      input[type="radio"]:focus ~ .radio-mark {
        box-shadow: 0 0 0 3px var(--color-focus-ring, rgba(52, 152, 219, 0.1));
      }
      input[type="radio"]:disabled ~ .radio-mark {
        background-color: var(--color-surface-muted, #f5f5f5);
        cursor: not-allowed;
        opacity: 0.6;
      }
      input[type="radio"]:disabled ~ .radio-text {
        opacity: 0.6;
      }
      .radio-text {
        font-size: var(--text-sm, 0.875rem);
        color: var(--color-text, #333);
      }
      .radio-option:hover input[type="radio"]:not(:disabled) ~ .radio-mark {
        border-color: var(--color-brand, #3498db);
      }
    `;
  }
}

customElements.define('studio-radio-group', RadioGroupElement);
registerComponent('radio-group', RadioGroupElement);
