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
 *   <li>{@link #check(Router.HttpRequest, AppConfig, String)} — for the {@code /api/{entity}}
 *       family, whose only identifier is the packed {@code {tenantId}_{appId}_{entityName}} key.
 *       Resolves {@code tenantId}/{@code appId} via {@link SchemaManager#loadSchema(String)}
 *       rather than splitting the string on {@code "_"} — {@code appId} or {@code entityName}
 *       could in principle themselves contain an underscore, and the schema record's own
 *       {@code tenant_id}/{@code app_id} columns are the authoritative source this whole plan
 *       already relies on for this exact lookup (see {@code SchemaManager.getUniqueSchemaKey}).
 *       A key that resolves to no schema at all is deliberately NOT distinguished from a real
 *       entity the caller isn't authorized for (S3.4 review LOW fix, hardened by the round-65
 *       review MEDIUM fix) — both produce the same 401/403 tail with the same constant message
 *       text, so no caller shape (unauthenticated, or authenticated to any tenant) can enumerate
 *       which packed keys are real by inspecting either the status code or the response body.
 *       Since this entry point already loads the schema, it reads {@code publicRead} (S3.5)
 *       straight off it — no separate lookup.</li>
 *   <li>{@link #check(Router.HttpRequest, AppConfig, String, String, String)} — for the
 *       studio-scoped ({@code /appbana-studio/{tenantId}/apps/{appId}/{entity}}) and env-scoped
 *       ({@code /api/{tenantId}/apps/{appId}/env/{env}/{entity}}) families, which already carry
 *       {@code tenantId}/{@code appId} as separate path params, so rules (i)/(ii) below need no
 *       schema lookup. Rule (iii) does, so this entry point resolves {@code publicRead} (S3.5)
 *       with its own {@code SchemaManager.loadSchema(appId, entityName, tenantId)} call — but
 *       only once rules (i)/(ii) have both failed, so a real member or scoped session (the
 *       common case for these routes) never pays for it.</li>
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
 *   <li>(iii) the entity's schema has {@code publicRead == true} (S3.5,
 *       {@link EntitySchema#isPublicRead()}) and the request is a {@code GET}.</li>
 * </ol>
 * A valid service/admin token ({@link AuthService#extractServiceToken}/{@link AuthService#hasAdmin})
 * is a break-glass override, evaluated LAST (fall-through) — deliberately not admit-first the way
 * {@code TenantAccessGuard} is, per the plan doc's own S3.2 spec. This is safe here (no NPE/early
 * termination risk if there is no session) because each earlier rule tolerates a null session/userId
 * and simply returns false rather than throwing — {@link AppMembershipService#isMember} already
 * short-circuits on a null userId before ever issuing a query.
 *
 * <p><b>publicRead resolution never changes the shape of the 401/403 tail</b> (S3.5): an entity
 * with {@code publicRead == false} — or one that doesn't resolve to a schema at all — falls
 * through to the exact same {@link #denyOrAdmit} tail either way, same status code and same
 * constant message. Only {@code publicRead == true} on a {@code GET} short-circuits to an allow,
 * which is the feature working as intended, not a regression of the existence-oracle closure
 * above: a caller can learn "this entity exists and is public", but never distinguish "exists but
 * private" from "doesn't exist" — both still 401/403 with identical bodies.
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
     * @param req       the incoming request
     * @param cfg       app config, for admin-token comparison
     * @param entityKey the packed {@code {tenantId}_{appId}_{entityName}} key (or a legacy,
     *                  non-tenant-scoped bare entity name — see the schema-not-found branch below)
     * @return a {@link Result} describing whether the request may proceed
     */
    public static Result check(Router.HttpRequest req, AppConfig cfg, String entityKey) {
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
            // that tail here makes the response byte-identical — status code AND body, since the
            // round-65 fix below made the 403 message a caller-invariant constant — for every
            // caller shape, to what a REAL entity the caller isn't authorized for would produce.
            // publicRead is false here too — there is no schema, so nothing to be public about.
            // The entity's genuine existence is only revealed to callers who turn out to be actual
            // members — same as entry point (b), whose own downstream route handler is what
            // finally 404s on a truly absent entity, for a caller already inside that app.
            SessionService.SessionData session = AuthService.resolveSession(req);
            return denyOrAdmit(req, cfg, session, false);
        }

        String tenantId = (schema.getTenantId() != null && !schema.getTenantId().isBlank())
                ? schema.getTenantId()
                : "default";
        String appId = schema.getAppId();
        SessionService.SessionData session = AuthService.resolveSession(req);

        Result admitted = admitByMembershipOrScope(tenantId, appId, session);
        if (admitted != null) {
            return admitted;
        }
        // S3.5 — this entry point already has the schema in hand, so publicRead is read directly
        // off it; no extra lookup (entry point (b) below has to do its own, lazily, since it's
        // never handed a schema).
        return denyOrAdmit(req, cfg, session, schema.isPublicRead());
    }

    /**
     * Entry point (b) — path-segmented families (studio-scoped {@code /appbana-studio/{tenantId}
     * /apps/{appId}/{entity}} and env-scoped {@code /api/{tenantId}/apps/{appId}/env/{env}/{entity}}):
     * {@code tenantId}/{@code appId} are already separate path params, so rules (i)/(ii) need no
     * schema lookup to reach an allow decision.
     *
     * @param req        the incoming request (used to extract the service token / session credential)
     * @param cfg        app config, for admin-token comparison
     * @param tenantId   the tenant id the target app belongs to
     * @param appId      the target app id
     * @param entityName the target entity name; not read by rules (i)/(ii), and never echoed in
     *                   any response body (the 403 message stays the round-65 caller-invariant
     *                   constant — see {@link #denyOrAdmit} Javadoc). Since S3.5, IS used —
     *                   server-side only, and only once rules (i)/(ii) have both failed — to look
     *                   up the entity's schema and read {@code publicRead} off it.
     * @return a {@link Result} describing whether the request may proceed
     */
    public static Result check(Router.HttpRequest req, AppConfig cfg, String tenantId, String appId,
                                String entityName) {
        SessionService.SessionData session = AuthService.resolveSession(req);

        Result admitted = admitByMembershipOrScope(tenantId, appId, session);
        if (admitted != null) {
            return admitted;
        }

        // S3.5 — only reached once rules (i)/(ii) have both failed, so a real member or scoped
        // session (the common case for these routes) never pays for this lookup.
        boolean publicRead = resolvePublicRead(tenantId, appId, entityName);
        return denyOrAdmit(req, cfg, session, publicRead);
    }

    /**
     * Rules (i) membership and (ii) scoped-session, shared by both entry points above. Returns
     * {@code null} (not a {@link Result}) when neither rule admits, so callers can tell "denied"
     * apart from "not yet decided — fall through to {@link #denyOrAdmit}". Both entry points still
     * resolve {@code publicRead} differently before that fall-through: entry point (a) already has
     * a schema in hand; entry point (b) has to look one up.
     */
    private static Result admitByMembershipOrScope(String tenantId, String appId,
                                                     SessionService.SessionData session) {
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

        return null;
    }

    /**
     * S3.5 — resolves {@code publicRead} for entry point (b), which (unlike entry point (a)) is
     * never handed a schema. Returns {@code false} without querying at all when {@code appId} or
     * {@code entityName} is missing — mirrors rule (i)'s own blank-guard above, and means a
     * malformed/absent identifier can never accidentally resolve to some unrelated schema via a
     * bare-name fallback lookup. A blank {@code tenantId} is left to
     * {@link SchemaManager#loadSchema(String, String, String)}'s own "default" fallback, same as
     * every other caller of that overload.
     */
    private static boolean resolvePublicRead(String tenantId, String appId, String entityName) {
        if (appId == null || appId.isBlank() || entityName == null || entityName.isBlank()) {
            return false;
        }
        EntitySchema schema = SchemaManager.loadSchema(appId, entityName, tenantId);
        return schema != null && schema.isPublicRead();
    }

    /**
     * Shared tail once membership (i) and scoped-session (ii) have been ruled out — or, for the
     * packed-key entry point with no resolvable schema, could never have applied at all: (iii)
     * publicRead rescues only {@code GET}s, (iv) a valid admin/service token is a break-glass
     * fall-through evaluated last, else deny with 401 (no session) or 403 (session exists, just
     * not authorized). Extracted so an unresolvable packed key and a real-but-unauthorized entity
     * produce byte-identical responses for every caller shape, status code AND body — see the
     * packed-key entry point's own Javadoc above for why this matters (S3.4 review LOW fix).
     * The 403 message is deliberately a constant with no entity-specific text (round-65 review
     * MEDIUM fix): an earlier version echoed the caller-supplied label into the message, but since
     * a real entity's label ({@code schema.getName()}, short) and an unresolvable packed key's
     * label (the full raw key, always longer) are never the same length, that still let an
     * authenticated caller distinguish real from fake keys by inspecting the response body even
     * after the status codes were unified — the same class of leak, just moved from the status
     * code into the body. {@code TenantAccessGuard}'s own 403 ("caller's tenant does not match the
     * requested app's tenant") is already worded this way for the same reason; this follows suit.
     */
    private static Result denyOrAdmit(Router.HttpRequest req, AppConfig cfg, SessionService.SessionData session,
                                       boolean publicRead) {
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
        // authorized for this app's data), same split TenantAccessGuard uses. Neither message
        // names the entity/key involved (round-65 review MEDIUM fix) — see this method's Javadoc.
        if (session == null) {
            return Result.deny(401, "Unauthorized: valid session required");
        }
        return Result.deny(403, "Forbidden: caller is not authorized for this entity");
    }

    private static boolean isGetRequest(Router.HttpRequest req) {
        String method = req.method();
        return method != null && "GET".equalsIgnoreCase(method);
    }
}
