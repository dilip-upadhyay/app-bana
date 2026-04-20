import { LitElement, html, css } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { AiChatService, ChatMessage, ChatSession } from '../../services/ai-chat-service.ts';
import { AuthService } from '../../pages/auth/auth-service.ts';
import { appStore } from '../../builder/store/AppStore';
import './ai-message.ts';

// localStorage key pattern: ai_session_<userId>
const SESSION_STORAGE_KEY_PREFIX = 'ai_session_';

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
      position: relative;
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
      justify-content: space-between;
      gap: 12px;
    }

    .history-btn {
      background: rgba(255, 255, 255, 0.2);
      border: 1px solid rgba(255, 255, 255, 0.4);
      color: white;
      padding: 6px 12px;
      border-radius: 4px;
      font-size: 12px;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 6px;
      transition: background 0.2s;
    }
    .history-btn:hover {
      background: rgba(255, 255, 255, 0.3);
      transform: none;
      box-shadow: none;
    }

    .sessions-drawer {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: white;
      z-index: 10;
      display: flex;
      flex-direction: column;
      transform: translateX(100%);
      transition: transform 0.3s ease;
    }
    .sessions-drawer.open {
      transform: translateX(0);
    }

    .drawer-header {
      padding: 16px 20px;
      background: #f8f9fb;
      border-bottom: 1px solid #e0e0e0;
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-weight: 600;
      color: #333;
    }
    
    .close-btn {
      background: transparent;
      border: none;
      color: #666;
      font-size: 20px;
      padding: 4px;
      cursor: pointer;
      box-shadow: none;
    }
    .close-btn:hover {
      color: #333;
      transform: none;
      background: #eee;
      border-radius: 4px;
    }

    .drawer-actions {
      padding: 16px 20px;
      border-bottom: 1px solid #e0e0e0;
    }

    .new-chat-btn {
      width: 100%;
      justify-content: center;
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .sessions-list {
      flex: 1;
      overflow-y: auto;
      padding: 12px 0;
    }

    .session-item {
      padding: 12px 20px;
      border-bottom: 1px solid #f0f0f0;
      cursor: pointer;
      transition: background 0.2s;
    }

    .session-item:hover {
      background: #f8f9fb;
    }
    
    .session-item.active {
      background: #eef2ff;
      border-left: 3px solid #667eea;
    }

    .session-date {
      font-size: 12px;
      color: #666;
      margin-bottom: 4px;
    }

    .session-id {
      font-size: 13px;
      color: #333;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
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

    /* ── History divider ── */
    .history-divider {
      display: flex;
      align-items: center;
      gap: 10px;
      margin: 8px 0 16px 0;
      color: #aaa;
      font-size: 11px;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.8px;
    }

    .history-divider::before,
    .history-divider::after {
      content: '';
      flex: 1;
      height: 1px;
      background: #e0e0e0;
    }

    /* ── Loading states ── */
    .loading {
      text-align: center;
      padding: 20px;
      color: #999;
    }

    .history-loading {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      padding: 12px;
      color: #aaa;
      font-size: 12px;
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

    .spinner-sm {
      width: 14px;
      height: 14px;
      border-width: 2px;
    }

    @keyframes spin {
      0% { transform: rotate(0deg); }
      100% { transform: rotate(360deg); }
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

    /* ── Provider Toggle ── */
    .provider-toggle {
      display: flex;
      gap: 4px;
      padding: 0 20px 8px 20px;
      background: white;
    }

    .provider-btn {
      padding: 6px 12px;
      background: #f0f0f0;
      color: #666;
      border: 1px solid #e0e0e0;
      border-radius: 16px;
      font-size: 11px;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s;
      box-shadow: none;
    }

    .provider-btn:hover {
      background: #e0e0e0;
      transform: none;
    }

    .provider-btn.active {
      background: #667eea;
      color: white;
      border-color: #667eea;
      box-shadow: 0 2px 4px rgba(102, 126, 234, 0.3);
    }

    /* ── Image Previews ── */
    .image-previews {
      display: flex;
      gap: 10px;
      padding: 10px 20px;
      background: #fdfdfd;
      border-top: 1px solid #f0f0f0;
      overflow-x: auto;
    }

    .preview-container {
      position: relative;
      width: 60px;
      height: 60px;
      border-radius: 8px;
      border: 1px solid #e0e0e0;
      overflow: hidden;
      flex-shrink: 0;
    }

    .preview-container img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    .remove-img {
      position: absolute;
      top: 2px;
      right: 2px;
      background: rgba(0,0,0,0.6);
      color: white;
      border: none;
      border-radius: 50%;
      width: 16px;
      height: 16px;
      font-size: 10px;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      padding: 0;
      box-shadow: none;
    }

    .remove-img:hover {
      background: rgba(255,0,0,0.8);
      transform: scale(1.1);
    }
  `;

  @state() private messages: ChatMessage[] = [];
  @state() private inputValue = '';
  @state() private isLoading = false;
  @state() private isLoadingHistory = false;
  @state() private hasHistory = false;

  @state() private showSessionsPanel = false;
  @state() private pastSessions: ChatSession[] = [];
  @state() private isLoadingSessionsPanel = false;
  @state() private selectedProvider = localStorage.getItem('ai_preferred_provider') || 'openai';
  @state() private attachedImages: string[] = [];

  private chatService = new AiChatService();
  private sessionId = '';

  /**
   * Returns the localStorage key for storing this user's sessionId.
   */
  private getSessionStorageKey(userId: string): string {
    return `${SESSION_STORAGE_KEY_PREFIX}${userId}`;
  }

  /**
   * Loads or creates a persistent sessionId for the current user.
   * Stored in localStorage so it survives page reloads.
   */
  private getOrCreateSessionId(userId: string): string {
    const key = this.getSessionStorageKey(userId);
    let existingSessionId = localStorage.getItem(key);
    if (!existingSessionId) {
      existingSessionId = crypto.randomUUID();
      localStorage.setItem(key, existingSessionId);
      console.log('[AiChatBuilder] Created new session:', existingSessionId);
    } else {
      console.log('[AiChatBuilder] Resumed session:', existingSessionId);
    }
    return existingSessionId;
  }

  async connectedCallback() {
    super.connectedCallback();

    const user = AuthService.getUser();
    if (!user) {
      // Not authenticated yet — show welcome
      this.messages = [this.welcomeMessage()];
      return;
    }

    const userId = user.email || user.id.toString();
    this.sessionId = this.getOrCreateSessionId(userId);

    // Load persisted history
    this.isLoadingHistory = true;
    try {
      const history = await this.chatService.getHistory(userId, this.sessionId);
      if (history.length > 0) {
        this.hasHistory = true;
        this.messages = history;
        // Scroll after render
        setTimeout(() => this.scrollToBottom(), 150);
      } else {
        // Fresh session — show welcome
        this.messages = [this.welcomeMessage()];
      }
    } catch (err) {
      console.warn('[AiChatBuilder] History load failed, starting fresh:', err);
      this.messages = [this.welcomeMessage()];
    } finally {
      this.isLoadingHistory = false;
    }
  }

  private welcomeMessage(): ChatMessage {
    return {
      role: 'assistant',
      content: "Hello! I'm here to help you build business applications - no technical skills needed! Just describe what you want to track or manage in your business, and I'll create it for you. What would you like to build today?",
      timestamp: new Date()
    };
  }

  render() {
    return html`
     <div class="header">
        <div>🤖 AI App Builder</div>
        <button class="history-btn" @click=${this.toggleSessionsPanel}>
           🕘 History
        </button>
      </div>

      <!-- Sessions Drawer -->
      <div class="sessions-drawer ${this.showSessionsPanel ? 'open' : ''}">
        <div class="drawer-header">
           Past Sessions
           <button class="close-btn" @click=${this.toggleSessionsPanel}>&times;</button>
        </div>
        <div class="drawer-actions">
           <button class="new-chat-btn" @click=${this.startNewSession}>
             ➕ New Chat
           </button>
        </div>
        <div class="sessions-list">
          ${this.isLoadingSessionsPanel ? html`
            <div class="loading"><div class="spinner spinner-sm"></div></div>
          ` : this.pastSessions.length === 0 ? html`
            <div class="empty-state">
              <p>No past sessions found.</p>
            </div>
          ` : this.pastSessions.map(session => html`
            <div class="session-item ${session.sessionId === this.sessionId ? 'active' : ''}" 
                 @click=${() => this.switchSession(session.sessionId)}>
              <div class="session-date">
                ${new Date(session.lastActivity).toLocaleString()}
              </div>
              <div class="session-id">
                Session: ${session.sessionId.substring(0, 8)}...
              </div>
            </div>
          `)}
        </div>
      </div>

      <div class="messages" @scroll=${this.handleScroll}>

        ${this.isLoadingHistory ? html`
          <div class="history-loading">
            <div class="spinner spinner-sm"></div>
            Loading previous conversation…
          </div>
        ` : ''}

        ${this.hasHistory && !this.isLoadingHistory ? html`
          <div class="history-divider">📜 Previous conversation</div>
        ` : ''}

        ${this.messages.map(msg => html`
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

      ${this.attachedImages.length > 0 ? html`
        <div class="image-previews">
          ${this.attachedImages.map((img, index) => html`
            <div class="preview-container">
              <img src="${img}" />
              <button class="remove-img" @click=${() => this.removeImage(index)}>&times;</button>
            </div>
          `)}
        </div>
      ` : ''}

      <div class="provider-toggle">
        <button class="provider-btn ${this.selectedProvider === 'openai' ? 'active' : ''}" 
                @click=${() => this.setProvider('openai')}>
          OpenAI (GPT-4o)
        </button>
        <button class="provider-btn ${this.selectedProvider === 'gemini' ? 'active' : ''}" 
                @click=${() => this.setProvider('gemini')}>
          Google Gemini 1.5
        </button>
      </div>

      <div class="input-area">
        <input
          type="text"
          placeholder="Type or paste images to analyze..."
          .value=${this.inputValue}
          @input=${this.handleInput}
          @keypress=${this.handleKeyPress}
          @paste=${this.handlePaste}
          ?disabled=${this.isLoading || this.isLoadingHistory}
        />
        <button
          @click=${this.sendMessage}
          ?disabled=${this.isLoading || this.isLoadingHistory || !this.inputValue.trim()}>
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
    this.inputValue = action;
    this.sendMessage();
  }

  private async sendMessage() {
    if (!this.inputValue.trim() && this.attachedImages.length === 0) return;
    if (this.isLoading) return;

    const userMessage = this.inputValue.trim();
    const imagesToSend = [...this.attachedImages];
    
    this.inputValue = '';
    this.attachedImages = [];

    // Add user message to UI
    this.messages = [...this.messages, {
      role: 'user',
      content: userMessage || (imagesToSend.length > 0 ? "[Images attached]" : ""),
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
        tenantId: tenantId,
        sessionId: this.sessionId
      });

      const response = await this.chatService.sendMessage({
        message: userMessage,
        userId: userId,
        sessionId: this.sessionId,
        token: authToken,
        tenantId: tenantId,
        appId: appId,
        provider: this.selectedProvider,
        images: imagesToSend
      });

      // Add assistant message
      this.messages = [...this.messages, {
        role: 'assistant',
        content: (response as any).response ?? response.message,
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
      this.focusInput();
    }
  }

  private setProvider(provider: string) {
    this.selectedProvider = provider;
    localStorage.setItem('ai_preferred_provider', provider);
    console.log('[AiChatBuilder] Provider switched to:', provider);
  }

  private handlePaste(e: ClipboardEvent) {
    const items = e.clipboardData?.items;
    if (!items) return;

    for (let i = 0; i < items.length; i++) {
      if (items[i].type.indexOf('image') !== -1) {
        const file = items[i].getAsFile();
        if (file) {
          const reader = new FileReader();
          reader.onload = (event) => {
            const base64 = event.target?.result as string;
            this.attachedImages = [...this.attachedImages, base64];
          };
          reader.readAsDataURL(file);
        }
      }
    }
  }

  private removeImage(index: number) {
    this.attachedImages = this.attachedImages.filter((_, i) => i !== index);
  }

  private handleScroll() {
    // Auto-scroll logic if needed
  }

  private async toggleSessionsPanel() {
    this.showSessionsPanel = !this.showSessionsPanel;
    if (this.showSessionsPanel && this.pastSessions.length === 0) {
      await this.loadSessions();
    }
  }

  private async loadSessions() {
    const user = AuthService.getUser();
    if (!user) return;
    const userId = user.email || user.id.toString();

    this.isLoadingSessionsPanel = true;
    try {
      this.pastSessions = await this.chatService.getSessions(userId, 20);
    } catch (err) {
      console.error('[AiChatBuilder] Failed to load sessions:', err);
    } finally {
      this.isLoadingSessionsPanel = false;
    }
  }

  private async startNewSession() {
    const user = AuthService.getUser();
    if (!user) return;
    const userId = user.email || user.id.toString();

    const newSessionId = crypto.randomUUID();
    const key = this.getSessionStorageKey(userId);
    localStorage.setItem(key, newSessionId);
    
    this.sessionId = newSessionId;
    this.messages = [this.welcomeMessage()];
    this.hasHistory = false;
    this.showSessionsPanel = false;
    
    // Refresh sessions list in background
    this.loadSessions();
  }

  private async switchSession(targetSessionId: string) {
    if (this.sessionId === targetSessionId) {
      this.showSessionsPanel = false;
      return;
    }

    const user = AuthService.getUser();
    if (!user) return;
    const userId = user.email || user.id.toString();

    const key = this.getSessionStorageKey(userId);
    localStorage.setItem(key, targetSessionId);
    
    this.sessionId = targetSessionId;
    this.showSessionsPanel = false;
    this.isLoadingHistory = true;
    this.hasHistory = false;
    this.messages = [];

    try {
      const history = await this.chatService.getHistory(userId, this.sessionId);
      if (history.length > 0) {
        this.hasHistory = true;
        this.messages = history;
        setTimeout(() => this.scrollToBottom(), 150);
      } else {
        this.messages = [this.welcomeMessage()];
      }
    } catch (err) {
      console.warn('[AiChatBuilder] History load failed for switched session:', err);
      this.messages = [this.welcomeMessage()];
    } finally {
      this.isLoadingHistory = false;
    }
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
        (inputEl as HTMLInputElement).focus();
      }
    }, 100);
  }
}
