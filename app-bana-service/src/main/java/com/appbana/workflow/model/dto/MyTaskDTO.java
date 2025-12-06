package com.appbana.workflow.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

/**
 * My Task DTO - Extended view for user task list
 * Includes workflow context and entity metadata
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MyTaskDTO(
    String tokenId,
    String nodeId,
    String nodeLabel,
    String assignedUserId,
    String assignedRole,
    String assignedQueue,
    LocalDateTime dueAt,
    String slaStatus,
    LocalDateTime arrivedAt,
    String instanceId,
    String entityId,
    String entityType,
    String contextData,
    String workflowName,
    String workflowDescription,
    String appId
) {
    // Constructor from database view v_my_active_tasks
    public static MyTaskDTO fromResultSet(
        String tokenId,
        String nodeId,
        String assignedUserId,
        String assignedRole,
        String assignedQueue,
        LocalDateTime dueAt,
        String slaStatus,
        LocalDateTime arrivedAt,
        String instanceId,
        String entityId,
        String entityType,
        String contextData,
        String workflowName,
        String workflowDescription,
        String appId
    ) {
        return new MyTaskDTO(
            tokenId, nodeId, null, assignedUserId, assignedRole, assignedQueue,
            dueAt, slaStatus, arrivedAt, instanceId, entityId, entityType,
            contextData, workflowName, workflowDescription, appId
        );
    }
}
