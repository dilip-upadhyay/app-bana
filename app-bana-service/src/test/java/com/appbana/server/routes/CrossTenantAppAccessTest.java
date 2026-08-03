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
 * CrossTenantAppAccessTest — S1.11 capstone (docs/planning/TENANT_ISOLATION_IMPLEMENTATION_TASKS.md).
 *
 * <p>Formalizes, as automated tests, that a tenant B session cannot list/get/update/delete/
 * publish/deploy/rollback/restore tenant A's apps — scenarios individually wired by S1.3 (guard on
 * every {@code AppRoutes} handler) but, other than app creation ({@link AppRoutesTenantIsolationTest},
 * finding B1) and the {@code .../full}/{@code .../env/{env}/full} pair (S1.9), never previously
 * exercised by a JUnit test; S1.3's own proof was a live browser click-through covering only a
 * subset (get by id, bare tenant list).
 *
 * <p>Every route below is an {@code AppRoutes.java} handler whose first substantive line is
 * {@code TenantAccessGuard.requireOwnTenant(req, cfg, tenantId, appId)} — confirmed by direct
 * read, not assumed from the route's name. Deliberately excluded: {@code POST
 * /appbana-studio/{tenantId}/apps} (create — covered by {@link AppRoutesTenantIsolationTest}) and
 * both {@code .../full} routes (covered there too, S1.9). {@code /api/templates} is intentionally
 * NOT in scope — it is admin-gated, not tenant-scoped (S1.6).
 *
 * <p>The positive (membership) case — a tenant B session that legitimately belongs to one specific
 * tenant A app — is deliberately NOT covered here. Per the S1.10 review round 2 sequencing note,
 * that stays owned by S2.9's {@code CrossTenantMembershipAllowsAccessTest}, since
 * {@code TenantAccessGuard}'s membership branch ships permanently inert until S2.6 wires
 * {@code AppMembershipService.isMember} in.
 *
 * <p>Unauthenticated (401): 9 of these 18 routes are {@code /appbana-studio/*}-shaped, so that 401
 * actually comes from {@code SessionMiddleware} (not excluded from session enforcement), not
 * {@code TenantAccessGuard}; the guard's own 401 branch is exercised by the other 9,
 * {@code /api/{tenantId}/apps/*}-shaped routes. Confirmed by live-probing the real backend's
 * response bodies, which differ per layer (S1.11 review round 4) — both denials are correct,
 * this is a defense-in-depth note, not a gap.
 */
public class CrossTenantAppAccessTest {

    private static final int PORT = 18095;
    private static final String BASE_URL = "http://localhost:" + PORT;
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String VICTIM_TENANT = "t_s111_victim";
    private static final String ATTACKER_TENANT = "t_s111_attacker";
    private static final String APP_ID = "s111-fixture-app";
    private static final String PAGE_ID = "s111-fixture-page";

    private String victimOwnerSession;
    private String attackerSession;

    @BeforeAll
    public static void startServer() throws Exception {
        ApiServer.startJdk(PORT);
    }

    // Scoped to this test class's own fixture tenants only — see RoleRoutesSecurityTest for why
    // a blanket DELETE with no WHERE clause must never be used against the shared dev Postgres.
    @BeforeEach
    public void setUp() throws Exception {
        try (Connection c = JdbcManager.getConnection("default");
             Statement s = c.createStatement()) {
            s.execute("DELETE FROM appbana_apps WHERE tenant_id IN ('" + VICTIM_TENANT + "', '" + ATTACKER_TENANT + "')");
            s.execute("DELETE FROM appbana_app_members WHERE tenant_id IN ('" + VICTIM_TENANT + "', '" + ATTACKER_TENANT + "')");
        }

        victimOwnerSession = createTestSession("s111_owner", VICTIM_TENANT);
        attackerSession = createTestSession("s111_attacker", ATTACKER_TENANT);

        HttpResponse<String> appRes = send("POST", "/appbana-studio/" + VICTIM_TENANT + "/apps", victimOwnerSession,
                MAPPER.writeValueAsString(Map.of("id", APP_ID, "name", "S1.11 fixture app", "version", "1.0.0")));
        assertEquals(201, appRes.statusCode(), "Test fixture setup: app creation must succeed: " + appRes.body());
        // S2.3 backend coverage: creator must receive an owner membership row when their app is created.
        assertTrue(AppMembershipService.isMember(VICTIM_TENANT, APP_ID, "s111_owner"),
                "S2.3: creator must hold an owner membership row immediately after app creation");

        HttpResponse<String> pageRes = send("PUT",
                "/appbana-studio/" + VICTIM_TENANT + "/apps/" + APP_ID + "/pages/" + PAGE_ID, victimOwnerSession,
                MAPPER.writeValueAsString(Map.of("title", "S1.11 fixture page")));
        assertEquals(200, pageRes.statusCode(), "Test fixture setup: page creation must succeed: " + pageRes.body());
    }

    private String createTestSession(String userId, String tenantId) {
        return SessionService.createSession(userId, tenantId).sessionId();
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

    /** One (method, path) pair per {@code AppRoutes.java} route gated by the tenant guard, in scope for S1.11. */
    private record RouteCase(String method, String path) {
        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    private List<RouteCase> guardedRoutes() {
        String t = VICTIM_TENANT, a = APP_ID, p = PAGE_ID;
        return List.of(
                new RouteCase("GET", "/appbana-studio/" + t + "/apps"),
                new RouteCase("GET", "/appbana-studio/" + t + "/apps/" + a),
                new RouteCase("PUT", "/appbana-studio/" + t + "/apps/" + a),
                new RouteCase("POST", "/api/" + t + "/apps/" + a + "/publish?env=DEV"),
                new RouteCase("PUT", "/api/" + t + "/apps/" + a + "/deploy/local"),
                new RouteCase("POST", "/api/" + t + "/apps/" + a + "/commits"),
                new RouteCase("POST", "/api/" + t + "/apps/" + a + "/commits/rollback"),
                new RouteCase("POST", "/api/" + t + "/apps/" + a + "/versions"),
                new RouteCase("GET", "/api/" + t + "/apps/" + a + "/versions"),
                new RouteCase("POST", "/api/" + t + "/apps/" + a + "/deploy/v1"),
                new RouteCase("GET", "/api/" + t + "/apps/" + a + "/pipeline"),
                new RouteCase("POST", "/api/" + t + "/apps/" + a + "/restore-schemas"),
                new RouteCase("GET", "/appbana-studio/" + t + "/apps/" + a + "/workflow"),
                new RouteCase("PUT", "/appbana-studio/" + t + "/apps/" + a + "/workflow"),
                new RouteCase("GET", "/appbana-studio/" + t + "/apps/" + a + "/pages/" + p),
                new RouteCase("PUT", "/appbana-studio/" + t + "/apps/" + a + "/pages/" + p),
                new RouteCase("DELETE", "/appbana-studio/" + t + "/apps/" + a + "/pages/" + p),
                new RouteCase("DELETE", "/appbana-studio/" + t + "/apps/" + a)
        );
    }

    @Test
    public void testEveryGuardedAppRouteRejectsCrossTenantSession() throws Exception {
        for (RouteCase rc : guardedRoutes()) {
            HttpResponse<String> res = send(rc.method(), rc.path(), attackerSession, null);
            assertEquals(403, res.statusCode(),
                    () -> rc + " must reject a cross-tenant session with 403, got " + res.statusCode() + ": " + res.body());
        }
    }

    @Test
    public void testEveryGuardedAppRouteRejectsUnauthenticated() throws Exception {
        for (RouteCase rc : guardedRoutes()) {
            HttpResponse<String> res = send(rc.method(), rc.path(), null, null);
            assertEquals(401, res.statusCode(),
                    () -> rc + " must reject an unauthenticated request with 401, got " + res.statusCode() + ": " + res.body());
        }
    }

    /**
     * "Not denied" rather than "200" on purpose: this suite tests the tenant guard specifically,
     * not whether every route's own business logic succeeds against a minimal fixture (e.g. publish
     * against an app with no entities, or pipeline status before anything was ever deployed, can
     * legitimately 400/404/500 downstream of the guard for reasons unrelated to tenant isolation).
     * A 401/403 here would mean the guard itself regressed for the app's own rightful owner.
     */
    @Test
    public void testEveryGuardedAppRouteStillAdmitsTheOwningTenant() throws Exception {
        for (RouteCase rc : guardedRoutes()) {
            HttpResponse<String> res = send(rc.method(), rc.path(), victimOwnerSession, null);
            assertNotEquals(401, res.statusCode(), () -> rc + " must not 401 the app's own owner");
            assertNotEquals(403, res.statusCode(), () -> rc + " must not 403 the app's own owner: " + res.body());
        }
    }

    @Test
    public void testCrossTenantDeleteAttemptLeavesAppIntact() throws Exception {
        HttpResponse<String> denyRes = send("DELETE", "/appbana-studio/" + VICTIM_TENANT + "/apps/" + APP_ID, attackerSession, null);
        assertEquals(403, denyRes.statusCode());

        HttpResponse<String> getRes = send("GET", "/appbana-studio/" + VICTIM_TENANT + "/apps/" + APP_ID, victimOwnerSession, null);
        assertEquals(200, getRes.statusCode(), "The blocked cross-tenant delete attempt must not have deleted the app");
    }

    @Test
    public void testOwnerCanStillDeleteOwnApp() throws Exception {
        HttpResponse<String> delRes = send("DELETE", "/appbana-studio/" + VICTIM_TENANT + "/apps/" + APP_ID, victimOwnerSession, null);
        assertEquals(200, delRes.statusCode());

        HttpResponse<String> getRes = send("GET", "/appbana-studio/" + VICTIM_TENANT + "/apps/" + APP_ID, victimOwnerSession, null);
        assertEquals(404, getRes.statusCode(), "After the owner's own real delete, the app must actually be gone");
    }
}
