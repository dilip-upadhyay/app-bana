/**
 * Workflow Storage Service
 * Handles persistence and retrieval of workflows, state machines, and approvals
 */

import type {
    WorkflowDefinition,
    StateMachine,
    ApprovalProcess,
    ApprovalInstance,
    WorkflowExecutionLog,
    EmailTemplate,
    ScheduledJob
} from '../models/workflow';

/**
 * Storage keys for localStorage
 */
const STORAGE_KEYS = {
    WORKFLOWS: 'appbana_workflows',
    STATE_MACHINES: 'appbana_state_machines',
    APPROVALS: 'appbana_approval_processes',
    APPROVAL_INSTANCES: 'appbana_approval_instances',
    EXECUTION_LOGS: 'appbana_workflow_logs',
    EMAIL_TEMPLATES: 'appbana_email_templates',
    SCHEDULED_JOBS: 'appbana_scheduled_jobs'
} as const;

/**
 * Workflow Storage Service
 */
export class WorkflowStorageService {

    // ============ Workflows ============

    /**
     * Get all workflows
     */
    async getWorkflows(): Promise<WorkflowDefinition[]> {
        const data = localStorage.getItem(STORAGE_KEYS.WORKFLOWS);
        return data ? JSON.parse(data) : [];
    }

    /**
     * Get workflow by ID
     */
    async getWorkflow(id: string): Promise<WorkflowDefinition | null> {
        const workflows = await this.getWorkflows();
        return workflows.find(w => w.id === id) || null;
    }

    /**
     * Get workflows for an entity
     */
    async getWorkflowsByEntity(entityName: string): Promise<WorkflowDefinition[]> {
        const workflows = await this.getWorkflows();
        return workflows.filter(w => w.entityName === entityName);
    }

    /**
     * Save workflow
     */
    async saveWorkflow(workflow: WorkflowDefinition): Promise<WorkflowDefinition> {
        const workflows = await this.getWorkflows();
        const index = workflows.findIndex(w => w.id === workflow.id);

        const now = new Date().toISOString();
        const updatedWorkflow = {
            ...workflow,
            updatedAt: now,
            createdAt: workflow.createdAt || now
        };

        if (index >= 0) {
            workflows[index] = updatedWorkflow;
        } else {
            workflows.push(updatedWorkflow);
        }

        localStorage.setItem(STORAGE_KEYS.WORKFLOWS, JSON.stringify(workflows));
        return updatedWorkflow;
    }

    /**
     * Delete workflow
     */
    async deleteWorkflow(id: string): Promise<boolean> {
        const workflows = await this.getWorkflows();
        const filtered = workflows.filter(w => w.id !== id);

        if (filtered.length === workflows.length) {
            return false;  // Not found
        }

        localStorage.setItem(STORAGE_KEYS.WORKFLOWS, JSON.stringify(filtered));
        return true;
    }

    // ============ State Machines ============

    /**
     * Get all state machines
     */
    async getStateMachines(): Promise<StateMachine[]> {
        const data = localStorage.getItem(STORAGE_KEYS.STATE_MACHINES);
        return data ? JSON.parse(data) : [];
    }

    /**
     * Get state machine by ID
     */
    async getStateMachine(id: string): Promise<StateMachine | null> {
        const machines = await this.getStateMachines();
        return machines.find(m => m.id === id) || null;
    }

    /**
     * Get state machine for an entity
     */
    async getStateMachineByEntity(entityName: string): Promise<StateMachine | null> {
        const machines = await this.getStateMachines();
        return machines.find(m => m.entityName === entityName) || null;
    }

    /**
     * Save state machine
     */
    async saveStateMachine(machine: StateMachine): Promise<StateMachine> {
        const machines = await this.getStateMachines();
        const index = machines.findIndex(m => m.id === machine.id);

        if (index >= 0) {
            machines[index] = machine;
        } else {
            machines.push(machine);
        }

        localStorage.setItem(STORAGE_KEYS.STATE_MACHINES, JSON.stringify(machines));
        return machine;
    }

    /**
     * Delete state machine
     */
    async deleteStateMachine(id: string): Promise<boolean> {
        const machines = await this.getStateMachines();
        const filtered = machines.filter(m => m.id !== id);

        if (filtered.length === machines.length) {
            return false;
        }

        localStorage.setItem(STORAGE_KEYS.STATE_MACHINES, JSON.stringify(filtered));
        return true;
    }

    // ============ Approval Processes ============

    /**
     * Get all approval processes
     */
    async getApprovalProcesses(): Promise<ApprovalProcess[]> {
        const data = localStorage.getItem(STORAGE_KEYS.APPROVALS);
        return data ? JSON.parse(data) : [];
    }

    /**
     * Get approval process by ID
     */
    async getApprovalProcess(id: string): Promise<ApprovalProcess | null> {
        const processes = await this.getApprovalProcesses();
        return processes.find(p => p.id === id) || null;
    }

    /**
     * Save approval process
     */
    async saveApprovalProcess(process: ApprovalProcess): Promise<ApprovalProcess> {
        const processes = await this.getApprovalProcesses();
        const index = processes.findIndex(p => p.id === process.id);

        if (index >= 0) {
            processes[index] = process;
        } else {
            processes.push(process);
        }

        localStorage.setItem(STORAGE_KEYS.APPROVALS, JSON.stringify(processes));
        return process;
    }

