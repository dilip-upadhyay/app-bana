package com.appbana.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Anthropic Claude provider implementation
 */
public class AnthropicProvider implements AiProvider {
    private static final Logger LOG = LoggerFactory.getLogger(AnthropicProvider.class);
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    private final String apiKey;
    private final String model;
    private final OkHttpClient client;
    
    public AnthropicProvider(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model != null && !model.isBlank() ? model : "claude-3-5-sonnet-20241022";
        this.client = new OkHttpClient();
    }
    
    @Override
    public String generateAppStructure(String userPrompt, String systemPrompt) throws Exception {
        LOG.info("Calling Anthropic API with model: {}", model);
        
        // Build request body
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", 2000,
                "system", systemPrompt,
                "messages", List.of(
                        Map.of("role", "user", "content", userPrompt)
                )
        );
        
        String jsonBody = MAPPER.writeValueAsString(requestBody);
        
        Request request = new Request.Builder()
                .url(API_URL)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("Anthropic API error: " + response.code() + " " + response.message());
            }
            
            String responseBody = response.body().string();
            Map<String, Object> responseMap = MAPPER.readValue(responseBody, Map.class);
            
            // Extract content from response
            List<Map<String, Object>> content = (List<Map<String, Object>>) responseMap.get("content");
            String text = (String) content.get(0).get("text");
            
            LOG.info("Anthropic response received");
            return text;
        }
    }
    
    @Override
    public boolean testConnection() {
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "max_tokens", 5,
                    "messages", List.of(
                            Map.of("role", "user", "content", "Say 'OK'")
                    )
            );
            
            String jsonBody = MAPPER.writeValueAsString(requestBody);
            
            Request request = new Request.Builder()
                    .url(API_URL)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("content-type", "application/json")
                    .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                    .build();
            
            try (Response response = client.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            LOG.error("Anthropic connection test failed", e);
            return false;
        }
    }
    
    @Override
    public String getProviderName() {
        return "anthropic";
    }
}
