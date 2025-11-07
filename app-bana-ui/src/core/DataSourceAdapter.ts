/**
 * Universal Data Source Adapter System
 * 
 * Philosophy: Business entities should be independent of underlying storage.
 * The same entity definition works for SQL, REST APIs, NoSQL, files, etc.
 * 
 * Each datasource type implements the DataSourceAdapter interface,
 * providing a unified CRUD interface regardless of the backend.
 */

/**
 * Supported datasource types
 */
export type DataSourceType = 
  // Relational databases (JDBC)
  | 'h2' | 'postgres' | 'mysql' | 'oracle' | 'mssql' | 'sqlite' | 'mariadb'
  
  // NoSQL databases
  | 'mongodb' | 'dynamodb' | 'cassandra' | 'couchdb' | 'redis'
  
  // External APIs
  | 'rest-api' | 'graphql' | 'soap' | 'grpc' | 'odata'
  
  // Files & In-Memory
  | 'json-file' | 'csv-file' | 'excel-file' | 'xml-file'
  | 'in-memory' | 'localstorage' | 'sessionstorage'
  
  // Cloud Services (future)
  | 'salesforce' | 'google-sheets' | 'airtable' | 's3';

/**
 * Query filter operators
 */
export type FilterOperator = 
  | 'eq'        // equals
  | 'ne'        // not equals
  | 'gt'        // greater than
  | 'gte'       // greater than or equal
  | 'lt'        // less than
  | 'lte'       // less than or equal
  | 'in'        // in array
  | 'nin'       // not in array
  | 'contains'  // string contains
  | 'startsWith' // string starts with
  | 'endsWith'   // string ends with
  | 'isNull'     // is null
  | 'isNotNull'; // is not null

/**
 * Query filter definition
 */
export interface Filter {
  field: string;
  operator: FilterOperator;
  value: any;
}

/**
 * Sort definition
 */
export interface Sort {
  field: string;
  desc?: boolean; // default: false (ascending)
}

/**
 * Aggregation definition
 */
export interface Aggregation {
  field: string;
  function: 'count' | 'sum' | 'avg' | 'min' | 'max';
  alias?: string;
}

/**
 * Query parameters (universal query language)
 */
export interface QueryParams {
  filters?: Filter[];        // WHERE clauses
  sort?: Sort[];            // ORDER BY
  limit?: number;           // Max records
  offset?: number;          // Skip records
  fields?: string[];        // SELECT specific fields
  search?: string;          // Full-text search
  aggregations?: Aggregation[]; // GROUP BY, SUM, AVG, etc.
}

/**
 * Query result (universal result format)
 */
export interface QueryResult<T = Record<string, any>> {
  data: T[];                // Retrieved records
  total: number;            // Total count (for pagination)
  hasMore: boolean;         // More records available
  nextOffset?: number;      // Next page offset
  aggregations?: Record<string, any>; // Aggregation results
}

/**
 * Datasource capabilities
 * Different datasources support different features
 */
export interface DatasourceCapabilities {
  // CRUD operations
  create: boolean;          // Can insert new records
  read: boolean;            // Can query/fetch records
  update: boolean;          // Can modify records
  delete: boolean;          // Can delete records
  
  // Advanced features
  transactions: boolean;    // Supports ACID transactions
  relationships: boolean;   // Supports foreign keys/joins
  fullTextSearch: boolean;  // Supports text search
  aggregations: boolean;    // Supports SUM, AVG, COUNT, etc.
  pagination: boolean;      // Supports offset/limit
  sorting: boolean;         // Supports ORDER BY
  filtering: boolean;       // Supports WHERE clauses
  
  // Schema management
  schemaMigration: boolean; // Can ALTER TABLE
  indexing: boolean;        // Can create indexes
  constraints: boolean;     // Supports PRIMARY KEY, UNIQUE, etc.
  
  // Real-time features
  realtime: boolean;        // WebSocket/SSE support
  caching: boolean;         // Built-in cache layer
  offline: boolean;         // Offline-first support
}

/**
 * Connection test result
 */
export interface ConnectionTestResult {
  success: boolean;
  message?: string;
  metadata?: {
    version?: string;
    product?: string;
    elapsedMs?: number;
  };
}

/**
 * Universal Data Source Adapter Interface
 * 
 * Every datasource type (SQL, REST API, NoSQL, files) implements this interface.
 * The entity layer doesn't care HOW data is stored, only that it can be queried.
 */
export interface DataSourceAdapter {
  /**
   * Datasource capabilities
   */
  readonly capabilities: DatasourceCapabilities;
  
  /**
   * Initialize connection to datasource
   */
  connect(config: Record<string, any>): Promise<void>;
  
  /**
   * Test connection health
   */
  testConnection(): Promise<ConnectionTestResult>;
  
  /**
   * Query records (SELECT in SQL, GET in REST, find() in MongoDB)
   */
  query<T = Record<string, any>>(entity: string, params: QueryParams): Promise<QueryResult<T>>;
  
  /**
   * Get single record by ID
   */
  get(entity: string, id: string): Promise<Record<string, any> | null>;
  
  /**
   * Create new record
   */
  create(entity: string, data: Record<string, any>): Promise<Record<string, any>>;
  
