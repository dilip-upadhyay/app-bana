package com.appbana.exception;

/**
 * Exception thrown when deployment fails during publish
 */
public class DeploymentException extends Exception {
    public DeploymentException(String message) {
        super(message);
    }
    
    public DeploymentException(String message, Throwable cause) {
        super(message, cause);
    }
}
