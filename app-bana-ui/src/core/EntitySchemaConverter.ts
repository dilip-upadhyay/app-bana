/**
 * Entity to Schema Converter
 * Converts business-friendly entities to technical database schemas
 * This is the bridge between business users and power users
 */

import type { EntityMeta, EntityField, EntityFieldType, EntityRelationship } from '../models/entity-metadata';
import type { RelationalSchema, RelationalField } from '../models/schema';
import { EntityFieldTypeHelper } from '../models/entity-metadata';

export class EntitySchemaConverter {
  /**
   * Convert entity to relational schema
   * This generates the technical database schema from business entity definition
   */
  static entityToSchema(entity: EntityMeta): RelationalSchema {
    const fields: RelationalField[] = [];
    
    // Always add ID field if not already present
    const hasIdField = entity.fields.some(f => f.name === 'id' || f.type === 'autoincrement');
    if (!hasIdField) {
      fields.push({
        name: 'id',
        type: 'long',
        primaryKey: true,
        autoIncrement: true,
        required: true,
      });
    }
    
    // Convert each entity field to relational field
    for (const field of entity.fields) {
      // Skip calculated fields (they're computed, not stored)
      if (field.type === 'formula') {
        continue;
      }
      
      const relField = this.convertField(field);
      fields.push(relField);
    }
    
    // Add foreign key fields from relationships
    if (entity.relationships) {
      for (const rel of entity.relationships) {
        if (this.shouldAddForeignKey(rel, entity.id)) {
          const fkField = this.createForeignKeyField(rel);
          // Only add if not already present
          if (!fields.some(f => f.name === fkField.name)) {
            fields.push(fkField);
          }
        }
      }
    }
    
    // Add soft delete field if enabled
    if (entity.softDelete) {
      fields.push({
        name: 'deleted',
        type: 'boolean',
        required: false,
      });
    }
    
    // Add version field if versioning enabled
    if (entity.versioning) {
      fields.push({
        name: 'version',
        type: 'int',
        required: true,
      });
    }
    
    return {
      name: entity.tableName || entity.name,
      datasourceName: entity.datasource,
      fields,
      description: entity.description,
      tags: entity.icon ? [entity.icon] : undefined,
    };
  }
  
  /**
   * Convert entity field to relational field
   */
  private static convertField(field: EntityField): RelationalField {
    const relField: RelationalField = {
      name: field.name,
      type: this.mapFieldType(field.type),
      required: field.required,
    };
    
    // Handle special field types
    if (field.type === 'autoincrement') {
      relField.primaryKey = true;
      relField.autoIncrement = true;
    }
    
    if (field.unique) {
      relField.required = true; // Unique implies not null in most DBs
    }
    
    // Add length for string types
    if (field.validation?.maxLength && this.isStringType(field.type)) {
      relField.length = field.validation.maxLength;
    }
    
    // Add numeric constraints
    if (field.validation?.min !== undefined) {
      relField.min = field.validation.min;
    }
    if (field.validation?.max !== undefined) {
      relField.max = field.validation.max;
    }
    
    // Add pattern validation
    if (field.validation?.pattern) {
      relField.pattern = field.validation.pattern;
    }
    
    return relField;
  }
  
  /**
   * Map business field type to technical SQL type
   */
  private static mapFieldType(fieldType: string): string {
    const typeMap: Record<string, string> = {
      // Text
      'text': 'string',
      'longtext': 'text',
      'email': 'string',
      'phone': 'string',
      'url': 'string',
      'color': 'string',
      'markdown': 'text',
      'richtext': 'text',
      
      // Numeric
      'number': 'long',
      'decimal': 'decimal',
      'currency': 'decimal',
      'percentage': 'decimal',
      'duration': 'int',
      
      // Date/Time
      'date': 'date',
      'datetime': 'timestamp',
      'time': 'time',
      
      // Boolean
      'boolean': 'boolean',
      
      // Selection (store as string/enum)
      'status': 'string',
      'radio': 'string',
      'multiselect': 'text', // JSON array
      
      // Rich types
      'file': 'string',
      'image': 'string',
      'json': 'text',
      
      // References
      'reference': 'long',
      'lookup': 'long',
      
      // System
      'autoincrement': 'long',
      'uuid': 'string',
      'createdAt': 'timestamp',
      'updatedAt': 'timestamp',
      'createdBy': 'long',
      'updatedBy': 'long',
    };
    
    return typeMap[fieldType] || 'string';
  }
  
  /**
   * Check if field type is string-based
   */
  private static isStringType(fieldType: string): boolean {
    return ['text', 'email', 'phone', 'url', 'color', 'status', 'radio', 'file', 'image', 'uuid'].includes(fieldType);
  }
  
