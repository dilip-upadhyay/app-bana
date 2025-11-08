import { LitElement, html, css } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { appStore } from '../store/AppStore';
import type { EntityMeta } from '../../models/entity-metadata';
import type { PageMeta } from '../../models/metadata';

interface ChatMessage {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  timestamp: number;
  metadata?: {
    generatedApp?: any;
    generatedEntities?: EntityMeta[];
    generatedPages?: PageMeta[];
    action?: 'preview' | 'create' | 'confirm';
  };
}

/**
 * AI Chat Builder - Chat-based interface for building apps with AI
 * 
 * Features:
 * - Natural language app generation via backend API
 * - Interactive chat interface
 * - Preview generated metadata
 * - Confirm and create apps
 */
@customElement('ai-chat-builder')
export class AiChatBuilder extends LitElement {
  static styles = css`
    :host {
      display: flex;
      flex-direction: column;
      height: 100%;
      background: var(--color-surface, #fff);
      font-family: var(--font-sans, system-ui, sans-serif);
    }

    .header {
      padding: 1rem 1.5rem;
      border-bottom: 1px solid var(--color-border, #e5e7eb);
      background: var(--color-surface-alt, #f9fafb);
    }

    .header h2 {
      margin: 0;
      font-size: var(--text-lg, 1.125rem);
      color: var(--color-text, #111827);
    }

    .header p {
      margin: 0.25rem 0 0;
      font-size: var(--text-sm, 0.875rem);
      color: var(--color-text-muted, #6b7280);
    }

    .chat-container {
      flex: 1;
      overflow-y: auto;
      padding: 1.5rem;
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }

    .message {
      display: flex;
      gap: 0.75rem;
      animation: slideIn 0.3s ease-out;
    }

    @keyframes slideIn {
      from {
        opacity: 0;
        transform: translateY(10px);
      }
      to {
        opacity: 1;
        transform: translateY(0);
      }
    }

    .message.user {
      flex-direction: row-reverse;
    }

    .message-avatar {
      width: 32px;
      height: 32px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 1.25rem;
      flex-shrink: 0;
    }

    .message.user .message-avatar {
      background: var(--color-brand, #3b82f6);
      color: white;
    }

    .message.assistant .message-avatar {
      background: var(--color-success, #10b981);
      color: white;
    }

    .message.system .message-avatar {
      background: var(--color-text-muted, #6b7280);
      color: white;
    }

    .message-content {
      flex: 1;
      max-width: 70%;
    }

    .message.user .message-content {
      background: var(--color-brand, #3b82f6);
      color: white;
      border-radius: 1rem 1rem 0 1rem;
    }

    .message.assistant .message-content {
      background: var(--color-surface-alt, #f9fafb);
      border: 1px solid var(--color-border, #e5e7eb);
      color: var(--color-text, #111827);
      border-radius: 1rem 1rem 1rem 0;
    }

    .message-text {
      padding: 0.75rem 1rem;
      line-height: 1.5;
      font-size: var(--text-sm, 0.875rem);
    }

    .message-metadata {
      margin-top: 0.75rem;
      padding: 0 1rem 0.75rem;
    }

    .preview-card {
      background: white;
      border: 1px solid var(--color-border, #e5e7eb);
      border-radius: 0.5rem;
      padding: 1rem;
      margin-top: 0.5rem;
    }

    .preview-card h4 {
      margin: 0 0 0.5rem;
      font-size: var(--text-sm, 0.875rem);
      font-weight: 600;
      color: var(--color-text, #111827);
    }

    .preview-list {
      list-style: none;
      padding: 0;
      margin: 0.5rem 0 0;
      font-size: var(--text-xs, 0.75rem);
      color: var(--color-text-muted, #6b7280);
    }

    .preview-list li {
      padding: 0.25rem 0;
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    .preview-list li::before {
      content: '✓';
      color: var(--color-success, #10b981);
      font-weight: bold;
    }

    .action-buttons {
      display: flex;
      gap: 0.5rem;
      margin-top: 0.75rem;
    }

    .btn {
      padding: 0.5rem 1rem;
      border-radius: 0.375rem;
      border: 1px solid var(--color-border, #e5e7eb);
      background: white;
      color: var(--color-text, #111827);
      font-size: var(--text-xs, 0.75rem);
      font-weight: 500;
      cursor: pointer;
      transition: all 150ms;
    }

    .btn:hover {
      background: var(--color-surface-alt, #f9fafb);
    }

    .btn.primary {
      background: var(--color-brand, #3b82f6);
      border-color: var(--color-brand, #3b82f6);
      color: white;
    }

    .btn.primary:hover {
      filter: brightness(1.1);
    }

    .btn:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }

    .input-container {
      padding: 1rem 1.5rem;
      border-top: 1px solid var(--color-border, #e5e7eb);
      background: white;
    }

    .input-wrapper {
      display: flex;
      gap: 0.75rem;
      align-items: flex-end;
    }

    .input-field {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }

    textarea {
      padding: 0.75rem;
      border: 1px solid var(--color-border, #e5e7eb);
      border-radius: 0.5rem;
      font-family: inherit;
      font-size: var(--text-sm, 0.875rem);
      resize: none;
      min-height: 60px;
      max-height: 120px;
    }

    textarea:focus {
      outline: none;
      border-color: var(--color-brand, #3b82f6);
      box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
    }

    .send-btn {
      padding: 0.75rem 1.5rem;
      background: var(--color-brand, #3b82f6);
      color: white;
      border: none;
      border-radius: 0.5rem;
      font-weight: 500;
      cursor: pointer;
      transition: all 150ms;
    }

    .send-btn:hover:not(:disabled) {
      filter: brightness(1.1);
    }

    .send-btn:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }

    .loading {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 1rem;
      color: var(--color-text-muted, #6b7280);
      font-size: var(--text-sm, 0.875rem);
    }

    .spinner {
      width: 16px;
      height: 16px;
      border: 2px solid var(--color-border, #e5e7eb);
      border-top-color: var(--color-brand, #3b82f6);
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    .empty-state {
      flex: 1;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 3rem;
      text-align: center;
      color: var(--color-text-muted, #6b7280);
    }

    .empty-state-icon {
      font-size: 3rem;
      margin-bottom: 1rem;
    }

    .empty-state h3 {
      margin: 0 0 0.5rem;
      font-size: var(--text-lg, 1.125rem);
      color: var(--color-text, #111827);
    }

    .empty-state p {
      margin: 0 0 1.5rem;
      font-size: var(--text-sm, 0.875rem);
    }

    .example-prompts {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      align-items: stretch;
      max-width: 400px;
      width: 100%;
    }

    .example-prompt {
      padding: 0.75rem 1rem;
      background: white;
      border: 1px solid var(--color-border, #e5e7eb);
      border-radius: 0.5rem;
      text-align: left;
      cursor: pointer;
      transition: all 150ms;
      font-size: var(--text-sm, 0.875rem);
    }

    .example-prompt:hover {
      border-color: var(--color-brand, #3b82f6);
      background: var(--color-brand-muted, #eff6ff);
    }
  `;

