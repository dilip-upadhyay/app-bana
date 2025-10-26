# API Client Migration Guide

This guide helps you migrate from direct `fetch` calls to the new API Client wrapper with interceptors.

## Quick Start

### 1. Initialize the API Client (Once in your app)

```typescript
// In your main entry file (e.g., index.ts or studio-entry.ts)
import { setupApiClient } from './core/api-setup.ts';

setupApiClient({
  enableLogging: true,  // Enable in development
  enableRetry: true,    // Auto-retry failed requests
  onLoadingChange: (isLoading, activeRequests) => {
    // Update global loading state
    document.dispatchEvent(new CustomEvent('loading-change', { 
      detail: { isLoading, activeRequests } 
    }));
  },
  onError: (error) => {
    // Show toast/notification
    console.error('API Error:', error);
  }
});
```

### 2. Use the API in Your Components

```typescript
import { api } from './core';

// High-level service layer (recommended)
const users = await api.entity.query('users', { limit: 25 });
const schema = await api.schema.get('users');
```

## Migration Examples

### Example 1: EntityExplorer - Load Entities

**BEFORE:**
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

**AFTER:**
```typescript
import { api } from './core';

private async loadEntities() {
  this.loadingEntities = true;
  try {
    this.entities = await api.schema.list();
  } catch (e) {
    console.error(e);
  } finally {
    this.loadingEntities = false;
  }
}
// No need for authHeaders() - handled automatically by interceptor!
```

### Example 2: EntityExplorer - Query with Parameters

**BEFORE:**
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

**AFTER:**
```typescript
import { api } from './core';

private async runQuery() {
  this.loadingData = true;
  try {
    const { entity, limit, offset, q, fields, sort, count } = this.query;
    if (!entity) return;
    
    this.result = await api.entity.query(entity, {
      limit,
      offset,
      q: q.trim() || undefined,
      fields: fields.trim() || undefined,
      sort: sort.trim() || undefined,
      filter: this.buildFilterParam() || undefined,
      count,
    });
  } catch (e: any) {
    this.result = { error: e.message };
  } finally {
    this.loadingData = false;
  }
}
// No need for buildQueryUrl() - handled by API client!
```

### Example 3: EntityExplorer - Batch Insert

**BEFORE:**
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

**AFTER:**
```typescript
import { api } from './core';

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
    this.batchResult = await api.entity.batchInsert(this.query.entity, parsed);
  } catch (e: any) {
    this.batchError = e.message;
  }
}
```

### Example 4: SchemaBuilder - Save/Preview Schema

**BEFORE:**
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

**AFTER:**
```typescript
import { api } from './core';

private async saveSchema() {
  try {
    const result = await api.schema.save({
      name: this.schemaName,
      fields: this.fields
    });
    console.log('Schema saved:', result);
  } catch (error) {
    console.error('Save failed:', error);
  }
}

private async previewSchema() {
  try {
    this.previewResult = await api.schema.preview({
      name: this.schemaName,
      fields: this.fields
    });
  } catch (error) {
    console.error('Preview failed:', error);
  }
}
```

### Example 5: Delete with Query Parameters

**BEFORE:**
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

**AFTER:**
```typescript
import { api } from './core';

private async deleteSchema() {
  try {
    await api.schema.delete(this.selectedSchemaName, this.deleteDropTable);
    console.log('Schema deleted');
    await this.loadSchemas();
  } catch (error) {
    console.error('Delete failed:', error);
  }
}
```

## Benefits of New API Client

### 1. **Automatic Authentication**
- No need to manually add auth headers
- Token is automatically added by interceptor

### 2. **Simplified Error Handling**
- Consistent error format
- Global error handler
- Better error messages

### 3. **Built-in Retry Logic**
- Automatically retries failed requests
- Exponential backoff
- Configurable retry conditions

### 4. **Request/Response Logging**
- Automatic logging in development mode
- Request/response/error tracking
- Easy debugging

### 5. **Type Safety**
- TypeScript interfaces for all endpoints
- Better autocomplete
- Compile-time checking

### 6. **Less Boilerplate**
- No need to build URLs manually
- Automatic JSON serialization
- Simplified query parameters

### 7. **Centralized Configuration**
- Single place to configure timeouts
- Global headers
- Base URL management

### 8. **Interceptor Flexibility**
- Add custom logic to all requests
- Transform requests/responses
- Handle errors globally

## Adding Custom Interceptors

```typescript
import { apiClient } from './core';

// Add custom interceptor
apiClient.interceptor.use({
  name: 'myInterceptor',
  onRequest: (config) => {
    console.log('Before request');
    config.headers = {
      ...config.headers,
      'X-Custom': 'value'
    };
    return config;
  },
  onResponse: (response, data) => {
    console.log('After response');
    return data;
  },
  onError: (error) => {
    console.error('Error occurred', error);
  }
});
```

## Low-Level API Client

If you need more control, use the low-level client:

```typescript
import { apiClient } from './core';

// Direct usage
const users = await apiClient.get('/api/users', { limit: 25 });
const newUser = await apiClient.post('/api/users', { name: 'John' });
const updated = await apiClient.put('/api/users/123', { name: 'Jane' });
await apiClient.delete('/api/users/123');
```

## Migration Checklist

- [ ] Initialize API client in your main entry file
- [ ] Replace `fetch('/schema')` with `api.schema.list()`
- [ ] Replace `fetch('/api/{entity}')` with `api.entity.query()`
- [ ] Replace POST requests with `api.entity.create()` or `api.schema.save()`
- [ ] Replace batch inserts with `api.entity.batchInsert()`
- [ ] Remove manual auth header code
- [ ] Remove manual URL building code
- [ ] Update error handling to use new error format
- [ ] Test all API calls

## Troubleshooting

### Issue: Authentication not working
**Solution:** Make sure you initialized the API client with `setupApiClient()` and the token is in localStorage as `appbana_token`.

### Issue: Requests not being logged
**Solution:** Enable logging in setup: `setupApiClient({ enableLogging: true })`

### Issue: Need to skip interceptors for specific request
**Solution:** Use `skipInterceptors: true`:
```typescript
await apiClient.request('/api/endpoint', { skipInterceptors: true });
```

### Issue: Custom headers not being sent
**Solution:** Add headers in request config:
```typescript
await apiClient.get('/api/endpoint', undefined, {
  headers: { 'X-Custom': 'value' }
});
```

## Next Steps

1. Read `/docs/API_INTEGRATION.md` for complete API documentation
2. Check `/src/core/api-examples.ts` for more examples
3. Review the interceptor documentation in `/src/core/api-interceptors.ts`

