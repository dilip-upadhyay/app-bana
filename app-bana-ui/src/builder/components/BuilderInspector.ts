import { LitElement, html, css, unsafeCSS } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { currentStore } from '../store/TreeStore';
import type { ComponentNode } from '../../models/metadata';
import styles from './BuilderInspector.css?inline';

@customElement('studio-builder-inspector')
export class BuilderInspector extends LitElement {
  static styles = css`${unsafeCSS(styles)}`;

  @state() private node: ComponentNode | null = null;

  connectedCallback(): void {
    super.connectedCallback();
    currentStore?.onChange(() => {
      this.node = currentStore?.getSelection() || null;
      this.requestUpdate();
    });
    this.node = currentStore?.getSelection() || null;
  }

  private updateProp(key: string, value: string) {
    if (!this.node) return;
    currentStore?.updateProps(this.node.id, { [key]: value });
  }

  private extractStyleValue(style: string, property: string): string {
    const regex = new RegExp(`${property}:\\s*([^;]+)`, 'i');
    const match = style.match(regex);
    return match ? match[1].trim() : '';
  }

  private updateDimension(property: string, value: string) {
    if (!this.node) return;

    const currentStyle = this.node.props?.style || '';
    let newStyle = currentStyle;

    // Remove old property
    const regex = new RegExp(`${property}:\\s*[^;]+;?`, 'gi');
    newStyle = newStyle.replace(regex, '');

    // Add new value if not empty
    if (value.trim()) {
      newStyle = newStyle.trim();
      if (newStyle && !newStyle.endsWith(';')) newStyle += ';';
      newStyle += ` ${property}: ${value.trim()};`;
    }

    currentStore?.updateProps(this.node.id, { style: newStyle.trim() });
  }

  private quickSize(width: string, height: string) {
    if (!this.node) return;
    const currentStyle = this.node.props?.style || '';
    let newStyle = currentStyle
      .replace(/width:\s*[^;]+;?/gi, '')
      .replace(/height:\s*[^;]+;?/gi, '');

    newStyle = newStyle.trim();
    if (newStyle && !newStyle.endsWith(';')) newStyle += ';';
    newStyle += ` width: ${width}; height: ${height};`;

    currentStore?.updateProps(this.node.id, { style: newStyle.trim() });
  }

  render() {
    if (!this.node) {
      return html`<div class="empty">
        <p>📋 No component selected</p>
        <small>Click on a component in the canvas to edit its properties</small>
      </div>`;
    }

    const props = this.node.props || {};
    const style = props.style || '';
    const width = this.extractStyleValue(style, 'width');
    const height = this.extractStyleValue(style, 'height');
    const minWidth = this.extractStyleValue(style, 'min-width');
    const minHeight = this.extractStyleValue(style, 'min-height');

    return html`
      <div class="inspector-header">
        <h4>Properties</h4>
        <div class="node-badge">
          <span class="type-badge">${this.node.type}</span>
          <span class="id-badge">${this.node.id}</span>
        </div>
      </div>

      <div class="section">
        <h5>📝 Content</h5>
        <label>Text / Label</label>
        <input
          type="text"
          .value=${props.text ?? props.label ?? ''}
          @input=${(e: Event) => this.updateProp('text', (e.target as HTMLInputElement).value)}
          placeholder="Enter text..." />
      </div>

      <div class="section">
        <h5>📏 Dimensions</h5>

        <div class="dimension-row">
          <div class="dimension-field">
            <label>Width</label>
            <input
              type="text"
              .value=${width}
              @change=${(e: Event) => this.updateDimension('width', (e.target as HTMLInputElement).value)}
              placeholder="auto, 100px, 50%" />
          </div>
          <div class="dimension-field">
            <label>Height</label>
            <input
              type="text"
              .value=${height}
              @change=${(e: Event) => this.updateDimension('height', (e.target as HTMLInputElement).value)}
              placeholder="auto, 100px, 50%" />
          </div>
        </div>

        <div class="dimension-row">
          <div class="dimension-field">
            <label>Min Width</label>
            <input
              type="text"
              .value=${minWidth}
              @change=${(e: Event) => this.updateDimension('min-width', (e.target as HTMLInputElement).value)}
              placeholder="100px" />
          </div>
          <div class="dimension-field">
            <label>Min Height</label>
            <input
              type="text"
              .value=${minHeight}
              @change=${(e: Event) => this.updateDimension('min-height', (e.target as HTMLInputElement).value)}
              placeholder="50px" />
          </div>
        </div>

        <div class="quick-sizes">
          <label>Quick Sizes</label>
          <div class="size-buttons">
            <button @click=${() => this.quickSize('100%', 'auto')}>Full Width</button>
            <button @click=${() => this.quickSize('50%', 'auto')}>Half</button>
            <button @click=${() => this.quickSize('auto', 'auto')}>Auto</button>
          </div>
          <div class="size-buttons">
            <button @click=${() => this.quickSize('200px', '200px')}>200×200</button>
            <button @click=${() => this.quickSize('300px', '200px')}>300×200</button>
            <button @click=${() => this.quickSize('400px', '300px')}>400×300</button>
          </div>
        </div>
      </div>

      <div class="section">
        <h5>🎨 Style</h5>
        <label>CSS Classes</label>
        <input
          type="text"
          .value=${props.className || ''}
          @input=${(e: Event) => this.updateProp('className', (e.target as HTMLInputElement).value)}
          placeholder="class-name another-class" />
    `;
  }
}
