package com.appbana.ai.api;

import com.appbana.ai.api.dto.*;
import com.appbana.ai.agent.AiAgent;
import com.appbana.ai.agent.AgentContext;
import com.appbana.ai.agent.AgentResponse;
import com.appbana.ai.dialogue.DialogueManager;
// IntentClassifier removed
import com.appbana.ai.llm.OpenAiLlmService;
import com.appbana.ai.llm.AdvancedPromptEngine;
import com.appbana.ai.rag.ConversationMemory;
import com.appbana.ai.learning.UserPreferenceEngine;
import com.appbana.ai.optimization.DirectAnswerService;
import com.appbana.ai.optimization.PatternExecutor;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * AI Chat controller using plain Java Router pattern
 * Story: 5.1 - Chat UI Component (Backend API)
 * Updated: Story 8.5 - Controller Integration (Agent support)
 */
@Slf4j
public class AiChatController {

    private final ConversationMemory conversationMemory;
    private final AiAgent agent;
    private final UserPreferenceEngine userPreferenceEngine;
    private final DirectAnswerService directAnswerService;
    private final PatternExecutor patternExecutor;
    private final DialogueManager dialogueManager;

    public AiChatController(
            OpenAiLlmService llmService,
            AdvancedPromptEngine promptEngine,
            ConversationMemory conversationMemory,
            AiAgent agent,
            UserPreferenceEngine userPreferenceEngine,
            DirectAnswerService directAnswerService,
            PatternExecutor patternExecutor,
            DialogueManager dialogueManager) {
        this.conversationMemory = conversationMemory;
        this.agent = agent;
        this.userPreferenceEngine = userPreferenceEngine;
        this.directAnswerService = directAnswerService;
        this.patternExecutor = patternExecutor;
        this.dialogueManager = dialogueManager;
    }

    /**
     * Unified Chat Endpoint
     * Handles both general conversation and tool execution via AiAgent
     */
    public BiConsumer<Router.HttpRequest, Router.HttpResponse> chat() {
        return (req, res) -> {
            try {
                ChatRequest request = req.readJson(new TypeReference<ChatRequest>() {
                });

                log.info("Chat request from user: {}", request.getUserId());

                // Reuse the unified agent logic
                // For the generic chat endpoint, we might not have app/tenant details, so use
                // defaults
                String tenantId = request.getTenantId() != null ? request.getTenantId() : "default";
                String appId = request.getAppId() != null ? request.getAppId() : "default";

                processAgentRequest(req, res, request, tenantId, appId);

            } catch (Exception e) {
                log.error("Error processing chat", e);
                res.json(500, Map.of("error", "Chat processing failed"));
            }
        };
    }

    /**
     * Agent-based chat endpoint
     * Preserved for backward compatibility, but logic is shared
     */
    public BiConsumer<Router.HttpRequest, Router.HttpResponse> chatAgent() {
        return (req, res) -> {
            try {
                ChatRequest chatRequest = req.readJson(new TypeReference<ChatRequest>() {
                });
                log.info("[AGENT-ENDPOINT] Chat request from user: {}", chatRequest.getUserId());

                String tenantId = chatRequest.getTenantId() != null ? chatRequest.getTenantId() : "default";
                String appId = chatRequest.getAppId() != null ? chatRequest.getAppId() : "default";

                processAgentRequest(req, res, chatRequest, tenantId, appId);

            } catch (Exception e) {
                log.error("[AGENT-ENDPOINT] Error processing agent chat", e);
                res.json(500, Map.of("error", "Agent processing failed: " + e.getMessage()));
            }
        };
    }

