import { LitElement, html, css, unsafeCSS } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { currentStore } from '../store/TreeStore';
import type { PageMeta, ComponentNode } from '../../models/metadata';
import styles from './PageManager.css?inline';

interface PageTemplate {
  id: string;
  name: string;
  description: string;
  icon: string;
  category: string;
  template: PageMeta;
}

const PAGE_TEMPLATES: PageTemplate[] = [
  {
    id: 'login-page',
    name: 'Login Page',
    description: 'Simple login form with email and password',
    icon: '🔐',
    category: 'Auth',
    template: {
      id: 'login-page',
      title: 'Login Page',
      rootId: 'root',
      nodes: [
        {
          id: 'root',
          type: 'container',
          props: {
            className: 'login-container',
            style: 'display: flex; align-items: center; justify-content: center; min-height: 100vh; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);'
          },
          children: ['login-card']
        },
        {
          id: 'login-card',
          type: 'container',
          props: {
            className: 'card',
            style: 'max-width: 400px; width: 100%; padding: 2rem; background: white; border-radius: 12px; box-shadow: 0 20px 60px rgba(0,0,0,0.3);'
          },
          children: ['login-title', 'login-form']
        },
        {
          id: 'login-title',
          type: 'text',
          props: { text: 'Welcome Back', tag: 'h1', style: 'text-align: center; margin: 0 0 1.5rem 0; color: #111827;' }
        },
        {
          id: 'login-form',
          type: 'container',
          props: { style: 'display: flex; flex-direction: column; gap: 1rem;' },
          children: ['email-input', 'password-input', 'login-button', 'signup-link']
        },
        {
          id: 'email-input',
          type: 'input',
          props: { type: 'email', placeholder: 'Email address', className: 'input' }
        },
        {
          id: 'password-input',
          type: 'input',
          props: { type: 'password', placeholder: 'Password', className: 'input' }
        },
        {
          id: 'login-button',
          type: 'button',
          props: { text: 'Sign In', className: 'btn btn-primary', style: 'width: 100%; padding: 12px;' }
        },
        {
          id: 'signup-link',
          type: 'text',
          props: { text: "Don't have an account? Sign up", tag: 'p', style: 'text-align: center; margin-top: 1rem; color: #6b7280; font-size: 14px;' }
        }
      ]
    }
  },
  {
    id: 'registration-page',
    name: 'Registration Form',
    description: 'User registration form with validation',
    icon: '📝',
    category: 'Auth',
    template: {
      id: 'registration-page',
      title: 'Registration Form',
      rootId: 'root',
      nodes: [
        {
          id: 'root',
          type: 'container',
          props: {
            style: 'display: flex; align-items: center; justify-content: center; min-height: 100vh; background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); padding: 2rem;'
          },
          children: ['reg-card']
        },
        {
          id: 'reg-card',
          type: 'container',
          props: {
            style: 'max-width: 500px; width: 100%; padding: 2rem; background: white; border-radius: 12px; box-shadow: 0 20px 60px rgba(0,0,0,0.3);'
          },
          children: ['reg-title', 'reg-form']
        },
        {
          id: 'reg-title',
          type: 'text',
          props: { text: 'Create Account', tag: 'h1', style: 'text-align: center; margin: 0 0 1.5rem 0; color: #111827;' }
        },
        {
          id: 'reg-form',
          type: 'container',
          props: { style: 'display: flex; flex-direction: column; gap: 1rem;' },
          children: ['name-row', 'reg-email', 'reg-password', 'reg-confirm', 'reg-button']
        },
        {
          id: 'name-row',
          type: 'container',
          props: { style: 'display: grid; grid-template-columns: 1fr 1fr; gap: 1rem;' },
          children: ['first-name', 'last-name']
        },
        {
          id: 'first-name',
          type: 'input',
          props: { type: 'text', placeholder: 'First Name' }
        },
        {
          id: 'last-name',
          type: 'input',
          props: { type: 'text', placeholder: 'Last Name' }
        },
        {
          id: 'reg-email',
          type: 'input',
          props: { type: 'email', placeholder: 'Email address' }
        },
        {
          id: 'reg-password',
          type: 'input',
          props: { type: 'password', placeholder: 'Password' }
        },
        {
          id: 'reg-confirm',
          type: 'input',
          props: { type: 'password', placeholder: 'Confirm Password' }
        },
        {
          id: 'reg-button',
          type: 'button',
          props: { text: 'Sign Up', style: 'width: 100%; padding: 12px;' }
        }
      ]
    }
  },
  {
    id: 'dashboard',
    name: 'Dashboard',
    description: 'Admin dashboard with stats and charts',
    icon: '📊',
    category: 'App',
    template: {
      id: 'dashboard',
      title: 'Dashboard',
      rootId: 'root',
      nodes: [
        {
          id: 'root',
          type: 'container',
          props: { style: 'min-height: 100vh; background: #f3f4f6;' },
          children: ['header', 'main-content']
        },
        {
          id: 'header',
          type: 'container',
          props: { style: 'background: white; padding: 1rem 2rem; border-bottom: 1px solid #e5e7eb; box-shadow: 0 1px 3px rgba(0,0,0,0.1);' },
          children: ['header-title']
        },
        {
          id: 'header-title',
          type: 'text',
          props: { text: 'Dashboard', tag: 'h1', style: 'margin: 0; color: #111827; font-size: 24px;' }
        },
        {
          id: 'main-content',
          type: 'container',
          props: { style: 'padding: 2rem;' },
          children: ['stats-grid', 'charts-section']
        },
        {
          id: 'stats-grid',
          type: 'container',
          props: { style: 'display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 1.5rem; margin-bottom: 2rem;' },
          children: ['stat-1', 'stat-2', 'stat-3', 'stat-4']
        },
        {
          id: 'stat-1',
          type: 'container',
          props: { style: 'background: white; padding: 1.5rem; border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.1);' },
          children: ['stat-1-label', 'stat-1-value']
        },
        {
          id: 'stat-1-label',
          type: 'text',
          props: { text: 'Total Users', tag: 'p', style: 'margin: 0 0 0.5rem 0; color: #6b7280; font-size: 14px;' }
        },
        {
          id: 'stat-1-value',
          type: 'text',
          props: { text: '12,345', tag: 'h2', style: 'margin: 0; color: #111827; font-size: 32px;' }
        },
        {
          id: 'stat-2',
          type: 'container',
          props: { style: 'background: white; padding: 1.5rem; border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.1);' },
          children: ['stat-2-label', 'stat-2-value']
        },
        {
          id: 'stat-2-label',
          type: 'text',
          props: { text: 'Revenue', tag: 'p', style: 'margin: 0 0 0.5rem 0; color: #6b7280; font-size: 14px;' }
        },
        {
          id: 'stat-2-value',
          type: 'text',
          props: { text: '$45,678', tag: 'h2', style: 'margin: 0; color: #111827; font-size: 32px;' }
        },
        {
          id: 'stat-3',
          type: 'container',
          props: { style: 'background: white; padding: 1.5rem; border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.1);' },
          children: ['stat-3-label', 'stat-3-value']
        },
        {
          id: 'stat-3-label',
          type: 'text',
          props: { text: 'Active Sessions', tag: 'p', style: 'margin: 0 0 0.5rem 0; color: #6b7280; font-size: 14px;' }
        },
        {
          id: 'stat-3-value',
          type: 'text',
          props: { text: '892', tag: 'h2', style: 'margin: 0; color: #111827; font-size: 32px;' }
        },
        {
          id: 'stat-4',
          type: 'container',
          props: { style: 'background: white; padding: 1.5rem; border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.1);' },
          children: ['stat-4-label', 'stat-4-value']
        },
        {
          id: 'stat-4-label',
          type: 'text',
          props: { text: 'Conversion Rate', tag: 'p', style: 'margin: 0 0 0.5rem 0; color: #6b7280; font-size: 14px;' }
        },
        {
          id: 'stat-4-value',
          type: 'text',
          props: { text: '23.5%', tag: 'h2', style: 'margin: 0; color: #111827; font-size: 32px;' }
        },
        {
          id: 'charts-section',
          type: 'container',
          props: { style: 'background: white; padding: 2rem; border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.1);' },
          children: ['chart-title', 'chart-placeholder']
        },
        {
          id: 'chart-title',
          type: 'text',
          props: { text: 'Analytics Overview', tag: 'h2', style: 'margin: 0 0 1rem 0; color: #111827;' }
        },
        {
          id: 'chart-placeholder',
          type: 'container',
          props: { style: 'height: 300px; background: #f9fafb; border-radius: 8px; display: flex; align-items: center; justify-content: center;' },
          children: ['chart-text']
        },
        {
          id: 'chart-text',
          type: 'text',
          props: { text: 'Chart Area', tag: 'p', style: 'color: #9ca3af;' }
        }
      ]
    }
  },
  {
    id: 'landing-page',
    name: 'Landing Page',
    description: 'Marketing landing page with hero section',
    icon: '🚀',
    category: 'Marketing',
    template: {
      id: 'landing-page',
      title: 'Landing Page',
      rootId: 'root',
      nodes: [
        {
          id: 'root',
          type: 'container',
          props: { style: 'min-height: 100vh;' },
          children: ['hero-section', 'features-section']
        },
        {
          id: 'hero-section',
          type: 'container',
          props: { style: 'background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 4rem 2rem; text-align: center;' },
          children: ['hero-title', 'hero-subtitle', 'hero-cta']
        },
        {
          id: 'hero-title',
          type: 'text',
          props: { text: 'Build Amazing Apps', tag: 'h1', style: 'font-size: 48px; margin: 0 0 1rem 0; font-weight: bold;' }
        },
        {
          id: 'hero-subtitle',
          type: 'text',
          props: { text: 'Create beautiful, responsive applications with our drag-and-drop builder', tag: 'p', style: 'font-size: 20px; margin: 0 0 2rem 0; opacity: 0.9;' }
        },
        {
          id: 'hero-cta',
          type: 'button',
          props: { text: 'Get Started Free', style: 'padding: 16px 32px; font-size: 18px; background: white; color: #667eea; border: none; border-radius: 8px; cursor: pointer; font-weight: 600;' }
        },
        {
          id: 'features-section',
          type: 'container',
          props: { style: 'padding: 4rem 2rem; background: white;' },
          children: ['features-title', 'features-grid']
        },
        {
          id: 'features-title',
          type: 'text',
          props: { text: 'Features', tag: 'h2', style: 'text-align: center; font-size: 36px; margin: 0 0 3rem 0; color: #111827;' }
        },
        {
          id: 'features-grid',
          type: 'container',
          props: { style: 'display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 2rem; max-width: 1200px; margin: 0 auto;' },
          children: ['feature-1', 'feature-2', 'feature-3']
        },
        {
          id: 'feature-1',
          type: 'container',
          props: { style: 'text-align: center; padding: 2rem;' },
          children: ['feature-1-icon', 'feature-1-title', 'feature-1-desc']
        },
        {
          id: 'feature-1-icon',
          type: 'text',
          props: { text: '⚡', tag: 'div', style: 'font-size: 48px; margin-bottom: 1rem;' }
        },
        {
          id: 'feature-1-title',
          type: 'text',
          props: { text: 'Fast & Efficient', tag: 'h3', style: 'margin: 0 0 0.5rem 0; color: #111827;' }
        },
        {
          id: 'feature-1-desc',
          type: 'text',
          props: { text: 'Build applications faster than ever before', tag: 'p', style: 'color: #6b7280; margin: 0;' }
        },
        {
          id: 'feature-2',
          type: 'container',
          props: { style: 'text-align: center; padding: 2rem;' },
          children: ['feature-2-icon', 'feature-2-title', 'feature-2-desc']
        },
        {
          id: 'feature-2-icon',
          type: 'text',
          props: { text: '🎨', tag: 'div', style: 'font-size: 48px; margin-bottom: 1rem;' }
        },
        {
          id: 'feature-2-title',
          type: 'text',
          props: { text: 'Beautiful Design', tag: 'h3', style: 'margin: 0 0 0.5rem 0; color: #111827;' }
        },
        {
          id: 'feature-2-desc',
          type: 'text',
          props: { text: 'Pre-built components with modern styling', tag: 'p', style: 'color: #6b7280; margin: 0;' }
        },
        {
          id: 'feature-3',
          type: 'container',
          props: { style: 'text-align: center; padding: 2rem;' },
          children: ['feature-3-icon', 'feature-3-title', 'feature-3-desc']
        },
        {
          id: 'feature-3-icon',
          type: 'text',
          props: { text: '📱', tag: 'div', style: 'font-size: 48px; margin-bottom: 1rem;' }
        },
        {
          id: 'feature-3-title',
          type: 'text',
          props: { text: 'Responsive', tag: 'h3', style: 'margin: 0 0 0.5rem 0; color: #111827;' }
        },
        {
          id: 'feature-3-desc',
          type: 'text',
          props: { text: 'Works perfectly on all devices', tag: 'p', style: 'color: #6b7280; margin: 0;' }
        }
      ]
    }
  }
];

