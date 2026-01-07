package com.appbana.workflow.model.dto;

import com.appbana.workflow.model.WorkflowToken;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

/**
 * Workflow Token DTO for API responses (My Tasks)
 * Uses Java 21 record for immutability
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowTokenDTO(
    String id,
    String workflowInstanceId,
    String nodeId,
    String nodeType,
    String status,
    String assignmentType,
    String assignedUserId,
    String assignedRole,
    String assignedQueue,
    LocalDateTime dueAt,
    String slaStatus,
    LocalDateTime arrivedAt,
    LocalDateTime completedAt,
    String completedBy,
    String outcome,
    String taskData
) {
    public static WorkflowTokenDTO fromEntity(WorkflowToken entity) {
        return new WorkflowTokenDTO(
            entity.getId(),
            entity.getWorkflowInstanceId(),
            entity.getNodeId(),
            entity.getNodeType() != null ? entity.getNodeType().name() : null,
            entity.getStatus() != null ? entity.getStatus().name() : null,
            entity.getAssignmentType() != null ? entity.getAssignmentType().name() : null,
            entity.getAssignedUserId(),
            entity.getAssignedRole(),
            entity.getAssignedQueue(),
            entity.getDueAt(),
            entity.getSlaStatus() != null ? entity.getSlaStatus().name() : null,
            entity.getArrivedAt(),
            entity.getCompletedAt(),
            entity.getCompletedBy(),
            entity.getOutcome(),
            entity.getTaskData()
        );
    }
}
