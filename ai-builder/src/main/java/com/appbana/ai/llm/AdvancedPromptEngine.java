package com.appbana.ai.llm;

import com.appbana.ai.config.AiConfig;
import com.appbana.ai.rag.ConversationMemory;
import lombok.extern.slf4j.Slf4j;
import java.util.*;

/**
 * Advanced prompt engine with RAG enhancement
 * Story: 3.3 - Implement Advanced Prompt Engine
 */
@Slf4j
public class AdvancedPromptEngine {

    private final AiConfig config;
    private final ConversationMemory conversationMemory;

    public AdvancedPromptEngine(AiConfig config, ConversationMemory conversationMemory) {
        this.config = config;
        this.conversationMemory = conversationMemory;
        log.info("Advanced Prompt Engine initialized");
    }

    public String buildPrompt(String userMessage, String userId, Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder();

        // System prompt
        prompt.append("You are an intelligent AI assistant that helps users build applications.\n\n");

        // Add context from past conversations
        try {
            List<ConversationMemory.Conversation> recentConvs = conversationMemory.getRecentByUser(userId, 5);
            if (!recentConvs.isEmpty()) {
                prompt.append("Recent conversation history:\n");
                for (ConversationMemory.Conversation conv : recentConvs) {
                    prompt.append("User: ").append(conv.getMessage()).append("\n");
                    prompt.append("Assistant: ").append(conv.getResponse()).append("\n");
                }
                prompt.append("\n");
            }
        } catch (Exception e) {
            log.warn("Failed to fetch conversation history", e);
        }

        // Add current context
        if (context != null && !context.isEmpty()) {
            prompt.append("Current context:\n");
            context.forEach((k, v) -> prompt.append(k).append(": ").append(v).append("\n"));
            prompt.append("\n");
        }

        // Add user message
        prompt.append("User: ").append(userMessage).append("\n");
        prompt.append("Assistant:");

        return prompt.toString();
    }
}
