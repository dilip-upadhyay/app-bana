package com.appbana.model.dto;

import java.time.LocalDateTime;

/**
 * Immutable DTO for User API responses (excludes password hash for security).
 * Uses Java 21 record for automatic equals/hashCode/toString/getters.
 * 
 * <p>
 * Records provide:
 * </p>
 * <ul>
 * <li>Immutability by default (all fields are final)</li>
 * <li>Automatic accessor methods (id(), email(), name(), etc.)</li>
 * <li>Automatic equals/hashCode based on all fields</li>
 * <li>Automatic toString with all field values</li>
 * <li>Compact constructor for validation</li>
 * </ul>
 * 
 * @param id        User's database ID
 * @param email     User's email address
 * @param name      User's display name
 * @param tenantId  Unique Tenant ID for data isolation
 * @param status    User status (ACTIVE, INACTIVE, SUSPENDED, PENDING)
 * @param createdAt Account creation timestamp
 * @param lastLogin Last successful login timestamp (nullable)
 * @param updatedAt Last update timestamp
 */
public record UserDTO(
        Long id,
        String email,
        String name,
        String tenantId,
        String status,
        LocalDateTime createdAt,
        LocalDateTime lastLogin,
        LocalDateTime updatedAt) {
    /**
     * Compact constructor for validation.
     * Validates email format before creating the record.
     */
    public UserDTO {
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("Invalid email: " + email);
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Status cannot be null or empty");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant ID cannot be null or empty");
        }
    }

    /**
     * Derived property: check if user is active.
     * Records can have instance methods in addition to accessors.
     */
    public boolean isActive() {
        return "active".equalsIgnoreCase(status);
    }

    /**
     * Factory method to create UserDTO from User entity (excludes password hash).
     * 
     * @param user User entity from database
     * @return UserDTO Safe DTO without sensitive data
     */
    public static UserDTO fromUser(com.appbana.model.User user) {
        return new UserDTO(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getTenantId(),
                user.getStatus().getValue(),
                user.getCreatedAt(),
                user.getLastLogin(),
                user.getUpdatedAt());
    }
}
