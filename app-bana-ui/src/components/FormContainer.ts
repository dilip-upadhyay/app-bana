import { BaseElement } from '../core/BaseElement';
import { apiClient } from '../core/api-client';
import { registerComponent } from '../core/registry';
import { RuntimeContext } from '../runtime/RuntimeContext';
import { AuthService } from '../pages/auth/auth-service';

/**
 * StudioForm - A container that handles data submission for an entity.
 * 
 * Enhanced with Story 1.2 (CSRF), Story 2.1 (Session), and UX features:
 * - CSRF token fetching and auto-inclusion in POST/PUT/DELETE
 * - Session token inclusion from localStorage
 * - Client-side validation with real-time feedback
 * - Loading states during submission
 * - Field-level error display from backend validation
 * - Password field mapping to passwordHash
 * - Rate limit error handling
 * 
 * Props:
 * - entity: The name of the entity (e.g., "LoanApplication")
 * - action: Custom endpoint to post to (e.g. "/auth/login"). If set, entity is ignored/optional.
 * - recordId: Optional ID for editing an existing record. If omitted, creates new.
 * - redirectOnSuccess: generic path (client-side router) to go to after success.
 * - validateOnBlur: Enable client-side validation on field blur (default: true)
 * - showLoadingState: Show loading indicators during submission (default: true)
 */
export class FormContainer extends BaseElement {
    private csrfToken: string | null = null;
    private isSubmitting: boolean = false;
    private validationErrors: Record<string, string> = {};

    static get observedAttributes() {
        return ['entity', 'action', 'record-id', 'redirect-on-success', 'validate-on-blur'];
    }

    protected render(): string {
        // We render a generic slot. 
        // The form submission is handled by listening to buttons with type="submit".
        return `
      <form style="display: block;" onsubmit="return false;">
        <slot></slot>
        <div id="error-msg" style="color: red; margin-top: 10px; display: none;"></div>
      </form>
    `;
    }

    protected styles(): string {
        return `
      :host { display: block; }
    `;
    }

    async connectedCallback() {
        super.connectedCallback();
        this.addEventListener('click', this.handleClick.bind(this));

        // Fetch CSRF token for form security (Story 1.2)
        await this.fetchCsrfToken();

        // Setup client-side validation listeners (Story 1.4)
        if (this.getAttribute('validate-on-blur') !== 'false') {
            this.setupValidationListeners();
        }

        // Load data if recordId is present
        this.checkAndLoadRecord();
    }

    attributeChangedCallback(name: string, oldValue: string | null, newValue: string | null) {
        if (name === 'record-id' && newValue && newValue !== oldValue) {
            this.loadRecord(newValue);
        }
    }

    /**
     * Fetch CSRF token from backend (Story 1.2)
     */
    private async fetchCsrfToken(): Promise<void> {
        try {
            const response = await apiClient.get<{ token: string }>('/api/csrf/token');
            this.csrfToken = response.token;
            console.log('[FormContainer] CSRF token fetched');
        } catch (e) {
            console.error('[FormContainer] Failed to fetch CSRF token:', e);
            // Non-blocking: forms to /api/auth/* don't require CSRF
        }
    }

    /**
     * Setup client-side validation listeners (Story 1.4, 2.2)
     */
    private setupValidationListeners(): void {
        const inputs = this.querySelectorAll('appbana-input, appbana-select, appbana-textarea, input, select, textarea');
        inputs.forEach((el: any) => {
            el.addEventListener('blur', () => this.validateField(el));
            el.addEventListener('input', () => this.clearFieldError(el));
        });
    }

