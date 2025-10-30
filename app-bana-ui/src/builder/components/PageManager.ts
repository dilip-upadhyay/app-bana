import { LitElement, html, css, unsafeCSS } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { initStore, initNewPageStore, currentStore } from '../store/TreeStore';
import { appStore } from '../store/AppStore';
import type { PageMeta, ComponentNode } from '../../models/metadata';
import styles from './PageManager.css?inline';

@customElement('studio-page-manager')
export class PageManager extends LitElement {
  static styles = css`${unsafeCSS(styles)}`;

  @state() private currentApp = appStore.getCurrentApp();
  @state() private pages: PageMeta[] = [];
  @state() private currentPageId: string | null = null;
  @state() private showCreateModal = false;
  @state() private showTemplateModal = false;

  // Form state
  @state() private formName = '';
  @state() private formPath = '';

  // Template selection state - for custom builder
  @state() private includeNav = false;
  @state() private includeSidenav = false;
  @state() private includeFooter = false;
  @state() private includeMain = true; // Always include main by default

  // Pre-built template selection
  @state() private selectedTemplate: 'custom' | 'login' | 'dashboard' | 'contact' | 'landing' | 'profile' | 'data-table' = 'custom';

  // Track if we're switching to a newly created page
  private isNewPage = false;

  connectedCallback() {
    super.connectedCallback();

    // Listen to app changes
    appStore.onChange(() => {
      this.currentApp = appStore.getCurrentApp();
      this.loadPages();
    });

    // Listen to store changes
    if (currentStore) {
      currentStore.onChange(() => {
        this.saveCurrentPage();
      });
    }

    this.loadPages();
  }

  private loadPages() {
    if (!this.currentApp) {
      this.pages = [];
      this.currentPageId = null;
      console.log('[PageManager] No app selected - clearing pages');
      return;
    }

    console.log('[PageManager] Loading pages for app:', this.currentApp.name, 'Pages:', this.currentApp.pages);

    // Load all pages for current app
    this.pages = this.currentApp.pages
      .map((pageId: string) => appStore.loadPage(this.currentApp!.id, pageId))
      .filter((page): page is PageMeta => page !== undefined);

    console.log('[PageManager] Loaded', this.pages.length, 'pages:', this.pages.map(p => p.name));

    // Reset currentPageId if it doesn't belong to current app, or set it if not set
    const currentPageExists = this.pages.some(p => p.id === this.currentPageId);
    if (!currentPageExists || !this.currentPageId) {
      const newPageId = this.currentApp.defaultPage || (this.pages.length > 0 ? this.pages[0].id : null);
      console.log('[PageManager] Switching to', currentPageExists ? 'existing' : 'new', 'page:', newPageId);
      this.currentPageId = newPageId;
      if (this.currentPageId) {
        this.switchToPage(this.currentPageId);
      } else {
        // No pages available - clear the store
        this.clearStore();
        console.log('[PageManager] No pages available - cleared store');
      }
    } else {
      // Current page exists in new app, just refresh it
      console.log('[PageManager] Refreshing current page:', this.currentPageId);
      this.switchToPage(this.currentPageId);
    }
  }

  private switchToPage(pageId: string) {
    if (!this.currentApp) return;

    const page = appStore.loadPage(this.currentApp.id, pageId);
    if (page) {
      this.currentPageId = pageId;

      // Reinitialize TreeStore with this page
      // If this is a newly created page, skip loading any existing draft
      if (this.isNewPage) {
        console.log('[PageManager] Initializing NEW page store (skipping draft)');
        initNewPageStore(page);
        this.isNewPage = false; // Reset flag
      } else {
        console.log('[PageManager] Initializing existing page store (loading draft if exists)');
        initStore(page);
      }

      // Re-register onChange listener for the new store
      if (currentStore) {
        currentStore.onChange(() => {
          this.saveCurrentPage();
        });
      }

      console.log('[PageManager] Switched to page:', pageId, page);
    }
  }

  private saveCurrentPage() {
    if (!this.currentApp || !this.currentPageId || !currentStore) return;

    const page = currentStore.getPage();
    appStore.savePage(this.currentApp.id, page);
  }

  private handleCreatePage() {
    // Reset template selections
    this.selectedTemplate = 'custom';
    this.includeNav = false;
    this.includeSidenav = false;
    this.includeFooter = false;
    this.includeMain = true;

    this.showCreateModal = true;
    this.formName = '';
    this.formPath = '/new-page';
  }

  private handleCloseModal() {
    this.showCreateModal = false;
    this.showTemplateModal = false;
  }

  private handleNextToTemplate = (e: Event) => {
    e.preventDefault();
    e.stopPropagation();

    if (!this.formName.trim()) {
      alert('Please enter a page name');
      return;
    }
    
    // Move to template selection using setTimeout to ensure clean state transition
    this.showCreateModal = false;
    
    // Use setTimeout to ensure the first modal is fully unmounted before showing the second
    setTimeout(() => {
      this.showTemplateModal = true;
      this.requestUpdate();
    }, 50);
  }

  private handleBackToBasicInfo() {
    this.showTemplateModal = false;
    this.showCreateModal = true;
  }

