import { LitElement, html, css } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { currentStore } from '../store/TreeStore';
import type { ComponentNode } from '../../models/metadata';
// import styles from './PropertiesPanel.css?inline';

console.log('[PropertiesPanel] Module loaded!');

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

  connectedCallback(): void {
    super.connectedCallback();

    console.log('[PropertiesPanel] Component connected, currentStore:', currentStore);
    if (currentStore) {
      currentStore.onChange(() => {
        this.updateSelectedNode();
      });
      this.updateSelectedNode();
    }
  }

  private updateSelectedNode() {
    if (!currentStore) return;

    const selection = currentStore.getSelection();
    console.log('[PropertiesPanel] updateSelectedNode called, selection:', selection);
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

          <!-- Dimensions Section -->
          <div class="section">
            <h4>📏 Dimensions</h4>

            <div class="form-row">
              <div class="form-group">
                <label>Width</label>
                <input
                  type="text"
                  .value=${this.width}
                  @input=${(e: Event) => this.width = (e.target as HTMLInputElement).value}
                  @change=${() => this.updateDimensions()}
                  placeholder="auto, 100px, 50%"
                />
              </div>
              <div class="form-group">
                <label>Height</label>
                <input
                  type="text"
                  .value=${this.height}
                  @input=${(e: Event) => this.height = (e.target as HTMLInputElement).value}
                  @change=${() => this.updateDimensions()}
                  placeholder="auto, 100px, 50%"
                />
              </div>
            </div>

            <div class="form-row">
              <div class="form-group">
                <label>Min Width</label>
                <input
                  type="text"
                  .value=${this.minWidth}
                  @input=${(e: Event) => this.minWidth = (e.target as HTMLInputElement).value}
                  @change=${() => this.updateDimensions()}
                  placeholder="100px"
                />
              </div>
              <div class="form-group">
                <label>Min Height</label>
                <input
                  type="text"
                  .value=${this.minHeight}
                  @input=${(e: Event) => this.minHeight = (e.target as HTMLInputElement).value}
                  @change=${() => this.updateDimensions()}
                  placeholder="50px"
                />
              </div>
            </div>

            <div class="form-row">
              <div class="form-group">
                <label>Max Width</label>
                <input
                  type="text"
                  .value=${this.maxWidth}
                  @input=${(e: Event) => this.maxWidth = (e.target as HTMLInputElement).value}
                  @change=${() => this.updateDimensions()}
                  placeholder="none, 500px"
                />
              </div>
              <div class="form-group">
                <label>Max Height</label>
                <input
                  type="text"
                  .value=${this.maxHeight}
                  @input=${(e: Event) => this.maxHeight = (e.target as HTMLInputElement).value}
                  @change=${() => this.updateDimensions()}
                  placeholder="none, 300px"
                />
              </div>
            </div>

            <!-- Quick Size Buttons -->
            <div class="quick-sizes">
              <h5>Quick Sizes</h5>
              <div class="button-group">
                <button @click=${() => this.handleQuickSize('100%', 'auto')}>Full Width</button>
                <button @click=${() => this.handleQuickSize('50%', 'auto')}>Half Width</button>
                <button @click=${() => this.handleQuickSize('auto', 'auto')}>Auto</button>
              </div>
              <div class="button-group">
                <button @click=${() => this.handleQuickSize('200px', '200px')}>200x200</button>
                <button @click=${() => this.handleQuickSize('300px', '200px')}>300x200</button>
                <button @click=${() => this.handleQuickSize('400px', '300px')}>400x300</button>
              </div>
              <button class="clear-btn" @click=${() => this.handleClearDimensions()}>Clear All</button>
            </div>
          </div>

          <!-- Info Section -->
          <div class="section info">
            <h5>💡 Tips</h5>
            <ul>
              <li>Use <code>px</code> for fixed sizes (e.g., 200px)</li>
              <li>Use <code>%</code> for relative sizes (e.g., 50%)</li>
              <li>Use <code>auto</code> for automatic sizing</li>
              <li>Use <code>rem</code> or <code>em</code> for responsive sizing</li>
              <li>Min/Max values constrain the size</li>
            </ul>
          </div>
        </div>
      </div>
    `;
  }

  private renderComponentProperties() {
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

