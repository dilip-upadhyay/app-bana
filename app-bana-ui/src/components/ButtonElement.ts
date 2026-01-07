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
      :host { 
        display: inline-block; 
        margin-bottom: var(--margin-bottom, 0);
      }
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
      .primary { 
        background: var(--color-brand, #3b82f6); 
        color: white; 
        border-color: var(--color-brand, #3b82f6);
      }
      .primary:hover:not(:disabled) { 
        filter: brightness(0.9);
      }
      .secondary { 
        background: var(--color-surface, white); 
        border-color: var(--color-border, #d1d5db); 
        color: var(--color-text, #374151); 
      }
      .secondary:hover:not(:disabled) { 
        background: var(--color-surface-alt, #f3f4f6); 
      }
      .danger { 
        background: var(--color-danger, #ef4444); 
        border-color: var(--color-danger, #ef4444);
        color: white; 
      }
      .danger:hover:not(:disabled) { 
        filter: brightness(0.9);
      }
    `;
  }
}

if (!customElements.get('appbana-button')) {
  customElements.define('appbana-button', ButtonElement);
}
registerComponent('button', ButtonElement, 'appbana-button');
