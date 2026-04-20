package com.appbana.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Utility to fix date metadata in existing apps.
 * Renames 'inputType' to 'type' and fixes date types.
 */
public class FixDateMetadata {
    private static final String BASE_URL = "http://localhost:8080";
    private static final String TENANT_ID = "t-cfe77e13";
    private static final String APP_ID = "e9f58057-d181-4e1b-b13f-54773f0644e0";
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final HttpClient client = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {
        System.out.println("Fetching app metadata...");
        String url = String.format("%s/appbana-studio/%s/apps/%s", BASE_URL, TENANT_ID, APP_ID);
        
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            System.err.println("Failed to fetch app: " + response.statusCode());
            return;
        }

        JsonNode app = mapper.readTree(response.body());
        ArrayNode pagesData = (ArrayNode) app.get("pagesData");

        for (JsonNode page : pagesData) {
            String pageId = page.get("id").asText();
            System.out.println("Processing page: " + pageId);
            
            boolean changed = fixPageMetadata((ObjectNode) page);
            
            if (changed) {
                System.out.println("  Syncing fixed page: " + pageId);
                syncPage(pageId, page);
            } else {
                System.out.println("  No changes needed for page: " + pageId);
            }
        }
        
        System.out.println("Migration complete!");
    }

    private static boolean fixPageMetadata(ObjectNode page) {
        boolean changed = false;
        ArrayNode nodes = (ArrayNode) page.get("nodes");
        if (nodes == null) return false;

        for (JsonNode node : nodes) {
            ObjectNode onode = (ObjectNode) node;
            ObjectNode props = (ObjectNode) onode.get("props");
            if (props == null) continue;

            // Handle inputType -> type migration
            if (props.has("inputType")) {
                JsonNode inputType = props.get("inputType");
                String typeValue = inputType.asText();
                
                // Fix "datetime" -> "datetime-local"
                if ("datetime".equals(typeValue)) {
                    typeValue = "datetime-local";
                }
                
                props.put("type", typeValue);
                props.remove("inputType");
                changed = true;
                System.out.println("    Migrated inputType to type: " + typeValue);
            }
            
            // Fix existing type "datetime" -> "datetime-local"
            if (props.has("type") && "datetime".equals(props.get("type").asText())) {
                props.put("type", "datetime-local");
                changed = true;
                System.out.println("    Fixed type: datetime -> datetime-local");
            }
            
            // Fix Table fields for consistency
            if ("table".equals(onode.get("type").asText()) && props.has("fields")) {
                ArrayNode fields = (ArrayNode) props.get("fields");
                for (JsonNode field : fields) {
                    ObjectNode ofield = (ObjectNode) field;
                    if (ofield.has("type") && "datetime".equals(ofield.get("type").asText())) {
                        ofield.put("type", "datetime-local");
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    private static void syncPage(String pageId, JsonNode page) throws IOException, InterruptedException {
        String url = String.format("%s/appbana-studio/%s/apps/%s/pages/%s", 
                BASE_URL, TENANT_ID, APP_ID, pageId);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(page)))
                .build();
        
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            System.out.println("    Successfully saved page: " + pageId);
        } else {
            System.err.println("    Failed to save page: " + pageId + " - " + response.statusCode());
        }
    }
}
