package com.appbana.server.routes;

import com.appbana.approval.ApprovalService;
import com.appbana.approval.UserRoleService;

import com.appbana.JdbcManager;
import com.appbana.SchemaManager;
import com.appbana.model.EntitySchema;
import com.appbana.service.EntityCrudService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RevisionFlowTest — Phase C2.3 + C2.7.
 *
 * <p>Covers the two items that were missing from the original C2 sign-off:
 * <ul>
 *   <li><b>C2.3</b> — editing a live APPROVED row must produce a separate DRAFT revision
 *       (never an in-place overwrite), and approving that revision must atomically fold it
 *       back into the parent row.</li>
 *   <li><b>C2.7</b> — {@code ?_approvalStatus=} filter validation and the checker-only
 *       restriction on listing PENDING rows.</li>
 * </ul>
 */
public class RevisionFlowTest {

    private static final String TENANT_ID = "t_rev";
    private static final String APP_ID = "app_rev";
    private static final String ENTITY_NAME = "Invoice";
    private static final String TABLE_NAME = "APP_T_REV_APP_REV_INVOICE";

    private static final String MAKER = "maker_mia";
    private static final String CHECKER = "checker_carl";

    private final EntityCrudService crud = new EntityCrudService();
    private EntitySchema schema;

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
    public void cleanAndSeed() throws Exception {
        try (Connection c = JdbcManager.getConnection("default");
             Statement s = c.createStatement()) {
            s.execute("DELETE FROM appbana_user_roles");
            s.execute("DELETE FROM appbana_approvals");
            s.execute("DROP TABLE IF EXISTS \"" + TABLE_NAME + "\"");
        }

        schema = new EntitySchema(ENTITY_NAME, List.of(
                new EntitySchema.Field("id", "integer", true, true, null),
                new EntitySchema.Field("vendor", "string", false, false, null),
                new EntitySchema.Field("amount", "decimal", false, false, null),
                new EntitySchema.Field("notes", "text", false, false, null),
                new EntitySchema.Field("approval_status", "status", false, false, null),
                new EntitySchema.Field("approval_revision", "integer", false, false, null),
                new EntitySchema.Field("approval_parent_id", "text", false, false, null),
                new EntitySchema.Field("submitted_by", "string", false, false, null),
                new EntitySchema.Field("submitted_at", "timestamp", false, false, null),
                new EntitySchema.Field("approved_by", "string", false, false, null),
                new EntitySchema.Field("approved_at", "timestamp", false, false, null),
                new EntitySchema.Field("rejection_reason", "text", false, false, null)
        ));
        schema.setTenantId(TENANT_ID);
        schema.setAppId(APP_ID);
        schema.setApprovalRequired(true);

        SchemaManager.saveSchema(schema);
        schema = SchemaManager.loadSchema(TENANT_ID + "_" + APP_ID + "_" + ENTITY_NAME);
        assertNotNull(schema, "schema must be loadable after save");
        assertTrue(schema.isApprovalRequired());

        UserRoleService.grantRole(TENANT_ID, APP_ID, ENTITY_NAME, MAKER, UserRoleService.Role.MAKER, "system");
        UserRoleService.grantRole(TENANT_ID, APP_ID, ENTITY_NAME, CHECKER, UserRoleService.Role.CHECKER, "system");
    }

    // ---------------------------------------------------------------- helpers

    /** Inserts a row that is already live and APPROVED, returning its id. */
    private String seedApprovedRow(String vendor, double amount) throws Exception {
        try (Connection c = JdbcManager.getConnection("default");
             PreparedStatement ps = c.prepareStatement("INSERT INTO \"" + TABLE_NAME + "\" " +
                     "(\"VENDOR\", \"AMOUNT\", \"APPROVAL_STATUS\", \"APPROVAL_REVISION\", \"SUBMITTED_BY\", \"APPROVED_BY\") " +
                     "VALUES (?, ?, 'APPROVED', 1, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, vendor);
            ps.setDouble(2, amount);
            ps.setString(3, MAKER);
            ps.setString(4, CHECKER);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                assertTrue(rs.next());
                return String.valueOf(rs.getObject(1));
            }
        }
    }

