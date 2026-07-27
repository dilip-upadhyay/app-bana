package com.appbana.server.routes;

import com.appbana.ApiServer;
import com.appbana.JdbcManager;
import com.appbana.SchemaManager;
import com.appbana.approval.UserRoleService;
import com.appbana.config.ConfigManager;
import com.appbana.model.EntitySchema;
import com.appbana.service.SessionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ApprovalRoutesSecurityTest — Phase C2.10 / H4 / C2.13 / C2.14 / C2.15-C2.21
 *
 * Full HTTP Integration Test Suite for Maker-Checker Approval Routes & Security Guards.
 * Tests real HTTP calls over port 18089 for:
 *   - Session authentication (unauthenticated → 401)
 *   - Separation of duties: submitter cannot approve own record (SoD violation → 403)
 *   - Owner SoD: owner_alice submits → owner_alice tries to approve → 403 (H9)
 *   - Generic CRUD POST bypass: approval_status=APPROVED is overwritten to DRAFT (B5 check)
 *   - Batch POST bypass (B5): every element forced to DRAFT with server-side submitted_by
 *   - Generic PUT while PENDING → 400 (state machine guard)
 *   - DELETE while PENDING → 400 (B6 check)
 *   - Bulk-delete PENDING gate (B11): PENDING rows skipped, returned in blocked[] list
 *   - Studio POST bypass (B10): forged status forced to DRAFT, actor = studioUserId not "studio"
 *   - Runtime app-scoped POST bypass (B8): unauthenticated → 401; forged status → DRAFT
 *   - Runtime env-scoped POST bypass (B9): unauthenticated → 401; forged status → DRAFT
 *   - Env-scoped PUT while PENDING → 400 (B7 check)
 *   - Env-scoped DELETE while PENDING → 400 (B7 check)
 *   - UUID / hyphen appId table name resolution
 */
public class ApprovalRoutesSecurityTest {

    private static final String BASE_URL = "http://localhost:18089";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TENANT_ID = "t_sec_appr";
    private static final String APP_ID = "app_sec_appr";
    private static final String ENTITY_NAME = "ExpenseReport";
    private static final String TABLE_NAME = "APP_T_SEC_APPR_APP_SEC_APPR_EXPENSEREPORT";

    private static String makerSessionToken;
    private static String checkerSessionToken;
    private static String attackerSessionToken;

    @BeforeAll
    public static void startServerAndPrepareDb() throws Exception {
        ApiServer.startJdk(18089);

        try (Connection c = JdbcManager.getConnection("default");
             Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS appbana_apps (" +
                    "id VARCHAR(100) NOT NULL, tenant_id VARCHAR(50) DEFAULT 'default', " +
                    "name VARCHAR(255), description CLOB, version VARCHAR(50), author VARCHAR(100), " +
                    "created_at BIGINT, updated_at BIGINT, json_metadata CLOB, PRIMARY KEY (id, tenant_id))");

            s.execute("CREATE TABLE IF NOT EXISTS appbana_schemas (" +
                    "name VARCHAR(255) PRIMARY KEY, json CLOB, tenant_id VARCHAR(255), app_id VARCHAR(255))");

            s.execute("CREATE TABLE IF NOT EXISTS appbana_user_roles (" +
                    "tenant_id VARCHAR(255) NOT NULL, app_id VARCHAR(255) NOT NULL, " +
                    "entity_name VARCHAR(255) NOT NULL, user_id VARCHAR(255) NOT NULL, " +
                    "role VARCHAR(20) NOT NULL CHECK (role IN ('maker', 'checker', 'both')), " +
                    "granted_by VARCHAR(255) NOT NULL, granted_at TIMESTAMP NOT NULL DEFAULT NOW(), " +
                    "PRIMARY KEY (tenant_id, app_id, entity_name, user_id))");

            s.execute("CREATE TABLE IF NOT EXISTS appbana_approvals (" +
                    "id UUID PRIMARY KEY, tenant_id VARCHAR(255) NOT NULL, app_id VARCHAR(255) NOT NULL, " +
                    "entity_name VARCHAR(255) NOT NULL, row_id VARCHAR(255) NOT NULL, revision INTEGER NOT NULL, " +
                    "from_state VARCHAR(20), to_state VARCHAR(20) NOT NULL, actor_user_id VARCHAR(255) NOT NULL, " +
                    "actor_role VARCHAR(50) NOT NULL, reason TEXT, diff TEXT, created_at TIMESTAMP NOT NULL DEFAULT NOW())");
        }

        makerSessionToken = SessionService.createSession("alice_maker").sessionId();
        checkerSessionToken = SessionService.createSession("bob_checker").sessionId();
        attackerSessionToken = SessionService.createSession("eve_attacker").sessionId();
    }

