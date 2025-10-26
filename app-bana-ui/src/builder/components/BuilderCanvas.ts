import { LitElement, html, css, unsafeCSS } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import demoPage from '../../demo/demo-page.json';
import type { PageMeta, ComponentNode } from '../../models/metadata';
import { initStore, currentStore } from '../store/TreeStore';
import styles from './BuilderCanvas.css?inline';

@customElement('studio-builder-canvas')
export class BuilderCanvas extends LitElement {
  static styles = css`${unsafeCSS(styles)}`;

  @state() private page: PageMeta | null = null;
  @state() private selectedId: string | null = null;
  @state() private expanded = new Set<string>();
  @state() private paletteOpen = false;
  @state() private paletteQuery = '';
  @state() private paletteIndex = 0;
  @state() private toast: string | null = null;
  @state() private editingId: string | null = null;
  @state() private editingValue: string = '';
  @state() private dragOverId: string | null = null;

  private toastTimer: any = null;

  connectedCallback(): void {
    super.connectedCallback();
    if (!currentStore) initStore(demoPage as PageMeta);
    currentStore!.onChange(() => {
      this.page = currentStore!.getPage();
      this.selectedId = currentStore!.getSelection()?.id || null;
      this.requestUpdate();
    });
    this.page = currentStore!.getPage();
    this.restoreExpanded();
    this.addEventListener('keydown', this.onKeyDown as any);
    this.setAttribute('tabindex', '0');
  }

  disconnectedCallback(): void {
    super.disconnectedCallback();
    this.removeEventListener('keydown', this.onKeyDown as any);
  }

