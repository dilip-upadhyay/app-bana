import { FormElement } from './FormElement';
import { registerComponent } from '../core/registry';

export class ContainerElement extends FormElement {
  static get observedAttributes() {
    return ['layout', 'gap', 'padding'];
  }

  attributeChangedCallback() {
    this.requestRender();
  }

  protected render(): string {
    return `<slot></slot>`;
  }

  protected styles(): string {
    const layout = this.getAttribute('layout') || 'column';
    const gap = this.getAttribute('gap') || '1rem';
    const padding = this.getAttribute('padding') || '1rem';

    return `
      :host {
        display: flex;
        flex-direction: ${layout};
        gap: ${gap};
        padding: ${padding};
      }
    `;
  }
}

if (!customElements.get('appbana-container')) {
  customElements.define('appbana-container', ContainerElement);
}
registerComponent('container', ContainerElement, 'appbana-container');
