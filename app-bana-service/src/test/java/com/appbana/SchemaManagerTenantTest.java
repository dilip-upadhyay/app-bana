package com.appbana;

import com.appbana.model.EntitySchema;
import com.appbana.model.TenantContext;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SchemaManager with tenant/app isolation
 * 
 * Verifies that:
 * 1. New tables automatically get tenant_id and app_id columns
 * 2. Existing tables without tenant_id/app_id get them added
 * 3. Indexes are created automatically
 */
public class SchemaManagerTenantTest {

    @Test
    public void testNewTableHasTenantAndAppColumns() throws SQLException {
        // Create a new entity schema
        EntitySchema schema = new EntitySchema();
        schema.setName("test_customer_new");
        schema.setAppId("test-app-1");
        schema.setTenantId("tenant-1");
        
        EntitySchema.Field idField = new EntitySchema.Field();
        idField.setName("id");
        idField.setType("long");
        idField.setPrimaryKey(true);
        idField.setAutoIncrement(true);
        
        EntitySchema.Field nameField = new EntitySchema.Field();
        nameField.setName("name");
        nameField.setType("string");
        nameField.setLength(100);
        
        List<EntitySchema.Field> fields = new ArrayList<>();
        fields.add(idField);
        fields.add(nameField);
        schema.setFields(fields);
        
        // Save schema (this creates the table)
        SchemaManager.saveSchema(schema);
        
        // Verify table has tenant_id and app_id columns
        String tableName = SchemaManager.getPhysicalTableName(schema);
        try (Connection conn = JdbcManager.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            
            try (ResultSet columns = metaData.getColumns(null, null, tableName.toUpperCase(), null)) {
                boolean hasTenantId = false;
                boolean hasAppId = false;
                boolean hasId = false;
                boolean hasName = false;
                
                while (columns.next()) {
                    String columnName = columns.getString("COLUMN_NAME");
                    if ("TENANT_ID".equals(columnName)) {
                        hasTenantId = true;
                    }
                    if ("APP_ID".equals(columnName)) {
                        hasAppId = true;
                    }
                    if ("ID".equals(columnName)) {
                        hasId = true;
                    }
                    if ("NAME".equals(columnName)) {
                        hasName = true;
                    }
                }
                
                assertTrue(hasTenantId, "New table should have tenant_id column");
                assertTrue(hasAppId, "New table should have app_id column");
                assertTrue(hasId, "New table should have id column");
                assertTrue(hasName, "New table should have name column");
            }
            
            // Verify index exists
            String indexName = "idx_" + tableName + "_tenant_app";
            try (ResultSet indexes = metaData.getIndexInfo(null, null, tableName.toUpperCase(), false, false)) {
                boolean hasIndex = false;
                
                while (indexes.next()) {
                    String idxName = indexes.getString("INDEX_NAME");
                    if (idxName != null && idxName.toUpperCase().contains("TENANT_APP")) {
                        hasIndex = true;
                        break;
                    }
                }
                
                assertTrue(hasIndex, "New table should have tenant_app composite index");
            }
        }
    }

    @Test
    public void testPhysicalTableNameIncludesTenantAndApp() {
        EntitySchema schema = new EntitySchema();
        schema.setName("customer");
        schema.setAppId("crm-app");
        schema.setTenantId("acme-corp");
        
        String tableName = SchemaManager.getPhysicalTableName(schema);
        
        assertTrue(tableName.contains("acme_corp"), "Table name should include tenant_id");
        assertTrue(tableName.contains("crm_app"), "Table name should include app_id");
        assertTrue(tableName.contains("customer"), "Table name should include entity name");
        
        // Expected format: app_tenantid_appid_entityname
        assertEquals("app_acme_corp_crm_app_customer", tableName);
    }

