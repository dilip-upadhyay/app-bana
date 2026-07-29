package com.appbana.ai.agent.tool;

import com.appbana.ai.agent.AgentContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.HashMap;

/**
 * Tool for rolling back an application to a previous AI-generated version.
 */
@Slf4j
public class RollbackAppTool implements Tool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final HttpClient httpClient;
    private final String baseUrl;

    public RollbackAppTool(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(60))
            .build();
    }

    @Override
    public String getName() {
        return "rollback_app";
    }

    @Override
    public String getDescription() {
        return "Reverts the current application back to a specific version number. Use this ONLY when the user explicitly asks to revert, rollback, or undo changes to a specific version.";
    }

    @Override
    public String getParameterSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "version": {
                  "type": "integer",
                  "description": "The specific version number to rollback to (e.g., 2)."
                }
              },
              "required": ["version"]
            }
            """;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments, AgentContext context) {
        log.info("[RollbackAppTool] Executing with args: {}", arguments);

        String appId = context.appId();
        String tenantId = context.tenantId();

        if (appId == null || appId.isBlank() || "default".equals(appId)) {
            return ToolResult.error(getName(), "Error: A specific application MUST be selected in the context to perform a rollback. Please ask the user to select an app.");
        }

        Integer version = null;
        Object versionObj = arguments.get("version");
        if (versionObj instanceof Number) {
            version = ((Number) versionObj).intValue();
        } else if (versionObj instanceof String) {
            try {
                version = Integer.parseInt((String) versionObj);
            } catch (NumberFormatException e) {
                return ToolResult.error(getName(), "Invalid version format.");
            }
        }

        if (version == null) {
            return ToolResult.error(getName(), "Error: Please specify a version number to rollback to.");
        }

        try {
            String url = baseUrl + "/api/" + tenantId + "/apps/" + appId + "/commits/rollback";
            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("version", version);
            
            String bodyJson = MAPPER.writeValueAsString(bodyMap);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson));

            if (context.token() != null && !context.token().isBlank()) {
                reqBuilder.header("Authorization", "Bearer " + context.token());
            }

            log.info("[RollbackAppTool] Initiating rollback for app {} to version {}", appId, version);
            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return ToolResult.success(getName(), "Successfully rolled back the application to version " + version + ".", 0);
            } else if (response.statusCode() == 401) {
                throw new BackendAuthException(getName() + ": rollback_app returned 401");
            } else {
                return ToolResult.error(getName(), "Rollback failed with status " + response.statusCode() + ": " + response.body());
            }

        } catch (BackendAuthException authEx) {
            log.warn("[RollbackAppTool] {}", authEx.getMessage());
            return ToolResult.authError(getName(), authEx.getMessage());
        } catch (Exception e) {
            log.error("[RollbackAppTool] Execution failed", e);
            return ToolResult.error(getName(), "Failed to rollback: " + e.getMessage());
        }
    }
}
