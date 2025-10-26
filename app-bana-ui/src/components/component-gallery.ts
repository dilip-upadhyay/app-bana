import { LitElement, html, css, unsafeCSS } from 'lit';
import { customElement } from 'lit/decorators.js';
import styles from './component-gallery.css?inline';

@customElement('component-gallery')
export class ComponentGallery extends LitElement {
  static styles = css`${unsafeCSS(styles)}`;

  render() {
    return html`
      <h2>Component Gallery</h2>
      <div class="grid">
        <div class="card">
          <header>Button</header>
          <studio-button label="Sample Button" variant="primary"></studio-button>
        </div>
        <div class="card">
          <header>Container</header>
          <studio-container variant="outlined">
            <studio-text text="Container sample text"></studio-text>
          </studio-container>
        </div>
      </div>
    `;
  }
}
