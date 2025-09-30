import { LitElement, html, css } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import demoPage from '../../demo/demo-page.json';
import type { PageMeta, ComponentNode } from '../../models/metadata';
import { initStore, currentStore } from '../store/TreeStore';

@customElement('studio-builder-canvas')
export class BuilderCanvas extends LitElement {
  static styles = css`
    :host { display:block; font: 13px/1.4 system-ui, sans-serif; }
    .tree { background:#fff; border:1px solid #e2e8f0; padding:8px; border-radius:4px; }
    .node { padding:2px 4px; margin:2px 0; border-radius:3px; cursor:pointer; display:flex; align-items:center; gap:4px; }
    .node[data-selected='true'] { background:#1e40af; color:#fff; }
    .children { margin-left:14px; border-left:1px dashed #cbd5e1; padding-left:6px; }
    button.inline { font-size:10px; padding:2px 4px; }
    .toolbar { display:flex; gap:4px; margin-bottom:6px; }
  `;

  @state() private page: PageMeta | null = null;
  @state() private selectedId: string | null = null;

  connectedCallback(): void {
    super.connectedCallback();
    if (!currentStore) initStore(demoPage as PageMeta);
    currentStore!.onChange(()=>{
      this.page = currentStore!.getPage();
      this.selectedId = currentStore!.getSelection()?.id || null;
      this.requestUpdate();
    });
    this.page = currentStore!.getPage();
    this.addEventListener('keydown', this.onKeyDown as any);
    this.setAttribute('tabindex','0');
  }

  disconnectedCallback(): void {
    super.disconnectedCallback();
    this.removeEventListener('keydown', this.onKeyDown as any);
  }

  private onKeyDown = (e: KeyboardEvent) => {
    if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase()==='d') {
      if (this.selectedId && this.selectedId !== this.page!.rootId) {
        currentStore?.duplicate(this.selectedId);
        e.preventDefault();
      }
    }
  };

  private select(id: string) { currentStore?.select(id); }
  private addChild(parentId: string) {
    const id = 'n' + Math.random().toString(36).slice(2,7);
    currentStore?.addNode(parentId, { id, type: 'text', props: { text: 'New text' } });
  }
  private remove(id: string) { currentStore?.removeNode(id); }
  private duplicateSelected() {
    if (this.selectedId && this.selectedId !== this.page!.rootId) currentStore?.duplicate(this.selectedId);
  }

  private renderNode(node: ComponentNode) {
    const sel = node.id === this.selectedId;
    return html`
      <div class="node" data-selected=${sel} @click=${(e:Event)=>{e.stopPropagation();this.select(node.id);}}>
        <span>${node.type}</span>
        <button class="inline" @click=${(e:Event)=>{e.stopPropagation();this.addChild(node.id);}}>+</button>
        ${node.id !== this.page!.rootId ? html`<button class="inline" @click=${(e:Event)=>{e.stopPropagation();this.remove(node.id);}}>×</button>`:null}
      </div>
      ${node.children && node.children.length ? html`<div class="children">${node.children.map(cid=>{
        const child = this.page!.nodes.find(n=>n.id===cid)!; return this.renderNode(child);
      })}</div>`:null}
    `;
  }

  render() {
    if (!this.page) return html`<div>Loading...</div>`;
    const root = this.page.nodes.find(n=>n.id===this.page!.rootId)!;
    return html`
      <div class="toolbar">
        <button @click=${()=>currentStore?.undo()}>Undo</button>
        <button @click=${()=>currentStore?.redo()}>Redo</button>
        <button ?disabled=${!this.selectedId || this.selectedId===this.page.rootId} @click=${()=>this.duplicateSelected()}>Duplicate</button>
      </div>
      <div class="tree">${this.renderNode(root)}</div>`;
  }
}
