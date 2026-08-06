package com.appbana.server.routes;

import com.appbana.ApiServer;
import com.appbana.JdbcManager;
import com.appbana.SchemaManager;
import com.appbana.model.EntitySchema;
import com.appbana.service.PasswordService;
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
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RuntimeSessionScopedToSingleAppTest — S3.6 (docs/planning/TENANT_ISOLATION_IMPLEMENTATION_TASKS.md).
 *
 * <p>Formalizes, through a <b>real HTTP login round-trip</b> (not a hand-minted
 * {@code SessionService.createSession} call), that the session issued by
 * {@link com.appbana.api.GenericAppAuthController#login()} — now reachable at all after this same
 * task's {@code SessionMiddleware} fix, see class-level note below — is scoped to exactly the
 * {@code appId} the caller logged into (rule (ii) of
 * {@link com.appbana.security.EntityAccessGuard}), not to the tenant at large and not to any other
 * app.
 *
 * <p>Deliberately does <b>not</b> grant any {@code appbana_app_members} row for the logging-in
 * user — the whole point is that {@code login()}'s own
 * {@code SessionService.createSession(userId, appTenantId, appId)} call (a scoped session, rule
 * (ii)) is sufficient by itself for APP_1 access and insufficient by itself for APP_2 access, with
 * no membership grant involved on either side.
 *
 * <p><b>Side discovery this task</b>: before the {@code SessionMiddleware} fix (this same task,
 * see {@code SessionMiddlewareTest#testRuntimeAuthLoginPathExcluded}), {@code POST
 * /api/runtime/auth/login} was itself blocked with 401 "Missing session token" for every caller —
 * a login endpoint that unconditionally required a pre-existing session was unreachable by design.
 * This test is the first to exercise that route through the real Router+SessionMiddleware stack
 * (S3.3's {@code GenericAppAuthControllerTest} calls the controller method directly, bypassing
 * middleware) and would have failed at step one (the login call itself returning 401) had the fix
 * not already landed.
 */
public class RuntimeSessionScopedToSingleAppTest {

    private static final int PORT = 18106;
    private static final String BASE_URL = "http://localhost:" + PORT;
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TENANT = "t-s36-rs-tenant";
    private static final String APP_1 = "s36-rs-app-1";
    private static final String APP_2 = "s36-rs-app-2";
    private static final String LOGIN_ENTITY = "S36RuntimeLoginUser";
    private static final String BUSINESS_ENTITY = "S36RuntimeEntity";

    private static final String ALICE_EMAIL = "alice-s36-rs@example.com";
    private static final String ALICE_PASSWORD = "Secret123!";

    @BeforeAll
    public static void startServer() throws Exception {
        ApiServer.startJdk(PORT);
    }

    @BeforeEach
    public void setUp() throws Exception {
        SessionService.clearAllSessions();
        cleanUpFixtures();

        saveLoginSchema();
        insertLoginUser(1L, ALICE_EMAIL, PasswordService.hashPassword(ALICE_PASSWORD));

        saveBusinessSchema(APP_1);
        insertBusinessRow(APP_1, 999001L, "app1's row");
        saveBusinessSchema(APP_2);
        insertBusinessRow(APP_2, 999002L, "app2's row");
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
            s.execute("DROP TABLE IF EXISTS \"" + physicalTableName(APP_1, LOGIN_ENTITY).toUpperCase() + "\"");
            s.execute("DROP TABLE IF EXISTS \"" + physicalTableName(APP_1, BUSINESS_ENTITY).toUpperCase() + "\"");
            s.execute("DROP TABLE IF EXISTS \"" + physicalTableName(APP_2, BUSINESS_ENTITY).toUpperCase() + "\"");
            s.execute("DELETE FROM appbana_schemas WHERE tenant_id = '" + TENANT + "' AND app_id IN ('"
                    + APP_1 + "', '" + APP_2 + "')");
            s.execute("DELETE FROM appbana_app_members WHERE tenant_id = '" + TENANT + "' AND app_id IN ('"
                    + APP_1 + "', '" + APP_2 + "')");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String physicalTableName(String appId, String entityName) {
        EntitySchema s = new EntitySchema();
        s.setTenantId(TENANT);
        s.setAppId(appId);
        s.setName(entityName);
        return SchemaManager.getPhysicalTableName(s);
    }

    private void saveLoginSchema() {
        EntitySchema s = new EntitySchema();
        s.setName(LOGIN_ENTITY);
        s.setAppId(APP_1);
        s.setTenantId(TENANT);

        EntitySchema.Field id = new EntitySchema.Field();
        id.setName("id");
        id.setType("long");
        id.setPrimaryKey(true);
        id.setAutoIncrement(true);

        EntitySchema.Field email = new EntitySchema.Field();
        email.setName("email");
        email.setType("string");

        EntitySchema.Field password = new EntitySchema.Field();
        password.setName("password");
        password.setType("string");

        s.setFields(List.of(id, email, password));
        SchemaManager.saveSchema(s);
    }

    private void insertLoginUser(long id, String email, String hashedPassword) {
        String table = physicalTableName(APP_1, LOGIN_ENTITY).toUpperCase();
        String sql = "INSERT INTO \"" + table + "\" (\"ID\",\"EMAIL\",\"PASSWORD\") VALUES (?,?,?)";
        try (Connection c = JdbcManager.getConnection("default");
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setString(2, email);
            ps.setString(3, hashedPassword);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void saveBusinessSchema(String appId) {
        EntitySchema s = new EntitySchema();
        s.setName(BUSINESS_ENTITY);
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

    private void insertBusinessRow(String appId, long id, String label) {
        String table = physicalTableName(appId, BUSINESS_ENTITY).toUpperCase();
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
        return TENANT + "_" + appId + "_" + BUSINESS_ENTITY;
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

    /** Real login round-trip; asserts 200 and returns the minted session token. */
    private String loginAsAlice() throws Exception {
        String body = MAPPER.writeValueAsString(Map.of(
                "appId", APP_1,
                "tenantId", TENANT,
                "entity", LOGIN_ENTITY,
                "email", ALICE_EMAIL,
                "password", ALICE_PASSWORD));
        HttpResponse<String> res = send("POST", "/api/runtime/auth/login", null, body);
        assertEquals(200, res.statusCode(), () -> "real login must succeed with the right credentials: " + res.body());
        JsonNode json = MAPPER.readTree(res.body());
        String token = json.get("token").asText();
        assertNotNull(token, "login response must carry a session token");
        assertTrue(!token.isBlank(), "session token must not be blank");
        return token;
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
                new RouteCase("GET", "/appbana-studio/" + TENANT + "/apps/" + appId + "/" + BUSINESS_ENTITY),
                new RouteCase("GET", "/api/" + TENANT + "/apps/" + appId + "/env/dev/" + BUSINESS_ENTITY)
        );
    }

    private List<RouteCase> writeRoutesFor(String appId) {
        return List.of(
                new RouteCase("POST", "/api/" + packedKey(appId)),
                new RouteCase("POST", "/appbana-studio/" + TENANT + "/apps/" + appId + "/" + BUSINESS_ENTITY),
                new RouteCase("POST", "/api/" + TENANT + "/apps/" + appId + "/" + BUSINESS_ENTITY),
                new RouteCase("POST", "/api/" + TENANT + "/apps/" + appId + "/env/dev/" + BUSINESS_ENTITY)
        );
    }

    @Test
    public void testLoginMintedSessionAdmittedOnOwnAppReadRoutes() throws Exception {
        String session = loginAsAlice();
        for (RouteCase rc : readRoutesFor(APP_1)) {
            HttpResponse<String> res = send(rc.method(), rc.path(), session, null);
            assertTrue(res.statusCode() >= 200 && res.statusCode() < 300,
                    () -> rc + " must admit a session scoped to this exact app: " + res.statusCode() + ": " + res.body());
        }
    }

    @Test
    public void testLoginMintedSessionAdmittedOnOwnAppWriteRoutes() throws Exception {
        String session = loginAsAlice();
        String body = MAPPER.writeValueAsString(Map.of("label", "written by scoped session"));
        for (RouteCase rc : writeRoutesFor(APP_1)) {
            HttpResponse<String> res = send(rc.method(), rc.path(), session, body);
            assertTrue(res.statusCode() >= 200 && res.statusCode() < 300,
                    () -> rc + " must admit a session scoped to this exact app: " + res.statusCode() + ": " + res.body());
        }
    }

    @Test
    public void testLoginMintedSessionDeniedOnDifferentAppReadRoutes() throws Exception {
        String session = loginAsAlice();
        for (RouteCase rc : readRoutesFor(APP_2)) {
            HttpResponse<String> res = send(rc.method(), rc.path(), session, null);
            assertEquals(403, res.statusCode(),
                    () -> rc + " must deny a session scoped only to APP_1 on APP_2's entity, got "
                            + res.statusCode() + ": " + res.body());
        }
    }

    @Test
    public void testLoginMintedSessionDeniedOnDifferentAppWriteRoutes() throws Exception {
        String session = loginAsAlice();
        String body = MAPPER.writeValueAsString(Map.of("label", "cross-app-injected"));
        for (RouteCase rc : writeRoutesFor(APP_2)) {
            HttpResponse<String> res = send(rc.method(), rc.path(), session, body);
            assertEquals(403, res.statusCode(),
                    () -> rc + " must deny a session scoped only to APP_1 on APP_2's entity, got "
                            + res.statusCode() + ": " + res.body());
        }
    }

    @Test
    public void testAppTwoRowNeverLeaksToAppOneScopedSession() throws Exception {
        String session = loginAsAlice();
        for (RouteCase rc : readRoutesFor(APP_2)) {
            HttpResponse<String> res = send(rc.method(), rc.path(), session, null);
            assertTrue(!res.body().contains("app2's row"),
                    () -> rc + " response body must never contain APP_2's actual data: " + res.body());
        }
    }

    @Test
    public void testUnauthenticatedCallerDeniedOnBothApps() throws Exception {
        for (String appId : List.of(APP_1, APP_2)) {
            for (RouteCase rc : readRoutesFor(appId)) {
                HttpResponse<String> res = send(rc.method(), rc.path(), null, null);
                assertEquals(401, res.statusCode(),
                        () -> rc + " must require a session at all for app " + appId + ", got "
                                + res.statusCode() + ": " + res.body());
            }
        }
    }
}
