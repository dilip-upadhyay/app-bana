package com.appbana.workflow.model;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * Workflow Instance Entity
 * Tracks each workflow execution (one per entity)
 * Locks to workflow version on creation (isolation from definition changes)
 */
@Data
public class WorkflowInstance {
    private String id;
    private String workflowDefinitionId;
    private Integer workflowVersion;     // Locked version (never changes after creation)
    private String appId;
    private String entityId;             // ID of entity that triggered workflow
    private String entityType;           // Entity name (denormalized for queries)
    private InstanceStatus status;       // RUNNING | COMPLETED | FAILED | CANCELLED
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String errorMessage;
    private String contextData;          // JSON snapshot of entity data at start
    private String createdBy;

    public enum InstanceStatus {
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }
}
