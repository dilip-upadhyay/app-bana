import { LitElement, html, css, unsafeCSS } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import styles from './app-sidebar.css?inline';

interface NavItem { label: string; path: string; icon?: string; }

@customElement('app-sidebar')
export class AppSidebar extends LitElement {
  static styles = css`${unsafeCSS(styles)}`;

  private nav: NavItem[] = [
    { label: 'Home', path: '/', icon: '🏠' },
    { label: 'Schemas', path: '/builder', icon: '🧬' },
    { label: 'Explorer', path: '/explorer', icon: '🗂️' },
    { label: 'Runtime', path: '/app', icon: '▶️' },
    { label: 'Studio', path: '/studio', icon: '🎨' }
  ];

  @state() private current = window.location.pathname;

  connectedCallback(): void {
    super.connectedCallback();
    window.addEventListener('popstate', this.onNav);
  }

  disconnectedCallback(): void {
    super.disconnectedCallback();
    window.removeEventListener('popstate', this.onNav);
  }

  private onNav = () => {
    this.current = window.location.pathname;
  };

  private isActive(item: NavItem) {
    if (item.path === '/') return this.current === '/';
    return this.current.startsWith(item.path);
  }

  private clickNav(e: MouseEvent, item: NavItem) {
    if (e.metaKey || e.ctrlKey || e.shiftKey || e.altKey || e.button !== 0) return;
    e.preventDefault();
    if (item.path !== window.location.pathname) {
      window.history.pushState({}, '', item.path);
      this.current = item.path;
      window.dispatchEvent(new Event('popstate'));
    }
  }

  render() {
    return html`
      <div class="inner">
        <div class="logo">
          <span>A</span>
          AppBana
        </div>

        <div class="section">Navigation</div>

        <nav>
          <ul>
            ${this.nav.map(n => html`
              <li>
                <a
                  class=${this.isActive(n) ? 'active' : ''}
                  href=${n.path}
                  @click=${(e: MouseEvent) => this.clickNav(e, n)}>
                  <span class="icon">${n.icon || ''}</span>
                  ${n.label}
                </a>
              </li>
            `)}
          </ul>
        </nav>

        <footer>
          <div>v0.1 design system</div>
          <div style="margin-top:4px;">© ${new Date().getFullYear()} AppBana</div>
        </footer>
      </div>
    `;
  }
}
