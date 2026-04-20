package com.appbana.ai.llm;

import com.appbana.ai.config.AiConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Google Gemini LLM Service implementation.
 * Uses REST API directly for maximum control over multimodal and structured features.
 */
@Slf4j
public class GeminiLlmService implements LlmService {

    private final AiConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ModelRouter modelRouter;

    public GeminiLlmService(AiConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
        // Map premium/standard tasks to Gemini models
        this.modelRouter = new ModelRouter(config.getGeminiPremiumModel(), config.getGeminiModel());
        log.info("Gemini LLM Service initialized - model: {}, premium: {}", 
                config.getGeminiModel(), config.getGeminiPremiumModel());
    }

    @Override
    public String chat(String prompt) throws Exception {
        return chatWithOptions(prompt, null, null, null);
    }

    @Override
    public String chat(String prompt, List<String> images) throws Exception {
        return chatWithOptions(prompt, null, null, images);
    }

    @Override
    public String chat(String prompt, String taskType) throws Exception {
        return chatWithOptions(prompt, taskType, null, null);
    }

    @Override
    public String chat(String prompt, List<String> images, String taskType) throws Exception {
        return chatWithOptions(prompt, taskType, null, images);
    }

    @Override
    public String chatWithOptions(String prompt, String taskType, Map<String, Object> options, List<String> images) throws Exception {
        String model = modelRouter.selectModel(taskType);
        return executeGeminiRequest(prompt, model, false, images);
    }

    @Override
    public String chatWithJsonMode(String prompt) throws Exception {
        return chatWithJsonMode(prompt, null);
    }

    @Override
    public String chatWithJsonMode(String prompt, List<String> images) throws Exception {
        String model = modelRouter.selectModel("agent_think");
        return executeGeminiRequest(prompt, model, true, images);
    }

    @Override
    public String chatStructured(String prompt, String schemaName, String schema) throws Exception {
        String model = config.getGeminiPremiumModel();
        String promptWithSchema = prompt + "\n\nOutput must be valid JSON matching this schema:\n" + schema;
        return executeGeminiRequest(promptWithSchema, model, true, null);
    }

    private String executeGeminiRequest(String prompt, String model, boolean jsonMode, List<String> images) throws Exception {
        if (config.getGeminiApiKey() == null) {
            throw new IllegalStateException("GEMINI_API_KEY is not configured");
        }

        String url = String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s",
                model, config.getGeminiApiKey());

        // Construct Gemini Payload
        Map<String, Object> body = new LinkedHashMap<>();
        
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("text", prompt));

        if (images != null && !images.isEmpty()) {
            for (String base64Image : images) {
                String rawBase64 = base64Image;
                if (base64Image.contains(";base64,")) {
                    rawBase64 = base64Image.split(";base64,")[1];
                }
                
                parts.add(Map.of(
                    "inline_data", Map.of(
                        "mime_type", "image/png", // Assuming PNG, or detect from URI
                        "data", rawBase64
                    )
                ));
            }
        }

        Map<String, Object> content = Map.of("parts", parts);
        body.put("contents", List.of(content));

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", config.getOpenaiTemperature());
        generationConfig.put("maxOutputTokens", config.getOpenaiMaxTokens());
        
        if (jsonMode) {
            generationConfig.put("response_mime_type", "application/json");
        }
        
        body.put("generationConfig", generationConfig);

        String requestBody = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(60))
                .build();

        log.debug("[Gemini] Sending request to model: {}", model);
        
        int maxRetries = 3;
        long backoff = 2000;

        for (int i = 0; i <= maxRetries; i++) {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return parseGeminiResponse(response.body());
            }

            if (response.statusCode() == 429) {
                if (i == maxRetries) throw new Exception("Gemini rate limit exceeded after retries");
                log.warn("[Gemini] Rate limit (429). Retrying in {}ms...", backoff);
                Thread.sleep(backoff);
                backoff *= 2;
                continue;
            }

            throw new Exception("Gemini API error (" + response.statusCode() + "): " + response.body());
        }

        throw new Exception("Gemini API call failed");
    }

    @SuppressWarnings("unchecked")
    private String parseGeminiResponse(String responseBody) throws Exception {
        Map<String, Object> map = objectMapper.readValue(responseBody, Map.class);
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) map.get("candidates");
        
        if (candidates == null || candidates.isEmpty()) {
            throw new Exception("Gemini returned no candidates");
        }

        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
        
        if (parts == null || parts.isEmpty()) {
            throw new Exception("Gemini returned no parts in content");
        }

        return (String) parts.get(0).get("text");
    }
}
