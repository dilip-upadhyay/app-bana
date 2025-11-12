import { LitElement, html, css, unsafeCSS } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { unsafeStatic, html as staticHtml } from 'lit/static-html.js';
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

  private retryCount = 0;
  private maxRetries = 10; // Maximum 10 retries (1 second total)
  private storeUnsubscribe: (() => void) | null = null;
  private lastStoreInstance: any = null;

  connectedCallback(): void {
    super.connectedCallback();
    console.log('[LivePreview] connectedCallback - currentStore:', currentStore);

    // Set up the store listener and initial state
    this.updateFromStore();

    // Check for store changes periodically (in case store is replaced)
    setInterval(() => {
      if (currentStore !== this.lastStoreInstance) {
        console.log('[LivePreview] Store instance changed, re-subscribing...');
        this.updateFromStore();
      }
    }, 200);

    // Listen for add-component events from ComponentLibrary (for configured components like Grid)
    window.addEventListener('add-component', this.handleAddComponentEvent as EventListener);

    // Listen for keyboard events (Delete/Backspace to remove selected component)
    window.addEventListener('keydown', this.handleKeyDown);
  }

  disconnectedCallback(): void {
    super.disconnectedCallback();
    // Clean up subscription
    if (this.storeUnsubscribe) {
      this.storeUnsubscribe();
      this.storeUnsubscribe = null;
    }
    window.removeEventListener('add-component', this.handleAddComponentEvent as EventListener);
    window.removeEventListener('keydown', this.handleKeyDown);
  }

  private handleKeyDown = (e: KeyboardEvent) => {
    // Delete or Backspace key to remove selected component
    if ((e.key === 'Delete' || e.key === 'Backspace') && this.selectedId) {
      // Don't delete if user is typing in an input
      const target = e.target as HTMLElement;
      if (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA') {
        return;
      }

      e.preventDefault();
      this.handleDeleteSelected();
    }
  }

  private handleAddComponentEvent = (e: CustomEvent) => {
    console.log('[LivePreview] Add component event received:', e.detail);
    const { template } = e.detail;

    // Add to root by default
    const rootId = this.page?.rootId || 'root';
    this.addComponentToNode(rootId, template);
  }

  private addComponentToNode(parentId: string, template: any) {
    if (!currentStore) return;

    const newId = this.generateUniqueId(template.type || 'element');

    // Create node tree structure with proper ID
    const nodeTree = {
      id: newId,
      type: template.type || 'container',
      props: template.props || {},
      children: template.children || []
    };

    console.log('[LivePreview] Adding component tree to node:', parentId, nodeTree);

    // Use addNodeTree which handles recursive children
    currentStore.addNodeTree(parentId, nodeTree);
  }

  private updateFromStore() {
    if (currentStore) {
      this.retryCount = 0; // Reset retry count on success
      this.lastStoreInstance = currentStore;

      // Unsubscribe from old store if exists
      if (this.storeUnsubscribe) {
        this.storeUnsubscribe();
      }

      // Subscribe to new store
      this.storeUnsubscribe = currentStore.onChange(() => {
        this.page = currentStore!.getPage();
        this.selectedId = currentStore!.getSelection()?.id || null;
        this.requestUpdate();
      });

      this.page = currentStore.getPage();
      this.selectedId = currentStore.getSelection()?.id || null;
      console.log('[LivePreview] Initial page loaded:', this.page);
      this.requestUpdate();
    } else {
      if (this.retryCount < this.maxRetries) {
        this.retryCount++;
        console.warn(`[LivePreview] currentStore is null! Retry ${this.retryCount}/${this.maxRetries}...`);
        // Retry after a short delay in case store is being initialized
        setTimeout(() => this.updateFromStore(), 100);
      } else {
        console.error('[LivePreview] currentStore is still null after max retries. Giving up.');
        console.error('[LivePreview] This usually means no pages exist. Create a page to start building.');
      }
    }
  }

  firstUpdated() {
    // Add global drop listeners to ensure drop events are captured
    const canvasContent = this.renderRoot.querySelector('.canvas-content');
    if (canvasContent) {
      console.log('Adding global drop listeners to canvas-content');

      canvasContent.addEventListener('dragover', (e) => {
        e.preventDefault();
        console.log('Canvas dragover');
      });

      canvasContent.addEventListener('drop', (e) => {
        console.log('Canvas drop event captured!', e);
        e.preventDefault();
        e.stopPropagation();

        // Find the closest element with data-node-id
        const target = (e as DragEvent).target as HTMLElement;
        const nodeElement = target.closest('[data-node-id]') as HTMLElement;

        if (nodeElement) {
          const nodeId = nodeElement.getAttribute('data-node-id');
          if (nodeId) {
            console.log('Dropping on node:', nodeId);
            this.handleDrop(e as DragEvent, nodeId);
          }
        } else {
          // Drop on root
          const rootId = this.page?.rootId;
          if (rootId) {
            console.log('Dropping on root');
            this.handleDrop(e as DragEvent, rootId);
          }
        }
      });
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

  private handleDragEnter(e: DragEvent, nodeId: string) {
    e.preventDefault();
    e.stopPropagation();
    console.log('Drag enter:', nodeId);
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
    console.log('DROP EVENT FIRED!', targetNodeId);
    e.preventDefault();
    e.stopPropagation();

    if (!currentStore) {
      console.error('No current store');
      return;
    }

    try {
      let data: any = null;

      console.log('Attempting to retrieve drag data...');

      // Try to get data from dataTransfer first
      if (e.dataTransfer) {
        console.log('DataTransfer available, types:', e.dataTransfer.types);
        try {
          const jsonData = e.dataTransfer.getData('application/json');
          console.log('JSON data:', jsonData);
          if (jsonData) {
            data = JSON.parse(jsonData);
          }
        } catch (err) {
          console.warn('Failed to get application/json:', err);
          // Try text/plain fallback
          try {
            const textData = e.dataTransfer.getData('text/plain');
            console.log('Text data:', textData);
            if (textData) {
              data = JSON.parse(textData);
            }
          } catch (err2) {
            console.warn('Failed to parse text/plain:', err2);
          }
        }
      }

      // Fallback to global variable (for Shadow DOM issues)
      if (!data && (window as any).__dragData) {
        console.log('Using global drag data fallback');
        data = (window as any).__dragData;
      }

      console.log('Final data:', data);

      if (!data || data.action !== 'add-component') {
        console.error('No valid drag data found', data);
        this.showToast('❌ No drag data found');
        return;
      }

      const template = data.template;
      const newId = this.generateUniqueId(template.type || 'element');

      console.log('Creating node:', newId, template);

      const newNode: ComponentNode = {
        id: newId,
        type: template.type || 'container',
        props: template.props || {},
        children: template.children !== undefined ? template.children : undefined
      };

      const targetNode = this.page?.nodes.find(n => n.id === targetNodeId);
      if (!targetNode) {
        console.error('Target node not found:', targetNodeId);
        return;
      }

      // Determine parent and index based on drop position
      let parentId = targetNodeId;
      let index: number | undefined = undefined;

      console.log('Drop position:', this.dropPosition);

      if (this.dropPosition === 'before' || this.dropPosition === 'after') {
        // Find parent of target
        const parent = this.page?.nodes.find(n => n.children?.includes(targetNodeId));
        if (parent) {
          parentId = parent.id;
          const targetIndex = parent.children!.indexOf(targetNodeId);
          index = this.dropPosition === 'before' ? targetIndex : targetIndex + 1;
          console.log('Inserting at parent:', parentId, 'index:', index);
        } else {
          // If no parent found (shouldn't happen), default to inside
          parentId = targetNodeId;
          index = undefined;
          console.log('No parent found, using inside');
        }
      } else {
        console.log('Adding inside:', parentId);
      }
      // else 'inside' - use targetNodeId as parent with undefined index (append)

      console.log('Adding node to store...');
      currentStore.addNode(parentId, newNode, index);
      console.log('Node added successfully!');
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
    const node = currentStore?.getNode(nodeId);
    console.log('[LivePreview] handleNodeClick:', nodeId, node?.type, node);
    if (currentStore) {
      currentStore.select(nodeId);
      console.log('[LivePreview] After select, currentStore.getSelection():', currentStore.getSelection());
    }
  }

  private handleNodeMouseEnter(nodeId: string) {
    this.hoveredId = nodeId;
  }

  private handleNodeMouseLeave() {
    this.hoveredId = null;
  }

  private handleDeleteSelected() {
    if (!this.selectedId || !currentStore || !this.page) {
      return;
    }

    // Don't allow deleting the root node
    if (this.selectedId === this.page.rootId) {
      this.showToast('❌ Cannot delete root container');
      return;
    }

    const node = this.page.nodes.find(n => n.id === this.selectedId);
    if (!node) {
      return;
    }

    // Check if node has children
    const hasChildren = node.children && node.children.length > 0;

    if (hasChildren) {
      // Confirm deletion of node with children
      const confirmed = confirm(`Delete "${node.id}" and all its children?`);
      if (!confirmed) {
        return;
      }
    }

    console.log('[LivePreview] Deleting node:', this.selectedId);

    try {
      currentStore.removeNode(this.selectedId);
      this.showToast(`🗑️ Deleted ${node.type}`);
    } catch (err) {
      console.error('[LivePreview] Failed to delete node:', err);
      this.showToast('❌ Failed to delete component');
    }
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
    const isRoot = node.id === this.page?.rootId;

    const classes = [
      'canvas-element',
      isSelected ? 'selected' : '',
      isHovered ? 'hovered' : '',
      isDragOver ? 'drag-over' : '',
      isDragOver && this.dropPosition ? `drop-${this.dropPosition}` : ''
    ].filter(Boolean).join(' ');

    // Delete icon overlay for selected non-root components
    const deleteOverlay = isSelected && !isRoot ? html`
      <button
        class="delete-overlay"
        @click=${(e: Event) => { e.stopPropagation(); this.handleDeleteSelected(); }}
        title="Delete (Del key)"
      >
        🗑️
      </button>
    ` : '';

    const style = node.props?.style || '';

    // Render different node types
    switch (node.type) {
      case 'text':
        const tag = unsafeStatic(node.props?.tag || 'p');
        const text = node.props?.text || '';
        const className = `${classes} ${node.props?.className || ''}`;

        // Use unsafeStatic for dynamic tag names
        return staticHtml`<${tag}
            class="${className}"
            style="${style}"
            data-node-id="${node.id}"
            @click=${(e: Event) => this.handleNodeClick(e, node.id)}
            @mouseenter=${() => this.handleNodeMouseEnter(node.id)}
            @mouseleave=${() => this.handleNodeMouseLeave()}
            @dragover=${(e: DragEvent) => this.handleDragOver(e, node.id)}
            @dragleave=${(e: DragEvent) => this.handleDragLeave(e)}
            @drop=${(e: DragEvent) => this.handleDrop(e, node.id)}>${text}</${tag}>`;

      case 'button':
        return html`
          <div style="position: relative; display: inline-block; width: max-content;" @click=${(e: Event) => this.handleNodeClick(e, node.id)}>
            ${deleteOverlay}
            <button
              class="${classes} ${node.props?.className || ''}"
              style="${style}"
              data-node-id="${node.id}"
              @mouseenter=${() => this.handleNodeMouseEnter(node.id)}
              @mouseleave=${() => this.handleNodeMouseLeave()}
              @dragover=${(e: DragEvent) => this.handleDragOver(e, node.id)}
              @dragleave=${(e: DragEvent) => this.handleDragLeave(e)}
              @drop=${(e: DragEvent) => this.handleDrop(e, node.id)}
            >
              ${node.props?.text || node.props?.label || 'Button'}
            </button>
          </div>
        `;

      case 'input':
        return html`
          <div style="position: relative; display: inline-block; width: max-content;" @click=${(e: Event) => this.handleNodeClick(e, node.id)}>
            ${deleteOverlay}
            <input
              type="${node.props?.type || 'text'}"
              class="${classes} ${node.props?.className || ''}"
              style="${style}"
              placeholder="${node.props?.placeholder || ''}"
              data-node-id="${node.id}"
              @mouseenter=${() => this.handleNodeMouseEnter(node.id)}
              @mouseleave=${() => this.handleNodeMouseLeave()}
              @dragover=${(e: DragEvent) => this.handleDragOver(e, node.id)}
              @dragleave=${(e: DragEvent) => this.handleDragLeave(e)}
              @drop=${(e: DragEvent) => this.handleDrop(e, node.id)}
            />
          </div>
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
            ${deleteOverlay}
            ${hasChildren ? children : html`<div class="drop-zone-hint">📦 Empty - Drop components here</div>`}
          </div>
        `;
    }
  }

  render() {

    if (!this.page) {
      console.warn('[LivePreview] No page data, showing loading...');
      return html`<div class="loading">Loading...</div>`;
    }

    const rootNode = this.page.nodes.find(n => n.id === this.page!.rootId);
    if (!rootNode) {
      console.error('[LivePreview] Root node not found, rootId:', this.page.rootId);
      return html`<div class="error">Root node not found</div>`;
    }

    return html`
      <div class="preview-container">
        <div class="preview-header">
          <h3>Visual Canvas</h3>
          <div class="preview-actions">
            ${this.selectedId && this.selectedId !== this.page.rootId ? html`
              <button
                class="action-btn delete-btn"
                @click=${() => this.handleDeleteSelected()}
                style="background: #ef4444; color: white; font-weight: 600;"
                title="Delete selected component (Delete key)"
              >
                🗑️ Delete
              </button>
            ` : ''}
            <button
              class="action-btn test-btn"
              @click=${() => this.testAddButton()}
              style="background: #10b981; color: white; font-weight: 600;"
              title="Click to test if adding components works"
            >
              ➕ TEST ADD
            </button>
            <button class="action-btn" title="Undo">↶</button>
            <button class="action-btn" title="Redo">↷</button>
            <button class="action-btn" @click=${this.handlePreview} title="Preview page in runtime">👁️</button>
          </div>
        </div>
        <div class="canvas-wrapper">
          <div class="canvas-content">
            ${this.renderNode(rootNode)}
          </div>
        </div>
        <div style="padding: 12px; background: #f9fafb; border-top: 1px solid #e5e7eb; font-size: 12px; color: #6b7280;">
          💡 <strong>Tip:</strong> Drag components from the left panel and drop them here.
          <br>
          🗑️ <strong>Delete:</strong> Select a component and press <kbd>Delete</kbd> or <kbd>Backspace</kbd> key, or click the 🗑️ button.
          <br>
          <strong>Total nodes:</strong> ${this.page.nodes.length}
        </div>
      </div>
    `;
  }

  private handlePreview = () => {
    if (!this.page) {
      alert('No page loaded to preview');
      return;
    }

    // Get current app context from AppStore
    // We need to import it dynamically since it's not in the imports at the top
    import('../store/AppStore').then(({ appStore }) => {
      const currentApp = appStore.getCurrentApp();
      
      if (!currentApp) {
        alert('No app selected. Please create or select an app first.');
        return;
      }

      // Create runtime state with full app context
      const runtimeState = {
        appId: currentApp.id,
        pageId: this.page!.id,
        mode: 'preview' as const
      };

      // Encode state for URL (base64 encoded JSON)
      const stateParam = btoa(JSON.stringify(runtimeState));
      const previewUrl = `/index.html?state=${stateParam}`;
      
      console.log('[LivePreview] Opening preview with state:', runtimeState);
      window.open(previewUrl, '_blank');
    }).catch((error) => {
      console.error('[LivePreview] Error loading AppStore:', error);
      alert('Error opening preview. Check console for details.');
    });
  }

  private testAddButton() {
    console.log('Test button clicked');
    if (!currentStore) return;

    const newNode: ComponentNode = {
      id: 'test-button-' + Date.now(),
      type: 'button',
      props: { text: 'Test Button', className: 'btn' }
    };

    const rootId = this.page?.rootId;
    if (rootId) {
      console.log('Adding test button to root:', rootId);
      currentStore.addNode(rootId, newNode);
      console.log('Test button added!');
    }
  }
}