  private handleSubmitCreate(e?: Event) {
    if (e) e.preventDefault();

    if (!this.currentApp || !this.formName.trim()) {
      alert('Please enter a page name');
      return;
    }

    // Generate unique page ID
    const pageId = this.generatePageId(this.formName);

    console.log('[PageManager] Creating new page with ID:', pageId);

    // Build page structure based on template selections
    const newPage = this.buildPageFromTemplate(pageId);

    console.log('[PageManager] New page data:', newPage);

    // Clear any existing draft for this page ID (in case it was used before)
    const draftKey = `studio.draft.${pageId}`;
    console.log('[PageManager] Clearing existing draft:', draftKey);
    localStorage.removeItem(draftKey);

    // Add page to app
    appStore.addPage(this.currentApp.id, newPage);

    // Switch to new page - mark as new so we skip draft loading
    this.isNewPage = true;
    this.currentPageId = pageId;
    this.switchToPage(pageId);

    // Close both modals
    this.showCreateModal = false;
    this.showTemplateModal = false;

    this.showToast(`✅ Created page: ${this.formName}`);
  }

  private handleDeletePage(pageId: string, pageName: string, e: Event) {
    e.stopPropagation();

    if (!this.currentApp) return;

    if (!confirm(`Delete page "${pageName}"?`)) {
      return;
    }

    try {
      // Clear the draft from localStorage before deleting
      const draftKey = `studio.draft.${pageId}`;
      console.log('[PageManager] Clearing draft for deleted page:', draftKey);
      localStorage.removeItem(draftKey);

      appStore.removePage(this.currentApp.id, pageId);

      // Switch to another page if any exist
      const remainingPages = this.pages.filter(p => p.id !== pageId);
      if (remainingPages.length > 0) {
        this.currentPageId = remainingPages[0].id;
        this.switchToPage(this.currentPageId);
      } else {
        // No pages left - clear current page and store
        this.currentPageId = null;
        this.clearStore();
        console.log('[PageManager] No pages left in app - cleared store');
      }

      this.showToast(`🗑️ Deleted page: ${pageName}`);
    } catch (error) {
      alert(error instanceof Error ? error.message : 'Failed to delete page');
    }
  }

  /**
   * Build pre-built page templates with full component trees
   */
  private buildPrebuiltTemplate(pageId: string, template: string): PageMeta {
    let nodes: ComponentNode[] = [];
    const nodeCounter = 1;

    switch (template) {
      case 'login':
        nodes = this.buildLoginTemplate(nodeCounter);
        break;
      case 'dashboard':
        nodes = this.buildDashboardTemplate(nodeCounter);
        break;
      case 'contact':
        nodes = this.buildContactTemplate(nodeCounter);
        break;
      case 'landing':
        nodes = this.buildLandingTemplate(nodeCounter);
        break;
      case 'profile':
        nodes = this.buildProfileTemplate(nodeCounter);
        break;
      case 'data-table':
        nodes = this.buildDataTableTemplate(nodeCounter);
        break;
      default:
        // Fall back to empty container
        nodes = [{
          id: 'root',
          type: 'container',
          props: { style: 'padding: 2rem;' },
          children: []
        }];
    }

    return {
      metaVersion: 1,
      id: pageId,
      name: this.formName.trim(),
      path: this.formPath.trim() || `/${pageId}`,
      rootId: 'root',
      nodes: nodes
    };
  }

