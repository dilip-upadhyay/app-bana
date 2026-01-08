package com.appbana.ai.agent;

import java.util.HashMap;
import java.util.Map;

/**
 * Immutable context for agent execution
 * Story 8.1: Core Agent Infrastructure
 * 
 * Carries session state and variables between tool executions.
 */
public record AgentContext(
        String tenantId,
        String appId,
        String userId,
        String sessionId,
        Map<String, Object> variables) {
    /**
     * Create a new context with default empty variables
     */
    public static AgentContext create(String tenantId, String appId, String userId, String sessionId) {
        return new AgentContext(tenantId, appId, userId, sessionId, new HashMap<>());
    }

    /**
     * Create a copy with updated variables
     */
    public AgentContext withVariable(String key, Object value) {
        Map<String, Object> newVars = new HashMap<>(variables);
        newVars.put(key, value);
        return new AgentContext(tenantId, appId, userId, sessionId, newVars);
    }

    /**
     * Get a variable value
     */
    public Object getVariable(String key) {
        return variables.get(key);
    }

    /**
     * Check if variable exists
     */
    public boolean hasVariable(String key) {
        return variables.containsKey(key);
    }
}
