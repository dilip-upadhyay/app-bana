# Universal Datasource Adapter System

## Overview

AppBana's Universal Datasource Adapter System allows entities to work with **any backend** - SQL databases, REST APIs, NoSQL stores, files, and more. The same entity definition works seamlessly across different datasources.

## Architecture

```
Business Layer (EntityMeta)
    ↓
Adapter Layer (DataSourceAdapter)
    ↓
Physical Storage (SQL, REST API, MongoDB, Files, etc.)
```

### Key Components

1. **DataSourceAdapter Interface**: Universal CRUD interface
2. **AdapterRegistry**: Manages adapter registration and creation
3. **Built-in Adapters**: REST API, JSON File, LocalStorage
4. **EntityMeta Extensions**: Datasource type and configuration

## Supported Datasource Types

### Relational Databases (JDBC)
- ✅ H2 (embedded)
- ✅ PostgreSQL
- ✅ MySQL / MariaDB
- ✅ Oracle
- ✅ SQL Server
- ✅ SQLite

### NoSQL Databases
- 🚧 MongoDB (planned)
- 🚧 DynamoDB (planned)
- 🚧 Redis (planned)
- 🚧 Cassandra (planned)

### External APIs
- ✅ REST API
- 🚧 GraphQL (planned)
- 🚧 SOAP (planned)
- 🚧 gRPC (planned)

### File-based Storage
- ✅ JSON File (in-memory)
- ✅ LocalStorage (browser)
- ✅ SessionStorage (browser)
- 🚧 CSV File (planned)
- 🚧 Excel File (planned)

### Cloud Services
- 🚧 Salesforce (planned)
- 🚧 Google Sheets (planned)
- 🚧 Airtable (planned)

## Quick Start

### 1. Using REST API Adapter

```typescript
import { RestApiAdapter } from './core/adapters';

// Connect to external API
const adapter = new RestApiAdapter({
  baseUrl: 'https://api.github.com',
  authType: 'bearer',
  apiKey: 'your-github-token',
  paginationStyle: 'page'
});

await adapter.connect();

// Query data
const result = await adapter.query('users', {
  limit: 25,
  offset: 0,
  search: 'john',
  sort: [{ field: 'created', desc: true }]
});

console.log(result.data); // Array of users
console.log(result.total); // Total count
```

### 2. Using JSON File Adapter (Prototyping)

```typescript
import { JsonFileAdapter } from './core/adapters';

// In-memory storage (perfect for demos)
const adapter = new JsonFileAdapter({
  storageType: 'memory',
  initialData: {
    customers: [
      { id: '1', name: 'Acme Corp', email: 'info@acme.com' },
      { id: '2', name: 'TechStart Inc', email: 'hello@techstart.io' }
    ]
  }
});

await adapter.connect();

// Query customers
const customers = await adapter.query('customers', {
  filters: [{ field: 'name', operator: 'contains', value: 'Acme' }]
});

// Create new customer
const newCustomer = await adapter.create('customers', {
  name: 'New Company',
  email: 'contact@newco.com'
});
```

### 3. Using LocalStorage (Offline-First)

```typescript
import { JsonFileAdapter } from './core/adapters';

// Persistent browser storage
const adapter = new JsonFileAdapter({
  storageType: 'localstorage',
  storageKey: 'my-app-data'
});

await adapter.connect();

// Data persists across browser sessions
const tasks = await adapter.query('tasks', {
  filters: [{ field: 'completed', operator: 'eq', value: false }]
});
```

## Defining Entities with Datasource Types

### Example 1: Entity with REST API Backend

```typescript
import type { EntityMeta } from './models/entity-metadata';

const customerEntity: EntityMeta = {
  id: 'customer',
  name: 'customer',
  displayName: 'Customer',
  
  // Datasource configuration
  datasource: 'stripe-api',
  datasourceType: 'rest-api',
  datasourceConfig: {
    api: {
      endpoint: '/customers',
      idField: 'id',
      responseTransform: 'data.data', // Stripe wraps response in { data: [...] }
      headers: {
        'Stripe-Version': '2023-10-16'
      }
    },
    cache: {
      enabled: true,
      ttlSeconds: 300 // Cache for 5 minutes
    }
  },
  
  fields: [
    { id: 'id', name: 'id', type: 'text', required: true, unique: true },
    { id: 'email', name: 'email', type: 'email', required: true, unique: true },
    { id: 'name', name: 'name', type: 'text', required: true },
    { id: 'balance', name: 'balance', type: 'currency', required: false }
  ]
};
```