  /**
   * Build Login Page Template
   * Includes: centered card with email, password, submit button
   */
  private buildLoginTemplate(_startId: number): ComponentNode[] {
    const nodes: ComponentNode[] = [];

    // Root container
    nodes.push({
      id: 'root',
      type: 'container',
      props: {
        style: 'display: flex; align-items: center; justify-content: center; min-height: 100vh; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);'
      },
      children: ['card-1']
    });

    // Login card
    nodes.push({
      id: 'card-1',
      type: 'container',
      props: {
        className: 'login-card',
        style: 'background: white; padding: 3rem; border-radius: 12px; box-shadow: 0 20px 60px rgba(0,0,0,0.3); width: 100%; max-width: 420px;'
      },
      children: ['title-1', 'subtitle-1', 'form-1']
    });

    // Title
    nodes.push({
      id: 'title-1',
      type: 'text',
      props: {
        tag: 'h1',
        text: 'Welcome Back',
        style: 'margin: 0 0 0.5rem 0; font-size: 28px; font-weight: 700; color: #1f2937;'
      }
    });

    // Subtitle
    nodes.push({
      id: 'subtitle-1',
      type: 'text',
      props: {
        tag: 'p',
        text: 'Sign in to your account',
        style: 'margin: 0 0 2rem 0; font-size: 14px; color: #6b7280;'
      }
    });

    // Form container
    nodes.push({
      id: 'form-1',
      type: 'container',
      props: {
        tag: 'form',
        style: 'display: flex; flex-direction: column; gap: 1.5rem;'
      },
      children: ['email-group-1', 'password-group-1', 'remember-1', 'submit-1', 'signup-1']
    });

    // Email field group
    nodes.push({
      id: 'email-group-1',
      type: 'container',
      props: { style: 'display: flex; flex-direction: column; gap: 0.5rem;' },
      children: ['email-label-1', 'email-input-1']
    });

    nodes.push({
      id: 'email-label-1',
      type: 'text',
      props: {
        tag: 'label',
        text: 'Email Address',
        style: 'font-size: 14px; font-weight: 500; color: #374151;'
      }
    });

    nodes.push({
      id: 'email-input-1',
      type: 'input',
      props: {
        type: 'email',
        placeholder: 'you@example.com',
        required: true,
        style: 'padding: 0.75rem; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px;'
      }
    });

    // Password field group
    nodes.push({
      id: 'password-group-1',
      type: 'container',
      props: { style: 'display: flex; flex-direction: column; gap: 0.5rem;' },
      children: ['password-label-1', 'password-input-1']
    });

    nodes.push({
      id: 'password-label-1',
      type: 'text',
      props: {
        tag: 'label',
        text: 'Password',
        style: 'font-size: 14px; font-weight: 500; color: #374151;'
      }
    });

    nodes.push({
      id: 'password-input-1',
      type: 'input',
      props: {
        type: 'password',
        placeholder: '••••••••',
        required: true,
        style: 'padding: 0.75rem; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px;'
      }
    });

    // Remember me checkbox
    nodes.push({
      id: 'remember-1',
      type: 'container',
      props: {
        style: 'display: flex; align-items: center; gap: 0.5rem;'
      },
      children: ['remember-checkbox-1', 'remember-label-1']
    });

    nodes.push({
      id: 'remember-checkbox-1',
      type: 'input',
      props: {
        type: 'checkbox',
        style: 'width: 16px; height: 16px;'
      }
    });

    nodes.push({
      id: 'remember-label-1',
      type: 'text',
      props: {
        tag: 'label',
        text: 'Remember me',
        style: 'font-size: 14px; color: #374151;'
      }
    });

    // Submit button
    nodes.push({
      id: 'submit-1',
      type: 'button',
      props: {
        text: 'Sign In',
        className: 'btn-primary',
        style: 'padding: 0.875rem; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; border: none; border-radius: 6px; font-size: 16px; font-weight: 600; cursor: pointer;'
      }
    });

    // Signup link
    nodes.push({
      id: 'signup-1',
      type: 'text',
      props: {
        tag: 'p',
        text: 'Don\'t have an account? <a href="/signup" style="color: #667eea; font-weight: 600;">Sign up</a>',
        style: 'margin: 1rem 0 0 0; text-align: center; font-size: 14px; color: #6b7280;'
      }
    });

    return nodes;
  }

  /**
   * Build Dashboard Template  
   * Includes: header, sidebar, main content with KPI cards
   */
  private buildDashboardTemplate(_startId: number): ComponentNode[] {
    // This is a large template - I'll create a simplified version for now
    // You can expand it later with all the KPI cards
    const nodes: ComponentNode[] = [];

    nodes.push({
      id: 'root',
      type: 'container',
      props: {
        style: 'display: flex; flex-direction: column; min-height: 100vh;'
      },
      children: ['header-1', 'content-wrapper-1']
    });

    // Header
    nodes.push({
      id: 'header-1',
      type: 'container',
      props: {
        tag: 'header',
        style: 'display: flex; justify-content: space-between; align-items: center; padding: 1rem 2rem; background: #1f2937; color: white;'
      },
      children: ['logo-1', 'user-menu-1']
    });

    nodes.push({
      id: 'logo-1',
      type: 'text',
      props: {
        tag: 'h1',
        text: '📊 Dashboard',
        style: 'margin: 0; font-size: 20px; font-weight: 700;'
      }
    });

    nodes.push({
      id: 'user-menu-1',
      type: 'text',
      props: {
        tag: 'span',
        text: 'John Doe 👤',
        style: 'font-size: 14px;'
      }
    });

    // Content wrapper
    nodes.push({
      id: 'content-wrapper-1',
      type: 'container',
      props: {
        style: 'display: flex; flex: 1;'
      },
      children: ['sidebar-1', 'main-1']
    });

    // Sidebar
    nodes.push({
      id: 'sidebar-1',
      type: 'container',
      props: {
        tag: 'aside',
        style: 'width: 250px; background: #f3f4f6; padding: 1.5rem 1rem; border-right: 1px solid #e5e7eb;'
      },
      children: ['nav-title-1', 'nav-home-1', 'nav-analytics-1', 'nav-reports-1']
    });

    nodes.push({
      id: 'nav-title-1',
      type: 'text',
      props: {
        tag: 'h3',
        text: 'Navigation',
        style: 'margin: 0 0 1rem 0; font-size: 12px; font-weight: 600; color: #6b7280; text-transform: uppercase;'
      }
    });

    ['home', 'analytics', 'reports'].forEach((item, idx) => {
      nodes.push({
        id: `nav-${item}-1`,
        type: 'text',
        props: {
          tag: 'a',
          text: `${['🏠', '📈', '📄'][idx]} ${item.charAt(0).toUpperCase() + item.slice(1)}`,
          href: '#',
          style: 'display: block; padding: 0.75rem 1rem; margin-bottom: 0.25rem; border-radius: 6px; color: #374151; text-decoration: none; font-size: 14px;'
        }
      });
    });

    // Main content
    nodes.push({
      id: 'main-1',
      type: 'container',
      props: {
        tag: 'main',
        style: 'flex: 1; padding: 2rem;'
      },
      children: ['main-title-1', 'kpi-grid-1']
    });

    nodes.push({
      id: 'main-title-1',
      type: 'text',
      props: {
        tag: 'h2',
        text: 'Overview',
        style: 'margin: 0 0 1.5rem 0; font-size: 24px; font-weight: 700; color: #111827;'
      }
    });

    // KPI Grid
    nodes.push({
      id: 'kpi-grid-1',
      type: 'container',
      props: {
        style: 'display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 1.5rem;'
      },
      children: ['kpi-users-1', 'kpi-revenue-1', 'kpi-orders-1']
    });

    const kpis = [
      { id: 'users', icon: '👥', title: 'Total Users', value: '12,458', color: '#3b82f6' },
      { id: 'revenue', icon: '💰', title: 'Revenue', value: '$45,231', color: '#10b981' },
      { id: 'orders', icon: '📦', title: 'Orders', value: '3,842', color: '#f59e0b' }
    ];

    kpis.forEach(kpi => {
      nodes.push({
        id: `kpi-${kpi.id}-1`,
        type: 'container',
        props: {
          style: `background: white; padding: 1.5rem; border-radius: 12px; border-left: 4px solid ${kpi.color}; box-shadow: 0 1px 3px rgba(0,0,0,0.1);`
        },
        children: [`${kpi.id}-header-1`, `${kpi.id}-value-1`]
      });

      nodes.push({
        id: `${kpi.id}-header-1`,
        type: 'text',
        props: {
          tag: 'div',
          text: `${kpi.icon} ${kpi.title}`,
          style: 'font-size: 14px; color: #6b7280; margin-bottom: 0.5rem;'
        }
      });

      nodes.push({
        id: `${kpi.id}-value-1`,
        type: 'text',
        props: {
          tag: 'div',
          text: kpi.value,
          style: 'font-size: 32px; font-weight: 700; color: #111827;'
        }
      });
    });

    return nodes;
  }

