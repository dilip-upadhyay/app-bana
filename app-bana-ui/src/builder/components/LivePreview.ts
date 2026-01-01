import { LitElement, html, css, unsafeCSS } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { unsafeStatic, html as staticHtml } from 'lit/static-html.js';
import { currentStore } from '../store/TreeStore';
import { createRow, apiClient } from '../../core/api-client';
import type { ComponentNode, PageMeta } from '../../models/metadata';
import styles from './LivePreview.css?inline';
import { AuthService } from '../../pages/auth/auth-service';

// Import custom components to ensure they are registered in the registry and customElements
import '../../components/ContainerElement';
import '../../components/FormContainer';
import '../../components/InputElement';
import '../../components/SelectElement';
import '../../components/TextareaElement';
import '../../components/TextElement';
import '../../components/ButtonElement';
import '../../components/HTMLElements'; // For header, text, etc.
import '../../components/GridElement';  // For app-grid
import '../../runtime/renderer/StudioTableLive'; // For studio-table-live
import '../../components/CheckboxElement';
import '../../components/RadioGroupElement';



@customElement('appbana-live-preview')
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
    // Check path for inputs to handle Shadow DOM retargeting
    const path = e.composedPath();
    const target = path.length > 0 ? (path[0] as HTMLElement) : (e.target as HTMLElement);

    if (
      target.tagName === 'INPUT' ||
      target.tagName === 'TEXTAREA' ||
      target.tagName === 'SELECT' ||
      target.isContentEditable
    ) {
      return;
    }

    // Delete or Backspace key to remove selected component
    if ((e.key === 'Delete' || e.key === 'Backspace') && this.selectedId) {
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

    // Prevent direct drops on app-grid (gaps), but ALLOW if over a shadow-cell
    const node = this.page?.nodes.find(n => n.id === nodeId);
    if (node?.type === 'app-grid') {
      // Check Shadow DOM to see if we are over a cell
      const gridEl = this.shadowRoot?.querySelector(`[data-node-id="${nodeId}"]`) as HTMLElement;
      // Note: LivePreview's renderNode renders inside LivePreview's shadowRoot or light DOM?
      // LivePreview is a LitElement, so it has a shadowRoot. The app-grid is inside it.
      // But we need the app-grid element itself.
      // Let's find it in the DOM.
      const appGrid = (e.target as HTMLElement).closest('app-grid');

      if (appGrid && appGrid.shadowRoot) {
        const shadowEl = appGrid.shadowRoot.elementFromPoint(e.clientX, e.clientY);
        const cell = shadowEl?.closest('.grid-cell');

        if (cell) {
          // We are over a cell! Allow drop, but conceptually we want to target the cell node, not the grid.
          // However, handleDragOver is called with grid ID.
          // We can't change the dragged ID here easily for the *caller*, 
          // but we can set dropEffect = 'copy' to indicate it's valid.
          e.dataTransfer.dropEffect = 'copy';
          this.dropPosition = 'inside';
          this.dragOverId = nodeId; // We still track grid as the ID for now, handleDrop will retarget
          return;
        }
      }

      // If not over a cell (e.g. over gap), block
      e.dataTransfer.dropEffect = 'none';
      this.dragOverId = null;
      return;
    }
    const target = e.currentTarget as HTMLElement;
    const rect = target.getBoundingClientRect();
    const y = e.clientY - rect.top;
    const height = rect.height;

    // For containers, prefer inside drop
    const isContainer = node && (node.type === 'container' || node.type === 'section' || node.type === 'div');
    const isGridCell = isContainer && node.props?.['data-cell-index'] !== undefined;

    if (isGridCell) {
      // Grid cells are fixed structure; always drop inside
      this.dropPosition = 'inside';
    } else if (isContainer && y > height * 0.2 && y < height * 0.8) {
      this.dropPosition = 'inside';
    } else if (y < height * 0.3) {
      this.dropPosition = 'before';
    } else if (y > height * 0.7) {
      this.dropPosition = 'after';
    } else {
      this.dropPosition = 'inside'; // Default fallback
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
      const nestedNodes: ComponentNode[] = (template as any).nestedNodes || [];
      const newRootId = this.generateUniqueId(template.type || 'element');

      console.log('Creating node:', newRootId, template);

      // Map for potentially re-linking internal refs (not strictly needed for simple grid but good practice)
      const idMap = new Map<string, string>();
      idMap.set(template.id || 'root', newRootId);
      nestedNodes.forEach(node => {
        idMap.set(node.id, this.generateUniqueId(node.type));
      });

      // Construct Root Node (Grid)
      // Start with empty children if we have nested nodes, so we can add them safely via addNode
      const childrenIds = nestedNodes.length > 0 ? undefined : template.children;

      const newNode: ComponentNode = {
        id: newRootId,
        type: template.type || 'container',
        props: template.props || {},
        children: childrenIds
      };

      const targetNode = this.page?.nodes.find(n => n.id === targetNodeId);
      if (!targetNode) {
        console.error('Target node not found:', targetNodeId);
        return;
      }

      // Prevent dropping directly into app-grid (must drop in cells)
      // BUT check if we can retarget to a cell first
      if (targetNode.type === 'app-grid') {
        const appGrid = this.shadowRoot?.querySelector(`[data-node-id="${targetNodeId}"]`);
        let retargetedCellId: string | null = null;
        let autoCreatedCellNode: ComponentNode | null = null;

        if (appGrid && appGrid.shadowRoot) {
          const shadowEl = appGrid.shadowRoot.elementFromPoint(e.clientX, e.clientY);
          const cell = shadowEl?.closest('.grid-cell');
          if (cell) {
            const cellIndex = cell.getAttribute('data-cell');
            if (cellIndex !== null) {
              const childId = targetNode.children?.find(childId => {
                const child = this.page?.nodes.find(n => n.id === childId);
                return child?.props?.slot === `cell-${cellIndex}`;
              });

              if (childId) {
                retargetedCellId = childId;
                console.log(`Retargeting drop from Grid to Cell: ${retargetedCellId}`);
              } else {
                // Auto-repair: Cell container missing? Create it! 
                console.log(`Cell container for slot cell-${cellIndex} missing. Auto-creating...`);
                const newCellId = this.generateUniqueId('container');
                const newCellNode: ComponentNode = {
                  id: newCellId,
                  type: 'container',
                  props: {
                    className: 'grid-cell',
                    slot: `cell-${cellIndex}`,
                    style: `min-height: 100px; padding: 0.5rem; display: flex; flex-direction: column; gap: 0.5rem;`,
                    'data-cell-index': cellIndex
                  },
                  children: []
                };
                autoCreatedCellNode = newCellNode;
                retargetedCellId = newCellId;
              }
            }
          }
        }

        if (retargetedCellId) {
          if (autoCreatedCellNode) {
            // If we auto-generated a cell, add it AND the component directly
            if (currentStore) {
              // Add the Cell to the Grid
              currentStore.addNode(targetNodeId, autoCreatedCellNode);
              // Add the Component to the Cell
              currentStore.addNode(autoCreatedCellNode.id, newNode);
              this.showToast('✨ Auto-repaired grid cell & Added component');
              console.log('Node added successfully via auto-repair!');
            }
            delete (window as any).__dragData;
            this.dragOverId = null;
            this.dropPosition = null;
            return;
          } else {
            // Existing cell found, standard recursion
            this.handleDrop(e, retargetedCellId);
            return;
          }
        }

        this.showToast('⚠️ Please drop INSIDE a grid cell box');
        return;
      }

      // Determine parent and index based on drop position
      let parentId = targetNodeId;
      let index: number | undefined = undefined;

      console.log('Drop position:', this.dropPosition);

      if (this.dropPosition === 'before' || this.dropPosition === 'after') {
        const parent = this.page?.nodes.find(n => n.children?.includes(targetNodeId));
        if (parent) {
          parentId = parent.id;
          const targetIndex = parent.children!.indexOf(targetNodeId);
          index = this.dropPosition === 'before' ? targetIndex : targetIndex + 1;
        } else {
          parentId = targetNodeId;
          index = undefined;
        }
      }

      console.log('Adding node to store...');
      if (currentStore) {
        // Add Root Node
        currentStore.addNode(parentId, newNode, index);

        // Add Nested Nodes (Cells)
        if (nestedNodes.length > 0) {
          nestedNodes.forEach(oldNode => {
            const newChildId = idMap.get(oldNode.id);
            if (newChildId) {
              const newChildNode: ComponentNode = {
                ...oldNode,
                id: newChildId,
                props: JSON.parse(JSON.stringify(oldNode.props || {}))
              };
              // Add as child of the New Grid
              currentStore?.addNode(newRootId, newChildNode);
            }
          });
        }

        console.log('Node added successfully!');
        this.showToast(`✅ Added ${newNode.type}`);
      }

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


  private async handleAction(node: ComponentNode) {
    const props = node.props || {};
    const actionType = props.actionType;

    if (!actionType || actionType === 'none') {
      this.showToast('ℹ️ No action configured');
      return;
    }

    console.log('[LivePreview] Executing action:', actionType, props);

    try {
      if (actionType === 'save-entity') {
        const entity = props.entity;
        if (!entity) {
          this.showToast('⚠️ No entity selected for Save action');
          return;
        }

        // Collect data from inputs
        const data: Record<string, any> = {};

        // Helper to gather inputs from the rendered DOM
        // We look for any input/select/textarea with a "name" attribute
        const gatherInputs = (root: any) => {
          const inputs = root.querySelectorAll('appbana-input, appbana-select, appbana-textarea, appbana-checkbox, appbana-radio-group, input, select, textarea');
          inputs.forEach((el: any) => {
            const name = el.name || el.getAttribute('name');
            if (name) {
              // Handle different element types
              if (el.tagName.toLowerCase().includes('checkbox')) {
                data[name] = el.checked;
              } else {
                data[name] = el.value;
              }
            }
          });
        };

        gatherInputs(this.renderRoot);

        console.log('[LivePreview] Saving data for entity', entity, data);

        // Call API
        await createRow(entity, data);

        this.showToast(`✅ Saved ${entity} successfully!`);

        if (props.onSuccess === 'navigate' && props.navigateUrl) {
          // Simulate navigation or real nav
          this.showToast(`➡️ Navigating to ${props.navigateUrl}...`);
          window.history.pushState({}, '', props.navigateUrl);
        } else if (props.onSuccess === 'refresh') {
          // Reload page from store to "refresh" (or just re-fetch if we had data binding)
          this.updateFromStore();
        }

      } else if (actionType === 'navigate') {
        if (props.navigateUrl) {
          window.history.pushState({}, '', props.navigateUrl);
          this.showToast(`➡️ Navigated to ${props.navigateUrl}`);
        }
      } else if (actionType === 'api') {
        if (props.apiEndpoint) {
          const method = props.apiMethod || 'POST';
          await apiClient.request(props.apiEndpoint, { method });
          this.showToast(`✅ API ${method} Success`);
        }
      }

    } catch (err: any) {
      console.error('[LivePreview] Action failed:', err);
      this.showToast(`❌ Action failed: ${err.message || 'Unknown error'}`);
    }
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

    // Always confirm deletion
    const hasChildren = node.children && node.children.length > 0;
    const message = hasChildren
      ? `Delete "${node.type}" (${node.id}) and all its children?`
      : `Delete "${node.type}" (${node.id})?`;

    if (!confirm(message)) {
      return;
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
        return html`
          <div style="position: relative; display: block;" @click=${(e: Event) => this.handleNodeClick(e, node.id)}>
            ${deleteOverlay}
            <appbana-text
              class="${classes} ${node.props?.className || ''}"
              style="${style}"
              content="${node.props?.content || node.props?.text || ''}"
              variant="${node.props?.variant || 'body'}"
              align="${node.props?.align || 'left'}"
              color="${node.props?.color || ''}"
              tag="${node.props?.tag || 'p'}"
              data-node-id="${node.id}"
              @mouseenter=${() => this.handleNodeMouseEnter(node.id)}
              @mouseleave=${() => this.handleNodeMouseLeave()}
              @dragover=${(e: DragEvent) => this.handleDragOver(e, node.id)}
              @dragleave=${(e: DragEvent) => this.handleDragLeave(e)}
              @drop=${(e: DragEvent) => this.handleDrop(e, node.id)}
            ></appbana-text>
            <div style="position: absolute; inset: 0; cursor: pointer; z-index: 10;" 
                 @click=${(e: Event) => this.handleNodeClick(e, node.id)}></div>
          </div>
        `;

      case 'button':
        return html`
          <div style="position: relative; display: inline-block; width: max-content;" 
               @click=${(e: MouseEvent) => {
            // If Alt key is pressed, run action instead of selecting
            if (e.altKey || (node.props?.actionType && node.props?.actionType !== 'none' && !this.selectedId)) {
              e.stopPropagation();
              this.handleAction(node);
            } else {
              this.handleNodeClick(e, node.id);
            }
          }}>
            ${deleteOverlay}
            <appbana-button
              class="${classes} ${node.props?.className || ''}"
              style="${style}"
              label="${node.props?.label || node.props?.text || 'Button'}"
              type="${node.props?.type || 'button'}"
              variant="${node.props?.variant || 'primary'}"
              ?disabled=${node.props?.disabled}
              data-node-id="${node.id}"
            ></appbana-button>
            <div style="position: absolute; inset: 0; cursor: pointer; z-index: 10;" 
                 title="Click to select. Alt+Click to run action."
                 ></div>
          </div>
        `;

      case 'input':
        return html`
          <div style="position: relative; display: inline-block; width: 100%;" @click=${(e: Event) => this.handleNodeClick(e, node.id)}>
            ${deleteOverlay}
            <appbana-input
              class="${classes} ${node.props?.className || ''}"
              style="${style}"
              label="${node.props?.label || ''}"
              type="${node.props?.type || 'text'}"
              placeholder="${node.props?.placeholder || ''}"
              value="${node.props?.value || ''}"
              name="${node.props?.name || ''}"
              ?required=${node.props?.required}
              ?readonly=${node.props?.readonly || true} 
              data-node-id="${node.id}"
              @mouseenter=${() => this.handleNodeMouseEnter(node.id)}
              @mouseleave=${() => this.handleNodeMouseLeave()}
              @dragover=${(e: DragEvent) => this.handleDragOver(e, node.id)}
              @dragleave=${(e: DragEvent) => this.handleDragLeave(e)}
              @drop=${(e: DragEvent) => this.handleDrop(e, node.id)}
            ></appbana-input>
            <div style="position: absolute; inset: 0; cursor: pointer; z-index: 10;" 
                 @click=${(e: Event) => this.handleNodeClick(e, node.id)}></div>
          </div>
        `;

      case 'select':
        return html`
          <div style="position: relative; display: inline-block; width: 100%;" @click=${(e: Event) => this.handleNodeClick(e, node.id)}>
            ${deleteOverlay}
            <appbana-select
              class="${classes} ${node.props?.className || ''}"
              style="${style}"
              label="${node.props?.label || ''}"
              name="${node.props?.name || ''}"
              value="${node.props?.value || ''}"
              options="${typeof node.props?.options === 'string' ? node.props.options : JSON.stringify(node.props?.options || [])}"
              placeholder="${node.props?.placeholder || ''}"
              ?required=${node.props?.required}
              data-node-id="${node.id}"
              @mouseenter=${() => this.handleNodeMouseEnter(node.id)}
              @mouseleave=${() => this.handleNodeMouseLeave()}
              @dragover=${(e: DragEvent) => this.handleDragOver(e, node.id)}
              @dragleave=${(e: DragEvent) => this.handleDragLeave(e)}
              @drop=${(e: DragEvent) => this.handleDrop(e, node.id)}
            ></appbana-select>
            <!-- Overlay to intercept clicks for selection -->
            <div style="position: absolute; inset: 0; cursor: pointer; z-index: 10;" 
                 @click=${(e: Event) => this.handleNodeClick(e, node.id)}></div>
          </div>
        `;

      case 'textarea':
        return html`
          <div style="position: relative; display: inline-block; width: 100%;" @click=${(e: Event) => this.handleNodeClick(e, node.id)}>
            ${deleteOverlay}
            <appbana-textarea
              class="${classes} ${node.props?.className || ''}"
              style="${style}"
              label="${node.props?.label || ''}"
              placeholder="${node.props?.placeholder || ''}"
              value="${node.props?.value || ''}"
              name="${node.props?.name || ''}"
              rows="${node.props?.rows || 4}"
              ?required=${node.props?.required}
              ?readonly=${node.props?.readonly || true}
              data-node-id="${node.id}"
              @mouseenter=${() => this.handleNodeMouseEnter(node.id)}
              @mouseleave=${() => this.handleNodeMouseLeave()}
              @dragover=${(e: DragEvent) => this.handleDragOver(e, node.id)}
              @dragleave=${(e: DragEvent) => this.handleDragLeave(e)}
              @drop=${(e: DragEvent) => this.handleDrop(e, node.id)}
            ></appbana-textarea>
            <div style="position: absolute; inset: 0; cursor: pointer; z-index: 10;" 
                 @click=${(e: Event) => this.handleNodeClick(e, node.id)}></div>
          </div>
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

      case 'checkbox':
        return html`
          <div style="position: relative; display: inline-block; width: 100%;" @click=${(e: Event) => this.handleNodeClick(e, node.id)}>
            ${deleteOverlay}
            <appbana-checkbox
              class="${classes} ${node.props?.className || ''}"
              style="${style}"
              label="${node.props?.label || ''}"
              name="${node.props?.name || ''}"
              value="${node.props?.value || 'on'}"
              ?checked=${node.props?.checked}
              ?disabled=${node.props?.disabled}
              data-node-id="${node.id}"
              @mouseenter=${() => this.handleNodeMouseEnter(node.id)}
              @mouseleave=${() => this.handleNodeMouseLeave()}
              @dragover=${(e: DragEvent) => this.handleDragOver(e, node.id)}
              @dragleave=${(e: DragEvent) => this.handleDragLeave(e)}
              @drop=${(e: DragEvent) => this.handleDrop(e, node.id)}
            ></appbana-checkbox>
            <div style="position: absolute; inset: 0; cursor: pointer; z-index: 10;" 
                 @click=${(e: Event) => this.handleNodeClick(e, node.id)}></div>
          </div>
        `;

      case 'radio-group':
        return html`
          <div style="position: relative; display: inline-block; width: 100%;" @click=${(e: Event) => this.handleNodeClick(e, node.id)}>
            ${deleteOverlay}
            <appbana-radio-group
              class="${classes} ${node.props?.className || ''}"
              style="${style}"
              label="${node.props?.label || ''}"
              name="${node.props?.name || ''}"
              value="${node.props?.value || ''}"
              options="${typeof node.props?.options === 'string' ? node.props.options : JSON.stringify(node.props?.options || [])}"
              layout="${node.props?.layout || 'vertical'}"
              ?required=${node.props?.required}
              ?disabled=${node.props?.disabled}
              data-node-id="${node.id}"
              @mouseenter=${() => this.handleNodeMouseEnter(node.id)}
              @mouseleave=${() => this.handleNodeMouseLeave()}
              @dragover=${(e: DragEvent) => this.handleDragOver(e, node.id)}
              @dragleave=${(e: DragEvent) => this.handleDragLeave(e)}
              @drop=${(e: DragEvent) => this.handleDrop(e, node.id)}
            ></appbana-radio-group>
            <div style="position: absolute; inset: 0; cursor: pointer; z-index: 10;" 
                 @click=${(e: Event) => this.handleNodeClick(e, node.id)}></div>
          </div>
        `;

      case 'table':
      case 'grid':
      case 'appbana-table-live':
        // Map props correctly or pass entire node if component supports it (Renderer passes node)
        // StudioTableLive takes properties directly or via accessors. Renderer.ts passes .node={nodeWithData}
        // But StudioTableLive definition (JSON) shows props: entity, fields...
        // We'll pass the whole node object as a property if supported, or individual props.
        // Renderer.ts does: html`<studio-table-live .node=${nodeWithData}></studio-table-live>`
        // Lit in LivePreview might not support .node property binding easily on custom element without explicit support?
        // studio-table-live likely has a 'node' setter.

        return html`
          <div style="position: relative; display: block; width: 100%; min-height: 200px;" @click=${(e: Event) => this.handleNodeClick(e, node.id)}>
            ${deleteOverlay}
            <appbana-table-live
              class="${classes} ${node.props?.className || ''}"
              style="${style}"
              .node=${node}
              entity="${node.props?.entity || ''}"
              view-mode="${node.props?.viewMode || 'dynamic'}"
              theme="${node.props?.theme || 'default'}"
              data-node-id="${node.id}"
              @mouseenter=${() => this.handleNodeMouseEnter(node.id)}
              @mouseleave=${() => this.handleNodeMouseLeave()}
              @dragover=${(e: DragEvent) => this.handleDragOver(e, node.id)}
              @dragleave=${(e: DragEvent) => this.handleDragLeave(e)}
              @drop=${(e: DragEvent) => this.handleDrop(e, node.id)}
            ></appbana-table-live>
             <div style="position: absolute; inset: 0; cursor: pointer; z-index: 10;" 
                  @click=${(e: Event) => this.handleNodeClick(e, node.id)}></div>
          </div>
        `;

      case 'form':
      case 'studio-form':
        const formChildren = node.children?.map(childId => {
          const childNode = this.page?.nodes.find(n => n.id === childId);
          return childNode ? this.renderNode(childNode) : null;
        });

        return html`
          <div style="position: relative; display: block; width: 100%;" @click=${(e: Event) => this.handleNodeClick(e, node.id)}>
            ${deleteOverlay}
            <studio-form
              class="${classes} ${node.props?.className || ''}"
              style="${style}"
              entity="${node.props?.entity || ''}"
              record-id="${node.props?.['record-id'] || ''}"
              data-node-id="${node.id}"
              @mouseenter=${() => this.handleNodeMouseEnter(node.id)}
              @mouseleave=${() => this.handleNodeMouseLeave()}
              @dragover=${(e: DragEvent) => this.handleDragOver(e, node.id)}
              @dragleave=${(e: DragEvent) => this.handleDragLeave(e)}
              @drop=${(e: DragEvent) => this.handleDrop(e, node.id)}
            >
              ${formChildren}
            </studio-form>
             <div style="position: absolute; top:0; left:0; right:0; height: 20px; cursor: pointer; z-index: 10;" 
                  @click=${(e: Event) => this.handleNodeClick(e, node.id)} title="Select Form"></div>
          </div>
        `;

      case 'app-grid':
        const gridChildren = node.children?.map(childId => {
          const childNode = this.page?.nodes.find(n => n.id === childId);
          return childNode ? this.renderNode(childNode) : null;
        });

        return html`
          <div style="position: relative; display: block; width: 100%;" @click=${(e: Event) => this.handleNodeClick(e, node.id)}>
            ${deleteOverlay}
            <app-grid
              class="${classes} ${node.props?.className || ''}"
              style="${style}"
              cols="${node.props?.cols || 2}"
              rows="${node.props?.rows || 2}"
              gap="${node.props?.gap || '16px'}"
              .minCellHeight="${node.props?.minCellHeight || 'auto'}"
              data-node-id="${node.id}"
              @mouseenter=${() => this.handleNodeMouseEnter(node.id)}
              @mouseleave=${() => this.handleNodeMouseLeave()}
              @dragover=${(e: DragEvent) => this.handleDragOver(e, node.id)}
              @dragleave=${(e: DragEvent) => this.handleDragLeave(e)}
              @drop=${(e: DragEvent) => this.handleDrop(e, node.id)}
            >
              ${gridChildren}
            </app-grid>
          </div>
        `;

      case 'header':
        return html`
          <div style="position: relative; display: block;" @click=${(e: Event) => this.handleNodeClick(e, node.id)}>
            ${deleteOverlay}
            <studio-header
              class="${classes} ${node.props?.className || ''}"
              style="${style}"
              data-node-id="${node.id}"
              @mouseenter=${() => this.handleNodeMouseEnter(node.id)}
              @mouseleave=${() => this.handleNodeMouseLeave()}
              @dragover=${(e: DragEvent) => this.handleDragOver(e, node.id)}
              @dragleave=${(e: DragEvent) => this.handleDragLeave(e)}
              @drop=${(e: DragEvent) => this.handleDrop(e, node.id)}
            ></studio-header>
            <div style="position: absolute; inset: 0; cursor: pointer; z-index: 10;" 
                 @click=${(e: Event) => this.handleNodeClick(e, node.id)}></div>
          </div>
        `;

      case 'card':
        // Recursively render children into the card slot
        const cardChildren = node.children?.map(childId => {
          const childNode = this.page?.nodes.find(n => n.id === childId);
          return childNode ? this.renderNode(childNode) : null;
        });

        return html`
          <div style="position: relative; display: block; width: 100%;" @click=${(e: Event) => this.handleNodeClick(e, node.id)}>
            ${deleteOverlay}
            <studio-card
              class="${classes} ${node.props?.className || ''}"
              style="${style}"
              data-node-id="${node.id}"
              @mouseenter=${() => this.handleNodeMouseEnter(node.id)}
              @mouseleave=${() => this.handleNodeMouseLeave()}
              @dragover=${(e: DragEvent) => this.handleDragOver(e, node.id)}
              @dragleave=${(e: DragEvent) => this.handleDragLeave(e)}
              @drop=${(e: DragEvent) => this.handleDrop(e, node.id)}
            >
              ${cardChildren}
            </studio-card>
             <!-- Partial overlay to allow dropping into children, but capture click on background -->
             <div style="position: absolute; top:0; left:0; right:0; height: 20px; cursor: pointer; z-index: 10;" 
                  @click=${(e: Event) => this.handleNodeClick(e, node.id)}
                  title="Select Card"></div>
          </div>
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

        // HOTFIX: Override legacy hardcoded styles for grid cells to allow tight packing
        let finalStyle = style;
        if (node.props?.className === 'grid-cell') {
          finalStyle = finalStyle
            .replace(/padding:\s*[^;]+;?/g, '')
            .replace(/gap:\s*[^;]+;?/g, '')
            .replace(/min-height:\s*[^;]+;?/g, '');
        }

        return html`
          <div
            class="${containerClasses}"
            style="${finalStyle}"
            slot="${node.props?.slot || ''}"
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
      // Get tenant ID from logged-in user
      const user = AuthService.getUser();
      const runtimeState = {
        tenantId: user?.tenantId || 'default',
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