  @state() private messages: ChatMessage[] = [];
  @state() private inputValue = '';
  @state() private isProcessing = false;

  connectedCallback() {
    super.connectedCallback();
    this.addSystemMessage('Welcome! I can help you build applications using natural language. Describe the app you want to create.');
  }

  private addSystemMessage(content: string) {
    this.messages = [...this.messages, {
      id: `msg-${Date.now()}`,
      role: 'system',
      content,
      timestamp: Date.now()
    }];
  }

  private addUserMessage(content: string) {
    this.messages = [...this.messages, {
      id: `msg-${Date.now()}`,
      role: 'user',
      content,
      timestamp: Date.now()
    }];
  }

  private addAssistantMessage(content: string, metadata?: ChatMessage['metadata']) {
    this.messages = [...this.messages, {
      id: `msg-${Date.now()}`,
      role: 'assistant',
      content,
      timestamp: Date.now(),
      metadata
    }];
  }

  private async handleSend() {
    if (!this.inputValue.trim() || this.isProcessing) return;

    const userMessage = this.inputValue.trim();
    this.addUserMessage(userMessage);
    this.inputValue = '';
    this.isProcessing = true;

    try {
      // Process user input and generate app
      await this.processUserInput(userMessage);
    } catch (error) {
      console.error('[AiChatBuilder] Error processing input:', error);
      this.addAssistantMessage('Sorry, I encountered an error processing your request. Please try again.');
    } finally {
      this.isProcessing = false;
    }
  }

