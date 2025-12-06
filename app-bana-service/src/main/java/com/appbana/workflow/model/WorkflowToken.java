package com.appbana.workflow.model;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * Workflow Token Entity
 * Represents current position(s) in workflow graph
 * USER_TASK tokens pause execution until user completes task
 * SERVICE_TASK tokens execute immediately and auto-transition
 */
@Data
public class WorkflowToken {
    private String id;
    private String workflowInstanceId;
    private String nodeId;               // Current node in workflow graph
    private NodeType nodeType;           // USER_TASK | SERVICE_TASK | DECISION | WAIT | START | END
    private TokenStatus status;          // ACTIVE | COMPLETED | FAILED | CANCELLED
    
    // Assignment fields (for USER_TASK only)
    private AssignmentType assignmentType;  // USER | ROLE | QUEUE | DYNAMIC | null
    private String assignedUserId;       // Specific user ID
    private String assignedRole;         // Role name
    private String assignedQueue;        // Queue name
    
    // SLA fields
    private LocalDateTime dueAt;         // SLA deadline
    private SlaStatus slaStatus;         // ON_TIME | OVERDUE | ESCALATED
    
    // Execution metadata
    private LocalDateTime arrivedAt;
    private LocalDateTime completedAt;
    private String completedBy;          // User who completed task (for audit)
    private String outcome;              // Transition taken (e.g., "APPROVE", "REJECT")
    private String taskData;             // JSON data for task (form inputs, comments)

    public enum NodeType {
        START,
        END,
        USER_TASK,
        SERVICE_TASK,
        DECISION,
        WAIT
    }

    public enum TokenStatus {
        ACTIVE,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    public enum AssignmentType {
        USER,       // Static user assignment
        ROLE,       // All users in role can claim
        QUEUE,      // Work queue (pool)
        DYNAMIC     // Expression-based (e.g., ${Order.createdBy.manager})
    }

    public enum SlaStatus {
        ON_TIME,
        OVERDUE,
        ESCALATED
    }
}
