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

            // 1. Build page metadata based on type
            Map<String, Object> pageMetadata = switch (pageType) {
                case "list" -> buildListPage(arguments);
                case "form" -> buildFormPage(arguments);
                case "detail" -> buildDetailPage(arguments);
                default -> throw new IllegalArgumentException("Unknown page type: " + pageType);
            };

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

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
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
            } else {
                log.error("[GeneratePageTool] API error: {} - {}",
                        response.statusCode(), response.body());
                return ToolResult.error(getName(),
                        "API error: " + response.statusCode() + " - " + response.body());
            }

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
        tableNode.put("id", "table-" + UUID.randomUUID().toString().substring(0, 8));
        tableNode.put("type", "table");

        Map<String, Object> tableProps = new HashMap<>();
        tableProps.put("entity", entityName);
        tableNode.put("props", tableProps);

        nodes.add(tableNode);
        page.put("nodes", nodes);

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
        // Build nodes list: app-grid + containers + inputs
        List<Map<String, Object>> allNodes = new ArrayList<>();
        List<String> gridChildren = new ArrayList<>();

        // Create app-grid as root
        Map<String, Object> gridNode = new HashMap<>();
        String gridId = "app-grid";
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

            // Skip auto-increment/primary key fields
            if ("id".equalsIgnoreCase(fieldName) || "autoincrement".equals(fieldType) ||
                    "long".equals(fieldType) && fieldName.equals("id")) {
                continue;
            }

            // Create container
            String containerId = containerIndex == 0 ? "container" : "container-" + containerIndex;
            Map<String, Object> container = new HashMap<>();
            container.put("id", containerId);
            container.put("type", "container");

            Map<String, Object> containerProps = new HashMap<>();
            containerProps.put("className", "grid-cell");
            containerProps.put("slot", "cell-" + containerIndex);
            containerProps.put("style",
                    "min-height: 100px; padding: 0.5rem; display: flex; flex-direction: column; gap: 0.5rem;");
            containerProps.put("data-cell-index", String.valueOf(containerIndex));
            container.put("props", containerProps);

            // Create input
            String inputId = containerIndex == 0 ? "input" : "input-" + containerIndex;
            container.put("children", List.of(inputId));

            Map<String, Object> input = new HashMap<>();
            input.put("id", inputId);
            input.put("type", mapFieldToInputType(fieldType));

            Map<String, Object> inputProps = new HashMap<>();
            inputProps.put("entity", entityName);
            inputProps.put("field", fieldName);
            inputProps.put("name", fieldName);
            inputProps.put("label", fieldLabel);
            inputProps.put("className", "input");
            inputProps.put("marginBottom", "0");
            if (!"reference".equals(fieldType)) {
                inputProps.put("placeholder", "Enter " + fieldLabel.toLowerCase() + "...");
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
        saveContainerProps.put("className", "grid-cell");
        saveContainerProps.put("slot", "cell-" + containerIndex);
        saveContainerProps.put("style", "min-height: 100px; padding: 0.5rem; display: flex; align-items: center;");
        saveContainer.put("props", saveContainerProps);

        String saveButtonId = "save-btn";
        saveContainer.put("children", List.of(saveButtonId));

        Map<String, Object> saveButton = new HashMap<>();
        saveButton.put("id", saveButtonId);
        saveButton.put("type", "button");
        Map<String, Object> saveButtonProps = new HashMap<>();
        saveButtonProps.put("label", "Save");
        saveButtonProps.put("actionType", "save");
        saveButtonProps.put("entities", List.of(entityName));
        saveButtonProps.put("className", "button");
        saveButton.put("props", saveButtonProps);

        gridChildren.add(saveContainerId);
        allNodes.add(saveContainer);
        allNodes.add(saveButton);

        // Set grid children and add grid to start of nodes
        gridNode.put("children", gridChildren);
        allNodes.add(0, gridNode);

        page.put("nodes", allNodes);

        return page;
    }

    /**
     * Map field type to input component type
     */
    private String mapFieldToInputType(String fieldType) {
        return switch (fieldType.toLowerCase()) {
            case "date" -> "date";
            case "datetime", "timestamp" -> "datetime";
            case "reference" -> "reference";
            case "status" -> "select";
            case "boolean" -> "checkbox";
            case "longtext" -> "textarea";
            default -> "input";
        };
    }

    /**
     * Fetch entity fields from backend (simplified version - returns empty list for
     * now)
     * In production, this would make an HTTP call to fetch the entity schema
     */
    private List<Map<String, Object>> fetchEntityFields(String entityName) {
        // TODO: Fetch from backend API
        // For now, return empty list - the page will be created but empty
        // The UI should handle this gracefully
        log.warn("[GeneratePageTool] Entity field fetching not implemented - form will be empty");
        return new ArrayList<>();
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
