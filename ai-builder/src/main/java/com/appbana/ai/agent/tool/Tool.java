package com.appbana.ai.agent.tool;

import com.appbana.ai.agent.AgentContext;

/**
 * Interface for agent tools
 * Story 8.1: Core Agent Infrastructure (needed by AiAgent)
 * 
 * Each tool represents a capability the agent can use.
 */
public interface Tool {

    /**
     * Get the tool name (must be unique)
     */
    String getName();

    /**
     * Get a description of what this tool does (for LLM)
     */
    String getDescription();

    /**
     * Get the parameter schema in JSON Schema format (for LLM)
     */
    String getParameterSchema();

    /**
     * Execute the tool with given arguments
     * 
     * @param arguments Tool arguments (JSON-serializable map)
     * @param context   Agent context with session state
     * @return Tool execution result
     */
    ToolResult execute(java.util.Map<String, Object> arguments, AgentContext context);
}
