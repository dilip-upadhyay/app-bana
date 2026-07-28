package com.appbana;

import com.appbana.model.EntitySchema;
import com.appbana.service.EntityCrudService;
import com.appbana.service.SessionService;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
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
    private static String TOKEN;

    @BeforeAll
    static void setup() throws Exception {
        // Start server first (this runs Flyway migrations which clean the DB)
        ApiServer.startJdk(PORT);
        Thread.sleep(300);

        // Initialize meta tables and create service
        SchemaManager.init();
        EntityCrudService crud = new EntityCrudService();

        // Create valid session for tests
        SessionService.SessionData session = SessionService.createSession("test-user");
        TOKEN = session.sessionId();

        // Define schemas with default tenant/app context to match API resolution
        EntitySchema customer = new EntitySchema();
        customer.setName("customer");
        customer.setTenantId("default");
        customer.setAppId("default");
        customer.setFields(List.of(
                field("id", "long", true, true),
                field("firstName", "string", false, false),
                field("lastName", "string", false, false),
                field("age", "int", false, false)));
        SchemaManager.saveSchema(customer);

        EntitySchema logs = new EntitySchema();
        logs.setName("logs");
        logs.setTenantId("default");
        logs.setAppId("default");
        logs.setFields(List.of(
                field("id", "long", true, true),
                field("level", "string", false, false),
                field("createdAt", "timestamp", false, false)));
        SchemaManager.saveSchema(logs);

        EntitySchema numeric = new EntitySchema();
        numeric.setName("numeric_only");
        numeric.setTenantId("default");
        numeric.setAppId("default");
        numeric.setFields(List.of(
                field("id", "long", true, true),
                field("age", "int", false, false)));
        SchemaManager.saveSchema(numeric);

        // Insert sample customer rows
        for (int i = 0; i < 5; i++) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("firstName", "Name" + i);
            r.put("lastName", "Last" + i);
            r.put("age", 20 + i);
            crud.insertRecord(customer, r);
        }
        // Insert logs rows
        for (int i = 0; i < 3; i++) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("level", i % 2 == 0 ? "INFO" : "WARN");
            r.put("createdAt", Timestamp.from(Instant.now()).getTime()); // epoch millis accepted
            crud.insertRecord(logs, r);
        }
        // Insert numeric rows
        for (int i = 0; i < 4; i++) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("age", 30 + i);
            crud.insertRecord(numeric, r);
        }

        // C3.10 (item A) — a scaffolded child table's foreign-key column, plus the
        // other numeric column types §11 of the copilot instructions tells every
        // caller to use. Before this fix, filtering on any of "reference"/
        // "number"/"decimal" bound a raw String against an INTEGER/NUMERIC column
        // and Postgres 500'd with "operator does not exist". int is included as
        // the control case that always worked.
        EntitySchema lineItem = new EntitySchema();
        lineItem.setName("line_item");
        lineItem.setTenantId("default");
        lineItem.setAppId("default");
        lineItem.setFields(List.of(
                field("id", "long", true, true),
                field("invoice_id", "reference", false, false),
                field("qty", "number", false, false),
                field("unit_price", "decimal", false, false),
                field("leg_no", "int", false, false)));
        SchemaManager.saveSchema(lineItem);

        // This is a dedicated test fixture table on the shared dev Postgres
        // instance — Liquibase/flywayCleanOnStart only manage schema, not
        // data, so rows accumulate across repeated `mvn test` invocations.
        // Clear it before seeding so Orders 6-10's exact-count assertions
        // are deterministic regardless of prior runs.
        try (java.sql.Connection conn = JdbcManager.getConnection();
                java.sql.Statement st = conn.createStatement()) {
            st.execute("DELETE FROM \"" + SchemaManager.getPhysicalTableName(lineItem).toUpperCase(Locale.ROOT)
                    + "\"");
        }

        // Invoice 1 has two line items, invoice 2 has one — mirrors the
        // master-detail shape ChildTable.tsx renders.
        insertLineItem(crud, lineItem, 1, 2, "9.99", 10);
        insertLineItem(crud, lineItem, 1, 3, "4.50", 11);
        insertLineItem(crud, lineItem, 2, 1, "100.00", 10);
    }

    private static void insertLineItem(EntityCrudService crud, EntitySchema schema,
            int invoiceId, int qty, String unitPrice, int legNo) throws SQLException {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("invoice_id", invoiceId);
        r.put("qty", qty);
        r.put("unit_price", unitPrice);
        r.put("leg_no", legNo);
        crud.insertRecord(schema, r);
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
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .header("X-Session-Token", TOKEN)
                .GET()
                .build();
        HttpResponse<String> resp = c.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode(),
                () -> "Unexpected status for GET " + path + ": " + resp.statusCode() + " body=" + resp.body());
        return M.readTree(resp.body());
    }

    @Test
    @Order(1)
    void emptyProjectionOmitsFieldsKey() throws Exception {
        JsonNode node = get("/api/default_default_customer?fields=&limit=2");
        assertTrue(node.has("rows"), "rows present");
        assertFalse(node.has("fields"), "fields key should be omitted when fields= (blank)");
        assertEquals(2, node.get("rows").size());
    }

    @Test
    @Order(2)
    void duplicateSortCollapses() throws Exception {
        JsonNode node = get("/api/default_default_customer?sort=firstName,-firstName&limit=1");
        assertTrue(node.has("sort"));
        JsonNode sort = node.get("sort");
        assertEquals(1, sort.size(), "duplicate sort entries should collapse to one");
        assertTrue(sort.get(0).asText().contains("FIRSTNAME"));
    }

    @Test
    @Order(3)
    void badTimestampFilterLeftLiteral() throws Exception {
        JsonNode node = get("/api/default_default_logs?filter=createdAt:notISO&count=true");
        assertTrue(node.has("filters"));
        assertEquals("notISO", node.get("filters").get("createdAt").asText());
        assertTrue(node.has("total"));
    }

    @Test
    @Order(4)
    void qIgnoredWhenNoTextualFields() throws Exception {
        JsonNode baseline = get("/api/default_default_numeric_only?count=true");
        long total = baseline.get("total").asLong();
        JsonNode withQ = get("/api/default_default_numeric_only?q=something&count=true");
        assertEquals(total, withQ.get("total").asLong(), "q should not change count when no textual fields");
        assertEquals("something", withQ.get("query").asText());
    }

    @Test
    @Order(5)
    void countOnlyOmitsRows() throws Exception {
        JsonNode node = get("/api/default_default_customer?count=true&q=Name");
        assertTrue(node.has("total"));
        assertFalse(node.has("rows"), "count-only response should not have rows");
        assertEquals("Name", node.get("query").asText());
    }

    // C3.10 (item A) — exact-match filter on every numeric-ish column type a
    // generated schema can have. Each must scope the list to the matching
    // parent with 200, not 500. "int" (leg_no) is the control: it already
    // worked before this fix and must keep working.

    @Test
    @Order(6)
    void referenceTypeExactFilterScopesToParentInsteadOf500() throws Exception {
        JsonNode node = get("/api/default_default_line_item?filter=invoice_id:=1&count=true");
        assertEquals(2, node.get("total").asLong(),
                "reference-typed FK exact filter must scope to the parent's 2 rows, not 500 or return all 3");
    }

    @Test
    @Order(7)
    void numberTypeExactFilterScopesRowsInsteadOf500() throws Exception {
        JsonNode node = get("/api/default_default_line_item?filter=qty:=2&count=true");
        assertEquals(1, node.get("total").asLong());
    }

    @Test
    @Order(8)
    void decimalTypeExactFilterScopesRowsInsteadOf500() throws Exception {
        JsonNode node = get("/api/default_default_line_item?filter=unit_price:=100.00&count=true");
        assertEquals(1, node.get("total").asLong());
    }

    @Test
    @Order(9)
    void intTypeExactFilterStillWorks() throws Exception {
        JsonNode node = get("/api/default_default_line_item?filter=leg_no:=10&count=true");
        assertEquals(2, node.get("total").asLong());
    }

    @Test
    @Order(10)
    void referenceTypeBareParamStillIgnored() throws Exception {
        // Consistent with ApprovalRoutesSecurityTest's
        // testBareFieldParamIsIgnoredButFilterParamScopesTheList — the bare
        // (non-filter=) form must still be silently ignored, not 500, for a
        // reference column too.
        JsonNode node = get("/api/default_default_line_item?invoice_id=1&count=true");
        assertEquals(3, node.get("total").asLong());
    }
}