    private String seedRow(String vendor, double amount, String status) throws Exception {
        try (Connection c = JdbcManager.getConnection("default");
             PreparedStatement ps = c.prepareStatement("INSERT INTO \"" + TABLE_NAME + "\" " +
                     "(\"VENDOR\", \"AMOUNT\", \"APPROVAL_STATUS\", \"APPROVAL_REVISION\", \"SUBMITTED_BY\") " +
                     "VALUES (?, ?, ?, 1, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, vendor);
            ps.setDouble(2, amount);
            ps.setString(3, status);
            ps.setString(4, MAKER);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                assertTrue(rs.next());
                return String.valueOf(rs.getObject(1));
            }
        }
    }

    private long rowCount() throws Exception {
        try (Connection c = JdbcManager.getConnection("default");
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM \"" + TABLE_NAME + "\"")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    /** {@code SELECT *} row maps come back with driver-cased keys, so probe all three forms. */
    private static Object val(Map<String, Object> row, String column) {
        if (row == null) return null;
        if (row.containsKey(column)) return row.get(column);
        if (row.containsKey(column.toUpperCase())) return row.get(column.toUpperCase());
        return row.get(column.toLowerCase());
    }

    private static String str(Map<String, Object> row, String column) {
        Object v = val(row, column);
        return v == null ? null : String.valueOf(v);
    }

    private static double dbl(Map<String, Object> row, String column) {
        return ((Number) val(row, column)).doubleValue();
    }

    private static int integer(Map<String, Object> row, String column) {
        return ((Number) val(row, column)).intValue();
    }

    // ------------------------------------------------------------- C2.3 tests

    @Test
    public void putOnApprovedRowCreatesDraftRevisionAndLeavesLiveRowUntouched() throws Exception {
        String liveId = seedApprovedRow("Acme", 100.0);

        GenericEntityRoutes.ApprovalPutResult result = GenericEntityRoutes.applyApprovalPutGuard(
                crud, schema, liveId, body("amount", 250.0), MAKER);

        assertEquals(GenericEntityRoutes.ApprovalPutAction.REVISION, result.action());
        assertEquals(liveId, result.body().get("parentId"));
        assertEquals("DRAFT", result.body().get("approvalStatus"));
        assertEquals(2, result.body().get("approvalRevision"));

        // The live row must be byte-for-byte untouched.
        Map<String, Object> live = crud.getById(schema, liveId);
        assertEquals("APPROVED", str(live, "approval_status"));
        assertEquals(100.0, dbl(live, "amount"), 0.0001);

        // A second, separate DRAFT row now carries the edit.
        String revisionId = String.valueOf(result.body().get("revisionId"));
        assertNotEquals(liveId, revisionId);
        Map<String, Object> revision = crud.getById(schema, revisionId);
        assertEquals("DRAFT", str(revision, "approval_status"));
        assertEquals(250.0, dbl(revision, "amount"), 0.0001);
        assertEquals(liveId, str(revision, "approval_parent_id"));
        assertEquals(MAKER, str(revision, "submitted_by"));
        // Fields not mentioned in the PUT body carry over from the live row.
        assertEquals("Acme", str(revision, "vendor"));

        assertEquals(2, rowCount());
    }

    @Test
    public void repeatedPutsReuseTheSameOpenRevision() throws Exception {
        String liveId = seedApprovedRow("Acme", 100.0);

        GenericEntityRoutes.ApprovalPutResult first = GenericEntityRoutes.applyApprovalPutGuard(
                crud, schema, liveId, body("amount", 250.0), MAKER);
        GenericEntityRoutes.ApprovalPutResult second = GenericEntityRoutes.applyApprovalPutGuard(
                crud, schema, liveId, body("amount", 300.0), MAKER);

        assertEquals(GenericEntityRoutes.ApprovalPutAction.REVISION, second.action());
        assertEquals(first.body().get("revisionId"), second.body().get("revisionId"),
                "a second edit must refresh the open revision, not create another row");
        assertEquals(2, rowCount(), "no duplicate revision rows");

        Map<String, Object> revision = crud.getById(schema, String.valueOf(second.body().get("revisionId")));
        assertEquals(300.0, dbl(revision, "amount"), 0.0001);
    }

    @Test
    public void putIsRejectedWithConflictWhileARevisionIsPending() throws Exception {
        String liveId = seedApprovedRow("Acme", 100.0);

        GenericEntityRoutes.ApprovalPutResult created = GenericEntityRoutes.applyApprovalPutGuard(
                crud, schema, liveId, body("amount", 250.0), MAKER);
        String revisionId = String.valueOf(created.body().get("revisionId"));

        ApprovalService.submitForApproval(TENANT_ID, APP_ID, ENTITY_NAME, revisionId, MAKER, "please review");

        GenericEntityRoutes.ApprovalPutResult blocked = GenericEntityRoutes.applyApprovalPutGuard(
                crud, schema, liveId, body("amount", 999.0), MAKER);

        assertEquals(GenericEntityRoutes.ApprovalPutAction.CONFLICT, blocked.action());
        assertEquals(revisionId, blocked.body().get("revisionId"));
    }

    @Test
    public void approvingARevisionMergesItIntoTheParentAndDeletesTheRevisionRow() throws Exception {
        String liveId = seedApprovedRow("Acme", 100.0);

        GenericEntityRoutes.ApprovalPutResult created = GenericEntityRoutes.applyApprovalPutGuard(
                crud, schema, liveId, body("amount", 250.0, "vendor", "Acme Global"), MAKER);
        String revisionId = String.valueOf(created.body().get("revisionId"));

        ApprovalService.submitForApproval(TENANT_ID, APP_ID, ENTITY_NAME, revisionId, MAKER, "please review");
        Map<String, Object> approveResult =
                ApprovalService.approveRecord(TENANT_ID, APP_ID, ENTITY_NAME, revisionId, CHECKER, "looks good");

        assertEquals("APPROVED", approveResult.get("status"));
        assertEquals(liveId, approveResult.get("rowId"), "the parent id stays canonical");
        assertEquals(revisionId, approveResult.get("mergedFromRevisionRowId"));

        // Exactly one row survives: the parent, carrying the revision's values.
        assertEquals(1, rowCount());
        assertNull(crud.getById(schema, revisionId), "revision row must be gone");

        Map<String, Object> live = crud.getById(schema, liveId);
        assertEquals("APPROVED", str(live, "approval_status"));
        assertEquals(250.0, dbl(live, "amount"), 0.0001);
        assertEquals("Acme Global", str(live, "vendor"));
        assertEquals(2, integer(live, "approval_revision"));
        assertEquals(CHECKER, str(live, "approved_by"));
        assertNull(val(live, "approval_parent_id"), "merged parent must not point at itself");

        // The pre-merge snapshot survives in the audit trail.
        List<Map<String, Object>> parentTrail =
                ApprovalService.getAuditTrail(TENANT_ID, APP_ID, ENTITY_NAME, liveId, CHECKER);
        assertEquals(1, parentTrail.size());
        String diff = String.valueOf(parentTrail.get(0).get("diff"));
        assertTrue(diff.contains("\"before\""), "audit diff must retain the previous version");
        assertTrue(diff.contains("Acme"), "audit diff must contain the superseded vendor value");
    }

    @Test
    public void rejectingARevisionLeavesTheLiveRowApproved() throws Exception {
        String liveId = seedApprovedRow("Acme", 100.0);

        GenericEntityRoutes.ApprovalPutResult created = GenericEntityRoutes.applyApprovalPutGuard(
                crud, schema, liveId, body("amount", 250.0), MAKER);
        String revisionId = String.valueOf(created.body().get("revisionId"));

        ApprovalService.submitForApproval(TENANT_ID, APP_ID, ENTITY_NAME, revisionId, MAKER, "please review");
        ApprovalService.rejectRecord(TENANT_ID, APP_ID, ENTITY_NAME, revisionId, CHECKER, "amount too high");

        Map<String, Object> live = crud.getById(schema, liveId);
        assertEquals("APPROVED", str(live, "approval_status"));
        assertEquals(100.0, dbl(live, "amount"), 0.0001);

        Map<String, Object> revision = crud.getById(schema, revisionId);
        assertEquals("REJECTED", str(revision, "approval_status"));
        assertEquals("amount too high", str(revision, "rejection_reason"));
    }

    @Test
    public void putOnPendingRowIsBlocked() throws Exception {
        String id = seedRow("Acme", 100.0, "PENDING");

        GenericEntityRoutes.ApprovalPutResult result = GenericEntityRoutes.applyApprovalPutGuard(
                crud, schema, id, body("amount", 250.0), MAKER);

        assertEquals(GenericEntityRoutes.ApprovalPutAction.BLOCKED_PENDING, result.action());
        assertEquals(1, rowCount(), "a blocked PUT must not create a revision row");
    }

    @Test
    public void putOnDraftRowEditsInPlaceWithoutCreatingARevision() throws Exception {
        String id = seedRow("Acme", 100.0, "DRAFT");

        Map<String, Object> data = body("amount", 250.0);
        GenericEntityRoutes.ApprovalPutResult result =
                GenericEntityRoutes.applyApprovalPutGuard(crud, schema, id, data, MAKER);

        assertEquals(GenericEntityRoutes.ApprovalPutAction.PROCEED, result.action());
        assertEquals("DRAFT", data.get("approval_status"));
        assertEquals(1, rowCount());
    }

    @Test
    public void putOnRejectedRowReturnsItToDraftInPlace() throws Exception {
        String id = seedRow("Acme", 100.0, "REJECTED");

        Map<String, Object> data = body("amount", 250.0);
        GenericEntityRoutes.ApprovalPutResult result =
                GenericEntityRoutes.applyApprovalPutGuard(crud, schema, id, data, MAKER);

        assertEquals(GenericEntityRoutes.ApprovalPutAction.PROCEED, result.action());
        assertEquals("DRAFT", data.get("approval_status"));
        assertNull(data.get("rejection_reason"), "resuming work must clear the previous rejection reason");
        assertEquals(1, rowCount());
    }

    @Test
    public void clientSuppliedApprovalMetadataIsStrippedIncludingParentId() throws Exception {
        String liveId = seedApprovedRow("Acme", 100.0);
        String otherId = seedApprovedRow("Globex", 42.0);

        Map<String, Object> forged = body(
                "amount", 250.0,
                "approval_status", "APPROVED",
                "approval_parent_id", otherId,
                "approved_by", MAKER,
                "approval_revision", 99);

        GenericEntityRoutes.ApprovalPutResult result =
                GenericEntityRoutes.applyApprovalPutGuard(crud, schema, liveId, forged, MAKER);

        assertEquals(GenericEntityRoutes.ApprovalPutAction.REVISION, result.action());
        Map<String, Object> revision = crud.getById(schema, String.valueOf(result.body().get("revisionId")));
        assertEquals("DRAFT", str(revision, "approval_status"),
                "forged approval_status must not survive");
        assertEquals(liveId, str(revision, "approval_parent_id"),
                "forged approval_parent_id must be overwritten with the real parent");
        assertNull(val(revision, "approved_by"), "forged approved_by must not survive");
        assertEquals(2, integer(revision, "approval_revision"),
                "forged approval_revision must not survive");
    }

    // ------------------------------------------------------------- C2.7 tests

    @Test
    public void approvalStatusFilterReturnsNullWhenAbsent() {
        assertNull(GenericEntityRoutes.resolveApprovalStatusFilter(schema, TENANT_ID, APP_ID, null, CHECKER, true));
        assertNull(GenericEntityRoutes.resolveApprovalStatusFilter(schema, TENANT_ID, APP_ID, "  ", CHECKER, true));
    }

    @Test
    public void approvalStatusFilterRejectsUnknownValues() {
        GenericEntityRoutes.ApprovalFilterException ex = assertThrows(
                GenericEntityRoutes.ApprovalFilterException.class,
                () -> GenericEntityRoutes.resolveApprovalStatusFilter(schema, TENANT_ID, APP_ID, "GARBAGE", CHECKER, true));
        assertEquals(400, ex.status());
    }

    @Test
    public void approvalStatusFilterRejectsEntitiesWithoutApprovals() {
        EntitySchema plain = new EntitySchema("Plain", List.of(
                new EntitySchema.Field("id", "integer", true, true, null)));
        plain.setTenantId(TENANT_ID);
        plain.setAppId(APP_ID);
        plain.setApprovalRequired(false);

        GenericEntityRoutes.ApprovalFilterException ex = assertThrows(
                GenericEntityRoutes.ApprovalFilterException.class,
                () -> GenericEntityRoutes.resolveApprovalStatusFilter(plain, TENANT_ID, APP_ID, "APPROVED", CHECKER, true));
        assertEquals(400, ex.status(),
                "silently ignoring the filter would leak the whole table to a caller asking for a subset");
    }

    @Test
    public void listingPendingRequiresCheckerRole() {
        GenericEntityRoutes.ApprovalFilterException ex = assertThrows(
                GenericEntityRoutes.ApprovalFilterException.class,
                () -> GenericEntityRoutes.resolveApprovalStatusFilter(schema, TENANT_ID, APP_ID, "PENDING", MAKER, true));
        assertEquals(403, ex.status());

        GenericEntityRoutes.ApprovalFilterException anon = assertThrows(
                GenericEntityRoutes.ApprovalFilterException.class,
                () -> GenericEntityRoutes.resolveApprovalStatusFilter(schema, TENANT_ID, APP_ID, "PENDING", null, true));
        assertEquals(403, anon.status());
    }

    @Test
    public void checkerMayListPendingAndAnyoneMayListApproved() {
        assertEquals("PENDING",
                GenericEntityRoutes.resolveApprovalStatusFilter(schema, TENANT_ID, APP_ID, "pending", CHECKER, true));
        assertEquals("APPROVED",
                GenericEntityRoutes.resolveApprovalStatusFilter(schema, TENANT_ID, APP_ID, "approved", MAKER, true));
        assertEquals("DRAFT",
                GenericEntityRoutes.resolveApprovalStatusFilter(schema, TENANT_ID, APP_ID, "DRAFT", MAKER, true));
    }

    @Test
    public void genericFilterParamCannotSmuggleAPendingListing() {
        // ?filter=approval_status:PENDING must face the same checker gate as ?_approvalStatus=PENDING.
        Map<String, Object> smuggled = new LinkedHashMap<>();
        smuggled.put("approval_status", "PENDING");

        GenericEntityRoutes.ApprovalFilterException ex = assertThrows(
                GenericEntityRoutes.ApprovalFilterException.class,
                () -> GenericEntityRoutes.applyApprovalStatusFilter(
                        schema, TENANT_ID, APP_ID, null, smuggled, MAKER, true));
        assertEquals(403, ex.status());

        // A checker may of course still use it.
        Map<String, Object> allowed = new LinkedHashMap<>();
        allowed.put("approval_status", "PENDING");
        assertFalse(GenericEntityRoutes.applyApprovalStatusFilter(
                schema, TENANT_ID, APP_ID, null, allowed, CHECKER, true));
    }

    @Test
    public void explicitApprovalStatusParamIsWrittenIntoTheFilterMap() {
        Map<String, Object> filters = new LinkedHashMap<>();
        assertTrue(GenericEntityRoutes.applyApprovalStatusFilter(
                schema, TENANT_ID, APP_ID, "pending", filters, CHECKER, true));
        assertEquals("PENDING", filters.get("approval_status"));
    }

    @Test
    public void approvalStatusFilterNarrowsListResults() throws Exception {
        seedApprovedRow("Acme", 100.0);
        seedRow("Globex", 42.0, "DRAFT");
        seedRow("Initech", 7.0, "PENDING");

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("approval_status",
                GenericEntityRoutes.resolveApprovalStatusFilter(schema, TENANT_ID, APP_ID, "APPROVED", MAKER, true));

        Map<String, Object> out = crud.listAdvanced(schema, 50, 0, null, null, null, filters);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) out.get("rows");

        assertEquals(1, rows.size());
        assertEquals("Acme", rows.get(0).get("vendor"));
        assertEquals(1L, ((Number) out.get("total")).longValue());
    }

