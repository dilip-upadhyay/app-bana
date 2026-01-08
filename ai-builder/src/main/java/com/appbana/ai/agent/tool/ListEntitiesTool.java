package com.appbana.ai.agent.tool;

import com.appbana.ai.agent.AgentContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool for listing entities in AppBana
 * Story 8.3: Essential Tools Implementation
 */
@Slf4j
public class ListEntitiesTool implements Tool {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public ListEntitiesTool(String baseUrl) {
        this.httpClient = HttpClient.newHttpClient();
        this.baseUrl = baseUrl;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getName() {
        return "list_entities";
    }

    @Override
    public String getDescription() {
        return "List all entities (database tables) in the current application. " +
                "Use this to see what entities already exist before creating new ones.";
    }

    @Override
    public String getParameterSchema() {
        return """
                {
                  "type": "object",
                  "properties": {},
                  "description": "No parameters required"
                }
                """;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments, AgentContext context) {
        long startTime = System.currentTimeMillis();

        try {
            log.info("[ListEntitiesTool] Listing entities");

            // Call backend API
            String url = baseUrl + "/schema";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            long executionTime = System.currentTimeMillis() - startTime;

            // Handle response
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                List<String> entityNames = objectMapper.readValue(
                        response.body(),
                        new TypeReference<List<String>>() {
                        });

                log.info("[ListEntitiesTool] Found {} entities", entityNames.size());

                Map<String, Object> result = new HashMap<>();
                result.put("entities", entityNames);
                result.put("count", entityNames.size());

                return ToolResult.success(getName(), result, executionTime);
            } else {
                log.error("[ListEntitiesTool] API error: {} - {}",
                        response.statusCode(), response.body());
                return ToolResult.error(getName(),
                        "API error: " + response.statusCode());
            }

        } catch (Exception e) {
            log.error("[ListEntitiesTool] Execution failed", e);
            return ToolResult.error(getName(), "Execution error: " + e.getMessage());
        }
    }
}
