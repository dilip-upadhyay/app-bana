package com.appbana.ai.agent.tool;

import com.appbana.ai.agent.AgentContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool for inserting generated mock data into an entity
 */
@Slf4j
public class GenerateMockDataTool implements Tool {

    private final HttpClient httpClient;
    private final String baseUrl;

    public GenerateMockDataTool(String baseUrl) {
        this.httpClient = HttpClient.newHttpClient();
        this.baseUrl = baseUrl;
    }

    @Override
    public String getName() {
        return "generate_mock_data";
    }

    @Override
    public String getDescription() {
        return "Inserts mock data records into an existing entity. Use this when the user asks to seed or create test data. Provide an array of realistic mock JSON objects (up to 10-20 max). Do NOT use fake field names; use exact field names defined in the entity. IMPORTANT: When seeding data for an app you just scaffolded, always pass the 'appId' returned from scaffold_app — otherwise the request will 404. "
                + "CRITICAL ordering rule for reference fields: a field of type 'reference' (e.g. Employee.department, Employee.manager, EquipmentRequest.employee) stores the referenced row's real integer primary key — never a name or free-text label. You MUST call this tool once per entity in dependency order: seed entities with no reference fields (or only self-references) first, read the inserted records' real 'id' values back from this tool's own JSON result, and only then seed dependent entities using those real ids for their reference fields. For a self-referencing field (e.g. Employee.manager referencing Employee itself), seed a first batch without that field (or null it), then use the returned ids for later records or a follow-up update. Never invent an id or pass a department/employee name into a reference field — that fails validation.";
    }

    @Override
    public String getParameterSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "entityName": {
                      "type": "string",
                      "description": "Entity name (e.g., 'Customer', 'Product'). Do NOT include the tenant/app prefix — that is added automatically."
                    },
                    "appId": {
                      "type": "string",
                      "description": "Target App ID (the appId returned from scaffold_app / create_app). If omitted, falls back to the current session's active app. Required when seeding into an app that was just created in the same turn."
                    },
                    "records": {
                      "type": "array",
                      "description": "An array of up to 20 realistic JSON objects representing the mock records to insert.",
                      "items": {
                        "type": "object",
                        "additionalProperties": true
                      }
                    }
                  },
                  "required": ["entityName", "records"]
                }
                """;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments, AgentContext context) {
        long startTime = System.currentTimeMillis();

        String entityName = (String) arguments.get("entityName");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) arguments.get("records");

        if (entityName == null || entityName.isBlank()) {
            return ToolResult.error(getName(), "Entity name is required.");
        }

        if (records == null || records.isEmpty()) {
            return ToolResult.error(getName(), "Records array cannot be empty.");
        }

        if (records.size() > 50) {
            log.warn("[GenerateMockDataTool] Limiting inserted records to 50 (asked for {}) to prevent overload.", records.size());
            records = records.subList(0, 50);
        }

        try {
            log.info("[GenerateMockDataTool] Inserting {} mock records into entity: {}", records.size(), entityName);

            // Prefer explicit appId from the LLM's tool arguments (works even for
            // apps created earlier in the same conversation turn, before the
            // per-session AgentContext has been re-hydrated). Fall back to the
            // session's active app when the LLM did not pass one.
            String appId = (String) arguments.get("appId");
            if (appId == null || appId.isBlank()) {
                appId = context.appId();
            }
            String tenantId = context.tenantId();

            String targetSchemaId = entityName;
            if (appId != null && !appId.equals("default") && !appId.isEmpty()) {
                String tenantPart = (tenantId != null && !tenantId.isEmpty()) ? tenantId : "default";
                if (!entityName.startsWith(tenantPart + "_")) {
                    targetSchemaId = tenantPart + "_" + appId + "_" + entityName;
                }
            }

            String url = String.format("%s/api/%s/batch", baseUrl, targetSchemaId);
            String token = context.token();

            String jsonBody = new ObjectMapper().writeValueAsString(records);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json");

            if (token != null && !token.isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + token);
            }

            HttpResponse<String> response = httpClient.send(
                    requestBuilder.POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build(),
                    HttpResponse.BodyHandlers.ofString());

            long executionTime = System.currentTimeMillis() - startTime;

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("[GenerateMockDataTool] Successfully inserted data into {}", entityName);
                
                Map<String, Object> result = new HashMap<>();
                result.put("entityName", entityName);
                result.put("recordsInserted", records.size());
                result.put("status", "success");
                result.put("details", response.body()); // Optionally include the backend response
                
                return ToolResult.success(getName(), result, executionTime);
            } else if (response.statusCode() == 401) {
                throw new BackendAuthException(getName() + ": generate_mock_data returned 401");
            } else {
                log.error("[GenerateMockDataTool] API error: {} - {}", response.statusCode(), response.body());
                return ToolResult.error(getName(),
                        "API error: " + response.statusCode() + " - " + response.body());
            }

        } catch (BackendAuthException authEx) {
            log.warn("[GenerateMockDataTool] {}", authEx.getMessage());
            return ToolResult.authError(getName(), authEx.getMessage());
        } catch (Exception e) {
            log.error("[GenerateMockDataTool] Execution failed", e);
            return ToolResult.error(getName(), "Execution error: " + e.getMessage());
        }
    }
}
