// Discriminated schema union types for Studio (frontend only for now)

export type ModelKind = 'relational' | 'document' | 'apiResource';

// Base shared fields
interface BaseSchemaMeta {
  metaVersion?: number;
  name: string;
  datasourceName?: string; // maps to backend datasource (relational now)
  modelKind?: ModelKind;   // default 'relational' if omitted
  description?: string;
  tags?: string[];
}

export interface RelationalField {
  name: string;
  type: string; // int,long,string,boolean,date,timestamp,text
  primaryKey?: boolean;
  autoIncrement?: boolean;
  length?: number;
  required?: boolean;
  min?: number;
  max?: number;
  pattern?: string;
  existingName?: string; // rename support
}

export interface RelationalSchema extends BaseSchemaMeta {
  modelKind?: 'relational';
  fields: RelationalField[];
}

export interface DocumentField {
  name: string;
  type: string; // string|number|boolean|date|object|array
  required?: boolean;
}

export interface DocumentSchema extends BaseSchemaMeta {
  modelKind: 'document';
  validationMode?: 'strict' | 'loose';
  fields?: DocumentField[]; // optional => schemaless
  sampleDocuments?: Record<string, any>[];
}

export interface ApiOperation {
  name: string;
  method: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';
  path: string;           // relative to basePath
  params?: Array<{ in: 'query'|'path'|'header'; name: string; required?: boolean; type?: string }>;
  responseType?: 'object' | 'array' | 'text' | 'binary';
  bodySchemaRef?: string;
  notes?: string;
}

export interface ResourceSchema extends BaseSchemaMeta {
  modelKind: 'apiResource';
  basePath?: string;
  operations: ApiOperation[];
  cacheTtlSeconds?: number;
}

export type AnyDesignSchema = RelationalSchema | DocumentSchema | ResourceSchema;

export function isRelational(s: AnyDesignSchema): s is RelationalSchema {
  return !s.modelKind || s.modelKind === 'relational';
}
export function isDocument(s: AnyDesignSchema): s is DocumentSchema {
  return s.modelKind === 'document';
}
export function isResource(s: AnyDesignSchema): s is ResourceSchema {
  return s.modelKind === 'apiResource';
}
