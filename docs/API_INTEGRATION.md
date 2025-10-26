# Backend API Integration in AppBana Studio UI

This document describes all the ways to call backend APIs from AppBana Studio UI components.

## Current Implementation (Phase A/B)

### 1. Native Fetch API (Primary Method) ✅

**What it is:** The standard browser Fetch API for making HTTP requests.

**All API calls in the codebase use this pattern.**

#### Basic GET Request

```typescript
private async loadData() {
  try {
    const response = await fetch('/api/endpoint');
    if (response.ok) {
      const data = await response.json();
      this.data = data;
    } else {
      console.error('Failed to load:', response.status);
    }
  } catch (error) {
    console.error('Network error:', error);
  }
}
```

**Example from EntityExplorer (GET with headers):**
```typescript
private async loadEntities() {
  this.loadingEntities = true;
  try {
    const r = await fetch('/schema', { 
      headers: this.authHeaders() 
    });
    if (r.ok) {
      const names: string[] = await r.json();
      this.entities = names;
    }
  } catch (e) {
    console.error(e);
  } finally {
    this.loadingEntities = false;
  }
}

private authHeaders(): Record<string, string> {
  return this.authToken ? { 'X-AppBana-Token': this.authToken } : {};
}
```

#### GET Request with Query Parameters

**Example from EntityExplorer:**
```typescript
private buildQueryUrl(): string {
  const { entity, limit, offset, q, fields, sort, count } = this.query;
  if (!entity) return '';
  
  const params = new URLSearchParams();
  if (limit != null) params.set('limit', String(limit));
  if (offset) params.set('offset', String(offset));
  if (q.trim()) params.set('q', q.trim());
  if (fields.trim()) params.set('fields', fields.trim());
  if (sort.trim()) params.set('sort', sort.trim());
  
  const filterParam = this.buildFilterParam();
  if (filterParam) params.set('filter', filterParam);
  if (count) params.set('count', 'true');
  
  return `/api/${encodeURIComponent(entity)}?${params.toString()}`;
}

private async runQuery() {
  this.loadingData = true;
  try {
    const url = this.buildQueryUrl();
    const r = await fetch(url, { 
      headers: { 
        'Accept': 'application/json', 
        ...this.authHeaders() 
      } 
    });
    const text = await r.text();
    this.result = JSON.parse(text);
  } catch (e: any) {
    this.result = { error: e.message };
  } finally {
    this.loadingData = false;
  }
}
```

#### POST Request with JSON Body

**Example from EntityExplorer (Batch Insert):**
```typescript
private async doBatchInsert() {
  if (!this.query.entity) return;
  
  this.batchError = null;
  this.batchResult = null;
  
  let parsed: any;
  try {
    parsed = JSON.parse(this.batchInput);
    if (!Array.isArray(parsed)) {
      throw new Error('JSON must be an array');
    }
  } catch (e: any) {
    this.batchError = 'Parse error: ' + e.message;
    return;
  }
  
  try {
    const r = await fetch(`/api/${encodeURIComponent(this.query.entity)}/batch`, {
      method: 'POST',
      headers: { 
        'Content-Type': 'application/json', 
        ...this.authHeaders() 
      },
      body: JSON.stringify(parsed)
    });
    
    const body = await r.text();
    this.batchResult = JSON.parse(body);
    
    if (!r.ok) {
      this.batchError = 'HTTP ' + r.status;
    }
  } catch (e: any) {
    this.batchError = e.message;
  }
}
```

**Example from SchemaBuilder (Create/Update Schema):**
```typescript
private async saveSchema() {
  const payload = { 
    name: this.schemaName, 
    fields: this.fields 
  };
  
  try {
    const r = await fetch('/schema', { 
      method: 'POST', 
      headers: { 'Content-Type': 'application/json' }, 
      body: JSON.stringify(payload) 
    });
    
    if (r.ok) {
      const result = await r.json();
      console.log('Schema saved:', result);
    }
  } catch (error) {
    console.error('Save failed:', error);
  }
}
```