  /**
   * Build Contact Form Template
   */
  private buildContactTemplate(_startId: number): ComponentNode[] {
    const nodes: ComponentNode[] = [];

    nodes.push({
      id: 'root',
      type: 'container',
      props: {
        style: 'min-height: 100vh; background: #f9fafb; padding: 3rem 1rem;'
      },
      children: ['form-wrapper-1']
    });

    nodes.push({
      id: 'form-wrapper-1',
      type: 'container',
      props: {
        style: 'max-width: 600px; margin: 0 auto; background: white; padding: 2.5rem; border-radius: 12px; box-shadow: 0 4px 6px rgba(0,0,0,0.1);'
      },
      children: ['form-title-1', 'contact-form-1']
    });

    nodes.push({
      id: 'form-title-1',
      type: 'text',
      props: {
        tag: 'h1',
        text: '✉️ Get In Touch',
        style: 'margin: 0 0 2rem 0; font-size: 32px; font-weight: 700; color: #111827;'
      }
    });

    nodes.push({
      id: 'contact-form-1',
      type: 'container',
      props: {
        tag: 'form',
        style: 'display: flex; flex-direction: column; gap: 1.5rem;'
      },
      children: ['name-input-1', 'email-input-1', 'message-input-1', 'submit-1']
    });

    nodes.push({
      id: 'name-input-1',
      type: 'input',
      props: {
        type: 'text',
        placeholder: 'Your Name',
        required: true,
        style: 'padding: 0.75rem; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px;'
      }
    });

    nodes.push({
      id: 'email-input-1',
      type: 'input',
      props: {
        type: 'email',
        placeholder: 'Your Email',
        required: true,
        style: 'padding: 0.75rem; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px;'
      }
    });

    nodes.push({
      id: 'message-input-1',
      type: 'textarea',
      props: {
        placeholder: 'Your Message',
        required: true,
        rows: 6,
        style: 'padding: 0.75rem; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; font-family: inherit; resize: vertical;'
      }
    });

    nodes.push({
      id: 'submit-1',
      type: 'button',
      props: {
        text: '📤 Send Message',
        style: 'padding: 1rem; background: linear-gradient(135deg, #3b82f6 0%, #1d4ed8 100%); color: white; border: none; border-radius: 6px; font-size: 16px; font-weight: 600; cursor: pointer;'
      }
    });

    return nodes;
  }

