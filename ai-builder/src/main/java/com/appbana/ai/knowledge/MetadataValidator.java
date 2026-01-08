package com.appbana.ai.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Validates AI-generated metadata against AppBana schemas
 * Story 7.4: Metadata Validation Service
 * 
 * Features:
 * - Validates entity metadata (fields, types, relationships)
 * - Validates page metadata (components, props, bindings)
 * - Auto-fixes common issues (typos, missing IDs, etc.)
 * - Provides detailed error messages
 */
@Slf4j
public class MetadataValidator {

    private final AppBanaSchemaLoader schemaLoader;
    private final ObjectMapper objectMapper;

    // Valid field types (from AppBanaSchemaLoader)
    private final Set<String> validFieldTypes;

    // Valid component types
    private static final Set<String> VALID_COMPONENT_TYPES = Set.of(
            "input", "button", "table", "app-grid", "container");

    public MetadataValidator(AppBanaSchemaLoader schemaLoader) {
        this.schemaLoader = schemaLoader;
        this.objectMapper = new ObjectMapper();

        // Load valid field types from schema loader
        this.validFieldTypes = schemaLoader.getAllSchemas().stream()
                .filter(s -> s.getType() == SchemaDefinition.SchemaType.ENTITY_FIELD)
                .map(SchemaDefinition::getName)
                .collect(Collectors.toSet());

        log.info("MetadataValidator initialized with {} field types", validFieldTypes.size());
    }

    /**
     * Validate entity metadata
     */
    public ValidationResult validateEntity(Map<String, Object> entityJson) {
        ValidationResult result = new ValidationResult();

        try {
            // Validate required fields
            validateRequiredField(entityJson, "id", "root", result);
            validateRequiredField(entityJson, "name", "root", result);
            validateRequiredField(entityJson, "displayName", "root", result);
            validateRequiredField(entityJson, "fields", "root", result);

            // Validate fields array
            Object fieldsObj = entityJson.get("fields");
            if (fieldsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> fields = (List<Map<String, Object>>) fieldsObj;

                if (fields.isEmpty()) {
                    result.addError(ValidationError.error("fields", "Entity must have at least one field"));
                } else {
                    validateEntityFields(fields, result);
                }
            } else if (fieldsObj != null) {
                result.addError(ValidationError.error("fields", "Fields must be an array"));
            }

            log.debug("Entity validation complete: {}", result.getSummary());

        } catch (Exception e) {
            log.error("Error validating entity", e);
            result.addError(ValidationError.error("root", "Validation failed: " + e.getMessage()));
        }

        return result;
    }

    /**
     * Validate page metadata
     */
    public ValidationResult validatePage(Map<String, Object> pageJson) {
        ValidationResult result = new ValidationResult();

        try {
            // Validate required fields
            validateRequiredField(pageJson, "id", "root", result);
            validateRequiredField(pageJson, "name", "root", result);
            validateRequiredField(pageJson, "path", "root", result);
            validateRequiredField(pageJson, "rootId", "root", result);
            validateRequiredField(pageJson, "nodes", "root", result);

            // Validate nodes array
            Object nodesObj = pageJson.get("nodes");
            if (nodesObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> nodes = (List<Map<String, Object>>) nodesObj;

                if (nodes.isEmpty()) {
                    result.addError(ValidationError.error("nodes", "Page must have at least one component"));
                } else {
                    validatePageComponents(nodes, result);
                }
            } else if (nodesObj != null) {
                result.addError(ValidationError.error("nodes", "Nodes must be an array"));
            }

            log.debug("Page validation complete: {}", result.getSummary());

        } catch (Exception e) {
            log.error("Error validating page", e);
            result.addError(ValidationError.error("root", "Validation failed: " + e.getMessage()));
        }

        return result;
    }

