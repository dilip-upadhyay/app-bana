/**
 * Backend Schema Sync
 * Converts EntityMeta to backend EntitySchema format and sends to /api/schema
 */

import { EntityMeta } from '../models/entity-metadata';
import { EntitySchemaConverter } from '../core/EntitySchemaConverter';
import { apiClient } from '../core/api-client';

/**
 * Backend EntitySchema format (matches Java model)
 */
interface BackendEntitySchema {
  name: string;
  fields: BackendField[];
  datasourceName?: string;
  modelKind?: 'relational' | 'document' | 'apiResource';
}

interface BackendField {
  name: string;
  type: string; // 'string', 'int', 'long', 'boolean', 'date', 'datetime', 'decimal'
  primaryKey: boolean;
  autoIncrement: boolean;
  length?: number;
  required?: boolean;
  min?: number;
  max?: number;
  pattern?: string;
  label?: string;
  placeholder?: string;
  order?: number;
}

/**
 * Convert EntityMeta to Backend EntitySchema format
 */
export function entityToBackendSchema(entity: EntityMeta): BackendEntitySchema {
  // First convert to RelationalSchema
  const relSchema = EntitySchemaConverter.entityToSchema(entity);
  
  // Then convert to backend format
  const backendFields: BackendField[] = relSchema.fields.map((field, index) => {
    const backendType = mapFieldTypeToBackend(field.type);
    const entityField = entity.fields.find(f => f.name === field.name);
    
    const backendField: BackendField = {
      name: field.name,
      type: backendType,
      primaryKey: field.primaryKey || false,
      autoIncrement: field.autoIncrement || false,
      required: field.required || false
    };

    // Add length for strings
    if (backendType === 'string' && field.length) {
      backendField.length = field.length;
    }

    // Add validation and display metadata
    addValidationMetadata(backendField, entityField);
    addDisplayMetadata(backendField, entityField);
    
    // Default order from array index
    backendField.order ??= index;

    return backendField;
  });

  return {
    name: entity.tableName || entity.name.toLowerCase(),
    fields: backendFields,
    datasourceName: entity.datasource,
    modelKind: 'relational'
  };
}

/**
 * Map RelationalField type to backend type
 */
function mapFieldTypeToBackend(type: string): string {
  const typeMap: Record<string, string> = {
    'string': 'string',
    'int': 'int',
    'long': 'long',
    'boolean': 'boolean',
    'date': 'date',
    'datetime': 'datetime',
    'timestamp': 'datetime',
    'decimal': 'decimal',
    'double': 'decimal'
  };
  return typeMap[type] || 'string';
}

/**
 * Add validation metadata from entity field
 */
function addValidationMetadata(backendField: BackendField, entityField: any): void {
  if (!entityField?.validation) return;
  
  if (entityField.validation.min !== undefined) {
    backendField.min = entityField.validation.min;
  }
  if (entityField.validation.max !== undefined) {
    backendField.max = entityField.validation.max;
  }
  if (entityField.validation.pattern) {
    backendField.pattern = entityField.validation.pattern;
  }
}

/**
 * Add display metadata from entity field
 */
function addDisplayMetadata(backendField: BackendField, entityField: any): void {
  if (!entityField?.display) return;
  
  if (entityField.display.label) {
    backendField.label = entityField.display.label;
  }
  if (entityField.display.placeholder) {
    backendField.placeholder = entityField.display.placeholder;
  }
  if (entityField.display.order !== undefined) {
    backendField.order = entityField.display.order;
  }
}

/**
 * Send entity schema to backend
 * This creates the database table
 */
export async function syncEntityToBackend(entity: EntityMeta, preview = false): Promise<any> {
  const backendSchema = entityToBackendSchema(entity);
  
  const endpoint = preview ? '/schema?preview=true' : '/schema';
  
  try {
    const response = await apiClient.post(endpoint, backendSchema);
    console.log(`✅ Entity ${entity.name} synced to backend`, response);
    return response;
  } catch (error) {
    console.error(`❌ Failed to sync entity ${entity.name} to backend:`, error);
    throw error;
  }
}

/**
 * Get migration plan (SQL DDL) from backend without creating the table
 */
export async function previewBackendSchema(entity: EntityMeta): Promise<string[]> {
  const response = await syncEntityToBackend(entity, true);
  return response as string[];
}

/**
 * List all schemas from backend
 */
export async function listBackendSchemas(): Promise<any[]> {
  try {
    const response = await apiClient.get('/schema/summaries');
    return response;
  } catch (error) {
    console.error('❌ Failed to list backend schemas:', error);
    throw error;
  }
}

/**
 * Get specific schema from backend
 */
export async function getBackendSchema(name: string): Promise<BackendEntitySchema> {
  try {
    const response = await apiClient.get(`/schema/${name}`);
    return response;
  } catch (error) {
    console.error(`❌ Failed to get backend schema ${name}:`, error);
    throw error;
  }
}

/**
 * Delete schema from backend (drops the table)
 */
export async function deleteBackendSchema(name: string): Promise<void> {
  try {
    await apiClient.delete(`/schema/${name}`);
    console.log(`✅ Schema ${name} deleted from backend`);
  } catch (error) {
    console.error(`❌ Failed to delete backend schema ${name}:`, error);
    throw error;
  }
}
