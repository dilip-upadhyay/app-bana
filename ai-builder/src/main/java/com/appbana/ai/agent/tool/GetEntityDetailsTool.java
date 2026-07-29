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
import java.util.Optional;

/**
 * Tool for getting details (schema/fields) of a specific entity.
 * Critical for questions like "How many fields in UserDetails?".
 */
@Slf4j
public class GetEntityDetailsTool implements Tool {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public GetEntityDetailsTool(String baseUrl) {
        this.httpClient = HttpClient.newHttpClient();
        this.baseUrl = baseUrl;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getName() {
        return "get_entity_details";
    }

    @Override
    public String getDescription() {
        return "Get the full schema (fields, types, relationships) for a SPECIFIC entity by name. " +
                "Use this when you need to know about columns, data types, or counts of fields in an entity.";
    }

    @Override
    public String getParameterSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "entityName": {
                      "type": "string",
                      "description": "The name of the entity to inspect (e.g. 'UserDetails', 'Employee')"
                    }
                  },
                  "required": ["entityName"]
                }
                """;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments, AgentContext context) {
        long startTime = System.currentTimeMillis();

        try {
            String entityNameArgs = (String) arguments.get("entityName");
            if (entityNameArgs == null || entityNameArgs.isBlank()) {
                return ToolResult.error(getName(), "entityName parameter is required");
            }
            // Normalize
            final String targetEntityName = entityNameArgs.trim();

            String appId = context.appId();
            String tenantId = context.tenantId();

            // 1. Try fetching from App Context first (Preferred)
            if (appId != null && !appId.equals("default") && !appId.isEmpty()) {
                log.info("[GetEntityDetailsTool] Fetching details for entity '{}' in app '{}'", targetEntityName,
                        appId);

                String url = baseUrl + "/appbana-studio/" + tenantId + "/apps/" + appId;

                // C4.4d -- worst of the five: a 401 here is not surfaced. The status != 200
                // path falls through to the global /schema fallback (which does authenticate),
                // and that lookup uses the bare entity name with no tenant/app prefix, so it
                // misses too. The tool then reports "entity not found" -- a wrong answer rather
                // than an auth error, for an entity that exists.
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Accept", "application/json");

                if (context.token() != null && !context.token().isEmpty()) {
                    requestBuilder.header("Authorization", "Bearer " + context.token());
                }

                HttpRequest request = requestBuilder.GET().build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                long executionTime = System.currentTimeMillis() - startTime;

                if (response.statusCode() == 200) {
                    Map<String, Object> appData = objectMapper.readValue(response.body(),
                            new TypeReference<Map<String, Object>>() {
                            });

                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> entities = (List<Map<String, Object>>) appData.get("entities");

                    if (entities != null) {
                        Optional<Map<String, Object>> match = entities.stream()
                                .filter(e -> targetEntityName.equalsIgnoreCase((String) e.get("name")))
                                .findFirst();

                        if (match.isPresent()) {
                            return ToolResult.success(getName(), match.get(), executionTime);
                        }
                    }

                    // If not found in app, maybe it's a new entity user just mentioned?
                    // Or maybe it exists globally? Fall through to global check.
                    log.info("[GetEntityDetailsTool] Entity '{}' not found in app '{}'. Checking global...",
                            targetEntityName, appId);
                }
            }

            // 2. Global/Schema API fallback
            log.info("[GetEntityDetailsTool] Fetching details for entity '{}' from global schema", targetEntityName);
            String url = baseUrl + "/schema/" + targetEntityName;

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
                Map<String, Object> schema = objectMapper.readValue(response.body(),
                        new TypeReference<Map<String, Object>>() {
                        });
                return ToolResult.success(getName(), schema, executionTime);
            } else if (response.statusCode() == 404) {
                return ToolResult.error(getName(),
                        "Entity '" + targetEntityName + "' not found in current app or global schema.");
            } else {
                return ToolResult.error(getName(), "API error: " + response.statusCode());
            }

        } catch (Exception e) {
            log.error("[GetEntityDetailsTool] Execution failed", e);
            return ToolResult.error(getName(), "Execution error: " + e.getMessage());
        }
    }
}
