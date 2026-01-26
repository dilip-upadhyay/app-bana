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
 * Tool for listing workflows in AppBana applied to the current app.
 */
@Slf4j
public class ListWorkflowsTool implements Tool {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public ListWorkflowsTool(String baseUrl) {
        this.httpClient = HttpClient.newHttpClient();
        this.baseUrl = baseUrl;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getName() {
        return "list_workflows";
    }

    @Override
    public String getDescription() {
        return "List all workflows/state-machines configured for the CURRENTLY SELECTED application. " +
                "If no app is selected, this tool will fail/warn.";
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

            if (appId == null || appId.equals("default") || appId.isEmpty()) {
                return ToolResult.error(getName(),
                        "No application selected. Please ask the user to select an app first.");
            }

            log.info("[ListWorkflowsTool] Listing workflows for app: {}", appId);

            // Fetch workflow data from backend
            String url = baseUrl + "/appbana-studio/" + tenantId + "/apps/" + appId + "/workflow";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long executionTime = System.currentTimeMillis() - startTime;

            if (response.statusCode() == 200) {
                Map<String, Object> workflowData = objectMapper.readValue(response.body(),
                        new TypeReference<Map<String, Object>>() {
                        });

                // Parse workflow structure (it might be a map of state machines)
                // Assuming root keys are entity names or workflow IDs
                int workflowCount = workflowData.size();
                List<String> workflowNames = workflowData.keySet().stream().toList();

                log.info("[ListWorkflowsTool] Found {} workflows in app {}", workflowCount, appId);

                Map<String, Object> result = new HashMap<>();
                result.put("workflows", workflowNames);
                result.put("count", workflowCount);
                result.put("appId", appId);

                return ToolResult.success(getName(), result, executionTime);
            } else {
                log.error("[ListWorkflowsTool] Failed to fetch workflows: {} - {}", response.statusCode(),
                        response.body());
                return ToolResult.error(getName(), "Failed to fetch workflows: " + response.statusCode());
            }

        } catch (Exception e) {
            log.error("[ListWorkflowsTool] Execution failed", e);
            return ToolResult.error(getName(), "Execution error: " + e.getMessage());
        }
    }
}
