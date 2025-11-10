import { LitElement, html, css } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { appStore } from '../store/AppStore';
import type { EntityMeta } from '../../models/entity-metadata';
import type { ComponentNode } from '../../models/metadata';

interface ChatMessage {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  timestamp: number;
  metadata?: {
    generatedApp?: any;
    generatedEntities?: EntityMeta[];
    generatedPages?: any[];
    action?: 'preview' | 'create' | 'confirm' | 'follow-up' | 'clarify';
    followUpQuestions?: string[];
    pendingGeneration?: any;
  };
}

interface ConversationState {
  phase: 'initial' | 'gathering-info' | 'confirming-details' | 'ready-to-create' | 'creating';
  userIntent?: string;
  appName?: string;
  appDescription?: string;
  entities?: any[];
  pages?: any[];
  followUpAnswers: Record<string, string>;
  questionsAsked: string[];
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
  @state() private readonly isLoadingConfig = false;
  @state() private isSavingConfig = false;
  @state() private isTestingConnection = false;
  @state() private testResult: { success: boolean; message: string } | null = null;
  @state() private conversationState: ConversationState = {
    phase: 'initial',
    followUpAnswers: {},
    questionsAsked: []
  };

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
      // Check conversation state
      if (this.conversationState.phase === 'ready-to-create') {
        // User is responding to confirmation - check for modify request
        if (input.toLowerCase().includes('modify') || 
            input.toLowerCase().includes('change') || 
            input.toLowerCase().includes('different')) {
          this.addAssistantMessage(
            `I can help you modify the app structure. What would you like to change? You can:\n` +
            `• Add or remove entities\n` +
            `• Modify entity fields\n` +
            `• Change relationships\n` +
            `• Add different page types`
          );
          this.conversationState.phase = 'gathering-info';
          return;
        }
      }

      // Build the prompt with conversation context
      const conversationContext = this.buildConversationContext(input);

