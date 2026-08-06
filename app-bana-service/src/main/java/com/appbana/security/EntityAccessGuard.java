package com.appbana.security;

import com.appbana.SchemaManager;
import com.appbana.api.Router;
import com.appbana.config.AppConfig;
import com.appbana.model.EntitySchema;
import com.appbana.service.AuthService;
import com.appbana.service.SessionService;

import java.util.Objects;

/**
 * EntityAccessGuard — Task S3.2 (Tenant Isolation Security Plan).
 *
 * Centralized guard for every {@code GenericEntityRoutes} handler (wired in by S3.4 — this class
 * only defines the check itself): no caller may read or write another app's entity data.
 *
 * <p><b>Two entry points</b>, since {@code GenericEntityRoutes} has two differently-shaped route
 * families:
 * <ul>
 *   <li>{@link #check(Router.HttpRequest, AppConfig, String, boolean)} — for the {@code /api/{entity}}
 *       family, whose only identifier is the packed {@code {tenantId}_{appId}_{entityName}} key.
 *       Resolves {@code tenantId}/{@code appId} via {@link SchemaManager#loadSchema(String)}
 *       rather than splitting the string on {@code "_"} — {@code appId} or {@code entityName}
 *       could in principle themselves contain an underscore, and the schema record's own
 *       {@code tenant_id}/{@code app_id} columns are the authoritative source this whole plan
 *       already relies on for this exact lookup (see {@code SchemaManager.getUniqueSchemaKey}).
 *       A key that resolves to no schema at all is deliberately NOT distinguished from a real
 *       entity the caller isn't authorized for (S3.4 review LOW fix) — both produce the same
 *       401/403 tail, so no caller shape can enumerate which packed keys are real.</li>
 *   <li>{@link #check(Router.HttpRequest, AppConfig, String, String, String, boolean)} — for the
 *       studio-scoped ({@code /appbana-studio/{tenantId}/apps/{appId}/{entity}}) and env-scoped
 *       ({@code /api/{tenantId}/apps/{appId}/env/{env}/{entity}}) families, which already carry
 *       {@code tenantId}/{@code appId} as separate path params — no schema lookup needed to reach
 *       an allow/deny decision.</li>
 * </ul>
 *
 * <p><b>Allow rule</b> — ONE disjunctive condition (review round 5 confirmed this guard must NOT
 * be a membership check layered behind a separate tenant-only AND gate, the way
 * {@code TenantAccessGuard} is; see plan doc "R4-1" discussion): admit if
 * <ol>
 *   <li>(i) the caller is an {@code appbana_app_members} member of {@code (tenantId, appId)} —
 *       <b>any role</b>, {@code owner}/{@code member}/{@code end-user} alike, since this guard is
 *       data-access-only, never a management check (that split lives in
 *       {@code AppAuthorization.isManagerOrSystem}, not here); <b>or</b></li>
 *   <li>(ii) the caller's session is scoped ({@code scopedAppId}, S3.1) to exactly this
 *       {@code appId} — hardened to also require the session's own {@code tenantId} to match
 *       (not in the plan doc's literal one-line spec, but {@code appId} values are not
 *       guaranteed globally unique across tenants — {@code appbana_apps}' own primary key is the
 *       composite {@code (id, tenant_id)}, exactly the lesson from the S2.10/S2.12
 *       {@code DataDrawer} dependency-array bug — so an appId-only comparison could in principle
 *       let a session scoped to one tenant's app reach a different tenant's app that happens to
 *       reuse the same id); <b>or</b></li>
 *   <li>(iii) the app is marked {@code publicRead} (S3.5 — passed in explicitly by the caller
 *       here, since no such field exists on {@code EntitySchema}/{@code AppMetadata} yet) and the
 *       request is a {@code GET}.</li>
 * </ol>
 * A valid service/admin token ({@link AuthService#extractServiceToken}/{@link AuthService#hasAdmin})
 * is a break-glass override, evaluated LAST (fall-through) — deliberately not admit-first the way
 * {@code TenantAccessGuard} is, per the plan doc's own S3.2 spec. This is safe here (no NPE/early
 * termination risk if there is no session) because each earlier rule tolerates a null session/userId
 * and simply returns false rather than throwing — {@link AppMembershipService#isMember} already
 * short-circuits on a null userId before ever issuing a query.
 *
 * <p><b>publicRead is NOT read from schema/app metadata by this class</b> — S3.5 (which adds that
 * flag) has not landed yet, and its exact shape (per-app vs. per-entity) isn't settled. Callers
 * pass the boolean explicitly; until S3.5 lands and S3.4 wires it through, callers should pass
 * {@code false}.
 */
public final class EntityAccessGuard {

    private EntityAccessGuard() {
        // Utility class
    }

