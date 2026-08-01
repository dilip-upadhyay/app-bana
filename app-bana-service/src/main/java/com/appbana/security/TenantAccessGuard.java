package com.appbana.security;

import com.appbana.api.Router;
import com.appbana.config.AppConfig;
import com.appbana.service.AuthService;
import com.appbana.service.SessionService;

import java.util.Objects;

/**
 * TenantAccessGuard — Task S1.2 (Tenant Isolation Security Plan).
 *
 * Centralized guard for every {@code AppRoutes}/{@code SchemaRoutes} handler: no caller may act
 * on a tenant other than its own. Wired into route handlers by S1.3 — this class only defines
 * the check itself.
 *
 * Check order (never reordered — see docs/planning/TENANT_ISOLATION_SECURITY_PLAN.md, S1.2):
 * <ol>
 *   <li>(0) A valid service/admin token ({@link AuthService#extractServiceToken}
 *       + {@link AuthService#hasAdmin}) admits immediately, regardless of path tenant. This is
 *       the break-glass override this plan already promises elsewhere; it has no tenant of its
 *       own to compare, so it is checked before any tenant logic runs, not folded into it
 *       (review round 5, R5-1).</li>
 *   <li>(1) 401 if no session resolves at all — distinct from a wrong-tenant 403 (fixes M7).</li>
 *   <li>(2) Allow if the resolved session's {@code tenantId} matches {@code pathTenantId}.</li>
 *   <li>(3) If {@code pathAppId} is present, allow anyway when the caller is a member of that
 *       specific app despite the tenant mismatch (review round 4, R4-1). This branch ships
 *       permanently inert in S1 — {@link #isMember} always returns false until S2.6 wires
 *       {@code AppMembershipService.isMember(...)} into this exact method, rather than adding a
 *       second, parallel check.</li>
 *   <li>(4) Otherwise 403.</li>
 * </ol>
 */
public final class TenantAccessGuard {

    private TenantAccessGuard() {
        // Utility class
    }

    /**
     * Result of a {@link #requireOwnTenant} check.
     *
     * @param allowed    true if the request may proceed
     * @param statusCode the HTTP status the caller should send when {@code allowed} is false
     *                   (401 or 403); meaningless when {@code allowed} is true
     * @param message    the "error" body message to send when {@code allowed} is false; null
     *                   when {@code allowed} is true
     */
    public record Result(boolean allowed, int statusCode, String message) {
        public static Result allow() {
            return new Result(true, 200, null);
        }

        public static Result deny(int statusCode, String message) {
            return new Result(false, statusCode, message);
        }
    }

    /**
     * Enforce that the caller's own tenant matches {@code pathTenantId}.
     *
     * @param req          the incoming request (used to extract the service token / session credential)
     * @param cfg          app config, for admin-token comparison
     * @param pathTenantId the tenant id named in the request path
     * @param pathAppId    the app id named in the request path, or null/blank if the route is
     *                     tenant-wide (e.g. a bare app-list route) rather than app-scoped — the
     *                     membership exception in check (3) only ever applies when this is present
     * @return a {@link Result} describing whether the request may proceed
     */
    public static Result requireOwnTenant(Router.HttpRequest req, AppConfig cfg, String pathTenantId, String pathAppId) {
        // (0) Admit-first: a valid service/admin token bypasses the tenant check entirely.
        String serviceToken = AuthService.extractServiceToken(req);
        if (serviceToken != null && !serviceToken.isBlank() && AuthService.hasAdmin(serviceToken, cfg)) {
            return Result.allow();
        }

        // (1) No resolved session at all => 401, distinct from a wrong-tenant 403.
        SessionService.SessionData session = AuthService.resolveSession(req);
        if (session == null) {
            return Result.deny(401, "Unauthorized: valid session required");
        }

        // (2) Own tenant => allow.
        if (Objects.equals(session.tenantId(), pathTenantId)) {
            return Result.allow();
        }

        // (3) Membership exception — only for app-scoped routes; ships inert until S2.6.
        if (pathAppId != null && !pathAppId.isBlank() && isMember(pathTenantId, pathAppId, session.userId())) {
            return Result.allow();
        }

        // (4) Otherwise: tenant mismatch.
        return Result.deny(403, "Forbidden: caller's tenant does not match the requested app's tenant");
    }

    /**
     * S1 membership-exception hook (review round 4, R4-1) — permanently inert until S2.6, which
     * replaces this body by wiring {@code AppMembershipService.isMember(appTenantId, appId, userId)}
     * in directly, rather than layering a second, parallel check elsewhere. Do not implement this
     * ahead of S2's membership table (S2.1/S2.2) — there is nothing real to consult yet.
     */
    private static boolean isMember(String appTenantId, String appId, String userId) {
        return false;
    }
}
