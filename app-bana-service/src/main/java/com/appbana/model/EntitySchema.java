package com.appbana.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class EntitySchema {
    private String name;
    private List<Field> fields;
    private String datasourceName; // optional: target datasource for this schema's table
    private String modelKind; // 'relational' | 'document' | 'apiResource' (default: relational)
    @com.fasterxml.jackson.annotation.JsonProperty("appId")
    private String appId; // Owner App ID for isolation
    @com.fasterxml.jackson.annotation.JsonProperty("tenantId")
    private String tenantId; // Owner Tenant ID for global uniqueness
    private boolean approvalRequired; // Task C1.3 — approval workflow enabled for this entity

    public EntitySchema() {
    }

    public EntitySchema(String name, List<Field> fields) {
        this.name = name;
        this.fields = fields;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class Field {
        private String name;
        private String type; // string, int, long, boolean, date
        private boolean primaryKey;
        private boolean autoIncrement;
        private Integer length; // for strings

        // validation / UI metadata
        private boolean required;
        private Long min;
        private Long max;
        private String pattern;
        private String label;
        private String placeholder;
        private Integer order;
        private String existingName; // optional: used for rename mapping

        // Phase B4 — master–detail metadata.
        // For type == "reference" fields, names the parent entity's key
        // (e.g. "Customer") that this FK column points at. Not enforced
        // at the DB level today (dynamic tables have no formal FK
        // constraints); consumed by the runtime + delete cascade logic.
        private String referenceEntity;
        /**
         * Cascade policy for reference fields when the referenced parent row
         * is deleted. Recognised values (case-insensitive):
         *   - "cascade"  → delete this child row too
         *   - "setNull"  → null out this FK column
         *   - "restrict" → block the parent delete if children exist (default)
         * Enforced by GenericEntityRoutes.delete when set.
         */
        private String onDelete;

        public Field() {
        }

        public Field(String name, String type, boolean primaryKey, boolean autoIncrement, Integer length) {
            this.name = name;
            this.type = type;
            this.primaryKey = primaryKey;
            this.autoIncrement = autoIncrement;
            this.length = length;
        }
    }
}
