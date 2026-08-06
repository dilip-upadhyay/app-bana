package com.appbana.server.routes;

import com.appbana.ApiServer;
import com.appbana.JdbcManager;
import com.appbana.SchemaManager;
import com.appbana.model.EntitySchema;
import com.appbana.security.AppMembershipService;
import com.appbana.service.SessionService;
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
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CrossTenantEntityAccessTest — S3.6 (docs/planning/TENANT_ISOLATION_IMPLEMENTATION_TASKS.md).
 *
 * <p>Formalizes, as an automated test, that {@link com.appbana.security.EntityAccessGuard}'s
 * rule (i) — "any {@code appbana_app_members} role admits, regardless of the caller session's
 * own {@code tenantId}" — actually holds through <b>real route dispatch</b>, not just the guard's
 * own unit tests. {@code EntityAccessGuardTest#testOwnerMembershipAdmits} already proves this at
 * the guard level directly; what that test structurally cannot catch is a route that extracts or
 * forwards {@code tenantId}/{@code appId} incorrectly before ever calling the guard. This class
 * hits the 3 real, registered route families (packed-key, studio-scoped, and the path-segmented
 * apps family covering both the no-env and env-scoped shapes) exactly as
 * {@link CrossTenantAppAccessTest} (S1.11) did for {@code AppRoutes}.
 *
 * <p>Two scenarios, same fixture entity: (1) a tenant-B session with <b>zero</b> membership on
 * tenant A's app is denied 403 everywhere: no rule matches (not a member, no matching
 * {@code scopedAppId}, entity is not {@code publicRead}). (2) that same user, once granted
 * membership on (tenant A, app A) despite their session's own {@code tenantId} still being tenant
 * B, is admitted everywhere — proving rule (i) is genuinely tenant-agnostic through real dispatch,
 * not merely in the guard's own isolated unit test.
 */
public class CrossTenantEntityAccessTest {

    private static final int PORT = 18104;
    private static final String BASE_URL = "http://localhost:" + PORT;
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TENANT_A = "t-s36-ct-victim";
    private static final String TENANT_B = "t-s36-ct-attacker";
    private static final String APP_A = "s36-ct-app";
    private static final String ENTITY_NAME = "S36CrossTenantEntity";
    private static final String ATTACKER_USER = "s36-ct-attacker-user";

    private String attackerSession;

    @BeforeAll
    public static void startServer() throws Exception {
        ApiServer.startJdk(PORT);
    }

    // Scoped to this test class's own fixture tenants/app only — never a blanket statement
    // against the shared dev Postgres (see RoleRoutesSecurityTest for why that matters).
    @BeforeEach
    public void setUp() throws Exception {
        SessionService.clearAllSessions();
        cleanUpFixtures();
        saveFixtureSchema();
        // A high manual ID avoids colliding with the BIGSERIAL sequence (which starts fresh at 1
        // for every re-created table) once the write-route tests below POST a new row through the
        // real auto-increment path.
        insertRow(999001L, "victim's row");

        attackerSession = SessionService.createSession(ATTACKER_USER, TENANT_B).sessionId();
    }

    @AfterEach
    public void tearDown() {
        SessionService.clearAllSessions();
        cleanUpFixtures();
    }

