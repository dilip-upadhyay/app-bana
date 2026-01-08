package com.appbana.ai.agent.tool;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry for managing available tools
 * Story 8.1: Core Agent Infrastructure
 */
@Slf4j
public class ToolRegistry {

    private final Map<String, Tool> tools;

    public ToolRegistry() {
        this.tools = new HashMap<>();
    }

    /**
     * Register a tool
     */
    public void register(Tool tool) {
        if (tool == null) {
            throw new IllegalArgumentException("Tool cannot be null");
        }

        String name = tool.getName();
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Tool name cannot be null or empty");
        }

        if (tools.containsKey(name)) {
            throw new IllegalStateException("Tool already registered: " + name);
        }

        tools.put(name, tool);
        log.info("Registered tool: {}", name);
    }

    /**
     * Get a tool by name
     */
    public Tool getTool(String name) {
        return tools.get(name);
    }

    /**
     * Get all registered tools
     */
    public List<Tool> getAllTools() {
        return new ArrayList<>(tools.values());
    }

    /**
     * Get count of registered tools
     */
    public int getToolCount() {
        return tools.size();
    }

    /**
     * Check if a tool is registered
     */
    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }

    /**
     * Get tool descriptions formatted for LLM prompt
     */
    public String getToolDescriptions() {
        if (tools.isEmpty()) {
            return "No tools available.";
        }

        StringBuilder sb = new StringBuilder();

        for (Tool tool : tools.values()) {
            sb.append("### ").append(tool.getName()).append("\n");
            sb.append(tool.getDescription()).append("\n\n");
            sb.append("**Parameters:**\n");
            sb.append("```json\n");
            sb.append(tool.getParameterSchema()).append("\n");
            sb.append("```\n\n");
        }

        return sb.toString();
    }

    /**
     * Clear all tools (for testing)
     */
    public void clear() {
        tools.clear();
        log.info("Cleared all tools");
    }
}