  /**
   * Build Landing Page Template
   */
  private buildLandingTemplate(_startId: number): ComponentNode[] {
    const nodes: ComponentNode[] = [];

    nodes.push({
      id: 'root',
      type: 'container',
      props: { style: 'min-height: 100vh;' },
      children: ['hero-1', 'features-1', 'footer-1']
    });

    // Hero section
    nodes.push({
      id: 'hero-1',
      type: 'container',
      props: {
        style: 'background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 6rem 2rem; text-align: center;'
      },
      children: ['hero-title-1', 'hero-subtitle-1', 'hero-cta-1']
    });

    nodes.push({
      id: 'hero-title-1',
      type: 'text',
      props: {
        tag: 'h1',
        text: '🚀 Build Amazing Apps Fast',
        style: 'margin: 0 0 1rem 0; font-size: 48px; font-weight: 700;'
      }
    });

    nodes.push({
      id: 'hero-subtitle-1',
      type: 'text',
      props: {
        tag: 'p',
        text: 'The no-code platform for enterprise applications',
        style: 'margin: 0 0 2rem 0; font-size: 20px;'
      }
    });

    nodes.push({
      id: 'hero-cta-1',
      type: 'button',
      props: {
        text: 'Get Started Free →',
        style: 'padding: 1rem 2.5rem; background: white; color: #667eea; border: none; border-radius: 8px; font-size: 18px; font-weight: 600; cursor: pointer;'
      }
    });

    // Features section
    nodes.push({
      id: 'features-1',
      type: 'container',
      props: {
        style: 'padding: 5rem 2rem; background: #f9fafb; display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 2rem; max-width: 1200px; margin: 0 auto;'
      },
      children: ['feature-1-1', 'feature-2-1', 'feature-3-1']
    });

    const features = [
      { id: 'feature-1-1', icon: '⚡', title: 'Lightning Fast', desc: 'Build 10x faster' },
      { id: 'feature-2-1', icon: '🔒', title: 'Secure by Default', desc: 'Enterprise-grade security' },
      { id: 'feature-3-1', icon: '🎨', title: 'Beautiful Design', desc: 'Professional templates' }
    ];

    features.forEach(feature => {
      nodes.push({
        id: feature.id,
        type: 'container',
        props: {
          style: 'background: white; padding: 2rem; border-radius: 12px; box-shadow: 0 4px 6px rgba(0,0,0,0.1); text-align: center;'
        },
        children: [`${feature.id}-icon`, `${feature.id}-title`, `${feature.id}-desc`]
      });

      nodes.push({
        id: `${feature.id}-icon`,
        type: 'text',
        props: {
          tag: 'div',
          text: feature.icon,
          style: 'font-size: 48px; margin-bottom: 1rem;'
        }
      });

      nodes.push({
        id: `${feature.id}-title`,
        type: 'text',
        props: {
          tag: 'h3',
          text: feature.title,
          style: 'margin: 0 0 0.75rem 0; font-size: 24px; font-weight: 600;'
        }
      });

      nodes.push({
        id: `${feature.id}-desc`,
        type: 'text',
        props: {
          tag: 'p',
          text: feature.desc,
          style: 'margin: 0; font-size: 16px; color: #6b7280;'
        }
      });
    });

    // Footer
    nodes.push({
      id: 'footer-1',
      type: 'container',
      props: {
        style: 'padding: 2rem; background: #1f2937; color: white; text-align: center;'
      },
      children: ['footer-text-1']
    });

    nodes.push({
      id: 'footer-text-1',
      type: 'text',
      props: {
        tag: 'p',
        text: '© 2025 Your Company',
        style: 'margin: 0; font-size: 14px;'
      }
    });

    return nodes;
  }

  /**
   * Build Profile Page Template
   */
  private buildProfileTemplate(_startId: number): ComponentNode[] {
    const nodes: ComponentNode[] = [];

    nodes.push({
      id: 'root',
      type: 'container',
      props: {
        style: 'min-height: 100vh; background: #f9fafb; padding: 2rem;'
      },
      children: ['profile-card-1']
    });

    nodes.push({
      id: 'profile-card-1',
      type: 'container',
      props: {
        style: 'max-width: 800px; margin: 0 auto; background: white; padding: 2.5rem; border-radius: 12px; box-shadow: 0 4px 6px rgba(0,0,0,0.1); text-align: center;'
      },
      children: ['avatar-1', 'name-1', 'title-1', 'bio-1', 'stats-grid-1']
    });

    nodes.push({
      id: 'avatar-1',
      type: 'text',
      props: {
        tag: 'div',
        text: '👤',
        style: 'font-size: 96px; margin-bottom: 1rem;'
      }
    });

    nodes.push({
      id: 'name-1',
      type: 'text',
      props: {
        tag: 'h1',
        text: 'John Doe',
        style: 'margin: 0 0 0.5rem 0; font-size: 32px; font-weight: 700;'
      }
    });

    nodes.push({
      id: 'title-1',
      type: 'text',
      props: {
        tag: 'p',
        text: 'Senior Product Designer',
        style: 'margin: 0 0 1rem 0; font-size: 16px; color: #6b7280;'
      }
    });

    nodes.push({
      id: 'bio-1',
      type: 'text',
      props: {
        tag: 'p',
        text: 'Passionate about creating beautiful user experiences',
        style: 'margin: 0 0 2rem 0; font-size: 14px; color: #374151;'
      }
    });

    // Stats grid
    nodes.push({
      id: 'stats-grid-1',
      type: 'container',
      props: {
        style: 'display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 2rem; padding-top: 2rem; border-top: 1px solid #e5e7eb;'
      },
      children: ['stat-projects-1', 'stat-followers-1', 'stat-posts-1']
    });

    const stats = [
      { id: 'projects', value: '48', label: 'Projects' },
      { id: 'followers', value: '2.5K', label: 'Followers' },
      { id: 'posts', value: '124', label: 'Posts' }
    ];

    stats.forEach(stat => {
      nodes.push({
        id: `stat-${stat.id}-1`,
        type: 'container',
        props: { style: 'text-align: center;' },
        children: [`stat-${stat.id}-value-1`, `stat-${stat.id}-label-1`]
      });

      nodes.push({
        id: `stat-${stat.id}-value-1`,
        type: 'text',
        props: {
          tag: 'div',
          text: stat.value,
          style: 'font-size: 28px; font-weight: 700; color: #111827; margin-bottom: 0.25rem;'
        }
      });

      nodes.push({
        id: `stat-${stat.id}-label-1`,
        type: 'text',
        props: {
          tag: 'div',
          text: stat.label,
          style: 'font-size: 14px; color: #6b7280;'
        }
      });
    });

    return nodes;
  }

