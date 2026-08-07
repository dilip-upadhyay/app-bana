package com.appbana.service;

import com.appbana.JdbcManager;
import com.appbana.SchemaManager;
import com.appbana.model.EntitySchema;
import com.appbana.model.TenantContext;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link EntityCrudService}'s hash-on-write behavior for any field named "password"
 * (Task S4.2 — Tenant Isolation Security Plan).
 *
 * <p>Before this task, the generic entity CRUD write paths ({@code insertRecord}, {@code
 * updateById}, {@code insertBatch}) stored a "password" field's value completely unchanged, so
 * any runtime end-user account created (or edited) via the generic entity API — the only way to
 * create one, since there is no dedicated {@code /api/runtime/auth/register} endpoint — persisted
 * a plaintext password. {@link GenericAppAuthController#login()}'s own BCrypt verification (S3.3)
 * only ever consumed rows written this way; it never wrote them itself except transparently
 * rehashing a legacy plaintext row on a successful login (also S4.2, see
 * {@code GenericAppAuthControllerTest}).
 *
 * <p>Covers the three independent write call sites that funnel through the new
 * {@code coerceValidateAndHashIfPassword} choke point, plus the double-hash-avoidance guard that
 * makes a "fetch full record (password column is not redacted by {@code getById}/{@code
 * listAll}) - edit an unrelated field - PUT it all back" round trip safe.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EntityCrudServicePasswordHashingTest {

    private static final String TENANT_A = "t-s42-crud";
    private static final String APP_1 = "s42-crud-app";
    private static final String ENTITY_NAME = "PwUser";

    private EntityCrudService service;
    private EntitySchema schema;

    @BeforeEach
    void setUp() {
        service = new EntityCrudService();
        cleanUpFixtures();
        schema = saveFixtureSchema();
    }

    @AfterEach
    void tearDown() {
        cleanUpFixtures();
    }

    private void cleanUpFixtures() {
        try (Connection c = JdbcManager.getConnection("default");
                Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS \"" + physicalTableName().toUpperCase() + "\"");
            s.execute("DELETE FROM appbana_schemas WHERE tenant_id = '" + TENANT_A + "' AND app_id = '" + APP_1 + "'");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String physicalTableName() {
        EntitySchema s = new EntitySchema();
        s.setTenantId(TENANT_A);
        s.setAppId(APP_1);
        s.setName(ENTITY_NAME);
        return SchemaManager.getPhysicalTableName(s);
    }

    private EntitySchema saveFixtureSchema() {
        EntitySchema s = new EntitySchema();
        s.setName(ENTITY_NAME);
        s.setAppId(APP_1);
        s.setTenantId(TENANT_A);

        EntitySchema.Field id = new EntitySchema.Field("id", "long", true, true, null);
        EntitySchema.Field email = new EntitySchema.Field("email", "string", false, false, null);
        EntitySchema.Field password = new EntitySchema.Field("password", "string", false, false, null);
        EntitySchema.Field name = new EntitySchema.Field("name", "string", false, false, null);

        s.setFields(List.of(id, email, password, name));
        SchemaManager.saveSchema(s);
        return s;
    }

    private String fetchStoredColumn(Object id, String column) throws SQLException {
        String table = physicalTableName().toUpperCase();
        String sql = "SELECT \"" + column.toUpperCase() + "\" FROM \"" + table + "\" WHERE \"ID\" = ?";
        try (Connection c = JdbcManager.getConnection("default");
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, Long.parseLong(id.toString()));
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next(), "fixture row " + id + " must exist");
                return rs.getString(1);
            }
        }
    }

    private Map<String, Object> row(String email, String password, String name) {
        Map<String, Object> data = new HashMap<>();
        data.put("email", email);
        data.put("password", password);
        data.put("name", name);
        return data;
    }

    // ========================================
    // insertRecord (single-record insert path)
    // ========================================

    @Test
    @DisplayName("insertRecord hashes a plaintext password before persisting it")
    void testInsertRecordHashesPlaintextPassword() throws SQLException {
        Object id = service.insertRecord(schema, row("alice@example.com", "PlainPass123!", "Alice"));

        String stored = fetchStoredColumn(id, "password");
        assertNotEquals("PlainPass123!", stored, "the raw plaintext must never reach the database");
        assertTrue(PasswordService.looksLikeBcryptHash(stored), "stored value must be a BCrypt hash");
        assertTrue(PasswordService.verifyPassword("PlainPass123!", stored),
                "the hash must verify against the original plaintext password");
    }

    @Test
    @DisplayName("insertRecord leaves non-password fields completely untouched, even if hash-shaped")
    void testInsertRecordDoesNotHashNonPasswordFields() throws SQLException {
        // A value that happens to start with a BCrypt-looking prefix in an unrelated field must
        // be stored byte-for-byte as given — the gating is on the field NAME, not the value shape.
        String hashShapedName = "$2a$12$notActuallyAHashJustLooksLikeOne.......................";
        Object id = service.insertRecord(schema, row("bob@example.com", "AnotherPass1!", hashShapedName));

        assertEquals(hashShapedName, fetchStoredColumn(id, "name"),
                "a non-'password' field must never be hashed, regardless of its value's shape");
    }

    // ========================================
    // updateById (update path)
    // ========================================

    @Test
    @DisplayName("updateById hashes a new plaintext password before persisting it")
    void testUpdateByIdHashesNewPlaintextPassword() throws SQLException {
        Object id = service.insertRecord(schema, row("carol@example.com", "OldPass123!", "Carol"));

        Map<String, Object> updates = new HashMap<>();
        updates.put("password", "NewPass456!");
        int rows = service.updateById(schema, id.toString(), updates);
        assertEquals(1, rows);

        String stored = fetchStoredColumn(id, "password");
        assertNotEquals("NewPass456!", stored);
        assertTrue(PasswordService.looksLikeBcryptHash(stored));
        assertTrue(PasswordService.verifyPassword("NewPass456!", stored));
    }

    @Test
    @DisplayName("updateById does not double-hash a value that already looks like a BCrypt hash "
            + "(fetch-then-PUT-back round trip)")
    void testUpdateByIdDoesNotDoubleHashAnAlreadyHashedValue() throws SQLException {
        Object id = service.insertRecord(schema, row("dave@example.com", "RoundTrip123!", "Dave"));
        String hashAfterInsert = fetchStoredColumn(id, "password");
        assertTrue(PasswordService.looksLikeBcryptHash(hashAfterInsert));

        // Simulate a client that fetched the full row (password column included - getById/listAll
        // apply no redaction), changed an unrelated field, and PUT the whole object back,
        // resubmitting the already-hashed password value unchanged.
        Map<String, Object> roundTrippedUpdate = new HashMap<>();
        roundTrippedUpdate.put("password", hashAfterInsert);
        roundTrippedUpdate.put("name", "Dave Updated");
        int rows = service.updateById(schema, id.toString(), roundTrippedUpdate);
        assertEquals(1, rows);

        String hashAfterRoundTrip = fetchStoredColumn(id, "password");
        assertEquals(hashAfterInsert, hashAfterRoundTrip,
                "an already-hashed value must be stored unchanged, never hashed a second time");
        assertTrue(PasswordService.verifyPassword("RoundTrip123!", hashAfterRoundTrip),
                "the original password must still authenticate after the round trip");
        assertEquals("Dave Updated", fetchStoredColumn(id, "name"), "the unrelated field edit must still apply");
    }

    @Test
    @DisplayName("updateById(TenantContext, ...) overload also hashes a new plaintext password "
            + "(round-81 review nit: this overload shares coerceValidateAndHashIfPassword with the "
            + "3-arg overload, but had no test calling it directly)")
    void testUpdateByIdWithTenantContextHashesNewPlaintextPassword() throws SQLException {
        Object id = service.insertRecord(schema, row("grace@example.com", "GraceOld123!", "Grace"));

        TenantContext context = new TenantContext(TENANT_A, APP_1);
        Map<String, Object> updates = new HashMap<>();
        updates.put("password", "GraceNew456!");
        int rows = service.updateById(context, schema, id.toString(), updates);
        assertEquals(1, rows);

        String stored = fetchStoredColumn(id, "password");
        assertNotEquals("GraceNew456!", stored, "the raw plaintext must never reach the database");
        assertTrue(PasswordService.looksLikeBcryptHash(stored));
        assertTrue(PasswordService.verifyPassword("GraceNew456!", stored));
    }

    // ========================================
    // insertBatch (batch insert path)
    // ========================================

    @Test
    @DisplayName("insertBatch hashes every row's plaintext password before persisting")
    void testInsertBatchHashesPlaintextPasswords() throws SQLException {
        List<Map<String, Object>> batch = List.of(
                row("eve@example.com", "EvePass123!", "Eve"),
                row("frank@example.com", "FrankPass456!", "Frank"));

        @SuppressWarnings("unchecked")
        List<Object> ids = (List<Object>) service.insertBatch(schema, batch).get("ids");
        assertNotNull(ids);
        assertEquals(2, ids.size());

        String eveHash = fetchStoredColumn(ids.get(0), "password");
        String frankHash = fetchStoredColumn(ids.get(1), "password");

        assertTrue(PasswordService.looksLikeBcryptHash(eveHash));
        assertTrue(PasswordService.verifyPassword("EvePass123!", eveHash));
        assertTrue(PasswordService.looksLikeBcryptHash(frankHash));
        assertTrue(PasswordService.verifyPassword("FrankPass456!", frankHash));
    }
}
