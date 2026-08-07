package com.appbana;

import com.appbana.model.EntitySchema;
import com.appbana.service.SessionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AuditLogTest {
    private static final ObjectMapper M = new ObjectMapper();
    private static final int PORT = 18081; // separate port from AdvancedQueryTest
    private static final String BASE = "http://localhost:" + PORT;
    private static long createdId;
    private static String TOKEN;

    @BeforeAll
    static void init() throws Exception {
        // Start server first (this runs Flyway migrations which clean the DB)
        ApiServer.startJdk(PORT);
        Thread.sleep(300);

        // Use SessionService to create a valid session, scoped to the "default"/"default"
        // tenant+app so it satisfies EntityAccessGuard (S3.4) for the fixture schema below.
        SessionService.SessionData session = SessionService.createSession("test-user", "default", "default");
        TOKEN = session.sessionId();

        // Now initialize SchemaManager and create the test schema
        SchemaManager.init();
        EntitySchema s = new EntitySchema();
        s.setName("audit_demo");
        // Set default tenant context to match API resolution expectations
        s.setTenantId("default");
        s.setAppId("default");
        s.setFields(List.of(field("id", "long", true, true), field("name", "string", false, false)));
        SchemaManager.saveSchema(s);
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
                .header("X-Session-Token", TOKEN)
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        HttpResponse<String> resp = c.send(req, HttpResponse.BodyHandlers.ofString());
        assertTrue(resp.statusCode() == 200 || resp.statusCode() == 201,
                () -> "Unexpected status: " + resp.statusCode() + " body=" + resp.body());
        return M.readTree(resp.body());
    }

    private static JsonNode put(String path, String json) throws Exception {
        HttpClient c = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(BASE + path))
                .header("Content-Type", "application/json")
                .header("X-Session-Token", TOKEN)
                .PUT(HttpRequest.BodyPublishers.ofString(json)).build();
        HttpResponse<String> resp = c.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode(), () -> "Unexpected status: " + resp.statusCode() + " body=" + resp.body());
        return M.readTree(resp.body());
    }

    private static JsonNode delete(String path) throws Exception {
        HttpClient c = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(BASE + path))
                .header("X-Session-Token", TOKEN)
                .DELETE().build();
        HttpResponse<String> resp = c.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode(), () -> "Unexpected status: " + resp.statusCode() + " body=" + resp.body());
        return M.readTree(resp.body());
    }

    private static JsonNode get(String path) throws Exception {
        HttpClient c = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(BASE + path))
                .header("X-Session-Token", TOKEN)
                .GET().build();
        HttpResponse<String> resp = c.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode(), () -> "Unexpected status: " + resp.statusCode() + " body=" + resp.body());
        return M.readTree(resp.body());
    }

    @Test
    @Order(1)
    void createUpdateDeleteGeneratesAudit() throws Exception {
        // create
        JsonNode created = post("/api/default_default_audit_demo", "{\"name\":\"Alpha\"}");
        createdId = created.get("id").asLong();
        assertTrue(createdId > 0);
        // update
        JsonNode upd = put("/api/default_default_audit_demo/" + createdId, "{\"name\":\"Beta\"}");
        assertEquals(1, upd.get("updated").asInt());
        // delete
        JsonNode del = delete("/api/default_default_audit_demo/" + createdId);
        assertEquals(1, del.get("deleted").asInt());
        // fetch audit
        JsonNode audit = get("/audit?entity=audit_demo&pk=" + createdId + "&limit=10");
        assertTrue(audit.has("rows"));
        JsonNode rows = audit.get("rows");
        assertEquals(3, rows.size(), "Expected 3 audit rows (INSERT, UPDATE, DELETE)");
        String op1 = rows.get(0).get("op").asText();
        String op2 = rows.get(1).get("op").asText();
        String op3 = rows.get(2).get("op").asText();
        assertEquals("INSERT", op1);
        assertEquals("UPDATE", op2);
        assertEquals("DELETE", op3);
        // INSERT: before null, after name Alpha
        assertTrue(rows.get(0).get("before").isNull());
        assertEquals("Alpha", rows.get(0).get("after").get("NAME").asText());
        // UPDATE: changes contains name from Alpha to Beta
        JsonNode changes = rows.get(1).get("changes");
        assertNotNull(changes);
        JsonNode nameChange = changes.get("NAME");
        assertNotNull(nameChange);
        assertEquals("Alpha", nameChange.get(0).asText());
        assertEquals("Beta", nameChange.get(1).asText());
        // DELETE: after null, before name Beta
        assertTrue(rows.get(2).get("after").isNull());
        assertEquals("Beta", rows.get(2).get("before").get("NAME").asText());
        // S4.6 — every row must carry the schema's own tenant/app, not be left null.
        for (int i = 0; i < 3; i++) {
            assertEquals("default", rows.get(i).get("tenantId").asText(),
                    "row " + i + " (" + rows.get(i).get("op").asText() + ") missing tenantId");
            assertEquals("default", rows.get(i).get("appId").asText(),
                    "row " + i + " (" + rows.get(i).get("op").asText() + ") missing appId");
        }
    }

    /**
     * S4.6 — proves the value written is genuinely sourced from the schema's own tenant/app, not
     * a hardcoded "default"/"default" that the first test above alone couldn't rule out.
     */
    @Test
    @Order(2)
    void auditRowsCarryTheSchemasOwnTenantAndAppNotAHardcodedDefault() throws Exception {
        String tenantId = "acme-corp";
        String appId = "billing-app";
        EntitySchema s = new EntitySchema();
        s.setName("audit_demo_custom");
        s.setTenantId(tenantId);
        s.setAppId(appId);
        s.setFields(List.of(field("id", "long", true, true), field("name", "string", false, false)));
        SchemaManager.saveSchema(s);

        SessionService.SessionData session = SessionService.createSession("custom-tenant-user", tenantId, appId);
        String customToken = session.sessionId();
        String packedKey = tenantId + "_" + appId + "_audit_demo_custom";

        JsonNode created = postWithToken("/api/" + packedKey, "{\"name\":\"Gamma\"}", customToken);
        long id = created.get("id").asLong();
        assertTrue(id > 0);

        JsonNode audit = getWithToken("/audit?entity=audit_demo_custom&pk=" + id + "&limit=10", customToken);
        JsonNode rows = audit.get("rows");
        assertEquals(1, rows.size(), "Expected 1 audit row (INSERT)");
        assertEquals(tenantId, rows.get(0).get("tenantId").asText());
        assertEquals(appId, rows.get(0).get("appId").asText());
    }

    /**
     * S4.6 — the studio-scoped route family ({@code /appbana-studio/{tenantId}/apps/{appId}/{entity}})
     * resolves the schema via {@code SchemaManager.loadSchema(appId, entity, tenantId)} rather than
     * a packed key, a genuinely different code path from the plain {@code /api/{entity}} route above
     * — worth its own live proof rather than assuming it behaves the same by inspection alone.
     */
    @Test
    @Order(3)
    void studioScopedWriteAlsoPopulatesTenantAndAppOnAudit() throws Exception {
        String tenantId = "acme-corp-studio";
        String appId = "billing-app-studio";
        EntitySchema s = new EntitySchema();
        s.setName("audit_demo_studio");
        s.setTenantId(tenantId);
        s.setAppId(appId);
        s.setFields(List.of(field("id", "long", true, true), field("name", "string", false, false)));
        SchemaManager.saveSchema(s);

        SessionService.SessionData session = SessionService.createSession("studio-tenant-user", tenantId, appId);
        String studioToken = session.sessionId();

        JsonNode created = postWithToken(
                "/appbana-studio/" + tenantId + "/apps/" + appId + "/audit_demo_studio",
                "{\"name\":\"Delta\"}", studioToken);
        long id = created.get("id").asLong();
        assertTrue(id > 0);

        JsonNode audit = getWithToken("/audit?entity=audit_demo_studio&pk=" + id + "&limit=10", studioToken);
        JsonNode rows = audit.get("rows");
        assertEquals(1, rows.size(), "Expected 1 audit row (INSERT)");
        assertEquals(tenantId, rows.get(0).get("tenantId").asText());
        assertEquals(appId, rows.get(0).get("appId").asText());
    }

    private static JsonNode postWithToken(String path, String json, String token) throws Exception {
        HttpClient c = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(BASE + path))
                .header("Content-Type", "application/json")
                .header("X-Session-Token", token)
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        HttpResponse<String> resp = c.send(req, HttpResponse.BodyHandlers.ofString());
        assertTrue(resp.statusCode() == 200 || resp.statusCode() == 201,
                () -> "Unexpected status: " + resp.statusCode() + " body=" + resp.body());
        return M.readTree(resp.body());
    }

    private static JsonNode getWithToken(String path, String token) throws Exception {
        HttpClient c = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(BASE + path))
                .header("X-Session-Token", token)
                .GET().build();
        HttpResponse<String> resp = c.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode(), () -> "Unexpected status: " + resp.statusCode() + " body=" + resp.body());
        return M.readTree(resp.body());
    }
}
