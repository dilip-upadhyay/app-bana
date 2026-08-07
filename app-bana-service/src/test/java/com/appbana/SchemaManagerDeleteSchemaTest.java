package com.appbana;

import com.appbana.model.EntitySchema;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S3.9 — {@code SchemaManager.deleteSchema(name, dropTable=true)} must actually
 * drop the entity's PHYSICAL table, not silently no-op.
 *
 * Before this fix the DROP statement was built from the schema's logical
 * registry key ({@code name.toUpperCase()}), never from
 * {@link SchemaManager#getPhysicalTableName(EntitySchema)} — the two strings
 * never match once an appId/tenantId prefix is involved, so
 * {@code DROP TABLE IF EXISTS} always silently no-opped, leaking the physical
 * table forever. Confirmed as a real, observed leak: S3.7's own fixture
 * teardown left 6 orphaned tables that had to be dropped by hand.
 */
public class SchemaManagerDeleteSchemaTest {

    private static final String TENANT = "default";

    private static EntitySchema.Field pk() {
        EntitySchema.Field id = new EntitySchema.Field();
        id.setName("id");
        id.setType("integer");
        id.setPrimaryKey(true);
        id.setAutoIncrement(true);
        return id;
    }

    private static EntitySchema.Field text(String name) {
        EntitySchema.Field f = new EntitySchema.Field();
        f.setName(name);
        f.setType("text");
        return f;
    }

    private static boolean physicalTableExists(String physicalTable) throws SQLException {
        try (Connection c = JdbcManager.getConnection()) {
            DatabaseMetaData meta = c.getMetaData();
            try (ResultSet rs = meta.getTables(null, null, physicalTable.toUpperCase(), new String[] { "TABLE" })) {
                return rs.next();
            }
        }
    }

    private static void bestEffortDrop(String physicalTable) {
        try (Connection c = JdbcManager.getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS \"" + physicalTable.toUpperCase() + "\"");
        } catch (SQLException ignored) {
            // best effort cleanup only
        }
    }

    @Test
    public void deleteSchemaWithDropTableRemovesThePhysicalTable() throws Exception {
        String appId = "S39-" + UUID.randomUUID().toString().substring(0, 8);
        List<EntitySchema.Field> fields = new ArrayList<>();
        fields.add(pk());
        fields.add(text("label"));
        EntitySchema schema = new EntitySchema("Widget", fields);
        schema.setTenantId(TENANT);
        schema.setAppId(appId);
        SchemaManager.saveSchema(schema);

        String schemaKey = TENANT + "_" + appId + "_Widget";
        String physicalTable = SchemaManager.getPhysicalTableName(schema);

        // Sanity: the physical table really was created before we assert its removal.
        assertTrue(physicalTableExists(physicalTable), "Physical table should exist right after saveSchema");

        boolean deleted = SchemaManager.deleteSchema(schemaKey, true);
        assertTrue(deleted, "deleteSchema should report success");

        assertFalse(physicalTableExists(physicalTable),
                "deleteSchema(dropTable=true) must actually drop the physical table, not silently no-op");
        assertNull(SchemaManager.loadSchema(schemaKey), "schema registry row should be gone too");

        // Nothing to clean up: the table is (correctly, now) already gone.
    }

    @Test
    public void deleteSchemaWithoutDropTablePreservesThePhysicalTable() throws Exception {
        String appId = "S39-" + UUID.randomUUID().toString().substring(0, 8);
        List<EntitySchema.Field> fields = new ArrayList<>();
        fields.add(pk());
        fields.add(text("label"));
        EntitySchema schema = new EntitySchema("Gadget", fields);
        schema.setTenantId(TENANT);
        schema.setAppId(appId);
        SchemaManager.saveSchema(schema);

        String schemaKey = TENANT + "_" + appId + "_Gadget";
        String physicalTable = SchemaManager.getPhysicalTableName(schema);

        boolean deleted = SchemaManager.deleteSchema(schemaKey, false);
        assertTrue(deleted, "deleteSchema should report success");

        assertNull(SchemaManager.loadSchema(schemaKey), "schema registry row should be gone");
        assertTrue(physicalTableExists(physicalTable),
                "deleteSchema(dropTable=false) must not touch the physical table");

        bestEffortDrop(physicalTable);
    }
}
