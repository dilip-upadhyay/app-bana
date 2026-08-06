package com.appbana.service;

import com.appbana.JdbcManager;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for PermissionService - Field-Level Security (FLS)
 *
 * <p><b>S3.8:</b> ported off H2 + a commented-out Flyway import onto the same live dev Postgres
 * every other integration test in this suite already uses ({@link JdbcManager}, {@code "default"}
 * datasource). Gutted by the H2→PostgreSQL migration and left permanently disabled via
 * {@code Assumptions.assumeTrue(false, ...)} in {@code @BeforeAll} — which made Surefire report
 * {@code Tests run: 0} for the whole class (not even "skipped"), silently since. No Testcontainers
 * adopted (S2.11's calibration: this module has no Testcontainers dependency, and S3.8's own
 * decision is independent either way).
 *
 * <p>Roles/field permissions are NOT fixture data here: {@code V1__auth_schema.sql} and
 * {@code V2__field_level_security.sql} already seed the admin/manager/user/hr roles and their
 * {@code field_permission} rows on every environment (a genuinely fresh one too, per S2.11's own
 * proof) — re-declaring that seed data in a test fixture would be a second, driftable source of
 * truth for it, the exact pattern that kept an approval-column defect invisible across 281 green
 * tests elsewhere in this project. This test only manages its own fixture *users* (fixture-prefixed
 * {@code s38-}) and their {@code user_role} grants; deleting a {@code "user"} row cascades to
 * {@code user_role} (V1's {@code ON DELETE CASCADE}), so no separate {@code user_role} cleanup is
 * needed. The one exception is Scenario 2 below, which inserts and removes its own single
 * {@code field_permission} row under a fixture-only {@code entity_name} that can never collide with
 * real data — see that test for why.
 *
 * Tests 8 critical scenarios:
 * 1. Admin bypass - admins see all fields
 * 2. Wildcard permissions - role with "*" field permission
 * 3. Explicit field permissions - specific field access
 * 4. Multi-role OR logic - user with multiple roles
 * 5. Deny by default - no permission = no access
 * 6. Cache functionality - permissions cached for 5 minutes
 * 7. Performance - <50ms for permission checks
 * 8. validateEditableFields - throws SecurityException for non-editable fields
 *
 * @author AppBana Team
 * @since 1.0
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PermissionServiceTest {
    
    private static PermissionService permissionService;
    
    private static final String ADMIN_USER_ID = "s38-admin-user";
    private static final String MANAGER_USER_ID = "s38-manager-user";
    private static final String STANDARD_USER_ID = "s38-standard-user";
    private static final String HR_USER_ID = "s38-hr-user";
    private static final String NO_ROLE_USER_ID = "s38-no-role-user";
    private static final String MULTI_ROLE_USER_ID = "s38-multi-role-user";

    /** Fixture-only entity name for Scenario 2's genuine (non-admin-bypass) wildcard-row probe. */
    private static final String WILDCARD_PROBE_ENTITY = "S38WildcardProbeEntity";

    private static final List<String> FIXTURE_USER_IDS = List.of(
            ADMIN_USER_ID, MANAGER_USER_ID, STANDARD_USER_ID, HR_USER_ID, NO_ROLE_USER_ID, MULTI_ROLE_USER_ID);
    
    @BeforeAll
    static void setupDatabase() throws SQLException {
        // Pre-clean in case a previous crashed run left fixture rows behind (matches the
        // cascade-delete-then-recreate pattern used by AppAuthorizationTest/AppMembershipServiceTest).
        cleanupFixtureData();
        createTestUsers();
        permissionService = new PermissionService(JdbcManager.getDataSource("default"));
    }
    
    @AfterAll
    static void tearDown() throws SQLException {
        cleanupFixtureData();
    }
    
    @BeforeEach
    void clearCache() {
        // Clear cache before each test for consistency
        permissionService.clearAllCaches();
    }

    /**
     * Removes every fixture row this class can create: the {@code s38-*} users (cascades to their
     * {@code user_role} grants) and the Scenario 2 wildcard probe's {@code field_permission} row,
     * scoped by its fixture-only {@code entity_name} so this can never touch real data.
     */
    private static void cleanupFixtureData() throws SQLException {
        try (Connection conn = JdbcManager.getConnection("default");
             Statement stmt = conn.createStatement()) {
            String ids = FIXTURE_USER_IDS.stream()
                    .map(id -> "'" + id + "'")
                    .collect(Collectors.joining(","));
            stmt.execute("DELETE FROM \"user\" WHERE id IN (" + ids + ")");
            stmt.execute("DELETE FROM field_permission WHERE entity_name = '" + WILDCARD_PROBE_ENTITY + "'");
        }
    }
    
    /**
     * Create test users and assign them to roles ALREADY seeded by V1__auth_schema.sql /
     * V2__field_level_security.sql (admin/manager/user/hr).
     */
    private static void createTestUsers() throws SQLException {
        try (Connection conn = JdbcManager.getConnection("default")) {
            // Get role IDs from seed data
            String adminRoleId = getRoleIdByName(conn, "admin");
            String managerRoleId = getRoleIdByName(conn, "manager");
            String userRoleId = getRoleIdByName(conn, "user");
            String hrRoleId = getRoleIdByName(conn, "hr");
            
            createUser(conn, ADMIN_USER_ID, "s38-admin@test.com", "S3.8 Test Admin");
            createUser(conn, MANAGER_USER_ID, "s38-manager@test.com", "S3.8 Test Manager");
            createUser(conn, STANDARD_USER_ID, "s38-user@test.com", "S3.8 Test User");
            createUser(conn, HR_USER_ID, "s38-hr@test.com", "S3.8 Test HR");
            createUser(conn, NO_ROLE_USER_ID, "s38-norole@test.com", "S3.8 Test No Role");
            
            // Assign roles to test users
            assignRole(conn, ADMIN_USER_ID, adminRoleId);
            assignRole(conn, MANAGER_USER_ID, managerRoleId);
            assignRole(conn, STANDARD_USER_ID, userRoleId);
            assignRole(conn, HR_USER_ID, hrRoleId);
            // No role assigned to NO_ROLE_USER_ID (test deny by default)
        }
    }
    
    private static String getRoleIdByName(Connection conn, String roleName) throws SQLException {
        String sql = "SELECT id FROM role WHERE name = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, roleName);
            var rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("id");
            }
        }
        throw new IllegalStateException(
                "Role not found: " + roleName + " -- expected to be seeded by "
                        + "V1__auth_schema.sql/V2__field_level_security.sql");
    }
    
    private static void createUser(Connection conn, String id, String email, String name) throws SQLException {
        String sql = "INSERT INTO \"user\" (id, email, password_hash, name, status, created_at, updated_at) " +
                     "VALUES (?, ?, '$2a$10$dummy.hash', ?, 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
                     "ON CONFLICT (id) DO NOTHING";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            stmt.setString(2, email);
            stmt.setString(3, name);
            stmt.executeUpdate();
        }
    }
    
    private static void assignRole(Connection conn, String userId, String roleId) throws SQLException {
        String sql = "INSERT INTO user_role (user_id, role_id) VALUES (?, ?) ON CONFLICT (user_id, role_id) DO NOTHING";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            stmt.setString(2, roleId);
            stmt.executeUpdate();
        }
    }

    private static void insertFieldPermission(Connection conn, String roleId, String entityName, String fieldName,
                                               boolean canRead, boolean canEdit) throws SQLException {
        String sql = "INSERT INTO field_permission (id, role_id, entity_name, field_name, can_read, can_edit) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, UUID.randomUUID().toString());
            stmt.setString(2, roleId);
            stmt.setString(3, entityName);
            stmt.setString(4, fieldName);
            stmt.setBoolean(5, canRead);
            stmt.setBoolean(6, canEdit);
            stmt.executeUpdate();
        }
    }

    private static void deleteFieldPermissionFixture(String roleId, String entityName) throws SQLException {
        try (Connection conn = JdbcManager.getConnection("default");
             PreparedStatement stmt = conn.prepareStatement(
                     "DELETE FROM field_permission WHERE role_id = ? AND entity_name = ?")) {
            stmt.setString(1, roleId);
            stmt.setString(2, entityName);
            stmt.executeUpdate();
        }
    }
    
    // ========== TEST SCENARIOS ==========
    
    /**
     * Scenario 1: Admin Bypass
     * Admins should have access to all fields regardless of field_permission records
     */
    @Test
    @Order(1)
    @DisplayName("1. Admin Bypass - Admins access all fields")
    void testAdminBypass() {
        // Admin should be able to read ANY field (bypass FLS)
        assertTrue(permissionService.canReadField(ADMIN_USER_ID, "User", "salary"), 
                   "Admin should read salary field");
        assertTrue(permissionService.canReadField(ADMIN_USER_ID, "User", "ssn"), 
                   "Admin should read ssn field");
        assertTrue(permissionService.canReadField(ADMIN_USER_ID, "User", "email"), 
                   "Admin should read email field");
        
        // Admin should be able to edit ANY field (bypass FLS)
        assertTrue(permissionService.canEditField(ADMIN_USER_ID, "User", "salary"), 
                   "Admin should edit salary field");
        assertTrue(permissionService.canEditField(ADMIN_USER_ID, "User", "ssn"), 
                   "Admin should edit ssn field");
        
        // getReadableFields should return wildcard "*"
        List<String> readableFields = permissionService.getReadableFields(ADMIN_USER_ID, "User");
        assertTrue(readableFields.contains("*"), 
                   "Admin readable fields should contain wildcard '*'");
        
        // filterReadableFields should return all fields for admin
        Map<String, Object> userData = new HashMap<>();
        userData.put("id", "123");
        userData.put("email", "test@example.com");
        userData.put("salary", 100000);
        userData.put("ssn", "123-45-6789");
        
        Map<String, Object> filtered = permissionService.filterReadableFields(ADMIN_USER_ID, "User", userData);
        assertEquals(4, filtered.size(), "Admin should see all 4 fields");
        assertTrue(filtered.containsKey("salary"), "Admin should see salary");
        assertTrue(filtered.containsKey("ssn"), "Admin should see ssn");
    }
    
    /**
     * Scenario 2: Wildcard Permissions
     * Role with "*" field permission should access all fields
     *
     * <p>Corrected during the S3.8 port: the original comment claimed "Manager role has wildcard
     * permissions in seed data" — false for the real V1/V2 seed (manager's 5 rows in
     * {@code V2__field_level_security.sql} are all explicit field names; only {@code admin} has a
     * {@code field_name = '*'} row). And {@code admin}'s wildcard row is never actually queried
     * either — {@link PermissionService#getReadableFields} short-circuits via {@code isAdmin()}
     * before touching {@code field_permission} at all (see {@code testAdminBypass}). So no test in
     * this class previously exercised {@code queryFieldPermission}'s
     * {@code (fp.field_name = ? OR fp.field_name = '*')} OR-clause for a non-bypassed role — this
     * scenario now does, via a temporary wildcard row on a fixture-only entity name.
     */
    @Test
    @Order(2)
    @DisplayName("2. Wildcard Permissions - '*' grants all field access")
    void testWildcardPermissions() throws SQLException {
        // Manager's explicit (non-wildcard) permissions from seed data.
        List<String> readableFields = permissionService.getReadableFields(MANAGER_USER_ID, "User");
        assertFalse(readableFields.isEmpty(), "Manager should have readable fields");
        
        boolean canReadEmail = permissionService.canReadField(MANAGER_USER_ID, "User", "email");
        assertTrue(canReadEmail, "Manager should read email via explicit permission");

        // Genuine wildcard-row probe: grant the STANDARD (non-admin) role a '*' field_permission
        // on a fixture-only entity, then assert it grants read access to a field it never names.
        String userRoleId;
        try (Connection conn = JdbcManager.getConnection("default")) {
            userRoleId = getRoleIdByName(conn, "user");
            insertFieldPermission(conn, userRoleId, WILDCARD_PROBE_ENTITY, "*", true, false);
        }
        try {
            permissionService.clearAllCaches();
            assertTrue(permissionService.canReadField(STANDARD_USER_ID, WILDCARD_PROBE_ENTITY, "literally_any_field"),
                    "A '*' field_permission row must grant read access to a field it never explicitly names");
            assertFalse(permissionService.canEditField(STANDARD_USER_ID, WILDCARD_PROBE_ENTITY, "literally_any_field"),
                    "This probe row grants can_read only -- can_edit must stay false for the same wildcard row");
        } finally {
            deleteFieldPermissionFixture(userRoleId, WILDCARD_PROBE_ENTITY);
        }
    }
    
    /**
     * Scenario 3: Explicit Field Permissions
     * User with specific field permission should access only that field
     */
    @Test
    @Order(3)
    @DisplayName("3. Explicit Permissions - Specific field access")
    void testExplicitFieldPermissions() {
        // HR role has explicit permissions for salary field (from seed data)
        assertTrue(permissionService.canReadField(HR_USER_ID, "User", "salary"), 
                   "HR should read salary field");
        assertTrue(permissionService.canEditField(HR_USER_ID, "User", "salary"), 
                   "HR should edit salary field");
        
        // HR should see salary in readable/editable fields list
        List<String> readableFields = permissionService.getReadableFields(HR_USER_ID, "User");
        assertTrue(readableFields.contains("salary") || readableFields.contains("*"), 
                   "HR readable fields should include salary or wildcard");
        
        List<String> editableFields = permissionService.getEditableFields(HR_USER_ID, "User");
        assertTrue(editableFields.contains("salary") || editableFields.contains("*"), 
                   "HR editable fields should include salary or wildcard");
    }
    
    /**
     * Scenario 4: Multi-Role OR Logic
     * User with multiple roles should have union of permissions (OR logic)
     *
     * <p>Strengthened during the S3.8 port: the original assertion was only
     * {@code assertFalse(readableFields.isEmpty())}, which would pass even if the union collapsed
     * to just ONE of the two roles (or even an AND instead of OR) — it never proved genuine union.
     * 'phone' is granted ONLY by the 'user' role and 'salary' ONLY by the 'hr' role
     * ({@code V2__field_level_security.sql}); both being present can only happen if permissions from
     * BOTH roles are actually combined. Break-tested (temporarily skipped the 'hr' role grant below,
     * confirmed the 'salary' assertion fails with the exact expected message, then restored it).
     */
    @Test
    @Order(4)
    @DisplayName("4. Multi-Role OR - Union of permissions")
    void testMultiRoleOrLogic() throws SQLException {
        // Create a user with both 'user' and 'hr' roles
        try (Connection conn = JdbcManager.getConnection("default")) {
            createUser(conn, MULTI_ROLE_USER_ID, "s38-multirole@test.com", "S3.8 Multi Role User");
            String userRoleId = getRoleIdByName(conn, "user");
            String hrRoleId = getRoleIdByName(conn, "hr");
            assignRole(conn, MULTI_ROLE_USER_ID, userRoleId);
            assignRole(conn, MULTI_ROLE_USER_ID, hrRoleId);
        }
        
        List<String> readableFields = permissionService.getReadableFields(MULTI_ROLE_USER_ID, "User");
        assertTrue(readableFields.contains("phone"),
                   "Multi-role user should read 'phone' (granted ONLY by the 'user' role) "
                           + "- proves the 'user' role's grant is included in the union");
        assertTrue(readableFields.contains("salary"),
                   "Multi-role user should read 'salary' (granted ONLY by the 'hr' role) "
                           + "- proves the 'hr' role's grant is included in the union");
    }
    
    /**
     * Scenario 5: Deny by Default
     * User without field permission should NOT access field
     */
    @Test
    @Order(5)
    @DisplayName("5. Deny by Default - No permission = no access")
    void testDenyByDefault() {
        // User with no role assignments should not access any fields
        assertFalse(permissionService.canReadField(NO_ROLE_USER_ID, "User", "salary"), 
                    "User without role should NOT read salary");
        assertFalse(permissionService.canEditField(NO_ROLE_USER_ID, "User", "salary"), 
                    "User without role should NOT edit salary");
        
        // getReadableFields should return empty list
        List<String> readableFields = permissionService.getReadableFields(NO_ROLE_USER_ID, "User");
        assertTrue(readableFields.isEmpty(), 
                   "User without role should have empty readable fields list");
        
        // filterReadableFields should return only 'id' field (always included)
        Map<String, Object> userData = new HashMap<>();
        userData.put("id", "123");
        userData.put("email", "test@example.com");
        userData.put("salary", 100000);
        
        Map<String, Object> filtered = permissionService.filterReadableFields(NO_ROLE_USER_ID, "User", userData);
        assertEquals(1, filtered.size(), "User without role should only see 'id' field");
        assertTrue(filtered.containsKey("id"), "Filtered data should always include 'id'");
        assertFalse(filtered.containsKey("salary"), "User without role should NOT see salary");
    }
    
    /**
     * Scenario 6: Cache Functionality
     * Permissions should be cached for 5 minutes to reduce database queries
     */
    @Test
    @Order(6)
    @DisplayName("6. Cache - Permissions cached for performance")
    void testCacheFunctionality() {
        // First call - should query database
        long start1 = System.nanoTime();
        boolean result1 = permissionService.canReadField(MANAGER_USER_ID, "User", "email");
        long duration1 = System.nanoTime() - start1;
        
        // Second call - should use cache (much faster)
        long start2 = System.nanoTime();
        boolean result2 = permissionService.canReadField(MANAGER_USER_ID, "User", "email");
        long duration2 = System.nanoTime() - start2;
        
        // Results should be consistent
        assertEquals(result1, result2, "Cached result should match database result");
        
        // Second call should be significantly faster (cache hit)
        // Typically: DB query ~1-10ms, cache ~0.001ms (1000x faster)
        assertTrue(duration2 < duration1, 
                   "Cached call should be faster than database call");
        
        // Verify clearCache works
        permissionService.clearCache(MANAGER_USER_ID);
        boolean result3 = permissionService.canReadField(MANAGER_USER_ID, "User", "email");
        assertEquals(result1, result3, "Result after cache clear should still be consistent");
    }
    
    /**
     * Scenario 7: Performance
     * Permission checks should be fast (<50ms for cold start, <20ms for cached)
     *
     * <p>S3.8 port note: the original H2-based threshold for the "cached" call was
     * &lt;1ms. That is not achievable against a real networked Postgres connection
     * regardless of cache correctness: {@code canReadField()}/{@code canEditField()}
     * unconditionally call {@code isAdmin()} first on every invocation — a live,
     * uncached SQL round-trip — before ever consulting the in-memory field-permission
     * cache (verified by reading {@code getCachedPermission()}/{@code cachePermission()},
     * which are pure {@code HashMap} operations with no I/O). Caching the admin bit
     * itself is a separate, security-sensitive design decision (an admin's role
     * revocation would not take effect until the cache entry expired) that is out of
     * scope here, so this threshold is relaxed to a value that still proves the field
     * -permission cache is doing real work (well under the 50ms cold budget, which
     * pays for two DB round-trips) without asserting a latency floor no networked
     * database call can consistently meet. Observed locally: ~3ms.</p>
     */
    @Test
    @Order(7)
    @DisplayName("7. Performance - Permission checks under 50ms")
    void testPerformance() {
        permissionService.clearCache(STANDARD_USER_ID);
        
        // Cold start (database query) - should be <50ms
        long startCold = System.nanoTime();
        permissionService.canReadField(STANDARD_USER_ID, "User", "email");
        long durationColdNs = System.nanoTime() - startCold;
        double durationColdMs = durationColdNs / 1_000_000.0;
        
        assertTrue(durationColdMs < 50, 
                   String.format("Cold permission check should be <50ms (was %.2fms)", durationColdMs));
        
        // Cached field-permission lookup, but isAdmin() still queries live each call - should be <20ms
        long startCached = System.nanoTime();
        permissionService.canReadField(STANDARD_USER_ID, "User", "email");
        long durationCachedNs = System.nanoTime() - startCached;
        double durationCachedMs = durationCachedNs / 1_000_000.0;
        
        assertTrue(durationCachedMs < 20, 
                   String.format("Cached permission check should be <20ms (was %.2fms)", durationCachedMs));
        
        // Batch operations - get all readable fields should be <100ms
        long startBatch = System.nanoTime();
        List<String> fields = permissionService.getReadableFields(STANDARD_USER_ID, "User");
        long durationBatchNs = System.nanoTime() - startBatch;
        double durationBatchMs = durationBatchNs / 1_000_000.0;
        
        assertNotNull(fields, "Fields list should not be null");
        assertTrue(durationBatchMs < 100, 
                   String.format("Batch field query should be <100ms (was %.2fms)", durationBatchMs));
    }
    
    /**
     * Scenario 8: validateEditableFields - Security Exception
     * Attempting to edit non-editable fields should throw SecurityException
     */
    @Test
    @Order(8)
    @DisplayName("8. validateEditableFields - Throws exception for non-editable fields")
    void testValidateEditableFieldsThrowsException() {
        // Admin should be able to edit any field (wildcard permission)
        Map<String, Object> adminUpdates = new HashMap<>();
        adminUpdates.put("email", "newemail@test.com");
        adminUpdates.put("salary", 150000);
        
        // Admin should NOT throw exception for any field
        assertDoesNotThrow(() -> 
            permissionService.validateEditableFields(ADMIN_USER_ID, "User", adminUpdates),
            "Admin should allow editing all fields");
        
        // Try to update non-editable field for user without permission
        Map<String, Object> forbiddenUpdates = new HashMap<>();
        forbiddenUpdates.put("salary", 150000); // Not allowed for standard user
        
        // This SHOULD throw SecurityException for user without salary edit permission
        SecurityException exception = assertThrows(SecurityException.class, () -> 
            permissionService.validateEditableFields(NO_ROLE_USER_ID, "User", forbiddenUpdates),
            "Should throw SecurityException when editing forbidden field");
        
        assertTrue(exception.getMessage().contains("cannot edit field"),
                   "Exception message should indicate field edit restriction");
        
        // Verify HR can edit salary (from seed data)
        Map<String, Object> hrUpdates = new HashMap<>();
        hrUpdates.put("salary", 120000);
        
        assertDoesNotThrow(() -> 
            permissionService.validateEditableFields(HR_USER_ID, "User", hrUpdates),
            "HR should allow editing salary field");
    }
}
