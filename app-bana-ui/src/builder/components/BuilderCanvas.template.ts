import { html, TemplateResult } from 'lit';
import type { BuilderCanvas } from './BuilderCanvas';
import type { ComponentNode } from '../../models/metadata';
import { currentStore } from '../store/TreeStore';

// Node rendering template
export function renderNode(this: BuilderCanvas, node: ComponentNode): TemplateResult {
  const sel = node.id === this['selectedId'];
  const expanded = this['expanded'].has(node.id);
  const childCount = node.children?.length || 0;
  const isEditing = this['editingId'] === node.id;

  return html`
    <div
      class="node ${this['dragOverId'] === node.id ? 'drag-over' : ''}"
      data-selected=${sel}
      role="treeitem"
      aria-selected=${sel}
      aria-expanded=${childCount ? String(expanded) : undefined}
      draggable=${node.id !== this['page']!.rootId}
      @dragstart=${(e: DragEvent) => this['handleDragStart'](e, node)}
      @dragover=${(e: DragEvent) => this['handleDragOver'](e, node)}
      @dragleave=${(e: DragEvent) => this['handleDragLeave'](e, node)}
      @drop=${(e: DragEvent) => this['handleDrop'](e, node)}
      @click=${(e: Event) => { e.stopPropagation(); this['select'](node.id); }}>

      ${renderExpandButton.call(this, node, childCount, expanded)}
      ${renderNodeContent.call(this, node, isEditing)}
      ${renderNodeActions.call(this, node)}
    </div>
    ${renderNodeChildren.call(this, node, childCount, expanded)}
  `;
}

// Expand/collapse button
function renderExpandButton(this: BuilderCanvas, node: ComponentNode, childCount: number, expanded: boolean): TemplateResult {
  return childCount
    ? html`<button
        class="expand-btn"
        aria-label="${expanded ? 'Collapse' : 'Expand'} ${node.id}"
        @click=${(e: Event) => { e.stopPropagation(); this['toggleExpand'](node.id); }}>
        ${expanded ? '▾' : '▸'}
      </button>`
    : html`<span class="expand-btn" style="opacity:.4;">•</span>`;
}

// Node content (label or edit input)
function renderNodeContent(this: BuilderCanvas, node: ComponentNode, isEditing: boolean): TemplateResult {
  return isEditing
    ? html`<input
        class="inline-edit"
        .value=${this['editingValue']}
        @input=${(e: Event) => this['editingValue'] = (e.target as HTMLInputElement).value}
        @keydown=${(e: KeyboardEvent) => {
          if (e.key === 'Enter') {
            this['commitEdit']();
          } else if (e.key === 'Escape') {
            this['cancelEdit']();
          }
        }}
        @blur=${() => this['commitEdit']()} />`
    : html`<span>${node.type === 'text' ? (node.props?.text || '<text>') : node.type}</span>`;
}

// Node action buttons
function renderNodeActions(this: BuilderCanvas, node: ComponentNode): TemplateResult {
  return html`
    <button
      class="inline"
      title="Add child"
      @click=${(e: Event) => { e.stopPropagation(); this['addChild'](node.id); }}>
      +
    </button>
    ${node.id !== this['page']!.rootId
      ? html`<button
          class="inline"
          title="Delete"
          @click=${(e: Event) => {
            e.stopPropagation();
            if (!node.children?.length || window.confirm('Delete ' + node.id + ' and its subtree?')) {
              this['deleteNode'](node.id);
            }
          }}>
          ×
        </button>`
      : null}
  `;
}

// Node children
function renderNodeChildren(this: BuilderCanvas, node: ComponentNode, childCount: number, expanded: boolean): TemplateResult | null {
  return childCount && expanded
    ? html`<div class="children" role="group">
        ${node.children!.map(cid => {
          const child = this['page']!.nodes.find(n => n.id === cid)!;
          return renderNode.call(this, child);
        })}
      </div>`
    : null;
}

// Toolbar template
function renderToolbar(this: BuilderCanvas): TemplateResult {
  return html`
    <div class="toolbar">
      <button @click=${() => currentStore?.undo()}>Undo</button>
      <button @click=${() => currentStore?.redo()}>Redo</button>
      <button
        ?disabled=${!this['selectedId'] || this['selectedId'] === this['page'].rootId}
        @click=${() => this['duplicateSelected']()}>
        Duplicate
      </button>
      <button @click=${() => this['openPalette']()}>Search (⌘/Ctrl+P)</button>
    </div>
  `;
}

// Tree template
function renderTree(this: BuilderCanvas, root: ComponentNode): TemplateResult {
  return html`
    <div
      class="tree"
      role="tree"
      aria-label="Component tree"
      @click=${() => this['select'](this['page']!.rootId)}>
      ${renderNode.call(this, root)}
    </div>
  `;
}

// Palette item template
function renderPaletteItem(this: BuilderCanvas, node: ComponentNode, index: number): TemplateResult {
  return html`
    <li
      role="option"
      aria-selected=${index === this['paletteIndex']}
      class=${index === this['paletteIndex'] ? 'active' : ''}
      @click=${() => {
        this['paletteIndex'] = index;
        this['selectPaletteIndex']();
      }}>
      <span>${node.id}</span>
      <span class="badge">${node.type}</span>
    </li>
  `;
}

