package com.appbana.ai.agent.tool;

import com.appbana.ai.agent.AgentContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

@Slf4j
public class DeployAppTool implements Tool {

    private final String backendUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DeployAppTool(String backendUrl) {
        this.backendUrl = backendUrl;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public String getName() {
        return "deploy_app";
    }

    @Override
    public String getDescription() {
        return "Deploys the current application to the DEV environment. Use this when the user is satisfied with the app and asks to 'deploy', 'publish', or 'test' it. Returns the test URL.";
    }

    @Override
    public String getParameterSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "appId": {
                      "type": "string",
                      "description": "The ID of the application to deploy. If not provided, uses the current context app ID."
                    }
                  }
                }
                """;
    }

    @Override
    public ToolResult execute(Map<String, Object> args, AgentContext context) {
        long startTime = System.currentTimeMillis();
        String tenantId = context.tenantId();

        // Prefer appId from args, fallback to context
        String appId = (String) args.get("appId");
        if (appId == null || appId.isEmpty()) {
            appId = context.appId();
        }

        String token = context.token();

        if (appId == null || appId.equals("default") || appId.isEmpty()) {
            return ToolResult.error(getName(),
                    "No active application found to deploy. Please create an app first or provide an appId.");
        }

        try {
            // Step 1: Fetch full App Metadata
            String fetchUrl = String.format("%s/appbana-studio/%s/apps/%s", backendUrl, tenantId, appId);
            log.info("Fetching app metadata from: {}", fetchUrl);

            HttpRequest fetchReq = HttpRequest.newBuilder()
                    .uri(URI.create(fetchUrl))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();

            HttpResponse<String> fetchRes = httpClient.send(fetchReq, HttpResponse.BodyHandlers.ofString());

            if (fetchRes.statusCode() != 200) {
                return ToolResult.error(getName(), "Failed to fetch app metadata: " + fetchRes.body());
            }

            String appMetaJson = fetchRes.body();
            Map<String, Object> appData = objectMapper.readValue(appMetaJson, Map.class);

            // Hydrate entities from schemas if missing
            if (!appData.containsKey("entities") || ((java.util.List) appData.get("entities")).isEmpty()) {
                if (appData.containsKey("schemas")) {
                    java.util.List<String> schemaNames = (java.util.List<String>) appData.get("schemas");
                    java.util.List<Map<String, Object>> hydratedEntities = new java.util.ArrayList<>();

                    log.info("Hydrating {} entities from schemas list...", schemaNames.size());

                    for (String schemaName : schemaNames) {
                        String schemaUrl = String.format("%s/schema/%s", backendUrl, schemaName);
                        HttpRequest schemaReq = HttpRequest.newBuilder()
                                .uri(URI.create(schemaUrl))
                                .header("Authorization", "Bearer " + token)
                                .GET()
                                .build();

                        HttpResponse<String> schemaRes = httpClient.send(schemaReq,
                                HttpResponse.BodyHandlers.ofString());
                        if (schemaRes.statusCode() == 200) {
                            Map<String, Object> schemaObj = objectMapper.readValue(schemaRes.body(), Map.class);

                            // Sanitize and Apply Mandatory Defaults
                            if (schemaObj.containsKey("fields") && schemaObj.get("fields") instanceof java.util.List) {
                                java.util.List<Map<String, Object>> fields = (java.util.List<Map<String, Object>>) schemaObj
                                        .get("fields");
                                for (Map<String, Object> field : fields) {
                                    String name = (String) field.get("name");

                                    // 1. Length (Mandatory, default 255)
                                    Object lenObj = field.get("length");
                                    int length = 255;
                                    if (lenObj instanceof Number) {
                                        length = ((Number) lenObj).intValue();
                                    } else if (lenObj instanceof String) {
                                        try {
                                            length = Integer.parseInt((String) lenObj);
                                        } catch (Exception ignored) {
                                        }
                                    }
                                    if (length <= 0)
                                        length = 255;
                                    field.put("length", length);

                                    // 2. Boolean Flags (Mandatory not null)
                                    if (field.get("primaryKey") == null)
                                        field.put("primaryKey", false);
                                    if (field.get("autoIncrement") == null)
                                        field.put("autoIncrement", false);
                                    if (field.get("required") == null)
                                        field.put("required", false);

                                    // 3. Label (Mandatory)
                                    if (field.get("label") == null && name != null && !name.isEmpty()) {
                                        String label = name.substring(0, 1).toUpperCase()
                                                + name.substring(1).replaceAll("([A-Z])", " $1").trim();
                                        field.put("label", label);
                                    }
                                }
                            }

                            hydratedEntities.add(schemaObj);
                        } else {
                            log.warn("Failed to hydration schema {}: {}", schemaName, schemaRes.statusCode());
                        }
                    }

                    if (hydratedEntities.isEmpty()) {
                        log.warn("No entities hydrated for app {}. Schemas list was: {}", appId, schemaNames);
                    } else {
                        try {
                            log.info("Hydrated entities JSON: {}", objectMapper.writeValueAsString(hydratedEntities));
                        } catch (Exception e) {
                            log.error("Failed to log entities", e);
                        }
                    }

                    appData.put("entities", hydratedEntities);
                    // Update JSON for publishing
                    appMetaJson = objectMapper.writeValueAsString(appData);
                }
            }

            // Step 2: Publish App
            String publishUrl = String.format("%s/api/%s/apps/%s/publish?env=DEV", backendUrl, tenantId, appId);
            log.info("Publishing app to: {}", publishUrl);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(publishUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(appMetaJson));

            if (token != null) {
                reqBuilder.header("Authorization", "Bearer " + token);
            }

            HttpResponse<String> publishRes = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            long duration = System.currentTimeMillis() - startTime;

            if (publishRes.statusCode() == 200) {
                // Construct test URL (assuming standard UI port 3000)
                // TODO: Get UI URL from config if possible
                String testUrl = String.format("http://localhost:3000/app/%s", appId);

                return ToolResult.success(getName(), String.format(
                        "App deployed successfully to DEV environment!\nTest URL: %s\nDeployment Details: %s",
                        testUrl, publishRes.body()), duration);
            } else {
                return ToolResult.error(getName(), "Deployment failed: " + publishRes.body());
            }

        } catch (Exception e) {
            log.error("DeployAppTool execution failed", e);
            return ToolResult.error(getName(), "Error deploying app: " + e.getMessage());
        }
    }
}
