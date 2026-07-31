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
                  "label": {"type": "string"},
                  "conditions": {
                    "type": "object",
                    "description": "Phase B2 — optional conditional visibility. Contains showWhen / requiredWhen / disabledWhen expressions. Each expression is either a leaf {field, op, value} where op is one of equals|notEquals|in|notIn|gt|lt|gte|lte|contains|isEmpty|isNotEmpty, or a combinator {and:[...]}, {or:[...]}, {not:{...}}."
                  },
                  "fileConstraints": {
                    "type": "object",
                    "description": "Phase B3 — required when type='file'. Shape: {maxSizeBytes:number, acceptedMimeTypes:string[]}. Example: {maxSizeBytes: 10485760, acceptedMimeTypes: [image/*, application/pdf]}."
                  },
                  "referenceEntity": {
                    "type": "string",
                    "description": "Phase B4 — for type='reference' fields, names the parent entity (e.g. 'Customer'). Required for 1:N relationships."
                  },
                  "onDelete": {
                    "type": "string",
                    "enum": ["cascade", "restrict", "setNull"],
                    "description": "Phase B4 — cascade policy for reference fields when the parent row is deleted. Defaults to 'restrict'."
                  }
                },
                "required": ["name", "type", "required"]
              }
            },
            "approvalRequired": {
              "type": "boolean",
              "description": "Phase C4 — set true to put this entity behind a two-person (maker-checker) approval workflow. Rows are then created as DRAFT, must be submitted for approval, and are invisible to normal reads until a DIFFERENT user approves them. Use for regulated records: customer onboarding, KYC, loan/credit applications, expense claims, purchase orders, policy issuance, contracts, employee onboarding, payments. Do NOT set it for reference/lookup tables or low-risk data (blog posts, todos, product catalogues). Only set it after the user has agreed to the approval flow in the Phase 1 specification."
            },
            "approvalLevels": {
              "type": "integer",
              "enum": [1, 2],
              "description": "Only meaningful when approvalRequired is true. 1 (default) is the standard single-checker workflow. Set to 2 for a two-level checker chain (checker-1 approves first, then a DIFFERENT checker-2 gives final signoff) — use for higher-stakes records such as large payments, policy issuance, or contract signoff where a single reviewer isn't enough. A checker-2 reject sends the record back to checker-1 for re-review rather than rejecting it outright."
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
      } else if (response.statusCode() == 401) {
        throw new BackendAuthException(getName() + ": create_entity returned 401");
      } else {
        log.error("[CreateEntityTool] API error: {} - {}", response.statusCode(), response.body());
        return ToolResult.error(getName(),
            "API error: " + response.statusCode() + " - " + response.body());
      }

    } catch (BackendAuthException authEx) {
      log.warn("[CreateEntityTool] {}", authEx.getMessage());
      return ToolResult.authError(getName(), authEx.getMessage());
    } catch (Exception e) {
      log.error("[CreateEntityTool] Execution failed", e);
      return ToolResult.error(getName(), "Execution error: " + e.getMessage());
    }
  }

  // Method to link entity to app
  // Review #13 (C4.4f follow-up): this used to swallow every failure (including 401) into a
  // log.error + silent return, so create_entity reported success while the entity was never
  // actually linked to the app. It now throws on ANY non-2xx (BackendAuthException for 401,
  // a plain RuntimeException otherwise) so the failure propagates to execute()'s try/catch and
  // the tool reports the failure instead of a false success.
  private void linkEntityToApp(String appId, String tenantId, String token, String entityName) throws Exception {
    String appUrl = String.format("%s/appbana-studio/%s/apps/%s", baseUrl, tenantId, appId);

    // 1. Fetch App
    HttpRequest getReq = HttpRequest.newBuilder()
        .uri(URI.create(appUrl))
        .header("Authorization", "Bearer " + token)
        .GET()
        .build();

    HttpResponse<String> getRes = httpClient.send(getReq, HttpResponse.BodyHandlers.ofString());
    if (getRes.statusCode() == 401) {
      throw new BackendAuthException(getName() + ": link-entity-to-app GET returned 401");
    }
    if (getRes.statusCode() != 200) {
      throw new IllegalStateException("Failed to fetch app for linking: " + getRes.body());
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
      if (putRes.statusCode() == 401) {
        throw new BackendAuthException(getName() + ": link-entity-to-app PUT returned 401");
      }
      if (putRes.statusCode() != 200) {
        throw new IllegalStateException("Failed to link entity to app: " + putRes.body());
      }
      log.info("Linked entity {} to app {}", entityName, appId);
    }
  }

  /**
   * Build entity metadata from tool arguments
   */
  // Package-private (not private) so CreateEntityToolApprovalTest can assert the
  // exact body this tool POSTs to /schema without standing up an HTTP stub. This
  // method decides what survives into the backend schema, so it is worth testing
  // directly — Task C4.1 exists because a field silently failed to survive it.
  Map<String, Object> buildEntityMetadata(Map<String, Object> arguments) {
    Map<String, Object> metadata = new HashMap<>();

    String name = (String) arguments.get("name");
    metadata.put("name", name);
    metadata.put("id", name.toLowerCase().replaceAll("\\s+", "_"));
    metadata.put("displayName", arguments.getOrDefault("displayName", name));
    metadata.put("fields", arguments.get("fields"));
    metadata.put("datasource", "default");

    // Task C4.1 — forward the approval flag to the backend's EntitySchema.
    // This method builds the ENTIRE body POSTed to /schema, so anything not
    // copied here is silently dropped. Before C4.1 'approvalRequired' was one
    // of those: SchemaEnricher already injected the 8 approval columns when the
    // flag was present, so the physical table came out approval-shaped, but the
    // schema record itself had approvalRequired=false — and every backend guard
    // (GenericEntityRoutes, ApprovalService, EntityCrudService) branches on
    // schema.isApprovalRequired(), not on the presence of the columns. The
    // result was an entity that LOOKED approval-enabled and behaved as if it
    // were not. Only forward an explicit true; never emit the key otherwise, so
    // non-approval entities keep exactly the payload shape they had before.
    if (Boolean.TRUE.equals(arguments.get("approvalRequired"))) {
      metadata.put("approvalRequired", true);

      // approvalLevels only means anything alongside approvalRequired=true, so it is
      // nested inside this branch rather than forwarded unconditionally. Only 2 is worth
      // sending explicitly — 1 is the backend's own default (see EntitySchema.getEffectiveApprovalLevels()).
      Object levelsArg = arguments.get("approvalLevels");
      int levels = (levelsArg instanceof Number n) ? n.intValue() : 1;
      if (levels == 2) {
        metadata.put("approvalLevels", 2);
      }
    }

    return metadata;
  }
}
