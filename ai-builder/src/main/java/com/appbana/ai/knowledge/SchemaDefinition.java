package com.appbana.ai.knowledge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.*;

/**
 * Represents an AppBana schema definition for knowledge base indexing.
 * Used by AppBanaKnowledgeLoader to store platform knowledge in vector database.
 * Story 7.1: Schema Loader & Knowledge Base
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchemaDefinition {
    private String id;
    private String name;
    private String type;              // Knowledge type: entity, field-type, component, page, security, workflow, etc.
    private String category;          // Category for organization: platform, schema, security, workflow, etc.
    private String description;
    private Map<String, PropertyDefinition> properties;
    private List<String> required;
    private List<String> examples;
    private Map<String, Object> metadata;   // Changed to Object to support any value type

    /**
     * Legacy enum for backward compatibility with existing code.
     * New code should use String type directly.
     */
    public enum SchemaType {
        ENTITY("entity"),
        ENTITY_FIELD("field-type"),
        PAGE("page"),
        COMPONENT("component"),
        VALIDATION("validation"),
        RELATIONSHIP("relationship"),
        PERMISSION("permission");

        private final String value;

        SchemaType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        /**
         * Resolve by wire value (e.g. "field-type") or by constant name, returning {@code null}
         * for unknown types. {@code valueOf} is wrong here: the wire value and the constant name
         * differ (ENTITY_FIELD carries the value "field-type"), so valueOf("FIELD_TYPE") throws.
         */
        public static SchemaType fromValue(String raw) {
            if (raw == null) {
                return null;
            }
            for (SchemaType st : values()) {
                if (st.value.equalsIgnoreCase(raw) || st.name().equalsIgnoreCase(raw)) {
                    return st;
                }
            }
            return null;
        }
    }


    /**
     * Set type as String - primary method for new code
     */
    public void setType(SchemaType type) {
        this.type = type.getValue();
    }

    /**
     * Set a wire type that has no matching enum constant (e.g. "domain-template").
     * Lombok suppresses the generated String setter because of the enum overload above.
     */
    public void setRawType(String type) {
        this.type = type;
    }

    /**
     * Get type as enum for backward compatibility.
     * Returns null if type doesn't match any enum value.
     */
    public SchemaType getTypeAsEnum() {
        if (type == null) return null;
        for (SchemaType st : SchemaType.values()) {
            if (st.getValue().equals(type) || st.name().equalsIgnoreCase(type)) {
                return st;
            }
        }
        return null;
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
