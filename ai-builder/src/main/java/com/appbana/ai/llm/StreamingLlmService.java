package com.appbana.ai.llm;

import com.appbana.ai.config.AiConfig;
import com.theokanning.openai.completion.chat.*;
import com.theokanning.openai.service.OpenAiService;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;

/**
 * Streaming LLM Service using Server-Sent Events (SSE)
 * Provides token-by-token streaming for better perceived performance
 */
@Slf4j
public class StreamingLlmService {

    private final AiConfig config;
    private final OpenAiService openAiService;

    public StreamingLlmService(AiConfig config) {
        this.config = config;
        this.openAiService = new OpenAiService(config.getOpenaiApiKey(), Duration.ofSeconds(60));
        log.info("Streaming LLM Service initialized with model: {}", config.getOpenaiModel());
    }

    /**
     * Stream chat completion token-by-token
     */
    public void chatStream(String prompt, StreamObserver<String> observer) {
        chatStream(prompt, 0.7, observer);
    }

    /**
     * Stream chat completion with custom temperature
     */
    public void chatStream(String prompt, double temperature, StreamObserver<String> observer) {
        try {
            log.debug("[StreamingLLM] Starting stream for prompt (length: {})", prompt.length());

            ChatMessage message = new ChatMessage(ChatMessageRole.USER.value(), prompt);

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(config.getOpenaiModel())
                    .messages(List.of(message))
                    .temperature(temperature)
                    .maxTokens(config.getOpenaiMaxTokens())
                    .stream(true) // Enable streaming
                    .build();

            // Stream tokens using the library's accumulator pattern
            openAiService.mapStreamToAccumulator(openAiService.streamChatCompletion(request))
                    .doOnError(error -> {
                        log.error("[StreamingLLM] Stream error", error);
                        observer.onError(error);
                    })
                    .doOnNext(accumulator -> {
                        // Get the incremental content from each chunk
                        if (accumulator.getMessageChunk() != null &&
                                accumulator.getMessageChunk().getContent() != null) {
                            String token = accumulator.getMessageChunk().getContent();
                            observer.onNext(token);
                        }
                    })
                    .doOnComplete(() -> {
                        observer.onComplete();
                        log.debug("[StreamingLLM] Stream completed successfully");
                    })
                    .blockingSubscribe();

        } catch (Exception e) {
            log.error("[StreamingLLM] Failed to stream chat completion", e);
            observer.onError(e);
        }
    }
}
