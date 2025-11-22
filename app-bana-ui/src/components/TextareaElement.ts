import { BaseElement } from '../core/BaseElement';
import { registerComponent } from '../core/registry';

/**
 * StudioTextarea - Multi-line text input component
 */
export class TextareaElement extends BaseElement {
  static get observedAttributes() {
    return ['label', 'placeholder', 'value', 'required', 'disabled', 'rows', 'maxlength', 'name'];
  }

  attributeChangedCallback(name: string, _oldValue: string | null, _newValue: string | null) {
    this.requestRender();
  }

  protected render(): string {
    const label = this.getAttribute('label') || '';
    const placeholder = this.getAttribute('placeholder') || '';
    const value = this.getAttribute('value') || '';
    const required = this.hasAttribute('required');
    const disabled = this.hasAttribute('disabled');
    const rows = this.getAttribute('rows') || '4';
    const maxlength = this.getAttribute('maxlength') || '';

    const textareaAttrs = [
      `placeholder="${placeholder}"`,
      `rows="${rows}"`,
      required ? 'required' : '',
      disabled ? 'disabled' : '',
      maxlength ? `maxlength="${maxlength}"` : ''
    ].filter(Boolean).join(' ');

    return `
      ${label ? `<label part="label">${label}${required ? '<span class="required">*</span>' : ''}</label>` : ''}
      <textarea part="textarea" ${textareaAttrs}>${value}</textarea>
      ${maxlength ? `<div class="char-count"><span class="current">${value.length}</span> / ${maxlength}</div>` : ''}
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
      textarea {
        width: 100%;
        padding: 0.625rem 0.75rem;
        border: 1px solid var(--color-border, #d1d5db);
        border-radius: var(--radius-sm, 4px);
        font-size: var(--text-base, 1rem);
        font-family: inherit;
        color: var(--color-text, #333);
        background: var(--color-surface, #fff);
        box-sizing: border-box;
        resize: vertical;
        transition: border-color 0.2s, box-shadow 0.2s;
      }
      textarea:focus {
        outline: none;
        border-color: var(--color-brand, #3498db);
        box-shadow: 0 0 0 3px var(--color-focus-ring, rgba(52, 152, 219, 0.1));
      }
      textarea:disabled {
        background-color: var(--color-surface-muted, #f5f5f5);
        cursor: not-allowed;
        opacity: 0.6;
      }
      textarea::placeholder {
        color: var(--color-text-muted, #9ca3af);
      }
      .char-count {
        margin-top: 0.25rem;
        font-size: var(--text-xs, 0.75rem);
        color: var(--color-text-muted, #6b7280);
        text-align: right;
      }
      .char-count .current {
        font-weight: 500;
      }
    `;
  }
}

customElements.define('studio-textarea', TextareaElement);
registerComponent('textarea', TextareaElement);
