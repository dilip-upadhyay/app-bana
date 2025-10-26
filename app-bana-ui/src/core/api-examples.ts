/**
 * API Client Usage Examples for AppBana
 *
 * This file demonstrates how to use the new API wrapper and interceptors
 */

import { apiClient } from './api-client.ts';
import { api } from './api-service.ts';
import { setupApiClient, setAuthToken } from './api-setup.ts';
import {
  authInterceptor,
  loggingInterceptor,
  cacheInterceptor,
  rateLimitInterceptor,
} from './api-interceptors.ts';

// ============================================================================
// SETUP - Call this once when your app initializes
// ============================================================================

/**
 * Basic setup - automatically configures common interceptors
 */
function initializeApp() {
  setupApiClient({
    enableLogging: true, // Enable in development
    enableRetry: true,   // Retry failed requests
    onLoadingChange: (isLoading, activeRequests) => {
      console.log(`Loading: ${isLoading}, Active: ${activeRequests}`);
      // Update your global loading state here
    },
    onError: (error) => {
      console.error('Global error handler:', error);
      // Show toast/notification to user
    },
  });
}

// ============================================================================
// EXAMPLE 1: Using High-Level Service Layer (Recommended)
// ============================================================================

async function exampleServiceLayer() {
  // Schema operations
  const schemas = await api.schema.list();
  const schema = await api.schema.get('users');
  await api.schema.save({ name: 'users', fields: [] });
  await api.schema.delete('users', true);

  // Entity CRUD operations
  const users = await api.entity.query('users', {
    limit: 25,
    offset: 0,
    q: 'john',
    sort: '-createdAt',
  });

  const user = await api.entity.get('users', '123');
  await api.entity.create('users', { name: 'John Doe', email: 'john@example.com' });
  await api.entity.update('users', '123', { name: 'Jane Doe' });
  await api.entity.delete('users', '123');

  // Batch operations
  await api.entity.batchInsert('users', [
    { name: 'User 1', email: 'user1@example.com' },
    { name: 'User 2', email: 'user2@example.com' },
  ]);

  // Datasource operations
  const datasources = await api.datasource.list();
  const connection = await api.datasource.testConnection({
    type: 'postgres',
    host: 'localhost',
    port: 5432,
  });

  // Audit logs
  const auditLogs = await api.auditLog.query({
    entity: 'users',
    action: 'CREATE',
    limit: 100,
  });
}

// ============================================================================
// EXAMPLE 2: Using Low-Level API Client Directly
// ============================================================================

async function exampleLowLevelClient() {
  // GET request
  const data = await apiClient.get('/api/users', { limit: 25, offset: 0 });

  // POST request
  const newUser = await apiClient.post('/api/users', {
    name: 'John Doe',
    email: 'john@example.com',
  });

  // PUT request
  const updated = await apiClient.put('/api/users/123', {
    name: 'Jane Doe',
  });

  // DELETE request
  await apiClient.delete('/api/users/123');

  // Custom request with full control
  const result = await apiClient.request('/api/custom', {
    method: 'POST',
    headers: {
      'X-Custom-Header': 'value',
    },
    body: JSON.stringify({ data: 'custom' }),
  });
}

// ============================================================================
// EXAMPLE 3: Adding Custom Interceptors
// ============================================================================

function exampleCustomInterceptors() {
  // Add logging interceptor
  apiClient.interceptor.use(loggingInterceptor({
    logRequests: true,
    logResponses: true,
    logErrors: true,
  }));

  // Add caching for GET requests
  apiClient.interceptor.use(cacheInterceptor({
    ttl: 60000, // 60 seconds
    maxSize: 100,
  }));

  // Add rate limiting
  apiClient.interceptor.use(rateLimitInterceptor({
    maxRequests: 100,
    windowMs: 60000,
  }));

  // Add custom interceptor
  const unsubscribe = apiClient.interceptor.use({
    name: 'myCustomInterceptor',
    onRequest: (config) => {
      console.log('Before request:', config.url);
      // Modify request config
      config.headers = {
        ...config.headers,
        'X-My-Header': 'custom-value',
      };
      return config;
    },
    onResponse: (response, data) => {
      console.log('After response:', response.status);
      // Transform response data
      return {
        ...data,
        timestamp: Date.now(),
      };
    },
    onError: (error) => {
      console.error('Request failed:', error.message);
      // Handle or transform error
    },
  });

  // Remove interceptor later
  unsubscribe();
}

// ============================================================================
// EXAMPLE 4: Using in Lit Components
// ============================================================================

import { LitElement, html } from 'lit';
import { customElement, state } from 'lit/decorators.js';

@customElement('user-list')
class UserList extends LitElement {
  @state() private users: any[] = [];
  @state() private loading = false;
  @state() private error: string | null = null;