    /**
     * Validate individual field on blur (Story 2.2)
     */
    private validateField(el: any): void {
        const name = el.getAttribute('name') || el.name;
        if (!name) return;

        const required = el.getAttribute('required') !== null || el.required;
        const value = el.getAttribute('value') || el.value || '';
        const type = el.getAttribute('type') || el.type || 'text';

        // Clear previous error
        delete this.validationErrors[name];

        // Required validation
        if (required && !value.trim()) {
            this.validationErrors[name] = 'This field is required';
            this.showFieldError(el, this.validationErrors[name]);
            return;
        }

        // Email validation
        if (type === 'email' && value) {
            const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
            if (!emailRegex.test(value)) {
                this.validationErrors[name] = 'Invalid email format';
                this.showFieldError(el, this.validationErrors[name]);
                return;
            }
        }

        // Password strength validation (Story 1.1.4)
        if (name === 'password' && value) {
            if (value.length < 8) {
                this.validationErrors[name] = 'Password must be at least 8 characters';
                this.showFieldError(el, this.validationErrors[name]);
                return;
            }
            if (!/[a-zA-Z]/.test(value) || !/\d/.test(value)) {
                this.validationErrors[name] = 'Password must contain letters and numbers';
                this.showFieldError(el, this.validationErrors[name]);
                return;
            }
        }

        // Confirm password matching (Story 1.1.5)
        if (name === 'confirmPassword' && value) {
            const passwordInput = this.querySelector('[name="password"]') as any;
            const password = passwordInput?.getAttribute('value') || passwordInput?.value || '';
            if (value !== password) {
                this.validationErrors[name] = 'Passwords must match';
                this.showFieldError(el, this.validationErrors[name]);
                return;
            }
        }

        // Clear error if validation passed
        this.clearFieldError(el);
    }

    /**
     * Show field-level error (Story 1.4)
     */
    private showFieldError(el: any, message: string): void {
        // Set aria-invalid for accessibility (Story 1.5)
        el.setAttribute('aria-invalid', 'true');

        // Add error class if element supports it
        if (el.classList) {
            el.classList.add('error');
        }

        // Find or create error message element
        let errorEl = el.nextElementSibling;
        if (!errorEl || !errorEl.classList.contains('field-error')) {
            errorEl = document.createElement('div');
            errorEl.className = 'field-error';
            errorEl.setAttribute('role', 'alert'); // Story 1.5.6
            errorEl.style.color = 'red';
            errorEl.style.fontSize = '0.875rem';
            errorEl.style.marginTop = '0.25rem';
            el.parentNode?.insertBefore(errorEl, el.nextSibling);
        }
        errorEl.textContent = message;
    }

    /**
     * Clear field error (Story 1.4.4)
     */
    private clearFieldError(el: any): void {
        const name = el.getAttribute('name') || el.name;
        if (name) {
            delete this.validationErrors[name];
        }

        el.removeAttribute('aria-invalid');
        if (el.classList) {
            el.classList.remove('error');
        }

        const errorEl = el.nextElementSibling;
        if (errorEl && errorEl.classList.contains('field-error')) {
            errorEl.remove();
        }
    }

    private checkAndLoadRecord() {
        const recordId = this.getAttribute('record-id') || this.getAttribute('recordId');
        if (recordId && recordId !== 'undefined' && recordId !== 'null') {
            this.loadRecord(recordId);
        }
    }

    /**
     * Get runtime context (tenant, app, env) for API calls
     * Uses safe fallback for development/testing
     */
    private getRuntimeContext() {
        try {
            return RuntimeContext.getInstance().getContext();
        } catch (e) {
            // Fallback for development/testing when runtime context not available
            console.warn('[FormContainer] Runtime context not available, using fallback values');
            return { tenantId: AuthService.getUser()?.tenantId || 'default', appId: 'test-app', env: 'dev' };
        }
    }

    private async loadRecord(id: string) {
        const entity = this.getAttribute('entity');
        if (!entity) return;

        try {
            const { tenantId, appId } = this.getRuntimeContext();
            const data = await apiClient.get<any>(
                `/appbana-studio/${tenantId}/apps/${appId}/${entity}/${id}`
            );
            this.populateForm(data);
        } catch (e) {
            console.error('Failed to load record', e);
            this.showError('Failed to load record');
        }
    }

    private populateForm(data: any) {
        // Create a normalized map of data keys for case-insensitive lookup
        const normalizedData: Record<string, any> = {};
        if (data) {
            Object.keys(data).forEach(key => {
                normalizedData[key.toLowerCase()] = data[key];
            });
        }

        // Helper to find value case-insensitively
        const getValue = (name: string) => {
            if (!name) return undefined;
            // Try exact match first
            if (data[name] !== undefined) return data[name];
            // Try lowercase match
            return normalizedData[name.toLowerCase()];
        };

        // Find all appbana-input, appbana-select, etc.
        const inputs = this.querySelectorAll('appbana-input, appbana-select, appbana-textarea');
        inputs.forEach((el: any) => {
            const name = el.getAttribute('name');
            const val = getValue(name);
            if (name && val !== undefined) {
                el.setAttribute('value', String(val));
                // If it supports .value property setting
                if (el.value !== undefined) el.value = val;
            }
        });

        // Also populate standard inputs if any
        const stdInputs = this.querySelectorAll('input, select, textarea');
        stdInputs.forEach((el: any) => {
            const val = getValue(el.name);
            if (el.name && val !== undefined) {
                el.value = val;
            }
        });
    }

