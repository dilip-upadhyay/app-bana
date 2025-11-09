package com.appbana.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Ollama provider for local AI models (Llama 3, Mistral, etc.)
 */
public class OllamaProvider implements AiProvider {
    private static final Logger LOG = LoggerFactory.getLogger(OllamaProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    private final String baseUrl;
    private final String model;
    private final OkHttpClient client;
    
    public OllamaProvider(String baseUrl, String model) {
        this.baseUrl = baseUrl != null && !baseUrl.isBlank() ? baseUrl : "http://localhost:11434";
        this.model = model != null && !model.isBlank() ? model : "llama3.1";
        this.client = new OkHttpClient();
    }
    
    @Override
    public String generateAppStructure(String userPrompt, String systemPrompt) throws Exception {
        LOG.info("Calling Ollama API at {} with model: {}", baseUrl, model);
        
        String fullPrompt = systemPrompt + "\n\nUser request: " + userPrompt + "\n\nGenerate ONLY valid JSON, no markdown:";
        
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "prompt", fullPrompt,
                "stream", false,
                "format", "json"
        );
        
        String jsonBody = MAPPER.writeValueAsString(requestBody);
        
        Request request = new Request.Builder()
                .url(baseUrl + "/api/generate")
                .header("content-type", "application/json")
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("Ollama API error: " + response.code() + " " + response.message());
            }
            
            String responseBody = response.body().string();
            Map<String, Object> responseMap = MAPPER.readValue(responseBody, Map.class);
            
            String generatedText = (String) responseMap.get("response");
            
            LOG.info("Ollama response received");
            return generatedText;
        }
    }
    
    @Override
    public boolean testConnection() {
        try {
            Request request = new Request.Builder()
                    .url(baseUrl + "/api/tags")
                    .get()
                    .build();
            
            try (Response response = client.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            LOG.error("Ollama connection test failed", e);
            return false;
        }
    }
    
    @Override
    public String getProviderName() {
        return "ollama";
    }
}
