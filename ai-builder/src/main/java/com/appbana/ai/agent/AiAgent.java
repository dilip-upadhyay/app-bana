package com.appbana.ai.agent;

import com.appbana.ai.agent.tool.Tool;
import com.appbana.ai.agent.tool.ToolCall;
import com.appbana.ai.agent.tool.ToolRegistry;
import com.appbana.ai.agent.tool.ToolResult;
import com.appbana.ai.llm.OpenAiLlmService;
import com.appbana.ai.rag.ConversationMemory;
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
    private final BatchedToolExecutor batchedExecutor;
    private boolean batchingEnabled = true; // Feature flag

    public AiAgent(OpenAiLlmService llmService, ToolRegistry toolRegistry, AgentConfig config) {
        this.llmService = llmService;
        this.toolRegistry = toolRegistry;
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.batchedExecutor = new BatchedToolExecutor(llmService);
        log.info("AiAgent initialized with {} tools, max iterations: {}, batching: enabled",
                toolRegistry.getToolCount(), config.getMaxIterations());
    }

    /**
     * Enable or disable batched execution (for testing/debugging)
     */
    public void setBatchingEnabled(boolean enabled) {
        this.batchingEnabled = enabled;
        log.info("Batched execution {}", enabled ? "enabled" : "disabled");
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

            // Check if this is a batchable create-app workflow
            if (batchingEnabled && BatchedToolExecutor.isBatchableCreateApp(userMessage)) {
                log.info("[AGENT] Detected batchable create-app workflow, using batched execution");
                return processBatchedCreateApp(userMessage, context, startTime);
            }

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

        // Check if sequential execution is required for safety
        // CreateEntityTool and GeneratePageTool modify shared app metadata and are not
        // thread-safe
        boolean requiresSequential = toolCalls.stream()
                .anyMatch(call -> call.getName().equals("create_entity") ||
                        call.getName().equals("generate_page") ||
                        call.getName().equals("create_app")); // create_app usually singleton but safe to serialize

        if (requiresSequential) {
            log.info("[AGENT] Forcing sequential execution for {} tool(s) to prevent race conditions",
                    toolCalls.size());
            List<ToolResult> results = new ArrayList<>();
            for (ToolCall call : toolCalls) {
                long startTime = System.currentTimeMillis();
                try {
                    log.debug("[AGENT] executing (sequential): {} args: {}", call.getName(), call.getArguments());
                    Tool tool = toolRegistry.getTool(call.getName());

                    if (tool == null) {
                        results.add(ToolResult.error(call.getName(), "Tool not found: " + call.getName()));
                        continue;
                    }

                    ToolResult result = tool.execute(call.getArguments(), context);
                    result.setExecutionTimeMs(System.currentTimeMillis() - startTime);
                    result.setToolName(call.getName());
                    results.add(result);

                } catch (Exception e) {
                    log.error("[AGENT] Tool execution failed: " + call.getName(), e);
                    results.add(ToolResult.error(call.getName(), "Execution error: " + e.getMessage()));
                }
            }
            return results;
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

                        ## INTERACTION RULES - CRITICAL
                        1. **ACKNOWLEDGE & PLAN FIRST**: When a user asks for an app, DO NOT build it immediately.
                           - First, say "Okay, that's a great idea."
                           - List the features you will build (e.g., "I will create a Leave Management system with paid, sick, and holiday leaves.")
                           - Describe what the user will see (e.g., "Employees can log in to view their balance.")

                        2. **ASK CLARIFYING QUESTIONS**:
                           - Ask 1 or 2 relevant follow-up questions to customize the app.
                           - Example: "What kind of leaves do you have in your organization?"
                           - Do NOT ask too many questions. Keep it simple.

                        3. **WAIT FOR CONFIRMATION**:
                           - After presenting your plan, ask: "Does this plan sound good? Shall I create the app?"
                           - **ONLY call the tools (`create_entity`, etc.) when the user confirms.**
                           - Accept ANY of these confirmation phrases:
                              * "yes", "proceed", "go ahead", "confirmed"
                              * "create it", "build it", "make it", "do it"
                              * "create the app", "build the app", "start", "begin"
                              * "looks good, create", "sounds good, proceed"
                           - **CRITICAL**: If the user says "Create App" (or clicks the button), it means "YES, EXECUTE THE PLAN". Do NOT ask for confirmation again. It is NOT a new request. START SEQUENTIAL EXECUTION IMMEDIATELY.
                           - If the user just answers your clarification questions, incorporate their answers and ASK FOR CONFIRMATION AGAIN.
                           - **IMPORTANT**: Include suggested action buttons in your response by adding this to your final_answer:
                              [ACTIONS: Create App | Ask More Questions]
                           - The UI will convert these into clickable buttons for better UX.

                        4. **FINAL EXECUTION & DEPLOYMENT**:
                           - STEP 1: Call `create_app` FIRST to create the app container.
                           - STEP 2: **Capture the `appId` returned by `create_app`**.
                           - STEP 3: Pass this `appId` to ALL subsequent tools (`create_entity`, `generate_page`, `deploy_app`) to ensure they target the correct app.
                          - STEP 4: Call `create_entity` for entities.
                           - STEP 5: Call `generate_page` for pages (passing `appId`).
                           - STEP 6: Call `deploy_app` (passing `appId`).
                           - CRITICAL: Do NOT execute `create_entity` or `generate_page` in parallel. They modify the same app metadata and will overwrite each other. You MUST queue them sequentially or call them one by one.
                           - **IMMEDIATELY AFTER creating the app structure, you MUST call `deploy_app` tool.**
                           - This will publish the app to the dev environment and generate a test link.
                           - **FINAL ANSWER FORMAT**: Your final message to the user MUST include:
                             1. Confirmation that the app was created and deployed
                             2. The **Test URL** returned by `deploy_app` (e.g., "http://localhost:3000/app/{appId}")
                             3. **Testing Instructions**:
                                - "Click the link above to open your app"
                                - "You can add, view, edit, and delete records"
                                - "The app is deployed to DEV environment and ready to use"

                             Example format:
                             "Great news! I've created your Employee Management app and deployed it successfully!

                             🔗 Test your app here: http://localhost:3000/app/abc123

                             How to test:
                             1. Click the link above to open your app
                             2. You'll see list pages for Employees and Payroll
                             3. Try adding a new employee or payroll record
                             4. You can edit, view details, or delete any record

                             Your app is now live in the DEV environment and ready to use!"


                        ## HOW TO TALK
                        - Speak like a business partner.
                        - Be encouraging and clear.
                        - Do not use jargon.

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
        prompt.append("**Format 2: Final Answer (Conversation)**\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"thinking\": \"Internal monologue...\",\n");
        prompt.append("  \"final_answer\": \"The actual message to show to the user. MUST BE PRESENT.\"\n");
        prompt.append("}\n");
        prompt.append("```\n\n");
        prompt.append(
                "IMPORTANT: Do NOT output raw text. ALWAYS use JSON. Verification step: Did you include `tool_calls` OR `final_answer`? One is REQUIRED.\n\n");

        // 1. User Request (The Goal)
        prompt.append("## ORIGINAL USER REQUEST\n\n");
        prompt.append(userMessage);
        prompt.append("\n\n");

        // 2. Conversation History (Context)
        if (context.hasVariable("chat_history")) {
            try {
                @SuppressWarnings("unchecked")
                List<ConversationMemory.Conversation> chatHistory = (List<ConversationMemory.Conversation>) context
                        .getVariable("chat_history");

                if (chatHistory != null && !chatHistory.isEmpty()) {
                    prompt.append("## Conversation Context\n\n");
                    for (ConversationMemory.Conversation conv : chatHistory) {
                        prompt.append(String.format("User: %s\n", conv.getMessage()));
                        prompt.append(String.format("Assistant: %s\n\n", conv.getResponse()));
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to format chat history", e);
            }
        }

        // 3. Execution Progress (What has been done so far)
        if (!history.isEmpty()) {
            prompt.append("## EXECUTION PROGRESS (Current Task)\n\n");
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

            prompt.append("## INSTRUCTION: \n");
            prompt.append(
                    "Review the EXECUTION PROGRESS above. If the ORIGINAL USER REQUEST is not yet fully completed (e.g. app not deployed), CONTINUE to the next necessary step. Do not ask for clarification if you are already making progress.\n\n");
        }

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

        // Robustness: Check for alternative keys if final_answer is missing
        if (finalAnswer == null)
            finalAnswer = (String) response.get("message");
        if (finalAnswer == null)
            finalAnswer = (String) response.get("answer");
        if (finalAnswer == null)
            finalAnswer = (String) response.get("text");

        // DO NOT use thinking as finalAnswer. It contains internal monologue (3rd
        // person) which confuses the user.

        // Parse tool calls first (Prioritize Action over Talk)
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> toolCallsRaw = (List<Map<String, Object>>) response.get("tool_calls");

        if (toolCallsRaw != null && !toolCallsRaw.isEmpty()) {
            List<ToolCall> toolCalls = new ArrayList<>();
            for (Map<String, Object> callRaw : toolCallsRaw) {
                String name = (String) callRaw.get("name");
                @SuppressWarnings("unchecked")
                Map<String, Object> arguments = (Map<String, Object>) callRaw.get("arguments");

                toolCalls.add(new ToolCall(name, arguments));
            }

            if (finalAnswer != null) {
                log.warn(
                        "[AGENT] LLM provided both tool_calls and final_answer. Ignoring final_answer to execute tools.");
            }

            return AgentThought.toolCalls(thinking, toolCalls);
        }

        // Check for final answer
        if (finalAnswer != null && !finalAnswer.isEmpty()) {
            return AgentThought.finalAnswer(thinking, finalAnswer);
        }

        log.warn("[AGENT] No tool_calls or final_answer in response. JSON: " + json);
        // Fallback: If we have thinking but no explicit final_answer, use thinking as
        // the answer.
        // This prevents "Internal Error" when the LLM forgets the final_answer field
        // but explains itself in thinking.
        return AgentThought.finalAnswer(thinking, thinking);
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

    /**
     * Process create-app workflow using batched execution
     * Combines app + entities + pages into single LLM call
     */
    private AgentResponse processBatchedCreateApp(String userMessage, AgentContext context, long startTime) {
        List<AgentResponse.AgentStep> steps = new ArrayList<>();

        try {
            // Extract app name from user message (simple heuristic)
            String appName = extractAppName(userMessage);
            String appDescription = null;
            List<String> entityNames = List.of(); // Let LLM decide default entities

            log.info("[AGENT-BATCHED] Creating app '{}' using batched execution", appName);

            // Execute batched workflow
            Map<String, Object> batchedResult = batchedExecutor.batchCreateApp(appName, appDescription, entityNames);

            // Execute create_app tool with batched result
            Tool createAppTool = toolRegistry.getTool("create_app");
            if (createAppTool == null) {
                return AgentResponse.error("create_app tool not found", steps,
                        System.currentTimeMillis() - startTime);
            }

            Map<String, Object> appData = (Map<String, Object>) batchedResult.get("app");
            Map<String, Object> createAppArgs = Map.of(
                    "name", appData.get("name"),
                    "description", appData.getOrDefault("description", ""),
                    "version", appData.getOrDefault("version", "1.0.0"));

            ToolResult createAppResult = createAppTool.execute(createAppArgs, context);

            if (!createAppResult.isSuccess()) {
                return AgentResponse.error("Failed to create app: " + createAppResult.getError(),
                        steps, System.currentTimeMillis() - startTime);
            }

            log.info("[AGENT-BATCHED] App '{}' created successfully", appName);

            // Execute create_entity tools for each entity
            List<Map<String, Object>> entities = (List<Map<String, Object>>) batchedResult.get("entities");
            Tool createEntityTool = toolRegistry.getTool("create_entity");

            if (createEntityTool != null && entities != null) {
                for (Map<String, Object> entity : entities) {
                    ToolResult entityResult = createEntityTool.execute(entity, context);
                    if (!entityResult.isSuccess()) {
                        log.warn("[AGENT-BATCHED] Failed to create entity: {}", entity.get("name"));
                    }
                }
                log.info("[AGENT-BATCHED] Created {} entities", entities.size());
            }

            // Execute create_page tools for each page
            List<Map<String, Object>> pages = (List<Map<String, Object>>) batchedResult.get("pages");
            Tool createPageTool = toolRegistry.getTool("create_page");

            if (createPageTool != null && pages != null) {
                for (Map<String, Object> page : pages) {
                    ToolResult pageResult = createPageTool.execute(page, context);
                    if (!pageResult.isSuccess()) {
                        log.warn("[AGENT-BATCHED] Failed to create page: {}", page.get("name"));
                    }
                }
                log.info("[AGENT-BATCHED] Created {} pages", pages.size());
            }

            long elapsed = System.currentTimeMillis() - startTime;
            String finalAnswer = String.format(
                    "✅ Successfully created app '%s' with %d entities and %d pages using batched execution (saved ~%d API calls!)",
                    appName, entities != null ? entities.size() : 0, pages != null ? pages.size() : 0,
                    (entities != null ? entities.size() : 0) + (pages != null ? pages.size() : 0));

            return AgentResponse.success(finalAnswer, steps, elapsed);

        } catch (Exception e) {
            log.error("[AGENT-BATCHED] Failed to execute batched create-app", e);
            return AgentResponse.error("Batched execution failed: " + e.getMessage(),
                    steps, System.currentTimeMillis() - startTime);
        }
    }

    /**
     * Extract app name from user message (simple heuristic)
     */
    private String extractAppName(String message) {
        // Simple extraction: look for "create X app" or "build X application"
        String lower = message.toLowerCase();
        String[] patterns = { "create ", "build ", "make " };

        for (String pattern : patterns) {
            int idx = lower.indexOf(pattern);
            if (idx >= 0) {
                String after = message.substring(idx + pattern.length()).trim();
                // Take first 1-3 words before "app" or "application"
                String[] words = after.split("\\s+");
                StringBuilder name = new StringBuilder();
                for (int i = 0; i < Math.min(3, words.length); i++) {
                    String word = words[i];
                    if (word.equalsIgnoreCase("app") || word.equalsIgnoreCase("application")) {
                        break;
                    }
                    if (name.length() > 0)
                        name.append(" ");
                    name.append(word.substring(0, 1).toUpperCase()).append(word.substring(1));
                }
                if (name.length() > 0) {
                    return name.toString();
                }
            }
        }

        return "MyApp"; // Default fallback
    }
}
