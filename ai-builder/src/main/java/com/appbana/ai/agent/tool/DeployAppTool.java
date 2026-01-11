package com.appbana.ai.agent.tool;

import com.appbana.ai.agent.AgentContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

@Slf4j
public class DeployAppTool implements Tool {

    private final String backendUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DeployAppTool(String backendUrl) {
        this.backendUrl = backendUrl;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public String getName() {
        return "deploy_app";
    }

    @Override
    public String getDescription() {
        return "Deploys the current application to the DEV environment. Use this when the user is satisfied with the app and asks to 'deploy', 'publish', or 'test' it. Returns the test URL.";
    }

    @Override
    public String getParameterSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "appId": {
                      "type": "string",
                      "description": "The ID of the application to deploy. If not provided, uses the current context app ID."
                    }
                  }
                }
                """;
    }

    @Override
    public ToolResult execute(Map<String, Object> args, AgentContext context) {
        long startTime = System.currentTimeMillis();
        String tenantId = context.tenantId();

        // Prefer appId from args, fallback to context
        String appId = (String) args.get("appId");
        if (appId == null || appId.isEmpty()) {
            appId = context.appId();
        }

        String token = context.token();

        if (appId == null || appId.equals("default") || appId.isEmpty()) {
            return ToolResult.error(getName(),
                    "No active application found to deploy. Please create an app first or provide an appId.");
        }

        try {
            // Call backend publish endpoint with empty body
            // Backend will fetch metadata from DB using AppManager.getAppFullMetadata()
            String publishUrl = String.format("%s/api/%s/apps/%s/publish?env=DEV", backendUrl, tenantId, appId);
            log.info("[DeployAppTool] Publishing app {} to DEV environment", appId);
            log.info("[DeployAppTool] Backend will fetch metadata from database");

            HttpRequest publishReq = HttpRequest.newBuilder()
                    .uri(URI.create(publishUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString("{}")) // Empty body
                    .build();

            HttpResponse<String> publishRes = httpClient.send(publishReq, HttpResponse.BodyHandlers.ofString());
            long executionTime = System.currentTimeMillis() - startTime;

            if (publishRes.statusCode() >= 200 && publishRes.statusCode() < 300) {
                // Parse success response
                Map<String, Object> response = objectMapper.readValue(publishRes.body(), Map.class);

                log.info("[DeployAppTool] ✅ Deployment successful");
                log.info("[DeployAppTool] Version: {}", response.get("version"));
                log.info("[DeployAppTool] Tables created: {}", response.get("tablesCreated"));

                Map<String, Object> result = Map.of(
                        "status", "deployed",
                        "environment", "DEV",
                        "version", response.getOrDefault("version", "unknown"),
                        "tablesCreated", response.getOrDefault("tablesCreated", java.util.List.of()),
                        "testUrl", String.format("http://localhost:3000/app/%s", appId),
                        "summary", response.getOrDefault("summary", "Deployment successful"));

                return ToolResult.success(getName(), result, executionTime);
            } else {
                // Parse error response
                String errorBody = publishRes.body();
                log.error("[DeployAppTool] ❌ Deployment failed: {}", errorBody);

                try {
                    Map<String, Object> errorResponse = objectMapper.readValue(errorBody, Map.class);
                    String errorMessage = (String) errorResponse.getOrDefault("error", "Unknown error");
                    String errorDetails = (String) errorResponse.get("details");

                    return ToolResult.error(getName(),
                            "Deployment failed: " + errorMessage +
                                    (errorDetails != null ? "\n" + errorDetails : ""));
                } catch (Exception e) {
                    return ToolResult.error(getName(), "Deployment failed: " + errorBody);
                }
            }

        } catch (Exception e) {
            log.error("[DeployAppTool] Exception during deployment", e);
            return ToolResult.error(getName(), "Deployment error: " + e.getMessage());
        }
    }
}
