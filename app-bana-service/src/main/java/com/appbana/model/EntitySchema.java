package com.appbana.model;

import java.util.List;

public class EntitySchema {
    private String name;
    private List<Field> fields;
    private String datasourceName; // optional: target datasource for this schema's table
    private String modelKind; // 'relational' | 'document' | 'apiResource' (default: relational)

    public EntitySchema() {}

    public EntitySchema(String name, List<Field> fields) {
        this.name = name;
        this.fields = fields;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Field> getFields() {
        return fields;
    }

    public void setFields(List<Field> fields) {
        this.fields = fields;
    }

    public String getDatasourceName() {
        return datasourceName;
    }

    public void setDatasourceName(String datasourceName) {
        this.datasourceName = datasourceName;
    }

    public String getModelKind() {
        return modelKind;
    }

    public void setModelKind(String modelKind) {
        this.modelKind = modelKind;
    }

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

        public Field() {}

        public Field(String name, String type, boolean primaryKey, boolean autoIncrement, Integer length) {
            this.name = name;
            this.type = type;
            this.primaryKey = primaryKey;
            this.autoIncrement = autoIncrement;
            this.length = length;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public boolean isPrimaryKey() {
            return primaryKey;
        }

        public void setPrimaryKey(boolean primaryKey) {
            this.primaryKey = primaryKey;
        }

        public boolean isAutoIncrement() {
            return autoIncrement;
        }

        public void setAutoIncrement(boolean autoIncrement) {
            this.autoIncrement = autoIncrement;
        }

        public Integer getLength() {
            return length;
        }

        public void setLength(Integer length) {
            this.length = length;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public Long getMin() {
            return min;
        }

        public void setMin(Long min) {
            this.min = min;
        }

        public Long getMax() {
            return max;
        }

        public void setMax(Long max) {
            this.max = max;
        }

        public String getPattern() {
            return pattern;
        }

        public void setPattern(String pattern) {
            this.pattern = pattern;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getPlaceholder() {
            return placeholder;
        }

        public void setPlaceholder(String placeholder) {
            this.placeholder = placeholder;
        }

        public Integer getOrder() {
            return order;
        }

        public void setOrder(Integer order) {
            this.order = order;
        }

        public String getExistingName() {
            return existingName;
        }

        public void setExistingName(String existingName) {
            this.existingName = existingName;
        }
    }
}
