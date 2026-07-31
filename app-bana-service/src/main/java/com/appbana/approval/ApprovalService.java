package com.appbana.approval;

import com.appbana.JdbcManager;
import com.appbana.SchemaManager;
import com.appbana.model.EntitySchema;
import com.appbana.security.AppAuthorization;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * ApprovalService — Phase C2.1 & C2.5-C2.9
 *
 * Core state machine service for Maker-Checker approval workflows:
 * State transitions: DRAFT -> PENDING -> APPROVED / REJECTED
 *
 * Enforces:
 * 1. Physical Table Name Unification (delegates to SchemaManager).
 * 2. Role Authorization (Submitter must be MAKER; Approver/Rejecter must be CHECKER).
 * 3. Separation of Duties (Approver/Rejecter CANNOT be the submitter of the record).
 * 4. Role-scoped Pending Queue and Audit Trail queries.
 * 5. Pessimistic SELECT FOR UPDATE locking & revision bump on resubmit.
 * 6. Diff snapshot creation for audit trail logging into `appbana_approvals`.
 */
public class ApprovalService {
    private static final Logger LOG = LoggerFactory.getLogger(ApprovalService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * C2.3 — columns that are never copied verbatim from a revision row onto its parent
     * during {@link #mergeRevisionIntoParent}. The PK and CREATED_AT belong to the parent;
     * the approval bookkeeping columns are set explicitly by the merge statement.
     */
    private static final Set<String> NON_MERGED_COLUMNS = Set.of(
            "ID",
            "CREATED_AT",
            "APPROVAL_STATUS",
            "APPROVAL_REVISION",
            "APPROVAL_PARENT_ID",
            "SUBMITTED_BY",
            "SUBMITTED_AT",
            "APPROVED_BY",
            "APPROVED_AT",
            "REJECTION_REASON"
    );

    public enum Status {
        DRAFT("DRAFT"),
        PENDING("PENDING"),
        /** Two-level checker chain only: level-1 approved, awaiting level-2 signoff. */
        PENDING_L2("PENDING_L2"),
        APPROVED("APPROVED"),
        REJECTED("REJECTED");

        private final String value;

        Status(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static Status fromValue(String val) {
            if (val == null) return DRAFT;
            for (Status s : values()) {
                if (s.value.equalsIgnoreCase(val)) return s;
            }
            return DRAFT;
        }
    }

    /**
     * Derives per-tenant dynamic physical table name using canonical SchemaManager.
     * Prevents table name mismatches caused by UUID appIds, hyphens, env prefixes, or length truncation.
     */
    public static String getTableName(String tenantId, String appId, String entityName) {
        String key = (tenantId != null ? tenantId : "default") + "_" + (appId != null ? appId : "default") + "_" + entityName;
        EntitySchema schema = SchemaManager.loadSchema(key);
        if (schema != null) {
            return SchemaManager.getPhysicalTableName(schema);
        }
        EntitySchema fallback = new EntitySchema();
        fallback.setTenantId(tenantId);
        fallback.setAppId(appId);
        fallback.setName(entityName);
        return SchemaManager.getPhysicalTableName(fallback);
    }

    /**
     * H6 — Named helper: returns true if the given user has CHECKER role OR is the app owner / system.
     *
     * App owners are deliberately treated as super-checkers here. If strict owner-SoD is required,
     * the caller should additionally verify that the owner is not the same user who submitted the record
     * (the submittedBy != checkerUserId guard in approveRecord/rejectRecord already handles that).
     */
    public static boolean hasCheckerOrOwnerPermission(String tenantId, String appId, String entityName, String userId) {
        return UserRoleService.isChecker(tenantId, appId, entityName, userId)
                || AppAuthorization.isAppOwnerOrSystem(tenantId, appId, userId);
    }

    /**
     * Two-level checker chain: returns true if the given user has the CHECKER_L2 role
     * (final signoff) OR is the app owner / system. Mirrors {@link #hasCheckerOrOwnerPermission}
     * for the level-1 role.
     */
    public static boolean hasCheckerL2OrOwnerPermission(String tenantId, String appId, String entityName, String userId) {
        return UserRoleService.isCheckerL2(tenantId, appId, entityName, userId)
                || AppAuthorization.isAppOwnerOrSystem(tenantId, appId, userId);
    }

    /**
     * How many checker levels this entity requires (1 or 2). Loads the schema fresh rather
     * than trusting a caller-supplied value, since the level gates who is allowed to act.
     * Defaults to 1 (today's single-level behaviour) if the schema cannot be resolved.
     */
    public static int resolveApprovalLevels(String tenantId, String appId, String entityName) {
        String key = (tenantId != null ? tenantId : "default") + "_" + (appId != null ? appId : "default") + "_" + entityName;
        EntitySchema schema = SchemaManager.loadSchema(key);
        return schema != null ? schema.getEffectiveApprovalLevels() : 1;
    }

    /**
     * H6 — Named helper: returns true if the given user has MAKER role OR is the app owner / system.
     */
    public static boolean hasMakerOrOwnerPermission(String tenantId, String appId, String entityName, String userId) {
        return UserRoleService.isMaker(tenantId, appId, entityName, userId)
                || AppAuthorization.isAppOwnerOrSystem(tenantId, appId, userId);
    }

    /**
     * Submits a DRAFT or REJECTED record for approval (DRAFT/REJECTED -> PENDING).
     */
    public static Map<String, Object> submitForApproval(String tenantId, String appId, String entityName, String rowId, String submitterUserId, String comments) throws Exception {
        if (!hasMakerOrOwnerPermission(tenantId, appId, entityName, submitterUserId)) {
            throw new IllegalStateException("Forbidden: User '" + submitterUserId + "' does not have MAKER role on entity " + entityName);
        }

        String tableName = getTableName(tenantId, appId, entityName);

        try (Connection conn = JdbcManager.getConnection(tenantId)) {
            conn.setAutoCommit(false);
            try {
                // Fetch current record with SELECT ... FOR UPDATE
                String selectSql = "SELECT \"APPROVAL_STATUS\", \"APPROVAL_REVISION\", \"SUBMITTED_BY\" FROM \"" + tableName + "\" WHERE \"ID\" = ? FOR UPDATE";
                String currentStateStr = "DRAFT";
                int currentRevision = 1;

                try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                    ps.setObject(1, parseRowId(rowId));
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalArgumentException("Record not found with ID: " + rowId);
                        }
                        currentStateStr = rs.getString("APPROVAL_STATUS");
                        if (currentStateStr == null) currentStateStr = "DRAFT";
                        currentRevision = rs.getInt("APPROVAL_REVISION");
                        if (currentRevision <= 0) currentRevision = 1;
                    }
                }

                Status currentStatus = Status.fromValue(currentStateStr);
                if (currentStatus == Status.PENDING || currentStatus == Status.PENDING_L2) {
                    throw new ApprovalConflictException("Record " + rowId + " is already in " + currentStatus.getValue() + " approval state");
                }

                // Increment revision on resubmit after rejection or state update
                int targetRevision = "REJECTED".equalsIgnoreCase(currentStateStr) ? currentRevision + 1 : currentRevision;

                // Update record status to PENDING
                String updateSql = "UPDATE \"" + tableName + "\" SET \"APPROVAL_STATUS\" = 'PENDING', \"APPROVAL_REVISION\" = ?, \"SUBMITTED_BY\" = ?, \"SUBMITTED_AT\" = NOW(), \"REJECTION_REASON\" = NULL WHERE \"ID\" = ?";
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setInt(1, targetRevision);
                    ps.setString(2, submitterUserId);
                    ps.setObject(3, parseRowId(rowId));
                    ps.executeUpdate();
                }

                String diffJson = MAPPER.writeValueAsString(Map.of(
                        "rowId", rowId,
                        "fromState", currentStateStr,
                        "toState", "PENDING",
                        "revision", targetRevision
                ));

                // Audit log entry
                logAuditEntry(conn, tenantId, appId, entityName, rowId, targetRevision, currentStateStr, "PENDING", submitterUserId, "MAKER", comments, diffJson);

                conn.commit();
                LOG.info("[ApprovalService] Submitted record {} in {} for approval by {}", rowId, tableName, submitterUserId);

                return Map.of(
                        "tenantId", tenantId,
                        "appId", appId,
                        "entityName", entityName,
                        "rowId", rowId,
                        "status", "PENDING",
                        "submittedBy", submitterUserId,
                        "revision", targetRevision
                );
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    /**
     * Approves a PENDING record.
     *
     * <p>Single-level entities (the default): PENDING -&gt; APPROVED, requires the CHECKER role.
     *
     * <p>Two-level entities ({@code approvalLevels == 2}): the same call also drives the second
     * hop, keyed on the row's <em>current</em> state rather than on anything the caller passes:
     * <ul>
     *   <li>PENDING -&gt; PENDING_L2, requires CHECKER (level-1). Not yet live; no revision merge.</li>
     *   <li>PENDING_L2 -&gt; APPROVED, requires CHECKER_L2 (level-2, final signoff). Separation of
     *       duties extends across the chain: the level-2 approver may be neither the submitter
     *       nor the level-1 approver who advanced it to PENDING_L2 (read back from APPROVED_BY,
     *       which the PENDING-&gt;PENDING_L2 hop stamps with the level-1 approver's id).</li>
     * </ul>
     */
    public static Map<String, Object> approveRecord(String tenantId, String appId, String entityName, String rowId, String checkerUserId, String comments) throws Exception {
        String tableName = getTableName(tenantId, appId, entityName);
        int levels = resolveApprovalLevels(tenantId, appId, entityName);

        try (Connection conn = JdbcManager.getConnection(tenantId)) {
            conn.setAutoCommit(false);
            try {
                String currentStateStr = "PENDING";
                int currentRevision = 1;
                String submittedBy = null;
                String parentRowId = null;
                String priorApproverId = null;

                String selectSql = "SELECT * FROM \"" + tableName + "\" WHERE \"ID\" = ? FOR UPDATE";
                try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                    ps.setObject(1, parseRowId(rowId));
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalArgumentException("Record not found with ID: " + rowId);
                        }
                        currentStateStr = rs.getString("APPROVAL_STATUS");
                        currentRevision = rs.getInt("APPROVAL_REVISION");
                        if (currentRevision <= 0) currentRevision = 1;
                        submittedBy = rs.getString("SUBMITTED_BY");
                        parentRowId = readOptionalString(rs, "APPROVAL_PARENT_ID");
                        priorApproverId = readOptionalString(rs, "APPROVED_BY");
                    }
                }

                Status currentStatus = Status.fromValue(currentStateStr);
                boolean atLevel2 = currentStatus == Status.PENDING_L2;

                if (currentStatus != Status.PENDING && currentStatus != Status.PENDING_L2) {
                    throw new ApprovalConflictException("Cannot approve record " + rowId + ": state is " + currentStateStr + " (must be PENDING" + (levels == 2 ? " or PENDING_L2" : "") + ")");
                }

                if (atLevel2) {
                    if (!hasCheckerL2OrOwnerPermission(tenantId, appId, entityName, checkerUserId)) {
                        throw new IllegalStateException("Forbidden: User '" + checkerUserId + "' does not have CHECKER_L2 role on entity " + entityName);
                    }
                } else if (!hasCheckerOrOwnerPermission(tenantId, appId, entityName, checkerUserId)) {
                    throw new IllegalStateException("Forbidden: User '" + checkerUserId + "' does not have CHECKER role on entity " + entityName);
                }

                // CRITICAL SEPARATION OF DUTIES ENFORCEMENT:
                // Approver CANNOT be the same user who submitted the record for approval, and at
                // level 2 CANNOT be the same user who already approved it at level 1 either.
                if (submittedBy != null && submittedBy.equalsIgnoreCase(checkerUserId)) {
                    throw new IllegalStateException("Separation of duties violation: Maker cannot approve their own submission.");
                }
                if (atLevel2 && priorApproverId != null && priorApproverId.equalsIgnoreCase(checkerUserId)) {
                    throw new IllegalStateException("Separation of duties violation: the level-2 checker cannot be the same user who approved at level 1.");
                }

                boolean advancesToLevel2 = !atLevel2 && levels == 2;
                String nextStatus = advancesToLevel2 ? "PENDING_L2" : "APPROVED";
                String actorRole = atLevel2 ? "CHECKER_L2" : "CHECKER";

                // PENDING -> PENDING_L2 stamps APPROVED_BY/APPROVED_AT with the level-1 approver so
                // the level-2 SoD check above can read it back; PENDING_L2 -> APPROVED overwrites it
                // with the level-2 approver, which is the row's final, live state.
                String updateSql = "UPDATE \"" + tableName + "\" SET \"APPROVAL_STATUS\" = ?, \"APPROVED_BY\" = ?, \"APPROVED_AT\" = NOW(), \"REJECTION_REASON\" = NULL WHERE \"ID\" = ?";
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setString(1, nextStatus);
                    ps.setString(2, checkerUserId);
                    ps.setObject(3, parseRowId(rowId));
                    ps.executeUpdate();
                }

                String diffJson = MAPPER.writeValueAsString(Map.of(
                        "rowId", rowId,
                        "fromState", currentStateStr,
                        "toState", nextStatus,
                        "submittedBy", submittedBy != null ? submittedBy : "",
                        "approvedBy", checkerUserId
                ));

                // Audit log entry
                logAuditEntry(conn, tenantId, appId, entityName, rowId, currentRevision, currentStateStr, nextStatus, checkerUserId, actorRole, comments, diffJson);

                // C2.3 — this row is a revision of a live APPROVED row. Fold it back into the
                // parent (which keeps its id, so foreign keys stay intact) and drop the
                // revision row. Only once the row has reached its FINAL APPROVED state — an
                // intermediate PENDING_L2 hop must not merge yet, since the record is not
                // actually approved until level 2 signs off. Same transaction => atomic.
                String supersededParentId = null;
                if ("APPROVED".equals(nextStatus) && parentRowId != null && !parentRowId.isBlank()) {
                    boolean merged = mergeRevisionIntoParent(conn, tenantId, appId, entityName, tableName,
                            rowId, parentRowId, currentRevision, checkerUserId, submittedBy, comments);
                    // When the parent had already been deleted the revision itself stays live, so the
                    // caller must be told about `rowId`, not about the parent id that no longer resolves.
                    if (merged) {
                        supersededParentId = parentRowId;
                    }
                }

                conn.commit();
                LOG.info("[ApprovalService] Record {} in {} moved {} -> {} by {} {}", rowId, tableName, currentStateStr, nextStatus, actorRole, checkerUserId);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("tenantId", tenantId);
                result.put("appId", appId);
                result.put("entityName", entityName);
                result.put("rowId", supersededParentId != null ? supersededParentId : rowId);
                result.put("status", nextStatus);
                result.put("approvedBy", checkerUserId);
                if (supersededParentId != null) {
                    result.put("mergedFromRevisionRowId", rowId);
                    result.put("revision", currentRevision);
                }
                return result;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    /**
     * Rejects a PENDING (or, for two-level entities, PENDING_L2) record.
     *
     * <p>Level-1 reject is terminal: PENDING -&gt; REJECTED, back to the maker, unchanged from the
     * single-level workflow.
     *
     * <p>Level-2 reject is NOT terminal: PENDING_L2 -&gt; PENDING, sent back to the level-1 checker
     * for re-review rather than all the way back to the maker. {@code rejection_reason} is set so
     * the level-1 checker sees why; {@code approved_by}/{@code approved_at} are cleared since the
     * row is, once again, simply awaiting a first approval.
     */
    public static Map<String, Object> rejectRecord(String tenantId, String appId, String entityName, String rowId, String checkerUserId, String reason) throws Exception {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Rejection reason is required");
        }

        String tableName = getTableName(tenantId, appId, entityName);

        try (Connection conn = JdbcManager.getConnection(tenantId)) {
            conn.setAutoCommit(false);
            try {
                String currentStateStr = "PENDING";
                int currentRevision = 1;
                String submittedBy = null;
                String priorApproverId = null;

                String selectSql = "SELECT \"APPROVAL_STATUS\", \"APPROVAL_REVISION\", \"SUBMITTED_BY\", \"APPROVED_BY\" FROM \"" + tableName + "\" WHERE \"ID\" = ? FOR UPDATE";
                try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                    ps.setObject(1, parseRowId(rowId));
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalArgumentException("Record not found with ID: " + rowId);
                        }
                        currentStateStr = rs.getString("APPROVAL_STATUS");
                        currentRevision = rs.getInt("APPROVAL_REVISION");
                        if (currentRevision <= 0) currentRevision = 1;
                        submittedBy = rs.getString("SUBMITTED_BY");
                        priorApproverId = readOptionalString(rs, "APPROVED_BY");
                    }
                }

