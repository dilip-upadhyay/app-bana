package com.appbana.ai.agent.tool;

import com.appbana.ai.agent.AgentContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Tool for updating the currently-selected app's own metadata — its name,
 * description, or default page.
 *
 * <p>Distinct from {@code batch_update_entities}, which edits entity schemas
 * (tables). The LLM historically mis-used batch_update_entities with
 * entityName="App" to try to rename the app itself; this tool exists so it
 * has an obvious, correct call to make instead.
 */
@Slf4j
public class UpdateAppTool implements Tool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final HttpClient httpClient;
    private final String baseUrl;

    public UpdateAppTool(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public String getName() {
        return "update_app";
    }

    @Override
    public String getDescription() {
        return "Update the currently-selected app's own metadata (its name, description, or default page). "
                + "Use this when the user says things like \"rename my app to X\", \"change the app name\", "
                + "or \"update the app description\". Do NOT use batch_update_entities for renaming the app — "
                + "the app is not an entity.";
    }

    @Override
    public String getParameterSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "name": {
                  "type": "string",
                  "description": "New display name for the app (e.g. \\"SereneStays\\"). Omit to leave unchanged."
                },
                "description": {
                  "type": "string",
                  "description": "New app description. Omit to leave unchanged."
                },
                "defaultPage": {
                  "type": "string",
                  "description": "ID of the page that should open by default when users launch the app. Omit to leave unchanged."
                }
              }
            }
            """;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments, AgentContext context) {
        log.info("[UpdateAppTool] Executing with args: {}", arguments);

        String appId = context.appId();
        String tenantId = context.tenantId() != null ? context.tenantId() : "default";

        if (appId == null || appId.isBlank() || "default".equals(appId)) {
            return ToolResult.error(getName(),
                    "No app is currently selected. Ask the user to open or select an app first.");
        }

        Map<String, Object> body = new HashMap<>();
        Object name = arguments.get("name");
        Object description = arguments.get("description");
        Object defaultPage = arguments.get("defaultPage");
        if (name instanceof String s && !s.isBlank()) body.put("name", s);
        if (description instanceof String s && !s.isBlank()) body.put("description", s);
        if (defaultPage instanceof String s && !s.isBlank()) body.put("defaultPage", s);

        if (body.isEmpty()) {
            return ToolResult.error(getName(),
                    "Nothing to update. Provide at least one of: name, description, defaultPage.");
        }

        try {
            String url = String.format("%s/appbana-studio/%s/apps/%s", baseUrl, tenantId, appId);
            String bodyJson = MAPPER.writeValueAsString(body);

            HttpRequest.Builder rb = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .PUT(HttpRequest.BodyPublishers.ofString(bodyJson));
            if (context.token() != null && !context.token().isBlank()) {
                rb.header("Authorization", "Bearer " + context.token());
            }

            HttpResponse<String> resp = httpClient.send(rb.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                // Invalidate the cached entity summary so subsequent prompts pick up
                // any related changes (e.g. renamed app name in context).
                context.variables().remove("entity_summary");
                context.variables().remove("app_name");
                StringBuilder msg = new StringBuilder("Updated the app.");
                if (body.containsKey("name")) {
                    msg.append(" New name: \"").append(body.get("name")).append("\".");
                }
                if (body.containsKey("description")) {
                    msg.append(" Description updated.");
                }
                if (body.containsKey("defaultPage")) {
                    msg.append(" Default page updated.");
                }
                Map<String, Object> data = new HashMap<>();
                data.put("message", msg.toString());
                data.put("appId", appId);
                data.put("updated", body);
                return ToolResult.success(getName(), data, 0);
            }
            if (resp.statusCode() == 401) {
                throw new BackendAuthException(getName() + ": update_app returned 401");
            }
            return ToolResult.error(getName(),
                    "Update failed with status " + resp.statusCode() + ": " + resp.body());
        } catch (BackendAuthException authEx) {
            log.warn("[UpdateAppTool] {}", authEx.getMessage());
            return ToolResult.authError(getName(), authEx.getMessage());
        } catch (Exception e) {
            log.error("[UpdateAppTool] Execution failed", e);
            return ToolResult.error(getName(), "Failed to update app: " + e.getMessage());
        }
    }
}
