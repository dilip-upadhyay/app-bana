import { LitElement, html } from 'lit';
import { customElement } from 'lit/decorators.js';
import './schema-builder';
import './app-renderer';
import './components/StudioWelcome';
import './components/entity-explorer'; // NEW explorer component
// --- AppBana Studio Runtime Demo ---
import { registerComponent } from './core/registry';
import { renderPage } from './runtime/renderer/Renderer';
import { ContainerElement } from './components/ContainerElement';
import { TextElement } from './components/TextElement';
import { ButtonElement } from './components/ButtonElement';
import { PageMeta } from './models/metadata';

// Register demo components
registerComponent('container', ContainerElement);
registerComponent('text', TextElement);
registerComponent('button', ButtonElement);

// Demo PageMeta JSON
const demoPage: PageMeta = {
  id: 'page1',
  path: '/',
  name: 'Demo Page',
  type: 'page',
  rootId: 'container1',
  nodes: [
    {
      id: 'container1',
      type: 'container',
      props: {},
      children: ['text1', 'button1'],
      style: { classes: ['demo-container'] }
    },
    {
      id: 'text1',
      type: 'text',
      props: { text: 'Hello from metadata-driven UI!' }
    },
    {
      id: 'button1',
      type: 'button',
      props: { label: 'Click Me' }
    }
  ]
};

// Render demo page if on /demo path
if (window.location.pathname === '/demo') {
  document.body.innerHTML = '<div id="demo-root"></div>';
  const container = document.getElementById('demo-root')!;
  renderPage(demoPage, container);
}

@customElement('app-root')
export class AppRoot extends LitElement {
  render() {
    const path = window.location.pathname;
    if (path.startsWith('/builder')) {
      return html`<schema-builder></schema-builder>`;
    } else if (path.startsWith('/explorer')) {
      return html`<entity-explorer></entity-explorer>`;
    } else if (path.startsWith('/app')) {
      return html`<app-renderer></app-renderer>`;
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
