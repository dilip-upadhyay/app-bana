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
                ChatRequest request = req.readJson(new TypeReference<ChatRequest>() {
                });
                log.info("[AGENT-ENDPOINT] Chat request from user: {}", request.getUserId());

                // Build agent context
                AgentContext context = AgentContext.create(
                        request.getTenantId() != null ? request.getTenantId() : "default",
                        request.getAppId() != null ? request.getAppId() : "default",
                        request.getUserId(),
                        request.getSessionId());

                // Execute agent
                AgentResponse agentResponse = agent.process(request.getMessage(), context);

                // Build chat response
                ChatResponse response = new ChatResponse();

                if (agentResponse.isSuccess()) {
                    response.setMessage(agentResponse.getFinalAnswer());
                    response.setIntent("agent_action");
                } else {
                    response.setMessage("I encountered an error: " + agentResponse.getError());
                    response.setIntent("agent_error");
                }

                response.setSuggestions(new ArrayList<>());
                response.setConversationId(UUID.randomUUID().toString());
                res.json(200, response);

            } catch (Exception e) {
                log.error("[AGENT-ENDPOINT] Error processing agent chat", e);
                res.json(500, Map.of("error", "Agent processing failed: " + e.getMessage()));
            }
        };
    }
}
