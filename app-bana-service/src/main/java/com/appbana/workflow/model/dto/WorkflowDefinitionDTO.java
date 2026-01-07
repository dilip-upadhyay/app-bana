package com.appbana.workflow.model.dto;

import com.appbana.workflow.model.WorkflowDefinition;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

/**
 * Workflow Definition DTO for API responses
 * Uses Java 21 record for immutability
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowDefinitionDTO(
    String id,
    String appId,
    String name,
    String description,
    String triggerEntity,
    String triggerEvent,
    String triggerCondition,
    Integer version,
    String status,
    String definitionJson,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    String createdBy,
    String updatedBy
) {
    public static WorkflowDefinitionDTO fromEntity(WorkflowDefinition entity) {
        return new WorkflowDefinitionDTO(
            entity.getId(),
            entity.getAppId(),
            entity.getName(),
            entity.getDescription(),
            entity.getTriggerEntity(),
            entity.getTriggerEvent(),
            entity.getTriggerCondition(),
            entity.getVersion(),
            entity.getStatus() != null ? entity.getStatus().name() : null,
            entity.getDefinitionJson(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getCreatedBy(),
            entity.getUpdatedBy()
        );
    }
}
