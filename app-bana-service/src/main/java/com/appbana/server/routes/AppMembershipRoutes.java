package com.appbana.server.routes;

import com.appbana.api.Router;
import com.appbana.security.AppAuthorization;
import com.appbana.security.AppMembershipService;
import com.appbana.security.TenantAccessGuard;
import com.appbana.service.AuthService;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * AppMembershipRoutes — Task S2.7 (Tenant Isolation Security Plan).
 *
 * <pre>
 * GET    /api/tenants/{tenantId}/apps/{appId}/members                   -> list members
 * POST   /api/tenants/{tenantId}/apps/{appId}/members (body: {userId, role}) -> grant/update a role
 * DELETE /api/tenants/{tenantId}/apps/{appId}/members?userId=X          -> revoke
 * </pre>
 *
 * <p>Deliberately {@code owner}-only on all three verbs, per the S2.7 spec — unlike {@code
 * AppRoutes}'s management gate (S2.6, {@link AppAuthorization#isManagerOrSystem}, which admits
 * {@code owner} or {@code member}), granting/revoking/listing membership itself is gated with the
 * strict {@link AppAuthorization#isAppOwnerOrSystem}. A {@code member} can manage the app's data
 * and configuration but must never be able to add or remove other members — that would let a
 * non-owner grant themselves (or an accomplice) {@code owner} and escalate.
 *
 * <p>{@code POST} accepts all three {@link AppMembershipService.Role} values on grant, including
 * {@code end-user} — that role exists specifically so an owner can hand out data-access-only
 * grants (S2.6) without also granting management rights.
 */
public class AppMembershipRoutes {
    private static final Logger LOG = LoggerFactory.getLogger(AppMembershipRoutes.class);

    private AppMembershipRoutes() {
    }

    public static void register(Router router) {
        router.get("/api/tenants/{tenantId}/apps/{appId}/members", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("appId");
            handleListMembers(req, res, tenantId, appId);
        });

        router.post("/api/tenants/{tenantId}/apps/{appId}/members", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("appId");
            handleGrant(req, res, tenantId, appId);
        });

        router.delete("/api/tenants/{tenantId}/apps/{appId}/members", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            String appId = req.pathParam("appId");
            handleRevoke(req, res, tenantId, appId);
        });
    }

    /**
     * Owner-only gate shared by all three handlers below, applied on top of (never instead of)
     * {@link TenantAccessGuard#requireOwnTenant}. Mirrors {@code AppRoutes.denyIfNotManager}'s
     * shape but calls the strict {@link AppAuthorization#isAppOwnerOrSystem} rather than
     * {@code isManagerOrSystem} — see the class Javadoc for why membership management itself must
     * stay owner-only rather than owner-or-member.
     */
    private static boolean denyIfNotOwner(Router.HttpRequest req, Router.HttpResponse res, String tenantId, String appId) {
        String callerUserId = AuthService.extractUserId(req, com.appbana.config.ConfigManager.getConfig());
        if (!AppAuthorization.isAppOwnerOrSystem(tenantId, appId, callerUserId)) {
            res.json(403, Map.of("error", "Forbidden: only the app owner can manage membership"));
            return true;
        }
        return false;
    }

    private static void handleListMembers(Router.HttpRequest req, Router.HttpResponse res, String tenantId, String appId) {
        if (tenantId == null || tenantId.isBlank() || appId == null || appId.isBlank()) {
            res.json(400, Map.of("error", "tenantId and appId required"));
            return;
        }

        TenantAccessGuard.Result access = TenantAccessGuard.requireOwnTenant(req,
                com.appbana.config.ConfigManager.getConfig(), tenantId, appId);
        if (!access.allowed()) {
            res.json(access.statusCode(), Map.of("error", access.message()));
            return;
        }
        if (denyIfNotOwner(req, res, tenantId, appId)) return;

        try {
            List<AppMembershipService.Member> members = AppMembershipService.listMembers(tenantId, appId);
            res.json(200, Map.of("members", members.stream()
                    .map(m -> Map.of(
                            "userId", m.userId(),
                            "role", m.role().getValue(),
                            "grantedBy", m.grantedBy() == null ? "" : m.grantedBy()))
                    .toList()));
        } catch (Exception e) {
            LOG.error("[AppMembershipRoutes] Failed to list members for {}/{}", tenantId, appId, e);
            res.json(500, Map.of("error", e.getMessage()));
        }
    }

    private static void handleGrant(Router.HttpRequest req, Router.HttpResponse res, String tenantId, String appId) {
        if (tenantId == null || tenantId.isBlank() || appId == null || appId.isBlank()) {
            res.json(400, Map.of("error", "tenantId and appId required"));
            return;
        }

        TenantAccessGuard.Result access = TenantAccessGuard.requireOwnTenant(req,
                com.appbana.config.ConfigManager.getConfig(), tenantId, appId);
        if (!access.allowed()) {
            res.json(access.statusCode(), Map.of("error", access.message()));
            return;
        }
        if (denyIfNotOwner(req, res, tenantId, appId)) return;

        try {
            Map<String, String> body = req.readJson(new TypeReference<>() {});
            // Body-supplied tenantId/appId are always ignored — path values are authoritative,
            // same reasoning as RoleRoutes.handlePostRole (C1.9).
            String targetUserId = body.get("userId");
            String roleStr = body.get("role");
            if (targetUserId == null || targetUserId.isBlank() || roleStr == null || roleStr.isBlank()) {
                res.json(400, Map.of("error", "userId and role required"));
                return;
            }

            AppMembershipService.Role role = AppMembershipService.Role.fromValue(roleStr);
            String callerUserId = AuthService.extractUserId(req, com.appbana.config.ConfigManager.getConfig());
            AppMembershipService.grant(tenantId, appId, targetUserId, role, callerUserId);

            res.json(200, Map.of(
                    "status", "granted",
                    "tenantId", tenantId,
                    "appId", appId,
                    "userId", targetUserId,
                    "role", role.getValue()));
        } catch (IllegalArgumentException e) {
            res.json(400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            LOG.error("[AppMembershipRoutes] Failed to grant membership for {}/{}", tenantId, appId, e);
            res.json(500, Map.of("error", e.getMessage()));
        }
    }

    private static void handleRevoke(Router.HttpRequest req, Router.HttpResponse res, String tenantId, String appId) {
        if (tenantId == null || tenantId.isBlank() || appId == null || appId.isBlank()) {
            res.json(400, Map.of("error", "tenantId and appId required"));
            return;
        }

        TenantAccessGuard.Result access = TenantAccessGuard.requireOwnTenant(req,
                com.appbana.config.ConfigManager.getConfig(), tenantId, appId);
        if (!access.allowed()) {
            res.json(access.statusCode(), Map.of("error", access.message()));
            return;
        }
        if (denyIfNotOwner(req, res, tenantId, appId)) return;

        String targetUserId = req.query("userId");
        if (targetUserId == null || targetUserId.isBlank()) {
            res.json(400, Map.of("error", "userId query parameter required"));
            return;
        }

        try {
            AppMembershipService.revoke(tenantId, appId, targetUserId);
            res.json(200, Map.of("status", "revoked", "tenantId", tenantId, "appId", appId, "userId", targetUserId));
        } catch (Exception e) {
            LOG.error("[AppMembershipRoutes] Failed to revoke membership for {}/{}", tenantId, appId, e);
            res.json(500, Map.of("error", e.getMessage()));
        }
    }
}
