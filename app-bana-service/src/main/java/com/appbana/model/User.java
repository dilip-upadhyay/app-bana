package com.appbana.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * User entity representing an authenticated user in AppBana.
 * Maps to the app_user table in the database.
 */
public class User {
    private Long id;
    private String email;
    private String passwordHash;
    private String name;
    private UserStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime lastLogin;
    private LocalDateTime updatedAt;

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

    // Constructors
    public User() {
        this.status = UserStatus.ACTIVE;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public User(String email, String passwordHash, String name) {
        this();
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(LocalDateTime lastLogin) {
        this.lastLogin = lastLogin;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
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
        User safe = new User();
        safe.id = this.id;
        safe.email = this.email;
        safe.name = this.name;
        safe.status = this.status;
        safe.createdAt = this.createdAt;
        safe.lastLogin = this.lastLogin;
        safe.updatedAt = this.updatedAt;
        // Explicitly NOT copying passwordHash
        return safe;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id) && Objects.equals(email, user.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, email);
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", email='" + email + '\'' +
                ", name='" + name + '\'' +
                ", status=" + status +
                ", createdAt=" + createdAt +
                ", lastLogin=" + lastLogin +
                '}';
    }
}
