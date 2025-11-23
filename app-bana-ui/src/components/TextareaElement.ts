import { FormElement } from './FormElement';
import { registerComponent } from '../core/registry';

export class TextareaElement extends FormElement {
  static get observedAttributes() {
    return ['label', 'value', 'placeholder', 'rows', 'required', 'disabled', 'name', 'entity'];
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
    const value = this.getAttribute('value') || '';
    const placeholder = this.getAttribute('placeholder') || '';
    const rows = this.getAttribute('rows') || '3';
    const required = this.hasAttribute('required');
    const disabled = this.hasAttribute('disabled');

    const flsDisabled = this.isFieldDisabled(fieldName);
    const isDisabled = disabled || flsDisabled;
    const lockIcon = flsDisabled ? this.getLockIcon() : '';
    const title = flsDisabled ? this.getDisabledTooltip() : '';

    const attrs = [
      `rows="${rows}"`,
      placeholder ? `placeholder="${placeholder}"` : '',
      required ? 'required' : '',
      isDisabled ? 'disabled' : '',
      title ? `title="${title}"` : ''
    ].filter(Boolean).join(' ');

    return `
      ${label ? `<label part="label">${label}${lockIcon}${required ? '<span class="required">*</span>' : ''}</label>` : ''}
      <textarea part="textarea" ${attrs}>${value}</textarea>
    `;
  }

  protected styles(): string {
    return `
      :host { display: block; font-family: var(--font-sans, system-ui); }
      label { display: block; margin-bottom: 0.5rem; font-size: 0.875rem; font-weight: 500; color: #374151; }
      .required { color: #ef4444; margin-left: 0.25rem; }
      textarea {
        width: 100%;
        padding: 0.625rem 0.75rem;
        border: 1px solid #d1d5db;
        border-radius: 4px;
        font-family: inherit;
        font-size: 1rem;
        color: #1f2937;
        resize: vertical;
      }
      textarea:focus { outline: none; border-color: #3b82f6; box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1); }
      textarea:disabled { background: #f3f4f6; cursor: not-allowed; }
    `;
  }
}

if (!customElements.get('studio-textarea')) {
  customElements.define('studio-textarea', TextareaElement);
}
registerComponent('textarea', TextareaElement);
