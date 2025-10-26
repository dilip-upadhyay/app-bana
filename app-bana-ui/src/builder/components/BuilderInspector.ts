import { LitElement, html, css, unsafeCSS } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { currentStore } from '../store/TreeStore';
import type { ComponentNode } from '../../models/metadata';
import styles from './BuilderInspector.css?inline';

@customElement('studio-builder-inspector')
export class BuilderInspector extends LitElement {
  static styles = css`${unsafeCSS(styles)}`;

  @state() private node: ComponentNode | null = null;

  connectedCallback(): void {
    super.connectedCallback();
    currentStore?.onChange(() => {
      this.node = currentStore?.getSelection() || null;
      this.requestUpdate();
    });
    this.node = currentStore?.getSelection() || null;
  }

  private updateProp(key: string, value: string) {
    if (!this.node) return;
    currentStore?.updateProps(this.node.id, { [key]: value });
  }

  render() {
    if (!this.node) {
      return html`<div class="empty">No selection</div>`;
    }

    const props = this.node.props || {};

    return html`
      <h4>Inspector</h4>
      <div><strong>Type:</strong> ${this.node.type}</div>

      <label>Text / Label</label>
      <input
        .value=${props.text ?? props.label ?? ''}
        @input=${(e: Event) => this.updateProp('text', (e.target as HTMLInputElement).value)} />

      <label>Classes (space separated)</label>
      <input
        .value=${(this.node.style?.classes || []).join(' ')}
        @input=${(e: Event) => {
          const raw = (e.target as HTMLInputElement).value.trim();
          const parts = raw ? raw.split(/\s+/) : [];
          (this.node as any).style = { ...(this.node!.style || {}), classes: parts };
          currentStore?.select(this.node!.id);
        }} />
    `;
  }
}
