package com.appbana.ai.agent;

import com.appbana.ai.agent.tool.ToolResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Response from the AI Agent after processing
 * Story 8.1: Core Agent Infrastructure
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentResponse {

    /**
     * Final answer to return to the user
     */
    private String finalAnswer;

    /**
     * All steps/iterations the agent took
     */
    private List<AgentStep> steps;

    /**
     * Whether the agent completed successfully
     */
    private boolean success;

    /**
     * Error message if failed
     */
    private String error;

    /**
     * Total execution time in milliseconds
     */
    private long totalTimeMs;

    /**
     * Number of iterations used
     */
    private int iterationCount;

    /**
     * Create a successful response
     */
    public static AgentResponse success(String finalAnswer, List<AgentStep> steps, long totalTimeMs) {
        return new AgentResponse(finalAnswer, steps, true, null, totalTimeMs, steps.size());
    }

    /**
     * Create an error response
     */
    public static AgentResponse error(String errorMessage, List<AgentStep> steps, long totalTimeMs) {
        return new AgentResponse(null, steps, false, errorMessage, totalTimeMs, steps.size());
    }

    /**
     * Represents a single step in the agent's execution
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentStep {
        private int iteration;
        private String thinking;
        private List<ToolResult> toolResults;

        public AgentStep(int iteration, String thinking) {
            this.iteration = iteration;
            this.thinking = thinking;
            this.toolResults = new ArrayList<>();
        }

        public void addToolResult(ToolResult result) {
            this.toolResults.add(result);
        }
    }
}
