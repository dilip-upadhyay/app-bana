import { BaseElement } from '../core/BaseElement';

export class UnknownElement extends BaseElement {
  static get observedAttributes() { return ['data-type']; }
  protected render(): string {
    const t = this.getAttribute('data-type') || 'unknown';
    return `<div part="unknown">Unknown component: <code>${t}</code></div>`;
  }
  protected styles(): string { return `:host{display:block;border:1px dashed #d33;padding:.5rem;font:12px/1.4 monospace;background:#fff5f5;color:#b00}`; }
}

customElements.define('studio-unknown', UnknownElement);

