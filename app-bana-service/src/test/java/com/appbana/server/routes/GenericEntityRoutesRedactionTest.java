package com.appbana.server.routes;

import com.appbana.ApiServer;
import com.appbana.JdbcManager;
import com.appbana.SchemaManager;
import com.appbana.model.EntitySchema;
import com.appbana.service.PasswordService;
import com.appbana.service.SessionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GenericEntityRoutesRedactionTest — Task S4.8 (Tenant Isolation Security Plan).
 *
 * <p>Before this task, every generic-entity read-path response — {@code GET /api/{entity}} (both
 * the simple and advanced/paginated shapes), {@code GET /api/{entity}/{id}}, bulk-export, and the
 * studio- and env-scoped list/single route families — returned a "password"/"secret"-named
 * column's real stored value straight to the client. This formalizes, as automated tests over
 * <b>real HTTP dispatch</b> (not just a unit test of the redaction utility itself, which cannot
 * catch "the utility exists but a route forgot to call it"), that every one of those call sites
 * now omits such a column entirely from its response.
 *
 * <p>Also covers two related fixes discovered during S4.8 implementation, beyond the original
 * per-route census: (1) {@code groupBy=} on a sensitive column now 400s, because the resulting
 * {@code groupCounts} map would otherwise leak the raw column value as a JSON object KEY — a leak
 * vector {@code rows} redaction cannot touch; (2) {@code GET /audit}'s {@code before}/{@code
 * after}/{@code changes} snapshots are now redacted too, since {@code AuditLogService} persists
 * (and returns) a complete, unredacted copy of every row on every INSERT/UPDATE/DELETE.
 *
 * <p>Finally, {@link #testGetThenPutRoundTripDoesNotCorruptStoredPassword()} proves the specific
 * safety argument that justified redacting-by-omission rather than by placeholder: a client that
 * never received the password value (because GET now omits it) can still safely PUT an unrelated
 * field without nulling out or corrupting the real stored value, because {@code updateById} treats
 * an absent key as "leave this column alone" ({@code data.containsKey(f.getName())}).
 */
public class GenericEntityRoutesRedactionTest {

    private static final int PORT = 18108;
    private static final String BASE_URL = "http://localhost:" + PORT;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TENANT_ID = "t_s48_redact";
    private static final String APP_ID = "app_s48_redact";
    private static final String ENTITY_NAME = "RedactUser";

    private static String sessionToken;

    @BeforeAll
    public static void startServer() throws Exception {
        ApiServer.startJdk(PORT);
        // Scoped to (TENANT_ID, APP_ID) so EntityAccessGuard's rule (ii) admits without needing a
        // separate AppMembershipService grant — mirrors AuditLogTest's own fixture pattern.
        sessionToken = SessionService.createSession("s48-redact-user", TENANT_ID, APP_ID).sessionId();
    }

    // Scoped to this test class's OWN fixture tenant/app only — never a blanket statement
    // against the shared dev Postgres (see RoleRoutesSecurityTest for why that matters).
    @BeforeEach
    public void setUp() {
        cleanUpFixtures();
        saveFixtureSchema();
    }

    @AfterEach
    public void tearDown() {
        cleanUpFixtures();
    }

    private void cleanUpFixtures() {
        try (Connection c = JdbcManager.getConnection("default");
                Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS \"" + physicalTableName().toUpperCase() + "\"");
            s.execute("DELETE FROM appbana_schemas WHERE tenant_id = '" + TENANT_ID + "' AND app_id = '" + APP_ID + "'");
            // appbana_audit is keyed only by (entity short-name, pk) with NO tenant/app scoping
            // columns at all (confirmed by reading AuditLogService's INSERT statement) — dropping
            // and recreating the physical table above resets the identity sequence back to 1 on
            // every test method, so without this cleanup, audit rows from earlier test methods in
            // this class (same entity name "RedactUser", same reused pk) would silently leak into
            // a later method's /audit query and inflate its row count. ENTITY_NAME is unique to
            // this test class, so scoping the purge to it cannot affect any other test class.
            s.execute("DELETE FROM appbana_audit WHERE entity = '" + ENTITY_NAME + "'");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String physicalTableName() {
        EntitySchema s = new EntitySchema();
        s.setTenantId(TENANT_ID);
        s.setAppId(APP_ID);
        s.setName(ENTITY_NAME);
        return SchemaManager.getPhysicalTableName(s);
    }

    private void saveFixtureSchema() {
        EntitySchema s = new EntitySchema();
        s.setName(ENTITY_NAME);
        s.setTenantId(TENANT_ID);
        s.setAppId(APP_ID);

        EntitySchema.Field id = new EntitySchema.Field("id", "long", true, true, null);
        EntitySchema.Field email = new EntitySchema.Field("email", "string", false, false, null);
        // Two independently-named sensitive columns: the exact-match "password" S4.2 hashes on
        // write, and a differently-named "api_secret" column proving the redaction is name-based
        // (substring "secret"), not limited to the one column S4.2 special-cases.
        EntitySchema.Field password = new EntitySchema.Field("password", "string", false, false, null);
        EntitySchema.Field apiSecret = new EntitySchema.Field("api_secret", "string", false, false, null);
        EntitySchema.Field name = new EntitySchema.Field("name", "string", false, false, null);

        s.setFields(List.of(id, email, password, apiSecret, name));
        SchemaManager.saveSchema(s);
    }

    private String packedKey() {
        return TENANT_ID + "_" + APP_ID + "_" + ENTITY_NAME;
    }

    // ---------------------------------------------------------------- HTTP helpers

    private HttpResponse<String> send(String method, String path, String bodyOrNull) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder().uri(URI.create(BASE_URL + path))
                .header("X-Session-Token", sessionToken);
        switch (method) {
            case "GET" -> b.GET();
            default -> b.method(method, HttpRequest.BodyPublishers.ofString(bodyOrNull != null ? bodyOrNull : "{}"))
                    .header("Content-Type", "application/json");
        }
        return HTTP_CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private Map<String, Object> readObject(String json) throws Exception {
        return MAPPER.readValue(json, new TypeReference<>() {
        });
    }

    private List<Map<String, Object>> readArray(String json) throws Exception {
        return MAPPER.readValue(json, new TypeReference<>() {
        });
    }

    /** Case-insensitive: true if ANY key in the map contains "password" or "secret". */
    private static boolean anyKeyLeaksSensitiveName(Map<String, Object> row) {
        if (row == null) return false;
        for (String key : row.keySet()) {
            String lower = key.toLowerCase(Locale.ROOT);
            if (lower.contains("password") || lower.contains("secret")) {
                return true;
            }
        }
        return false;
    }

    /** Row maps come back with driver-cased keys (typically uppercase for listAll/getById's raw
     *  SELECT *, but exactly as declared for listAdvanced's projected/aliased SELECT) — probe all
     *  three forms, mirroring RevisionFlowTest's own val() helper. */
    private static Object val(Map<String, Object> row, String column) {
        if (row == null) return null;
        if (row.containsKey(column)) return row.get(column);
        if (row.containsKey(column.toUpperCase(Locale.ROOT))) return row.get(column.toUpperCase(Locale.ROOT));
        return row.get(column.toLowerCase(Locale.ROOT));
    }

    private long insertFixtureRow(String email, String password, String apiSecret, String name) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", password);
        body.put("api_secret", apiSecret);
        body.put("name", name);
        HttpResponse<String> resp = send("POST", "/api/" + packedKey(), MAPPER.writeValueAsString(body));
        assertEquals(201, resp.statusCode(), resp.body());
        Map<String, Object> created = readObject(resp.body());
        return ((Number) created.get("id")).longValue();
    }

    private String fetchStoredColumn(long id, String column) throws Exception {
        String table = physicalTableName().toUpperCase(Locale.ROOT);
        String sql = "SELECT \"" + column.toUpperCase(Locale.ROOT) + "\" FROM \"" + table + "\" WHERE \"ID\" = ?";
        try (Connection c = JdbcManager.getConnection("default");
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "fixture row " + id + " must exist");
                return rs.getString(1);
            }
        }
    }

    // ================================================================
    // GET /api/{entity} — simple list (listAll path, no query params)
    // ================================================================

    @Test
    public void testSimpleListRedactsPasswordAndSecret() throws Exception {
        insertFixtureRow("alice@example.com", "PlainPass123!", "sk-live-abc", "Alice");

        HttpResponse<String> resp = send("GET", "/api/" + packedKey(), null);
        assertEquals(200, resp.statusCode(), resp.body());

        List<Map<String, Object>> rows = readArray(resp.body());
        assertFalse(rows.isEmpty());
        for (Map<String, Object> row : rows) {
            assertFalse(anyKeyLeaksSensitiveName(row),
                    "simple list row must not expose a password/secret-named key: " + row.keySet());
        }
        assertEquals("alice@example.com", val(rows.get(0), "email"),
                "redaction must not over-strip non-sensitive fields");
    }

    // ================================================================
    // GET /api/{entity} — advanced/paginated list (listAdvanced path)
    // ================================================================

    @Test
    public void testAdvancedListRedactsPasswordAndSecret() throws Exception {
        insertFixtureRow("bob@example.com", "BobPass123!", "sk-live-bob", "Bob");

        HttpResponse<String> resp = send("GET", "/api/" + packedKey() + "?limit=10", null);
        assertEquals(200, resp.statusCode(), resp.body());

        Map<String, Object> out = readObject(resp.body());
        assertTrue(out.containsKey("rows"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) out.get("rows");
        assertFalse(rows.isEmpty());
        for (Map<String, Object> row : rows) {
            assertFalse(anyKeyLeaksSensitiveName(row),
                    "advanced list row must not expose a password/secret-named key: " + row.keySet());
        }
        assertEquals("bob@example.com", val(rows.get(0), "email"));
    }

    // ================================================================
    // GET /api/{entity}/{id} — single record
    // ================================================================

    @Test
    public void testSingleRecordRedactsPasswordAndSecret() throws Exception {
        long id = insertFixtureRow("carol@example.com", "CarolPass123!", "sk-live-carol", "Carol");

        HttpResponse<String> resp = send("GET", "/api/" + packedKey() + "/" + id, null);
        assertEquals(200, resp.statusCode(), resp.body());

        Map<String, Object> row = readObject(resp.body());
        assertFalse(anyKeyLeaksSensitiveName(row), "single record must not expose a password/secret-named key: " + row.keySet());
        assertEquals("carol@example.com", val(row, "email"));
        assertEquals("Carol", val(row, "name"));

        // The real value must still be genuinely present (and, per S4.2, hashed) in the database
        // — this test must fail if insertFixtureRow silently didn't persist a password at all.
        assertTrue(PasswordService.looksLikeBcryptHash(fetchStoredColumn(id, "password")));
    }

    // ================================================================
    // POST /api/{entity}/bulk-export
    // ================================================================

    @Test
    public void testBulkExportRedactsPasswordAndSecret() throws Exception {
        long id = insertFixtureRow("dave@example.com", "DavePass123!", "sk-live-dave", "Dave");

        String body = MAPPER.writeValueAsString(Map.of("ids", List.of(id)));
        HttpResponse<String> resp = send("POST", "/api/" + packedKey() + "/bulk-export", body);
        assertEquals(200, resp.statusCode(), resp.body());

        Map<String, Object> out = readObject(resp.body());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) out.get("rows");
        assertEquals(1, rows.size());
        assertFalse(anyKeyLeaksSensitiveName(rows.get(0)),
                "bulk-export row must not expose a password/secret-named key: " + rows.get(0).keySet());
        assertEquals("dave@example.com", val(rows.get(0), "email"));
    }

    // ================================================================
    // groupBy= on a sensitive column (groupCounts key-leak guard)
    // ================================================================

    @Test
    public void testGroupByOnSensitiveColumnIsRejectedWith400() throws Exception {
        insertFixtureRow("erin@example.com", "ErinPass123!", "sk-live-erin", "Erin");

        HttpResponse<String> resp = send("GET", "/api/" + packedKey() + "?groupBy=password", null);
        assertEquals(400, resp.statusCode(), resp.body());
        Map<String, Object> out = readObject(resp.body());
        String error = String.valueOf(out.get("error"));
        assertTrue(error.contains("sensitive"), "error message should explain the rejection: " + error);
    }

    @Test
    public void testGroupByOnDifferentlyNamedSensitiveColumnIsRejectedWith400() throws Exception {
        insertFixtureRow("frank@example.com", "FrankPass123!", "sk-live-frank", "Frank");

        HttpResponse<String> resp = send("GET", "/api/" + packedKey() + "?groupBy=api_secret", null);
        assertEquals(400, resp.statusCode(), resp.body());
        Map<String, Object> out = readObject(resp.body());
        assertTrue(String.valueOf(out.get("error")).contains("sensitive"));
    }

    @Test
    public void testGroupByOnNonSensitiveColumnStillWorks() throws Exception {
        insertFixtureRow("grace@example.com", "GracePass123!", "sk-live-grace", "Grace");

        // Regression guard: the new S4.8 groupBy guard must not over-reject a legitimate,
        // non-sensitive groupBy column.
        HttpResponse<String> resp = send("GET", "/api/" + packedKey() + "?groupBy=name", null);
        assertEquals(200, resp.statusCode(), resp.body());
        Map<String, Object> out = readObject(resp.body());
        assertTrue(out.containsKey("groupCounts"));
    }

    // ================================================================
    // Studio-scoped list + single: /appbana-studio/{tenantId}/apps/{appId}/{entity}[/{id}]
    // ================================================================

    @Test
    public void testStudioScopedListRedactsPasswordAndSecret() throws Exception {
        insertFixtureRow("henry@example.com", "HenryPass123!", "sk-live-henry", "Henry");

        String path = "/appbana-studio/" + TENANT_ID + "/apps/" + APP_ID + "/" + ENTITY_NAME;
        HttpResponse<String> resp = send("GET", path, null);
        assertEquals(200, resp.statusCode(), resp.body());

        Map<String, Object> out = readObject(resp.body());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) out.get("rows");
        assertFalse(rows.isEmpty());
        for (Map<String, Object> row : rows) {
            assertFalse(anyKeyLeaksSensitiveName(row), "studio-scoped list row leaked a sensitive key: " + row.keySet());
        }
    }

    @Test
    public void testStudioScopedSingleRedactsPasswordAndSecret() throws Exception {
        long id = insertFixtureRow("iris@example.com", "IrisPass123!", "sk-live-iris", "Iris");

        String path = "/appbana-studio/" + TENANT_ID + "/apps/" + APP_ID + "/" + ENTITY_NAME + "/" + id;
        HttpResponse<String> resp = send("GET", path, null);
        assertEquals(200, resp.statusCode(), resp.body());

        Map<String, Object> row = readObject(resp.body());
        assertFalse(anyKeyLeaksSensitiveName(row), "studio-scoped single row leaked a sensitive key: " + row.keySet());
        assertEquals("iris@example.com", val(row, "email"));
    }

    // ================================================================
    // Env-scoped list + single: /api/{tenantId}/apps/{appId}/env/{env}/{entity}[/{id}]
    // ================================================================

    @Test
    public void testEnvScopedListRedactsPasswordAndSecret() throws Exception {
        insertFixtureRow("jack@example.com", "JackPass123!", "sk-live-jack", "Jack");

        String path = "/api/" + TENANT_ID + "/apps/" + APP_ID + "/env/dev/" + ENTITY_NAME;
        HttpResponse<String> resp = send("GET", path, null);
        assertEquals(200, resp.statusCode(), resp.body());

        Map<String, Object> out = readObject(resp.body());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) out.get("rows");
        assertFalse(rows.isEmpty());
        for (Map<String, Object> row : rows) {
            assertFalse(anyKeyLeaksSensitiveName(row), "env-scoped list row leaked a sensitive key: " + row.keySet());
        }
    }

    @Test
    public void testEnvScopedSingleRedactsPasswordAndSecret() throws Exception {
        long id = insertFixtureRow("kate@example.com", "KatePass123!", "sk-live-kate", "Kate");

        String path = "/api/" + TENANT_ID + "/apps/" + APP_ID + "/env/dev/" + ENTITY_NAME + "/" + id;
        HttpResponse<String> resp = send("GET", path, null);
        assertEquals(200, resp.statusCode(), resp.body());

        Map<String, Object> row = readObject(resp.body());
        assertFalse(anyKeyLeaksSensitiveName(row), "env-scoped single row leaked a sensitive key: " + row.keySet());
        assertEquals("kate@example.com", val(row, "email"));
    }

    // ================================================================
    // GET /audit — before/after/changes redaction
    // ================================================================

    @Test
    public void testAuditRouteRedactsBeforeAfterAndChanges() throws Exception {
        long id = insertFixtureRow("liam@example.com", "LiamOldPass123!", "sk-live-liam", "Liam");

        // Update an unrelated field AND the password, so both "after" (from INSERT+UPDATE) and
        // "changes" (from the UPDATE) carry a password value to potentially leak.
        String updateBody = MAPPER.writeValueAsString(Map.of("name", "Liam Updated", "password", "LiamNewPass456!"));
        HttpResponse<String> putResp = send("PUT", "/api/" + packedKey() + "/" + id, updateBody);
        assertEquals(200, putResp.statusCode(), putResp.body());

        String auditPath = "/audit?entity=" + ENTITY_NAME + "&pk=" + id + "&limit=10";
        HttpResponse<String> resp = send("GET", auditPath, null);
        assertEquals(200, resp.statusCode(), resp.body());

        Map<String, Object> out = readObject(resp.body());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) out.get("rows");
        assertEquals(2, rows.size(), "expected one INSERT row and one UPDATE row");

        for (Map<String, Object> auditRow : rows) {
            for (String key : List.of("before", "after", "changes")) {
                Object snapshot = auditRow.get(key);
                if (snapshot instanceof Map<?, ?> snapshotMap) {
                    for (Object snapshotKey : snapshotMap.keySet()) {
                        String lower = String.valueOf(snapshotKey).toLowerCase(Locale.ROOT);
                        assertFalse(lower.contains("password") || lower.contains("secret"),
                                "audit " + key + " leaked a sensitive key '" + snapshotKey + "' on op="
                                        + auditRow.get("op"));
                    }
                }
            }
        }

        // Non-sensitive audit data must still be genuinely present — the redaction must not have
        // over-stripped the whole snapshot instead of just the sensitive keys.
        Map<String, Object> updateRow = rows.get(1);
        assertEquals("UPDATE", updateRow.get("op"));
        @SuppressWarnings("unchecked")
        Map<String, Object> changes = (Map<String, Object>) updateRow.get("changes");
        assertNotNull(changes);
        assertTrue(changes.containsKey("NAME") || changes.containsKey("name"),
                "the unrelated field's change must still be visible in the audit trail: " + changes.keySet());
    }

    // ================================================================
    // GET-then-PUT round trip: omitting the password key must not corrupt it
    // ================================================================

    @Test
    public void testGetThenPutRoundTripDoesNotCorruptStoredPassword() throws Exception {
        long id = insertFixtureRow("mona@example.com", "MonaOriginalPass123!", "sk-live-mona", "Mona");
        // The stored value is a BCrypt hash of the plaintext above (S4.2), captured here — before
        // any redaction-related round trip — as the ground truth to compare against afterwards.
        String storedHashBeforeRoundTrip = fetchStoredColumn(id, "password");
        assertTrue(PasswordService.looksLikeBcryptHash(storedHashBeforeRoundTrip));
        assertTrue(PasswordService.verifyPassword("MonaOriginalPass123!", storedHashBeforeRoundTrip));

        // 1. A client fetches the record — the password key is absent, by design.
        HttpResponse<String> getResp = send("GET", "/api/" + packedKey() + "/" + id, null);
        assertEquals(200, getResp.statusCode(), getResp.body());
        Map<String, Object> fetched = readObject(getResp.body());
        assertFalse(anyKeyLeaksSensitiveName(fetched));

        // 2. The client edits only a field it actually has (name) and PUTs the update back. It
        // never re-sends "password" because it was never given the value to resend — this is
        // the exact round trip S4.8's "omit the key" design had to be proven safe against.
        String putBody = MAPPER.writeValueAsString(Map.of("name", "Mona Updated"));
        HttpResponse<String> putResp = send("PUT", "/api/" + packedKey() + "/" + id, putBody);
        assertEquals(200, putResp.statusCode(), putResp.body());
        Map<String, Object> putResult = readObject(putResp.body());
        assertEquals(1, ((Number) putResult.get("updated")).intValue());

        // 3. The stored password hash must be byte-for-byte unchanged (never nulled, never
        // re-hashed); the stored name must reflect the edit.
        assertEquals(storedHashBeforeRoundTrip, fetchStoredColumn(id, "password"),
                "omitting the password key from GET must never cause a subsequent PUT to null out "
                        + "or corrupt the real stored value");
        assertEquals("Mona Updated", fetchStoredColumn(id, "name"));
    }
}
