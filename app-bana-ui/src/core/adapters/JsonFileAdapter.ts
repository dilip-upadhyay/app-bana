/**
 * JSON File Adapter
 * 
 * File-based storage adapter for prototyping and testing.
 * Stores data in JSON format in browser localStorage or in-memory.
 * 
 * Perfect for:
 * - Rapid prototyping without backend
 * - Demo apps
 * - Testing entity definitions
 * - Offline-first apps
 */

import { BaseAdapter } from '../DataSourceAdapter';
import type {
  QueryParams,
  QueryResult,
  DatasourceCapabilities,
  ConnectionTestResult
} from '../DataSourceAdapter';

/**
 * JSON File Configuration
 */
export interface JsonFileConfig {
  storageType: 'memory' | 'localstorage' | 'sessionstorage';
  storageKey?: string;        // Key for localStorage (default: "appbana-data")
  initialData?: Record<string, Record<string, any>[]>; // Seed data
}

/**
 * JSON File Adapter
 */
export class JsonFileAdapter extends BaseAdapter {
  readonly capabilities: DatasourceCapabilities = {
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
    offline: true // Works offline!
  };
  
  private config!: JsonFileConfig;
  private data: Map<string, Record<string, any>[]> = new Map();
  private storage: Storage | null = null;
  
  async connect(config: JsonFileConfig): Promise<void> {
    this.config = {
      ...config,
      storageKey: config.storageKey || 'appbana-data'
    };
    
    // Setup storage
    if (config.storageType === 'localstorage') {
      this.storage = localStorage;
    } else if (config.storageType === 'sessionstorage') {
      this.storage = sessionStorage;
    }
    
    // Load existing data
    await this.loadData();
    
    // Apply initial data if provided
    if (config.initialData) {
      for (const [entity, records] of Object.entries(config.initialData)) {
        if (!this.data.has(entity)) {
          this.data.set(entity, records);
        }
      }
      await this.persist();
    }
  }
  
  async testConnection(): Promise<ConnectionTestResult> {
    try {
      // Test storage access
      if (this.storage) {
        const testKey = `${this.config.storageKey}_test`;
        this.storage.setItem(testKey, 'test');
        this.storage.removeItem(testKey);
      }
      
      return {
        success: true,
        message: `Connected to ${this.config.storageType} storage`,
        metadata: {
          product: 'JSON File Storage',
          version: '1.0.0'
        }
      };
    } catch (error: any) {
      return {
        success: false,
        message: error.message
      };
    }
  }
  
  async query<T = Record<string, any>>(entity: string, params: QueryParams): Promise<QueryResult<T>> {
    let records = this.data.get(entity) || [];
    
    // Apply filters
    if (params.filters && params.filters.length > 0) {
      records = this.applyFilters(records, params.filters);
    }
    
    // Apply search (full-text search across all fields)
    if (params.search) {
      const searchLower = params.search.toLowerCase();
      records = records.filter(record =>
        Object.values(record).some(value =>
          String(value).toLowerCase().includes(searchLower)
        )
      );
    }
    
    // Apply sorting
    if (params.sort && params.sort.length > 0) {
      records = this.applySort(records, params.sort);
    }
    
    const total = records.length;
    
    // Apply pagination
    const offset = params.offset || 0;
    const limit = params.limit;
    
    if (limit !== undefined) {
      records = records.slice(offset, offset + limit);
    } else if (offset > 0) {
      records = records.slice(offset);
    }
    
    return {
      data: records as T[],
      total,
      hasMore: limit !== undefined && offset + records.length < total,
      nextOffset: limit !== undefined && offset + records.length < total
        ? offset + records.length
        : undefined
    };
  }
  
  async get(entity: string, id: string): Promise<Record<string, any> | null> {
    const records = this.data.get(entity) || [];
    return records.find(r => r.id === id) || null;
  }
  
