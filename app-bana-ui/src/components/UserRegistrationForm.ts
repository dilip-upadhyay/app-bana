import { html, LitElement, css } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { registerUser, UserRegistrationData } from '../demo/user-registration-test';

/**
 * User Registration Form Component
 * Demonstrates metadata-driven form with adapter integration
 */
@customElement('user-registration-form')
export class UserRegistrationForm extends LitElement {
  static readonly styles = css`
    :host {
      display: block;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
    }

    .registration-form-container {
      max-width: 600px;
      margin: 0 auto;
      padding: 2rem;
      background: #ffffff;
      border-radius: 8px;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
    }

    .form-header {
      text-align: center;
      margin-bottom: 2rem;
    }

    .form-header h2 {
      margin: 0 0 0.5rem 0;
      color: #333;
      font-size: 1.8rem;
    }

    .form-header p {
      margin: 0;
      color: #666;
      font-size: 0.9rem;
    }

    .form-group {
      margin-bottom: 1.5rem;
    }

    .form-row {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 1rem;
    }

    label {
      display: block;
      margin-bottom: 0.5rem;
      font-weight: 500;
      color: #333;
      font-size: 0.9rem;
    }

    .required {
      color: #e74c3c;
    }

    input {
      width: 100%;
      padding: 0.75rem;
      border: 1px solid #ddd;
      border-radius: 4px;
      font-size: 1rem;
      box-sizing: border-box;
      transition: border-color 0.2s;
    }

    input:focus {
      outline: none;
      border-color: #3498db;
      box-shadow: 0 0 0 3px rgba(52, 152, 219, 0.1);
    }

    input:disabled {
      background-color: #f5f5f5;
      cursor: not-allowed;
    }

    .form-group.has-error input {
      border-color: #e74c3c;
    }

    .error-message {
      display: block;
      margin-top: 0.5rem;
      color: #e74c3c;
      font-size: 0.85rem;
    }

    .submit-message {
      padding: 1rem;
      border-radius: 4px;
      margin-bottom: 1rem;
      font-weight: 500;
    }

    .submit-message.success {
      background-color: #d4edda;
      color: #155724;
      border: 1px solid #c3e6cb;
    }

    .submit-message.error {
      background-color: #f8d7da;
      color: #721c24;
      border: 1px solid #f5c6cb;
    }

    .form-actions {
      display: flex;
      gap: 1rem;
      justify-content: flex-end;
      margin-top: 2rem;
    }

    button {
      padding: 0.75rem 1.5rem;
      border: 1px solid #ddd;
      border-radius: 4px;
      font-size: 1rem;
      font-weight: 500;
      cursor: pointer;
      transition: all 0.2s;
    }

    button:hover:not(:disabled) {
      background-color: #f5f5f5;
    }

    button:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }

    button.primary {
      background-color: #3498db;
      color: white;
      border-color: #3498db;
    }

    button.primary:hover:not(:disabled) {
      background-color: #2980b9;
    }

    .form-footer {
      margin-top: 2rem;
      padding-top: 1rem;
      border-top: 1px solid #eee;
      text-align: center;
    }

    .form-footer p {
      margin: 0.5rem 0;
      color: #666;
      font-size: 0.85rem;
    }

    .form-footer code {
      background-color: #f5f5f5;
      padding: 0.2rem 0.5rem;
      border-radius: 3px;
      font-family: monospace;
      font-size: 0.8rem;
    }
  `;

  @state()
  private formData: UserRegistrationData = {
    email: '',
    firstName: '',
    lastName: '',
    password: '',
    dateOfBirth: '',
    phoneNumber: ''
  };

  @state()
  private errors: Record<string, string> = {};

  @state()
  private isSubmitting = false;

  @state()
  private submitMessage = '';

  @state()
  private submitSuccess = false;

  private validateEmail(email: string): string | null {
    if (!email) return 'Email is required';
    const pattern = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;
    if (!pattern.test(email)) return 'Please enter a valid email address';
    return null;
  }

  private validateName(name: string, fieldName: string): string | null {
    if (!name) return `${fieldName} is required`;
    if (name.length < 2) return `${fieldName} must be at least 2 characters`;
    if (name.length > 50) return `${fieldName} must be less than 50 characters`;
    return null;
  }

  private validatePassword(password: string): string | null {
    if (!password) return 'Password is required';
    if (password.length < 8) return 'Password must be at least 8 characters';
    return null;
  }

  private validatePhone(phone: string): string | null {
    if (!phone) return null; // Optional field
    const pattern = /^\+?[1-9]\d{1,14}$/;
    if (!pattern.test(phone)) return 'Please enter a valid phone number';
    return null;
  }

  private validateForm(): boolean {
    const newErrors: Record<string, string> = {};

    const emailError = this.validateEmail(this.formData.email);
    if (emailError) newErrors.email = emailError;

    const firstNameError = this.validateName(this.formData.firstName, 'First name');
    if (firstNameError) newErrors.firstName = firstNameError;

    const lastNameError = this.validateName(this.formData.lastName, 'Last name');
    if (lastNameError) newErrors.lastName = lastNameError;

    const passwordError = this.validatePassword(this.formData.password);
    if (passwordError) newErrors.password = passwordError;

    const phoneError = this.validatePhone(this.formData.phoneNumber || '');
    if (phoneError) newErrors.phoneNumber = phoneError;

    this.errors = newErrors;
    return Object.keys(newErrors).length === 0;
  }

  private handleInput(field: keyof UserRegistrationData, value: string) {
    this.formData = { ...this.formData, [field]: value };
    // Clear error for this field when user starts typing
    if (this.errors[field]) {
      const newErrors = { ...this.errors };
      delete newErrors[field];
      this.errors = newErrors;
    }
  }

