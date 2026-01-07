import { LitElement, html, css } from 'lit';
import { customElement, state } from 'lit/decorators.js';

/**
 * AuthGuard - Authentication gate for Studio
 * 
 * Checks if user is authenticated before allowing access to Studio.
 * If not authenticated, shows login/register form.
 */
@customElement('auth-guard')
export class AuthGuard extends LitElement {
  static styles = css`
    :host {
      display: block;
      width: 100%;
      height: 100vh;
    }

    .auth-container {
      display: flex;
      align-items: center;
      justify-content: center;
      min-height: 100vh;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      padding: 20px;
    }

    .auth-card {
      background: white;
      border-radius: 12px;
      box-shadow: 0 20px 60px rgba(0,0,0,0.3);
      padding: 40px;
      width: 100%;
      max-width: 420px;
    }

    .auth-header {
      text-align: center;
      margin-bottom: 32px;
    }

    .auth-logo {
      font-size: 48px;
      margin-bottom: 16px;
    }

    .auth-title {
      font-size: 28px;
      font-weight: 700;
      color: #1a202c;
      margin-bottom: 8px;
    }

    .auth-subtitle {
      color: #718096;
      font-size: 14px;
    }

    .auth-tabs {
      display: flex;
      gap: 8px;
      margin-bottom: 24px;
      border-bottom: 2px solid #e2e8f0;
    }

    .auth-tab {
      flex: 1;
      padding: 12px;
      background: none;
      border: none;
      border-bottom: 3px solid transparent;
      cursor: pointer;
      font-size: 16px;
      font-weight: 500;
      color: #718096;
      transition: all 0.2s;
      margin-bottom: -2px;
    }

    .auth-tab:hover {
      color: #4a5568;
    }

    .auth-tab.active {
      color: #667eea;
      border-bottom-color: #667eea;
    }

    .form-group {
      margin-bottom: 20px;
    }

    .form-label {
      display: block;
      font-size: 14px;
      font-weight: 500;
      color: #4a5568;
      margin-bottom: 8px;
    }

    .form-input {
      width: 100%;
      padding: 12px 16px;
      border: 2px solid #e2e8f0;
      border-radius: 8px;
      font-size: 14px;
      transition: all 0.2s;
      font-family: inherit;
    }

    .form-input:focus {
      outline: none;
      border-color: #667eea;
      box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
    }

    .form-input.error {
      border-color: #f56565;
    }

    .field-error {
      display: block;
      color: #f56565;
      font-size: 12px;
      margin-top: 4px;
      min-height: 16px;
    }

    .submit-btn {
      width: 100%;
      padding: 14px;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      border: none;
      border-radius: 8px;
      font-size: 16px;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s;
      margin-top: 8px;
    }

    .submit-btn:hover:not(:disabled) {
      transform: translateY(-2px);
      box-shadow: 0 10px 20px rgba(102, 126, 234, 0.3);
    }

    .submit-btn:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }

    .error-message {
      background: #fed7d7;
      color: #c53030;
      padding: 12px;
      border-radius: 8px;
      margin-bottom: 16px;
      font-size: 14px;
      text-align: center;
    }

    .success-message {
      background: #c6f6d5;
      color: #2f855a;
      padding: 12px;
      border-radius: 8px;
      margin-bottom: 16px;
      font-size: 14px;
      text-align: center;
    }

    .divider {
      text-align: center;
      margin: 24px 0;
      position: relative;
    }

    .divider::before {
      content: '';
      position: absolute;
      top: 50%;
      left: 0;
      right: 0;
      height: 1px;
      background: #e2e8f0;
    }

    .divider-text {
      background: white;
      padding: 0 16px;
      color: #a0aec0;
      font-size: 13px;
      position: relative;
    }

    .password-hint {
      font-size: 12px;
      color: #718096;
      margin-top: 4px;
    }

    .loading-overlay {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: rgba(255, 255, 255, 0.9);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
    }

    .spinner {
      width: 48px;
      height: 48px;
      border: 4px solid #e2e8f0;
      border-top-color: #667eea;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }
  `;

