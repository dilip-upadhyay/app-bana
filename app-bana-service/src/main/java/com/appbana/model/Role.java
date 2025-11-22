package com.appbana.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Role entity representing a user role in AppBana.
 * Maps to the role table in the database.
 */
public class Role {
    private Long id;
    private String name;
    private String description;
    private boolean isSystem; // System roles cannot be deleted
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<Permission> permissions; // Lazy loaded

    // Predefined system roles
    public static final String ADMIN = "admin";
    public static final String MANAGER = "manager";
    public static final String USER = "user";

    // Constructors
    public Role() {
        this.isSystem = false;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.permissions = new ArrayList<>();
    }

    public Role(String name, String description) {
        this();
        this.name = name;
        this.description = description;
    }

    public Role(String name, String description, boolean isSystem) {
        this(name, description);
        this.isSystem = isSystem;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isSystem() {
        return isSystem;
    }

    public void setSystem(boolean system) {
        isSystem = system;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<Permission> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<Permission> permissions) {
        this.permissions = permissions;
    }

    // Utility methods
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Role role = (Role) o;
        return Objects.equals(id, role.id) && Objects.equals(name, role.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }

    @Override
    public String toString() {
        return "Role{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", isSystem=" + isSystem +
                ", permissionCount=" + (permissions != null ? permissions.size() : 0) +
                '}';
    }
}
