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
                    throw new IllegalStateException("Record " + rowId + " is already in PENDING approval state");
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
                    throw new IllegalStateException("Cannot approve record " + rowId + ": state is " + currentStateStr + " (must be PENDING)");
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

                conn.commit();
                LOG.info("[ApprovalService] Approved record {} in {} by checker {}", rowId, tableName, checkerUserId);

                return Map.of(
                        "tenantId", tenantId,
                        "appId", appId,
                        "entityName", entityName,
                        "rowId", rowId,
                        "status", "APPROVED",
                        "approvedBy", checkerUserId
                );
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
                    throw new IllegalStateException("Cannot reject record " + rowId + ": state is " + currentStateStr + " (must be PENDING)");
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
        String sql = "SELECT * FROM appbana_approvals WHERE tenant_id = ? AND app_id = ? AND entity_name = ? AND row_id = ? ORDER BY created_at ASC";

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

    private static void logAuditEntry(Connection conn, String tenantId, String appId, String entityName, String rowId, int revision, String fromState, String toState, String actorUserId, String actorRole, String reason, String diff) throws Exception {
        // H10 FIX — Truncation must produce valid JSON so audit UIs can JSON.parse without throwing.
        // The previous approach sliced mid-string and appended [TRUNCATED]" producing unbalanced JSON.
        final int MAX_DIFF_LEN = 65536;
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

    private static Object parseRowId(String rowId) {
        if (rowId == null) return "";
        try {
            return Long.parseLong(rowId);
        } catch (NumberFormatException e) {
            return rowId;
        }
    }
}

