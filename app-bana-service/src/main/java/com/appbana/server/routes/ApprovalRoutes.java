package com.appbana.server.routes;

import com.appbana.api.Router;
import com.appbana.approval.ApprovalConflictException;
import com.appbana.approval.ApprovalService;
import com.appbana.config.ConfigManager;
import com.appbana.service.AuthService;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * ApprovalRoutes — Phase C2.2
 *
 * REST Endpoints for Maker-Checker Approval State Machine:
 * - POST /api/tenants/{tenantId}/apps/{appId}/entities/{entityName}/records/{id}/submit
 * - POST /api/tenants/{tenantId}/apps/{appId}/entities/{entityName}/records/{id}/approve
 * - POST /api/tenants/{tenantId}/apps/{appId}/entities/{entityName}/records/{id}/reject
 * - GET  /api/tenants/{tenantId}/apps/{appId}/entities/{entityName}/approvals/pending
 * - GET  /api/tenants/{tenantId}/apps/{appId}/entities/{entityName}/records/{id}/approvals/audit
 */
public class ApprovalRoutes {
    private static final Logger LOG = LoggerFactory.getLogger(ApprovalRoutes.class);

    public static void register(Router router) {

        // POST submit record for approval
        router.post("/api/tenants/{tenantId}/apps/{appId}/entities/{entityName}/records/{id}/submit", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("appId");
            String entityName = req.pathParam("entityName");
            String rowId = req.pathParam("id");

            String callerUserId = AuthService.extractUserId(req, ConfigManager.getConfig());
            if (callerUserId == null || callerUserId.isBlank()) {
                res.json(401, Map.of("error", "Unauthorized: valid session required"));
                return;
            }

            try {
                String comments = null;
                try {
                    Map<String, String> body = req.readJson(new TypeReference<>() {});
                    if (body != null) comments = body.get("comments");
                } catch (Exception ignored) {}

                Map<String, Object> result = ApprovalService.submitForApproval(tenantId, appId, entityName, rowId, callerUserId, comments);
                res.json(200, result);
            } catch (ApprovalConflictException e) {
                res.json(409, Map.of("error", e.getMessage()));
            } catch (IllegalStateException e) {
                res.json(403, Map.of("error", e.getMessage()));
            } catch (IllegalArgumentException e) {
                res.json(400, Map.of("error", e.getMessage()));
            } catch (Exception e) {
                LOG.error("[ApprovalRoutes] Failed to submit record for approval", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // POST approve record
        router.post("/api/tenants/{tenantId}/apps/{appId}/entities/{entityName}/records/{id}/approve", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("appId");
            String entityName = req.pathParam("entityName");
            String rowId = req.pathParam("id");

            String callerUserId = AuthService.extractUserId(req, ConfigManager.getConfig());
            if (callerUserId == null || callerUserId.isBlank()) {
                res.json(401, Map.of("error", "Unauthorized: valid session required"));
                return;
            }

            try {
                String comments = null;
                try {
                    Map<String, String> body = req.readJson(new TypeReference<>() {});
                    if (body != null) comments = body.get("comments");
                } catch (Exception ignored) {}

                Map<String, Object> result = ApprovalService.approveRecord(tenantId, appId, entityName, rowId, callerUserId, comments);
                res.json(200, result);
            } catch (ApprovalConflictException e) {
                res.json(409, Map.of("error", e.getMessage()));
            } catch (IllegalStateException e) {
                res.json(403, Map.of("error", e.getMessage()));
            } catch (IllegalArgumentException e) {
                res.json(400, Map.of("error", e.getMessage()));
            } catch (Exception e) {
                LOG.error("[ApprovalRoutes] Failed to approve record", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // POST reject record
        router.post("/api/tenants/{tenantId}/apps/{appId}/entities/{entityName}/records/{id}/reject", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("appId");
            String entityName = req.pathParam("entityName");
            String rowId = req.pathParam("id");

            String callerUserId = AuthService.extractUserId(req, ConfigManager.getConfig());
            if (callerUserId == null || callerUserId.isBlank()) {
                res.json(401, Map.of("error", "Unauthorized: valid session required"));
                return;
            }

            try {
                String reason = null;
                try {
                    Map<String, String> body = req.readJson(new TypeReference<>() {});
                    if (body != null) reason = body.get("reason");
                } catch (Exception ignored) {}

                Map<String, Object> result = ApprovalService.rejectRecord(tenantId, appId, entityName, rowId, callerUserId, reason);
                res.json(200, result);
            } catch (ApprovalConflictException e) {
                res.json(409, Map.of("error", e.getMessage()));
            } catch (IllegalStateException e) {
                res.json(403, Map.of("error", e.getMessage()));
            } catch (IllegalArgumentException e) {
                res.json(400, Map.of("error", e.getMessage()));
            } catch (Exception e) {
                LOG.error("[ApprovalRoutes] Failed to reject record", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // GET pending approval queue for checkers
        router.get("/api/tenants/{tenantId}/apps/{appId}/entities/{entityName}/approvals/pending", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("appId");
            String entityName = req.pathParam("entityName");

            String callerUserId = AuthService.extractUserId(req, ConfigManager.getConfig());
            if (callerUserId == null || callerUserId.isBlank()) {
                res.json(401, Map.of("error", "Unauthorized: valid session required"));
                return;
            }

            try {
                // Two-level checker chain — ?level=2 asks for the level-2 (final signoff) queue
                // instead of the default level-1 one. Anything other than "2" is treated as 1,
                // preserving today's behaviour for every entity that doesn't opt into two levels.
                int level = "2".equals(req.query("level")) ? 2 : 1;

                // C3.7: the nav badge polls, so it asks for a count only rather than
                // dragging every pending row across the wire on every tick.
                //
                // C3.9: the response carries no `records` key at all. It used to send
                // `"records": []` beside a non-zero count, so any caller reading it
                // with the normal queue semantics saw an empty queue and a badge that
                // disagreed with it. An absent key forces the caller to notice.
                if (Boolean.parseBoolean(req.query("countOnly"))) {
                    int count = ApprovalService.getPendingCount(tenantId, appId, entityName, callerUserId, level);
                    res.json(200, Map.of(
                            "tenantId", tenantId,
                            "appId", appId,
                            "entityName", entityName,
                            "level", level,
                            "count", count,
                            "countOnly", true
                    ));
                    return;
                }

                int offset = 0;
                try {
                    String offsetParam = req.query("offset");
                    if (offsetParam != null && !offsetParam.isBlank()) {
                        offset = Math.max(0, Integer.parseInt(offsetParam));
                    }
                } catch (NumberFormatException ignore) {
                    offset = 0;
                }

                List<Map<String, Object>> pending =
                        ApprovalService.getPendingQueue(tenantId, appId, entityName, callerUserId, offset, level);
                res.json(200, Map.of(
                        "tenantId", tenantId,
                        "appId", appId,
                        "entityName", entityName,
                        "level", level,
                        "count", pending.size(),
                        "offset", offset,
                        "pageSize", ApprovalService.QUEUE_PAGE_SIZE,
                        // A full page means there is very likely more behind it. The
                        // queue is FIFO, so silently stopping at the page boundary
                        // would hide the newest work indefinitely.
                        "hasMore", pending.size() == ApprovalService.QUEUE_PAGE_SIZE,
                        "records", pending
                ));
            } catch (ApprovalConflictException e) {
                res.json(409, Map.of("error", e.getMessage()));
            } catch (IllegalStateException e) {
                res.json(403, Map.of("error", e.getMessage()));
            } catch (Exception e) {
                LOG.error("[ApprovalRoutes] Failed to fetch pending queue", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // GET audit trail for a record
        router.get("/api/tenants/{tenantId}/apps/{appId}/entities/{entityName}/records/{id}/approvals/audit", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("appId");
            String entityName = req.pathParam("entityName");
            String rowId = req.pathParam("id");

            String callerUserId = AuthService.extractUserId(req, ConfigManager.getConfig());
            if (callerUserId == null || callerUserId.isBlank()) {
                res.json(401, Map.of("error", "Unauthorized: valid session required"));
                return;
            }

            try {
                List<Map<String, Object>> auditTrail = ApprovalService.getAuditTrail(tenantId, appId, entityName, rowId, callerUserId);
                res.json(200, Map.of(
                        "tenantId", tenantId,
                        "appId", appId,
                        "entityName", entityName,
                        "rowId", rowId,
                        "count", auditTrail.size(),
                        "history", auditTrail
                ));
            } catch (ApprovalConflictException e) {
                res.json(409, Map.of("error", e.getMessage()));
            } catch (IllegalStateException e) {
                res.json(403, Map.of("error", e.getMessage()));
            } catch (Exception e) {
                LOG.error("[ApprovalRoutes] Failed to fetch audit trail", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });
    }
}
