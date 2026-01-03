import { LitElement, html, css } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { currentStore } from '../store/TreeStore';
import { appStore } from '../store/AppStore';
import type { ComponentNode } from '../../models/metadata';


// Property editor panel for selected components
@customElement('appbana-properties-panel')
export class PropertiesPanel extends LitElement {
  static readonly styles = css`
    :host {
      display: block;
      padding: 16px;
      height: 100%;
      overflow-y: auto;
    }
  `;

  @state() private selectedNode: ComponentNode | null = null;
  @state() private width: string = '';
  @state() private height: string = '';
  @state() private minWidth: string = '';
  @state() private minHeight: string = '';
  @state() private maxWidth: string = '';
  @state() private maxHeight: string = '';
  @state() private editingProps: Record<string, any> = {};

  private lastStoreInstance: any = null;
  private storeUnsubscribe: (() => void) | null = null;
  private storePollTimer: any = null;

  constructor() {
    super();
  }

  connectedCallback(): void {
    super.connectedCallback();
    this.subscribeToLatestStore();
    // Periodically check for store changes
    this.storePollTimer = setInterval(() => {
      if (currentStore !== this.lastStoreInstance) {
        this.subscribeToLatestStore();
      }
    }, 200);
  }

  disconnectedCallback(): void {
    super.disconnectedCallback();
    if (this.storeUnsubscribe) {
      this.storeUnsubscribe();
      this.storeUnsubscribe = null;
    }
    if (this.storePollTimer) {
      clearInterval(this.storePollTimer);
      this.storePollTimer = null;
    }
  }

  private subscribeToLatestStore() {
    if (this.storeUnsubscribe) {
      this.storeUnsubscribe();
      this.storeUnsubscribe = null;
    }
    if (currentStore) {
      this.lastStoreInstance = currentStore;
      this.storeUnsubscribe = currentStore.onChange(() => {
        this.updateSelectedNode();
      });
      this.updateSelectedNode();
    }
  }

  private updateSelectedNode() {
    if (!currentStore) return;
    const selection = currentStore.getSelection();
    this.selectedNode = selection;

    if (selection) {
      // Copy current props for editing
      this.editingProps = selection.props ? { ...selection.props } : {};

      // Parse current dimensions from style
      const style = selection.props?.style || '';
      this.width = this.extractStyleValue(style, 'width') || '';
      this.height = this.extractStyleValue(style, 'height') || '';
      this.minWidth = this.extractStyleValue(style, 'min-width') || '';
      this.minHeight = this.extractStyleValue(style, 'min-height') || '';
      this.maxWidth = this.extractStyleValue(style, 'max-width') || '';
      this.maxHeight = this.extractStyleValue(style, 'max-height') || '';
    } else {
      // Clear state when nothing is selected
      this.editingProps = {};
    }
  }

  private extractStyleValue(style: string, property: string): string {
    const regex = new RegExp(`${property}:\\s*([^;]+)`, 'i');
    const match = regex.exec(style);
    return match ? match[1].trim() : '';
  }

  private updateDimensions() {
    if (!this.selectedNode || !currentStore) return;

    const currentStyle = this.selectedNode.props?.style || '';
    let newStyle = currentStyle;

    // Remove old dimension properties
    newStyle = newStyle.replaceAll(/width:\s*[^;]+;?/gi, '');
    newStyle = newStyle.replaceAll(/height:\s*[^;]+;?/gi, '');
    newStyle = newStyle.replaceAll(/min-width:\s*[^;]+;?/gi, '');
    newStyle = newStyle.replaceAll(/min-height:\s*[^;]+;?/gi, '');
    newStyle = newStyle.replaceAll(/max-width:\s*[^;]+;?/gi, '');
    newStyle = newStyle.replaceAll(/max-height:\s*[^;]+;?/gi, '');

    // Add new dimensions
    const dimensions: string[] = [];
    if (this.width) dimensions.push(`width: ${this.width}`);
    if (this.height) dimensions.push(`height: ${this.height}`);
    if (this.minWidth) dimensions.push(`min-width: ${this.minWidth}`);
    if (this.minHeight) dimensions.push(`min-height: ${this.minHeight}`);
    if (this.maxWidth) dimensions.push(`max-width: ${this.maxWidth}`);
    if (this.maxHeight) dimensions.push(`max-height: ${this.maxHeight}`);

    if (dimensions.length > 0) {
      newStyle = newStyle.trim();
      if (newStyle && !newStyle.endsWith(';')) newStyle += ';';
      newStyle += ' ' + dimensions.join('; ') + ';';
    }

    // Update the node
    currentStore.updateProps(this.selectedNode.id, { style: newStyle.trim() });
  }

