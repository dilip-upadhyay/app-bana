import { LitElement, html, css } from 'lit';
import { customElement, state } from 'lit/decorators.js';

interface NavItem { label: string; path: string; icon?: string; }

@customElement('app-sidebar')
export class AppSidebar extends LitElement {
  static styles = css`
    :host { display:block; height:100%; }
    .inner { display:flex; flex-direction:column; height:100%; }
    .logo { font-size:16px; font-weight:600; padding:16px 16px 8px; letter-spacing:.5px; display:flex; align-items:center; gap:6px; }
    .logo span { background:var(--color-brand); color:#fff; width:20px; height:20px; display:inline-flex; align-items:center; justify-content:center; border-radius:4px; font-size:11px; box-shadow:var(--shadow-xs); }
    nav { flex:1; overflow:auto; padding:4px 8px 12px; }
    ul { margin:0; padding:0; list-style:none; }
    li { margin:2px 0; }
    a { text-decoration:none; display:flex; gap:10px; padding:8px 10px; font-size:13px; font-weight:500; color:var(--color-text); border-radius:6px; align-items:center; line-height:1.2; position:relative; }
    a .icon { font-size:14px; opacity:.85; width:18px; text-align:center; }
    a:hover { background:var(--color-surface-alt); }
    a.active { background:var(--color-brand); color:#fff; }
    a.active .icon { opacity:1; }
    footer { padding:12px 14px; font-size:11px; border-top:1px solid var(--color-border); color:var(--color-text-secondary); }
    .section { margin:12px 10px 4px; font-size:10px; font-weight:600; letter-spacing:.5px; text-transform:uppercase; color:var(--color-text-secondary); }
  `;

  private nav: NavItem[] = [
    { label: 'Home', path: '/', icon: '🏠' },
    { label: 'Schemas', path: '/builder', icon: '🧬' },
    { label: 'Explorer', path: '/explorer', icon: '🗂️' },
    { label: 'Runtime', path: '/app', icon: '▶️' },
    { label: 'Studio', path: '/studio', icon: '🎨' },
    { label: 'Gallery', path: '/gallery', icon: '🧩' }
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
  private onNav = () => { this.current = window.location.pathname; };

  private isActive(item: NavItem) {
    if (item.path === '/') return this.current === '/';
    return this.current.startsWith(item.path);
  }

  private clickNav(e: MouseEvent, item: NavItem) {
    if (e.metaKey || e.ctrlKey || e.shiftKey || e.altKey || e.button !== 0) return; // let browser do default
    e.preventDefault();
    if (item.path !== window.location.pathname) {
      window.history.pushState({}, '', item.path);
      this.current = item.path;
      // Dispatch a custom event so outer app-root can re-render route
      window.dispatchEvent(new Event('popstate'));
    }
  }

  render() {
    return html`
      <div class="inner">
        <div class="logo"><span>A</span>AppBana</div>
        <div class="section">Navigation</div>
        <nav>
          <ul>
            ${this.nav.map(n => html`<li><a class=${this.isActive(n)?'active':''} href=${n.path} @click=${(e:MouseEvent)=>this.clickNav(e,n)}><span class="icon">${n.icon||''}</span>${n.label}</a></li>`)}
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