    private async handleClick(e: Event) {
        const target = e.target as HTMLElement;
        // Walk up to find the button if clicked on icon inside
        const btn = target.closest('appbana-button') || target.closest('studio-button') || target.closest('button');
        if (!btn) return;

        // Check for field updates (e.g. Approve/Reject buttons setting status)
        const updateField = btn.getAttribute('update-field');
        const updateValue = btn.getAttribute('update-value');

        if (updateField && updateValue) {
            // Find the input and update it
            const input = this.querySelector(`[name="${updateField}"]`) as any;
            if (input) {
                if (input.value !== undefined) input.value = updateValue;
                else input.setAttribute('value', updateValue);
            } else {
                // Create hidden input if it doesn't exist
                // (Though ideally it should exist).
            }
        }

        // Check if it's a submit button
        const type = btn.getAttribute('type');
        if (type === 'submit') {
            e.preventDefault();
            await this.handleSubmit();
        }
    }

    private async handleSubmit() {
        // Prevent double submission (Story 2.1.5)
        if (this.isSubmitting) {
            console.warn('[FormContainer] Form already submitting, ignoring duplicate submission');
            return;
        }

        const entity = this.getAttribute('entity');
        const action = this.getAttribute('action');
        const recordId = this.getAttribute('record-id') || this.getAttribute('recordId');
        const redirect = this.getAttribute('redirect-on-success') || this.getAttribute('redirectOnSuccess');

        if (!entity && !action) {
            this.showError('Configuration Error: No entity or action specified for form.');
            return;
        }

        // Validate all fields before submission (Story 2.2)
        const inputs = this.querySelectorAll('appbana-input, appbana-select, appbana-textarea, input, select, textarea');
        inputs.forEach((el: any) => this.validateField(el));

        if (Object.keys(this.validationErrors).length > 0) {
            this.showError('Please fix validation errors before submitting');
            // Focus first error field (Story 1.4.3)
            const firstErrorField = Array.from(inputs).find((el: any) => {
                const name = el.getAttribute('name') || el.name;
                return name && this.validationErrors[name];
            }) as any;
            if (firstErrorField && firstErrorField.focus) {
                firstErrorField.focus();
            }
            return;
        }

        // Set loading state (Story 2.1)
        this.isSubmitting = true;
        this.setLoadingState(true);
        this.showError(''); // Clear previous errors

        // Collect data
        const formData: Record<string, any> = {};

        inputs.forEach((el: any) => {
            const name = el.getAttribute('name') || el.name;
            const val = el.getAttribute('value') || el.value;
            if (name) {
                // Exclude confirmPassword from entity (Story 1.1.3)
                if (name === 'confirmPassword') {
                    return; // Skip confirmPassword, not sent to backend
                }
                formData[name] = val;
            }
        });

        // Password field mapping: password → passwordHash (Story 1.1.2)
        // Backend PasswordService will hash it, but we need to rename the field
        if (formData.password && entity) {
            // For entity operations, map to passwordHash
            // For auth endpoints (/api/auth/login, /api/auth/register), keep as "password"
            if (!action || !action.includes('/auth/')) {
                formData.passwordHash = formData.password;
                delete formData.password;
            }
        }

        try {
            // Get session token from localStorage (Story 2.1)
            const sessionToken = localStorage.getItem('appbana_token');

            // Prepare headers with CSRF and Session tokens
            const headers: Record<string, string> = {};

            // Add CSRF token for non-GET requests (Story 1.2)
            if (this.csrfToken) {
                headers['X-CSRF-Token'] = this.csrfToken;
            }

            // Add Session token (Story 2.1)
            if (sessionToken) {
                headers['X-Session-Token'] = sessionToken;
            }

            // Make request with security headers
            if (action) {
                // Post to custom action endpoint (e.g., /api/auth/login)
                // Custom actions don't need tenant/app context
                await apiClient.post(action, formData, { headers });
            } else if (recordId) {
                // Update - app-scoped entity route
                const { tenantId, appId } = this.getRuntimeContext();
                await apiClient.put(
                    `/appbana-studio/${tenantId}/apps/${appId}/${entity}/${recordId}`,
                    formData,
                    { headers }
                );
            } else {
                // Create - app-scoped entity route
                const { tenantId, appId } = this.getRuntimeContext();
                await apiClient.post(
                    `/appbana-studio/${tenantId}/apps/${appId}/${entity}`,
                    formData,
                    { headers }
                );
            }

            // Success!
            alert('Saved successfully!');
            if (redirect) {
                // Use global navigation if available or location assignment
                // Since this is runtime, we might use history API if we are in SPA mode
                // Simplest:
                const url = new URL(window.location.href);
                // If redirect starts with /, it's a page path.
                if (redirect.startsWith('/')) {
                    // Check if we are in "runtime state" mode url?state=...
                    // If so, we need to update the state to change pageId.
                    // But navigation is complex.
                    // Studio Runtime Shim:
                    // If window['navigate'] exists (injected by runtime), use it.
                    if ((window as any).navigate) {
                        (window as any).navigate(redirect);
                    } else {
                        // Fallback: dispatch custom event in case window.navigate is not bound yet or we are in a shadow root issue
                        this.dispatchEvent(new CustomEvent('navigate', {
                            bubbles: true,
                            composed: true,
                            detail: { path: redirect }
                        }));
                    }
                    // Do NOT use window.location.href here as it breaks the preview state.
                } else {
                    window.location.href = redirect;
                }
            }
        } catch (e: any) {
            console.error('Submit error:', e);

            // Handle validation errors from backend (Story 1.4)
            if (e.response && e.response.validationErrors) {
                const errors = e.response.validationErrors;
                Object.keys(errors).forEach(fieldName => {
                    const input = this.querySelector(`[name="${fieldName}"]`) as any;
                    if (input) {
                        this.validationErrors[fieldName] = errors[fieldName];
                        this.showFieldError(input, errors[fieldName]);
                    }
                });
                this.showError('Please fix the errors above');

                // Focus first error field
                const firstErrorField = this.querySelector(`[name="${Object.keys(errors)[0]}"]`) as any;
                if (firstErrorField && firstErrorField.focus) {
                    firstErrorField.focus();
                }
            }
            // Handle rate limit errors (Story 1.3)
            else if (e.message && e.message.includes('rate limit')) {
                this.showError('Too many requests. Please wait a moment and try again.');
            }
            // Handle session errors (Story 2.1)
            else if (e.status === 401 || e.message.includes('unauthorized')) {
                this.showError('Session expired. Please log in again.');
                setTimeout(() => {
                    window.location.href = '/login';
                }, 2000);
            }
            // Generic error
            else {
                this.showError(`Error: ${e.message || 'Unknown error'}`);
            }
        } finally {
            // Clear loading state (Story 2.1.3, 2.1.4)
            this.isSubmitting = false;
            this.setLoadingState(false);
        }
    }