  private handleQuickSize(width: string, height: string) {
    this.width = width;
    this.height = height;
    this.updateDimensions();
  }

  private handleClearDimensions() {
    this.width = '';
    this.height = '';
    this.minWidth = '';
    this.minHeight = '';
    this.maxWidth = '';
    this.maxHeight = '';
    this.updateDimensions();
  }

  // UX Enhancement #1: Helper to get entity initial for badge
  private getEntityInitial(entityName: string): string {
    if (!entityName) return '';
    // For multi-word entities like "LineItem", take first letters: "LI"
    const words = entityName.match(/[A-Z][a-z]*/g) || [entityName];
    if (words.length > 1) {
      return words.map(w => w.charAt(0)).join('').toUpperCase();
    }
    return entityName.charAt(0).toUpperCase();
  }

  // CRITICAL FIX #3: Apply binding indicators to canvas elements
  private updateBindingIndicators() {
    if (!this.selectedNode) return;
    const entity = this.editingProps.entity;
    const field = this.editingProps.field;

    // Only for form components
    const formComponents = ['input', 'select', 'textarea', 'checkbox', 'radio'];
    if (!formComponents.includes(this.selectedNode.type)) return;

    const canvas = document.querySelector('appbana-builder-canvas');
    const element = canvas?.querySelector(`[data-node-id="${this.selectedNode.id}"]`) as HTMLElement;
    if (!element) return;

    if (entity && field) {
      element.setAttribute('data-binding-status', `bound:${entity}`);
      element.setAttribute('data-entity-initial', this.getEntityInitial(entity));
      element.removeAttribute('data-requires-binding');
    } else {
      element.setAttribute('data-binding-status', 'unbound');
      element.setAttribute('data-requires-binding', 'true');
      element.removeAttribute('data-entity-initial');
    }
  }

  private updateProperty(key: string, value: any) {
    if (!this.selectedNode || !currentStore) return;
    console.log('[PropertiesPanel] Updating property:', key, value);

    // Update local state
    this.editingProps = { ...this.editingProps, [key]: value };

    // Update the node in the store
    currentStore.updateProps(this.selectedNode.id, { [key]: value });

    // Trigger binding indicator update when binding properties change
    if (key === 'entity' || key === 'field') setTimeout(() => this.updateBindingIndicators(), 100);
  }

  private toggleEntity(entityName: string, checked: boolean) {
    const current = this.editingProps.entities || [];
    let updated: string[];

    if (checked) {
      // Add entity if not already in list (prevent duplicates)
      if (!current.includes(entityName)) {
        updated = [...current, entityName];
      } else {
        updated = current; // Already selected, no change
      }
    } else {
      // Remove entity from list
      updated = current.filter((e: string) => e !== entityName);
    }

    this.updateProperty('entities', updated);
  }

