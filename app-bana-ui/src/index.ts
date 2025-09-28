import { LitElement, html } from 'lit';
import { customElement } from 'lit/decorators.js';
import './schema-builder';
import './app-renderer';
import './components/StudioWelcome';

@customElement('app-root')
export class AppRoot extends LitElement {
  render() {
    const path = window.location.pathname;
    if (path.startsWith('/builder')) {
      return html`<schema-builder></schema-builder>`;
    } else if (path.startsWith('/app')) {
      return html`<app-renderer></app-renderer>`;
    }
    return html`
      <h1>Welcome to AppBana Studio</h1>
      <p>
        <a href="/builder">Go to Schema Builder</a> |
        <a href="/app">Go to App Renderer</a>
      </p>
      <hr />
      <h2>BaseElement Test Component:</h2>
      <studio-welcome name="AppBana Team"></studio-welcome>
    `;
  }
}

