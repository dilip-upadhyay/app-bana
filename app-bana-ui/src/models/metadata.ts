// Metadata interfaces for Studio (Phase A)
export interface ComponentNode {
  id: string;
  type: string;
  props?: Record<string, any>;
  children?: string[];
  bindings?: Record<string, BindingSpec>;
  events?: Record<string, ActionSpec[]>;
  security?: SecuritySpec;
  style?: StyleSpec;
}

export interface PageMeta {
  metaVersion?: number;
  id: string;
  appId?: string;
  path: string;
  name: string;
  type: string;
  rootId: string;
  layout?: Record<string, any>;
  nodes: ComponentNode[];
  datasourceOverrides?: Record<string, string>; // componentId -> datasourceId (optional future use)
}

export interface BindingSpec {
  kind: 'schemaField' | 'apiResult' | 'formState' | 'expression' | 'globalState' | 'pageParam';
  ref?: string;
  expr?: string;
  transform?: string; // name of transform pipeline (future)
}

export interface ActionSpec {
  type: string;
  config?: Record<string, any>;
  conditions?: ConditionSpec[];
}
export interface ConditionSpec { expr: string; }

export interface SecuritySpec {
  roles?: string[];
  hideIfUnauthorized?: boolean;
}

export interface StyleSpec {
  classes?: string[];
  inline?: Record<string, string>;
}

export interface DatasourceMeta {
  id: string;                 // unique within project scope
  scope: 'project' | 'app';   // inheritance boundary
  name: string;
  // High-level category of datasource
  category: 'relational' | 'nosql' | 'rest' | 'soap' | 'mcp';
  // For relational: specific RDBMS / driver inference (back-compat)
  type?: 'h2' | 'postgres' | 'mysql' | 'mariadb' | 'mssql' | 'oracle' | 'sqlite' | 'custom';
  driver?: string;
  // Relational connection details
  connection?: {
    jdbcUrl?: string | null;
    parts?: { host?: string; port?: number; database?: string; params?: string };
  };
  // NoSQL specific (design-time only for now)
  nosql?: {
    engine?: 'mongodb' | 'dynamodb' | 'cassandra' | 'redis' | 'elastic' | 'neo4j' | string;
    connectionString?: string;         // e.g. mongodb+srv://...
    database?: string;
    options?: Record<string, any>;
  };
  // REST / SOAP / MCP API style config (design-time only initial)
  api?: {
    baseUrl?: string;                  // REST base or SOAP endpoint base
    wsdlUrl?: string;                  // SOAP only
    protocol?: 'http' | 'https' | 'tcp' | 'udp';
    headers?: Record<string, string>;
    authType?: 'none' | 'basic' | 'bearer' | 'apiKey' | 'custom';
    apiKeyLocation?: 'header' | 'query' | 'cookie';
    apiKeyName?: string;
    apiKeyValueRef?: string;           // secret reference
    bearerTokenRef?: string;           // secret reference
    basicUserRef?: string;             // secret reference
    basicPassRef?: string;             // secret reference
    timeoutMs?: number;
    retry?: { attempts?: number; backoffMs?: number; }; // client retry policy
    mcpEndpoint?: string;              // MCP (Message / Control / Custom Protocol) endpoint identifier
  };
  auth?: { username?: string; passwordRef?: string };
  pool?: { maxPoolSize?: number; minIdle?: number; connectionTimeoutMs?: number };
  defaultForApp?: boolean;
  managed?: boolean; // whether migrations (relational only) are applied automatically
  env?: Record<string, Partial<Pick<DatasourceMeta, 'connection' | 'auth' | 'pool' | 'api' | 'nosql'>>>; // environment overrides
  rolesAllowed?: string[]; // who may bind schemas to this datasource
  notes?: string;          // free-form documentation
}

// Simple runtime context shape (will expand later)
export interface RuntimeContext {
  appState: Record<string, any>;
  pageState: Record<string, any>;
  bindingsDisabled?: boolean;
}

export type MetadataEntity = ComponentNode | PageMeta | DatasourceMeta;