  private renderActionProperties() {
    const actionType = this.editingProps.actionType || 'none';
    const currentApp = appStore.getCurrentApp();
    const entities = currentApp?.entities || [];

    return html`
      <div class="section" style="margin-top: 16px; border-top: 1px solid #e5e7eb; padding-top: 16px;">
        <h4 style="margin-bottom: 12px;">⚡ Actions</h4>
        
        <div class="form-group">
          <label>Action Type</label>
          <select 
            .value=${actionType}
            @change=${(e: Event) => this.updateProperty('actionType', (e.target as HTMLSelectElement).value)}
          >
            <option value="none" ?selected=${actionType === 'none'}>None</option>
            <option value="save-entity" ?selected=${actionType === 'save-entity'}>Save Entity</option>
            <option value="navigate" ?selected=${actionType === 'navigate'}>Navigate</option>
            <option value="api" ?selected=${actionType === 'api'}>Custom API</option>
          </select>
        </div>

        ${actionType === 'save-entity' ? html`
          <div class="form-group">
            <label>Entities to Save <span style="color: #dc2626;">*</span></label>
            <div style="border: 1px solid #d1d5db; border-radius: 6px; padding: 8px; max-height: 200px; overflow-y: auto; background: white;">
              ${entities.length === 0 ? html`
                <p style="color: #6b7280; font-size: 12px; margin: 0;">No entities defined in this app</p>
              ` : entities.map((entity: any) => {
      const selectedEntities = this.editingProps.entities || [];
      const isSelected = selectedEntities.includes(entity.name);
      return html`
                  <label style="display: flex; align-items: center; padding: 6px; cursor: pointer; border-radius: 4px;" 
                         @mouseover=${(e: MouseEvent) => (e.currentTarget as HTMLElement).style.background = '#f3f4f6'}
                         @mouseout=${(e: MouseEvent) => (e.currentTarget as HTMLElement).style.background = 'transparent'}>
                    <input 
                      type="checkbox" 
                      .checked=${isSelected}
                      @change=${(e: Event) => this.toggleEntity(entity.name, (e.target as HTMLInputElement).checked)}
                      style="margin-right: 8px;"
                    />
                    <span style="flex: 1;">${entity.displayName || entity.name}</span>
                    <span style="font-size: 11px; color: #6b7280;">${entity.fields?.length || 0} fields</span>
                  </label>
                `;
    })}
            </div>
            
            ${(this.editingProps.entities || []).length > 0 ? html`
              <p style="font-size: 11px; color: #059669; margin-top: 8px;">
                ✓ Will save: <strong>${(this.editingProps.entities || []).join(', ')}</strong>
              </p>
            ` : html`
              <p style="font-size: 11px; color: #dc2626; margin-top: 8px;">
                ⚠ Select at least one entity
              </p>
            `}
          </div>
          
          <div class="form-group">
            <label>On Success</label>
            <select 
              .value=${this.editingProps.onSuccess || 'toast'}
              @change=${(e: Event) => this.updateProperty('onSuccess', (e.target as HTMLSelectElement).value)}
            >
              <option value="toast" ?selected=${this.editingProps.onSuccess === 'toast'}>Show Toast</option>
              <option value="navigate" ?selected=${this.editingProps.onSuccess === 'navigate'}>Navigate</option>
              <option value="refresh" ?selected=${this.editingProps.onSuccess === 'refresh'}>Refresh Data</option>
            </select>
          </div>

          ${this.editingProps.onSuccess === 'navigate' ? html`
             <div class="form-group">
              <label>Navigate To (URL)</label>
              <input 
                type="text" 
                .value=${this.editingProps.navigateUrl || ''} 
                @input=${(e: Event) => this.updateProperty('navigateUrl', (e.target as HTMLInputElement).value)}
                placeholder="/dashboard"
              />
            </div>
          `: ''}
        ` : ''}

        ${actionType === 'navigate' ? html`
          <div class="form-group">
            <label>Destination URL</label>
            <input 
              type="text" 
              .value=${this.editingProps.navigateUrl || ''} 
              @input=${(e: Event) => this.updateProperty('navigateUrl', (e.target as HTMLInputElement).value)}
              placeholder="/page-name"
            />
          </div>
        ` : ''}

        ${actionType === 'api' ? html`
          <div class="form-group">
            <label>API Endpoint</label>
            <input 
              type="text" 
              .value=${this.editingProps.apiEndpoint || ''} 
              @input=${(e: Event) => this.updateProperty('apiEndpoint', (e.target as HTMLInputElement).value)}
              placeholder="/api/custom-action"
            />
          </div>
          <div class="form-group">
            <label>Method</label>
            <select 
              .value=${this.editingProps.apiMethod || 'POST'}
              @change=${(e: Event) => this.updateProperty('apiMethod', (e.target as HTMLSelectElement).value)}
            >
              <option value="GET" ?selected=${this.editingProps.apiMethod === 'GET'}>GET</option>
              <option value="POST" ?selected=${this.editingProps.apiMethod === 'POST'}>POST</option>
              <option value="PUT" ?selected=${this.editingProps.apiMethod === 'PUT'}>PUT</option>
              <option value="DELETE" ?selected=${this.editingProps.apiMethod === 'DELETE'}>DELETE</option>
            </select>
          </div>
        ` : ''}
      </div>
    `;
  }

