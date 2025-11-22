package com.appbana.model.dto;

import com.appbana.model.Profile;

import java.sql.Timestamp;
import java.util.List;

/**
 * Profile Data Transfer Object
 * 
 * <p>Immutable record for API responses containing profile data.
 * 
 * @param id Unique identifier
 * @param name Profile name
 * @param description Profile description
 * @param isActive Whether profile is active
 * @param permissionCount Number of permissions in this profile (for summary views)
 * @param roleCount Number of roles using this profile (for summary views)
 * @param permissions List of permission names (for detailed views)
 * @param createdAt Creation timestamp
 * @param updatedAt Update timestamp
 * @param createdBy Creator user ID
 * @param updatedBy Last updater user ID
 * 
 * @author AppBana Auth Team
 * @since V3 Migration
 */
public record ProfileDTO(
    String id,
    String name,
    String description,
    Boolean isActive,
    Integer permissionCount,
    Integer roleCount,
    List<String> permissions,
    Timestamp createdAt,
    Timestamp updatedAt,
    String createdBy,
    String updatedBy
) {
    
    /**
     * Convert Profile entity to DTO (summary view without counts)
     */
    public static ProfileDTO fromProfile(Profile profile) {
        return new ProfileDTO(
            profile.getId(),
            profile.getName(),
            profile.getDescription(),
            profile.getIsActive(),
            null,  // Not loaded
            null,  // Not loaded
            null,  // Not loaded
            profile.getCreatedAt(),
            profile.getUpdatedAt(),
            profile.getCreatedBy(),
            profile.getUpdatedBy()
        );
    }
    
    /**
     * Create summary DTO with counts
     */
    public static ProfileDTO summary(Profile profile, int permissionCount, int roleCount) {
        return new ProfileDTO(
            profile.getId(),
            profile.getName(),
            profile.getDescription(),
            profile.getIsActive(),
            permissionCount,
            roleCount,
            null,  // Not loaded in summary
            profile.getCreatedAt(),
            profile.getUpdatedAt(),
            profile.getCreatedBy(),
            profile.getUpdatedBy()
        );
    }
    
    /**
     * Create detailed DTO with permission list
     */
    public static ProfileDTO detailed(Profile profile, List<String> permissions, int roleCount) {
        return new ProfileDTO(
            profile.getId(),
            profile.getName(),
            profile.getDescription(),
            profile.getIsActive(),
            permissions != null ? permissions.size() : 0,
            roleCount,
            permissions,
            profile.getCreatedAt(),
            profile.getUpdatedAt(),
            profile.getCreatedBy(),
            profile.getUpdatedBy()
        );
    }
}
