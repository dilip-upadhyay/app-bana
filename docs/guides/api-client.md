# AppBana API Client & Interceptor System

A comprehensive, production-ready API client wrapper with interceptor support for AppBana Studio UI.

## 📦 What's Included

- **API Client** (`api-client.ts`) - Core HTTP client with interceptor support
- **Interceptor Manager** (`api-interceptor.ts`) - Manage request/response/error interceptors
- **Pre-built Interceptors** (`api-interceptors.ts`) - Common interceptors (auth, logging, retry, cache, etc.)
- **Service Layer** (`api-service.ts`) - High-level AppBana-specific API services
- **Setup Utilities** (`api-setup.ts`) - Easy configuration and initialization
- **Examples** (`api-examples.ts`) - Comprehensive usage examples

## 🚀 Quick Start

### 1. Initialize (Once in your app)

```typescript
// In your main entry file (e.g., index.ts)
import { setupApiClient } from './core/api-setup';

setupApiClient({
  enableLogging: true,  // Logs all requests in development
  enableRetry: true,    // Auto-retry failed requests
  onLoadingChange: (isLoading, activeRequests) => {
    // Update your global loading indicator
    console.log(`Loading: ${isLoading}, Active: ${activeRequests}`);
  },
  onError: (error) => {
    // Show toast/notification to user
    console.error('API Error:', error.message);
  }
});
```

### 2. Use in Components

```typescript
import { api } from './core';

// Schema operations
const schemas = await api.schema.list();
const schema = await api.schema.get('users');
await api.schema.save({ name: 'users', fields: [...] });

// Entity CRUD
const users = await api.entity.query('users', { 
  limit: 25, 
  offset: 0,
  q: 'search term',
  sort: '-createdAt'
});

const user = await api.entity.get('users', '123');
await api.entity.create('users', { name: 'John', email: 'john@example.com' });
await api.entity.update('users', '123', { name: 'Jane' });
await api.entity.delete('users', '123');

// Batch operations
await api.entity.batchInsert('users', [
  { name: 'User 1', email: 'user1@example.com' },
  { name: 'User 2', email: 'user2@example.com' }
]);
```

## 🔌 Interceptors

### Built-in Interceptors

```typescript
import { apiClient } from './core';
import { 
  authInterceptor,
  loggingInterceptor,
  cacheInterceptor,
  rateLimitInterceptor 
} from './core';

// Add authentication
apiClient.interceptor.use(
  authInterceptor(() => localStorage.getItem('appbana_token'))
);

// Add caching for GET requests
apiClient.interceptor.use(
  cacheInterceptor({ ttl: 60000, maxSize: 100 })
);

// Add rate limiting
apiClient.interceptor.use(
  rateLimitInterceptor({ maxRequests: 100, windowMs: 60000 })
);
```

### Create Custom Interceptor

```typescript
apiClient.interceptor.use({
  name: 'myCustomInterceptor',
  
  // Runs before request is sent
  onRequest: (config) => {
    console.log('Request:', config.url);
    config.headers = {
      ...config.headers,
      'X-Custom-Header': 'value'
    };
    return config; // or return null to abort request
  },
  
  // Runs after successful response
  onResponse: (response, data) => {
    console.log('Response:', response.status);
    return { ...data, timestamp: Date.now() }; // transform data
  },
  
  // Runs on error
  onError: (error) => {
    console.error('Error:', error.message);
    // Handle or transform error
  }
});
```

## 📚 API Reference

### High-Level Service Layer

#### Schema Service
```typescript
api.schema.list()                           // List all schemas
api.schema.get(name)                        // Get schema details
api.schema.save({ name, fields })           // Create/update schema
api.schema.preview({ name, fields })        // Preview changes
api.schema.delete(name, dropTable)          // Delete schema
api.schema.migrations(name)                 // Get migration history
```

#### Entity Service
```typescript
api.entity.query(entity, params)            // Query with filters
api.entity.get(entity, id)                  // Get by ID
api.entity.create(entity, data)             // Create
api.entity.update(entity, id, data)         // Update
api.entity.delete(entity, id)               // Delete
api.entity.batchInsert(entity, records)     // Batch insert
api.entity.batchUpdate(entity, records)     // Batch update
api.entity.batchDelete(entity, ids)         // Batch delete
```

