package com.appbana.model;

/**
 * Validation result wrapper.
 * Used by ValidationService and PasswordService to return validation status.
 * 
 * @see ENTITY_FORM_BINDING_ARCHITECTURE.md Issue #1 (Password Security)
 */
public class ValidationResult {
    
    private final boolean valid;
    private final String errorMessage;
    
    private ValidationResult(boolean valid, String errorMessage) {
        this.valid = valid;
        this.errorMessage = errorMessage;
    }
    
    /**
     * Creates a successful validation result.
     */
    public static ValidationResult success() {
        return new ValidationResult(true, null);
    }
    
    /**
     * Creates a failed validation result with error message.
     */
    public static ValidationResult error(String errorMessage) {
        return new ValidationResult(false, errorMessage);
    }
    
    public boolean isValid() {
        return valid;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    @Override
    public String toString() {
        return valid ? "Valid" : "Invalid: " + errorMessage;
    }
}