    /**
     * Result of a {@link #check} call.
     *
     * @param allowed    true if the request may proceed
     * @param statusCode the HTTP status the caller should send when {@code allowed} is false
     *                   (401 or 403 — see the class Javadoc's note on why an unresolvable
     *                   packed key no longer 404s here); meaningless when {@code allowed} is true
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
     * Entry point (a) — packed-key family ({@code /api/{entity}}).
     *
     * @param req        the incoming request
     * @param cfg        app config, for admin-token comparison
     * @param entityKey  the packed {@code {tenantId}_{appId}_{entityName}} key (or a legacy,
     *                   non-tenant-scoped bare entity name — see the schema-not-found branch below)
     * @param publicRead whether this app/entity has been marked publicly readable (S3.5); pass
     *                   {@code false} until that flag exists and is threaded through by the caller
     * @return a {@link Result} describing whether the request may proceed
     */
    public static Result check(Router.HttpRequest req, AppConfig cfg, String entityKey, boolean publicRead) {
        EntitySchema schema = SchemaManager.loadSchema(entityKey);
        if (schema == null) {
            // S3.4 review LOW fix: this used to return 404 immediately, before any session/admin
            // check — which let ANY caller, unauthenticated or merely authenticated to some other
            // tenant entirely, distinguish "this packed key resolves to a real schema" (401/403,
            // further down) from "it doesn't" (404, here) — a cross-tenant existence oracle, the
            // same class of leak M6 (S3.3) closes for login. There is no (tenantId, appId) to
            // check membership/scoped-session against for a key with no schema, so rules (i)/(ii)
            // could never admit here regardless of ordering — only the caller-facts tail
            // (publicRead+GET, admin override, then the 401-vs-403 split) can apply, and running
            // that tail here makes the response byte-identical, for every caller shape, to what a
            // REAL entity the caller isn't authorized for would produce. The entity's genuine
            // existence is only revealed to callers who turn out to be actual members — same as
            // entry point (b), whose own downstream route handler is what finally 404s on a truly
            // absent entity, for a caller already inside that app.
            SessionService.SessionData session = AuthService.resolveSession(req);
            return denyOrAdmit(req, cfg, session, publicRead, entityKey);
        }

        String tenantId = (schema.getTenantId() != null && !schema.getTenantId().isBlank())
                ? schema.getTenantId()
                : "default";
        String appId = schema.getAppId();

        return check(req, cfg, tenantId, appId, schema.getName(), publicRead);
    }

    /**
     * Entry point (b) — path-segmented families (studio-scoped {@code /appbana-studio/{tenantId}
     * /apps/{appId}/{entity}} and env-scoped {@code /api/{tenantId}/apps/{appId}/env/{env}/{entity}}):
     * {@code tenantId}/{@code appId} are already separate path params, so no schema lookup is
     * needed to reach an allow/deny decision. This is also the shared core the packed-key entry
     * point above delegates to once it has resolved those two values.
     *
     * @param req        the incoming request (used to extract the service token / session credential)
     * @param cfg        app config, for admin-token comparison
     * @param tenantId   the tenant id the target app belongs to
     * @param appId      the target app id
     * @param entityName the target entity name (used only for messages — the allow-rule itself
     *                   never depends on it)
     * @param publicRead whether this app/entity has been marked publicly readable (S3.5); pass
     *                   {@code false} until that flag exists and is threaded through by the caller
     * @return a {@link Result} describing whether the request may proceed
     */
    public static Result check(Router.HttpRequest req, AppConfig cfg, String tenantId, String appId,
                                String entityName, boolean publicRead) {
        SessionService.SessionData session = AuthService.resolveSession(req);
        String userId = session != null ? session.userId() : null;

        // (i) any appbana_app_members role for this (tenantId, appId) — data-access-only, so
        // owner/member/end-user are all equally admitted here.
        if (tenantId != null && !tenantId.isBlank() && appId != null && !appId.isBlank()
                && AppMembershipService.isMember(tenantId, appId, userId)) {
            return Result.allow();
        }

        // (ii) runtime session scoped to exactly this app — hardened with a tenantId match (see
        // class Javadoc): appId alone is not guaranteed globally unique across tenants. tenantId
        // is required non-blank here too (TenantAccessGuard M1 lesson): otherwise a null path
        // tenantId and a null session.tenantId() would satisfy Objects.equals(null, null) == true
        // and leak through as an allow.
        if (session != null && tenantId != null && !tenantId.isBlank()
                && session.scopedAppId() != null && !session.scopedAppId().isBlank()
                && session.scopedAppId().equals(appId) && Objects.equals(session.tenantId(), tenantId)) {
            return Result.allow();
        }

        return denyOrAdmit(req, cfg, session, publicRead, entityName);
    }

    /**
     * Shared tail once membership (i) and scoped-session (ii) have been ruled out — or, for the
     * packed-key entry point with no resolvable schema, could never have applied at all: (iii)
     * publicRead rescues only {@code GET}s, (iv) a valid admin/service token is a break-glass
     * fall-through evaluated last, else deny with 401 (no session) or 403 (session exists, just
     * not authorized). Extracted so an unresolvable packed key and a real-but-unauthorized entity
     * produce byte-identical responses for every caller shape — see the packed-key entry point's
     * own Javadoc above for why this matters (S3.4 review LOW fix).
     *
     * @param entityLabel text used only in the 403 message body
     */
    private static Result denyOrAdmit(Router.HttpRequest req, AppConfig cfg, SessionService.SessionData session,
                                       boolean publicRead, String entityLabel) {
        // (iii) publicRead rescues only GETs.
        if (publicRead && isGetRequest(req)) {
            return Result.allow();
        }

        // (iv) break-glass admin token — fall-through, evaluated last (plan doc's own S3.2 spec).
        String serviceToken = AuthService.extractServiceToken(req);
        if (serviceToken != null && !serviceToken.isBlank() && AuthService.hasAdmin(serviceToken, cfg)) {
            return Result.allow();
        }

        // Deny — 401 (no session at all) is distinct from 403 (session exists, just not
        // authorized for this app's data), same split TenantAccessGuard uses.
        if (session == null) {
            return Result.deny(401, "Unauthorized: valid session required");
        }
        return Result.deny(403, "Forbidden: caller is not authorized for entity '" + entityLabel + "'");
    }

    private static boolean isGetRequest(Router.HttpRequest req) {
        String method = req.method();
        return method != null && "GET".equalsIgnoreCase(method);
    }
}
