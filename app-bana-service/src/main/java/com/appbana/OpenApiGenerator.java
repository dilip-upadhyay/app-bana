package org.example;

import org.example.model.EntitySchema;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;

public class OpenApiGenerator {
    public static String generate(List<EntitySchema> schemas) {
        try {
            Map<String, Object> openapi = new LinkedHashMap<>();
            openapi.put("openapi", "3.0.0");
            openapi.put("info", Map.of(
                "title", "AppBana API",
                "version", "1.0.0"
            ));
            Map<String, Object> paths = new LinkedHashMap<>();
            Map<String, Object> components = new LinkedHashMap<>();
            Map<String, Object> schemasMap = new LinkedHashMap<>();
            for (EntitySchema schema : schemas) {
                String entity = schema.getName();
                // CRUD paths
                paths.put("/api/"+entity, Map.of(
                    "get", Map.of(
                        "summary", "List all "+entity+" records",
                        "responses", Map.of("200", Map.of("description", "OK")),
                        "tags", List.of(entity)
                    ),
                    "post", Map.of(
                        "summary", "Create a new "+entity+" record",
                        "requestBody", Map.of(
                            "required", true,
                            "content", Map.of(
                                "application/json", Map.of(
                                    "schema", Map.of("$ref", "#/components/schemas/"+entity)
                                )
                            )
                        ),
                        "responses", Map.of("201", Map.of("description", "Created")),
                        "tags", List.of(entity)
                    )
                ));
                paths.put("/api/"+entity+"/{id}", Map.of(
                    "get", Map.of(
                        "summary", "Get a single "+entity+" record",
                        "parameters", List.of(Map.of(
                            "name", "id",
                            "in", "path",
                            "required", true,
                            "schema", Map.of("type", "string")
                        )),
                        "responses", Map.of("200", Map.of("description", "OK")),
                        "tags", List.of(entity)
                    ),
                    "put", Map.of(
                        "summary", "Update a "+entity+" record",
                        "parameters", List.of(Map.of(
                            "name", "id",
                            "in", "path",
                            "required", true,
                            "schema", Map.of("type", "string")
                        )),
                        "requestBody", Map.of(
                            "required", true,
                            "content", Map.of(
                                "application/json", Map.of(
                                    "schema", Map.of("$ref", "#/components/schemas/"+entity)
                                )
                            )
                        ),
                        "responses", Map.of("200", Map.of("description", "Updated")),
                        "tags", List.of(entity)
                    ),
                    "delete", Map.of(
                        "summary", "Delete a "+entity+" record",
                        "parameters", List.of(Map.of(
                            "name", "id",
                            "in", "path",
                            "required", true,
                            "schema", Map.of("type", "string")
                        )),
                        "responses", Map.of("204", Map.of("description", "Deleted")),
                        "tags", List.of(entity)
                    )
                ));
                // Schema definition
                Map<String, Object> props = new LinkedHashMap<>();
                for (EntitySchema.Field f : schema.getFields()) {
                    Map<String, Object> prop = new LinkedHashMap<>();
                    String t = f.getType();
                    if (t.equals("int") || t.equals("integer")) prop.put("type", "integer");
                    else if (t.equals("long")) prop.put("type", "integer");
                    else if (t.equals("boolean")) prop.put("type", "boolean");
                    else if (t.equals("date") || t.equals("timestamp")) prop.put("type", "string");
                    else prop.put("type", "string");
                    if (f.getLength() != null) prop.put("maxLength", f.getLength());
                    if (f.isRequired()) prop.put("nullable", false);
                    props.put(f.getName(), prop);
                }
                schemasMap.put(entity, Map.of(
                    "type", "object",
                    "properties", props
                ));
            }
            components.put("schemas", schemasMap);
            openapi.put("paths", paths);
            openapi.put("components", components);
            ObjectMapper om = new ObjectMapper();
            return om.writerWithDefaultPrettyPrinter().writeValueAsString(openapi);
        } catch (Exception e) {
            return "{\"error\":\"Failed to generate OpenAPI spec: "+e.getMessage()+"\"}";
        }
    }
}

