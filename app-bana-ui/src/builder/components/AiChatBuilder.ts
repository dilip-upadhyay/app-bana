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
      position: relative;
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

    /* Settings Button */
    .settings-btn {
      position: absolute;
      top: 1rem;
      right: 1.5rem;
      padding: 0.5rem;
      background: white;
      border: 1px solid var(--color-border, #e5e7eb);
      border-radius: 0.375rem;
      cursor: pointer;
      font-size: 1.25rem;
      transition: all 150ms;
    }

    .settings-btn:hover {
      background: var(--color-surface-alt, #f9fafb);
      border-color: var(--color-brand, #3b82f6);
    }

    /* Settings Modal */
    .settings-modal {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: rgba(0, 0, 0, 0.5);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
      animation: fadeIn 0.2s ease-out;
    }

    @keyframes fadeIn {
      from { opacity: 0; }
      to { opacity: 1; }
    }

    .settings-content {
      background: white;
      border-radius: 0.5rem;
      box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1);
      max-width: 600px;
      width: 90%;
      max-height: 80vh;
      overflow-y: auto;
      animation: slideUp 0.3s ease-out;
    }

    @keyframes slideUp {
      from {
        opacity: 0;
        transform: translateY(20px);
      }
      to {
        opacity: 1;
        transform: translateY(0);
      }
    }

    .settings-header {
      padding: 1.5rem;
      border-bottom: 1px solid var(--color-border, #e5e7eb);
      display: flex;
      align-items: center;
      justify-content: space-between;
    }

    .settings-header h3 {
      margin: 0;
      font-size: var(--text-lg, 1.125rem);
      color: var(--color-text, #111827);
    }

    .close-btn {
      padding: 0.25rem;
      background: none;
      border: none;
      font-size: 1.5rem;
      cursor: pointer;
      color: var(--color-text-muted, #6b7280);
      line-height: 1;
    }

    .close-btn:hover {
      color: var(--color-text, #111827);
    }

    .settings-body {
      padding: 1.5rem;
    }

    .form-group {
      margin-bottom: 1.5rem;
    }

    .form-group label {
      display: block;
      margin-bottom: 0.5rem;
      font-size: var(--text-sm, 0.875rem);
      font-weight: 500;
      color: var(--color-text, #111827);
    }

    .form-group select,
    .form-group input {
      width: 100%;
      padding: 0.5rem;
      border: 1px solid var(--color-border, #e5e7eb);
      border-radius: 0.375rem;
      font-size: var(--text-sm, 0.875rem);
      font-family: inherit;
    }

    .form-group select:focus,
    .form-group input:focus {
      outline: none;
      border-color: var(--color-brand, #3b82f6);
      box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
    }

    .form-group input[type="password"] {
      font-family: monospace;
    }

    .form-help {
      margin-top: 0.25rem;
      font-size: var(--text-xs, 0.75rem);
      color: var(--color-text-muted, #6b7280);
    }

    .status-badge {
      display: inline-flex;
      align-items: center;
      gap: 0.25rem;
      padding: 0.25rem 0.5rem;
      border-radius: 0.25rem;
      font-size: var(--text-xs, 0.75rem);
      font-weight: 500;
    }

    .status-badge.success {
      background: #d1fae5;
      color: #065f46;
    }

    .status-badge.error {
      background: #fee2e2;
      color: #991b1b;
    }

    .status-badge.info {
      background: #dbeafe;
      color: #1e40af;
    }

    .settings-footer {
      padding: 1rem 1.5rem;
      border-top: 1px solid var(--color-border, #e5e7eb);
      display: flex;
      gap: 0.75rem;
      justify-content: flex-end;
    }

    .btn-group {
      display: flex;
      gap: 0.5rem;
    }
  `;

  @state() private messages: ChatMessage[] = [];
  @state() private inputValue = '';
  @state() private isProcessing = false;
  @state() private showSettings = false;
  @state() private aiConfig: any = null;
  @state() private aiProviders: any[] = [];
  @state() private isLoadingConfig = false;
  @state() private isSavingConfig = false;
  @state() private isTestingConnection = false;
  @state() private testResult: { success: boolean; message: string } | null = null;

  connectedCallback() {
    super.connectedCallback();
    this.addSystemMessage('Welcome! I can help you build applications using natural language. Describe the app you want to create.');
    this.loadAIConfiguration();
  }

  private async loadAIConfiguration() {
    try {
      // Load current AI configuration
      const configResponse = await fetch('/api/ai/config');
      if (configResponse.ok) {
        this.aiConfig = await configResponse.json();
      }

      // Load available AI providers
      const providersResponse = await fetch('/api/ai/providers');
      if (providersResponse.ok) {
        this.aiProviders = await providersResponse.json();
      }
    } catch (error) {
      console.error('[AiChatBuilder] Failed to load AI configuration:', error);
    }
  }

  private async openSettings() {
    this.showSettings = true;
    this.testResult = null;
    await this.loadAIConfiguration();
  }

  private closeSettings() {
    this.showSettings = false;
    this.testResult = null;
  }

  private async saveAIConfiguration() {
    if (!this.aiConfig) return;

    this.isSavingConfig = true;
    try {
      const response = await fetch('/api/ai/config', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(this.aiConfig)
      });

      if (response.ok) {
        this.addSystemMessage('✅ AI configuration saved successfully!');
        this.closeSettings();
      } else {
        const error = await response.json();
        alert(`Failed to save configuration: ${error.error || 'Unknown error'}`);
      }
    } catch (error) {
      console.error('[AiChatBuilder] Failed to save AI configuration:', error);
      alert('Failed to save configuration');
    } finally {
      this.isSavingConfig = false;
    }
  }

  private async testAIConnection() {
    this.isTestingConnection = true;
    this.testResult = null;

    try {
      const response = await fetch('/api/ai/test', {
        method: 'POST'
      });

      const result = await response.json();
      this.testResult = {
        success: result.success,
        message: result.message || (result.success ? 'Connection successful!' : 'Connection failed')
      };
    } catch (error) {
      console.error('[AiChatBuilder] Connection test failed:', error);
      this.testResult = {
        success: false,
        message: 'Connection test failed: ' + (error as Error).message
      };
    } finally {
      this.isTestingConnection = false;
    }
  }

  private updateConfigField(field: string, value: any) {
    this.aiConfig = {
      ...this.aiConfig,
      [field]: value
    };
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
        <button class="settings-btn" @click=${this.openSettings} title="AI Settings">
          ⚙️
        </button>
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

      ${this.showSettings ? this.renderSettingsModal() : ''}
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

  private renderSettingsModal() {
    if (!this.aiConfig) {
      return html`
        <div class="settings-modal" @click=${this.closeSettings}>
          <div class="settings-content" @click=${(e: Event) => e.stopPropagation()}>
            <div class="settings-header">
              <h3>⚙️ AI Settings</h3>
              <button class="close-btn" @click=${this.closeSettings}>×</button>
            </div>
            <div class="settings-body">
              <p>Loading configuration...</p>
            </div>
          </div>
        </div>
      `;
    }

    const selectedProvider = this.aiProviders.find(p => p.id === this.aiConfig.provider);
    const availableModels = selectedProvider?.models || [];

    return html`
      <div class="settings-modal" @click=${this.closeSettings}>
        <div class="settings-content" @click=${(e: Event) => e.stopPropagation()}>
          <div class="settings-header">
            <h3>⚙️ AI Settings</h3>
            <button class="close-btn" @click=${this.closeSettings}>×</button>
          </div>

          <div class="settings-body">
            ${this.aiConfig.isEnabled ? html`
              <div class="status-badge success">
                ✓ AI Enabled
              </div>
            ` : html`
              <div class="status-badge info">
                ℹ AI Not Configured
              </div>
            `}

            <div class="form-group">
              <label for="ai-provider">AI Provider</label>
              <select
                id="ai-provider"
                .value=${this.aiConfig.provider || ''}
                @change=${(e: Event) => this.updateConfigField('provider', (e.target as HTMLSelectElement).value)}
              >
                <option value="">-- Select Provider --</option>
                ${this.aiProviders.map(provider => html`
                  <option value=${provider.id}>${provider.name}</option>
                `)}
              </select>
              <div class="form-help">
                ${selectedProvider?.description || 'Choose an AI provider to enable app generation'}
              </div>
            </div>

            ${this.aiConfig.provider === 'openai' ? html`
              <div class="form-group">
                <label for="openai-key">OpenAI API Key</label>
                <input
                  type="password"
                  id="openai-key"
                  .value=${this.aiConfig.openaiApiKey || ''}
                  @input=${(e: Event) => this.updateConfigField('openaiApiKey', (e.target as HTMLInputElement).value)}
                  placeholder="sk-..."
                />
                <div class="form-help">
                  ${this.aiConfig.hasOpenaiKey ? '✓ API key configured' : 'Enter your OpenAI API key'}
                </div>
              </div>

              <div class="form-group">
                <label for="openai-model">Model</label>
                <select
                  id="openai-model"
                  .value=${this.aiConfig.openaiModel || 'gpt-4o-mini'}
                  @change=${(e: Event) => this.updateConfigField('openaiModel', (e.target as HTMLSelectElement).value)}
                >
                  ${availableModels.map((model: string) => html`
                    <option value=${model}>${model}</option>
                  `)}
                </select>
              </div>
            ` : ''}

            ${this.aiConfig.provider === 'anthropic' ? html`
              <div class="form-group">
                <label for="anthropic-key">Anthropic API Key</label>
                <input
                  type="password"
                  id="anthropic-key"
                  .value=${this.aiConfig.anthropicApiKey || ''}
                  @input=${(e: Event) => this.updateConfigField('anthropicApiKey', (e.target as HTMLInputElement).value)}
                  placeholder="sk-ant-..."
                />
                <div class="form-help">
                  ${this.aiConfig.hasAnthropicKey ? '✓ API key configured' : 'Enter your Anthropic API key'}
                </div>
              </div>

              <div class="form-group">
                <label for="anthropic-model">Model</label>
                <select
                  id="anthropic-model"
                  .value=${this.aiConfig.anthropicModel || 'claude-3-5-sonnet-20241022'}
                  @change=${(e: Event) => this.updateConfigField('anthropicModel', (e.target as HTMLSelectElement).value)}
                >
                  ${availableModels.map((model: string) => html`
                    <option value=${model}>${model}</option>
                  `)}
                </select>
              </div>
            ` : ''}

            ${this.aiConfig.provider === 'ollama' ? html`
              <div class="form-group">
                <label for="ollama-url">Ollama URL</label>
                <input
                  type="text"
                  id="ollama-url"
                  .value=${this.aiConfig.ollamaUrl || 'http://localhost:11434'}
                  @input=${(e: Event) => this.updateConfigField('ollamaUrl', (e.target as HTMLInputElement).value)}
                  placeholder="http://localhost:11434"
                />
                <div class="form-help">
                  Local Ollama server URL
                </div>
              </div>

              <div class="form-group">
                <label for="ollama-model">Model</label>
                <select
                  id="ollama-model"
                  .value=${this.aiConfig.ollamaModel || 'llama3.1'}
                  @change=${(e: Event) => this.updateConfigField('ollamaModel', (e.target as HTMLSelectElement).value)}
                >
                  ${availableModels.map((model: string) => html`
                    <option value=${model}>${model}</option>
                  `)}
                </select>
              </div>
            ` : ''}

            ${this.testResult ? html`
              <div class="status-badge ${this.testResult.success ? 'success' : 'error'}">
                ${this.testResult.success ? '✓' : '✗'} ${this.testResult.message}
              </div>
            ` : ''}
          </div>

          <div class="settings-footer">
            <div class="btn-group">
              <button
                class="btn"
                @click=${this.testAIConnection}
                ?disabled=${!this.aiConfig.provider || this.isTestingConnection}
              >
                ${this.isTestingConnection ? 'Testing...' : 'Test Connection'}
              </button>
            </div>
            <div class="btn-group">
              <button class="btn" @click=${this.closeSettings}>
                Cancel
              </button>
              <button
                class="btn primary"
                @click=${this.saveAIConfiguration}
                ?disabled=${this.isSavingConfig}
              >
                ${this.isSavingConfig ? 'Saving...' : 'Save'}
              </button>
            </div>
          </div>
        </div>
      </div>
    `;
  }
}
