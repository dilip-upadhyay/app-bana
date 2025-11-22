package com.appbana.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.util.Date;
import java.util.List;

/**
 * Service for generating and verifying JSON Web Tokens (JWT) for authentication.
 * 
 * JWT Structure:
 * - Header: Algorithm and token type
 * - Payload: Claims (user id, email, roles, expiration)
 * - Signature: HMAC-SHA256 signature for verification
 * 
 * Security Notes:
 * - SECRET_KEY should be loaded from environment variable in production
 * - Tokens expire after 7 days
 * - Use HTTPS in production to prevent token interception
 */
public class JwtService {
    
    /**
     * Secret key for signing JWT tokens.
     * IMPORTANT: In production, load this from environment variable or secrets manager.
     * DO NOT commit real secret keys to version control.
     */
    private static final String SECRET_KEY = System.getenv("JWT_SECRET") != null 
            ? System.getenv("JWT_SECRET") 
            : "appbana-default-secret-change-in-production-2025";
    
    /**
     * JWT issuer identifier
     */
    private static final String ISSUER = "appbana";
    
    /**
     * Token expiration time: 7 days in milliseconds
     */
    private static final long EXPIRATION_TIME = 7L * 24 * 60 * 60 * 1000; // 7 days
    
    /**
     * HMAC-SHA256 algorithm for JWT signing
     */
    private static final Algorithm algorithm = Algorithm.HMAC256(SECRET_KEY);

    /**
     * Generate a JWT token for a user.
     * 
     * @param userId User's database ID
     * @param email User's email address
     * @param name User's display name
     * @param roles List of role names assigned to the user
     * @return JWT token string
     */
    public static String generateToken(Long userId, String email, String name, List<String> roles) {
        Date now = new Date();
        Date expiresAt = new Date(now.getTime() + EXPIRATION_TIME);

        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(userId.toString())
                .withClaim("email", email)
                .withClaim("name", name)
                .withClaim("roles", roles)
                .withIssuedAt(now)
                .withExpiresAt(expiresAt)
                .sign(algorithm);
    }

    /**
     * Verify and decode a JWT token.
     * 
     * @param token The JWT token string to verify
     * @return DecodedJWT object containing claims
     * @throws JWTVerificationException if token is invalid or expired
     */
    public static DecodedJWT verifyToken(String token) throws JWTVerificationException {
        JWTVerifier verifier = JWT.require(algorithm)
                .withIssuer(ISSUER)
                .build();
        
        return verifier.verify(token);
    }

    /**
     * Extract user ID from a JWT token.
     * 
     * @param token The JWT token string
     * @return User ID as Long
     * @throws JWTVerificationException if token is invalid
     */
    public static Long getUserIdFromToken(String token) throws JWTVerificationException {
        DecodedJWT jwt = verifyToken(token);
        return Long.parseLong(jwt.getSubject());
    }

    /**
     * Extract email from a JWT token.
     * 
     * @param token The JWT token string
     * @return User's email address
     * @throws JWTVerificationException if token is invalid
     */
    public static String getEmailFromToken(String token) throws JWTVerificationException {
        DecodedJWT jwt = verifyToken(token);
        return jwt.getClaim("email").asString();
    }

    /**
     * Extract roles from a JWT token.
     * 
     * @param token The JWT token string
     * @return List of role names
     * @throws JWTVerificationException if token is invalid
     */
    public static List<String> getRolesFromToken(String token) throws JWTVerificationException {
        DecodedJWT jwt = verifyToken(token);
        return jwt.getClaim("roles").asList(String.class);
    }

    /**
     * Check if a token is expired.
     * 
     * @param token The JWT token string
     * @return true if token is expired
     */
    public static boolean isTokenExpired(String token) {
        try {
            DecodedJWT jwt = verifyToken(token);
            return jwt.getExpiresAt().before(new Date());
        } catch (JWTVerificationException e) {
            return true; // Treat invalid tokens as expired
        }
    }

    /**
     * Get expiration date from token.
     * 
     * @param token The JWT token string
     * @return Expiration date or null if invalid
     */
    public static Date getExpirationDate(String token) {
        try {
            DecodedJWT jwt = JWT.decode(token);
            return jwt.getExpiresAt();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extract token from Authorization header.
     * Expected format: "Bearer <token>"
     * 
     * @param authorizationHeader The Authorization header value
     * @return JWT token string or null if invalid format
     */
    public static String extractTokenFromHeader(String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7);
        }
        return null;
    }

    /**
     * Main method for testing JWT generation and verification.
     */
    public static void main(String[] args) {
        // Example usage
        Long userId = 1L;
        String email = "admin@appbana.local";
        String name = "System Administrator";
        List<String> roles = List.of("admin", "user");

        System.out.println("Generating JWT for user: " + name);
        String token = generateToken(userId, email, name, roles);
        System.out.println("\nGenerated Token:");
        System.out.println(token);

        System.out.println("\n--- Token Verification ---");
        try {
            DecodedJWT decoded = verifyToken(token);
            System.out.println("Token is valid!");
            System.out.println("User ID: " + decoded.getSubject());
            System.out.println("Email: " + decoded.getClaim("email").asString());
            System.out.println("Name: " + decoded.getClaim("name").asString());
            System.out.println("Roles: " + decoded.getClaim("roles").asList(String.class));
            System.out.println("Issued At: " + decoded.getIssuedAt());
            System.out.println("Expires At: " + decoded.getExpiresAt());
            System.out.println("Is Expired: " + isTokenExpired(token));
        } catch (JWTVerificationException e) {
            System.err.println("Token verification failed: " + e.getMessage());
        }

        System.out.println("\n--- Testing Invalid Token ---");
        try {
            verifyToken("invalid.token.here");
        } catch (JWTVerificationException e) {
            System.out.println("Expected error: " + e.getMessage());
        }

        System.out.println("\n--- Testing Authorization Header ---");
        String authHeader = "Bearer " + token;
        String extractedToken = extractTokenFromHeader(authHeader);
        System.out.println("Extracted token matches: " + token.equals(extractedToken));
    }
}
