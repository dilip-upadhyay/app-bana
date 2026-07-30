package com.appbana;

import com.appbana.approval.ApprovalColumns;
import com.appbana.model.EntitySchema;
import com.appbana.service.EntityCrudService;
import com.appbana.service.SessionService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.function.Executable;

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

        // Same rationale as the line_item cleanup below — customer/logs/numeric_only
        // are dedicated test fixture tables on the shared dev Postgres instance, and
        // Liquibase/flywayCleanOnStart only manage schema, not data, so rows accumulate
        // across repeated `mvn test` invocations. The new Range-filter tests (Orders
        // 19-27) assert small exact/bounded counts, so — like Orders 6-10 already do
        // for line_item — these tables must start empty on every run.
        try (java.sql.Connection conn = JdbcManager.getConnection();
                java.sql.Statement st = conn.createStatement()) {
            st.execute("DELETE FROM \"" + SchemaManager.getPhysicalTableName(customer).toUpperCase(Locale.ROOT)
                    + "\"");
            st.execute("DELETE FROM \"" + SchemaManager.getPhysicalTableName(logs).toUpperCase(Locale.ROOT)
                    + "\"");
            st.execute("DELETE FROM \"" + SchemaManager.getPhysicalTableName(numeric).toUpperCase(Locale.ROOT)
                    + "\"");
        }

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
        return getExpectStatus(path, 200);
    }

    /**
     * Review #4 — needed so {@code badTimestampFilterRejectedWith400} (and the
     * type-allowlist round-trip test) can assert a deliberate non-200 response
     * instead of only ever exercising the happy path.
     */
    private static JsonNode getExpectStatus(String path, int expectedStatus) throws IOException, InterruptedException {
        HttpClient c = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .header("X-Session-Token", TOKEN)
                .GET()
                .build();
        HttpResponse<String> resp = c.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(expectedStatus, resp.statusCode(),
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
    void badTimestampFilterRejectedWith400() throws Exception {
        // Review #4 (High A) — an unparseable typed filter value used to be
        // silently dropped (fail-open: 200 with the literal echoed back and an
        // unscoped `total`). filter= is the only scoping mechanism several
        // callers rely on, so failing open here is a correctness/data-exposure
        // hazard, not just a UX one. Deliberately fails closed with 400 now —
        // this replaces badTimestampFilterLeftLiteral, which asserted the old
        // fail-open contract.
        JsonNode node = getExpectStatus("/api/default_default_logs?filter=createdAt:notISO&count=true", 400);
        assertTrue(node.has("errors"), "400 body should have a structured field-error map");
        assertTrue(node.get("errors").has("createdAt"), "the offending field should be named in the error");
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

    // Review #4 — the round-3/round-4 commit messages both claimed the
    // coercion switches now "cover every type the AI Builder generates", and
    // both times that claim was wrong ("datetime" slipped through both
    // rounds). The cheapest way to stop the claim and the coverage from
    // drifting apart again is to make the claim itself a test: enumerate the
    // literal field-type allowlist the AI Builder hands the LLM (see
    // ai-builder's schema-field-type instructions / §11 of the copilot
    // instructions) and assert every single one round-trips insert -> exact
    // filter without a 500 and without a false zero-match. Uses assertAll so
    // one failing type doesn't hide the others.
    @Test
    @Order(11)
    void everyAiBuilderAllowlistedTypeRoundTripsInsertToFilter() {
        long ts = Instant.parse("2026-01-15T10:30:00Z").toEpochMilli();
        List<org.junit.jupiter.api.function.Executable> checks = new ArrayList<>();
        for (Object[] c : new Object[][] {
                { "text", "hello-text", "hello-text" },
                { "number", 42, "42" },
                { "decimal", "19.99", "19.99" },
                { "boolean", true, "true" },
                { "date", ts, "2026-01-15T10:30:00Z" },
                { "datetime", ts, "2026-01-15T10:30:00Z" },
                { "email", "user@example.com", "user@example.com" },
                { "phone", "+1-555-0100", "+1-555-0100" },
                { "status", "ACTIVE", "ACTIVE" },
                { "reference", 5, "5" },
                { "longtext", "long text content here", "long text content here" },
        }) {
            String type = (String) c[0];
            Object insertValue = c[1];
            String filterLiteral = (String) c[2];
            checks.add(() -> assertTypeRoundTrips(type, insertValue, filterLiteral));
        }
        assertAll("AI Builder field-type allowlist must round-trip insert -> filter", checks);
    }

    private static void assertTypeRoundTrips(String type, Object insertValue, String filterLiteral) throws Exception {
        EntityCrudService crud = new EntityCrudService();
        EntitySchema schema = new EntitySchema();
        schema.setName("rt_" + type);
        schema.setTenantId("default");
        schema.setAppId("default");
        schema.setFields(List.of(
                field("id", "long", true, true),
                field("value", type, false, false)));
        SchemaManager.saveSchema(schema);

        // Fresh table on the shared dev Postgres instance — clear it so the
        // exact-count assertion below is deterministic across repeated runs.
        try (java.sql.Connection conn = JdbcManager.getConnection();
                java.sql.Statement st = conn.createStatement()) {
            st.execute("DELETE FROM \"" + SchemaManager.getPhysicalTableName(schema).toUpperCase(Locale.ROOT)
                    + "\"");
        }

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("value", insertValue);
        crud.insertRecord(schema, row);

        // Review #5 (High A) — the server now decodes the query string exactly
        // once, via URI.getQuery() (RFC 3986: %XX only, '+' is a literal
        // character, not a space). java.net.URLEncoder is form-encoding
        // (application/x-www-form-urlencoded): it emits '+' for a space and
        // "%2B" for a literal '+'. Without the extra ".replace(\"+\", \"%20\")"
        // this test would send literal '+' characters for spaces that the
        // server would no longer convert back — swap to RFC 3986-conformant
        // percent-encoding so this test matches how a real client must encode
        // spaces for this API.
        String encoded = java.net.URLEncoder.encode(filterLiteral, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        JsonNode node = get("/api/default_default_rt_" + type + "?filter=value:=" + encoded + "&count=true");
        assertEquals(1, node.get("total").asLong(),
                "type '" + type + "' must round-trip insert -> filter without a 500 or a 0-match");
    }

    // Review #7 — the root cause was that EntitySchema.getFields() is the sole
    // authority every filter/sort/projection/groupBy path consults, and the 8
    // approval columns are physical-only (never members of getFields()) for any
    // approvalRequired entity. This test is parameterized directly over
    // ApprovalColumns.NAMES (not a hand-copied list of 8 strings) so that adding
    // a 9th approval column automatically extends coverage here without anyone
    // remembering to update this test by hand.
    @Test
    @Order(12)
    void approvalColumnsRoundTripInsertToFilterByType() throws Exception {
        String entityName = "approval_cols_rt";
        EntitySchema schema = new EntitySchema();
        schema.setName(entityName);
        schema.setTenantId("default");
        schema.setAppId("default");
        schema.setApprovalRequired(true);
        schema.setFields(List.of(field("id", "long", true, true), field("amount", "int", false, false)));
        SchemaManager.saveSchema(schema);

        String table = SchemaManager.getPhysicalTableName(schema).toUpperCase(Locale.ROOT);
        Map<String, String> ddlType = Map.of(
                "approval_status", "VARCHAR(255)",
                "approval_revision", "INTEGER",
                "approval_parent_id", "INTEGER",
                "submitted_by", "VARCHAR(255)",
                "submitted_at", "TIMESTAMP",
                "approved_by", "VARCHAR(255)",
                "approved_at", "TIMESTAMP",
                "rejection_reason", "TEXT");
        try (java.sql.Connection conn = JdbcManager.getConnection();
                java.sql.Statement st = conn.createStatement()) {
            st.execute("DELETE FROM \"" + table + "\"");
            for (String col : ApprovalColumns.NAMES) {
                String type = ddlType.get(col);
                assertNotNull(type, "test is missing a DDL type fixture for approval column '" + col + "'");
                // Deliberately raw DDL, NOT an EntitySchema.Field — reproducing the
                // "physical-only" scenario (scaffold_app enrichment /
                // batch_update_entities) for every approval column, not just the
                // handful the round-6 fixture happened to add.
                st.execute("ALTER TABLE \"" + table + "\" ADD COLUMN IF NOT EXISTS \""
                        + col.toUpperCase(Locale.ROOT) + "\" " + type);
            }
        }

        long ts1 = Instant.parse("2026-01-15T10:30:00Z").toEpochMilli();
        long ts2 = Instant.parse("2026-02-20T08:00:00Z").toEpochMilli();
        // { value1, value2, exact-filter literal for value1 }
        Map<String, Object[]> byColumn = Map.of(
                "approval_status", new Object[] { "REJECTED", "DRAFT", "REJECTED" },
                "approval_revision", new Object[] { 3, 9, "3" },
                "approval_parent_id", new Object[] { 101, 202, "101" },
                "submitted_by", new Object[] { "alice_maker", "carol_maker", "alice_maker" },
                "submitted_at", new Object[] { new Timestamp(ts1), new Timestamp(ts2), "2026-01-15T10:30:00Z" },
                "approved_by", new Object[] { "bob_checker", "dave_checker", "bob_checker" },
                "approved_at", new Object[] { new Timestamp(ts1), new Timestamp(ts2), "2026-01-15T10:30:00Z" },
                "rejection_reason", new Object[] { "missing-receipt", "wrong-amount", "missing-receipt" });

        List<Executable> checks = new ArrayList<>();
        for (String column : ApprovalColumns.NAMES) {
            Object[] cfg = byColumn.get(column);
            assertNotNull(cfg, "test is missing a values fixture for approval column '" + column + "'");
            checks.add(() -> assertApprovalColumnRoundTrips(table, entityName, column, cfg[0], cfg[1],
                    (String) cfg[2]));
        }
        assertAll("Every ApprovalColumns.NAMES entry must round-trip insert -> exact filter without a 500", checks);

        // D9 — resolveApprovalStatusFilter()'s validation-failure messages are
        // written for _approvalStatus=; when the same validation is triggered via
        // filter=approval_status: instead, the message must name the door the
        // caller actually used, not the other one.
        JsonNode invalid = getExpectStatus(
                "/api/default_default_" + entityName + "?filter=approval_status:BOGUS", 400);
        String message = invalid.get("error").asText();
        assertTrue(message.contains("filter=approval_status:"), "unexpected message: " + message);
        assertFalse(message.contains("_approvalStatus"), "unexpected message: " + message);
    }

    private static void assertApprovalColumnRoundTrips(String table, String entityName, String column,
            Object value1, Object value2, String filterLiteral) throws Exception {
        try (java.sql.Connection conn = JdbcManager.getConnection();
                java.sql.PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO \"" + table + "\" (\"" + column.toUpperCase(Locale.ROOT) + "\") VALUES (?)")) {
            ps.setObject(1, value1);
            ps.executeUpdate();
            ps.setObject(1, value2);
            ps.executeUpdate();
        }
        String encoded = java.net.URLEncoder.encode(filterLiteral, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        JsonNode node = get("/api/default_default_" + entityName + "?filter=" + column + ":=" + encoded
                + "&count=true");
        assertEquals(1, node.get("total").asLong(),
                "approval column '" + column + "' must round-trip insert -> exact filter without a 500 or a 0-match: "
                        + node);
    }

    @Test
    @Order(13)
    void unknownFieldNameFailsClosedForFilterSortFieldsAndGroupBy() throws Exception {
        // Review #7 (D7) — sort=/fields=/groupBy= used to fail OPEN on an unknown
        // field name (silently dropped/skipped/bucketed into one fake group),
        // unlike filter= which has failed closed since Review #5. All four must
        // behave the same way against the "customer" entity's real field list.
        assertTrue(getExpectStatus("/api/default_default_customer?filter=doesNotExist:=x", 400).has("error"));
        assertTrue(getExpectStatus("/api/default_default_customer?sort=doesNotExist", 400).has("error"));
        assertTrue(getExpectStatus("/api/default_default_customer?fields=doesNotExist", 400).has("error"));
        assertTrue(getExpectStatus("/api/default_default_customer?groupBy=doesNotExist", 400).has("error"));
    }

    @Test
    @Order(14)
    void approvalColumnFilterOnNonApprovalEntityFailsClosed() throws Exception {
        // Review #7 (D3) — the exemption that lets approval_status/submitted_by/...
        // bypass the getFields() check is gated on the entity actually having the
        // approval workflow enabled; "customer" does not, so this must 400 like any
        // other genuinely unknown field name, not silently succeed.
        assertTrue(getExpectStatus("/api/default_default_customer?filter=approval_status:=REJECTED", 400)
                .has("error"));
    }

    @Test
    @Order(15)
    void conflictingApprovalStatusDirectivesFailClosed() throws Exception {
        // Review #7 (D8) — _approvalStatus=X and filter=approval_status:=Y with
        // different values used to silently resolve to X with no indication Y was
        // ignored. Must 400 instead of picking a winner.
        String entityName = "approval_conflict_rt";
        EntitySchema schema = new EntitySchema();
        schema.setName(entityName);
        schema.setTenantId("default");
        schema.setAppId("default");
        schema.setApprovalRequired(true);
        schema.setFields(
                List.of(field("id", "long", true, true), field("approval_status", "string", false, false)));
        SchemaManager.saveSchema(schema);

        assertTrue(getExpectStatus("/api/default_default_" + entityName
                + "?_approvalStatus=REJECTED&filter=approval_status:=APPROVED", 400).has("error"));

        // The same value through both doors is not a conflict.
        getExpectStatus(
                "/api/default_default_" + entityName + "?_approvalStatus=REJECTED&filter=approval_status:=REJECTED",
                200);
    }

    @Test
    @Order(16)
    void groupByOnApprovalColumnDoesNotContradictGroupCounts() throws Exception {
        // Review #8 (High) — groupByParam resolves through getQueryableFields()
        // (Review #7), which includes the 8 approval columns, but those are
        // excluded from the DEFAULT projection the `rows` are fetched with. The
        // per-page Java bucketing used to read row.get(groupByParam) against rows
        // that never had the column, collapsing every row into a single "" bucket
        // in `groups` while `groupCounts` (SQL, unaffected by projection) reported
        // the true per-group breakdown in the SAME response body. Assert the two
        // halves of the response never disagree: either `groups` is correctly
        // bucketed (because the column IS on the fetched rows) or it is omitted
        // entirely — never a single fake "" bucket sitting next to a correct
        // `groupCounts`.
        String entityName = "approval_group_rt";
        EntitySchema schema = new EntitySchema();
        schema.setName(entityName);
        schema.setTenantId("default");
        schema.setAppId("default");
        schema.setApprovalRequired(true);
        schema.setFields(List.of(field("id", "long", true, true)));
        SchemaManager.saveSchema(schema);

        String table = SchemaManager.getPhysicalTableName(schema).toUpperCase(Locale.ROOT);
        try (java.sql.Connection conn = JdbcManager.getConnection();
                java.sql.Statement st = conn.createStatement()) {
            st.execute("DELETE FROM \"" + table + "\"");
            st.execute("ALTER TABLE \"" + table + "\" ADD COLUMN IF NOT EXISTS \"APPROVAL_STATUS\" VARCHAR(255)");
            st.execute("INSERT INTO \"" + table + "\" (\"APPROVAL_STATUS\") VALUES ('DRAFT')");
            st.execute("INSERT INTO \"" + table + "\" (\"APPROVAL_STATUS\") VALUES ('DRAFT')");
            st.execute("INSERT INTO \"" + table + "\" (\"APPROVAL_STATUS\") VALUES ('PENDING')");
        }

        // Default projection (no fields=): `groups`, if present at all, must not
        // contain a bucket with an empty key while `groupCounts` shows a real split.
        JsonNode defaultProjection = getExpectStatus(
                "/api/default_default_" + entityName + "?groupBy=approval_status", 200);
        Map<String, Long> groupCounts = new HashMap<>();
        defaultProjection.get("groupCounts").fields()
                .forEachRemaining(e -> groupCounts.put(e.getKey(), e.getValue().asLong()));
        assertEquals(2L, groupCounts.get("DRAFT"));
        assertEquals(1L, groupCounts.get("PENDING"));
        if (defaultProjection.has("groups")) {
            for (JsonNode g : defaultProjection.get("groups")) {
                assertFalse(g.get("key").asText().isEmpty(),
                        "a `groups` entry must never carry an empty key while groupCounts reports a real split: "
                                + defaultProjection);
            }
        }

        // Explicit fields= including the group column: `groups` must now be
        // present AND correctly bucketed, agreeing with `groupCounts`.
        JsonNode explicitProjection = getExpectStatus(
                "/api/default_default_" + entityName + "?groupBy=approval_status&fields=id,approval_status", 200);
        assertTrue(explicitProjection.has("groups"), "groups must be present once the column is in the projection");
        Map<String, Long> groupsSeen = new HashMap<>();
        for (JsonNode g : explicitProjection.get("groups")) {
            groupsSeen.put(g.get("key").asText(), g.get("count").asLong());
        }
        assertEquals(2L, groupsSeen.get("DRAFT"));
        assertEquals(1L, groupsSeen.get("PENDING"));
    }

    @Test
    @Order(17)
    void declaredApprovalColumnIsNotShadowedBySyntheticDefinition() throws Exception {
        // Review #8 (Nit) — when a schema declares one of the 8 approval columns
        // as a real EntitySchema.Field of its own (as this test does), the
        // synthetic ApprovalColumns.asFields() definition used to be appended
        // unconditionally, so getQueryableFields() held two entries for the same
        // name: fieldMap-building callers (parseFilters/listAdvanced) put()
        // last-wins so the synthetic one silently shadowed the declared one,
        // while resolveQueryableField()'s loop is first-wins and returned the
        // declared one instead — two lookup paths disagreeing about which Field
        // was authoritative for the same name. This just needs filter/sort/
        // groupBy to resolve and behave consistently with no contradiction.
        String entityName = "approval_declared_field_rt";
        EntitySchema schema = new EntitySchema();
        schema.setName(entityName);
        schema.setTenantId("default");
        schema.setAppId("default");
        schema.setApprovalRequired(true);
        schema.setFields(List.of(field("id", "long", true, true),
                field("approval_status", "string", false, false)));
        SchemaManager.saveSchema(schema);

        String table = SchemaManager.getPhysicalTableName(schema).toUpperCase(Locale.ROOT);
        try (java.sql.Connection conn = JdbcManager.getConnection();
                java.sql.Statement st = conn.createStatement()) {
            st.execute("DELETE FROM \"" + table + "\"");
            st.execute("INSERT INTO \"" + table + "\" (\"APPROVAL_STATUS\") VALUES ('DRAFT')");
            st.execute("INSERT INTO \"" + table + "\" (\"APPROVAL_STATUS\") VALUES ('APPROVED')");
        }

        JsonNode filtered = getExpectStatus(
                "/api/default_default_" + entityName + "?filter=approval_status:=DRAFT&count=true", 200);
        assertEquals(1L, filtered.get("total").asLong());

        JsonNode sorted = getExpectStatus(
                "/api/default_default_" + entityName + "?sort=approval_status&fields=id,approval_status", 200);
        assertEquals(2, sorted.get("rows").size());

        JsonNode grouped = getExpectStatus(
                "/api/default_default_" + entityName + "?groupBy=approval_status&fields=id,approval_status", 200);
        Map<String, Long> counts = new HashMap<>();
        grouped.get("groupCounts").fields().forEachRemaining(e -> counts.put(e.getKey(), e.getValue().asLong()));
        assertEquals(1L, counts.get("DRAFT"));
        assertEquals(1L, counts.get("APPROVED"));
    }

    @Test
    @Order(18)
    void bareGetWithNoQueryParamsProjectsDeclaredFieldsOnlyOnApprovalEntity() throws Exception {
        // Review #9 (High) — a bare `GET /api/{entity}` with NO query parameters
        // takes the `!anyAdv` simple-list branch, which calls
        // EntityCrudService.listAll(EntitySchema) — a plain SELECT * before this
        // fix. Review #7's default-projection leak guardrail was enforced in
        // listAdvanced()'s default (no fields=) path but never on THIS branch, so
        // every approval column (raw, uppercase DB keys) leaked into the response
        // of the one request shape a parameter-by-parameter sweep never probes:
        // the caller sending nothing at all. listAll() now projects
        // schema.getFields() explicitly, same guardrail as listAdvanced().
        String entityName = "approval_bare_get_rt";
        EntitySchema schema = new EntitySchema();
        schema.setName(entityName);
        schema.setTenantId("default");
        schema.setAppId("default");
        schema.setApprovalRequired(true);
        schema.setFields(List.of(field("id", "long", true, true), field("title", "string", false, false)));
        SchemaManager.saveSchema(schema);

        String table = SchemaManager.getPhysicalTableName(schema).toUpperCase(Locale.ROOT);
        try (java.sql.Connection conn = JdbcManager.getConnection();
                java.sql.Statement st = conn.createStatement()) {
            st.execute("DELETE FROM \"" + table + "\"");
            // Deliberately raw DDL, NOT EntitySchema.Field entries — reproducing
            // the "physical-only" approval columns scenario, same as the other
            // tests in this suite.
            st.execute("ALTER TABLE \"" + table + "\" ADD COLUMN IF NOT EXISTS \"APPROVAL_STATUS\" VARCHAR(255)");
            st.execute("ALTER TABLE \"" + table + "\" ADD COLUMN IF NOT EXISTS \"SUBMITTED_BY\" VARCHAR(255)");
            st.execute("INSERT INTO \"" + table + "\" (\"TITLE\", \"APPROVAL_STATUS\", \"SUBMITTED_BY\") "
                    + "VALUES ('widget', 'PENDING', 'alice_maker')");
        }

        JsonNode rows = get("/api/default_default_" + entityName);
        assertTrue(rows.isArray(), "bare GET with no query params returns a plain row array: " + rows);
        assertEquals(1, rows.size());
        JsonNode row = rows.get(0);
        List<String> keys = new ArrayList<>();
        row.fieldNames().forEachRemaining(keys::add);
        assertEquals(Set.of("id", "title"), new HashSet<>(keys),
                "bare GET on an approval-required entity must return exactly the declared fields, "
                        + "no approval columns: " + row);
    }

    // Column-filter/sort/scale hardening — the "min..max" double-dot range
    // operator (filter=field:min..max). customer.age is seeded 20..24 across
    // 5 rows (see setup()); line_item.unit_price is seeded 9.99 / 4.50 / 100.00.

    @Test
    @Order(19)
    void rangeFilterOnIntColumnScopesToBoundedSubset() throws Exception {
        JsonNode node = get("/api/default_default_customer?filter=age:22..24&count=true");
        assertEquals(3, node.get("total").asLong(), "age in [22,24] should match ages 22, 23, 24");
    }

    @Test
    @Order(20)
    void openEndedLowerBoundRangeMatchesEverythingAtOrAbove() throws Exception {
        JsonNode node = get("/api/default_default_customer?filter=age:23..&count=true");
        assertEquals(2, node.get("total").asLong(), "age >= 23 should match ages 23, 24 only");
    }

    @Test
    @Order(21)
    void openEndedUpperBoundRangeMatchesEverythingAtOrBelow() throws Exception {
        JsonNode node = get("/api/default_default_customer?filter=age:..21&count=true");
        assertEquals(2, node.get("total").asLong(), "age <= 21 should match ages 20, 21 only");
    }

    @Test
    @Order(22)
    void rangeFilterOnDecimalColumnScopesToBoundedSubset() throws Exception {
        JsonNode node = get("/api/default_default_line_item?filter=unit_price:5..50&count=true");
        assertEquals(1, node.get("total").asLong(), "only unit_price 9.99 falls within [5, 50]");
    }

    @Test
    @Order(23)
    void rangeFilterOnTimestampColumnWithOpenUpperBound() throws Exception {
        // Every seeded logs row was inserted "now" — a lower bound far in the
        // past with no upper bound must match all 3 without needing exact times.
        JsonNode node = get("/api/default_default_logs?filter=createdAt:1900-01-01T00:00:00Z..&count=true");
        assertEquals(3, node.get("total").asLong());
    }

    @Test
    @Order(24)
    void rangeFilterOnNonOrderableFieldRejectedWith400() throws Exception {
        // firstName is a plain string column — "min..max" only makes sense for
        // orderable (numeric/date/reference) kinds; must fail closed, not
        // silently ignore the range and return an unscoped list.
        JsonNode node = getExpectStatus("/api/default_default_customer?filter=firstName:A..Z&count=true", 400);
        assertTrue(node.has("errors") && node.get("errors").has("firstName"),
                "the offending field should be named in the error: " + node);
    }

    @Test
    @Order(25)
    void rangeFilterWithUnparseableBoundRejectedWith400() throws Exception {
        JsonNode node = getExpectStatus("/api/default_default_customer?filter=age:abc..30&count=true", 400);
        assertTrue(node.has("errors") && node.get("errors").has("age"), "malformed bound should 400: " + node);
    }

    @Test
    @Order(26)
    void rangeFilterWithNeitherBoundRejectedWith400() throws Exception {
        // "age:.." — both sides empty — is meaningless, not "no filter".
        JsonNode node = getExpectStatus("/api/default_default_customer?filter=age:..&count=true", 400);
        assertTrue(node.has("errors") && node.get("errors").has("age"), "empty range should 400: " + node);
    }

    @Test
    @Order(27)
    void syncIndexesCreatesBtreeAndTrigramIndexesOnPostgres() throws Exception {
        // Scale hardening — every ensureTable() call (see saveSchema() in
        // setup()) should leave the customer table with more than just its PK
        // index: a plain B-tree per column, plus (Postgres-only) a pg_trgm GIN
        // index on the STRING columns (firstName, lastName) so the default
        // substring ILIKE filter isn't a full table scan once this table has
        // millions of rows.
        EntitySchema customer = new EntitySchema();
        customer.setName("customer");
        customer.setTenantId("default");
        customer.setAppId("default");
        String table = SchemaManager.getPhysicalTableName(customer);

        List<String> indexDefs = new ArrayList<>();
        try (java.sql.Connection conn = JdbcManager.getConnection();
                java.sql.PreparedStatement ps = conn.prepareStatement(
                        "SELECT indexdef FROM pg_indexes WHERE tablename = ?")) {
            // getPhysicalTableName() already returns the upper-cased identifier
            // this table was actually quoted+created with (see SchemaManager.quote()) —
            // Postgres preserves case for quoted identifiers, so pg_indexes.tablename
            // is this same uppercase string, not the SQL-standard-default lowercase.
            ps.setString(1, table);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) indexDefs.add(rs.getString(1));
            }
        }
        assertTrue(indexDefs.size() > 1, "expected more than just the PK index on " + table + ": " + indexDefs);
        assertTrue(indexDefs.stream().anyMatch(d -> d.toLowerCase(Locale.ROOT).contains("gin")),
                "expected at least one GIN trigram index on a string column: " + indexDefs);
    }
}
