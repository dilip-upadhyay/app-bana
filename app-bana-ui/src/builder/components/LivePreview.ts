import { LitElement, html, css, unsafeCSS } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { currentStore } from '../store/TreeStore';
import type { ComponentNode, PageMeta } from '../../models/metadata';
import styles from './LivePreview.css?inline';

@customElement('studio-live-preview')
export class LivePreview extends LitElement {
  static styles = css`${unsafeCSS(styles)}`;

  @state() private page: PageMeta | null = null;
  @state() private selectedId: string | null = null;
  @state() private hoveredId: string | null = null;
  @state() private dragOverId: string | null = null;
  @state() private dropPosition: 'before' | 'after' | 'inside' | null = null;

  connectedCallback(): void {
    super.connectedCallback();
    if (currentStore) {
      currentStore.onChange(() => {
        this.page = currentStore!.getPage();
        this.selectedId = currentStore!.getSelection()?.id || null;
        this.requestUpdate();
      });
      this.page = currentStore.getPage();
      this.selectedId = currentStore.getSelection()?.id || null;
    }
  }

  private generateUniqueId(base: string): string {
    let id = base;
    let counter = 1;
    while (this.page?.nodes.some(n => n.id === id)) {
      id = `${base}-${counter}`;
      counter++;
    }
    return id;
  }

  private handleDragOver(e: DragEvent, nodeId: string) {
    e.preventDefault();
    e.stopPropagation();
    if (!e.dataTransfer) return;

    e.dataTransfer.dropEffect = 'copy';

    // Calculate drop position based on mouse position
    const target = e.currentTarget as HTMLElement;
    const rect = target.getBoundingClientRect();
    const y = e.clientY - rect.top;
    const height = rect.height;

    // For containers, prefer inside drop
    const node = this.page?.nodes.find(n => n.id === nodeId);
    const isContainer = node && (node.type === 'container' || node.type === 'section' || node.type === 'div');

    if (isContainer && y > height * 0.2 && y < height * 0.8) {
      this.dropPosition = 'inside';
    } else if (y < height * 0.3) {
      this.dropPosition = 'before';
    } else if (y > height * 0.7) {
      this.dropPosition = 'after';
    } else {
      this.dropPosition = 'inside';
    }

    this.dragOverId = nodeId;
  }

  private handleDragLeave(e: DragEvent) {
    e.stopPropagation();
    const target = e.currentTarget as HTMLElement;
    const relatedTarget = e.relatedTarget as HTMLElement;

    // Only clear if we're actually leaving the element
    if (!target.contains(relatedTarget)) {
      this.dragOverId = null;
      this.dropPosition = null;
    }
  }

  private handleDrop(e: DragEvent, targetNodeId: string) {
    e.preventDefault();
    e.stopPropagation();

    if (!currentStore) return;

    try {
      let data: any = null;

      // Try to get data from dataTransfer first
      if (e.dataTransfer) {
        try {
          const jsonData = e.dataTransfer.getData('application/json');
          if (jsonData) {
            data = JSON.parse(jsonData);
          }
        } catch (err) {
          // Try text/plain fallback
          try {
            const textData = e.dataTransfer.getData('text/plain');
            if (textData) {
              data = JSON.parse(textData);
            }
          } catch (err2) {
            console.warn('Failed to parse drag data from dataTransfer');
          }
        }
      }

      // Fallback to global variable (for Shadow DOM issues)
      if (!data && (window as any).__dragData) {
        data = (window as any).__dragData;
        console.log('Using global drag data fallback');
      }

      if (!data || data.action !== 'add-component') {
        console.error('No valid drag data found');
        return;
      }

      const template = data.template;
      const newId = this.generateUniqueId(template.type || 'element');

      const newNode: ComponentNode = {
        id: newId,
        type: template.type || 'container',
        props: template.props || {},
        children: template.children !== undefined ? template.children : undefined
      };

      const targetNode = this.page?.nodes.find(n => n.id === targetNodeId);
      if (!targetNode) return;

      // Determine parent and index based on drop position
      let parentId = targetNodeId;
      let index: number | undefined = undefined;

      if (this.dropPosition === 'before' || this.dropPosition === 'after') {
        // Find parent of target
        const parent = this.page?.nodes.find(n => n.children?.includes(targetNodeId));
        if (parent) {
          parentId = parent.id;
          const targetIndex = parent.children!.indexOf(targetNodeId);
          index = this.dropPosition === 'before' ? targetIndex : targetIndex + 1;
        } else {
          // If no parent found (shouldn't happen), default to inside
          parentId = targetNodeId;
          index = undefined;
        }
      }
      // else 'inside' - use targetNodeId as parent with undefined index (append)

      currentStore.addNode(parentId, newNode, index);
      this.showToast(`✅ Added ${newNode.type}`);

      // Clean up global drag data
      delete (window as any).__dragData;
    } catch (err) {
      console.error('Drop error:', err);
      this.showToast('❌ Failed to add component');
    } finally {
      this.dragOverId = null;
      this.dropPosition = null;
    }
  }

