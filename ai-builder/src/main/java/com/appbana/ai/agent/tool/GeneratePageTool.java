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

        Map<String, Object> page = new HashMap<>();
        page.put("id", generatePageId(name));
        page.put("name", name);
        page.put("path", path);
        page.put("rootId", "root");

        // Create app-grid container with input components
        List<Map<String, Object>> nodes = new ArrayList<>();

        Map<String, Object> gridNode = new HashMap<>();
        gridNode.put("id", "grid-" + UUID.randomUUID().toString().substring(0, 8));
        gridNode.put("type", "app-grid");
        gridNode.put("children", new ArrayList<>());

        nodes.add(gridNode);

        // Add save button
        Map<String, Object> buttonNode = new HashMap<>();
        buttonNode.put("id", "save-btn-" + UUID.randomUUID().toString().substring(0, 8));
        buttonNode.put("type", "button");

        Map<String, Object> buttonProps = new HashMap<>();
        buttonProps.put("label", "Save");
        buttonProps.put("actionType", "save");
        buttonProps.put("entities", List.of(entityName));
        buttonNode.put("props", buttonProps);

        nodes.add(buttonNode);

        page.put("nodes", nodes);

        return page;
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