                Status currentStatus = Status.fromValue(currentStateStr);
                boolean atLevel2 = currentStatus == Status.PENDING_L2;

                if (currentStatus != Status.PENDING && currentStatus != Status.PENDING_L2) {
                    throw new ApprovalConflictException("Cannot reject record " + rowId + ": state is " + currentStateStr + " (must be PENDING or PENDING_L2)");
                }

                if (atLevel2) {
                    if (!hasCheckerL2OrOwnerPermission(tenantId, appId, entityName, checkerUserId)) {
                        throw new IllegalStateException("Forbidden: User '" + checkerUserId + "' does not have CHECKER_L2 role on entity " + entityName);
                    }
                } else if (!hasCheckerOrOwnerPermission(tenantId, appId, entityName, checkerUserId)) {
                    throw new IllegalStateException("Forbidden: User '" + checkerUserId + "' does not have CHECKER role on entity " + entityName);
                }

                // CRITICAL SEPARATION OF DUTIES ENFORCEMENT:
                if (submittedBy != null && submittedBy.equalsIgnoreCase(checkerUserId)) {
                    throw new IllegalStateException("Separation of duties violation: Maker cannot reject their own submission.");
                }
                if (atLevel2 && priorApproverId != null && priorApproverId.equalsIgnoreCase(checkerUserId)) {
                    throw new IllegalStateException("Separation of duties violation: the level-2 checker cannot be the same user who approved at level 1.");
                }