    private void processAgentRequest(Router.HttpRequest req, Router.HttpResponse res, ChatRequest chatRequest,
            String tenantId, String appId) throws Exception {
        String userId = chatRequest.getUserId();
        String sessionId = chatRequest.getSessionId();
        String token = chatRequest.getToken();

        // 1. Get Conversation History
        List<ConversationMemory.Conversation> history = new ArrayList<>();
        if (conversationMemory != null) {
            try {
                history = conversationMemory.getSessionHistory(UUID.fromString(sessionId));
            } catch (Exception e) {
                log.warn("Failed to retrieve conversation history for session {}: {}", sessionId,
                        e.getMessage());
            }
        }

        // 2a. Get User Preferences
        Map<String, String> userPreferences = new HashMap<>();
        if (userPreferenceEngine != null) {
            try {
                userPreferences = userPreferenceEngine.getPreferences(userId);
            } catch (Exception e) {
                log.warn("Failed to retrieve preferences for user {}: {}", userId, e.getMessage());
            }
        }

        // === DECISION TREE: RAG-First Cost Optimization ===

        // Stage 1: Try RAG-only answer (zero LLM cost)
        if (directAnswerService != null) {
            Optional<DirectAnswerService.DirectAnswer> directAnswer = directAnswerService
                    .tryDirectAnswer(chatRequest.getMessage());

            if (directAnswer.isPresent()) {
                log.info("[RAG-FIRST] Direct answer provided (0 LLM cost)");

                // Save to conversation history using the store() method
                if (conversationMemory != null) {
                    try {
                        ConversationMemory.Conversation conv = new ConversationMemory.Conversation();
                        conv.setSessionId(UUID.fromString(sessionId));
                        conv.setUserId(userId);
                        conv.setMessage(chatRequest.getMessage());
                        conv.setResponse(directAnswer.get().getAnswer());
                        conv.setIntent("rag_direct");
                        conversationMemory.store(conv);
                    } catch (Exception e) {
                        log.warn("Failed to save direct answer to history: {}", e.getMessage());
                    }
                }

                res.json(200, Map.of(
                        "response", directAnswer.get().getAnswer(),
                        "source", "rag_direct",
                        "schemasUsed", directAnswer.get().getSchemasUsed(),
                        "llmCost", 0.0));
                return;
            }
        }

        // Stage 2: Try pattern-based execution (minimal LLM cost)
        if (patternExecutor != null) {
            Optional<PatternExecutor.PatternExecutionResult> patternResult = patternExecutor
                    .tryPatternExecution(chatRequest.getMessage(), userId);

            if (patternResult.isPresent()) {
                log.info("[RAG-FIRST] Pattern execution successful (minimal LLM cost)");

                res.json(200, Map.of(
                        "response", "Successfully created " + patternResult.get().getAppName() +
                                " using learned pattern: " + patternResult.get().getPatternType(),
                        "source", "pattern",
                        "patternType", patternResult.get().getPatternType(),
                        "metadata", patternResult.get().getMetadata()));
                return;
            }
        }

        // === Stage 3: Full agent loop (standard LLM cost) ===
        log.info("[RAG-FIRST] Falling back to full agent loop");

        // 2a. Resolve conversation state via DialogueManager
        //     This determines which phase the user is in and filters the tool set.
        DialogueManager.ConversationState conversationState =
                dialogueManager.resolveState(sessionId, history, chatRequest.getMessage());
        log.info("[DIALOGUE] session={} resolved state={}", sessionId, conversationState);

        // 2b. Prepare Agent Context (with conversation state injected)
        AgentContext agentContext = AgentContext.create(
                tenantId,
                appId,
                userId,
                sessionId,
                token)
                .withVariable("chat_history", history)
                .withVariable("user_preferences", userPreferences)
                .withVariable("conversation_state", conversationState.name());

        // 3. Execute Agent
        AgentResponse result = agent.process(chatRequest.getMessage(), agentContext);

        // 4. Handle Result
        if (result.isSuccess()) {
            // Advance dialogue state if the agent built the app
            String finalAnswer = result.getFinalAnswer() != null ? result.getFinalAnswer().toLowerCase() : "";
            if (finalAnswer.contains("scaffold") || finalAnswer.contains("app created")
                    || finalAnswer.contains("successfully created") || finalAnswer.contains("application has been created")) {
                dialogueManager.notifyScaffolding(sessionId);
            } else if (finalAnswer.contains("deployed") || finalAnswer.contains("app is ready")) {
                dialogueManager.notifyCompleted(sessionId);
            }

            // Refresh state after possible notify
            DialogueManager.ConversationState updatedState = dialogueManager.getCurrentState(sessionId);
            // Store conversation in memory
            if (conversationMemory != null) {
                ConversationMemory.Conversation conv = new ConversationMemory.Conversation();
                conv.setUserId(userId);
                conv.setSessionId(UUID.fromString(sessionId));
                conv.setMessage(chatRequest.getMessage());
                conv.setResponse(result.getFinalAnswer());
                conv.setIntent("agent_conversation");

                try {
                    conversationMemory.store(conv);
                } catch (Exception e) {
                    log.warn("Failed to store conversation for user {} session {}: {}", userId, sessionId,
                            e.getMessage());
                }
            }

            // Return success response
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("status", "success");
            responseMap.put("response", result.getFinalAnswer());
            responseMap.put("message", result.getFinalAnswer()); // Legacy support
            responseMap.put("steps", result.getSteps());
            responseMap.put("intent", "agent_processed");
            responseMap.put("conversationState", updatedState.name()); // Story 3.1

            res.json(200, responseMap);
        } else {
            // Return error response
            res.json(500, Map.of(
                    "status", "error",
                    "message", result.getError() != null ? result.getError() : "Unknown error occurred"));
        }
    }
}
