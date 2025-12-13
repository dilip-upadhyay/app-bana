import { LitElement, html, css, nothing } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { appStore } from '../store/AppStore';
import type { AppMeta } from '../../models/app-metadata';

@customElement('theme-editor')
export class ThemeEditor extends LitElement {
  static styles = css`
    :host {
      display: block;
      font-family: 'Inter', sans-serif;
    }
    
    .editor-container {
      display: flex;
      flex-direction: column;
      gap: 24px;
    }
    
    .section-title {
      font-size: 0.875rem;
      font-weight: 600;
      color: #64748b;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      margin-bottom: 12px;
      display: flex;
      align-items: center;
      gap: 8px;
    }
    
    .magic-input-wrapper {
      position: relative;
    }
    
    .magic-textarea {
      width: 100%;
      min-height: 80px;
      padding: 12px;
      border: 1px solid #cbd5e1;
      border-radius: 8px;
      font-family: inherit;
      font-size: 0.9rem;
      resize: vertical;
      transition: all 0.2s;
    }
    
    .magic-textarea:focus {
      outline: none;
      border-color: #3b82f6;
      box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
    }
    
    .generate-btn {
      margin-top: 8px;
      padding: 8px 16px;
      background: linear-gradient(135deg, #6366f1 0%, #a855f7 100%);
      color: white;
      border: none;
      border-radius: 6px;
      font-weight: 500;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 0.9rem;
      width: 100%;
      justify-content: center;
    }
    
    .generate-btn:hover {
      opacity: 0.9;
    }
    
    .generate-btn:disabled {
      opacity: 0.7;
      cursor: not-allowed;
    }

    .color-grid {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 16px;
    }
    
    .color-field {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    
    .color-label {
      font-size: 0.8rem;
      color: #475569;
    }
    
    .color-input-wrapper {
      display: flex;
      align-items: center;
      gap: 8px;
      border: 1px solid #e2e8f0;
      padding: 4px;
      border-radius: 6px;
      background: white;
    }
    
    .color-swatch {
      width: 32px;
      height: 32px;
      border-radius: 4px;
      border: none;
      padding: 0;
      cursor: pointer;
    }
    
    .color-text {
      font-family: monospace;
      font-size: 0.85rem;
      color: #334155;
      border: none;
      background: transparent;
      width: 100%;
    }
    
    .color-text:focus { outline: none; }

    .preview-card {
      background: var(--preview-surface, white);
      border: 1px solid var(--preview-border, #e2e8f0);
      border-radius: var(--preview-radius, 8px);
      padding: 24px;
      margin-top: 8px;
    }
    
    .preview-header {
      color: var(--preview-text, #1e293b);
      font-size: 1.25rem;
      font-weight: 600;
      margin-bottom: 8px;
    }
    
    .preview-text {
      color: var(--preview-text-secondary, #64748b);
      font-size: 0.9rem;
      margin-bottom: 16px;
    }
    
    .preview-btn {
      background: var(--preview-brand, #3b82f6);
      color: white;
      padding: 8px 16px;
      border-radius: var(--preview-radius, 4px);
      border: none;
      font-size: 0.9rem;
    }

    .divider {
      height: 1px;
      background: #e2e8f0;
      margin: 8px 0;
    }
  `;

  @property({ type: Object }) app: AppMeta | undefined;

  @state() private magicPrompt = '';
  @state() private isGenerating = false;

  // Local state for editing before save
  @state() private primaryColor = '#2563eb';
  @state() private secondaryColor = '#64748b';
  @state() private surfaceColor = '#ffffff';
  @state() private textColor = '#1e293b';
  @state() private borderRadius = '4px';

  connectedCallback() {
    super.connectedCallback();
    if (this.app?.theme) {
      this.syncFromApp();
    }
  }

  updated(changedProps: Map<string, any>) {
    if (changedProps.has('app') && this.app) {
      this.syncFromApp();
    }
  }

  private syncFromApp() {
    if (!this.app?.theme) return;
    this.primaryColor = this.app.theme.primaryColor || '#2563eb';
    this.secondaryColor = this.app.theme.secondaryColor || '#64748b';
    // We might need to expand AppTheme model to support more granular colors if needed
    // For now we map what we have
  }

