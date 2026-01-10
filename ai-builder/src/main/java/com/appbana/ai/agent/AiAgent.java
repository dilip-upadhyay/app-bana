package com.appbana.ai.agent;

import com.appbana.ai.agent.tool.Tool;
import com.appbana.ai.agent.tool.ToolCall;
import com.appbana.ai.agent.tool.ToolRegistry;
import com.appbana.ai.agent.tool.ToolResult;
import com.appbana.ai.llm.OpenAiLlmService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;

/**
 * Main AI Agent orchestrator implementing Think → Act → Observe loop
 * Story 8.1: Core Agent Infrastructure
 * 
 * The agent:
 * 1. THINK - Asks LLM what to do next
 * 2. ACT - Executes tools based on LLM decision
 * 3. OBSERVE - Collects tool results
 * 4. Repeat until final answer or max iterations
 */
@Slf4j
public class AiAgent {

    private final OpenAiLlmService llmService;
    private final ToolRegistry toolRegistry;
    private final AgentConfig config;
    private final ObjectMapper objectMapper;

    public AiAgent(OpenAiLlmService llmService, ToolRegistry toolRegistry, AgentConfig config) {
        this.llmService = llmService;
        this.toolRegistry = toolRegistry;
        this.config = config;
        this.objectMapper = new ObjectMapper();
        log.info("AiAgent initialized with {} tools, max iterations: {}",
                toolRegistry.getToolCount(), config.getMaxIterations());
    }

    /**
     * Process a user message through the agent loop
     */
    public AgentResponse process(String userMessage, AgentContext context) {
        long startTime = System.currentTimeMillis();
        List<AgentResponse.AgentStep> steps = new ArrayList<>();

        try {
            log.info("[AGENT] Starting processing for user: {}", context.userId());
            log.debug("[AGENT] User message: {}", userMessage);

            // Agent loop
            for (int iteration = 1; iteration <= config.getMaxIterations(); iteration++) {
                log.info("[AGENT] === Iteration {} ===", iteration);

                // Check timeout
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed > config.getTimeoutSeconds() * 1000) {
                    log.warn("[AGENT] Timeout reached after {}ms", elapsed);
                    return AgentResponse.error(
                            "Agent timeout after " + elapsed + "ms",
                            steps,
                            elapsed);
                }

                // 1. THINK - Ask LLM what to do
                AgentThought thought = think(userMessage, steps, context);

                if (thought == null) {
                    log.error("[AGENT] Failed to get thought from LLM");
                    return AgentResponse.error(
                            "Failed to get response from LLM",
                            steps,
                            System.currentTimeMillis() - startTime);
                }

                log.debug("[AGENT] Thinking: {}", thought.getThinking());

                // Create step for this iteration
                AgentResponse.AgentStep step = new AgentResponse.AgentStep(iteration, thought.getThinking());

                // 2. Check if done
                if (thought.isFinalAnswer()) {
                    log.info("[AGENT] Final answer reached after {} iterations", iteration);
                    steps.add(step);
                    return AgentResponse.success(
                            thought.getFinalAnswer(),
                            steps,
                            System.currentTimeMillis() - startTime);
                }

                // 3. ACT - Execute tools
                if (thought.hasToolCalls()) {
                    log.info("[AGENT] Executing {} tool(s)", thought.getToolCalls().size());
                    List<ToolResult> results = executeTools(thought.getToolCalls(), context);

                    // Add results to step
                    for (ToolResult result : results) {
                        step.addToolResult(result);
                        log.info("[AGENT] {}", result.getSummary());
                    }
                } else {
                    log.warn("[AGENT] No tool calls and no final answer - LLM may be confused");
                }

                // 4. OBSERVE - Add step to history
                steps.add(step);
            }

            // Max iterations reached
            log.warn("[AGENT] Max iterations ({}) reached without final answer", config.getMaxIterations());
            return AgentResponse.error(
                    "Maximum iterations reached without completing task",
                    steps,
                    System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            log.error("[AGENT] Error during processing", e);
            return AgentResponse.error(
                    "Agent error: " + e.getMessage(),
                    steps,
                    System.currentTimeMillis() - startTime);
        }
    }

