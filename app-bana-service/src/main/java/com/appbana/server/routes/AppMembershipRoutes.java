package com.appbana.server.routes;

import com.appbana.AppManager;
import com.appbana.api.Router;
import com.appbana.model.AppMetadata;
import com.appbana.security.AppAuthorization;
import com.appbana.security.AppMembershipService;
import com.appbana.security.TenantAccessGuard;
import com.appbana.service.AuthService;
import com.appbana.service.SessionService;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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

        router.get("/api/users/me/apps", AppMembershipRoutes::handleListMyApps);
    }

    /**
     * Owner-only gate shared by all three handlers below, applied on top of (never instead of)
     * {@link TenantAccessGuard#requireOwnTenant}. Mirrors {@code AppRoutes.denyIfNotManager}'s
     * shape but calls the strict {@link AppAuthorization#isAppOwnerOrSystem} rather than
     * {@code isManagerOrSystem} — see the class Javadoc for why membership management itself must
     * stay owner-only rather than owner-or-member.
     *
     * <p>Shares {@code denyIfNotManager}'s admin-token asymmetry (round-44 review nit, on record,
     * not fixed): no service-token short-circuit here either, so an admin-token caller with no
     * {@code X-User-Id} is denied (fail-closed, inert under the shipped {@code adminToken: null}
     * config) — see that method's own Javadoc in {@code AppRoutes.java} for the full rationale.
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

        Map<String, String> body;
        try {
            body = req.readJson(new TypeReference<>() {});
        } catch (RuntimeException e) {
            res.json(400, Map.of("error", "Malformed JSON body"));
            return;
        }

        try {
            // Body-supplied tenantId/appId are always ignored — path values are authoritative,
            // same reasoning as RoleRoutes.handlePostRole (C1.9).
            String targetUserId = body.get("userId");
            String roleStr = body.get("role");
            if (targetUserId == null || targetUserId.isBlank() || roleStr == null || roleStr.isBlank()) {
                res.json(400, Map.of("error", "userId and role required"));
                return;
            }

            AppMembershipService.Role role = AppMembershipService.Role.fromValue(roleStr);

            // S2.7 review round 44 (LOW): refuse a grant that would demote the app's only owner —
            // it would freeze membership management (this route requires an existing owner) and,
            // via isManagerOrSystem, also strip the actor's own AppRoutes management rights.
            if (role != AppMembershipService.Role.OWNER
                    && AppMembershipService.isSoleOwner(tenantId, appId, targetUserId)) {
                res.json(409, Map.of("error", "Conflict: cannot demote the app's only owner - grant another owner first"));
                return;
            }

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

        // S2.7 review round 44 (LOW): refuse to revoke the app's only owner — same rationale as
        // the demote-guard in handleGrant above.
        if (AppMembershipService.isSoleOwner(tenantId, appId, targetUserId)) {
            res.json(409, Map.of("error", "Conflict: cannot revoke the app's only owner - grant another owner first"));
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

    /**
     * Task S2.10 — {@code GET /api/users/me/apps}: the one deliberately non-tenant-scoped
     * app-listing route in this plan. Returns the union of:
     * <ol>
     *   <li>every app in the caller's OWN tenant, unfiltered by membership — same semantics as
     *       {@code GET /appbana-studio/{tenantId}/apps} ({@link AppManager#listApps}); and</li>
     *   <li>every app the caller holds a membership grant on in a DIFFERENT tenant
     *       ({@link AppMembershipService#listAppsForUser}), each tagged with that grant's
     *       {@code role} so the caller can be told apart from an owner/member of their own
     *       app.</li>
     * </ol>
     *
     * <p>Consumed by the Studio app switcher (S2.8) — an app switcher that shows only the
     * caller's own tenant can never surface an app they were granted membership on elsewhere,
     * which is the entire point of {@code AppMembershipService}'s cross-tenant {@code
     * listAppsForUser} lookup existing at all.
     *
     * <p><b>Why {@code ownTenantId} comes from the verified session, never a client-supplied
     * query param</b>: unlike {@code UserRoutes}'s {@code GET /api/users/me}, which only ever
     * reports the caller's own role on one named app (safe to let the caller pick which app to
     * ask about — it can never leak a second app's data), this route's own-tenant half calls
     * {@code AppManager.listApps(tenantId)} — an <em>unfiltered dump of every app row in that
     * tenant</em>, regardless of membership. Trusting a client-supplied tenant id here would let
     * any authenticated caller enumerate an arbitrary tenant's entire app roster just by naming
     * it, with no membership check at all — exactly the cross-tenant listing hole
     * {@code TenantAccessGuard} exists to close everywhere else. {@link
     * AuthService#resolveSession} returns the server-verified {@code SessionData}, whose {@code
     * tenantId} is the only tenant this route will ever list unfiltered.
     *
     * <p>Fails closed (401) for a bare service/admin-token caller with no session — same
     * fail-closed posture as this file's other three routes (see {@link #denyIfNotOwner}'s
     * Javadoc for the full rationale). There is no "own tenant" concept for a principal with no
     * session to derive one from, and this route has no path tenant/app to fall back on either.
     */
    private static void handleListMyApps(Router.HttpRequest req, Router.HttpResponse res) {
        SessionService.SessionData session = AuthService.resolveSession(req);
        if (session == null || session.userId() == null || session.userId().isBlank()) {
            res.json(401, Map.of("error", "Unauthorized: valid session required"));
            return;
        }

        String callerUserId = session.userId();
        String ownTenantId = session.tenantId() != null ? session.tenantId() : "default";

        try {
            List<Map<String, Object>> result = new ArrayList<>();

            List<Map<String, Object>> ownApps = AppManager.listApps(ownTenantId);
            for (Map<String, Object> app : ownApps) {
                Map<String, Object> tagged = new LinkedHashMap<>(app);
                tagged.put("tenantId", ownTenantId);
                result.add(tagged);
            }

            List<AppMembershipService.MembershipGrant> grants = AppMembershipService.listAppsForUser(callerUserId);
            for (AppMembershipService.MembershipGrant grant : grants) {
                // A same-tenant grant is already covered, unfiltered, by the own-tenant list
                // above -- AppManager.listApps() doesn't consult membership at all, so including
                // it again here would just duplicate an entry already present.
                if (grant.tenantId().equals(ownTenantId)) {
                    continue;
                }

                AppMetadata app = AppManager.getApp(grant.tenantId(), grant.appId());
                if (app == null) {
                    // Orphaned grant: the app was deleted after the membership row was written.
                    // Skip it rather than fabricate an entry with no backing metadata.
                    continue;
                }

                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("id", app.getId());
                summary.put("name", app.getName());
                summary.put("tenantId", grant.tenantId());
                summary.put("description", app.getDescription());
                summary.put("version", app.getVersion());
                summary.put("created", app.getCreated());
                summary.put("updated", app.getUpdated());
                summary.put("pageCount", app.getPages() != null ? app.getPages().size() : 0);
                summary.put("role", grant.role().getValue());
                result.add(summary);
            }

            res.json(200, Map.of("apps", result));
        } catch (Exception e) {
            LOG.error("[AppMembershipRoutes] Failed to list apps for user '{}'", callerUserId, e);
            res.json(500, Map.of("error", e.getMessage()));
        }
    }
}
