package com.appbana.migration;

import com.appbana.JdbcManager;
import org.junit.jupiter.api.Test;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for V10 Migration: Tenant and App Isolation
 * 
 * Verifies that:
 * 1. appbana_schemas table has tenant_id and app_id columns
 * 2. Workflow tables have tenant_id and app_id columns
 * 3. Indexes are created correctly
 */
public class V10MigrationTest {

    @Test
    public void testSchemaTableHasTenantAndAppColumns() throws SQLException {
        try (Connection conn = JdbcManager.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            
            // Check appbana_schemas table
            try (ResultSet columns = metaData.getColumns(null, null, "APPBANA_SCHEMAS", null)) {
                boolean hasTenantId = false;
                boolean hasAppId = false;
                
                while (columns.next()) {
                    String columnName = columns.getString("COLUMN_NAME");
                    if ("TENANT_ID".equals(columnName)) {
                        hasTenantId = true;
                        String columnType = columns.getString("TYPE_NAME");
                        int columnSize = columns.getInt("COLUMN_SIZE");
                        assertTrue(columnType.contains("VARCHAR") || columnType.contains("CHARACTER VARYING"), 
                            "tenant_id should be VARCHAR type, but was: " + columnType);
                        assertEquals(50, columnSize, "tenant_id should be VARCHAR(50)");
                    }
                    if ("APP_ID".equals(columnName)) {
                        hasAppId = true;
                        String columnType = columns.getString("TYPE_NAME");
                        int columnSize = columns.getInt("COLUMN_SIZE");
                        assertTrue(columnType.contains("VARCHAR") || columnType.contains("CHARACTER VARYING"), 
                            "app_id should be VARCHAR type, but was: " + columnType);
                        assertEquals(50, columnSize, "app_id should be VARCHAR(50)");
                    }
                }
                
                assertTrue(hasTenantId, "appbana_schemas should have tenant_id column");
                assertTrue(hasAppId, "appbana_schemas should have app_id column");
            }
        }
    }

    @Test
    public void testWorkflowDefinitionTableHasTenantAndAppColumns() throws SQLException {
        try (Connection conn = JdbcManager.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            
            try (ResultSet columns = metaData.getColumns(null, null, "APPBANA_WF_DEFINITION", null)) {
                boolean hasTenantId = false;
                boolean hasAppId = false;
                
                while (columns.next()) {
                    String columnName = columns.getString("COLUMN_NAME");
                    if ("TENANT_ID".equals(columnName)) {
                        hasTenantId = true;
                    }
                    if ("APP_ID".equals(columnName)) {
                        hasAppId = true;
                    }
                }
                
                assertTrue(hasTenantId, "appbana_wf_definition should have tenant_id column");
                assertTrue(hasAppId, "appbana_wf_definition should have app_id column");
            }
        }
    }

    @Test
    public void testWorkflowInstanceTableHasTenantAndAppColumns() throws SQLException {
        try (Connection conn = JdbcManager.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            
            try (ResultSet columns = metaData.getColumns(null, null, "APPBANA_WF_INSTANCE", null)) {
                boolean hasTenantId = false;
                boolean hasAppId = false;
                
                while (columns.next()) {
                    String columnName = columns.getString("COLUMN_NAME");
                    if ("TENANT_ID".equals(columnName)) {
                        hasTenantId = true;
                    }
                    if ("APP_ID".equals(columnName)) {
                        hasAppId = true;
                    }
                }
                
                assertTrue(hasTenantId, "appbana_wf_instance should have tenant_id column");
                assertTrue(hasAppId, "appbana_wf_instance should have app_id column");
            }
        }
    }

    @Test
    public void testWorkflowTokenTableHasTenantAndAppColumns() throws SQLException {
        try (Connection conn = JdbcManager.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            
            try (ResultSet columns = metaData.getColumns(null, null, "APPBANA_WF_TOKEN", null)) {
                boolean hasTenantId = false;
                boolean hasAppId = false;
                
                while (columns.next()) {
                    String columnName = columns.getString("COLUMN_NAME");
                    if ("TENANT_ID".equals(columnName)) {
                        hasTenantId = true;
                    }
                    if ("APP_ID".equals(columnName)) {
                        hasAppId = true;
                    }
                }
                
                assertTrue(hasTenantId, "appbana_wf_token should have tenant_id column");
                assertTrue(hasAppId, "appbana_wf_token should have app_id column");
            }
        }
    }