    @Test
    public void testSchemaKeyIncludesTenantAndApp() {
        EntitySchema schema = new EntitySchema();
        schema.setName("order");
        schema.setAppId("ecommerce");
        schema.setTenantId("company-x");
        
        // This is tested indirectly through saveSchema
        EntitySchema.Field idField = new EntitySchema.Field();
        idField.setName("id");
        idField.setType("long");
        idField.setPrimaryKey(true);
        
        List<EntitySchema.Field> fields = new ArrayList<>();
        fields.add(idField);
        schema.setFields(fields);
        
        // Save and load back
        SchemaManager.saveSchema(schema);
        EntitySchema loaded = SchemaManager.loadSchema("ecommerce", "order", "company-x");
        
        assertNotNull(loaded, "Should be able to load schema by app_id and tenant_id");
        assertEquals("order", loaded.getName());
        assertEquals("ecommerce", loaded.getAppId());
        assertEquals("company-x", loaded.getTenantId());
    }

    @Test
    public void testDifferentAppsSameTenantHaveSeparateTables() {
        // Create schema for app1
        EntitySchema schema1 = new EntitySchema();
        schema1.setName("product");
        schema1.setAppId("app1");
        schema1.setTenantId("tenant1");
        
        EntitySchema.Field field1 = new EntitySchema.Field();
        field1.setName("id");
        field1.setType("long");
        field1.setPrimaryKey(true);
        
        schema1.setFields(List.of(field1));
        SchemaManager.saveSchema(schema1);
        
        // Create schema for app2 (same tenant, same entity name)
        EntitySchema schema2 = new EntitySchema();
        schema2.setName("product");
        schema2.setAppId("app2");
        schema2.setTenantId("tenant1");
        
        EntitySchema.Field field2 = new EntitySchema.Field();
        field2.setName("id");
        field2.setType("long");
        field2.setPrimaryKey(true);
        
        schema2.setFields(List.of(field2));
        SchemaManager.saveSchema(schema2);
        
        // Verify they have different physical table names
        String table1 = SchemaManager.getPhysicalTableName(schema1);
        String table2 = SchemaManager.getPhysicalTableName(schema2);
        
        assertNotEquals(table1, table2, "Different apps should have different physical tables");
        assertTrue(table1.contains("app1"), "Table1 should contain app1");
        assertTrue(table2.contains("app2"), "Table2 should contain app2");
    }

    @Test
    public void testCanInsertWithTenantAndAppValues() throws SQLException {
        // Create test schema
        EntitySchema schema = new EntitySchema();
        schema.setName("test_insert_isolation");
        schema.setAppId("test-app");
        schema.setTenantId("test-tenant");
        
        EntitySchema.Field idField = new EntitySchema.Field();
        idField.setName("id");
        idField.setType("long");
        idField.setPrimaryKey(true);
        idField.setAutoIncrement(true);
        
        EntitySchema.Field dataField = new EntitySchema.Field();
        dataField.setName("data");
        dataField.setType("string");
        
        schema.setFields(List.of(idField, dataField));
        SchemaManager.saveSchema(schema);
        
        String tableName = SchemaManager.getPhysicalTableName(schema);
        
        // Insert test data
        try (Connection conn = JdbcManager.getConnection()) {
            String insertSql = "INSERT INTO \"" + tableName.toUpperCase() + "\" (\"TENANT_ID\", \"APP_ID\", \"DATA\") VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, "test-tenant");
                ps.setString(2, "test-app");
                ps.setString(3, "test data");
                int rows = ps.executeUpdate();
                assertEquals(1, rows, "Should insert 1 row");
            }
            
            // Query with tenant/app filter
            String selectSql = "SELECT \"DATA\" FROM \"" + tableName.toUpperCase() + "\" WHERE \"TENANT_ID\" = ? AND \"APP_ID\" = ?";
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setString(1, "test-tenant");
                ps.setString(2, "test-app");
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "Should find inserted row");
                    assertEquals("test data", rs.getString("DATA"));
                }
            }
        }
    }
}