  async create(entity: string, data: Record<string, any>): Promise<Record<string, any>> {
    const records = this.data.get(entity) || [];
    
    // Generate ID if not provided
    const id = data.id || this.generateId();
    const record = { ...data, id };
    
    records.push(record);
    this.data.set(entity, records);
    
    await this.persist();
    
    return record;
  }
  
  async update(entity: string, id: string, data: Record<string, any>): Promise<Record<string, any>> {
    const records = this.data.get(entity) || [];
    const index = records.findIndex(r => r.id === id);
    
    if (index === -1) {
      throw new Error(`Record with id ${id} not found in entity ${entity}`);
    }
    
    // Update record (preserve id)
    const updated = { ...records[index], ...data, id };
    records[index] = updated;
    
    await this.persist();
    
    return updated;
  }
  
  async delete(entity: string, id: string): Promise<void> {
    const records = this.data.get(entity) || [];
    const filtered = records.filter(r => r.id !== id);
    
    if (filtered.length === records.length) {
      throw new Error(`Record with id ${id} not found in entity ${entity}`);
    }
    
    this.data.set(entity, filtered);
    await this.persist();
  }
  
  async batchCreate(entity: string, records: Record<string, any>[]): Promise<Record<string, any>[]> {
    const existingRecords = this.data.get(entity) || [];
    
    const newRecords = records.map(data => ({
      ...data,
      id: data.id || this.generateId()
    }));
    
    existingRecords.push(...newRecords);
    this.data.set(entity, existingRecords);
    
    await this.persist();
    
    return newRecords;
  }
  
  async batchUpdate(entity: string, records: Record<string, any>[]): Promise<Record<string, any>[]> {
    const existingRecords = this.data.get(entity) || [];
    const updated: Record<string, any>[] = [];
    
    for (const record of records) {
      if (!record.id) {
        throw new Error('Record must have id field for update');
      }
      
      const index = existingRecords.findIndex(r => r.id === record.id);
      if (index !== -1) {
        existingRecords[index] = { ...existingRecords[index], ...record };
        updated.push(existingRecords[index]);
      }
    }
    
    await this.persist();
    
    return updated;
  }
  
  async batchDelete(entity: string, ids: string[]): Promise<void> {
    const records = this.data.get(entity) || [];
    const filtered = records.filter(r => !ids.includes(r.id));
    
    this.data.set(entity, filtered);
    await this.persist();
  }
  
  async disconnect(): Promise<void> {
    await this.persist();
    this.data.clear();
  }
  
  /**
   * Load data from storage
   */
  private async loadData(): Promise<void> {
    if (!this.storage) {
      // In-memory only
      return;
    }
    
    try {
      const stored = this.storage.getItem(this.config.storageKey!);
      if (stored) {
        const parsed = JSON.parse(stored);
        this.data = new Map(Object.entries(parsed));
      }
    } catch (error: any) {
      console.warn('Failed to load data from storage:', error.message);
      this.data = new Map();
    }
  }
  
  /**
   * Persist data to storage
   */
  private async persist(): Promise<void> {
    if (!this.storage) {
      // In-memory only, no persistence
      return;
    }
    
    try {
      const obj = Object.fromEntries(this.data);
      const json = JSON.stringify(obj);
      this.storage.setItem(this.config.storageKey!, json);
    } catch (error: any) {
      console.error('Failed to persist data to storage:', error.message);
      throw new Error(`Storage error: ${error.message}`);
    }
  }
  
  /**
   * Generate unique ID
   */
  private generateId(): string {
    return `${Date.now()}-${Math.random().toString(36).substring(2, 11)}`;
  }
  
  /**
   * Get all data (for debugging/export)
   */
  getAllData(): Record<string, Record<string, any>[]> {
    return Object.fromEntries(this.data);
  }
  
  /**
   * Import data (for seeding/migration)
   */
  async importData(data: Record<string, Record<string, any>[]>): Promise<void> {
    this.data = new Map(Object.entries(data));
    await this.persist();
  }
  
  /**
   * Clear all data
   */
  async clearAll(): Promise<void> {
    this.data.clear();
    await this.persist();
  }
}
