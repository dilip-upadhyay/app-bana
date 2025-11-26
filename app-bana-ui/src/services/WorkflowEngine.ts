/**
 * Workflow Runtime Engine
 * Executes state transitions, validates permissions, and fires workflow actions
 */

import type {
    StateMachine,
    Transition,
    WorkflowDefinition,
    WorkflowExecutionLog,
    ActionExecutionLog,
    WorkflowAction
} from '../models/workflow';
import { workflowStorage } from './WorkflowStorage';

/**
 * State Transition Service
 * Handles state machine execution and validation
 */
export class StateTransitionService {

    /**
     * Check if a transition is allowed
     */
    canTransition(
        machine: StateMachine,
        currentState: string,
        targetState: string,
        userRoles: string[] = []
    ): { allowed: boolean; reason?: string } {

        // Find the transition
        const transition = machine.transitions.find(
            t => t.from === currentState && t.to === targetState
        );

        if (!transition) {
            return {
                allowed: false,
                reason: 'No transition defined between these states'
            };
        }

        // Check role permissions
        if (transition.roles && transition.roles.length > 0) {
            const hasRole = transition.roles.some(role => userRoles.includes(role));
            if (!hasRole) {
                return {
                    allowed: false,
                    reason: `User must have one of these roles: ${transition.roles.join(', ')}`
                };
            }
        }

        // TODO: Evaluate condition if present
        if (transition.condition) {
            // For now, we'll just allow it
            // In the future, evaluate the condition expression
        }

        return { allowed: true };
    }

    /**
     * Execute a state transition
     */
    async executeTransition(
        entityName: string,
        recordId: string,
        targetState: string,
        userRoles: string[] = []
    ): Promise<{ success: boolean; error?: string }> {

        // Load the state machine for this entity
        const machine = await workflowStorage.getStateMachineByEntity(entityName);
        if (!machine) {
            return { success: false, error: 'No state machine defined for this entity' };
        }

        // Get current state from the record
        // TODO: Fetch actual record and get current state
        // For now, we'll use initial state as a placeholder
        const currentState = machine.initialState;

        // Validate transition
        const validation = this.canTransition(machine, currentState, targetState, userRoles);
        if (!validation.allowed) {
            return { success: false, error: validation.reason };
        }

        // Update the record's state
        // TODO: Call API to update record
        console.log(`Transitioning ${entityName}#${recordId} from ${currentState} to ${targetState}`);

        // Emit state change event
        this.emitStateChangeEvent(entityName, recordId, currentState, targetState);

        // Trigger any workflows listening for this state change
        await this.triggerStateChangeWorkflows(entityName, recordId, currentState, targetState);

        return { success: true };
    }

    /**
     * Emit state change event
     */
    private emitStateChangeEvent(
        entityName: string,
        recordId: string,
        from: string,
        to: string
    ) {
        const event = new CustomEvent('state-changed', {
            detail: { entityName, recordId, from, to },
            bubbles: true,
            composed: true
        });

        window.dispatchEvent(event);
    }

    /**
     * Trigger workflows for state changes
     */
    private async triggerStateChangeWorkflows(
        entityName: string,
        recordId: string,
        from: string,
        to: string
    ) {
        const workflows = await workflowStorage.getWorkflowsByEntity(entityName);

        for (const workflow of workflows) {
            if (!workflow.enabled) continue;

            // Check if this workflow listens for state changes
            if (workflow.trigger.event === 'onStateChange') {
                const stateTransition = workflow.trigger.stateTransition;

                // Check if this specific transition matches
                if (
                    (!stateTransition?.from || stateTransition.from === from) &&
                    (!stateTransition?.to || stateTransition.to === to)
                ) {
                    // Execute the workflow
                    await workflowEngine.executeWorkflow(workflow, { entityName, recordId });
                }
            }
        }
    }
}

/**
 * Workflow Execution Engine
 * Executes workflow actions in sequence
 */
export class WorkflowEngine {