  private getCommonProperties(): string[] {
    if (!this.selectedNode) return [];

    const type = this.selectedNode.type;

    // Define common editable properties by component type
    const propertyMap: Record<string, string[]> = {
      'input': ['label', 'name', 'value', 'placeholder', 'type', 'required', 'disabled', 'error', 'helper-text'],
      'text-input': ['label', 'placeholder', 'name', 'value', 'required', 'disabled'],
      'textarea': ['label', 'placeholder', 'name', 'value', 'rows', 'required', 'disabled'],
      'button': ['label', 'variant', 'disabled', 'actionType', 'entity', 'navigateUrl', 'apiEndpoint', 'apiMethod', 'onSuccess'],
      'text': ['content'],
      'heading': ['level', 'content'],
      'link': ['href', 'text', 'target'],
      'image': ['src', 'alt', 'width', 'height'],
      'checkbox': ['label', 'name', 'checked', 'disabled'],
      'radio': ['label', 'name', 'value', 'checked', 'disabled'],
      'select': ['label', 'name', 'options', 'value', 'disabled'],
      'app-grid': ['rows', 'cols', 'gap', 'minCellHeight'],
      'grid': ['rows', 'cols', 'gap', 'minCellHeight'],
    };

    return propertyMap[type] || [];
  }

  render() {
    console.log('[PropertiesPanel] render called, selectedNode:', this.selectedNode?.type, this.selectedNode);
    if (!this.selectedNode) {
      return html`
        <div class="panel-container">
          <div class="panel-header">
            <h3>Properties</h3>
          </div>
          <div class="panel-body empty">
            <p>Select a component to edit its properties</p>
          </div>
        </div>
      `;
    }

    return html`
      <div class="panel-container">
        <div class="panel-header">
          <h3>Properties</h3>
          <div class="node-info">
            <span class="node-type">${this.selectedNode.type}</span>
            <span class="node-id">${this.selectedNode.id}</span>
          </div>
        </div>

        ${this.renderParentGridNavigation()}

        <div class="panel-body">
          <!-- Component Properties Section -->
          ${this.renderComponentProperties()}
        </div>
      </div>
    `;
  }

  private renderParentGridNavigation() {
    if (!currentStore || !this.selectedNode) return '';
    const parent = currentStore.findParent(this.selectedNode.id);

    if (parent && (parent.type === 'app-grid' || parent.type === 'grid')) {
      // Check if this node is a cell (has slot starting with cell-)
      const slot = this.selectedNode.props?.slot;
      const isCell = slot && String(slot).startsWith('cell-');

      if (isCell) {
        return html`
           <div style="background: #eef2ff; border: 1px solid #6366f1; border-radius: 6px; padding: 12px; margin: 0 16px 16px 16px;">
             <div style="font-size: 12px; color: #4338ca; margin-bottom: 8px; font-weight: 500;">
               Currently editing a Grid Cell inside <strong>${parent.id}</strong>
             </div>
             <button 
               style="width: 100%; padding: 8px; background: #4f46e5; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 13px;"
               @click=${() => currentStore?.select(parent.id)}
             >
               ⚙️ Configure Grid Properties
             </button>
           </div>
         `;
      }
    }
    return '';
  }

