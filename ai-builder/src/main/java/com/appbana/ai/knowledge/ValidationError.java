package com.appbana.ai.knowledge;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single validation error
 * Story 7.4: Metadata Validation Service
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidationError {

    /**
     * JSON path to the error location (e.g., "fields[0].type")
     */
    private String path;

    /**
     * Human-readable error message
     */
    private String message;

    /**
     * Severity level
     */
    private ValidationSeverity severity;

    /**
     * Optional suggestion for fixing the error
     */
    private String suggestedFix;

    /**
     * Validation severity levels
     */
    public enum ValidationSeverity {
        ERROR, // Must be fixed
        WARNING, // Should be reviewed
        INFO // Informational
    }

    /**
     * Create an error-level validation error
     */
    public static ValidationError error(String path, String message) {
        return new ValidationError(path, message, ValidationSeverity.ERROR, null);
    }

    /**
     * Create an error with a suggested fix
     */
    public static ValidationError errorWithFix(String path, String message, String suggestedFix) {
        return new ValidationError(path, message, ValidationSeverity.ERROR, suggestedFix);
    }

    /**
     * Create a warning-level validation error
     */
    public static ValidationError warning(String path, String message) {
        return new ValidationError(path, message, ValidationSeverity.WARNING, null);
    }

    /**
     * Create an info-level validation error
     */
    public static ValidationError info(String path, String message) {
        return new ValidationError(path, message, ValidationSeverity.INFO, null);
    }
}
