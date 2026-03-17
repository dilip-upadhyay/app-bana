import { LitElement, html, css } from 'lit';
import { customElement, state } from 'lit/decorators.js';

@customElement('voice-input')
export class VoiceInput extends LitElement {
    static styles = css`
    :host {
      display: inline-block;
    }

    button {
      width: 40px;
      height: 40px;
      border-radius: 50%;
      border: 2px solid #667eea;
      background: white;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      transition: all 0.2s;
      font-size: 20px;
    }

    button:hover:not(:disabled) {
      background: #f5f5f5;
      transform: scale(1.05);
    }

    button:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }

    button.recording {
      background: #ef4444;
      border-color: #ef4444;
      animation: pulse 1.5s infinite;
    }

    @keyframes pulse {
      0%, 100% {
        transform: scale(1);
        box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.7);
      }
      50% {
        transform: scale(1.05);
        box-shadow: 0 0 0 10px rgba(239, 68, 68, 0);
      }
    }

    .transcript {
      position: absolute;
      bottom: 100%;
      right: 0;
      margin-bottom: 8px;
      padding: 8px 12px;
      background: white;
      border: 1px solid #e0e0e0;
      border-radius: 8px;
      box-shadow: 0 2px 8px rgba(0,0,0,0.1);
      font-size: 13px;
      color: #666;
      max-width: 200px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .error {
      color: #ef4444;
      font-size: 11px;
      margin-top: 4px;
    }
  `;

    @state() private isRecording = false;
    @state() private transcript = '';
    @state() private error = '';
    @state() private isSupported = false;

    private recognition: any = null;

    connectedCallback() {
        super.connectedCallback();
        this.checkSupport();
    }

    private checkSupport() {
        const SpeechRecognition = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;

        if (SpeechRecognition) {
            this.isSupported = true;
            this.recognition = new SpeechRecognition();
            this.recognition.continuous = false;
            this.recognition.interimResults = true;
            this.recognition.lang = 'en-US';

            this.recognition.onresult = (event: any) => {
                const transcript = Array.from(event.results)
                    .map((result: any) => result[0])
                    .map((result: any) => result.transcript)
                    .join('');

                this.transcript = transcript;
            };

            this.recognition.onend = () => {
                this.isRecording = false;
                if (this.transcript) {
                    this.dispatchTranscript();
                }
            };

            this.recognition.onerror = (event: any) => {
                this.isRecording = false;
                this.error = `Speech recognition error: ${event.error}`;
                console.error('Speech recognition error:', event.error);
            };
        } else {
            this.error = 'Speech recognition not supported in this browser';
        }
    }

    render() {
        if (!this.isSupported) {
            return html`
        <div class="error">Voice input not supported</div>
      `;
        }

        return html`
      <div style="position: relative;">
        ${this.transcript && this.isRecording ? html`
          <div class="transcript">${this.transcript}</div>
        ` : ''}
        
        <button
          class="${this.isRecording ? 'recording' : ''}"
          @click=${this.toggleRecording}
          title="${this.isRecording ? 'Stop recording' : 'Start voice input'}">
          ${this.isRecording ? '⏹️' : '🎤'}
        </button>

        ${this.error ? html`
          <div class="error">${this.error}</div>
        ` : ''}
      </div>
    `;
    }

    private toggleRecording() {
        if (!this.recognition) return;

        if (this.isRecording) {
            this.recognition.stop();
        } else {
            this.transcript = '';
            this.error = '';
            this.isRecording = true;

            try {
                this.recognition.start();
            } catch (error) {
                this.isRecording = false;
                this.error = 'Failed to start recording';
                console.error('Failed to start recording:', error);
            }
        }
    }

    private dispatchTranscript() {
        if (!this.transcript) return;

        this.dispatchEvent(new CustomEvent('transcript', {
            detail: { text: this.transcript },
            bubbles: true,
            composed: true
        }));

        this.transcript = '';
    }
}
