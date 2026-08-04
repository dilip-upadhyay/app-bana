package com.appbana.server.routes;

import com.appbana.ApiServer;
import com.appbana.JdbcManager;
import com.appbana.security.AppMembershipService;
import com.appbana.service.SessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CrossTenantMembershipAllowsAccessTest — S2.9 (docs/planning/TENANT_ISOLATION_IMPLEMENTATION_TASKS.md).
 *
 * <p>The FULL cross-tenant role × route matrix, explicitly deferred to this class by both {@link
 * CrossTenantAppAccessTest}'s own Javadoc ("the FULL formalized suite (every route × every role)
 * is still {@code CrossTenantMembershipAllowsAccessTest}, owned by S2.9") and S2.6 round-41's
 * review note (the {@code FileRoutes.handleUpload} cross-tenant-member smoke test "is added to
 * S2.9's matrix ... since S2.9 owns the full role × route test matrix").
 *
 * <p>{@code CrossTenantAppAccessTest} proved the negative (no membership ⇒ denied) across 18
 * routes, and a 2-route smoke test that membership admits a cross-tenant caller at all. This class:
 * <ul>
 *   <li>extends the no-membership baseline to all 20 {@code AppRoutes.java} handlers (the 18 plus
 *       the 2 {@code .../full} read-only routes S1.9 covered only for the simpler tenant-mismatch
 *       case, not the membership-aware gate);</li>
 *   <li>proves a cross-tenant {@code end-user} is admitted to every app-scoped read-only route but
 *       denied every management route with exactly 403;</li>
 *   <li>proves a cross-tenant {@code member} is admitted to every one of the 19 app-scoped routes;</li>
 *   <li>proves a cross-tenant {@code owner} — granted via membership only, deliberately NOT the
 *       app's {@code author} column — is admitted to every one of the 19 app-scoped routes, at
 *       full-route scope (the existing smoke test only ever checked 2 routes for this);</li>
 *   <li>proves the one deliberate exception — the bare tenant-wide {@code GET
 *       /appbana-studio/{tenantId}/apps} list, which has no {@code {appId}} path segment — denies
 *       EVERY cross-tenant caller regardless of membership role, because {@code AppRoutes.java}
 *       calls {@code TenantAccessGuard.requireOwnTenant} there with a {@code null} {@code
 *       pathAppId}, so the S2.6 membership branch structurally cannot fire (found while writing
 *       this class: an earlier draft asserted this route would be membership-admitted like the
 *       other 19, and failed against the real server with a 403 — the route's own source comment
 *       confirms this is deliberate, not a bug);</li>
 *   <li>adds the {@code FileRoutes.handleUpload} cross-tenant-member smoke test flagged by S2.6
 *       round-41.</li>
 * </ul>
 *
 * <p>Route census and route/role gating rules are shared with {@link AppRoutesMembershipTest}
 * (same-tenant matrix) — see that class's Javadoc for the full reasoning; this class only adds the
 * cross-tenant dimension.
 */
public class CrossTenantMembershipAllowsAccessTest {

    private static final int PORT = 18102;
    private static final String BASE_URL = "http://localhost:" + PORT;
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String VICTIM_TENANT = "s29-xtenant-victim";
    private static final String ATTACKER_TENANT = "s29-xtenant-attacker";
    private static final String APP_ID = "s29-xtenant-app";
    private static final String PAGE_ID = "s29-xtenant-page";

    private String victimOwnerSession;

    @BeforeAll
    public static void startServer() throws Exception {
        ApiServer.startJdk(PORT);
    }

    @BeforeEach
    public void setUp() throws Exception {
        try (Connection c = JdbcManager.getConnection("default");
             Statement s = c.createStatement()) {
            s.execute("DELETE FROM appbana_apps WHERE tenant_id IN ('" + VICTIM_TENANT + "', '" + ATTACKER_TENANT + "')");
            s.execute("DELETE FROM appbana_app_members WHERE tenant_id IN ('" + VICTIM_TENANT + "', '" + ATTACKER_TENANT + "')");
        }

        victimOwnerSession = SessionService.createSession("s29xt_owner", VICTIM_TENANT).sessionId();

        HttpResponse<String> appRes = send("POST", "/appbana-studio/" + VICTIM_TENANT + "/apps", victimOwnerSession,
                MAPPER.writeValueAsString(Map.of("id", APP_ID, "name", "S2.9 cross-tenant fixture", "version", "1.0.0")));
        assertEquals(201, appRes.statusCode(), "Test fixture setup: app creation must succeed: " + appRes.body());

        HttpResponse<String> pageRes = send("PUT",
                "/appbana-studio/" + VICTIM_TENANT + "/apps/" + APP_ID + "/pages/" + PAGE_ID, victimOwnerSession,
                MAPPER.writeValueAsString(Map.of("title", "S2.9 cross-tenant fixture page")));
        assertEquals(200, pageRes.statusCode(), "Test fixture setup: page creation must succeed: " + pageRes.body());
    }

    private String crossTenantSession(String userId) {
        return SessionService.createSession(userId, ATTACKER_TENANT).sessionId();
    }

    private HttpResponse<String> send(String method, String path, String sessionOrNull, String bodyOrNull) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder().uri(URI.create(BASE_URL + path));
        if (sessionOrNull != null) {
            b.header("X-Session-Token", sessionOrNull);
        }
        switch (method) {
            case "GET" -> b.GET();
            case "DELETE" -> b.DELETE();
            default -> b.method(method, HttpRequest.BodyPublishers.ofString(bodyOrNull != null ? bodyOrNull : "{}"))
                    .header("Content-Type", "application/json");
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private record RouteCase(String method, String path) {
        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    /** The 12 {@code denyIfNotManager}-gated handlers — same census as {@link AppRoutesMembershipTest}. */
    private List<RouteCase> managementRoutes() {
        String t = VICTIM_TENANT, a = APP_ID, p = PAGE_ID;
        return List.of(
                new RouteCase("POST", "/api/" + t + "/apps/" + a + "/publish?env=DEV"),
                new RouteCase("PUT", "/api/" + t + "/apps/" + a + "/deploy/local"),
                new RouteCase("POST", "/api/" + t + "/apps/" + a + "/commits"),
                new RouteCase("POST", "/api/" + t + "/apps/" + a + "/commits/rollback"),
                new RouteCase("POST", "/api/" + t + "/apps/" + a + "/versions"),
                new RouteCase("POST", "/api/" + t + "/apps/" + a + "/deploy/v1"),
                new RouteCase("POST", "/api/" + t + "/apps/" + a + "/restore-schemas"),
                new RouteCase("PUT", "/appbana-studio/" + t + "/apps/" + a + "/workflow"),
                new RouteCase("PUT", "/appbana-studio/" + t + "/apps/" + a + "/pages/" + p),
                new RouteCase("PUT", "/appbana-studio/" + t + "/apps/" + a),
                new RouteCase("DELETE", "/appbana-studio/" + t + "/apps/" + a + "/pages/" + p),
                new RouteCase("DELETE", "/appbana-studio/" + t + "/apps/" + a)
        );
    }

    /**
     * The 7 APP-SCOPED read-only handlers (a real {@code {appId}} path segment). Deliberately
     * excludes the bare tenant-wide {@code GET /appbana-studio/{tenantId}/apps} list — see {@link
     * #bareTenantListRoute()}'s Javadoc for why that one route is NOT part of this membership-aware
     * census. Otherwise the same census as {@link AppRoutesMembershipTest}, including the 2 {@code
     * .../full} routes {@link CrossTenantAppAccessTest} deliberately excluded (S1.9 covers their
     * plain tenant-mismatch case only, not the membership-aware gate this class targets).
     */
    private List<RouteCase> appScopedReadOnlyRoutes() {
        String t = VICTIM_TENANT, a = APP_ID, p = PAGE_ID;
        return List.of(
                new RouteCase("GET", "/appbana-studio/" + t + "/apps/" + a),
                new RouteCase("GET", "/api/" + t + "/apps/" + a + "/full"),
                new RouteCase("GET", "/api/" + t + "/apps/" + a + "/env/DEV/full"),
                new RouteCase("GET", "/api/" + t + "/apps/" + a + "/versions"),
                new RouteCase("GET", "/api/" + t + "/apps/" + a + "/pipeline"),
                new RouteCase("GET", "/appbana-studio/" + t + "/apps/" + a + "/workflow"),
                new RouteCase("GET", "/appbana-studio/" + t + "/apps/" + a + "/pages/" + p)
        );
    }

    /**
     * {@code GET /appbana-studio/{tenantId}/apps} — the ONE route in the 20-route census with no
     * {@code {appId}} path segment. Confirmed by direct read of {@code AppRoutes.java} (its own
     * comment): "Bare tenant-wide list: no pathAppId, so the S2.6 membership exception never
     * applies here — own-tenant only, deliberately stricter than the app-scoped routes below."
     * Structurally incapable of per-app membership admission (there is no specific app to check
     * membership against), so it is tested separately from {@link #appScopedReadOnlyRoutes()}
     * rather than lumped into the same "admitted via membership" assertions.
     */
    private RouteCase bareTenantListRoute() {
        return new RouteCase("GET", "/appbana-studio/" + VICTIM_TENANT + "/apps");
    }

    private List<RouteCase> allAppScopedRoutes() {
        return java.util.stream.Stream.concat(managementRoutes().stream(), appScopedReadOnlyRoutes().stream()).toList();
    }

    /** Every route in the 20-route census, app-scoped or not — used only for the no-membership baseline. */
    private List<RouteCase> everyRouteIncludingBareList() {
        return java.util.stream.Stream.concat(allAppScopedRoutes().stream(), java.util.stream.Stream.of(bareTenantListRoute())).toList();
    }

    @Test
    public void crossTenantSessionWithNoMembershipIsDeniedEveryRoute() throws Exception {
        String noMembershipSession = crossTenantSession("s29xt_no_membership");
        for (RouteCase rc : everyRouteIncludingBareList()) {
            HttpResponse<String> res = send(rc.method(), rc.path(), noMembershipSession, null);
            assertEquals(403, res.statusCode(),
                    () -> rc + " must reject a cross-tenant session with no membership row with 403, got "
                            + res.statusCode() + ": " + res.body());
        }
    }

    @Test
    public void crossTenantEndUserIsAdmittedToReadOnlyRoutesButDeniedManagementRoutes() throws Exception {
        String endUserSession = crossTenantSession("s29xt_enduser");
        AppMembershipService.grant(VICTIM_TENANT, APP_ID, "s29xt_enduser", AppMembershipService.Role.END_USER, "test-setup");

        for (RouteCase rc : appScopedReadOnlyRoutes()) {
            HttpResponse<String> res = send(rc.method(), rc.path(), endUserSession, null);
            assertNotEquals(401, res.statusCode(), () -> rc + " must not 401 a cross-tenant end-user on a read-only route");
            assertNotEquals(403, res.statusCode(), () -> rc + " must not 403 a cross-tenant end-user on a read-only route: " + res.body());
        }
        for (RouteCase rc : managementRoutes()) {
            HttpResponse<String> res = send(rc.method(), rc.path(), endUserSession, null);
            assertEquals(403, res.statusCode(),
                    () -> rc + " must deny a cross-tenant end-user's management attempt with exactly 403, got "
                            + res.statusCode() + ": " + res.body());
        }
    }

    @Test
    public void crossTenantMemberIsAdmittedToEveryAppScopedRoute() throws Exception {
        String memberSession = crossTenantSession("s29xt_member");
        AppMembershipService.grant(VICTIM_TENANT, APP_ID, "s29xt_member", AppMembershipService.Role.MEMBER, "test-setup");

        for (RouteCase rc : allAppScopedRoutes()) {
            HttpResponse<String> res = send(rc.method(), rc.path(), memberSession, null);
            assertNotEquals(401, res.statusCode(), () -> rc + " must not 401 a cross-tenant member");
            assertNotEquals(403, res.statusCode(), () -> rc + " must not 403 a cross-tenant member: " + res.body());
        }
    }

    /**
     * The existing S2.6 smoke test proved this for 2 routes; this proves it for the full 19
     * app-scoped routes, and — critically — the grant is to a user who is NOT the app's {@code
     * author}, so any pass here is attributable only to the membership row, not an author-fallback
     * artifact.
     */
    @Test
    public void crossTenantMembershipOnlyOwnerIsAdmittedToEveryAppScopedRoute() throws Exception {
        String ownerViaMembershipSession = crossTenantSession("s29xt_owner_via_membership");
        AppMembershipService.grant(VICTIM_TENANT, APP_ID, "s29xt_owner_via_membership",
                AppMembershipService.Role.OWNER, "test-setup");
        assertTrue(AppMembershipService.isMember(VICTIM_TENANT, APP_ID, "s29xt_owner_via_membership"),
                "sanity check: the granted user must actually hold a membership row before the route assertions mean anything");
        assertTrue(AppMembershipService.isOwner(VICTIM_TENANT, APP_ID, "s29xt_owner_via_membership"),
                "sanity check: the granted user must hold the OWNER role specifically, not just any membership row");

        for (RouteCase rc : allAppScopedRoutes()) {
            HttpResponse<String> res = send(rc.method(), rc.path(), ownerViaMembershipSession, null);
            assertNotEquals(401, res.statusCode(), () -> rc + " must not 401 a cross-tenant membership-only owner");
            assertNotEquals(403, res.statusCode(), () -> rc + " must not 403 a cross-tenant membership-only owner: " + res.body());
        }
    }

    /**
     * The one deliberate exception to "membership admits a cross-tenant caller": the bare
     * tenant-wide list has no specific app to check membership against, so {@code
     * TenantAccessGuard.requireOwnTenant} is called with a {@code null} {@code pathAppId} there
     * (confirmed by direct read) and the S2.6 membership branch structurally cannot fire. Proven
     * across all three membership roles so a future accidental widening of that route to accept
     * per-app membership would be caught here specifically, not just inferred from the other tests'
     * silence on this route.
     */
    @Test
    public void bareTenantListRouteNeverAdmitsCrossTenantCallerRegardlessOfMembershipRole() throws Exception {
        String endUserSession = crossTenantSession("s29xt_barelist_enduser");
        AppMembershipService.grant(VICTIM_TENANT, APP_ID, "s29xt_barelist_enduser", AppMembershipService.Role.END_USER, "test-setup");
        String memberSession = crossTenantSession("s29xt_barelist_member");
        AppMembershipService.grant(VICTIM_TENANT, APP_ID, "s29xt_barelist_member", AppMembershipService.Role.MEMBER, "test-setup");
        String ownerSession = crossTenantSession("s29xt_barelist_owner");
        AppMembershipService.grant(VICTIM_TENANT, APP_ID, "s29xt_barelist_owner", AppMembershipService.Role.OWNER, "test-setup");

        RouteCase rc = bareTenantListRoute();
        for (String session : List.of(endUserSession, memberSession, ownerSession)) {
            HttpResponse<String> res = send(rc.method(), rc.path(), session, null);
            assertEquals(403, res.statusCode(),
                    () -> rc + " must deny every cross-tenant caller regardless of app membership role"
                            + " (no pathAppId ⇒ the S2.6 membership exception cannot apply), got "
                            + res.statusCode() + ": " + res.body());
        }
    }

    @Test
    public void crossTenantMemberCanUploadFileForTheApp() throws Exception {
        String memberSession = crossTenantSession("s29xt_file_member");
        AppMembershipService.grant(VICTIM_TENANT, APP_ID, "s29xt_file_member", AppMembershipService.Role.MEMBER, "test-setup");

        String contentBase64 = Base64.getEncoder().encodeToString("s2.9 cross-tenant upload fixture".getBytes(StandardCharsets.UTF_8));
        String uploadBody = MAPPER.writeValueAsString(Map.of(
                "tenantId", VICTIM_TENANT,
                "appId", APP_ID,
                "filename", "s29-fixture.txt",
                "mimeType", "text/plain",
                "contentBase64", contentBase64
        ));

        HttpResponse<String> uploadRes = send("POST", "/api/files/upload", memberSession, uploadBody);
        assertEquals(201, uploadRes.statusCode(),
                "a cross-tenant member must be admitted by FileRoutes.handleUpload's TenantAccessGuard check: " + uploadRes.body());

        String noMembershipSession = crossTenantSession("s29xt_file_no_membership");
        HttpResponse<String> deniedRes = send("POST", "/api/files/upload", noMembershipSession, uploadBody);
        assertEquals(403, deniedRes.statusCode(),
                "a cross-tenant session with no membership row must still be denied the same upload");
    }
}