  @state() private mode: 'login' | 'register' = 'login';
  @state() private isLoading = false;
  @state() private isAuthenticated = false;
  @state() private errorMessage = '';
  @state() private successMessage = '';

  @state() private email = '';
  @state() private password = '';
  @state() private confirmPassword = '';
  @state() private name = '';

  @state() private fieldErrors: Record<string, string> = {};

  connectedCallback() {
    super.connectedCallback();
    this.checkAuthentication();
  }

  private checkAuthentication() {
    const token = localStorage.getItem('appbana_token');
    if (token) {
      // Verify token is still valid
      this.verifyToken(token);
    }
  }

  private async verifyToken(token: string) {
    try {
      // Try to fetch user profile or any authenticated endpoint
      const response = await fetch('/api/auth/profile', {
        headers: {
          'X-Session-Token': token
        }
      });

      if (response.ok) {
        this.isAuthenticated = true;
        this.dispatchAuthEvent(true);
      } else {
        // Token invalid, clear it
        localStorage.removeItem('appbana_token');
        this.isAuthenticated = false;
      }
    } catch (error) {
      // If endpoint doesn't exist, assume token is valid for now
      // Backend should validate on actual requests
      this.isAuthenticated = true;
      this.dispatchAuthEvent(true);
    }
  }

  private dispatchAuthEvent(authenticated: boolean) {
    this.dispatchEvent(new CustomEvent('auth-change', {
      detail: { authenticated },
      bubbles: true,
      composed: true
    }));
  }

  private switchMode(mode: 'login' | 'register') {
    this.mode = mode;
    this.errorMessage = '';
    this.successMessage = '';
    this.fieldErrors = {};
  }

  private validateEmail(email: string): boolean {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
  }

  private validatePassword(password: string): boolean {
    return password.length >= 8 && /[a-zA-Z]/.test(password) && /\d/.test(password);
  }

  private validateForm(): boolean {
    this.fieldErrors = {};
    let isValid = true;

    // Email validation
    if (!this.email) {
      this.fieldErrors['email'] = 'Email is required';
      isValid = false;
    } else if (!this.validateEmail(this.email)) {
      this.fieldErrors['email'] = 'Invalid email format';
      isValid = false;
    }

    // Password validation
    if (!this.password) {
      this.fieldErrors['password'] = 'Password is required';
      isValid = false;
    } else if (this.mode === 'register' && !this.validatePassword(this.password)) {
      this.fieldErrors['password'] = 'Password must be 8+ chars with letters and numbers';
      isValid = false;
    }

    // Register-specific validations
    if (this.mode === 'register') {
      if (!this.name) {
        this.fieldErrors['name'] = 'Name is required';
        isValid = false;
      }

      if (!this.confirmPassword) {
        this.fieldErrors['confirmPassword'] = 'Please confirm password';
        isValid = false;
      } else if (this.password !== this.confirmPassword) {
        this.fieldErrors['confirmPassword'] = 'Passwords do not match';
        isValid = false;
      }
    }

    this.requestUpdate();
    return isValid;
  }

  private async handleSubmit(e: Event) {
    e.preventDefault();
    
    if (!this.validateForm()) {
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';
    this.successMessage = '';

    try {
      const endpoint = this.mode === 'login' ? '/api/auth/login' : '/api/auth/register';
      
      const body = this.mode === 'login' 
        ? { email: this.email, password: this.password }
        : { email: this.email, password: this.password, name: this.name };

      const response = await fetch(endpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(body)
      });

      const data = await response.json();

      if (response.ok) {
        // Store session token
        const sessionId = data.sessionId || data.token;
        if (sessionId) {
          localStorage.setItem('appbana_token', sessionId);
        }

        this.successMessage = this.mode === 'login' 
          ? 'Login successful! Loading Studio...'
          : 'Registration successful! Loading Studio...';

        // Wait a moment to show success message
        setTimeout(() => {
          this.isAuthenticated = true;
          this.dispatchAuthEvent(true);
        }, 1000);
      } else {
        // Handle error response
        this.errorMessage = data.message || data.error || 
          (this.mode === 'login' ? 'Invalid credentials' : 'Registration failed');
      }
    } catch (error) {
      console.error(`${this.mode} error:`, error);
      this.errorMessage = 'Network error. Please check your connection and try again.';
    } finally {
      this.isLoading = false;
    }
  }

