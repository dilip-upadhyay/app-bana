import { LitElement, html, css, unsafeCSS } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import styles from './ComponentLibrary.css?inline';
import type { ComponentNode } from '../../models/metadata';

interface ComponentTemplate {
  type: string;
  label: string;
  icon: string;
  category: string;
  description: string;
  template: Partial<ComponentNode>;
}

const COMPONENT_TEMPLATES: ComponentTemplate[] = [
  // Layout Components
  {
    type: 'container',
    label: 'Container',
    icon: '📦',
    category: 'Layout',
    description: 'Generic container for grouping elements',
    template: { type: 'container', props: { className: 'container' }, children: [] }
  },
  {
    type: 'flex-row',
    label: 'Flex Row',
    icon: '↔️',
    category: 'Layout',
    description: 'Horizontal flex container',
    template: { type: 'container', props: { className: 'flex-row', style: 'display: flex; flex-direction: row; gap: 1rem;' }, children: [] }
  },
  {
    type: 'flex-column',
    label: 'Flex Column',
    icon: '↕️',
    category: 'Layout',
    description: 'Vertical flex container',
    template: { type: 'container', props: { className: 'flex-column', style: 'display: flex; flex-direction: column; gap: 1rem;' }, children: [] }
  },
  {
    type: 'grid',
    label: 'Grid',
    icon: '⊞',
    category: 'Layout',
    description: 'CSS Grid container',
    template: { type: 'container', props: { className: 'grid', style: 'display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 1rem;' }, children: [] }
  },
  {
    type: 'section',
    label: 'Section',
    icon: '📄',
    category: 'Layout',
    description: 'Semantic section element',
    template: { type: 'section', props: { className: 'section' }, children: [] }
  },

  // Form Components
  {
    type: 'input',
    label: 'Text Input',
    icon: '📝',
    category: 'Form',
    description: 'Text input field',
    template: { type: 'input', props: { type: 'text', placeholder: 'Enter text...', className: 'input' } }
  },
  {
    type: 'textarea',
    label: 'Text Area',
    icon: '📋',
    category: 'Form',
    description: 'Multi-line text input',
    template: { type: 'textarea', props: { placeholder: 'Enter text...', rows: 4, className: 'textarea' } }
  },
  {
    type: 'button',
    label: 'Button',
    icon: '🔘',
    category: 'Form',
    description: 'Action button',
    template: { type: 'button', props: { text: 'Click Me', className: 'btn btn-primary' } }
  },
  {
    type: 'checkbox',
    label: 'Checkbox',
    icon: '☑️',
    category: 'Form',
    description: 'Checkbox input',
    template: { type: 'input', props: { type: 'checkbox', className: 'checkbox' } }
  },
  {
    type: 'select',
    label: 'Select',
    icon: '📋',
    category: 'Form',
    description: 'Dropdown select',
    template: { type: 'select', props: { className: 'select' }, children: [] }
  },

  // Content Components
  {
    type: 'text',
    label: 'Text',
    icon: '📝',
    category: 'Content',
    description: 'Text element',
    template: { type: 'text', props: { text: 'Text content', tag: 'p' } }
  },
  {
    type: 'heading',
    label: 'Heading',
    icon: '📰',
    category: 'Content',
    description: 'Heading element',
    template: { type: 'text', props: { text: 'Heading', tag: 'h2', className: 'heading' } }
  },
  {
    type: 'image',
    label: 'Image',
    icon: '🖼️',
    category: 'Content',
    description: 'Image element',
    template: { type: 'img', props: { src: 'https://via.placeholder.com/300x200', alt: 'Image', className: 'image' } }
  },
  {
    type: 'link',
    label: 'Link',
    icon: '🔗',
    category: 'Content',
    description: 'Hyperlink',
    template: { type: 'a', props: { text: 'Link', href: '#', className: 'link' } }
  },

  // Data Components
  {
    type: 'table',
    label: 'Table',
    icon: '📊',
    category: 'Data',
    description: 'Data table',
    template: { type: 'table', props: { className: 'table' }, children: [] }
  },
  {
    type: 'list',
    label: 'List',
    icon: '📝',
    category: 'Data',
    description: 'Unordered list',
    template: { type: 'ul', props: { className: 'list' }, children: [] }
  },
  {
    type: 'card',
    label: 'Card',
    icon: '🎴',
    category: 'Data',
    description: 'Card component',
    template: { type: 'container', props: { className: 'card', style: 'padding: 1.5rem; border-radius: 8px; border: 1px solid #e0e0e0; background: white;' }, children: [] }
  },
];

@customElement('studio-component-library')
export class ComponentLibrary extends LitElement {
  static styles = css`${unsafeCSS(styles)}`;

  @state() private selectedCategory: string = 'All';
  @state() private searchQuery: string = '';

  private get categories(): string[] {
    const cats = new Set<string>(['All']);
    COMPONENT_TEMPLATES.forEach(t => cats.add(t.category));
    return Array.from(cats);
  }

  private get filteredComponents(): ComponentTemplate[] {
    let filtered = COMPONENT_TEMPLATES;

    if (this.selectedCategory !== 'All') {
      filtered = filtered.filter(c => c.category === this.selectedCategory);
    }

    if (this.searchQuery) {
      const query = this.searchQuery.toLowerCase();
      filtered = filtered.filter(c =>
        c.label.toLowerCase().includes(query) ||
        c.description.toLowerCase().includes(query) ||
        c.type.toLowerCase().includes(query)
      );
    }

    return filtered;
  }

  private handleDragStart(e: DragEvent, template: ComponentTemplate) {
    if (!e.dataTransfer) return;

    e.dataTransfer.effectAllowed = 'copy';
    e.dataTransfer.setData('application/json', JSON.stringify({
      action: 'add-component',
      template: template.template
    }));

    // Create drag image
    const dragImage = document.createElement('div');
    dragImage.style.cssText = 'padding: 8px 12px; background: #4F46E5; color: white; border-radius: 6px; font-size: 14px;';
    dragImage.textContent = `${template.icon} ${template.label}`;
    document.body.appendChild(dragImage);
    e.dataTransfer.setDragImage(dragImage, 0, 0);
    setTimeout(() => document.body.removeChild(dragImage), 0);
  }

  render() {
    return html`
      <div class="library-container">
        <div class="library-header">
          <h3>Components</h3>
          <input
            type="text"
            class="search-input"
            placeholder="Search components..."
            .value=${this.searchQuery}
            @input=${(e: Event) => this.searchQuery = (e.target as HTMLInputElement).value}
          />
        </div>

        <div class="category-tabs">
          ${this.categories.map(cat => html`
            <button
              class="category-tab ${this.selectedCategory === cat ? 'active' : ''}"
              @click=${() => this.selectedCategory = cat}
            >
              ${cat}
            </button>
          `)}
        </div>

        <div class="components-grid">
          ${this.filteredComponents.map(template => html`
            <div
              class="component-item"
              draggable="true"
              @dragstart=${(e: DragEvent) => this.handleDragStart(e, template)}
              title="${template.description}"
            >
              <div class="component-icon">${template.icon}</div>
              <div class="component-label">${template.label}</div>
            </div>
          `)}
        </div>

        ${this.filteredComponents.length === 0 ? html`
          <div class="empty-state">
            <p>No components found</p>
          </div>
        ` : ''}
      </div>
    `;
  }
}

