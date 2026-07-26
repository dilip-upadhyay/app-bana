package com.appbana.server.routes;

import com.appbana.api.Router;
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
                List<Map<String, Object>> pending = ApprovalService.getPendingQueue(tenantId, appId, entityName, callerUserId);
                res.json(200, Map.of(
                        "tenantId", tenantId,
                        "appId", appId,
                        "entityName", entityName,
                        "count", pending.size(),
                        "records", pending
                ));
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
            } catch (IllegalStateException e) {
                res.json(403, Map.of("error", e.getMessage()));
            } catch (Exception e) {
                LOG.error("[ApprovalRoutes] Failed to fetch audit trail", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });
    }
}
