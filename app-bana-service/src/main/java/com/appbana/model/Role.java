package com.appbana.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Role entity representing a user role in AppBana RBAC.
 * Maps to the role table in the database.
 * 
 * <p>Uses Lombok annotations for cleaner code:</p>
 * <ul>
 *   <li>@Data: Auto-generates getters/setters/toString/equals/hashCode</li>
 *   <li>@Builder: Fluent builder pattern for object creation</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Role {
    private Long id;
    private String name;
    private String description;
    
    @Builder.Default
    private boolean isSystem = false; // System roles cannot be deleted
    
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    @Builder.Default
    private List<Permission> permissions = new ArrayList<>(); // Lazy loaded

    // Predefined system roles
    public static final String ADMIN = "admin";
    public static final String MANAGER = "manager";
    public static final String USER = "user";

    /**
     * Add a permission to this role
     */
    public void addPermission(Permission permission) {
        if (!this.permissions.contains(permission)) {
            this.permissions.add(permission);
        }
    }

    /**
     * Remove a permission from this role
     */
    public void removePermission(Permission permission) {
        this.permissions.remove(permission);
    }

    /**
     * Check if this role has a specific permission
     */
    public boolean hasPermission(String resource, String action, String scope) {
        return permissions.stream()
                .anyMatch(p -> p.matches(resource, action, scope));
    }

    /**
     * Check if this role has wildcard admin permission
     */
    public boolean isAdmin() {
        return ADMIN.equals(name) || permissions.stream()
                .anyMatch(p -> "*".equals(p.getResource()) && "*".equals(p.getAction()));
    }
}
