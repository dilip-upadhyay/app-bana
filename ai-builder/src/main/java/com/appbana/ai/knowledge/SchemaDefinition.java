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
    }


    /**
     * Set type as String - primary method for new code
     */
    public void setType(SchemaType type) {
        this.type = type.getValue();
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
