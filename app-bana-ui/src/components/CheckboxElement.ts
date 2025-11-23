import { FormElement } from './FormElement';
import { registerComponent } from '../core/registry';

export class CheckboxElement extends FormElement {
  static get observedAttributes() {
    return ['label', 'checked', 'required', 'disabled', 'name', 'entity'];
  }

  attributeChangedCallback() {
    this.requestRender();
  }

  async connectedCallback() {
    await this.loadFieldPermissionsFromAttribute();
  }

  protected render(): string {
    const fieldName = this.getAttribute('name') || '';
    if (this.isFieldHidden(fieldName)) return this.renderHiddenField();

    const label = this.getAttribute('label') || '';
    const checked = this.hasAttribute('checked');
    const required = this.hasAttribute('required');
    const disabled = this.hasAttribute('disabled');

    const flsDisabled = this.isFieldDisabled(fieldName);
    const isDisabled = disabled || flsDisabled;
    const lockIcon = flsDisabled ? this.getLockIcon() : '';
    const title = flsDisabled ? this.getDisabledTooltip() : '';

    const attrs = [
      'type="checkbox"',
      checked ? 'checked' : '',
      required ? 'required' : '',
      isDisabled ? 'disabled' : '',
      title ? `title="${title}"` : ''
    ].filter(Boolean).join(' ');

    return `
      <label class="checkbox-container">
        <input part="checkbox" ${attrs}>
        <span class="label-text">${label}${lockIcon}${required ? '<span class="required">*</span>' : ''}</span>
      </label>
    `;
  }

  protected styles(): string {
    return `
      :host { display: block; font-family: var(--font-sans, system-ui); }
      .checkbox-container { display: flex; align-items: center; gap: 0.5rem; cursor: pointer; }
      .label-text { font-size: 0.875rem; color: #374151; font-weight: 500; }
      .required { color: #ef4444; margin-left: 0.25rem; }
      input[type="checkbox"] {
        width: 1rem; height: 1rem;
        border-radius: 4px; border: 1px solid #d1d5db;
        cursor: pointer;
      }
      input:disabled { cursor: not-allowed; opacity: 0.6; }
      input:disabled + .label-text { opacity: 0.6; }
    `;
  }
}

if (!customElements.get('studio-checkbox')) {
  customElements.define('studio-checkbox', CheckboxElement);
}
registerComponent('checkbox', CheckboxElement);
