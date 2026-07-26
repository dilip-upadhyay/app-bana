package com.appbana.service;

import com.appbana.ApiServer;
import com.appbana.JdbcManager;
import com.appbana.SchemaManager;
import com.appbana.model.EntitySchema;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * H6 hardening — the /api/{entity}?groupBy=... endpoint used to bucket
 * only the current page of rows in Java, so the counts it returned were
 * silently wrong the moment the dataset exceeded {@code limit}. This
 * class covers the new {@code countByGroup} method which issues a real
 * SQL GROUP BY across the entire filtered dataset.
 */
public class EntityCrudGroupByTest {

    private static final String TENANT = "default";
    private static EntityCrudService service;

    @BeforeAll
    public static void setup() throws Exception {
        ApiServer.startJdk(18085);
        service = new EntityCrudService();
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

    private static EntitySchema schema(String appId, String entity, EntitySchema.Field... fields) {
        List<EntitySchema.Field> fs = new ArrayList<>();
        for (EntitySchema.Field f : fields) fs.add(f);
        EntitySchema s = new EntitySchema(entity, fs);
        s.setTenantId(TENANT);
        s.setAppId(appId);
        return s;
    }

    private static void insertStatus(EntitySchema s, String status) throws SQLException {
        String table = "\"" + SchemaManager.getPhysicalTableName(s) + "\"";
        try (Connection c = JdbcManager.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO " + table + " (\"STATUS\") VALUES (?)")) {
            ps.setString(1, status);
            ps.executeUpdate();
        }
    }

    private static void dropTable(EntitySchema s) {
        String table = SchemaManager.getPhysicalTableName(s);
        try (Connection c = JdbcManager.getConnection(); Statement st = c.createStatement()) {
            st.execute("DROP TABLE IF EXISTS \"" + table + "\" CASCADE");
        } catch (SQLException ignored) {
            // best-effort cleanup
        }
    }

    @Test
    public void countsSpanWholeDatasetNotJustCurrentPage() throws Exception {
        String appId = "H6-" + UUID.randomUUID().toString().substring(0, 8);
        EntitySchema s = schema(appId, "Ticket", pk(), text("status"));
        SchemaManager.saveSchema(s);
        try {
            // 100 total rows: 60 active, 30 pending, 10 closed. Enough to blow
            // past any reasonable page size and prove the count is not clipped.
            for (int i = 0; i < 60; i++) insertStatus(s, "active");
            for (int i = 0; i < 30; i++) insertStatus(s, "pending");
            for (int i = 0; i < 10; i++) insertStatus(s, "closed");

            Map<String, Long> counts = service.countByGroup(s, "status", null, Map.of());

            assertEquals(3, counts.size(), "must return one entry per distinct status");
            assertEquals(60L, counts.get("active"));
            assertEquals(30L, counts.get("pending"));
            assertEquals(10L, counts.get("closed"));
            // Results are ordered COUNT DESC — active comes first.
            String first = counts.keySet().iterator().next();
            assertEquals("active", first, "results must be ordered by count DESC");
        } finally {
            dropTable(s);
        }
    }

    @Test
    public void countsRespectSearchAndFilters() throws Exception {
        String appId = "H6-" + UUID.randomUUID().toString().substring(0, 8);
        EntitySchema s = schema(appId, "Ticket", pk(), text("status"));
        SchemaManager.saveSchema(s);
        try {
            for (int i = 0; i < 5; i++) insertStatus(s, "active");
            for (int i = 0; i < 5; i++) insertStatus(s, "pending");

            // Filter to only "active" — the group counts must reflect the filter.
            Map<String, Long> counts = service.countByGroup(s, "status", null, Map.of("status", "active"));

            assertEquals(1, counts.size(), "filter must reduce the groups seen");
            assertEquals(5L, counts.get("active"));
        } finally {
            dropTable(s);
        }
    }

    @Test
    public void unknownGroupByColumnIsRejectedNotInterpolated() throws Exception {
        // SQL-injection guard: an attacker cannot smuggle arbitrary SQL through
        // the groupBy query-string. Unknown columns return an empty map, they
        // do NOT throw an SQLException with the raw identifier leaking.
        String appId = "H6-" + UUID.randomUUID().toString().substring(0, 8);
        EntitySchema s = schema(appId, "Ticket", pk(), text("status"));
        SchemaManager.saveSchema(s);
        try {
            insertStatus(s, "active");

            Map<String, Long> counts = service.countByGroup(
                    s, "status; DROP TABLE ticket; --", null, Map.of());
            assertNotNull(counts);
            assertTrue(counts.isEmpty(), "unknown / malicious column must return empty, not execute");

            Map<String, Long> counts2 = service.countByGroup(s, "no_such_field", null, Map.of());
            assertTrue(counts2.isEmpty(), "unknown column must return empty");
        } finally {
            dropTable(s);
        }
    }

    @Test
    public void nullValuesBucketToEmptyString() throws Exception {
        String appId = "H6-" + UUID.randomUUID().toString().substring(0, 8);
        EntitySchema s = schema(appId, "Ticket", pk(), text("status"));
        SchemaManager.saveSchema(s);
        try {
            // Insert three rows without a status — they become NULL.
            String table = "\"" + SchemaManager.getPhysicalTableName(s) + "\"";
            try (Connection c = JdbcManager.getConnection(); Statement st = c.createStatement()) {
                st.execute("INSERT INTO " + table + " (\"STATUS\") VALUES (NULL), (NULL), (NULL)");
            }
            insertStatus(s, "active");

            Map<String, Long> counts = service.countByGroup(s, "status", null, Map.of());
            assertEquals(2, counts.size(), "NULL + 'active' == 2 distinct buckets");
            assertEquals(3L, counts.get(""), "NULL bucket must key to empty string");
            assertEquals(1L, counts.get("active"));
        } finally {
            dropTable(s);
        }
    }
}
