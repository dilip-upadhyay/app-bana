import { LitElement, html, css } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { AiChatService, ChatMessage } from '../../services/ai-chat-service.ts';
import { AuthService } from '../../pages/auth/auth-service.ts';
import { appStore } from '../../builder/store/AppStore';
import './ai-message.ts';

@customElement('ai-chat-builder')
export class AiChatBuilder extends LitElement {
  static styles = css`
    :host {
      display: flex;
      flex-direction: column;
      height: 100%;
      width: 100%;
      border: none;
      border-radius: 0;
      overflow: hidden;
      background: white;
      box-shadow: none;
    }

    .header {
      padding: 16px 20px;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      font-weight: 600;
      font-size: 16px;
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .messages {
      flex: 1;
      overflow-y: auto;
      padding: 20px;
      background: #fafafa;
    }

    .messages::-webkit-scrollbar {
      width: 6px;
    }

    .messages::-webkit-scrollbar-thumb {
      background: #ccc;
      border-radius: 3px;
    }

    .input-area {
      padding: 16px 20px;
      background: white;
      border-top: 1px solid #e0e0e0;
      display: flex;
      gap: 12px;
    }

    input {
      flex: 1;
      padding: 12px 16px;
      border: 1px solid #e0e0e0;
      border-radius: 24px;
      font-size: 14px;
      outline: none;
      transition: border-color 0.2s;
    }

    input:focus {
      border-color: #667eea;
    }

    button {
      padding: 12px 24px;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      border: none;
      border-radius: 24px;
      font-weight: 600;
      cursor: pointer;
      transition: transform 0.2s, box-shadow 0.2s;
    }

    button:hover:not(:disabled) {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
    }

    button:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }

    .loading {
      text-align: center;
      padding: 20px;
      color: #999;
    }

    .spinner {
      display: inline-block;
      width: 20px;
      height: 20px;
      border: 3px solid #f3f3f3;
      border-top: 3px solid #667eea;
      border-radius: 50%;
      animation: spin 1s linear infinite;
    }

    @keyframes spin {
      0% { transform: rotate(0deg); }
      100% { transform: rotate(360deg); }
    }

    .empty-state {
      text-align: center;
      padding: 60px 20px;
      color: #999;
    }

    .empty-state h3 {
      margin: 0 0 12px 0;
      color: #666;
    }

    .empty-state p {
      margin: 0 0 8px 0;
      color: #666;
    }
  `;

  @state() private messages: ChatMessage[] = [];
  @state() private inputValue = '';
  @state() private isLoading = false;

  private chatService = new AiChatService();
  private sessionId = crypto.randomUUID();

  connectedCallback() {
    super.connectedCallback();
    // Add welcome message
    this.messages = [
      {
        role: 'assistant',
        content: "Hello! I'm here to help you build business applications - no technical skills needed! Just describe what you want to track or manage in your business, and I'll create it for you. What would you like to build today?",
        timestamp: new Date()
      }
    ];
  }

  render() {
    return html`
     <div class="header">
        🤖 AI App Builder
      </div>

      <div class="messages" @scroll=${this.handleScroll}>
        ${this.messages.length === 0 ? html`
          <div class="empty-state">
            <h3>Start a conversation</h3>
            <p>Ask me to build an application for you!</p>
          </div>
        ` : this.messages.map(msg => html`
          <ai-message
            .role=${msg.role}
            .content=${msg.content}
            .timestamp=${msg.timestamp}
            @action-click=${this.handleActionClick}>
          </ai-message>
        `)}
        
        ${this.isLoading ? html`
          <div class="loading">
            <div class="spinner"></div>
          </div>
        ` : ''}
      </div>

      <div class="input-area">
        <input
          type="text"
          placeholder="Type your message..."
          .value=${this.inputValue}
          @input=${this.handleInput}
          @keypress=${this.handleKeyPress}
          ?disabled=${this.isLoading}
        />
        <button 
          @click=${this.sendMessage}
          ?disabled=${this.isLoading || !this.inputValue.trim()}>
          Send
        </button>
      </div>
    `;
  }

  private handleInput(e: Event) {
    this.inputValue = (e.target as HTMLInputElement).value;
  }

  private handleKeyPress(e: KeyboardEvent) {
    if (e.key === 'Enter' && !this.isLoading && this.inputValue.trim()) {
      this.sendMessage();
    }
  }

  private handleActionClick(e: CustomEvent) {
    const action = e.detail.action;
    // Send the action text as a message
    this.inputValue = action;
    this.sendMessage();
  }

  private async sendMessage() {
    if (!this.inputValue.trim() || this.isLoading) return;

    const userMessage = this.inputValue.trim();
    this.inputValue = '';

    // Add user message
    this.messages = [...this.messages, {
      role: 'user',
      content: userMessage,
      timestamp: new Date()
    }];

    this.isLoading = true;
    this.scrollToBottom();

    try {
      const authToken = AuthService.getToken() || '';
      const user = AuthService.getUser();

      if (!user) {
        console.error('User not authenticated');
        this.messages = [...this.messages, {
          role: 'assistant',
          content: 'Please log in to use the AI Builder.',
          timestamp: new Date()
        }];
        return;
      }

      const tenantId = user.tenantId;
      const userId = user.email || user.id.toString();

      // Get current app ID from store
      const currentApp = appStore.getCurrentApp();
      const appId = currentApp ? currentApp.id : undefined;

      console.log('[AiChatBuilder] Sending message with Context:', {
        appId: appId,
        appName: currentApp?.name,
        tenantId: tenantId
      });

      const response = await this.chatService.sendMessage({
        message: userMessage,
        userId: userId,
        sessionId: this.sessionId,
        token: authToken,
        tenantId: tenantId,
        appId: appId
      });

      // Add assistant message
      this.messages = [...this.messages, {
        role: 'assistant',
        content: response.response,
        timestamp: new Date()
      }];

      this.scrollToBottom();
    } catch (error) {
      console.error('Chat error:', error);
      this.messages = [...this.messages, {
        role: 'assistant',
        content: 'Sorry, I encountered an error. Please try again.',
        timestamp: new Date()
      }];
    } finally {
      this.isLoading = false;
      // Auto-focus the input field after response
      this.focusInput();
    }
  }

  private handleScroll() {
    // Auto-scroll logic if needed
  }

  private scrollToBottom() {
    setTimeout(() => {
      const messagesEl = this.shadowRoot?.querySelector('.messages');
      if (messagesEl) {
        messagesEl.scrollTop = messagesEl.scrollHeight;
      }
    }, 100);
  }

  private focusInput() {
    setTimeout(() => {
      const inputEl = this.shadowRoot?.querySelector('input');
      if (inputEl) {
        inputEl.focus();
      }
    }, 100);
  }
}