    /**
     * Set loading state on submit button (Story 2.1)
     */
    private setLoadingState(loading: boolean): void {
        const buttons = this.querySelectorAll('appbana-button[type="submit"], studio-button[type="submit"], button[type="submit"]');
        buttons.forEach((btn: any) => {
            if (loading) {
                btn.setAttribute('disabled', 'true');
                const originalText = btn.textContent || btn.getAttribute('text') || 'Submit';
                btn.setAttribute('data-original-text', originalText);
                if (btn.setAttribute && btn.getAttribute('text') !== undefined) {
                    btn.setAttribute('text', 'Submitting...');
                } else {
                    btn.textContent = 'Submitting...';
                }
            } else {
                btn.removeAttribute('disabled');
                const originalText = btn.getAttribute('data-original-text');
                if (originalText) {
                    if (btn.setAttribute && btn.getAttribute('text') !== undefined) {
                        btn.setAttribute('text', originalText);
                    } else {
                        btn.textContent = originalText;
                    }
                }
            }
        });
    }

    private showError(msg: string) {
        const el = this.shadowRoot?.getElementById('error-msg');
        if (el) {
            el.textContent = msg;
            el.style.display = msg ? 'block' : 'none';
        }
    }
}

if (!customElements.get('appbana-form')) {
    customElements.define('appbana-form', FormContainer);
}
registerComponent('form', FormContainer, 'appbana-form');
