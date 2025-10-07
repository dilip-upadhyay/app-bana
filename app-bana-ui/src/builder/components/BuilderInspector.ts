import { LitElement, html, css } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { currentStore } from '../store/TreeStore';
import type { ComponentNode } from '../../models/metadata';

@customElement('studio-builder-inspector')
export class BuilderInspector extends LitElement {
  static styles = css`
    :host { display:block; font:12px/1.4 system-ui,sans-serif; background:#fff; border:1px solid #e2e8f0; border-radius:4px; padding:8px; }
    h4 { margin:0 0 6px; font-size:13px; }
    label { display:block; font-size:11px; margin-top:6px; text-transform:uppercase; letter-spacing:.5px; color:#334155; }
    input, textarea, select { width:100%; box-sizing:border-box; padding:4px 6px; font:inherit; }
    .empty { font-style:italic; color:#64748b; }
  `;
  @state() private node: ComponentNode | null = null;

  connectedCallback(): void {
    super.connectedCallback();
    currentStore?.onChange(()=>{
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
    if (!this.node) return html`<div class="empty">No selection</div>`;
    const props = this.node.props || {};
    return html`
      <h4>Inspector</h4>
      <div><strong>Type:</strong> ${this.node.type}</div>
      <label>Text / Label</label>
      <input .value=${props.text ?? props.label ?? ''} @input=${(e:Event)=>this.updateProp('text', (e.target as HTMLInputElement).value)} />
      <label>Classes (space separated)</label>
      <input .value=${(this.node.style?.classes||[]).join(' ')} @input=${(e:Event)=>{
        const raw = (e.target as HTMLInputElement).value.trim();
        const parts = raw? raw.split(/\s+/): [];
        // patch via updateProps using synthetic style prop
        (this.node as any).style = { ...(this.node!.style||{}), classes: parts };
        currentStore?.select(this.node!.id); // triggers refresh
      }} />
    `;
  }
}
