package com.appbana.ai.llm;

import java.util.List;
import java.util.Map;

/**
 * Interface for LLM providers (OpenAI, Gemini, etc.)
 * Provides a unified way to interact with different models.
 */
public interface LlmService {

    /**
     * Basic chat call with a prompt.
     */
    String chat(String prompt) throws Exception;

    /**
     * Basic chat call with a prompt and images.
     */
    String chat(String prompt, List<String> images) throws Exception;

    /**
     * Chat with a specific task type hint for routing.
     */
    String chat(String prompt, String taskType) throws Exception;

    /**
     * Chat with task type and images.
     */
    String chat(String prompt, List<String> images, String taskType) throws Exception;

    /**
     * Advanced chat with additional options and images.
     */
    String chatWithOptions(String prompt, String taskType, Map<String, Object> options, List<String> images) throws Exception;

    /**
     * Chat with JSON mode enforced.
     */
    String chatWithJsonMode(String prompt) throws Exception;

    /**
     * Chat with JSON mode and images.
     */
    String chatWithJsonMode(String prompt, List<String> images) throws Exception;

    /**
     * Chat with strict JSON schema enforcement.
     */
    String chatStructured(String prompt, String schemaName, String schema) throws Exception;

    /**
     * Specialized exception for LLM-related failures
     */
    class LlmException extends Exception {
        public LlmException(String message) { super(message); }
        public LlmException(String message, Throwable cause) { super(message, cause); }
    }
}

