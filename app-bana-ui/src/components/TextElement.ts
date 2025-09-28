import { BaseElement } from '../core/BaseElement';
import { registerComponent } from '../core/registry';

export class TextElement extends BaseElement {
  static get observedAttributes() { return ['text']; }

  attributeChangedCallback(name: string, _oldValue: string | null, _newValue: string | null) {
    if (name === 'text') this.requestRender();
  }

  protected render(): string {
    return `<span>${this.getAttribute('text') || ''}</span>`;
  }
}

customElements.define('studio-text', TextElement);
registerComponent('text', TextElement);
