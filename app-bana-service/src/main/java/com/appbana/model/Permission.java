package com.appbana.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Permission entity representing granular access control in AppBana RBAC.
 * Maps to the permission table in the database.
 * 
 * <p>Permission format: resource:action:scope</p>
 * <ul>
 *   <li><b>resource</b>: Entity or resource name (e.g., "Project", "User")</li>
 *   <li><b>action</b>: CRUD operation (create, read, update, delete, *)</li>
 *   <li><b>scope</b>: Access scope (all, own, team, *)</li>
 * </ul>
 * 
 * <p>Examples:</p>
 * <pre>
 * "Project:*:*"        - Full access to all projects
 * "Project:read:all"   - Read all projects
 * "Project:update:own" - Update own projects only
 * "User:delete:team"   - Delete users in own team
 * </pre>
 * 
 * <p>Uses Lombok for reduced boilerplate</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Permission {
    private Long id;
    private String resource;
    private String action;
    
    @Builder.Default
    private String scope = SCOPE_ALL;
    
    private String description;
    
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

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
        return Permission.builder()
                .resource(WILDCARD)
                .action(WILDCARD)
                .scope(SCOPE_ALL)
                .description("Full access to all resources")
                .build();
    }

    /**
     * Factory method to create CRUD permissions for a resource
     */
    public static Permission[] createCrudPermissions(String resource, String scope) {
        return new Permission[]{
                Permission.builder().resource(resource).action(CREATE).scope(scope).description("Create " + resource).build(),
                Permission.builder().resource(resource).action(READ).scope(scope).description("Read " + resource).build(),
                Permission.builder().resource(resource).action(UPDATE).scope(scope).description("Update " + resource).build(),
                Permission.builder().resource(resource).action(DELETE).scope(scope).description("Delete " + resource).build()
        };
    }
}