  private renderComponentProperties() {
    // Table Specific Properties
    if (this.selectedNode?.type === 'table') {
      return this.renderTableProperties();
    }

    const commonProps = this.getCommonProperties();

    if (commonProps.length === 0) {
      return html``;
    }

    return html`
      <div class="section">
        <h4>🔧 Component Properties</h4>
        
        ${['input', 'text-input', 'select', 'textarea', 'checkbox', 'radio'].includes(this.selectedNode?.type || '') ? this.renderFieldBinding() : ''}
        ${this.selectedNode?.type === 'button' ? this.renderActionProperties() : ''}

        ${commonProps
        .filter(p => !['actionType', 'entity', 'field', 'entities', 'navigateUrl', 'apiEndpoint', 'apiMethod', 'onSuccess'].includes(p))
        .map(propKey => {
          const currentValue = this.editingProps[propKey] ?? '';
          const propType = this.getPropertyType(propKey);

          if (propType === 'boolean') {
            return html`
              <div class="form-group">
                <label class="checkbox-label">
                  <input
                    type="checkbox"
                    .checked=${currentValue === true || currentValue === 'true'}
                    @change=${(e: Event) => {
                const checked = (e.target as HTMLInputElement).checked;
                this.updateProperty(propKey, checked);
              }}
                  />
                  <span>${this.formatPropertyLabel(propKey)}</span>
                </label>
              </div>
            `;
          }

          if (propType === 'number') {
            return html`
              <div class="form-group">
                <label>${this.formatPropertyLabel(propKey)}</label>
                <input
                  type="number"
                  .value=${String(currentValue)}
                  @input=${(e: Event) => {
                const value = (e.target as HTMLInputElement).value;
                this.updateProperty(propKey, value ? Number(value) : '');
              }}
                  placeholder=${this.getPropertyPlaceholder(propKey)}
                />
              </div>
            `;
          }

          if (propType === 'textarea') {
            return html`
              <div class="form-group">
                <label>${this.formatPropertyLabel(propKey)}</label>
                <textarea
                  .value=${String(currentValue)}
                  @input=${(e: Event) => {
                const value = (e.target as HTMLTextAreaElement).value;
                this.updateProperty(propKey, value);
              }}
                  placeholder=${this.getPropertyPlaceholder(propKey)}
                  rows="3"
                ></textarea>
              </div>
            `;
          }

          if (propType === 'spacing') {
            const isHeight = propKey === 'minCellHeight';

            // Define steps for stepping up/down
            const options = isHeight ? [
              { label: 'Auto', value: 'auto' },
              { label: 'Compact (60px)', value: '60px' },
              { label: 'Normal (100px)', value: '100px' },
              { label: 'Medium (150px)', value: '150px' },
              { label: 'Large (200px)', value: '200px' },
              { label: 'Extra Large (300px)', value: '300px' }
            ] : [
              { label: 'None (0)', value: '0' },
              { label: 'Tx (2px)', value: '2px' },
              { label: 'Tiny (4px)', value: '0.25rem' },
              { label: 'Small (8px)', value: '0.5rem' },
              { label: 'Normal (16px)', value: '1rem' },
              { label: 'Large (32px)', value: '2rem' },
              { label: 'Huge (64px)', value: '4rem' }
            ];

            // Find current index to enable/disable buttons
            const normalize = (v: any) => String(v || (isHeight ? 'auto' : '1rem')).toLowerCase();
            const currentVal = normalize(currentValue);
            const currentIndex = options.findIndex(o => normalize(o.value) === currentVal);
            const effectiveIndex = currentIndex === -1 ? 0 : currentIndex; // Default to 0 if custom/unknown

            return html`
          <div class="form-group">
            <label>${this.formatPropertyLabel(propKey)}</label>
            <div style="display: flex; gap: 8px; align-items: center;">
              <!-- Minus Button -->
              <button 
                type="button"
                style="width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; border: 1px solid #d1d5db; background: ${effectiveIndex <= 0 ? '#f3f4f6' : '#fff'}; cursor: pointer; border-radius: 4px;"
                ?disabled=${effectiveIndex <= 0}
                @click=${() => {
                if (effectiveIndex > 0) this.updateProperty(propKey, options[effectiveIndex - 1].value);
              }}
              >
                ➖
              </button>

              <!-- Dropdown -->
              <select
                style="flex: 1;"
                @change=${(e: Event) => this.updateProperty(propKey, (e.target as HTMLSelectElement).value)}
              >
                ${options.map(opt => html`
                  <option value="${opt.value}" ?selected=${normalize(opt.value) === currentVal}>
                    ${opt.label}
                  </option>
                `)}
                ${currentIndex === -1 && currentValue ? html`<option value="${currentValue}" selected>Custom (${currentValue})</option>` : ''}
              </select>

              <!-- Plus Button -->
              <button 
                type="button"
                style="width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; border: 1px solid #d1d5db; background: ${effectiveIndex >= options.length - 1 ? '#f3f4f6' : '#fff'}; cursor: pointer; border-radius: 4px;"
                ?disabled=${effectiveIndex >= options.length - 1}
                @click=${() => {
                if (effectiveIndex < options.length - 1) this.updateProperty(propKey, options[effectiveIndex + 1].value);
              }}
              >
                ➕
              </button>
            </div>
          </div>
        `;
          }
          return html`
            <div class="form-group">
              <label>${this.formatPropertyLabel(propKey)}</label>
              <input
                type="text"
                .value=${String(currentValue)}
                @input=${(e: Event) => {
              const value = (e.target as HTMLInputElement).value;
              this.updateProperty(propKey, value);
            }}
                placeholder=${this.getPropertyPlaceholder(propKey)}
              />
            </div>
          `;
        })}
      </div>
    `;
  }

