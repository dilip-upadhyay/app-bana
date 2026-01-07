// Workflow Metadata Type Definitions
// Describes the workflow structure for canvas editing

export interface WorkflowMetadata {
    id: string;
    name: string;
    version: number;
    schemaVersion: string;  // For backward compatibility
    triggerEntity?: string;
    triggerEvent?: string;
    triggerCondition?: string;
    nodes: NodeMetadata[];
    connections: ConnectionMetadata[];
}

export interface NodeMetadata {
    id: string;
    type: NodeType;
    position: Position;
    label: string;
    properties: Record<string, any>;
}

export interface ConnectionMetadata {
    id: string;
    from: string;  // source node id
    to: string;    // target node id
    label?: string;
    condition?: string;  // MVEL expression
}

export interface Position {
    x: number;
    y: number;
}

export type NodeType =
    | 'START'
    | 'END'
    | 'USER_TASK'
    | 'SERVICE_TASK'
    | 'DECISION';

export interface ViewportState {
    zoom: number;
    offsetX: number;
    offsetY: number;
}

export interface SelectionState {
    selectedNodeId?: string;
    selectedConnectionId?: string;
}

// Node template for palette
export interface NodeTemplate {
    type: NodeType;
    label: string;
    icon: string;
    color: string;
    description: string;
}

// Validation result
export interface ValidationResult {
    valid: boolean;
    errors: string[];
    warnings: string[];
}