  private onKeyDown = (e: KeyboardEvent) => {
    if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'd') {
      if (this.selectedId && this.selectedId !== this.page!.rootId) {
        currentStore?.duplicate(this.selectedId);
        e.preventDefault();
        return;
      }
    }
    if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'p') {
      this.openPalette();
      e.preventDefault();
      return;
    }
    if ((e.metaKey || e.ctrlKey) && e.shiftKey && e.key.toLowerCase() === 'c') {
      this.copySelectedId();
      e.preventDefault();
      return;
    }
    if ((e.key === 'Backspace' || e.key === 'Delete') && this.selectedId && this.selectedId !== this.page!.rootId) {
      const node = this.page!.nodes.find(n => n.id === this.selectedId)!;
      const hasChildren = !!(node.children && node.children.length);
      if (!hasChildren || window.confirm(`Delete ${node.id}${hasChildren ? ' and its subtree' : ''}?`)) {
        currentStore?.removeNode(node.id);
      }
      e.preventDefault();
      return;
    }
    if (e.key === 'Enter' && this.selectedId) {
      const node = this.page!.nodes.find(n => n.id === this.selectedId)!;
      if (this.editingId) {
        this.commitEdit();
        e.preventDefault();
        return;
      }
      if (node.type === 'text') {
        this.startEdit(node);
        e.preventDefault();
        return;
      }
    }
    if (e.key === 'Escape') {
      if (this.editingId) {
        this.cancelEdit();
        e.preventDefault();
      } else if (this.paletteOpen) {
        this.closePalette();
        e.preventDefault();
      }
    }
    if (this.paletteOpen) {
      if (e.key === 'ArrowDown') {
        this.movePaletteIndex(1);
        e.preventDefault();
      } else if (e.key === 'ArrowUp') {
        this.movePaletteIndex(-1);
        e.preventDefault();
      } else if (e.key === 'Enter') {
        this.selectPaletteIndex();
        e.preventDefault();
      }
    }
  };

  private startEdit(node: ComponentNode) {
    const text = (node.props?.text) || '';
    this.editingId = node.id;
    this.editingValue = text;
    this.requestUpdate();
    setTimeout(() => {
      const inp = this.renderRoot.querySelector<HTMLInputElement>('.inline-edit');
      inp?.focus();
      inp?.select();
    }, 0);
  }

  private commitEdit() {
    if (!this.editingId) return;
    currentStore?.updateProps(this.editingId, { text: this.editingValue });
    this.editingId = null;
    this.editingValue = '';
  }

  private cancelEdit() {
    this.editingId = null;
    this.editingValue = '';
  }

  private toggleExpand(id: string) {
    if (this.expanded.has(id)) {
      this.expanded.delete(id);
    } else {
      this.expanded.add(id);
    }
    this.persistExpanded();
    this.requestUpdate();
  }

  private persistExpanded() {
    if (!this.page) return;
    try {
      localStorage.setItem(this.expandedKey(), JSON.stringify(Array.from(this.expanded)));
    } catch {}
  }

  private restoreExpanded() {
    if (!this.page) return;
    try {
      const raw = localStorage.getItem(this.expandedKey());
      if (raw) {
        const arr = JSON.parse(raw);
        this.expanded = new Set(arr);
      } else {
        this.expanded = new Set([this.page.rootId]);
      }
    } catch {
      this.expanded = new Set([this.page.rootId]);
    }
  }

  private expandedKey() {
    return `studio.expanded.${this.page?.id}`;
  }

  private openPalette() {
    this.paletteOpen = true;
    this.paletteQuery = '';
    this.paletteIndex = 0;
    this.requestUpdate();
    setTimeout(() => {
      (this.renderRoot.querySelector('.palette input') as HTMLInputElement)?.focus();
    }, 0);
  }

  private closePalette() {
    this.paletteOpen = false;
  }

  private filteredNodes(): ComponentNode[] {
    if (!this.page) return [];
    const q = this.paletteQuery.trim().toLowerCase();
    let list = [...this.page.nodes];
    if (q) {
      list = list.filter(n =>
        n.id.toLowerCase().includes(q) ||
        n.type.toLowerCase().includes(q) ||
        (n.props?.text || '').toLowerCase().includes(q)
      );
    }
    return list.slice(0, 50);
  }

  private movePaletteIndex(d: number) {
    const list = this.filteredNodes();
    if (!list.length) return;
    this.paletteIndex = (this.paletteIndex + d + list.length) % list.length;
    this.requestUpdate();
  }

  private selectPaletteIndex() {
    const list = this.filteredNodes();
    if (!list.length) return;
    const node = list[this.paletteIndex];
    this.select(node.id);
    this.closePalette();
  }

  private copySelectedId() {
    if (!this.selectedId) return;
    try {
      navigator.clipboard?.writeText(this.selectedId);
      this.showToast(`Copied ID: ${this.selectedId}`);
    } catch {
      this.showToast('Copy not supported');
    }
  }

  private showToast(msg: string) {
    this.toast = msg;
    clearTimeout(this.toastTimer);
    this.toastTimer = setTimeout(() => {
      this.toast = null;
      this.requestUpdate();
    }, 2000);
    this.requestUpdate();
  }

  private handleDragStart(e: DragEvent, node: ComponentNode) {
    if (!e.dataTransfer) return;
    e.dataTransfer.setData('text/plain', node.id);
    e.dataTransfer.effectAllowed = 'move';
  }

  private handleDragOver(e: DragEvent, node: ComponentNode) {
    if (!e.dataTransfer) return;
    if (node.type === 'container') {
      e.preventDefault();
      this.dragOverId = node.id;
      this.requestUpdate();
    }
  }

  private handleDragLeave(_e: DragEvent, node: ComponentNode) {
    if (this.dragOverId === node.id) {
      this.dragOverId = null;
      this.requestUpdate();
    }
  }

  private handleDrop(e: DragEvent, node: ComponentNode) {
    if (!e.dataTransfer) return;
    const dragged = e.dataTransfer.getData('text/plain');
    if (!dragged) return;
    if (node.type !== 'container') return;
    if (dragged === node.id) return;
    this.dragOverId = null;
    currentStore?.moveNode(dragged, node.id);
    this.requestUpdate();
    e.preventDefault();
  }

  private select(id: string) {
    currentStore?.select(id);
  }

  private addChild(parentId: string) {
    const id = 'n' + Math.random().toString(36).slice(2, 7);
    currentStore?.addNode(parentId, { id, type: 'text', props: { text: 'New text' } });
  }

  private deleteNode(id: string) {
    currentStore?.removeNode(id);
  }

  private duplicateSelected() {
    if (this.selectedId && this.selectedId !== this.page!.rootId) {
      currentStore?.duplicate(this.selectedId);
    }
  }

  private renderNode(node: ComponentNode): any {
    const sel = node.id === this.selectedId;
    const expanded = this.expanded.has(node.id);
    const childCount = node.children?.length || 0;
    const isEditing = this.editingId === node.id;

    return html`
      <div
        class="node ${this.dragOverId === node.id ? 'drag-over' : ''}"
        data-selected=${sel}
        role="treeitem"
        aria-selected=${sel}
        aria-expanded=${childCount ? String(expanded) : undefined}
        draggable=${node.id !== this.page!.rootId}
        @dragstart=${(e: DragEvent) => this.handleDragStart(e, node)}
        @dragover=${(e: DragEvent) => this.handleDragOver(e, node)}
        @dragleave=${(e: DragEvent) => this.handleDragLeave(e, node)}
        @drop=${(e: DragEvent) => this.handleDrop(e, node)}
        @click=${(e: Event) => { e.stopPropagation(); this.select(node.id); }}>

        ${childCount
          ? html`<button
              class="expand-btn"
              aria-label="${expanded ? 'Collapse' : 'Expand'} ${node.id}"
              @click=${(e: Event) => { e.stopPropagation(); this.toggleExpand(node.id); }}>
              ${expanded ? '▾' : '▸'}
            </button>`
          : html`<span class="expand-btn" style="opacity:.4;">•</span>`}

        ${isEditing
          ? html`<input
              class="inline-edit"
              .value=${this.editingValue}
              @input=${(e: Event) => this.editingValue = (e.target as HTMLInputElement).value}
              @keydown=${(e: KeyboardEvent) => {
                if (e.key === 'Enter') {
                  this.commitEdit();
                } else if (e.key === 'Escape') {
                  this.cancelEdit();
                }
              }}
              @blur=${() => this.commitEdit()} />`
          : html`<span>${node.type === 'text' ? (node.props?.text || '<text>') : node.type}</span>`}

        <button
          class="inline"
          title="Add child"
          @click=${(e: Event) => { e.stopPropagation(); this.addChild(node.id); }}>
          +
        </button>

        ${node.id !== this.page!.rootId
          ? html`<button
              class="inline"
              title="Delete"
              @click=${(e: Event) => {
                e.stopPropagation();
                if (!node.children?.length || window.confirm('Delete ' + node.id + ' and its subtree?')) {
                  this.deleteNode(node.id);
                }
              }}>
              ×
            </button>`
          : null}
      </div>

      ${childCount && expanded
        ? html`<div class="children" role="group">
            ${node.children!.map(cid => {
              const child = this.page!.nodes.find(n => n.id === cid)!;
              return this.renderNode(child);
            })}
          </div>`
        : null}
    `;
  }

  render() {
    if (!this.page) return html`<div>Loading...</div>`;

    const root = this.page.nodes.find(n => n.id === this.page!.rootId)!;
    const paletteList = this.paletteOpen ? this.filteredNodes() : [];

    return html`
      <div class="toolbar">
        <button @click=${() => currentStore?.undo()}>Undo</button>
        <button @click=${() => currentStore?.redo()}>Redo</button>
        <button
          ?disabled=${!this.selectedId || this.selectedId === this.page.rootId}
          @click=${() => this.duplicateSelected()}>
          Duplicate
        </button>
        <button @click=${() => this.openPalette()}>Search (⌘/Ctrl+P)</button>
      </div>

      <div
        class="tree"
        role="tree"
        aria-label="Component tree"
        @click=${() => this.select(this.page!.rootId)}>
        ${this.renderNode(root)}
      </div>

      ${this.paletteOpen
        ? html`
          <div class="palette-backdrop" @click=${() => this.closePalette()}>
            <div
              class="palette"
              role="dialog"
              aria-modal="true"
              aria-label="Search nodes"
              @click=${(e: Event) => e.stopPropagation()}>

              <header id="palette-header">
                <span>Find Node</span>
                <button
                  class="inline"
                  style="font-size:11px"
                  @click=${() => this.closePalette()}>
                  Esc
                </button>
              </header>

              <input
                aria-label="Filter nodes"
                placeholder="Filter by id / type / text"
                .value=${this.paletteQuery}
                @input=${(e: Event) => {
                  this.paletteQuery = (e.target as HTMLInputElement).value;
                  this.paletteIndex = 0;
                }} />

              <ul role="listbox" aria-label="Search results">
                ${paletteList.map((n, i) => html`
                  <li
                    role="option"
                    aria-selected=${i === this.paletteIndex}
                    class=${i === this.paletteIndex ? 'active' : ''}
                    @click=${() => {
                      this.paletteIndex = i;
                      this.selectPaletteIndex();
                    }}>
                    <span>${n.id}</span>
                    <span class="badge">${n.type}</span>
                  </li>
                `)}
                ${!paletteList.length
                  ? html`<li style="opacity:.6; cursor:default;" aria-disabled="true">No matches</li>`
                  : null}
              </ul>

              <div style="padding:4px 8px; font-size:10px; border-top:1px solid #e2e8f0; display:flex; justify-content:space-between;">
                <span>↑↓ navigate • Enter select • Esc close</span>
                <span>Shift+⌘/Ctrl+C copy ID</span>
              </div>
            </div>
          </div>`
        : null}

      ${this.toast
        ? html`<div class="toast" role="status" aria-live="polite">${this.toast}</div>`
        : null}

      <div class="sr-live" aria-live="polite">${this.toast || ''}</div>
    `;
  }
}
