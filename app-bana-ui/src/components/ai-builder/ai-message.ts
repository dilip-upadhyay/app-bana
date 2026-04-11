import { LitElement, html, css } from 'lit';
import { customElement, property } from 'lit/decorators.js';
import { unsafeHTML } from 'lit/directives/unsafe-html.js';

@customElement('ai-message')
export class AiMessage extends LitElement {
  static styles = css`
    :host {
      display: block;
      margin: 12px 0;
    }

    .message {
      display: flex;
      gap: 12px;
      align-items: flex-start;
    }

    .message.user {
      flex-direction: row-reverse;
    }

    .avatar {
      width: 32px;
      height: 32px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 16px;
      flex-shrink: 0;
    }

    .avatar.user {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
    }

    .avatar.assistant {
      background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
      color: white;
    }

    .content {
      max-width: 92%;
      padding: 12px 16px;
      border-radius: 12px;
      line-height: 1.6;
      font-size: 14px;
    }

    .content.user {
      background: #667eea;
      color: white;
      border-bottom-right-radius: 4px;
    }

    .content.assistant {
      background: #ffffff;
      color: #1a1a2e;
      border-bottom-left-radius: 4px;
      border: 1px solid #e8eaf6;
      box-shadow: 0 2px 10px rgba(102, 126, 234, 0.10);
      overflow: hidden;
    }

    /* ── H1: App title banner ── */
    .content.assistant h1 {
      font-size: 17px;
      font-weight: 800;
      color: #fff;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      margin: -12px -16px 16px -16px;
      padding: 16px 20px;
      border-radius: 0;
      letter-spacing: 0.3px;
    }

    /* ── H2: Section labels (pill style) ── */
    .content.assistant h2 {
      font-size: 10px;
      font-weight: 800;
      text-transform: uppercase;
      letter-spacing: 1.2px;
      color: #764ba2;
      background: #f3f0ff;
      border-radius: 20px;
      padding: 4px 14px;
      margin: 18px 0 10px 0;
      display: inline-block;
    }

    /* ── H3: Entity / group card ── */
    .content.assistant h3 {
      font-size: 14px;
      font-weight: 700;
      color: #2d2d6b;
      background: #f7f8ff;
      border-left: 4px solid #667eea;
      border-radius: 0 8px 8px 0;
      padding: 9px 14px;
      margin: 10px 0 6px 0;
    }

    .content.assistant p {
      margin: 5px 0;
      color: #4a4a6a;
      font-size: 13.5px;
      line-height: 1.6;
    }

    /* ── Lists: checkmark bullets ── */
    .content.assistant ul {
      margin: 4px 0 4px 0;
      padding: 0;
      list-style: none;
    }

    .content.assistant ol {
      margin: 4px 0 4px 18px;
      padding: 0;
    }

    .content.assistant li {
      font-size: 13.5px;
      color: #333355;
      padding: 4px 8px 4px 26px;
      margin: 2px 0;
      position: relative;
      line-height: 1.55;
    }

    .content.assistant ul > li::before {
      content: "✔";
      position: absolute;
      left: 6px;
      color: #667eea;
      font-size: 10px;
      top: 7px;
    }

    .content.assistant strong {
      color: #2d2d6b;
      font-weight: 700;
    }

    .content.assistant em {
      color: #777;
      font-style: italic;
    }

    .content.assistant code {
      background: #f0f2ff;
      color: #5c35cc;
      border-radius: 4px;
      padding: 1px 5px;
      font-family: 'Courier New', monospace;
      font-size: 12px;
    }

    .content.assistant pre {
      background: #1e1e3f;
      color: #a8b4ff;
      border-radius: 8px;
      padding: 12px;
      overflow-x: auto;
      font-size: 12px;
      margin: 8px 0;
    }

    .content.assistant pre code {
      background: none;
      color: inherit;
      padding: 0;
    }

    /* ── HR: dashed divider ── */
    .content.assistant hr {
      border: none;
      border-top: 2px dashed #e0e0f0;
      margin: 16px 0;
    }

    .timestamp {
      font-size: 11px;
      color: #999;
      margin-top: 4px;
    }

    .action-buttons {
      display: flex;
      gap: 8px;
      margin-top: 12px;
      flex-wrap: wrap;
    }

    .action-button {
      padding: 8px 16px;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      border: none;
      border-radius: 20px;
      font-size: 13px;
      font-weight: 600;
      cursor: pointer;
      transition: transform 0.2s, box-shadow 0.2s;
    }

    .action-button:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
    }

    .action-button.secondary {
      background: white;
      color: #667eea;
      border: 1px solid #667eea;
    }

    .action-button.secondary:hover {
      background: #f5f7ff;
      box-shadow: 0 4px 12px rgba(102, 126, 234, 0.2);
    }
  `;

