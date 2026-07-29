package com.appbana.ai.agent.tool;

import com.appbana.ai.agent.AgentContext;
import com.appbana.ai.knowledge.MetadataValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Tool for creating a new AppBana application
 */
@Slf4j
public class CreateAppTool implements Tool {

  private final HttpClient httpClient;
  private final String backendUrl;
  private final ObjectMapper objectMapper;

  public CreateAppTool(String backendUrl) {
    this.backendUrl = backendUrl;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build();
    this.objectMapper = new ObjectMapper();
  }

  @Override
  public String getName() {
    return "create_app";
  }

  @Override
  public String getDescription() {
    return "Creates a new AppBana application. This must be called FIRST before creating any entities or pages. " +
        "Arguments: {name: string (required), description: string (optional)}";
  }

  @Override
  public String getParameterSchema() {
    return """
        {
          "type": "object",
          "properties": {
            "name": {
              "type": "string",
              "description": "App name (e.g., 'EmployeeManagement' or 'HR Management')"
            },
            "description": {
              "type": "string",
              "description": "Description of what the app does"
            }
          },
          "required": ["name"]
        }
        """;
  }

  @Override
  public ToolResult execute(Map<String, Object> args, AgentContext context) {
    long startTime = System.currentTimeMillis();

    try {
      log.info("[CreateAppTool] Creating app with args: {}", args);

      String name = (String) args.get("name");
      String description = (String) args.getOrDefault("description", "");
      String tenantId = context.tenantId();
      String token = context.token();

      // Generate app ID (backend requires it upfront)
      String appId = UUID.randomUUID().toString();

      // Build app metadata matching backend AppMetadata class
      Map<String, Object> appMeta = new HashMap<>();
      appMeta.put("id", appId); // Required by backend
      appMeta.put("name", name);
      appMeta.put("description", description);
      appMeta.put("tenantId", tenantId);
      // Don't include displayName - it's not in the backend schema

      // Call backend API to create app
      String url = String.format("%s/appbana-studio/%s/apps", backendUrl, tenantId);
      log.info("[CreateAppTool] POST {}", url);

      String jsonBody = objectMapper.writeValueAsString(appMeta);

      HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .header("Content-Type", "application/json");

      if (token != null && !token.isEmpty()) {
        requestBuilder.header("Authorization", "Bearer " + token);
      }

      HttpRequest request = requestBuilder
          .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
          .build();

      HttpResponse<String> response = httpClient.send(request,
          HttpResponse.BodyHandlers.ofString());

      long executionTime = System.currentTimeMillis() - startTime;

      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        log.info("[CreateAppTool] App created successfully: {} (ID: {})", name, appId);

        Map<String, Object> result = new HashMap<>();
        result.put("appId", appId);
        result.put("appName", name);
        result.put("description", description);
        result.put("status", "created");

        return ToolResult.success(getName(), result, executionTime);
      } else if (response.statusCode() == 401) {
        throw new BackendAuthException(getName() + ": create_app returned 401");
      } else {
        log.error("[CreateAppTool] API error: {} - {}", response.statusCode(), response.body());
        return ToolResult.error(getName(),
            "Failed to create app: " + response.statusCode() + " - " + response.body());
      }

    } catch (BackendAuthException authEx) {
      log.warn("[CreateAppTool] {}", authEx.getMessage());
      return ToolResult.authError(getName(), authEx.getMessage());
    } catch (Exception e) {
      log.error("[CreateAppTool] Execution failed", e);
      return ToolResult.error(getName(), "Error creating app: " + e.getMessage());
    }
  }
}