    /**
     * Delete approval process
     */
    async deleteApprovalProcess(id: string): Promise<boolean> {
        const processes = await this.getApprovalProcesses();
        const filtered = processes.filter(p => p.id !== id);

        if (filtered.length === processes.length) {
            return false;
        }

        localStorage.setItem(STORAGE_KEYS.APPROVALS, JSON.stringify(filtered));
        return true;
    }

    // ============ Approval Instances (Runtime) ============

    /**
     * Get all approval instances
     */
    async getApprovalInstances(): Promise<ApprovalInstance[]> {
        const data = localStorage.getItem(STORAGE_KEYS.APPROVAL_INSTANCES);
        return data ? JSON.parse(data) : [];
    }

    /**
     * Get approval instance by ID
     */
    async getApprovalInstance(id: string): Promise<ApprovalInstance | null> {
        const instances = await this.getApprovalInstances();
        return instances.find(i => i.id === id) || null;
    }

    /**
     * Get pending approvals for a user
     */
    async getPendingApprovalsForUser(userId: string): Promise<ApprovalInstance[]> {
        const instances = await this.getApprovalInstances();
        return instances.filter(i => i.status === 'pending');
        // TODO: Filter by approver role/user
    }

    /**
     * Save approval instance
     */
    async saveApprovalInstance(instance: ApprovalInstance): Promise<ApprovalInstance> {
        const instances = await this.getApprovalInstances();
        const index = instances.findIndex(i => i.id === instance.id);

        const now = new Date().toISOString();
        const updated = {
            ...instance,
            updatedAt: now,
            createdAt: instance.createdAt || now
        };

        if (index >= 0) {
            instances[index] = updated;
        } else {
            instances.push(updated);
        }

        localStorage.setItem(STORAGE_KEYS.APPROVAL_INSTANCES, JSON.stringify(instances));
        return updated;
    }

    // ============ Execution Logs ============

    /**
     * Get execution logs
     */
    async getExecutionLogs(workflowId?: string): Promise<WorkflowExecutionLog[]> {
        const data = localStorage.getItem(STORAGE_KEYS.EXECUTION_LOGS);
        const logs: WorkflowExecutionLog[] = data ? JSON.parse(data) : [];

        if (workflowId) {
            return logs.filter(log => log.workflowId === workflowId);
        }

        return logs;
    }

    /**
     * Save execution log
     */
    async saveExecutionLog(log: WorkflowExecutionLog): Promise<WorkflowExecutionLog> {
        const logs = await this.getExecutionLogs();
        const index = logs.findIndex(l => l.id === log.id);

        if (index >= 0) {
            logs[index] = log;
        } else {
            logs.push(log);
        }

        // Keep only last 100 logs to avoid storage bloat
        const trimmed = logs.slice(-100);
        localStorage.setItem(STORAGE_KEYS.EXECUTION_LOGS, JSON.stringify(trimmed));

        return log;
    }

    // ============ Email Templates ============

    /**
     * Get all email templates
     */
    async getEmailTemplates(): Promise<EmailTemplate[]> {
        const data = localStorage.getItem(STORAGE_KEYS.EMAIL_TEMPLATES);
        return data ? JSON.parse(data) : [];
    }

    /**
     * Get email template by ID
     */
    async getEmailTemplate(id: string): Promise<EmailTemplate | null> {
        const templates = await this.getEmailTemplates();
        return templates.find(t => t.id === id) || null;
    }

    /**
     * Save email template
     */
    async saveEmailTemplate(template: EmailTemplate): Promise<EmailTemplate> {
        const templates = await this.getEmailTemplates();
        const index = templates.findIndex(t => t.id === template.id);

        if (index >= 0) {
            templates[index] = template;
        } else {
            templates.push(template);
        }

        localStorage.setItem(STORAGE_KEYS.EMAIL_TEMPLATES, JSON.stringify(templates));
        return template;
    }

    /**
     * Delete email template
     */
    async deleteEmailTemplate(id: string): Promise<boolean> {
        const templates = await this.getEmailTemplates();
        const filtered = templates.filter(t => t.id !== id);

        if (filtered.length === templates.length) {
            return false;
        }

        localStorage.setItem(STORAGE_KEYS.EMAIL_TEMPLATES, JSON.stringify(filtered));
        return true;
    }

    // ============ Scheduled Jobs ============

    /**
     * Get all scheduled jobs
     */
    async getScheduledJobs(): Promise<ScheduledJob[]> {
        const data = localStorage.getItem(STORAGE_KEYS.SCHEDULED_JOBS);
        return data ? JSON.parse(data) : [];
    }

    /**
     * Save scheduled job
     */
    async saveScheduledJob(job: ScheduledJob): Promise<ScheduledJob> {
        const jobs = await this.getScheduledJobs();
        const index = jobs.findIndex(j => j.id === job.id);

        if (index >= 0) {
            jobs[index] = job;
        } else {
            jobs.push(job);
        }

        localStorage.setItem(STORAGE_KEYS.SCHEDULED_JOBS, JSON.stringify(jobs));
        return job;
    }

    /**
     * Clear all workflow data (for testing)
     */
    async clearAll(): Promise<void> {
        Object.values(STORAGE_KEYS).forEach(key => {
            localStorage.removeItem(key);
        });
    }
}

// Export singleton instance
export const workflowStorage = new WorkflowStorageService();
