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

    // 1. Resolve context (appId, tenantId)
    String tenantId = context.tenantId();
    String appId = (String) arguments.get("appId");
    if (appId == null || appId.isEmpty()) {
      appId = context.appId();
    }

    try {
      log.info("[CreateEntityTool] Creating entity with args: {}", arguments);

      // 2. Build entity metadata
      Map<String, Object> entityMetadata = buildEntityMetadata(arguments);

      // Inject app context into metadata
      if (appId != null && !appId.isEmpty() && !appId.equals("default")) {
        entityMetadata.put("appId", appId);
        entityMetadata.put("tenantId", tenantId);
      }

      // 3. Validate metadata
      ValidationResult validation = validator.validateEntity(entityMetadata);

      if (!validation.isValid()) {
        // Try auto-fix immediately without warring first
        ValidationResult originalValidation = validation;
        entityMetadata = validator.autoFix(entityMetadata, validation);

        // Re-validate
        validation = validator.validateEntity(entityMetadata);
        if (!validation.isValid()) {
          log.warn("[CreateEntityTool] Validation failed even after auto-fix: {}", validation.getDetailedErrors());
          return ToolResult.error(getName(),
              "Entity validation failed: " + validation.getDetailedErrors());
        }

        log.info("[CreateEntityTool] Applied auto-fixes (IDs/Types) successfully.");
      }

      // 4. Step 1: Create Schema (Global)
      // Always use /schema endpoint to create the entity definition first
      String schemaUrl = baseUrl + "/schema";
      String token = context.token();

      log.info("[CreateEntityTool] POST {}", schemaUrl);

      String jsonBody = new com.fasterxml.jackson.databind.ObjectMapper()
          .writeValueAsString(entityMetadata);

      HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
          .uri(URI.create(schemaUrl))
          .header("Content-Type", "application/json");

      if (token != null && !token.isEmpty()) {
        requestBuilder.header("Authorization", "Bearer " + token);
      }

      HttpResponse<String> response = httpClient.send(
          requestBuilder.POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build(),
          HttpResponse.BodyHandlers.ofString());

      long executionTime = System.currentTimeMillis() - startTime;

      // 4. Handle response
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        log.info("[CreateEntityTool] Schema created successfully: {}", arguments.get("name"));

        // Step 2: Link to App (if appId provided)
        if (appId != null && !appId.equals("default") && !appId.isEmpty()) {
          linkEntityToApp(appId, tenantId, token, (String) arguments.get("name"));
        }

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

  // Method to link entity to app
  private void linkEntityToApp(String appId, String tenantId, String token, String entityName) {
    try {
      String appUrl = String.format("%s/appbana-studio/%s/apps/%s", baseUrl, tenantId, appId);

      // 1. Fetch App
      HttpRequest getReq = HttpRequest.newBuilder()
          .uri(URI.create(appUrl))
          .header("Authorization", "Bearer " + token)
          .GET()
          .build();

      HttpResponse<String> getRes = httpClient.send(getReq, HttpResponse.BodyHandlers.ofString());
      if (getRes.statusCode() != 200) {
        log.error("Failed to fetch app for linking: {}", getRes.body());
        return;
      }

      Map<String, Object> appData = new com.fasterxml.jackson.databind.ObjectMapper().readValue(getRes.body(),
          Map.class);

      // 2. Add to schemas list
      List<String> schemas = (List<String>) appData.get("schemas");
      if (schemas == null) {
        schemas = new java.util.ArrayList<>();
      }
      if (!schemas.contains(entityName)) {
        schemas.add(entityName);
        appData.put("schemas", schemas);

        // 3. Save App
        String updateBody = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(appData);
        HttpRequest putReq = HttpRequest.newBuilder()
            .uri(URI.create(appUrl))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + token)
            .PUT(HttpRequest.BodyPublishers.ofString(updateBody))
            .build();

        HttpResponse<String> putRes = httpClient.send(putReq, HttpResponse.BodyHandlers.ofString());
        if (putRes.statusCode() == 200) {
          log.info("Linked entity {} to app {}", entityName, appId);
        } else {
          log.error("Failed to link entity to app: {}", putRes.body());
        }
      }
    } catch (Exception e) {
      log.error("Error linking entity to app", e);
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