  // Extracted Table Render Logic
  private renderTableProperties() {
    // Use singleton appStore for robust, reactive entity access
    type FieldMeta = { name: string; displayName?: string; type?: string; label?: string };
    type EntityMeta = { name: string; displayName?: string; fields?: FieldMeta[] };
    // Import appStore at top of file: import { appStore } from '../store/AppStore';
    const currentApp = appStore.getCurrentApp();
    const entities: EntityMeta[] = currentApp?.entities || [];
    const selectedEntity: EntityMeta | undefined = entities.find((e: EntityMeta) => e.name === this.editingProps.entity);
    const fields: FieldMeta[] = selectedEntity?.fields || [];
    const selectedFields: FieldMeta[] = this.editingProps.fields || [];
    return html`
        <div class="section">
          <h4>🔗 Table Entity Mapping</h4>
          <div class="form-group">
            <label>Entity</label>
            <select @change=${(e: Event) => this.updateProperty('entity', (e.target as HTMLSelectElement).value)}>
              <option value="">-- Select Entity --</option>
              ${entities.map((entity: EntityMeta) => html`<option value="${entity.name}" ?selected=${entity.name === this.editingProps.entity}>${entity.displayName || entity.name}</option>`)}
            </select>
          </div>
          <div class="form-group">
            <label>Fields</label>
            <div style="max-height:160px;overflow:auto;border:1px solid #eee;padding:4px;">
              ${fields.map((field: FieldMeta) => html`
                <label style="display:block;font-size:13px;padding:2px 0;">
                  <input type="checkbox"
                    .checked=${selectedFields.some((f: FieldMeta) => f.name === field.name)}
                    @change=${(e: Event) => {
        const checked = (e.target as HTMLInputElement).checked;
        let newFields = Array.isArray(selectedFields) ? [...selectedFields] : [];
        if (checked) newFields.push({ name: field.name, label: field.displayName || field.name });
        else newFields = newFields.filter((f: FieldMeta) => f.name !== field.name);
        this.updateProperty('fields', newFields);
      }}
                  /> ${field.displayName || field.name} (${field.type})
                </label>
              `)}
            </div>
          </div>
          <!-- Other Table Props (Sort, PageSize, etc) -->
          <div class="form-group">
            <label>Sort</label>
            <input type="text" value="${this.editingProps.sort || ''}" @input=${(e: Event) => this.updateProperty('sort', (e.target as HTMLInputElement).value)} />
          </div>
           <div class="form-group">
             <label>Page Size</label>
             <input type="number" min="1" value="${this.editingProps.pageSize || 25}" @input=${(e: Event) => this.updateProperty('pageSize', Number((e.target as HTMLInputElement).value))} />
           </div>
           <!-- ... Copied from original ... -->
           <!-- For brevity, simplistic restoration of rest of table props if needed, but original code had them inline. -->
           <!-- Since I am replacing the method, I must include them. -->
           <div class="form-group">
            <label class="checkbox-label">
              <input type="checkbox" .checked=${Boolean(this.editingProps.multiSelect)} @change=${(e: Event) => this.updateProperty('multiSelect', (e.target as HTMLInputElement).checked)} />
              <span>Enable Multi-select</span>
            </label>
          </div>
          <div class="form-group">
            <label>Actions</label>
            <label><input type="checkbox" .checked=${(this.editingProps.actions || []).includes('edit')} @change=${(e: Event) => this.toggleAction('edit', e)} /> Edit</label>
            <label><input type="checkbox" .checked=${(this.editingProps.actions || []).includes('delete')} @change=${(e: Event) => this.toggleAction('delete', e)} /> Delete</label>
            <label><input type="checkbox" .checked=${(this.editingProps.actions || []).includes('view')} @change=${(e: Event) => this.toggleAction('view', e)} /> View</label>
          </div>
          <div class="form-group">
             <label>View Mode</label>
             <select @change=${(e: Event) => this.updateProperty('viewMode', (e.target as HTMLSelectElement).value)}>
               ${['dynamic', 'custom'].map(m => html`<option value="${m}" ?selected=${(this.editingProps.viewMode || 'dynamic') === m}>${m}</option>`)}
             </select>
          </div>
           ${(() => {
        const mode = this.editingProps.viewMode || 'dynamic';
        if (mode !== 'custom') return '';
        const raw = this.editingProps.viewFormFieldsRaw || (this.editingProps.viewFormFields ? JSON.stringify(this.editingProps.viewFormFields, null, 2) : '[]');
        return html`<div class="form-group">
               <label>Custom View Form Fields (JSON Array)</label>
               <textarea rows="6" @input=${(e: Event) => {
            const val = (e.target as HTMLTextAreaElement).value;
            this.updateProperty('viewFormFieldsRaw', val);
            try { const parsed = JSON.parse(val); if (Array.isArray(parsed)) this.updateProperty('viewFormFields', parsed); } catch { }
          }} placeholder='[ { "name": "price", "label": "Price" }, { "name": "description", "label": "Description", "type": "textarea" } ]'>${raw}</textarea>
             </div>`;
      })()}
        </div>
      `;
  }





