package com.appbana.workflow.model;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * Workflow Definition Entity
 * Stores workflow templates with versioning support
 * Instances lock to specific version on creation (prevents breaking changes)
 */
@Data
public class WorkflowDefinition {
    private String id;
    private String appId;
    private String name;
    private String description;
    private String triggerEntity;        // Entity name (e.g., "PaymentRequest")
    private String triggerEvent;         // ON_CREATE | ON_UPDATE | ON_DELETE | MANUAL
    private String triggerCondition;     // MVEL expression
    private Integer version;             // Auto-incremented on publish
    private WorkflowStatus status;       // DRAFT | ACTIVE | ARCHIVED
    private String definitionJson;       // Full workflow JSON (nodes, transitions)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    public enum WorkflowStatus {
        DRAFT,      // Being edited, not executable
        ACTIVE,     // Published, can be triggered
        ARCHIVED    // Deprecated, no new instances allowed
    }

    public enum TriggerEvent {
        ON_CREATE,
        ON_UPDATE,
        ON_DELETE,
        MANUAL
    }
}
