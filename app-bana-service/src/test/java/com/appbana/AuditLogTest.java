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

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AuditLogTest {
    private static final ObjectMapper M = new ObjectMapper();
    private static final int PORT = 18081; // separate port from AdvancedQueryTest
    private static final String BASE = "http://localhost:" + PORT;
    private static long createdId;

    @BeforeAll
    static void init() throws Exception {
        // Start server first (this runs Flyway migrations which clean the DB)
        ApiServer.startJdk(PORT);
        Thread.sleep(300);
        
        // Now initialize SchemaManager and create the test schema
        SchemaManager.init();
        EntitySchema s = new EntitySchema();
        s.setName("audit_demo");
        s.setFields(List.of(field("id","long", true, true), field("name","string", false, false)));
        SchemaManager.saveSchema(s);
    }

    private static EntitySchema.Field field(String name, String type, boolean pk, boolean auto) {
        EntitySchema.Field f = new EntitySchema.Field();
        f.setName(name); f.setType(type); f.setPrimaryKey(pk); f.setAutoIncrement(auto); return f;
    }

    private static JsonNode post(String path, String json) throws Exception {
        HttpClient c = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(BASE+path))
                .header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        HttpResponse<String> resp = c.send(req, HttpResponse.BodyHandlers.ofString());
        assertTrue(resp.statusCode()==200 || resp.statusCode()==201, ()->"Unexpected status: "+resp.statusCode()+" body="+resp.body());
        return M.readTree(resp.body());
    }
    private static JsonNode put(String path, String json) throws Exception {
        HttpClient c = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(BASE+path))
                .header("Content-Type","application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json)).build();
        HttpResponse<String> resp = c.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode(), ()->"Unexpected status: "+resp.statusCode()+" body="+resp.body());
        return M.readTree(resp.body());
    }
    private static JsonNode delete(String path) throws Exception {
        HttpClient c = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(BASE+path)).DELETE().build();
        HttpResponse<String> resp = c.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode(), ()->"Unexpected status: "+resp.statusCode()+" body="+resp.body());
        return M.readTree(resp.body());
    }
    private static JsonNode get(String path) throws Exception {
        HttpClient c = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(BASE+path)).GET().build();
        HttpResponse<String> resp = c.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode(), ()->"Unexpected status: "+resp.statusCode()+" body="+resp.body());
        return M.readTree(resp.body());
    }

    @Test @Order(1)
    void createUpdateDeleteGeneratesAudit() throws Exception {
        // create
        JsonNode created = post("/api/audit_demo", "{\"name\":\"Alpha\"}");
        createdId = created.get("id").asLong();
        assertTrue(createdId > 0);
        // update
        JsonNode upd = put("/api/audit_demo/"+createdId, "{\"name\":\"Beta\"}");
        assertEquals(1, upd.get("updated").asInt());
        // delete
        JsonNode del = delete("/api/audit_demo/"+createdId);
        assertEquals(1, del.get("deleted").asInt());
        // fetch audit
        JsonNode audit = get("/audit?entity=audit_demo&pk="+createdId+"&limit=10");
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
    }
}

