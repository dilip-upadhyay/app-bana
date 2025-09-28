package com.appbana;

import com.appbana.model.EntitySchema;
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
                // Build reusable query parameter list for advanced GET
                List<Map<String,Object>> listParams = new ArrayList<>();
                listParams.add(param("limit", "query", false, Map.of("type","integer","default",50,"maximum",500), "Max rows to return (default 50, max 500)", true));
                listParams.add(param("offset", "query", false, Map.of("type","integer","default",0), "Starting offset (zero-based)", true));
                listParams.add(param("q", "query", false, Map.of("type","string"), "Case-insensitive substring search across textual fields", true));
                listParams.add(param("fields", "query", false, Map.of("type","string"), "Comma-separated projection (e.g. firstName,lastName)", true));
                listParams.add(param("sort", "query", false, Map.of("type","string"), "Comma-separated sort list; prefix '-' for DESC (e.g. -createdAt,firstName)", true));
                listParams.add(param("filter", "query", false, Map.of("type","string"), "Comma-separated equality filters field:value (e.g. status:ACTIVE,age:30)", true));
                listParams.add(param("count", "query", false, Map.of("type","boolean"), "If true (or 1) returns only total matching rows (no rows array)", true));

                // CRUD + batch paths
                Map<String,Object> collectionOps = new LinkedHashMap<>();
                // GET with advanced query params
                Map<String,Object> getOp = new LinkedHashMap<>();
                getOp.put("summary", "List or query "+entity+" records");
                getOp.put("parameters", listParams);
                getOp.put("responses", Map.of(
                    "200", Map.of(
                        "description", "Success (array for legacy simple call; object with rows/total when any adv param present)",
                        "x-response-structure", Map.of(
                            "legacy", "[ { ...entityFields } ]",
                            "advanced", Map.of(
                                "rows", "Array of projected rows",
                                "total", "Total rows matching criteria",
                                "limit", "Applied limit",
                                "offset", "Applied offset",
                                "query?", "Echo of q when present",
                                "fields?", "Array of projected field names when projection applied",
                                "sort?", "Array of ORDER BY fragments",
                                "filters?", "Map of equality filters applied"
                            )
                        )
                    )
                ));
                getOp.put("tags", List.of(entity));
                getOp.put("x-advanced-queries", true);

                // POST (single create)
                Map<String,Object> postOp = Map.of(
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
                );

                // Batch POST
                Map<String,Object> batchPost = Map.of(
                    "summary", "Bulk insert "+entity+" records",
                    "requestBody", Map.of(
                        "required", true,
                        "content", Map.of(
                            "application/json", Map.of(
                                "schema", Map.of(
                                    "type", "array",
                                    "items", Map.of("$ref", "#/components/schemas/"+entity)
                                )
                            )
                        )
                    ),
                    "responses", Map.of("201", Map.of("description", "Batch insert result (inserted, ids[]?)")),
                    "tags", List.of(entity),
                    "x-bulk-limit", 1000
                );

                collectionOps.put("get", getOp);
                collectionOps.put("post", postOp);
                collectionOps.put("post#batch", batchPost); // temp key, will be normalized below

                // /api/{entity}
                Map<String,Object> normalizedCollection = new LinkedHashMap<>();
                for (Map.Entry<String,Object> e : collectionOps.entrySet()) {
                    if (e.getKey().equals("post#batch")) continue; // handled separately
                    normalizedCollection.put(e.getKey(), e.getValue());
                }
                paths.put("/api/"+entity, normalizedCollection);
                // /api/{entity}/batch endpoint
                paths.put("/api/"+entity+"/batch", Map.of(
                    "post", batchPost
                ));

                // /api/{entity}/{id}
                paths.put("/api/"+entity+"/{id}", Map.of(
                    "get", Map.of(
                        "summary", "Get a single "+entity+" record",
                        "parameters", List.of(pathIdParam()),
                        "responses", Map.of("200", Map.of("description", "OK")),
                        "tags", List.of(entity)
                    ),
                    "put", Map.of(
                        "summary", "Update a "+entity+" record",
                        "parameters", List.of(pathIdParam()),
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
                        "parameters", List.of(pathIdParam()),
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

    private static Map<String,Object> param(String name, String in, boolean required, Map<String,Object> schema, String desc, boolean advanced){
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("in", in);
        if (required) m.put("required", true);
        m.put("schema", schema);
        m.put("description", desc);
        if (advanced) m.put("x-advanced", true);
        return m;
    }
    private static Map<String,Object> pathIdParam(){
        return Map.of(
            "name","id",
            "in","path",
            "required",true,
            "schema", Map.of("type","string")
        );
    }
}
