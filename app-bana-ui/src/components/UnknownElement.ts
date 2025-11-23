import { FormElement } from './FormElement';

export class UnknownElement extends FormElement {
  protected render(): string {
    return `<div style="padding:1rem; border:1px dashed red; color:red;">Unknown Component: ${this.tagName}</div>`;
  }
  protected styles(): string {
    return `:host { display: block; }`;
  }
}

if (!customElements.get('studio-unknown')) {
  customElements.define('studio-unknown', UnknownElement);
}
