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
import com.appbana.ai.api.Router;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * AI Chat controller using plain Java Router pattern
 * Story: 5.1 - Chat UI Component (Backend API)
 * Updated: Story 8.5 - Controller Integration (Agent support)
 */
@Slf4j
public class AiChatController {

    private final OpenAiLlmService llmService;
    // IntentClassifier removed
    private final AdvancedPromptEngine promptEngine;
    private final ConversationMemory conversationMemory;
    private final AiAgent agent;
    private final UserPreferenceEngine userPreferenceEngine;

    public AiChatController(
            OpenAiLlmService llmService,
            AdvancedPromptEngine promptEngine,
            ConversationMemory conversationMemory,
            AiAgent agent,
            UserPreferenceEngine userPreferenceEngine) {
        this.llmService = llmService;
        this.promptEngine = promptEngine;
        this.conversationMemory = conversationMemory;
        this.agent = agent;
        this.userPreferenceEngine = userPreferenceEngine;
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

        // 2. Prepare Agent Context
        // Pass token for authenticated tool calls and history for context
        AgentContext agentContext = AgentContext.create(
                tenantId,
                appId,
                userId,
                sessionId,
                token)
                .withVariable("chat_history", history)
                .withVariable("user_preferences", userPreferences);

        // 3. Execute Agent
        // The agent will decide which tools to call based on the user's message
        // Or simply reply if no tool is needed (Zero-Intent Flow)
        AgentResponse result = agent.process(chatRequest.getMessage(), agentContext);

        // 4. Handle Result
        if (result.isSuccess()) {
            // Store conversation in memory
            if (conversationMemory != null) {
                ConversationMemory.Conversation conv = new ConversationMemory.Conversation();
                conv.setUserId(userId);
                conv.setSessionId(UUID.fromString(sessionId));
                conv.setMessage(chatRequest.getMessage());
                conv.setResponse(result.getFinalAnswer());
                conv.setIntent("agent_conversation"); // Generic intent for stored history

                try {
                    conversationMemory.store(conv);
                } catch (Exception e) {
                    log.warn("Failed to store conversation for user {} session {}: {}", userId, sessionId,
                            e.getMessage());
                }
            }

            // Return success response
            // Map legacy 'message' field for older clients if needed, primarily use
            // 'response'
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("status", "success");
            responseMap.put("response", result.getFinalAnswer());
            responseMap.put("message", result.getFinalAnswer()); // Legacy support
            responseMap.put("steps", result.getSteps());

            // Populate intent if available from agent thought (optional in future)
            responseMap.put("intent", "agent_processed");

            res.json(200, responseMap);
        } else {
            // Return error response
            res.json(500, Map.of(
                    "status", "error",
                    "message", result.getError() != null ? result.getError() : "Unknown error occurred"));
        }
    }
}
