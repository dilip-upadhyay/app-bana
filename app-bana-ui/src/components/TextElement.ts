import { FormElement } from './FormElement';
import { registerComponent } from '../core/registry';

export class TextElement extends FormElement {
  static get observedAttributes() {
    return ['content', 'text', 'tag', 'align', 'color'];
  }

  attributeChangedCallback() {
    this.requestRender();
  }

  protected render(): string {
    const content = this.getAttribute('content') || this.getAttribute('text') || 'Text content';
    const tag = this.getAttribute('tag') || 'p';

    // Sanitize tag to prevent XSS (basic check)
    const safeTag = ['h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'p', 'span', 'div'].includes(tag) ? tag : 'p';

    return `<${safeTag}>${content}</${safeTag}>`;
  }

  protected styles(): string {
    const align = this.getAttribute('align') || 'left';
    const color = this.getAttribute('color') || 'inherit';

    return `
      :host { display: block; text-align: ${align}; color: ${color}; }
      h1 { font-size: 2rem; font-weight: 700; margin: 0 0 1rem; }
      h2 { font-size: 1.5rem; font-weight: 600; margin: 0 0 0.75rem; }
      h3 { font-size: 1.25rem; font-weight: 600; margin: 0 0 0.5rem; }
      p { margin: 0 0 1rem; line-height: 1.5; }
    `;
  }
}

if (!customElements.get('studio-text')) {
  customElements.define('studio-text', TextElement);
}
registerComponent('text', TextElement);
