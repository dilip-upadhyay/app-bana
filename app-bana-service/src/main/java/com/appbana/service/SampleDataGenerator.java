package com.appbana.service;

import com.appbana.JdbcManager;
import com.appbana.model.EntitySchema;
import com.appbana.model.EntitySchema.Field;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Generates sample data for LOCAL environment testing
 */
public class SampleDataGenerator {
    private static final Logger LOG = LoggerFactory.getLogger(SampleDataGenerator.class);
    private static final int DEFAULT_RECORDS_PER_ENTITY = 5;

    /**
     * Generate sample data for all entities in LOCAL environment
     */
    public void generateSampleData(String tenantId, String appId, String environment, List<EntitySchema> entities) {
        // Only generate for LOCAL environment
        if (!"local".equals(environment)) {
            LOG.debug("[SampleData] Skipping sample data generation for environment: {}", environment);
            return;
        }

        LOG.info("[SampleData] Generating sample data for {} entities in LOCAL environment", entities.size());

        for (EntitySchema entity : entities) {
            try {
                insertSampleRecords(tenantId, appId, environment, entity, DEFAULT_RECORDS_PER_ENTITY);
            } catch (Exception e) {
                LOG.warn("[SampleData] Failed to generate data for entity '{}': {}",
                        entity.getName(), e.getMessage());
            }
        }

        LOG.info("[SampleData] Sample data generation complete");
    }

    /**
     * Insert sample records for a single entity
     */
    private void insertSampleRecords(String tenantId, String appId, String env,
            EntitySchema entity, int count) throws SQLException {
        String tableName = getTableName(tenantId, appId, env, entity.getName());

        LOG.info("[SampleData] Inserting {} sample records into {}", count, tableName);

        for (int i = 1; i <= count; i++) {
            Map<String, Object> record = generateRecord(entity, i);
            if (!record.isEmpty()) {
                insertRecord(tableName, record);
            }
        }
    }

    /**
     * Generate table name matching SchemaManager convention
     */
    private String getTableName(String tenantId, String appId, String env, String entityName) {
        // Format: app_{tenantId}_{appId}_{env}_{entityName}
        return String.format("app_%s_%s_%s_%s",
                tenantId.replace("-", "_"),
                appId.replace("-", "_"),
                env,
                entityName);
    }

    /**
     * Generate a single record with sample values
     */
    private Map<String, Object> generateRecord(EntitySchema entity, int index) {
        Map<String, Object> record = new HashMap<>();

        for (Field field : entity.getFields()) {
            // Skip auto-increment/primary key fields
            if (isSkippableField(field)) {
                continue;
            }

            Object value = generateFieldValue(field, index);
            if (value != null) {
                record.put(field.getName(), value);
            }
        }

        return record;
    }

    /**
     * Check if field should be skipped (auto-generated fields)
     */
    private boolean isSkippableField(Field field) {
        String type = field.getType();
        String name = field.getName().toLowerCase();

        // Skip auto-increment fields
        if ("autoincrement".equalsIgnoreCase(type) || "long".equalsIgnoreCase(type) && "id".equals(name)) {
            return true;
        }

        // Skip primary keys that are auto-generated
        return field.isPrimaryKey() && field.isAutoIncrement();
    }

    /**
     * Generate appropriate value based on field type and name
     */
    private Object generateFieldValue(Field field, int index) {
        String type = field.getType().toLowerCase();
        String name = field.getName().toLowerCase();
        String label = field.getLabel() != null ? field.getLabel() : field.getName();

        return switch (type) {
            case "text", "string", "varchar" -> generateTextValue(name, label, index);
            case "number", "int", "integer" -> index * 10;
            case "decimal", "double", "float" -> index * 10.99;
            case "boolean" -> index % 2 == 0;
            case "date" -> LocalDate.now().minusDays(index).toString();
            case "datetime", "timestamp" -> Instant.now().minus(index, ChronoUnit.HOURS).toString();
            case "status" -> generateStatusValue(index);
            case "reference" -> index; // Simple ID reference
            default -> "Sample " + label + " " + index;
        };
    }

    /**
     * Generate text value based on field name patterns
     */
    private String generateTextValue(String fieldName, String label, int index) {
        // Email patterns
        if (fieldName.contains("email")) {
            return "user" + index + "@example.com";
        }

        // Phone patterns
        if (fieldName.contains("phone") || fieldName.contains("mobile")) {
            return String.format("555-01%02d", index);
        }

        // Address patterns
        if (fieldName.contains("address") || fieldName.contains("street")) {
            return index + " Main Street, City " + index;
        }

        // Name patterns
        if (fieldName.contains("name")) {
            String[] names = { "John", "Jane", "Bob", "Alice", "Charlie" };
            String selectedName = names[(index - 1) % names.length];

            if (fieldName.contains("first")) {
                return selectedName;
            } else if (fieldName.contains("last")) {
                String[] lastNames = { "Smith", "Johnson", "Williams", "Brown", "Jones" };
                return lastNames[(index - 1) % lastNames.length];
            }
            return selectedName + " " + (fieldName.contains("full") ? "Smith" : "");
        }

        // Default
        return "Sample " + label + " " + index;
    }

    /**
     * Generate status value (cycles through common statuses)
     */
    private String generateStatusValue(int index) {
        String[] statuses = { "Pending", "Active", "Completed", "Cancelled" };
        return statuses[(index - 1) % statuses.length];
    }

    /**
     * Insert a single record into the database
     */
    private void insertRecord(String tableName, Map<String, Object> record) throws SQLException {
        if (record.isEmpty()) {
            return;
        }

        List<String> columns = new ArrayList<>(record.keySet());
        String placeholders = String.join(", ", Collections.nCopies(columns.size(), "?"));

        String sql = String.format("INSERT INTO %s (%s) VALUES (%s)",
                tableName,
                String.join(", ", columns),
                placeholders);

        try (Connection conn = JdbcManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            int paramIndex = 1;
            for (String column : columns) {
                ps.setObject(paramIndex++, record.get(column));
            }

            ps.executeUpdate();
        }
    }
}
