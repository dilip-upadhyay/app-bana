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
     * Validate scaffold_app input (full app metadata)
     * Used by ScaffoldAppTool to validate before calling backend
     */
    public ValidationResult validateScaffoldApp(Map<String, Object> scaffoldInput) {
        ValidationResult result = new ValidationResult();

        try {
            // Validate app name
            validateRequiredField(scaffoldInput, "appName", "root", result);
            Object appName = scaffoldInput.get("appName");
            if (appName instanceof String) {
                String name = (String) appName;
                if (name.length() < 2) {
                    result.addError(ValidationError.error("appName", "App name must be at least 2 characters"));
                }
                if (!name.matches("^[a-zA-Z][a-zA-Z0-9 _-]*$")) {
                    result.addError(ValidationError.errorWithFix(
                            "appName",
                            "App name should start with a letter and contain only letters, numbers, spaces, hyphens, or underscores",
                            "Try: '" + name.replaceAll("[^a-zA-Z0-9 _-]", "") + "'"));
                }
            }

            // Validate entities array
            Object entitiesObj = scaffoldInput.get("entities");
            if (entitiesObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> entities = (List<Map<String, Object>>) entitiesObj;

                if (entities.isEmpty()) {
                    result.addWarning(ValidationError.warning("entities", 
                            "No entities defined. App will have no data storage."));
                } else {
                    for (int i = 0; i < entities.size(); i++) {
                        validateEntityForScaffold(entities.get(i), "entities[" + i + "]", result);
                    }
                }
            }

            // Validate pages array (optional but recommended)
            Object pagesObj = scaffoldInput.get("pages");
            if (pagesObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> pages = (List<Map<String, Object>>) pagesObj;

                for (int i = 0; i < pages.size(); i++) {
                    validatePageForScaffold(pages.get(i), "pages[" + i + "]", result);
                }
            }

            log.debug("Scaffold validation complete: {}", result.getSummary());

        } catch (Exception e) {
            log.error("Error validating scaffold input", e);
            result.addError(ValidationError.error("root", "Validation failed: " + e.getMessage()));
        }

        return result;
    }

    /**
     * Validate entity metadata within scaffold context
     */
    private void validateEntityForScaffold(Map<String, Object> entity, String path, ValidationResult result) {
        validateRequiredField(entity, "name", path, result);
        
        // Entity name should be PascalCase
        Object nameObj = entity.get("name");
        if (nameObj instanceof String) {
            String name = (String) nameObj;
            if (!name.isEmpty() && !Character.isUpperCase(name.charAt(0))) {
                result.addWarning(ValidationError.warning(
                        path + ".name",
                        "Entity name '" + name + "' should use PascalCase (e.g., '" + toPascalCase(name) + "')"));
            }
        }

        // Validate fields
        Object fieldsObj = entity.get("fields");
        if (fieldsObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> fields = (List<Map<String, Object>>) fieldsObj;

            if (fields.isEmpty()) {
                result.addError(ValidationError.error(path + ".fields", 
                        "Entity must have at least one field"));
            } else {
                for (int i = 0; i < fields.size(); i++) {
                    validateFieldForScaffold(fields.get(i), path + ".fields[" + i + "]", result);
                }
            }
        } else {
            result.addError(ValidationError.error(path + ".fields", "Entity must have a 'fields' array"));
        }
    }

    /**
     * Validate field metadata within scaffold context
     */
    private void validateFieldForScaffold(Map<String, Object> field, String path, ValidationResult result) {
        // Every field MUST have an id
        if (!field.containsKey("id")) {
            Object nameObj = field.get("name");
            if (nameObj instanceof String) {
                String suggested = toSnakeCase((String) nameObj);
                result.addError(ValidationError.errorWithFix(
                        path + ".id",
                        "Field is missing 'id' property",
                        "Add: \"id\": \"" + suggested + "\""));
            } else {
                result.addError(ValidationError.error(path + ".id", "Field is missing 'id' property"));
            }
        }

        // Validate field type
        Object typeObj = field.get("type");
        if (typeObj instanceof String) {
            String fieldType = (String) typeObj;
            if (!validFieldTypes.contains(fieldType)) {
                String suggestion = suggestFieldType(fieldType);
                if (suggestion != null) {
                    result.addError(ValidationError.errorWithFix(
                            path + ".type",
                            "Invalid type: '" + fieldType + "'",
                            "Use: \"type\": \"" + suggestion + "\""));
                } else {
                    result.addError(ValidationError.error(
                            path + ".type",
                            "Invalid type: '" + fieldType + "'. Valid: " + String.join(", ", validFieldTypes)));
                }
            }

            // Reference fields MUST have referenceEntity
            if ("reference".equals(fieldType) && !field.containsKey("referenceEntity")) {
                result.addError(ValidationError.error(
                        path + ".referenceEntity",
                        "Reference field requires 'referenceEntity' property"));
            }

            // Status/Select fields SHOULD have options
            if (("status".equals(fieldType) || "select".equals(fieldType)) && !field.containsKey("options")) {
                result.addWarning(ValidationError.warning(
                        path + ".options",
                        "Status/select field should have an 'options' array"));
            }
        } else {
            result.addError(ValidationError.error(path + ".type", "Field is missing 'type' property"));
        }
    }

    /**
     * Validate page metadata within scaffold context
     */
    private void validatePageForScaffold(Map<String, Object> page, String path, ValidationResult result) {
        validateRequiredField(page, "name", path, result);
        validateRequiredField(page, "path", path, result);

        // Path should start with /
        Object pathObj = page.get("path");
        if (pathObj instanceof String) {
            String pagePath = (String) pathObj;
            if (!pagePath.startsWith("/")) {
                result.addError(ValidationError.errorWithFix(
                        path + ".path",
                        "Page path must start with '/'",
                        "Use: \"path\": \"/" + pagePath + "\""));
            }
        }

        // Page type should be valid
        Object typeObj = page.get("type");
        if (typeObj instanceof String) {
            String pageType = (String) typeObj;
            Set<String> validPageTypes = Set.of("list", "form", "dashboard", "detail", "crud", "custom");
            if (!validPageTypes.contains(pageType)) {
                result.addWarning(ValidationError.warning(
                        path + ".type",
                        "Unknown page type: '" + pageType + "'. Common types: list, form, dashboard"));
            }
        }
    }

    /**
     * Suggest a field type for a typo/unknown type
     */
    private String suggestFieldType(String invalidType) {
        String lower = invalidType.toLowerCase();

        // Comprehensive typo map
        Map<String, String> typoMap = Map.ofEntries(
                // Text variations
                Map.entry("txt", "text"),
                Map.entry("string", "text"),
                Map.entry("str", "text"),
                Map.entry("varchar", "text"),
                Map.entry("char", "text"),
                // Email variations
                Map.entry("emails", "email"),
                Map.entry("mail", "email"),
                Map.entry("e-mail", "email"),
                // Phone variations
                Map.entry("phones", "phone"),
                Map.entry("tel", "phone"),
                Map.entry("telephone", "phone"),
                Map.entry("mobile", "phone"),
                // Number variations
                Map.entry("int", "number"),
                Map.entry("integer", "number"),
                Map.entry("count", "number"),
                Map.entry("quantity", "number"),
                // Decimal variations
                Map.entry("float", "decimal"),
                Map.entry("double", "decimal"),
                Map.entry("money", "decimal"),
                Map.entry("price", "decimal"),
                Map.entry("currency", "decimal"),
                Map.entry("amount", "decimal"),
                // Boolean variations
                Map.entry("bool", "boolean"),
                Map.entry("checkbox", "boolean"),
                Map.entry("flag", "boolean"),
                // DateTime variations
                Map.entry("timestamp", "datetime"),
                Map.entry("time", "datetime"),
                // Status variations
                Map.entry("select", "status"),
                Map.entry("dropdown", "status"),
                Map.entry("enum", "status"),
                Map.entry("choice", "status"),
                // Longtext variations
                Map.entry("textarea", "longtext"),
                Map.entry("multiline", "longtext"),
                Map.entry("description", "longtext"),
                Map.entry("notes", "longtext"),
                // Reference variations
                Map.entry("fk", "reference"),
                Map.entry("foreign_key", "reference"),
                Map.entry("foreignkey", "reference"),
                Map.entry("relation", "reference"),
                Map.entry("lookup", "reference"));

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
     * Convert string to PascalCase
     */
    private String toPascalCase(String input) {
        if (input == null || input.isEmpty()) return input;
        String[] parts = input.split("[\\s_-]+");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                result.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    result.append(part.substring(1).toLowerCase());
                }
            }
        }
        return result.toString();
    }

    /**
     * Convert string to snake_case
     */
    private String toSnakeCase(String input) {
        if (input == null || input.isEmpty()) return input;
        return input.replaceAll("([a-z])([A-Z])", "$1_$2")
                .replaceAll("[\\s-]+", "_")
                .toLowerCase();
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
