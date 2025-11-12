import { LitElement, html, css } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { currentStore } from '../store/TreeStore';
import { appStore } from '../store/AppStore';
import type { ComponentNode } from '../../models/metadata';
// import styles from './PropertiesPanel.css?inline';


// Property editor panel for selected components
@customElement('studio-properties-panel')
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
      this.editingProps = { ...(selection.props || {}) };
      
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
    const match = style.match(regex);
    return match ? match[1].trim() : '';
  }

  private updateDimensions() {
    if (!this.selectedNode || !currentStore) return;

    const currentStyle = this.selectedNode.props?.style || '';
    let newStyle = currentStyle;

    // Remove old dimension properties
    newStyle = newStyle.replace(/width:\s*[^;]+;?/gi, '');
    newStyle = newStyle.replace(/height:\s*[^;]+;?/gi, '');
    newStyle = newStyle.replace(/min-width:\s*[^;]+;?/gi, '');
    newStyle = newStyle.replace(/min-height:\s*[^;]+;?/gi, '');
    newStyle = newStyle.replace(/max-width:\s*[^;]+;?/gi, '');
    newStyle = newStyle.replace(/max-height:\s*[^;]+;?/gi, '');

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

  private updateProperty(key: string, value: any) {
    if (!this.selectedNode || !currentStore) return;
    
    // Update local state
    this.editingProps = { ...this.editingProps, [key]: value };
    
    // Update the node in the store
    currentStore.updateProps(this.selectedNode.id, { [key]: value });
  }

  private getCommonProperties(): string[] {
    if (!this.selectedNode) return [];
    
    const type = this.selectedNode.type;
    
    // Define common editable properties by component type
    const propertyMap: Record<string, string[]> = {
      'text-input': ['label', 'placeholder', 'name', 'value', 'required', 'disabled'],
      'textarea': ['label', 'placeholder', 'name', 'value', 'rows', 'required', 'disabled'],
      'button': ['label', 'variant', 'disabled'],
      'text': ['content'],
      'heading': ['level', 'content'],
      'link': ['href', 'text', 'target'],
      'image': ['src', 'alt', 'width', 'height'],
      'checkbox': ['label', 'name', 'checked', 'disabled'],
      'radio': ['label', 'name', 'value', 'checked', 'disabled'],
      'select': ['label', 'name', 'options', 'value', 'disabled'],
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

        <div class="panel-body">
          <!-- Component Properties Section -->
          ${this.renderComponentProperties()}
        </div>
      </div>
    `;
  }

  private renderComponentProperties() {
    if (this.selectedNode?.type === 'table') {
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
          <div class="form-group">
            <label>Sort</label>
            <input type="text" value="${this.editingProps.sort || ''}" @input=${(e: Event) => this.updateProperty('sort', (e.target as HTMLInputElement).value)} />
          </div>
          <div class="form-group">
            <label>Page Size</label>
            <input type="number" min="1" value="${this.editingProps.pageSize || 25}" @input=${(e: Event) => this.updateProperty('pageSize', Number((e.target as HTMLInputElement).value))} />
          </div>
          <div class="form-group">
            <label>Actions</label>
            <label><input type="checkbox" .checked=${(this.editingProps.actions || []).includes('edit')} @change=${(e: Event) => this.toggleAction('edit', e)} /> Edit</label>
            <label><input type="checkbox" .checked=${(this.editingProps.actions || []).includes('delete')} @change=${(e: Event) => this.toggleAction('delete', e)} /> Delete</label>
            <label><input type="checkbox" .checked=${(this.editingProps.actions || []).includes('view')} @change=${(e: Event) => this.toggleAction('view', e)} /> View</label>
          </div>
        </div>
      `;
    }
    
    const commonProps = this.getCommonProperties();
    
    if (commonProps.length === 0) {
      return html``;
    }

    return html`
      <div class="section">
        <h4>🔧 Component Properties</h4>
        
        ${commonProps.map(propKey => {
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
          
          // Default: text input
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

  private toggleAction(action: string, e: Event) {
    if (!this.selectedNode || !currentStore) return;
    const checked = (e.target as HTMLInputElement).checked;
    let actions = Array.isArray(this.editingProps.actions) ? [...this.editingProps.actions] : [];
    if (checked) actions.push(action);
    else actions = actions.filter((a: string) => a !== action);
    this.updateProperty('actions', actions);
  }

  private getPropertyType(propKey: string): 'text' | 'number' | 'boolean' | 'textarea' {
    const booleanProps = ['required', 'disabled', 'checked'];
    const numberProps = ['rows', 'level', 'width', 'height'];
    const textareaProps = ['content', 'options'];
    
    if (booleanProps.includes(propKey)) return 'boolean';
    if (numberProps.includes(propKey)) return 'number';
    if (textareaProps.includes(propKey)) return 'textarea';
    return 'text';
  }

  private formatPropertyLabel(propKey: string): string {
    // Convert camelCase to Title Case with spaces
    return propKey
      .replace(/([A-Z])/g, ' $1')
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
}