  private handleNodeClick(e: Event, nodeId: string) {
    e.stopPropagation();
    currentStore?.select(nodeId);
  }

  private handleNodeMouseEnter(nodeId: string) {
    this.hoveredId = nodeId;
  }

  private handleNodeMouseLeave() {
    this.hoveredId = null;
  }

  private showToast(message: string) {
    // Simple toast notification
    const toast = document.createElement('div');
    toast.style.cssText = `
      position: fixed;
      bottom: 24px;
      right: 24px;
      padding: 12px 20px;
      background: #111827;
      color: white;
      border-radius: 8px;
      font-size: 14px;
      z-index: 10000;
      animation: slideIn 0.3s ease;
    `;
    toast.textContent = message;
    document.body.appendChild(toast);
    setTimeout(() => {
      toast.style.animation = 'slideOut 0.3s ease';
      setTimeout(() => document.body.removeChild(toast), 300);
    }, 2000);
  }

  private renderNode(node: ComponentNode): any {
    const isSelected = node.id === this.selectedId;
    const isHovered = node.id === this.hoveredId;
    const isDragOver = node.id === this.dragOverId;

    const classes = [
      'canvas-element',
      isSelected ? 'selected' : '',
      isHovered ? 'hovered' : '',
      isDragOver ? 'drag-over' : '',
      isDragOver && this.dropPosition ? `drop-${this.dropPosition}` : ''
    ].filter(Boolean).join(' ');

    const style = node.props?.style || '';

    // Render different node types
    switch (node.type) {
      case 'text':
        const tag = node.props?.tag || 'p';
        const text = node.props?.text || '';
        return html`
          <${tag}
            class="${classes} ${node.props?.className || ''}"
            style="${style}"
            data-node-id="${node.id}"
            @click=${(e: Event) => this.handleNodeClick(e, node.id)}
            @mouseenter=${() => this.handleNodeMouseEnter(node.id)}
            @mouseleave=${() => this.handleNodeMouseLeave()}
            @dragover=${(e: DragEvent) => this.handleDragOver(e, node.id)}
            @dragleave=${(e: DragEvent) => this.handleDragLeave(e)}
            @drop=${(e: DragEvent) => this.handleDrop(e, node.id)}
          >
            ${text}
          </${tag}>
        `;

      case 'button':
        return html`
          <button
            class="${classes} ${node.props?.className || ''}"
            style="${style}"
            data-node-id="${node.id}"
            @click=${(e: Event) => this.handleNodeClick(e, node.id)}
            @mouseenter=${() => this.handleNodeMouseEnter(node.id)}
            @mouseleave=${() => this.handleNodeMouseLeave()}
            @dragover=${(e: DragEvent) => this.handleDragOver(e, node.id)}
            @dragleave=${(e: DragEvent) => this.handleDragLeave(e)}
            @drop=${(e: DragEvent) => this.handleDrop(e, node.id)}
          >
            ${node.props?.text || 'Button'}
          </button>
        `;

      case 'input':
        return html`
          <input
            type="${node.props?.type || 'text'}"
            class="${classes} ${node.props?.className || ''}"
            style="${style}"
            placeholder="${node.props?.placeholder || ''}"
            data-node-id="${node.id}"
            @click=${(e: Event) => this.handleNodeClick(e, node.id)}
            @mouseenter=${() => this.handleNodeMouseEnter(node.id)}
            @mouseleave=${() => this.handleNodeMouseLeave()}
            @dragover=${(e: DragEvent) => this.handleDragOver(e, node.id)}
            @dragleave=${(e: DragEvent) => this.handleDragLeave(e)}
            @drop=${(e: DragEvent) => this.handleDrop(e, node.id)}
          />
        `;

      case 'textarea':
        return html`
          <textarea
            class="${classes} ${node.props?.className || ''}"
            style="${style}"
            placeholder="${node.props?.placeholder || ''}"
            rows="${node.props?.rows || 4}"
            data-node-id="${node.id}"
            @click=${(e: Event) => this.handleNodeClick(e, node.id)}
            @mouseenter=${() => this.handleNodeMouseEnter(node.id)}
            @mouseleave=${() => this.handleNodeMouseLeave()}
            @dragover=${(e: DragEvent) => this.handleDragOver(e, node.id)}
            @dragleave=${(e: DragEvent) => this.handleDragLeave(e)}
            @drop=${(e: DragEvent) => this.handleDrop(e, node.id)}
          ></textarea>
        `;

      case 'img':
        return html`
          <img
            src="${node.props?.src || ''}"
            alt="${node.props?.alt || ''}"
            class="${classes} ${node.props?.className || ''}"
            style="${style}"
            data-node-id="${node.id}"
            @click=${(e: Event) => this.handleNodeClick(e, node.id)}
            @mouseenter=${() => this.handleNodeMouseEnter(node.id)}
            @mouseleave=${() => this.handleNodeMouseLeave()}
            @dragover=${(e: DragEvent) => this.handleDragOver(e, node.id)}
            @dragleave=${(e: DragEvent) => this.handleDragLeave(e)}
            @drop=${(e: DragEvent) => this.handleDrop(e, node.id)}
          />
        `;

      case 'a':
        return html`
          <a
            href="${node.props?.href || '#'}"
            class="${classes} ${node.props?.className || ''}"
            style="${style}"
            data-node-id="${node.id}"
            @click=${(e: Event) => { e.preventDefault(); this.handleNodeClick(e, node.id); }}
            @mouseenter=${() => this.handleNodeMouseEnter(node.id)}
            @mouseleave=${() => this.handleNodeMouseLeave()}
            @dragover=${(e: DragEvent) => this.handleDragOver(e, node.id)}
            @dragleave=${(e: DragEvent) => this.handleDragLeave(e)}
            @drop=${(e: DragEvent) => this.handleDrop(e, node.id)}
          >
            ${node.props?.text || 'Link'}
          </a>
        `;

      case 'container':
      case 'section':
      case 'div':
      default:
        const children = node.children?.map(childId => {
          const childNode = this.page?.nodes.find(n => n.id === childId);
          return childNode ? this.renderNode(childNode) : null;
        });

        const hasChildren = children && children.length > 0;
        const containerClasses = `${classes} ${node.props?.className || ''} ${hasChildren ? '' : 'empty-container'}`;

        return html`
          <div
            class="${containerClasses}"
            style="${style}"
            data-node-id="${node.id}"
            @click=${(e: Event) => this.handleNodeClick(e, node.id)}
            @mouseenter=${() => this.handleNodeMouseEnter(node.id)}
            @mouseleave=${() => this.handleNodeMouseLeave()}
            @dragover=${(e: DragEvent) => this.handleDragOver(e, node.id)}
            @dragleave=${(e: DragEvent) => this.handleDragLeave(e)}
            @drop=${(e: DragEvent) => this.handleDrop(e, node.id)}
          >
            ${hasChildren ? children : html`<div class="drop-zone-hint">Drop components here</div>`}
          </div>
        `;
    }
  }

  render() {
    if (!this.page) {
      return html`<div class="loading">Loading...</div>`;
    }

    const rootNode = this.page.nodes.find(n => n.id === this.page!.rootId);
    if (!rootNode) {
      return html`<div class="error">Root node not found</div>`;
    }

    return html`
      <div class="preview-container">
        <div class="preview-header">
          <h3>Visual Canvas</h3>
          <div class="preview-actions">
            <button class="action-btn" title="Undo">↶</button>
            <button class="action-btn" title="Redo">↷</button>
            <button class="action-btn" title="Preview">👁️</button>
          </div>
        </div>
        <div class="canvas-wrapper">
          <div class="canvas-content">
            ${this.renderNode(rootNode)}
          </div>
        </div>
      </div>
    `;
  }
}
