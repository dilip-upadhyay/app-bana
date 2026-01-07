import { LitElement, html, css, unsafeCSS } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { currentStore } from '../store/TreeStore';
import type { ComponentNode } from '../../models/metadata';
import { getComponentDefinition, PropType, type PropDefinition } from '../../core/component-metadata';
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

  private updateProp(key: string, value: any) {
    if (!this.node) return;
    currentStore?.updateProps(this.node.id, { [key]: value });
  }

  private renderPropField(prop: PropDefinition, currentValue: any) {
    const value = currentValue ?? prop.defaultValue ?? '';

    switch (prop.type) {
      case PropType.Text:
        return html`
          <div class="form-group">
            <label>${prop.label || prop.name}</label>
            <input
              type="text"
              .value=${value}
              @input=${(e: Event) => this.updateProp(prop.name, (e.target as HTMLInputElement).value)}
              placeholder=${prop.placeholder || ''} />
          </div>
        `;

      case PropType.Number:
        return html`
          <div class="form-group">
            <label>${prop.label || prop.name}</label>
            <input
              type="number"
              .value=${value}
              @input=${(e: Event) => this.updateProp(prop.name, Number((e.target as HTMLInputElement).value))}
              placeholder=${prop.placeholder || ''} />
          </div>
        `;

      case PropType.Boolean:
        return html`
          <div class="form-group checkbox-group">
            <label class="checkbox-label">
              <input
                type="checkbox"
                .checked=${!!value}
                @change=${(e: Event) => this.updateProp(prop.name, (e.target as HTMLInputElement).checked)} />
              <span>${prop.label || prop.name}</span>
            </label>
          </div>
        `;

      case PropType.Select:
        return html`
          <div class="form-group">
            <label>${prop.label || prop.name}</label>
            <select
              .value=${value}
              @change=${(e: Event) => this.updateProp(prop.name, (e.target as HTMLSelectElement).value)}>
              ${prop.options?.map(opt => html`
                <option value=${opt.value} ?selected=${opt.value === value}>${opt.label}</option>
              `)}
            </select>
          </div>
        `;

      case PropType.Textarea:
        return html`
          <div class="form-group">
            <label>${prop.label || prop.name}</label>
            <textarea
              .value=${value}
              @input=${(e: Event) => this.updateProp(prop.name, (e.target as HTMLTextAreaElement).value)}
              placeholder=${prop.placeholder || ''}
              rows="3"></textarea>
            ${prop.description ? html`<small>${prop.description}</small>` : ''}
          </div>
        `;

      default:
        return null;
    }
  }

  private renderGroup(title: string, props: PropDefinition[], currentProps: Record<string, any>) {
    if (!props.length) return null;
    return html`
      <div class="section">
        <h5>${title}</h5>
        ${props.map(p => this.renderPropField(p, currentProps[p.name]))}
      </div>
    `;
  }

  render() {
    if (!this.node) {
      return html`<div class="empty">
        <p>📋 No component selected</p>
        <small>Click on a component in the canvas to edit its properties</small>
      </div>`;
    }

    const def = getComponentDefinition(this.node.type);
    const props = this.node.props || {};

    if (!def) {
      return html`
        <div class="inspector-header">
          <h4>Unknown Component</h4>
          <div class="node-badge">
            <span class="type-badge">${this.node.type}</span>
            <span class="id-badge">${this.node.id}</span>
          </div>
        </div>
        <div class="section">
          <p>No definition found for type "${this.node.type}".</p>
        </div>
      `;
    }

    // Group properties
    const contentProps = def.props.filter(p => p.group === 'content' || !p.group);
    const styleProps = def.props.filter(p => p.group === 'style');
    const layoutProps = def.props.filter(p => p.group === 'layout');
    const advancedProps = def.props.filter(p => p.group === 'advanced');

    return html`
      <div class="inspector-header">
        <h4>${def.label} Properties</h4>
        <div class="node-badge">
          <span class="type-badge">${def.icon || ''} ${this.node.type}</span>
          <span class="id-badge">${this.node.id}</span>
        </div>
        ${def.description ? html`<small class="description">${def.description}</small>` : ''}
      </div>

      ${this.renderGroup('📝 Content', contentProps, props)}
      ${this.renderGroup('🎨 Style', styleProps, props)}
      ${this.renderGroup('📐 Layout', layoutProps, props)}
      ${this.renderGroup('⚙️ Advanced', advancedProps, props)}
    `;
  }
}