                String nextStatus = atLevel2 ? "PENDING" : "REJECTED";
                String actorRole = atLevel2 ? "CHECKER_L2" : "CHECKER";

                String updateSql = atLevel2
                        // Sent back to level-1: this is a re-review, not a terminal rejection, so the
                        // row returns to plain PENDING with no approver on record yet.
                        ? "UPDATE \"" + tableName + "\" SET \"APPROVAL_STATUS\" = 'PENDING', \"APPROVED_BY\" = NULL, \"APPROVED_AT\" = NULL, \"REJECTION_REASON\" = ? WHERE \"ID\" = ?"
                        : "UPDATE \"" + tableName + "\" SET \"APPROVAL_STATUS\" = 'REJECTED', \"APPROVED_BY\" = ?, \"APPROVED_AT\" = NOW(), \"REJECTION_REASON\" = ? WHERE \"ID\" = ?";
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    if (atLevel2) {
                        ps.setString(1, reason);
                        ps.setObject(2, parseRowId(rowId));
                    } else {
                        ps.setString(1, checkerUserId);
                        ps.setString(2, reason);
                        ps.setObject(3, parseRowId(rowId));
                    }
                    ps.executeUpdate();
                }

                String diffJson = MAPPER.writeValueAsString(Map.of(
                        "rowId", rowId,
                        "fromState", currentStateStr,
                        "toState", nextStatus,
                        "submittedBy", submittedBy != null ? submittedBy : "",
                        "rejectedBy", checkerUserId,
                        "reason", reason
                ));

                // Audit log entry
                logAuditEntry(conn, tenantId, appId, entityName, rowId, currentRevision, currentStateStr, nextStatus, checkerUserId, actorRole, reason, diffJson);

                conn.commit();
                LOG.info("[ApprovalService] Record {} in {} moved {} -> {} by {} {} with reason: {}", rowId, tableName, currentStateStr, nextStatus, actorRole, checkerUserId, reason);

                return Map.of(
                        "tenantId", tenantId,
                        "appId", appId,
                        "entityName", entityName,
                        "rowId", rowId,
                        "status", nextStatus,
                        "rejectedBy", checkerUserId,
                        "reason", reason
                );
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    /** Page size for the checker queue. Also the cap the badge count agrees with. */
    public static final int QUEUE_PAGE_SIZE = 100;

    /**
     * Gets pending records across an entity table for the Checker Queue.
     * Enforces role authorization (callerUserId must be CHECKER or App Owner/System).
     *
     * <p>C3.9 — paged, and the caller's own submissions are excluded. Separation of
     * duties means a checker can never approve what they submitted, so including
     * those rows offered work the backend would refuse. The previous fixed
     * {@code LIMIT 500} with no offset silently truncated a longer queue, and the
     * C3.7 switch to FIFO made that worse rather than better: the oldest 500 pin
     * the top of the list and newer work becomes permanently invisible.
     */
    public static List<Map<String, Object>> getPendingQueue(String tenantId, String appId, String entityName, String callerUserId) throws Exception {
        return getPendingQueue(tenantId, appId, entityName, callerUserId, 0);
    }

    public static List<Map<String, Object>> getPendingQueue(String tenantId, String appId, String entityName,
                                                           String callerUserId, int offset) throws Exception {
        return getPendingQueue(tenantId, appId, entityName, callerUserId, offset, 1);
    }

    /**
     * Two-level checker chain — {@code level == 2} returns the level-2 (final signoff) queue
     * instead of the level-1 one: PENDING_L2 rows, authorized against CHECKER_L2, additionally
     * excluding rows the caller themselves already approved at level 1 (separation of duties
     * extends across the chain, not just against the original maker).
     */
    public static List<Map<String, Object>> getPendingQueue(String tenantId, String appId, String entityName,
                                                           String callerUserId, int offset, int level) throws Exception {
        if (callerUserId == null || callerUserId.isBlank()) {
            throw new IllegalStateException("Unauthorized: Caller user ID required");
        }
        boolean l2 = level == 2;
        if (l2) {
            if (!hasCheckerL2OrOwnerPermission(tenantId, appId, entityName, callerUserId)) {
                throw new IllegalStateException("Forbidden: User '" + callerUserId + "' does not have CHECKER_L2 or owner rights on entity " + entityName);
            }
        } else if (!hasCheckerOrOwnerPermission(tenantId, appId, entityName, callerUserId)) {
            throw new IllegalStateException("Forbidden: User '" + callerUserId + "' does not have CHECKER or owner rights on entity " + entityName);
        }

        String tableName = getTableName(tenantId, appId, entityName);
        List<Map<String, Object>> results = new ArrayList<>();

        // C3.7: oldest submission first. A review queue is FIFO — under DESC the
        // longest-waiting record sinks to the bottom and starves, which is exactly
        // what an approval SLA exists to prevent.
        String statusFilter = l2 ? "PENDING_L2" : "PENDING";
        String sql = "SELECT * FROM \"" + tableName + "\""
                + " WHERE \"APPROVAL_STATUS\" = '" + statusFilter + "'"
                + " AND (\"SUBMITTED_BY\" IS NULL OR \"SUBMITTED_BY\" <> ?)"
                + (l2 ? " AND (\"APPROVED_BY\" IS NULL OR \"APPROVED_BY\" <> ?)" : "")
                + " ORDER BY \"SUBMITTED_AT\" ASC LIMIT ? OFFSET ?";

        try (Connection conn = JdbcManager.getConnection(tenantId);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, callerUserId);
            if (l2) ps.setString(i++, callerUserId);
            ps.setInt(i++, QUEUE_PAGE_SIZE);
            ps.setInt(i, Math.max(0, offset));
            try (ResultSet rs = ps.executeQuery()) {
                int colCount = rs.getMetaData().getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int c = 1; c <= colCount; c++) {
                        row.put(rs.getMetaData().getColumnName(c).toLowerCase(Locale.ROOT), rs.getObject(c));
                    }
                    results.add(row);
                }
            }
        }
        return results;
    }

    /**
     * Number of records awaiting this caller's review, for the nav badge (C3.7).
     *
     * <p>A dedicated COUNT exists because the badge polls: reusing
     * {@link #getPendingQueue} would ship a full page of materialised rows per
     * entity per user every polling interval purely to read their length.
     * Authorization is identical to the queue itself.
     *
     * <p>C3.9 — the predicate must match {@link #getPendingQueue} exactly. It used
     * to count every PENDING row including the caller's own submissions, which
     * separation of duties forbids them from approving: the badge said "3" and the
     * queue then offered nothing actionable. A badge that overstates is worse than
     * no badge, because users stop trusting it and stop opening the queue.
     */
    public static int getPendingCount(String tenantId, String appId, String entityName, String callerUserId) throws Exception {
        return getPendingCount(tenantId, appId, entityName, callerUserId, 1);
    }

    /** Two-level checker chain — {@code level == 2} counts the level-2 queue instead. */
    public static int getPendingCount(String tenantId, String appId, String entityName, String callerUserId, int level) throws Exception {
        if (callerUserId == null || callerUserId.isBlank()) {
            throw new IllegalStateException("Unauthorized: Caller user ID required");
        }
        boolean l2 = level == 2;
        if (l2) {
            if (!hasCheckerL2OrOwnerPermission(tenantId, appId, entityName, callerUserId)) {
                throw new IllegalStateException("Forbidden: User '" + callerUserId + "' does not have CHECKER_L2 or owner rights on entity " + entityName);
            }
        } else if (!hasCheckerOrOwnerPermission(tenantId, appId, entityName, callerUserId)) {
            throw new IllegalStateException("Forbidden: User '" + callerUserId + "' does not have CHECKER or owner rights on entity " + entityName);
        }

        String tableName = getTableName(tenantId, appId, entityName);
        String statusFilter = l2 ? "PENDING_L2" : "PENDING";
        String sql = "SELECT COUNT(*) FROM \"" + tableName + "\""
                + " WHERE \"APPROVAL_STATUS\" = '" + statusFilter + "'"
                + " AND (\"SUBMITTED_BY\" IS NULL OR \"SUBMITTED_BY\" <> ?)"
                + (l2 ? " AND (\"APPROVED_BY\" IS NULL OR \"APPROVED_BY\" <> ?)" : "");
        try (Connection conn = JdbcManager.getConnection(tenantId);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, callerUserId);
            if (l2) ps.setString(2, callerUserId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Gets audit history entries for a given record.
     * Enforces role authorization (callerUserId must be MAKER, CHECKER, CHECKER_L2, or App Owner/System).
     */
    public static List<Map<String, Object>> getAuditTrail(String tenantId, String appId, String entityName, String rowId, String callerUserId) throws Exception {
        if (callerUserId == null || callerUserId.isBlank()) {
            throw new IllegalStateException("Unauthorized: Caller user ID required");
        }
        boolean isMaker = UserRoleService.isMaker(tenantId, appId, entityName, callerUserId);
        boolean isOwnerOrHasCheckerRole = hasCheckerOrOwnerPermission(tenantId, appId, entityName, callerUserId)
                || hasCheckerL2OrOwnerPermission(tenantId, appId, entityName, callerUserId);

        if (!isMaker && !isOwnerOrHasCheckerRole) {
            throw new IllegalStateException("Forbidden: User '" + callerUserId + "' does not have permission to view approval audit history for " + entityName);
        }

        List<Map<String, Object>> trail = new ArrayList<>();
        // C2.6: most recent first. `revision` is the tiebreaker for entries that share a
        // timestamp; `id` is a UUID and would order arbitrarily.
        String sql = "SELECT * FROM appbana_approvals WHERE tenant_id = ? AND app_id = ? AND entity_name = ? AND row_id = ? ORDER BY created_at DESC, revision DESC";

        try (Connection conn = JdbcManager.getConnection(tenantId);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            ps.setString(2, appId);
            ps.setString(3, entityName);
            ps.setString(4, rowId);

            try (ResultSet rs = ps.executeQuery()) {
                int colCount = rs.getMetaData().getColumnCount();
                while (rs.next()) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        entry.put(rs.getMetaData().getColumnName(i).toLowerCase(Locale.ROOT), rs.getObject(i));
                    }
                    trail.add(entry);
                }
            }
        }
        return trail;
    }

    /**
     * C2.3 — atomically folds an approved revision row back into the live parent row.
     *
     * <p>Runs inside the caller's transaction (which already holds a {@code FOR UPDATE}
     * lock on the revision row). Steps:
     * <ol>
     *   <li>{@code SELECT ... FOR UPDATE} the parent, snapshotting it for the audit diff.</li>
     *   <li>Copy every business column from the revision onto the parent. Business =
     *       everything except the PK, {@code CREATED_AT} and the approval bookkeeping
     *       columns, which are set explicitly.</li>
     *   <li>Mark the parent APPROVED at the revision's revision number.</li>
     *   <li>Delete the revision row.</li>
     *   <li>Write an audit entry against the <em>parent</em> row id whose diff carries the
     *       full pre-merge snapshot — this is how the previous version stays recoverable.</li>
     * </ol>
     *
     * <p>The parent id is deliberately preserved so foreign keys created by Phase B.H4 keep
     * pointing at a live row. That is why no {@code superseded_by} column is needed.
     */
    private static boolean mergeRevisionIntoParent(Connection conn, String tenantId, String appId, String entityName,
                                                   String tableName, String revisionRowId, String parentRowId,
                                                   int revision, String checkerUserId, String submittedBy,
                                                   String comments) throws Exception {

        Map<String, Object> parentBefore = new LinkedHashMap<>();
        List<String> allColumns = new ArrayList<>();

        String parentSql = "SELECT * FROM \"" + tableName + "\" WHERE \"ID\" = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(parentSql)) {
            ps.setObject(1, parseRowId(parentRowId));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    // Parent vanished (deleted out from under us). Leave the revision row standing
                    // as the new live record rather than losing the data entirely.
                    LOG.warn("[ApprovalService] Revision {} points at missing parent {} in {} — keeping revision as live row",
                            revisionRowId, parentRowId, tableName);
                    clearRevisionPointer(conn, tableName, revisionRowId);
                    return false;
                }
                int cols = rs.getMetaData().getColumnCount();
                for (int i = 1; i <= cols; i++) {
                    String col = rs.getMetaData().getColumnName(i);
                    allColumns.add(col);
                    parentBefore.put(col.toLowerCase(Locale.ROOT), rs.getObject(i));
                }
            }
        }

        Map<String, Object> revisionRow = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM \"" + tableName + "\" WHERE \"ID\" = ?")) {
            ps.setObject(1, parseRowId(revisionRowId));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApprovalConflictException("Revision row " + revisionRowId + " disappeared mid-approval");
                }
                int cols = rs.getMetaData().getColumnCount();
                for (int i = 1; i <= cols; i++) {
                    revisionRow.put(rs.getMetaData().getColumnName(i).toUpperCase(Locale.ROOT), rs.getObject(i));
                }
            }
        }

        List<String> setParts = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        for (String col : allColumns) {
            if (NON_MERGED_COLUMNS.contains(col.toUpperCase(Locale.ROOT))) {
                continue;
            }
            setParts.add("\"" + col + "\" = ?");
            params.add(revisionRow.get(col.toUpperCase(Locale.ROOT)));
        }

        setParts.add("\"APPROVAL_STATUS\" = 'APPROVED'");
        setParts.add("\"APPROVAL_REVISION\" = ?");
        params.add(revision);
        setParts.add("\"SUBMITTED_BY\" = ?");
        params.add(submittedBy);
        setParts.add("\"SUBMITTED_AT\" = ?");
        params.add(revisionRow.get("SUBMITTED_AT"));
        setParts.add("\"APPROVED_BY\" = ?");
        params.add(checkerUserId);
        setParts.add("\"APPROVED_AT\" = NOW()");
        setParts.add("\"REJECTION_REASON\" = NULL");
        if (allColumns.stream().anyMatch(c -> "APPROVAL_PARENT_ID".equalsIgnoreCase(c))) {
            setParts.add("\"APPROVAL_PARENT_ID\" = NULL");
        }

        String mergeSql = "UPDATE \"" + tableName + "\" SET " + String.join(", ", setParts) + " WHERE \"ID\" = ?";
        try (PreparedStatement ps = conn.prepareStatement(mergeSql)) {
            int i = 1;
            for (Object p : params) {
                ps.setObject(i++, p);
            }
            ps.setObject(i, parseRowId(parentRowId));
            ps.executeUpdate();
        }

        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM \"" + tableName + "\" WHERE \"ID\" = ?")) {
            ps.setObject(1, parseRowId(revisionRowId));
            ps.executeUpdate();
        }

        Map<String, Object> after = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : revisionRow.entrySet()) {
            if (!NON_MERGED_COLUMNS.contains(e.getKey())) {
                after.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue());
            }
        }

        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("rowId", parentRowId);
        diff.put("fromState", "APPROVED");
        diff.put("toState", "APPROVED");
        diff.put("mergedFromRevisionRowId", revisionRowId);
        diff.put("revision", revision);
        diff.put("before", stringifyValues(parentBefore));
        diff.put("after", stringifyValues(after));

        logAuditEntry(conn, tenantId, appId, entityName, parentRowId, revision, "APPROVED", "APPROVED",
                checkerUserId, "CHECKER", comments, MAPPER.writeValueAsString(diff));

        LOG.info("[ApprovalService] Revision {} merged into live parent {} in {} (revision {})",
                revisionRowId, parentRowId, tableName, revision);
        return true;
    }

    /**
     * Fallback when a revision's parent no longer exists: null out the dangling pointer so
     * the revision becomes an ordinary live row instead of an orphan.
     *
     * <p>Failures are propagated on purpose. This runs inside the caller's open transaction, and
     * in PostgreSQL a failed statement aborts the whole transaction — swallowing the error here
     * would let {@code approveRecord} reach {@code commit()} (silently downgraded to a rollback)
     * and report a success that never persisted.
     */
    private static void clearRevisionPointer(Connection conn, String tableName, String revisionRowId)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE \"" + tableName + "\" SET \"APPROVAL_PARENT_ID\" = NULL WHERE \"ID\" = ?")) {
            ps.setObject(1, parseRowId(revisionRowId));
            ps.executeUpdate();
        }
    }

    /**
     * Audit diffs are stored as JSON. JDBC hands back Timestamp/BigDecimal/array types that
     * Jackson may not serialise predictably, so values are normalised to strings first.
     */
    private static Map<String, Object> stringifyValues(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : row.entrySet()) {
            Object v = e.getValue();
            if (v == null || v instanceof Number || v instanceof Boolean || v instanceof String) {
                out.put(e.getKey(), v);
            } else {
                out.put(e.getKey(), String.valueOf(v));
            }
        }
        return out;
    }

    /** Reads a column that may not exist on legacy tables, returning null instead of throwing. */
    private static String readOptionalString(ResultSet rs, String column) {
        try {
            return rs.getString(column);
        } catch (Exception e) {
            return null;
        }
    }

    private static void logAuditEntry(Connection conn, String tenantId, String appId, String entityName, String rowId, int revision, String fromState, String toState, String actorUserId, String actorRole, String reason, String diff) throws Exception {
        // H10 FIX — Truncation must produce valid JSON so audit UIs can JSON.parse without throwing.
        // The previous approach sliced mid-string and appended [TRUNCATED]" producing unbalanced JSON.
        final int MAX_DIFF_LEN = 65536;
        if (diff != null && diff.length() > MAX_DIFF_LEN) {
            // C2.3 — for a revision merge, `before` is the ONLY surviving copy of the previous
            // approved row (the parent is overwritten in place and the revision row is deleted),
            // whereas `after` is always reconstructible by reading the now-live parent. So shed
            // `after` before resorting to a blind prefix truncation that would destroy `before`.
            diff = dropAfterSnapshot(diff, MAX_DIFF_LEN, entityName, rowId);
        }
        if (diff != null && diff.length() > MAX_DIFF_LEN) {
            int originalLen = diff.length();
            // Reserve ~120 chars for the sentinel wrapper so the total stays under MAX_DIFF_LEN.
            int prefixLen = MAX_DIFF_LEN - 120;
            try {
                diff = MAPPER.writeValueAsString(Map.of(
                        "truncated", true,
                        "originalLen", originalLen,
                        "prefix", diff.substring(0, Math.min(diff.length(), prefixLen))
                ));
            } catch (Exception e) {
                diff = "{\"truncated\":true,\"originalLen\":" + originalLen + "}";
            }
            LOG.warn("[ApprovalService] diff snapshot truncated: originalLen={} for entity={} rowId={}", originalLen, entityName, rowId);
        }

        String sql = "INSERT INTO appbana_approvals (id, tenant_id, app_id, entity_name, row_id, revision, from_state, to_state, actor_user_id, actor_role, reason, diff, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, tenantId);
            ps.setString(3, appId);
            ps.setString(4, entityName);
            ps.setString(5, rowId);
            ps.setInt(6, revision);
            ps.setString(7, fromState);
            ps.setString(8, toState);
            ps.setString(9, actorUserId);
            ps.setString(10, actorRole);
            ps.setString(11, reason);
            ps.setString(12, diff);
            ps.executeUpdate();
        }
    }

    /**
     * Oversized-diff first resort: drop the {@code after} snapshot, which can always be recovered
     * by reading the live row, so that the irreplaceable {@code before} snapshot survives.
     * Returns the input unchanged if it is not a JSON object or has no {@code after} key.
     */
    @SuppressWarnings("unchecked")
    private static String dropAfterSnapshot(String diff, int maxLen, String entityName, String rowId) {
        try {
            Object parsed = MAPPER.readValue(diff, Object.class);
            if (!(parsed instanceof Map)) {
                return diff;
            }
            Map<String, Object> map = new LinkedHashMap<>((Map<String, Object>) parsed);
            if (!map.containsKey("after")) {
                return diff;
            }
            map.remove("after");
            map.put("afterOmitted", true);
            String shrunk = MAPPER.writeValueAsString(map);
            if (shrunk.length() <= maxLen) {
                LOG.warn("[ApprovalService] diff too large — dropped 'after' snapshot to preserve 'before' "
                        + "for entity={} rowId={} (after is recoverable from the live row)", entityName, rowId);
                return shrunk;
            }
            return shrunk;
        } catch (Exception e) {
            return diff;
        }
    }

    private static Object parseRowId(String rowId) {
        if (rowId == null) return "";
        try {
            return Long.parseLong(rowId);
        } catch (NumberFormatException e) {
            return rowId;
        }
    }
}