    /**
     * Execute a workflow
     */
    async executeWorkflow(
        workflow: WorkflowDefinition,
        context: { entityName?: string; recordId?: string; data?: any }
    ): Promise<WorkflowExecutionLog> {

        const log: WorkflowExecutionLog = {
            id: this.generateLogId(),
            workflowId: workflow.id,
            triggeredBy: {
                event: workflow.trigger.event,
                recordId: context.recordId,
                userId: 'current-user' // TODO: Get from auth context
            },
            status: 'running',
            actions: [],
            startedAt: new Date().toISOString()
        };

        try {
            // Execute actions in sequence
            for (const action of workflow.actions) {
                const actionLog = await this.executeAction(action, context);
                log.actions.push(actionLog);

                // Stop on failure if configured
                if (actionLog.status === 'failed' && action.onError === 'stop') {
                    log.status = 'failed';
                    log.error = actionLog.error;
                    break;
                }
            }

            if (log.status === 'running') {
                log.status = 'completed';
            }
        } catch (error) {
            log.status = 'failed';
            log.error = error instanceof Error ? error.message : 'Unknown error';
        } finally {
            log.completedAt = new Date().toISOString();
        }

        // Save execution log
        await workflowStorage.saveExecutionLog(log);

        return log;
    }

    /**
     * Execute a single action
     */
    private async executeAction(
        action: WorkflowAction,
        context: any
    ): Promise<ActionExecutionLog> {

        const actionLog: ActionExecutionLog = {
            actionId: action.id,
            actionType: action.type,
            status: 'running',
            startedAt: new Date().toISOString()
        };

        try {
            switch (action.type) {
                case 'updateField':
                    actionLog.result = await this.executeUpdateField(action, context);
                    break;

                case 'sendEmail':
                    actionLog.result = await this.executeSendEmail(action, context);
                    break;

                case 'createRecord':
                    actionLog.result = await this.executeCreateRecord(action, context);
                    break;

                case 'webhook':
                    actionLog.result = await this.executeWebhook(action, context);
                    break;

                default:
                    throw new Error(`Unknown action type: ${action.type}`);
            }

            actionLog.status = 'completed';
        } catch (error) {
            actionLog.status = 'failed';
            actionLog.error = error instanceof Error ? error.message : 'Unknown error';
        } finally {
            actionLog.completedAt = new Date().toISOString();
        }

        return actionLog;
    }

    /**
     * Execute updateField action
     */
    private async executeUpdateField(action: WorkflowAction, context: any): Promise<any> {
        const config = action.config;

        console.log(`[Update Field] ${config.entityName}.${config.field} = ${config.value}`);

        // TODO: Call API to update field
        // await apiClient.updateRecord(config.entityName, context.recordId, {
        //   [config.field]: config.value
        // });

        return { updated: true };
    }

    /**
     * Execute sendEmail action
     */
    private async executeSendEmail(action: WorkflowAction, context: any): Promise<any> {
        const config = action.config;

        console.log(`[Send Email] To: ${config.to}, Subject: ${config.subject}`);

        // TODO: Integrate with email service
        // Load template if templateId is provided
        if (config.templateId) {
            const template = await workflowStorage.getEmailTemplate(config.templateId);
            // Render template with data
        }

        // For now, just log
        return { sent: true, to: config.to };
    }

    /**
     * Execute createRecord action
     */
    private async executeCreateRecord(action: WorkflowAction, context: any): Promise<any> {
        const config = action.config;

        console.log(`[Create Record] ${config.entityName}`, config.data);

        // TODO: Call API to create record
        // const newRecord = await apiClient.createRecord(config.entityName, config.data);

        return { created: true };
    }

    /**
     * Execute webhook action
     */
    private async executeWebhook(action: WorkflowAction, context: any): Promise<any> {
        const config = action.config;

        console.log(`[Webhook] ${config.method} ${config.url}`);

        try {
            const response = await fetch(config.url, {
                method: config.method,
                headers: config.headers || {},
                body: config.body ? JSON.stringify(config.body) : undefined
            });

            return {
                success: response.ok,
                status: response.status,
                data: await response.json()
            };
        } catch (error) {
            throw new Error(`Webhook failed: ${error instanceof Error ? error.message : 'Unknown error'}`);
        }
    }

    /**
     * Evaluate a condition expression
     */
    evaluateCondition(condition: string, context: any): boolean {
        // Simple expression evaluation
        // For now, we'll use a basic approach
        // In production, use a proper expression parser like jsep

        try {
            // WARNING: eval is dangerous, use proper parser in production
            const func = new Function('context', `with(context) { return ${condition}; }`);
            return func(context);
        } catch (error) {
            console.error('Condition evaluation error:', error);
            return false;
        }
    }

    private generateLogId(): string {
        return `log_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    }
}

// Export singleton instances
export const stateTransitionService = new StateTransitionService();
export const workflowEngine = new WorkflowEngine();
