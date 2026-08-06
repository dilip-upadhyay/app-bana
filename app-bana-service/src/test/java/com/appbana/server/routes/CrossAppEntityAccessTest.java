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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CrossAppEntityAccessTest — S3.6 (docs/planning/TENANT_ISOLATION_IMPLEMENTATION_TASKS.md).
 *
 * <p>Formalizes, as an automated test, that {@link com.appbana.security.EntityAccessGuard}'s
 * membership check is scoped to a specific ({@code tenantId}, {@code appId}) pair, not just the
 * tenant — i.e. same-tenant, cross-app access must still be denied.
 * {@code EntityAccessGuardTest#testMembershipOnDifferentAppDoesNotAdmit} already proves this at
 * the guard level directly; this class proves it holds through <b>real route dispatch</b>, across
 * all 3 route families, exactly mirroring {@link CrossTenantEntityAccessTest}'s structure but
 * varying {@code appId} instead of {@code tenantId}.
 *
 * <p>Single tenant, two apps (APP_1, APP_2), each with its own same-named entity (proving
 * isolation comes from {@code appId}, not merely a different entity name). A user who is a
 * {@code MEMBER} of APP_1 only is: denied 403 on APP_2's entity across every family, but still
 * admitted on APP_1's own entity (positive control — proves the denial is a genuine app-scoping
 * check, not a broken fixture or an unrelated regression).
 */
public class CrossAppEntityAccessTest {

    private static final int PORT = 18105;
    private static final String BASE_URL = "http://localhost:" + PORT;
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TENANT = "t-s36-ca-tenant";
    private static final String APP_1 = "s36-ca-app-1";
    private static final String APP_2 = "s36-ca-app-2";
    private static final String ENTITY_NAME = "S36CrossAppEntity";
    private static final String MEMBER_USER = "s36-ca-member-user";

    private String memberSession;

    @BeforeAll
    public static void startServer() throws Exception {
        ApiServer.startJdk(PORT);
    }

    @BeforeEach
    public void setUp() throws Exception {
        SessionService.clearAllSessions();
        cleanUpFixtures();

        saveFixtureSchema(APP_1);
        insertRow(APP_1, 999001L, "app1's row");
        saveFixtureSchema(APP_2);
        insertRow(APP_2, 999002L, "app2's row");

        AppMembershipService.grant(TENANT, APP_1, MEMBER_USER, AppMembershipService.Role.MEMBER, "test-setup");
        memberSession = SessionService.createSession(MEMBER_USER, TENANT).sessionId();
    }

    @AfterEach
    public void tearDown() {
        SessionService.clearAllSessions();
        cleanUpFixtures();
    }

