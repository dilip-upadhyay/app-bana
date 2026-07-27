package com.appbana.approval;

import com.appbana.JdbcManager;
import com.appbana.SchemaManager;
import com.appbana.model.EntitySchema;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ApprovalServiceTest — Phase C2.4
 *
 * Unit and integration tests for ApprovalService state machine & separation of duties rules.
 */
public class ApprovalServiceTest {

    private static final String TENANT_ID = "t_appr";
    private static final String APP_ID = "app_appr";
    private static final String ENTITY_NAME = "PurchaseOrder";
    private static final String TABLE_NAME = "APP_T_APPR_APP_APPR_PURCHASEORDER";

    @BeforeAll
    public static void setUpDb() throws Exception {
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
    }

    @BeforeEach
    public void cleanAndSeedTable() throws Exception {
        try (Connection c = JdbcManager.getConnection("default");
             Statement s = c.createStatement()) {
            s.execute("DELETE FROM appbana_user_roles");
            s.execute("DELETE FROM appbana_approvals");
            s.execute("DROP TABLE IF EXISTS \"" + TABLE_NAME + "\"");
        }

        // Create entity physical table with all enriched approval columns
        EntitySchema.Field idField = new EntitySchema.Field("id", "integer", true, true, null);
        EntitySchema.Field amountField = new EntitySchema.Field("amount", "number", false, false, null);
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

        // Seed 1 test row in DRAFT state
        try (Connection c = JdbcManager.getConnection("default");
             PreparedStatement ps = c.prepareStatement("INSERT INTO \"" + TABLE_NAME + "\" (\"ID\", \"AMOUNT\", \"APPROVAL_STATUS\", \"APPROVAL_REVISION\") VALUES (101, 5000.0, 'DRAFT', 1)")) {
            ps.executeUpdate();
        }
    }

    @Test
    public void testSubmitForApproval() throws Exception {
        String maker = "maker_alice";

        // Non-maker submit attempt fails
        assertThrows(IllegalStateException.class, () ->
                ApprovalService.submitForApproval(TENANT_ID, APP_ID, ENTITY_NAME, "101", "random_user", "Pls approve"));

        // Grant MAKER role to maker_alice
        UserRoleService.grantRole(TENANT_ID, APP_ID, ENTITY_NAME, maker, UserRoleService.Role.MAKER, "system");

        // Submit for approval succeeds
        Map<String, Object> res = ApprovalService.submitForApproval(TENANT_ID, APP_ID, ENTITY_NAME, "101", maker, "Pls approve PO #101");
        assertEquals("PENDING", res.get("status"));

        // Submitting an already PENDING record throws IllegalStateException
        assertThrows(IllegalStateException.class, () ->
                ApprovalService.submitForApproval(TENANT_ID, APP_ID, ENTITY_NAME, "101", maker, "Re-submit"));

        // Grant CHECKER role to checker_bob and verify Pending Queue returns record
        UserRoleService.grantRole(TENANT_ID, APP_ID, ENTITY_NAME, "checker_bob", UserRoleService.Role.CHECKER, "system");
        List<Map<String, Object>> queue = ApprovalService.getPendingQueue(TENANT_ID, APP_ID, ENTITY_NAME, "checker_bob");
        assertEquals(1, queue.size());
        assertEquals("101", queue.get(0).get("id").toString());
    }

    @Test
    public void testSeparationOfDutiesViolationFailsApproveAndReject() throws Exception {
        String userBoth = "user_both";

        // User with BOTH role can submit
        UserRoleService.grantRole(TENANT_ID, APP_ID, ENTITY_NAME, userBoth, UserRoleService.Role.BOTH, "system");
        ApprovalService.submitForApproval(TENANT_ID, APP_ID, ENTITY_NAME, "101", userBoth, "Self submission");

        // Submitter trying to approve their own record is rejected for Separation of Duties!
        IllegalStateException approveEx = assertThrows(IllegalStateException.class, () ->
                ApprovalService.approveRecord(TENANT_ID, APP_ID, ENTITY_NAME, "101", userBoth, "Self approve"));
        assertTrue(approveEx.getMessage().contains("Separation of duties violation"));

        // Submitter trying to reject their own record is rejected for Separation of Duties!
        IllegalStateException rejectEx = assertThrows(IllegalStateException.class, () ->
                ApprovalService.rejectRecord(TENANT_ID, APP_ID, ENTITY_NAME, "101", userBoth, "Self reject"));
        assertTrue(rejectEx.getMessage().contains("Separation of duties violation"));
    }

    @Test
    public void testApproveAndRejectFlowWithAuditTrail() throws Exception {
        String maker = "maker_alice";
        String checker = "checker_bob";

        UserRoleService.grantRole(TENANT_ID, APP_ID, ENTITY_NAME, maker, UserRoleService.Role.MAKER, "system");
        UserRoleService.grantRole(TENANT_ID, APP_ID, ENTITY_NAME, checker, UserRoleService.Role.CHECKER, "system");

        // Step 1: Submit record
        ApprovalService.submitForApproval(TENANT_ID, APP_ID, ENTITY_NAME, "101", maker, "Submitting PO 101");

        // Step 2: Reject record
        Map<String, Object> rejectRes = ApprovalService.rejectRecord(TENANT_ID, APP_ID, ENTITY_NAME, "101", checker, "Amount too high");
        assertEquals("REJECTED", rejectRes.get("status"));

        // Step 3: Resubmit after fix
        ApprovalService.submitForApproval(TENANT_ID, APP_ID, ENTITY_NAME, "101", maker, "Resubmitting fixed PO 101");

        // Step 4: Approve record
        Map<String, Object> approveRes = ApprovalService.approveRecord(TENANT_ID, APP_ID, ENTITY_NAME, "101", checker, "Looks good now");
        assertEquals("APPROVED", approveRes.get("status"));

        // Verify Audit Trail has 4 state transition log entries.
        // C2.6: the trail is returned most-recent-first, so index 0 is the final transition.
        List<Map<String, Object>> audit = ApprovalService.getAuditTrail(TENANT_ID, APP_ID, ENTITY_NAME, "101", maker);
        assertEquals(4, audit.size(), "Audit trail must record all 4 state transitions");

        assertEquals("PENDING", audit.get(0).get("from_state"));
        assertEquals("APPROVED", audit.get(0).get("to_state"));

        assertEquals("REJECTED", audit.get(1).get("from_state"));
        assertEquals("PENDING", audit.get(1).get("to_state"));

        assertEquals("PENDING", audit.get(2).get("from_state"));
        assertEquals("REJECTED", audit.get(2).get("to_state"));

        assertEquals("DRAFT", audit.get(3).get("from_state"));
        assertEquals("PENDING", audit.get(3).get("to_state"));
    }

    @Test
    public void testTableNameResolutionWithUUIDAndHyphens() {
        String uuidAppId = "7495460a-bc30-40e9-8235-9ddb08720b2a";
        String hyphenTenantId = "t-81919f7d";
        String entity = "PurchaseOrder";

        String derivedTable = ApprovalService.getTableName(hyphenTenantId, uuidAppId, entity);

        // Must convert hyphens to underscores, UPPERCASE, and truncate to 63 chars via SchemaManager
        assertEquals("APP_T_81919F7D_7495460A_BC30_40E9_8235_9DDB08720B2A_PURCHASEORD", derivedTable);
    }
}