#### POST Request with Preview Mode

**Example from SchemaBuilder:**
```typescript
private async previewSchema() {
  const payload = { 
    name: this.schemaName, 
    fields: this.fields 
  };
  
  try {
    const r = await fetch('/schema?preview=true', { 
      method: 'POST', 
      headers: { 'Content-Type': 'application/json' }, 
      body: JSON.stringify(payload) 
    });
    
    if (r.ok) {
      const preview = await r.json();
      this.previewResult = preview;
    }
  } catch (error) {
    console.error('Preview failed:', error);
  }
}
```

#### DELETE Request

**Example from SchemaBuilder:**
```typescript
private async deleteSchema() {
  try {
    const r = await fetch(
      `/schema/${encodeURIComponent(this.selectedSchemaName)}?dropTable=${this.deleteDropTable}`, 
      { method: 'DELETE' }
    );
    
    if (r.ok) {
      console.log('Schema deleted');
      await this.loadSchemas();
    }
  } catch (error) {
    console.error('Delete failed:', error);
  }
}
```

### 2. Common Patterns Used in Codebase

#### Pattern 1: Loading State Management

```typescript
@state() private isLoading = false;
@state() private error: string | null = null;
@state() private data: any = null;

private async fetchData() {
  this.isLoading = true;
  this.error = null;
  
  try {
    const response = await fetch('/api/data');
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }
    this.data = await response.json();
  } catch (e: any) {
    this.error = e.message;
  } finally {
    this.isLoading = false;
  }
}

render() {
  return html`
    ${this.isLoading ? html`<div>Loading...</div>` : null}
    ${this.error ? html`<div class="error">${this.error}</div>` : null}
    ${this.data ? html`<div>${JSON.stringify(this.data)}</div>` : null}
  `;
}
```

#### Pattern 2: Authentication Headers

```typescript
private authToken: string = '';

connectedCallback() {
  super.connectedCallback();
  this.authToken = localStorage.getItem('appbana_token') || '';
}

private authHeaders(): Record<string, string> {
  return this.authToken ? { 'X-AppBana-Token': this.authToken } : {};
}

private async authenticatedRequest(url: string, options: RequestInit = {}) {
  return fetch(url, {
    ...options,
    headers: {
      ...options.headers,
      ...this.authHeaders()
    }
  });
}
```

#### Pattern 3: Response Parsing with Fallback

```typescript
private async fetchWithFallback(url: string) {
  try {
    const response = await fetch(url);
    const text = await response.text();
    
    try {
      return JSON.parse(text);
    } catch {
      return { raw: text }; // Return raw text if not JSON
    }
  } catch (e: any) {
    return { error: e.message };
  }
}
```

#### Pattern 4: URL Building with URLSearchParams

```typescript
private buildUrl(base: string, params: Record<string, any>): string {
  const searchParams = new URLSearchParams();
  
  Object.entries(params).forEach(([key, value]) => {
    if (value !== null && value !== undefined && value !== '') {
      searchParams.set(key, String(value));
    }
  });
  
  const query = searchParams.toString();
  return query ? `${base}?${query}` : base;
}

// Usage
const url = this.buildUrl('/api/users', {
  limit: 25,
  offset: 0,
  status: 'ACTIVE'
});
```

#### Pattern 5: Error Boundary with Try-Catch

```typescript
private async safeApiCall<T>(
  apiCall: () => Promise<T>,
  fallbackValue: T
): Promise<T> {
  try {
    return await apiCall();
  } catch (error) {
    console.error('API call failed:', error);
    return fallbackValue;
  }
}

// Usage
const users = await this.safeApiCall(
  () => fetch('/api/users').then(r => r.json()),
  []
);
```

### 3. API Endpoints Used in Codebase

