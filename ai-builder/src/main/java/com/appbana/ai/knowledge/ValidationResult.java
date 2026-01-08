package com.appbana.ai.knowledge;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Aggregates validation results
 * Story 7.4: Metadata Validation Service
 */
@Data
public class ValidationResult {

    private boolean valid;
    private List<ValidationError> errors;
    private List<ValidationError> warnings;
    private Object fixedMetadata;

    public ValidationResult() {
        this.valid = true;
        this.errors = new ArrayList<>();
        this.warnings = new ArrayList<>();
    }

    /**
     * Add an error to the result
     */
    public void addError(ValidationError error) {
        this.errors.add(error);
        if (error.getSeverity() == ValidationError.ValidationSeverity.ERROR) {
            this.valid = false;
        }
    }

    /**
     * Add a warning to the result
     */
    public void addWarning(ValidationError warning) {
        this.warnings.add(warning);
    }

    /**
     * Check if there are any errors
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * Check if there are any warnings
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    /**
     * Get total count of issues (errors + warnings)
     */
    public int getIssueCount() {
        return errors.size() + warnings.size();
    }

    /**
     * Get a summary of the validation result
     */
    public String getSummary() {
        if (valid && !hasWarnings()) {
            return "Validation passed with no issues";
        }

        StringBuilder summary = new StringBuilder();

        if (hasErrors()) {
            summary.append(String.format("%d error(s)", errors.size()));
        }

        if (hasWarnings()) {
            if (summary.length() > 0) {
                summary.append(", ");
            }
            summary.append(String.format("%d warning(s)", warnings.size()));
        }

        return summary.toString();
    }

    /**
     * Get detailed error messages
     */
    public String getDetailedErrors() {
        if (!hasErrors()) {
            return "No errors";
        }

        return errors.stream()
                .map(e -> String.format("[%s] %s: %s%s",
                        e.getSeverity(),
                        e.getPath(),
                        e.getMessage(),
                        e.getSuggestedFix() != null ? " (Suggestion: " + e.getSuggestedFix() + ")" : ""))
                .collect(Collectors.joining("\n"));
    }

    /**
     * Get detailed warning messages
     */
    public String getDetailedWarnings() {
        if (!hasWarnings()) {
            return "No warnings";
        }

        return warnings.stream()
                .map(w -> String.format("[%s] %s: %s",
                        w.getSeverity(),
                        w.getPath(),
                        w.getMessage()))
                .collect(Collectors.joining("\n"));
    }

    /**
     * Create a successful validation result
     */
    public static ValidationResult success() {
        return new ValidationResult();
    }

    /**
     * Create a failed validation result with an error
     */
    public static ValidationResult failure(String path, String message) {
        ValidationResult result = new ValidationResult();
        result.addError(ValidationError.error(path, message));
        return result;
    }
}