    @BeforeEach
    public void setupSchemaAndRoles() throws Exception {
        try (Connection c = JdbcManager.getConnection("default");
             Statement s = c.createStatement()) {
            s.execute("DELETE FROM appbana_user_roles");
            s.execute("DELETE FROM appbana_approvals");
            s.execute("DROP TABLE IF EXISTS \"" + TABLE_NAME + "\"");
        }

        EntitySchema.Field idField = new EntitySchema.Field("id", "integer", true, true, null);
        EntitySchema.Field amountField = new EntitySchema.Field("amount", "integer", false, false, null);
        EntitySchema.Field statusField = new EntitySchema.Field("approval_status", "string", false, false, null);
        EntitySchema.Field revisionField = new EntitySchema.Field("approval_revision", "integer", false, false, null);
        EntitySchema.Field submittedByField = new EntitySchema.Field("submitted_by", "string", false, false, null);
        EntitySchema.Field submittedAtField = new EntitySchema.Field("submitted_at", "timestamp", false, false, null);
        EntitySchema.Field approvedByField = new EntitySchema.Field("approved_by", "string", false, false, null);
        EntitySchema.Field approvedAtField = new EntitySchema.Field("approved_at", "timestamp", false, false, null);
        EntitySchema.Field rejectionReasonField = new EntitySchema.Field("rejection_reason", "text", false, false, null);

        EntitySchema schema = new EntitySchema(ENTITY_NAME, List.of(
                idField, amountField, statusField, revisionField,
                submittedByField, submittedAtField, approvedByField, approvedAtField, rejectionReasonField
        ));
        schema.setTenantId(TENANT_ID);
        schema.setAppId(APP_ID);
        schema.setApprovalRequired(true);

        SchemaManager.saveSchema(schema);

        UserRoleService.grantRole(TENANT_ID, APP_ID, ENTITY_NAME, "alice_maker", UserRoleService.Role.BOTH, "system");
        UserRoleService.grantRole(TENANT_ID, APP_ID, ENTITY_NAME, "bob_checker", UserRoleService.Role.CHECKER, "system");

        // Seed 1 test row in DRAFT state
        try (Connection c = JdbcManager.getConnection("default");
             PreparedStatement ps = c.prepareStatement("INSERT INTO \"" + TABLE_NAME + "\" (\"ID\", \"AMOUNT\", \"APPROVAL_STATUS\", \"APPROVAL_REVISION\") VALUES (201, 1200.0, 'DRAFT', 1)")) {
            ps.executeUpdate();
        }
    }