#### Schema Management APIs
```typescript
// List all schemas
GET /schema
// Response: string[] - Array of schema names

// Get schema summaries
GET /schema/summaries
// Response: Array<{ name: string, fieldCount: number }>

// Get specific schema
GET /schema/{name}
// Response: Schema object

// Create/update schema
POST /schema
// Body: { name: string, fields: Field[] }

// Preview schema changes
POST /schema?preview=true
// Body: { name: string, fields: Field[] }
// Response: { ddl: string[], warnings: string[] }

// Delete schema
DELETE /schema/{name}?dropTable=true
// Response: { success: boolean }

// Get migration history
GET /schema/{name}/migrations
// Response: Array<Migration>
```

#### Entity CRUD APIs
```typescript
// Query entities
GET /api/{entity}?limit=25&offset=0&q=search&fields=id,name&sort=-createdAt&filter=status:ACTIVE
// Response: { rows: any[], total: number, query?: string }

// Batch insert
POST /api/{entity}/batch
// Body: Array<Record>
// Response: { inserted: number }
```

#### Datasource APIs
```typescript
// List datasources
GET /ui/datasource/list
// Response: Array<Datasource>
```

### 4. Advanced Patterns

#### Debounced API Calls

```typescript
private debounceTimer?: number;

private debouncedSearch(query: string, delay: number = 300) {
  clearTimeout(this.debounceTimer);
  this.debounceTimer = setTimeout(() => {
    this.performSearch(query);
  }, delay) as any;
}

private async performSearch(query: string) {
  const response = await fetch(`/api/search?q=${encodeURIComponent(query)}`);
  this.results = await response.json();
}
```

#### Cancellable Requests with AbortController

```typescript
private abortController?: AbortController;

private async fetchWithCancel(url: string) {
  // Cancel previous request
  if (this.abortController) {
    this.abortController.abort();
  }
  
  // Create new controller
  this.abortController = new AbortController();
  
  try {
    const response = await fetch(url, {
      signal: this.abortController.signal
    });
    return await response.json();
  } catch (e: any) {
    if (e.name === 'AbortError') {
      console.log('Request cancelled');
    } else {
      throw e;
    }
  }
}

disconnectedCallback() {
  super.disconnectedCallback();
  // Cancel any pending requests when component unmounts
  if (this.abortController) {
    this.abortController.abort();
  }
}
```

#### Retry Logic

```typescript
private async fetchWithRetry(
  url: string, 
  options: RequestInit = {}, 
  maxRetries: number = 3
): Promise<Response> {
  for (let i = 0; i < maxRetries; i++) {
    try {
      const response = await fetch(url, options);
      if (response.ok) {
        return response;
      }
      // Don't retry client errors (4xx)
      if (response.status >= 400 && response.status < 500) {
        throw new Error(`HTTP ${response.status}`);
      }
    } catch (error) {
      if (i === maxRetries - 1) throw error;
      // Wait before retry (exponential backoff)
      await new Promise(resolve => setTimeout(resolve, Math.pow(2, i) * 1000));
    }
  }
  throw new Error('Max retries exceeded');
}
```

#### Parallel Requests

```typescript
private async loadAllData() {
  this.isLoading = true;
  
  try {
    const [schemas, datasources, entities] = await Promise.all([
      fetch('/schema').then(r => r.json()),
      fetch('/ui/datasource/list').then(r => r.json()),
      fetch('/api/users').then(r => r.json())
    ]);
    
    this.schemas = schemas;
    this.datasources = datasources;
    this.entities = entities;
  } catch (error) {
    console.error('Failed to load data:', error);
  } finally {
    this.isLoading = false;
  }
}
```

#### Sequential Dependent Requests