    private void cleanUpFixtures() {
        try (Connection c = JdbcManager.getConnection("default");
                Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS \"" + physicalTableName().toUpperCase() + "\"");
            s.execute("DELETE FROM appbana_schemas WHERE tenant_id = '" + TENANT_A + "' AND app_id = '" + APP_A + "'");
            s.execute("DELETE FROM appbana_app_members WHERE tenant_id = '" + TENANT_A + "' AND app_id = '" + APP_A + "'");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String physicalTableName() {
        EntitySchema s = new EntitySchema();
        s.setTenantId(TENANT_A);
        s.setAppId(APP_A);
        s.setName(ENTITY_NAME);
        return SchemaManager.getPhysicalTableName(s);
    }

    private void saveFixtureSchema() {
        EntitySchema s = new EntitySchema();
        s.setName(ENTITY_NAME);
        s.setAppId(APP_A);
        s.setTenantId(TENANT_A);

        EntitySchema.Field id = new EntitySchema.Field();
        id.setName("id");
        id.setType("long");
        id.setPrimaryKey(true);
        id.setAutoIncrement(true);

        EntitySchema.Field label = new EntitySchema.Field();
        label.setName("label");
        label.setType("string");

        s.setFields(List.of(id, label));
        SchemaManager.saveSchema(s);
    }

    private void insertRow(long id, String label) {
        String table = physicalTableName().toUpperCase();
        String sql = "INSERT INTO \"" + table + "\" (\"ID\",\"LABEL\") VALUES (?,?)";
        try (Connection c = JdbcManager.getConnection("default");
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setString(2, label);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String packedKey() {
        return TENANT_A + "_" + APP_A + "_" + ENTITY_NAME;
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

    /** One (method, path) pair per route family in scope for S3.6. */
    private record RouteCase(String method, String path) {
        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    /** GET routes: one representative per family — (a) packed-key, (b) studio-scoped, (b) env-scoped. */
    private List<RouteCase> readRoutesAcrossAllFamilies() {
        String t = TENANT_A, a = APP_A, e = ENTITY_NAME;
        return List.of(
                new RouteCase("GET", "/api/" + packedKey()),
                new RouteCase("GET", "/appbana-studio/" + t + "/apps/" + a + "/" + e),
                new RouteCase("GET", "/api/" + t + "/apps/" + a + "/env/dev/" + e)
        );
    }

    /** POST routes: one representative per family, including the distinct app-scoped-no-env shape. */
    private List<RouteCase> writeRoutesAcrossAllFamilies() {
        String t = TENANT_A, a = APP_A, e = ENTITY_NAME;
        return List.of(
                new RouteCase("POST", "/api/" + packedKey()),
                new RouteCase("POST", "/appbana-studio/" + t + "/apps/" + a + "/" + e),
                new RouteCase("POST", "/api/" + t + "/apps/" + a + "/" + e),
                new RouteCase("POST", "/api/" + t + "/apps/" + a + "/env/dev/" + e)
        );
    }

    @Test
    public void testNoMembershipAttackerDeniedOnReadRoutesAcrossAllFamilies() throws Exception {
        for (RouteCase rc : readRoutesAcrossAllFamilies()) {
            HttpResponse<String> res = send(rc.method(), rc.path(), attackerSession, null);
            assertEquals(403, res.statusCode(),
                    () -> rc + " must deny a same-app-but-no-membership tenant-B session with 403, got "
                            + res.statusCode() + ": " + res.body());
        }
    }

    @Test
    public void testNoMembershipAttackerDeniedOnWriteRoutesAcrossAllFamilies() throws Exception {
        String body = MAPPER.writeValueAsString(Map.of("label", "attacker-injected"));
        for (RouteCase rc : writeRoutesAcrossAllFamilies()) {
            HttpResponse<String> res = send(rc.method(), rc.path(), attackerSession, body);
            assertEquals(403, res.statusCode(),
                    () -> rc + " must deny a same-app-but-no-membership tenant-B session with 403, got "
                            + res.statusCode() + ": " + res.body());
        }
    }

    /**
     * The flagship property: membership admits regardless of the session's own tenantId. Granting
     * {@code ATTACKER_USER} (whose session's tenantId is still TENANT_B) a membership row on
     * (TENANT_A, APP_A) must flip every route above from 403 to admitted — proving rule (i) is
     * genuinely tenant-agnostic through real dispatch, not just in EntityAccessGuardTest's own
     * direct-call unit test.
     */
    @Test
    public void testCrossTenantMembershipAdmitsOnReadRoutesAcrossAllFamilies() throws Exception {
        AppMembershipService.grant(TENANT_A, APP_A, ATTACKER_USER, AppMembershipService.Role.MEMBER, "test-setup");

        for (RouteCase rc : readRoutesAcrossAllFamilies()) {
            HttpResponse<String> res = send(rc.method(), rc.path(), attackerSession, null);
            assertNotEquals(401, res.statusCode(), () -> rc + " must not 401 a cross-tenant member: " + res.body());
            assertNotEquals(403, res.statusCode(), () -> rc + " must not 403 a cross-tenant member: " + res.body());
        }
    }

    @Test
    public void testCrossTenantMembershipAdmitsOnWriteRoutesAcrossAllFamilies() throws Exception {
        AppMembershipService.grant(TENANT_A, APP_A, ATTACKER_USER, AppMembershipService.Role.MEMBER, "test-setup");
        String body = MAPPER.writeValueAsString(Map.of("label", "legitimate cross-tenant member write"));

        for (RouteCase rc : writeRoutesAcrossAllFamilies()) {
            HttpResponse<String> res = send(rc.method(), rc.path(), attackerSession, body);
            // A genuine 2xx, not merely "not 401/403" — a stray 500 downstream of the guard must
            // not be silently read as proof that authorization succeeded.
            assertTrue(res.statusCode() >= 200 && res.statusCode() < 300,
                    () -> rc + " must actually succeed for a cross-tenant member (guard admitted, insert must work): "
                            + res.statusCode() + ": " + res.body());
        }
    }

    @Test
    public void testUnauthenticatedRequestDeniedOnEveryFamily() throws Exception {
        for (RouteCase rc : readRoutesAcrossAllFamilies()) {
            HttpResponse<String> res = send(rc.method(), rc.path(), null, null);
            // Studio-scoped is 401 from SessionMiddleware itself (no session at all, pre-existing,
            // documented behavior — SessionMiddleware.isExcludedPath does not exclude
            // /appbana-studio/*, S1.11 review round 4); packed-key and env-scoped are 401 from
            // EntityAccessGuard's own no-session branch. Either way the caller-visible contract is
            // identical: no credentials at all must never be treated as more privileged than an
            // authenticated-but-unauthorized one.
            assertEquals(401, res.statusCode(),
                    () -> rc + " must deny a fully unauthenticated request with 401, got " + res.statusCode());
        }
    }

    @Test
    public void testAttackerNeverActuallyReadTheVictimRowDespiteRepeatedAttempts() throws Exception {
        for (RouteCase rc : readRoutesAcrossAllFamilies()) {
            HttpResponse<String> res = send(rc.method(), rc.path(), attackerSession, null);
            assertTrue(res.statusCode() >= 400, rc + " must not leak the victim row: " + res.body());
            assertTrue(!res.body().contains("victim's row"),
                    () -> rc + " response body must never contain the victim's actual data: " + res.body());
        }
    }
}
