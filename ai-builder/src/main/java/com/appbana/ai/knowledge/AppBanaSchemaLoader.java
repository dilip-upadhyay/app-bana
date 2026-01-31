package com.appbana.ai.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Loads AppBana schema knowledge from embedded definitions
 * Story 7.1: Schema Loader & Knowledge Base
 */
@Slf4j
public class AppBanaSchemaLoader {

        private final ObjectMapper objectMapper;
        private final Map<String, SchemaDefinition> schemas;

        public AppBanaSchemaLoader() {
                this.objectMapper = new ObjectMapper();
                this.schemas = new HashMap<>();
                loadSchemas();
                log.info("Loaded {} AppBana schemas", schemas.size());
        }

        public List<SchemaDefinition> getAllSchemas() {
                return new ArrayList<>(schemas.values());
        }

        public SchemaDefinition getSchema(String id) {
                return schemas.get(id);
        }

        public List<SchemaDefinition> getSchemasByType(SchemaDefinition.SchemaType type) {
                return schemas.values().stream()
                                .filter(s -> s.getTypeAsEnum() == type)
                                .toList();
        }

        private void loadSchemas() {
                // Load all schema types
                loadEntityFieldTypes();
                loadComponentSchemas();
                loadPageSchemas();
                loadValidationSchemas();

                log.info("Schema loading complete");
        }

        /**
         * Load all 58 AppBana entity field types
         */
        private void loadEntityFieldTypes() {
                // TEXT TYPES
                addFieldType("text", "Short text (VARCHAR 255)", "text",
                                List.of("name", "title", "label"),
                                Map.of("maxLength", 255));

                addFieldType("longtext", "Long text (TEXT)", "textarea",
                                List.of("description", "notes", "comments"),
                                Map.of("rows", 5));

                addFieldType("email", "Email with validation", "email",
                                List.of("user@example.com"),
                                Map.of("format", "email", "maxLength", 255));

                addFieldType("phone", "Phone number with formatting", "tel",
                                List.of("+1-555-0123", "(555) 123-4567"),
                                Map.of("format", "phone", "maxLength", 20));

                addFieldType("url", "URL with validation", "url",
                                List.of("https://example.com"),
                                Map.of("format", "url", "maxLength", 500));

                addFieldType("color", "Color picker (#RRGGBB)", "color",
                                List.of("#FF5733", "#3498DB"),
                                Map.of("pattern", "^#[0-9A-Fa-f]{6}$"));

                // NUMERIC TYPES
                addFieldType("number", "Integer (BIGINT)", "number",
                                List.of("42", "1000"),
                                Map.of("step", 1));

                addFieldType("decimal", "Decimal/float (DECIMAL)", "number",
                                List.of("3.14", "99.99"),
                                Map.of("step", 0.01));

                addFieldType("currency", "Money (DECIMAL 19,4)", "number",
                                List.of("1299.99", "49.95"),
                                Map.of("step", 0.01, "prefix", "$"));

                addFieldType("percentage", "Percentage (0-100)", "number",
                                List.of("75", "100"),
                                Map.of("min", 0, "max", 100, "suffix", "%"));

                // DATE/TIME TYPES
                addFieldType("date", "Date only (DATE)", "date",
                                List.of("2024-01-15"),
                                Map.of());

                addFieldType("datetime", "Date and time (TIMESTAMP)", "datetime-local",
                                List.of("2024-01-15T14:30:00"),
                                Map.of());

                addFieldType("time", "Time only (TIME)", "time",
                                List.of("14:30", "09:00"),
                                Map.of());

                addFieldType("duration", "Duration in minutes/hours", "number",
                                List.of("60", "120"),
                                Map.of("suffix", "minutes"));

                // SELECTION TYPES
                addFieldType("boolean", "Yes/No checkbox (BOOLEAN)", "checkbox",
                                List.of("true", "false"),
                                Map.of());

                addFieldType("status", "Dropdown with predefined options", "select",
                                List.of("active", "pending", "completed"),
                                Map.of("options", List.of("active", "pending", "completed")));

                addFieldType("radio", "Radio buttons", "radio",
                                List.of("option1", "option2"),
                                Map.of("options", List.of("option1", "option2", "option3")));

                addFieldType("multiselect", "Multiple selections", "multiselect",
                                List.of("tag1,tag2", "category1,category2"),
                                Map.of("options", List.of("tag1", "tag2", "tag3")));

                // RICH TYPES
                addFieldType("file", "File upload (stores URL/path)", "file",
                                List.of("/uploads/document.pdf"),
                                Map.of("accept", "*/*"));

                addFieldType("image", "Image upload with preview", "file",
                                List.of("/uploads/photo.jpg"),
                                Map.of("accept", "image/*"));

                addFieldType("json", "JSON data", "textarea",
                                List.of("{\"key\": \"value\"}"),
                                Map.of("rows", 10));

                addFieldType("markdown", "Markdown editor", "textarea",
                                List.of("# Heading\\n\\nParagraph"),
                                Map.of("rows", 10));

                addFieldType("richtext", "WYSIWYG editor", "textarea",
                                List.of("<p>Rich <strong>text</strong></p>"),
                                Map.of("rows", 10));

                // RELATIONSHIP TYPES
                addFieldType("reference", "Foreign key - links to another entity", "select",
                                List.of("customer_id", "order_id"),
                                Map.of("referenceEntity", "required"));

                addFieldType("lookup", "Same as reference but shows related data", "select",
                                List.of("customer_id", "product_id"),
                                Map.of("referenceEntity", "required", "referenceDisplay", "name"));

                // CALCULATED/SYSTEM TYPES
                addFieldType("formula", "Calculated field (computed)", "text",
                                List.of("price * quantity", "firstName + ' ' + lastName"),
                                Map.of("readOnly", true, "formula", "required"));

                addFieldType("autoincrement", "Auto-incrementing ID", "number",
                                List.of("1", "2", "3"),
                                Map.of("readOnly", true, "autoGenerated", true));

                addFieldType("uuid", "UUID/GUID", "text",
                                List.of("550e8400-e29b-41d4-a716-446655440000"),
                                Map.of("readOnly", true, "autoGenerated", true));

                addFieldType("createdAt", "Auto-set creation timestamp", "datetime-local",
                                List.of("2024-01-15T10:30:00"),
                                Map.of("readOnly", true, "autoGenerated", true));

                addFieldType("updatedAt", "Auto-update modified timestamp", "datetime-local",
                                List.of("2024-01-15T14:45:00"),
                                Map.of("readOnly", true, "autoGenerated", true));

                addFieldType("createdBy", "Auto-set creator user ID", "number",
                                List.of("123", "456"),
                                Map.of("readOnly", true, "autoGenerated", true));

                addFieldType("updatedBy", "Auto-update modifier user ID", "number",
                                List.of("123", "456"),
                                Map.of("readOnly", true, "autoGenerated", true));
        }

