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
 * Tool for listing all applications in the tenant.
 * Use this when the user asks "List all apps" or needs to select an app.
 */
@Slf4j
public class ListAppsTool implements Tool {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public ListAppsTool(String baseUrl) {
        this.httpClient = HttpClient.newHttpClient();
        this.baseUrl = baseUrl;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getName() {
        return "list_apps";
    }

    @Override
    public String getDescription() {
        return "List all created applications. Use this when the user asks to see what apps exist, or when you need to help them select an app.";
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
            String tenantId = context.tenantId();
            if (tenantId == null || tenantId.isEmpty()) {
                tenantId = "default";
            }

            log.info("[ListAppsTool] Listing apps for tenant: {}", tenantId);

            String url = baseUrl + "/appbana-studio/" + tenantId + "/apps";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long executionTime = System.currentTimeMillis() - startTime;

            if (response.statusCode() == 200) {
                Map<String, Object> resultValues = objectMapper.readValue(response.body(),
                        new TypeReference<Map<String, Object>>() {
                        });

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> apps = (List<Map<String, Object>>) resultValues.get("apps");

                List<String> appNames = apps.stream()
                        .map(a -> (String) a.get("name"))
                        .toList();

                // Detailed info for agent - use HashMap to handle null values
                List<Map<String, String>> appDetails = apps.stream()
                        .map(a -> {
                            Map<String, String> details = new HashMap<>();
                            details.put("name", a.get("name") != null ? (String) a.get("name") : "Unnamed");
                            details.put("id", a.get("id") != null ? (String) a.get("id") : "");
                            details.put("description", a.get("description") != null ? (String) a.get("description") : "");
                            return details;
                        })
                        .toList();

                log.info("[ListAppsTool] Found {} apps", appNames.size());

                Map<String, Object> result = new HashMap<>();
                result.put("apps", appDetails);
                result.put("count", appNames.size());

                return ToolResult.success(getName(), result, executionTime);
            } else {
                log.error("[ListAppsTool] Failed to list apps: {} - {}", response.statusCode(), response.body());
                return ToolResult.error(getName(), "Failed to list apps: " + response.statusCode());
            }

        } catch (Exception e) {
            log.error("[ListAppsTool] Execution failed", e);
            return ToolResult.error(getName(), "Execution error: " + e.getMessage());
        }
    }
}
