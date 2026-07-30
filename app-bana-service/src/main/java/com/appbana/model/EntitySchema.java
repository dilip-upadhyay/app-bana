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

    // Two-level checker chain — platform-wide, per-entity opt-in. Boxed rather than a
    // primitive int so that schemas persisted before this field existed (i.e. every
    // existing schema JSON blob) deserialize to `null`, not to `0`. `0` would not equal
    // either valid level and would need its own defaulting branch everywhere this is
    // read; `null` cleanly means "not set" and is normalised to 1 by
    // getEffectiveApprovalLevels(). Only meaningful when approvalRequired == true.
    private Integer approvalLevels;

    /** 1 (single checker, default) or 2 (checker-1 then checker-2). Never null. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public int getEffectiveApprovalLevels() {
        return (approvalLevels != null && approvalLevels == 2) ? 2 : 1;
    }

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
