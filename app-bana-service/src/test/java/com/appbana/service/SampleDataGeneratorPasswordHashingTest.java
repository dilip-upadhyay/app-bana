package com.appbana.service;

import com.appbana.JdbcManager;
import com.appbana.model.EntitySchema;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SampleDataGenerator}'s hash-on-write guard for any column named "password"
 * (Task S4.2 — Tenant Isolation Security Plan).
 *
 * <p>{@link SampleDataGenerator} is the third and last of the three independent password-write
 * paths S4.2 closes. It predates {@link EntityCrudService} and builds/executes its own raw SQL
 * INSERT directly via {@link JdbcManager#getConnection()} (used by {@code AppPublishService} only
 * for LOCAL-environment sample-data seeding), so it needed its own guard rather than inheriting
 * {@code EntityCrudService.coerceValidateAndHashIfPassword}. Before this fix, a "password" field
 * fell through {@link SampleDataGenerator}'s field-name-pattern matching in {@code
 * generateTextValue} (no branch there recognizes "password") straight to the generic {@code
 * "Sample " + label + " " + index} placeholder text, persisted completely unhashed.
 */
class SampleDataGeneratorPasswordHashingTest {

    private static final String TENANT_ID = "s42gen";
    private static final String APP_ID = "s42genapp";
    private static final String ENV = "local";
    private static final String ENTITY_NAME = "sduser";
    private static final String TABLE_NAME = "app_" + TENANT_ID + "_" + APP_ID + "_" + ENV + "_" + ENTITY_NAME;

    private final SampleDataGenerator generator = new SampleDataGenerator();

    @BeforeEach
    void setUp() throws SQLException {
        dropTable();
        try (Connection c = JdbcManager.getConnection();
                Statement s = c.createStatement()) {
            s.execute("CREATE TABLE " + TABLE_NAME + " ("
                    + "id BIGSERIAL PRIMARY KEY, "
                    + "email VARCHAR(255), "
                    + "password VARCHAR(255), "
                    + "name VARCHAR(255))");
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        dropTable();
    }

    private void dropTable() throws SQLException {
        try (Connection c = JdbcManager.getConnection();
                Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS " + TABLE_NAME);
        }
    }

    private EntitySchema fixtureSchema() {
        EntitySchema schema = new EntitySchema();
        schema.setName(ENTITY_NAME);
        EntitySchema.Field id = new EntitySchema.Field("id", "long", true, true, null);
        EntitySchema.Field email = new EntitySchema.Field("email", "string", false, false, null);
        EntitySchema.Field password = new EntitySchema.Field("password", "string", false, false, null);
        EntitySchema.Field name = new EntitySchema.Field("name", "string", false, false, null);
        schema.setFields(List.of(id, email, password, name));
        return schema;
    }

    @Test
    @DisplayName("S4.2: LOCAL-environment sample data seeding hashes generated 'password' column values")
    void testGenerateSampleDataHashesPasswordColumn() throws SQLException {
        generator.generateSampleData(TENANT_ID, APP_ID, ENV, List.of(fixtureSchema()));

        List<String> storedPasswords = fetchAllPasswords();
        assertEquals(5, storedPasswords.size(), "default sample record count is 5 per entity");
        for (String stored : storedPasswords) {
            assertTrue(PasswordService.looksLikeBcryptHash(stored),
                    "every generated 'password' value must be persisted as a BCrypt hash, not plain text: "
                            + stored);
            assertFalse(stored.startsWith("Sample "),
                    "the raw 'Sample password N' placeholder text must never reach the database");
        }
    }

    @Test
    @DisplayName("S4.2: non-LOCAL environments are unaffected (pre-existing early-return, unchanged)")
    void testNonLocalEnvironmentSkipsGenerationEntirely() throws SQLException {
        generator.generateSampleData(TENANT_ID, APP_ID, "production", List.of(fixtureSchema()));

        assertEquals(0, fetchAllPasswords().size(), "no rows should be generated for a non-local environment");
    }

    private List<String> fetchAllPasswords() throws SQLException {
        List<String> out = new java.util.ArrayList<>();
        try (Connection c = JdbcManager.getConnection();
                Statement s = c.createStatement();
                var rs = s.executeQuery("SELECT password FROM " + TABLE_NAME)) {
            while (rs.next()) {
                out.add(rs.getString(1));
            }
        }
        return out;
    }
}
