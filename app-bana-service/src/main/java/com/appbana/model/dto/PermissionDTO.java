package com.appbana.model.dto;

import java.time.LocalDateTime;

/**
 * Immutable DTO for Permission API responses.
 * Uses Java 21 record for clean, immutable data transfer.
 * 
 * <p>Permission Format: resource:action:scope</p>
 * <ul>
 *   <li>Resource: Entity or feature being accessed (e.g., "user", "app", "*")</li>
 *   <li>Action: Operation type (e.g., "create", "read", "update", "delete", "*")</li>
 *   <li>Scope: Access level (e.g., "all", "own", "team")</li>
 * </ul>
 * 
 * @param id Permission database ID
 * @param resource Resource being protected (wildcard "*" for all)
 * @param action Action allowed (wildcard "*" for all)
 * @param scope Access scope ("all", "own", "team")
 * @param createdAt Creation timestamp
 */
public record PermissionDTO(
    Long id,
    String resource,
    String action,
    String scope,
    LocalDateTime createdAt
) {
    /**
     * Compact constructor for validation.
     */
    public PermissionDTO {
        if (resource == null || resource.isBlank()) {
            throw new IllegalArgumentException("Resource cannot be null or empty");
        }
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("Action cannot be null or empty");
        }
        if (scope == null || scope.isBlank()) {
            scope = "all"; // Default scope
        }
    }
    
    /**
     * Check if this is a wildcard admin permission.
     */
    public boolean isWildcard() {
        return "*".equals(resource) && "*".equals(action);
    }
    
    /**
     * Get permission string in format: resource:action:scope
     */
    public String toPermissionString() {
        return resource + ":" + action + ":" + scope;
    }
    
    /**
     * Factory method from Permission entity.
     */
    public static PermissionDTO fromPermission(com.appbana.model.Permission permission) {
        return new PermissionDTO(
            permission.getId(),
            permission.getResource(),
            permission.getAction(),
            permission.getScope(),
            permission.getCreatedAt()
        );
    }
}
