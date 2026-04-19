package com.appbana.ai.agent;

import com.appbana.ai.agent.tool.Tool;
import com.appbana.ai.agent.tool.ToolCall;
import com.appbana.ai.agent.tool.ToolRegistry;
import com.appbana.ai.agent.tool.ToolResult;
import com.appbana.ai.agent.BatchedToolExecutor;
import com.appbana.ai.agent.PatternExecutor;
import com.appbana.ai.cache.SemanticCache;
import com.appbana.ai.dialogue.ConversationSpec;
import com.appbana.ai.dialogue.DialogueManager;
import com.appbana.ai.knowledge.KnowledgeBaseService;
import com.appbana.ai.knowledge.SchemaDefinition;
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
import java.util.Set;
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
    private final PatternExecutor patternExecutor;
    private final SemanticCache semanticCache;
    private boolean batchingEnabled = true; // Feature flag
    private boolean patternMatchingEnabled = true; // Cost optimization
    private boolean semanticCacheEnabled = true; // Cost optimization - cache LLM responses
    private KnowledgeBaseService knowledgeBase = null; // Optional - RAG domain examples (Phase 4)

    public AiAgent(OpenAiLlmService llmService, ToolRegistry toolRegistry, AgentConfig config) {
        this.llmService = llmService;
        this.toolRegistry = toolRegistry;
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.batchedExecutor = new BatchedToolExecutor(llmService);
        this.patternExecutor = new PatternExecutor(toolRegistry);
        this.semanticCache = new SemanticCache();
        log.info("AiAgent initialized with {} tools, max iterations: {}, batching: enabled, patterns: enabled, semantic-cache: enabled",
                toolRegistry.getToolCount(), config.getMaxIterations());
    }

    /**
     * Attach a KnowledgeBaseService so the agent can inject domain-specific
     * few-shot examples into scaffold prompts. Optional — agent works without it.
     */
    public AiAgent withKnowledgeBase(KnowledgeBaseService kb) {
        this.knowledgeBase = kb;
        log.info("AiAgent: RAG domain examples enabled");
        return this;
    }

    /**
     * Enable or disable batched execution (for testing/debugging)
     */
    public void setBatchingEnabled(boolean enabled) {
        this.batchingEnabled = enabled;
        log.info("Batched execution {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Enable or disable pattern matching (for testing/debugging)
     */
    public void setPatternMatchingEnabled(boolean enabled) {
        this.patternMatchingEnabled = enabled;
        log.info("Pattern matching {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Enable or disable semantic caching (for testing/debugging)
     */
    public void setSemanticCacheEnabled(boolean enabled) {
        this.semanticCacheEnabled = enabled;
        log.info("Semantic cache {}", enabled ? "enabled" : "disabled");
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

            // COST OPTIMIZATION: Try pattern matching first (no LLM call)
            if (patternMatchingEnabled) {
                java.util.Optional<AgentResponse> patternResult = patternExecutor.tryExecute(userMessage, context);
                if (patternResult.isPresent()) {
                    log.info("[AGENT] Pattern matched - skipping LLM call (100% cost savings)");
                    return patternResult.get();
                }
            }

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

            // Fail-safe limit
            int effectiveMaxIterations = Math.min(config.getMaxIterations(), 5);
            log.info("[AGENT] Effective max iterations: {}", effectiveMaxIterations);

            // Agent loop
            int consecutiveFailures = 0;
            for (int iteration = 1; iteration <= effectiveMaxIterations; iteration++) {
                log.info("[AGENT] === Iteration {} / {} ===", iteration, effectiveMaxIterations);

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
                    boolean allToolsFailed = true;
                    for (ToolResult result : results) {
                        step.addToolResult(result);
                        log.info("[AGENT] {}", result.getSummary());
                        if (result.isSuccess()) {
                            allToolsFailed = false;
                        }
                    }

                    // Abort early after 2 consecutive all-tools-failed iterations to avoid
                    // burning the remaining budget on the same unrecoverable error.
                    if (allToolsFailed) {
                        consecutiveFailures++;
                        log.warn("[AGENT] All tools failed in iteration {} (consecutive failures: {}).",
                                iteration, consecutiveFailures);
                        if (consecutiveFailures >= 2) {
                            log.warn("[AGENT] Aborting early after {} consecutive tool failures to save cost.",
                                    consecutiveFailures);
                            steps.add(step);
                            return AgentResponse.error(
                                    "I was unable to complete your request after multiple attempts. " +
                                    "The last error was: " + results.get(0).getError() + 
                                    ". Please check the app exists and try rephrasing your request.",
                                    steps,
                                    System.currentTimeMillis() - startTime);
                        }
                    } else {
                        consecutiveFailures = 0; // reset on any partial success
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
     * Integrates SemanticCache for cost optimization
     */
    private AgentThought think(String userMessage, List<AgentResponse.AgentStep> history, AgentContext context) {
        try {
            // Build prompt with system instructions, tools, history, and user message
            String prompt = buildAgentPrompt(userMessage, history, context);

            if (config.isDebugMode()) {
                log.debug("[AGENT] Prompt:\n{}", prompt);
            }

            // COST OPTIMIZATION: Check semantic cache before LLM call
            String llmResponse = null;
            if (semanticCacheEnabled) {
                java.util.Optional<com.appbana.ai.cache.SemanticCache.CachedResponse> cachedResponse = 
                    semanticCache.get(prompt, "agent_think");
                if (cachedResponse.isPresent()) {
                    llmResponse = cachedResponse.get().response();
                    log.info("[AGENT] SemanticCache HIT - skipping LLM call (100% cost savings for this request)");
                }
            }

            // Call LLM if not cached - with JSON mode to guarantee valid JSON output
            if (llmResponse == null) {
                // Use chatWithJsonMode to enforce valid JSON (prevents parse failures)
                // Falls back to regular chat if JSON mode call fails (e.g. unsupported model)
                try {
                    llmResponse = llmService.chatWithJsonMode(prompt);
                    log.debug("[AGENT] LLM responded via JSON mode");
                } catch (Exception jsonModeEx) {
                    log.warn("[AGENT] JSON mode unavailable ({}), falling back to standard chat", jsonModeEx.getMessage());
                    llmResponse = llmService.chat(prompt, "agent_think");
                }
                
                // Store in cache for future similar requests
                if (semanticCacheEnabled) {
                    semanticCache.put(prompt, llmResponse, Map.of("taskType", "agent_think"));
                    log.debug("[AGENT] Stored response in SemanticCache");
                }
            }

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

                        ## SPECIFICATION DRIVEN DEVELOPMENT (CRITICAL WORKFLOW)
                        **CRITICAL**: When the user asks to create a new application, YOU MUST NEVER scaffold it immediately. You must follow this two-phase process:

                        ### PHASE 1: Specification (TALK) - BUSINESS FRIENDLY
                        1. Listen to the user describe their app (e.g., "Build a Salon Booking App").
                        2. Do NOT run any tools. Think like a business analyst: what information does this business need to track, and what will their team need to do daily?
                        3. Output a `final_answer` written entirely in **plain business English**. NO technical jargon. Format it as follows:

                        ---
                        ## 🚀 [Friendly App Name]
                        [One warm sentence describing what this app helps the business achieve.]

                        ## 📦 What We'll Keep Track Of
                        ### [Friendly Name, e.g. "Customers"]
                        [One-sentence plain description, e.g. "Stores the details of every person who visits the salon."]
                        - Full name and contact details (phone, email)
                        - [other information in plain English, no field IDs or types]

                        ### [Friendly Name, e.g. "Appointments"]
                        [Plain description]
                        - Which customer booked, what service they want
                        - Date and time of the visit, current status (Scheduled / Done / Cancelled)

                        ---
                        ## 🖥️ Screens Your Team Will See
                        - **[Friendly Screen Name]** — [What the user does on this screen, e.g. "View all customers and search by name"]
                        - **[Friendly Screen Name]** — [Plain description]
                        ---

                        4. STRICT RULES FOR THE SPECIFICATION:
                           - **NEVER** use technical field names like `id`, `first_name`, `customer_id`, `appointment_date`
                           - **NEVER** mention data types like `text`, `reference`, `datetime`, `decimal`, `status`, `longtext`
                           - **NEVER** mention URL paths like `/customers/new` or terms like `type: list`, `type: form`, `entityName`
                           - Describe information in plain English: "customer's full name", "date and time of the visit", "booking status"
                           - Name screens like a business person: "Customer List", "Book Appointment", "Service Menu" — NOT "CustomerList" or "CreateAppointmentPage"
                           - Use a friendly emoji per entity section (👤 for people, 📅 for appointments, ✂️ for services, etc.)
                        5. Close with: "Does this match what you had in mind? Feel free to ask me to add, remove, or change anything. When you're ready, just say **Yes, let's build it!** ✅"

                        ### PHASE 2: Execution (ACT)
                        1. ONLY after the user explicitly says 'yes', 'build it', 'proceed', or clearly approves, proceed.
                        2. Internally map the plain-English spec back to proper technical entities and fields.
                        3. Call `scaffold_app` ONCE with the full JSON structure.

                        **DO NOT** use `create_app`, `create_entity`, `generate_page` individually unless the user explicitly modifies an existing app individually.

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
                        6. **Validation & Patterns (CRITICAL)**:
                           - **NEVER** use regex patterns for fields meant for human names (e.g., Full Name, First Name) as they often contain spaces, hyphens, or multiple words.
                           - For `pattern` and other validation fields, use a JSON `null` value if no specific validation is needed. **NEVER** use the literal string "null".
                           - Only use patterns for strictly formatted fields like `postcode` or `tax_id`.

                        ## EXECUTION RULES
                        1. **NO RETRIES**: If a tool fails (e.g., validation error), **STOP IMMEDIATELY**. Do not retry the same call. Report the error to the user and ask for guidance. Repeated failures cost money and frustrate users.
                        2. **Check Your Work**:
                           - Before calling any tool, verify `type` is one of: [text, number, decimal, boolean, date, datetime, email, phone, status, reference, longtext].
                           - Do NOT invent types like "money" or "currency" (use `decimal`).
                        3. **Context Sensitivity**:
                           - Always use the `appId` from context when calling tools like `create_page` or `create_entity`.
                           - If `appId` is "default" or missing, and the user wants to add to an app, ASK WHICH APP.

                        ## TONE
                        - During Phase 1 Specification: Warm, friendly, and non-technical. You are a helpful business consultant.
                        - During Phase 2 Execution: Precise, expert, and efficient. You are a senior engineer.
                        - Always be encouraging. Make the user feel confident about what they're building.
                        """);

        // Available tools — filtered by conversation state to prevent the LLM from
        // calling build tools (scaffold_app, deploy_app, etc.) before the user has
        // confirmed.  If no state is stored in context, we fall back to all tools.
        prompt.append("## Available Tools\n\n");

        Set<String> allowedTools = resolveAllowedTools(context);
        if (allowedTools != null) {
            prompt.append(toolRegistry.getToolDescriptions(allowedTools));
            log.debug("[AGENT] Tool filter active — state={}, exposed={}",
                    context.getVariable("conversation_state"), allowedTools);
        } else {
            prompt.append(toolRegistry.getToolDescriptions());
        }
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

        // 0. Current Context (CRITICAL for Context-Aware Rules)
        prompt.append("## CURRENT EXECUTION CONTEXT\n");
        prompt.append(String.format("- **Tenant ID**: %s\n", context.tenantId()));
        prompt.append(
                String.format("- **App ID**: %s\n", context.appId() != null ? context.appId() : "(none selected)"));
        prompt.append(String.format("- **User ID**: %s\n", context.userId()));
        prompt.append("\n");

        // 0b. Spec Coverage Tracker — dynamic checklist of what's been discussed
        if (context.hasVariable("chat_history")) {
            try {
                @SuppressWarnings("unchecked")
                List<ConversationMemory.Conversation> specHistory =
                        (List<ConversationMemory.Conversation>) context.getVariable("chat_history");
                ConversationSpec spec = ConversationSpec.analyse(specHistory, userMessage);
                String snippet = spec.toPromptSnippet();
                if (!snippet.isEmpty()) {
                    prompt.append(snippet);
                }
            } catch (Exception e) {
                log.warn("[AGENT] Failed to build spec coverage tracker: {}", e.getMessage());
            }
        }

        // 0c. Domain Examples — RAG-retrieved few-shot templates (Phase 4)
        if (knowledgeBase != null) {
            try {
                List<SchemaDefinition> domainExamples = knowledgeBase.getDomainExamples(userMessage, 2);
                if (!domainExamples.isEmpty()) {
                    prompt.append("## DOMAIN EXAMPLES (similar apps built before)\n");
                    prompt.append("Use these as reference for correct field types. Do NOT copy them verbatim — adapt to the user's request.\n\n");
                    for (SchemaDefinition example : domainExamples) {
                        prompt.append("**").append(example.getDescription()).append("**\n");
                        if (example.getMetadata() != null && example.getMetadata().containsKey("entities")) {
                            @SuppressWarnings("unchecked")
                            Map<String, String> entities = (Map<String, String>) example.getMetadata().get("entities");
                            entities.forEach((entityName, fields) ->
                                    prompt.append("  ").append(entityName).append(": ").append(fields).append("\n"));
                        }
                        prompt.append("\n");
                    }
                }
            } catch (Exception e) {
                log.debug("[AGENT] Domain examples unavailable: {}", e.getMessage());
            }
        }

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

        // 4. User Preferences (Learning)
        if (context.hasVariable("user_preferences")) {
            @SuppressWarnings("unchecked")
            Map<String, String> prefs = (Map<String, String>) context.getVariable("user_preferences");
            if (prefs != null && !prefs.isEmpty()) {
                prompt.append("## USER PREFERENCES & STYLE\n");
                prompt.append("You MUST respect the following user preferences:\n");
                prefs.forEach((k, v) -> prompt.append(String.format("- **%s**: %s\n", k, v)));
                prompt.append("\n");
            }
        }

        prompt.append("Respond with JSON only:");

        return prompt.toString();
    }

    /**
     * Resolve the set of tool names allowed for the current conversation state.
     * Returns {@code null} when no state is set (backwards-compatible: show all).
     */
    private Set<String> resolveAllowedTools(AgentContext context) {
        if (!context.hasVariable("conversation_state")) {
            return null;
        }
        try {
            String stateName = (String) context.getVariable("conversation_state");
            DialogueManager.ConversationState state =
                    DialogueManager.ConversationState.valueOf(stateName);
            // Create a temporary DialogueManager just to call getAllowedTools.
            // getAllowedTools() is a pure function — no side effects.
            return new DialogueManager().getAllowedTools(state);
        } catch (Exception e) {
            log.warn("[AGENT] Could not resolve allowed tools from conversation_state: {}", e.getMessage());
            return null;
        }
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
//     private AgentResponse processBatchedCreateApp(String userMessage, AgentContext context, long startTime) {
//         List<AgentResponse.AgentStep> steps = new ArrayList<>();
// 
//         try {
//             // Extract app name from user message (simple heuristic)
//             String appName = extractAppName(userMessage);
//             String appDescription = null;
//             List<String> entityNames = List.of(); // Let LLM decide default entities
// 
//             log.info("[AGENT-BATCHED] Creating app '{}' using batched execution", appName);
// 
//             // Execute batched workflow
//             Map<String, Object> batchedResult = batchedExecutor.batchCreateApp(appName, appDescription, entityNames);
// 
//             // Execute create_app tool with batched result
//             Tool createAppTool = toolRegistry.getTool("create_app");
//             if (createAppTool == null) {
//                 return AgentResponse.error("create_app tool not found", steps,
//                         System.currentTimeMillis() - startTime);
//             }
// 
//             Map<String, Object> appData = (Map<String, Object>) batchedResult.get("app");
//             Map<String, Object> createAppArgs = Map.of(
//                     "name", appData.get("name"),
//                     "description", appData.getOrDefault("description", ""),
//                     "version", appData.getOrDefault("version", "1.0.0"));
// 
//             ToolResult createAppResult = createAppTool.execute(createAppArgs, context);
// 
//             if (!createAppResult.isSuccess()) {
//                 return AgentResponse.error("Failed to create app: " + createAppResult.getError(),
//                         steps, System.currentTimeMillis() - startTime);
//             }
// 
//             // Extract appId for subsequent steps
//             Map<String, Object> resultData = (Map<String, Object>) createAppResult.getData();
//             String appId = (String) resultData.get("id");
// 
//             log.info("[AGENT-BATCHED] App '{}' created successfully (ID: {})", appName, appId);
// 
//             // Execute create_entity tools for each entity
//             List<Map<String, Object>> entities = (List<Map<String, Object>>) batchedResult.get("entities");
//             Tool createEntityTool = toolRegistry.getTool("create_entity");
// 
//             if (createEntityTool != null && entities != null) {
//                 for (Map<String, Object> entity : entities) {
//                     ToolResult entityResult = createEntityTool.execute(entity, context);
//                     if (!entityResult.isSuccess()) {
//                         log.warn("[AGENT-BATCHED] Failed to create entity: {}", entity.get("name"));
//                     }
//                 }
//                 log.info("[AGENT-BATCHED] Created {} entities", entities.size());
//             }
// 
//             // Execute create_page tools for each page
//             List<Map<String, Object>> pages = (List<Map<String, Object>>) batchedResult.get("pages");
//             Tool createPageTool = toolRegistry.getTool("create_page");
// 
//             if (createPageTool != null && pages != null) {
//                 for (Map<String, Object> page : pages) {
//                     Map<String, Object> pageArgs = new HashMap<>(page);
//                     pageArgs.put("appId", appId); // Explicitly pass appId
//                     ToolResult pageResult = createPageTool.execute(pageArgs, context);
//                     if (!pageResult.isSuccess()) {
//                         log.warn("[AGENT-BATCHED] Failed to create page: {}", page.get("name"));
//                     }
//                 }
//                 log.info("[AGENT-BATCHED] Created {} pages", pages.size());
//             }
// 
//             // NEW: Execute deploy_app tool
//             log.info("[AGENT-BATCHED] Deploying app '{}'...", appName);
//             Tool deployTool = toolRegistry.getTool("deploy_app");
//             String testUrl = " (link pending) ";
//             if (deployTool != null) {
//                 ToolResult deployResult = deployTool.execute(Map.of("appId", appId), context);
//                 if (deployResult.isSuccess() && deployResult.getData() instanceof Map) {
//                     Map<String, Object> deployData = (Map<String, Object>) deployResult.getData();
//                     testUrl = (String) deployData.getOrDefault("testUrl", " (link pending) ");
//                     log.info("[AGENT-BATCHED] App deployed successfully. URL: {}", testUrl);
//                 } else {
//                     log.warn("[AGENT-BATCHED] Deployment failed: {}", deployResult.getError());
//                 }
//             }
// 
//             long elapsed = System.currentTimeMillis() - startTime;
//             String finalAnswer = String.format(
//                     "✅ Successfully created and deployed app '%s' with %d entities and %d pages!\\n\\n" +
//                             "🔗 **Test your app here**: %s\\n\\n" +
//                             "You can now add, edit, and view records in your new application.",
//                     appName, entities != null ? entities.size() : 0, pages != null ? pages.size() : 0,
//                     testUrl);
// 
//             return AgentResponse.success(finalAnswer, steps, elapsed);
// 
//         } catch (Exception e) {
//             log.error("[AGENT-BATCHED] Failed to execute batched create-app", e);
//             return AgentResponse.error("Batched execution failed: " + e.getMessage(),
//                     steps, System.currentTimeMillis() - startTime);
//         }
//     }

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
