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
        String token,
        Map<String, Object> variables) {

    /**
     * C4.4e — the token is the one field with no safe default.
     *
     * <p>Every tool attaches {@code Authorization: Bearer <token>} only when the token is non-blank,
     * so a context carrying a blank one produces unauthenticated backend calls: 401s that surface as
     * the agent burning iterations and giving up vaguely, never as an auth error. The per-site
     * conditionals were checked in one direction only — the guards proved the header is attached
     * when a token exists, and nothing established that one does.
     *
     * <p>Validating here rather than in {@code create} means {@code withVariable} and any future
     * construction path inherit it, and the type simply cannot hold the broken state. Callers at the
     * HTTP boundary must reject the request with 401 before they get this far; this is the backstop
     * that makes the per-site conditionals genuinely defensive rather than load-bearing.
     */
    public AgentContext {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(
                    "AgentContext requires a session token: every tool call it authorises would "
                            + "otherwise 401 and be reported to the user as a failed tool rather "
                            + "than as an auth error. Reject the request at the controller with 401.");
        }
    }

    /**
     * Create a new context with default empty variables
     */
    public static AgentContext create(String tenantId, String appId, String userId, String sessionId, String token) {
        return new AgentContext(tenantId, appId, userId, sessionId, token, new HashMap<>());
    }

    /**
     * Create a copy with updated variables
     */
    public AgentContext withVariable(String key, Object value) {
        Map<String, Object> newVars = new HashMap<>(variables);
        newVars.put(key, value);
        return new AgentContext(tenantId, appId, userId, sessionId, token, newVars);
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
