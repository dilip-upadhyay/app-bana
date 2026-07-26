package com.appbana;

import com.appbana.model.EntitySchema;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * H4 hardening — SchemaManager must emit real FOREIGN KEY constraints
 * (not just metadata) when an EntityField declares a reference. The
 * onDelete policy must map to the corresponding SQL DELETE action so
 * `DELETE FROM parent` behaves as the schema promised.
 *
 * Before H4:
 *   - `referenceEntity` and `onDelete` were pure documentation
 *   - Deleting a parent row silently orphaned children
 *
 * We verify by round-tripping through saveSchema (which calls
 * ensureTable → syncForeignKeys) and inspecting DatabaseMetaData for
 * the resulting FK.
 */
public class SchemaManagerForeignKeyTest {

    private static final String TENANT = "default";

    @BeforeAll
    public static void setup() throws Exception {
        ApiServer.startJdk(18084);
    }

    private static EntitySchema entity(String appId, String name, List<EntitySchema.Field> fields) {
        EntitySchema s = new EntitySchema(name, fields);
        s.setTenantId(TENANT);
        s.setAppId(appId);
        return s;
    }

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

    private static EntitySchema.Field reference(String name, String parent, String onDelete) {
        EntitySchema.Field f = new EntitySchema.Field();
        f.setName(name);
        f.setType("reference");
        f.setReferenceEntity(parent);
        f.setOnDelete(onDelete);
        return f;
    }

    /** Read the FK's delete rule from the DB metadata (short-integer codes). */
    private static short deleteRule(String childTable, String colName) throws SQLException {
        try (Connection c = JdbcManager.getConnection();
             ResultSet rs = c.getMetaData().getImportedKeys(null, null, childTable.toUpperCase())) {
            while (rs.next()) {
                String fkCol = rs.getString("FKCOLUMN_NAME");
                if (fkCol != null && fkCol.equalsIgnoreCase(colName)) {
                    return rs.getShort("DELETE_RULE");
                }
            }
        }
        return -1;
    }