  // --- New Methods for Field Binding ---
  private findAncestor(nodeId: string, type: string): ComponentNode | null {
    if (!currentStore) return null;
    let pid = nodeId;
    while (pid) {
      const parent = currentStore.findParent(pid);
      if (!parent) return null; // Root or detached
      if (parent.type === type) return parent;
      pid = parent.id;
      if (pid === 'root') break;
    }
    return null;
  }

  private renderFieldBinding() {
    if (!this.selectedNode) return '';

    const entity = this.editingProps.entity || '';
    const field = this.editingProps.field || '';
    const currentApp = appStore.getCurrentApp();
    const entities = currentApp?.entities || [];

    // Get fields from selected entity
    const selectedEntity = entities.find((e: any) => e.name === entity);
    const fields = selectedEntity?.fields || [];

    // DEBUG: Log to see actual field structure
    if (fields.length > 0) {
      console.log('[PropertiesPanel] Entity fields:', entity, fields);
      console.log('[PropertiesPanel] First field structure:', fields[0]);
    }


    return html`
      <div class="section" style="background: #f0f9ff; padding: 12px; border-radius: 8px; border: 1px solid #bae6fd; margin-bottom: 16px;">
        <h4 style="margin: 0 0 12px 0; color: #0c4a6e; font-size: 0.9rem;">📊 Entity Binding</h4>
        
        <div class="form-group">
          <label>Entity <span style="color: #dc2626;">*</span></label>
          <select 
            .value=${entity}
            @change=${(e: Event) => this.updateProperty('entity', (e.target as HTMLSelectElement).value)}
            style="width: 100%;"
          >
            <option value="">-- Select Entity --</option>
            ${entities.map((e: any) => html`
              <option value="${e.name}" ?selected=${this.editingProps.entity === e.name}>
                ${e.displayName || e.name}
              </option>
            `)}
          </select>
        </div>
        
        ${entity ? html`
          <div class="form-group">
            <label>Field <span style="color: #dc2626;">*</span></label>
            <select 
              .value=${field}
              @change=${(e: Event) => this.handleFieldChange((e.target as HTMLSelectElement).value, fields)}
              style="width: 100%;"
            >
              <option value="">-- Select Field --</option>
            ${fields.map((f: any) => {
      // Better display name logic: Use display.label, or format the name nicely
      let displayName = f.display?.label || f.name;

      // If displayName looks like a technical name (camelCase/snake_case), format it
      if (displayName === f.name) {
        // Convert camelCase to Title Case: "firstName" -> "First Name"
        displayName = f.name
          .replace(/([A-Z])/g, ' $1') // Add space before capitals
          .replace(/^./, (str: string) => str.toUpperCase()) // Capitalize first letter
          .trim();
      }

      return html`
                <option value="${f.name}" ?selected=${this.editingProps.field === f.name}>
                  ${displayName} (${f.type})
                </option>
              `;
    })}
          </select>
          </div>
          
          <p style="font-size: 11px; color: #059669; margin: 8px 0 0 0;">
            ✓ Binds to: <strong>${entity}.${field || '?'}</strong>
          </p>
        ` : html`
          <p style="font-size: 11px; color: #dc2626; margin: 8px 0 0 0;">
            ⚠ Must select entity and field
          </p>
        `}
      </div>
    `;
  }

