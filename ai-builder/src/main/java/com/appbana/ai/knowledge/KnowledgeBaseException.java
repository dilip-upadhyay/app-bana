package com.appbana.ai.knowledge;

/**
 * Custom exception for knowledge base operations
 * Story 7.2: Vector Store Integration
 */
public class KnowledgeBaseException extends Exception {

    public KnowledgeBaseException(String message) {
        super(message);
    }

    public KnowledgeBaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
