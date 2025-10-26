import { LitElement, html, css, unsafeCSS } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import demoPage from '../../demo/demo-page.json';
import type { PageMeta, ComponentNode } from '../../models/metadata';
import { initStore, currentStore } from '../store/TreeStore';
import { renderTemplate } from './BuilderCanvas.template';
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

  render() {
    return renderTemplate.call(this);
  }
}