  async connectedCallback() {
    super.connectedCallback();
    await this.loadUsers();
  }

  private async loadUsers() {
    this.loading = true;
    this.error = null;

    try {
      // Using service layer
      const result = await api.entity.query('users', {
        limit: 25,
        offset: 0,
      });
      this.users = result.rows;
    } catch (e: any) {
      this.error = e.message;
    } finally {
      this.loading = false;
    }
  }

  private async createUser() {
    try {
      await api.entity.create('users', {
        name: 'New User',
        email: 'new@example.com',
      });
      await this.loadUsers(); // Reload list
    } catch (e: any) {
      this.error = e.message;
    }
  }

  private async deleteUser(id: string) {
    try {
      await api.entity.delete('users', id);
      await this.loadUsers();
    } catch (e: any) {
      this.error = e.message;
    }
  }

  render() {
    if (this.loading) return html`<div>Loading...</div>`;
    if (this.error) return html`<div class="error">${this.error}</div>`;

    return html`
      <div>
        <h2>Users (${this.users.length})</h2>
        <button @click=${this.createUser}>Add User</button>
        <ul>
          ${this.users.map(user => html`
            <li>
              ${user.name} - ${user.email}
              <button @click=${() => this.deleteUser(user.id)}>Delete</button>
            </li>
          `)}
        </ul>
      </div>
    `;
  }
}

// ============================================================================
// EXAMPLE 5: Authentication
// ============================================================================

async function exampleAuth() {
  // Login
  const response = await apiClient.post('/auth/login', {
    username: 'admin',
    password: 'password',
  });

  // Save token
  setAuthToken(response.token);

  // All subsequent requests will include the token automatically
  const data = await api.entity.query('users');

  // Logout
  setAuthToken(null);
}

// ============================================================================
// EXAMPLE 6: Error Handling Patterns
// ============================================================================

async function exampleErrorHandling() {
  // Pattern 1: Try-catch
  try {
    const user = await api.entity.get('users', '123');
    console.log(user);
  } catch (error: any) {
    if (error.status === 404) {
      console.log('User not found');
    } else if (error.status === 401) {
      console.log('Unauthorized');
    } else {
      console.error('Unexpected error:', error);
    }
  }

  // Pattern 2: Using error data
  try {
    await api.entity.create('users', { name: 'Invalid' });
  } catch (error: any) {
    console.log('Status:', error.status);
    console.log('Message:', error.message);
    console.log('Response data:', error.data);
  }

  // Pattern 3: Global error handler (already set in setup)
  // All errors will be handled by the global handler
  // unless you catch them locally
}

// ============================================================================
// EXAMPLE 7: Advanced Patterns
// ============================================================================

async function exampleAdvancedPatterns() {
  // Parallel requests
  const [schemas, users, datasources] = await Promise.all([
    api.schema.list(),
    api.entity.query('users'),
    api.datasource.list(),
  ]);

  // Sequential dependent requests
  const user = await api.entity.get('users', '123');
  const orders = await api.entity.query('orders', {
    filter: `userId:${user.id}`,
  });

  // Conditional requests
  const shouldFetch = true;
  const data = shouldFetch ? await api.entity.query('users') : [];

  // Request with custom headers
  const result = await apiClient.get('/api/users', undefined, {
    headers: {
      'X-Custom-Header': 'value',
    },
  });

  // Skip interceptors for specific request
  const rawData = await apiClient.request('/api/users', {
    skipInterceptors: true,
  });
}

// ============================================================================
// EXAMPLE 8: Migrating from Old Fetch Code
// ============================================================================

// OLD CODE (using fetch directly)
async function oldWay() {
  const response = await fetch('/api/users?limit=25', {
    headers: {
      'X-AppBana-Token': localStorage.getItem('appbana_token') || '',
    },
  });
  const users = await response.json();
}

// NEW CODE (using api wrapper)
async function newWay() {
  // Auth token is added automatically by interceptor
  const result = await api.entity.query('users', { limit: 25 });
  const users = result.rows;
}

// OLD CODE (batch insert)
async function oldBatchInsert() {
  const response = await fetch('/api/users/batch', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-AppBana-Token': localStorage.getItem('appbana_token') || '',
    },
    body: JSON.stringify([{ name: 'User 1' }]),
  });
  const result = await response.json();
}

// NEW CODE (batch insert)
async function newBatchInsert() {
  const result = await api.entity.batchInsert('users', [
    { name: 'User 1' },
  ]);
}

export {
  initializeApp,
  exampleServiceLayer,
  exampleLowLevelClient,
  exampleCustomInterceptors,
  exampleAuth,
  exampleErrorHandling,
  exampleAdvancedPatterns,
};

