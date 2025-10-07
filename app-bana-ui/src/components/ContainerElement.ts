import { BaseElement } from '../core/BaseElement';
import { registerComponent } from '../core/registry';

export class ContainerElement extends BaseElement {
  static get observedAttributes() { return ['variant']; }
  attributeChangedCallback(name: string, _o: string|null, _n: string|null){ if (name==='variant') this.requestRender(); }
  private variant(): string { const v=(this.getAttribute('variant')||'default').toLowerCase(); return ['outlined','subtle','ghost'].includes(v)? v : 'default'; }
  protected render(): string { return `<div class="wrap ${this.variant()}"><slot></slot></div>`; }
  protected styles(): string { return `:host { display:block; }
    .wrap { background: var(--color-surface); border:1px solid var(--color-border); border-radius: var(--radius-md); padding: var(--space-4); box-shadow: var(--shadow-xs); }
    .wrap.outlined { box-shadow:none; }
    .wrap.subtle { background: var(--color-surface-alt); }
    .wrap.ghost { background: transparent; border-color: transparent; box-shadow:none; }
  `; }
}

customElements.define('studio-container', ContainerElement);
registerComponent('container', ContainerElement);