```typescript
private async loadUserAndOrders(userId: string) {
  try {
    // First, get user
    const userResponse = await fetch(`/api/users/${userId}`);
    const user = await userResponse.json();
    
    // Then, get user's orders
    const ordersResponse = await fetch(`/api/orders?userId=${userId}`);
    const orders = await ordersResponse.json();
    
    return { user, orders };
  } catch (error) {
    console.error('Failed to load user data:', error);
    return null;
  }
}
```

## NOT Currently Implemented (Planned)

### 5. API Service Layer (Phase C)

**Planned:** Centralized API service with TypeScript interfaces.

```typescript
// services/api.service.ts
export class ApiService {
  private baseUrl = '';
  private authToken: string | null = null;
  
  setAuthToken(token: string) {
    this.authToken = token;
  }
  
  private async request<T>(
    endpoint: string, 
    options: RequestInit = {}
  ): Promise<T> {
    const headers = {
      'Content-Type': 'application/json',
      ...(this.authToken && { 'X-AppBana-Token': this.authToken }),
      ...options.headers
    };
    
    const response = await fetch(`${this.baseUrl}${endpoint}`, {
      ...options,
      headers
    });
    
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }
    
    return response.json();
  }
  
  async get<T>(endpoint: string): Promise<T> {
    return this.request<T>(endpoint);
  }
  
  async post<T>(endpoint: string, data: any): Promise<T> {
    return this.request<T>(endpoint, {
      method: 'POST',
      body: JSON.stringify(data)
    });
  }
  
  async put<T>(endpoint: string, data: any): Promise<T> {
    return this.request<T>(endpoint, {
      method: 'PUT',
      body: JSON.stringify(data)
    });
  }
  
  async delete<T>(endpoint: string): Promise<T> {
    return this.request<T>(endpoint, { method: 'DELETE' });
  }
}

export const apiService = new ApiService();
```

### 6. GraphQL Support (Future)

**Planned:** For Phase E when complex data requirements emerge.

```typescript
private async graphqlQuery(query: string, variables?: any) {
  const response = await fetch('/graphql', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ query, variables })
  });
  
  const result = await response.json();
  if (result.errors) {
    throw new Error(result.errors[0].message);
  }
  return result.data;
}
```

### 7. WebSocket for Real-time (Phase E)

**Planned:** Real-time data subscriptions.

```typescript
private ws?: WebSocket;

private connectWebSocket(channel: string) {
  this.ws = new WebSocket(`ws://localhost:8080/ws`);
  
  this.ws.onopen = () => {
    this.ws?.send(JSON.stringify({ 
      type: 'subscribe', 
      channel 
    }));
  };
  
  this.ws.onmessage = (event) => {
    const data = JSON.parse(event.data);
    this.handleRealtimeUpdate(data);
  };
  
  this.ws.onerror = (error) => {
    console.error('WebSocket error:', error);
  };
}

disconnectedCallback() {
  super.disconnectedCallback();
  if (this.ws) {
    this.ws.close();
  }
}
```

### 8. Request Interceptors (Phase C)

**Planned:** Global request/response interceptors for logging, auth refresh, etc.

```typescript
interface Interceptor {
  onRequest?: (config: RequestInit) => RequestInit | Promise<RequestInit>;
  onResponse?: (response: Response) => Response | Promise<Response>;
  onError?: (error: Error) => void;
}

class HttpClient {
  private interceptors: Interceptor[] = [];
  
  use(interceptor: Interceptor) {
    this.interceptors.push(interceptor);
  }
  
  async fetch(url: string, options: RequestInit = {}): Promise<Response> {
    let config = options;
    
    // Apply request interceptors
    for (const interceptor of this.interceptors) {
      if (interceptor.onRequest) {
        config = await interceptor.onRequest(config);
      }
    }
    
    try {
      let response = await fetch(url, config);
      
      // Apply response interceptors
      for (const interceptor of this.interceptors) {
        if (interceptor.onResponse) {
          response = await interceptor.onResponse(response);
        }
      }
      
      return response;
    } catch (error) {
      // Apply error interceptors
      for (const interceptor of this.interceptors) {
        if (interceptor.onError) {
          interceptor.onError(error as Error);
        }
      }
      throw error;
    }
  }
}
```

## Best Practices

### 1. Always Handle Errors

```typescript
// ✅ Good
try {
  const response = await fetch('/api/data');
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
  const data = await response.json();
} catch (error) {
  console.error('API call failed:', error);
  this.showError(error.message);
}

