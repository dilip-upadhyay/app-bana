package com.appbana.service;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Error handling and formatting service
 */
public class ErrorHandler {

    /**
     * Format exception into standard error response
     * Preserves SQL error codes and messages for debugging
     */
    public static Map<String, Object> errorDetails(Throwable ce) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", ce.getMessage() != null ? ce.getMessage() : ce.getClass().getSimpleName());

        if (ce instanceof SQLException) {
            SQLException sqe = (SQLException) ce;
            m.put("sqlState", sqe.getSQLState());
            m.put("errorCode", sqe.getErrorCode());
        }

        return m;
    }

    /**
     * Sprint 3 (Runtime Foundations) task 3.1 — Turn a validation-style
     * exception message into a structured field-error response the runtime
     * can render inline under the offending input.
     *
     * <p>{@link com.appbana.service.EntityCrudService#coerceAndValidate} emits
     * messages of the form {@code "field 'FIELDNAME' is required"} or
     * {@code "field 'FIELDNAME' below min"}. We extract the field name and
     * everything after it as the human-readable reason. When the message
     * doesn't match the expected pattern, we return a `_form` bucket instead
     * so the client can still surface it — as a form-level error rather than
     * an inline field error.
     *
     * <p>Response shape:
     * <pre>
     * {
     *   "error":  "field 'email' is required",         // original message
     *   "errors": { "email": "is required" }           // parsed for field-level render
     * }
     * </pre>
     */
    public static Map<String, Object> fieldValidationError(Throwable ce) {
        Map<String, Object> body = new LinkedHashMap<>();
        String msg = ce.getMessage() != null ? ce.getMessage() : ce.getClass().getSimpleName();
        body.put("error", msg);

        // Sprint 3 post-review fix — prefer typed field errors from
        // FieldValidationException. Falls through to the regex parser only for
        // legacy callers that still throw plain IllegalArgumentException with
        // a "field 'X' <reason>" message.
        if (ce instanceof FieldValidationException fve && !fve.getFieldErrors().isEmpty()) {
            body.put("errors", new LinkedHashMap<>(fve.getFieldErrors()));
            return body;
        }

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        Matcher m = FIELD_MSG.matcher(msg);
        if (m.find()) {
            String field  = m.group(1);
            String reason = m.group(2).trim();
            fieldErrors.put(field, reason);
        } else {
            fieldErrors.put("_form", msg);
        }
        body.put("errors", fieldErrors);
        return body;
    }

    private static final Pattern FIELD_MSG =
            Pattern.compile("field\\s+'([^']+)'\\s+(.+)");
}
