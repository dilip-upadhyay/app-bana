package com.appbana.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

/**
 * Profile Entity - Named collection of permissions
 * 
 * <p>Profiles enable reusable permission sets that can be assigned to roles.
 * Instead of assigning 50+ individual permissions to a role, assign 2-3 profiles.
 * 
 * <p>Example: "Sales Profile" contains all sales-related permissions.
 * Assign "Sales Profile" to "Sales Manager", "Sales Rep", and "Team Lead" roles.
 * 
 * @author AppBana Auth Team
 * @since V3 Migration
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Profile {
    
    /**
     * Unique identifier (UUID)
     */
    private String id;
    
    /**
     * Profile name (unique)
     * Examples: "System Administrator", "Manager", "Sales Profile"
     */
    private String name;
    
    /**
     * Human-readable description
     */
    private String description;
    
    /**
     * Whether this profile is currently active
     * Inactive profiles are ignored during permission resolution
     */
    private Boolean isActive;
    
    /**
     * When this profile was created
     */
    private Timestamp createdAt;
    
    /**
     * When this profile was last updated
     */
    private Timestamp updatedAt;
    
    /**
     * User ID who created this profile
     */
    private String createdBy;
    
    /**
     * User ID who last updated this profile
     */
    private String updatedBy;
}