    private static void dropTable(String physical) {
        try (Connection c = JdbcManager.getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS \"" + physical + "\" CASCADE");
        } catch (SQLException ignored) {
            // tests are isolated by unique appId per test — best effort
        }
    }

    /**
     * Postgres folds unquoted identifiers to lowercase, but SchemaManager creates
     * tables with QUOTED upper-case names. Any raw JDBC we do in these tests must
     * quote the table name so the case is preserved.
     */
    private static String q(String ident) {
        return "\"" + ident + "\"";
    }

    @Test
    public void restrictIsTheDefaultWhenOnDeleteIsUnset() throws Exception {
        String appId = "H4-" + UUID.randomUUID().toString().substring(0, 8);
        List<EntitySchema.Field> parentFields = new ArrayList<>();
        parentFields.add(pk());
        parentFields.add(text("name"));
        EntitySchema parent = entity(appId, "Customer", parentFields);
        SchemaManager.saveSchema(parent);

        List<EntitySchema.Field> childFields = new ArrayList<>();
        childFields.add(pk());
        childFields.add(text("subject"));
        // No onDelete → should default to RESTRICT
        childFields.add(reference("customer_id", "Customer", null));
        EntitySchema child = entity(appId, "Ticket", childFields);
        SchemaManager.saveSchema(child);

        String childTable = SchemaManager.getPhysicalTableName(child);
        short rule = deleteRule(childTable, "customer_id");
        // java.sql.DatabaseMetaData.importedKeyRestrict == 1
        assertEquals(1, rule, "onDelete omitted must map to RESTRICT (rule=1)");

        dropTable(childTable);
        dropTable(SchemaManager.getPhysicalTableName(parent));
    }

    @Test
    public void cascadeIsWiredThroughToTheDb() throws Exception {
        String appId = "H4-" + UUID.randomUUID().toString().substring(0, 8);
        EntitySchema parent = entity(appId, "Customer", List.of(pk(), text("name")));
        SchemaManager.saveSchema(parent);

        EntitySchema child = entity(appId, "Order",
                List.of(pk(), text("sku"), reference("customer_id", "Customer", "cascade")));
        SchemaManager.saveSchema(child);

        String childTable = SchemaManager.getPhysicalTableName(child);
        String parentTable = SchemaManager.getPhysicalTableName(parent);

        // rule == importedKeyCascade == 0
        assertEquals(0, deleteRule(childTable, "customer_id"), "onDelete=cascade must map to CASCADE (rule=0)");

        // End-to-end: delete parent row → child rows disappear.
        try (Connection c = JdbcManager.getConnection()) {
            long parentId;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + q(parentTable) + " (\"NAME\") VALUES (?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, "Acme");
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    assertTrue(rs.next());
                    parentId = rs.getLong(1);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + q(childTable) + " (\"SKU\", \"CUSTOMER_ID\") VALUES (?, ?)")) {
                ps.setString(1, "SKU-1");
                ps.setLong(2, parentId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM " + q(parentTable) + " WHERE \"ID\" = ?")) {
                ps.setLong(1, parentId);
                assertEquals(1, ps.executeUpdate(), "parent row should delete");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM " + q(childTable) + " WHERE \"CUSTOMER_ID\" = ?")) {
                ps.setLong(1, parentId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(0, rs.getInt(1), "cascade must have removed the child row");
                }
            }
        }

        dropTable(childTable);
        dropTable(parentTable);
    }

    @Test
    public void setNullIsWiredThroughToTheDb() throws Exception {
        String appId = "H4-" + UUID.randomUUID().toString().substring(0, 8);
        EntitySchema parent = entity(appId, "Category", List.of(pk(), text("name")));
        SchemaManager.saveSchema(parent);

        EntitySchema child = entity(appId, "Product",
                List.of(pk(), text("title"), reference("category_id", "Category", "setNull")));
        SchemaManager.saveSchema(child);

        String childTable = SchemaManager.getPhysicalTableName(child);
        // rule == importedKeySetNull == 2
        assertEquals(2, deleteRule(childTable, "category_id"), "onDelete=setNull must map to SET NULL (rule=2)");

        dropTable(childTable);
        dropTable(SchemaManager.getPhysicalTableName(parent));
    }

    @Test
    public void restrictBlocksDeletingParentWithChildren() throws Exception {
        String appId = "H4-" + UUID.randomUUID().toString().substring(0, 8);
        EntitySchema parent = entity(appId, "Author", List.of(pk(), text("name")));
        SchemaManager.saveSchema(parent);

        EntitySchema child = entity(appId, "Book",
                List.of(pk(), text("title"), reference("author_id", "Author", "restrict")));
        SchemaManager.saveSchema(child);

        String childTable = SchemaManager.getPhysicalTableName(child);
        String parentTable = SchemaManager.getPhysicalTableName(parent);

        try (Connection c = JdbcManager.getConnection()) {
            long parentId;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + q(parentTable) + " (\"NAME\") VALUES (?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, "Rowling");
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    assertTrue(rs.next());
                    parentId = rs.getLong(1);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + q(childTable) + " (\"TITLE\", \"AUTHOR_ID\") VALUES (?, ?)")) {
                ps.setString(1, "Ink & Ashes");
                ps.setLong(2, parentId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM " + q(parentTable) + " WHERE \"ID\" = ?")) {
                ps.setLong(1, parentId);
                ps.executeUpdate();
                fail("RESTRICT must block deleting a parent that has children");
            } catch (SQLException expected) {
                // Postgres error code 23503 = foreign_key_violation
                assertTrue(expected.getMessage().toLowerCase().contains("foreign key")
                                || "23503".equals(expected.getSQLState()),
                        "expected FK violation, got: " + expected.getMessage());
            }
        }

        // Clean up (delete child first so we can drop cleanly).
        try (Connection c = JdbcManager.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("DELETE FROM " + q(childTable));
            s.executeUpdate("DELETE FROM " + q(parentTable));
        }
        dropTable(childTable);
        dropTable(parentTable);
    }
}
