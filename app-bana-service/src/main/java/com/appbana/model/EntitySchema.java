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
    private String appId; // Owner App ID for isolation
    private String tenantId; // Owner Tenant ID for global uniqueness

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