        private void addFieldType(String name, String description, String htmlType,
                        List<String> examples, Map<String, Object> config) {
                SchemaDefinition schema = new SchemaDefinition();
                schema.setId("field_" + name);
                schema.setName(name);
                schema.setType(SchemaDefinition.SchemaType.ENTITY_FIELD);
                schema.setDescription(description);
                schema.setExamples(examples);
                Map<String, Object> metadata = new HashMap<>();
                config.forEach((k, v) -> metadata.put(k, v));
                metadata.put("htmlType", htmlType);
                schema.setMetadata(metadata);

                schemas.put(schema.getId(), schema);
        }

        /**
         * Load AppBana component schemas
         */
        private void loadComponentSchemas() {
                // INPUT component
                addComponentSchema("input", "Text input field",
                                Map.of(
                                                "label", prop("string", "Field label", true),
                                                "name", prop("string", "Field name for binding", true),
                                                "type",
                                                prop("string", "Input type (text, email, number, etc.)", false, "text"),
                                                "placeholder", prop("string", "Placeholder text", false),
                                                "value", prop("string", "Default value", false),
                                                "required", prop("boolean", "Is field required?", false, false),
                                                "disabled", prop("boolean", "Is field disabled?", false, false),
                                                "entity", prop("string", "Entity name to bind to", false),
                                                "field", prop("string", "Field name in entity", false)),
                                List.of(
                                                "{\"type\":\"input\",\"props\":{\"label\":\"Email\",\"name\":\"email\",\"type\":\"email\",\"required\":true}}",
                                                "{\"type\":\"input\",\"props\":{\"label\":\"Name\",\"name\":\"name\",\"placeholder\":\"Enter your name\"}}"));

                // BUTTON component
                addComponentSchema("button", "Action button",
                                Map.of(
                                                "label", prop("string", "Button text", true),
                                                "variant", prop("string", "Button style", false, "primary"),
                                                "actionType",
                                                prop("string", "Action type: save-entity, navigate, api", false),
                                                "entities",
                                                prop("array", "Entities to save (for save-entity action)", false),
                                                "navigateUrl", prop("string", "URL to navigate to", false),
                                                "apiEndpoint", prop("string", "API endpoint to call", false),
                                                "disabled", prop("boolean", "Is button disabled?", false, false)),
                                List.of(
                                                "{\"type\":\"button\",\"props\":{\"label\":\"Save\",\"actionType\":\"save-entity\",\"entities\":[\"Customer\"]}}",
                                                "{\"type\":\"button\",\"props\":{\"label\":\"Cancel\",\"actionType\":\"navigate\",\"navigateUrl\":\"/customers\"}}"));

                // TABLE component
                addComponentSchema("table", "Data table",
                                Map.of(
                                                "entity", prop("string", "Entity to display", true),
                                                "fields", prop("array", "Fields to show in columns", true),
                                                "actions", prop("array", "Row actions: edit, delete, view", false),
                                                "pageSize", prop("number", "Rows per page", false, 25),
                                                "sort", prop("string", "Default sort field", false),
                                                "multiSelect", prop("boolean", "Enable row selection", false, false)),
                                List.of(
                                                "{\"type\":\"table\",\"props\":{\"entity\":\"Customer\",\"fields\":[{\"name\":\"name\"},{\"name\":\"email\"}],\"actions\":[\"edit\",\"delete\"]}}",
                                                "{\"type\":\"table\",\"props\":{\"entity\":\"Order\",\"fields\":[{\"name\":\"orderNumber\"},{\"name\":\"total\"}],\"pageSize\":50}}"));

                // APP-GRID component
                addComponentSchema("app-grid", "Responsive grid layout",
                                Map.of(
                                                "rows", prop("number", "Number of rows", false, 1),
                                                "cols", prop("number", "Number of columns", false, 2),
                                                "gap", prop("string", "Gap between cells", false, "1rem"),
                                                "minCellHeight", prop("string", "Minimum cell height", false, "auto")),
                                List.of(
                                                "{\"type\":\"app-grid\",\"props\":{\"rows\":2,\"cols\":2,\"gap\":\"1rem\"}}",
                                                "{\"type\":\"app-grid\",\"props\":{\"cols\":3,\"minCellHeight\":\"100px\"}}"));

                // CONTAINER component
                addComponentSchema("container", "Container for grouping components",
                                Map.of(
                                                "padding", prop("string", "Padding", false, "1rem"),
                                                "gap", prop("string", "Gap between children", false, "1rem"),
                                                "layout",
                                                prop("string", "Layout direction: row, column", false, "column"),
                                                "backgroundColor", prop("string", "Background color", false)),
                                List.of(
                                                "{\"type\":\"container\",\"props\":{\"padding\":\"2rem\",\"layout\":\"column\"}}",
                                                "{\"type\":\"container\",\"props\":{\"gap\":\"0.5rem\",\"backgroundColor\":\"#f5f5f5\"}}"));
        }