@customElement('studio-page-manager')
export class PageManager extends LitElement {
  static styles = css`${unsafeCSS(styles)}`;

  @state() private showTemplates = false;
  @state() private currentPage: string = 'Current Page';

  private loadTemplate(template: PageTemplate) {
    if (currentStore) {
      const confirmed = confirm(`Load "${template.name}" template? This will replace the current page.`);
      if (confirmed) {
        // Reinitialize store with new template
        const { initStore } = require('../store/TreeStore');
        initStore(template.template);
        this.currentPage = template.name;
        this.showTemplates = false;
        this.dispatchEvent(new CustomEvent('template-loaded', { detail: template }));
      }
    }
  }

  render() {
    return html`
      <div class="page-manager">
        <div class="page-info">
          <span class="page-name">${this.currentPage}</span>
          <button
            class="templates-btn"
            @click=${() => this.showTemplates = !this.showTemplates}
          >
            📋 Templates
          </button>
        </div>

        ${this.showTemplates ? html`
          <div class="templates-modal" @click=${() => this.showTemplates = false}>
            <div class="templates-content" @click=${(e: Event) => e.stopPropagation()}>
              <div class="templates-header">
                <h2>Page Templates</h2>
                <button class="close-btn" @click=${() => this.showTemplates = false}>✕</button>
              </div>

              <div class="templates-grid">
                ${PAGE_TEMPLATES.map(template => html`
                  <div class="template-card" @click=${() => this.loadTemplate(template)}>
                    <div class="template-icon">${template.icon}</div>
                    <h3>${template.name}</h3>
                    <p>${template.description}</p>
                    <span class="template-category">${template.category}</span>
                  </div>
                `)}
              </div>
            </div>
          </div>
        ` : ''}
      </div>
    `;
  }
}

