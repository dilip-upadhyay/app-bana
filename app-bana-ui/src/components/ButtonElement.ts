import { BaseElement } from '../core/BaseElement';
import { registerComponent } from '../core/registry';

export class ButtonElement extends BaseElement {
  static get observedAttributes() { return ['label','variant','size','disabled']; }

  attributeChangedCallback(name: string, _oldValue: string | null, _newValue: string | null) {
    if (['label','variant','size','disabled'].includes(name)) this.requestRender();
  }

  private variantClass(): string {
    const v = (this.getAttribute('variant')||'primary').toLowerCase();
    switch (v) {
      case 'danger': return 'danger';
      case 'outline': return 'outline';
      case 'ghost': return 'ghost';
      case 'primary': default: return 'primary';
    }
  }
  private sizeClass(): string {
    const s = (this.getAttribute('size')||'md').toLowerCase();
    return ['sm','small'].includes(s)? 'sm' : ['lg','large'].includes(s)? 'lg' : 'md';
  }
  protected render(): string {
    const disabled = this.hasAttribute('disabled');
    return `<button part="button" class="btn ${this.variantClass()} ${this.sizeClass()}" ${disabled? 'disabled':''}>${this.getAttribute('label') || 'Button'}</button>`;
  }
  protected styles(): string {
    return `:host { display:inline-block; font-family: var(--font-sans); }
      .btn { font: inherit; font-size: var(--text-sm); line-height: var(--line-height-normal); padding:6px 14px; border-radius: var(--radius-sm); border:1px solid var(--color-border); background: var(--color-surface); color: var(--color-text); display:inline-flex; align-items:center; gap:6px; position:relative; transition: background var(--transition-fast), border-color var(--transition-fast), color var(--transition-fast), box-shadow var(--transition-fast); }
      .btn:hover:not(:disabled) { background: var(--color-surface-alt); }
      .btn:active:not(:disabled) { background: var(--color-brand-muted); }
      .btn:focus-visible { outline:2px solid var(--color-focus-ring); outline-offset:2px; }
      .btn.primary { background: var(--color-brand); border-color: var(--color-brand); color:#fff; }
      .btn.primary:hover:not(:disabled) { background: var(--color-brand-accent); }
      .btn.danger { background: var(--color-danger); border-color: var(--color-danger); color:#fff; }
      .btn.danger:hover:not(:disabled) { filter:brightness(.92); }
      .btn.outline { background: transparent; }
      .btn.outline:hover:not(:disabled) { background: var(--color-surface-alt); }
      .btn.ghost { background: transparent; border-color: transparent; }
      .btn.ghost:hover:not(:disabled) { background: var(--color-brand-muted); }
      .btn.sm { padding:4px 10px; font-size: var(--text-xs); }
      .btn.lg { padding:10px 20px; font-size: var(--text-md); }
      .btn:disabled { opacity:.55; cursor:not-allowed; }
    `;
  }
}

customElements.define('studio-button', ButtonElement);
registerComponent('button', ButtonElement);
