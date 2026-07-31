package com.appbana.ai.agent.tool;

import com.appbana.ai.agent.AgentContext;
import com.appbana.ai.knowledge.MetadataValidator;
import com.appbana.ai.knowledge.ValidationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

/**
 * Tool for generating pages in AppBana
 * Story 8.3: Essential Tools Implementation
 */
@Slf4j
public class GeneratePageTool implements Tool {

    private final MetadataValidator validator;
    private final HttpClient httpClient;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public GeneratePageTool(MetadataValidator validator, String baseUrl) {
        this.validator = validator;
        this.httpClient = HttpClient.newHttpClient();
        this.baseUrl = baseUrl;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getName() {
        return "generate_page";
    }

    @Override
    public String getDescription() {
        return "Generate a UI page for the application. " +
                "Use this to create list pages, form pages, or detail pages for entities.";
    }

    @Override
    public String getParameterSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "name": {
                      "type": "string",
                      "description": "Page name (e.g., 'CustomerList', 'AddCustomer')"
                    },
                    "path": {
                      "type": "string",
                      "description": "Page URL path (e.g., '/customers', '/customers/new')"
                    },
                    "type": {
                      "type": "string",
                      "enum": ["list", "form", "detail"],
                      "description": "Page type"
                    },
                    "entityName": {
                      "type": "string",
                      "description": "Entity this page is for (e.g., 'Customer')"
                    },
                    "appId": {
                      "type": "string",
                      "description": "Target App ID. If not provided, uses current context."
                    },
                    "onSuccess": {
                      "type": "string",
                      "enum": ["navigate", "refresh"],
                      "description": "Action to take after successful save"
                    },
                    "navigateUrl": {
                      "type": "string",
                      "description": "URL to navigate to after success (can use {{id}})"
                    },
                    "fixedFields": {
                      "type": "object",
                      "description": "Static field values to merge into the save request (e.g. { 'Cart': { 'qty': 1 } })"
                    },
                    "layout": {
                      "type": "string",
                      "enum": ["form", "list", "detail", "wizard"],
                      "description": "Phase B1 — Optional compound layout. Use 'wizard' with `steps[]` for multi-step forms (onboarding, KYC, long signups). Omit for a flat single-page form."
                    },
                    "steps": {
                      "type": "array",
                      "description": "Phase B1 — Wizard step definitions. Required when layout='wizard'. Each step lists the field names to render in that step; the runtime auto-appends a Review & Submit step.",
                      "items": {
                        "type": "object",
                        "properties": {
                          "id":       { "type": "string", "description": "Stable step id, e.g. 'personal-info'" },
                          "title":    { "type": "string", "description": "Short step title shown in the progress bar" },
                          "subtitle": { "type": "string", "description": "Optional one-line subtitle under the title" },
                          "fields":   { "type": "array", "items": { "type": "string" }, "description": "Field names (from the entity schema) rendered in this step" }
                        },
                        "required": ["id", "title", "fields"]
                      }
                    },
                    "filters": {
                      "type": "array",
                      "description": "Phase B5 — Filter chip definitions for list pages. Each entry becomes a FilterBar chip. Use for lists with multi-dimension slicing (status, owner, date range).",
                      "items": {
                        "type": "object",
                        "properties": {
                          "field":   { "type": "string" },
                          "op":      { "type": "string", "enum": ["equals", "in", "range", "contains", "dateRange"] },
                          "label":   { "type": "string" },
                          "default": {}
                        },
                        "required": ["field", "op", "label"]
                      }
                    },
                    "groupBy": {
                      "type": "string",
                      "description": "Phase B5 — When set, the list page renders rows grouped by this field (client-side bucketing). Use for kanban-style views (group orders by status, tasks by assignee)."
                    },
                    "defaultSort": {
                      "type": "object",
                      "description": "Phase B5 — Default ORDER BY for the list. { field, direction: 'asc'|'desc' }",
                      "properties": {
                        "field":     { "type": "string" },
                        "direction": { "type": "string", "enum": ["asc", "desc"] }
                      },
                      "required": ["field", "direction"]
                    },
                    "aggregates": {
                      "type": "array",
                      "description": "Phase B5 — Footer aggregates (sum/avg/count/min/max) rendered below the table. Use when the user asks for totals ('show total revenue').",
                      "items": {
                        "type": "object",
                        "properties": {
                          "field": { "type": "string" },
                          "agg":   { "type": "string", "enum": ["sum", "avg", "count", "min", "max"] },
                          "label": { "type": "string" }
                        },
                        "required": ["field", "agg"]
                      }
                    }
                  },
                  "required": ["name", "path", "type", "entityName"]
                }
                """;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments, AgentContext context) {
        long startTime = System.currentTimeMillis();

        try {
            log.info("[GeneratePageTool] Generating page with args: {}", arguments);

            String pageType = (String) arguments.get("type");

            // GeneratePageTool referenceEntity/empty-fields bug (2nd half of the fix) --
            // ScaffoldAppTool injects `entityFields` when it calls this tool during initial
            // app creation, but the LLM can (and does) call generate_page directly with just
            // {name, path, type, entityName, appId} -- e.g. any "regenerate this page" request
            // after the app already exists. buildListPage/buildFormPage silently produced an
            // empty fields array whenever entityFields was missing (fetchEntityFields() was a
            // stub that always returned an empty list), so every ad-hoc regeneration reset the
            // page to zero columns regardless of the schema. Fetch the entity's fields from the
            // backend ourselves whenever the caller didn't supply them, instead of trusting the
            // LLM to always pass entityFields through.
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> suppliedFields = (List<Map<String, Object>>) arguments.get("entityFields");
            if ((suppliedFields == null || suppliedFields.isEmpty()) && arguments.get("entityName") != null) {
                String entityNameForFetch = (String) arguments.get("entityName");
                String appIdForFetch = (String) arguments.get("appId");
                if (appIdForFetch == null || appIdForFetch.isEmpty()) {
                    appIdForFetch = context.appId();
                }
                List<Map<String, Object>> fetched = fetchEntityFields(entityNameForFetch, appIdForFetch, context);
                if (!fetched.isEmpty()) {
                    arguments = new HashMap<>(arguments);
                    arguments.put("entityFields", fetched);
                    log.info("[GeneratePageTool] Fetched {} fields for entity '{}' from backend (caller did not supply entityFields)",
                            fetched.size(), entityNameForFetch);
                }
            }

            // 1. Build page metadata based on type
            Map<String, Object> pageMetadata = switch (pageType) {
                case "list" -> buildListPage(arguments);
                case "form" -> buildFormPage(arguments);
                case "detail" -> buildDetailPage(arguments);
                default -> throw new IllegalArgumentException("Unknown page type: " + pageType);
            };

            // Sprint 3 post-review fix — inject `kind` and `entityKey` on every
            // generated page so the runtime DetailPage / classifier can trust
            // metadata over name-sniffing. Without these, DetailPage.tsx sees
            // page.entityKey === '' and silently short-circuits into an empty
            // overlay on every AI-generated app.
            String tenantForKey = context.tenantId();
            if (tenantForKey == null || tenantForKey.isEmpty()) {
                tenantForKey = "default";
            }
            String appIdForKey = (String) arguments.get("appId");
            if (appIdForKey == null || appIdForKey.isEmpty()) {
                appIdForKey = context.appId();
            }
            Object entityForKey = arguments.get("entityName");
            pageMetadata.put("kind", pageType);
            if (entityForKey != null && appIdForKey != null && !appIdForKey.isEmpty()) {
                pageMetadata.put("entityKey", tenantForKey + "_" + appIdForKey + "_" + entityForKey);
            }

            // Phase B1 — Wizard layout. When the caller specifies
            // layout='wizard' + steps[], stamp them onto the PageMeta so the
            // runtime WizardShell takes over rendering. Requires the underlying
            // form page to have been built (so form-field nodes exist that the
            // wizard can then partition by step).
            Object layoutArg = arguments.get("layout");
            Object stepsArg  = arguments.get("steps");
            if ("wizard".equals(layoutArg) && stepsArg instanceof List<?> stepsList && !stepsList.isEmpty()) {
                if (!"form".equals(pageType)) {
                    log.warn("[GeneratePageTool] layout='wizard' requires type='form'; ignoring wizard on {} page", pageType);
                } else {
                    pageMetadata.put("layout", "wizard");
                    pageMetadata.put("steps", stepsList);
                    log.info("[GeneratePageTool] Wizard layout applied with {} steps", stepsList.size());
                }
            }

            // 2. Validate metadata
            ValidationResult validation = validator.validatePage(pageMetadata);

            if (!validation.isValid()) {
                log.warn("[GeneratePageTool] Validation failed: {}", validation.getDetailedErrors());

                // Try auto-fix
                pageMetadata = validator.autoFix(pageMetadata, validation);

                // Re-validate
                validation = validator.validatePage(pageMetadata);
                if (!validation.isValid()) {
                    return ToolResult.error(getName(),
                            "Page validation failed: " + validation.getDetailedErrors());
                }

                log.info("[GeneratePageTool] Auto-fix applied successfully");
            }

            // 3. Call backend API
            String tenantId = context.tenantId();

            // Prefer appId from args, fallback to context
            String appId = (String) arguments.get("appId");
            if (appId == null || appId.isEmpty()) {
                appId = context.appId();
            }

            String pageId = (String) pageMetadata.get("id");

            String url = String.format("%s/appbana-studio/%s/apps/%s/pages/%s",
                    baseUrl, tenantId, appId, pageId);

            String jsonBody = objectMapper.writeValueAsString(pageMetadata);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json");

            // C4.4c — this tool was the only writer in the scaffold chain that never sent the
            // caller's session token, so CreateAppTool and CreateEntityTool would succeed and page
            // generation would then 401, leaving a half-built app: entities and tables created,
            // no pages, and a rollback triggered by an auth error rather than a modelling one.
            String token = context.token();
            if (token != null && !token.isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + token);
            }

            HttpRequest request = requestBuilder
                    .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            long executionTime = System.currentTimeMillis() - startTime;

            // 4. Handle response
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("[GeneratePageTool] Page created successfully: {}", arguments.get("name"));

                Map<String, Object> result = new HashMap<>();
                result.put("pageName", arguments.get("name"));
                result.put("pageId", pageId);
                result.put("path", arguments.get("path"));
                result.put("type", pageType);
                result.put("status", "created");

                return ToolResult.success(getName(), result, executionTime);
            } else if (response.statusCode() == 401) {
                throw new BackendAuthException(getName() + ": generate_page returned 401");
            } else {
                log.error("[GeneratePageTool] API error: {} - {}",
                        response.statusCode(), response.body());
                return ToolResult.error(getName(),
                        "API error: " + response.statusCode() + " - " + response.body());
            }

        } catch (BackendAuthException authEx) {
            log.warn("[GeneratePageTool] {}", authEx.getMessage());
            return ToolResult.authError(getName(), authEx.getMessage());
        } catch (Exception e) {
            log.error("[GeneratePageTool] Execution failed", e);
            return ToolResult.error(getName(), "Execution error: " + e.getMessage());
        }
    }

    /**
     * Build list page metadata
     */
    private Map<String, Object> buildListPage(Map<String, Object> arguments) {
        String name = (String) arguments.get("name");
        String path = (String) arguments.get("path");
        String entityName = (String) arguments.get("entityName");

        Map<String, Object> page = new HashMap<>();
        page.put("id", generatePageId(name));
        page.put("name", name);
        page.put("path", path);
        page.put("rootId", "root");

        // Create table component
        List<Map<String, Object>> nodes = new ArrayList<>();

        Map<String, Object> tableNode = new HashMap<>();
        tableNode.put("id", "root");
        tableNode.put("type", "table");

        Map<String, Object> tableProps = new HashMap<>();
        tableProps.put("entity", entityName);

        // Fetch entity schema to get fields (from arguments if provided)
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entityFields = (List<Map<String, Object>>) arguments.get("entityFields");
        if (entityFields != null && !entityFields.isEmpty()) {
            List<Map<String, Object>> tableFields = new ArrayList<>();
            for (Map<String, Object> field : entityFields) {
                Map<String, Object> f = new HashMap<>();
                f.put("name", field.get("name"));
                f.put("label", field.getOrDefault("label", field.get("name")));
                f.put("type", field.get("type"));
                // StudioTableLive's FK-label lookup reads props.fields[].referenceEntity
                // to know which entity to fetch for resolving a reference column's human
                // label (e.g. Employee.department -> "Human Resources" instead of raw id
                // "1"). Without this, every reference-type column in a generated list page
                // silently falls back to rendering the raw FK id, with no error anywhere.
                Object referenceEntity = field.get("referenceEntity");
                if (referenceEntity != null && !"null".equals(referenceEntity)) {
                    f.put("referenceEntity", referenceEntity);
                }
                tableFields.add(f);
            }
            tableProps.put("fields", tableFields);
        } else {
            tableProps.put("fields", new ArrayList<>());
            log.warn("[GeneratePageTool] No entity fields provided for list page - columns will be empty");
        }

        tableNode.put("props", tableProps);

        nodes.add(tableNode);
        page.put("nodes", nodes);

        // Phase B5 — propagate list-page metadata (filters / groupBy /
        // defaultSort / aggregates / savedViews). These are declarative
        // extras the runtime primitives (FilterBar, SavedViewsBar,
        // StudioTableLive group rendering) know how to consume.
        Object filters = arguments.get("filters");
        if (filters instanceof List<?> fl && !fl.isEmpty()) {
            tableProps.put("filters", fl);
            page.put("filters", fl);
        }
        Object groupBy = arguments.get("groupBy");
        if (groupBy instanceof String gb && !gb.isBlank()) {
            tableProps.put("groupBy", gb);
            page.put("groupBy", gb);
        }
        Object defaultSort = arguments.get("defaultSort");
        if (defaultSort instanceof Map<?, ?> ds && !ds.isEmpty()) {
            tableProps.put("defaultSort", ds);
            page.put("defaultSort", ds);
        }
        Object aggregates = arguments.get("aggregates");
        if (aggregates instanceof List<?> al && !al.isEmpty()) {
            tableProps.put("aggregates", al);
            page.put("aggregates", al);
        }

        return page;
    }

    /**
     * Build form page metadata
     */
    private Map<String, Object> buildFormPage(Map<String, Object> arguments) {
        String name = (String) arguments.get("name");
        String path = (String) arguments.get("path");
        String entityName = (String) arguments.get("entityName");
        String appId = (String) arguments.get("appId");

        Map<String, Object> page = new HashMap<>();
        page.put("id", generatePageId(name));
        page.put("name", name);
        page.put("path", path);
        page.put("rootId", "root");
        page.put("metaVersion", 1);

        // Fetch entity schema to get fields (from arguments if provided)
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entityFields = (List<Map<String, Object>>) arguments.get("entityFields");
        if (entityFields == null) {
            entityFields = new ArrayList<>();
            log.warn("[GeneratePageTool] No entity fields provided for form page - form will be empty");
        }
        // Build nodes list
        List<Map<String, Object>> allNodes = new ArrayList<>();
        List<String> gridChildren = new ArrayList<>();

        // 1. Create appbana-form as root
        Map<String, Object> formNode = new HashMap<>();
        String formId = "root";
        formNode.put("id", formId);
        formNode.put("type", "form"); // Standard form container
        Map<String, Object> formProps = new HashMap<>();
        formProps.put("entity", entityName);
        formNode.put("props", formProps);
        formNode.put("children", List.of("grid"));

        // 2. Create app-grid
        Map<String, Object> gridNode = new HashMap<>();
        String gridId = "grid";
        gridNode.put("id", gridId);
        gridNode.put("type", "app-grid");

        Map<String, Object> gridProps = new HashMap<>();
        gridProps.put("cols", 3);
        gridProps.put("rows", Math.max(1, (entityFields.size() + 2) / 3)); // Auto-calculate rows
        gridProps.put("gap", "0");
        gridProps.put("className", "grid");
        gridProps.put("minCellHeight", "auto");
        gridProps.put("marginBottom", "0");
        gridNode.put("props", gridProps);

        // Generate container + input pairs for each field
        int containerIndex = 0;
        for (Map<String, Object> field : entityFields) {
            String fieldName = (String) field.get("name");
            String fieldType = (String) field.get("type");
            String fieldLabel = (String) field.getOrDefault("label", fieldName);

            // Skip auto-increment/primary key fields and audit fields
            if ("id".equalsIgnoreCase(fieldName) || "autoincrement".equals(fieldType) ||
                    "long".equals(fieldType) && fieldName.equals("id") ||
                    "created_at".equalsIgnoreCase(fieldName) || "updated_at".equalsIgnoreCase(fieldName)) {
                continue;
            }

            // Create container
            String containerId = "container-" + containerIndex;
            Map<String, Object> container = new HashMap<>();
            container.put("id", containerId);
            container.put("type", "container");

            Map<String, Object> containerProps = new HashMap<>();
            containerProps.put("className", "appbana-form-cell");
            containerProps.put("slot", "cell-" + containerIndex);
            containerProps.put("data-cell-index", String.valueOf(containerIndex));
            container.put("props", containerProps);

            // Create input
            String inputId = "input-" + containerIndex;
            container.put("children", List.of(inputId));

            Map<String, Object> input = new HashMap<>();
            input.put("id", inputId);
            // Emit a top-level `select` node for status fields so the runtime's
            // <select> renderer uses the options[] directly instead of routing
            // through the `<input type="select">` fallback (broken HTML).
            String topLevelNodeType = "status".equals(fieldType) ? "select" : "input";
            input.put("type", topLevelNodeType);

            Map<String, Object> inputProps = new HashMap<>();
            inputProps.put("entity", entityName);
            inputProps.put("field", fieldName);
            inputProps.put("name", fieldName);
            inputProps.put("label", fieldLabel);
            inputProps.put("type", mapFieldToInputType(fieldType));
            inputProps.put("className", "input");
            if ("status".equals(fieldType)) {
                // Copy status options through so the runtime <select> can populate.
                // SchemaEnricher guarantees a non-empty options[] at the metadata boundary.
                Object statusOptions = field.get("options");
                if (statusOptions instanceof List<?> list && !list.isEmpty()) {
                    inputProps.put("options", list);
                }
                if (Boolean.TRUE.equals(field.get("required"))) {
                    inputProps.put("required", true);
                }
            } else if (!"reference".equals(fieldType)) {
                inputProps.put("placeholder", "Enter " + fieldLabel.toLowerCase() + "...");
            } else {
                // Propagate the referenced entity name so the runtime ReferenceField
                // knows which table to query. Without this, the runtime falls back to
                // the field name, which only works when the FK column happens to share
                // its name with the referenced entity (e.g. "customer" → Customer entity).
                // Aliased FKs like "owner" → User or "assignee" → Employee would break.
                Object refEntity = field.get("referenceEntity");
                if (refEntity != null) {
                    inputProps.put("referenceEntity", refEntity);
                }
            }
            // Phase B2 — propagate optional conditional-visibility metadata onto
            // the runtime node.props so <ConditionalField> can honor showWhen /
            // requiredWhen / disabledWhen expressions at render time.
            Object conditions = field.get("conditions");
            if (conditions instanceof Map<?, ?> conditionsMap && !conditionsMap.isEmpty()) {
                inputProps.put("conditions", conditionsMap);
            }
            // Phase B3 — propagate per-field file constraints (maxSizeBytes,
            // acceptedMimeTypes) so FileUploadField can enforce them client-side.
            Object fileConstraints = field.get("fileConstraints");
            if (fileConstraints instanceof Map<?, ?> fcMap && !fcMap.isEmpty()) {
                inputProps.put("fileConstraints", fcMap);
            }
            input.put("props", inputProps);

            gridChildren.add(containerId);
            allNodes.add(container);
            allNodes.add(input);
            containerIndex++;
        }

        // Add save button container
        String saveContainerId = "container-save";
        Map<String, Object> saveContainer = new HashMap<>();
        saveContainer.put("id", saveContainerId);
        saveContainer.put("type", "container");
        Map<String, Object> saveContainerProps = new HashMap<>();
        saveContainerProps.put("className", "appbana-form-save-cell");
        saveContainerProps.put("slot", "cell-" + containerIndex);
        saveContainer.put("props", saveContainerProps);

        String saveButtonId = "save-btn";
        saveContainer.put("children", List.of(saveButtonId));

        Map<String, Object> saveButton = new HashMap<>();
        saveButton.put("id", saveButtonId);
        saveButton.put("type", "button");
        Map<String, Object> saveButtonProps = new HashMap<>();
        saveButtonProps.put("label", "Save");
        saveButtonProps.put("type", "submit"); // ESSENTIAL for FormContainer
        saveButtonProps.put("actionType", "save-entity");
        saveButtonProps.put("entities", List.of(entityName));
        saveButtonProps.put("className", "button");
        
        // GENERIC FEATURE: Pass through workflow props from AI if provided
        if (arguments.containsKey("onSuccess")) {
            saveButtonProps.put("onSuccess", arguments.get("onSuccess"));
        }
        if (arguments.containsKey("navigateUrl")) {
            saveButtonProps.put("navigateUrl", arguments.get("navigateUrl"));
        }
        if (arguments.containsKey("fixedFields")) {
            saveButtonProps.put("fixedFields", arguments.get("fixedFields"));
        }

        saveButton.put("props", saveButtonProps);

        gridChildren.add(saveContainerId);
        allNodes.add(saveContainer);
        allNodes.add(saveButton);

        // Finalize assembly
        gridNode.put("children", gridChildren);
        allNodes.add(0, gridNode);
        allNodes.add(0, formNode);

        page.put("nodes", allNodes);

        return page;
    }

    /**
     * Map field type to input component type
     */
    private String mapFieldToInputType(String fieldType) {
        return switch (fieldType.toLowerCase()) {
            case "date" -> "date";
            case "datetime", "timestamp" -> "datetime-local";
            case "reference" -> "reference";
            case "status" -> "select";
            case "boolean" -> "checkbox";
            case "longtext" -> "textarea";
            case "file" -> "file";
            default -> "input";
        };
    }

    /**
     * Fetch entity fields from the backend app-context endpoint when the caller
     * (the LLM) did not supply `entityFields` directly. Mirrors the lookup
     * GetEntityDetailsTool uses: GET /appbana-studio/{tenantId}/apps/{appId}
     * and match the entity by name within its `entities[]` list. Returns an
     * empty list (never throws) if the app/entity can't be resolved so the
     * page is still created -- just without columns, same as the prior
     * behavior -- rather than failing the whole generate_page call.
     */
    private List<Map<String, Object>> fetchEntityFields(String entityName, String appId, AgentContext context) {
        if (entityName == null || entityName.isBlank() || appId == null || appId.isBlank()) {
            return new ArrayList<>();
        }
        try {
            String tenantId = context.tenantId();
            if (tenantId == null || tenantId.isEmpty()) {
                tenantId = "default";
            }
            String url = baseUrl + "/appbana-studio/" + tenantId + "/apps/" + appId;

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json");
            String token = context.token();
            if (token != null && !token.isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + token);
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 401) {
                throw new BackendAuthException(getName() + ": entity-fields lookup for '"
                        + entityName + "' returned 401");
            }

            if (response.statusCode() != 200) {
                log.warn("[GeneratePageTool] Could not fetch entity fields for '{}' in app '{}': backend returned {}",
                        entityName, appId, response.statusCode());
                return new ArrayList<>();
            }

            Map<String, Object> appData = objectMapper.readValue(response.body(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                    });

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entities = (List<Map<String, Object>>) appData.get("entities");
            if (entities == null) {
                return new ArrayList<>();
            }

            for (Map<String, Object> entity : entities) {
                if (entityName.equalsIgnoreCase((String) entity.get("name"))) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> fields = (List<Map<String, Object>>) entity.get("fields");
                    return fields != null ? fields : new ArrayList<>();
                }
            }
            return new ArrayList<>();
        } catch (BackendAuthException authEx) {
            throw authEx;
        } catch (Exception e) {
            log.warn("[GeneratePageTool] Failed to fetch entity fields for '{}': {}", entityName, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Build detail page metadata
     */
    private Map<String, Object> buildDetailPage(Map<String, Object> arguments) {
        // For now, detail page is similar to form but read-only
        return buildFormPage(arguments);
    }

    /**
     * Generate a page ID from name
     */
    private String generatePageId(String name) {
        return name.toLowerCase()
                .replaceAll("\\s+", "-")
                .replaceAll("[^a-z0-9-]", "");
    }
}