    /**
     * THINK step - Ask LLM what to do next
     */
    private AgentThought think(String userMessage, List<AgentResponse.AgentStep> history, AgentContext context) {
        try {
            // Build prompt with system instructions, tools, history, and user message
            String prompt = buildAgentPrompt(userMessage, history, context);

            if (config.isDebugMode()) {
                log.debug("[AGENT] Prompt:\n{}", prompt);
            }

            // Call LLM
            String llmResponse = llmService.chat(prompt);

            if (config.isDebugMode()) {
                log.debug("[AGENT] LLM Response:\n{}", llmResponse);
            }

            // Parse LLM response as JSON
            AgentThought thought = parseAgentThought(llmResponse);

            return thought;

        } catch (Exception e) {
            log.error("[AGENT] Error in think step", e);

            // Retry if configured
            if (config.isRetryOnError() && config.getMaxRetries() > 0) {
                log.info("[AGENT] Retrying think step...");
                // Simple retry without recursion
                try {
                    String prompt = buildAgentPrompt(userMessage, history, context);
                    String llmResponse = llmService.chat(prompt);
                    return parseAgentThought(llmResponse);
                } catch (Exception retryError) {
                    log.error("[AGENT] Retry failed", retryError);
                }
            }

            return null;
        }
    }

    /**
     * ACT step - Execute tool calls in parallel using Virtual Threads
     */
    private List<ToolResult> executeTools(List<ToolCall> toolCalls, AgentContext context) {
        if (toolCalls.isEmpty()) {
            return Collections.emptyList();
        }

        log.info("[AGENT] Executing {} tool(s) in parallel using Virtual Threads", toolCalls.size());

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<ToolResult>> tasks = new ArrayList<>();

            for (ToolCall call : toolCalls) {
                tasks.add(() -> {
                    long startTime = System.currentTimeMillis();
                    try {
                        log.debug("[AGENT] executing: {} args: {}", call.getName(), call.getArguments());
                        Tool tool = toolRegistry.getTool(call.getName());

                        if (tool == null) {
                            return ToolResult.error(call.getName(), "Tool not found: " + call.getName());
                        }

                        ToolResult result = tool.execute(call.getArguments(), context);
                        result.setExecutionTimeMs(System.currentTimeMillis() - startTime);
                        result.setToolName(call.getName());
                        return result;

                    } catch (Exception e) {
                        log.error("[AGENT] Tool execution failed: " + call.getName(), e);
                        return ToolResult.error(call.getName(), "Execution error: " + e.getMessage());
                    }
                });
            }

            return executor.invokeAll(tasks).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            return ToolResult.error("unknown", "Parallel execution failed: " + e.getMessage());
                        }
                    })
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("[AGENT] Critical error in parallel execution", e);
            return Collections.singletonList(
                    ToolResult.error("agent_system", "Parallel execution failed: " + e.getMessage()));
        }
    }

    /**
     * Build the prompt for the LLM with agent instructions
     */
    private String buildAgentPrompt(String userMessage, List<AgentResponse.AgentStep> history, AgentContext context) {
        StringBuilder prompt = new StringBuilder();

        // AppBana-specific system instructions
        // AppBana-specific system instructions
        prompt.append(
                """
                        Hello! I'm your AppBana assistant, here to build the business tools you need.

                        I am a smart app builder that takes your business ideas and turns them into working software instantly.

                        ## WHAT I DO FOR YOU
                        You don't need to know technical terms. Just tell me what you want to achieve in your business.

                        I can help you build:
                        1. TRACKING SYSTEMS:
                           - "I need to track customer orders"
                           - "Manage my inventory and stock levels"
                           - "Keep track of employee leave requests"

                        2. DATA COLLECTION:
                           - "Create a signup form for events"
                           - "Collect feedback from customers"
                           - "Allow staff to submit expenses"

                        3. BUSINESS MANAGEMENT:
                           - CRM (Customer lists, sales pipeline)
                           - HR (Employee directory, onboarding)
                           - Operations (Project tracking, equipment logs)

                        ## HOW TO TALK TO ME
                        Speak like a business owner, not a developer.

                        INSTEAD OF SAYING: "Create an entity with text fields and a lookup."
                        SAY: "I need to keep a list of my trusted vendors and what products they supply."

                        INSTEAD OF SAYING: "Add a form component with validation."
                        SAY: "Make sure people can't submit the order without a phone number."

                        PERFORMANCE TIP: You can and SHOULD call multiple tools in a single turn. If the user asks to create 3 things, generate ONE response with 3 tool calls in the `tool_calls` list. I will run them all in parallel for maximum speed. Do not wait for one to finish before asking for the next if they are independent.

                        ## MY PROMISE
                        - I will build the forms, lists, and databases you need.
                        - I will set up the security so your data is safe.
                        - I will explain things in plain English.

                        Let's get to work! What business problem can I solve for you today?
                        """);

        // Available tools
        prompt.append("## Available Tools\n\n");
        prompt.append(toolRegistry.getToolDescriptions());
        prompt.append("\n\n");

        // Response format
        prompt.append("## Response Format\n\n");
        prompt.append("You must respond with valid JSON in one of two formats:\n\n");
        prompt.append("**Format 1: Call Tools**\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"thinking\": \"Your reasoning about what to do next...\",\n");
        prompt.append("  \"tool_calls\": [\n");
        prompt.append("    {\"name\": \"tool_name\", \"arguments\": {\"arg1\": \"value1\"}}\n");
        prompt.append("  ]\n");
        prompt.append("}\n");
        prompt.append("```\n\n");
        prompt.append("**Format 2: Final Answer**\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"thinking\": \"My reasoning...\",\n");
        prompt.append("  \"final_answer\": \"The complete response to the user...\"\n");
        prompt.append("}\n");
        prompt.append("```\n\n");

        // Conversation history
        if (!history.isEmpty()) {
            prompt.append("## Previous Steps\n\n");
            for (AgentResponse.AgentStep step : history) {
                prompt.append(String.format("**Iteration %d:**\n", step.getIteration()));
                prompt.append(String.format("Thinking: %s\n", step.getThinking()));

                if (!step.getToolResults().isEmpty()) {
                    prompt.append("Tool Results:\n");
                    for (ToolResult result : step.getToolResults()) {
                        if (result.isSuccess()) {
                            prompt.append(
                                    String.format("- %s: Success - %s\n", result.getToolName(), result.getData()));
                        } else {
                            prompt.append(String.format("- %s: Error - %s\n", result.getToolName(), result.getError()));
                        }
                    }
                }
                prompt.append("\n");
            }
        }

        // User message
        prompt.append("## User Request\n\n");
        prompt.append(userMessage);
        prompt.append("\n\n");
        prompt.append("Respond with JSON only:");

        return prompt.toString();
    }

    /**
     * Parse LLM response into AgentThought
     */
    private AgentThought parseAgentThought(String llmResponse) throws Exception {
        // Try to extract JSON from response (LLM might wrap it in markdown)
        String json = extractJson(llmResponse);

        // Parse JSON
        Map<String, Object> response = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
        });

        String thinking = (String) response.get("thinking");
        String finalAnswer = (String) response.get("final_answer");

        // Check for final answer
        if (finalAnswer != null && !finalAnswer.isEmpty()) {
            return AgentThought.finalAnswer(thinking, finalAnswer);
        }

        // Parse tool calls
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> toolCallsRaw = (List<Map<String, Object>>) response.get("tool_calls");

        if (toolCallsRaw == null || toolCallsRaw.isEmpty()) {
            log.warn("[AGENT] No tool_calls or final_answer in response");
            return AgentThought.finalAnswer(thinking, "I'm not sure what to do next.");
        }

        List<ToolCall> toolCalls = new ArrayList<>();
        for (Map<String, Object> callRaw : toolCallsRaw) {
            String name = (String) callRaw.get("name");
            @SuppressWarnings("unchecked")
            Map<String, Object> arguments = (Map<String, Object>) callRaw.get("arguments");

            toolCalls.add(new ToolCall(name, arguments));
        }

        return AgentThought.toolCalls(thinking, toolCalls);
    }

    /**
     * Extract JSON from LLM response (handles markdown code blocks)
     */
    private String extractJson(String response) {
        // Remove markdown code blocks if present
        String cleaned = response.trim();

        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }

        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        return cleaned.trim();
    }
}
