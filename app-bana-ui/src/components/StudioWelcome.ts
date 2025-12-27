import { LitElement, html, css } from 'lit';

export class StudioWelcome extends LitElement {
  static styles = css`
    :host {
      display: block;
      height: 100%;
      display: flex;
      align-items: center;
      justify-content: center;
      background: #f8f9fb;
      color: #6b7280;
    }
    .content {
      text-align: center;
    }
    h1 {
      font-size: 1.5rem;
      font-weight: 600;
      margin-bottom: 0.5rem;
      color: #111827;
    }
    p {
      font-size: 0.875rem;
    }
  `;

  render() {
    return html`
      <div class="content">
        <h1>Welcome to Studio</h1>
        <p>Select an app to start building.</p>
      </div>
    `;
  }
}

if (!customElements.get('appbana-welcome')) {
  customElements.define('appbana-welcome', StudioWelcome);
}
