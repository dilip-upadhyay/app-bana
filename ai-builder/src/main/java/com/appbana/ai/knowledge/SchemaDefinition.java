package com.appbana.ai.knowledge;

import lombok.Data;
import java.util.*;

/**
 * Represents an AppBana schema definition
 * Story 7.1: Schema Loader & Knowledge Base
 */
@Data
public class SchemaDefinition {
    private String id;
    private String name;
    private SchemaType type;
    private String description;
    private Map<String, PropertyDefinition> properties;
    private List<String> required;
    private List<String> examples;
    private Map<String, String> metadata;

    public enum SchemaType {
        ENTITY,
        ENTITY_FIELD,
        PAGE,
        COMPONENT,
        VALIDATION,
        RELATIONSHIP,
        PERMISSION
    }

    @Data
    public static class PropertyDefinition {
        private String name;
        private String type; // string, number, boolean, array, object, enum
        private String description;
        private List<String> enumValues;
        private PropertyDefinition items; // for arrays
        private Map<String, PropertyDefinition> properties; // for objects
        private boolean required;
        private Object defaultValue;
    }
}