    /**
     * Validate entity fields
     */
    private void validateEntityFields(List<Map<String, Object>> fields, ValidationResult result) {
        for (int i = 0; i < fields.size(); i++) {
            Map<String, Object> field = fields.get(i);
            String path = "fields[" + i + "]";

            // Validate required field properties
            validateRequiredField(field, "id", path, result);
            validateRequiredField(field, "name", path, result);
            validateRequiredField(field, "type", path, result);
            validateRequiredField(field, "required", path, result);

            // Validate field type
            Object typeObj = field.get("type");
            if (typeObj instanceof String) {
                String fieldType = (String) typeObj;
                validateFieldType(fieldType, path + ".type", result);

                // Validate type-specific requirements
                validateFieldTypeRequirements(field, fieldType, path, result);
            }
        }
    }

    /**
     * Validate field type against known types
     */
    private void validateFieldType(String fieldType, String path, ValidationResult result) {
        if (!validFieldTypes.contains(fieldType)) {
            String suggestion = suggestFieldType(fieldType);
            if (suggestion != null) {
                result.addError(ValidationError.errorWithFix(
                        path,
                        "Unknown field type: '" + fieldType + "'",
                        "Did you mean '" + suggestion + "'?"));
            } else {
                result.addError(ValidationError.error(
                        path,
                        "Unknown field type: '" + fieldType + "'. Valid types: " +
                                String.join(", ", validFieldTypes)));
            }
        }
    }

    /**
     * Validate type-specific requirements
     */
    private void validateFieldTypeRequirements(Map<String, Object> field, String fieldType,
            String path, ValidationResult result) {
        // Reference fields must have referenceEntity
        if ("reference".equals(fieldType) || "lookup".equals(fieldType)) {
            if (!field.containsKey("referenceEntity")) {
                result.addError(ValidationError.error(
                        path + ".referenceEntity",
                        "Reference field must specify 'referenceEntity'"));
            }
        }

        // Selection fields should have options
        if ("status".equals(fieldType) || "radio".equals(fieldType) || "multiselect".equals(fieldType)) {
            if (!field.containsKey("options")) {
                result.addWarning(ValidationError.warning(
                        path + ".options",
                        "Selection field should have 'options' array"));
            }
        }

        // Formula fields should have formula
        if ("formula".equals(fieldType)) {
            if (!field.containsKey("formula")) {
                result.addWarning(ValidationError.warning(
                        path + ".formula",
                        "Formula field should have 'formula' expression"));
            }
        }
    }

    /**
     * Validate page components
     */
    private void validatePageComponents(List<Map<String, Object>> nodes, ValidationResult result) {
        for (int i = 0; i < nodes.size(); i++) {
            Map<String, Object> node = nodes.get(i);
            String path = "nodes[" + i + "]";

            // Validate required node properties
            validateRequiredField(node, "id", path, result);
            validateRequiredField(node, "type", path, result);

            // Validate component type
            Object typeObj = node.get("type");
            if (typeObj instanceof String) {
                String componentType = (String) typeObj;
                validateComponentType(componentType, path + ".type", result);

                // Validate component-specific props
                validateComponentProps(node, componentType, path, result);
            }
        }
    }

    /**
     * Validate component type against known types
     */
    private void validateComponentType(String componentType, String path, ValidationResult result) {
        if (!VALID_COMPONENT_TYPES.contains(componentType)) {
            result.addError(ValidationError.error(
                    path,
                    "Unknown component type: '" + componentType + "'. Valid types: " +
                            String.join(", ", VALID_COMPONENT_TYPES)));
        }
    }

    /**
     * Validate component properties
     */
    private void validateComponentProps(Map<String, Object> node, String componentType,
            String path, ValidationResult result) {
        Object propsObj = node.get("props");
        if (!(propsObj instanceof Map)) {
            return; // Props are optional
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) propsObj;

        // Validate input component props
        if ("input".equals(componentType)) {
            if (!props.containsKey("entity") || !props.containsKey("field")) {
                result.addWarning(ValidationError.warning(
                        path + ".props",
                        "Input component should have 'entity' and 'field' props for data binding"));
            }
        }

        // Validate button component props
        if ("button".equals(componentType)) {
            Object actionType = props.get("actionType");
            if ("save".equals(actionType) && !props.containsKey("entities")) {
                result.addWarning(ValidationError.warning(
                        path + ".props.entities",
                        "Save button should have 'entities' array specifying which entities to save"));
            }
        }

        // Validate table component props
        if ("table".equals(componentType)) {
            if (!props.containsKey("entity")) {
                result.addWarning(ValidationError.warning(
                        path + ".props.entity",
                        "Table component should have 'entity' prop specifying data source"));
            }
        }
    }

