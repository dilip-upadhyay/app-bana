package com.appbana.ai;

import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;

/**
 * OpenAI provider implementation (GPT-4, GPT-3.5-turbo, etc.)
 */
public class OpenAiProvider implements AiProvider {
    private static final Logger LOG = LoggerFactory.getLogger(OpenAiProvider.class);
    
    private final String apiKey;
    private final String model;
    private final OpenAiService service;
    
    public OpenAiProvider(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model != null && !model.isBlank() ? model : "gpt-4o-mini";
        this.service = new OpenAiService(apiKey, Duration.ofSeconds(60));
    }
    
    @Override
    public String generateAppStructure(String userPrompt, String systemPrompt) throws Exception {
        LOG.info("Calling OpenAI API with model: {}", model);
        
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(model)
                .messages(Arrays.asList(
                        new ChatMessage(ChatMessageRole.SYSTEM.value(), systemPrompt),
                        new ChatMessage(ChatMessageRole.USER.value(), userPrompt)
                ))
                .temperature(0.7)
                .maxTokens(2000)
                .build();
        
        var response = service.createChatCompletion(request);
        String content = response.getChoices().get(0).getMessage().getContent();
        
        LOG.info("OpenAI response received ({} tokens)", response.getUsage().getTotalTokens());
        return content;
    }
    
    @Override
    public boolean testConnection() {
        try {
            // Simple test request
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(model)
                    .messages(Arrays.asList(
                            new ChatMessage(ChatMessageRole.USER.value(), "Say 'OK'")
                    ))
                    .maxTokens(5)
                    .build();
            
            service.createChatCompletion(request);
            return true;
        } catch (Exception e) {
            LOG.error("OpenAI connection test failed", e);
            return false;
        }
    }
    
    @Override
    public String getProviderName() {
        return "openai";
    }
}
