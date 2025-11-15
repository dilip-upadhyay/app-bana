// ...existing code...

// Remove duplicate/stray class and misplaced code above imports
import { LitElement, html, css } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { appStore, type ConversationTelemetryType } from '../store/AppStore';
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
    action?: 'preview' | 'create' | 'confirm' | 'follow-up' | 'clarify' | 'list' | 'app' | 'delete' | 'pages';
    followUpQuestions?: string[];
    pendingGeneration?: any;
  };
}

type ConversationPhase = 'initial' | 'idea-suggest' | 'gathering-info' | 'confirming-details' | 'ready-to-create' | 'creating';

interface ConversationState {
  phase: ConversationPhase;
  userIntent?: string;
  appName?: string;
  appDescription?: string;
  entities?: any[];
  pages?: any[];
  followUpAnswers: Record<string, string>;
  questionsAsked: string[];
}

const personaPrompts = {
  friendly: {
    greeting: 'Hey there! I’m your AI copilot for Studio. Tell me what you want to build or ask for ideas and I’ll pick a helpful direction.',
    ideaIntro: 'I can take the lead and map a complete metadata path. Here are three ideas tuned for Studio:',
    ideaLead: 'Let me know which one resonates or describe your problem and I’ll choose the best route.',
    decisionLead: 'Here’s what I’ll build next—just say yes and I’ll get started.'
  }
} as const;

const ideaCatalog = [
  {
    title: 'Team Ops Command Center',
    description: 'Dashboards, approvals, and action cards for distributed operations teams.'
  },
  {
    title: 'Client Success Portal',
    description: 'CRM-style tables, guided forms, and alerts so teams can manage every customer journey.'
  },
  {
    title: 'Resource Scheduler',
    description: 'Booking workflows, capacity planning, and notifications tied to your data model.'
  }
];

