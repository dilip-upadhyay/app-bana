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

                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Accept", "application/json");

                if (context.token() != null && !context.token().isEmpty()) {
                    requestBuilder.header("Authorization", "Bearer " + context.token());
                }

                HttpRequest request = requestBuilder.GET().build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                long executionTime = System.currentTimeMillis() - startTime;

                // C4.4e -- this used to be `if (statusCode() == 200) { ... }` with every other status
                // falling silently through to the global fallback below. C4.4d removed one trigger
                // (the 401) and left the masking, but 403, 500, 502 and a mid-restart backend all
                // still reached it. The fallback cannot succeed for an app-scoped entity by
                // construction -- it looks up /schema/{bareName} while schema keys are
                // {tenantId}_{appId}_{entityName} -- so its only possible effect here was to convert
                // a transport error into "entity not found": a wrong answer about an entity that
                // exists, which is how the missing auth header survived unnoticed.
                if (response.statusCode() != 200) {
                    if (response.statusCode() == 401) {
                        throw new BackendAuthException(getName() + ": app-context lookup for '"
                                + targetEntityName + "' returned 401");
                    }
                    log.error("[GetEntityDetailsTool] App-context lookup for '{}' in app '{}' failed: {} - {}",
                            targetEntityName, appId, response.statusCode(), response.body());
                    return ToolResult.error(getName(),
                            "Could not read app '" + appId + "' while looking up entity '"
                                    + targetEntityName + "': backend returned " + response.statusCode()
                                    + ". This is a transport or authorization failure, not a missing entity.");
                }

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

                // C4.4e -- no fall-through to the global fallback: an app is selected, so the entity
                // would be keyed {tenantId}_{appId}_{name} and /schema/{bareName} cannot match it.
                // Answer the question that was actually asked instead of asking a different one.
                return ToolResult.error(getName(),
                        "Entity '" + targetEntityName + "' does not exist in app '" + appId + "'.");
            }

            // 2. Global/Schema lookup -- reached only when no app is selected, which is the only
            // case where an unprefixed schema key is the right thing to ask for.
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
            } else if (response.statusCode() == 401) {
                throw new BackendAuthException(getName() + ": global lookup for '" + targetEntityName + "' returned 401");
            } else {
                return ToolResult.error(getName(), "API error: " + response.statusCode());
            }

        } catch (BackendAuthException authEx) {
            log.warn("[GetEntityDetailsTool] {}", authEx.getMessage());
            return ToolResult.authError(getName(), authEx.getMessage());
        } catch (Exception e) {
            log.error("[GetEntityDetailsTool] Execution failed", e);
            return ToolResult.error(getName(), "Execution error: " + e.getMessage());
        }
    }
}
