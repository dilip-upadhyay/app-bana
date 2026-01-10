package com.appbana.ai.agent;

import lombok.Data;

/**
 * Configuration for AI Agent
 * Story 8.1: Core Agent Infrastructure
 */
@Data
public class AgentConfig {

    /**
     * Maximum number of iterations before stopping
     */
    private int maxIterations = 10;

    /**
     * Timeout in seconds for entire agent execution
     */
    private int timeoutSeconds = 180;

    /**
     * Whether to enable debug logging
     */
    private boolean debugMode = false;

    /**
     * Whether to retry on LLM errors
     */
    private boolean retryOnError = true;

    /**
     * Maximum retries for LLM calls
     */
    private int maxRetries = 2;

    /**
     * Create default configuration
     */
    public static AgentConfig defaults() {
        return new AgentConfig();
    }
}