const greetingPattern = /^(hi|hello|hey|greetings|yo|how are you|howdy|good morning|good afternoon|good evening|what's up|sup|hola|bonjour|namaste|nice to meet you|pleased to meet you|how do you do|how are things|how are you doing|how are things going|how is it going|how's it going|how's life|how's everything|how's your day|how's your week|how's your morning|how's your afternoon|how's your evening)([.!]?\s*)?$/i;
const ideaPromptPattern = /(what should i build|suggest (?:an|some)? app|ideas (?:for|to build)|decide what to build|choose (?:an|a)? app)/;
type PersonaKey = keyof typeof personaPrompts['friendly'];

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

  private transitionPhase(phase: ConversationPhase) {
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
      // Quick local intent detection for simple commands to avoid AI asking unnecessary follow-ups
      const lower = input.trim().toLowerCase();


      // Prioritize app suggestion if user asks for an idea or suggestion about a dance app
      if (/suggest|idea|recommend|build|create/.test(lower) && /dance/.test(lower)) {
        this.recordConversationTelemetry('idea', { input: lower });
        this.addAssistantMessage(
          `Here's a dance app idea for you:
          \n**Dance Academy Portal**: Manage classes, instructors, schedules, and student registrations. Includes video lessons, event calendars, and feedback forms.\nWant to customize it or add more features?`
        );
        this.transitionPhase('idea-suggest');
        return;
      }

      if (this.handleGreetingIntent(lower)) {
        return;
      }

      if (this.handleSmallTalkIntent(lower)) {
        return;
      }

      if (this.handleIdeaIntent(lower)) {
        return;
      }

      // List apps
      if (/\b(show|list|display|all)\b.*\bapps?\b/.test(lower) || /\bmy apps\b/.test(lower)) {
        // Send explicit action to backend
        const response = await fetch('/api/ai/generate', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ action: 'listApps' })
        });
        if (!response.ok) throw new Error(`API error: ${response.statusText}`);
        const result = await response.json();
        if (result.success && result.payload && result.payload.apps) {
          this.addAssistantMessage(`I found ${result.payload.apps.length} apps.`, {
            generatedApp: { payload: result.payload },
            action: 'list'
          });
        } else {
          this.addAssistantMessage(result.error || 'No apps found.');
        }
        return;
      }

      // Open/load an app by name/id: try to extract an id-like token
      const openMatch = lower.match(/\b(open|load|show)\b.*\bapp\b\s*([A-Za-z0-9_\-]+)/);
      if (openMatch) {
        const appId = openMatch[2];
        const response = await fetch('/api/ai/generate', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ action: 'loadApp', options: { appId } })
        });
        if (!response.ok) throw new Error(`API error: ${response.statusText}`);
        const result = await response.json();
        if (result.success && result.payload && result.payload.app) {
          this.addAssistantMessage(`Loaded app: ${result.payload.app.name || appId}`, {
            action: 'app',
            generatedApp: result.payload.app,
            generatedPages: result.payload.pages || []
          });
        } else {
          this.addAssistantMessage(result.error || `Failed to load app ${appId}`);
        }
        return;
      }

      // Delete an app by id/name
      const deleteMatch = lower.match(/\b(delete|remove)\b.*\bapp\b\s*([A-Za-z0-9_\-]+)/);
      if (deleteMatch) {
        const appId = deleteMatch[2];
        if (!confirm(`Delete app ${appId}? This cannot be undone.`)) return;
        const response = await fetch('/api/ai/generate', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ action: 'deleteApp', options: { appId } })
        });
        if (!response.ok) throw new Error(`API error: ${response.statusText}`);
        const result = await response.json();
        if (result.success) {
          this.addAssistantMessage(`App ${appId} deleted.`);
        } else {
          this.addAssistantMessage(result.error || `Failed to delete app ${appId}`);
        }
        return;
      }

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
          this.transitionPhase('gathering-info');
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
        // If backend returned an action payload (listApps, loadApp, deleteApp, listPages), handle UI rendering
        if (result.payload) {
          if (result.payload.apps) {
            // Show list of apps with actions
            this.addAssistantMessage(
              `I found ${result.payload.apps.length} apps.`,
              {
                pendingGeneration: result,
                action: 'list',
                generatedPages: [],
                generatedEntities: [],
                followUpQuestions: [] as any,
                // include payload so renderMessageMetadata can show buttons
                // store under generatedApp for reuse in metadata rendering
                generatedApp: { payload: result.payload }
              }
            );
            return;
          }

          if (result.payload.pages) {
            // Show list of pages for the app
            const pages = result.payload.pages;
            const pageCount = result.payload.pageCount || pages.length;
            const appId = result.payload.appId;
            
            this.addAssistantMessage(
              `This app has ${pageCount} page${pageCount === 1 ? '' : 's'}:`,
              {
                action: 'pages',
                generatedPages: pages,
                generatedApp: { id: appId }
              }
            );
            return;
          }

          if (result.payload.app) {
            // Show app details with option to open
            const app = result.payload.app;
            this.addAssistantMessage(
              `Loaded app: ${app.name || app.id}`,
              {
                action: 'app',
                generatedApp: app,
                generatedPages: result.payload.pages || []
              }
            );
            return;
          }

          if (result.payload.deleted !== undefined) {
            const deleted = !!result.payload.deleted;
            this.addAssistantMessage(deleted ? 'App deleted successfully.' : 'Failed to delete app.');
            return;
          }
        }

        // Check if AI is asking follow-up questions
        if (result.followUpQuestions && result.followUpQuestions.length > 0) {
          this.updateConversationState({
            phase: 'gathering-info',
            userIntent: input
          });
          
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
        this.updateConversationState({
          appName: result.appName,
          appDescription: result.appDescription,
          entities: result.entities || [],
          pages: result.suggestedPages || [],
          phase: 'ready-to-create'
        });
        this.recordConversationTelemetry('decision', { appName: result.appName, description: result.appDescription });

        // Show detailed preview with confirmation
        this.addAssistantMessage(
          `${this.getPersonaText('decisionLead')}\nI've prepared your app "${result.appName}". Here's what I'll create:`,
          {
            generatedApp: {
              id: `app-${Date.now()}`,
              name: result.appName,
              description: result.appDescription
            },
            generatedEntities: result.entities || [],
            generatedPages: result.pages || result.suggestedPages || [],
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
    // Get currently selected app from AppStore
    const currentApp = appStore.getCurrentApp();
    
    return {
      phase: this.conversationState.phase,
      userIntent: this.conversationState.userIntent,
      followUpAnswers: this.conversationState.followUpAnswers,
      questionsAsked: this.conversationState.questionsAsked,
      currentAppName: this.conversationState.appName,
      currentEntities: this.conversationState.entities,
      currentPages: this.conversationState.pages,
      // Include currently selected app context
      currentAppId: currentApp?.id,
      currentAppContext: currentApp ? {
        id: currentApp.id,
        name: currentApp.name,
        description: currentApp.description,
        entityCount: currentApp.entities?.length || 0,
        pageCount: currentApp.pages?.length || 0
      } : null
    };
  }

  private async handleConfirmCreate(message: ChatMessage) {
    if (!message.metadata) return;

    this.isProcessing = true;
    this.transitionPhase('creating');

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
      this.transitionPhase('ready-to-create'); // Allow retry
    } finally {
      this.isProcessing = false;
    }
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
