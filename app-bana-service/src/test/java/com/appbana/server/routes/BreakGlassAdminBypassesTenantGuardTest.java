package com.appbana.server.routes;

import com.appbana.ApiServer;
import com.appbana.JdbcManager;
import com.appbana.config.AppConfig;
import com.appbana.config.ConfigManager;
import com.appbana.service.SessionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BreakGlassAdminBypassesTenantGuardTest — S1.14
 * (docs/planning/TENANT_ISOLATION_IMPLEMENTATION_TASKS.md).
 *
 * <p>Formalizes, end-to-end against real running {@code AppRoutes} handlers, the admit-first
 * branch {@link com.appbana.security.TenantAccessGuard#requireOwnTenant} already implements and
 * {@code TenantAccessGuardTest} already unit-tests against a mocked {@code Router.HttpRequest}.
 * That existing test proves the guard method itself admits a valid service/admin token; this
 * class proves the SAME behavior survives a real route, a real fixture app, and real
 * session/X-User-Id combinations — the S1.11-style "prove it at the route, not just the guard"
 * discipline.
 *
 * <p><b>Task-text correction #1 (route family):</b> the task names "an AppRoutes/SchemaRoutes
 * route." Confirmed by direct grep that {@code SchemaRoutes.java} never calls
 * {@code TenantAccessGuard.requireOwnTenant} anywhere — its one textual match on
 * "TenantAccessGuard" is a comment noting its own, separate {@code hasAdmin}-based gate "mirrors
 * TenantAccessGuard" in shape, not a shared call site. {@code SchemaRoutesAdminTokenTest} and
 * {@code SchemaRoutesTenantIsolationTest#testGetSchemaAdminTokenSeesBothTenants} already cover
 * SchemaRoutes' own, independent admin bypass at the route level; there is no
 * {@code TenantAccessGuard} call there left to additionally prove. This class instead covers TWO
 * real {@code AppRoutes} call sites — one read ({@code GET .../apps/{id}}), one write
 * ({@code PUT .../apps/{id}}) — to span both verb shapes without inventing a SchemaRoutes call
 * site that does not exist.
 *
 * <p><b>Task-text correction #2 ("no session needed") — found by this test, live, first run:</b>
 * the first draft of this class sent the admin token with NO session at all, mirroring
 * {@code TenantAccessGuardTest}'s pure-unit-test scenario (a mocked request, calling the guard
 * method directly). All 5 of those cases failed with 401 "Missing session token" — NOT from
 * {@code TenantAccessGuard} at all, but from {@link com.appbana.middleware.SessionMiddleware},
 * a separate, EARLIER layer that unconditionally requires a session for {@code /appbana-studio/*}
 * (see the standing comment in {@code SessionMiddleware.isExcludedPath}: "/appbana-studio/* is NOT
 * excluded above ... verified live, S1.11 review round 4"). This is the exact
 * "a route can be protected by more than one independent layer" trap this repo's own
 * instructions already document for {@code /schema} — now confirmed to separately apply here too.
 * The fix is not a bypass of {@code SessionMiddleware} (there isn't one, by design) — it is
 * pairing the admin token with a session that belongs to a tenant UNRELATED to both the path
 * tenant and the fixture app's real tenant. That satisfies {@code SessionMiddleware} (any valid
 * session at all) while proving {@code TenantAccessGuard}'s admit-first branch ignores the
 * session's tenant entirely once a valid service token is present — the accurate reading of
 * "regardless of path tenant." {@link #testWithoutAdminTokenOrSessionTheRouteStill401s} keeps the
 * true zero-credential case as the baseline negative control.
 *
 * <p>Every positive assertion below checks real evidence the guarded handler actually executed
 * (the real fixture app's own name in the response body, or the route's own distinct DB-layer
 * 404 message) rather than merely "the response wasn't 403" — the S1.9-class hazard the review
 * loop explicitly flagged for this task: a test that only checks "not 403" can pass even when the
 * guarded code path never really ran.
 */
public class BreakGlassAdminBypassesTenantGuardTest {

    private static final int PORT = 18097;
    private static final String BASE_URL = "http://localhost:" + PORT;
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String ADMIN_TOKEN = "s114-admin-xyz";
    private static final String FIXTURE_TENANT = "t_s114_fixture";
    private static final String SERVICE_CALLER_TENANT = "t_s114_service_caller_tenant";
    private static final String MISMATCHED_TENANT = "t-s114-totally-unrelated-tenant";
    private static final String FIXTURE_APP_ID = "s114-fixture-app";
    private static final String FIXTURE_APP_NAME = "S1.14 fixture app";

    private String originalAdminToken;
    // A session belonging to a tenant unrelated to FIXTURE_TENANT and MISMATCHED_TENANT alike.
    // Satisfies SessionMiddleware's unconditional "some valid session" requirement for
    // /appbana-studio/* without giving TenantAccessGuard's tenant-matching branch anything to
    // agree with — so a bypass proven with this session is genuinely due to the admin token, not
    // to the session happening to already own the right tenant.
    private String serviceCallerSession;

    @BeforeAll
    public static void startServer() throws Exception {
        ApiServer.startJdk(PORT);
    }

    // Scoped to this test class's own fixture tenant only — see RoleRoutesSecurityTest for why a
    // blanket DELETE with no WHERE clause must never be used against the shared dev Postgres.
    @BeforeEach
    public void setUp() throws Exception {
        try (Connection c = JdbcManager.getConnection("default");
             Statement s = c.createStatement()) {
            s.execute("DELETE FROM appbana_apps WHERE tenant_id = '" + FIXTURE_TENANT + "'");
        }

        String ownerSession = SessionService.createSession("s114_owner", FIXTURE_TENANT).sessionId();
        HttpResponse<String> appRes = send("POST", "/appbana-studio/" + FIXTURE_TENANT + "/apps",
                null, ownerSession, null,
                MAPPER.writeValueAsString(Map.of("id", FIXTURE_APP_ID, "name", FIXTURE_APP_NAME, "version", "1.0.0")));
        assertEquals(201, appRes.statusCode(), "Test fixture setup: app creation must succeed: " + appRes.body());

        serviceCallerSession = SessionService.createSession("s114_service_caller", SERVICE_CALLER_TENANT).sessionId();

        AppConfig cfg = ConfigManager.getConfig();
        originalAdminToken = cfg.getAdminToken();
        cfg.setAdminToken(ADMIN_TOKEN);
    }

    @AfterEach
    public void tearDown() {
        ConfigManager.getConfig().setAdminToken(originalAdminToken);
    }

    private HttpResponse<String> send(String method, String path, String serviceTokenOrNull,
                                       String sessionOrNull, String xUserIdOrNull, String bodyOrNull) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder().uri(URI.create(BASE_URL + path));
        if (serviceTokenOrNull != null) b.header("X-AppBana-Token", serviceTokenOrNull);
        if (sessionOrNull != null) b.header("X-Session-Token", sessionOrNull);
        if (xUserIdOrNull != null) b.header("X-User-Id", xUserIdOrNull);
        switch (method) {
            case "GET" -> b.GET();
            case "DELETE" -> b.DELETE();
            default -> b.method(method, HttpRequest.BodyPublishers.ofString(bodyOrNull != null ? bodyOrNull : "{}"))
                    .header("Content-Type", "application/json");
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    // ========================================
    // Negative control — establishes the baseline this whole class contrasts against
    // ========================================

    @Test
    public void testWithoutAdminTokenOrSessionTheRouteStill401s() throws Exception {
        HttpResponse<String> res = send("GET",
                "/appbana-studio/" + FIXTURE_TENANT + "/apps/" + FIXTURE_APP_ID, null, null, null, null);
        assertEquals(401, res.statusCode(),
                "No credentials at all must still 401 — proves the bypasses below are really the admin token, not an unguarded route");
    }

    @Test
    public void testOrdinarySessionMismatchedTenantGets403WithGuardsOwnMessage() throws Exception {
        // Direct contrast for testAdminTokenBypassesEvenWithACompletelyMismatchedPathTenant below:
        // the SAME session (serviceCallerSession), same path tenant, with no admin token, must hit
        // TenantAccessGuard's own denial branch with its own distinct message — a genuine
        // single-variable (admin token present/absent) comparison, not just a different status code.
        HttpResponse<String> res = send("GET",
                "/appbana-studio/" + MISMATCHED_TENANT + "/apps/" + FIXTURE_APP_ID, null, serviceCallerSession, null, null);
        assertEquals(403, res.statusCode(), "A real session whose tenant differs from the path tenant must be denied by the guard: " + res.body());
        JsonNode body = MAPPER.readTree(res.body());
        assertTrue(body.get("error").asText().toLowerCase().contains("tenant"),
                "Must be the guard's own tenant-mismatch denial message: " + res.body());
    }

    @Test
    public void sameSessionOnFixturePathWithoutAdminTokenIs403() throws Exception {
        // Pairs with testAdminTokenAdmitsGetWithUnrelatedTenantSessionAndNoXUserId below: identical
        // session, path, and method, with only the admin token removed — isolates the admin token
        // itself as the one variable that flips 403 to 200, rather than relying solely on the
        // one-time break-test to establish that causation.
        HttpResponse<String> res = send("GET",
                "/appbana-studio/" + FIXTURE_TENANT + "/apps/" + FIXTURE_APP_ID, null, serviceCallerSession, null, null);
        assertEquals(403, res.statusCode(),
                "Same session on the same fixture path, minus the admin token, must be denied by the guard: " + res.body());
        JsonNode body = MAPPER.readTree(res.body());
        assertTrue(body.get("error").asText().toLowerCase().contains("tenant"),
                "Must be the guard's own tenant-mismatch denial message: " + res.body());
    }

    // ========================================
    // GET /appbana-studio/{tenantId}/apps/{id} — read route
    // ========================================

    @Test
    public void testAdminTokenAdmitsGetWithUnrelatedTenantSessionAndNoXUserId() throws Exception {
        HttpResponse<String> res = send("GET",
                "/appbana-studio/" + FIXTURE_TENANT + "/apps/" + FIXTURE_APP_ID, ADMIN_TOKEN, serviceCallerSession, null, null);
        assertEquals(200, res.statusCode(),
                "Admin token + a session for an unrelated tenant, no X-User-Id, must be admitted: " + res.body());
        JsonNode body = MAPPER.readTree(res.body());
        assertEquals(FIXTURE_APP_NAME, body.get("name").asText(),
                "Response must contain the real fixture app's own data — proves the guarded handler actually ran, not merely that the status wasn't 403");
    }

    @Test
    public void testAdminTokenAdmitsGetWithXUserIdPresent() throws Exception {
        HttpResponse<String> res = send("GET",
                "/appbana-studio/" + FIXTURE_TENANT + "/apps/" + FIXTURE_APP_ID, ADMIN_TOKEN, serviceCallerSession, "s114-service-caller", null);
        assertEquals(200, res.statusCode(), "Admin token + X-User-Id must also be admitted: " + res.body());
        JsonNode body = MAPPER.readTree(res.body());
        assertEquals(FIXTURE_APP_NAME, body.get("name").asText());
    }

    @Test
    public void testAdminTokenBypassesEvenWithACompletelyMismatchedPathTenant() throws Exception {
        // The strongest form of "regardless of path tenant": the SAME unrelated-tenant session
        // used alone (see testOrdinarySessionMismatchedTenantGets403WithGuardsOwnMessage above)
        // gets TenantAccessGuard's own 403. Adding the admin token on top must instead clear the
        // guard and reach the route's real handler and DB layer, which surfaces a DIFFERENT, more
        // specific message ("App not found") — proving the code path taken was genuinely
        // different, not merely that the status code happened not to equal 403.
        HttpResponse<String> res = send("GET",
                "/appbana-studio/" + MISMATCHED_TENANT + "/apps/" + FIXTURE_APP_ID, ADMIN_TOKEN, serviceCallerSession, null, null);
        assertEquals(404, res.statusCode(),
                "Admin token must clear TenantAccessGuard even for a path tenant that matches nothing real; " +
                "the 404 (not 403) proves the DB-layer lookup ran, not the guard's own tenant-mismatch branch: " + res.body());
        JsonNode body = MAPPER.readTree(res.body());
        assertTrue(body.get("error").asText().contains("App not found"),
                "Must be the route's own not-found message, not the guard's tenant-mismatch denial: " + res.body());
    }

    // ========================================
    // PUT /appbana-studio/{tenantId}/apps/{id} — write route
    // ========================================

    @Test
    public void testAdminTokenAdmitsPutWithUnrelatedTenantSessionAndNoXUserId() throws Exception {
        String newName = "S1.14 fixture app (updated, no X-User-Id)";
        HttpResponse<String> res = send("PUT",
                "/appbana-studio/" + FIXTURE_TENANT + "/apps/" + FIXTURE_APP_ID, ADMIN_TOKEN, serviceCallerSession, null,
                MAPPER.writeValueAsString(Map.of("name", newName)));
        assertEquals(200, res.statusCode(), "Admin token + unrelated-tenant session must be admitted on the write route too: " + res.body());
        JsonNode body = MAPPER.readTree(res.body());
        assertEquals(newName, body.get("name").asText(), "The real update must have actually been applied and returned, not a stub 200");
    }

    @Test
    public void testAdminTokenAdmitsPutWithXUserIdPresent() throws Exception {
        String newName = "S1.14 fixture app (updated, with X-User-Id)";
        HttpResponse<String> res = send("PUT",
                "/appbana-studio/" + FIXTURE_TENANT + "/apps/" + FIXTURE_APP_ID, ADMIN_TOKEN, serviceCallerSession, "s114-service-caller",
                MAPPER.writeValueAsString(Map.of("name", newName)));
        assertEquals(200, res.statusCode(), "Admin token + X-User-Id must also be admitted on the write route: " + res.body());
        JsonNode body = MAPPER.readTree(res.body());
        assertEquals(newName, body.get("name").asText());
    }
}
