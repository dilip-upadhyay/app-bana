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
 * ApprovalRoutesSecurityTest — Phase C2.10 / H4
 *
 * Full HTTP Integration Test Suite for Maker-Checker Approval Routes & Security Guards:
 * Tests real HTTP calls over port 18088 for session authentication, separation of duties,
 * role authorization, generic CRUD POST/PUT bypass prevention, and UUID table name resolution.
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
        try {
            ApiServer.startJdk(18089);
        } catch (Exception ignored) {}

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
}
