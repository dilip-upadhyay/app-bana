import { LitElement, html, css } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { AuthService } from './auth-service';

@customElement('register-page')
export class RegisterPage extends LitElement {
    static styles = css`
    :host {
      display: flex;
      justify-content: center;
      align-items: center;
      min-height: 100vh;
      background-color: #f5f7fa;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    }
    .card {
      background: white;
      padding: 2rem;
      border-radius: 8px;
      box-shadow: 0 4px 6px rgba(0,0,0,0.1);
      width: 100%;
      max-width: 400px;
    }
    h1 {
      margin-top: 0;
      text-align: center;
      color: #333;
    }
    .form-group {
      margin-bottom: 1rem;
    }
    label {
      display: block;
      margin-bottom: 0.5rem;
      color: #555;
    }
    input {
      width: 100%;
      padding: 0.75rem;
      border: 1px solid #ddd;
      border-radius: 4px;
      box-sizing: border-box;
      font-size: 1rem;
    }
    button {
      width: 100%;
      padding: 0.75rem;
      background-color: #28a745;
      color: white;
      border: none;
      border-radius: 4px;
      font-size: 1rem;
      cursor: pointer;
      margin-top: 1rem;
    }
    button:disabled {
      background-color: #ccc;
    }
    .error {
      color: red;
      margin-bottom: 1rem;
      text-align: center;
    }
    .footer {
      margin-top: 1rem;
      text-align: center;
      font-size: 0.9rem;
    }
    a {
      color: #007bff;
      text-decoration: none;
    }
  `;

    @state() private name = '';
    @state() private email = '';
    @state() private password = '';
    @state() private error = '';
    @state() private isLoading = false;

    async handleSubmit(e: Event) {
        e.preventDefault();
        this.error = '';
        this.isLoading = true;
        try {
            await AuthService.register(this.name, this.email, this.password);
            window.location.href = '/studio';
        } catch (err: any) {
            this.error = err.message;
        } finally {
            this.isLoading = false;
        }
    }

    render() {
        return html`
      <div class="card">
        <h1>Create Account</h1>
        ${this.error ? html`<div class="error">${this.error}</div>` : ''}
        <form @submit=${this.handleSubmit}>
          <div class="form-group">
            <label>Full Name</label>
            <input 
              type="text" 
              .value=${this.name} 
              @input=${(e: any) => this.name = e.target.value}
              required 
            />
          </div>
          <div class="form-group">
            <label>Email</label>
            <input 
              type="email" 
              .value=${this.email} 
              @input=${(e: any) => this.email = e.target.value}
              required 
            />
          </div>
          <div class="form-group">
            <label>Password</label>
            <input 
              type="password" 
              .value=${this.password} 
              @input=${(e: any) => this.password = e.target.value}
              required 
              minlength="6"
            />
          </div>
          <button type="submit" ?disabled=${this.isLoading}>
            ${this.isLoading ? 'Creating account...' : 'Register'}
          </button>
        </form>
        <div class="footer">
          Already have an account? <a href="/login">Login</a>
        </div>
      </div>
    `;
    }
}
