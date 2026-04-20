package com.appbana.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class MetadataPatcher {
    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        File file = new File("app_metadata.json");
        ObjectNode app = (ObjectNode) mapper.readTree(file);

        Set<String> auditFields = new HashSet<>();
        auditFields.add("created_at");
        auditFields.add("updated_at");
        auditFields.add("id");

        ArrayNode pagesData = (ArrayNode) app.get("pagesData");
        for (JsonNode page : pagesData) {
            String pageName = page.get("name").asText();
            System.out.println("Processing page: " + pageName);
            ObjectNode pageObj = (ObjectNode) page;
            ArrayNode nodes = (ArrayNode) pageObj.get("nodes");
            
            Set<String> nodesToRemove = new HashSet<>();
            
            // First pass: identify input nodes for audit fields
            for (JsonNode node : nodes) {
                JsonNode props = node.get("props");
                if (props != null && props.has("field")) {
                    String field = props.get("field").asText().toLowerCase();
                    if (auditFields.contains(field)) {
                        System.out.println("  Marking input node for removal: " + node.get("id").asText() + " (field: " + field + ")");
                        nodesToRemove.add(node.get("id").asText());
                    }
                }
            }
            
            // Second pass: identify containers for those inputs (recursively or repeatedly to catch nested ones)
            boolean changed = true;
            while (changed) {
                changed = false;
                for (JsonNode node : nodes) {
                    String nodeId = node.get("id").asText();
                    if (nodesToRemove.contains(nodeId)) continue;

                    if (node.has("children")) {
                        ArrayNode children = (ArrayNode) node.get("children");
                        if (children.size() > 0) {
                            boolean allChildrenAreAudit = true;
                            for (JsonNode child : children) {
                                if (!nodesToRemove.contains(child.asText())) {
                                    allChildrenAreAudit = false;
                                    break;
                                }
                            }
                            if (allChildrenAreAudit) {
                                System.out.println("  Marking container node for removal (all children are audit): " + nodeId);
                                nodesToRemove.add(nodeId);
                                changed = true;
                            }
                        }
                    }
                }
            }

            // Remove identified nodes from the array
            Iterator<JsonNode> it = nodes.iterator();
            while (it.hasNext()) {
                JsonNode node = it.next();
                String nodeId = node.get("id").asText();
                if (nodesToRemove.contains(nodeId)) {
                    it.remove();
                } else if (node.has("children")) {
                    // Filter children list of kept nodes
                    ArrayNode children = (ArrayNode) node.get("children");
                    for (int i = 0; i < children.size(); i++) {
                        if (nodesToRemove.contains(children.get(i).asText())) {
                            children.remove(i);
                            i--; // adjust index
                        }
                    }
                }
                
                // Fix common field mapping errors: spaces to underscores (only for kept nodes)
                if (!nodesToRemove.contains(nodeId)) {
                    JsonNode props = node.get("props");
                    if (props != null && props.has("field")) {
                        String field = props.get("field").asText();
                        if (field.contains(" ")) {
                            String newField = field.replace(" ", "_");
                            System.out.println("  Fixing field mapping: '" + field + "' -> '" + newField + "' in node " + nodeId);
                            ((ObjectNode) props).put("field", newField);
                            if (props.has("name")) {
                                ((ObjectNode) props).put("name", newField);
                            }
                        }
                    }
                }
            }
        }

        mapper.writerWithDefaultPrettyPrinter().writeValue(new File("app_metadata_fixed.json"), app);
        System.out.println("Metadata patched successfully!");
    }
}
