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
                if (currentStatus == Status.PENDING) {
                    throw new ApprovalConflictException("Record " + rowId + " is already in PENDING approval state");
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
     * Approves a PENDING record (PENDING -> APPROVED).
     */
    public static Map<String, Object> approveRecord(String tenantId, String appId, String entityName, String rowId, String checkerUserId, String comments) throws Exception {
        if (!hasCheckerOrOwnerPermission(tenantId, appId, entityName, checkerUserId)) {
            throw new IllegalStateException("Forbidden: User '" + checkerUserId + "' does not have CHECKER role on entity " + entityName);
        }

        String tableName = getTableName(tenantId, appId, entityName);

        try (Connection conn = JdbcManager.getConnection(tenantId)) {
            conn.setAutoCommit(false);
            try {
                String currentStateStr = "PENDING";
                int currentRevision = 1;
                String submittedBy = null;
                String parentRowId = null;

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
                    }
                }

                if (!"PENDING".equalsIgnoreCase(currentStateStr)) {
                    throw new ApprovalConflictException("Cannot approve record " + rowId + ": state is " + currentStateStr + " (must be PENDING)");
                }

                // CRITICAL SEPARATION OF DUTIES ENFORCEMENT:
                // Approver CANNOT be the same user who submitted the record for approval!
                if (submittedBy != null && submittedBy.equalsIgnoreCase(checkerUserId)) {
                    throw new IllegalStateException("Separation of duties violation: Maker cannot approve their own submission.");
                }

                // Update record status to APPROVED
                String updateSql = "UPDATE \"" + tableName + "\" SET \"APPROVAL_STATUS\" = 'APPROVED', \"APPROVED_BY\" = ?, \"APPROVED_AT\" = NOW(), \"REJECTION_REASON\" = NULL WHERE \"ID\" = ?";
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setString(1, checkerUserId);
                    ps.setObject(2, parseRowId(rowId));
                    ps.executeUpdate();
                }

                String diffJson = MAPPER.writeValueAsString(Map.of(
                        "rowId", rowId,
                        "fromState", "PENDING",
                        "toState", "APPROVED",
                        "submittedBy", submittedBy != null ? submittedBy : "",
                        "approvedBy", checkerUserId
                ));

                // Audit log entry
                logAuditEntry(conn, tenantId, appId, entityName, rowId, currentRevision, "PENDING", "APPROVED", checkerUserId, "CHECKER", comments, diffJson);

                // C2.3 — this row is a revision of a live APPROVED row. Fold it back into the
                // parent (which keeps its id, so foreign keys stay intact) and drop the
                // revision row. Same transaction => the replacement is atomic.
                String supersededParentId = null;
                if (parentRowId != null && !parentRowId.isBlank()) {
                    boolean merged = mergeRevisionIntoParent(conn, tenantId, appId, entityName, tableName,
                            rowId, parentRowId, currentRevision, checkerUserId, submittedBy, comments);
                    // When the parent had already been deleted the revision itself stays live, so the
                    // caller must be told about `rowId`, not about the parent id that no longer resolves.
                    if (merged) {
                        supersededParentId = parentRowId;
                    }
                }

                conn.commit();
                LOG.info("[ApprovalService] Approved record {} in {} by checker {}", rowId, tableName, checkerUserId);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("tenantId", tenantId);
                result.put("appId", appId);
                result.put("entityName", entityName);
                result.put("rowId", supersededParentId != null ? supersededParentId : rowId);
                result.put("status", "APPROVED");
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
     * Rejects a PENDING record (PENDING -> REJECTED).
     */
    public static Map<String, Object> rejectRecord(String tenantId, String appId, String entityName, String rowId, String checkerUserId, String reason) throws Exception {
        if (!hasCheckerOrOwnerPermission(tenantId, appId, entityName, checkerUserId)) {
            throw new IllegalStateException("Forbidden: User '" + checkerUserId + "' does not have CHECKER role on entity " + entityName);
        }

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

                String selectSql = "SELECT \"APPROVAL_STATUS\", \"APPROVAL_REVISION\", \"SUBMITTED_BY\" FROM \"" + tableName + "\" WHERE \"ID\" = ? FOR UPDATE";
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
                    }
                }

                if (!"PENDING".equalsIgnoreCase(currentStateStr)) {
                    throw new ApprovalConflictException("Cannot reject record " + rowId + ": state is " + currentStateStr + " (must be PENDING)");
                }

                // CRITICAL SEPARATION OF DUTIES ENFORCEMENT:
                if (submittedBy != null && submittedBy.equalsIgnoreCase(checkerUserId)) {
                    throw new IllegalStateException("Separation of duties violation: Maker cannot reject their own submission.");
                }

                // Update record status to REJECTED
                String updateSql = "UPDATE \"" + tableName + "\" SET \"APPROVAL_STATUS\" = 'REJECTED', \"APPROVED_BY\" = ?, \"APPROVED_AT\" = NOW(), \"REJECTION_REASON\" = ? WHERE \"ID\" = ?";
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setString(1, checkerUserId);
                    ps.setString(2, reason);
                    ps.setObject(3, parseRowId(rowId));
                    ps.executeUpdate();
                }

                String diffJson = MAPPER.writeValueAsString(Map.of(
                        "rowId", rowId,
                        "fromState", "PENDING",
                        "toState", "REJECTED",
                        "submittedBy", submittedBy != null ? submittedBy : "",
                        "rejectedBy", checkerUserId,
                        "reason", reason
                ));

                // Audit log entry
                logAuditEntry(conn, tenantId, appId, entityName, rowId, currentRevision, "PENDING", "REJECTED", checkerUserId, "CHECKER", reason, diffJson);

                conn.commit();
                LOG.info("[ApprovalService] Rejected record {} in {} by checker {} with reason: {}", rowId, tableName, checkerUserId, reason);

                return Map.of(
                        "tenantId", tenantId,
                        "appId", appId,
                        "entityName", entityName,
                        "rowId", rowId,
                        "status", "REJECTED",
                        "rejectedBy", checkerUserId,
                        "reason", reason
                );
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    /**
     * Gets all pending records across an entity table for the Checker Queue.
     * Enforces role authorization (callerUserId must be CHECKER or App Owner/System).
     */
    public static List<Map<String, Object>> getPendingQueue(String tenantId, String appId, String entityName, String callerUserId) throws Exception {
        if (callerUserId == null || callerUserId.isBlank()) {
            throw new IllegalStateException("Unauthorized: Caller user ID required");
        }
        if (!hasCheckerOrOwnerPermission(tenantId, appId, entityName, callerUserId)) {
            throw new IllegalStateException("Forbidden: User '" + callerUserId + "' does not have CHECKER or owner rights on entity " + entityName);
        }

        String tableName = getTableName(tenantId, appId, entityName);
        List<Map<String, Object>> results = new ArrayList<>();

        String sql = "SELECT * FROM \"" + tableName + "\" WHERE \"APPROVAL_STATUS\" = 'PENDING' ORDER BY \"SUBMITTED_AT\" DESC LIMIT 500";

        try (Connection conn = JdbcManager.getConnection(tenantId);
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            int colCount = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= colCount; i++) {
                    row.put(rs.getMetaData().getColumnName(i).toLowerCase(Locale.ROOT), rs.getObject(i));
                }
                results.add(row);
            }
        }
        return results;
    }

    /**
     * Gets audit history entries for a given record.
     * Enforces role authorization (callerUserId must be MAKER, CHECKER, or App Owner/System).
     */
    public static List<Map<String, Object>> getAuditTrail(String tenantId, String appId, String entityName, String rowId, String callerUserId) throws Exception {
        if (callerUserId == null || callerUserId.isBlank()) {
            throw new IllegalStateException("Unauthorized: Caller user ID required");
        }
        boolean isMaker = UserRoleService.isMaker(tenantId, appId, entityName, callerUserId);
        boolean isOwnerOrHasCheckerRole = hasCheckerOrOwnerPermission(tenantId, appId, entityName, callerUserId);

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