  /**
   * Build Data Table Template
   */
  private buildDataTableTemplate(_startId: number): ComponentNode[] {
    const nodes: ComponentNode[] = [];

    nodes.push({
      id: 'root',
      type: 'container',
      props: {
        style: 'min-height: 100vh; background: #f9fafb; padding: 2rem;'
      },
      children: ['table-wrapper-1']
    });

    nodes.push({
      id: 'table-wrapper-1',
      type: 'container',
      props: {
        style: 'max-width: 1200px; margin: 0 auto; background: white; padding: 2rem; border-radius: 12px; box-shadow: 0 4px 6px rgba(0,0,0,0.1);'
      },
      children: ['table-header-1', 'search-1', 'table-placeholder-1']
    });

    nodes.push({
      id: 'table-header-1',
      type: 'text',
      props: {
        tag: 'h1',
        text: '📊 Data Table',
        style: 'margin: 0 0 2rem 0; font-size: 28px; font-weight: 700;'
      }
    });

    nodes.push({
      id: 'search-1',
      type: 'input',
      props: {
        type: 'text',
        placeholder: '🔍 Search...',
        style: 'width: 100%; padding: 0.75rem; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; margin-bottom: 1.5rem;'
      }
    });

    nodes.push({
      id: 'table-placeholder-1',
      type: 'text',
      props: {
        tag: 'div',
        text: '<div style="padding: 3rem; text-align: center; border: 2px dashed #e5e7eb; border-radius: 8px; color: #9ca3af;">Add data binding to populate table rows...</div>',
        style: ''
      }
    });

    return nodes;
  }

  private buildPageFromTemplate(pageId: string): PageMeta {
    // Check if using a pre-built template
    if (this.selectedTemplate !== 'custom') {
      return this.buildPrebuiltTemplate(pageId, this.selectedTemplate);
    }

    // Otherwise, build custom template from selected sections
    const nodes: ComponentNode[] = [];
    const rootChildren: string[] = [];

    // Build the page structure based on template selections
    let nodeCounter = 1;

    // Add Nav section if selected
    if (this.includeNav) {
      const navId = `nav-${nodeCounter++}`;
      nodes.push({
        id: navId,
        type: 'container',
        props: {
          className: 'nav-container',
          style: 'display: flex; justify-content: space-between; align-items: center; padding: 1rem 2rem; background: #1f2937; color: white; min-height: 60px;',
          'data-section': 'nav'
        },
        children: []
      });
      rootChildren.push(navId);
    }

    // Create main content wrapper (if sidenav is included, we need a flex layout)
    if (this.includeSidenav) {
      const contentWrapperId = `content-wrapper-${nodeCounter++}`;
      const contentWrapperChildren: string[] = [];

      // Add Sidenav
      const sidenavId = `sidenav-${nodeCounter++}`;
      nodes.push({
        id: sidenavId,
        type: 'container',
        props: {
          className: 'sidenav-container',
          style: 'width: 250px; background: #f3f4f6; padding: 1rem; min-height: 400px; border-right: 1px solid #e5e7eb;',
          'data-section': 'sidenav'
        },
        children: []
      });
      contentWrapperChildren.push(sidenavId);

      // Add Main section
      if (this.includeMain) {
        const mainId = `main-${nodeCounter++}`;
        nodes.push({
          id: mainId,
          type: 'container',
          props: {
            className: 'main-container',
            style: 'flex: 1; padding: 2rem; min-height: 400px;',
            'data-section': 'main'
          },
          children: []
        });
        contentWrapperChildren.push(mainId);
      }

      // Add the content wrapper
      nodes.push({
        id: contentWrapperId,
        type: 'container',
        props: {
          className: 'content-wrapper',
          style: 'display: flex; flex: 1;'
        },
        children: contentWrapperChildren
      });
      rootChildren.push(contentWrapperId);
    } else {
      // No sidenav, just add main directly
      if (this.includeMain) {
        const mainId = `main-${nodeCounter++}`;
        nodes.push({
          id: mainId,
          type: 'container',
          props: {
            className: 'main-container',
            style: 'padding: 2rem; min-height: 400px;',
            'data-section': 'main'
          },
          children: []
        });
        rootChildren.push(mainId);
      }
    }

    // Add Footer section if selected
    if (this.includeFooter) {
      const footerId = `footer-${nodeCounter++}`;
      nodes.push({
        id: footerId,
        type: 'container',
        props: {
          className: 'footer-container',
          style: 'padding: 2rem; background: #1f2937; color: white; text-align: center; min-height: 80px;',
          'data-section': 'footer'
        },
        children: []
      });
      rootChildren.push(footerId);
    }

    // Create root container
    const rootNode: ComponentNode = {
      id: 'root',
      type: 'container',
      props: {
        style: 'display: flex; flex-direction: column; min-height: 100vh;'
      },
      children: rootChildren
    };

    nodes.unshift(rootNode); // Add root at the beginning

    return {
      metaVersion: 1,
      id: pageId,
      name: this.formName.trim(),
      path: this.formPath.trim() || `/${pageId}`,
      rootId: 'root',
      nodes
    };
  }