  /**
   * Determine if foreign key should be added for relationship
   */
  private static shouldAddForeignKey(rel: EntityRelationship, currentEntityId: string): boolean {
    // Add FK for many-to-one (this entity has FK to other entity)
    if (rel.type === 'many-to-one' && rel.fromEntity === currentEntityId) {
      return true;
    }
    
    // Add FK for one-to-one (depends on which side owns the relationship)
    if (rel.type === 'one-to-one' && rel.fromEntity === currentEntityId) {
      return true;
    }
    
    return false;
  }
  
  /**
   * Create foreign key field for relationship
   */
  private static createForeignKeyField(rel: EntityRelationship): RelationalField {
    const fieldName = rel.fromField || `${rel.toEntity}Id`;
    
    return {
      name: fieldName,
      type: 'long',
      required: rel.required || false,
    };
  }
  
  /**
   * Convert relational schema back to entity (for editing existing schemas)
   */
  static schemaToEntity(schema: RelationalSchema): EntityMeta {
    const fields: EntityField[] = [];
    
    for (const field of schema.fields) {
      // Skip system ID field (we'll add it automatically)
      if (field.name === 'id' && field.primaryKey) {
        continue;
      }
      
      const entityField = this.convertToEntityField(field);
      fields.push(entityField);
    }
    
    return {
      id: schema.name,
      name: schema.name,
      displayName: this.toDisplayName(schema.name),
      datasource: schema.datasourceName || 'default',
      fields,
      description: schema.description,
    };
  }
  
  /**
   * Convert relational field to entity field
   */
  private static convertToEntityField(field: RelationalField): EntityField {
    const businessType = this.inferBusinessType(field);
    
    const entityField: EntityField = {
      id: field.name,
      name: field.name,
      type: businessType,
      required: field.required || false,
      unique: false, // Inferred from schema constraints if available
    };
    
    // Add validation rules
    if (field.min !== undefined || field.max !== undefined || field.length !== undefined || field.pattern) {
      entityField.validation = {
        min: field.min,
        max: field.max,
        maxLength: field.length,
        pattern: field.pattern,
      };
    }
    
    // Add display configuration
    entityField.display = {
      label: this.toDisplayName(field.name),
    };
    
    return entityField;
  }
  
  /**
   * Infer business field type from technical SQL type
   */
  private static inferBusinessType(field: RelationalField): EntityFieldType {
    // Check for auto-increment primary key
    if (field.primaryKey && field.autoIncrement) {
      return 'autoincrement';
    }
    
    // Check for foreign keys (convention: ends with "Id")
    if (field.name.endsWith('Id') && field.type === 'long') {
      return 'reference';
    }
    
    // Check for timestamps
    if (field.type === 'timestamp') {
      if (field.name === 'createdAt' || field.name === 'created') return 'createdAt';
      if (field.name === 'updatedAt' || field.name === 'updated' || field.name === 'modified') return 'updatedAt';
      return 'datetime';
    }
    
    // Map SQL type to business type
    const typeMap: Record<string, EntityFieldType> = {
      'string': 'text',
      'text': 'longtext',
      'long': 'number',
      'int': 'number',
      'decimal': 'decimal',
      'date': 'date',
      'time': 'time',
      'boolean': 'boolean',
    };
    
    return typeMap[field.type] || 'text' as EntityFieldType;
  }
  
  /**
   * Convert camelCase/snake_case to "Display Name"
   */
  private static toDisplayName(name: string): string {
    return name
      .replace(/([A-Z])/g, ' $1') // camelCase -> camel Case
      .replace(/_/g, ' ')          // snake_case -> snake case
      .replace(/\s+/g, ' ')        // normalize spaces
      .trim()
      .split(' ')
      .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
      .join(' ');
  }
  