        private void addComponentSchema(String name, String description,
                        Map<String, SchemaDefinition.PropertyDefinition> properties,
                        List<String> examples) {
                SchemaDefinition schema = new SchemaDefinition();
                schema.setId("component_" + name);
                schema.setName(name);
                schema.setType(SchemaDefinition.SchemaType.COMPONENT);
                schema.setDescription(description);
                schema.setProperties(properties);
                schema.setExamples(examples);

                schemas.put(schema.getId(), schema);
        }

        private SchemaDefinition.PropertyDefinition prop(String type, String description, boolean required) {
                return prop(type, description, required, null);
        }

        private SchemaDefinition.PropertyDefinition prop(String type, String description, boolean required,
                        Object defaultValue) {
                SchemaDefinition.PropertyDefinition prop = new SchemaDefinition.PropertyDefinition();
                prop.setName("");
                prop.setType(type);
                prop.setDescription(description);
                prop.setRequired(required);
                prop.setDefaultValue(defaultValue);
                return prop;
        }

        /**
         * Load page schemas
         */
        private void loadPageSchemas() {
                SchemaDefinition pageSchema = new SchemaDefinition();
                pageSchema.setId("page");
                pageSchema.setName("Page");
                pageSchema.setType(SchemaDefinition.SchemaType.PAGE);
                pageSchema.setDescription("AppBana page metadata structure");

                Map<String, SchemaDefinition.PropertyDefinition> props = new HashMap<>();
                props.put("id", prop("string", "Unique page identifier", true));
                props.put("name", prop("string", "Page name", true));
                props.put("path", prop("string", "URL path", true));
                props.put("rootId", prop("string", "Root component ID", true));
                props.put("nodes", prop("array", "Component nodes", true));

                pageSchema.setProperties(props);
                pageSchema.setExamples(List.of(
                                "{\"id\":\"customers-list\",\"name\":\"Customers\",\"path\":\"/customers\",\"rootId\":\"root\",\"nodes\":[...]}"));

                schemas.put(pageSchema.getId(), pageSchema);
        }

        /**
         * Load validation schemas
         */
        private void loadValidationSchemas() {
                SchemaDefinition validationSchema = new SchemaDefinition();
                validationSchema.setId("validation");
                validationSchema.setName("Validation");
                validationSchema.setType(SchemaDefinition.SchemaType.VALIDATION);
                validationSchema.setDescription("Field validation rules");

                Map<String, SchemaDefinition.PropertyDefinition> props = new HashMap<>();
                props.put("required", prop("boolean", "Is field required?", false));
                props.put("minLength", prop("number", "Minimum length", false));
                props.put("maxLength", prop("number", "Maximum length", false));
                props.put("min", prop("number", "Minimum value", false));
                props.put("max", prop("number", "Maximum value", false));
                props.put("pattern", prop("string", "Regex pattern", false));
                props.put("format", prop("string", "Format: email, phone, url, etc.", false));

                validationSchema.setProperties(props);
                schemas.put(validationSchema.getId(), validationSchema);
        }
}
