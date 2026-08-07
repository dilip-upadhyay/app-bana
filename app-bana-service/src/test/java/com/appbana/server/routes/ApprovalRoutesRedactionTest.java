package com.appbana.server.routes;

import com.appbana.ApiServer;
import com.appbana.JdbcManager;
import com.appbana.SchemaManager;
import com.appbana.approval.ApprovalService;
import com.appbana.approval.UserRoleService;
import com.appbana.model.EntitySchema;
import com.appbana.security.AppMembershipService;
import com.appbana.service.EntityCrudService;
import com.appbana.service.PasswordService;
import com.appbana.service.SessionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * ApprovalRoutesRedactionTest — Task S4.8 (Tenant Isolation Security Plan), maker-checker
 * approval routes.
 *
 * <p>Covers two things beyond {@link GenericEntityRoutesRedactionTest}'s generic-entity read
 * paths:
 *
 * <ol>
 *   <li>{@link #testPendingQueueRedactsPasswordColumn()} — the original 8th census item:
 *       {@code GET .../approvals/pending}'s {@code records[]} must not carry a password/secret
 *       column's real value to the checker reviewing the queue.</li>
 *   <li>{@link #testAuditTrailRedactsPasswordFromMergeDiff()} — an <b>additional leak point</b>
 *       discovered while writing this test class, beyond the original 8-site census and the 2
 *       extra discoveries already fixed in {@code GenericEntityRoutes} ({@code groupCounts},
 *       {@code GET /audit}): {@code GET .../records/{id}/approvals/audit} returns
 *       {@code ApprovalService.mergeRevisionIntoParent}'s pre-merge {@code diff}, which is a
 *       JSON-serialized STRING (not an already-parsed {@code Map}, unlike every other S4.8 call
 *       site) carrying a full {@code before}/{@code after} row snapshot — including any
 *       password/secret column's real value. Fixed in {@code ApprovalRoutes.java}'s
 *       {@code /approvals/audit} handler by parsing, redacting, and re-serializing that string.</li>
 *   <li>{@link #testInternalRevisionMergeStillCarriesRealPasswordValueForward()} — the safety
 *       regression proving S4.8's redaction is route-layer-only: the internal merge machinery
 *       ({@link GenericEntityRoutes#applyApprovalPutGuard} and {@code ApprovalService}'s
 *       submit/approve/merge, called directly with no HTTP involved) must keep carrying the REAL
 *       password value from a DRAFT revision into the merged live parent row — if this test ever
 *       broke, it would mean redaction had leaked into an internal code path that legitimately
 *       needs the real value.</li>
 * </ol>
 */
public class ApprovalRoutesRedactionTest {

    private static final int PORT = 18109;
    private static final String BASE_URL = "http://localhost:" + PORT;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TENANT_ID = "t_s48_appr";
    private static final String APP_ID = "app_s48_appr";
    private static final String ENTITY_NAME = "ApprovalSecret";
    private static final String TABLE_NAME = "APP_T_S48_APPR_APP_S48_APPR_APPROVALSECRET";

    private static final String MAKER = "maker_s48";
    private static final String CHECKER = "checker_s48";

    private static String makerSessionToken;
    private static String checkerSessionToken;

    private final EntityCrudService crud = new EntityCrudService();
    private EntitySchema schema;

    @BeforeAll
    public static void startServerAndSessions() throws Exception {
        ApiServer.startJdk(PORT);

        makerSessionToken = SessionService.createSession(MAKER).sessionId();
        checkerSessionToken = SessionService.createSession(CHECKER).sessionId();

        // S3.4 — EntityAccessGuard rule (i): membership, not just a maker/checker business role,
        // is what admits these calls at all (mirrors ApprovalRoutesSecurityTest's own fixture).
        AppMembershipService.grant(TENANT_ID, APP_ID, MAKER, AppMembershipService.Role.MEMBER, "system");
        AppMembershipService.grant(TENANT_ID, APP_ID, CHECKER, AppMembershipService.Role.MEMBER, "system");
    }

    @BeforeEach
    public void setUpSchemaAndRoles() throws Exception {
        try (Connection c = JdbcManager.getConnection("default");
                Statement s = c.createStatement()) {
            // Scoped to this test's OWN fixture tenant -- never a blanket wipe of the shared dev
            // Postgres instance's role grants / approval history (see ApprovalRoutesSecurityTest).
            s.execute("DELETE FROM appbana_user_roles WHERE tenant_id = '" + TENANT_ID + "'");
            s.execute("DELETE FROM appbana_approvals WHERE tenant_id = '" + TENANT_ID + "'");
            s.execute("DROP TABLE IF EXISTS \"" + TABLE_NAME + "\"");
        }

        // C4.6 — business fields only; approval columns are materialised by SchemaManager from
        // setApprovalRequired(true) alone.
        schema = new EntitySchema(ENTITY_NAME, List.of(
                new EntitySchema.Field("id", "integer", true, true, null),
                new EntitySchema.Field("amount", "decimal", false, false, null),
                new EntitySchema.Field("password", "string", false, false, null),
                new EntitySchema.Field("notes", "text", false, false, null)));
        schema.setTenantId(TENANT_ID);
        schema.setAppId(APP_ID);
        schema.setApprovalRequired(true);

        SchemaManager.saveSchema(schema);
        schema = SchemaManager.loadSchema(TENANT_ID + "_" + APP_ID + "_" + ENTITY_NAME);
        assertNotNull(schema);
        assertTrue(schema.isApprovalRequired());

        UserRoleService.grantRole(TENANT_ID, APP_ID, ENTITY_NAME, MAKER, UserRoleService.Role.BOTH, "system");
        UserRoleService.grantRole(TENANT_ID, APP_ID, ENTITY_NAME, CHECKER, UserRoleService.Role.CHECKER, "system");
    }

    // ---------------------------------------------------------------- helpers

    private String packedKey() {
        return TENANT_ID + "_" + APP_ID + "_" + ENTITY_NAME;
    }

    private HttpResponse<String> send(String method, String path, String bodyOrNull, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder().uri(URI.create(BASE_URL + path))
                .header("X-Session-Token", token);
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

    /** {@code SELECT *} row maps come back with driver-cased keys — probe all three forms. */
    private static Object val(Map<String, Object> row, String column) {
        if (row == null) return null;
        if (row.containsKey(column)) return row.get(column);
        if (row.containsKey(column.toUpperCase(Locale.ROOT))) return row.get(column.toUpperCase(Locale.ROOT));
        return row.get(column.toLowerCase(Locale.ROOT));
    }

    private Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    /** Inserts a row that is already live and APPROVED, returning its id (raw JDBC — mirrors
     *  RevisionFlowTest.seedApprovedRow — so the seeded password is stored exactly as given,
     *  unhashed, letting the test independently confirm the *later* merge value is genuinely
     *  different). */
    private String seedApprovedRow(double amount, String password, String notes) throws Exception {
        try (Connection c = JdbcManager.getConnection("default");
                PreparedStatement ps = c.prepareStatement("INSERT INTO \"" + TABLE_NAME + "\" " +
                        "(\"AMOUNT\", \"PASSWORD\", \"NOTES\", \"APPROVAL_STATUS\", \"APPROVAL_REVISION\", \"SUBMITTED_BY\", \"APPROVED_BY\") " +
                        "VALUES (?, ?, ?, 'APPROVED', 1, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setDouble(1, amount);
            ps.setString(2, password);
            ps.setString(3, notes);
            ps.setString(4, MAKER);
            ps.setString(5, CHECKER);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                assertTrue(rs.next());
                return String.valueOf(rs.getObject(1));
            }
        }
    }

    // ================================================================
    // 1. Pending queue redaction (original 8th census item)
    // ================================================================

    @Test
    public void testPendingQueueRedactsPasswordColumn() throws Exception {
        Map<String, Object> insertBody = body("amount", 500, "password", "MakerPlainPass1!", "notes", "expense claim");
        HttpResponse<String> post = send("POST", "/api/" + packedKey(), MAPPER.writeValueAsString(insertBody), makerSessionToken);
        assertEquals(201, post.statusCode(), post.body());
        long id = ((Number) readObject(post.body()).get("id")).longValue();

        HttpResponse<String> submit = send("POST",
                "/api/tenants/" + TENANT_ID + "/apps/" + APP_ID + "/entities/" + ENTITY_NAME + "/records/" + id + "/submit",
                "{}", makerSessionToken);
        assertEquals(200, submit.statusCode(), submit.body());

        HttpResponse<String> pending = send("GET",
                "/api/tenants/" + TENANT_ID + "/apps/" + APP_ID + "/entities/" + ENTITY_NAME + "/approvals/pending",
                null, checkerSessionToken);
        assertEquals(200, pending.statusCode(), pending.body());

        Map<String, Object> out = readObject(pending.body());
        assertEquals(1, ((Number) out.get("count")).intValue());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) out.get("records");
        assertEquals(1, records.size());
        Map<String, Object> record = records.get(0);
        assertFalse(anyKeyLeaksSensitiveName(record),
                "pending-queue record must not expose a password/secret-named key: " + record.keySet());
        // Redaction must not over-strip non-sensitive fields the checker needs to review.
        assertEquals("expense claim", val(record, "notes"));
    }

    // ================================================================
    // 2. Approval audit-trail redaction (additional discovery)
    // ================================================================

    @Test
    public void testAuditTrailRedactsPasswordFromMergeDiff() throws Exception {
        String liveId = seedApprovedRow(100.0, "OriginalPlainPass1!", "initial notes");

        // Maker edits the live APPROVED row via real HTTP PUT — this must produce a DRAFT
        // revision (applyApprovalPutGuard's REVISION path), not an in-place overwrite.
        Map<String, Object> editBody = body("amount", 777.0, "password", "RevisedPlainPass2!", "notes", "revised notes");
        HttpResponse<String> put = send("PUT", "/api/" + packedKey() + "/" + liveId, MAPPER.writeValueAsString(editBody), makerSessionToken);
        assertEquals(200, put.statusCode(), put.body());
        Map<String, Object> putResult = readObject(put.body());
        assertEquals(Boolean.TRUE, putResult.get("revision"));
        String revisionId = String.valueOf(putResult.get("revisionId"));

        HttpResponse<String> submit = send("POST",
                "/api/tenants/" + TENANT_ID + "/apps/" + APP_ID + "/entities/" + ENTITY_NAME + "/records/" + revisionId + "/submit",
                "{}", makerSessionToken);
        assertEquals(200, submit.statusCode(), submit.body());

        HttpResponse<String> approve = send("POST",
                "/api/tenants/" + TENANT_ID + "/apps/" + APP_ID + "/entities/" + ENTITY_NAME + "/records/" + revisionId + "/approve",
                "{}", checkerSessionToken);
        assertEquals(200, approve.statusCode(), approve.body());
        Map<String, Object> approveResult = readObject(approve.body());
        assertEquals(liveId, String.valueOf(approveResult.get("rowId")), "the parent id stays canonical after merge");

        HttpResponse<String> audit = send("GET",
                "/api/tenants/" + TENANT_ID + "/apps/" + APP_ID + "/entities/" + ENTITY_NAME + "/records/" + liveId + "/approvals/audit",
                null, checkerSessionToken);
        assertEquals(200, audit.statusCode(), audit.body());

        Map<String, Object> auditOut = readObject(audit.body());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> history = (List<Map<String, Object>>) auditOut.get("history");
        assertEquals(1, history.size(), "exactly one audit entry: the revision merge");

        String diffStr = String.valueOf(history.get(0).get("diff"));
        assertFalse(diffStr.toLowerCase(Locale.ROOT).contains("originalplainpass"),
                "audit diff must not leak the pre-merge (before) password value");
        assertFalse(diffStr.toLowerCase(Locale.ROOT).contains("revisedplainpass"),
                "audit diff must not leak the post-merge (after) password value");

        Map<String, Object> diffMap = readObject(diffStr);
        for (String key : List.of("before", "after")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> snapshot = (Map<String, Object>) diffMap.get(key);
            assertNotNull(snapshot, "diff." + key + " must still be present");
            assertFalse(anyKeyLeaksSensitiveName(snapshot),
                    "diff." + key + " must not carry a password/secret-named key: " + snapshot.keySet());
        }

        // Redaction must not over-strip: the non-sensitive field change must still be visible,
        // proving the checker can still see what actually changed.
        @SuppressWarnings("unchecked")
        Map<String, Object> afterSnapshot = (Map<String, Object>) diffMap.get("after");
        assertEquals("revised notes", val(afterSnapshot, "notes"));
        assertEquals(777.0, ((Number) val(afterSnapshot, "amount")).doubleValue(), 0.0001);
    }

    // ================================================================
    // 3. Internal merge safety regression (no HTTP — direct calls only)
    // ================================================================

    @Test
    public void testInternalRevisionMergeStillCarriesRealPasswordValueForward() throws Exception {
        String liveId = seedApprovedRow(100.0, "OldInternalPass1!", "old notes");

        GenericEntityRoutes.ApprovalPutResult created = GenericEntityRoutes.applyApprovalPutGuard(
                crud, schema, liveId, body("amount", 999.0, "password", "NewInternalPass2!"), MAKER);
        assertEquals(GenericEntityRoutes.ApprovalPutAction.REVISION, created.action());
        String revisionId = String.valueOf(created.body().get("revisionId"));

        ApprovalService.submitForApproval(TENANT_ID, APP_ID, ENTITY_NAME, revisionId, MAKER, "please review");
        Map<String, Object> approveResult =
                ApprovalService.approveRecord(TENANT_ID, APP_ID, ENTITY_NAME, revisionId, CHECKER, "looks good");
        assertEquals(liveId, String.valueOf(approveResult.get("rowId")));

        // Internal getById (no HTTP, no S4.8 redaction layer involved) must show the REAL,
        // amended password value — proving S4.8's redaction lives only at the route boundary and
        // never reaches into the approval revision-merge's own internal reads/writes.
        Map<String, Object> live = crud.getById(schema, liveId);
        assertEquals(999.0, ((Number) val(live, "amount")).doubleValue(), 0.0001);
        String storedPassword = String.valueOf(val(live, "password"));
        assertNotEquals("OldInternalPass1!", storedPassword, "must carry the NEW value forward, not the stale one");
        assertTrue(PasswordService.looksLikeBcryptHash(storedPassword),
                "password field must have been hashed like any other write path");
        assertTrue(PasswordService.verifyPassword("NewInternalPass2!", storedPassword),
                "the stored hash must verify against the plaintext password that was actually submitted");
    }
}