  private async processUserInput(input: string) {
    try {
      // Call backend AI generation API
      const response = await fetch('/api/ai/generate', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          description: input
        })
      });

      if (!response.ok) {
        throw new Error(`API error: ${response.statusText}`);
      }

      const result = await response.json();

      if (result.success) {
        this.addAssistantMessage(
          `I've analyzed your request and prepared an app with the following structure:`,
          {
            generatedApp: {
              id: `app-${Date.now()}`,
              name: result.appName,
              description: result.appDescription
            },
            generatedEntities: result.entities || [],
            generatedPages: result.suggestedPages || [],
            action: 'preview'
          }
        );
      } else {
        this.addAssistantMessage(result.error || 'Failed to generate app structure.');
      }
    } catch (error) {
      console.error('[AiChatBuilder] Error calling AI API:', error);
      this.addAssistantMessage(
        `Sorry, I encountered an error processing your request: ${error}`
      );
    }
  }

  private async handleConfirmCreate(message: ChatMessage) {
    if (!message.metadata) return;

    this.isProcessing = true;
    try {
      const { generatedApp, generatedEntities } = message.metadata;

      // Create app via AppStore - returns the created app with real ID from backend
      const createdApp = await appStore.createApp({
        name: generatedApp.name,
        description: generatedApp.description
      });

      // Set as current app using the REAL ID from backend
      appStore.setCurrentApp(createdApp.id);

      // Add entities to app
      if (generatedEntities && generatedEntities.length > 0) {
        await appStore.updateApp(createdApp.id, {
          entities: generatedEntities
        });
      }

      this.addAssistantMessage(
        `✅ Application "${createdApp.name}" created successfully! You can now view it in the Studio Builder.`
      );

      // Dispatch event to switch to app view using REAL ID
      this.dispatchEvent(new CustomEvent('app-created', {
        detail: { appId: createdApp.id },
        bubbles: true,
        composed: true
      }));
    } catch (error) {
      console.error('[AiChatBuilder] Error creating app:', error);
      this.addAssistantMessage(`❌ Failed to create application: ${error}`);
    } finally {
      this.isProcessing = false;
    }
  }

  private handleExamplePrompt(prompt: string) {
    this.inputValue = prompt;
    this.handleSend();
  }

  private formatTimestamp(timestamp: number): string {
    return new Date(timestamp).toLocaleTimeString();
  }

  render() {
    return html`
      <div class="header">
        <h2>🤖 AI App Builder</h2>
        <p>Describe your app idea and I'll build it for you</p>
      </div>

      <div class="chat-container">
        ${this.messages.length === 0 ? this.renderEmptyState() : this.renderMessages()}
        ${this.isProcessing ? this.renderLoading() : ''}
      </div>

      <div class="input-container">
        <div class="input-wrapper">
          <div class="input-field">
            <textarea
              .value=${this.inputValue}
              @input=${(e: Event) => this.inputValue = (e.target as HTMLTextAreaElement).value}
              @keydown=${(e: KeyboardEvent) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault();
                  this.handleSend();
                }
              }}
              placeholder="Describe the app you want to build... (Press Enter to send, Shift+Enter for new line)"
              ?disabled=${this.isProcessing}
            ></textarea>
          </div>
          <button
            class="send-btn"
            @click=${this.handleSend}
            ?disabled=${!this.inputValue.trim() || this.isProcessing}
          >
            Send
          </button>
        </div>
      </div>
    `;
  }

  private renderEmptyState() {
    return html`
      <div class="empty-state">
        <div class="empty-state-icon">💬</div>
        <h3>Start Building with AI</h3>
        <p>Try one of these examples or describe your own app:</p>
        <div class="example-prompts">
          <button
            class="example-prompt"
            @click=${() => this.handleExamplePrompt('Create a blog app with posts and comments')}
          >
            📝 Create a blog app with posts and comments
          </button>
          <button
            class="example-prompt"
            @click=${() => this.handleExamplePrompt('Build a task manager with priorities and due dates')}
          >
            ✅ Build a task manager with priorities and due dates
          </button>
          <button
            class="example-prompt"
            @click=${() => this.handleExamplePrompt('Make an e-commerce store with products and categories')}
          >
            🛍️ Make an e-commerce store with products
          </button>
          <button
            class="example-prompt"
            @click=${() => this.handleExamplePrompt('Create a CRM for managing customer contacts')}
          >
            👥 Create a CRM for managing customers
          </button>
        </div>
      </div>
    `;
  }

  private renderMessages() {
    return this.messages.map(msg => this.renderMessage(msg));
  }

  private renderMessage(message: ChatMessage) {
    // Determine avatar icon based on role
    let avatar = 'ℹ️';
    if (message.role === 'user') {
      avatar = '👤';
    } else if (message.role === 'assistant') {
      avatar = '🤖';
    }

    return html`
      <div class="message ${message.role}">
        <div class="message-avatar">
          ${avatar}
        </div>
        <div class="message-content">
          <div class="message-text">${message.content}</div>
          ${message.metadata ? this.renderMessageMetadata(message) : ''}
        </div>
      </div>
    `;
  }

  private renderMessageMetadata(message: ChatMessage) {
    if (!message.metadata) return '';

    const { generatedApp, generatedEntities, generatedPages } = message.metadata;

    return html`
      <div class="message-metadata">
        <div class="preview-card">
          <h4>📱 ${generatedApp?.name || 'Application'}</h4>
          
          ${generatedEntities && generatedEntities.length > 0 ? html`
            <div class="preview-card">
              <h4>🗂️ Entities (${generatedEntities.length})</h4>
              <ul class="preview-list">
                ${generatedEntities.map((entity: EntityMeta) => html`
                  <li>${entity.name} (${entity.fields?.length || 0} fields)</li>
                `)}
              </ul>
            </div>
          ` : ''}

          ${generatedPages && generatedPages.length > 0 ? html`
            <div class="preview-card">
              <h4>📄 Pages (${generatedPages.length})</h4>
              <ul class="preview-list">
                ${generatedPages.map((page: any) => html`
                  <li>${page.name} (${page.template})</li>
                `)}
              </ul>
            </div>
          ` : ''}

          <div class="action-buttons">
            <button
              class="btn primary"
              @click=${() => this.handleConfirmCreate(message)}
              ?disabled=${this.isProcessing}
            >
              ✓ Create This App
            </button>
            <button
              class="btn"
              @click=${() => this.inputValue = 'Can you modify this by...'}
            >
              ✎ Request Changes
            </button>
          </div>
        </div>
      </div>
    `;
  }

  private renderLoading() {
    return html`
      <div class="loading">
        <div class="spinner"></div>
        <span>AI is thinking...</span>
      </div>
    `;
  }
}
