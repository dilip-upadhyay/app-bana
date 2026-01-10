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
        return "{}";
    }

    @Override
    public ToolResult execute(Map<String, Object> args, AgentContext context) {
        long startTime = System.currentTimeMillis();
        String tenantId = context.tenantId();
        String appId = context.appId();
        String token = context.token();

        if (appId == null || appId.equals("default")) {
            return ToolResult.error(getName(), "No active application found to deploy. Please create an app first.");
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
