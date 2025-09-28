package com.appbana;

import com.appbana.model.EntitySchema;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import com.fasterxml.jackson.databind.*;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AdvancedQueryTest {
    private static final ObjectMapper M = new ObjectMapper();
    private static final int PORT = 18080; // dedicated test port
    private static final String BASE = "http://localhost:" + PORT;

    @BeforeAll
    static void setup() throws Exception {
        // Initialize meta tables
        SchemaManager.init();

        // Define schemas
        EntitySchema customer = new EntitySchema();
        customer.setName("customer");
        customer.setFields(List.of(
                field("id","long", true, true),
                field("firstName","string", false, false),
                field("lastName","string", false, false),
                field("age","int", false, false)
        ));
        SchemaManager.saveSchema(customer);

        EntitySchema logs = new EntitySchema();
        logs.setName("logs");
        logs.setFields(List.of(
                field("id","long", true, true),
                field("level","string", false, false),
                field("createdAt","timestamp", false, false)
        ));
        SchemaManager.saveSchema(logs);

        EntitySchema numeric = new EntitySchema();
        numeric.setName("numeric_only");
        numeric.setFields(List.of(
                field("id","long", true, true),
                field("age","int", false, false)
        ));
        SchemaManager.saveSchema(numeric);

        // Insert sample customer rows
        for (int i=0;i<5;i++) {
            Map<String,Object> r = new LinkedHashMap<>();
            r.put("firstName", "Name"+i);
            r.put("lastName", "Last"+i);
            r.put("age", 20 + i);
            ApiServer.insertRecord(customer, r);
        }
        // Insert logs rows
        for (int i=0;i<3;i++) {
            Map<String,Object> r = new LinkedHashMap<>();
            r.put("level", i%2==0?"INFO":"WARN");
            r.put("createdAt", Timestamp.from(Instant.now()).getTime()); // epoch millis accepted
            ApiServer.insertRecord(logs, r);
        }
        // Insert numeric rows
        for (int i=0;i<4;i++) {
            Map<String,Object> r = new LinkedHashMap<>();
            r.put("age", 30+i);
            ApiServer.insertRecord(numeric, r);
        }

        // Start server (once)
        ApiServer.startJdk(PORT);
        // small wait to ensure server binds
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

    private static JsonNode get(String path) throws IOException, InterruptedException {
        HttpClient c = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(BASE + path)).GET().build();
        HttpResponse<String> resp = c.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode(), ()->"Unexpected status for GET " + path + ": " + resp.statusCode()+" body="+resp.body());
        return M.readTree(resp.body());
    }

    @Test @Order(1)
    void emptyProjectionOmitsFieldsKey() throws Exception {
        JsonNode node = get("/api/customer?fields=&limit=2");
        assertTrue(node.has("rows"), "rows present");
        assertFalse(node.has("fields"), "fields key should be omitted when fields= (blank)");
        assertEquals(2, node.get("rows").size());
    }

    @Test @Order(2)
    void duplicateSortCollapses() throws Exception {
        JsonNode node = get("/api/customer?sort=firstName,-firstName&limit=1");
        assertTrue(node.has("sort"));
        JsonNode sort = node.get("sort");
        assertEquals(1, sort.size(), "duplicate sort entries should collapse to one");
        assertTrue(sort.get(0).asText().contains("FIRSTNAME"));
    }

    @Test @Order(3)
    void badTimestampFilterLeftLiteral() throws Exception {
        JsonNode node = get("/api/logs?filter=createdAt:notISO&count=true");
        assertTrue(node.has("filters"));
        assertEquals("notISO", node.get("filters").get("createdAt").asText());
        assertTrue(node.has("total"));
    }

    @Test @Order(4)
    void qIgnoredWhenNoTextualFields() throws Exception {
        JsonNode baseline = get("/api/numeric_only?count=true");
        long total = baseline.get("total").asLong();
        JsonNode withQ = get("/api/numeric_only?q=something&count=true");
        assertEquals(total, withQ.get("total").asLong(), "q should not change count when no textual fields");
        assertEquals("something", withQ.get("query").asText());
    }

    @Test @Order(5)
    void countOnlyOmitsRows() throws Exception {
        JsonNode node = get("/api/customer?count=true&q=Name");
        assertTrue(node.has("total"));
        assertFalse(node.has("rows"), "count-only response should not have rows");
        assertEquals("Name", node.get("query").asText());
    }
}

