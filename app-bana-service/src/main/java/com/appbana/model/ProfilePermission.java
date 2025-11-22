package com.appbana.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

/**
 * ProfilePermission Entity - Links profiles to permissions
 * 
 * <p>Many-to-many relationship: A profile can have many permissions,
 * and a permission can belong to many profiles.
 * 
 * @author AppBana Auth Team
 * @since V3 Migration
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfilePermission {
    
    /**
     * Unique identifier (UUID)
     */
    private String id;
    
    /**
     * Profile ID
     */
    private String profileId;
    
    /**
     * Permission ID
     */
    private String permissionId;
    
    /**
     * When this assignment was created
     */
    private Timestamp createdAt;
    
    /**
     * User ID who created this assignment
     */
    private String createdBy;
}
