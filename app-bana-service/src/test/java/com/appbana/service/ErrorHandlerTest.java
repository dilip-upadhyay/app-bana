package com.appbana.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sprint 3 post-review fix — coverage for the three paths through
 * {@link ErrorHandler#fieldValidationError}: (1) typed
 * {@link FieldValidationException}, (2) legacy regex-parseable IAE,
 * (3) IAE that doesn't match the regex (falls into `_form` bucket).
 */
@DisplayName("ErrorHandler Tests")
class ErrorHandlerTest {

    @SuppressWarnings("unchecked")
    private static Map<String, String> errorsOf(Map<String, Object> body) {
        return (Map<String, String>) body.get("errors");
    }

    @Test
    @DisplayName("typed FieldValidationException surfaces field errors directly")
    void typedExceptionForwardsFieldErrors() {
        FieldValidationException fve = new FieldValidationException("email", "is required");
        Map<String, Object> body = ErrorHandler.fieldValidationError(fve);

        assertEquals("field 'email' is required", body.get("error"));
        Map<String, String> errors = errorsOf(body);
        assertEquals(1, errors.size());
        assertEquals("is required", errors.get("email"));
    }

    @Test
    @DisplayName("typed FieldValidationException with multiple fields returns all of them")
    void typedExceptionWithMultipleFields() {
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("email", "invalid format");
        raw.put("age", "below min");
        FieldValidationException fve = new FieldValidationException(raw, "Multiple fields failed");

        Map<String, Object> body = ErrorHandler.fieldValidationError(fve);

        assertEquals("Multiple fields failed", body.get("error"));
        Map<String, String> errors = errorsOf(body);
        assertEquals(2, errors.size());
        assertEquals("invalid format", errors.get("email"));
        assertEquals("below min", errors.get("age"));
    }

    @Test
    @DisplayName("legacy IllegalArgumentException with matching message is regex-parsed")
    void legacyRegexParsedIntoFieldError() {
        IllegalArgumentException iae = new IllegalArgumentException("field 'name' is required");
        Map<String, Object> body = ErrorHandler.fieldValidationError(iae);

        assertEquals("field 'name' is required", body.get("error"));
        Map<String, String> errors = errorsOf(body);
        assertEquals("is required", errors.get("name"));
    }

    @Test
    @DisplayName("unrecognized exception message falls back to _form bucket")
    void unrecognizedMessageFallsBackToFormBucket() {
        IllegalArgumentException iae = new IllegalArgumentException("boom");
        Map<String, Object> body = ErrorHandler.fieldValidationError(iae);

        assertEquals("boom", body.get("error"));
        Map<String, String> errors = errorsOf(body);
        assertEquals("boom", errors.get("_form"));
    }

    @Test
    @DisplayName("FieldValidationException still extends IllegalArgumentException for compat")
    void typedExceptionIsIllegalArgumentException() {
        FieldValidationException fve = new FieldValidationException("x", "invalid");
        assertTrue(fve instanceof IllegalArgumentException,
                "route handlers must still catch this via existing IAE branch");
    }
}
