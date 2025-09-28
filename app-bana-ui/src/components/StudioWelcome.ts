import { BaseElement } from '../core/BaseElement';

/**
 * An example component demonstrating the use of the BaseElement class.
 * It displays a welcome message and a counter that increments on button click.
 */
export class StudioWelcome extends BaseElement {
  // Tell the browser to observe the 'name' attribute for changes.
  static get observedAttributes() {
    return ['name'];
  }

  protected onInit(): void {
    // Set initial state. The 'name' will be populated from the attribute.
    this.setState({
      name: this.getAttribute('name') || 'Developer',
      count: 0,
    });
  }

  protected styles(): string {
    // Scoped CSS for the component.
    return `
      :host {
        display: block;
        padding: 16px;
        border: 1px solid #ccc;
        border-radius: 8px;
        font-family: sans-serif;
      }
      button {
        margin-top: 8px;
        padding: 8px 12px;
        border-radius: 4px;
        border: 1px solid #888;
        cursor: pointer;
      }
      button:hover {
        background-color: #f0f0f0;
      }
    `;
  }

  protected render(): string {
    // Render the component's HTML based on its current state.
    const { name, count } = this.state;
    return `
      <h2>Welcome to AppBana Studio, ${name}!</h2>
      <p>This is the first component built with our custom <strong>BaseElement</strong>.</p>
      <p>Counter: <strong>${count}</strong></p>
      <button id="increment-btn">Click Me</button>
    `;
  }

  connectedCallback() {
    // Set up event listeners after the component is added to the DOM.
    this.root.addEventListener('click', (e: Event) => {
      if ((e.target as HTMLElement).id === 'increment-btn') {
        this.setState({ count: this.state.count + 1 });
      }
    });
  }
}

// Define the custom element so it can be used in HTML.
customElements.define('studio-welcome', StudioWelcome);

