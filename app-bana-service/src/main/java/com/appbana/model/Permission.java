package com.appbana.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Permission entity representing a granular permission in AppBana.
 * Maps to the permission table in the database.
 * 
 * Permission Model:
 * - resource: The resource being accessed (e.g., "Project", "User", "*" for all)
 * - action: The action being performed (e.g., "create", "read", "update", "delete", "*" for all)
 * - scope: The scope of access (e.g., "all", "own", "team")
 */
public class Permission {
    private Long id;
    private String resource;
    private String action;
    private String scope;
    private String description;
    private LocalDateTime createdAt;

    // Wildcard constants
    public static final String WILDCARD = "*";
    
    // Common actions
    public static final String CREATE = "create";
    public static final String READ = "read";
    public static final String UPDATE = "update";
    public static final String DELETE = "delete";
    
    // Common scopes
    public static final String SCOPE_ALL = "all";
    public static final String SCOPE_OWN = "own";
    public static final String SCOPE_TEAM = "team";

    // Constructors
    public Permission() {
        this.scope = SCOPE_ALL;
        this.createdAt = LocalDateTime.now();
    }

    public Permission(String resource, String action, String scope) {
        this();
        this.resource = resource;
        this.action = action;
        this.scope = scope;
    }

    public Permission(String resource, String action, String scope, String description) {
        this(resource, action, scope);
        this.description = description;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // Utility methods
    /**
     * Check if this permission matches the requested resource, action, and scope.
     * Supports wildcard matching.
     * 
     * @param resource The requested resource
     * @param action The requested action
     * @param scope The requested scope
     * @return true if this permission grants access
     */
    public boolean matches(String resource, String action, String scope) {
        boolean resourceMatch = WILDCARD.equals(this.resource) || this.resource.equals(resource);
        boolean actionMatch = WILDCARD.equals(this.action) || this.action.equals(action);
        boolean scopeMatch = SCOPE_ALL.equals(this.scope) || this.scope.equals(scope);
        
        return resourceMatch && actionMatch && scopeMatch;
    }

    /**
     * Check if this is a wildcard admin permission (*.*.all)
     */
    public boolean isWildcardAdmin() {
        return WILDCARD.equals(resource) && WILDCARD.equals(action) && SCOPE_ALL.equals(scope);
    }

    /**
     * Create a permission key for unique identification
     */
    public String getKey() {
        return String.format("%s:%s:%s", resource, action, scope);
    }

    /**
     * Factory method to create admin permission
     */
    public static Permission createAdminPermission() {
        return new Permission(WILDCARD, WILDCARD, SCOPE_ALL, "Full access to all resources");
    }

    /**
     * Factory method to create CRUD permissions for a resource
     */
    public static Permission[] createCrudPermissions(String resource, String scope) {
        return new Permission[]{
                new Permission(resource, CREATE, scope, "Create " + resource),
                new Permission(resource, READ, scope, "Read " + resource),
                new Permission(resource, UPDATE, scope, "Update " + resource),
                new Permission(resource, DELETE, scope, "Delete " + resource)
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Permission that = (Permission) o;
        return Objects.equals(id, that.id) ||
                (Objects.equals(resource, that.resource) &&
                        Objects.equals(action, that.action) &&
                        Objects.equals(scope, that.scope));
    }

    @Override
    public int hashCode() {
        return Objects.hash(resource, action, scope);
    }

    @Override
    public String toString() {
        return "Permission{" +
                "id=" + id +
                ", resource='" + resource + '\'' +
                ", action='" + action + '\'' +
                ", scope='" + scope + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