    @Test
    public void testIndexesExist() throws SQLException {
        try (Connection conn = JdbcManager.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            
            // Check appbana_schemas index
            try (ResultSet indexes = metaData.getIndexInfo(null, null, "APPBANA_SCHEMAS", false, false)) {
                boolean hasSchemaIndex = false;
                
                while (indexes.next()) {
                    String indexName = indexes.getString("INDEX_NAME");
                    if (indexName != null && indexName.toUpperCase().contains("SCHEMA_TENANT_APP")) {
                        hasSchemaIndex = true;
                        break;
                    }
                }
                
                assertTrue(hasSchemaIndex, "appbana_schemas should have idx_schema_tenant_app index");
            }
            
            // Check workflow definition index
            try (ResultSet indexes = metaData.getIndexInfo(null, null, "APPBANA_WF_DEFINITION", false, false)) {
                boolean hasWfDefIndex = false;
                
                while (indexes.next()) {
                    String indexName = indexes.getString("INDEX_NAME");
                    if (indexName != null && indexName.toUpperCase().contains("WF_DEF_TENANT_APP")) {
                        hasWfDefIndex = true;
                        break;
                    }
                }
                
                assertTrue(hasWfDefIndex, "appbana_wf_definition should have idx_wf_def_tenant_app index");
            }
        }
    }

    @Test
    public void testTenantAndAppColumnsAreRequired() throws SQLException {
        try (Connection conn = JdbcManager.getConnection()) {
            // Try to insert a row into appbana_schemas without tenant_id/app_id
            // This should FAIL because columns are NOT NULL with no defaults
            String insertSql = "INSERT INTO appbana_schemas (name, json) VALUES (?, ?)";
            
            assertThrows(SQLException.class, () -> {
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setString(1, "test_migration_schema_fail");
                    ps.setString(2, "{\"name\":\"test\"}");
                    ps.executeUpdate();
                }
            }, "Insert without tenant_id/app_id should fail (NOT NULL constraint)");
            
            // Now insert with tenant_id and app_id - should succeed
            String insertWithTenantSql = "INSERT INTO appbana_schemas (name, json, tenant_id, app_id) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertWithTenantSql)) {
                ps.setString(1, "test_migration_schema_success");
                ps.setString(2, "{\"name\":\"test\"}");
                ps.setString(3, "tenant-123");
                ps.setString(4, "app-456");
                int rows = ps.executeUpdate();
                assertEquals(1, rows, "Insert with tenant_id/app_id should succeed");
            }
            
            // Verify inserted values
            String selectSql = "SELECT tenant_id, app_id FROM appbana_schemas WHERE name = ?";
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setString(1, "test_migration_schema_success");
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "Should find the inserted row");
                    String tenantId = rs.getString("tenant_id");
                    String appId = rs.getString("app_id");
                    
                    assertEquals("tenant-123", tenantId, "tenant_id should be 'tenant-123'");
                    assertEquals("app-456", appId, "app_id should be 'app-456'");
                }
            }
            
            // Cleanup
            String deleteSql = "DELETE FROM appbana_schemas WHERE name = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setString(1, "test_migration_schema_success");
                ps.executeUpdate();
            }
        }
    }

    @Test
    public void testCanQueryByTenantAndApp() throws SQLException {
        try (Connection conn = JdbcManager.getConnection()) {
            // Insert test data with specific tenant and app
            String insertSql = "INSERT INTO appbana_schemas (name, json, tenant_id, app_id) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, "test_tenant_app_query");
                ps.setString(2, "{\"name\":\"test\"}");
                ps.setString(3, "tenant-123");
                ps.setString(4, "app-456");
                ps.executeUpdate();
            }
            
            // Query by tenant and app
            String selectSql = "SELECT name FROM appbana_schemas WHERE tenant_id = ? AND app_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setString(1, "tenant-123");
                ps.setString(2, "app-456");
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "Should find row by tenant_id and app_id");
                    assertEquals("test_tenant_app_query", rs.getString("name"));
                }
            }
            
            // Verify other tenant/app cannot see this data
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setString(1, "other-tenant");
                ps.setString(2, "other-app");
                try (ResultSet rs = ps.executeQuery()) {
                    assertFalse(rs.next(), "Should not find row for different tenant/app");
                }
            }
            
            // Cleanup
            String deleteSql = "DELETE FROM appbana_schemas WHERE name = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setString(1, "test_tenant_app_query");
                ps.executeUpdate();
            }
        }
    }
}