// Palette template
function renderPalette(this: BuilderCanvas, paletteList: ComponentNode[]): TemplateResult {
  return html`
    <div class="palette-backdrop" @click=${() => this['closePalette']()}>
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
            @click=${() => this['closePalette']()}>
            Esc
          </button>
        </header>

        <input
          aria-label="Filter nodes"
          placeholder="Filter by id / type / text"
          .value=${this['paletteQuery']}
          @input=${(e: Event) => {
            this['paletteQuery'] = (e.target as HTMLInputElement).value;
            this['paletteIndex'] = 0;
          }} />

        <ul role="listbox" aria-label="Search results">
          ${paletteList.map((n, i) => renderPaletteItem.call(this, n, i))}
          ${!paletteList.length
            ? html`<li style="opacity:.6; cursor:default;" aria-disabled="true">No matches</li>`
            : null}
        </ul>

        <div style="padding:4px 8px; font-size:10px; border-top:1px solid #e2e8f0; display:flex; justify-content:space-between;">
          <span>↑↓ navigate • Enter select • Esc close</span>
          <span>Shift+⌘/Ctrl+C copy ID</span>
        </div>
      </div>
    </div>
  `;
}

// Toast notification template
function renderToast(this: BuilderCanvas): TemplateResult | null {
  return this['toast']
    ? html`<div class="toast" role="status" aria-live="polite">${this['toast']}</div>`
    : null;
}

// Screen reader live region
function renderScreenReaderLive(this: BuilderCanvas): TemplateResult {
  return html`<div class="sr-live" aria-live="polite">${this['toast'] || ''}</div>`;
}

// Main template
export function renderTemplate(this: BuilderCanvas): TemplateResult {
  if (!this['page']) {
    return html`<div>Loading...</div>`;
  }

  const root = this['page'].nodes.find(n => n.id === this['page']!.rootId)!;
  const paletteList = this['paletteOpen'] ? this['filteredNodes']() : [];

  return html`
    ${renderToolbar.call(this)}
    ${renderTree.call(this, root)}
    ${this['paletteOpen'] ? renderPalette.call(this, paletteList) : null}
    ${renderToast.call(this)}
    ${renderScreenReaderLive.call(this)}
  `;
}
:host {
  display: block;
  font: 13px/1.4 system-ui, sans-serif;
  position: relative;
}

.tree {
  background: #fff;
  border: 1px solid #e2e8f0;
  padding: 8px;
  border-radius: 4px;
}

.node {
  padding: 2px 4px;
  margin: 2px 0;
  border-radius: 3px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
  user-select: none;
}

.node[data-selected='true'] {
  background: #1e40af;
  color: #fff;
}

.children {
  margin-left: 14px;
  border-left: 1px dashed #cbd5e1;
  padding-left: 6px;
}

button.inline {
  font-size: 10px;
  padding: 2px 4px;
}

.toolbar {
  display: flex;
  gap: 4px;
  margin-bottom: 6px;
}

.expand-btn {
  background: transparent;
  border: none;
  cursor: pointer;
  font-size: 11px;
  width: 18px;
  text-align: center;
  color: #4b5563;
}

.expand-btn:focus {
  outline: 1px solid #94a3b8;
}

.node.drag-over {
  outline: 2px dashed #2563eb;
}

.palette-backdrop {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, .35);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 60px;
  z-index: 10;
}

.palette {
  background: #fff;
  width: 380px;
  max-height: 420px;
  border: 1px solid #334155;
  border-radius: 6px;
  box-shadow: 0 6px 22px -4px rgba(0, 0, 0, .25);
  display: flex;
  flex-direction: column;
}

.palette header {
  padding: 6px 8px;
  border-bottom: 1px solid #e2e8f0;
  font-weight: 600;
  font-size: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.palette input {
  margin: 6px 8px;
  padding: 6px 8px;
  font: 12px system-ui, sans-serif;
}

.palette ul {
  list-style: none;
  margin: 0;
  padding: 4px 0 6px;
  overflow: auto;
  flex: 1;
}

.palette li {
  padding: 4px 10px;
  font-size: 12px;
  display: flex;
  justify-content: space-between;
  gap: 8px;
  cursor: pointer;
}

.palette li.active {
  background: #1e3a8a;
  color: #fff;
}

.badge {
  font-size: 10px;
  background: #e2e8f0;
  color: #334155;
  padding: 0 4px;
  border-radius: 3px;
}

.toast {
  position: absolute;
  bottom: 8px;
  right: 8px;
  background: #1e3a8a;
  color: #fff;
  padding: 6px 10px;
  font-size: 11px;
  border-radius: 4px;
  animation: fade .4s ease;
}

@keyframes fade {
  from {
    opacity: 0;
    transform: translateY(4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.inline-edit {
  background: #fff;
  color: #111;
  border: 1px solid #2563eb;
  font: 11px system-ui, sans-serif;
  padding: 1px 3px;
  border-radius: 3px;
}

.sr-live {
  position: absolute;
  left: -9999px;
  top: auto;
  width: 1px;
  height: 1px;
  overflow: hidden;
}