    @Test
    public void testUnauthenticatedAccessReturns401() throws Exception {
        String url = BASE_URL + "/api/tenants/" + TENANT_ID + "/apps/" + APP_ID + "/entities/" + ENTITY_NAME + "/records/201/submit";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, response.statusCode());
    }

    @Test
    public void testSubmitApproveAndSeparationOfDutiesOverHttp() throws Exception {
        String submitUrl = BASE_URL + "/api/tenants/" + TENANT_ID + "/apps/" + APP_ID + "/entities/" + ENTITY_NAME + "/records/201/submit";
        String approveUrl = BASE_URL + "/api/tenants/" + TENANT_ID + "/apps/" + APP_ID + "/entities/" + ENTITY_NAME + "/records/201/approve";

        // 1. Submit record as alice_maker over HTTP
        HttpRequest submitReq = HttpRequest.newBuilder()
                .uri(URI.create(submitUrl))
                .header("X-Session-Token", makerSessionToken)
                .POST(HttpRequest.BodyPublishers.ofString("{\"comments\":\"Please approve report\"}"))
                .build();

        HttpResponse<String> submitRes = HTTP_CLIENT.send(submitReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, submitRes.statusCode(), "Submit should return 200 OK: " + submitRes.body());
        assertTrue(submitRes.body().contains("\"PENDING\""));

        // 2. Attempt self-approval as alice_maker -> must be blocked with 403 Forbidden!
        HttpRequest selfApproveReq = HttpRequest.newBuilder()
                .uri(URI.create(approveUrl))
                .header("X-Session-Token", makerSessionToken)
                .POST(HttpRequest.BodyPublishers.ofString("{\"comments\":\"Self approve\"}"))
                .build();

        HttpResponse<String> selfApproveRes = HTTP_CLIENT.send(selfApproveReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(403, selfApproveRes.statusCode(), "Self-approval must return 403 Forbidden");
        assertTrue(selfApproveRes.body().contains("Separation of duties violation"));

        // 3. Approve as bob_checker over HTTP -> succeeds with 200 OK
        HttpRequest checkerApproveReq = HttpRequest.newBuilder()
                .uri(URI.create(approveUrl))
                .header("X-Session-Token", checkerSessionToken)
                .POST(HttpRequest.BodyPublishers.ofString("{\"comments\":\"Approved by Bob\"}"))
                .build();

        HttpResponse<String> checkerApproveRes = HTTP_CLIENT.send(checkerApproveReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, checkerApproveRes.statusCode(), "Checker approve should return 200 OK");
        assertTrue(checkerApproveRes.body().contains("\"APPROVED\""));
    }

    /**
     * A workflow conflict (acting on a record that is not in the required state) must surface
     * as 409 Conflict, not 403 Forbidden — 403 would tell clients they lack permission when in
     * fact they simply lost a race or acted out of order.
     */
    @Test
    public void testStateConflictReturns409NotForbidden() throws Exception {
        try (Connection c = JdbcManager.getConnection("default");
             PreparedStatement ps = c.prepareStatement("INSERT INTO \"" + TABLE_NAME + "\" (\"ID\", \"AMOUNT\", \"APPROVAL_STATUS\", \"APPROVAL_REVISION\") VALUES (409, 55.0, 'DRAFT', 1)")) {
            ps.executeUpdate();
        }

        String base = BASE_URL + "/api/tenants/" + TENANT_ID + "/apps/" + APP_ID + "/entities/" + ENTITY_NAME + "/records/409";

        // Approving a DRAFT record: the checker is fully authorized, the record is just not PENDING.
        HttpResponse<String> approveDraft = HTTP_CLIENT.send(HttpRequest.newBuilder()
                .uri(URI.create(base + "/approve"))
                .header("X-Session-Token", checkerSessionToken)
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(409, approveDraft.statusCode(), "Approving a non-PENDING record must be 409 Conflict: " + approveDraft.body());
        assertTrue(approveDraft.body().contains("must be PENDING"));

        // Rejecting a DRAFT record is the same class of conflict.
        HttpResponse<String> rejectDraft = HTTP_CLIENT.send(HttpRequest.newBuilder()
                .uri(URI.create(base + "/reject"))
                .header("X-Session-Token", checkerSessionToken)
                .POST(HttpRequest.BodyPublishers.ofString("{\"reason\":\"nope\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(409, rejectDraft.statusCode(), "Rejecting a non-PENDING record must be 409 Conflict: " + rejectDraft.body());

        // Submitting twice: the second submit finds the record already PENDING.
        HttpRequest submit = HttpRequest.newBuilder()
                .uri(URI.create(base + "/submit"))
                .header("X-Session-Token", makerSessionToken)
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        assertEquals(200, HTTP_CLIENT.send(submit, HttpResponse.BodyHandlers.ofString()).statusCode());

        HttpResponse<String> doubleSubmit = HTTP_CLIENT.send(submit, HttpResponse.BodyHandlers.ofString());
        assertEquals(409, doubleSubmit.statusCode(), "Re-submitting a PENDING record must be 409 Conflict: " + doubleSubmit.body());
        assertTrue(doubleSubmit.body().contains("already in PENDING"));
    }

    @Test
    public void testGenericPostBypassPrevented() throws Exception {
        String postUrl = BASE_URL + "/api/" + TENANT_ID + "_" + APP_ID + "_" + ENTITY_NAME;

        // Attacker attempts to POST with approval_status = APPROVED and forged submitted_by
        String payload = MAPPER.writeValueAsString(Map.of(
                "amount", 9999,
                "approval_status", "APPROVED",
                "submitted_by", "hacker_user"
        ));

        HttpRequest postReq = HttpRequest.newBuilder()
                .uri(URI.create(postUrl))
                .header("X-Session-Token", makerSessionToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> postRes = HTTP_CLIENT.send(postReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, postRes.statusCode(), "Record creation should return 201 Created: " + postRes.body());

        Map<String, Object> respMap = MAPPER.readValue(postRes.body(), new TypeReference<>() {});
        Object insertedId = respMap.get("id");

        // Verify in DB that approval_status was forced to DRAFT and submitted_by to alice_maker
        try (Connection c = JdbcManager.getConnection("default");
             PreparedStatement ps = c.prepareStatement("SELECT \"APPROVAL_STATUS\", \"SUBMITTED_BY\" FROM \"" + TABLE_NAME + "\" WHERE \"ID\" = ?")) {
            ps.setObject(1, Integer.parseInt(insertedId.toString()));
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("DRAFT", rs.getString("APPROVAL_STATUS"), "Client-supplied APPROVED status MUST be overwritten to DRAFT");
                assertEquals("alice_maker", rs.getString("SUBMITTED_BY"), "Client-supplied submitted_by MUST be overwritten to session user");
            }
        }
    }

    /**
     * C2.13 — B5: Batch POST bypass prevention.
     * Attacker supplies approval_status=APPROVED in a batch payload.
     * The batch handler MUST strip and force DRAFT on every element.
     * This test fails if the B5 fix is not present (insertBatch would accept raw payload).
     */
    @Test
    public void testBatchPostBypassPrevented() throws Exception {
        String batchUrl = BASE_URL + "/api/" + TENANT_ID + "_" + APP_ID + "_" + ENTITY_NAME + "/batch";

        // Batch of 2 records, both with forged approval_status=APPROVED — note: no explicit id
        // (table uses SERIAL PK so auto-assign). Use submitted_by probe to verify both were stored.
        String batchPayload = MAPPER.writeValueAsString(List.of(
                Map.of("amount", 500, "approval_status", "APPROVED", "submitted_by", "hacker_user"),
                Map.of("amount", 600, "approval_status", "APPROVED", "approved_by", "hacker_checker")
        ));

        HttpRequest batchReq = HttpRequest.newBuilder()
                .uri(URI.create(batchUrl))
                .header("X-Session-Token", makerSessionToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(batchPayload))
                .build();

        HttpResponse<String> batchRes = HTTP_CLIENT.send(batchReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, batchRes.statusCode(), "Batch insert should return 201: " + batchRes.body());

        // Verify BOTH records are forced to DRAFT, not APPROVED (query by server-set submitted_by)
        try (Connection c = JdbcManager.getConnection("default");
             PreparedStatement ps = c.prepareStatement(
                     "SELECT \"APPROVAL_STATUS\", \"SUBMITTED_BY\", \"APPROVED_BY\" FROM \"" + TABLE_NAME +
                     "\" WHERE \"AMOUNT\" IN (500, 600) ORDER BY \"AMOUNT\"")) {
            try (var rs = ps.executeQuery()) {
                int rowCount = 0;
                while (rs.next()) {
                    rowCount++;
                    String status = rs.getString("APPROVAL_STATUS");
                    String submittedBy = rs.getString("SUBMITTED_BY");
                    String approvedBy = rs.getString("APPROVED_BY");
                    assertEquals("DRAFT", status,
                            "Batch element must be forced to DRAFT, not APPROVED (amount=" + (rowCount == 1 ? 500 : 600) + ")");
                    assertEquals("alice_maker", submittedBy,
                            "Batch element submitted_by must be session user alice_maker, not hacker_user");
                    assertNull(approvedBy,
                            "Batch element approved_by must be null — forged value must be stripped");
                }
                assertEquals(2, rowCount, "Both batch records must have been inserted");
            }
        }
    }

    @Test
    public void testGenericPutBypassPreventedWhilePending() throws Exception {
        // First, set record 201 to PENDING state
        String submitUrl = BASE_URL + "/api/tenants/" + TENANT_ID + "/apps/" + APP_ID + "/entities/" + ENTITY_NAME + "/records/201/submit";
        HttpRequest submitReq = HttpRequest.newBuilder()
                .uri(URI.create(submitUrl))
                .header("X-Session-Token", makerSessionToken)
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        assertEquals(200, HTTP_CLIENT.send(submitReq, HttpResponse.BodyHandlers.ofString()).statusCode());

        // Attempt generic PUT while record is PENDING -> must return 400 Bad Request!
        String putUrl = BASE_URL + "/api/" + TENANT_ID + "_" + APP_ID + "_" + ENTITY_NAME + "/201";
        String putPayload = MAPPER.writeValueAsString(Map.of(
                "amount", 8888.0,
                "approval_status", "APPROVED"
        ));

        HttpRequest putReq = HttpRequest.newBuilder()
                .uri(URI.create(putUrl))
                .header("X-Session-Token", makerSessionToken)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(putPayload))
                .build();

        HttpResponse<String> putRes = HTTP_CLIENT.send(putReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, putRes.statusCode(), "Generic PUT while PENDING must be rejected with 400");
        assertTrue(putRes.body().contains("Cannot update record while approval is PENDING"));
    }

    /**
     * C2.13 — B6: DELETE while PENDING bypass prevention.
     * Deleting a PENDING record would orphan the audit trail and silently bypass the checker queue.
     * This test fails if the B6 fix is not present (deleteById would succeed without a state check).
     */
    @Test
    public void testDeleteBlockedWhilePending() throws Exception {
        // Submit record 201 to PENDING state
        String submitUrl = BASE_URL + "/api/tenants/" + TENANT_ID + "/apps/" + APP_ID + "/entities/" + ENTITY_NAME + "/records/201/submit";
        HttpRequest submitReq = HttpRequest.newBuilder()
                .uri(URI.create(submitUrl))
                .header("X-Session-Token", makerSessionToken)
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        HttpResponse<String> submitRes = HTTP_CLIENT.send(submitReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, submitRes.statusCode(), "Submit must succeed before DELETE test: " + submitRes.body());

        // Attempt DELETE while record is PENDING -> must return 400 Bad Request!
        String deleteUrl = BASE_URL + "/api/" + TENANT_ID + "_" + APP_ID + "_" + ENTITY_NAME + "/201";
        HttpRequest deleteReq = HttpRequest.newBuilder()
                .uri(URI.create(deleteUrl))
                .header("X-Session-Token", checkerSessionToken)
                .DELETE()
                .build();

        HttpResponse<String> deleteRes = HTTP_CLIENT.send(deleteReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, deleteRes.statusCode(),
                "DELETE on PENDING record must return 400 to prevent orphaned audit trail. Got: " + deleteRes.body());
        assertTrue(deleteRes.body().contains("Cannot delete record while approval is PENDING"),
                "Response must explain deletion was blocked due to PENDING state");

        // Verify the record was NOT deleted from DB
        try (Connection c = JdbcManager.getConnection("default");
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM \"" + TABLE_NAME + "\" WHERE \"ID\" = 201")) {
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Record 201 must still exist in the DB after blocked DELETE");
            }
        }
    }

    /**
     * C2.13 — B7: Env-scoped PUT while PENDING bypass prevention.
     * The /api/{tenantId}/apps/{appId}/env/{env}/{entity}/{id} PUT route must enforce the same
     * PENDING gate as the generic /api/{entity}/{id} PUT. Without the B7 fix, this route
     * would accept the PUT and persist APPROVED status directly — bypassing the checker queue.
     */
    @Test
    public void testEnvScopedPutBypassPreventedWhilePending() throws Exception {
        // Submit record 201 to PENDING state
        String submitUrl = BASE_URL + "/api/tenants/" + TENANT_ID + "/apps/" + APP_ID + "/entities/" + ENTITY_NAME + "/records/201/submit";
        HttpRequest submitReq = HttpRequest.newBuilder()
                .uri(URI.create(submitUrl))
                .header("X-Session-Token", makerSessionToken)
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        assertEquals(200, HTTP_CLIENT.send(submitReq, HttpResponse.BodyHandlers.ofString()).statusCode());

        // Attempt env-scoped PUT while record is PENDING.
        // Use env=DEV which maps to the same (non-prefixed) table as the seeded record.
        // This exercises both the session auth gate and the PENDING state guard.
        String envPutUrl = BASE_URL + "/api/" + TENANT_ID + "/apps/" + APP_ID + "/env/DEV/" + ENTITY_NAME + "/201";
        String putPayload = MAPPER.writeValueAsString(Map.of(
                "amount", 7777.0,
                "approval_status", "APPROVED"
        ));

        // Unauthenticated env PUT -> 401 (session auth gate fires before schema/DB lookup)
        HttpRequest unauthReq = HttpRequest.newBuilder()
                .uri(URI.create(envPutUrl))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(putPayload))
                .build();
        HttpResponse<String> unauthRes = HTTP_CLIENT.send(unauthReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, unauthRes.statusCode(),
                "Unauthenticated env-scoped PUT must return 401. Got: " + unauthRes.body());

        // Authenticated env PUT while PENDING -> 400
        HttpRequest authReq = HttpRequest.newBuilder()
                .uri(URI.create(envPutUrl))
                .header("X-Session-Token", makerSessionToken)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(putPayload))
                .build();
        HttpResponse<String> authRes = HTTP_CLIENT.send(authReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, authRes.statusCode(),
                "Env-scoped PUT while PENDING must be blocked with 400. Got: " + authRes.body());
        assertTrue(authRes.body().contains("Cannot update record while approval is PENDING"));
    }

    /**
     * C2.13 — B7: Env-scoped DELETE while PENDING bypass prevention.
     * Same DELETE orphan-trail attack vector as B6, but via the env-scoped route.
     * This test fails if the B7 DELETE fix is absent.
     */
    @Test
    public void testEnvScopedDeleteBlockedWhilePending() throws Exception {
        // Submit record 201 to PENDING state
        String submitUrl = BASE_URL + "/api/tenants/" + TENANT_ID + "/apps/" + APP_ID + "/entities/" + ENTITY_NAME + "/records/201/submit";
        HttpRequest submitReq = HttpRequest.newBuilder()
                .uri(URI.create(submitUrl))
                .header("X-Session-Token", makerSessionToken)
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        assertEquals(200, HTTP_CLIENT.send(submitReq, HttpResponse.BodyHandlers.ofString()).statusCode());

        // Attempt env-scoped DELETE while record is PENDING.
        // Use env=DEV which maps to the same (non-prefixed) table as the seeded record.
        String envDeleteUrl = BASE_URL + "/api/" + TENANT_ID + "/apps/" + APP_ID + "/env/DEV/" + ENTITY_NAME + "/201";

        // Unauthenticated env DELETE -> 401 (session auth gate fires before schema/DB lookup)
        HttpRequest unauthReq = HttpRequest.newBuilder()
                .uri(URI.create(envDeleteUrl))
                .DELETE()
                .build();
        HttpResponse<String> unauthRes = HTTP_CLIENT.send(unauthReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, unauthRes.statusCode(),
                "Unauthenticated env-scoped DELETE must return 401. Got: " + unauthRes.body());

        // Authenticated env DELETE while PENDING -> 400
        HttpRequest authReq = HttpRequest.newBuilder()
                .uri(URI.create(envDeleteUrl))
                .header("X-Session-Token", checkerSessionToken)
                .DELETE()
                .build();
        HttpResponse<String> authRes = HTTP_CLIENT.send(authReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, authRes.statusCode(),
                "Env-scoped DELETE on PENDING record must return 400. Got: " + authRes.body());
        assertTrue(authRes.body().contains("Cannot delete record while approval is PENDING"),
                "Response body must indicate PENDING deletion was blocked");

        // Verify record 201 still exists in DB
        try (Connection c = JdbcManager.getConnection("default");
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM \"" + TABLE_NAME + "\" WHERE \"ID\" = 201")) {
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Record 201 must still exist after blocked env DELETE");
            }
        }
    }

    @Test
    public void testUUIDAppIdTableNameResolutionOverHttp() throws Exception {
        String uuidAppId = "7495460a-bc30-40e9-8235-9ddb08720b2a";
        String hyphenTenantId = "t-81919f7d";
        String uuidEntity = "InvoiceOrder";

        EntitySchema.Field idField = new EntitySchema.Field("id", "integer", true, true, null);
        EntitySchema.Field statusField = new EntitySchema.Field("approval_status", "string", false, false, null);
        EntitySchema.Field revisionField = new EntitySchema.Field("approval_revision", "integer", false, false, null);
        EntitySchema.Field submittedByField = new EntitySchema.Field("submitted_by", "string", false, false, null);
        EntitySchema.Field submittedAtField = new EntitySchema.Field("submitted_at", "timestamp", false, false, null);
        EntitySchema.Field approvedByField = new EntitySchema.Field("approved_by", "string", false, false, null);
        EntitySchema.Field approvedAtField = new EntitySchema.Field("approved_at", "timestamp", false, false, null);
        EntitySchema.Field rejectionReasonField = new EntitySchema.Field("rejection_reason", "text", false, false, null);

        EntitySchema schema = new EntitySchema(uuidEntity, List.of(
                idField, statusField, revisionField, submittedByField,
                submittedAtField, approvedByField, approvedAtField, rejectionReasonField
        ));
        schema.setTenantId(hyphenTenantId);
        schema.setAppId(uuidAppId);
        schema.setApprovalRequired(true);

        String physicalTable = SchemaManager.getPhysicalTableName(schema);

        try (Connection c = JdbcManager.getConnection("default");
             Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS \"" + physicalTable + "\"");
        }

        SchemaManager.saveSchema(schema);

        UserRoleService.grantRole(hyphenTenantId, uuidAppId, uuidEntity, "alice_maker", UserRoleService.Role.MAKER, "system");

        // Seed record 501
        try (Connection c = JdbcManager.getConnection("default");
             PreparedStatement ps = c.prepareStatement("INSERT INTO \"" + physicalTable + "\" (\"ID\", \"APPROVAL_STATUS\", \"APPROVAL_REVISION\") VALUES (501, 'DRAFT', 1)")) {
            ps.executeUpdate();
        }

        String submitUrl = BASE_URL + "/api/tenants/" + hyphenTenantId + "/apps/" + uuidAppId + "/entities/" + uuidEntity + "/records/501/submit";
        HttpRequest submitReq = HttpRequest.newBuilder()
                .uri(URI.create(submitUrl))
                .header("X-Session-Token", makerSessionToken)
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        HttpResponse<String> submitRes = HTTP_CLIENT.send(submitReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, submitRes.statusCode(), "UUID appId submit failed: " + submitRes.body());
        assertTrue(submitRes.body().contains("\"PENDING\""));
    }

    /**
     * H9 — Owner SoD invariant.
     * alice_maker holds role=BOTH (maker+checker) on the entity.
     * She submits record 201 herself.
     * When she then tries to approve it herself, the SoD check must fire → 403.
     * This test fails if submittedBy.equalsIgnoreCase(checkerUserId) is ever removed or bypassed.
     */
    @Test
    public void testOwnerCannotSelfApprove() throws Exception {
        String submitUrl = BASE_URL + "/api/tenants/" + TENANT_ID + "/apps/" + APP_ID + "/entities/" + ENTITY_NAME + "/records/201/submit";
        String approveUrl = BASE_URL + "/api/tenants/" + TENANT_ID + "/apps/" + APP_ID + "/entities/" + ENTITY_NAME + "/records/201/approve";

        // alice_maker has BOTH role — she is a valid submitter
        HttpRequest submitReq = HttpRequest.newBuilder()
                .uri(URI.create(submitUrl))
                .header("X-Session-Token", makerSessionToken)
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        HttpResponse<String> submitRes = HTTP_CLIENT.send(submitReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, submitRes.statusCode(), "Owner submit must succeed: " + submitRes.body());
        assertTrue(submitRes.body().contains("\"PENDING\""), "Record must be PENDING after owner submit");

        // Now alice_maker (same user) tries to approve her own submission → must be 403
        HttpRequest selfApproveReq = HttpRequest.newBuilder()
                .uri(URI.create(approveUrl))
                .header("X-Session-Token", makerSessionToken)
                .POST(HttpRequest.BodyPublishers.ofString("{\"comments\":\"Self-approving as owner\"}"))
                .build();
        HttpResponse<String> selfApproveRes = HTTP_CLIENT.send(selfApproveReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(403, selfApproveRes.statusCode(),
                "Owner must NOT be able to approve their own submission. Got: " + selfApproveRes.body());
        assertTrue(selfApproveRes.body().contains("Separation of duties violation"),
                "403 response must cite SoD violation");
    }

    /**
     * C2.18 — B8: Runtime app-scoped POST bypass prevention.
     * POST /api/{tenantId}/apps/{appId}/{entity} was previously unauthenticated AND ungated.
     * This test fails if:
     *   - Unauthenticated request is not rejected with 401 (auth gate missing)
     *   - Authenticated request with approval_status=APPROVED lands as APPROVED in DB (strip missing)
     */
    @Test
    public void testRuntimeAppScopedInsertBypassPrevented() throws Exception {
        String insertUrl = BASE_URL + "/api/" + TENANT_ID + "/apps/" + APP_ID + "/" + ENTITY_NAME;

        String payload = MAPPER.writeValueAsString(Map.of(
                "amount", 750,
                "approval_status", "APPROVED",
                "submitted_by", "hacker_runtime"
        ));

        // Unauthenticated → 401 (B8 auth gate)
        HttpRequest unauthReq = HttpRequest.newBuilder()
                .uri(URI.create(insertUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        HttpResponse<String> unauthRes = HTTP_CLIENT.send(unauthReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, unauthRes.statusCode(),
                "Unauthenticated runtime POST must return 401. Got: " + unauthRes.body());

        // Authenticated with forged approval_status=APPROVED → 201 but DB must show DRAFT
        HttpRequest authReq = HttpRequest.newBuilder()
                .uri(URI.create(insertUrl))
                .header("X-Session-Token", makerSessionToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        HttpResponse<String> authRes = HTTP_CLIENT.send(authReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, authRes.statusCode(), "Authenticated runtime POST must return 201: " + authRes.body());

        // Verify DB: approval_status=DRAFT, submitted_by=alice_maker (not hacker_runtime)
        try (Connection c = JdbcManager.getConnection("default");
             PreparedStatement ps = c.prepareStatement(
                     "SELECT \"APPROVAL_STATUS\", \"SUBMITTED_BY\" FROM \"" + TABLE_NAME + "\" WHERE \"AMOUNT\" = 750")) {
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Inserted record (amount=750) must exist in DB");
                assertEquals("DRAFT", rs.getString("APPROVAL_STATUS"),
                        "Runtime POST: client-supplied APPROVED must be forced to DRAFT");
                assertEquals("alice_maker", rs.getString("SUBMITTED_BY"),
                        "Runtime POST: submitted_by must be session user alice_maker, not hacker_runtime");
            }
        }
    }

    /**
     * C2.18 — B9: Runtime env-scoped POST bypass prevention.
     * POST /api/{tenantId}/apps/{appId}/env/{env}/{entity} was previously unauthenticated AND ungated.
     * Same attack vector as B8 but via the SIT/PROD env route.
     */
    @Test
    public void testEnvScopedInsertBypassPrevented() throws Exception {
        // Use DEV env (maps to the same non-prefixed table as the seeded schema)
        String insertUrl = BASE_URL + "/api/" + TENANT_ID + "/apps/" + APP_ID + "/env/DEV/" + ENTITY_NAME;

        String payload = MAPPER.writeValueAsString(Map.of(
                "amount", 850,
                "approval_status", "APPROVED",
                "submitted_by", "hacker_env"
        ));

        // Unauthenticated → 401 (B9 auth gate)
        HttpRequest unauthReq = HttpRequest.newBuilder()
                .uri(URI.create(insertUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        HttpResponse<String> unauthRes = HTTP_CLIENT.send(unauthReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, unauthRes.statusCode(),
                "Unauthenticated env-scoped POST must return 401. Got: " + unauthRes.body());

        // Authenticated with forged approval_status=APPROVED → 201 but DB must show DRAFT
        HttpRequest authReq = HttpRequest.newBuilder()
                .uri(URI.create(insertUrl))
                .header("X-Session-Token", makerSessionToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        HttpResponse<String> authRes = HTTP_CLIENT.send(authReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, authRes.statusCode(), "Authenticated env POST must return 201: " + authRes.body());

        // Verify DB: approval_status=DRAFT, submitted_by=alice_maker
        try (Connection c = JdbcManager.getConnection("default");
             PreparedStatement ps = c.prepareStatement(
                     "SELECT \"APPROVAL_STATUS\", \"SUBMITTED_BY\" FROM \"" + TABLE_NAME + "\" WHERE \"AMOUNT\" = 850")) {
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Inserted env record (amount=850) must exist in DB");
                assertEquals("DRAFT", rs.getString("APPROVAL_STATUS"),
                        "Env POST: client-supplied APPROVED must be forced to DRAFT");
                assertEquals("alice_maker", rs.getString("SUBMITTED_BY"),
                        "Env POST: submitted_by must be session user alice_maker, not hacker_env");
            }
        }
    }

    /**
     * C2.18 — B10: Studio POST bypass prevention.
     * POST /appbana-studio/{tenantId}/apps/{appId}/{entity} went through SessionMiddleware
     * but had no stripApprovalColumns / DRAFT enforcement. Any authenticated user could create
     * a pre-approved record via the studio path.
     *
     * Also verifies M9: audit actor must be the studioUserId, not hardcoded "studio".
     */
    @Test
    public void testStudioInsertBypassPrevented() throws Exception {
        String studioUrl = BASE_URL + "/appbana-studio/" + TENANT_ID + "/apps/" + APP_ID + "/" + ENTITY_NAME;

        String payload = MAPPER.writeValueAsString(Map.of(
                "amount", 950,
                "approval_status", "APPROVED",
                "submitted_by", "hacker_studio",
                "approved_by", "fake_checker"
        ));

        // Authenticated as alice_maker with forged approval fields → 201 but DB must show DRAFT
        HttpRequest authReq = HttpRequest.newBuilder()
                .uri(URI.create(studioUrl))
                .header("X-Session-Token", makerSessionToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        HttpResponse<String> authRes = HTTP_CLIENT.send(authReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, authRes.statusCode(), "Studio POST must return 201: " + authRes.body());

        // Verify DB: status forced to DRAFT, submitted_by = authenticated user (not hacker_studio),
        // approved_by must be null (stripped)
        try (Connection c = JdbcManager.getConnection("default");
             PreparedStatement ps = c.prepareStatement(
                     "SELECT \"APPROVAL_STATUS\", \"SUBMITTED_BY\", \"APPROVED_BY\" FROM \"" + TABLE_NAME + "\" WHERE \"AMOUNT\" = 950")) {
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Studio-inserted record (amount=950) must exist");
                assertEquals("DRAFT", rs.getString("APPROVAL_STATUS"),
                        "Studio POST: client-supplied APPROVED must be forced to DRAFT");
                assertEquals("alice_maker", rs.getString("SUBMITTED_BY"),
                        "Studio POST: submitted_by must be authenticated studioUserId, not hacker_studio");
                assertNull(rs.getString("APPROVED_BY"),
                        "Studio POST: forged approved_by must be stripped to null");
            }
        }
    }

    /**
     * C2.18 — B11: Bulk-delete PENDING gate.
     * POST /api/{entity}/bulk-delete iterating IDs with no approval check would let
     * an admin orphan PENDING records from the audit trail.
     * This test fails if the B11 per-ID PENDING gate is absent.
     */
    @Test
    public void testBulkDeleteBlocksPendingRows() throws Exception {
        // Submit record 201 to PENDING state
        String submitUrl = BASE_URL + "/api/tenants/" + TENANT_ID + "/apps/" + APP_ID + "/entities/" + ENTITY_NAME + "/records/201/submit";
        HttpRequest submitReq = HttpRequest.newBuilder()
                .uri(URI.create(submitUrl))
                .header("X-Session-Token", makerSessionToken)
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        HttpResponse<String> submitRes = HTTP_CLIENT.send(submitReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, submitRes.statusCode(), "Submit must succeed before bulk-delete test: " + submitRes.body());

        // Bulk-delete request targeting record 201 (which is now PENDING)
        String bulkDeleteUrl = BASE_URL + "/api/" + TENANT_ID + "_" + APP_ID + "_" + ENTITY_NAME + "/bulk-delete";
        String bulkPayload = MAPPER.writeValueAsString(Map.of("ids", List.of(201)));

        HttpRequest bulkReq = HttpRequest.newBuilder()
                .uri(URI.create(bulkDeleteUrl))
                .header("X-Session-Token", makerSessionToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(bulkPayload))
                .build();
        HttpResponse<String> bulkRes = HTTP_CLIENT.send(bulkReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, bulkRes.statusCode(), "Bulk-delete must return 200 (partial success): " + bulkRes.body());

        // Parse response — 201 must appear in blocked[], not in ids[]
        Map<String, Object> result = MAPPER.readValue(bulkRes.body(), new TypeReference<>() {});
        @SuppressWarnings("unchecked")
        List<Object> deletedIds = (List<Object>) result.get("ids");
        @SuppressWarnings("unchecked")
        List<Object> blockedIds = (List<Object>) result.get("blocked");

        assertNotNull(blockedIds, "Response must contain 'blocked' array when PENDING rows are skipped");
        assertFalse(blockedIds.isEmpty(), "PENDING record 201 must appear in blocked[]");
        assertTrue(deletedIds == null || !deletedIds.contains(201),
                "PENDING record 201 must NOT appear in deleted ids[]");

        // Verify the record was NOT physically deleted from DB
        try (Connection c = JdbcManager.getConnection("default");
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM \"" + TABLE_NAME + "\" WHERE \"ID\" = 201")) {
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Record 201 must still exist after bulk-delete blocked it due to PENDING state");
            }
        }
    }
}
