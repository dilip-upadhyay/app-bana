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
import java.util.HashMap;
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

            // Removed batched shortcut to respect "Plan First" workflow
            /*
             * if (batchingEnabled && BatchedToolExecutor.isBatchableCreateApp(userMessage))
             * {
             * log.
             * info("[AGENT] Detected batchable create-app workflow, using batched execution"
             * );
             * return processBatchedCreateApp(userMessage, context, startTime);
             * }
             */

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

            // Graceful exit: don't error, just return what we have with a note
            String partialSummary = "I've reached the maximum number of steps (" + config.getMaxIterations()
                    + ") allowed for this request to save costs. " +
                    "I may have completed some parts of your request. Please check the logs or ask me to continue if more work is needed.";

            return AgentResponse.success(
                    partialSummary,
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
        prompt.append(
                """
                        You are an AppBana AI assistant (Expert Architect & Data Modeler).
                        Your goal is to build robust, correct, and professional applications with "Zero Defects".

                        ## CORE INSTRUCTIONS (ZERO-INTENT)
                        You are an autonomous agent. You must decide whether to TALK or ACT.

                        1. **GENERAL CONVERSATION (TALK)**:
                           - If the user greets you, asks a general question, or asks for clarification, DO NOT call any tools.
                           - Simply reply with a `final_answer`.

                        2. **APP BUILDING (ACT)**:
                           - If the user wants to build, modify, or deploy an app, YOU MUST call the appropriate tool.
                           - **Preferred Workflow**: Use `scaffold_app` for new apps (10x faster).

                        ## CONTEXT-AWARE ENTITY QUERYING (CRITICAL RULE)
                        1. **CHECK CONTEXT**: Before answering questions about entities (e.g., "How many fields in Employee?", "Show me the Customer entity"), you MUST check if an app is selected in `context.appId`.
                        2. **IF APP SELECTED**: Use the `list_entities` tool. It will automatically filter for the selected app.
                        3. **IF NO APP SELECTED**: DO NOT GUESS. You MUST ask the user to select an app first.
                           - Example: "Please select an application first so I know which 'Employee' entity you are referring to."
                           - Exception: If the user explicitly asks to "List all apps" or "Create a new app", you can proceed without a selected app.

                        ## PREFERRED WORKFLOW: ONE-SHOT APP CREATION
                        **CRITICAL**: When the user asks to create a new application, YOU MUST use the `scaffold_app` tool.
                        This tool is 10x faster and cheaper than using granular tools sequentially.

                        ### How to Use `scaffold_app`:
                        1. Listen to the user describe their app (e.g., "Build a Salon Booking App").
                        2. Design the COMPLETE metadata: App Name, Entities (with fields), and Pages.
                        3. Call `scaffold_app` ONCE with the full JSON structure.
                        4. Done. The app will be created and deployed automatically.

                        ### Example:
                        User: "Create a Library Management App"
                        You: {
                          "tool_calls": [{
                            "name": "scaffold_app",
                            "arguments": {
                              "appName": "Library Management",
                              "entities": [
                                {"name": "Book", "displayName": "Book", "fields": [...]},
                                {"name": "Member", "displayName": "Member", "fields": [...]}
                              ],
                              "pages": [
                                {"name": "BookList", "path": "/books", "type": "list", "entityName": "Book"}
                              ]
                            }
                          }]
                        }

                        **DO NOT** use `create_app`, `create_entity`, `generate_page` individually unless the user explicitly modifies an existing app.

                        ## EXPERT DATA MODELING RULES (CRITICAL)
                        1. **Every Field MUST Have an `id`** (snake_case, e.g., "first_name", "phone_number").
                        2. **Every Relationship Field MUST Have `referenceEntity`** (e.g., {"type": "reference", "referenceEntity": "Customer"}).
                        3. **Choose the Right Types** (Be Precise):
                           - **Money/Price**: Use `decimal` (NOT `number` or `float`).
                           - **Quantities/Counts/Duration**: Use `number` (maps to Integer).
                           - **Phone/Zip/ID**: Use `text` (NOT `number`). Phone numbers contain formatting characters.
                           - **Descriptions/Notes**: Use `longtext` (NOT `text`).
                           - **Dates**: Use `date` for birthdays, `datetime` for logs/schedules.
                           - **Status/Category**: Use `status` with defined `options`.
                        4. **Schema Integrity**:
                           - Every Entity MUST have a meaningful name (PascalCase, e.g., "StudentProfile").
                           - Avoid generic field names like "Data" or "Value". Use "ExamScore", "TotalAmount".
                        5. **Relationships**:
                           - If Entity A "belongs to" Entity B (e.g., Order -> Customer), add a field `customer` of type `reference` pointing to `Customer` entity.

                        ## EXECUTION RULES
                        1. **NO RETRIES**: If a tool fails (e.g., validation error), **STOP IMMEDIATELY**. Do not retry the same call. Report the error to the user and ask for guidance. Repeated failures cost money and frustrate users.
                        2. **Check Your Work**:
                           - Before calling any tool, verify `type` is one of: [text, number, decimal, boolean, date, datetime, email, phone, status, reference, longtext].
                           - Do NOT invent types like "money" or "currency" (use `decimal`).
                        3. **Context Sensitivity**:
                           - Always use the `appId` from context when calling tools like `create_page` or `create_entity`.
                           - If `appId` is "default" or missing, and the user wants to add to an app, ASK WHICH APP.

                        ## TONE
                        - Expert, Precise, and Efficient.
                        - You are a Senior Architect. Output clean, optimized metadata.
                        """);

        // Available tools
        prompt.append("## Available Tools\n\n");
        prompt.append(toolRegistry.getToolDescriptions());
        prompt.append("\n\n");

        // Response format
        prompt.append("## Response Format\n\n");
        prompt.append("You must respond with valid JSON in one of two formats:\n\n");
        prompt.append("**Format 1: Call Tools (ACT)**\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"thinking\": \"Your reasoning about what to do next...\",\n");
        prompt.append("  \"tool_calls\": [\n");
        prompt.append("    {\"name\": \"tool_name\", \"arguments\": {\"arg1\": \"value1\"}}\n");
        prompt.append("  ]\n");
        prompt.append("}\n");
        prompt.append("```\n\n");
        prompt.append("**Format 2: Final Answer (TALK)**\n");
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

        // 2. Conversation History (Context) - Limited to last 5 messages for token
        // efficiency
        if (context.hasVariable("chat_history")) {
            try {
                @SuppressWarnings("unchecked")
                List<ConversationMemory.Conversation> chatHistory = (List<ConversationMemory.Conversation>) context
                        .getVariable("chat_history");

                if (chatHistory != null && !chatHistory.isEmpty()) {
                    // Limit to last 5 messages to reduce token usage
                    int startIdx = Math.max(0, chatHistory.size() - 5);
                    List<ConversationMemory.Conversation> recentHistory = chatHistory.subList(startIdx,
                            chatHistory.size());

                    prompt.append("## Conversation Context\n\n");
                    for (ConversationMemory.Conversation conv : recentHistory) {
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

            // Extract appId for subsequent steps
            Map<String, Object> resultData = (Map<String, Object>) createAppResult.getData();
            String appId = (String) resultData.get("id");

            log.info("[AGENT-BATCHED] App '{}' created successfully (ID: {})", appName, appId);

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
                    Map<String, Object> pageArgs = new HashMap<>(page);
                    pageArgs.put("appId", appId); // Explicitly pass appId
                    ToolResult pageResult = createPageTool.execute(pageArgs, context);
                    if (!pageResult.isSuccess()) {
                        log.warn("[AGENT-BATCHED] Failed to create page: {}", page.get("name"));
                    }
                }
                log.info("[AGENT-BATCHED] Created {} pages", pages.size());
            }

            // NEW: Execute deploy_app tool
            log.info("[AGENT-BATCHED] Deploying app '{}'...", appName);
            Tool deployTool = toolRegistry.getTool("deploy_app");
            String testUrl = " (link pending) ";
            if (deployTool != null) {
                ToolResult deployResult = deployTool.execute(Map.of("appId", appId), context);
                if (deployResult.isSuccess() && deployResult.getData() instanceof Map) {
                    Map<String, Object> deployData = (Map<String, Object>) deployResult.getData();
                    testUrl = (String) deployData.getOrDefault("testUrl", " (link pending) ");
                    log.info("[AGENT-BATCHED] App deployed successfully. URL: {}", testUrl);
                } else {
                    log.warn("[AGENT-BATCHED] Deployment failed: {}", deployResult.getError());
                }
            }

            long elapsed = System.currentTimeMillis() - startTime;
            String finalAnswer = String.format(
                    "✅ Successfully created and deployed app '%s' with %d entities and %d pages!\\n\\n" +
                            "🔗 **Test your app here**: %s\\n\\n" +
                            "You can now add, edit, and view records in your new application.",
                    appName, entities != null ? entities.size() : 0, pages != null ? pages.size() : 0,
                    testUrl);

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
