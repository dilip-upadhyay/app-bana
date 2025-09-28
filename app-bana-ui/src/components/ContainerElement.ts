import { BaseElement } from '../core/BaseElement';

export class ContainerElement extends BaseElement {
  protected render(): string { return `<slot></slot>`; }
  protected styles(): string { return `:host { display:block; padding:1rem; border:1px solid #eee; border-radius:4px; }`; }
}

customElements.define('studio-container', ContainerElement);
