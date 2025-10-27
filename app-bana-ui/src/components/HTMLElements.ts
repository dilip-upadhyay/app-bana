// Generic HTML element wrapper for Studio
import { registerComponent } from '../core/registry';

class GenericHTMLElement extends HTMLElement {
  connectedCallback() {
    // Apply text content if provided
    const text = this.getAttribute('text');
    if (text && !this.hasChildNodes()) {
      this.textContent = text;
    }

    // Apply inline styles
    const style = this.getAttribute('style');
    if (style) {
      this.setAttribute('style', style);
    }

    // Apply class names
    const className = this.getAttribute('className');
    if (className) {
      this.className = className;
    }
  }
}

// Register all HTML elements that can be used in Studio
const htmlElements = [
  'header', 'footer', 'nav', 'aside', 'main', 'section', 'article',
  'div', 'span', 'p', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
  'ul', 'ol', 'li', 'a', 'img', 'form', 'input', 'textarea',
  'select', 'option', 'label', 'fieldset', 'legend',
  'table', 'thead', 'tbody', 'tr', 'th', 'td',
  'hr', 'br'
];

htmlElements.forEach(tag => {
  const className = `studio-${tag}`;

  if (!customElements.get(className)) {
    class ExtendedElement extends GenericHTMLElement {}
    customElements.define(className, ExtendedElement);
  }

  // Register in Studio registry
  registerComponent(tag, customElements.get(className) as CustomElementConstructor);
});

export {};