  private async generateTheme() {
    if (!this.magicPrompt.trim()) return;

    this.isGenerating = true;
    try {
      const res = await fetch('/api/ai/theme-generate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ description: this.magicPrompt })
      });

      if (!res.ok) throw new Error('Generation failed');

      const theme = await res.json();

      // Apply generated colors
      if (theme.colors) {
        this.primaryColor = theme.colors.brand;
        this.secondaryColor = theme.colors.textSecondary;
        this.surfaceColor = theme.colors.surface;
        this.textColor = theme.colors.text;
      }
      if (theme.radius && theme.radius.sm) {
        this.borderRadius = theme.radius.sm;
      }

      this.saveTheme();

    } catch (e) {
      alert('Failed to generate theme');
    } finally {
      this.isGenerating = false;
    }
  }

  private applyPreset(preset: string) {
    switch (preset) {
      case 'modern-blue':
        this.primaryColor = '#2563eb';
        this.secondaryColor = '#64748b';
        this.surfaceColor = '#ffffff';
        this.textColor = '#1e293b';
        this.borderRadius = '4px';
        break;
      case 'forest-green':
        this.primaryColor = '#059669';
        this.secondaryColor = '#3f6212';
        this.surfaceColor = '#f0fdf4';
        this.textColor = '#14532d';
        this.borderRadius = '6px';
        break;
      case 'crimson-red':
        this.primaryColor = '#dc2626';
        this.secondaryColor = '#7f1d1d';
        this.surfaceColor = '#fef2f2';
        this.textColor = '#450a0a';
        this.borderRadius = '2px';
        break;
      case 'midnight-violet':
        this.primaryColor = '#8b5cf6';
        this.secondaryColor = '#a78bfa';
        this.surfaceColor = '#1e1b4b';
        this.textColor = '#f5f3ff';
        this.borderRadius = '8px';
        break;
      case 'corporate-gray':
        this.primaryColor = '#475569';
        this.secondaryColor = '#94a3b8';
        this.surfaceColor = '#f8fafc';
        this.textColor = '#0f172a';
        this.borderRadius = '0px';
        break;
    }
    this.saveTheme();
  }

  private saveTheme() {
    if (!this.app) return;

    appStore.updateApp(this.app.id, {
      theme: {
        ...this.app.theme,
        primaryColor: this.primaryColor,
        secondaryColor: this.secondaryColor,
        // We can store customized CSS variables in customCSS field if we expand the model
        // For now, let's just save the core fields we have
      }
    });
  }

  render() {
    // Dynamic styles for preview
    const previewStyles = `
      --preview-brand: ${this.primaryColor};
      --preview-text: ${this.textColor};
      --preview-text-secondary: ${this.secondaryColor};
      --preview-surface: ${this.surfaceColor};
      --preview-radius: ${this.borderRadius};
      --preview-border: ${this.secondaryColor}40;
    `;

    return html`
      <div class="editor-container">
        
        <!-- AI Generator -->
        <div>
          <div class="section-title">✨ Magic Theme Generator</div>
          <p style="font-size:0.85rem; color:#64748b; margin:0 0 8px 0">
            Describe the look you want (e.g., "Cyberpunk dark mode with neon pink accents")
          </p>
          <div class="magic-input-wrapper">
            <textarea
              class="magic-textarea"
              placeholder="e.g. Professional corporate blue, or Warm coffee shop vibes..."
              .value=${this.magicPrompt}
              @input=${(e: any) => this.magicPrompt = e.target.value}
            ></textarea>
            <button 
              class="generate-btn" 
              ?disabled=${this.isGenerating}
              @click=${this.generateTheme}
            >
              ${this.isGenerating ? 'Generating...' : '✨ Generate Theme'}
            </button>
          </div>
        </div>

        <div class="divider"></div>

        <!-- Presets -->
        <div>
          <div class="section-title">🎨 Presets</div>
          <select 
            class="magic-textarea" 
            style="min-height:40px; padding:8px;"
            @change=${(e: any) => this.applyPreset(e.target.value)}
          >
            <option value="">Select a preset...</option>
            <option value="modern-blue">🔵 Modern Blue (Default)</option>
            <option value="forest-green">🌲 Forest Green</option>
            <option value="crimson-red">🔴 Crimson Red</option>
            <option value="midnight-violet">🌙 Midnight Violet (Dark)</option>
            <option value="corporate-gray">🏢 Corporate Gray</option>
          </select>
        </div>

        <div class="divider"></div>

        <!-- Manual Controls -->
        <div>
          <div class="section-title">🎨 Tweak Colors</div>
          <div class="color-grid">
            ${this.renderColorInput('Primary Brand', this.primaryColor, (c) => { this.primaryColor = c; this.saveTheme(); })}
            ${this.renderColorInput('Secondary', this.secondaryColor, (c) => { this.secondaryColor = c; this.saveTheme(); })}
            ${this.renderColorInput('Surface', this.surfaceColor, (c) => { this.surfaceColor = c; this.saveTheme(); })}
            ${this.renderColorInput('Text', this.textColor, (c) => { this.textColor = c; this.saveTheme(); })}
          </div>
        </div>

        <!-- Live Preview -->
        <div>
          <div class="section-title">👁️ Live Preview</div>
          <div class="preview-card" style="${previewStyles}">
            <div class="preview-header">Theme Preview</div>
            <div class="preview-text">
              This is how your content will look with the applied theme. 
              The colors and border radius are reflected instantly.
            </div>
            <button class="preview-btn">Primary Action</button>
          </div>
        </div>

      </div>
    `;
  }

  private renderColorInput(label: string, value: string, onChange: (val: string) => void) {
    return html`
      <div class="color-field">
        <label class="color-label">${label}</label>
        <div class="color-input-wrapper">
          <input 
            type="color" 
            class="color-swatch" 
            .value=${value}
            @input=${(e: any) => onChange(e.target.value)}
          >
          <input 
            type="text" 
            class="color-text" 
            .value=${value}
            @change=${(e: any) => onChange(e.target.value)}
          >
        </div>
      </div>
    `;
  }
}
