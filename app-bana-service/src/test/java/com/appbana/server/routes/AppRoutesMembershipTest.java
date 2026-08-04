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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AppRoutesMembershipTest — S2.9 (docs/planning/TENANT_ISOLATION_IMPLEMENTATION_TASKS.md).
 *
 * <p>{@code AppAuthorizationTest} proves {@code isManagerOrSystem}'s own contract at the unit
 * level (owner/member allow, end-user denies), and {@code CrossTenantAppAccessTest}'s S2.6
 * activation smoke test proves the split is wired end-to-end for exactly 2 of
 * {@code AppRoutes.java}'s 12 {@code denyIfNotManager}-gated handlers, in a CROSS-tenant scenario.
 * Neither proves the SAME-tenant case across the full route set: whether a same-tenant
 * {@code member} (a role that did not exist before S2) is genuinely admitted, and a same-tenant
 * {@code end-user} genuinely denied, by every one of the 12 real HTTP handlers — the pre-S2 trust
 * model let every same-tenant session manage every app of that tenant, so this is the first test
 * to formalize that the new role split actually narrows that for a real end-user grant.
 *
 * <p>"Not denied" (401/403) rather than a specific 2xx is asserted for the admitted cases,
 * mirroring {@code CrossTenantAppAccessTest}'s own reasoning: this suite tests the role gate
 * specifically, not whether each route's business logic succeeds against a minimal fixture.
 * {@code denyIfNotManager} runs immediately after the tenant guard and before any
 * query/body validation (confirmed by reading every handler), so the end-user 403 assertions are
 * exact and cannot be masked by a downstream 400.
 */
public class AppRoutesMembershipTest {

    private static final int PORT = 18100;
    private static final String BASE_URL = "http://localhost:" + PORT;
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TENANT = "s29-approutes-tenant";
    private static final String APP_ID = "s29-approutes-app";
    private static final String PAGE_ID = "s29-approutes-page";

    private String ownerSession;
    private String memberSession;
    private String endUserSession;

    @BeforeAll
    public static void startServer() throws Exception {
        ApiServer.startJdk(PORT);
    }