    // ------------------------------------------ post-review hardening (round 2)

    /**
     * If the parent is deleted while a revision is pending, the revision stays live. The
     * response must then point at the revision id — pointing at the deleted parent id would
     * send clients to a row that no longer exists and make the approved edit look lost.
     */
    @Test
    public void approvingRevisionWhoseParentVanishedReportsTheRevisionIdAsLive() throws Exception {
        String liveId = seedApprovedRow("Acme", 100.0);

        GenericEntityRoutes.ApprovalPutResult created = GenericEntityRoutes.applyApprovalPutGuard(
                crud, schema, liveId, body("amount", 250.0), MAKER);
        String revisionId = String.valueOf(created.body().get("revisionId"));

        ApprovalService.submitForApproval(TENANT_ID, APP_ID, ENTITY_NAME, revisionId, MAKER, "please review");

        // Parent is deleted out from under the pending revision.
        try (Connection c = JdbcManager.getConnection("default");
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM \"" + TABLE_NAME + "\" WHERE \"ID\" = ?")) {
            ps.setLong(1, Long.parseLong(liveId));
            assertEquals(1, ps.executeUpdate());
        }

        Map<String, Object> result = ApprovalService.approveRecord(
                TENANT_ID, APP_ID, ENTITY_NAME, revisionId, CHECKER, "ok");

