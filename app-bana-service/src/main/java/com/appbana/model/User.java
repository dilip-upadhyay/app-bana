package com.appbana.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * User entity representing an authenticated user in AppBana.
 * Maps to the app_user table in the database.
 * 
 * <p>Uses Lombok to reduce boilerplate code:</p>
 * <ul>
 *   <li>@Data: Generates getters, setters, toString, equals, hashCode</li>
 *   <li>@Builder: Enables fluent builder pattern</li>
 *   <li>@NoArgsConstructor: Default constructor</li>
 *   <li>@AllArgsConstructor: Constructor with all fields</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private Long id;
    private String email;
    private String passwordHash;
    private String name;
    
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;
    
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    private LocalDateTime lastLogin;
    
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    /**
     * User status enumeration
     */
    public enum UserStatus {
        ACTIVE("active"),
        INACTIVE("inactive"),
        SUSPENDED("suspended"),
        PENDING("pending");

        private final String value;

        UserStatus(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static UserStatus fromString(String value) {
            for (UserStatus status : UserStatus.values()) {
                if (status.value.equalsIgnoreCase(value)) {
                    return status;
                }
            }
            throw new IllegalArgumentException("Unknown user status: " + value);
        }
    }

    // Utility methods
    /**
     * Check if this user is active
     */
    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    /**
     * Update last login timestamp to now
     */
    public void updateLastLogin() {
        this.lastLogin = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Create a safe copy of this user without sensitive data (password hash)
     * for sending to frontend
     */
    public User toSafeUser() {
        return User.builder()
                .id(this.id)
                .email(this.email)
                .name(this.name)
                .status(this.status)
                .createdAt(this.createdAt)
                .lastLogin(this.lastLogin)
                .updatedAt(this.updatedAt)
                // Explicitly NOT copying passwordHash
                .build();
    }
}