  /**
   * Generate CREATE TABLE SQL for entity (for preview/debugging)
   */
  static generateDDL(entity: EntityMeta): string {
    const schema = this.entityToSchema(entity);
    const tableName = entity.tableName || entity.name;
    
    let sql = `CREATE TABLE ${tableName} (\n`;
    
    // Generate field definitions
    const fieldDefs = schema.fields.map(field => {
      const sqlType = EntityFieldTypeHelper.toSQLType(field.type as any);
      let def = `  ${field.name} ${sqlType}`;
      
      if (field.required) def += ' NOT NULL';
      if (field.primaryKey) def += ' PRIMARY KEY';
      if (field.autoIncrement) def += ' AUTO_INCREMENT';
      
      return def;
    });
    
    sql += fieldDefs.join(',\n');
    
    // Generate FOREIGN KEY constraints from relationships
    if (entity.relationships && entity.relationships.length > 0) {
      const foreignKeyDefs = entity.relationships
        .filter(rel => this.shouldAddForeignKey(rel, entity.id))
        .map(rel => {
          const fromField = rel.fromField || `${rel.toEntity}Id`;
          const toField = rel.toField || 'id';
          const cascadeDelete = rel.cascadeDelete ? ' ON DELETE CASCADE' : '';
          
          return `  FOREIGN KEY (${fromField}) REFERENCES ${rel.toEntity}(${toField})${cascadeDelete}`;
        });
      
      if (foreignKeyDefs.length > 0) {
        sql += ',\n' + foreignKeyDefs.join(',\n');
      }
    }
    
    sql += '\n);';
    
    // Generate junction tables for many-to-many relationships
    if (entity.relationships) {
      const manyToManyRels = entity.relationships.filter(rel => 
        rel.type === 'many-to-many' && rel.fromEntity === entity.id
      );
      
      if (manyToManyRels.length > 0) {
        sql += '\n\n-- Junction tables for many-to-many relationships\n';
        
        for (const rel of manyToManyRels) {
          const junctionTable = rel.junctionTable || `${entity.name}_${rel.toEntity}`;
          const fromField = rel.junctionFromField || `${entity.name}Id`;
          const toField = rel.junctionToField || `${rel.toEntity}Id`;
          const cascadeDelete = rel.cascadeDelete ? ' ON DELETE CASCADE' : '';
          
          sql += `\nCREATE TABLE ${junctionTable} (\n`;
          sql += `  id VARCHAR(255) NOT NULL PRIMARY KEY AUTO_INCREMENT,\n`;
          sql += `  ${fromField} BIGINT NOT NULL,\n`;
          sql += `  ${toField} BIGINT NOT NULL,\n`;
          sql += `  FOREIGN KEY (${fromField}) REFERENCES ${entity.name}(id)${cascadeDelete},\n`;
          sql += `  FOREIGN KEY (${toField}) REFERENCES ${rel.toEntity}(id)${cascadeDelete}\n`;
          sql += ');';
        }
      }
    }
    
    return sql;
  }
  
  /**
   * Detect changes between old and new entity definitions
   */
  static detectEntityChanges(oldEntity: EntityMeta, newEntity: EntityMeta): EntityChange[] {
    const changes: EntityChange[] = [];
    
    // Detect field additions
    for (const newField of newEntity.fields) {
      if (!oldEntity.fields.some(f => f.id === newField.id)) {
        changes.push({
          type: 'field-added',
          fieldId: newField.id,
          field: newField,
        });
      }
    }
    
    // Detect field removals
    for (const oldField of oldEntity.fields) {
      if (!newEntity.fields.some(f => f.id === oldField.id)) {
        changes.push({
          type: 'field-removed',
          fieldId: oldField.id,
          field: oldField,
        });
      }
    }
    
    // Detect field modifications
    for (const newField of newEntity.fields) {
      const oldField = oldEntity.fields.find(f => f.id === newField.id);
      if (oldField && this.hasFieldChanged(oldField, newField)) {
        changes.push({
          type: 'field-modified',
          fieldId: newField.id,
          oldField,
          newField,
        });
      }
    }
    
    return changes;
  }
  
  /**
   * Check if field definition has changed
   */
  private static hasFieldChanged(oldField: EntityField, newField: EntityField): boolean {
    return oldField.name !== newField.name ||
           oldField.type !== newField.type ||
           oldField.required !== newField.required ||
           oldField.unique !== newField.unique ||
           JSON.stringify(oldField.validation) !== JSON.stringify(newField.validation);
  }
  
  /**
   * Generate ALTER TABLE migrations for entity changes
   */
  static generateMigrationDDL(changes: EntityChange[], tableName: string): string[] {
    const statements: string[] = [];
    
    for (const change of changes) {
      switch (change.type) {
        case 'field-added':
          if (change.field) {
            const sqlType = EntityFieldTypeHelper.toSQLType(change.field.type, change.field);
            let stmt = `ALTER TABLE ${tableName} ADD COLUMN ${change.field.name} ${sqlType}`;
            if (change.field.required) stmt += ' NOT NULL';
            statements.push(stmt + ';');
          }
          break;
          
        case 'field-removed':
          if (change.field) {
            statements.push(`ALTER TABLE ${tableName} DROP COLUMN ${change.field.name};`);
          }
          break;
          
        case 'field-modified':
          if (change.oldField && change.newField) {
            const sqlType = EntityFieldTypeHelper.toSQLType(change.newField.type, change.newField);
            let stmt = `ALTER TABLE ${tableName} MODIFY COLUMN ${change.newField.name} ${sqlType}`;
            if (change.newField.required) stmt += ' NOT NULL';
            statements.push(stmt + ';');
          }
          break;
      }
    }
    
    return statements;
  }
}

/**
 * Entity change types
 */
export interface EntityChange {
  type: 'field-added' | 'field-removed' | 'field-modified' | 
        'relationship-added' | 'relationship-removed' | 'relationship-modified';
  fieldId?: string;
  field?: EntityField;
  oldField?: EntityField;
  newField?: EntityField;
  relationship?: EntityRelationship;
}
