import { BaseElement } from '../core/BaseElement';

export class ButtonElement extends BaseElement {
  static get observedAttributes() { return ['label']; }

  attributeChangedCallback(name: string, _oldValue: string | null, _newValue: string | null) {
    if (name === 'label') this.requestRender();
  }

  protected render(): string {
    return `<button part="button">${this.getAttribute('label') || 'Button'}</button>`;
  }
  protected styles(): string {
    return `:host { display:inline-block; } button { cursor:pointer; font: inherit; padding: 0.5rem 1rem; background:#1976d2; color:#fff; border:none; border-radius:4px; }
      button:hover { background:#125a9d; } button:active { background:#0d4476; }`;
  }
}

customElements.define('studio-button', ButtonElement);
