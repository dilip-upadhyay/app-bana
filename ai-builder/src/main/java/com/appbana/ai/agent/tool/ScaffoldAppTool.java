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

  public String getDescription() {
    return "Creates a complete AppBana application in ONE SHOT: App + Entities + Pages + Deployment. " +
        "WARNING: Because of Specification Driven Development rules, DO NOT invoke this tool until you have proposed the features to the user and they have explicitly replied with confirmation/approval.";
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
                  "approvalRequired": {
                    "type": "boolean",
                    "description": "Phase C4 — set true to put this entity behind a two-person (maker-checker) approval workflow. Rows are then created as DRAFT, must be submitted for approval, and are invisible to normal reads until a DIFFERENT user approves them. Use for regulated records: customer onboarding, KYC, loan/credit applications, expense claims, purchase orders, policy issuance, contracts, employee onboarding, payments. Do NOT set it for reference/lookup tables or low-risk data (blog posts, todos, product catalogues). Only set it after the user has agreed to the approval flow in the Phase 1 specification."
                  },
                  "approvalLevels": {
                    "type": "integer",
                    "enum": [1, 2],
                    "description": "Only meaningful when approvalRequired is true. 1 (default) is the standard single-checker workflow. Set to 2 for a two-level checker chain (checker-1 approves first, then a DIFFERENT checker-2 gives final signoff) — use for higher-stakes records such as large payments, policy issuance, or contract signoff."
                  },
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

    log.info("[ScaffoldAppTool] ═══════════════════════════════════════════════════════");
    log.info("[ScaffoldAppTool] Starting ONE-SHOT app creation");
    log.info("[ScaffoldAppTool] ═══════════════════════════════════════════════════════");

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

      // Log AI-generated metadata structure
      log.info("[ScaffoldAppTool] 📋 AI-Generated App Metadata:");
      log.info("[ScaffoldAppTool]   App Name: {}", appName);
      log.info("[ScaffoldAppTool]   Description: {}", description.isEmpty() ? "(none)" : description);
      log.info("[ScaffoldAppTool]   Entities: {} defined", entities.size());
      log.info("[ScaffoldAppTool]   Pages: {} defined", pages.size());

      // Log entity details
      log.info("[ScaffoldAppTool] 🗂️  Entity Definitions:");
      for (int i = 0; i < entities.size(); i++) {
        Map<String, Object> entity = entities.get(i);
        String entityName = (String) entity.get("name");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) entity.get("fields");
        int fieldCount = fields != null ? fields.size() : 0;
        log.info("[ScaffoldAppTool]   {}. {} ({} fields)", i + 1, entityName, fieldCount);

        if (fields != null) {
          for (Map<String, Object> field : fields) {
            String fieldName = (String) field.get("name");
            String fieldType = (String) field.get("type");
            Boolean required = (Boolean) field.getOrDefault("required", false);
            log.info("[ScaffoldAppTool]      - {} : {} {}",
                fieldName, fieldType, required ? "[REQUIRED]" : "");
          }
        }
      }

      // Log page details
      if (!pages.isEmpty()) {
        log.info("[ScaffoldAppTool] 📄 Page Definitions:");
        for (int i = 0; i < pages.size(); i++) {
          Map<String, Object> page = pages.get(i);
          String pageName = (String) page.get("name");
          String pageType = (String) page.get("type");
          String entityName = (String) page.get("entityName");
          String path = (String) page.get("path");
          log.info("[ScaffoldAppTool]   {}. {} ({}) for {} → {}",
              i + 1, pageName, pageType, entityName, path);
        }
      }
      log.info("[ScaffoldAppTool] ───────────────────────────────────────────────────────");

      // Story 3: Phase 1 - Create App (or use existing)
      // C4.4e Review #12 -- the orphan-app lead. This branch used to reuse context.appId()
      // outright and skip CreateAppTool entirely, so entities landed in appbana_schemas with
      // no matching row in appbana_apps whenever the caller's appId did not already have one
      // (e.g. a stale persisted currentApp in the Studio's Zustand store). list_apps and any
      // app-listing UI then had no way to see the app the agent just built, even though its
      // entities and physical tables exist. Verify the row exists before trusting the id;
      // create it under that SAME id if it does not, instead of silently trusting the caller.
      if (context.appId() != null && !context.appId().isBlank() && !"default".equals(context.appId())) {
        log.info("[ScaffoldAppTool] Phase 1: Re-using app ID '{}' from context -- verifying it exists",
            context.appId());
        if (!appRowExists(context.appId(), context.tenantId(), context)) {
          log.warn("[ScaffoldAppTool] App '{}' has no appbana_apps row -- creating one instead of "
              + "silently building orphaned entities under it", context.appId());
          createAppRowWithId(context.appId(), context.tenantId(), appName, description, context);
        }
        createdAppId = context.appId();
      } else {
        log.info("[ScaffoldAppTool] Phase 1: Creating new app '{}'", appName);
        CreateAppTool appTool = new CreateAppTool(baseUrl);

        Map<String, Object> appArgs = new HashMap<>();
        appArgs.put("name", appName);
        appArgs.put("description", description);

        ToolResult appResult = appTool.execute(appArgs, context);
        if (!appResult.isSuccess()) {
          log.error("[ScaffoldAppTool] App creation failed: {}", appResult.getError());
          if (appResult.isAuthFailure()) {
            throw new BackendAuthException(appResult.getError());
          }
          return ToolResult.error(getName(), "App creation failed: " + appResult.getError());
        }

        // Extract appId from result
        @SuppressWarnings("unchecked")
        Map<String, Object> appData = (Map<String, Object>) appResult.getData();
        createdAppId = (String) appData.get("appId");

        log.info("[ScaffoldAppTool] ✅ App created successfully: {}", createdAppId);
      }
      
      // Store in context for AiChatController to use for auto-commit
      context.variables().put("createdAppId", createdAppId);

      // Story 4: Phase 2 - Create Entities
      log.info("[ScaffoldAppTool] Phase 2: Creating {} entities", entities.size());

      // Collect known entity names for reference-entity inference
      Set<String> knownEntityNames = new java.util.LinkedHashSet<>();
      for (Map<String, Object> e : entities) {
        Object n = e.get("name");
        if (n != null) knownEntityNames.add((String) n);
      }
      // Enrich entities: coerce types + inject baseline + auto-infer referenceEntity
      new SchemaEnricher().enrichAll(entities, knownEntityNames);

      CreateEntityTool entityTool = new CreateEntityTool(validator, baseUrl);
      List<String> createdEntities = new ArrayList<>();

      for (int i = 0; i < entities.size(); i++) {
        Map<String, Object> entityDef = entities.get(i);
        String entityName = (String) entityDef.get("name");

        log.info("[ScaffoldAppTool] Creating entity {}/{}: {}", i + 1, entities.size(), entityName);

        // Inject appId into entity definition
        entityDef.put("appId", createdAppId);

        ToolResult entityResult = entityTool.execute(entityDef, context);
        if (!entityResult.isSuccess()) {
          log.error("[ScaffoldAppTool] Entity creation failed for '{}': {}", entityName, entityResult.getError());
          if (entityResult.isAuthFailure()) {
            throw new BackendAuthException(entityResult.getError());
          }
          throw new RuntimeException("Entity creation failed for '" + entityName + "': " + entityResult.getError());
        }

        createdEntities.add(entityName);
        log.info("[ScaffoldAppTool] ✅ Entity created: {}", entityName);
      }

      log.info("[ScaffoldAppTool] ✅ All {} entities created successfully", createdEntities.size());

      // Story 5: Phase 3 - Generate Pages
      List<String> createdPages = new ArrayList<>();
      if (!pages.isEmpty()) {
        log.info("[ScaffoldAppTool] Phase 3: Generating {} pages", pages.size());
        GeneratePageTool pageTool = new GeneratePageTool(validator, baseUrl);

        for (int i = 0; i < pages.size(); i++) {
          Map<String, Object> pageDef = pages.get(i);
          String pageName = (String) pageDef.get("name");

          log.info("[ScaffoldAppTool] Generating page {}/{}: {}", i + 1, pages.size(), pageName);

          // Inject appId into page definition
          pageDef.put("appId", createdAppId);

          // Inject entity fields so GeneratePageTool can create inputs and table columns
          String entityName = (String) pageDef.get("entityName");
          if (entityName != null) {
            // Find matching entity and pass its fields
            for (Map<String, Object> entity : entities) {
              if (entityName.equals(entity.get("name"))) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> fields = (List<Map<String, Object>>) entity.get("fields");
                pageDef.put("entityFields", fields);
                log.info("[ScaffoldAppTool] Injected {} fields for {} page", fields != null ? fields.size() : 0, pageDef.get("type"));
                break;
              }
            }
          }

          ToolResult pageResult = pageTool.execute(pageDef, context);
          if (!pageResult.isSuccess()) {
            log.error("[ScaffoldAppTool] Page generation failed for '{}': {}", pageName, pageResult.getError());
            if (pageResult.isAuthFailure()) {
              throw new BackendAuthException(pageResult.getError());
            }
            throw new RuntimeException("Page generation failed for '" + pageName + "': " + pageResult.getError());
          }

          createdPages.add(pageName);
          log.info("[ScaffoldAppTool] ✅ Page generated: {}", pageName);
        }

        log.info("[ScaffoldAppTool] ✅ All {} pages generated successfully", createdPages.size());
      } else {
        log.info("[ScaffoldAppTool] Phase 3: No pages to generate (optional)");
      }

      // Story 6: Phase 4 - Deploy App
      log.info("[ScaffoldAppTool] Phase 4: Deploying app to DEV environment");
      DeployAppTool deployTool = new DeployAppTool(baseUrl);

      Map<String, Object> deployArgs = new HashMap<>();
      deployArgs.put("appId", createdAppId);

      ToolResult deployResult = deployTool.execute(deployArgs, context);
      if (!deployResult.isSuccess()) {
        log.error("[ScaffoldAppTool] Deployment failed: {}", deployResult.getError());
        if (deployResult.isAuthFailure()) {
          throw new BackendAuthException(deployResult.getError());
        }
        throw new RuntimeException("Deployment failed: " + deployResult.getError());
      }

      @SuppressWarnings("unchecked")
      Map<String, Object> deployData = (Map<String, Object>) deployResult.getData();
      String testUrl = (String) deployData.get("testUrl");

      log.info("[ScaffoldAppTool] ✅ App deployed successfully");
      log.info("[ScaffoldAppTool] ═══════════════════════════════════════════════════════");
      log.info("[ScaffoldAppTool] 🎉 ONE-SHOT CREATION COMPLETE");
      log.info("[ScaffoldAppTool]   App: {}", appName);
      log.info("[ScaffoldAppTool]   App ID: {}", createdAppId);
      log.info("[ScaffoldAppTool]   Entities Created: {}", createdEntities);
      log.info("[ScaffoldAppTool]   Pages Created: {}", createdPages);
      log.info("[ScaffoldAppTool]   Test URL: {}", testUrl);
      log.info("[ScaffoldAppTool] ═══════════════════════════════════════════════════════");

      long executionTime = System.currentTimeMillis() - startTime;
      
      // Story 6: Consolidated Summary
      Map<String, Object> resultData = new HashMap<>();
      resultData.put("status", "deployed");
      resultData.put("appId", createdAppId);
      resultData.put("appName", appName);
      resultData.put("entitiesCreated", createdEntities);
      resultData.put("pagesCreated", createdPages);
      resultData.put("testUrl", testUrl);
      
      // Neutral summary allows the LLM to decide if it was a 'build' or 'update' based on context (Story 3.2 logic)
      resultData.put("summary", String.format(
          "Successfully processed '%s' with %d entities and %d pages. App is now deployed.",
          appName, createdEntities.size(), createdPages.size()));

      return ToolResult.success(getName(), resultData, executionTime);

    } catch (BackendAuthException authEx) {
      log.warn("[ScaffoldAppTool] {}", authEx.getMessage());
      // No rollback: an app-level 401 mid-scaffold means the token stopped being valid, not that
      // the app itself is bad. Deleting a half-built app the user can't currently re-authenticate
      // to fix would make recovery harder, not safer. Same reasoning as the existing "modifying an
      // EXISTING app" skip below -- App Versioning handles partial state.
      return ToolResult.authError(getName(), authEx.getMessage());
    } catch (Exception e) {
      log.error("[ScaffoldAppTool] Execution failed", e);

      // Story 3: Rollback mechanism - delete the app ONLY IF IT WAS CREATED in this session
      // Do NOT delete an existing app if we were just adding to it
      boolean isNewApp = !(context.appId() != null && !context.appId().isBlank() && !"default".equals(context.appId()));
      if (createdAppId != null && isNewApp) {
        log.warn("[ScaffoldAppTool] Rolling back - deleting NEW app: {}", createdAppId);
        try {
          rollback(createdAppId, context);
          log.info("[ScaffoldAppTool] Rollback successful");
        } catch (Exception rollbackError) {
          log.error("[ScaffoldAppTool] Rollback failed", rollbackError);
          // Continue to return the original error
        }
      } else if (createdAppId != null) {
        log.warn("[ScaffoldAppTool] Partial failure while modifying EXISTING app {}. Deletion rollback skipped safely since App Versioning handles it.", createdAppId);
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

    HttpRequest.Builder deleteBuilder = HttpRequest.newBuilder()
        .uri(URI.create(deleteUrl));

    // C4.4e -- was unconditional, so a blank token produced a literal "Bearer null" header and the
    // rollback 401'd, stranding the half-built app it was meant to remove. AgentContext now refuses
    // to hold a blank token, so this matches the other thirteen sites as defence in depth.
    if (context.token() != null && !context.token().isEmpty()) {
      deleteBuilder.header("Authorization", "Bearer " + context.token());
    }

    HttpRequest deleteRequest = deleteBuilder
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

  /**
   * C4.4e Review #12 -- returns true only if the backend already has an {@code appbana_apps} row
   * for {@code appId}. {@code GET .../apps/{appId}} 404s when it does not (see
   * {@code AppRoutes.java}); any other non-200 is treated the same as "can't confirm it exists",
   * which routes the caller to (re)create the row rather than silently building entities under an
   * id nothing backs.
   */
  private boolean appRowExists(String appId, String tenantId, AgentContext context) throws Exception {
    String url = String.format("%s/appbana-studio/%s/apps/%s", baseUrl, tenantId, appId);
    HttpRequest.Builder rb = HttpRequest.newBuilder().uri(URI.create(url)).header("Accept", "application/json");
    if (context.token() != null && !context.token().isEmpty()) {
      rb.header("Authorization", "Bearer " + context.token());
    }
    java.net.http.HttpResponse<String> response = httpClient.send(rb.GET().build(),
        java.net.http.HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() == 401) {
      throw new BackendAuthException(getName() + ": app-existence check for '" + appId + "' returned 401");
    }
    return response.statusCode() == 200;
  }

  /**
   * C4.4e Review #12 -- creates the missing {@code appbana_apps} row under the caller's SUPPLIED
   * id, instead of {@link CreateAppTool}'s normal behaviour of minting a fresh
   * {@code UUID.randomUUID()}. The entities this scaffold is about to create are keyed by this
   * exact id ({@code {tenantId}_{appId}_{entityName}}), so a fresh id here would just move the
   * orphan rather than remove it.
   */
  private void createAppRowWithId(String appId, String tenantId, String appName, String description,
      AgentContext context) throws Exception {
    Map<String, Object> appMeta = new HashMap<>();
    appMeta.put("id", appId);
    appMeta.put("name", appName);
    appMeta.put("description", description);
    appMeta.put("tenantId", tenantId);

    String url = String.format("%s/appbana-studio/%s/apps", baseUrl, tenantId);
    HttpRequest.Builder rb = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Content-Type", "application/json");
    if (context.token() != null && !context.token().isEmpty()) {
      rb.header("Authorization", "Bearer " + context.token());
    }

    java.net.http.HttpResponse<String> response = httpClient.send(
        rb.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(appMeta))).build(),
        java.net.http.HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() == 401) {
      throw new BackendAuthException(getName() + ": creating the missing app row for '" + appId + "' returned 401");
    }
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new Exception("Creating missing app row for '" + appId + "' failed: " + response.statusCode()
          + " - " + response.body());
    }
    log.info("[ScaffoldAppTool] Created missing appbana_apps row for existing id '{}'", appId);
  }
}