### Example 2: Entity with File-based Storage (Prototyping)

```typescript
const productEntity: EntityMeta = {
  id: 'product',
  name: 'product',
  displayName: 'Product',
  
  // Use LocalStorage for offline demo
  datasource: 'demo-storage',
  datasourceType: 'localstorage',
  datasourceConfig: {
    file: {
      format: 'json'
    }
  },
  
  fields: [
    { id: 'id', name: 'id', type: 'autoincrement', required: true, unique: true },
    { id: 'name', name: 'name', type: 'text', required: true },
    { id: 'price', name: 'price', type: 'currency', required: true },
    { id: 'inStock', name: 'inStock', type: 'boolean', required: true }
  ]
};
```

### Example 3: Hybrid Entity (Cache + Database)

```typescript
const orderEntity: EntityMeta = {
  id: 'order',
  name: 'order',
  displayName: 'Order',
  
  // Primary datasource: PostgreSQL
  datasource: 'primary-db',
  datasourceType: 'postgres',
  
  // Enable caching for frequently accessed data
  datasourceConfig: {
    cache: {
      enabled: true,
      ttlSeconds: 60,
      strategy: 'memory'
    }
  },
  
  fields: [
    { id: 'id', name: 'id', type: 'autoincrement', required: true, unique: true },
    { id: 'customerId', name: 'customerId', type: 'reference', required: true },
    { id: 'total', name: 'total', type: 'currency', required: true },
    { id: 'status', name: 'status', type: 'status', required: true,
      options: [
        { value: 'pending', label: 'Pending', color: '#FFA500' },
        { value: 'completed', label: 'Completed', color: '#00FF00' },
        { value: 'cancelled', label: 'Cancelled', color: '#FF0000' }
      ]
    }
  ]
};
```

## Creating Custom Adapters

You can create adapters for any datasource by extending `BaseAdapter`:

```typescript
import { BaseAdapter } from './core/DataSourceAdapter';
import type {
  QueryParams,
  QueryResult,
  DatasourceCapabilities,
  ConnectionTestResult
} from './core/DataSourceAdapter';

export class CustomAdapter extends BaseAdapter {
  readonly capabilities: DatasourceCapabilities = {
    create: true,
    read: true,
    update: true,
    delete: true,
    transactions: false,
    relationships: false,
    fullTextSearch: true,
    aggregations: false,
    pagination: true,
    sorting: true,
    filtering: true,
    schemaMigration: false,
    indexing: false,
    constraints: false,
    realtime: false,
    caching: true,
    offline: false
  };
  
  async connect(config: any): Promise<void> {
    // Initialize connection
  }
  
  async testConnection(): Promise<ConnectionTestResult> {
    // Test connection
    return { success: true, message: 'Connected' };
  }
  
  async query<T>(entity: string, params: QueryParams): Promise<QueryResult<T>> {
    // Implement query logic
    return {
      data: [],
      total: 0,
      hasMore: false
    };
  }
  
  async get(entity: string, id: string): Promise<Record<string, any> | null> {
    // Get single record
    return null;
  }
  
  async create(entity: string, data: Record<string, any>): Promise<Record<string, any>> {
    // Create record
    return data;
  }
  
  async update(entity: string, id: string, data: Record<string, any>): Promise<Record<string, any>> {
    // Update record
    return data;
  }
  
  async delete(entity: string, id: string): Promise<void> {
    // Delete record
  }
  
  async disconnect(): Promise<void> {
    // Cleanup
  }
}
```

### Register Custom Adapter

```typescript
import { registerAdapter } from './core/AdapterRegistry';
import { CustomAdapter } from './adapters/CustomAdapter';

registerAdapter('custom', CustomAdapter, {
  name: 'Custom Datasource',
  description: 'My custom datasource adapter'
});
```

## Datasource Capabilities

Each adapter declares its capabilities, allowing the UI to adapt:

```typescript
interface DatasourceCapabilities {
  create: boolean;          // Can insert records
  read: boolean;            // Can query records
  update: boolean;          // Can modify records
  delete: boolean;          // Can delete records
  transactions: boolean;    // Supports ACID transactions
  relationships: boolean;   // Supports foreign keys/joins
  fullTextSearch: boolean;  // Supports text search
  aggregations: boolean;    // Supports SUM, AVG, COUNT
  pagination: boolean;      // Supports offset/limit
  sorting: boolean;         // Supports ORDER BY
  filtering: boolean;       // Supports WHERE clauses
  schemaMigration: boolean; // Can ALTER TABLE
  indexing: boolean;        // Can create indexes
  constraints: boolean;     // Supports PRIMARY KEY, UNIQUE
  realtime: boolean;        // WebSocket/SSE support
  caching: boolean;         // Built-in cache layer
  offline: boolean;         // Offline-first support
}
```

### Checking Capabilities

```typescript
import { adapterRegistry } from './core/AdapterRegistry';

const capabilities = adapterRegistry.getCapabilities('rest-api');

if (capabilities.relationships) {
  // Show relationship editor
} else {
  // Hide relationship features
  console.warn('This datasource does not support relationships');
}
```

## Universal Query Language

All adapters support a universal query format:

```typescript
interface QueryParams {
  filters?: Filter[];        // WHERE clauses
  sort?: Sort[];            // ORDER BY
  limit?: number;           // Max records
  offset?: number;          // Skip records
  fields?: string[];        // SELECT specific fields
  search?: string;          // Full-text search
  aggregations?: Aggregation[]; // GROUP BY, SUM, AVG
}
```

### Filter Operators

- `eq`: equals
- `ne`: not equals
- `gt`: greater than
- `gte`: greater than or equal
- `lt`: less than
- `lte`: less than or equal
- `in`: in array
- `nin`: not in array
- `contains`: string contains
- `startsWith`: string starts with
- `endsWith`: string ends with
- `isNull`: is null
- `isNotNull`: is not null

### Example Queries

```typescript
// Complex query with multiple filters
const result = await adapter.query('orders', {
  filters: [
    { field: 'status', operator: 'eq', value: 'pending' },
    { field: 'total', operator: 'gt', value: 100 },
    { field: 'customerId', operator: 'in', value: ['1', '2', '3'] }
  ],
  sort: [
    { field: 'createdAt', desc: true }
  ],
  limit: 25,
  offset: 0,
  search: 'urgent'
});
```

## Best Practices

### 1. Choose the Right Datasource

- **PostgreSQL/MySQL**: Production apps with relationships
- **REST API**: Integrate external services (Stripe, GitHub, Twilio)
- **LocalStorage**: Offline-first apps, demos, prototyping
- **In-Memory**: Testing, temporary data

### 2. Enable Caching for External APIs

```typescript
datasourceConfig: {
  cache: {
    enabled: true,
    ttlSeconds: 300, // 5 minutes
    strategy: 'memory'
  }
}
```

### 3. Handle Rate Limiting

REST API adapter includes built-in rate limiting:

```typescript
const adapter = new RestApiAdapter({
  baseUrl: 'https://api.example.com',
  rateLimit: 60, // Max 60 requests per minute
  retryAttempts: 3, // Retry failed requests
  timeout: 30000 // 30 second timeout
});
```

### 4. Use Read-Only Mode for External APIs

```typescript
datasourceConfig: {
  api: {
    endpoint: '/readonly-data',
    readOnly: true // Disable create/update/delete
  }
}
```

## Troubleshooting

### CORS Issues with REST APIs

Configure CORS headers or use a proxy:

```typescript
datasourceConfig: {
  api: {
    endpoint: '/api/proxy/external',
    headers: {
      'X-Proxy-Target': 'https://external-api.com'
    }
  }
}
```

### LocalStorage Quota Exceeded

Switch to in-memory storage:

```typescript
storageType: 'memory' // No persistence, unlimited size
```

### Slow REST API Responses

Enable caching:

```typescript
datasourceConfig: {
  cache: {
    enabled: true,
    ttlSeconds: 600 // Cache for 10 minutes
  }
}
```

## Future Enhancements

- [ ] MongoDB adapter
- [ ] GraphQL adapter with subscription support
- [ ] Real-time sync across devices
- [ ] Conflict resolution for offline mode
- [ ] Data transformation pipeline
- [ ] Multi-datasource joins
- [ ] Adapter middleware system

## Contributing

To add a new adapter:

1. Create adapter class extending `BaseAdapter`
2. Implement all required methods
3. Declare capabilities
4. Register with `registerAdapter()`
5. Add tests
6. Update documentation

---

**Questions?** Check the main [Architecture Documentation](../docs/01-ARCHITECTURE.md)
