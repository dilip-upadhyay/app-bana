import { LitElement, html, css } from 'lit';
import { customElement } from 'lit/decorators.js';

@customElement('component-gallery')
export class ComponentGallery extends LitElement {
  static styles = css`
    :host { display:block; padding:16px; font-family: var(--font-sans, system-ui); }
    h2 { margin:0 0 12px; font-size:18px; font-weight:600; }
    .grid { display:grid; grid-template-columns: repeat(auto-fill,minmax(180px,1fr)); gap:16px; }
    .card { border:1px solid var(--color-border, #d0d7de); background: var(--color-surface, #fff); border-radius:8px; padding:12px; box-shadow: var(--shadow-xs, 0 1px 2px rgba(0,0,0,.06)); }
    .card header { font-size:12px; text-transform:uppercase; letter-spacing:.5px; color:#555; margin-bottom:8px; }
    studio-container { display:block; }
  `;

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