    // Scoped to this test class's own fixture tenant/apps only — never a blanket statement
    // against the shared dev Postgres (see RoleRoutesSecurityTest for why that matters).
    private void cleanUpFixtures() {
        try (Connection c = JdbcManager.getConnection("default");
                Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS \"" + physicalTableName(APP_1).toUpperCase() + "\"");
            s.execute("DROP TABLE IF EXISTS \"" + physicalTableName(APP_2).toUpperCase() + "\"");
            s.execute("DELETE FROM appbana_schemas WHERE tenant_id = '" + TENANT + "' AND app_id IN ('"
                    + APP_1 + "', '" + APP_2 + "')");
            s.execute("DELETE FROM appbana_app_members WHERE tenant_id = '" + TENANT + "' AND app_id IN ('"
                    + APP_1 + "', '" + APP_2 + "')");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String physicalTableName(String appId) {
        EntitySchema s = new EntitySchema();
        s.setTenantId(TENANT);
        s.setAppId(appId);
        s.setName(ENTITY_NAME);
        return SchemaManager.getPhysicalTableName(s);
    }

    private void saveFixtureSchema(String appId) {
        EntitySchema s = new EntitySchema();
        s.setName(ENTITY_NAME);
        s.setAppId(appId);
        s.setTenantId(TENANT);

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

    private void insertRow(String appId, long id, String label) {
        String table = physicalTableName(appId).toUpperCase();
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

    private String packedKey(String appId) {
        return TENANT + "_" + appId + "_" + ENTITY_NAME;
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

    private List<RouteCase> readRoutesFor(String appId) {
        return List.of(
                new RouteCase("GET", "/api/" + packedKey(appId)),
                new RouteCase("GET", "/appbana-studio/" + TENANT + "/apps/" + appId + "/" + ENTITY_NAME),
                new RouteCase("GET", "/api/" + TENANT + "/apps/" + appId + "/env/dev/" + ENTITY_NAME)
        );
    }

    private List<RouteCase> writeRoutesFor(String appId) {
        return List.of(
                new RouteCase("POST", "/api/" + packedKey(appId)),
                new RouteCase("POST", "/appbana-studio/" + TENANT + "/apps/" + appId + "/" + ENTITY_NAME),
                new RouteCase("POST", "/api/" + TENANT + "/apps/" + appId + "/" + ENTITY_NAME),
                new RouteCase("POST", "/api/" + TENANT + "/apps/" + appId + "/env/dev/" + ENTITY_NAME)
        );
    }

    @Test
    public void testAppOneMemberDeniedOnAppTwoReadRoutesAcrossAllFamilies() throws Exception {
        for (RouteCase rc : readRoutesFor(APP_2)) {
            HttpResponse<String> res = send(rc.method(), rc.path(), memberSession, null);
            assertEquals(403, res.statusCode(),
                    () -> rc + " must deny an APP_1-only member on APP_2's entity with 403, got "
                            + res.statusCode() + ": " + res.body());
        }
    }

    @Test
    public void testAppOneMemberDeniedOnAppTwoWriteRoutesAcrossAllFamilies() throws Exception {
        String body = MAPPER.writeValueAsString(Map.of("label", "cross-app-injected"));
        for (RouteCase rc : writeRoutesFor(APP_2)) {
            HttpResponse<String> res = send(rc.method(), rc.path(), memberSession, body);
            assertEquals(403, res.statusCode(),
                    () -> rc + " must deny an APP_1-only member on APP_2's entity with 403, got "
                            + res.statusCode() + ": " + res.body());
        }
    }

    /** Positive control — proves the APP_2 denial above is a real app-scoping check, not a broken fixture. */
    @Test
    public void testAppOneMemberStillAdmittedOnAppOneReadRoutesAcrossAllFamilies() throws Exception {
        for (RouteCase rc : readRoutesFor(APP_1)) {
            HttpResponse<String> res = send(rc.method(), rc.path(), memberSession, null);
            assertTrue(res.statusCode() >= 200 && res.statusCode() < 300,
                    () -> rc + " must still admit the member on their own app: " + res.statusCode() + ": " + res.body());
        }
    }

    @Test
    public void testAppOneMemberStillAdmittedOnAppOneWriteRoutesAcrossAllFamilies() throws Exception {
        String body = MAPPER.writeValueAsString(Map.of("label", "legitimate app1 write"));
        for (RouteCase rc : writeRoutesFor(APP_1)) {
            HttpResponse<String> res = send(rc.method(), rc.path(), memberSession, body);
            assertTrue(res.statusCode() >= 200 && res.statusCode() < 300,
                    () -> rc + " must still admit the member on their own app: " + res.statusCode() + ": " + res.body());
        }
    }

    @Test
    public void testAppTwoRowNeverLeaksToAppOneMember() throws Exception {
        for (RouteCase rc : readRoutesFor(APP_2)) {
            HttpResponse<String> res = send(rc.method(), rc.path(), memberSession, null);
            assertTrue(!res.body().contains("app2's row"),
                    () -> rc + " response body must never contain APP_2's actual data: " + res.body());
        }
    }
}
