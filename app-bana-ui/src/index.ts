import { LitElement, html } from 'lit';
import { customElement } from 'lit/decorators.js';
import './schema-builder';
import './app-renderer';
import './components/StudioWelcome';
import './components/entity-explorer';
import demoPage from './demo/demo-page.json';
import { ensureCoreRegistered } from './core/registry';
import { renderPage } from './runtime/renderer/Renderer';
import { PageMeta } from './models/metadata';
import './styles/theme.css';

@customElement('app-root')
export class AppRoot extends LitElement {
  async firstUpdated() {
    const path = window.location.pathname;
    if (path.includes('/studio')) {
      await ensureCoreRegistered();
      if (path.includes('/studio') && !path.includes('/studio/builder')) {
        const host = this.renderRoot.querySelector('#studio-root') as HTMLElement | null;
        if (host) {
          renderPage(demoPage as PageMeta, host);
        }
      }
    }
  }

  render() {
    const path = window.location.pathname;
    if (path.includes('/builder') && !path.includes('/studio/builder')) {
      return html`<schema-builder></schema-builder>`;
    } else if (path.includes('/explorer')) {
      return html`<entity-explorer></entity-explorer>`;
    } else if (path.includes('/app')) {
      return html`<app-renderer></app-renderer>`;
    } else if (path.includes('/studio/builder')) {
      return html`<studio-builder-shell></studio-builder-shell>`;
    } else if (path.includes('/studio')) {
      return html`<div id="studio-root"></div>`;
    }
    return html`
      <h1>Welcome to AppBana Studio</h1>
      <p>
        <a href="/builder">Schema Builder</a> |
        <a href="/explorer">Entity Explorer</a> |
        <a href="/app">App Renderer</a>
      </p>
      <hr />
      <h2>BaseElement Test Component:</h2>
      <studio-welcome name="AppBana Team"></studio-welcome>
    `;
  }
}