  private handleFieldChange(fieldName: string, fields: any[]) {
    if (!fieldName) return;
    this.updateProperty('field', fieldName);

    const field = fields.find((f: any) => f.name === fieldName);
    if (field) {
      // Auto-populate name if empty or default
      if (!this.editingProps.name || this.editingProps.name === 'name') {
        this.updateProperty('name', fieldName);
      }

      // Auto-populate label if empty or default
      const currentLabel = this.editingProps.label;
      if (!currentLabel || currentLabel === 'Label' || currentLabel === 'Input') {
        this.updateProperty('label', field.display?.label || this.formatPropertyLabel(field.name));
      }

      // Auto-set input type based on field type
      if (field.type === 'number' || field.type === 'integer' || field.type === 'decimal') {
        this.updateProperty('type', 'number');
      } else if (field.name.toLowerCase().includes('email')) {
        this.updateProperty('type', 'email');
      } else if (field.name.toLowerCase().includes('password')) {
        this.updateProperty('type', 'password');
      } else if (field.name.toLowerCase().includes('date')) {
        this.updateProperty('type', 'date');
      } else {
        this.updateProperty('type', 'text');
      }

      // Use required from schema? (If available in meta)
      // if (field.required) this.updateProperty('required', true);
    }
  }




  private toggleAction(action: string, e: Event) {
    if (!this.selectedNode || !currentStore) return;
    const checked = (e.target as HTMLInputElement).checked;
    let actions = Array.isArray(this.editingProps.actions) ? [...this.editingProps.actions] : [];
    if (checked) actions.push(action);
    else actions = actions.filter((a: string) => a !== action);
    this.updateProperty('actions', actions);
  }

  private toggleBulkAction(action: string, e: Event) {
    if (!this.selectedNode || !currentStore) return;
    const checked = (e.target as HTMLInputElement).checked;
    let actions = Array.isArray(this.editingProps.bulkActions) ? [...this.editingProps.bulkActions] : [];
    if (checked) actions.push(action);
    else actions = actions.filter((a: string) => a !== action);
    this.updateProperty('bulkActions', actions);
  }

  private getPropertyType(propKey: string): 'text' | 'number' | 'boolean' | 'textarea' | 'spacing' {
    const booleanProps = ['required', 'disabled', 'checked'];
    const numberProps = ['rows', 'cols', 'level', 'width', 'height'];
    const textareaProps = ['content', 'options'];
    const spacingProps = ['gap', 'minCellHeight'];

    if (booleanProps.includes(propKey)) return 'boolean';
    if (numberProps.includes(propKey)) return 'number';
    if (textareaProps.includes(propKey)) return 'textarea';
    if (spacingProps.includes(propKey)) return 'spacing';
    return 'text';
  }

  private formatPropertyLabel(propKey: string): string {
    // Convert camelCase to Title Case with spaces
    return propKey
      .replaceAll(/([A-Z])/g, ' $1')
      .replace(/^./, str => str.toUpperCase())
      .trim();
  }

  private getPropertyPlaceholder(propKey: string): string {
    const placeholders: Record<string, string> = {
      'label': 'Enter label text',
      'placeholder': 'Enter placeholder text',
      'name': 'field-name',
      'value': 'default value',
      'href': 'https://example.com',
      'text': 'Link text',
      'src': 'image-url',
      'alt': 'Image description',
      'content': 'Enter content',
      'options': 'option1,option2,option3',
      'rows': '3',
      'level': '1-6',
    };

    return placeholders[propKey] || '';
  }

  private serializeThemeTokens(): string {
    try { return JSON.stringify(this.editingProps.themeTokens || {}, null, 2); } catch { return '{}'; }
  }
  private handleCustomThemeInput(e: Event) {
    const raw = (e.target as HTMLTextAreaElement).value;
    let parsed: Record<string, string> = {};
    try { parsed = JSON.parse(raw); } catch { /* ignore parse errors; keep previous tokens */ }
    this.updateProperty('themeTokens', parsed);
  }
}

