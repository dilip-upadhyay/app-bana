import { LitElement, html, css } from 'lit';
import { customElement } from 'lit/decorators.js';
import '../components/ai-builder/index';
import { AuthService } from './auth/auth-service';

/**
 * AI Chat Page - Dedicated page for AI-powered app building chat
 * Requires authentication
 */
@customElement('ai-chat-page')
export class AiChatPage extends LitElement {

  connectedCallback() {
    super.connectedCallback();

    // Check authentication
    if (!AuthService.isAuthenticated()) {
      console.log('User not authenticated, redirecting to login');
      window.location.href = '/login';
      return;
    }
  }
  static styles = css`
    :host {
      display: flex;
      flex-direction: column;
      height: 100vh;
      width: 100%;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      padding: 2rem;
      box-sizing: border-box;
    }

    .page-header {
      text-align: center;
      color: white;
      margin-bottom: 2rem;
    }

    .page-header h1 {
      margin: 0 0 0.5rem 0;
      font-size: 2.5rem;
      font-weight: 700;
    }

    .page-header p {
      margin: 0;
      font-size: 1.125rem;
      opacity: 0.95;
    }

    .chat-container {
      flex: 1;
      display: flex;
      justify-content: center;
      align-items: flex-start;
      max-width: 1200px;
      width: 100%;
      margin: 0 auto;
    }

    ai-chat-builder {
      flex: 1;
      max-width: 900px;
    }

    .back-link {
      position: fixed;
      top: 1rem;
      left: 1rem;
      padding: 0.75rem 1.5rem;
      background: rgba(255, 255, 255, 0.2);
      color: white;
      text-decoration: none;
      border-radius: 8px;
      font-weight: 600;
      transition: background 0.2s;
      backdrop-filter: blur(10px);
    }

    .back-link:hover {
      background: rgba(255, 255, 255, 0.3);
    }
  `;

  render() {
    const user = AuthService.getUser();

    return html`
      <a href="/studio.html" class="back-link">← Back to Studio</a>
      
      <div class="page-header">
        <h1>🤖 AI App Builder</h1>
        <p>Tell me what you want to build, and I'll help you create it!</p>
        ${user ? html`
          <div style="margin-top: 1rem; font-size: 0.9rem;">
            Logged in as: <strong>${user.email}</strong>
            <button @click="${() => AuthService.logout()}" 
                    style="margin-left: 1rem; padding: 0.5rem 1rem; background: rgba(255,255,255,0.2); border: none; color: white; border-radius: 4px; cursor: pointer;">
              Logout
            </button>
          </div>
        ` : ''}
      </div>

      <div class="chat-container">
        <ai-chat-builder></ai-chat-builder>
      </div>
    `;
  }
}