    /**
     * Auto-fix common issues in metadata
     */
    public Map<String, Object> autoFix(Map<String, Object> metadata, ValidationResult validationResult) {
        Map<String, Object> fixed = new HashMap<>(metadata);

        try {
            // Fix field type typos
            if (fixed.containsKey("fields") && fixed.get("fields") instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> fields = (List<Map<String, Object>>) fixed.get("fields");

                for (Map<String, Object> field : fields) {
                    if (field.get("type") instanceof String) {
                        String fieldType = (String) field.get("type");
                        if (!validFieldTypes.contains(fieldType)) {
                            String suggestion = suggestFieldType(fieldType);
                            if (suggestion != null) {
                                field.put("type", suggestion);
                                log.info("Auto-fixed field type: {} -> {}", fieldType, suggestion);
                            }
                        }
                    }

                    // Generate missing IDs
                    if (!field.containsKey("id")) {
                        field.put("id", UUID.randomUUID().toString());
                        log.info("Generated missing field ID");
                    }

                    // Generate missing labels from name
                    if (!field.containsKey("label") && field.containsKey("name")) {
                        String name = (String) field.get("name");
                        field.put("label", generateLabel(name));
                        log.info("Generated label from name: {}", name);
                    }
                }
            }

            // Fix component nodes
            if (fixed.containsKey("nodes") && fixed.get("nodes") instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> nodes = (List<Map<String, Object>>) fixed.get("nodes");

                for (Map<String, Object> node : nodes) {
                    // Generate missing IDs
                    if (!node.containsKey("id")) {
                        node.put("id", UUID.randomUUID().toString());
                        log.info("Generated missing node ID");
                    }
                }
            }

            validationResult.setFixedMetadata(fixed);
            log.info("Auto-fix complete");

        } catch (Exception e) {
            log.error("Error during auto-fix", e);
        }

        return fixed;
    }

    /**
     * Suggest a field type for a typo/unknown type
     */
    private String suggestFieldType(String invalidType) {
        String lower = invalidType.toLowerCase();

        // Common typos
        Map<String, String> typoMap = Map.ofEntries(
                Map.entry("txt", "text"),
                Map.entry("string", "text"),
                Map.entry("str", "text"),
                Map.entry("emails", "email"),
                Map.entry("mail", "email"),
                Map.entry("phones", "phone"),
                Map.entry("tel", "phone"),
                Map.entry("int", "number"),
                Map.entry("integer", "number"),
                Map.entry("float", "decimal"),
                Map.entry("double", "decimal"),
                Map.entry("bool", "boolean"),
                Map.entry("checkbox", "boolean"),
                Map.entry("timestamp", "datetime"),
                Map.entry("select", "status"),
                Map.entry("dropdown", "status"),
                Map.entry("textarea", "longtext"),
                Map.entry("fk", "reference"),
                Map.entry("foreign_key", "reference"));

        if (typoMap.containsKey(lower)) {
            return typoMap.get(lower);
        }

        // Find closest match using simple similarity
        return validFieldTypes.stream()
                .filter(valid -> valid.toLowerCase().contains(lower) || lower.contains(valid.toLowerCase()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Generate a human-readable label from a camelCase name
     */
    private String generateLabel(String name) {
        // Convert camelCase to Title Case
        return name.replaceAll("([A-Z])", " $1")
                .trim()
                .substring(0, 1).toUpperCase() +
                name.replaceAll("([A-Z])", " $1").trim().substring(1);
    }

    /**
     * Validate that a required field exists
     */
    private void validateRequiredField(Map<String, Object> obj, String fieldName,
            String path, ValidationResult result) {
        if (!obj.containsKey(fieldName) || obj.get(fieldName) == null) {
            result.addError(ValidationError.error(
                    path + "." + fieldName,
                    "Required field '" + fieldName + "' is missing"));
        }
    }
}
