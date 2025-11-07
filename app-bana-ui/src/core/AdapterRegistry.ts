/**
 * Adapter Registry
 * 
 * Central registry for all datasource adapters.
 * Manages adapter registration, creation, and capability detection.
 */

import type { DataSourceAdapter, DataSourceType, DatasourceCapabilities } from './DataSourceAdapter';

/**
 * Adapter constructor type
 */
type AdapterConstructor = new (config: any) => DataSourceAdapter;

/**
 * Adapter metadata
 */
interface AdapterMetadata {
  type: DataSourceType;
  name: string;
  description: string;
  constructor: AdapterConstructor;
  configSchema?: any; // JSON schema for config validation
}

/**
 * Adapter Registry
 * Singleton pattern for managing datasource adapters
 */
export class AdapterRegistry {
  private static instance: AdapterRegistry;
  private readonly adapters = new Map<DataSourceType, AdapterMetadata>();
  
  private constructor() {
    // Private constructor for singleton
  }
  
  /**
   * Get singleton instance
   */
  static getInstance(): AdapterRegistry {
    if (!AdapterRegistry.instance) {
      AdapterRegistry.instance = new AdapterRegistry();
    }
    return AdapterRegistry.instance;
  }
  
  /**
   * Register adapter for datasource type
   */
  register(
    type: DataSourceType,
    constructor: AdapterConstructor,
    metadata?: {
      name?: string;
      description?: string;
      configSchema?: any;
    }
  ): void {
    this.adapters.set(type, {
      type,
      name: metadata?.name || type,
      description: metadata?.description || `${type} datasource adapter`,
      constructor,
      configSchema: metadata?.configSchema
    });
  }
  
  /**
   * Create adapter instance
   */
  create(type: DataSourceType, config: any): DataSourceAdapter {
    const metadata = this.adapters.get(type);
    if (!metadata) {
      throw new Error(`No adapter registered for datasource type: ${type}`);
    }
    return new metadata.constructor(config);
  }
  
  /**
   * Get capabilities for datasource type
   */
  getCapabilities(type: DataSourceType): DatasourceCapabilities {
    const metadata = this.adapters.get(type);
    if (!metadata) {
      // Return default capabilities if adapter not registered
      return this.getDefaultCapabilities(type);
    }
    
    // Create temporary instance to get capabilities
    try {
      const tempInstance = new metadata.constructor({});
      return tempInstance.capabilities;
    } catch (error: any) {
      // If instantiation fails, return defaults
      console.warn(`Failed to instantiate adapter for ${type}:`, error.message);
      return this.getDefaultCapabilities(type);
    }
  }
  
  /**
   * Check if adapter is registered
   */
  isRegistered(type: DataSourceType): boolean {
    return this.adapters.has(type);
  }
  
  /**
   * Get all registered adapters
   */
  getRegisteredAdapters(): AdapterMetadata[] {
    return Array.from(this.adapters.values());
  }
  
  /**
   * Get adapter metadata
   */
  getMetadata(type: DataSourceType): AdapterMetadata | undefined {
    return this.adapters.get(type);
  }
  
  /**
   * Get default capabilities based on datasource type
   */
  private getDefaultCapabilities(type: DataSourceType): DatasourceCapabilities {
    // Relational databases have full capabilities
    if (this.isRelationalDb(type)) {
      return {
        create: true,
        read: true,
        update: true,
        delete: true,
        transactions: true,
        relationships: true,
        fullTextSearch: true,
        aggregations: true,
        pagination: true,
        sorting: true,
        filtering: true,
        schemaMigration: true,
        indexing: true,
        constraints: true,
        realtime: false,
        caching: false,
        offline: false
      };
    }
    
    // NoSQL databases have most capabilities except relationships
    if (this.isNoSqlDb(type)) {
      return {
        create: true,
        read: true,
        update: true,
        delete: true,
        transactions: false,
        relationships: false,
        fullTextSearch: true,
        aggregations: true,
        pagination: true,
        sorting: true,
        filtering: true,
        schemaMigration: false,
        indexing: true,
        constraints: false,
        realtime: type === 'mongodb', // MongoDB supports change streams
        caching: true,
        offline: false
      };
    }
    
    // REST APIs have limited capabilities
    if (this.isRestApi(type)) {
      return {
        create: true,
        read: true,
        update: true,
        delete: true,
        transactions: false,
        relationships: false,
        fullTextSearch: false,
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
    }
    
    // File-based storage has basic capabilities
    if (this.isFileBased(type)) {
      return {
        create: true,
        read: true,
        update: true,
        delete: true,
        transactions: false,
        relationships: false,
        fullTextSearch: false,
        aggregations: false,
        pagination: true,
        sorting: true,
        filtering: true,
        schemaMigration: false,
        indexing: false,
        constraints: false,
        realtime: false,
        caching: false,
        offline: type === 'localstorage' || type === 'sessionstorage'
      };
    }
    
    // Default minimal capabilities
    return {
      create: true,
      read: true,
      update: true,
      delete: true,
      transactions: false,
      relationships: false,
      fullTextSearch: false,
      aggregations: false,
      pagination: false,
      sorting: false,
      filtering: false,
      schemaMigration: false,
      indexing: false,
      constraints: false,
      realtime: false,
      caching: false,
      offline: false
    };
  }
  
  /**
   * Type guards
   */
  private isRelationalDb(type: DataSourceType): boolean {
    return ['h2', 'postgres', 'mysql', 'oracle', 'mssql', 'sqlite', 'mariadb'].includes(type);
  }
  
  private isNoSqlDb(type: DataSourceType): boolean {
    return ['mongodb', 'couchdb', 'dynamodb', 'cassandra', 'redis'].includes(type);
  }
  
  private isRestApi(type: DataSourceType): boolean {
    return ['rest-api', 'graphql', 'soap', 'grpc', 'odata'].includes(type);
  }
  
  private isFileBased(type: DataSourceType): boolean {
    return [
      'json-file', 'csv-file', 'excel-file', 'xml-file',
      'in-memory', 'localstorage', 'sessionstorage'
    ].includes(type);
  }
}

/**
 * Export singleton instance
 */
export const adapterRegistry = AdapterRegistry.getInstance();

/**
 * Convenience function to register adapter
 */
export function registerAdapter(
  type: DataSourceType,
  constructor: AdapterConstructor,
  metadata?: {
    name?: string;
    description?: string;
    configSchema?: any;
  }
): void {
  adapterRegistry.register(type, constructor, metadata);
}
