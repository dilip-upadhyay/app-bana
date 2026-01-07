package com.appbana.migration;

import com.appbana.ApiServer;
import com.appbana.JdbcManager;
import org.junit.jupiter.api.BeforeAll;
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

    @BeforeAll
    public static void setup() throws Exception {
        // Trigger migrations by starting the server (on a unique test port)
        ApiServer.startJdk(18082);
    }

    private boolean tableHasColumn(Connection conn, String tableName, String columnName) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Use ResultSetMetaData which is more reliable than DatabaseMetaData for case
            // sensitivity
            try (ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName + " WHERE 1=0")) {
                ResultSetMetaData md = rs.getMetaData();
                int count = md.getColumnCount();
                for (int i = 1; i <= count; i++) {
                    if (columnName.equalsIgnoreCase(md.getColumnName(i))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Test
    public void testSchemaTableHasTenantAndAppColumns() throws SQLException {
        try (Connection conn = JdbcManager.getConnection()) {
            assertTrue(tableHasColumn(conn, "APPBANA_SCHEMAS", "tenant_id"),
                    "appbana_schemas should have tenant_id column");
            assertTrue(tableHasColumn(conn, "APPBANA_SCHEMAS", "app_id"),
                    "appbana_schemas should have app_id column");
        }
    }

    @Test
    public void testWorkflowDefinitionTableHasTenantAndAppColumns() throws SQLException {
        try (Connection conn = JdbcManager.getConnection()) {
            assertTrue(tableHasColumn(conn, "APPBANA_WF_DEFINITION", "tenant_id"),
                    "appbana_wf_definition should have tenant_id column");
            assertTrue(tableHasColumn(conn, "APPBANA_WF_DEFINITION", "app_id"),
                    "appbana_wf_definition should have app_id column");
        }
    }

    @Test
    public void testWorkflowInstanceTableHasTenantAndAppColumns() throws SQLException {
        try (Connection conn = JdbcManager.getConnection()) {
            assertTrue(tableHasColumn(conn, "APPBANA_WF_INSTANCE", "tenant_id"),
                    "appbana_wf_instance should have tenant_id column");
            assertTrue(tableHasColumn(conn, "APPBANA_WF_INSTANCE", "app_id"),
                    "appbana_wf_instance should have app_id column");
        }
    }

    @Test
    public void testWorkflowTokenTableHasTenantAndAppColumns() throws SQLException {
        try (Connection conn = JdbcManager.getConnection()) {
            assertTrue(tableHasColumn(conn, "APPBANA_WF_TOKEN", "tenant_id"),
                    "appbana_wf_token should have tenant_id column");
            assertTrue(tableHasColumn(conn, "APPBANA_WF_TOKEN", "app_id"),
                    "appbana_wf_token should have app_id column");
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
                // H2 might verify index differently, but if column exists, we are mostly good.
                // We keep this assertion but relax if needed.
                assertTrue(hasSchemaIndex, "appbana_schemas should have idx_schema_tenant_app index");
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

            // Cleanup
            String deleteSql = "DELETE FROM appbana_schemas WHERE name = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setString(1, "test_tenant_app_query");
                ps.executeUpdate();
            }
        }
    }
}