  @property() role: 'user' | 'assistant' = 'user';
  @property() content = '';
  @property() timestamp?: Date;

  private parseActions(content: string): { text: string; actions: string[] } {
    const actionsMatch = content.match(/\[ACTIONS:\s*([^\]]+)\]/);
    if (!actionsMatch) {
      return { text: content, actions: [] };
    }
    const actionsText = actionsMatch[1];
    const actions = actionsText.split('|').map(a => a.trim()).filter(a => a);
    const cleanText = content.replace(actionsMatch[0], '').trim();
    return { text: cleanText, actions };
  }

  private markdownToHtml(md: string): string {
    // Escape HTML entities first
    let out = md
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');

    // Fenced code blocks (before inline code)
    out = out.replace(/```[\w]*\n([\s\S]*?)```/g, (_m, code) =>
      `<pre><code>${code.trim()}</code></pre>`);

    // Headings
    out = out.replace(/^### (.+)$/gm, '<h3>$1</h3>');
    out = out.replace(/^## (.+)$/gm,  '<h2>$1</h2>');
    out = out.replace(/^# (.+)$/gm,   '<h1>$1</h1>');

    // Bold + italic
    out = out.replace(/\*\*\*(.+?)\*\*\*/g, '<strong><em>$1</em></strong>');
    out = out.replace(/\*\*(.+?)\*\*/g,     '<strong>$1</strong>');
    out = out.replace(/\*(.+?)\*/g,         '<em>$1</em>');

    // Inline code
    out = out.replace(/`([^`]+)`/g, '<code>$1</code>');

    // Horizontal rules
    out = out.replace(/^---+$/gm, '<hr/>');

    // List items
    out = out.replace(/^[ \t]*[-*] (.+)$/gm, '<li>$1</li>');
    out = out.replace(/^[ \t]*\d+\. (.+)$/gm, '<li class="ol">$1</li>');

    // Wrap consecutive <li> blocks in <ul>
    out = out.replace(/((?:<li(?:[^>]*)>[\s\S]*?<\/li>\n?)+)/g, (block) => {
      const tag = block.includes('class="ol"') ? 'ol' : 'ul';
      return `<${tag}>${block}</${tag}>`;
    });

    // Paragraphs for plain text lines
    const blockStart = /^<(h[1-3]|ul|ol|li|pre|hr|blockquote)/;
    const lines = out.split('\n');
    const result: string[] = [];
    for (const line of lines) {
      const t = line.trim();
      if (!t) continue;
      if (blockStart.test(t) || t.startsWith('</') || t.startsWith('<pre') || t.startsWith('<hr')) {
        result.push(t);
      } else {
        result.push(`<p>${t}</p>`);
      }
    }
    return result.join('\n');
  }

  private handleActionClick(action: string) {
    this.dispatchEvent(new CustomEvent('action-click', {
      detail: { action },
      bubbles: true,
      composed: true
    }));
  }

  render() {
    const { text, actions } = this.parseActions(this.content);

    const renderedContent = this.role === 'assistant'
      ? unsafeHTML(this.markdownToHtml(text))
      : text;

    return html`
      <div class="message ${this.role}">
        <div class="avatar ${this.role}">
          ${this.role === 'user' ? '👤' : '🤖'}
        </div>
        <div>
          <div class="content ${this.role}">
            ${renderedContent}
          </div>
          ${actions.length > 0 && this.role === 'assistant' ? html`
            <div class="action-buttons">
              ${actions.map((action, index) => html`
                <button
                  class="action-button ${index > 0 ? 'secondary' : ''}"
                  @click=${() => this.handleActionClick(action)}
                >
                  ${action}
                </button>
              `)}
            </div>
          ` : ''}
          ${this.timestamp ? html`
            <div class="timestamp">
              ${this.formatTime(this.timestamp)}
            </div>
          ` : ''}
        </div>
      </div>
    `;
  }

  private formatTime(date: Date): string {
    return date.toLocaleTimeString('en-US', {
      hour: '2-digit',
      minute: '2-digit'
    });
  }
}
