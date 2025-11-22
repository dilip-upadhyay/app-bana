package com.appbana.model.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Immutable DTO for Role API responses.
 * Uses Java 21 record for concise, type-safe data transfer.
 * 
 * @param id Role database ID
 * @param name Role name (e.g., "admin", "manager", "user")
 * @param description Human-readable description of the role
 * @param isSystem Whether this is a system role (cannot be deleted)
 * @param createdAt Creation timestamp
 * @param updatedAt Last update timestamp
 * @param permissionIds List of permission IDs assigned to this role (optional)
 */
public record RoleDTO(
    Long id,
    String name,
    String description,
    boolean isSystem,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<Long> permissionIds
) {
    /**
     * Compact constructor for validation.
     */
    public RoleDTO {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Role name cannot be null or empty");
        }
        // Defensive copy for mutable list
        if (permissionIds != null) {
            permissionIds = List.copyOf(permissionIds);
        }
    }
    
    /**
     * Check if this is an admin role.
     */
    public boolean isAdmin() {
        return "admin".equalsIgnoreCase(name);
    }
    
    /**
     * Factory method from Role entity.
     */
    public static RoleDTO fromRole(com.appbana.model.Role role) {
        List<Long> permIds = role.getPermissions() != null 
            ? role.getPermissions().stream()
                .map(com.appbana.model.Permission::getId)
                .toList()
            : List.of();
        
        return new RoleDTO(
            role.getId(),
            role.getName(),
            role.getDescription(),
            role.isSystem(),
            role.getCreatedAt(),
            role.getUpdatedAt(),
            permIds
        );
    }
}
