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
            String appId = context.appId();
            String tenantId = context.tenantId();

            if (appId != null && !appId.equals("default") && !appId.isEmpty()) {
                // CASE 1: App Context is selected - Fetch entities for this app
                log.info("[ListEntitiesTool] Listing entities for app: {}", appId);

                String url = baseUrl + "/appbana-studio/" + tenantId + "/apps/" + appId;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Accept", "application/json")
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                long executionTime = System.currentTimeMillis() - startTime;

                if (response.statusCode() == 200) {
                    Map<String, Object> appData = objectMapper.readValue(response.body(),
                            new TypeReference<Map<String, Object>>() {
                            });

                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> entities = (List<Map<String, Object>>) appData.get("entities");

                    if (entities == null) {
                        entities = List.of();
                    }

                    List<String> entityNames = entities.stream()
                            .map(e -> (String) e.get("name"))
                            .toList();

                    log.info("[ListEntitiesTool] Found {} entities in app {}", entityNames.size(), appId);

                    Map<String, Object> result = new HashMap<>();
                    result.put("entities", entityNames);
                    result.put("count", entityNames.size());
                    result.put("context", "app");
                    result.put("appId", appId);
                    
                    // Provide a formatted message for the AI to use
                    StringBuilder formattedMessage = new StringBuilder();
                    if (entityNames.isEmpty()) {
                        formattedMessage.append("No entities found in app **").append(appId).append("**. You can create entities by describing your data model.");
                    } else {
                        formattedMessage.append("Found ").append(entityNames.size()).append(" entity/entities in app **").append(appId).append("**:\n\n");
                        for (int i = 0; i < entityNames.size(); i++) {
                            formattedMessage.append(i + 1).append(". `").append(entityNames.get(i)).append("`\n");
                        }
                    }
                    result.put("message", formattedMessage.toString());

                    return ToolResult.success(getName(), result, executionTime);
                } else {
                    log.error("[ListEntitiesTool] Failed to fetch app: {} - {}", response.statusCode(),
                            response.body());
                    return ToolResult.error(getName(), "Failed to fetch app metadata: " + response.statusCode());
                }

            } else {
                // CASE 2: No App Context - Fallback to global schema list
                log.info("[ListEntitiesTool] No app context, listing all global schemas");

                String url = baseUrl + "/schema";

                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Accept", "application/json");

                if (context.token() != null && !context.token().isEmpty()) {
                    reqBuilder.header("Authorization", "Bearer " + context.token());
                }

                HttpRequest request = reqBuilder.GET().build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                long executionTime = System.currentTimeMillis() - startTime;

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    List<String> entityNames = objectMapper.readValue(
                            response.body(),
                            new TypeReference<List<String>>() {
                            });

                    log.info("[ListEntitiesTool] Found {} global entities", entityNames.size());

                    Map<String, Object> result = new HashMap<>();
                    result.put("entities", entityNames);
                    result.put("count", entityNames.size());
                    result.put("context", "global");
                    result.put("warning", "No app selected. Listing all global schemas.");
                    
                    // Provide a formatted message for the AI to use
                    StringBuilder formattedMessage = new StringBuilder();
                    formattedMessage.append("⚠️ No app selected. Showing all global schemas:\n\n");
                    if (entityNames.isEmpty()) {
                        formattedMessage.append("No global schemas found.");
                    } else {
                        for (int i = 0; i < entityNames.size(); i++) {
                            formattedMessage.append(i + 1).append(". `").append(entityNames.get(i)).append("`\n");
                        }
                    }
                    result.put("message", formattedMessage.toString());

                    return ToolResult.success(getName(), result, executionTime);
                } else {
                    log.error("[ListEntitiesTool] API error: {} - {}", response.statusCode(), response.body());
                    return ToolResult.error(getName(), "API error: " + response.statusCode());
                }
            }

        } catch (Exception e) {
            log.error("[ListEntitiesTool] Execution failed", e);
            return ToolResult.error(getName(), "Execution error: " + e.getMessage());
        }
    }
}
