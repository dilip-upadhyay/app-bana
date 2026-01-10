package com.appbana.ai.agent.tool;

import com.appbana.ai.agent.AgentContext;
import com.appbana.ai.knowledge.MetadataValidator;
import com.appbana.ai.knowledge.ValidationResult;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool for creating entities in AppBana
 * Story 8.3: Essential Tools Implementation
 */
@Slf4j
public class CreateEntityTool implements Tool {

  private final MetadataValidator validator;
  private final HttpClient httpClient;
  private final String baseUrl;

  public CreateEntityTool(MetadataValidator validator, String baseUrl) {
    this.validator = validator;
    this.httpClient = HttpClient.newHttpClient();
    this.baseUrl = baseUrl;
  }

  @Override
  public String getName() {
    return "create_entity";
  }

  @Override
  public String getDescription() {
    return "Create a new entity (database table) in the application. " +
        "Use this when the user wants to add a new data model like Customer, Product, Order, etc.";
  }

  @Override
  public String getParameterSchema() {
    return """
        {
          "type": "object",
          "properties": {
            "name": {
              "type": "string",
              "description": "Entity name (e.g., 'Customer', 'Product')"
            },
            "displayName": {
              "type": "string",
              "description": "Human-readable display name"
            },
            "fields": {
              "type": "array",
              "description": "Array of field definitions",
              "items": {
                "type": "object",
                "properties": {
                  "name": {"type": "string"},
                  "type": {"type": "string", "description": "Field type: text, email, number, date, etc."},
                  "required": {"type": "boolean"},
                  "label": {"type": "string"}
                },
                "required": ["name", "type", "required"]
              }
            },
            "appId": {
              "type": "string",
              "description": "Target App ID. If not provided, uses current context."
            }
          },
          "required": ["name", "fields"]
        }
        """;
  }

  @Override
  public ToolResult execute(Map<String, Object> arguments, AgentContext context) {
    long startTime = System.currentTimeMillis();

    try {
      log.info("[CreateEntityTool] Creating entity with args: {}", arguments);

      // 1. Build entity metadata
      Map<String, Object> entityMetadata = buildEntityMetadata(arguments);

      // 2. Validate metadata
      ValidationResult validation = validator.validateEntity(entityMetadata);

      if (!validation.isValid()) {
        log.warn("[CreateEntityTool] Validation failed: {}", validation.getDetailedErrors());

        // Try auto-fix
        entityMetadata = validator.autoFix(entityMetadata, validation);

        // Re-validate
        validation = validator.validateEntity(entityMetadata);
        if (!validation.isValid()) {
          return ToolResult.error(getName(),
              "Entity validation failed: " + validation.getDetailedErrors());
        }

        log.info("[CreateEntityTool] Auto-fix applied successfully");
      }

      // 3. Call backend API
      String tenantId = context.tenantId();
      String token = context.token();

      // Prefer appId from args, fallback to context
      String appId = (String) arguments.get("appId");
      if (appId == null || appId.isEmpty()) {
        appId = context.appId();
      }

      // Use app-specific endpoint if appId is available
      String url;
      if (appId != null && !appId.equals("default") && !appId.isEmpty()) {
        url = String.format("%s/appbana-studio/%s/apps/%s/entities", baseUrl, tenantId, appId);
      } else {
        // Fallback to legacy endpoint (only if no appId)
        url = baseUrl + "/schema";
      }

      log.info("[CreateEntityTool] POST {}", url);

      String jsonBody = new com.fasterxml.jackson.databind.ObjectMapper()
          .writeValueAsString(entityMetadata);

      HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .header("Content-Type", "application/json");

      if (context.token() != null && !context.token().isEmpty()) {
        requestBuilder.header("Authorization", "Bearer " + context.token());
      }

      HttpRequest request = requestBuilder
          .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
          .build();

      HttpResponse<String> response = httpClient.send(request,
          HttpResponse.BodyHandlers.ofString());

      long executionTime = System.currentTimeMillis() - startTime;

      // 4. Handle response
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        log.info("[CreateEntityTool] Entity created successfully: {}", arguments.get("name"));

        Map<String, Object> result = new HashMap<>();
        result.put("entityName", arguments.get("name"));
        result.put("status", "created");
        result.put("fieldCount", ((List<?>) arguments.get("fields")).size());

        return ToolResult.success(getName(), result, executionTime);
      } else {
        log.error("[CreateEntityTool] API error: {} - {}", response.statusCode(), response.body());
        return ToolResult.error(getName(),
            "API error: " + response.statusCode() + " - " + response.body());
      }

    } catch (Exception e) {
      log.error("[CreateEntityTool] Execution failed", e);
      return ToolResult.error(getName(), "Execution error: " + e.getMessage());
    }
  }

  /**
   * Build entity metadata from tool arguments
   */
  private Map<String, Object> buildEntityMetadata(Map<String, Object> arguments) {
    Map<String, Object> metadata = new HashMap<>();

    String name = (String) arguments.get("name");
    metadata.put("name", name);
    metadata.put("id", name.toLowerCase().replaceAll("\\s+", "_"));
    metadata.put("displayName", arguments.getOrDefault("displayName", name));
    metadata.put("fields", arguments.get("fields"));
    metadata.put("datasource", "default");

    return metadata;
  }
}
