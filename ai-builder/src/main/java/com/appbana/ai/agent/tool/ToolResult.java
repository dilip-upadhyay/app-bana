package com.appbana.ai.agent.tool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of a tool execution
 * Story 8.1: Core Agent Infrastructure
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult {

    /**
     * Whether the tool executed successfully
     */
    private boolean success;

    /**
     * Result data (JSON-serializable)
     */
    private Object data;

    /**
     * Error message if failed
     */
    private String error;

    /**
     * Execution time in milliseconds
     */
    private long executionTimeMs;

    /**
     * Name of the tool that was executed
     */
    private String toolName;

    /**
     * Create a successful result
     */
    public static ToolResult success(String toolName, Object data, long executionTimeMs) {
        return new ToolResult(true, data, null, executionTimeMs, toolName);
    }

    /**
     * Create an error result
     */
    public static ToolResult error(String toolName, String errorMessage) {
        return new ToolResult(false, null, errorMessage, 0, toolName);
    }

    /**
     * Get a summary of the result for logging
     */
    public String getSummary() {
        if (success) {
            return String.format("[%s] Success (%dms)", toolName, executionTimeMs);
        } else {
            return String.format("[%s] Error: %s", toolName, error);
        }
    }
}
