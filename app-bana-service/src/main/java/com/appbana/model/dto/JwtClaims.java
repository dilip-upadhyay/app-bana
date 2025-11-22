package com.appbana.model.dto;

/**
 * Immutable JWT claims record for token payload.
 * Uses Java 21 record for type-safe JWT data extraction.
 * 
 * <p>This record represents the decoded JWT token payload:</p>
 * <ul>
 *   <li>subject: User ID (from JWT "sub" claim)</li>
 *   <li>email: User's email address</li>
 *   <li>name: User's display name</li>
 *   <li>roles: List of role names assigned to user</li>
 *   <li>issuedAt: Token issue timestamp (epoch seconds)</li>
 *   <li>expiresAt: Token expiration timestamp (epoch seconds)</li>
 * </ul>
 * 
 * @param subject User ID as string
 * @param email User email
 * @param name User display name
 * @param roles List of role names
 * @param issuedAt Token issue time (epoch seconds)
 * @param expiresAt Token expiration time (epoch seconds)
 */
public record JwtClaims(
    String subject,
    String email,
    String name,
    java.util.List<String> roles,
    long issuedAt,
    long expiresAt
) {
    /**
     * Compact constructor for validation and defensive copy.
     */
    public JwtClaims {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Subject (user ID) cannot be null or empty");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }
        // Defensive copy of mutable list
        if (roles != null) {
            roles = java.util.List.copyOf(roles);
        } else {
            roles = java.util.List.of();
        }
    }
    
    /**
     * Get user ID as Long.
     */
    public Long getUserId() {
        try {
            return Long.parseLong(subject);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Subject is not a valid user ID: " + subject);
        }
    }
    
    /**
     * Check if token is expired (based on current time).
     */
    public boolean isExpired() {
        return System.currentTimeMillis() / 1000 > expiresAt;
    }
    
    /**
     * Check if user has a specific role.
     */
    public boolean hasRole(String roleName) {
        return roles.contains(roleName);
    }
    
    /**
     * Check if user is admin.
     */
    public boolean isAdmin() {
        return hasRole("admin") || hasRole("ADMIN");
    }
    
    /**
     * Factory method from decoded JWT.
     */
    public static JwtClaims fromDecodedJWT(com.auth0.jwt.interfaces.DecodedJWT jwt) {
        return new JwtClaims(
            jwt.getSubject(),
            jwt.getClaim("email").asString(),
            jwt.getClaim("name").asString(),
            jwt.getClaim("roles").asList(String.class),
            jwt.getIssuedAt().getTime() / 1000,
            jwt.getExpiresAt().getTime() / 1000
        );
    }
}
