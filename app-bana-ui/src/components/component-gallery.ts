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
          <appbana-button label="Sample Button" variant="primary"></appbana-button>
        </div>
        <div class="card">
          <header>Container</header>
          <appbana-container variant="outlined">
            <appbana-text text="Container sample text"></appbana-text>
          </appbana-container>
        </div>
      </div>
    `;
  }
}
