import { BaseElement } from '../core/BaseElement';
import { registerComponent } from '../core/registry';

export class ContainerElement extends BaseElement {
  protected render(): string { return `<slot></slot>`; }
  protected styles(): string { return `:host { display:block; padding:1rem; border:1px solid #eee; border-radius:4px; }`; }
}

customElements.define('studio-container', ContainerElement);
registerComponent('container', ContainerElement);
