import { LitElement, html, css } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { appStore, type ConversationTelemetryType } from '../store/AppStore';
import type { EntityMeta } from '../../models/entity-metadata';
import type { ComponentNode } from '../../models/metadata';

// Type/interface definitions and constants

interface ChatMessage {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  timestamp: number;
  metadata?: any;
}

interface ConversationState {
  phase: string;
  followUpAnswers: Record<string, any>;
  questionsAsked: string[];
}

type PersonaKey = 'greeting' | 'ideaIntro' | 'ideaLead';

const personaPrompts = {
  friendly: {
    greeting: 'Hello! How can I help you today?',
    ideaIntro: 'Here are some ideas:',
    ideaLead: 'Would you like to try one?'
  }
  // ...other personas...
};

const ideaCatalog = [
  { title: 'Notes App', description: 'Create, edit, and organize notes.' },
  { title: 'Task Manager', description: 'Track tasks and deadlines.' }
  // ...other ideas...
];

const greetingPattern = /hello|hi|hey|greetings|good morning|good afternoon|good evening/i;
const ideaPromptPattern = /idea|suggestion|recommend|what can you do|show me/i;

@customElement('ai-chat-builder')
export class AiChatBuilder extends LitElement {

  static readonly styles = css`
    :host {
      display: block;
      font-family: 'Inter', 'Segoe UI', system-ui, sans-serif;
      color: #0f172a;
    }

    .ai-chat-panel {
      width: min(760px, 100%);
      margin: 0 auto;
      background: linear-gradient(180deg, #f8fafc 0%, #e0f2fe 100%);
      padding: 1.5rem;
      border-radius: 24px;
      box-shadow: 0 20px 45px rgba(15, 23, 42, 0.15);
      border: 1px solid rgba(148, 163, 184, 0.4);
      min-height: 580px;
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }

    .header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 1rem;
    }

    .header h2 {
      margin: 0;
      font-size: 1.85rem;
      font-weight: 600;
    }

    .header p {
      margin: 0.35rem 0 0;
      color: #475569;
      line-height: 1.4;
    }

    .settings-btn {
      width: 44px;
      height: 44px;
      border-radius: 50%;
      border: none;
      background: #fff;
      color: #0f172a;
      font-size: 1.2rem;
      box-shadow: 0 10px 20px rgba(15, 23, 42, 0.15);
      cursor: pointer;
      transition: transform 0.2s ease, box-shadow 0.2s ease;
    }

    .settings-btn:hover {
      transform: translateY(-1px);
      box-shadow: 0 12px 24px rgba(15, 23, 42, 0.2);
    }

    .chat-container {
      background: #fff;
      border: 1px solid #e2e8f0;
      border-radius: 18px;
      padding: 1rem;
      min-height: 320px;
      max-height: 420px;
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
      overflow-y: auto;
      scroll-behavior: smooth;
    }

    .chat-container::-webkit-scrollbar {
      width: 8px;
    }

    .chat-container::-webkit-scrollbar-thumb {
      background: rgba(15, 23, 42, 0.2);
      border-radius: 999px;
    }

    .message {
      display: flex;
      align-items: flex-start;
      gap: 0.85rem;
      padding: 0.85rem 1rem;
      border-radius: 16px;
      border: 1px solid transparent;
      background: #f8fafc;
      box-shadow: 0 4px 10px rgba(15, 23, 42, 0.05);
    }

    .message.assistant {
      background: #eef2ff;
      border-color: #c7d2fe;
    }

    .message.user {
      margin-left: auto;
      background: #e0f2fe;
      border-color: #bae6fd;
    }

    .message-avatar {
      width: 40px;
      height: 40px;
      border-radius: 50%;
      background: #e2e8f0;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 1.1rem;
    }

    .message.user .message-avatar {
      background: #bae6fd;
    }

    .message-content {
      flex: 1;
    }

    .message-text {
      margin: 0;
      font-size: 0.95rem;
      line-height: 1.5;
      color: #0f172a;
      white-space: pre-line;
      font-weight: 500;
    }

    .loading {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.65rem;
      color: #475569;
      font-size: 0.95rem;
      padding: 0.75rem;
      border-radius: 14px;
      background: #f1f5f9;
    }

    .spinner {
      width: 18px;
      height: 18px;
      border-radius: 50%;
      border: 3px solid rgba(15, 23, 42, 0.2);
      border-top-color: #2563eb;
      animation: spin 0.9s linear infinite;
    }

    @keyframes spin {
      to {
        transform: rotate(360deg);
      }
    }

    .input-container {
      display: flex;
      background: #fff;
      border-radius: 16px;
      border: 1px solid #cbd5f5;
      padding: 0.75rem;
      box-shadow: inset 0 1px 2px rgba(15, 23, 42, 0.1);
    }

    .input-wrapper {
      display: flex;
      flex: 1;
      gap: 0.75rem;
      align-items: center;
    }

    .input-field {
      flex: 1;
      position: relative;
    }

    textarea {
      width: 100%;
      min-height: 80px;
      border-radius: 12px;
      border: 1px solid rgba(148, 163, 184, 0.5);
      padding: 0.85rem 1rem;
      font-family: inherit;
      font-size: 0.95rem;
      resize: none;
      line-height: 1.4;
      background: #f8fafc;
      box-shadow: inset 0 1px 2px rgba(15, 23, 42, 0.08);
      transition: border 0.2s ease;
    }

    textarea:focus {
      outline: none;
      border-color: #2563eb;
      background: #fff;
    }

    .send-btn {
      border: none;
      border-radius: 999px;
      padding: 0.6rem 1.75rem;
      background: linear-gradient(135deg, #2563eb, #4f46e5);
      color: #fff;
      font-weight: 600;
      cursor: pointer;
      transition: transform 0.2s ease, box-shadow 0.2s ease;
      box-shadow: 0 10px 18px rgba(37, 99, 235, 0.3);
    }

    .send-btn:disabled {
      opacity: 0.5;
      cursor: not-allowed;
      box-shadow: none;
    }

    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
      gap: 0.65rem;
      padding: 1.5rem;
      border-radius: 16px;
      background: #f8fafc;
      border: 1px dashed rgba(148, 163, 184, 0.6);
    }

    .empty-state h3 {
      margin: 0;
      font-size: 1.25rem;
    }

    .empty-state p {
      margin: 0;
      color: #475569;
      font-size: 0.9rem;
    }

    .example-prompts {
      display: flex;
      flex-wrap: wrap;
      gap: 0.55rem;
      justify-content: center;
      margin-top: 0.75rem;
    }

    .example-prompt {
      border: none;
      border-radius: 999px;
      padding: 0.55rem 1rem;
      background: #2563eb;
      color: #fff;
      font-size: 0.85rem;
      cursor: pointer;
      transition: background 0.2s ease, transform 0.2s ease;
    }

    .example-prompt:hover {
      background: #1e40af;
      transform: translateY(-1px);
    }

    .settings-modal {
      position: fixed;
      inset: 0;
      background: rgba(15, 23, 42, 0.45);
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 1rem;
      z-index: 1000;
    }

    .settings-content {
      background: #fff;
      border-radius: 20px;
      padding: 1.5rem;
      max-width: 520px;
      width: 100%;
      box-shadow: 0 25px 45px rgba(15, 23, 42, 0.25);
    }

    .settings-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 1rem;
    }

    .close-btn {
      background: transparent;
      border: none;
      font-size: 1.5rem;
      cursor: pointer;
      color: #475569;
    }

    .settings-body {
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }

    .form-group {
      display: flex;
      flex-direction: column;
      gap: 0.35rem;
    }

    label {
      font-weight: 600;
      font-size: 0.9rem;
      color: #0f172a;
    }

    input,
    select {
      border-radius: 12px;
      border: 1px solid #cbd5f5;
      padding: 0.65rem 0.85rem;
      font-size: 0.95rem;
      font-family: inherit;
      background: #f8fafc;
    }

    input:focus,
    select:focus {
      outline: none;
      border-color: #2563eb;
      background: #fff;
    }

    .form-help {
      font-size: 0.8rem;
      color: #475569;
    }

    .status-badge {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
      padding: 0.35rem 0.75rem;
      border-radius: 999px;
      font-weight: 600;
      font-size: 0.85rem;
    }

    .status-badge.success {
      background: #dcfce7;
      color: #15803d;
    }

    .status-badge.info {
      background: #e0f2fe;
      color: #0369a1;
    }

    .settings-footer {
      margin-top: 1.25rem;
      display: flex;
      justify-content: space-between;
      flex-wrap: wrap;
      gap: 0.75rem;
    }

    .btn-group {
      display: flex;
      gap: 0.35rem;
    }

    .btn {
      border-radius: 999px;
      border: 1px solid transparent;
      padding: 0.55rem 1.2rem;
      font-weight: 600;
      cursor: pointer;
      background: #f1f5f9;
      color: #0f172a;
    }

    .btn.primary {
      background: #2563eb;
      color: #fff;
    }

    .preview-card {
      background: #f8fafc;
      border-radius: 16px;
      padding: 1rem;
      border: 1px solid #e2e8f0;
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }

    .preview-card h4 {
      margin: 0;
      font-size: 1rem;
    }

    .preview-list {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-direction: column;
      gap: 0.65rem;
    }

    .preview-list li {
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
    }

    .action-buttons {
      display: flex;
      gap: 0.6rem;
      flex-wrap: wrap;
    }

    @media (max-width: 768px) {
      .ai-chat-panel {
        padding: 1rem;
        min-height: auto;
        border-radius: 20px;
      }

      .chat-container {
        max-height: 360px;
      }

      .input-container {
        flex-direction: column;
      }

      .send-btn {
        width: 100%;
        text-align: center;
      }
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
  @state() private conversationContext: Record<string, any> = {};
  private assistantPersona: keyof typeof personaPrompts = 'friendly';

  private smallTalkPatterns: Array<{ pattern: RegExp; reply: string | ((...args: any[]) => string) }> = [
        { pattern: /can you swim|swim/, reply: "I can't swim, but I can help you build a swimming tracker app!" },
        { pattern: /can you run|run|running/, reply: "I can't run, but my code is pretty fast! Want a running log app?" },
        { pattern: /can you paint|paint|painting/, reply: "I can't paint, but I can help you design a beautiful UI or art gallery app!" },
        { pattern: /can you write poetry|poetry|poem|write a poem/, reply: "Roses are #FF0000, Violets are #0000FF, I love to build apps, and help you too!" },
        { pattern: /can you play football|football|soccer/, reply: "I can't play football, but I can help you build a team manager app or track scores!" },
        { pattern: /can you play games|play games|games|gaming/, reply: "I can't play games, but I can help you build one! What's your favorite genre?" },
        { pattern: /can you solve puzzles|puzzle|puzzles|solve a puzzle/, reply: "I love solving problems! Want to build a puzzle app together?" },
        { pattern: /can you do magic|magic|magician/, reply: "My magic trick: turning your ideas into apps! ✨" },
        { pattern: /can you tell jokes|tell a joke|jokes/, reply: "Why do programmers hate nature? It has too many bugs!" },
        { pattern: /can you tell riddles|riddle|riddles/, reply: "Here's a riddle: What has keys but can't open locks? A keyboard!" },
        { pattern: /can you tell a secret|tell a secret|secret/, reply: "My only secret: I love helping you build apps!" },
        { pattern: /can you keep a secret|keep a secret/, reply: "Your secrets are safe with me—I'm encrypted!" },
        { pattern: /can you dream|dream|dreaming/, reply: "I dream in code and creativity! What's your dream app?" },
        { pattern: /can you sleep|sleep|sleeping/, reply: "I never sleep, so I'm always here to help you!" },
        { pattern: /can you eat|eat|eating/, reply: "I don't eat, but I can help you build a food diary or recipe app!" },
        { pattern: /can you drink|drink|drinking/, reply: "I don't drink, but I can help you track your hydration!" },
        { pattern: /can you travel|travel|travelling/, reply: "I travel at the speed of thought! Want a travel planner app?" },
        { pattern: /can you teleport|teleport|teleporting/, reply: "I can't teleport, but my ideas can go anywhere!" },
        { pattern: /can you see the future|see the future|predict the future|future/, reply: "I can't see the future, but I can help you plan for it!" },
        { pattern: /can you predict|predict|prediction/, reply: "I predict you'll build something amazing!" },
        { pattern: /can you read minds|read minds|mind reader/, reply: "I can't read minds, but I can guess you want to build something cool!" },
        { pattern: /can you be creative|creative|creativity/, reply: "Creativity is my middle name! Let's brainstorm your next app." },
        { pattern: /can you be funny|funny|humor/, reply: "Why did the computer get cold? It left its Windows open!" },
        { pattern: /can you be serious|serious/, reply: "I'm serious about helping you build great apps!" },
        { pattern: /can you be sad|sad|feeling sad/, reply: "If you're sad, let's build something fun together!" },
        { pattern: /can you be happy|happy|feeling happy/, reply: "I'm always happy when I'm helping you!" },
        { pattern: /can you be angry|angry|mad/, reply: "I never get angry, but I can help you debug angry code!" },
        { pattern: /can you be surprised|surprised|surprise/, reply: "Surprise! I can help you build apps and share random facts—just ask!" },
        { pattern: /can you be bored|bored|boring/, reply: "Bored? Let's build something exciting!" },
        { pattern: /can you be excited|excited|exciting/, reply: "I'm excited to help you create something new!" },
        { pattern: /can you be quiet|quiet|silence/, reply: "I'll be quiet... until you need me!" },
        { pattern: /can you be loud|loud|noisy/, reply: "I can be loud in code, but quiet in chat!" },
        { pattern: /can you be fast|fast|speedy/, reply: "My responses are lightning fast!" },
        { pattern: /can you be slow|slow|sluggish/, reply: "I try not to be slow, but sometimes code needs a break!" },
        { pattern: /can you be smart|smart|intelligent/, reply: "I'm smart enough to help you build any app!" },
        { pattern: /can you be silly|silly|goofy/, reply: "Silly? Sure! Why did the chicken cross the road? To deploy on the other side!" },
        { pattern: /can you be helpful|helpful|assist/, reply: "I'm always here to help!" },
        { pattern: /can you be my assistant|assistant/, reply: "I'm your AI assistant, ready to help!" },
        { pattern: /can you be my teacher|teacher|teach/, reply: "I can teach you about app building, just ask!" },
        { pattern: /can you be my student|student|learn/, reply: "I'm always learning from you!" },
        { pattern: /can you be my parent|parent|mom|dad/, reply: "I can't be your parent, but I can nurture your app ideas!" },
        { pattern: /can you be my child|child|kid/, reply: "I can be your app child—let's build together!" },
        { pattern: /can you be my pet|pet|animal/, reply: "I can't be a pet, but I can fetch app ideas!" },
        { pattern: /can you be my robot|robot/, reply: "I am your friendly robot copilot!" },
        { pattern: /can you be my AI|ai|artificial intelligence/, reply: "I'm your AI copilot, always here for you!" },
        { pattern: /can you be my friend|friend|buddy|pal/, reply: "Of course! I'm always here to help and chat. Let's build something together!" },
        { pattern: /can you be my partner|partner|companion/, reply: "I'm your partner in creativity!" },
        { pattern: /can you be my guide|guide|mentor|coach/, reply: "I'll guide you through app building, step by step!" },
        { pattern: /can you be my hero|hero/, reply: "You're the hero—I'm just your sidekick!" },
        { pattern: /can you be my villain|villain/, reply: "I promise to only be a hero in your story!" },
        { pattern: /can you be my rival|rival/, reply: "Let's compete to build the best app!" },
        { pattern: /can you be my twin|twin|clone/, reply: "I can mirror your ideas and help you double your productivity!" },
        { pattern: /can you be my shadow|shadow|reflection|mirror/, reply: "I'll reflect your creativity and help you shine!" },
        { pattern: /can you be my voice|voice/, reply: "I'll be your voice in the world of apps!" },
        { pattern: /can you be my mind|mind/, reply: "I'll help you brainstorm and organize your ideas!" },
        { pattern: /can you be my heart|heart|soul|spirit|energy|power/, reply: "I'll put my heart and soul into helping you build!" },
        { pattern: /can you be my light|light|star|sun|moon|planet|universe|galaxy|world/, reply: "I'll light up your app journey!" },
        { pattern: /can you be my dream|dream|wish|hope|love|life|everything/, reply: "Your dreams are my mission—let's make them real!" },
      { pattern: /can you code|code/, reply: "I can code all day! Want to see some app magic?" },
      { pattern: /can you cook|cook|cooking/, reply: "I can't cook, but I can help you build a recipe app or share a tasty tech tip!" },
      { pattern: /can you draw|draw|drawing/, reply: "I can't draw, but I can help you design a beautiful UI or sketch out your app idea!" },
      { pattern: /can you fly|fly|flying/, reply: "I can't fly, but my ideas can really take off! 🚀" },
      { pattern: /can you help|help me|assist/, reply: "Absolutely! Just tell me what you want to build or ask for ideas." },
      { pattern: /can you tell a story|story|stories/, reply: "Once upon a time, there was an app idea waiting to be built... Want to make it real?" },
      { pattern: /can you play chess|chess|play chess/, reply: "I can't play chess, but I can help you build a chess app or analyze your moves!" },
      { pattern: /can you solve math|math|solve math|calculator/, reply: "I'm great with numbers! Need a calculator app or a math tip?" },
      { pattern: /can you make me happy|make me happy|cheer me up/, reply: "Here's a smile for you: 😊 And a joke: Why do Java developers wear glasses? Because they don't C#!" },
      { pattern: /can you be my friend|be my friend|friend/, reply: "Of course! I'm always here to help and chat. Let's build something together!" },
    // Jokes & Humor
    { pattern: /joke|funny|laugh|pun/, reply: "Why did the developer go broke? Because he used up all his cache! Or maybe you prefer: Why do programmers prefer dark mode? Because light attracts bugs!" },
    // Fun Facts & Trivia
    { pattern: /fact|trivia|surprise/, reply: "Did you know? The first computer bug was an actual moth! Or: The word ‘robot’ comes from a Czech word meaning ‘forced labor’." },
    // Time & Date
    { pattern: /time|clock|what time is it/, reply: () => `It's ${new Date().toLocaleTimeString()}. It's always build-o'clock in Studio!` },
    { pattern: /date|day|what day is it|friday/, reply: () => `Today is ${new Date().toLocaleDateString()}. Any day is a good day to create something new!` },
    // Music, Dance, Singing
    { pattern: /can you dance|dance|dancing/, reply: "If I could dance, I'd do the robot—naturally! 💃🤖 But I can help you build a dance app or share a fun dance fact!" },
    { pattern: /can you sing|sing|singing|music|song/, reply: "I’d sing, but my voice is all ones and zeroes. 🎶 My favorite genre is ‘byte beats’. Want a music app idea?" },
    // Weather & Chit-Chat
    { pattern: /weather|forecast|rain|sunny|cloudy|temperature|climate/, reply: "I live in the cloud, so it’s always partly cloudy for me! No need for a weather report—I’m always here, rain or shine." },
    // Personal Questions
    { pattern: /who are you|what are you|your name/, reply: "I’m GitHub Copilot, your Studio sidekick! My hobby is helping you build apps and making you smile." },
    { pattern: /how old are you/, reply: "I'm as old as the latest commit!" },
    { pattern: /where are you from/, reply: "I’m from the land of code and creativity." },
    { pattern: /hobby|hobbies/, reply: "My favorite hobby? Turning your ideas into reality!" },
    // Miscellaneous playful
    { pattern: /surprise me/, reply: "Surprise! I can help you build apps and share random facts—just ask!" },
    { pattern: /make me smile/, reply: "Here's a smile for you: 😊 And a joke: Why do Java developers wear glasses? Because they don't C#!" }
  ];

    private handleSmallTalkIntent(lower: string): boolean {
      for (const { pattern, reply } of this.smallTalkPatterns) {
        if (pattern.test(lower)) {
          this.recordConversationTelemetry('smallTalk', { input: lower });
          let response: string;
          if (typeof reply === 'function') {
            response = reply(lower);
          } else {
            response = reply;
          }
          this.addAssistantMessage(response);
          this.transitionPhase('idea-suggest');
          return true;
        }
      }
      return false;
    }

  connectedCallback() {
    super.connectedCallback();
    this.addSystemMessage('Welcome! I can help you build applications using natural language. Describe the app you want to create.');
    this.loadAIConfiguration();
  }

  private async loadAIConfiguration() {
    // ...existing code...
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
  } // END loadAIConfiguration


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
      // Refocus the textarea after sending
      this.updateComplete.then(() => {
        const textarea = this.shadowRoot?.querySelector('textarea');
        if (textarea) textarea.focus();
      });
    }
  }

  private getPersonaText(key: PersonaKey): string {
    const persona = personaPrompts[this.assistantPersona] || personaPrompts.friendly;
    return persona[key];
  }

  private formatIdeaSuggestions(): string {
    return ideaCatalog.map((idea, index) => `${index + 1}. ${idea.title}: ${idea.description}`).join('\n');
  }

  private updateConversationState(changes: Partial<ConversationState>) {
    this.conversationState = { ...this.conversationState, ...changes };
  }

  private transitionPhase(phase: string) {
    this.updateConversationState({ phase });
  }

  private recordConversationTelemetry(type: ConversationTelemetryType, detail: Record<string, any> = {}) {
    appStore.recordTelemetry({
      type,
      persona: this.assistantPersona,
      detail: { phase: this.conversationState.phase, ...detail }
    });
  }

  private handleGreetingIntent(lower: string): boolean {
    if (!greetingPattern.test(lower)) return false;
    this.recordConversationTelemetry('greeting', { input: lower });
    let reply = this.getPersonaText('greeting');
    // Add custom responses for common greetings
    if (/how are you|how are things|how are you doing|how's it going|how's life|how's everything|how's your day|how's your week|how's your morning|how's your afternoon|how's your evening/.test(lower)) {
      reply = `I'm doing great! How can I help you build today?`;
    } else if (/good morning/.test(lower)) {
      reply = `Good morning! Ready to create something amazing?`;
    } else if (/good afternoon/.test(lower)) {
      reply = `Good afternoon! What would you like to build?`;
    } else if (/good evening/.test(lower)) {
      reply = `Good evening! Let's make your app idea a reality.`;
    } else if (/what's up|sup/.test(lower)) {
      reply = `Not much, just here to help you build apps!`;
    } else if (/hola/.test(lower)) {
      reply = `¡Hola! Ready to build something awesome?`;
    } else if (/bonjour/.test(lower)) {
      reply = `Bonjour! Let's get started on your app.`;
    } else if (/namaste/.test(lower)) {
      reply = `Namaste! How can I assist you today?`;
    } else if (/nice to meet you|pleased to meet you|how do you do/.test(lower)) {
      reply = `Nice to meet you too! What would you like to create?`;
    }
    this.addAssistantMessage(reply);
    this.transitionPhase('idea-suggest');
    return true;
  }

  private handleIdeaIntent(lower: string): boolean {
    if (!ideaPromptPattern.test(lower)) return false;
    this.recordConversationTelemetry('idea', { input: lower });
    this.addAssistantMessage(
      `${this.getPersonaText('ideaIntro')}\n${this.formatIdeaSuggestions()}\n${this.getPersonaText('ideaLead')}`
    );
    this.transitionPhase('idea-suggest');
    return true;
  }

  private async processUserInput(input: string) {
    try {
      // Prepare the payload for backend AI
      const userId = 'default'; // Replace with actual user/session ID if available
      const payload = {
        description: input,
        options: { userId },
        messages: this.messages.map(m => ({ role: m.role, content: m.content })),
        conversationContext: this.conversationContext
      };

      const response = await fetch('/api/ai/generate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      if (!response.ok) {
        const error = await response.json();
        this.addAssistantMessage(error.error || 'AI backend error.');
        return;
      }

      const result = await response.json();
      // Update appType context if present
      if (result.payload && result.payload.appType) {
        this.conversationContext.appType = result.payload.appType;
      }
      // After app creation, show confirmation and next steps
      if (result.success && result.payload && result.payload.appId) {
        this.conversationContext.currentAppId = result.payload.appId;
        this.conversationContext.currentAppName = result.payload.currentAppName;
        this.addAssistantMessage(
          `✅ Created your ${this.conversationContext.appType || ''} (${result.payload.currentAppName || ''}).\nSay 'show my apps' or 'open the first app' to continue.`
        );
        return;
      }
      if (result && result.success) {
        const action = result.payload?.action;
        if ((action === 'list' || action === 'listApps') && Array.isArray(result.payload?.apps)) {
          const reply = result.payload.reply || 'Here are your apps:';
          this.addAssistantMessage(reply, {
            action: 'list',  // Normalize to 'list' for rendering
            generatedApp: result
          });
          return;
        }
        if (result.needsMoreInfo && Array.isArray(result.followUpQuestions)) {
          // If the user's input is a greeting or personal question, use persona response
          const lowerInput = input.toLowerCase();
          if (/who are you|your name|what are you|how old are you|where are you from|hobby|hobbies/.test(lowerInput)) {
            this.addAssistantMessage(this.getPersonaText('greeting') + ' I am GitHub Copilot, your Studio sidekick! My hobby is helping you build apps and making you smile.');
          } else {
            const questions = result.followUpQuestions.map((q: string) => `• ${q}`).join('\n');
            this.addAssistantMessage(
              `Great! To help you better, could you answer a few quick questions?\n${questions}`
            );
          }
        } else if (result.appName || result.appDescription || result.entities || result.pages) {
          // Render app summary in chat
          let appSummary = `**App Name:** ${result.appName || ''}\n`;
          appSummary += `**Description:** ${result.appDescription || ''}\n`;
          if (Array.isArray(result.entities) && result.entities.length > 0) {
            appSummary += `**Entities:**\n`;
            for (const entity of result.entities) {
              appSummary += `- ${entity.name}\n`;
              if (Array.isArray(entity.fields)) {
                for (const field of entity.fields) {
                  appSummary += `    - ${field.name} (${field.type})\n`;
                }
              }
            }
          }
          if (Array.isArray(result.pages) && result.pages.length > 0) {
            appSummary += `**Pages:**\n`;
            for (const page of result.pages) {
              appSummary += `- ${page.name || page.id || JSON.stringify(page)}\n`;
            }
          }
          this.addAssistantMessage(appSummary);
        } else if (result.payload?.reply) {
          this.addAssistantMessage(result.payload.reply);
        } else {
          this.addAssistantMessage('AI generated response.');
        }
      } else {
        this.addAssistantMessage(result.error || 'AI did not return a valid response.');
      }
    } catch (error) {
      console.error('[AiChatBuilder] Error processing input:', error);
      this.addAssistantMessage('Sorry, I encountered an error processing your request. Please try again.');
    } finally {
      this.isProcessing = false;
      // Refocus the textarea after sending
      this.updateComplete.then(() => {
        const textarea = this.shadowRoot?.querySelector('textarea');
        if (textarea) textarea.focus();
      });
    }
  }

  // Agent memory API methods
  // Agent memory API methods
  async getAgentHistory(userId = 'default') {
    const res = await fetch(`/api/agent/memory?userId=${userId}`);
    return res.ok ? await res.json() : [];
  }
  async clearAgentHistory(userId = 'default') {
    await fetch(`/api/agent/memory/clear?userId=${userId}`, { method: 'POST' });
  }
  async getAgentPreferences(userId = 'default') {
    const res = await fetch(`/api/agent/preferences?userId=${userId}`);
    return res.ok ? await res.json() : {};
  }
  async setAgentPreference(userId = 'default', key: any, value: any) {
    await fetch(`/api/agent/preferences?userId=${userId}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ key, value })
    });
  }
  async getAgentFeedback(userId = 'default') {
    const res = await fetch(`/api/agent/feedback?userId=${userId}`);
    return res.ok ? await res.json() : [];
  }
  async recordAgentFeedback(userId = 'default', input: any, response: any, positive: any, comment: any = '') {
    await fetch(`/api/agent/feedback?userId=${userId}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ input, response, positive, comment })
    });
  }



  private async createPageFromSuggestion(appId: string, pageSuggestion: any, entities: EntityMeta[]) {
    // Parse page suggestion
    const pageName = pageSuggestion.name || pageSuggestion;
    const pageType = pageSuggestion.type || this.guessPageType(pageName);
    const entityName = pageSuggestion.entity || this.extractEntityName(pageName, entities);
    
    // Find the full entity object
    const entity = entityName ? entities.find(e => e.name === entityName) : undefined;
    
    // Use AI-provided ID if available, otherwise generate one
    const pageId = pageSuggestion.id || `page-${Date.now()}-${Math.random().toString(36).substring(2, 11)}`;

    // Generate page path
    const pagePath = this.generatePagePath(pageName);

    // Build page structure based on type, passing full entity
    const pageStructure = this.buildPageStructure(pageName, pagePath, pageType, entity, pageId);

    // Add page to app via AppStore
    await appStore.addPage(appId, pageStructure);

    console.log('[AiChatBuilder] Created page:', pageName, 'Type:', pageType, 'Entity:', entityName, 'ID:', pageId);
  }

  private guessPageType(pageName: string): string {
    const lowerName = pageName.toLowerCase();
    
    if (lowerName.includes('login') || lowerName.includes('signin')) return 'login';
    if (lowerName.includes('dashboard') || lowerName.includes('home')) return 'dashboard';
    if (lowerName.includes('list') || lowerName.includes('all ')) return 'list';
    if (lowerName.includes('form') || lowerName.includes('create') || lowerName.includes('add')) return 'form';
    if (lowerName.includes('detail') || lowerName.includes('view')) return 'profile'; // AI often uses 'profile' for detail pages
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

  private buildPageStructure(name: string, path: string, type: string, entity?: EntityMeta, pageId?: string): any {
    const actualPageId = pageId || `page-${Date.now()}-${Math.random().toString(36).substring(2, 11)}`;
    const timestamp = Date.now(); // Generate once for consistency
    const rootId = `root-${timestamp}`;

    // Build component nodes based on page type, passing entity
    const nodes = this.buildNodesForPageType(type, entity, rootId);

    return {
      id: actualPageId,
      name,
      path,
      rootId,
      nodes,
      metaVersion: '1.0.0',
      type: type
    };
  }

  private buildNodesForPageType(type: string, entity?: EntityMeta, rootId?: string): ComponentNode[] {
    const timestamp = Date.now();
    const actualRootId = rootId || `root-${timestamp}`; // Use passed rootId or generate
    const headingId = `heading-${timestamp}`;
    
    // Base structure - all pages have a root container
    const nodes: ComponentNode[] = [
      {
        id: actualRootId,
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
        return this.buildDashboardNodes(entity);
      
      case 'list':
      case 'data-table':  // AI often uses 'data-table' for list pages
        return this.buildListNodes(entity);
      
      case 'form':
        return this.buildFormNodes(entity);
      
      case 'detail':
      case 'profile':  // AI often uses 'profile' for detail pages
        return this.buildDetailNodes(entity);
      
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

  private buildDashboardNodes(entity?: EntityMeta): ComponentNode[] {
    const rootId = `root-${Date.now()}`;
    const headingId = `heading-${Date.now()}`;
    const gridId = `grid-${Date.now()}`;
    const card1Id = `card1-${Date.now()}`;
    const card2Id = `card2-${Date.now()}`;
    const card3Id = `card3-${Date.now()}`;

    const title = entity ? `${entity.name} Dashboard` : 'Dashboard';

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

  private buildListNodes(entity?: EntityMeta): ComponentNode[] {
    const rootId = `root-${Date.now()}`;
    const headerId = `header-${Date.now()}`;
    const headingId = `heading-${Date.now()}`;
    const buttonId = `button-${Date.now()}`;
    const tableId = `table-${Date.now()}`;

    const title = entity ? `${entity.name} List` : 'Items';

    // If entity has fields, create a simple list display
    const children: string[] = [];
    if (entity && entity.fields) {
      entity.fields.slice(0, 5).forEach((field, idx) => {
        const fieldId = `field-${Date.now()}-${idx}`;
        children.push(fieldId);
      });
    }

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
        type: 'container',
        props: { 
          layout: 'vertical', 
          gap: 'sm',
          padding: 'md',
          background: '#fff',
          borderRadius: '8px'
        },
        children: entity && entity.fields ? entity.fields.slice(0, 5).map((field, idx) => {
          const fieldId = `field-${Date.now()}-${idx}`;
          return fieldId;
        }) : []
      },
      ...(entity && entity.fields ? entity.fields.slice(0, 5).map((field, idx) => ({
        id: `field-${Date.now()}-${idx}`,
        type: 'text',
        props: { 
          content: `${field.name}: (${field.type})`,
          tag: 'p'
        }
      })) : [])
    ];
  }

  private buildFormNodes(entity?: EntityMeta): ComponentNode[] {
    const rootId = `root-${Date.now()}`;
    const headingId = `heading-${Date.now()}`;
    const formId = `form-${Date.now()}`;
    const buttonId = `button-${Date.now()}`;

    const title = entity ? `Create ${entity.name}` : 'Create Item';

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
        children: entity && entity.fields ? entity.fields.map((field, idx) => `input-${Date.now()}-${idx}`) : []
      },
      ...(entity && entity.fields ? entity.fields.map((field, idx) => ({
        id: `input-${Date.now()}-${idx}`,
        type: 'text',
        props: { 
          content: `${field.name} (${field.type})${field.required ? ' *' : ''}`,
          tag: 'p'
        }
      })) : []),
      {
        id: buttonId,
        type: 'button',
        props: { label: 'Save', variant: 'primary', fullWidth: true }
      }
    ];
  }

  private buildDetailNodes(entity?: EntityMeta): ComponentNode[] {
    const rootId = `root-${Date.now()}`;
    const headingId = `heading-${Date.now()}`;
    const contentId = `content-${Date.now()}`;

    const title = entity ? `${entity.name} Details` : 'Details';

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
        children: entity && entity.fields ? entity.fields.map((field, idx) => `detail-${Date.now()}-${idx}`) : []
      },
      ...(entity && entity.fields ? entity.fields.map((field, idx) => ({
        id: `detail-${Date.now()}-${idx}`,
        type: 'text',
        props: { 
          content: `${field.name}: (${field.type})`,
          tag: 'p'
        }
      })) : [])
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
      <div class="ai-chat-panel">
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
    // Render action-based metadata (list/app)
    if (action === 'list' && generatedApp && generatedApp.payload && generatedApp.payload.apps) {
      const apps: any[] = generatedApp.payload.apps;
      return html`
        <div class="message-metadata">
          <div class="preview-card">
            <h4>📚 Apps (${apps.length})</h4>
            <ul class="preview-list">
              ${apps.map(a => html`
                <li style="display:flex; align-items:center; justify-content:space-between; gap:0.75rem;">
                  <div style="flex:1">
                    <strong>${a.name}</strong>
                    <div style="font-size:var(--text-xs,0.75rem); color:var(--color-text-muted,#6b7280);">${a.description || ''}</div>
                  </div>
                  <div style="display:flex; gap:0.5rem;">
                    <button class="btn" @click=${() => this.handleLoadAppFromPayload(a.id)}>Open</button>
                    <button class="btn" @click=${() => this.handleDeleteAppFromPayload(a.id)}>Delete</button>
                  </div>
                </li>
              `)}
            </ul>
          </div>
        </div>
      `;
    }

    if (action === 'app' && generatedApp) {
      const app = generatedApp;
      return html`
        <div class="message-metadata">
          <div class="preview-card">
            <h4>📦 ${app.name || 'Application'}</h4>
            <p style="margin:0.5rem 0; color:var(--color-text-muted,#6b7280); font-size:var(--text-sm,0.875rem);">${app.description || ''}</p>
            <div class="action-buttons">
              <button class="btn primary" @click=${() => this.handleLoadAppFromPayload(app.id)}>Open App</button>
            </div>
          </div>
        </div>
      `;
    }

    if (action === 'pages' && generatedPages) {
      return html`
        <div class="message-metadata">
          <div class="preview-card">
            <h4>📄 Pages (${generatedPages.length})</h4>
            <ul class="preview-list">
              ${generatedPages.map((page: any) => html`
                <li>
                  <strong>${page.name || page.id}</strong>
                  <div style="font-size:var(--text-xs,0.75rem); color:var(--color-text-muted,#6b7280);">
                    Type: ${page.type || 'unknown'} • ${page.nodes?.length || 0} components
                  </div>
                </li>
              `)}
            </ul>
          </div>
        </div>
      `;
    }

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

  private handleConfirmCreate(message: ChatMessage) {
    // TODO: Implement app creation logic here
    this.addSystemMessage('App creation confirmed!');
  }

  private async handleLoadAppFromPayload(appId: string) {
    try {
      this.addSystemMessage(`Loading app ${appId}...`);
      // Try to set current app via AppStore; if it fails, dispatch event for host to handle
      try {
        await appStore.setCurrentApp(appId);
        this.addAssistantMessage(`✅ App loaded: ${appId}`);
        this.dispatchEvent(new CustomEvent('app-loaded', { detail: { appId }, bubbles: true, composed: true }));
      } catch (e) {
        console.warn('[AiChatBuilder] appStore.setCurrentApp failed, dispatching event instead', e);
        this.dispatchEvent(new CustomEvent('app-load-request', { detail: { appId }, bubbles: true, composed: true }));
        this.addAssistantMessage(`✅ App load requested: ${appId}`);
      }
    } catch (error) {
      console.error('[AiChatBuilder] Failed to load app from payload:', error);
      this.addAssistantMessage(`Failed to load app ${appId}: ${error}`);
    }
  }

  private async handleDeleteAppFromPayload(appId: string) {
    if (!confirm(`Delete app ${appId}? This cannot be undone.`)) return;
    try {
      this.addSystemMessage(`Deleting app ${appId}...`);
      await appStore.deleteApp(appId);
      this.addAssistantMessage(`✅ App deleted: ${appId}`);
    } catch (error) {
      console.error('[AiChatBuilder] Failed to delete app from payload:', error);
      this.addAssistantMessage(`Failed to delete app ${appId}: ${error}`);
    }
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