// ❌ Avoid
const response = await fetch('/api/data');
const data = await response.json(); // Can fail silently
```

### 2. Use Loading States

```typescript
// ✅ Good
this.isLoading = true;
try {
  await this.fetchData();
} finally {
  this.isLoading = false; // Always reset
}

// ❌ Avoid
this.isLoading = true;
await this.fetchData();
this.isLoading = false; // Won't run if error occurs
```

### 3. Encode URI Components

```typescript
// ✅ Good
const url = `/api/${encodeURIComponent(entityName)}`;

// ❌ Avoid
const url = `/api/${entityName}`; // Fails with special characters
```

### 4. Use Typed Responses

```typescript
// ✅ Good
interface User {
  id: string;
  name: string;
  email: string;
}

const response = await fetch('/api/users');
const users: User[] = await response.json();

// ❌ Avoid
const users: any = await response.json();
```

### 5. Cancel Requests on Unmount

```typescript
// ✅ Good
private abortController = new AbortController();

disconnectedCallback() {
  super.disconnectedCallback();
  this.abortController.abort();
}

// ❌ Avoid
// Letting requests complete after component is destroyed
```

### 6. Validate Request Data

```typescript
// ✅ Good
private async saveUser(user: User) {
  if (!user.email || !user.name) {
    throw new Error('Email and name are required');
  }
  
  return fetch('/api/users', {
    method: 'POST',
    body: JSON.stringify(user)
  });
}
```

### 7. Use Content-Type Headers

```typescript
// ✅ Good
fetch('/api/data', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(data)
});

// ❌ Avoid
fetch('/api/data', {
  method: 'POST',
  body: JSON.stringify(data) // Missing Content-Type
});
```

## Performance Considerations

### 1. Debounce Search Queries

```typescript
// Avoid making API calls on every keystroke
private debouncedSearch = debounce(this.search.bind(this), 300);
```

### 2. Cache Responses

```typescript
private cache = new Map<string, any>();

private async fetchWithCache(url: string) {
  if (this.cache.has(url)) {
    return this.cache.get(url);
  }
  
  const response = await fetch(url);
  const data = await response.json();
  this.cache.set(url, data);
  return data;
}
```

### 3. Use Pagination

```typescript
// Load data in chunks instead of all at once
const url = `/api/users?limit=25&offset=${page * 25}`;
```

### 4. Parallel Independent Requests

```typescript
// Load multiple resources concurrently
const [users, orders, products] = await Promise.all([
  fetch('/api/users').then(r => r.json()),
  fetch('/api/orders').then(r => r.json()),
  fetch('/api/products').then(r => r.json())
]);
```

## Testing API Calls

```typescript
// Mock fetch in tests
global.fetch = vi.fn(() =>
  Promise.resolve({
    ok: true,
    json: () => Promise.resolve({ data: 'test' })
  })
) as any;

describe('ApiComponent', () => {
  it('should fetch data', async () => {
    const component = new ApiComponent();
    await component.loadData();
    
    expect(fetch).toHaveBeenCalledWith('/api/data');
    expect(component.data).toEqual({ data: 'test' });
  });
});
```

## Resources

- [MDN Fetch API](https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API)
- [URLSearchParams](https://developer.mozilla.org/en-US/docs/Web/API/URLSearchParams)
- [AbortController](https://developer.mozilla.org/en-US/docs/Web/API/AbortController)
- [AppBana API Documentation](./ARCHITECT_GUIDE.md#23-api-generation)

