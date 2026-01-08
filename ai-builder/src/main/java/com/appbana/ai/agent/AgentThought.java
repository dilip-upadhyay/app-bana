package com.appbana.ai.agent;

import com.appbana.ai.agent.tool.ToolCall;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the LLM's thought/decision in the agent loop
 * Story 8.1: Core Agent Infrastructure
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentThought {

    /**
     * The LLM's reasoning about what to do next
     */
    private String thinking;

    /**
     * Tools the LLM wants to call (empty if final answer)
     */
    private List<ToolCall> toolCalls;

    /**
     * Final answer to return to user (null if more work needed)
     */
    private String finalAnswer;

    /**
     * Check if this is a final answer (no more tool calls)
     */
    public boolean isFinalAnswer() {
        return finalAnswer != null && !finalAnswer.isEmpty();
    }

    /**
     * Check if there are tool calls to execute
     */
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    /**
     * Create a final answer thought
     */
    public static AgentThought finalAnswer(String thinking, String answer) {
        return new AgentThought(thinking, new ArrayList<>(), answer);
    }

    /**
     * Create a tool call thought
     */
    public static AgentThought toolCalls(String thinking, List<ToolCall> calls) {
        return new AgentThought(thinking, calls, null);
    }
}
