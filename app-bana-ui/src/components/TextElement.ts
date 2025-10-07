import { BaseElement } from '../core/BaseElement';
import { registerComponent } from '../core/registry';

export class TextElement extends BaseElement {
  static get observedAttributes() { return ['text']; }

  attributeChangedCallback(name: string, _oldValue: string | null, newValue: string | null) {
    super.attributeChangedCallback?.(name, _oldValue, newValue);
    if (name === 'text') { this.textContent = newValue || ''; this.requestRender(); }
  }

  protected render(): string {
    return `<span>${this.getAttribute('text') || ''}</span>`;
  }
}

customElements.define('studio-text', TextElement);
registerComponent('text', TextElement);
