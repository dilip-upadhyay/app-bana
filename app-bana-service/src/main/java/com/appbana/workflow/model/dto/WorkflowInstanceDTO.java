package com.appbana.workflow.model.dto;

import com.appbana.workflow.model.WorkflowInstance;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

/**
 * Workflow Instance DTO for API responses
 * Uses Java 21 record for immutability
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowInstanceDTO(
    String id,
    String workflowDefinitionId,
    Integer workflowVersion,
    String appId,
    String entityId,
    String entityType,
    String status,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    String errorMessage,
    String contextData,
    String createdBy
) {
    public static WorkflowInstanceDTO fromEntity(WorkflowInstance entity) {
        return new WorkflowInstanceDTO(
            entity.getId(),
            entity.getWorkflowDefinitionId(),
            entity.getWorkflowVersion(),
            entity.getAppId(),
            entity.getEntityId(),
            entity.getEntityType(),
            entity.getStatus() != null ? entity.getStatus().name() : null,
            entity.getStartedAt(),
            entity.getCompletedAt(),
            entity.getErrorMessage(),
            entity.getContextData(),
            entity.getCreatedBy()
        );
    }
}
