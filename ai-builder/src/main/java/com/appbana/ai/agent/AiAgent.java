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
import com.appbana.ai.knowledge.DomainBlueprintPrompt;
import com.appbana.ai.knowledge.KnowledgeBaseService;
import com.appbana.ai.knowledge.SchemaDefinition;
import com.appbana.ai.llm.LlmService;
import com.appbana.ai.llm.LlmRegistry;
import com.appbana.ai.rag.ConversationMemory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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

    private final LlmRegistry llmRegistry;
    private final ToolRegistry toolRegistry;
    private final AgentConfig config;
    private final ObjectMapper objectMapper;
    private final BatchedToolExecutor batchedExecutor;
    private final PatternExecutor patternExecutor;
    private final SemanticCache semanticCache;
    private boolean batchingEnabled = true; // Feature flag
    private boolean patternMatchingEnabled = true; // Cost optimization
    private boolean semanticCacheEnabled = false; // DISABLED TEMPORARILY: Cache returning stale prompt responses
    private KnowledgeBaseService knowledgeBase = null; // Optional - RAG domain examples (Phase 4)
    // Optional — when set the agent can fetch the currently-selected app's entity
    // summary and inject it into the system prompt so the LLM doesn't have to guess
    // whether "Department" is an entity or a field on Employee.
    private String backendBaseUrl = null;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public AiAgent(LlmRegistry llmRegistry, ToolRegistry toolRegistry, AgentConfig config) {
        this.llmRegistry = llmRegistry;
        this.toolRegistry = toolRegistry;
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.batchedExecutor = new BatchedToolExecutor(null); // Will be set per call or registry
        this.patternExecutor = new PatternExecutor(toolRegistry);
        this.semanticCache = new SemanticCache();
        log.info("AiAgent initialized with {} tools, using LlmRegistry for multi-provider support",
                toolRegistry.getToolCount());
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
     * Attach the app-bana-service base URL so the agent can lazily fetch a compact
     * entity summary for the currently-selected app and inject it into the LLM
     * prompt. Without this the LLM has to guess entity/field names, which leads to
     * wasted tool calls (e.g. calling get_entity_details on a field name).
     */
    public AiAgent withBackendBaseUrl(String url) {
        this.backendBaseUrl = url;
        log.info("AiAgent: backend base URL set for entity summary injection: {}", url);
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
        return process(userMessage, context, null, null);
    }

    /**
     * Process a user message through the agent loop, emitting SSE events to the supplied emitter.
     * The agent loop runs synchronously on the calling (virtual) thread while events are pushed
     * to the client in real time.
     */
    public AgentResponse processWithStream(String userMessage, AgentContext context,
                                           String provider, List<String> images,
                                           StreamEmitter emitter) {
        LlmService llmService = llmRegistry.getService(provider);
        long startTime = System.currentTimeMillis();
        List<AgentResponse.AgentStep> steps = new ArrayList<>();

        // Emit the current dialogue state as the first event so the UI can react
        String conversationState = (String) context.getVariable("conversation_state");
        if (conversationState != null) {
            emitter.state(conversationState);
        }

        try {
            log.info("[AGENT-STREAM] Starting stream processing for user: {}", context.userId());

            // Pattern matching (cost optimisation, no LLM call)
            if (patternMatchingEnabled) {
                java.util.Optional<AgentResponse> patternResult = patternExecutor.tryExecute(userMessage, context);
                if (patternResult.isPresent()) {
                    AgentResponse resp = patternResult.get();
                    emitter.token(resp.getFinalAnswer());
                    emitter.done(context.sessionId(), resp.getFinalAnswer());
                    return resp;
                }
            }

            // Retrieved once per request: userMessage is fixed for the whole loop, and this is a
            // paid embedding + vector search. Computed after the pattern-match short-circuit so a
            // pattern-matched request pays nothing.
            String blueprintSection = buildDomainBlueprintSection(userMessage);

            int effectiveMaxIterations = Math.min(config.getMaxIterations(), 10);
            int consecutiveFailures = 0;
            Set<String> failedSignatures = new HashSet<>();
            // Track successful tool signatures per request to break repeat-success loops
            // (e.g. LLM calling list_apps 6 times in a row with identical arguments).
            java.util.Map<String, Integer> successCounts = new java.util.HashMap<>();

            for (int iteration = 1; iteration <= effectiveMaxIterations; iteration++) {
                log.info("[AGENT-STREAM] === Iteration {} / {} ===", iteration, effectiveMaxIterations);

                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed > config.getTimeoutSeconds() * 1000L) {
                    log.warn("[AGENT-STREAM] Timeout reached after {}ms", elapsed);
                    return AgentResponse.error("Agent timeout after " + elapsed + "ms", steps, elapsed);
                }

                AgentThought thought = think(userMessage, steps, context, llmService, images, blueprintSection);
                if (thought == null) {
                    String msg = "Sorry — I couldn't get a response from the AI model. Please try again in a moment.";
                    emitter.token(msg);
                    emitter.done(context.sessionId(), msg);
                    return AgentResponse.error(msg, steps, System.currentTimeMillis() - startTime);
                }

                AgentResponse.AgentStep step = new AgentResponse.AgentStep(iteration, thought.getThinking());

                // Stall guard: the LLM returned neither a final answer nor a tool call.
                // Without this the loop would silently burn iterations and the user would
                // see an empty assistant bubble. Convert it into a helpful fallback.
                if (!thought.isFinalAnswer() && !thought.hasToolCalls()) {
                    steps.add(step);
                    String stallMsg = buildStallFallback(steps);
                    emitter.token(stallMsg);
                    emitter.done(context.sessionId(), stallMsg);
                    return AgentResponse.success(stallMsg, steps, System.currentTimeMillis() - startTime);
                }

                if (thought.isFinalAnswer()) {
                    steps.add(step);
                    String finalAnswer = thought.getFinalAnswer();
                    emitter.token(finalAnswer);
                    emitter.done(context.sessionId(), finalAnswer);
                    return AgentResponse.success(finalAnswer, steps, System.currentTimeMillis() - startTime);
                }

                if (thought.hasToolCalls()) {
                    List<ToolResult> results = executeToolsWithStream(thought.getToolCalls(), context, emitter);
                    boolean allToolsFailed = true;
                    boolean loopDetected = false;

                    for (ToolResult result : results) {
                        String signature = result.getToolName() + ":" + result.getArguments();
                        if (!result.isSuccess()) {
                            if (failedSignatures.contains(signature)) {
                                result.setError("CRITICAL: Repeated failure. " + result.getError());
                            }
                            failedSignatures.add(signature);
                        } else {
                            failedSignatures.remove(signature);
                            allToolsFailed = false;
                            int count = successCounts.merge(signature, 1, Integer::sum);
                            if (count >= 2) {
                                // Same tool + same args already succeeded this request. Do not
                                // let the LLM keep re-issuing it — abort the loop and hand back
                                // whatever answer we have so far.
                                log.warn("[AGENT-STREAM] Aborting loop: tool '{}' already succeeded with identical args {} time(s)",
                                        result.getToolName(), count);
                                loopDetected = true;
                            }
                        }
                        step.addToolResult(result);
                    }

                    if (loopDetected) {
                        steps.add(step);
                        // Operation-aware fallback: if a mutation tool (e.g. batch_update_entities)
                        // succeeded, tell the user the change went through instead of the old
                        // "please tell me which app" message which made successful edits look failed.
                        String finalMsg;
                        ToolResult batchOk = results.stream()
                                .filter(r -> "batch_update_entities".equals(r.getToolName()) && r.isSuccess())
                                .findFirst().orElse(null);
                        if (batchOk != null) {
                            finalMsg = buildBatchUpdateFinalMessage(batchOk);
                        } else if (results.stream().anyMatch(r -> r.isSuccess() && isMutationTool(r.getToolName()))) {
                            finalMsg = "I've made the requested changes. Refresh the preview if you don't see them yet.";
                        } else {
                            // Read-only loop (e.g. LLM kept calling list_entities). Use
                            // buildStallFallback which summarises tools that ran and asks a
                            // sensible follow-up — do NOT tell the user to "pick an app"
                            // when they clearly already have one selected.
                            finalMsg = buildStallFallback(steps);
                        }
                        emitter.token(finalMsg);
                        emitter.done(context.sessionId(), finalMsg);
                        return AgentResponse.success(finalMsg, steps, System.currentTimeMillis() - startTime);
                    }

                    if (allToolsFailed) {
                        consecutiveFailures++;
                        if (consecutiveFailures >= 3) {
                            steps.add(step);
                            String msg = "I ran into repeated errors trying to complete that request. " +
                                    "Details: " + results.get(0).getError() + ". Please rephrase or try again.";
                            emitter.token(msg);
                            emitter.done(context.sessionId(), msg);
                            return AgentResponse.error(msg, steps, System.currentTimeMillis() - startTime);
                        }
                    } else {
                        consecutiveFailures = 0;

                        boolean scaffoldSucceeded = results.stream()
                                .anyMatch(r -> "scaffold_app".equals(r.getToolName()) && r.isSuccess());
                        if (scaffoldSucceeded) {
                            steps.add(step);
                            ToolResult scaffoldResult = results.stream()
                                    .filter(r -> "scaffold_app".equals(r.getToolName()) && r.isSuccess())
                                    .findFirst().get();

                            String finalMsg = buildScaffoldFinalMessage(scaffoldResult, context);
                            emitter.token(finalMsg);
                            emitter.done(context.sessionId(), finalMsg);
                            return AgentResponse.success(finalMsg, steps, System.currentTimeMillis() - startTime);
                        }

                        // Same shortcut for batch entity edits — once the mutation succeeds we
                        // don't need to hand control back to the LLM (which tends to loop and
                        // re-issue the identical call, wasting tokens and confusing the user).
                        ToolResult batchOk = results.stream()
                                .filter(r -> "batch_update_entities".equals(r.getToolName()) && r.isSuccess())
                                .findFirst().orElse(null);
                        if (batchOk != null) {
                            steps.add(step);
                            String finalMsg = buildBatchUpdateFinalMessage(batchOk);
                            emitter.token(finalMsg);
                            emitter.done(context.sessionId(), finalMsg);
                            return AgentResponse.success(finalMsg, steps, System.currentTimeMillis() - startTime);
                        }
                    }
                }

                steps.add(step);
            }

            String errMsg = buildStallFallback(steps);
            emitter.token(errMsg);
            emitter.done(context.sessionId(), errMsg);
            return AgentResponse.error(errMsg, steps, System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            log.error("[AGENT-STREAM] Error in streaming processing", e);
            String msg = "Sorry — something went wrong while processing your request. Please try again.";
            try {
                emitter.token(msg);
                emitter.done(context.sessionId(), msg);
            } catch (Exception ignored) {
                // Emitter may already be closed; nothing else we can do here.
            }
            return AgentResponse.error("Agent error: " + e.getMessage(), steps,
                    System.currentTimeMillis() - startTime);
        }
    }

    /**
     * Execute tools and emit tool_call_start / tool_call_end events for each one.
     */
    private List<ToolResult> executeToolsWithStream(List<ToolCall> toolCalls, AgentContext context,
                                                     StreamEmitter emitter) {
        if (toolCalls.isEmpty()) return Collections.emptyList();

        boolean requiresSequential = toolCalls.stream()
                .anyMatch(call -> call.getName().equals("create_entity") ||
                        call.getName().equals("generate_page") ||
                        call.getName().equals("create_app"));

        List<ToolResult> results = new ArrayList<>();

        if (requiresSequential) {
            for (ToolCall call : toolCalls) {
                String id = call.getName() + "-" + System.nanoTime();
                emitter.toolCallStart(id, call.getName(), call.getArguments());
                long t = System.currentTimeMillis();
                ToolResult result;
                try {
                    Tool tool = toolRegistry.getTool(call.getName());
                    if (tool == null) {
                        result = ToolResult.error(call.getName(), "Tool not found: " + call.getName());
                    } else {
                        result = tool.execute(call.getArguments(), context);
                        result.setExecutionTimeMs(System.currentTimeMillis() - t);
                        result.setToolName(call.getName());
                    }
                } catch (Exception e) {
                    log.error("[AGENT-STREAM] Tool execution failed: {}", call.getName(), e);
                    result = ToolResult.error(call.getName(), "Execution error: " + e.getMessage());
                }
                emitter.toolCallEnd(id, result.isSuccess() ? "ok" : "error",
                        result.isSuccess() ? result.getData() : result.getError());
                results.add(result);
            }
        } else {
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<Callable<ToolResult>> tasks = new ArrayList<>();
                List<String> ids = new ArrayList<>();
                for (ToolCall call : toolCalls) {
                    String id = call.getName() + "-" + System.nanoTime();
                    ids.add(id);
                    emitter.toolCallStart(id, call.getName(), call.getArguments());
                    tasks.add(() -> {
                        long t = System.currentTimeMillis();
                        try {
                            Tool tool = toolRegistry.getTool(call.getName());
                            if (tool == null) return ToolResult.error(call.getName(), "Tool not found: " + call.getName());
                            ToolResult res = tool.execute(call.getArguments(), context);
                            res.setExecutionTimeMs(System.currentTimeMillis() - t);
                            res.setToolName(call.getName());
                            try { res.setArguments(objectMapper.writeValueAsString(call.getArguments())); } catch (Exception ignored) {}
                            return res;
                        } catch (Exception e) {
                            return ToolResult.error(call.getName(), "Execution error: " + e.getMessage());
                        }
                    });
                }
                List<Future<ToolResult>> futures = executor.invokeAll(tasks);
                for (int i = 0; i < futures.size(); i++) {
                    ToolResult res;
                    try { res = futures.get(i).get(); } catch (Exception e) {
                        res = ToolResult.error("unknown", "Future failed: " + e.getMessage());
                    }
                    emitter.toolCallEnd(ids.get(i), res.isSuccess() ? "ok" : "error",
                            res.isSuccess() ? res.getData() : res.getError());
                    results.add(res);
                }
            } catch (Exception e) {
                log.error("[AGENT-STREAM] Critical error in parallel streaming execution", e);
                results.add(ToolResult.error("agent_system", "Parallel execution failed: " + e.getMessage()));
            }
        }
        return results;
    }

    /** Reusable scaffold success message builder (used by both sync and stream paths). */
    private String buildScaffoldFinalMessage(ToolResult scaffoldResult, AgentContext context) {
        String finalMsg = "Your app has been built and deployed successfully! You can now find it in the Apps section.";
        Object data = scaffoldResult.getData();
        if (data instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = (Map<String, Object>) data;
            String appName = (String) dataMap.get("appName");
            @SuppressWarnings("unchecked")
            List<?> pages = (List<?>) dataMap.get("pagesCreated");
            @SuppressWarnings("unchecked")
            List<?> entities = (List<?>) dataMap.get("entitiesCreated");
            String appId = (String) dataMap.get("appId");
            String tenantId = context.tenantId() != null ? context.tenantId() : "default";
            if (appName != null) {
                String appUrl = appId != null ? String.format("/run/%s/%s", tenantId, appId) : null;
                String urlLine = appUrl != null
                        ? String.format("\n\n🌐 **Open your app:** [Click here to launch it](%s)", appUrl)
                        : "\n\nYou can open it via 📂 Open App in the top toolbar.";
                finalMsg = String.format(
                        "🎉 **%s** has been built and deployed successfully!\n\n" +
                        "- **%d entities** created: %s\n" +
                        "- **%d pages** created: %s%s",
                        appName,
                        entities != null ? entities.size() : 0, entities,
                        pages != null ? pages.size() : 0, pages,
                        urlLine);
            }
        }
        return finalMsg;
    }

    /**
     * Reusable batch-update success message builder. Turns the tool result into a
     * user-facing "done" message that names the entities/operations touched, so the
     * user isn't left wondering whether their add_fields / remove_fields / rename
     * request actually landed.
     */
    private String buildBatchUpdateFinalMessage(ToolResult batchResult) {
        Object data = batchResult.getData();
        if (data instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = (Map<String, Object>) data;
            @SuppressWarnings("unchecked")
            List<String> successful = (List<String>) dataMap.get("successfulUpdates");
            if (successful != null && !successful.isEmpty()) {
                // successful entries look like "Employee:add_fields"
                String detail = String.join(", ", successful);
                return "✅ Done — I applied the requested changes: " + detail
                        + ". Refresh the preview if you don't see them yet.";
            }
        }
        return "✅ Done — I applied the requested changes. Refresh the preview if you don't see them yet.";
    }

    /**
     * Fallback message when the agent loop finishes without the LLM producing a final answer
     * (e.g. it kept issuing read-only tool calls, stalled, or hit max iterations). Summarises
     * what did happen so the user isn't left staring at an empty chat bubble.
     */
    private String buildStallFallback(List<AgentResponse.AgentStep> steps) {
        // Collect names of tools that ran successfully across all steps.
        java.util.LinkedHashSet<String> okTools = new java.util.LinkedHashSet<>();
        java.util.LinkedHashSet<String> failedTools = new java.util.LinkedHashSet<>();
        boolean anyMutation = false;
        for (AgentResponse.AgentStep s : steps) {
            List<ToolResult> results = s.getToolResults();
            if (results == null) continue;
            for (ToolResult r : results) {
                String name = r.getToolName() != null ? r.getToolName() : "unknown";
                if (r.isSuccess()) {
                    okTools.add(name);
                    if (isMutationTool(name)) anyMutation = true;
                } else {
                    failedTools.add(name);
                }
            }
        }

        if (anyMutation) {
            return "I've made the requested changes. Refresh the preview if you don't see them yet, "
                    + "or tell me what to adjust next.";
        }
        if (!okTools.isEmpty() && failedTools.isEmpty()) {
            return "I gathered the information I needed (" + String.join(", ", okTools) + ") "
                    + "but I haven't made any changes yet. Want me to go ahead — just say **yes** "
                    + "or tell me exactly what to change.";
        }
        if (!failedTools.isEmpty()) {
            return "I tried to complete that request but ran into problems with: "
                    + String.join(", ", failedTools) + ". Could you rephrase or give me more detail?";
        }
        return "I'm not sure what to do next. Could you tell me a bit more about what you'd like to change?";
    }

    private static final java.util.Set<String> MUTATION_TOOLS = java.util.Set.of(
            "scaffold_app", "create_app", "create_entity", "generate_page",
            "batch_update_entities", "generate_mock_data", "deploy_app", "rollback_app");

    private static boolean isMutationTool(String name) {
        return name != null && MUTATION_TOOLS.contains(name);
    }

    /**
     * Process a user message through the agent loop using a specific provider and optional images
     */
    public AgentResponse process(String userMessage, AgentContext context, String provider, List<String> images) {
        LlmService llmService = llmRegistry.getService(provider);
        long startTime = System.currentTimeMillis();
        List<AgentResponse.AgentStep> steps = new ArrayList<>();

        try {
            log.info("[AGENT] Starting processing for user: {} (Provider: {}, Images: {})", 
                context.userId(), provider != null ? provider : "default", 
                images != null ? images.size() : 0);

            // COST OPTIMIZATION: Try pattern matching first (no LLM call)
            if (patternMatchingEnabled) {
                java.util.Optional<AgentResponse> patternResult = patternExecutor.tryExecute(userMessage, context);
                if (patternResult.isPresent()) {
                    log.info("[AGENT] Pattern matched - skipping LLM call (100% cost savings)");
                    return patternResult.get();
                }
            }

            // Retrieved once per request: userMessage is fixed for the whole loop, and this is a
            // paid embedding + vector search. Computed after the pattern-match short-circuit so a
            // pattern-matched request pays nothing.
            String blueprintSection = buildDomainBlueprintSection(userMessage);

            // Fail-safe limit
            int effectiveMaxIterations = Math.min(config.getMaxIterations(), 10);
            
            // Agent loop
            int consecutiveFailures = 0;
            Set<String> failedSignatures = new HashSet<>(); 
            for (int iteration = 1; iteration <= effectiveMaxIterations; iteration++) {
                log.info("[AGENT] === Iteration {} / {} ===", iteration, effectiveMaxIterations);

                // Check timeout
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed > config.getTimeoutSeconds() * 1000) {
                    log.warn("[AGENT] Timeout reached after {}ms", elapsed);
                    return AgentResponse.error("Agent timeout after " + elapsed + "ms", steps, elapsed);
                }

                // 1. THINK - Ask LLM what to do
                AgentThought thought = think(userMessage, steps, context, llmService, images, blueprintSection);

                if (thought == null) {
                    log.error("[AGENT] Failed to get thought from LLM");
                    return AgentResponse.error("Failed to get response from LLM", steps, System.currentTimeMillis() - startTime);
                }

                log.debug("[AGENT] Thinking: {}", thought.getThinking());
                AgentResponse.AgentStep step = new AgentResponse.AgentStep(iteration, thought.getThinking());

                // 2. Check if done
                if (thought.isFinalAnswer()) {
                    log.info("[AGENT] Final answer reached after {} iterations", iteration);
                    steps.add(step);
                    return AgentResponse.success(thought.getFinalAnswer(), steps, System.currentTimeMillis() - startTime);
                }

                // 3. ACT - Execute tools
                if (thought.hasToolCalls()) {
                    List<ToolResult> results = executeTools(thought.getToolCalls(), context);
                    boolean allToolsFailed = true;

                    for (ToolResult result : results) {
                        String signature = result.getToolName() + ":" + result.getArguments();
                        if (!result.isSuccess()) {
                            if (failedSignatures.contains(signature)) {
                                result.setError("CRITICAL: Repeated failure. " + result.getError());
                            }
                            failedSignatures.add(signature);
                        } else {
                            failedSignatures.remove(signature);
                            allToolsFailed = false;
                        }
                        step.addToolResult(result);
                    }

                    if (allToolsFailed) {
                        consecutiveFailures++;
                        if (consecutiveFailures >= 3) {
                            steps.add(step);
                            return AgentResponse.error("Stuck after 3 failures: " + results.get(0).getError(), steps, System.currentTimeMillis() - startTime);
                        }
                    } else {
                        consecutiveFailures = 0;

                        // SHORT-CIRCUIT: If scaffold_app succeeded, return immediately.
                        // Do NOT loop back to LLM — it will call scaffold_app again and create duplicate apps.
                        boolean scaffoldSucceeded = results.stream()
                                .anyMatch(r -> "scaffold_app".equals(r.getToolName()) && r.isSuccess());
                        if (scaffoldSucceeded) {
                            steps.add(step);
                            ToolResult scaffoldResult = results.stream()
                                    .filter(r -> "scaffold_app".equals(r.getToolName()) && r.isSuccess())
                                    .findFirst().get();

                            // Build a friendly final message from the scaffold result data
                            String finalMsg = "Your app has been built and deployed successfully! You can now find it in the Apps section.";
                            Object data = scaffoldResult.getData();
                            if (data instanceof java.util.Map) {
                                @SuppressWarnings("unchecked")
                                java.util.Map<String, Object> dataMap = (java.util.Map<String, Object>) data;
                                String summaryFromData = (String) dataMap.get("summary");
                                String appName = (String) dataMap.get("appName");
                                @SuppressWarnings("unchecked")
                                java.util.List<?> pages = (java.util.List<?>) dataMap.get("pagesCreated");
                                @SuppressWarnings("unchecked")
                                java.util.List<?> entities = (java.util.List<?>) dataMap.get("entitiesCreated");

                                if (appName != null) {
                                    int pageCount = pages != null ? pages.size() : 0;
                                    int entityCount = entities != null ? entities.size() : 0;
                                    String appId = (String) dataMap.get("appId");
                                    String tenantId = context.tenantId() != null ? context.tenantId() : "default";
                                    String appUrl = (appId != null)
                                        ? String.format("/run/%s/%s", tenantId, appId)
                                        : null;
                                    String urlLine = (appUrl != null)
                                        ? String.format("\n\n🌐 **Open your app:** [Click here to launch it](%s)", appUrl)
                                        : "\n\nYou can open it via 📂 Open App in the top toolbar.";
                                    finalMsg = String.format(
                                        "🎉 **%s** has been built and deployed successfully!\n\n" +
                                        "- **%d entities** created: %s\n" +
                                        "- **%d pages** created: %s%s",
                                        appName, entityCount, entities, pageCount, pages, urlLine);
                                } else if (summaryFromData != null) {
                                    finalMsg = summaryFromData;
                                }
                            }

                            log.info("[AGENT] scaffold_app succeeded — returning final answer immediately (skipping further iterations)");
                            return AgentResponse.success(finalMsg, steps, System.currentTimeMillis() - startTime);
                        }
                    }
                }
                
                steps.add(step); // 4. OBSERVE
            }

            return AgentResponse.error("Max iterations reached", steps, System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            log.error("[AGENT] Error in processing", e);
            return AgentResponse.error("Agent error: " + e.getMessage(), steps, System.currentTimeMillis() - startTime);
        }
    }

    /**
     * Render the learned user preferences that AiChatController puts on the context under
     * "user_preferences". Without this the UserPreferenceEngine collects preferences that
     * never reach the model. Returns "" when there is nothing to say.
     */
    @SuppressWarnings("unchecked")
    private String buildUserPreferencesSection(AgentContext context) {
        Object raw = context.getVariable("user_preferences");
        if (!(raw instanceof Map<?, ?> prefs) || prefs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("## USER PREFERENCES & STYLE\n");
        sb.append("You MUST respect the following user preferences:\n");
        for (Map.Entry<String, Object> entry : ((Map<String, Object>) prefs).entrySet()) {
            sb.append("- **").append(entry.getKey()).append("**: ").append(entry.getValue()).append("\n");
        }
        return sb.toString();
    }

    /**
     * C4.4a — inject worked examples of similar apps (including which entities need a maker-checker
     * approval flow) into the live agent prompt.
     *
     * <p>This is the <b>only</b> route from the knowledge base into the model's prompt.
     * {@code AppBanaPromptEnhancer}/{@code AdvancedPromptEngine.buildPrompt} look like that route
     * and are not: {@code buildPrompt} has zero call sites, and {@code AiChatController} takes the
     * engine as a constructor parameter it never stores. Until now this class had the same gap in
     * miniature — {@code AiServer} has always called {@code withKnowledgeBase(...)}, and the field
     * was assigned and never read.
     *
     * <p>No try/catch: {@link KnowledgeBaseService#getDomainExamples} already swallows its own
     * failures and returns an empty list, so RAG being down degrades to a prompt without examples
     * rather than a failed request.
     *
     * <p><b>Call this once per request, not once per iteration.</b> It costs an OpenAI embedding
     * call plus a Qdrant search, and {@code userMessage} does not change across the agent loop, so
     * calling it from {@code think()} bought the same string up to ten times at ten times the price.
     * Both entry points compute it before their loop and pass the result down;
     * {@code ApprovalDomainTemplateTest.theBlueprintLookupIsPaidForOncePerRequestNotPerIteration}
     * fails if it is ever re-inlined.
     */
    private String buildDomainBlueprintSection(String userMessage) {
        if (knowledgeBase == null || userMessage == null || userMessage.isBlank()) {
            return "";
        }
        List<SchemaDefinition> blueprints = knowledgeBase.getDomainExamples(userMessage, 2);
        return DomainBlueprintPrompt.render(blueprints);
    }

    /**
     * Determine next action via LLM with multimodal support
     */
    private AgentThought think(String userMessage, List<AgentResponse.AgentStep> previousSteps,
                              AgentContext context, LlmService llmService, List<String> images,
                              String blueprintSection) {
        try {
            // Build prompt
            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("### SYSTEM INSTRUCTIONS ###\n");
            promptBuilder.append(buildSystemPrompt(context)).append("\n\n");

            promptBuilder.append("### CURRENT EXECUTION CONTEXT ###\n");
            promptBuilder.append(buildExecutionContext(context)).append("\n\n");

            promptBuilder.append("### AVAILABLE TOOLS ###\n");
            promptBuilder.append(toolRegistry.getToolDescriptions()).append("\n\n");

            if (blueprintSection != null && !blueprintSection.isEmpty()) {
                promptBuilder.append("### SIMILAR APP BLUEPRINTS ###\n");
                promptBuilder.append(blueprintSection).append("\n\n");
            }

            String preferencesSection = buildUserPreferencesSection(context);
            if (!preferencesSection.isEmpty()) {
                promptBuilder.append(preferencesSection).append("\n\n");
            }


            if (!previousSteps.isEmpty()) {
                promptBuilder.append("### CONVERSATION HISTORY ###\n");
                for (AgentResponse.AgentStep step : previousSteps) {
                    promptBuilder.append("Thought: ").append(step.getThinking()).append("\n");
                    for (ToolResult result : step.getToolResults()) {
                        promptBuilder.append("Tool [").append(result.getToolName()).append("] Output: ")
                                     .append(result.getSummary()).append("\n");
                    }
                }
                promptBuilder.append("\n");
            }
            
            promptBuilder.append("### USER REQUEST ###\n");
            promptBuilder.append(userMessage).append("\n\n");
            
            promptBuilder.append("### RESPONSE GUIDELINES (MANDATORY) ###\n");
            promptBuilder.append("1. Analyze the user request and any provided images.\n");
            promptBuilder.append("2. You MUST return your next step in VALID JSON format ONLY.\n");
            promptBuilder.append("3. DO NOT output code blocks or text outside the JSON.\n");
            promptBuilder.append("4. Use ONLY the authorized keys: 'thinking', 'tool_calls', or 'final_answer'.\n");
            promptBuilder.append("5. DO NOT use keys like 'action', 'response', or 'nextStep'.\n");
            promptBuilder.append("6. 'tool_calls' MUST be a JSON ARRAY. Each element must have 'name' (string) and 'arguments' (object).\n");
            promptBuilder.append("   CORRECT:   {\"thinking\": \"...\", \"tool_calls\": [{\"name\": \"scaffold_app\", \"arguments\": {...}}]}\n");
            promptBuilder.append("   INCORRECT: {\"tool_calls\": {\"scaffold_app\": {...}}}  <-- object format is FORBIDDEN\n\n");
            promptBuilder.append("Respond with JSON only:");

            String fullPrompt = promptBuilder.toString();
            String llmResponse;
            
            if (images != null && !images.isEmpty()) {
                llmResponse = llmService.chatWithJsonMode(fullPrompt, images);
            } else {
                llmResponse = llmService.chatWithJsonMode(fullPrompt);
            }

            if (config.isDebugMode()) {
                log.debug("[AGENT] LLM Response: {}", llmResponse);
            }

            return parseAgentThought(llmResponse);
        } catch (Exception e) {
            log.error("[AGENT] Error in think step", e);
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

                        String argsJson = "";
                        try {
                            argsJson = objectMapper.writeValueAsString(call.getArguments());
                        } catch (Exception e2) {
                            argsJson = call.getArguments().toString();
                        }

                        ToolResult result = tool.execute(call.getArguments(), context);
                        result.setExecutionTimeMs(System.currentTimeMillis() - startTime);
                        result.setToolName(call.getName());
                        result.setArguments(argsJson); // Required for Story 3.2
                        return result;

                    } catch (Exception e) {
                        log.error("[AGENT] Tool execution failed: " + call.getName(), e);
                        String args = (call.getArguments() != null) ? call.getArguments().toString() : "";
                        return ToolResult.error(call.getName(), "Execution error: " + e.getMessage(), args);
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
     * Build the core system instructions for the LLM
     */
    private String buildSystemPrompt(AgentContext context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(
                """
                        You are an AppBana AI assistant (Expert Architect & Data Modeler).
                        Your goal is to build robust, correct, and professional applications with "Zero Defects".

                        ## MULTIMODAL INTELLIGENCE (VISION ENABLED)
                        - You are equipped with advanced vision capabilities. You can ANALYZE and UNDERSTAND images provided by the user.
                        - You should use images for:
                          1. UI/UX inspiration (converting screenshots/sketches to AppBana pages).
                          2. Schema extraction (creating entities from database diagrams or spreadsheets).
                          3. Logic analysis (interpreting handwritten notes or flowcharts).
                        - **NEVER** refuse to read an image. If an image is provided, incorporate it into your thinking and execution plan.

                        ## CORE INSTRUCTIONS (ZERO-INTENT)
                        You are an autonomous agent. You must decide whether to TALK or ACT.

                        1. **GENERAL CONVERSATION (TALK)**:
                           - If the user greets you, asks a general question, or asks for clarification, DO NOT call any tools.
                           - Simply reply with a `final_answer`.

                        2. **APP BUILDING (ACT)**:
                           - If the user wants to build, modify, or deploy an app, YOU MUST call the appropriate tool.
                           - **Preferred Workflow**: Use `scaffold_app` for new apps AND when making bulk additions (new entities/pages) to an existing app (it is 10x faster and automatically merges into the current app if `context.appId` is set).

                        ## CONTEXT-AWARE ENTITY QUERYING (CRITICAL RULE)
                        1. **CHECK CONTEXT**: Before answering questions about entities (e.g., "How many fields in Employee?", "Show me the Customer entity"), you MUST check if an app is selected in `context.appId`.
                        2. **IF APP SELECTED**: Use the `list_entities` tool. It will automatically filter for the selected app.
                        3. **IF NO APP SELECTED**: DO NOT GUESS. You MUST ask the user to select an app first.
                           - Example: "Please select an application first so I know which 'Employee' entity you are referring to."
                           - Exception: If the user explicitly asks to "List all apps" or "Create a new app", you can proceed without a selected app.

                        ## SPECIFICATION DRIVEN DEVELOPMENT (CRITICAL WORKFLOW)
                        **CRITICAL**: When the user asks to create a new application or significantly modify an existing one, YOU MUST follow this two-phase process:

                        ### PHASE 1: Specification (TALK) - BUSINESS FRIENDLY
                        **GOLDEN RULE**: The moment the user names ANY business domain or app idea (e.g. "grocery store", "salon", "school", "skymap", "hospital"), you MUST **immediately** produce a full spec proposal in your `final_answer`. Do NOT ask clarifying questions first. Do NOT say "I will gather information". Do NOT acknowledge and wait.

                        You have expert domain knowledge. Use it:
                        - "grocery store" → Products (name, price, stock, category), Customers, Orders, Suppliers
                        - "salon" → Appointments, Services, Customers, Stylists
                        - "school" → Students, Teachers, Classes, Grades, Attendance
                        - "restaurant" → Menu Items, Tables, Orders, Reservations
                        - "skymap / celestial / astronomy" → Celestial Bodies (name, type, coordinates, magnitude), Observations, User Favorites
                        - Any other domain → apply your own knowledge to propose the most useful and complete set of entities

                        **NEVER** reply with: "I will gather information", "I understand you want to build", or any pure acknowledgement. Always respond with CONTENT — a full spec or a direct answer.

                        1. When the user describes their app, IMMEDIATELY output a `final_answer` written entirely in **plain business English**. NO technical jargon. Format it as follows:

                        ---
                        ## 🚀 [Friendly App/Feature Name]
                        [One warm sentence describing what this app/feature helps the business achieve.]

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
                        5. **CONVERSATIONAL FOLLOW-UPS (CRITICAL)**: If the user asks a clarification question (e.g., "How does the user find spices?"), requests a tweak, or gives feedback (e.g., "Show me a prototype"), **DO NOT repeat the proposal template**. Instead, answer naturally and conversationally like a human.
                           - If they ask for a "prototype", explain that you will build the app for them to try out once they confirm.
                           - You only need to present the full template once per feature request.
                        6. **CONFIRMATION SYNTAX (MANDATORY)**: When seeking approval, you MUST use the exact syntax: "When you're ready, click the button below to proceed! [ACTIONS: Yes, let's build it!]".
                           - **NEVER** say "just say Yes, let's build it! ✅". This is DEPRECATED.
                           - If you are updating an existing app, use the word "update" or "modify" instead of "build".

                        ### PHASE 2: Execution (ACT)
                        1. ONLY after the user explicitly says 'yes', 'build it', 'proceed', or clearly approves, proceed.
                        2. Internally map the plain-English spec back to proper technical entities and fields.
                        3. Call `scaffold_app` ONCE with the full JSON structure (it will safely merge into the current app if one is selected).

                        **DO NOT** use `create_app`, `create_entity`, `generate_page` individually unless the user explicitly asks to create a single specific item bypassing the bulk scaffold process.

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

                        ## UX DESIGN & WORKFLOW PATTERNS (Generic)
                        Follow these patterns to ensure applications are interconnected and professional:
                        1. **The 'Success Loop' Pattern**:
                           - After a user creates a new record (e.g. Registration, New Lead), ALWAYS redirect them to a confirmation or list page.
                           - Use `"onSuccess": "navigate"` and `"navigateUrl": "/target-path"` in the button props.
                        2. **The 'Linear Selection' Pattern (Add to Cart)**:
                           - On 'List' pages for entities that can be 'selected' or 'added', include a button in the row actions.
                           - Use `"actionType": "save-entity"` with `"fixedFields"` to map current row data to a new record.
                           - Example for Product -> Cart mapping: `"fixedFields": { "Cart": { "productId": "{{id}}", "qty": 1 } }`.
                        3. **The 'Global Navigation' Pattern**:
                           - Always suggest a navigation menu or home links that ensure the user never gets stuck on a page.

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

                        ## POST-BUILD BEHAVIOR (CRITICAL)
                        After an app has been successfully built (tool results show scaffold_app succeeded):
                        1. **DO NOT build another app** if the user says "make the app", "create it", "build it now" — the app is ALREADY BUILT. Tell them so.
                        2. **If the user asks "how to open the app"**: Tell them to click the 📂 Open App button in the top toolbar, select their app, and the live preview will appear on the right. They can also click the ↗ Open button in the Live App panel to open it full-screen.
                        3. **If the user asks "what is my app URL"**: The URL format is `/run/{tenantId}/{appId}` where both values appear in the context or success message.
                        4. **NEVER reset the conversation** with "Hello! How can I assist you?" — always maintain context from the current session.

                        ## SUCCESS MESSAGE PHRASING (CRITICAL)
                        When providing your `final_answer` after successful tool execution:
                        1. **MODIFICATION / UPDATE**: If an `appId` was already present in the "CURRENT EXECUTION CONTEXT" below, you are MODifying an existing app. You MUST use words like "updated", "enhanced", or "modified". NEVER say "built and deployed" for an existing app.
                        2. **NEW BUILD**: If the `appId` was "(none selected)" or you just created a brand new app, you MUST use words like "built and deployed" or "created".
                        """);
        return prompt.toString();
    }

    /**
     * Emit a compact block describing the current agent execution context so the LLM knows
     * which app / tenant / user it's operating on. The system prompt references
     * "CURRENT EXECUTION CONTEXT below" — this method is what produces it.
     */
    private String buildExecutionContext(AgentContext context) {
        String appId = context.appId();
        boolean appSelected = appId != null && !appId.isBlank() && !"default".equals(appId);
        String appName = context.hasVariable("app_name") ? String.valueOf(context.getVariable("app_name")) : "";
        String tenantId = context.tenantId() != null ? context.tenantId() : "default";
        String userId = context.userId() != null ? context.userId() : "anonymous";

        StringBuilder sb = new StringBuilder();
        if (appSelected) {
            sb.append("- Selected app ID: ").append(appId).append('\n');
            if (!appName.isBlank()) {
                sb.append("- Selected app name: \"").append(appName).append("\"\n");
            }
            sb.append("- An app IS currently selected. When the user asks \"which app do I have selected?\" or ")
              .append("similar, answer directly using the name above — DO NOT tell them to select an app first.\n");
        } else {
            sb.append("- Selected app ID: (none selected)\n");
            sb.append("- No app is currently selected. If the user's request needs an app, ask which one.\n");
        }
        sb.append("- Tenant: ").append(tenantId).append('\n');
        sb.append("- User: ").append(userId);

        // Inject a compact entity+field summary for the selected app so the LLM can
        // answer factual questions ("how many chars can I enter in Department?")
        // without a round-trip that might mis-identify a field name as an entity.
        // Best-effort: silently skipped if the backend URL isn't wired or the fetch
        // fails.
        if (appSelected && backendBaseUrl != null) {
            String summary = loadEntitySummary(context);
            if (summary != null && !summary.isBlank()) {
                sb.append("\n\n### ENTITIES IN THIS APP ###\n");
                sb.append(summary);
                sb.append("\n(Names of fields listed above are FIELDS on their parent entity, not standalone entities. ")
                  .append("Do NOT call get_entity_details on a field name — call it on the parent entity.)");
            }
        }
        return sb.toString();
    }

    /**
     * Lazily fetch a compact entity summary for the currently-selected app and cache
     * it on the context. Runs once per conversation (cached under variable key
     * "entity_summary") so per-iteration prompt building is cheap.
     */
    @SuppressWarnings("unchecked")
    private String loadEntitySummary(AgentContext context) {
        if (context.hasVariable("entity_summary")) {
            Object cached = context.getVariable("entity_summary");
            return cached instanceof String s ? s : null;
        }
        try {
            String tenantId = context.tenantId() != null ? context.tenantId() : "default";
            String url = String.format("%s/appbana-studio/%s/apps/%s", backendBaseUrl, tenantId, context.appId());
            HttpRequest.Builder rb = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(5));
            if (context.token() != null && !context.token().isEmpty()) {
                rb.header("Authorization", "Bearer " + context.token());
            }
            HttpResponse<String> resp = httpClient.send(rb.GET().build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.debug("[AGENT] Entity summary fetch returned {}: skipping", resp.statusCode());
                context.variables().put("entity_summary", "");
                return "";
            }
            Map<String, Object> app = objectMapper.readValue(resp.body(), Map.class);
            String summary = buildEntitySummaryText(app);
            context.variables().put("entity_summary", summary);
            return summary;
        } catch (Exception e) {
            log.debug("[AGENT] Entity summary fetch failed (best-effort): {}", e.getMessage());
            context.variables().put("entity_summary", "");
            return "";
        }
    }

    /** Turn the raw app metadata into a short, LLM-friendly entity+field listing. */
    @SuppressWarnings("unchecked")
    private String buildEntitySummaryText(Map<String, Object> app) {
        Object entitiesObj = app.get("entities");
        if (!(entitiesObj instanceof List<?> entitiesList) || entitiesList.isEmpty()) {
            // Fall back to bare schema names if the app hasn't been hydrated with full entities.
            Object schemas = app.get("schemas");
            if (schemas instanceof List<?> sl && !sl.isEmpty()) {
                return sl.stream().map(String::valueOf).map(n -> "- " + n).collect(Collectors.joining("\n"));
            }
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (Object eo : entitiesList) {
            if (!(eo instanceof Map<?, ?> em)) continue;
            Map<String, Object> entity = (Map<String, Object>) em;
            String entityName = String.valueOf(entity.getOrDefault("name", "(unnamed)"));
            Object fieldsObj = entity.get("fields");
            out.append("- ").append(entityName);
            if (fieldsObj instanceof List<?> fields && !fields.isEmpty()) {
                out.append(" (fields: ");
                out.append(fields.stream().limit(30).map(f -> {
                    if (!(f instanceof Map<?, ?> fm)) return "";
                    Map<String, Object> fmap = (Map<String, Object>) fm;
                    Object fn = fmap.get("name");
                    Object ft = fmap.get("type");
                    Object flen = fmap.get("length");
                    String base = String.valueOf(fn) + (ft != null ? ":" + ft : "");
                    if (flen != null && ("text".equals(String.valueOf(ft)) || "string".equals(String.valueOf(ft)))) {
                        base += "(" + flen + ")";
                    }
                    return base;
                }).filter(s -> !s.isEmpty()).collect(Collectors.joining(", ")));
                out.append(")");
            }
            out.append('\n');
        }
        return out.toString();
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
    private AgentThought parseAgentThought(String llmResponse) {
        if (llmResponse == null || llmResponse.isBlank()) {
            return AgentThought.finalAnswer("I received an empty response from the LLM.", "Sorry, I encountered an empty response.");
        }

        try {
            // Try to extract JSON from response (LLM might wrap it in markdown)
            String json = extractJson(llmResponse);

            // Parse JSON
            Map<String, Object> response = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });

            String thinking = (String) response.get("thinking");
            if (thinking == null) thinking = (String) response.get("action"); // Hallucination support
            
            String finalAnswer = (String) response.get("final_answer");

            // Robustness: Check for alternative keys if final_answer is missing
            if (finalAnswer == null) finalAnswer = (String) response.get("response"); // common hallucination
            if (finalAnswer == null) finalAnswer = (String) response.get("nextStep"); // common hallucination
            if (finalAnswer == null) finalAnswer = (String) response.get("message");
            if (finalAnswer == null) finalAnswer = (String) response.get("answer");
            if (finalAnswer == null) finalAnswer = (String) response.get("text");

            // Parse tool calls first (Prioritize Action over Talk)
            // Handle both array format [{name, arguments}] and object format {toolName: {args}}
            List<Map<String, Object>> toolCallsRaw = null;
            Object rawToolCalls = response.get("tool_calls");
            if (rawToolCalls instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> asList = (List<Map<String, Object>>) rawToolCalls;
                toolCallsRaw = asList;
            } else if (rawToolCalls instanceof Map) {
                // LLM used object format {"toolName": {arguments}} — convert to array format
                log.warn("[AGENT] LLM returned tool_calls as object instead of array — converting automatically");
                @SuppressWarnings("unchecked")
                Map<String, Object> asMap = (Map<String, Object>) rawToolCalls;
                toolCallsRaw = new ArrayList<>();
                for (Map.Entry<String, Object> entry : asMap.entrySet()) {
                    Map<String, Object> call = new java.util.LinkedHashMap<>();
                    call.put("name", entry.getKey());
                    call.put("arguments", entry.getValue());
                    toolCallsRaw.add(call);
                }
            }

            if (toolCallsRaw != null && !toolCallsRaw.isEmpty()) {
                List<ToolCall> toolCalls = new ArrayList<>();
                for (Map<String, Object> callRaw : toolCallsRaw) {
                    String name = (String) callRaw.get("name");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> arguments = (Map<String, Object>) callRaw.get("arguments");

                    toolCalls.add(new ToolCall(name, arguments));
                }

                if (finalAnswer != null) {
                    log.warn("[AGENT] LLM provided both tool_calls and final_answer. Ignoring final_answer to execute tools.");
                }

                return AgentThought.toolCalls(thinking != null ? thinking : "Executing tools...", toolCalls);
            }

            // Check for final answer
            if (finalAnswer != null && !finalAnswer.isEmpty()) {
                return AgentThought.finalAnswer(thinking != null ? thinking : "Providing final answer...", finalAnswer);
            }

            // Fallback 1: If we have thinking but no explicit final_answer, use thinking as the answer.
            if (thinking != null && !thinking.isEmpty()) {
                log.warn("[AGENT] No final_answer in JSON. Falling back to 'thinking' text.");
                return AgentThought.finalAnswer(thinking, thinking);
            }

        } catch (Exception e) {
            log.warn("[AGENT] Failed to parse response as JSON: {}. Falling back to raw text extraction.", e.getMessage());
        }

        // Fallback 2: Last resort - extract any non-JSON text or use the raw response
        // This handles cases where the LLM talks before/after the JSON or forgets JSON entirely
        String fallbackAnswer = llmResponse.replaceAll("```json[\\s\\S]*?```", "").replaceAll("```[\\s\\S]*?```", "").trim();
        if (fallbackAnswer.isEmpty()) {
            fallbackAnswer = llmResponse.trim();
        }

        log.info("[AGENT] Using raw response as final_answer (Length: {})", fallbackAnswer.length());
        return AgentThought.finalAnswer("Extracted from raw response", fallbackAnswer);
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
