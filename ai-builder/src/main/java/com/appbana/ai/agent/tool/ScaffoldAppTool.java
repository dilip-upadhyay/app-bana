package com.appbana.ai.agent.tool;

import com.appbana.ai.agent.AgentContext;
import com.appbana.ai.knowledge.MetadataValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.*;

/**
 * Story 2: ScaffoldAppTool - One-Shot App Creation
 * 
 * Creates a complete AppBana application (App + Entities + Pages) in a single
 * tool call.
 * This reduces LLM token usage and execution time by eliminating multi-turn
 * conversations.
 * 
 * Architecture: Compound Pattern - orchestrates existing tools (CreateAppTool,
 * CreateEntityTool, etc.)
 * Reliability: Implements rollback mechanism to prevent "zombie apps" on
 * partial failures.
 */
@Slf4j
public class ScaffoldAppTool implements Tool {

  private final MetadataValidator validator;
  private final String baseUrl;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public ScaffoldAppTool(MetadataValidator validator, String baseUrl) {
    this.validator = validator;
    this.baseUrl = baseUrl;
    this.objectMapper = new ObjectMapper();

    // Story 6: Configure longer timeout (120s) for batch operations
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(120))
        .build();
  }

  @Override
  public String getName() {
    return "scaffold_app";
  }

  @Override
  public String getDescription() {
    return "Creates a complete AppBana application in ONE SHOT: App + Entities + Pages + Deployment. " +
        "Use this when the user asks to 'create an app' or 'build an application'. " +
        "This is MUCH faster and cheaper than using individual tools sequentially.";
  }

  @Override
  public String getParameterSchema() {
    return """
        {
          "type": "object",
          "properties": {
            "appName": {
              "type": "string",
              "description": "Application name (e.g., 'Salon Management', 'HR System')"
            },
            "description": {
              "type": "string",
              "description": "Brief description of what the app does"
            },
            "entities": {
              "type": "array",
              "description": "Array of entity definitions (database tables)",
              "items": {
                "type": "object",
                "properties": {
                  "name": {"type": "string"},
                  "displayName": {"type": "string"},
                  "fields": {
                    "type": "array",
                    "items": {
                      "type": "object",
                      "properties": {
                        "id": {"type": "string"},
                        "name": {"type": "string"},
                        "type": {"type": "string"},
                        "required": {"type": "boolean"},
                        "label": {"type": "string"}
                      },
                      "required": ["id", "name", "type", "required"]
                    }
                  }
                },
                "required": ["name", "fields"]
              }
            },
            "pages": {
              "type": "array",
              "description": "Array of page definitions (UI screens)",
              "items": {
                "type": "object",
                "properties": {
                  "name": {"type": "string"},
                  "path": {"type": "string"},
                  "type": {"type": "string", "enum": ["list", "form", "detail"]},
                  "entityName": {"type": "string"}
                },
                "required": ["name", "path", "type", "entityName"]
              }
            }
          },
          "required": ["appName", "entities"]
        }
        """;
  }

  @Override
  public ToolResult execute(Map<String, Object> arguments, AgentContext context) {
    long startTime = System.currentTimeMillis();
    String createdAppId = null; // Track for rollback

    log.info("[ScaffoldAppTool] Starting one-shot app creation");
    log.info("[ScaffoldAppTool] Arguments: {}", arguments);

    try {
      // Story 2: Parameter validation
      String appName = (String) arguments.get("appName");
      if (appName == null || appName.isBlank()) {
        return ToolResult.error(getName(), "Parameter 'appName' is required");
      }

      @SuppressWarnings("unchecked")
      List<Map<String, Object>> entities = (List<Map<String, Object>>) arguments.get("entities");
      if (entities == null || entities.isEmpty()) {
        return ToolResult.error(getName(), "Parameter 'entities' is required and must not be empty");
      }

      @SuppressWarnings("unchecked")
      List<Map<String, Object>> pages = (List<Map<String, Object>>) arguments.getOrDefault("pages", new ArrayList<>());

      String description = (String) arguments.getOrDefault("description", "");

      // Story 3: Phase 1 - Create App
      log.info("[ScaffoldAppTool] Phase 1: Creating app '{}'", appName);
      CreateAppTool appTool = new CreateAppTool(baseUrl);

      Map<String, Object> appArgs = new HashMap<>();
      appArgs.put("name", appName);
      appArgs.put("description", description);

      ToolResult appResult = appTool.execute(appArgs, context);
      if (!appResult.isSuccess()) {
        log.error("[ScaffoldAppTool] App creation failed: {}", appResult.getError());
        return ToolResult.error(getName(), "App creation failed: " + appResult.getError());
      }

      // Extract appId from result
      @SuppressWarnings("unchecked")
      Map<String, Object> appData = (Map<String, Object>) appResult.getData();
      createdAppId = (String) appData.get("appId");

      log.info("[ScaffoldAppTool] ✅ App created successfully: {}", createdAppId);

      // Story 4-6: TODO - Entity, Page, Deploy phases

      // Temporary success response for Story 3 testing
      Map<String, Object> result = new HashMap<>();
      result.put("status", "app_created");
      result.put("appId", createdAppId);
      result.put("appName", appName);
      result.put("entitiesProvided", entities.size());
      result.put("pagesProvided", pages.size());
      result.put("message", "Story 3: App creation complete. Entity/Page/Deploy phases coming in Stories 4-6.");

      long executionTime = System.currentTimeMillis() - startTime;
      return ToolResult.success(getName(), result, executionTime);

    } catch (Exception e) {
      log.error("[ScaffoldAppTool] Execution failed", e);

      // Story 3: Rollback mechanism - delete the app if it was created
      if (createdAppId != null) {
        log.warn("[ScaffoldAppTool] Rolling back - deleting app: {}", createdAppId);
        try {
          rollback(createdAppId, context);
          log.info("[ScaffoldAppTool] Rollback successful");
        } catch (Exception rollbackError) {
          log.error("[ScaffoldAppTool] Rollback failed", rollbackError);
          // Continue to return the original error
        }
      }

      return ToolResult.error(getName(), "Execution error: " + e.getMessage());
    }
  }

  /**
   * Story 3: Rollback mechanism - delete app to prevent "zombie" state
   */
  private void rollback(String appId, AgentContext context) throws Exception {
    log.info("[ScaffoldAppTool] Executing rollback for appId: {}", appId);

    String deleteUrl = String.format("%s/appbana-studio/%s/apps/%s",
        baseUrl, context.tenantId(), appId);

    HttpRequest deleteRequest = HttpRequest.newBuilder()
        .uri(URI.create(deleteUrl))
        .header("Authorization", "Bearer " + context.token())
        .DELETE()
        .build();

    java.net.http.HttpResponse<String> response = httpClient.send(
        deleteRequest,
        java.net.http.HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      log.info("[ScaffoldAppTool] App {} successfully deleted during rollback", appId);
    } else {
      log.error("[ScaffoldAppTool] Failed to delete app during rollback: {} - {}",
          response.statusCode(), response.body());
      throw new Exception("Rollback delete failed: " + response.statusCode());
    }
  }
}
