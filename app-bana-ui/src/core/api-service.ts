/**
 * AppBana API Service
 * High-level service layer for AppBana-specific endpoints
 */

import { apiClient } from './api-client.ts';
import type { QueryParams } from './api-client.ts';

/**
 * Schema Management Service
 */
export class SchemaService {
  /**
   * List all schema names
   */
  async list(): Promise<string[]> {
    return apiClient.get<string[]>('/schema');
  }

  /**
   * Get schema summaries
   */
  async summaries(): Promise<Array<{ name: string; fieldCount: number }>> {
    return apiClient.get('/schema/summaries');
  }

  /**
   * Get specific schema
   */
  async get(name: string): Promise<any> {
    return apiClient.get(`/schema/${encodeURIComponent(name)}`);
  }

  /**
   * Create or update schema
   */
  async save(schema: { name: string; fields: any[] }): Promise<any> {
    return apiClient.post('/schema', schema);
  }

  /**
   * Preview schema changes
   */
  async preview(schema: { name: string; fields: any[] }): Promise<{ ddl: string[]; warnings: string[] }> {
    return apiClient.post('/schema', schema, {
      params: { preview: 'true' },
    });
  }

  /**
   * Delete schema
   */
  async delete(name: string, dropTable: boolean = false): Promise<{ success: boolean }> {
    return apiClient.delete(`/schema/${encodeURIComponent(name)}`, {
      dropTable: dropTable.toString(),
    });
  }

  /**
   * Get migration history
   */
  async migrations(name: string): Promise<any[]> {
    return apiClient.get(`/schema/${encodeURIComponent(name)}/migrations`);
  }
}

/**
 * Entity CRUD Service
 */
export class EntityService {
  /**
   * Query entities with filters
   */
  async query(entity: string, params: {
    limit?: number;
    offset?: number;
    q?: string;
    fields?: string;
    sort?: string;
    filter?: string;
    count?: boolean;
  } = {}): Promise<{ rows: any[]; total: number; query?: string }> {
    return apiClient.get(`/api/${encodeURIComponent(entity)}`, params);
  }

  /**
   * Get single entity by ID
   */
  async get(entity: string, id: string | number): Promise<any> {
    return apiClient.get(`/api/${encodeURIComponent(entity)}/${id}`);
  }

  /**
   * Create entity
   */
  async create(entity: string, data: any): Promise<any> {
    return apiClient.post(`/api/${encodeURIComponent(entity)}`, data);
  }

  /**
   * Update entity
   */
  async update(entity: string, id: string | number, data: any): Promise<any> {
    return apiClient.put(`/api/${encodeURIComponent(entity)}/${id}`, data);
  }

  /**
   * Delete entity
   */
  async delete(entity: string, id: string | number): Promise<any> {
    return apiClient.delete(`/api/${encodeURIComponent(entity)}/${id}`);
  }

  /**
   * Batch insert entities
   */
  async batchInsert(entity: string, records: any[]): Promise<{ inserted: number }> {
    return apiClient.post(`/api/${encodeURIComponent(entity)}/batch`, records);
  }

  /**
   * Batch update entities
   */
  async batchUpdate(entity: string, records: any[]): Promise<{ updated: number }> {
    return apiClient.put(`/api/${encodeURIComponent(entity)}/batch`, records);
  }

  /**
   * Batch delete entities
   */
  async batchDelete(entity: string, ids: Array<string | number>): Promise<{ deleted: number }> {
    return apiClient.post(`/api/${encodeURIComponent(entity)}/batch-delete`, { ids });
  }
}

/**
 * Datasource Service
 */
export class DatasourceService {
  /**
   * List all datasources
   */
  async list(): Promise<any[]> {
    return apiClient.get('/ui/datasource/list');
  }

  /**
   * Get specific datasource
   */
  async get(id: string): Promise<any> {
    return apiClient.get(`/ui/datasource/${id}`);
  }

  /**
   * Create datasource
   */
  async create(datasource: any): Promise<any> {
    return apiClient.post('/ui/datasource', datasource);
  }

  /**
   * Update datasource
   */
  async update(id: string, datasource: any): Promise<any> {
    return apiClient.put(`/ui/datasource/${id}`, datasource);
  }

  /**
   * Delete datasource
   */
  async delete(id: string): Promise<any> {
    return apiClient.delete(`/ui/datasource/${id}`);
  }

  /**
   * Test datasource connection
   */
  async testConnection(datasource: any): Promise<{ success: boolean; message?: string }> {
    return apiClient.post('/ui/datasource/test', datasource);
  }
}

/**
 * Audit Log Service
 */
export class AuditLogService {
  /**
   * Query audit logs
   */
  async query(params: {
    entity?: string;
    action?: string;
    userId?: string;
    startDate?: string;
    endDate?: string;
    limit?: number;
    offset?: number;
  } = {}): Promise<{ rows: any[]; total: number }> {
    return apiClient.get('/api/audit-log', params);
  }

  /**
   * Get audit log by ID
   */
  async get(id: string): Promise<any> {
    return apiClient.get(`/api/audit-log/${id}`);
  }
}

// Create singleton instances
export const schemaService = new SchemaService();
export const entityService = new EntityService();
export const datasourceService = new DatasourceService();
export const auditLogService = new AuditLogService();

// Export all services as a single object
export const api = {
  schema: schemaService,
  entity: entityService,
  datasource: datasourceService,
  auditLog: auditLogService,

  // Direct access to underlying client
  client: apiClient,
};