        assertEquals(revisionId, String.valueOf(result.get("rowId")),
                "no merge happened, so the caller must be pointed at the surviving revision row");
        assertNull(result.get("mergedFromRevisionRowId"),
                "nothing was merged — the merge fields must not be reported");

        Map<String, Object> survivor = crud.getById(schema, revisionId);
        assertNotNull(survivor, "the revision must survive as the live row");
        assertEquals("APPROVED", str(survivor, "approval_status"));
        assertEquals(250.0, dbl(survivor, "amount"), 0.0001);
        assertNull(str(survivor, "approval_parent_id"), "dangling parent pointer must be cleared");
        assertEquals(1, rowCount());
    }

    /**
     * The merge overwrites the parent and deletes the revision, so {@code diff.before} in the
     * audit table is the only surviving copy of the previous approved version. It must not be
     * lost to the oversized-diff truncation — {@code after} is dropped first because it can
     * always be recovered by reading the live row.
     */
    @Test
    public void oversizedMergeDiffPreservesTheBeforeSnapshot() throws Exception {
        String bulky = "x".repeat(40_000);

        String liveId = seedApprovedRow("Acme", 100.0);
        try (Connection c = JdbcManager.getConnection("default");
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE \"" + TABLE_NAME + "\" SET \"NOTES\" = ? WHERE \"ID\" = ?")) {
            ps.setString(1, bulky);
            ps.setLong(2, Long.parseLong(liveId));
            assertEquals(1, ps.executeUpdate());
        }

        GenericEntityRoutes.ApprovalPutResult created = GenericEntityRoutes.applyApprovalPutGuard(
                crud, schema, liveId, body("notes", "y".repeat(40_000)), MAKER);
        String revisionId = String.valueOf(created.body().get("revisionId"));

        ApprovalService.submitForApproval(TENANT_ID, APP_ID, ENTITY_NAME, revisionId, MAKER, "big");
        ApprovalService.approveRecord(TENANT_ID, APP_ID, ENTITY_NAME, revisionId, CHECKER, "ok");

        String mergeDiff = null;
        try (Connection c = JdbcManager.getConnection("default");
             PreparedStatement ps = c.prepareStatement(
                     "SELECT diff FROM appbana_approvals WHERE row_id = ? ORDER BY created_at DESC")) {
            ps.setString(1, liveId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String d = rs.getString(1);
                    if (d != null && d.contains("mergedFromRevisionRowId")) {
                        mergeDiff = d;
                        break;
                    }
                }
            }
        }

        assertNotNull(mergeDiff, "merge audit entry must exist");
        assertFalse(mergeDiff.startsWith("{\"truncated\":true"),
                "the whole diff must not be blindly prefix-truncated");
        assertTrue(mergeDiff.contains("\"before\""), "the pre-merge snapshot must survive");
        assertTrue(mergeDiff.contains(bulky), "the previous approved value must be recoverable verbatim");
        assertTrue(mergeDiff.contains("\"afterOmitted\":true"),
                "'after' is shed first because it is recoverable from the live row");
    }

    /**
     * Two simultaneous PUTs on the same APPROVED parent must not each insert a revision.
     * Without the parent row lock both observe "no open revision" and the
     * one-open-revision-per-parent invariant (and the 409 that depends on it) breaks.
     */
    @Test
    public void concurrentPutsOnTheSameParentProduceOnlyOneRevision() throws Exception {
        String liveId = seedApprovedRow("Acme", 100.0);

        int threads = 8;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<GenericEntityRoutes.ApprovalPutResult>> futures = new java.util.ArrayList<>();

        try (java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                final double amount = 200.0 + i;
                futures.add(pool.submit(() -> {
                    start.await();
                    return GenericEntityRoutes.applyApprovalPutGuard(
                            crud, schema, liveId, body("amount", amount), MAKER);
                }));
            }
            start.countDown();
            for (java.util.concurrent.Future<GenericEntityRoutes.ApprovalPutResult> f : futures) {
                assertEquals(GenericEntityRoutes.ApprovalPutAction.REVISION, f.get().action());
            }
        }

        assertEquals(2, rowCount(), "the parent plus exactly one revision row");

        java.util.Set<String> revisionIds = new java.util.HashSet<>();
        for (java.util.concurrent.Future<GenericEntityRoutes.ApprovalPutResult> f : futures) {
            revisionIds.add(String.valueOf(f.get().body().get("revisionId")));
        }
        assertEquals(1, revisionIds.size(), "every writer must converge on the same revision row");
    }
}
