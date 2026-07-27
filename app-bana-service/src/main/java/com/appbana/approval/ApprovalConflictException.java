package com.appbana.approval;

/**
 * Signals that an approval action was rejected because the record is not in a state
 * that permits it (e.g. approving a row that is no longer {@code PENDING}, or opening a
 * second revision while one is already {@code PENDING}).
 *
 * <p>This is a workflow conflict, not an authorization failure, so route handlers map it
 * to {@code 409 Conflict}. It extends {@link IllegalStateException} so that existing
 * callers which catch the broader type keep working.</p>
 */
public class ApprovalConflictException extends IllegalStateException {
    public ApprovalConflictException(String message) {
        super(message);
    }
}