  private clearStore() {
    // Clear the current store to ensure canvas is empty
    if (currentStore) {
      console.log('[PageManager] Clearing current store');
      // Create an empty page to clear the canvas
      const emptyPage: PageMeta = {
        metaVersion: 1,
        id: 'empty',
        name: 'Empty',
        path: '/empty',
        rootId: 'root',
        nodes: [
          {
            id: 'root',
            type: 'container',
            props: {},
            children: [],
          },
        ],
      };
      initStore(emptyPage, { persist: false });
    }
  }

  private generatePageId(name: string): string {
    let id = name.toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-|-$/g, '');

    let counter = 1;
    let uniqueId = id;
    while (this.pages.some(p => p.id === uniqueId)) {
      uniqueId = `${id}-${counter}`;
      counter++;
    }
    return uniqueId;
  }

  private showToast(message: string) {
    const toast = document.createElement('div');
    toast.style.cssText = `
      position: fixed;
      bottom: 24px;
      right: 24px;
      padding: 12px 20px;
      background: #111827;
      color: white;
      border-radius: 8px;
      font-size: 14px;
      z-index: 10000;
    `;
    toast.textContent = message;
    document.body.appendChild(toast);
    setTimeout(() => document.body.removeChild(toast), 3000);
  }

  render() {
    if (!this.currentApp) {
      return html`
        <div class="page-manager">
          <div class="no-app-message">
            <span>📱 No app selected</span>
            <span style="color: #9ca3af; font-size: 13px;">Create or select an app to manage pages</span>
          </div>
        </div>
      `;
    }

    // If app has 0 pages, show message
    if (this.pages.length === 0) {
      return html`
        <div class="page-manager">
          <div class="page-tabs">
            <div class="no-pages-message">
              <span>📄 No pages yet</span>
            </div>
            <button class="new-page-btn" @click=${this.handleCreatePage} title="Create new page">
              ➕ New Page
            </button>
          </div>
        </div>

        ${this.showCreateModal ? this.renderCreateModal() : ''}
        ${this.showTemplateModal ? this.renderTemplateModal() : ''}
      `;
    }

    return html`
      <div class="page-manager">
        <div class="page-tabs">
          ${this.pages.map(page => html`
            <div
              class="page-tab ${this.currentPageId === page.id ? 'active' : ''}"
              @click=${() => this.switchToPage(page.id)}
            >
              <span class="page-name">${page.name}</span>
              <button
                class="delete-page-btn"
                @click=${(e: Event) => this.handleDeletePage(page.id, page.name, e)}
                title="Delete page"
              >
                ✕
              </button>
            </div>
          `)}

          <button class="new-page-btn" @click=${this.handleCreatePage} title="Create new page">
            ➕ New Page
          </button>
        </div>
      </div>

      ${this.showCreateModal ? this.renderCreateModal() : ''}
      ${this.showTemplateModal ? this.renderTemplateModal() : ''}
    `;
  }

  private renderCreateModal() {
    return html`
      <div class="modal-overlay" @click=${this.handleCloseModal}>
        <div class="modal" @click=${(e: Event) => e.stopPropagation()}>
          <div class="modal-header">
            <h3>📄 Create New Page - Step 1</h3>
            <button class="modal-close" @click=${this.handleCloseModal}>×</button>
          </div>

          <form @submit=${this.handleNextToTemplate}>
            <div class="modal-body">
              <div class="form-group">
                <label for="page-name">Page Name *</label>
                <input
                  id="page-name"
                  type="text"
                  placeholder="Dashboard"
                  .value=${this.formName}
                  @input=${(e: Event) => this.formName = (e.target as HTMLInputElement).value}
                  required
                  autofocus
                />
                <div class="form-help">A descriptive name for this page</div>
              </div>

              <div class="form-group">
                <label for="page-path">URL Path *</label>
                <input
                  id="page-path"
                  type="text"
                  placeholder="/dashboard"
                  .value=${this.formPath}
                  @input=${(e: Event) => this.formPath = (e.target as HTMLInputElement).value}
                  required
                />
                <div class="form-help">The URL path for this page (e.g., /dashboard, /about)</div>
              </div>
            </div>

            <div class="modal-footer">
              <button type="button" class="btn" @click=${this.handleCloseModal}>
                Cancel
              </button>
              <button type="submit" class="btn btn-primary">
                Next →
              </button>
            </div>
          </form>
        </div>
      </div>
    `;
  }

  private renderTemplateModal() {
    const templates = [
      {
        id: 'login',
        icon: '🔐',
        name: 'Login Page',
        description: 'Email, password, submit button'
      },
      {
        id: 'dashboard',
        icon: '📊',
        name: 'Dashboard',
        description: 'Header, sidebar, KPI cards'
      },
      {
        id: 'contact',
        icon: '✉️',
        name: 'Contact Form',
        description: 'Name, email, message fields'
      },
      {
        id: 'landing',
        icon: '🚀',
        name: 'Landing Page',
        description: 'Hero, features, CTA, footer'
      },
      {
        id: 'profile',
        icon: '👤',
        name: 'Profile Page',
        description: 'Avatar, bio, stats'
      },
      {
        id: 'data-table',
        icon: '📋',
        name: 'Data Table',
        description: 'Search, filters, table, pagination'
      },
      {
        id: 'custom',
        icon: '🎨',
        name: 'Custom Builder',
        description: 'Build from scratch with sections'
      }
    ];

    return html`
      <div class="modal-overlay" @click=${this.handleCloseModal}>
        <div class="modal modal-wide" @click=${(e: Event) => e.stopPropagation()}>
          <div class="modal-header">
            <h3>🎨 Choose Template - Step 2</h3>
            <div class="header-actions">
              <button type="button" class="btn btn-primary" @click=${this.handleSubmitCreate}>
                ✓ Create Page
              </button>
              <button class="modal-close" @click=${this.handleCloseModal}>×</button>
            </div>
          </div>

          <div class="modal-body">
            <p class="template-help">Choose a ready-to-use template or build custom:</p>

            <div class="template-gallery">
              ${templates.map(template => html`
                <div
                  class="template-card ${this.selectedTemplate === template.id ? 'selected' : ''}"
                  @click=${() => this.selectedTemplate = template.id as any}
                >
                  <div class="template-card-icon">${template.icon}</div>
                  <h4 class="template-card-title">${template.name}</h4>
                  <p class="template-card-desc">${template.description}</p>
                  ${this.selectedTemplate === template.id ? html`<div class="template-card-check">✓</div>` : ''}
                </div>
              `)}
            </div>

            ${this.selectedTemplate === 'custom' ? html`
              <div class="custom-builder-section">
                <h4 style="margin: 2rem 0 1rem 0; font-size: 18px;">Select sections to include:</h4>
                <div class="template-options">
                  <div class="template-option ${this.includeNav ? 'selected' : ''}"
                       @click=${() => this.includeNav = !this.includeNav}>
                <div class="option-icon">🧭</div>
                <div class="option-content">
                  <h4>Navigation Bar</h4>
                  <p>Top navigation with logo and menu</p>
                </div>
                <div class="option-checkbox">
                  ${this.includeNav ? '✓' : ''}
                </div>
              </div>

              <div class="template-option ${this.includeSidenav ? 'selected' : ''}"
                   @click=${() => this.includeSidenav = !this.includeSidenav}>
                <div class="option-icon">📁</div>
                <div class="option-content">
                  <h4>Side Navigation</h4>
                  <p>Left sidebar for secondary navigation</p>
                </div>
                <div class="option-checkbox">
                  ${this.includeSidenav ? '✓' : ''}
                </div>
              </div>

              <div class="template-option selected disabled">
                <div class="option-icon">📄</div>
                <div class="option-content">
                  <h4>Main Content</h4>
                  <p>Primary content area (always included)</p>
                </div>
                <div class="option-checkbox">✓</div>
              </div>

              <div class="template-option ${this.includeFooter ? 'selected' : ''}"
                   @click=${() => this.includeFooter = !this.includeFooter}>
                <div class="option-icon">📝</div>
                <div class="option-content">
                  <h4>Footer</h4>
                  <p>Bottom footer section</p>
                </div>
                <div class="option-checkbox">
                  ${this.includeFooter ? '✓' : ''}
                </div>
              </div>
            </div>

            <div class="template-preview">
              <h4>Preview:</h4>
              <div class="preview-layout">
                ${this.includeNav ? html`<div class="preview-section nav">Nav</div>` : ''}
                <div class="preview-content">
                  ${this.includeSidenav ? html`<div class="preview-section sidenav">Sidenav</div>` : ''}
                  <div class="preview-section main">Main</div>
                </div>
                ${this.includeFooter ? html`<div class="preview-section footer">Footer</div>` : ''}
              </div>
            </div>
          </div>
        ` : ''}
      </div>

          <div class="modal-footer">
            <button type="button" class="btn" @click=${this.handleBackToBasicInfo}>
              ← Back
            </button>
          </div>
        </div>
      </div>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'studio-page-manager': PageManager;
  }
}
