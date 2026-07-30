package com.appbana.converter;

import com.appbana.model.EntitySchema;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts frontend EntityMeta (JSON) to backend EntitySchema objects.
 * Handles field type mapping and default id field generation.
 */
public class EntitySchemaConverter {
    private static final Logger LOG = LoggerFactory.getLogger(EntitySchemaConverter.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Convert EntityMeta JSON to EntitySchema
     * 
     * @param entityMetaJson JSON representation of EntityMeta from frontend
     * @return EntitySchema ready for SchemaManager
     */
    public static EntitySchema convert(String entityName, JsonNode entityMetaJson) {
        LOG.debug("[CONVERTER] Converting entity: {}", entityName);

        EntitySchema schema = new EntitySchema();
        schema.setName(entityName);

        // Extract fields array
        JsonNode fieldsNode = entityMetaJson.get("fields");
        if (fieldsNode == null || !fieldsNode.isArray()) {
            LOG.warn("[CONVERTER] No fields found for entity: {}", entityName);
            schema.setFields(new ArrayList<>());
            return schema;
        }

        List<EntitySchema.Field> fields = new ArrayList<>();
        boolean hasIdField = false;

        // Convert each field
        for (JsonNode fieldNode : fieldsNode) {
            EntitySchema.Field field = convertField(fieldNode);
            fields.add(field);

            if ("id".equalsIgnoreCase(field.getName())) {
                hasIdField = true;
            }
        }

        // Add default id field if not present
        if (!hasIdField) {
            LOG.debug("[CONVERTER] Adding default id field for entity: {}", entityName);
            EntitySchema.Field idField = new EntitySchema.Field();
            idField.setName("id");
            idField.setType("long");
            idField.setPrimaryKey(true);
            idField.setAutoIncrement(true);
            fields.add(0, idField); // Add at beginning
        }

        schema.setFields(fields);
        LOG.info("[CONVERTER] Converted entity {} with {} fields", entityName, fields.size());

        return schema;
    }

    /**
     * Convert a single field from frontend format to backend format
     */
    private static EntitySchema.Field convertField(JsonNode fieldNode) {
        EntitySchema.Field field = new EntitySchema.Field();

        // Name (required)
        field.setName(fieldNode.get("name").asText());

        // Label (optional, default to name if missing/empty)
        if (fieldNode.has("label") && !fieldNode.get("label").asText().isEmpty()) {
            field.setLabel(fieldNode.get("label").asText());
        } else {
            // If no label, default to the Name (which might be the original unsanitized one
            // if we are lucky,
            // but here we get the node's current name.
            // Ideally caller sets label BEFORE calling this if name is being changed.)
            // The AppPublishService will handle setting the label in the JSON before
            // calling this.
            if (fieldNode.has("displayName")) {
                // Support legacy/alt property if exists
                field.setLabel(fieldNode.get("displayName").asText());
            } else {
                field.setLabel(field.getName());
            }
        }

        // Type mapping (frontend → backend)
        String frontendType = fieldNode.has("type") ? fieldNode.get("type").asText() : "text";
        field.setType(mapFieldType(frontendType, fieldNode));

        // Primary key
        if (fieldNode.has("primaryKey")) {
            field.setPrimaryKey(fieldNode.get("primaryKey").asBoolean());
        }

        // Auto increment - can be set via "autoIncrement" property OR via
        // type="autoincrement"
        if (fieldNode.has("autoIncrement")) {
            field.setAutoIncrement(fieldNode.get("autoIncrement").asBoolean());
        } else if ("autoincrement".equalsIgnoreCase(frontendType)) {
            field.setAutoIncrement(true);
            field.setPrimaryKey(true); // Auto-increment fields are always primary keys
        }

        // Required - auto-increment fields should NOT be required (database generates
        // value)
        if (fieldNode.has("required")) {
            if (!field.isAutoIncrement()) { // Only apply if not auto-increment
                field.setRequired(fieldNode.get("required").asBoolean());
            }
        }

        // Length (for string types)
        if (fieldNode.has("length")) {
            field.setLength(fieldNode.get("length").asInt());
        }

        // Pattern (regex validation)
        if (fieldNode.has("pattern")) {
            field.setPattern(fieldNode.get("pattern").asText());
        }

        // Min/Max (for numeric types)
        if (fieldNode.has("min")) {
            field.setMin((long) fieldNode.get("min").asDouble());
        }
        if (fieldNode.has("max")) {
            field.setMax((long) fieldNode.get("max").asDouble());
        }

        // Phase B4 master-detail metadata — must survive the frontend->backend
        // conversion or every reference field silently loses its FK target and
        // delete-cascade policy (found live: scaffold_app's Department.head_of_department
        // -> Employee reference had no referenceEntity carried through at all).
        if (fieldNode.has("referenceEntity")) {
            field.setReferenceEntity(fieldNode.get("referenceEntity").asText());
        }
        if (fieldNode.has("onDelete")) {
            field.setOnDelete(fieldNode.get("onDelete").asText());
        }

        return field;
    }

    /**
     * Map frontend field types to backend EntitySchema types
     * 
     * Frontend types: text, number, email, phone, date, datetime, boolean, textarea
     * Backend types: string, int, long, boolean, date, timestamp, text
     */
    private static String mapFieldType(String frontendType, JsonNode fieldNode) {
        return switch (frontendType.toLowerCase()) {
            case "text", "email", "phone", "select", "dropdown", "status" -> "string";
            case "autoincrement" -> "long"; // Auto-increment fields are always long
            case "number" -> {
                // Check if autoIncrement to use long for ID fields
                boolean isAutoIncrement = fieldNode.has("autoIncrement") &&
                        fieldNode.get("autoIncrement").asBoolean();
                yield isAutoIncrement ? "long" : "int";
            }
            // "longtext" is a distinct approved schema type (TEXT/CLOB, unbounded) —
            // it must NOT collapse into "textarea"'s "text" case by accident, but it
            // maps to the same backend kind, so both are listed explicitly.
            case "textarea", "longtext" -> "text";
            case "date" -> "date";
            case "datetime", "timestamp" -> "timestamp";
            case "boolean", "checkbox" -> "boolean";
            case "int", "integer" -> "int";
            case "long", "bigint" -> "long";
            // "decimal" was previously falling through to the default "string" case,
            // silently turning every money/decimal field (salary, cost, price...) into
            // a VARCHAR(255) column instead of NUMERIC(19,4). Found live via the
            // Employee Onboarding scaffold test (salary/estimated_cost fields).
            case "decimal", "double", "float", "currency" -> "decimal";
            // "reference" was previously falling through to the default "string" case,
            // silently turning every FK field into a VARCHAR(255) column — which then
            // made SchemaManager.syncForeignKeys' FK constraint fail with an
            // "incompatible types: character varying and integer" error against the
            // parent's INTEGER primary key. Found live via the Employee Onboarding
            // scaffold test (Department.head_of_department -> Employee reference).
            case "reference" -> "reference";
            default -> {
                LOG.warn("[CONVERTER] Unknown field type: {}, defaulting to string", frontendType);
                yield "string";
            }
        };
    }

    /**
     * Validate entity name (alphanumeric + underscore only)
     */
    public static boolean isValidEntityName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        return name.matches("^[a-zA-Z][a-zA-Z0-9_]*$");
    }

    /**
     * Validate field name (alphanumeric + underscore only)
     */
    public static boolean isValidFieldName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        return name.matches("^[a-zA-Z][a-zA-Z0-9_]*$");
    }
}
