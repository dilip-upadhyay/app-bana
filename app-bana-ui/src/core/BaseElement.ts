/**
 * Base class for all custom elements in the AppBana Studio framework.
 * It provides a minimal reactive rendering system and manages the component lifecycle.
 */
export abstract class BaseElement extends HTMLElement {
  protected root: ShadowRoot;
  protected state: Record<string, any> = {};

  constructor() {
    super();
    this.root = this.attachShadow({ mode: 'open' });
    this._mount();
  }

  /**
   * Lifecycle hook called when the component is first initialized.
   * Subclasses can override this to set up initial state.
   */
  protected onInit(): void {}

  /**
   * Subclasses must implement this method to return the HTML string for the component's template.
   */
  protected abstract render(): string;

  /**
   * Optional method for subclasses to provide scoped CSS styles as a string.
   */
  protected styles(): string {
    return '';
  }

  /**
   * Updates a portion of the component's state and triggers a re-render if the state has changed.
   * @param patch A partial state object.
   */
  protected setState(patch: Record<string, any>): void {
    let changed = false;
    for (const key of Object.keys(patch)) {
      if (this.state[key] !== patch[key]) {
        this.state[key] = patch[key];
        changed = true;
      }
    }
    if (changed) {
      this.requestRender();
    }
  }

  /**
   * Standard Web Component lifecycle callback.
   * Reflects attribute changes to the component's state.
   */
  attributeChangedCallback(name: string, _oldValue: string | null, newValue: string | null) {
    // Basic reflection from attribute to state
    if (this.state[name] !== newValue) {
        this.setState({ [name]: newValue });
    }
  }

  private _mount() {
    this.onInit();
    this._renderInternal();
  }

  protected requestRender() { this._renderInternal(); }

  private _renderInternal() {
    const template = `
      ${this.styles() ? `<style>${this.styles()}</style>` : ''}
      ${this.render()}
    `;
    this.root.innerHTML = template;
  }
}
