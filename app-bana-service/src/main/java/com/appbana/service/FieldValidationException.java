package com.appbana.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Typed contract for row-level field validation failures.
 *
 * <p>Replaces the "throw {@link IllegalArgumentException} with a
 * regex-parseable message" pattern that {@link EntityCrudService} historically
 * used. Carrying the field name/reason pair as structured data lets
 * {@link ErrorHandler#fieldValidationError(Throwable)} skip its legacy string
 * parser and forward the errors directly to the runtime — which renders each
 * one inline under the offending input.
 *
 * <p>Still extends {@link IllegalArgumentException} so existing catch
 * blocks in {@link com.appbana.server.routes.GenericEntityRoutes} continue to
 * route this to the 400-status branch without change.
 */
public class FieldValidationException extends IllegalArgumentException {

    private final Map<String, String> fieldErrors;

    /** Single-field convenience — the common case in {@code coerceAndValidate}. */
    public FieldValidationException(String field, String reason) {
        super("field '" + field + "' " + reason);
        Map<String, String> m = new LinkedHashMap<>();
        m.put(field, reason);
        this.fieldErrors = Collections.unmodifiableMap(m);
    }

    /** Batch constructor for when a payload has multiple invalid fields. */
    public FieldValidationException(Map<String, String> fieldErrors, String summary) {
        super(summary);
        this.fieldErrors = Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors));
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
