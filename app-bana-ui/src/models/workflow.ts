/**
 * Workflow Automation Type Definitions
 * Core types for state machines, workflows, triggers, and actions
 */

/**
 * State in a state machine
 */
export interface State {
    id: string;
    name: string;
    color?: string;
    description?: string;
    position?: { x: number; y: number };
    type?: 'state' | 'decision' | 'start' | 'end'; // Node type for visual distinction
}

/**
 * Transition between states with conditional logic
 */
export interface Transition {
    id: string;
    from: string;  // state id
    to: string;    // state id
    label?: string;

    // Conditional logic
    condition?: TransitionCondition;

    // Role-based security
    roles?: string[];  // Roles allowed to trigger this transition

    // Priority for evaluation (lower number = higher priority)
    priority?: number;

    // Mark as fallback (ELSE condition)
    isFallback?: boolean;

    // Analytics
    analytics?: {
        timesEvaluated: number;
        timesTrue: number;
        timesFalse: number;
        avgExecutionTime: number;
    };
}

/**
 * Condition for a transition
 */
export interface TransitionCondition {
    // Expression to evaluate (e.g., "amount > 10000")
    expression: string;

    // Human-readable description
    description?: string;

    // Natural language representation
    naturalLanguage?: string;

    // Fields referenced (for validation)
    fields?: string[];

    // Operator used
    operator?: ConditionOperator;

    // Field being compared
    field?: string;

    // Value to compare against
    value?: any;
}

/**
 * Condition operators
 */
export type ConditionOperator =
    | 'equals'
    | 'notEquals'
    | 'greaterThan'
    | 'lessThan'
    | 'greaterThanOrEqual'
    | 'lessThanOrEqual'
    | 'contains'
    | 'notContains'
    | 'startsWith'
    | 'endsWith'
    | 'isEmpty'
    | 'isNotEmpty'
    | 'in'
    | 'notIn';

/**
 * Field type for condition validation
 */
export type FieldType =
    | 'string'
    | 'number'
    | 'boolean'
    | 'date'
    | 'datetime'
    | 'array'
    | 'object';

/**
 * State Machine definition
 */
export interface StateMachine {
    id: string;
    name: string;
    states: State[];
    transitions: Transition[];
    initialState: string;  // state id
    entityName?: string;   // Which entity this applies to
    statusField?: string;  // Which field stores the state (default: 'status')
}

/**
 * Workflow trigger types
 */
export type WorkflowTriggerEvent =
    | 'onCreate'
    | 'onUpdate'
    | 'onDelete'
    | 'onSchedule'
    | 'onStateChange';

/**
 * Workflow trigger configuration
 */
export interface WorkflowTrigger {
    event: WorkflowTriggerEvent;
    entityName?: string;
    condition?: string;  // JS expression like "amount > 10000"
    schedule?: string;   // Cron expression for scheduled triggers
    stateTransition?: {  // For onStateChange triggers
        from?: string;
        to?: string;
    };
}

/**
 * Workflow action types
 */
export type WorkflowActionType =
    | 'updateField'
    | 'sendEmail'
    | 'createRecord'
    | 'deleteRecord'
    | 'runApproval'
    | 'webhook'
    | 'notification';

/**
 * Base workflow action
 */
export interface WorkflowAction {
    id: string;
    type: WorkflowActionType;
    config: Record<string, any>;
    onError?: 'continue' | 'stop' | 'retry';
    retryAttempts?: number;
}

/**
 * Update field action
 */
export interface UpdateFieldAction extends WorkflowAction {
    type: 'updateField';
    config: {
        entityName: string;
        recordId?: string;  // Or use trigger record
        field: string;
        value: any;
    };
}

/**
 * Send email action
 */
export interface SendEmailAction extends WorkflowAction {
    type: 'sendEmail';
    config: {
        to: string | string[];
        cc?: string | string[];
        subject: string;
        templateId?: string;
        templateData?: Record<string, any>;
        body?: string;
    };
}

/**
 * Create record action
 */
export interface CreateRecordAction extends WorkflowAction {
    type: 'createRecord';
    config: {
        entityName: string;
        data: Record<string, any>;
    };
}

/**
 * Webhook action
 */
export interface WebhookAction extends WorkflowAction {
    type: 'webhook';
    config: {
        url: string;
        method: 'GET' | 'POST' | 'PUT' | 'DELETE';
        headers?: Record<string, string>;
        body?: any;
        retryPolicy?: {
            maxAttempts: number;
            backoff: 'linear' | 'exponential';
        };
    };
}

/**
 * Complete workflow definition
 */
export interface WorkflowDefinition {
    id: string;
    name: string;
    description?: string;
    entityName?: string;
    trigger: WorkflowTrigger;
    actions: WorkflowAction[];
    enabled: boolean;
    createdAt: string;
    updatedAt: string;
    createdBy?: string;
}

/**
 * Approval process definition
 */
export interface ApprovalProcess {
    id: string;
    name: string;
    entityName: string;
    steps: ApprovalStep[];
    onFinalApprove?: WorkflowAction[];
    onFinalReject?: WorkflowAction[];
}

/**
 * Single approval step
 */
export interface ApprovalStep {
    id: string;
    name: string;
    approverRole?: string;
    approverField?: string;  // Field containing approver user ID
    condition?: string;
    requireAll?: boolean;  // If multiple approvers, require all?
    actions: {
        onApprove?: WorkflowAction[];
        onReject?: WorkflowAction[];
    };
}

/**
 * Approval instance (runtime)
 */
export interface ApprovalInstance {
    id: string;
    processId: string;
    recordId: string;
    entityName: string;
    currentStep: number;
    status: 'pending' | 'approved' | 'rejected';
    history: ApprovalHistoryEntry[];
    createdAt: string;
    updatedAt: string;
}

/**
 * Approval history entry
 */
export interface ApprovalHistoryEntry {
    stepId: string;
    approverId: string;
    decision: 'approve' | 'reject';
    comment?: string;
    timestamp: string;
}

/**
 * Workflow execution log
 */
export interface WorkflowExecutionLog {
    id: string;
    workflowId: string;
    triggeredBy: {
        event: string;
        recordId?: string;
        userId?: string;
    };
    status: 'running' | 'completed' | 'failed';
    actions: ActionExecutionLog[];
    startedAt: string;
    completedAt?: string;
    error?: string;
}

/**
 * Action execution log
 */
export interface ActionExecutionLog {
    actionId: string;
    actionType: string;
    status: 'pending' | 'running' | 'completed' | 'failed';
    result?: any;
    error?: string;
    startedAt: string;
    completedAt?: string;
}

/**
 * Email template
 */
export interface EmailTemplate {
    id: string;
    name: string;
    subject: string;
    body: string;
    variables: string[];  // List of allowed variables like {orderId}
    htmlBody?: string;
    attachments?: string[];
}

/**
 * Scheduled job definition
 */
export interface ScheduledJob {
    id: string;
    name: string;
    schedule: string;  // Cron expression
    workflow?: WorkflowDefinition;
    action?: WorkflowAction;
    enabled: boolean;
    lastRun?: string;
    nextRun?: string;
}