    @BeforeEach
    public void setUp() throws Exception {
        try (Connection c = JdbcManager.getConnection("default");
             Statement s = c.createStatement()) {
            s.execute("DELETE FROM appbana_apps WHERE tenant_id = '" + TENANT + "'");
            s.execute("DELETE FROM appbana_app_members WHERE tenant_id = '" + TENANT + "'");
        }

        ownerSession = SessionService.createSession("s29ar_owner", TENANT).sessionId();
        memberSession = SessionService.createSession("s29ar_member", TENANT).sessionId();
        endUserSession = SessionService.createSession("s29ar_enduser", TENANT).sessionId();

        HttpResponse<String> appRes = send("POST", "/appbana-studio/" + TENANT + "/apps", ownerSession,
                MAPPER.writeValueAsString(Map.of("id", APP_ID, "name", "S2.9 AppRoutes fixture", "version", "1.0.0")));
        assertEquals(201, appRes.statusCode(), "Test fixture setup: app creation must succeed: " + appRes.body());

        HttpResponse<String> pageRes = send("PUT",
                "/appbana-studio/" + TENANT + "/apps/" + APP_ID + "/pages/" + PAGE_ID, ownerSession,
                MAPPER.writeValueAsString(Map.of("title", "S2.9 fixture page")));
        assertEquals(200, pageRes.statusCode(), "Test fixture setup: page creation must succeed: " + pageRes.body());

        AppMembershipService.grant(TENANT, APP_ID, "s29ar_member", AppMembershipService.Role.MEMBER, "test-setup");
        AppMembershipService.grant(TENANT, APP_ID, "s29ar_enduser", AppMembershipService.Role.END_USER, "test-setup");
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

    /**
     * The 12 {@code denyIfNotManager}-gated handlers, confirmed by direct read of
     * {@code AppRoutes.java}. Both DELETE routes are placed last, matching
     * {@code CrossTenantAppAccessTest}'s own ordering rationale: within a single test method's
     * loop, nothing after them needs the app/page to still exist.
     */
    private List<RouteCase> managementRoutes() {
        String t = TENANT, a = APP_ID, p = PAGE_ID;
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

    /** The 8 read-only handlers — tenant guard only, no role gate; every role must still be admitted. */
    private List<RouteCase> readOnlyRoutes() {
        String t = TENANT, a = APP_ID, p = PAGE_ID;
        return List.of(
                new RouteCase("GET", "/appbana-studio/" + t + "/apps"),
                new RouteCase("GET", "/appbana-studio/" + t + "/apps/" + a),
                new RouteCase("GET", "/api/" + t + "/apps/" + a + "/full"),
                new RouteCase("GET", "/api/" + t + "/apps/" + a + "/env/DEV/full"),
                new RouteCase("GET", "/api/" + t + "/apps/" + a + "/versions"),
                new RouteCase("GET", "/api/" + t + "/apps/" + a + "/pipeline"),
                new RouteCase("GET", "/appbana-studio/" + t + "/apps/" + a + "/workflow"),
                new RouteCase("GET", "/appbana-studio/" + t + "/apps/" + a + "/pages/" + p)
        );
    }

    @Test
    public void ownerIsAdmittedToEveryManagementRoute() throws Exception {
        for (RouteCase rc : managementRoutes()) {
            HttpResponse<String> res = send(rc.method(), rc.path(), ownerSession, null);
            assertNotEquals(401, res.statusCode(), () -> rc + " must not 401 the owner");
            assertNotEquals(403, res.statusCode(), () -> rc + " must not 403 the owner: " + res.body());
        }
    }

    @Test
    public void memberIsAdmittedToEveryManagementRoute() throws Exception {
        for (RouteCase rc : managementRoutes()) {
            HttpResponse<String> res = send(rc.method(), rc.path(), memberSession, null);
            assertNotEquals(401, res.statusCode(), () -> rc + " must not 401 a member — only end-user is excluded from management");
            assertNotEquals(403, res.statusCode(), () -> rc + " must not 403 a member: " + res.body());
        }
    }

    @Test
    public void endUserIsDeniedEveryManagementRouteWith403() throws Exception {
        for (RouteCase rc : managementRoutes()) {
            HttpResponse<String> res = send(rc.method(), rc.path(), endUserSession, null);
            assertEquals(403, res.statusCode(),
                    () -> rc + " must deny an end-user with exactly 403 (denyIfNotManager runs before any "
                            + "query/body validation), got " + res.statusCode() + ": " + res.body());
        }
    }

    @Test
    public void allThreeRolesAreAdmittedToEveryReadOnlyRoute() throws Exception {
        for (RouteCase rc : readOnlyRoutes()) {
            for (String session : List.of(ownerSession, memberSession, endUserSession)) {
                HttpResponse<String> res = send(rc.method(), rc.path(), session, null);
                assertNotEquals(401, res.statusCode(), () -> rc + " must not 401 any of the 3 roles");
                assertNotEquals(403, res.statusCode(), () -> rc + " must not 403 any of the 3 roles: " + res.body());
            }
        }
    }

    /**
     * A blocked end-user management attempt must not have any side effect. Picks the app's own
     * PUT (rename) as the representative destructive-but-non-terminal case — DELETE would remove
     * the fixture the assertion needs to re-read.
     */
    @Test
    public void endUsersBlockedUpdateAttemptLeavesAppUnchanged() throws Exception {
        HttpResponse<String> denyRes = send("PUT", "/appbana-studio/" + TENANT + "/apps/" + APP_ID, endUserSession,
                MAPPER.writeValueAsString(Map.of("name", "hijacked-by-enduser")));
        assertEquals(403, denyRes.statusCode());

        HttpResponse<String> getRes = send("GET", "/appbana-studio/" + TENANT + "/apps/" + APP_ID, ownerSession, null);
        assertEquals(200, getRes.statusCode());
        assertFalse(getRes.body().contains("hijacked-by-enduser"),
                "the blocked end-user update attempt must not have renamed the app");
    }
}