  private async handleSubmit(e: Event) {
    e.preventDefault();
    
    if (!this.validateForm()) {
      this.submitMessage = 'Please fix the errors above';
      this.submitSuccess = false;
      return;
    }

    this.isSubmitting = true;
    this.submitMessage = '';

    try {
      const user = await registerUser(this.formData);
      this.submitSuccess = true;
      this.submitMessage = `✅ Registration successful! Welcome, ${user.firstName}! User ID: ${user.id}`;
      
      // Reset form
      this.formData = {
        email: '',
        firstName: '',
        lastName: '',
        password: '',
        dateOfBirth: '',
        phoneNumber: ''
      };
      this.errors = {};
    } catch (error) {
      this.submitSuccess = false;
      this.submitMessage = `❌ ${error instanceof Error ? error.message : 'Registration failed'}`;
    } finally {
      this.isSubmitting = false;
    }
  }

  private handleReset() {
    this.formData = {
      email: '',
      firstName: '',
      lastName: '',
      password: '',
      dateOfBirth: '',
      phoneNumber: ''
    };
    this.errors = {};
    this.submitMessage = '';
  }

  private renderSubmitMessage() {
    if (!this.submitMessage) {
      return html``;
    }
    const messageClass = this.submitSuccess ? 'success' : 'error';
    return html`
      <div class="submit-message ${messageClass}">
        ${this.submitMessage}
      </div>
    `;
  }

  render() {
    return html`
      <div class="registration-form-container">
        <div class="form-header">
          <h2>User Registration</h2>
          <p>Create your account using LocalStorage datasource</p>
        </div>

        <form @submit=${this.handleSubmit} @reset=${this.handleReset}>
          <!-- Email Field -->
          <div class="form-group ${this.errors.email ? 'has-error' : ''}">
            <label for="email">
              Email <span class="required">*</span>
            </label>
            <input
              type="email"
              id="email"
              .value=${this.formData.email}
              @input=${(e: Event) => this.handleInput('email', (e.target as HTMLInputElement).value)}
              placeholder="john.doe@example.com"
              ?disabled=${this.isSubmitting}
            />
            ${this.errors.email ? html`<span class="error-message">${this.errors.email}</span>` : ''}
          </div>

          <!-- Name Fields -->
          <div class="form-row">
            <div class="form-group ${this.errors.firstName ? 'has-error' : ''}">
              <label for="firstName">
                First Name <span class="required">*</span>
              </label>
              <input
                type="text"
                id="firstName"
                .value=${this.formData.firstName}
                @input=${(e: Event) => this.handleInput('firstName', (e.target as HTMLInputElement).value)}
                placeholder="John"
                ?disabled=${this.isSubmitting}
              />
              ${this.errors.firstName ? html`<span class="error-message">${this.errors.firstName}</span>` : ''}
            </div>

            <div class="form-group ${this.errors.lastName ? 'has-error' : ''}">
              <label for="lastName">
                Last Name <span class="required">*</span>
              </label>
              <input
                type="text"
                id="lastName"
                .value=${this.formData.lastName}
                @input=${(e: Event) => this.handleInput('lastName', (e.target as HTMLInputElement).value)}
                placeholder="Doe"
                ?disabled=${this.isSubmitting}
              />
              ${this.errors.lastName ? html`<span class="error-message">${this.errors.lastName}</span>` : ''}
            </div>
          </div>

          <!-- Password Field -->
          <div class="form-group ${this.errors.password ? 'has-error' : ''}">
            <label for="password">
              Password <span class="required">*</span>
            </label>
            <input
              type="password"
              id="password"
              .value=${this.formData.password}
              @input=${(e: Event) => this.handleInput('password', (e.target as HTMLInputElement).value)}
              placeholder="Minimum 8 characters"
              ?disabled=${this.isSubmitting}
            />
            ${this.errors.password ? html`<span class="error-message">${this.errors.password}</span>` : ''}
          </div>

          <!-- Optional Fields -->
          <div class="form-row">
            <div class="form-group">
              <label for="dateOfBirth">Date of Birth</label>
              <input
                type="date"
                id="dateOfBirth"
                .value=${this.formData.dateOfBirth || ''}
                @input=${(e: Event) => this.handleInput('dateOfBirth', (e.target as HTMLInputElement).value)}
                ?disabled=${this.isSubmitting}
              />
            </div>

            <div class="form-group ${this.errors.phoneNumber ? 'has-error' : ''}">
              <label for="phoneNumber">Phone Number</label>
              <input
                type="tel"
                id="phoneNumber"
                .value=${this.formData.phoneNumber || ''}
                @input=${(e: Event) => this.handleInput('phoneNumber', (e.target as HTMLInputElement).value)}
                placeholder="+1234567890"
                ?disabled=${this.isSubmitting}
              />
              ${this.errors.phoneNumber ? html`<span class="error-message">${this.errors.phoneNumber}</span>` : ''}
            </div>
          </div>

          <!-- Submit Message -->
          ${this.renderSubmitMessage()}

          <!-- Form Actions -->
          <div class="form-actions">
            <button type="reset" ?disabled=${this.isSubmitting}>
              Clear Form
            </button>
            <button type="submit" class="primary" ?disabled=${this.isSubmitting}>
              ${this.isSubmitting ? 'Registering...' : 'Register'}
            </button>
          </div>
        </form>

        <div class="form-footer">
          <p>💾 Data is stored in LocalStorage (check DevTools > Application > Local Storage)</p>
          <p>🧪 Open browser console and run: <code>await userRegistrationDemo.listUsers()</code></p>
        </div>
      </div>
    `;
  }
}
