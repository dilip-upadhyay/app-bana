package com.appbana.service;

import com.appbana.JdbcManager;
import com.appbana.model.EntitySchema;
import com.appbana.model.TenantContext;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for EntityCrudService with TenantContext support
 * 
 * Tests both context-aware (multi-tenant) and legacy (backward compatible) methods
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class EntityCrudServiceTest {
    
    private EntityCrudService service;
    private EntitySchema testSchema;
    private TenantContext tenant1AppA;
    private TenantContext tenant1AppB;
    private TenantContext tenant2AppA;
    
    @BeforeAll
    void setupDatabase() throws SQLException {
        service = new EntityCrudService();
        
        // Create contexts
        tenant1AppA = new TenantContext("tenant-1", "app-a");
        tenant1AppB = new TenantContext("tenant-1", "app-b");
        tenant2AppA = new TenantContext("tenant-2", "app-a");
        
        // Create test schema
        testSchema = new EntitySchema();
        testSchema.setName("test_users");
        testSchema.setDatasourceName(null); // default H2
        
        List<EntitySchema.Field> fields = new ArrayList<>();
        
        // Primary key
        EntitySchema.Field id = new EntitySchema.Field();
        id.setName("id");
        id.setType("long");
        id.setPrimaryKey(true);
        id.setAutoIncrement(true);
        fields.add(id);
        
        // Tenant isolation fields
        EntitySchema.Field tenantId = new EntitySchema.Field();
        tenantId.setName("tenant_id");
        tenantId.setType("string");
        tenantId.setLength(50);
        tenantId.setRequired(true);
        fields.add(tenantId);
        
        EntitySchema.Field appId = new EntitySchema.Field();
        appId.setName("app_id");
        appId.setType("string");
        appId.setLength(50);
        appId.setRequired(true);
        fields.add(appId);
        
        // Data fields
        EntitySchema.Field name = new EntitySchema.Field();
        name.setName("name");
        name.setType("string");
        name.setLength(100);
        name.setRequired(true);
        fields.add(name);
        
        EntitySchema.Field email = new EntitySchema.Field();
        email.setName("email");
        email.setType("string");
        email.setLength(100);
        fields.add(email);
        
        EntitySchema.Field age = new EntitySchema.Field();
        age.setName("age");
        age.setType("int");
        fields.add(age);
        
        testSchema.setFields(fields);
        
        // Create table
        try (Connection conn = JdbcManager.getConnection(null);
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS \"TEST_USERS\"");
            stmt.execute("""
                CREATE TABLE "TEST_USERS" (
                    "ID" BIGINT AUTO_INCREMENT PRIMARY KEY,
                    "TENANT_ID" VARCHAR(50) NOT NULL,
                    "APP_ID" VARCHAR(50) NOT NULL,
                    "NAME" VARCHAR(100) NOT NULL,
                    "EMAIL" VARCHAR(100),
                    "AGE" INT
                )
            """);
        }
    }
    
    @AfterEach
    void cleanupData() throws SQLException {
        try (Connection conn = JdbcManager.getConnection(null);
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM \"TEST_USERS\"");
        }
    }
    
    @AfterAll
    void teardownDatabase() throws SQLException {
        try (Connection conn = JdbcManager.getConnection(null);
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS \"TEST_USERS\"");
        }
    }
    
    // ==================== Context-Aware Insert Tests ====================
    
    @Test
    @DisplayName("Should insert record with tenant/app context")
    void testInsertWithContext() throws SQLException {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "Alice");
        data.put("email", "alice@example.com");
        data.put("age", 30);
        
        Object id = service.insertRecord(tenant1AppA, testSchema, data);
        
        assertNotNull(id);
        assertTrue(id instanceof Long || id instanceof Integer);
    }
    
    @Test
    @DisplayName("Should auto-inject tenant_id and app_id on insert")
    void testAutoInjectTenantAndApp() throws SQLException {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "Bob");
        data.put("email", "bob@example.com");
        
        Object id = service.insertRecord(tenant1AppA, testSchema, data);
        
        // Verify data was inserted with correct tenant/app
        Map<String, Object> retrieved = service.getById(tenant1AppA, testSchema, id.toString());
        assertNotNull(retrieved);
        assertEquals("tenant-1", retrieved.get("TENANT_ID"));
        assertEquals("app-a", retrieved.get("APP_ID"));
        assertEquals("Bob", retrieved.get("NAME"));
    }
    
    @Test
    @DisplayName("Should throw exception when context is null on insert")
    void testInsertRequiresContext() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "Charlie");
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> service.insertRecord(null, testSchema, data)
        );
        
        assertTrue(exception.getMessage().contains("TenantContext is required"));
    }
    
    // ==================== Context-Aware List Tests ====================
    
    @Test
    @DisplayName("Should list only records for specific tenant/app")
    void testListAllWithIsolation() throws SQLException {
        // Insert records for different tenants/apps
        insertRecord(tenant1AppA, "Alice");
        insertRecord(tenant1AppA, "Bob");
        insertRecord(tenant1AppB, "Charlie");
        insertRecord(tenant2AppA, "David");
        
        // List for tenant1/app-a
        List<Map<String, Object>> tenant1AppARecords = service.listAll(tenant1AppA, testSchema);
        assertEquals(2, tenant1AppARecords.size());
        Set<String> names1A = extractNames(tenant1AppARecords);
        assertTrue(names1A.contains("Alice"));
        assertTrue(names1A.contains("Bob"));
        
        // List for tenant1/app-b
        List<Map<String, Object>> tenant1AppBRecords = service.listAll(tenant1AppB, testSchema);
        assertEquals(1, tenant1AppBRecords.size());
        assertEquals("Charlie", tenant1AppBRecords.get(0).get("NAME"));
        
        // List for tenant2/app-a
        List<Map<String, Object>> tenant2AppARecords = service.listAll(tenant2AppA, testSchema);
        assertEquals(1, tenant2AppARecords.size());
        assertEquals("David", tenant2AppARecords.get(0).get("NAME"));
    }
    
    @Test
    @DisplayName("Should throw exception when context is null on list")
    void testListRequiresContext() {
        assertThrows(
            IllegalArgumentException.class,
            () -> service.listAll(null, testSchema)
        );
    }
    
    // ==================== Context-Aware GetById Tests ====================
    
    @Test
    @DisplayName("Should get record by ID within tenant/app scope")
    void testGetByIdWithIsolation() throws SQLException {
        // Insert records in different contexts
        Object id1 = insertRecord(tenant1AppA, "Alice");
        Object id2 = insertRecord(tenant1AppB, "Bob");
        
        // Get from correct context
        Map<String, Object> alice = service.getById(tenant1AppA, testSchema, id1.toString());
        assertNotNull(alice);
        assertEquals("Alice", alice.get("NAME"));
        assertEquals("tenant-1", alice.get("TENANT_ID"));
        assertEquals("app-a", alice.get("APP_ID"));
        
        // Try to get from wrong context (should return null - isolation)
        Map<String, Object> wrongContext = service.getById(tenant1AppB, testSchema, id1.toString());
        assertNull(wrongContext, "Should not retrieve record from different app context");
    }
    
    @Test
    @DisplayName("Should return null when record doesn't exist in context")
    void testGetByIdNotFound() throws SQLException {
        Map<String, Object> result = service.getById(tenant1AppA, testSchema, "99999");
        assertNull(result);
    }
    
    // ==================== Context-Aware Update Tests ====================
    
    @Test
    @DisplayName("Should update record within tenant/app scope")
    void testUpdateByIdWithIsolation() throws SQLException {
        // Insert record
        Object id = insertRecord(tenant1AppA, "Alice");
        
        // Update record
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", "Alice Updated");
        updates.put("age", 31);
        
        int rowsUpdated = service.updateById(tenant1AppA, testSchema, id.toString(), updates);
        assertEquals(1, rowsUpdated);
        
        // Verify update
        Map<String, Object> updated = service.getById(tenant1AppA, testSchema, id.toString());
        assertEquals("Alice Updated", updated.get("NAME"));
        assertEquals(31, updated.get("AGE"));
    }
    
    @Test
    @DisplayName("Should not update record from different context")
    void testUpdateIsolation() throws SQLException {
        // Insert record in app-a
        Object id = insertRecord(tenant1AppA, "Alice");
        
        // Try to update from app-b (should fail - wrong context)
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", "Alice Hacked");
        
        int rowsUpdated = service.updateById(tenant1AppB, testSchema, id.toString(), updates);
        assertEquals(0, rowsUpdated, "Should not update record from different context");
        
        // Verify original record unchanged
        Map<String, Object> original = service.getById(tenant1AppA, testSchema, id.toString());
        assertEquals("Alice", original.get("NAME"));
    }
    
    @Test
    @DisplayName("Should not allow updating tenant_id or app_id")
    void testCannotUpdateIsolationFields() throws SQLException {
        // Insert record
        Object id = insertRecord(tenant1AppA, "Alice");
        
        // Try to update tenant_id and app_id (should be ignored)
        Map<String, Object> maliciousUpdates = new HashMap<>();
        maliciousUpdates.put("name", "Alice Updated");
        maliciousUpdates.put("tenant_id", "hacker-tenant");
        maliciousUpdates.put("app_id", "hacker-app");
        
        service.updateById(tenant1AppA, testSchema, id.toString(), maliciousUpdates);
        
        // Verify isolation fields were NOT changed
        Map<String, Object> record = service.getById(tenant1AppA, testSchema, id.toString());
        assertEquals("tenant-1", record.get("TENANT_ID"));
        assertEquals("app-a", record.get("APP_ID"));
        assertEquals("Alice Updated", record.get("NAME")); // Name should be updated
    }
    
    // ==================== Context-Aware Delete Tests ====================
    
    @Test
    @DisplayName("Should delete record within tenant/app scope")
    void testDeleteByIdWithIsolation() throws SQLException {
        // Insert record
        Object id = insertRecord(tenant1AppA, "Alice");
        
        // Delete record
        int rowsDeleted = service.deleteById(tenant1AppA, testSchema, id.toString());
        assertEquals(1, rowsDeleted);
        
        // Verify deleted
        Map<String, Object> deleted = service.getById(tenant1AppA, testSchema, id.toString());
        assertNull(deleted);
    }
    
    @Test
    @DisplayName("Should not delete record from different context")
    void testDeleteIsolation() throws SQLException {
        // Insert record in app-a
        Object id = insertRecord(tenant1AppA, "Alice");
        
        // Try to delete from app-b (should fail - wrong context)
        int rowsDeleted = service.deleteById(tenant1AppB, testSchema, id.toString());
        assertEquals(0, rowsDeleted, "Should not delete record from different context");
        
        // Verify record still exists
        Map<String, Object> record = service.getById(tenant1AppA, testSchema, id.toString());
        assertNotNull(record);
        assertEquals("Alice", record.get("NAME"));
    }
    
    // ==================== Backward Compatibility Tests ====================
    
    @Test
    @DisplayName("Should maintain backward compatibility for insertRecord without context")
    void testLegacyInsertStillWorks() throws SQLException {
        Map<String, Object> data = new HashMap<>();
        data.put("tenant_id", "legacy-tenant");
        data.put("app_id", "legacy-app");
        data.put("name", "Legacy User");
        data.put("email", "legacy@example.com");
        
        Object id = service.insertRecord(testSchema, data);
        assertNotNull(id);
    }
    
    @Test
    @DisplayName("Should maintain backward compatibility for listAll without context")
    void testLegacyListStillWorks() throws SQLException {
        // Insert using legacy method
        Map<String, Object> data = new HashMap<>();
        data.put("tenant_id", "legacy-tenant");
        data.put("app_id", "legacy-app");
        data.put("name", "Legacy User");
        
        service.insertRecord(testSchema, data);
        
        // List using legacy method (returns all records without filtering)
        List<Map<String, Object>> all = service.listAll(testSchema);
        assertNotNull(all);
        assertTrue(all.size() >= 1);
    }
    
    // ==================== Helper Methods ====================
    
    private Object insertRecord(TenantContext context, String name) throws SQLException {
        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("email", name.toLowerCase() + "@example.com");
        return service.insertRecord(context, testSchema, data);
    }
    
    private Set<String> extractNames(List<Map<String, Object>> records) {
        Set<String> names = new HashSet<>();
        for (Map<String, Object> record : records) {
            names.add((String) record.get("NAME"));
        }
        return names;
    }
}
