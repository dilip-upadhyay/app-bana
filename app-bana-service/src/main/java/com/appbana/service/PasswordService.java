package com.appbana.service;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Service for secure password hashing and verification using BCrypt.
 * BCrypt is a password hashing function designed to be slow, making brute-force attacks impractical.
 * 
 * Cost Factor: 12 (2^12 iterations) - provides strong security while maintaining acceptable performance.
 * Higher cost = more secure but slower. Cost factor 12 takes ~0.25 seconds per hash.
 */
public class PasswordService {
    
    /**
     * BCrypt cost factor (work factor).
     * Determines the number of iterations: 2^COST_FACTOR
     * Cost 12 = 4096 iterations (~250ms on modern hardware)
     */
    private static final int COST_FACTOR = 12;

    /**
     * Hash a plain-text password using BCrypt with salt.
     * 
     * @param plainPassword The plain-text password to hash
     * @return BCrypt hash string (includes salt and cost factor)
     * @throws IllegalArgumentException if password is null or empty
     */
    public static String hashPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        
        // BCrypt.gensalt() generates a random salt with the specified cost factor
        // BCrypt.hashpw() combines password + salt and performs hashing
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(COST_FACTOR));
    }

    /**
     * Verify that a plain-text password matches a BCrypt hash.
     * 
     * @param plainPassword The plain-text password to verify
     * @param hashedPassword The BCrypt hash to compare against
     * @return true if password matches hash, false otherwise
     * @throws IllegalArgumentException if either parameter is null or empty
     */
    public static boolean verifyPassword(String plainPassword, String hashedPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        if (hashedPassword == null || hashedPassword.isEmpty()) {
            throw new IllegalArgumentException("Hashed password cannot be null or empty");
        }

        try {
            // BCrypt.checkpw() extracts salt from hash and re-hashes the plain password
            // Uses constant-time comparison to prevent timing attacks
            return BCrypt.checkpw(plainPassword, hashedPassword);
        } catch (IllegalArgumentException e) {
            // Invalid hash format
            System.err.println("Invalid BCrypt hash format: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if a password meets minimum security requirements.
     * Requirements:
     * - At least 8 characters
     * - Contains at least one uppercase letter
     * - Contains at least one lowercase letter
     * - Contains at least one digit
     * - Contains at least one special character
     * 
     * @param password The password to validate
     * @return true if password meets requirements
     */
    public static boolean isPasswordStrong(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }

        boolean hasUpperCase = false;
        boolean hasLowerCase = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;

        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpperCase = true;
            else if (Character.isLowerCase(c)) hasLowerCase = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSpecial = true;
        }

        return hasUpperCase && hasLowerCase && hasDigit && hasSpecial;
    }

    /**
     * Get a human-readable description of password strength requirements.
     * 
     * @return Password requirements as a string
     */
    public static String getPasswordRequirements() {
        return "Password must be at least 8 characters and contain: " +
                "uppercase letter, lowercase letter, digit, and special character";
    }

    /**
     * Main method for testing password hashing.
     */
    public static void main(String[] args) {
        // Example usage
        String password = "Admin@123";
        
        System.out.println("Original password: " + password);
        System.out.println("Is strong? " + isPasswordStrong(password));
        
        String hash1 = hashPassword(password);
        System.out.println("\nFirst hash:  " + hash1);
        
        String hash2 = hashPassword(password);
        System.out.println("Second hash: " + hash2);
        System.out.println("(Notice hashes are different due to random salt)");
        
        System.out.println("\nVerifying password against first hash: " + verifyPassword(password, hash1));
        System.out.println("Verifying password against second hash: " + verifyPassword(password, hash2));
        System.out.println("Verifying wrong password: " + verifyPassword("WrongPassword", hash1));
    }
}