      // Call backend AI generation API with enhanced mode
      const response = await fetch('/api/ai/generate', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          description: input,
          conversationContext: conversationContext,
          mode: this.conversationState.phase === 'initial' ? 'detailed' : 'refine'
        })
      });

      if (!response.ok) {
        throw new Error(`API error: ${response.statusText}`);
      }

      const result = await response.json();

      if (result.success) {
        // Check if AI is asking follow-up questions
        if (result.followUpQuestions && result.followUpQuestions.length > 0) {
          this.conversationState.phase = 'gathering-info';
          this.conversationState.userIntent = input;
          
          this.addAssistantMessage(
            `I have a few questions to make your app better:\n\n${result.followUpQuestions.map((q: string, i: number) => `${i + 1}. ${q}`).join('\n')}`,
            {
              followUpQuestions: result.followUpQuestions,
              pendingGeneration: result,
              action: 'follow-up'
            }
          );
          return;
        }

        // Store conversation state
        this.conversationState.appName = result.appName;
        this.conversationState.appDescription = result.appDescription;
        this.conversationState.entities = result.entities || [];
        this.conversationState.pages = result.suggestedPages || [];
        this.conversationState.phase = 'ready-to-create';

        // Show detailed preview with confirmation
        this.addAssistantMessage(
          `I've prepared your app "${result.appName}". Here's what I'll create:`,
          {
            generatedApp: {
              id: `app-${Date.now()}`,
              name: result.appName,
              description: result.appDescription
            },
            generatedEntities: result.entities || [],
            generatedPages: result.suggestedPages || [],
            action: 'confirm'
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

  private buildConversationContext(currentInput: string): any {
    return {
      phase: this.conversationState.phase,
      userIntent: this.conversationState.userIntent,
      followUpAnswers: this.conversationState.followUpAnswers,
      questionsAsked: this.conversationState.questionsAsked,
      currentAppName: this.conversationState.appName,
      currentEntities: this.conversationState.entities,
      currentPages: this.conversationState.pages
    };
  }

  private async handleConfirmCreate(message: ChatMessage) {
    if (!message.metadata) return;

    this.isProcessing = true;
    this.conversationState.phase = 'creating';

    try {
      const { generatedApp, generatedEntities, generatedPages } = message.metadata;

      // Create app via AppStore - returns the created app with real ID from backend
      this.addSystemMessage(`Creating app "${generatedApp.name}"...`);
      const createdApp = await appStore.createApp({
        name: generatedApp.name,
        description: generatedApp.description
      });

      // Set as current app using the REAL ID from backend
      await appStore.setCurrentApp(createdApp.id);

      // Add entities to app
      if (generatedEntities && generatedEntities.length > 0) {
        this.addSystemMessage(`Adding ${generatedEntities.length} entities...`);
        await appStore.updateApp(createdApp.id, {
          entities: generatedEntities
        });
      }

      // Create pages based on AI suggestions
      if (generatedPages && generatedPages.length > 0) {
        this.addSystemMessage(`Creating ${generatedPages.length} pages...`);
        
        for (const pageSuggestion of generatedPages) {
          try {
            await this.createPageFromSuggestion(createdApp.id, pageSuggestion, generatedEntities || []);
          } catch (pageError) {
            console.error('[AiChatBuilder] Error creating page:', pageSuggestion, pageError);
            // Continue with other pages even if one fails
          }
        }
      }

      this.addAssistantMessage(
        `✅ Application "${createdApp.name}" created successfully!\n\n` +
        `• ${generatedEntities?.length || 0} entities created\n` +
        `• ${generatedPages?.length || 0} pages created\n\n` +
        `You can now view and edit your app in the Studio Builder.`
      );

      // Reset conversation state for next app
      this.conversationState = {
        phase: 'initial',
        followUpAnswers: {},
        questionsAsked: []
      };

      // Dispatch event to switch to app view using REAL ID
      this.dispatchEvent(new CustomEvent('app-created', {
        detail: { appId: createdApp.id },
        bubbles: true,
        composed: true
      }));
    } catch (error) {
      console.error('[AiChatBuilder] Error creating app:', error);
      this.addAssistantMessage(`❌ Failed to create application: ${error}`);
      this.conversationState.phase = 'ready-to-create'; // Allow retry
    } finally {
      this.isProcessing = false;
    }
  }

  private async createPageFromSuggestion(appId: string, pageSuggestion: any, entities: EntityMeta[]) {
    // Parse page suggestion
    const pageName = pageSuggestion.name || pageSuggestion;
    const pageType = pageSuggestion.type || this.guessPageType(pageName);
    const entityName = pageSuggestion.entity || this.extractEntityName(pageName, entities);

    // Generate page path
    const pagePath = this.generatePagePath(pageName);

    // Build page structure based on type
    const pageStructure = this.buildPageStructure(pageName, pagePath, pageType, entityName);

    // Add page to app via AppStore
    await appStore.addPage(appId, pageStructure);

    console.log('[AiChatBuilder] Created page:', pageName, 'Type:', pageType);
  }

  private guessPageType(pageName: string): string {
    const lowerName = pageName.toLowerCase();
    
    if (lowerName.includes('login') || lowerName.includes('signin')) return 'login';
    if (lowerName.includes('dashboard') || lowerName.includes('home')) return 'dashboard';
    if (lowerName.includes('list') || lowerName.includes('all ')) return 'list';
    if (lowerName.includes('form') || lowerName.includes('create') || lowerName.includes('add')) return 'form';
    if (lowerName.includes('detail') || lowerName.includes('view')) return 'detail';
    if (lowerName.includes('profile')) return 'profile';
    if (lowerName.includes('contact')) return 'contact';
    
    return 'blank';
  }

  private extractEntityName(pageName: string, entities: EntityMeta[]): string | undefined {
    const lowerName = pageName.toLowerCase();
    
    // Find entity mentioned in page name
    for (const entity of entities) {
      if (lowerName.includes(entity.name.toLowerCase())) {
        return entity.name;
      }
    }
    
    return undefined;
  }

  private generatePagePath(pageName: string): string {
    return '/' + pageName
      .toLowerCase()
      .replaceAll(/\s+/g, '-')
      .replaceAll(/[^a-z0-9-]/g, '');
  }

  private buildPageStructure(name: string, path: string, type: string, entityName?: string): any {
    const pageId = `page-${Date.now()}-${Math.random().toString(36).substring(2, 11)}`;
    const rootId = `root-${Date.now()}`;

    // Build component nodes based on page type
    const nodes = this.buildNodesForPageType(type, entityName);

    return {
      id: pageId,
      name,
      path,
      rootId,
      nodes,
      metaVersion: '1.0.0',
      type: type
    };
  }

  private buildNodesForPageType(type: string, entityName?: string): ComponentNode[] {
    const rootId = `root-${Date.now()}`;
    const headingId = `heading-${Date.now()}`;
    
    // Base structure - all pages have a root container
    const nodes: ComponentNode[] = [
      {
        id: rootId,
        type: 'container',
        props: {
          layout: 'vertical',
          gap: 'lg',
          padding: 'xl',
          maxWidth: '1200px',
          margin: '0 auto'
        },
        children: [headingId]
      }
    ];

    switch (type) {
      case 'login':
        return this.buildLoginNodes();
      
      case 'dashboard':
        return this.buildDashboardNodes(entityName);
      
      case 'list':
        return this.buildListNodes(entityName);
      
      case 'form':
        return this.buildFormNodes(entityName);
      
      case 'detail':
        return this.buildDetailNodes(entityName);
      
      default:
        // Blank page - just root container with heading
        nodes.push({
          id: headingId,
          type: 'text',
          props: {
            content: 'New Page',
            tag: 'h1',
            style: 'heading'
          }
        });
        return nodes;
    }
  }

  private buildLoginNodes(): ComponentNode[] {
    const rootId = `root-${Date.now()}`;
    const containerId = `container-${Date.now()}`;
    const headingId = `heading-${Date.now()}`;
    const emailId = `email-${Date.now()}`;
    const passwordId = `password-${Date.now()}`;
    const buttonId = `button-${Date.now()}`;

    return [
      {
        id: rootId,
        type: 'container',
        props: { layout: 'vertical', alignment: 'center', justifyContent: 'center', minHeight: '100vh' },
        children: [containerId]
      },
      {
        id: containerId,
        type: 'container',
        props: { layout: 'vertical', gap: 'md', padding: 'xl', maxWidth: '400px', background: '#fff', borderRadius: '8px', boxShadow: '0 2px 8px rgba(0,0,0,0.1)' },
        children: [headingId, emailId, passwordId, buttonId]
      },
      {
        id: headingId,
        type: 'text',
        props: { content: 'Login', tag: 'h2', textAlign: 'center' }
      },
      {
        id: emailId,
        type: 'text',
        props: { content: 'Email', tag: 'input', inputType: 'email', placeholder: 'Enter your email' }
      },
      {
        id: passwordId,
        type: 'text',
        props: { content: 'Password', tag: 'input', inputType: 'password', placeholder: 'Enter your password' }
      },
      {
        id: buttonId,
        type: 'button',
        props: { label: 'Sign In', variant: 'primary', fullWidth: true }
      }
    ];
  }

  private buildDashboardNodes(entityName?: string): ComponentNode[] {
    const rootId = `root-${Date.now()}`;
    const headingId = `heading-${Date.now()}`;
    const gridId = `grid-${Date.now()}`;
    const card1Id = `card1-${Date.now()}`;
    const card2Id = `card2-${Date.now()}`;
    const card3Id = `card3-${Date.now()}`;

    const title = entityName ? `${entityName} Dashboard` : 'Dashboard';

    return [
      {
        id: rootId,
        type: 'container',
        props: { layout: 'vertical', gap: 'lg', padding: 'xl' },
        children: [headingId, gridId]
      },
      {
        id: headingId,
        type: 'text',
        props: { content: title, tag: 'h1' }
      },
      {
        id: gridId,
        type: 'app-grid',
        props: { columns: '3', gap: 'md' },
        children: [card1Id, card2Id, card3Id]
      },
      {
        id: card1Id,
        type: 'container',
        props: { padding: 'lg', background: '#fff', borderRadius: '8px', boxShadow: '0 1px 3px rgba(0,0,0,0.1)' },
        children: [`text-${Date.now()}-1`]
      },
      {
        id: `text-${Date.now()}-1`,
        type: 'text',
        props: { content: 'Total Items: 0', tag: 'p' }
      },
      {
        id: card2Id,
        type: 'container',
        props: { padding: 'lg', background: '#fff', borderRadius: '8px', boxShadow: '0 1px 3px rgba(0,0,0,0.1)' },
        children: [`text-${Date.now()}-2`]
      },
      {
        id: `text-${Date.now()}-2`,
        type: 'text',
        props: { content: 'Active: 0', tag: 'p' }
      },
      {
        id: card3Id,
        type: 'container',
        props: { padding: 'lg', background: '#fff', borderRadius: '8px', boxShadow: '0 1px 3px rgba(0,0,0,0.1)' },
        children: [`text-${Date.now()}-3`]
      },
      {
        id: `text-${Date.now()}-3`,
        type: 'text',
        props: { content: 'Recent: 0', tag: 'p' }
      }
    ];
  }

  private buildListNodes(entityName?: string): ComponentNode[] {
    const rootId = `root-${Date.now()}`;
    const headerId = `header-${Date.now()}`;
    const headingId = `heading-${Date.now()}`;
    const buttonId = `button-${Date.now()}`;
    const tableId = `table-${Date.now()}`;

    const title = entityName ? `${entityName} List` : 'Items';

    return [
      {
        id: rootId,
        type: 'container',
        props: { layout: 'vertical', gap: 'lg', padding: 'xl' },
        children: [headerId, tableId]
      },
      {
        id: headerId,
        type: 'container',
        props: { layout: 'horizontal', justifyContent: 'space-between', alignItems: 'center' },
        children: [headingId, buttonId]
      },
      {
        id: headingId,
        type: 'text',
        props: { content: title, tag: 'h1' }
      },
      {
        id: buttonId,
        type: 'button',
        props: { label: 'Add New', variant: 'primary' }
      },
      {
        id: tableId,
        type: 'app-grid',
        props: { columns: '1', gap: 'sm' },
        children: []
      }
    ];
  }

  private buildFormNodes(entityName?: string): ComponentNode[] {
    const rootId = `root-${Date.now()}`;
    const headingId = `heading-${Date.now()}`;
    const formId = `form-${Date.now()}`;
    const buttonId = `button-${Date.now()}`;

    const title = entityName ? `Create ${entityName}` : 'Create Item';

    return [
      {
        id: rootId,
        type: 'container',
        props: { layout: 'vertical', gap: 'lg', padding: 'xl', maxWidth: '600px', margin: '0 auto' },
        children: [headingId, formId, buttonId]
      },
      {
        id: headingId,
        type: 'text',
        props: { content: title, tag: 'h1' }
      },
      {
        id: formId,
        type: 'container',
        props: { layout: 'vertical', gap: 'md' },
        children: []
      },
      {
        id: buttonId,
        type: 'button',
        props: { label: 'Save', variant: 'primary', fullWidth: true }
      }
    ];
  }

  private buildDetailNodes(entityName?: string): ComponentNode[] {
    const rootId = `root-${Date.now()}`;
    const headingId = `heading-${Date.now()}`;
    const contentId = `content-${Date.now()}`;

    const title = entityName ? `${entityName} Details` : 'Details';

    return [
      {
        id: rootId,
        type: 'container',
        props: { layout: 'vertical', gap: 'lg', padding: 'xl' },
        children: [headingId, contentId]
      },
      {
        id: headingId,
        type: 'text',
        props: { content: title, tag: 'h1' }
      },
      {
        id: contentId,
        type: 'container',
        props: { layout: 'vertical', gap: 'md', padding: 'lg', background: '#fff', borderRadius: '8px' },
        children: []
      }
    ];
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

    const { generatedApp, generatedEntities, generatedPages, action, followUpQuestions } = message.metadata;

    // Render follow-up questions
    if (action === 'follow-up' && followUpQuestions) {
      return html`
        <div class="message-metadata">
          <div class="preview-card">
            <h4>� Please provide more details</h4>
            <p style="margin-top: 0.5rem; font-size: var(--text-sm, 0.875rem);">
              Your answers will help me create a better app for you.
            </p>
          </div>
        </div>
      `;
    }

    // Render confirmation preview
    return html`
      <div class="message-metadata">
        <div class="preview-card">
          <h4>�📱 ${generatedApp?.name || 'Application'}</h4>
          <p style="margin: 0.5rem 0; color: var(--color-text-muted, #6b7280); font-size: var(--text-sm, 0.875rem);">
            ${generatedApp?.description || ''}
          </p>
          
          ${generatedEntities && generatedEntities.length > 0 ? html`
            <div class="preview-card" style="margin-top: 1rem;">
              <h4>🗂️ Entities (${generatedEntities.length})</h4>
              <ul class="preview-list">
                ${generatedEntities.map((entity: EntityMeta) => html`
                  <li>
                    <strong>${entity.name}</strong>
                    ${entity.fields && entity.fields.length > 0 ? html`
                      <ul style="list-style: none; padding-left: 1rem; margin: 0.25rem 0;">
                        ${entity.fields.slice(0, 5).map((field: any) => html`
                          <li style="font-size: var(--text-xs, 0.75rem); color: var(--color-text-muted, #6b7280);">
                            ${field.name}: ${field.type}${field.required ? ' *' : ''}
                          </li>
                        `)}
                        ${entity.fields.length > 5 ? html`
                          <li style="font-size: var(--text-xs, 0.75rem); color: var(--color-text-muted, #6b7280);">
                            ...and ${entity.fields.length - 5} more fields
                          </li>
                        ` : ''}
                      </ul>
                    ` : ''}
                  </li>
                `)}
              </ul>
            </div>
          ` : ''}

          ${generatedPages && generatedPages.length > 0 ? html`
            <div class="preview-card" style="margin-top: 1rem;">
              <h4>📄 Pages (${generatedPages.length})</h4>
              <ul class="preview-list">
                ${generatedPages.map((page: any) => {
                  const pageName = typeof page === 'string' ? page : page.name || 'Page';
                  const pageType = typeof page === 'object' ? page.type : '';
                  return html`
                    <li>
                      ${pageName}
                      ${pageType ? html`<span style="color: var(--color-text-muted, #6b7280);"> (${pageType})</span>` : ''}
                    </li>
                  `;
                })}
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
              @click=${() => {
                this.inputValue = 'Can you modify this by ';
                this.requestUpdate();
                // Focus the textarea
                this.updateComplete.then(() => {
                  const textarea = this.shadowRoot?.querySelector('textarea');
                  if (textarea) {
                    textarea.focus();
                    textarea.setSelectionRange(textarea.value.length, textarea.value.length);
                  }
                });
              }}
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