  /**
   * Update existing record
   */
  update(entity: string, id: string, data: Record<string, any>): Promise<Record<string, any>>;
  
  /**
   * Delete record
   */
  delete(entity: string, id: string): Promise<void>;
  
  /**
   * Batch operations
   */
  batchCreate?(entity: string, records: Record<string, any>[]): Promise<Record<string, any>[]>;
  batchUpdate?(entity: string, records: Record<string, any>[]): Promise<Record<string, any>[]>;
  batchDelete?(entity: string, ids: string[]): Promise<void>;
  
  /**
   * Schema operations (if supported)
   */
  createSchema?(entityMeta: any): Promise<void>;
  updateSchema?(entityMeta: any): Promise<void>;
  deleteSchema?(entity: string, dropTable?: boolean): Promise<void>;
  
  /**
   * Relationship handling (if supported)
   */
  queryRelated?(entity: string, id: string, relationship: string): Promise<any[]>;
  linkRelated?(entity: string, id: string, relationship: string, relatedIds: string[]): Promise<void>;
  unlinkRelated?(entity: string, id: string, relationship: string, relatedIds: string[]): Promise<void>;
  
  /**
   * Transaction support (if supported)
   */
  beginTransaction?(): Promise<void>;
  commitTransaction?(): Promise<void>;
  rollbackTransaction?(): Promise<void>;
  
  /**
   * Disconnect
   */
  disconnect(): Promise<void>;
}

/**
 * Base adapter class with common functionality
 */
export abstract class BaseAdapter implements DataSourceAdapter {
  abstract readonly capabilities: DatasourceCapabilities;
  
  abstract connect(config: Record<string, any>): Promise<void>;
  abstract testConnection(): Promise<ConnectionTestResult>;
  abstract query<T = Record<string, any>>(entity: string, params: QueryParams): Promise<QueryResult<T>>;
  abstract get(entity: string, id: string): Promise<Record<string, any> | null>;
  abstract create(entity: string, data: Record<string, any>): Promise<Record<string, any>>;
  abstract update(entity: string, id: string, data: Record<string, any>): Promise<Record<string, any>>;
  abstract delete(entity: string, id: string): Promise<void>;
  abstract disconnect(): Promise<void>;
  
  /**
   * Default batch create (calls create() in loop)
   */
  async batchCreate(entity: string, records: Record<string, any>[]): Promise<Record<string, any>[]> {
    const results: Record<string, any>[] = [];
    for (const record of records) {
      const result = await this.create(entity, record);
      results.push(result);
    }
    return results;
  }
  
  /**
   * Default batch update (calls update() in loop)
   */
  async batchUpdate(entity: string, records: Record<string, any>[]): Promise<Record<string, any>[]> {
    const results: Record<string, any>[] = [];
    for (const record of records) {
      if (!record.id) {
        throw new Error('Record must have id field for update');
      }
      const result = await this.update(entity, record.id, record);
      results.push(result);
    }
    return results;
  }
  
  /**
   * Default batch delete (calls delete() in loop)
   */
  async batchDelete(entity: string, ids: string[]): Promise<void> {
    for (const id of ids) {
      await this.delete(entity, id);
    }
  }
  
  /**
   * Helper: Apply filters to in-memory data
   */
  protected applyFilters(records: Record<string, any>[], filters: Filter[]): Record<string, any>[] {
    return records.filter(record => 
      filters.every(filter => this.matchesFilter(record, filter))
    );
  }
  
  /**
   * Helper: Check if record matches filter
   */
  protected matchesFilter(record: Record<string, any>, filter: Filter): boolean {
    const value = record[filter.field];
    
    switch (filter.operator) {
      case 'eq': return value === filter.value;
      case 'ne': return value !== filter.value;
      case 'gt': return value > filter.value;
      case 'gte': return value >= filter.value;
      case 'lt': return value < filter.value;
      case 'lte': return value <= filter.value;
      case 'in': return Array.isArray(filter.value) && filter.value.includes(value);
      case 'nin': return Array.isArray(filter.value) && !filter.value.includes(value);
      case 'contains': 
        return String(value).toLowerCase().includes(String(filter.value).toLowerCase());
      case 'startsWith':
        return String(value).toLowerCase().startsWith(String(filter.value).toLowerCase());
      case 'endsWith':
        return String(value).toLowerCase().endsWith(String(filter.value).toLowerCase());
      case 'isNull': return value === null || value === undefined;
      case 'isNotNull': return value !== null && value !== undefined;
      default: return true;
    }
  }
  
  /**
   * Helper: Apply sorting to in-memory data
   */
  protected applySort(records: Record<string, any>[], sort: Sort[]): Record<string, any>[] {
    return [...records].sort((a, b) => {
      for (const s of sort) {
        const aVal = a[s.field];
        const bVal = b[s.field];
        
        if (aVal < bVal) return s.desc ? 1 : -1;
        if (aVal > bVal) return s.desc ? -1 : 1;
      }
      return 0;
    });
  }
  
  /**
   * Helper: Apply pagination to in-memory data
   */
  protected applyPagination(records: Record<string, any>[], offset?: number, limit?: number): Record<string, any>[] {
    let result = records;
    if (offset) result = result.slice(offset);
    if (limit) result = result.slice(0, limit);
    return result;
  }
}
