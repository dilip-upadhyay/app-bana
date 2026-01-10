package com.appbana.ai.api;

import com.appbana.ai.api.dto.*;
import com.appbana.ai.agent.AiAgent;
import com.appbana.ai.agent.AgentContext;
import com.appbana.ai.agent.AgentResponse;
import com.appbana.ai.dialogue.DialogueManager;
import com.appbana.ai.llm.IntentClassifier;
import com.appbana.ai.llm.OpenAiLlmService;
import com.appbana.ai.llm.AdvancedPromptEngine;
import com.appbana.ai.rag.ConversationMemory;
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
    private final IntentClassifier intentClassifier;
    private final AdvancedPromptEngine promptEngine;
    private final ConversationMemory conversationMemory;
    private final AiAgent agent; // Story 8.5

    public AiChatController(
            OpenAiLlmService llmService,
            IntentClassifier intentClassifier,
            AdvancedPromptEngine promptEngine,
            ConversationMemory conversationMemory,
            AiAgent agent) {
        this.llmService = llmService;
        this.intentClassifier = intentClassifier;
        this.promptEngine = promptEngine;
        this.conversationMemory = conversationMemory;
        this.agent = agent;
    }

    public BiConsumer<Router.HttpRequest, Router.HttpResponse> chat() {
        return (req, res) -> {
            try {
                ChatRequest request = req.readJson(new TypeReference<ChatRequest>() {
                });

                log.info("Chat request from user: {}", request.getUserId());

                // Classify intent
                IntentClassifier.IntentResult intent = intentClassifier.classifyIntent(request.getMessage());

                // Build context-aware prompt
                String prompt = promptEngine.buildPrompt(
                        request.getMessage(),
                        request.getUserId(),
                        null);

                // Get LLM response
                String aiResponse = llmService.chat(prompt);

                // Store conversation (optional - skip if memory is disabled)
                String conversationId = null;
                if (conversationMemory != null) {
                    ConversationMemory.Conversation conversation = new ConversationMemory.Conversation();
                    conversation.setUserId(request.getUserId());
                    conversation.setSessionId(UUID.fromString(request.getSessionId()));
                    conversation.setMessage(request.getMessage());
                    conversation.setResponse(aiResponse);
                    conversation.setIntent(intent.getIntent());

                    ConversationMemory.Conversation stored = conversationMemory.store(conversation);
                    conversationId = stored.getId();
                } else {
                    log.debug("Conversation memory disabled - skipping storage");
                    conversationId = UUID.randomUUID().toString();
                }

                // Build response
                ChatResponse response = new ChatResponse();
                response.setMessage(aiResponse);
                response.setIntent(intent.getIntent());
                response.setSuggestions(new ArrayList<>());
                response.setConversationId(conversationId);

                res.json(200, response);

            } catch (Exception e) {
                log.error("Error processing chat", e);
                res.json(500, Map.of("error", "Chat processing failed"));
            }
        };
    }

    /**
     * Agent-based chat endpoint
     * Story 8.5: Controller Integration
     */
    public BiConsumer<Router.HttpRequest, Router.HttpResponse> chatAgent() {
        return (req, res) -> {
            try {
                ChatRequest chatRequest = req.readJson(new TypeReference<ChatRequest>() {
                });
                log.info("[AGENT-ENDPOINT] Chat request from user: {}", chatRequest.getUserId());

                // Extract context details from the request
                String tenantId = chatRequest.getTenantId() != null ? chatRequest.getTenantId() : "default";
                String appId = chatRequest.getAppId() != null ? chatRequest.getAppId() : "default";
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

                // 2. Prepare Agent Context
                // Pass token for authenticated tool calls and history for context
                AgentContext agentContext = AgentContext.create(
                        tenantId,
                        appId,
                        userId,
                        sessionId,
                        token).withVariable("chat_history", history);

                // 3. Execute Agent
                // The agent will decide which tools to call based on the user's message
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
                        conv.setIntent("agent_action");

                        try {
                            conversationMemory.store(conv);
                        } catch (Exception e) {
                            log.warn("Failed to store conversation for user {} session {}: {}", userId, sessionId,
                                    e.getMessage());
                        }
                    }

                    // Return success response
                    res.json(200, Map.of(
                            "status", "success",
                            "response", result.getFinalAnswer(),
                            "steps", result.getSteps()));
                } else {
                    // Return error response
                    res.json(500, Map.of(
                            "status", "error",
                            "message", result.getError() != null ? result.getError() : "Unknown error occurred"));
                }
            } catch (Exception e) {
                log.error("[AGENT-ENDPOINT] Error processing agent chat", e);
                res.json(500, Map.of("error", "Agent processing failed: " + e.getMessage()));
            }
        };
    }
}
