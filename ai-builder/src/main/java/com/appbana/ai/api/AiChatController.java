package com.appbana.ai.api;

import com.appbana.ai.api.dto.*;
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

/**
 * AI Chat controller using plain Java Router pattern
 * Story: 5.1 - Chat UI Component (Backend API)
 */
@Slf4j
public class AiChatController {

    private final OpenAiLlmService llmService;
    private final IntentClassifier intentClassifier;
    private final AdvancedPromptEngine promptEngine;
    private final ConversationMemory conversationMemory;

    public AiChatController(
            OpenAiLlmService llmService,
            IntentClassifier intentClassifier,
            AdvancedPromptEngine promptEngine,
            ConversationMemory conversationMemory) {
        this.llmService = llmService;
        this.intentClassifier = intentClassifier;
        this.promptEngine = promptEngine;
        this.conversationMemory = conversationMemory;
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

                // Store conversation
                ConversationMemory.Conversation conversation = new ConversationMemory.Conversation();
                conversation.setUserId(request.getUserId());
                conversation.setSessionId(UUID.fromString(request.getSessionId()));
                conversation.setMessage(request.getMessage());
                conversation.setResponse(aiResponse);
                conversation.setIntent(intent.getIntent());

                ConversationMemory.Conversation stored = conversationMemory.store(conversation);

                // Build response
                ChatResponse response = new ChatResponse();
                response.setMessage(aiResponse);
                response.setIntent(intent.getIntent());
                response.setSuggestions(new ArrayList<>());
                response.setConversationId(stored.getId());

                res.json(200, response);

            } catch (Exception e) {
                log.error("Error processing chat", e);
                res.json(500, Map.of("error", "Chat processing failed"));
            }
        };
    }
}
