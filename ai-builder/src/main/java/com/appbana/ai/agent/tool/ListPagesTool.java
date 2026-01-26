package com.appbana.ai.agent.tool;

import com.appbana.ai.agent.AgentContext;
import com.fasterxml.jackson.core.type.TypeReference;
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
 * Tool for listing pages in AppBana
 * Implements context-aware listing.
 */
@Slf4j
public class ListPagesTool implements Tool {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public ListPagesTool(String baseUrl) {
        this.httpClient = HttpClient.newHttpClient();
        this.baseUrl = baseUrl;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getName() {
        return "list_pages";
    }

    @Override
    public String getDescription() {
        return "List all pages in the CURRENTLY SELECTED application. " +
                "If no app is selected, this tool will fail/warn. Use this to see existing pages.";
    }

    @Override
    public String getParameterSchema() {
        return """
                {
                  "type": "object",
                  "properties": {},
                  "description": "No parameters required"
                }
                """;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments, AgentContext context) {
        long startTime = System.currentTimeMillis();

        try {
            String appId = context.appId();
            String tenantId = context.tenantId();

            if (appId == null || appId.equals("default") || appId.isEmpty()) {
                return ToolResult.error(getName(),
                        "No application selected. Please ask the user to select an app first.");
            }

            log.info("[ListPagesTool] Listing pages for app: {}", appId);

            // Fetch app metadata which contains pages
            String url = baseUrl + "/appbana-studio/" + tenantId + "/apps/" + appId;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long executionTime = System.currentTimeMillis() - startTime;

            if (response.statusCode() == 200) {
                Map<String, Object> appData = objectMapper.readValue(response.body(),
                        new TypeReference<Map<String, Object>>() {
                        });

                @SuppressWarnings("unchecked")
                List<Object> pagesData = (List<Object>) appData.get("pagesData");

                // Fallback to "pages" list if pagesData is missing (older model)
                List<String> pageNames;

                if (pagesData != null) {
                    pageNames = pagesData.stream()
                            .map(p -> {
                                if (p instanceof Map)
                                    return (String) ((Map<?, ?>) p).get("name");
                                return p.toString();
                            })
                            .toList();
                } else {
                    @SuppressWarnings("unchecked")
                    List<String> simplePages = (List<String>) appData.get("pages");
                    pageNames = simplePages != null ? simplePages : List.of();
                }

                log.info("[ListPagesTool] Found {} pages in app {}", pageNames.size(), appId);

                Map<String, Object> result = new HashMap<>();
                result.put("pages", pageNames);
                result.put("count", pageNames.size());
                result.put("appId", appId);

                return ToolResult.success(getName(), result, executionTime);
            } else {
                log.error("[ListPagesTool] Failed to fetch app: {} - {}", response.statusCode(), response.body());
                return ToolResult.error(getName(), "Failed to fetch app pages: " + response.statusCode());
            }

        } catch (Exception e) {
            log.error("[ListPagesTool] Execution failed", e);
            return ToolResult.error(getName(), "Execution error: " + e.getMessage());
        }
    }
}
