import { BaseElement } from '../core/BaseElement';
import { registerComponent } from '../core/registry';

/**
 * StudioCheckbox - Single checkbox input component
 */
export class CheckboxElement extends BaseElement {
  static get observedAttributes() {
    return ['label', 'checked', 'disabled', 'name', 'value'];
  }

  attributeChangedCallback(name: string, _oldValue: string | null, _newValue: string | null) {
    this.requestRender();
  }

  protected render(): string {
    const label = this.getAttribute('label') || '';
    const checked = this.hasAttribute('checked');
    const disabled = this.hasAttribute('disabled');
    const value = this.getAttribute('value') || 'on';

    const inputAttrs = [
      `type="checkbox"`,
      `value="${value}"`,
      checked ? 'checked' : '',
      disabled ? 'disabled' : ''
    ].filter(Boolean).join(' ');

    return `
      <label part="label" class="checkbox-wrapper">
        <input part="input" ${inputAttrs} />
        <span class="checkmark"></span>
        ${label ? `<span class="label-text">${label}</span>` : ''}
      </label>
    `;
  }

  protected styles(): string {
    return `
      :host {
        display: inline-block;
        font-family: var(--font-sans, system-ui);
      }
      .checkbox-wrapper {
        display: inline-flex;
        align-items: center;
        gap: 0.5rem;
        cursor: pointer;
        user-select: none;
        position: relative;
      }
      input[type="checkbox"] {
        position: absolute;
        opacity: 0;
        cursor: pointer;
        height: 0;
        width: 0;
      }
      .checkmark {
        display: inline-block;
        width: 1.125rem;
        height: 1.125rem;
        border: 2px solid var(--color-border, #d1d5db);
        border-radius: var(--radius-xs, 3px);
        background: var(--color-surface, #fff);
        position: relative;
        transition: all 0.2s;
        flex-shrink: 0;
      }
      .checkmark::after {
        content: '';
        position: absolute;
        display: none;
        left: 4px;
        top: 1px;
        width: 5px;
        height: 9px;
        border: solid white;
        border-width: 0 2px 2px 0;
        transform: rotate(45deg);
      }
      input[type="checkbox"]:checked ~ .checkmark {
        background: var(--color-brand, #3498db);
        border-color: var(--color-brand, #3498db);
      }
      input[type="checkbox"]:checked ~ .checkmark::after {
        display: block;
      }
      input[type="checkbox"]:focus ~ .checkmark {
        box-shadow: 0 0 0 3px var(--color-focus-ring, rgba(52, 152, 219, 0.1));
      }
      input[type="checkbox"]:disabled ~ .checkmark {
        background-color: var(--color-surface-muted, #f5f5f5);
        cursor: not-allowed;
        opacity: 0.6;
      }
      input[type="checkbox"]:disabled ~ .label-text {
        opacity: 0.6;
      }
      .label-text {
        font-size: var(--text-sm, 0.875rem);
        color: var(--color-text, #333);
      }
      .checkbox-wrapper:hover input[type="checkbox"]:not(:disabled) ~ .checkmark {
        border-color: var(--color-brand, #3498db);
      }
    `;
  }
}

customElements.define('studio-checkbox', CheckboxElement);
registerComponent('checkbox', CheckboxElement);
