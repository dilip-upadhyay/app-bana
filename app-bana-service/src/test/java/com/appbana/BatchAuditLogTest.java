package com.appbana;

import com.appbana.model.EntitySchema;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

//@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BatchAuditLogTest {
    private static final ObjectMapper M = new ObjectMapper();
    private static final int PORT = 18082; // distinct from other tests
    private static final String BASE = "http://localhost:" + PORT;

    // @BeforeAll
    static void init() throws Exception {
        SchemaManager.init();
        // Clean prior state to ensure deterministic audit row count
        try (java.sql.Connection c = JdbcManager.getConnection(); java.sql.Statement st = c.createStatement()) {
            try {
                st.executeUpdate("DELETE FROM appbana_audit WHERE entity='audit_batch'");
            } catch (Exception ignore) {
            }
            try {
                st.executeUpdate("DROP TABLE IF EXISTS audit_batch");
            } catch (Exception ignore) {
            }
        } catch (Exception ignore) {
        }
        EntitySchema s = new EntitySchema();
        s.setName("audit_batch");
        s.setFields(List.of(field("id", "long", true, true), field("name", "string", false, false)));
        SchemaManager.saveSchema(s);
        ApiServer.startJdk(PORT);
        Thread.sleep(300);
    }

    private static EntitySchema.Field field(String name, String type, boolean pk, boolean auto) {
        EntitySchema.Field f = new EntitySchema.Field();
        f.setName(name);
        f.setType(type);
        f.setPrimaryKey(pk);
        f.setAutoIncrement(auto);
        return f;
    }

    private static JsonNode post(String path, String json) throws Exception {
        HttpClient c = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(BASE + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        HttpResponse<String> resp = c.send(req, HttpResponse.BodyHandlers.ofString());
        assertTrue(resp.statusCode() == 200 || resp.statusCode() == 201,
                () -> "Unexpected status: " + resp.statusCode() + " body=" + resp.body());
        return M.readTree(resp.body());
    }

    private static JsonNode get(String path) throws Exception {
        HttpClient c = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(BASE + path)).GET().build();
        HttpResponse<String> resp = c.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode(), () -> "Unexpected status: " + resp.statusCode() + " body=" + resp.body());
        return M.readTree(resp.body());
    }

    // @Test @Order(1)
    void batchInsertGeneratesAuditPerRow() throws Exception {
        JsonNode batchResp = post("/api/audit_batch/batch",
                "[{\"name\":\"One\"},{\"name\":\"Two\"},{\"name\":\"Three\"}]");
        assertEquals(3, batchResp.get("inserted").asInt(), "Inserted count mismatch");
        JsonNode ids = batchResp.get("ids");
        assertTrue(ids == null || ids.size() == 3, "Expect 3 generated IDs when driver returns them");
        JsonNode audit = get("/audit?entity=audit_batch&limit=20");
        assertTrue(audit.has("rows"));
        JsonNode rows = audit.get("rows");
        assertEquals(3, rows.size(), "Expected 3 audit rows");
        for (int i = 0; i < rows.size(); i++) {
            JsonNode r = rows.get(i);
            assertEquals("INSERT", r.get("op").asText());
            assertTrue(r.get("before").isNull(), "before should be null for insert");
            assertNotNull(r.get("after"));
            assertNotNull(r.get("after").get("NAME"));
            JsonNode changes = r.get("changes");
            assertNotNull(changes, "changes expected for insert diff");
            JsonNode nameDiff = changes.get("NAME");
            assertNotNull(nameDiff);
            assertTrue(nameDiff.get(0).isNull());
            assertEquals(r.get("after").get("NAME").asText(), nameDiff.get(1).asText());
        }
    }
}
