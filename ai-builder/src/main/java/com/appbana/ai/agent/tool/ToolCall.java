package com.appbana.ai.agent.tool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Represents a tool call from the LLM
 * Story 8.1: Core Agent Infrastructure
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolCall {

    /**
     * Name of the tool to call
     */
    private String name;

    /**
     * Arguments for the tool (JSON-serializable map)
     */
    private Map<String, Object> arguments;
}
