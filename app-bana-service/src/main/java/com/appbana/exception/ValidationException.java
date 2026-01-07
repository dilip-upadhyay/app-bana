package com.appbana.exception;

/**
 * Exception thrown when app validation fails during publish
 */
public class ValidationException extends Exception {
    public ValidationException(String message) {
        super(message);
    }
    
    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
