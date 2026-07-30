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

    // Two-level checker chain fixture — separate entity/table so the level-1-only tests
    // above are unaffected. Schema is created on demand (createL2Schema()) rather than in
    // @BeforeEach since only the L2-specific tests below need it.
    private static final String ENTITY_NAME_L2 = "PurchaseOrderL2";
    private static final String TABLE_NAME_L2 = "APP_T_APPR_APP_APPR_PURCHASEORDERL2";

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
            // Scoped to this test's OWN fixture tenant -- a blanket "DELETE FROM
            // appbana_user_roles" (no WHERE) wipes every real app's role grants in the
            // shared dev Postgres instance on every `mvn test` run. See the sibling fix
            // in RoleRoutesAuthorizationTest/RoleRoutesSecurityTest for the appbana_apps/
            // appbana_schemas version of this same bug.
            s.execute("DELETE FROM appbana_user_roles WHERE tenant_id = '" + TENANT_ID + "'");
            s.execute("DELETE FROM appbana_approvals WHERE tenant_id = '" + TENANT_ID + "'");
            s.execute("DROP TABLE IF EXISTS \"" + TABLE_NAME + "\"");
            s.execute("DROP TABLE IF EXISTS \"" + TABLE_NAME_L2 + "\"");
        }

        // C4.6a — business fields only; setApprovalRequired(true) is what materialises the eight
        // approval columns. This fixture was benign even before the conversion (it writes rows via
        // raw INSERT, so it never went through the getFields()-driven builders that carried the
        // defect), but it was the last one in the repo of the shape that hid C4.6 and C4.6a.
        EntitySchema.Field idField = new EntitySchema.Field("id", "integer", true, true, null);
        EntitySchema.Field amountField = new EntitySchema.Field("amount", "number", false, false, null);

        EntitySchema schema = new EntitySchema(ENTITY_NAME, List.of(idField, amountField));
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

    /**
     * C3.7 exit criterion: the checker queue ranks oldest-submitted first.
     * It shipped as DESC, under which the longest-waiting record sinks to the
     * bottom of the queue and starves — the opposite of what an approval SLA is
     * for.
     */
    @Test
    public void testPendingQueueIsOldestFirst() throws Exception {
        String maker = "fifo_maker";
        String checker = "fifo_checker";
        UserRoleService.grantRole(TENANT_ID, APP_ID, ENTITY_NAME, maker, UserRoleService.Role.MAKER, "system");
        UserRoleService.grantRole(TENANT_ID, APP_ID, ENTITY_NAME, checker, UserRoleService.Role.CHECKER, "system");

        try (Connection c = JdbcManager.getConnection("default");
             Statement s = c.createStatement()) {
            s.execute("INSERT INTO \"" + TABLE_NAME + "\" (\"ID\", \"AMOUNT\", \"APPROVAL_STATUS\", \"APPROVAL_REVISION\") VALUES (102, 10.0, 'DRAFT', 1)");
            s.execute("INSERT INTO \"" + TABLE_NAME + "\" (\"ID\", \"AMOUNT\", \"APPROVAL_STATUS\", \"APPROVAL_REVISION\") VALUES (103, 20.0, 'DRAFT', 1)");
        }

        ApprovalService.submitForApproval(TENANT_ID, APP_ID, ENTITY_NAME, "101", maker, "first");
        ApprovalService.submitForApproval(TENANT_ID, APP_ID, ENTITY_NAME, "102", maker, "second");
        ApprovalService.submitForApproval(TENANT_ID, APP_ID, ENTITY_NAME, "103", maker, "third");

        // submitted_at is set by the service; force distinct, out-of-insertion-order
        // timestamps so the assertion tests the ORDER BY rather than insertion order.
        try (Connection c = JdbcManager.getConnection("default");
             Statement s = c.createStatement()) {
            s.execute("UPDATE \"" + TABLE_NAME + "\" SET \"SUBMITTED_AT\" = TIMESTAMP '2026-01-03 10:00:00' WHERE \"ID\" = 101");
            s.execute("UPDATE \"" + TABLE_NAME + "\" SET \"SUBMITTED_AT\" = TIMESTAMP '2026-01-01 10:00:00' WHERE \"ID\" = 102");
            s.execute("UPDATE \"" + TABLE_NAME + "\" SET \"SUBMITTED_AT\" = TIMESTAMP '2026-01-02 10:00:00' WHERE \"ID\" = 103");
        }

        List<Map<String, Object>> queue = ApprovalService.getPendingQueue(TENANT_ID, APP_ID, ENTITY_NAME, checker);
        assertEquals(3, queue.size());
        assertEquals("102", queue.get(0).get("id").toString(), "The longest-waiting record must be reviewed first");
        assertEquals("103", queue.get(1).get("id").toString());
        assertEquals("101", queue.get(2).get("id").toString());
    }

    /**
     * The badge and the queue must agree, and both must exclude the caller's own
     * submissions.
     *
     * <p>C3.9 — separation of duties means a checker can never approve what they
     * submitted, so counting those rows offered work the backend would then refuse:
     * the badge said "2" and the queue presented nothing actionable. A badge that
     * overstates trains users to ignore it. This pins the two predicates together,
     * because the failure mode is precisely them drifting apart.
     */
    @Test
    public void testQueueAndCountBothExcludeTheCallersOwnSubmissions() throws Exception {
        String other = "sod_other_maker";
        String reviewer = "sod_reviewer";
        UserRoleService.grantRole(TENANT_ID, APP_ID, ENTITY_NAME, other, UserRoleService.Role.MAKER, "system");
        // BOTH so the reviewer can submit as well as review — that is the whole
        // point: their own submission must not appear in their own queue.
        UserRoleService.grantRole(TENANT_ID, APP_ID, ENTITY_NAME, reviewer, UserRoleService.Role.BOTH, "system");

        try (Connection c = JdbcManager.getConnection("default");
             Statement s = c.createStatement()) {
            s.execute("INSERT INTO \"" + TABLE_NAME + "\" (\"ID\", \"AMOUNT\", \"APPROVAL_STATUS\", \"APPROVAL_REVISION\") VALUES (110, 10.0, 'DRAFT', 1)");
        }

        ApprovalService.submitForApproval(TENANT_ID, APP_ID, ENTITY_NAME, "101", other, "someone else's work");
        ApprovalService.submitForApproval(TENANT_ID, APP_ID, ENTITY_NAME, "110", reviewer, "my own work");

        List<Map<String, Object>> queue = ApprovalService.getPendingQueue(TENANT_ID, APP_ID, ENTITY_NAME, reviewer);
        assertEquals(1, queue.size(), "The reviewer's own submission must not appear in their queue");
        assertEquals("101", queue.get(0).get("id").toString());

        int count = ApprovalService.getPendingCount(TENANT_ID, APP_ID, ENTITY_NAME, reviewer);
        assertEquals(queue.size(), count, "The badge count must match what the queue actually offers");
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

    // ------------------------------------------------------------------
    // Two-level checker chain (approvalLevels == 2)
    // ------------------------------------------------------------------

    /** Creates the PurchaseOrderL2 entity (approvalLevels=2) and seeds one DRAFT row (id=201). */
    private void createL2Schema() throws Exception {
        EntitySchema.Field idField = new EntitySchema.Field("id", "integer", true, true, null);
        EntitySchema.Field amountField = new EntitySchema.Field("amount", "number", false, false, null);

        EntitySchema schema = new EntitySchema(ENTITY_NAME_L2, List.of(idField, amountField));
        schema.setTenantId(TENANT_ID);
        schema.setAppId(APP_ID);
        schema.setApprovalRequired(true);
        schema.setApprovalLevels(2);
        SchemaManager.saveSchema(schema);

        try (Connection c = JdbcManager.getConnection("default");
             PreparedStatement ps = c.prepareStatement("INSERT INTO \"" + TABLE_NAME_L2 + "\" (\"ID\", \"AMOUNT\", \"APPROVAL_STATUS\", \"APPROVAL_REVISION\") VALUES (201, 5000.0, 'DRAFT', 1)")) {
            ps.executeUpdate();
        }
    }

    /**
     * The core two-level contract: a level-1 approve on a 2-level entity must land on
     * PENDING_L2, NOT on APPROVED. Getting this wrong (falling straight to APPROVED) would
     * silently downgrade the entity back to single-level behavior.
     */
    @Test
    public void testLevel1ApproveAdvancesToPendingL2NotApproved() throws Exception {
        createL2Schema();
        String maker = "l2_maker";
        String checkerL1 = "l2_checker1";

        UserRoleService.grantRole(TENANT_ID, APP_ID, ENTITY_NAME_L2, maker, UserRoleService.Role.MAKER, "system");
        UserRoleService.grantRole(TENANT_ID, APP_ID, ENTITY_NAME_L2, checkerL1, UserRoleService.Role.CHECKER, "system");

        ApprovalService.submitForApproval(TENANT_ID, APP_ID, ENTITY_NAME_L2, "201", maker, "please review");

        Map<String, Object> res = ApprovalService.approveRecord(TENANT_ID, APP_ID, ENTITY_NAME_L2, "201", checkerL1, "looks fine to me");
        assertEquals("PENDING_L2", res.get("status"), "Level-1 approve on a 2-level entity must advance to PENDING_L2, not APPROVED");

        // A plain CHECKER (not CHECKER_L2) must not be able to approve a PENDING_L2 row.
        UserRoleService.grantRole(TENANT_ID, APP_ID, ENTITY_NAME_L2, "l2_checker_only1", UserRoleService.Role.CHECKER, "system");
        assertThrows(IllegalStateException.class, () ->
                ApprovalService.approveRecord(TENANT_ID, APP_ID, ENTITY_NAME_L2, "201", "l2_checker_only1", "trying to finish it off"));
    }

    /**
     * Level-2 approve is the only hop that reaches the terminal APPROVED state, and it is the
     * only hop that merges a revision back into its parent.
     */
    @Test
    public void testLevel2ApproveReachesApprovedState() throws Exception {
        createL2Schema();
        String maker = "l2_maker2";
        String checkerL1 = "l2_checker1b";
        String checkerL2 = "l2_checker2b";

        UserRoleService.grantRole(TENANT_ID, APP_ID, ENTITY_NAME_L2, maker, UserRoleService.Role.MAKER, "system");
        UserRoleService.grantRole(TENANT_ID, APP_ID, ENTITY_NAME_L2, checkerL1, UserRoleService.Role.CHECKER, "system");
        UserRoleService.grantRole(TENANT_ID, APP_ID, ENTITY_NAME_L2, checkerL2, UserRoleService.Role.CHECKER_L2, "system");

        ApprovalService.submitForApproval(TENANT_ID, APP_ID, ENTITY_NAME_L2, "201", maker, "please review");
        ApprovalService.approveRecord(TENANT_ID, APP_ID, ENTITY_NAME_L2, "201", checkerL1, "level-1 ok");

        Map<String, Object> res = ApprovalService.approveRecord(TENANT_ID, APP_ID, ENTITY_NAME_L2, "201", checkerL2, "level-2 final signoff");
        assertEquals("APPROVED", res.get("status"));

        List<Map<String, Object>> audit = ApprovalService.getAuditTrail(TENANT_ID, APP_ID, ENTITY_NAME_L2, "201", maker);
        assertEquals(3, audit.size(), "Audit trail must record submit, level-1 approve, and level-2 approve");
        assertEquals("PENDING_L2", audit.get(0).get("from_state"));
        assertEquals("APPROVED", audit.get(0).get("to_state"));
        assertEquals("PENDING", audit.get(1).get("from_state"));
        assertEquals("PENDING_L2", audit.get(1).get("to_state"));
    }

    /**
     * Level-1 reject remains terminal (unchanged single-level behavior) even on a 2-level
     * entity: PENDING -&gt; REJECTED, back to the maker.
     */
    @Test
    public void testLevel1RejectIsTerminal() throws Exception {
        createL2Schema();
        String maker = "l2_maker3";
        String checkerL1 = "l2_checker1c";

        UserRoleService.grantRole(TENANT_ID, APP_ID, ENTITY_NAME_L2, maker, UserRoleService.Role.MAKER, "system");
        UserRoleService.grantRole(TENANT_ID, APP_ID, ENTITY_NAME_L2, checkerL1, UserRoleService.Role.CHECKER, "system");

        ApprovalService.submitForApproval(TENANT_ID, APP_ID, ENTITY_NAME_L2, "201", maker, "please review");

        Map<String, Object> res = ApprovalService.rejectRecord(TENANT_ID, APP_ID, ENTITY_NAME_L2, "201", checkerL1, "not enough detail");
        assertEquals("REJECTED", res.get("status"));
    }

    /**
     * Level-2 reject is NOT terminal: it sends the row back to plain PENDING (the level-1
     * queue) rather than all the way to REJECTED, and clears the stashed level-1 approver id.
     */
    @Test
    public void testLevel2RejectSendsBackToPendingNotTerminal() throws Exception {
        createL2Schema();
        String maker = "l2_maker4";
        String checkerL1 = "l2_checker1d";
        String checkerL2 = "l2_checker2d";

        UserRoleService.grantRole(TENANT_ID, APP_ID, ENTITY_NAME_L2, maker, UserRoleService.Role.MAKER, "system");
        UserRoleService.grantRole(TENANT_ID, APP_ID, ENTITY_NAME_L2, checkerL1, UserRoleService.Role.CHECKER, "system");
        UserRoleService.grantRole(TENANT_ID, APP_ID, ENTITY_NAME_L2, checkerL2, UserRoleService.Role.CHECKER_L2, "system");

        ApprovalService.submitForApproval(TENANT_ID, APP_ID, ENTITY_NAME_L2, "201", maker, "please review");
        ApprovalService.approveRecord(TENANT_ID, APP_ID, ENTITY_NAME_L2, "201", checkerL1, "level-1 ok");

        Map<String, Object> res = ApprovalService.rejectRecord(TENANT_ID, APP_ID, ENTITY_NAME_L2, "201", checkerL2, "needs more detail before final signoff");
        assertEquals("PENDING", res.get("status"), "Level-2 reject must send the row back to PENDING, not REJECTED");

        // It must now be actionable again by a level-1 checker (new one, to avoid an
        // unrelated SoD collision with checkerL1 who already acted on this row once).
        UserRoleService.grantRole(TENANT_ID, APP_ID, ENTITY_NAME_L2, "l2_checker1d2", UserRoleService.Role.CHECKER, "system");
        List<Map<String, Object>> l1Queue = ApprovalService.getPendingQueue(TENANT_ID, APP_ID, ENTITY_NAME_L2, "l2_checker1d2", 0, 1);
        assertEquals(1, l1Queue.size(), "Record must reappear in the level-1 queue after a level-2 reject");
        assertEquals("201", l1Queue.get(0).get("id").toString());
    }

    /**
     * Separation of duties extends across the chain: the level-2 approver must not be the
     * same user who already approved the row at level 1.
     */
    @Test
    public void testLevel2CheckerCannotBeSameAsLevel1Approver() throws Exception {
        createL2Schema();
        String maker = "l2_maker5";
        // A single user can only hold ONE role value per entity at a time (grantRole overwrites
        // the prior grant), so this user must approve at level 1 as CHECKER first, and only then
        // be re-granted CHECKER_L2 to attempt (and be refused) the level-2 signoff on their own work.
        String checkerBoth = "l2_checker_both5";

        UserRoleService.grantRole(TENANT_ID, APP_ID, ENTITY_NAME_L2, maker, UserRoleService.Role.MAKER, "system");
        UserRoleService.grantRole(TENANT_ID, APP_ID, ENTITY_NAME_L2, checkerBoth, UserRoleService.Role.CHECKER, "system");

        ApprovalService.submitForApproval(TENANT_ID, APP_ID, ENTITY_NAME_L2, "201", maker, "please review");
        ApprovalService.approveRecord(TENANT_ID, APP_ID, ENTITY_NAME_L2, "201", checkerBoth, "level-1 ok");

        UserRoleService.grantRole(TENANT_ID, APP_ID, ENTITY_NAME_L2, checkerBoth, UserRoleService.Role.CHECKER_L2, "system");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                ApprovalService.approveRecord(TENANT_ID, APP_ID, ENTITY_NAME_L2, "201", checkerBoth, "self level-2 approve"));
        assertTrue(ex.getMessage().contains("Separation of duties violation"));

        IllegalStateException rejectEx = assertThrows(IllegalStateException.class, () ->
                ApprovalService.rejectRecord(TENANT_ID, APP_ID, ENTITY_NAME_L2, "201", checkerBoth, "self level-2 reject"));
        assertTrue(rejectEx.getMessage().contains("Separation of duties violation"));
    }

    /**
     * The level-2 pending queue/count must only surface PENDING_L2 rows (never plain PENDING
     * rows still awaiting a level-1 checker), and must exclude the caller's own level-1 approval.
     */
    @Test
    public void testLevel2QueueOnlyShowsPendingL2AndExcludesOwnLevel1Approval() throws Exception {
        createL2Schema();
        String maker = "l2_maker6";
        String checkerL1 = "l2_checker1f";
        String checkerL2 = "l2_checker2f";

        UserRoleService.grantRole(TENANT_ID, APP_ID, ENTITY_NAME_L2, maker, UserRoleService.Role.MAKER, "system");
        UserRoleService.grantRole(TENANT_ID, APP_ID, ENTITY_NAME_L2, checkerL1, UserRoleService.Role.CHECKER, "system");
        UserRoleService.grantRole(TENANT_ID, APP_ID, ENTITY_NAME_L2, checkerL2, UserRoleService.Role.CHECKER_L2, "system");

        // A second row that never leaves level 1 -- must NOT show up in the level-2 queue.
        try (Connection c = JdbcManager.getConnection("default");
             Statement s = c.createStatement()) {
            s.execute("INSERT INTO \"" + TABLE_NAME_L2 + "\" (\"ID\", \"AMOUNT\", \"APPROVAL_STATUS\", \"APPROVAL_REVISION\") VALUES (202, 10.0, 'DRAFT', 1)");
        }
        ApprovalService.submitForApproval(TENANT_ID, APP_ID, ENTITY_NAME_L2, "202", maker, "still at level 1");

        ApprovalService.submitForApproval(TENANT_ID, APP_ID, ENTITY_NAME_L2, "201", maker, "please review");
        ApprovalService.approveRecord(TENANT_ID, APP_ID, ENTITY_NAME_L2, "201", checkerL1, "level-1 ok");

        // checkerL1 also holds CHECKER_L2 so we can prove their OWN level-1 approval is excluded
        // from their own level-2 queue (separation of duties), even though they generically hold
        // the role that would otherwise entitle them to see it. Granted only AFTER the level-1
        // approve above, since a user can only hold one role value per entity at a time.
        UserRoleService.grantRole(TENANT_ID, APP_ID, ENTITY_NAME_L2, checkerL1, UserRoleService.Role.CHECKER_L2, "system");

        // checkerL2 (a distinct user) sees exactly the one PENDING_L2 row.
        List<Map<String, Object>> l2Queue = ApprovalService.getPendingQueue(TENANT_ID, APP_ID, ENTITY_NAME_L2, checkerL2, 0, 2);
        assertEquals(1, l2Queue.size(), "Level-2 queue must only contain PENDING_L2 rows");
        assertEquals("201", l2Queue.get(0).get("id").toString());

        int l2Count = ApprovalService.getPendingCount(TENANT_ID, APP_ID, ENTITY_NAME_L2, checkerL2, 2);
        assertEquals(1, l2Count);

        // checkerL1, despite ALSO holding CHECKER_L2, must not see row 201 in their own
        // level-2 queue because they were the level-1 approver on it.
        List<Map<String, Object>> ownApprovalExcluded = ApprovalService.getPendingQueue(TENANT_ID, APP_ID, ENTITY_NAME_L2, checkerL1, 0, 2);
        assertEquals(0, ownApprovalExcluded.size(), "A level-2 checker must not see their own level-1 approval in their level-2 queue");
    }
}