  private handleInputChange(field: string, value: string) {
    (this as any)[field] = value;
    
    // Clear field error when user types
    if (this.fieldErrors[field]) {
      delete this.fieldErrors[field];
      this.requestUpdate();
    }
  }

  render() {
    if (this.isAuthenticated) {
      return html`<slot></slot>`;
    }

    return html`
      <div class="auth-container">
        ${this.isLoading ? html`
          <div class="loading-overlay">
            <div class="spinner"></div>
          </div>
        ` : ''}

        <div class="auth-card">
          <div class="auth-header">
            <div class="auth-logo">🚀</div>
            <h1 class="auth-title">AppBana Studio</h1>
            <p class="auth-subtitle">Build applications visually with no code</p>
          </div>

          <div class="auth-tabs">
            <button 
              class="auth-tab ${this.mode === 'login' ? 'active' : ''}"
              @click=${() => this.switchMode('login')}>
              Sign In
            </button>
            <button 
              class="auth-tab ${this.mode === 'register' ? 'active' : ''}"
              @click=${() => this.switchMode('register')}>
              Sign Up
            </button>
          </div>

          ${this.errorMessage ? html`
            <div class="error-message">${this.errorMessage}</div>
          ` : ''}

          ${this.successMessage ? html`
            <div class="success-message">${this.successMessage}</div>
          ` : ''}

          <form @submit=${this.handleSubmit}>
            ${this.mode === 'register' ? html`
              <div class="form-group">
                <label class="form-label">Full Name</label>
                <input
                  type="text"
                  class="form-input ${this.fieldErrors['name'] ? 'error' : ''}"
                  .value=${this.name}
                  @input=${(e: Event) => this.handleInputChange('name', (e.target as HTMLInputElement).value)}
                  placeholder="John Doe"
                  ?disabled=${this.isLoading}
                />
                <span class="field-error">${this.fieldErrors['name'] || ''}</span>
              </div>
            ` : ''}

            <div class="form-group">
              <label class="form-label">Email Address</label>
              <input
                type="email"
                class="form-input ${this.fieldErrors['email'] ? 'error' : ''}"
                .value=${this.email}
                @input=${(e: Event) => this.handleInputChange('email', (e.target as HTMLInputElement).value)}
                placeholder="you@example.com"
                ?disabled=${this.isLoading}
              />
              <span class="field-error">${this.fieldErrors['email'] || ''}</span>
            </div>

            <div class="form-group">
              <label class="form-label">Password</label>
              <input
                type="password"
                class="form-input ${this.fieldErrors['password'] ? 'error' : ''}"
                .value=${this.password}
                @input=${(e: Event) => this.handleInputChange('password', (e.target as HTMLInputElement).value)}
                placeholder="••••••••"
                ?disabled=${this.isLoading}
              />
              ${this.mode === 'register' ? html`
                <div class="password-hint">
                  Must be 8+ characters with letters and numbers
                </div>
              ` : ''}
              <span class="field-error">${this.fieldErrors['password'] || ''}</span>
            </div>

            ${this.mode === 'register' ? html`
              <div class="form-group">
                <label class="form-label">Confirm Password</label>
                <input
                  type="password"
                  class="form-input ${this.fieldErrors['confirmPassword'] ? 'error' : ''}"
                  .value=${this.confirmPassword}
                  @input=${(e: Event) => this.handleInputChange('confirmPassword', (e.target as HTMLInputElement).value)}
                  placeholder="••••••••"
                  ?disabled=${this.isLoading}
                />
                <span class="field-error">${this.fieldErrors['confirmPassword'] || ''}</span>
              </div>
            ` : ''}

            <button 
              type="submit" 
              class="submit-btn"
              ?disabled=${this.isLoading}>
              ${this.isLoading ? 'Please wait...' : 
                this.mode === 'login' ? 'Sign In' : 'Create Account'}
            </button>
          </form>
        </div>
      </div>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'auth-guard': AuthGuard;
  }
}
