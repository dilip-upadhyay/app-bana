package com.appbana.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

/**
 * RoleProfile Entity - Links roles to profiles
 * 
 * <p>Many-to-many relationship: A role can have many profiles,
 * and a profile can be assigned to many roles.
 * 
 * <p>Example: "Manager" role has "Manager Profile" + "User Management Profile"
 * 
 * @author AppBana Auth Team
 * @since V3 Migration
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleProfile {
    
    /**
     * Unique identifier (UUID)
     */
    private String id;
    
    /**
     * Role ID
     */
    private String roleId;
    
    /**
     * Profile ID
     */
    private String profileId;
    
    /**
     * When this assignment was created
     */
    private Timestamp createdAt;
    
    /**
     * User ID who created this assignment
     */
    private String createdBy;
}
