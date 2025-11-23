import { FormElement } from './FormElement';
import { registerComponent } from '../core/registry';

export class ButtonElement extends FormElement {
  static get observedAttributes() {
    return ['label', 'variant', 'disabled', 'type'];
  }

  attributeChangedCallback() {
    this.requestRender();
  }

  protected render(): string {
    const label = this.getAttribute('label') || 'Button';
    const variant = this.getAttribute('variant') || 'primary';
    const disabled = this.hasAttribute('disabled');
    const type = this.getAttribute('type') || 'button';

    return `
      <button 
        type="${type}" 
        class="btn ${variant}" 
        ${disabled ? 'disabled' : ''}
        part="button"
      >
        ${label}
      </button>
    `;
  }

  protected styles(): string {
    return `
      :host { display: inline-block; }
      .btn {
        font-family: inherit;
        font-size: 0.875rem;
        font-weight: 500;
        padding: 0.625rem 1.25rem;
        border-radius: 4px;
        border: 1px solid transparent;
        cursor: pointer;
        transition: all 0.2s;
      }
      .btn:disabled { opacity: 0.6; cursor: not-allowed; }
      .primary { background: #3b82f6; color: white; }
      .primary:hover:not(:disabled) { background: #2563eb; }
      .secondary { background: white; border-color: #d1d5db; color: #374151; }
      .secondary:hover:not(:disabled) { background: #f3f4f6; }
      .danger { background: #ef4444; color: white; }
      .danger:hover:not(:disabled) { background: #dc2626; }
    `;
  }
}

if (!customElements.get('studio-button')) {
  customElements.define('studio-button', ButtonElement);
}
registerComponent('button', ButtonElement);
