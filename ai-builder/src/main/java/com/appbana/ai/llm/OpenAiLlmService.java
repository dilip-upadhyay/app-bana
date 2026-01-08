package com.appbana.ai.llm;

import com.appbana.ai.config.AiConfig;
import com.theokanning.openai.completion.chat.*;
import com.theokanning.openai.service.OpenAiService;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.*;

/**
 * OpenAI GPT-4 integration service
 * Story: 4.1 - Implement GPT-4 Integration
 */
@Slf4j
public class OpenAiLlmService implements AutoCloseable {

    private final AiConfig config;
    private final OpenAiService openAiService;

    public OpenAiLlmService(AiConfig config) {
        this.config = config;
        this.openAiService = new OpenAiService(config.getOpenaiApiKey(), Duration.ofSeconds(60));
        log.info("OpenAI LLM Service initialized with model: {}", config.getOpenaiModel());
    }

    public String chat(String prompt) throws LlmException {
        return chat(prompt, null);
    }

    public String chat(String prompt, Map<String, Object> options) throws LlmException {
        try {
            log.debug("Sending chat request to OpenAI");

            ChatMessage message = new ChatMessage(ChatMessageRole.USER.value(), prompt);

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(config.getOpenaiModel())
                    .messages(List.of(message))
                    .temperature(0.7)
                    .maxTokens(2000)
                    .build();

            ChatCompletionResult result = openAiService.createChatCompletion(request);

            if (result.getChoices() == null || result.getChoices().isEmpty()) {
                throw new LlmException("No response from OpenAI");
            }

            String response = result.getChoices().get(0).getMessage().getContent();

            log.debug("Received response from OpenAI ({} tokens)",
                    result.getUsage().getTotalTokens());

            return response;

        } catch (Exception e) {
            log.error("Failed to get chat completion from OpenAI", e);
            throw new LlmException("Failed to get chat completion", e);
        }
    }

    @Override
    public void close() {
        log.info("Closing OpenAI LLM Service");
        // OpenAiService doesn't need explicit closing
    }

    public static class LlmException extends Exception {
        public LlmException(String message) {
            super(message);
        }

        public LlmException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