#### Datasource Service
```typescript
api.datasource.list()                       // List all datasources
api.datasource.get(id)                      // Get datasource
api.datasource.create(datasource)           // Create datasource
api.datasource.update(id, datasource)       // Update datasource
api.datasource.delete(id)                   // Delete datasource
api.datasource.testConnection(datasource)   // Test connection
```

### Low-Level API Client

```typescript
import { apiClient } from './core';

// HTTP methods
await apiClient.get(url, params, config)
await apiClient.post(url, data, config)
await apiClient.put(url, data, config)
await apiClient.patch(url, data, config)
await apiClient.delete(url, params, config)

// Generic request
await apiClient.request(url, config)
```

## 🎯 Use Cases

### In Lit Components

```typescript
import { LitElement, html } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { api } from './core';

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
      const result = await api.entity.query('users', { limit: 25 });
      this.users = result.rows;
    } catch (e: any) {
      this.error = e.message;
    } finally {
      this.loading = false;
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
      <ul>
        ${this.users.map(user => html`
          <li>
            ${user.name}
            <button @click=${() => this.deleteUser(user.id)}>Delete</button>
          </li>
        `)}
      </ul>
    `;
  }
}
```

## 🔧 Advanced Features

### Authentication

```typescript
import { setAuthToken, clearAuth } from './core';

// Login
const response = await apiClient.post('/auth/login', {
  username: 'admin',
  password: 'password'
});

setAuthToken(response.token);

// All subsequent requests automatically include token

// Logout
clearAuth();
```

### Error Handling

```typescript
import type { ApiError } from './core';

try {
  await api.entity.get('users', '123');
} catch (error: any) {
  const apiError = error as ApiError;
  
  console.log('Status:', apiError.status);           // 404
  console.log('Message:', apiError.message);         // "HTTP 404: Not Found"
  console.log('Response data:', apiError.data);      // Server error details
  
  if (apiError.status === 404) {
    console.log('User not found');
  } else if (apiError.status === 401) {
    console.log('Unauthorized - redirect to login');
  }
}
```

### Parallel Requests

```typescript
const [schemas, users, datasources] = await Promise.all([
  api.schema.list(),
  api.entity.query('users'),
  api.datasource.list()
]);
```

### Request Cancellation

```typescript
const controller = new AbortController();

apiClient.request('/api/users', {
  signal: controller.signal
});

// Cancel after 5 seconds
setTimeout(() => controller.abort(), 5000);
```

## 📖 Documentation

- **Migration Guide:** `/docs/API_CLIENT_MIGRATION.md` - How to migrate from fetch
- **Examples:** `/src/core/api-examples.ts` - Comprehensive examples
- **Original API Docs:** `/docs/API_INTEGRATION.md` - Legacy fetch patterns

## 🧪 Testing

```typescript
// Mock the API client in tests
import { apiClient } from './core';

vi.spyOn(apiClient, 'get').mockResolvedValue({ rows: [], total: 0 });
```

## 🎨 Architecture

```
┌─────────────────────────────────────────┐
│         Your Component                   │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│     High-Level Service Layer (api)      │
│  (schema, entity, datasource, etc.)     │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│         API Client (apiClient)          │
│   (get, post, put, delete, request)     │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│      Interceptor Chain                  │
│  Request → Response → Error             │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│         Native Fetch API                │
└─────────────────────────────────────────┘
```

## 🚦 What Gets Added Automatically

When you use the API client, these features are automatic:

✅ Authentication token from localStorage  
✅ Request/response logging (dev mode)  
✅ Auto-retry on 5xx errors  
✅ Unique request IDs  
✅ Global error handling  
✅ Global loading state  
✅ Content-Type headers  
✅ URL encoding  
✅ JSON parsing  
✅ Query parameter building  
✅ Request timeouts (30s default)  

## 📝 License

Part of the AppBana project.

